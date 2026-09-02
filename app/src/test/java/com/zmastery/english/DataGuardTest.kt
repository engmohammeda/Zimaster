package com.zmastery.english

import com.zmastery.english.data.AppState
import com.zmastery.english.data.CourseDto
import com.zmastery.english.data.DataGuard
import com.zmastery.english.data.LessonDto
import com.zmastery.english.data.ProfileDto
import com.zmastery.english.data.WordDto
import org.junit.Assert.*
import org.junit.Test

class DataGuardTest {

    private fun sampleState(
        lessons: Int = 0,
        vocab: Int = 0,
        courses: Int = 0,
        name: String = "",
    ) = AppState(
        courses = (1..courses).map { CourseDto(it, 1, "C$it", "vocab", 10, 0L, "", "", "") },
        lessons = (1..lessons).map {
            LessonDto(it, 1, it, "L$it", "", "", "", emptyList(), false,
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                0, 0, 0, 0)
        },
        vocab = (1..vocab).map {
            WordDto(it, "w$it", "ك$it", "", "", "", "", 1,
                0.0, 0.0, "", 0, 0, 0L, 0, false,
                0, 0, 0, "", 0f, 0, false, 0)
        },
        profile = ProfileDto(learnerName = name),
    )

    @Test
    fun `health of empty state`() {
        val health = DataGuard.healthOf(AppState())
        assertTrue(health.isEmpty)
        assertEquals(0, health.lessonCount)
        assertEquals(0, health.vocabCount)
        assertEquals(0, health.courseCount)
        assertFalse(health.hasProfile)
    }

    @Test
    fun `health of populated state`() {
        val state = sampleState(lessons = 10, vocab = 50, courses = 2, name = "أحمد")
        val health = DataGuard.healthOf(state)
        assertFalse(health.isEmpty)
        assertEquals(10, health.lessonCount)
        assertEquals(50, health.vocabCount)
        assertEquals(2, health.courseCount)
        assertTrue(health.hasProfile)
    }

    @Test
    fun `health counts are accurate`() {
        val state = sampleState(lessons = 3, vocab = 7, courses = 1)
        val health = DataGuard.healthOf(state)
        assertEquals(3, health.lessonCount)
        assertEquals(7, health.vocabCount)
        assertEquals(1, health.courseCount)
    }

    @Test
    fun `state without name has no profile`() {
        val state = sampleState(lessons = 1, name = "")
        assertFalse(DataGuard.healthOf(state).hasProfile)
    }

    @Test
    fun `state with name has profile`() {
        val state = sampleState(name = "Fatima")
        assertTrue(DataGuard.healthOf(state).hasProfile)
    }

    @Test
    fun `encode and decode round-trip preserves state`() {
        val original = sampleState(lessons = 5, vocab = 20, courses = 1, name = "Test")
        val encoded = com.zmastery.english.data.Persistence.encode(original)
        val decoded = com.zmastery.english.data.Persistence.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original.lessons.size, decoded!!.lessons.size)
        assertEquals(original.vocab.size, decoded.vocab.size)
        assertEquals(original.profile.learnerName, decoded.profile.learnerName)
    }

    @Test
    fun `decode invalid JSON returns null`() {
        val decoded = com.zmastery.english.data.Persistence.decode("not valid json")
        assertNull(decoded)
    }

    @Test
    fun `decode empty string returns null`() {
        val decoded = com.zmastery.english.data.Persistence.decode("")
        assertNull(decoded)
    }

    @Test
    fun `decode partial JSON returns null`() {
        val decoded = com.zmastery.english.data.Persistence.decode("""{"courses": [""")
        assertNull(decoded)
    }

    @Test
    fun `encode produces valid JSON`() {
        val state = sampleState(lessons = 1, name = "Test")
        val encoded = com.zmastery.english.data.Persistence.encode(state)
        assertTrue(encoded.startsWith("{"))
        assertTrue(encoded.endsWith("}"))
        assertTrue(encoded.contains("\"courses\""))
    }

    @Test
    fun `fetched models round-trip so the catalogue survives restart`() {
        val models = listOf(
            com.zmastery.english.data.AiModelDto(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                kind = "TEXT",
                fetched = true,
            ),
            com.zmastery.english.data.AiModelDto(
                id = "gemini-2.5-flash-preview-tts",
                displayName = "Flash TTS",
                kind = "TTS",
                fetched = true,
            ),
        )
        val original = sampleState(name = "Test").copy(
            aiModels = models,
            profile = ProfileDto(learnerName = "Test", showFreeModelsOnly = true),
        )
        val decoded = com.zmastery.english.data.Persistence.decode(
            com.zmastery.english.data.Persistence.encode(original),
        )
        assertNotNull(decoded)
        assertEquals(2, decoded!!.aiModels.size)
        assertTrue(decoded.aiModels.all { it.fetched })
        assertEquals("TTS", decoded.aiModels[1].kind)
        assertTrue(decoded.profile.showFreeModelsOnly)
    }
}
