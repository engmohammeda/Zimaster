package com.zmastery.english.data

import org.json.JSONObject

/**
 * Generates a full vocabulary entry from just an English word.
 *
 * Provider-agnostic: it delegates to [AiClient], so it works with Gemini,
 * Groq, OpenRouter, Cerebras, DeepSeek, a local Ollama — anything the user has
 * configured. The name is kept for source compatibility with existing callers.
 */
object GeminiWordService {

    data class GeneratedWord(
        val english: String,
        val arabic: String,
        val exampleEn: String,
        val exampleAr: String,
        val phonetic: String,
        val mentalImage: String,
    )

    sealed class Result {
        data class Success(val word: GeneratedWord) : Result()
        data class Error(val message: String) : Result()
    }

    private const val SYSTEM =
        "You are a precise English-to-Arabic vocabulary assistant for a language-learning app. " +
            "You always reply with a single raw JSON object and nothing else — no prose, no markdown fences."

    /**
     * @param key   the active credential (any provider)
     * @param model model id to use ("" → the provider default)
     */
    suspend fun generate(
        word: String,
        userContext: String,
        key: ApiKeyEntry?,
        model: String = "",
    ): Result {
        if (key == null || key.rawKey.isBlank()) {
            return Result.Error("أضف مفتاح API من إعدادات الذكاء الاصطناعي أولاً")
        }
        val clean = word.trim()
        if (clean.isEmpty()) return Result.Error("اكتب الكلمة الإنجليزية")

        val prompt = buildString {
            append("For the English word \"$clean\", return ONLY a compact JSON object ")
            append("with these exact keys: ")
            append("\"arabic\" (accurate Arabic translation), ")
            append("\"example_en\" (a short natural English example sentence using the word), ")
            append("\"example_ar\" (Arabic translation of that example), ")
            append("\"phonetic\" (IPA pronunciation with slashes, e.g. /wɜːd/), ")
            append("\"mental_image\" (a very short vivid Arabic phrase describing a memorable mental image). ")
            if (userContext.isNotBlank()) {
                append("Prefer this custom context/sentence from the user if relevant: \"${userContext.trim()}\". ")
            }
            append("Output raw JSON only.")
        }

        val reply = AiClient.complete(
            key = key, model = model, system = SYSTEM, user = prompt,
            json = true, temperature = 0.7,
        )
        if (!reply.ok) return Result.Error("فشل التوليد: ${reply.error}")

        val json = runCatching { JSONObject(AiClient.stripFences(reply.text)) }.getOrNull()
            ?: return Result.Error("رد غير صالح من النموذج — جرّب نموذجاً آخر")

        val arabic = json.optString("arabic").ifBlank { "—" }
        return Result.Success(
            GeneratedWord(
                english = clean,
                arabic = arabic,
                exampleEn = json.optString("example_en"),
                exampleAr = json.optString("example_ar"),
                phonetic = json.optString("phonetic"),
                mentalImage = json.optString("mental_image"),
            )
        )
    }
}
