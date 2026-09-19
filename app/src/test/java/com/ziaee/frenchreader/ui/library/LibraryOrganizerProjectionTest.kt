package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.TextDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizerProjectionTest {
    private val filed = TextDocument(id = 1, title = "Voyage", rawText = "", createdAtMs = 100, folderId = 10)
    private val unfiled = TextDocument(id = 2, title = "Cuisine", rawText = "", createdAtMs = 200)
    private val otherFolder = TextDocument(
        id = 3, title = "Voyage scolaire", rawText = "", createdAtMs = 300, folderId = 20
    )
    private val nested = TextDocument(id = 4, title = "Verbes", rawText = "", createdAtMs = 400, folderId = 11)
    private val documents = listOf(filed, unfiled, otherFolder, nested)
    private val folders = listOf(
        LibraryFolder(id = 10, name = "Grammar"),
        LibraryFolder(id = 11, name = "Verbs", parentId = 10),
        LibraryFolder(id = 20, name = "Culture")
    )
    private val tags = mapOf(1L to setOf(1L, 2L), 2L to setOf(1L), 3L to setOf(2L, 3L))

    @Test
    fun `folder filters include all unfiled or one folder`() {
        assertEquals(listOf(4L, 3L, 2L, 1L), ids(FolderFilter.All))
        assertEquals(listOf(2L), ids(FolderFilter.Unfiled))
        assertEquals(listOf(4L, 1L), ids(FolderFilter.Folder(10)))
        assertEquals(listOf(4L), ids(FolderFilter.Folder(11)))
    }

    @Test
    fun `tag filter requires every selected tag`() {
        assertEquals(listOf(2L, 1L), ids(selectedTagIds = setOf(1L)))
        assertEquals(listOf(1L), ids(selectedTagIds = setOf(1L, 2L)))
        assertEquals(emptyList<Long>(), ids(selectedTagIds = setOf(1L, 3L)))
    }

    @Test
    fun `folder tags and search combine`() {
        assertEquals(
            listOf(1L),
            ids(
                folderFilter = FolderFilter.Folder(10),
                selectedTagIds = setOf(1L, 2L),
                query = "voy"
            )
        )
        assertEquals(
            listOf(3L),
            ids(selectedTagIds = setOf(2L), query = "SCOLAIRE")
        )
    }

    private fun ids(
        folderFilter: FolderFilter = FolderFilter.All,
        selectedTagIds: Set<Long> = emptySet(),
        query: String = ""
    ) = projectLibrary(
        documents = documents,
        query = query,
        sort = LibrarySort.NEWEST,
        folderFilter = folderFilter,
        selectedTagIds = selectedTagIds,
        tagIdsByText = tags,
        folders = folders
    ).map { it.id }
}
