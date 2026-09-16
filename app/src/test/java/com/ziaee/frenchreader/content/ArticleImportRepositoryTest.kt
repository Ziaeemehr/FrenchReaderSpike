package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.data.HeadlineEntity
import com.ziaee.frenchreader.data.TextDao
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.images.ArticleImageStorage
import com.ziaee.frenchreader.news.normalizeArticleUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleImportRepositoryTest {
    @Test
    fun `an already-imported headline reopens the existing document without fetching again`() = runBlocking {
        val headline = headline()
        val existing = TextDocument(
            id = 42L,
            title = "Ancien titre",
            rawText = "Ancien texte",
            externalKey = normalizedKeyFor(headline)
        )
        val textDao = FakeTextDao(mutableListOf(existing))
        val source = FakeContentSource { error("fetchArticle must not be called for a known externalKey") }
        val repository = ArticleImportRepository(textDao, listOf(source), FakeImageStorage())

        val result = repository.import(headline)

        assertEquals(ArticleImportResult.OpenExisting(42L), result)
        assertFalse(source.fetchArticleCalled)
        assertTrue(textDao.inserted.isEmpty())
    }

    @Test
    fun `a new headline is fetched, inserted, and its image persisted`() = runBlocking {
        val headline = headline()
        val textDao = FakeTextDao(mutableListOf())
        val source = FakeContentSource {
            ContentArticle(
                title = "Titre complet",
                text = "Texte complet",
                sourceUrl = headline.articleUrl,
                sourceName = headline.sourceLabel,
                author = null,
                license = null,
                publishedAtMs = headline.publishedAtMs
            )
        }
        val imageStorage = FakeImageStorage(storedPath = "text_images/1.jpg")
        val repository = ArticleImportRepository(textDao, listOf(source), imageStorage)

        val result = repository.import(headline)

        assertTrue(result is ArticleImportResult.Imported)
        assertTrue(source.fetchArticleCalled)
        val inserted = textDao.inserted.single()
        assertEquals(normalizedKeyFor(headline), inserted.externalKey)
        assertEquals("text_images/1.jpg", textDao.imagePaths[inserted.id])
    }

    private fun headline() = HeadlineEntity(
        sourceId = "rfi_facile",
        sourceLabel = "RFI",
        externalId = "guid-1",
        title = "Titre",
        snippet = "Résumé",
        articleUrl = "https://example.com/article",
        imageUrl = "https://example.com/article.jpg",
        publishedAtMs = 100L,
        cachedAtMs = 200L
    )

    private fun normalizedKeyFor(headline: HeadlineEntity) = normalizeArticleUrl(headline.articleUrl)

    private class FakeContentSource(private val onFetch: () -> ContentArticle?) : ContentSource {
        override val id: String = "rfi_facile"
        override val label: String = "RFI"
        var fetchArticleCalled = false

        override suspend fun search(query: String, limit: Int): List<ContentResult> = emptyList()

        override suspend fun fetchArticle(result: ContentResult): ContentArticle? {
            fetchArticleCalled = true
            return onFetch()
        }
    }

    private class FakeImageStorage(private val storedPath: String? = null) : ArticleImageStorage {
        override suspend fun downloadAndStore(documentId: Long, imageUrl: String): String? = storedPath
        override suspend fun delete(relativePath: String): Boolean = true
    }

    private class FakeTextDao(initial: MutableList<TextDocument>) : TextDao {
        private val documents = initial
        val inserted = mutableListOf<TextDocument>()
        val imagePaths = mutableMapOf<Long, String>()
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

        override fun observeAll(): Flow<List<TextDocument>> = flowOf(documents)
        override fun observeRecent(): Flow<List<TextDocument>> = flowOf(documents.take(5))
        override fun observeMostRecentlyAccessed(): Flow<TextDocument?> = flowOf(documents.firstOrNull())
        override fun observeMatchingTitleOrSource(query: String): Flow<List<TextDocument>> = flowOf(documents)

        override suspend fun getById(id: Long): TextDocument? = documents.find { it.id == id }

        override suspend fun findByExternalKey(externalKey: String): TextDocument? =
            documents.find { it.externalKey == externalKey }

        override suspend fun insert(text: TextDocument): Long {
            val id = nextId++
            val stored = text.copy(id = id)
            documents.add(stored)
            inserted.add(stored)
            return id
        }

        override suspend fun update(text: TextDocument) {
            val index = documents.indexOfFirst { it.id == text.id }
            if (index >= 0) documents[index] = text
        }

        override suspend fun delete(text: TextDocument) {
            documents.removeAll { it.id == text.id }
        }

        override suspend fun savePosition(id: Long, chunkIndex: Int, positionMs: Long) = Unit

        override suspend fun markAccessed(id: Long, now: Long) = Unit

        override suspend fun updateImagePath(id: Long, imagePath: String) {
            imagePaths[id] = imagePath
        }
    }
}
