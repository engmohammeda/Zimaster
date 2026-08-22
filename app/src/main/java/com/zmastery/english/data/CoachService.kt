package com.zmastery.english.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ==========================================================================
//  AI Coach — analyses the learner's REAL telemetry and returns a structured,
//  Arabic, actionable report.
//
//  Runs ON DEMAND only (a button), never on a timer, to conserve API quota.
//  When no API key is configured it falls back to a fully local rule-based
//  analysis so the feature always produces something useful.
// ==========================================================================

/** Analysis window. */
enum class CoachScope(val label: String, val days: Int) {
    DAILY("يومي", 1),
    WEEKLY("أسبوعي", 7),
    MONTHLY("شهري", 30);

    companion object {
        fun from(name: String) = runCatching { valueOf(name) }.getOrDefault(WEEKLY)
    }
}

/** The coach's structured verdict. */
@Serializable
data class CoachReport(
    val scope: String = "WEEKLY",
    val good: List<String> = emptyList(),
    val weak: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val motivation: String = "",
    val focusNextWeek: String = "",
    val stamp: String = "",
    val epochDay: Long = 0L,
    /** True when produced locally (no API key / offline). */
    val local: Boolean = false,
) {
    val isEmpty: Boolean get() = good.isEmpty() && weak.isEmpty() && suggestions.isEmpty()
}

/** Everything the coach is told about the learner. */
data class CoachFacts(
    val scope: CoachScope,
    val levelName: String,
    val span: StatSpan,
    val previous: StatSpan,
    val totalWords: Int,
    val masteredWords: Int,
    val dueNow: Int,
    val predictedRetention: Float,
    val trueRecallRate: Float,
    val avgStability: Float,
    val leeches: List<VocabWord>,
    val hardWords: List<VocabWord>,
    val examAvg: Int,
    val lastExams: List<ExamRecord>,
    val skills: List<SkillScore>,
    val streak: Int,
    val planLabel: String,
    val planOnTrack: Boolean,
    val planDrift: Int,
    /** Coverage of the learner's OWN imported curriculum. */
    val curriculum: CurriculumReport,
) {
    /** Compact English brief — the model reasons better on structured facts. */
    fun brief(): String = buildString {
        appendLine("LEARNER PROFILE")
        appendLine("- Curriculum level: $levelName")
        appendLine("- Dictionary: $totalWords words, $masteredWords mastered, $dueNow due now")
        appendLine("- Streak: $streak days")
        appendLine("- Study plan: $planLabel (${if (planOnTrack) "ON TRACK" else "BEHIND"}, drift $planDrift lessons)")
        appendLine()
        appendLine("PERIOD: ${scope.label} (last ${scope.days} day(s))")
        appendLine("- Study time: ${span.studyMinutes} min total, avg ${span.avgMinutesPerActiveDay} min/active day")
        appendLine("- Active days: ${span.activeDays} of ${scope.days}")
        appendLine("- Reviews: ${span.reviews} (${(span.recallRate * 100).toInt()}% recalled correctly)")
        appendLine("- Lessons completed: ${span.lessons}")
        appendLine("- Words added: ${span.wordsAdded}, newly mastered: ${span.wordsMastered}")
        appendLine("- Exams: ${span.exams}, average score ${span.examAvg}%")
        appendLine("- Mistakes logged: ${span.mistakes}")
        appendLine("- Listening: ${span.listenMinutes} min · Stories read: ${span.stories}")
        appendLine("- Conversation turns: ${span.conversationTurns} · Phonetics drills: ${span.phonetics}")
        appendLine()
        appendLine("TREND vs previous ${scope.days} day(s)")
        appendLine("- Study minutes: ${span.deltaPct(previous) { it.studyMinutes }}%")
        appendLine("- Reviews: ${span.deltaPct(previous) { it.reviews }}%")
        appendLine("- Lessons: ${span.deltaPct(previous) { it.lessons }}%")
        appendLine()
        appendLine("MEMORY MODEL (FSRS)")
        appendLine("- Predicted retention: ${(predictedRetention * 100).toInt()}%")
        appendLine("- True recall rate: ${(trueRecallRate * 100).toInt()}%")
        appendLine("- Average stability: ${avgStability.toInt()} days")
        if (leeches.isNotEmpty()) {
            appendLine("- Leech words (repeatedly forgotten):")
            leeches.take(6).forEach {
                appendLine("    \"${it.english}\" (${it.arabic}) lapses=${it.lapses} stability=${"%.1f".format(it.stability)} difficulty=${"%.1f".format(it.difficulty)}")
            }
        }
        if (hardWords.isNotEmpty()) {
            appendLine("- Hard words: " + hardWords.take(8).joinToString(", ") { it.english })
        }
        if (lastExams.isNotEmpty()) {
            appendLine()
            appendLine("RECENT EXAMS")
            lastExams.take(5).forEach { e ->
                appendLine("- ${e.title}: ${e.pct}% (${e.correct}/${e.total})")
                e.skillTotal.forEach { (skill, tot) ->
                    val ok = e.skillCorrect[skill] ?: 0
                    if (tot > 0) appendLine("    ${skill.name}: $ok/$tot")
                }
            }
        }
        appendLine()
        appendLine("SKILL RADAR")
        skills.forEach { appendLine("- ${it.label}: ${(it.value * 100).toInt()}% (${it.detail})") }

        // The learner's OWN curriculum — advice must reference THIS content,
        // never generic English material.
        appendLine()
        appendLine("MY CURRICULUM COVERAGE (imported courses only)")
        appendLine("- Overall: ${curriculum.lessonsDone}/${curriculum.lessonsTotal} lessons done (${curriculum.overallPct}%)")
        appendLine("- Courses completed: ${curriculum.coursesCompleted} of ${curriculum.coursesWithContent.size}")
        appendLine(
            "- Studied content so far: ${curriculum.grammarPointsStudied} grammar points, " +
                "${curriculum.dialogueLinesStudied} dialogue lines, " +
                "${curriculum.readingSegmentsStudied} reading segments, " +
                "${curriculum.expressionsStudied} key expressions"
        )
        curriculum.coursesWithContent.forEach { c ->
            appendLine(
                "- [${c.type.name}] \"${c.name}\": ${c.lessonsDone}/${c.lessonsTotal} lessons " +
                    "(${c.pct}%), ${c.wordsMastered}/${c.wordsTotal} words mastered"
            )
        }
        curriculum.nextCourse?.let {
            appendLine("- NEXT UP: \"${it.name}\" (${it.remaining} lesson(s) remaining)")
        }
        curriculum.weakestSkill?.let {
            appendLine("- WEAKEST AREA IN MY COURSE: ${it.type.name} at ${it.pct}%")
        }
    }
}

