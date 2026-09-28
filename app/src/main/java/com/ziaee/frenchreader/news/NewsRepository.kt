package com.ziaee.frenchreader.news

import com.ziaee.frenchreader.data.HeadlineDao
import com.ziaee.frenchreader.data.HeadlineEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.supervisorScope

const val RFI_FACILE_SOURCE_ID = "rfi_facile"
const val FRANCE_INFO_SOURCE_ID = "france_info"
const val RFI_FACILE_SOURCE_LABEL = "RFI – فرانسهٔ ساده"
const val FRANCE_INFO_SOURCE_LABEL = "France Info"

const val RFI_FACILE_FEED_URL = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
    "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
const val FRANCE_INFO_FEED_URL = "https://www.francetvinfo.fr/titres.rss"

const val DW_LANGSAM_SOURCE_ID = "dw_langsam"
const val TAGESSCHAU_SOURCE_ID = "tagesschau"
const val DEUTSCHLANDFUNK_SOURCE_ID = "deutschlandfunk"

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

/** [language] is the learning language the feed is written in; only that language's sources are fetched. */
data class NewsSource(
    val id: String,
    val label: String,
    val feedUrl: String,
    val category: NewsCategory,
    val language: String = "fr"
)

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
    NewsSource("francetv_sports", "France Info Sports", "https://www.francetvinfo.fr/sports.rss", NewsCategory.SPORT),
    // German feeds, each verified live on 2026-09-28.
    NewsSource(DW_LANGSAM_SOURCE_ID, "DW – Langsam gesprochen", "https://rss.dw.com/xml/DKpodcast_lgn_de", NewsCategory.GENERAL, "de"),
    NewsSource(TAGESSCHAU_SOURCE_ID, "Tagesschau", "https://www.tagesschau.de/index~rss2.xml", NewsCategory.GENERAL, "de"),
    NewsSource(DEUTSCHLANDFUNK_SOURCE_ID, "Deutschlandfunk", "https://www.deutschlandfunk.de/nachrichten-100.rss", NewsCategory.GENERAL, "de"),
    NewsSource("spiegel", "Der Spiegel", "https://www.spiegel.de/schlagzeilen/index.rss", NewsCategory.GENERAL, "de"),
    NewsSource("dw_de", "DW", "https://rss.dw.com/rdf/rss-de-all", NewsCategory.GENERAL, "de"),
    NewsSource("tagesschau_europa", "Tagesschau Europa", "https://www.tagesschau.de/ausland/europa/index~rss2.xml", NewsCategory.INTERNATIONAL_EUROPE, "de"),
    NewsSource("tagesschau_innenpolitik", "Tagesschau Innenpolitik", "https://www.tagesschau.de/inland/innenpolitik/index~rss2.xml", NewsCategory.POLITICS, "de"),
    NewsSource("spiegel_politik", "Spiegel Politik", "https://www.spiegel.de/politik/index.rss", NewsCategory.POLITICS, "de"),
    NewsSource("tagesschau_wirtschaft", "Tagesschau Wirtschaft", "https://www.tagesschau.de/wirtschaft/index~rss2.xml", NewsCategory.ECONOMY, "de"),
    NewsSource("spiegel_wirtschaft", "Spiegel Wirtschaft", "https://www.spiegel.de/wirtschaft/index.rss", NewsCategory.ECONOMY, "de"),
    NewsSource("heise", "heise online", "https://www.heise.de/rss/heise-atom.xml", NewsCategory.TECHNOLOGY, "de"),
    NewsSource("spiegel_netzwelt", "Spiegel Netzwelt", "https://www.spiegel.de/netzwelt/index.rss", NewsCategory.TECHNOLOGY, "de"),
    NewsSource("spektrum", "Spektrum", "https://www.spektrum.de/alias/rss/spektrum-de-rss-feed/996406", NewsCategory.SCIENCE, "de"),
    NewsSource("tagesschau_wissen", "Tagesschau Wissen", "https://www.tagesschau.de/wissen/index~rss2.xml", NewsCategory.SCIENCE, "de"),
    NewsSource("tagesschau_gesundheit", "Tagesschau Gesundheit", "https://www.tagesschau.de/wissen/gesundheit/index~rss2.xml", NewsCategory.HEALTH, "de"),
    NewsSource("spiegel_gesundheit", "Spiegel Gesundheit", "https://www.spiegel.de/gesundheit/index.rss", NewsCategory.HEALTH, "de"),
    NewsSource("spiegel_kultur", "Spiegel Kultur", "https://www.spiegel.de/kultur/index.rss", NewsCategory.CULTURE, "de"),
    NewsSource("spiegel_sport", "Spiegel Sport", "https://www.spiegel.de/sport/index.rss", NewsCategory.SPORT, "de"),
    NewsSource("dw_sport", "DW Sport", "https://rss.dw.com/rdf/rss-de-sport", NewsCategory.SPORT, "de")
)

