package com.ziaee.frenchreader.data

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
class Migration7To8Test {
    private val databaseName = "migration-7-8-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
    )

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationCreatesOrganizerTablesAndLeavesExistingTextUnfiled() {
        val db = helper.writableDatabase
        db.execSQL(
            "CREATE TABLE texts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, rawText TEXT NOT NULL, " +
                "createdAtMs INTEGER NOT NULL, lastChunkIndex INTEGER NOT NULL, lastPositionMs INTEGER NOT NULL, " +
                "voice TEXT NOT NULL, ratePercent INTEGER NOT NULL, translationLang TEXT NOT NULL, " +
                "sourceUrl TEXT, sourceName TEXT, author TEXT, license TEXT, publishedAt INTEGER, imagePath TEXT, " +
                "externalKey TEXT, lastAccessedAtMs INTEGER NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO texts VALUES " +
                "(1, 'Titre', 'Texte', 1, 0, 0, 'voice', 0, 'fa', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0)"
        )

        MIGRATION_7_8.migrate(db)

        listOf("library_folders", "library_tags", "text_tags").forEach { table ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        }
        db.query("SELECT folderId FROM texts WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(true, cursor.isNull(0))
        }
    }
}
