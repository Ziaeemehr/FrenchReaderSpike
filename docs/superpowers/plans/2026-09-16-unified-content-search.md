# Unified Content Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the separate "دریافت خبر امروز" (RFI/France Info newspaper icon + dropdown, which only ever returns a one-sentence teaser) with a single `ContentSource` abstraction that both Vikidia and the two news sources implement, so the existing "پیدا کردن مطلب" search sheet queries all three at once and every result — Vikidia or news — opens with real, full article text.

**Architecture:** A new `content` package defines `ContentSource` (search + fetchArticle), `ContentResult`, and `ContentArticle`. `VikidiaContentSource` adapts the existing `VikidiaClient` (unchanged). `NewsContentSource` is a shared base class for `RfiFacileContentSource` and `FranceInfoContentSource`: `search()` fetches ~25 recent RSS items (via a generalized, multi-item `NewsFetcher`) and keyword-filters them client-side (RSS has no server-side search); `fetchArticle()` fetches the item's real webpage and extracts the article text with **jsoup**, using CSS selectors verified against live pages. `TextsListScreen`'s ViewModel queries all registered sources in parallel and merges results into one list; the old newspaper icon/dropdown and `NewsPrefs` (guid-based dedup) are deleted, superseded by explicit search-and-pick like Vikidia already has.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Kotlin coroutines, `org.json`, **jsoup** (new dependency — first real third-party library in this app's own logic, needed because RSS/API parsing tools aren't enough for arbitrary news-site HTML), JUnit 4.

**Spec:** `ROADMAP.md` section 6, subsection "طراحی فنی: جست‌وجوی یکپارچهٔ منابع (Vikidia + RFI + France Info)" — this plan implements that design. The rest of section 6 (Wikisource, Gutenberg, The Conversation, Gallica) stays out of scope.

## Global Constraints

- jsoup version: exactly `org.jsoup:jsoup:1.17.2`, as a normal `implementation` dependency (used at runtime, not just in tests).
- `ContentResult.ref` is an opaque per-source string: Vikidia's page id as a string, or a news item's real article `<link>` URL.
- News `ContentArticle`s always have `author = null` and `license = null` (no reliable byline in RSS, no clear reuse license for news) — only `sourceUrl`/`sourceName`/`publishedAtMs` are populated, matching ROADMAP.md's attribution policy.
- `NewsPrefs.kt` and `NewsSource.kt` are deleted in this plan — nothing may reference them after Task 5.
- Existing `ReadingScreen.kt`'s `SourceInfoSheet` (built for Vikidia) is NOT modified — it already renders whatever `sourceName`/`author`/`license`/`publishedAt` a `TextDocument` has, generically. News-imported texts will show it too (with license/author blank) automatically once Task 5 populates those fields.
- Partial source failure must not break the whole search: if one `ContentSource` throws, its results are simply omitted; the search only surfaces as a search-level error if every source fails.

---

## Task 1: Generalize `NewsFetcher` to a multi-item RSS fetcher

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt` (one line only — see Step 4b)
- Test: `app/src/test/java/com/ziaee/frenchreader/news/NewsFetcherTest.kt`

**Interfaces:**
- Produces: `data class NewsItem(val title: String, val snippet: String, val link: String, val guid: String, val publishedAtMs: Long?)`; `suspend fun NewsFetcher.fetchItems(feedUrl: String, limit: Int = 25): List<NewsItem>`; internal pure function `NewsFetcher.parseItems(xml: String, limit: Int): List<NewsItem>` used directly by the test. Task 3 calls `fetchItems`. Also keeps (unchanged signature) `sealed class NewsFetchResult` and `suspend fun NewsFetcher.fetchLatest(source: NewsSource, lastGuid: String?): NewsFetchResult` as a transitional shim over `fetchItems` — `TextsListScreen.kt`'s existing `fetchNews` still calls this until Task 5 deletes both.

Today's `NewsFetcher` only parses the FIRST `<item>` (this app's only-ever use case until now: "today's latest"). This task makes it walk up to `limit` items, and adds RSS `<pubDate>` parsing (RFC 822 format, e.g. `Wed, 16 Sep 2026 09:37:29 +0200`) since the new `ContentArticle.publishedAtMs` field wants it.

**Why the old `fetchLatest`/`NewsFetchResult` API stays for now:** `TextsListScreen.kt` (Task 5's job, not this one) still calls `NewsFetcher.fetchLatest(source, lastGuid)` and reads `NewsItem.title`/`.snippet`/`.guid` for the OLD single-item news dropdown. Deleting that API now — before Task 5 removes its only caller — would leave the whole app failing to compile from this task through Task 4, which would also break every `./gradlew testDebugUnitTest` command in Tasks 2-4 (Gradle compiles all of `app/src/main` before running any unit test, regardless of which test you target). So this task turns `fetchLatest` into a thin wrapper over the new `parseItems`/multi-item logic instead of deleting it, and Task 5 deletes it once it removes the old dropdown UI that's `fetchLatest`'s last caller. One field rename (`NewsItem.body` -> `NewsItem.snippet`, since that's what the field actually is now that full text comes from Task 4's article extraction instead) needs a matching one-line fix in `TextsListScreen.kt`'s existing `fetchNews` function — Step 4b below, nothing else in that file changes in this task.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ziaee/frenchreader/news/NewsFetcherTest.kt`:

```kotlin
package com.ziaee.frenchreader.news

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsFetcherTest {
    // Trimmed real response shape from https://www.franceinfo.fr/titres.rss
    // (redirects to franceinfo.fr) -- two items, in feed order.
    private val sampleFeedXml = """
        <?xml version="1.0"?>
        <rss xmlns:atom="http://www.w3.org/2005/Atom" version="2.0"><channel>
        <title>franceinfo - Les Titres</title>
        <item>
        <title>Discours sur l'&#xE9;tat de l'Union&#xA0;: Ursula von der Leyen</title>
        <description>La pr&#xE9;sidente de la Commission europ&#xE9;enne s'exprime &#xE0; 9 heures.</description>
        <pubDate>Wed, 16 Sep 2026 09:37:29 +0200</pubDate>
        <link>https://www.franceinfo.fr/monde/direct-discours_8193647.html#xtor=RSS-3-[lestitres]</link>
        <guid>https://www.franceinfo.fr/monde/direct-discours_8193647.html#xtor=RSS-3-[lestitres]</guid>
        </item>
        <item>
        <title>Six questions sur le proc&#xE8;s de Rachida Dati</title>
        <description>L'ancienne ministre compara&#xEE;t &#xE0; partir de mercredi.</description>
        <pubDate>Wed, 16 Sep 2026 08:47:58 +0200</pubDate>
        <link>https://www.franceinfo.fr/politique/six-questions_8193776.html#xtor=RSS-3-[lestitres]</link>
        <guid>https://www.franceinfo.fr/politique/six-questions_8193776.html#xtor=RSS-3-[lestitres]</guid>
        </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `parses multiple items in feed order`() {
        val items = NewsFetcher.parseItems(sampleFeedXml, limit = 25)

        assertEquals(2, items.size)
        assertEquals("Discours sur l'état de l'Union : Ursula von der Leyen", items[0].title)
        assertEquals(
            "https://www.franceinfo.fr/monde/direct-discours_8193647.html#xtor=RSS-3-[lestitres]",
            items[0].link
        )
        assertEquals("Six questions sur le procès de Rachida Dati", items[1].title)
    }

    @Test
    fun `respects the limit`() {
        val items = NewsFetcher.parseItems(sampleFeedXml, limit = 1)
        assertEquals(1, items.size)
    }

    @Test
    fun `parses RFC822 pubDate into epoch ms`() {
        val items = NewsFetcher.parseItems(sampleFeedXml, limit = 25)
        // 2026-09-16T09:37:29+02:00
        assertEquals(1789635449000L, items[0].publishedAtMs)
    }

    @Test
    fun `prefers content encoded over description when present`() {
        val xml = """
            <rss><channel><item>
            <title>Titre</title>
            <description>Court résumé.</description>
            <content:encoded>Contenu plus long.</content:encoded>
            <link>https://example.com/a</link>
            <guid>https://example.com/a</guid>
            </item></channel></rss>
        """.trimIndent()

        val items = NewsFetcher.parseItems(xml, limit = 25)
        assertEquals("Contenu plus long.", items[0].snippet)
    }
}
```

