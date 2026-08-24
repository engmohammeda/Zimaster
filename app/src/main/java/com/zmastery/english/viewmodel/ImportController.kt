package com.zmastery.english.viewmodel

import com.zmastery.english.data.*

/**
 * Controller for JSON lesson import (delta update) + the quiz/colour/style
 * helpers used by it. The shared content lists + id counters stay on
 * [AppViewModel]; this class owns the import algorithm. See [ExamsController]
 * for conventions.
 */
internal class ImportController(internal val vm: AppViewModel) {

    private val courses get() = vm.courses
    private val vocab get() = vm.vocab
    private val lessons get() = vm.lessons
    private var nextCourseId
        get() = vm.nextCourseId
        set(v) { vm.nextCourseId = v }
    private var nextLessonId
        get() = vm.nextLessonId
        set(v) { vm.nextLessonId = v }
    private var nextWordId
        get() = vm.nextWordId
        set(v) { vm.nextWordId = v }
    private var lastImportSummary
        get() = vm.lastImportSummary
        set(v) { vm.lastImportSummary = v }
    private val activeVocab get() = vm.activeVocab

    /** Map a lesson-JSON quiz array to domain items (shared by import + self-heal). */
    private fun mapJsonQuiz(items: List<JsonQuiz>): List<QuizItem> = items.map { q ->
        QuizItem(
            type = when (q.type.lowercase().trim()) {
                "true_false", "true/false", "tf" -> QuizType.TRUE_FALSE
                "written", "write", "translation" -> QuizType.WRITTEN
                else -> QuizType.MULTIPLE_CHOICE
            },
            question = q.question,
            options = q.options,
            answer = q.answer,
            explanationAr = q.explanationAr,
            audioText = q.wordToSpeak?.trim().orEmpty(),
        )
    }

    fun importPackage(pkg: CoursePackage): String {
        val type = CourseType.fromKey(pkg.courseType)
        // 1) stable course_key  2) course_id/jsonId  3) name+level  (all tolerant)
        val byKey = if (pkg.courseKey.isNotBlank()) SampleData.courseByKey(pkg.courseKey) else null
        val byId = byKey ?: (if (pkg.courseId.isNotBlank()) SampleData.courseByJsonId(pkg.courseId) else null)
        val existing = byId ?: SampleData.resolveCourse(pkg.courseName, pkg.courseName, pkg.level)
            ?: courses.firstOrNull { it.name == pkg.courseName && it.levelId == pkg.level }
        val course = existing ?: Course(
            id = nextCourseId++,
            levelId = pkg.level.coerceIn(1, 3),
            name = pkg.courseName.ifBlank { "كورس مستورد" },
            type = type,
            target = if (pkg.target > 0) pkg.target else pkg.lessons.size,
            accent = accentFor(type),
            style = styleFor(pkg.style, type),
            key = pkg.courseKey.trim(),
        ).also { courses.add(it) }

        var addedLessons = 0
        var addedWords = 0
        var updatedLessons = 0

        pkg.lessons.forEach { jl ->
            val wordIds = mutableListOf<Int>()
            val lessonIdForWords = nextLessonId
            jl.words.forEach { jw ->
                if (jw.word.isNotBlank()) {
                    val id = nextWordId++
                    vocab.add(
                        VocabWord(
                            id = id,
                            english = jw.word,
                            arabic = jw.translation.ifBlank { "—" },
                            exampleEn = jw.example,
                            exampleAr = jw.exampleAr,
                            phonetic = jw.phonetic,
                            mentalImage = jw.mentalImage,
                            courseId = course.id,
                            pendingApproval = true,
                            lessonId = lessonIdForWords,
                        )
                    )
                    wordIds.add(id)
                    addedWords++
                }
            }

            // Build key points from explicit list or grammar rules.
            val keyPoints = jl.keyPoints.ifEmpty {
                jl.grammarRules.map { r -> if (r.ruleAr.isNotBlank()) "${r.rule} — ${r.ruleAr}" else r.rule }
            }

            val newLesson = Lesson(
                id = nextLessonId++,
                courseId = course.id,
                no = if (jl.no > 0) jl.no else (lessons.filter { it.courseId == course.id }.maxOfOrNull { it.no } ?: 0) + 1,
                title = jl.title,
                summaryAr = jl.summaryAr,
                readingEn = jl.readingEn,
                readingAr = jl.readingAr,
                keyPoints = keyPoints,
                dialogues = jl.dialogues.map { Dialogue(it.speaker, it.en, it.ar) },
                newWordIds = wordIds,
            )

            // Delta update: replace a lesson with the same course+lesson_no, else add.
            val dupIdx = lessons.indexOfFirst { it.courseId == course.id && it.no == newLesson.no }
            if (dupIdx >= 0) {
                lessons[dupIdx] = newLesson
                updatedLessons++
            } else {
                lessons.add(newLesson)
                addedLessons++
            }
        }
        val summary = buildString {
            append("تم استيراد «${course.name}» (المستوى ${course.levelId})\n")
            append("جديد: $addedLessons درس")
            if (updatedLessons > 0) append(" · محدّث: $updatedLessons")
            append(" · $addedWords كلمة")
        }
        lastImportSummary = summary
        // Newly imported reading lessons must appear in the story archive at once.
        vm.syncLessonStories()
        vm.persist()
        return summary
    }

