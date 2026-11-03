package com.ziaee.frenchreader.content.pdf

/** Pending import dialogs also own a file until save transfers ownership to a database row. */
internal class PdfImportReferences {
    private val counts = mutableMapOf<String, Int>()
    @Synchronized fun retain(path: String) { counts[path] = (counts[path] ?: 0) + 1 }
    @Synchronized fun release(path: String) {
        val remaining = (counts[path] ?: 0) - 1
        if (remaining <= 0) counts.remove(path) else counts[path] = remaining
    }
    @Synchronized fun contains(path: String): Boolean = (counts[path] ?: 0) > 0
}
