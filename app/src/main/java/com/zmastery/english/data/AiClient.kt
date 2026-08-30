package com.zmastery.english.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * One client for every provider.
 *
 * All AI text features call [complete]. It dispatches to the right wire format
 * based on the credential's protocol, so features never care who the provider
 * is. Adding a provider = adding an enum entry.
 */
object AiClient {

    data class Reply(val ok: Boolean, val text: String, val error: String = "")

    private const val CONNECT_MS = 20_000
    private const val READ_MS = 60_000

    /** generateContent-capable default when a LIVE/TTS/image id is selected. */
    const val DEFAULT_TEXT_MODEL = "gemini-2.5-flash"

    /**
     * Run a chat completion.
     *
     * @param key      credential to authenticate with
     * @param model    model id ("" → provider default)
     * @param system   optional system prompt
     * @param user     the user message
     * @param json     request a raw-JSON response
     */
    suspend fun complete(
        key: ApiKeyEntry,
        model: String,
        system: String,
        user: String,
        json: Boolean = false,
        temperature: Double = 0.7,
    ): Reply = withContext(Dispatchers.IO) {
        if (key.rawKey.isBlank()) return@withContext Reply(false, "", "المفتاح فارغ")
        val requested = model.ifBlank { key.providerEnum.defaultModel }
        // Live / TTS / image ids cannot call generateContent. Never send them.
        val m = if (canGenerateText(requested)) requested else DEFAULT_TEXT_MODEL
        if (m.isBlank()) return@withContext Reply(false, "", "لم يُحدَّد نموذج")
        try {
            when (key.protocol) {
                AiProtocol.OPENAI -> openAiChat(key, m, system, user, json, temperature)
                AiProtocol.GEMINI -> geminiGenerate(key, m, system, user, json, temperature)
            }
        } catch (e: Exception) {
            Reply(false, "", friendly(e))
        }
    }

    /** Lightweight credential check — cheap call, clear verdict. */
    suspend fun verify(key: ApiKeyEntry): Reply = withContext(Dispatchers.IO) {
        if (key.rawKey.isBlank()) return@withContext Reply(false, "", "المفتاح فارغ")
        try {
            when (key.protocol) {
                AiProtocol.OPENAI -> {
                    val (code, body) = request(
                        url = "${key.effectiveBase}/models",
                        method = "GET",
                        headers = authHeaders(key),
                        payload = null,
                    )
                    if (code in 200..299) {
                        val n = runCatching {
                            JSONObject(body).optJSONArray("data")?.length() ?: 0
                        }.getOrDefault(0)
                        Reply(true, if (n > 0) "المفتاح يعمل · $n نموذج متاح" else "المفتاح يعمل")
                    } else {
                        Reply(false, "", httpError(code, body))
                    }
                }
                AiProtocol.GEMINI -> {
                    val (code, body) = request(
                        url = "${key.effectiveBase}/v1beta/models?key=${key.rawKey}&pageSize=1",
                        method = "GET",
                        headers = emptyMap(),
                        payload = null,
                    )
                    if (code in 200..299) Reply(true, "المفتاح يعمل")
                    else Reply(false, "", httpError(code, body))
                }
            }
        } catch (e: Exception) {
            Reply(false, "", friendly(e))
        }
    }

