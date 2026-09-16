# Home Dashboard and Localization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat texts landing page with a cached-news Home dashboard and dedicated Library, while adding System/Persian/French/English UI localization and Vazirmatn for Persian UI.

**Architecture:** Keep downloaded documents in `TextDocument`, add a separate Room-backed `HeadlineEntity` cache, and coordinate RSS refreshes through `NewsRepository`. Split the current `TextsListScreen` responsibilities into Home, Library, shared import/search UI, and small components; apply locale through AppCompat and localized resources.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room 2.6.1, Coroutines/Flow, Navigation Compose, XmlPullParser, jsoup, Coil Compose, AndroidX AppCompat locales.

**Spec:** `docs/superpowers/specs/2026-09-16-home-dashboard-localization-design.md`

## Global Constraints

- Minimum SDK remains 26; compile/target SDK remain 34.
- English is the default/fallback resource language; Persian and French are explicit alternatives.
- Persian UI uses bundled Vazirmatn; French article content keeps the existing reading font and is always LTR.
- Opening Home or a preview never downloads the full article body.
- RFI and France Info refresh independently; a source failure must preserve the other source and cached data.
- No background worker, periodic sync, personalized recommendations, or selectable article font.
- Existing paste/share/file import, content search, vocabulary, reading, appearance settings, and source attribution must keep working.

---

### Task 1: Localization Foundation and Persian UI Font

**Files:**
- Modify: `app/build.gradle`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/data/LocalePrefs.kt`
- Create: `app/src/main/res/font/vazirmatn_regular.ttf`
- Create: `app/src/main/res/font/vazirmatn_medium.ttf`
- Create: `app/src/main/res/raw/vazirmatn_ofl.txt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-fa/strings.xml`
- Create: `app/src/main/res/values-fr/strings.xml`
- Test: `app/src/test/java/com/ziaee/frenchreader/data/LocalePrefsTest.kt`

**Interfaces:**
- Produces: `enum class AppLanguage { SYSTEM, FA, FR, EN }`
- Produces: `LocalePrefs.get(context): AppLanguage`, `LocalePrefs.set(context, language)`
- Produces: `applyAppLanguage(language: AppLanguage)` using `AppCompatDelegate.setApplicationLocales`
- Produces: localized string resources used by every later screen.

- [ ] **Step 1: Add locale and font dependencies**

Add to `dependencies`:

```groovy
implementation 'androidx.appcompat:appcompat:1.7.0'
```

Use bundled `Vazirmatn-Regular.ttf` and `Vazirmatn-Medium.ttf` from Vazirmatn 33.003 under the SIL Open Font License; retain its license file at `app/src/main/res/raw/vazirmatn_ofl.txt`.

Define an AppCompat-compatible no-action-bar parent and reference it from the manifest:

```xml
<style name="Theme.FrenchReader" parent="Theme.AppCompat.Light.NoActionBar">
    <item name="android:fontFamily">sans</item>
    <item name="android:windowActionModeOverlay">true</item>
</style>
```

Set `<application android:theme="@style/Theme.FrenchReader">` so `AppCompatActivity` starts with a compatible theme.

- [ ] **Step 2: Write the failing preference/resolution tests**

```kotlin
class LocalePrefsTest {
    @Test fun `system maps to empty locale tags`() =
        assertEquals("", AppLanguage.SYSTEM.languageTags)

    @Test fun `explicit languages use stable BCP 47 tags`() {
        assertEquals("fa", AppLanguage.FA.languageTags)
        assertEquals("fr", AppLanguage.FR.languageTags)
        assertEquals("en", AppLanguage.EN.languageTags)
    }

    @Test fun `unknown persisted value falls back to system`() =
        assertEquals(AppLanguage.SYSTEM, parseAppLanguage("UNKNOWN"))
}
```

- [ ] **Step 3: Run the focused test and confirm failure**

Run: `./gradlew testDebugUnitTest --tests '*LocalePrefsTest'`

Expected: compilation fails because `AppLanguage` and `parseAppLanguage` do not exist.

- [ ] **Step 4: Implement locale persistence and application**

```kotlin
enum class AppLanguage(val languageTags: String) {
    SYSTEM(""), FA("fa"), FR("fr"), EN("en")
}

