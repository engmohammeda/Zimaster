package com.zmastery.english.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI-powered Course & Lesson Generation Service.
 *
 * Supports generating individual rich lessons or full multi-lesson curricula
 * with customizable teacher personas, pedagogical styles, CEFR levels, and
 * structured JSON output that conforms directly to Z-Mastery's block architecture.
 */
data class TeacherPersona(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val avatarEmoji: String,
    val taglineAr: String,
    val toneDescription: String,
    val systemPromptGuidance: String,
    val accentColor: Long = 0xFF4F46E5,
)

object AiLessonService {

    val builtinPersonas = listOf(
        TeacherPersona(
            id = "prof_harrison",
            nameAr = "البروفيسور هاريسون",
            nameEn = "Prof. Harrison",
            avatarEmoji = "🎓",
            taglineAr = "أكاديمي بريطاني · دقة لغوية وبناء مفردات غني",
            toneDescription = "أكاديمي، فصيح، يركز على الفروق اللغوية الدقيقة والقواعد الرصينة",
            systemPromptGuidance = "You are Professor Harrison, an Oxford-educated linguistics professor. You craft elegant, structured, academically rich English lessons with precise grammar logic, sophisticated vocabulary, and cultural insights.",
            accentColor = 0xFF3B82F6,
        ),
        TeacherPersona(
            id = "sarah_native",
            nameAr = "سارة (المحادثة الحية)",
            nameEn = "Sarah Miller",
            avatarEmoji = "☕",
            taglineAr = "متحدثة أمريكية ودودة · لغة الحياة اليومية والعفوية",
            toneDescription = "عفوي، ودود، يركز على التعابير الشائعة والمحادثات الواقعية",
            systemPromptGuidance = "You are Sarah, a warm and enthusiastic American conversation coach. You specialize in real-world spoken English, modern idioms, daily dialogues, and natural phrasing that native speakers actually use.",
            accentColor = 0xFF10B981,
        ),
        TeacherPersona(
            id = "david_business",
            nameAr = "ديفيد (إنجليزية الأعمال)",
            nameEn = "David Vance",
            avatarEmoji = "💼",
            taglineAr = "خبير مهني · اجتماعات وتفاوض ومراسلات رسمية",
            toneDescription = "احترافي، عملي، موجه لبيئات العمل والشركات والمقابلات",
            systemPromptGuidance = "You are David Vance, an executive corporate communication trainer. You build concise, high-impact business English lessons focused on workplace communication, negotiation, presentations, and professional vocabulary.",
            accentColor = 0xFF6366F1,
        ),
        TeacherPersona(
            id = "alex_ielts",
            nameAr = "أليكس (خبير الآيلتس)",
            nameEn = "Alex Bennett",
            avatarEmoji = "🎯",
            taglineAr = "مدرب امتحانات · قوالب إجابة وتوسيع مدى التعبير",
            toneDescription = "استراتيجي، تحليلي، يركز على معايير التقييم وتطوير التراكيب",
            systemPromptGuidance = "You are Alex Bennett, a master IELTS & TOEFL examiner and prep coach. You produce lessons with academic band 7-9 vocabulary, cohesive discourse markers, grammar range, and exam-style comprehension exercises.",
            accentColor = 0xFFF59E0B,
        ),
        TeacherPersona(
            id = "liam_storyteller",
            nameAr = "ليام (الراوي الأدبي)",
            nameEn = "Liam Walker",
            avatarEmoji = "📖",
            taglineAr = "سرد قصصي مشوّق · ترسيخ المعاني بالسياق والدراما",
            toneDescription = "سردي، مشوق، يربط المفردات والأفكار بحبكة ممتعة",
            systemPromptGuidance = "You are Liam Walker, an acclaimed storyteller and language tutor. You weave compelling mini-stories and immersive reading passages where target vocabulary and structures emerge effortlessly in vivid narrative context.",
            accentColor = 0xFFEC4899,
        ),
        TeacherPersona(
            id = "dr_phonetics",
            nameAr = "د. جوليان (مدرب النطق)",
            nameEn = "Dr. Julian Sound",
            avatarEmoji = "🎙️",
            taglineAr = "صوتيات ومخارج حروف · تصحيح اللكنة والأزواج الصغرى",
            toneDescription = "دقيق صوتياً، يركز على مخارج الأصوات وتدريب اللسان",
            systemPromptGuidance = "You are Dr. Julian, a speech and phonetics specialist. You emphasize accurate IPA phonetics, tongue placement, minimal pair contrasts, rhythm, and clear syllable stress.",
            accentColor = 0xFF8B5CF6,
        ),
        TeacherPersona(
            id = "custom",
            nameAr = "شخصية مخصصة",
            nameEn = "Custom Persona",
            avatarEmoji = "✍️",
            taglineAr = "خصص شخصية المعلم ومطالبته بحرية تامة",
            toneDescription = "حسب اختيارك",
            systemPromptGuidance = "You are an expert English teacher following custom instructional guidelines tailored specifically to the learner's preferences.",
            accentColor = 0xFF06B6D4,
        ),
    )

