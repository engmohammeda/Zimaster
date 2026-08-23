package com.zmastery.english

import com.zmastery.english.data.ChestMood
import com.zmastery.english.domain.usecases.StreakManager
import com.zmastery.english.domain.usecases.StreakTier
import org.junit.Assert.*
import org.junit.Test

class StreakManagerTest {

    private val manager = StreakManager()

    // ─── shouldEarnDay ───

    @Test
    fun `earn day when micro habit done`() {
        assertTrue(manager.shouldEarnDay(0, 4, true, false))
    }

    @Test
    fun `earn day when all tasks done`() {
        assertTrue(manager.shouldEarnDay(4, 4, false, false))
    }

    @Test
    fun `no earn when already earned`() {
        assertFalse(manager.shouldEarnDay(4, 4, true, true))
    }

    @Test
    fun `no earn when nothing done`() {
        assertFalse(manager.shouldEarnDay(1, 4, false, false))
    }

    // ─── streakMessage ───

    @Test
    fun `message for zero streak`() {
        val msg = manager.streakMessage(0, ChestMood.IDLE)
        assertTrue(msg.contains("ابدأ"))
    }

    @Test
    fun `message for broken streak`() {
        val msg = manager.streakMessage(0, ChestMood.BROKEN)
        assertTrue(msg.contains("انكسرت"))
    }

    @Test
    fun `message for cracking streak`() {
        val msg = manager.streakMessage(10, ChestMood.CRACKING)
        assertTrue(msg.contains("خطر"))
    }

    @Test
    fun `message for long streak`() {
        val msg = manager.streakMessage(150, ChestMood.SAFE)
        assertTrue(msg.contains("أسطورة"))
    }

    // ─── shouldOfferRescue ───

    @Test
    fun `offer rescue when streak broken with significant streak`() {
        assertTrue(manager.shouldOfferRescue(true, 10, 0L, 100L))
    }

    @Test
    fun `no rescue when streak not broken`() {
        assertFalse(manager.shouldOfferRescue(false, 10, 0L, 100L))
    }

    @Test
    fun `no rescue when streak too short`() {
        assertFalse(manager.shouldOfferRescue(true, 2, 0L, 100L))
    }

    @Test
    fun `no rescue when already offered today`() {
        assertFalse(manager.shouldOfferRescue(true, 10, 100L, 100L))
    }

    // ─── streakTier ───

    @Test
    fun `tier for zero`() {
        assertEquals(StreakTier.NONE, manager.streakTier(0))
    }

    @Test
    fun `tier for starter`() {
        assertEquals(StreakTier.STARTER, manager.streakTier(2))
    }

    @Test
    fun `tier for champion`() {
        assertEquals(StreakTier.CHAMPION, manager.streakTier(50))
    }

    @Test
    fun `tier for legend`() {
        assertEquals(StreakTier.LEGEND, manager.streakTier(365))
    }

    // ─── computeDecay ───

    @Test
    fun `computeDecay delegates correctly`() {
        val result = manager.computeDecay(
            minimumDone = true,
            currentStreak = 5,
            streakBroken = false,
            hourNow = 22,
            minuteNow = 0,
        )
        assertEquals(ChestMood.SAFE, result.mood)
    }
}
