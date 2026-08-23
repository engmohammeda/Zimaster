package com.zmastery.english.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zmastery.english.data.*
import com.zmastery.english.domain.usecases.AiService
import com.zmastery.english.domain.usecases.BackupCoordinator
import com.zmastery.english.domain.usecases.CloudSyncService
import com.zmastery.english.domain.usecases.PerformanceUtils
import com.zmastery.english.domain.usecases.ReviewScheduler
import com.zmastery.english.domain.usecases.StoryService
import com.zmastery.english.domain.usecases.StreakManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.math.max
import kotlin.math.roundToInt

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ── Use Cases (extracted logic — independently testable) ──
    val reviewScheduler = ReviewScheduler()
    val streakManager = StreakManager()
    val backupCoordinator = BackupCoordinator()
    val aiService = AiService()
    val cloudSyncService = CloudSyncService()
    val storyService = StoryService()

    // ── Performance (throttle widget refresh + cache expensive computations) ──
    private val widgetThrottle = PerformanceUtils.Throttle(5 * 60_000L)  // 5 min
    private val statsCache = PerformanceUtils.TimedCache<Any>(30_000L)   // 30 sec

    // ----- Courses (mutable so imports can add) -----
    val courses = mutableStateListOf<Course>().apply { addAll(SampleData.courses) }
    val vocab = mutableStateListOf<VocabWord>().apply { addAll(SampleData.vocab.map { it.copy() }) }
    val lessons = mutableStateListOf<Lesson>().apply { addAll(SampleData.lessons.map { it.copy() }) }

    private var nextCourseId = (courses.maxOfOrNull { it.id } ?: 0) + 1
    private var nextLessonId = (lessons.maxOfOrNull { it.id } ?: 0) + 1
    private var nextWordId = (vocab.maxOfOrNull { it.id } ?: 0) + 1

    // ----- Persistence -----
    /** True once the initial load from disk completes (UI can show a splash if needed). */
    var isLoaded by mutableStateOf(false)
        private set
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            // Safe load with automatic backup recovery — if the primary data
            // is corrupted or empty, DataGuard tries the last good backup.
            val result = DataGuard.safeLoad(getApplication())
            if (result.source != LoadSource.EMPTY) {
                restoreFrom(result.state)
                if (result.recovered) {
                    android.util.Log.w("AppViewModel",
                        "Data recovered from backup: ${result.health.lessonCount} lessons, " +
                            "${result.health.vocabCount} vocab")
                }
            }
            isLoaded = true
            // مزامنة صامتة عند الإقلاع: تبني الصناديق الناقصة دون إشعارات.
            syncMysteryRewards(notify = false)
            syncWidget()
        }
    }

    /** Map a lesson-JSON quiz array to domain items (shared by import + self-heal). */
    private fun mapJsonQuiz(items: List<JsonQuiz>): List<QuizItem> = items.map { q ->
        QuizItem(
            type = when (q.type.lowercase().trim()) {
                "true_false", "true/false", "tf" -> QuizType.TRUE_FALSE
                "written", "write", "translation" -> QuizType.WRITTEN
                else -> QuizType.MULTIPLE_CHOICE
            },
            question = q.question,
            options = q.options,
            answer = q.answer,
            explanationAr = q.explanationAr,
            audioText = q.wordToSpeak?.trim().orEmpty(),
        )
    }

    /**
     * Self-healing migration — lessons imported BEFORE rich-field persistence was
     * fixed had their grammar/reading/conversation/writing content silently
     * dropped when saving (audio_quiz words were lost even at import time).
     * The verbatim `rawJson` always survived, though, so we re-derive every
     * missing field from it. Each field fills only when empty, so this
     * converges after one pass and never clobbers anything.
     * Runs on every restore (startup + backup import).
     */
    private fun repairLessonRichContent() {
        var repaired = 0
        for (i in lessons.indices) {
            val l = lessons[i]
            if (l.rawJson.isBlank()) continue
            val pkg = runCatching {
                ImportEngine.json.decodeFromString(LessonPackage.serializer(), l.rawJson)
            }.getOrNull() ?: continue

            // Heal audio_quiz spoken words (index-aligned with the JSON array).
            val healedQuiz = l.quiz.mapIndexed { idx, qi ->
                val spoken = pkg.quiz.getOrNull(idx)?.wordToSpeak?.trim().orEmpty()
                if (qi.audioText.isBlank() && spoken.isNotBlank()) qi.copy(audioText = spoken) else qi
            }

            val refilled = l.copy(
                keyExpressions = if (l.keyExpressions.isEmpty()) pkg.lessonContent.keyExpressions.map {
                    KeyExpression(it.expressionEn, it.expressionAr, it.usageAr)
                } else l.keyExpressions,
                explanationAr = l.explanationAr.ifBlank { pkg.lessonContent.explanationAr },
                logicAr = l.logicAr.ifBlank { pkg.lessonContent.logicAr },
                examples = if (l.examples.isEmpty()) pkg.lessonContent.examples.map { Sentence(it.en, it.ar) } else l.examples,
                fullTextEn = l.fullTextEn.ifBlank { pkg.lessonContent.fullTextEn },
                fullTextAr = l.fullTextAr.ifBlank { pkg.lessonContent.fullTextAr },
                segments = if (l.segments.isEmpty()) pkg.lessonContent.segments.map { Sentence(it.en, it.ar) } else l.segments,
                topicEn = l.topicEn.ifBlank { pkg.lessonContent.topicEn },
                topicAr = l.topicAr.ifBlank { pkg.lessonContent.topicAr },
                brainstorming = if (l.brainstorming.isEmpty()) pkg.lessonContent.brainstormingQuestions.map {
                    BrainstormQ(it.questionEn, it.questionAr, it.suggestedAnswerEn, it.suggestedAnswerAr)
                } else l.brainstorming,
                guidedSentences = if (l.guidedSentences.isEmpty()) pkg.lessonContent.guidedSentences.map { Sentence(it.en, it.ar) } else l.guidedSentences,
                finalDraft = l.finalDraft ?: pkg.lessonContent.finalDraft.let {
                    if (it.en.isNotBlank() || it.ar.isNotBlank()) Sentence(it.en, it.ar) else null
                },
                quiz = healedQuiz,
            )
            if (refilled != l) {
                lessons[i] = refilled
                repaired++
            }
        }
        if (repaired > 0) {
            android.util.Log.i("Zmastery", "استُشفي المحتوى الغني لـ $repaired درساً من rawJson")
        }
    }

    /** Rebuild in-memory state from a persisted snapshot. Curriculum courses are
     *  merged with any imported ones (imported course ids > built-in). */
    private fun restoreFrom(s: AppState) {
        if (s.courses.isNotEmpty()) {
            courses.clear()
            courses.addAll(s.courses.map { it.toDomain() })
            // Ensure all built-in curriculum courses still exist (in case schema grew).
            SampleData.courses.forEach { base ->
                if (courses.none { it.id == base.id }) courses.add(base)
            }
        }
        lessons.clear(); lessons.addAll(s.lessons.map { it.toDomain() })
        vocab.clear(); vocab.addAll(s.vocab.map { it.toDomain() })
        repairLessonRichContent()

        val p = s.profile
        streak = p.streak; xp = p.xp; totalReviewsToday = p.totalReviewsToday
        dailyGoal = p.dailyGoal; lessonsPerDay = p.lessonsPerDay; studyHours = p.studyHours
        motivationLevel = p.motivationLevel; accuracy = p.accuracy
        geminiApiKey = p.geminiApiKey; ttsVoice = p.ttsVoice
        desiredRetention = p.desiredRetention; maxIntervalDays = p.maxIntervalDays
        revealMode = runCatching { com.zmastery.english.data.RevealMode.valueOf(p.revealMode) }
            .getOrDefault(com.zmastery.english.data.RevealMode.FULL)
        reviewAutoPlay = p.reviewAutoPlay
        autoGenerateAiAudio = p.autoGenerateAiAudio
        aiAudioEnabled = p.aiAudioEnabled
        mnemonicStyle = MnemonicArtStyle.from(p.mnemonicStyle)
        mnemonicPersona = MnemonicPersona.from(p.mnemonicPersona)
        mnemonicModel = MnemonicModel.from(p.mnemonicModel)
        mnemonicNumbering = p.mnemonicNumbering
        mnemonicBatchSize = p.mnemonicBatchSize.coerceIn(MnemonicSpec.MIN_BATCH, MnemonicSpec.MAX_BATCH)
        nextCourseId = maxOf(p.nextCourseId, (courses.maxOfOrNull { it.id } ?: 0) + 1)
        nextLessonId = maxOf(p.nextLessonId, (lessons.maxOfOrNull { it.id } ?: 0) + 1)
        nextWordId = maxOf(p.nextWordId, (vocab.maxOfOrNull { it.id } ?: 0) + 1)

        // Restore AI agent overrides onto the default agents
        if (s.aiAgents.isNotEmpty()) {
            s.aiAgents.forEach { dto ->
                val i = aiAgents.indexOfFirst { it.id == dto.id }
                if (i >= 0) aiAgents[i] = aiAgents[i].copy(
                    modelId = dto.modelId, character = dto.character,
                    voiceId = dto.voiceId, style = dto.style, prompt = dto.prompt,
                )
            }
        }
        apiKeys.clear()
        apiKeys.addAll(
            s.apiKeys
                // Drop legacy placeholder rows that only ever held a masked string.
                .filter { it.rawKey.isNotBlank() }
                .map {
                    ApiKeyEntry(
                        id = it.id, label = it.label, provider = it.provider,
                        rawKey = it.rawKey, active = it.active,
                        baseUrl = it.baseUrl, status = it.status,
                    )
                }
        )
        // Migration: a key typed into the old single-field settings box becomes
        // a real credential entry so nothing the user already saved is lost.
        if (apiKeys.isEmpty() && geminiApiKey.isNotBlank()) {
            apiKeys.add(
                ApiKeyEntry(
                    id = "migrated", label = "مفتاح Gemini", provider = AiProvider.GEMINI.name,
                    rawKey = geminiApiKey.trim(), active = true,
                )
            )
        }
        if (apiKeys.isNotEmpty() && apiKeys.none { it.active }) {
            apiKeys[0] = apiKeys[0].copy(active = true)
        }
        syncActiveKey()
        examHistory.clear()
        examHistory.addAll(s.exams.map { it.toDomain() })
        examMisses.clear()
        s.examMisses.forEach { (k, v) -> k.toIntOrNull()?.let { examMisses[it] = v } }
        onboardingDone = p.onboardingDone
        lastActiveDay = p.lastActiveDay
        // Restore today's task progress, then roll over if the date changed.
        p.dailyTaskProgress.forEach { entry ->
            val id = entry.substringBefore(':')
            val prog = entry.substringAfter(':', "0").toIntOrNull() ?: 0
            val i = dailyTasks.indexOfFirst { it.id == id }
            if (i >= 0) dailyTasks[i] = dailyTasks[i].copy(progress = prog)
        }
        applyDayRollover()
        lastStoryDay = p.lastStoryDay
        storyArchive.clear()
        storyArchive.addAll(s.stories.map { it.toDomain() })
        nextStoryId = maxOf(p.nextStoryId, (storyArchive.maxOfOrNull { it.id } ?: 0) + 1)
        // Reading-course lessons always contribute their text to the archive.
        syncLessonStories()
        dayStats.clear()
        s.dayStats.forEach { dayStats[it.epochDay] = it }
        studyPlan = s.studyPlan
        coachReports.clear()
        s.coachReports.forEach { coachReports[it.scope] = it }
        // ----- 3D Momentum + Seven Seals -----
        microHabitId = p.microHabitId.ifBlank { MicroHabits.all.first().id }
        microHabitProgress = p.microHabitProgress
        dayEarnedStreak = p.dayEarnedStreak
        learnerName = p.learnerName
        learnerEmail = p.learnerEmail
        lastCloudLessonSyncMillis = p.lastCloudLessonSyncMillis
        cloudSyncEnabled = p.cloudSyncEnabled
        googleWebClientId = p.googleWebClientId
        com.zmastery.english.cloud.CloudAuth.webClientId = p.googleWebClientId
        openedChests.clear()
        s.chests.forEach { openedChests[it.tierId] = it }
        wallet = s.wallet
        // ----- الصناديق الغامضة -----
        mysteryRewards.clear()
        mysteryRewards.addAll(s.mysteryRewards)
        // ----- المرحلة 3/4 — مرآة الإدراك + الإنقاذ -----
        mirrorReports.clear()
        mirrorReports.putAll(s.mirrorReports)
        // إعادة بناء التيليمتري السلوكي — بدونه تُفقد بصمة المتعلّم عند كل تشغيل.
        reviewLogs.clear()
        s.reviewSignals.forEach { sig ->
            reviewLogs.add(
                ReviewLog(
                    wordId = sig.wordId,
                    recall = RecallSource.NONE,
                    grade = sig.grade,
                    reachedStage = sig.stage,
                    replays = sig.replays,
                    timeMs = sig.timeMs,
                    retrievability = 0.0,
                    stabilityAfter = 0.0,
                    intervalAfter = 0,
                )
            )
        }
        reviewHours.clear()
        reviewHours.addAll(s.reviewHours)
        rescue = s.rescue
        lastRescueOfferDay = s.lastRescueOfferDay
        streakBeforeBreak = s.streakBeforeBreak
    }

    /** Debounced save — batches rapid mutations into one write with backup. */
    fun persist() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            val state = buildAppState()
            // Safe save: creates a backup of the previous state before overwriting,
            // and logs errors clearly instead of silently swallowing them.
            val result = DataGuard.safeSave(getApplication(), state)
            if (!result.success) {
                android.util.Log.e("AppViewModel", "Save FAILED: ${result.error}")
            }
            syncWidget()
        }
    }

    /** Push current stats to the shared store and refresh home-screen widgets. */
    /** Push current stats to the shared store and refresh home-screen widgets.
     *  Widget refresh is throttled to 5 minutes to avoid excessive broadcasts. */
    fun syncWidget() {
        val ctx = getApplication<Application>()
        ProgressStore.save(
            ctx,
            streak = streak,
            xp = xp,
            reviewsToday = totalReviewsToday,
            dailyGoal = dailyGoal,
            tasksDone = activeTasksDone,
            tasksTotal = activeDailyTasks.size,
            hasContent = hasContent,
            chestMood = decayState.mood.name,
            decaySeverity = decayState.severity,
            minimumDone = microHabitDone || tasksDone >= dailyTasks.size,
        )
        // Throttle widget refresh to avoid excessive broadcasts
        if (widgetThrottle.allow()) {
            com.zmastery.english.widget.ZMasteryWidget.refreshAll(ctx)
        }
        syncNotifState()
    }

    /** Force an immediate widget refresh (bypasses throttle).
     *  Use for explicit user actions like completing a lesson. */
    fun forceWidgetRefresh() {
        widgetThrottle.reset()
        val ctx = getApplication<Application>()
        com.zmastery.english.widget.ZMasteryWidget.refreshAll(ctx)
    }

    /** Keep the notification receiver's state fresh (runs even when app is closed). */
    fun syncNotifState() {
        val ctx = getApplication<Application>()
        val prefs = ctx.getSharedPreferences("z_notif_state", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("due_words", dueWords.size)
            .putInt("streak", streak)
            // "done" must mean real, achievable work was finished.
            .putBoolean("done_today", planCompleteToday)
            // Suppress nagging entirely until the learner has content to study.
            .putBoolean("has_content", hasContent)
            .putBoolean("has_history", hasHistory)
            // ----- Stage 4: loss-aversion state for the evening alarm -----
            .putString("chest_mood", decayState.mood.name)
            .putInt("rescue_streak", rescue.streakToRestore)
            .putBoolean("rescue_active", rescue.isActive && !rescue.completed)
            .apply()
    }

    /** Wipe all imported content & progress, restoring the empty curriculum. */
    fun resetAll() {
        courses.clear(); courses.addAll(SampleData.courses)
        lessons.clear(); vocab.clear()
        nextCourseId = (courses.maxOfOrNull { it.id } ?: 0) + 1
        nextLessonId = 1; nextWordId = 1
        streak = 0; xp = 0; totalReviewsToday = 0; studyHours = 0.0; accuracy = 0
        motivationLevel = 0f
        // Stories referenced words/lessons that no longer exist — clear the archive.
        // (Onboarding is intentionally NOT reset: the learner already saw the mission.)
        storyArchive.clear()
        nextStoryId = 1
        lastStoryDay = 0L
        // Mnemonic tiles belong to word ids that no longer exist — drop them all.
        MnemonicStore.clearAll(getApplication())
        dayStats.clear(); reviewHours.clear(); coachReports.clear()
        studyPlan = StudyPlanDto()
        examHistory.clear(); examMisses.clear(); examQuestions.clear()
        mnemonicBatch.clear()
        mnemonicPromptText = ""
        mnemonicVersion++
        viewModelScope.launch { Persistence.clear(getApplication()) }
    }

    // ======================================================================
    //  Backup & Restore
    // ======================================================================

    /** Build a full snapshot of the current in-memory state. */
    fun currentAppState(): AppState = AppState(
        courses = courses.map { it.toDto() },
        lessons = lessons.map { it.toDto() },
        vocab = vocab.map { it.toDto() },
        profile = ProfileDto(
            streak = streak, xp = xp, totalReviewsToday = totalReviewsToday,
            dailyGoal = dailyGoal, lessonsPerDay = lessonsPerDay, studyHours = studyHours,
            motivationLevel = motivationLevel, accuracy = accuracy,
            geminiApiKey = geminiApiKey, ttsVoice = ttsVoice,
            nextCourseId = nextCourseId, nextLessonId = nextLessonId, nextWordId = nextWordId,
            desiredRetention = desiredRetention, maxIntervalDays = maxIntervalDays,
            revealMode = revealMode.name,
            reviewAutoPlay = reviewAutoPlay,
            autoGenerateAiAudio = autoGenerateAiAudio,
            aiAudioEnabled = aiAudioEnabled,
            mnemonicStyle = mnemonicStyle.name, mnemonicPersona = mnemonicPersona.name,
            mnemonicModel = mnemonicModel.name, mnemonicNumbering = mnemonicNumbering,
            mnemonicBatchSize = mnemonicBatchSize,
        ),
        exams = examHistory.map { it.toDto() },
        examMisses = examMisses.mapKeys { it.key.toString() },
    )

    private fun nowStamp(): String = java.time.LocalDateTime.now()
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    // ---- Exports (return the text content to be written to a file) ----

    fun exportFullBackup(): String = BackupManager.exportFull(currentAppState(), nowStamp())

    fun exportLessonsOnly(): String = BackupManager.exportLessons(
        courses = courses.filter { c -> lessons.any { it.courseId == c.id } }.map { it.toDto() },
        lessons = lessons.map { it.toDto() },
        vocab = vocab.map { it.toDto() },
        createdAt = nowStamp(),
    )

    fun exportHardWordsJson(): String =
        BackupManager.exportWordsJson(hardWordsForExport().map { it.toDto() }, nowStamp())

    fun exportHardWordsCsv(): String =
        BackupManager.exportWordsCsv(hardWordsForExport().map { it.toDto() })

    fun exportAllWordsCsv(): String =
        BackupManager.exportWordsCsv(activeVocab.map { it.toDto() })

    /** Difficult words = leeches + words currently below target retention. */
    fun hardWordsForExport(): List<VocabWord> {
        val leeches = activeVocab.filter { it.lapses >= 2 && it.stability < 7.0 }
        val hardByDifficulty = activeVocab.filter { it.difficulty >= 6.5 }
        return (leeches + hardByDifficulty).distinctBy { it.id }.sortedByDescending { it.lapses }
    }

    val hardWordsCount: Int get() = hardWordsForExport().size

    // ---- Imports / Restore ----

    data class RestoreResult(val ok: Boolean, val message: String)

    /** Full restore — replaces everything with the backup's contents. */
    fun restoreFullBackup(raw: String): RestoreResult {
        val parsed = BackupManager.parseFull(raw)
        return parsed.fold(
            onSuccess = { state ->
                restoreFrom(state)
                persist()
                RestoreResult(true, "تمت الاستعادة: ${state.courses.size} كورس · ${state.lessons.size} درس · ${state.vocab.size} كلمة")
            },
            onFailure = { RestoreResult(false, "تعذّر قراءة الملف: ${it.message?.take(60) ?: "تنسيق غير صالح"}") },
        )
    }

    /** Merge lessons backup into the current library (delta update, keeps progress). */
    fun importLessonsBackup(raw: String): RestoreResult {
        val parsed = BackupManager.parseLessons(raw)
        return parsed.fold(
            onSuccess = { b ->
                var addedCourses = 0; var addedLessons = 0; var addedWords = 0
                // Remap incoming ids to avoid collisions.
                val courseIdMap = HashMap<Int, Int>()
                b.courses.forEach { cdto ->
                    val existing = courses.firstOrNull { it.name == cdto.name && it.levelId == cdto.levelId }
                    if (existing != null) {
                        courseIdMap[cdto.id] = existing.id
                    } else {
                        val newId = nextCourseId++
                        courseIdMap[cdto.id] = newId
                        courses.add(cdto.toDomain().copy(id = newId))
                        addedCourses++
                    }
                }
                val wordIdMap = HashMap<Int, Int>()
                b.vocab.forEach { wdto ->
                    val newId = nextWordId++
                    wordIdMap[wdto.id] = newId
                    val mappedCourse = courseIdMap[wdto.courseId] ?: wdto.courseId
                    vocab.add(wdto.toDomain().copy(id = newId, courseId = mappedCourse))
                    addedWords++
                }
                b.lessons.forEach { ldto ->
                    val mappedCourse = courseIdMap[ldto.courseId] ?: ldto.courseId
                    val newWordIds = ldto.newWordIds.map { wordIdMap[it] ?: it }
                    lessons.add(ldto.toDomain().copy(id = nextLessonId++, courseId = mappedCourse, newWordIds = newWordIds))
                    addedLessons++
                }
                // Heal rich content for lessons coming from backups made before
                // the persistence fix — their rawJson still carries everything.
                repairLessonRichContent()
                persist()
                RestoreResult(true, "تمت الإضافة: $addedCourses كورس · $addedLessons درس · $addedWords كلمة")
            },
            onFailure = { RestoreResult(false, "تعذّر قراءة ملف الدروس: ${it.message?.take(60) ?: "تنسيق غير صالح"}") },
        )
    }

    /** Import a words file (JSON) into the dictionary as new active words. */
    fun importWordsBackup(raw: String): RestoreResult {
        val parsed = BackupManager.parseWords(raw)
        return parsed.fold(
            onSuccess = { words ->
                var added = 0
                words.forEach { wdto ->
                    val exists = vocab.any { it.english.equals(wdto.english, ignoreCase = true) }
                    if (!exists) {
                        vocab.add(wdto.toDomain().copy(id = nextWordId++, pendingApproval = false))
                        added++
                    }
                }
                persist()
                RestoreResult(true, "تمت إضافة $added كلمة جديدة إلى القاموس")
            },
            onFailure = { RestoreResult(false, "تعذّر قراءة ملف الكلمات: ${it.message?.take(60) ?: "تنسيق غير صالح"}") },
        )
    }

    // ----- User profile / gamification (fresh start) -----
    var streak by mutableStateOf(0)
        private set
    var totalReviewsToday by mutableStateOf(0)
        private set
    var dailyGoal by mutableStateOf(30)
    var lessonsPerDay by mutableStateOf(2)
    var xp by mutableStateOf(0)
        private set
    var themeMode by mutableStateOf(com.zmastery.english.data.ThemeMode.SYSTEM)
    var studyHours by mutableStateOf(0.0)
        private set
    /**
     * Legacy stored mood value. Prefer [engagement] — this is kept only for
     * backward compatibility with saved profiles and the widget, and now
     * starts at 0 so a fresh install never claims "excellent motivation".
     */
    var motivationLevel by mutableStateOf(0f) // 0..1 mascot mood

    // Global default for how vocab cards reveal inside lessons.
    var revealMode by mutableStateOf(com.zmastery.english.data.RevealMode.FULL)

    /** Auto-play the English pronunciation as soon as each review stage appears. */
    var reviewAutoPlay by mutableStateOf(true)

    /**
     * Settings toggle: when true, freshly imported content is queued for
     * permanent AI voice generation automatically in the background. When
     * false, nothing is generated until the learner taps "توليد" manually.
     * On-demand instant playback is unaffected either way.
     */
    var autoGenerateAiAudio by mutableStateOf(true)

    /**
     * MASTER switch — "إيقاف توليد الأصوات نهائياً". When false, AI audio
     * generation is completely disabled: no auto-generation after import, no
     * manual "توليد" button, and any generation currently in flight is
     * cancelled immediately. Instant local playback (speakInstant) is always
     * unaffected — this only ever controls the PERMANENT AI-cached path.
     */
    var aiAudioEnabled by mutableStateOf(true)
        private set

    fun updateAiAudioEnabled(enabled: Boolean) {
        aiAudioEnabled = enabled
        if (!enabled) stopAudioGeneration()
        persist()
    }

    // ----- API keys / voice (for Gemini TTS + LLM) -----
    var geminiApiKey by mutableStateOf("")
    var ttsVoice by mutableStateOf("Kore")

    // ===================== AI configuration layer =====================
    // Per-feature agents (character / prompt / style / voice / model).
    val aiAgents = mutableStateListOf<AiAgent>().apply { addAll(AiDefaults.agents()) }
    // Available models (starts with built-ins; "fetch models" appends provider models).
    val aiModels = mutableStateListOf<AiModel>().apply { addAll(AiDefaults.builtinModels) }
    val aiVoices = mutableStateListOf<AiVoice>().apply { addAll(AiDefaults.builtinVoices) }
    val apiKeys = mutableStateListOf<ApiKeyEntry>().apply { addAll(AiDefaults.sampleKeys) }

    var isFetchingModels by mutableStateOf(false)
        private set
    var fetchModelsMessage by mutableStateOf<String?>(null)
    /** Per-version diagnostics from the last fetch (e.g. "v1beta: 68 · v1alpha: 9"). */
    var fetchModelsDetail by mutableStateOf<String?>(null)
        private set
    /** True when the last fetch failed (drives error styling). */
    var fetchModelsFailed by mutableStateOf(false)
        private set
    /** Show only models with a documented free-tier allowance. */
    var showFreeModelsOnly by mutableStateOf(false)

    fun updateAgent(updated: AiAgent) {
        val i = aiAgents.indexOfFirst { it.id == updated.id }
        if (i >= 0) { aiAgents[i] = updated; persist() }
    }

    /** The credential currently used by every AI feature (null = none). */
    val activeKey: ApiKeyEntry?
        get() = apiKeys.firstOrNull { it.active } ?: apiKeys.firstOrNull()

    /** True when at least one real key is stored — gates every AI feature. */
    val hasAiKey: Boolean get() = apiKeys.any { it.rawKey.isNotBlank() }

    /** Keep the legacy [geminiApiKey] field pointing at the active Gemini key
     *  so older code paths (TTS, models.list) keep working unchanged. */
    private fun syncActiveKey() {
        val gem = apiKeys.firstOrNull { it.active && it.providerEnum == AiProvider.GEMINI }
            ?: apiKeys.firstOrNull { it.providerEnum == AiProvider.GEMINI }
        geminiApiKey = gem?.rawKey ?: ""
        tts?.apiKey = geminiApiKey
    }

    var keyMessage by mutableStateOf<String?>(null)
    var verifyingKeyId by mutableStateOf<String?>(null)
        private set

    fun addApiKey(
        label: String,
        rawKey: String,
        provider: AiProvider = AiProvider.GEMINI,
        baseUrl: String = "",
    ) {
        val clean = rawKey.trim()
        if (clean.isBlank()) { keyMessage = "المفتاح فارغ"; return }
        if (apiKeys.any { it.rawKey == clean }) { keyMessage = "هذا المفتاح مضاف مسبقاً"; return }
        val entry = ApiKeyEntry(
            id = "k${System.currentTimeMillis()}",
            label = label.ifBlank { provider.label },
            provider = provider.name,
            rawKey = clean,
            active = apiKeys.isEmpty(),
            baseUrl = baseUrl.trim(),
        )
        apiKeys.add(entry)
        syncActiveKey()
        persist()
        keyMessage = "تمت إضافة المفتاح — جارٍ التحقق…"
        verifyKey(entry.id)
    }

    /** Live-test a stored credential against its provider. */
    fun verifyKey(id: String) {
        val i = apiKeys.indexOfFirst { it.id == id }
        if (i < 0) return
        verifyingKeyId = id
        viewModelScope.launch {
            val res = AiClient.verify(apiKeys[i])
            val j = apiKeys.indexOfFirst { it.id == id }
            if (j >= 0) {
                apiKeys[j] = apiKeys[j].copy(status = if (res.ok) "ok" else res.error)
            }
            keyMessage = if (res.ok) (res.text.ifBlank { "المفتاح يعمل ✓" }) else res.error
            verifyingKeyId = null
            persist()
        }
    }

    fun activateKey(id: String) {
        for (i in apiKeys.indices) apiKeys[i] = apiKeys[i].copy(active = apiKeys[i].id == id)
        syncActiveKey()
        persist()
    }

    fun updateKeyLabel(id: String, label: String) {
        val i = apiKeys.indexOfFirst { it.id == id }
        if (i >= 0) { apiKeys[i] = apiKeys[i].copy(label = label.trim().ifBlank { apiKeys[i].label }); persist() }
    }

    /** Delete a credential. Callers MUST confirm first (destructive). */
    fun removeKey(id: String) {
        val removed = apiKeys.firstOrNull { it.id == id }
        apiKeys.removeAll { it.id == id }
        // Never leave the app keyless-but-marked-active.
        if (apiKeys.isNotEmpty() && apiKeys.none { it.active }) {
            apiKeys[0] = apiKeys[0].copy(active = true)
        }
        syncActiveKey()
        persist()
        keyMessage = removed?.let { "تم حذف «${it.label}»" }
    }

    /**
     * Run a text completion through the ACTIVE credential, whoever the provider
     * is. Feature code calls this instead of talking to Gemini directly.
     */
    suspend fun aiComplete(
        system: String,
        user: String,
        agentId: String = "",
        json: Boolean = false,
    ): AiClient.Reply {
        val key = activeKey
            ?: return AiClient.Reply(false, "", "أضف مفتاح API من إعدادات الذكاء الاصطناعي")
        val model = aiAgents.firstOrNull { it.id == agentId }?.modelId.orEmpty()
        return AiClient.complete(key, model, system, user, json)
    }

    /**
     * Fetch EVERY model the active API key can access — no allow-list, no
     * filtering. Queries both v1beta and v1alpha and merges the results, so
     * preview/experimental models (live, native-audio, new TTS families) that
     * only exist on one version still show up. The fetched list REPLACES the
     * built-ins, because what the key actually exposes is the truth; built-ins
     * are only a fallback for an offline first run.
     */
    fun fetchModels() {
        if (isFetchingModels) return
        val cred = activeKey
        if (cred == null || cred.rawKey.isBlank()) {
            fetchModelsFailed = true
            fetchModelsMessage = "أضف مفتاح API من هذه الشاشة أولاً"
            return
        }
        // OpenAI-compatible providers use /models; Gemini uses its own lister.
        if (cred.protocol == AiProtocol.OPENAI) {
            isFetchingModels = true
            fetchModelsFailed = false
            viewModelScope.launch {
                val list = AiClient.listOpenAiModels(cred)
                isFetchingModels = false
                if (list.isEmpty()) {
                    fetchModelsFailed = true
                    fetchModelsMessage = "تعذّر جلب النماذج من ${cred.providerEnum.label}"
                } else {
                    aiModels.clear(); aiModels.addAll(list.sortedByDescending { it.familyRank })
                    fetchModelsMessage = "تم جلب ${list.size} نموذج من ${cred.providerEnum.label}"
                    fetchModelsDetail = cred.providerEnum.label
                    persist()
                }
            }
            return
        }
        val key = cred.rawKey.trim()
        isFetchingModels = true
        fetchModelsFailed = false
        fetchModelsMessage = null
        fetchModelsDetail = null
        viewModelScope.launch {
            val res = GeminiModelsService.listAll(key, includeAllVersions = true)
            isFetchingModels = false
            if (res.success && res.models.isNotEmpty()) {
                // Keep any built-in the provider did not return, so a selected
                // model never vanishes from an agent mid-session.
                val fetchedIds = res.models.map { it.id }.toSet()
                val keptBuiltins = aiModels.filter { !it.fetched && it.id !in fetchedIds }
                aiModels.clear()
                aiModels.addAll(res.models)
                aiModels.addAll(keptBuiltins)
                fetchModelsFailed = false
                fetchModelsMessage = res.message
                fetchModelsDetail = res.detail
                persist()
            } else {
                fetchModelsFailed = true
                fetchModelsMessage = res.message
                fetchModelsDetail = res.detail
            }
        }
    }

    /** Models of a kind, honouring the "free only" toggle, newest first. */
    fun modelsOfKind(kind: ModelKind): List<AiModel> {
        val base = aiModels.filter { it.kind == kind }
        val filtered = if (showFreeModelsOnly) {
            base.filter { GeminiQuotas.isFree(it.id) || GeminiQuotas.isUnknown(it.id) }
        } else base
        return filtered.sortedWith(compareByDescending<AiModel> { it.familyRank }.thenBy { it.id })
    }

    /**
     * Every model, grouped by kind — powers the full catalogue view. Kinds with
     * no models are omitted.
     */
    fun modelsGrouped(): List<Pair<ModelKind, List<AiModel>>> =
        ModelKind.values().mapNotNull { k ->
            val list = modelsOfKind(k)
            if (list.isEmpty()) null else k to list
        }

    /** Count of models with a documented free-tier allowance. */
    val freeModelCount: Int get() = aiModels.count { GeminiQuotas.isFree(it.id) }

    /**
     * Candidate models for an agent. The agent's declared kind comes first, but
     * the FULL catalogue is always appended so the user can pick any model for
     * any persona — including brand-new previews we could not classify.
     */
    fun modelChoicesFor(agent: AiAgent): List<Pair<ModelKind, List<AiModel>>> {
        val primary = modelsOfKind(agent.kind)
        val rest = ModelKind.values()
            .filter { it != agent.kind }
            .mapNotNull { k ->
                val l = modelsOfKind(k)
                if (l.isEmpty()) null else k to l
            }
        return buildList {
            if (primary.isNotEmpty()) add(agent.kind to primary)
            addAll(rest)
        }
    }

    fun modelName(id: String) = aiModels.firstOrNull { it.id == id }?.displayName ?: id
    fun modelById(id: String) = aiModels.firstOrNull { it.id == id }
    fun voiceName(id: String) = aiVoices.firstOrNull { it.id == id }?.displayName ?: id

    // ----- Daily phrase -----
    val dailyPhrase: String get() = SampleData.dailyPhrases[java.time.LocalDate.now().dayOfYear % SampleData.dailyPhrases.size]

    // ======================================================================
    //  Onboarding / mission
    // ======================================================================
    /** False until the learner has seen the mission ("why consistency") screen. */
    var onboardingDone by mutableStateOf(false)

    fun completeOnboarding() {
        onboardingDone = true
        persist()
    }

    // ======================================================================
    //  Story archive — daily stories + every reading lesson's story
    // ======================================================================
    val storyArchive = mutableStateListOf<ArchivedStory>()
    private var nextStoryId = 1

    /** Epoch-day of the last generated daily story (0 = never). */
    var lastStoryDay by mutableStateOf(0L)
        private set

    var isMakingStory by mutableStateOf(false)
        private set
    var storyMessage by mutableStateOf<String?>(null)

    /** True when today's daily story already exists. */
    val hasTodayStory: Boolean
        get() {
            val today = todayEpochDay()
            return storyArchive.any { it.kind == StoryKind.DAILY && it.dayEpoch == today }
        }

    /** Today's daily story, if generated. */
    val todayStory: ArchivedStory?
        get() {
            val today = todayEpochDay()
            return storyArchive.firstOrNull { it.kind == StoryKind.DAILY && it.dayEpoch == today }
        }

    /** Archive newest-first, favourites pinned on top. */
    val storiesSorted: List<ArchivedStory>
        get() = storyArchive.sortedWith(
            compareByDescending<ArchivedStory> { it.isFavorite }
                .thenByDescending { it.dayEpoch }
                .thenByDescending { it.id }
        )

    val dailyStoryCount: Int get() = storyArchive.count { it.kind == StoryKind.DAILY }
    val lessonStoryCount: Int get() = storyArchive.count { it.kind == StoryKind.LESSON }
    val unreadStoryCount: Int get() = storyArchive.count { !it.isRead }

    /** Words eligible to seed today's story: due first, then the newest. */
    private fun storySeedWords(max: Int = 7): List<VocabWord> {
        val due = dueWords.filter { it.exampleEn.isNotBlank() }
        val pool = if (due.size >= 3) due else activeVocab.filter { it.exampleEn.isNotBlank() }
        return pool.sortedByDescending { it.id }.take(max)
    }

    /** How many words are available to build a story from right now. */
    val storySeedCount: Int get() = storySeedWords().size

    // ---- AI-only daily story state ----
    /** True while the generator is parked waiting for internet / the model. */
    var isWaitingForAi by mutableStateOf(false)
        private set
    /** Attempt number of the current waiting loop (1-based, 0 = idle). */
    var storyAttempt by mutableStateOf(0)
        private set
    /** Seconds remaining before the next automatic retry. */
    var storyRetryIn by mutableStateOf(0)
        private set
    private var storyJob: Job? = null

    /** True when the story engine can run at all (key configured). */
    val storyAiReady: Boolean get() = hasAiKey

    /** Cancel an in-flight / waiting story generation. */
    fun cancelStoryGeneration() {
        storyJob?.cancel()
        storyJob = null
        isMakingStory = false
        isWaitingForAi = false
        storyAttempt = 0
        storyRetryIn = 0
        storyMessage = "تم إلغاء التوليد"
    }

    /**
     * Generate (or regenerate) today's story with the AI **only**.
     *
     * Per product requirement there is NO offline/template fallback: if the
     * device is offline or the model is momentarily unavailable, the coroutine
     * parks and retries with exponential backoff (capped) until it succeeds or
     * the user cancels. A story is only ever written by the model.
     */
    fun generateTodayStory(force: Boolean = false, onDone: (ArchivedStory?) -> Unit = {}) {
        if (isMakingStory || isWaitingForAi) return
        val today = todayEpochDay()
        if (!force) {
            todayStory?.let { storyMessage = "قصة اليوم جاهزة"; onDone(it); return }
        }
        val seeds = storySeedWords()
        if (seeds.size < 2) {
            storyMessage = "أضف كلمتين على الأقل لها أمثلة لتوليد قصة"
            onDone(null)
            return
        }
        if (!hasAiKey) {
            storyMessage = "قصة اليوم تُكتب بالذكاء الاصطناعي — أضف مفتاح API من إعدادات الذكاء الاصطناعي"
            onDone(null)
            return
        }

        val agent = aiAgents.firstOrNull { it.id == "story_writer" }
        val ctx = getApplication<Application>()

        isMakingStory = true
        isWaitingForAi = false
        storyAttempt = 0
        storyMessage = null

        storyJob = viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                storyAttempt = attempt
                isWaitingForAi = false
                storyRetryIn = 0

                val res = GeminiStoryService.generate(
                    ctx = ctx,
                    words = seeds,
                    apiKey = geminiApiKey,
                    modelId = agent?.modelId ?: "gemini-2.5-flash",
                    persona = agent?.character.orEmpty(),
                    style = agent?.style.orEmpty(),
                    basePrompt = agent?.prompt.orEmpty(),
                )

                when (res) {
                    is GeminiStoryService.Result.Success -> {
                        val st = res.story
                        storyArchive.removeAll { it.kind == StoryKind.DAILY && it.dayEpoch == today }
                        val story = ArchivedStory(
                            id = nextStoryId++,
                            kind = StoryKind.DAILY,
                            title = st.title.ifBlank { DailyStoryMaker.title(today, seeds.size) },
                            en = st.en,
                            ar = st.ar,
                            words = seeds.map { it.english },
                            dayEpoch = today,
                            dateLabel = java.time.LocalDate.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        )
                        storyArchive.add(story)
                        lastStoryDay = today
                        isMakingStory = false
                        isWaitingForAi = false
                        storyAttempt = 0
                        storyMessage = "تم توليد قصة اليوم بالذكاء الاصطناعي من ${seeds.size} كلمة"
                        xp += 12
                        completeTask("story")
                        persist()
                        onDone(story)
                        return@launch
                    }

                    is GeminiStoryService.Result.Fatal -> {
                        isMakingStory = false
                        isWaitingForAi = false
                        storyAttempt = 0
                        storyMessage = res.message
                        onDone(null)
                        return@launch
                    }

                    is GeminiStoryService.Result.Retryable -> {
                        // Park and wait — never fall back to a template story.
                        isWaitingForAi = true
                        storyMessage = res.message
                        // Backoff: 5s, 10s, 20s, 40s … capped at 60s.
                        val waitSec = (5 * (1 shl (attempt - 1).coerceAtMost(4))).coerceAtMost(60)
                        for (t in waitSec downTo 1) {
                            if (!isActive) return@launch
                            storyRetryIn = t
                            delay(1000)
                        }
                        storyRetryIn = 0
                        // Before burning an API call, wait for the radio to come back.
                        var guard = 0
                        while (isActive && !GeminiStoryService.isOnline(ctx) && guard < 600) {
                            storyMessage = "بانتظار عودة الإنترنت…"
                            delay(1000)
                            guard++
                        }
                    }
                }
            }
        }
    }

    /** AI prompt for enriching today's story (used by the AI-assisted path). */
    fun todayStoryPrompt(): String = DailyStoryMaker.aiPrompt(storySeedWords())

    /**
     * Mirror every reading-style lesson's text into the archive, so lesson
     * stories and daily stories live in ONE place. Idempotent: re-syncing
     * updates existing entries instead of duplicating them.
     */
    fun syncLessonStories() {
        lessons.forEach { lesson ->
            val course = courses.firstOrNull { it.id == lesson.courseId }
            val style = course?.style
            val isStoryLike = style == LessonStyle.READING_TEXT ||
                style == LessonStyle.STORY ||
                style == LessonStyle.NEWS
            val body = lesson.fullTextEn.ifBlank { lesson.readingEn }
            val existing = storyArchive.indexOfFirst {
                it.kind == StoryKind.LESSON && it.lessonId == lesson.id
            }

            // Reading stories only ever belong in the archive once the learner
            // has actually FINISHED the lesson — an in-progress/未完成 lesson
            // would otherwise clutter the archive with things not yet earned.
            val shouldBeArchived = isStoryLike && body.isNotBlank() && lesson.isCompleted

            if (!shouldBeArchived) {
                if (existing >= 0) storyArchive.removeAt(existing)
                return@forEach
            }

            val bodyAr = lesson.fullTextAr.ifBlank { lesson.readingAr }
            if (existing >= 0) {
                val prev = storyArchive[existing]
                storyArchive[existing] = prev.copy(
                    title = lesson.title,
                    en = body,
                    ar = bodyAr,
                    courseName = course?.name ?: prev.courseName,
                    words = lesson.newWordIds.mapNotNull { id ->
                        vocab.firstOrNull { it.id == id }?.english
                    },
                    isRead = true,
                )
            } else {
                storyArchive.add(
                    ArchivedStory(
                        id = nextStoryId++,
                        kind = StoryKind.LESSON,
                        title = lesson.title,
                        en = body,
                        ar = bodyAr,
                        words = lesson.newWordIds.mapNotNull { id ->
                            vocab.firstOrNull { it.id == id }?.english
                        },
                        dayEpoch = 0L,
                        dateLabel = "الدرس ${lesson.no}",
                        lessonId = lesson.id,
                        courseName = course?.name ?: "",
                        isRead = true,
                    )
                )
            }
        }
    }

    fun toggleStoryRead(id: Int) {
        val i = storyArchive.indexOfFirst { it.id == id }
        if (i < 0) return
        val nowRead = !storyArchive[i].isRead
        storyArchive[i] = storyArchive[i].copy(isRead = nowRead)
        // Count only transitions into "read" so toggling can't inflate stats.
        if (nowRead) track { it.storiesRead += 1 }
        persist()
    }

    fun toggleStoryFavorite(id: Int) {
        val i = storyArchive.indexOfFirst { it.id == id }
        if (i < 0) return
        storyArchive[i] = storyArchive[i].copy(isFavorite = !storyArchive[i].isFavorite)
        persist()
    }

    /** Delete a story. Lesson stories are re-created on the next sync by design. */
    fun deleteStory(id: Int) {
        storyArchive.removeAll { it.id == id }
        persist()
    }

    /**
     * حذف قصة اليوم الحالية فقط. نصفّر [lastStoryDay] أيضاً حتى يعود زر
     * "ولّد قصة اليوم" للعمل مباشرة بعد الحذف.
     */
    fun deleteTodayStory() {
        val today = todayEpochDay()
        val existed = storyArchive.any { it.kind == StoryKind.DAILY && it.dayEpoch == today }
        storyArchive.removeAll { it.kind == StoryKind.DAILY && it.dayEpoch == today }
        if (existed) {
            lastStoryDay = 0L
            storyMessage = "حُذفت قصة اليوم — يمكنك توليد قصة جديدة"
            persist()
        }
    }

    /** إعادة توليد قصة اليوم: تحذف الحالية ثم تطلب واحدة جديدة من النموذج. */
    fun regenerateTodayStory(onDone: (ArchivedStory?) -> Unit = {}) {
        if (isMakingStory || isWaitingForAi) return
        val today = todayEpochDay()
        storyArchive.removeAll { it.kind == StoryKind.DAILY && it.dayEpoch == today }
        lastStoryDay = 0L
        generateTodayStory(force = true, onDone = onDone)
    }

    // ----- Daily tasks (adaptive) -----
    // الخطة تُولَّد يومياً من حالة المتعلّم الحقيقية عبر [AdaptiveTasks]، فلا
    // تُطلب مهمة مستحيلة (محادثة بلا دروس، اختبار بلا كلمات) ولا تبقى ثابتة.
    val dailyTasks = mutableStateListOf<DailyTask>()

    /** اليوم الذي بُنيت له الخطة الحالية — يمنع إعادة البناء داخل نفس اليوم. */
    private var planBuiltForDay = -1L

    /** لقطة حالة المتعلّم التي يقرأها مولّد الخطة. */
    private fun learnerSnapshot(): LearnerSnapshot {
        val recentDays = (0 until 7).mapNotNull { off -> dayStats[Telemetry.today() - off] }
            .filter { it.isActive }
        val avgReviews = if (recentDays.isEmpty()) 0
        else recentDays.sumOf { it.reviews } / recentDays.size
        return LearnerSnapshot(
            epochDay = Telemetry.today(),
            activeWords = activeVocab.size,
            dueWords = dueWords.size,
            newWords = activeVocab.count { it.totalReviews == 0 },
            openLessons = lessons.count { !it.isCompleted },
            completedLessons = lessons.count { it.isCompleted },
            storiesAvailable = storyArchive.size,
            canMakeStory = storySeedCount >= 2 && storyAiReady,
            hasConversationLesson = lessons.any { l ->
                courses.firstOrNull { it.id == l.courseId }?.type == CourseType.CONVERSATION
            } || lessons.any { it.dialogues.isNotEmpty() },
            wordsMissingMnemonic = mnemonicMissingCount,
            activeDays = dayStats.values.count { it.isActive },
            recentAvgReviews = avgReviews,
            streak = streak,
        )
    }

    /** شرح مختصر لسبب حجم خطة اليوم — يُعرض تحت العنوان. */
    val planRationale: String
        get() = AdaptiveTasks.rationale(learnerSnapshot(), dailyTasks.size)

    /** مرحلة المتعلّم الحالية (تُعرض كشارة). */
    val learnerTier: LearnerTier get() = learnerSnapshot().tier

    /**
     * (أعد) بناء خطة اليوم مع الحفاظ على أي تقدّم أُحرز في مهام مشتركة.
     * يُستدعى عند بدء يوم جديد وعند تغيّر المحتوى جوهرياً (استيراد/إضافة كلمات).
     */
    fun rebuildDailyPlan(force: Boolean = false) {
        val today = Telemetry.today()
        if (!force && planBuiltForDay == today && dailyTasks.isNotEmpty()) return
        val previous = dailyTasks.associate { it.id to it.progress }
        val fresh = AdaptiveTasks.buildPlan(learnerSnapshot())
        dailyTasks.clear()
        // نُبقي التقدّم السابق لنفس المعرّف حتى لا يضيع جهد اليوم عند التحديث.
        fresh.forEach { t ->
            val prior = previous[t.id] ?: 0
            dailyTasks.add(t.copy(progress = prior.coerceAtMost(t.target)))
        }
        planBuiltForDay = today
    }

    /** Epoch-day that [dailyTasks] / [totalReviewsToday] currently belong to. */
    var lastActiveDay by mutableStateOf(0L)
        private set

    /**
     * Reset the per-day counters when the calendar day changes.
     *
     * Without this, a task completed yesterday still shows as done today and
     * the dashboard wrongly claims "you finished today's plan" right after
     * install/restore. Called on load and whenever the dashboard is composed.
     */
    fun applyDayRollover() {
        val today = Telemetry.today()
        if (lastActiveDay == today) return
        val hadPrevious = lastActiveDay > 0L
        lastActiveDay = today
        if (hadPrevious) {
            // New day: clear task progress and today's review counter, then
            // regenerate the plan so it reflects the learner's new state.
            for (i in dailyTasks.indices) dailyTasks[i] = dailyTasks[i].copy(progress = 0)
            totalReviewsToday = 0
            microHabitProgress = 0
            dayEarnedStreak = false
            rebuildDailyPlan(force = true)
            // A missed day breaks the streak — but a Streak Freeze can absorb it.
            val rawStreak = Telemetry.currentStreak(dayStats)
            if (rawStreak == 0 && streak > 0 && wallet.streakFreezes > 0) {
                // Spend one shield: the streak survives a single missed day.
                wallet = wallet.spendFreeze()
                lastFreezeUsedDay = today
                com.zmastery.english.notify.Notifier.achievement(
                    getApplication(),
                    "درع تجميد السلسلة عمل!",
                    "فاتك يوم لكن الدرع حماك — سلسلتك ($streak) لا تزال قائمة. تبقّى ${wallet.streakFreezes} درع.",
                )
            } else {
                // المرحلة الرابعة: بدل واجهة اللوم، نطلق مهمة إنقاذ فورية.
                val broken = streak
                streak = rawStreak
                if (rawStreak == 0 && broken > 0) maybeOfferRescue(broken)
            }
            persist()
        }
    }

    fun completeTask(id: String, amount: Int = 1) {
        val i = dailyTasks.indexOfFirst { it.id == id }
        if (i >= 0) {
            val t = dailyTasks[i]
            if (!t.done) {
                dailyTasks[i] = t.copy(progress = (t.progress + amount).coerceAtMost(t.target))
                if (dailyTasks[i].done) {
                    xp += 30
                    motivationLevel = (motivationLevel + 0.05f).coerceAtMost(1f)
                    // Celebrate the individual task
                    com.zmastery.english.notify.Notifier.achievement(
                        getApplication(),
                        "أحسنت! أكملت مهمة",
                        "${t.title} \u2705 حصلت على 30 نقطة XP. واصل التقدم!",
                    )
                    // إتمام كل المهام هو *شرط* اليوم — نمرّره لمقيّم الحماسة
                    // الذي يتحقق من الشروط الحقيقية قبل احتساب اليوم.
                    evaluateStreakDay()
                    syncNotifState()
                }
            }
        }
    }

    /** Explicitly flush pending state to disk immediately (e.g. on app pause). */
    fun flush() = persist()

    val tasksDone: Int get() = dailyTasks.count { it.done }

    // ======================================================================
    //  احتساب يوم الحماسة — بشروط حقيقية لا بمجرد لمس زر
    // ======================================================================
    // القاعدة القديمة: أي درس مكتمل ⇒ اليوم محسوب فوراً. النتيجة: سلسلة
    // متضخّمة لا تعكس جهداً حقيقياً.
    //
    // القاعدة الجديدة: اليوم لا يُحتسب إلا بعد تحقّق **شرط اليوم** فعلياً،
    // وهو أحد أمرين:
    //   • إتمام كل مهام الخطة المتكيّفة (وهي مهام واقعية بحجم مناسب), أو
    //   • بلوغ حد النشاط الحقيقي المقاس من التيليمتري (احتياط للمستخدم
    //     الذي يدرس خارج الخطة).
    // وبما أن الخطة نفسها تتكيّف مع مستوى المتعلّم، فالشرط عادل من اليوم الأول.

    /** هل حُسب اليوم الحالي ضمن السلسلة؟ (يُصفَّر عند تغيّر اليوم) */
    var dayEarnedStreak by mutableStateOf(false)
        private set

    /**
     * الحد الأدنى من النشاط المقاس الذي يُعتبر "يوم دراسة" حتى لو لم تُكمل
     * الخطة. يتكيّف مع مرحلة المتعلّم بدل أن يكون رقماً ثابتاً.
     */
    val streakActivityThreshold: Int
        get() = when (learnerTier) {
            LearnerTier.SEED -> 3        // 3 نقاط نشاط تكفي في يوم التأسيس
            LearnerTier.SPROUT -> 6
            LearnerTier.GROWING -> 10
            LearnerTier.ESTABLISHED -> 14
        }

    /** نقاط نشاط اليوم المقاسة من التيليمتري (مراجعات + دروس + قراءة…). */
    val todayActivityScore: Int
        get() {
            val t = todayStat
            return t.reviews +
                t.lessonsCompleted * 5 +
                t.examsTaken * 6 +
                t.storiesRead * 3 +
                t.wordsAdded * 2 +
                t.conversationTurns +
                t.studyMinutes / 5
        }

    /** هل تحقّق شرط اليوم؟ (خطة مكتملة أو نشاط كافٍ) */
    val streakConditionMet: Boolean
        get() = planCompleteToday || todayActivityScore >= streakActivityThreshold

    /** وصف الشرط المتبقّي — يُعرض للمستخدم بشفافية. */
    val streakConditionLabel: String
        get() = when {
            dayEarnedStreak -> "تم تأمين يومك ✅"
            planCompleteToday -> "أنهيت خطة اليوم"
            activeDailyTasks.isEmpty() -> "أضف محتوى لتبدأ خطة اليوم"
            else -> {
                val remaining = activeDailyTasks.size - activeTasksDone
                "أكمل $remaining " +
                    (if (remaining == 1) "مهمة" else "مهام") +
                    " لتأمين يومك"
            }
        }

    /** تقدّم شرط اليوم 0..1 — لشريط التقدّم في رأس الشاشة. */
    val streakConditionProgress: Float
        get() {
            if (dayEarnedStreak || streakConditionMet) return 1f
            val byPlan = if (activeDailyTasks.isEmpty()) 0f
            else activeTasksDone.toFloat() / activeDailyTasks.size
            val byActivity = if (streakActivityThreshold <= 0) 0f
            else todayActivityScore.toFloat() / streakActivityThreshold
            return maxOf(byPlan, byActivity).coerceIn(0f, 1f)
        }

    /**
     * يقيّم شرط اليوم ويحتسبه في السلسلة عند تحقّقه (مرة واحدة فقط).
     * يُستدعى بعد كل نشاط مؤثّر.
     */
    fun evaluateStreakDay() {
        if (dayEarnedStreak) return
        if (!streakConditionMet) return
        dayEarnedStreak = true
        // السلسلة تبقى مشتقّة من التيليمتري (لا تُزاد بشكل أعمى) لتظل صادقة
        // عبر إعادة التثبيت والاستعادة والأيام الفائتة.
        streak = Telemetry.currentStreak(dayStats).coerceAtLeast(1)
        grantXp(40)
        com.zmastery.english.notify.Notifier.achievement(
            getApplication(),
            "يوم مؤمَّن! \uD83D\uDD25",
            "حقّقت شرط اليوم وسلسلتك الآن $streak " +
                (if (streak == 1) "يوم" else "أيام") + " متتالية. إنجاز رائع!",
        )
        syncNotifState()
        persist()
    }

    // ======================================================================
    //  Engagement — honest, derived learner state
    // ======================================================================

    /** True once the learner has any real content (lessons or dictionary words). */
    val hasContent: Boolean get() = activeVocab.isNotEmpty() || lessons.isNotEmpty()

    /** True once any activity has ever been recorded. */
    val hasHistory: Boolean get() = dayStats.values.any { it.isActive }

    /** Today's progress toward the review goal, 0..1. */
    val todayGoalProgress: Float
        get() = if (dailyGoal <= 0) 0f
        else (totalReviewsToday.toFloat() / dailyGoal).coerceIn(0f, 1f)

    /**
     * Daily tasks that are actually ACHIEVABLE right now.
     *
     * A fresh install has no words and no lessons, so "review 20 words" is not
     * a real task — showing it (and counting it toward completion) is what made
     * the app claim the plan was finished on day one.
     */
    val activeDailyTasks: List<DailyTask>
        get() = dailyTasks.filter { t ->
            when (t.id) {
                "review" -> activeVocab.isNotEmpty()
                "lesson" -> lessons.any { !it.isCompleted }
                "quiz" -> activeVocab.size >= 4
                "story" -> storySeedCount >= 2 || todayStory != null
                "speak" -> lessons.isNotEmpty()
                else -> true
            }
        }

    val activeTasksDone: Int get() = activeDailyTasks.count { it.done }

    /** True only when there is real work scheduled AND all of it is finished. */
    val planCompleteToday: Boolean
        get() = activeDailyTasks.isNotEmpty() && activeTasksDone >= activeDailyTasks.size

    /** The single derived snapshot every engagement surface reads. */
    val engagement: Engagement
        get() = EngagementEngine.derive(
            rows = dayStats,
            hasContent = hasContent,
            goalProgress = todayGoalProgress,
            planDone = planCompleteToday,
            planHasTasks = activeDailyTasks.isNotEmpty(),
        )

    /** Last 7 days as (date, wasActive) for the streak dot rail. */
    val weekDots: List<Pair<java.time.LocalDate, Boolean>>
        get() = EngagementEngine.weekDots(dayStats)

    // ======================================================================
    //  المرحلة الأولى — 3D Momentum Model
    // ======================================================================
    // Three independent axes so one missed day can never erase the whole story:
    //   🔥 streak (engine) · 🌱 continuity (shield) · ⭐ mastery (quality gauge)

    /** Learner's display name — personalises the AI wisdom cards. */
    var learnerName by mutableStateOf("")

    /** Learner's email (for future Firebase Auth integration). */
    var learnerEmail by mutableStateOf("")

    /** The chosen micro-habit (الورد اليومي) that keeps the streak winnable. */
    var microHabitId by mutableStateOf(MicroHabits.all.first().id)
        private set

    /** Progress on today's micro-habit. */
    var microHabitProgress by mutableStateOf(0)
        private set

    /** Epoch-day a Streak Freeze was last spent (for the UI notice). */
    var lastFreezeUsedDay by mutableStateOf(0L)
        private set

    val microHabit: MicroHabit
        get() = MicroHabits.byId(microHabitId) ?: MicroHabits.all.first()

    val microHabitDone: Boolean get() = microHabitProgress >= microHabit.target

    val microHabitFraction: Float
        get() = if (microHabit.target <= 0) 0f
        else (microHabitProgress.toFloat() / microHabit.target).coerceIn(0f, 1f)

    fun setMicroHabit(id: String) {
        if (id == microHabitId) return
        microHabitId = id
        microHabitProgress = 0
        persist()
    }

    /**
     * Advance the micro-habit. Called by real activity (a review, a listen, a
     * story page, a new word) so the 3–5 minute daily ورد completes naturally.
     */
    fun advanceMicroHabit(id: String, amount: Int = 1) {
        if (id != microHabitId) return
        if (microHabitDone) return
        microHabitProgress = (microHabitProgress + amount).coerceAtMost(microHabit.target)
        if (microHabitDone) {
            grantXp(15)
            com.zmastery.english.notify.Notifier.achievement(
                getApplication(),
                "الورد اليومي مكتمل \uD83D\uDD25",
                "${microHabit.title} — سلسلتك محمية اليوم. +15 XP",
            )
            checkChests()
            syncMysteryRewards()
        }
        persist()
    }

    /** The single derived 3D snapshot every momentum surface reads. */
    val momentum: Momentum3D
        get() = MomentumEngine.derive(
            rows = dayStats,
            microHabitDone = microHabitDone,
            masteredWords = masteredCount,
            totalWords = totalWords,
            lessonsDone = completedLessons,
            totalLessons = totalLessons,
            examAvg = lifetime.examAvg,
        )

    /** The 30 continuity cells (oldest → today) for the shield visual. */
    val continuityCells: List<Boolean>
        get() = MomentumEngine.continuityCells(dayStats)

    // ======================================================================
    //  المرحلة الثانية — Seven Seals (Z-Mastery Enigma)
    // ======================================================================

    /** tierId -> the record of it being opened. */
    val openedChests = mutableStateMapOf<String, ChestRecord>()

    /** Everything the learner currently owns. */
    var wallet by mutableStateOf(PerkWallet())
        private set

    /**
     * The streak that QUALIFIES for seals.
     *
     * Deliberately the learner's BEST streak, not the current one: a seal that
     * was genuinely earned must never be revoked by a later missed day. Taking
     * a reward away is precisely the punishment the 3D model exists to prevent.
     */
    val chestQualifyingStreak: Int
        get() = maxOf(momentum.streak, momentum.bestStreak)

    /** Chests whose day requirement is met but which are still SEALED. */
    val pendingChests: List<ChestTier>
        get() = SevenSeals.earned(chestQualifyingStreak).filter { it.id !in openedChests }

    val hasPendingChest: Boolean get() = pendingChests.isNotEmpty()

    /** The next seal being worked toward. */
    val nextChest: ChestTier? get() = SevenSeals.next(chestQualifyingStreak)

    val chestProgress: Float get() = SevenSeals.progressToNext(chestQualifyingStreak)

    /** Days remaining until the next seal breaks (measured on the live streak). */
    val daysToNextChest: Int
        get() = nextChest?.let { (it.day - momentum.streak).coerceAtLeast(0) } ?: 0

    fun isChestOpened(tierId: String): Boolean = tierId in openedChests

    fun chestRecord(tierId: String): ChestRecord? = openedChests[tierId]

    /** Fires a notification when a new seal becomes available. */
    private fun checkChests() {
        val pending = pendingChests
        if (pending.isEmpty()) return
        val tier = pending.last()
        com.zmastery.english.notify.Notifier.achievement(
            getApplication(),
            "صندوق مجهول ينتظرك! \uD83C\uDF81",
            "${tier.name} (${tier.rarity.label}) — افتحه لتكتشف ما بداخله.",
        )
    }

    /**
     * Open a sealed chest: reveal the AI wisdom card and APPLY every perk.
     * Idempotent — reopening returns the cached record without re-granting.
     */
    fun openChest(tierId: String): ChestRecord? {
        val tier = SevenSeals.byId(tierId) ?: return null
        openedChests[tierId]?.let { return it }
        // Qualify on the BEST streak so an earned seal is never taken back.
        if (chestQualifyingStreak < tier.day) return null

        val m = momentum
        val wisdom = WisdomCards.forTier(tier, learnerName, m, totalWords)

        // ---- apply the perks ----
        var w = wallet
        tier.rewards.forEach { r ->
            when (r.kind) {
                PerkKind.XP_BONUS -> grantXp(r.amount, applyMultiplier = false)
                PerkKind.XP_MULTIPLIER -> {
                    val until = System.currentTimeMillis() + r.amount * 60_000L
                    w = w.copy(xpMultiplierUntil = maxOf(w.xpMultiplierUntil, until))
                }
                PerkKind.STREAK_FREEZE ->
                    w = w.grantFreezes(r.amount.coerceAtLeast(1))
                PerkKind.THEME ->
                    w = w.copy(themesUnlocked = (w.themesUnlocked + tier.id).distinct())
                PerkKind.UNLOCK_ZONE ->
                    w = w.copy(zonesUnlocked = (w.zonesUnlocked + tier.id).distinct())
                PerkKind.VOICE ->
                    w = w.copy(voicesUnlocked = (w.voicesUnlocked + tier.id).distinct())
                PerkKind.SPONSOR ->
                    w = w.copy(sponsorGifts = w.sponsorGifts + r.amount.coerceAtLeast(1))
                PerkKind.LEGACY -> w = w.copy(legacyUnlocked = true)
                PerkKind.AI_REPORT -> Unit // the wisdom text IS the report
            }
        }
        w = w.copy(badges = (w.badges + tier.badge).distinct())
        wallet = w

        val rec = ChestRecord(
            tierId = tier.id,
            openedEpochDay = Telemetry.today(),
            streakAtOpen = m.streak,
            wisdom = wisdom,
        )
        openedChests[tier.id] = rec
        persist()
        return rec
    }

    // ======================================================================
    //  الصناديق الغامضة — الحالة والمؤشرات الثلاثة
    // ======================================================================
    // نظام مستقل خفيف يعمل بجوار السبعة أختام: يحفظ حالة كل صندوق
    // (خامل / مستحق / مفتوح) مع تقريره المولَّد، ولا يُعيد قفل شيء أبداً.

    /** كل الصناديق الغامضة بحالتها الحالية. */
    val mysteryRewards = mutableStateListOf<MysteryReward>()

    /** آخر صندوق فُتح للتو — يقود نافذة الكشف الاحتفالية. */
    var justOpenedReward by mutableStateOf<MysteryReward?>(null)

    /**
     * المؤشرات الثلاثة (🔥 شعلة · 🌱 استمرارية · ⭐ إتقان).
     * دالة get() نقيّة تُشتق من الصفوف اليومية، فلا تتعارض أبداً مع الواقع.
     */
    val metrics: MomentumMetrics
        get() = MomentumMetricsEngine.computeMetrics(
            rows = dayStats,
            masteredWords = masteredCount,
            totalWords = totalWords,
            lessonsDone = completedLessons,
            totalLessons = totalLessons,
            examAvg = lifetime.examAvg,
        )

    /** الصناديق المستحقة والمختومة (جاهزة للكسر). */
    val sealedRewards: List<MysteryReward>
        get() = mysteryRewards.filter { it.isSealed }

    val hasSealedReward: Boolean get() = sealedRewards.isNotEmpty()

    val openedRewardCount: Int get() = mysteryRewards.count { it.isOpened }

    /** المعلم التالي الذي يسعى إليه المتعلّم. */
    val nextMilestone: MysteryCatalog.Milestone?
        get() = MysteryCatalog.next(chestQualifyingStreak)

    /** التقدّم 0..1 نحو المعلم التالي. */
    val milestoneProgress: Float
        get() = MysteryCatalog.progressToNext(chestQualifyingStreak)

    /**
     * يزامن الصناديق مع السلسلة الحالية. يُستدعى عند كل نشاط حقيقي.
     * لا يُعيد قفل صندوق مفتوح ولا يسحب استحقاقاً سابقاً.
     */
    fun syncMysteryRewards(notify: Boolean = true) {
        val before = mysteryRewards.count { it.isSealed }
        val synced = MysteryCatalog.sync(
            existing = mysteryRewards.toList(),
            streak = chestQualifyingStreak,
            todayDay = Telemetry.today(),
        )
        if (synced != mysteryRewards.toList()) {
            mysteryRewards.clear()
            mysteryRewards.addAll(synced)
            persist()
        }
        val after = mysteryRewards.count { it.isSealed }
        if (notify && after > before) {
            mysteryRewards.lastOrNull { it.isSealed }?.let { r ->
                com.zmastery.english.notify.Notifier.achievement(
                    getApplication(),
                    "صندوق غامض ينتظرك! \uD83C\uDF81",
                    "${r.title} (${r.rarity.label}) — اكسر الختم لتكتشف ما بداخله.",
                )
            }
        }
    }

    /** يمنح صندوق إتمام كورس (يُستدعى عند إنهاء آخر درس في الكورس). */
    fun grantCourseReward(courseId: Int, courseName: String, lessonCount: Int) {
        val key = "course_$courseId"
        if (mysteryRewards.any { it.key == key }) return
        mysteryRewards.add(
            MysteryCatalog.buildCourseChest(courseId, courseName, lessonCount, Telemetry.today())
        )
        com.zmastery.english.notify.Notifier.achievement(
            getApplication(),
            "صندوق إتمام الكورس! \uD83C\uDF93",
            "$courseName — اكسر الختم لتستلم شارتك.",
        )
        persist()
    }

    /**
     * يكسر ختم صندوق ويطبّق جوائزه. عملية idempotent تماماً:
     * إعادة الفتح تُعيد نفس الصندوق دون منح الجوائز مرة أخرى.
     */
    fun openMysteryReward(id: String): MysteryReward? {
        val idx = mysteryRewards.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val r = mysteryRewards[idx]
        if (r.isOpened) return r
        if (r.isDormant) return null

        // تقرير محلّي فوري: يُعرض بلا انتظار، ثم يُستبدل بتقرير Gemini إن توفّر.
        val html = OfflineMirrorHtml.build(mirrorStatsFor(r.title))

        // ---- منح الجوائز ----
        if (r.xpAwarded > 0) grantXp(r.xpAwarded, applyMultiplier = false)
        var w = wallet
        r.themeUnlockKey?.let { w = w.copy(themesUnlocked = (w.themesUnlocked + it).distinct()) }
        w = w.copy(badges = (w.badges + "${r.badgeEmoji} ${r.title}").distinct())
        // الصناديق النادرة فأعلى تمنح درع تجميد سلسلة إضافياً.
        if (r.rarity.ordinal >= RewardRarity.RARE.ordinal) {
            w = w.grantFreezes(1)
        }
        wallet = w

        val opened = r.copy(
            isOpened = true,
            openedAt = System.currentTimeMillis(),
            descriptionHtmlAr = html,
        )
        mysteryRewards[idx] = opened
        justOpenedReward = opened
        persist()

        // ثم نرقّي التقرير بالذكاء الاصطناعي في الخلفية (إن وُجد مفتاح وإنترنت).
        upgradeRewardHtmlWithAi(opened.id, opened.title)
        return opened
    }

    // ======================================================================
    //  الجزء 4 · مرآة الإدراك بالذكاء الاصطناعي (Gemini 2.0 Flash)
    // ======================================================================

    /** true أثناء توليد تقرير الصندوق بالذكاء الاصطناعي. */
    var isChestMirrorLoading by mutableStateOf(false)
        private set

    /** معرّف الصندوق الذي يُولَّد تقريره الآن (لعرض مؤشّر داخل بطاقته). */
    var chestMirrorLoadingId by mutableStateOf<String?>(null)
        private set

    /** يجمع الإحصاءات الحقيقية التي تُغذّي المطالبة. */
    fun mirrorStatsFor(milestoneTitle: String): MirrorStats {
        val mt = metrics
        return MirrorStats(
            learnerName = learnerName,
            milestoneTitle = milestoneTitle,
            streakDays = mt.dailyStreak,
            masteredWords = masteredCount,
            totalWords = totalWords,
            lessonsCompleted = completedLessons,
            studyMinutes = (studyHours * 60).toInt().coerceAtLeast(0),
            recallRate = trueRecallRate,
            continuityPercent = mt.continuityPercent,
            masteryPercent = mt.masteryPercent,
            cefr = mt.cefr,
            nextCefr = mt.nextCefr,
            bestStreak = mt.bestStreak,
        )
    }

    /**
     * يستبدل التقرير المحلّي بتقرير Gemini المخصّص، ويحفظه داخل الصندوق نهائياً.
     * أي فشل يُترك بصمت — التقرير المحلّي الجميل معروض بالفعل.
     */
    private fun upgradeRewardHtmlWithAi(rewardId: String, title: String) {
        if (!hasAiKey) return
        if (isChestMirrorLoading) return
        isChestMirrorLoading = true
        chestMirrorLoadingId = rewardId
        val stats = mirrorStatsFor(title)
        viewModelScope.launch {
            val (html, fromAi) = CognitiveMirrorService.generateHtml(stats, geminiApiKey)
            isChestMirrorLoading = false
            chestMirrorLoadingId = null
            if (!fromAi) return@launch
            val i = mysteryRewards.indexOfFirst { it.id == rewardId }
            if (i < 0) return@launch
            val updated = mysteryRewards[i].copy(descriptionHtmlAr = html)
            mysteryRewards[i] = updated
            if (justOpenedReward?.id == rewardId) justOpenedReward = updated
            persist()
        }
    }

    /** إعادة توليد تقرير صندوق مفتوح يدوياً (زر "أعد التوليد"). */
    fun regenerateRewardMirror(rewardId: String) {
        val i = mysteryRewards.indexOfFirst { it.id == rewardId }
        if (i < 0) return
        val r = mysteryRewards[i]
        if (!r.isOpened) return
        if (!hasAiKey) {
            // بلا مفتاح: نعيد بناء التقرير المحلّي بأحدث الأرقام.
            val fresh = OfflineMirrorHtml.build(mirrorStatsFor(r.title))
            mysteryRewards[i] = r.copy(descriptionHtmlAr = fresh)
            persist()
            return
        }
        upgradeRewardHtmlWithAi(rewardId, r.title)
    }

    /** يمسح إشارة "فُتح للتو" بعد انتهاء مراسم الاحتفال. */
    fun clearJustOpened() { justOpenedReward = null }

    // ======================================================================
    //  المرحلة الثالثة — مرآة الإدراك (AI Cognitive Mirroring)
    // ======================================================================

    /** tierId -> التقرير المولّد (يُخزَّن فلا يتغيّر عند إعادة الفتح). */
    val mirrorReports = mutableStateMapOf<String, MirrorReport>()

    /** جارٍ توليد تقرير من Gemini الآن. */
    var isMirrorLoading by mutableStateOf(false)
        private set

    /** آخر رسالة حالة لتوليد المرآة. */
    var mirrorMessage by mutableStateOf<String?>(null)

    /**
     * البصمة المعرفية الحيّة — مشتقّة بالكامل من التيليمتري.
     * دالة get() نقيّة: لا تُخزَّن فلا تتعارض أبداً مع الواقع.
     */
    val cognitiveMirror: CognitiveMirror
        get() = EnigmaStreakEngine.computeMirror(
            logs = reviewLogs,
            hours = reviewHours,
            leeches = forgottenWords,
            dayRows = dayStats,
        )

    fun mirrorReport(tierId: String): MirrorReport? = mirrorReports[tierId]

    /**
     * يولّد تقرير مرآة الإدراك لصندوق مفتوح.
     * يستدعي Gemini عند توفّر مفتاح، وإلا يولّد التقرير محلياً فوراً.
     * النتيجة تُحفظ فلا تُستهلك حصة الـ API مرتين لنفس الصندوق.
     */
    fun generateMirrorReport(tierId: String, force: Boolean = false) {
        if (!force && mirrorReports.containsKey(tierId)) return
        if (isMirrorLoading) return
        val tier = SevenSeals.byId(tierId) ?: return
        val m = cognitiveMirror
        val mo = momentum
        isMirrorLoading = true
        mirrorMessage = null
        viewModelScope.launch {
            val report = MirrorService.generate(
                m = m,
                mo = mo,
                name = learnerName,
                totalWords = totalWords,
                masteredWords = masteredCount,
                leechSamples = forgottenWords.take(5).map { it.english },
                tierName = tier.name,
                apiKey = geminiApiKey,
                stamp = nowStamp(),
            )
            mirrorReports[tierId] = report
            isMirrorLoading = false
            mirrorMessage = if (report.local) {
                "تم التوليد محلياً — أضف مفتاح Gemini لتحليل أعمق"
            } else {
                "تم توليد مرآة الإدراك بالذكاء الاصطناعي"
            }
            persist()
        }
    }

    // ======================================================================
    //  المرحلة الرابعة — هندسة الخوف من السقوط والتعافي
    // ======================================================================

    /** أعلى سلسلة قبل الانكسار — مرجع الاستعادة. */
    var streakBeforeBreak by mutableStateOf(0)
        private set

    /** مهمة الإنقاذ النشطة. */
    var rescue by mutableStateOf(RescueMission())
        private set

    /** آخر يوم عُرضت فيه مهمة إنقاذ. */
    var lastRescueOfferDay by mutableStateOf(0L)
        private set

    /**
     * حالة "صندوق اليوم الباكي".
     * تُقرأ عند كل إعادة تركيب — لا مؤقتات ولا خدمات، فلا استهلاك بطارية.
     */
    val decayState: DecayState
        get() {
            val now = java.time.LocalTime.now()
            return EnigmaStreakEngine.computeDecay(
                minimumDone = microHabitDone || tasksDone >= dailyTasks.size,
                currentStreak = momentum.streak,
                hourNow = now.hour,
                minuteNow = now.minute,
                streakBroken = rescue.isActive && !rescue.completed,
            )
        }

    val isChestCracking: Boolean get() = decayState.isCracking

    /**
     * هل تُعرض بوابة الإنقاذ البنفسجية الآن؟
     *
     * المهمة صالحة ليومين فقط من تاريخ عرضها: بعد ذلك تنتهي صلاحيتها بهدوء
     * فلا تبقى بوابة قديمة معلّقة على الواجهة إلى الأبد. (المهمة المكتملة
     * تبقى معروضة دائماً حتى يستلم المتعلّم مكافأته.)
     */
    val showRescueGate: Boolean
        get() {
            val r = rescue
            if (!r.isActive) return false
            if (r.completed) return true
            return (Telemetry.today() - r.offeredEpochDay) <= RESCUE_VALID_DAYS
        }

    private val RESCUE_VALID_DAYS = 2L

    /**
     * يُستدعى عند تدوير اليوم: إذا انكسرت سلسلة معتبرة (> 2 أيام) ولم يحمها
     * درع، نطلق مهمة إنقاذ بدل إظهار واجهة لوم.
     */
    private fun maybeOfferRescue(brokenStreak: Int) {
        val today = Telemetry.today()
        if (brokenStreak <= 2) return              // لا شيء يستحق الإنقاذ
        if (lastRescueOfferDay == today) return     // عُرضت اليوم بالفعل
        if (rescue.isActive) return                 // مهمة قائمة
        streakBeforeBreak = brokenStreak
        lastRescueOfferDay = today
        rescue = RescueMission(
            offeredEpochDay = today,
            streakToRestore = brokenStreak,
            kind = EnigmaStreakEngine.pickRescueKind(cognitiveMirror).name,
        )
        com.zmastery.english.notify.Notifier.achievement(
            getApplication(),
            "مهمة إنقاذ عاجلة! \uD83D\uDFE3",
            "سلسلتك ($brokenStreak يوماً) قابلة للاستعادة — أنجز مهمة قصيرة الآن.",
        )
    }

    /**
     * يبدأ العدّ التنازلي (3 دقائق) ويصفّر التقدّم.
     * يُستدعى من زر «ابدأ الإنقاذ» قبل الانتقال لشاشة المراجعة.
     */
    fun startRescueTimer() {
        val r = rescue
        if (!r.isActive || r.completed) return
        rescue = r.copy(progress = 0, startedAtMs = System.currentTimeMillis())
        persist()
    }

    /**
     * يُنهي محاولة انتهت مهلتها: يصفّر العدّاد ويسمح بإعادة المحاولة فوراً.
     * لا عقوبة ولا فقدان — الهدف هو الإلحاح لا الإحباط.
     */
    fun timeoutRescue() {
        val r = rescue
        if (!r.isRunning) return
        if (!r.isExpired()) return
        rescue = r.copy(progress = 0, startedAtMs = 0L, timeouts = r.timeouts + 1)
        persist()
    }

    /** يسجّل تقدّماً في مهمة الإنقاذ (يُستدعى من شاشة المراجعة). */
    fun advanceRescue(amount: Int = 1) {
        val r = rescue
        if (!r.isActive || r.completed) return
        // لا يُحتسب التقدّم قبل بدء العدّاد أو بعد انتهاء المهلة.
        if (!r.isRunning) return
        if (r.isExpired()) {
            timeoutRescue()
            return
        }
        val p = (r.progress + amount).coerceAtMost(r.target)
        rescue = r.copy(progress = p, completed = p >= r.target)
        if (rescue.completed) {
            com.zmastery.english.notify.Notifier.achievement(
                getApplication(),
                "أنقذت شعلتك! \uD83D\uDD25",
                "المهمة اكتملت — استلم سلسلتك المستعادة الآن.",
            )
        }
        persist()
    }

    /**
     * استلام مكافأة الإنقاذ: تعود الشعلة القديمة كاملة.
     * @return السلسلة المستعادة، أو 0 عند الفشل.
     */
    fun claimRescue(): Int {
        val r = rescue
        if (!r.isActive || !r.completed || r.claimed) return 0
        val restored = r.streakToRestore.coerceAtLeast(1)
        streak = restored
        rescue = r.copy(claimed = true)
        grantXp(60)
        // درع مجاني: مكافأة على العودة، ويحمي من انتكاسة فورية.
        wallet = wallet.grantFreezes(1)
        motivationLevel = (motivationLevel + 0.15f).coerceAtMost(1f)
        persist()
        return restored
    }

    /** تجاهل مهمة الإنقاذ (يبقى الخيار للمتعلّم دائماً). */
    fun dismissRescue() {
        rescue = rescue.copy(claimed = true)
        persist()
    }

    /** Spend a sponsor gift (rewards generosity — a real retention driver). */
    fun useSponsorGift(): Boolean {
        if (wallet.sponsorGifts <= 0) return false
        wallet = wallet.copy(sponsorGifts = wallet.sponsorGifts - 1)
        persist()
        return true
    }

    /**
     * Central XP grant. Honours an active ×2 multiplier so the Seven Seals perk
     * is genuinely felt. All XP should flow through here.
     */
    fun grantXp(amount: Int, applyMultiplier: Boolean = true) {
        if (amount <= 0) return
        val mult = if (applyMultiplier && wallet.multiplierActive()) 2 else 1
        val gained = amount * mult
        xp += gained
        track { it.xpEarned += gained }
    }

    // ----- Derived stats -----
    /** Words that have been approved into the active dictionary. */
    val activeVocab: List<VocabWord> get() = vocab.filter { !it.pendingApproval }

    /** Words still awaiting approval for a given lesson. */
    fun pendingWordsForLesson(lessonId: Int): List<VocabWord> =
        vocab.filter { it.lessonId == lessonId && it.pendingApproval }

    /**
     * A word is due when its predicted retrievability has dropped to (or below)
     * the desired retention. New words (never reviewed) are always due.
     * Pending (unapproved) words are excluded.
     */
    val dueWords: List<VocabWord>
        get() {
            val today = todayEpochDay()
            return activeVocab.filter { w ->
                when {
                    w.totalReviews == 0 -> true
                    else -> {
                        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()
                        Fsrs.retrievability(elapsed, w.stability) <= desiredRetention
                    }
                }
            }
        }

    /** Leeches — words forgotten repeatedly (high lapses, low stability). */
    val forgottenWords: List<VocabWord>
        get() = activeVocab.filter { it.lapses >= 2 && it.stability < 7.0 }
            .sortedByDescending { it.lapses }
            .take(5)

    val masteredCount: Int get() = activeVocab.count { it.mastered }
    val totalWords: Int get() = activeVocab.size

    /**
     * Approve a subset of a lesson's pending words into the active dictionary,
     * and discard the rest. Called from the word-approval sheet on completion.
     */
    fun approveWords(lessonId: Int, approvedIds: Set<Int>) {
        // Remove rejected pending words for this lesson.
        vocab.removeAll { it.lessonId == lessonId && it.pendingApproval && it.id !in approvedIds }
        // Mark approved ones active.
        approvedIds.forEach { id ->
            val i = vocab.indexOfFirst { it.id == id }
            if (i >= 0 && vocab[i].pendingApproval) vocab[i] = vocab[i].copy(pendingApproval = false)
        }
        persist()
    }
    val completedLessons: Int get() = lessons.count { it.isCompleted }
    val totalLessons: Int get() = lessons.size
    var accuracy by mutableStateOf(0) // exam accuracy avg (updated by quiz)
        private set

    fun recordExamResult(correct: Int, total: Int) {
        if (total <= 0) return
        val pct = correct * 100 / total
        accuracy = if (accuracy == 0) pct else (accuracy + pct) / 2
        studyHours += 0.1
        completeTask("quiz")
        persist()
    }

    // ======================================================================
    //  Exams — studied, weakness-driven assessment
    // ======================================================================

    /** Finished exams, newest last. Powers history + the trend chart. */
    val examHistory = mutableStateListOf<ExamRecord>()

    /** wordId → how many times it was answered wrong in an exam. */
    val examMisses = mutableStateMapOf<Int, Int>()

    /** The exam currently being taken (empty = none in progress). */
    val examQuestions = mutableStateListOf<ExamQuestion>()
    var examMode by mutableStateOf(ExamMode.SMART)
        private set
    var examTitle by mutableStateOf("")
        private set
    var examQuestionCount by mutableStateOf(10)

    /** Lessons the learner has actually completed — the only exam material. */
    val doneLessons: List<Lesson> get() = lessons.filter { it.isCompleted }

    /** Courses that have at least one completed lesson. */
    val examableCourses: List<Course>
        get() {
            val ids = doneLessons.map { it.courseId }.toSet()
            return courses.filter { it.id in ids }
        }

    /** Completed lessons for a course, ordered. */
    fun completedLessonsOf(courseId: Int): List<Lesson> =
        doneLessons.filter { it.courseId == courseId }.sortedBy { it.no }

    /** Words eligible for exams (approved + from completed lessons or reviewed). */
    val examableWords: List<VocabWord>
        get() {
            val done = doneLessons.map { it.id }.toSet()
            return activeVocab.filter { it.lessonId == 0 || it.lessonId in done || it.totalReviews > 0 }
        }

    private fun examSource(): ExamSource = ExamSource(
        words = examableWords,
        lessons = lessons.toList(),
        courses = courses.toList(),
        misses = examMisses.toMap(),
        desiredRetention = desiredRetention,
        todayEpochDay = todayEpochDay(),
    )

    /** Weakness-ranked words — drives the "نقاط ضعفك" panel. */
    val weakestWords: List<WeakWord>
        get() = ExamBuilder.rankWords(examSource()).take(20)

    /** True when there is enough studied material for a given mode. */
    fun canTakeExam(mode: ExamMode): Boolean {
        val src = examSource()
        if (src.words.size < ExamBuilder.MIN_WORDS && mode != ExamMode.GRAMMAR && mode != ExamMode.CONVERSATION) return false
        return when (mode) {
            ExamMode.GRAMMAR -> doneLessons.any { it.quiz.isNotEmpty() }
            ExamMode.CONVERSATION -> doneLessons.any { it.dialogues.isNotEmpty() }
            ExamMode.WEAKNESS -> src.words.size >= ExamBuilder.MIN_WORDS
            ExamMode.LESSON -> doneLessons.isNotEmpty()
            ExamMode.COURSE -> examableCourses.isNotEmpty()
            else -> src.words.size >= ExamBuilder.MIN_WORDS
        }
    }

    /** How many questions a mode could actually produce right now (for badges). */
    fun examAvailability(mode: ExamMode): Int {
        val src = examSource()
        return when (mode) {
            ExamMode.GRAMMAR -> doneLessons.sumOf { it.quiz.size }
            ExamMode.CONVERSATION -> doneLessons.sumOf { it.dialogues.size }
            ExamMode.WEAKNESS -> ExamBuilder.rankWords(src).count { it.weakness >= 0.32f }
            ExamMode.LISTENING -> src.words.size
            else -> src.words.size
        }
    }

    /**
     * Build and start an exam. Returns the number of questions created
     * (0 = not enough material).
     */
    fun startExam(
        mode: ExamMode,
        count: Int = examQuestionCount,
        courseId: Int? = null,
        lessonId: Int? = null,
    ): Int {
        val qs = ExamBuilder.build(
            src = examSource(),
            mode = mode,
            count = count.coerceIn(4, 40),
            courseId = courseId,
            lessonId = lessonId,
        )
        examQuestions.clear()
        examQuestions.addAll(qs)
        examMode = mode
        examTitle = when {
            lessonId != null -> lessons.firstOrNull { it.id == lessonId }?.title ?: mode.label
            courseId != null -> courses.firstOrNull { it.id == courseId }?.name ?: mode.label
            else -> mode.label
        }
        return qs.size
    }

    fun clearExam() {
        examQuestions.clear()
    }

    /**
     * Record a single answer. A wrong answer on a vocabulary question feeds
     * BOTH the exam-miss counter and the FSRS scheduler, so the word resurfaces
     * in the normal review queue — exams genuinely drive learning.
     */
    fun recordExamAnswer(q: ExamQuestion, correct: Boolean) {
        if (q.wordId > 0) {
            if (!correct) {
                examMisses[q.wordId] = (examMisses[q.wordId] ?: 0) + 1
                // Treat an exam miss as a real lapse: halve stability, make due now.
                val i = vocab.indexOfFirst { it.id == q.wordId }
                if (i >= 0) {
                    val w = vocab[i]
                    vocab[i] = w.copy(
                        dueInDays = 0,
                        intervalDays = 0,
                        stability = (w.stability * 0.6).coerceAtLeast(0.1),
                        lapses = w.lapses + 1,
                        phase = if (w.phase == FsrsPhase.NEW) FsrsPhase.NEW else FsrsPhase.RELEARNING,
                    )
                }
            } else {
                // A correct answer under exam pressure is real evidence — decay the
                // miss counter so old mistakes stop dominating future exams.
                val m = examMisses[q.wordId] ?: 0
                if (m > 0) examMisses[q.wordId] = m - 1
            }
        }
        xp += if (correct) 8 + q.difficulty * 4 else 1
    }

    /** Finish an exam: store the record, update accuracy, award XP. */
    fun finishExam(
        correct: Int,
        total: Int,
        durationMs: Long,
        skillCorrect: Map<ExamSkill, Int>,
        skillTotal: Map<ExamSkill, Int>,
    ) {
        if (total <= 0) return
        val rec = ExamRecord(
            id = "ex_${System.currentTimeMillis()}",
            mode = examMode,
            title = examTitle.ifBlank { examMode.label },
            correct = correct, total = total,
            stamp = nowStamp(), durationMs = durationMs,
            skillCorrect = skillCorrect, skillTotal = skillTotal,
        )
        examHistory.add(rec)
        if (examHistory.size > 60) examHistory.removeAt(0)
        val pct = correct * 100 / total
        accuracy = if (accuracy == 0) pct else (accuracy * 2 + pct) / 3
        // NOTE: study seconds are banked by TrackStudyTime on the exam screen,
        // so they are deliberately NOT added again here.
        track {
            it.examsTaken += 1
            it.examScoreSum += pct
            it.mistakes += (total - correct)
        }
        completeTask("quiz")
        if (pct >= 80) {
            com.zmastery.english.notify.Notifier.achievement(
                getApplication(),
                "نتيجة ممتازة! \uD83C\uDFC6",
                "أنهيت \"${rec.title}\" بنسبة $pct%. استمر على هذا المستوى!",
            )
        }
        persist()
    }

    /** Lifetime average across all recorded exams. */
    val examAverage: Int
        get() = if (examHistory.isEmpty()) 0 else examHistory.map { it.pct }.average().toInt()

    val examBest: Int get() = examHistory.maxOfOrNull { it.pct } ?: 0

    /** Aggregated accuracy per skill across all exams — powers the radar list. */
    val skillAccuracy: Map<ExamSkill, Float>
        get() {
            val corr = HashMap<ExamSkill, Int>()
            val tot = HashMap<ExamSkill, Int>()
            examHistory.forEach { r ->
                r.skillTotal.forEach { (s, n) -> tot[s] = (tot[s] ?: 0) + n }
                r.skillCorrect.forEach { (s, n) -> corr[s] = (corr[s] ?: 0) + n }
            }
            return tot.filter { it.value > 0 }
                .mapValues { (s, n) -> (corr[s] ?: 0).toFloat() / n }
        }

    /** The weakest skill overall, or null when there is no data yet. */
    val weakestSkill: ExamSkill?
        get() = skillAccuracy.minByOrNull { it.value }?.key

    // ----- Daily plan (synced from roadmap) -----
    val todayPlan: List<PlanItem>
        get() {
            val result = mutableListOf<PlanItem>()
            // Pick next incomplete lesson from up to N distinct courses
            val byCourse = lessons.filter { !it.isCompleted }.groupBy { it.courseId }
            byCourse.entries.take(lessonsPerDay).forEach { (cid, ls) ->
                val course = courses.firstOrNull { it.id == cid } ?: return@forEach
                val lesson = ls.minByOrNull { it.no } ?: return@forEach
                result.add(PlanItem(cid, course.name, lesson.id, lesson.title, course.accent))
            }
            return result
        }

    // ======================================================================
    //  Curriculum progress (النِسَب الحقيقية)
    // ======================================================================
    // A course's REAL denominator is its curriculum size (`course.target`) —
    // the number of lessons the syllabus says it contains — NOT the number of
    // lessons that happen to be imported right now.
    //
    // Using the imported count made 1 completed lesson out of 16 imported read
    // as 6% of a whole level, even though Level 1 actually holds 116 lessons.
    // Real answer: 1 / 116 ≈ 1%. That is what these functions now return.
    //
    // `target` is authoritative, but it is clamped up by the imported count so
    // an author who ships MORE lessons than the syllabus promised never
    // produces a percentage above 100%.

    /** Curriculum size of a course: the syllabus target, never below what exists. */
    fun courseTotal(courseId: Int): Int {
        val course = courses.firstOrNull { it.id == courseId } ?: return 0
        val imported = lessons.count { it.courseId == courseId }
        return maxOf(course.target, imported)
    }

    /** Completed lesson count for a course. */
    fun courseDone(courseId: Int): Int =
        lessons.count { it.courseId == courseId && it.isCompleted }

    /** Imported (available) lesson count for a course. */
    fun courseImported(courseId: Int): Int = lessons.count { it.courseId == courseId }

    /**
     * Real completion of a course = completed / curriculum size.
     * This is the number shown to the learner as "تقدمي".
     */
    fun courseCompletion(courseId: Int): Float {
        val total = courseTotal(courseId)
        if (total <= 0) return 0f
        return (courseDone(courseId).toFloat() / total).coerceIn(0f, 1f)
    }

    /** How much of the course's content has been imported (content coverage). */
    fun courseCoverage(courseId: Int): Float {
        val total = courseTotal(courseId)
        if (total <= 0) return 0f
        return (courseImported(courseId).toFloat() / total).coerceIn(0f, 1f)
    }

    /**
     * Legacy pair used by several screens: (completed, curriculum total).
     * Now reports the syllabus total instead of the imported total.
     */
    fun courseProgress(courseId: Int): Pair<Int, Int> = courseDone(courseId) to courseTotal(courseId)

    /** Aggregate stats for a whole level, computed over curriculum size. */
    data class LevelStats(
        val done: Int,        // lessons completed
        val imported: Int,    // lessons available on device
        val total: Int,       // curriculum size (sum of course targets)
        val courseCount: Int,
        val coursesStarted: Int,
        val coursesDone: Int,
    ) {
        /** Real progress through the level's curriculum. */
        val completion: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        /** Share of the curriculum that has been imported. */
        val coverage: Float get() = if (total <= 0) 0f else (imported.toFloat() / total).coerceIn(0f, 1f)
        /** Completion measured only against what is actually available. */
        val completionOfImported: Float get() = if (imported <= 0) 0f else (done.toFloat() / imported).coerceIn(0f, 1f)
    }

    fun levelStats(levelId: Int): LevelStats {
        val lvlCourses = courses.filter { it.levelId == levelId }
        var done = 0
        var imported = 0
        var total = 0
        var started = 0
        var finished = 0
        lvlCourses.forEach { c ->
            val d = courseDone(c.id)
            val i = courseImported(c.id)
            val t = courseTotal(c.id)
            done += d; imported += i; total += t
            if (d > 0) started++
            if (t > 0 && d >= t) finished++
        }
        return LevelStats(done, imported, total, lvlCourses.size, started, finished)
    }

    /** Real level completion (completed lessons ÷ full curriculum of the level). */
    fun levelProgress(levelId: Int): Float = levelStats(levelId).completion

    /** Overall completion across every level in the curriculum. */
    val overallCompletion: Float
        get() {
            val total = courses.sumOf { courseTotal(it.id) }
            if (total <= 0) return 0f
            val done = courses.sumOf { courseDone(it.id) }
            return (done.toFloat() / total).coerceIn(0f, 1f)
        }

    fun coursesForLevel(levelId: Int) = courses.filter { it.levelId == levelId }

    fun toggleLesson(lessonId: Int) {
        val idx = lessons.indexOfFirst { it.id == lessonId }
        if (idx >= 0) {
            val l = lessons[idx]
            val nowComplete = !l.isCompleted
            lessons[idx] = l.copy(
                isCompleted = nowComplete,
                // first review scheduled 1 day after completion
                dueInDays = if (nowComplete) 1 else 0,
                intervalDays = if (nowComplete) 1 else 0,
            )
            if (nowComplete) {
                xp += 25
                totalReviewsToday += 5
                // Study seconds come from the real on-screen timer, not a constant.
                track { it.lessonsCompleted += 1; it.xpEarned += 25 }
                completeTask("lesson")
                syncMysteryRewards()
                // صندوق إتمام الكورس: يُمنح عند إنهاء آخر درس في المسار.
                val courseLessons = lessons.filter { it.courseId == l.courseId }
                if (courseLessons.isNotEmpty() && courseLessons.all { it.isCompleted }) {
                    val course = courses.firstOrNull { it.id == l.courseId }
                    if (course != null) {
                        grantCourseReward(course.id, course.name, courseLessons.size)
                    }
                }
            }
            // Reading stories only belong in the archive once completed —
            // this both adds it on completion and removes it if un-marked.
            syncLessonStories()
            persist()
        }
    }

    fun isCompleted(lessonId: Int) = lessons.firstOrNull { it.id == lessonId }?.isCompleted == true

    /** Dictionary words that came from this lesson (approved ones only). */
    fun wordsFromLesson(lessonId: Int): List<VocabWord> =
        vocab.filter { it.lessonId == lessonId && !it.pendingApproval }

    /**
     * Un-complete a lesson (an explicit, confirmed action).
     *
     * @param alsoRemoveWords when true the words this lesson added are deleted
     *        from the dictionary too, along with their mnemonic tiles.
     * @return how many words were removed.
     */
    fun uncompleteLesson(lessonId: Int, alsoRemoveWords: Boolean): Int {
        val idx = lessons.indexOfFirst { it.id == lessonId }
        if (idx < 0) return 0
        val l = lessons[idx]
        if (l.isCompleted) {
            lessons[idx] = l.copy(isCompleted = false, dueInDays = 0, intervalDays = 0)
            // Roll back the completion reward so stats stay honest.
            xp = (xp - 25).coerceAtLeast(0)
        }
        var removed = 0
        if (alsoRemoveWords) {
            val ids = vocab.filter { it.lessonId == lessonId }.map { it.id }
            ids.forEach { MnemonicStore.delete(getApplication(), it) }
            removed = ids.size
            vocab.removeAll { it.lessonId == lessonId }
            if (removed > 0) mnemonicVersion++
        }
        // The reading story must disappear from the archive again since the
        // lesson is no longer marked complete.
        syncLessonStories()
        persist()
        return removed
    }

    // ----- Lesson review (self-assessed spaced repetition) -----
    val lessonsToReview: List<Lesson>
        get() = lessons.filter { it.needsReview }

    /**
     * Records a lesson review. mastery = self-rated recall % (0..100).
     * Higher mastery → longer next interval. Low mastery → review again soon,
     * and (optionally) the forgotten words are pushed back into the word queue.
     */
    fun reviewLesson(lessonId: Int, mastery: Int, forgottenWordIds: List<Int> = emptyList()) {
        val idx = lessons.indexOfFirst { it.id == lessonId }
        if (idx < 0) return
        val l = lessons[idx]
        val prevInterval = l.intervalDays.coerceAtLeast(1)
        // interval factor from mastery: <50% → shrink, 50-80% → grow slowly, >80% → grow fast
        val factor = when {
            mastery < 50 -> 0.6
            mastery < 80 -> 1.8
            else -> 3.0
        }
        val next = (prevInterval * factor).roundToInt().coerceIn(1, 120)
        lessons[idx] = l.copy(
            reviewCount = l.reviewCount + 1,
            lastMastery = mastery,
            intervalDays = next,
            dueInDays = next,
        )
        // Reset forgotten words so they resurface immediately in word review.
        // We halve stability and mark due now (a "manual lapse").
        forgottenWordIds.forEach { wid ->
            val wi = vocab.indexOfFirst { it.id == wid }
            if (wi >= 0) {
                val fw = vocab[wi]
                vocab[wi] = fw.copy(
                    dueInDays = 0,
                    intervalDays = 0,
                    stability = (fw.stability * 0.5).coerceAtLeast(0.1),
                    phase = if (fw.phase == FsrsPhase.NEW) FsrsPhase.NEW else FsrsPhase.RELEARNING,
                )
            }
        }
        totalReviewsToday += 2
        xp += 15 + mastery / 10
        studyHours += 0.05
        persist()
    }

    // ----- Review analytics log (raw data for study improvement + FSRS tuning) -----
    val reviewLogs = mutableStateListOf<ReviewLog>()

    /** سقف الأحداث المحفوظة — يمنع تضخّم ملف الحالة مهما طال الاستخدام. */
    private val SIGNAL_CAP = 400

    // ----- FSRS configuration -----
    /** Target recall probability (Anki default 0.90). User-tunable. */
    var desiredRetention by mutableStateOf(0.90)
    var maxIntervalDays by mutableStateOf(365)
    private val fsrsWeights = Fsrs.DEFAULT_W

    private fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

    /**
     * Records a full 4-stage review using the FSRS scheduler.
     *
     * The recall source maps to an FSRS rating (Again/Hard/Good/Easy). We first
     * apply a *stage penalty*: if the learner only recalled after seeing more
     * hints than the memory strength warranted, the rating is softened — this
     * captures how hard the retrieval actually was, improving accuracy.
     */
    fun reviewWord(
        wordId: Int,
        source: RecallSource,
        reachedStage: Int = 4,
        replays: Int = 0,
        timeMs: Long = 0L,
        explicitGrade: Int? = null,
    ) {
        val idx = vocab.indexOfFirst { it.id == wordId }
        if (idx < 0) return
        val w = vocab[idx]

        // Rating resolution. When [explicitGrade] is supplied the caller already
        // derived the FSRS rating (stage-based quick rating) — use it verbatim so
        // the stage penalty is never applied twice. Otherwise: base rating from
        // the recall source, then softened/rewarded by the stage reached.
        var grade = source.grade
        if (explicitGrade != null) {
            grade = explicitGrade.coerceIn(1, 4)
        } else if (source.grade >= 2) {
            grade = when (reachedStage) {
                1 -> (source.grade + 1).coerceAtMost(4) // recalled from audio only → reward
                2 -> source.grade
                3 -> (source.grade - 1).coerceAtLeast(2)
                else -> (source.grade - 1).coerceAtLeast(1) // full reveal → soften more
            }
        }

        val today = todayEpochDay()
        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()

        val phase = when (w.phase) {
            FsrsPhase.NEW -> Fsrs.Phase.NEW
            FsrsPhase.LEARNING -> Fsrs.Phase.LEARNING
            FsrsPhase.REVIEW -> Fsrs.Phase.REVIEW
            FsrsPhase.RELEARNING -> Fsrs.Phase.RELEARNING
        }

        val sched = Fsrs.schedule(
            w = fsrsWeights,
            rating = grade,
            phase = phase,
            stability = w.stability,
            difficulty = w.difficulty,
            elapsedDays = elapsed,
            desiredRetention = desiredRetention,
            maxInterval = maxIntervalDays,
        )

        val newPhase = when (sched.phase) {
            Fsrs.Phase.NEW -> FsrsPhase.NEW
            Fsrs.Phase.LEARNING -> FsrsPhase.LEARNING
            Fsrs.Phase.REVIEW -> FsrsPhase.REVIEW
            Fsrs.Phase.RELEARNING -> FsrsPhase.RELEARNING
        }

        val reps = if (grade == 1) 0 else w.repetitions + 1
        val lapses = if (grade == 1) w.lapses + 1 else w.lapses
        // "Mastered" = a strong long-term memory (stability ≥ 30d in REVIEW).
        val mastered = newPhase == FsrsPhase.REVIEW && sched.stability >= 30.0
        val newTotal = w.totalReviews + 1
        val newAvgStage = ((w.avgRecallStage * w.totalReviews) + reachedStage) / newTotal

        vocab[idx] = w.copy(
            stability = sched.stability,
            difficulty = sched.difficulty,
            phase = newPhase,
            intervalDays = sched.intervalDays,
            dueInDays = sched.intervalDays,
            lastReviewedDay = today,
            repetitions = reps,
            mastered = mastered,
            lapses = lapses,
            listenCount = w.listenCount + replays,
            totalReviews = newTotal,
            lastRecall = source,
            lastRetrievability = sched.retrievabilityAtReview,
            avgRecallStage = newAvgStage,
            totalTimeMs = w.totalTimeMs + timeMs,
            lastGrade = grade,
        )
        reviewLogs.add(
            ReviewLog(
                wordId = wordId,
                recall = source,
                grade = grade,
                reachedStage = reachedStage,
                replays = replays,
                timeMs = timeMs,
                retrievability = sched.retrievabilityAtReview,
                stabilityAfter = sched.stability,
                intervalAfter = sched.intervalDays,
            )
        )
        totalReviewsToday += 1
        // XP rewards accuracy: higher grade & earlier stage → more XP.
        val gained = 5 + grade * 3 + (4 - reachedStage).coerceAtLeast(0) * 2
        xp += gained
        // ---- telemetry ----
        val nowMastered = vocab[idx].mastered && !w.mastered
        // Study seconds for the review screen are banked by TrackStudyTime.
        track {
            it.reviews += 1
            if (grade >= 2) it.reviewsCorrect += 1 else it.mistakes += 1
            if (nowMastered) it.wordsMastered += 1
            it.xpEarned += gained
        }
        reviewHours.add(java.time.LocalTime.now().hour)
        if (reviewHours.size > 800) reviewHours.removeAt(0)
        // نفس السقف على السجلّات: التحليل يستقرّ إحصائياً قبل 400 حدث بكثير.
        while (reviewLogs.size > 800) reviewLogs.removeAt(0)
        completeTask("review")
        // 🔥 The micro-habit (الورد اليومي) — 5 cards is enough to keep the
        // streak alive on a busy day.
        advanceMicroHabit("micro_review")
        // 🟣 المرحلة الرابعة: كل بطاقة تُحتسب في مهمة الإنقاذ إن كانت نشطة.
        advanceRescue()
        checkChests()
        syncMysteryRewards()
        persist()
    }

    // ======================================================================
    //  Telemetry — one row per calendar day, the source of truth for
    //  Analytics and the AI coach. Every learning action funnels through
    //  [track], so nothing is ever lost.
    // ======================================================================

    /** epochDay -> stats. Kept as a map for O(1) upserts. */
    val dayStats = mutableStateMapOf<Long, DayStat>()

    /** Hour-of-day of every review, used to find the learner's peak hour. */
    val reviewHours = mutableStateListOf<Int>()

    /** Mutate today's row. All counters are additive. */
    private fun track(mutate: (DayStat) -> Unit) {
        val d = Telemetry.today()
        val row = dayStats[d] ?: DayStat(d)
        mutate(row)
        // Reassign to trigger Compose snapshot invalidation.
        dayStats[d] = row.copy()
        // كل نشاط حقيقي قد يُحقّق شرط اليوم — نقيّمه فوراً (مرّة واحدة).
        if (!dayEarnedStreak) evaluateStreakDay()
    }

    val todayStat: DayStat get() = dayStats[Telemetry.today()] ?: DayStat(Telemetry.today())
    val yesterdayStat: DayStat get() = dayStats[Telemetry.today() - 1] ?: DayStat(Telemetry.today() - 1)

    fun spanFor(scope: CoachScope): StatSpan =
        Telemetry.span(dayStats, scope.days, scope.label)

    fun previousSpanFor(scope: CoachScope): StatSpan =
        Telemetry.previousSpan(dayStats, scope.days)

    /** Days for the heatmap — last 119 days (17 weeks) ending today. */
    fun heatmapDays(n: Int = 119): List<DayStat> = Telemetry.span(dayStats, n, "heatmap").days

    val activityStreak: Int get() = Telemetry.currentStreak(dayStats)
    val bestActivityStreak: Int get() = Telemetry.bestStreak(dayStats)

    /** Lifetime totals across every recorded day. */
    val lifetime: StatSpan get() = StatSpan(dayStats.values.sortedBy { it.epochDay }, "كل الأوقات")

    /** Grammar accuracy measured from real exam skill breakdowns. */
    val grammarAccuracy: Float
        get() {
            var ok = 0; var tot = 0
            examHistory.forEach { e ->
                tot += e.skillTotal[ExamSkill.GRAMMAR] ?: 0
                ok += e.skillCorrect[ExamSkill.GRAMMAR] ?: 0
            }
            return if (tot > 0) ok.toFloat() / tot else 0f
        }

    val skillRadar: List<SkillScore>
        get() {
            val lt = lifetime
            return SkillRadar.compute(
                totalWords = totalWords,
                masteredWords = masteredCount,
                lessonsDone = completedLessons,
                listenMinutes = lt.listenMinutes,
                storiesRead = lt.stories,
                conversationTurns = lt.conversationTurns,
                phoneticsDrills = lt.phonetics,
                grammarCorrect = grammarAccuracy,
            )
        }

    val cefrEstimate: Pair<String, Float>
        get() = Telemetry.estimatedCefr(masteredCount, completedLessons, lifetime.examAvg)

    /**
     * Curriculum coverage — measured purely from the learner's OWN imported
     * courses. This is the backbone of "how far through MY course am I".
     */
    val curriculum: CurriculumReport
        get() = Coverage.report(courses.toList(), lessons.toList(), vocab.toList())

    val peakStudyHour: Int? get() = Telemetry.peakHour(reviewHours)

    // ---- Public trackers used across the app ----
    fun trackListening(seconds: Long) {
        if (seconds <= 0) return
        track { it.listenSeconds += seconds }
        // Two spoken sentences (~8s+) satisfy the listening micro-habit step.
        if (seconds >= 3) {
            advanceMicroHabit("micro_listen")
            completeTask("listen")
        }
    }

    // ----- Real on-screen study time -----
    // Estimated constants are not enough: we measure the wall-clock time the
    // learner actually spends on learning screens. A session starts when a
    // learning screen appears and is banked when it leaves (or the app pauses).
    // Idle protection: any single stretch longer than MAX_SESSION is clamped,
    // so leaving the app open overnight can never inflate the numbers.
    private var sessionStartMs: Long = 0L
    private var sessionLabel: String = ""

    /** Longest credit a single uninterrupted stretch can earn (25 min). */
    private val maxSessionSeconds = 25 * 60L

    /** Called when a learning screen becomes visible. */
    fun beginStudySession(label: String) {
        // Bank any previous stretch first (e.g. moving screen → screen).
        endStudySession()
        sessionStartMs = System.currentTimeMillis()
        sessionLabel = label
    }

    /** Called when the learning screen goes away / the app pauses. */
    fun endStudySession() {
        if (sessionStartMs <= 0L) return
        val secs = ((System.currentTimeMillis() - sessionStartMs) / 1000)
            .coerceAtMost(maxSessionSeconds)
        sessionStartMs = 0L
        sessionLabel = ""
        // Ignore accidental taps shorter than 3 seconds.
        if (secs < 3) return
        track { it.studySeconds += secs }
        studyHours += secs / 3600.0
        persist()
    }

    fun trackStoryRead() {
        track { it.storiesRead += 1 }
        advanceMicroHabit("micro_story")
        persist()
    }

    fun trackConversationTurn(n: Int = 1) { track { it.conversationTurns += n }; persist() }

    fun trackPhoneticsDrill(n: Int = 1) { track { it.phoneticsDrills += n }; persist() }

    // ======================================================================
    //  Study plan (Roadmap)
    // ======================================================================

    var studyPlan by mutableStateOf(StudyPlanDto())

    /** Highest level that has any lessons — the natural default target. */
    private fun defaultTargetLevel(): Int =
        courses.filter { c -> lessons.any { it.courseId == c.id } }
            .minOfOrNull { it.levelId } ?: 1

    /** The effective plan (auto-creates a sensible default when inactive). */
    val effectivePlan: StudyPlanDto
        get() = if (studyPlan.active) studyPlan else StudyPlanner.defaultPlan(defaultTargetLevel())

    val planSummary: PlanSummary
        get() = StudyPlanner.summarize(effectivePlan, courses, lessons)

    val planTimeline: List<PlanDay>
        get() = StudyPlanner.buildTimeline(effectivePlan, courses, lessons)

    /** Today's scheduled day inside the plan (null when the plan ended). */
    val planToday: PlanDay?
        get() {
            val t = Telemetry.today()
            return planTimeline.firstOrNull { it.epochDay == t }
        }

    fun savePlan(plan: StudyPlanDto) {
        studyPlan = plan.copy(
            active = true,
            startEpochDay = if (plan.startEpochDay > 0) plan.startEpochDay else Telemetry.today(),
        )
        // Keep the dashboard's simple counter in sync with the plan.
        val rate = StudyPlanner.lessonsPerDay(
            studyPlan,
            StudyPlanner.totalLessons(courses.filter { it.levelId == studyPlan.targetLevel }, lessons),
        )
        lessonsPerDay = rate.roundToInt().coerceIn(1, 5)
        persist()
    }

    fun resetPlan() {
        studyPlan = StudyPlanDto()
        persist()
    }

    // ======================================================================
    //  AI Coach
    // ======================================================================

    /** scope name -> latest report. */
    val coachReports = mutableStateMapOf<String, CoachReport>()

    var isCoaching by mutableStateOf(false)
        private set
    var coachError by mutableStateOf<String?>(null)

    fun coachReport(scope: CoachScope): CoachReport? = coachReports[scope.name]

    private fun coachFacts(scope: CoachScope): CoachFacts {
        val plan = effectivePlan
        val summary = planSummary
        val levelName = SampleData.levels.firstOrNull { it.id == plan.targetLevel }?.name
            ?: "المستوى ${plan.targetLevel}"
        return CoachFacts(
            scope = scope,
            levelName = levelName,
            span = spanFor(scope),
            previous = previousSpanFor(scope),
            totalWords = totalWords,
            masteredWords = masteredCount,
            dueNow = dueCount,
            predictedRetention = predictedRetention,
            trueRecallRate = trueRecallRate,
            avgStability = avgStability,
            leeches = forgottenWords,
            hardWords = activeVocab.filter { it.difficulty >= 6.5 && it.totalReviews > 0 }
                .sortedByDescending { it.difficulty }.take(8),
            examAvg = lifetime.examAvg,
            lastExams = examHistory.takeLast(5).reversed(),
            skills = skillRadar,
            streak = maxOf(streak, activityStreak),
            planLabel = "${PlanDuration.from(plan.duration).label} · ${PlanIntensity.from(plan.intensity).label}",
            planOnTrack = summary.onTrack,
            planDrift = summary.driftLessons,
            curriculum = curriculum,
        )
    }

    /** Run the coach on demand for [scope]. Never automatic — saves quota. */
    fun runCoach(scope: CoachScope) {
        if (isCoaching) return
        isCoaching = true
        coachError = null
        viewModelScope.launch {
            val facts = coachFacts(scope)
            val report = CoachService.analyze(facts, geminiApiKey, nowStamp())
            coachReports[scope.name] = report
            isCoaching = false
            if (report.local && hasAiKey) {
                coachError = "تعذّر الاتصال بالذكاء الاصطناعي — عُرض تحليل محلي"
            }
            persist()
        }
    }

    /** Instant local analysis without any network — used for the dashboard card. */
    fun quickCoach(scope: CoachScope = CoachScope.WEEKLY): CoachReport =
        coachReports[scope.name] ?: CoachService.localReport(coachFacts(scope), nowStamp())

    // ----- Aggregate session / lifetime insights for analytics -----
    val avgReplaysPerWord: Float
        get() = if (reviewLogs.isEmpty()) 0f else reviewLogs.map { it.replays }.average().toFloat()
    val avgSecondsPerWord: Float
        get() = if (reviewLogs.isEmpty()) 0f else reviewLogs.map { it.timeMs }.average().toFloat() / 1000f

    /** Predicted retention right now = mean retrievability across all seen words. */
    val predictedRetention: Float
        get() {
            val seen = vocab.filter { it.totalReviews > 0 }
            if (seen.isEmpty()) return 0f
            val today = todayEpochDay()
            return seen.map {
                val elapsed = if (it.lastReviewedDay < 0) 0.0 else (today - it.lastReviewedDay).toDouble()
                Fsrs.retrievability(elapsed, it.stability)
            }.average().toFloat()
        }

    /** True recall rate observed in reviews (grade ≥ 2 counts as recalled). */
    val trueRecallRate: Float
        get() = if (reviewLogs.isEmpty()) 0f else reviewLogs.count { it.grade >= 2 }.toFloat() / reviewLogs.size

    /** Average memory stability (days) across studied words. */
    val avgStability: Float
        get() {
            val seen = vocab.filter { it.totalReviews > 0 }
            return if (seen.isEmpty()) 0f else seen.map { it.stability }.average().toFloat()
        }

    /** Count of words currently due for review (retrievability-aware). */
    val dueCount: Int get() = dueWords.size

    // ======================================================================
    //  Stage-based quick rating (single visible "تذكرتها" button)
    // ======================================================================
    // The learner sees ONE positive action at every stage: "تذكرتها".
    // Behind the scenes the stage at which they pressed it decides both the
    // recall source (what cue unlocked the memory) and the FSRS rating:
    //
    //   Stage 1 (audio only)          → SOUND  → 4 Easy   (strongest memory)
    //   Stage 2 (+ mental image)      → IMAGE  → 3 Good
    //   Stage 3 (+ word & example)    → TEXT   → 2 Hard
    //   Stage 4 (full reveal, forgot) → FAILED → 1 Again
    //
    // This keeps the UI to a single tap while preserving full FSRS fidelity.

    /** The implicit recall source for recalling at [stage] (1..4). */
    fun sourceForStage(stage: Int): RecallSource = when (stage) {
        1 -> RecallSource.SOUND
        2 -> RecallSource.IMAGE
        3 -> RecallSource.TEXT
        else -> RecallSource.STUDIED
    }

    /** The FSRS rating (1..4) earned by recalling at [stage] (1..4). */
    fun gradeForStage(stage: Int): Int = when (stage) {
        1 -> 4   // Easy  — recalled from sound alone
        2 -> 3   // Good  — needed the mental image
        3 -> 2   // Hard  — needed to read the word
        else -> 2 // Hard — vaguely familiar after the full reveal
    }

    /**
     * Record a review where the learner pressed "تذكرتها" at [stage].
     * The recall source and FSRS rating are derived from the stage.
     */
    fun reviewWordAtStage(wordId: Int, stage: Int, replays: Int = 0, timeMs: Long = 0L) {
        reviewWord(
            wordId = wordId,
            source = sourceForStage(stage),
            reachedStage = stage,
            replays = replays,
            timeMs = timeMs,
            explicitGrade = gradeForStage(stage),
        )
    }

    /** Record a review where the learner never recalled the word ("نسيتها"). */
    fun failWord(wordId: Int, reachedStage: Int = 4, replays: Int = 0, timeMs: Long = 0L) {
        reviewWord(
            wordId = wordId,
            source = RecallSource.FAILED,
            reachedStage = reachedStage,
            replays = replays,
            timeMs = timeMs,
            explicitGrade = 1,
        )
    }

    /** Preview the interval the word gets if "تذكرتها" is pressed at [stage]. */
    fun previewStageIntervalDays(wordId: Int, stage: Int): Int =
        previewIntervalWithGrade(wordId, gradeForStage(stage))

    /** Preview the interval the word gets when marked forgotten. */
    fun previewFailIntervalDays(wordId: Int): Int = previewIntervalWithGrade(wordId, 1)

    /** Core interval preview for an already-resolved FSRS [grade]. */
    fun previewIntervalWithGrade(wordId: Int, grade: Int): Int {
        val w = vocab.firstOrNull { it.id == wordId } ?: return 1
        val today = todayEpochDay()
        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()
        val phase = when (w.phase) {
            FsrsPhase.NEW -> Fsrs.Phase.NEW
            FsrsPhase.LEARNING -> Fsrs.Phase.LEARNING
            FsrsPhase.REVIEW -> Fsrs.Phase.REVIEW
            FsrsPhase.RELEARNING -> Fsrs.Phase.RELEARNING
        }
        return Fsrs.schedule(
            fsrsWeights, grade.coerceIn(1, 4), phase, w.stability, w.difficulty, elapsed,
            desiredRetention, maxIntervalDays,
        ).intervalDays
    }

    /**
     * Preview the next interval (in days) a given word would get for a recall
     * source at a reached stage — powers the Anki-style interval hints on the
     * rating buttons.
     */
    fun previewIntervalDays(wordId: Int, source: RecallSource, reachedStage: Int): Int {
        val w = vocab.firstOrNull { it.id == wordId } ?: return 1
        var grade = source.grade
        if (source.grade >= 2) {
            grade = when (reachedStage) {
                1 -> (source.grade + 1).coerceAtMost(4)
                2 -> source.grade
                3 -> (source.grade - 1).coerceAtLeast(2)
                else -> (source.grade - 1).coerceAtLeast(1)
            }
        }
        val today = todayEpochDay()
        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()
        val phase = when (w.phase) {
            FsrsPhase.NEW -> Fsrs.Phase.NEW
            FsrsPhase.LEARNING -> Fsrs.Phase.LEARNING
            FsrsPhase.REVIEW -> Fsrs.Phase.REVIEW
            FsrsPhase.RELEARNING -> Fsrs.Phase.RELEARNING
        }
        return Fsrs.schedule(
            fsrsWeights, grade, phase, w.stability, w.difficulty, elapsed,
            desiredRetention, maxIntervalDays,
        ).intervalDays
    }

    /** Format a day count as a short Arabic label ("الآن" / "٣ي" / "٢ش"). */
    fun formatInterval(days: Int): String = when {
        days <= 0 -> "الآن"
        days < 30 -> "$days ي"
        days < 365 -> "${days / 30} ش"
        else -> "${days / 365} س"
    }

    // ----- Quiz generation from vocab -----
    fun generateQuiz(count: Int): List<QuizQuestion> {
        val base = activeVocab
        val pool = base.shuffled()
        val chosen = pool.take(count.coerceAtMost(pool.size))
        return chosen.mapIndexed { i, w ->
            val kind = QuizKind.values()[i % QuizKind.values().size]
            when (kind) {
                QuizKind.MEANING -> {
                    val wrong = base.filter { it.id != w.id }.shuffled().take(3).map { it.arabic }
                    val opts = (wrong + w.arabic).shuffled()
                    QuizQuestion("ما معنى الكلمة؟", w.english, opts, opts.indexOf(w.arabic), kind)
                }
                QuizKind.EXAMPLE -> {
                    val wrong = base.filter { it.id != w.id }.shuffled().take(3).map { it.english }
                    val opts = (wrong + w.english).shuffled()
                    QuizQuestion("أكمل الجملة بالكلمة المناسبة", w.exampleEn.replace(w.english, "_____", ignoreCase = true), opts, opts.indexOf(w.english), kind)
                }
                QuizKind.SPELLING -> {
                    val correct = w.english
                    val scrambled = mutableSetOf(correct)
                    var guard = 0
                    while (scrambled.size < 4 && guard < 40) { scrambled.add(scramble(correct)); guard++ }
                    val opts = scrambled.toList().shuffled()
                    QuizQuestion("اختر الإملاء الصحيح لـ: ${w.arabic}", w.phonetic, opts, opts.indexOf(correct), kind)
                }
            }
        }
    }

    private fun scramble(s: String): String {
        if (s.length < 3) return s + "x"
        val chars = s.toCharArray()
        val i = (1 until s.length - 1).random()
        val j = (1 until s.length - 1).random()
        val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        return String(chars)
    }

    fun addXp(amount: Int) { xp += amount }

    // ----- Add a single word to the dictionary (manual or AI) -----
    fun addWord(
        english: String,
        arabic: String,
        exampleEn: String = "",
        exampleAr: String = "",
        phonetic: String = "",
        mentalImage: String = "",
        courseId: Int = 0,
    ): Boolean {
        val en = english.trim()
        if (en.isEmpty()) return false
        vocab.add(
            0,
            VocabWord(
                id = nextWordId++,
                english = en,
                arabic = arabic.trim().ifBlank { "—" },
                exampleEn = exampleEn.trim(),
                exampleAr = exampleAr.trim(),
                phonetic = phonetic.trim(),
                mentalImage = mentalImage.trim(),
                courseId = courseId,
                // Manually added words go STRAIGHT into the active dictionary.
                // (Only words staged from a lesson wait for approval.)
                pendingApproval = false,
            )
        )
        xp += 5
        track { it.wordsAdded += 1; it.xpEarned += 5 }
        advanceMicroHabit("micro_word")
        completeTask("addword")
        // القاموس تغيّر ⇒ قد تتغيّر الخطة المتاحة (اختبار/مراجعة صارت ممكنة).
        rebuildDailyPlan(force = true)
        persist()
        return true
    }

    fun updateWord(
        id: Int,
        english: String,
        arabic: String,
        exampleEn: String,
        exampleAr: String,
        phonetic: String,
        mentalImage: String,
    ): Boolean {
        val idx = vocab.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val en = english.trim()
        if (en.isEmpty()) return false
        vocab[idx] = vocab[idx].copy(
            english = en,
            arabic = arabic.trim().ifBlank { "—" },
            exampleEn = exampleEn.trim(),
            exampleAr = exampleAr.trim(),
            phonetic = phonetic.trim(),
            mentalImage = mentalImage.trim(),
        )
        persist()
        return true
    }

    fun deleteWord(id: Int) {
        vocab.removeAll { it.id == id }
        MnemonicStore.delete(getApplication(), id)
        mnemonicVersion++
        persist()
    }

    // ======================================================================
    //  Mnemonic images (الروابط الذهنية)
    // ======================================================================
    // A batch flow that scales to any dictionary size:
    //   pick batch → build precision prompt → user generates & uploads one
    //   composite sheet → app slices it into one square tile per word.
    //
    // Only words that are actually IN the dictionary (approved, non-pending)
    // are ever eligible — pending lesson words are skipped by design.

    /** Bumped after any tile is written/removed so Composables re-read the disk. */
    var mnemonicVersion by mutableStateOf(0)
        private set

    /** User-tunable generation settings (persisted). */
    var mnemonicStyle by mutableStateOf(MnemonicArtStyle.CARTOON_3D)
    var mnemonicPersona by mutableStateOf(MnemonicPersona.NONE)
    var mnemonicModel by mutableStateOf(MnemonicModel.GEMINI)
    var mnemonicNumbering by mutableStateOf(false)
    var mnemonicBatchSize by mutableStateOf(MnemonicSpec.DEFAULT_BATCH)

    val mnemonicConfig: MnemonicConfig
        get() = MnemonicConfig(mnemonicStyle, mnemonicPersona, mnemonicModel, mnemonicNumbering)

    /** True when this word already has a mnemonic tile on disk. */
    fun hasMnemonic(wordId: Int): Boolean {
        @Suppress("UNUSED_EXPRESSION") mnemonicVersion // read → recompose on change
        return MnemonicStore.has(getApplication(), wordId)
    }

    /** Absolute file path of a word's tile (for Coil), or null. */
    fun mnemonicPath(wordId: Int): String? {
        @Suppress("UNUSED_EXPRESSION") mnemonicVersion
        return MnemonicStore.pathFor(getApplication(), wordId)
    }

    /** Dictionary words still missing a mnemonic image, oldest id first. */
    val wordsMissingMnemonic: List<VocabWord>
        get() {
            @Suppress("UNUSED_EXPRESSION") mnemonicVersion
            val ctx = getApplication<Application>()
            return activeVocab.filter { !MnemonicStore.has(ctx, it.id) }.sortedBy { it.id }
        }

    /** Count of dictionary words that already have an image. */
    val mnemonicReadyCount: Int
        get() {
            @Suppress("UNUSED_EXPRESSION") mnemonicVersion
            val ctx = getApplication<Application>()
            return activeVocab.count { MnemonicStore.has(ctx, it.id) }
        }

    val mnemonicMissingCount: Int get() = activeVocab.size - mnemonicReadyCount

    /** Disk space used by all tiles, human readable. */
    val mnemonicDiskLabel: String
        get() {
            @Suppress("UNUSED_EXPRESSION") mnemonicVersion
            val b = MnemonicStore.totalBytes(getApplication())
            return when {
                b <= 0L -> "0 KB"
                b < 1024 * 1024 -> "${b / 1024} KB"
                else -> String.format("%.1f MB", b / 1024.0 / 1024.0)
            }
        }

    // ----- The batch currently being generated -----
    val mnemonicBatch = mutableStateListOf<VocabWord>()
    var mnemonicSpec by mutableStateOf(MnemonicSpec.forCount(MnemonicSpec.DEFAULT_BATCH))
        private set
    var mnemonicPromptText by mutableStateOf("")
        private set
    var mnemonicMessage by mutableStateOf<String?>(null)
    var isSlicing by mutableStateOf(false)
        private set

    /**
     * Take the next [size] dictionary words without an image and build the
     * prompt for them. Returns the batch size actually claimed (0 = nothing to do).
     */
    fun startMnemonicBatch(size: Int = mnemonicBatchSize, onlyIds: List<Int>? = null): Int {
        val pool = if (onlyIds != null) {
            val set = onlyIds.toSet()
            activeVocab.filter { it.id in set }
        } else {
            wordsMissingMnemonic
        }
        val batch = pool.take(size.coerceIn(1, MnemonicSpec.MAX_BATCH))
        mnemonicBatch.clear()
        if (batch.isEmpty()) {
            mnemonicPromptText = ""
            return 0
        }
        mnemonicBatch.addAll(batch)
        mnemonicSpec = MnemonicSpec.forCount(batch.size)
        mnemonicPromptText = MnemonicPrompt.build(batch, mnemonicSpec, mnemonicConfig)
        return batch.size
    }

    /** Rebuild the prompt after the user changes style / persona / model. */
    fun refreshMnemonicPrompt() {
        if (mnemonicBatch.isEmpty()) return
        mnemonicSpec = MnemonicSpec.forCount(mnemonicBatch.size)
        mnemonicPromptText = MnemonicPrompt.build(mnemonicBatch.toList(), mnemonicSpec, mnemonicConfig)
    }

    /** Slice an uploaded composite sheet onto the current batch. */
    fun sliceMnemonicSheet(uri: android.net.Uri, onDone: (MnemonicStore.SliceResult) -> Unit = {}) {
        if (mnemonicBatch.isEmpty()) {
            val r = MnemonicStore.SliceResult(false, 0, "ابدأ دفعة أولاً")
            mnemonicMessage = r.message
            onDone(r)
            return
        }
        isSlicing = true
        viewModelScope.launch {
            val ids = mnemonicBatch.map { it.id }
            val res = MnemonicStore.sliceAndSave(getApplication(), uri, ids, mnemonicSpec)
            isSlicing = false
            mnemonicVersion++
            mnemonicMessage = res.message
            if (res.success) {
                xp += res.saved * 2
                track { it.mnemonicsMade += res.saved; it.xpEarned += res.saved * 2 }
                completeTask("mnemonic", res.saved)
                persist()
            }
            onDone(res)
        }
    }

    /** Drop a single word's image (so it re-enters the missing pool). */
    fun clearMnemonic(wordId: Int) {
        MnemonicStore.delete(getApplication(), wordId)
        mnemonicVersion++
    }

    /** Wipe every mnemonic tile. */
    fun clearAllMnemonics(): Int {
        val n = MnemonicStore.clearAll(getApplication())
        mnemonicVersion++
        mnemonicMessage = if (n > 0) "تم حذف $n صورة" else "لا توجد صور لحذفها"
        return n
    }

    // ----- JSON Import (delta update) -----
    var lastImportSummary by mutableStateOf<String?>(null)


    fun importPackage(pkg: CoursePackage): String {
        val type = CourseType.fromKey(pkg.courseType)
        // 1) stable course_key  2) course_id/jsonId  3) name+level  (all tolerant)
        val byKey = if (pkg.courseKey.isNotBlank()) SampleData.courseByKey(pkg.courseKey) else null
        val byId = byKey ?: (if (pkg.courseId.isNotBlank()) SampleData.courseByJsonId(pkg.courseId) else null)
        val existing = byId ?: SampleData.resolveCourse(pkg.courseName, pkg.courseName, pkg.level)
            ?: courses.firstOrNull { it.name == pkg.courseName && it.levelId == pkg.level }
        val course = existing ?: Course(
            id = nextCourseId++,
            levelId = pkg.level.coerceIn(1, 3),
            name = pkg.courseName.ifBlank { "كورس مستورد" },
            type = type,
            target = if (pkg.target > 0) pkg.target else pkg.lessons.size,
            accent = accentFor(type),
            style = styleFor(pkg.style, type),
            key = pkg.courseKey.trim(),
        ).also { courses.add(it) }

        var addedLessons = 0
        var addedWords = 0
        var updatedLessons = 0

        pkg.lessons.forEach { jl ->
            val wordIds = mutableListOf<Int>()
            val lessonIdForWords = nextLessonId
            jl.words.forEach { jw ->
                if (jw.word.isNotBlank()) {
                    val id = nextWordId++
                    vocab.add(
                        VocabWord(
                            id = id,
                            english = jw.word,
                            arabic = jw.translation.ifBlank { "—" },
                            exampleEn = jw.example,
                            exampleAr = jw.exampleAr,
                            phonetic = jw.phonetic,
                            mentalImage = jw.mentalImage,
                            courseId = course.id,
                            pendingApproval = true,
                            lessonId = lessonIdForWords,
                        )
                    )
                    wordIds.add(id)
                    addedWords++
                }
            }

            // Build key points from explicit list or grammar rules.
            val keyPoints = jl.keyPoints.ifEmpty {
                jl.grammarRules.map { r -> if (r.ruleAr.isNotBlank()) "${r.rule} — ${r.ruleAr}" else r.rule }
            }

            val newLesson = Lesson(
                id = nextLessonId++,
                courseId = course.id,
                no = if (jl.no > 0) jl.no else (lessons.filter { it.courseId == course.id }.maxOfOrNull { it.no } ?: 0) + 1,
                title = jl.title,
                summaryAr = jl.summaryAr,
                readingEn = jl.readingEn,
                readingAr = jl.readingAr,
                keyPoints = keyPoints,
                dialogues = jl.dialogues.map { Dialogue(it.speaker, it.en, it.ar) },
                newWordIds = wordIds,
            )

            // Delta update: replace a lesson with the same course+lesson_no, else add.
            val dupIdx = lessons.indexOfFirst { it.courseId == course.id && it.no == newLesson.no }
            if (dupIdx >= 0) {
                lessons[dupIdx] = newLesson
                updatedLessons++
            } else {
                lessons.add(newLesson)
                addedLessons++
            }
        }
        val summary = buildString {
            append("تم استيراد «${course.name}» (المستوى ${course.levelId})\n")
            append("جديد: $addedLessons درس")
            if (updatedLessons > 0) append(" · محدّث: $updatedLessons")
            append(" · $addedWords كلمة")
        }
        lastImportSummary = summary
        // Newly imported reading lessons must appear in the story archive at once.
        syncLessonStories()
        persist()
        return summary
    }

    /**
     * Import ONE lesson from the author's per-lesson JSON (metadata / lesson_content
     * / global_vocabulary / lesson_notes / quiz). Matched to a fixed curriculum course
     * via metadata.course_id. Delta-updates a lesson with the same course + lesson_no.
     *
     * Runs [syncLessonStories] + [persist] immediately — use this for a SINGLE
     * lesson import. For a batch, use [importLessons] which defers both side
     * effects to run exactly once after every package lands (fast, no freeze).
     */
    fun importLesson(pkg: LessonPackage, rawJson: String = ""): String {
        val summary = importLessonCore(pkg, rawJson)
        // Newly imported reading lessons must appear in the story archive at once.
        syncLessonStories()
        persist()
        return summary
    }

    /**
     * Core delta-import logic shared by [importLesson] and [importLessons].
     * Pure in-memory mutation only — no [syncLessonStories] / [persist] call,
     * so a batch of N lessons does this ONCE at the end instead of N times.
     */
    private fun importLessonCore(pkg: LessonPackage, rawJson: String = ""): String {
        // Match a curriculum course; if unknown, auto-create one so import never fails.
        val course = SampleData.resolveCourse(pkg.metadata.courseId, pkg.metadata.courseNameAr, pkg.metadata.level)
            ?: courses.firstOrNull { it.jsonId.equals(pkg.metadata.courseId, true) || it.name == pkg.metadata.courseNameAr }
            ?: Course(
                id = nextCourseId++,
                levelId = pkg.metadata.level.coerceIn(1, 3),
                name = pkg.metadata.courseNameAr.ifBlank { pkg.metadata.courseId.ifBlank { "كورس مستورد" } },
                type = CourseType.VOCABULARY,
                target = 20,
                accent = 0xFFE07856,
                style = LessonStyle.VOCAB_CARDS,
                jsonId = pkg.metadata.courseId,
            ).also { courses.add(it) }

        // Register global vocabulary as SRS words linked to this course.
        val wordIds = mutableListOf<Int>()
        val lessonIdForWords = nextLessonId // the lesson we're about to create
        pkg.globalVocabulary.forEach { gw ->
            if (gw.word.isNotBlank()) {
                val id = nextWordId++
                vocab.add(
                    VocabWord(
                        id = id,
                        english = gw.word,
                        arabic = gw.meaning.ifBlank { "—" },
                        exampleEn = gw.exampleEn,
                        exampleAr = gw.exampleAr,
                        phonetic = gw.phonetic,
                        mentalImage = gw.mentalImage,
                        courseId = course.id,
                        pendingApproval = true,
                        lessonId = lessonIdForWords,
                    )
                )
                wordIds.add(id)
            }
        }

        val quiz = mapJsonQuiz(pkg.quiz)

        val sentences = pkg.lessonContent.keySentences.map { Sentence(it.en, it.ar) }
        val segments = pkg.lessonContent.segments.map { Sentence(it.en, it.ar) }
        val examples = pkg.lessonContent.examples.map { Sentence(it.en, it.ar) }
        val keyPoints = pkg.globalVocabulary.filter { it.word.isNotBlank() }
            .map { "${it.word} = ${it.meaning}" }

        val no = if (pkg.metadata.lessonNo > 0) pkg.metadata.lessonNo
            else (lessons.filter { it.courseId == course.id }.maxOfOrNull { it.no } ?: 0) + 1

        val newLesson = Lesson(
            id = nextLessonId++,
            courseId = course.id,
            no = no,
            title = pkg.metadata.title,
            summaryAr = pkg.lessonContent.explanationAr.ifBlank { pkg.lessonNotes.firstOrNull() ?: "" },
            readingEn = pkg.lessonContent.fullTextEn.ifBlank { sentences.joinToString(" ") { it.en } },
            readingAr = pkg.lessonContent.fullTextAr.ifBlank { sentences.joinToString(" ") { it.ar } },
            keyPoints = keyPoints,
            keySentences = sentences,
            dialogues = pkg.lessonContent.dialogue.map { Dialogue(it.speaker, it.en, it.ar) },
            keyExpressions = pkg.lessonContent.keyExpressions.map {
                KeyExpression(it.expressionEn, it.expressionAr, it.usageAr)
            },
            notes = pkg.lessonNotes,
            quiz = quiz,
            newWordIds = wordIds,
            explanationAr = pkg.lessonContent.explanationAr,
            logicAr = pkg.lessonContent.logicAr,
            examples = examples,
            fullTextEn = pkg.lessonContent.fullTextEn,
            fullTextAr = pkg.lessonContent.fullTextAr,
            segments = segments,
            topicEn = pkg.lessonContent.topicEn,
            topicAr = pkg.lessonContent.topicAr,
            brainstorming = pkg.lessonContent.brainstormingQuestions.map {
                BrainstormQ(it.questionEn, it.questionAr, it.suggestedAnswerEn, it.suggestedAnswerAr)
            },
            guidedSentences = pkg.lessonContent.guidedSentences.map { Sentence(it.en, it.ar) },
            finalDraft = pkg.lessonContent.finalDraft.let {
                if (it.en.isNotBlank() || it.ar.isNotBlank()) Sentence(it.en, it.ar) else null
            },
            rawJson = rawJson,
        )

        val dupIdx = lessons.indexOfFirst { it.courseId == course.id && it.no == newLesson.no }
        val updated = dupIdx >= 0
        if (updated) lessons[dupIdx] = newLesson else lessons.add(newLesson)

        val summary = "تم ${if (updated) "تحديث" else "استيراد"} الدرس $no: «${pkg.metadata.title}»\n" +
            "الكورس: ${course.name} (المستوى ${course.levelId}) · ${wordIds.size} كلمة · ${quiz.size} سؤال"
        lastImportSummary = summary
        return summary
    }

    /**
     * Import MANY lessons at once (batch) — INSTANT, fully offline/local.
     *
     * Each package is delta-imported via [importLessonCore] (no network, no
     * per-lesson persist/sync — that used to make a 10-lesson import freeze
     * for minutes because it also kicked off audio generation between every
     * single lesson). [syncLessonStories] and [persist] now run exactly ONCE
     * after the whole batch lands. Audio generation is a completely separate,
     * opt-in background step (see [generateMissingAudio]) — importing content
     * never blocks on it anymore.
     */
    fun importLessons(packages: List<LessonPackage>): String {
        if (packages.isEmpty()) return "لا توجد دروس للاستيراد"
        var imported = 0
        val touchedCourses = LinkedHashSet<String>()
        var words = 0
        packages.forEach { pkg ->
            importLessonCore(pkg)
            imported++
            words += pkg.globalVocabulary.size
            touchedCourses.add(pkg.metadata.courseNameAr.ifBlank { pkg.metadata.courseId })
        }
        val summary = buildString {
            append("تم استيراد $imported درس في ${touchedCourses.size} كورس")
            if (words > 0) append(" · $words كلمة")
            append("\n")
            append(touchedCourses.joinToString(" · "))
        }
        lastImportSummary = summary
        // Newly imported reading lessons must appear in the story archive at once.
        // Runs ONE time for the whole batch, not once per lesson.
        syncLessonStories()
        persist()
        return summary
    }

    private fun accentFor(type: CourseType): Long = when (type) {
        CourseType.VOCABULARY -> 0xFFE07856   // terracotta
        CourseType.GRAMMAR -> 0xFFCB5F41      // sienna
        CourseType.READING -> 0xFF6B9080      // sage
        CourseType.LISTENING -> 0xFF52796F    // pine
        CourseType.PHONETICS -> 0xFFE0A34E    // gold
        CourseType.CONVERSATION -> 0xFFD9776A // coral
        CourseType.WRITING -> 0xFF5E9C76      // green
    }

    private fun styleFor(key: String, type: CourseType): LessonStyle {
        when (key.lowercase().trim()) {
            "vocab_cards", "vocab", "bites" -> return LessonStyle.VOCAB_CARDS
            "grammar_rules", "grammar" -> return LessonStyle.GRAMMAR_RULES
            "reading_text", "reading" -> return LessonStyle.READING_TEXT
            "listening_audio", "listening" -> return LessonStyle.LISTENING_AUDIO
            "conversation" -> return LessonStyle.CONVERSATION
            "phonetics_sounds", "phonetics" -> return LessonStyle.PHONETICS_SOUNDS
            "writing_practice", "writing" -> return LessonStyle.WRITING_PRACTICE
            "story" -> return LessonStyle.STORY
            "news" -> return LessonStyle.NEWS
            "comedy" -> return LessonStyle.COMEDY
            "idioms" -> return LessonStyle.IDIOMS
            "exam_prep", "ielts" -> return LessonStyle.EXAM_PREP
            "thinking" -> return LessonStyle.THINKING
            "culture" -> return LessonStyle.CULTURE
        }
        return when (type) {
            CourseType.VOCABULARY -> LessonStyle.VOCAB_CARDS
            CourseType.GRAMMAR -> LessonStyle.GRAMMAR_RULES
            CourseType.READING -> LessonStyle.READING_TEXT
            CourseType.LISTENING -> LessonStyle.LISTENING_AUDIO
            CourseType.CONVERSATION -> LessonStyle.CONVERSATION
            CourseType.PHONETICS -> LessonStyle.PHONETICS_SOUNDS
            CourseType.WRITING -> LessonStyle.WRITING_PRACTICE
        }
    }

    // ==========================================================================
    // AI AUDIO GENERATION — Gemini neural TTS only, always permanently cached.
    //
    // This runs as a background QUEUE with limited parallelism (3 concurrent
    // requests) so it never blocks the UI thread and never freezes the app,
    // even after importing many lessons at once. Importing content is always
    // instant (see importLessons) — this step is entirely separate and
    // optional (see [autoGenerateAiAudio] setting).
    // ==========================================================================
    private var tts: com.zmastery.english.audio.TtsManager? = null
    private var audioGenJob: Job? = null

    /** Wire the shared TTS engine (called once from the Activity/Composition). */
    fun attachTts(engine: com.zmastery.english.audio.TtsManager) {
        tts = engine
        // Push whatever credential is already loaded so TTS works immediately.
        engine.apiKey = geminiApiKey
        engine.voice = ttsVoice
    }

    var isGeneratingAudio by mutableStateOf(false)
        private set
    var audioGenTotal by mutableStateOf(0)
        private set
    var audioGenDone by mutableStateOf(0)
        private set
    var audioGenLabel by mutableStateOf("")
        private set
    var lastAudioMessage by mutableStateOf<String?>(null)

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
    private fun lessonAudioText(l: Lesson): String {
        val parts = mutableListOf<String>()
        if (l.fullTextEn.isNotBlank()) parts += l.fullTextEn
        else if (l.readingEn.isNotBlank()) parts += l.readingEn
        if (l.segments.isNotEmpty()) parts += l.segments.joinToString(" ") { it.en }
        if (l.keySentences.isNotEmpty()) parts += l.keySentences.joinToString(" ") { it.en }
        if (l.dialogues.isNotEmpty()) parts += l.dialogues.joinToString(" ") { it.en }
        return parts.joinToString(" ").trim()
    }

    /** How many Gemini TTS requests may run at the same time (rate-limit friendly). */
    private val AUDIO_PARALLELISM = 3

    /**
     * Scan all content for items lacking a PERMANENT AI voice and generate
     * them via a bounded-concurrency background queue (max [AUDIO_PARALLELISM]
     * requests in flight at once, to respect Gemini's per-minute quota without
     * freezing the app on a huge batch). Safe to call anytime — skips whatever
     * is already cached, and is a no-op while already running.
     */
    fun generateMissingAudio() {
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
        audioGenJob = viewModelScope.launch {
            isGeneratingAudio = true
            audioGenDone = 0
            audioGenLabel = "جارٍ التحضير…"

            // Build the work list (short clips + long-form story/lesson narrations).
            data class Job2(val text: String, val label: String, val longForm: Boolean, val onDone: () -> Unit)
            val jobs = mutableListOf<Job2>()

            vocab.toList().forEach { w ->
                if (!w.wordAudioReady && w.english.isNotBlank()) {
                    jobs += Job2(w.english, w.english, false) {
                        val i = vocab.indexOfFirst { it.id == w.id }
                        if (i >= 0) vocab[i] = vocab[i].copy(wordAudioReady = true)
                    }
                }
                if (!w.exampleAudioReady && w.exampleEn.isNotBlank()) {
                    jobs += Job2(w.exampleEn, "مثال: ${w.english}", false) {
                        val i = vocab.indexOfFirst { it.id == w.id }
                        if (i >= 0) vocab[i] = vocab[i].copy(exampleAudioReady = true)
                    }
                }
            }
            lessons.toList().forEach { l ->
                val t = lessonAudioText(l)
                if (!l.audioReady && t.isNotBlank()) {
                    jobs += Job2(t, "درس: ${l.title}", true) {
                        val i = lessons.indexOfFirst { it.id == l.id }
                        if (i >= 0) lessons[i] = lessons[i].copy(audioReady = true)
                    }
                }
            }
            storyArchive.toList().forEach { s ->
                if (!s.audioReady && s.en.isNotBlank()) {
                    jobs += Job2(s.en, "قصة: ${s.title}", true) {
                        val i = storyArchive.indexOfFirst { it.id == s.id }
                        if (i >= 0) storyArchive[i] = storyArchive[i].copy(audioReady = true)
                    }
                }
            }

            audioGenTotal = jobs.size
            if (jobs.isEmpty()) {
                isGeneratingAudio = false
                lastAudioMessage = "كل الأصوات مولّدة بالفعل ✓"
                return@launch
            }

            // Bounded-concurrency fan-out: up to AUDIO_PARALLELISM requests in
            // flight, so a big batch finishes fast without exceeding rate limits
            // or ever blocking the calling coroutine (and therefore the UI).
            val semaphore = Semaphore(AUDIO_PARALLELISM)
            val okCount = Mutex()
            var ok = 0
            val tasks = jobs.map { job ->
                async {
                    semaphore.withPermit {
                        val success = runCatching {
                            if (job.longForm) engine.generateLongFormAndCache(job.text)
                            else engine.generateAndCachePermanent(job.text)
                        }.getOrDefault(false)
                        if (success) {
                            job.onDone()
                            okCount.withLock { ok++ }
                        }
                        audioGenLabel = job.label
                        audioGenDone++
                    }
                }
            }
            tasks.awaitAll()

            isGeneratingAudio = false
            audioGenLabel = ""
            lastAudioMessage = if (ok == jobs.size) "تم توليد أصوات $ok عنصر ✓"
                else "تم توليد $ok من ${jobs.size} — البقية ستُحاول لاحقاً"
            persist()
        }
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
        persist()
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
        viewModelScope.launch {
            val success = runCatching { engine.generateLongFormAndCache(story.en) }.getOrDefault(false)
            if (success) {
                val i = storyArchive.indexOfFirst { it.id == storyId }
                if (i >= 0) storyArchive[i] = storyArchive[i].copy(audioReady = true)
                persist()
                lastAudioMessage = "تم توليد صوت القصة ✓"
            } else {
                lastAudioMessage = "تعذّر توليد صوت القصة — حاول لاحقاً"
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
        viewModelScope.launch {
            val success = runCatching { engine.generateLongFormAndCache(text) }.getOrDefault(false)
            if (success) {
                val i = lessons.indexOfFirst { it.id == lessonId }
                if (i >= 0) lessons[i] = lessons[i].copy(audioReady = true)
                persist()
                lastAudioMessage = "تم توليد صوت الدرس ✓"
            } else {
                lastAudioMessage = "تعذّر توليد صوت الدرس — حاول لاحقاً"
            }
        }
    }

    // ==========================================================================
    // CLOUD SYNC (Firebase) — pull new lessons added outside the app (Firestore),
    // and back up / restore this learner's own progress across devices.
    //
    // The app always has a Firebase user from the very first launch (anonymous
    // by default — see CloudAuth.ensureSignedIn). Everything below works with
    // that uid immediately, no extra setup required; Google Sign-In (optional,
    // see CloudAuth.signInWithGoogle) simply links onto the SAME uid so nothing
    // is ever lost when the learner later decides to "claim" their account.
    // ==========================================================================
    var lastCloudLessonSyncMillis by mutableStateOf(0L)
    var cloudSyncEnabled by mutableStateOf(true)
    // Web Client ID from google-services.json (client_type: 3) — enables Google Sign-In
    var googleWebClientId by mutableStateOf("567438543557-93ce76v8d4kiqcf9scl8qk04tsf90num.apps.googleusercontent.com")

    var cloudUid by mutableStateOf<String?>(null)
        private set
    var cloudIsAnonymous by mutableStateOf(true)
        private set
    var cloudDisplayName by mutableStateOf<String?>(null)
        private set
    var cloudEmail by mutableStateOf<String?>(null)
        private set

    var isSyncingCloud by mutableStateOf(false)
        private set
    var cloudSyncMessage by mutableStateOf<String?>(null)
    var newLessonsFromCloud by mutableStateOf(0)

    private fun refreshCloudAuthState() {
        cloudUid = com.zmastery.english.cloud.CloudAuth.uid
        cloudIsAnonymous = com.zmastery.english.cloud.CloudAuth.isAnonymous
        cloudDisplayName = com.zmastery.english.cloud.CloudAuth.displayName
        cloudEmail = com.zmastery.english.cloud.CloudAuth.email
    }

    /**
     * Called once from the Activity/Composition root at startup. Ensures a
     * Firebase user exists (anonymous if nothing else), then pulls any new
     * cloud lessons and the latest progress snapshot — completely silent,
     * never blocks the UI, safe to call with no network at all.
     */
    fun initCloudSync() {
        if (!cloudSyncEnabled) return
        viewModelScope.launch {
            runCatching { com.zmastery.english.cloud.CloudAuth.ensureSignedIn() }
            refreshCloudAuthState()
            val uid = cloudUid ?: return@launch
            // Pull progress FIRST (only if the cloud copy is newer than anything
            // we have locally — a fresh install has nothing to lose by adopting
            // it; an existing install keeps its own state, since local writes
            // always win once this device has been used at all).
            if (lastCloudLessonSyncMillis == 0L) {
                runCatching {
                    com.zmastery.english.cloud.CloudSync.pullProgress(uid).getOrNull()
                }.getOrNull()?.let { cloudJson ->
                    if (cloudJson != null) {
                        Persistence.decode(cloudJson)?.let { restoreFrom(it) }
                    }
                }
            }
            syncCloudLessons(silent = true)
        }
    }

    /**
     * Pull every lesson document added/changed in Firestore since the last
     * sync and import them exactly like a manual batch import — instant,
     * fully local once downloaded, and audio generation (if enabled) queues
     * separately afterwards so this never freezes the UI.
     */
    fun syncCloudLessons(silent: Boolean = false) {
        if (!cloudSyncEnabled) {
            if (!silent) cloudSyncMessage = "المزامنة السحابية متوقفة من الإعدادات"
            return
        }
        if (isSyncingCloud) return
        viewModelScope.launch {
            isSyncingCloud = true
            if (!silent) cloudSyncMessage = "جارٍ التحقق من دروس جديدة…"
            val uid = cloudUid ?: run {
                runCatching { com.zmastery.english.cloud.CloudAuth.ensureSignedIn() }
                refreshCloudAuthState()
                cloudUid
            }
            if (uid == null) {
                isSyncingCloud = false
                if (!silent) cloudSyncMessage = "تعذّر الاتصال بالسحابة الآن"
                return@launch
            }
            val result = com.zmastery.english.cloud.CloudSync.pullNewLessons(lastCloudLessonSyncMillis)
            result.onSuccess { sync ->
                if (sync.packages.isNotEmpty()) {
                    importLessons(sync.packages)
                    newLessonsFromCloud += sync.packages.size
                    autoGenerateAudioIfOnline()
                }
                if (sync.latestUpdatedAtMillis > lastCloudLessonSyncMillis) {
                    lastCloudLessonSyncMillis = sync.latestUpdatedAtMillis
                    persist()
                }
                cloudSyncMessage = when {
                    sync.packages.isEmpty() -> "لا توجد دروس جديدة — كل شيء محدّث ✓"
                    else -> "تمت إضافة ${sync.packages.size} درس جديد من السحابة ✓"
                }
                pushProgressToCloud()
            }.onFailure {
                cloudSyncMessage = "تعذّر المزامنة — تحقق من الاتصال"
            }
            isSyncingCloud = false
        }
    }

    /** Push the CURRENT local state to Firestore under this learner's uid.
     *  API keys are stripped before pushing — they must NEVER leave the device. */
    fun pushProgressToCloud() {
        if (!cloudSyncEnabled) return
        val uid = cloudUid ?: return
        viewModelScope.launch {
            val state = buildAppState()
            // Strip API keys before cloud sync — keys stay on device only
            val safeState = KeyProtector.stripKeysForSharing(state)
            val raw = Persistence.encode(safeState)
            com.zmastery.english.cloud.CloudSync.pushProgress(uid, raw)
        }
    }

    /**
     * Link the current anonymous cloud account to a real Google account so the
     * SAME progress + uid can be restored on any other device later. Requires
     * [googleWebClientId] to already be configured in Settings.
     */
    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            com.zmastery.english.cloud.CloudAuth.webClientId = googleWebClientId.trim()
            val result = com.zmastery.english.cloud.CloudAuth.signInWithGoogle(context)
            result.onSuccess {
                refreshCloudAuthState()
                cloudSyncMessage = "تم ربط حساب جوجل بنجاح ✓"
                pushProgressToCloud()
            }.onFailure { e ->
                cloudSyncMessage = e.message ?: "تعذّر تسجيل الدخول بحساب جوجل"
            }
        }
    }

    fun updateGoogleWebClientId(id: String) {
        googleWebClientId = id.trim()
        com.zmastery.english.cloud.CloudAuth.webClientId = googleWebClientId
        persist()
    }

    fun updateCloudSyncEnabled(enabled: Boolean) {
        cloudSyncEnabled = enabled
        persist()
    }

    /**
     * Build the exact same [AppState] snapshot [persist] writes locally — used
     * both by local save and by [pushProgressToCloud] so the two never drift.
     */
    private fun buildAppState(): AppState = AppState(
        courses = courses.map { it.toDto() },
        lessons = lessons.map { it.toDto() },
        vocab = vocab.map { it.toDto() },
        profile = ProfileDto(
            streak = streak, xp = xp, totalReviewsToday = totalReviewsToday,
            dailyGoal = dailyGoal, lessonsPerDay = lessonsPerDay, studyHours = studyHours,
            motivationLevel = motivationLevel, accuracy = accuracy,
            geminiApiKey = geminiApiKey, ttsVoice = ttsVoice,
            nextCourseId = nextCourseId, nextLessonId = nextLessonId, nextWordId = nextWordId,
            desiredRetention = desiredRetention, maxIntervalDays = maxIntervalDays,
            revealMode = revealMode.name,
            reviewAutoPlay = reviewAutoPlay,
            autoGenerateAiAudio = autoGenerateAiAudio,
            aiAudioEnabled = aiAudioEnabled,
            mnemonicStyle = mnemonicStyle.name, mnemonicPersona = mnemonicPersona.name,
            mnemonicModel = mnemonicModel.name, mnemonicNumbering = mnemonicNumbering,
            mnemonicBatchSize = mnemonicBatchSize,
            onboardingDone = onboardingDone, lastStoryDay = lastStoryDay,
            lastActiveDay = lastActiveDay,
            dailyTaskProgress = dailyTasks.map { "${it.id}:${it.progress}" },
            nextStoryId = nextStoryId,
            microHabitId = microHabitId,
            microHabitProgress = microHabitProgress,
            learnerName = learnerName,
            learnerEmail = learnerEmail,
            dayEarnedStreak = dayEarnedStreak,
            lastCloudLessonSyncMillis = lastCloudLessonSyncMillis,
            cloudSyncEnabled = cloudSyncEnabled,
            googleWebClientId = googleWebClientId,
        ),
        aiAgents = aiAgents.map { AiAgentDto(it.id, it.modelId, it.character, it.voiceId, it.style, it.prompt) },
        apiKeys = apiKeys.map {
            ApiKeyDto(
                id = it.id, label = it.label, provider = it.provider,
                maskedKey = it.maskedKey, active = it.active,
                rawKey = it.rawKey, baseUrl = it.baseUrl, status = it.status,
            )
        },
        exams = examHistory.map { it.toDto() },
        examMisses = examMisses.mapKeys { it.key.toString() },
        stories = storyArchive.map { it.toDto() },
        dayStats = dayStats.values.sortedBy { it.epochDay },
        studyPlan = studyPlan,
        coachReports = coachReports.values.toList(),
        chests = openedChests.values.toList(),
        mysteryRewards = mysteryRewards.toList(),
        wallet = wallet,
        mirrorReports = mirrorReports.toMap(),
        reviewSignals = reviewLogs.takeLast(SIGNAL_CAP).map {
            ReviewSignalDto(it.wordId, it.grade, it.reachedStage, it.replays, it.timeMs)
        },
        reviewHours = reviewHours.takeLast(SIGNAL_CAP),
        rescue = rescue,
        lastRescueOfferDay = lastRescueOfferDay,
        streakBeforeBreak = streakBeforeBreak,
    )
}
