package com.zmastery.english

import com.zmastery.english.domain.usecases.SeedableWord
import com.zmastery.english.domain.usecases.StoryService
import org.junit.Assert.*
import org.junit.Test

class StoryServiceTest {

    private val service = StoryService()

    private data class TestWord(
        override val id: Int,
        override val english: String,
        override val arabic: String,
        override val stability: Double,
        override val mastered: Boolean,
    ) : SeedableWord

    private val sampleWords = listOf(
        TestWord(1, "hello", "مرحبا", 2.0, false),
        TestWord(2, "world", "عالم", 8.0, false),
        TestWord(3, "book", "كتاب", 0.5, false),
        TestWord(4, "master", "متقن", 15.0, true),
        TestWord(5, "learn", "تعلم", 1.0, false),
        TestWord(6, "study", "درس", 3.0, false),
    )

    // ─── Story eligibility ───

    @Test
    fun `eligible when has key and enough vocab`() {
        val result = service.shouldGenerateToday(99L, 100L, true, 50)
        assertTrue(result.eligible)
    }

    @Test
    fun `not eligible without AI key`() {
        val result = service.shouldGenerateToday(99L, 100L, false, 50)
        assertFalse(result.eligible)
        assertTrue(result.reason.contains("مفتاح"))
    }

    @Test
    fun `not eligible with too few words`() {
        val result = service.shouldGenerateToday(99L, 100L, true, 5)
        assertFalse(result.eligible)
        assertTrue(result.reason.contains("10"))
    }

    @Test
    fun `not eligible when story already generated today`() {
        val result = service.shouldGenerateToday(100L, 100L, true, 50)
        assertFalse(result.eligible)
        assertTrue(result.reason.contains("موجودة"))
    }

    // ─── Seed word selection ───

    @Test
    fun `select hardest non-mastered words`() {
        val seeds = service.selectSeedWords(sampleWords, maxCount = 3)
        assertEquals(3, seeds.size)
        // Hardest first (lowest stability, not mastered)
        assertEquals("كتاب", seeds[0].arabic)   // stability 0.5
        assertEquals("تعلم", seeds[1].arabic)   // stability 1.0
        assertEquals("مرحبا", seeds[2].arabic)  // stability 2.0
    }

    @Test
    fun `exclude mastered words`() {
        val seeds = service.selectSeedWords(sampleWords, maxCount = 10)
        assertFalse(seeds.any { it.mastered })
    }

    @Test
    fun `limit to maxCount`() {
        val seeds = service.selectSeedWords(sampleWords, maxCount = 2)
        assertEquals(2, seeds.size)
    }

    @Test
    fun `empty vocab returns empty seeds`() {
        val seeds = service.selectSeedWords(emptyList(), maxCount = 5)
        assertTrue(seeds.isEmpty())
    }

    // ─── Retry logic ───

    @Test
    fun `first attempt is immediate`() {
        assertEquals(0, service.retryDelaySeconds(0))
    }

    @Test
    fun `second attempt waits 15 seconds`() {
        assertEquals(15, service.retryDelaySeconds(1))
    }

    @Test
    fun `third attempt waits 60 seconds`() {
        assertEquals(60, service.retryDelaySeconds(2))
    }

    @Test
    fun `fourth attempt waits 5 minutes`() {
        assertEquals(300, service.retryDelaySeconds(3))
    }

    @Test
    fun `max retries is 3`() {
        assertEquals(3, service.maxRetries)
    }

    // ─── Title generation ───

    @Test
    fun `title with multiple words`() {
        val title = service.generateTitle(listOf("hello", "world", "book"))
        assertTrue(title.contains("hello"))
    }

    @Test
    fun `title with two words`() {
        val title = service.generateTitle(listOf("hello", "world"))
        assertTrue(title.contains("hello"))
        assertTrue(title.contains("world"))
    }

    @Test
    fun `title with single word`() {
        val title = service.generateTitle(listOf("hello"))
        assertTrue(title.contains("hello"))
    }

    @Test
    fun `title with empty words`() {
        val title = service.generateTitle(emptyList())
        assertEquals("قصة اليوم", title)
    }

    // ─── Story kind labels ───

    @Test
    fun `daily kind label`() {
        assertEquals("قصة يومية", service.storyKindLabel("DAILY"))
    }

    @Test
    fun `lesson kind label`() {
        assertEquals("قصة درس", service.storyKindLabel("LESSON"))
    }

    @Test
    fun `unknown kind label`() {
        assertEquals("قصة", service.storyKindLabel("UNKNOWN"))
    }
}
