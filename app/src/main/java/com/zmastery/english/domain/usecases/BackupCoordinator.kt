package com.zmastery.english.domain.usecases

import com.zmastery.english.data.AppState
import com.zmastery.english.data.BackupManager

/**
 * Backup Coordinator Use Case — manages backup creation, restoration, and validation.
 *
 * Extracted from AppViewModel to be independently testable and reusable.
 * Contains no Android dependencies — pure Kotlin (operates on strings).
 *
 * Responsibilities:
 *  - Export full/lessons/words backups
 *  - Parse and validate backup files
 *  - Validate backup integrity via checksum
 *  - Handle format version migration
 */
class BackupCoordinator {

    /** Export the full app state as a backup JSON string. */
    fun exportFull(state: AppState, createdAt: String): String =
        BackupManager.exportFull(state, createdAt)

    /** Parse a full backup, validating magic + checksum. */
    fun parseFull(raw: String): Result<AppState> =
        BackupManager.parseFull(raw)

    /** Export only lessons (share curriculum without progress). */
    fun exportLessons(
        state: AppState,
        createdAt: String,
    ): String = BackupManager.exportLessons(
        state.courses, state.lessons, state.vocab, createdAt,
    )

    /** Export hard words (leeches) as JSON. */
    fun exportHardWords(
        words: List<com.zmastery.english.data.WordDto>,
        createdAt: String,
    ): String = BackupManager.exportWordsJson(words, createdAt)

    /** Export hard words as CSV for Anki-style import. */
    fun exportHardWordsCsv(
        words: List<com.zmastery.english.data.WordDto>,
    ): String = BackupManager.exportWordsCsv(words)

    /** Parse a words backup (JSON). */
    fun parseWords(raw: String): Result<List<com.zmastery.english.data.WordDto>> =
        BackupManager.parseWords(raw)

    /** Parse a lessons backup. */
    fun parseLessons(raw: String): Result<BackupManager.LessonsBackup> =
        BackupManager.parseLessons(raw)

    /**
     * Detect the backup type from raw text.
     *
     * @return one of: "full", "lessons", "words", "unknown"
     */
    fun detectBackupType(raw: String): String {
        val text = raw.trim()
        return when {
            text.contains("\"magic\"") && text.contains("ZMASTERY_BACKUP") -> "full"
            text.contains("\"magic\"") && text.contains("ZMASTERY_LESSONS") -> "lessons"
            text.contains("\"magic\"") && text.contains("ZMASTERY_WORDS") -> "words"
            text.contains("\"courses\"") && text.contains("\"lessons\"") -> "full"
            text.contains("\"words\"") -> "words"
            else -> "unknown"
        }
    }

    /**
     * Quick validation of a backup file before showing the restore dialog.
     * Returns a human-readable summary or error message.
     */
    fun validateBackup(raw: String): BackupValidation {
        val type = detectBackupType(raw)
        return when (type) {
            "full" -> {
                val result = parseFull(raw)
                if (result.isSuccess) {
                    val state = result.getOrThrow()
                    BackupValidation(
                        valid = true,
                        type = type,
                        summary = "نسخة كاملة: ${state.lessons.size} درس · " +
                            "${state.vocab.size} كلمة · " +
                            "${state.profile.learnerName.ifBlank { "بدون اسم" }}",
                    )
                } else {
                    BackupValidation(false, type, error = result.exceptionOrNull()?.message ?: "خطأ غير معروف")
                }
            }
            "lessons" -> {
                val result = parseLessons(raw)
                if (result.isSuccess) {
                    val backup = result.getOrThrow()
                    BackupValidation(
                        valid = true,
                        type = type,
                        summary = "دروس فقط: ${backup.courses.size} كورس · ${backup.lessons.size} درس",
                    )
                } else {
                    BackupValidation(false, type, error = result.exceptionOrNull()?.message ?: "خطأ غير معروف")
                }
            }
            "words" -> {
                val result = parseWords(raw)
                if (result.isSuccess) {
                    BackupValidation(
                        valid = true,
                        type = type,
                        summary = "كلمات: ${result.getOrThrow().size} كلمة",
                    )
                } else {
                    BackupValidation(false, type, error = result.exceptionOrNull()?.message ?: "خطأ غير معروف")
                }
            }
            else -> BackupValidation(false, type, error = "نوع نسخة احتياطية غير معروف")
        }
    }
}

data class BackupValidation(
    val valid: Boolean,
    val type: String,
    val summary: String = "",
    val error: String = "",
)
