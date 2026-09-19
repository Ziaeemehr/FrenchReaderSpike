package com.ziaee.frenchreader.content

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextBodyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class EpubImportRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var epubFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        epubFile = File(context.cacheDir, "repository-test.epub")
        writeEpub(epubFile)
    }

    @After
    fun tearDown() {
        db.close()
        epubFile.delete()
    }

    @Test
    fun importsBookAsOneDocumentAndDeduplicatesSecondImport() = runBlocking {
        val repository = EpubImportRepository(context, db)
        val uri = Uri.fromFile(epubFile)

        val first = repository.import(uri)
        val documents = db.textDao().getAllOnce()

        assertEquals("Le Petit Test", first.bookTitle)
        assertEquals(2, first.importedCount)
        assertEquals(0, first.skippedCount)
        assertNotNull(first.firstTextId)
        assertEquals(null, first.folderId)
        assertEquals(1, documents.size)
        assertEquals("Le Petit Test", documents.single().title)
        assertEquals(null, documents.single().folderId)
        assertEquals("Le Petit Test", documents.single().sourceName)
        val body = TextBodyStore(context).read(documents.single())
        assertTrue(body.contains("# Première"))
        assertTrue(body.contains("# Deuxième"))

        val second = repository.import(uri)

        assertEquals(0, second.importedCount)
        assertEquals(0, second.skippedCount)
        assertEquals(first.folderId, second.folderId)
        assertEquals(first.firstTextId, second.firstTextId)
        assertEquals(1, db.textDao().getAllOnce().size)
    }

    @Test
    fun largeImportedBookRoundTripsThroughBodyFile() = runBlocking {
        writeEpub(epubFile, "Un très long texte. ".repeat(12_000))
        val result = EpubImportRepository(context, db).import(Uri.fromFile(epubFile))
        val document = db.textDao().getById(result.firstTextId!!)!!

        assertEquals("", document.rawText)
        assertNotNull(document.bodyPath)
        assertTrue(TextBodyStore(context).read(document).length > 200_000)
    }

    private fun writeEpub(
        file: File,
        longText: String = "Un texte français assez long pour former un chapitre complet. ".repeat(5)
    ) {
        val entries = linkedMapOf(
            "META-INF/container.xml" to """
                <?xml version="1.0"?>
                <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
            """.trimIndent(),
            "OEBPS/content.opf" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                  <metadata><dc:title>Le Petit Test</dc:title><dc:identifier>test-book-id</dc:identifier></metadata>
                  <manifest>
                    <item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="two" href="two.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="one"/><itemref idref="two"/></spine>
                </package>
            """.trimIndent(),
            "OEBPS/one.xhtml" to "<html><body><h1>Première</h1><p>$longText</p></body></html>",
            "OEBPS/two.xhtml" to "<html><body><h1>Deuxième</h1><p>$longText</p></body></html>"
        )
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, contents) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(contents.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
