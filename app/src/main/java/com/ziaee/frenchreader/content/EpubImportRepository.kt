package com.ziaee.frenchreader.content

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.TextBodyStorage
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.insertTextDocument
import com.ziaee.frenchreader.images.ArticleImageStorage
import com.ziaee.frenchreader.images.ArticleImageStore
import com.ziaee.frenchreader.ui.shared.queryDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class EpubImportResult(
    val folderId: Long? = null,
    val bookTitle: String,
    val firstTextId: Long?,
    val importedCount: Int,
    val skippedCount: Int
)

class EpubImportRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val imageStore: ArticleImageStorage = ArticleImageStore(context),
    private val bodyStore: TextBodyStorage = TextBodyStore(context)
) {
    suspend fun import(uri: Uri): EpubImportResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(context, uri)
        val book = context.contentResolver.openInputStream(uri)?.use { EpubReader.read(it) }
            ?: throw EpubFormatException("unable to open EPUB")
        val bookTitle = book.title.takeUnless { it.isBlank() || it == "EPUB" }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: "EPUB"
        val identifierHash = MessageDigest.getInstance("SHA-1")
            .digest(book.identifier.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        val externalKey = "epub:$identifierHash"

        db.withTransaction {
            val textDao = db.textDao()
            textDao.findByExternalKey(externalKey)?.let { existing ->
                return@withTransaction EpubImportResult(null, bookTitle, existing.id, 0, 0)
            }

            val coverPath = book.cover?.let {
                imageStore.storeBytes("text_images/epub_${identifierHash}_cover.jpg", it.bytes)
            }
            val body = book.chapters.mapIndexed { _, chapter ->
                var chapterText = chapter.text
                chapter.images.forEach { image ->
                    val relativePath = "text_images/epub_$identifierHash/${image.name}"
                    val stored = imageStore.storeBytes(relativePath, image.bytes)
                    chapterText = if (stored != null) {
                        chapterText.replace("epubimg:${image.name}", "epubimg:$identifierHash/${image.name}")
                    } else {
                        removeImageMarker(chapterText, image.name)
                    }
                }
                val firstLineTitle = chapterText.lineSequence().firstOrNull()
                    ?.removePrefix("# ")?.trim()
                if (firstLineTitle.equals(chapter.title.trim(), ignoreCase = true)) chapterText.trim()
                else "# ${chapter.title.trim()}\n\n${chapterText.trim()}"
            }.joinToString("\n\n")
            val id = insertTextDocument(
                textDao,
                bodyStore,
                TextDocument(
                    title = bookTitle,
                    rawText = "",
                    folderId = null,
                    sourceName = bookTitle,
                    externalKey = externalKey,
                    imagePath = coverPath
                ),
                body
            )
            EpubImportResult(
                null,
                bookTitle,
                id,
                book.chapters.size,
                0
            )
        }
    }

    private fun removeImageMarker(text: String, name: String): String {
        val marker = Regex("(?m)^!\\[[^]]*]\\(epubimg:${Regex.escape(name)}\\)\\s*(?:\\n\\s*\\n)?")
        return text.replace(marker, "").trim()
    }
}
