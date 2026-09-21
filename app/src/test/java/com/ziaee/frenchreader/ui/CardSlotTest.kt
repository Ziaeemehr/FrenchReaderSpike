package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CardSlotTest {
    private val a = VocabEntry(id = 1, word = "w", sentence = "s", textId = 0, dictionaryUrl = "")

    @Test fun `data class copy is equal but slots are not`() {
        assertEquals(a, a.copy())
        assertNotEquals(CardSlot(a), CardSlot(a.copy()))
        val s = CardSlot(a)
        assertEquals(s, s)
        assertSame(a, s.entry)
    }
}
