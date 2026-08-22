package com.zmastery.english.data

object SampleData {

    val dailyPhrases = listOf(
        "Practice makes perfect. — التدريب يصنع الإتقان.",
        "Every expert was once a beginner. — كل خبير كان مبتدئاً يوماً ما.",
        "Small steps every day lead to big results. — خطوات صغيرة كل يوم تقود لنتائج كبيرة.",
        "Learning never exhausts the mind. — التعلم لا يُنهك العقل أبداً.",
        "The best time to start was yesterday. The next best is now. — أفضل وقت للبدء كان الأمس، والأفضل التالي هو الآن.",
        "Consistency beats intensity. — الاستمرارية تتفوق على الشدة.",
        "Mistakes are proof that you are trying. — الأخطاء دليل على أنك تحاول.",
    )

    // ----- Curriculum plan (structure only — no default lessons/words) -----
    val levels = listOf(
        Level(1, "المبتدئ", "7 كورسات · أساسيات اللغة من الصفر", "\uD83C\uDF31"),
        Level(2, "المتوسط", "13 كورساً · بناء الطلاقة والثقة", "\uD83D\uDE80"),
        Level(3, "المتقدم", "5 كورسات · إتقان اللغة كالمحترفين", "\uD83D\uDC51"),
    )

    // A sample phonetics lesson (Level 1 · lesson 2) to preview the design.
    val samplePhoneticsJson = """
{ "metadata": { "course_id": "phonetics", "course_name_ar": "الصوتيات", "level": 1, "lesson_no": 2, "title": "الفرق بين أصوات الحروف A و E و I وطريقة التشكيل" }, "lesson_content": { "focus_sounds": [ { "symbol": "/æ/", "description": "صوت حرف A القصير: صوت بطيء وممدود يُشبه حركة الحلزون. يتم نطقه بفتح الفم بشكل دائري وواسع إلى الأسفل والأطراف (آ)." }, { "symbol": "/e/", "description": "صوت حرف E القصير: صوت سريع ونشيط جداً يُشبه انطلاق الصاروخ (أَ)، وهو نفس الصوت البادئ في كلمة energy." }, { "symbol": "/ɪ/", "description": "صوت حرف I القصير: صوت حاد ومكسور يطابق الكسرة في اللغة العربية تماماً (إِ)، ويتم نطقه بوضعية فم مبتسمة ومشدودة جانباً." } ], "minimal_pairs": [ { "word1": "Ban", "word2": "Ben" }, { "word1": "Bad", "word2": "Bed" }, { "word1": "Bat", "word2": "Bet" }, { "word1": "Lag", "word2": "Leg" }, { "word1": "Lad", "word2": "Led" }, { "word1": "Pan", "word2": "Pen" }, { "word1": "Sat", "word2": "Set" }, { "word1": "Tan", "word2": "Ten" }, { "word1": "Set", "word2": "Sit" }, { "word1": "Bet", "word2": "Bit" }, { "word1": "Led", "word2": "Lid" }, { "word1": "Reg", "word2": "Rig" }, { "word1": "Beg", "word2": "Big" } ], "practice_scripts": [ "Pam and dad are on the van", "The fat cat ran to pam", "Rex and the hen get fed", "The hen is on the red bed", "Tex set a wet net on the bed", "Siz and liz sit on the rim", "Siz and liz sit and sip" ] }, "global_vocabulary": [ { "word": "energy", "meaning": "طاقة", "example_en": "You need energy to run.", "example_ar": "تحتاج إلى طاقة لكي تركض." }, { "word": "web", "meaning": "شبكة العنكبوت", "example_en": "The spider is on the web.", "example_ar": "العنكبوت موجود على الشبكة." } ], "lesson_notes": [ "الفرق الأساسي بين صوت الـ A وصوت الـ E هو نسبة المد؛ صوت الـ A ممدود وبطيء (يرمز له بالحلزون)، بينما صوت الـ E مقطوع وسريع (يرمز له بالصاروخ).", "صوت الـ I الإنجليزي يماثل الكسرة العربية تماماً، ويجب تفعيل وضعية الابتسامة بالشفاه أثناء النطق لإخراجه بشكل سليم.", "استخدم الأستاذ نظام رموز تشكيلية خاصة لتسهيل القراءة في هذه المرحلة: الخط المستقيم فوق حرف الـ A للتعبير عن الصوت الممدود البطيء، والخط المائل فوق الـ E للتعبير عن الصوت السريع الخاطف، والرمز (v) فوق الـ I للتعبير عن الكسرة العربية.", "يُنصح باستخدام مرآة لمراقبة ومقارنة شكل حركة الفم والشفاه بالصور التوضيحية أثناء تمرينات التكرار والظل (Shadowing)." ], "quiz": [ { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Sat", "options": ["Sat", "Set", "Sit", "Sad"], "answer": "Sat", "explanation_ar": "الكلمة تحتوي على صوت الـ A الممدود والبطيء كالحلزون." }, { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Set", "options": ["Sat", "Set", "Sit", "Seat"], "answer": "Set", "explanation_ar": "الكلمة تحتوي على صوت الـ E السريع والنشيط كالصاروخ." }, { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Sit", "options": ["Sat", "Set", "Sit", "Seat"], "answer": "Sit", "explanation_ar": "الكلمة تحتوي على صوت الـ I المكسور مثل الكسرة في اللغة العربية." }, { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Ben", "options": ["Ban", "Ben", "Bin", "Bean"], "answer": "Ben", "explanation_ar": "الكلمة المنطوقة هي Ben بصوت حرف E السريع." }, { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Ban", "options": ["Ban", "Ben", "Bin", "Bean"], "answer": "Ban", "explanation_ar": "الكلمة المنطوقة هي Ban بصوت حرف A الممدود والبطيء." }, { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Big", "options": ["Bag", "Beg", "Big", "Bug"], "answer": "Big", "explanation_ar": "الكلمة المنطوقة هي Big بصوت حرف I المكسور (كالكسرة العربية)." }, { "type": "true_false", "question": "صوت حرف الـ E يكون ممدوداً وبطيئاً مقارنة بصوت حرف الـ A.", "word_to_speak": null, "options": null, "answer": "False", "explanation_ar": "العبارة خاطئة. صوت الـ E سريع وخاطف كالصاروخ، بينما صوت الـ A هو الممدود والبطيء كالحلزون." }, { "type": "true_false", "question": "عند نطق صوت الـ I القصير، يأخذ الفم وضعية تشبه الابتسامة لتسهيل كسر الصوت.", "word_to_speak": null, "options": null, "answer": "True", "explanation_ar": "العبارة صحيحة. وضعية الفم المبتسم تساعد على إخراج صوت الـ I المكسور بوضوح ودقة." }, { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Wet", "options": ["Wait", "Wet", "Wit", "What"], "answer": "Wet", "explanation_ar": "الكلمة المنطوقة هي Wet بصوت الـ E النشيط القصير." }, { "type": "audio_quiz", "question": "استمع واختر الكلمة الصحيحة التي سمعتها", "word_to_speak": "Lad", "options": ["Lad", "Led", "Lid", "Late"], "answer": "Lad", "explanation_ar": "الكلمة المنطوقة هي Lad بصوت الـ A الممدود المفتوح." } ] }
""".trimIndent()

