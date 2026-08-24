package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlin.math.roundToInt

/**
 * Controller for lesson completion / self-assessed review. The lesson/vocab
 * lists and XP counters stay on [AppViewModel]; this class holds the toggle /
 * review / un-complete actions. See [ExamsController] for conventions.
 */
internal class LessonReviewController(internal val vm: AppViewModel) {

    private val lessons get() = vm.lessons
    private val vocab get() = vm.vocab
    private val courses get() = vm.courses
    private var xp
        get() = vm.xp
        set(v) { vm.xp = v }
    private var totalReviewsToday
        get() = vm.totalReviewsToday
        set(v) { vm.totalReviewsToday = v }
    private var studyHours
        get() = vm.studyHours
        set(v) { vm.studyHours = v }
    private var mnemonicVersion
        get() = vm.mnemonicVersion
        set(v) { vm.mnemonicVersion = v }
    private val app get() = vm.app

    private fun track(mutate: (DayStat) -> Unit) = vm.track(mutate)
    private fun completeTask(id: String, amount: Int = 1) = vm.completeTask(id, amount)
    private fun syncMysteryRewards(notify: Boolean = true) = vm.syncMysteryRewards(notify)
    private fun grantCourseReward(courseId: Int, courseName: String, lessonCount: Int) =
        vm.grantCourseReward(courseId, courseName, lessonCount)
    private fun syncLessonStories() = vm.syncLessonStories()
    private fun persist() = vm.persist()

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
            ids.forEach { MnemonicStore.delete(app, it) }
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
}
