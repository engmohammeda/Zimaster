package com.zmastery.english.viewmodel

import com.zmastery.english.data.*

/**
 * Controller for telemetry: stat spans, the heatmap, derived analytics, and the
 * real on-screen study-time trackers. The `dayStats`/`reviewHours` maps, the
 * `track` mutator and today/yesterday rows stay on [AppViewModel]. See
 * [ExamsController] for conventions.
 */
internal class TelemetryController(internal val vm: AppViewModel) {

    private val dayStats get() = vm.dayStats
    private val reviewHours get() = vm.reviewHours
    private val examHistory get() = vm.examHistory
    private val totalWords get() = vm.totalWords
    private val masteredCount get() = vm.masteredCount
    private val completedLessons get() = vm.completedLessons
    private val courses get() = vm.courses
    private val lessons get() = vm.lessons
    private val vocab get() = vm.vocab
    private var studyHours
        get() = vm.studyHours
        set(v) { vm.studyHours = v }

    private fun track(mutate: (DayStat) -> Unit) = vm.track(mutate)
    private fun advanceMicroHabit(id: String, amount: Int = 1) = vm.advanceMicroHabit(id, amount)
    private fun completeTask(id: String, amount: Int = 1) = vm.completeTask(id, amount)
    private fun persist() = vm.persist()

    fun spanFor(scope: CoachScope): StatSpan =
        Telemetry.span(dayStats, scope.days, scope.label)

    fun previousSpanFor(scope: CoachScope): StatSpan =
        Telemetry.previousSpan(dayStats, scope.days)

    /** Days for the heatmap — last 119 days (17 weeks) ending today. */
    fun heatmapDays(n: Int = 119): List<DayStat> = Telemetry.span(dayStats, n, "heatmap").days

    val activityStreak: Int get() = Telemetry.currentStreak(dayStats)
    val bestActivityStreak: Int get() = Telemetry.bestStreak(dayStats)

    /** Lifetime totals across every recorded day. */
    val lifetime: StatSpan get() = StatSpan(dayStats.values.sortedBy { it.epochDay }, "كل الأوقات")

    /** Grammar accuracy measured from real exam skill breakdowns. */
    val grammarAccuracy: Float
        get() {
            var ok = 0; var tot = 0
            examHistory.forEach { e ->
                tot += e.skillTotal[ExamSkill.GRAMMAR] ?: 0
                ok += e.skillCorrect[ExamSkill.GRAMMAR] ?: 0
            }
            return if (tot > 0) ok.toFloat() / tot else 0f
        }

    val skillRadar: List<SkillScore>
        get() {
            val lt = lifetime
            return SkillRadar.compute(
                totalWords = totalWords,
                masteredWords = masteredCount,
                lessonsDone = completedLessons,
                listenMinutes = lt.listenMinutes,
                storiesRead = lt.stories,
                conversationTurns = lt.conversationTurns,
                phoneticsDrills = lt.phonetics,
                grammarCorrect = grammarAccuracy,
            )
        }

    val cefrEstimate: Pair<String, Float>
        get() = Telemetry.estimatedCefr(masteredCount, completedLessons, lifetime.examAvg)

    /**
     * Curriculum coverage — measured purely from the learner's OWN imported
     * courses. This is the backbone of "how far through MY course am I".
     */
    val curriculum: CurriculumReport
        get() = Coverage.report(courses.toList(), lessons.toList(), vocab.toList())

    val peakStudyHour: Int? get() = Telemetry.peakHour(reviewHours)

    // ---- Public trackers used across the app ----
    fun trackListening(seconds: Long) {
        if (seconds <= 0) return
        track { it.listenSeconds += seconds }
        // Two spoken sentences (~8s+) satisfy the listening micro-habit step.
        if (seconds >= 3) {
            advanceMicroHabit("micro_listen")
            completeTask("listen")
        }
    }

    // ----- Real on-screen study time -----
    // Idle protection: any single stretch longer than MAX_SESSION is clamped,
    // so leaving the app open overnight can never inflate the numbers.
    private var sessionStartMs: Long = 0L
    private var sessionLabel: String = ""

    /** Longest credit a single uninterrupted stretch can earn (25 min). */
    private val maxSessionSeconds = 25 * 60L

    /** Called when a learning screen becomes visible. */
    fun beginStudySession(label: String) {
        // Bank any previous stretch first (e.g. moving screen → screen).
        endStudySession()
        sessionStartMs = System.currentTimeMillis()
        sessionLabel = label
    }

    /** Called when the learning screen goes away / the app pauses. */
    fun endStudySession() {
        if (sessionStartMs <= 0L) return
        val secs = ((System.currentTimeMillis() - sessionStartMs) / 1000)
            .coerceAtMost(maxSessionSeconds)
        sessionStartMs = 0L
        sessionLabel = ""
        // Ignore accidental taps shorter than 3 seconds.
        if (secs < 3) return
        track { it.studySeconds += secs }
        studyHours += secs / 3600.0
        persist()
    }

    fun trackStoryRead() {
        track { it.storiesRead += 1 }
        advanceMicroHabit("micro_story")
        persist()
    }

    fun trackConversationTurn(n: Int = 1) { track { it.conversationTurns += n }; persist() }

    fun trackPhoneticsDrill(n: Int = 1) { track { it.phoneticsDrills += n }; persist() }
}
