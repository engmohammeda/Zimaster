package com.zmastery.english.data

/**
 * Multi-provider AI layer.
 *
 * The app speaks TWO wire protocols and nothing else:
 *
 *  1. **OpenAI Chat Completions** (`POST {base}/chat/completions`)
 *     The de-facto industry standard. Google, Groq, OpenRouter, DeepSeek,
 *     Mistral, Together, xAI, Cerebras, Ollama and dozens of free providers all
 *     expose it. Adding a new provider is therefore just a base URL — no code.
 *
 *  2. **Google Generative Language** (`POST /v1beta/models/{m}:generateContent`)
 *     Kept because it is the only way to reach Gemini TTS (audio modality),
 *     `models.list`, and some preview models that never appear on the OpenAI
 *     compatibility surface.
 *
 * Gemini keys work on BOTH: Google ships an OpenAI-compatible endpoint at
 * `https://generativelanguage.googleapis.com/v1beta/openai`. We default Gemini
 * keys to the native protocol (richer), and let any other provider ride the
 * OpenAI protocol.
 */

/** The wire protocol a provider speaks. */
enum class AiProtocol { OPENAI, GEMINI }

/**
 * A provider preset. [baseUrl] must NOT end with a slash.
 * For OpenAI-protocol providers the app calls `$baseUrl/chat/completions`
 * and `$baseUrl/models`.
 */
enum class AiProvider(
    val label: String,
    val protocol: AiProtocol,
    val baseUrl: String,
    /** Where the user gets a key. */
    val keyUrl: String,
    val hint: String,
    /** A safe, widely-available default model id. */
    val defaultModel: String,
    /** True when the provider has a genuinely free tier. */
    val freeTier: Boolean,
    /** Typical key prefix, used for auto-detection. */
    val keyPrefix: String = "",
) {
    GEMINI(
        "Google Gemini", AiProtocol.GEMINI,
        "https://generativelanguage.googleapis.com",
        "https://aistudio.google.com/apikey",
        "حصة مجانية سخية · نصوص وصوت وصور",
        "gemini-2.0-flash", true, "AIza",
    ),
    GEMINI_OPENAI(
        "Gemini (بروتوكول OpenAI)", AiProtocol.OPENAI,
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "https://aistudio.google.com/apikey",
        "نفس مفتاح Gemini عبر واجهة OpenAI",
        "gemini-2.0-flash", true, "AIza",
    ),
    OPENROUTER(
        "OpenRouter", AiProtocol.OPENAI,
        "https://openrouter.ai/api/v1",
        "https://openrouter.ai/keys",
        "بوابة لمئات النماذج · نماذج مجانية كثيرة",
        "meta-llama/llama-3.3-70b-instruct:free", true, "sk-or-",
    ),
    GROQ(
        "Groq", AiProtocol.OPENAI,
        "https://api.groq.com/openai/v1",
        "https://console.groq.com/keys",
        "الأسرع على الإطلاق · حصة مجانية",
        "llama-3.3-70b-versatile", true, "gsk_",
    ),
    CEREBRAS(
        "Cerebras", AiProtocol.OPENAI,
        "https://api.cerebras.ai/v1",
        "https://cloud.cerebras.ai",
        "سرعة فائقة · حصة مجانية يومية",
        "llama-3.3-70b", true, "csk-",
    ),
    MISTRAL(
        "Mistral", AiProtocol.OPENAI,
        "https://api.mistral.ai/v1",
        "https://console.mistral.ai/api-keys",
        "نماذج أوروبية · طبقة مجانية",
        "mistral-small-latest", true,
    ),
    DEEPSEEK(
        "DeepSeek", AiProtocol.OPENAI,
        "https://api.deepseek.com/v1",
        "https://platform.deepseek.com/api_keys",
        "رخيص جداً وقوي في المنطق",
        "deepseek-chat", false,
    ),
    TOGETHER(
        "Together AI", AiProtocol.OPENAI,
        "https://api.together.xyz/v1",
        "https://api.together.ai/settings/api-keys",
        "نماذج مفتوحة المصدر متنوعة",
        "meta-llama/Llama-3.3-70B-Instruct-Turbo", false,
    ),
    XAI(
        "xAI (Grok)", AiProtocol.OPENAI,
        "https://api.x.ai/v1",
        "https://console.x.ai",
        "نماذج Grok",
        "grok-2-latest", false, "xai-",
    ),
    OPENAI(
        "OpenAI", AiProtocol.OPENAI,
        "https://api.openai.com/v1",
        "https://platform.openai.com/api-keys",
        "GPT الرسمي · مدفوع",
        "gpt-4o-mini", false, "sk-",
    ),
    CUSTOM(
        "مزوّد مخصص", AiProtocol.OPENAI,
        "",
        "",
        "أي خدمة متوافقة مع OpenAI — أدخل الرابط",
        "", false,
    );

    companion object {
        fun from(name: String): AiProvider =
            runCatching { valueOf(name) }.getOrDefault(GEMINI)

        /** Best-effort provider guess from the shape of a raw key. */
        fun detect(rawKey: String): AiProvider {
            val k = rawKey.trim()
            return when {
                k.startsWith("AIza") -> GEMINI
                k.startsWith("sk-or-") -> OPENROUTER
                k.startsWith("gsk_") -> GROQ
                k.startsWith("csk-") -> CEREBRAS
                k.startsWith("xai-") -> XAI
                k.startsWith("sk-") -> OPENAI
                else -> GEMINI
            }
        }
    }
}

/**
 * A stored credential. The RAW key is kept (encrypted-at-rest is out of scope
 * for a local-only app, but it never leaves the device except to its provider),
 * because a masked key cannot authenticate anything — the original bug.
 */
data class ApiKeyEntry(
    val id: String,
    val label: String,
    /** Provider enum name. */
    val provider: String,
    /** The real secret. Never shown in full in the UI. */
    val rawKey: String = "",
    val active: Boolean = false,
    /** Overrides [AiProvider.baseUrl] when the provider is CUSTOM. */
    val baseUrl: String = "",
    /** Last verification result: "" = untested, "ok" = verified, else error. */
    val status: String = "",
) {
    val providerEnum: AiProvider get() = AiProvider.from(provider)

    /** Effective endpoint for this credential. */
    val effectiveBase: String
        get() = baseUrl.ifBlank { providerEnum.baseUrl }.trimEnd('/')

    val protocol: AiProtocol get() = providerEnum.protocol

    /** Safe display form — never reveals the middle of the secret. */
    val maskedKey: String
        get() = when {
            rawKey.isBlank() -> "—"
            rawKey.length > 10 -> rawKey.take(6) + "••••••" + rawKey.takeLast(4)
            else -> "••••••"
        }

    val verified: Boolean get() = status == "ok"
    val hasError: Boolean get() = status.isNotBlank() && status != "ok"
}
