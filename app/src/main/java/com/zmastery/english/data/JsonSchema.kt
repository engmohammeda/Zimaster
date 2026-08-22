package com.zmastery.english.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Unified JSON schema — the single source of truth for imported courses.
 *
 * IMPORTANT: `course_key` is the STABLE identifier. It must exactly match one
 * of the fixed course keys defined in SampleData.courses (e.g. "l1_scratch",
 * "l2_bites", "l3_idioms"). This is what guarantees lessons are attached to
 * the right course and never collide across courses/levels.
 */
@Serializable
data class CoursePackage(
    @SerialName("course_key") val courseKey: String = "",
    @SerialName("course_id") val courseId: String = "",
    @SerialName("course_name") val courseName: String = "",
    @SerialName("course_type") val courseType: String = "vocabulary",
    val style: String = "",
    val level: Int = 1,
    @SerialName("level_name") val levelName: String = "",
    val target: Int = 20,
    val lessons: List<JsonLesson> = emptyList(),
)

@Serializable
data class JsonLesson(
    @SerialName("lesson_id") val lessonId: String = "",
    @SerialName("lesson_title") val title: String = "",
    @SerialName("lesson_no") val no: Int = 1,
    @SerialName("summary_ar") val summaryAr: String = "",
    @SerialName("reading_en") val readingEn: String = "",
    @SerialName("reading_ar") val readingAr: String = "",
    @SerialName("key_points") val keyPoints: List<String> = emptyList(),
    val words: List<JsonWord> = emptyList(),
    val dialogues: List<JsonDialogue> = emptyList(),
    @SerialName("grammar_rules") val grammarRules: List<JsonGrammarRule> = emptyList(),
    val phonetics: List<JsonPhonetic> = emptyList(),
    @SerialName("audio_url") val audioUrl: String = "",
    @SerialName("video_url") val videoUrl: String = "",
)

@Serializable
data class JsonGrammarRule(
    val rule: String = "",
    @SerialName("rule_ar") val ruleAr: String = "",
    val examples: List<String> = emptyList(),
)

@Serializable
data class JsonPhonetic(
    val symbol: String = "",
    val examples: String = "",
    @SerialName("description_ar") val descriptionAr: String = "",
)

@Serializable
data class JsonWord(
    val word: String = "",
    @SerialName("phonetic") val phonetic: String = "",
    val example: String = "",
    @SerialName("example_ar") val exampleAr: String = "",
    val translation: String = "",
    @SerialName("mental_image") val mentalImage: String = "",
)

@Serializable
data class JsonDialogue(
    val speaker: String = "",
    val en: String = "",
    val ar: String = "",
)

// ==========================================================================
// Per-lesson JSON format (the real content format the author uploads).
// Each JSON file is ONE lesson, matched to a course via metadata.course_id.
// ==========================================================================

@Serializable
data class LessonPackage(
    val metadata: LessonMeta = LessonMeta(),
    @SerialName("lesson_content") val lessonContent: LessonContent = LessonContent(),
    @SerialName("global_vocabulary") val globalVocabulary: List<JsonGlobalWord> = emptyList(),
    @SerialName("lesson_notes") val lessonNotes: List<String> = emptyList(),
    val quiz: List<JsonQuiz> = emptyList(),
)

@Serializable
data class LessonMeta(
    @SerialName("course_id") val courseId: String = "",
    @SerialName("course_name_ar") val courseNameAr: String = "",
    val level: Int = 1,
    @SerialName("lesson_no") val lessonNo: Int = 1,
    val title: String = "",
)

@Serializable
data class LessonContent(
    @SerialName("key_sentences") val keySentences: List<JsonSentence> = emptyList(),
    // Conversation-course format
    val dialogue: List<JsonDialogue> = emptyList(),
    @SerialName("key_expressions") val keyExpressions: List<JsonKeyExpression> = emptyList(),
    // Grammar-course format
    @SerialName("explanation_ar") val explanationAr: String = "",
    @SerialName("logic_ar") val logicAr: String = "",
    val examples: List<JsonSentence> = emptyList(),
    // Reading-course format
    @SerialName("full_text_en") val fullTextEn: String = "",
    @SerialName("full_text_ar") val fullTextAr: String = "",
    val segments: List<JsonSentence> = emptyList(),
    // ----- Writing-course format -----
    @SerialName("topic_en") val topicEn: String = "",
    @SerialName("topic_ar") val topicAr: String = "",
    @SerialName("brainstorming_questions") val brainstormingQuestions: List<JsonBrainstorm> = emptyList(),
    @SerialName("guided_sentences") val guidedSentences: List<JsonSentence> = emptyList(),
    @SerialName("final_draft") val finalDraft: JsonSentence = JsonSentence(),
)

