package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.LibraryTag
import com.ziaee.frenchreader.data.TextDocument
import java.util.Locale

enum class LibrarySort { NEWEST, TITLE, LAST_READ }

sealed interface FolderFilter {
    data object All : FolderFilter
    data object Unfiled : FolderFilter
    data class Folder(val id: Long) : FolderFilter
}

data class LibraryUiState(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.NEWEST,
    val documents: List<TextDocument> = emptyList(),
    val allDocuments: List<TextDocument> = documents,
    val folders: List<LibraryFolder> = emptyList(),
    val tags: List<LibraryTag> = emptyList(),
    val tagIdsByText: Map<Long, Set<Long>> = emptyMap(),
    val completionByTextId: Map<Long, Boolean> = emptyMap(),
    val folderFilter: FolderFilter = FolderFilter.All,
    val selectedTagIds: Set<Long> = emptySet(),
    val selectedIds: Set<Long> = emptySet(),
    val totalDocumentCount: Int = documents.size,
    /** Text id -> context around the match, for texts whose body matches the search. */
    val bodySnippets: Map<Long, String> = emptyMap()
) {
    /** Distinguishes "the library has zero saved texts" from "the current
     * search has zero matches" so the empty state can show the right copy. */
    val isSearching: Boolean get() = query.isNotBlank()
    val isFiltering: Boolean get() = folderFilter != FolderFilter.All || selectedTagIds.isNotEmpty()
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
}

internal fun toggleSelection(current: Set<Long>, id: Long): Set<Long> =
    current.toMutableSet().apply { if (!add(id)) remove(id) }

internal fun selectAllVisible(current: Set<Long>, visibleIds: Set<Long>): Set<Long> =
    if (visibleIds.isNotEmpty() && visibleIds.all(current::contains)) current - visibleIds else current + visibleIds

internal fun prunedSelection(selected: Set<Long>, existingIds: Set<Long>): Set<Long> = selected intersect existingIds

/** Pure local filter + sort over every saved document -- no network
 * involved, so Library stays fully usable offline. [id] is the final
 * tie-breaker for a stable, deterministic order within equal sort keys. */
internal fun projectLibrary(
    documents: List<TextDocument>,
    query: String,
    sort: LibrarySort,
    folderFilter: FolderFilter = FolderFilter.All,
    selectedTagIds: Set<Long> = emptySet(),
    tagIdsByText: Map<Long, Set<Long>> = emptyMap(),
    folders: List<LibraryFolder> = emptyList(),
    bodyMatchIds: Set<Long> = emptySet()
): List<TextDocument> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val includedFolderIds = (folderFilter as? FolderFilter.Folder)?.let {
        descendantIds(folders, it.id)
    }.orEmpty()
    val filtered = documents.filter { doc ->
        val matchesQuery = normalizedQuery.isBlank() ||
            doc.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            doc.sourceName?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true ||
            doc.id in bodyMatchIds
        val matchesFolder = when (folderFilter) {
            FolderFilter.All -> true
            FolderFilter.Unfiled -> doc.folderId == null
            is FolderFilter.Folder -> doc.folderId in includedFolderIds
        }
        val matchesTags = tagIdsByText[doc.id].orEmpty().containsAll(selectedTagIds)
        matchesQuery && matchesFolder && matchesTags
    }
    val comparator: Comparator<TextDocument> = when (sort) {
        LibrarySort.NEWEST -> compareByDescending { it.createdAtMs }
        LibrarySort.TITLE -> compareBy { it.title.lowercase(Locale.ROOT) }
        LibrarySort.LAST_READ -> compareByDescending { it.lastAccessedAtMs }
    }
    return filtered.sortedWith(comparator.thenBy { it.id })
}

/** Turns what the user typed into an FTS4 MATCH expression: every word must appear, each as a
 * prefix so results update while typing ("eco" finds "école"). Only letters and digits reach
 * the query, so FTS syntax in the input can't break it. Null when there is nothing to search. */
internal fun textSearchMatch(query: String): String? =
    Regex("[\\p{L}\\p{N}]+").findAll(query.lowercase(Locale.ROOT))
        .joinToString(" ") { "${it.value}*" }
        .ifBlank { null }
