package com.ziaee.frenchreader.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val INLINE_BODY_CHAR_LIMIT = 200_000
private const val BODIES_DIR_NAME = "text_bodies"

interface TextBodyStorage {
    suspend fun read(doc: TextDocument): String
    suspend fun writeBody(id: Long, body: String): String
    suspend fun delete(doc: TextDocument): Boolean
}

class TextBodyStore(private val context: Context) : TextBodyStorage {
    override suspend fun read(doc: TextDocument): String = withContext(Dispatchers.IO) {
        val path = doc.bodyPath ?: return@withContext doc.rawText
        val file = resolveOwned(path) ?: return@withContext ""
        runCatching { file.takeIf(File::isFile)?.readText(Charsets.UTF_8).orEmpty() }.getOrDefault("")
    }

    override suspend fun writeBody(id: Long, body: String): String = withContext(Dispatchers.IO) {
        val relativePath = "$BODIES_DIR_NAME/$id.txt"
        val destination = resolveOwned(relativePath) ?: error("Invalid text body path")
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.tmp")
        try {
            temporary.writeText(body, Charsets.UTF_8)
            if (!temporary.renameTo(destination)) {
                destination.writeBytes(temporary.readBytes())
                temporary.delete()
            }
            relativePath
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    override suspend fun delete(doc: TextDocument): Boolean = withContext(Dispatchers.IO) {
        doc.bodyPath?.let(::resolveOwned)?.delete() ?: false
    }

    private fun resolveOwned(relativePath: String): File? {
        if (!isOwnedPath(relativePath)) return null
        val directory = File(context.filesDir, BODIES_DIR_NAME).canonicalFile
        val file = File(context.filesDir, relativePath).canonicalFile
        return file.takeIf { it.parentFile == directory }
    }

    companion object {
        fun isOwnedPath(relativePath: String): Boolean {
            if (relativePath.startsWith('/') || '\\' in relativePath) return false
            val components = relativePath.split('/')
            return components.size == 2 && components[0] == BODIES_DIR_NAME &&
                components[1].matches(Regex("[1-9][0-9]*\\.txt"))
        }
    }
}

suspend fun insertTextDocument(
    textDao: TextDao,
    bodyStore: TextBodyStorage,
    document: TextDocument,
    body: String
): Long {
    if (body.length <= INLINE_BODY_CHAR_LIMIT) {
        return textDao.insert(document.copy(rawText = body, bodyPath = null))
    }
    val row = document.copy(rawText = "", bodyPath = null)
    val id = textDao.insert(row)
    val path = try {
        bodyStore.writeBody(id, body)
    } catch (error: Exception) {
        textDao.getById(id)?.let { textDao.delete(it) }
        throw error
    }
    try {
        textDao.update(row.copy(id = id, bodyPath = path))
    } catch (error: Exception) {
        bodyStore.delete(row.copy(id = id, bodyPath = path))
        textDao.getById(id)?.let { textDao.delete(it) }
        throw error
    }
    indexFileBody(textDao, id, body)
    return id
}

suspend fun updateTextDocumentBody(
    textDao: TextDao,
    bodyStore: TextBodyStorage,
    document: TextDocument,
    title: String,
    body: String
) {
    val oldBody = bodyStore.read(document)
    // A blank body never replaces a stored one: editors that only rename (e.g. file-stored
    // bodies, which aren't editable) must not be able to wipe the text.
    val changed = body.isNotBlank() && body != oldBody
    val base = document.copy(
        title = title.ifBlank { document.title },
        lastChunkIndex = if (changed) 0 else document.lastChunkIndex,
        lastPositionMs = if (changed) 0 else document.lastPositionMs
    )
    if (!changed) {
        textDao.update(base)
    } else if (body.length <= INLINE_BODY_CHAR_LIMIT) {
        textDao.update(base.copy(rawText = body, bodyPath = null))
        bodyStore.delete(document)
    } else {
        val path = bodyStore.writeBody(document.id, body)
        textDao.update(base.copy(rawText = "", bodyPath = path))
        indexFileBody(textDao, document.id, body)
    }
}

/** Triggers index inline bodies; a body stored as a file has to be indexed here. Best effort:
 * a failure only leaves the text unsearchable by body until [indexTextBodyFiles] retries it. */
private suspend fun indexFileBody(textDao: TextDao, id: Long, body: String) {
    try {
        textDao.setSearchBody(id, body)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
    }
}

/** Indexes file-stored bodies the index hasn't seen yet (after the v16 migration, a restored
 * backup, or a failed [indexFileBody]). */
suspend fun indexTextBodyFiles(textDao: TextDao, bodyStore: TextBodyStorage) {
    textDao.getUnindexedFileBodies().forEach { doc ->
        val body = bodyStore.read(doc)
        if (body.isNotEmpty()) textDao.setSearchBody(doc.id, body)
    }
}
