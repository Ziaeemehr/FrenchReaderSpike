# Home Dashboard, News Discovery, Library, and Localization Design

Date: 2026-09-16

## Summary

Replace the current text-list landing page with a structured Home dashboard. Home will make fresh news discoverable without automatically downloading full articles, keep the existing cross-source search available, surface the user's current reading, and show only a small recent subset of downloaded texts. The complete downloaded collection moves to a dedicated Library screen.

The same release will establish proper application localization for Persian, French, and English, plus a system-language option. Persian interface text will use a bundled Vazirmatn font. French article content and its existing reading typography remain unchanged.

## Goals

- Give the app a clear, useful Home screen instead of an unstructured list.
- Show recent RFI Français Facile and France Info headlines together, ordered by publication time.
- Let the user preview a headline before choosing to download its full article.
- Preserve the existing Vikidia/RFI/France Info topic search.
- Separate the full downloaded collection into a searchable, sortable Library.
- Make cached headlines and the user's library useful when the network is unavailable.
- Support System, Persian, French, and English interface-language choices.
- Keep French learning content in French and left-to-right regardless of interface language.

## Non-goals

- Personalized content recommendations.
- Automatic background downloading of full news articles.
- Adding sources beyond RFI Français Facile and France Info to the daily-news section.
- Translating article titles, snippets, or article bodies.
- A user-selectable article font. This may be designed separately later; the existing article font and font-size setting remain in place.
- Reading statistics or a completed/uncompleted reading model.

## Information Architecture

### Home

The sections appear in this order:

1. Top app bar with the app identity, Saved Vocabulary, and Settings.
2. A prominent search field that opens the existing multi-source content-search experience.
3. **Today's News**, displayed as horizontally scrolling image cards.
4. **Continue Reading**, showing the most recently active document when one exists.
5. **My Texts**, showing at most five recently added documents and a **See All** action.
6. Bottom navigation for Home, Library, and Add Text.

The existing file import and paste/add flows remain available through Add Text. Search remains a discovery action rather than a separate permanent tab.

### Library

Library contains every saved `TextDocument` and supports:

- Local search by title and source name.
- Sorting by newest added, title, or most recently read.
- Opening a document.
- Deleting a document.

To support accurate “most recently read” ordering, documents gain a last-access timestamp updated when their Reading screen is opened. Existing documents receive a safe default during migration.

### News Preview

Selecting a headline opens a preview surface containing:

- Image or source-specific placeholder.
- French headline.
- French RSS summary.
- Source name and publication time.
- **Download and Read** action.

The full article is not fetched merely by opening Home or the preview. Selecting **Download and Read** fetches and extracts the full text, persists the document and its image, then opens Reading.

If the article was already downloaded, the primary action becomes **Open in Library**. Deduplication uses stable source identity, primarily the normalized source URL, rather than the displayed title.

## Data Model

### Headline

A headline is distinct from a downloaded `TextDocument`. It contains:

- Stable source identifier.
- Source display name.
- Title.
- Summary/snippet.
- Article URL and source item identifier when available.
- Publication timestamp.
- Remote image URL, nullable.
- Cache update timestamp.

Headlines are stored in a small Room-backed cache. Cache replacement is performed per source only after a successful response, preventing a failed refresh from deleting usable data from that source. A bounded number of recent entries is retained.

### TextDocument additions

`TextDocument` gains:

- A nullable local `imagePath` for a downloaded image.
- A nullable stable external identifier or normalized source key for deduplication.
- A `lastAccessedAtMs` timestamp for Continue Reading and Library sorting.

The existing source attribution fields remain authoritative. A Room migration adds the fields without invalidating current documents.

### Image storage

Remote images are shown in news cards through an image loader with memory/disk caching. When an article is downloaded, its image is resized to a bounded resolution, JPEG-compressed where appropriate, and copied into the app's private storage. The relative path is stored in `TextDocument.imagePath`.

Deleting a document also deletes its owned image file. A missing or failed image never prevents a headline or downloaded text from being used.

## Components and Responsibilities

### News repository

The news repository coordinates only RFI Français Facile and France Info for Home. It:

- Requests recent items from both sources concurrently.
- Maps RSS data into `Headline` records.
- Extracts an image URL from RSS enclosure/media markup or, when needed, supported page metadata.
- Merges and sorts successful results by publication date descending.
- Writes successful source results to the headline cache.
- Exposes cached headlines immediately and refresh state separately.

