package com.ziaee.frenchreader.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Max ids per `IN (:ids)` query: older SQLite caps bound variables at 999. */
const val SQL_ID_CHUNK = 500

@Dao
interface TextDao {
    @Query("SELECT * FROM texts WHERE language = :language ORDER BY createdAtMs DESC")
    fun observeAll(language: String): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts WHERE language = :language ORDER BY createdAtMs DESC LIMIT 5")
    fun observeRecent(language: String): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts WHERE language = :language AND lastAccessedAtMs > 0 ORDER BY lastAccessedAtMs DESC LIMIT 1")
    fun observeMostRecentlyAccessed(language: String): Flow<TextDocument?>

    @Query("SELECT * FROM texts WHERE language = :language AND (title LIKE '%' || :query || '%' OR sourceName LIKE '%' || :query || '%') ORDER BY createdAtMs DESC")
    fun observeMatchingTitleOrSource(query: String, language: String): Flow<List<TextDocument>>

    @Query("SELECT * FROM texts WHERE id = :id")
    suspend fun getById(id: Long): TextDocument?

    // One-shot snapshot for the Statistics screen -- same reasoning as
    // VocabDao.getAllOnce(): a stable list to compute totals/completion
    // from, not a live Flow that would recompute mid-calculation.
    @Query("SELECT * FROM texts WHERE language = :language")
    suspend fun getAllOnce(language: String): List<TextDocument>

    @Query("SELECT * FROM texts WHERE externalKey = :externalKey AND language = :language LIMIT 1")
    suspend fun findByExternalKey(externalKey: String, language: String): TextDocument?

    @Query("SELECT externalKey FROM texts WHERE language = :language AND externalKey LIKE 'dataset:%'")
    suspend fun getDatasetExternalKeys(language: String): List<String>

    @Insert
    suspend fun insert(text: TextDocument): Long

    // Body column is 1; ntokens 12 gives roughly a line of context around the match.
    @Query(
        "SELECT texts_fts.rowid AS id, snippet(texts_fts, '', '', '…', 1, 12) AS snippet " +
            "FROM texts_fts JOIN texts ON texts.id = texts_fts.rowid " +
            "WHERE texts_fts MATCH :match AND texts.language = :language LIMIT 1000"
    )
    suspend fun searchText(match: String, language: String): List<TextSearchHit>

    @Query("UPDATE texts_fts SET body = :body WHERE rowid = :id")
    suspend fun setSearchBody(id: Long, body: String)

    @Query("SELECT * FROM texts WHERE bodyPath IS NOT NULL AND id IN (SELECT rowid FROM texts_fts WHERE body = '')")
    suspend fun getUnindexedFileBodies(): List<TextDocument>

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
    @Query("SELECT * FROM library_folders WHERE language = :language ORDER BY name COLLATE NOCASE")
    fun observeFolders(language: String): Flow<List<LibraryFolder>>

    @Query("SELECT * FROM library_tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<LibraryTag>>

    @Query("SELECT * FROM text_tags")
    fun observeAllTagRefs(): Flow<List<TextTagCrossRef>>

    @Insert
    suspend fun insertFolder(folder: LibraryFolder): Long

    @Query("SELECT * FROM library_folders WHERE language = :language AND name = :name COLLATE NOCASE AND (parentId IS :parentId) LIMIT 1")
    suspend fun findFolderByName(name: String, parentId: Long? = null, language: String): LibraryFolder?

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

    @Query("DELETE FROM library_folders WHERE id IN (:ids)")
    suspend fun deleteFolderRows(ids: List<Long>)

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
    @Query("SELECT * FROM headlines WHERE language = :language ORDER BY publishedAtMs DESC LIMIT :limit")
    fun observeRecent(limit: Int, language: String): Flow<List<HeadlineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HeadlineEntity>)

    @Query("DELETE FROM headlines WHERE sourceId = :sourceId AND language = :language")
    suspend fun clearSource(sourceId: String, language: String)

    @Transaction
    suspend fun replaceSource(sourceId: String, language: String, items: List<HeadlineEntity>) {
        clearSource(sourceId, language)
        insertAll(items)
    }
}

@Dao
interface VocabDao {
    @Query("SELECT * FROM vocab WHERE language = :language ORDER BY createdAtMs DESC")
    fun observeAll(language: String): Flow<List<VocabEntry>>

    // One-shot snapshot for a review session -- deliberately not a Flow, so
    // the queue a person is reviewing doesn't reorder/shrink under them
    // mid-session as they answer cards.
    @Query("SELECT * FROM vocab WHERE language = :language")
    suspend fun getAllOnce(language: String): List<VocabEntry>

    @Query("SELECT * FROM vocab WHERE textId = :textId AND language = :language ORDER BY createdAtMs DESC")
    fun observeForText(textId: Long, language: String): Flow<List<VocabEntry>>

    // Repeating a word within the same sentence should update the existing
    // entry, not create a duplicate row (design doc requirement).
    @Query("SELECT * FROM vocab WHERE textId = :textId AND word = :word AND sentence = :sentence AND language = :language LIMIT 1")
    suspend fun findExisting(textId: Long, word: String, sentence: String, language: String): VocabEntry?

    @Query("SELECT * FROM vocab WHERE textId = -1 AND sentence = '' AND language = :language")
    suspend fun getManualEntries(language: String): List<VocabEntry>

    @Insert
    suspend fun insert(entry: VocabEntry): Long

    @Update
    suspend fun update(entry: VocabEntry)

