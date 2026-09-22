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
class Migration11To12Test {
    private val databaseName = "migration-11-12-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
    )

    @After fun tearDown() { helper.close(); context.deleteDatabase(databaseName) }

    @Test fun migrationCreatesHighlightsTableAndTextIdIndex() {
        val db = helper.writableDatabase

        MIGRATION_11_12.migrate(db)
        db.execSQL(
            "INSERT INTO highlights (textId, startOffset, endOffset, colorKey, createdAtMs) " +
                "VALUES (7, 2, 9, 'yellow', 123)"
        )

        db.query("SELECT textId, startOffset, endOffset, colorKey, createdAtMs FROM highlights").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7L, cursor.getLong(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals(9, cursor.getInt(2))
            assertEquals("yellow", cursor.getString(3))
            assertEquals(123L, cursor.getLong(4))
        }
        db.query("PRAGMA index_list(highlights)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(name) == "index_highlights_textId") found = true
            assertTrue(found)
        }
    }
}
