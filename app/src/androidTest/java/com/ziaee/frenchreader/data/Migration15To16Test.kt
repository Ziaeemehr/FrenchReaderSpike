package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration15To16Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "migration_15_16_test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(*ALL_MIGRATIONS)
        .addCallback(TEXT_SEARCH_CALLBACK)
        .build()

    @Test
    fun migrationIndexesExistingTextsAndKeepsThemInSync() = runBlocking {
        // Build a current schema, then strip it back to v15 (no FTS table or triggers).
        open().apply {
            textDao().insert(TextDocument(id = 1, title = "Le camping", rawText = "Nous allons à l'école demain."))
            textDao().insert(TextDocument(id = 2, title = "Livre", rawText = "", bodyPath = "text_bodies/2.txt"))
            openHelper.writableDatabase.apply {
                execSQL("DROP TRIGGER IF EXISTS texts_fts_ai")
                execSQL("DROP TRIGGER IF EXISTS texts_fts_ad")
                execSQL("DROP TRIGGER IF EXISTS texts_fts_au")
                execSQL("DROP TABLE texts_fts")
                // v17 columns, so the chain 15 -> 16 -> 17 runs from a true v15 shape.
                execSQL("ALTER TABLE resources DROP COLUMN description")
                execSQL("ALTER TABLE resources DROP COLUMN level")
                version = 15
            }
            close()
        }

        // No destructive fallback here: a schema mismatch would throw instead of wiping data.
        val db = open()
        val dao = db.textDao()
        assertEquals(2, dao.getAllOnce().size)

        // Accent-insensitive, prefix match on an inline body.
        assertEquals(listOf(1L), dao.searchText("ecol*").map { it.id })
        assertTrue(dao.searchText("ecol*").single().snippet.contains("école"))

        // File-backed body is indexed from Kotlin.
        assertEquals(listOf(2L), dao.getUnindexedFileBodies().map { it.id })
        dao.setSearchBody(2, "Il était une fois un dragon.")
        assertEquals(listOf(2L), dao.searchText("dragon*").map { it.id })
        assertTrue(dao.getUnindexedFileBodies().isEmpty())

        // Triggers: insert, edit (inline body + title), delete.
        dao.insert(TextDocument(id = 3, title = "Recette", rawText = "Une tarte aux pommes."))
        assertEquals(listOf(3L), dao.searchText("pomme*").map { it.id })
        dao.update(dao.getById(3)!!.copy(title = "Dessert", rawText = "Un gâteau au chocolat."))
        assertTrue(dao.searchText("pomme*").isEmpty())
        assertEquals(listOf(3L), dao.searchText("gateau*").map { it.id })
        assertEquals(listOf(3L), dao.searchText("dessert*").map { it.id })
        // A title-only update of a file-backed text keeps its indexed body.
        dao.update(dao.getById(2)!!.copy(title = "Conte"))
        assertEquals(listOf(2L), dao.searchText("dragon*").map { it.id })
        dao.delete(dao.getById(3)!!)
        assertTrue(dao.searchText("gateau*").isEmpty())
        db.close()
    }

    @Test
    fun migration16To17AddsResourceDetailsAndKeepsResources() = runBlocking {
        open().apply {
            resourceDao().insert(ResourceLink(title = "Mine", url = "https://example.org/", category = "reading"))
            openHelper.writableDatabase.apply {
                execSQL("ALTER TABLE resources DROP COLUMN description")
                execSQL("ALTER TABLE resources DROP COLUMN level")
                version = 16
            }
            close()
        }
        val db = open()
        val stored = db.resourceDao().getAllOnce().single()
        assertEquals("Mine", stored.title)
        assertEquals(null, stored.description)
        db.resourceDao().update(stored.copy(description = "Notes", level = "B1"))
        assertEquals("B1", db.resourceDao().getAllOnce().single().level)
        db.close()
    }
}
