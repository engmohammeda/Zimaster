package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Controller for AI audio generation (Gemini neural TTS, permanently cached).
 *
 * Runs a bounded-concurrency background queue. Owns the transient `audioGenJob`
 * and the private `lessonAudioText` helper; everything else is reached through
 * [AppViewModel] aliases. See [ExamsController] for the ownership conventions.
 */
internal class AudioController(internal val vm: AppViewModel) {

    // ── The TTS engine is owned by the view model (shared with key sync) ──
    private var tts
        get() = vm.tts
        set(v) { vm.tts = v }

    // ── Transient generation state owned by the view model (written here) ──
    private var isGeneratingAudio
        get() = vm.isGeneratingAudio
        set(v) { vm.isGeneratingAudio = v }
    private var audioGenTotal
        get() = vm.audioGenTotal
        set(v) { vm.audioGenTotal = v }
    private var audioGenDone
        get() = vm.audioGenDone
        set(v) { vm.audioGenDone = v }
    private var audioGenLabel
        get() = vm.audioGenLabel
        set(v) { vm.audioGenLabel = v }
    private var lastAudioMessage
        get() = vm.lastAudioMessage
        set(v) { vm.lastAudioMessage = v }

    private val vocab get() = vm.vocab
    private val lessons get() = vm.lessons
    private val storyArchive get() = vm.storyArchive
    private val aiAudioEnabled get() = vm.aiAudioEnabled
    private val autoGenerateAiAudio get() = vm.autoGenerateAiAudio
    private val geminiApiKey get() = vm.geminiApiKey
    private val ttsVoice get() = vm.ttsVoice

    private var audioGenJob: Job? = null

    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
        vm.vmScope.launch(block = block)

    /** Wire the shared TTS engine (called once from the Activity/Composition). */
    fun attachTts(engine: com.zmastery.english.audio.TtsManager) {
        tts = engine
        // Push whatever credential is already loaded so TTS works immediately.
        engine.apiKey = geminiApiKey
        engine.voice = ttsVoice
        engine.backupApiKeys = vm.apiKeys.filter { it.rawKey.isNotBlank() }.map { it.rawKey }
    }

    /** Count of clips still needing generation (drives the button badge). */
    val pendingAudioCount: Int
        get() {
            var n = 0
            vocab.forEach { w ->
                if (!w.wordAudioReady && w.english.isNotBlank()) n++
                if (!w.exampleAudioReady && w.exampleEn.isNotBlank()) n++
            }
            lessons.forEach { l -> if (!l.audioReady && lessonAudioText(l).isNotBlank()) n++ }
            storyArchive.forEach { s -> if (!s.audioReady && s.en.isNotBlank()) n++ }
            return n
        }

    val hasPendingAudio: Boolean get() = pendingAudioCount > 0

    /** The English text spoken for a lesson (reading/listening/story/dialogue). */
    fun lessonAudioText(l: Lesson): String {
        val parts = mutableListOf<String>()
        if (l.fullTextEn.isNotBlank()) parts += l.fullTextEn
        else if (l.readingEn.isNotBlank()) parts += l.readingEn
        if (l.segments.isNotEmpty()) parts += l.segments.joinToString(" ") { it.en }
        if (l.keySentences.isNotEmpty()) parts += l.keySentences.joinToString(" ") { it.en }
        if (l.dialogues.isNotEmpty()) parts += l.dialogues.joinToString(" ") { it.en }
        return parts.joinToString(" ").trim()
    }

    /** How many Gemini TTS requests may run at the same time (rate-limit friendly). */
    private val AUDIO_PARALLELISM = 2

    private data class AudioJob(val text: String, val label: String, val longForm: Boolean, val onDone: () -> Unit)

