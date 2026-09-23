# Multilingual Learning Profiles Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan task by task. The checkboxes track progress. Resolve the product decisions before implementation.

**Goal:** On first launch, let a learner choose a language to learn and a language they know. Keep that pair fixed for the active account, and let them create and switch between accounts, such as one for French and one for Spanish.

**Architecture:** Each learning account owns an immutable `learningLanguage` and `baseLanguage`. The active account controls interface locale, translation direction, voice, dictionary, content sources, and learning data. The concrete storage design below assumes local accounts for the first release; whether accounts need login or cross-device sync remains a product decision. Switching stops account-owned work and resets navigation after applying the new base locale.

**Tech stack:** Kotlin, Jetpack Compose, Room, AndroidX AppCompat locales, Edge TTS through Chaquopy, optional XTTS/Vosk, JUnit4 and Android instrumented tests.

**Spec:** The Product Contract and Acceptance Criteria below are the implementation target. This plan replaces the previous per-text language design.

## Product Contract

- First launch asks for a **learning language** and a **base language**. The base language drives the interface, translation target, and word meanings. The learning language drives imported texts, speech, dictionary source, discovery, and exercises. For example, a French/Persian account shows Persian UI and French→Persian translations; a Spanish/English account shows English UI and Spanish→English translations.
- The language pair stays fixed while using the account. There is no language picker during import or reading and no per-text language switch. A different learning language requires creating or switching to another account.
- Multiple accounts can exist. Each has a separate library, vocabulary, review progress, highlights, statistics, relevant preferences, and downloaded learning content. Account switching must change both languages together.
- Existing installs automatically get one French profile. Preserve all existing rows, text bodies, images, positions, highlights, vocabulary, review history, and backup compatibility.
- For legacy users, migrate the learning language as `fr`. Preselect a base language from the current meaning-language preference (`fa` or `en`) if present, otherwise the current supported UI locale. If old UI and meaning languages differ, ask the user to confirm one base language before applying the new unified behavior; do not silently change the UI or translation destination.
- A base language is selectable only when its complete UI resources exist. Today these are English, French, and Persian. Learning languages can be expanded independently after voice and dictionary checks.
- Keep `applicationId`, Kotlin package, legacy database filename, and old backup reader stable. Rename the product only in user-facing places.
- Whether an account is local only or also has login/cloud sync has not been specified. Tasks 2–9 describe the recommended local-account first release, with explicit data isolation and backup. If online accounts are required, add authentication, identity, sync/conflict resolution, and privacy design before implementation; do not claim the local plan covers them.

## Product Decisions Before Coding

1. Choose the new display name and the initial learning languages beyond French. Spanish is an example, not yet a confirmed launch language.
2. Confirm the base-language list. Recommendation: only the existing fully localized `en`, `fr`, and `fa` for the first release.
3. Confirm that two profiles may share a learning language but have different names or base languages. Recommendation: yes; profile ID is the identity.
4. Confirm that language pairs cannot be edited after profile creation in this release. Recommendation: allow rename/delete; create another profile for a different pair.
5. Confirm backup behavior. Recommendation: one archive contains **all profiles** and restore replaces the whole local installation after a clear warning; single-profile export can come later.
6. Decide whether “account” means a local switchable learning space or a login-backed, cross-device account. This plan estimates the local version; online sync needs a separate estimate and architecture.

## Global Constraints

- Follow project instructions: write implementation code via `codex exec` when practical; verify diffs and builds directly. Do not raise Android version floors without need.
- Put new UI strings in `values/strings.xml`, `values-fr/strings.xml`, and `values-fa/strings.xml`. No base language appears in onboarding without full UI localization.
- Use explicit migrations; never rely on `fallbackToDestructiveMigration()` for existing users.
- Use opaque stable profile IDs in paths and cache keys, never names or language codes. Rename must not move data.
- A feature appears for a learning language only after its provider and tests pass. Missing discovery or shadowing support must not block local reading.
- Each task ends with focused verification and a reviewable commit.

## Storage and State Design

**Global registry for local accounts:** `ProfileRegistry` stores `{id, name, learningLanguage, baseLanguage, createdAt}` and `activeProfileId`. Writes must be atomic. The active ID must always reference a real account. Generate UUIDs; account name is display only.

**Profile data:** Use one Room database per profile rather than adding `profileId` to every DAO query. The migrated first profile retains `french_reader.db`; new profiles use ID-based names. Text bodies, images, relevant preferences, translation/TTS caches, and downloaded models use profile-scoped paths. Shared binary assets are allowed only when they reveal no profile content. Pass an explicit `ProfileScope` into repositories: low-level operations must not read a mutable global active ID midway through work.

**Switch lifecycle:** Persist pending reading progress, stop playback and recording, cancel profile-owned jobs, set the active ID, apply the base-language locale, then recreate/reset navigation to the new profile's home. Late old-profile results are discarded with a profile/generation token. An incoming share is saved to the profile visible when the user confirms import.

