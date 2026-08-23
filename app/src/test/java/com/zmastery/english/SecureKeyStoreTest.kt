package com.zmastery.english

import com.zmastery.english.data.KeyProtector
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for KeyProtector — the API key security layer.
 * SecureKeyStore requires Android Keystore and can only be tested on-device.
 */
class SecureKeyStoreTest {

    // ─── KeyProtector masking ───

    @Test
    fun `mask Gemini key shows only last 4 chars`() {
        val key = "AIzaSyA1234567890abcdefghijklmnopqrstu"
        val masked = KeyProtector.mask(key)
        assertEquals(4, masked.count { it != '•' })
        assertTrue(masked.endsWith("rstu"))
        assertFalse(masked.contains("AIzaSy"))
    }

    @Test
    fun `mask handles blank key`() {
        assertEquals("", KeyProtector.mask(""))
        assertEquals("", KeyProtector.mask("   "))
    }

    @Test
    fun `mask handles very short key`() {
        assertEquals("••••", KeyProtector.mask("ab"))
    }

    // ─── Scrubbing from text ───

    @Test
    fun `scrub removes Gemini key from error message`() {
        val msg = "Error: invalid key AIzaSyA1234567890abcdefghijklmnopqrstu returned 403"
        val clean = KeyProtector.scrubFromText(msg)
        assertFalse(clean.contains("AIzaSyA1234"))
        assertTrue(clean.contains("403"))
    }

    @Test
    fun `scrub removes OpenAI key from log`() {
        val msg = "Using key sk-abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV"
        val clean = KeyProtector.scrubFromText(msg)
        assertFalse(clean.contains("sk-abcde"))
    }

    @Test
    fun `scrub preserves normal text`() {
        val msg = "User أحمد completed lesson 5 with 90% accuracy"
        val clean = KeyProtector.scrubFromText(msg)
        assertEquals(msg, clean)
    }

    // ─── Contains API key detection ───

    @Test
    fun `detects Gemini key in text`() {
        assertTrue(KeyProtector.containsApiKey("prefix AIzaSyA1234567890abcdefghijklmnopqrstu suffix"))
    }

    @Test
    fun `detects OpenAI key in text`() {
        assertTrue(KeyProtector.containsApiKey("key: sk-abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUV"))
    }

    @Test
    fun `no false positive on normal text`() {
        assertFalse(KeyProtector.containsApiKey("Hello world, how are you today?"))
        assertFalse(KeyProtector.containsApiKey("الدرس الخامس: الحيوانات"))
    }

    // ─── Provider detection ───

    @Test
    fun `detect Gemini provider`() {
        assertEquals("Gemini", KeyProtector.detectProvider("AIzaSySomething123"))
    }

    @Test
    fun `detect OpenAI provider`() {
        assertEquals("OpenAI", KeyProtector.detectProvider("sk-something"))
    }

    @Test
    fun `detect xAI provider`() {
        assertEquals("xAI", KeyProtector.detectProvider("xai-test"))
    }

    @Test
    fun `detect Anthropic provider`() {
        assertEquals("Anthropic", KeyProtector.detectProvider("sk-ant-test"))
    }

    @Test
    fun `detect empty key`() {
        assertEquals("بدون مفتاح", KeyProtector.detectProvider(""))
    }

    @Test
    fun `detect custom provider`() {
        assertEquals("مخصص", KeyProtector.detectProvider("my-custom-key"))
    }

    // ─── Gemini key validation ───

    @Test
    fun `valid Gemini key format`() {
        assertTrue(KeyProtector.isValidGeminiKey("AIzaSyA1234567890abcdefghijklmnopqrstu"))
    }

    @Test
    fun `invalid Gemini key - too short`() {
        assertFalse(KeyProtector.isValidGeminiKey("AIzaSyShort"))
    }

    @Test
    fun `invalid Gemini key - wrong prefix`() {
        assertFalse(KeyProtector.isValidGeminiKey("sk-1234567890abcdefghijklmnopqrstu"))
    }

    // ─── Strip keys for sharing ───

    @Test
    fun `stripKeys removes all raw keys`() {
        val state = com.zmastery.english.data.AppState(
            profile = com.zmastery.english.data.ProfileDto(
                geminiApiKey = "AIzaSyA1234567890abcdefghijklmnopqrstu",
                learnerName = "أحمد",
            ),
            apiKeys = listOf(
                com.zmastery.english.data.ApiKeyDto(
                    id = "k1", label = "Test", provider = "GEMINI",
                    maskedKey = "••••rstu", rawKey = "AIzaSyA1234567890abcdefghijklmnopqrstu",
                    active = true, baseUrl = "", status = "ok",
                ),
            ),
        )
        val stripped = KeyProtector.stripKeysForSharing(state)

        // Keys removed
        assertEquals("", stripped.profile.geminiApiKey)
        assertEquals("", stripped.apiKeys[0].rawKey)
        assertEquals("", stripped.apiKeys[0].maskedKey)

        // Other data preserved
        assertEquals("أحمد", stripped.profile.learnerName)
        assertEquals("Test", stripped.apiKeys[0].label)
        assertTrue(stripped.apiKeys[0].active)
    }

    // ─── Log sanitization ───

    @Test
    fun `sanitizeLog removes keys from complex message`() {
        val log = "POST /v1/models?key=AIzaSyA1234567890abcdefghijklmnopqrstu → 429 Rate limit"
        val clean = KeyProtector.sanitizeLog(log)
        assertFalse(clean.contains("AIzaSyA1234"))
        assertTrue(clean.contains("429"))
        assertTrue(clean.contains("Rate limit"))
    }
}
