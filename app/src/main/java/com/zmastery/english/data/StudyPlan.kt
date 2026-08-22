package com.zmastery.english.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

// ==========================================================================
//  Study plan — turns a goal ("finish Level 1 in 3 months") into a concrete,
//  dated schedule of daily work, generated from the learner's REAL curriculum.
//
//  Design decisions:
//   • Three parallel tracks so every day mixes skills instead of grinding one
//     course: a CORE track (vocabulary / zero-to-hero), an INPUT track
//     (reading & listening, alternating) and an APPLIED track (grammar,
//     conversation, phonetics, writing).
//   • Days are generated lazily from the plan parameters — nothing is stored
//     per-day, so a 6-month plan costs the same as a 1-week plan.
//   • The dashboard reads "today"; the roadmap reads the whole timeline.
// ==========================================================================

/** How long the learner wants to take. */
enum class PlanDuration(val label: String, val days: Int) {
    ONE_MONTH("شهر واحد", 30),
    TWO_MONTHS("شهران", 60),
    THREE_MONTHS("3 أشهر", 90),
    SIX_MONTHS("6 أشهر", 180),
    ONE_YEAR("سنة كاملة", 365);

    companion object {
        fun from(name: String) = runCatching { valueOf(name) }.getOrDefault(THREE_MONTHS)
    }
}

/** Daily time budget. */
enum class PlanIntensity(val label: String, val minutes: Int, val emoji: String) {
    LIGHT("خفيف", 30, "\uD83C\uDF31"),
    NORMAL("متوازن", 60, "\u26A1"),
    INTENSE("مكثّف", 120, "\uD83D\uDD25");

    companion object {
        fun from(name: String) = runCatching { valueOf(name) }.getOrDefault(NORMAL)
    }
}

/** The three parallel learning tracks a day is built from. */
enum class PlanTrack(val label: String, val emoji: String) {
    CORE("المسار الأساسي", "\uD83C\uDF31"),
    INPUT("القراءة والاستماع", "\uD83D\uDCD6"),
    APPLIED("القواعد والمحادثة", "\uD83D\uDCAC"),
}

/** Persisted plan configuration. */
@Serializable
data class StudyPlanDto(
    val active: Boolean = false,
    val custom: Boolean = false,
    val targetLevel: Int = 1,
    val duration: String = "THREE_MONTHS",
    val intensity: String = "NORMAL",
    val startEpochDay: Long = 0L,
    /** Custom overrides — 0 means "derive automatically". */
    val lessonsPerDay: Int = 0,
    val reviewWordsPerDay: Int = 0,
    val conversationMinutes: Int = 0,
    val includeWeekends: Boolean = true,
)

/** One scheduled task inside a plan day. */
data class PlanTask(
    val track: PlanTrack,
    val courseId: Int,
    val courseName: String,
    val lessonId: Int,
    val lessonTitle: String,
    val accent: Long,
    val done: Boolean,
)

/** A single dated day of the plan. */
data class PlanDay(
    val index: Int,               // 0-based day number
    val epochDay: Long,
    val tasks: List<PlanTask>,
    val reviewWords: Int,
    val conversationMinutes: Int,
    val isRest: Boolean = false,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
    val isToday: Boolean get() = epochDay == LocalDate.now().toEpochDay()
    val isPast: Boolean get() = epochDay < LocalDate.now().toEpochDay()
    val doneCount: Int get() = tasks.count { it.done }
    val progress: Float get() = if (tasks.isEmpty()) if (isRest) 1f else 0f else doneCount.toFloat() / tasks.size
    val isComplete: Boolean get() = tasks.isNotEmpty() && doneCount == tasks.size

    val dayLabel: String
        get() = when (date.dayOfWeek.value) {
            1 -> "الإثنين"; 2 -> "الثلاثاء"; 3 -> "الأربعاء"; 4 -> "الخميس"
            5 -> "الجمعة"; 6 -> "السبت"; else -> "الأحد"
        }
    val dateLabel: String get() = "${date.dayOfMonth}/${date.monthValue}"
}

