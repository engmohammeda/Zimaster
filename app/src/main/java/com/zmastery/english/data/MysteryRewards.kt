package com.zmastery.english.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// ==========================================================================
//  الصناديق الغامضة — الجزء 1: نماذج البيانات ومحرك المؤشرات
//
//  ┌──────────────────────────────────────────────────────────────────┐
//  │  MysteryReward   · كائن الصندوق المخزَّن (خفيف: أرقام ونصوص فقط) │
//  │  RewardRarity    · درجة الندرة التي تقود اللون والوهج والاحتفال  │
//  │  MysteryCatalog  · مصنع الصناديق السبعة + صناديق إتمام الكورسات  │
//  │  EnigmaStreakEngine.computeMetrics · المؤشرات الثلاثة            │
//  └──────────────────────────────────────────────────────────────────┘
//
//  فلسفة التخزين: لا صور ولا ملفات — الصندوق كله نص وأرقام، فسنة كاملة
//  من الصناديق لا تتجاوز بضعة كيلوبايت داخل الـ DataStore.
// ==========================================================================

/** درجة ندرة الصندوق — تقود اللون والوهج ومدة مراسم الفتح. */
enum class RewardRarity(
    val label: String,
    val colorArgb: Long,
    val glowArgb: Long,
    /** مدة اهتزاز كسر الختم بالملي ثانية — كلما ندر الصندوق طالت المراسم. */
    val shakeMs: Int,
    /** عدد جسيمات الاحتفال المنطلقة عند الفتح. */
    val confetti: Int,
) {
    COMMON("عادي", 0xFF9CA3AF, 0xFFD1D5DB, 700, 40),
    UNCOMMON("غير شائع", 0xFF3B82F6, 0xFF93C5FD, 850, 60),
    RARE("نادر", 0xFF8B5CF6, 0xFFC4B5FD, 1000, 90),
    EPIC("ملحمي", 0xFFD9A44E, 0xFFFCD34D, 1150, 130),
    LEGENDARY("أسطوري", 0xFFDC2626, 0xFFFCA5A5, 1300, 180),
    MYSTICAL("غامض", 0xFF10B981, 0xFF6EE7B7, 1500, 240);

    companion object {
        fun from(name: String): RewardRarity =
            runCatching { valueOf(name) }.getOrDefault(COMMON)
    }
}

/**
 * صندوق غامض واحد — الحالة المحفوظة بالكامل.
 *
 * @param key               معرّف المعلم الثابت: daily_spark · first_rhythm_3d ·
 *                          glowing_week_7d · gate_month_30d · transformation_90d ·
 *                          sovereignty_180d · transcendence_365d · course_<id>
 * @param title             اسم المعلم البارز الظاهر على الصندوق
 * @param rewardName        اسم الجائزة المغلّفة بالداخل (تبقى سرّية حتى الفتح)
 * @param unlockedAtDay     يوم الاستحقاق (Epoch Day) — 0 إن لم يُستحق بعد
 * @param descriptionHtmlAr التقرير المولَّد بالذكاء الاصطناعي بتنسيق HTML مبسّط
 * @param themeUnlockKey    مفتاح السمة المفتوحة (VANTABLACK · GOLD …)
 * @param privilegeAr       الصلاحية السيادية الممنوحة للمستخدم
 */
@Serializable
data class MysteryReward(
    val id: String,
    val key: String,
    val title: String,
    val rewardName: String,
    val badgeEmoji: String,
    val rarity: RewardRarity,
    val unlockedAtDay: Long,
    val isOpened: Boolean = false,
    val openedAt: Long = 0L,
    val descriptionHtmlAr: String = "",
    val xpAwarded: Int = 0,
    val themeUnlockKey: String? = null,
    val privilegeAr: String? = null,
) {
    /** استُحق الصندوق وصار جاهزاً للكسر لكنه ما زال مختوماً. */
    val isSealed: Boolean get() = unlockedAtDay > 0L && !isOpened

    /** لم يُستحق بعد — يظهر رمادياً مع عدّاد تنازلي. */
    val isDormant: Boolean get() = unlockedAtDay <= 0L

    /** عدد الأيام المطلوبة لهذا المعلم (0 لصناديق الكورسات). */
    val requiredDay: Int get() = MysteryCatalog.dayForKey(key)

    val isCourseChest: Boolean get() = key.startsWith("course_")
}