    // Warm accent palette
    private const val TERRACOTTA = 0xFFE07856
    private const val SIENNA = 0xFFCB5F41
    private const val SAGE = 0xFF6B9080
    private const val PINE = 0xFF52796F
    private const val GOLD = 0xFFE0A34E
    private const val CORAL = 0xFFD9776A
    private const val GREEN = 0xFF5E9C76

    val courses = listOf(
        // ===== المستوى الأول =====
        Course(1, 1, "من الصفر", CourseType.VOCABULARY, 25, TERRACOTTA, LessonStyle.VOCAB_CARDS, "l1_scratch", "zero_to_hero"),
        Course(2, 1, "القواعد", CourseType.GRAMMAR, 24, SIENNA, LessonStyle.GRAMMAR_RULES, "l1_grammar", "grammar_l1"),
        Course(3, 1, "القراءة", CourseType.READING, 17, SAGE, LessonStyle.READING_TEXT, "l1_reading", "reading_l1"),
        Course(4, 1, "الاستماع", CourseType.LISTENING, 14, PINE, LessonStyle.LISTENING_AUDIO, "l1_listening", "listening_l1"),
        Course(5, 1, "المحادثة", CourseType.CONVERSATION, 8, CORAL, LessonStyle.CONVERSATION, "l1_conversation", "conversation_l1"),
        Course(6, 1, "الصوتيات", CourseType.PHONETICS, 14, GOLD, LessonStyle.PHONETICS_SOUNDS, "l1_phonetics", "phonetics"),
        Course(7, 1, "الكتابة", CourseType.WRITING, 14, GREEN, LessonStyle.WRITING_PRACTICE, "l1_writing", "writing_l1"),

        // ===== المستوى الثاني =====
        Course(8, 2, "Bites", CourseType.VOCABULARY, 30, TERRACOTTA, LessonStyle.VOCAB_CARDS, "l2_bites", "bites"),
        Course(9, 2, "شفرة أمريكا", CourseType.READING, 10, GOLD, LessonStyle.CULTURE, "l2_americacode", "america_code"),
        Course(10, 2, "المحادثة", CourseType.CONVERSATION, 17, CORAL, LessonStyle.CONVERSATION, "l2_conversation", "conversation_l2"),
        Course(11, 2, "الاستماع", CourseType.LISTENING, 7, PINE, LessonStyle.LISTENING_AUDIO, "l2_listening", "listening_l2"),
        Course(12, 2, "التفكير", CourseType.READING, 12, SAGE, LessonStyle.THINKING, "l2_thinking", "thinking"),
        Course(13, 2, "الأخبار", CourseType.READING, 6, SIENNA, LessonStyle.NEWS, "l2_news", "news"),
        Course(14, 2, "الكتابة", CourseType.WRITING, 10, GREEN, LessonStyle.WRITING_PRACTICE, "l2_writing", "writing_l2"),
        Course(15, 2, "الصوتيات", CourseType.PHONETICS, 16, GOLD, LessonStyle.PHONETICS_SOUNDS, "l2_phonetics", "phonetics_l2"),
        Course(16, 2, "القراءة", CourseType.READING, 8, SAGE, LessonStyle.READING_TEXT, "l2_reading", "reading_l2"),
        Course(17, 2, "الغرب", CourseType.READING, 8, SIENNA, LessonStyle.CULTURE, "l2_west", "the_west"),
        Course(18, 2, "القواعد", CourseType.GRAMMAR, 15, SIENNA, LessonStyle.GRAMMAR_RULES, "l2_grammar", "grammar_l2"),
        Course(19, 2, "كوميدي", CourseType.LISTENING, 16, CORAL, LessonStyle.COMEDY, "l2_comedy", "comedy"),
        Course(20, 2, "Story", CourseType.READING, 9, TERRACOTTA, LessonStyle.STORY, "l2_story", "story"),

        // ===== المستوى الثالث =====
        Course(21, 3, "Idioms Podium", CourseType.VOCABULARY, 21, TERRACOTTA, LessonStyle.IDIOMS, "l3_idioms", "idioms_podium"),
        Course(22, 3, "I Know", CourseType.READING, 23, SAGE, LessonStyle.READING_TEXT, "l3_iknow", "i_know"),
        Course(23, 3, "Coffee Break", CourseType.CONVERSATION, 2, CORAL, LessonStyle.CONVERSATION, "l3_coffeebreak", "coffee_break"),
        Course(24, 3, "IELTS", CourseType.GRAMMAR, 6, PINE, LessonStyle.EXAM_PREP, "l3_ielts", "ielts"),
        Course(25, 3, "Book Worm", CourseType.READING, 6, GOLD, LessonStyle.READING_TEXT, "l3_bookworm", "book_worm"),
    )

