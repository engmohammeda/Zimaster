package com.zmastery.english.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt

// ==========================================================================
//  المرحلة الثالثة + الرابعة + الخامسة — Z-Mastery Enigma Core
//
//  ┌────────────────────────────────────────────────────────────────────┐
//  │  القسم الثالث · مرآة الإدراك (AI Cognitive Mirroring)              │
//  │  القسم الرابع · هندسة الخوف من السقوط والتعافي (Loss Aversion)     │
//  │  القسم الخامس · بنية النظام ومسارات البيانات                       │
//  └────────────────────────────────────────────────────────────────────┘
//
//  مخطط التفاعل البرمجي:
//
//     [ نشاط المستخدم ] ──► AppViewModel
//              │
//              ▼
//     EnigmaStreakEngine.computeMetrics(telemetry)
//              │   (يفحص شروط فك الأختام + حالة التصدع + الإنقاذ)
//              ▼
//     Persistence.kt ──► AppState JSON (خفيف: أرقام فقط، لا صور)
//              │
//              ▼
//     Dashboard UI ──► شلالات الدوبامين + الصندوق الباكي + بوابة الإنقاذ
//
//  ملاحظة أداء: كل الحسابات هنا O(n) على صفوف يومية صغيرة (سنة = 365 صف)،
//  بدون أي عمليات I/O أو مؤقتات خلفية — لا استهلاك بطارية ولا إبطاء.
// ==========================================================================

/* ══════════════════════════════════════════════════════════════════════
   القسم الثالث · مرآة الإدراك — أنماط التعلم المستخلصة من البيانات
   ══════════════════════════════════════════════════════════════════════ */

/**
 * نمط سرعة الاستجابة — يُستخلص من متوسط زمن البطاقة.
 * سريع = مندفع إبداعي · بطيء = تحليلي دقيق.
 */
enum class TempoArchetype(
    val label: String,
    val emoji: String,
    val desc: String,
    val colorArgb: Long,
) {
    IMPULSIVE(
        "المندفع المبدع", "\u26A1",
        "تجيب بسرعة وتثق بحدسك — قوّتك في الربط السريع، وخطرك في التسرّع",
        0xFFF59E0B,
    ),
    BALANCED(
        "المتوازن", "\u2696\uFE0F",
        "توازن ممتاز بين السرعة والتأنّي — تقرأ بما يكفي ثم تحسم",
        0xFF10B981,
    ),
    ANALYTICAL(
        "التحليلي الدقيق", "\uD83D\uDD2C",
        "تتمهّل وتزن الخيارات — دقّتك عالية وذاكرتك عميقة، لكن راقب الوقت",
        0xFF3B82F6,
    );

    companion object {
        /** @param avgSeconds متوسط الزمن لكل بطاقة */
        fun from(avgSeconds: Float): TempoArchetype = when {
            avgSeconds <= 0f -> BALANCED
            avgSeconds < 6f -> IMPULSIVE
            avgSeconds <= 14f -> BALANCED
            else -> ANALYTICAL
        }
    }
}

/**
 * نمط التوقيت — من ساعة الذروة في سجل المراجعات.
 */
enum class ChronoArchetype(
    val label: String,
    val emoji: String,
    val desc: String,
    val colorArgb: Long,
) {
    DAWN_OWL(
        "طائر الفجر الملتزم", "\uD83C\uDF05",
        "تدرس والعالم نائم — أعلى تركيز وأقل تشتيت، هذه ميزة نادرة",
        0xFFF97316,
    ),
    DAY_HAWK(
        "صقر النهار", "\u2600\uFE0F",
        "تستثمر ذروة نشاطك النهاري — طاقتك عالية وإنتاجيتك مستقرة",
        0xFFEAB308,
    ),
    EVENING_DEER(
        "غزال المساء", "\uD83C\uDF07",
        "تدرس بعد انتهاء اليوم — عقلك يرسّخ ما تعلّمته أثناء النوم مباشرة",
        0xFF8B5CF6,
    ),
    NIGHT_WOLF(
        "الذئب الليلي الهادئ", "\uD83C\uDF19",
        "الليل ملعبك — هدوء تام وتركيز عميق، فقط احرص على نوم كافٍ",
        0xFF6366F1,
    ),
    UNKNOWN(
        "لم يتحدّد بعد", "\u2753",
        "راجع بضع جلسات أخرى لنكتشف توقيتك المفضّل",
        0xFF9CA3AF,
    );

    companion object {
        fun from(hour: Int?): ChronoArchetype = when (hour) {
            null -> UNKNOWN
            in 4..8 -> DAWN_OWL
            in 9..16 -> DAY_HAWK
            in 17..21 -> EVENING_DEER
            else -> NIGHT_WOLF
        }
    }
}

