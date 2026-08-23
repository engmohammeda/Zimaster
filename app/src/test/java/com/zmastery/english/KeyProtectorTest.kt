package com.zmastery.english

import com.zmastery.english.data.ApiKeyDto
import com.zmastery.english.data.AppState
import com.zmastery.english.data.KeyProtector
import com.zmastery.english.data.ProfileDto
import org.junit.Assert.*
import org.junit.Test

class KeyProtectorTest {

    // ─── Masking ───

    @Test
    fun `mask hides all but last 4 chars`() {
        val key = "AIzaSyA1234567890abcdefghijklmnop"
        val masked = KeyProtector.mask(key)
        assertTrue("Ends with last 4 chars", masked.endsWith("mnop"))
        assertTrue("Contains dots", masked.contains("•"))
        assertFalse("Does not contain original prefix", masked.contains("AIzaSy"))
    }

    @Test
    fun `mask empty string returns empty`() {
        assertEquals("", KeyProtector.mask(""))
    }

    @Test
    fun `mask short string returns dots`() {
        assertEquals("••••", KeyProtector.mask("abc"))
    }

    @Test
    fun `mask with custom visible chars`() {
        val masked = KeyProtector.mask("ABCDEFGHIJKLMNOP", 6)
        assertTrue(masked.endsWith("KLMNOP"))
    }

    // ─── Scrubbing ───

    @Test
    fun `scrub hides Gemini key in error message`() {
        val msg = "Failed with key AIzaSyA1234567890abcdefghijklmnopqrstu"
        val scrubbed = KeyProtector.scrubFromText(msg)
        assertFalse("Should not contain raw key", scrubbed.contains("AIzaSyA1234"))
        assertTrue("Should contain masked version", scrubbed.contains("•"))
    }

    @Test
    fun `scrub preserves non-key text`() {
        val msg = "Error in lesson 5: missing title field"
        val scrubbed = KeyProtector.scrubFromText(msg)
        assertEquals(msg, scrubbed)
    }

    // ─── Detection ───

    @Test
    fun `detect Gemini key`() {
        assertTrue(KeyProtector.containsApiKey("key=AIzaSyA1234567890abcdefghijklmnopqrstuv"))
    }

    @Test
    fun `detect no key in normal text`() {
        assertFalse(KeyProtector.containsApiKey("Hello world, this is a test"))
    }

    // ─── Key validation ───

    @Test
    fun `valid Gemini key`() {
        assertTrue(KeyProtector.isValidGeminiKey("AIzaSyA1234567890abcdefghijklmnopqrstuv"))
    }

    @Test
    fun `invalid Gemini key too short`() {
        assertFalse(KeyProtector.isValidGeminiKey("AIzaSyA123"))
    }

    @Test
    fun `invalid Gemini key wrong prefix`() {
        assertFalse(KeyProtector.isValidGeminiKey("sk-1234567890abcdefghijklmnopqrstu"))
    }

    // ─── Provider detection ───

    @Test
    fun `detect Gemini provider`() {
        assertEquals("Gemini", KeyProtector.detectProvider("AIzaSySomething"))
    }

    @Test
    fun `detect OpenAI provider`() {
        assertEquals("OpenAI", KeyProtector.detectProvider("sk-something"))
    }

    @Test
    fun `detect empty key`() {
        assertEquals("بدون مفتاح", KeyProtector.detectProvider(""))
    }

    @Test
    fun `detect custom provider`() {
        assertEquals("مخصص", KeyProtector.detectProvider("my-custom-key-123"))
    }

    // ─── Strip keys for sharing ───

    @Test
    fun `stripKeysForSharing removes all keys`() {
        val state = AppState(
            profile = ProfileDto(geminiApiKey = "AIzaSyA1234567890abcdefghijklmnopqrstu"),
            apiKeys = listOf(
                ApiKeyDto("k1", "My Key", "GEMINI", "••••", true, "AIzaSyA1234567890abcdefghijklmnopqrstu", "", "active")
            ),
        )
        val stripped = KeyProtector.stripKeysForSharing(state)
        assertEquals("", stripped.profile.geminiApiKey)
        assertEquals("", stripped.apiKeys[0].rawKey)
        assertEquals("", stripped.apiKeys[0].maskedKey)
    }

    @Test
    fun `stripKeysForSharing preserves non-key data`() {
        val state = AppState(
            profile = ProfileDto(
                geminiApiKey = "AIzaSyA1234567890abcdefghijklmnopqrstu",
                learnerName = "أحمد",
                streak = 10,
            ),
        )
        val stripped = KeyProtector.stripKeysForSharing(state)
        assertEquals("أحمد", stripped.profile.learnerName)
        assertEquals(10, stripped.profile.streak)
    }

    // ─── Log sanitization ───

    @Test
    fun `sanitizeLog removes keys`() {
        val log = "API call failed with key AIzaSyA1234567890abcdefghijklmnopqrstu: 403 Forbidden"
        val clean = KeyProtector.sanitizeLog(log)
        assertFalse(clean.contains("AIzaSyA1234"))
        assertTrue(clean.contains("403 Forbidden"))
    }
}