    sealed class Result<out T> {
        data class Success<T>(val data: T, val summary: String) : Result<T>()
        data class Error(val message: String) : Result<Nothing>()
    }

    /**
     * Generate a single rich structured lesson package.
     */
    suspend fun generateLesson(
        topic: String,
        level: Int = 1,
        courseType: CourseType = CourseType.VOCABULARY,
        lessonStyle: LessonStyle = LessonStyle.VOCAB_CARDS,
        lessonNo: Int = 1,
        courseNameAr: String = "",
        courseId: String = "",
        persona: TeacherPersona = builtinPersonas.first(),
        customInstructions: String = "",
        key: ApiKeyEntry?,
        modelId: String = "",
    ): Result<LessonPackage> = withContext(Dispatchers.IO) {
        if (key == null || key.rawKey.isBlank()) {
            return@withContext Result.Error("أضف مفتاح API في إعدادات الذكاء الاصطناعي أولاً")
        }
        if (topic.isBlank()) {
            return@withContext Result.Error("حدد موضوع الدرس المراد توليده")
        }

        val levelName = when (level) {
            1 -> "A1 - Beginner (مبتدئ)"
            2 -> "A2 - Elementary (أساسي)"
            3 -> "B1 - Intermediate (متوسط)"
            4 -> "B2 - Upper Intermediate (فوق متوسط)"
            5 -> "C1 - Advanced (متقدم)"
            else -> "Level $level"
        }

        val effectiveCourseName = courseNameAr.ifBlank { "كورس $topic" }
        val effectiveCourseId = courseId.ifBlank { "ai_course_${topic.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")}" }

        val systemPrompt = buildString {
            appendLine(persona.systemPromptGuidance)
            appendLine()
            appendLine("You are an expert curriculum developer for Z-Mastery English app.")
            appendLine("Your task is to generate ONE comprehensive, high-quality, fully structured English lesson.")
            appendLine("CRITICAL: You MUST respond ONLY with a single valid JSON object strictly matching the required schema.")
            appendLine("DO NOT wrap with markdown code fences, do not output explanatory text before or after.")
        }

