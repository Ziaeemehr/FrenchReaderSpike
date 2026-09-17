# Reading-screen text selection core — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user long-press a word on the Reading screen and drag the native selection
handles to extend the selection to a phrase or sentence, instead of only ever selecting one
word. A single-word selection opens the existing dictionary (unchanged). A multi-word
selection shows a small floating toolbar with **Copy** and **Listen**.

**Architecture:** Replace the plain `Text` composable that renders each paragraph
(`SentenceFlowText` in `ReadingScreen.kt`) with a read-only `BasicTextField`, which gives
native long-press-to-select, drag handles, and a magnifier for free while exposing the live
`TextFieldValue.selection` to application code. A pure function re-derives word/phrase
boundaries from that selection using the same rules the existing single-word lookup already
uses (apostrophe/hyphen handling), so routing is deterministic. A custom `LocalTextToolbar`
override supplies the on-screen anchor rect for the new floating toolbar, reusing the
framework's own selection-positioning instead of doing manual coordinate math.

**Tech Stack:** Kotlin, Jetpack Compose (foundation 1.6.8 / material3, via
`compose-bom:2024.06.00`), ExoPlayer (media3) for audio playback, JUnit4 for unit tests.

**Spec:** `docs/superpowers/specs/2026-09-17-reading-text-selection-core-design.md`

## Global Constraints

- No Translate / Ask AI / Save vocabulary / Share / Search actions in this pass — the
  multi-word toolbar has exactly two actions: Copy and Listen (spec §2).
- No changes to `DictionarySheet.kt`, `AssistantSheet.kt`, or `LlmModelSetupSheet.kt` (spec §5).
- Single-word selection must keep opening `DictionarySheet(word, sentence)` unchanged — same
  composable, same parameters, same call site behavior (spec §4).
- Selection highlight color must be visually distinct from the existing TTS-playback
  highlight (`palette.highlightBg`/`highlightInk`) in all three reading backgrounds (spec §3.4).
- Scrolling must never trigger sentence playback or start a selection (existing gesture
  behavior, must be preserved — spec §4).
- New/changed public declarations that tests need to reach must be `internal`, not `private`
  (Kotlin top-level `private` is file-scoped, not package-scoped — the existing codebase's own
  pattern, e.g. `internal class AssistantSession` in `AssistantSheet.kt`).

---

## Task 1: Selection-classification pure logic

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt:549-568` (the
  existing `extractWordAt` function and its `isWordChar` helper)
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/ReadingScreenSelectionTest.kt` (new)

