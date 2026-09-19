package com.ziaee.frenchreader.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TextBodyStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = TextBodyStore(context)

    @Test
    fun writesReadsAndDeletesOwnedBody() = runBlocking {
        val body = "Corps très long"
        val path = store.writeBody(987654321L, body)
        val document = TextDocument(id = 987654321L, title = "Titre", rawText = "", bodyPath = path)

        assertEquals(body, store.read(document))
        assertTrue(store.delete(document))
        assertFalse(File(context.filesDir, path).exists())
    }

    @Test
    fun missingBodyFileReadsAsEmptyText() = runBlocking {
        val document = TextDocument(
            id = 987654322L,
            title = "Titre",
            rawText = "",
            bodyPath = "text_bodies/987654322.txt"
        )
        File(context.filesDir, document.bodyPath!!).delete()

        assertEquals("", store.read(document))
    }
}
