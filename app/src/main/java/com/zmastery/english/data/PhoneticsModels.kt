package com.zmastery.english.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ==========================================================================
// Phonetics lesson schema — matches the imported JSON structure exactly.
// ==========================================================================

@Serializable
data class PhoneticsLesson(
    val metadata: PhMetadata = PhMetadata(),
    @SerialName("lesson_content") val content: PhContent = PhContent(),
    @SerialName("global_vocabulary") val vocabulary: List<PhVocab> = emptyList(),
    @SerialName("lesson_notes") val notes: List<String> = emptyList(),
    val quiz: List<PhQuiz> = emptyList(),
)

@Serializable
data class PhMetadata(
    @SerialName("course_id") val courseId: String = "phonetics",
    @SerialName("course_name_ar") val courseNameAr: String = "الصوتيات",
    val level: Int = 1,
    @SerialName("lesson_no") val lessonNo: Int = 1,
    val title: String = "",
)

@Serializable
data class PhContent(
    @SerialName("focus_sounds") val focusSounds: List<PhSound> = emptyList(),
    @SerialName("minimal_pairs") val minimalPairs: List<PhPair> = emptyList(),
    @SerialName("practice_scripts") val practiceScripts: List<String> = emptyList(),
)

@Serializable
data class PhSound(val symbol: String = "", val description: String = "")

@Serializable
data class PhPair(val word1: String = "", val word2: String = "")

@Serializable
data class PhVocab(
    val word: String = "",
    val meaning: String = "",
    @SerialName("example_en") val exampleEn: String = "",
    @SerialName("example_ar") val exampleAr: String = "",
)

@Serializable
data class PhQuiz(
    val type: String = "audio_quiz", // audio_quiz | true_false
    val question: String = "",
    @SerialName("word_to_speak") val wordToSpeak: String? = null,
    val options: List<String>? = null,
    val answer: String = "",
    @SerialName("explanation_ar") val explanationAr: String = "",
)

object PhoneticsParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(raw: String): PhoneticsLesson? = try {
        json.decodeFromString<PhoneticsLesson>(raw.trim())
    } catch (e: Exception) {
        null
    }
}
