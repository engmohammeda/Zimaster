package com.zmastery.english.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Generates the DAILY story with a real LLM — no offline template fallback.
 *
 * Product decision (explicit user requirement): the daily story must ALWAYS be
 * AI-written. If there is no internet or the model is unavailable, we do not
 * fabricate a story from templates; we report a retryable state and the caller
 * waits, then tries again automatically once connectivity returns.
 */
object GeminiStoryService {

    data class Story(
        val title: String,
        val en: String,
        val ar: String,
        val modelId: String,
    )

    sealed class Result {
        data class Success(val story: Story) : Result()
        /** Transient — worth retrying automatically (offline, 429, 5xx, timeout). */
        data class Retryable(val message: String) : Result()
        /** Permanent for this attempt — bad key, bad request, empty input. */
        data class Fatal(val message: String) : Result()
    }

    /** True when the device currently has validated internet access. */
    fun isOnline(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        false
    }

    /**
     * Ask the model for today's story.
     *
     * @param words     the learner's own due words to weave in
     * @param apiKey    Gemini API key
     * @param modelId   model chosen for the "story_writer" agent
     * @param persona   agent character (steers voice/tone)
     * @param style     agent style
     * @param basePrompt the agent's editable prompt (supports {WORDS} / {LEVEL})
     * @param level     CEFR-ish target level
     */
    suspend fun generate(
        ctx: Context,
        words: List<VocabWord>,
        apiKey: String,
        modelId: String,
        persona: String,
        style: String,
        basePrompt: String,
        level: String = "A2-B1",
    ): Result = withContext(Dispatchers.IO) {
        if (words.isEmpty()) return@withContext Result.Fatal("لا توجد كلمات لبناء القصة")
        if (apiKey.isBlank()) {
            return@withContext Result.Fatal("أضف مفتاح Gemini API من إعدادات الذكاء الاصطناعي")
        }
        if (!isOnline(ctx)) {
            return@withContext Result.Retryable("لا يوجد اتصال بالإنترنت — سيُعاد المحاولة تلقائياً")
        }

        val wordList = words.joinToString(", ") { it.english }
        val examples = words.filter { it.exampleEn.isNotBlank() }
            .joinToString("\n") { "- ${it.english}: ${it.exampleEn}" }

        val instruction = buildString {
            if (persona.isNotBlank()) appendLine("PERSONA: $persona")
            if (style.isNotBlank()) appendLine("STYLE: $style")
            appendLine()
            // The agent's own editable prompt drives the request.
            val filled = basePrompt
                .replace("{WORDS}", wordList)
                .replace("{LEVEL}", level)
            appendLine(filled.ifBlank { DailyStoryMaker.aiPrompt(words, level) })
            appendLine()
            appendLine("TARGET WORDS (use every one, naturally): $wordList")
            if (examples.isNotBlank()) {
                appendLine()
                appendLine("The learner already knows these words in these contexts — stay consistent with them:")
                appendLine(examples)
            }
            appendLine()
            appendLine("Return ONLY a JSON object with exactly these keys:")
            appendLine("  \"title\"   : a short catchy English title (max 6 words)")
            appendLine("  \"english\" : the story, 6-9 short sentences, one small narrative arc")
            appendLine("  \"arabic\"  : a fluent Modern Standard Arabic translation of the whole story")
            appendLine("No markdown, no code fences, no commentary. Raw JSON only.")
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", instruction) }))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.9)
                put("topP", 0.95)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val model = modelId.ifBlank { "gemini-2.5-flash" }
        try {
            val url = URL(
                "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "$model:generateContent?key=$apiKey"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()

            if (code !in 200..299) {
                val apiMsg = runCatching {
                    JSONObject(resp).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty()
                return@withContext when (code) {
                    429 -> Result.Retryable("تجاوزت حصة النموذج — سيُعاد المحاولة تلقائياً")
                    in 500..599 -> Result.Retryable("خوادم جوجل مشغولة — سيُعاد المحاولة تلقائياً")
                    503 -> Result.Retryable("النموذج غير متاح مؤقتاً — سيُعاد المحاولة")
                    401, 403 -> Result.Fatal("المفتاح غير مصرّح له: ${apiMsg.take(90)}")
                    404 -> Result.Fatal("النموذج «$model» غير متاح لمفتاحك — اختر نموذجاً آخر")
                    else -> Result.Fatal(apiMsg.ifBlank { "فشل التوليد ($code)" })
                }
            }

            val text = extractText(resp)
                ?: return@withContext Result.Retryable("رد غير مكتمل من النموذج — سيُعاد المحاولة")

            val story = parseStory(text, model)
                ?: return@withContext Result.Retryable("تعذّر تحليل رد النموذج — سيُعاد المحاولة")

            if (story.en.isBlank()) {
                return@withContext Result.Retryable("النموذج أعاد قصة فارغة — سيُعاد المحاولة")
            }
            Result.Success(story)
        } catch (e: java.net.UnknownHostException) {
            Result.Retryable("لا يوجد اتصال بالإنترنت — سيُعاد المحاولة تلقائياً")
        } catch (e: java.net.SocketTimeoutException) {
            Result.Retryable("انتهت مهلة الاتصال — سيُعاد المحاولة تلقائياً")
        } catch (e: Exception) {
            Result.Retryable(e.message?.take(90) ?: "خطأ في الشبكة — سيُعاد المحاولة")
        }
    }

    /** Pull the model's text out of the candidates envelope. */
    private fun extractText(resp: String): String? = runCatching {
        val root = JSONObject(resp)
        val cands = root.optJSONArray("candidates") ?: return@runCatching null
        if (cands.length() == 0) return@runCatching null
        val parts = cands.getJSONObject(0)
            .optJSONObject("content")?.optJSONArray("parts") ?: return@runCatching null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        sb.toString().takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Parse the model's JSON payload, tolerating stray code fences. */
    private fun parseStory(raw: String, modelId: String): Story? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val o = JSONObject(cleaned.substring(start, end + 1))
            Story(
                title = o.optString("title").trim(),
                en = o.optString("english").ifBlank { o.optString("en") }.trim(),
                ar = o.optString("arabic").ifBlank { o.optString("ar") }.trim(),
                modelId = modelId,
            )
        }.getOrNull()
    }
}