    /**
     * Import ONE lesson from the author's per-lesson JSON. Matched to a fixed
     * curriculum course via metadata.course_id. Delta-updates a lesson with the
     * same course + lesson_no. Runs [syncLessonStories] + persist immediately.
     */
    fun importLesson(pkg: LessonPackage, rawJson: String = ""): String {
        val summary = importLessonCore(pkg, rawJson)
        vm.syncLessonStories()
        vm.persist()
        return summary
    }

    /**
     * Core delta-import logic shared by [importLesson] and [importLessons].
     * Pure in-memory mutation only — no syncLessonStories / persist call, so a
     * batch of N lessons does this ONCE at the end instead of N times.
     */
    private fun importLessonCore(pkg: LessonPackage, rawJson: String = ""): String {
        // Match a curriculum course; if unknown, auto-create one so import never fails.
        val course = SampleData.resolveCourse(pkg.metadata.courseId, pkg.metadata.courseNameAr, pkg.metadata.level)
            ?: courses.firstOrNull { it.jsonId.equals(pkg.metadata.courseId, true) || it.name == pkg.metadata.courseNameAr }
            ?: Course(
                id = nextCourseId++,
                levelId = pkg.metadata.level.coerceIn(1, 3),
                name = pkg.metadata.courseNameAr.ifBlank { pkg.metadata.courseId.ifBlank { "كورس مستورد" } },
                type = CourseType.VOCABULARY,
                target = 20,
                accent = 0xFFE07856,
                style = LessonStyle.VOCAB_CARDS,
                jsonId = pkg.metadata.courseId,
            ).also { courses.add(it) }

        // Register global vocabulary as SRS words linked to this course.
        val wordIds = mutableListOf<Int>()
        val lessonIdForWords = nextLessonId // the lesson we're about to create
        pkg.globalVocabulary.forEach { gw ->
            if (gw.word.isNotBlank()) {
                val id = nextWordId++
                vocab.add(
                    VocabWord(
                        id = id,
                        english = gw.word,
                        arabic = gw.meaning.ifBlank { "—" },
                        exampleEn = gw.exampleEn,
                        exampleAr = gw.exampleAr,
                        phonetic = gw.phonetic,
                        mentalImage = gw.mentalImage,
                        courseId = course.id,
                        pendingApproval = true,
                        lessonId = lessonIdForWords,
                    )
                )
                wordIds.add(id)
            }
        }

        val quiz = mapJsonQuiz(pkg.quiz)

        val sentences = pkg.lessonContent.keySentences.map { Sentence(it.en, it.ar) }
        val segments = pkg.lessonContent.segments.map { Sentence(it.en, it.ar) }
        val examples = pkg.lessonContent.examples.map { Sentence(it.en, it.ar) }
        val keyPoints = pkg.globalVocabulary.filter { it.word.isNotBlank() }
            .map { "${it.word} = ${it.meaning}" }

        val no = if (pkg.metadata.lessonNo > 0) pkg.metadata.lessonNo
            else (lessons.filter { it.courseId == course.id }.maxOfOrNull { it.no } ?: 0) + 1

        val newLesson = Lesson(
            id = nextLessonId++,
            courseId = course.id,
            no = no,
            title = pkg.metadata.title,
            summaryAr = pkg.lessonContent.explanationAr.ifBlank { pkg.lessonNotes.firstOrNull() ?: "" },
            readingEn = pkg.lessonContent.fullTextEn.ifBlank { sentences.joinToString(" ") { it.en } },
            readingAr = pkg.lessonContent.fullTextAr.ifBlank { sentences.joinToString(" ") { it.ar } },
            keyPoints = keyPoints,
            keySentences = sentences,
            dialogues = pkg.lessonContent.dialogue.map { Dialogue(it.speaker, it.en, it.ar) },
            keyExpressions = pkg.lessonContent.keyExpressions.map {
                KeyExpression(it.expressionEn, it.expressionAr, it.usageAr)
            },
            notes = pkg.lessonNotes,
            quiz = quiz,
            newWordIds = wordIds,
            explanationAr = pkg.lessonContent.explanationAr,
            logicAr = pkg.lessonContent.logicAr,
            examples = examples,
            fullTextEn = pkg.lessonContent.fullTextEn,
            fullTextAr = pkg.lessonContent.fullTextAr,
            segments = segments,
            topicEn = pkg.lessonContent.topicEn,
            topicAr = pkg.lessonContent.topicAr,
            brainstorming = pkg.lessonContent.brainstormingQuestions.map {
                BrainstormQ(it.questionEn, it.questionAr, it.suggestedAnswerEn, it.suggestedAnswerAr)
            },
            guidedSentences = pkg.lessonContent.guidedSentences.map { Sentence(it.en, it.ar) },
            finalDraft = pkg.lessonContent.finalDraft.let {
                if (it.en.isNotBlank() || it.ar.isNotBlank()) Sentence(it.en, it.ar) else null
            },
            rawJson = rawJson,
        )

        val dupIdx = lessons.indexOfFirst { it.courseId == course.id && it.no == newLesson.no }
        val updated = dupIdx >= 0
        if (updated) lessons[dupIdx] = newLesson else lessons.add(newLesson)

        val summary = "تم ${if (updated) "تحديث" else "استيراد"} الدرس $no: «${pkg.metadata.title}»\n" +
            "الكورس: ${course.name} (المستوى ${course.levelId}) · ${wordIds.size} كلمة · ${quiz.size} سؤال"
        lastImportSummary = summary
        return summary
    }

