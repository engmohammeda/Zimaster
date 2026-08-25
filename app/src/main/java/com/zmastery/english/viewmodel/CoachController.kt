package com.zmastery.english.viewmodel

import com.zmastery.english.data.*
import kotlinx.coroutines.launch

/**
 * Controller for the AI Coach. Holds coach-fact assembly and the on-demand /
 * instant analysis actions. Report state stays on [AppViewModel]. See
 * [ExamsController] for conventions.
 */
internal class CoachController(internal val vm: AppViewModel) {

    private val coachReports get() = vm.coachReports
    private var isCoaching
        get() = vm.isCoaching
        set(v) { vm.isCoaching = v }
    private var coachError
        get() = vm.coachError
        set(v) { vm.coachError = v }

    private val effectivePlan get() = vm.effectivePlan
    private val planSummary get() = vm.planSummary
    private fun spanFor(scope: CoachScope) = vm.spanFor(scope)
    private fun previousSpanFor(scope: CoachScope) = vm.previousSpanFor(scope)
    private val totalWords get() = vm.totalWords
    private val masteredCount get() = vm.masteredCount
    private val dueCount get() = vm.dueCount
    private val predictedRetention get() = vm.predictedRetention
    private val trueRecallRate get() = vm.trueRecallRate
    private val avgStability get() = vm.avgStability
    private val forgottenWords get() = vm.forgottenWords
    private val activeVocab get() = vm.activeVocab
    private val examHistory get() = vm.examHistory
    private val skillRadar get() = vm.skillRadar
    private val streak get() = vm.streak
    private val activityStreak get() = vm.activityStreak
    private val curriculum get() = vm.curriculum
    private val geminiApiKey get() = vm.geminiApiKey
    private val hasAiKey get() = vm.hasAiKey
    private fun nowStamp(): String = vm.nowStamp()
    private fun launch(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        vm.vmScope.launch(block = block)

    fun coachReport(scope: CoachScope): CoachReport? = coachReports[scope.name]

    private fun coachFacts(scope: CoachScope): CoachFacts {
        val plan = effectivePlan
        val summary = planSummary
        val levelName = vm.allLevels.firstOrNull { it.id == plan.targetLevel }?.name
            ?: "المستوى ${plan.targetLevel}"
        return CoachFacts(
            scope = scope,
            levelName = levelName,
            span = spanFor(scope),
            previous = previousSpanFor(scope),
            totalWords = totalWords,
            masteredWords = masteredCount,
            dueNow = dueCount,
            predictedRetention = predictedRetention,
            trueRecallRate = trueRecallRate,
            avgStability = avgStability,
            leeches = forgottenWords,
            hardWords = activeVocab.filter { it.difficulty >= 6.5 && it.totalReviews > 0 }
                .sortedByDescending { it.difficulty }.take(8),
            examAvg = vm.lifetime.examAvg,
            lastExams = examHistory.takeLast(5).reversed(),
            skills = skillRadar,
            streak = maxOf(streak, activityStreak),
            planLabel = "${PlanDuration.from(plan.duration).label} · ${PlanIntensity.from(plan.intensity).label}",
            planOnTrack = summary.onTrack,
            planDrift = summary.driftLessons,
            curriculum = curriculum,
        )
    }

    /** Run the coach on demand for [scope]. Never automatic — saves quota. */
    fun runCoach(scope: CoachScope) {
        if (isCoaching) return
        isCoaching = true
        coachError = null
        launch {
            val facts = coachFacts(scope)
            val report = CoachService.analyze(facts, geminiApiKey, nowStamp())
            coachReports[scope.name] = report
            isCoaching = false
            if (report.local && hasAiKey) {
                coachError = "تعذّر الاتصال بالذكاء الاصطناعي — عُرض تحليل محلي"
            }
            vm.persist()
        }
    }

    /** Instant local analysis without any network — used for the dashboard card. */
    fun quickCoach(scope: CoachScope = CoachScope.WEEKLY): CoachReport =
        coachReports[scope.name] ?: CoachService.localReport(coachFacts(scope), nowStamp())
}
