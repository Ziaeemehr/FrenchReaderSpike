# Google sign-in + Drive backup/restore — design spec

Status: approved for implementation (scope and technical approach confirmed by user
on 2026-09-18).

## 1. Problem

The app has no account system and no way to move data (saved texts, vocabulary,
Leitner progress, review/activity history) off the device. Reinstalling the app,
switching phones, or losing the device loses everything. The user wants to sign in
with their Google account and back up/restore the app's data via Google Drive.

This is a deliberate, explicit exception to the app's established "fully local,
nothing leaves the device, no accounts" philosophy (see ROADMAP.md §9). It's scoped
narrowly on purpose: **backup/restore only**, not multi-device sync, not an account
system for any other feature.

## 2. Scope

**In scope:**
- Sign in / sign out with a Google account.
- Manual "Back up now" — uploads the local Room database to a hidden per-app folder
  in the user's Google Drive.
- Manual "Restore" — downloads that backup and replaces the local database,
  overwriting everything currently on the device, with an explicit warning.
- A small "Cloud Backup" section in `SettingsScreen.kt` showing sign-in state,
  account email, last-backup timestamp, and the three actions above.

**Explicitly out of scope (not built now, can be separate future work):**
- Real-time or automatic multi-device sync.
- Automatic/scheduled backups (no WorkManager, no background jobs — matches the
  rest of the app's "nothing happens without the user tapping a button" pattern).
- Backing up article images (`filesDir/text_images/`) or any cached audio.
- Multiple/versioned backups — there is exactly one backup file, always overwritten.
- Restoring from a different Google account than the one that made the backup
  (works mechanically the same, but isn't a designed/tested scenario).
- Revoking Drive access remotely, or any account-management UI beyond sign
  in/out.

## 3. Prerequisite: Google Cloud Console setup (done by the user, not in code)

Already completed as of this spec:
- Project: reusing the existing "Gemini API" project (ID `gen-lang-client-0521224903`).
- Google Drive API: enabled on that project.
- OAuth consent screen: External, Testing mode, `drive.appdata` scope added, user's
  own email added as a test user.
- OAuth client #1 (Android type): package `com.ziaee.frenchreader`, debug SHA-1
  `4B:2F:D4:8A:1D:CA:F8:48:04:E0:02:79:52:3E:BD:12:6D:BE:1F:30`. (A release-build
  SHA-1 will need to be added the same way if/when this ships as a signed release —
  not needed for now since only the debug build is being tested.)
- OAuth client #2 (Web application type): Client ID
  `623452723151-dn9i6noo7c40m72nf7fbsspa8uispjd8.apps.googleusercontent.com`. This is
  the one the app's code actually references (as `serverClientId` for Credential
  Manager). Its client secret is **not used anywhere in the app** — secrets are for
  server-side flows and would be exposed if embedded in an APK.

## 4. Architecture

New package `com.ziaee.frenchreader.backup`:

- **`GoogleAuthManager.kt`** — wraps two separate Google APIs that are easy to
  conflate:
  1. **Credential Manager** (`androidx.credentials`) — the modern "Sign in with
     Google" flow. Produces identity only: a `GoogleIdTokenCredential` with the
     user's email/display name. This is *authentication*, not Drive access.
  2. **Authorization API** (`com.google.android.gms.auth.api.identity.Identity
     .getAuthorizationClient`, from `play-services-auth`) — requests an OAuth
     access token scoped to `https://www.googleapis.com/auth/drive.appdata`. This
     is *authorization* for Drive specifically, and is what `DriveBackupClient`
     actually uses. It can silently re-authorize (refresh the token) on later calls
     once the user has granted it once, without prompting again.
- **`DriveBackupClient.kt`** — talks to the Drive REST API v3 directly over
  `HttpURLConnection`, the same pattern already used by `NewsFetcher.kt` and
  `VikidiaClient.kt` (no `google-api-services-drive` dependency — that library is
  heavy and designed for server/Java use, not idiomatic on Android). Three calls:
  - `GET https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,modifiedTime)`
    — find the existing backup file, if any.
  - `POST/PATCH https://www.googleapis.com/upload/drive/v3/files...` (multipart,
    `uploadType=multipart`) — create the file (first backup) or update it in place
    (every backup after that) with `parents=["appDataFolder"]` on create.
  - `GET https://www.googleapis.com/drive/v3/files/{id}?alt=media` — download the
    backup's bytes for restore.
  All three send `Authorization: Bearer <token>`.
- **`BackupPrefs.kt`** — `SharedPreferences`-backed, same pattern as
  `AppearancePrefs.kt`/`VocabPrefs.kt`: stores the signed-in account email and the
  last-successful-backup timestamp (epoch ms) for display in Settings.

## 5. Backup data format

Just the raw Room SQLite database file — no images, no audio (per decision above).
Before upload, run `PRAGMA wal_checkpoint(FULL)` on the open database so the WAL
file is merged into the main `.db` file; this makes a plain copy of that one file a
complete, consistent snapshot (no need to also ship `-wal`/`-shm` sidecar files).

There is always exactly **one** file in the app's `appDataFolder` — checked for by
name (`frenchreader_backup.db`) via the `files.list` call above, then either updated
in place or created if it doesn't exist yet. No versioning, no history.

## 6. Backup flow

1. User taps "Back up now" in Settings.
2. If not signed in, trigger sign-in first (Credential Manager), then request Drive
   authorization (Authorization API) if not already granted.
3. Run the WAL checkpoint on the live `AppDatabase` instance.
4. Copy `context.getDatabasePath("french_reader.db")` to a temp file (so upload reads a
   stable snapshot even if writes happen concurrently — unlikely mid-tap, but cheap
   to do right).
5. `DriveBackupClient` looks up the existing backup file id; upload (create or
   update) the temp file's bytes.
6. On success: `BackupPrefs` records `lastBackupAtMs = now`; Settings UI updates;
   Snackbar confirms.
7. On failure (network, auth, Drive API error): Snackbar with a retry-style message,
   matching the existing pattern used for `synthesizeChunk`/news-fetch failures. No
   partial/corrupt state possible since the real DB file was never touched.

## 7. Restore flow

1. User taps "Restore".
2. Warning dialog: *"This will replace all data currently on this device with the
   backup from Google Drive. This can't be undone. Continue?"* — explicit
   confirm/cancel, matching the existing delete-confirmation pattern in
   `LibraryScreen.kt`.
3. On confirm: download the backup file's bytes to a temp file in cache dir. If no
   backup file exists yet (first-time restore attempt), show "No backup found for
   this account" instead of proceeding.
4. Close the current `AppDatabase` instance (`.close()`), delete any `-wal`/`-shm`
   sidecar files next to the real DB path (avoids stale WAL data conflicting with
   the restored file), then copy the downloaded temp file over the real DB path.
5. Immediately restart the app process: relaunch `MainActivity` via
   `packageManager.getLaunchIntentForPackage(...)` with
   `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`, then
   `Runtime.getRuntime().exit(0)`. This is the safe way to swap the SQLite file out
   from under a live Room instance — trying to keep the process alive and just
   reopening Room in place is fragile and not worth the complexity for a manual,
   infrequent action.
6. On any failure before step 4 (download/network/auth): Snackbar error, nothing on
   disk is touched, app keeps running normally.

## 8. Settings UI (`SettingsScreen.kt`)

New "Cloud Backup" section, added the same way the existing appearance
radio-groups are laid out (Scaffold/Column, no new screen/route needed):

- **Signed out:** one button, "Sign in with Google".
- **Signed in:** account email, "Last backup: <date>" (or "No backup yet"), then
  three actions: "Back up now", "Restore" (opens the warning dialog from §7),
  "Sign out".
- Sign out clears the Credential Manager credential state and clears
  `BackupPrefs`'s stored email; it does not revoke the Drive authorization grant
  itself (the user can revoke that from their Google Account settings if they ever
  want to fully disconnect — out of scope to build an in-app "revoke" action for
  this initial version).

## 9. New dependencies

```
implementation "androidx.credentials:credentials:1.3.0"
implementation "androidx.credentials:credentials-play-services-auth:1.3.0"
implementation "com.google.android.libraries.identity.googleid:googleid:1.1.1"
implementation "com.google.android.gms:play-services-auth:21.2.0"
```
(Exact versions to be confirmed against latest stable at implementation time.)

## 10. Error handling summary

| Failure | Behavior |
|---|---|
| No network during backup/restore | Snackbar, no state change |
| User cancels Google sign-in | No-op, returns to Settings unchanged |
| Drive authorization denied | Snackbar explaining Drive access is required for backup |
| Access token expired mid-call | `GoogleAuthManager` re-authorizes silently once, retries the one call; if that also fails, treat as a normal failure (Snackbar) |
| No backup file exists yet (restore) | "No backup found for this account" message, dialog stays open-equivalent (user can cancel) |
| Drive API quota/error response | Snackbar with the generic failure message (quota exhaustion is not realistically reachable at this app's scale) |

## 11. Testing approach

Consistent with how the rest of the app's network-dependent code is tested (see
`NewsFetcherTest.kt`, `VikidiaClientTest.kt` — pure parsing logic gets JVM tests,
actual network/framework calls are verified on a real device):

- **JVM-testable (pure logic):** the Drive `files.list` JSON response parser (find
  existing file id), the multipart request body builder, and the "does a backup
  file already exist" decision logic.
- **Real-device-only (no automated test, per project convention for this class of
  integration):** the actual Credential Manager sign-in UI, the Authorization API
  consent prompt, and live Drive API calls — these require real Google
  infrastructure and an interactive account picker that can't be driven from an
  instrumented test. Verified manually: sign in, back up, verify the file appears
  in Drive (`drive.google.com` won't show it since it's in `appDataFolder` — verified
  instead via the app's own "Last backup" timestamp updating, and by clearing app
  data / reinstalling and confirming Restore brings the data back), sign out, sign
  back in, restore.

## 12. Files touched/created

- Create: `backup/GoogleAuthManager.kt`, `backup/DriveBackupClient.kt`,
  `backup/BackupPrefs.kt`
- Create: `data/AppDatabase.kt` gets one small addition — a way to run the WAL
  checkpoint and to close/reopen the singleton instance (needed for restore).
- Change: `ui/SettingsScreen.kt` (new section), `app/build.gradle` (new
  dependencies), `strings.xml` × 3 (new UI strings).
