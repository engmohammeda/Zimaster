package com.zmastery.english.data

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// ==========================================================================
//  Exam engine — a *studied* exam builder, never a random shuffle.
//
//  Core ideas:
//   • Only STUDIED material is eligible: completed lessons and approved
//     dictionary words. A lesson that was merely imported never appears.
//   • Every candidate carries a WEAKNESS SCORE derived from real FSRS memory
//     state (lapses, difficulty, stability, retrievability, recall stage) plus
//     the learner's own exam history. High-weakness items are sampled first.
//   • A BLUEPRINT decides the exact mix of question types per mode, so an exam
//     always covers the skills it claims to cover.
//   • Questions span every skill: meaning, reverse recall, cloze, typed spelling,
//     true/false, listening ("ماذا سمعت؟"), phonetics, grammar (from lesson
//     quizzes), conversation (from dialogues) and sentence building.
// ==========================================================================

/** The five skills an exam can probe. Drives the per-skill result breakdown. */
enum class ExamSkill(val label: String, val emoji: String) {
    VOCAB("المفردات", "\uD83D\uDCD8"),
    SPELLING("الإملاء والكتابة", "\u270F\uFE0F"),
    LISTENING("الاستماع", "\uD83D\uDD0A"),
    GRAMMAR("القواعد", "\uD83D\uDCD0"),
    CONVERSATION("المحادثة", "\uD83D\uDCAC"),
    READING("القراءة والفهم", "\uD83D\uDCD6"),
}

/** Every question shape the runner knows how to render. */
enum class ExamQType(val label: String, val skill: ExamSkill, val written: Boolean = false) {
    MCQ_MEANING("معنى الكلمة", ExamSkill.VOCAB),
    MCQ_REVERSE("الكلمة من المعنى", ExamSkill.VOCAB),
    MCQ_CLOZE("أكمل الجملة", ExamSkill.READING),
    PHONETIC_MCQ("النطق الصوتي", ExamSkill.LISTENING),
    TRUE_FALSE("صح أم خطأ", ExamSkill.VOCAB),
    LISTENING_CHOICE("ماذا سمعت؟", ExamSkill.LISTENING),
    LISTENING_WRITTEN("اكتب ما تسمع", ExamSkill.LISTENING, written = true),
    WRITTEN_BLANK("اكتب الكلمة الناقصة", ExamSkill.SPELLING, written = true),
    WRITTEN_MEANING("ترجم واكتب", ExamSkill.SPELLING, written = true),
    GRAMMAR_MCQ("قاعدة نحوية", ExamSkill.GRAMMAR),
    GRAMMAR_TF("قاعدة — صح أم خطأ", ExamSkill.GRAMMAR),
    DIALOGUE_MCQ("أكمل الحوار", ExamSkill.CONVERSATION),
    ORDER_WORDS("رتّب الجملة", ExamSkill.SPELLING),
}

/** A single exam question, fully self-contained. */
data class ExamQuestion(
    val key: String,
    val type: ExamQType,
    /** Arabic instruction line. */
    val prompt: String,
    /** The main subject shown large (may be blank for listening questions). */
    val subject: String = "",
    /** Secondary line under the subject (phonetic, hint, Arabic gloss…). */
    val subtitle: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = -1,
    /** Expected text for typed questions. */
    val correctText: String = "",
    /** Extra accepted spellings for typed questions. */
    val accepted: List<String> = emptyList(),
    val explanationAr: String = "",
    /** English text spoken by TTS (listening questions + replay after answering). */
    val audioText: String = "",
    /** Hide [subject] until the learner answers (listening questions). */
    val hideSubject: Boolean = false,
    val wordId: Int = 0,
    val lessonId: Int = 0,
    val courseId: Int = 0,
    /** 1 easy · 2 medium · 3 hard — shown as a chip and used for XP. */
    val difficulty: Int = 1,
    /** Weakness score of the source item at build time (diagnostics). */
    val weakness: Float = 0f,
) {
    val skill: ExamSkill get() = type.skill
    val isWritten: Boolean get() = type.written
    val isOrdering: Boolean get() = type == ExamQType.ORDER_WORDS

    /** True when [answer] is an acceptable typed response. */
    fun matchesTyped(answer: String): Boolean {
        val a = ExamText.normalize(answer)
        if (a.isEmpty()) return false
        val targets = (listOf(correctText) + accepted).map { ExamText.normalize(it) }.filter { it.isNotEmpty() }
        if (targets.any { it == a }) return true
        // Forgive a single typo on longer words (real learners mistype).
        return targets.any { t ->
            t.length >= 5 && ExamText.levenshtein(a, t) <= 1
        }
    }
}

/** Text helpers for grading typed answers. */
object ExamText {

    private val strip = Regex("[^\\p{L}\\p{Nd}\\s']")

    /** Lowercase, drop punctuation, collapse whitespace. */
    fun normalize(s: String): String =
        s.lowercase().replace(strip, " ").replace(Regex("\\s+"), " ").trim()

