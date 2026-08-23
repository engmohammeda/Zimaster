package com.zmastery.english

import com.zmastery.english.data.AppState
import com.zmastery.english.data.ProfileDto
import com.zmastery.english.domain.usecases.BackupCoordinator
import org.junit.Assert.*
import org.junit.Test

class BackupCoordinatorTest {

    private val coordinator = BackupCoordinator()

    @Test
    fun `detect full backup type`() {
        val raw = """{"magic":"ZMASTERY_BACKUP","formatVersion":3,"state":{}}"""
        assertEquals("full", coordinator.detectBackupType(raw))
    }

    @Test
    fun `detect lessons backup type`() {
        val raw = """{"magic":"ZMASTERY_LESSONS","courses":[],"lessons":[]}"""
        assertEquals("lessons", coordinator.detectBackupType(raw))
    }

    @Test
    fun `detect words backup type`() {
        val raw = """{"magic":"ZMASTERY_WORDS","words":[]}"""
        assertEquals("words", coordinator.detectBackupType(raw))
    }

    @Test
    fun `detect unknown type`() {
        val raw = """{"random":"data"}"""
        assertEquals("unknown", coordinator.detectBackupType(raw))
    }

    @Test
    fun `export and validate full backup`() {
        val state = AppState(profile = ProfileDto(learnerName = "Test"))
        val exported = coordinator.exportFull(state, "2026-08-23")
        val validation = coordinator.validateBackup(exported)
        assertTrue("Should be valid: ${validation.error}", validation.valid)
        assertEquals("full", validation.type)
        assertTrue(validation.summary.contains("Test"))
    }

    @Test
    fun `validate invalid backup fails gracefully`() {
        val validation = coordinator.validateBackup("{invalid json")
        assertFalse(validation.valid)
    }

    @Test
    fun `validate empty backup reports unknown type`() {
        val validation = coordinator.validateBackup("{}")
        assertEquals("unknown", validation.type)
    }
}