@Serializable
data class JsonKeyExpression(
    @SerialName("expression_en") val expressionEn: String = "",
    @SerialName("expression_ar") val expressionAr: String = "",
    @SerialName("usage_ar") val usageAr: String = "",
)

@Serializable
data class JsonBrainstorm(
    @SerialName("question_en") val questionEn: String = "",
    @SerialName("question_ar") val questionAr: String = "",
    @SerialName("suggested_answer_en") val suggestedAnswerEn: String = "",
    @SerialName("suggested_answer_ar") val suggestedAnswerAr: String = "",
)

@Serializable
data class JsonSentence(val en: String = "", val ar: String = "")

@Serializable
data class JsonGlobalWord(
    val word: String = "",
    val meaning: String = "",
    @SerialName("example_en") val exampleEn: String = "",
    @SerialName("example_ar") val exampleAr: String = "",
    val phonetic: String = "",
    @SerialName("mental_image") val mentalImage: String = "",
)

@Serializable
data class JsonQuiz(
    val type: String = "multiple_choice",
    val question: String = "",
    val options: List<String> = emptyList(),
    val answer: String = "",
    @SerialName("explanation_ar") val explanationAr: String = "",
    /** phonetics audio_quiz: the word to pronounce aloud (nullable in some files). */
    @SerialName("word_to_speak") val wordToSpeak: String? = null,
)

object ImportEngine {
    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    data class ImportResult(
        val success: Boolean,
        val message: String,
        val pkg: CoursePackage? = null,
        val matchedCourse: Course? = null,
        val lessonCount: Int = 0,
        val wordCount: Int = 0,
    )

    // ---- Per-lesson import result ----
    data class LessonImportResult(
        val success: Boolean,
        val message: String,
        val pkg: LessonPackage? = null,
        val matchedCourse: Course? = null,
    )

    // ---- Multi-lesson import result (batch of per-lesson packages) ----
    data class MultiLessonImportResult(
        val success: Boolean,
        val message: String,
        val packages: List<LessonPackage> = emptyList(),
    )

    /** True if the raw JSON looks like a batch: a top-level array, or a
     *  { "lessons": [ ... ] } wrapper whose items each carry a metadata block. */
    fun looksLikeMultiLesson(raw: String): Boolean {
        val t = raw.trim()
        if (t.startsWith("[")) return true
        // wrapper object with a lessons array of per-lesson objects
        return t.startsWith("{") &&
            t.contains("\"lessons\"") &&
            t.contains("\"metadata\"") &&
            t.contains("course_id")
    }

    @Serializable
    private data class LessonBatch(val lessons: List<LessonPackage> = emptyList())

