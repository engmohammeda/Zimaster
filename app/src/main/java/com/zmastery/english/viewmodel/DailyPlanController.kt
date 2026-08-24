package com.zmastery.english.viewmodel

import com.zmastery.english.data.*

/**
 * Controller for adaptive daily-plan generation. It builds the learner
 * snapshot and (re)generates today's task list. The `dailyTasks` list and the
 * streak/engagement orchestration (`completeTask`, `evaluateStreakDay`,
 * `applyDayRollover`, the streak-condition views) stay on [AppViewModel] because
 * they are tightly coupled to the central telemetry `track` loop. See
 * [ExamsController] for conventions.
 */
internal class DailyPlanController(internal val vm: AppViewModel) {

    private val dailyTasks get() = vm.dailyTasks
    private val dayStats get() = vm.dayStats
    private val activeVocab get() = vm.activeVocab
    private val dueWords get() = vm.dueWords
    private val lessons get() = vm.lessons
    private val courses get() = vm.courses
    private val storyArchive get() = vm.storyArchive
    private val storySeedCount get() = vm.storySeedCount
    private val storyAiReady get() = vm.storyAiReady
    private val mnemonicMissingCount get() = vm.mnemonicMissingCount
    private val streak get() = vm.streak

    /** Today's day is memoised so the plan is not rebuilt within the same day. */
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
}