    /** Classic Levenshtein edit distance (iterative, O(n) memory). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            prev = cur.copyOf()
        }
        return prev[b.length]
    }

    /** Split an English sentence into word tokens (keeps apostrophes). */
    fun tokens(s: String): List<String> =
        s.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Replace [word] inside [sentence] with a blank, case-insensitively. */
    fun blank(sentence: String, word: String, blank: String = "ــــــــ"): String {
        if (word.isBlank()) return sentence
        val rx = Regex("\\b${Regex.escape(word)}\\w*\\b", RegexOption.IGNORE_CASE)
        val out = rx.replace(sentence, blank)
        return if (out == sentence) sentence.replace(word, blank, ignoreCase = true) else out
    }

    /** True when the sentence actually contains the word (so a blank is possible). */
    fun contains(sentence: String, word: String): Boolean =
        word.isNotBlank() && sentence.contains(word, ignoreCase = true)
}

/** The kind of exam the learner picks from the hub. */
enum class ExamMode(
    val label: String,
    val desc: String,
    /** Relative type weights used to fill the blueprint. */
    val mix: Map<ExamQType, Int>,
    /** How strongly weakness biases sampling: 0 = uniform, 1 = weakest first. */
    val weaknessBias: Float,
) {
    SMART(
        "الاختبار الذكي",
        "يوزّع الأسئلة على كل مهاراتك ويركّز على نقاط ضعفك",
        mapOf(
            ExamQType.MCQ_MEANING to 3,
            ExamQType.MCQ_REVERSE to 2,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.WRITTEN_BLANK to 2,
            ExamQType.LISTENING_CHOICE to 2,
            ExamQType.TRUE_FALSE to 1,
            ExamQType.GRAMMAR_MCQ to 2,
            ExamQType.DIALOGUE_MCQ to 1,
            ExamQType.PHONETIC_MCQ to 1,
            ExamQType.ORDER_WORDS to 1,
        ),
        weaknessBias = 0.75f,
    ),
    WEAKNESS(
        "نقاط الضعف",
        "الكلمات المنسية والصعبة فقط — علاج مركّز",
        mapOf(
            ExamQType.MCQ_MEANING to 3,
            ExamQType.WRITTEN_BLANK to 3,
            ExamQType.MCQ_REVERSE to 2,
            ExamQType.LISTENING_WRITTEN to 2,
            ExamQType.WRITTEN_MEANING to 2,
            ExamQType.TRUE_FALSE to 1,
        ),
        weaknessBias = 1f,
    ),
    LISTENING(
        "الاستماع والصوتيات",
        "ماذا سمعت؟ — تدريب الأذن والنطق",
        mapOf(
            ExamQType.LISTENING_CHOICE to 4,
            ExamQType.LISTENING_WRITTEN to 3,
            ExamQType.PHONETIC_MCQ to 3,
        ),
        weaknessBias = 0.6f,
    ),
    WRITING(
        "الكتابة والإملاء",
        "اكتب بيدك — بلا خيارات جاهزة",
        mapOf(
            ExamQType.WRITTEN_BLANK to 4,
            ExamQType.WRITTEN_MEANING to 3,
            ExamQType.ORDER_WORDS to 2,
            ExamQType.LISTENING_WRITTEN to 1,
        ),
        weaknessBias = 0.7f,
    ),
    GRAMMAR(
        "القواعد",
        "من أسئلة الدروس المكتملة",
        mapOf(
            ExamQType.GRAMMAR_MCQ to 5,
            ExamQType.GRAMMAR_TF to 2,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.ORDER_WORDS to 1,
        ),
        weaknessBias = 0.5f,
    ),
    CONVERSATION(
        "المحادثة",
        "أكمل الحوارات من دروسك",
        mapOf(
            ExamQType.DIALOGUE_MCQ to 5,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.LISTENING_CHOICE to 2,
        ),
        weaknessBias = 0.4f,
    ),
    LESSON(
        "اختبار درس",
        "أسئلة درس واحد أكملته",
        mapOf(
            ExamQType.GRAMMAR_MCQ to 3,
            ExamQType.MCQ_MEANING to 2,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.WRITTEN_BLANK to 2,
            ExamQType.DIALOGUE_MCQ to 1,
            ExamQType.LISTENING_CHOICE to 1,
        ),
        weaknessBias = 0.3f,
    ),
    COURSE(
        "اختبار كورس",
        "كل ما أكملته في كورس واحد",
        mapOf(
            ExamQType.MCQ_MEANING to 3,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.WRITTEN_BLANK to 2,
            ExamQType.GRAMMAR_MCQ to 2,
            ExamQType.LISTENING_CHOICE to 2,
            ExamQType.MCQ_REVERSE to 1,
            ExamQType.DIALOGUE_MCQ to 1,
        ),
        weaknessBias = 0.55f,
    ),
    DAILY(
        "اختبار كلمات اليوم",
        "كلمات مراجعة اليوم + المنسية + كلمات الأمس",
        mapOf(
            ExamQType.MCQ_MEANING to 3,
            ExamQType.MCQ_REVERSE to 2,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.LISTENING_WRITTEN to 2,
            ExamQType.WRITTEN_BLANK to 2,
            ExamQType.LISTENING_CHOICE to 1,
        ),
        weaknessBias = 0.85f,
    ),
    WEEKLY(
        "الاختبار الأسبوعي",
        "كل ما درسته هذا الأسبوع — كلمات وقواعد وقراءة وحوار",
        mapOf(
            ExamQType.MCQ_MEANING to 3,
            ExamQType.MCQ_REVERSE to 2,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.WRITTEN_BLANK to 2,
            ExamQType.GRAMMAR_MCQ to 2,
            ExamQType.GRAMMAR_TF to 1,
            ExamQType.DIALOGUE_MCQ to 2,
            ExamQType.LISTENING_CHOICE to 2,
            ExamQType.ORDER_WORDS to 1,
        ),
        weaknessBias = 0.6f,
    ),
    FINAL(
        "الاختبار الشامل",
        "كل الكورسات والدروس المكتملة — تقييم حقيقي",
        mapOf(
            ExamQType.MCQ_MEANING to 2,
            ExamQType.MCQ_REVERSE to 2,
            ExamQType.MCQ_CLOZE to 2,
            ExamQType.WRITTEN_BLANK to 2,
            ExamQType.WRITTEN_MEANING to 1,
            ExamQType.LISTENING_CHOICE to 2,
            ExamQType.LISTENING_WRITTEN to 1,
            ExamQType.PHONETIC_MCQ to 1,
            ExamQType.TRUE_FALSE to 1,
            ExamQType.GRAMMAR_MCQ to 2,
            ExamQType.GRAMMAR_TF to 1,
            ExamQType.DIALOGUE_MCQ to 1,
            ExamQType.ORDER_WORDS to 1,
        ),
        weaknessBias = 0.5f,
    );

    companion object {
        fun from(name: String): ExamMode = runCatching { valueOf(name) }.getOrDefault(SMART)
    }
}

