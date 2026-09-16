package com.ziaee.frenchreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TextDao {
    @Query("SELECT * FROM texts ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts ORDER BY createdAtMs DESC LIMIT 5")
    fun observeRecent(): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts WHERE lastAccessedAtMs > 0 ORDER BY lastAccessedAtMs DESC LIMIT 1")
    fun observeMostRecentlyAccessed(): Flow<TextDocument?>

    @Query("SELECT * FROM texts WHERE title LIKE '%' || :query || '%' OR sourceName LIKE '%' || :query || '%' ORDER BY createdAtMs DESC")
    fun observeMatchingTitleOrSource(query: String): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts WHERE id = :id")
    suspend fun getById(id: Long): TextDocument?

    @Query("SELECT * FROM texts WHERE externalKey = :externalKey LIMIT 1")
    suspend fun findByExternalKey(externalKey: String): TextDocument?

    @Insert
    suspend fun insert(text: TextDocument): Long

    @Update
    suspend fun update(text: TextDocument)

    @Delete
    suspend fun delete(text: TextDocument)

    @Query("UPDATE texts SET lastChunkIndex = :chunkIndex, lastPositionMs = :positionMs WHERE id = :id")
    suspend fun savePosition(id: Long, chunkIndex: Int, positionMs: Long)

    @Query("UPDATE texts SET lastAccessedAtMs = :now WHERE id = :id")
    suspend fun markAccessed(id: Long, now: Long)
}

@Dao
interface HeadlineDao {
    @Query("SELECT * FROM headlines ORDER BY publishedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HeadlineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HeadlineEntity>)

    @Query("DELETE FROM headlines WHERE sourceId = :sourceId")
    suspend fun clearSource(sourceId: String)

    @Transaction
    suspend fun replaceSource(sourceId: String, items: List<HeadlineEntity>) {
        clearSource(sourceId)
        insertAll(items)
    }
}

@Dao
interface VocabDao {
    @Query("SELECT * FROM vocab ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<VocabEntry>>

    // One-shot snapshot for a review session -- deliberately not a Flow, so
    // the queue a person is reviewing doesn't reorder/shrink under them
    // mid-session as they answer cards.
    @Query("SELECT * FROM vocab")
    suspend fun getAllOnce(): List<VocabEntry>

    @Query("SELECT * FROM vocab WHERE textId = :textId ORDER BY createdAtMs DESC")
    fun observeForText(textId: Long): Flow<List<VocabEntry>>

    // Repeating a word within the same sentence should update the existing
    // entry, not create a duplicate row (design doc requirement).
    @Query("SELECT * FROM vocab WHERE textId = :textId AND word = :word AND sentence = :sentence LIMIT 1")
    suspend fun findExisting(textId: Long, word: String, sentence: String): VocabEntry?

    @Insert
    suspend fun insert(entry: VocabEntry): Long

    @Update
    suspend fun update(entry: VocabEntry)

    @Delete
    suspend fun delete(entry: VocabEntry)

    // Un-files every word in a deleted list rather than deleting the words
    // themselves.
    @Query("UPDATE vocab SET listId = NULL WHERE listId = :listId")
    suspend fun clearListId(listId: Long)
}

@Dao
interface VocabListDao {
    @Query("SELECT * FROM vocab_lists ORDER BY createdAtMs ASC")
    fun observeAll(): Flow<List<VocabList>>

    @Insert
    suspend fun insert(list: VocabList): Long

    @Delete
    suspend fun delete(list: VocabList)
}
