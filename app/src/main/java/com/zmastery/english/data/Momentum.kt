package com.zmastery.english.data

import kotlin.math.roundToInt

// ==========================================================================
//  المرحلة الأولى — الهندسة النفسية العميقة
//  The 3D Momentum Model.
//
//  WHY traditional learning apps fail: they reduce a learner's self-worth to a
//  single consecutive-day counter. One missed day (travel, illness, a busy
//  shift) resets it to zero and triggers the "what-the-hell effect" — the
//  learner concludes the whole effort is ruined and uninstalls.
//
//  Z-Mastery therefore measures commitment across THREE independent axes, so a
//  single bad day can never erase the whole story:
//
//        ┌─────────────────────────────────────┐
//        │      زخم التعلم والالتزام            │
//        └──────────────────┬──────────────────┘
//           ┌──────────────┼──────────────┐
//     🔥 سلسلة الحماسة   🌱 رصيد الاستمرارية   ⭐ مستوى الإتقان
//      (Daily Streak)     (Continuity)      (Mastery Level)
//      المحرك اليومي       الدرع النفسي        مقياس الجودة
//
//   🔥 Daily Streak     — the engine. Kept alive by a 3–5 minute micro-habit
//                         (الورد اليومي) so busy days stay winnable.
//   🌱 Continuity Ratio — the psychological shield. Active days / last 30 days.
//                         Even at streak 0, an 86% month is still visible proof
//                         that the effort was real.
//   ⭐ Mastery Level    — the quality gauge. Rewards linguistic DEPTH so the
//                         learner cannot game the system with easy cards.
// ==========================================================================

/** The daily micro-habit (الورد اليومي) that protects the streak. */
data class MicroHabit(
    val id: String,
    val title: String,
    val detail: String,
    val minutes: Int,
    val icon: String,
    val route: String,
    val target: Int,
) {
    /** "3–5 دقائق" style label. */
    val timeLabel: String get() = "$minutes دقائق"
}

object MicroHabits {
    /**
     * Every option is deliberately tiny. The philosophy: continuity is easier
     * when the psychological barrier to STARTING is nearly zero.
     */
    val all = listOf(
        MicroHabit(
            "micro_review", "راجع 5 بطاقات", "أسرع طريق للحفاظ على الشعلة",
            3, "brain", "review", 5,
        ),
        MicroHabit(
            "micro_listen", "استمع لجملتين", "درّب أذنك على النطق الصحيح",
            3, "ear", "review", 2,
        ),
        MicroHabit(
            "micro_story", "اقرأ صفحة قصة", "كلماتك في سياق حقيقي",
            5, "story", "stories", 1,
        ),
        MicroHabit(
            "micro_word", "أضف كلمة جديدة", "وسّع قاموسك بخطوة واحدة",
            3, "add", "vocab", 1,
        ),
    )

    fun byId(id: String): MicroHabit? = all.firstOrNull { it.id == id }
}

/**
 * The 3D snapshot. Fully derived from telemetry — never stored, so it can never
 * drift out of sync with what the learner actually did.
 */
data class Momentum3D(
    // ── 🔥 axis 1 · daily streak ──
    val streak: Int,
    val bestStreak: Int,
    val microHabitDone: Boolean,
    val streakSavedByMicro: Boolean,
    // ── 🌱 axis 2 · continuity ──
    val continuityRatio: Float,       // 0..1 over the last 30 days
    val activeDays30: Int,
    val continuityBest: Float,        // best 30-day window ever achieved
    // ── ⭐ axis 3 · mastery ──
    val masteryLevel: Float,          // 0..1 composite depth score
    val masteryWords: Float,          // sub-scores, 0..1 each
    val masteryLessons: Float,
    val masteryAccuracy: Float,
    val cefr: String,
    val cefrProgress: Float,
    val nextCefr: String,
) {
    /** Combined commitment score — the number at the top of the pyramid. */
    val overall: Float
        get() = (streakScore * 0.30f + continuityRatio * 0.35f + masteryLevel * 0.35f)
            .coerceIn(0f, 1f)

    /** Streak normalised against a 30-day habit-formation horizon. */
    val streakScore: Float get() = (streak / 30f).coerceIn(0f, 1f)

    val continuityPct: Int get() = (continuityRatio * 100).roundToInt()
    val masteryPct: Int get() = (masteryLevel * 100).roundToInt()
    val overallPct: Int get() = (overall * 100).roundToInt()

    /**
     * The reassurance line shown when the streak has broken. This is the exact
     * moment the "what-the-hell effect" strikes, so the copy must redirect the
     * learner's attention to the evidence that their effort still counts.
     */
    val shieldMessage: String
        get() = when {
            streak == 0 && continuityPct >= 70 ->
                "سلسلتك بدأت من جديد، لكن رصيدك الشهري $continuityPct% — إنجازك محفوظ ولم يضِع"
            streak == 0 && continuityPct >= 40 ->
                "درست $activeDays30 يوماً من آخر 30 — الرصيد باقٍ، أكمل من حيث توقفت"
            streak == 0 && activeDays30 > 0 ->
                "كل يوم درسته لا يزال محسوباً لك — ابدأ اليوم بالورد المصغّر"
            streak == 0 ->
                "أنجز الورد المصغّر (3 دقائق) لتبدأ سلسلتك"
            else -> ""
        }

    val continuityLabel: String
        get() = when {
            continuityRatio >= 0.9f -> "التزام استثنائي"
            continuityRatio >= 0.7f -> "التزام قوي"
            continuityRatio >= 0.5f -> "التزام جيد"
            continuityRatio >= 0.25f -> "قابل للتحسين"
            else -> "لنبدأ البناء"
        }

    val masteryLabel: String
        get() = when {
            masteryLevel >= 0.85f -> "إتقان متقدّم"
            masteryLevel >= 0.6f -> "إتقان راسخ"
            masteryLevel >= 0.35f -> "في طريق الإتقان"
            masteryLevel >= 0.15f -> "أساس ينمو"
            else -> "بداية الرحلة"
        }
}

