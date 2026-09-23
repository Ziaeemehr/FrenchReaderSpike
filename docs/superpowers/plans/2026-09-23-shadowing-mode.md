# Shadowing Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a shadowing mode to the reading screen. The TTS plays one sentence, the learner holds a mic button and repeats it, the app colors each word matched or missed, shows a score, and lets the learner replay their own recording.

**Architecture:** A new `shadowing/` package holds these pieces:
- a pure `TranscriptAligner`
- a `SpeechEngine` interface with two implementations (Vosk offline and Android `SpeechRecognizer`), built on a shared `MicRecorder`
- a `VoskModelManager` for the one-time model download
- a testable `ShadowingController` state machine

`ReadingViewModel` owns the controller and adds a stop-at-sentence-end playback mode, driven by an explicit target sentence. Attempts go into a new Room table (`shadow_attempts`), and a Statistics card and a Settings tab read from it.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room 2.6.1, Media3 ExoPlayer 1.3.1, Vosk Android `com.alphacephei:vosk-android:0.3.47` (+ JNA 5.13.0 aar), `android.speech.SpeechRecognizer`, JUnit4 + kotlinx-coroutines-test, AndroidX instrumented tests.

**Spec:** `docs/superpowers/specs/2026-09-23-shadowing-mode-design.md`

## Global Constraints

- Write implementation code via `codex exec` when practical. Claude verifies the diffs and the build (project CLAUDE.md). Codex uses default effort and the standard service tier only.
- `minSdk 26`, `targetSdk 34`, `compileSdk 34`, Java 17. Do not bump any of them.
- Every new user-visible string goes into all three of `values/strings.xml`, `values-fr/strings.xml` and `values-fa/strings.xml`. `LocalizationCompletenessTest` enforces this. No hardcoded Compose literals.
- Recordings are **never** written to disk or the database. They are PCM in memory only, discarded on Retry, Next, toggle off, or leaving the screen.
- Only attempts with a non-empty transcript are stored.
- Recordings shorter than 300 ms are ignored (accidental tap).
- The in-memory recording is capped at 30 s.
- The Vosk engine is the default and must work with no network once the model is installed.
- Room: `SCHEMA_VERSION` goes 14 → 15 with `MIGRATION_14_15` added to `ALL_MIGRATIONS`. `exportSchema = false` stays.
- On-device testing uses a signed release build only: `./gradlew assembleRelease` then `adb install -r app/build/outputs/apk/release/app-release.apk`. Never a debug build.
- Unit tests: `./gradlew testDebugUnitTest`. Instrumented tests: `./gradlew connectedDebugAndroidTest` (needs a device). If none is connected, report the step as skipped. Don't claim it passed.

## Review Focus

1. **Next right after an auto-pause.** The auto-pause fires at the exact start of the next sentence, so position-based `nextSentence()` would skip a sentence. Next and Play original must use the explicit shadow target (Task 5 tests `nextSentenceRef` and `previousSentenceRef`).
2. **Words with elisions, hyphens and apostrophe variants** (`l’avion` with a curly apostrophe, `peut-être`, `c'est-à-dire`) must still match what the recognizer prints (Task 1 tests).
3. **An interrupted or partial model download** must never count as installed. The unzip goes to a temp dir with an atomic rename and a marker file (Task 3 tests).
4. **Pressing the mic while TTS plays, or Play while recording**, must not overlap. The controller refuses to record during playback, and playback is refused while recording (Task 5 controller tests).
5. **Leaving the reading screen or rotating mid-recording** must release the mic and not save a half attempt. `onCleared` cancels the controller (Task 5 test `cancel_whileRecording_savesNothing`).

---

