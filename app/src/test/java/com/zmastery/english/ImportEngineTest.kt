package com.zmastery.english

import com.zmastery.english.data.ImportEngine
import org.junit.Assert.*
import org.junit.Test

class ImportEngineTest {

    @Test
    fun `parse empty string fails`() {
        val result = ImportEngine.parse("")
        assertFalse(result.success)
        assertTrue(result.message.contains("فارغ"))
    }

    @Test
    fun `parse invalid JSON fails`() {
        val result = ImportEngine.parse("{invalid json}")
        assertFalse(result.success)
        assertTrue(result.message.contains("خطأ"))
    }

    @Test
    fun `parse valid course package succeeds`() {
        val json = """
        {
            "course_key": "l1_scratch",
            "course_name": "Test Course",
            "course_type": "vocabulary",
            "level": 1,
            "target": 10,
            "lessons": [
                {
                    "lesson_id": "1",
                    "lesson_title": "First Lesson",
                    "lesson_no": 1,
                    "words": [
                        {"word": "hello", "translation": "مرحبا", "phonetic": "həˈloʊ", "example": "Hello world", "example_ar": "مرحبا بالعالم"}
                    ]
                }
            ]
        }
        """.trimIndent()
        val result = ImportEngine.parse(json)
        assertTrue("Should succeed: ${result.message}", result.success)
        assertEquals(1, result.lessonCount)
        assertEquals(1, result.wordCount)
    }

    @Test
    fun `parse course with no lessons fails`() {
        val json = """
        {
            "course_key": "l1_scratch",
            "course_name": "Empty Course",
            "lessons": []
        }
        """.trimIndent()
        val result = ImportEngine.parse(json)
        assertFalse(result.success)
        assertTrue(result.message.contains("درس واحد على الأقل"))
    }

    @Test
    fun `parse lesson with no title fails`() {
        val json = """
        {
            "course_key": "l1_scratch",
            "course_name": "Test",
            "lessons": [
                {"lesson_id": "1", "lesson_title": "", "lesson_no": 1, "words": []}
            ]
        }
        """.trimIndent()
        val result = ImportEngine.parse(json)
        assertFalse(result.success)
        assertTrue(result.message.contains("عنوان"))
    }

    @Test
    fun `parse unknown course_key fails`() {
        val json = """
        {
            "course_key": "nonexistent_key",
            "course_name": "Test",
            "lessons": [
                {"lesson_id": "1", "lesson_title": "Lesson", "lesson_no": 1, "words": []}
            ]
        }
        """.trimIndent()
        val result = ImportEngine.parse(json)
        assertFalse(result.success)
        assertTrue(result.message.contains("غير معروف"))
    }

    // ─── Per-lesson import ───

    @Test
    fun `parseLesson with valid metadata succeeds`() {
        val json = """
        {
            "metadata": {
                "course_id": "l1_scratch",
                "course_name_ar": "كورس تجريبي",
                "level": 1,
                "lesson_no": 1,
                "title": "الدرس الأول"
            },
            "lesson_content": {
                "key_sentences": [{"en": "Hello", "ar": "مرحبا"}]
            },
            "global_vocabulary": [
                {"word": "hello", "meaning": "مرحبا", "example_en": "Hi", "example_ar": "أهلا"}
            ],
            "quiz": []
        }
        """.trimIndent()
        val result = ImportEngine.parseLesson(json)
        assertTrue("Should succeed: ${result.message}", result.success)
        assertNotNull(result.pkg)
    }

    @Test
    fun `parseLesson without course_id fails`() {
        val json = """
        {
            "metadata": {
                "title": "Lesson without course"
            },
            "lesson_content": {},
            "quiz": []
        }
        """.trimIndent()
        val result = ImportEngine.parseLesson(json)
        assertFalse(result.success)
        assertTrue(result.message.contains("course_id") || result.message.contains("course_name"))
    }

    @Test
    fun `looksLikeLesson detects metadata`() {
        assertTrue(ImportEngine.looksLikeLesson("""{"metadata": {"course_id": "x"}}"""))
        assertFalse(ImportEngine.looksLikeLesson("""{"course_key": "x"}"""))
        assertFalse(ImportEngine.looksLikeLesson("""[1, 2, 3]"""))
    }

    // ─── Multi-lesson import ───

    @Test
    fun `parseMultiLesson with JSON array`() {
        val json = """
        [
            {
                "metadata": {"course_id": "l1_scratch", "title": "Lesson 1", "lesson_no": 1},
                "lesson_content": {},
                "global_vocabulary": [{"word": "a", "meaning": "أ"}],
                "quiz": []
            },
            {
                "metadata": {"course_id": "l1_scratch", "title": "Lesson 2", "lesson_no": 2},
                "lesson_content": {},
                "global_vocabulary": [{"word": "b", "meaning": "ب"}],
                "quiz": []
            }
        ]
        """.trimIndent()
        val result = ImportEngine.parseMultiLesson(json)
        assertTrue("Should succeed: ${result.message}", result.success)
        assertEquals(2, result.packages.size)
    }

    @Test
    fun `parseMultiLesson with empty array fails`() {
        val result = ImportEngine.parseMultiLesson("[]")
        assertFalse(result.success)
    }

    @Test
    fun `courseKeyReference is not empty`() {
        val keys = ImportEngine.courseKeyReference()
        assertTrue("Should have at least one course key", keys.isNotEmpty())
    }
}
