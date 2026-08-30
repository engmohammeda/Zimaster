package com.zmastery.english.domain.usecases

import com.zmastery.english.data.ArchivedStory
import com.zmastery.english.data.Dialogue
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.PhoneticsParser
import com.zmastery.english.data.VocabWord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Pure training-skills engine — scoring, material selection, conversation
 * parsing. No Android, no Compose: unit-tested in isolation.
 */
object SkillsEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // ── tokenisation / scoring ──────────────────────────────────────────

    private val WORD = Regex("[a-zA-Z']+")

    fun wordsOf(text: String): List<String> =
        WORD.findAll(text.lowercase()).map { it.value }.filter { it.length > 1 || it in setOf("a", "i") }.toList()

    /**
     * F1 overlap between a reference passage and what the learner produced
     * (spoken transcript or typed dictation).
     */
    fun overlapScore(reference: String, attempt: String): SkillScore {
        val ref = wordsOf(reference)
        val att = wordsOf(attempt)
        if (ref.isEmpty()) return SkillScore(0, 0, 0, emptyList(), att)
        val refSet = ref.toSet()
        val attSet = att.toSet()
        val matched = refSet.intersect(attSet)
        val missed = ref.filter { it !in attSet }.distinct()
        val extra = att.filter { it !in refSet }.distinct()
        val recall = matched.size.toFloat() / refSet.size
        val precision = if (attSet.isEmpty()) 0f else matched.size.toFloat() / attSet.size
        val f1 = if (precision + recall == 0f) 0f else 2f * precision * recall / (precision + recall)
        return SkillScore(
            percent = (f1 * 100).roundToInt().coerceIn(0, 100),
            matched = matched.size,
            total = refSet.size,
            missed = missed.take(12),
            extra = extra.take(8),
        )
    }

    // ── conversation ────────────────────────────────────────────────────

    fun buildConversationSystem(
        character: String,
        style: String,
        prompt: String,
        sceneTitle: String,
        sceneContext: String,
        history: List<ChatTurn>,
        level: String,
    ): String = buildString {
        appendLine(prompt.ifBlank {
            "You are a friendly English conversation partner for an Arabic-speaking learner."
        }.replace("{DIALOGUE}", sceneContext).replace("{LEVEL}", level))
        appendLine()
        if (character.isNotBlank()) appendLine("Persona: $character")
        if (style.isNotBlank()) appendLine("Style: $style")
        appendLine("Scene: $sceneTitle")
        if (sceneContext.isNotBlank()) {
            appendLine("Scene material (stay inside this context):")
            appendLine(sceneContext.take(1200))
        }
        appendLine()
        appendLine("Rules:")
        appendLine("- Speak English at the learner's level ($level). Keep replies to 1–2 short sentences.")
        appendLine("- Always ask a short follow-up question so the dialogue continues.")
        appendLine("- If the learner made a grammar/word error, put a kind correction in \"correction\" (English + brief Arabic).")
        appendLine("- Never break character. Never lecture. Never reply in Arabic as the spoken line.")
        appendLine("- Reply ONLY with compact JSON:")
        appendLine("""{"reply_en":"...","reply_ar":"...","correction":"","praise":""}""")
        if (history.isNotEmpty()) {
            appendLine()
            appendLine("Recent turns:")
            history.takeLast(8).forEach { t ->
                val who = if (t.fromLearner) "Learner" else "You"
                appendLine("$who: ${t.en}")
            }
        }
    }

    fun parseConversationReply(raw: String, fallbackEn: String = ""): ConversationReply {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val parsed = runCatching { json.decodeFromString(ConversationReplyDto.serializer(), cleaned) }.getOrNull()
        if (parsed != null && parsed.reply_en.isNotBlank()) {
            return ConversationReply(
                replyEn = parsed.reply_en.trim(),
                replyAr = parsed.reply_ar.trim(),
                correction = parsed.correction.trim(),
                praise = parsed.praise.trim(),
            )
        }
        val en = cleaned.ifBlank { fallbackEn }.ifBlank { "Could you say that again, please?" }
        return ConversationReply(replyEn = en.take(400))
    }

    fun fallbackPartnerLine(script: List<String>, partnerTurnsSoFar: Int): ConversationReply {
        if (script.isEmpty()) {
            return ConversationReply(
                replyEn = "Tell me more — what happened next?",
                replyAr = "أخبرني بالمزيد — ماذا حدث بعد ذلك؟",
            )
        }
        val line = script[partnerTurnsSoFar.coerceAtLeast(0) % script.size]
        return ConversationReply(replyEn = line)
    }

    fun defaultCafeScript(): List<String> = listOf(
        "Hello! Welcome. What would you like today?",
        "Great choice. Anything to drink with that?",
        "Perfect. How has your day been so far?",
        "Nice! Do you come here often?",
        "Would you like anything else?",
        "Thank you! Have a wonderful day.",
    )

    // ── writing ─────────────────────────────────────────────────────────

    fun buildWritingSystem(
        targetWord: String,
        promptEn: String,
        level: String,
        character: String = "",
        style: String = "",
        prompt: String = "",
    ): String = buildString {
        val filled = com.zmastery.english.data.AiPrompts.fill(
            prompt,
            mapOf(
                "LEVEL" to level,
                "PROMPT" to promptEn,
                "WORD" to targetWord,
            ),
        )
        if (filled.isNotBlank()) appendLine(filled) else {
            appendLine("You are a kind English writing tutor for an Arabic-speaking learner (level $level).")
            appendLine("The learner was asked: $promptEn")
            if (targetWord.isNotBlank()) appendLine("They should use the word: $targetWord")
        }
        if (character.isNotBlank()) appendLine("Persona: $character")
        if (style.isNotBlank()) appendLine("Tone: $style")
        appendLine("Score 0–100. Correct grammar and word choice. Keep the learner's meaning.")
        appendLine("Reply ONLY with JSON:")
        appendLine("""{"score":80,"corrected":"...","notes_ar":"ملاحظات قصيرة بالعربية"}""")
    }

    fun parseWritingFeedback(raw: String, original: String, targetWord: String): WritingFeedback {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val parsed = runCatching { json.decodeFromString(WritingFeedbackDto.serializer(), cleaned) }.getOrNull()
        if (parsed != null) {
            return WritingFeedback(
                score = parsed.score.coerceIn(0, 100),
                corrected = parsed.corrected.ifBlank { original }.trim(),
                notesAr = parsed.notes_ar.trim(),
                usedTargetWord = targetWord.isBlank() || original.contains(targetWord, ignoreCase = true),
            )
        }
        return localWritingCheck(original, targetWord)
    }

    fun localWritingCheck(text: String, targetWord: String?): WritingFeedback {
        val words = wordsOf(text)
        val used = targetWord.isNullOrBlank() || text.contains(targetWord, ignoreCase = true)
        val lenScore = when {
            words.size >= 8 -> 70
            words.size >= 5 -> 55
            words.size >= 3 -> 40
            words.isNotEmpty() -> 25
            else -> 0
        }
        val bonus = if (used) 15 else 0
        val notes = buildString {
            if (words.isEmpty()) append("اكتب جملة واحدة على الأقل.")
            else {
                append("عدد الكلمات: ${words.size}.")
                if (!used && !targetWord.isNullOrBlank()) append(" لم تُستخدم الكلمة المطلوبة «$targetWord».")
                else append(" جيد — أكمل بتدريب أطول أو فعّل الذكاء الاصطناعي لتصحيح أدق.")
            }
        }
        return WritingFeedback(
            score = (lenScore + bonus).coerceIn(0, 100),
            corrected = text.trim(),
            notesAr = notes,
            usedTargetWord = used,
        )
    }

    // ── material from the learner's library ─────────────────────────────

    fun readingPassages(lessons: List<Lesson>): List<TrainingPassage> {
        val out = mutableListOf<TrainingPassage>()
        lessons.forEach { l ->
            val en = l.fullTextEn.ifBlank { l.readingEn }.trim()
            val ar = l.fullTextAr.ifBlank { l.readingAr }.trim()
            if (en.length >= 20) {
                out += TrainingPassage(
                    id = "read-${l.id}",
                    title = l.title.ifBlank { "درس ${l.no}" },
                    en = en,
                    ar = ar,
                    source = "درس",
                )
            }
            l.segments.forEachIndexed { i, s ->
                if (s.en.length >= 20) {
                    out += TrainingPassage(
                        id = "seg-${l.id}-$i",
                        title = "${l.title} · مقطع ${i + 1}",
                        en = s.en, ar = s.ar, source = "مقطع",
                    )
                }
            }
        }
        if (out.isEmpty()) out += defaultReading()
        return out.distinctBy { it.en.lowercase() }
    }

    fun listeningPassages(
        lessons: List<Lesson>,
        stories: List<ArchivedStory>,
        vocab: List<VocabWord>,
    ): List<TrainingPassage> {
        val out = mutableListOf<TrainingPassage>()
        stories.forEach { s ->
            if (s.en.length >= 20) {
                out += TrainingPassage("story-${s.id}", s.title.ifBlank { "قصة" }, s.en, s.ar, "قصة")
            }
        }
        lessons.forEach { l ->
            l.dialogues.forEachIndexed { i, d ->
                if (d.en.length >= 8) {
                    out += TrainingPassage("dlg-${l.id}-$i", "${d.speaker} · ${l.title}", d.en, d.ar, "حوار")
                }
            }
            val reading = l.readingEn.ifBlank { l.fullTextEn }
            if (reading.length >= 20) {
                out += TrainingPassage("lis-${l.id}", l.title, reading, l.readingAr.ifBlank { l.fullTextAr }, "درس")
            }
        }
        vocab.filter { it.exampleEn.length >= 8 }.take(20).forEach { w ->
            out += TrainingPassage("ex-${w.id}", w.english, w.exampleEn, w.exampleAr, "مثال")
        }
        if (out.isEmpty()) out += defaultListening()
        return out.distinctBy { it.en.lowercase() }
    }

    fun conversationScenes(lessons: List<Lesson>): List<ConversationScene> {
        val out = mutableListOf<ConversationScene>()
        lessons.filter { it.dialogues.isNotEmpty() }.forEach { l ->
            out += ConversationScene(
                id = "lesson-${l.id}",
                title = l.title.ifBlank { "حوار الدرس ${l.no}" },
                subtitle = "${l.dialogues.size} جملة · من درسك",
                script = l.dialogues.map { it.en },
                context = l.dialogues.joinToString("\n") { "${it.speaker}: ${it.en}" },
                starter = l.dialogues.first().en,
                starterAr = l.dialogues.first().ar,
            )
        }
        out += ConversationScene(
            id = "cafe",
            title = "في المقهى",
            subtitle = "حوار حر للتدريب اليومي",
            script = defaultCafeScript(),
            context = "A casual conversation at a café. The tutor is a friendly barista.",
            starter = defaultCafeScript().first(),
            starterAr = "مرحباً! أهلاً بك. ماذا تحب أن تطلب اليوم؟",
        )
        out += ConversationScene(
            id = "intro",
            title = "التعارف",
            subtitle = "عرّف بنفسك وتحدّث عن يومك",
            script = listOf(
                "Hi! What's your name?",
                "Nice to meet you. Where are you from?",
                "That's interesting. What do you do?",
                "Cool. What do you like to do on weekends?",
                "I hope we talk again soon!",
            ),
            context = "Two people meeting for the first time. Keep it warm and simple.",
            starter = "Hi! What's your name?",
            starterAr = "مرحباً! ما اسمك؟",
        )
        return out
    }

    fun writingPrompts(vocab: List<VocabWord>, lessons: List<Lesson>): List<WritingPrompt> {
        val out = mutableListOf<WritingPrompt>()
        lessons.filter { it.topicEn.isNotBlank() }.forEach { l ->
            out += WritingPrompt(
                id = "topic-${l.id}",
                title = l.title,
                promptEn = l.topicEn,
                promptAr = l.topicAr.ifBlank { "اكتب عن موضوع الدرس" },
                targetWord = "",
            )
        }
        vocab.filter { it.english.isNotBlank() }.take(30).forEach { w ->
            out += WritingPrompt(
                id = "word-${w.id}",
                title = w.english,
                promptEn = "Write 2–3 sentences using the word \"${w.english}\".",
                promptAr = "اكتب جملتين أو ثلاثاً تستخدم فيها كلمة «${w.english}» (${w.arabic}).",
                targetWord = w.english,
            )
        }
        if (out.isEmpty()) {
            out += WritingPrompt(
                id = "default-day",
                title = "يومك",
                promptEn = "Write three sentences about your day.",
                promptAr = "اكتب ثلاث جمل عن يومك.",
                targetWord = "",
            )
        }
        return out
    }

    fun phoneticDrills(lessons: List<Lesson>): List<PhoneticDrill> {
        val out = mutableListOf<PhoneticDrill>()
        lessons.forEach { l ->
            if (l.rawJson.isBlank()) return@forEach
            val ph = PhoneticsParser.parse(l.rawJson) ?: return@forEach
            ph.content.focusSounds.forEach { s ->
                val examples = ph.content.minimalPairs
                    .flatMap { listOf(it.word1, it.word2) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(6)
                out += PhoneticDrill(
                    id = "ph-${l.id}-${s.symbol}",
                    symbol = s.symbol,
                    description = s.description,
                    examples = if (examples.isNotEmpty()) examples else listOf(s.symbol),
                )
            }
            ph.content.minimalPairs.forEach { p ->
                if (p.word1.isNotBlank() && p.word2.isNotBlank()) {
                    out += PhoneticDrill(
                        id = "pair-${l.id}-${p.word1}-${p.word2}",
                        symbol = "${p.word1} / ${p.word2}",
                        description = "زوج أدنى — فرّق بين الكلمتين",
                        examples = listOf(p.word1, p.word2),
                        isPair = true,
                    )
                }
            }
        }
        if (out.none { !it.isPair }) out.addAll(0, defaultPhonetics())
        return out.distinctBy { it.id }
    }

    fun defaultPhonetics(): List<PhoneticDrill> = listOf(
        PhoneticDrill("th-v", "/θ/", "صوت th المهموس كما في think", listOf("think", "three", "thank", "path")),
        PhoneticDrill("th-z", "/ð/", "صوت th المجهور كما في this", listOf("this", "that", "the", "brother")),
        PhoneticDrill("sh", "/ʃ/", "صوت sh كما في she", listOf("she", "ship", "fish", "wash")),
        PhoneticDrill("ch", "/tʃ/", "صوت ch كما في chair", listOf("chair", "watch", "teacher", "lunch")),
        PhoneticDrill("ng", "/ŋ/", "صوت ng كما في sing", listOf("sing", "ring", "long", "going")),
        PhoneticDrill("ae", "/æ/", "صوت a القصير كما في cat", listOf("cat", "man", "apple", "black")),
        PhoneticDrill("ih", "/ɪ/", "صوت i القصير كما في sit", listOf("sit", "big", "fish", "it")),
        PhoneticDrill("eh", "/e/", "صوت e القصير كما في bed", listOf("bed", "red", "pen", "yes")),
    )

    private fun defaultReading() = TrainingPassage(
        id = "default-read",
        title = "قطعة للتدريب",
        en = "The sun is bright today. I walk to the park and see a small cat. The cat is happy and sits under a tree. I smile and say hello.",
        ar = "الشمس مشرقة اليوم. أمشي إلى الحديقة وأرى قطة صغيرة. القطة سعيدة وتجلس تحت شجرة. أبتسم وأقول مرحباً.",
        source = "تدريب",
    )

    private fun defaultListening() = listOf(
        TrainingPassage("default-hi", "تحية", "Hello, how are you today?", "مرحباً، كيف حالك اليوم؟", "تدريب"),
        TrainingPassage("default-park", "الحديقة", "I like to walk in the park every morning.", "أحب المشي في الحديقة كل صباح.", "تدريب"),
        TrainingPassage("default-tea", "الشاي", "Would you like a cup of tea?", "هل تحب فنجان شاي؟", "تدريب"),
    )
}

