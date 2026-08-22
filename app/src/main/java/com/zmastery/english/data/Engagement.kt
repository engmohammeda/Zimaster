package com.zmastery.english.data

import java.time.LocalDate

// ==========================================================================
//  Engagement — the honest "state of the learner" model.
//
//  Motivation, streaks and encouragement must be DERIVED from real activity,
//  never from a hardcoded default. A brand-new install has no data, so the
//  dashboard must say "let's begin", not "your streak is excellent".
//
//  This file centralises those rules so every surface (dashboard, mascot,
//  widget, notifications, coach) tells the same, truthful story.
// ==========================================================================

/**
 * Where the learner is in their lifecycle. Drives copy, colours and which
 * dashboard sections are even worth rendering.
 */
enum class LearnerStage {
    /** Nothing imported, no words — the app is empty. */
    EMPTY,

    /** Content exists but the learner has never completed any activity. */
    READY,

    /** Activity has started today, but the daily goal is not met. */
    IN_PROGRESS,

    /** Everything scheduled for today is finished. */
    DONE_TODAY,

    /** Has history, but nothing done today yet. */
    RETURNING,

    /** Missed 2+ consecutive days after having a habit. */
    LAPSED,
}

/**
 * A fully-derived snapshot of engagement. Nothing here is stored; it is
 * recomputed from telemetry so it can never drift out of sync with reality.
 *
 * @param stage           lifecycle bucket
 * @param streak          consecutive active days (0 when never active)
 * @param daysSinceActive 0 = active today, -1 = never active at all
 * @param momentum        0..1 "how strong is the habit right now" — only
 *                        meaningful once [hasHistory] is true
 * @param hasHistory      true once at least one day of real activity exists
 * @param goalProgress    0..1 progress toward today's review goal
 * @param streakAtRisk    had a streak, nothing done today, and it is late
 */
data class Engagement(
    val stage: LearnerStage,
    val streak: Int,
    val bestStreak: Int,
    val daysSinceActive: Int,
    val momentum: Float,
    val hasHistory: Boolean,
    val hasContent: Boolean,
    val goalProgress: Float,
    val activeDaysLast14: Int,
    val streakAtRisk: Boolean,
) {
    val isFresh: Boolean get() = stage == LearnerStage.EMPTY || stage == LearnerStage.READY

    /** Mascot emoji — neutral while unknown, never falsely celebratory. */
    val emoji: String
        get() = when (stage) {
            LearnerStage.EMPTY -> "\uD83D\uDC4B"      // 👋 wave
            LearnerStage.READY -> "\uD83D\uDE80"      // 🚀 launch
            LearnerStage.IN_PROGRESS -> "\uD83D\uDCAA" // 💪 flex
            LearnerStage.DONE_TODAY -> "\uD83C\uDF89"  // 🎉 party
            LearnerStage.RETURNING -> "\uD83D\uDC40"   // 👀 eyes
            LearnerStage.LAPSED -> "\uD83E\uDDED"      // 🧭 compass
        }

    /** Headline shown by the mascot banner. */
    val title: String
        get() = when (stage) {
            LearnerStage.EMPTY -> "أهلاً بك! لنبدأ الإعداد"
            LearnerStage.READY -> "كل شيء جاهز — ابدأ أول جلسة"
            LearnerStage.IN_PROGRESS -> "أنت في المسار الصحيح"
            LearnerStage.DONE_TODAY ->
                if (streak > 1) "يوم مكتمل · $streak أيام متتالية" else "أنجزت مهام اليوم"
            LearnerStage.RETURNING ->
                if (streak > 0) "حافظ على سلسلتك ($streak)" else "لنستأنف اليوم"
            LearnerStage.LAPSED ->
                if (daysSinceActive > 0) "مضى ${dayWord(daysSinceActive)} — لا بأس، نبدأ من جديد"
                else "لنعد للمسار"
        }

    /** Supporting line under the headline. */
    val subtitle: String
        get() = when (stage) {
            LearnerStage.EMPTY -> "استورد كورساً أو أضف كلمات لتفعيل خطتك اليومية"
            LearnerStage.READY -> "أول مراجعة تكفي لبدء سلسلتك"
            LearnerStage.IN_PROGRESS -> "أكمل هدف اليوم لتثبيت السلسلة"
            LearnerStage.DONE_TODAY -> "أحسنت — راجعة إضافية تزيد التثبيت"
            LearnerStage.RETURNING ->
                if (streakAtRisk) "سلسلتك في خطر — جلسة قصيرة تكفي لإنقاذها"
                else "جلسة قصيرة اليوم تحافظ على تقدمك"
            LearnerStage.LAPSED -> "ابدأ بـ 5 كلمات فقط — الاستمرارية أهم من الكمال"
        }

    /**
     * Progress bar value for the mascot card. Before any history exists we show
     * today's goal progress (0 at install) instead of a fabricated "momentum".
     */
    val barValue: Float get() = if (hasHistory) momentum else goalProgress

    val barLabel: String
        get() = if (hasHistory) "الزخم ${(momentum * 100).toInt()}%"
        else "هدف اليوم ${(goalProgress * 100).toInt()}%"

    private fun dayWord(n: Int) = when (n) {
        1 -> "يوم"
        2 -> "يومان"
        in 3..10 -> "$n أيام"
        else -> "$n يوماً"
    }
}

