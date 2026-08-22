package com.zmastery.english.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// ==========================================================================
//  الصناديق الغامضة — الجزء 4: مرآة الإدراك بالذكاء الاصطناعي
//
//  ┌──────────────────────────────────────────────────────────────────┐
//  │  CognitiveMirrorPrompt · صياغة المطالبة ببيانات الطالب الحقيقية  │
//  │  CognitiveMirrorService · استدعاء Gemini 2.0 Flash               │
//  │  OfflineMirrorHtml      · البديل العربي الفصيح دون إنترنت        │
//  └──────────────────────────────────────────────────────────────────┘
//
//  الناتج في كل الحالات: نصّ HTML واحد يُحفظ في MysteryReward.descriptionHtmlAr
//  فيصبح جزءاً دائماً من ذاكرة الصندوق ولا يتغيّر أبداً عند إعادة الفتح.
//
//  فلسفة عدم الفشل: لا توجد شاشة خطأ جافّة إطلاقاً. غياب المفتاح أو الإنترنت
//  أو أي استثناء يسقط تلقائياً إلى تقرير محلّي مكتوب بعربية فصيحة جميلة
//  يقتبس أرقام المتعلّم الحقيقية — فلا يشعر أبداً أنه حُرم من شيء.
// ==========================================================================

/** لقطة إحصاءات المتعلّم التي تُغذّي المطالبة. */
data class MirrorStats(
    val learnerName: String,
    val milestoneTitle: String,
    val streakDays: Int,
    val masteredWords: Int,
    val totalWords: Int,
    val lessonsCompleted: Int,
    val studyMinutes: Int,
    val recallRate: Float,
    val continuityPercent: Int,
    val masteryPercent: Int,
    val cefr: String,
    val nextCefr: String,
    val bestStreak: Int,
) {
    val recallPercent: Int get() = (recallRate * 100).roundToInt().coerceIn(0, 100)

    val studyHoursLabel: String
        get() = when {
            studyMinutes >= 60 -> "${studyMinutes / 60} ساعة و${studyMinutes % 60} دقيقة"
            else -> "$studyMinutes دقيقة"
        }

    val who: String get() = if (learnerName.isBlank()) "يا بطل" else "يا $learnerName"
}

/* ══════════════════════════════════════════════════════════════════════
   1 · صياغة المطالبة المخصّصة ببيانات الطالب
   ══════════════════════════════════════════════════════════════════════ */

object CognitiveMirrorPrompt {

    /**
     * يصوغ المطالبة السيكولوجية الموجّهة لـ Gemini.
     * تُقتبس فيها أرقام المتعلّم الحقيقية كلها حتى يشعر أن التقرير كُتب له وحده.
     */
    fun build(s: MirrorStats): String = """
        You are a master psychological educational coach. The learner "${s.learnerName.ifBlank { "the learner" }}" has just unlocked a Mystery Chest in their English app Z-Mastery for completing a milestone: "${s.milestoneTitle}" (Streak: ${s.streakDays} days, Best streak: ${s.bestStreak} days, Mastered: ${s.masteredWords} of ${s.totalWords} words, Lessons: ${s.lessonsCompleted}, Study Time: ${s.studyMinutes} minutes, Memory Recall Rate: ${s.recallPercent}%, 30-day continuity: ${s.continuityPercent}%, Mastery level: ${s.masteryPercent}%, CEFR: ${s.cefr} heading to ${s.nextCefr}).

        Write a deeply personalized, poetic, and highly motivating Arabic psychological analysis of their cognitive identity.
        Do not write dry lists. Speak directly to them ("${s.who}") in a warm, inspiring, and cinematic person-to-person style.

        Structure the response in beautiful HTML tags (like <h3>, <p>, <strong>, <div class='highlight'>) with the following sections:
        1. Cognitive Mirroring (مرآة الإدراك): Analyze their learning style based on their metrics. Call them a specific psychological archetype (e.g. "الصقر العنيد" or "النسر الهادئ") and explain why their ${s.streakDays} days of discipline show an iron-clad executive function.
        2. The Secret Discovery (السر المكتشف في عقلك): Tell them something positive we "discovered" about how their brain handles language storage based on their high recall rate of ${s.recallPercent}%.
        3. Actionable Tactical Wisdom (السلاح التكتيكي القادم): Give them one hyper-focused behavioral hack to overcome potential vocabulary forgetting.

        Keep the tone warm, mysterious, and grand—like a wise mentor revealing an ancient scroll. Return ONLY the HTML content, no markdown blocks.
    """.trimIndent()
}