### Task 1: TranscriptAligner

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/shadowing/TranscriptAligner.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/shadowing/TranscriptAlignerTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `data class WordResult(val word: String, val matched: Boolean)`
  - `data class AlignmentResult(val words: List<WordResult>, val matched: Int, val total: Int)`
  - `object TranscriptAligner { fun normalizeTokens(text: String): List<String>; fun align(original: String, transcript: String): AlignmentResult }`
  - `WordResult.word` is the **original display word** (punctuation stripped, original case and accents kept). With elision splitting, `l'avion` yields two WordResults: `l'` and `avion`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAlignerTest {
    @Test fun exactMatch_allWordsMatched() {
        val r = TranscriptAligner.align("Le chat dort.", "le chat dort")
        assertEquals(3, r.total); assertEquals(3, r.matched)
        assertEquals(listOf("Le", "chat", "dort"), r.words.map { it.word })
    }

    @Test fun accentsAndCaseIgnored() {
        val r = TranscriptAligner.align("Élève à l'école", "eleve a l ecole")
        assertEquals(r.total, r.matched)
    }

    @Test fun elisionSplit_straightAndCurlyApostrophe() {
        assertEquals(listOf("l", "avion"), TranscriptAligner.normalizeTokens("l'avion"))
        assertEquals(listOf("l", "avion"), TranscriptAligner.normalizeTokens("l’avion"))
        val r = TranscriptAligner.align("J'aime l’avion", "j'aime l'avion")
        assertEquals(4, r.total); assertEquals(4, r.matched)
        assertEquals(listOf("J'", "aime", "l’", "avion"), r.words.map { it.word })
    }

    @Test fun hyphenatedWordsSplit() {
        assertEquals(listOf("peut", "etre"), TranscriptAligner.normalizeTokens("peut-être"))
        val r = TranscriptAligner.align("C'est-à-dire", "c'est à dire")
        assertEquals(r.total, r.matched)
    }

    @Test fun missingWordMarkedUnmatched() {
        val r = TranscriptAligner.align("une magnifique image dans un livre", "une image dans un livre")
        assertEquals(6, r.total); assertEquals(5, r.matched)
        assertEquals(false, r.words[1].matched)
    }

    @Test fun wrongWordMarkedUnmatched() {
        val r = TranscriptAligner.align("sur la forêt vierge", "sur la forêt vi")
        assertEquals(listOf(true, true, true, false), r.words.map { it.matched })
    }

    @Test fun extraSpokenWordsDoNotReduceScore() {
        val r = TranscriptAligner.align("le chat dort", "euh le chat euh dort")
        assertEquals(3, r.matched)
    }

    @Test fun emptyTranscript_nothingMatched() {
        val r = TranscriptAligner.align("le chat dort", "")
        assertEquals(3, r.total); assertEquals(0, r.matched)
    }

    @Test fun punctuationOnlyTokensDropped() {
        val r = TranscriptAligner.align("« Bonjour ! » — dit-il.", "bonjour dit il")
        assertEquals(listOf("Bonjour", "dit", "il"), r.words.map { it.word })
        assertEquals(3, r.matched)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.shadowing.TranscriptAlignerTest'`
Expected: compilation FAIL, `Unresolved reference: TranscriptAligner`

- [ ] **Step 3: Implement**

```kotlin
package com.ziaee.frenchreader.shadowing

import java.text.Normalizer

data class WordResult(val word: String, val matched: Boolean)
data class AlignmentResult(val words: List<WordResult>, val matched: Int, val total: Int)

/** Word-level comparison of a sentence with what the recognizer heard. Pure, no Android deps. */
object TranscriptAligner {
    private val APOSTROPHES = Regex("[’ʼ`´]")
    // Elided particle followed by an apostrophe, at a word start: l' j' qu' c' d' n' s' t' m'
    private val ELISION = Regex("(?i)\\b(qu|[ljcdnstm])'")
    private val SPLIT = Regex("[\\s\\-–—]+")
    private val EDGE_PUNCT = Regex("^[^\\p{L}\\p{N}']+|[^\\p{L}\\p{N}']+$")
    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Display tokens: original casing and accents, elisions kept with their apostrophe. */
    private fun displayTokens(text: String): List<String> =
        text.split(SPLIT).flatMap { raw ->
            val w = raw.replace(EDGE_PUNCT, "")
            if (w.isEmpty()) return@flatMap emptyList()
            val unified = w.replace(APOSTROPHES, "'")
            val m = ELISION.find(unified)
            if (m != null && m.range.first == 0 && m.range.last < unified.length - 1) {
                val cut = m.range.last + 1
                listOf(w.substring(0, cut), w.substring(cut))
            } else listOf(w)
        }

    private fun key(token: String): String =
        Normalizer.normalize(token.replace(APOSTROPHES, "'"), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .replace("'", "")

    fun normalizeTokens(text: String): List<String> =
        displayTokens(text).map(::key).filter { it.isNotEmpty() }

    fun align(original: String, transcript: String): AlignmentResult {
        val display = displayTokens(original).filter { key(it).isNotEmpty() }
        val a = display.map(::key)
        val b = normalizeTokens(transcript)
        // LCS table: extra spoken words cost nothing, and each original word is matched or not.
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val matched = BooleanArray(a.size)
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { matched[i] = true; i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        val words = display.mapIndexed { idx, w -> WordResult(w, matched[idx]) }
        return AlignmentResult(words, matched.count { it }, words.size)
    }
}
```

The spec's word-level alignment is implemented as LCS, which is Levenshtein alignment without substitution cost. It handles missing, wrong and extra words in one pass.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.shadowing.TranscriptAlignerTest'`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/shadowing/TranscriptAligner.kt app/src/test/java/com/ziaee/frenchreader/shadowing/TranscriptAlignerTest.kt
git commit -m "feat(shadowing): word-level transcript aligner"
```

---

### Task 2: ShadowAttempt table, DAO and migration 14→15

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Entities.kt` (append entity)
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Daos.kt` (append DAO)
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt` (migration, entity list, DAO accessor, `SCHEMA_VERSION = 15`)
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/data/Migration14To15Test.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/data/ShadowAttemptDaoTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `@Entity(tableName = "shadow_attempts") data class ShadowAttempt(id: Long = 0, textId: Long, chunkIndex: Int, sentenceIndex: Int, matched: Int, total: Int, engine: String, timestampMs: Long)`
  - `interface ShadowAttemptDao { suspend fun insert(a: ShadowAttempt): Long; suspend fun getSince(sinceMs: Long): List<ShadowAttempt>; suspend fun countAll(): Int; suspend fun distinctActiveDates(): List<String> }`
  - `AppDatabase.shadowAttemptDao()`
  - `MIGRATION_14_15`

- [ ] **Step 1: Write the failing migration test**

```kotlin
package com.ziaee.frenchreader.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration14To15Test {
    private val databaseName = "migration-14-15-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context).name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
    )

    @After fun tearDown() { helper.close(); context.deleteDatabase(databaseName) }

    @Test fun migrationCreatesShadowAttemptsTable() {
        val db = helper.writableDatabase
        MIGRATION_14_15.migrate(db)
        db.execSQL(
            "INSERT INTO shadow_attempts (textId, chunkIndex, sentenceIndex, matched, total, engine, timestampMs) " +
                "VALUES (1, 0, 2, 7, 9, 'vosk', 1000)"
        )
        db.query("SELECT matched, total, engine FROM shadow_attempts").use { c ->
            c.moveToFirst()
            assertEquals(7, c.getInt(0)); assertEquals(9, c.getInt(1)); assertEquals("vosk", c.getString(2))
        }
    }
}
```

- [ ] **Step 2: Write the failing DAO test**

```kotlin
package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ShadowAttemptDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ShadowAttemptDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        dao = db.shadowAttemptDao()
    }

    @After fun tearDown() { db.close() }

    private fun ms(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).plusHours(12).toInstant().toEpochMilli()
    private fun attempt(ts: Long) = ShadowAttempt(textId = 1, chunkIndex = 0, sentenceIndex = 0, matched = 3, total = 4, engine = "vosk", timestampMs = ts)

    @Test fun getSince_filtersByTimestamp_andDistinctDates() = runBlocking {
        val today = LocalDate.now()
        dao.insert(attempt(ms(today.minusDays(10))))
        dao.insert(attempt(ms(today)))
        dao.insert(attempt(ms(today)))
        assertEquals(2, dao.getSince(ms(today.minusDays(6))).size)
        assertEquals(3, dao.countAll())
        assertEquals(setOf(today.toString(), today.minusDays(10).toString()), dao.distinctActiveDates().toSet())
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: FAIL, `Unresolved reference: MIGRATION_14_15` / `ShadowAttempt`

- [ ] **Step 4: Implement**

Append to `Entities.kt`:

```kotlin
/** One shadowing try on one sentence. Only the score is kept -- never the audio. */
@Entity(tableName = "shadow_attempts", indices = [Index(value = ["timestampMs"])])
data class ShadowAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val textId: Long,
    val chunkIndex: Int,
    val sentenceIndex: Int,
    val matched: Int,
    val total: Int,
    val engine: String,
    val timestampMs: Long
)
```

Append to `Daos.kt`:

```kotlin
@Dao
interface ShadowAttemptDao {
    @Insert
    suspend fun insert(attempt: ShadowAttempt): Long

    @Query("SELECT * FROM shadow_attempts WHERE timestampMs >= :sinceMs ORDER BY timestampMs")
    suspend fun getSince(sinceMs: Long): List<ShadowAttempt>

    @Query("SELECT COUNT(*) FROM shadow_attempts")
    suspend fun countAll(): Int

    @Query("SELECT DISTINCT date(timestampMs / 1000, 'unixepoch', 'localtime') FROM shadow_attempts")
    suspend fun distinctActiveDates(): List<String>
}
```

In `AppDatabase.kt`, add after `MIGRATION_13_14`:

```kotlin
// v14 -> v15: shadowing-mode scores (one row per attempt; audio is never stored).
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shadow_attempts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`textId` INTEGER NOT NULL, " +
                "`chunkIndex` INTEGER NOT NULL, " +
                "`sentenceIndex` INTEGER NOT NULL, " +
                "`matched` INTEGER NOT NULL, " +
                "`total` INTEGER NOT NULL, " +
                "`engine` TEXT NOT NULL, " +
                "`timestampMs` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_shadow_attempts_timestampMs` " +
                "ON `shadow_attempts` (`timestampMs`)"
        )
    }
}
```

Then:
- Append `MIGRATION_14_15` to `ALL_MIGRATIONS`.
- Add `ShadowAttempt::class` to `entities`.
- Add `abstract fun shadowAttemptDao(): ShadowAttemptDao`.
- Set `const val SCHEMA_VERSION = 15`.

Check that `ReviewLogDao.distinctActiveDates` uses the same `date(... 'localtime')` form, and copy its exact SQL if it differs.

- [ ] **Step 5: Run the tests**

Run: `./gradlew testDebugUnitTest assembleDebug` (compile check). Then, with a device: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.data.Migration14To15Test,com.ziaee.frenchreader.data.ShadowAttemptDaoTest`
Expected: PASS. Without a device, report "instrumented tests skipped: no device".

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/data/ app/src/androidTest/java/com/ziaee/frenchreader/data/Migration14To15Test.kt app/src/androidTest/java/com/ziaee/frenchreader/data/ShadowAttemptDaoTest.kt
git commit -m "feat(shadowing): shadow_attempts table, DAO and v15 migration"
```

---

### Task 3: ShadowingPrefs and VoskModelManager

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/shadowing/ShadowingPrefs.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/shadowing/VoskModelManager.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/shadowing/VoskModelInstallerTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `enum class SpeechEngineKind(val id: String) { VOSK("vosk"), ANDROID("android") }`
  - `object ShadowingPrefs { fun getEngine(context: Context): SpeechEngineKind; fun setEngine(context: Context, kind: SpeechEngineKind) }` (default `VOSK`)
  - `object VoskModelInstaller { fun installFromZip(zip: InputStream, targetDir: File) }`: pure JVM, testable. It unzips into `targetDir.parent/<name>.tmp`, strips the single top-level folder, writes a marker `.installed`, deletes any old `targetDir`, then renames tmp → `targetDir`. On any exception it deletes tmp and rethrows.
  - `fun isModelInstalled(dir: File): Boolean`: returns `File(dir, ".installed").exists()`
  - `class VoskModelManager(context: Context) { val modelDir: File; fun isInstalled(): Boolean; fun sizeBytes(): Long; suspend fun download(onProgress: (Float) -> Unit); fun delete() }`
  - `const val VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoskModelInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, body) -> z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test fun installsAndStripsTopFolder() {
        val target = File(tmp.root, "vosk-fr")
        VoskModelInstaller.installFromZip(
            ByteArrayInputStream(zipOf("vosk-model-small-fr-0.22/am/final.mdl" to "x", "vosk-model-small-fr-0.22/conf/model.conf" to "y")),
            target
        )
        assertEquals("x", File(target, "am/final.mdl").readText())
        assertTrue(isModelInstalled(target))
        assertFalse(File(tmp.root, "vosk-fr.tmp").exists())
    }

    @Test fun truncatedZip_leavesNothingInstalled() {
        val target = File(tmp.root, "vosk-fr")
        val bytes = zipOf("m/am/final.mdl" to "x".repeat(10_000))
        runCatching { VoskModelInstaller.installFromZip(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)), target) }
        assertFalse(isModelInstalled(target))
        assertFalse(File(tmp.root, "vosk-fr.tmp").exists())
    }

    @Test fun rejectsZipSlipEntries() {
        val target = File(tmp.root, "vosk-fr")
        val result = runCatching {
            VoskModelInstaller.installFromZip(ByteArrayInputStream(zipOf("m/../../evil.txt" to "x")), target)
        }
        assertTrue(result.isFailure)
        assertFalse(File(tmp.root.parentFile, "evil.txt").exists())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.shadowing.VoskModelInstallerTest'`
Expected: compilation FAIL, `Unresolved reference: VoskModelInstaller`

- [ ] **Step 3: Implement**

`ShadowingPrefs.kt`:

```kotlin
package com.ziaee.frenchreader.shadowing

import android.content.Context

enum class SpeechEngineKind(val id: String) { VOSK("vosk"), ANDROID("android") }

object ShadowingPrefs {
    private const val PREFS_NAME = "shadowing_prefs"
    private const val KEY_ENGINE = "engine"

    fun getEngine(context: Context): SpeechEngineKind {
        val id = prefs(context).getString(KEY_ENGINE, SpeechEngineKind.VOSK.id)
        return SpeechEngineKind.entries.firstOrNull { it.id == id } ?: SpeechEngineKind.VOSK
    }

    fun setEngine(context: Context, kind: SpeechEngineKind) =
        prefs(context).edit().putString(KEY_ENGINE, kind.id).apply()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
```

`VoskModelManager.kt`:

```kotlin
package com.ziaee.frenchreader.shadowing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

const val VOSK_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip"
private const val MARKER = ".installed"

fun isModelInstalled(dir: File): Boolean = File(dir, MARKER).exists()

object VoskModelInstaller {
    fun installFromZip(zip: InputStream, targetDir: File) {
        val tmp = File(targetDir.parentFile, targetDir.name + ".tmp")
        tmp.deleteRecursively()
        try {
            tmp.mkdirs()
            val root = tmp.canonicalFile
            ZipInputStream(zip).use { z ->
                while (true) {
                    val entry = z.nextEntry ?: break
                    // Strip the single top-level folder the Vosk zips ship with.
                    val relative = entry.name.substringAfter('/', "")
                    if (relative.isEmpty()) continue
                    val out = File(tmp, relative).canonicalFile
                    require(out.path.startsWith(root.path + File.separator)) { "Bad zip entry: ${entry.name}" }
                    if (entry.isDirectory) out.mkdirs()
                    else { out.parentFile?.mkdirs(); out.outputStream().use { z.copyTo(it) } }
                }
            }
            File(tmp, MARKER).writeText("ok")
            targetDir.deleteRecursively()
            check(tmp.renameTo(targetDir)) { "Could not move model into place" }
        } catch (e: Throwable) {
            tmp.deleteRecursively()
            throw e
        }
    }
}

class VoskModelManager(context: Context) {
    val modelDir: File = File(context.filesDir, "vosk/fr-small")

    fun isInstalled(): Boolean = isModelInstalled(modelDir)

    fun sizeBytes(): Long = modelDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    suspend fun download(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        modelDir.parentFile?.mkdirs()
        val zipFile = File(modelDir.parentFile, "model.zip.part")
        try {
            val conn = URL(VOSK_MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                zipFile.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            zipFile.inputStream().use { VoskModelInstaller.installFromZip(it, modelDir) }
        } finally {
            zipFile.delete()
        }
    }

    fun delete() { modelDir.deleteRecursively() }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.shadowing.VoskModelInstallerTest'`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/shadowing/ShadowingPrefs.kt app/src/main/java/com/ziaee/frenchreader/shadowing/VoskModelManager.kt app/src/test/java/com/ziaee/frenchreader/shadowing/VoskModelInstallerTest.kt
git commit -m "feat(shadowing): engine preference and safe Vosk model install"
```

---

### Task 4: MicRecorder and the two speech engines

**Files:**
- Modify: `app/build.gradle` (Vosk + JNA dependencies)
- Modify: `app/src/main/AndroidManifest.xml` (add `RECORD_AUDIO`)
- Create: `app/src/main/java/com/ziaee/frenchreader/shadowing/MicRecorder.kt`
- Create: `app/src/main/java/com/ziaee/frenchreader/shadowing/SpeechEngine.kt` (interface + `VoskEngine` + `AndroidSpeechEngine` + `parseVoskText`)
- Test: `app/src/test/java/com/ziaee/frenchreader/shadowing/SpeechEngineHelpersTest.kt`

**Interfaces:**
- Consumes: `VoskModelManager.modelDir` (Task 3)
- Produces:
  - `const val SAMPLE_RATE = 16_000`
  - `data class Recording(val transcript: String, val pcm: ShortArray?, val durationMs: Long)`: `pcm == null` means Replay mine is unavailable.
  - `interface SpeechEngine { val kind: SpeechEngineKind; fun start(); suspend fun stop(): Recording; fun cancel(); fun release() }`
  - `class MicRecorder { fun start(); fun stop(): ShortArray; fun cancel() }`: 30 s cap (`MAX_SAMPLES = SAMPLE_RATE * 30`)
  - `object PcmPlayer { fun play(pcm: ShortArray); fun stop() }`
  - `internal fun parseVoskText(json: String): String`
  - `internal fun durationMsOf(samples: Int): Long`
  - `class VoskEngine(modelDir: File) : SpeechEngine`
  - `class AndroidSpeechEngine(context: Context) : SpeechEngine`. `start()`/`stop()` must be called on the main thread.

The spec described `recognize(pcm)` plus `canUseRecordedAudio`. This plan uses a `start()`/`stop()` session instead, because API < 33 Android recognition must own the mic live. `Recording.pcm == null` carries the same "no replay" information.

- [ ] **Step 1: Write the failing helper tests**

```kotlin
package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechEngineHelpersTest {
    @Test fun parseVoskText_readsTextField() {
        assertEquals("le chat dort", parseVoskText("{\n  \"text\" : \"le chat dort\"\n}"))
    }

    @Test fun parseVoskText_emptyOrBroken() {
        assertEquals("", parseVoskText("{\"text\" : \"\"}"))
        assertEquals("", parseVoskText("not json"))
    }

    @Test fun durationMsOf_uses16k() {
        assertEquals(1000L, durationMsOf(16_000))
        assertEquals(250L, durationMsOf(4_000))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.shadowing.SpeechEngineHelpersTest'`
Expected: compilation FAIL, `Unresolved reference: parseVoskText`

- [ ] **Step 3: Add dependencies and the permission**

In `app/build.gradle` `dependencies`, after the jsoup line:

```groovy
    // Offline French speech recognition for shadowing mode (model downloaded at runtime).
    implementation 'com.alphacephei:vosk-android:0.3.47'
    implementation 'net.java.dev.jna:jna:5.13.0@aar'
```

In `AndroidManifest.xml`, after the `RECEIVE_BOOT_COMPLETED` line:

```xml
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
```

- [ ] **Step 4: Implement `MicRecorder.kt`**

```kotlin
package com.ziaee.frenchreader.shadowing

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread

const val SAMPLE_RATE = 16_000
private const val MAX_SAMPLES = SAMPLE_RATE * 30

internal fun durationMsOf(samples: Int): Long = samples * 1000L / SAMPLE_RATE

/** Records mono 16 kHz PCM into memory. Never touches disk. Caller must hold RECORD_AUDIO. */
class MicRecorder {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val buffer = ShortArray(MAX_SAMPLES)
    @Volatile private var size = 0

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096))
        record = r
        size = 0
        running = true
        r.startRecording()
        worker = thread(name = "mic-recorder") {
            val chunk = ShortArray(1024)
            while (running && size < MAX_SAMPLES) {
                val n = r.read(chunk, 0, minOf(chunk.size, MAX_SAMPLES - size))
                if (n > 0) { System.arraycopy(chunk, 0, buffer, size, n); size += n }
            }
        }
    }

    fun stop(): ShortArray {
        running = false
        worker?.join(500)
        worker = null
        record?.run { runCatching { stop() }; release() }
        record = null
        return buffer.copyOf(size)
    }

    fun cancel() { stop(); size = 0 }
}

/** Plays back an in-memory recording for "Replay mine". */
object PcmPlayer {
    private var track: AudioTrack? = null

    fun play(pcm: ShortArray) {
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(maxOf(pcm.size * 2, 2))
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        track = t
    }

    fun stop() { track?.run { runCatching { stop() }; release() }; track = null }
}
```

- [ ] **Step 5: Implement `SpeechEngine.kt`**

```kotlin
package com.ziaee.frenchreader.shadowing

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Recording(val transcript: String, val pcm: ShortArray?, val durationMs: Long)

interface SpeechEngine {
    val kind: SpeechEngineKind
    fun start()
    suspend fun stop(): Recording
    fun cancel()
    fun release()
}

internal fun parseVoskText(json: String): String =
    runCatching { JSONObject(json).optString("text", "") }.getOrDefault("").trim()

class VoskEngine(private val modelDir: File) : SpeechEngine {
    override val kind = SpeechEngineKind.VOSK
    private val recorder = MicRecorder()
    private var model: Model? = null

    override fun start() = recorder.start()

    override suspend fun stop(): Recording {
        val pcm = recorder.stop()
        val text = withContext(Dispatchers.Default) {
            val m = model ?: Model(modelDir.absolutePath).also { model = it }
            Recognizer(m, SAMPLE_RATE.toFloat()).use { rec ->
                rec.acceptWaveForm(pcm, pcm.size)
                parseVoskText(rec.finalResult)
            }
        }
        return Recording(text, pcm, durationMsOf(pcm.size))
    }

    override fun cancel() = recorder.cancel()
    override fun release() { recorder.cancel(); model?.close(); model = null }
}

/** Android's recognizer. API 33+: we record, then feed our PCM through EXTRA_AUDIO_SOURCE, so
 * replay works. Older: the recognizer owns the mic live, so pcm is null (no replay). Main thread only. */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    override val kind = SpeechEngineKind.ANDROID
    private val feedsAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private val recorder = MicRecorder()
    private var recognizer: SpeechRecognizer? = null
    private var result: CompletableDeferred<String>? = null
    private var startedAtMs = 0L

    private fun baseIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun newRecognizer(): SpeechRecognizer {
        recognizer?.destroy()
        val deferred = CompletableDeferred<String>().also { result = it }
        return SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    deferred.complete(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onError(error: Int) { deferred.complete("") }
                override fun onReadyForSpeech(p: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(v: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(b: Bundle?) = Unit
                override fun onEvent(t: Int, b: Bundle?) = Unit
            })
        }.also { recognizer = it }
    }

    override fun start() {
        startedAtMs = System.currentTimeMillis()
        if (feedsAudio) recorder.start() else newRecognizer().startListening(baseIntent())
    }

    override suspend fun stop(): Recording {
        if (!feedsAudio) {
            recognizer?.stopListening()
            val text = result?.await().orEmpty()
            return Recording(text.trim(), null, System.currentTimeMillis() - startedAtMs)
        }
        val pcm = recorder.stop()
        val pipe = ParcelFileDescriptor.createPipe()
        val intent = baseIntent().apply {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
        }
        newRecognizer().startListening(intent)
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                bytes.asShortBuffer().put(pcm)
                out.write(bytes.array())
            }
        }
        pipe[0].close()
        val text = result?.await().orEmpty()
        return Recording(text.trim(), pcm, durationMsOf(pcm.size))
    }

    override fun cancel() {
        recorder.cancel()
        recognizer?.cancel()
        result?.complete("")
    }

    override fun release() { cancel(); recognizer?.destroy(); recognizer = null }

    companion object {
        fun isAvailable(context: Context) = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
```

- [ ] **Step 6: Run the tests and the build**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.shadowing.SpeechEngineHelpersTest' && ./gradlew assembleDebug`
Expected: PASS and BUILD SUCCESSFUL. If the Vosk AAR's native libs conflict with the `abiFilters`, the build still succeeds: the filters keep only `arm64-v8a` and `x86_64`, and Vosk ships both.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle app/src/main/AndroidManifest.xml app/src/main/java/com/ziaee/frenchreader/shadowing/MicRecorder.kt app/src/main/java/com/ziaee/frenchreader/shadowing/SpeechEngine.kt app/src/test/java/com/ziaee/frenchreader/shadowing/SpeechEngineHelpersTest.kt
git commit -m "feat(shadowing): mic recorder and Vosk/Android speech engines"
```

---

### Task 5: ShadowingController and sentence-targeted playback in ReadingViewModel

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/shadowing/ShadowingController.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/shadowing/ShadowingControllerTest.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/ShadowSentenceRefTest.kt`

**Interfaces:**
- Consumes: `TranscriptAligner.align` (Task 1), `ShadowAttempt` and `shadowAttemptDao()` (Task 2), `SpeechEngine`, `Recording` and `PcmPlayer` (Task 4), `ShadowingPrefs` and `VoskModelManager` (Task 3), `VocabPrefs.addStudyTimeMs(context, date, deltaMs)` (existing)
- Produces:
  - `data class SentenceRef(val chunkIndex: Int, val sentenceIndex: Int)`
  - `internal fun nextSentenceRef(chunks: List<ChunkState>, ref: SentenceRef): SentenceRef?` and `internal fun previousSentenceRef(chunks: List<ChunkState>, ref: SentenceRef): SentenceRef?` (in `ReadingViewModel.kt`)
  - `sealed interface ShadowPhase { Idle; Recording; Recognizing; data class Result(val alignment: AlignmentResult); data class Error(val kind: ShadowError) }`
  - `enum class ShadowError { NOT_HEARD, ENGINE_FAILED }`
  - `data class ShadowingState(val enabled: Boolean = false, val target: SentenceRef? = null, val sentenceText: String = "", val phase: ShadowPhase = ShadowPhase.Idle, val hasRecording: Boolean = false)`
  - `class ShadowingController(scope, engineFactory: () -> SpeechEngine, saveAttempt: suspend (ShadowAttempt) -> Unit, addStudyTimeMs: (Long) -> Unit, now: () -> Long, isPlaying: () -> Boolean)` with `state: StateFlow<ShadowingState>`, `setEnabled(Boolean)`, `setTarget(textId: Long, ref: SentenceRef, text: String)`, `pressStart(): Boolean`, `pressEnd()`, `retry()`, `replayMine()`, `cancel()`, `release()`
  - `ReadingViewModel`: `val shadowing: StateFlow<ShadowingState>`, `fun setShadowing(enabled: Boolean)`, `fun shadowPlayOriginal()`, `fun shadowNext()`, `fun shadowPrevious()`, `fun shadowPressStart()`, `fun shadowPressEnd()`, `fun shadowRetry()`, `fun shadowReplayMine()`

- [ ] **Step 1: Write the failing sentence-ref tests**

`ShadowSentenceRefTest.kt`: build chunks with `ChunkState(block = MarkdownParser.parse(text), sentences = ...)`, following `CardSlotTest`/existing test construction. For an IMAGE block, use `MarkdownParser.parse("![](x.png)")`.

```kotlin
package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.shadowing.SentenceRef
import com.ziaee.frenchreader.text.MarkdownParser
import com.ziaee.frenchreader.tts.SentenceBoundary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShadowSentenceRefTest {
    private fun chunk(n: Int) = ChunkState(
        block = MarkdownParser.parse("texte"),
        sentences = List(n) { SentenceBoundary("s$it", it * 1000.0, 1000.0) }
    )
    private val image = ChunkState(block = MarkdownParser.parse("![](x.png)"))

    @Test fun nextWithinChunk() =
        assertEquals(SentenceRef(0, 1), nextSentenceRef(listOf(chunk(2)), SentenceRef(0, 0)))

    @Test fun nextCrossesChunkAndSkipsImage() =
        assertEquals(SentenceRef(2, 0), nextSentenceRef(listOf(chunk(1), image, chunk(2)), SentenceRef(0, 0)))

    @Test fun nextIntoUnsynthesizedChunkPointsAtItsFirstSentence() =
        assertEquals(SentenceRef(1, 0), nextSentenceRef(listOf(chunk(1), chunk(0)), SentenceRef(0, 0)))

    @Test fun nextAtEndIsNull() = assertNull(nextSentenceRef(listOf(chunk(1)), SentenceRef(0, 0)))

    @Test fun previousCrossesChunkToLastSentence() =
        assertEquals(SentenceRef(0, 2), previousSentenceRef(listOf(chunk(3), image, chunk(1)), SentenceRef(2, 0)))

    @Test fun previousAtStartIsNull() = assertNull(previousSentenceRef(listOf(chunk(1)), SentenceRef(0, 0)))
}
```

- [ ] **Step 2: Write the failing controller tests**

```kotlin
package com.ziaee.frenchreader.shadowing

import com.ziaee.frenchreader.data.ShadowAttempt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine(var next: Recording) : SpeechEngine {
    override val kind = SpeechEngineKind.VOSK
    var started = 0; var cancelled = 0
    override fun start() { started++ }
    override suspend fun stop() = next
    override fun cancel() { cancelled++ }
    override fun release() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShadowingControllerTest {
    private val saved = mutableListOf<ShadowAttempt>()
    private var studyMs = 0L
    private var clock = 10_000L
    private var playing = false

    private fun TestScope.controller(engine: FakeEngine) = ShadowingController(
        scope = this, engineFactory = { engine },
        saveAttempt = { saved += it }, addStudyTimeMs = { studyMs += it },
        now = { clock }, isPlaying = { playing }
    ).apply {
        setEnabled(true)
        setTarget(textId = 7, ref = SentenceRef(1, 2), text = "le chat dort")
    }

    @Test fun pressAndRelease_scoresAndSaves() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("le chat", ShortArray(8000), 500)))
        assertTrue(c.pressStart()); clock += 2_000; c.pressEnd(); advanceUntilIdle()
        val phase = c.state.value.phase as ShadowPhase.Result
        assertEquals(2, phase.alignment.matched); assertEquals(3, phase.alignment.total)
        assertEquals(1, saved.size)
        assertEquals(ShadowAttempt(textId = 7, chunkIndex = 1, sentenceIndex = 2, matched = 2, total = 3, engine = "vosk", timestampMs = clock), saved[0])
        assertEquals(2_000L, studyMs)
        assertTrue(c.state.value.hasRecording)
    }

    @Test fun emptyTranscript_errorAndNothingSaved() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("", ShortArray(8000), 500)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        assertEquals(ShadowPhase.Error(ShadowError.NOT_HEARD), c.state.value.phase)
        assertTrue(saved.isEmpty())
    }

    @Test fun tooShortRecording_ignored() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("le", ShortArray(100), 120)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertTrue(saved.isEmpty())
    }

    @Test fun cannotRecordWhilePlaying() = runTest(StandardTestDispatcher()) {
        val engine = FakeEngine(Recording("x", ShortArray(8000), 500))
        val c = controller(engine)
        playing = true
        assertFalse(c.pressStart())
        assertEquals(0, engine.started)
    }

    @Test fun cancel_whileRecording_savesNothing() = runTest(StandardTestDispatcher()) {
        val engine = FakeEngine(Recording("le chat dort", ShortArray(8000), 500))
        val c = controller(engine)
        c.pressStart(); c.cancel(); advanceUntilIdle()
        assertEquals(1, engine.cancelled)
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertTrue(saved.isEmpty())
    }

    @Test fun newTarget_clearsResultAndRecording() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("le chat dort", ShortArray(8000), 500)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        c.setTarget(7, SentenceRef(1, 3), "autre phrase")
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertFalse(c.state.value.hasRecording)
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.shadowing.ShadowingControllerTest' --tests 'com.ziaee.frenchreader.ui.ShadowSentenceRefTest'`
Expected: compilation FAIL, `Unresolved reference: ShadowingController` / `nextSentenceRef`

- [ ] **Step 4: Implement `ShadowingController.kt`**

```kotlin
package com.ziaee.frenchreader.shadowing

import com.ziaee.frenchreader.data.ShadowAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MIN_RECORDING_MS = 300L

data class SentenceRef(val chunkIndex: Int, val sentenceIndex: Int)

enum class ShadowError { NOT_HEARD, ENGINE_FAILED }

sealed interface ShadowPhase {
    data object Idle : ShadowPhase
    data object Recording : ShadowPhase
    data object Recognizing : ShadowPhase
    data class Result(val alignment: AlignmentResult) : ShadowPhase
    data class Error(val kind: ShadowError) : ShadowPhase
}

data class ShadowingState(
    val enabled: Boolean = false,
    val target: SentenceRef? = null,
    val sentenceText: String = "",
    val phase: ShadowPhase = ShadowPhase.Idle,
    val hasRecording: Boolean = false
)

/** Record -> recognize -> align -> save state machine. No Android deps, so it is unit-testable. */
class ShadowingController(
    private val scope: CoroutineScope,
    private val engineFactory: () -> SpeechEngine,
    private val saveAttempt: suspend (ShadowAttempt) -> Unit,
    private val addStudyTimeMs: (Long) -> Unit,
    private val now: () -> Long,
    private val isPlaying: () -> Boolean
) {
    private val _state = MutableStateFlow(ShadowingState())
    val state: StateFlow<ShadowingState> = _state.asStateFlow()

    private var engine: SpeechEngine? = null
    private var textId = 0L
    private var pressedAtMs = 0L
    private var lastPcm: ShortArray? = null
    private var job: Job? = null

    fun setEnabled(enabled: Boolean) {
        if (!enabled) { cancel(); engine?.release(); engine = null; lastPcm = null }
        _state.value = if (enabled) _state.value.copy(enabled = true) else ShadowingState()
    }

    fun setTarget(textId: Long, ref: SentenceRef, text: String) {
        cancel()
        this.textId = textId
        lastPcm = null
        _state.value = _state.value.copy(target = ref, sentenceText = text, phase = ShadowPhase.Idle, hasRecording = false)
    }

    /** Returns false (and does nothing) while TTS is playing or a result is being computed. */
    fun pressStart(): Boolean {
        val s = _state.value
        if (!s.enabled || s.target == null || isPlaying()) return false
        if (s.phase == ShadowPhase.Recording || s.phase == ShadowPhase.Recognizing) return false
        PcmPlayer.stop()
        val e = engine ?: engineFactory().also { engine = it }
        pressedAtMs = now()
        runCatching { e.start() }.onFailure {
            _state.value = s.copy(phase = ShadowPhase.Error(ShadowError.ENGINE_FAILED)); return false
        }
        _state.value = s.copy(phase = ShadowPhase.Recording)
        return true
    }

    fun pressEnd() {
        val e = engine ?: return
        if (_state.value.phase != ShadowPhase.Recording) return
        _state.value = _state.value.copy(phase = ShadowPhase.Recognizing)
        job = scope.launch {
            val rec = runCatching { e.stop() }.getOrElse {
                _state.value = _state.value.copy(phase = ShadowPhase.Error(ShadowError.ENGINE_FAILED)); return@launch
            }
            if (rec.durationMs < MIN_RECORDING_MS) {
                _state.value = _state.value.copy(phase = ShadowPhase.Idle); return@launch
            }
            addStudyTimeMs(now() - pressedAtMs)
            lastPcm = rec.pcm
            if (rec.transcript.isBlank()) {
                _state.value = _state.value.copy(phase = ShadowPhase.Error(ShadowError.NOT_HEARD), hasRecording = rec.pcm != null)
                return@launch
            }
            val target = _state.value.target ?: return@launch
            val alignment = TranscriptAligner.align(_state.value.sentenceText, rec.transcript)
            saveAttempt(
                ShadowAttempt(textId = textId, chunkIndex = target.chunkIndex, sentenceIndex = target.sentenceIndex,
                    matched = alignment.matched, total = alignment.total, engine = e.kind.id, timestampMs = now())
            )
            _state.value = _state.value.copy(phase = ShadowPhase.Result(alignment), hasRecording = rec.pcm != null)
        }
    }

    fun retry() {
        cancel()
        lastPcm = null
        _state.value = _state.value.copy(phase = ShadowPhase.Idle, hasRecording = false)
    }

    fun replayMine() { lastPcm?.let { PcmPlayer.play(it) } }

    /** Stops any in-flight recording/recognition without saving. */
    fun cancel() {
        job?.cancel(); job = null
        if (_state.value.phase == ShadowPhase.Recording || _state.value.phase == ShadowPhase.Recognizing) {
            engine?.cancel()
            _state.value = _state.value.copy(phase = ShadowPhase.Idle)
        }
        PcmPlayer.stop()
    }

    fun release() { cancel(); engine?.release(); engine = null }
}
```

`PcmPlayer` is an Android object. `PcmPlayer.stop()` with a null track is a no-op, so the unit tests never touch `AudioTrack`. If the JVM test crashes on the `android.media` stub anyway, add `testOptions { unitTests.returnDefaultValues = true }` to `app/build.gradle`.

- [ ] **Step 5: Wire it into `ReadingViewModel.kt`**

1. Add the pure helpers near `previousSpokenChunkIndex`:

```kotlin
internal fun nextSentenceRef(chunks: List<ChunkState>, ref: SentenceRef): SentenceRef? {
    val chunk = chunks.getOrNull(ref.chunkIndex) ?: return null
    if (ref.sentenceIndex < chunk.sentences.size - 1) return ref.copy(sentenceIndex = ref.sentenceIndex + 1)
    val next = nextSpokenChunkIndex(chunks, ref.chunkIndex + 1) ?: return null
    return SentenceRef(next, 0)
}

internal fun previousSentenceRef(chunks: List<ChunkState>, ref: SentenceRef): SentenceRef? {
    if (ref.sentenceIndex > 0) return ref.copy(sentenceIndex = ref.sentenceIndex - 1)
    val prev = previousSpokenChunkIndex(chunks, ref.chunkIndex - 1) ?: return null
    return SentenceRef(prev, (chunks[prev].sentences.size - 1).coerceAtLeast(0))
}
```

2. Add fields and the controller to the class:

```kotlin
    private val shadowingController = ShadowingController(
        scope = viewModelScope,
        engineFactory = {
            when (ShadowingPrefs.getEngine(app)) {
                SpeechEngineKind.VOSK -> VoskEngine(VoskModelManager(app).modelDir)
                SpeechEngineKind.ANDROID -> AndroidSpeechEngine(app)
            }
        },
        saveAttempt = { db.shadowAttemptDao().insert(it) },
        addStudyTimeMs = { delta ->
            VocabPrefs.addStudyTimeMs(app, java.time.LocalDate.now().toString(), delta)
        },
        now = System::currentTimeMillis,
        isPlaying = { player.isPlaying }
    )
    val shadowing: StateFlow<ShadowingState> = shadowingController.state
    private var shadowStopMessage: PlayerMessage? = null
    // Set when the target chunk had no sentences/player item yet; the ticker retries.
    private var shadowPendingPlay = false
```

3. Add the public API and the stop scheduling:

```kotlin
    fun setShadowing(enabled: Boolean) {
        shadowingController.setEnabled(enabled)
        cancelShadowStop()
        if (!enabled) return
        player.pause()
        val ref = activeSentenceRef() ?: firstSentenceRefFrom(_state.value.currentChunkIndex) ?: return
        setShadowTarget(ref)
    }

    fun shadowPlayOriginal() { shadowing.value.target?.let { playShadowSentence(it) } }

    fun shadowNext() {
        val ref = shadowing.value.target ?: return
        nextSentenceRef(_state.value.chunks, ref)?.let { setShadowTarget(it); playShadowSentence(it) }
    }

    fun shadowPrevious() {
        val ref = shadowing.value.target ?: return
        previousSentenceRef(_state.value.chunks, ref)?.let { setShadowTarget(it); playShadowSentence(it) }
    }

    fun shadowPressStart(): Boolean = shadowingController.pressStart()
    fun shadowPressEnd() = shadowingController.pressEnd()
    fun shadowRetry() = shadowingController.retry()
    fun shadowReplayMine() { player.pause(); shadowingController.replayMine() }

    private fun activeSentenceRef(): SentenceRef? =
        activeSentenceGlobalIndex()?.let { SentenceRef(it.chunkIdx, it.sentenceIdx) }

    private fun firstSentenceRefFrom(chunkIndex: Int): SentenceRef? =
        nextSpokenChunkIndex(_state.value.chunks, chunkIndex)?.let { SentenceRef(it, 0) }

    private fun setShadowTarget(ref: SentenceRef) {
        val doc = _state.value.textDoc ?: return
        val text = _state.value.chunks.getOrNull(ref.chunkIndex)?.sentences?.getOrNull(ref.sentenceIndex)?.text.orEmpty()
        shadowingController.setTarget(doc.id, ref, text)
    }

    private fun playShadowSentence(ref: SentenceRef) {
        shadowingController.cancel()
        val chunk = _state.value.chunks.getOrNull(ref.chunkIndex) ?: return
        val sentence = chunk.sentences.getOrNull(ref.sentenceIndex)
        val itemIndex = chunk.playerItemIndex
        if (sentence == null || itemIndex == null) {
            // Not synthesized yet: jump there; the ticker plays once sentences arrive.
            shadowPendingPlay = true
            jumpToChunk(ref.chunkIndex)
            return
        }
        shadowPendingPlay = false
        cancelShadowStop()
        val endMs = (sentence.offsetMs + sentence.durationMs).toLong()
        shadowStopMessage = player.createMessage { _, _ -> player.pause() }
            .setLooper(android.os.Looper.getMainLooper())
            .setPosition(itemIndex, endMs)
            .setDeleteAfterDelivery(true)
            .send()
        player.seekTo(itemIndex, sentence.offsetMs.toLong())
        player.play()
    }

    private fun cancelShadowStop() { shadowStopMessage?.cancel(); shadowStopMessage = null }
```

4. Route the existing controls through shadowing when it is enabled:
- `togglePlayPause()`: `if (shadowing.value.enabled) { if (player.isPlaying) player.pause() else shadowPlayOriginal(); return }` at the top.
- `nextSentence()` / `previousSentence()`: `if (shadowing.value.enabled) { shadowNext(); return }` / `{ shadowPrevious(); return }` at the top.
- `seekToSentence(chunkIndex, sentence)`: at the top, if shadowing is enabled, run `val idx = _state.value.chunks.getOrNull(chunkIndex)?.sentences?.indexOf(sentence) ?: -1`. If `idx >= 0`, call `setShadowTarget(SentenceRef(chunkIndex, idx)); playShadowSentence(SentenceRef(chunkIndex, idx)); return`.
- In `startPositionTicker()`'s loop, add before `delay(150)`:
  ```kotlin
  if (shadowPendingPlay) shadowing.value.target?.let { ref ->
      val c = _state.value.chunks.getOrNull(ref.chunkIndex)
      if (c?.playerItemIndex != null && c.sentences.isNotEmpty()) { setShadowTarget(ref); playShadowSentence(ref) }
  }
  ```
- In `onCleared()`, before `player.release()`, add `shadowingController.release()`.

Add the imports for `androidx.media3.exoplayer.PlayerMessage` and `com.ziaee.frenchreader.shadowing.*`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all unit tests, including the 6 ref tests and 6 controller tests)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/shadowing/ShadowingController.kt app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt app/src/test/java/com/ziaee/frenchreader/shadowing/ShadowingControllerTest.kt app/src/test/java/com/ziaee/frenchreader/ui/ShadowSentenceRefTest.kt
git commit -m "feat(shadowing): controller state machine and sentence-targeted playback"
```

---

### Task 6: Shadowing panel, toggle, and mic permission on the reading screen

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/ShadowingPanel.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt` (`PlaybackControls` toggle and `bottomBar` wiring)
- Modify: `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`, `values-fa/strings.xml`

**Interfaces:**
- Consumes: `ReadingViewModel.shadowing`, `setShadowing`, `shadowPlayOriginal`, `shadowNext`, `shadowPressStart`, `shadowPressEnd`, `shadowRetry`, `shadowReplayMine` (Task 5); `ShadowPhase`, `ShadowError`, `WordResult`; `ShadowingPrefs`, `VoskModelManager` (Task 3)
- Produces: `@Composable fun ShadowingPanel(state: ShadowingState, isPlaying: Boolean, palette: ReadingPalette, onPlayOriginal: () -> Unit, onReplayMine: () -> Unit, onRetry: () -> Unit, onNext: () -> Unit, onPressStart: () -> Boolean, onPressEnd: () -> Unit)`

- [ ] **Step 1: Add the strings (all three locales)**

`values/strings.xml`:

```xml
    <string name="shadowing_toggle">Shadowing mode</string>
    <string name="shadowing_play_original">Original</string>
    <string name="shadowing_replay_mine">Mine</string>
    <string name="shadowing_retry">Retry</string>
    <string name="shadowing_next">Next</string>
    <string name="shadowing_hold_to_speak">Hold to speak</string>
    <string name="shadowing_listening">Listening…</string>
    <string name="shadowing_recognizing">Checking…</string>
    <string name="shadowing_score">%1$d / %2$d</string>
    <string name="shadowing_not_heard">Didn\'t catch that — try again</string>
    <string name="shadowing_engine_failed">Speech recognition failed — try again</string>
    <string name="shadowing_mic_denied">Shadowing needs the microphone to hear you repeat each sentence.</string>
    <string name="shadowing_model_prompt_title">Download French speech model?</string>
    <string name="shadowing_model_prompt_body">Offline recognition needs a one-time download of about 40 MB. Your voice never leaves the phone.</string>
    <string name="shadowing_model_download">Download</string>
    <string name="shadowing_use_android_engine">Use Android recognizer</string>
    <string name="shadowing_model_downloading">Downloading speech model… %1$d%%</string>
    <string name="shadowing_model_failed">Download failed. Check your connection.</string>
    <string name="shadowing_waiting_for_audio">Preparing audio…</string>
```

`values-fr/strings.xml`:

```xml
    <string name="shadowing_toggle">Mode shadowing</string>
    <string name="shadowing_play_original">Original</string>
    <string name="shadowing_replay_mine">Moi</string>
    <string name="shadowing_retry">Réessayer</string>
    <string name="shadowing_next">Suivant</string>
    <string name="shadowing_hold_to_speak">Maintenir pour parler</string>
    <string name="shadowing_listening">J\'écoute…</string>
    <string name="shadowing_recognizing">Vérification…</string>
    <string name="shadowing_score">%1$d / %2$d</string>
    <string name="shadowing_not_heard">Je n\'ai pas compris — réessayez</string>
    <string name="shadowing_engine_failed">La reconnaissance vocale a échoué — réessayez</string>
    <string name="shadowing_mic_denied">Le shadowing a besoin du micro pour vous entendre répéter chaque phrase.</string>
    <string name="shadowing_model_prompt_title">Télécharger le modèle vocal français ?</string>
    <string name="shadowing_model_prompt_body">La reconnaissance hors ligne nécessite un téléchargement unique d\'environ 40 Mo. Votre voix ne quitte jamais le téléphone.</string>
    <string name="shadowing_model_download">Télécharger</string>
    <string name="shadowing_use_android_engine">Utiliser la reconnaissance Android</string>
    <string name="shadowing_model_downloading">Téléchargement du modèle… %1$d%%</string>
    <string name="shadowing_model_failed">Échec du téléchargement. Vérifiez votre connexion.</string>
    <string name="shadowing_waiting_for_audio">Préparation de l\'audio…</string>
```

`values-fa/strings.xml`:

```xml
    <string name="shadowing_toggle">حالت شدوئینگ</string>
    <string name="shadowing_play_original">اصلی</string>
    <string name="shadowing_replay_mine">صدای من</string>
    <string name="shadowing_retry">دوباره</string>
    <string name="shadowing_next">بعدی</string>
    <string name="shadowing_hold_to_speak">برای صحبت نگه دارید</string>
    <string name="shadowing_listening">در حال گوش دادن…</string>
    <string name="shadowing_recognizing">در حال بررسی…</string>
    <string name="shadowing_score">%1$d / %2$d</string>
    <string name="shadowing_not_heard">متوجه نشدم — دوباره امتحان کنید</string>
    <string name="shadowing_engine_failed">تشخیص گفتار ناموفق بود — دوباره امتحان کنید</string>
    <string name="shadowing_mic_denied">حالت شدوئینگ برای شنیدن تکرار جمله‌ها به میکروفون نیاز دارد.</string>
    <string name="shadowing_model_prompt_title">مدل گفتار فرانسوی دانلود شود؟</string>
    <string name="shadowing_model_prompt_body">تشخیص آفلاین به یک دانلود یک‌باره حدود ۴۰ مگابایت نیاز دارد. صدای شما هرگز از گوشی خارج نمی‌شود.</string>
    <string name="shadowing_model_download">دانلود</string>
    <string name="shadowing_use_android_engine">استفاده از تشخیص‌دهنده اندروید</string>
    <string name="shadowing_model_downloading">در حال دانلود مدل گفتار… %1$d%%</string>
    <string name="shadowing_model_failed">دانلود ناموفق بود. اتصال را بررسی کنید.</string>
    <string name="shadowing_waiting_for_audio">در حال آماده‌سازی صدا…</string>
```

- [ ] **Step 2: Run the localization test**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.LocalizationCompletenessTest'`
Expected: PASS

- [ ] **Step 3: Create `ShadowingPanel.kt`**

```kotlin
package com.ziaee.frenchreader.ui

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.shadowing.ShadowError
import com.ziaee.frenchreader.shadowing.ShadowPhase
import com.ziaee.frenchreader.shadowing.ShadowingState

private val MatchedColor = Color(0xFF2E7D32)
private val MissedColor = Color(0xFFC62828)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShadowingPanel(
    state: ShadowingState,
    isPlaying: Boolean,
    palette: ReadingPalette,
    onPlayOriginal: () -> Unit,
    onReplayMine: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onPressStart: () -> Boolean,
    onPressEnd: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // French sentence always LTR, even in the Persian UI.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val result = state.phase as? ShadowPhase.Result
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (result == null) {
                    Text(state.sentenceText, color = palette.ink, style = MaterialTheme.typography.bodyLarge)
                } else {
                    result.alignment.words.forEach { w ->
                        Text(
                            w.word,
                            color = if (w.matched) MatchedColor else MissedColor,
                            fontWeight = if (w.matched) FontWeight.Normal else FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
        val status = when (val p = state.phase) {
            ShadowPhase.Recording -> stringResource(R.string.shadowing_listening)
            ShadowPhase.Recognizing -> stringResource(R.string.shadowing_recognizing)
            is ShadowPhase.Result -> stringResource(R.string.shadowing_score, p.alignment.matched, p.alignment.total)
            is ShadowPhase.Error -> stringResource(
                if (p.kind == ShadowError.NOT_HEARD) R.string.shadowing_not_heard else R.string.shadowing_engine_failed
            )
            ShadowPhase.Idle -> if (state.sentenceText.isEmpty()) stringResource(R.string.shadowing_waiting_for_audio) else ""
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.titleMedium, color = palette.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onPlayOriginal) {
                Icon(Icons.Default.VolumeUp, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.shadowing_play_original))
            }
            TextButton(onClick = onReplayMine, enabled = state.hasRecording) {
                Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.shadowing_replay_mine))
            }
            TextButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.shadowing_retry))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            val recording = state.phase == ShadowPhase.Recording
            Surface(
                shape = CircleShape,
                color = if (recording) MissedColor else palette.accent.copy(alpha = if (isPlaying) 0.4f else 1f),
                modifier = Modifier.size(72.dp).pointerInput(isPlaying) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val started = onPressStart()
                        waitForUpOrCancellation()
                        if (started) onPressEnd()
                    }
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Mic, stringResource(R.string.shadowing_hold_to_speak), tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onNext) {
                    Text(stringResource(R.string.shadowing_next)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    }
}
```

Add `import androidx.compose.material.icons.automirrored.filled.ArrowForward`. If `Icons.Default.VolumeUp` is deprecated in this BOM, use `Icons.AutoMirrored.Filled.VolumeUp`.

- [ ] **Step 4: Wire the toggle, permission and model prompt into `ReadingScreen.kt`**

1. In `ReadingScreen`, collect the state and set up the permission launcher:

```kotlin
    val shadowState by vm.shadowing.collectAsState()
    val micDenied = stringResource(R.string.shadowing_mic_denied)
    var showModelPrompt by remember { mutableStateOf(false) }
    var modelProgress by remember { mutableStateOf<Float?>(null) }
    val modelManager = remember { VoskModelManager(settingsContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun enableShadowingChecked() {
        if (ShadowingPrefs.getEngine(settingsContext) == SpeechEngineKind.VOSK && !modelManager.isInstalled()) {
            showModelPrompt = true
        } else vm.setShadowing(true)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enableShadowingChecked() else scope.launch { snackbar.showSnackbar(micDenied) }
    }
    val onToggleShadowing: () -> Unit = {
        when {
            shadowState.enabled -> vm.setShadowing(false)
            ContextCompat.checkSelfPermission(settingsContext, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> enableShadowingChecked()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
```

If the `Scaffold` already has a `snackbarHost`, reuse its `SnackbarHostState` instead of creating a second one. Otherwise add `snackbarHost = { SnackbarHost(snackbar) }` to the Scaffold.

2. Model prompt dialog. Place it next to the other dialogs in `ReadingScreen`:

```kotlin
    val modelFailed = stringResource(R.string.shadowing_model_failed)
    if (showModelPrompt) {
        AlertDialog(
            onDismissRequest = { if (modelProgress == null) showModelPrompt = false },
            title = { Text(stringResource(R.string.shadowing_model_prompt_title)) },
            text = {
                val p = modelProgress
                if (p == null) Text(stringResource(R.string.shadowing_model_prompt_body))
                else Column {
                    Text(stringResource(R.string.shadowing_model_downloading, (p * 100).toInt()))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = modelProgress == null, onClick = {
                    modelProgress = 0f
                    scope.launch {
                        val ok = runCatching { modelManager.download { modelProgress = it } }.isSuccess
                        modelProgress = null
                        showModelPrompt = false
                        if (ok) vm.setShadowing(true) else snackbar.showSnackbar(modelFailed)
                    }
                }) { Text(stringResource(R.string.shadowing_model_download)) }
            },
            dismissButton = {
                if (AndroidSpeechEngine.isAvailable(settingsContext)) TextButton(enabled = modelProgress == null, onClick = {
                    ShadowingPrefs.setEngine(settingsContext, SpeechEngineKind.ANDROID)
                    showModelPrompt = false
                    vm.setShadowing(true)
                }) { Text(stringResource(R.string.shadowing_use_android_engine)) }
            }
        )
    }
```

`modelManager.download`'s progress callback runs on `Dispatchers.IO`. Writing Compose state from a background thread is allowed, but if lint complains, wrap it in `withContext(Dispatchers.Main)`.

3. Change `bottomBar = { PlaybackControls(vm, state, palette) }` to:

```kotlin
            bottomBar = {
                Column {
                    if (shadowState.enabled) {
                        Surface(color = palette.background, tonalElevation = FrenchReaderDesign.elevations.raised) {
                            ShadowingPanel(
                                state = shadowState, isPlaying = state.isPlaying, palette = palette,
                                onPlayOriginal = vm::shadowPlayOriginal, onReplayMine = vm::shadowReplayMine,
                                onRetry = vm::shadowRetry, onNext = vm::shadowNext,
                                onPressStart = vm::shadowPressStart, onPressEnd = vm::shadowPressEnd
                            )
                        }
                    }
                    PlaybackControls(vm, state, palette, shadowState.enabled, onToggleShadowing)
                }
            }
```

4. `PlaybackControls` signature becomes `(vm, state, palette, shadowingEnabled: Boolean, onToggleShadowing: () -> Unit)`. Add this before the speed `Box`:

```kotlin
                IconButton(onClick = onToggleShadowing, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = stringResource(R.string.shadowing_toggle),
                        modifier = Modifier.size(22.dp),
                        tint = if (shadowingEnabled) palette.accent else palette.ink.copy(alpha = 0.75f)
                    )
                }
```

5. Add these imports: `android.Manifest`, `android.content.pm.PackageManager`, `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.core.content.ContextCompat`, `com.ziaee.frenchreader.shadowing.*`, `kotlinx.coroutines.launch`.

- [ ] **Step 5: Build and run the unit tests**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ShadowingPanel.kt app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml app/src/main/res/values-fa/strings.xml
git commit -m "feat(shadowing): reading-screen panel, toggle, mic permission and model prompt"
```

---

### Task 7: Settings tab, Statistics card, privacy policy

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt` (new tab)
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiState.kt` (summary type and pure function)
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt` (load and section)
- Modify: `PRIVACY.md`
- Modify: the three `strings.xml`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/statistics/ShadowingSummaryTest.kt`

**Interfaces:**
- Consumes: `ShadowAttemptDao.getSince`, `countAll`, `distinctActiveDates` (Task 2); `ShadowingPrefs`, `SpeechEngineKind`, `VoskModelManager` (Task 3); `AndroidSpeechEngine.isAvailable` (Task 4)
- Produces:
  - `data class ShadowingSummary(val sentencesPracticed: Int, val averageAccuracyPercent: Int, val last7DaysPercent: List<Int>)`
  - `internal fun shadowingSummary(totalCount: Int, recent: List<ShadowAttempt>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ShadowingSummary`
  - `StatisticsUiState.shadowing: ShadowingSummary`

- [ ] **Step 1: Write the failing summary test**

```kotlin
package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.ShadowAttempt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ShadowingSummaryTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 23)
    private fun at(d: LocalDate, matched: Int, total: Int) = ShadowAttempt(
        textId = 1, chunkIndex = 0, sentenceIndex = 0, matched = matched, total = total, engine = "vosk",
        timestampMs = d.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
    )

    @Test fun averagesByWordsAndBucketsByDay() {
        val s = shadowingSummary(
            totalCount = 12,
            recent = listOf(at(today, 3, 4), at(today, 1, 4), at(today.minusDays(6), 5, 5)),
            today = today, zone = zone
        )
        assertEquals(12, s.sentencesPracticed)
        assertEquals(69, s.averageAccuracyPercent) // (3+1+5)/(4+4+5) = 9/13
        assertEquals(listOf(100, 0, 0, 0, 0, 0, 50), s.last7DaysPercent)
    }

    @Test fun emptyIsZeros() {
        val s = shadowingSummary(0, emptyList(), today, zone)
        assertEquals(0, s.averageAccuracyPercent)
        assertEquals(List(7) { 0 }, s.last7DaysPercent)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.statistics.ShadowingSummaryTest'`
Expected: compilation FAIL, `Unresolved reference: shadowingSummary`

- [ ] **Step 3: Implement the summary**

In `StatisticsUiState.kt`:

```kotlin
data class ShadowingSummary(
    val sentencesPracticed: Int = 0,
    val averageAccuracyPercent: Int = 0,
    val last7DaysPercent: List<Int> = List(7) { 0 }
)

/** [recent] = attempts from the last 7 days; accuracy is word-weighted (sum matched / sum total). */
internal fun shadowingSummary(
    totalCount: Int,
    recent: List<ShadowAttempt>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): ShadowingSummary {
    fun pct(list: List<ShadowAttempt>): Int {
        val total = list.sumOf { it.total }
        return if (total == 0) 0 else Math.round(list.sumOf { it.matched } * 100f / total)
    }
    val byDay = recent.groupBy { Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate() }
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    return ShadowingSummary(totalCount, pct(recent), days.map { pct(byDay[it].orEmpty()) })
}
```

Add `val shadowing: ShadowingSummary = ShadowingSummary()` to `StatisticsUiState`. Add the imports `com.ziaee.frenchreader.data.ShadowAttempt` and `java.time.Instant`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests 'com.ziaee.frenchreader.ui.statistics.ShadowingSummaryTest'`
Expected: PASS

- [ ] **Step 5: Load it and render the card**

In `StatisticsViewModel.load()`, after `uiState = composeStatisticsState(...)`:

```kotlin
            val shadowRecent = db.shadowAttemptDao().getSince(today.minusDays(6).startOfDayMs())
            uiState = uiState.copy(shadowing = shadowingSummary(db.shadowAttemptDao().countAll(), shadowRecent, today))
```

Shadowing days also count toward the streak. Extend `loadActiveDates`'s `activityLogDates` lambda to:
`{ db.activityLogDao().activeDates() + db.shadowAttemptDao().distinctActiveDates() }`

In `StatisticsScreen`, add `ShadowingSection(state.shadowing)` after `WeeklyListeningSection(...)`:

```kotlin
@Composable
private fun ShadowingSection(summary: ShadowingSummary) {
    if (summary.sentencesPracticed == 0) return
    Text(stringResource(R.string.statistics_shadowing_title), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp))
    Text(
        stringResource(R.string.statistics_shadowing_summary, summary.sentencesPracticed, summary.averageAccuracyPercent),
        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)
    )
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    BarChart(
        values = summary.last7DaysPercent.map { it.toFloat() },
        labels = (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, locale) },
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
        maxValue = 100f
    )
}
```

Strings (all three locales):

```xml
<!-- values -->
    <string name="statistics_shadowing_title">Shadowing</string>
    <string name="statistics_shadowing_summary">%1$d sentences practiced · %2$d%% average accuracy</string>
