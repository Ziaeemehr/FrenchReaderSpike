# Leitner Review Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the vocabulary review screen better: review a single box by tapping it, animated card flip/transition, and a richer visual overview and summary.

**Architecture:** Extract queue selection from `VocabReviewViewModel.startReview` into pure functions (`ReviewQueue.kt`, unit-tested). Then rework the overview composables (tappable colored box ladder, goal ring, streak chip, settings shortcut), then the card and summary composables (flip, transitions, box dots, accuracy ring). No data-model changes.

**Tech Stack:** Kotlin, Jetpack Compose (animation APIs, Canvas), JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-21-stats-leitner-anki-design.md` (section 2, revised)

## Global Constraints

- No Room schema change / no migration.
- Every new user-visible string goes in `values/`, `values-fa/`, `values-fr/` strings.xml with identical keys (`LocalizationCompletenessTest`); no hardcoded Persian literals in Kotlin. Reuse existing keys where possible (`review_box_label`, `review_box_details`, `review_summary_*`, `review_goal_progress`, `review_streak`).
- Review behavior for the default "Start Review" (all boxes) must stay exactly as today: overdue first (sorted by due), then due today, then new cards (oldest first, capped by remaining daily new-card allowance).
- Box review (tap a box): only non-learned cards in that box that have been reviewed before and are due (`nextReviewAtMs <= now`); no new cards.
- Persian (RTL) must work: no hardcoded left/right; animations symmetric (use start/end).
- Implementation via `codex exec -c model_reasoning_effort="low"` when practical; Claude verifies diffs and builds. Unit tests: `./gradlew :app:testDebugUnitTest --tests "<class>"`. UI changes cannot be visually verified without a device: say so.

---

### Task 1: Pure queue selection and bar scaling

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/ReviewQueue.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/ReviewQueueTest.kt`

