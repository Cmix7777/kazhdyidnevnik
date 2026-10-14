package io.github.cmix7777.kazhdyidnevnik.data

import io.github.cmix7777.kazhdyidnevnik.notify.PartnerHeard
import io.github.cmix7777.kazhdyidnevnik.service.PartnerSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerNoteTest {

    private val stamp: (Long) -> String = { "13 окт, 21:40" }

    @Test
    fun saysWhenPartnerLastShared() {
        val info = PartnerSync.Info(paired = true, partnerState = PartnerHeard.OK, partnerMillis = 1L)
        assertEquals("Последние дела от Насти: 13 окт, 21:40.", PartnerSync.partnerNote(info, Person.NASTYA, stamp))
    }

    @Test
    fun explainsWhenNothingCameForThisCode() {
        val info = PartnerSync.Info(paired = true, partnerState = PartnerHeard.NONE)
        val note = PartnerSync.partnerNote(info, Person.AIZAT, stamp)!!
        assertTrue(note, note.startsWith("От Айзата по этому коду пока ничего не приходило"))
        assertTrue(note, note.contains("Айзат пусть вставит"))
    }

    @Test
    fun unreadableAndUnknown() {
        val unreadable = PartnerSync.Info(paired = true, partnerState = PartnerHeard.UNREADABLE)
        assertTrue(PartnerSync.partnerNote(unreadable, Person.NASTYA, stamp)!!.contains("старая версия"))
        assertNull(PartnerSync.partnerNote(PartnerSync.Info(paired = true), Person.NASTYA, stamp))
    }
}
