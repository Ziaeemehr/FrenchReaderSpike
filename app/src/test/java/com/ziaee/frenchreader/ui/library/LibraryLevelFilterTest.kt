package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.TextDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLevelFilterTest {
    private val documents = listOf(
        TextDocument(id = 1, title = "Story", rawText = "", folderId = 10, level = "B2"),
        TextDocument(id = 2, title = "Story", rawText = "", folderId = 11, level = "B2"),
        TextDocument(id = 3, title = "Story", rawText = "", folderId = 11, level = "A1"),
        TextDocument(id = 4, title = "Other", rawText = "")
    )

    @Test fun allAndUnspecifiedAreDistinct() {
        assertEquals(4, projectLibrary(documents, "", LibrarySort.TITLE, shelf = LibraryShelf.ALL).size)
        assertEquals(listOf(4L), projectLibrary(
            documents, "", LibrarySort.TITLE, shelf = LibraryShelf.ALL, levelFilter = LevelFilter.Level(null)
        ).map { it.id })
        assertTrue(LibraryUiState(levelFilter = LevelFilter.Level(null)).isFiltering)
    }

    @Test fun combinesWithSearchFolderTagsAndShelfAndCounts() {
        val filtered = projectLibrary(
            documents, "story", LibrarySort.TITLE,
            folderFilter = FolderFilter.Folder(10), selectedTagIds = setOf(7),
            tagIdsByText = mapOf(1L to setOf(7), 2L to setOf(7), 3L to setOf(7)),
            folders = listOf(LibraryFolder(id = 10, name = "Parent"), LibraryFolder(id = 11, name = "Child", parentId = 10)),
            shelf = LibraryShelf.ALL, levelFilter = LevelFilter.Level("B2")
        )
        assertEquals(listOf(1L, 2L), filtered.map { it.id })
        assertEquals(LibraryShelfCounts(1, 1), libraryShelfCounts(filtered, setOf(2)))
        assertEquals(listOf(2L), projectLibrary(
            filtered, "", LibrarySort.TITLE, shelf = LibraryShelf.FINISHED, finishedIds = setOf(2)
        ).map { it.id })
        assertEquals(emptyList<TextDocument>(), projectLibrary(
            documents, "missing", LibrarySort.TITLE, shelf = LibraryShelf.ALL, levelFilter = LevelFilter.Level("B2")
        ))
        assertEquals(listOf(2L), projectLibrary(
            documents, "body", LibrarySort.TITLE, bodyMatchIds = setOf(2, 3),
            shelf = LibraryShelf.ALL, levelFilter = LevelFilter.Level("B2")
        ).map { it.id })
    }
}
