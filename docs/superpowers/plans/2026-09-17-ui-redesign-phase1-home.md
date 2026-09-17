# UI Redesign Phase 1: Design System and Home Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the “Editorial Magazine + Language Learning” design system and apply it to a substantially redesigned Home screen without changing the app’s working content, import, reading, review, localization, or navigation behavior.

**Architecture:** Extend the shipped `FrenchReaderTheme` with explicit light/dark color schemes, locale-aware typography roles, shapes, spacing, icon/touch sizes, and elevations, while leaving `ThemeMode`, `ReadingBackground`, `ReadingPalette`, `FontScale`, `AppearanceState`, and `AppearancePrefs` intact. Add a small shared Compose primitive layer, then rebuild Home around the existing `HomeViewModel` flows; enrich `HomeUiState` only with metrics derived from existing `TextDocument` and `VocabEntry` rows, and reuse the existing search, headline preview/import, file import, vocabulary, statistics, settings, Library, and review routes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 (Compose BOM 2024.06.00), Room/Flow, Navigation Compose, Coil Compose, bundled Vazirmatn, Android platform serif/sans families, JUnit 4, Compose UI tests.

**Spec:** `docs/Redesign the application’s user interfac.md` — only Implementation Process steps 1–6 and the design-token/reusable-component/Home requirements are implemented by this plan.

## Global Constraints

- Phase 1 is tokens + reusable primitives + Home only; Library, Reading, Dictionary, Vocabulary, Settings, Statistics, and AI redesigns are later phases.
- Preserve `ThemeMode.SYSTEM/LIGHT/DARK`, `ReadingBackground.SEPIA/WHITE/DARK`, `ReadingPalette`, `FontScale`, `AppearanceState`, and the persisted keys/defaults in `AppearancePrefs`.
- General app theme and reading background remain independent; this phase must not visually or behaviorally rewrite `ReadingScreen`.
- Use warm ivory/oxblood/teal/amber in light mode and warm near-black/layered charcoal/softened red/desaturated teal in dark mode; purple is not a dominant color.
- Use an 8-point spacing system, with 4 dp only as the supported half-step for compact internal gaps.
- French editorial content remains explicitly LTR. Persian UI follows the ambient RTL layout and uses bundled Vazirmatn; mixed-language rows do not force the whole screen into a content-specific direction.
- Every new user-facing string is defined with the same key in `values`, `values-fa`, and `values-fr`.
- Do not add a dependency: Material 3, Compose foundation behavior, Coil, and the existing icon pack are sufficient.
- Do not invent category, CEFR level, article reading time, streak, or weekly-goal values when the current entities do not contain enough data.
- Preserve cached headlines during refresh, independent source failures, pull-to-refresh, preview-before-download, duplicate reopening, topic search, paste/share/file import, and all existing destinations.
- Interactive targets are at least 48 dp; icons have localized content descriptions; selected/loading/error/completed states do not rely on color alone.

---

## Current Code Audit

### Theme and preferences

- `ui/theme/Theme.kt` currently calls bare `lightColorScheme()` / `darkColorScheme()` and applies a partial Persian-only typography override. It already owns the shipped `ThemeMode`, reading-background palettes, `FontScale`, and live `AppearanceState` contract.
- `data/AppearancePrefs.kt` persists exactly those three enum choices by name. No new visual token needs persistence, so this file must remain unchanged.
- Only Vazirmatn Regular/Medium are bundled. Use them for Persian UI and use Android’s `FontFamily.Serif` / `FontFamily.SansSerif` for the editorial/humanist roles; do not add a font-download library or network font.
- Reading palettes are explicit and independent from `MaterialTheme`; they remain byte-for-byte unchanged in this phase.

### Home and data

- `HomeScreen` owns the existing unified topic-search sheet, add/paste/share/file flows, Snackbar handling, preview sheet, and callbacks to Library, Vocabulary, Statistics, Settings, and Reading.
- `HomeViewModel` combines cached headlines, five recent documents, most recently accessed document, refresh state, source errors, and import state. It also searches Vikidia/RFI/France Info and imports selected results.
- `HomeComponents.kt` currently contains Home-only section headers, fixed-width news cards, image fallback, continue-reading card, recent-text rows, and document thumbnails. Styling is raw Material defaults with repeated 12/16 dp values.
- `HeadlineEntity` supplies source, title, snippet, URL, optional image, and publication time. It has no category, CEFR level, or full-body word count; Home must not fabricate those fields.
- `TextDocument` supplies full text, source/date/image, last chunk, last position, and last-accessed time. Text reading time and chunk-level progress can be presented as estimates derived locally.
- `VocabEntry` supplies `learned` and `nextReviewAtMs`; `VocabDao.observeAll()` already exists, so saved/learned/due counts need no schema or DAO change.
- There is no Home daily-review card today. The existing all-vocabulary review route is `vocab_review/$VOCAB_SCOPE_ALL` and the due rule is `!learned && nextReviewAtMs <= now`; Phase 1 reuses both exactly.

### Shared UI and duplicated styles