**Interfaces:**
- Produces: `internal fun wordBoundsAt(text: String, offset: Int): IntRange?`,
  `internal fun extractWordAt(text: String, offset: Int): String` (same signature as today,
  now `internal` instead of `private`, behavior unchanged),
  `internal sealed interface SelectionKind { data class Word(val word: String) : SelectionKind; data class Phrase(val text: String) : SelectionKind }`,
  `internal fun classifySelection(text: String, selection: androidx.compose.ui.text.TextRange): SelectionKind?`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/ziaee/frenchreader/ui/ReadingScreenSelectionTest.kt`:

```kotlin
package com.ziaee.frenchreader.ui

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingScreenSelectionTest {
    private val sentence = "Le chat noir dort dans l'appartement."

    @Test
    fun extractWordAtIsolatesWordTouchingOffset() {
        // "chat" spans indices 3..6 (0-indexed, end-exclusive) in the sentence above.
        assertEquals("chat", extractWordAt(sentence, 4))
    }

    @Test
    fun extractWordAtStripsElidedArticleAcrossApostrophe() {
        val offsetInsideAppartement = sentence.indexOf("appartement") + 2
        assertEquals("appartement", extractWordAt(sentence, offsetInsideAppartement))
    }

    @Test
    fun classifySelectionReturnsNullForCollapsedSelection() {
        assertNull(classifySelection(sentence, TextRange(4, 4)))
    }

    @Test
    fun classifySelectionReturnsWordWhenRangeIsWithinOneWord() {
        // Selecting just "ha" inside "chat" (indices 4..6) must snap to the whole word.
        val kind = classifySelection(sentence, TextRange(4, 6))
        assertTrue(kind is SelectionKind.Word)
        assertEquals("chat", (kind as SelectionKind.Word).word)
    }

    @Test
    fun classifySelectionReturnsWordAndDropsElidedArticleForApostropheSpan() {
        // A raw selection covering the whole "l'appartement" token (as a native
        // double-tap word-selection might report it) must resolve to just
        // "appartement", matching extractWordAt's existing dictionary-lookup rule.
        val start = sentence.indexOf("l'appartement")
        val end = start + "l'appartement".length
        val kind = classifySelection(sentence, TextRange(start, end))
        assertTrue(kind is SelectionKind.Word)
        assertEquals("appartement", (kind as SelectionKind.Word).word)
    }

    @Test
    fun classifySelectionReturnsPhraseForMultiWordRange() {
        val start = sentence.indexOf("chat")
        val end = sentence.indexOf("dort") + "dort".length
        val kind = classifySelection(sentence, TextRange(start, end))
        assertTrue(kind is SelectionKind.Phrase)
        assertEquals("chat noir dort", (kind as SelectionKind.Phrase).text)
    }

    @Test
    fun classifySelectionSnapsPartialWordsAtBothEndsOfAPhrase() {
        // Selection starts mid-"chat" and ends mid-"dort" -- both ends must
        // snap outward to their full word boundaries.
        val start = sentence.indexOf("chat") + 2
        val end = sentence.indexOf("dort") + 2
        val kind = classifySelection(sentence, TextRange(start, end))
        assertTrue(kind is SelectionKind.Phrase)
        assertEquals("chat noir dort", (kind as SelectionKind.Phrase).text)
    }

    @Test
    fun classifySelectionReturnsNullWhenRangeHasNoWordCharacters() {
        // A selection that lands entirely on punctuation/whitespace with no
        // adjacent word character at either edge is not classifiable.
        assertNull(classifySelection("   ", TextRange(0, 2)))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.ui.ReadingScreenSelectionTest"`
Expected: FAIL to compile — `classifySelection`, `SelectionKind`, and `wordBoundsAt` don't exist yet, and `extractWordAt` is `private` (unresolved reference from the test file).

- [ ] **Step 3: Implement `wordBoundsAt`, `classifySelection`, and `SelectionKind`**

Replace lines 549-568 of `ReadingScreen.kt` (the current `extractWordAt` function and its
doc comment) with:

```kotlin
/** Word/selection boundary rules shared by the dictionary long-press lookup and the
 * multi-word selection classifier below. Apostrophes are treated as boundaries (not word
 * characters) so "l'appartement" isolates "appartement", not the elided article with it --
 * WordReference wouldn't recognize the elided form. */
private fun isWordChar(c: Char) = c.isLetter() || c == '-'

/** Returns the [start, end) range of the word touching [offset] in [text], or null if
 * [offset] doesn't touch a word character. */
internal fun wordBoundsAt(text: String, offset: Int): IntRange? {
    if (text.isEmpty()) return null
    var pos = offset.coerceIn(0, text.length)
    if ((pos >= text.length || !isWordChar(text[pos])) && pos > 0 && isWordChar(text[pos - 1])) {
        pos -= 1
    }
    if (pos >= text.length || !isWordChar(text[pos])) return null

    var start = pos
    var end = pos + 1
    while (start > 0 && isWordChar(text[start - 1])) start--
    while (end < text.length && isWordChar(text[end])) end++
    return start until end
}

/** Extracts the word touching [offset] in [text] for the dictionary long-press lookup. */
internal fun extractWordAt(text: String, offset: Int): String {
    val bounds = wordBoundsAt(text, offset) ?: return ""
    return text.substring(bounds.first, bounds.last + 1)
}

/** What a (non-collapsed) text-field selection resolves to once snapped to word
 * boundaries: exactly one word (dictionary lookup), or a multi-word phrase (selection
 * toolbar). */
internal sealed interface SelectionKind {
    data class Word(val word: String) : SelectionKind
    data class Phrase(val text: String) : SelectionKind
}

/** Classifies a raw text-field [selection] against [text]'s word boundaries. Both edges of
 * the raw selection are snapped outward to the word they touch (so a drag that stops
 * mid-word still selects that whole word), using the same rules as [wordBoundsAt] --
 * independent of how the platform's own long-press/double-tap decided the initial
 * selection. Returns null for a collapsed selection or one that touches no word
 * characters at either edge.
 *
 * A snapped span containing no whitespace resolves to [SelectionKind.Word] using only the
 * word touching the *end* of the selection -- this is what makes a raw selection spanning
 * "l'appartement" (whether from an OS word-break that keeps the elision attached, or a
 * short drag) resolve to "appartement", matching [extractWordAt]'s established behavior,
 * instead of being treated as a two-word phrase. */
internal fun classifySelection(text: String, selection: androidx.compose.ui.text.TextRange): SelectionKind? {
    if (selection.collapsed) return null
    val rawStart = selection.min.coerceIn(0, text.length)
    val rawEnd = selection.max.coerceIn(0, text.length)
    if (rawStart >= rawEnd) return null

    val startBounds = wordBoundsAt(text, rawStart)
    val endBounds = wordBoundsAt(text, rawEnd - 1)
    if (startBounds == null && endBounds == null) return null

    val start = startBounds?.first ?: rawStart
    val end = (endBounds?.last ?: (rawEnd - 1)) + 1
    if (start >= end) return null

    val snapped = text.substring(start, end)
    if (snapped.isBlank()) return null

    return if (snapped.none { it.isWhitespace() }) {
        val word = if (endBounds != null) text.substring(endBounds.first, endBounds.last + 1) else snapped
        SelectionKind.Word(word)
    } else {
        SelectionKind.Phrase(snapped.trim())
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.ui.ReadingScreenSelectionTest"`
Expected: PASS (all 8 tests).

- [ ] **Step 5: Run the full unit test suite to confirm nothing else broke**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: PASS (existing `extractWordAt` call site at the old line ~537 still compiles since
the function's name and signature are unchanged, only its visibility).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt app/src/test/java/com/ziaee/frenchreader/ui/ReadingScreenSelectionTest.kt
git commit -m "Add multi-word selection classification logic for the reading screen"
```

---

## Task 2: Selection highlight colors in `ReadingPalette`

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt:52-90`

**Interfaces:**
- Produces: two new fields on `ReadingPalette`: `val selectionBg: Color`, `val selectionHandle: Color`

- [ ] **Step 1: Add the fields and per-background values**

In `Theme.kt`, change the `ReadingPalette` data class (lines 52-60) to:

```kotlin
data class ReadingPalette(
    val background: Color,
    val ink: Color,
    val inkFaded: Color,
    val highlightBg: Color,
    val highlightInk: Color,
    val divider: Color,
    val accent: Color,
    val selectionBg: Color,
    val selectionHandle: Color
)
```

And add `selectionBg`/`selectionHandle` to each of the three `readingPaletteFor` branches
(lines 62-90), using each palette's existing `accent` color so the selection tint always
matches that background's own accent rather than reusing the amber playback-highlight color:

```kotlin
fun readingPaletteFor(background: ReadingBackground): ReadingPalette = when (background) {
    ReadingBackground.SEPIA -> ReadingPalette(
        background = Color(0xFFFBF6EC),
        ink = Color(0xFF2E2A22),
        inkFaded = Color(0xFF6B6252),
        highlightBg = Color(0xFFF6D97A),
        highlightInk = Color(0xFF2E2A22),
        divider = Color(0xFFE6DDC8),
        accent = Color(0xFF8A6D3B),
        selectionBg = Color(0xFF8A6D3B).copy(alpha = 0.28f),
        selectionHandle = Color(0xFF8A6D3B)
    )
    ReadingBackground.WHITE -> ReadingPalette(
        background = Color(0xFFFFFFFF),
        ink = Color(0xFF1A1A1A),
        inkFaded = Color(0xFF5C5C5C),
        highlightBg = Color(0xFFFFE082),
        highlightInk = Color(0xFF1A1A1A),
        divider = Color(0xFFE0E0E0),
        accent = Color(0xFF3B6EA8),
        selectionBg = Color(0xFF3B6EA8).copy(alpha = 0.28f),
        selectionHandle = Color(0xFF3B6EA8)
    )
    ReadingBackground.DARK -> ReadingPalette(
        background = Color(0xFF1A1A1A),
        ink = Color(0xFFE8E4DA),
        inkFaded = Color(0xFFA8A296),
        highlightBg = Color(0xFF4A3F1E),
        highlightInk = Color(0xFFF6D97A),
        divider = Color(0xFF3A3A3A),
        accent = Color(0xFFD8B978),
        selectionBg = Color(0xFFD8B978).copy(alpha = 0.28f),
        selectionHandle = Color(0xFFD8B978)
    )
}
```

- [ ] **Step 2: Compile-check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: succeeds. (No existing test constructs `ReadingPalette` field-by-field with
positional args in a way the two new fields would break — `ThemeTest.kt` only tests
`resolveDarkTheme`, confirmed by reading that file before writing this plan.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt
git commit -m "Add selection highlight colors to ReadingPalette"
```

---

## Task 3: `ReadingViewModel.playSelection` for arbitrary-text TTS

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt`

**Interfaces:**
- Consumes: `TtsChunkRepository.getOrSynthesize(text: String, voice: String, ratePercent: Int): Result<SynthesisResult>` (existing, `app/src/main/java/com/ziaee/frenchreader/tts/TtsChunkRepository.kt:16`), `SynthesisResult(val audioFile: File, val sentences: List<SentenceBoundary>)` (existing)
- Produces: `fun playSelection(text: String)`, `fun stopSelectionPlayback()` on `ReadingViewModel`

The existing `player: ExoPlayer` drives the paragraph/sentence playlist and position-saving
logic (`onMediaItemTransition`, `persistPositionNow`, etc.) — reusing it for an arbitrary
selection's one-off audio would corrupt that chunk-index bookkeeping. A second, independent
`ExoPlayer` is used instead.

- [ ] **Step 1: Add the second player, and `playSelection`/`stopSelectionPlayback`**

In `ReadingViewModel.kt`, add a field next to the existing `player` (after line 59):

```kotlin
    val player: ExoPlayer = ExoPlayer.Builder(app).build()
    private val selectionPlayer: ExoPlayer = ExoPlayer.Builder(app).build()
    private var selectionPlaybackJob: Job? = null
```

Add these two new functions (near `togglePlayPause`/`skipMs`, after line 281):

```kotlin
    /** Plays [text] (an arbitrary multi-word selection, not necessarily a whole sentence)
     * via a synthesis call independent of the paragraph/sentence playlist -- selections
     * don't line up with the per-chunk audio already generated for playback, and using the
     * main [player] for a one-off clip would corrupt its chunk<->player-item bookkeeping. */
    fun playSelection(text: String) {
        val doc = _state.value.textDoc ?: return
        selectionPlaybackJob?.cancel()
        selectionPlaybackJob = viewModelScope.launch {
            selectionPlayer.stop()
            selectionPlayer.clearMediaItems()
            val result = ttsRepo.getOrSynthesize(text, doc.voice, doc.ratePercent)
            result.onSuccess { synth ->
                selectionPlayer.setMediaItem(MediaItem.fromUri(synth.audioFile.toURI().toString()))
                selectionPlayer.prepare()
                selectionPlayer.play()
            }
        }
    }

    fun stopSelectionPlayback() {
        selectionPlaybackJob?.cancel()
        selectionPlayer.stop()
    }
```

- [ ] **Step 2: Release the second player in `onCleared`**

Change `onCleared` (currently lines 368-372):

```kotlin
    override fun onCleared() {
        persistPositionNow()
        player.release()
        selectionPlayer.release()
        super.onCleared()
    }
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: succeeds.

There is no existing unit test for `ReadingViewModel` (it depends on `AndroidViewModel` +
`ExoPlayer.Builder(app)` + Room, none of which run under plain JUnit here — confirmed by the
absence of any `ReadingViewModelTest` in `app/src/test`, matching this codebase's existing
pattern of only unit-testing the pure/non-Android pieces, e.g. `AssistantSessionTest` tests
`AssistantSession`, never `AssistantViewModel` directly). `playSelection` is covered by the
manual QA pass in Task 6, not a new unit test.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ReadingViewModel.kt
git commit -m "Add ReadingViewModel.playSelection for arbitrary text-selection playback"
```

---

## Task 4: Selection toolbar composable, controller, and strings

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/SelectionToolbar.kt`
- Modify: `app/src/main/res/values/strings.xml:152` (after `assistant_action_added`)
- Modify: `app/src/main/res/values-fa/strings.xml:152` (same anchor)
- Modify: `app/src/main/res/values-fr/strings.xml:152` (same anchor)

**Interfaces:**
- Produces: `class SelectionToolbarController : TextToolbar` (properties: `rect: Rect?`,
  `onCopyRequested: (() -> Unit)?`, `status: TextToolbarStatus`), `@Composable fun SelectionToolbarContent(onCopy: () -> Unit, onListen: () -> Unit)`,
  `class SelectionRectPositionProvider(rect: Rect, marginPx: Float) : PopupPositionProvider`

- [ ] **Step 1: Add the two string resources to all three locale files**

In `app/src/main/res/values/strings.xml`, immediately after line 152
(`<string name="assistant_action_added">Added</string>`), add:

```xml
    <string name="selection_action_copy">Copy</string>
    <string name="selection_action_listen">Listen</string>
```

In `app/src/main/res/values-fa/strings.xml`, immediately after the equivalent
`assistant_action_added` line, add:

```xml
    <string name="selection_action_copy">کپی</string>
    <string name="selection_action_listen">شنیدن</string>
```

In `app/src/main/res/values-fr/strings.xml`, immediately after the equivalent
`assistant_action_added` line, add:

```xml
    <string name="selection_action_copy">Copier</string>
    <string name="selection_action_listen">Écouter</string>
```

- [ ] **Step 2: Create `SelectionToolbar.kt`**

```kotlin
package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Row
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.ziaee.frenchreader.R
import kotlin.math.roundToInt

/** Intercepts the framework's own text-selection toolbar trigger (fired by [BasicTextField]
 * when a selection is active) purely to read its on-screen anchor [rect] and its
 * ready-made [onCopyRequested] clipboard callback -- the actual toolbar UI shown is
 * [SelectionToolbarContent], not the platform's default copy/paste bubble. */
class SelectionToolbarController : TextToolbar {
    var rect by mutableStateOf<Rect?>(null)
        private set
    var onCopyRequested by mutableStateOf<(() -> Unit)?>(null)
        private set
    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.rect = rect
        this.onCopyRequested = onCopyRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
    }
}

/** Positions a [androidx.compose.ui.window.Popup] just above [rect] (a global-coordinates
 * selection anchor from [SelectionToolbarController]), centered horizontally on it and
 * clamped to stay within the window. */
class SelectionRectPositionProvider(
    private val rect: Rect,
    private val marginPx: Float
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).toFloat().coerceAtLeast(0f)
        val x = (rect.left + rect.width / 2f - popupContentSize.width / 2f).coerceIn(0f, maxX)
        val y = (rect.top - popupContentSize.height - marginPx).coerceAtLeast(0f)
        return IntOffset(x.roundToInt(), y.roundToInt())
    }
}

/** The two-action floating toolbar shown for a multi-word selection. */
@Composable
fun SelectionToolbarContent(onCopy: () -> Unit, onListen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            TextButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_copy))
            }
            TextButton(onClick = onListen) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                Text(stringResource(R.string.selection_action_listen))
            }
        }
    }
}
```

- [ ] **Step 3: Compile-check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: succeeds. (`Row`/`Surface`/`Text`/`TextButton` from `material3.*` wildcard-style
imports match this file's usage in `DictionarySheet.kt`; here they're imported explicitly
since this is a small, focused file per the codebase's newer files' style, e.g.
`AssistantSheet.kt`.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/SelectionToolbar.kt app/src/main/res/values/strings.xml app/src/main/res/values-fa/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "Add selection toolbar composable, TextToolbar controller, and strings"
```

