package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDao
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.images.ArticleImageStorage
import com.ziaee.frenchreader.news.normalizeArticleUrl

sealed class ArticleImportResult {
    data class OpenExisting(val id: Long) : ArticleImportResult()
    data class Imported(val id: Long) : ArticleImportResult()
}

/**
 * Turns a selected Home headline into a local [TextDocument], never
 * downloading the same article twice: a headline whose normalized article
 * URL was already imported reopens that document instead of re-fetching
 * (see the design doc's duplicate-safe import requirement). Also owns
 * image-aware deletion so Home/Library never orphan a stored image file.
 */
class ArticleImportRepository(
    private val textDao: TextDao,
    private val sources: List<ContentSource>,
    private val imageStore: ArticleImageStorage
) {
    /** Returns null if no registered source matches the headline or the
     * source fails to fetch the full article; the headline preview stays
     * open so the caller can offer Retry. */
    suspend fun import(headline: HeadlineEntity): ArticleImportResult? {
        val externalKey = normalizeArticleUrl(headline.articleUrl)
        textDao.findByExternalKey(externalKey)?.let { existing ->
            return ArticleImportResult.OpenExisting(existing.id)
        }

        val source = sources.find { it.id == headline.sourceId } ?: return null
        val article = source.fetchArticle(headline.toContentResult()) ?: return null

        val id = textDao.insert(
            TextDocument(
                title = article.title,
                rawText = article.text,
                sourceUrl = article.sourceUrl,
                sourceName = article.sourceName,
                author = article.author,
                license = article.license,
                publishedAt = article.publishedAtMs,
                externalKey = externalKey
            )
        )

        persistImage(id, headline.imageUrl)
        return ArticleImportResult.Imported(id)
    }

    /** A missing/failed image is never fatal -- only a failure recording the
     * already-downloaded image's path rolls the whole import back, so the
     * library never ends up with an orphaned image file or a half-written row. */
    private suspend fun persistImage(documentId: Long, imageUrl: String?) {
        val imagePath = imageUrl?.let { runCatching { imageStore.downloadAndStore(documentId, it) }.getOrNull() }
        imagePath ?: return
        try {
            textDao.updateImagePath(documentId, imagePath)
        } catch (e: Exception) {
            imageStore.delete(imagePath)
            textDao.getById(documentId)?.let { textDao.delete(it) }
        }
    }

    /** Deletes a document and, if present, its owned image file -- the one
     * path Home/Library should use so an image is never left behind. */
    suspend fun deleteWithImage(doc: TextDocument) {
        doc.imagePath?.let { imageStore.delete(it) }
        textDao.delete(doc)
    }
}

private fun HeadlineEntity.toContentResult() = ContentResult(
    sourceId = sourceId,
    sourceLabel = sourceLabel,
    title = title,
    snippet = snippet,
    lengthHint = "خبر",
    ref = articleUrl,
    publishedAtMs = publishedAtMs
)
