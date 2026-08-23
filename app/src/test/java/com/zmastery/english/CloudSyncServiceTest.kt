package com.zmastery.english

import com.zmastery.english.domain.usecases.CloudSyncService
import com.zmastery.english.domain.usecases.ConflictResolution
import com.zmastery.english.domain.usecases.SyncAction
import com.zmastery.english.domain.usecases.SyncPhase
import org.junit.Assert.*
import org.junit.Test

class CloudSyncServiceTest {

    private val service = CloudSyncService()

    // ─── Conflict resolution ───

    @Test
    fun `keep local when local has more lessons`() {
        val result = service.resolveConflict(10, 5, 100L, 200L)
        assertEquals(ConflictResolution.KEEP_LOCAL, result)
    }

    @Test
    fun `keep cloud when cloud has more lessons`() {
        val result = service.resolveConflict(5, 10, 100L, 200L)
        assertEquals(ConflictResolution.KEEP_CLOUD, result)
    }

    @Test
    fun `keep local on tie when local is newer`() {
        val result = service.resolveConflict(5, 5, 200L, 100L)
        assertEquals(ConflictResolution.KEEP_LOCAL, result)
    }

    @Test
    fun `keep cloud on tie when cloud is newer`() {
        val result = service.resolveConflict(5, 5, 100L, 200L)
        assertEquals(ConflictResolution.KEEP_CLOUD, result)
    }

    @Test
    fun `keep local on perfect tie`() {
        val result = service.resolveConflict(5, 5, 100L, 100L)
        assertEquals(ConflictResolution.KEEP_LOCAL, result)
    }

    // ─── Sync action determination ───

    @Test
    fun `disabled when cloud is off`() {
        val action = service.determineSyncAction(false, true, 100L, 200L, 100L)
        assertEquals(SyncAction.DISABLED, action)
    }

    @Test
    fun `no account when not signed in`() {
        val action = service.determineSyncAction(true, false, 100L, 200L, 100L)
        assertEquals(SyncAction.NO_ACCOUNT, action)
    }

    @Test
    fun `push when only local has changes`() {
        val now = System.currentTimeMillis()
        val action = service.determineSyncAction(true, true, now - 1000, now, now - 5000)
        assertEquals(SyncAction.PUSH_ONLY, action)
    }

    @Test
    fun `pull when only cloud has changes`() {
        val now = System.currentTimeMillis()
        val action = service.determineSyncAction(true, true, now - 1000, now - 5000, now)
        assertEquals(SyncAction.PULL_ONLY, action)
    }

    @Test
    fun `bidirectional when both changed`() {
        val now = System.currentTimeMillis()
        val action = service.determineSyncAction(true, true, now - 5000, now, now)
        assertEquals(SyncAction.BIDIRECTIONAL, action)
    }

    // ─── Merge ───

    @Test
    fun `merge lesson IDs keeps all unique`() {
        val local = setOf(1, 2, 3)
        val cloud = setOf(3, 4, 5)
        val merged = service.mergeLessonIds(local, cloud)
        assertEquals(setOf(1, 2, 3, 4, 5), merged)
    }

    @Test
    fun `merge empty sets`() {
        assertEquals(emptySet<Int>(), service.mergeLessonIds(emptySet(), emptySet()))
    }

    // ─── Progress messages ───

    @Test
    fun `connecting message`() {
        val msg = service.syncProgressMessage(SyncPhase.CONNECTING, 0, 0)
        assertTrue(msg.contains("الاتصال"))
    }

    @Test
    fun `done message with new lessons`() {
        val msg = service.syncProgressMessage(SyncPhase.DONE, 3, 0)
        assertTrue(msg.contains("3"))
        assertTrue(msg.contains("درس"))
    }

    @Test
    fun `done message with updated words`() {
        val msg = service.syncProgressMessage(SyncPhase.DONE, 0, 10)
        assertTrue(msg.contains("10"))
        assertTrue(msg.contains("كلمة"))
    }

    @Test
    fun `up to date message`() {
        val msg = service.syncProgressMessage(SyncPhase.DONE, 0, 0)
        assertTrue(msg.contains("محدّث"))
    }

    @Test
    fun `error message`() {
        val msg = service.syncProgressMessage(SyncPhase.ERROR, 0, 0)
        assertTrue(msg.contains("فشل"))
    }
}