/**
 * مصنع الصناديق. يبني الكائنات من المعالم الثابتة ويزامنها مع تقدّم المتعلّم
 * دون أن يمسّ أبداً صندوقاً سبق فتحه (الجائزة المكتسبة لا تُسحب).
 */
object MysteryCatalog {

    /** المعلم الواحد قبل أن يتحوّل إلى صندوق محفوظ. */
    data class Milestone(
        val key: String,
        val day: Int,
        val title: String,
        val rewardName: String,
        val badgeEmoji: String,
        val rarity: RewardRarity,
        val xp: Int,
        val themeKey: String? = null,
        val privilege: String? = null,
    )

    /** المعالم السبعة الثابتة، مرتّبة تصاعدياً. */
    val milestones: List<Milestone> = listOf(
        Milestone(
            key = "daily_spark", day = 1,
            title = "الشرارة اليومية",
            rewardName = "بطاقة الحكمة الأولى",
            badgeEmoji = "\u2728", rarity = RewardRarity.COMMON, xp = 25,
            privilege = "فتح سجلّ الشرارات اليومية",
        ),
        Milestone(
            key = "first_rhythm_3d", day = 3,
            title = "أول إيقاع",
            rewardName = "مضاعف الخبرة ×2 لثلاثين دقيقة",
            badgeEmoji = "\uD83C\uDFB5", rarity = RewardRarity.UNCOMMON, xp = 50,
            privilege = "تفعيل مضاعف الخبرة يدوياً مرة واحدة",
        ),
        Milestone(
            key = "glowing_week_7d", day = 7,
            title = "الأسبوع المتوهّج",
            rewardName = "درع تجميد السلسلة",
            badgeEmoji = "\uD83D\uDD25", rarity = RewardRarity.RARE, xp = 100,
            themeKey = "EMBER",
            privilege = "حماية يوم واحد فائت تلقائياً",
        ),
        Milestone(
            key = "gate_month_30d", day = 30,
            title = "بوابة الشهر",
            rewardName = "السمة البرونزية الملكية + المنطقة السرّية",
            badgeEmoji = "\uD83C\uDFC6", rarity = RewardRarity.EPIC, xp = 300,
            themeKey = "BRONZE",
            privilege = "دخول منطقة التحديات المتقدّمة",
        ),
        Milestone(
            key = "transformation_90d", day = 90,
            title = "رحلة التحوّل",
            rewardName = "سمة الذهب الملكي + النبرة الصوتية الفخمة",
            badgeEmoji = "\uD83D\uDC51", rarity = RewardRarity.LEGENDARY, xp = 500,
            themeKey = "GOLD",
            privilege = "ترشيح دائم لمجلس النخبة الصوتي",
        ),
        Milestone(
            key = "sovereignty_180d", day = 180,
            title = "السيادة الملكية",
            rewardName = "امتياز رعاية متعلّم متعثّر",
            badgeEmoji = "\uD83E\uDD1D", rarity = RewardRarity.LEGENDARY, xp = 1000,
            themeKey = "OBSIDIAN",
            privilege = "إهداء درع سلسلة لمبتدئ + تقرير اليوم 1 مقابل 180",
        ),
        Milestone(
            key = "transcendence_365d", day = 365,
            title = "الخلود السنوي",
            rewardName = "سيرتك اللغوية الفخرية الخالدة",
            badgeEmoji = "\u267E\uFE0F", rarity = RewardRarity.MYSTICAL, xp = 3650,
            themeKey = "VANTABLACK",
            privilege = "صفحة الإرث التفاعلية + أيقونة الأستاذ المتوهّج",
        ),
    )

    fun milestoneFor(key: String): Milestone? = milestones.firstOrNull { it.key == key }

    fun dayForKey(key: String): Int = milestoneFor(key)?.day ?: 0

    /** أعلى معلم استحقّه المتعلّم بهذه السلسلة. */
    fun highestEarned(streak: Int): Milestone? =
        milestones.lastOrNull { streak >= it.day }

    /** المعلم التالي الذي يسعى إليه، أو null إن أنهى الجميع. */
    fun next(streak: Int): Milestone? = milestones.firstOrNull { streak < it.day }

    /** التقدّم 0..1 من المعلم السابق نحو التالي. */
    fun progressToNext(streak: Int): Float {
        val nxt = next(streak) ?: return 1f
        val prev = milestones.filter { it.day <= streak }.maxOfOrNull { it.day } ?: 0
        val span = (nxt.day - prev).coerceAtLeast(1)
        return ((streak - prev).toFloat() / span).coerceIn(0f, 1f)
    }

