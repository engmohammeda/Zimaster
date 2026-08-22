package com.zmastery.english.data

import kotlinx.serialization.Serializable

// ==========================================================================
//  المرحلة الثانية — نظام Z-Mastery Enigma
//  The Seven Seals: mystery chests unlocked by continuity milestones.
//
//  Mystery is the primary driver of anticipation. The learner never knows the
//  exact contents of a sealed chest, which converts a boring "day counter" into
//  an unfolding discovery. Each chest carries THREE reward layers:
//
//    1. A personalised AI wisdom card that addresses the learner by name.
//    2. A functional perk (XP multiplier, streak freeze, theme, unlocked zone).
//    3. A narrative badge that marks the milestone permanently.
// ==========================================================================

/** Rarity tier — drives colour, glow and the ceremony of the opening. */
enum class ChestRarity(
    val label: String,
    val colorArgb: Long,
    val glowArgb: Long,
) {
    COMMON("عادي", 0xFF9CA3AF, 0xFFD1D5DB),
    UNCOMMON("غير شائع", 0xFF3B82F6, 0xFF93C5FD),
    RARE("نادر", 0xFF8B5CF6, 0xFFC4B5FD),
    EPIC("أسطوري", 0xFFD9A44E, 0xFFFCD34D),
    LEGENDARY("خرافي", 0xFFDC2626, 0xFFFCA5A5),
    MYSTICAL("غامض", 0xFF10B981, 0xFF6EE7B7),
}

/** The functional perk hidden inside a chest. */
enum class PerkKind {
    XP_BONUS,        // one-off XP grant
    XP_MULTIPLIER,   // timed 2x XP window
    STREAK_FREEZE,   // a shield that absorbs one missed day
    THEME,           // unlocks a UI theme
    UNLOCK_ZONE,     // opens a hidden challenge area
    AI_REPORT,       // generates a deep AI reflection
    VOICE,           // unlocks a premium coach voice
    SPONSOR,         // gift a streak freeze to a beginner
    LEGACY,          // the yearly time capsule / CV page
}

/** A single reward line revealed when the chest opens. */
data class ChestReward(
    val kind: PerkKind,
    val title: String,
    val detail: String,
    val emoji: String,
    /** Numeric payload: XP amount, minutes, count… */
    val amount: Int = 0,
)

/**
 * One of the Seven Seals.
 *
 * @param day  streak day at which the seal breaks
 */
data class ChestTier(
    val id: String,
    val day: Int,
    val name: String,
    val rarity: ChestRarity,
    /** Arabic description of the on-screen animation. */
    val visualNote: String,
    val badge: String,
    val rewards: List<ChestReward>,
) {
    val isLocked: Boolean get() = false
}

object SevenSeals {

