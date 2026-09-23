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
class Migration14To15Test {
    private val databaseName = "migration-14-15-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
    )

    @After fun tearDown() { helper.close(); context.deleteDatabase(databaseName) }

    @Test fun migrationCreatesShadowAttemptsTable() {
        val db = helper.writableDatabase
        MIGRATION_14_15.migrate(db)
        db.execSQL(
            "INSERT INTO shadow_attempts (textId, chunkIndex, sentenceIndex, matched, total, paceRatio, engine, timestampMs) " +
                "VALUES (1, 0, 2, 7, 9, 1.25, 'vosk', 1000), (1, 0, 3, 2, 4, NULL, 'android', 2000)"
        )
        db.query("SELECT matched, total, engine, paceRatio FROM shadow_attempts ORDER BY id").use { c ->
            c.moveToFirst()
            assertEquals(7, c.getInt(0)); assertEquals(9, c.getInt(1)); assertEquals("vosk", c.getString(2))
            assertEquals(1.25f, c.getFloat(3), 0.001f)
            c.moveToNext()
            assertEquals(true, c.isNull(3))
        }
    }
}