- The only current shared Compose UI file is `ui/shared/TextImportComponents.kt` (add-text state/dialog, file picker, and unified content-search sheet). Keep these behaviors and entry points.
- Home and Library duplicate their three-item `NavigationBar`; Phase 1 creates the shared bottom-bar primitive and migrates Home only. Library migration waits for its redesign phase.
- `TopAppBar`, section title padding, card padding, 48/56 dp thumbnails, rounded shapes, buttons, error/loading layouts, and sheet padding are repeatedly specified inline across screens.
- Home currently exposes five unrelated top-bar icons. The redesign keeps Search and Settings visible and places Import file, Vocabulary, and Statistics in a localized overflow menu, so no route disappears.
- `HomeScreenTest`, `HomeStateTest`, `LocalizationDirectionTest`, `ThemeTest`, and `LocalizationCompletenessTest` already provide the correct test seams. Extend them rather than creating a screenshot-test stack.

### Dependencies

- `app/build.gradle` already has Material 3, extended Material icons, Compose UI tests, Coil, Room, and coroutines. It is audited but not changed by this plan.

---

## Target File Map

**Create:**

- `app/src/main/java/com/ziaee/frenchreader/ui/theme/Color.kt` — exact semantic light/dark palettes.
- `app/src/main/java/com/ziaee/frenchreader/ui/theme/Type.kt` — locale-aware interface and editorial typography roles.
- `app/src/main/java/com/ziaee/frenchreader/ui/theme/DesignTokens.kt` — spacing, shapes, elevations, icon/touch sizes, and CompositionLocals.
- `app/src/main/java/com/ziaee/frenchreader/ui/components/EditorialPrimitives.kt` — app bar, search entry, section header, badge, progress, buttons, and empty-state primitives.
- `app/src/main/java/com/ziaee/frenchreader/ui/components/EditorialNavigation.kt` — shared Home/Library/Add bottom navigation.
- `app/src/androidTest/java/com/ziaee/frenchreader/ui/components/EditorialPrimitivesTest.kt` — primitive interaction and selection semantics.

**Modify:**

- `app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt` — install new schemes/typography/shapes/tokens while retaining all shipped appearance/read-mode APIs.
- `app/src/test/java/com/ziaee/frenchreader/ui/theme/ThemeTest.kt` — lock palette values and theme/font resolution.
- `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeUiState.kt` — reliable learning counts and pure reading-metric helpers.
- `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeViewModel.kt` — combine existing all-text/all-vocabulary flows into Home state.
- `app/src/test/java/com/ziaee/frenchreader/ui/home/HomeStateTest.kt` — learning/due/progress/reading-time behavior.
- `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml` — identical Phase 1 keys and localized values.
- `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt` — pass the existing all-vocabulary review route into Home.
- `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeScreen.kt` — new editorial scaffold/header/search/summary/overflow/review/navigation composition.
- `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeComponents.kt` — responsive carousel and redesigned Home cards/list.
- `app/src/main/java/com/ziaee/frenchreader/ui/home/NewsPreviewSheet.kt` — apply Phase 1 typography, tokens, directionality, and button states to the Home preview.
- `app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt` — Home structure, actions, completed/due states, and carousel peek.
- `app/src/androidTest/java/com/ziaee/frenchreader/LocalizationDirectionTest.kt` — retain French LTR assertions under Persian RTL after the component rewrite.

**Explicitly unchanged:** `AppearancePrefs.kt`, `Daos.kt`, `Entities.kt`, `AppDatabase.kt`, `TextImportComponents.kt`, and `app/build.gradle`.

---

### Task 1: Semantic theme tokens without preference regression

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/theme/Color.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/theme/Type.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/theme/DesignTokens.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/theme/ThemeTest.kt`

**Interfaces:**
- Produces: `FrenchReaderLightColorScheme`, `FrenchReaderDarkColorScheme`.
- Produces: `interfaceTypography(language: String): Typography` and `editorialTypography(language: String): EditorialTypography`.
- Produces: `FrenchReaderDesign.spacing`, `.sizes`, `.elevations`, and `.editorialTypography` composition-local accessors.
- Preserves: every existing public enum/function/object in `Theme.kt`, including `FrenchReaderTheme(themeMode, content)`.

- [ ] **Step 1: Extend the palette tests first**

Add tests asserting the semantic anchors, theme resolution, and font role choice:

```kotlin
@Test fun `light scheme uses editorial palette anchors`() {
    assertEquals(Color(0xFFF7F1E7), FrenchReaderLightColorScheme.background)
    assertEquals(Color(0xFF7A2432), FrenchReaderLightColorScheme.primary)
    assertEquals(Color(0xFF2F6F6A), FrenchReaderLightColorScheme.secondary)
    assertEquals(Color(0xFFA66A16), FrenchReaderLightColorScheme.tertiary)
}

@Test fun `dark scheme is warm and not pure black`() {
    assertEquals(Color(0xFF181513), FrenchReaderDarkColorScheme.background)
    assertEquals(Color(0xFFE0A2AA), FrenchReaderDarkColorScheme.primary)
}

@Test fun `Persian typography keeps Vazirmatn while French editorial text uses serif`() {
    assertEquals(Vazirmatn, interfaceFontFamilyFor("fa"))
    assertEquals(Vazirmatn, editorialFontFamilyFor("fa"))
    assertEquals(FontFamily.SansSerif, interfaceFontFamilyFor("fr"))
    assertEquals(FontFamily.Serif, editorialFontFamilyFor("fr"))
}
```

Keep all existing reading-palette, theme-mode, and `FontScale` tests.

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.theme.ThemeTest'`

