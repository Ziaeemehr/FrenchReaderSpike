package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.TextDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    private val docA = TextDocument(id = 1L, title = "Zebra", rawText = "", createdAtMs = 100L, sourceName = "RFI", lastAccessedAtMs = 500L)
    private val docB = TextDocument(id = 2L, title = "apple", rawText = "", createdAtMs = 300L, sourceName = "France Info", lastAccessedAtMs = 0L)
    private val docC = TextDocument(id = 3L, title = "Mango", rawText = "", createdAtMs = 200L, sourceName = null, lastAccessedAtMs = 700L)
    private val documents = listOf(docA, docB, docC)

    @Test
    fun `filters case-insensitively by title or source name`() {
        assertEquals(listOf(docA.id), projectLibrary(documents, "zeb", LibrarySort.NEWEST).map { it.id })
        assertEquals(listOf(docB.id), projectLibrary(documents, "FRANCE", LibrarySort.NEWEST).map { it.id })
        assertEquals(emptyList<Long>(), projectLibrary(documents, "nonexistent", LibrarySort.NEWEST).map { it.id })
    }

    @Test
    fun `sorts newest first by creation time`() {
        assertEquals(listOf(docB.id, docC.id, docA.id), projectLibrary(documents, "", LibrarySort.NEWEST).map { it.id })
    }

    @Test
    fun `sorts by title case-insensitively`() {
        assertEquals(listOf(docB.id, docC.id, docA.id), projectLibrary(documents, "", LibrarySort.TITLE).map { it.id })
    }

    @Test
    fun `sorts by most recently read, with never-read documents last`() {
        assertEquals(listOf(docC.id, docA.id, docB.id), projectLibrary(documents, "", LibrarySort.LAST_READ).map { it.id })
    }
}
