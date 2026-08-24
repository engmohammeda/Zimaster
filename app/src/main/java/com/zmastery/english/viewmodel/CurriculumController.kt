package com.zmastery.english.viewmodel

import com.zmastery.english.data.*

/**
 * Controller for curriculum progress computations (course & level completion,
 * coverage, and today's lesson plan). See [ExamsController] for conventions.
 *
 * `LevelStats` (the nested type) and [AppViewModel.levelStats] stay on the view
 * model because the type is public; the pure functions live here.
 */
internal class CurriculumController(internal val vm: AppViewModel) {

    private val courses get() = vm.courses
    private val lessons get() = vm.lessons
    private val lessonsPerDay get() = vm.lessonsPerDay

    val todayPlan: List<PlanItem>
        get() {
            val result = mutableListOf<PlanItem>()
            // Pick next incomplete lesson from up to N distinct courses
            val byCourse = lessons.filter { !it.isCompleted }.groupBy { it.courseId }
            byCourse.entries.take(lessonsPerDay).forEach { (cid, ls) ->
                val course = courses.firstOrNull { it.id == cid } ?: return@forEach
                val lesson = ls.minByOrNull { it.no } ?: return@forEach
                result.add(PlanItem(cid, course.name, lesson.id, lesson.title, course.accent))
            }
            return result
        }

    /** Curriculum size of a course: the syllabus target, never below what exists. */
    fun courseTotal(courseId: Int): Int {
        val course = courses.firstOrNull { it.id == courseId } ?: return 0
        val imported = lessons.count { it.courseId == courseId }
        return maxOf(course.target, imported)
    }

    /** Completed lesson count for a course. */
    fun courseDone(courseId: Int): Int =
        lessons.count { it.courseId == courseId && it.isCompleted }

    /** Imported (available) lesson count for a course. */
    fun courseImported(courseId: Int): Int = lessons.count { it.courseId == courseId }

    fun courseCompletion(courseId: Int): Float {
        val total = courseTotal(courseId)
        if (total <= 0) return 0f
        return (courseDone(courseId).toFloat() / total).coerceIn(0f, 1f)
    }

    fun courseCoverage(courseId: Int): Float {
        val total = courseTotal(courseId)
        if (total <= 0) return 0f
        return (courseImported(courseId).toFloat() / total).coerceIn(0f, 1f)
    }

    fun courseProgress(courseId: Int): Pair<Int, Int> = courseDone(courseId) to courseTotal(courseId)

    /** Overall completion across every level in the curriculum. */
    val overallCompletion: Float
        get() {
            val total = courses.sumOf { courseTotal(it.id) }
            if (total <= 0) return 0f
            val done = courses.sumOf { courseDone(it.id) }
            return (done.toFloat() / total).coerceIn(0f, 1f)
        }

    fun coursesForLevel(levelId: Int) = courses.filter { it.levelId == levelId }
}
