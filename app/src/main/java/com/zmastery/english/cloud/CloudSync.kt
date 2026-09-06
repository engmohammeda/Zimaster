package com.zmastery.english.cloud

import android.os.Build
import com.zmastery.english.data.ImportEngine
import com.zmastery.english.data.LessonPackage
import com.zmastery.english.data.QuoteStore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Cloud content, user provisioning, progress sync, and realtime updates built on Supabase.
 */
object CloudSync {

    private val supabase: SupabaseClient get() = SupabaseClientProvider.client

    private const val LESSONS_TABLE = "lessons"
    private const val PROFILES_TABLE = "profiles"
    private const val ANNOUNCEMENTS_TABLE = "announcements"
    private const val QUOTES_TABLE = "quotes"
    private const val LEADERBOARD_VIEW = "leaderboard"
    private const val USER_PROGRESS_TABLE = "user_progress"
    private const val USER_ROLES_TABLE = "user_roles"
    private const val PERMISSION_PROBE_ID = "__permission_probe__"

    @Serializable
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

    @Serializable
    private data class ProfileRow(
        val user_id: String,
        val email: String? = null,
        val display_name: String = "Learner",
        val photo_url: String? = null,
        val is_anonymous: Boolean = false,
        val streak: Int = 0,
        val xp: Int = 0,
        val completed_lessons_count: Int = 0,
        val words_learned_count: Int = 0,
        val accuracy: Double = 0.0,
        val last_active_millis: Long = 0L,
        val device_model: String? = null,
        val android_version: String? = null,
        val app_version: String? = null,
        val platform: String? = "android",
        val role: String = "student",
        val created_at_millis: Long = 0L,
    )

    @Serializable
    private data class LeaderboardRow(
        val uid: String,
        val display_name: String = "Learner",
        val photo_url: String? = null,
        val streak: Int = 0,
        val xp: Int = 0,
        val completed_lessons_count: Int = 0,
        val words_learned_count: Int = 0,
        val accuracy: Double = 0.0,
        val last_active_millis: Long = 0L,
    )

    @Serializable
    private data class LessonRow(
        val doc_id: String,
        val course_id: String,
        val lesson_no: Int,
        val title: String,
        val level: String,
        val json: String,
        val updated_at: Long,
    )

    @Serializable
    private data class LessonIndexRow(
        val doc_id: String,
        val updated_at: Long,
    )

    @Serializable
    private data class UserProgressRow(
        val user_id: String,
        val payload: JsonElement,
        val client_ts: Long = 0L,
    )

    @Serializable
    private data class QuoteRow(
        val id: String,
        val text: String,
        val author: String = "",
        val is_active: Boolean = true,
        val created_at_millis: Long = 0L,
        val created_by_uid: String? = null,
    )

    @Serializable
    private data class AnnouncementRow(
        val id: String,
        val title: String,
        val message: String,
        val type: String = "info",
        val created_at_millis: Long = 0L,
        val is_active: Boolean = true,
        val is_probe: Boolean = false,
    )

    @Serializable
    private data class UserRoleRow(
        val user_id: String,
        val role: String,
    )

    /**
     * Auto-provision or update the user document in `profiles` table in Supabase.
     */
    suspend fun provisionOrUpdateUser(
        user: CloudUser,
        profile: UserProfileSnapshot,
    ): Result<String> = runCatching {
        val now = System.currentTimeMillis()

        // Fetch current role from user_roles or RPC is_admin
        val isAdmin = runCatching {
            supabase.postgrest.rpc("is_admin").decodeAs<Boolean>()
        }.getOrDefault(false)

        val currentRole = if (isAdmin) "admin" else {
            runCatching {
                supabase.from(USER_ROLES_TABLE).select {
                    filter { eq("user_id", user.uid) }
                }.decodeSingleOrNull<UserRoleRow>()?.role
            }.getOrNull() ?: "student"
        }

        val row = ProfileRow(
            user_id = user.uid,
            email = user.email,
            display_name = user.displayName ?: profile.displayName ?: "Learner",
            photo_url = user.photoUrl ?: profile.photoUrl,
            is_anonymous = user.isAnonymous,
            streak = profile.streak,
            xp = profile.xp,
            completed_lessons_count = profile.completedLessonsCount,
            words_learned_count = profile.wordsLearnedCount,
            accuracy = profile.accuracy,
            last_active_millis = now,
            device_model = "${Build.MANUFACTURER} ${Build.MODEL}",
            android_version = Build.VERSION.RELEASE,
            app_version = com.zmastery.english.BuildConfig.VERSION_NAME,
            platform = "android",
            role = currentRole,
            created_at_millis = now,
        )

        supabase.from(PROFILES_TABLE).upsert(row)
        currentRole
    }

