package com.ziaee.frenchreader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class IncomingContentClassifierTest {
    @Test
    fun `classifies supported mime types`() {
        assertEquals(IncomingContentKind.PDF, classifyIncomingContent("application/pdf", null))
        assertEquals(IncomingContentKind.IMAGE, classifyIncomingContent("image/jpeg", null))
        assertEquals(IncomingContentKind.EPUB, classifyIncomingContent("application/epub+zip", null))
        assertEquals(IncomingContentKind.TEXT, classifyIncomingContent("text/plain", null))
    }

    @Test
    fun `uses display name when mime type is missing or generic`() {
        assertEquals(IncomingContentKind.PDF, classifyIncomingContent(null, "lesson.PDF"))
        assertEquals(IncomingContentKind.EPUB, classifyIncomingContent("application/octet-stream", "book.epub"))
        assertEquals(IncomingContentKind.TEXT, classifyIncomingContent(null, "notes.md"))
    }

    @Test
    fun `mime type takes precedence over a conflicting file name`() {
        assertEquals(IncomingContentKind.IMAGE, classifyIncomingContent("image/png", "document.pdf"))
    }
}
