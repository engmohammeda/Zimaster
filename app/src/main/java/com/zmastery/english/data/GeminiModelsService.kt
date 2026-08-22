package com.zmastery.english.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the COMPLETE list of models available to a given API key.
 *
 * Why this is more than a single HTTP call:
 *
 *  1. Google exposes different model sets on different API versions.
 *     `v1beta` carries the stable + most preview models, while `v1alpha`
 *     surfaces the bleeding-edge ones (live / native-audio / experimental TTS)
 *     that never appear on v1beta. We query BOTH and merge.
 *  2. `models.list` is paginated. With `pageSize=1000` one page is normally
 *     enough, but we still follow `nextPageToken` so nothing is ever truncated.
 *  3. Availability is per-key: a free key and a paid key legitimately see
 *     different lists. We never filter by an allow-list — whatever the key can
 *     see, the user gets. This is the whole point: no model is hidden.
 *
 * The result is classified into [ModelKind] purely from the model's declared
 * `supportedGenerationMethods` + id, so brand-new models the app has never
 * heard of are still categorised correctly.
 */
object GeminiModelsService {

    /** API versions probed, in priority order (earlier wins on duplicates). */
    private val VERSIONS = listOf("v1beta", "v1alpha")

    data class FetchResult(
        val success: Boolean,
        val models: List<AiModel>,
        val message: String,
        /** Per-version diagnostics, e.g. "v1beta: 68 · v1alpha: 12 جديد". */
        val detail: String = "",
    )

