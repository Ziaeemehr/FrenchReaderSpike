package com.ziaee.frenchreader.data

import kotlinx.coroutines.flow.Flow

class HighlightRepository(private val dao: HighlightDao) {
    fun observeHighlights(textId: Long): Flow<List<HighlightEntry>> = dao.observeForText(textId)

    suspend fun addHighlight(textId: Long, startOffset: Int, endOffset: Int, colorKey: String) {
        if (startOffset >= endOffset || colorKey !in HighlightColors.keys) return
        dao.insert(
            HighlightEntry(
                textId = textId,
                startOffset = startOffset,
                endOffset = endOffset,
                colorKey = colorKey
            )
        )
    }

    suspend fun updateColor(id: Long, colorKey: String) {
        if (colorKey in HighlightColors.keys) dao.updateColor(id, colorKey)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}

object HighlightColors {
    val keys = setOf("yellow", "green", "blue", "pink", "orange")
}
