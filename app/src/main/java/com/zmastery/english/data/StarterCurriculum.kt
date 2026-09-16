package com.zmastery.english.data

/**
 * المنهج التأسيسي المضمن في التطبيق:
 * يضمن أن المتعلّم يستطيع بدء دراسته فوراً من اليوم الأول،
 * حتى بدون اتصال بالإنترنت أو قبل رفع أي ملفات إضافية.
 * يشمل دروساً تفاعلية في: التأسيس، الصوتيات، القواعد، القراءة، والمحادثة.
 */
object StarterCurriculum {

    val defaultPackages: List<LessonPackage> by lazy {
        val packages = mutableListOf<LessonPackage>()

        // 1. درس من الصفر (Zero to Hero · Lesson 1)
        packages += LessonPackage(
            metadata = LessonMeta(
                courseId = "zero_to_hero",
                courseNameAr = "من الصفر",
                level = 1,
                lessonNo = 1,
                title = "التحيات والتعارف الأساسي - Greetings & Basics",
                courseType = "vocabulary",
            ),
            lessonContent = LessonContent(
                fullTextEn = "Hello and welcome! Today is the start of your English journey. A good friend will practice with you every day. When you speak English consistently, you will learn fast.",
                fullTextAr = "مرحباً وأهلاً بك! اليوم هو بداية رحلتك في اللغة الإنجليزية. الصديق الجيد سيتدرب معك كل يوم. عندما تتحدث الإنجليزية باستمرار، ستتعلم بسرعة.",
                explanationAr = "في هذا الدرس التأسيسي، نتعلم أشهر التحيات وكلمات التخاطب الأساسية التي تحتاجها في أول يوم لك في دراسة الإنجليزية.",
                keyExpressions = listOf(
                    JsonKeyExpression("Nice to meet you", "سررت بلقائك", "تُقال عند التعرف على شخص للمرة الأولى"),
                    JsonKeyExpression("How are you doing?", "كيف حالك وكيف تسير أمورك؟", "تحية ودية شائعة جداً"),
                    JsonKeyExpression("Step by step", "خطوة بخطوة", "مبدأ الاستمرارية في التعلم"),
                ),
                keySentences = listOf(
                    JsonSentence("Hello, nice to meet you.", "مرحباً، سررت بلقائك."),
                    JsonSentence("I am ready to learn English.", "أنا مستعد لتعلم الإنجليزية."),
                    JsonSentence("Practice makes perfect.", "التدريب يصنع الإتقان."),
                ),
                dialogue = listOf(
                    JsonDialogue("Omar", "Hello! My name is Omar. Welcome to Z-Mastery.", "مرحباً! اسمي عمر. أهلاً بك في Z-Mastery."),
                    JsonDialogue("Sarah", "Hi Omar! I am ready to learn English today.", "أهلاً عمر! أنا جاهزة لتعلم الإنجليزية اليوم."),
                    JsonDialogue("Omar", "Awesome! Practice every day and you will speak fluently.", "رائع! تدربي كل يوم وستتحدثين بطلاقة."),
                ),
            ),
            globalVocabulary = listOf(
                JsonGlobalWord("hello", "مرحباً", "Hello! Nice to meet you.", "مرحباً! سررت بلقائك.", "/həˈloʊ/", "شخص يبتسم ويلوح بيده ترحيباً"),
                JsonGlobalWord("welcome", "أهلاً وسهلاً", "Welcome to our learning journey.", "أهلاً بك في رحلتنا التعليمية.", "/ˈwel.kəm/", "باب مفتوح مع لافتة ترحيبية مضيئة"),
                JsonGlobalWord("friend", "صديق", "He is my best friend.", "هو صديقي المفضل.", "/frend/", "صديقان يتعاونان معاً في الدراسة"),
                JsonGlobalWord("learn", "يتعلم", "I want to learn English every day.", "أريد أن أتعلم الإنجليزية كل يوم.", "/lɜːrn/", "كتاب مفتوح تخرج منه مصابيح أفكار مضيئة"),
                JsonGlobalWord("speak", "يتحدث", "She can speak English clearly.", "هي تستطيع التحدث بالإنجليزية بوضوح.", "/spiːk/", "ميكروفون وشخص يتحدث بثقة"),
                JsonGlobalWord("today", "اليوم", "Today is a great day to start.", "اليوم يوم رائع للبدء.", "/təˈdeɪ/", "تقويم يُشير إلى اليوم الحالي بإشراقة شمس"),
            ),
            lessonNotes = listOf(
                "ركّز على النطق الصحيح للكلمات عبر الضغط على أيقونة الصوت.",
                "كرر الجمل بصوت مسموع لتدريب عضلات النطق واللسان.",
                "احفظ الروابط الذهنية للكلمات لتثبيتها في الذاكرة طويلة المدى.",
            ),
            quiz = listOf(
                JsonQuiz(
                    type = "multiple_choice",
                    question = "ما معنى كلمة «Friend» باللغة العربية؟",
                    options = listOf("صديق", "معلم", "كتاب", "طريق"),
                    answer = "صديق",
                    explanationAr = "Friend تعني صديق، ومثالها: He is my friend.",
                ),
                JsonQuiz(
                    type = "multiple_choice",
                    question = "ما هي الترجمة الصحيحة لعبارة «Welcome to our lesson»؟",
                    options = listOf("أهلاً بك في درسنا", "وداعاً ونلتقي غداً", "أنا أحب القراءة", "شكراً جزيلاً لك"),
                    answer = "أهلاً بك في درسنا",
                    explanationAr = "Welcome تعني أهلاً وسهلاً، وlesson تعني درس.",
                ),
                JsonQuiz(
                    type = "true_false",
                    question = "عبارة «Nice to meet you» تعني «سررت بلقائك».",
                    options = listOf("True", "False"),
                    answer = "True",
                    explanationAr = "العبارة صحيحة، وتُستخدم دائماً عند التعارف الأول.",
                ),
                JsonQuiz(
                    type = "multiple_choice",
                    question = "أي كلمة تعني «يتحدث»؟",
                    options = listOf("Speak", "Learn", "Sleep", "Write"),
                    answer = "Speak",
                    explanationAr = "Speak تعني يتحدث أو ينطق باللغة.",
                ),
            ),
        )

        // 2. درس الصوتيات التأسيسي (Phonetics · Lesson 2)
        runCatching {
            ImportEngine.json.decodeFromString<LessonPackage>(SampleData.samplePhoneticsJson)
        }.getOrNull()?.let { packages += it }

        // 3. درس القواعد (Grammar · Lesson 1)
        packages += LessonPackage(
            metadata = LessonMeta(
                courseId = "grammar_l1",
                courseNameAr = "القواعد",
                level = 1,
                lessonNo = 1,
                title = "ضمائر الفاعل وفعل الكينونة - Subject Pronouns & Verb To Be",
                courseType = "grammar",
            ),
            lessonContent = LessonContent(
                fullTextEn = "I am a learner. You are smart. English is easy and logical when we practice every day.",
                fullTextAr = "أنا متعلّم. أنت ذكي. اللغة الإنجليزية سهلة ومنطقية عندما نتدرب كل يوم.",
                explanationAr = "فعل الكينونة (Verb to be) في المضارع البسيط هو حجر الأساس في تكوين الجمل:\n- نستخدم (am) مع ضمير المتكلم: I am\n- نستخدم (is) مع المفرد الغائب: He is, She is, It is\n- نستخدم (are) مع الجمع والمخاطب: You are, We are, They are",
                keyExpressions = listOf(
                    JsonKeyExpression("I am ready", "أنا جاهز ومستعد", "تعبير للتأكيد على الجاهزية"),
                    JsonKeyExpression("You are capable", "أنت قادر ولديك الإمكانية", "تعبير تشجيعي"),
                ),
                keySentences = listOf(
                    JsonSentence("I am happy today.", "أنا سعيد اليوم."),
                    JsonSentence("You are a great student.", "أنت طالب رائع."),
                    JsonSentence("We are ready for the challenge.", "نحن مستعدون للتحدي."),
                ),
            ),
            globalVocabulary = listOf(
                JsonGlobalWord("always", "دائماً", "I am always consistent.", "أنا دائماً مستمر وملتزم.", "/ˈɔːl.weɪz/", "ساعة تدور بانتظام دون توقف"),
                JsonGlobalWord("happy", "سعيد", "We are happy to learn together.", "نحن سعداء بالتعلم معاً.", "/ˈhæp.i/", "وجه مشرق يبتسم بسعادة وثقة"),
                JsonGlobalWord("ready", "جاهز / مستعد", "You are ready for the test.", "أنت جاهز للاختبار.", "/ˈred.i/", "عداء عند خط البداية بانتظار الانطلاق"),
                JsonGlobalWord("smart", "ذكي", "Consistent study is smart.", "المذاكرة المستمرة تصرف ذكي.", "/smɑːrt/", "مصباح كهربائي متوهج بفكرة عبقرية"),
            ),
            lessonNotes = listOf(
                "في اللغة الإنجليزية لا توجد جملة بدون فعل، لذلك نستخدم فعل الكينونة be عندما لا يوجد فعل حركة.",
                "الاختصارات الشائعة: I am = I'm, You are = You're, He is = He's.",
            ),
            quiz = listOf(
                JsonQuiz(
                    type = "multiple_choice",
                    question = "اختر الفعل المناسب: I ___ ready to study.",
                    options = listOf("am", "is", "are", "be"),
                    answer = "am",
                    explanationAr = "مع الضمير I نستخدم دائماً am في المضارع البسيط.",
                ),
                JsonQuiz(
                    type = "multiple_choice",
                    question = "اختر الفعل المناسب: You ___ a smart learner.",
                    options = listOf("are", "am", "is", "be"),
                    answer = "are",
                    explanationAr = "مع الضمير You نستخدم are دائماً.",
                ),
                JsonQuiz(
                    type = "true_false",
                    question = "نستخدم «is» مع ضمائر المفرد الغائب (He, She, It).",
                    options = listOf("True", "False"),
                    answer = "True",
                    explanationAr = "صحيح، He is / She is / It is.",
                ),
            ),
        )

        // 4. درس القراءة (Reading · Lesson 1)
        packages += LessonPackage(
            metadata = LessonMeta(
                courseId = "reading_l1",
                courseNameAr = "القراءة",
                level = 1,
                lessonNo = 1,
                title = "يومي المشرق - My Bright Day",
                courseType = "reading",
            ),
            lessonContent = LessonContent(
                fullTextEn = "Every morning, I wake up with positive energy. I review my English vocabulary and listen to native speakers. Small daily habits create big results over time. I truly believe that consistency is the key to success.",
                fullTextAr = "كل صباح، أستيقظ بطاقة إيجابية. أراجع مفرداتي الإنجليزية وأستمع إلى المتحدثين الأصليين. العادات اليومية الصغيرة تصنع نتائج كبيرة مع مرور الوقت. أنا أؤمن حقاً بأن الاستمرارية هي مفتاح النجاح.",
                explanationAr = "في هذا النص القرائي البسيط، نتعلم كيف نصف روتيننا اليومي وربط الجمل بأدوات الربط واستيعاب المعنى الإجمالي للنص.",
                keyExpressions = listOf(
                    JsonKeyExpression("Key to success", "مفتاح النجاح", "رمز للسبب الأساسي في التفوق"),
                    JsonKeyExpression("Over time", "مع مرور الوقت", "تعبر عن التطور التدريجي"),
                ),
                keySentences = listOf(
                    JsonSentence("Every morning, I review my words.", "كل صباح، أراجع كلماتي."),
                    JsonSentence("Small habits create big results.", "العادات الصغيرة تصنع نتائج كبيرة."),
                    JsonSentence("Consistency is the key to success.", "الاستمرارية هي مفتاح النجاح."),
                ),
            ),
            globalVocabulary = listOf(
                JsonGlobalWord("morning", "صباح", "Good morning, my friend.", "صباح الخير يا صديقي.", "/ˈmɔːr.nɪŋ/", "شروق شمس ذهبي دافئ في الصباح"),
                JsonGlobalWord("believe", "يؤمن / يثق", "I believe in my potential.", "أنا أؤمن بقدراتي وإمكانياتي.", "/bɪˈliːv/", "قلب ناصع يملؤه اليقين والثقة"),
                JsonGlobalWord("result", "نتيجة", "Hard work brings great results.", "العمل الجاد يجلب نتائج عظيمة.", "/rɪˈzʌlt/", "كأس تفوق ذهبي مع شهادة إنجاز"),
            ),
            lessonNotes = listOf(
                "اقرأ النص بالإنجليزية أولاً وحاول تخمين معاني الكلمات من السياق قبل قراءة الترجمة.",
                "استمع للنص كاملاً بالصوت لترسيخ نبرة الجمل الطبيعية.",
            ),
            quiz = listOf(
                JsonQuiz(
                    type = "multiple_choice",
                    question = "ما هو مفتاح النجاح المذكور في النص؟",
                    options = listOf("الاستمرارية (Consistency)", "السرعة والعجلة", "التوقف عند الصعوبات", "الدراسة لساعة واحدة فقط في الشهر"),
                    answer = "الاستمرارية (Consistency)",
                    explanationAr = "النص يذكر بوضوح: Consistency is the key to success.",
                ),
                JsonQuiz(
                    type = "multiple_choice",
                    question = "ما معنى كلمة «Believe»؟",
                    options = listOf("يؤمن / يثق", "ينسى", "يركض", "يسأل"),
                    answer = "يؤمن / يثق",
                    explanationAr = "Believe تعني يؤمن أو يعتقد بيقين.",
                ),
            ),
        )

        // 5. درس المحادثة (Conversation · Lesson 1)
        packages += LessonPackage(
            metadata = LessonMeta(
                courseId = "conversation_l1",
                courseNameAr = "المحادثة",
                level = 1,
                lessonNo = 1,
                title = "في المقهى - At the Coffee Shop",
                courseType = "conversation",
            ),
            lessonContent = LessonContent(
                fullTextEn = "Barista: Hello! What can I get for you today?\nCustomer: Hello! Could I have a warm coffee, please?\nBarista: Sure! Anything else?\nCustomer: No, thank you. How much is it?\nBarista: That is three dollars, please.\nCustomer: Here you go. Have a great day!",
                fullTextAr = "النادل: مرحباً! ماذا يمكنني أن أقدم لك اليوم؟\nالزبون: مرحباً! هل يمكنني الحصول على قهوة دافئة من فضلك؟\nالنادل: بالتأكيد! أي شيء آخر؟\nالزبون: لا، شكراً لك. كم ثمنها؟\nالنادل: ثلاثة دولارات من فضلك.\nالزبون: تفضل. أتمنى لك يوماً رائعاً!",
                explanationAr = "حوار تطبيقي واقعي لطلب المشروبات والتعامل بلباقة في الأماكن العامة، مع التعرف على صيغ الطلب المؤدب مثل Could I have...",
                dialogue = listOf(
                    JsonDialogue("Barista", "Hello! What can I get for you today?", "مرحباً! ماذا يمكنني أن أقدم لك اليوم؟"),
                    JsonDialogue("Customer", "Could I have a coffee, please?", "هل يمكنني الحصول على قهوة من فضلك؟"),
                    JsonDialogue("Barista", "Sure! That is three dollars.", "بالتأكيد! ثلاثة دولارات."),
                    JsonDialogue("Customer", "Here you go. Thank you very much!", "تفضل. شكراً جزيلاً لك!"),
                ),
                keyExpressions = listOf(
                    JsonKeyExpression("Could I have...", "هل يمكنني الحصول على...", "صيغة طلب مهذبة جداً"),
                    JsonKeyExpression("Here you go", "تفضل (عند تسليم شيء)", "تُقال عند إعطاء النقود أو الشيء للشخص الآخر"),
                    JsonKeyExpression("Have a great day", "أتمنى لك يوماً رائعاً", "وداع لطيف في ختام المعاملات"),
                ),
                keySentences = listOf(
                    JsonSentence("Could I have a coffee, please?", "هل يمكنني الحصول على قهوة من فضلك؟"),
                    JsonSentence("How much is it?", "كم ثمنها؟"),
                    JsonSentence("Here you go, thank you!", "تفضل، شكراً لك!"),
                ),
            ),
            globalVocabulary = listOf(
                JsonGlobalWord("coffee", "قهوة", "I like to drink coffee in the morning.", "أحب شرب القهوة في الصباح.", "/ˈkɑː.fi/", "فنجان قهوة أنيق يتصاعد منه البخار"),
                JsonGlobalWord("please", "من فضلك / رجاءً", "Could you help me, please?", "هل يمكنك مساعدتي من فضلك؟", "/pliːz/", "يدان معبرتان عن اللطف والاحترام"),
                JsonGlobalWord("order", "يطلب / طلب", "I would like to place an order.", "أود تقديم طلب.", "/ˈɔːr.dər/", "قائمة طعام مع قلم لتحديد الطلب"),
            ),
            lessonNotes = listOf(
                "استخدم نبرة صوت صاعدة في نهاية السؤال المؤدب: Could I have a coffee, please?",
                "تدرّب على لعب دور الزبون والنادل لتطبيق الحوار من الذاكرة.",
            ),
            quiz = listOf(
                JsonQuiz(
                    type = "multiple_choice",
                    question = "ما هي العبارة المهذبة لطلب القهوة؟",
                    options = listOf("Could I have a coffee, please?", "Give me coffee now", "I take coffee", "Coffee here"),
                    answer = "Could I have a coffee, please?",
                    explanationAr = "Could I have... please هي الصيغة الإنجليزية الأكثر تهذيباً وشيوعاً للطلب.",
                ),
                JsonQuiz(
                    type = "multiple_choice",
                    question = "ماذا تقول عندما تسلّم النقود أو غرضاً لشخص آخر؟",
                    options = listOf("Here you go", "Go away", "Where is it", "I don't know"),
                    answer = "Here you go",
                    explanationAr = "Here you go تعني «تفضل» عند تسليم شيء.",
                ),
            ),
        )

        packages
    }
}