    /**
     * Fetch the user's role from Supabase ("admin" or "student")
     */
    suspend fun fetchUserRole(uid: String): String = runCatching {
        val roleRow = supabase.from(USER_ROLES_TABLE).select {
            filter { eq("user_id", uid) }
        }.decodeSingleOrNull<UserRoleRow>()
        roleRow?.role ?: "student"
    }.getOrDefault("student")

    /**
     * Distinguishes "no-doc" from "admin" / "student" for developer UI diagnostics.
     */
    suspend fun fetchRoleDoc(uid: String): Result<String> = runCatching {
        val profile = supabase.from(PROFILES_TABLE).select {
            filter { eq("user_id", uid) }
        }.decodeSingleOrNull<ProfileRow>()
        if (profile == null) "no-doc" else profile.role
    }

    /**
     * Fetch all registered users (for admin dashboard).
     */
    suspend fun fetchAllUsers(): Result<List<UserRecord>> = runCatching {
        val rows = try {
            supabase.from(PROFILES_TABLE).select {
                order("last_active_millis", Order.DESCENDING)
                limit(100)
            }.decodeList<ProfileRow>()
        } catch (e: Exception) {
            supabase.from(PROFILES_TABLE).select {
                limit(100)
            }.decodeList<ProfileRow>()
        }

        rows.map { doc ->
            UserRecord(
                uid = doc.user_id,
                email = doc.email,
                displayName = doc.display_name,
                photoUrl = doc.photo_url,
                role = doc.role,
                streak = doc.streak,
                xp = doc.xp,
                completedLessonsCount = doc.completed_lessons_count,
                wordsLearnedCount = doc.words_learned_count,
                accuracy = doc.accuracy,
                lastActiveMillis = doc.last_active_millis,
                deviceModel = doc.device_model,
            )
        }.sortedByDescending { it.lastActiveMillis }
    }

    // ---------------------------------------------------------------- LESSONS

    data class RemoteLesson(val docId: String, val json: String, val updatedAtMillis: Long)