internal fun parseAppLanguage(value: String?): AppLanguage =
    AppLanguage.entries.firstOrNull { it.name == value } ?: AppLanguage.SYSTEM

fun applyAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(
        LocaleListCompat.forLanguageTags(language.languageTags)
    )
}
```

Persist the enum name in `locale_prefs`; load and apply it before `setContent`. Change `MainActivity` to `AppCompatActivity`.

- [ ] **Step 5: Add localized resource sets and remove settings hardcoding**

Define matching keys in all three resource files, including `settings_title`, `language_title`, `language_system`, `language_persian`, `language_french`, `language_english`, all appearance labels, navigation labels, errors, actions, content-search copy, and accessibility descriptions. English values live in `values/strings.xml`; Persian in `values-fa`; French in `values-fr`.

Replace `Text("تنظیمات")`-style literals with `Text(stringResource(R.string.settings_title))`. Make `FontScale` store no localized label; map each enum to a string resource inside `SettingsScreen`.

- [ ] **Step 6: Apply Vazirmatn only for Persian UI**

```kotlin
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium)
)

@Composable
private fun interfaceTypography(): Typography =
    if (LocalConfiguration.current.locales[0].language == "fa") {
        Typography().let { base -> base.copy(
            bodyLarge = base.bodyLarge.copy(fontFamily = Vazirmatn),
            bodyMedium = base.bodyMedium.copy(fontFamily = Vazirmatn),
            titleLarge = base.titleLarge.copy(fontFamily = Vazirmatn),
            titleMedium = base.titleMedium.copy(fontFamily = Vazirmatn),
            labelLarge = base.labelLarge.copy(fontFamily = Vazirmatn)
        ) }
    } else Typography()
```

Pass this typography to `MaterialTheme`. Do not apply it to explicit Reading-screen article text styles.

- [ ] **Step 7: Run tests and build**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: all tests pass and the debug APK builds.

- [ ] **Step 8: Commit**

```bash
git add app/build.gradle app/src/main/java app/src/main/res app/src/test
git commit -m "Add Persian French and English interface localization"
```

---

### Task 2: Headline Cache and Document Metadata Migration

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Entities.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Daos.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt`
- Create: `app/src/androidTest/java/com/ziaee/frenchreader/data/Migration4To5Test.kt`
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: `HeadlineEntity(sourceId, sourceLabel, externalId, title, snippet, articleUrl, imageUrl, publishedAtMs, cachedAtMs)`.
- Produces: `HeadlineDao.observeRecent(limit)`, `replaceSource(sourceId, items)`, `clearSource(sourceId)`.
- Extends: `TextDocument.imagePath`, `externalKey`, `lastAccessedAtMs`.
- Produces: `TextDao.findByExternalKey`, `observeRecent`, `observeMostRecentlyAccessed`, `markAccessed`.

- [ ] **Step 1: Add Room migration-test dependencies**

```groovy
androidTestImplementation 'androidx.test.ext:junit:1.2.1'
androidTestImplementation 'androidx.room:room-testing:2.6.1'
```

Set `testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig` and add `androidx.test:runner:1.6.1`.

- [ ] **Step 2: Write the failing migration test**

Create a version-4 database containing one text, run `MIGRATION_4_5`, and assert:

```kotlin
db.query("SELECT imagePath, externalKey, lastAccessedAtMs FROM texts").use { cursor ->
    assertTrue(cursor.moveToFirst())
    assertTrue(cursor.isNull(0))
    assertTrue(cursor.isNull(1))
    assertEquals(0L, cursor.getLong(2))
}
db.query("SELECT COUNT(*) FROM headlines").use { cursor ->
    assertTrue(cursor.moveToFirst())
    assertEquals(0, cursor.getInt(0))
}
```

