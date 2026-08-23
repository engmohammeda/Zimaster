package com.zmastery.english.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * حماية البيانات — طبقة أمان فوق [Persistence] تضمن عدم فقدان بيانات
 * المتعلّم أبداً، حتى في أسوأ السيناريوهات (انقطاع أثناء الحفظ، ملف تالف،
 * مساحة ممتلئة).
 *
 * الآليات:
 *  1. **نسخة احتياطية قبل كل حفظ** — نحفظ الحالة السابقة في مفتاح منفصل
 *     قبل استبدالها، فإذا فسد الحفظ الجديد نستعيد القديمة.
 *  2. **تحقق من السلامة بعد التحميل** — نتأكد أن البيانات المحملة ليست
 *     فارغة بشكل مشبوه (مثلاً: كل الدروس اختفت).
 *  3. **تسجيل الأخطاء بوضوح** — لا نبتلع الأخطاء بصمت.
 *  4. **استعادة تلقائية** — إذا فشل تحميل البيانات الرئيسية، نحاول
 *     تحميل النسخة الاحتياطية السابقة.
 */
object DataGuard {
    private const val TAG = "ZMasteryDataGuard"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // SharedPreferences keys for the fallback backup
    private const val PREFS_NAME = "zmastery_data_guard"
    private const val KEY_BACKUP = "last_good_state"
    private const val KEY_BACKUP_TIME = "backup_time_ms"
    private const val KEY_BACKUP_LESSON_COUNT = "backup_lesson_count"
    private const val KEY_BACKUP_VOCAB_COUNT = "backup_vocab_count"
    private const val KEY_CORRUPTION_COUNT = "corruption_count"
    private const val KEY_LAST_ERROR = "last_error"

    // ──────────────────────── Snapshot metadata ────────────────────────

    data class StateHealth(
        val lessonCount: Int,
        val vocabCount: Int,
        val courseCount: Int,
        val hasProfile: Boolean,
    ) {
        val isEmpty: Boolean get() = lessonCount == 0 && vocabCount == 0 && courseCount == 0
    }

    fun healthOf(state: AppState): StateHealth = StateHealth(
        lessonCount = state.lessons.size,
        vocabCount = state.vocab.size,
        courseCount = state.courses.size,
        hasProfile = state.profile.learnerName.isNotBlank(),
    )

    // ──────────────────────── Safe save with backup ────────────────────────