<!-- values-fr -->
    <string name="statistics_shadowing_title">Shadowing</string>
    <string name="statistics_shadowing_summary">%1$d phrases pratiquées · %2$d %% de précision moyenne</string>
<!-- values-fa -->
    <string name="statistics_shadowing_title">شدوئینگ</string>
    <string name="statistics_shadowing_summary">%1$d جمله تمرین شده · میانگین دقت %2$d%%</string>
```

- [ ] **Step 6: Add the Settings tab**

Add `R.string.settings_section_shadowing` to `tabTitles`, after `settings_section_xtts`. The later tab indices shift by one, so update the `when` branches to match. Then add the branch `4 -> ShadowingSettings(context, scope, snackbarHostState)` and this composable:

```kotlin
@Composable
private fun ShadowingSettings(context: Context, scope: CoroutineScope, snackbarHostState: SnackbarHostState) {
    var engine by remember { mutableStateOf(ShadowingPrefs.getEngine(context)) }
    val manager = remember { VoskModelManager(context) }
    var installed by remember { mutableStateOf(manager.isInstalled()) }
    var progress by remember { mutableStateOf<Float?>(null) }
    val androidAvailable = remember { AndroidSpeechEngine.isAvailable(context) }
    val failed = stringResource(R.string.shadowing_model_failed)

    CompactSettingLabel(R.string.shadowing_engine_title)
    val engines = if (androidAvailable) SpeechEngineKind.entries else listOf(SpeechEngineKind.VOSK)
    CompactChoiceRow(engines, engine, {
        stringResource(if (it == SpeechEngineKind.VOSK) R.string.shadowing_engine_vosk else R.string.shadowing_engine_android)
    }) { kind -> engine = kind; ShadowingPrefs.setEngine(context, kind) }

    CompactSettingLabel(R.string.shadowing_model_title)
    val p = progress
    when {
        p != null -> {
            Text(stringResource(R.string.shadowing_model_downloading, (p * 100).toInt()), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        }
        installed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.shadowing_model_installed, manager.sizeBytes() / (1024 * 1024)),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { manager.delete(); installed = false }) { Text(stringResource(R.string.shadowing_model_delete)) }
        }
        else -> OutlinedButton(onClick = {
            progress = 0f
            scope.launch {
                val ok = runCatching { manager.download { progress = it } }.isSuccess
                progress = null
                installed = manager.isInstalled()
                if (!ok) snackbarHostState.showSnackbar(failed)
            }
        }) { Text(stringResource(R.string.shadowing_model_download)) }
    }
}
```

Strings (all three locales):

```xml
<!-- values -->
    <string name="settings_section_shadowing">Shadowing</string>
    <string name="shadowing_engine_title">Speech recognition</string>
    <string name="shadowing_engine_vosk">Offline (Vosk)</string>
    <string name="shadowing_engine_android">Android</string>
    <string name="shadowing_model_title">French speech model</string>
    <string name="shadowing_model_installed">Installed · %1$d MB</string>
    <string name="shadowing_model_delete">Delete</string>