- [ ] **Step 3: Run the migration test and confirm failure**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.data.Migration4To5Test`

Expected: compilation fails because `MIGRATION_4_5` does not exist.

- [ ] **Step 4: Add entities and migration**

```kotlin
@Entity(
    tableName = "headlines",
    primaryKeys = ["sourceId", "externalId"],
    indices = [Index("publishedAtMs"), Index("articleUrl")]
)
data class HeadlineEntity(
    val sourceId: String,
    val sourceLabel: String,
    val externalId: String,
    val title: String,
    val snippet: String,
    val articleUrl: String,
    val imageUrl: String?,
    val publishedAtMs: Long?,
    val cachedAtMs: Long
)
```

`MIGRATION_4_5` adds nullable `imagePath`, nullable `externalKey`, non-null `lastAccessedAtMs DEFAULT 0`, creates the `headlines` table, and creates a unique partial index on non-null `texts.externalKey`. Bump Room to version 5 and register the migration.

- [ ] **Step 5: Implement DAO queries and transactional replacement**

```kotlin
@Query("SELECT * FROM headlines ORDER BY publishedAtMs DESC LIMIT :limit")
fun observeRecent(limit: Int): Flow<List<HeadlineEntity>>

@Transaction
suspend fun replaceSource(sourceId: String, items: List<HeadlineEntity>) {
    clearSource(sourceId)
    insertAll(items)
}
```

Add document queries for recent five, most recently accessed, title/source filtering, duplicate lookup, and `UPDATE texts SET lastAccessedAtMs = :now WHERE id = :id`.

- [ ] **Step 6: Run tests and build**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: unit tests and build pass. Run the migration instrumentation test on the available emulator/device before release.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle app/src/main/java/com/ziaee/frenchreader/data app/src/androidTest
git commit -m "Add headline cache and library metadata schema"
```

---

### Task 3: RSS Image Metadata and Pure News Merge Logic

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/news/NewsFetcher.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/news/HeadlineMerger.kt`
- Modify: `app/src/test/java/com/ziaee/frenchreader/news/NewsFetcherTest.kt`
- Create: `app/src/test/java/com/ziaee/frenchreader/news/HeadlineMergerTest.kt`

**Interfaces:**
- Extends: `NewsItem.imageUrl: String?`.
- Produces: `mergeHeadlines(vararg sources: List<HeadlineEntity>, limit: Int): List<HeadlineEntity>`.

- [ ] **Step 1: Write failing parser tests**

Add fixtures asserting `<media:content url="https://img.test/a.jpg">`, image `<enclosure>`, and `<img src>` inside description resolve to `NewsItem.imageUrl`, while a missing image returns null.

- [ ] **Step 2: Write failing merge tests**

```kotlin
@Test fun `merge sorts newest first and deduplicates normalized urls`() {
    val merged = mergeHeadlines(listOf(older, duplicateA), listOf(newer, duplicateB), 10)
    assertEquals(listOf(newer.externalId, duplicateA.externalId, older.externalId), merged.map { it.externalId })
}
```

- [ ] **Step 3: Run focused tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests '*NewsFetcherTest' --tests '*HeadlineMergerTest'`

- [ ] **Step 4: Parse image candidates safely**

Track `media:content`, `media:thumbnail`, image enclosures, and the first description `<img>`. Accept only absolute HTTPS URLs (upgrade HTTP with existing `httpsUrl`); prefer media content, then enclosure, then description image.

- [ ] **Step 5: Implement deterministic merging**

Normalize article URLs by HTTPS-upgrade, lowercase host, remove fragments, and remove trailing `/`. Deduplicate by normalized URL, prefer a record with an image, sort null dates after dated items, then apply `limit`.

- [ ] **Step 6: Run tests and commit**

Run: `./gradlew testDebugUnitTest`

