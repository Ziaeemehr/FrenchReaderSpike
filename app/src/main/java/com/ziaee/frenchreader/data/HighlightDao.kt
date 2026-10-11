package com.ziaee.frenchreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Insert
    suspend fun insert(entry: HighlightEntry): Long

    @Query("UPDATE highlights SET colorKey = :colorKey WHERE id = :id")
    suspend fun updateColor(id: Long, colorKey: String)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM highlights WHERE textId = :textId AND startOffset < :endOffset AND endOffset > :startOffset")
    suspend fun overlapping(textId: Long, startOffset: Int, endOffset: Int): List<HighlightEntry>

    /** Inserts [entry] and removes the highlights it replaces, as one step. */
    @Transaction
    suspend fun insertReplacing(entry: HighlightEntry, replacedIds: List<Long>) {
        insert(entry)
        replacedIds.forEach { deleteById(it) }
    }

    @Query("SELECT * FROM highlights WHERE textId = :textId ORDER BY createdAtMs ASC, id ASC")
    fun observeForText(textId: Long): Flow<List<HighlightEntry>>
}
