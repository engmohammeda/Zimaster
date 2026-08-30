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
        /** سؤال السياق اليومي — يبني به الـAI سياق المتعلم تدريجياً (مسار الهدف). */
        val contextQuestionEn: String = "",
        val contextQuestionAr: String = "",
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
     * @param goalTitle مسار الهدف: إن لم يكن فارغاً تُنسج القصة حول الهدف لا حول الكلمات.
     * @param goalStage المرحلة المصغّرة الجارية داخل الهدف.
     * @param goalContext سطور السياق الشخصي التي جمعها الـAI من إجابات المتعلم.
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
        goalTitle: String = "",
        goalStage: String = "",
        goalContext: List<String> = emptyList(),
    ): Result = withContext(Dispatchers.IO) {
        val goalMode = goalTitle.isNotBlank()
        // في وضع الهدف لا تشترط كلمات: الموقف يكفي. في الوضع الأكاديمي القديم نعم.
        if (!goalMode && words.isEmpty()) return@withContext Result.Fatal("لا توجد كلمات لبناء القصة")
        if (apiKey.isBlank()) {
            return@withContext Result.Fatal("أضف مفتاح Gemini API من إعدادات الذكاء الاصطناعي")
        }
        if (!isOnline(ctx)) {
            return@withContext Result.Retryable("لا يوجد اتصال بالإنترنت — سيُعاد المحاولة تلقائياً")
        }

        // وضع الهدف: ≤3 كلمات مستحقة فقط كتوابل اختيارية.
        val seedWords = if (goalMode) words.take(3) else words
        val wordList = seedWords.joinToString(", ") { it.english }
        val examples = seedWords.filter { it.exampleEn.isNotBlank() }
            .joinToString("\n") { "- ${it.english}: ${it.exampleEn}" }

        val instruction = buildString {
            if (persona.isNotBlank()) appendLine("PERSONA: $persona")
            if (style.isNotBlank()) appendLine("STYLE: $style")
            appendLine()
            if (goalMode) {
                // القصة خادمة للهدف — برومبت الهدف يتقدم على برومبت الوكيل.
                appendLine(DailyStoryMaker.goalPrompt(goalTitle, goalStage, goalContext, seedWords.map { it.english }, level))
            } else {
                // The agent's own editable prompt drives the request.
                val filled = AiPrompts.fill(
                    basePrompt,
                    mapOf(
                        "WORDS" to wordList,
                        "LEVEL" to level,
                        "CONTEXT" to goalContext.takeLast(8).joinToString(" | "),
                    ),
                )
                appendLine(filled.ifBlank { DailyStoryMaker.aiPrompt(words, level) })
                appendLine()
                appendLine("TARGET WORDS (use every one, naturally): $wordList")
            }
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
            if (goalMode) {
                appendLine("  \"context_question_en\" : ONE small personal question to learn more about the learner's real life/skills")
                appendLine("  \"context_question_ar\" : its Arabic translation")
            }
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
                contextQuestionEn = o.optString("context_question_en").trim(),
                contextQuestionAr = o.optString("context_question_ar").trim(),
            )
        }.getOrNull()
    }

    // ---------------------------------------------------------------- QUIZ

    /**
     * اختبار إثبات المرحلة: ٣ أسئلة موقفية حول مرحلة الهدف الحالية وسياق
     * المتعلم. الاجتياز (≥٢/٣) هو وحده ما يقدّم المرحلة — قرار النقاش.
     */
    /** نتيجة اختبار إثبات المرحلة — منفصلة عن [Result] الخاص بالقصص. */
    sealed class QuizResult {
        data class Ok(val questions: List<com.zmastery.english.data.StageQuestion>) : QuizResult()
        data class Fail(val message: String, val retryable: Boolean = true) : QuizResult()
    }

    suspend fun generateStageQuiz(
        ctx: Context,
        goalTitle: String,
        stage: String,
        contextLines: List<String>,
        apiKey: String,
        modelId: String,
    ): QuizResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext QuizResult.Fail("أضف مفتاح Gemini API أولاً", retryable = false)
        if (!isOnline(ctx)) return@withContext QuizResult.Fail("لا يوجد اتصال بالإنترنت")

        val ctxBlock = contextLines.takeLast(8).joinToString("\n") { "- $it" }
        val instruction = buildString {
            appendLine("The learner pursues the goal \"$goalTitle\", current stage \"$stage\".")
            if (ctxBlock.isNotBlank()) appendLine("Known about the learner:\n$ctxBlock")
            appendLine()
            appendLine("Write 3 short SITUATIONAL multiple-choice questions (English, A2-B1) that prove")
            appendLine("the learner can ACT in this stage in real life (e.g. choose the best reply when")
            appendLine("a manager asks them to introduce themselves). Exactly 3 options each, one correct.")
            appendLine()
            appendLine("Return ONLY a JSON array of 3 objects with keys:")
            appendLine("  \"q\" : the question, \"options\" : [3 strings], \"answer\" : 0-based index of the correct option")
            appendLine("No markdown, no fences. Raw JSON only.")
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", instruction) }))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("responseMimeType", "application/json")
            })
        }.toString()

        val model = modelId.ifBlank { "gemini-2.5-flash" }
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) return@withContext QuizResult.Fail("فشل توليد الاختبار ($code) — أعد المحاولة")

            val text = extractText(resp) ?: return@withContext QuizResult.Fail("رد غير مكتمل")
            val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arrStart = cleaned.indexOf('[')
            val arrEnd = cleaned.lastIndexOf(']')
            if (arrStart < 0 || arrEnd <= arrStart) return@withContext QuizResult.Fail("تعذّر تحليل الاختبار")
            val arr = JSONArray(cleaned.substring(arrStart, arrEnd + 1))
            val quiz = mutableListOf<com.zmastery.english.data.StageQuestion>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val opts = o.optJSONArray("options") ?: continue
                val options = (0 until opts.length()).map { opts.optString(it) }.filter { it.isNotBlank() }
                if (options.size < 2) continue
                quiz += com.zmastery.english.data.StageQuestion(
                    questionEn = o.optString("q").trim(),
                    options = options,
                    correctIndex = o.optInt("answer", 0).coerceIn(0, options.size - 1),
                )
            }
            if (quiz.size < 3) return@withContext QuizResult.Fail("الاختبار ناقص (${quiz.size}/3) — أعد المحاولة")
            QuizResult.Ok(quiz)
        } catch (e: java.net.SocketTimeoutException) {
            QuizResult.Fail("انتهت مهلة الاتصال — أعد المحاولة")
        } catch (e: Exception) {
            QuizResult.Fail(e.message?.take(90) ?: "خطأ في الشبكة")
        }
    }
}
