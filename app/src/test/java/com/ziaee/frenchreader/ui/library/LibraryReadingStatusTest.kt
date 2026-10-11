package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.READ_MARK_AUTOMATIC
import com.ziaee.frenchreader.data.READ_MARK_FINISHED
import com.ziaee.frenchreader.data.READ_MARK_NOT_FINISHED
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryReadingStatusTest {
    @Test
    fun `manual marks override automatic completion and badge`() {
        val document = TextDocument(title = "Text", rawText = "Texte", lastAccessedAtMs = 1L)
        for (automatic in listOf(false, true)) {
            assertEquals(automatic, isTextFinished(document.copy(readMark = READ_MARK_AUTOMATIC), automatic))
            assertEquals(automatic, isTextFinished(document.copy(readMark = 99), automatic))
            val finished = document.copy(readMark = READ_MARK_FINISHED)
            val reading = document.copy(readMark = READ_MARK_NOT_FINISHED)
            assertEquals(true, isTextFinished(finished, automatic))
            assertEquals(false, isTextFinished(reading, automatic))
            assertEquals(ReadingStatus.READ, readingStatus(finished, automatic))
            assertEquals(ReadingStatus.IN_PROGRESS, readingStatus(reading, automatic))
        }
        val untouched = document.copy(lastAccessedAtMs = 0L)
        assertEquals(ReadingStatus.READ, readingStatus(untouched.copy(readMark = READ_MARK_FINISHED), false))
        assertEquals(ReadingStatus.NOT_STARTED, readingStatus(untouched.copy(readMark = READ_MARK_NOT_FINISHED), true))
    }

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
    fun `automatic completion input determines finished status`() {
        val document = TextDocument(title = "New", rawText = "Texte")

        assertEquals(ReadingStatus.READ, readingStatus(document, completed = true))
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

    @Test
    fun `a manual mark is stored only when it overrides the reading progress`() {
        assertEquals(READ_MARK_FINISHED, readMarkToStore(READ_MARK_FINISHED, completedAutomatically = false))
        assertEquals(READ_MARK_AUTOMATIC, readMarkToStore(READ_MARK_FINISHED, completedAutomatically = true))
        assertEquals(READ_MARK_NOT_FINISHED, readMarkToStore(READ_MARK_NOT_FINISHED, completedAutomatically = true))
        assertEquals(READ_MARK_AUTOMATIC, readMarkToStore(READ_MARK_NOT_FINISHED, completedAutomatically = false))
    }
}
