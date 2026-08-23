package com.zmastery.english

import com.zmastery.english.domain.usecases.PerformanceUtils
import org.junit.Assert.*
import org.junit.Test

class PerformanceUtilsTest {

    // ─── Throttle ───

    @Test
    fun `throttle allows first call`() {
        val throttle = PerformanceUtils.Throttle(1000L)
        assertTrue(throttle.allow())
    }

    @Test
    fun `throttle blocks immediate second call`() {
        val throttle = PerformanceUtils.Throttle(1000L)
        throttle.allow()
        assertFalse(throttle.allow())
    }

    @Test
    fun `throttle allows after interval`() {
        val throttle = PerformanceUtils.Throttle(100L)
        throttle.allow(nowMs = 1000L)
        assertTrue(throttle.allow(nowMs = 1200L))
    }

    @Test
    fun `throttle remaining time`() {
        val throttle = PerformanceUtils.Throttle(1000L)
        throttle.allow(nowMs = 0L)
        assertEquals(600L, throttle.remainingMs(nowMs = 400L))
    }

    @Test
    fun `throttle reset allows immediately`() {
        val throttle = PerformanceUtils.Throttle(1000L)
        throttle.allow()
        throttle.reset()
        assertTrue(throttle.allow())
    }

    // ─── Debounce ───

    @Test
    fun `debounce trigger increments count`() {
        val debounce = PerformanceUtils.Debounce(400L)
        debounce.trigger()
        debounce.trigger()
        assertTrue(debounce.isLastTrigger())
    }

    @Test
    fun `debounce delay is correct`() {
        val debounce = PerformanceUtils.Debounce(400L)
        assertEquals(400L, debounce.delayMs)
    }

    // ─── TimedCache ───

    @Test
    fun `cache computes on first access`() {
        val cache = PerformanceUtils.TimedCache<Int>(1000L)
        var computeCount = 0
        val value = cache.getOrCompute { computeCount++; 42 }
        assertEquals(42, value)
        assertEquals(1, computeCount)
    }

    @Test
    fun `cache returns cached value within TTL`() {
        val cache = PerformanceUtils.TimedCache<Int>(1000L)
        var computeCount = 0
        cache.getOrCompute(nowMs = 0L) { computeCount++; 42 }
        cache.getOrCompute(nowMs = 500L) { computeCount++; 99 }
        assertEquals(1, computeCount)
    }

    @Test
    fun `cache recomputes after TTL expires`() {
        val cache = PerformanceUtils.TimedCache<Int>(1000L)
        var computeCount = 0
        cache.getOrCompute(nowMs = 0L) { computeCount++; 42 }
        val value2 = cache.getOrCompute(nowMs = 1500L) { computeCount++; 99 }
        assertEquals(2, computeCount)
        assertEquals(99, value2)
    }

    @Test
    fun `cache invalidate forces recompute`() {
        val cache = PerformanceUtils.TimedCache<Int>(1000L)
        cache.getOrCompute { 42 }
        cache.invalidate()
        assertFalse(cache.isValid())
    }

    @Test
    fun `cache isValid reports correctly`() {
        val cache = PerformanceUtils.TimedCache<String>(500L)
        assertFalse(cache.isValid())
        cache.getOrCompute(nowMs = 0L) { "hello" }
        assertTrue(cache.isValid(nowMs = 200L))
        assertFalse(cache.isValid(nowMs = 600L))
    }

    // ─── List stable key ───

    @Test
    fun `empty list key is zero`() {
        assertEquals(0, PerformanceUtils.listStableKey(emptyList()))
    }

    @Test
    fun `same list produces same key`() {
        val list = listOf("a", "b", "c")
        assertEquals(
            PerformanceUtils.listStableKey(list),
            PerformanceUtils.listStableKey(list),
        )
    }

    @Test
    fun `different lists produce different keys`() {
        val list1 = listOf("a", "b", "c")
        val list2 = listOf("x", "y", "z")
        assertNotEquals(
            PerformanceUtils.listStableKey(list1),
            PerformanceUtils.listStableKey(list2),
        )
    }

    // ─── shouldRecompose ───

    @Test
    fun `recompose when size changes`() {
        assertTrue(PerformanceUtils.shouldRecompose(5, 6, 100, 100))
    }

    @Test
    fun `recompose when hash changes`() {
        assertTrue(PerformanceUtils.shouldRecompose(5, 5, 100, 200))
    }

    @Test
    fun `no recompose when nothing changes`() {
        assertFalse(PerformanceUtils.shouldRecompose(5, 5, 100, 100))
    }

    // ─── JSON size estimation ───

    @Test
    fun `estimate JSON size`() {
        val size = PerformanceUtils.estimateJsonSize(1000, 500)
        assertEquals(500_000L, size)
    }

    @Test
    fun `large state threshold is 2MB`() {
        assertEquals(2_000_000L, PerformanceUtils.LARGE_STATE_THRESHOLD)
    }
}