    /**
     * توليد أصوات العناصر ذات الأولوية فقط (الكلمات المستحقة، قصة اليوم، الدرس الحالي)
     * لتوفير الحصص وتجنب استهلاك حدود النماذج بسرعة.
     */
    fun generatePriorityAudio() {
        val engine = tts ?: run { lastAudioMessage = "محرك الصوت غير جاهز بعد"; return }
        if (!aiAudioEnabled) {
            lastAudioMessage = "توليد الأصوات بالذكاء الاصطناعي متوقّف من الإعدادات"
            return
        }
        if (isGeneratingAudio) return
        if (!engine.hasGeminiKey) {
            lastAudioMessage = "أضف مفتاح Gemini في الإعدادات لتوليد أصوات دائمة بالذكاء الاصطناعي"
            return
        }
        if (!engine.isOnline()) {
            lastAudioMessage = "لا يوجد اتصال — سيتم توليد الأصوات لاحقاً"
            return
        }

        audioGenJob?.cancel()
        audioGenJob = launch {
            isGeneratingAudio = true
            audioGenDone = 0
            audioGenLabel = "تحديد العناصر ذات الأولوية…"

            val jobs = mutableListOf<AudioJob>()

            // 1. Due words (review items)
            val dueWords = vocab.filter { it.dueInDays <= 0 && (!it.wordAudioReady || !it.exampleAudioReady) }.take(15)
            dueWords.forEach { w ->
                if (!w.wordAudioReady && w.english.isNotBlank()) {
                    jobs += AudioJob(w.english, w.english, false) {
                        val i = vocab.indexOfFirst { it.id == w.id }
                        if (i >= 0) vocab[i] = vocab[i].copy(wordAudioReady = true)
                    }
                }
                if (!w.exampleAudioReady && w.exampleEn.isNotBlank()) {
                    jobs += AudioJob(w.exampleEn, "مثال: ${w.english}", false) {
                        val i = vocab.indexOfFirst { it.id == w.id }
                        if (i >= 0) vocab[i] = vocab[i].copy(exampleAudioReady = true)
                    }
                }
            }

            // 2. Latest stories
            val pendingStories = storyArchive.filter { !it.audioReady && it.en.isNotBlank() }.take(2)
            pendingStories.forEach { s ->
                jobs += AudioJob(s.en, "قصة: ${s.title}", true) {
                    val i = storyArchive.indexOfFirst { it.id == s.id }
                    if (i >= 0) storyArchive[i] = storyArchive[i].copy(audioReady = true)
                }
            }

            // 3. Next incomplete lesson
            val nextLesson = lessons.firstOrNull { !it.isCompleted && !it.audioReady && lessonAudioText(it).isNotBlank() }
            if (nextLesson != null) {
                val t = lessonAudioText(nextLesson)
                jobs += AudioJob(t, "درس: ${nextLesson.title}", true) {
                    val i = lessons.indexOfFirst { it.id == nextLesson.id }
                    if (i >= 0) lessons[i] = lessons[i].copy(audioReady = true)
                }
            }

            executeAudioJobs(engine, jobs, "الأولوية")
        }
    }

    /**
     * Scan all content for items lacking a PERMANENT AI voice and generate
     * them via a bounded-concurrency background queue.
     * @param maxBatch أقصى عدد عناصر في هذه الدفعة لتجنب نفاذ الحصة (0 أو سالب = الكل).
     */
    fun generateMissingAudio(maxBatch: Int = -1) {
        val engine = tts ?: run { lastAudioMessage = "محرك الصوت غير جاهز بعد"; return }
        if (!aiAudioEnabled) {
            lastAudioMessage = "توليد الأصوات بالذكاء الاصطناعي متوقّف من الإعدادات"
            return
        }
        if (isGeneratingAudio) return
        if (!engine.hasGeminiKey) {
            lastAudioMessage = "أضف مفتاح Gemini في الإعدادات لتوليد أصوات دائمة بالذكاء الاصطناعي"
            return
        }
        if (!engine.isOnline()) {
            lastAudioMessage = "لا يوجد اتصال — سيتم توليد الأصوات لاحقاً"
            return
        }

        audioGenJob?.cancel()
        audioGenJob = launch {
            isGeneratingAudio = true
            audioGenDone = 0
            audioGenLabel = "جارٍ التحضير…"

            // Build the work list (short clips + long-form story/lesson narrations).
            val jobs = mutableListOf<AudioJob>()

            vocab.toList().forEach { w ->
                if (!w.wordAudioReady && w.english.isNotBlank()) {
                    jobs += AudioJob(w.english, w.english, false) {
                        val i = vocab.indexOfFirst { it.id == w.id }
                        if (i >= 0) vocab[i] = vocab[i].copy(wordAudioReady = true)
                    }
                }
                if (!w.exampleAudioReady && w.exampleEn.isNotBlank()) {
                    jobs += AudioJob(w.exampleEn, "مثال: ${w.english}", false) {
                        val i = vocab.indexOfFirst { it.id == w.id }
                        if (i >= 0) vocab[i] = vocab[i].copy(exampleAudioReady = true)
                    }
                }
            }
            lessons.toList().forEach { l ->
                val t = lessonAudioText(l)
                if (!l.audioReady && t.isNotBlank()) {
                    jobs += AudioJob(t, "درس: ${l.title}", true) {
                        val i = lessons.indexOfFirst { it.id == l.id }
                        if (i >= 0) lessons[i] = lessons[i].copy(audioReady = true)
                    }
                }
            }
            storyArchive.toList().forEach { s ->
                if (!s.audioReady && s.en.isNotBlank()) {
                    jobs += AudioJob(s.en, "قصة: ${s.title}", true) {
                        val i = storyArchive.indexOfFirst { it.id == s.id }
                        if (i >= 0) storyArchive[i] = storyArchive[i].copy(audioReady = true)
                    }
                }
            }

            val targetJobs = if (maxBatch > 0) jobs.take(maxBatch) else jobs
            executeAudioJobs(engine, targetJobs, if (maxBatch > 0) "دفعة $maxBatch" else "الكل")
        }
    }