/**
 * القناة الحسّية المهيمنة — من مقارنة مرات الاستماع بمراحل التذكّر.
 * (المرحلة 1 = صوت · 2 = صورة · 3 = نص)
 */
enum class SensoryChannel(
    val label: String,
    val emoji: String,
    val desc: String,
    val colorArgb: Long,
) {
    AUDITORY(
        "سمعي", "\uD83D\uDD0A",
        "أذنك أقوى من عينك — النطق هو مفتاحك، أكثر من الاستماع المتكرّر",
        0xFF06B6D4,
    ),
    VISUAL(
        "بصري", "\uD83D\uDDBC\uFE0F",
        "الصور الذهنية تفتح ذاكرتك فوراً — استثمر في الروابط الذهنية",
        0xFFA855F7,
    ),
    TEXTUAL(
        "نصّي", "\uD83D\uDCDD",
        "تحتاج رؤية الكلمة مكتوبة — قوّ الجانب السمعي لتصبح متوازناً",
        0xFFF43F5E,
    ),
    MIXED(
        "متعدّد القنوات", "\uD83C\uDF08",
        "تستخدم كل حواسك — أقوى نمط للذاكرة طويلة المدى",
        0xFF10B981,
    );

    companion object {
        /**
         * @param stageHist توزيع مراحل التذكّر [s1, s2, s3, s4]
         *
         * التصنيف على مرحلتين لتفادي تحيّز بنيوي: المرحلتان 3 و4 تُدمجان في
         * قناة "النص"، فلو قارنّا مباشرة لفازت دائماً لأنها تجمع مرحلتين.
         *  1) إن كان التوزّع على المراحل الأربع الخام متقارباً ← متعدّد القنوات.
         *  2) وإلا نطلب هيمنة حقيقية: ≥45% وبفارق ≥15% عن التالية.
         */
        fun from(stageHist: IntArray): SensoryChannel {
            val total = stageHist.sum()
            if (total < 5) return MIXED
            val frac = FloatArray(4) { stageHist[it].toFloat() / total }

            // (1) توزّع متساوٍ تقريباً على المراحل الأربع ← متعدّد القنوات
            if ((frac.max() - frac.min()) < 0.14f) return MIXED

            // (2) هيمنة واضحة لإحدى القنوات الثلاث
            val channels = floatArrayOf(frac[0], frac[1], frac[2] + frac[3])
            val sorted = channels.sortedDescending()
            val top = sorted[0]
            val second = sorted[1]
            if (top < 0.45f || (top - second) < 0.15f) return MIXED
            return when (channels.indexOfFirst { it == top }) {
                0 -> AUDITORY
                1 -> VISUAL
                else -> TEXTUAL
            }
        }
    }
}

/** طبيعة الكلمات التي ينساها المتعلّم (مجرّدة مقابل محسوسة). */
enum class ForgetPattern(
    val label: String,
    val emoji: String,
    val advice: String,
) {
    ABSTRACT(
        "الكلمات المجرّدة", "\uD83D\uDCAD",
        "تنسى المفاهيم غير المرئية — اربطها بمواقف شخصية حقيقية عشتها",
    ),
    CONCRETE(
        "الكلمات المحسوسة", "\uD83E\uDDF1",
        "غريب أن تنسى الملموس — غالباً السبب مراجعة سريعة، أبطئ قليلاً",
    ),
    LONG_WORDS(
        "الكلمات الطويلة", "\uD83D\uDCCF",
        "الطول يربكك — قسّم الكلمة لمقاطع وانطقها ببطء ثلاث مرات",
    ),
    BALANCED_FORGET(
        "لا يوجد نمط واضح", "\u2696\uFE0F",
        "نسيانك موزّع طبيعياً — استمر على نفس النهج",
    ),
    NONE(
        "لا تكاد تنسى", "\uD83C\uDFAF",
        "معدّل نسيانك منخفض جداً — ارفع صعوبة الجدولة لتتحدّى نفسك",
    ),
}

/**
 * مرآة الإدراك — البصمة المعرفية الكاملة للمتعلّم.
 * كل الحقول مشتقّة من بيانات حقيقية، لا شيء مُختلق.
 */
