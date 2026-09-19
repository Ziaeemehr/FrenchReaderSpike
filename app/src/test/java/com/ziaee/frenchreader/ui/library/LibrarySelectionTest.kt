package com.ziaee.frenchreader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySelectionTest {
    @Test
    fun toggleAddsAndRemovesAnId() {
        assertEquals(setOf(1L, 2L), toggleSelection(setOf(1L), 2L))
        assertEquals(setOf(1L), toggleSelection(setOf(1L, 2L), 2L))
    }

    @Test
    fun selectAllVisiblePreservesHiddenSelectionAndTogglesVisibleIds() {
        assertEquals(setOf(1L, 2L, 3L), selectAllVisible(setOf(1L), setOf(2L, 3L)))
        assertEquals(setOf(1L), selectAllVisible(setOf(1L, 2L, 3L), setOf(2L, 3L)))
    }

    @Test
    fun pruneRemovesIdsThatNoLongerExist() {
        assertEquals(setOf(2L), prunedSelection(setOf(1L, 2L), setOf(2L, 3L)))
    }
}