---

## Task 5: Replace `Text` with a read-only `BasicTextField` in `SentenceFlowText`

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt:1-45` (imports),
  `:288-421` (`ChunkParagraph`, to add/thread the new callback), `:459-547` (`SentenceFlowText`)

**Interfaces:**
- Consumes: `internal fun classifySelection(text: String, selection: TextRange): SelectionKind?` (Task 1), `ReadingPalette.selectionBg`/`selectionHandle` (Task 2)
- Produces: new `SentenceFlowText`/`ChunkParagraph` parameter `onPhraseSelected: (String) -> Unit`, new `ChunkParagraph`/`SentenceFlowText` parameter `clearSelectionSignal: Int`

This is the core mechanical change: `SentenceFlowText` currently renders a plain `Text` with
a custom `pointerInput`/`detectTapGestures` modifier for tap-to-play and long-press-to-lookup
(lines 512-547 today). Both gestures move into `onValueChange` on a read-only
`BasicTextField` instead, so the field's native long-press-and-drag selection can report a
real `TextRange` back to application code.

- [ ] **Step 1: Update imports**

In `ReadingScreen.kt`, remove (no longer used anywhere in this file after this task):

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
```

Add:

```kotlin
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Popup
```

(`CompositionLocalProvider` is used in Task 6 and is already available via the existing
`androidx.compose.runtime.*` wildcard import at line 15 — no new import needed for it. `Rect`
and `IntOffset` are only used inside `SelectionToolbar.kt`, added in Task 4 — not needed
here.)