/** A word plus its computed weakness, used for weighted sampling. */
data class WeakWord(val word: VocabWord, val weakness: Float, val reason: String)

/** Everything the builder needs, gathered from the ViewModel. */
class ExamSource(
    val words: List<VocabWord>,
    val lessons: List<Lesson>,
    val courses: List<Course>,
    /** wordId → number of wrong exam answers so far. */
    val misses: Map<Int, Int> = emptyMap(),
    val desiredRetention: Double = 0.90,
    val todayEpochDay: Long = 0L,
)

/**
 * Builds exams. Stateless and pure — give it a source, get questions back.
 */
object ExamBuilder {

    /** Minimum distinct words needed before an exam can be built at all. */
    const val MIN_WORDS = 4

    /**
     * Weakness score 0..1 for a word. Blends FSRS memory state with the
     * learner's exam mistakes so the exam attacks what is genuinely fragile.
     */
    fun weaknessOf(w: VocabWord, misses: Int, today: Long, desiredRetention: Double): Pair<Float, String> {
        var score = 0f
        val reasons = ArrayList<String>(3)

        // 1) Lapses — the strongest signal of a leech.
        if (w.lapses > 0) {
            score += min(0.30f, w.lapses * 0.11f)
            if (w.lapses >= 2) reasons.add("نُسيت ${w.lapses} مرات")
        }
        // 2) Intrinsic difficulty from FSRS (1..10).
        if (w.difficulty > 0) {
            val d = ((w.difficulty - 4.0) / 6.0).coerceIn(0.0, 1.0)
            score += (d * 0.22).toFloat()
            if (w.difficulty >= 6.5) reasons.add("صعبة")
        }
        // 3) Fragile memory — low stability.
        if (w.totalReviews > 0 && w.stability < 10.0) {
            score += (((10.0 - w.stability) / 10.0).coerceIn(0.0, 1.0) * 0.16).toFloat()
        }
        // 4) Overdue — retrievability already below target.
        if (w.totalReviews > 0) {
            val elapsed = if (w.lastReviewedDay < 0) 0.0 else (today - w.lastReviewedDay).toDouble()
            val r = Fsrs.retrievability(elapsed, w.stability)
            if (r <= desiredRetention) {
                score += ((desiredRetention - r).coerceAtLeast(0.0) * 0.30).toFloat()
                if (r < 0.6) reasons.add("على وشك النسيان")
            }
        } else {
            // Never reviewed → genuinely untested.
            score += 0.20f
            reasons.add("لم تُراجع بعد")
        }
        // 5) Needed late hints during review.
        if (w.avgRecallStage >= 3f) {
            score += 0.10f
            reasons.add("تحتاج تلميحات")
        }
        // 6) Past exam mistakes.
        if (misses > 0) {
            score += min(0.25f, misses * 0.09f)
            reasons.add("أخطأت بها $misses مرة")
        }

        return score.coerceIn(0f, 1f) to (reasons.firstOrNull() ?: "مراجعة دورية")
    }

