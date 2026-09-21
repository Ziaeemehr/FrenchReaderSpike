# Statistics Charts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add six new charts/analyses to the Statistics screen: review heatmap, 30-day due forecast, weekly accuracy trend, words added per week, retention by Leitner box, and 30-day listening.

**Architecture:** Pure aggregation functions in a new `StatisticsAggregates.kt` (unit-tested, take raw rows + `today` + `ZoneId`), new fields on `StatisticsUiState`, a one-line DAO addition, ViewModel wiring, and reusable Canvas chart composables in `StatisticsCharts.kt`. No DB schema change.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas), Room, JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-21-stats-leitner-anki-design.md` (section 1)

## Global Constraints

- No Room schema change / no migration (DB stays at version 11).
- Every new user-visible string goes in `values/`, `values-fa/` and `values-fr/` strings.xml with identical keys (`LocalizationCompletenessTest`); no hardcoded Persian literals in Kotlin.
- Existing `composeStatisticsState` callers/tests must keep compiling (new params have defaults).
- Implementation is written via `codex exec -c model_reasoning_effort="low"` (standard tier); Claude verifies each diff and the build.
- Unit test command: `./gradlew :app:testDebugUnitTest --tests "<class>"`.

---

### Task 1: Pure aggregation functions

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsAggregates.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/statistics/StatisticsAggregatesTest.kt`

**Interfaces:**
- Produces:
  - `data class HeatmapDay(val date: LocalDate, val count: Int)`
  - `data class WeeklyCount(val weekStart: LocalDate, val count: Int)`
  - `data class WeeklyAccuracy(val weekStart: LocalDate, val percent: Int, val answers: Int)`
  - `data class BoxRetention(val box: Int, val percent: Int, val answers: Int)`
  - `internal fun reviewHeatmap(logs: List<ReviewLogEntry>, today: LocalDate, weeks: Int = 12, zone: ZoneId = ZoneId.systemDefault()): List<HeatmapDay>` (Monday of the first week through `today`, inclusive)
  - `internal fun dueForecast(entries: List<VocabEntry>, today: LocalDate, days: Int = 30, zone: ZoneId = ZoneId.systemDefault()): List<Int>` (size `days`; overdue counted on index 0; `learned` entries excluded)
  - `internal fun accuracyTrend(logs: List<ReviewLogEntry>, today: LocalDate, weeks: Int = 8, zone: ZoneId = ZoneId.systemDefault()): List<WeeklyAccuracy>` (oldest first, ends with week containing `today`; percent 0 when no answers)
  - `internal fun wordsAddedPerWeek(entries: List<VocabEntry>, today: LocalDate, weeks: Int = 8, zone: ZoneId = ZoneId.systemDefault()): List<WeeklyCount>`
  - `internal fun retentionByBox(logs: List<ReviewLogEntry>): List<BoxRetention>` (boxes 1..5 by `boxBefore`)
  - `internal fun lastDays(today: LocalDate, n: Int): List<LocalDate>` (oldest first)

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.ReviewLogEntry
import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatisticsAggregatesTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 21) // Monday

    private fun ms(d: LocalDate) = d.atTime(12, 0).toInstant(utc).toEpochMilli()
    private fun log(d: LocalDate, knew: Boolean, boxBefore: Int = 1) =
        ReviewLogEntry(entryId = 1, timestampMs = ms(d), knew = knew, boxBefore = boxBefore, boxAfter = boxBefore)
    private fun vocab(created: LocalDate, due: LocalDate, learned: Boolean = false) =
        VocabEntry(word = "w", sentence = "s", textId = 0, dictionaryUrl = "",
            createdAtMs = ms(created), nextReviewAtMs = ms(due), learned = learned)

    @Test fun `lastDays returns n days oldest first`() {
        val days = lastDays(today, 3)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), days)
    }

    @Test fun `heatmap spans first Monday through today and counts per day`() {
        val logs = listOf(log(today, true), log(today, false), log(today.minusDays(1), true))
        val map = reviewHeatmap(logs, today, weeks = 2, zone = utc)
        assertEquals(today.minusWeeks(1), map.first().date)
        assertEquals(today, map.last().date)
        assertEquals(8, map.size) // Mon of last week .. Mon today
        assertEquals(2, map.last().count)
        assertEquals(1, map.first { it.date == today.minusDays(1) }.count)
    }

    @Test fun `forecast counts overdue on day zero and excludes learned`() {
        val entries = listOf(
            vocab(today, today.minusDays(5)),
            vocab(today, today),
            vocab(today, today.plusDays(2)),
            vocab(today, today.plusDays(40)),
            vocab(today, today, learned = true)
        )
        val f = dueForecast(entries, today, days = 30, zone = utc)
        assertEquals(30, f.size)
        assertEquals(2, f[0])
        assertEquals(1, f[2])
        assertEquals(3, f.sum())
    }

    @Test fun `accuracy trend buckets by week`() {
        val logs = listOf(
            log(today, true), log(today, false),               // this week: 50%
            log(today.minusWeeks(1), true)                     // last week: 100%
        )
        val t = accuracyTrend(logs, today, weeks = 3, zone = utc)
        assertEquals(3, t.size)
        assertEquals(WeeklyAccuracy(today.minusWeeks(2), 0, 0), t[0])
        assertEquals(WeeklyAccuracy(today.minusWeeks(1), 100, 1), t[1])
        assertEquals(WeeklyAccuracy(today, 50, 2), t[2])
    }

    @Test fun `words added per week`() {
        val entries = listOf(vocab(today, today), vocab(today.minusDays(1), today), vocab(today.minusWeeks(1), today))
        val w = wordsAddedPerWeek(entries, today, weeks = 2, zone = utc)
        assertEquals(listOf(WeeklyCount(today.minusWeeks(1), 2), WeeklyCount(today, 1)).map { it.count }.reversed(), w.map { it.count })
    }

    @Test fun `retention by box uses boxBefore`() {
        val logs = listOf(log(today, true, 2), log(today, false, 2), log(today, true, 5))
        val r = retentionByBox(logs)
        assertEquals(5, r.size)
        assertEquals(BoxRetention(1, 0, 0), r[0])
        assertEquals(BoxRetention(2, 50, 2), r[1])
        assertEquals(BoxRetention(5, 100, 1), r[4])
    }
}
```

Note on `words added per week`: `today` is a Monday, so `today` and `today.minusDays(1)` fall in different weeks -- fix the fixture while writing: use `today.plusDays(2)` is in the future, so instead create the second entry on `today` too. Expected result `[1, 2]` is wrong for that fixture; final assertion must be `assertEquals(listOf(1, 2), w.map { it.count })` with entries `[vocab(today), vocab(today), vocab(today.minusWeeks(1))]`... use: created on `today` (x2) and `today.minusWeeks(1)` (x1) → oldest first `[1, 2]`. Replace the fixture and assertion accordingly before running.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.ui.statistics.StatisticsAggregatesTest"`
Expected: FAIL (unresolved references).

