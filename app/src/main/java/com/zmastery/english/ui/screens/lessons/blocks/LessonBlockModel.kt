package com.zmastery.english.ui.screens.lessons.blocks

import com.zmastery.english.data.Lesson
import com.zmastery.english.data.LessonStyle

// ==========================================================================
// Universal lesson blocks — THE single source of truth for lesson rendering.
//
// المبدأ: المحتوى يقود الواجهة، وليس نوع الكورس.
// كل درس يتحول إلى قائمة مرتبة من البلوكات؛ كل بلوك يظهر فقط إذا وُجدت بياناته.
// دور الكورس بعد التوحيد: اللون + الأيقونة + ترتيب البلوكات فقط — لا شاشات خاصة.
// ==========================================================================

enum class LessonBlockKind {
    HERO,            // بطاقة العنوان + إحصاءات ديناميكية
    VOCAB_WORDS,     // بطاقات المفردات (كشف/إخفاء + صوت)
    KEY_SENTENCES,   // الجمل الأساسية
    GRAMMAR_RULE,    // القاعدة: شرح + منطق
    EXAMPLES,        // أمثلة القاعدة
    DIALOGUE,        // فقاعات الحوار + تشغيل الكل
    KEY_EXPRESSIONS, // تعبيرات المحادثة المهمة
    READING,         // النص القرائي (مقاطع تفاعلية / نص كامل)
    PHONETICS,       // أصوات + أزواج صغرى + جُمل تدريب
    WRITING,         // موضوع + عصف ذهني + جمل موجّهة + مسودة
    NOTES,           // ملاحظات الدرس
    KEY_POINTS,      // مسرد مصغّر (بديل عند غياب بطاقات المفردات)
    QUIZ,            // بطاقة تشغيل اختبار الدرس
    ACTIONS,         // رابط ذهني + زر الإكمال
}

object LessonBlocks {

    /** الترتيب الافتراضي — يغطي كل البلوكات مهما كان نمط الكورس. */
    private val DEFAULT_ORDER = listOf(
        LessonBlockKind.HERO,
        LessonBlockKind.KEY_SENTENCES,
        LessonBlockKind.VOCAB_WORDS,
        LessonBlockKind.DIALOGUE,
        LessonBlockKind.KEY_EXPRESSIONS,
        LessonBlockKind.GRAMMAR_RULE,
        LessonBlockKind.EXAMPLES,
        LessonBlockKind.READING,
        LessonBlockKind.PHONETICS,
        LessonBlockKind.WRITING,
        LessonBlockKind.NOTES,
        LessonBlockKind.KEY_POINTS,
        LessonBlockKind.QUIZ,
        LessonBlockKind.ACTIONS,
    )

    /**
     * ترتيب معبّر عن «قصة كل نمط» — نفس المحتوى، سرد مختلف.
     * أي بلوك غير مذكور هنا يُلحق تلقائياً بالترتيب الافتراضي فلا يضيع شيء.
     */
    private val ORDER_OVERRIDES: Map<LessonStyle, List<LessonBlockKind>> = mapOf(
        LessonStyle.VOCAB_CARDS to listOf(
            LessonBlockKind.HERO, LessonBlockKind.KEY_SENTENCES, LessonBlockKind.VOCAB_WORDS,
            LessonBlockKind.NOTES, LessonBlockKind.QUIZ, LessonBlockKind.ACTIONS,
        ),
        LessonStyle.GRAMMAR_RULES to listOf(
            LessonBlockKind.HERO, LessonBlockKind.GRAMMAR_RULE, LessonBlockKind.EXAMPLES,
            LessonBlockKind.VOCAB_WORDS, LessonBlockKind.NOTES, LessonBlockKind.QUIZ, LessonBlockKind.ACTIONS,
        ),
        LessonStyle.CONVERSATION to listOf(
            LessonBlockKind.HERO, LessonBlockKind.DIALOGUE, LessonBlockKind.KEY_EXPRESSIONS,
            LessonBlockKind.VOCAB_WORDS, LessonBlockKind.NOTES, LessonBlockKind.QUIZ, LessonBlockKind.ACTIONS,
        ),
        LessonStyle.READING_TEXT to listOf(
            LessonBlockKind.HERO, LessonBlockKind.READING, LessonBlockKind.VOCAB_WORDS,
            LessonBlockKind.NOTES, LessonBlockKind.QUIZ, LessonBlockKind.ACTIONS,
        ),
        LessonStyle.PHONETICS_SOUNDS to listOf(
            LessonBlockKind.HERO, LessonBlockKind.PHONETICS, LessonBlockKind.VOCAB_WORDS,
            LessonBlockKind.NOTES, LessonBlockKind.QUIZ, LessonBlockKind.ACTIONS,
        ),
        LessonStyle.WRITING_PRACTICE to listOf(
            LessonBlockKind.HERO, LessonBlockKind.WRITING, LessonBlockKind.VOCAB_WORDS,
            LessonBlockKind.NOTES, LessonBlockKind.QUIZ, LessonBlockKind.ACTIONS,
        ),
    )