    /** All eligible words ranked by weakness (weakest first). */
    fun rankWords(src: ExamSource): List<WeakWord> =
        src.words.map { w ->
            val (score, reason) = weaknessOf(w, src.misses[w.id] ?: 0, src.todayEpochDay, src.desiredRetention)
            WeakWord(w, score, reason)
        }.sortedByDescending { it.weakness }

    /**
     * Weighted sample without replacement. [bias] 0 → uniform, 1 → strictly
     * weakest-first. Guarantees no duplicate words inside one exam.
     */
    private fun sample(pool: List<WeakWord>, n: Int, bias: Float, rnd: Random): List<WeakWord> {
        if (pool.isEmpty() || n <= 0) return emptyList()
        val remaining = pool.toMutableList()
        val out = ArrayList<WeakWord>(min(n, pool.size))
        repeat(min(n, pool.size)) {
            val weights = remaining.map { 0.08f + (it.weakness * bias + (1f - bias) * 0.5f) }
            val total = weights.sum()
            var r = rnd.nextFloat() * total
            var idx = 0
            for (i in weights.indices) {
                r -= weights[i]
                if (r <= 0f) { idx = i; break }
                idx = i
            }
            out.add(remaining.removeAt(idx))
        }
        return out
    }

    /** Distinct wrong answers drawn from other words, padded if the pool is thin. */
    private fun distractors(
        all: List<VocabWord>,
        exceptId: Int,
        n: Int,
        rnd: Random,
        pick: (VocabWord) -> String,
    ): List<String> {
        val seen = LinkedHashSet<String>()
        all.filter { it.id != exceptId }
            .shuffled(rnd)
            .forEach { c ->
                val v = pick(c).trim()
                if (v.isNotBlank() && v != "—") seen.add(v)
                if (seen.size >= n) return@forEach
            }
        return seen.take(n).toList()
    }

    /** Build a shuffled option list and report where the answer landed. */
    private fun optionsWith(correct: String, wrong: List<String>, rnd: Random): Pair<List<String>, Int> {
        val opts = (wrong.filter { it != correct } + correct).distinct().shuffled(rnd)
        return opts to opts.indexOf(correct)
    }

