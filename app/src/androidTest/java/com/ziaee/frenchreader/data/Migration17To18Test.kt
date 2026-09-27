package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration17To18Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration_17_18_test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(*ALL_MIGRATIONS)
        .addCallback(TEXT_SEARCH_CALLBACK)
        .build()

    @Test
    fun migrationTagsExistingRowsAndScopesUniqueKeysByLanguage() = runBlocking {
        open().apply {
            textDao().insert(TextDocument(title = "Texte", rawText = "bonjour", externalKey = "shared"))
            libraryOrganizerDao().insertFolder(LibraryFolder(name = "Folder"))
            headlineDao().insertAll(listOf(HeadlineEntity("source", "Source", "item", "Title", "Snippet", "https://news.example", null, 1, 2)))
            vocabDao().insert(VocabEntry(word = "chat", sentence = "", textId = -1, dictionaryUrl = ""))
            vocabListDao().insert(VocabList(name = "List"))
            reviewLogDao().insert(ReviewLogEntry(entryId = 1, timestampMs = 1, knew = true, boxBefore = 1, boxAfter = 2))
            activityLogDao().insert(ActivityLogEntry(date = "2026-09-28", listeningMs = 100))
            resourceDao().insert(ResourceLink(title = "Resource", url = "https://example.org"))
            shadowAttemptDao().insert(ShadowAttempt(textId = 1, chunkIndex = 0, sentenceIndex = 0, matched = 1, total = 1, engine = "test", timestampMs = 1))
            openHelper.writableDatabase.apply {
                listOf(
                    "texts", "library_folders", "headlines", "vocab", "vocab_lists",
                    "review_log", "activity_log", "resources", "shadow_attempts"
                ).forEach { table -> execSQL("DROP INDEX IF EXISTS index_${table}_language") }
                execSQL("DROP INDEX index_texts_externalKey_language")
                execSQL("CREATE UNIQUE INDEX index_texts_externalKey ON texts (externalKey)")
                execSQL("DROP INDEX index_resources_url_language")
                execSQL("CREATE UNIQUE INDEX index_resources_url ON resources (url)")
                // SQLite cannot drop columns that participate in primary keys, so rebuild those two.
                execSQL("CREATE TABLE headlines_v17 AS SELECT sourceId, sourceLabel, externalId, title, snippet, articleUrl, imageUrl, publishedAtMs, cachedAtMs FROM headlines")
                execSQL("DROP TABLE headlines")
                execSQL("ALTER TABLE headlines_v17 RENAME TO headlines")
                execSQL("CREATE UNIQUE INDEX index_headlines_pk_v17 ON headlines(sourceId, externalId)")
                execSQL("CREATE INDEX index_headlines_publishedAtMs ON headlines(publishedAtMs)")
                execSQL("CREATE INDEX index_headlines_articleUrl ON headlines(articleUrl)")
                execSQL("CREATE TABLE activity_log_v17 AS SELECT date, listeningMs FROM activity_log")
                execSQL("DROP TABLE activity_log")
                execSQL("ALTER TABLE activity_log_v17 RENAME TO activity_log")
                execSQL("CREATE UNIQUE INDEX index_activity_log_pk_v17 ON activity_log(date)")
                listOf("texts", "library_folders", "vocab", "vocab_lists", "review_log", "resources", "shadow_attempts")
                    .forEach { table -> execSQL("ALTER TABLE $table DROP COLUMN language") }
                version = 17
            }
            close()
        }

        val db = open()
        assertEquals("fr", db.textDao().getAllOnce("fr").single().language)
        assertEquals("fr", db.vocabDao().getAllOnce("fr").single().language)
        assertEquals("fr", db.resourceDao().getAllOnce("fr").single().language)
        listOf(
            "texts", "library_folders", "headlines", "vocab", "vocab_lists",
            "review_log", "activity_log", "resources", "shadow_attempts"
        ).forEach { table ->
            db.openHelper.readableDatabase.query("SELECT language FROM $table").use { cursor ->
                assertTrue("missing representative row for $table", cursor.moveToFirst())
                assertEquals("fr", cursor.getString(0))
            }
        }

        db.vocabDao().insert(VocabEntry(word = "chat", sentence = "", textId = -1, dictionaryUrl = "", language = "de"))
        assertEquals(1, db.vocabDao().getAllOnce("de").size)
        assertEquals(1, db.vocabDao().getAllOnce("fr").size)
        val germanTextId = db.textDao().insert(
            TextDocument(title = "Deutsch", rawText = "bonjour Hallo", externalKey = "shared", language = "de")
        )
        assertEquals(listOf(germanTextId), db.textDao().searchText("bonjour", "de").map { it.id })
        assertEquals(listOf(1L), db.textDao().searchText("bonjour", "fr").map { it.id })
        db.resourceDao().insert(ResourceLink(title = "Deutsch", url = "https://example.org", language = "de"))

        val indices = db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%_language'"
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        listOf(
            "texts", "library_folders", "headlines", "vocab", "vocab_lists",
            "review_log", "activity_log", "resources", "shadow_attempts"
        ).forEach { table -> assertTrue("missing language index for $table", "index_${table}_language" in indices) }
        db.close()
    }
}
