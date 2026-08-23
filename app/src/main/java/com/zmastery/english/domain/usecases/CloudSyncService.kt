package com.zmastery.english.domain.usecases

/**
 * Cloud Sync Use Case — manages cloud synchronization logic.
 *
 * Pure logic extracted from AppViewModel — no Firebase dependencies.
 * The ViewModel handles actual Firebase calls; this class provides the rules.
 */
class CloudSyncService {

    // ─── Conflict resolution ───

    /**
     * Resolve a conflict between local and cloud data.
     *
     * Strategy: the side with more lessons wins. If equal,
     * the more recently modified side wins.
     *
     * @param localLessonCount number of lessons on the device
     * @param cloudLessonCount number of lessons in the cloud
     * @param localModifiedMs last modification time locally
     * @param cloudModifiedMs last modification time in the cloud
     * @return which side should be kept
     */
    fun resolveConflict(
        localLessonCount: Int,
        cloudLessonCount: Int,
        localModifiedMs: Long,
        cloudModifiedMs: Long,
    ): ConflictResolution {
        return when {
            localLessonCount > cloudLessonCount ->
                ConflictResolution.KEEP_LOCAL
            cloudLessonCount > localLessonCount ->
                ConflictResolution.KEEP_CLOUD
            localModifiedMs > cloudModifiedMs ->
                ConflictResolution.KEEP_LOCAL
            cloudModifiedMs > localModifiedMs ->
                ConflictResolution.KEEP_CLOUD
            else ->
                ConflictResolution.KEEP_LOCAL  // Default: local wins on tie
        }
    }

    // ─── Sync state ───

    /**
     * Determine what sync action to take based on current state.
     */
    fun determineSyncAction(
        cloudEnabled: Boolean,
        hasCloudAccount: Boolean,
        lastSyncMillis: Long,
        localModifiedMs: Long,
        cloudModifiedMs: Long,
    ): SyncAction {
        if (!cloudEnabled) return SyncAction.DISABLED
        if (!hasCloudAccount) return SyncAction.NO_ACCOUNT

        val timeSinceSync = System.currentTimeMillis() - lastSyncMillis
        val hasLocalChanges = localModifiedMs > lastSyncMillis
        val hasCloudChanges = cloudModifiedMs > lastSyncMillis

        return when {
            hasLocalChanges && hasCloudChanges -> SyncAction.BIDIRECTIONAL
            hasLocalChanges -> SyncAction.PUSH_ONLY
            hasCloudChanges -> SyncAction.PULL_ONLY
            timeSinceSync > SYNC_INTERVAL_MS -> SyncAction.PULL_ONLY  // Periodic check
            else -> SyncAction.UP_TO_DATE
        }
    }

    // ─── Merge strategy ───

    /**
     * Merge two lists of lesson IDs, keeping all unique IDs.
     * Used for merging local and cloud lesson sets.
     */
    fun mergeLessonIds(localIds: Set<Int>, cloudIds: Set<Int>): Set<Int> {
        return localIds + cloudIds
    }

    /**
     * Calculate sync progress message for the UI.
     */
    fun syncProgressMessage(
        phase: SyncPhase,
        newLessons: Int,
        updatedWords: Int,
    ): String = when (phase) {
        SyncPhase.CONNECTING -> "جارٍ الاتصال بالسحابة…"
        SyncPhase.PULLING -> "جارٍ سحب الدروس الجديدة…"
        SyncPhase.PUSHING -> "جارٍ رفع تقدمك…"
        SyncPhase.MERGING -> "جارٍ دمج البيانات…"
        SyncPhase.DONE -> when {
            newLessons > 0 && updatedWords > 0 ->
                "تم ✓ $newLessons درس جديد · $updatedWords كلمة محدثة"
            newLessons > 0 ->
                "تم ✓ $newLessons درس جديد"
            updatedWords > 0 ->
                "تم ✓ $updatedWords كلمة محدثة"
            else -> "كل شيء محدّث ✓"
        }
        SyncPhase.ERROR -> "فشل المزامنة — تحقق من الاتصال"
    }

    companion object {
        /** Auto-sync interval: 15 minutes. */
        const val SYNC_INTERVAL_MS = 15 * 60 * 1000L
    }
}

enum class ConflictResolution {
    KEEP_LOCAL,
    KEEP_CLOUD,
    MERGE,
}

enum class SyncAction {
    DISABLED,
    NO_ACCOUNT,
    PUSH_ONLY,
    PULL_ONLY,
    BIDIRECTIONAL,
    UP_TO_DATE,
}

enum class SyncPhase {
    CONNECTING,
    PULLING,
    PUSHING,
    MERGING,
    DONE,
    ERROR,
}
