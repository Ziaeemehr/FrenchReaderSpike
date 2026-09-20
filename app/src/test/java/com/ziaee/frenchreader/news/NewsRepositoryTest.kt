package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.data.HeadlineDao
import com.ziaee.frenchreader.data.HeadlineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsRepositoryTest {
    @Test
    fun `refresh caches RFI when France Info fails without clearing France Info cache`() = runBlocking {
        val franceInfoCached = headline(sourceId = "france_info", externalId = "cached-france-info")
        val dao = FakeHeadlineDao(listOf(franceInfoCached))
        val client = NewsFeedClient { url, _ ->
            if (url == FRANCE_INFO_FEED_URL) error("France Info is unavailable")
            listOf(newsItem("fresh-rfi"))
        }
        val repository = NewsRepository(dao, client, Clock { NOW_MS })

        val result = repository.refresh(force = true)

        assertEquals(listOf("rfi_facile"), result.updatedSources)
        assertEquals(listOf("france_info"), result.failedSources)
        assertEquals(listOf("rfi_facile"), dao.replacedSourceIds)
        assertTrue(dao.cached.any { it.sourceId == "france_info" && it.externalId == "cached-france-info" })
        assertEquals("fresh-rfi", dao.cached.single { it.sourceId == "rfi_facile" }.externalId)
    }

    @Test
    fun `refresh skips non-stale cache unless forced`() = runBlocking {
        val dao = FakeHeadlineDao(
            listOf(
                headline(cachedAtMs = NOW_MS),
                headline(sourceId = FRANCE_INFO_SOURCE_ID, externalId = "cached-fi", cachedAtMs = NOW_MS)
            )
        )
        val fetchedUrls = mutableListOf<String>()
        val client = NewsFeedClient { url, _ ->
            fetchedUrls += url
            listOf(newsItem(url))
        }
        val repository = NewsRepository(dao, client, Clock { NOW_MS })

        val automaticResult = repository.refresh(force = false)
        val forcedResult = repository.refresh(force = true)

        assertEquals(NewsRefreshResult(emptyList(), emptyList()), automaticResult)
        assertEquals(listOf(RFI_FACILE_FEED_URL, FRANCE_INFO_FEED_URL), fetchedUrls)
        assertEquals(listOf("rfi_facile", "france_info"), forcedResult.updatedSources)
        assertTrue(forcedResult.failedSources.isEmpty())
    }

    @Test
    fun `refresh fetches and observes only enabled sources`() = runBlocking {
        val dao = FakeHeadlineDao(
            listOf(
                headline(sourceId = RFI_FACILE_SOURCE_ID, externalId = "disabled"),
                headline(sourceId = FRANCE_INFO_SOURCE_ID, externalId = "enabled")
            )
        )
        val fetchedUrls = mutableListOf<String>()
        val repository = NewsRepository(
            dao,
            NewsFeedClient { url, _ ->
                fetchedUrls.add(url)
                listOf(newsItem("fresh"))
            },
            Clock { NOW_MS },
            enabledSourceIds = { setOf(FRANCE_INFO_SOURCE_ID) }
        )

        repository.refresh(force = true)

        assertEquals(listOf(FRANCE_INFO_FEED_URL), fetchedUrls)
        assertEquals(
            setOf(FRANCE_INFO_SOURCE_ID),
            repository.observeHeadlines().first().map { it.sourceId }.toSet()
        )
    }

    private fun newsItem(guid: String) = NewsItem(
        title = "Titre $guid",
        snippet = "Résumé $guid",
        link = "https://example.com/$guid",
        guid = guid,
        publishedAtMs = 500L,
        imageUrl = "https://example.com/$guid.jpg"
    )

    private fun headline(
        sourceId: String = "rfi_facile",
        externalId: String = "cached-rfi",
        cachedAtMs: Long = NOW_MS - 1
    ) = HeadlineEntity(
        sourceId = sourceId,
        sourceLabel = sourceId,
        externalId = externalId,
        title = "Titre $externalId",
        snippet = "Résumé $externalId",
        articleUrl = "https://example.com/$externalId",
        imageUrl = null,
        publishedAtMs = 100L,
        cachedAtMs = cachedAtMs
    )

    private class FakeHeadlineDao(initial: List<HeadlineEntity>) : HeadlineDao {
        var cached = initial.toMutableList()
        val replacedSourceIds = mutableListOf<String>()

        override fun observeRecent(limit: Int): Flow<List<HeadlineEntity>> = flowOf(cached.take(limit))

        override suspend fun insertAll(items: List<HeadlineEntity>) = Unit

        override suspend fun clearSource(sourceId: String) = Unit

        override suspend fun replaceSource(sourceId: String, items: List<HeadlineEntity>) {
            replacedSourceIds += sourceId
            cached.removeAll { it.sourceId == sourceId }
            cached.addAll(items)
        }
    }

    private companion object {
        const val NOW_MS = 1_000_000L
    }
}
