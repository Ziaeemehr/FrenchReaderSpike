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
class Migration17To18Test {
    private val name = "migration-17-18-test.db"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(17) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE existing(id INTEGER PRIMARY KEY)")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()
    )

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(name)
    }

    @Test
    fun createsExactStudyLogShapeAndPreservesExistingTables() {
        val db = helper.writableDatabase
        MIGRATION_17_18.migrate(db)
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }
        assertTrue(tables.containsAll(setOf("existing", "study_source", "study_session")))

        val columns = db.query("PRAGMA table_info(study_session)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(1))
                }
            }
        }
        assertEquals(
            listOf(
                "id",
                "date",
                "duration_min",
                "skill",
                "source_id",
                "note",
                "created_at",
                "updated_at"
            ),
            columns
        )

        db.query("PRAGMA foreign_key_list(study_session)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "RESTRICT",
                cursor.getString(cursor.getColumnIndexOrThrow("on_delete"))
            )
        }
        val indexes = db.query("PRAGMA index_list(study_session)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(1))
                }
            }
        }
        assertTrue(
            indexes.containsAll(
                setOf("index_study_session_date", "index_study_session_source_id")
            )
        )
    }
}
