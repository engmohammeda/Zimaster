package com.zmastery.english

import com.zmastery.english.data.ChestMood
import com.zmastery.english.data.DecayState
import com.zmastery.english.data.EnigmaStreakEngine
import org.junit.Assert.*
import org.junit.Test

class EnigmaStreakTest {

    // ─── computeDecay ───

    @Test
    fun `SAFE when minimum is done regardless of time`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = true,
            currentStreak = 10,
            hourNow = 23,
            minuteNow = 55,
            streakBroken = false,
        )
        assertEquals(ChestMood.SAFE, result.mood)
        assertEquals(0f, result.severity, 0.001f)
    }

    @Test
    fun `IDLE when minimum not done but before crack hour`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = false,
            currentStreak = 10,
            hourNow = 15,
            minuteNow = 30,
            streakBroken = false,
        )
        assertEquals(ChestMood.IDLE, result.mood)
    }

    @Test
    fun `IDLE when streak is too short even after crack hour`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = false,
            currentStreak = 2,
            hourNow = 22,
            minuteNow = 0,
            streakBroken = false,
        )
        assertEquals(ChestMood.IDLE, result.mood)
    }

    @Test
    fun `CRACKING when eligible after crack hour`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = false,
            currentStreak = 5,
            hourNow = 21,
            minuteNow = 0,
            streakBroken = false,
        )
        assertEquals(ChestMood.CRACKING, result.mood)
        assertTrue("Severity should be > 0", result.severity > 0f)
    }

    @Test
    fun `severity increases as midnight approaches`() {
        val early = EnigmaStreakEngine.computeDecay(
            minimumDone = false, currentStreak = 5,
            hourNow = 20, minuteNow = 30, streakBroken = false,
        )
        val late = EnigmaStreakEngine.computeDecay(
            minimumDone = false, currentStreak = 5,
            hourNow = 23, minuteNow = 30, streakBroken = false,
        )
        assertTrue("Late severity > early severity",
            late.severity > early.severity)
    }

    @Test
    fun `BROKEN when streak is broken`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = false,
            currentStreak = 0,
            hourNow = 10,
            minuteNow = 0,
            streakBroken = true,
        )
        assertEquals(ChestMood.BROKEN, result.mood)
        assertEquals(1f, result.severity, 0.001f)
    }

    @Test
    fun `BROKEN takes priority over everything else`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = true,
            currentStreak = 100,
            hourNow = 23,
            minuteNow = 59,
            streakBroken = true,
        )
        assertEquals(ChestMood.BROKEN, result.mood)
    }

    @Test
    fun `time left calculation is correct at 22 30`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = false, currentStreak = 5,
            hourNow = 22, minuteNow = 30, streakBroken = false,
        )
        // 22:30 → midnight = 1h 30m
        assertEquals(1, result.hoursLeft)
        assertEquals(30, result.minutesLeft)
    }

    @Test
    fun `time left at midnight is zero`() {
        val result = EnigmaStreakEngine.computeDecay(
            minimumDone = false, currentStreak = 5,
            hourNow = 0, minuteNow = 0, streakBroken = false,
        )
        // 00:00 → 24 hours to next midnight
        assertEquals(24, result.hoursLeft)
    }

    // ─── DecayState message formatting ───

    @Test
    fun `SAFE message is positive`() {
        val state = DecayState(ChestMood.SAFE, 5, 3, 30, 0f)
        assertTrue(state.message.contains("مؤمَّن"))
    }

    @Test
    fun `CRACKING message mentions streak count`() {
        val state = DecayState(ChestMood.CRACKING, 7, 1, 30, 0.5f)
        assertTrue(state.message.contains("7"))
    }

    @Test
    fun `BROKEN message mentions rescue`() {
        val state = DecayState(ChestMood.BROKEN, 0, 0, 0, 1f)
        assertTrue(state.message.contains("إنقاذ"))
    }
}
