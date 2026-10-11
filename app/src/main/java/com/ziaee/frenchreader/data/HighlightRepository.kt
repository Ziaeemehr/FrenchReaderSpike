package com.ziaee.frenchreader.data

import kotlinx.coroutines.flow.Flow

class HighlightRepository(private val dao: HighlightDao) {
    fun observeHighlights(textId: Long): Flow<List<HighlightEntry>> = dao.observeForText(textId)

    suspend fun addHighlight(textId: Long, startOffset: Int, endOffset: Int, colorKey: String) {
        if (startOffset >= endOffset || colorKey !in HighlightColors.keys) return
        // Highlighting text that is already highlighted recolours it: the new
        // highlight absorbs every one it touches instead of being stacked on top,
        // where the old colour would still show through.
        val absorbed = dao.overlapping(textId, startOffset, endOffset)
        val (start, end) = mergedHighlightRange(startOffset, endOffset, absorbed)
        dao.insert(HighlightEntry(textId = textId, startOffset = start, endOffset = end, colorKey = colorKey))
        absorbed.forEach { dao.deleteById(it.id) }
    }

    suspend fun updateColor(id: Long, colorKey: String) {
        if (colorKey in HighlightColors.keys) dao.updateColor(id, colorKey)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun delete(ids: Collection<Long>) {
        ids.distinct().forEach { dao.deleteById(it) }
    }
}

internal fun mergedHighlightRange(
    startOffset: Int,
    endOffset: Int,
    absorbed: List<HighlightEntry>
): Pair<Int, Int> = Pair(
    minOf(startOffset, absorbed.minOfOrNull { it.startOffset } ?: startOffset),
    maxOf(endOffset, absorbed.maxOfOrNull { it.endOffset } ?: endOffset)
)

object HighlightColors {
    val keys = setOf("yellow", "green", "blue", "pink", "orange")
}
