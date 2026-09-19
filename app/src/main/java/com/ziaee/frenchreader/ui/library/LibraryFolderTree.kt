package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.LibraryFolder
import java.util.Locale

private val folderNameComparator = compareBy<LibraryFolder> { it.name.lowercase(Locale.ROOT) }
    .thenBy { it.name }
    .thenBy { it.id }

/** Returns [rootId] and every folder reachable below it, even if the data contains a cycle. */
fun descendantIds(folders: List<LibraryFolder>, rootId: Long): Set<Long> {
    val childrenByParent = folders.groupBy { it.parentId }
    val visited = linkedSetOf<Long>()
    val pending = ArrayDeque<Long>().apply { add(rootId) }
    while (pending.isNotEmpty()) {
        val id = pending.removeLast()
        if (!visited.add(id)) continue
        childrenByParent[id].orEmpty().forEach { pending.add(it.id) }
    }
    return visited
}

/** Returns a valid root-to-folder path, or empty when the folder is missing or its ancestry is corrupt. */
fun pathTo(folders: List<LibraryFolder>, id: Long): List<LibraryFolder> {
    val byId = folders.associateBy { it.id }
    val reversed = mutableListOf<LibraryFolder>()
    val visited = mutableSetOf<Long>()
    var current = byId[id] ?: return emptyList()
    while (true) {
        if (!visited.add(current.id)) return emptyList()
        reversed += current
        val parentId = current.parentId ?: break
        current = byId[parentId] ?: return emptyList()
    }
    return reversed.asReversed()
}

fun childrenOf(folders: List<LibraryFolder>, parentId: Long?): List<LibraryFolder> =
    folders.filter { it.parentId == parentId }.sortedWith(folderNameComparator)

fun canMoveFolder(folders: List<LibraryFolder>, id: Long, newParentId: Long?): Boolean =
    newParentId != id && (newParentId == null || newParentId !in descendantIds(folders, id))

/** Depth-first tree order with deterministic recovery for orphans and parent cycles. */
fun flattenTree(folders: List<LibraryFolder>): List<Pair<LibraryFolder, Int>> {
    val ids = folders.mapTo(mutableSetOf()) { it.id }
    val childrenByParent = folders.groupBy { it.parentId }
        .mapValues { (_, children) -> children.sortedWith(folderNameComparator) }
    val roots = folders.filter { it.parentId == null || it.parentId !in ids }.sortedWith(folderNameComparator)
    val visited = mutableSetOf<Long>()
    val result = mutableListOf<Pair<LibraryFolder, Int>>()

    fun append(folder: LibraryFolder, depth: Int) {
        if (!visited.add(folder.id)) return
        result += folder to depth
        childrenByParent[folder.id].orEmpty().forEach { append(it, depth + 1) }
    }

    roots.forEach { append(it, 0) }
    folders.sortedWith(folderNameComparator).forEach { if (it.id !in visited) append(it, 0) }
    return result
}