- [ ] **Step 3: Implement**

```kotlin
package com.ziaee.frenchreader.ui.statistics

import com.ziaee.frenchreader.data.ReviewLogEntry
import com.ziaee.frenchreader.data.VocabEntry
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class HeatmapDay(val date: LocalDate, val count: Int)
data class WeeklyCount(val weekStart: LocalDate, val count: Int)
data class WeeklyAccuracy(val weekStart: LocalDate, val percent: Int, val answers: Int)
data class BoxRetention(val box: Int, val percent: Int, val answers: Int)

private fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
private fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

internal fun lastDays(today: LocalDate, n: Int): List<LocalDate> =
    (n - 1 downTo 0).map { today.minusDays(it.toLong()) }

internal fun reviewHeatmap(
    logs: List<ReviewLogEntry>, today: LocalDate, weeks: Int = 12, zone: ZoneId = ZoneId.systemDefault()
): List<HeatmapDay> {
    val start = today.weekStart().minusWeeks((weeks - 1).toLong())
    val counts = logs.groupingBy { it.timestampMs.toLocalDate(zone) }.eachCount()
    val length = ChronoUnit.DAYS.between(start, today).toInt() + 1
    return (0 until length).map { i ->
        val d = start.plusDays(i.toLong())
        HeatmapDay(d, counts[d] ?: 0)
    }
}

internal fun dueForecast(
    entries: List<VocabEntry>, today: LocalDate, days: Int = 30, zone: ZoneId = ZoneId.systemDefault()
): List<Int> {
    val buckets = IntArray(days)
    entries.filter { !it.learned }.forEach { e ->
        val offset = ChronoUnit.DAYS.between(today, e.nextReviewAtMs.toLocalDate(zone)).toInt().coerceAtLeast(0)
        if (offset < days) buckets[offset]++
    }
    return buckets.toList()
}

private fun weekStarts(today: LocalDate, weeks: Int): List<LocalDate> =
    (weeks - 1 downTo 0).map { today.weekStart().minusWeeks(it.toLong()) }

internal fun accuracyTrend(
    logs: List<ReviewLogEntry>, today: LocalDate, weeks: Int = 8, zone: ZoneId = ZoneId.systemDefault()
): List<WeeklyAccuracy> {
    val byWeek = logs.groupBy { it.timestampMs.toLocalDate(zone).weekStart() }
    return weekStarts(today, weeks).map { ws ->
        val week = byWeek[ws].orEmpty()
        WeeklyAccuracy(ws, computeAccuracyPercent(week.count { it.knew }, week.size), week.size)
    }
}

internal fun wordsAddedPerWeek(
    entries: List<VocabEntry>, today: LocalDate, weeks: Int = 8, zone: ZoneId = ZoneId.systemDefault()
): List<WeeklyCount> {
    val byWeek = entries.groupingBy { it.createdAtMs.toLocalDate(zone).weekStart() }.eachCount()
    return weekStarts(today, weeks).map { WeeklyCount(it, byWeek[it] ?: 0) }
}

internal fun retentionByBox(logs: List<ReviewLogEntry>): List<BoxRetention> {
    val byBox = logs.groupBy { it.boxBefore }
    return (1..LEITNER_BOX_COUNT).map { box ->
        val rows = byBox[box].orEmpty()
        BoxRetention(box, computeAccuracyPercent(rows.count { it.knew }, rows.size), rows.size)
    }
}
```