data class CognitiveMirror(
    val tempo: TempoArchetype,
    val chrono: ChronoArchetype,
    val sensory: SensoryChannel,
    val forgetPattern: ForgetPattern,
    // أرقام خام تُعرض في البطاقة
    val avgSecondsPerCard: Float,
    val peakHour: Int?,
    val avgReplays: Float,
    val recallRate: Float,
    val lapseRate: Float,
    val stageHistogram: IntArray,
    val totalReviews: Int,
    val consistencyIndex: Float,   // 0..1 انتظام التوقيت
    val depthIndex: Float,         // 0..1 عمق الترسيخ
) {
    /** الاسم السرّي للبصمة — يُعرض كعنوان مبهر. */
    val codename: String
        get() = "${tempo.label} · ${chrono.label}"

    val peakHourLabel: String
        get() = peakHour?.let {
            val h12 = if (it % 12 == 0) 12 else it % 12
            val period = if (it < 12) "صباحاً" else "مساءً"
            "$h12:00 $period"
        } ?: "—"

    /** مؤشر ثقة التحليل: يحتاج بيانات كافية ليكون ذا معنى. */
    val confidence: Float
        get() = (totalReviews / 60f).coerceIn(0f, 1f)

    val confidenceLabel: String
        get() = when {
            confidence >= 0.85f -> "تحليل عالي الدقة"
            confidence >= 0.5f -> "تحليل موثوق"
            confidence >= 0.2f -> "تحليل أوّلي"
            else -> "يحتاج جلسات أكثر"
        }

    val hasEnoughData: Boolean get() = totalReviews >= 12

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CognitiveMirror) return false
        return tempo == other.tempo && chrono == other.chrono &&
            sensory == other.sensory && forgetPattern == other.forgetPattern &&
            totalReviews == other.totalReviews
    }

    override fun hashCode(): Int {
        var r = tempo.hashCode()
        r = 31 * r + chrono.hashCode()
        r = 31 * r + sensory.hashCode()
        r = 31 * r + forgetPattern.hashCode()
        r = 31 * r + totalReviews
        return r
    }
}

/** تقرير مرآة الإدراك المولَّد (يُحفظ داخل الصندوق فلا يتغيّر عند إعادة الفتح). */
@Serializable
data class MirrorReport(
    val title: String = "",
    val identity: String = "",       // فقرة "من أنت كمتعلّم"
    val superpower: String = "",     // القوة الخارقة
    val blindSpot: String = "",      // النقطة العمياء
    val ritual: String = "",         // الطقس اليومي المقترح
    val prophecy: String = "",       // النبوءة / الأثر المستقبلي
    val stamp: String = "",
    val epochDay: Long = 0L,
    val local: Boolean = true,
) {
    val isEmpty: Boolean
        get() = identity.isBlank() && superpower.isBlank() && blindSpot.isBlank()
}

/* ══════════════════════════════════════════════════════════════════════
   القسم الرابع · هندسة الخوف من السقوط والتعافي
   ══════════════════════════════════════════════════════════════════════ */

/** حالة الصندوق البصرية على الواجهة والودجت. */
enum class ChestMood(val label: String) {
    /** كل شيء بخير — الحد الأدنى منجز. */
    SAFE("آمن"),
    /** لم يُنجز بعد لكن الوقت مبكر. */
    IDLE("بانتظارك"),
    /** بعد 8 مساءً + سلسلة > 3 أيام ← تصدّع ودخان أحمر. */
    CRACKING("متصدّع"),
    /** السلسلة انكسرت فعلاً — بوابة الإنقاذ نشطة. */
    BROKEN("منكسر"),
}

/**
 * حالة "صندوق اليوم الباكي" — منبّه بصري صامت.
 *
 * لا يُفعّل إلا عند اجتماع شرطين حقيقيين:
 *  1) الساعة ≥ [CRACK_HOUR] (افتراضياً 20:00)
 *  2) السلسلة الحالية > [CRACK_MIN_STREAK] (افتراضياً 3 أيام)
 * حتى لا نُخيف مبتدئاً ليس لديه ما يخسره أصلاً.
 */
