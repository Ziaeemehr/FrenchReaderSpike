# Google Sign-In + Drive Backup/Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user sign in with Google and manually back up/restore the app's Room database to/from a hidden per-app folder in their Google Drive.

**Architecture:** `GoogleAuthManager` wraps two separate Google APIs (Credential Manager for identity, the Identity Authorization API for Drive-scoped access tokens). `DriveBackupClient` calls the Drive REST API v3 directly over `HttpURLConnection` — no heavy Drive client library, matching how `NewsFetcher`/`VikidiaClient` already talk to the network in this codebase. `AppDatabase` gains a WAL-checkpoint helper and a way to close/reset its singleton so restore can safely swap the underlying SQLite file. Everything is wired into a new "Cloud Backup" section in `SettingsScreen.kt`; nothing runs automatically or in the background.

**Tech Stack:** Kotlin, Jetpack Compose, Room, `androidx.credentials` (Credential Manager), `com.google.android.gms:play-services-auth` (Identity Authorization API), `org.json` (already used elsewhere in this codebase), plain `HttpURLConnection`.

**Spec:** `docs/superpowers/specs/2026-09-18-google-drive-backup-design.md`

## Global Constraints

- No `google-api-services-drive` / `google-api-client` dependency — Drive REST calls go through plain `HttpURLConnection`, same pattern as `NewsFetcher.kt`/`VikidiaClient.kt`.
- Backup contains only the Room database file — no article images, no audio.
- Exactly one backup file, name `frenchreader_backup.db`, always created-or-updated in place (no versioning/history).
- No automatic/scheduled backups — every backup/restore is triggered by an explicit user tap, no WorkManager.
- Web OAuth Client ID (for Credential Manager's `serverClientId`, and *only* this — never the client secret, which is never used in app code): `623452723151-dn9i6noo7c40m72nf7fbsspa8uispjd8.apps.googleusercontent.com`
- Android package name registered with Google: `com.ziaee.frenchreader`; debug SHA-1 already registered: `4B:2F:D4:8A:1D:CA:F8:48:04:E0:02:79:52:3E:BD:12:6D:BE:1F:30`.
- Drive scope requested: `https://www.googleapis.com/auth/drive.appdata` only.
- No hardcoded Persian/French/English UI string literals in Kotlin source — every user-facing string goes through `stringResource(R.string....)`, and `values/`, `values-fr/`, `values-fa/strings.xml` must all define the exact same set of keys (`LocalizationCompletenessTest.kt` enforces this and will fail the build otherwise).
- Follow this codebase's existing terse comment style (no comment unless it explains a non-obvious *why*).

---

## Task 1: `BackupPrefs.kt`

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/backup/BackupPrefs.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/backup/BackupPrefsTest.kt`

**Interfaces:**
- Produces: `internal fun formatBackupTimestamp(atMs: Long, locale: Locale = Locale.getDefault()): String`, `formatLastBackupLabel(context: Context, lastBackupAtMs: Long?): String`, `BackupPrefs.getSignedInEmail(context): String?`, `BackupPrefs.setSignedInEmail(context, email: String?)`, `BackupPrefs.getLastBackupAtMs(context): Long?`, `BackupPrefs.setLastBackupAtMs(context, atMs: Long)`

`formatLastBackupLabel` needs a real `Context` (for the "Never backed up" string
resource) that isn't available in a JVM test — matching how `LocalePrefsTest.kt` tests
`parseAppLanguage` directly rather than the `Context`-dependent `LocalePrefs.get`/`.set`.
So the part that's actually worth unit-testing is split out into its own internal pure
function, `formatBackupTimestamp`, which the test calls directly — not a copy-pasted
duplicate of the formatting logic (that would let this test pass even if
`BackupPrefs.kt` never existed, which defeats the point of writing it first).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziaee.frenchreader.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class BackupPrefsTest {
    @Test
    fun `formatBackupTimestamp formats a timestamp using the given locale`() {
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2026, 8, 18, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals("Sep 18, 2026, 14:30", formatBackupTimestamp(calendar.timeInMillis, Locale.US))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.backup.BackupPrefsTest"`
Expected: FAIL — compile error, `formatBackupTimestamp` (and the whole
`com.ziaee.frenchreader.backup` package) doesn't exist yet.

- [ ] **Step 3: Write `BackupPrefs.kt`**

```kotlin
package com.ziaee.frenchreader.backup

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ziaee.frenchreader.R

private const val PREFS_NAME = "backup_prefs"
private const val KEY_SIGNED_IN_EMAIL = "signed_in_email"
private const val KEY_LAST_BACKUP_AT_MS = "last_backup_at_ms"

object BackupPrefs {
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSignedInEmail(context: Context): String? =
        prefs(context).getString(KEY_SIGNED_IN_EMAIL, null)

    fun setSignedInEmail(context: Context, email: String?) {
        prefs(context).edit().putString(KEY_SIGNED_IN_EMAIL, email).apply()
    }

    fun getLastBackupAtMs(context: Context): Long? {
        val value = prefs(context).getLong(KEY_LAST_BACKUP_AT_MS, -1L)
        return if (value == -1L) null else value
    }

    fun setLastBackupAtMs(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_BACKUP_AT_MS, atMs).apply()
    }
}

internal fun formatBackupTimestamp(atMs: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("MMM d, yyyy, HH:mm", locale).format(Date(atMs))

fun formatLastBackupLabel(context: Context, lastBackupAtMs: Long?): String =
    if (lastBackupAtMs == null) context.getString(R.string.backup_never_backed_up)
    else formatBackupTimestamp(lastBackupAtMs)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.backup.BackupPrefsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/backup/BackupPrefs.kt app/src/test/java/com/ziaee/frenchreader/backup/BackupPrefsTest.kt
git commit -m "Add BackupPrefs for storing Drive backup sign-in state"
```

---

## Task 2: `DriveBackupClient` — parse the file-list response

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/backup/DriveBackupClient.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/backup/DriveBackupClientTest.kt`

**Interfaces:**
- Produces: `internal fun parseBackupFileId(responseJson: String, fileName: String): String?`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziaee.frenchreader.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveBackupClientTest {
    @Test
    fun `parseBackupFileId finds the matching file by name`() {
        val json = """
            {"files":[
                {"id":"abc123","name":"frenchreader_backup.db","modifiedTime":"2026-09-18T10:00:00.000Z"}
            ]}
        """.trimIndent()

        assertEquals("abc123", parseBackupFileId(json, "frenchreader_backup.db"))
    }

    @Test
    fun `parseBackupFileId returns null when no file matches`() {
        val json = """{"files":[{"id":"xyz","name":"something_else.db"}]}"""

        assertNull(parseBackupFileId(json, "frenchreader_backup.db"))
    }

    @Test
    fun `parseBackupFileId returns null when files list is empty`() {
        assertNull(parseBackupFileId("""{"files":[]}""", "frenchreader_backup.db"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.backup.DriveBackupClientTest"`
Expected: FAIL — `parseBackupFileId` is unresolved.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
package com.ziaee.frenchreader.backup

import org.json.JSONObject

internal fun parseBackupFileId(responseJson: String, fileName: String): String? {
    val files = JSONObject(responseJson).optJSONArray("files") ?: return null
    for (i in 0 until files.length()) {
        val file = files.getJSONObject(i)
        if (file.optString("name") == fileName) return file.optString("id")
    }
    return null
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.backup.DriveBackupClientTest"`
Expected: PASS, 3 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/backup/DriveBackupClient.kt app/src/test/java/com/ziaee/frenchreader/backup/DriveBackupClientTest.kt
git commit -m "Add Drive files.list response parser"
```

---

## Task 3: `DriveBackupClient` — build the multipart upload body

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/backup/DriveBackupClient.kt`
- Modify: `app/src/test/java/com/ziaee/frenchreader/backup/DriveBackupClientTest.kt`

**Interfaces:**
- Consumes: nothing from Task 2's function directly, but lives in the same file.
- Produces: `internal fun buildMultipartUploadBody(metadataJson: String, fileBytes: ByteArray, boundary: String): ByteArray`

- [ ] **Step 1: Write the failing test**

Add to `DriveBackupClientTest.kt`:

```kotlin
    @Test
    fun `buildMultipartUploadBody produces correct multipart structure`() {
        val body = buildMultipartUploadBody(
            metadataJson = """{"name":"frenchreader_backup.db"}""",
            fileBytes = "FAKE_DB_BYTES".toByteArray(Charsets.UTF_8),
            boundary = "test-boundary"
        )
        val text = String(body, Charsets.UTF_8)

        assertEquals(true, text.startsWith("--test-boundary\r\n"))
        assertEquals(
            true,
            text.contains("Content-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"frenchreader_backup.db\"}\r\n")
        )
        assertEquals(true, text.contains("Content-Type: application/octet-stream\r\n\r\nFAKE_DB_BYTES"))
        assertEquals(true, text.endsWith("\r\n--test-boundary--\r\n"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.backup.DriveBackupClientTest"`
Expected: FAIL — `buildMultipartUploadBody` is unresolved.

- [ ] **Step 3: Write the minimal implementation**

Add to `DriveBackupClient.kt`:

```kotlin
internal fun buildMultipartUploadBody(metadataJson: String, fileBytes: ByteArray, boundary: String): ByteArray {
    val prefix = buildString {
        append("--").append(boundary).append("\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(metadataJson).append("\r\n")
        append("--").append(boundary).append("\r\n")
        append("Content-Type: application/octet-stream\r\n\r\n")
    }.toByteArray(Charsets.UTF_8)
    val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
    return prefix + fileBytes + suffix
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.backup.DriveBackupClientTest"`
Expected: PASS, 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/backup/DriveBackupClient.kt app/src/test/java/com/ziaee/frenchreader/backup/DriveBackupClientTest.kt
git commit -m "Add Drive multipart upload body builder"
```

---

## Task 4: `DriveBackupClient` — real network calls

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/backup/DriveBackupClient.kt`

**Interfaces:**
- Consumes: `parseBackupFileId` (Task 2), `buildMultipartUploadBody` (Task 3)
- Produces: `DriveBackupClient.findBackupFileId(accessToken: String): String?`, `DriveBackupClient.uploadBackup(accessToken: String, existingFileId: String?, file: File)`, `DriveBackupClient.downloadBackup(accessToken: String, fileId: String): ByteArray`

This task has no automated test — it's a thin wrapper over `HttpURLConnection` calling real Google servers, same as `NewsFetcher`'s `httpGet` (also untested directly). It's exercised for real in Task 7's manual verification.

- [ ] **Step 1: Add the network object to `DriveBackupClient.kt`**

```kotlin
package com.ziaee.frenchreader.backup

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val BACKUP_FILE_NAME = "frenchreader_backup.db"
private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"

object DriveBackupClient {
    fun findBackupFileId(accessToken: String): String? {
        val url = "$FILES_ENDPOINT?spaces=appDataFolder&fields=files(id,name)"
        val json = httpGet(url, accessToken)
        return parseBackupFileId(json, BACKUP_FILE_NAME)
    }

    fun uploadBackup(accessToken: String, existingFileId: String?, file: File) {
        val boundary = "frenchreader-backup-${System.currentTimeMillis()}"
        val metadataJson = if (existingFileId == null) {
            """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}"""
        } else {
            """{"name":"$BACKUP_FILE_NAME"}"""
        }
        val body = buildMultipartUploadBody(metadataJson, file.readBytes(), boundary)
        val url = if (existingFileId == null) "$UPLOAD_ENDPOINT?uploadType=multipart"
            else "$UPLOAD_ENDPOINT/$existingFileId?uploadType=multipart"
        val method = if (existingFileId == null) "POST" else "PATCH"
        httpUpload(url, accessToken, method, boundary, body)
    }

    fun downloadBackup(accessToken: String, fileId: String): ByteArray {
        val url = "$FILES_ENDPOINT/$fileId?alt=media"
        return httpDownload(url, accessToken)
    }

    private fun httpGet(urlString: String, accessToken: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Accept", "application/json")
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("Drive files.list failed: HTTP $code")
        return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun httpUpload(urlString: String, accessToken: String, method: String, boundary: String, body: ByteArray) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.requestMethod = method
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.outputStream.use { it.write(body) }
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("Drive upload failed: HTTP $code")
    }

    private fun httpDownload(urlString: String, accessToken: String): ByteArray {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("Drive download failed: HTTP $code")
        return connection.inputStream.use { it.readBytes() }
    }
}
```

- [ ] **Step 2: Run the existing unit tests to confirm nothing broke**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.backup.DriveBackupClientTest"`
Expected: PASS, same 4 tests as Task 3 (this task adds no new pure-logic tests).

- [ ] **Step 3: Run a full compile to confirm the new network code builds**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/backup/DriveBackupClient.kt
git commit -m "Add Drive REST API network calls for backup upload/download"
```

---

## Task 5: `AppDatabase` — WAL checkpoint and restore-safe close

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/data/AppDatabaseBackupTest.kt`

**Interfaces:**
- Produces: `AppDatabase.checkpointWal(db: AppDatabase)`, `AppDatabase.closeForRestore()` (companion object functions)

This needs a real SQLite/Room runtime, so it's an instrumented test (same as the existing `Migration4To5Test.kt`/`Migration5To6Test.kt` in this same directory) rather than a JVM one.

**Real-device findings that shaped this task (recorded here since they overturned the
original naive approach — see the commit history for the exact debugging sequence):**
1. `PRAGMA wal_checkpoint(...)` returns a result row, so it must go through
   `query()`/`rawQuery()` — `execSQL()` throws ("Queries can be performed using
   SQLiteDatabase query or rawQuery methods only").
2. `FULL` mode checkpoints the data but doesn't guarantee the `-wal` file shrinks;
   `TRUNCATE` does.
3. Even `TRUNCATE` can come back with `busy=1` (only partially completed) while
   Room's own internal reader connection is momentarily active — verified on-device
   that silently ignoring this left committed rows missing from a plain copy of just
   the main `.db` file. This is transient and clears within milliseconds, so retry a
   few times rather than treating one busy result as final.
4. Closing the *last* connection to a WAL-mode database triggers SQLite's own
   automatic checkpoint — which is exactly why a naive test that reads the file only
   *after* `db.close()` can pass even with a completely broken `checkpointWal`. The
   test below deliberately copies the file *before* closing the connection, matching
   the real backup flow (which never closes the live db mid-backup).

- [ ] **Step 1: Write the failing instrumented test**

```kotlin
package com.ziaee.frenchreader.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile get() = context.getDatabasePath("checkpoint_test.db")
    private val snapshotFile get() = context.getDatabasePath("checkpoint_test_snapshot.db")

    @After
    fun tearDown() {
        context.deleteDatabase("checkpoint_test.db")
        snapshotFile.delete()
    }

    // What actually matters for backup: reading only the main .db file's bytes
    // (never -wal/-shm, since that's exactly what the real upload path does) right
    // after checkpointWal -- while the live connection stays open, matching the real
    // backup flow, which never closes the db mid-backup -- must be a complete, valid
    // snapshot on its own. Checked via raw SQLiteDatabase (not Room) so a Room-level
    // quirk on reopening a copied file can't hide or fake this result.
    @Test
    fun checkpointWalMakesTheMainDbFileACompleteSnapshotOnItsOwn() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "checkpoint_test.db")
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
        db.vocabListDao().insert(VocabList(name = "test-list"))

        AppDatabase.checkpointWal(db)
        dbFile.copyTo(snapshotFile, overwrite = true)
        db.close()

        val rawDb = SQLiteDatabase.openDatabase(snapshotFile.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = rawDb.rawQuery("SELECT name FROM vocab_lists WHERE name = ?", arrayOf("test-list"))
        val found = cursor.moveToFirst()
        cursor.close()
        rawDb.close()

        assertTrue("expected the inserted list to be present in a snapshot copy of just the main .db file", found)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.data.AppDatabaseBackupTest`
Expected: FAIL — `AppDatabase.checkpointWal` is unresolved.

- [ ] **Step 3: Add `checkpointWal` and `closeForRestore` to `AppDatabase.kt`**

Add inside the existing `companion object` block in `AppDatabase.kt` (alongside `get()`):

```kotlin
        fun checkpointWal(db: AppDatabase) {
            repeat(20) {
                val busy = db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
                if (busy == 0) return
                Thread.sleep(50)
            }
        }

        /** Closes and forgets the singleton so the next [get] call reopens against
         * whatever file is on disk at that point -- used right before a restore
         * overwrites the database file, since swapping the file under a live
         * Room instance is not safe. Callers must not use any existing DAO/db
         * reference obtained before this call. */
        fun closeForRestore() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.data.AppDatabaseBackupTest`
Expected: PASS. Run it 3-4 times in a row to rule out flakiness from the busy-retry timing.

- [ ] **Step 5: Run the full existing test suites to confirm no regression**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, all previously-passing tests still pass (make sure the phone screen is unlocked/awake before running — this project has seen instrumented tests fail with "No compose hierarchies found" purely because the screen was locked, unrelated to code).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt app/src/androidTest/java/com/ziaee/frenchreader/data/AppDatabaseBackupTest.kt
git commit -m "Add WAL checkpoint and restore-safe close to AppDatabase"
```

---

## Task 6: `GoogleAuthManager`

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/java/com/ziaee/frenchreader/backup/GoogleAuthManager.kt`

**Interfaces:**
- Produces: `data class GoogleAccountInfo(val email: String, val displayName: String?)`, `sealed interface AuthorizationOutcome` with `Authorized(accessToken: String)` and `NeedsResolution(pendingIntent: PendingIntent)`, `class GoogleAuthManager(context: Context)` with `suspend fun signIn(): GoogleAccountInfo`, `suspend fun requestDriveAuthorization(): AuthorizationOutcome`, `fun extractAccessTokenFromResolutionResult(intent: Intent): String`, `suspend fun signOut()`

No automated test for this task — Credential Manager's account picker and the Authorization API's consent screen are real interactive Google UI that can't be driven from an instrumented test. Verified manually as part of Task 7's on-device checklist.

- [ ] **Step 1: Add dependencies to `app/build.gradle`**

Add inside the existing `dependencies { ... }` block:

```groovy
    implementation "androidx.credentials:credentials:1.3.0"
    implementation "androidx.credentials:credentials-play-services-auth:1.3.0"
    implementation "com.google.android.libraries.identity.googleid:googleid:1.1.1"
    implementation "com.google.android.gms:play-services-auth:21.2.0"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1"
```

- [ ] **Step 2: Run a sync/compile to confirm the dependencies resolve**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (this just confirms Gradle can fetch and resolve the new artifacts; nothing references them yet).

- [ ] **Step 3: Write `GoogleAuthManager.kt`**

```kotlin
package com.ziaee.frenchreader.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.tasks.await

private const val WEB_CLIENT_ID = "623452723151-dn9i6noo7c40m72nf7fbsspa8uispjd8.apps.googleusercontent.com"
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

data class GoogleAccountInfo(val email: String, val displayName: String?)

sealed interface AuthorizationOutcome {
    data class Authorized(val accessToken: String) : AuthorizationOutcome
    data class NeedsResolution(val pendingIntent: PendingIntent) : AuthorizationOutcome
}

/** Two separate Google APIs on purpose: [signIn] (Credential Manager) only proves
 * who the user is; it does not grant Drive access. [requestDriveAuthorization]
 * (the Identity Authorization API) is the one that actually authorizes
 * `drive.appdata` and hands back a usable access token. */
class GoogleAuthManager(private val context: Context) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): GoogleAccountInfo {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = credentialManager.getCredential(context, request)
        val credential = GoogleIdTokenCredential.createFrom(response.credential.data)
        return GoogleAccountInfo(email = credential.id, displayName = credential.displayName)
    }

    suspend fun requestDriveAuthorization(): AuthorizationOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        return if (result.hasResolution()) {
            AuthorizationOutcome.NeedsResolution(result.pendingIntent!!)
        } else {
            AuthorizationOutcome.Authorized(result.accessToken!!)
        }
    }

    fun extractAccessTokenFromResolutionResult(intent: Intent): String {
        val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
        return result.accessToken
            ?: throw IllegalStateException("Drive authorization resolution did not return an access token")
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
```

- [ ] **Step 4: Run a full compile to confirm it builds against the real APIs**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If any of the above class/method names have shifted in the exact dependency versions pinned in Step 1 (these APIs have moved before), fix the import/method name to match what actually resolves — check the AAR's sources via Android Studio's "Go to declaration" or the library's release notes, and keep the two-call shape (identity via Credential Manager, authorization via the Identity Authorization API) intact.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle app/src/main/java/com/ziaee/frenchreader/backup/GoogleAuthManager.kt
git commit -m "Add GoogleAuthManager wrapping Credential Manager sign-in and Drive authorization"
```

---

## Task 7: Settings UI integration

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fr/strings.xml`, `app/src/main/res/values-fa/strings.xml`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt`

**Interfaces:**
- Consumes: `GoogleAuthManager` (Task 6), `DriveBackupClient` (Tasks 2/3/4), `BackupPrefs`/`formatLastBackupLabel` (Task 1), `AppDatabase.checkpointWal`/`closeForRestore` (Task 5)

No automated test — this is the end-to-end wiring, verified entirely on the real device per the manual checklist in Step 4.

**Threading (found in review, applies to the code below):** `rememberCoroutineScope()` runs on the main thread, so every blocking call — `HttpURLConnection`, file reads/writes, and `checkpointWal`'s `Thread.sleep` retry — must sit inside `withContext(Dispatchers.IO)`, or it throws `NetworkOnMainThreadException` on every attempt.

- [ ] **Step 1: Add new string resources to all three locale files**

Add to `values/strings.xml` (English), right after the existing `reading_font_scale_title` block or any convenient spot near the end of the settings-related strings:

```xml
    <string name="backup_section_title">Cloud Backup</string>
    <string name="backup_sign_in">Sign in with Google</string>
    <string name="backup_sign_out">Sign out</string>
    <string name="backup_now">Back up now</string>
    <string name="backup_restore">Restore</string>
    <string name="backup_never_backed_up">Never backed up</string>
    <string name="backup_last_backup_label">Last backup: %1$s</string>
    <string name="backup_restore_confirm_title">Restore from Google Drive?</string>
    <string name="backup_restore_confirm_message">This replaces all data currently on this device with the backup from Google Drive. This can\'t be undone.</string>
    <string name="backup_restore_not_found">No backup found for this account</string>
    <string name="backup_success">Backup complete</string>
    <string name="backup_failure">Backup failed</string>
    <string name="backup_restore_failure">Restore failed</string>
```

Add the matching French translations to `values-fr/strings.xml`:

```xml
    <string name="backup_section_title">Sauvegarde cloud</string>
    <string name="backup_sign_in">Se connecter avec Google</string>
    <string name="backup_sign_out">Se déconnecter</string>
    <string name="backup_now">Sauvegarder maintenant</string>
    <string name="backup_restore">Restaurer</string>
    <string name="backup_never_backed_up">Aucune sauvegarde</string>
    <string name="backup_last_backup_label">Dernière sauvegarde : %1$s</string>
    <string name="backup_restore_confirm_title">Restaurer depuis Google Drive ?</string>
    <string name="backup_restore_confirm_message">Cela remplace toutes les données actuelles de cet appareil par la sauvegarde de Google Drive. Cette action est irréversible.</string>
    <string name="backup_restore_not_found">Aucune sauvegarde trouvée pour ce compte</string>
    <string name="backup_success">Sauvegarde terminée</string>
    <string name="backup_failure">Échec de la sauvegarde</string>
    <string name="backup_restore_failure">Échec de la restauration</string>
```

Add the matching Persian translations to `values-fa/strings.xml`:

```xml
    <string name="backup_section_title">پشتیبان‌گیری ابری</string>
    <string name="backup_sign_in">ورود با گوگل</string>
    <string name="backup_sign_out">خروج از حساب</string>
    <string name="backup_now">پشتیبان‌گیری الان</string>
    <string name="backup_restore">بازیابی</string>
    <string name="backup_never_backed_up">هنوز پشتیبان‌گیری نشده</string>
    <string name="backup_last_backup_label">آخرین پشتیبان: %1$s</string>
    <string name="backup_restore_confirm_title">بازیابی از Google Drive؟</string>
    <string name="backup_restore_confirm_message">این کار همهٔ داده‌های فعلی روی این دستگاه رو با پشتیبان Google Drive جایگزین می‌کنه و قابل بازگشت نیست.</string>
    <string name="backup_restore_not_found">پشتیبانی برای این حساب پیدا نشد</string>
    <string name="backup_success">پشتیبان‌گیری انجام شد</string>
    <string name="backup_failure">پشتیبان‌گیری ناموفق بود</string>
    <string name="backup_restore_failure">بازیابی ناموفق بود</string>
```

- [ ] **Step 2: Run the localization completeness test**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.LocalizationCompletenessTest"`
Expected: PASS — all three files now define the same set of keys.

- [ ] **Step 3: Add the "Cloud Backup" section and its logic to `SettingsScreen.kt`**

Add these imports alongside the existing ones at the top of `SettingsScreen.kt`:

```kotlin
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ziaee.frenchreader.backup.AuthorizationOutcome
import com.ziaee.frenchreader.backup.BackupPrefs
import com.ziaee.frenchreader.backup.DriveBackupClient
import com.ziaee.frenchreader.backup.GoogleAuthManager
import com.ziaee.frenchreader.backup.formatLastBackupLabel
import com.ziaee.frenchreader.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
```

Change the `Scaffold(...)` call in `SettingsScreen` to also carry a `SnackbarHost`, matching the pattern already used in `HomeScreen.kt`:

```kotlin
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
```

(keep the existing `topBar` content as-is; only the added `snackbarHost` line and the two `remember`/`rememberCoroutineScope` declarations above it are new). Then pass `snackbarHostState` and `scope` down when calling `CloudBackupSection` in the next step.

Add this composable to the bottom of `SettingsScreen.kt` (after `SettingsRadioRow`):

```kotlin
@Composable
private fun CloudBackupSection(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val authManager = remember { GoogleAuthManager(context) }
    var signedInEmail by remember { mutableStateOf(BackupPrefs.getSignedInEmail(context)) }
    var lastBackupAtMs by remember { mutableStateOf(BackupPrefs.getLastBackupAtMs(context)) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingAccessTokenAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun showMessage(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val token = authManager.extractAccessTokenFromResolutionResult(data)
            pendingAccessTokenAction?.invoke(token)
        } else {
            showMessage(context.getString(R.string.backup_failure))
        }
        pendingAccessTokenAction = null
    }

    fun withDriveAccessToken(onToken: (String) -> Unit) {
        scope.launch {
            when (val outcome = authManager.requestDriveAuthorization()) {
                is AuthorizationOutcome.Authorized -> onToken(outcome.accessToken)
                is AuthorizationOutcome.NeedsResolution -> {
                    pendingAccessTokenAction = onToken
                    try {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(outcome.pendingIntent).build())
                    } catch (e: IntentSender.SendIntentException) {
                        showMessage(context.getString(R.string.backup_failure))
                    }
                }
            }
        }
    }

    Text(
        stringResource(R.string.backup_section_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )

    if (signedInEmail == null) {
        Button(onClick = {
            scope.launch {
                try {
                    val account = authManager.signIn()
                    BackupPrefs.setSignedInEmail(context, account.email)
                    signedInEmail = account.email
                } catch (e: Exception) {
                    showMessage(context.getString(R.string.backup_failure))
                }
            }
        }) {
            Text(stringResource(R.string.backup_sign_in))
        }
    } else {
        Text(signedInEmail!!)
        Text(formatLastBackupLabel(context, lastBackupAtMs))

        Button(onClick = {
            withDriveAccessToken { token ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.get(context)
                            AppDatabase.checkpointWal(db)
                            val dbFile = context.getDatabasePath("french_reader.db")
                            val existingId = DriveBackupClient.findBackupFileId(token)
                            DriveBackupClient.uploadBackup(token, existingId, dbFile)
                        }
                        val now = System.currentTimeMillis()
                        BackupPrefs.setLastBackupAtMs(context, now)
                        lastBackupAtMs = now
                        showMessage(context.getString(R.string.backup_success))
                    } catch (e: Exception) {
                        showMessage(context.getString(R.string.backup_failure))
                    }
                }
            }
        }) {
            Text(stringResource(R.string.backup_now))
        }

        Button(onClick = { showRestoreConfirm = true }) {
            Text(stringResource(R.string.backup_restore))
        }

        Button(onClick = {
            scope.launch {
                authManager.signOut()
                BackupPrefs.setSignedInEmail(context, null)
                signedInEmail = null
            }
        }) {
            Text(stringResource(R.string.backup_sign_out))
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirm = false
                    withDriveAccessToken { token ->
                        scope.launch {
                            try {
                                val restored = withContext(Dispatchers.IO) {
                                    val fileId = DriveBackupClient.findBackupFileId(token)
                                        ?: return@withContext false
                                    val bytes = DriveBackupClient.downloadBackup(token, fileId)
                                    val dbFile = context.getDatabasePath("french_reader.db")
                                    AppDatabase.closeForRestore()
                                    File(dbFile.path + "-wal").delete()
                                    File(dbFile.path + "-shm").delete()
                                    dbFile.writeBytes(bytes)
                                    true
                                }
                                if (!restored) {
                                    showMessage(context.getString(R.string.backup_restore_not_found))
                                    return@launch
                                }
                                restartApp(context)
                            } catch (e: Exception) {
                                showMessage(context.getString(R.string.backup_restore_failure))
                            }
                        }
                    }
                }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = {
                Button(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.accessibility_back))
                }
            }
        )
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
```

Then call `CloudBackupSection(context, scope, snackbarHostState)` inside the existing `Column { ... }` in `SettingsScreen`, right after the font-scale block (after the closing brace of the `FontScale.entries.forEach { ... }` loop, before the `Column`'s own closing brace).

- [ ] **Step 4: Manual on-device verification checklist**

Wake/unlock the phone (per the project's known "locked screen breaks instrumented Compose tests" caveat, this also just needs the screen on and the app in the foreground). Install the debug build and, in Settings:

1. Tap "Sign in with Google" → the account picker appears → pick your test-user account → email now shows in Settings.
2. Tap "Back up now" → the Drive-authorization consent screen appears the first time → approve it → "Backup complete" shows and "Last backup" updates to just now.
3. Add or edit a saved word/vocab entry, then tap "Back up now" again → confirm it re-runs without asking for authorization again (the Authorization API should silently reauthorize on the second call) and "Last backup" updates again.
4. Tap "Restore" → confirm the warning dialog text is readable and makes sense → confirm → app should visibly restart.
5. After restart, verify the vocab entry from step 3 is present (proves the restored file round-tripped correctly, not just "the app didn't crash").
6. Tap "Sign out" → buttons revert to "Sign in with Google"; relaunch the app and confirm it stays signed out (i.e. `BackupPrefs` persisted `null` correctly).
7. Tap "Restore" as a *newly* signed-in account that has never backed up (or after deleting the Drive file manually via `drive.google.com` isn't possible for appDataFolder — instead, test this by signing in with a second Google test-user account that's never run "Back up now") → confirm "No backup found for this account" shows instead of a crash.

- [ ] **Step 5: Run the full test suite one more time for regression safety**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest`
Expected: BUILD SUCCESSFUL, all tests green (screen unlocked).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-fa/strings.xml app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt
git commit -m "Wire Google sign-in and Drive backup/restore into Settings"
```

---

## Task 8: Update ROADMAP.md

**Files:**
- Modify: `ROADMAP.md`

- [ ] **Step 1: Add a new numbered section documenting this feature's status**, following the exact pattern already used for every other completed feature in this file (see section 10, "ارتباط با اپ‌های دیگر", for the template: a status line, what was built, what files, what was tested, and what — if anything — is still manual/pending). Cover: the deliberate exception to the "fully local" philosophy and why, the two-API auth split, the appDataFolder/single-file backup design, and the manual-only trigger.

- [ ] **Step 2: Commit**

```bash
git add ROADMAP.md
git commit -m "Document Google Drive backup/restore in ROADMAP"
```