    /** نص العدّاد التنازلي المعروض على الصندوق المقفل. */
    fun countdownLabel(streak: Int, day: Int): String {
        val left = (day - streak).coerceAtLeast(0)
        return when {
            left <= 0 -> "جاهز للفتح"
            left == 1 -> "تدرّب يوماً واحداً إضافياً"
            left == 2 -> "تدرّب يومين إضافيين"
            left <= 10 -> "تدرّب $left أيام إضافية"
            else -> "تدرّب $left يوماً إضافياً"
        }
    }

    /** ندرة صندوق إتمام كورس — تتصاعد بحجم الكورس. */
    fun courseRarity(lessonCount: Int): RewardRarity = when {
        lessonCount >= 40 -> RewardRarity.LEGENDARY
        lessonCount >= 20 -> RewardRarity.EPIC
        lessonCount >= 10 -> RewardRarity.RARE
        else -> RewardRarity.UNCOMMON
    }

    /** يبني صندوق معلم واحد بحالته الصحيحة (مستحق أو خامل). */
    fun buildStreakChest(m: Milestone, streak: Int, todayDay: Long): MysteryReward =
        MysteryReward(
            id = "chest_${m.key}",
            key = m.key,
            title = m.title,
            rewardName = m.rewardName,
            badgeEmoji = m.badgeEmoji,
            rarity = m.rarity,
            unlockedAtDay = if (streak >= m.day) todayDay else 0L,
            xpAwarded = m.xp,
            themeUnlockKey = m.themeKey,
            privilegeAr = m.privilege,
        )

    /** يبني صندوق إتمام كورس. */
    fun buildCourseChest(
        courseId: Int,
        courseName: String,
        lessonCount: Int,
        todayDay: Long,
    ): MysteryReward {
        val rarity = courseRarity(lessonCount)
        return MysteryReward(
            id = "chest_course_$courseId",
            key = "course_$courseId",
            title = "إتمام: $courseName",
            rewardName = "شارة إتقان الكورس + مكافأة الإنجاز",
            badgeEmoji = "\uD83C\uDF93",
            rarity = rarity,
            unlockedAtDay = todayDay,
            xpAwarded = 120 + lessonCount * 15,
            privilegeAr = "فتح مراجعة الكورس المكثّفة",
        )
    }

    /**
     * يزامن قائمة الصناديق المحفوظة مع الواقع الحالي.
     *
     * القواعد الصارمة:
     *  • الصندوق المفتوح لا يُمَس أبداً (لا يُعاد قفله ولا تُسحب جائزته).
     *  • الصندوق المستحق سابقاً يبقى مستحقاً حتى لو انكسرت السلسلة لاحقاً.
     *  • الصناديق الجديدة تُضاف فقط، ولا يُحذف شيء.
     *
     * @param existing الصناديق المحفوظة حالياً
     * @param streak   السلسلة المؤهِّلة (الأفضل بين الحالية وأفضل سلسلة)
     */
    fun sync(
        existing: List<MysteryReward>,
        streak: Int,
        todayDay: Long,
    ): List<MysteryReward> {
        val byKey = existing.associateBy { it.key }.toMutableMap()
        milestones.forEach { m ->
            val old = byKey[m.key]
            when {
                // مفتوح ← لا يُمس إطلاقاً
                old != null && old.isOpened -> Unit
                // موجود ومستحق سابقاً ← يبقى مستحقاً
                old != null && old.unlockedAtDay > 0L -> Unit
                // موجود لكنه خامل ← يُستحق الآن إن بلغ الشرط
                old != null -> if (streak >= m.day) {
                    byKey[m.key] = old.copy(unlockedAtDay = todayDay)
                }
                // غير موجود ← يُنشأ
                else -> byKey[m.key] = buildStreakChest(m, streak, todayDay)
            }
        }
        // ترتيب: صناديق السلسلة بترتيب المعالم، ثم صناديق الكورسات
        val ordered = milestones.mapNotNull { byKey[it.key] }
        val courses = existing.filter { it.isCourseChest }
            .map { byKey[it.key] ?: it }
            .sortedBy { it.id }
        return ordered + courses
    }
}

/* ══════════════════════════════════════════════════════════════════════
   مؤشرات زخم التعلّم الثلاثة
   ══════════════════════════════════════════════════════════════════════ */

