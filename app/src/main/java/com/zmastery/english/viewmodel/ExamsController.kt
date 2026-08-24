package com.zmastery.english.viewmodel

import com.zmastery.english.data.*

/**
 * Controller for the Exam feature.
 *
 * Holds the exam *logic* (building, answering, scoring, weakness ranking).
 * All persisted/UI state still lives on [AppViewModel]; this class reaches it
 * through small local aliases, so the method bodies read almost exactly as
 * they did when they were members of the view model.
 *
 * Ownership rule (cycle-free): every symbol is owned by exactly one of
 * {AppViewModel, ExamsController}. The view model keeps thin delegating
 * members for everything this class implements, so no screen has to change.
 */
internal class ExamsController(internal val vm: AppViewModel) {

    // ── State owned by the view model (read/write through aliases) ──
    private val examHistory get() = vm.examHistory
    private val examMisses get() = vm.examMisses
    private val examQuestions get() = vm.examQuestions
    private var examMode
        get() = vm.examMode
        set(v) { vm.examMode = v }
    private var examTitle
        get() = vm.examTitle
        set(v) { vm.examTitle = v }

    private val lessons get() = vm.lessons
    private val courses get() = vm.courses
    private val vocab get() = vm.vocab
    private val activeVocab get() = vm.activeVocab
    private val desiredRetention get() = vm.desiredRetention
    private var xp
        get() = vm.xp
        set(v) { vm.xp = v }
    private var accuracy
        get() = vm.accuracy
        set(v) { vm.accuracy = v }

    // ── Computed props owned by the view model (read-only aliases) ──
    private val doneLessons get() = vm.doneLessons
    private val examableWords get() = vm.examableWords

    // ── View-model helpers ──
    private fun todayEpochDay(): Long = vm.todayEpochDay()
    private fun nowStamp(): String = vm.nowStamp()
    private fun track(mutate: (DayStat) -> Unit) = vm.track(mutate)
    private fun completeTask(id: String, amount: Int = 1) = vm.completeTask(id, amount)
    private val app get() = vm.app

    private fun examSource(): ExamSource = ExamSource(
        words = examableWords,
        lessons = lessons.toList(),
        courses = courses.toList(),
        misses = examMisses.toMap(),
        desiredRetention = desiredRetention,
        todayEpochDay = todayEpochDay(),
    )

    /** Weakness-ranked words — drives the "نقاط ضعفك" panel. */
    val weakestWords: List<WeakWord>
        get() = ExamBuilder.rankWords(examSource()).take(20)

    /** True when there is enough studied material for a given mode. */
    fun canTakeExam(mode: ExamMode): Boolean {
        val src = examSource()
        if (src.words.size < ExamBuilder.MIN_WORDS && mode != ExamMode.GRAMMAR && mode != ExamMode.CONVERSATION) return false
        return when (mode) {
            ExamMode.GRAMMAR -> doneLessons.any { it.quiz.isNotEmpty() }
            ExamMode.CONVERSATION -> doneLessons.any { it.dialogues.isNotEmpty() }
            ExamMode.WEAKNESS -> src.words.size >= ExamBuilder.MIN_WORDS
            ExamMode.LESSON -> doneLessons.isNotEmpty()
            ExamMode.COURSE -> vm.examableCourses.isNotEmpty()
            else -> src.words.size >= ExamBuilder.MIN_WORDS
        }
    }

    /** How many questions a mode could actually produce right now (for badges). */
    fun examAvailability(mode: ExamMode): Int {
        val src = examSource()
        return when (mode) {
            ExamMode.GRAMMAR -> doneLessons.sumOf { it.quiz.size }
            ExamMode.CONVERSATION -> doneLessons.sumOf { it.dialogues.size }
            ExamMode.WEAKNESS -> ExamBuilder.rankWords(src).count { it.weakness >= 0.32f }
            ExamMode.LISTENING -> src.words.size
            else -> src.words.size
        }
    }

    /**
     * Build and start an exam. Returns the number of questions created
     * (0 = not enough material).
     */
    fun startExam(
        mode: ExamMode,
        count: Int,
        courseId: Int?,
        lessonId: Int?,
    ): Int {
        val qs = ExamBuilder.build(
            src = examSource(),
            mode = mode,
            count = count.coerceIn(4, 40),
            courseId = courseId,
            lessonId = lessonId,
        )
        examQuestions.clear()
        examQuestions.addAll(qs)
        examMode = mode
        examTitle = when {
            lessonId != null -> lessons.firstOrNull { it.id == lessonId }?.title ?: mode.label
            courseId != null -> courses.firstOrNull { it.id == courseId }?.name ?: mode.label
            else -> mode.label
        }
        return qs.size
    }