Note: `fetchLatest` (the transitional shim kept for `TextsListScreen.kt`'s old caller, removed in Task 5) is not separately unit-tested here — it's a thin wrapper over `parseItems`/`httpGet` with no logic of its own beyond a guid comparison, it makes a real network call so it's not a unit-test target, and it was never tested before this plan either. Testing `parseItems` (above) is what actually matters; `fetchLatest`'s only job is to keep compiling and behaving as it did before until Task 5 deletes it.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.news.NewsFetcherTest"`
Expected: FAIL — `NewsFetcher.parseItems` doesn't exist yet (compilation error), and `NewsItem` doesn't have `link`/`publishedAtMs` fields yet.

- [ ] **Step 3: Verify the pubDate epoch expectation**

Before implementing, confirm the expected epoch value in Step 1's third test is correct: `Wed, 16 Sep 2026 09:37:29 +0200` is `2026-09-16T07:37:29Z` in UTC. Compute it yourself (e.g. `date -u -d "2026-09-16T07:37:29Z" +%s` gives seconds; multiply by 1000) and confirm it matches `1789635449000L` before trusting it — if your computation differs, use YOUR verified value in the test, not the one given here.

- [ ] **Step 4: Rewrite `NewsFetcher.kt`**

Replace the entire contents of `app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt` with:

```kotlin
package com.ziaee.frenchreader.news

import android.util.Xml
import com.ziaee.frenchreader.util.HtmlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** One RSS `<item>`: a title, a short plain-text snippet (HTML stripped --
 * this is a teaser, NOT the full article; see ROADMAP.md section 6 on why
 * RSS descriptions can't be treated as full text), the item's real webpage
 * link (used to fetch the full article), a guid, and the parsed publish
 * date if the feed provides one. */
data class NewsItem(
    val title: String,
    val snippet: String,
    val link: String,
    val guid: String,
    val publishedAtMs: Long?
)

/** Transitional result type for [NewsFetcher.fetchLatest] -- the OLD
 * single-item, dedup-aware news flow that `TextsListScreen.kt`'s news
 * dropdown still calls. Deleted in Task 5 of the unified-content-search
 * plan once that dropdown is removed; do not add new callers of this. */
sealed class NewsFetchResult {
    data class NewItem(val item: NewsItem) : NewsFetchResult()
    data object NoNewItem : NewsFetchResult()
    data class Error(val message: String) : NewsFetchResult()
}

/**
 * Fetches up to [limit] items from an RSS feed, in feed order (newest
 * first, by RSS convention). Dependency-free: HttpURLConnection + Android's
 * built-in XmlPullParser, no OkHttp or RSS library.
 */
object NewsFetcher {
    suspend fun fetchItems(feedUrl: String, limit: Int = 25): List<NewsItem> = withContext(Dispatchers.IO) {
        parseItems(httpGet(feedUrl), limit)
    }

    /** Transitional: the old "today's latest, skip if already seen" flow,
     * now built on top of [fetchItems] instead of its own parsing. See
     * [NewsFetchResult]'s kdoc -- deleted in Task 5. */
    suspend fun fetchLatest(source: NewsSource, lastGuid: String?): NewsFetchResult = withContext(Dispatchers.IO) {
        try {
            val item = fetchItems(source.feedUrl, limit = 1).firstOrNull()
                ?: return@withContext NewsFetchResult.Error("فید خالی یا در قالب نامعتبر بود")
            if (lastGuid != null && item.guid == lastGuid) NewsFetchResult.NoNewItem
            else NewsFetchResult.NewItem(item)
        } catch (e: Exception) {
            NewsFetchResult.Error(e.message ?: e.toString())
        }
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Namespace processing is left off so tags like `content:encoded` show
     * up under their literal (prefixed) name, which is all this needs. */
    internal fun parseItems(xml: String, limit: Int): List<NewsItem> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        val items = mutableListOf<NewsItem>()
        var inItem = false
        var title: String? = null
        var description: String? = null
        var contentEncoded: String? = null
        var link: String? = null
        var guid: String? = null
        var pubDate: String? = null
        var currentTag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && items.size < limit) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    currentTag = name
                    if (name == "item") {
                        inItem = true
                        title = null; description = null; contentEncoded = null
                        link = null; guid = null; pubDate = null
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inItem) {
                        val text = parser.text
                        when (currentTag) {
                            "title" -> title = (title ?: "") + text
                            "description" -> description = (description ?: "") + text
                            "content:encoded" -> contentEncoded = (contentEncoded ?: "") + text
                            "link" -> link = (link ?: "") + text
                            "guid" -> guid = (guid ?: "") + text
                            "pubDate" -> pubDate = (pubDate ?: "") + text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "item" && inItem) {
                        inItem = false
                        val rawSnippet = contentEncoded?.takeIf { it.isNotBlank() } ?: description.orEmpty()
                        val cleanTitle = HtmlUtil.stripHtml(title.orEmpty())
                        val cleanSnippet = HtmlUtil.stripHtml(rawSnippet)
                        val itemLink = link?.trim().orEmpty()
                        if (cleanTitle.isNotBlank() && cleanSnippet.isNotBlank() && itemLink.isNotBlank()) {
                            items.add(
                                NewsItem(
                                    title = cleanTitle,
                                    snippet = cleanSnippet,
                                    link = itemLink,
                                    guid = (guid?.takeIf { it.isNotBlank() } ?: itemLink).trim(),
                                    publishedAtMs = pubDate?.let(::parseRfc822)
                                )
                            )
                        }
                    }
                    currentTag = null
                }
            }
            event = parser.next()
        }
        return items
    }

    private fun parseRfc822(date: String): Long? = try {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(date.trim())?.time
    } catch (e: Exception) {
        null
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.news.NewsFetcherTest"`
Expected: PASS (5 tests)

- [ ] **Step 6: Fix the one call site broken by the `body` -> `snippet` rename**

`TextsListScreen.kt`'s existing `fetchNews` function (in `TextsListViewModel`) constructs a `TextDocument` from the old `NewsItem`'s fields. Find this line (it's the only reference to the renamed field in the whole codebase):

```kotlin
                    val id = db.textDao().insert(
                        TextDocument(title = "[$dateLabel] ${item.title}", rawText = item.body)
                    )
```

and change `item.body` to `item.snippet`:

```kotlin
                    val id = db.textDao().insert(
                        TextDocument(title = "[$dateLabel] ${item.title}", rawText = item.snippet)
                    )
```

Do not change anything else in `TextsListScreen.kt` in this task — the rest of the old news dropdown (and its eventual removal) is Task 5's job.

- [ ] **Step 7: Build the whole project to confirm nothing else broke**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. This is a real compile of every file in `app/src/main`, not just the two files this task touched — it's the actual guarantee that `TextsListScreen.kt`'s still-existing news dropdown (untouched otherwise) keeps compiling against the changed `NewsFetcher.kt`/`NewsItem`. If it fails anywhere else, that's a real gap this step exists to catch — fix it before moving on, don't skip ahead assuming Task 5 will cover it.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt app/src/test/java/com/ziaee/frenchreader/news/NewsFetcherTest.kt
git commit -m "Generalize NewsFetcher to fetch multiple RSS items with pubDate"
```

---

## Task 2: `ContentSource` abstraction + `VikidiaContentSource` adapter

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/content/ContentSource.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/content/VikidiaContentSource.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/content/VikidiaContentSourceTest.kt`

**Interfaces:**
- Consumes: `VikidiaClient.search/fetchArticle/articleUrl`, `VikidiaSearchResult`, `VikidiaArticle` (existing, unchanged).
- Produces: `interface ContentSource { val id: String; val label: String; suspend fun search(query: String, limit: Int = 10): List<ContentResult>; suspend fun fetchArticle(result: ContentResult): ContentArticle? }`; `data class ContentResult(val sourceId: String, val sourceLabel: String, val title: String, val snippet: String, val lengthHint: String, val ref: String)`; `data class ContentArticle(val title: String, val text: String, val sourceUrl: String, val sourceName: String, val author: String?, val license: String?, val publishedAtMs: Long?)`; `object VikidiaContentSource : ContentSource`. Tasks 3-5 use these types.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ziaee/frenchreader/content/VikidiaContentSourceTest.kt`:

```kotlin
package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.vikidia.VikidiaArticle
import com.ziaee.frenchreader.vikidia.VikidiaSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class VikidiaContentSourceTest {
    @Test
    fun `maps a Vikidia search result to a ContentResult`() {
        val vikidiaResult = VikidiaSearchResult(pageId = 8826, title = "Espace", snippet = "dans la course à l'espace.", wordCount = 457)

        val result = VikidiaContentSource.toContentResult(vikidiaResult)

        assertEquals(ContentResult(
            sourceId = "vikidia",
            sourceLabel = "Vikidia",
            title = "Espace",
            snippet = "dans la course à l'espace.",
            lengthHint = "~457 کلمه",
            ref = "8826"
        ), result)
    }

    @Test
    fun `maps a fetched Vikidia article to a ContentArticle`() {
        val article = VikidiaArticle(title = "Espace", text = "L'espace est l'étendue.", publishedAtMs = 1774901090000L)

        val contentArticle = VikidiaContentSource.toContentArticle(article)

        assertEquals(ContentArticle(
            title = "Espace",
            text = "L'espace est l'étendue.",
            sourceUrl = "https://fr.vikidia.org/wiki/Espace",
            sourceName = "Vikidia",
            author = null,
            license = "CC BY-SA 3.0",
            publishedAtMs = 1774901090000L
        ), contentArticle)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.content.VikidiaContentSourceTest"`
Expected: FAIL — `ContentSource`, `ContentResult`, `ContentArticle`, `VikidiaContentSource` don't exist yet.

- [ ] **Step 3: Create the `ContentSource` abstraction**

Create `app/src/main/java/com/ziaee/frenchreader/content/ContentSource.kt`:

```kotlin
package com.ziaee.frenchreader.content

/** One search hit from any content source (Vikidia, a news source, and
 * whatever ROADMAP.md section 6 adds later): enough to show a result row.
 * [ref] is an opaque per-source reference passed back to [ContentSource.fetchArticle]
 * to actually download the full text -- a Vikidia page id, a news item's
 * article URL, etc. Never interpreted outside the source that produced it. */
data class ContentResult(
    val sourceId: String,
    val sourceLabel: String,
    val title: String,
    val snippet: String,
    val lengthHint: String,
    val ref: String
)

/** A fetched full article, ready to become a TextDocument -- see
 * ROADMAP.md section 6's attribution policy for what each field means
 * per source type. */
data class ContentArticle(
    val title: String,
    val text: String,
    val sourceUrl: String,
    val sourceName: String,
    val author: String?,
    val license: String?,
    val publishedAtMs: Long?
)

/** One pluggable place to find reading material -- see ROADMAP.md section 6.
 * The "پیدا کردن مطلب" search sheet queries every registered ContentSource
 * in parallel and merges the results. */
interface ContentSource {
    val id: String
    val label: String
    suspend fun search(query: String, limit: Int = 10): List<ContentResult>
    suspend fun fetchArticle(result: ContentResult): ContentArticle?
}
```

- [ ] **Step 4: Create `VikidiaContentSource`**

Create `app/src/main/java/com/ziaee/frenchreader/content/VikidiaContentSource.kt`:

```kotlin
package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.vikidia.VikidiaArticle
import com.ziaee.frenchreader.vikidia.VikidiaClient
import com.ziaee.frenchreader.vikidia.VikidiaSearchResult

/** Adapts the existing [VikidiaClient] (unchanged) to [ContentSource]. */
object VikidiaContentSource : ContentSource {
    override val id = "vikidia"
    override val label = "Vikidia"

    override suspend fun search(query: String, limit: Int): List<ContentResult> =
        VikidiaClient.search(query, limit).map(::toContentResult)

    override suspend fun fetchArticle(result: ContentResult): ContentArticle? {
        val pageId = result.ref.toIntOrNull() ?: return null
        return VikidiaClient.fetchArticle(pageId)?.let(::toContentArticle)
    }

    internal fun toContentResult(r: VikidiaSearchResult): ContentResult = ContentResult(
        sourceId = id,
        sourceLabel = label,
        title = r.title,
        snippet = r.snippet,
        lengthHint = "~${r.wordCount} کلمه",
        ref = r.pageId.toString()
    )

    internal fun toContentArticle(a: VikidiaArticle): ContentArticle = ContentArticle(
        title = a.title,
        text = a.text,
        sourceUrl = VikidiaClient.articleUrl(a.title),
        sourceName = label,
        author = null,
        license = "CC BY-SA 3.0",
        publishedAtMs = a.publishedAtMs
    )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.content.VikidiaContentSourceTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/content/ContentSource.kt app/src/main/java/com/ziaee/frenchreader/content/VikidiaContentSource.kt app/src/test/java/com/ziaee/frenchreader/content/VikidiaContentSourceTest.kt
git commit -m "Add ContentSource abstraction and VikidiaContentSource adapter"
```

---

## Task 3: `NewsContentSource` base + keyword-filtered search

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/content/NewsContentSource.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/content/NewsContentSourceTest.kt`

**Interfaces:**
- Consumes: `NewsFetcher.fetchItems`, `NewsItem` (Task 1); `ContentSource`, `ContentResult`, `ContentArticle` (Task 2).
- Produces: `abstract class NewsContentSource(id, label, feedUrl) : ContentSource` with `protected abstract fun extractArticleText(html: String): String?`; internal pure function `filterNewsItems(items: List<NewsItem>, query: String): List<NewsItem>`. Task 4 provides the `extractArticleText` implementations; Task 5 registers the two concrete subclasses (added at the end of this task, but their `extractArticleText` bodies are Task 4's job — see Step 4 below for the placeholder-free way this task handles that ordering).

Since Task 4 (the jsoup extractor) doesn't exist yet when this task runs, this task creates the two concrete subclasses (`RfiFacileContentSource`, `FranceInfoContentSource`) directly in this same file with a **real, working** (not-yet-extracting) `extractArticleText` that returns the fetched HTML's `<body>` text via a minimal same-file helper — then Task 4 replaces that helper's body with real jsoup-based extraction. This avoids an abstract method with no implementation (which wouldn't compile) while keeping Task 3 self-contained and testable on its own.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/ziaee/frenchreader/content/NewsContentSourceTest.kt`:

```kotlin
package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.news.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsContentSourceTest {
    private val items = listOf(
        NewsItem(
            title = "Discours sur l'état de l'Union",
            snippet = "Ursula von der Leyen s'exprime à Strasbourg.",
            link = "https://example.com/a",
            guid = "https://example.com/a",
            publishedAtMs = 1L
        ),
        NewsItem(
            title = "Six questions sur le procès de Rachida Dati",
            snippet = "L'ancienne ministre comparaît mercredi.",
            link = "https://example.com/b",
            guid = "https://example.com/b",
            publishedAtMs = 2L
        )
    )

    @Test
    fun `matches query against title case-insensitively`() {
        val result = filterNewsItems(items, "union")
        assertEquals(listOf("Discours sur l'état de l'Union"), result.map { it.title })
    }

    @Test
    fun `matches query against snippet too`() {
        val result = filterNewsItems(items, "Strasbourg")
        assertEquals(listOf("Discours sur l'état de l'Union"), result.map { it.title })
    }

    @Test
    fun `blank query returns everything unfiltered, in order`() {
        val result = filterNewsItems(items, "")
        assertEquals(2, result.size)
        assertEquals(items[0].title, result[0].title)
    }

    @Test
    fun `no match returns empty list`() {
        val result = filterNewsItems(items, "cricket")
        assertEquals(emptyList<NewsItem>(), result)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.content.NewsContentSourceTest"`
Expected: FAIL — `filterNewsItems` doesn't exist yet.

- [ ] **Step 3: Create `NewsContentSource.kt`**

Create `app/src/main/java/com/ziaee/frenchreader/content/NewsContentSource.kt`:

```kotlin
package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.news.NewsFetcher
import com.ziaee.frenchreader.news.NewsItem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** How many recent RSS items to pull before keyword-filtering -- RSS has no
 * server-side search, so this app fetches a window of recent items and
 * filters client-side. See ROADMAP.md section 6. */
private const val RECENT_ITEMS_WINDOW = 25

/** Matches [query] against each item's title or snippet, case-insensitively.
 * A blank query matches everything (used when a source should just show
 * its most recent items). Internal + top-level so it's testable without an
 * Android runtime, same pattern as VikidiaClient.parseSearchResults. */
internal fun filterNewsItems(items: List<NewsItem>, query: String): List<NewsItem> {
    val q = query.trim()
    if (q.isBlank()) return items
    return items.filter { it.title.contains(q, ignoreCase = true) || it.snippet.contains(q, ignoreCase = true) }
}

/** Shared implementation for an RSS-backed news [ContentSource]: search
 * pulls a window of recent items and keyword-filters them (real search,
 * unlike the old single-item "latest only" news feature); fetchArticle
 * downloads the item's real webpage and hands it to [extractArticleText]
 * for site-specific full-text extraction (see subclasses). */
abstract class NewsContentSource(
    override val id: String,
    override val label: String,
    private val feedUrl: String
) : ContentSource {

    override suspend fun search(query: String, limit: Int): List<ContentResult> {
        val items = NewsFetcher.fetchItems(feedUrl, limit = RECENT_ITEMS_WINDOW)
        return filterNewsItems(items, query).take(limit).map { item ->
            ContentResult(
                sourceId = id,
                sourceLabel = label,
                title = item.title,
                snippet = item.snippet,
                lengthHint = "خبر",
                ref = item.link
            )
        }
    }

    override suspend fun fetchArticle(result: ContentResult): ContentArticle? {
        val html = httpGetHtml(result.ref)
        val text = extractArticleText(html) ?: return null
        return ContentArticle(
            title = result.title,
            text = text,
            sourceUrl = result.ref,
            sourceName = label,
            author = null,
            license = null,
            publishedAtMs = null
        )
    }

    /** Site-specific full-text extraction from a fetched article page's raw
     * HTML, or null if the expected structure isn't found. */
    protected abstract fun extractArticleText(html: String): String?

    private fun httpGetHtml(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
        connection.setRequestProperty("Accept", "text/html")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** RFI's "Journal en français facile" -- see ROADMAP.md section 6.
 * [extractArticleText] is a placeholder until Task 4 replaces it with real
 * jsoup-based extraction from `div.m-transcription__content`; for now it
 * returns null (fetchArticle fails cleanly) so this task compiles and its
 * own tests (which only exercise filterNewsItems) pass without jsoup. */
object RfiFacileContentSource : NewsContentSource(
    id = "rfi_facile",
    label = "RFI – فرانسهٔ ساده",
    feedUrl = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
        "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
) {
    override fun extractArticleText(html: String): String? = null
}

/** France Info's general headlines -- see ROADMAP.md section 6.
 * [extractArticleText] is a placeholder until Task 4; see [RfiFacileContentSource]'s
 * kdoc for why that's fine at this point in the plan. */
object FranceInfoContentSource : NewsContentSource(
    id = "france_info",
    label = "France Info",
    feedUrl = "https://www.francetvinfo.fr/titres.rss"
) {
    override fun extractArticleText(html: String): String? = null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.content.NewsContentSourceTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/content/NewsContentSource.kt app/src/test/java/com/ziaee/frenchreader/content/NewsContentSourceTest.kt
git commit -m "Add NewsContentSource base with keyword-filtered RSS search"
```

---

## Task 4: `jsoup`-based full-text extraction for France Info and RFI Facile

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/java/com/ziaee/frenchreader/content/NewsContentSource.kt:` the two `extractArticleText` placeholder bodies
- Test: `app/src/test/java/com/ziaee/frenchreader/content/ArticleExtractorTest.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/content/ArticleExtractor.kt`

**Interfaces:**
- Produces: `object ArticleExtractor { fun extractFranceInfoArticle(html: String): String?; fun extractRfiFacileTranscript(html: String): String? }`. `RfiFacileContentSource`/`FranceInfoContentSource` (Task 3) call these.

The selectors below were verified against real, live-fetched pages (not guessed): France Info's `div.c-body` contains the article `<p>`/`<h2>` text but ALSO embeds "À lire aussi" related-article cards inside nested `.media-embed` divs, which must be stripped before extracting text or they'd pollute the article with unrelated card titles. RFI Facile's `div.m-transcription__content p` is the actual synced transcript, already clean. jsoup's `Element.text()` normalizes internal whitespace on its own (collapses runs of spaces/newlines) — no extra whitespace cleanup needed on top of it.

- [ ] **Step 1: Add the jsoup dependency**

Edit `app/build.gradle`, inside the existing `dependencies { ... }` block, add (near the Room dependencies):

```groovy
    implementation 'org.jsoup:jsoup:1.17.2'
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/ziaee/frenchreader/content/ArticleExtractorTest.kt`:

```kotlin
package com.ziaee.frenchreader.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleExtractorTest {
    // Trimmed but structurally real shape of a franceinfo.fr article page:
    // the body text lives in div.c-body, which also embeds an "À lire aussi"
    // related-article card inside a nested .media-embed div that must NOT
    // end up in the extracted text.
    private val franceInfoHtml = """
        <html><body>
        <div class="c-body"><p>Première phrase de l'article.</p>
        <div class="media-embed">
          <span class="media-embed__title">À lire aussi</span>
          <div class="media-embed__wrapper">
            <article class="card-article-list-xs"><a href="/x"><p class="card-article-list-xs__title">Un article lié</p></a></article>
          </div>
        </div>
        <p>Deuxième phrase, après le lien.</p><h2 class="number"><span>1</span> <span>Une question ?</span></h2><p>Réponse à la question.</p>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `extracts France Info article paragraphs and headings, excluding related-article embeds`() {
        val text = ArticleExtractor.extractFranceInfoArticle(franceInfoHtml)

        assertEquals(
            "Première phrase de l'article.\n\nDeuxième phrase, après le lien.\n\n1 Une question ?\n\nRéponse à la question.",
            text
        )
    }

    @Test
    fun `returns null for France Info when c-body is missing`() {
        assertNull(ArticleExtractor.extractFranceInfoArticle("<html><body><p>no body div here</p></body></html>"))
    }

    // Trimmed but structurally real shape of a francaisfacile.rfi.fr episode
    // page: the synced transcript lives in div.m-transcription__content,
    // as plain <p> tags -- already clean, no embeds to strip.
    private val rfiHtml = """
        <html><body>
        <div class="m-transcription__content">
          <div class="m-box-expand">
            <div class="m-box-expand__content">
              <p>
                Bonjour  à  toutes  et  à  tous.
              </p><p>
                Le  Journal en  français  facile.
              </p>
            </div>
          </div>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `extracts RFI Facile transcript paragraphs`() {
        val text = ArticleExtractor.extractRfiFacileTranscript(rfiHtml)

        assertEquals("Bonjour à toutes et à tous.\n\nLe Journal en français facile.", text)
    }

    @Test
    fun `returns null for RFI Facile when transcription content is missing`() {
        assertNull(ArticleExtractor.extractRfiFacileTranscript("<html><body><p>no transcript here</p></body></html>"))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.content.ArticleExtractorTest"`
Expected: FAIL — `ArticleExtractor` doesn't exist yet (compilation error).

- [ ] **Step 4: Create `ArticleExtractor.kt`**

Create `app/src/main/java/com/ziaee/frenchreader/content/ArticleExtractor.kt`:

```kotlin
package com.ziaee.frenchreader.content

import org.jsoup.Jsoup

/** Site-specific full-article extraction for the two news sources this app
 * knows about -- see ROADMAP.md section 6. Selectors verified against real,
 * live-fetched pages, not guessed; if either site redesigns, these will
 * need updating (the standard risk of any HTML scraper). */
object ArticleExtractor {
    fun extractFranceInfoArticle(html: String): String? {
        val body = Jsoup.parse(html).selectFirst("div.c-body") ?: return null
        body.select(".media-embed").remove()
        val text = body.select("p, h2, h3").joinToString("\n\n") { it.text() }.trim()
        return text.ifBlank { null }
    }

    fun extractRfiFacileTranscript(html: String): String? {
        val paragraphs = Jsoup.parse(html).select("div.m-transcription__content p")
        val text = paragraphs.joinToString("\n\n") { it.text() }.trim()
        return text.ifBlank { null }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.content.ArticleExtractorTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: Wire the two `NewsContentSource` subclasses to real extraction**

In `app/src/main/java/com/ziaee/frenchreader/content/NewsContentSource.kt`. No new import is needed — `ArticleExtractor` is in the same `com.ziaee.frenchreader.content` package as this file, and same-package references never need an import in Kotlin.

1. Replace:

```kotlin
object RfiFacileContentSource : NewsContentSource(
    id = "rfi_facile",
    label = "RFI – فرانسهٔ ساده",
    feedUrl = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
        "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
) {
    override fun extractArticleText(html: String): String? = null
}
```

with:

```kotlin
object RfiFacileContentSource : NewsContentSource(
    id = "rfi_facile",
    label = "RFI – فرانسهٔ ساده",
    feedUrl = "https://apis.fle.rfi.fr/products/get_product/fle_getpodcast_by_nid_author_rfi" +
        "?token_application=applepodcast_fle&program.entrepriseId=WBMZ39-FLE-FR-20220627"
) {
    override fun extractArticleText(html: String): String? = ArticleExtractor.extractRfiFacileTranscript(html)
}
```

2. Replace:

```kotlin
object FranceInfoContentSource : NewsContentSource(
    id = "france_info",
    label = "France Info",
    feedUrl = "https://www.francetvinfo.fr/titres.rss"
) {
    override fun extractArticleText(html: String): String? = null
}
```

with:

```kotlin
object FranceInfoContentSource : NewsContentSource(
    id = "france_info",
    label = "France Info",
    feedUrl = "https://www.francetvinfo.fr/titres.rss"
) {
    override fun extractArticleText(html: String): String? = ArticleExtractor.extractFranceInfoArticle(html)
}
```

Also update the two kdoc comments above these objects (the ones saying "placeholder until Task 4") to remove that note, since it's no longer true.

- [ ] **Step 7: Run the full content package's tests to confirm nothing broke**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.content.*"`
Expected: PASS (all tests across `VikidiaContentSourceTest`, `NewsContentSourceTest`, `ArticleExtractorTest`)

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle app/src/main/java/com/ziaee/frenchreader/content/ArticleExtractor.kt app/src/main/java/com/ziaee/frenchreader/content/NewsContentSource.kt app/src/test/java/com/ziaee/frenchreader/content/ArticleExtractorTest.kt
git commit -m "Add jsoup-based full-text extraction for France Info and RFI Facile"
```

---

## Task 5: Wire unified multi-source search into `TextsListScreen`, remove the old news UI

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt` (remove the transitional shim from Task 1 — see Step 6)
- Delete: `app/src/main/java/com/ziaee/frenchreader/data/NewsPrefs.kt`
- Delete: `app/src/main/java/com/ziaee/frenchreader/news/NewsSource.kt`

**Interfaces:**
- Consumes: `ContentSource`, `ContentResult`, `ContentArticle` (Task 2); `VikidiaContentSource`, `RfiFacileContentSource`, `FranceInfoContentSource` (Tasks 2-4).
- Produces: nothing consumed by later tasks — this is the last task in the plan.

No automated UI test — same rationale as the Vikidia plan's UI tasks (no Compose UI test infra in this project). Verification is a build + manual on-device check, per Step 6.

- [ ] **Step 1: Replace the news/Vikidia-specific ViewModel state with generic content-search state**

In `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`, replace these imports:

```kotlin
import com.ziaee.frenchreader.data.NewsPrefs
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.news.NewsFetchResult
import com.ziaee.frenchreader.news.NewsFetcher
import com.ziaee.frenchreader.news.NewsSource
import com.ziaee.frenchreader.vikidia.VikidiaArticle
import com.ziaee.frenchreader.vikidia.VikidiaClient
import com.ziaee.frenchreader.vikidia.VikidiaSearchResult
```

with:

```kotlin
import com.ziaee.frenchreader.content.ContentArticle
import com.ziaee.frenchreader.content.ContentResult
import com.ziaee.frenchreader.content.ContentSource
import com.ziaee.frenchreader.content.FranceInfoContentSource
import com.ziaee.frenchreader.content.RfiFacileContentSource
import com.ziaee.frenchreader.content.VikidiaContentSource
import com.ziaee.frenchreader.data.TextDocument
```

Also add these two imports (needed for the parallel search in Step 2):

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
```

- [ ] **Step 2: Replace the sealed UI-state classes and ViewModel body**

Replace the entire `NewsFetchUiState` and `VikidiaSearchUiState` sealed classes:

```kotlin
/** Result of the last "دریافت خبر امروز" tap, surfaced as a Snackbar (or a
 * loading spinner while in flight) -- see [TextsListViewModel.fetchNews]. */
sealed class NewsFetchUiState {
    data object Idle : NewsFetchUiState()
    data object Loading : NewsFetchUiState()
    data class NoNewItem(val source: NewsSource) : NewsFetchUiState()
    data class FetchError(val source: NewsSource, val message: String) : NewsFetchUiState()
}

/** State of the "پیدا کردن مطلب" (Vikidia search) bottom sheet -- see
 * ROADMAP.md section 6. [Importing] tracks which result is being
 * downloaded so its row can show a spinner without blocking the rest of
 * the list. */
sealed class VikidiaSearchUiState {
    data object Idle : VikidiaSearchUiState()
    data object Searching : VikidiaSearchUiState()
    data class Results(val items: List<VikidiaSearchResult>) : VikidiaSearchUiState()
    data object NoResults : VikidiaSearchUiState()
    data class Error(val message: String) : VikidiaSearchUiState()
}
```

with:

```kotlin
/** State of the "پیدا کردن مطلب" search sheet -- see ROADMAP.md section 6.
 * [Importing] tracks which result (by [ContentResult.ref]) is being
 * downloaded so its row can show a spinner without blocking the rest of
 * the list. */
sealed class ContentSearchUiState {
    data object Idle : ContentSearchUiState()
    data object Searching : ContentSearchUiState()
    data class Results(val items: List<ContentResult>) : ContentSearchUiState()
    data object NoResults : ContentSearchUiState()
    data class Error(val message: String) : ContentSearchUiState()
}

/** Every registered content source -- see ROADMAP.md section 6. Adding a
 * future source (Wikisource, Gutenberg, ...) means implementing
 * ContentSource and adding it here; nothing else in this file changes. */
private val CONTENT_SOURCES: List<ContentSource> = listOf(VikidiaContentSource, RfiFacileContentSource, FranceInfoContentSource)
```

Then, inside `TextsListViewModel`, replace:

```kotlin
    var newsFetchState by mutableStateOf<NewsFetchUiState>(NewsFetchUiState.Idle)
        private set

    var vikidiaSearchState by mutableStateOf<VikidiaSearchUiState>(VikidiaSearchUiState.Idle)
        private set
    var vikidiaImportingPageId by mutableStateOf<Int?>(null)
        private set
    var vikidiaImportError by mutableStateOf<String?>(null)
        private set
```

with:

```kotlin
    var contentSearchState by mutableStateOf<ContentSearchUiState>(ContentSearchUiState.Idle)
        private set
    var contentImportingRef by mutableStateOf<String?>(null)
        private set
    var contentImportError by mutableStateOf<String?>(null)
        private set
```

- [ ] **Step 3: Replace `fetchNews`/`dismissNewsMessage`/`searchVikidia`/`resetVikidiaSearch`/`dismissVikidiaSearchError`/`dismissVikidiaImportError`/`importVikidiaArticle`**

Delete this entire block from `TextsListViewModel`:

```kotlin
    /**
     * Fetches [source]'s latest RSS item and, if it's new (see
     * [NewsPrefs]'s per-source guid tracking), creates a text from it and
     * opens it via [onOpen] -- exactly like opening a text imported from
     * Share/file. See ROADMAP.md section 1.
     */
    fun fetchNews(source: NewsSource, onOpen: (Long) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            newsFetchState = NewsFetchUiState.Loading
            NewsPrefs.setLastSourceId(context, source.id)
            val lastGuid = NewsPrefs.getLastImportedGuid(context, source.id)
            when (val result = NewsFetcher.fetchLatest(source, lastGuid)) {
                is NewsFetchResult.NewItem -> {
                    val item = result.item
                    val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val id = db.textDao().insert(
                        TextDocument(title = "[$dateLabel] ${item.title}", rawText = item.snippet)
                    )
                    NewsPrefs.setLastImportedGuid(context, source.id, item.guid)
                    newsFetchState = NewsFetchUiState.Idle
                    onOpen(id)
                }
                NewsFetchResult.NoNewItem -> newsFetchState = NewsFetchUiState.NoNewItem(source)
                is NewsFetchResult.Error -> newsFetchState = NewsFetchUiState.FetchError(source, result.message)
            }
        }
    }

    fun dismissNewsMessage() {
        newsFetchState = NewsFetchUiState.Idle
    }

    fun searchVikidia(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            vikidiaSearchState = VikidiaSearchUiState.Searching
            vikidiaSearchState = try {
                val results = VikidiaClient.search(query)
                if (results.isEmpty()) VikidiaSearchUiState.NoResults else VikidiaSearchUiState.Results(results)
            } catch (e: Exception) {
                VikidiaSearchUiState.Error(e.message ?: e.toString())
            }
        }
    }

    fun resetVikidiaSearch() {
        vikidiaSearchState = VikidiaSearchUiState.Idle
        vikidiaImportError = null
    }

    /** Search failures go to a Snackbar (like [fetchNews]'s errors), not
     * inline in the sheet -- called after the Snackbar has been shown. */
    fun dismissVikidiaSearchError() {
        if (vikidiaSearchState is VikidiaSearchUiState.Error) vikidiaSearchState = VikidiaSearchUiState.Idle
    }

    fun dismissVikidiaImportError() {
        vikidiaImportError = null
    }

    /** Fetches [result]'s full article and, on success, creates a
     * TextDocument with Vikidia's attribution fields and opens it via
     * [onOpen] -- same "create then navigate" pattern as [fetchNews]. */
    fun importVikidiaArticle(result: VikidiaSearchResult, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            vikidiaImportingPageId = result.pageId
            vikidiaImportError = null
            val article: VikidiaArticle? = try {
                VikidiaClient.fetchArticle(result.pageId)
            } catch (e: Exception) {
                vikidiaImportError = e.message ?: e.toString()
                null
            }
            vikidiaImportingPageId = null
            if (article == null) {
                if (vikidiaImportError == null) vikidiaImportError = "دریافت مقاله ممکن نشد"
                return@launch
            }
            val id = db.textDao().insert(
                TextDocument(
                    title = article.title,
                    rawText = article.text,
                    sourceUrl = VikidiaClient.articleUrl(article.title),
                    sourceName = "Vikidia",
                    author = null,
                    license = "CC BY-SA 3.0",
                    publishedAt = article.publishedAtMs
                )
            )
            resetVikidiaSearch()
            onOpen(id)
        }
    }
```

Replace it with:

```kotlin
    /** Queries every registered [ContentSource] in parallel and merges the
     * results into one list. A source that throws is simply omitted from
     * the results -- one source failing (e.g. no internet reaching one
     * site) shouldn't hide results the others found. Only if EVERY source
     * fails does this surface as an error. */
    fun searchContent(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            contentSearchState = ContentSearchUiState.Searching
            contentSearchState = try {
                val perSource = coroutineScope {
                    CONTENT_SOURCES.map { source ->
                        async {
                            try {
                                source.search(query)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }.map { it.await() }
                }
                if (perSource.all { it == null }) {
                    ContentSearchUiState.Error("هیچ منبعی در دسترس نبود")
                } else {
                    val merged = perSource.filterNotNull().flatten()
                    if (merged.isEmpty()) ContentSearchUiState.NoResults else ContentSearchUiState.Results(merged)
                }
            } catch (e: Exception) {
                ContentSearchUiState.Error(e.message ?: e.toString())
            }
        }
    }

    fun resetContentSearch() {
        contentSearchState = ContentSearchUiState.Idle
        contentImportError = null
    }

    /** Search failures go to a Snackbar, not inline in the sheet -- called
     * after the Snackbar has been shown. */
    fun dismissContentSearchError() {
        if (contentSearchState is ContentSearchUiState.Error) contentSearchState = ContentSearchUiState.Idle
    }

    fun dismissContentImportError() {
        contentImportError = null
    }

    /** Fetches [result]'s full article from whichever source produced it
     * and, on success, creates a TextDocument with that source's
     * attribution and opens it via [onOpen]. */
    fun importContent(result: ContentResult, onOpen: (Long) -> Unit) {
        viewModelScope.launch {
            contentImportingRef = result.ref
            contentImportError = null
            val source = CONTENT_SOURCES.find { it.id == result.sourceId }
            val article: ContentArticle? = try {
                source?.fetchArticle(result)
            } catch (e: Exception) {
                contentImportError = e.message ?: e.toString()
                null
            }
            contentImportingRef = null
            if (article == null) {
                if (contentImportError == null) contentImportError = "دریافت مقاله ممکن نشد"
                return@launch
            }
            val id = db.textDao().insert(
                TextDocument(
                    title = article.title,
                    rawText = article.text,
                    sourceUrl = article.sourceUrl,
                    sourceName = article.sourceName,
                    author = article.author,
                    license = article.license,
                    publishedAt = article.publishedAtMs
                )
            )
            resetContentSearch()
            onOpen(id)
        }
    }
```

- [ ] **Step 4: Remove the news icon/dropdown from the `TopAppBar`, and the news Snackbar effect**

In the `TextsListScreen` composable, delete this `LaunchedEffect` block:

```kotlin
    // Surfaces the outcome of "دریافت خبر امروز" as a one-off Snackbar; a
    // successful fetch instead navigates straight to the new text (handled
    // in the onClick below), so there's nothing to show for that case here.
    LaunchedEffect(vm.newsFetchState) {
        when (val s = vm.newsFetchState) {
            is NewsFetchUiState.NoNewItem -> {
                snackbarHostState.showSnackbar("خبر جدیدی از «${s.source.label}» منتشر نشده")
                vm.dismissNewsMessage()
            }
            is NewsFetchUiState.FetchError -> {
                snackbarHostState.showSnackbar("دریافت خبر از «${s.source.label}» ممکن نشد: ${s.message}")
                vm.dismissNewsMessage()
            }
            else -> {}
        }
    }
```

Replace the two `LaunchedEffect` blocks right after it:

```kotlin
    LaunchedEffect(vm.vikidiaSearchState) {
        val s = vm.vikidiaSearchState
        if (s is VikidiaSearchUiState.Error) {
            snackbarHostState.showSnackbar("جست‌وجو در Vikidia ممکن نشد: ${s.message}")
            vm.dismissVikidiaSearchError()
        }
    }
    LaunchedEffect(vm.vikidiaImportError) {
        vm.vikidiaImportError?.let { message ->
            snackbarHostState.showSnackbar("دریافت مقاله ممکن نشد: $message")
            vm.dismissVikidiaImportError()
        }
    }
```

with:

```kotlin
    LaunchedEffect(vm.contentSearchState) {
        val s = vm.contentSearchState
        if (s is ContentSearchUiState.Error) {
            snackbarHostState.showSnackbar("جست‌وجو ممکن نشد: ${s.message}")
            vm.dismissContentSearchError()
        }
    }
    LaunchedEffect(vm.contentImportError) {
        vm.contentImportError?.let { message ->
            snackbarHostState.showSnackbar("دریافت مقاله ممکن نشد: $message")
            vm.dismissContentImportError()
        }
    }
```

Delete the `var newsMenuExpanded by remember { mutableStateOf(false) }` line.

In the `TopAppBar`'s `actions = { ... }` block, delete the entire news `Box { ... }` block:

```kotlin
                    Box {
                        IconButton(
                            onClick = { newsMenuExpanded = true },
                            enabled = vm.newsFetchState !is NewsFetchUiState.Loading
                        ) {
                            if (vm.newsFetchState is NewsFetchUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Newspaper, contentDescription = "دریافت خبر امروز")
                            }
                        }
                        DropdownMenu(
                            expanded = newsMenuExpanded,
                            onDismissRequest = { newsMenuExpanded = false }
                        ) {
                            NewsSource.entries.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.label) },
                                    onClick = {
                                        newsMenuExpanded = false
                                        vm.fetchNews(source) { id -> onOpenText(id) }
                                    }
                                )
                            }
                        }
                    }
