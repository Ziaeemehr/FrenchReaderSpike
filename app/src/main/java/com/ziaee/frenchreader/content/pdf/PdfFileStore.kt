package com.ziaee.frenchreader.content.pdf

import android.content.Context
import android.net.Uri
import com.ziaee.frenchreader.data.TextDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Content-addressed, app-owned PDFs. Never touches the original picked document. */
object PdfFileStore {
    private val lock = Mutex()
    private val pending = PdfImportReferences()
    private val ownedPath = Regex("pdfs/[a-f0-9]{64}\\.pdf")

    suspend fun copy(context: Context, uri: Uri): String {
        var retained: String? = null
        try {
            return withContext(Dispatchers.IO) {
                val directory = File(context.filesDir, "pdfs").apply { mkdirs() }
                val temp = File.createTempFile(".import-", ".tmp", directory)
                try {
                    val hash = MessageDigest.getInstance("SHA-256")
                    (context.contentResolver.openInputStream(uri) ?: error("Unable to open PDF")).use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                hash.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    val relative = "pdfs/${hash.digest().joinToString("") { "%02x".format(it) }}.pdf"
                    lock.withLock {
                        val target = File(context.filesDir, relative)
                        if (!target.isFile) check(temp.renameTo(target)) { "Unable to store PDF" }
                        pending.retain(relative)
                        retained = relative
                    }
                    relative
                } finally {
                    temp.delete()
                }
            }
        } catch (error: Throwable) {
            retained?.let(::releaseImport)
            throw error
        }
    }

    fun releaseImport(relPath: String) { pending.release(relPath) }

    fun resolve(context: Context, relPath: String): File? {
        if (!ownedPath.matches(relPath)) return null
        val directory = File(context.filesDir, "pdfs").canonicalFile
        return File(context.filesDir, relPath).canonicalFile.takeIf { it.parentFile == directory && it.isFile }
    }

    suspend fun deleteIfUnreferenced(context: Context, relPath: String, dao: TextDao) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!pending.contains(relPath) && dao.countWithPdfPath(relPath) == 0) resolve(context, relPath)?.delete()
        }
    }

    fun sizeBytes(context: Context): Long = File(context.filesDir, "pdfs")
        .walkTopDown().filter { it.isFile && it.extension == "pdf" }.sumOf { it.length() }
}
