package com.ziaee.frenchreader.text

import java.util.Locale

val TEXT_LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

fun normalizeTextLevel(value: String?): String? =
    value?.trim()?.uppercase(Locale.ROOT)?.takeIf { it in TEXT_LEVELS }

fun levelFromFolderNames(namesFromRootToLeaf: List<String>): String? =
    namesFromRootToLeaf.asReversed().firstNotNullOfOrNull(::normalizeTextLevel)

internal suspend fun com.ziaee.frenchreader.data.LibraryOrganizerDao.levelForFolder(folderId: Long?): String? {
    if (folderId == null) return null
    val folders = getFoldersOnce().associateBy { it.id }
    val names = mutableListOf<String>()
    val visited = mutableSetOf<Long>()
    var id: Long? = folderId
    while (id != null && visited.add(id)) {
        val folder = folders[id] ?: break
        names += folder.name
        id = folder.parentId
    }
    return levelFromFolderNames(names.asReversed())
}
