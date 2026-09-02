package com.zmastery.english.data

import kotlinx.serialization.Serializable

/** Severity of an in-app diagnostic alert. */
enum class AlertKind { ERROR, WARNING, INFO, SUCCESS, QUOTA }

/**
 * One row in the in-app inbox. Survives restarts so the learner can open
 * الإشعارات later and see *why* a voice, model, quota or key failed.
 */
@Serializable
data class AppAlert(
    val id: String,
    val kind: String = AlertKind.INFO.name,
    val source: String,
    val title: String,
    val detail: String,
    val atMillis: Long,
    val read: Boolean = false,
    val route: String? = null,
) {
    val kindEnum: AlertKind
        get() = runCatching { AlertKind.valueOf(kind) }.getOrDefault(AlertKind.INFO)
}

/**
 * Pure inbox helpers — de-dupe, cap, voice-id → Gemini prebuilt name.
 * Kept off Android so unit tests can lock the behaviour.
 */
object AlertInbox {

    const val CAP = 80
    const val DEDUPE_MS = 25_000L

    fun push(existing: List<AppAlert>, incoming: AppAlert): List<AppAlert> {
        val last = existing.firstOrNull()
        if (last != null &&
            last.source == incoming.source &&
            last.title == incoming.title &&
            incoming.atMillis - last.atMillis < DEDUPE_MS
        ) {
            return listOf(incoming.copy(id = last.id, read = false)) + existing.drop(1)
        }
        return (listOf(incoming) + existing).take(CAP)
    }

    fun markRead(existing: List<AppAlert>, id: String): List<AppAlert> =
        existing.map { if (it.id == id) it.copy(read = true) else it }

    fun markAllRead(existing: List<AppAlert>): List<AppAlert> =
        existing.map { it.copy(read = true) }

    fun unreadCount(existing: List<AppAlert>): Int = existing.count { !it.read }

    /**
     * Gemini prebuilt voices are title-case (`Kore`, `Puck`). Agent ids are
     * stored lowercase (`kore`). Feeding the lowercase id makes TTS ignore
     * the pick and fall back to the default — every voice then sounds the same.
     */
    fun geminiVoiceName(id: String, voices: List<AiVoice> = AiDefaults.builtinVoices): String {
        val clean = id.trim()
        if (clean.isBlank()) return "Kore"
        voices.firstOrNull { it.id.equals(clean, true) }?.displayName?.let { return it }
        return clean.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
    }

    fun previewSample(voiceName: String): String =
        "Hello, I am $voiceName. This is how I sound when we practice English together."

    fun ttsFailureTitle(quota: Boolean, hasKey: Boolean, online: Boolean): String = when {
        !hasKey -> "لا يوجد مفتاح API"
        !online -> "لا يوجد اتصال بالإنترنت"
        quota -> "نفدت حصة الصوت"
        else -> "تعذّر تشغيل الصوت الطبيعي"
    }

    fun ttsFailureDetail(
        quota: Boolean,
        hasKey: Boolean,
        online: Boolean,
        engineError: String?,
        voiceName: String,
    ): String = when {
        !hasKey ->
            "أضف مفتاح Gemini من إعدادات الذكاء الاصطناعي لمعاينة الأصوات وتشغيل صوت المحادثة الطبيعي."
        !online ->
            "الصوت الطبيعي يحتاج إنترنت. وصّل الجهاز ثم أعد المحاولة."
        quota ->
            "حصة نماذج TTS على هذا المفتاح استُنفدت (صوت $voiceName). انتظر إعادة التعيين، أو أضف مفتاحاً آخر، أو أوقف فلتر المجانية."
        else ->
            (engineError?.takeIf { it.isNotBlank() } ?: "النموذج الصوتي لم يُرجع مقطعاً.") +
                "\nالصوت المطلوب: $voiceName"
    }
}
