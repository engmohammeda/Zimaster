package com.zmastery.english.cloud

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.zmastery.english.data.ImportEngine
import com.zmastery.english.data.LessonPackage
import kotlinx.coroutines.tasks.await

/**
 * Cloud content + progress sync, built on Cloud Firestore (free Spark plan is
 * more than enough for one learner's whole curriculum).
 *
 * Two independent things live in Firestore:
 *
 *  1. CONTENT — collection "lessons": every document is one lesson, added
 *     entirely OUTSIDE the app (a small upload script the learner runs on
 *     their computer — see tools/upload_lessons.py). Each document stores
 *     the exact same per-lesson JSON the in-app importer already understands
 *     (field "json"), so pulling it into the app reuses the same battle
 *     tested [ImportEngine] parsing — zero duplicate logic, zero risk of the
 *     cloud format drifting from the local one.
 *
 *  2. PROGRESS — document "users/{uid}/progress/state": a mirror of the
 *     local on-device [com.zmastery.english.data.AppState] blob, so the
 *     learner's streak / XP / SRS memory state / everything survives a
 *     reinstall or a new device. DataStore stays the fast local cache;
 *     Firestore is the backup + cross-device sync layer.
 */
object CloudSync {

    private val db get() = Firebase.firestore

    private const val LESSONS_COLLECTION = "lessons"
    private const val UPDATED_AT = "updated_at"

    /** One lesson document pulled from Firestore. */
    data class RemoteLesson(val docId: String, val json: String, val updatedAtMillis: Long)

    /**
     * Fetch every lesson document added/changed since [sinceMillis] (0 = every
     * lesson ever uploaded — used for the very first sync). Ordered oldest
     * first so a partial/interrupted sync can safely resume from the last
     * timestamp it actually saw.
     */
    suspend fun fetchLessonsSince(sinceMillis: Long): Result<List<RemoteLesson>> = runCatching {
        val snap = db.collection(LESSONS_COLLECTION)
            .whereGreaterThan(UPDATED_AT, sinceMillis)
            .orderBy(UPDATED_AT, Query.Direction.ASCENDING)
            .get()
            .await()
        snap.documents.mapNotNull { doc ->
            val json = doc.getString("json") ?: return@mapNotNull null
            val updatedAt = doc.getLong(UPDATED_AT) ?: 0L
            RemoteLesson(doc.id, json, updatedAt)
        }
    }

    /** Every lesson document, regardless of timestamp (used by "إعادة مزامنة كاملة"). */
    suspend fun fetchAllLessons(): Result<List<RemoteLesson>> = fetchLessonsSince(0L)

    /**
     * Parse the raw JSON of every remote lesson into the same [LessonPackage]
     * model the manual importer produces. Invalid documents are skipped
     * (reported in [SyncResult.skipped]) rather than failing the whole sync.
     */
    data class SyncResult(
        val packages: List<LessonPackage>,
        val latestUpdatedAtMillis: Long,
        val skipped: Int,
    )

    suspend fun pullNewLessons(sinceMillis: Long): Result<SyncResult> {
        val remote = fetchLessonsSince(sinceMillis).getOrElse { return Result.failure(it) }
        if (remote.isEmpty()) return Result.success(SyncResult(emptyList(), sinceMillis, 0))
        val packages = mutableListOf<LessonPackage>()
        var skipped = 0
        var latest = sinceMillis
        remote.forEach { r ->
            if (r.updatedAtMillis > latest) latest = r.updatedAtMillis
            val parsed = runCatching {
                ImportEngine.json.decodeFromString(LessonPackage.serializer(), r.json)
            }.getOrNull()
            if (parsed != null && (parsed.metadata.courseId.isNotBlank() || parsed.metadata.courseNameAr.isNotBlank()) && parsed.metadata.title.isNotBlank()) {
                packages += parsed
            } else {
                skipped++
            }
        }
        return Result.success(SyncResult(packages, latest, skipped))
    }

    // ---------------------------------------------------------------- PROGRESS

    private fun progressDoc(uid: String) =
        db.collection("users").document(uid).collection("progress").document("state")

    /** Push the full local AppState JSON blob to the cloud (overwrite). */
    suspend fun pushProgress(uid: String, stateJson: String): Result<Unit> = runCatching {
        progressDoc(uid).set(
            mapOf(
                "json" to stateJson,
                UPDATED_AT to FieldValue.serverTimestamp(),
                "client_updated_at" to System.currentTimeMillis(),
            )
        ).await()
        Unit
    }

    /** Pull the cloud progress blob, or null if nothing has been synced yet. */
    suspend fun pullProgress(uid: String): Result<String?> = runCatching {
        val doc = progressDoc(uid).get().await()
        doc.getString("json")
    }

    /** Epoch millis the cloud copy was last written (0 = never synced). */
    suspend fun pullProgressTimestamp(uid: String): Result<Long> = runCatching {
        val doc = progressDoc(uid).get().await()
        doc.getLong("client_updated_at") ?: 0L
    }
}