    /** Look up a course by its stable key (used when importing lessons). */
    fun courseByKey(key: String) = courses.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }

    private fun normalize(s: String): String = s.trim().lowercase()
        .replace("ـ", "").replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace(Regex("[\\s_\\-]+"), "")

    /**
     * Resolve a course from the author's per-lesson `course_id`. Very tolerant:
     * matches jsonId, stable key, English/Arabic name, or a normalized form of any.
     */
    fun courseByJsonId(id: String): Course? {
        val t = id.trim()
        if (t.isEmpty()) return null
        // 1) exact jsonId / key / name
        courses.firstOrNull { it.jsonId.equals(t, ignoreCase = true) }?.let { return it }
        courses.firstOrNull { it.key.equals(t, ignoreCase = true) }?.let { return it }
        courses.firstOrNull { it.name.equals(t, ignoreCase = true) }?.let { return it }
        // 2) normalized match against jsonId / key / name
        val n = normalize(t)
        return courses.firstOrNull {
            normalize(it.jsonId) == n || normalize(it.key) == n || normalize(it.name) == n
        }
    }

    /** Resolve a course using multiple hints (course_id, course_name_ar, level). */
    fun resolveCourse(courseId: String, courseNameAr: String = "", level: Int = 0): Course? {
        courseByJsonId(courseId)?.let { return it }

        // Level-aware prefix match: a generic course_id like "conversation" or
        // "grammar" should bind to the course of that family in the given level.
        if (level > 0 && courseId.isNotBlank()) {
            val n = normalize(courseId)
            courses.firstOrNull {
                it.levelId == level &&
                    (normalize(it.jsonId).startsWith(n) || normalize(it.jsonId).removeSuffix(normalize("l$level")) == n)
            }?.let { return it }
        }

        if (courseNameAr.isNotBlank()) {
            courseByJsonId(courseNameAr)?.let { return it }
            val n = normalize(courseNameAr)
            courses.firstOrNull { normalize(it.name) == n && (level == 0 || it.levelId == level) }?.let { return it }
        }
        return null
    }

    // ----- No default content: lessons / words / stories start empty -----
    // All learning content is added by importing JSON course packages.
    val lessons = emptyList<Lesson>()

    val vocab = emptyList<VocabWord>()

    val stories = emptyList<Story>()

    // Weekly activity chart starts flat until the user studies.
    val weeklyActivity = listOf(
        DailyActivity("السبت", 0),
        DailyActivity("الأحد", 0),
        DailyActivity("الاثنين", 0),
        DailyActivity("الثلاثاء", 0),
        DailyActivity("الأربعاء", 0),
        DailyActivity("الخميس", 0),
        DailyActivity("الجمعة", 0),
    )

    fun coursesForLevel(levelId: Int) = courses.filter { it.levelId == levelId }
    fun lessonsForCourse(courseId: Int) = lessons.filter { it.courseId == courseId }
    fun courseById(id: Int) = courses.firstOrNull { it.id == id }
}
