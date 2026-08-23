package com.zmastery.english.domain.usecases

import com.zmastery.english.data.ChestMood
import com.zmastery.english.data.DecayState
import com.zmastery.english.data.EnigmaStreakEngine

/**
 * Streak Manager Use Case — manages streak state and decay calculations.
 *
 * Extracted from AppViewModel to be independently testable and reusable.
 * Contains no Android dependencies — pure Kotlin.
 *
 * Responsibilities:
 *  - Compute streak decay state (CRACKING / BROKEN / SAFE / IDLE)
 *  - Evaluate whether a day should be counted in the streak
 *  - Track streak history (best streak, current streak)
 *  - Manage rescue mission eligibility
 */
class StreakManager {

    /**
     * Compute the current decay state based on time and streak.
     *
     * @param minimumDone whether the daily minimum has been completed
     * @param currentStreak the current streak count
     * @param streakBroken whether the streak has been broken
     * @param hourNow current hour (0-23)
     * @param minuteNow current minute (0-59)
     */
    fun computeDecay(
        minimumDone: Boolean,
        currentStreak: Int,
        streakBroken: Boolean,
        hourNow: Int,
        minuteNow: Int,
    ): DecayState = EnigmaStreakEngine.computeDecay(
        minimumDone = minimumDone,
        currentStreak = currentStreak,
        hourNow = hourNow,
        minuteNow = minuteNow,
        streakBroken = streakBroken,
    )

    /**
     * Compute decay using the device's current time.
     * Uses java.util.Calendar for API 24+ compatibility.
     */
    fun computeDecayNow(
        minimumDone: Boolean,
        currentStreak: Int,
        streakBroken: Boolean,
    ): DecayState {
        val cal = java.util.Calendar.getInstance()
        return computeDecay(
            minimumDone = minimumDone,
            currentStreak = currentStreak,
            streakBroken = streakBroken,
            hourNow = cal.get(java.util.Calendar.HOUR_OF_DAY),
            minuteNow = cal.get(java.util.Calendar.MINUTE),
        )
    }

    /**
     * Determine whether a streak day should be earned based on activity.
     *
     * Conditions (at least one must be true):
     *  1. All daily tasks are completed
     *  2. The micro-habit (daily minimum) is done
     *  3. The day hasn't been earned yet today
     *
     * @param tasksDone number of completed daily tasks
     * @param tasksTotal total number of daily tasks
     * @param microHabitDone whether the micro-habit is completed
     * @param alreadyEarned whether the day was already earned
     */
    fun shouldEarnDay(
        tasksDone: Int,
        tasksTotal: Int,
        microHabitDone: Boolean,
        alreadyEarned: Boolean,
    ): Boolean {
        if (alreadyEarned) return false
        return microHabitDone || (tasksTotal > 0 && tasksDone >= tasksTotal)
    }

    /**
     * Calculate the streak message for display.
     */
    fun streakMessage(streak: Int, mood: ChestMood): String = when {
        mood == ChestMood.BROKEN -> "سلسلتك انكسرت — أنقذها الآن!"
        mood == ChestMood.CRACKING -> "⚠️ سلسلتك ($streak يوم) في خطر!"
        streak == 0 -> "ابدأ سلسلتك اليوم 🔥"
        streak == 1 -> "يوم واحد — واصل!"
        streak < 7 -> "$streak أيام — بداية ممتازة 🔥"
        streak < 30 -> "$streak يوم — أنت ملتزم! 💪"
        streak < 100 -> "$streak يوم — إنجاز حقيقي! 🏆"
        else -> "$streak يوم — أسطورة! 👑"
    }

    /**
     * Check if a rescue mission should be offered.
     *
     * A rescue is offered when:
     *  - The streak was broken (streakBroken = true)
     *  - The previous streak was significant (>= 3 days)
     *  - No rescue has been offered today yet
     */
    fun shouldOfferRescue(
        streakBroken: Boolean,
        streakBeforeBreak: Int,
        lastRescueOfferDay: Long,
        todayEpochDay: Long,
    ): Boolean {
        if (!streakBroken) return false
        if (streakBeforeBreak < DecayState.CRACK_MIN_STREAK) return false
        if (lastRescueOfferDay == todayEpochDay) return false
        return true
    }

    /**
     * Get the motivational tier based on streak length.
     */
    fun streakTier(streak: Int): StreakTier = when {
        streak == 0 -> StreakTier.NONE
        streak < 3 -> StreakTier.STARTER
        streak < 7 -> StreakTier.BUILDING
        streak < 14 -> StreakTier.COMMITTED
        streak < 30 -> StreakTier.DEDICATED
        streak < 100 -> StreakTier.CHAMPION
        else -> StreakTier.LEGEND
    }
}

enum class StreakTier(val label: String, val emoji: String) {
    NONE("لم تبدأ بعد", "🌱"),
    STARTER("مبتدئ", "🔥"),
    BUILDING("يبني عادته", "🔥🔥"),
    COMMITTED("ملتزم", "💪"),
    DEDICATED("مثابر", "🏆"),
    CHAMPION("بطل", "⭐"),
    LEGEND("أسطورة", "👑"),
}