data class DecayState(
    val mood: ChestMood,
    val streakAtRisk: Int,
    val hoursLeft: Int,
    val minutesLeft: Int,
    /** 0..1 — شدة التصدع والدخان (تتصاعد مع اقتراب منتصف الليل). */
    val severity: Float,
) {
    val isCracking: Boolean get() = mood == ChestMood.CRACKING
    val isBroken: Boolean get() = mood == ChestMood.BROKEN

    val timeLeftLabel: String
        get() = when {
            hoursLeft > 0 -> "$hoursLeft س $minutesLeft د"
            minutesLeft > 0 -> "$minutesLeft دقيقة"
            else -> "دقائق معدودة"
        }

    val message: String
        get() = when (mood) {
            ChestMood.SAFE -> "صندوق اليوم مؤمَّن \u2705"
            ChestMood.IDLE -> "صندوق اليوم بانتظار وردك"
            ChestMood.CRACKING ->
                "\uD83D\uDD25 سلسلتك ($streakAtRisk يوماً) تتصدّع! تبقّى $timeLeftLabel لإنقاذها"
            ChestMood.BROKEN -> "سلسلتك انكسرت — مهمة الإنقاذ متاحة الآن"
        }

    companion object {
        const val CRACK_HOUR = 20
        const val CRACK_MIN_STREAK = 3
    }
}

/** نوع مهمة الإنقاذ المعروضة بعد انكسار السلسلة. */
enum class RescueKind(
    val label: String,
    val detail: String,
    val emoji: String,
    val target: Int,
    val route: String,
) {
    QUICK_QUIZ(
        "اختبار سريع", "أجب على 5 بطاقات خلال 3 دقائق", "\u26A1", 5, "review",
    ),
    SPEAK_FIVE(
        "انطق 5 جمل", "استمع وكرّر خمس جمل تفاعلية", "\uD83D\uDDE3\uFE0F", 5, "review",
    ),
}

/**
 * مهمة الإنقاذ — البوابة البنفسجية المتوهّجة.
 *
 * الفلسفة: لا لوم ولا واجهة حزينة. نعرض فرصة فورية لاستعادة الشعلة،
 * فيتحوّل الانكسار من صدمة إلى لحظة بطولة.
 */
