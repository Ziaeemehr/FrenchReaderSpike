package com.ziaee.frenchreader.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "highlights", indices = [Index("textId")])
data class HighlightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val startOffset: Int,
    val endOffset: Int,
    val colorKey: String,
    val createdAtMs: Long = System.currentTimeMillis()
)
