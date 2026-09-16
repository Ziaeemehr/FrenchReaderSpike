# Vikidia Content Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "پیدا کردن مطلب" (find content) search flow that lets the user search Vikidia (a French wiki written for young readers, CC BY-SA) by topic, pick one of up to 10 results, and import the full article as a new `TextDocument` with source attribution — without touching the existing RFI/France Info RSS flow.

**Architecture:** A new dependency-free `VikidiaClient` (same `HttpURLConnection` + `org.json` style as the existing `NewsFetcher`) exposes a `search()` call and a `fetchArticle()` call against Vikidia's public MediaWiki API. `TextDocument` gains five nullable attribution columns via a new Room migration. `TextsListScreen` gets a new icon that opens a `ModalBottomSheet` (search field + results list); tapping a result fetches the full article and opens it in the reading screen exactly like the existing RSS import does. `ReadingScreen` gets a small info icon, visible only when a text has a `sourceUrl`, that shows attribution and links to the source.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Kotlin coroutines, `org.json` (Android built-in), JUnit 4 (newly added, for pure-function unit tests).

**Spec:** `ROADMAP.md` section 6 ("منابع بیشتر برای مطالعه") — the "طراحی فنی: Vikidia" subsection is what this plan implements. Sections above it (RFI/France Info fix, Wikisource, Gutenberg, The Conversation, Gallica) are explicitly out of scope for this plan.

## Global Constraints

