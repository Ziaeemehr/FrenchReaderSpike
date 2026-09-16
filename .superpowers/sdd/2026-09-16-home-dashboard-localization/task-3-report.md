# Task 3 report

Status: implemented.

Changes:

- Added safe RSS image candidate parsing to `NewsItem` (`media:content`, `media:thumbnail`, image enclosures, and description images), with HTTP upgrade and HTTPS validation.
- Added deterministic `mergeHeadlines` URL normalization, image-aware deduplication, date ordering, and limiting.
- Added parser and merger coverage for image sources, missing images, URL deduplication, null dates, and limits.

Tests: `./gradlew testDebugUnitTest --tests '*NewsFetcherTest' --tests '*HeadlineMergerTest'` — **BUILD SUCCESSFUL** (11 tests). `./gradlew testDebugUnitTest assembleDebug` — **BUILD SUCCESSFUL** (55 actionable tasks).

Concerns: none identified after focused tests and full unit test plus debug assembly verification.

## Fix Round 1 evidence

- Validated `media:content` using its `type`, `medium`, or image extension, so audio/video candidates fall back to an image thumbnail or enclosure.
- Normalized HTTP and HTTPS schemes case insensitively before enforcing HTTPS, covering uppercase `HTTP://` image URLs.
- Added regression tests for non-image media fallback and uppercase HTTP image URLs.
- Focused `./gradlew testDebugUnitTest --tests '*NewsFetcherTest' --tests '*HeadlineMergerTest'` — **BUILD SUCCESSFUL** (14 tests).
- Full `./gradlew testDebugUnitTest assembleDebug` — **BUILD SUCCESSFUL** (55 actionable tasks).
