# Statistics screen — design

Status: **approved design, not yet implemented.** Implements ROADMAP.md §3.

## Goal

A new screen showing the user's study activity: listening time, vocab
review accuracy/volume, Leitner box progress, streak, and text/word
totals — using history the app currently discards (it only keeps
*current* state, e.g. `leitnerBox`/`lastReviewedAtMs` hold just the
latest value, not a timeline).

## Decisions locked in during brainstorming

- **"Texts completed"** = the user's saved reading position has reached
  the last chunk of that text at least once. Computed on the fly with
  the existing `TextChunker.chunk(rawText)` (pure, cheap) rather than a
  new stored column — avoids a value that can drift out of sync with
  `rawText`.
- **Streak** = "any activity" per day: a day counts if `ActivityLog` has
  `listeningMs > 0` for that date, or `ReviewLog` has ≥1 row that date.
  Most forgiving definition, matches how Duolingo-style streaks read.
- **TCF exam countdown** (ROADMAP.md §5) is explicitly **out of scope**
  for this spec — separate, small follow-up.
- **Accuracy** is shown as a single all-time cumulative percentage
  (`knew=true` / total), not recomputed per time window — avoids several
  near-duplicate metrics for one number.
- Charts are static Compose `Canvas` bar charts, no charting library —
  consistent with the rest of the app (no chart dependency exists today).

## Data model

Two new Room entities, added in `data/Entities.kt`:

```kotlin
@Entity(tableName = "review_log")
data class ReviewLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val timestampMs: Long,
    val knew: Boolean,
    val boxBefore: Int,
    val boxAfter: Int
)

@Entity(tableName = "activity_log")
data class ActivityLogEntry(
    @PrimaryKey val date: String, // yyyy-MM-dd, java.time.LocalDate.toString()
    val listeningMs: Long
)
```

Migration `MIGRATION_5_6` in `data/AppDatabase.kt`, same style as
`MIGRATION_4_5`:

```kotlin
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

`@Database(entities = [..., ReviewLogEntry::class, ActivityLogEntry::class], version = 6, ...)`,
add `.addMigrations(..., MIGRATION_5_6)`, add `abstract fun reviewLogDao(): ReviewLogDao`
and `abstract fun activityLogDao(): ActivityLogDao`.

## New DAOs (`data/Daos.kt`)

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

`addListening` is a read-modify-write transaction rather than a raw SQL
`ON CONFLICT ... DO UPDATE` — SQLite upsert syntax isn't reliably
available on the bundled SQLite across the app's full `minSdk 26` API
range, and this is called at most once every few seconds (see below),
so the extra round-trip is irrelevant.

## Wiring writes into existing flows

**`VocabReviewViewModel.answer()`** (`VocabReviewScreen.kt:80`) — insert a
`ReviewLogEntry` in the same `viewModelScope.launch` block that already
calls `db.vocabDao().update(updated)`:

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
```

**`ReadingViewModel.startPositionTicker()`** (`ReadingViewModel.kt:331`) —
add a `pendingListeningMs` accumulator, incremented by the tick delta
(150ms) whenever `player.isPlaying`. Flushed to `ActivityLog` from inside
the *existing* debounced `persistPositionNow()` (already fires ~3s after
each position update via `schedulePositionSave()`), so no new timer is
introduced:

```kotlin
private var pendingListeningMs = 0L

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

private suspend fun persistPositionNow() {
    // ...existing position save...
    if (pendingListeningMs > 0) {
        db.activityLogDao().addListening(LocalDate.now().toString(), pendingListeningMs)
        pendingListeningMs = 0
    }
}
```

No separate pause/stop flush is needed: `persistPositionNow()` already
has three call sites — inside the debounce job, eagerly in `onCleared()`
(`ReadingViewModel.kt:361`), and in `ReadingScreen.kt`'s
`DisposableEffect(Unit) { onDispose { ... } }`. Putting the flush inside
`persistPositionNow()` itself means the last unflushed seconds of a session
are caught when the screen closes; the `onDispose` call is the one that
reliably flushes on ordinary back-navigation because it runs before
`viewModelScope` is cancelled.

## Derived metrics (no extra storage)

- **Leitner distribution**: `SELECT leitnerBox, COUNT(*) FROM vocab GROUP BY leitnerBox`
  (new query on the existing `vocab` table — no new table).
- **Texts completed**: for each `TextDocument`, `completed = doc.lastChunkIndex >= TextChunker.chunk(doc.rawText).size - 1`.
  Computed in `StatisticsViewModel` over all rows from `textDao().observeAll()`
  (small N — dozens of saved texts at most).
