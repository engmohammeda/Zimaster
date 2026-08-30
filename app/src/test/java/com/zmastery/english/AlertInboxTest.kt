package com.zmastery.english

import com.zmastery.english.data.AlertInbox
import com.zmastery.english.data.AppAlert
import org.junit.Assert.*
import org.junit.Test

class AlertInboxTest {

    private fun alert(
        title: String = "نفدت حصة الصوت",
        source: String = "صوت",
        at: Long = 1_000L,
        id: String = "a$at",
    ) = AppAlert(
        id = id,
        kind = "QUOTA",
        source = source,
        title = title,
        detail = "detail",
        atMillis = at,
    )

    @Test
    fun `gemini voice ids are title-cased for the TTS API`() {
        assertEquals("Kore", AlertInbox.geminiVoiceName("kore"))
        assertEquals("Puck", AlertInbox.geminiVoiceName("puck"))
        assertEquals("Aoede", AlertInbox.geminiVoiceName("AOEDE"))
        assertEquals("Kore", AlertInbox.geminiVoiceName(""))
        assertEquals("Kore", AlertInbox.geminiVoiceName("Kore"))
    }

    @Test
    fun `duplicate title within the window replaces instead of stacking`() {
        val first = alert(at = 1_000L, id = "one")
        val second = alert(at = 5_000L, id = "two")
        val out = AlertInbox.push(listOf(first), second)
        assertEquals(1, out.size)
        assertEquals("one", out[0].id)
        assertEquals(5_000L, out[0].atMillis)
        assertFalse(out[0].read)
    }

    @Test
    fun `a different title is kept as a new row`() {
        val first = alert(title = "لا يوجد مفتاح", at = 1_000L)
        val second = alert(title = "نفدت حصة الصوت", at = 2_000L)
        val out = AlertInbox.push(listOf(first), second)
        assertEquals(2, out.size)
        assertEquals("نفدت حصة الصوت", out[0].title)
    }

    @Test
    fun `unread count ignores read rows`() {
        val rows = listOf(
            alert(id = "a", at = 3).copy(read = false),
            alert(id = "b", at = 2).copy(read = true),
        )
        assertEquals(1, AlertInbox.unreadCount(rows))
        assertEquals(0, AlertInbox.unreadCount(AlertInbox.markAllRead(rows)))
    }

    @Test
    fun `preview sample names the voice so they are distinguishable`() {
        val sample = AlertInbox.previewSample("Puck")
        assertTrue(sample.contains("Puck"))
        assertTrue(sample.contains("English"))
    }
}
