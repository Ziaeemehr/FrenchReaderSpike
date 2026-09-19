package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.LibraryFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderTreeTest {
    private val grammar = folder(1, "Grammar")
    private val verbs = folder(2, "verbs", parentId = 1)
    private val irregular = folder(3, "Irregular", parentId = 2)
    private val adjectives = folder(4, "Adjectives", parentId = 1)
    private val culture = folder(5, "culture")
    private val folders = listOf(verbs, culture, irregular, grammar, adjectives)

    @Test
    fun `descendant ids include the root and every nested descendant`() {
        assertEquals(setOf(1L, 2L, 3L, 4L), descendantIds(folders, grammar.id))
        assertEquals(setOf(3L), descendantIds(folders, irregular.id))
        assertEquals(setOf(99L), descendantIds(folders, 99L))
    }

    @Test
    fun `path is ordered from top-level folder to requested folder`() {
        assertEquals(listOf(grammar, verbs, irregular), pathTo(folders, irregular.id))
        assertEquals(emptyList<LibraryFolder>(), pathTo(folders, 99L))
    }

    @Test
    fun `children are scoped to one parent and sorted ignoring case`() {
        assertEquals(listOf(culture, grammar), childrenOf(folders, null))
        assertEquals(listOf(adjectives, verbs), childrenOf(folders, grammar.id))
    }

    @Test
    fun `folder cannot move into itself or any descendant`() {
        assertFalse(canMoveFolder(folders, grammar.id, grammar.id))
        assertFalse(canMoveFolder(folders, grammar.id, verbs.id))
        assertFalse(canMoveFolder(folders, grammar.id, irregular.id))
        assertTrue(canMoveFolder(folders, verbs.id, culture.id))
        assertTrue(canMoveFolder(folders, verbs.id, null))
    }

    @Test
    fun `flatten tree is depth first and alphabetical at each level`() {
        assertEquals(
            // This literal catches breadth-first or globally-sorted implementations.
            listOf(
                culture to 0,
                grammar to 0,
                adjectives to 1,
                verbs to 1,
                irregular to 2
            ),
            flattenTree(folders)
        )
    }

    @Test
    fun `corrupt cycles terminate and emit each folder once`() {
        val alpha = folder(10, "Alpha", parentId = 11)
        val beta = folder(11, "Beta", parentId = 10)
        val corrupt = listOf(beta, alpha)

        assertEquals(setOf(10L, 11L), descendantIds(corrupt, 10))
        assertEquals(emptyList<LibraryFolder>(), pathTo(corrupt, 10))
        assertEquals(listOf(alpha to 0, beta to 1), flattenTree(corrupt))
        assertFalse(canMoveFolder(corrupt, 10, 11))
    }

    private fun folder(id: Long, name: String, parentId: Long? = null) =
        LibraryFolder(id = id, name = name, createdAtMs = id, parentId = parentId)
}