    val tiers: List<ChestTier> = listOf(
        // ── 1 day ──────────────────────────────────────────────────────
        ChestTier(
            id = "seal_spark", day = 1,
            name = "صندوق الشرارة اليومية",
            rarity = ChestRarity.COMMON,
            visualNote = "شرارة كهربائية صغيرة تنطلق عند حل الورد اليومي",
            badge = "الشرارة الأولى",
            rewards = listOf(
                ChestReward(PerkKind.AI_REPORT, "بطاقة حكمة مخصّصة", "رسالة دافئة تناديك باسمك", "\uD83D\uDCDC"),
                ChestReward(PerkKind.XP_BONUS, "نقاط انطلاق", "+25 XP تضمن صعودك الأول", "\u2728", 25),
            ),
        ),
        // ── 3 days ─────────────────────────────────────────────────────
        ChestTier(
            id = "seal_rhythm", day = 3,
            name = "صندوق أول إيقاع",
            rarity = ChestRarity.UNCOMMON,
            visualNote = "اهتزاز خفيف للصندوق مع هالة زرقاء تشع من جوانبه",
            badge = "نغمة الإصرار الأولى",
            rewards = listOf(
                ChestReward(PerkKind.XP_MULTIPLIER, "مضاعف XP ×2", "لمدة 30 دقيقة — استغلّها الآن", "\u26A1", 30),
                ChestReward(PerkKind.XP_BONUS, "مكافأة الإيقاع", "+50 XP", "\uD83C\uDFB5", 50),
            ),
        ),
        // ── 7 days ─────────────────────────────────────────────────────
        ChestTier(
            id = "seal_week", day = 7,
            name = "صندوق الأسبوع المتوهج",
            rarity = ChestRarity.RARE,
            visualNote = "اشتعال لهب متوهج خفيف مع أصوات انتصار مميّزة",
            badge = "أسبوع بلا انقطاع",
            rewards = listOf(
                ChestReward(PerkKind.STREAK_FREEZE, "درع تجميد السلسلة", "يحمي سلسلتك من يوم واحد فائت", "\uD83D\uDEE1\uFE0F", 1),
                ChestReward(PerkKind.AI_REPORT, "تقرير النمط الإدراكي", "تحليل أسبوعي لطريقة تعلّمك", "\uD83E\uDDE0"),
                ChestReward(PerkKind.XP_BONUS, "مكافأة الأسبوع", "+100 XP", "\uD83D\uDD25", 100),
            ),
        ),
        // ── 30 days ────────────────────────────────────────────────────
        ChestTier(
            id = "seal_month", day = 30,
            name = "صندوق بوابة الشهر",
            rarity = ChestRarity.EPIC,
            visualNote = "ختم شمعي أحمر يذوب برفق عند النقر مع إشعاع ذهبي غامر",
            badge = "بوابة الشهر",
            rewards = listOf(
                ChestReward(PerkKind.THEME, "السمة البرونزية الملكية", "مظهر جديد للواجهة", "\uD83C\uDFC6"),
                ChestReward(PerkKind.UNLOCK_ZONE, "المنطقة السرية", "تحديات صعبة للمتقدّمين فقط", "\uD83C\uDF11"),
                ChestReward(PerkKind.AI_REPORT, "مرآة العقل الشهرية", "تقرير عميق عن شهر كامل", "\uD83E\uDE9E"),
                ChestReward(PerkKind.STREAK_FREEZE, "درعان إضافيان", "حماية مزدوجة", "\uD83D\uDEE1\uFE0F", 2),
            ),
        ),
        // ── 90 days ────────────────────────────────────────────────────
        ChestTier(
            id = "seal_transform", day = 90,
            name = "صندوق رحلة التحول",
            rarity = ChestRarity.LEGENDARY,
            visualNote = "صندوق معدني ضخم تدور حوله تروس وأقفال ميكانيكية مع وهج أحمر ناري",
            badge = "رحلة التحوّل",
            rewards = listOf(
                ChestReward(PerkKind.VOICE, "النبرة الصوتية الفخمة", "صوت معاشر AI مميّز", "\uD83C\uDFA9"),
                ChestReward(PerkKind.THEME, "سمة الذهب الملكي", "Royal Gold", "\uD83D\uDC51"),
                ChestReward(PerkKind.UNLOCK_ZONE, "مجلس النخبة", "ترشّح للتحدث الصوتي", "\uD83C\uDFDB\uFE0F"),
                ChestReward(PerkKind.XP_BONUS, "مكافأة التحوّل", "+500 XP", "\uD83D\uDCA5", 500),
            ),
        ),
        // ── 180 days ───────────────────────────────────────────────────
        ChestTier(
            id = "seal_sovereign", day = 180,
            name = "صندوق السيادة الملكي",
            rarity = ChestRarity.LEGENDARY,
            visualNote = "الصندوق يرتفع عن الشاشة وتلتف حوله أوراق الغار مع أصوات احتفالية مهيبة",
            badge = "سيادة نصف عام",
            rewards = listOf(
                ChestReward(PerkKind.SPONSOR, "رعاية صديق متعثّر", "أهدِ حماية سلسلة لمبتدئ", "\uD83E\uDD1D", 1),
                ChestReward(PerkKind.AI_REPORT, "اليوم 1 مقابل اليوم 180", "تحليل مقارن بصوت المعاشر", "\uD83D\uDCC8"),
                ChestReward(PerkKind.XP_BONUS, "مكافأة السيادة", "+1000 XP", "\uD83D\uDC51", 1000),
            ),
        ),
        // ── 365 days ───────────────────────────────────────────────────
        ChestTier(
            id = "seal_eternal", day = 365,
            name = "صندوق الخلود السنوي",
            rarity = ChestRarity.MYSTICAL,
            visualNote = "كبسولة زمنية فضية تشع بضوء أبيض مبهر من داخل ثقب أسود",
            badge = "الأستاذ المتوهّج",
            rewards = listOf(
                ChestReward(PerkKind.LEGACY, "سيرتك اللغوية الفخرية", "صفحة تفاعلية تعرض مسيرتك السنوية", "\uD83D\uDCC4"),
                ChestReward(PerkKind.THEME, "أيقونة الأستاذ المتوهّج", "شعار خالد لا يُنسى", "\uD83C\uDF1F"),
                ChestReward(PerkKind.XP_BONUS, "مكافأة الخلود", "+3650 XP", "\u267E\uFE0F", 3650),
            ),
        ),
    )

    fun byId(id: String): ChestTier? = tiers.firstOrNull { it.id == id }

    /** Tiers whose day requirement is met by [streak]. */
    fun earned(streak: Int): List<ChestTier> = tiers.filter { streak >= it.day }

    /** The next seal the learner is working toward, or null when all are done. */
    fun next(streak: Int): ChestTier? = tiers.firstOrNull { streak < it.day }

    /** Progress 0..1 from the previous seal toward [next]. */
    fun progressToNext(streak: Int): Float {
        val nxt = next(streak) ?: return 1f
        val prevDay = tiers.filter { it.day <= streak }.maxOfOrNull { it.day } ?: 0
        val span = (nxt.day - prevDay).coerceAtLeast(1)
        return ((streak - prevDay).toFloat() / span).coerceIn(0f, 1f)
    }
}

