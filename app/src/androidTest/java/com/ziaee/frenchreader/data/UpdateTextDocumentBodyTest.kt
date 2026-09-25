package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateTextDocumentBodyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val bodyStore = TextBodyStore(context)

    @After
    fun tearDown() = db.close()

    @Test
    fun renamingAFileStoredTextWithABlankBodyKeepsTheBody() = runBlocking {
        val body = "Une très longue histoire. ".repeat(INLINE_BODY_CHAR_LIMIT / 20)
        val id = insertTextDocument(db.textDao(), bodyStore, TextDocument(title = "Livre", rawText = ""), body)
        val stored = db.textDao().getById(id)!!
        assertNotNull(stored.bodyPath)

        // What the editor sends for a read-only (file-stored) body: new title, blank body.
        updateTextDocumentBody(db.textDao(), bodyStore, stored, "Nouveau titre", "")

        val renamed = db.textDao().getById(id)!!
        assertEquals("Nouveau titre", renamed.title)
        assertEquals(stored.bodyPath, renamed.bodyPath)
        assertEquals(body, bodyStore.read(renamed))
        bodyStore.delete(renamed)
        Unit
    }
}
