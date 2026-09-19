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
class Migration9To10Test {
    private val databaseName = "migration-9-10-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
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
    fun migrationAddsCategoryCategorisesExistingRowsAndSeedsPodcasts() {
        val db = helper.writableDatabase
        db.execSQL(
            "CREATE TABLE resources (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "title TEXT NOT NULL, url TEXT NOT NULL, imageUrl TEXT, createdAtMs INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX index_resources_url ON resources (url)")
        val existing = listOf(
            "TV5" to "https://apprendre.tv5monde.com/",
            "Lingua" to "https://lingua.com/french/reading/",
            "Lawless" to "https://www.lawlessfrench.com/reading/",
            "FluencyDrop" to "https://fluencydrop.com/",
            "Fabulang" to "https://www.fabulang.com/en/fr/",
            "Gutenberg" to "https://www.gutenberg.org/ebooks/",
            "Unmatched" to "https://example.com/"
        )
        existing.forEachIndexed { index, (title, url) ->
            db.execSQL(
                "INSERT INTO resources (title, url, imageUrl, createdAtMs) VALUES (?, ?, NULL, ?)",
                arrayOf(title, url, index)
            )
        }

        MIGRATION_9_10.migrate(db)

        db.query("PRAGMA table_info(resources)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
            var foundCategory = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == "category") {
                    foundCategory = true
                    assertEquals(1, cursor.getInt(notNullIndex))
                    assertEquals("'other'", cursor.getString(defaultIndex))
                }
            }
            assertTrue(foundCategory)
        }

        val expectedCategories = mapOf(
            "TV5" to "video",
            "Lingua" to "reading",
            "Lawless" to "reading",
            "FluencyDrop" to "reading",
            "Fabulang" to "reading",
            "Gutenberg" to "books",
            "Unmatched" to "other"
        )
        db.query("SELECT title, category FROM resources WHERE category != 'podcasts'").use { cursor ->
            while (cursor.moveToNext()) {
                assertEquals(expectedCategories.getValue(cursor.getString(0)), cursor.getString(1))
            }
        }

        db.query(
            "SELECT title, url, imageUrl, createdAtMs, category FROM resources " +
                "WHERE category = 'podcasts' ORDER BY title"
        ).use { cursor ->
            val podcasts = mutableListOf<Pair<String, String>>()
            while (cursor.moveToNext()) {
                podcasts += cursor.getString(0) to cursor.getString(1)
                assertTrue(cursor.isNull(2))
                assertEquals(1L, cursor.getLong(3))
                assertEquals("podcasts", cursor.getString(4))
            }
            assertEquals(
                listOf(
                    "Coffee Break French" to "https://coffeebreakfrench.com/",
                    "Français Authentique" to "https://www.francaisauthentique.com/podcast/",
                    "InnerFrench" to "https://innerfrench.com/podcast/",
                    "Journal en français facile (RFI)" to
                        "https://francaisfacile.rfi.fr/fr/podcasts/journal-en-fran%C3%A7ais-facile/"
                ),
                podcasts
            )
        }
    }
}