    /**
     * Parse many raw JSON blobs at once (e.g. several uploaded .json files, or
     * entries extracted from a .zip). Each blob may itself be:
     *   • a single per-lesson object           { "metadata": ... }
     *   • an array of per-lesson objects        [ {...}, {...} ]
     *   • a { "lessons": [ ... ] } wrapper
     * All valid lessons are merged into one batch result.
     */
    fun parseRawList(raws: List<String>): MultiLessonImportResult {
        if (raws.isEmpty()) return MultiLessonImportResult(false, "لم يتم العثور على أي ملف JSON")
        val all = mutableListOf<LessonPackage>()
        val problems = mutableListOf<String>()
        raws.forEachIndexed { i, raw ->
            val t = raw.trim()
            if (t.isEmpty()) return@forEachIndexed
            try {
                val parsed: List<LessonPackage> = when {
                    t.startsWith("[") -> json.decodeFromString(t)
                    t.startsWith("{") && t.contains("\"lessons\"") && t.contains("\"metadata\"") ->
                        json.decodeFromString<LessonBatch>(t).lessons
                    else -> listOf(json.decodeFromString(t))
                }
                all += parsed
            } catch (e: Exception) {
                problems += "الملف ${i + 1}: ${e.message?.take(60) ?: "تنسيق غير صالح"}"
            }
        }

        if (all.isEmpty()) {
            val why = if (problems.isEmpty()) "لم يُعثر على دروس صالحة" else problems.joinToString("\n")
            return MultiLessonImportResult(false, "فشل الاستيراد:\n$why")
        }

        // Validate merged lessons.
        all.forEachIndexed { i, p ->
            if (p.metadata.courseId.isBlank() && p.metadata.courseNameAr.isBlank())
                return MultiLessonImportResult(false, "الدرس رقم ${i + 1}: الحقل metadata.course_id مطلوب")
            if (p.metadata.title.isBlank())
                return MultiLessonImportResult(false, "الدرس رقم ${i + 1}: الحقل metadata.title مطلوب")
        }

        val byCourse = all.groupBy { it.metadata.courseNameAr.ifBlank { it.metadata.courseId } }
        val totalWords = all.sumOf { it.globalVocabulary.size }
        val lines = byCourse.entries.joinToString("\n") { (c, ls) -> "• «$c»: ${ls.size} درس" }
        val warn = if (problems.isEmpty()) "" else "\n⚠️ تم تخطي ${problems.size} ملف غير صالح"
        return MultiLessonImportResult(
            true,
            "تم التحقق بنجاح ✓\n${all.size} درس في ${byCourse.size} كورس · $totalWords كلمة\n$lines$warn",
            all,
        )
    }

    /**
     * Parse many lessons at once. Accepts either:
     *   [ {lesson}, {lesson}, ... ]                (a JSON array)
     *   { "lessons": [ {lesson}, ... ] }           (a wrapper object)
     * Each item must be a valid per-lesson package (metadata + content).
     */
    fun parseMultiLesson(raw: String): MultiLessonImportResult {
        val text = raw.trim()
        if (text.isEmpty()) return MultiLessonImportResult(false, "النص فارغ — الصق كود JSON صالح")
        return try {
            val list: List<LessonPackage> = if (text.startsWith("[")) {
                json.decodeFromString(text)
            } else {
                json.decodeFromString<LessonBatch>(text).lessons
            }
            if (list.isEmpty()) return MultiLessonImportResult(false, "لم يتم العثور على أي درس داخل الملف")

            // Validate each lesson.
            list.forEachIndexed { i, p ->
                if (p.metadata.courseId.isBlank() && p.metadata.courseNameAr.isBlank())
                    return MultiLessonImportResult(false, "الدرس رقم ${i + 1}: الحقل metadata.course_id مطلوب")
                if (p.metadata.title.isBlank())
                    return MultiLessonImportResult(false, "الدرس رقم ${i + 1}: الحقل metadata.title مطلوب")
            }

            // Summarise grouped by course.
            val byCourse = list.groupBy { it.metadata.courseNameAr.ifBlank { it.metadata.courseId } }
            val totalWords = list.sumOf { it.globalVocabulary.size }
            val lines = byCourse.entries.joinToString("\n") { (c, ls) -> "• «$c»: ${ls.size} درس" }
            MultiLessonImportResult(
                true,
                "تم التحقق بنجاح ✓\n${list.size} درس في ${byCourse.size} كورس · $totalWords كلمة\n$lines",
                list,
            )
        } catch (e: Exception) {
            MultiLessonImportResult(false, "خطأ في تنسيق JSON: ${e.message?.take(90) ?: "غير معروف"}")
        }
    }