Expected: compilation fails because the new schemes/font helpers do not exist.

- [ ] **Step 3: Define exact light and dark color schemes**

In `Color.kt`, expose Material 3 schemes with these anchors and fill the related container/on-colors consistently:

```kotlin
val FrenchReaderLightColorScheme = lightColorScheme(
    primary = Color(0xFF7A2432), onPrimary = Color(0xFFFFF8F6),
    primaryContainer = Color(0xFFF4DCE0), onPrimaryContainer = Color(0xFF54131F),
    secondary = Color(0xFF2F6F6A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7ECE8), onSecondaryContainer = Color(0xFF163F3C),
    tertiary = Color(0xFFA66A16), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9E3BB), onTertiaryContainer = Color(0xFF5D3908),
    background = Color(0xFFF7F1E7), onBackground = Color(0xFF211C19),
    surface = Color(0xFFFFFBF4), onSurface = Color(0xFF211C19),
    surfaceVariant = Color(0xFFEFE6D9), onSurfaceVariant = Color(0xFF655B55),
    outline = Color(0xFF8C8178), outlineVariant = Color(0xFFD8CEC3),
    error = Color(0xFFBA1A1A), onError = Color.White
)

val FrenchReaderDarkColorScheme = darkColorScheme(
    primary = Color(0xFFE0A2AA), onPrimary = Color(0xFF4B101B),
    primaryContainer = Color(0xFF64202B), onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Color(0xFF86BDB6), onSecondary = Color(0xFF0D3733),
    secondaryContainer = Color(0xFF234F4B), onSecondaryContainer = Color(0xFFA1D8D1),
    tertiary = Color(0xFFE3B66F), onTertiary = Color(0xFF432C04),
    tertiaryContainer = Color(0xFF5D410D), onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF181513), onBackground = Color(0xFFF2EAE0),
    surface = Color(0xFF211D1A), onSurface = Color(0xFFF2EAE0),
    surfaceVariant = Color(0xFF2D2825), onSurfaceVariant = Color(0xFFD2C6BC),
    outline = Color(0xFF9B9087), outlineVariant = Color(0xFF49413C),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)
```

- [ ] **Step 4: Define typography roles that preserve scripts**

Move `Vazirmatn` from `Theme.kt` to `Type.kt`. Implement pure family selectors and build all Material text styles from the selected interface family, not only the five currently overridden styles. Use `FontFamily.SansSerif` for non-Persian interface text and `FontFamily.Serif` for non-Persian editorial display text. Define `EditorialTypography(screenTitle, sectionTitle, articleHeadline)` with line heights at least 1.2× font size. Persian editorial roles use Vazirmatn rather than risking missing/reordered Arabic glyphs.

Exact public contracts:

```kotlin
internal fun interfaceFontFamilyFor(language: String): FontFamily
internal fun editorialFontFamilyFor(language: String): FontFamily
internal fun interfaceTypography(language: String): Typography

@Immutable
data class EditorialTypography(
    val screenTitle: TextStyle,
    val sectionTitle: TextStyle,
    val articleHeadline: TextStyle
)
```

Use `FontWeight.SemiBold` for editorial titles, `FontWeight.Normal/Medium` for body/labels, and do not alter Reading-screen `sp` values or apply `AppearanceState.fontScale` to the interface.

- [ ] **Step 5: Define spacing, shape, size, and elevation tokens**

In `DesignTokens.kt`, define immutable values:

```kotlin
@Immutable data class AppSpacing(
    val half: Dp = 4.dp, val xSmall: Dp = 8.dp, val small: Dp = 16.dp,
    val medium: Dp = 24.dp, val large: Dp = 32.dp, val xLarge: Dp = 48.dp
)
@Immutable data class AppSizes(
    val iconSmall: Dp = 18.dp, val icon: Dp = 24.dp,
    val touchTarget: Dp = 48.dp, val thumbnailSmall: Dp = 48.dp,
    val thumbnailMedium: Dp = 72.dp
)
@Immutable data class AppElevations(
    val flat: Dp = 0.dp, val card: Dp = 1.dp,
    val raised: Dp = 3.dp, val overlay: Dp = 6.dp
)
val FrenchReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
```

Provide stable CompositionLocals and the `FrenchReaderDesign` accessor object. Defaults must match the production values so isolated Compose tests/previews do not crash.

- [ ] **Step 6: Integrate with the shipped theme rather than replacing it**

Change only the theme setup portion of `Theme.kt`:

```kotlin
@Composable
fun FrenchReaderTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val language = LocalConfiguration.current.locales[0].language
    CompositionLocalProvider(
        LocalAppSpacing provides AppSpacing(),
        LocalAppSizes provides AppSizes(),
        LocalAppElevations provides AppElevations(),
        LocalEditorialTypography provides editorialTypography(language)
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) FrenchReaderDarkColorScheme else FrenchReaderLightColorScheme,
            typography = interfaceTypography(language),
            shapes = FrenchReaderShapes,
            content = content
        )
    }
}
```

Leave everything from `ReadingBackground` through `AppearanceState` functionally unchanged. Do not edit `AppearancePrefs.kt` or add a migration/new preference.

- [ ] **Step 7: Verify and commit**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.theme.ThemeTest' assembleDebug`