    /**
     * The main entry point.
     *
     * @param mode      exam flavour (decides the type mix + weakness bias)
     * @param count     desired number of questions
     * @param courseId  restrict to one course (COURSE mode)
     * @param lessonId  restrict to one lesson (LESSON mode)
     */
    fun build(
        src: ExamSource,
        mode: ExamMode,
        count: Int,
        courseId: Int? = null,
        lessonId: Int? = null,
        seed: Long = System.currentTimeMillis(),
    ): List<ExamQuestion> {
        val rnd = Random(seed)

        // ---- 1. Scope the studied material --------------------------------
        val completed = src.lessons.filter { it.isCompleted }
        val scopedLessons = when {
            lessonId != null -> completed.filter { it.id == lessonId }
            courseId != null -> completed.filter { it.courseId == courseId }
            else -> completed
        }
        val scopedLessonIds = scopedLessons.map { it.id }.toSet()

        // Words: approved dictionary entries. A word tied to a lesson is only
        // eligible once that lesson is completed (or it has been reviewed).
        val completedIds = completed.map { it.id }.toSet()
        var words = src.words.filter { w ->
            w.lessonId == 0 || w.lessonId in completedIds || w.totalReviews > 0
        }
        if (lessonId != null) {
            val l = src.lessons.firstOrNull { it.id == lessonId }
            val ids = (l?.newWordIds ?: emptyList()).toSet()
            val scoped = words.filter { it.id in ids || it.lessonId == lessonId }
            // Fall back to the course when a lesson carries no vocabulary.
            if (scoped.size >= 2) words = scoped
            else if (l != null) words = words.filter { w -> w.courseId == l.courseId || w.lessonId in scopedLessonIds }
        } else if (courseId != null) {
            val scoped = words.filter { it.courseId == courseId || it.lessonId in scopedLessonIds }
            if (scoped.size >= 2) words = scoped
        }

        val ranked = rankWords(ExamSource(words, src.lessons, src.courses, src.misses, src.desiredRetention, src.todayEpochDay))

        // Weakness mode narrows to genuinely fragile items.
        val pool = when (mode) {
            ExamMode.WEAKNESS -> {
                val weak = ranked.filter { it.weakness >= 0.32f }
                if (weak.size >= 3) weak else ranked.take(12)
            }
            // Today's set: words due now + leeches + words touched yesterday.
            ExamMode.DAILY -> {
                val today = src.todayEpochDay
                val picked = ranked.filter { r ->
                    val w = r.word
                    val due = w.totalReviews == 0 ||
                        (today - w.lastReviewedDay) >= w.intervalDays.toLong()
                    val leech = w.lapses >= 2
                    val yesterday = w.lastReviewedDay == today - 1
                    due || leech || yesterday
                }
                if (picked.size >= 4) picked else ranked.take(20)
            }
            // This week's material: anything reviewed or added in the last 7 days.
            ExamMode.WEEKLY -> {
                val today = src.todayEpochDay
                val picked = ranked.filter { r ->
                    val w = r.word
                    w.lastReviewedDay >= today - 7 || w.totalReviews == 0
                }
                if (picked.size >= 6) picked else ranked
            }
            else -> ranked
        }

        if (pool.isEmpty()) return emptyList()

        // ---- 2. Content banks from completed lessons ----------------------
        val grammarBank = scopedLessons.flatMap { l -> l.quiz.map { l to it } }
            .filter { (_, q) -> q.question.isNotBlank() && q.answer.isNotBlank() }
        val dialogueBank = scopedLessons.flatMap { l ->
            l.dialogues.filter { it.en.isNotBlank() }.map { l to it }
        }
        val sentenceBank = scopedLessons.flatMap { l ->
            (l.keySentences + l.examples + l.segments).filter { it.en.split(" ").size in 3..14 }.map { l to it }
        }

        // ---- 3. Resolve the blueprint ------------------------------------
        val allWords = words
        val plan = plan(mode, count, pool.size, grammarBank.size, dialogueBank.size, sentenceBank.size, allWords)

        // ---- 4. Emit questions -------------------------------------------
        val questions = ArrayList<ExamQuestion>(count)
        val usedWordTypes = HashSet<String>()   // "wordId:type" — no repeats
        var gIdx = 0
        var dIdx = 0
        var sIdx = 0
        val shuffledGrammar = grammarBank.shuffled(rnd)
        val shuffledDialogue = dialogueBank.shuffled(rnd)
        val shuffledSentence = sentenceBank.shuffled(rnd)

        // Pre-sample enough words for all the vocab-driven slots.
        val vocabSlots = plan.filterKeys { !isLessonType(it) }.values.sum()
        val picked = sample(pool, min(vocabSlots.coerceAtLeast(1), pool.size), mode.weaknessBias, rnd)
        var pIdx = 0
        fun nextWord(): WeakWord? {
            if (picked.isEmpty()) return null
            val w = picked[pIdx % picked.size]
            pIdx++
            return w
        }

        plan.forEach { (type, n) ->
            repeat(n) {
                val q: ExamQuestion? = when (type) {
                    ExamQType.GRAMMAR_MCQ, ExamQType.GRAMMAR_TF -> {
                        val item = shuffledGrammar.getOrNull(gIdx++)
                        if (item != null) grammarQuestion(item.first, item.second, allWords, rnd) else null
                    }
                    ExamQType.DIALOGUE_MCQ -> {
                        val item = shuffledDialogue.getOrNull(dIdx++)
                        if (item != null) dialogueQuestion(item.first, item.second, shuffledDialogue.map { it.second }, rnd) else null
                    }
                    ExamQType.ORDER_WORDS -> {
                        val s = shuffledSentence.getOrNull(sIdx++)
                        if (s != null) orderQuestion(s.first, s.second, rnd)
                        else nextWord()?.let { orderFromWord(it, rnd) }
                    }
                    else -> {
                        var made: ExamQuestion? = null
                        var tries = 0
                        while (made == null && tries < 6) {
                            val ww = nextWord() ?: break
                            val k = "${ww.word.id}:${type.name}"
                            if (k !in usedWordTypes) {
                                made = vocabQuestion(type, ww, allWords, rnd)
                                if (made != null) usedWordTypes.add(k)
                            }
                            tries++
                        }
                        made
                    }
                }
                if (q != null) questions.add(q)
            }
        }

        // ---- 5. Top up if some banks were empty ---------------------------
        if (questions.size < count) {
            val fallbacks = listOf(
                ExamQType.MCQ_MEANING, ExamQType.MCQ_REVERSE, ExamQType.WRITTEN_BLANK,
                ExamQType.MCQ_CLOZE, ExamQType.TRUE_FALSE, ExamQType.LISTENING_CHOICE,
            )
            var guard = 0
            while (questions.size < count && guard < count * 8) {
                guard++
                val ww = nextWord() ?: break
                val type = fallbacks[guard % fallbacks.size]
                val k = "${ww.word.id}:${type.name}"
                if (k in usedWordTypes) continue
                val q = vocabQuestion(type, ww, allWords, rnd)
                if (q != null) { usedWordTypes.add(k); questions.add(q) }
            }
        }

        // Interleave so the same type never runs three times in a row.
        return interleave(questions.take(count), rnd)
    }

    private fun isLessonType(t: ExamQType) = when (t) {
        ExamQType.GRAMMAR_MCQ, ExamQType.GRAMMAR_TF, ExamQType.DIALOGUE_MCQ, ExamQType.ORDER_WORDS -> true
        else -> false
    }

