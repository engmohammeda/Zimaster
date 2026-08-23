package com.zmastery.english

import com.zmastery.english.data.Fsrs
import org.junit.Assert.*
import org.junit.Test

class FsrsTest {

    // ─── Retrievability ───

    @Test
    fun `retrievability at time zero is 1`() {
        val r = Fsrs.retrievability(0.0, 5.0)
        assertEquals(1.0, r, 0.001)
    }

    @Test
    fun `retrievability decays over time`() {
        val r1 = Fsrs.retrievability(1.0, 10.0)
        val r2 = Fsrs.retrievability(5.0, 10.0)
        val r3 = Fsrs.retrievability(20.0, 10.0)
        assertTrue("R should decrease", r1 > r2)
        assertTrue("R should decrease more", r2 > r3)
    }

    @Test
    fun `retrievability at stability equals approximately 0_9`() {
        // By definition, R(S) ≈ 0.9 for FSRS
        val r = Fsrs.retrievability(5.0, 5.0)
        assertEquals(0.9, r, 0.01)
    }

    @Test
    fun `retrievability is zero for zero stability`() {
        val r = Fsrs.retrievability(5.0, 0.0)
        assertEquals(0.0, r, 0.001)
    }

    @Test
    fun `retrievability never exceeds 1`() {
        val r = Fsrs.retrievability(0.0, 100.0)
        assertTrue(r <= 1.0)
    }

    // ─── Interval ───

    @Test
    fun `interval is at least 1 day`() {
        val ivl = Fsrs.intervalFor(0.1, 0.9)
        assertTrue("Interval >= 1", ivl >= 1)
    }

    @Test
    fun `higher stability gives longer interval`() {
        val ivl1 = Fsrs.intervalFor(5.0, 0.9)
        val ivl2 = Fsrs.intervalFor(20.0, 0.9)
        assertTrue("Higher stability → longer interval", ivl2 > ivl1)
    }

    @Test
    fun `higher desired retention gives shorter interval`() {
        val ivl1 = Fsrs.intervalFor(10.0, 0.85)
        val ivl2 = Fsrs.intervalFor(10.0, 0.95)
        assertTrue("Higher retention → shorter interval", ivl1 > ivl2)
    }

    @Test
    fun `interval respects max limit`() {
        val ivl = Fsrs.intervalFor(10000.0, 0.9, maxInterval = 365)
        assertEquals(365, ivl)
    }

    // ─── Schedule (new card) ───

    @Test
    fun `schedule new card with Good rating`() {
        val result = Fsrs.schedule(
            stability = 0.0,
            difficulty = 0.0,
            elapsedDays = 0.0,
            rating = 3, // Good
            desiredRetention = 0.9,
        )
        assertTrue("New card gets positive stability", result.stability > 0)
        assertTrue("New card gets valid difficulty", result.difficulty in 1.0..10.0)
        assertTrue("Interval is positive", result.intervalDays > 0)
    }

    @Test
    fun `Again rating gives short interval`() {
        val again = Fsrs.schedule(0.0, 0.0, 0.0, 1, 0.9)
        val good = Fsrs.schedule(0.0, 0.0, 0.0, 3, 0.9)
        assertTrue("Again interval < Good interval",
            again.intervalDays <= good.intervalDays)
    }

    @Test
    fun `Easy rating gives longest interval for new card`() {
        val hard = Fsrs.schedule(0.0, 0.0, 0.0, 2, 0.9)
        val good = Fsrs.schedule(0.0, 0.0, 0.0, 3, 0.9)
        val easy = Fsrs.schedule(0.0, 0.0, 0.0, 4, 0.9)
        assertTrue("Easy > Good", easy.intervalDays >= good.intervalDays)
        assertTrue("Good >= Hard", good.intervalDays >= hard.intervalDays)
    }

    // ─── Review existing card ───

    @Test
    fun `review increases stability on Good`() {
        val result = Fsrs.schedule(
            stability = 5.0,
            difficulty = 5.0,
            elapsedDays = 5.0,
            rating = 3, // Good
            desiredRetention = 0.9,
        )
        assertTrue("Stability should increase", result.stability > 5.0)
    }

    @Test
    fun `Again on review decreases stability`() {
        val result = Fsrs.schedule(
            stability = 10.0,
            difficulty = 5.0,
            elapsedDays = 10.0,
            rating = 1, // Again
            desiredRetention = 0.9,
        )
        assertTrue("Stability should decrease after Again", result.stability < 10.0)
    }
}
