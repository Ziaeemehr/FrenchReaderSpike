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
class Migration21To22Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-21-22-test.db"
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(21) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE texts(id INTEGER PRIMARY KEY NOT NULL, title TEXT NOT NULL, rawText TEXT NOT NULL)")
                db.execSQL("INSERT INTO texts VALUES (42, 'Existing text', 'Keep my body')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build()
    )
    @After fun tearDown() { helper.close(); context.deleteDatabase(name) }

    @Test fun addsNullablePdfPathAndPreservesText() {
        val db = helper.writableDatabase
        MIGRATION_21_22.migrate(db)
        db.query("PRAGMA table_info(texts)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) if (cursor.getString(1) == "pdfPath") {
                found = true
                assertEquals("TEXT", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
            }
            assertTrue(found)
        }
        db.query("SELECT id, title, rawText, pdfPath FROM texts").use {
            assertTrue(it.moveToFirst())
            assertEquals(42L, it.getLong(0))
            assertEquals("Existing text", it.getString(1))
            assertEquals("Keep my body", it.getString(2))
            assertTrue(it.isNull(3))
            assertFalse(it.moveToNext())
        }
        db.execSQL("INSERT INTO texts(id,title,rawText) VALUES (43,'New','Body')")
        db.query("SELECT pdfPath FROM texts WHERE id=43").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
    }
}
