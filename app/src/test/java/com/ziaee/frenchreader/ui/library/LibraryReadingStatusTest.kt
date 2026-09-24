package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.TextDocument
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryReadingStatusTest {
    @Test
    fun `untouched document is not started`() {
        val document = TextDocument(title = "New", rawText = "Texte")

        assertEquals(ReadingStatus.NOT_STARTED, readingStatus(document, completed = false))
    }

    @Test
    fun `opened document is in progress`() {
        val document = TextDocument(title = "Started", rawText = "Texte", lastAccessedAtMs = 1L)

        assertEquals(ReadingStatus.IN_PROGRESS, readingStatus(document, completed = false))
    }

    @Test
    fun `saved playback position is in progress even without access timestamp`() {
        val document = TextDocument(title = "Started", rawText = "Texte", lastPositionMs = 1L)

        assertEquals(ReadingStatus.IN_PROGRESS, readingStatus(document, completed = false))
    }

    @Test
    fun `completed document is read`() {
        val document = TextDocument(title = "Done", rawText = "Texte", lastAccessedAtMs = 1L)

        assertEquals(ReadingStatus.READ, readingStatus(document, completed = true))
    }

    @Test
    fun `untouched document stays not started even when completion input says true`() {
        val document = TextDocument(title = "New", rawText = "Texte")

        assertEquals(ReadingStatus.NOT_STARTED, readingStatus(document, completed = true))
    }

    @Test
    fun `completion cache skips body reads for untouched documents and reuses unchanged results`() = runTest {
        val cache = LibraryCompletionCache()
        val untouched = TextDocument(id = 1L, title = "New", rawText = "One paragraph")
        val started = TextDocument(
            id = 2L,
            title = "Started",
            rawText = "First paragraph.\n\nSecond paragraph.",
            lastAccessedAtMs = 1L
        )
        var reads = 0
        val readBody: suspend (TextDocument) -> String = { document ->
            reads++
            document.rawText
        }

        assertEquals(mapOf(1L to false, 2L to false), cache.completionFor(listOf(untouched, started), readBody))
        assertEquals(1, reads)

        assertEquals(mapOf(1L to false, 2L to false), cache.completionFor(listOf(untouched, started), readBody))
        assertEquals(1, reads)
    }

    @Test
    fun `completion cache rereads only when a completion input changes`() = runTest {
        val cache = LibraryCompletionCache()
        val document = TextDocument(
            id = 3L,
            title = "Started",
            rawText = "First paragraph.\n\nSecond paragraph.",
            lastAccessedAtMs = 1L
        )
        var reads = 0
        val readBody: suspend (TextDocument) -> String = { doc -> reads++; doc.rawText }

        cache.completionFor(listOf(document), readBody)
        cache.completionFor(listOf(document.copy(title = "Renamed", lastAccessedAtMs = 2L)), readBody)
        cache.completionFor(listOf(document.copy(lastPositionMs = 500L)), readBody)
        cache.completionFor(listOf(document.copy(lastChunkIndex = 1)), readBody)

        assertEquals(2, reads)
    }
}
