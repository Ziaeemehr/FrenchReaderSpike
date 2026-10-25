# Prepare translations implementation plan

Goal: implement Phase 3 of `.omc/plans/llm-translation.md` and the supplied requirements.
Architecture: snapshot selected source items to an atomic private file, pin engine cache identity and target languages, run bounded WorkManager slices with network constraints, and use existing LlmTranslator/LlmCache exclusively. Cached entries are the resume checkpoint. No Room changes.

- [x] Add pure item projection, identity/deduplication, estimates, batching and split validation tests first.
- [x] Build projection from TextChunker/MarkdownParser, merged stored captions, vocabulary meanings/examples using live languages/context.
- [x] Add atomic selection/job persistence and unique CoroutineWorker; cache-only skips, availability checks, smaller retries for malformed batches, pause/resume and bounded continuation.
- [x] Add Settings entry and vertical Compose screen, counts/estimates/progress/cache deletion confirmation, localized English/French/Persian strings.
- [x] Verify cache precedence and live flashcard example cache hits; review lifecycle, cancellation, engine changes and secret handling.
- [x] Run :app:testDebugUnitTest :app:assembleDebug with Java 17 (passed on the Mac 2026-10-25).

Validation: changed Kotlin/Compose files compiled with the cached Kotlin 1.9.24 compiler and Compose plugin against existing app classes/dependencies (temporary resource ID stand-in). 83 translation unit tests passed directly through JUnit. AAPT2 compiled real resources; localization placeholders and git diff whitespace checked. Read-only reviewer found no actionable issues. Android runtime/process-death testing remains unverified.