    /**
     * Turn a mode's relative weights into concrete counts, dropping types whose
     * source bank is empty and redistributing their share.
     */
    private fun plan(
        mode: ExamMode,
        count: Int,
        wordPool: Int,
        grammarBank: Int,
        dialogueBank: Int,
        sentenceBank: Int,
        words: List<VocabWord>,
    ): LinkedHashMap<ExamQType, Int> {
        val hasExamples = words.count { ExamText.contains(it.exampleEn, it.english) } >= 2
        val hasPhonetic = words.count { it.phonetic.isNotBlank() } >= 3
        val hasArabic = words.count { it.arabic.isNotBlank() && it.arabic != "—" } >= 3

        val viable = mode.mix.filterKeys { t ->
            when (t) {
                ExamQType.GRAMMAR_MCQ, ExamQType.GRAMMAR_TF -> grammarBank > 0
                ExamQType.DIALOGUE_MCQ -> dialogueBank > 0
                ExamQType.ORDER_WORDS -> sentenceBank > 0 || hasExamples
                ExamQType.MCQ_CLOZE, ExamQType.WRITTEN_BLANK -> hasExamples
                ExamQType.PHONETIC_MCQ -> hasPhonetic
                ExamQType.MCQ_MEANING, ExamQType.MCQ_REVERSE, ExamQType.WRITTEN_MEANING, ExamQType.TRUE_FALSE -> hasArabic
                else -> wordPool > 0
            }
        }.ifEmpty { mapOf(ExamQType.MCQ_MEANING to 1) }

        val totalWeight = viable.values.sum().coerceAtLeast(1)
        val out = LinkedHashMap<ExamQType, Int>()
        var assigned = 0
        viable.entries.sortedByDescending { it.value }.forEach { (t, w) ->
            val n = (count * w / totalWeight.toFloat()).toInt()
            if (n > 0) { out[t] = n; assigned += n }
        }
        // Hand out the remainder round-robin so the count is exact.
        val keys = viable.keys.toList()
        var i = 0
        while (assigned < count && keys.isNotEmpty()) {
            val t = keys[i % keys.size]
            out[t] = (out[t] ?: 0) + 1
            assigned++; i++
        }
        return out
    }

    /** Avoid long runs of one question type. */
    private fun interleave(qs: List<ExamQuestion>, rnd: Random): List<ExamQuestion> {
        if (qs.size < 4) return qs
        val byType = qs.groupBy { it.type }.mapValues { it.value.toMutableList() }.toMutableMap()
        val out = ArrayList<ExamQuestion>(qs.size)
        var lastType: ExamQType? = null
        while (out.size < qs.size) {
            val candidates = byType.filter { it.value.isNotEmpty() }
            if (candidates.isEmpty()) break
            // Prefer the biggest remaining bucket that differs from the last type.
            val pick = candidates.entries
                .sortedByDescending { it.value.size }
                .firstOrNull { it.key != lastType } ?: candidates.entries.first()
            out.add(pick.value.removeAt(0))
            lastType = pick.key
        }
        return out
    }

    // ------------------------------------------------------------------
    //  Question factories
    // ------------------------------------------------------------------

