package io.github.cmix7777.kazhdyidnevnik.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class BackupTest {

    private val done = mapOf(
        "2026-10-08|PDD" to 20,
        "2026-10-08|QA" to 45,
    )
    private val settings = ReminderSettings(morningTime = LocalTime.of(7, 30), blocks = false)

    @Test
    fun roundTrip() {
        val text = Backup.encode(done, settings, LocalDateTime.of(2026, 10, 8, 21, 0, 15, 123))
        val restored = requireNotNull(Backup.decode(text))
        assertEquals(done, restored.done)
        assertEquals(settings, restored.settings)
    }

    @Test
    fun rejectsForeignFiles() {
        assertNull(Backup.decode("""{"app":"other","format":1,"createdAt":"x","done":{}}"""))
        assertNull(Backup.decode("просто текст"))
    }

    @Test
    fun brokenSettingsDoNotBreakProgress() {
        val text = """
            {"app":"kazhdyidnevnik","format":1,"createdAt":"2026-10-08T21:00:00",
             "done":{"2026-10-08|PDD":20},
             "settings":{"changes":true,"morning":true,"morningTime":"утром","blocks":true,"evening":true,"eveningTime":"22:15"}}
        """.trimIndent()
        val restored = requireNotNull(Backup.decode(text))
        assertEquals(mapOf("2026-10-08|PDD" to 20), restored.done)
        assertNull(restored.settings)
    }

    @Test
    fun mergeKeepsEverything() {
        val current = mapOf("a" to 10, "b" to 30)
        val incoming = mapOf("b" to 25, "c" to 40)
        assertEquals(mapOf("a" to 10, "b" to 30, "c" to 40), Backup.merge(current, incoming))
    }
}