Expected: all theme tests pass; debug APK builds.

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/theme app/src/test/java/com/ziaee/frenchreader/ui/theme/ThemeTest.kt
git commit -m "Add editorial semantic design tokens"
```

---

### Task 2: Shared editorial primitives

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/components/EditorialPrimitives.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/components/EditorialNavigation.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/ui/components/EditorialPrimitivesTest.kt`

**Interfaces:**
- Produces: `EditorialTopAppBar`, `EditorialSearchEntry`, `EditorialSectionHeader`, `MetadataBadge`, `EditorialProgressIndicator`, `EditorialPrimaryButton`, `EditorialIconButton`, `EditorialEmptyState`.
- Produces: `EditorialBottomBar(selectedDestination, onHome, onLibrary, onAddText)` and `enum class EditorialDestination { HOME, LIBRARY }`.
- All primitives consume `MaterialTheme` and `FrenchReaderDesign`; they contain no screen state, repository access, or navigation controller.

- [ ] **Step 1: Write interaction/semantics tests**

Create tests that render under `FrenchReaderTheme(ThemeMode.LIGHT)` and verify:

```kotlin
@Test fun searchEntryIsOneClickableControl() {
    var clicks = 0
    composeRule.setContent {
        FrenchReaderTheme(ThemeMode.LIGHT) {
            EditorialSearchEntry(text = "Rechercher…", onClick = { clicks++ })
        }
    }
    composeRule.onNodeWithText("Rechercher…").performClick()
    assertEquals(1, clicks)
}

@Test fun bottomBarExposesSelectedDestinationAndAddAction() {
    composeRule.setContent {
        FrenchReaderTheme(ThemeMode.LIGHT) {
            EditorialBottomBar(
                selectedDestination = EditorialDestination.HOME,
                onHome = {}, onLibrary = {}, onAddText = {}
            )
        }
    }
    composeRule.onNodeWithText("Home").assertIsSelected()
    composeRule.onNodeWithText("Add text").assertHasClickAction()
}
```

Use resource strings in the production bottom bar; tests may render with explicit labels through an internal label-bearing overload if needed.

- [ ] **Step 2: Run the test and confirm failure**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.components.EditorialPrimitivesTest`

Expected: compilation fails because the primitive package does not exist.

- [ ] **Step 3: Implement primitives with stable visual contracts**

Implement the listed composables with these constraints:

- `EditorialTopAppBar` accepts title plus navigation/action slots, uses the editorial screen-title style, transparent/tonal background, and Material 3 window insets.
- `EditorialSearchEntry` is a single semantics node, 48 dp minimum height, 16 dp horizontal padding, search icon, one-line ellipsized text, medium shape, outline/tonal states, and Material ripple.
- `EditorialSectionHeader` accepts optional trailing content and uses editorial section typography.
- `MetadataBadge` is compact, never below a 24 dp visual height, and uses text plus optional icon so meaning is not color-only.
- `EditorialProgressIndicator` wraps `LinearProgressIndicator`, clamps values to `0f..1f`, animates with built-in short motion, and accepts a visible percentage label supplied by the caller.
- Button wrappers preserve Material enabled/loading semantics and 48 dp minimum targets; loading keeps a text label available to accessibility.
- `EditorialEmptyState` centers icon/title/body/action without fixed height.
- `EditorialBottomBar` keeps the existing Home/Library/Add labels and routes, uses ordinary Material selection indication (no oversized capsule), and emits no navigation itself.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.components.EditorialPrimitivesTest assembleDebug`

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/components app/src/androidTest/java/com/ziaee/frenchreader/ui/components/EditorialPrimitivesTest.kt
git commit -m "Add reusable editorial Compose primitives"
```

---

### Task 3: Reliable Home learning and reading metrics

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiState.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeUiState.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeViewModel.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/home/HomeStateTest.kt`

**Interfaces:**
- Extends `HomeUiState` with `savedTextCount`, `savedWordCount`, `learnedWordCount`, `dueReviewCount`, and `streakDays: Int`.
- Preserves and reuses `computeStreak(activeDates: Set<LocalDate>, today: LocalDate): Int`; produces `loadActiveDates(reviewDates: suspend () -> List<String>, activityDates: suspend () -> List<String>): Set<LocalDate>` as the one shared internal query-union/parser used by both Statistics and Home.
- Produces: `data class HomeReadingMetrics(val progressFraction: Float, val progressPercent: Int, val estimatedTotalMinutes: Int, val estimatedRemainingMinutes: Int)`.
- Produces: `homeReadingMetrics(doc: TextDocument, wordsPerMinute: Int = 200)` and `estimatedReadingMinutes(rawText, wordsPerMinute = 200)`.
- Changes `composeHomeState` to consume complete text/vocabulary snapshots, the shared active-date union, and `today`, derive `streakDays` with `computeStreak`, and still cap `recentTexts` to five.

- [ ] **Step 1: Add failing pure-state tests**

Add concrete tests for:

