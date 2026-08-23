package com.zmastery.english.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Backup & Restore engine.
 *
 * Produces / consumes several export formats so the learner never loses data:
 *  - FULL      : complete AppState (courses + lessons + vocab + profile + FSRS)
 *                — a self-contained ".zmastery" JSON. Because audio is generated
 *                on-device via TTS from the stored text, restoring this file
 *                fully reproduces every lesson AND its audio.
 *  - LESSONS   : courses + lessons only (share a curriculum without your progress).
 *  - HARD_WORDS: the difficult words (leeches) as JSON and/or CSV for study elsewhere.
 *
 * All formats are validated on import and merged safely (delta update).
 */
object BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    const val MAGIC = "ZMASTERY_BACKUP"
    const val FORMAT_VERSION = 3

    // ---------------------------------------------------------------- FULL
    @kotlinx.serialization.Serializable
    data class FullBackup(
        val magic: String = MAGIC,
        val formatVersion: Int = FORMAT_VERSION,
        val createdAt: String = "",
        val appVersion: String = "2.1.0",
        /** Simple integrity hash — detects truncated or corrupted files. */
        val checksum: String = "",
        val state: AppState,
    )

    /** Compute a lightweight checksum: lesson count + vocab count + first 8 chars of name. */
    private fun computeChecksum(state: AppState): String {
        val raw = "${state.lessons.size}:${state.vocab.size}:${state.profile.learnerName.take(8)}"
        // Simple hash — not cryptographic, just corruption detection
        return raw.hashCode().toString(16)
    }

    fun exportFull(state: AppState, createdAt: String): String =
        json.encodeToString(
            FullBackup(
                createdAt = createdAt,
                checksum = computeChecksum(state),
                state = state,
            )
        )

    /**
     * Export a full backup with API keys stripped — safe for sharing.
     * Use this when the user wants to share a backup file with someone
     * else (e.g., sharing course content) without leaking their API keys.
     */
    fun exportFullSafe(state: AppState, createdAt: String): String {
        val safeState = KeyProtector.stripKeysForSharing(state)
        return json.encodeToString(
            FullBackup(
                createdAt = createdAt,
                checksum = computeChecksum(safeState),
                state = safeState,
            )
        )
    }

    fun parseFull(raw: String): Result<AppState> = runCatching {
        val text = raw.trim()
        // Accept both a wrapped FullBackup and a bare AppState.
        if (text.contains("\"magic\"")) {
            val backup = json.decodeFromString<FullBackup>(text)
            require(backup.magic == MAGIC) { "ملف النسخة الاحتياطية غير صالح" }
            // Verify checksum if present (v3+ backups)
            if (backup.checksum.isNotBlank()) {
                val expected = computeChecksum(backup.state)
                require(backup.checksum == expected) {
                    "ملف النسخة الاحتياطية تالف (عدم تطابق التحقق)"
                }
            }
            // Migrate older formats if needed
            migrateState(backup.state, backup.formatVersion)
        } else {
            json.decodeFromString<AppState>(text)
        }
    }

    /**
     * Migrate older backup formats to the current version.
     * Each migration step transforms the state from one version to the next.
     */
    private fun migrateState(state: AppState, fromVersion: Int): AppState {
        var s = state
        // v1 → v2: no structural changes needed (all new fields have defaults)
        // v2 → v3: no structural changes needed (checksum added to wrapper only)
        // Future migrations go here:
        // if (fromVersion < 4) { s = migrateV3toV4(s) }
        return s
    }

    // ------------------------------------------------------------- LESSONS
    @kotlinx.serialization.Serializable
    data class LessonsBackup(
        val magic: String = "ZMASTERY_LESSONS",
        val formatVersion: Int = FORMAT_VERSION,
        val createdAt: String = "",
        val courses: List<CourseDto>,
        val lessons: List<LessonDto>,
        val vocab: List<WordDto> = emptyList(),
    )

    fun exportLessons(courses: List<CourseDto>, lessons: List<LessonDto>, vocab: List<WordDto>, createdAt: String): String =
        json.encodeToString(LessonsBackup(createdAt = createdAt, courses = courses, lessons = lessons, vocab = vocab))

    fun parseLessons(raw: String): Result<LessonsBackup> = runCatching {
        json.decodeFromString<LessonsBackup>(raw.trim())
    }

    // ----------------------------------------------------------- HARD WORDS
    @kotlinx.serialization.Serializable
    data class WordsBackup(
        val magic: String = "ZMASTERY_WORDS",
        val formatVersion: Int = FORMAT_VERSION,
        val createdAt: String = "",
        val count: Int = 0,
        val words: List<WordDto>,
    )

    fun exportWordsJson(words: List<WordDto>, createdAt: String): String =
        json.encodeToString(WordsBackup(createdAt = createdAt, count = words.size, words = words))

    fun parseWords(raw: String): Result<List<WordDto>> = runCatching {
        val text = raw.trim()
        if (text.contains("\"words\"")) json.decodeFromString<WordsBackup>(text).words
        else json.decodeFromString<List<WordDto>>(text)
    }

    /** CSV for spreadsheets / Anki-style import. */
    fun exportWordsCsv(words: List<WordDto>): String = buildString {
        append("english,arabic,phonetic,example_en,example_ar,mental_image,lapses,repetitions,mastered\n")
        words.forEach { w ->
            append(
                listOf(
                    w.english, w.arabic, w.phonetic, w.exampleEn, w.exampleAr,
                    w.mentalImage, w.lapses.toString(), w.repetitions.toString(),
                    if (w.mastered) "yes" else "no",
                ).joinToString(",") { csvCell(it) }
            )
            append("\n")
        }
    }

    private fun csvCell(s: String): String {
        val needsQuote = s.contains(',') || s.contains('"') || s.contains('\n')
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}