object EngagementEngine {

    /**
     * Derive the full engagement snapshot.
     *
     * @param rows          telemetry rows keyed by epoch-day
     * @param hasContent    the learner has lessons or vocabulary
     * @param goalProgress  today's reviews / daily goal, clamped 0..1
     * @param planDone      today's plan has tasks AND all are complete
     * @param planHasTasks  today's plan actually has something scheduled
     * @param hourOfDay     current hour (used for the "at risk" window)
     */
    fun derive(
        rows: Map<Long, DayStat>,
        hasContent: Boolean,
        goalProgress: Float,
        planDone: Boolean,
        planHasTasks: Boolean,
        hourOfDay: Int = LocalDate.now().let { java.time.LocalTime.now().hour },
    ): Engagement {
        val today = Telemetry.today()
        val activeDays = rows.filterValues { it.isActive }.keys
        val hasHistory = activeDays.isNotEmpty()
        val activeToday = rows[today]?.isActive == true

        val daysSinceActive = when {
            !hasHistory -> -1
            activeToday -> 0
            else -> (today - activeDays.max()).toInt()
        }

        val streak = Telemetry.currentStreak(rows)
        val best = Telemetry.bestStreak(rows)
        val last14 = (0 until 14).count { off -> rows[today - off]?.isActive == true }

        // Momentum = recent consistency + streak depth + today's effort.
        //
        // The window grows with the learner: judging someone's 2nd day against
        // a full 14-day window would show a demoralising 7%. We therefore
        // measure consistency over the days they have actually been using the
        // app (min 3, max 14), and let today's progress carry real weight.
        val momentum = if (!hasHistory) 0f else {
            val firstDay = activeDays.min()
            val lifespan = ((today - firstDay).toInt() + 1).coerceIn(1, 14)
            val window = lifespan.coerceAtLeast(3)
            val activeInWindow = (0 until window).count { rows[today - it]?.isActive == true }
            val consistency = (activeInWindow.toFloat() / window).coerceIn(0f, 1f)
            val depth = (streak / 21f).coerceAtMost(1f)
            val todayEffort = goalProgress.coerceIn(0f, 1f)
            (consistency * 0.5f + depth * 0.2f + todayEffort * 0.3f).coerceIn(0f, 1f)
        }

        // "Done today" requires REAL completion, never an empty plan.
        val goalMet = goalProgress >= 1f
        val doneToday = activeToday && (goalMet || (planHasTasks && planDone))

        val stage = when {
            !hasContent -> LearnerStage.EMPTY
            !hasHistory -> LearnerStage.READY
            doneToday -> LearnerStage.DONE_TODAY
            activeToday -> LearnerStage.IN_PROGRESS
            daysSinceActive >= 2 -> LearnerStage.LAPSED
            else -> LearnerStage.RETURNING
        }

        val atRisk = streak > 0 && !activeToday && hourOfDay >= 18

        return Engagement(
            stage = stage,
            streak = streak,
            bestStreak = best,
            daysSinceActive = daysSinceActive,
            momentum = momentum,
            hasHistory = hasHistory,
            hasContent = hasContent,
            goalProgress = goalProgress.coerceIn(0f, 1f),
            activeDaysLast14 = last14,
            streakAtRisk = atRisk,
        )
    }

    /** The last 7 calendar days as (date, active) — for the streak dot rail. */
    fun weekDots(rows: Map<Long, DayStat>): List<Pair<LocalDate, Boolean>> {
        val today = Telemetry.today()
        return (6 downTo 0).map { off ->
            val d = today - off
            LocalDate.ofEpochDay(d) to (rows[d]?.isActive == true)
        }
    }
}
