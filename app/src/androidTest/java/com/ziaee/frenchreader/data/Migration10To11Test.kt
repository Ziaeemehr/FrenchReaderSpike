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
class Migration10To11Test {
    private val databaseName = "migration-10-11-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
    )

    @After fun tearDown() { helper.close(); context.deleteDatabase(databaseName) }

    @Test fun migrationAddsNullableBodyPathAndPreservesRows() {
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE texts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, rawText TEXT NOT NULL)")
        db.execSQL("INSERT INTO texts (title, rawText) VALUES ('Titre', 'Corps')")

        MIGRATION_10_11.migrate(db)

        db.query("PRAGMA table_info(texts)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(name) == "bodyPath") found = true
            assertTrue(found)
        }
        db.query("SELECT rawText, bodyPath FROM texts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Corps", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
    }
}