        val userPrompt = buildString {
            appendLine("Generate a complete structured English lesson for:")
            appendLine("- Topic: $topic")
            appendLine("- Target Level: $levelName (Level integer: $level)")
            appendLine("- Course Type: ${courseType.name.lowercase()}")
            appendLine("- Lesson Style: ${lessonStyle.name.lowercase()}")
            appendLine("- Lesson Number: $lessonNo")
            appendLine("- Course Name (Arabic): $effectiveCourseName")
            appendLine("- Course ID: $effectiveCourseId")
            if (customInstructions.isNotBlank()) {
                appendLine("- Specific Learner Custom Instructions: $customInstructions")
            }
            appendLine()
            appendLine("Output must be a valid JSON object with the following schema:")
            appendLine("""
{
  "metadata": {
    "course_id": "$effectiveCourseId",
    "course_name_ar": "$effectiveCourseName",
    "course_type": "${courseType.name.lowercase()}",
    "level": $level,
    "level_name": "$levelName",
    "lesson_no": $lessonNo,
    "lesson_style": "${lessonStyle.name.lowercase()}",
    "title": "Clear English Lesson Title",
    "summary_ar": "ملخص عربي واضح وموجز للدرس"
  },
  "words": [
    {
      "word": "english_word",
      "translation": "الترجمة العربية الدقيقة",
      "phonetic": "/IPA/",
      "example": "A clear example sentence using the word.",
      "example_ar": "ترجمة عربية للمثال",
      "mental_image": "صورة ذهنية بصرية طريفة ومبتكرة للمساعدة في الحفظ"
    }
  ],
  "grammar_rules": [
    {
      "rule": "Rule title in English",
      "rule_ar": "شرح القاعدة بالعربية",
      "examples": [
        {"en": "Example 1 in English", "ar": "ترجمة عربية"}
      ]
    }
  ],
  "dialogues": [
    {
      "speaker": "Speaker Name",
      "en": "Dialogue line in English",
      "ar": "الترجمة العربية للسطر"
    }
  ],
  "reading_en": "A cohesive 5-8 sentence reading passage utilizing the lesson vocabulary and concepts naturally.",
  "reading_ar": "ترجمة عربية دقيقة وكاملة للنص القرائي.",
  "key_points": [
    "نقطة رئيسية 1 بالعربية",
    "نقطة رئيسية 2 بالعربية"
  ],
  "quiz": [
    {
      "type": "multiple_choice",
      "question": "Clear question testing vocabulary or grammar?",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "answer": "Option A",
      "explanation_ar": "شرح توضيحي لسبب صحة الإجابة",
      "word_to_speak": "keyword"
    }
  ],
  "lesson_content": {
    "key_expressions": [
      {"expression_en": "Key idiom/expression", "expression_ar": "المعنى بالعربي", "usage_ar": "متى وكيف يُستخدم"}
    ],
    "explanation_ar": "شرح تعليمي تفصيلي للدرس بالعربية",
    "logic_ar": "منطق وأسرار هذا المفهوم اللغوي للمتعلم العربي",
    "examples": [
      {"en": "Rich English example", "ar": "ترجمته العربية"}
    ],
    "full_text_en": "Full reading or listening text in English",
    "full_text_ar": "الترجمة العربية الكاملة",
    "segments": [
      {"en": "Sentence segment in English", "ar": "ترجمة الجملة بالعربية"}
    ],
    "topic_en": "$topic",
    "topic_ar": "$topic",
    "brainstorming_questions": [
      {"question_en": "Discussion question?", "question_ar": "ترجمة السؤال", "suggested_answer_en": "Sample answer", "suggested_answer_ar": "ترجمة الإجابة"}
    ],
    "guided_sentences": [
      {"en": "Practice sentence", "ar": "ترجمة تدريبية"}
    ],
    "final_draft": {"en": "Master sample paragraph", "ar": "الترجمة الكاملة للفقرة"}
  }
}
            """.trimIndent())
        }

        val reply = AiClient.complete(
            key = key,
            model = modelId,
            system = systemPrompt,
            user = userPrompt,
            json = true,
            temperature = 0.6,
        )

        if (!reply.ok) {
            return@withContext Result.Error(reply.error.ifBlank { "تعذر الاتصال بنموذج الذكاء الاصطناعي" })
        }

        val cleanJson = AiClient.stripFences(reply.text).trim()
        val pkg = runCatching {
            ImportEngine.json.decodeFromString<LessonPackage>(cleanJson)
        }.getOrNull()

        if (pkg != null) {
            return@withContext Result.Success(pkg, "تم توليد درس «${pkg.metadata.title}» بنجاح")
        }

        // Fallback tolerant parser if schema minor mismatch occurs
        val fallbackPkg = parseLessonJsonTolerant(cleanJson, topic, level, courseType, lessonStyle, lessonNo, effectiveCourseName, effectiveCourseId)
        if (fallbackPkg != null) {
            return@withContext Result.Success(fallbackPkg, "تم توليد درس «${fallbackPkg.metadata.title}» بنجاح")
        }