The existing `ContentSource` implementations remain responsible for fetching full article text. Topic search continues using the existing multi-source aggregation, including Vikidia.

### Home view model

The Home view model combines independent streams for:

- Cached and refreshed headlines.
- Most recently accessed document.
- Five most recently created documents.
- Per-headline download/import state.

A failure in any stream does not replace usable data from the other streams.

### Library view model

The Library view model owns the local query, sort choice, document list, and deletion. Network state has no effect on Library browsing.

### UI decomposition

The current large `TextsListScreen` is split along feature boundaries instead of being expanded further:

- `HomeScreen`
- `TodayNewsSection`
- `NewsCard`
- `NewsPreviewSheet`
- `ContinueReadingCard`
- `RecentTextsSection`
- `LibraryScreen`
- Shared document-row/card components
- Existing content-search sheet, reused by Home

## Refresh and Offline Behaviour

On Home entry:

1. Cached headlines render immediately.
2. A refresh begins when the cache is stale for the current app session.
3. The user can always request pull-to-refresh.
4. Each source refresh succeeds or fails independently.
5. Successful source results replace only that source's cached subset.
6. If all sources fail, cached data remains visible with a non-blocking error and Retry action.

Refreshing headlines never downloads full article bodies. There is no periodic worker or background scheduler in this scope.

## Localization and Layout Direction

All user-interface strings currently embedded in Compose code are moved to Android string resources. Resource sets are supplied for:

- English as the fallback/default resource language.
- Persian.
- French.

Settings offers:

- System language.
- فارسی.
- Français.
- English.

The selection is persisted and applied through Android's application-locale mechanism so UI updates do not require a manual app restart. System mode follows a supported device language and falls back to English for unsupported languages.

Persian UI uses right-to-left layout and a bundled Vazirmatn family for menus, buttons, labels, and messages. French and English UI use the platform's standard sans-serif typography. French article bodies, headlines, and other source content are explicitly left-to-right even while the surrounding interface is Persian. The Reading screen retains its existing content font and font-size controls.

## Error Handling

- Failure of one news source does not suppress results from the other source.
- Total refresh failure leaves cached headlines visible and offers Retry.
- Empty first-run cache plus total failure shows a dedicated offline/error state rather than an empty Home.
- Missing image data uses a source-specific placeholder.
- Article download shows progress on the selected preview action and remains retryable after failure.
- Extraction returning no usable article body is reported as an import error and creates no database row.
- Database insertion and local image persistence are coordinated so failed imports do not leave a visible partial document or orphaned image.
- Duplicate download detection changes the action to open the existing document.

## Testing Strategy

Unit tests cover:

- Parsing headline image metadata.
- Combining two source result sets and ordering by date.
- Preserving one source when the other fails.
- Per-source cache replacement behaviour.
- Source URL normalization and duplicate detection.
- Home state composition for cached, refreshing, partial-error, and empty-error states.
- Library filtering and each sort order.
- Locale preference resolution, including unsupported system-language fallback.

Database tests cover the schema migration, existing-row defaults, headline cache operations, and duplicate lookup. UI tests cover Home section presence, preview-to-import behaviour, Library navigation, language switching, and RTL/LTR direction at key boundaries.

Manual device checks cover actual RFI/France Info feeds, image loading, full-text extraction, offline relaunch, Persian Vazirmatn rendering, and all three explicit interface languages.

## Acceptance Criteria

- Home shows the approved hierarchy: Search, Today's News, Continue Reading, and five recent texts.
- Recent RFI and France Info headlines are combined and sorted newest-first.
- Home can render usable cached headlines before a refresh completes.
- Opening a headline does not download the article body.
- Download and Read produces one saved document, persists attribution and any available image, and opens Reading.
- A previously downloaded headline opens its existing library item instead of creating a duplicate.
- See All opens a dedicated searchable and sortable Library containing every saved text.
- Existing add/paste, file import, topic search, vocabulary, reading, and settings flows remain reachable.
- Interface language can be System, Persian, French, or English and persists across launches.
- Unsupported System language falls back to English.
- Persian interface text uses bundled Vazirmatn and RTL layout.
- French content remains LTR, and article typography is unchanged.
- Failure of either news source alone still allows the other source and cached results to display.

## Delivery Boundaries

This design should be implemented as one coordinated feature, but the implementation plan should divide it into independently verifiable increments: localization foundation, data/schema changes, news cache/repository, Home UI, preview/import with images, Library, and end-to-end polish. Schema and localization foundations must land before screens that depend on them.
