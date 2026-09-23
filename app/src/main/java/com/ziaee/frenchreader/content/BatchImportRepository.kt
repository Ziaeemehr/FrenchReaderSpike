package com.ziaee.frenchreader.content

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.TextBodyStorage
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.insertTextDocument
import com.ziaee.frenchreader.images.ArticleImageStorage
import com.ziaee.frenchreader.images.ArticleImageStore
import com.ziaee.frenchreader.ui.shared.queryDisplayName
import com.ziaee.frenchreader.util.MAX_TEXT_IMPORT_BYTES
import com.ziaee.frenchreader.util.readBytesLimited
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.util.UUID

data class BatchImportResult(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val folderId: Long?
)

private val localImageLine = Regex("^!\\[([^]]*)]\\((?!epubimg:|https?:)([^)]+)\\)$")

private val parentDocIds = java.util.concurrent.ConcurrentHashMap<String, String>()
private const val MAX_IMPORTED_IMAGE_BYTES = 8L * 1024 * 1024

class BatchImportRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val bodyStore: TextBodyStorage = TextBodyStore(context),
    private val epubRepository: EpubImportRepository = EpubImportRepository(context, db),
    private val imageStore: ArticleImageStorage = ArticleImageStore(context)
) {
    suspend fun importAll(uris: List<Uri>, folderName: String? = null): BatchImportResult =
        withContext(Dispatchers.IO) {
            importUris(uris, if (uris.size > 1) folderName ?: datedFolderName() else null)
        }

    suspend fun importTree(treeUri: Uri): BatchImportResult = withContext(Dispatchers.IO) {
        val name = queryTreeName(treeUri) ?: datedFolderName()
        importUris(listTreeFiles(treeUri), name, treeUri)
    }

    private suspend fun importUris(uris: List<Uri>, folderName: String?, treeUri: Uri? = null): BatchImportResult {
            val folderId = folderName?.let { createFolder(it) }
            var imported = 0
            var skipped = 0
            var failed = 0
            uris.forEach { uri ->
                try {
                    if (isEpub(uri)) {
                        val result = epubRepository.import(uri)
                        if (result.importedCount > 0) result.firstTextId?.let { db.textDao().setFolder(it, folderId) }
                        if (result.importedCount == 0) skipped++ else imported++
                    } else {
                        when (importText(uri, folderId, treeUri)) {
                            true -> imported++
                            false -> skipped++
                        }
                    }
                } catch (_: Exception) {
                    failed++
                }
            }
            return BatchImportResult(imported, skipped, failed, folderId)
    }

    private suspend fun importText(uri: Uri, folderId: Long?, treeUri: Uri?): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(MAX_TEXT_IMPORT_BYTES) }
            ?: error("Unable to open document")
        val content = bytes.toString(Charsets.UTF_8)
        val externalKey = "file:${sha1(bytes)}"
        val displayName = queryDisplayName(context, uri, stripExtension = false) ?: "Untitled.txt"
        val parsed = parseFrontMatter(content)
        if (db.textDao().findByExternalKey(externalKey) != null) return false
        val stagingDir = File(context.cacheDir, "batch-import-${UUID.randomUUID()}").apply { mkdirs() }
        val writtenPaths = mutableListOf<String>()
        try {
            File(stagingDir, "body-source.txt").writeText(parsed.body, Charsets.UTF_8)
            val body = resolveLocalImages(parsed.body, uri, treeUri, stagingDir, writtenPaths)
            val stagedBody = File(stagingDir, "body-final.txt").apply { writeText(body, Charsets.UTF_8) }
            return db.withTransaction {
                if (db.textDao().findByExternalKey(externalKey) != null) {
                    writtenPaths.forEach { imageStore.delete(it) }
                    return@withTransaction false
                }
                insertTextDocument(
                    db.textDao(), bodyStore,
                    TextDocument(
                        title = parsed.title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
                        rawText = "", sourceName = displayName, externalKey = externalKey, folderId = folderId
                    ),
                    stagedBody.readText(Charsets.UTF_8)
                )
                true
            }
        } catch (error: Exception) {
            writtenPaths.forEach { runCatching { imageStore.delete(it) } }
            throw error
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /** Rewrites standalone `![alt](relative/path.jpg)` lines to the reader's `epubimg:` form when the
     * image can be read from the imported tree; otherwise drops the line so it never reaches TTS. */
    private suspend fun resolveLocalImages(
        body: String,
        docUri: Uri,
        treeUri: Uri?,
        stagingDir: File,
        writtenPaths: MutableList<String>
    ): String {
        val out = mutableListOf<String>()
        for (line in body.lines()) {
            val m = localImageLine.matchEntire(line.trim())
            if (m == null) { out += line; continue }
            val bytes = treeUri?.let { readSibling(docUri, it, m.groupValues[2]) }
            val name = m.groupValues[2].substringAfterLast('/')
            val hash = bytes?.let(::sha1)
            val staged = bytes?.let { File(stagingDir, "image_${out.size}").apply { writeBytes(it) } }
            val stored = if (staged != null && hash != null) {
                imageStore.storeBytes("text_images/epub_$hash/$name", staged.readBytes())
            } else null
            if (stored != null) {
                writtenPaths += stored
                out += "![${m.groupValues[1]}](epubimg:$hash/$name)"
            }
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n")
    }

    private fun readSibling(docUri: Uri, treeUri: Uri, relative: String): ByteArray? = try {
        var dirId = parentDocIds[docUri.toString()]
        val parts = relative.split('/').filter { it.isNotEmpty() && it != "." }
        parts.forEachIndexed { index, part ->
            dirId = dirId?.let { findChild(treeUri, it, part) }
        }
        dirId?.let { id ->
            context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))?.use {
                it.readBytesLimited(MAX_IMPORTED_IMAGE_BYTES)
            }
        }
    } catch (_: Exception) { null }

    private fun findChild(treeUri: Uri, parentId: String, name: String): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) if (c.getString(1) == name) return c.getString(0)
        }
        return null
    }

    private suspend fun createFolder(name: String): Long = db.withTransaction {
        val cleanName = name.trim().ifBlank { datedFolderName() }
        db.libraryOrganizerDao().insertFolder(LibraryFolder(name = cleanName))
    }

    private fun isEpub(uri: Uri): Boolean {
        val name = queryDisplayName(context, uri, stripExtension = false)
        return context.contentResolver.getType(uri) == "application/epub+zip" ||
            name?.endsWith(".epub", ignoreCase = true) == true
    }

    private fun listTreeFiles(treeUri: Uri): List<Uri> {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val result = mutableListOf<Uri>()
        fun visit(documentId: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val type = cursor.getString(typeColumn)
                    if (type == DocumentsContract.Document.MIME_TYPE_DIR) visit(id)
                    else if (isTextName(cursor.getString(nameColumn))) {
                        result += DocumentsContract.buildDocumentUriUsingTree(treeUri, id).also { parentDocIds[it.toString()] = documentId }
                    }
                }
            }
        }
        visit(rootId)
        return result
    }

    private fun queryTreeName(treeUri: Uri): String? {
        val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return queryDisplayName(context, root, stripExtension = false)
    }

    private fun isTextName(name: String): Boolean =
        name.endsWith(".md", true) || name.endsWith(".markdown", true) || name.endsWith(".txt", true)

    private fun datedFolderName(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun sha1(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1")
        .digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