<!-- values-fr -->
    <string name="settings_section_shadowing">Shadowing</string>
    <string name="shadowing_engine_title">Reconnaissance vocale</string>
    <string name="shadowing_engine_vosk">Hors ligne (Vosk)</string>
    <string name="shadowing_engine_android">Android</string>
    <string name="shadowing_model_title">Modèle vocal français</string>
    <string name="shadowing_model_installed">Installé · %1$d Mo</string>
    <string name="shadowing_model_delete">Supprimer</string>
<!-- values-fa -->
    <string name="settings_section_shadowing">شدوئینگ</string>
    <string name="shadowing_engine_title">تشخیص گفتار</string>
    <string name="shadowing_engine_vosk">آفلاین (Vosk)</string>
    <string name="shadowing_engine_android">اندروید</string>
    <string name="shadowing_model_title">مدل گفتار فرانسوی</string>
    <string name="shadowing_model_installed">نصب شده · %1$d مگابایت</string>
    <string name="shadowing_model_delete">حذف</string>
```

- [ ] **Step 7: Update `PRIVACY.md`**

Under "What the app stores", change the third bullet to:
`- highlights, listening and study time, shadowing scores (per sentence, never your voice), and your settings`

Add rows to the "When the app goes online" table:

```markdown
| Shadowing, offline engine (default) | alphacephei.com, once, to download the French speech model | A normal download request. Your voice is processed on the phone and never sent anywhere |
| Shadowing, Android engine (optional) | Your phone's speech recognition service (usually Google) | The sentence you speak, unless offline French recognition is installed on the phone |
```

Add under "Permissions":
`- **Microphone:** only while you hold the mic button in shadowing mode. Recordings stay in memory and are discarded when you move to the next sentence; they are never saved.`

- [ ] **Step 8: Run all the unit tests and the build**

Run: `./gradlew testDebugUnitTest assembleDebug`
Expected: PASS and BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt app/src/main/java/com/ziaee/frenchreader/ui/statistics/ app/src/test/java/com/ziaee/frenchreader/ui/statistics/ShadowingSummaryTest.kt app/src/main/res/values*/strings.xml PRIVACY.md
git commit -m "feat(shadowing): settings tab, statistics card and privacy policy"
```