@Serializable
data class RescueMission(
    /** اليوم الذي عُرضت فيه المهمة. */
    val offeredEpochDay: Long = 0L,
    /** السلسلة التي انكسرت والتي ستُستعاد عند النجاح. */
    val streakToRestore: Int = 0,
    val kind: String = "QUICK_QUIZ",
    val progress: Int = 0,
    val completed: Boolean = false,
    /** true بعد استهلاك المكافأة (لا تتكرّر). */
    val claimed: Boolean = false,
    /**
     * لحظة بدء العدّ التنازلي (ملي ثانية). 0 = لم يبدأ المتعلّم بعد.
     * المهمة يجب أن تُنجز خلال [LIMIT_MS] من هذه اللحظة.
     */
    val startedAtMs: Long = 0L,
    /** عدد المحاولات التي انتهت مهلتها (للعرض فقط — لا عقوبة). */
    val timeouts: Int = 0,
) {
    val kindEnum: RescueKind
        get() = runCatching { RescueKind.valueOf(kind) }.getOrDefault(RescueKind.QUICK_QUIZ)

    val target: Int get() = kindEnum.target

    val ratio: Float get() = (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)

    val isActive: Boolean get() = offeredEpochDay > 0 && !claimed

    val remaining: Int get() = (target - progress).coerceAtLeast(0)

    /** بدأ العدّ التنازلي ولم يُكمل بعد. */
    val isRunning: Boolean get() = startedAtMs > 0L && !completed

    /** الملي ثانية المتبقية من المهلة (0 = انتهت أو لم تبدأ). */
    fun msLeft(now: Long = System.currentTimeMillis()): Long =
        if (!isRunning) 0L else (startedAtMs + LIMIT_MS - now).coerceAtLeast(0L)

    /** الثواني المتبقية للعرض. */
    fun secondsLeft(now: Long = System.currentTimeMillis()): Int = (msLeft(now) / 1000L).toInt()

    /** "2:45" — نص العدّاد. */
    fun timerLabel(now: Long = System.currentTimeMillis()): String {
        val s = secondsLeft(now)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** انتهت المهلة قبل إكمال الهدف. */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        isRunning && msLeft(now) <= 0L

    companion object {
        /** المهلة المنصوص عليها: ثلاث دقائق فقط. */
        const val LIMIT_MS = 3 * 60 * 1000L
    }
}

/* ══════════════════════════════════════════════════════════════════════
   القسم الخامس · النواة الحسابية
   ══════════════════════════════════════════════════════════════════════ */

/**
 * النواة الذكية: تحسب البصمة المعرفية، وحالة التصدع، وتصوغ مطالبة Gemini،
 * وتولّد تقريراً محلياً فورياً عند غياب الإنترنت أو المفتاح.
 */
object EnigmaStreakEngine {

    // ─────────────────────────── مرآة الإدراك ───────────────────────────

    /**
     * يبني البصمة المعرفية من السجلات الخام.
     *
     * @param logs        سجل المراجعات (زمن، مرحلة، إعادات، تقييم)
     * @param hours       ساعات المراجعة المسجّلة
     * @param leeches     الكلمات كثيرة النسيان
     * @param dayRows     الصفوف اليومية (لحساب الانتظام)
     */
    fun computeMirror(
        logs: List<ReviewLog>,
        hours: List<Int>,
        leeches: List<VocabWord>,
        dayRows: Map<Long, DayStat>,
    ): CognitiveMirror {
        val n = logs.size

        // متوسط الزمن لكل بطاقة (ثوانٍ) — نتجاهل القيم الشاذة (> 5 دقائق).
        val times = logs.map { it.timeMs }.filter { it in 200..300_000 }
        val avgSec = if (times.isEmpty()) 0f else (times.average() / 1000.0).toFloat()

        // توزيع مراحل التذكّر 1..4
        val hist = IntArray(4)
        logs.forEach { l ->
            val idx = (l.reachedStage - 1).coerceIn(0, 3)
            hist[idx]++
        }

        val avgReplays = if (logs.isEmpty()) 0f else logs.map { it.replays }.average().toFloat()
        val recall = if (logs.isEmpty()) 0f else logs.count { it.grade >= 2 }.toFloat() / logs.size
        val lapse = if (logs.isEmpty()) 0f else logs.count { it.grade == 1 }.toFloat() / logs.size

        val peak = Telemetry.peakHour(hours)

        // انتظام التوقيت: كلّما تركّزت الساعات حول الذروة ارتفع المؤشر.
        val consistency = if (hours.size < 5 || peak == null) 0f else {
            val near = hours.count { abs(circularHourDelta(it, peak)) <= 2 }
            (near.toFloat() / hours.size).coerceIn(0f, 1f)
        }

        // عمق الترسيخ: نسبة التذكّر من الصوت/الصورة (مرحلة 1-2) = ذاكرة قوية.
        val early = hist[0] + hist[1]
        val depth = if (n == 0) 0f else (early.toFloat() / n).coerceIn(0f, 1f)

        return CognitiveMirror(
            tempo = TempoArchetype.from(avgSec),
            chrono = ChronoArchetype.from(peak),
            sensory = SensoryChannel.from(hist),
            forgetPattern = classifyForgetting(leeches, lapse),
            avgSecondsPerCard = avgSec,
            peakHour = peak,
            avgReplays = avgReplays,
            recallRate = recall,
            lapseRate = lapse,
            stageHistogram = hist,
            totalReviews = n,
            consistencyIndex = consistency,
            depthIndex = depth,
        )
    }

    /** أقصر مسافة بين ساعتين على مدار 24 ساعة. */
    private fun circularHourDelta(a: Int, b: Int): Int {
        val d = abs(a - b)
        return minOf(d, 24 - d)
    }

    /**
     * تصنيف نمط النسيان. نستخدم كشفاً لغوياً بسيطاً: الكلمات المجرّدة تميل
     * لامتلاك لواحق اشتقاقية (‑tion, ‑ness, ‑ity, ‑ment, ‑ance, ‑ism…).
     */
    private fun classifyForgetting(leeches: List<VocabWord>, lapseRate: Float): ForgetPattern {
        if (leeches.isEmpty()) {
            return if (lapseRate < 0.08f) ForgetPattern.NONE else ForgetPattern.BALANCED_FORGET
        }
        val abstractSuffixes = listOf(
            "tion", "sion", "ness", "ity", "ment", "ance", "ence",
            "ism", "ship", "hood", "cy", "ure", "al", "ude",
        )
        var abstractN = 0
        var longN = 0
        leeches.forEach { w ->
            val e = w.english.trim().lowercase()
            if (abstractSuffixes.any { e.endsWith(it) }) abstractN++
            if (e.length >= 9) longN++
        }
        val total = leeches.size
        return when {
            abstractN.toFloat() / total >= 0.45f -> ForgetPattern.ABSTRACT
            longN.toFloat() / total >= 0.5f -> ForgetPattern.LONG_WORDS
            total >= 3 -> ForgetPattern.CONCRETE
            else -> ForgetPattern.BALANCED_FORGET
        }
    }

    // ─────────────────────── الخوف من السقوط ───────────────────────

    /**
     * يحسب حالة الصندوق. دالة نقيّة تُستدعى عند إعادة التركيب فقط —
     * لا مؤقتات ولا خدمات خلفية.
     *
     * @param minimumDone  أُنجز الحد الأدنى اليومي (الورد المصغّر)
     * @param currentStreak السلسلة الحالية
     * @param hourNow      الساعة الآن 0..23
     * @param minuteNow    الدقيقة الآن 0..59
     * @param streakBroken انكسرت السلسلة ومهمة الإنقاذ مطلوبة
     */
    fun computeDecay(
        minimumDone: Boolean,
        currentStreak: Int,
        hourNow: Int,
        minuteNow: Int,
        streakBroken: Boolean,
    ): DecayState {
        val minsToMidnight = ((24 - hourNow) * 60 - minuteNow).coerceAtLeast(0)
        val h = minsToMidnight / 60
        val m = minsToMidnight % 60

        if (streakBroken) {
            return DecayState(ChestMood.BROKEN, currentStreak, h, m, 1f)
        }
        if (minimumDone) {
            return DecayState(ChestMood.SAFE, currentStreak, h, m, 0f)
        }
        val eligible = hourNow >= DecayState.CRACK_HOUR &&
            currentStreak > DecayState.CRACK_MIN_STREAK
        if (!eligible) {
            return DecayState(ChestMood.IDLE, currentStreak, h, m, 0f)
        }
        // الشدّة تتصاعد من 20:00 حتى منتصف الليل.
        val windowMins = (24 - DecayState.CRACK_HOUR) * 60
        val elapsed = (windowMins - minsToMidnight).coerceIn(0, windowMins)
        val severity = (elapsed.toFloat() / windowMins).coerceIn(0.15f, 1f)
        return DecayState(ChestMood.CRACKING, currentStreak, h, m, severity)
    }

    /** اختيار نوع مهمة الإنقاذ حسب القناة الحسّية المهيمنة. */
    fun pickRescueKind(mirror: CognitiveMirror): RescueKind =
        if (mirror.sensory == SensoryChannel.AUDITORY) RescueKind.SPEAK_FIVE
        else RescueKind.QUICK_QUIZ

    // ─────────────────────── التقرير المحلّي السريع ───────────────────────

    /**
     * يولّد "مرآة الإدراك" محلياً بالكامل — يعمل دون إنترنت ودون مفتاح.
     * يقتبس أرقام المتعلّم الحقيقية ليشعر أن التطبيق يعرفه فعلاً.
     */
    fun localMirrorReport(
        m: CognitiveMirror,
        mo: Momentum3D,
        name: String,
        totalWords: Int,
        stamp: String,
    ): MirrorReport {
        val who = if (name.isBlank()) "يا بطل" else "يا $name"
        val sec = if (m.avgSecondsPerCard > 0) String.format("%.1f", m.avgSecondsPerCard) else "—"

        val identity = buildString {
            append("$who، بعد ${m.totalReviews} مراجعة حقيقية تكشف بياناتك عن نمط واضح: ")
            append("أنت **${m.tempo.label}** ${m.tempo.emoji} — ${m.tempo.desc}. ")
            if (m.chrono != ChronoArchetype.UNKNOWN) {
                append("وذروتك عند ${m.peakHourLabel}، أي أنك **${m.chrono.label}** ${m.chrono.emoji}.")
            }
        }

        val superpower = when (m.sensory) {
            SensoryChannel.AUDITORY ->
                "قوّتك الخارقة سمعية ${m.sensory.emoji}: ${pct(m.stageHistogram[0], m.totalReviews)}% من كلماتك " +
                    "تسترجعها من الصوت وحده قبل رؤية أي صورة أو نص. هذه قدرة يملكها قلّة."
            SensoryChannel.VISUAL ->
                "قوّتك الخارقة بصرية ${m.sensory.emoji}: الصورة الذهنية تفتح ذاكرتك فوراً " +
                    "(${pct(m.stageHistogram[1], m.totalReviews)}% من استرجاعك). استثمر في الروابط الذهنية بكثافة."
            SensoryChannel.TEXTUAL ->
                "قوّتك في التحليل النصّي ${m.sensory.emoji}: تقرأ الكلمة والمثال فتنكشف لك المعاني بدقّة."
            SensoryChannel.MIXED ->
                "قوّتك الخارقة أنك **متعدّد القنوات** ${m.sensory.emoji}: تستخدم السمع والبصر والنص معاً — " +
                    "وهذا أقوى تركيب لتثبيت الذاكرة طويلة المدى."
        } + " معدّل تذكّرك ${(m.recallRate * 100).roundToInt()}% وعمق ترسيخك ${(m.depthIndex * 100).roundToInt()}%."

        val blindSpot = buildString {
            append("نقطتك العمياء: ${m.forgetPattern.label} ${m.forgetPattern.emoji}. ")
            append("${m.forgetPattern.advice}. ")
            when {
                m.avgSecondsPerCard in 0.1f..4f ->
                    append("كما أن $sec ثانية للبطاقة سريعة جداً — امنح نفسك ثانيتين إضافيتين قبل الحسم.")
                m.avgSecondsPerCard > 20f ->
                    append("وتستغرق $sec ثانية للبطاقة — التردّد الطويل يستهلك طاقتك، احسم أسرع.")
                m.avgReplays > 2.5f ->
                    append("وتعيد الاستماع ${String.format("%.1f", m.avgReplays)} مرة للبطاقة — جرّب النطق بصوتك بدل الإعادة.")
                else ->
                    append("وإيقاعك العام سليم — حافظ عليه.")
            }
        }

        val ritual = buildString {
            append("طقسك الأمثل: ")
            when (m.chrono) {
                ChronoArchetype.DAWN_OWL -> append("راجع 15 بطاقة بعد صلاة الفجر مباشرة، فذاكرتك في ذروتها حينها. ")
                ChronoArchetype.DAY_HAWK -> append("خصّص 10 دقائق ثابتة في منتصف نهارك — استغل ذروة طاقتك. ")
                ChronoArchetype.EVENING_DEER -> append("راجع قبل النوم بساعة؛ النوم سيثبّت ما راجعته تلقائياً. ")
                ChronoArchetype.NIGHT_WOLF -> append("حدّد سقفاً لجلستك الليلية ولا تؤجّل النوم — الترسيخ يحدث أثناء النوم. ")
                ChronoArchetype.UNKNOWN -> append("جرّب المراجعة في أوقات مختلفة أسبوعاً لنكتشف ذروتك. ")
            }
            append("وثبّت وقتك: انتظامك الحالي ${(m.consistencyIndex * 100).roundToInt()}%.")
        }

        val prophecy = buildString {
            append("النبوءة: برصيد استمرارية ${mo.continuityPct}% ومستوى ${mo.cefr} و$totalWords كلمة، ")
            if (mo.streak >= 7) {
                append("أنت في المسار الذي يوصل إلى ${mo.nextCefr} — ")
                append("استمرارك 30 يوماً أخرى بنفس الإيقاع يجعل ذلك شبه محتوم.")
            } else {
                append("كل ما تحتاجه هو 7 أيام متتالية لتشعر بالفرق بنفسك — ابدأها اليوم.")
            }
        }

        return MirrorReport(
            title = "مرآة الإدراك · ${m.codename}",
            identity = identity,
            superpower = superpower,
            blindSpot = blindSpot,
            ritual = ritual,
            prophecy = prophecy,
            stamp = stamp,
            epochDay = Telemetry.today(),
            local = true,
        )
    }

    private fun pct(part: Int, total: Int): Int =
        if (total <= 0) 0 else ((part.toFloat() / total) * 100).roundToInt()

    // ─────────────────────── مطالبة Gemini ───────────────────────

    /** يصوغ المطالبة السيكولوجية المخصّصة لـ Gemini. */
    fun buildMirrorPrompt(
        m: CognitiveMirror,
        mo: Momentum3D,
        name: String,
        totalWords: Int,
        masteredWords: Int,
        leechSamples: List<String>,
        tierName: String,
    ): String = buildString {
        appendLine("You are an elite learning psychologist and a warm personal mentor writing to an ARABIC-speaking learner.")
        appendLine("You have their REAL study telemetry. Write a short, striking 'Cognitive Mirror' that makes them feel")
        appendLine("genuinely SEEN and understood — as if you had watched every study session.")
        appendLine()
        appendLine("LEARNER")
        appendLine("- Name: ${name.ifBlank { "(unknown)" }}")
        appendLine("- Chest just unlocked: $tierName")
        appendLine("- Dictionary: $totalWords words, $masteredWords mastered")
        appendLine("- CEFR: ${mo.cefr} (next: ${mo.nextCefr}), mastery ${mo.masteryPct}%")
        appendLine("- Streak: ${mo.streak} days (best ${mo.bestStreak}), continuity ${mo.continuityPct}% of last 30 days")
        appendLine()
        appendLine("BEHAVIOURAL TELEMETRY")
        appendLine("- Total reviews analysed: ${m.totalReviews}")
        appendLine("- Avg time per card: ${String.format("%.1f", m.avgSecondsPerCard)}s -> tempo archetype: ${m.tempo.name}")
        appendLine("- Peak study hour: ${m.peakHour ?: -1} (24h) -> chrono archetype: ${m.chrono.name}")
        appendLine("- Timing consistency index: ${(m.consistencyIndex * 100).roundToInt()}%")
        appendLine("- Recall stage histogram [audio-only, image, text, full-reveal]: ${m.stageHistogram.joinToString()}")
        appendLine("  -> dominant sensory channel: ${m.sensory.name}")
        appendLine("- Avg audio replays per card: ${String.format("%.2f", m.avgReplays)}")
        appendLine("- Recall rate: ${(m.recallRate * 100).roundToInt()}% · lapse rate: ${(m.lapseRate * 100).roundToInt()}%")
        appendLine("- Memory depth index (recalled before seeing text): ${(m.depthIndex * 100).roundToInt()}%")
        appendLine("- Forgetting pattern: ${m.forgetPattern.name}")
        if (leechSamples.isNotEmpty()) {
            appendLine("- Words they repeatedly forget: ${leechSamples.joinToString(", ")}")
        }
        appendLine()
        appendLine("TASK — return ONLY a raw JSON object (no markdown, no code fences) with EXACTLY these keys:")
        appendLine("  \"title\"      : a short striking Arabic codename for this learner's cognitive profile (max 45 chars).")
        appendLine("  \"identity\"   : 2-3 Arabic sentences describing WHO they are as a learner. Quote real numbers.")
        appendLine("  \"superpower\" : 1-2 Arabic sentences naming their genuine cognitive strength, backed by a real statistic.")
        appendLine("  \"blind_spot\" : 1-2 Arabic sentences naming their real weakness honestly but kindly, with the fix.")
        appendLine("  \"ritual\"     : one concrete Arabic sentence prescribing the ideal daily ritual for THIS chrono type.")
        appendLine("  \"prophecy\"   : one inspiring Arabic sentence about where this trajectory leads. Specific, not generic.")
        appendLine()
        appendLine("RULES")
        appendLine("- Write ALL values in eloquent, warm, natural Arabic. Never translate the learner's English vocabulary.")
        appendLine("- Quote their ACTUAL numbers (seconds, percentages, hour, counts). Never invent data.")
        appendLine("- Address them directly. Use their name if provided.")
        appendLine("- Be specific and psychological — this must feel impossible to have been written for anyone else.")
        appendLine("- No clichés, no empty motivation, no bullet lists inside the values.")
        appendLine("- Keep each value under 320 characters.")
    }
}

/* ══════════════════════════════════════════════════════════════════════
   خدمة الشبكة — Gemini 2.0 Flash
   ══════════════════════════════════════════════════════════════════════ */

object MirrorService {

    /**
     * يطلب التقرير من Gemini، ويسقط تلقائياً للتقرير المحلّي عند أي فشل
     * (لا مفتاح، لا إنترنت، خطأ في الاستجابة) — فلا تبقى الواجهة فارغة أبداً.
     */
    suspend fun generate(
        m: CognitiveMirror,
        mo: Momentum3D,
        name: String,
        totalWords: Int,
        masteredWords: Int,
        leechSamples: List<String>,
        tierName: String,
        apiKey: String,
        stamp: String,
    ): MirrorReport = withContext(Dispatchers.IO) {
        val fallback = {
            EnigmaStreakEngine.localMirrorReport(m, mo, name, totalWords, stamp)
        }
        if (apiKey.isBlank()) return@withContext fallback()
        try {
            val prompt = EnigmaStreakEngine.buildMirrorPrompt(
                m, mo, name, totalWords, masteredWords, leechSamples, tierName,
            )
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.9)
                    put("responseMimeType", "application/json")
                })
            }.toString()

            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.0-flash:generateContent?key=$apiKey"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 35000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() } ?: return@withContext fallback()
            if (code !in 200..299) return@withContext fallback()

            val text = JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text")
                .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

            val j = JSONObject(text)
            val report = MirrorReport(
                title = j.optString("title").ifBlank { "مرآة الإدراك · ${m.codename}" },
                identity = j.optString("identity"),
                superpower = j.optString("superpower"),
                blindSpot = j.optString("blind_spot"),
                ritual = j.optString("ritual"),
                prophecy = j.optString("prophecy"),
                stamp = stamp,
                epochDay = Telemetry.today(),
                local = false,
            )
            if (report.isEmpty) fallback() else report
        } catch (e: Exception) {
            fallback()
        }
    }
}