    private suspend fun executeAudioJobs(
        engine: com.zmastery.english.audio.TtsManager,
        jobs: List<AudioJob>,
        modeLabel: String
    ) {
        audioGenTotal = jobs.size
        if (jobs.isEmpty()) {
            isGeneratingAudio = false
            lastAudioMessage = "كل الأصوات المطلوبة مولّدة بالفعل ✓"
            return
        }

        val semaphore = Semaphore(AUDIO_PARALLELISM)
        val okCount = Mutex()
        var ok = 0
        var quotaExhausted = false

        coroutineScope {
            val tasks = jobs.map { job ->
                async {
                    semaphore.withPermit {
                        if (quotaExhausted || !isGeneratingAudio) return@withPermit
                        val success = runCatching {
                            if (job.longForm) engine.generateLongFormAndCache(job.text)
                            else engine.generateAndCachePermanent(job.text)
                        }.getOrDefault(false)

                        if (success) {
                            job.onDone()
                            okCount.withLock { ok++ }
                        } else {
                            if (engine.exhaustedModels.size >= engine.ttsFallbackModels.size) {
                                quotaExhausted = true
                            }
                        }
                        audioGenLabel = job.label
                        audioGenDone++
                    }
                }
            }
            tasks.awaitAll()
        }

        isGeneratingAudio = false
        audioGenLabel = ""
        lastAudioMessage = when {
            quotaExhausted -> "تم توليد $ok صوت — استُنفدت الحصص وسيتم إبقاء البقية لوقت لاحق"
            ok == jobs.size -> "تم توليد أصوات $ok عنصر ($modeLabel) بنجاح عبر ${engine.activeModel} ✓"
            else -> "تم توليد $ok من ${jobs.size} — البقية محفوظة لتوليدها لاحقاً"
        }
        vm.persist()
    }

    /**
     * Hard stop: cancels any AI audio generation currently running right now.
     * Does NOT touch the [autoGenerateAiAudio] setting — call that separately
     * to also stop it from auto-starting again after future imports.
     */
    fun stopAudioGeneration() {
        audioGenJob?.cancel()
        audioGenJob = null
        isGeneratingAudio = false
        audioGenLabel = ""
        lastAudioMessage = "تم إيقاف توليد الأصوات"
    }

    /**
     * "استبدل بصوت الذكاء الاصطناعي" — forces EVERY word/example/lesson/story
     * that already has a permanently cached clip to be regenerated by Gemini
     * neural TTS from scratch (e.g. clips that were originally produced before
     * this AI-only policy existed, or simply to refresh the voice). Marks
     * every item as "not ready" and deletes its old cached file, then runs the
     * normal background queue exactly like [generateMissingAudio].
     */
    fun regenerateAllAudioWithAi() {
        val engine = tts ?: run { lastAudioMessage = "محرك الصوت غير جاهز بعد"; return }
        if (!aiAudioEnabled) {
            lastAudioMessage = "توليد الأصوات بالذكاء الاصطناعي متوقّف من الإعدادات"
            return
        }
        if (isGeneratingAudio) return
        if (!engine.hasGeminiKey) {
            lastAudioMessage = "أضف مفتاح Gemini في الإعدادات أولاً"
            return
        }

        vocab.toList().forEachIndexed { _, w ->
            if (w.english.isNotBlank()) engine.evictCache(w.english)
            if (w.exampleEn.isNotBlank()) engine.evictCache(w.exampleEn)
        }
        for (i in vocab.indices) {
            vocab[i] = vocab[i].copy(wordAudioReady = false, exampleAudioReady = false)
        }
        lessons.toList().forEach { l ->
            val t = lessonAudioText(l)
            if (t.isNotBlank()) engine.evictCache(t)
        }
        for (i in lessons.indices) {
            lessons[i] = lessons[i].copy(audioReady = false)
        }
        storyArchive.toList().forEach { s ->
            if (s.en.isNotBlank()) engine.evictCache(s.en)
        }
        for (i in storyArchive.indices) {
            storyArchive[i] = storyArchive[i].copy(audioReady = false)
        }
        vm.persist()
        generateMissingAudio()
    }