**Backup:** New archives contain registry, all profile databases, per-profile files/preferences, and active ID. Old single-database archives restore into one French profile. Validate the complete archive in temporary storage before replacing live data; a failed restore leaves existing profiles intact.

## Review Focus

1. Switching during TTS, recording, translation, article fetch, or import cannot write an old-profile result into the new profile.
2. Identical words, folders, URLs, and headlines in two profiles remain independent.
3. Upgrade and old-backup restore retain text bodies, images, highlights, positions, words, and review history.
4. The new base locale is applied before meaningful UI renders after startup or switch.
5. Deleting a profile cannot delete another profile's data or leave an active ID, reminder, cache item, or job pointing to the deleted profile.

---

### Task 1: Inventory State and Define Language Capabilities

**Files:** Create `profiles/ProfileLanguageCatalog.kt` and tests. Inspect `data/*Prefs.kt`, `backup/LocalBackup.kt`, `AppDatabase.get(...)` callers, file stores, caches, reminders, and model downloads. Record ownership in a companion design note or this plan before Task 2.

**Interfaces:** `ProfileLanguages(learningLanguage, baseLanguage)` validates pairs; the catalog maps base language to `AppLanguage` and learning language to verified voice, dictionary, discovery, and shadowing capabilities.

- [ ] Test valid/invalid pairs, UI-locale mapping, unknown codes, and capability flags; confirm tests fail first.
- [ ] Classify every database, preference file, directory, cache, job, and notification as global, profile-owned, or safely shared.
- [ ] Verify at least one Edge voice and dictionary route for each launch learning language.
- [ ] Implement the catalog, run focused tests, and commit the catalog plus ownership map.

**Done when:** supported pairs and persistent-state ownership have one documented source of truth.

### Task 2: Registry and First-Run Onboarding

**Files:** Create `profiles/ProfileRegistry.kt`, `profiles/ProfileManager.kt`, onboarding UI and tests; modify `MainActivity.kt` and navigation.

**Interfaces:** `createProfile(name, learningLanguage, baseLanguage): ProfileId`, `activeProfile(): LearningProfile?`, `setActiveProfile(id)`, `renameProfile(id, name)`.

- [ ] Test empty registry, creation, duplicate display names, invalid pairs, missing active-ID recovery, and atomic writes.
- [ ] Add onboarding for learning language, base language, and optional name; persist the profile before entering the main app.
- [ ] Apply the profile base locale before home renders. Remove the independent in-profile UI-language picker as a competing setting.
- [ ] Run tests and a fresh-install device walkthrough; commit.

**Done when:** no learning screen opens without a valid active profile.

### Task 3: Migrate Existing Installs to One French Profile

**Files:** Modify startup/profile initialization and add instrumented migration tests under `app/src/androidTest/java/com/ziaee/frenchreader/profiles/`.

- [ ] Build a fixture with the current database, text body/image files, preferences, progress, highlights, and review data.
- [ ] Create one registry entry pointing to legacy `french_reader.db` and legacy file roots. Derive base language using the Product Contract precedence; do not rewrite all rows merely to tag them French.
- [ ] Test repeated startup and interrupted migration. When old UI and meaning languages conflict, show a one-time base-language choice before switching either behavior; test both possible choices.
- [ ] Upgrade an old installed build to the new build on a device/emulator; commit.

**Done when:** existing users see their complete French library in one profile.

### Task 4: Isolate Databases, Files, Preferences, and Caches

**Files:** Modify `data/AppDatabase.kt`, `data/TextBodyStore.kt`, image store, `data/*Prefs.kt`, `translate/TranslationCache.kt`, `tts/TtsCache.kt`, and repositories; add integration tests.

**Interfaces:** `ProfileScope(profileId)` supplies a stable database, directories, preference namespace, and caches. Repositories capture that scope at construction.

- [ ] Test two profiles containing identical titles, words, file names, and translation strings; assert independent rows/files.
- [ ] Convert `AppDatabase.get(context)` callers to a stable profile database. Keep the legacy first-profile DB path.
- [ ] Scope preferences classified in Task 1; copy legacy values into only the migrated French profile.
- [ ] Scope body/image/model/cache paths; test that deleting one scope leaves another intact. Commit.

**Done when:** profile B starts empty and cannot see or alter profile A's learning data.

### Task 5: Safe Switching and Profile Management

**Files:** Add profile switcher/management UI; modify `MainActivity.kt`, reading/shadowing ViewModels, incoming-share flow, reminders, and tests.

- [ ] Test switches during playback, translation, import, recording, and reminder delivery; old results must not update the new profile.
- [ ] Add create, rename, switch, and delete. Deleting the active profile requires another profile to become active first.
- [ ] On switch, persist progress, release old resources, update active ID, apply locale, and reset navigation.
- [ ] Confirm before irreversible profile deletion. Test failure recovery and profile-specific cleanup; commit.

**Done when:** switching changes the entire learning context without stale UI or writes.

### Task 6: Route the Fixed Language Pair Through Learning Features

