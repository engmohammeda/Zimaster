package com.zmastery.english.domain.usecases

import com.zmastery.english.data.Fsrs
import com.zmastery.english.data.FsrsPhase
import com.zmastery.english.data.RecallSource
import com.zmastery.english.data.VocabWord

/**
 * Review Scheduler Use Case — pure logic for FSRS-based review scheduling.
 *
 * Extracted from AppViewModel to be independently testable and reusable.
 * Contains no Android dependencies — pure Kotlin.
 *
 * Responsibilities:
 *  - Map review stages to FSRS grades and recall sources
 *  - Compute next intervals for preview
 *  - Generate quiz questions from vocabulary
 *  - Format intervals for display
 */
class ReviewScheduler(
    private val weights: DoubleArray = Fsrs.DEFAULT_W,
    private val desiredRetention: Double = 0.90,
    private val maxIntervalDays: Int = 365,
) {

    // ─── Stage-to-Grade mapping ───

    /**
     * The implicit recall source for recalling at [stage] (1..4).
     *
     *   Stage 1 (audio only)          → SOUND  (strongest memory)
     *   Stage 2 (+ mental image)      → IMAGE
     *   Stage 3 (+ word & example)    → TEXT
     *   Stage 4 (full reveal, forgot) → STUDIED (weakest)
     */
    fun sourceForStage(stage: Int): RecallSource = when (stage) {
        1 -> RecallSource.SOUND
        2 -> RecallSource.IMAGE
        3 -> RecallSource.TEXT
        else -> RecallSource.STUDIED
    }

    /**
     * The FSRS rating (1..4) earned by recalling at [stage] (1..4).
     *
     *   Stage 1 → 4 Easy   (recalled from sound alone — strongest)
     *   Stage 2 → 3 Good   (needed the mental image)
     *   Stage 3 → 2 Hard   (needed to read the word)
     *   Stage 4 → 2 Hard   (vaguely familiar after full reveal)
     */
    fun gradeForStage(stage: Int): Int = when (stage) {
        1 -> 4
        2 -> 3
        3 -> 2
        else -> 2
    }

    // ─── Interval preview ───

    /** Preview the next interval for a word given an FSRS [grade] (1..4). */
    fun previewInterval(
        word: VocabWord,
        grade: Int,
        todayEpochDay: Long,
    ): Int {
        val elapsed = if (word.lastReviewedDay < 0) 0.0
        else (todayEpochDay - word.lastReviewedDay).toDouble()
        val phase = mapPhase(word.phase)
        return Fsrs.schedule(
            weights, grade.coerceIn(1, 4), phase,
            word.stability, word.difficulty, elapsed,
            desiredRetention, maxIntervalDays,
        ).intervalDays
    }

    /** Preview the interval at a given review stage. */
    fun previewStageInterval(word: VocabWord, stage: Int, todayEpochDay: Long): Int =
        previewInterval(word, gradeForStage(stage), todayEpochDay)

    /** Preview the interval when the word is forgotten (grade = 1). */
    fun previewFailInterval(word: VocabWord, todayEpochDay: Long): Int =
        previewInterval(word, 1, todayEpochDay)

    /**
     * Preview the interval for a recall source at a reached stage.
     * Used for Anki-style interval hints on rating buttons.
     */
    fun previewIntervalForSource(
        word: VocabWord,
        source: RecallSource,
        reachedStage: Int,
        todayEpochDay: Long,
    ): Int {
        var grade = source.grade
        if (source.grade >= 2) {
            grade = when (reachedStage) {
                1 -> (source.grade + 1).coerceAtMost(4)
                2 -> source.grade
                3 -> (source.grade - 1).coerceAtLeast(2)
                else -> (source.grade - 1).coerceAtLeast(1)
            }
        }
        return previewInterval(word, grade, todayEpochDay)
    }

    // ─── Interval formatting ───

    /** Format a day count as a short Arabic label. */
    fun formatInterval(days: Int): String = when {
        days <= 0 -> "الآن"
        days < 30 -> "$days ي"
        days < 365 -> "${days / 30} ش"
        else -> "${days / 365} س"
    }

    // ─── Quiz generation ───

    /**
     * Generate quiz questions from a vocabulary pool.
     *
     * @param pool the full active vocabulary
     * @param count how many questions to generate
     * @return list of quiz questions
     */
    fun generateQuiz(pool: List<VocabWord>, count: Int): List<QuizQuestion> {
        if (pool.isEmpty()) return emptyList()
        val chosen = pool.shuffled().take(count.coerceAtMost(pool.size))
        return chosen.mapIndexed { i, w ->
            val kind = QuizKind.values()[i % QuizKind.values().size]
            when (kind) {
                QuizKind.MEANING -> {
                    val wrong = pool.filter { it.id != w.id }.shuffled().take(3).map { it.arabic }
                    val opts = (wrong + w.arabic).shuffled()
                    QuizQuestion(
                        question = "ما معنى الكلمة؟",
                        context = w.english,
                        options = opts,
                        correctIndex = opts.indexOf(w.arabic),
                        kind = kind,
                    )
                }
                QuizKind.EXAMPLE -> {
                    val wrong = pool.filter { it.id != w.id }.shuffled().take(3).map { it.english }
                    val opts = (wrong + w.english).shuffled()
                    QuizQuestion(
                        question = "أكمل الجملة بالكلمة المناسبة",
                        context = w.exampleEn.replace(w.english, "_____", ignoreCase = true),
                        options = opts,
                        correctIndex = opts.indexOf(w.english),
                        kind = kind,
                    )
                }
                QuizKind.SPELLING -> {
                    val correct = w.english
                    val scrambled = mutableSetOf(correct)
                    var guard = 0
                    while (scrambled.size < 4 && guard < 40) {
                        scrambled.add(scramble(correct))
                        guard++
                    }
                    val opts = scrambled.toList().shuffled()
                    QuizQuestion(
                        question = "اختر الإملاء الصحيح لـ: ${w.arabic}",
                        context = w.phonetic,
                        options = opts,
                        correctIndex = opts.indexOf(correct),
                        kind = kind,
                    )
                }
            }
        }
    }

    // ─── Helpers ───

    private fun mapPhase(phase: FsrsPhase): Fsrs.Phase = when (phase) {
        FsrsPhase.NEW -> Fsrs.Phase.NEW
        FsrsPhase.LEARNING -> Fsrs.Phase.LEARNING
        FsrsPhase.REVIEW -> Fsrs.Phase.REVIEW
        FsrsPhase.RELEARNING -> Fsrs.Phase.RELEARNING
    }

    private fun scramble(s: String): String {
        if (s.length < 3) return s + "x"
        val chars = s.toCharArray()
        val i = (1 until s.length - 1).random()
        val j = (1 until s.length - 1).random()
        val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        return String(chars)
    }
}

// ─── Quiz models ───

enum class QuizKind { MEANING, EXAMPLE, SPELLING }

data class QuizQuestion(
    val question: String,
    val context: String,
    val options: List<String>,
    val correctIndex: Int,
    val kind: QuizKind,
)