fun newsSourcesFor(language: String): List<NewsSource> = NEWS_SOURCES.filter { it.language == language }

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
    private val enabledSourceIds: () -> Set<String> = { setOf(RFI_FACILE_SOURCE_ID, FRANCE_INFO_SOURCE_ID) },
    private val language: () -> String = { "fr" }
) {
    fun observeHeadlines(limit: Int = 200): Flow<List<HeadlineEntity>> =
        observeHeadlines(flowOf(language()), limit)

    fun observeHeadlines(languages: Flow<String>, limit: Int = 200): Flow<List<HeadlineEntity>> =
        languages.flatMapLatest { headlineDao.observeRecent(CACHE_SCAN_LIMIT, it) }.map { headlines ->
            val enabled = enabledSourceIds()
            headlines.filter { it.sourceId in enabled }.take(limit)
        }

    suspend fun refresh(force: Boolean): NewsRefreshResult {
        if (!force && !isStale()) return NewsRefreshResult(emptyList(), emptyList())

        val targetLanguage = language()
        return supervisorScope {
            val enabled = enabledSourceIds()
            val outcomes = newsSourcesFor(targetLanguage).filter { it.id in enabled }.map { source ->
                source to async { runCatching { feedClient.fetch(source.feedUrl, FETCH_LIMIT) } }
            }

            val updatedSources = mutableListOf<String>()
            val failedSources = mutableListOf<String>()
            for ((source, deferred) in outcomes) {
                deferred.await()
                    .onSuccess { items ->
                        val cachedAtMs = clock.nowMs()
                        val entities = items.map { it.toHeadlineEntity(source.id, source.label, cachedAtMs, targetLanguage) }
                        headlineDao.replaceSource(source.id, targetLanguage, entities)
                        updatedSources += source.id
                    }
                    .onFailure { failedSources += source.id }
            }
            NewsRefreshResult(updatedSources, failedSources)
        }
    }

    private suspend fun isStale(): Boolean {
        val language = language()
        val enabled = enabledSourceIds() intersect newsSourcesFor(language).mapTo(mutableSetOf()) { it.id }
        if (enabled.isEmpty()) return false
        val enabledHeadlines = headlineDao.observeRecent(CACHE_SCAN_LIMIT, language).first()
            .filter { it.sourceId in enabled }
        if (!enabledHeadlines.mapTo(mutableSetOf()) { it.sourceId }.containsAll(enabled)) return true
        val oldestSourceCacheWrite = enabledHeadlines
            .groupBy { it.sourceId }
            .values
            .minOfOrNull { sourceHeadlines -> sourceHeadlines.maxOf { it.cachedAtMs } }
            ?: return true
        return clock.nowMs() - oldestSourceCacheWrite >= STALE_THRESHOLD_MS
    }
}

private fun NewsItem.toHeadlineEntity(sourceId: String, sourceLabel: String, cachedAtMs: Long, language: String) = HeadlineEntity(
    sourceId = sourceId,
    sourceLabel = sourceLabel,
    externalId = guid,
    title = title,
    snippet = snippet,
    articleUrl = link,
    imageUrl = imageUrl,
    publishedAtMs = publishedAtMs,
    cachedAtMs = cachedAtMs,
    language = language
)