```bash
git add app/src/main/java/com/ziaee/frenchreader/news app/src/test/java/com/ziaee/frenchreader/news
git commit -m "Parse and merge image-backed news headlines"
```

---

### Task 4: News Repository with Independent Source Refresh

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/news/NewsRepository.kt`
- Create: `app/src/test/java/com/ziaee/frenchreader/news/NewsRepositoryTest.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/content/NewsContentSource.kt`

**Interfaces:**
- Consumes: `HeadlineDao`, `NewsFetcher.fetchItems`, existing RFI/France Info feed URLs.
- Produces: `NewsRefreshResult(updatedSources, failedSources)`.
- Produces: `NewsRepository.observeHeadlines(limit = 20)`, `refresh(force: Boolean)`.

- [ ] **Step 1: Introduce an injectable feed client and clock**

```kotlin
fun interface NewsFeedClient { suspend fun fetch(url: String, limit: Int): List<NewsItem> }
fun interface Clock { fun nowMs(): Long }
```

Use production defaults wrapping `NewsFetcher` and `System.currentTimeMillis()`.

- [ ] **Step 2: Write failing partial-success and stale-cache tests**

Test that RFI success calls `replaceSource("rfi_facile", ...)` even when France Info throws; verify France Info cache is not cleared. Test that a non-stale cache skips automatic refresh while `force = true` performs it.

- [ ] **Step 3: Run tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests '*NewsRepositoryTest'`

- [ ] **Step 4: Implement repository refresh**

Use `supervisorScope` and one `async` per source. Convert successful items to `HeadlineEntity`, replace only that source in a Room transaction, and return source IDs for failures without throwing away successful work.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew testDebugUnitTest`

```bash
git add app/src/main/java/com/ziaee/frenchreader/news app/src/test/java/com/ziaee/frenchreader/news app/src/main/java/com/ziaee/frenchreader/content
git commit -m "Add independently refreshable news repository"
```

---

### Task 5: Article Image Persistence and Duplicate-Safe Import

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/ziaee/frenchreader/images/ArticleImageStore.kt`
- Create: `app/src/test/java/com/ziaee/frenchreader/images/ArticleImageStoreTest.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`

**Interfaces:**
- Produces: `ArticleImageStore.downloadAndStore(documentId, imageUrl): String?`.
- Produces: `ArticleImportResult.OpenExisting(id)` and `ArticleImportResult.Imported(id)`.
- Consumes: normalized headline URL as `TextDocument.externalKey`.

- [ ] **Step 1: Add Coil**

```groovy
implementation 'io.coil-kt:coil-compose:2.7.0'
```

- [ ] **Step 2: Write failing image and duplicate-import tests**

Test the pure `scaledDimensions(1600, 1200, maxWidth = 1080)` helper returns `1080×810`. Test that an existing `externalKey` returns `OpenExisting` without calling `fetchArticle`.

- [ ] **Step 3: Run focused tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests '*ArticleImageStoreTest' --tests '*ArticleImport*'`

- [ ] **Step 4: Implement bounded image storage**

Download on `Dispatchers.IO`, reject non-image MIME types and responses over 8 MiB, decode with bounds, scale to max width 1080, and write JPEG quality 82 to `filesDir/text_images/<documentId>.jpg`. Return a relative path; on failure return null and delete partial files.

- [ ] **Step 5: Extract import logic from the old screen view model**

Create a focused `ArticleImportRepository` that checks `findByExternalKey`, fetches full content from the matching `ContentSource`, inserts `TextDocument`, persists an optional image, updates `imagePath`, and removes the inserted row/file if a post-insert fatal operation fails. Missing image is not fatal.

- [ ] **Step 6: Make deletion remove owned images**

Route Library/Home deletions through one repository method that validates the relative path is within `filesDir/text_images`, deletes the file, then deletes the Room row.

- [ ] **Step 7: Add a dashboard UI test**

Run: `./gradlew testDebugUnitTest assembleDebug`

```bash
git add app/build.gradle app/src/main/java/com/ziaee/frenchreader/images app/src/main/java/com/ziaee/frenchreader/ui app/src/test
git commit -m "Persist article images and prevent duplicate imports"
```

---

### Task 6: Home State and Dashboard UI

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeComponents.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/home/NewsPreviewSheet.kt`
- Create: `app/src/test/java/com/ziaee/frenchreader/ui/home/HomeStateTest.kt`
- Create: `app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`