    @Query("UPDATE vocab SET word = :word, meaning = :meaning, sentence = :sentence WHERE id = :id")
    suspend fun updateText(id: Long, word: String, meaning: String?, sentence: String)

    @Query("UPDATE vocab SET leitnerBox = :leitnerBox, nextReviewAtMs = :nextReviewAtMs, lastReviewedAtMs = :lastReviewedAtMs, learned = :learned WHERE id = :id")
    suspend fun updateSchedule(id: Long, leitnerBox: Int, nextReviewAtMs: Long, lastReviewedAtMs: Long?, learned: Boolean)

    @Delete
    suspend fun delete(entry: VocabEntry)

    // Un-files every word in a deleted list rather than deleting the words
    // themselves.
    @Query("UPDATE vocab SET listId = NULL WHERE listId = :listId")
    suspend fun clearListId(listId: Long)

    @Query("UPDATE vocab SET listId = :listId WHERE id IN (:ids)")
    suspend fun setListId(ids: List<Long>, listId: Long?)

    @Query("DELETE FROM vocab WHERE listId = :listId")
    suspend fun deleteByListId(listId: Long)

    @Query("DELETE FROM vocab WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface VocabListDao {
    @Query("SELECT * FROM vocab_lists WHERE language = :language ORDER BY createdAtMs ASC")
    fun observeAll(language: String): Flow<List<VocabList>>

    @Query("SELECT * FROM vocab_lists WHERE language = :language")
    suspend fun getAllOnce(language: String): List<VocabList>

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

    @Query("SELECT COUNT(*) FROM review_log WHERE language = :language AND timestampMs >= :sinceMs")
    suspend fun countSince(sinceMs: Long, language: String): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE language = :language AND timestampMs >= :sinceMs AND boxAfter > boxBefore")
    suspend fun countMovedForwardSince(sinceMs: Long, language: String): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE language = :language AND timestampMs >= :sinceMs AND boxAfter = 1 AND boxBefore != 1")
    suspend fun countReturnedToBoxOneSince(sinceMs: Long, language: String): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE language = :language AND knew = 1")
    suspend fun countKnew(language: String): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE language = :language")
    suspend fun countTotal(language: String): Int

    @Query("SELECT DISTINCT date(timestampMs / 1000, 'unixepoch', 'localtime') FROM review_log WHERE language = :language")
    suspend fun distinctActiveDates(language: String): List<String>

    @Query("SELECT * FROM review_log WHERE language = :language")
    suspend fun getAll(language: String): List<ReviewLogEntry>
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log WHERE date = :date AND language = :language")
    suspend fun getForDate(date: String, language: String): ActivityLogEntry?

    @Insert
    suspend fun insert(entry: ActivityLogEntry)

    @Update
    suspend fun update(entry: ActivityLogEntry)

    // Read-modify-write instead of an SQL upsert -- see Global Constraints.
    @Transaction
    suspend fun addListening(date: String, deltaMs: Long, language: String) {
        val existing = getForDate(date, language)
        if (existing == null) {
            insert(ActivityLogEntry(date, deltaMs, language))
        } else {
            update(existing.copy(listeningMs = existing.listeningMs + deltaMs))
        }
    }

    @Query("SELECT * FROM activity_log WHERE language = :language AND date IN (:dates)")
    suspend fun getForDates(dates: List<String>, language: String): List<ActivityLogEntry>

    @Query("SELECT date FROM activity_log WHERE language = :language AND listeningMs > 0")
    suspend fun activeDates(language: String): List<String>
}

@Dao
interface ResourceDao {
    @Query("SELECT * FROM resources WHERE language = :language ORDER BY createdAtMs DESC, id DESC")
    fun observeAll(language: String): Flow<List<ResourceLink>>

    @Query("SELECT COUNT(*) FROM resources WHERE language = :language")
    suspend fun count(language: String): Int

    @Query("SELECT * FROM resources WHERE language = :language ORDER BY createdAtMs DESC, id DESC")
    suspend fun getAllOnce(language: String): List<ResourceLink>

    @Query("SELECT * FROM resources WHERE language = :language AND imageUrl IS NULL")
    suspend fun getWithoutImages(language: String): List<ResourceLink>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(resource: ResourceLink): Long

    /** For seeding built-ins: a URL the user already has is left untouched. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(resources: List<ResourceLink>)

    @Update
    suspend fun update(resource: ResourceLink)

    @Delete
    suspend fun delete(resource: ResourceLink)

    @Query("DELETE FROM resources WHERE language = :language AND url IN (:urls)")
    suspend fun deleteByUrls(urls: Collection<String>, language: String)
}

@Dao
interface ShadowAttemptDao {
    @Insert
    suspend fun insert(attempt: ShadowAttempt): Long

    @Query("SELECT * FROM shadow_attempts WHERE language = :language AND timestampMs >= :sinceMs ORDER BY timestampMs")
    suspend fun getSince(sinceMs: Long, language: String): List<ShadowAttempt>

    @Query("SELECT * FROM shadow_attempts WHERE textId = :textId AND language = :language AND timestampMs >= :sinceMs ORDER BY timestampMs")
    suspend fun getForTextSince(textId: Long, sinceMs: Long, language: String): List<ShadowAttempt>

    @Query("SELECT COUNT(*) FROM shadow_attempts WHERE language = :language")
    suspend fun countAll(language: String): Int

    @Query("SELECT DISTINCT date(timestampMs / 1000, 'unixepoch', 'localtime') FROM shadow_attempts WHERE language = :language")
    suspend fun distinctActiveDates(language: String): List<String>
}