```kotlin
@Test fun `home derives saved learned and due counts without fake goals`() {
    val now = 1_000L
    val entries = listOf(
        vocab(learned = true, nextReviewAtMs = 0L),
        vocab(learned = false, nextReviewAtMs = 999L),
        vocab(learned = false, nextReviewAtMs = 1_001L)
    )
    val state = composeHomeState(
        headlines = emptyList(), allTexts = listOf(textDoc(1), textDoc(2)),
        vocabEntries = entries, activeDates = emptySet(),
        today = LocalDate.of(2026, 9, 17), nowMs = now, continueReading = null,
        isRefreshing = false, sourceErrors = emptyList(), importingKey = null
    )
    assertEquals(2, state.savedTextCount)
    assertEquals(3, state.savedWordCount)
    assertEquals(1, state.learnedWordCount)
    assertEquals(1, state.dueReviewCount)
}

@Test fun `home streak uses computeStreak over the same review and activity queries as Statistics`() = runTest {
    val today = LocalDate.of(2026, 9, 17)
    val activeDates = loadActiveDates(
        reviewDates = { listOf(today.toString(), today.minusDays(2).toString()) },
        activityDates = { listOf(today.minusDays(1).toString()) }
    )
    val state = composeHomeState(
        headlines = emptyList(), allTexts = emptyList(), vocabEntries = emptyList(),
        activeDates = activeDates, today = today, nowMs = 1_000L,
        continueReading = null, isRefreshing = false,
        sourceErrors = emptyList(), importingKey = null
    )
    assertEquals(computeStreak(activeDates, today), state.streakDays)
    assertEquals(3, state.streakDays)
}

@Test fun `reading metrics clamp progress and round nonempty reading time up`() {
    val doc = TextDocument(title = "Long", rawText = List(401) { "mot" }.joinToString(" "))
    val metrics = homeReadingMetrics(doc)
    assertEquals(3, metrics.estimatedTotalMinutes)
    assertTrue(metrics.progressFraction in 0f..1f)
}
```

The two lambdas are the test seam for the exact `db.reviewLogDao().distinctActiveDates()` and `db.activityLogDao().activeDates()` calls used by both screens. Also test duplicate dates across the two lists, empty/malformed-free DAO results, empty text (0 minutes), overdue learned entries (not due), future unlearned entries (not due), a last-chunk document (100%), and out-of-range chunk indices (clamped).

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.home.HomeStateTest'`

- [ ] **Step 3: Implement pure derivation**

Use `TextChunker.chunk(doc.rawText)` for chunk-level progress and a Unicode-whitespace word split for reading time. Round non-empty minutes upward with `ceil(words / wordsPerMinute)`; never report zero minutes for non-empty text. Remaining time is based on chunks after the current saved chunk, clearly displayed as an estimate. Keep the existing `computeStreak` implementation unchanged; move only the two DAO calls plus date-string union/parsing into `loadActiveDates`, with the DAO reads supplied as suspend lambdas so the helper remains unit-testable. Call that helper from both Statistics and Home. Do not store any derived value.

Update `composeHomeState` to:

```kotlin
recentTexts = allTexts.take(MAX_RECENT_TEXTS)
savedTextCount = allTexts.size
savedWordCount = vocabEntries.size
learnedWordCount = vocabEntries.count { it.learned }
dueReviewCount = vocabEntries.count { !it.learned && it.nextReviewAtMs <= nowMs }
streakDays = computeStreak(activeDates, today)
```

- [ ] **Step 4: Feed existing Room flows into Home**

In `HomeViewModel`, replace `observeRecent()` in the `combine` with `db.textDao().observeAll()`, add `db.vocabDao().observeAll()`, and add an active-date flow driven by `db.invalidationTracker.createFlow("review_log", "activity_log", emitInitialState = true)`. Map each initial emission/table invalidation through the shared helper using the exact calls `db.reviewLogDao().distinctActiveDates()` and `db.activityLogDao().activeDates()`, then include that flow in the existing `combine`. Pass the resulting set, `LocalDate.now()`, and `System.currentTimeMillis()` into the pure composer each time the combined flows emit. Keep headline refresh/search/import methods unchanged.

In `StatisticsScreen`, replace its inline union/parsing expression with the same `loadActiveDates` helper and the same two DAO-call lambdas before calling `composeStatisticsState`. This preserves the shipped Statistics calculation, leaves both DAO interfaces and SQL unchanged, and prevents Home from duplicating the query/union logic.

The due count refreshes when vocabulary changes or Home is recreated; do not add timers, workers, schema changes, or duplicate DAO methods for a midnight-only edge case.

- [ ] **Step 5: Verify and commit**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.home.HomeStateTest' assembleDebug`

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiState.kt app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt app/src/main/java/com/ziaee/frenchreader/ui/home/HomeUiState.kt app/src/main/java/com/ziaee/frenchreader/ui/home/HomeViewModel.kt app/src/test/java/com/ziaee/frenchreader/ui/home/HomeStateTest.kt
git commit -m "Derive reliable Home learning and reading metrics"
```

---

### Task 4: Localized Phase 1 copy

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`

**Interfaces:**
- Produces matching keys consumed by Tasks 5–6.
- Existing `LocalizationCompletenessTest` enforces exact key parity.

- [ ] **Step 1: Add all keys to all three locales in one change**

Add this exact key set, with natural translations rather than sharing English copy:

| Key | English/default | Persian | French |
|---|---|---|---|
| `home_brand_title` | Lire en français | Lire en français | Lire en français |
| `home_search_hint` | Search a topic, word, or article… | جست‌وجوی موضوع، واژه یا مقاله… | Rechercher un sujet, un mot ou un article… |
| `home_summary_learned` | %1$d learned | %1$d واژه آموخته | %1$d appris |
| `home_summary_saved_words` | %1$d saved words | %1$d واژه ذخیره | %1$d mots enregistrés |
| `home_summary_saved_texts` | %1$d texts | %1$d متن | %1$d textes |
| `home_summary_streak_days` | %1$d-day streak | %1$d روز پیاپی | %1$d jours d'affilée |
| `home_section_daily_review` | Today’s Review | مرور امروز | Révision du jour |
| `home_review_due` | %1$d words are ready | %1$d واژه آمادهٔ مرور است | %1$d mots sont prêts |
| `home_review_due_support` | A short review keeps your vocabulary active. | یک مرور کوتاه واژگان را فعال نگه می‌دارد. | Une courte révision entretient votre vocabulaire. |
| `home_review_start` | Start | شروع | Commencer |
| `home_review_complete` | You’re all caught up | مرور امروز تمام شد | Tout est à jour |
| `home_review_complete_support` | No words are due right now. | اکنون واژه‌ای برای مرور نیست. | Aucun mot n’est à réviser pour le moment. |
| `home_action_resume` | Resume | ادامه | Reprendre |
| `home_progress_percent` | %1$d%% complete | %1$d٪ خوانده‌شده | %1$d %% terminé |
| `home_remaining_minutes` | About %1$d min left | حدود %1$d دقیقه مانده | Environ %1$d min restantes |
| `home_reading_complete` | Reading complete | مطالعه کامل شد | Lecture terminée |
| `home_estimated_minutes` | %1$d min read | مطالعهٔ %1$d دقیقه‌ای | Lecture de %1$d min |
| `home_more_actions` | More Home actions | گزینه‌های بیشتر خانه | Plus d’actions d’accueil |
| `home_action_import_file` | Import a file | افزودن از فایل | Importer un fichier |
| `home_action_vocabulary` | Saved vocabulary | واژگان ذخیره‌شده | Vocabulaire enregistré |
| `home_action_statistics` | Learning statistics | آمار یادگیری | Statistiques d’apprentissage |
| `home_news_image_unavailable` | Article image unavailable | تصویر مقاله در دسترس نیست | Image de l’article indisponible |

Keep existing keys and translations. XML-escape apostrophes consistently with the existing files.

- [ ] **Step 2: Run localization checks**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.LocalizationCompletenessTest'`

Expected: both key-parity and hardcoded-Persian checks pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-fa/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "Localize editorial Home copy"
```

---

### Task 5: Redesign the Home scaffold and preserve every entry point

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeScreen.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt`

**Interfaces:**
- Adds `onStartReview: () -> Unit` to `HomeScreen` and `HomeContent`.
- Preserves all current callbacks; `onFilePickerClick`, `onOpenVocab`, and `onOpenStatistics` move into overflow rather than disappearing.
- Consumes `EditorialTopAppBar`, `EditorialSearchEntry`, and `EditorialBottomBar` from Task 2.

- [ ] **Step 1: Update the Home UI test contract first**

Render `HomeContent` under `FrenchReaderTheme`, add `onStartReview`, and assert:

- `Lire en français`, refined search hint, Today’s News, Today’s Review, Continue Reading, and My Texts are present for populated state.
- Only Search, Settings, and More are direct header actions; opening More exposes Import file, Saved vocabulary, and Learning statistics.
- Search field and header Search both call the same callback.
- Due state shows the count and Start; Start calls `onStartReview` once.
- Zero due shows the calm completed copy and no disabled Start button.
- Home is selected in bottom navigation; Library and Add retain their callbacks.

- [ ] **Step 2: Run the Home UI test and confirm failure**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.home.HomeScreenTest`

- [ ] **Step 3: Wire the existing review route**

In `MainActivity`’s Home destination, add:

```kotlin
onStartReview = { navController.navigate("vocab_review/$VOCAB_SCOPE_ALL") }
```

No new route is created. Back behavior remains the existing review-screen `popBackStack()`.

- [ ] **Step 4: Replace the Home chrome**

In `HomeContent`:

- Use `EditorialTopAppBar` with the exact brand title.
- Keep Search and Settings visible.
- Add one `MoreVert` action with `DropdownMenu` items for file import, vocabulary, and statistics; each item closes the menu before invoking its existing callback.
- Keep the existing Snackbar host and pull-to-refresh behavior.
- Use `EditorialBottomBar(EditorialDestination.HOME, ...)` with the existing Home/Library/Add actions.
- Use a spaced `LazyColumn` with 16 dp horizontal content gutters and 24/32 dp section rhythm; avoid fixed container heights for localized text.
- Render, in order: wide search entry, compact learning summary, Today’s News, daily review, conditional Continue Reading, My Texts.
- Preserve `FindArticleSheet`, `AddTextHost`, and `NewsPreviewSheet` wiring exactly.

The learning strip shows learned words, saved words/texts, and a real streak from state. The streak reuses `computeStreak` over the same review-log and activity-log dates as the shipped Statistics screen. Weekly-goal progress remains omitted because no stored or configurable goal target exists anywhere in the app; this follows the spec’s “omit or simplify rather than invent” rule.

- [ ] **Step 5: Keep directionality explicit at content boundaries**

Let the scaffold, menu, labels, and metrics inherit ambient direction. Wrap French article titles/snippets in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` and use start alignment inside that LTR scope. Do not force the whole Home root LTR in Persian.