/* ══════════════════════════════════════════════════════════════════════
   2 · خدمة Gemini 2.0 Flash
   ══════════════════════════════════════════════════════════════════════ */

object CognitiveMirrorService {

    private const val MODEL = "gemini-2.0-flash"

    /**
     * يطلب تقرير HTML من Gemini. لا يرمي أبداً — أي فشل يسقط بصمت إلى
     * [OfflineMirrorHtml.build] فيحصل المتعلّم دوماً على تقرير جميل.
     *
     * @return زوج (نص HTML، هل وُلّد بالذكاء الاصطناعي؟)
     */
    suspend fun generateHtml(
        stats: MirrorStats,
        apiKey: String,
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val offline = { OfflineMirrorHtml.build(stats) }
        if (apiKey.isBlank()) return@withContext offline() to false
        try {
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", CognitiveMirrorPrompt.build(stats))
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.95)
                    put("topP", 0.95)
                    put("maxOutputTokens", 2048)
                })
            }.toString()

            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$MODEL:generateContent?key=$apiKey"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 40000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() }
                ?: return@withContext offline() to false
            if (code !in 200..299) return@withContext offline() to false

            val raw = JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text")

            val html = sanitize(raw)
            if (html.length < 60) offline() to false else html to true
        } catch (e: Exception) {
            offline() to false
        }
    }

    /** ينظّف أسوار الماركداون ويزيل أي وسوم خطرة. */
    private fun sanitize(raw: String): String = raw
        .trim()
        .removePrefix("```html").removePrefix("```HTML").removePrefix("```")
        .removeSuffix("```")
        .replace(Regex("(?is)<script.*?</script>"), "")
        .replace(Regex("(?is)<style.*?</style>"), "")
        .replace(Regex("(?is)</?(html|head|body|iframe|object|embed)[^>]*>"), "")
        .trim()
}

/* ══════════════════════════════════════════════════════════════════════
   3 · البديل المحلّي — عربية فصيحة دون إنترنت
   ══════════════════════════════════════════════════════════════════════ */

/**
 * مولّد التقرير المحلّي. يختار النمط النفسي من الأرقام الحقيقية، ثم يكتب
 * تقريراً بثلاثة فصول بنفس بنية HTML التي يعيدها Gemini تماماً — فلا تفرّق
 * الواجهة بين المصدرين ولا يشعر المتعلّم بأي نقص.
 */
object OfflineMirrorHtml {

    /** النمط النفسي المستخلص من مزيج الانضباط والدقّة. */
    private data class Archetype(val name: String, val emoji: String, val trait: String)

    private fun archetypeFor(s: MirrorStats): Archetype {
        val disciplined = s.streakDays >= 7
        val precise = s.recallPercent >= 75
        val veteran = s.streakDays >= 30 || s.masteredWords >= 150
        return when {
            veteran && precise -> Archetype(
                "النسر الهادئ", "\uD83E\uDD85",
                "يحلّق عالياً بلا ضجيج، يرى الصورة كاملة قبل أن ينقضّ على هدفه",
            )
            veteran -> Archetype(
                "الجبل الراسخ", "\u26F0\uFE0F",
                "لا تزحزحه العواصف، يبني طبقة فوق طبقة حتى تصير القمة أمراً واقعاً",
            )
            disciplined && precise -> Archetype(
                "الصقر العنيد", "\uD83E\uDD85",
                "عينه لا تخطئ الفريسة، وإرادته لا تعرف كلمة غداً",
            )
            disciplined -> Archetype(
                "النهر الذي لا يتوقف", "\uD83C\uDF0A",
                "لا يهتم بسرعته بقدر ما يهتم بألّا يجفّ أبداً — وهكذا ينحت الصخر",
            )
            precise -> Archetype(
                "القنّاص الصبور", "\uD83C\uDFAF",
                "يطلق طلقة واحدة فتصيب، لأنه لا يستعجل الضغط على الزناد",
            )
            else -> Archetype(
                "الشرارة الأولى", "\u2728",
                "في داخله وقود لم يُشعل بعد، وقد بدأ للتو يلمس حجم ما يستطيع",
            )
        }
    }