- [ ] **Step 4: Run tests, expect PASS**

Run: same command as Step 2.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsAggregates.kt app/src/test/java/com/ziaee/frenchreader/ui/statistics/StatisticsAggregatesTest.kt
git commit -m "feat(stats): add aggregation functions for new charts"
```

---

### Task 2: UI state, DAO query, ViewModel wiring

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Daos.kt` (`ReviewLogDao`, after `distinctActiveDates`)
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiState.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt` (`StatisticsViewModel.load`)
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/statistics/StatisticsUiStateTest.kt` (append)

**Interfaces:**
- Consumes: Task 1 functions and data classes.
- Produces: `ReviewLogDao.getAll(): List<ReviewLogEntry>`; `StatisticsUiState` new fields `reviewHeatmap: List<HeatmapDay>`, `dueForecast: List<Int>`, `accuracyTrend: List<WeeklyAccuracy>`, `wordsAddedPerWeek: List<WeeklyCount>`, `retentionByBox: List<BoxRetention>`, `monthlyListening: List<DailyListening>` (all default `emptyList()`); `composeStatisticsState` new defaulted params `reviewLogs: List<ReviewLogEntry> = emptyList()`, `monthlyListening: List<DailyListening> = emptyList()`, `zone: ZoneId = ZoneId.systemDefault()`.

- [ ] **Step 1: Failing test** (append to `StatisticsUiStateTest`)

```kotlin
@Test fun `composeStatisticsState fills new chart fields`() {
    val today = LocalDate.of(2026, 9, 21)
    val log = ReviewLogEntry(entryId = 1, timestampMs = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        knew = true, boxBefore = 2, boxAfter = 3)
    val state = composeStatisticsState(
        texts = emptyList(), vocabEntries = emptyList(), reviewedToday = 0, reviewedThisWeek = 0,
        knewCount = 0, totalReviewCount = 0, weeklyListening = emptyList(), activeDates = emptySet(),
        today = today, reviewLogs = listOf(log), zone = ZoneOffset.UTC
    )
    assertEquals(30, state.dueForecast.size)
    assertEquals(8, state.accuracyTrend.size)
    assertEquals(5, state.retentionByBox.size)
    assertEquals(1, state.reviewHeatmap.last().count)
}
```
(add imports `ReviewLogEntry`, `java.time.ZoneOffset` if missing)

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.ui.statistics.StatisticsUiStateTest"` → FAIL.

- [ ] **Step 3: Implement**
  - `Daos.kt`, in `ReviewLogDao`: `@Query("SELECT * FROM review_log") suspend fun getAll(): List<ReviewLogEntry>`
  - `StatisticsUiState`: add the six fields above; in `composeStatisticsState` add the three defaulted params and set
    `reviewHeatmap = reviewHeatmap(reviewLogs, today, zone = zone)`, `dueForecast = dueForecast(vocabEntries, today, zone = zone)`, `accuracyTrend = accuracyTrend(reviewLogs, today, zone = zone)`, `wordsAddedPerWeek = wordsAddedPerWeek(vocabEntries, today, zone = zone)`, `retentionByBox = retentionByBox(reviewLogs)`, `monthlyListening = monthlyListening`.
  - `StatisticsViewModel.load`: `val reviewLogs = db.reviewLogDao().getAll()`; build `monthlyListening` exactly like `weeklyListening` but over `lastDays(today, 30)`; pass both to `composeStatisticsState`.

