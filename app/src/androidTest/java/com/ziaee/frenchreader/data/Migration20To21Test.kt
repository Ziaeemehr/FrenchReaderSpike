package com.ziaee.frenchreader.data

import android.content.Context
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
class Migration20To21Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-20-21-test.db"
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(20) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE activity_log(date TEXT PRIMARY KEY NOT NULL, listeningMs INTEGER NOT NULL)")
                db.execSQL("INSERT INTO activity_log VALUES ('2026-10-25', 12345)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build()
    )
    @After fun tearDown() { helper.close(); context.deleteDatabase(name) }

    @Test fun addsNonNullDefaultDurationsAndPreservesListening() {
        val db = helper.writableDatabase
        MIGRATION_20_21.migrate(db)
        db.query("PRAGMA table_info(activity_log)").use { cursor ->
            var added = 0
            while (cursor.moveToNext()) {
                if (cursor.getString(1) in setOf("readingMs", "watchingMs")) {
                    assertEquals("INTEGER", cursor.getString(2))
                    assertEquals(1, cursor.getInt(3))
                    assertEquals("0", cursor.getString(4))
                    added++
                }
            }
            assertEquals(2, added)
        }
        db.execSQL("INSERT INTO activity_log(date, listeningMs) VALUES ('2026-10-26', 0)")
        db.query("SELECT listeningMs, readingMs, watchingMs FROM activity_log ORDER BY date").use {
            assertTrue(it.moveToFirst())
            assertEquals(12345L, it.getLong(0))
            assertEquals(0L, it.getLong(1))
            assertEquals(0L, it.getLong(2))
            assertTrue(it.moveToNext())
            assertEquals(0L, it.getLong(1))
            assertEquals(0L, it.getLong(2))
        }
    }
}