    /** List model ids the credential can reach (OpenAI protocol). */
    suspend fun listOpenAiModels(key: ApiKeyEntry): List<AiModel> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, body) = request(
                url = "${key.effectiveBase}/models",
                method = "GET",
                headers = authHeaders(key),
                payload = null,
            )
            if (code !in 200..299) return@withContext emptyList()
            val arr = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                AiModel(
                    id = id,
                    displayName = id,
                    kind = classify(id),
                    description = o.optString("owned_by"),
                    fetched = true,
                )
            }
        }.getOrDefault(emptyList())
    }

    /* ─────────────────────────── OpenAI protocol ─────────────────────────── */

    private fun openAiChat(
        key: ApiKeyEntry, model: String, system: String, user: String,
        json: Boolean, temperature: Double,
    ): Reply {
        val messages = JSONArray()
        if (system.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", system))
        }
        messages.put(JSONObject().put("role", "user").put("content", user))

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", temperature)
            if (json) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }.toString()

        val (code, body) = request(
            url = "${key.effectiveBase}/chat/completions",
            method = "POST",
            headers = authHeaders(key),
            payload = payload,
        )
        if (code !in 200..299) return Reply(false, "", httpError(code, body))

        val text = runCatching {
            JSONObject(body).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }.getOrNull()
        return if (text.isNullOrBlank()) Reply(false, "", "رد فارغ من المزوّد")
        else Reply(true, stripFences(text))
    }

    private fun authHeaders(key: ApiKeyEntry): Map<String, String> {
        val h = mutableMapOf("Authorization" to "Bearer ${key.rawKey}")
        if (key.providerEnum == AiProvider.OPENROUTER) {
            // OpenRouter asks for attribution headers; harmless elsewhere.
            h["HTTP-Referer"] = "https://zmastery.app"
            h["X-Title"] = "Z-Mastery"
        }
        return h
    }

    /* ─────────────────────────── Gemini protocol ─────────────────────────── */

    private fun geminiGenerate(
        key: ApiKeyEntry, model: String, system: String, user: String,
        json: Boolean, temperature: Double,
    ): Reply {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", user)))
            }))
            if (system.isNotBlank()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", system)))
                })
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                if (json) put("responseMimeType", "application/json")
            })
        }.toString()

        val id = model.removePrefix("models/")
        val (code, body) = request(
            url = "${key.effectiveBase}/v1beta/models/$id:generateContent?key=${key.rawKey}",
            method = "POST",
            headers = emptyMap(),
            payload = payload,
        )
        if (code !in 200..299) return Reply(false, "", httpError(code, body))

        val text = runCatching {
            val parts = JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            buildString {
                for (i in 0 until parts.length()) {
                    append(parts.getJSONObject(i).optString("text"))
                }
            }
        }.getOrNull()
        return if (text.isNullOrBlank()) Reply(false, "", "رد فارغ من المزوّد")
        else Reply(true, stripFences(text))
    }

    /* ─────────────────────────── plumbing ─────────────────────────── */

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        payload: String?,
    ): Pair<Int, String> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_MS
            readTimeout = READ_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (payload != null) doOutput = true
        }
        payload?.let { p -> conn.outputStream.use { it.write(p.toByteArray(Charsets.UTF_8)) } }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        runCatching { conn.disconnect() }
        return code to body
    }

    /** Turn any provider's error envelope into one readable Arabic line. */
    private fun httpError(code: Int, body: String): String {
        val detail = runCatching {
            val o = JSONObject(body)
            when {
                o.has("error") && o.opt("error") is JSONObject ->
                    o.getJSONObject("error").optString("message")
                o.has("error") -> o.optString("error")
                o.has("message") -> o.optString("message")
                else -> ""
            }
        }.getOrDefault("").take(140)

        val ldetail = detail.lowercase()
        if (ldetail.contains("not supported for generatecontent") ||
            ldetail.contains("native-audio") ||
            ldetail.contains("bidigeneratecontent") ||
            (ldetail.contains("generatecontent") && ldetail.contains("call mode"))
        ) {
            return "هذا نموذج حيّ/صوتي ولا يرد بالنص المكتوب"
        }

        val reason = when (code) {
            400 -> "طلب غير صالح — تحقّق من اسم النموذج"
            401 -> "المفتاح غير صالح أو منتهي"
            403 -> "المفتاح لا يملك صلاحية لهذا النموذج"
            404 -> "النموذج غير موجود لدى هذا المزوّد"
            429 -> "تجاوزت الحصة — انتظر قليلاً أو بدّل المفتاح"
            in 500..599 -> "خطأ في خادم المزوّد — أعد المحاولة"
            else -> "خطأ $code"
        }
        return if (detail.isBlank()) reason else "$reason · $detail"
    }

    private fun friendly(e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            m.contains("timeout", true) || m.contains("timed out", true) -> "انتهت مهلة الاتصال"
            m.contains("Unable to resolve host", true) || m.contains("UnknownHost", true) ->
                "لا يوجد اتصال بالإنترنت"
            m.contains("CertPath", true) || m.contains("SSL", true) -> "فشل التحقق من الشهادة"
            else -> "تعذّر الاتصال: ${m.take(80).ifBlank { "خطأ غير معروف" }}"
        }
    }

    /** Strip ```json fences some models still emit. */
    fun stripFences(s: String): String = s.trim()
        .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
        .removeSuffix("```").trim()

    /** Classify a model id into a [ModelKind]. Live/native-audio before TTS. */
    fun classify(id: String): ModelKind {
        val l = id.lowercase()
        return when {
            l.contains("native-audio") || l.contains("audio-dialog") ||
                l.contains("live") || l.contains("realtime") || l.contains("bidi") -> ModelKind.LIVE
            l.contains("tts") || l.contains("text-to-speech") ||
                l.contains("whisper") || l.contains("speech") || l.contains("audio") -> ModelKind.TTS
            l.contains("imagen") || l.contains("image") || l.contains("dall") || l.contains("flux") -> ModelKind.IMAGE
            l.contains("veo") || l.contains("video") || l.contains("sora") -> ModelKind.VIDEO
            l.contains("embed") -> ModelKind.EMBEDDING
            else -> ModelKind.TEXT
        }
    }

    /**
     * True when [modelId] can be sent to `generateContent` / chat completions.
     * Live / native-audio / TTS / image / video / embedding ids cannot.
     */
    fun canGenerateText(modelId: String): Boolean {
        val lid = modelId.removePrefix("models/").lowercase().trim()
        if (lid.isBlank()) return false
        return when (classify(lid)) {
            ModelKind.TEXT, ModelKind.OTHER -> true
            else -> false
        }
    }

    /**
     * Pick a model that actually answers a text completion.
     *
     * Conversation personas are LIVE (picker shows native-audio only) but the
     * current turn-based chat still uses generateContent. Never send a Live
     * id down that path — swap in a TEXT sibling from the catalogue.
     */
    fun textFallbackId(requested: String, catalogue: List<AiModel>): String {
        val id = requested.removePrefix("models/").trim()
        if (canGenerateText(id)) return id
        val text = catalogue.filter { canGenerateText(it.id) }
        val preferred = listOf(
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash-lite",
            "gemini-2.5-pro",
            "gemini-2.0-flash-exp",
        )
        return preferred.firstOrNull { p -> text.any { it.id.equals(p, true) } }
            ?: text.maxByOrNull { it.familyRank }?.id
            ?: DEFAULT_TEXT_MODEL
    }
}