- Vikidia API base: `https://fr.vikidia.org/w/api.php` — no API key, no rate-limit handling beyond the existing 15s timeout pattern `NewsFetcher` already uses.
- Content license to store for every Vikidia import: exactly the string `"CC BY-SA 3.0"`.
- `sourceName` for Vikidia imports: exactly the string `"Vikidia"`.
- `author` for Vikidia imports: `null` (wiki is multi-author; no single-author field to fill).
- No new third-party libraries — reuse `org.json` (Android built-in) and plain `HttpURLConnection`, matching `NewsFetcher.kt`'s existing dependency-free approach.
- Existing RFI/France Info icon, dropdown, and behavior in `TextsListScreen.kt` must not change.
- New `TextDocument` columns must be nullable with no `NOT NULL DEFAULT`, so the migration is a pure additive `ALTER TABLE` (matches `MIGRATION_2_3`'s style for nullable columns).

---

## Task 1: Test infrastructure + extract `HtmlUtil`

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/ziaee/frenchreader/util/HtmlUtil.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt:137-152` (remove private `stripHtml`, delegate to `HtmlUtil`)
- Test: `app/src/test/java/com/ziaee/frenchreader/util/HtmlUtilTest.kt`

**Interfaces:**
- Produces: `object HtmlUtil { fun stripHtml(html: String): String }` — used by `NewsFetcher` (this task) and `VikidiaClient` (Task 3).

- [ ] **Step 1: Add JUnit to the test source set**

Edit `app/build.gradle`, inside the existing `dependencies { ... }` block, add:

```groovy
    testImplementation 'junit:junit:4.13.2'
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/ziaee/frenchreader/util/HtmlUtilTest.kt`:

```kotlin
package com.ziaee.frenchreader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlUtilTest {
    @Test
    fun `strips tags and collapses whitespace`() {
        val input = "<p>Bonjour   <br>le <b>monde</b></p>"
        assertEquals("Bonjour le monde", HtmlUtil.stripHtml(input))
    }

    @Test
    fun `unescapes common html entities`() {
        val input = "Tom &amp; Jerry &lt;3 &quot;cats&quot; &amp; dogs&#39;"
        assertEquals("Tom & Jerry <3 \"cats\" & dogs'", HtmlUtil.stripHtml(input))
    }

    @Test
    fun `trims leading and trailing whitespace`() {
        assertEquals("texte", HtmlUtil.stripHtml("   texte   "))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.util.HtmlUtilTest"`
Expected: FAIL — `HtmlUtil` does not exist yet (compilation error).

- [ ] **Step 4: Create `HtmlUtil` (moving the existing logic out of `NewsFetcher`)**

Create `app/src/main/java/com/ziaee/frenchreader/util/HtmlUtil.kt`:

```kotlin
package com.ziaee.frenchreader.util

/** Strips HTML tags and unescapes the handful of entities RSS/wiki feeds
 * actually use -- not a full HTML parser, just enough for a feed
 * description or search snippet (a paragraph or two, occasionally a stray
 * `<p>`/`<br>` or an `&nbsp;`), collapsing whitespace left behind by
 * removed tags. */
object HtmlUtil {
    fun stripHtml(html: String): String {
        val noTags = html.replace(Regex("<[^>]*>"), " ")
        return noTags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
```

- [ ] **Step 5: Update `NewsFetcher.kt` to delegate to `HtmlUtil`**

In `app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt`:

1. Add the import near the top (with the other imports):

```kotlin
import com.ziaee.frenchreader.util.HtmlUtil
```

2. Delete the entire private `stripHtml` function (lines 137-152, the block starting `/** Strips HTML tags ... */` and ending with the closing `}` of `private fun stripHtml`).

3. In `parseFirstItem`, replace the two call sites:

```kotlin
val cleanTitle = stripHtml(title.orEmpty())
val cleanBody = stripHtml(rawBody)
```

with:

```kotlin
val cleanTitle = HtmlUtil.stripHtml(title.orEmpty())
val cleanBody = HtmlUtil.stripHtml(rawBody)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.util.HtmlUtilTest"`
Expected: PASS (3 tests)

- [ ] **Step 7: Run a full build to confirm `NewsFetcher` still compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle app/src/main/java/com/ziaee/frenchreader/util/HtmlUtil.kt app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt app/src/test/java/com/ziaee/frenchreader/util/HtmlUtilTest.kt
git commit -m "Extract HtmlUtil from NewsFetcher and add JUnit test infra"
```

---

## Task 2: `TextDocument` attribution columns + migration

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Entities.kt:11-22`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt`

**Interfaces:**
- Produces: `TextDocument` gains `sourceUrl: String? = null`, `sourceName: String? = null`, `author: String? = null`, `license: String? = null`, `publishedAt: Long? = null`. All later tasks that construct a `TextDocument` for a Vikidia import use these exact names.

There is no existing Room migration test harness in this project (no `androidTest` source set, no prior migration tests for `MIGRATION_2_3`) — verification for this task is a real build + manual DB upgrade check on a device/emulator with existing data (Step 4), matching the project's existing precedent rather than introducing new instrumented-test infrastructure.

- [ ] **Step 1: Add the five columns to `TextDocument`**

In `app/src/main/java/com/ziaee/frenchreader/data/Entities.kt`, change the `TextDocument` data class from:

```kotlin
@Entity(tableName = "texts")
data class TextDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawText: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastChunkIndex: Int = 0,
    val lastPositionMs: Long = 0,
    val voice: String = "fr-FR-DeniseNeural",
    val ratePercent: Int = 0, // edge-tts rate, e.g. -15..+40, applied as "+N%"/"-N%"
    val translationLang: String = "fa" // ISO code for the paragraph-translation target language
)
```

to:

```kotlin
@Entity(tableName = "texts")
data class TextDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawText: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastChunkIndex: Int = 0,
    val lastPositionMs: Long = 0,
    val voice: String = "fr-FR-DeniseNeural",
    val ratePercent: Int = 0, // edge-tts rate, e.g. -15..+40, applied as "+N%"/"-N%"
    val translationLang: String = "fa", // ISO code for the paragraph-translation target language
    // Attribution for texts imported from an external source (e.g. Vikidia,
    // see ROADMAP.md section 6). All null for pasted/file-imported texts and
    // for the existing RFI/France Info RSS import, which doesn't set these yet.
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val author: String? = null,
    val license: String? = null,
    val publishedAt: Long? = null // epoch ms of the source's original publish/revision date
)
```

- [ ] **Step 2: Add `MIGRATION_3_4`**

In `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt`, add a new migration object right after `MIGRATION_2_3`:

```kotlin
// v3 -> v4: adds source-attribution columns to `texts` for content imported
// from an external source (Vikidia and beyond -- ROADMAP.md section 6). All
// nullable, so existing rows (pasted texts, file imports, RSS news) just get
// NULL and keep behaving exactly as before.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE texts ADD COLUMN sourceUrl TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN sourceName TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN license TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN publishedAt INTEGER")
    }
}
```

- [ ] **Step 3: Bump the database version and register the migration**

In the same file, change:

```kotlin
@Database(
    entities = [TextDocument::class, VocabEntry::class, VocabList::class],
    version = 3,
    exportSchema = false
)
```

to:

```kotlin
@Database(
    entities = [TextDocument::class, VocabEntry::class, VocabList::class],
    version = 4,
    exportSchema = false
)
```

And change:

```kotlin
                    .addMigrations(MIGRATION_2_3)