    fun clearExam() {
        examQuestions.clear()
    }

    /**
     * Record a single answer. A wrong answer on a vocabulary question feeds
     * BOTH the exam-miss counter and the FSRS scheduler, so the word resurfaces
     * in the normal review queue — exams genuinely drive learning.
     */
    fun recordExamAnswer(q: ExamQuestion, correct: Boolean) {
        if (q.wordId > 0) {
            if (!correct) {
                examMisses[q.wordId] = (examMisses[q.wordId] ?: 0) + 1
                // Treat an exam miss as a real lapse: halve stability, make due now.
                val i = vocab.indexOfFirst { it.id == q.wordId }
                if (i >= 0) {
                    val w = vocab[i]
                    vocab[i] = w.copy(
                        dueInDays = 0,
                        intervalDays = 0,
                        stability = (w.stability * 0.6).coerceAtLeast(0.1),
                        lapses = w.lapses + 1,
                        phase = if (w.phase == FsrsPhase.NEW) FsrsPhase.NEW else FsrsPhase.RELEARNING,
                    )
                }
            } else {
                // A correct answer under exam pressure is real evidence — decay the
                // miss counter so old mistakes stop dominating future exams.
                val m = examMisses[q.wordId] ?: 0
                if (m > 0) examMisses[q.wordId] = m - 1
            }
        }
        xp += if (correct) 8 + q.difficulty * 4 else 1
    }

    /** Finish an exam: store the record, update accuracy, award XP. */
    fun finishExam(
        correct: Int,
        total: Int,
        durationMs: Long,
        skillCorrect: Map<ExamSkill, Int>,
        skillTotal: Map<ExamSkill, Int>,
    ) {
        if (total <= 0) return
        val rec = ExamRecord(
            id = "ex_${System.currentTimeMillis()}",
            mode = examMode,
            title = examTitle.ifBlank { examMode.label },
            correct = correct, total = total,
            stamp = nowStamp(), durationMs = durationMs,
            skillCorrect = skillCorrect, skillTotal = skillTotal,
        )
        examHistory.add(rec)
        if (examHistory.size > 60) examHistory.removeAt(0)
        val pct = correct * 100 / total
        accuracy = if (accuracy == 0) pct else (accuracy * 2 + pct) / 3
        // NOTE: study seconds are banked by TrackStudyTime on the exam screen,
        // so they are deliberately NOT added again here.
        track {
            it.examsTaken += 1
            it.examScoreSum += pct
            it.mistakes += (total - correct)
        }
        completeTask("quiz")
        if (pct >= 80) {
            com.zmastery.english.notify.Notifier.achievement(
                app,
                "نتيجة ممتازة! \uD83C\uDFC6",
                "أنهيت \"$rec.title\" بنسبة $pct%. استمر على هذا المستوى!",
            )
        }
        vm.persist()
    }

    /** Lifetime average across all recorded exams. */
    val examAverage: Int
        get() = if (examHistory.isEmpty()) 0 else examHistory.map { it.pct }.average().toInt()

    val examBest: Int get() = examHistory.maxOfOrNull { it.pct } ?: 0

    /** Aggregated accuracy per skill across all exams — powers the radar list. */
    val skillAccuracy: Map<ExamSkill, Float>
        get() {
            val corr = HashMap<ExamSkill, Int>()
            val tot = HashMap<ExamSkill, Int>()
            examHistory.forEach { r ->
                r.skillTotal.forEach { (s, n) -> tot[s] = (tot[s] ?: 0) + n }
                r.skillCorrect.forEach { (s, n) -> corr[s] = (corr[s] ?: 0) + n }
            }
            return tot.filter { it.value > 0 }
                .mapValues { (s, n) -> (corr[s] ?: 0).toFloat() / n }
        }

    /** The weakest skill overall, or null when there is no data yet. */
    val weakestSkill: ExamSkill?
        get() = skillAccuracy.minByOrNull { it.value }?.key
}