    /** الحيلة السلوكية المضادة للنسيان، مختارة حسب نقطة الضعف الحقيقية. */
    private fun tacticFor(s: MirrorStats): Pair<String, String> = when {
        s.recallPercent < 55 -> "قاعدة الثواني الثلاث" to
            "قبل أن تكشف أي بطاقة، أغمض عينيك وعُدّ ثلاث ثوانٍ وأنت تحاول استحضار المعنى " +
            "من الفراغ. هذا التوتّر اللحظي — الذي يسمّيه علماء الذاكرة <strong>الاسترجاع الفعّال</strong> — " +
            "يضاعف رسوخ الكلمة أكثر من عشر قراءات سلبية."
        s.recallPercent < 75 -> "تقنية الجملة الشخصية" to
            "لا تحفظ الكلمة وحدها أبداً. اصنع لها جملة واحدة من <strong>حياتك أنت</strong> " +
            "تذكر فيها اسم شخص تعرفه أو مكاناً تحبه. الدماغ يرفض تخزين المجرّد، لكنه " +
            "لا يستطيع نسيان ما ارتبط بك شخصياً."
        s.streakDays < 7 -> "قاعدة الدقيقتين" to
            "في الأيام التي تشعر فيها بالإرهاق، لا تفتح التطبيق لتدرس — افتحه لتراجع " +
            "<strong>بطاقتين فقط</strong>. الهدف ليس التعلّم بل منع انكسار الحلقة العصبية. " +
            "العادة تموت من الانقطاع لا من قلّة الكمّ."
        else -> "مبدأ التباعد المتصاعد" to
            "خذ أصعب خمس كلمات لديك وراجعها اليوم، ثم بعد يومين، ثم بعد خمسة، ثم بعد أسبوعين. " +
            "كل مراجعة على حافة النسيان تماماً تضاعف عمر الذكرى — وهذا سرّ من يتذكّرون " +
            "بعد سنوات بلا مجهود."
    }