    /**
     * Auto-queue AI voice generation for freshly imported content — only when
     * the setting is enabled AND the device is online. Completely silent (no
     * UI takeover, does not block the caller); progress is still observable
     * via the state fields for the dashboard banner.
     */
    fun autoGenerateAudioIfOnline() {
        if (!aiAudioEnabled || !autoGenerateAiAudio) return
        val engine = tts ?: return
        if (!engine.hasGeminiKey) return
        if (!engine.isOnline()) {
            lastAudioMessage = "لا يوجد اتصال — سيتم توليد الأصوات لاحقاً"
            return
        }
        generateMissingAudio()
    }

    /** Generate (and permanently cache) the AI narration for ONE story on demand. */
    fun generateStoryAudio(storyId: Int) {
        val engine = tts ?: run { lastAudioMessage = "محرك الصوت غير جاهز بعد"; return }
        if (!aiAudioEnabled) {
            lastAudioMessage = "توليد الأصوات بالذكاء الاصطناعي متوقّف من الإعدادات"
            return
        }
        val story = storyArchive.firstOrNull { it.id == storyId } ?: return
        if (story.audioReady || story.en.isBlank()) return
        if (!engine.hasGeminiKey) {
            lastAudioMessage = "أضف مفتاح Gemini في الإعدادات لتوليد صوت طبيعي لهذه القصة"
            return
        }
        if (!engine.isOnline()) {
            lastAudioMessage = "لا يوجد اتصال حالياً"
            return
        }
        if (isGeneratingAudio) return
        launch {
            isGeneratingAudio = true
            audioGenTotal = 1
            audioGenDone = 0
            audioGenLabel = "قصة: ${story.title}"
            lastAudioMessage = "جارٍ توليد الصوت الطبيعي…"
            val prevVoice = engine.voice
            val readerVoice = vm.aiAgents.firstOrNull { it.id == "story_reader" }?.voiceId.orEmpty()
            if (readerVoice.isNotBlank()) {
                engine.voice = readerVoice.replaceFirstChar { it.uppercaseChar() }
            }
            try {
                val success = runCatching { engine.generateLongFormAndCache(story.en) }.getOrDefault(false)
                if (success) {
                    val i = storyArchive.indexOfFirst { it.id == storyId }
                    if (i >= 0) storyArchive[i] = storyArchive[i].copy(audioReady = true)
                    vm.persist()
                    lastAudioMessage = "تم توليد صوت القصة ✓"
                    audioGenDone = 1
                } else {
                    lastAudioMessage = "تعذّر توليد صوت القصة — حاول لاحقاً"
                }
            } finally {
                engine.voice = prevVoice
                isGeneratingAudio = false
                audioGenLabel = ""
            }
        }
    }

    /** Generate (and permanently cache) the AI narration for ONE lesson's reading text on demand. */
    fun generateLessonAudio(lessonId: Int) {
        val engine = tts ?: run { lastAudioMessage = "محرك الصوت غير جاهز بعد"; return }
        if (!aiAudioEnabled) {
            lastAudioMessage = "توليد الأصوات بالذكاء الاصطناعي متوقّف من الإعدادات"
            return
        }
        val lesson = lessons.firstOrNull { it.id == lessonId } ?: return
        val text = lessonAudioText(lesson)
        if (lesson.audioReady || text.isBlank()) return
        if (!engine.hasGeminiKey) {
            lastAudioMessage = "أضف مفتاح Gemini في الإعدادات لتوليد صوت طبيعي لهذا الدرس"
            return
        }
        if (!engine.isOnline()) {
            lastAudioMessage = "لا يوجد اتصال حالياً"
            return
        }
        launch {
            val success = runCatching { engine.generateLongFormAndCache(text) }.getOrDefault(false)
            if (success) {
                val i = lessons.indexOfFirst { it.id == lessonId }
                if (i >= 0) lessons[i] = lessons[i].copy(audioReady = true)
                vm.persist()
                lastAudioMessage = "تم توليد صوت الدرس ✓"
            } else {
                lastAudioMessage = "تعذّر توليد صوت الدرس — حاول لاحقاً"
            }
        }
    }
}