**Files:** Modify `ui/ReadingViewModel.kt`, `ui/ReadingScreen.kt`, `translate/*`, `tts/Voices.kt`, `tts/XttsClient.kt`, `ui/DictionarySheet.kt`, `ui/VocabReviewScreen.kt`, import repositories, and tests.

**Interfaces:** translation takes explicit `(text, sourceLang, targetLang)`; dictionary routing takes `(word, learningLang, baseLang)`; voice lookup uses learning language. All calls use the captured profile pair.

- [ ] Test French→Persian and one new learning/base pair, including identical source text in separate profiles and source=target.
- [ ] Remove implicit `fr` translation defaults. Include source and target language in translation cache keys even though caches are profile-scoped.
- [ ] Filter voices by learning language; enable XTTS only for verified supported languages.
- [ ] Route dictionary, meanings, vocabulary review, and all text imports from the profile pair. Add no per-text picker. Commit.

**Done when:** the active pair consistently determines interface, translation, speech, dictionary, and review.

### Task 7: Text Processing, Content Sources, and Shadowing

**Files:** Modify `text/TextChunker.kt`, `content/VocabHighlighter.kt`, `ui/ReadingScreen.kt`, `content/ContentSource.kt`, Vikidia/Wikisource/news clients, `resources/*`, and `shadowing/*`.

- [ ] Test punctuation, accents, apostrophes, combining marks, and long text for each launch learning language. Use learning language for text direction independently from UI direction.
- [ ] Apply French elision rules only within French profiles; add other language rules only where needed.
- [ ] Make content sources declare supported languages. Search/resources follow the active profile; French news does not appear in a Spanish profile by default.
- [ ] Select speech-recognition locale/model by verified learning language. Hide or explain unavailable shadowing. Preserve French behavior.
- [ ] Run fixture tests and one device walkthrough per launch language; commit.

**Done when:** content and exercises are appropriate to the profile language.

### Task 8: Multi-Profile Backup and Legacy Restore

**Files:** Modify `backup/LocalBackup.kt`, `backup/DriveBackupClient.kt`, backup UI/preferences and tests.

- [ ] Test round-trip of two profiles with different libraries, files, preferences, and active ID; test old single-profile archive restore.
- [ ] Version the new archive and include registry, every profile DB, files, preferences, and active ID; retain old archive reader.
- [ ] Validate into temporary storage before replacement; reject invalid references, duplicate IDs, unsafe paths, and missing profile data. On failure preserve the current installation.
- [ ] Verify Google Drive backup/restore and document that restore replaces all local profiles; commit.

**Done when:** backup cannot silently merge profiles or partially replace live data.

### Task 9: Brand, Localization, and Release Verification

**Files:** Modify `app/src/main/res/values*/strings.xml`, `ui/AboutScreen.kt`, `README.md`, release copy, and focused tests. Keep `applicationId` stable.

- [ ] Apply the selected neutral product name and explain language pairs and local profiles in UI/docs.
- [ ] Translate onboarding, switch, delete, feature-availability, and backup strings to all three existing UI locales; run `LocalizationCompletenessTest`.
- [ ] Run `./gradlew :app:testDebugUnitTest`, relevant instrumented tests, and a release build. On device, verify fresh install, legacy upgrade, two profiles, switching during work, backup/restore, and deletion.
- [ ] Review every hardcoded French assumption and global storage caller found in Task 1; verify it is intentional or removed. Commit documentation.

**Done when:** the release build meets all acceptance criteria.

## Acceptance Criteria

1. On fresh install, the learner chooses learning and base languages. UI/translation use base; text/speech use learning.
2. French and another launch-language profile have separate libraries, vocabulary, progress, statistics, preferences, and reminders.
3. No language choice appears during ordinary import/reading; the pair changes only by switching profiles.
4. Switching during audio, recognition, translation, or import cannot save to the wrong profile.
5. Old installations become one French profile without data loss, and old backups remain restorable.
6. A new backup restores all profiles atomically; an invalid archive leaves the installation intact.
7. Every selectable base locale has complete UI strings; language-specific capabilities are only shown where verified.

## Estimate and Sequence

| Work | One-developer estimate |
| --- | ---: |
| Tasks 1–3: catalog, onboarding, legacy migration | 6–10 working days |
| Tasks 4–5: storage isolation and safe switching | 10–18 working days |
| Tasks 6–7: learning features and capability gates | 9–16 working days |
| Task 8: backup and legacy restore | 5–9 working days |
| Task 9: localization, regression, release | 4–7 working days |

**Total for local accounts:** about **7–12 weeks** for one developer, depending on launch languages and the state-ownership audit. Account isolation, migration, and safe switching are the main costs. Login-backed accounts and cross-device sync are outside this estimate. Each additional learning language needs voice/dictionary validation; discovery and shadowing may take additional provider/model work. A new base language requires full interface translation and layout testing.

**Delivery order:** preserve legacy French data first; then create/switch a second isolated profile; then enable the full learning flow for selected language pairs; finally release multi-profile backup and the new brand. Each stage should have an independently testable build.
