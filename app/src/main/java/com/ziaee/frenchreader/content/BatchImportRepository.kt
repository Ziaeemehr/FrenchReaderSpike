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
import com.ziaee.frenchreader.ui.shared.queryDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatchImportResult(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val folderId: Long?
)

class BatchImportRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val bodyStore: TextBodyStorage = TextBodyStore(context),
    private val epubRepository: EpubImportRepository = EpubImportRepository(context, db)
) {
    suspend fun importAll(uris: List<Uri>, folderName: String? = null): BatchImportResult =
        withContext(Dispatchers.IO) {
            importUris(uris, if (uris.size > 1) folderName ?: datedFolderName() else null)
        }

    suspend fun importTree(treeUri: Uri): BatchImportResult = withContext(Dispatchers.IO) {
        val name = queryTreeName(treeUri) ?: datedFolderName()
        importUris(listTreeFiles(treeUri), name)
    }

    private suspend fun importUris(uris: List<Uri>, folderName: String?): BatchImportResult {
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
                        when (importText(uri, folderId)) {
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

    private suspend fun importText(uri: Uri, folderId: Long?): Boolean {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to open document")
        val content = bytes.toString(Charsets.UTF_8)
        val externalKey = "file:${sha1(bytes)}"
        val displayName = queryDisplayName(context, uri, stripExtension = false) ?: "Untitled.txt"
        val parsed = parseFrontMatter(content)
        return db.withTransaction {
            if (db.textDao().findByExternalKey(externalKey) != null) return@withTransaction false
            insertTextDocument(
                db.textDao(), bodyStore,
                TextDocument(
                    title = parsed.title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.'),
                    rawText = "",
                    sourceName = displayName,
                    externalKey = externalKey,
                    folderId = folderId
                ),
                parsed.body
            )
            true
        }
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
                        result += DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
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
