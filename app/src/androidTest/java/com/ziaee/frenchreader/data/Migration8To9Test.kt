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
class Migration8To9Test {
    private val databaseName = "migration-8-9-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
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
    fun migrationAddsNullableParentAndKeepsExistingFoldersTopLevel() {
        val db = helper.writableDatabase
        db.execSQL(
            "CREATE TABLE library_folders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, createdAtMs INTEGER NOT NULL)"
        )
        db.execSQL("INSERT INTO library_folders VALUES (1, 'Grammar', 10)")

        MIGRATION_8_9.migrate(db)

        db.query("PRAGMA table_info(library_folders)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            var foundParent = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "parentId") {
                    foundParent = true
                    assertEquals(0, cursor.getInt(notNullIndex))
                }
            }
            assertTrue(foundParent)
        }
        db.query("SELECT parentId FROM library_folders WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
    }
}