- [ ] **Step 2: Replace `SentenceFlowText`'s rendering (lines 512-547) with a `BasicTextField`**

Replace:

```kotlin
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = 0.1.sp,
            color = color,
            fontWeight = fontWeight,
            textAlign = TextAlign.Start
        ),
        onTextLayout = { textLayout = it },
        modifier = Modifier.pointerInput(chunk.sentences) {
            detectTapGestures(
                onTap = { pos ->
                    val layout = textLayout ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    val idx = ranges.indexOfFirst { offset in it }
                    if (idx >= 0) onSentenceClick(chunk.sentences[idx])
                },
                onLongPress = { pos ->
                    val layout = textLayout ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    val full = annotated.text
                    val word = extractWordAt(full, offset)
                    if (word.isNotBlank()) {
                        val sentIdx = ranges.indexOfFirst { offset in it }
                        val sentenceText = if (sentIdx >= 0) chunk.sentences[sentIdx].text else full
                        onWordLookup(word, sentenceText)
                    }
                }
            )
        }
    )
```

with:

```kotlin
    var selection by remember(chunk.sentences, clearSelectionSignal) { mutableStateOf(TextRange.Zero) }

    fun sentenceTextFor(offset: Int): String {
        val idx = ranges.indexOfFirst { offset in it }
        return if (idx >= 0) chunk.sentences[idx].text else annotated.text
    }

    BasicTextField(
        value = TextFieldValue(annotatedString = annotated, selection = selection),
        onValueChange = { newValue ->
            if (newValue.selection.collapsed) {
                val idx = ranges.indexOfFirst { newValue.selection.start in it }
                if (idx >= 0) onSentenceClick(chunk.sentences[idx])
                selection = TextRange.Zero
            } else {
                selection = newValue.selection
                when (val kind = classifySelection(annotated.text, newValue.selection)) {
                    is SelectionKind.Word -> {
                        onWordLookup(kind.word, sentenceTextFor(newValue.selection.start))
                        selection = TextRange.Zero
                    }
                    is SelectionKind.Phrase -> onPhraseSelected(kind.text)
                    null -> selection = TextRange.Zero
                }
            }
        },
        readOnly = true,
        textStyle = TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = 0.1.sp,
            color = color,
            fontWeight = fontWeight,
            textAlign = TextAlign.Start
        ),
        cursorBrush = SolidColor(Color.Transparent)
    )
```

