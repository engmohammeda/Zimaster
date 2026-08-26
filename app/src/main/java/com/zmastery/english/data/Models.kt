package com.zmastery.english.data

data class Level(
    val id: Int,
    val name: String,
    val description: String,
    val emoji: String,
)

enum class CourseType(val label: String, val icon: String) {
    VOCABULARY("المفردات", "book"),
    GRAMMAR("القواعد", "rule"),
    READING("القراءة", "read"),
    LISTENING("الاستماع", "listen"),
    CONVERSATION("المحادثة", "talk"),
    PHONETICS("الصوتيات", "sound"),
    WRITING("الكتابة", "write");

    companion object {
        fun fromKey(key: String): CourseType = when (key.lowercase().trim()) {
            "vocabulary", "vocab", "المفردات", "من الصفر" -> VOCABULARY
            "grammar", "القواعد" -> GRAMMAR
            "reading", "القراءة" -> READING
            "listening", "الاستماع" -> LISTENING
            "conversation", "المحادثة" -> CONVERSATION
            "phonetics", "الصوتيات" -> PHONETICS
            "writing", "الكتابة" -> WRITING
            else -> VOCABULARY
        }
    }
}

/**
 * بعد التوحيد (شاشة الدرس الموحّدة): لم يعد النمط يوجّه إلى شاشة خاصة.
 * دوره الآن ثلاثة أشياء فقط داخل نظام البلوكات:
 *   ١) شارة بصرية في بطاقة العنوان (Hero).
 *   ٢) ترتيب عرض البلوكات عبر [LessonBlocks.orderFor] — نفس المحتوى، سرد مختلف.
 *   ٣) لا يخفي ولا يُظهر أي محتوى — الظهور يقرره وجود البيانات نفسها.
 * أي كورس جديد مستقبلاً = بيانات JSON فقط، بلا أي كود واجهة.
 * (يُستخدم أيضاً خارج الدروس: زر «درس تجريبي» في شاشة الكورس الفارغة.)
 */
enum class LessonStyle {
    VOCAB_CARDS,        // من الصفر / bites — بطاقات كلمات
    GRAMMAR_RULES,      // القواعد — قواعد وأمثلة
    READING_TEXT,       // القراءة / book worm / i know — نص قرائي
    LISTENING_AUDIO,    // الاستماع — مشغل صوتي + نص
    CONVERSATION,       // المحادثة / coffe break — حوار
    PHONETICS_SOUNDS,   // الصوتيات — رموز صوتية
    WRITING_PRACTICE,   // الكتابة — تدريب كتابة
    STORY,              // story — قصة
    NEWS,               // الاخبار — خبر
    COMEDY,             // كوميدي — مقطع كوميدي
    IDIOMS,             // idioms podium — تعابير
    EXAM_PREP,          // Ielts — تحضير امتحان
    THINKING,           // التفكير — تمرين تفكير
    CULTURE,            // الغرب / شفرة أمريكا — ثقافة
}

data class Course(
    val id: Int,
    val levelId: Int,
    val name: String,
    val type: CourseType,
    val target: Int,
    val accent: Long,
    val style: LessonStyle = LessonStyle.VOCAB_CARDS,
    // Stable identifier used to match imported JSON to this exact course.
    // MUST stay constant forever so lessons never collide across courses.
    val key: String = "",
    // The author's `course_id` used in the per-lesson JSON files (e.g. "zero_to_hero").
    val jsonId: String = "",
)

