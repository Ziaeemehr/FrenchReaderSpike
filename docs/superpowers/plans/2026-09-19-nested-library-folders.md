# Nested Library Folders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat Library folder model with an unlimited-depth, cycle-safe nested folder tree across Room, filtering, ViewModel behavior, Compose UI, localization, and tests.

**Architecture:** Store a nullable self-parent identifier on each `LibraryFolder`, keep tree operations as pure functions in `LibraryFolderTree.kt`, and have the ViewModel enforce move and sibling-name rules before DAO writes. Compose derives breadcrumbs, direct-child navigation, indented trees, and full folder paths from the same pure helpers.

**Tech Stack:** Kotlin, Android Room, Jetpack Compose Material 3, JUnit 4, AndroidX migration tests.

**Spec:** User-approved specification in the 2026-09-19 task request.

## Global Constraints

- Do not run Gradle.
- Preserve existing callers with default parameters wherever possible.
- Keep folders unlimited-depth and guard pure traversal helpers against corrupt cycles.
- Use natural English, French, and Persian translations.
- Keep the organizer's typed-name-on-Close behavior.

---

### Task 1: Pure folder tree behavior and projection tests

**Files:**
- Create: `app/src/test/java/com/ziaee/frenchreader/ui/library/LibraryFolderTreeTest.kt`
- Modify: `app/src/test/java/com/ziaee/frenchreader/ui/library/LibraryOrganizerProjectionTest.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryFolderTree.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryUiState.kt`

**Interfaces:**
- Produces: `descendantIds`, `pathTo`, `childrenOf`, `canMoveFolder`, `flattenTree`.
- Produces: `projectLibrary(..., folders: List<LibraryFolder> = emptyList())` with descendant-aware folder matching.

- [x] Write unit tests for descendants, root-to-node paths, legal/illegal moves including cycles, alphabetical depth-first flattening, corrupt-cycle termination, and descendant-inclusive projection.
- [x] Add the pure cycle-safe tree helpers.
- [x] Extend `projectLibrary` without breaking existing positional callers.

### Task 2: Room schema and DAO semantics

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Entities.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Daos.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt`
- Create: `app/src/androidTest/java/com/ziaee/frenchreader/data/Migration8To9Test.kt`

**Interfaces:**
- Produces: `LibraryFolder.parentId: Long? = null`, `findFolderByName(name, parentId)`, and `moveFolder(id, parentId)`.
- Produces: transactional delete that promotes child folders and texts to the deleted folder's parent.

- [x] Add the migration test fixture and assertions for nullable `parentId` preserving existing rows as top-level.
- [x] Add `parentId`, database version 9, `MIGRATION_8_9`, and register it in `ALL_MIGRATIONS`.
- [x] Update DAO duplicate lookup, folder move, and delete promotion queries.

### Task 3: ViewModel folder operations

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryViewModel.kt`

**Interfaces:**
- Produces: `createFolder(name, parentId = null)`, `moveFolder(id, newParentId)`, and `createFolderAndMove(doc, name, parentId = null)`.

- [x] Pass folders to `projectLibrary`.
- [x] Enforce per-parent duplicate names for create, rename, and move.
- [x] Reject self/descendant moves and fall back to the deleted folder's parent filter.

### Task 4: Nested-folder Compose UI

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryScreen.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/library/LibraryOrganizerDialogs.kt`

**Interfaces:**
- Consumes: pure tree helpers and ViewModel operations from Tasks 1 and 3.
- Produces: breadcrumbs, direct-child chips, indented organizer/move lists, subfolder creation, folder moving, and full-path row badges.

- [x] Replace flat chips with top-level/direct-child navigation and clickable breadcrumb segments.
- [x] Add defaulted callbacks for parent-aware creation and folder moves.
- [x] Render organizer folders via `flattenTree` with 16 dp depth indentation and add subfolder, rename, move, and delete controls with test tags.
- [x] Add valid-target folder move dialog excluding the folder and all descendants.
- [x] Render text move choices as an indented tree and create new text-move folders at top level.
- [x] Display each text's full folder path with one-line ellipsis.

### Task 5: Localization and static verification

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Modify: `app/src/main/res/values-fa/strings.xml`

**Interfaces:**
- Produces: strings for add-subfolder, move-folder, top-level, breadcrumb root, and updated delete confirmation.

- [x] Add natural translations in all three locales.
- [x] Review the diff for signature compatibility, missing resource keys, tree-cycle safety, and full spec coverage.
- [x] Do not execute Gradle; report that runtime/build verification was intentionally not performed.
