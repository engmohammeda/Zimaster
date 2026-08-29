package com.zmastery.english.data

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * الخطة اليومية المتكيّفة.
 *
 * المشكلة القديمة: خمس مهام ثابتة تُطلب من المستخدم في يومه الأول
 * (منها "محادثة واحدة" و"اجتز اختباراً") — مهام غير واقعية لمن لم يضف
 * كلمة واحدة بعد، فتبدو الخطة مستحيلة ويُحبَط المتعلّم.
 *
 * الحل: مولّد خطة يومية يقرأ حالة المتعلّم الحقيقية ثم يبني مهامّ
 *   • ممكنة فعلاً (لا تُطلب مهمة بلا محتوى يخدمها)
 *   • متدرّجة مع التقدّم (الأهداف تكبر كلما كبرت قدرة المتعلّم)
 *   • متغيّرة يومياً (تدوير مصدره رقم اليوم — لا رتابة)
 *   • قليلة العدد في البداية (مهمة أو اثنتان) لتقليل حاجز البدء
 */

/** مرحلة المتعلّم — تحدد سقف الطموح اليومي. */
enum class LearnerTier(
    val label: String,
    val tagline: String,
    /** أقصى عدد مهام يومية في هذه المرحلة. */
    val maxTasks: Int,
) {
    /** لا محتوى بعد — الهدف الوحيد هو التأسيس. */
    SEED("التأسيس", "ابنِ قاموسك الأول", 2),

    /** أول أسبوع — عادة صغيرة تُبنى. */
    SPROUT("الانطلاق", "عادة صغيرة كل يوم", 3),

    /** متعلّم منتظم. */
    GROWING("النمو", "وسّع نطاقك تدريجياً", 4),

    /** متمكّن — خطة كاملة متعددة المهارات. */
    ESTABLISHED("الترسيخ", "خطة متكاملة متعددة المهارات", 5);

    companion object {
        /**
         * يُشتق من المحتوى الفعلي وعمر الاستخدام — لا من رقم اعتباطي.
         *
         * @param words     كلمات القاموس المعتمدة
         * @param lessons   عدد الدروس المتاحة
         * @param doneLessons دروس أُنجزت فعلاً
         * @param activeDays أيام النشاط الحقيقي
         */
        fun of(words: Int, lessons: Int, doneLessons: Int, activeDays: Int): LearnerTier = when {
            words < 5 && lessons == 0 -> SEED
            activeDays < 7 || words < 25 -> SPROUT
            activeDays < 21 || (words < 90 && doneLessons < 8) -> GROWING
            else -> ESTABLISHED
        }
    }
}

/** ما يحتاجه المولّد ليقرّر خطة اليوم. */
data class LearnerSnapshot(
    val epochDay: Long,
    val activeWords: Int,
    val dueWords: Int,
    val newWords: Int,              // كلمات لم تُراجع بعد
    val openLessons: Int,           // دروس غير مكتملة
    val completedLessons: Int,
    val storiesAvailable: Int,
    val canMakeStory: Boolean,      // بذور + مفتاح AI جاهزان
    val hasConversationLesson: Boolean,
    val wordsMissingMnemonic: Int,
    val activeDays: Int,
    val recentAvgReviews: Int,      // متوسط المراجعات في آخر 7 أيام نشطة
    val streak: Int,
) {
    val tier: LearnerTier
        get() = LearnerTier.of(activeWords, openLessons + completedLessons, completedLessons, activeDays)
}

/**
 * قالب مهمة. `target` تُحسب لحظياً من حالة المتعلّم بدل أن تكون رقماً ثابتاً.
 */
private data class TaskBlueprint(
    val id: String,
    val title: (Int) -> String,
    val subtitle: String,
    val icon: String,
    /** null = المهمة غير متاحة اليوم. */
    val target: (LearnerSnapshot) -> Int?,
    /** أولوية الظهور — الأعلى يُختار أولاً. */
    val weight: (LearnerSnapshot) -> Int,
    /** هل تُعتبر مهمة "أساسية" تظهر كل يوم متى توفّرت؟ */
    val core: Boolean = false,
)