data class Lesson(
    val id: Int,
    val courseId: Int,
    val no: Int,
    val title: String,
    val summaryAr: String,
    val readingEn: String,
    val readingAr: String,
    val keyPoints: List<String>,
    var isCompleted: Boolean = false,
    val dialogues: List<Dialogue> = emptyList(),
    val newWordIds: List<Int> = emptyList(),
    // ----- Conversation-course content -----
    val keyExpressions: List<KeyExpression> = emptyList(),
    // ----- Rich lesson content (from JSON) -----
    val keySentences: List<Sentence> = emptyList(),
    val notes: List<String> = emptyList(),
    val quiz: List<QuizItem> = emptyList(),
    // ----- Grammar-course content -----
    val explanationAr: String = "",
    val logicAr: String = "",
    val examples: List<Sentence> = emptyList(),
    // ----- Reading-course content -----
    val fullTextEn: String = "",
    val fullTextAr: String = "",
    val segments: List<Sentence> = emptyList(),
    // ----- Writing-course content -----
    val topicEn: String = "",
    val topicAr: String = "",
    val brainstorming: List<BrainstormQ> = emptyList(),
    val guidedSentences: List<Sentence> = emptyList(),
    val finalDraft: Sentence? = null,
    // ----- Lesson-level spaced repetition -----
    var reviewCount: Int = 0,
    var lastMastery: Int = 0,      // last self-rated recall % (0..100)
    var dueInDays: Int = 0,        // days until this lesson is due for review
    var intervalDays: Int = 0,
    // Verbatim JSON the author uploaded — lets specialized viewers render every
    // field (phonetics focus_sounds, minimal_pairs, etc.) without data loss.
    val rawJson: String = "",
    // ----- AI audio generation state (for reading/listening/story content) -----
    var audioReady: Boolean = false,
) {
    /** A completed lesson becomes due for review as its interval elapses. */
    val needsReview: Boolean get() = isCompleted && dueInDays <= 0
}

data class Sentence(val en: String, val ar: String)

/** A key expression from a conversation lesson (`key_expressions`). */
data class KeyExpression(
    val expressionEn: String,
    val expressionAr: String,
    val usageAr: String = "",
)

data class BrainstormQ(
    val questionEn: String,
    val questionAr: String = "",
    val suggestedAnswerEn: String = "",
    val suggestedAnswerAr: String = "",
)

data class Dialogue(val speaker: String, val en: String, val ar: String)

enum class QuizType { MULTIPLE_CHOICE, TRUE_FALSE, WRITTEN }

/** A quiz item embedded inside a lesson (from the lesson JSON `quiz` array). */
data class QuizItem(
    val type: QuizType,
    val question: String,
    val options: List<String> = emptyList(),
    val answer: String,
    val explanationAr: String = "",
    /**
     * For `audio_quiz` items (phonetics course): the word the learner must
     * HEAR before choosing. Captured from the JSON `word_to_speak` field so a
     * listen button can be rendered next to the question.
     */
    val audioText: String = "",
)

enum class ReviewState(val label: String) { NEW("جديدة"), REVIEWING("قيد المراجعة"), SAVED("محفوظة") }

enum class ThemeMode(val label: String) { SYSTEM("حسب النظام"), LIGHT("نهاري"), DARK("ليلي") }

/** Controls how vocab cards inside a lesson are revealed. */
enum class RevealMode(val label: String, val desc: String) {
    FULL("كشف كامل", "إظهار الكلمة والجملة والمعنى مباشرة"),
    WORD_ONLY("الكلمة فقط", "إظهار الكلمة فقط والنقر للكشف"),
}

