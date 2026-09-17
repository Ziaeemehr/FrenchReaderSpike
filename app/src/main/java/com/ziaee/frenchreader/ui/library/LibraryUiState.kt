package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.TextDocument
import java.util.Locale

enum class LibrarySort { NEWEST, TITLE, LAST_READ }

data class LibraryUiState(
    val query: String = "",
    val sort: LibrarySort = LibrarySort.NEWEST,
    val documents: List<TextDocument> = emptyList()
) {
    /** Distinguishes "the library has zero saved texts" from "the current
     * search has zero matches" so the empty state can show the right copy. */
    val isSearching: Boolean get() = query.isNotBlank()
}

/** Pure local filter + sort over every saved document -- no network
 * involved, so Library stays fully usable offline. [id] is the final
 * tie-breaker for a stable, deterministic order within equal sort keys. */
internal fun projectLibrary(documents: List<TextDocument>, query: String, sort: LibrarySort): List<TextDocument> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filtered = if (normalizedQuery.isBlank()) {
        documents
    } else {
        documents.filter { doc ->
            doc.title.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                doc.sourceName?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true
        }
    }
    val comparator: Comparator<TextDocument> = when (sort) {
        LibrarySort.NEWEST -> compareByDescending { it.createdAtMs }
        LibrarySort.TITLE -> compareBy { it.title.lowercase(Locale.ROOT) }
        LibrarySort.LAST_READ -> compareByDescending { it.lastAccessedAtMs }
    }
    return filtered.sortedWith(comparator.thenBy { it.id })
}
