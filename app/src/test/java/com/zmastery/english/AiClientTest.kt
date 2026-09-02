package com.zmastery.english

import com.zmastery.english.data.AiClient
import com.zmastery.english.data.AiModel
import com.zmastery.english.data.ModelKind
import org.junit.Assert.*
import org.junit.Test

class AiClientTest {

    @Test
    fun `native-audio is LIVE not TTS`() {
        assertEquals(
            ModelKind.LIVE,
            AiClient.classify("gemini-2.5-flash-native-audio-preview-09-2025"),
        )
    }

    @Test
    fun `live id is LIVE`() {
        assertEquals(ModelKind.LIVE, AiClient.classify("gemini-2.0-flash-live-001"))
    }

    @Test
    fun `flash stays TEXT`() {
        assertEquals(ModelKind.TEXT, AiClient.classify("gemini-2.5-flash"))
    }

    @Test
    fun `tts id is TTS`() {
        assertEquals(ModelKind.TTS, AiClient.classify("gemini-2.5-flash-preview-tts"))
    }

    @Test
    fun `native-audio cannot generateContent`() {
        assertFalse(
            AiClient.canGenerateText("gemini-2.5-flash-native-audio-preview-09-2025"),
        )
        assertFalse(AiClient.canGenerateText("models/gemini-2.5-flash-native-audio-preview-09-2025"))
    }

    @Test
    fun `text flash can generateContent`() {
        assertTrue(AiClient.canGenerateText("gemini-2.5-flash"))
        assertFalse(AiClient.canGenerateText(""))
        assertFalse(AiClient.canGenerateText("gemini-2.5-flash-tts"))
        assertFalse(AiClient.canGenerateText("imagen-4.0"))
    }

    @Test
    fun `text fallback keeps a TEXT id`() {
        val catalogue = listOf(
            AiModel("gemini-2.5-flash-native-audio-preview-09-2025", "Live", ModelKind.LIVE),
            AiModel("gemini-2.5-flash", "Flash", ModelKind.TEXT),
            AiModel("gemini-2.5-flash-tts", "TTS", ModelKind.TTS),
        )
        assertEquals(
            "gemini-2.5-flash",
            AiClient.textFallbackId("gemini-2.5-flash-native-audio-preview-09-2025", catalogue),
        )
    }

    @Test
    fun `text fallback passes through a TEXT request`() {
        val catalogue = listOf(AiModel("gemini-2.5-pro", "Pro", ModelKind.TEXT))
        assertEquals("gemini-2.5-pro", AiClient.textFallbackId("gemini-2.5-pro", catalogue))
    }

    @Test
    fun `text fallback uses builtin when catalogue has no TEXT`() {
        val catalogue = listOf(
            AiModel("gemini-2.5-flash-native-audio-preview-09-2025", "Live", ModelKind.LIVE),
        )
        assertEquals(
            AiClient.DEFAULT_TEXT_MODEL,
            AiClient.textFallbackId("gemini-2.5-flash-native-audio-preview-09-2025", catalogue),
        )
    }
}