```

leaving just the search `IconButton` (unchanged) followed directly by the file-import `IconButton`.

- [ ] **Step 5: Update the `FindArticleSheet` invocation and composable to use `ContentResult`**

Replace:

```kotlin
    if (showVikidiaSheet) {
        FindArticleSheet(
            searchState = vm.vikidiaSearchState,
            importingPageId = vm.vikidiaImportingPageId,
            onSearch = { vm.searchVikidia(it) },
            onSelect = { result -> vm.importVikidiaArticle(result) { id -> onOpenText(id) } },
            onDismiss = { showVikidiaSheet = false; vm.resetVikidiaSearch() }
        )
    }
```

with:

```kotlin
    if (showVikidiaSheet) {
        FindArticleSheet(
            searchState = vm.contentSearchState,
            importingRef = vm.contentImportingRef,
            onSearch = { vm.searchContent(it) },
            onSelect = { result -> vm.importContent(result) { id -> onOpenText(id) } },
            onDismiss = { showVikidiaSheet = false; vm.resetContentSearch() }
        )
    }
```

(`showVikidiaSheet`'s variable name stays as-is -- it's the sheet's visibility flag, not source-specific; renaming it is optional polish, not required.)

Then replace the whole `FindArticleSheet` composable at the end of the file:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindArticleSheet(
    searchState: VikidiaSearchUiState,
    importingPageId: Int?,
    onSearch: (String) -> Unit,
    onSelect: (VikidiaSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("پیدا کردن مطلب (Vikidia)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("موضوع را بنویسید") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onSearch(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "جست‌وجو")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Search failures surface as a Snackbar (handled by the caller,
            // TextsListScreen), so there's no error branch to render here --
            // by the time this recomposes, the state's already back to Idle.
            when (searchState) {
                VikidiaSearchUiState.Idle -> {}
                VikidiaSearchUiState.Searching -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                VikidiaSearchUiState.NoResults -> {
                    Text("نتیجه‌ای برای «$query» پیدا نشد.")
                }
                is VikidiaSearchUiState.Error -> {}
                is VikidiaSearchUiState.Results -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(searchState.items, key = { it.pageId }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = importingPageId == null) { onSelect(result) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        result.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                    Text("~${result.wordCount} کلمه", style = MaterialTheme.typography.labelSmall)
                                }
                                if (importingPageId == result.pageId) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
```

