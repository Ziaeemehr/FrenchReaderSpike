package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.data.HeadlineDao
import com.ziaee.frenchreader.data.HeadlineEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope

const val RFI_FACILE_SOURCE_ID = "rfi_facile"
const val FRANCE_INFO_SOURCE_ID = "france_info"
const val RFI_FACILE_SOURCE_LABEL = "RFI – فرانسهٔ ساده"
const val FRANCE_INFO_SOURCE_LABEL = "France Info"

const val RFI_FACILE_FEED_URL = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
    "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
const val FRANCE_INFO_FEED_URL = "https://www.francetvinfo.fr/titres.rss"

private const val FETCH_LIMIT = 25
private const val CACHE_SCAN_LIMIT = 200
private const val STALE_THRESHOLD_MS = 15 * 60 * 1000L

/** Injectable seam over [NewsFetcher] so [NewsRepository] can be tested without real network calls. */
fun interface NewsFeedClient {
    suspend fun fetch(url: String, limit: Int): List<NewsItem>
}

/** Injectable seam over [System.currentTimeMillis] so cache-staleness tests are deterministic. */
fun interface Clock {
    fun nowMs(): Long
}

private val defaultFeedClient = NewsFeedClient { url, limit -> NewsFetcher.fetchItems(url, limit) }
private val systemClock = Clock { System.currentTimeMillis() }

data class NewsRefreshResult(val updatedSources: List<String>, val failedSources: List<String>)

private data class NewsSource(val id: String, val label: String, val feedUrl: String)

private val NEWS_SOURCES = listOf(
    NewsSource(RFI_FACILE_SOURCE_ID, RFI_FACILE_SOURCE_LABEL, RFI_FACILE_FEED_URL),
    NewsSource(FRANCE_INFO_SOURCE_ID, FRANCE_INFO_SOURCE_LABEL, FRANCE_INFO_FEED_URL)
)

/**
 * Coordinates RSS refreshes for the independent RFI/France Info sources and
 * exposes their merged, cached headlines. Each source's cache is only
 * replaced after that source's own fetch succeeds, so a failure on one
 * source never drops the other source's usable data (see ROADMAP.md
 * section 6 and the design doc's Refresh and Offline Behaviour section).
 */
class NewsRepository(
    private val headlineDao: HeadlineDao,
    private val feedClient: NewsFeedClient = defaultFeedClient,
    private val clock: Clock = systemClock
) {
    fun observeHeadlines(limit: Int = 20): Flow<List<HeadlineEntity>> = headlineDao.observeRecent(limit)

    suspend fun refresh(force: Boolean): NewsRefreshResult {
        if (!force && !isStale()) return NewsRefreshResult(emptyList(), emptyList())

        return supervisorScope {
            val outcomes = NEWS_SOURCES.map { source ->
                source to async { runCatching { feedClient.fetch(source.feedUrl, FETCH_LIMIT) } }
            }

            val updatedSources = mutableListOf<String>()
            val failedSources = mutableListOf<String>()
            for ((source, deferred) in outcomes) {
                deferred.await()
                    .onSuccess { items ->
                        val cachedAtMs = clock.nowMs()
                        val entities = items.map { it.toHeadlineEntity(source.id, source.label, cachedAtMs) }
                        headlineDao.replaceSource(source.id, entities)
                        updatedSources += source.id
                    }
                    .onFailure { failedSources += source.id }
            }
            NewsRefreshResult(updatedSources, failedSources)
        }
    }

    private suspend fun isStale(): Boolean {
        val mostRecentCacheWrite = headlineDao.observeRecent(CACHE_SCAN_LIMIT).first()
            .maxOfOrNull { it.cachedAtMs } ?: return true
        return clock.nowMs() - mostRecentCacheWrite >= STALE_THRESHOLD_MS
    }
}

private fun NewsItem.toHeadlineEntity(sourceId: String, sourceLabel: String, cachedAtMs: Long) = HeadlineEntity(
    sourceId = sourceId,
    sourceLabel = sourceLabel,
    externalId = guid,
    title = title,
    snippet = snippet,
    articleUrl = link,
    imageUrl = imageUrl,
    publishedAtMs = publishedAtMs,
    cachedAtMs = cachedAtMs
)
