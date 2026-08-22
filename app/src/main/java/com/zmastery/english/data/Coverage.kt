package com.zmastery.english.data

import kotlin.math.roundToInt

// ==========================================================================
//  Curriculum Coverage — analytics computed ONLY from the learner's own
//  imported courses. Nothing here is random or generic: every number traces
//  back to a lesson the learner actually imported and studied.
//
//  This is what turns the app from "a random English trainer" into "a tracker
//  for MY course": it measures how much of MY curriculum I have consumed,
//  per skill, per course, and what concrete items remain.
// ==========================================================================

/** Coverage of one course: lessons done vs total, plus its content inventory. */
data class CourseCoverage(
    val courseId: Int,
    val name: String,
    val type: CourseType,
    val levelId: Int,
    val accent: Long,
    val lessonsTotal: Int,
    val lessonsDone: Int,
    val wordsTotal: Int,
    val wordsMastered: Int,
    /** Content items counted across the course's lessons. */
    val dialogueLines: Int = 0,
    val grammarPoints: Int = 0,
    val readingSegments: Int = 0,
    val keyExpressions: Int = 0,
    val keySentences: Int = 0,
    val quizItems: Int = 0,
) {
    val progress: Float get() = if (lessonsTotal == 0) 0f else lessonsDone.toFloat() / lessonsTotal
    val pct: Int get() = (progress * 100).roundToInt()
    val isComplete: Boolean get() = lessonsTotal > 0 && lessonsDone >= lessonsTotal
    val remaining: Int get() = (lessonsTotal - lessonsDone).coerceAtLeast(0)
    val wordMasteryPct: Int
        get() = if (wordsTotal == 0) 0 else (wordsMastered * 100.0 / wordsTotal).roundToInt()
}

/** Coverage rolled up per skill (course type). */
data class SkillCoverage(
    val type: CourseType,
    val courses: Int,
    val lessonsTotal: Int,
    val lessonsDone: Int,
    val items: Int,
    val itemLabel: String,
) {
    val progress: Float get() = if (lessonsTotal == 0) 0f else lessonsDone.toFloat() / lessonsTotal
    val pct: Int get() = (progress * 100).roundToInt()
    val hasContent: Boolean get() = lessonsTotal > 0
}

/**
 * The learner's whole curriculum, measured.
 */
data class CurriculumReport(
    val courses: List<CourseCoverage>,
    val skills: List<SkillCoverage>,
) {
    val lessonsTotal: Int get() = courses.sumOf { it.lessonsTotal }
    val lessonsDone: Int get() = courses.sumOf { it.lessonsDone }
    val coursesWithContent: List<CourseCoverage> get() = courses.filter { it.lessonsTotal > 0 }
    val coursesCompleted: Int get() = courses.count { it.isComplete }

    val overallProgress: Float
        get() = if (lessonsTotal == 0) 0f else lessonsDone.toFloat() / lessonsTotal
    val overallPct: Int get() = (overallProgress * 100).roundToInt()

    // ---- content inventory totals (things the learner has actually studied) ----
    val dialogueLinesStudied: Int get() = courses.sumOf { it.dialogueLines }
    val grammarPointsStudied: Int get() = courses.sumOf { it.grammarPoints }
    val readingSegmentsStudied: Int get() = courses.sumOf { it.readingSegments }
    val expressionsStudied: Int get() = courses.sumOf { it.keyExpressions }
    val sentencesStudied: Int get() = courses.sumOf { it.keySentences }
    val quizItemsAvailable: Int get() = courses.sumOf { it.quizItems }

    /** The course to push next: in-progress first, else the first untouched. */
    val nextCourse: CourseCoverage?
        get() = coursesWithContent
            .filter { !it.isComplete }
            .sortedWith(
                compareByDescending<CourseCoverage> { it.lessonsDone > 0 }
                    .thenBy { it.levelId }
                    .thenBy { it.courseId },
            )
            .firstOrNull()

    /** Weakest skill that actually has content — the coach's focus candidate. */
    val weakestSkill: SkillCoverage?
        get() = skills.filter { it.hasContent && it.lessonsDone < it.lessonsTotal }
            .minByOrNull { it.progress }
}

object Coverage {

    /**
     * Build the full report from live state.
     *
     * Content items are only counted for COMPLETED lessons — the number then
     * means "how much have I actually studied", not "how much exists".
     */
    fun report(
        courses: List<Course>,
        lessons: List<Lesson>,
        vocab: List<VocabWord>,
    ): CurriculumReport {
        val perCourse = courses.map { c ->
            val courseLessons = lessons.filter { it.courseId == c.id }
            val done = courseLessons.filter { it.isCompleted }
            val courseWords = vocab.filter { it.courseId == c.id && !it.pendingApproval }
            CourseCoverage(
                courseId = c.id,
                name = c.name,
                type = c.type,
                levelId = c.levelId,
                accent = c.accent,
                lessonsTotal = courseLessons.size,
                lessonsDone = done.size,
                wordsTotal = courseWords.size,
                wordsMastered = courseWords.count { it.mastered },
                dialogueLines = done.sumOf { it.dialogues.size },
                grammarPoints = done.sumOf {
                    // A grammar lesson's teaching load = its key points + examples.
                    it.keyPoints.size + it.examples.size
                },
                readingSegments = done.sumOf {
                    if (it.segments.isNotEmpty()) it.segments.size
                    else if (it.fullTextEn.isNotBlank() || it.readingEn.isNotBlank()) 1 else 0
                },
                keyExpressions = done.sumOf { it.keyExpressions.size },
                keySentences = done.sumOf { it.keySentences.size },
                quizItems = courseLessons.sumOf { it.quiz.size },
            )
        }

        val perSkill = CourseType.values().map { t ->
            val group = perCourse.filter { it.type == t }
            val items = when (t) {
                CourseType.CONVERSATION -> group.sumOf { it.dialogueLines }
                CourseType.GRAMMAR -> group.sumOf { it.grammarPoints }
                CourseType.READING, CourseType.LISTENING -> group.sumOf { it.readingSegments }
                CourseType.VOCABULARY -> group.sumOf { it.wordsTotal }
                CourseType.PHONETICS -> group.sumOf { it.lessonsDone }
                CourseType.WRITING -> group.sumOf { it.keySentences }
            }
            SkillCoverage(
                type = t,
                courses = group.count { it.lessonsTotal > 0 },
                lessonsTotal = group.sumOf { it.lessonsTotal },
                lessonsDone = group.sumOf { it.lessonsDone },
                items = items,
                itemLabel = itemLabelFor(t),
            )
        }

        return CurriculumReport(perCourse, perSkill)
    }

    fun itemLabelFor(t: CourseType): String = when (t) {
        CourseType.VOCABULARY -> "كلمة"
        CourseType.GRAMMAR -> "قاعدة"
        CourseType.READING -> "مقطع مقروء"
        CourseType.LISTENING -> "مقطع مسموع"
        CourseType.CONVERSATION -> "سطر حوار"
        CourseType.PHONETICS -> "درس صوتي"
        CourseType.WRITING -> "جملة مكتوبة"
    }
}