/**
 * لقطة المؤشرات الثلاثة. مشتقّة بالكامل من الصفوف اليومية — لا تُخزَّن
 * أبداً فلا يمكن أن تتعارض مع ما فعله المتعلّم حقاً.
 */
data class MomentumMetrics(
    /** 🔥 عدد الأيام المتتالية النشطة. */
    val dailyStreak: Int,
    val bestStreak: Int,
    /** 🌱 نسبة الالتزام 0..100 على آخر 30 يوماً. */
    val continuityPercent: Int,
    val activeDays30: Int,
    /** ⭐ مستوى الإتقان الإدراكي 0..100. */
    val masteryPercent: Int,
    // ── المكوّنات الخام للمؤشر الثالث (تُعرض في التفصيل) ──
    val masteredWordsRatio: Float,
    val completedLessonsRatio: Float,
    val avgExamScore: Float,
    val continuityRatio: Float,
    /** تصنيف CEFR المقدَّر ونسبة التقدّم داخله. */
    val cefr: String,
    val cefrProgress: Float,
    val nextCefr: String,
) {
    val continuityFraction: Float get() = continuityPercent / 100f
    val masteryFraction: Float get() = masteryPercent / 100f

    /** اسم المستوى العربي المعروض بجانب شريط الإتقان. */
    val masteryTierLabel: String
        get() = when {
            masteryPercent >= 85 -> "المستوى الماسي"
            masteryPercent >= 70 -> "المستوى الذهبي"
            masteryPercent >= 50 -> "المستوى الفضي"
            masteryPercent >= 30 -> "المستوى البرونزي"
            masteryPercent >= 12 -> "مستوى الأساس"
            else -> "بداية الرحلة"
        }

    /** "المستوى الماسي (B2)" — العنوان الكامل تحت الشريط. */
    val masteryHeadline: String get() = "$masteryTierLabel ($cefr)"

    val continuityLabel: String
        get() = when {
            continuityPercent >= 90 -> "التزام استثنائي"
            continuityPercent >= 70 -> "التزام قوي"
            continuityPercent >= 50 -> "التزام جيد"
            continuityPercent >= 25 -> "قابل للتحسين"
            else -> "لنبدأ البناء"
        }

    val streakLabel: String
        get() = when (dailyStreak) {
            0 -> "ابدأ سلسلتك اليوم"
            1 -> "يوم واحد"
            2 -> "يومان متتاليان"
            in 3..10 -> "$dailyStreak أيام متتالية"
            else -> "$dailyStreak يوماً متتالياً"
        }

    /** رسالة الدرع النفسي عند انكسار السلسلة — الرصيد لم يضِع. */
    val shieldMessage: String
        get() = when {
            dailyStreak > 0 -> ""
            continuityPercent >= 70 ->
                "سلسلتك بدأت من جديد، لكن رصيدك الشهري $continuityPercent% — إنجازك محفوظ ولم يضِع"
            continuityPercent >= 40 ->
                "درست $activeDays30 يوماً من آخر 30 — الرصيد باقٍ، أكمل من حيث توقفت"
            activeDays30 > 0 ->
                "كل يوم درسته لا يزال محسوباً لك — ابدأ اليوم بالورد المصغّر"
            else -> "أنجز الورد المصغّر (3 دقائق) لتبدأ سلسلتك"
        }
}

/**
 * محرك المؤشرات. يُضاف كملحق لـ [EnigmaStreakEngine] الموجود.
 *
 * كل الدوال نقيّة و O(n) على صفوف يومية صغيرة (سنة = 365 صفاً)،
 * بلا أي I/O أو مؤقتات — صفر استهلاك بطارية.
 */
object MomentumMetricsEngine {

    /** نافذة رصيد الاستمرارية: 30 يوماً ميلادياً. */
    const val CONTINUITY_WINDOW = 30

    // ── أوزان مستوى الإتقان الإدراكي (المجموع = 1.0) ──
    private const val W_WORDS = 0.40f
    private const val W_LESSONS = 0.30f
    private const val W_EXAMS = 0.20f
    private const val W_CONTINUITY = 0.10f

    /**
     * 🔥 شعلة الحماسة اليومية.
     *
     * عدد الأيام المتتالية النشطة. يُسمح لليوم الحالي أن يكون خاملاً دون كسر
     * السلسلة طالما أن أمس كان نشطاً — حتى يبقى أمام المتعلّم فرصة الدراسة
     * حتى منتصف الليل دون أن يرى عدّاده صفراً ويستسلم.
     */
    fun dailyStreak(rows: Map<Long, DayStat>, todayDay: Long = Telemetry.today()): Int {
        var cursor = if (rows[todayDay]?.isActive == true) todayDay else todayDay - 1
        var n = 0
        while (rows[cursor]?.isActive == true) {
            n++
            cursor--
        }
        return n
    }