- **Streak**: union of `ReviewLogDao.distinctActiveDates()` and
  `ActivityLogDao.activeDates()` → a `Set<LocalDate>`. Walk backward from
  today (falling back to yesterday if today isn't in the set yet) while
  each preceding day is present; count = streak length.
- **7-day chart data**: the 7 dates from `today.minusDays(6)..today`,
  each looked up in `ActivityLogDao.getForDates(...)` (0 if missing).

## Screen

New files:
- `ui/StatisticsScreen.kt` (Composable + `StatisticsViewModel`)
- No new prefs — this screen is read-only.

`StatisticsViewModel` loads everything once in a single
`viewModelScope.launch` on `init` (same one-shot pattern as
`VocabReviewViewModel.start()`), exposing a single `StatisticsUiState`
via `mutableStateOf`. No live refresh — it's a snapshot view, matching
how `VocabReviewViewModel` treats its queue as a stable snapshot rather
than a live `Flow`.

Layout, top to bottom (Scaffold + TopAppBar + Column, matching other
screens):
1. **Streak** — flame icon + "N روز پشت‌سرهم" (localized string, new key).
2. **7-day listening chart** — `Canvas`, 7 bars, height ∝ minutes listened
   that day, x-axis labels via `DayOfWeek.getDisplayName(TextStyle.SHORT, currentLocale)`
   (respects `LocalePrefs`/`AppLanguage`, so labels follow the app's FA/FR/EN
   setting rather than always being French — French source content stays
   LTR per existing rule, but this UI chrome is not source content).
3. **Vocab review stats** — "امروز: N", "این هفته: N", "دقت کلی: N%", plus
   a 5-bar `Canvas` chart of Leitner box counts (box 1 → 5).
4. **Totals** — texts saved, texts completed, total vocab words saved.

**Entry point**: new `IconButton` in `ui/home/HomeScreen.kt`'s `TopAppBar`
`actions` block (`HomeScreen.kt:168-182`), inserted between the existing
vocab icon (`Icons.Default.MenuBook`) and settings icon
(`Icons.Default.Settings`). Icon: `Icons.Default.BarChart` (or
`Icons.Default.Insights` if that reads better in context — pick during
implementation), `contentDescription` from a new
`R.string.accessibility_statistics` key (added to all three locale
`strings.xml` files per the existing localization pattern).

**Navigation**: new `"statistics"` route in `MainActivity.kt`'s
`AppNavHost`, alongside the existing `"vocab"`/`"settings"` routes:

```kotlin
composable("statistics") {
    StatisticsScreen(onBack = { navController.popBackStack() })
}
```

`HomeScreen`'s signature gains `onOpenStatistics: () -> Unit`, wired the
same way `onOpenVocab`/`onOpenSettings` already are.

## Error handling

None needed beyond what already exists — this feature only *reads*
existing tables plus two new always-populated ones; there's no network
call, no external API, no failure mode beyond "no data yet" (handled by
showing zero/empty states, e.g. streak = 0, empty chart bars).

## Testing

The project has no automated test suite (noted already in ROADMAP.md
§6 for `ContentSource`). Verification is manual, on-device: use the app
for a day, play some audio, answer some review cards, open Statistics
and confirm the numbers/charts match what was actually done, plus a
migration smoke-test (open the app once on the old schema, confirm no
crash and empty/zero stats, matching how `MIGRATION_4_5` was verified).

## Files touched

- Change: `data/Entities.kt` (add `ReviewLogEntry`, `ActivityLogEntry`)
- Change: `data/AppDatabase.kt` (add `MIGRATION_5_6`, bump `version = 6`,
  register new DAOs)
- Change: `data/Daos.kt` (add `ReviewLogDao`, `ActivityLogDao`)
- Change: `ui/VocabReviewScreen.kt` (`VocabReviewViewModel.answer()` logs
  a `ReviewLogEntry`)
- Change: `ui/ReadingViewModel.kt` (accumulate + flush `pendingListeningMs`
  into `ActivityLog`)
- Create: `ui/StatisticsScreen.kt`
- Change: `ui/home/HomeScreen.kt` (new TopAppBar icon + `onOpenStatistics` param)
- Change: `MainActivity.kt` (new `"statistics"` route)
- Change: `values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml`
  (new localized strings for the Statistics screen and its entry icon)

---
*Design only — not yet implemented. Next step: `writing-plans` skill for
the implementation plan.*