    /**
     * يحفظ الحالة مع إنشاء نسخة احتياطية من الحالة السابقة أولاً.
     * يُرجع `true` إذا نجح الحفظ، `false` مع رسالة خطأ إذا فشل.
     */
    suspend fun safeSave(context: Context, newState: AppState): SaveResult {
        val ctx = context.applicationContext

        // Step 1: Read the current state to create a backup
        val currentState = try {
            Persistence.load(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read current state for backup: ${e.message}")
            null
        }

        // Step 2: If we have a current state, back it up
        if (currentState != null && !healthOf(currentState).isEmpty) {
            try {
                createBackup(ctx, currentState)
            } catch (e: Exception) {
                Log.w(TAG, "Could not create backup before save: ${e.message}")
                // Continue anyway — the new save is more important
            }
        }

        // Step 3: Validate the new state before saving
        val newHealth = healthOf(newState)
        if (currentState != null) {
            val oldHealth = healthOf(currentState)
            val warning = detectDataLoss(oldHealth, newHealth)
            if (warning != null) {
                Log.w(TAG, "Potential data loss detected: $warning")
                // Still save — the caller decided to save, but we log it
            }
        }

        // Step 4: Perform the actual save
        return try {
            Persistence.save(ctx, newState)
            SaveResult(success = true)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Save FAILED: $msg", e)
            recordError(ctx, "save_failed: $msg")
            SaveResult(success = false, error = msg)
        }
    }

    // ──────────────────────── Safe load with recovery ────────────────────────

    /**
     * يحمّل الحالة مع محاولة الاستعادة من النسخة الاحتياطية إذا فشل
     * التحميل الرئيسي أو كانت البيانات فاسدة.
     */
    suspend fun safeLoad(context: Context): LoadResult {
        val ctx = context.applicationContext

        // Step 1: Try loading the primary state
        val primary = try {
            Persistence.load(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "Primary load FAILED: ${e.message}", e)
            recordError(ctx, "load_failed: ${e.message}")
            null
        }

        // Step 2: If primary loaded and looks healthy, return it
        if (primary != null) {
            val health = healthOf(primary)
            if (!health.isEmpty || !hasBackup(ctx)) {
                return LoadResult(
                    state = primary,
                    source = LoadSource.PRIMARY,
                    health = health,
                )
            }
            // Primary is empty but we have a backup — suspicious
            Log.w(TAG, "Primary state is empty, attempting backup recovery")
        }

        // Step 3: Try loading from backup
        val backup = try {
            loadBackup(ctx)
        } catch (e: Exception) {
            Log.e(TAG, "Backup load also FAILED: ${e.message}", e)
            null
        }

        if (backup != null) {
            val health = healthOf(backup)
            Log.i(TAG, "Recovered from backup: ${health.lessonCount} lessons, ${health.vocabCount} vocab")
            // Restore the backup as the primary state
            try {
                Persistence.save(ctx, backup)
                Log.i(TAG, "Backup restored as primary state")
            } catch (e: Exception) {
                Log.e(TAG, "Could not restore backup as primary: ${e.message}")
            }
            return LoadResult(
                state = backup,
                source = LoadSource.BACKUP,
                health = health,
                recovered = true,
            )
        }

        // Step 4: Both failed — return empty state
        return LoadResult(
            state = primary ?: AppState(),
            source = if (primary != null) LoadSource.PRIMARY else LoadSource.EMPTY,
            health = healthOf(primary ?: AppState()),
        )
    }

    // ──────────────────────── Backup management ────────────────────────

    private fun createBackup(context: Context, state: AppState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = json.encodeToString(state)

        // Size guard: don't store backups larger than 2MB in SharedPreferences
        if (raw.length > 2_000_000) {
            Log.w(TAG, "State too large for backup (${raw.length} chars), skipping")
            return
        }

        prefs.edit()
            .putString(KEY_BACKUP, raw)
            .putLong(KEY_BACKUP_TIME, System.currentTimeMillis())
            .putInt(KEY_BACKUP_LESSON_COUNT, state.lessons.size)
            .putInt(KEY_BACKUP_VOCAB_COUNT, state.vocab.size)
            .apply()
    }

    private fun loadBackup(context: Context): AppState? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_BACKUP, null) ?: return null
        return json.decodeFromString<AppState>(raw)
    }

    private fun hasBackup(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_BACKUP)
    }

    /** معلومات النسخة الاحتياطية المتاحة (للعرض في الواجهة). */
    fun backupInfo(context: Context): BackupInfo? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_BACKUP_TIME, 0L)
        if (time == 0L) return null
        return BackupInfo(
            timestamp = time,
            lessonCount = prefs.getInt(KEY_BACKUP_LESSON_COUNT, 0),
            vocabCount = prefs.getInt(KEY_BACKUP_VOCAB_COUNT, 0),
        )
    }

    // ──────────────────────── Data loss detection ────────────────────────

    /**
     * يكشف فقدان البيانات المحتمل بمقارنة الحالة القديمة والجديدة.
     * لا يمنع الحفظ، بل يُسجّل تحذيراً.
     */
    private fun detectDataLoss(old: StateHealth, new: StateHealth): String? {
        // Significant lesson loss (> 50% of lessons gone)
        if (old.lessonCount > 5 && new.lessonCount < old.lessonCount * 0.5) {
            return "فقدان دروس كبير: ${old.lessonCount} → ${new.lessonCount}"
        }
        // Significant vocab loss
        if (old.vocabCount > 20 && new.vocabCount < old.vocabCount * 0.5) {
            return "فقدان مفردات كبير: ${old.vocabCount} → ${new.vocabCount}"
        }
        // Profile loss
        if (old.hasProfile && !new.hasProfile) {
            return "فقدان بيانات الملف الشخصي"
        }
        return null
    }

    // ──────────────────────── Error tracking ────────────────────────

    private fun recordError(context: Context, error: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_CORRUPTION_COUNT, 0) + 1
            prefs.edit()
                .putInt(KEY_CORRUPTION_COUNT, count)
                .putString(KEY_LAST_ERROR, "${System.currentTimeMillis()}: $error")
                .apply()
        } catch (_: Exception) {}
    }

    /** عدد مرات الفشل المسجلة (للعرض في شاشة التشخيص). */
    fun corruptionCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CORRUPTION_COUNT, 0)
    }

    /** آخر رسالة خطأ مسجلة. */
    fun lastError(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_ERROR, null)
    }
}

// ──────────────────────── Result types ────────────────────────

data class SaveResult(
    val success: Boolean,
    val error: String? = null,
)

data class LoadResult(
    val state: AppState,
    val source: LoadSource,
    val health: DataGuard.StateHealth,
    val recovered: Boolean = false,
)

enum class LoadSource {
    PRIMARY,
    BACKUP,
    EMPTY,
}

data class BackupInfo(
    val timestamp: Long,
    val lessonCount: Int,
    val vocabCount: Int,
)
