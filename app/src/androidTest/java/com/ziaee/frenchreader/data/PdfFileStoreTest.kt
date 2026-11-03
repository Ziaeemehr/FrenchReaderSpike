package com.ziaee.frenchreader.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ziaee.frenchreader.content.pdf.PdfFileStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfFileStoreTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val leases = mutableListOf<String>()

    @Before fun setUp() {
        root = File.createTempFile("pdf-store-test", "", app.cacheDir).apply { delete(); mkdirs() }
        context = object : ContextWrapper(app) { override fun getFilesDir(): File = root }
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
    }
    @After fun tearDown() {
        leases.forEach(PdfFileStore::releaseImport)
        database.close()
        root.deleteRecursively()
    }
    private suspend fun store(): String {
        val picked = File(root, "picked.pdf").apply { writeText("%PDF-1.4 test bytes") }
        return PdfFileStore.copy(context, Uri.fromFile(picked)).also { leases += it }
    }
    private fun release(path: String) { leases.remove(path); PdfFileStore.releaseImport(path) }

    @Test fun identicalBytesReuseFileAndKeepOriginal() = runBlocking {
        val first = store()
        val second = store()
        val source = File(root, "picked.pdf")
        val hash = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("pdfs/$hash.pdf", first)
        assertEquals(first, second)
        assertEquals(1, File(root, "pdfs").listFiles()!!.size)
        assertArrayEquals(source.readBytes(), PdfFileStore.resolve(context, first)!!.readBytes())
        assertNull(PdfFileStore.resolve(context, "pdfs/../../picked.pdf"))
    }

    @Test fun sharedReferencesAndPendingImportProtectFile() = runBlocking {
        val path = store()
        val dao = database.textDao()
        val first = TextDocument(title = "One", rawText = "Body", pdfPath = path)
        val second = TextDocument(title = "Two", rawText = "Body", pdfPath = path)
        val firstId = dao.insert(first)
        val secondId = dao.insert(second)
        dao.delete(first.copy(id = firstId))
        PdfFileStore.deleteIfUnreferenced(context, path, dao)
        assertNotNull(PdfFileStore.resolve(context, path))
        dao.delete(second.copy(id = secondId))
        // A pending confirmation dialog protects the bytes even with no saved rows.
        PdfFileStore.deleteIfUnreferenced(context, path, dao)
        assertNotNull(PdfFileStore.resolve(context, path))
        release(path)
        PdfFileStore.deleteIfUnreferenced(context, path, dao)
        assertNull(PdfFileStore.resolve(context, path))
    }
}
