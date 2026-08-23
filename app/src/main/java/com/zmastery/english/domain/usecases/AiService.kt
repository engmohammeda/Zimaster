package com.zmastery.english.domain.usecases

import com.zmastery.english.data.AiProvider

/**
 * AI Service Use Case — manages API key validation, model listing, and provider detection.
 *
 * Pure logic extracted from AppViewModel — no Compose state, no Android dependencies.
 * The ViewModel holds the observable state; this class provides the business rules.
 */
class AiService {

    // ─── Key validation ───

    /**
     * Validate an API key format before sending it to the provider.
     *
     * @return a human-readable validation result
     */
    fun validateKeyFormat(rawKey: String, provider: AiProvider): KeyValidation {
        val key = rawKey.trim()
        if (key.isBlank()) return KeyValidation(false, "المفتاح فارغ")

        return when (provider) {
            AiProvider.GEMINI -> {
                if (key.startsWith("AIzaSy") && key.length == 39) {
                    KeyValidation(true, "مفتاح Gemini صالح ✓")
                } else {
                    KeyValidation(false, "مفتاح Gemini يجب أن يبدأ بـ AIzaSy وطوله 39 حرف (الطول الحالي: ${key.length})")
                }
            }
            AiProvider.OPENAI -> {
                if (key.startsWith("sk-") && key.length >= 20) {
                    KeyValidation(true, "مفتاح OpenAI صالح ✓")
                } else {
                    KeyValidation(false, "مفتاح OpenAI يجب أن يبدأ بـ sk-")
                }
            }
            AiProvider.OPENAI_COMPATIBLE -> {
                if (key.isNotBlank()) {
                    KeyValidation(true, "مفتاح متوافق ✓")
                } else {
                    KeyValidation(false, "المفتاح فارغ")
                }
            }
            AiProvider.XAI -> {
                if (key.startsWith("xai-")) {
                    KeyValidation(true, "مفتاح xAI صالح ✓")
                } else {
                    KeyValidation(false, "مفتاح xAI يجب أن يبدأ بـ xai-")
                }
            }
        }
    }

    // ─── Provider detection ───

    /**
     * Auto-detect the provider from the key format.
     */
    fun detectProvider(rawKey: String): AiProvider {
        val key = rawKey.trim()
        return when {
            key.startsWith("AIzaSy") -> AiProvider.GEMINI
            key.startsWith("sk-") -> AiProvider.OPENAI
            key.startsWith("xai-") -> AiProvider.XAI
            else -> AiProvider.OPENAI_COMPATIBLE
        }
    }

    // ─── Model management ───

    /**
     * Classify a model ID into a kind for grouping in the UI.
     */
    fun classifyModel(modelId: String): ModelKind {
        val id = modelId.lowercase()
        return when {
            id.contains("flash") -> ModelKind.FAST
            id.contains("pro") -> ModelKind.POWERFUL
            id.contains("embedding") -> ModelKind.EMBEDDING
            id.contains("tts") || id.contains("voice") -> ModelKind.TTS
            id.contains("image") || id.contains("vision") -> ModelKind.VISION
            else -> ModelKind.GENERAL
        }
    }

    /**
     * Check if a model is likely free-tier based on its ID.
     */
    fun isLikelyFree(modelId: String): Boolean {
        val id = modelId.lowercase()
        return id.contains("flash") && !id.contains("pro") ||
            id.contains("lite") ||
            id.contains("free")
    }

    // ─── Rate limiting ───

    /**
     * Calculate remaining quota based on model and usage.
     *
     * @param modelId the Gemini model ID
     * @param requestsToday number of requests made today
     * @return quota info
     */
    fun estimateQuota(modelId: String, requestsToday: Int): QuotaInfo {
        val dailyLimit = when {
            modelId.contains("flash") -> 1500     // Gemini Flash free tier
            modelId.contains("pro") -> 50          // Gemini Pro free tier (reduced)
            else -> 100                             // Conservative default
        }
        val remaining = (dailyLimit - requestsToday).coerceAtLeast(0)
        val pct = if (dailyLimit > 0) (remaining.toFloat() / dailyLimit) else 0f
        return QuotaInfo(
            dailyLimit = dailyLimit,
            used = requestsToday,
            remaining = remaining,
            usagePercent = pct,
            isNearLimit = pct < 0.15f,
        )
    }

    // ─── Cost estimation ───

    /**
     * Estimate the cost of a text completion.
     *
     * @param inputTokens approximate input token count
     * @param outputTokens approximate output token count
     * @param modelId the model used
     * @return estimated cost in USD
     */
    fun estimateCost(inputTokens: Int, outputTokens: Int, modelId: String): Double {
        // Approximate pricing per 1M tokens (as of 2026)
        val (inputPrice, outputPrice) = when {
            modelId.contains("flash") -> 0.075 to 0.30       // Flash
            modelId.contains("pro") -> 1.25 to 5.0           // Pro
            else -> 0.50 to 1.50                              // Default
        }
        return (inputTokens * inputPrice + outputTokens * outputPrice) / 1_000_000.0
    }
}

// ─── Result types ───

data class KeyValidation(
    val valid: Boolean,
    val message: String,
)

enum class ModelKind(val label: String, val emoji: String) {
    FAST("سريع", "⚡"),
    POWERFUL("قوي", "🧠"),
    GENERAL("عام", "🤖"),
    VISION("رؤية", "👁"),
    TTS("صوت", "🔊"),
    EMBEDDING("تمثيل", "📐"),
}

data class QuotaInfo(
    val dailyLimit: Int,
    val used: Int,
    val remaining: Int,
    val usagePercent: Float,
    val isNearLimit: Boolean,
)