**Interfaces:**
- Produces: `HomeUiState(headlines, recentTexts, continueReading, isRefreshing, sourceErrors, importingKey)`.
- Consumes: News repository flows, recent/last-accessed DAO flows, article importer, existing content-search UI.

- [ ] **Step 1: Write failing pure state tests**

Test cached headlines remain present during refresh; one source error produces a non-blocking warning; no cache plus both errors produces the full empty-error state; recent texts are capped at five.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests '*HomeStateTest'`

- [ ] **Step 3: Implement Home view model**

Combine Room flows into immutable `StateFlow<HomeUiState>`. Trigger a stale-aware refresh once per view-model lifetime and expose `refresh(force = true)`, `selectHeadline`, `importSelected`, and `dismissPreview`.

- [ ] **Step 4: Build the approved Home hierarchy**

Implement localized TopAppBar, search field, horizontal `LazyRow` news cards with Coil `AsyncImage`, Continue Reading card, and five-item recent list. Use source-specific placeholders when `imageUrl` is null or image loading fails.

Wrap the dashboard content in Material pull-to-refresh; its gesture calls `refresh(force = true)` and its indicator reflects only headline refresh, not article imports.

- [ ] **Step 5: Implement preview and retry states**

`NewsPreviewSheet` shows headline metadata without fetching the body. Its action displays progress only for the selected headline; failures keep the sheet open and show localized Retry. Existing downloads show Open in Library.

- [ ] **Step 6: Reuse and relocate current search/add flows**

Move `FindArticleSheet`, add/paste dialog, file picker, and share-prefill coordination into focused shared files without changing behavior. Keep `TextsListScreen` temporarily as a compatibility wrapper until navigation switches.

- [ ] **Step 8: Run tests and commit**

Add a Compose UI test with a static `HomeUiState` that asserts localized section headings, a headline card, Continue Reading, and See All are present; click the headline and assert the preview action appears. Add `androidx.compose.ui:ui-test-junit4` to `androidTestImplementation`.

- [ ] **Step 7: Run tests and commit**

Run: `./gradlew testDebugUnitTest assembleDebug`

```bash
git add app/build.gradle app/src/main/java/com/ziaee/frenchreader/ui app/src/test/java/com/ziaee/frenchreader/ui app/src/androidTest/java/com/ziaee/frenchreader/ui
git commit -m "Build cached-news Home dashboard"
```

---

### Task 7: Searchable and Sortable Library

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryViewModel.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryScreen.kt`
- Create: `app/src/test/java/com/ziaee/frenchreader/ui/library/LibrarySortTest.kt`

**Interfaces:**
- Produces: `enum class LibrarySort { NEWEST, TITLE, LAST_READ }`.
- Produces: `LibraryUiState(query, sort, documents)`.
- Consumes: `TextDao.observeAll()` and shared deletion repository.

- [ ] **Step 1: Write failing filter/sort tests**

Test case-insensitive title/source filtering and exact ordering for all three sort modes, including `lastAccessedAtMs == 0` after accessed documents.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests '*LibrarySortTest'`

- [ ] **Step 3: Implement pure library projection**

```kotlin
internal fun projectLibrary(
    documents: List<TextDocument>, query: String, sort: LibrarySort
): List<TextDocument>
```

Normalize with `Locale.ROOT`; filter title and `sourceName`; use stable ID as the final tie-breaker.

- [ ] **Step 4: Build Library screen**

Add localized search, sort menu, empty states, source/image-aware document rows, open action, and delete confirmation. All operations are local and available offline.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew testDebugUnitTest assembleDebug`

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/library app/src/test/java/com/ziaee/frenchreader/ui/library
git commit -m "Add searchable sortable text library"
```

---

### Task 8: Navigation, Reading Access Tracking, and Complete String Migration

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt`
- Modify: every UI file under `app/src/main/java/com/ziaee/frenchreader/ui/` containing user-visible literals
- Modify: all three `strings.xml` resource sets
- Test: `app/src/test/java/com/ziaee/frenchreader/LocalizationCompletenessTest.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/LocalizationDirectionTest.kt`

