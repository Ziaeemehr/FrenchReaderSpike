# Statistics Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Statistics screen showing listening streak, weekly listening time, vocab review accuracy/volume, Leitner box distribution, and text/word totals.

**Architecture:** Two new Room tables (`review_log`, `activity_log`) capture history the app currently discards. A pure, dependency-free calculation module (`StatisticsUiState.kt`, mirroring the existing `HomeUiState.kt` pattern) derives streak/accuracy/completion from that history and is unit-testable without Android. A thin `StatisticsViewModel` loads raw rows via DAOs and hands them to that pure module. Two `Canvas`-based bar charts (no charting library) render weekly listening time and Leitner box distribution.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room, `java.time` (LocalDate — safe on `minSdk 26`, natively available, no desugaring needed), JUnit4 (JVM unit tests), AndroidJUnit4 + `androidx.room:room-testing` (instrumented tests).

**Spec:** [`docs/superpowers/specs/2026-09-16-statistics-screen-design.md`](../specs/2026-09-16-statistics-screen-design.md)

## Global Constraints

- `minSdk 26`, `compileSdk 34`, Kotlin/Java target 17 — already configured, nothing to change.
- No new Gradle dependencies. Charts use Compose's own `Canvas`; icons come from the already-included `androidx.compose.material:material-icons-extended`.
- Every new user-visible string goes through `stringResource(...)` and must be added to **all three** locale files (`values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml`) with the same key — `LocalizationCompletenessTest` (`app/src/test/java/com/ziaee/frenchreader/LocalizationCompletenessTest.kt`) fails the build otherwise, and it also fails on any hardcoded Persian literal inside a `Text(...)`/`contentDescription =`/`showSnackbar(...)` call in Kotlin source.
- New Room migrations follow the existing raw-SQL style in `data/AppDatabase.kt` (`MIGRATION_2_3` .. `MIGRATION_4_5`) — `CREATE TABLE`/`ALTER TABLE` strings, no `ON CONFLICT DO UPDATE` upsert syntax (not reliably available on the bundled SQLite across this app's API range).
- Instrumented (`androidTest`) tests require a connected device or running emulator — same requirement the existing `Migration4To5Test` already has.
- Accuracy is a single all-time cumulative percentage, not per-window (locked in during brainstorming — avoids near-duplicate metrics).
- "Texts completed" is computed on the fly with `TextChunker.chunk(rawText)`, never a stored column.
- Streak = "any activity" per day (listening OR vocab review), not vocab-only or listening-only.

---

### Task 1: Statistics data layer — entities, migration, DAOs

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Entities.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Daos.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/data/Migration5To6Test.kt`
- Test: `app/src/androidTest/java/com/ziaee/frenchreader/data/ActivityLogDaoTest.kt`

**Interfaces:**
- Produces: `ReviewLogEntry(id, entryId, timestampMs, knew, boxBefore, boxAfter)`, `ActivityLogEntry(date, listeningMs)`, `AppDatabase.reviewLogDao(): ReviewLogDao`, `AppDatabase.activityLogDao(): ActivityLogDao`, `TextDao.getAllOnce(): List<TextDocument>`. Task 2 uses the entity shapes; Tasks 3–5 use the DAO methods below.

- [ ] **Step 1: Write the failing migration test**

Create `app/src/androidTest/java/com/ziaee/frenchreader/data/Migration5To6Test.kt`:

```kotlin
package com.ziaee.frenchreader.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    private val databaseName = "migration-5-6-test.db"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `texts` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`rawText` TEXT NOT NULL, " +
                            "`createdAtMs` INTEGER NOT NULL, " +
                            "`lastChunkIndex` INTEGER NOT NULL, " +
                            "`lastPositionMs` INTEGER NOT NULL, " +
                            "`voice` TEXT NOT NULL, " +
                            "`ratePercent` INTEGER NOT NULL, " +
                            "`translationLang` TEXT NOT NULL, " +
                            "`sourceUrl` TEXT, " +
                            "`sourceName` TEXT, " +
                            "`author` TEXT, " +
                            "`license` TEXT, " +
                            "`publishedAt` INTEGER, " +
                            "`imagePath` TEXT, " +
                            "`externalKey` TEXT, " +
                            "`lastAccessedAtMs` INTEGER NOT NULL DEFAULT 0)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_texts_externalKey` " +
                            "ON `texts` (`externalKey`)"
                    )
                    db.execSQL(
                        "CREATE TABLE `vocab` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`word` TEXT NOT NULL, " +
                            "`sentence` TEXT NOT NULL, " +
                            "`textId` INTEGER NOT NULL, " +
                            "`dictionaryUrl` TEXT NOT NULL, " +
                            "`meaning` TEXT, " +
                            "`learned` INTEGER NOT NULL, " +
                            "`createdAtMs` INTEGER NOT NULL, " +
                            "`listId` INTEGER, " +
                            "`leitnerBox` INTEGER NOT NULL, " +
                            "`nextReviewAtMs` INTEGER NOT NULL, " +
                            "`lastReviewedAtMs` INTEGER)"
                    )
                    db.execSQL(
                        "CREATE TABLE `vocab_lists` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`createdAtMs` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE `headlines` (" +
                            "`sourceId` TEXT NOT NULL, " +
                            "`sourceLabel` TEXT NOT NULL, " +
                            "`externalId` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`snippet` TEXT NOT NULL, " +
                            "`articleUrl` TEXT NOT NULL, " +
                            "`imageUrl` TEXT, " +
                            "`publishedAtMs` INTEGER, " +
                            "`cachedAtMs` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`sourceId`, `externalId`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_headlines_publishedAtMs` " +
                            "ON `headlines` (`publishedAtMs`)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_headlines_articleUrl` " +
                            "ON `headlines` (`articleUrl`)"
                    )
                    db.execSQL(
                        "INSERT INTO texts (title, rawText, createdAtMs, lastChunkIndex, " +
                            "lastPositionMs, voice, ratePercent, translationLang, lastAccessedAtMs) " +
                            "VALUES ('Existing text', 'Existing body', 1, 2, 3, " +
                            "'fr-FR-DeniseNeural', 4, 'fa', 0)"
                    )
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
    )

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationCreatesEmptyReviewLogAndActivityLogTables() {
        val db = helper.writableDatabase

        MIGRATION_5_6.migrate(db)

        db.query("SELECT title FROM texts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Existing text", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM review_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM activity_log").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        db.version = 6
        helper.close()
        val roomDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        roomDatabase.openHelper.writableDatabase
        roomDatabase.close()
    }

    @Test
    fun migrationCreatesTimestampIndexOnReviewLog() {
        val db = helper.writableDatabase

        MIGRATION_5_6.migrate(db)

        var foundIndex = false
        db.query("PRAGMA index_list(`review_log`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "index_review_log_timestampMs") foundIndex = true
            }
        }
        assertTrue(foundIndex)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.data.Migration5To6Test` (needs a connected device/emulator)
Expected: FAILS to compile — `MIGRATION_5_6` is unresolved (doesn't exist yet).

- [ ] **Step 3: Add the new entities**

In `app/src/main/java/com/ziaee/frenchreader/data/Entities.kt`, add at the end of the file (after the `VocabList` data class):

```kotlin
/** One row per vocab-review answer, feeding the Statistics screen's
 * accuracy and streak calculations. Written once per [VocabEntry] answer,
 * never updated or deleted. */
@Entity(tableName = "review_log", indices = [Index("timestampMs")])
data class ReviewLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val timestampMs: Long,
    val knew: Boolean,
    val boxBefore: Int,
    val boxAfter: Int
)

