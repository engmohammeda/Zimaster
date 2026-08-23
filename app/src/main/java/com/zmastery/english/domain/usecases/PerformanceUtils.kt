package com.zmastery.english.domain.usecases

/**
 * Performance Utilities — throttle, debounce, and optimization helpers.
 *
 * Used across the app to prevent excessive recomputation, redundant API calls,
 * and unnecessary widget refreshes.
 */
object PerformanceUtils {

    // ─── Throttle ───

    /**
     * Simple time-based throttle.
     *
     * Returns true if at least [intervalMs] have passed since the last call.
     *
     * Usage:
     * ```
     * private val widgetThrottle = PerformanceUtils.Throttle(5 * 60_000L)
     *
     * fun refreshWidget() {
     *     if (widgetThrottle.allow()) {
     *         ZMasteryWidget.refreshAll(context)
     *     }
     * }
     * ```
     */
    class Throttle(private val intervalMs: Long) {
        private var lastCallMs: Long = 0L

        /** Returns true if enough time has passed since the last allowed call. */
        fun allow(nowMs: Long = System.currentTimeMillis()): Boolean {
            if (nowMs - lastCallMs >= intervalMs) {
                lastCallMs = nowMs
                return true
            }
            return false
        }

        /** Time remaining until the next call is allowed (ms). */
        fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long =
            (intervalMs - (nowMs - lastCallMs)).coerceAtLeast(0L)

        /** Reset the throttle (allow next call immediately). */
        fun reset() { lastCallMs = 0L }
    }

    // ─── Debounce (synchronous counter) ───

    /**
     * Counts rapid calls and only signals "ready" after a quiet period.
     *
     * Useful with viewModelScope.launch + delay() for debounced saves:
     * ```
     * private val saveDebounce = PerformanceUtils.Debounce(400L)
     *
     * fun onSomethingChanged() {
     *     saveDebounce.trigger {
     *         viewModelScope.launch {
     *             delay(saveDebounce.delayMs)
     *             if (saveDebounce.isLastTrigger()) save()
     *         }
     *     }
     * }
     * ```
     */
    class Debounce(val delayMs: Long) {
        @Volatile
        private var triggerCount: Long = 0L

        /** Increment the trigger counter. */
        fun trigger(action: () -> Unit = {}) {
            triggerCount++
            action()
        }

        /**
         * Check if this is the last trigger (no new triggers since).
         * Call this after the delay to see if a newer trigger invalidated this one.
         */
        fun isLastTrigger(): Boolean {
            val current = triggerCount
            return current == triggerCount  // Still the same → no new triggers
        }
    }

    // ─── Cache ───

    /**
     * Simple time-based cache for expensive computations.
     *
     * ```
     * private val statsCache = PerformanceUtils.TimedCache<StatsResult>(30_000L)
     *
     * fun getStats(): StatsResult = statsCache.getOrCompute {
     *     computeExpensiveStats()
     * }
     * ```
     */
    class TimedCache<T>(private val ttlMs: Long) {
        private var value: T? = null
        private var cachedAtMs: Long = 0L

        /** Get the cached value or compute a new one if expired. */
        fun getOrCompute(nowMs: Long = System.currentTimeMillis(), compute: () -> T): T {
            val cached = value
            if (cached != null && (nowMs - cachedAtMs) < ttlMs) {
                return cached
            }
            val fresh = compute()
            value = fresh
            cachedAtMs = nowMs
            return fresh
        }

        /** Invalidate the cache. */
        fun invalidate() {
            value = null
            cachedAtMs = 0L
        }

        /** Whether the cache currently holds a valid (non-expired) value. */
        fun isValid(nowMs: Long = System.currentTimeMillis()): Boolean =
            value != null && (nowMs - cachedAtMs) < ttlMs
    }

    // ─── Compose optimization helpers ───

    /**
     * Compute a stable hash key for a list to use with `key()` in LazyColumn.
     *
     * Much faster than using the list itself as a key (which recomputes
     * equals for every item on every recomposition).
     */
    fun listStableKey(items: List<Any>): Int {
        if (items.isEmpty()) return 0
        // Use size + first + last hashCode — O(1) and unique enough for most cases
        return items.size * 31 + items.first().hashCode() + items.last().hashCode() * 7
    }

    /**
     * Estimate if a recomposition is worth doing based on content changes.
     *
     * @param oldSize previous list size
     * @param newSize current list size
     * @param oldHash previous stable key
     * @param newHash current stable key
     * @return true if the UI should recompose
     */
    fun shouldRecompose(oldSize: Int, newSize: Int, oldHash: Int, newHash: Int): Boolean {
        return oldSize != newSize || oldHash != newHash
    }

    // ─── JSON size estimation ───

    /**
     * Estimate the serialized JSON size of a string without actually serializing.
     *
     * Used to warn before saving very large states (> 2MB).
     */
    fun estimateJsonSize(itemCount: Int, avgItemChars: Int = 500): Long {
        return itemCount.toLong() * avgItemChars
    }

    /** Size threshold for "large" state that should trigger a warning. */
    const val LARGE_STATE_THRESHOLD = 2_000_000L
}