**Interfaces:**
- Consumes: Home and Library routes.
- Produces: routes `home`, `library`, and existing `reading/{textId}`, `vocab`, `settings`.

- [ ] **Step 1: Add localization completeness test**

Parse the three XML resource files and assert their string-name sets are identical. Scan Kotlin UI source for `Text("`, `contentDescription = "`, and `showSnackbar("`; allow only French sample/source content explicitly documented in the test.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew testDebugUnitTest --tests '*LocalizationCompletenessTest'`

Expected: failure listing remaining hardcoded UI strings.

- [ ] **Step 3: Wire final navigation**

Set start destination to `home`; add Library; connect bottom navigation and Add Text; preserve Reading, Vocabulary, Review, and Settings deep flows. Avoid duplicate Home/Library destinations with `launchSingleTop` and state restoration.

- [ ] **Step 4: Mark documents accessed**

On successful Reading document load, call `textDao.markAccessed(textId, clock.nowMs())` once. Do not update on every playback tick.

- [ ] **Step 5: Finish localization migration**

Replace every user-facing literal reported by the test across Reading, Dictionary, Vocabulary, Review, search, add/import, Home, and Library. Keep source-provided French strings and TTS content untouched and LTR.

- [ ] **Step 6: Add layout-direction instrumentation coverage**

Add an instrumentation test that forces Persian and English app locales and asserts the Home root layout direction changes while a tagged French headline remains LTR.

- [ ] **Step 7: Run full verification**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: all tests pass, no hardcoded UI-string violations, debug APK builds.

- [ ] **Step 8: Commit**

```bash
git add app/src/main app/src/test
git commit -m "Wire Home library navigation and complete localization"
```

---

### Task 9: Device Verification, Accessibility, and Roadmap Update

**Files:**
- Modify: `ROADMAP.md`
- Modify: `docs/superpowers/specs/2026-09-16-home-dashboard-localization-design.md` only if verified implementation behavior differs and the user approves the correction.

**Interfaces:**
- Validates all previous task outputs as one application.

- [ ] **Step 1: Run automated verification from a clean state**

Run: `./gradlew clean testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run device/emulator database and UI checks**

Run: `./gradlew connectedDebugAndroidTest`

Expected: migration and UI tests pass on API 26+ target.

- [ ] **Step 3: Complete the live-source checklist**

Verify: cached Home renders immediately; RFI and France Info refresh; dates interleave correctly; cards show remote image or placeholder; preview performs no article fetch; Download and Read imports full text; the second selection opens the existing document; airplane-mode relaunch shows cache; deletion removes the local image.

- [ ] **Step 4: Complete language and direction checks**

Switch among System, Persian, French, and English. Verify persistence, English fallback for an unsupported system locale, Vazirmatn only on Persian UI, RTL Persian chrome, and LTR French headlines/article bodies.

- [ ] **Step 5: Complete regression checks**

Verify pasted text, TXT/MD file import, Android Share/Open With, Vikidia topic search, dictionary tabs, vocabulary review, voice switching, reading position, theme, reading background, and font size.

- [ ] **Step 6: Update roadmap status**

Mark the Home dashboard, cached daily-news cards with images, Library, and UI localization as implemented. Keep selectable article fonts explicitly deferred.

- [ ] **Step 7: Commit final verification/docs**

```bash
git add ROADMAP.md
git commit -m "Document Home dashboard and localization delivery"
```