- [ ] **Step 6: Verify and commit**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.home.HomeScreenTest assembleDebug`

```bash
git add app/src/main/java/com/ziaee/frenchreader/MainActivity.kt app/src/main/java/com/ziaee/frenchreader/ui/home/HomeScreen.kt app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt
git commit -m "Redesign Home shell and navigation actions"
```

---

### Task 6: Editorial Home sections, carousel, and preview

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeComponents.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/home/NewsPreviewSheet.kt`
- Modify: `app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt`
- Modify: `app/src/androidTest/java/com/ziaee/frenchreader/LocalizationDirectionTest.kt`

**Interfaces:**
- Keeps `TodayNewsSection`, `ContinueReadingCard`, `RecentTextsSection`, `NewsCardImage`, and `DocumentThumbnail` callable from Home.
- Adds `LearningSummaryStrip` and `DailyReviewCard`.
- `ContinueReadingCard` consumes `HomeReadingMetrics`; `RecentTextsSection` uses `estimatedReadingMinutes`.

- [ ] **Step 1: Add carousel and direction regression tests**

Give cards stable test tags such as `news-card-${headline.sourceId}-${headline.externalId}`. At a 360 dp test width with two headlines, assert the second card starts inside the viewport while its right edge extends beyond it, proving the adjacent-card peek. Click the first card and retain the existing preview/download-action assertion.

Update `LocalizationDirectionTest` to render with the new `onStartReview` callback and `FrenchReaderTheme`; retain both assertions: root follows RTL/LTR ambient direction, French headline stays LTR in the unmerged tree.

- [ ] **Step 2: Implement the compact learning summary and daily review states**

`LearningSummaryStrip` is one low-elevation tonal surface with three equal metric cells, short localized labels, and dividers that remain visible in both themes: a flame-icon streak cell showing `streakDays`, a learned-words cell, and a saved cell grouping the real saved-word/saved-text counts. The streak is sourced from `computeStreak` over the shared review/activity active-date union; no display value is fabricated.

`DailyReviewCard` has two explicit branches:

```kotlin
if (dueCount > 0) {
    // count + support copy + enabled primary Start action
} else {
    // check icon + completed title/support copy; no disabled button
}
```

Use muted teal for the learning container and oxblood for the primary action; amber is limited to small emphasis/progress details. The icon and wording distinguish states without color.

- [ ] **Step 3: Rebuild Today’s News as a responsive snapping carousel**

Use a remembered `LazyListState`, `LazyRow`, `rememberSnapFlingBehavior(lazyListState)`, 16 dp outer content padding, and 12/16 dp gaps. Each item uses `Modifier.fillParentMaxWidth(0.86f)` rather than a fixed 220 dp width, so one prominent card and part of the next are visible across phone widths.

Each card:

- Uses an image with a stable aspect ratio rather than a fixed text-bearing height.
- Applies a functional bottom image scrim only to guarantee headline contrast.
- Uses editorial serif/Vazirmatn headline styling and explicit LTR for title/snippet.
- Shows source as a badge and localized publication date when available.
- Uses a deliberate source-tinted placeholder with icon plus the localized “image unavailable” semantics when Coil fails or the URL is absent.
- Keeps the entire card clickable with ripple and a 48 dp practical target.

Do not show category, CEFR badge, or reading time: `HeadlineEntity` cannot support them reliably without downloading the article, and opening Home/preview must not download bodies. Do not add pagination dots; the peek and snapping communicate position accurately without maintaining a second indicator model.

Keep existing partial-error copy nonblocking above the carousel and existing full-error Retry state when both sources fail with no cache.

- [ ] **Step 4: Redesign Continue Reading from stored document data**

Create a bookmark-style card with 72 dp thumbnail, serif/LTR title, source or localized created date, visible progress bar + percentage, estimated remaining time (or completion label), and Resume action. Use `homeReadingMetrics(doc)` and do not persist estimates. Allow the title to wrap to two lines; do not fix the card height.

- [ ] **Step 5: Redesign My Texts as compact editorial rows**

Keep at most five state-provided documents and See All. Each row has a 48 dp thumbnail, two-line LTR title, source-or-date metadata, and locally derived total reading time. Preserve row click behavior. Do not invent difficulty/vocabulary counts and do not add delete/overflow actions that Home does not currently own.

When empty, use `EditorialEmptyState` with the existing helpful copy and the existing Add Text action passed down from `HomeContent`; update `RecentTextsSection` to accept `onAddText`. This is the same add dialog, not a new screen.

- [ ] **Step 6: Bring the Home preview sheet into the system**

In `NewsPreviewSheet`, replace literal padding/shape/elevation values with tokens, keep French title/snippet LTR, use editorial headline style, style metadata as badges/secondary text, and use `EditorialPrimaryButton` for download/open/loading. Preserve its non-fetching open behavior, duplicate state, retryable error, and callbacks.

- [ ] **Step 7: Run focused tests and build**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.home.HomeStateTest'
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.home.HomeScreenTest,com.ziaee.frenchreader.LocalizationDirectionTest
./gradlew assembleDebug
```

Expected: unit/UI tests pass and APK builds.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/home/HomeComponents.kt app/src/main/java/com/ziaee/frenchreader/ui/home/NewsPreviewSheet.kt app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt app/src/androidTest/java/com/ziaee/frenchreader/LocalizationDirectionTest.kt
git commit -m "Apply editorial design system to Home content"
```