- [ ] **Step 4: Run tests, expect PASS** (whole `ui.statistics` package: `--tests "com.ziaee.frenchreader.ui.statistics.*"`).

- [ ] **Step 5: Commit** `git commit -am "feat(stats): expose new chart data in UI state and ViewModel"`

---

### Task 3: Chart composables, strings, screen sections

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsCharts.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/statistics/StatisticsScreen.kt` (`StatisticsScreen` column)
- Modify: `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml`

**Interfaces:**
- Consumes: new `StatisticsUiState` fields from Task 2.
- Produces: `@Composable fun BarChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier)`, `@Composable fun LineChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier)` (values 0..1 already normalized by caller? No: both take raw values and normalize by `max(values.max, 1f)`), `@Composable fun HeatmapGrid(days: List<HeatmapDay>, modifier: Modifier = Modifier)` (columns = weeks, rows = Mon..Sun, alpha of `MaterialTheme.colorScheme.primary` scaled by count/maxCount, empty cells `surfaceVariant`).

- [ ] **Step 1: Add strings** (same keys in all three files)

| key | en | fa | fr |
|---|---|---|---|
| statistics_heatmap_title | Review activity (last 12 weeks) | فعالیت مرور (۱۲ هفتهٔ اخیر) | Activité de révision (12 dernières semaines) |
| statistics_forecast_title | Reviews due (next 30 days) | مرورهای پیش‌رو (۳۰ روز آینده) | Révisions à venir (30 prochains jours) |
| statistics_accuracy_trend_title | Weekly accuracy | دقت هفتگی | Précision hebdomadaire |
| statistics_words_added_title | Words added per week | لغات اضافه‌شده در هر هفته | Mots ajoutés par semaine |
| statistics_retention_title | Retention by Leitner box | ماندگاری در هر جعبهٔ لایتنر | Rétention par boîte de Leitner |
| statistics_listening_month_title | Listening time (last 30 days, min) | زمان گوش دادن (۳۰ روز اخیر، دقیقه) | Temps d'écoute (30 derniers jours, min) |
| statistics_no_data | Not enough data yet | هنوز داده کافی نیست | Pas encore assez de données |

- [ ] **Step 2: Implement `StatisticsCharts.kt`** — `BarChart` and `LineChart` use `Canvas`; bars mirror the existing bar drawing in `StatisticsScreen.kt` (`drawRect` with height = `size.height * value / max`); labels row beneath via `Text(style = labelSmall)`; `LineChart` draws `drawLine` segments between points plus `drawCircle` r=4dp dots; `HeatmapGrid` lays out `days.chunked(7)` as columns of 7 `Box`es (size 14.dp, 2.dp gaps), padding the first column at the top so rows align to Monday.

- [ ] **Step 3: Add sections to `StatisticsScreen`** after `VocabReviewSection(state)` and before `TotalsSection`, each preceded by a `Text(titleMedium, padding(top = 20.dp))` title; show `statistics_no_data` instead of the chart when the data is empty (`reviewHeatmap.all { it.count == 0 }`, `dueForecast.sum() == 0`, `accuracyTrend.all { it.answers == 0 }`, `wordsAddedPerWeek.all { it.count == 0 }`, `retentionByBox.all { it.answers == 0 }`, `monthlyListening.all { it.listeningMs == 0L }`):
  1. Heatmap → `HeatmapGrid(state.reviewHeatmap)`
  2. Forecast → `BarChart(state.dueForecast.map { it.toFloat() }, labels = every 5th day index as "+N" else "")`
  3. Accuracy trend → `LineChart(percents, labels = week start "d/M")`
  4. Words per week → `BarChart(counts, labels = week start "d/M")`
  5. Retention → `BarChart(percents, labels = "1".."5")`
  6. Listening 30d → `BarChart(minutes = listeningMs / 60_000f, labels = every 5th day "d" else "")`

- [ ] **Step 4: Verify**
  Run: `./gradlew :app:testDebugUnitTest` (includes `LocalizationCompletenessTest`) then `./gradlew :app:assembleDebug`
  Expected: all PASS, build succeeds. Then install on device/emulator (`/run`) and eyeball the Statistics screen in en/fa (RTL) to check charts render and labels don't overlap.

- [ ] **Step 5: Commit** `git commit -am "feat(stats): add heatmap, forecast, trend, retention and listening charts"`
