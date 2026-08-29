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
    OTHER("نماذج أخرى", "أخرى"),
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

    // ---- Voices ----
    val builtinVoices = listOf(
        AiVoice("achernar", "Achernar", "أنثى", "أمريكي", "صوت دافئ واضح"),
        AiVoice("puck", "Puck", "ذكر", "أمريكي", "صوت شبابي حيوي"),
        AiVoice("kore", "Kore", "أنثى", "بريطاني", "صوت هادئ رسمي"),
        AiVoice("charon", "Charon", "ذكر", "بريطاني", "صوت عميق واثق"),
        AiVoice("fenrir", "Fenrir", "ذكر", "أمريكي", "صوت قوي للسرد"),
        AiVoice("aoede", "Aoede", "أنثى", "أمريكي", "صوت لطيف للقصص"),
    )

    // No sample keys: a fake masked entry can never authenticate,
    // which is exactly what made "add a key first" appear after adding one.
    val sampleKeys = emptyList<ApiKeyEntry>()

    /**
     * The AI agents currently wired into the app's features.
     * Each corresponds to a real capability we already ship.
     * As we add features, we append new agents here.
     */
    fun agents(): List<AiAgent> = listOf(
        AiAgent(
            id = "translator",
            feature = "مترجم الكلمات والجمل",
            description = "يولّد الترجمة العربية للكلمات والأمثلة عند الاستيراد والمراجعة.",
            icon = "translate",
            kind = ModelKind.TEXT,
            modelId = "gemini-2.5-flash",
            character = "مترجم لغوي دقيق ثنائي اللغة (عربي/إنجليزي)",
            voiceId = "",
            style = "دقيق، مختصر، يحافظ على المعنى والسياق",
            prompt = "أنت مترجم محترف. ترجم النص الإنجليزي إلى عربية فصحى واضحة مع الحفاظ على المعنى الدقيق والسياق. قدّم الترجمة فقط دون شرح إضافي.",
        ),
        AiAgent(
            id = "story_writer",
            feature = "كاتب القصص",
            description = "يكتب قصصاً قصيرة تدمج الكلمات المكتملة من دروسك لتثبيتها في سياق.",
            icon = "book",
            kind = ModelKind.TEXT,
            modelId = "gemini-2.5-pro",
            character = "راوٍ مبدع يكتب قصصاً تعليمية بسيطة وممتعة",
            voiceId = "",
            style = "سلس، مشوّق، مستوى لغوي متدرّج حسب المتعلم",
            prompt = "اكتب قصة قصيرة (5-7 جمل) باللغة الإنجليزية تستخدم الكلمات التالية بشكل طبيعي: {WORDS}. اجعلها مناسبة لمتعلم بمستوى {LEVEL}، ممتعة وسهلة الفهم، ثم أرفق ترجمة عربية.",
        ),
        AiAgent(
            id = "story_reader",
            feature = "قارئ القصص (صوت)",
            description = "ينطق القصص المولّدة بصوت طبيعي معبّر.",
            icon = "headphones",
            kind = ModelKind.TTS,
            modelId = "gemini-2.5-pro-tts",
            character = "راوٍ صوتي هادئ ومعبّر",
            voiceId = "aoede",
            style = "سرد قصصي بإيقاع متوسط، نبرة دافئة",
            prompt = "اقرأ النص التالي بنبرة قصصية دافئة وواضحة، بسرعة متوسطة مناسبة لمتعلمي اللغة.",
        ),
        AiAgent(
            id = "conversation",
            feature = "شريك المحادثة",
            description = "يحاورك بناءً على حوارات الدرس المستوردة (ليس عشوائياً).",
            icon = "talk",
            kind = ModelKind.TTS,
            modelId = "gemini-2.5-flash-tts",
            character = "صديق أمريكي ودود يساعدك على التدرّب",
            voiceId = "puck",
            style = "عفوي، مشجّع، يصحّح الأخطاء بلطف",
            prompt = "أنت شريك محادثة ودود. تحدث بالإنجليزية البسيطة ضمن سياق الحوار الحالي: {DIALOGUE}. شجّع المتعلم وصحّح أخطاءه بلطف واطرح أسئلة متابعة قصيرة.",
        ),
        AiAgent(
            id = "phonetics",
            feature = "معلّم الصوتيات",
            description = "ينطق الحروف والأصوات في كورس الصوتيات بدقة.",
            icon = "sound",
            kind = ModelKind.TTS,
            modelId = "gemini-2.5-flash-tts",
            character = "معلّم نطق واضح يركّز على مخارج الحروف",
            voiceId = "kore",
            style = "بطيء وواضح، يبالغ قليلاً في نطق الصوت المستهدف",
            prompt = "انطق الصوت أو الحرف التالي بوضوح وبطء مع أمثلة كلمات: {SOUND}. ركّز على مخرج الصوت الصحيح.",
        ),
        AiAgent(
            id = "mental_image",
            feature = "مولّد الصور الذهنية",
            description = "يولّد صوراً ذهنية مركّبة تربط الكلمة بمعناها بصرياً.",
            icon = "image",
            kind = ModelKind.IMAGE,
            modelId = "imagen-4.0",
            character = "فنان يرسم روابط بصرية سهلة التذكّر",
            voiceId = "",
            style = "صور حيّة، خلية واحدة واضحة لكل كلمة، بأحجام متساوية",
            prompt = "أنشئ صورة مركّبة (شبكة خلايا متساوية للقص لاحقاً) توضّح كل كلمة ومثالها: {WORDS}. أسلوب حيّ سهل التذكّر.",
        ),
        AiAgent(
            id = "coach",
            feature = "المدرب الذكي",
            description = "يحلّل أداءك أسبوعياً ويقترح خطة علاجية.",
            icon = "coach",
            kind = ModelKind.TEXT,
            modelId = "gemini-2.5-pro",
            character = "مدرّب لغة محفّز يقدّم نصائح عملية",
            voiceId = "",
            style = "تحليلي، إيجابي، عملي ومختصر",
            prompt = "حلّل بيانات تعلّم المستخدم التالية: {STATS}. حدّد نقاط القوة والضعف واقترح خطة علاجية عملية لأسبوع، بنبرة محفّزة ومختصرة.",
        ),
        AiAgent(
            id = "quiz_maker",
            feature = "مولّد الاختبارات",
            description = "يبني أسئلة اختبار من كلمات وقواعد الدروس المكتملة.",
            icon = "quiz",
            kind = ModelKind.TEXT,
            modelId = "gemini-2.5-flash",
            character = "مصمّم اختبارات يقيس الفهم بدقة",
            voiceId = "",
            style = "أسئلة متنوعة متدرجة الصعوبة، بلا غموض",
            prompt = "ولّد {N} سؤال اختبار من الكلمات والقواعد التالية: {CONTENT}. نوّع بين المعنى وإكمال الجملة والإملاء، مع خيار صحيح واحد وثلاثة مموّهات منطقية.",
        ),
        AiAgent(
            id = "lesson_creator",
            feature = "مؤلف ومصمم الدروس",
            description = "يولّد دروساً تعليمية غنية ومصممة بدقة وبلوكات متكاملة وفق الشخصية والمستوى المحدد.",
            icon = "edit",
            kind = ModelKind.TEXT,
            modelId = "gemini-2.5-pro",
            character = "أستاذ لغة إنجليزية محترف يصمم دروساً تفاعلية تجمع بين المفردات والحوار والقواعد والتمارين",
            voiceId = "kore",
            style = "تفاعلي، متدرج، غني بالأمثلة الواقعية والتطبيقات العملية",
            prompt = "أنت معلم لغة إنجليزية خبير. صمم درساً تعليمياً متكاملاً بالموضوع {TOPIC} للمستوى {LEVEL} بالأسلوب {STYLE}. اتبع مخطط JSON للدرس بدقة شاملاً المفردات، الحوار، القواعد، القراءة، والتمارين.",
        ),
        AiAgent(
            id = "curriculum_builder",
            feature = "مهندس المناهج والمسارات",
            description = "يبني كورسات ومناهج تعليمية كاملة متسلسلة تدريجياً لتعلم موضوع أو مسار محدد.",
            icon = "school",
            kind = ModelKind.TEXT,
            modelId = "gemini-2.5-pro",
            character = "خبير تخطيط مناهج أكاديمية وتخصصية متقدمة",
            voiceId = "",
            style = "منهجي، متسلسل، يبني المهارة خطوة بخطوة بلا فجوات",
            prompt = "أنت خبير تصميم مناهج لغة إنجليزية. صمم سلسلة دروس متكاملة متدرجة الصعوبة تغطي المسار أو المنهج المطلوب {TOPIC} للمستوى {LEVEL} بعدد دروس {COUNT}.",
        ),
    )
}
