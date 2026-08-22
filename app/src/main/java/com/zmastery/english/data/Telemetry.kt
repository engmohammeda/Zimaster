package com.zmastery.english.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

// ==========================================================================
//  Telemetry — the raw learning record the coach reasons over.
//
//  Everything the learner does is folded into ONE row per calendar day
//  (DayStat). Rows are append-only and tiny, so a full year of history costs
//  a few kilobytes and can be charted instantly without any database.
// ==========================================================================

/** Every trackable activity kind. */
enum class ActivityKind(val label: String, val emoji: String) {
    REVIEW("مراجعة كلمات", "\uD83D\uDD01"),
    LESSON("درس", "\uD83D\uDCD8"),
    EXAM("اختبار", "\uD83D\uDCDD"),
    STORY("قصة", "\uD83D\uDCD6"),
    CONVERSATION("محادثة", "\uD83D\uDCAC"),
    LISTENING("استماع", "\uD83D\uDD0A"),
    PHONETICS("صوتيات", "\uD83D\uDDE3\uFE0F"),
    WORD_ADD("إضافة كلمة", "\u2795"),
    MNEMONIC("رابط ذهني", "\uD83D\uDD17"),
}

/**
 * One calendar day of learning. All counters are cumulative for that day.
 */
@Serializable
data class DayStat(
    val epochDay: Long,
    var reviews: Int = 0,            // review events
    var reviewsCorrect: Int = 0,     // graded >= 2
    var wordsAdded: Int = 0,
    var wordsMastered: Int = 0,
    var lessonsCompleted: Int = 0,
    var examsTaken: Int = 0,
    var examScoreSum: Int = 0,       // sum of percentages (for averaging)
    var mistakes: Int = 0,           // wrong exam answers + failed recalls
    var studySeconds: Long = 0L,     // total focused time
    var listenSeconds: Long = 0L,    // TTS playback time
    var conversationTurns: Int = 0,
    var storiesRead: Int = 0,
    var phoneticsDrills: Int = 0,
    var mnemonicsMade: Int = 0,
    var xpEarned: Int = 0,
) {
    val studyMinutes: Int get() = (studySeconds / 60).toInt()
    val examAvg: Int get() = if (examsTaken > 0) examScoreSum / examsTaken else 0
    val recallRate: Float get() = if (reviews > 0) reviewsCorrect.toFloat() / reviews else 0f

    /** Activity intensity 0..4 — drives the GitHub-style heatmap colour. */
    val intensity: Int
        get() {
            val score = reviews + lessonsCompleted * 5 + examsTaken * 6 +
                studyMinutes / 5 + storiesRead * 3 + wordsAdded
            return when {
                score <= 0 -> 0
                score < 8 -> 1
                score < 20 -> 2
                score < 40 -> 3
                else -> 4
            }
        }

    val isActive: Boolean get() = intensity > 0

    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val label: String get() = date.format(DateTimeFormatter.ofPattern("MM/dd"))
}

/** Aggregate of a span of days — used for every tab and the coach prompt. */
data class StatSpan(
    val days: List<DayStat>,
    val title: String,
) {
    val reviews: Int get() = days.sumOf { it.reviews }
    val reviewsCorrect: Int get() = days.sumOf { it.reviewsCorrect }
    val wordsAdded: Int get() = days.sumOf { it.wordsAdded }
    val wordsMastered: Int get() = days.sumOf { it.wordsMastered }
    val lessons: Int get() = days.sumOf { it.lessonsCompleted }
    val exams: Int get() = days.sumOf { it.examsTaken }
    val mistakes: Int get() = days.sumOf { it.mistakes }
    val studyMinutes: Int get() = days.sumOf { it.studyMinutes }
    val listenMinutes: Int get() = days.sumOf { (it.listenSeconds / 60).toInt() }
    val conversationTurns: Int get() = days.sumOf { it.conversationTurns }
    val stories: Int get() = days.sumOf { it.storiesRead }
    val phonetics: Int get() = days.sumOf { it.phoneticsDrills }
    val mnemonics: Int get() = days.sumOf { it.mnemonicsMade }
    val xp: Int get() = days.sumOf { it.xpEarned }

    val activeDays: Int get() = days.count { it.isActive }
    val recallRate: Float get() = if (reviews > 0) reviewsCorrect.toFloat() / reviews else 0f
    val examAvg: Int
        get() {
            val taken = days.filter { it.examsTaken > 0 }
            val n = taken.sumOf { it.examsTaken }
            return if (n > 0) taken.sumOf { it.examScoreSum } / n else 0
        }
    val avgMinutesPerActiveDay: Int
        get() = if (activeDays > 0) studyMinutes / activeDays else 0

    /** Percentage change of [selector] vs an earlier span. */
    fun deltaPct(previous: StatSpan, selector: (StatSpan) -> Int): Int {
        val now = selector(this)
        val before = selector(previous)
        if (before == 0) return if (now > 0) 100 else 0
        return ((now - before) * 100.0 / before).roundToInt()
    }
}