    /**
     * Fetch every lesson document added/changed since [sinceMillis]
     */
    suspend fun fetchLessonsSince(sinceMillis: Long): Result<List<RemoteLesson>> = runCatching {
        val rows = if (sinceMillis > 0L) {
            supabase.from(LESSONS_TABLE).select {
                filter { gt("updated_at", sinceMillis) }
                order("updated_at", Order.ASCENDING)
            }.decodeList<LessonRow>()
        } else {
            supabase.from(LESSONS_TABLE).select {
                order("updated_at", Order.ASCENDING)
            }.decodeList<LessonRow>()
        }

        rows.map { RemoteLesson(it.doc_id, it.json, it.updated_at) }
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
     * Publish or update a single lesson package in Supabase under `lessons`.
     */
    suspend fun publishLessonToCloud(pkg: LessonPackage): Result<String> = runCatching {
        val courseKey = pkg.metadata.courseId.ifBlank { "l1_scratch" }
        val docId = "${courseKey}_lesson_${pkg.metadata.lessonNo}"
        val json = ImportEngine.json.encodeToString(LessonPackage.serializer(), pkg)
        val now = System.currentTimeMillis()

        val row = LessonRow(
            doc_id = docId,
            course_id = courseKey,
            lesson_no = pkg.metadata.lessonNo,
            title = pkg.metadata.title,
            level = pkg.metadata.level.toString(),
            json = json,
            updated_at = now,
        )

        supabase.from(LESSONS_TABLE).upsert(row)
        docId
    }

    /**
     * Publish a batch of lesson packages to Supabase.
     */
    suspend fun publishLessonsBatchToCloud(packages: List<LessonPackage>): Result<Int> = runCatching {
        val now = System.currentTimeMillis()
        val rows = packages.map { pkg ->
            val courseKey = pkg.metadata.courseId.ifBlank { "l1_scratch" }
            val docId = "${courseKey}_lesson_${pkg.metadata.lessonNo}"
            val json = ImportEngine.json.encodeToString(LessonPackage.serializer(), pkg)
            LessonRow(
                doc_id = docId,
                course_id = courseKey,
                lesson_no = pkg.metadata.lessonNo,
                title = pkg.metadata.title,
                level = pkg.metadata.level.toString(),
                json = json,
                updated_at = now,
            )
        }
        supabase.from(LESSONS_TABLE).upsert(rows)
        packages.size
    }

    /**
     * Delete a lesson from Supabase by docId.
     */
    suspend fun deleteLessonFromCloud(docId: String): Result<Unit> = runCatching {
        supabase.from(LESSONS_TABLE).delete {
            filter { eq("doc_id", docId) }
        }
        Unit
    }

    /**
     * Index of lessons in Supabase: docId -> updated_at.
     * Uses column projection to only download doc_id and updated_at, saving network bandwidth.
     */
    suspend fun fetchCloudLessonIndex(): Result<Map<String, Long>> = runCatching {
        val rows = supabase.from(LESSONS_TABLE)
            .select(Columns.list("doc_id", "updated_at"))
            .decodeList<LessonIndexRow>()
        rows.associate { it.doc_id to it.updated_at }
    }

    // ---------------------------------------------------------------- QUOTES

    /**
     * Pull active quotes from Supabase.
     */
    suspend fun pullQuotes(): Result<List<QuoteStore.CloudQuote>> = runCatching {
        val rows = supabase.from(QUOTES_TABLE).select {
            filter { eq("is_active", true) }
        }.decodeList<QuoteRow>()

        rows.map {
            QuoteStore.CloudQuote(
                id = it.id,
                text = it.text,
                author = it.author,
                active = it.is_active,
            )
        }
    }

    /**
     * Add quote to Supabase (Admin only).
     */
    suspend fun addQuote(text: String, author: String, uid: String): Result<String> = runCatching {
        require(text.isNotBlank()) { "نص العبارة فارغ" }
        val newId = java.util.UUID.randomUUID().toString()
        val row = QuoteRow(
            id = newId,
            text = text.trim(),
            author = author.trim(),
            is_active = true,
            created_at_millis = System.currentTimeMillis(),
            created_by_uid = uid,
        )
        supabase.from(QUOTES_TABLE).insert(row)
        newId
    }

    /**
     * Delete quote by id.
     */
    suspend fun deleteQuote(quoteId: String): Result<Unit> = runCatching {
        supabase.from(QUOTES_TABLE).delete {
            filter { eq("id", quoteId) }
        }
        Unit
    }

    // ---------------------------------------------------------------- PROGRESS

    /**
     * Push user progress to Supabase via `push_progress` RPC.
     */
    suspend fun pushProgress(uid: String, stateJson: String): Result<Unit> = runCatching {
        val jsonElement = Json.parseToJsonElement(stateJson)
        val clientTs = System.currentTimeMillis()
        val params = buildJsonObject {
            put("payload", jsonElement)
            put("client_ts", clientTs)
        }
        supabase.postgrest.rpc("push_progress", params)
        Unit
    }

    /**
     * Pull user progress state from Supabase.
     */
    suspend fun pullProgress(uid: String): Result<String?> = runCatching {
        val row = supabase.from(USER_PROGRESS_TABLE).select {
            filter { eq("user_id", uid) }
        }.decodeSingleOrNull<UserProgressRow>()
        row?.payload?.toString()
    }

    /**
     * Pull timestamp of user progress state.
     */
    suspend fun pullProgressTimestamp(uid: String): Result<Long> = runCatching {
        val row = supabase.from(USER_PROGRESS_TABLE).select {
            filter { eq("user_id", uid) }
        }.decodeSingleOrNull<UserProgressRow>()
        row?.client_ts ?: 0L
    }

    /**
     * Realtime subscription for user progress state updates.
     */
    suspend fun subscribeToRealtimeProgress(uid: String, onUpdate: (String) -> Unit): RealtimeChannel {
        val channel = supabase.realtime.channel("user_progress:$uid")
        channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = USER_PROGRESS_TABLE
            filter("user_id", FilterOperator.EQ, uid)
        }.onEach { action ->
            if (action is PostgresAction.Update || action is PostgresAction.Insert) {
                val progress = pullProgress(uid).getOrNull()
                if (progress != null) onUpdate(progress)
            }
        }.launchIn(CoroutineScope(Dispatchers.IO))
        channel.subscribe()
        return channel
    }

