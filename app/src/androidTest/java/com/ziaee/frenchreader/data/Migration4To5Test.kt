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
                            "lastPositionMs, voice, ratePercent, translationLang) VALUES " +
                            "('Existing text', 'Existing body', 1, 0, 0, 'fr-FR-DeniseNeural', 0, 'fa')"
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

        db.query("SELECT imagePath, externalKey, lastAccessedAtMs FROM texts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertEquals(0L, cursor.getLong(2))
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
}