    /**
     * List every model the [apiKey] can access.
     *
     * @param includeAllVersions when false only `v1beta` is queried (faster).
     */
    suspend fun listAll(apiKey: String, includeAllVersions: Boolean = true): FetchResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext FetchResult(false, emptyList(), "أضف مفتاح Gemini API أولاً")
            }

            val merged = LinkedHashMap<String, AiModel>()
            val notes = mutableListOf<String>()
            var anyOk = false
            var lastError = ""

            val versions = if (includeAllVersions) VERSIONS else listOf("v1beta")
            for (version in versions) {
                val before = merged.size
                when (val r = fetchVersion(apiKey, version)) {
                    is VersionResult.Ok -> {
                        anyOk = true
                        r.models.forEach { m ->
                            val prev = merged[m.id]
                            if (prev == null) {
                                merged[m.id] = m
                            } else {
                                // Keep the richest record: prefer a real description
                                // and the union of supported methods.
                                merged[m.id] = prev.copy(
                                    description = prev.description.ifBlank { m.description },
                                    methods = (prev.methods + m.methods).distinct(),
                                    apiVersions = (prev.apiVersions + m.apiVersions).distinct(),
                                )
                            }
                        }
                        val added = merged.size - before
                        notes += "$version: ${r.models.size}" + if (added != r.models.size) " (+$added جديد)" else ""
                    }
                    is VersionResult.Err -> {
                        lastError = r.message
                        // v1alpha failing is normal on some keys — don't treat as fatal.
                        if (version == "v1beta") notes += "$version: فشل"
                    }
                }
            }

            if (!anyOk) {
                return@withContext FetchResult(false, emptyList(), lastError.ifBlank { "تعذّر الاتصال بجوجل" })
            }

            val all = merged.values.sortedWith(
                compareBy<AiModel> { it.kind.ordinal }
                    .thenByDescending { it.familyRank }
                    .thenBy { it.id }
            )
            FetchResult(
                success = true,
                models = all,
                message = "تم جلب ${all.size} نموذج متاح لمفتاحك",
                detail = notes.joinToString(" · "),
            )
        }

    private sealed class VersionResult {
        data class Ok(val models: List<AiModel>) : VersionResult()
        data class Err(val message: String) : VersionResult()
    }

    private fun fetchVersion(apiKey: String, version: String): VersionResult {
        val out = mutableListOf<AiModel>()
        var pageToken: String? = null
        var pages = 0
        try {
            do {
                val sb = StringBuilder("https://generativelanguage.googleapis.com/$version/models")
                sb.append("?key=").append(apiKey)
                sb.append("&pageSize=1000")
                if (!pageToken.isNullOrBlank()) sb.append("&pageToken=").append(pageToken)

                val conn = (URL(sb.toString()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 25000
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()

                if (code !in 200..299) {
                    return VersionResult.Err(errorMessage(code, body))
                }

                val root = JSONObject(body)
                val arr = root.optJSONArray("models") ?: break
                for (i in 0 until arr.length()) {
                    parseModel(arr.getJSONObject(i), version)?.let { out.add(it) }
                }
                pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                pages++
            } while (!pageToken.isNullOrBlank() && pages < 20)

            return VersionResult.Ok(out)
        } catch (e: Exception) {
            return VersionResult.Err(e.message ?: "خطأ في الشبكة")
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val apiMsg = runCatching {
            JSONObject(body).getJSONObject("error").optString("message")
        }.getOrNull().orEmpty()
        return when (code) {
            400 -> "طلب غير صالح — تحقّق من المفتاح"
            401, 403 -> "المفتاح غير مصرّح له (${apiMsg.take(80)})"
            429 -> "تجاوزت حد الطلبات — انتظر قليلاً"
            in 500..599 -> "خطأ من خوادم جوجل ($code)"
            else -> apiMsg.ifBlank { "فشل الجلب ($code)" }
        }
    }

    /** Convert one API model object into our [AiModel]. */
    private fun parseModel(o: JSONObject, version: String): AiModel? {
        val name = o.optString("name") // "models/gemini-2.5-flash"
        if (name.isBlank()) return null
        val id = name.removePrefix("models/").removePrefix("tunedModels/")
        if (id.isBlank()) return null

        val methods = mutableListOf<String>()
        o.optJSONArray("supportedGenerationMethods")?.let { arr ->
            for (i in 0 until arr.length()) methods.add(arr.optString(i))
        }
        val display = o.optString("displayName").ifBlank { prettify(id) }
        val desc = o.optString("description")

        return AiModel(
            id = id,
            displayName = display,
            kind = classify(id, methods),
            description = desc,
            methods = methods,
            inputTokenLimit = o.optInt("inputTokenLimit", 0),
            outputTokenLimit = o.optInt("outputTokenLimit", 0),
            version = o.optString("version"),
            apiVersions = listOf(version),
            fetched = true,
        )
    }

    /**
     * Classify a model from its declared methods first (authoritative), then
     * fall back to id heuristics so unknown/experimental models still land in
     * a sensible bucket instead of disappearing.
     */
    fun classify(id: String, methods: List<String>): ModelKind {
        val lid = id.lowercase()
        val m = methods.map { it.lowercase() }

        // --- method-driven (most reliable) ---
        if (m.any { it.contains("embedcontent") || it.contains("embedtext") }) return ModelKind.EMBEDDING
        if (m.any { it.contains("predictlongrunning") } && lid.contains("veo")) return ModelKind.VIDEO
        if (m.any { it.contains("bidigeneratecontent") || it.contains("bidistream") }) return ModelKind.LIVE

        // --- id-driven (covers preview models whose methods are generic) ---
        return when {
            lid.contains("tts") || lid.contains("text-to-speech") -> ModelKind.TTS
            lid.contains("native-audio") || lid.contains("audio-dialog") -> ModelKind.LIVE
            lid.contains("live") -> ModelKind.LIVE
            lid.contains("veo") || lid.contains("video") -> ModelKind.VIDEO
            lid.contains("imagen") || lid.contains("image-generation") -> ModelKind.IMAGE
            lid.contains("-image") || lid.endsWith("image") -> ModelKind.IMAGE
            lid.contains("embedding") || lid.contains("embed") -> ModelKind.EMBEDDING
            lid.contains("aqa") -> ModelKind.OTHER
            m.any { it.contains("generatecontent") } -> ModelKind.TEXT
            m.isEmpty() -> ModelKind.OTHER
            else -> ModelKind.TEXT
        }
    }

    /** "gemini-2.5-flash-preview-tts" → "Gemini 2.5 Flash Preview Tts". */
    private fun prettify(id: String): String =
        id.split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                if (part.firstOrNull()?.isDigit() == true) part
                else part.replaceFirstChar { c -> c.uppercaseChar() }
            }
}

/**
 * Known free-tier limits for the Gemini API, keyed by model-id prefix.
 *
 * Google publishes these per model family. They are surfaced in the UI so the
 * learner can pick a model that actually fits a free key instead of discovering
 * a 429 mid-lesson. Matching is longest-prefix-first, so a specific entry
 * always beats a general one.
 */
object GeminiQuotas {

    /**
     * @param rpm requests per minute
     * @param rpd requests per day (0 = not published / effectively unlimited)
     * @param tpm tokens per minute (0 = not published)
     * @param free whether the family has ANY free-tier allowance
     */
    data class Quota(
        val rpm: Int,
        val rpd: Int,
        val tpm: Int,
        val free: Boolean = true,
    ) {
        val rpmLabel: String get() = if (rpm > 0) "$rpm/دقيقة" else "—"
        val rpdLabel: String get() = if (rpd > 0) "$rpd/يوم" else "—"
        val tpmLabel: String get() = when {
            tpm >= 1_000_000 -> "${tpm / 1_000_000}M رمز/دقيقة"
            tpm >= 1000 -> "${tpm / 1000}K رمز/دقيقة"
            else -> "—"
        }
        val short: String get() = buildString {
            if (rpm > 0) append("$rpm ط/د")
            if (rpd > 0) { if (isNotEmpty()) append(" · "); append("$rpd ط/ي") }
            if (isEmpty()) append("غير منشور")
        }
    }

    // Longest prefix wins — order here is irrelevant, length decides.
    private val TABLE: Map<String, Quota> = mapOf(
        // ---- 2.5 family ----
        "gemini-2.5-pro" to Quota(5, 100, 250_000),
        "gemini-2.5-flash-lite" to Quota(15, 1000, 250_000),
        "gemini-2.5-flash-preview-tts" to Quota(3, 15, 10_000),
        "gemini-2.5-pro-preview-tts" to Quota(0, 0, 0, free = false),
        "gemini-2.5-flash-image" to Quota(10, 100, 200_000),
        "gemini-2.5-flash-native-audio" to Quota(3, 25, 25_000),
        "gemini-2.5-flash" to Quota(10, 250, 250_000),
        // ---- 2.0 family ----
        "gemini-2.0-flash-lite" to Quota(30, 200, 1_000_000),
        "gemini-2.0-flash-preview-image" to Quota(10, 100, 200_000),
        "gemini-2.0-flash-exp" to Quota(10, 250, 1_000_000),
        "gemini-2.0-flash" to Quota(15, 200, 1_000_000),
        // ---- 1.5 legacy ----
        "gemini-1.5-flash-8b" to Quota(15, 50, 250_000),
        "gemini-1.5-flash" to Quota(15, 50, 250_000),
        "gemini-1.5-pro" to Quota(0, 0, 0, free = false),
        // ---- embeddings ----
        "gemini-embedding" to Quota(100, 1000, 30_000),
        "text-embedding" to Quota(100, 1000, 30_000),
        "embedding" to Quota(100, 1000, 30_000),
        // ---- media ----
        "imagen" to Quota(0, 0, 0, free = false),
        "veo" to Quota(0, 0, 0, free = false),
        // ---- generic newer families (3.x etc.) ----
        "gemini-3-pro" to Quota(2, 50, 200_000),
        "gemini-3-flash" to Quota(10, 250, 250_000),
        "gemini-3" to Quota(5, 100, 200_000),
        "gemma" to Quota(30, 14_400, 15_000),
    )

    /**
     * Best-matching published free-tier quota for [modelId], or null when we
     * have no reliable figure.
     *
     * Matching is longest-prefix, but capability-aware: a TTS / image / video
     * variant must match a prefix of the SAME capability. Otherwise a model
     * like `gemini-3.1-flash-tts-preview` would silently inherit the (much
     * larger) plain-text `gemini-3` quota and mislead the user. When the
     * capability disagrees we report "unknown" instead of a wrong number.
     */
    fun forModel(modelId: String): Quota? {
        val lid = modelId.lowercase()
        val match = TABLE.entries
            .filter { lid.startsWith(it.key) }
            .maxByOrNull { it.key.length }
            ?: return null

        fun capOf(s: String): String = when {
            s.contains("tts") -> "tts"
            s.contains("native-audio") || s.contains("live") -> "live"
            s.contains("image") || s.contains("imagen") -> "image"
            s.contains("veo") || s.contains("video") -> "video"
            s.contains("embed") -> "embed"
            else -> "text"
        }
        return if (capOf(lid) == capOf(match.key)) match.value else null
    }

    /** True when the model has a documented free allowance. */
    fun isFree(modelId: String): Boolean = forModel(modelId)?.free ?: false

    /** True when we have no published data (common for brand-new previews). */
    fun isUnknown(modelId: String): Boolean = forModel(modelId) == null
}