```

to:

```kotlin
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
```

- [ ] **Step 4: Build and manually verify the migration**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

Then, on a device/emulator that already has the app installed with existing texts (a pre-migration DB): install the new build over it (`./gradlew installDebug`, or via Android Studio) and open the app. Expected: existing texts list loads without a crash (confirms `MIGRATION_3_4` ran instead of `fallbackToDestructiveMigration()` wiping the DB). If no device with existing data is available, a fresh install is an acceptable substitute — just confirm the app launches and the texts list loads without a crash.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/data/Entities.kt app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt
git commit -m "Add source attribution columns to TextDocument (v3->v4 migration)"
```

---

## Task 3: `VikidiaClient` — search

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/vikidia/VikidiaClient.kt`
- Modify: `app/build.gradle`
- Test: `app/src/test/java/com/ziaee/frenchreader/vikidia/VikidiaClientTest.kt`

**Interfaces:**
- Consumes: `HtmlUtil.stripHtml(String): String` (Task 1).
- Produces: `data class VikidiaSearchResult(val pageId: Int, val title: String, val snippet: String, val wordCount: Int)`; `suspend fun VikidiaClient.search(query: String, limit: Int = 10): List<VikidiaSearchResult>`; internal pure function `VikidiaClient.parseSearchResults(json: String): List<VikidiaSearchResult>` used directly by the test and reused by `search()`. Task 4 adds `fetchArticle` to the same `VikidiaClient` object and Task 6 calls `search()`.

Android's unit-test classpath stubs out `org.json` (throws `RuntimeException: Method ... not mocked` for anything beyond trivial field access), so a *real* `org.json` implementation is needed for JVM unit tests — this is why Step 1 adds a `testImplementation` for it, separate from the Android-provided `org.json` used at runtime.

- [ ] **Step 1: Add a real `org.json` implementation for unit tests**

Edit `app/build.gradle`, in `dependencies { ... }`, add (near the `testImplementation 'junit:junit:4.13.2'` line added in Task 1):

```groovy
    testImplementation 'org.json:json:20240303'
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/ziaee/frenchreader/vikidia/VikidiaClientTest.kt`:

```kotlin
package com.ziaee.frenchreader.vikidia

import org.junit.Assert.assertEquals
import org.junit.Test