    private fun vocabQuestion(type: ExamQType, ww: WeakWord, all: List<VocabWord>, rnd: Random): ExamQuestion? {
        val w = ww.word
        val diff = when {
            ww.weakness >= 0.6f -> 3
            ww.weakness >= 0.35f -> 2
            else -> 1
        }
        val base = "q_${w.id}_${type.name}"
        val hasAr = w.arabic.isNotBlank() && w.arabic != "—"

        return when (type) {
            ExamQType.MCQ_MEANING -> {
                if (!hasAr) return null
                val wrong = distractors(all, w.id, 3, rnd) { it.arabic }
                if (wrong.size < 2) return null
                val (opts, ci) = optionsWith(w.arabic, wrong, rnd)
                ExamQuestion(
                    key = base, type = type, prompt = "ما معنى هذه الكلمة؟",
                    subject = w.english, subtitle = w.phonetic,
                    options = opts, correctIndex = ci,
                    explanationAr = if (w.exampleEn.isNotBlank()) "${w.exampleEn}\n${w.exampleAr}" else "",
                    audioText = w.english, wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = diff, weakness = ww.weakness,
                )
            }
            ExamQType.MCQ_REVERSE -> {
                if (!hasAr) return null
                val wrong = distractors(all, w.id, 3, rnd) { it.english }
                if (wrong.size < 2) return null
                val (opts, ci) = optionsWith(w.english, wrong, rnd)
                ExamQuestion(
                    key = base, type = type, prompt = "اختر الكلمة الإنجليزية الصحيحة",
                    subject = w.arabic,
                    options = opts, correctIndex = ci,
                    explanationAr = w.exampleEn, audioText = w.english,
                    wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = diff, weakness = ww.weakness,
                )
            }
            ExamQType.MCQ_CLOZE -> {
                if (!ExamText.contains(w.exampleEn, w.english)) return null
                val wrong = distractors(all, w.id, 3, rnd) { it.english }
                if (wrong.size < 2) return null
                val (opts, ci) = optionsWith(w.english, wrong, rnd)
                ExamQuestion(
                    key = base, type = type, prompt = "أكمل الجملة بالكلمة المناسبة",
                    subject = ExamText.blank(w.exampleEn, w.english),
                    subtitle = w.exampleAr,
                    options = opts, correctIndex = ci,
                    explanationAr = w.exampleEn, audioText = w.exampleEn,
                    wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = diff, weakness = ww.weakness,
                )
            }
            ExamQType.WRITTEN_BLANK -> {
                if (!ExamText.contains(w.exampleEn, w.english)) return null
                ExamQuestion(
                    key = base, type = type, prompt = "اكتب الكلمة الناقصة",
                    subject = ExamText.blank(w.exampleEn, w.english),
                    subtitle = if (hasAr) w.arabic else w.exampleAr,
                    correctText = w.english,
                    explanationAr = w.exampleEn, audioText = w.exampleEn,
                    wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = (diff + 1).coerceAtMost(3), weakness = ww.weakness,
                )
            }
            ExamQType.WRITTEN_MEANING -> {
                if (!hasAr) return null
                ExamQuestion(
                    key = base, type = type, prompt = "اكتب الكلمة الإنجليزية لهذا المعنى",
                    subject = w.arabic, subtitle = w.phonetic,
                    correctText = w.english,
                    explanationAr = w.exampleEn, audioText = w.english,
                    wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = (diff + 1).coerceAtMost(3), weakness = ww.weakness,
                )
            }
            ExamQType.TRUE_FALSE -> {
                if (!hasAr) return null
                val lie = rnd.nextBoolean()
                val shown = if (lie) {
                    distractors(all, w.id, 1, rnd) { it.arabic }.firstOrNull() ?: return null
                } else w.arabic
                val answerTrue = !lie
                val opts = listOf("صحيح", "خطأ")
                ExamQuestion(
                    key = base, type = type,
                    prompt = "هل هذا المعنى صحيح؟",
                    subject = w.english, subtitle = "= $shown",
                    options = opts, correctIndex = if (answerTrue) 0 else 1,
                    explanationAr = "${w.english} = ${w.arabic}", audioText = w.english,
                    wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = diff, weakness = ww.weakness,
                )
            }
            ExamQType.LISTENING_CHOICE -> {
                val wrong = distractors(all, w.id, 3, rnd) { it.english }
                if (wrong.size < 2) return null
                val (opts, ci) = optionsWith(w.english, wrong, rnd)
                ExamQuestion(
                    key = base, type = type, prompt = "استمع جيداً — ماذا سمعت؟",
                    subject = w.english, subtitle = w.phonetic, hideSubject = true,
                    options = opts, correctIndex = ci,
                    explanationAr = if (hasAr) w.arabic else "", audioText = w.english,
                    wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = (diff + 1).coerceAtMost(3), weakness = ww.weakness,
                )
            }
            ExamQType.LISTENING_WRITTEN -> ExamQuestion(
                key = base, type = type, prompt = "استمع واكتب ما سمعت",
                subject = w.english, subtitle = w.phonetic, hideSubject = true,
                correctText = w.english,
                explanationAr = if (hasAr) w.arabic else "", audioText = w.english,
                wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                difficulty = 3, weakness = ww.weakness,
            )
            ExamQType.PHONETIC_MCQ -> {
                if (w.phonetic.isBlank()) return null
                val wrong = distractors(all, w.id, 3, rnd) { it.english }
                if (wrong.size < 2) return null
                val (opts, ci) = optionsWith(w.english, wrong, rnd)
                ExamQuestion(
                    key = base, type = type, prompt = "أي كلمة تُنطق هكذا؟",
                    subject = w.phonetic,
                    options = opts, correctIndex = ci,
                    explanationAr = "${w.english} — ${w.arabic}", audioText = w.english,
                    wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
                    difficulty = diff, weakness = ww.weakness,
                )
            }
            ExamQType.ORDER_WORDS -> orderFromWord(ww, rnd)
            else -> null
        }
    }

    /** Sentence-building question from a word's own example. */
    private fun orderFromWord(ww: WeakWord, rnd: Random): ExamQuestion? {
        val w = ww.word
        val toks = ExamText.tokens(w.exampleEn)
        if (toks.size !in 3..12) return null
        return ExamQuestion(
            key = "q_${w.id}_ORDER", type = ExamQType.ORDER_WORDS,
            prompt = "رتّب الكلمات لتكوين جملة صحيحة",
            subject = w.exampleAr.ifBlank { w.arabic },
            options = toks.shuffled(rnd),
            correctText = toks.joinToString(" "),
            explanationAr = w.exampleAr, audioText = w.exampleEn,
            wordId = w.id, lessonId = w.lessonId, courseId = w.courseId,
            difficulty = 3, weakness = ww.weakness,
        )
    }