object AdaptiveTasks {

    /** سقف مريح لهدف المراجعة اليومي مهما بلغ عدد الكلمات. */
    private const val REVIEW_CEILING = 60

    private val blueprints = listOf(
        // ── مراجعة الكلمات — المهمة الأساسية متى وُجد قاموس ──
        TaskBlueprint(
            id = "review",
            title = { n -> "راجع $n " + plural(n, "كلمة", "كلمات") },
            subtitle = "تثبيت الذاكرة",
            icon = "brain",
            target = { s ->
                if (s.activeWords == 0) null else {
                    // الهدف = المستحق فعلاً، مقيَّداً بقدرة المتعلّم المُثبتة.
                    val capability = when (s.tier) {
                        LearnerTier.SEED -> 5
                        LearnerTier.SPROUT -> 10
                        LearnerTier.GROWING -> 20
                        LearnerTier.ESTABLISHED -> 30
                    }
                    // نتتبّع أداءه الحقيقي: من يراجع 40 يومياً لا نطلب منه 20.
                    val personal = if (s.recentAvgReviews > 0) {
                        ((s.recentAvgReviews * 1.1).toInt()).coerceAtLeast(capability)
                    } else capability
                    val wanted = min(personal, REVIEW_CEILING)
                    // لا نطلب أكثر مما هو متاح فعلاً.
                    val available = max(s.dueWords, min(s.activeWords, 5))
                    min(wanted, available).coerceAtLeast(1)
                }
            },
            weight = { s -> if (s.dueWords > 0) 100 else 60 },
            core = true,
        ),

        // ── إكمال درس ──
        TaskBlueprint(
            id = "lesson",
            title = { n -> if (n == 1) "أكمل درساً واحداً" else "أكمل $n دروس" },
            subtitle = "من خطتك اليومية",
            icon = "book",
            target = { s ->
                if (s.openLessons == 0) null else when (s.tier) {
                    LearnerTier.SEED, LearnerTier.SPROUT -> 1
                    LearnerTier.GROWING -> min(1, s.openLessons)
                    LearnerTier.ESTABLISHED -> min(2, s.openLessons)
                }
            },
            weight = { 95 },
            core = true,
        ),

        // ── بناء القاموس — محورية في البداية ──
        TaskBlueprint(
            id = "addword",
            title = { n -> "أضف $n " + plural(n, "كلمة جديدة", "كلمات جديدة") },
            subtitle = "وسّع قاموسك",
            icon = "add",
            target = { s ->
                when (s.tier) {
                    LearnerTier.SEED -> 3
                    LearnerTier.SPROUT -> 2
                    // في المراحل المتقدمة تظهر أحياناً فقط (تدوير)
                    else -> if (s.activeWords < 300) 2 else null
                }
            },
            weight = { s ->
                when (s.tier) {
                    LearnerTier.SEED -> 120      // أعلى أولوية على الإطلاق
                    LearnerTier.SPROUT -> 70
                    else -> 30
                }
            },
            core = false,
        ),

        // ── قراءة قصة ──
        TaskBlueprint(
            id = "story",
            title = { _ -> "اقرأ قصة اليوم" },
            subtitle = "كلماتك في سياق حقيقي",
            icon = "story",
            target = { s -> if (s.storiesAvailable > 0 || s.canMakeStory) 1 else null },
            weight = { s -> if (s.storiesAvailable > 0) 65 else 40 },
        ),

        // ── اختبار — لا يُطلب قبل وجود مادة كافية ──
        TaskBlueprint(
            id = "quiz",
            title = { _ -> "اجتز اختباراً قصيراً" },
            subtitle = "قِس ما ثبت فعلاً",
            icon = "quiz",
            target = { s ->
                // 12 كلمة حد أدنى منطقي لاختبار ذي معنى
                if (s.activeWords >= 12 && s.tier != LearnerTier.SEED) 1 else null
            },
            weight = { s -> if (s.tier == LearnerTier.ESTABLISHED) 60 else 35 },
        ),

        // ── محادثة — أصعب مهارة، تُطلب متأخراً فقط ──
        TaskBlueprint(
            id = "speak",
            title = { _ -> "تدرّب على محادثة" },
            subtitle = "درّب لسانك على النطق",
            icon = "talk",
            target = { s ->
                if (s.hasConversationLesson && s.tier >= LearnerTier.GROWING) 1 else null
            },
            weight = { s -> if (s.tier == LearnerTier.ESTABLISHED) 55 else 25 },
        ),

        // ── الاستماع ──
        TaskBlueprint(
            id = "listen",
            title = { n -> "استمع لنطق $n " + plural(n, "كلمة", "كلمات") },
            subtitle = "درّب أذنك",
            icon = "ear",
            target = { s ->
                if (s.activeWords < 5) null else when (s.tier) {
                    LearnerTier.SEED -> null
                    LearnerTier.SPROUT -> 5
                    LearnerTier.GROWING -> 8
                    LearnerTier.ESTABLISHED -> 12
                }
            },
            weight = { 45 },
        ),

        // ── الروابط الذهنية ──
        TaskBlueprint(
            id = "mnemonic",
            title = { n -> "اربط $n " + plural(n, "كلمة", "كلمات") + " بصورة ذهنية" },
            subtitle = "الصورة تُثبّت أقوى من التكرار",
            icon = "link",
            target = { s ->
                if (s.wordsMissingMnemonic >= 4 && s.tier >= LearnerTier.GROWING)
                    min(4, s.wordsMissingMnemonic) else null
            },
            weight = { 38 },
        ),
    )