object CoachService {

    /**
     * Ask Gemini for a structured report. Falls back to [localReport] when
     * there is no key or the call fails, so the UI is never left empty.
     */
    suspend fun analyze(facts: CoachFacts, apiKey: String, stamp: String): CoachReport =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext localReport(facts, stamp)
            try {
                val prompt = buildPrompt(facts)
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
                    }))
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.8)
                        put("responseMimeType", "application/json")
                    })
                }.toString()

                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                        "gemini-2.0-flash:generateContent?key=$apiKey"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 35000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader()?.use { it.readText() }
                    ?: return@withContext localReport(facts, stamp)
                if (code !in 200..299) return@withContext localReport(facts, stamp)

                val text = JSONObject(resp)
                    .getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
                    .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

                val j = JSONObject(text)
                fun arr(key: String): List<String> {
                    val a = j.optJSONArray(key) ?: return emptyList()
                    return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
                }
                val report = CoachReport(
                    scope = facts.scope.name,
                    good = arr("good"),
                    weak = arr("weak"),
                    suggestions = arr("suggestions"),
                    motivation = j.optString("motivation"),
                    focusNextWeek = j.optString("focus_next_week"),
                    stamp = stamp,
                    epochDay = Telemetry.today(),
                    local = false,
                )
                if (report.isEmpty) localReport(facts, stamp) else report
            } catch (e: Exception) {
                localReport(facts, stamp)
            }
        }

    private fun buildPrompt(facts: CoachFacts): String = buildString {
        appendLine("You are an elite, warm and honest personal English coach for an ARABIC-speaking learner.")
        appendLine("You are given real telemetry from their study app. Analyse it like a professional trainer:")
        appendLine("be specific, quote their real numbers and real word examples, and never give generic advice.")
        appendLine()
        append(facts.brief())
        appendLine()
        appendLine("TASK")
        appendLine("Return ONLY a raw JSON object (no markdown, no code fences) with exactly these keys:")
        appendLine("  \"good\"            : array of 2-3 short Arabic strings — what genuinely went well, each citing a real number.")
        appendLine("  \"weak\"            : array of 2-3 short Arabic strings — the real weak points, each citing evidence.")
        appendLine("  \"suggestions\"     : array of 3 Arabic strings — concrete, doable actions for the coming period.")
        appendLine("                      Each must be measurable (e.g. \"راجع 25 كلمة يومياً قبل النوم\").")
        appendLine("  \"motivation\"      : one warm Arabic sentence, personal and encouraging, not clichéd.")
        appendLine("  \"focus_next_week\" : one short Arabic sentence naming the single highest-impact focus.")
        appendLine()
        appendLine("RULES")
        appendLine("- Write ALL values in natural, fluent Arabic. Keep English only for the learner's own vocabulary words.")
        appendLine("- Mention specific leech words by name when they exist.")
        appendLine("- If activity was low, be kind but clear about it — no false praise.")
        appendLine("- Keep every string under 160 characters.")
        appendLine("- CRITICAL: tie every suggestion to the learner's OWN curriculum listed above —")
        appendLine("  name the actual course/lesson they should study next. Never suggest outside")
        appendLine("  material, random topics, or content that is not in their imported courses.")
    }

    /**
     * Fully offline rule-based analysis. Reads exactly the same facts and
     * produces the same shape, so the UI never branches.
     */
    fun localReport(f: CoachFacts, stamp: String): CoachReport {
        val good = ArrayList<String>()
        val weak = ArrayList<String>()
        val tips = ArrayList<String>()
        val s = f.span

        // ---- cold start ----
        // Never diagnose a learner who has not produced a single data point.
        // Give them a setup path instead of a fabricated performance review.
        val noActivity = s.activeDays == 0 && s.reviews == 0 && s.lessons == 0
        if (noActivity && f.totalWords == 0) {
            return CoachReport(
                scope = f.scope.name,
                good = listOf("التطبيق جاهز — تبقّى فقط أن تضيف محتواك."),
                weak = listOf("لا توجد بيانات بعد، لذلك لا يمكنني تحليل أدائك."),
                suggestions = listOf(
                    "استورد كورساً من شاشة الاستيراد، أو أضف 10 كلمات يدوياً للقاموس.",
                    "حدّد وقت دراسة ثابتاً في الإعدادات ليصلك تذكير يومي.",
                    "ابدأ بجلسة مراجعة واحدة — عندها أبدأ بقياس ذاكرتك فعلياً.",
                ),
                motivation = "أفضل يوم للبدء كان أمس، وثاني أفضل يوم هو اليوم. أضف أول كلماتك الآن.",
                focusNextWeek = "إضافة المحتوى وبدء أول جلسة مراجعة.",
                stamp = stamp,
                epochDay = Telemetry.today(),
                local = true,
            )
        }
        if (noActivity) {
            return CoachReport(
                scope = f.scope.name,
                good = listOf("لديك ${f.totalWords} كلمة في القاموس وكل شيء مهيّأ للانطلاق."),
                weak = listOf("لم تبدأ أي جلسة بعد — لا يوجد ما أقيسه حتى الآن."),
                suggestions = listOf(
                    "ابدأ بمراجعة ${minOf(f.dueNow.coerceAtLeast(5), 20)} كلمة اليوم — 10 دقائق تكفي.",
                    "فعّل النطق التلقائي لتجمع بين الاستماع والمراجعة.",
                    "أكمل درساً واحداً لتفتح كلمات جديدة.",
                ),
                motivation = "أول جلسة هي الأصعب والأهم — بعدها يبدأ المحرك بجدولة مراجعاتك تلقائياً.",
                focusNextWeek = "إتمام أول ثلاث جلسات متتالية.",
                stamp = stamp,
                epochDay = Telemetry.today(),
                local = true,
            )
        }

        // ---- strengths ----
        if (s.activeDays >= (f.scope.days * 0.7).toInt() && s.activeDays > 0) {
            good.add("انتظامك ممتاز: درست ${s.activeDays} يوماً من أصل ${f.scope.days}.")
        }
        if (s.reviews > 0 && s.recallRate >= 0.75f) {
            good.add("معدل تذكّرك ${(s.recallRate * 100).toInt()}% على ${s.reviews} مراجعة — ذاكرة قوية.")
        }
        if (s.lessons > 0) good.add("أكملت ${s.lessons} درساً في هذه الفترة.")
        if (f.masteredWords > 0) good.add("رصيدك ${f.masteredWords} كلمة محفوظة من أصل ${f.totalWords}.")
        // Curriculum-anchored praise — names the learner's real course.
        val cur = f.curriculum
        if (cur.lessonsTotal > 0 && cur.lessonsDone > 0) {
            good.add("أنجزت ${cur.overallPct}% من منهجك (${cur.lessonsDone} من ${cur.lessonsTotal} درساً).")
        }
        cur.coursesWithContent.firstOrNull { it.isComplete }?.let {
            good.add("أكملت كورس \"${it.name}\" بالكامل.")
        }
        if (f.streak >= 3) good.add("سلسلة ${f.streak} يوم متتالي — استمرارية رائعة.")
        if (s.examAvg >= 75 && s.exams > 0) good.add("متوسط اختباراتك ${s.examAvg}% — أداء فوق المتوسط.")
        if (good.isEmpty()) good.add("بدأت الطريق، وهذه أصعب خطوة. البيانات ستتحسن بأول جلسة مراجعة.")

        // ---- weaknesses ----
        if (s.activeDays < f.scope.days / 2) {
            weak.add("نشطت ${s.activeDays} يوماً فقط من ${f.scope.days} — الفجوات تُضعف التثبيت.")
        }
        if (f.dueNow > 25) weak.add("تراكمت ${f.dueNow} كلمة مستحقة للمراجعة.")
        if (s.reviews > 5 && s.recallRate < 0.65f) {
            weak.add("معدل التذكّر ${(s.recallRate * 100).toInt()}% — أقل من المستوى الصحي (70%+).")
        }
        if (f.leeches.isNotEmpty()) {
            weak.add("كلمات عنيدة تتكرر: " + f.leeches.take(3).joinToString("، ") { it.english } + ".")
        }
        if (s.exams > 0 && s.examAvg < 60) weak.add("متوسط الاختبارات ${s.examAvg}% — يحتاج علاجاً مركّزاً.")
        if (s.listenMinutes < 10 && f.scope.days >= 7) weak.add("الاستماع ضعيف: ${s.listenMinutes} دقيقة فقط.")
        if (!f.planOnTrack && f.planDrift < 0) weak.add("أنت متأخر ${-f.planDrift} درساً عن خطتك.")
        // Curriculum-anchored weakness — names the real lagging area.
        cur.weakestSkill?.let { ws ->
            if (ws.pct < 40) {
                weak.add("أضعف جانب في منهجك: ${ws.type.label} عند ${ws.pct}% فقط.")
            }
        }
        if (weak.isEmpty()) weak.add("لا توجد نقاط ضعف حادة — واصل على نفس الوتيرة.")

        // ---- prescriptions ----
        if (f.dueNow > 0) tips.add("ابدأ كل يوم بمراجعة ${minOf(f.dueNow, 25)} كلمة مستحقة قبل أي نشاط آخر.")
        if (f.leeches.isNotEmpty()) {
            tips.add("اصنع رابطاً ذهنياً مصوّراً لكلمة \"${f.leeches.first().english}\" وأخواتها من زر الروابط الذهنية.")
        }
        if (s.listenMinutes < 20) tips.add("أضف 10 دقائق استماع يومياً بتشغيل النطق التلقائي في المراجعة.")
        if (s.exams == 0) tips.add("اختبر نفسك باختبار ذكي واحد هذا الأسبوع لقياس مستواك فعلياً.")
        // The single most useful prescription: the exact next lesson in MY course.
        cur.nextCourse?.let { nc ->
            tips.add(0, "تابع كورس \"${nc.name}\" — بقي ${nc.remaining} درساً (أنت عند ${nc.pct}%).")
        }
        if (s.lessons == 0) tips.add("أكمل درساً واحداً على الأقل لتفتح كلمات جديدة للمراجعة.")
        while (tips.size < 3) tips.add("حافظ على جلسة يومية قصيرة (${maxOf(15, s.avgMinutesPerActiveDay)} دقيقة) بدل جلسة طويلة نادرة.")

        val motivation = when {
            f.streak >= 7 -> "سلسلتك ${f.streak} يوم — أنت الآن في المنطقة التي يتوقف عندها أغلب الناس. لا تتوقف."
            s.activeDays > 0 -> "كل مراجعة صغيرة اليوم تعني كلمة لن تنساها بعد شهر. استمر."
            else -> "العودة اليوم أفضل من البداية المثالية غداً. افتح جلسة واحدة فقط."
        }
        val focus = when {
            f.dueNow > 25 -> "تصفية المراجعات المتراكمة قبل إضافة أي كلمات جديدة."
            f.leeches.isNotEmpty() -> "علاج الكلمات العنيدة بالصور الذهنية والأمثلة الشخصية."
            cur.nextCourse != null -> "التقدّم في كورس \"${cur.nextCourse!!.name}\" حتى إنهائه."
            s.listenMinutes < 20 -> "رفع حصة الاستماع اليومية."
            s.lessons == 0 -> "إكمال دروس جديدة لتوسيع الرصيد."
            else -> "الحفاظ على الانتظام اليومي ورفع دقة الاختبارات."
        }

        return CoachReport(
            scope = f.scope.name,
            good = good.take(3),
            weak = weak.take(3),
            suggestions = tips.take(3),
            motivation = motivation,
            focusNextWeek = focus,
            stamp = stamp,
            epochDay = Telemetry.today(),
            local = true,
        )
    }
}
