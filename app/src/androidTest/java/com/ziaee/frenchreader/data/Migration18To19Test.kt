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
class Migration18To19Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-18-19-test.db"
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(18) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE texts(id INTEGER PRIMARY KEY)")
                db.execSQL("INSERT INTO texts VALUES (3)")
                db.execSQL("CREATE TABLE existing(id INTEGER PRIMARY KEY)")
                db.execSQL("INSERT INTO existing VALUES (7)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build()
    )
    @After fun tearDown() { helper.close(); context.deleteDatabase(name) }

    @Test fun createsExactAttemptShapeAndPreservesExistingRows() {
        val db = helper.writableDatabase
        MIGRATION_18_19.migrate(db)
        db.query("SELECT id FROM existing").use { assertTrue(it.moveToFirst()); assertEquals(7, it.getInt(0)) }
        db.query("PRAGMA table_info(texts)").use { cursor ->
            assertTrue(cursor.moveToLast())
            assertEquals("readMark", cursor.getString(1))
            assertEquals("INTEGER", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals("0", cursor.getString(4))
        }
        db.query("SELECT readMark FROM texts WHERE id = 3").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        db.execSQL("INSERT INTO texts(id) VALUES (4)")
        db.query("SELECT readMark FROM texts WHERE id = 4").use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
        }
        val columns = db.query("PRAGMA table_info(exam_attempts)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(Triple(cursor.getString(1), cursor.getString(2), cursor.getInt(3))) }
        }
        assertEquals(listOf("id", "examId", "questionId", "questionFr", "answer", "score", "note", "createdAtMs", "updatedAtMs"), columns.map { it.first })
        assertEquals(listOf("INTEGER", "TEXT", "TEXT", "TEXT", "TEXT", "REAL", "TEXT", "INTEGER", "INTEGER"), columns.map { it.second })
        assertEquals(listOf(1, 1, 1, 1, 1, 0, 1, 1, 1), columns.map { it.third })
        db.query("PRAGMA index_info(index_exam_attempts_examId_questionId)").use { cursor ->
            val fields = buildList { while (cursor.moveToNext()) add(cursor.getString(2)) }
            assertEquals(listOf("examId", "questionId"), fields)
        }
        db.execSQL("INSERT INTO exam_attempts(examId, questionId, questionFr, answer, score, note, createdAtMs, updatedAtMs) VALUES ('tcf', 'q', 'French', '', NULL, '', 1, 1)")
        db.query("SELECT id, score FROM exam_attempts").use { assertTrue(it.moveToFirst()); assertEquals(1L, it.getLong(0)); assertTrue(it.isNull(1)) }
    }
}