Note: `onTextLayout`/`textLayout` are dropped entirely here — the tap-to-sentence lookup no
longer needs `getOffsetForPosition` from a manually-tracked `TextLayoutResult`, because
`BasicTextField`'s own `onValueChange` already reports the tapped character offset via
`newValue.selection.start` for a collapsed selection.

- [ ] **Step 3: Thread the two new parameters through `SentenceFlowText` and `ChunkParagraph`**

In `SentenceFlowText`'s signature (currently lines 460-471), add two parameters:

```kotlin
@Composable
private fun SentenceFlowText(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    palette: ReadingPalette,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit,
    onPhraseSelected: (String) -> Unit,
    clearSelectionSignal: Int
)
```

In `ChunkParagraph`'s signature (currently lines 289-298), add the same two parameters:

```kotlin
@Composable
private fun ChunkParagraph(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    showTranslation: Boolean,
    palette: ReadingPalette,
    fontScale: Float,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onRetry: () -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit,
    onPhraseSelected: (String) -> Unit,
    clearSelectionSignal: Int
)
```

Then pass them through at all three existing `SentenceFlowText(...)` call sites inside
`ChunkParagraph`'s body (currently lines 307-318 for `BlockType.HEADER`, lines 327-338 for
`BlockType.LIST_ITEM`, lines 341-352 for `BlockType.PARAGRAPH`) by adding the same two
arguments to each, e.g. the `HEADER` call site becomes:

```kotlin
                        BlockType.HEADER -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = headerFontSize(chunk.block.headerLevel, fontScale),
                            lineHeight = headerLineHeight(chunk.block.headerLevel, fontScale),
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            palette = palette,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup,
                            onPhraseSelected = onPhraseSelected,
                            clearSelectionSignal = clearSelectionSignal
                        )
```

and identically for the `LIST_ITEM` and `PARAGRAPH` call sites (add
`onPhraseSelected = onPhraseSelected, clearSelectionSignal = clearSelectionSignal` after each
one's existing `onWordLookup = onWordLookup` line).

- [ ] **Step 4: Apply the selection background color via `LocalTextSelectionColors`**

`BasicTextField` reads its selection highlight color from the ambient
`LocalTextSelectionColors`, not from a parameter. Wrap the `BasicTextField` added in Step 2
with:

```kotlin
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.text.selection.LocalTextSelectionColors provides
            androidx.compose.foundation.text.selection.TextSelectionColors(
                handleColor = palette.selectionHandle,
                backgroundColor = palette.selectionBg
            )
    ) {
        BasicTextField(
            // ...as in Step 2...
        )
    }
```

- [ ] **Step 5: Compile-check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: fails at this point with unresolved references `onPhraseSelected` and
`clearSelectionSignal` at `ChunkParagraph`'s own call site in `ReadingScreen` (line ~224) —
expected, fixed in Task 6. Confirm the *only* errors reported are at that call site, not
inside `SentenceFlowText`/`ChunkParagraph` themselves.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt
git commit -m "Replace SentenceFlowText's Text with a read-only BasicTextField for multi-word selection"
```

---

## Task 6: Wire selection state into `ReadingScreen`, render the toolbar, manual QA

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt:49-260`

**Interfaces:**
- Consumes: `SelectionToolbarController` (Task 4), `SelectionToolbarContent` (Task 4), `SelectionRectPositionProvider` (Task 4), `ReadingViewModel.playSelection(text: String)` (Task 3)

- [ ] **Step 1: Add selection state and the toolbar controller to `ReadingScreen`**

After the existing `dictionaryTarget` state (currently line 58), add:

```kotlin
    var selectedPhrase by remember { mutableStateOf<String?>(null) }
    var clearSelectionTick by remember { mutableIntStateOf(0) }
    val selectionToolbarController = remember { SelectionToolbarController() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
```

- [ ] **Step 2: Provide the controller as `LocalTextToolbar` around the paragraph list**

The existing body wraps its content in a `Scaffold { padding -> ... LazyColumn { ... } }`
(currently lines 204-244). Wrap that whole `Scaffold` call in:

```kotlin
    androidx.compose.runtime.CompositionLocalProvider(LocalTextToolbar provides selectionToolbarController) {
        Scaffold(
            // ...unchanged Scaffold arguments and content...
        )
    }
```

- [ ] **Step 3: Pass the two new callbacks into `ChunkParagraph`**

Change the existing `ChunkParagraph(...)` call (currently lines 224-237) to add the two new
arguments:

```kotlin
                ChunkParagraph(
                    chunk = chunk,
                    isCurrentChunk = chunkIndex == state.currentChunkIndex,
                    currentPositionMs = state.currentPositionMs,
                    showTranslation = state.showTranslations,
                    palette = palette,
                    fontScale = fontScale,
                    onSentenceClick = { sentence -> vm.seekToSentence(chunkIndex, sentence) },
                    onRetry = { vm.retryChunk(chunkIndex) },
                    onWordLookup = { word, sentenceText ->
                        vm.player.pause()
                        dictionaryTarget = word to sentenceText
                    },
                    onPhraseSelected = { phrase -> selectedPhrase = phrase },
                    clearSelectionSignal = clearSelectionTick
                )
```

- [ ] **Step 4: Render the floating toolbar**

After the existing `dictionaryTarget?.let { ... }` block (currently lines 246-253), add:

```kotlin
    val toolbarRect = selectionToolbarController.rect
    if (selectedPhrase != null && toolbarRect != null &&
        selectionToolbarController.status == TextToolbarStatus.Shown
    ) {
        val phrase = selectedPhrase!!
        Popup(
            popupPositionProvider = SelectionRectPositionProvider(
                rect = toolbarRect,
                marginPx = with(density) { 8.dp.toPx() }
            ),
            onDismissRequest = {
                selectedPhrase = null
                clearSelectionTick++
            }
        ) {
            SelectionToolbarContent(
                onCopy = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(phrase))
                    selectionToolbarController.onCopyRequested?.invoke()
                    selectedPhrase = null
                    clearSelectionTick++
                },
                onListen = {
                    vm.playSelection(phrase)
                    selectedPhrase = null
                    clearSelectionTick++
                }
            )
        }
    }
```

(`clipboard.setText(...)` copies directly via Compose's own `LocalClipboardManager` — this
is used instead of relying solely on `onCopyRequested`, because that callback is only
guaranteed non-null while the *framework's* selection is still exactly what triggered
`showMenu`; calling `clipboard.setText` with the already-classified `phrase` text is
unambiguous regardless of timing. `onCopyRequested?.invoke()` is still called too so the
framework's own toolbar-hide bookkeeping runs the same path it would for its default menu.)

- [ ] **Step 5: Compile-check**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: succeeds, no unresolved references anywhere in `ReadingScreen.kt`.

- [ ] **Step 6: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: PASS (all existing tests plus the new `ReadingScreenSelectionTest` from Task 1).

- [ ] **Step 7: Manual QA on-device (this task's Compose gesture/UI behavior cannot be
      verified by JVM unit tests — no Robolectric or instrumented UI tests are configured
      for this project)**

Install the debug build on a device or emulator, open any text with at least one
multi-sentence paragraph, and verify:

- Single tap on a sentence plays it, exactly as before.
- Scrolling the article does not trigger playback or start a selection.
- Long-pressing a word selects just that word, with native selection handles visible.
- Dragging a handle extends the selection to a phrase; releasing shows the floating toolbar
  with Copy and Listen, positioned just above the selected text.
- Releasing a drag back down to a single word (or long-pressing without dragging) does not
  show the toolbar — it opens `DictionarySheet` directly, same as before this change.
- Copy places the selected phrase on the clipboard (paste into another app to confirm).
- Listen plays the selected phrase's audio (not the whole sentence's pre-generated audio).
- Tapping elsewhere while a phrase is selected dismisses the toolbar and clears the
  selection.
- The selection's highlight color is visually distinct from the amber TTS-playback
  highlight, in all three reading backgrounds (Settings → reading background: Sepia/White/Dark).
- `l'appartement`-style elided words: long-pressing anywhere in `l'appartement` and
  releasing without dragging opens the dictionary for `appartement`, not `l'appartement`.
- Behavior holds with the font-scale setting set to its largest option.

If any of these fail, fix the specific issue before moving on — do not defer manual-QA
failures.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt
git commit -m "Wire multi-word selection toolbar into ReadingScreen"
```