        Result.Error("رد غير صالح من النموذج — يرجى إعادة المحاولة أو تجربة نموذج آخر")
    }

    /**
     * Generate a multi-lesson curriculum / course package.
     */
    suspend fun generateCurriculumCourse(
        courseTitle: String,
        level: Int = 1,
        courseType: CourseType = CourseType.VOCABULARY,
        lessonCount: Int = 3,
        persona: TeacherPersona = builtinPersonas.first(),
        customInstructions: String = "",
        key: ApiKeyEntry?,
        modelId: String = "",
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
    ): Result<CoursePackage> = withContext(Dispatchers.IO) {
        if (key == null || key.rawKey.isBlank()) {
            return@withContext Result.Error("أضف مفتاح API في إعدادات الذكاء الاصطناعي أولاً")
        }
        if (courseTitle.isBlank()) {
            return@withContext Result.Error("اكتب عنوان أو موضوع المنهج")
        }

        val count = lessonCount.coerceIn(1, 8)
        val generatedLessons = mutableListOf<JsonLesson>()
        val courseKey = "custom_ai_${System.currentTimeMillis()}"
        val courseId = "ai_${courseTitle.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")}"

        for (i in 1..count) {
            onProgress(i, count, "جارٍ توليد الدرس $i من $count: $courseTitle…")
            val lessonSubtopic = "$courseTitle (الجزء $i من $count)"
            val lessonRes = generateLesson(
                topic = lessonSubtopic,
                level = level,
                courseType = courseType,
                lessonStyle = when (courseType) {
                    CourseType.VOCABULARY -> LessonStyle.VOCAB_CARDS
                    CourseType.GRAMMAR -> LessonStyle.GRAMMAR_RULES
                    CourseType.READING -> LessonStyle.READING_TEXT
                    CourseType.CONVERSATION -> LessonStyle.CONVERSATION
                    CourseType.PHONETICS -> LessonStyle.PHONETICS_SOUNDS
                    CourseType.WRITING -> LessonStyle.WRITING_PRACTICE
                    else -> LessonStyle.VOCAB_CARDS
                },
                lessonNo = i,
                courseNameAr = courseTitle,
                courseId = courseId,
                persona = persona,
                customInstructions = "$customInstructions (Lesson $i of $count syllabus)",
                key = key,
                modelId = modelId,
            )

            when (lessonRes) {
                is Result.Success -> {
                    val p = lessonRes.data
                    generatedLessons.add(
                        JsonLesson(
                            lessonId = "${courseId}_lesson_$i",
                            no = i,
                            title = p.metadata.title.ifBlank { "Lesson $i: $courseTitle" },
                            summaryAr = p.lessonNotes.firstOrNull() ?: "درس $i من $courseTitle",
                            readingEn = p.lessonContent.fullTextEn,
                            readingAr = p.lessonContent.fullTextAr,
                            keyPoints = p.lessonNotes,
                            words = p.globalVocabulary.map {
                                JsonWord(
                                    word = it.word,
                                    phonetic = it.phonetic,
                                    example = it.exampleEn,
                                    exampleAr = it.exampleAr,
                                    translation = it.meaning,
                                    mentalImage = it.mentalImage,
                                )
                            },
                            grammarRules = if (p.lessonContent.explanationAr.isNotBlank()) listOf(
                                JsonGrammarRule(
                                    rule = p.lessonContent.explanationAr,
                                    ruleAr = p.lessonContent.logicAr,
                                    examples = p.lessonContent.examples.map { "${it.en} — ${it.ar}" },
                                )
                            ) else emptyList(),
                            dialogues = p.lessonContent.dialogue,
                        )
                    )
                }
                is Result.Error -> {
                    // Create resilient minimum lesson if single iteration failed
                    generatedLessons.add(
                        JsonLesson(
                            lessonId = "${courseId}_lesson_$i",
                            no = i,
                            title = "Lesson $i: $courseTitle",
                            summaryAr = "درس رقم $i ضمن منهج $courseTitle",
                            readingEn = "Welcome to Lesson $i of $courseTitle. Practice the key vocabulary and sentences consistently.",
                            readingAr = "مرحباً بك في الدرس $i من منهج $courseTitle. تدرّب على المفردات والجمل بانتظام.",
                            keyPoints = listOf("إتقان المفردات الأساسية للدرس $i", "التدريب على النطق والاستماع اليومي"),
                        )
                    )
                }
            }
        }

        val pkg = CoursePackage(
            courseKey = courseKey,
            courseId = courseId,
            courseName = courseTitle,
            courseType = courseType.name.lowercase(),
            level = level,
            levelName = when (level) {
                1 -> "المستوى الأول (A1)"
                2 -> "المستوى الثاني (A2)"
                3 -> "المستوى الثالث (B1)"
                4 -> "المستوى الرابع (B2)"
                else -> "المسار التخصصي"
            },
            style = courseType.name.lowercase(),
            target = count,
            lessons = generatedLessons,
        )

        Result.Success(pkg, "تم توليد منهج «$courseTitle» بـ ${generatedLessons.size} دروس كاملة ✓")
    }

    private fun parseLessonJsonTolerant(
        jsonStr: String,
        fallbackTopic: String,
        fallbackLevel: Int,
        fallbackType: CourseType,
        fallbackStyle: LessonStyle,
        fallbackNo: Int,
        courseNameAr: String,
        courseId: String,
    ): LessonPackage? = runCatching {
        val root = JSONObject(jsonStr)
        val metaObj = root.optJSONObject("metadata") ?: JSONObject()

        val meta = LessonMeta(
            courseId = metaObj.optString("course_id", courseId),
            courseNameAr = metaObj.optString("course_name_ar", courseNameAr),
            courseType = metaObj.optString("course_type", fallbackType.name.lowercase()),
            level = metaObj.optInt("level", fallbackLevel),
            levelName = metaObj.optString("level_name", "Level $fallbackLevel"),
            lessonNo = metaObj.optInt("lesson_no", fallbackNo),
            style = metaObj.optString("style", fallbackStyle.name.lowercase()),
            title = metaObj.optString("title", fallbackTopic).ifBlank { fallbackTopic },
        )

        val wordsList = mutableListOf<JsonGlobalWord>()
        val wordsArr = root.optJSONArray("words") ?: root.optJSONArray("global_vocabulary")
        if (wordsArr != null) {
            for (i in 0 until wordsArr.length()) {
                val o = wordsArr.optJSONObject(i) ?: continue
                wordsList.add(
                    JsonGlobalWord(
                        word = o.optString("word"),
                        meaning = o.optString("translation").ifBlank { o.optString("meaning") },
                        phonetic = o.optString("phonetic"),
                        exampleEn = o.optString("example").ifBlank { o.optString("example_en") },
                        exampleAr = o.optString("example_ar"),
                        mentalImage = o.optString("mental_image"),
                    )
                )
            }
        }

        val rulesList = mutableListOf<JsonSentence>()
        var explanationAr = ""
        var logicAr = ""
        val rulesArr = root.optJSONArray("grammar_rules")
        if (rulesArr != null) {
            for (i in 0 until rulesArr.length()) {
                val o = rulesArr.optJSONObject(i) ?: continue
                if (explanationAr.isBlank()) explanationAr = o.optString("rule")
                if (logicAr.isBlank()) logicAr = o.optString("rule_ar")
                val exArr = o.optJSONArray("examples")
                if (exArr != null) {
                    for (j in 0 until exArr.length()) {
                        val eo = exArr.optJSONObject(j)
                        if (eo != null) {
                            rulesList.add(JsonSentence(eo.optString("en"), eo.optString("ar")))
                        } else {
                            val str = exArr.optString(j)
                            if (str.isNotBlank()) rulesList.add(JsonSentence(str, ""))
                        }
                    }
                }
            }
        }

        val dialoguesList = mutableListOf<JsonDialogue>()
        val diagArr = root.optJSONArray("dialogues") ?: root.optJSONArray("dialogue")
        if (diagArr != null) {
            for (i in 0 until diagArr.length()) {
                val o = diagArr.optJSONObject(i) ?: continue
                dialoguesList.add(
                    JsonDialogue(
                        speaker = o.optString("speaker"),
                        en = o.optString("en"),
                        ar = o.optString("ar"),
                    )
                )
            }
        }

        val quizList = mutableListOf<JsonQuiz>()
        val quizArr = root.optJSONArray("quiz")
        if (quizArr != null) {
            for (i in 0 until quizArr.length()) {
                val o = quizArr.optJSONObject(i) ?: continue
                val opts = mutableListOf<String>()
                val optArr = o.optJSONArray("options")
                if (optArr != null) {
                    for (j in 0 until optArr.length()) {
                        opts.add(optArr.optString(j))
                    }
                }
                quizList.add(
                    JsonQuiz(
                        type = o.optString("type", "multiple_choice"),
                        question = o.optString("question"),
                        options = opts,
                        answer = o.optString("answer"),
                        explanationAr = o.optString("explanation_ar"),
                        wordToSpeak = o.optString("word_to_speak"),
                    )
                )
            }
        }

        val keyPoints = mutableListOf<String>()
        val kpArr = root.optJSONArray("key_points") ?: root.optJSONArray("lesson_notes")
        if (kpArr != null) {
            for (i in 0 until kpArr.length()) {
                keyPoints.add(kpArr.optString(i))
            }
        }

        val keySentences = mutableListOf<JsonSentence>()
        val ksArr = root.optJSONArray("key_sentences")
        if (ksArr != null) {
            for (i in 0 until ksArr.length()) {
                val o = ksArr.optJSONObject(i) ?: continue
                keySentences.add(JsonSentence(o.optString("en"), o.optString("ar")))
            }
        }

        val content = LessonContent(
            keySentences = keySentences,
            dialogue = dialoguesList,
            explanationAr = explanationAr,
            logicAr = logicAr,
            examples = rulesList,
            fullTextEn = root.optString("reading_en").ifBlank { root.optString("full_text_en") },
            fullTextAr = root.optString("reading_ar").ifBlank { root.optString("full_text_ar") },
        )

        LessonPackage(
            metadata = meta,
            lessonContent = content,
            globalVocabulary = wordsList,
            lessonNotes = keyPoints,
            quiz = quizList,
        )
    }.getOrNull()
}
