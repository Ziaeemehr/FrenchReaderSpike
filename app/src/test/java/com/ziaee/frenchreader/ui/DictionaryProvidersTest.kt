package com.ziaee.frenchreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryProvidersTest {
    @Test fun `German dictionary providers use German endpoints`() {
        val providers = dictionaryProvidersFor("de")
        assertEquals(listOf("wordreference", "bamooz", "duden", "linguee", "wiktionary", "dwds"), providers.map { it.id })
        assertTrue(providers.first().urlFor("Straße").startsWith("https://www.wordreference.com/deen/"))
    }
}