---

### Task 8: Release build and on-device verification

**Files:** none (verification only; fix anything found in the owning task's files)

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew testDebugUnitTest`. If a device is connected, also run `./gradlew connectedDebugAndroidTest`.
Expected: all PASS. Report instrumented tests as skipped if there is no device.

- [ ] **Step 2: Build and install the signed release**

Run: `./gradlew assembleRelease && adb install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`. The install keeps existing data, and the migration 14→15 runs on first launch.

- [ ] **Step 3: Manual checklist on the phone**

1. Open a text and tap the 🎤 toggle. The permission prompt appears. Deny it: a snackbar explains why, and shadowing stays off.
2. Toggle again and allow. The model prompt appears. Download it and watch the progress. Shadowing turns on and the panel shows the current sentence.
3. Tap Original. Exactly one sentence plays and then it pauses.
4. Hold the mic, repeat the sentence, and release. The words turn green or red and a score shows. Tap Mine: your voice plays back.
5. Tap Next. The **following** sentence plays (not one after it). Repeat this 5 times across a paragraph boundary.
6. Tap a sentence in the text. It becomes the target and plays.
7. Mumble or stay silent: "Didn't catch that". Quick tap on the mic: nothing happens.
8. Leave the screen while holding the mic. The app doesn't crash, and the mic indicator turns off.
9. In Settings → Shadowing, switch to Android and repeat step 4. Delete the model and check that it shows as not installed.
10. In Statistics, the Shadowing card shows your sentence count and accuracy, and the streak counts today.
11. Switch the app language to Persian. The panel sentence stays left-to-right and the labels are Persian.
12. Turn on airplane mode with the Vosk engine: step 4 still works.

- [ ] **Step 4: Commit any fixes**

For each fix, commit using the message style of the task it belongs to. Don't bump the version in this plan: releases follow the separate release workflow.
