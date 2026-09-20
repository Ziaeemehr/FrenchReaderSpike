package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.data.HeadlineDao
import com.ziaee.frenchreader.data.HeadlineEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope

const val RFI_FACILE_SOURCE_ID = "rfi_facile"
const val FRANCE_INFO_SOURCE_ID = "france_info"
const val RFI_FACILE_SOURCE_LABEL = "RFI – فرانسهٔ ساده"
const val FRANCE_INFO_SOURCE_LABEL = "France Info"

const val RFI_FACILE_FEED_URL = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
    "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
const val FRANCE_INFO_FEED_URL = "https://www.francetvinfo.fr/titres.rss"

private const val FETCH_LIMIT = 50
private const val CACHE_SCAN_LIMIT = 500
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

enum class NewsCategory { GENERAL, INTERNATIONAL_EUROPE, POLITICS, ECONOMY, TECHNOLOGY, SCIENCE, HEALTH, PSYCHOLOGY, CULTURE, SPORT }

data class NewsSource(val id: String, val label: String, val feedUrl: String, val category: NewsCategory)

val NEWS_SOURCES = listOf(
    NewsSource(RFI_FACILE_SOURCE_ID, RFI_FACILE_SOURCE_LABEL, RFI_FACILE_FEED_URL, NewsCategory.GENERAL),
    NewsSource(FRANCE_INFO_SOURCE_ID, FRANCE_INFO_SOURCE_LABEL, FRANCE_INFO_FEED_URL, NewsCategory.GENERAL),
    NewsSource("france24", "France 24", "https://www.france24.com/fr/rss", NewsCategory.GENERAL),
    NewsSource("rfi", "RFI", "https://www.rfi.fr/fr/rss", NewsCategory.GENERAL),
    NewsSource("20minutes", "20 Minutes", "https://www.20minutes.fr/feeds/rss-une.xml", NewsCategory.GENERAL),
    NewsSource("france24_europe", "France 24 Europe", "https://www.france24.com/fr/europe/rss", NewsCategory.INTERNATIONAL_EUROPE),
    NewsSource("lemonde_politique", "Le Monde Politique", "https://www.lemonde.fr/politique/rss_full.xml", NewsCategory.POLITICS),
    NewsSource("francetv_politique", "France Info Politique", "https://www.francetvinfo.fr/politique.rss", NewsCategory.POLITICS),
    NewsSource("lemonde_economie", "Le Monde Économie", "https://www.lemonde.fr/economie/rss_full.xml", NewsCategory.ECONOMY),
    NewsSource("france24_economie", "France 24 Économie", "https://www.france24.com/fr/economie/rss", NewsCategory.ECONOMY),
    NewsSource("numerama", "Numerama", "https://www.numerama.com/feed/", NewsCategory.TECHNOLOGY),
    NewsSource("lemonde_pixels", "Le Monde Pixels", "https://www.lemonde.fr/pixels/rss_full.xml", NewsCategory.TECHNOLOGY),
    NewsSource("futura", "Futura", "https://www.futura-sciences.com/rss/actualites.xml", NewsCategory.SCIENCE),
    NewsSource("sciencesetavenir", "Sciences et Avenir", "https://www.sciencesetavenir.fr/rss.xml", NewsCategory.SCIENCE),
    NewsSource("lemonde_sciences", "Le Monde Sciences", "https://www.lemonde.fr/sciences/rss_full.xml", NewsCategory.SCIENCE),
    NewsSource("lemonde_sante", "Le Monde Santé", "https://www.lemonde.fr/sante/rss_full.xml", NewsCategory.HEALTH),
    NewsSource("psychologies", "Psychologies", "https://www.psychologies.com/rss", NewsCategory.PSYCHOLOGY),
    NewsSource("france24_culture", "France 24 Culture", "https://www.france24.com/fr/culture/rss", NewsCategory.CULTURE),
    NewsSource("lemonde_culture", "Le Monde Culture", "https://www.lemonde.fr/culture/rss_full.xml", NewsCategory.CULTURE),
    NewsSource("france24_sport", "France 24 Sport", "https://www.france24.com/fr/sport/rss", NewsCategory.SPORT),
    NewsSource("francetv_sports", "France Info Sports", "https://www.francetvinfo.fr/sports.rss", NewsCategory.SPORT)
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
    private val clock: Clock = systemClock,
    private val enabledSourceIds: () -> Set<String> = { setOf(RFI_FACILE_SOURCE_ID, FRANCE_INFO_SOURCE_ID) }
) {
    fun observeHeadlines(limit: Int = 200): Flow<List<HeadlineEntity>> =
        headlineDao.observeRecent(CACHE_SCAN_LIMIT).map { headlines ->
            val enabled = enabledSourceIds()
            headlines.filter { it.sourceId in enabled }.take(limit)
        }

    suspend fun refresh(force: Boolean): NewsRefreshResult {
        if (!force && !isStale()) return NewsRefreshResult(emptyList(), emptyList())

        return supervisorScope {
            val enabled = enabledSourceIds()
            val outcomes = NEWS_SOURCES.filter { it.id in enabled }.map { source ->
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
        val enabled = enabledSourceIds()
        if (enabled.isEmpty()) return false
        val enabledHeadlines = headlineDao.observeRecent(CACHE_SCAN_LIMIT).first()
            .filter { it.sourceId in enabled }
        if (!enabledHeadlines.mapTo(mutableSetOf()) { it.sourceId }.containsAll(enabled)) return true
        val mostRecentCacheWrite = enabledHeadlines
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
