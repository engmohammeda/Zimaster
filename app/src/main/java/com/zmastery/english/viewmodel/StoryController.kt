package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controller for the Story archive feature (daily AI stories + mirrored
 * reading lessons).
 *
 * Holds the generation/sync/CRUD logic plus the transient `storyJob`. All
 * persisted/UI state remains on [AppViewModel] and is reached via aliases.
 * See [ExamsController] for the ownership/delegation conventions.
 */
internal class StoryController(internal val vm: AppViewModel) {

    private val storyArchive get() = vm.storyArchive
    private var nextStoryId
        get() = vm.nextStoryId
        set(v) { vm.nextStoryId = v }
    private var lastStoryDay
        get() = vm.lastStoryDay
        set(v) { vm.lastStoryDay = v }

    private var isMakingStory
        get() = vm.isMakingStory
        set(v) { vm.isMakingStory = v }
    private var storyMessage
        get() = vm.storyMessage
        set(v) { vm.storyMessage = v }
    private var isWaitingForAi
        get() = vm.isWaitingForAi
        set(v) { vm.isWaitingForAi = v }
    private var storyAttempt
        get() = vm.storyAttempt
        set(v) { vm.storyAttempt = v }
    private var storyRetryIn
        get() = vm.storyRetryIn
        set(v) { vm.storyRetryIn = v }

    private var storyJob: Job? = null

    private val lessons get() = vm.lessons
    private val courses get() = vm.courses
    private val vocab get() = vm.vocab
    private val activeVocab get() = vm.activeVocab
    private val dueWords get() = vm.dueWords
    private val aiAgents get() = vm.aiAgents
    private val geminiApiKey get() = vm.geminiApiKey
    private val hasAiKey get() = vm.hasAiKey
    private var xp
        get() = vm.xp
        set(v) { vm.xp = v }

    private fun todayEpochDay(): Long = vm.todayEpochDay()
    private fun track(mutate: (DayStat) -> Unit) = vm.track(mutate)
    private fun completeTask(id: String, amount: Int = 1) = vm.completeTask(id, amount)
    private val app get() = vm.app
    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
        vm.vmScope.launch(block = block)

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

    // ---- AI-only daily story state (owned by the view model, written here) ----
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
        val ctx = app

        isMakingStory = true
        isWaitingForAi = false
        storyAttempt = 0
        storyMessage = null

        storyJob = launch {
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
                        vm.persist()
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
            // has actually FINISHED the lesson — an in-progress lesson would
            // otherwise clutter the archive with things not yet earned.
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
        vm.persist()
    }

    fun toggleStoryFavorite(id: Int) {
        val i = storyArchive.indexOfFirst { it.id == id }
        if (i < 0) return
        storyArchive[i] = storyArchive[i].copy(isFavorite = !storyArchive[i].isFavorite)
        vm.persist()
    }

    /** Delete a story. Lesson stories are re-created on the next sync by design. */
    fun deleteStory(id: Int) {
        storyArchive.removeAll { it.id == id }
        vm.persist()
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
            vm.persist()
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
}
