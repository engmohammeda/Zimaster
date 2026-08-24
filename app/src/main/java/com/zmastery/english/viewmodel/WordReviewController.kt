package com.zmastery.english.viewmodel

import com.zmastery.english.data.*

/**
 * Controller for word-level review: the FSRS scheduler ([reviewWord]), the
 * stage-based quick rating, interval previews, quiz generation, and word CRUD.
 * The shared `vocab`/`reviewLogs`/`reviewHours` lists and the FSRS tuning
 * (`desiredRetention`, `maxIntervalDays`) stay on [AppViewModel]. See
 * [ExamsController] for conventions.
 */
internal class WordReviewController(internal val vm: AppViewModel) {

    private val vocab get() = vm.vocab
    private val activeVocab get() = vm.activeVocab
    private val reviewLogs get() = vm.reviewLogs
    private val reviewHours get() = vm.reviewHours
    private var totalReviewsToday
        get() = vm.totalReviewsToday
        set(v) { vm.totalReviewsToday = v }
    private var xp
        get() = vm.xp
        set(v) { vm.xp = v }
    private var nextWordId
        get() = vm.nextWordId
        set(v) { vm.nextWordId = v }
    private var mnemonicVersion
        get() = vm.mnemonicVersion
        set(v) { vm.mnemonicVersion = v }
    private val desiredRetention get() = vm.desiredRetention
    private val maxIntervalDays get() = vm.maxIntervalDays
    private val app get() = vm.app

    private val fsrsWeights = Fsrs.DEFAULT_W

    private fun todayEpochDay(): Long = vm.todayEpochDay()
    private fun track(mutate: (DayStat) -> Unit) = vm.track(mutate)
    private fun completeTask(id: String, amount: Int = 1) = vm.completeTask(id, amount)
    private fun advanceMicroHabit(id: String, amount: Int = 1) = vm.advanceMicroHabit(id, amount)
    private fun advanceRescue(amount: Int = 1) = vm.advanceRescue(amount)
    private fun checkChests() = vm.checkChests()
    private fun syncMysteryRewards(notify: Boolean = true) = vm.syncMysteryRewards(notify)
    private fun rebuildDailyPlan(force: Boolean = false) = vm.rebuildDailyPlan(force)
    private fun persist() = vm.persist()

    fun pendingWordsForLesson(lessonId: Int): List<VocabWord> =
        vocab.filter { it.lessonId == lessonId && it.pendingApproval }

    /**
     * Approve a subset of a lesson's pending words into the active dictionary,
     * and discard the rest.
     */
    fun approveWords(lessonId: Int, approvedIds: Set<Int>) {
        // Remove rejected pending words for this lesson.
        vocab.removeAll { it.lessonId == lessonId && it.pendingApproval && it.id !in approvedIds }
        // Mark approved ones active.
        approvedIds.forEach { id ->
            val i = vocab.indexOfFirst { it.id == id }
            if (i >= 0 && vocab[i].pendingApproval) vocab[i] = vocab[i].copy(pendingApproval = false)
        }
        persist()
    }

