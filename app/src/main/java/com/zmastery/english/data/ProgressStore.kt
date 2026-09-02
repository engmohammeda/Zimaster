package com.zmastery.english.data

import android.content.Context

/**
 * Lightweight SharedPreferences bridge shared between the app and the
 * home-screen widget so the widget can render real streak / progress data.
 */
object ProgressStore {
    private const val PREFS = "zmastery_progress"
    private const val KEY_STREAK = "streak"
    private const val KEY_XP = "xp"
    private const val KEY_REVIEWS = "reviews_today"
    private const val KEY_GOAL = "daily_goal"
    private const val KEY_TASKS_DONE = "tasks_done"
    private const val KEY_TASKS_TOTAL = "tasks_total"
    private const val KEY_HAS_CONTENT = "has_content"
    /** المرحلة الرابعة — حالة الصندوق الباكي على الودجت. */
    private const val KEY_CHEST_MOOD = "chest_mood"
    private const val KEY_DECAY_SEVERITY = "decay_severity"
    /** أُنجز الحد الأدنى اليومي — يسمح للودجت بإعادة الحساب بنفسه. */
    private const val KEY_MIN_DONE = "min_done"

    // ── الودجت الجديد: كلمة اللحظة + نمط الأسبوع ──
    /** الكلمة المستحقة للمراجعة الآن (FSRS) — يعرضها الودجت ليُعلّم لا ليُذكّر. */
    private const val KEY_WORD_EN = "word_en"
    private const val KEY_WORD_AR = "word_ar"
    private const val KEY_WORD_IPA = "word_ipa"
    /** سبعة أحرف 0/1 — أقدم يوم أولاً، آخرها اليوم. يُظهر النمط لا الرقم. */
    private const val KEY_WEEK = "week_pattern"

    fun save(
        context: Context,
        streak: Int,
        xp: Int,
        reviewsToday: Int,
        dailyGoal: Int,
        tasksDone: Int,
        tasksTotal: Int,
        hasContent: Boolean,
        chestMood: String = "IDLE",
        decaySeverity: Float = 0f,
        minimumDone: Boolean = false,
        wordEn: String = "",
        wordAr: String = "",
        wordIpa: String = "",
        weekPattern: String = "",
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_XP, xp)
            .putInt(KEY_REVIEWS, reviewsToday)
            .putInt(KEY_GOAL, dailyGoal)
            .putInt(KEY_TASKS_DONE, tasksDone)
            .putInt(KEY_TASKS_TOTAL, tasksTotal)
            .putBoolean(KEY_HAS_CONTENT, hasContent)
            .putString(KEY_CHEST_MOOD, chestMood)
            .putFloat(KEY_DECAY_SEVERITY, decaySeverity)
            .putBoolean(KEY_MIN_DONE, minimumDone)
            .putString(KEY_WORD_EN, wordEn)
            .putString(KEY_WORD_AR, wordAr)
            .putString(KEY_WORD_IPA, wordIpa)
            .putString(KEY_WEEK, weekPattern)
            .apply()
    }

    data class Snapshot(
        val streak: Int,
        val xp: Int,
        val reviewsToday: Int,
        val dailyGoal: Int,
        val tasksDone: Int,
        val tasksTotal: Int,
        val hasContent: Boolean,
        val chestMood: String = "IDLE",
        val decaySeverity: Float = 0f,
        val minimumDone: Boolean = false,
        val wordEn: String = "",
        val wordAr: String = "",
        val wordIpa: String = "",
        val weekPattern: String = "",
    ) {
        /** الصندوق متصدّع ويحتاج إنقاذاً عاجلاً. */
        val isCracking: Boolean get() = chestMood == "CRACKING"
        val isBroken: Boolean get() = chestMood == "BROKEN"

        /** صف كلمة اللحظة يظهر فقط حين توجد كلمة فعلية — لا بطاقة فارغة. */
        val hasWord: Boolean get() = wordEn.isNotBlank()

        /**
         * نمط الأسبوع كقائمة منطقية بطول 7 (أقدم يوم أولاً).
         * أي قيمة محفوظة تالفة أو ناقصة تسقط آمناً على 7 أيام فارغة.
         */
        val weekDays: List<Boolean>
            get() = if (weekPattern.length == 7) weekPattern.map { it == '1' }
            else List(7) { false }
    }

    /**
     * يقرأ اللقطة ويعيد حساب حالة التصدّع بساعة الجهاز الحالية.
     *
     * لماذا؟ الودجت يُحدَّث كل نصف ساعة حتى والتطبيق مغلق تماماً. لو اكتفينا
     * بالقيمة المحفوظة لبقي الصندوق «هادئاً» طوال المساء لأن آخر حفظ جرى
     * ظهراً. بإعادة الحساب هنا يبدأ الدخان الأحمر في الظهور والتكاثف تلقائياً
     * بعد الساعة 8 مساءً بتوقيت الجهاز — دون أي خدمة خلفية أو استهلاك بطارية.
     */
    fun load(context: Context): Snapshot {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val streak = p.getInt(KEY_STREAK, 0)
        val minDone = p.getBoolean(KEY_MIN_DONE, false)
        val storedMood = p.getString(KEY_CHEST_MOOD, "IDLE") ?: "IDLE"

        // حالة الانكسار تأتي من التطبيق فقط (تحتاج معرفة مهمة الإنقاذ).
        val recomputed = if (storedMood == "BROKEN") {
            "BROKEN" to 1f
        } else {
            val now = java.time.LocalTime.now()
            val d = EnigmaStreakEngine.computeDecay(
                minimumDone = minDone,
                currentStreak = streak,
                hourNow = now.hour,
                minuteNow = now.minute,
                streakBroken = false,
            )
            d.mood.name to d.severity
        }

        return Snapshot(
            streak = streak,
            xp = p.getInt(KEY_XP, 0),
            reviewsToday = p.getInt(KEY_REVIEWS, 0),
            dailyGoal = p.getInt(KEY_GOAL, 30),
            tasksDone = p.getInt(KEY_TASKS_DONE, 0),
            tasksTotal = p.getInt(KEY_TASKS_TOTAL, 4),
            hasContent = p.getBoolean(KEY_HAS_CONTENT, false),
            chestMood = recomputed.first,
            decaySeverity = recomputed.second,
            minimumDone = minDone,
            wordEn = p.getString(KEY_WORD_EN, "").orEmpty(),
            wordAr = p.getString(KEY_WORD_AR, "").orEmpty(),
            wordIpa = p.getString(KEY_WORD_IPA, "").orEmpty(),
            weekPattern = p.getString(KEY_WEEK, "").orEmpty(),
        )
    }
}