    /**
     * Parse a single-lesson JSON file (metadata / lesson_content / global_vocabulary
     * / lesson_notes / quiz). Detected automatically when a "metadata" object exists.
     */
    fun parseLesson(raw: String): LessonImportResult {
        val text = raw.trim()
        if (text.isEmpty()) return LessonImportResult(false, "النص فارغ — الصق كود JSON صالح")
        if (!text.startsWith("{")) return LessonImportResult(false, "يجب أن يبدأ الكود بـ { لكائن JSON")
        return try {
            val pkg = json.decodeFromString<LessonPackage>(text)
            val id = pkg.metadata.courseId
            if (id.isBlank() && pkg.metadata.courseNameAr.isBlank()) {
                return LessonImportResult(false, "الحقل metadata.course_id أو course_name_ar مطلوب")
            }
            if (pkg.metadata.title.isBlank()) return LessonImportResult(false, "الحقل metadata.title مطلوب")
            val matched = SampleData.resolveCourse(id, pkg.metadata.courseNameAr, pkg.metadata.level)
            val vocabCount = pkg.globalVocabulary.size
            // Detect rich content sections captured from the raw JSON.
            val extras = mutableListOf<String>()
            if (text.contains("focus_sounds")) extras += "أصوات"
            if (text.contains("minimal_pairs")) extras += "أزواج صغرى"
            if (text.contains("practice_scripts")) extras += "جُمل تدريب"
            if (pkg.lessonContent.keySentences.isNotEmpty()) extras += "${pkg.lessonContent.keySentences.size} جملة"
            if (pkg.lessonNotes.isNotEmpty()) extras += "${pkg.lessonNotes.size} ملاحظة"
            val courseLabel = matched?.let { "«${it.name}» (المستوى ${it.levelId})" }
                ?: "«${pkg.metadata.courseNameAr.ifBlank { id }}» (سيُنشأ كورس جديد)"
            val extrasLine = if (extras.isEmpty()) "" else "\nالمحتوى: ${extras.joinToString(" · ")}"
            LessonImportResult(
                true,
                "تم التحقق بنجاح ✓\nالكورس: $courseLabel\nالدرس ${pkg.metadata.lessonNo}: ${pkg.metadata.title}\n$vocabCount كلمة · ${pkg.quiz.size} سؤال$extrasLine",
                pkg, matched,
            )
        } catch (e: Exception) {
            LessonImportResult(false, "خطأ في تنسيق JSON: ${e.message?.take(90) ?: "غير معروف"}")
        }
    }

    /** True if the raw JSON looks like a per-lesson file (has a metadata block). */
    fun looksLikeLesson(raw: String): Boolean {
        val t = raw.trim()
        return t.startsWith("{") && t.contains("\"metadata\"") && t.contains("course_id")
    }

    /** Parse + validate a single JSON course package. */
    fun parse(raw: String): ImportResult {
        val text = raw.trim()
        if (text.isEmpty()) return ImportResult(false, "النص فارغ — الصق كود JSON صالح")
        if (!text.startsWith("{")) return ImportResult(false, "يجب أن يبدأ الكود بـ { لكائن JSON")
        return try {
            val pkg = json.decodeFromString<CoursePackage>(text)
            // Resolve the stable course by its key.
            val matched = if (pkg.courseKey.isNotBlank()) SampleData.courseByKey(pkg.courseKey) else null
            val errors = validate(pkg, matched)
            if (errors != null) return ImportResult(false, errors)
            val words = pkg.lessons.sumOf { it.words.size }
            val courseLabel = matched?.let { "«${it.name}» (المستوى ${it.levelId})" } ?: "«${pkg.courseName}» (كورس جديد)"
            ImportResult(
                true,
                "تم التحقق بنجاح ✓\nالكورس: $courseLabel\n${pkg.lessons.size} درس · $words كلمة",
                pkg, matched, pkg.lessons.size, words,
            )
        } catch (e: Exception) {
            ImportResult(false, "خطأ في تنسيق JSON: ${e.message?.take(80) ?: "غير معروف"}")
        }
    }

    private fun validate(pkg: CoursePackage, matched: Course?): String? {
        // Require a stable key OR a course name for a brand-new course.
        if (pkg.courseKey.isBlank() && pkg.courseName.isBlank()) {
            return "يجب تحديد course_key ثابت (مثل l1_scratch) أو course_name لكورس جديد"
        }
        if (pkg.courseKey.isNotBlank() && matched == null) {
            return "المعرّف course_key = \"${pkg.courseKey}\" غير معروف.\nاستخدم أحد المعرّفات الثابتة للكورسات."
        }
        if (pkg.lessons.isEmpty()) return "يجب أن يحتوي الكورس على درس واحد على الأقل"
        pkg.lessons.forEachIndexed { i, l ->
            if (l.title.isBlank()) return "الدرس رقم ${i + 1} بدون عنوان (lesson_title)"
        }
        return null
    }

    /** All fixed course keys — shown in the import screen as reference. */
    fun courseKeyReference(): List<Triple<String, String, Int>> =
        SampleData.courses.map { Triple(it.key, it.name, it.levelId) }

}