    /**
     * Records a full 4-stage review using the FSRS scheduler. See the original
     * docstring in [AppViewModel.reviewWord] for the rating-resolution policy.
     */
    fun reviewWord(
        wordId: Int,
        source: RecallSource,
        reachedStage: Int = 4,
        replays: Int = 0,
        timeMs: Long = 0L,
        explicitGrade: Int? = null,
    ) {
        val idx = vocab.indexOfFirst { it.id == wordId }
        if (idx < 0) return
        val w = vocab[idx]

        // Rating resolution. When [explicitGrade] is supplied the caller already
        // derived the FSRS rating (stage-based quick rating) — use it verbatim so
        // the stage penalty is never applied twice. Otherwise: base rating from
        // the recall source, then softened/rewarded by the stage reached.
        var grade = source.grade
        if (explicitGrade != null) {
            grade = explicitGrade.coerceIn(1, 4)
        } else if (source.grade >= 2) {
            grade = when (reachedStage) {
                1 -> (source.grade + 1).coerceAtMost(4) // recalled from audio only → reward
                2 -> source.grade
                3 -> (source.grade - 1).coerceAtLeast(2)
                else -> (source.grade - 1).coerceAtLeast(1) // full reveal → soften more
            }
        }

        val today = todayEpochDay()
        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()

        val phase = when (w.phase) {
            FsrsPhase.NEW -> Fsrs.Phase.NEW
            FsrsPhase.LEARNING -> Fsrs.Phase.LEARNING
            FsrsPhase.REVIEW -> Fsrs.Phase.REVIEW
            FsrsPhase.RELEARNING -> Fsrs.Phase.RELEARNING
        }

        val sched = Fsrs.schedule(
            w = fsrsWeights,
            rating = grade,
            phase = phase,
            stability = w.stability,
            difficulty = w.difficulty,
            elapsedDays = elapsed,
            desiredRetention = desiredRetention,
            maxInterval = maxIntervalDays,
        )

        val newPhase = when (sched.phase) {
            Fsrs.Phase.NEW -> FsrsPhase.NEW
            Fsrs.Phase.LEARNING -> FsrsPhase.LEARNING
            Fsrs.Phase.REVIEW -> FsrsPhase.REVIEW
            Fsrs.Phase.RELEARNING -> FsrsPhase.RELEARNING
        }

        val reps = if (grade == 1) 0 else w.repetitions + 1
        val lapses = if (grade == 1) w.lapses + 1 else w.lapses
        // "Mastered" = a strong long-term memory (stability ≥ 30d in REVIEW).
        val mastered = newPhase == FsrsPhase.REVIEW && sched.stability >= 30.0
        val newTotal = w.totalReviews + 1
        val newAvgStage = ((w.avgRecallStage * w.totalReviews) + reachedStage) / newTotal

        vocab[idx] = w.copy(
            stability = sched.stability,
            difficulty = sched.difficulty,
            phase = newPhase,
            intervalDays = sched.intervalDays,
            dueInDays = sched.intervalDays,
            lastReviewedDay = today,
            repetitions = reps,
            mastered = mastered,
            lapses = lapses,
            listenCount = w.listenCount + replays,
            totalReviews = newTotal,
            lastRecall = source,
            lastRetrievability = sched.retrievabilityAtReview,
            avgRecallStage = newAvgStage,
            totalTimeMs = w.totalTimeMs + timeMs,
            lastGrade = grade,
        )
        reviewLogs.add(
            ReviewLog(
                wordId = wordId,
                recall = source,
                grade = grade,
                reachedStage = reachedStage,
                replays = replays,
                timeMs = timeMs,
                retrievability = sched.retrievabilityAtReview,
                stabilityAfter = sched.stability,
                intervalAfter = sched.intervalDays,
            )
        )
        totalReviewsToday += 1
        // XP rewards accuracy: higher grade & earlier stage → more XP.
        val gained = 5 + grade * 3 + (4 - reachedStage).coerceAtLeast(0) * 2
        xp += gained
        // ---- telemetry ----
        val nowMastered = vocab[idx].mastered && !w.mastered
        // Study seconds for the review screen are banked by TrackStudyTime.
        track {
            it.reviews += 1
            if (grade >= 2) it.reviewsCorrect += 1 else it.mistakes += 1
            if (nowMastered) it.wordsMastered += 1
            it.xpEarned += gained
        }
        reviewHours.add(java.time.LocalTime.now().hour)
        if (reviewHours.size > 800) reviewHours.removeAt(0)
        // نفس السقف على السجلّات: التحليل يستقرّ إحصائياً قبل 400 حدث بكثير.
        while (reviewLogs.size > 800) reviewLogs.removeAt(0)
        completeTask("review")
        // 🔥 The micro-habit (الورد اليومي) — 5 cards is enough to keep the
        // streak alive on a busy day.
        advanceMicroHabit("micro_review")
        // 🟣 المرحلة الرابعة: كل بطاقة تُحتسب في مهمة الإنقاذ إن كانت نشطة.
        advanceRescue()
        checkChests()
        syncMysteryRewards()
        persist()
    }