    /**
     * Import MANY lessons at once (batch) — INSTANT, fully offline/local.
     * syncLessonStories + persist run exactly ONCE after the whole batch lands.
     */
    fun importLessons(packages: List<LessonPackage>): String {
        if (packages.isEmpty()) return "لا توجد دروس للاستيراد"
        var imported = 0
        val touchedCourses = LinkedHashSet<String>()
        var words = 0
        packages.forEach { pkg ->
            importLessonCore(pkg)
            imported++
            words += pkg.globalVocabulary.size
            touchedCourses.add(pkg.metadata.courseNameAr.ifBlank { pkg.metadata.courseId })
        }
        val summary = buildString {
            append("تم استيراد $imported درس في ${touchedCourses.size} كورس")
            if (words > 0) append(" · $words كلمة")
            append("\n")
            append(touchedCourses.joinToString(" · "))
        }
        lastImportSummary = summary
        // Newly imported reading lessons must appear in the story archive at once.
        // Runs ONE time for the whole batch, not once per lesson.
        vm.syncLessonStories()
        vm.persist()
        return summary
    }

    private fun accentFor(type: CourseType): Long = when (type) {
        CourseType.VOCABULARY -> 0xFFE07856   // terracotta
        CourseType.GRAMMAR -> 0xFFCB5F41      // sienna
        CourseType.READING -> 0xFF6B9080      // sage
        CourseType.LISTENING -> 0xFF52796F    // pine
        CourseType.PHONETICS -> 0xFFE0A34E    // gold
        CourseType.CONVERSATION -> 0xFFD9776A // coral
        CourseType.WRITING -> 0xFF5E9C76      // green
    }

    private fun styleFor(key: String, type: CourseType): LessonStyle {
        when (key.lowercase().trim()) {
            "vocab_cards", "vocab", "bites" -> return LessonStyle.VOCAB_CARDS
            "grammar_rules", "grammar" -> return LessonStyle.GRAMMAR_RULES
            "reading_text", "reading" -> return LessonStyle.READING_TEXT
            "listening_audio", "listening" -> return LessonStyle.LISTENING_AUDIO
            "conversation" -> return LessonStyle.CONVERSATION
            "phonetics_sounds", "phonetics" -> return LessonStyle.PHONETICS_SOUNDS
            "writing_practice", "writing" -> return LessonStyle.WRITING_PRACTICE
            "story" -> return LessonStyle.STORY
            "news" -> return LessonStyle.NEWS
            "comedy" -> return LessonStyle.COMEDY
            "idioms" -> return LessonStyle.IDIOMS
            "exam_prep", "ielts" -> return LessonStyle.EXAM_PREP
            "thinking" -> return LessonStyle.THINKING
            "culture" -> return LessonStyle.CULTURE
        }
        return when (type) {
            CourseType.VOCABULARY -> LessonStyle.VOCAB_CARDS
            CourseType.GRAMMAR -> LessonStyle.GRAMMAR_RULES
            CourseType.READING -> LessonStyle.READING_TEXT
            CourseType.LISTENING -> LessonStyle.LISTENING_AUDIO
            CourseType.CONVERSATION -> LessonStyle.CONVERSATION
            CourseType.PHONETICS -> LessonStyle.PHONETICS_SOUNDS
            CourseType.WRITING -> LessonStyle.WRITING_PRACTICE
        }
    }
}
