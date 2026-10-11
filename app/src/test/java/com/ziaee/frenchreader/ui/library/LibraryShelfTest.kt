package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.TextDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShelfTest {
    private val documents = listOf(
        TextDocument(id = 1, title = "New", rawText = "", createdAtMs = 1),
        TextDocument(id = 2, title = "Started", rawText = "", createdAtMs = 2, lastAccessedAtMs = 1),
        TextDocument(id = 3, title = "Done", rawText = "", createdAtMs = 3, folderId = 10),
        TextDocument(id = 4, title = "Done too", rawText = "", createdAtMs = 4, folderId = 11)
    )
    private val finishedIds = setOf(3L, 4L)

    @Test
    fun `each shelf projects the right documents in sort order`() {
        fun ids(shelf: LibraryShelf) = projectLibrary(
            documents, "", LibrarySort.NEWEST, shelf = shelf, finishedIds = finishedIds
        ).map { it.id }
        assertEquals(listOf(2L, 1L), ids(LibraryShelf.READING))
        assertEquals(listOf(4L, 3L), ids(LibraryShelf.FINISHED))
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(LibraryShelf.ALL))
        assertEquals(LibraryShelfCounts(2, 2), libraryShelfCounts(documents, finishedIds))
        assertEquals(4, libraryShelfCounts(documents, finishedIds).all)
    }

    @Test
    fun `counts respect search folder descendants tags and body matches before shelf filtering`() {
        val filtered = projectLibrary(
            documents, "done", LibrarySort.TITLE,
            folderFilter = FolderFilter.Folder(10), selectedTagIds = setOf(7L),
            tagIdsByText = mapOf(3L to setOf(7L), 4L to setOf(7L)),
            folders = listOf(LibraryFolder(id = 10, name = "Parent"), LibraryFolder(id = 11, name = "Child", parentId = 10)),
            shelf = LibraryShelf.ALL
        )
        assertEquals(LibraryShelfCounts(0, 2), libraryShelfCounts(filtered, finishedIds))
        assertEquals(emptyList<TextDocument>(), projectLibrary(filtered, "", LibrarySort.TITLE, finishedIds = finishedIds))
        val bodyMatches = projectLibrary(documents, "body", LibrarySort.NEWEST, bodyMatchIds = setOf(2L, 3L), shelf = LibraryShelf.ALL)
        assertEquals(LibraryShelfCounts(1, 1), libraryShelfCounts(bodyMatches, finishedIds))
        val noMatches = projectLibrary(documents, "missing", LibrarySort.NEWEST, shelf = LibraryShelf.ALL)
        assertEquals(LibraryShelfCounts(), libraryShelfCounts(noMatches, finishedIds))
    }

    @Test
    fun `shelf change prunes selection to visible ids`() {
        val reading = projectLibrary(documents, "", LibrarySort.NEWEST, finishedIds = finishedIds)
        assertEquals(setOf(1L), prunedSelection(setOf(1L, 3L), reading.mapTo(mutableSetOf()) { it.id }))
    }
}
