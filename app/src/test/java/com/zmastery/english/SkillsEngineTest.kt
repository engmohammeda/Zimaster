package com.zmastery.english

import com.zmastery.english.data.Dialogue
import com.zmastery.english.data.Lesson
import com.zmastery.english.data.ModelKind
import com.zmastery.english.data.VocabWord
import com.zmastery.english.domain.usecases.ChatTurn
import com.zmastery.english.domain.usecases.SkillsEngine
import org.junit.Assert.*
import org.junit.Test

class SkillsEngineTest {

    @Test
    fun `wordsOf strips punctuation and lowercases`() {
        val words = SkillsEngine.wordsOf("Hello, world! I'm here.")
        assertEquals(listOf("hello", "world", "i'm", "here"), words)
    }

    @Test
    fun `identical passage scores 100`() {
        val text = "The sun is bright today."
        val score = SkillsEngine.overlapScore(text, text)
        assertEquals(100, score.percent)
        assertTrue(score.missed.isEmpty())
        assertEquals("ممتاز", score.grade)
    }

    @Test
    fun `partial overlap is between 0 and 100`() {
        val score = SkillsEngine.overlapScore(
            "I walk to the park and see a small cat",
            "I walk to the park",
        )
        assertTrue(score.percent in 1..99)
        assertTrue(score.missed.contains("see") || score.missed.contains("cat"))
    }

    @Test
    fun `empty attempt scores 0`() {
        val score = SkillsEngine.overlapScore("Hello there friend", "")
        assertEquals(0, score.percent)
        assertEquals(0, score.matched)
    }

    @Test
    fun `parse conversation JSON`() {
        val raw = """{"reply_en":"How are you?","reply_ar":"كيف حالك؟","correction":"Use am not is","praise":"Nice!"}"""
        val r = SkillsEngine.parseConversationReply(raw)
        assertEquals("How are you?", r.replyEn)
        assertEquals("كيف حالك؟", r.replyAr)
        assertEquals("Use am not is", r.correction)
        assertEquals("Nice!", r.praise)
    }

    @Test
    fun `parse conversation falls back to plain text`() {
        val r = SkillsEngine.parseConversationReply("See you tomorrow!")
        assertEquals("See you tomorrow!", r.replyEn)
    }

    @Test
    fun `fallback partner cycles the script`() {
        val script = listOf("Hi", "Bye")
        assertEquals("Hi", SkillsEngine.fallbackPartnerLine(script, 0).replyEn)
        assertEquals("Bye", SkillsEngine.fallbackPartnerLine(script, 1).replyEn)
        assertEquals("Hi", SkillsEngine.fallbackPartnerLine(script, 2).replyEn)
    }

    @Test
    fun `local writing rewards target word`() {
        val withWord = SkillsEngine.localWritingCheck("I see a cat in the garden today", "cat")
        val without = SkillsEngine.localWritingCheck("I see a dog in the garden today", "cat")
        assertTrue(withWord.usedTargetWord)
        assertFalse(without.usedTargetWord)
        assertTrue(withWord.score > without.score)
    }

    @Test
    fun `empty writing scores zero`() {
        val fb = SkillsEngine.localWritingCheck("  ", "hello")
        assertEquals(0, fb.score)
        assertFalse(fb.usedTargetWord)
    }

    @Test
    fun `reading passages fall back to a default`() {
        val passages = SkillsEngine.readingPassages(emptyList())
        assertTrue(passages.isNotEmpty())
        assertTrue(passages.first().en.length >= 20)
    }

    @Test
    fun `reading passages use lesson text`() {
        val lesson = Lesson(
            id = 1, courseId = 1, no = 1, title = "Park",
            summaryAr = "", readingEn = "I walk in the green park every morning.",
            readingAr = "أمشي", keyPoints = emptyList(),
        )
        val passages = SkillsEngine.readingPassages(listOf(lesson))
        assertTrue(passages.any { it.en.contains("green park") })
    }

    @Test
    fun `conversation scenes always include cafe and intro`() {
        val scenes = SkillsEngine.conversationScenes(emptyList())
        assertTrue(scenes.any { it.id == "cafe" })
        assertTrue(scenes.any { it.id == "intro" })
    }

    @Test
    fun `conversation scenes include lesson dialogues`() {
        val lesson = Lesson(
            id = 9, courseId = 1, no = 2, title = "At school",
            summaryAr = "", readingEn = "", readingAr = "", keyPoints = emptyList(),
            dialogues = listOf(Dialogue("Tom", "Hi, are you new here?", "مرحبا")),
        )
        val scenes = SkillsEngine.conversationScenes(listOf(lesson))
        assertTrue(scenes.any { it.id == "lesson-9" })
        assertEquals("Hi, are you new here?", scenes.first { it.id == "lesson-9" }.starter)
    }

    @Test
    fun `writing prompts use vocab words`() {
        val w = VocabWord(id = 3, english = "garden", arabic = "حديقة", exampleEn = "", exampleAr = "", phonetic = "", mentalImage = "")
        val prompts = SkillsEngine.writingPrompts(listOf(w), emptyList())
        assertTrue(prompts.any { it.targetWord == "garden" })
    }

    @Test
    fun `default phonetics are never empty`() {
        val drills = SkillsEngine.phoneticDrills(emptyList())
        assertTrue(drills.size >= 6)
        assertTrue(drills.any { it.symbol.contains("θ") || it.id == "th-v" })
    }

    @Test
    fun `conversation system prompt asks for JSON and includes history`() {
        val sys = SkillsEngine.buildConversationSystem(
            character = "friend",
            style = "warm",
            prompt = "Stay in scene: {DIALOGUE}",
            sceneTitle = "Cafe",
            sceneContext = "A barista greets you.",
            history = listOf(ChatTurn(true, "Hello")),
            level = "A2",
        )
        assertTrue(sys.contains("reply_en"))
        assertTrue(sys.contains("Learner: Hello"))
        assertTrue(sys.contains("A barista greets you."))
        assertTrue(sys.contains("A2"))
    }

    @Test
    fun `pickerKinds stay inside the agent's skill`() {
        assertEquals(listOf(ModelKind.TTS), ModelKind.TTS.pickerKinds)
        assertEquals(listOf(ModelKind.IMAGE), ModelKind.IMAGE.pickerKinds)
        assertEquals(listOf(ModelKind.LIVE, ModelKind.TEXT), ModelKind.LIVE.pickerKinds)
        assertEquals(listOf(ModelKind.TEXT), ModelKind.TEXT.pickerKinds)
        assertTrue(ModelKind.TTS.usesVoice)
        assertTrue(ModelKind.LIVE.usesVoice)
        assertFalse(ModelKind.TEXT.usesVoice)
        assertFalse(ModelKind.IMAGE.usesVoice)
    }

    @Test
    fun `parse writing JSON`() {
        val raw = """{"score":82,"corrected":"I am happy.","notes_ar":"أحسنت"}"""
        val fb = SkillsEngine.parseWritingFeedback(raw, "I is happy", "happy")
        assertEquals(82, fb.score)
        assertEquals("I am happy.", fb.corrected)
        assertEquals("أحسنت", fb.notesAr)
        assertTrue(fb.usedTargetWord)
    }
}
