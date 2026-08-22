package com.zmastery.english.data

// ==========================================================================
// Domain <-> DTO mappers for persistence.
// ==========================================================================

fun Course.toDto() = CourseDto(id, levelId, name, type.name, target, accent, style.name, key, jsonId)

fun CourseDto.toDomain() = Course(
    id = id, levelId = levelId, name = name,
    type = runCatching { CourseType.valueOf(type) }.getOrDefault(CourseType.VOCABULARY),
    target = target, accent = accent,
    style = runCatching { LessonStyle.valueOf(style) }.getOrDefault(LessonStyle.VOCAB_CARDS),
    key = key, jsonId = jsonId,
)

fun Lesson.toDto() = LessonDto(
    id = id, courseId = courseId, no = no, title = title, summaryAr = summaryAr,
    readingEn = readingEn, readingAr = readingAr, keyPoints = keyPoints, isCompleted = isCompleted,
    dialogues = dialogues.map { DialogueDto(it.speaker, it.en, it.ar) },
    newWordIds = newWordIds,
    keySentences = keySentences.map { SentenceDto(it.en, it.ar) },
    notes = notes,
    quiz = quiz.map { QuizDto(it.type.name, it.question, it.options, it.answer, it.explanationAr) },
    reviewCount = reviewCount, lastMastery = lastMastery, dueInDays = dueInDays, intervalDays = intervalDays,
    rawJson = rawJson,
)

fun LessonDto.toDomain() = Lesson(
    id = id, courseId = courseId, no = no, title = title, summaryAr = summaryAr,
    readingEn = readingEn, readingAr = readingAr, keyPoints = keyPoints, isCompleted = isCompleted,
    dialogues = dialogues.map { Dialogue(it.speaker, it.en, it.ar) },
    newWordIds = newWordIds,
    keySentences = keySentences.map { Sentence(it.en, it.ar) },
    notes = notes,
    quiz = quiz.map {
        QuizItem(
            type = runCatching { QuizType.valueOf(it.type) }.getOrDefault(QuizType.MULTIPLE_CHOICE),
            question = it.question, options = it.options, answer = it.answer, explanationAr = it.explanationAr,
        )
    },
    reviewCount = reviewCount, lastMastery = lastMastery, dueInDays = dueInDays, intervalDays = intervalDays,
    rawJson = rawJson,
)

fun VocabWord.toDto() = WordDto(
    id = id, english = english, arabic = arabic, exampleEn = exampleEn, exampleAr = exampleAr,
    phonetic = phonetic, mentalImage = mentalImage, courseId = courseId,
    stability = stability, difficulty = difficulty, phase = phase.name, dueInDays = dueInDays,
    intervalDays = intervalDays, lastReviewedDay = lastReviewedDay, repetitions = repetitions,
    mastered = mastered, listenCount = listenCount, totalReviews = totalReviews, lapses = lapses,
    lastRecall = lastRecall.name, avgRecallStage = avgRecallStage, lastGrade = lastGrade,
    pendingApproval = pendingApproval, lessonId = lessonId,
)

fun WordDto.toDomain() = VocabWord(
    id = id, english = english, arabic = arabic, exampleEn = exampleEn, exampleAr = exampleAr,
    phonetic = phonetic, mentalImage = mentalImage, courseId = courseId,
    stability = stability, difficulty = difficulty,
    phase = runCatching { FsrsPhase.valueOf(phase) }.getOrDefault(FsrsPhase.NEW),
    dueInDays = dueInDays, intervalDays = intervalDays, lastReviewedDay = lastReviewedDay,
    repetitions = repetitions, mastered = mastered, listenCount = listenCount,
    totalReviews = totalReviews, lapses = lapses,
    lastRecall = runCatching { RecallSource.valueOf(lastRecall) }.getOrDefault(RecallSource.NONE),
    avgRecallStage = avgRecallStage, lastGrade = lastGrade,
    pendingApproval = pendingApproval, lessonId = lessonId,
)

// ----- Exam records -----

fun ExamRecord.toDto() = ExamRecordDto(
    id = id, mode = mode.name, title = title, correct = correct, total = total,
    stamp = stamp, durationMs = durationMs,
    skillCorrect = skillCorrect.mapKeys { it.key.name },
    skillTotal = skillTotal.mapKeys { it.key.name },
)

fun ExamRecordDto.toDomain() = ExamRecord(
    id = id, mode = ExamMode.from(mode), title = title, correct = correct, total = total,
    stamp = stamp, durationMs = durationMs,
    skillCorrect = skillCorrect.mapNotNull { (k, v) ->
        runCatching { ExamSkill.valueOf(k) }.getOrNull()?.let { it to v }
    }.toMap(),
    skillTotal = skillTotal.mapNotNull { (k, v) ->
        runCatching { ExamSkill.valueOf(k) }.getOrNull()?.let { it to v }
    }.toMap(),
)

fun ArchivedStory.toDto() = StoryDto(
    id = id, kind = kind.name, title = title, en = en, ar = ar, words = words,
    dayEpoch = dayEpoch, dateLabel = dateLabel, lessonId = lessonId, courseName = courseName,
    isRead = isRead, isFavorite = isFavorite, audioReady = audioReady,
)

fun StoryDto.toDomain() = ArchivedStory(
    id = id,
    kind = runCatching { StoryKind.valueOf(kind) }.getOrDefault(StoryKind.DAILY),
    title = title, en = en, ar = ar, words = words,
    dayEpoch = dayEpoch, dateLabel = dateLabel, lessonId = lessonId, courseName = courseName,
    isRead = isRead, isFavorite = isFavorite, audioReady = audioReady,
)
