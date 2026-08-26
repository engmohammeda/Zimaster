package com.zmastery.english.cloud

import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.zmastery.english.data.ImportEngine
import com.zmastery.english.data.LessonPackage
import com.zmastery.english.data.QuoteStore
import kotlinx.coroutines.tasks.await

/**
 * Cloud content + user provisioning + progress sync built on Cloud Firestore.
 */
object CloudSync {

    private val db get() = Firebase.firestore

    private const val LESSONS_COLLECTION = "lessons"
    private const val USERS_COLLECTION = "users"
    private const val ANNOUNCEMENTS_COLLECTION = "announcements"
    private const val QUOTES_COLLECTION = "quotes"
    private const val LEADERBOARD_COLLECTION = "leaderboard"
    private const val UPDATED_AT = "updated_at"

    /** Known super-admin emails */
    private val SUPER_ADMIN_EMAILS = setOf(
        "mohammedalbkhyty@gmail.com",
    )

    data class Announcement(
        val id: String = "",
        val title: String = "",
        val message: String = "",
        val type: String = "info", // "info", "update", "challenge", "alert"
        val createdAtMillis: Long = 0L,
        val isActive: Boolean = true,
    )

    data class UserProfileSnapshot(
        val uid: String,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null,
        val isAnonymous: Boolean = false,
        val streak: Int = 0,
        val xp: Int = 0,
        val completedLessonsCount: Int = 0,
        val wordsLearnedCount: Int = 0,
        val accuracy: Double = 0.0,
    )

    data class UserRecord(
        val uid: String,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null,
        val role: String = "student",
        val streak: Int = 0,
        val xp: Int = 0,
        val completedLessonsCount: Int = 0,
        val wordsLearnedCount: Int = 0,
        val accuracy: Double = 0.0,
        val lastActiveMillis: Long = 0L,
        val deviceModel: String? = null,
    )

    /**
     * Auto-provision or update the user document in `/users/{uid}` in Firestore.
     */
    suspend fun provisionOrUpdateUser(
        user: FirebaseUser,
        profile: UserProfileSnapshot,
    ): Result<String> = runCatching {
        val userRef = db.collection(USERS_COLLECTION).document(user.uid)
        val existingDoc = userRef.get().await()

        val isSuperAdmin = user.email?.lowercase()?.trim() in SUPER_ADMIN_EMAILS
        val existingRole = existingDoc.getString("role")

        val userData = mutableMapOf<String, Any?>(
            "uid" to user.uid,
            "email" to user.email,
            "displayName" to (user.displayName ?: profile.displayName ?: "Learner"),
            "photoUrl" to (user.photoUrl?.toString() ?: profile.photoUrl),
            "isAnonymous" to user.isAnonymous,
            "streak" to profile.streak,
            "xp" to profile.xp,
            "completedLessonsCount" to profile.completedLessonsCount,
            "wordsLearnedCount" to profile.wordsLearnedCount,
            "accuracy" to profile.accuracy,
            "lastActive" to FieldValue.serverTimestamp(),
            "lastActiveMillis" to System.currentTimeMillis(),
            "deviceModel" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "androidVersion" to Build.VERSION.RELEASE,
            "appVersion" to com.zmastery.english.BuildConfig.VERSION_NAME,
            "platform" to "android",
        )

        // SECURITY (anti privilege-escalation): the 'role' key is written ONLY
        // by the verified super-admin account. Everyone else must omit it
        // entirely — Firestore rules reject any write that attempts to change
        // 'role', so sending it would break the whole profile sync.
        if (isSuperAdmin) userData["role"] = "admin"

        if (!existingDoc.exists()) {
            userData["createdAt"] = FieldValue.serverTimestamp()
            userData["createdAtMillis"] = System.currentTimeMillis()
        }

        userRef.set(userData, SetOptions.merge()).await()

        // Public leaderboard mirror. /leaderboard is readable by EVERY signed-in
        // learner, so it must never contain the email (or any private field) —
        // the same rule also validates this server-side.
        db.collection(LEADERBOARD_COLLECTION).document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "displayName" to (user.displayName ?: profile.displayName ?: "Learner"),
                "photoUrl" to (user.photoUrl?.toString() ?: profile.photoUrl),
                "streak" to profile.streak,
                "xp" to profile.xp,
                "completedLessonsCount" to profile.completedLessonsCount,
                "wordsLearnedCount" to profile.wordsLearnedCount,
                "accuracy" to profile.accuracy,
                "lastActiveMillis" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()

        when {
            isSuperAdmin -> "admin"
            existingRole != null -> existingRole
            else -> "student"
        }
    }

