package com.ziaee.frenchreader.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    private val databaseName = "migration-4-5-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `texts` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`rawText` TEXT NOT NULL, " +
                            "`createdAtMs` INTEGER NOT NULL, " +
                            "`lastChunkIndex` INTEGER NOT NULL, " +
                            "`lastPositionMs` INTEGER NOT NULL, " +
                            "`voice` TEXT NOT NULL, " +
                            "`ratePercent` INTEGER NOT NULL, " +
                            "`translationLang` TEXT NOT NULL, " +
                            "`sourceUrl` TEXT, " +
                            "`sourceName` TEXT, " +
                            "`author` TEXT, " +
                            "`license` TEXT, " +
                            "`publishedAt` INTEGER)"
                    )
                    db.execSQL(
                        "CREATE TABLE `vocab` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`word` TEXT NOT NULL, " +
                            "`sentence` TEXT NOT NULL, " +
                            "`textId` INTEGER NOT NULL, " +
                            "`dictionaryUrl` TEXT NOT NULL, " +
                            "`meaning` TEXT, " +
                            "`learned` INTEGER NOT NULL, " +
                            "`createdAtMs` INTEGER NOT NULL, " +
                            "`listId` INTEGER, " +
                            "`leitnerBox` INTEGER NOT NULL, " +
                            "`nextReviewAtMs` INTEGER NOT NULL, " +
                            "`lastReviewedAtMs` INTEGER)"
                    )
                    db.execSQL(
                        "CREATE TABLE `vocab_lists` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`createdAtMs` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO texts (title, rawText, createdAtMs, lastChunkIndex, " +
                            "lastPositionMs, voice, ratePercent, translationLang, sourceUrl, " +
                            "sourceName, author, license, publishedAt) VALUES " +
                            "('Existing text', 'Existing body', 1, 2, 3, 'fr-FR-DeniseNeural', " +
                            "4, 'fa', 'https://example.test/article', 'Example Source', " +
                            "'Example Author', 'CC BY', 5)"
                    )
                    db.execSQL(
                        "INSERT INTO vocab (word, sentence, textId, dictionaryUrl, meaning, " +
                            "learned, createdAtMs, listId, leitnerBox, nextReviewAtMs, " +
                            "lastReviewedAtMs) VALUES ('bonjour', 'Bonjour le monde.', 1, " +
                            "'https://example.test/dictionary', 'hello', 0, 6, NULL, 2, 7, 8)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
    )

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesExistingTextAndCreatesEmptyHeadlineCache() {
        val db = helper.writableDatabase

        MIGRATION_4_5.migrate(db)

        db.query(
            "SELECT title, rawText, createdAtMs, lastChunkIndex, lastPositionMs, voice, " +
                "ratePercent, translationLang, sourceUrl, sourceName, author, license, " +
                "publishedAt, imagePath, externalKey, lastAccessedAtMs FROM texts"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing text", cursor.getString(0))
            assertEquals("Existing body", cursor.getString(1))
            assertEquals(1L, cursor.getLong(2))
            assertEquals(2, cursor.getInt(3))
            assertEquals(3L, cursor.getLong(4))
            assertEquals("fr-FR-DeniseNeural", cursor.getString(5))
            assertEquals(4, cursor.getInt(6))
            assertEquals("fa", cursor.getString(7))
            assertEquals("https://example.test/article", cursor.getString(8))
            assertEquals("Example Source", cursor.getString(9))
            assertEquals("Example Author", cursor.getString(10))
            assertEquals("CC BY", cursor.getString(11))
            assertEquals(5L, cursor.getLong(12))
            assertTrue(cursor.isNull(13))
            assertTrue(cursor.isNull(14))
            assertEquals(0L, cursor.getLong(15))
        }
        db.query("SELECT word, sentence, textId, meaning, leitnerBox FROM vocab").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("bonjour", cursor.getString(0))
            assertEquals("Bonjour le monde.", cursor.getString(1))
            assertEquals(1L, cursor.getLong(2))
            assertEquals("hello", cursor.getString(3))
            assertEquals(2, cursor.getInt(4))
        }
        db.query("SELECT COUNT(*) FROM headlines").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.version = 5
        helper.close()
        val roomDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        roomDatabase.openHelper.writableDatabase
        roomDatabase.close()
    }

    @Test
    fun migrationCreatesRoomCompatibleExternalKeyIndex() {
        val db = helper.writableDatabase

        MIGRATION_4_5.migrate(db)

        var foundExternalKeyIndex = false
        db.query("PRAGMA index_list(`texts`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val partialColumn = cursor.getColumnIndexOrThrow("partial")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "index_texts_externalKey") {
                    foundExternalKeyIndex = true
                    assertEquals(0, cursor.getInt(partialColumn))
                }
            }
        }
        assertTrue(foundExternalKeyIndex)
    }
}