class VikidiaClientTest {
    // Trimmed real response shape from
    // https://fr.vikidia.org/w/api.php?action=query&list=search&srsearch=espace&srlimit=2&srprop=snippet|wordcount&format=json
    private val sampleSearchJson = """
        {
          "batchcomplete": "",
          "query": {
            "searchinfo": { "totalhits": 1857 },
            "search": [
              {
                "ns": 0,
                "title": "Espace",
                "pageid": 8826,
                "wordcount": 457,
                "snippet": "dans la course à <span class=\"searchmatch\">l'espace</span>. À ne pas confondre."
              },
              {
                "ns": 0,
                "title": "Superficie",
                "pageid": 8218,
                "wordcount": 227,
                "snippet": "comme un terrain (un jardin, un champ...)"
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses titles, pageids, wordcounts and strips snippet markup`() {
        val results = VikidiaClient.parseSearchResults(sampleSearchJson)

        assertEquals(2, results.size)
        assertEquals(VikidiaSearchResult(
            pageId = 8826,
            title = "Espace",
            snippet = "dans la course à l'espace. À ne pas confondre.",
            wordCount = 457
        ), results[0])
        assertEquals("Superficie", results[1].title)
        assertEquals(227, results[1].wordCount)
    }

    @Test
    fun `returns empty list when search array is missing`() {
        val results = VikidiaClient.parseSearchResults("""{"query": {}}""")
        assertEquals(emptyList<VikidiaSearchResult>(), results)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.vikidia.VikidiaClientTest"`
Expected: FAIL — `VikidiaClient` / `VikidiaSearchResult` do not exist yet (compilation error).

- [ ] **Step 4: Implement `VikidiaClient.search` and `parseSearchResults`**

Create `app/src/main/java/com/ziaee/frenchreader/vikidia/VikidiaClient.kt`:

```kotlin
package com.ziaee.frenchreader.vikidia

import com.ziaee.frenchreader.util.HtmlUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One Vikidia search hit: enough to show a result row (title + snippet +
 * an approximate length) without a second request per result -- the search
 * API returns wordcount alongside the snippet in a single call. */
data class VikidiaSearchResult(
    val pageId: Int,
    val title: String,
    val snippet: String,
    val wordCount: Int
)

/**
 * Client for Vikidia's public MediaWiki API (fr.vikidia.org) -- see
 * ROADMAP.md section 6. Dependency-free like NewsFetcher: HttpURLConnection
 * + org.json, no OkHttp/Retrofit/MediaWiki client library.
 */
object VikidiaClient {
    private const val API_BASE = "https://fr.vikidia.org/w/api.php"

    suspend fun search(query: String, limit: Int = 10): List<VikidiaSearchResult> = withContext(Dispatchers.IO) {
        val url = "$API_BASE?action=query&list=search&format=json&srlimit=$limit" +
            "&srprop=snippet%7Cwordcount&srsearch=" + URLEncoder.encode(query, "UTF-8")
        parseSearchResults(httpGet(url))
    }

    internal fun parseSearchResults(json: String): List<VikidiaSearchResult> {
        val search = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until search.length()).map { i ->
            val obj = search.getJSONObject(i)
            VikidiaSearchResult(
                pageId = obj.getInt("pageid"),
                title = obj.getString("title"),
                snippet = HtmlUtil.stripHtml(obj.optString("snippet", "")),
                wordCount = obj.optInt("wordcount", 0)
            )
        }
    }

    private fun httpGet(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; FrenchReaderApp)")
        connection.setRequestProperty("Accept", "application/json")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.vikidia.VikidiaClientTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle app/src/main/java/com/ziaee/frenchreader/vikidia/VikidiaClient.kt app/src/test/java/com/ziaee/frenchreader/vikidia/VikidiaClientTest.kt
git commit -m "Add VikidiaClient.search backed by Vikidia's MediaWiki search API"
```

---

## Task 4: `VikidiaClient` — fetch full article

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/vikidia/VikidiaClient.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/vikidia/VikidiaClientTest.kt`

**Interfaces:**
- Produces: `data class VikidiaArticle(val title: String, val text: String, val publishedAtMs: Long?)`; `suspend fun VikidiaClient.fetchArticle(pageId: Int): VikidiaArticle?`; `fun VikidiaClient.articleUrl(title: String): String`. Task 6 calls `fetchArticle` and `articleUrl` when the user taps a search result.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/ziaee/frenchreader/vikidia/VikidiaClientTest.kt` (inside the existing `VikidiaClientTest` class, after the existing two test functions):

```kotlin
    // Trimmed real response shape from
    // https://fr.vikidia.org/w/api.php?action=query&prop=extracts|revisions&explaintext=1&rvprop=timestamp&pageids=8826&format=json
    private val sampleArticleJson = """
        {
          "query": {
            "pages": {
              "8826": {
                "pageid": 8826,
                "ns": 0,
                "title": "Espace",
                "extract": "L'espace est l'étendue qui sépare les planètes.",
                "revisions": [ { "timestamp": "2026-03-30T20:04:50Z" } ]
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses article extract and revision timestamp`() {
        val article = VikidiaClient.parseArticle(sampleArticleJson, pageId = 8826)

        requireNotNull(article)
        assertEquals("Espace", article.title)
        assertEquals("L'espace est l'étendue qui sépare les planètes.", article.text)
        assertEquals(1774901090000L, article.publishedAtMs) // 2026-03-30T20:04:50Z
    }

    @Test
    fun `returns null when the requested page id is missing`() {
        val article = VikidiaClient.parseArticle("""{"query": {"pages": {}}}""", pageId = 8826)
        assertEquals(null, article)
    }

    @Test
    fun `articleUrl builds a wiki link with underscores and encoding`() {
        assertEquals(
            "https://fr.vikidia.org/wiki/Conqu%C3%AAte_de_l%27espace",
            VikidiaClient.articleUrl("Conquête de l'espace")
        )
    }
```

Also add the import needed by `assertEquals`/`requireNotNull` if not already present — `org.junit.Assert.assertEquals` is already imported from Task 3; `requireNotNull` is a Kotlin stdlib function, no import needed.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.vikidia.VikidiaClientTest"`
Expected: FAIL — `VikidiaArticle`, `parseArticle`, `articleUrl` do not exist yet (compilation error).

- [ ] **Step 3: Implement `fetchArticle`, `parseArticle`, and `articleUrl`**

In `app/src/main/java/com/ziaee/frenchreader/vikidia/VikidiaClient.kt`:

1. Add near the top, after the existing imports:

```kotlin
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
```

2. Add this data class after `VikidiaSearchResult`:

```kotlin
/** A fetched Vikidia article: plain-text body (already stripped of
 * wiki markup by the API's explaintext mode) plus the source's last
 * revision date, used as [com.ziaee.frenchreader.data.TextDocument.publishedAt]. */
data class VikidiaArticle(
    val title: String,
    val text: String,
    val publishedAtMs: Long?
)
```

3. Inside the `VikidiaClient` object, after `search`, add:

```kotlin
    suspend fun fetchArticle(pageId: Int): VikidiaArticle? = withContext(Dispatchers.IO) {
        val url = "$API_BASE?action=query&prop=extracts%7Crevisions&explaintext=1" +
            "&rvprop=timestamp&format=json&pageids=$pageId"
        parseArticle(httpGet(url), pageId)
    }

    /** Vikidia's canonical article URL, used for [com.ziaee.frenchreader.data.TextDocument.sourceUrl]
     * and the "open in browser" action -- spaces become underscores per
     * MediaWiki's URL convention, and the result is URL-encoded. */
    fun articleUrl(title: String): String =
        "https://fr.vikidia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
```

4. After `parseSearchResults`, add:

```kotlin
    internal fun parseArticle(json: String, pageId: Int): VikidiaArticle? {
        val page = JSONObject(json).optJSONObject("query")?.optJSONObject("pages")
            ?.optJSONObject(pageId.toString()) ?: return null
        val title = page.optString("title", "")
        val extract = page.optString("extract", "").trim()
        if (title.isBlank() || extract.isBlank()) return null
        val timestamp = page.optJSONArray("revisions")?.optJSONObject(0)?.optString("timestamp")
        return VikidiaArticle(title = title, text = extract, publishedAtMs = timestamp?.let(::parseIso8601))
    }

    private fun parseIso8601(timestamp: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(timestamp)?.time
    } catch (e: Exception) {
        null
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.vikidia.VikidiaClientTest"`
Expected: PASS (5 tests total)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/vikidia/VikidiaClient.kt app/src/test/java/com/ziaee/frenchreader/vikidia/VikidiaClientTest.kt
git commit -m "Add VikidiaClient.fetchArticle for full article text + revision date"
```

---

## Task 5: "پیدا کردن مطلب" search UI in `TextsListScreen`

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`

**Interfaces:**
- Consumes: `VikidiaClient.search(query, limit)`, `VikidiaClient.fetchArticle(pageId)`, `VikidiaClient.articleUrl(title)` (Tasks 3-4); `TextDocument(sourceUrl=, sourceName=, author=, license=, publishedAt=, ...)` (Task 2); `db.textDao().insert(TextDocument): Long` (existing `Daos.kt`).
- Produces: nothing consumed by later tasks — this is the last task that needs `VikidiaClient`.

No automated UI test is added here — the project has no `androidTest` source set or Compose UI test precedent (verified: zero existing test files before Task 1). Step 6 is a manual on-device verification instead, matching how the existing RFI/France Info flow was verified.

- [ ] **Step 1: Add Vikidia search state to `TextsListViewModel`**

In `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`:

1. Add these imports near the top, with the existing ones:

```kotlin
import com.ziaee.frenchreader.vikidia.VikidiaArticle
import com.ziaee.frenchreader.vikidia.VikidiaClient
import com.ziaee.frenchreader.vikidia.VikidiaSearchResult
```

2. Add this sealed class right after the existing `NewsFetchUiState` sealed class:

```kotlin
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

3. Inside `TextsListViewModel`, right after the existing `newsFetchState` property, add:

```kotlin
    var vikidiaSearchState by mutableStateOf<VikidiaSearchUiState>(VikidiaSearchUiState.Idle)
        private set
    var vikidiaImportingPageId by mutableStateOf<Int?>(null)
        private set
    var vikidiaImportError by mutableStateOf<String?>(null)
        private set
```

- [ ] **Step 2: Add `searchVikidia` and `importVikidiaArticle` to the ViewModel**

Still inside `TextsListViewModel`, right after the existing `dismissNewsMessage()` function, add:

```kotlin
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

- [ ] **Step 3: Add the "پیدا کردن مطلب" icon to the `TopAppBar`**

In the `TextsListScreen` composable, inside the `TopAppBar`'s `actions = { ... }` block, right before the existing `Box { IconButton(onClick = { newsMenuExpanded = true }) ... }` (the news icon), add:

```kotlin
                    IconButton(onClick = { showVikidiaSheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = "پیدا کردن مطلب")
                    }
```

And right after the line `var newsMenuExpanded by remember { mutableStateOf(false) }` (near the top of the composable), add:

```kotlin
    var showVikidiaSheet by remember { mutableStateOf(false) }
```

- [ ] **Step 4: Surface errors as a Snackbar, and render the `FindArticleSheet` when open**

Right after the existing `LaunchedEffect(vm.newsFetchState) { ... }` block in `TextsListScreen`, add two more effects that route Vikidia errors to the same `snackbarHostState`, matching how `fetchNews` errors are already handled:

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

Then, at the bottom of the `TextsListScreen` composable, right after the closing brace of the existing `if (showAddDialog) { AddTextDialog(...) }` block, add:

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

- [ ] **Step 5: Implement `FindArticleSheet`**

At the end of the file (after the existing `AddTextDialog` composable), add:

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

- [ ] **Step 6: Build and manually verify on a device/emulator**

Run: `./gradlew assembleDebug` — Expected: BUILD SUCCESSFUL.

Install and run the app (`./gradlew installDebug` or via Android Studio, with network access on the device):
1. Tap the new search icon in the texts list's top bar.
2. Type a topic (e.g. "espace") and tap search — expect up to 10 results with title, snippet, and word count within a few seconds.
3. Tap a result — expect the reading screen to open with the article's full French text (multiple paragraphs, not a one-line teaser).
4. Confirm the existing RFI/France Info newspaper icon and dropdown still work exactly as before (unchanged).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt
git commit -m "Add Vikidia search UI (\"پیدا کردن مطلب\") to the texts list"
```

---

## Task 6: Source attribution display in `ReadingScreen`

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt`

**Interfaces:**
- Consumes: `TextDocument.sourceUrl/sourceName/author/license/publishedAt` (Task 2), already exposed via `state.textDoc` in `ReadingViewModel`'s existing `ReadingUiState`.

No automated test — same rationale as Task 5 (no Compose UI test infra in this project). Step 3 is manual on-device verification.

- [ ] **Step 1: Add the info icon, shown only when `sourceUrl` is set**

In `app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt`:

1. Add these imports near the top, with the existing ones:

```kotlin
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

2. Inside the `ReadingScreen` composable, right after the line `var voiceMenuExpanded by remember { mutableStateOf(false) }`, add:

```kotlin
    var showSourceInfoSheet by remember { mutableStateOf(false) }
```

3. Inside the `TopAppBar`'s `actions = { ... }` block, right after the existing `IconButton(onClick = onOpenVocab) { ... }` block and before the `Box { ... voiceMenuExpanded ... }` block, add:

```kotlin
                        if (state.textDoc?.sourceUrl != null) {
                            IconButton(onClick = { showSourceInfoSheet = true }) {
                                Icon(Icons.Default.Info, contentDescription = "دربارهٔ این متن")
                            }
                        }
```

- [ ] **Step 2: Render `SourceInfoSheet` and implement it**

`ReadingScreen.kt:198-205` already renders `DictionarySheet` the same way this needs to render `SourceInfoSheet` — as a sibling block right before the composable's closing brace:

```kotlin
    dictionaryTarget?.let { (word, sentence) ->
        DictionarySheet(
            textId = textId,
            word = word,
            sentence = sentence,
            onDismiss = { dictionaryTarget = null }
        )
    }
}
```

Right after that `dictionaryTarget?.let { ... }` block (i.e. after line 205, still before the `ReadingScreen` composable's closing `}` on line 206), add:

```kotlin

    if (showSourceInfoSheet) {
        state.textDoc?.let { doc ->
            SourceInfoSheet(doc = doc, onDismiss = { showSourceInfoSheet = false })
        }
    }
```

Then, after the `ReadingScreen` composable's closing brace, add a new composable:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceInfoSheet(doc: com.ziaee.frenchreader.data.TextDocument, onDismiss: () -> Unit) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(doc.sourceName ?: "منبع", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                doc.sourceUrl?.let { url ->
                    IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "باز کردن منبع در مرورگر")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            doc.author?.let { Text("نویسنده: $it") }
            doc.license?.let { Text("مجوز: $it") }
            doc.publishedAt?.let {
                val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                Text("تاریخ انتشار: $dateLabel")
            }
        }
    }
}
```

- [ ] **Step 3: Build and manually verify on a device/emulator**

Run: `./gradlew assembleDebug` — Expected: BUILD SUCCESSFUL.

Install and run the app:
1. Open a text imported via the new Vikidia search (Task 5) — expect a small info icon in the reading screen's top bar.
2. Tap it — expect a bottom sheet with "Vikidia", the license string "CC BY-SA 3.0", a publish date, and a working "باز کردن منبع در مرورگر" button that opens the article on `fr.vikidia.org`.
3. Open an older text (pasted, file-imported, or from RFI/France Info) — expect **no** info icon (since `sourceUrl` is null for those).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt
git commit -m "Show source attribution (Vikidia license, date, link) in reading screen"
```