**Interfaces:**
- Consumes: `VocabEntry` (`id`, `leitnerBox`, `learned`, `createdAtMs`, `nextReviewAtMs`, `lastReviewedAtMs`).
- Produces:
  - `internal fun buildReviewQueue(entries: List<VocabEntry>, nowMs: Long, dayStartMs: Long, newLimit: Int, box: Int? = null): List<VocabEntry>`
  - `internal fun boxBarFractions(counts: List<Int>): List<Float>` (each count / max(max count, 1); empty list -> empty)

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQueueTest {
    private val day = 86_400_000L
    private val now = 10 * day + 5_000
    private val dayStart = 10 * day

    private fun e(id: Long, box: Int, due: Long, reviewed: Long? = 1L, created: Long = id, learned: Boolean = false) =
        VocabEntry(id = id, word = "w$id", sentence = "", textId = 0, dictionaryUrl = "", learned = learned,
            createdAtMs = created, leitnerBox = box, nextReviewAtMs = due, lastReviewedAtMs = reviewed)

    @Test fun `default queue is overdue then today then new capped`() {
        val entries = listOf(
            e(1, 2, dayStart + 100),            // due today
            e(2, 3, dayStart - day),            // overdue
            e(3, 1, now, reviewed = null, created = 30),  // new (newer)
            e(4, 1, now, reviewed = null, created = 20),  // new (older)
            e(5, 4, now + day),                 // not due
            e(6, 2, dayStart - 2 * day, learned = true)   // learned: excluded
        )
        assertEquals(listOf(2L, 1L, 4L), buildReviewQueue(entries, now, dayStart, newLimit = 1).map { it.id })
    }

    @Test fun `box queue keeps only that box's reviewed due cards`() {
        val entries = listOf(
            e(1, 2, dayStart - day), e(2, 2, dayStart + 10), e(3, 3, dayStart - day),
            e(4, 2, now + day), e(5, 2, now, reviewed = null)
        )
        assertEquals(listOf(1L, 2L), buildReviewQueue(entries, now, dayStart, newLimit = 10, box = 2).map { it.id })
    }

    @Test fun `bar fractions scale to the largest count`() {
        assertEquals(listOf(0.5f, 1f, 0f), boxBarFractions(listOf(5, 10, 0)))
        assertEquals(listOf(0f, 0f), boxBarFractions(listOf(0, 0)))
        assertEquals(emptyList<Float>(), boxBarFractions(emptyList()))
    }
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.ui.ReviewQueueTest"` — expect FAIL (unresolved references).

- [ ] **Step 3: Implement**

```kotlin
package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.data.VocabEntry

internal fun buildReviewQueue(
    entries: List<VocabEntry>, nowMs: Long, dayStartMs: Long, newLimit: Int, box: Int? = null
): List<VocabEntry> {
    val active = entries.filter { !it.learned }
    val scoped = if (box == null) active else active.filter { it.leitnerBox == box }
    val overdue = scoped.filter { it.lastReviewedAtMs != null && it.nextReviewAtMs < dayStartMs }.sortedBy { it.nextReviewAtMs }
    val today = scoped.filter { it.lastReviewedAtMs != null && it.nextReviewAtMs in dayStartMs..nowMs }.sortedBy { it.nextReviewAtMs }
    val fresh = if (box != null) emptyList()
    else active.filter { it.lastReviewedAtMs == null }.sortedBy { it.createdAtMs }.take(newLimit)
    return overdue + today + fresh
}

internal fun boxBarFractions(counts: List<Int>): List<Float> {
    val max = maxOf(counts.maxOrNull() ?: 0, 1)
    return counts.map { it.toFloat() / max }
}
```

- [ ] **Step 4: Run tests, expect PASS** (also `--tests "com.ziaee.frenchreader.ui.*"`).
- [ ] **Step 5: Commit** `git add app/src && git commit -m "feat(review): add pure review queue selection"`

---

### Task 2: ViewModel box mode and overview redesign

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/VocabReviewScreen.kt` (`VocabReviewViewModel.startReview`, `VocabReviewScreen` signature/top bar, `ReviewOverview`, `BoxDashboardRow`)
- Modify: `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt` (route `vocab_review/{scope}`)
- Modify: `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml`

**Interfaces:**
- Consumes: `buildReviewQueue`, `boxBarFractions` from Task 1; existing `leitnerBoxColor(box)`, `computeStreak`.
- Produces: `fun startReview(box: Int? = null)` on the ViewModel; `VocabReviewScreen(scope: Long, onBack: () -> Unit, onOpenSettings: () -> Unit)`; new composables `GoalRing(done: Int, goal: Int, modifier)` and `BoxLadder(boxes: List<ReviewBoxSummary>, onBoxClick: (Int) -> Unit)` (private, in the same file).

- [ ] **Step 1: ViewModel.** Replace the queue-building block in `startReview` with `queue.addAll(buildReviewQueue(scoped(db.vocabDao().getAllOnce()), now, dayStart, newCount, box))` and give `startReview` the `box: Int? = null` parameter (keep everything else: clear queue, counters, `sessionStartedAtMs`, `stage`). If the built queue is empty the existing code already goes straight to SUMMARY.

- [ ] **Step 2: Top bar + navigation.** Add an `IconButton` with `Icons.Default.Settings` (`contentDescription = stringResource(R.string.review_open_settings)`) in `TopAppBar` `actions`, calling `onOpenSettings`. In `MainActivity`'s `vocab_review/{scope}` route pass `onOpenSettings = { navController.navigate("settings") }`.

- [ ] **Step 3: Overview UI.** Rebuild `ReviewOverview`:
  - Header row: `GoalRing` (72.dp; Canvas `drawArc` track in `outlineVariant`, sweep in `primary`, sweep = `(done/goal).coerceIn(0f,1f) * 360f`, text `"$done/$goal"` centered) next to a Column with `review_goal_progress` text and a streak chip (`AssistChip`/`SuggestionChip` with `Icons.Default.LocalFireDepartment`, `review_streak`).
  - The three `Metric`s stay (due/new/estimated).
  - The main Start button stays and calls `vm.startReview()`.
  - `Text(stringResource(R.string.review_box_tap_hint), labelSmall)` above the ladder.
  - `BoxLadder`: for each box a clickable `Card` (`enabled` only when `dueCount > 0`; disabled cards drawn at 60% alpha) containing icon (existing icon list), title `review_box_label`, details `review_box_details`, a bar row: a `Box` track with a filled `Box` whose `fillMaxWidth(fraction)` uses `boxBarFractions(boxes.map { it.count })[i]` colored `leitnerBoxColor(box)`, and a due badge (`Badge`/small rounded `Surface` with `dueCount`) when `dueCount > 0`. Clicking calls `vm.startReview(box)` via `onBoxClick`. Keep the next-review date label.

- [ ] **Step 4: Strings** (same keys in all three files)

| key | en | fa | fr |
|---|---|---|---|
| review_open_settings | Review settings | تنظیمات مرور | Paramètres de révision |
| review_box_tap_hint | Tap a box to review only its cards | برای مرور فقط یک جعبه، روی آن بزنید | Touchez une boîte pour ne réviser que ses cartes |
| review_tap_to_reveal | Tap the card to reveal | برای دیدن معنی روی کارت بزنید | Touchez la carte pour voir la réponse |

- [ ] **Step 5: Verify** `./gradlew :app:testDebugUnitTest :app:assembleDebug` pass.
- [ ] **Step 6: Commit** `git commit -am "feat(review): tappable box ladder, goal ring and settings shortcut"`

---

### Task 3: Card flip, transitions, box dots, summary

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/VocabReviewScreen.kt` (`VocabReviewScreen` body, `ReviewCard`, `ReviewSummary`)

**Interfaces:**
- Consumes: Task 2 strings (`review_tap_to_reveal`), `leitnerBoxColor`, `computeAccuracyPercent(knewCount, totalCount)` from `ui.statistics` (internal, same module), existing `vm.correctCount`, `vm.answerCount`.
- Produces: private composables `FlipCard(revealed: Boolean, onFlip: () -> Unit, front: @Composable () -> Unit, back: @Composable () -> Unit)`, `BoxDots(box: Int)`, `AccuracyRing(percent: Int)`.

- [ ] **Step 1: FlipCard.**

```kotlin
@Composable
private fun FlipCard(revealed: Boolean, onFlip: () -> Unit, front: @Composable () -> Unit, back: @Composable () -> Unit) {
    val rotation by animateFloatAsState(if (revealed) 180f else 0f, tween(350), label = "flip")
    Card(
        Modifier.fillMaxWidth().graphicsLayer { rotationY = rotation; cameraDistance = 12f * density }
            .clickable(enabled = !revealed, onClick = onFlip)
    ) {
        if (rotation <= 90f) front()
        else Box(Modifier.graphicsLayer { rotationY = 180f }) { back() }
    }
}
```
  In `ReviewCard` replace the single `Card { ... if (revealed) ... }` with `FlipCard(revealed, onReveal, front = { word centered + Text(review_tap_to_reveal, labelSmall) }, back = { the existing revealed content: TappableFrenchText word, divider, dictionary button, meaning, sentence + audio button, audio error })`. Keep the "Show meaning" button under the card when not revealed.

- [ ] **Step 2: BoxDots + card transition.** `BoxDots(box)`: a `Row` of 5 small circles (10.dp); dots `1..box` filled with `leitnerBoxColor(index+1)` (animate with `animateColorAsState`), the rest `outlineVariant`; shown at the top of `ReviewCard` above the progress text. Wrap the card area in `VocabReviewScreen`'s `else ->` branch with `AnimatedContent(targetState = vm.current, transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut(tween(160))) }, label = "card") { entry -> entry?.let { ReviewCard(...) } }`. The `revealed` reset logic in `LaunchedEffect(vm.current)` stays. Slide direction must flip in RTL (`LocalLayoutDirection`): use `val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1` multiplying the offsets.

- [ ] **Step 3: Summary.** In `ReviewSummary` replace the plain "Correct" `SummaryLine` with an `AccuracyRing(computeAccuracyPercent(vm.correctCount, vm.answerCount))` (Canvas ring 96.dp like `GoalRing`, percent text centered, ring color `primary`) shown under the check icon/title; keep the other lines.

- [ ] **Step 4: Verify** `./gradlew :app:testDebugUnitTest :app:assembleDebug` pass; state that flip/transition/RTL visuals were not verified on a device.
- [ ] **Step 5: Commit** `git commit -am "feat(review): card flip, transitions, box dots and accuracy ring"`
