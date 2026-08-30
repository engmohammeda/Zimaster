package com.zmastery.english

import com.zmastery.english.data.AgentGroup
import com.zmastery.english.data.AiDefaults
import com.zmastery.english.data.AiPrompts
import com.zmastery.english.data.ModelKind
import com.zmastery.english.domain.usecases.SkillsEngine
import org.junit.Assert.*
import org.junit.Test

class AiPromptsTest {

    @Test
    fun `every agent has a unique id and a professional prompt`() {
        val agents = AiPrompts.agents()
        assertEquals(agents.size, agents.map { it.id }.toSet().size)
        assertTrue(agents.size >= 12)
        agents.forEach { a ->
            assertTrue("${a.id} prompt too short (${a.prompt.length})", a.prompt.length >= 500)
            assertTrue("${a.id} has no character", a.character.length >= 40)
            assertTrue("${a.id} has no style", a.style.isNotBlank())
            assertEquals(listOf(a.kind), a.kind.pickerKinds)
        }
    }

    @Test
    fun `skill agents are grouped together`() {
        assertEquals(AgentGroup.SKILLS, AiPrompts.groupOf("conversation"))
        assertEquals(AgentGroup.SKILLS, AiPrompts.groupOf("writing"))
        assertEquals(AgentGroup.SKILLS, AiPrompts.groupOf("listening"))
        assertEquals(AgentGroup.SKILLS, AiPrompts.groupOf("reading"))
        assertEquals(AgentGroup.SKILLS, AiPrompts.groupOf("phonetics"))
        assertEquals(AgentGroup.CONTENT, AiPrompts.groupOf("story_writer"))
        assertEquals(AgentGroup.TEACHING, AiPrompts.groupOf("coach"))
        assertEquals(AgentGroup.VOICE, AiPrompts.groupOf("story_reader"))
    }

    @Test
    fun `fill replaces tokens without braces or with braces`() {
        val raw = "Speak at {LEVEL} about {WORDS}."
        val out = AiPrompts.fill(raw, mapOf("LEVEL" to "A2", "{WORDS}" to "garden, tea"))
        assertEquals("Speak at A2 about garden, tea.", out)
    }

    @Test
    fun `defaults match AiDefaults and conversation stays LIVE`() {
        val fromDefaults = AiDefaults.agents()
        val fromStudio = AiPrompts.agents()
        assertEquals(fromStudio.map { it.id }, fromDefaults.map { it.id })
        val talk = fromStudio.first { it.id == "conversation" }
        assertEquals(ModelKind.LIVE, talk.kind)
        assertTrue(talk.prompt.contains("{DIALOGUE}"))
        assertTrue(talk.kind.usesVoice)
    }

    @Test
    fun `writing system prompt carries persona and JSON contract`() {
        val writer = AiPrompts.defaultOf("writing")!!
        val sys = SkillsEngine.buildWritingSystem(
            targetWord = "garden",
            promptEn = "Write about your garden.",
            level = "A2",
            character = writer.character,
            style = writer.style,
            prompt = writer.prompt,
        )
        assertTrue(sys.contains("A2"))
        assertTrue(sys.contains("garden"))
        assertTrue(sys.contains("notes_ar"))
        assertTrue(sys.contains("Persona:"))
        assertTrue(sys.contains("Tone:"))
    }

    @Test
    fun `legacy detector flags the old one-liner`() {
        val fresh = AiPrompts.defaultOf("coach")!!
        val old = fresh.copy(prompt = "حلّل {STATS}.")
        assertTrue(AiPrompts.isLegacy(old))
        assertFalse(AiPrompts.isLegacy(fresh))
    }

    @Test
    fun `tone presets are applied on agents`() {
        AiPrompts.agents().forEach { a ->
            assertNotNull("${a.id} style is not a known tone", AiPrompts.matchingTone(a.style))
        }
    }
}
