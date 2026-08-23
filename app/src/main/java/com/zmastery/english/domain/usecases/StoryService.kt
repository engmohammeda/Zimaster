package com.zmastery.english.domain.usecases

/**
 * Story Service Use Case — manages daily story generation logic.
 *
 * Pure logic extracted from AppViewModel.
 */
class StoryService {

    // ─── Story eligibility ───

    /**
     * Check if a daily story should be generated today.
     *
     * @param lastStoryDay epoch day of the last generated story
     * @param todayEpochDay today's epoch day
     * @param hasAiKey whether an AI API key is configured
     * @param vocabSize number of words in the dictionary
     */
    fun shouldGenerateToday(
        lastStoryDay: Long,
        todayEpochDay: Long,
        hasAiKey: Boolean,
        vocabSize: Int,
    ): StoryEligibility {
        return when {
            !hasAiKey -> StoryEligibility(false, "يحتاج مفتاح Gemini")
            vocabSize < 10 -> StoryEligibility(false, "يحتاج 10 كلمات على الأقل (لديك $vocabSize)")
            lastStoryDay == todayEpochDay -> StoryEligibility(false, "قصة اليوم موجودة بالفعل")
            else -> StoryEligibility(true, "جاهز للتوليد")
        }
    }

    // ─── Seed word selection ───

    /**
     * Select words for the story seed.
     *
     * Strategy: pick recently reviewed words that are not yet mastered,
     * prioritizing words with lower stability (harder to remember).
     *
     * @param words the full vocabulary
     * @param maxCount maximum number of words to select
     * @return selected word IDs
     */
    fun selectSeedWords(
        words: List<SeedableWord>,
        maxCount: Int = 7,
    ): List<SeedableWord> {
        return words
            .filter { !it.mastered && it.stability > 0 }
            .sortedBy { it.stability }  // Hardest first
            .take(maxCount)
    }

    // ─── Retry logic ───

    /**
     * Calculate retry delay based on attempt number (exponential backoff).
     *
     * @param attempt current attempt (0-based)
     * @return delay in seconds
     */
    fun retryDelaySeconds(attempt: Int): Int {
        return when (attempt) {
            0 -> 0       // Immediate first try
            1 -> 15      // 15 seconds
            2 -> 60      // 1 minute
            else -> 300  // 5 minutes
        }
    }

    /**
     * Maximum number of retry attempts for story generation.
     */
    val maxRetries: Int = 3

    // ─── Story metadata ───

    /**
     * Generate a title for a daily story based on seed words.
     */
    fun generateTitle(seedWords: List<String>): String {
        if (seedWords.isEmpty()) return "قصة اليوم"
        val first = seedWords.first()
        return when {
            seedWords.size >= 3 -> "مغامرة $first وأصدقائه"
            seedWords.size == 2 -> "$first و ${seedWords[1]}"
            else -> "قصة $first"
        }
    }

    /**
     * Classify a story by its kind.
     */
    fun storyKindLabel(kind: String): String = when (kind) {
        "DAILY" -> "قصة يومية"
        "LESSON" -> "قصة درس"
        "READING" -> "قراءة"
        else -> "قصة"
    }
}

/** Minimal interface for words that can seed a story. */
interface SeedableWord {
    val id: Int
    val english: String
    val arabic: String
    val stability: Double
    val mastered: Boolean
}

data class StoryEligibility(
    val eligible: Boolean,
    val reason: String,
)
