package com.ziaee.frenchreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualDictionaryTest {
    @Test
    fun manualDictionaryWordTrimsAValidLookup() {
        assertEquals("bonjour", manualDictionaryWord("  bonjour  "))
    }

    @Test
    fun manualDictionaryWordRejectsBlankLookup() {
        assertNull(manualDictionaryWord("   "))
    }
}
