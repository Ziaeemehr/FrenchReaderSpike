package com.ziaee.frenchreader.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    private val databaseName = "migration-5-6-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
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
                            "`publishedAt` INTEGER, " +
                            "`imagePath` TEXT, " +
                            "`externalKey` TEXT, " +
                            "`lastAccessedAtMs` INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_texts_externalKey` " +
                            "ON `texts` (`externalKey`)"
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
                        "CREATE TABLE `headlines` (" +
                            "`sourceId` TEXT NOT NULL, " +
                            "`sourceLabel` TEXT NOT NULL, " +
                            "`externalId` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`snippet` TEXT NOT NULL, " +
                            "`articleUrl` TEXT NOT NULL, " +
                            "`imageUrl` TEXT, " +
                            "`publishedAtMs` INTEGER, " +
                            "`cachedAtMs` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`sourceId`, `externalId`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_headlines_publishedAtMs` " +
                            "ON `headlines` (`publishedAtMs`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_headlines_articleUrl` " +
                            "ON `headlines` (`articleUrl`)"
                    )
                    db.execSQL(
                        "INSERT INTO texts (title, rawText, createdAtMs, lastChunkIndex, " +
                            "lastPositionMs, voice, ratePercent, translationLang, lastAccessedAtMs) " +
                            "VALUES ('Existing text', 'Existing body', 1, 2, 3, " +
                            "'fr-FR-DeniseNeural', 4, 'fa', 0)"
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
    fun migrationCreatesEmptyReviewLogAndActivityLogTables() {
        val db = helper.writableDatabase

        MIGRATION_5_6.migrate(db)

        db.query("SELECT title FROM texts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing text", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM review_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM activity_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.version = 6
        helper.close()
        val roomDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        roomDatabase.openHelper.writableDatabase
        roomDatabase.close()
    }

    @Test
    fun migrationCreatesTimestampIndexOnReviewLog() {
        val db = helper.writableDatabase

        MIGRATION_5_6.migrate(db)

        var foundIndex = false
        db.query("PRAGMA index_list(`review_log`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "index_review_log_timestampMs") foundIndex = true
            }
        }
        assertTrue(foundIndex)
    }
}