    private fun orderQuestion(l: Lesson, s: Sentence, rnd: Random): ExamQuestion? {
        val toks = ExamText.tokens(s.en)
        if (toks.size !in 3..12) return null
        return ExamQuestion(
            key = "q_l${l.id}_ORDER_${abs(s.en.hashCode())}", type = ExamQType.ORDER_WORDS,
            prompt = "رتّب الكلمات لتكوين جملة صحيحة",
            subject = s.ar,
            options = toks.shuffled(rnd),
            correctText = toks.joinToString(" "),
            explanationAr = s.ar, audioText = s.en,
            lessonId = l.id, courseId = l.courseId, difficulty = 3,
        )
    }

    /** Convert a lesson's own quiz item into an exam question. */
    private fun grammarQuestion(l: Lesson, item: QuizItem, all: List<VocabWord>, rnd: Random): ExamQuestion? {
        return when (item.type) {
            QuizType.TRUE_FALSE -> {
                val ansTrue = item.answer.trim().lowercase().let {
                    it == "true" || it == "صحيح" || it == "صح" || it == "t" || it == "yes"
                }
                ExamQuestion(
                    key = "q_l${l.id}_TF_${abs(item.question.hashCode())}",
                    type = ExamQType.GRAMMAR_TF,
                    prompt = "صحيح أم خطأ؟", subject = item.question,
                    options = listOf("صحيح", "خطأ"), correctIndex = if (ansTrue) 0 else 1,
                    explanationAr = item.explanationAr, audioText = item.question,
                    lessonId = l.id, courseId = l.courseId, difficulty = 2,
                )
            }
            QuizType.WRITTEN -> ExamQuestion(
                key = "q_l${l.id}_W_${abs(item.question.hashCode())}",
                type = ExamQType.GRAMMAR_MCQ,
                prompt = "أجب عن السؤال", subject = item.question,
                options = buildList {
                    add(item.answer)
                    addAll(distractors(all, -1, 3, rnd) { it.english })
                }.distinct().shuffled(rnd).let { it },
                correctIndex = -1, // fixed below
                explanationAr = item.explanationAr, audioText = item.question,
                lessonId = l.id, courseId = l.courseId, difficulty = 2,
            ).let { q -> q.copy(correctIndex = q.options.indexOf(item.answer)) }
                .takeIf { it.correctIndex >= 0 && it.options.size >= 2 }
            else -> {
                val opts = item.options.filter { it.isNotBlank() }
                if (opts.size < 2) return null
                var ci = opts.indexOfFirst { it.trim().equals(item.answer.trim(), ignoreCase = true) }
                if (ci < 0) {
                    // Some authors store the answer as an index ("2") or letter ("B").
                    val asNum = item.answer.trim().toIntOrNull()
                    ci = when {
                        asNum != null && asNum in 1..opts.size -> asNum - 1
                        asNum != null && asNum in 0 until opts.size -> asNum
                        item.answer.trim().length == 1 -> item.answer.trim().uppercase()[0] - 'A'
                        else -> -1
                    }
                }
                if (ci !in opts.indices) return null
                val shuffled = opts.shuffled(rnd)
                ExamQuestion(
                    key = "q_l${l.id}_G_${abs(item.question.hashCode())}",
                    type = ExamQType.GRAMMAR_MCQ,
                    prompt = "اختر الإجابة الصحيحة", subject = item.question,
                    options = shuffled, correctIndex = shuffled.indexOf(opts[ci]),
                    explanationAr = item.explanationAr, audioText = item.question,
                    lessonId = l.id, courseId = l.courseId, difficulty = 2,
                )
            }
        }
    }

    /** "Complete the dialogue" — hide one turn and offer plausible replies. */
    private fun dialogueQuestion(l: Lesson, d: Dialogue, allTurns: List<Dialogue>, rnd: Random): ExamQuestion? {
        if (d.en.isBlank()) return null
        val wrong = allTurns.filter { it.en != d.en && it.en.isNotBlank() }
            .shuffled(rnd).take(3).map { it.en }
        if (wrong.size < 2) return null
        val (opts, ci) = optionsWith(d.en, wrong, rnd)
        val speaker = d.speaker.ifBlank { "المتحدث" }
        return ExamQuestion(
            key = "q_l${l.id}_D_${abs(d.en.hashCode())}",
            type = ExamQType.DIALOGUE_MCQ,
            prompt = "ماذا قال $speaker؟ اختر الجملة الصحيحة",
            subject = d.ar.ifBlank { "أكمل الحوار" },
            subtitle = "من درس: ${l.title}",
            options = opts, correctIndex = ci,
            explanationAr = d.ar, audioText = d.en,
            lessonId = l.id, courseId = l.courseId, difficulty = 2,
        )
    }
}

/** One finished exam, kept for history + trend charts. */
data class ExamRecord(
    val id: String,
    val mode: ExamMode,
    val title: String,
    val correct: Int,
    val total: Int,
    val stamp: String,
    val durationMs: Long,
    /** skill → correct count */
    val skillCorrect: Map<ExamSkill, Int> = emptyMap(),
    /** skill → total count */
    val skillTotal: Map<ExamSkill, Int> = emptyMap(),
) {
    val pct: Int get() = if (total > 0) correct * 100 / total else 0
    val passed: Boolean get() = pct >= 60
}
