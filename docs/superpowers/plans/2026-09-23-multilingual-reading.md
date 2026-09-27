# German + Target-Language Switching — Revised Plan

## Context
The app is French-only today. The user wants to add German, and wants the learner to choose, at first launch and later from Settings, **which language they are learning** (target) and **which language they know** (for translations and meanings). The menu language (`LocalePrefs`) stays a separate setting.

The existing plan (`docs/superpowers/plans/2026-09-23-multilingual-reading.md`) had two ideas we are dropping:
- Separate profiles, each with its own database. We will use one database where each row carries a `language` tag and the app filters by it. That cuts the estimate from about 7–12 weeks to about 2–4.
- Letting the known language drive the menu language. The two will stay independent.

Work happens on a new branch, `feat/multilingual-german`. Before branching, commit or stash the uncommitted TextEditorTools work on `main`.

## Extensibility (more languages later, one at a time)
German is the first new language, but more will follow. Nothing may assume just `fr`/`de`:
- The `language` column is a free BCP-47 code (no enum in the DB, so no migration per language).
- Everything language-specific lives in `LanguageCatalog` entries. A `LanguageSupport` object holds the voices, STT locale, XTTS flag, sources, text rules and the dictionary route.
- Adding language N means adding a catalog entry, running a checklist (voice works, dictionary route, text-rule tests, sources), and running one device test. No schema change and no edits elsewhere in the app.
- Optional features (news, shadowing, XTTS) are capability flags. A missing one hides the feature instead of blocking the language.
- Put this "add a language" checklist in `docs/` as part of Task 1.

## Model
- `LanguagePrefs` (new, in `data/`) stores `targetLanguage` (`fr` | `de`) and `knownLanguage` (`fa` | `en` | `fr` | …). `knownLanguage` takes over the current meaning-language preference in `data/VocabPrefs.kt`: migrate that value into it.
- `LanguageCatalog` (new) maps each target language to its Edge voices, STT locale, XTTS support, content sources, and text rules (for example, French elision).
- Switching the target language is a single pref write. The whole app watches it through a Flow, stops TTS and recording first, then re-queries.

## Tasks
1. **Catalog + prefs** (`data/LanguagePrefs.kt`, `profiles/LanguageCatalog.kt`, with unit tests). Verify that the German Edge voices actually work in edge-tts (for example `de-DE-KatjaNeural` and `de-DE-ConradNeural`), because `tts/Voices.kt` notes that some voice IDs which look valid are not.
2. **Room migration 17→18** (`data/AppDatabase.kt`, `data/Entities.kt`). Add `language TEXT NOT NULL DEFAULT 'fr'` to `texts`, `library_folders`, `headlines`, `vocab`, `vocab_lists`, `review_log`, `activity_log`, `resources`, and `shadow_attempts`, plus an index on `language`. Filter the DAO queries, FTS search and statistics by the active language. Write an explicit migration test; do not use destructive fallback.
3. **Onboarding + switcher.** On first launch (no `LanguagePrefs` set), show a screen to pick the target and known languages. Existing installs are silently set to `fr` plus the current meaning language. Add a target-language switcher in Settings, and a small chip on Home that shows the active language. Add strings to `values`, `values-fr`, and `values-fa`, and keep `LocalizationCompletenessTest` passing.
4. **Remove hardcoded `fr`:**
   - `translate/TranslationService.kt` (default `sourceLang="fr"`, and the hardcoded MyMemory call). Add the source and target languages to the translation cache key.
   - `tts/Voices.kt`, `Entities.kt:27`, and `VocabPrefs.DEFAULT_CARD_VOICE`: choose the voice list and default voice by target language.
   - `tts/XttsClient.kt:36` (the language comes from the catalog), `shadowing/SpeechEngine.kt:114` (STT locale), and `comprehension/ComprehensionAnalyzer.kt:34` (lowercase with the target locale).
   - Dictionary routing in `ui/DictionarySheet.kt` and `ui/VocabReviewScreen.kt`: use `(target, known)`.
5. **Text processing.** In `text/TextChunker.kt` and `content/VocabHighlighter.kt`, apply French elision only when the target is `fr`. For German: handle ß and umlauts, keep capitalised nouns (do not lowercase them before lookup, or keep a case-insensitive fallback), and let compound words fall back to a lookup without splitting them.
6. **Content sources** (`content/ContentSource.kt` plus the Vikidia, Wikisource and News sources). Each source declares the languages it supports. Vikidia has a German edition (`de.vikidia.org`), and Wikisource has `de.wikisource.org`. The French news feeds need a German list (for example Tagesschau and Deutsche Welle RSS); hide news if there isn't one. Built-in resources in `resources/ResourceCategory.kt` are tagged by language.
7. **Backup.** Most of this comes for free because it is the same database. Bump the backup version and check that old archives restore as `fr`.
8. **Verification + release.** Run `./gradlew :app:testDebugUnitTest`, then `assembleRelease` and `adb install -r` over the existing build. On the device, check that the French data is all still there after the upgrade, create German, import a German text, and test TTS, translation and the dictionary. Then switch back to French and confirm the libraries are separate.

## Verification
- Unit tests: catalog, prefs migration, and the language filter on the DAOs (the same word in fr and de stays two separate rows).
- Instrumented Room migration test 17→18 using a real v17 fixture.
- Device walkthrough as in step 8. Per the release workflow, test only the release build, never debug.

## Implementation notes
- Code is written via `codex exec`, subject to the 90% quota reserve. I review the diffs and run the builds.
- One commit per task on `feat/multilingual-german`. Once this revised plan is approved, it replaces the old plan file in `docs/superpowers/plans/`.
