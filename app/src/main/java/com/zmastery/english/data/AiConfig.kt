package com.zmastery.english.data

/**
 * AI configuration layer.
 *
 * Every AI-powered feature in Z-Mastery needs its own tuning:
 *  - model      : which LLM / TTS / image model to use
 *  - character  : the persona/voice identity
 *  - voice      : the TTS voice id
 *  - style      : delivery style (tone / speed / accent)
 *  - prompt     : the system prompt that steers generation
 *
 * These are grouped as "AI Agents" — one per feature. As new AI features
 * are added, we append a new AiAgent here.
 */

enum class ModelKind(val label: String, val short: String) {
    TEXT("نماذج نصية (LLM)", "نصي"),
    TTS("نماذج تحويل النص لكلام (TTS)", "صوتي"),
    LIVE("نماذج حيّة / صوت أصلي (Live)", "حي"),
    IMAGE("نماذج الصور", "صور"),
    VIDEO("نماذج الفيديو", "فيديو"),
    EMBEDDING("نماذج التضمين (Embedding)", "تضمين"),
    OTHER("نماذج أخرى", "أخرى");

    /**
     * Kinds offered in this agent's model picker — strictly this kind, nothing
     * else. A TTS teacher never sees Gemini text or Imagen; an image artist
     * never sees voices; a live partner never sees a writing model.
     */
    val pickerKinds: List<ModelKind> get() = listOf(this)

    /** Whether this kind speaks aloud and therefore needs a voice picker. */
    val usesVoice: Boolean get() = this == TTS || this == LIVE
}

/**
 * A model available from the provider.
 *
 * Everything after [description] is populated only when the model was fetched
 * live from the provider — built-in fallback entries leave them empty.
 */
data class AiModel(
    val id: String,
    val displayName: String,
    val kind: ModelKind,
    val description: String = "",
    /** `supportedGenerationMethods` reported by the API. */
    val methods: List<String> = emptyList(),
    val inputTokenLimit: Int = 0,
    val outputTokenLimit: Int = 0,
    val version: String = "",
    /** Which API versions exposed this model (v1beta / v1alpha). */
    val apiVersions: List<String> = emptyList(),
    /** True when this came from a live models.list call. */
    val fetched: Boolean = false,
) {
    /** True when the id marks it as preview / experimental. */
    val isPreview: Boolean
        get() = id.contains("preview", true) || id.contains("exp", true) ||
            id.contains("-latest", true).not() && id.contains("experimental", true)

    /** Newer families sort first in pickers. */
    val familyRank: Int
        get() = when {
            id.startsWith("gemini-3") -> 100
            id.startsWith("gemini-2.5") -> 90
            id.startsWith("gemini-2.0") -> 80
            id.startsWith("gemini-1.5") -> 60
            id.startsWith("imagen-4") -> 55
            id.startsWith("imagen") -> 50
            id.startsWith("veo") -> 45
            id.startsWith("gemma") -> 40
            else -> 10
        }

    /** Compact capability line for the UI, e.g. "1M ← / 65K →". */
    val tokenLabel: String
        get() {
            fun fmt(n: Int) = when {
                n >= 1_000_000 -> "${n / 1_000_000}M"
                n >= 1000 -> "${n / 1000}K"
                n > 0 -> "$n"
                else -> ""
            }
            val i = fmt(inputTokenLimit)
            val o = fmt(outputTokenLimit)
            return when {
                i.isNotBlank() && o.isNotBlank() -> "دخل $i · خرج $o"
                i.isNotBlank() -> "دخل $i"
                else -> ""
            }
        }
}

/** A TTS voice option. */
data class AiVoice(
    val id: String,
    val displayName: String,
    val gender: String,       // ذكر / أنثى / محايد
    val accent: String,       // أمريكي / بريطاني ...
    val sample: String = "",
)

// NOTE: ApiKeyEntry now lives in AiProvider.kt (it stores the real key).

/**
 * One configurable AI agent tied to a specific feature.
 * The user can edit every field per agent.
 */
data class AiAgent(
    val id: String,
    val feature: String,          // اسم الميزة
    val description: String,      // ماذا يفعل هذا العميل
    val icon: String,             // اسم أيقونة
    val kind: ModelKind,          // نوع النموذج المطلوب
    var modelId: String,          // النموذج المختار
    var character: String,        // الشخصية / الهوية
    var voiceId: String,          // الصوت (لعملاء TTS)
    var style: String,            // الأسلوب
    var prompt: String,           // المطالبة (System Prompt)
)

object AiDefaults {

    // ---- Models the app knows about out of the box (before "fetch models") ----
    val builtinModels = listOf(
        AiModel("gemini-2.5-pro", "Gemini 2.5 Pro", ModelKind.TEXT, "الأقوى للفهم والتوليد المعقد"),
        AiModel("gemini-2.5-flash", "Gemini 2.5 Flash", ModelKind.TEXT, "سريع واقتصادي للمهام اليومية"),
        AiModel("gemini-2.0-flash", "Gemini 2.0 Flash", ModelKind.TEXT, "متوازن للترجمة والقصص"),
        AiModel("gemini-2.5-flash-tts", "Gemini 2.5 Flash TTS", ModelKind.TTS, "تحويل نص إلى كلام طبيعي"),
        AiModel("gemini-2.5-pro-tts", "Gemini 2.5 Pro TTS", ModelKind.TTS, "صوت عالي الجودة متعدد المتحدثين"),
        AiModel("imagen-4.0", "Imagen 4.0", ModelKind.IMAGE, "توليد الصور الذهنية عالية الدقة"),
        AiModel("imagen-3.0", "Imagen 3.0", ModelKind.IMAGE, "توليد صور سريع"),
    )

    // ---- Voices (Gemini-style ids + a learner-facing tone note) ----
    val builtinVoices = listOf(
        AiVoice("puck", "Puck", "ذكر", "أمريكي", "شبابي حيوي — شريك محادثة"),
        AiVoice("kore", "Kore", "أنثى", "بريطاني", "هادئ رسمي — استماع وصوتيات"),
        AiVoice("aoede", "Aoede", "أنثى", "أمريكي", "دافئ قصصي — راوية"),
        AiVoice("charon", "Charon", "ذكر", "بريطاني", "عميق واثق — سرد رصين"),
        AiVoice("fenrir", "Fenrir", "ذكر", "أمريكي", "قوي للسرد الدرامي"),
        AiVoice("achernar", "Achernar", "أنثى", "أمريكي", "واضح دافئ — شرح بطيء"),
        AiVoice("leda", "Leda", "أنثى", "أمريكي", "ناعم قريب — تشجيع"),
        AiVoice("orus", "Orus", "ذكر", "أمريكي", "محايد واضح — اختبارات"),
    )

    // No sample keys: a fake masked entry can never authenticate,
    // which is exactly what made "add a key first" appear after adding one.
    val sampleKeys = emptyList<ApiKeyEntry>()

    /** Every wired persona — prompts, tones and groups live in [AiPrompts]. */
    fun agents(): List<AiAgent> = AiPrompts.agents()
}