/** Computed summary of a whole plan. */
data class PlanSummary(
    /** Lessons actually available on the device (what the timeline can schedule). */
    val totalLessons: Int,
    val completedLessons: Int,
    val remainingLessons: Int,
    val days: Int,
    val lessonsPerDay: Float,
    val minutesPerDay: Int,
    val endDate: LocalDate,
    val onTrack: Boolean,
    val expectedByNow: Int,
    val dayIndex: Int,
    /** Full curriculum size of the target level (sum of course targets). */
    val curriculumLessons: Int = 0,
) {
    /**
     * Honest progress through the LEVEL'S CURRICULUM — not through the subset
     * of lessons that happens to be imported. Falls back to the imported count
     * only when no curriculum size is known.
     */
    val progress: Float
        get() {
            val denom = if (curriculumLessons > 0) curriculumLessons else totalLessons
            return if (denom > 0) (completedLessons.toFloat() / denom).coerceIn(0f, 1f) else 0f
        }

    /** Progress measured only against the lessons the learner actually has. */
    val progressOfAvailable: Float
        get() = if (totalLessons > 0) (completedLessons.toFloat() / totalLessons).coerceIn(0f, 1f) else 0f

    /** How much of the curriculum has been imported so far. */
    val coverage: Float
        get() = if (curriculumLessons > 0) (totalLessons.toFloat() / curriculumLessons).coerceIn(0f, 1f) else 0f

    val driftLessons: Int get() = completedLessons - expectedByNow
}

object StudyPlanner {

    /** Which course types feed which track. */
    private fun trackOf(type: CourseType): PlanTrack = when (type) {
        CourseType.VOCABULARY -> PlanTrack.CORE
        CourseType.READING, CourseType.LISTENING -> PlanTrack.INPUT
        else -> PlanTrack.APPLIED
    }

    /**
     * Build the full dated timeline for a plan.
     *
     * @param courses  all courses of the target level
     * @param lessons  every lesson (any course), used to order and mark done
     */
    fun buildTimeline(
        plan: StudyPlanDto,
        courses: List<Course>,
        lessons: List<Lesson>,
        maxDays: Int = 400,
    ): List<PlanDay> {
        val levelCourses = courses.filter { it.levelId == plan.targetLevel }
        if (levelCourses.isEmpty()) return emptyList()

        val duration = PlanDuration.from(plan.duration)
        val intensity = PlanIntensity.from(plan.intensity)
        val start = if (plan.startEpochDay > 0) plan.startEpochDay else LocalDate.now().toEpochDay()

        // Queue of remaining lessons per track, ordered by course then lesson no.
        val queues = PlanTrack.values().associateWith { track ->
            levelCourses
                .filter { trackOf(it.type) == track }
                .sortedBy { it.id }
                .flatMap { c ->
                    lessons.filter { it.courseId == c.id }
                        .sortedBy { it.no }
                        .map { l -> Triple(c, l, l.isCompleted) }
                }
                .toMutableList()
        }.mapValues { it.value.toMutableList() }

        val totalRemaining = queues.values.sumOf { q -> q.count { !it.third } }
        if (totalRemaining == 0 && queues.values.all { it.isEmpty() }) return emptyList()

        val perDay = lessonsPerDay(plan, totalLessons(levelCourses, lessons), duration, intensity)
        val reviewTarget = if (plan.reviewWordsPerDay > 0) plan.reviewWordsPerDay
        else when (intensity) {
            PlanIntensity.LIGHT -> 15
            PlanIntensity.NORMAL -> 25
            PlanIntensity.INTENSE -> 45
        }
        val convoMinutes = if (plan.conversationMinutes > 0) plan.conversationMinutes
        else when (intensity) {
            PlanIntensity.LIGHT -> 3
            PlanIntensity.NORMAL -> 5
            PlanIntensity.INTENSE -> 12
        }

        val days = ArrayList<PlanDay>()
        // Rotate the track order every day so no track is starved.
        val order = PlanTrack.values()
        var rotation = 0
        var dayIdx = 0
        var carry = 0.0

        while (dayIdx < minOf(duration.days, maxDays)) {
            val epoch = start + dayIdx
            val date = LocalDate.ofEpochDay(epoch)
            val isWeekend = date.dayOfWeek.value == 5 // Friday rest when disabled
            if (!plan.includeWeekends && isWeekend) {
                days.add(PlanDay(dayIdx, epoch, emptyList(), 0, 0, isRest = true))
                dayIdx++
                continue
            }

            // How many lessons land on this day (fractional rates accumulate).
            // EPSILON absorbs binary float drift — without it a rate such as
            // 130/90 = 1.4444… loses the final lesson over a long plan.
            carry += perDay + 1e-6
            var slots = carry.toInt()
            carry -= slots
            if (slots <= 0 && queues.values.any { it.isNotEmpty() }) {
                // Guarantee at least review work on a zero-lesson day.
                days.add(PlanDay(dayIdx, epoch, emptyList(), reviewTarget, convoMinutes))
                dayIdx++
                continue
            }

            val tasks = ArrayList<PlanTask>()
            var guard = 0
            while (slots > 0 && guard < 12) {
                guard++
                var placed = false
                for (k in order.indices) {
                    val track = order[(rotation + k) % order.size]
                    val q = queues[track]
                    if (q.isNullOrEmpty()) continue
                    val (course, lesson, done) = q.removeAt(0)
                    tasks.add(
                        PlanTask(track, course.id, course.name, lesson.id, lesson.title, course.accent, done)
                    )
                    placed = true
                    slots--
                    break
                }
                if (!placed) break
            }
            rotation++

            if (tasks.isEmpty() && queues.values.all { it.isEmpty() }) break
            days.add(PlanDay(dayIdx, epoch, tasks, reviewTarget, convoMinutes))
            dayIdx++
        }
        return days
    }

