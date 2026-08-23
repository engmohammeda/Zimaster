package com.zmastery.english.data

/**
 * حماية المفاتيح — أدوات لإخفاء مفاتيح API ومنع تسربها.
 *
 * المبادئ:
 *  1. لا تظهر المفاتيح كاملة في الواجهة (إظهار آخر 4 أحرف فقط)
 *  2. لا تظهر المفاتيح في السجلات (Logs) أبداً
 *  3. لا تُصدَّر المفاتيح في النسخ الاحتياطية المشتركة
 *  4. التشفير باستخدام Android Keystore (للتخزين المحلي)
 */
object KeyProtector {

    /**
     * إخفاء المفتاح للعرض في الواجهة.
     *
     * مثال: "AIzaSyA1234567890abcdef" → "••••••••••••cdef"
     *
     * @param key المفتاح الأصلي
     * @param visibleChars عدد الأحرف المرئية من النهاية (افتراضي: 4)
     */
    fun mask(key: String, visibleChars: Int = 4): String {
        if (key.isBlank()) return ""
        if (key.length <= visibleChars) return "••••"
        val hidden = "•".repeat(key.length - visibleChars)
        return hidden + key.takeLast(visibleChars)
    }

    /**
     * فحص نص بحثاً عن مفاتيح API معروفة وإخفائها.
     * يُستخدم لتنقية رسائل الأخطاء والسجلات.
     *
     * يبحث عن أنماط المفاتيح الشائعة:
     *  - Google/Gemini: AIzaSy... (39 حرف)
     *  - OpenAI: sk-... (48+ حرف)
     *  - عام: أي سلسلة طويلة من الأحرف الأبجدية الرقمية
     */
    fun scrubFromText(text: String): String {
        var result = text
        // Google/Gemini API keys: AIzaSy + 33 alphanumeric chars
        result = result.replace(Regex("AIzaSy[a-zA-Z0-9_-]{33}")) { match ->
            mask(match.value)
        }
        // OpenAI API keys: sk- + 48 alphanumeric chars
        result = result.replace(Regex("sk-[a-zA-Z0-9]{48,}")) { match ->
            mask(match.value)
        }
        // Generic long tokens (possible keys): 32+ alphanumeric chars
        result = result.replace(Regex("[a-zA-Z0-9]{32,}")) { match ->
            // Only mask if it looks like a key (mixed case + digits)
            val value = match.value
            if (value.any { it.isUpperCase() } && value.any { it.isDigit() } && value.length >= 32) {
                mask(value)
            } else {
                value
            }
        }
        return result
    }

    /**
     * تنظيف سجل من أي مفاتيح محتملة.
     *
     * @param logMessage رسالة السجل الأصلية
     * @return الرسالة بعد إخفاء المفاتيح
     */
    fun sanitizeLog(logMessage: String): String = scrubFromText(logMessage)

    /**
     * فحص ما إذا كان النص يحتوي على مفتاح API معروف.
     */
    fun containsApiKey(text: String): Boolean {
        return text.contains(Regex("AIzaSy[a-zA-Z0-9_-]{33}")) ||
            text.contains(Regex("sk-[a-zA-Z0-9]{48,}"))
    }

    /**
     * إزالة المفاتيح من نسخة احتياطية مُعدّة للمشاركة.
     *
     * عند تصدير نسخة للمشاركة (ليس للاستعادة الشخصية)، يجب إزالة
     * المفاتيح حتى لا يتسرب المفتاح مع الملف.
     *
     * @param state الحالة الأصلية
     * @return نسخة بدون مفاتيح
     */
    fun stripKeysForSharing(state: AppState): AppState {
        return state.copy(
            apiKeys = state.apiKeys.map { it.copy(rawKey = "", maskedKey = "") },
            profile = state.profile.copy(geminiApiKey = ""),
        )
    }

    /**
     * التحقق من صحة مفتاح Gemini (الشكل فقط، لا الاتصال).
     *
     * مفاتيح Gemini تبدأ بـ "AIzaSy" وطولها 39 حرف.
     */
    fun isValidGeminiKey(key: String): Boolean {
        return key.startsWith("AIzaSy") && key.length == 39 &&
            key.drop(6).all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    /**
     * تصنيف نوع المفتاح من باديته.
     */
    fun detectProvider(key: String): String = when {
        key.startsWith("AIzaSy") -> "Gemini"
        key.startsWith("sk-") -> "OpenAI"
        key.startsWith("xai-") -> "xAI"
        key.startsWith("anthropic") || key.startsWith("sk-ant-") -> "Anthropic"
        key.isBlank() -> "بدون مفتاح"
        else -> "مخصص"
    }
}