    /** يبني تقرير HTML كاملاً بثلاثة فصول. */
    fun build(s: MirrorStats): String {
        val a = archetypeFor(s)
        val (tacticName, tacticBody) = tacticFor(s)

        val disciplineLine = when {
            s.streakDays >= 90 ->
                "تسعون يوماً أو أكثر من الانضباط المتصل ليست عادة — إنها <strong>إعادة تشكيل " +
                    "لبنية دماغك</strong>. القشرة الجبهية لديك تعمل الآن بكفاءة قائد عسكري محنّك."
            s.streakDays >= 30 ->
                "ثلاثون يوماً متصلة هي الحد الفاصل الذي يسقط عنده 90% من الناس. " +
                    "أنت لم تسقط، وهذا يكشف <strong>وظيفة تنفيذية حديدية</strong> لا يملكها إلا القليل."
            s.streakDays >= 7 ->
                "سبعة أيام متصلة تعني أن دماغك بدأ يحجز للدراسة مكاناً ثابتاً في خريطته اليومية — " +
                    "هذه هي <strong>اللحظة التي تتحوّل فيها النية إلى هوية</strong>."
            s.streakDays >= 3 ->
                "ثلاثة أيام ليست رقماً، بل أول <strong>إيقاع</strong> يلتقطه دماغك. " +
                    "من هنا تبدأ العادات الحقيقية بالتشكّل بصمت."
            else ->
                "كل رحلة عظيمة تبدأ بيوم واحد قرّر صاحبه ألّا يؤجّل. " +
                    "وقد <strong>قرّرت أنت</strong>."
        }

        val discoveryLine = when {
            s.recallPercent >= 85 ->
                "معدّل استرجاعك <strong>${s.recallPercent}%</strong> رقم استثنائي. " +
                    "دماغك لا يخزّن الكلمات في الذاكرة قصيرة المدى ثم يتخلّص منها، بل ينقلها " +
                    "مباشرة إلى <strong>الحُصين</strong> ويمنحها وسم «مهم». هذا يعني أن ما تتعلّمه " +
                    "اليوم سيبقى معك بعد سنوات، لا أسابيع."
            s.recallPercent >= 70 ->
                "باسترجاع قدره <strong>${s.recallPercent}%</strong> يتّضح أن ذاكرتك اللغوية " +
                    "<strong>ترابطية لا حفظية</strong>: أنت لا تحفظ الكلمة كصورة صمّاء، بل تربطها " +
                    "بشبكة معانٍ. ولهذا تعود إليك الكلمة حتى بعد أن تظن أنك نسيتها."
            s.recallPercent >= 50 ->
                "استرجاعك <strong>${s.recallPercent}%</strong> يقع في المنطقة الذهبية التي يسمّيها " +
                    "الباحثون <strong>الصعوبة المرغوبة</strong>. أنت تنسى بالقدر الذي يجبر دماغك " +
                    "على بذل جهد الاسترجاع — وهذا الجهد نفسه هو ما يحفر الذكرى عميقاً."
            else ->
                "ذاكرتك الآن في <strong>مرحلة البناء</strong>، وهذه أهم مراحلها على الإطلاق. " +
                    "كل كلمة تنساها ثم تستعيدها تحفر مساراً عصبياً أعمق من كلمة لم تنسها قط."
        }

        val volumeLine = if (s.totalWords > 0) {
            "أتقنت <strong>${s.masteredWords}</strong> كلمة من أصل <strong>${s.totalWords}</strong>، " +
                "وأنهيت <strong>${s.lessonsCompleted}</strong> درساً، واستثمرت <strong>${s.studyHoursLabel}</strong> " +
                "من عمرك في بناء عقل جديد."
        } else {
            "استثمرت <strong>${s.studyHoursLabel}</strong> في بناء أساسك الأول."
        }

        return buildString {
            append("<h3>${a.emoji} مرآة الإدراك — أنت ${a.name}</h3>")
            append("<p>${s.who}، لحظة فتح صندوق «<strong>${s.milestoneTitle}</strong>» ليست صدفة. ")
            append("حين نضع بياناتك تحت المجهر يظهر نمط واحد لا يخطئه فاحص: أنت <strong>${a.name}</strong> — ")
            append("${a.trait}. $disciplineLine</p>")
            append("<div class='highlight'>$volumeLine</div>")

            append("<h3>\uD83D\uDD2E السر المكتشف في عقلك</h3>")
            append("<p>$discoveryLine</p>")
            if (s.continuityPercent >= 60) {
                append("<p>ولاحظنا أمراً آخر: رصيد استمراريتك <strong>${s.continuityPercent}%</strong> ")
                append("خلال الثلاثين يوماً الماضية. هذا يعني أن التزامك ليس نوبة حماس عابرة، ")
                append("بل <strong>نظام تشغيل</strong> صار يعمل في الخلفية دون أن تشعر.</p>")
            }

            append("<h3>\u2694\uFE0F السلاح التكتيكي القادم — $tacticName</h3>")
            append("<p>$tacticBody</p>")

            append("<div class='highlight'>")
            append("\uD83D\uDD25 السلسلة: <strong>${s.streakDays}</strong> يوماً (الأفضل ${s.bestStreak}) &nbsp;·&nbsp; ")
            append("\uD83C\uDF31 الاستمرارية: <strong>${s.continuityPercent}%</strong> &nbsp;·&nbsp; ")
            append("\u2B50 الإتقان: <strong>${s.masteryPercent}%</strong> (${s.cefr} \u2190 ${s.nextCefr})")
            append("</div>")

            append("<p>واصل يا ${if (s.learnerName.isBlank()) "بطل" else s.learnerName}. ")
            append("الطريق من <strong>${s.cefr}</strong> إلى <strong>${s.nextCefr}</strong> ")
            append("لم يعد سؤال «هل أستطيع؟» بل سؤال «متى؟» — والجواب بين يديك.</p>")
        }
    }
}