    fun totalLessons(courses: List<Course>, lessons: List<Lesson>): Int {
        val ids = courses.map { it.id }.toSet()
        return lessons.count { it.courseId in ids }
    }

    /** Lessons/day implied by the plan (or the user's explicit override). */
    fun lessonsPerDay(
        plan: StudyPlanDto,
        total: Int,
        duration: PlanDuration = PlanDuration.from(plan.duration),
        intensity: PlanIntensity = PlanIntensity.from(plan.intensity),
    ): Double {
        if (plan.lessonsPerDay > 0) return plan.lessonsPerDay.toDouble()
        if (total <= 0) return when (intensity) {
            PlanIntensity.LIGHT -> 1.0
            PlanIntensity.NORMAL -> 2.0
            PlanIntensity.INTENSE -> 3.0
        }
        return (total.toDouble() / duration.days).coerceAtLeast(0.2)
    }

    fun summarize(
        plan: StudyPlanDto,
        courses: List<Course>,
        lessons: List<Lesson>,
    ): PlanSummary {
        val levelCourses = courses.filter { it.levelId == plan.targetLevel }
        val ids = levelCourses.map { it.id }.toSet()
        val lvl = lessons.filter { it.courseId in ids }
        val total = lvl.size
        val done = lvl.count { it.isCompleted }
        // The syllabus size of the whole level — clamped up by what exists so an
        // over-delivered course can never push progress past 100%.
        val curriculum = levelCourses.sumOf { c ->
            maxOf(c.target, lessons.count { it.courseId == c.id })
        }
        val duration = PlanDuration.from(plan.duration)
        val intensity = PlanIntensity.from(plan.intensity)
        val start = if (plan.startEpochDay > 0) plan.startEpochDay else LocalDate.now().toEpochDay()
        val dayIndex = (LocalDate.now().toEpochDay() - start).toInt().coerceAtLeast(0)
        val rate = lessonsPerDay(plan, total, duration, intensity)
        val expected = ceil(rate * (dayIndex + 1)).toInt().coerceAtMost(total)
        val needDays = if (rate > 0) ceil(total / rate).toInt() else duration.days
        return PlanSummary(
            totalLessons = total,
            completedLessons = done,
            remainingLessons = (total - done).coerceAtLeast(0),
            days = minOf(duration.days, needDays.coerceAtLeast(1)),
            lessonsPerDay = rate.toFloat(),
            minutesPerDay = if (plan.lessonsPerDay > 0) (rate * 22).roundToInt() else intensity.minutes,
            endDate = LocalDate.ofEpochDay(start + minOf(duration.days, needDays).toLong()),
            onTrack = done >= expected,
            expectedByNow = expected,
            dayIndex = dayIndex,
            curriculumLessons = curriculum,
        )
    }

    /** A sensible default plan for the learner's current level. */
    fun defaultPlan(levelId: Int): StudyPlanDto = StudyPlanDto(
        active = true,
        custom = false,
        targetLevel = levelId,
        duration = PlanDuration.THREE_MONTHS.name,
        intensity = PlanIntensity.NORMAL.name,
        startEpochDay = LocalDate.now().toEpochDay(),
    )
}
