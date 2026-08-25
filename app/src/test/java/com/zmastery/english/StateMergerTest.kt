package com.zmastery.english

import com.zmastery.english.data.AppState
import com.zmastery.english.data.CourseDto
import com.zmastery.english.data.LessonDto
import com.zmastery.english.data.ProfileDto
import com.zmastery.english.data.StateMerger
import com.zmastery.english.data.WordDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات [StateMerger] — حجر الأساس لدواء فقدان البيانات:
 * أي دمج يجب أن يستحيل معه فقدان عنصر محلي موجود مسبقاً.
 */
class StateMergerTest {

    // ---- بُناة DTO مختصرة ----

    private fun course(id: Int, key: String = "c$id") = CourseDto(
        id = id, levelId = 1, name = "كورس $id", type = "VOCABULARY",
        target = 10, accent = 0xFF5B62D6, style = "VOCAB_CARDS", key = key, jsonId = key,
    )

    private fun lesson(
        id: Int, courseId: Int, no: Int, completed: Boolean = false, title: String = "درس $no",
    ) = LessonDto(
        id = id, courseId = courseId, no = no, title = title,
        summaryAr = "", readingEn = "", readingAr = "",
        keyPoints = emptyList(), isCompleted = completed,
        dialogues = emptyList(), newWordIds = emptyList(),
        keySentences = emptyList(), notes = emptyList(), quiz = emptyList(),
        reviewCount = 0, lastMastery = 0, dueInDays = 0, intervalDays = 0,
    )

    private fun word(id: Int, english: String, reps: Int = 0) = WordDto(
        id = id, english = english, arabic = "معنى", exampleEn = "", exampleAr = "",
        phonetic = "", mentalImage = "", courseId = 1,
        stability = 0.0, difficulty = 0.0, phase = "NEW", dueInDays = 0,
        intervalDays = 0, lastReviewedDay = 0, repetitions = reps, mastered = false,
        listenCount = 0, totalReviews = 0, lapses = 0, lastRecall = "NONE",
        avgRecallStage = 0f, lastGrade = 0, pendingApproval = false, lessonId = 0,
    )

    // ---- 1) اللقطة السحابية القديمة/الفارغة لا تمسح المحلي أبداً ----

    @Test
    fun `stale empty cloud never wipes local lessons`() {
        val local = AppState(
            courses = listOf(course(1)),
            lessons = (1..24).map { lesson(it, 1, it) },
            vocab = listOf(word(1, "energy", reps = 3)),
            profile = ProfileDto(learnerName = "محمد"),
        )
        val cloud = AppState() // لقطة قديمة فارغة — سيناريو الخلل الأصلي بالضبط

        val merged = StateMerger.merge(local, cloud)

        assertEquals(24, merged.lessons.size)
        assertEquals(1, merged.vocab.size)
        assertEquals("محمد", merged.profile.learnerName)
    }

    // ---- 2) دروس سحابية جديدة تُضاف، والمحلية تبقى ----

    @Test
    fun `cloud-only lessons are added and local ones survive`() {
        val local = AppState(lessons = listOf(lesson(1, 1, 1), lesson(2, 1, 2)))
        val cloud = AppState(
            courses = listOf(course(1)),
            lessons = listOf(lesson(1, 1, 1), lesson(50, 1, 20), lesson(51, 1, 21)),
        )

        val merged = StateMerger.merge(local, cloud)

        assertEquals(listOf(1, 2, 50, 51), merged.lessons.map { it.id })
    }

    // ---- 3) إكمال أُنجز على جهاز آخر يُتبنّى مع بقاء المحتوى المحلي ----

    @Test
    fun `cloud completion is adopted for shared lesson`() {
        val local = AppState(lessons = listOf(lesson(5, 1, 5, completed = false)))
        val cloud = AppState(lessons = listOf(lesson(5, 1, 5, completed = true)))

        val merged = StateMerger.merge(local, cloud)

        assertEquals(1, merged.lessons.size)
        assertTrue(merged.lessons.first().isCompleted)
    }

    // ---- 4) جهاز جديد فارغ يتبنى محتوى السحابة كاملاً ----

    @Test
    fun `empty local adopts cloud content`() {
        val cloud = AppState(
            courses = listOf(course(1)),
            lessons = listOf(lesson(10, 1, 1), lesson(11, 1, 2)),
            vocab = listOf(word(10, "energy")),
            profile = ProfileDto(learnerName = "محمد", streak = 7, xp = 320),
        )

        val merged = StateMerger.merge(AppState(), cloud)

        assertEquals(2, merged.lessons.size)
        assertEquals(1, merged.vocab.size)
        assertEquals("محمد", merged.profile.learnerName)
        assertEquals(7, merged.profile.streak)
        assertEquals(320, merged.profile.xp)
    }

    // ---- 5) الكلمة المحلية تفوز عند التعارض (FSRS أحدث على الجهاز) ----

    @Test
    fun `local vocab wins on english conflict`() {
        val local = AppState(vocab = listOf(word(1, "Energy", reps = 5)))
        val cloud = AppState(vocab = listOf(word(90, "energy", reps = 1)))

        val merged = StateMerger.merge(local, cloud)

        assertEquals(1, merged.vocab.size)
        assertEquals(5, merged.vocab.first().repetitions)
    }

    // ---- 6) الملف الشخصي: الأعلى في السلسلة/XP بلا رجوع ----

    @Test
    fun `profile takes max streak and xp`() {
        val local = AppState(profile = ProfileDto(learnerName = "محمد", streak = 3, xp = 100))
        val cloud = AppState(profile = ProfileDto(learnerName = "سحابة", streak = 9, xp = 40))

        val merged = StateMerger.merge(local, cloud)

        assertEquals("محمد", merged.profile.learnerName) // الاسم المحلي يفوز
        assertEquals(9, merged.profile.streak)
        assertEquals(100, merged.profile.xp)
    }

    // ---- 7) نفس الدرس بهوية (courseId+no) مختلف id يُعامل كواحد ----

    @Test
    fun `same lesson by course and number merges completion not duplicate`() {
        val local = AppState(lessons = listOf(lesson(101, 1, 3, completed = false)))
        val cloud = AppState(lessons = listOf(lesson(770, 1, 3, completed = true)))

        val merged = StateMerger.merge(local, cloud)

        assertEquals(1, merged.lessons.size)
        assertTrue(merged.lessons.first().isCompleted)
    }
}
