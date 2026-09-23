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

    // One-shot snapshot for the Statistics screen -- same reasoning as
    // VocabDao.getAllOnce(): a stable list to compute totals/completion
    // from, not a live Flow that would recompute mid-calculation.
    @Query("SELECT * FROM texts")
    suspend fun getAllOnce(): List<TextDocument>

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

    @Query("UPDATE texts SET imagePath = :imagePath WHERE id = :id")
    suspend fun updateImagePath(id: Long, imagePath: String)

    @Query("SELECT COUNT(*) FROM texts WHERE imagePath = :path AND id != :id")
    suspend fun countOtherTextsWithImagePath(path: String, id: Long): Int

    @Query("UPDATE texts SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE texts SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE texts SET folderId = :folderId WHERE id IN (:ids)")
    suspend fun setFolders(ids: List<Long>, folderId: Long?)
}

@Dao
interface LibraryOrganizerDao {
    @Query("SELECT * FROM library_folders ORDER BY name COLLATE NOCASE")
    fun observeFolders(): Flow<List<LibraryFolder>>

    @Query("SELECT * FROM library_tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<LibraryTag>>

    @Query("SELECT * FROM text_tags")
    fun observeAllTagRefs(): Flow<List<TextTagCrossRef>>

    @Insert
    suspend fun insertFolder(folder: LibraryFolder): Long

    @Query("SELECT * FROM library_folders WHERE name = :name COLLATE NOCASE AND (parentId IS :parentId) LIMIT 1")
    suspend fun findFolderByName(name: String, parentId: Long? = null): LibraryFolder?

    @Query("UPDATE library_folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("UPDATE library_folders SET parentId = :parentId WHERE id = :id")
    suspend fun moveFolder(id: Long, parentId: Long?)

    @Query("SELECT parentId FROM library_folders WHERE id = :id")
    suspend fun getFolderParentId(id: Long): Long?

    @Query("UPDATE library_folders SET parentId = :parentId WHERE parentId = :id")
    suspend fun moveChildFoldersUp(id: Long, parentId: Long?)

    @Query("UPDATE texts SET folderId = :parentId WHERE folderId = :id")
    suspend fun moveTextsUp(id: Long, parentId: Long?)

    @Query("DELETE FROM library_folders WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)

    @Transaction
    suspend fun deleteFolder(id: Long) {
        val parentId = getFolderParentId(id)
        moveChildFoldersUp(id, parentId)
        moveTextsUp(id, parentId)
        deleteFolderRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: LibraryTag): Long

    @Query("SELECT * FROM library_tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTagByName(name: String): LibraryTag?

    @Query("UPDATE library_tags SET name = :name WHERE id = :id")
    suspend fun renameTag(id: Long, name: String)

    @Query("DELETE FROM text_tags WHERE tagId = :id")
    suspend fun deleteRefsForTag(id: Long)

    @Query("DELETE FROM library_tags WHERE id = :id")
    suspend fun deleteTagRow(id: Long)

    @Transaction
    suspend fun deleteTag(id: Long) {
        deleteRefsForTag(id)
        deleteTagRow(id)
    }

    @Query("DELETE FROM text_tags WHERE textId = :textId")
    suspend fun deleteRefsForText(textId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagRefs(refs: List<TextTagCrossRef>)

    @Transaction
    suspend fun addTagsToTexts(textIds: List<Long>, tagIds: List<Long>) {
        insertTagRefs(textIds.distinct().flatMap { textId ->
            tagIds.distinct().map { tagId -> TextTagCrossRef(textId, tagId) }
        })
    }

    @Transaction
    suspend fun setTextTags(textId: Long, tagIds: List<Long>) {
        deleteRefsForText(textId)
        insertTagRefs(tagIds.distinct().map { TextTagCrossRef(textId, it) })
    }
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

    @Query("SELECT * FROM vocab WHERE textId = -1 AND sentence = ''")
    suspend fun getManualEntries(): List<VocabEntry>

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

    @Query("SELECT * FROM vocab_lists")
    suspend fun getAllOnce(): List<VocabList>

    @Insert
    suspend fun insert(list: VocabList): Long

    @Delete
    suspend fun delete(list: VocabList)
}

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(entry: ReviewLogEntry): Long

    @Query("DELETE FROM review_log WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM review_log WHERE timestampMs >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE timestampMs >= :sinceMs AND boxAfter > boxBefore")
    suspend fun countMovedForwardSince(sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE timestampMs >= :sinceMs AND boxAfter = 1 AND boxBefore != 1")
    suspend fun countReturnedToBoxOneSince(sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE knew = 1")
    suspend fun countKnew(): Int

    @Query("SELECT COUNT(*) FROM review_log")
    suspend fun countTotal(): Int

    @Query("SELECT DISTINCT date(timestampMs / 1000, 'unixepoch', 'localtime') FROM review_log")
    suspend fun distinctActiveDates(): List<String>

    @Query("SELECT * FROM review_log")
    suspend fun getAll(): List<ReviewLogEntry>
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log WHERE date = :date")
    suspend fun getForDate(date: String): ActivityLogEntry?

    @Insert
    suspend fun insert(entry: ActivityLogEntry)

    @Update
    suspend fun update(entry: ActivityLogEntry)

    // Read-modify-write instead of an SQL upsert -- see Global Constraints.
    @Transaction
    suspend fun addListening(date: String, deltaMs: Long) {
        val existing = getForDate(date)
        if (existing == null) {
            insert(ActivityLogEntry(date, deltaMs))
        } else {
            update(existing.copy(listeningMs = existing.listeningMs + deltaMs))
        }
    }

    @Query("SELECT * FROM activity_log WHERE date IN (:dates)")
    suspend fun getForDates(dates: List<String>): List<ActivityLogEntry>

    @Query("SELECT date FROM activity_log WHERE listeningMs > 0")
    suspend fun activeDates(): List<String>
}

@Dao
interface ResourceDao {
    @Query("SELECT * FROM resources ORDER BY createdAtMs DESC, id DESC")
    fun observeAll(): Flow<List<ResourceLink>>

    @Query("SELECT COUNT(*) FROM resources")
    suspend fun count(): Int

    @Query("SELECT * FROM resources WHERE imageUrl IS NULL")
    suspend fun getWithoutImages(): List<ResourceLink>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(resource: ResourceLink): Long

    @Update
    suspend fun update(resource: ResourceLink)

    @Delete
    suspend fun delete(resource: ResourceLink)
}