/** Rolls raw day rows into spans and derived insights. */
object Telemetry {

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Last [n] days ending today, filling gaps with empty rows. */
    fun span(rows: Map<Long, DayStat>, n: Int, title: String, endDay: Long = today()): StatSpan {
        val list = (0 until n).map { off ->
            val d = endDay - (n - 1 - off)
            rows[d] ?: DayStat(d)
        }
        return StatSpan(list, title)
    }

    /** The span immediately BEFORE the last [n] days (for delta comparisons). */
    fun previousSpan(rows: Map<Long, DayStat>, n: Int, endDay: Long = today()): StatSpan =
        span(rows, n, "السابق", endDay - n)

    /** Longest run of consecutive active days ending today (or yesterday). */
    fun currentStreak(rows: Map<Long, DayStat>): Int {
        val t = today()
        // Allow "today not yet started" without breaking the streak.
        var cursor = if (rows[t]?.isActive == true) t else t - 1
        var n = 0
        while (rows[cursor]?.isActive == true) { n++; cursor-- }
        return n
    }

    fun bestStreak(rows: Map<Long, DayStat>): Int {
        if (rows.isEmpty()) return 0
        val days = rows.filterValues { it.isActive }.keys.sorted()
        var best = 0; var run = 0; var prev: Long? = null
        days.forEach { d ->
            run = if (prev != null && d == prev!! + 1) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }

    /** Estimated CEFR level from mastered words + lessons + exam accuracy. */
    fun estimatedCefr(masteredWords: Int, lessons: Int, examAvg: Int): Pair<String, Float> {
        // Word count dominates; lessons and accuracy nudge it.
        val score = masteredWords + lessons * 6 + examAvg / 2
        return when {
            score < 120 -> "A1" to (score / 120f).coerceIn(0f, 1f)
            score < 400 -> "A2" to ((score - 120) / 280f).coerceIn(0f, 1f)
            score < 900 -> "B1" to ((score - 400) / 500f).coerceIn(0f, 1f)
            score < 1800 -> "B2" to ((score - 900) / 900f).coerceIn(0f, 1f)
            score < 3200 -> "C1" to ((score - 1800) / 1400f).coerceIn(0f, 1f)
            else -> "C2" to 1f
        }
    }

    /** The next CEFR band after [cefr]. */
    fun nextCefr(cefr: String): String = when (cefr) {
        "A1" -> "A2"; "A2" -> "B1"; "B1" -> "B2"; "B2" -> "C1"; else -> "C2"
    }

    /** Best study hour-of-day from the review log (0..23), or null. */
    fun peakHour(hours: List<Int>): Int? =
        hours.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** Human "منذ س" label. */
    fun agoLabel(epochDay: Long): String {
        val diff = (today() - epochDay).toInt()
        return when {
            diff <= 0 -> "اليوم"
            diff == 1 -> "أمس"
            diff < 7 -> "قبل $diff أيام"
            diff < 30 -> "قبل ${diff / 7} أسابيع"
            else -> "قبل ${diff / 30} أشهر"
        }
    }

    fun formatMinutes(min: Int): String = when {
        min < 60 -> "$min د"
        min % 60 == 0 -> "${min / 60} س"
        else -> "${min / 60}س ${min % 60}د"
    }

    fun arrow(delta: Int): String = when {
        delta > 0 -> "▲ $delta%"
        delta < 0 -> "▼ ${abs(delta)}%"
        else -> "— 0%"
    }
}

// ==========================================================================
//  Skill radar — six competencies scored 0..1 from real activity.
// ==========================================================================

data class SkillScore(val label: String, val emoji: String, val value: Float, val detail: String)

object SkillRadar {
    fun compute(
        totalWords: Int,
        masteredWords: Int,
        lessonsDone: Int,
        listenMinutes: Int,
        storiesRead: Int,
        conversationTurns: Int,
        phoneticsDrills: Int,
        grammarCorrect: Float,
    ): List<SkillScore> = listOf(
        SkillScore(
            "المفردات", "\uD83D\uDCD8",
            (masteredWords / 300f).coerceIn(0f, 1f),
            "$masteredWords كلمة محفوظة من $totalWords",
        ),
        SkillScore(
            "القواعد", "\uD83D\uDCD0",
            grammarCorrect.coerceIn(0f, 1f),
            "${(grammarCorrect * 100).toInt()}% دقة في أسئلة القواعد",
        ),
        SkillScore(
            "الاستماع", "\uD83D\uDD0A",
            (listenMinutes / 600f).coerceIn(0f, 1f),
            "${Telemetry.formatMinutes(listenMinutes)} استماع",
        ),
        SkillScore(
            "القراءة", "\uD83D\uDCD6",
            (storiesRead / 40f).coerceIn(0f, 1f),
            "$storiesRead قطعة مقروءة",
        ),
        SkillScore(
            "المحادثة", "\uD83D\uDCAC",
            (conversationTurns / 120f).coerceIn(0f, 1f),
            "$conversationTurns جولة محادثة",
        ),
        SkillScore(
            "النطق", "\uD83D\uDDE3\uFE0F",
            (phoneticsDrills / 60f).coerceIn(0f, 1f),
            "$phoneticsDrills تدريب صوتي",
        ),
    )
}
