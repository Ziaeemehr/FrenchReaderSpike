# Task 3 report

Status: implemented.

Changes:

- Added safe RSS image candidate parsing to `NewsItem` (`media:content`, `media:thumbnail`, image enclosures, and description images), with HTTP upgrade and HTTPS validation.
- Added deterministic `mergeHeadlines` URL normalization, image-aware deduplication, date ordering, and limiting.
- Added parser and merger coverage for image sources, missing images, URL deduplication, null dates, and limits.

Tests: `./gradlew testDebugUnitTest --tests '*NewsFetcherTest' --tests '*HeadlineMergerTest'` — **BUILD SUCCESSFUL** (11 tests). `./gradlew testDebugUnitTest assembleDebug` — **BUILD SUCCESSFUL** (55 actionable tasks).

Concerns: none identified after focused tests and full unit test plus debug assembly verification.
