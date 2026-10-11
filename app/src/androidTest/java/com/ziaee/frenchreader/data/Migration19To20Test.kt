package com.ziaee.frenchreader.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration19To20Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-19-20-test.db"
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(19) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE texts(id INTEGER PRIMARY KEY, folderId INTEGER, title TEXT)")
                db.execSQL("CREATE TABLE library_folders(id INTEGER PRIMARY KEY, name TEXT, parentId INTEGER)")
                db.execSQL("INSERT INTO library_folders VALUES (1, 'Histoires graduées', NULL), (2, 'A1', 1), (3, ' b2 ', 2), (4, 'Nested', 3), (5, 'Divers', 1), (6, 'C1 stories', 1)")
                db.execSQL("INSERT INTO texts VALUES (1, 2, 'A1 story'), (2, 4, 'B2 story'), (3, 5, 'Other'), (4, NULL, 'Unfiled'), (5, 6, 'Not exact')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build()
    )
    @After fun tearDown() { helper.close(); context.deleteDatabase(name) }

    @Test fun addsNullableLevelAndBackfillsNearestExactAncestor() {
        val db = helper.writableDatabase
        MIGRATION_19_20.migrate(db)
        db.query("PRAGMA table_info(texts)").use {
            assertTrue(it.moveToLast())
            assertEquals("level", it.getString(1))
            assertEquals("TEXT", it.getString(2))
            assertEquals(0, it.getInt(3))
            assertTrue(it.isNull(4))
        }
        db.query("SELECT id, title, level FROM texts ORDER BY id").use {
            val levels = buildList<String?> {
                while (it.moveToNext()) {
                    assertEquals(size + 1L, it.getLong(0))
                    assertFalse(it.getString(1).isBlank())
                    add(if (it.isNull(2)) null else it.getString(2))
                }
            }
            assertEquals(listOf("A1", "B2", null, null, null), levels)
        }
        db.execSQL("INSERT INTO texts(id, title) VALUES (6, 'New')")
        db.query("SELECT level FROM texts WHERE id = 6").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
    }
}