with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindArticleSheet(
    searchState: ContentSearchUiState,
    importingRef: String?,
    onSearch: (String) -> Unit,
    onSelect: (ContentResult) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("پیدا کردن مطلب", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("موضوع را بنویسید") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onSearch(query) }) {
                        Icon(Icons.Default.Search, contentDescription = "جست‌وجو")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Search failures surface as a Snackbar (handled by the caller,
            // TextsListScreen), so there's no error branch to render here --
            // by the time this recomposes, the state's already back to Idle.
            when (searchState) {
                ContentSearchUiState.Idle -> {}
                ContentSearchUiState.Searching -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                ContentSearchUiState.NoResults -> {
                    Text("نتیجه‌ای برای «$query» پیدا نشد.")
                }
                is ContentSearchUiState.Error -> {}
                is ContentSearchUiState.Results -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(searchState.items, key = { it.ref }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = importingRef == null) { onSelect(result) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        result.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2
                                    )
                                    Text("${result.sourceLabel} · ${result.lengthHint}", style = MaterialTheme.typography.labelSmall)
                                }
                                if (importingRef == result.ref) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Delete `NewsPrefs.kt`, `NewsSource.kt`, and the now-dead `NewsFetchResult`/`fetchLatest` shim**

```bash
git rm app/src/main/java/com/ziaee/frenchreader/data/NewsPrefs.kt app/src/main/java/com/ziaee/frenchreader/news/NewsSource.kt
```

In `app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt`, delete the `NewsFetchResult` sealed class (added in Task 1 as a transitional shim, its kdoc says exactly this) and the `fetchLatest` function:

```kotlin
/** Transitional result type for [NewsFetcher.fetchLatest] -- the OLD
 * single-item, dedup-aware news flow that `TextsListScreen.kt`'s news
 * dropdown still calls. Deleted in Task 5 of the unified-content-search
 * plan once that dropdown is removed; do not add new callers of this. */
sealed class NewsFetchResult {
    data class NewItem(val item: NewsItem) : NewsFetchResult()
    data object NoNewItem : NewsFetchResult()
    data class Error(val message: String) : NewsFetchResult()
}
```

and:

```kotlin
    /** Transitional: the old "today's latest, skip if already seen" flow,
     * now built on top of [fetchItems] instead of its own parsing. See
     * [NewsFetchResult]'s kdoc -- deleted in Task 5. */
    suspend fun fetchLatest(source: NewsSource, lastGuid: String?): NewsFetchResult = withContext(Dispatchers.IO) {
        try {
            val item = fetchItems(source.feedUrl, limit = 1).firstOrNull()
                ?: return@withContext NewsFetchResult.Error("فید خالی یا در قالب نامعتبر بود")
            if (lastGuid != null && item.guid == lastGuid) NewsFetchResult.NoNewItem
            else NewsFetchResult.NewItem(item)
        } catch (e: Exception) {
            NewsFetchResult.Error(e.message ?: e.toString())
        }
    }
```

Leave `fetchItems`, `parseItems`, `httpGet`, and `parseRfc822` exactly as they are — only the two blocks above are removed. (`NewsSource` was this function's only reference to that now-deleted type, so removing it also clears the last reason `NewsFetcher.kt` needed `NewsSource.kt` to exist — confirm this with the grep in Step 7 rather than assuming.)

- [ ] **Step 7: Build and confirm no dangling references**

Run: `grep -rn "NewsFetchResult\|fetchLatest\|NewsSource\|NewsPrefs" app/src/main/java --include="*.kt"`
Expected: no matches at all, anywhere. If anything shows up, it's a leftover reference this task's earlier steps should have removed — find and fix it before proceeding.

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If it fails on an unused-but-still-imported symbol (e.g. `SimpleDateFormat`/`Locale`/`Date` were used by the deleted `fetchNews` for a date-labeled title -- check whether `TextRow`'s date formatting still needs them before removing the `java.text.SimpleDateFormat`/`java.util.*` imports; it does, at `TextsListScreen.kt`'s `TextRow` composable and `AddTextDialog`'s unrelated code -- so those two imports stay), fix the specific dangling reference and re-run.

- [ ] **Step 8: Build, install, and manually verify on device**

Run: `./gradlew assembleDebug installDebug`

Install and exercise the app:
1. Tap the search icon — expect the sheet titled "پیدا کردن مطلب" (no longer "(Vikidia)").
2. Search a current-events topic (e.g. a word you know is in the news, or a general term like "France") — expect results from BOTH Vikidia and news sources mixed in one list, each row showing its source name.
3. Tap a France Info or RFI result — expect the reading screen to open with several paragraphs of real article/transcript text, NOT a one-sentence teaser.
4. Confirm the old separate newspaper icon is gone — only the search icon, file-import icon, and vocab icon remain in the top bar.
5. Confirm a Vikidia result still works exactly as before (this plan didn't change `VikidiaContentSource`'s behavior, just its packaging).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt
git commit -m "Wire unified multi-source search into TextsListScreen, remove old news UI"
```