    /** الترتيب الكامل لنمط ما: المخصص أولاً ثم ما تبقى من الافتراضي (بلا تكرار). */
    fun orderFor(style: LessonStyle?): List<LessonBlockKind> {
        val base = ORDER_OVERRIDES[style] ?: DEFAULT_ORDER
        return base + DEFAULT_ORDER.filter { it !in base }
    }

    /**
     * البلوكات الظاهرة لدرس معين = الترتيب ∩ ما له بيانات فعلية.
     * لا بيانات → لا بلوك → لا أقسام فارغة أبداً، مهما كان الكورس.
     */
    fun visibleBlocks(
        lesson: Lesson,
        style: LessonStyle?,
        wordCount: Int,
        hasPhonetics: Boolean,
    ): List<LessonBlockKind> = orderFor(style).filter { kind ->
        when (kind) {
            LessonBlockKind.HERO,
            LessonBlockKind.ACTIONS -> true

            LessonBlockKind.VOCAB_WORDS -> wordCount > 0
            LessonBlockKind.KEY_SENTENCES -> lesson.keySentences.isNotEmpty()
            LessonBlockKind.GRAMMAR_RULE ->
                lesson.explanationAr.isNotBlank() || lesson.logicAr.isNotBlank()
            LessonBlockKind.EXAMPLES -> lesson.examples.isNotEmpty()
            LessonBlockKind.DIALOGUE -> lesson.dialogues.isNotEmpty()
            LessonBlockKind.KEY_EXPRESSIONS -> lesson.keyExpressions.isNotEmpty()

            // النص القرائي يظهر للمقاطع/النص الكامل، أو لنص reading العام فقط
            // حين لا توجد جمل أساسية (كي لا يتكرر المحتوى ذاته مرتين).
            LessonBlockKind.READING ->
                lesson.segments.isNotEmpty() || lesson.fullTextEn.isNotBlank() ||
                    (lesson.readingEn.isNotBlank() && lesson.keySentences.isEmpty())

            LessonBlockKind.PHONETICS -> hasPhonetics
            LessonBlockKind.WRITING ->
                lesson.topicEn.isNotBlank() || lesson.topicAr.isNotBlank() ||
                    lesson.brainstorming.isNotEmpty() || lesson.guidedSentences.isNotEmpty() ||
                    lesson.finalDraft != null
            LessonBlockKind.NOTES -> lesson.notes.isNotEmpty()

            // مسرد مصغّر فقط حين لا توجد بطاقات مفردات تعرض نفس الكلمات.
            LessonBlockKind.KEY_POINTS -> wordCount == 0 && lesson.keyPoints.isNotEmpty()

            LessonBlockKind.QUIZ -> lesson.quiz.isNotEmpty()
        }
    }
}