    // ---------------------------------------------------------------- ANNOUNCEMENTS & LEADERBOARD

    /**
     * Fetch the latest active announcement.
     */
    suspend fun fetchActiveAnnouncement(): Result<Announcement?> = runCatching {
        val rows = supabase.from(ANNOUNCEMENTS_TABLE).select {
            filter {
                eq("is_active", true)
                eq("is_probe", false)
            }
            order("created_at_millis", Order.DESCENDING)
            limit(1)
        }.decodeList<AnnouncementRow>()

        rows.firstOrNull()?.let {
            Announcement(
                id = it.id,
                title = it.title,
                message = it.message,
                type = it.type,
                createdAtMillis = it.created_at_millis,
                isActive = it.is_active,
            )
        }
    }

    /**
     * Post a new announcement across all devices (Admin only).
     */
    suspend fun postAnnouncement(title: String, message: String, type: String = "info"): Result<String> = runCatching {
        require(title.isNotBlank()) { "عنوان الإعلان فارغ" }
        require(message.isNotBlank()) { "نص الإعلان فارغ" }
        val newId = java.util.UUID.randomUUID().toString()
        val row = AnnouncementRow(
            id = newId,
            title = title.trim(),
            message = message.trim(),
            type = type,
            created_at_millis = System.currentTimeMillis(),
            is_active = true,
            is_probe = false,
        )
        supabase.from(ANNOUNCEMENTS_TABLE).insert(row)
        newId
    }

    /**
     * Deactivate an announcement (Admin only).
     */
    suspend fun deactivateAnnouncement(id: String): Result<Unit> = runCatching {
        supabase.from(ANNOUNCEMENTS_TABLE).update(buildJsonObject {
            put("is_active", false)
        }) {
            filter { eq("id", id) }
        }
        Unit
    }

    /**
     * Probes publish permission by testing `can_publish()` RPC or writing a probe announcement.
     */
    suspend fun probePublishPermission(): Result<String> = runCatching {
        // First try the security definer function
        val canPublish = runCatching {
            supabase.postgrest.rpc("can_publish").decodeAs<Boolean>()
        }.getOrNull()

        if (canPublish == true) {
            return@runCatching PERMISSION_PROBE_ID
        }

        // Fallback: write and delete temporary probe row
        val probeRow = AnnouncementRow(
            id = PERMISSION_PROBE_ID,
            title = "فحص الصلاحية",
            message = "مستند اختبار يُحذف تلقائياً",
            type = "info",
            created_at_millis = System.currentTimeMillis(),
            is_active = false,
            is_probe = true,
        )
        supabase.from(ANNOUNCEMENTS_TABLE).upsert(probeRow)
        runCatching {
            supabase.from(ANNOUNCEMENTS_TABLE).delete {
                filter { eq("id", PERMISSION_PROBE_ID) }
            }
        }
        PERMISSION_PROBE_ID
    }

    /**
     * Fetch the global leaderboard from `leaderboard` VIEW.
     * The VIEW deliberately excludes email and role to protect privacy.
     */
    suspend fun fetchLeaderboard(limit: Int = 30): Result<List<UserRecord>> = runCatching {
        val rows = try {
            supabase.from(LEADERBOARD_VIEW).select {
                order("xp", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<LeaderboardRow>()
        } catch (e: Exception) {
            supabase.from(LEADERBOARD_VIEW).select {
                limit((limit * 2).toLong())
            }.decodeList<LeaderboardRow>()
        }

        rows.map { row ->
            UserRecord(
                uid = row.uid,
                email = null,
                displayName = row.display_name,
                photoUrl = row.photo_url,
                role = "student",
                streak = row.streak,
                xp = row.xp,
                completedLessonsCount = row.completed_lessons_count,
                wordsLearnedCount = row.words_learned_count,
                accuracy = row.accuracy,
                lastActiveMillis = row.last_active_millis,
                deviceModel = null,
            )
        }.sortedByDescending { it.xp }
    }
}
