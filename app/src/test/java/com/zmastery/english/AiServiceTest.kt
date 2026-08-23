package com.zmastery.english

import com.zmastery.english.data.AiProvider
import com.zmastery.english.domain.usecases.AiService
import com.zmastery.english.domain.usecases.ModelKind
import org.junit.Assert.*
import org.junit.Test

class AiServiceTest {

    private val service = AiService()

    // ─── Key validation ───

    @Test
    fun `valid Gemini key passes`() {
        val result = service.validateKeyFormat("AIzaSyA1234567890abcdefghijklmnopqrstu", AiProvider.GEMINI)
        assertTrue(result.valid)
    }

    @Test
    fun `short Gemini key fails`() {
        val result = service.validateKeyFormat("AIzaSyShort", AiProvider.GEMINI)
        assertFalse(result.valid)
        assertTrue(result.message.contains("39"))
    }

    @Test
    fun `valid OpenAI key passes`() {
        val result = service.validateKeyFormat("sk-abc123def456ghi789jkl0", AiProvider.OPENAI)
        assertTrue(result.valid)
    }

    @Test
    fun `OpenAI key without sk- prefix fails`() {
        val result = service.validateKeyFormat("abc123def456ghi789jkl0mnop", AiProvider.OPENAI)
        assertFalse(result.valid)
    }

    @Test
    fun `empty key fails for any provider`() {
        assertFalse(service.validateKeyFormat("", AiProvider.GEMINI).valid)
        assertFalse(service.validateKeyFormat("   ", AiProvider.OPENAI).valid)
    }

    @Test
    fun `xAI key with prefix passes`() {
        val result = service.validateKeyFormat("xai-test123", AiProvider.XAI)
        assertTrue(result.valid)
    }

    // ─── Provider detection ───

    @Test
    fun `detect Gemini from AIzaSy prefix`() {
        assertEquals(AiProvider.GEMINI, service.detectProvider("AIzaSyABC"))
    }

    @Test
    fun `detect OpenAI from sk- prefix`() {
        assertEquals(AiProvider.OPENAI, service.detectProvider("sk-abc"))
    }

    @Test
    fun `detect xAI from xai- prefix`() {
        assertEquals(AiProvider.XAI, service.detectProvider("xai-abc"))
    }

    @Test
    fun `detect compatible for unknown prefix`() {
        assertEquals(AiProvider.OPENAI_COMPATIBLE, service.detectProvider("my-custom-key"))
    }

    // ─── Model classification ───

    @Test
    fun `classify flash model as FAST`() {
        assertEquals(ModelKind.FAST, service.classifyModel("gemini-2.0-flash"))
    }

    @Test
    fun `classify pro model as POWERFUL`() {
        assertEquals(ModelKind.POWERFUL, service.classifyModel("gemini-1.5-pro"))
    }

    @Test
    fun `classify embedding model`() {
        assertEquals(ModelKind.EMBEDDING, service.classifyModel("text-embedding-004"))
    }

    @Test
    fun `classify TTS model`() {
        assertEquals(ModelKind.TTS, service.classifyModel("gemini-tts-v1"))
    }

    @Test
    fun `unknown model classified as GENERAL`() {
        assertEquals(ModelKind.GENERAL, service.classifyModel("some-unknown-model"))
    }

    // ─── Free tier detection ───

    @Test
    fun `flash models are likely free`() {
        assertTrue(service.isLikelyFree("gemini-2.0-flash"))
    }

    @Test
    fun `pro models are not free`() {
        assertFalse(service.isLikelyFree("gemini-1.5-pro"))
    }

    // ─── Quota estimation ───

    @Test
    fun `flash model has 1500 daily limit`() {
        val quota = service.estimateQuota("gemini-2.0-flash", 0)
        assertEquals(1500, quota.dailyLimit)
        assertEquals(1500, quota.remaining)
        assertFalse(quota.isNearLimit)
    }

    @Test
    fun `near limit when usage is high`() {
        val quota = service.estimateQuota("gemini-2.0-flash", 1400)
        assertTrue(quota.isNearLimit)
        assertEquals(100, quota.remaining)
    }

    @Test
    fun `zero remaining when over limit`() {
        val quota = service.estimateQuota("gemini-2.0-flash", 2000)
        assertEquals(0, quota.remaining)
    }

    // ─── Cost estimation ───

    @Test
    fun `flash model cost is very low`() {
        val cost = service.estimateCost(1000, 500, "gemini-2.0-flash")
        assertTrue("Flash cost should be < 0.01", cost < 0.01)
    }

    @Test
    fun `pro model cost is higher`() {
        val flashCost = service.estimateCost(10000, 5000, "gemini-2.0-flash")
        val proCost = service.estimateCost(10000, 5000, "gemini-1.5-pro")
        assertTrue("Pro > Flash", proCost > flashCost)
    }
}