---

### Task 7: Phase 1 verification gate

**Files:**
- No source changes expected; fix only Phase 1 regressions in the files listed above.

- [ ] **Step 1: Run automated verification**

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

Expected: all unit tests, Android lint, build, and connected tests pass. If no emulator/device is available, record `connectedDebugAndroidTest` as unexecuted rather than claiming it passed; all JVM tests/lint/build must still pass.

- [ ] **Step 2: Verify Home manually in all interface directions/themes**

On a small phone/emulator and a typical modern phone, check this matrix:

- English light/dark.
- French light/dark, including long labels/headlines.
- Persian light/dark with RTL chrome and LTR French cards.
- Default and largest system font scale.

For each, verify no clipping/overlap, 48 dp practical touch targets, readable contrast, logical TalkBack order/content descriptions, and that selected/completed/error/loading states remain understandable without color.

- [ ] **Step 3: Verify every preserved Home flow**

1. Initial refresh and pull-to-refresh retain cached cards while loading.
2. Partial source failure keeps cached/other-source news; total empty failure shows Retry.
3. Horizontal news swipe snaps smoothly, shows the adjacent-card peek, and does not hijack vertical scrolling.
4. Missing/broken news images use the deliberate placeholder.
5. Headline preview opens without body download; Download/Open Existing behavior still works.
6. Wide search field and header Search open the existing unified content-search sheet.
7. More menu opens file import, Vocabulary, and Statistics.
8. Add bottom action still opens the existing add-text dialog; incoming share behavior remains intact.
9. Daily review Start opens the existing all-vocabulary review; zero due shows the completed state.
10. Continue Reading and My Texts open the correct stored document; See All opens Library.
11. Theme changes still persist through `AppearancePrefs`; reading background/font-size preferences and Reading screen appearance are unchanged.

- [ ] **Step 4: Review the Phase 1 diff for scope**

Run: `git diff --check` and `git status --short`.

Confirm no production fake data, no new dependency, no Room schema change, and no redesign edits to Library/Reading/Dictionary/Vocabulary/Settings/Statistics. Commit any verification-only fixes with a narrowly scoped message.

---

## Phase 1 Acceptance Criteria

- Light mode is warm ivory/warm white with carbon text, oxblood primary, muted teal secondary, restrained amber emphasis, soft warm dividers, and subtle elevation.
- Dark mode is warm near-black/layered charcoal with warm off-white text, softened red, desaturated teal, and low-contrast borders; no pure-black or purple-dominant fallback remains.
- `ThemeMode` persistence/system resolution and independent `ReadingBackground`/`FontScale` behavior remain unchanged.
- Semantic color, typography, spacing, shape, size, and elevation tokens exist and Home does not hardcode palette colors.
- Shared primitives cover the Phase 1 app bar, search entry, section header, metadata badge, progress, button/icon target, empty state, and bottom navigation needs.
- Home is visibly and structurally different: editorial brand header, refined wide search, compact real-data learning strip, immersive news carousel, daily review state, progress-rich Continue Reading, and compact My Texts.
- Today’s News is horizontally scrollable with built-in snapping and a partially visible neighboring card; it remains vertically scroll-compatible.
- News photographs remain readable under a controlled scrim, and missing images have an intentional accessible placeholder.
- Headline source/date are shown; unavailable category, CEFR, and full-article reading-time fields are omitted rather than fabricated.
- The learning summary shows learned words, saved words/texts, and a real reading streak derived by the shipped `computeStreak` function from review-log plus activity-log dates; it does not show a weekly-goal percentage or ring.
- Daily review uses the existing Leitner due rule and existing review route; no-due state is calm and complete, not a disabled empty rectangle.
- Continue Reading shows derived, clamped progress/percentage and an explicitly estimated remaining time; no new persistence or fake precision is introduced.
- Search, file import, add/paste/share, preview/import/reopen, Library, Vocabulary, Statistics, Settings, review, pull-to-refresh, and Reading navigation all remain reachable and functional.
- English/French/Persian string sets stay key-identical; no new hardcoded Persian UI literal is introduced.
- Persian UI uses Vazirmatn and ambient RTL; French headlines/snippets/titles use an editorial serif role and remain LTR with correct accents/punctuation.
- UI works on small/typical phones and largest tested font scale without fixed-height text clipping; touch targets and TalkBack descriptions meet the scoped accessibility requirements.
- Unit tests, lint, debug build, and available connected tests pass.

## Explicitly Out of Scope

- Redesigning Library, Reading, Dictionary, Vocabulary list/review, Settings, Statistics, AI assistant, or their screen-specific components.
- Migrating Library to the new shared bottom bar in this phase; its current bottom bar remains until the Library phase.
- Changing reading typography, reading palettes, text selection, TTS gestures, contextual actions, dictionary tabs, AI behavior, or external-app integration.
- Adding database columns, migrations, APIs, background refresh workers, analytics, image generation, or downloaded font infrastructure.
- Adding or faking news category, CEFR/difficulty, full-article reading time, weekly-goal targets/progress, recommendations, or pagination indicators; the real derived reading streak is in scope.
- Reworking `FindArticleSheet`, `AddTextDialog`, or other shared sheets beyond ensuring the new theme continues to style them coherently.
- A pixel-perfect reproduction of mockups; this phase adapts the direction to current Compose/data/navigation constraints.