/** Persisted record of a chest the learner has actually opened. */
@Serializable
data class ChestRecord(
    val tierId: String,
    val openedEpochDay: Long,
    val streakAtOpen: Int,
    /** The AI wisdom text revealed (cached so it never changes on reopen). */
    val wisdom: String = "",
)

/** Persisted perk wallet — what the learner currently owns. */
@Serializable
data class PerkWallet(
    val streakFreezes: Int = 0,
    /** Epoch-millis when the active XP multiplier expires (0 = none). */
    val xpMultiplierUntil: Long = 0L,
    val themesUnlocked: List<String> = emptyList(),
    val zonesUnlocked: List<String> = emptyList(),
    val voicesUnlocked: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val sponsorGifts: Int = 0,
    val legacyUnlocked: Boolean = false,
) {
    fun multiplierActive(now: Long = System.currentTimeMillis()): Boolean = xpMultiplierUntil > now

    /**
     * يمنح دروعاً مع احترام السقف الأقصى (3 دروع) المنصوص عليه في التصميم.
     * الدرع الزائد لا يُخزَّن — فلا يمكن تكديس حماية لا نهائية.
     */
    fun grantFreezes(amount: Int): PerkWallet =
        copy(streakFreezes = (streakFreezes + amount).coerceIn(0, MAX_STREAK_FREEZES))

    /** يستهلك درعاً واحداً إن وُجد. */
    fun spendFreeze(): PerkWallet =
        copy(streakFreezes = (streakFreezes - 1).coerceAtLeast(0))

    val freezesFull: Boolean get() = streakFreezes >= MAX_STREAK_FREEZES

    companion object {
        /** السقف الأقصى لدروع تجميد السلسلة. */
        const val MAX_STREAK_FREEZES = 3
    }

    fun multiplierMinutesLeft(now: Long = System.currentTimeMillis()): Int =
        if (xpMultiplierUntil <= now) 0
        else (((xpMultiplierUntil - now) / 60_000L) + 1).toInt()
}

/**
 * Builds the personalised AI wisdom card. Works fully offline — the learner's
 * own numbers make it feel bespoke without needing a network call.
 */
object WisdomCards {

    private val openers = listOf(
        "يا {name}، ", "{name}، ", "اسمع يا {name}، ",
    )

    fun forTier(tier: ChestTier, name: String, m: Momentum3D, totalWords: Int): String {
        val who = if (name.isBlank()) "يا صديقي" else "يا $name"
        return when (tier.id) {
            "seal_spark" ->
                "$who، البداية هي أصعب خطوة وقد خطوتها بالفعل. " +
                    "اليوم أثبتت أن لديك القدرة على البدء — وهذه مهارة يفتقدها كثيرون."
            "seal_rhythm" ->
                "$who، ثلاثة أيام ليست رقماً بل إيقاع. " +
                    "دماغك بدأ يتوقّع الدراسة في هذا الوقت، وهذا أول دليل على تكوّن العادة."
            "seal_week" ->
                "$who، أسبوع كامل! لديك الآن $totalWords كلمة و${m.masteryPct}% إتقان. " +
                    "الأسبوع الأول هو الفلتر الذي يفصل الجادّين عن المتحمّسين مؤقتاً — وقد عبرته."
            "seal_month" ->
                "$who، شهر من الالتزام برصيد استمرارية ${m.continuityPct}%. " +
                    "مستواك الآن ${m.cefr} وأنت في طريقك إلى ${m.nextCefr}. " +
                    "ما بنيته هذا الشهر لن يزول بيوم واحد فائت — تذكّر ذلك دائماً."
            "seal_transform" ->
                "$who، تسعون يوماً هي المدة التي يحتاجها العقل لإعادة تشكيل مساراته العصبية. " +
                    "أنت لم تتعلّم لغة فقط، بل غيّرت طريقة عمل ذاكرتك. " +
                    "${m.masteryPct}% إتقان و${m.streak} يوم متتالٍ يشهدان على ذلك."
            "seal_sovereign" ->
                "$who، نصف عام. قارن نفسك اليوم بمن كنت في اليوم الأول: " +
                    "من $totalWords كلمة إلى مستوى ${m.cefr}. " +
                    "أنت الآن مؤهّل لأن تكون قدوة لغيرك — وهذه أعلى مراتب التعلّم."
            "seal_eternal" ->
                "$who، سنة كاملة. ما فعلته يستحق أن يُروى: " +
                    "${m.bestStreak} يوماً كأطول سلسلة، ومستوى ${m.cefr}، و$totalWords كلمة. " +
                    "لم تتعلّم الإنجليزية — بل تعلّمت كيف تُتقن أي شيء تريده."
            else ->
                "$who، كل يوم تدرس فيه هو استثمار في نسختك القادمة. واصل."
        }
    }
}
