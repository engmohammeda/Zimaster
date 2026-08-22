package com.zmastery.english.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * FSRS v5 — Free Spaced Repetition Scheduler.
 *
 * A modern, evidence-based memory model that replaces the older SM-2 algorithm.
 * It represents each memory with three variables:
 *
 *  • Stability (S)        — days for retrievability to fall from 100% to 90%.
 *  • Difficulty (D)       — intrinsic hardness of the item (1 easy .. 10 hard).
 *  • Retrievability (R)   — current probability of recall at review time.
 *
 * The scheduler picks the next interval so that recall probability drops to the
 * learner's desired retention (default 90%). This is far more accurate than SM-2
 * because the interval adapts to both the item's difficulty and the exact time
 * elapsed since the last review.
 *
 * Ratings: 1 = Again, 2 = Hard, 3 = Good, 4 = Easy.
 */
object Fsrs {

    /** Card learning states. */
    enum class Phase { NEW, LEARNING, REVIEW, RELEARNING }

    /** Default optimized weights (the 19 FSRS-5 parameters). */
    val DEFAULT_W = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046,
        1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315,
        2.9898, 0.51655, 0.6621,
    )

    private const val DECAY = -0.5
    // FACTOR = 0.9^(1/DECAY) - 1
    private val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0

    /** Result of scheduling one review. */
    data class Sched(
        val stability: Double,
        val difficulty: Double,
        val intervalDays: Int,
        val phase: Phase,
        val retrievabilityAtReview: Double,
    )

    /**
     * Retrievability after [elapsedDays] with the given [stability].
     * R(t) = (1 + FACTOR * t / S) ^ DECAY.
     */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY).coerceIn(0.0, 1.0)
    }

    /** Days until retrievability decays to [desiredRetention]. */
    fun intervalFor(stability: Double, desiredRetention: Double, maxInterval: Int = 365): Int {
        val ivl = (stability / FACTOR) * (desiredRetention.pow(1.0 / DECAY) - 1.0)
        return ivl.toInt().coerceIn(1, maxInterval)
    }

    private fun initStability(w: DoubleArray, rating: Int): Double =
        w[rating - 1].coerceAtLeast(0.1)

    private fun initDifficulty(w: DoubleArray, rating: Int): Double =
        (w[4] - exp(w[5] * (rating - 1)) + 1.0).coerceIn(1.0, 10.0)

    private fun clampD(d: Double) = d.coerceIn(1.0, 10.0)

    private fun nextDifficulty(w: DoubleArray, d: Double, rating: Int): Double {
        val deltaD = -w[6] * (rating - 3)
        val damped = d + deltaD * (10.0 - d) / 9.0        // linear damping
        val meanRev = w[7] * initDifficulty(w, 4) + (1.0 - w[7]) * damped
        return clampD(meanRev)
    }

    /** Stability after a successful review (Hard/Good/Easy). */
    private fun nextRecallStability(w: DoubleArray, d: Double, s: Double, r: Double, rating: Int): Double {
        val hardPenalty = if (rating == 2) w[15] else 1.0
        val easyBonus = if (rating == 4) w[16] else 1.0
        val inc = exp(w[8]) * (11.0 - d) * s.pow(-w[9]) *
            (exp(w[10] * (1.0 - r)) - 1.0) * hardPenalty * easyBonus
        return (s * (1.0 + inc)).coerceAtLeast(0.1)
    }

    /** Stability after a lapse (Again). */
    private fun nextForgetStability(w: DoubleArray, d: Double, s: Double, r: Double): Double {
        val sf = w[11] * d.pow(-w[12]) * ((s + 1.0).pow(w[13]) - 1.0) * exp(w[14] * (1.0 - r))
        return sf.coerceIn(0.1, s) // a lapse never increases stability
    }

    /** Short-term (same-day / learning-step) stability adjustment. */
    private fun shortTermStability(w: DoubleArray, s: Double, rating: Int): Double =
        (s * exp(w[17] * (rating - 3 + w[18]))).coerceAtLeast(0.1)

    /**
     * Schedule the next review.
     *
     * @param w                 FSRS weights.
     * @param rating            1 Again · 2 Hard · 3 Good · 4 Easy.
     * @param phase             current phase of the card.
     * @param stability         current stability (ignored for NEW).
     * @param difficulty        current difficulty (ignored for NEW).
     * @param elapsedDays       days since the last review.
     * @param desiredRetention  target recall probability (e.g. 0.90).
     */
    fun schedule(
        w: DoubleArray,
        rating: Int,
        phase: Phase,
        stability: Double,
        difficulty: Double,
        elapsedDays: Double,
        desiredRetention: Double,
        maxInterval: Int = 365,
    ): Sched {
        val r = if (phase == Phase.NEW) 0.0 else retrievability(elapsedDays, stability)

        if (phase == Phase.NEW) {
            val s0 = initStability(w, rating)
            val d0 = initDifficulty(w, rating)
            // Learning-step feel: Again/Hard stay short; Good/Easy graduate.
            return when (rating) {
                1 -> Sched(shortTermStability(w, s0, 1), d0, 1, Phase.LEARNING, r)
                2 -> Sched(s0, d0, 1, Phase.LEARNING, r)
                else -> {
                    val ivl = intervalFor(s0, desiredRetention, maxInterval)
                    Sched(s0, d0, ivl, Phase.REVIEW, r)
                }
            }
        }

        val newD = nextDifficulty(w, difficulty, rating)

        if (rating == 1) {
            // Lapse → relearning, short interval.
            val sf = nextForgetStability(w, newD, stability, r)
            return Sched(sf, newD, 1, Phase.RELEARNING, r)
        }

        val newS = nextRecallStability(w, newD, stability, r, rating)
        val ivl = intervalFor(newS, desiredRetention, maxInterval)
        return Sched(newS, newD, ivl, Phase.REVIEW, r)
    }

    /** Human-readable memory strength 0..1 from stability (log scale, ~180d = full). */
    fun strengthFromStability(stability: Double): Float {
        if (stability <= 0.0) return 0f
        return (ln(stability + 1.0) / ln(181.0)).toFloat().coerceIn(0f, 1f)
    }
}
