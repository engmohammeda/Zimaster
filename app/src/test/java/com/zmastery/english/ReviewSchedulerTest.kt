package com.zmastery.english

import com.zmastery.english.domain.usecases.QuizKind
import com.zmastery.english.domain.usecases.ReviewScheduler
import com.zmastery.english.data.FsrsPhase
import com.zmastery.english.data.RecallSource
import com.zmastery.english.data.VocabWord
import org.junit.Assert.*
import org.junit.Test

class ReviewSchedulerTest {

    private val scheduler = ReviewScheduler()

    private fun testWord(id: Int = 1, stability: Double = 5.0, difficulty: Double = 5.0) = VocabWord(
        id = id, english = "word$id", arabic = "كلمة$id",
        exampleEn = "Example $id", exampleAr = "مثال $id",
        phonetic = "/wɜːrd/", mentalImage = "image", courseId = 1,
        stability = stability, difficulty = difficulty,
        phase = FsrsPhase.REVIEW, dueInDays = 3, intervalDays = 7,
        lastReviewedDay = 100L, repetitions = 5, mastered = false,
        listenCount = 10, totalReviews = 20, lapses = 2,
        lastRecall = "good", avgRecallStage = 3.0f, lastGrade = 3,
        pendingApproval = false, lessonId = 1,
    )

    // ─── Stage-to-Grade mapping ───

    @Test
    fun `stage 1 maps to Easy grade 4`() {
        assertEquals(4, scheduler.gradeForStage(1))
    }

    @Test
    fun `stage 2 maps to Good grade 3`() {
        assertEquals(3, scheduler.gradeForStage(2))
    }

    @Test
    fun `stage 3 maps to Hard grade 2`() {
        assertEquals(2, scheduler.gradeForStage(3))
    }

    @Test
    fun `stage 4 maps to Hard grade 2`() {
        assertEquals(2, scheduler.gradeForStage(4))
    }

    // ─── Stage-to-Source mapping ───

    @Test
    fun `stage 1 maps to SOUND`() {
        assertEquals(RecallSource.SOUND, scheduler.sourceForStage(1))
    }

    @Test
    fun `stage 2 maps to IMAGE`() {
        assertEquals(RecallSource.IMAGE, scheduler.sourceForStage(2))
    }

    @Test
    fun `stage 3 maps to TEXT`() {
        assertEquals(RecallSource.TEXT, scheduler.sourceForStage(3))
    }

    @Test
    fun `stage 4 maps to STUDIED`() {
        assertEquals(RecallSource.STUDIED, scheduler.sourceForStage(4))
    }

    // ─── Interval preview ───

    @Test
    fun `Easy grade gives longest interval`() {
        val word = testWord()
        val easy = scheduler.previewInterval(word, 4, 110L)
        val good = scheduler.previewInterval(word, 3, 110L)
        val hard = scheduler.previewInterval(word, 2, 110L)
        val again = scheduler.previewInterval(word, 1, 110L)
        assertTrue("Easy > Good", easy >= good)
        assertTrue("Good >= Hard", good >= hard)
        assertTrue("Hard >= Again", hard >= again)
    }

    @Test
    fun `fail interval is shortest`() {
        val word = testWord()
        val fail = scheduler.previewFailInterval(word, 110L)
        val pass = scheduler.previewStageInterval(word, 2, 110L)
        assertTrue("Fail < Pass", fail <= pass)
    }

    // ─── Format interval ───

    @Test
    fun `format zero days`() {
        assertEquals("الآن", scheduler.formatInterval(0))
    }

    @Test
    fun `format days`() {
        assertEquals("5 ي", scheduler.formatInterval(5))
    }

    @Test
    fun `format months`() {
        assertEquals("2 ش", scheduler.formatInterval(60))
    }

    @Test
    fun `format years`() {
        assertEquals("1 س", scheduler.formatInterval(365))
    }

    // ─── Quiz generation ───

    @Test
    fun `generate quiz from empty pool returns empty`() {
        val quiz = scheduler.generateQuiz(emptyList(), 5)
        assertTrue(quiz.isEmpty())
    }

    @Test
    fun `generate quiz from pool returns correct count`() {
        val pool = (1..10).map { testWord(it) }
        val quiz = scheduler.generateQuiz(pool, 5)
        assertEquals(5, quiz.size)
    }

    @Test
    fun `quiz questions have 4 options`() {
        val pool = (1..10).map { testWord(it) }
        val quiz = scheduler.generateQuiz(pool, 3)
        quiz.forEach { q ->
            assertEquals("Each question should have 4 options", 4, q.options.size)
        }
    }

    @Test
    fun `quiz correct index is valid`() {
        val pool = (1..10).map { testWord(it) }
        val quiz = scheduler.generateQuiz(pool, 3)
        quiz.forEach { q ->
            assertTrue("Correct index in range", q.correctIndex in 0..3)
        }
    }

    @Test
    fun `quiz count capped at pool size`() {
        val pool = (1..3).map { testWord(it) }
        val quiz = scheduler.generateQuiz(pool, 10)
        assertEquals(3, quiz.size)
    }
}
