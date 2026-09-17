# UI Redesign Phase 2: Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Library screen as an editorial collection on top of Phase 1's shared design system, replacing the always-visible per-row delete icon with an overflow action, distinguishing "library is empty" from "no search results," and migrating Library onto the shared bottom navigation now that both Home and Library share it.

**Architecture:** Reuse Phase 1's `EditorialTopAppBar`, `EditorialSearchEntry`, `EditorialBottomBar`/`EditorialDestination.LIBRARY`, `EditorialEmptyState`, `MetadataBadge`, and `FrenchReaderDesign` tokens. Keep `LibraryViewModel`'s pure `projectLibrary` query/sort untouched; only extend `LibraryUiState` with the minimum derived fields the redesigned rows need. No schema change.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 (Compose BOM 2024.06.00), Room/Flow, JUnit 4, Compose UI tests.

**Spec:** `docs/Redesign the application's user interfac.md` — only the "## Library Screen" section, plus the shared global constraints (RTL, localization, accessibility, technical constraints) as they apply to Library.

## Global Constraints

- Reuse Phase 1 primitives and tokens; do not redefine colors/typography/spacing here.
- Do not add a delete icon that is permanently visible on every row; replace it with a per-row overflow menu (`Icons.Default.MoreVert`), reusing the confirmation `AlertDialog` pattern already used here and in `VocabListScreen.kt` rather than inventing swipe-to-delete or a multi-select mode.
- Distinguish "library has zero saved texts" from "the current search has zero matches" -- today both show the same `library_empty` string, which is actually worded as a no-results message.
- Do not invent a difficulty/CEFR field or a "reading status" beyond what `TextChunker`-derived completion already supports (see Home's precedent: `isTextCompleted` in `StatisticsUiState.kt`). Library has no network dependency (`db.textDao().observeAll()` is a local Room `Flow`), so there is no real Loading or Error state to build; both are explicitly out of scope.
- Every new user-facing string is defined with the same key in `values`, `values-fa`, and `values-fr`.
- Do not add a dependency.
- Interactive targets stay at least 48 dp; icons keep localized content descriptions; selected/empty states do not rely on color alone.

---

## Current Code Audit

- `LibraryScreen.kt` uses a plain `TopAppBar`/`NavigationBar` (not yet migrated to `EditorialTopAppBar`/`EditorialBottomBar` -- Phase 1's Task 1 explicitly deferred this). Search is a raw `OutlinedTextField`, not `EditorialSearchEntry`. Sort is a `DropdownMenu` off a `Sort` icon -- keep this control and its three existing `LibrarySort` options unchanged; only restyle it.
- `LibraryRow` shows a 48 dp `DocumentThumbnail`, title, source, and an always-visible trailing `IconButton(Icons.Default.Delete)` -- exactly the anti-pattern the spec calls out. `VocabListScreen.kt` uses the identical always-visible-icon + `AlertDialog` pattern; Phase 2 only touches Library, but the overflow-menu treatment here should be the one worth copying into Vocabulary later (not in this phase).
- `LibraryUiState` has `query`, `sort`, `documents` (already-filtered/sorted `List<TextDocument>`). `projectLibrary` in `LibraryUiState.kt` is a pure, already-tested function (`LibrarySortTest.kt`) filtering by title/source and sorting by NEWEST/TITLE/LAST_READ with `id` as a stable tie-breaker. No changes needed to this function.
- `library_empty` = "No texts match your search." is shown whenever `state.documents.isEmpty()`, regardless of whether `query` is blank (library genuinely empty) or non-blank (no matches) -- these need two distinct strings/states.
- `TextDocument` (`data/Entities.kt`) has no difficulty/CEFR/category field. It does have `rawText`/`lastChunkIndex`, so `isTextCompleted(doc)` (already defined in `ui/statistics/StatisticsUiState.kt`, pure and tested) can supply a real "read"/"in progress" status without inventing data. `createdAtMs` and `lastAccessedAtMs` already exist for date display.
- `LibraryScreen`'s `AddTextHost`/`rememberAddTextUiState`/`rememberFilePickerLauncher` wiring is shared with Home (`ui/shared/TextImportComponents.kt`) and must keep working unchanged.
- Existing string keys: `library_sort_newest/title/last_read`, `library_search_hint`, `library_empty`, `library_delete_confirm_title/message`, `accessibility_sort`, `accessibility_delete`, `action_delete`, `action_cancel`, `nav_library`, `nav_home`, `action_add_text` -- Phase 2 keeps all of these except `library_empty`, which is replaced by two more precisely named keys.

---

## Target File Map

**Create:**
- `app/src/androidTest/java/com/ziaee/frenchreader/ui/library/LibraryScreenTest.kt` — Compose UI test for the redesigned screen (no prior instrumented test exists for Library).

**Modify:**
- `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryUiState.kt` — add `isTextCompleted`-derived status to what's exposed per row (as a small pure helper, not a stored field).
- `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryScreen.kt` — editorial scaffold, search, sort, rows, overflow-menu delete, distinct empty/no-results states.
- `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml` — replace `library_empty` with `library_empty_title`/`library_empty_body` and `library_no_results`; add `library_row_more_actions`.
- `app/src/test/java/com/ziaee/frenchreader/ui/library/LibrarySortTest.kt` — no logic changes expected; re-run as a regression check only.

**Explicitly unchanged:** `LibraryViewModel.kt`'s public API, `projectLibrary`, `LibrarySort`, `Entities.kt`, `AppDatabase.kt`, `ui/shared/TextImportComponents.kt`, `ui/statistics/*`.

---

### Task 1: Distinguish empty-library from no-search-results

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryUiState.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/library/LibrarySortTest.kt`

**Interfaces:**
- Adds `val LibraryUiState.isSearching: Boolean get() = query.isNotBlank()` (or an equivalent pure val/property) so the screen can pick the right empty-state copy without re-deriving the blank check itself.

- [ ] **Step 1: Add a failing test**

```kotlin
@Test fun `isSearching reflects whether a query is active`() {
    assertFalse(LibraryUiState(query = "").isSearching)
    assertFalse(LibraryUiState(query = "   ").isSearching)
    assertTrue(LibraryUiState(query = "zeb").isSearching)
}
```

- [ ] **Step 2: Implement and verify**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.library.LibrarySortTest'`

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryUiState.kt app/src/test/java/com/ziaee/frenchreader/ui/library/LibrarySortTest.kt
git commit -m "Distinguish empty library from no search results in LibraryUiState"
```

---

### Task 2: Localized copy for the redesigned states

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml`

**Interfaces:** Adds these exact-parity keys (replacing `library_empty`):

| Key | English/default | Persian | French |
|---|---|---|---|
| `library_empty_title` | No texts yet | هنوز متنی نیست | Aucun texte pour l’instant |
| `library_empty_body` | Save an article or paste your own text to build your library. | یک مقاله ذخیره کنید یا متن خودتان را بچسبانید تا کتابخانه‌تان ساخته شود. | Enregistrez un article ou collez votre propre texte pour créer votre bibliothèque. |
| `library_no_results` | No texts match “%1$s”. | متنی با «%1$s» مطابقت ندارد. | Aucun texte ne correspond à « %1$s ». |
| `library_row_more_actions` | More actions for “%1$s” | گزینه‌های بیشتر برای «%1$s» | Plus d’actions pour « %1$s » |

- [ ] **Step 1: Remove `library_empty` and add the four keys above to all three files.**
- [ ] **Step 2: Verify**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.LocalizationCompletenessTest'`

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-fa/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "Localize Library empty/no-results/overflow copy"
```

---

### Task 3: Redesign the Library scaffold, search, and sort

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryScreen.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/ui/library/LibraryScreenTest.kt`

**Interfaces:**
- `LibraryScreen(onOpenText, onOpenHome)` keeps its exact signature.
- Extracts a stateless `LibraryContent(state, onQueryChange, onSortSelect, onOpen, onDeleteRequest, onAddText, onOpenHome, ...)` composable (mirroring Home's `HomeContent` split) so a UI test can drive it without a real ViewModel -- match `HomeScreenTest.kt`'s pattern of a `setLibraryContent` test helper.

- [ ] **Step 1: Write the UI test first**

Render `LibraryContent` under `FrenchReaderTheme(ThemeMode.LIGHT)` and assert:
- Populated state: title, search entry, sort control, and each document's title are present.
- Empty library (`documents = emptyList()`, `query = ""`): `library_empty_title`/`library_empty_body` and an "Add text" action are shown; `library_no_results` is not.
- No search results (`documents = emptyList()`, `query = "zzz"`): `library_no_results` (formatted with the query) is shown; `library_empty_title` is not.
- Opening a row's overflow menu shows a Delete item; tapping it and then confirming in the dialog invokes the delete callback exactly once; dismissing the dialog invokes it zero times.
- Home is not selected and Library is selected in the shared bottom bar; tapping Home invokes `onOpenHome`.

- [ ] **Step 2: Run and confirm failure**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.library.LibraryScreenTest`

- [ ] **Step 3: Implement the redesigned scaffold**

- Replace `TopAppBar` with `EditorialTopAppBar(title = stringResource(R.string.nav_library), actions = { file-import icon, existing Sort dropdown })`. Keep the exact three `LibrarySort` menu items and `vm.setSort` wiring.
- Replace the `OutlinedTextField` with `EditorialSearchEntry`-style search: since Library's search is live-as-you-type (unlike Home's tap-to-open-sheet entry), keep a real text field but restyle it with `FrenchReaderDesign` tokens/shape rather than switching to `EditorialSearchEntry` (which is a non-editable trigger, not an input) -- add a short note inline explaining why Library's field stays a real `OutlinedTextField`/`TextField` instead of reusing that specific Home primitive.
- Replace `NavigationBar`/`NavigationBarItem` with `EditorialBottomBar(selectedDestination = EditorialDestination.LIBRARY, onHome = onOpenHome, onLibrary = {}, onAddText = { addTextState.openBlank() })`.
- Branch the content area three ways: `state.documents.isEmpty() && !state.isSearching` → `EditorialEmptyState` with `library_empty_title`/`library_empty_body` and an `action_add_text` action wired to `addTextState.openBlank()`; `state.documents.isEmpty() && state.isSearching` → centered `library_no_results` text (formatted with `state.query`); otherwise the existing `LazyColumn` of rows.

- [ ] **Step 4: Verify and commit**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.library.LibraryScreenTest assembleDebug`

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryScreen.kt app/src/androidTest/java/com/ziaee/frenchreader/ui/library/LibraryScreenTest.kt
git commit -m "Redesign Library scaffold, search, sort, and empty/no-results states"
```

---

### Task 4: Move the delete affordance into a per-row overflow menu

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryScreen.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/ui/library/LibraryScreenTest.kt` (already covers this from Task 3, Step 1 -- no new test file)

**Interfaces:** `LibraryRow` gains a trailing `IconButton(Icons.Default.MoreVert)` + `DropdownMenu` with a single `Delete` item (styled like Home's overflow menu), replacing the permanently-visible `Delete` icon. Confirmation stays the same `AlertDialog`, triggered from the menu item instead of directly from the row.

- [ ] **Step 1: Implement**

Restyle `LibraryRow` to use `FrenchReaderDesign` spacing/typography and `isTextCompleted(doc)` (imported from `com.ziaee.frenchreader.ui.statistics`) to show a small `MetadataBadge`("Read"/"In progress" -- add two more localized keys, `library_status_read`/`library_status_in_progress`, to Task 2's table before implementing) next to the source line. Do not add a difficulty badge.

- [ ] **Step 2: Verify and commit**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.ui.library.LibraryScreenTest assembleDebug`

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryScreen.kt
git commit -m "Replace Library's always-visible delete icon with a per-row overflow menu"
```

---

### Task 5: Phase 2 verification gate

- [ ] **Step 1: Automated verification**

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

- [ ] **Step 2: Manual verification matrix**

English/French/Persian × light/dark, largest font scale: empty library, no-results, populated list, delete confirm/cancel, sort menu, Home↔Library navigation via the shared bottom bar, Add Text from both the empty state and the bottom bar.

- [ ] **Step 3: Scope check**

`git status --short` and `git diff --check`: confirm no changes outside `ui/library/*`, the three `strings.xml` files, and the new test file; no schema change; no new dependency; no fabricated difficulty/CEFR data.

## Phase 2 Acceptance Criteria

- Library uses `EditorialTopAppBar`, the shared `EditorialBottomBar` (Library selected), and Phase 1 tokens throughout -- no hardcoded colors.
- No row shows a permanently visible delete icon; deletion is reachable only via a per-row overflow menu plus the existing confirm/cancel dialog.
- An empty library (no saved texts, no query) shows a centered icon, `library_empty_title`/`library_empty_body`, and a working "Add text" action.
- A query with zero matches shows `library_no_results` (naming the query) instead of the empty-library copy.
- Populated rows show thumbnail, title, source, date, and a real (non-fabricated) read/in-progress status derived from `isTextCompleted`.
- Sort (Newest/Title/Last read) and search-by-title-or-source behave exactly as before; `projectLibrary`'s tests are unchanged and passing.
- English/French/Persian string sets stay key-identical; Persian renders RTL with Vazirmatn; French titles/sources stay LTR.
- Unit tests, lint, debug build, and instrumented tests pass.

## Explicitly Out of Scope

- Loading and Error states (Library has no network dependency; a local Room `Flow` has no realistic loading/error condition to design for).
- Difficulty/CEFR labels or any new content-classification field.
- Swipe-to-delete or a multi-select/selection-mode UI -- the overflow menu satisfies the spec's "not a permanently exposed icon" requirement with the smallest change.
- Changing `LibrarySort`'s options, `projectLibrary`'s filter/sort behavior, or the Add Text/file-import flows.
- Applying the same overflow-menu treatment to `VocabListScreen.kt` (noted as a good future follow-up, not part of this phase).
- A pixel-perfect reproduction of any mockup; this phase adapts the direction to Library's actual data and existing interaction patterns.