    // ======================================================================
    //  Stage-based quick rating (single visible "تذكرتها" button)
    // ======================================================================

    /** The implicit recall source for recalling at [stage] (1..4). */
    fun sourceForStage(stage: Int): RecallSource = when (stage) {
        1 -> RecallSource.SOUND
        2 -> RecallSource.IMAGE
        3 -> RecallSource.TEXT
        else -> RecallSource.STUDIED
    }

    /** The FSRS rating (1..4) earned by recalling at [stage] (1..4). */
    fun gradeForStage(stage: Int): Int = when (stage) {
        1 -> 4   // Easy  — recalled from sound alone
        2 -> 3   // Good  — needed the mental image
        3 -> 2   // Hard  — needed to read the word
        else -> 2 // Hard — vaguely familiar after the full reveal
    }

    fun reviewWordAtStage(wordId: Int, stage: Int, replays: Int = 0, timeMs: Long = 0L) {
        reviewWord(
            wordId = wordId,
            source = sourceForStage(stage),
            reachedStage = stage,
            replays = replays,
            timeMs = timeMs,
            explicitGrade = gradeForStage(stage),
        )
    }

    fun failWord(wordId: Int, reachedStage: Int = 4, replays: Int = 0, timeMs: Long = 0L) {
        reviewWord(
            wordId = wordId,
            source = RecallSource.FAILED,
            reachedStage = reachedStage,
            replays = replays,
            timeMs = timeMs,
            explicitGrade = 1,
        )
    }

    fun previewStageIntervalDays(wordId: Int, stage: Int): Int =
        previewIntervalWithGrade(wordId, gradeForStage(stage))

    fun previewFailIntervalDays(wordId: Int): Int = previewIntervalWithGrade(wordId, 1)

    fun previewIntervalWithGrade(wordId: Int, grade: Int): Int {
        val w = vocab.firstOrNull { it.id == wordId } ?: return 1
        val today = todayEpochDay()
        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()
        val phase = when (w.phase) {
            FsrsPhase.NEW -> Fsrs.Phase.NEW
            FsrsPhase.LEARNING -> Fsrs.Phase.LEARNING
            FsrsPhase.REVIEW -> Fsrs.Phase.REVIEW
            FsrsPhase.RELEARNING -> Fsrs.Phase.RELEARNING
        }
        return Fsrs.schedule(
            fsrsWeights, grade.coerceIn(1, 4), phase, w.stability, w.difficulty, elapsed,
            desiredRetention, maxIntervalDays,
        ).intervalDays
    }

    fun previewIntervalDays(wordId: Int, source: RecallSource, reachedStage: Int): Int {
        val w = vocab.firstOrNull { it.id == wordId } ?: return 1
        var grade = source.grade
        if (source.grade >= 2) {
            grade = when (reachedStage) {
                1 -> (source.grade + 1).coerceAtMost(4)
                2 -> source.grade
                3 -> (source.grade - 1).coerceAtLeast(2)
                else -> (source.grade - 1).coerceAtLeast(1)
            }
        }
        val today = todayEpochDay()
        val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()
        val phase = when (w.phase) {
            FsrsPhase.NEW -> Fsrs.Phase.NEW
            FsrsPhase.LEARNING -> Fsrs.Phase.LEARNING
            FsrsPhase.REVIEW -> Fsrs.Phase.REVIEW
            FsrsPhase.RELEARNING -> Fsrs.Phase.RELEARNING
        }
        return Fsrs.schedule(
            fsrsWeights, grade, phase, w.stability, w.difficulty, elapsed,
            desiredRetention, maxIntervalDays,
        ).intervalDays
    }

    /** Format a day count as a short Arabic label ("الآن" / "٣ي" / "٢ش"). */
    fun formatInterval(days: Int): String = when {
        days <= 0 -> "الآن"
        days < 30 -> "$days ي"
        days < 365 -> "${days / 30} ش"
        else -> "${days / 365} س"
    }