object MomentumEngine {

    /** Days in the continuity window. 30 = one honest month of effort. */
    const val CONTINUITY_WINDOW = 30

    /**
     * @param rows            telemetry, keyed by epoch-day
     * @param microHabitDone  today's micro-habit is complete
     * @param masteredWords   words at high FSRS stability
     * @param totalWords      dictionary size
     * @param lessonsDone     completed lessons
     * @param totalLessons    lessons available
     * @param examAvg         average exam percentage (0..100)
     */
    fun derive(
        rows: Map<Long, DayStat>,
        microHabitDone: Boolean,
        masteredWords: Int,
        totalWords: Int,
        lessonsDone: Int,
        totalLessons: Int,
        examAvg: Int,
    ): Momentum3D {
        val today = Telemetry.today()
        val streak = Telemetry.currentStreak(rows)
        val best = Telemetry.bestStreak(rows)

        // ── 🌱 continuity: active days in the trailing 30-day window ──
        val active30 = (0 until CONTINUITY_WINDOW).count { rows[today - it]?.isActive == true }
        val ratio = (active30.toFloat() / CONTINUITY_WINDOW).coerceIn(0f, 1f)

        // Best 30-day window ever — proof of the learner's personal ceiling.
        val bestRatio = bestContinuity(rows)

        // ── ⭐ mastery: three depth signals, deliberately hard to game ──
        // Mastered words dominate: they can only be earned through real FSRS
        // stability over real time, which no amount of easy tapping can fake.
        val wordScore = (masteredWords / 500f).coerceIn(0f, 1f)
        val lessonScore = if (totalLessons > 0) {
            (lessonsDone.toFloat() / totalLessons).coerceIn(0f, 1f)
        } else 0f
        val accScore = (examAvg / 100f).coerceIn(0f, 1f)
        val mastery = (wordScore * 0.5f + lessonScore * 0.3f + accScore * 0.2f).coerceIn(0f, 1f)

        val (cefr, cefrProg) = Telemetry.estimatedCefr(masteredWords, lessonsDone, examAvg)

        return Momentum3D(
            streak = streak,
            bestStreak = best,
            microHabitDone = microHabitDone,
            streakSavedByMicro = microHabitDone && streak > 0,
            continuityRatio = ratio,
            activeDays30 = active30,
            continuityBest = bestRatio,
            masteryLevel = mastery,
            masteryWords = wordScore,
            masteryLessons = lessonScore,
            masteryAccuracy = accScore,
            cefr = cefr,
            cefrProgress = cefrProg,
            nextCefr = Telemetry.nextCefr(cefr),
        )
    }

    /** Highest continuity ratio across every historical 30-day window. */
    private fun bestContinuity(rows: Map<Long, DayStat>): Float {
        val activeDays = rows.filterValues { it.isActive }.keys
        if (activeDays.isEmpty()) return 0f
        val first = activeDays.min()
        val last = Telemetry.today()
        var best = 0
        var cursor = first
        while (cursor <= last) {
            val n = (0 until CONTINUITY_WINDOW).count { rows[cursor + it]?.isActive == true }
            if (n > best) best = n
            cursor++
        }
        return (best.toFloat() / CONTINUITY_WINDOW).coerceIn(0f, 1f)
    }

    /** The 30 continuity cells (oldest → today) for the shield visual. */
    fun continuityCells(rows: Map<Long, DayStat>): List<Boolean> {
        val today = Telemetry.today()
        return (CONTINUITY_WINDOW - 1 downTo 0).map { off -> rows[today - off]?.isActive == true }
    }
}
