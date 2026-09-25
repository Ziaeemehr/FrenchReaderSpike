package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.TextDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryTextSearchTest {
    @Test
    fun matchUsesPrefixWordsAndDropsFtsSyntax() {
        assertEquals("l* école* demain*", textSearchMatch("L'école  DEMAIN"))
        assertEquals("a* or* b*", textSearchMatch("\"a\" OR -b*"))
        assertEquals("چادر*", textSearchMatch("چادر"))
        assertNull(textSearchMatch("  ,;! "))
    }

    @Test
    fun bodyMatchesAreIncludedAlongsideTitleMatches() {
        val docs = listOf(
            TextDocument(id = 1, title = "École", rawText = ""),
            TextDocument(id = 2, title = "Autre", rawText = ""),
            TextDocument(id = 3, title = "Rien", rawText = "")
        )
        val result = projectLibrary(docs, "école", LibrarySort.TITLE, bodyMatchIds = setOf(2L))
        assertEquals(listOf(2L, 1L), result.map { it.id })
    }
}