    // ----- Quiz generation from vocab -----
    fun generateQuiz(count: Int): List<QuizQuestion> {
        val base = activeVocab
        val pool = base.shuffled()
        val chosen = pool.take(count.coerceAtMost(pool.size))
        return chosen.mapIndexed { i, w ->
            val kind = QuizKind.values()[i % QuizKind.values().size]
            when (kind) {
                QuizKind.MEANING -> {
                    val wrong = base.filter { it.id != w.id }.shuffled().take(3).map { it.arabic }
                    val opts = (wrong + w.arabic).shuffled()
                    QuizQuestion("ما معنى الكلمة؟", w.english, opts, opts.indexOf(w.arabic), kind)
                }
                QuizKind.EXAMPLE -> {
                    val wrong = base.filter { it.id != w.id }.shuffled().take(3).map { it.english }
                    val opts = (wrong + w.english).shuffled()
                    QuizQuestion("أكمل الجملة بالكلمة المناسبة", w.exampleEn.replace(w.english, "_____", ignoreCase = true), opts, opts.indexOf(w.english), kind)
                }
                QuizKind.SPELLING -> {
                    val correct = w.english
                    val scrambled = mutableSetOf(correct)
                    var guard = 0
                    while (scrambled.size < 4 && guard < 40) { scrambled.add(scramble(correct)); guard++ }
                    val opts = scrambled.toList().shuffled()
                    QuizQuestion("اختر الإملاء الصحيح لـ: ${w.arabic}", w.phonetic, opts, opts.indexOf(correct), kind)
                }
            }
        }
    }

    private fun scramble(s: String): String {
        if (s.length < 3) return s + "x"
        val chars = s.toCharArray()
        val i = (1 until s.length - 1).random()
        val j = (1 until s.length - 1).random()
        val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        return String(chars)
    }

    fun addXp(amount: Int) { xp += amount }

    // ----- Add a single word to the dictionary (manual or AI) -----
    fun addWord(
        english: String,
        arabic: String,
        exampleEn: String = "",
        exampleAr: String = "",
        phonetic: String = "",
        mentalImage: String = "",
        courseId: Int = 0,
    ): Boolean {
        val en = english.trim()
        if (en.isEmpty()) return false
        vocab.add(
            0,
            VocabWord(
                id = nextWordId++,
                english = en,
                arabic = arabic.trim().ifBlank { "—" },
                exampleEn = exampleEn.trim(),
                exampleAr = exampleAr.trim(),
                phonetic = phonetic.trim(),
                mentalImage = mentalImage.trim(),
                courseId = courseId,
                // Manually added words go STRAIGHT into the active dictionary.
                pendingApproval = false,
            )
        )
        xp += 5
        track { it.wordsAdded += 1; it.xpEarned += 5 }
        advanceMicroHabit("micro_word")
        completeTask("addword")
        // القاموس تغيّر ⇒ قد تتغيّر الخطة المتاحة (اختبار/مراجعة صارت ممكنة).
        rebuildDailyPlan(force = true)
        persist()
        return true
    }

    fun updateWord(
        id: Int,
        english: String,
        arabic: String,
        exampleEn: String,
        exampleAr: String,
        phonetic: String,
        mentalImage: String,
    ): Boolean {
        val idx = vocab.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val en = english.trim()
        if (en.isEmpty()) return false
        vocab[idx] = vocab[idx].copy(
            english = en,
            arabic = arabic.trim().ifBlank { "—" },
            exampleEn = exampleEn.trim(),
            exampleAr = exampleAr.trim(),
            phonetic = phonetic.trim(),
            mentalImage = mentalImage.trim(),
        )
        persist()
        return true
    }

    fun deleteWord(id: Int) {
        vocab.removeAll { it.id == id }
        MnemonicStore.delete(app, id)
        mnemonicVersion++
        persist()
    }
}
