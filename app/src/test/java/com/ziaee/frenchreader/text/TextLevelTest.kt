package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextLevelTest {
    @Test fun normalizesOnlyCefrLevels() {
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), TEXT_LEVELS)
        TEXT_LEVELS.forEach { assertEquals(it, normalizeTextLevel(" ${it.lowercase()} ")) }
        listOf(null, "", "Divers", "A3", "A1 stories", "B2/C1").forEach {
            assertNull(normalizeTextLevel(it))
        }
    }

    @Test fun nearestExactFolderNameWins() {
        assertEquals("A1", levelFromFolderNames(listOf("Histoires graduées", " a1 ")))
        assertEquals("B2", levelFromFolderNames(listOf("A1", " b2 ", "Stories")))
        assertEquals("C1", levelFromFolderNames(listOf("C1", "Divers")))
        assertNull(levelFromFolderNames(listOf("Histoires graduées", "Divers", "A2 stories")))
        assertNull(levelFromFolderNames(emptyList()))
    }
}