    /**
     * Fetch the user's role from Firestore ("admin" or "student")
     */
    suspend fun fetchUserRole(uid: String): String = runCatching {
        val doc = db.collection(USERS_COLLECTION).document(uid).get().await()
        doc.getString("role") ?: "student"
    }.getOrDefault("student")

    /**
     * نفس [fetchUserRole] لكنها تميّز «لا يوجد مستند» من «المستند بلا دور» —
     * يحتاجها تشخيص سبب رفض النشر في شاشة أدوات المطور.
     * القيم: "admin" / "student" / "no-doc".
     */
    suspend fun fetchRoleDoc(uid: String): Result<String> = runCatching {
        val doc = db.collection(USERS_COLLECTION).document(uid).get().await()
        if (!doc.exists()) "no-doc" else (doc.getString("role") ?: "student")
    }

    /**
     * Fetch all registered users (for admin dashboard)
     */
    suspend fun fetchAllUsers(): Result<List<UserRecord>> = runCatching {
        val snap = try {
            db.collection(USERS_COLLECTION)
                .orderBy("lastActiveMillis", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()
        } catch (e: Exception) {
            // Fallback without ordering in case index or field is not indexed yet
            db.collection(USERS_COLLECTION)
                .limit(100)
                .get()
                .await()
        }

        snap.documents.mapNotNull { doc ->
            UserRecord(
                uid = doc.getString("uid") ?: doc.id,
                email = doc.getString("email"),
                displayName = doc.getString("displayName") ?: "مستخدم",
                photoUrl = doc.getString("photoUrl"),
                role = doc.getString("role") ?: "student",
                streak = (doc.getLong("streak") ?: 0L).toInt(),
                xp = (doc.getLong("xp") ?: 0L).toInt(),
                completedLessonsCount = (doc.getLong("completedLessonsCount") ?: 0L).toInt(),
                wordsLearnedCount = (doc.getLong("wordsLearnedCount") ?: 0L).toInt(),
                accuracy = doc.getDouble("accuracy") ?: 0.0,
                lastActiveMillis = doc.getLong("lastActiveMillis") ?: 0L,
                deviceModel = doc.getString("deviceModel"),
            )
        }.sortedByDescending { it.lastActiveMillis }
    }

    // ---------------------------------------------------------------- LESSONS

    /** One lesson document pulled from Firestore. */
    data class RemoteLesson(val docId: String, val json: String, val updatedAtMillis: Long)

    /**
     * Fetch every lesson document added/changed since [sinceMillis]
     */
    suspend fun fetchLessonsSince(sinceMillis: Long): Result<List<RemoteLesson>> = runCatching {
        val snap = try {
            if (sinceMillis > 0L) {
                db.collection(LESSONS_COLLECTION)
                    .whereGreaterThan(UPDATED_AT, sinceMillis)
                    .orderBy(UPDATED_AT, Query.Direction.ASCENDING)
                    .get()
                    .await()
            } else {
                db.collection(LESSONS_COLLECTION)
                    .get()
                    .await()
            }
        } catch (e: Exception) {
            // Fallback to plain collection fetch
            db.collection(LESSONS_COLLECTION)
                .get()
                .await()
        }

        snap.documents.mapNotNull { doc ->
            val json = doc.getString("json") ?: return@mapNotNull null
            val updatedAt = doc.getLong(UPDATED_AT) ?: 0L
            if (sinceMillis > 0L && updatedAt <= sinceMillis) null
            else RemoteLesson(doc.id, json, updatedAt)
        }.sortedBy { it.updatedAtMillis }
    }

    suspend fun fetchAllLessons(): Result<List<RemoteLesson>> = fetchLessonsSince(0L)

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

    // ---------------------------------------------------------------- PUBLISH / ADMIN (LESSONS)

    /**
     * Publish or update a single lesson package to Firestore under `/lessons/{docId}`.
     */
    suspend fun publishLessonToCloud(pkg: LessonPackage): Result<String> = runCatching {
        val courseKey = pkg.metadata.courseId.ifBlank { "l1_scratch" }
        val docId = "${courseKey}_lesson_${pkg.metadata.lessonNo}"
        val json = ImportEngine.json.encodeToString(LessonPackage.serializer(), pkg)

        val docData = mapOf(
            "docId" to docId,
            "courseId" to courseKey,
            "lessonNo" to pkg.metadata.lessonNo,
            "title" to pkg.metadata.title,
            "level" to pkg.metadata.level,
            "json" to json,
            UPDATED_AT to System.currentTimeMillis(),
            "updatedAtServer" to FieldValue.serverTimestamp(),
        )

        db.collection(LESSONS_COLLECTION).document(docId).set(docData, SetOptions.merge()).await()
        docId
    }

    /**
     * Publish a batch of lesson packages to Firestore.
     */
    suspend fun publishLessonsBatchToCloud(packages: List<LessonPackage>): Result<Int> = runCatching {
        var count = 0
        packages.forEach { pkg ->
            publishLessonToCloud(pkg).getOrThrow()
            count++
        }
        count
    }

    /**
     * Delete a lesson from Firestore by docId.
     */
    suspend fun deleteLessonFromCloud(docId: String): Result<Unit> = runCatching {
        db.collection(LESSONS_COLLECTION).document(docId).delete().await()
        Unit
    }

    /**
     * فهرس الدروس الموجودة فعلاً في السحابة: `docId → updated_at`.
     *
     * هذا هو مصدر الحقيقة لشارة «تم الرفع» — يشمل أيضاً الدروس التي رفعها
     * سكربت البايثون خارج التطبيق. ملاحظة تقنية: Firestore على أندرويد لا
     * يدعم اختيار حقول معيّنة، لذا تُنزَّل المستندات كاملة؛ لهذا يُستدعى
     * الفحص بطلب صريح من زر «التحقق من السحابة» لا تلقائياً.
     */
    suspend fun fetchCloudLessonIndex(): Result<Map<String, Long>> = runCatching {
        val snap = db.collection(LESSONS_COLLECTION).get().await()
        snap.documents.associate { doc -> doc.id to (doc.getLong(UPDATED_AT) ?: 0L) }
    }

    // ---------------------------------------------------------------- QUOTES

    /**
     * يسحب كل العبارات النشطة من `/quotes` (يضيفها المسؤول وتتزامن عبر الأجهزة).
     */
    suspend fun pullQuotes(): Result<List<QuoteStore.CloudQuote>> = runCatching {
        val snap = db.collection(QUOTES_COLLECTION)
            .whereEqualTo("active", true)
            .get()
            .await()
        snap.documents.mapNotNull { doc ->
            val text = doc.getString("text") ?: return@mapNotNull null
            QuoteStore.CloudQuote(
                id = doc.id,
                text = text,
                author = doc.getString("author") ?: "",
                active = doc.getBoolean("active") ?: true,
            )
        }
    }

    /**
     * يضيف المسؤول عبارة جديدة إلى `/quotes` فتظهر لكل الأجهزة عند مزامنتها.
     */
    suspend fun addQuote(text: String, author: String, uid: String): Result<String> = runCatching {
        require(text.isNotBlank()) { "نص العبارة فارغ" }
        val ref = db.collection(QUOTES_COLLECTION).document()
        ref.set(
            mapOf(
                "text" to text.trim(),
                "author" to author.trim(),
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "createdAtMillis" to System.currentTimeMillis(),
                "createdByUid" to uid,
            )
        ).await()
        ref.id
    }

    /** يحذف المسؤول عبارة (إلغاء تفعيلها بالكامل). */
    suspend fun deleteQuote(quoteId: String): Result<Unit> = runCatching {
        db.collection(QUOTES_COLLECTION).document(quoteId).delete().await()
        Unit
    }

    // ---------------------------------------------------------------- PROGRESS

    private fun progressDoc(uid: String) =
        db.collection("users").document(uid).collection("progress").document("state")

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

    suspend fun pullProgress(uid: String): Result<String?> = runCatching {
        val doc = progressDoc(uid).get().await()
        doc.getString("json")
    }

    suspend fun pullProgressTimestamp(uid: String): Result<Long> = runCatching {
        val doc = progressDoc(uid).get().await()
        doc.getLong("client_updated_at") ?: 0L
    }

    // ---------------------------------------------------------------- ANNOUNCEMENTS & LEADERBOARD

    /**
     * Fetch the latest active announcement to show to students.
     */
    suspend fun fetchActiveAnnouncement(): Result<Announcement?> = runCatching {
        val snap = try {
            db.collection(ANNOUNCEMENTS_COLLECTION)
                .whereEqualTo("isActive", true)
                .orderBy("createdAtMillis", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
        } catch (e: Exception) {
            // Fallback without ordering in case compound index is building
            db.collection(ANNOUNCEMENTS_COLLECTION)
                .whereEqualTo("isActive", true)
                .limit(10)
                .get()
                .await()
        }

        val doc = snap.documents
            .sortedByDescending { it.getLong("createdAtMillis") ?: 0L }
            .firstOrNull() ?: return@runCatching null

        Announcement(
            id = doc.id,
            title = doc.getString("title") ?: "",
            message = doc.getString("message") ?: "",
            type = doc.getString("type") ?: "info",
            createdAtMillis = doc.getLong("createdAtMillis") ?: 0L,
            isActive = doc.getBoolean("isActive") ?: true,
        )
    }

    /**
     * Post a new announcement across all devices (Admin only).
     */
    suspend fun postAnnouncement(title: String, message: String, type: String = "info"): Result<String> = runCatching {
        require(title.isNotBlank()) { "عنوان الإعلان فارغ" }
        require(message.isNotBlank()) { "نص الإعلان فارغ" }
        val docRef = db.collection(ANNOUNCEMENTS_COLLECTION).document()
        val data = mapOf(
            "id" to docRef.id,
            "title" to title.trim(),
            "message" to message.trim(),
            "type" to type,
            "createdAtMillis" to System.currentTimeMillis(),
            "createdAt" to FieldValue.serverTimestamp(),
            "isActive" to true,
        )
        docRef.set(data).await()
        docRef.id
    }

    /**
     * Deactivate or delete an announcement (Admin only).
     */
    suspend fun deactivateAnnouncement(id: String): Result<Unit> = runCatching {
        db.collection(ANNOUNCEMENTS_COLLECTION).document(id).update("isActive", false).await()
        Unit
    }

    /**
     * فحص حيّ لصلاحية النشر: يكتب مستند اختبار داخل `/announcements` ثم يحذفه.
     *
     * لماذا؟ لأن «البث لا يعمل» له ثلاثة أسباب مختلفة تماماً (لا يوجد حساب،
     * القواعد لم تُنشر، الحساب ليس مسؤولاً) والرسالة الخام وحدها لا تميّزها.
     * المستند يُكتب بـ `isActive = false` فلا يراه أي طالب إطلاقاً، ويُمسح
     * فوراً بعد الفحص.
     */
    suspend fun probePublishPermission(): Result<String> = runCatching {
        val ref = db.collection(ANNOUNCEMENTS_COLLECTION).document(PERMISSION_PROBE_ID)
        ref.set(
            mapOf(
                "id" to PERMISSION_PROBE_ID,
                "title" to "فحص الصلاحية",
                "message" to "مستند اختبار يُحذف تلقائياً",
                "type" to "info",
                "isActive" to false,
                "isProbe" to true,
                "createdAtMillis" to System.currentTimeMillis(),
            )
        ).await()
        runCatching { ref.delete().await() }
        PERMISSION_PROBE_ID
    }

    private const val PERMISSION_PROBE_ID = "__permission_probe__"

    /**
     * Fetch the global leaderboard from `/leaderboard` — a public mirror of
     * each learner's stats that deliberately contains NO email and NO role.
     * (Reading `/users` directly is admin-only under the Firestore rules.)
     */
    suspend fun fetchLeaderboard(limit: Int = 30): Result<List<UserRecord>> = runCatching {
        val snap = try {
            db.collection(LEADERBOARD_COLLECTION)
                .orderBy("xp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
        } catch (e: Exception) {
            db.collection(LEADERBOARD_COLLECTION)
                .limit((limit * 2).toLong())
                .get()
                .await()
        }

        snap.documents.mapNotNull { doc ->
            UserRecord(
                uid = doc.getString("uid") ?: doc.id,
                email = null,
                displayName = doc.getString("displayName") ?: "متعلم",
                photoUrl = doc.getString("photoUrl"),
                role = "student",
                streak = (doc.getLong("streak") ?: 0L).toInt(),
                xp = (doc.getLong("xp") ?: 0L).toInt(),
                completedLessonsCount = (doc.getLong("completedLessonsCount") ?: 0L).toInt(),
                wordsLearnedCount = (doc.getLong("wordsLearnedCount") ?: 0L).toInt(),
                accuracy = doc.getDouble("accuracy") ?: 0.0,
                lastActiveMillis = doc.getLong("lastActiveMillis") ?: 0L,
                deviceModel = null,
            )
        }.sortedByDescending { it.xp }
    }
}