data class SkillScore(
    val percent: Int,
    val matched: Int,
    val total: Int,
    val missed: List<String>,
    val extra: List<String>,
) {
    val grade: String get() = when {
        percent >= 90 -> "ممتاز"
        percent >= 75 -> "جيد جداً"
        percent >= 60 -> "جيد"
        percent >= 40 -> "يحتاج تمريناً"
        else -> "أعد المحاولة"
    }
}

data class ChatTurn(
    val fromLearner: Boolean,
    val en: String,
    val ar: String = "",
    val correction: String = "",
    val praise: String = "",
)

data class ConversationReply(
    val replyEn: String,
    val replyAr: String = "",
    val correction: String = "",
    val praise: String = "",
)

data class WritingFeedback(
    val score: Int,
    val corrected: String,
    val notesAr: String,
    val usedTargetWord: Boolean,
)

data class TrainingPassage(
    val id: String,
    val title: String,
    val en: String,
    val ar: String,
    val source: String,
)

data class ConversationScene(
    val id: String,
    val title: String,
    val subtitle: String,
    val script: List<String>,
    val context: String,
    val starter: String,
    val starterAr: String,
)

data class WritingPrompt(
    val id: String,
    val title: String,
    val promptEn: String,
    val promptAr: String,
    val targetWord: String,
)

data class PhoneticDrill(
    val id: String,
    val symbol: String,
    val description: String,
    val examples: List<String>,
    val isPair: Boolean = false,
)

@Serializable
internal data class ConversationReplyDto(
    val reply_en: String = "",
    val reply_ar: String = "",
    val correction: String = "",
    val praise: String = "",
)

@Serializable
internal data class WritingFeedbackDto(
    val score: Int = 0,
    val corrected: String = "",
    val notes_ar: String = "",
)