/** One row per calendar day with any listening activity, accumulated from
 * [ReadingViewModel][com.ziaee.frenchreader.ui.ReadingViewModel]'s position
 * ticker. [date] is `LocalDate.toString()` (`yyyy-MM-dd`). */
@Entity(tableName = "activity_log")
data class ActivityLogEntry(
    @PrimaryKey val date: String,
    val listeningMs: Long
)
```

- [ ] **Step 4: Add the migration, bump the database version, register the entities/DAOs**

In `app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt`, add after `MIGRATION_4_5`:

```kotlin
// v5 -> v6: adds review_log (one row per vocab-review answer) and
// activity_log (one row per day with listening time) for the Statistics
// screen (ROADMAP.md section 3). Both start empty -- existing texts/vocab
// are untouched, so the Statistics screen just starts at zero history
// for anyone upgrading.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entryId` INTEGER NOT NULL, " +
                "`timestampMs` INTEGER NOT NULL, " +
                "`knew` INTEGER NOT NULL, " +
                "`boxBefore` INTEGER NOT NULL, " +
                "`boxAfter` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_review_log_timestampMs` " +
                "ON `review_log` (`timestampMs`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity_log` (" +
                "`date` TEXT NOT NULL PRIMARY KEY, " +
                "`listeningMs` INTEGER NOT NULL)"
        )
    }
}
```

Then change the `@Database` annotation and companion object:

```kotlin
@Database(
    entities = [
        TextDocument::class, HeadlineEntity::class, VocabEntry::class, VocabList::class,
        ReviewLogEntry::class, ActivityLogEntry::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun textDao(): TextDao
    abstract fun headlineDao(): HeadlineDao
    abstract fun vocabDao(): VocabDao
    abstract fun vocabListDao(): VocabListDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "french_reader.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
```

- [ ] **Step 5: Add the new DAOs and the `TextDao.getAllOnce()` helper**

In `app/src/main/java/com/ziaee/frenchreader/data/Daos.kt`, add inside `TextDao` (alongside the existing `getById`):

```kotlin
    // One-shot snapshot for the Statistics screen -- same reasoning as
    // VocabDao.getAllOnce(): a stable list to compute totals/completion
    // from, not a live Flow that would recompute mid-calculation.
    @Query("SELECT * FROM texts")
    suspend fun getAllOnce(): List<TextDocument>
```

Then add two new DAO interfaces at the end of the file:

```kotlin
@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(entry: ReviewLogEntry)

    @Query("SELECT COUNT(*) FROM review_log WHERE timestampMs >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    @Query("SELECT COUNT(*) FROM review_log WHERE knew = 1")
    suspend fun countKnew(): Int

    @Query("SELECT COUNT(*) FROM review_log")
    suspend fun countTotal(): Int

    @Query("SELECT DISTINCT date(timestampMs / 1000, 'unixepoch', 'localtime') FROM review_log")
    suspend fun distinctActiveDates(): List<String>
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_log WHERE date = :date")
    suspend fun getForDate(date: String): ActivityLogEntry?

    @Insert
    suspend fun insert(entry: ActivityLogEntry)

    @Update
    suspend fun update(entry: ActivityLogEntry)

    // Read-modify-write instead of an SQL upsert -- see Global Constraints.
    @Transaction
    suspend fun addListening(date: String, deltaMs: Long) {
        val existing = getForDate(date)
        if (existing == null) {
            insert(ActivityLogEntry(date, deltaMs))
        } else {
            update(existing.copy(listeningMs = existing.listeningMs + deltaMs))
        }
    }

    @Query("SELECT * FROM activity_log WHERE date IN (:dates)")
    suspend fun getForDates(dates: List<String>): List<ActivityLogEntry>

    @Query("SELECT date FROM activity_log WHERE listeningMs > 0")
    suspend fun activeDates(): List<String>
}
```

- [ ] **Step 6: Run the migration test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.data.Migration5To6Test`
Expected: PASS (both test methods).

- [ ] **Step 7: Write the DAO upsert test**

Create `app/src/androidTest/java/com/ziaee/frenchreader/data/ActivityLogDaoTest.kt`:

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

@RunWith(AndroidJUnit4::class)
class ActivityLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ActivityLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.activityLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addListening_createsRowWhenNoneExists() = runBlocking {
        dao.addListening("2026-09-16", 5000L)

        assertEquals(5000L, dao.getForDate("2026-09-16")?.listeningMs)
    }

    @Test
    fun addListening_accumulatesAcrossCalls() = runBlocking {
        dao.addListening("2026-09-16", 5000L)
        dao.addListening("2026-09-16", 3000L)

        assertEquals(8000L, dao.getForDate("2026-09-16")?.listeningMs)
    }

    @Test
    fun addListening_keepsSeparateDatesIndependent() = runBlocking {
        dao.addListening("2026-09-16", 5000L)
        dao.addListening("2026-09-17", 2000L)

        assertEquals(5000L, dao.getForDate("2026-09-16")?.listeningMs)
        assertEquals(2000L, dao.getForDate("2026-09-17")?.listeningMs)
    }
}
```

- [ ] **Step 8: Run the DAO test**

`ActivityLogDao.addListening` was already implemented in Step 5, so this test is confirming that implementation rather than following a fail-first cycle.

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ziaee.frenchreader.data.ActivityLogDaoTest`
Expected: PASS on all three test methods.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/data/Entities.kt \
        app/src/main/java/com/ziaee/frenchreader/data/AppDatabase.kt \
        app/src/main/java/com/ziaee/frenchreader/data/Daos.kt \
        app/src/androidTest/java/com/ziaee/frenchreader/data/Migration5To6Test.kt \
        app/src/androidTest/java/com/ziaee/frenchreader/data/ActivityLogDaoTest.kt
git commit -m "Add review_log/activity_log tables for the Statistics screen"
```

---

### Task 2: Pure statistics calculations and state

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiState.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiStateTest.kt`

**Interfaces:**
- Consumes: `TextDocument` (`data/Entities.kt`), `VocabEntry` (`data/Entities.kt`), `TextChunker.chunk(rawText: String): List<String>` (`text/TextChunker.kt`).
- Produces: `data class DailyListening(date: LocalDate, listeningMs: Long)`, `data class StatisticsUiState(streakDays, weeklyListening, reviewedToday, reviewedThisWeek, accuracyPercent, leitnerBoxCounts, textsSaved, textsCompleted, wordsSaved)`, `fun last7Days(today: LocalDate): List<LocalDate>`, `fun composeStatisticsState(texts, vocabEntries, reviewedToday, reviewedThisWeek, knewCount, totalReviewCount, weeklyListening, activeDates, today): StatisticsUiState`. Task 5's `StatisticsViewModel` calls `last7Days` and `composeStatisticsState`.

This is pure Kotlin with no Android runtime dependency (mirrors `ui/home/HomeUiState.kt`'s `composeHomeState`), so it's fully covered by a fast JVM unit test — write that test first.

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiStateTest.kt`:

```kotlin
package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatisticsUiStateTest {

    @Test
    fun `isTextCompleted is true when last chunk index reached the final chunk`() {
        val doc = TextDocument(
            id = 1,
            title = "t",
            rawText = "Paragraphe un.\n\nParagraphe deux.\n\nParagraphe trois.",
            lastChunkIndex = 2
        )
        assertEquals(true, isTextCompleted(doc))
    }

    @Test
    fun `isTextCompleted is false when reading stopped before the last chunk`() {
        val doc = TextDocument(
            id = 1,
            title = "t",
            rawText = "Paragraphe un.\n\nParagraphe deux.\n\nParagraphe trois.",
            lastChunkIndex = 0
        )
        assertEquals(false, isTextCompleted(doc))
    }

    @Test
    fun `leitnerBoxCounts fills every box from 1 to 5 even when empty`() {
        val entries = listOf(vocabEntry(1), vocabEntry(1), vocabEntry(3))

        val counts = leitnerBoxCounts(entries)

        assertEquals(mapOf(1 to 2, 2 to 0, 3 to 1, 4 to 0, 5 to 0), counts)
    }

    @Test
    fun `computeAccuracyPercent rounds down and handles zero reviews`() {
        assertEquals(0, computeAccuracyPercent(0, 0))
        assertEquals(66, computeAccuracyPercent(2, 3))
        assertEquals(100, computeAccuracyPercent(3, 3))
    }

    @Test
    fun `last7Days returns 7 consecutive days ending today`() {
        val today = LocalDate.of(2026, 9, 16)

        val days = last7Days(today)

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 10), days.first())
        assertEquals(today, days.last())
    }

    @Test
    fun `computeStreak counts consecutive active days ending today`() {
        val today = LocalDate.of(2026, 9, 16)
        val active = setOf(today, today.minusDays(1), today.minusDays(2))

        assertEquals(3, computeStreak(active, today))
    }

    @Test
    fun `computeStreak still counts yesterday if today has no activity yet`() {
        val today = LocalDate.of(2026, 9, 16)
        val active = setOf(today.minusDays(1), today.minusDays(2))

        assertEquals(2, computeStreak(active, today))
    }

    @Test
    fun `computeStreak resets to zero after a gap`() {
        val today = LocalDate.of(2026, 9, 16)
        val active = setOf(today.minusDays(3))

        assertEquals(0, computeStreak(active, today))
    }

    @Test
    fun `composeStatisticsState combines all derived metrics`() {
        val texts = listOf(
            TextDocument(id = 1, title = "a", rawText = "Un seul paragraphe.", lastChunkIndex = 0),
            TextDocument(id = 2, title = "b", rawText = "P1.\n\nP2.", lastChunkIndex = 0)
        )
        val vocab = listOf(vocabEntry(1), vocabEntry(5))
        val today = LocalDate.of(2026, 9, 16)

        val state = composeStatisticsState(
            texts = texts,
            vocabEntries = vocab,
            reviewedToday = 4,
            reviewedThisWeek = 10,
            knewCount = 8,
            totalReviewCount = 10,
            weeklyListening = last7Days(today).map { DailyListening(it, 0L) },
            activeDates = setOf(today),
            today = today
        )

        assertEquals(1, state.streakDays)
        assertEquals(4, state.reviewedToday)
        assertEquals(10, state.reviewedThisWeek)
        assertEquals(80, state.accuracyPercent)
        assertEquals(2, state.textsSaved)
        assertEquals(1, state.textsCompleted)
        assertEquals(2, state.wordsSaved)
        assertEquals(7, state.weeklyListening.size)
    }

    private fun vocabEntry(leitnerBox: Int) = VocabEntry(
        word = "mot",
        sentence = "Une phrase.",
        textId = 1,
        dictionaryUrl = "https://example.test",
        leitnerBox = leitnerBox
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.ui.statistics.StatisticsUiStateTest"`
Expected: FAILS to compile — none of `isTextCompleted`, `leitnerBoxCounts`, `computeAccuracyPercent`, `last7Days`, `computeStreak`, `composeStatisticsState`, `StatisticsUiState`, `DailyListening` exist yet.

- [ ] **Step 3: Implement the calculations and state**

Create `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiState.kt`:

```kotlin
package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.text.TextChunker
import java.time.LocalDate

internal const val LEITNER_BOX_COUNT = 5

/** One day's listening time, always present for all 7 days in the chart
 * even when [listeningMs] is 0 -- so the chart never has a missing bar. */
data class DailyListening(val date: LocalDate, val listeningMs: Long)

data class StatisticsUiState(
    val streakDays: Int = 0,
    val weeklyListening: List<DailyListening> = emptyList(),
    val reviewedToday: Int = 0,
    val reviewedThisWeek: Int = 0,
    val accuracyPercent: Int = 0,
    val leitnerBoxCounts: Map<Int, Int> = emptyMap(),
    val textsSaved: Int = 0,
    val textsCompleted: Int = 0,
    val wordsSaved: Int = 0
)

/** A text is "completed" once its saved reading position has reached the
 * last chunk at least once. Recomputed from [TextChunker] (pure, cheap)
 * rather than a stored column, so it can never drift out of sync with
 * [TextDocument.rawText]. */
internal fun isTextCompleted(doc: TextDocument): Boolean {
    val chunkCount = TextChunker.chunk(doc.rawText).size
    return chunkCount > 0 && doc.lastChunkIndex >= chunkCount - 1
}

internal fun leitnerBoxCounts(entries: List<VocabEntry>): Map<Int, Int> {
    val counts = entries.groupingBy { it.leitnerBox }.eachCount()
    return (1..LEITNER_BOX_COUNT).associateWith { box -> counts[box] ?: 0 }
}

internal fun computeAccuracyPercent(knewCount: Int, totalCount: Int): Int =
    if (totalCount == 0) 0 else ((knewCount * 100L) / totalCount).toInt()

internal fun last7Days(today: LocalDate): List<LocalDate> =
    (6 downTo 0).map { today.minusDays(it.toLong()) }

/** "Any activity" streak: a day counts if it's in [activeDates] (listening
 * OR a vocab review happened that day). Falls back to checking yesterday
 * if today has no activity logged yet, so the streak doesn't drop to zero
 * first thing in the morning before the user has done anything today. */
internal fun computeStreak(activeDates: Set<LocalDate>, today: LocalDate): Int {
    val start = if (today in activeDates) today else today.minusDays(1)
    if (start !in activeDates) return 0
    var streak = 0
    var day = start
    while (day in activeDates) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

internal fun composeStatisticsState(
    texts: List<TextDocument>,
    vocabEntries: List<VocabEntry>,
    reviewedToday: Int,
    reviewedThisWeek: Int,
    knewCount: Int,
    totalReviewCount: Int,
    weeklyListening: List<DailyListening>,
    activeDates: Set<LocalDate>,
    today: LocalDate
): StatisticsUiState = StatisticsUiState(
    streakDays = computeStreak(activeDates, today),
    weeklyListening = weeklyListening,
    reviewedToday = reviewedToday,
    reviewedThisWeek = reviewedThisWeek,
    accuracyPercent = computeAccuracyPercent(knewCount, totalReviewCount),
    leitnerBoxCounts = leitnerBoxCounts(vocabEntries),
    textsSaved = texts.size,
    textsCompleted = texts.count { isTextCompleted(it) },
    wordsSaved = vocabEntries.size
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.ui.statistics.StatisticsUiStateTest"`
Expected: PASS (all 9 test methods).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiState.kt \
        app/src/test/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiStateTest.kt
git commit -m "Add pure statistics calculations (streak, accuracy, completion)"
```

---

### Task 3: Log vocab review answers

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/VocabReviewScreen.kt:80-103` (the `answer()` function)

**Interfaces:**
- Consumes: `ReviewLogEntry` (Task 1), `AppDatabase.reviewLogDao(): ReviewLogDao` (Task 1).

No automated test: `VocabReviewViewModel` is an `AndroidViewModel` with no existing unit-test coverage in this codebase (there's no Robolectric dependency, and DB-backed ViewModels aren't unit tested here — only pure state builders like `HomeUiState.kt`'s `composeHomeState` are). Verified manually in Step 3.

- [ ] **Step 1: Add the import**

In `app/src/main/java/com/ziaee/frenchreader/ui/VocabReviewScreen.kt`, add to the imports:

```kotlin
import com.ziaee.frenchreader.data.ReviewLogEntry
```

- [ ] **Step 2: Log the answer alongside the existing update**

Replace:

```kotlin
        viewModelScope.launch { db.vocabDao().update(updated) }
        reviewedCount++
        current = queue.removeFirstOrNull()
```

with:

```kotlin
        viewModelScope.launch {
            db.vocabDao().update(updated)
            db.reviewLogDao().insert(
                ReviewLogEntry(
                    entryId = entry.id,
                    timestampMs = now,
                    knew = knew,
                    boxBefore = entry.leitnerBox,
                    boxAfter = updated.leitnerBox
                )
            )
        }
        reviewedCount++
        current = queue.removeFirstOrNull()
```

- [ ] **Step 3: Manually verify**

Build and run the app on a device/emulator:
```bash
./gradlew :app:installDebug
```
Open a text, save at least one vocab word if none exist yet, open Vocab Review, answer a few cards (mix of "knew"/"didn't know"), then inspect the table:
```bash
adb shell run-as com.ziaee.frenchreader sqlite3 /data/data/com.ziaee.frenchreader/databases/french_reader.db "SELECT entryId, knew, boxBefore, boxAfter FROM review_log;"
```
Expected: one row per answered card, `knew` matching what was tapped, `boxBefore`/`boxAfter` matching the Leitner box transition shown in `VocabReviewScreen.kt`'s existing "Current box" display.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/VocabReviewScreen.kt
git commit -m "Log vocab review answers to review_log"
```

---

### Task 4: Log listening time

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt` (imports, `startPositionTicker()`, `persistPositionNow()`)

**Interfaces:**
- Consumes: `AppDatabase.activityLogDao(): ActivityLogDao` and `ActivityLogDao.addListening(date: String, deltaMs: Long)` (Task 1).

No automated test, same reasoning as Task 3 (`ReadingViewModel` has no existing unit-test coverage — it depends on `ExoPlayer`/`Application`). Verified manually in Step 4.

- [ ] **Step 1: Add the import**

In `app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt`, add to the imports:

```kotlin
import java.time.LocalDate
```

- [ ] **Step 2: Accumulate listening time in the ticker**

Add a new field right below the other job fields:

```kotlin
    private var positionTickerJob: Job? = null
    private var savePositionJob: Job? = null
    private var pendingListeningMs = 0L
```

Replace `startPositionTicker()`:

```kotlin
    private fun startPositionTicker() {
        positionTickerJob = viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    _state.value = _state.value.copy(currentPositionMs = player.currentPosition)
                    pendingListeningMs += 150
                    schedulePositionSave()
                }
                delay(150)
            }
        }
    }
```

- [ ] **Step 3: Flush the accumulated time in the existing debounced save**

Replace `persistPositionNow()`:

```kotlin
    fun persistPositionNow() {
        val doc = _state.value.textDoc ?: return
        val chunkIndex = _state.value.currentChunkIndex
        val positionMs = player.currentPosition
        val listeningMs = pendingListeningMs
        pendingListeningMs = 0
        viewModelScope.launch {
            db.textDao().savePosition(doc.id, chunkIndex, positionMs)
            if (listeningMs > 0) {
                db.activityLogDao().addListening(LocalDate.now().toString(), listeningMs)
            }
        }
    }
```

This reuses the two existing call sites of `persistPositionNow()` (the 3-second debounce in `schedulePositionSave()`, and the eager call in `onCleared()`) — no new lifecycle wiring needed, and the last few unflushed seconds of a session are always caught when the reading screen closes.

- [ ] **Step 4: Manually verify**

```bash
./gradlew :app:installDebug
```
Open a text, press play, let it run for roughly 15 seconds, then press back (triggers `onCleared()` → `persistPositionNow()`). Then:
```bash
adb shell run-as com.ziaee.frenchreader sqlite3 /data/data/com.ziaee.frenchreader/databases/french_reader.db "SELECT * FROM activity_log;"
```
Expected: one row for today's date (`yyyy-MM-dd`), `listeningMs` roughly matching how long it actually played (a few seconds of slack from the 150ms tick granularity and the 3s debounce is fine).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt
git commit -m "Log listening time to activity_log"
```

---

### Task 5: Statistics screen, entry point, and localization

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeScreen.kt`
- Modify: `app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt` (both `HomeContent(...)` calls need the new parameter or the module won't compile)
- Modify: `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-fa/strings.xml`, `app/src/main/res/values-fr/strings.xml`

**Interfaces:**
- Consumes: `StatisticsUiState`, `DailyListening`, `last7Days`, `composeStatisticsState`, `LEITNER_BOX_COUNT` (Task 2); `AppDatabase.textDao().getAllOnce()`, `vocabDao().getAllOnce()`, `reviewLogDao()`, `activityLogDao()` (Task 1); `HomeScreen`'s existing `onOpenVocab`/`onOpenSettings` wiring pattern (`HomeScreen.kt:145-181`) as the template for the new `onOpenStatistics` parameter.
- Produces: `StatisticsScreen(onBack: () -> Unit)` composable, consumed by `MainActivity.kt`'s new `"statistics"` route.

No automated test for the screen/ViewModel itself (same reasoning as Tasks 3–4 — `StatisticsViewModel` is an `AndroidViewModel`); `StatisticsUiStateTest` (Task 2) already covers every calculation this screen displays. Verified with the localization test (which is automated) plus a manual walkthrough.

- [ ] **Step 1: Add localized strings**

In `app/src/main/res/values/strings.xml`, add these lines right before `</resources>` (after `accessibility_save_vocabulary`):

```xml
    <string name="accessibility_statistics">Statistics</string>
    <string name="statistics_title">Statistics</string>
    <string name="statistics_streak_days">%1$d day streak</string>
    <string name="statistics_weekly_chart_title">Listening time (last 7 days)</string>
    <string name="statistics_vocab_section_title">Vocabulary review</string>
    <string name="statistics_reviewed_today">Reviewed today: %1$d</string>
    <string name="statistics_reviewed_week">Reviewed this week: %1$d</string>
    <string name="statistics_accuracy_percent">Overall accuracy: %1$d%%</string>
    <string name="statistics_leitner_chart_title">Leitner box distribution</string>
    <string name="statistics_totals_title">Totals</string>
    <string name="statistics_texts_saved">Texts saved: %1$d</string>
    <string name="statistics_texts_completed">Texts completed: %1$d</string>
    <string name="statistics_words_saved">Words saved: %1$d</string>
```

In `app/src/main/res/values-fa/strings.xml`, add before `</resources>`:

```xml
    <string name="accessibility_statistics">آمار</string>
    <string name="statistics_title">آمار</string>
    <string name="statistics_streak_days">%1$d روز پشت‌سرهم</string>
    <string name="statistics_weekly_chart_title">زمان گوش‌دادن (۷ روز اخیر)</string>
    <string name="statistics_vocab_section_title">مرور لغات</string>
    <string name="statistics_reviewed_today">مرورشده امروز: %1$d</string>
    <string name="statistics_reviewed_week">مرورشده این هفته: %1$d</string>
    <string name="statistics_accuracy_percent">دقت کلی: %1$d%%</string>
    <string name="statistics_leitner_chart_title">پراکندگی جعبه‌های لایتنر</string>
    <string name="statistics_totals_title">مجموع</string>
    <string name="statistics_texts_saved">متن‌های ذخیره‌شده: %1$d</string>
    <string name="statistics_texts_completed">متن‌های تکمیل‌شده: %1$d</string>
    <string name="statistics_words_saved">لغات ذخیره‌شده: %1$d</string>
```

In `app/src/main/res/values-fr/strings.xml`, add before `</resources>`:

```xml
    <string name="accessibility_statistics">Statistiques</string>
    <string name="statistics_title">Statistiques</string>
    <string name="statistics_streak_days">%1$d jours consécutifs</string>
    <string name="statistics_weekly_chart_title">Temps d\'écoute (7 derniers jours)</string>
    <string name="statistics_vocab_section_title">Révision du vocabulaire</string>
    <string name="statistics_reviewed_today">Révisés aujourd\'hui : %1$d</string>
    <string name="statistics_reviewed_week">Révisés cette semaine : %1$d</string>
    <string name="statistics_accuracy_percent">Précision globale : %1$d %%</string>
    <string name="statistics_leitner_chart_title">Répartition des boîtes de Leitner</string>
    <string name="statistics_totals_title">Totaux</string>
    <string name="statistics_texts_saved">Textes enregistrés : %1$d</string>
    <string name="statistics_texts_completed">Textes terminés : %1$d</string>
    <string name="statistics_words_saved">Mots enregistrés : %1$d</string>
```

- [ ] **Step 2: Run the localization completeness test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.LocalizationCompletenessTest"`
Expected: PASS (all three locale files now define the same 13 new keys, in addition to everything that was already there).

- [ ] **Step 3: Build the Statistics screen**

Create `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt`:

```kotlin
package com.ziaee.frenchreader.ui.statistics

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.AppDatabase
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class StatisticsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)

    var uiState by mutableStateOf(StatisticsUiState())
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val texts = db.textDao().getAllOnce()
            val vocabEntries = db.vocabDao().getAllOnce()

            val reviewedToday = db.reviewLogDao().countSince(today.startOfDayMs())
            val reviewedThisWeek = db.reviewLogDao().countSince(today.minusDays(6).startOfDayMs())
            val knewCount = db.reviewLogDao().countKnew()
            val totalReviewCount = db.reviewLogDao().countTotal()

            val days = last7Days(today)
            val activityRows = db.activityLogDao().getForDates(days.map { it.toString() })
                .associateBy { it.date }
            val weeklyListening = days.map { day ->
                DailyListening(day, activityRows[day.toString()]?.listeningMs ?: 0L)
            }

            val activeDates = (db.reviewLogDao().distinctActiveDates() + db.activityLogDao().activeDates())
                .toSet()
                .map { LocalDate.parse(it) }
                .toSet()

            uiState = composeStatisticsState(
                texts = texts,
                vocabEntries = vocabEntries,
                reviewedToday = reviewedToday,
                reviewedThisWeek = reviewedThisWeek,
                knewCount = knewCount,
                totalReviewCount = totalReviewCount,
                weeklyListening = weeklyListening,
                activeDates = activeDates,
                today = today
            )
            loading = false
        }
    }
}

private fun LocalDate.startOfDayMs(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit) {
    val vm: StatisticsViewModel = viewModel()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
                    }
                }
            )
        }
    ) { padding ->
        if (vm.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val state = vm.uiState
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            StreakSection(state.streakDays)
            WeeklyListeningSection(state.weeklyListening)
            VocabReviewSection(state)
            TotalsSection(state)
        }
    }
}

@Composable
private fun StreakSection(streakDays: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            stringResource(R.string.statistics_streak_days, streakDays),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun WeeklyListeningSection(days: List<DailyListening>) {
    Text(
        stringResource(R.string.statistics_weekly_chart_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
    val maxMs = (days.maxOfOrNull { it.listeningMs } ?: 0L).coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.weight(1f).fillMaxWidth(0.5f)) {
                    val heightFraction = (day.listeningMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
                    val barHeight = size.height * heightFraction
                    drawRect(
                        color = barColor,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight)
                    )
                }
                Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun VocabReviewSection(state: StatisticsUiState) {
    Text(
        stringResource(R.string.statistics_vocab_section_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(stringResource(R.string.statistics_reviewed_today, state.reviewedToday))
    Text(stringResource(R.string.statistics_reviewed_week, state.reviewedThisWeek))
    Text(stringResource(R.string.statistics_accuracy_percent, state.accuracyPercent))
    LeitnerChart(state.leitnerBoxCounts)
}

@Composable
private fun LeitnerChart(counts: Map<Int, Int>) {
    Text(
        stringResource(R.string.statistics_leitner_chart_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp)
    )
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.secondary
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        (1..LEITNER_BOX_COUNT).forEach { box ->
            val count = counts[box] ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.weight(1f).fillMaxWidth(0.5f)) {
                    val heightFraction = count.toFloat() / maxCount.toFloat()
                    val barHeight = size.height * heightFraction
                    drawRect(
                        color = barColor,
                        topLeft = Offset(0f, size.height - barHeight),
                        size = Size(size.width, barHeight)
                    )
                }
                Text(box.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TotalsSection(state: StatisticsUiState) {
    Text(
        stringResource(R.string.statistics_totals_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(stringResource(R.string.statistics_texts_saved, state.textsSaved))
    Text(stringResource(R.string.statistics_texts_completed, state.textsCompleted))
    Text(stringResource(R.string.statistics_words_saved, state.wordsSaved))
}
```

- [ ] **Step 4: Wire the entry point in `HomeScreen.kt`**

In `app/src/main/java/com/ziaee/frenchreader/ui/home/HomeScreen.kt`, add one new import alongside the existing individual icon imports (`HomeScreen.kt:14-21`):

```kotlin
import androidx.compose.material.icons.filled.BarChart
```

Add a new parameter to the top-level `HomeScreen` composable (`HomeScreen.kt:66-71`):

```kotlin
fun HomeScreen(
    onOpenText: (Long) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenVocab: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit
) {
```

Pass it through to `HomeContent` at `HomeScreen.kt:104-106` the same way `onOpenVocab`/`onOpenSettings` are already passed:

```kotlin
        onOpenVocab = onOpenVocab,
        onOpenStatistics = onOpenStatistics,
        onOpenSettings = onOpenSettings,
        onOpenLibrary = onOpenLibrary,
```

Add the matching parameter to `HomeContent`'s signature (`HomeScreen.kt:145-147`):

```kotlin
    onOpenVocab: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
```

And add the icon button in the `TopAppBar` `actions` block (`HomeScreen.kt:170-182`), between the vocab icon and the settings icon:

```kotlin
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.accessibility_vocabulary))
                    }
                    IconButton(onClick = onOpenStatistics) {
                        Icon(Icons.Default.BarChart, contentDescription = stringResource(R.string.accessibility_statistics))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.accessibility_settings))
                    }
```

`HomeContent` is also driven directly (with a static `HomeUiState`, bypassing the real `HomeViewModel`) by `app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt`, in two places. Since `onOpenStatistics` is now a required parameter, both `HomeContent(...)` calls there need it too, or the module won't compile. In `HomeScreenTest.kt`, add `onOpenStatistics = {},` to both calls, right after the existing `onOpenVocab = {},` line (there are two occurrences, in `homeShowsApprovedSections` and `selectingAHeadlineShowsTheDownloadAction`):

```kotlin
                    onOpenVocab = {},
                    onOpenStatistics = {},
                    onOpenSettings = {},
```

- [ ] **Step 5: Wire the route in `MainActivity.kt`**

In `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt`, add the import:

```kotlin
import com.ziaee.frenchreader.ui.statistics.StatisticsScreen
```

Update the `"home"` composable's `HomeScreen(...)` call to pass the new callback:

```kotlin
        composable("home") {
            HomeScreen(
                onOpenText = { id -> navController.navigate("reading/$id") },
                onOpenLibrary = { navigateToTab("library") },
                onOpenVocab = { navController.navigate("vocab") },
                onOpenStatistics = { navController.navigate("statistics") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
```

Add the new route right after the existing `"settings"` composable:

```kotlin
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("statistics") {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }
```

- [ ] **Step 6: Build and manually verify**

```bash
./gradlew :app:installDebug
```
Open the app, tap the new statistics icon on Home (between the vocab and settings icons), confirm:
- The screen opens without crashing and shows a streak count, a 7-day bar chart, review stats, a 5-bar Leitner chart, and totals.
- Numbers roughly match what Tasks 3–4's manual verification produced (reviewed-today count, listening chart bar for today).
- Switching the app language in Settings (fa/fr/en) changes every label on this screen, including the weekday abbreviations on the chart.
- Back button returns to Home.

- [ ] **Step 7: Run the full test suite**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```
Expected: PASS (no regressions in `LocalizationCompletenessTest` or any other existing test).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt \
        app/src/main/java/com/ziaee/frenchreader/ui/home/HomeScreen.kt \
        app/src/androidTest/java/com/ziaee/frenchreader/ui/home/HomeScreenTest.kt \
        app/src/main/java/com/ziaee/frenchreader/MainActivity.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-fa/strings.xml \
        app/src/main/res/values-fr/strings.xml
git commit -m "Add Statistics screen with entry point on Home"
```

---
*Plan derived from the approved spec. Next: hand off to subagent-driven-development or executing-plans.*