data class VocabWord(
    val id: Int,
    val english: String,
    val arabic: String,
    val exampleEn: String,
    val exampleAr: String,
    val phonetic: String,
    val mentalImage: String = "",
    val courseId: Int = 0,
    // ----- FSRS memory state -----
    var stability: Double = 0.0,       // days for R to fall from 100% → 90%
    var difficulty: Double = 0.0,      // 1 (easy) .. 10 (hard)
    var phase: FsrsPhase = FsrsPhase.NEW,
    var dueInDays: Int = 0,            // days until next review (0 = due now)
    var intervalDays: Int = 0,         // last scheduled interval
    var lastReviewedDay: Long = -1,    // epoch-day of last review (-1 = never)
    var repetitions: Int = 0,          // successful reviews in a row
    var mastered: Boolean = false,     // stability high enough to be "learned"
    // ----- Rich analytics (feeds SRS tuning + coach) -----
    var listenCount: Int = 0,          // total audio replays across sessions
    var totalReviews: Int = 0,         // total review events
    var lapses: Int = 0,               // times fully forgotten (Again)
    var lastRecall: RecallSource = RecallSource.NONE,
    var lastRetrievability: Double = 0.0, // R at the moment of last review
    var avgRecallStage: Float = 0f,    // avg stage (1-4) recalled at; lower = stronger
    var totalTimeMs: Long = 0L,        // cumulative review time
    var lastGrade: Int = 0,            // last FSRS rating 1..4
    // Pending words are staged from a lesson but NOT yet in the active dictionary
    // until the learner approves them on lesson completion.
    var pendingApproval: Boolean = true,
    val lessonId: Int = 0,             // lesson this word came from
    // ----- AI audio generation state -----
    var wordAudioReady: Boolean = false,      // pronunciation audio generated
    var exampleAudioReady: Boolean = false,   // example sentence audio generated
) {
    /** True when both the word and its example have generated audio. */
    val audioReady: Boolean get() = wordAudioReady && (exampleEn.isBlank() || exampleAudioReady)

    val state: ReviewState
        get() = when {
            mastered -> ReviewState.SAVED
            totalReviews > 0 -> ReviewState.REVIEWING
            else -> ReviewState.NEW
        }

    /** Memory strength 0..1 derived from FSRS stability. */
    val strength: Float
        get() = com.zmastery.english.data.Fsrs.strengthFromStability(stability)

    /** Legacy easiness surrogate for older UI (difficulty → 1.3..2.7 ease). */
    val easiness: Double
        get() = if (difficulty <= 0) 2.5 else (2.7 - (difficulty - 1.0) / 9.0 * 1.4)
}

enum class FsrsPhase { NEW, LEARNING, REVIEW, RELEARNING }

/**
 * How the learner recalled the word. Maps directly to an FSRS rating (1..4):
 *  1 = Again · 2 = Hard · 3 = Good · 4 = Easy.
 */
enum class RecallSource(val label: String, val emoji: String, val grade: Int) {
    NONE("—", "", 3),
    SOUND("من الصوت", "\uD83D\uDD0A", 4),          // recalled from audio alone → Easy
    IMAGE("من الصورة", "\uD83D\uDDBC\uFE0F", 3),   // recalled from mental image → Good
    TEXT("من النص", "\uD83D\uDCDD", 2),            // needed the word/example → Hard
    STUDIED("تذكرت أني درستها", "\uD83D\uDCA1", 2),// vague familiarity → Hard
    FAILED("نسيتها تماماً", "\u274C", 1),           // forgot even after reading → Again
}

/** One review event — the raw data logged for study improvement & FSRS tuning. */
data class ReviewLog(
    val wordId: Int,
    val recall: RecallSource,
    val grade: Int,            // FSRS rating 1..4
    val reachedStage: Int,     // 1..4 — how far the learner needed to go
    val replays: Int,          // audio replays this session
    val timeMs: Long,          // time spent on the card
    val retrievability: Double,// predicted R before this review
    val stabilityAfter: Double,
    val intervalAfter: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

data class Story(
    val id: Int,
    val title: String,
    val date: String,
    val en: String,
    val ar: String,
    val words: List<String>,
    var audioReady: Boolean = false,
)

enum class QuizKind { MEANING, EXAMPLE, SPELLING }

data class QuizQuestion(
    val prompt: String,
    val promptSub: String,
    val options: List<String>,
    val correctIndex: Int,
    val kind: QuizKind,
)

data class DailyActivity(val label: String, val reviews: Int)

data class DailyTask(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val target: Int,
    var progress: Int = 0,
) {
    val done: Boolean get() = progress >= target
}

data class PlanItem(
    val courseId: Int,
    val courseName: String,
    val lessonId: Int,
    val lessonTitle: String,
    val accent: Long,
)
