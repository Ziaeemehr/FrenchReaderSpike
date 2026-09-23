package com.ziaee.frenchreader.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration13To14Test {
    private val databaseName = "migration-13-14-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
    )

    @After fun tearDown() { helper.close(); context.deleteDatabase(databaseName) }

    @Test fun migrationMarksBoxFiveCardsLearned() {
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE vocab (id INTEGER PRIMARY KEY NOT NULL, leitnerBox INTEGER NOT NULL, learned INTEGER NOT NULL)")
        db.execSQL("INSERT INTO vocab VALUES (1, 4, 0), (2, 5, 0), (3, 5, 1)")

        MIGRATION_13_14.migrate(db)

        db.query("SELECT id, learned FROM vocab ORDER BY id").use { cursor ->
            val values = mutableListOf<Pair<Long, Int>>()
            while (cursor.moveToNext()) values += cursor.getLong(0) to cursor.getInt(1)
            assertEquals(listOf(1L to 0, 2L to 1, 3L to 1), values)
        }
    }
}