    /** أطول سلسلة متتالية في التاريخ كله. */
    fun bestStreak(rows: Map<Long, DayStat>): Int {
        if (rows.isEmpty()) return 0
        val days = rows.filterValues { it.isActive }.keys.sorted()
        var best = 0
        var run = 0
        var prev: Long? = null
        days.forEach { d ->
            run = if (prev != null && d == prev!! + 1L) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }

    /**
     * 🌱 رصيد الاستمرارية.
     *
     * (عدد الأيام النشطة في آخر 30 يوماً / 30) × 100.
     * مؤشر ممتص لصدمة كسر السلسلة: يبقى مرتفعاً ويحفظ معنويات الطالب.
     */
    fun continuityPercent(rows: Map<Long, DayStat>, todayDay: Long = Telemetry.today()): Int {
        val active = activeDaysInWindow(rows, todayDay)
        return ((active / CONTINUITY_WINDOW.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    fun activeDaysInWindow(rows: Map<Long, DayStat>, todayDay: Long = Telemetry.today()): Int =
        (0 until CONTINUITY_WINDOW).count { rows[todayDay - it]?.isActive == true }

    /**
     * ⭐ مستوى الإتقان الإدراكي — المعادلة الموزونة.
     *
     *   Mastery = (MasteredWordsRatio   × 0.4)
     *           + (CompletedLessonsRatio × 0.3)
     *           + (AvgExamScore          × 0.2)
     *           + (ContinuityRatio       × 0.1)
     *
     * كل المدخلات في المجال 0..1، والناتج 0..1.
     */
    fun masteryLevel(
        masteredWordsRatio: Float,
        completedLessonsRatio: Float,
        avgExamScore: Float,
        continuityRatio: Float,
    ): Float = (
        masteredWordsRatio.coerceIn(0f, 1f) * W_WORDS +
            completedLessonsRatio.coerceIn(0f, 1f) * W_LESSONS +
            avgExamScore.coerceIn(0f, 1f) * W_EXAMS +
            continuityRatio.coerceIn(0f, 1f) * W_CONTINUITY
        ).coerceIn(0f, 1f)

    /**
     * يحسب المؤشرات الثلاثة دفعة واحدة من الصفوف اليومية التراكمية.
     *
     * @param rows          الصفوف اليومية (DayStat لكل يوم ميلادي)
     * @param masteredWords الكلمات التي استقرّت في خوارزمية FSRS
     * @param totalWords    حجم القاموس الفعّال
     * @param lessonsDone   الدروس المنجزة
     * @param totalLessons  إجمالي الدروس المتاحة
     * @param examAvg       متوسط درجات الاختبارات (0..100)
     */
    fun computeMetrics(
        rows: Map<Long, DayStat>,
        masteredWords: Int,
        totalWords: Int,
        lessonsDone: Int,
        totalLessons: Int,
        examAvg: Int,
        todayDay: Long = Telemetry.today(),
    ): MomentumMetrics {
        val streak = dailyStreak(rows, todayDay)
        val best = maxOf(bestStreak(rows), streak)

        val active30 = activeDaysInWindow(rows, todayDay)
        val continuityRatio = (active30 / CONTINUITY_WINDOW.toFloat()).coerceIn(0f, 1f)
        val continuityPct = (continuityRatio * 100f).roundToInt().coerceIn(0, 100)

        // نسبة الكلمات المستقرّة في FSRS من إجمالي القاموس.
        val wordsRatio = if (totalWords > 0) {
            (masteredWords.toFloat() / totalWords).coerceIn(0f, 1f)
        } else 0f

        val lessonsRatio = if (totalLessons > 0) {
            (lessonsDone.toFloat() / totalLessons).coerceIn(0f, 1f)
        } else 0f

        val examRatio = (examAvg / 100f).coerceIn(0f, 1f)

        val mastery = masteryLevel(wordsRatio, lessonsRatio, examRatio, continuityRatio)

        val (cefr, cefrProg) = Telemetry.estimatedCefr(masteredWords, lessonsDone, examAvg)

        return MomentumMetrics(
            dailyStreak = streak,
            bestStreak = best,
            continuityPercent = continuityPct,
            activeDays30 = active30,
            masteryPercent = (mastery * 100f).roundToInt().coerceIn(0, 100),
            masteredWordsRatio = wordsRatio,
            completedLessonsRatio = lessonsRatio,
            avgExamScore = examRatio,
            continuityRatio = continuityRatio,
            cefr = cefr,
            cefrProgress = cefrProg,
            nextCefr = Telemetry.nextCefr(cefr),
        )
    }

    /**
     * تقرير HTML مبسّط يُحفظ داخل الصندوق عند فتحه، فلا يتغيّر أبداً لاحقاً.
     * يعمل بالكامل دون إنترنت ويقتبس أرقام المتعلّم الحقيقية.
     */
    fun buildRewardHtml(
        reward: MysteryReward,
        metrics: MomentumMetrics,
        learnerName: String,
        totalWords: Int,
        masteredWords: Int,
    ): String {
        val who = if (learnerName.isBlank()) "يا بطل" else "يا $learnerName"
        val body = when (reward.key) {
            "daily_spark" ->
                "البداية هي أصعب خطوة وقد خطوتها فعلاً. اليوم أثبتّ أن لديك القدرة على " +
                    "<b>البدء</b> — وهي مهارة يفتقدها كثيرون."
            "first_rhythm_3d" ->
                "ثلاثة أيام ليست رقماً بل <b>إيقاع</b>. دماغك بدأ يتوقّع الدراسة في هذا " +
                    "الوقت، وهذا أول دليل حقيقي على تكوّن العادة."
            "glowing_week_7d" ->
                "أسبوع كامل بلا انقطاع! لديك الآن <b>$totalWords كلمة</b> و" +
                    "<b>${metrics.masteryPercent}%</b> إتقان. الأسبوع الأول هو الفلتر الذي " +
                    "يفصل الجادّين عن المتحمّسين مؤقتاً — وقد عبرتَه."
            "gate_month_30d" ->
                "شهر من الالتزام برصيد استمرارية <b>${metrics.continuityPercent}%</b>. " +
                    "مستواك الآن <b>${metrics.cefr}</b> وأنت في طريقك إلى " +
                    "<b>${metrics.nextCefr}</b>. ما بنيته هذا الشهر لن يزول بيوم واحد فائت."
            "transformation_90d" ->
                "تسعون يوماً هي المدة التي يحتاجها العقل لإعادة تشكيل مساراته العصبية. " +
                    "لم تتعلّم لغة فقط، بل <b>غيّرت طريقة عمل ذاكرتك</b>. " +
                    "<b>$masteredWords</b> كلمة راسخة و<b>${metrics.dailyStreak}</b> يوماً متتالياً يشهدان."
            "sovereignty_180d" ->
                "نصف عام. قارن نفسك اليوم بمن كنت في اليوم الأول: من الصفر إلى " +
                    "<b>$totalWords كلمة</b> ومستوى <b>${metrics.cefr}</b>. " +
                    "أنت الآن مؤهّل لأن تكون <b>قدوة لغيرك</b> — وهذه أعلى مراتب التعلّم."
            "transcendence_365d" ->
                "سنة كاملة. ما فعلته يستحق أن يُروى: <b>${metrics.bestStreak}</b> يوماً كأطول " +
                    "سلسلة، ومستوى <b>${metrics.cefr}</b>، و<b>$totalWords</b> كلمة. " +
                    "لم تتعلّم الإنجليزية — بل تعلّمت <b>كيف تُتقن أي شيء تريده</b>."
            else ->
                "أتممت مساراً كاملاً بنجاح. رصيد استمرارك <b>${metrics.continuityPercent}%</b> " +
                    "ومستوى إتقانك <b>${metrics.masteryPercent}%</b> — واصل البناء."
        }
        return buildString {
            append("<h3>$who، ${reward.title}</h3>")
            append("<p>$body</p>")
            append("<ul>")
            append("<li>🔥 السلسلة: <b>${metrics.dailyStreak}</b> يوماً (الأفضل ${metrics.bestStreak})</li>")
            append("<li>🌱 الاستمرارية: <b>${metrics.continuityPercent}%</b> من آخر 30 يوماً</li>")
            append("<li>⭐ الإتقان: <b>${metrics.masteryPercent}%</b> · ${metrics.masteryHeadline}</li>")
            append("</ul>")
            reward.privilegeAr?.let { append("<p><b>الصلاحية الممنوحة:</b> $it</p>") }
        }
    }
}
