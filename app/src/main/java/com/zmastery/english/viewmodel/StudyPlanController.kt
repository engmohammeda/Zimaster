package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlin.math.roundToInt

/**
 * Controller for the study plan / roadmap. The persisted `studyPlan` value stays
 * on [AppViewModel]; this class holds the derived plan views and the save/reset
 * actions. See [ExamsController] for conventions.
 */
internal class StudyPlanController(internal val vm: AppViewModel) {

    private var studyPlan
        get() = vm.studyPlan
        set(v) { vm.studyPlan = v }
    private val courses get() = vm.courses
    private val lessons get() = vm.lessons
    private var lessonsPerDay
        get() = vm.lessonsPerDay
        set(v) { vm.lessonsPerDay = v }

    /** Highest level that has any lessons — the natural default target. */
    private fun defaultTargetLevel(): Int =
        courses.filter { c -> lessons.any { it.courseId == c.id } }
            .minOfOrNull { it.levelId } ?: 1

    /** The effective plan (auto-creates a sensible default when inactive). */
    val effectivePlan: StudyPlanDto
        get() = if (studyPlan.active) studyPlan else StudyPlanner.defaultPlan(defaultTargetLevel())

    val planSummary: PlanSummary
        get() = StudyPlanner.summarize(effectivePlan, courses, lessons)

    val planTimeline: List<PlanDay>
        get() = StudyPlanner.buildTimeline(effectivePlan, courses, lessons)

    /** Today's scheduled day inside the plan (null when the plan ended). */
    val planToday: PlanDay?
        get() {
            val t = Telemetry.today()
            return planTimeline.firstOrNull { it.epochDay == t }
        }

    fun savePlan(plan: StudyPlanDto) {
        studyPlan = plan.copy(
            active = true,
            startEpochDay = if (plan.startEpochDay > 0) plan.startEpochDay else Telemetry.today(),
        )
        // Keep the dashboard's simple counter in sync with the plan.
        val rate = StudyPlanner.lessonsPerDay(
            studyPlan,
            StudyPlanner.totalLessons(courses.filter { it.levelId == studyPlan.targetLevel }, lessons),
        )
        lessonsPerDay = rate.roundToInt().coerceIn(1, 5)
        vm.persist()
    }

    fun resetPlan() {
        studyPlan = StudyPlanDto()
        vm.persist()
    }
}