    /**
     * يبني خطة اليوم.
     *
     * الترتيب: المهام الأساسية المتاحة أولاً، ثم تُملأ بقية الفتحات من
     * المهام الاختيارية بتدوير يومي مُبذّر بـ [LearnerSnapshot.epochDay]
     * حتى لا تتكرر نفس التوليفة يوماً بعد يوم.
     */
    fun buildPlan(s: LearnerSnapshot): List<DailyTask> {
        val eligible = blueprints.mapNotNull { bp ->
            val t = bp.target(s) ?: return@mapNotNull null
            if (t <= 0) null else Triple(bp, t, bp.weight(s))
        }
        if (eligible.isEmpty()) return emptyList()

        val core = eligible.filter { it.first.core }
        val optional = eligible.filterNot { it.first.core }

        // تدوير يومي ثابت لكل يوم (نفس اليوم ⇒ نفس الخطة دائماً).
        val rng = Random(s.epochDay * 31 + s.tier.ordinal)
        val shuffledOptional = optional
            .sortedByDescending { it.third + rng.nextInt(0, 25) }

        val slots = s.tier.maxTasks
        val chosen = (core.sortedByDescending { it.third } + shuffledOptional).take(slots)

        return chosen.map { (bp, target, _) ->
            DailyTask(
                id = bp.id,
                title = bp.title(target),
                subtitle = bp.subtitle,
                icon = bp.icon,
                target = target,
                progress = 0,
            )
        }
    }

    /** كل المعرّفات التي قد يولّدها المحرّك — تُستخدم لتصفية التقدّم المحفوظ. */
    val allTaskIds: Set<String> = blueprints.map { it.id }.toSet()

    /** رسالة تحفيزية تشرح لماذا خطة اليوم بهذا الحجم. */
    fun rationale(s: LearnerSnapshot, planSize: Int): String = when {
        planSize == 0 -> "أضف محتوى لتبدأ خطتك — استورد كورساً أو أضف كلمات"
        s.tier == LearnerTier.SEED -> "خطة خفيفة لبناء الأساس — لا نطلب أكثر مما تحتاج"
        s.tier == LearnerTier.SPROUT -> "نبني عادة صغيرة أولاً · تكبر الخطة معك"
        s.tier == LearnerTier.GROWING -> "خطتك تتوسّع تدريجياً مع تقدّمك"
        else -> "خطة متكاملة تناسب مستواك الحالي"
    }

    private fun plural(n: Int, singular: String, plural: String) = if (n == 1) singular else plural
}
