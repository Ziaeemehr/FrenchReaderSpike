package com.ziaee.frenchreader.ui.library

import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.READ_MARK_AUTOMATIC
import com.ziaee.frenchreader.data.READ_MARK_FINISHED
import com.ziaee.frenchreader.data.READ_MARK_NOT_FINISHED
import com.ziaee.frenchreader.ui.statistics.isTextCompleted

enum class ReadingStatus { NOT_STARTED, IN_PROGRESS, READ }

internal fun isTextNotStarted(document: TextDocument): Boolean =
    document.lastAccessedAtMs == 0L &&
        document.lastChunkIndex == 0 &&
        document.lastPositionMs == 0L

internal fun isTextFinished(document: TextDocument, completedAutomatically: Boolean): Boolean =
    when (document.readMark) {
        READ_MARK_FINISHED -> true
        READ_MARK_NOT_FINISHED -> false
        else -> completedAutomatically
    }

internal fun readingStatus(document: TextDocument, completed: Boolean): ReadingStatus = when {
    isTextFinished(document, completed) -> ReadingStatus.READ
    isTextNotStarted(document) -> ReadingStatus.NOT_STARTED
    else -> ReadingStatus.IN_PROGRESS
}

internal class LibraryCompletionCache {
    private data class Key(
        val id: Long,
        val rawText: String,
        val bodyPath: String?,
        val lastChunkIndex: Int
    )

    private data class Entry(val key: Key, val completed: Boolean)

    private val entries = mutableMapOf<Long, Entry>()

    suspend fun completionFor(
        documents: List<TextDocument>,
        readBody: suspend (TextDocument) -> String
    ): Map<Long, Boolean> {
        val liveIds = documents.mapTo(mutableSetOf()) { it.id }
        entries.keys.retainAll(liveIds)
        return documents.associate { document ->
            if (isTextNotStarted(document)) {
                entries.remove(document.id)
                document.id to false
            } else {
                val key = Key(
                    id = document.id,
                    rawText = document.rawText,
                    bodyPath = document.bodyPath,
                    lastChunkIndex = document.lastChunkIndex
                )
                val cached = entries[document.id]?.takeIf { it.key == key }
                val completed = cached?.completed ?: isTextCompleted(document, readBody(document)).also {
                    entries[document.id] = Entry(key, it)
                }
                document.id to completed
            }
        }
    }
}

/** What to store when the user puts a text on the Finished ([READ_MARK_FINISHED]) or Reading shelf. */
internal fun readMarkToStore(chosen: Int, completedAutomatically: Boolean): Int =
    if ((chosen == READ_MARK_FINISHED) == completedAutomatically) READ_MARK_AUTOMATIC else chosen
