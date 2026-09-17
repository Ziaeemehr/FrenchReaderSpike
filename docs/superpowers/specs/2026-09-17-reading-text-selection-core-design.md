# Reading-screen text selection core — design spec

Status: approved for implementation (technical approach and scope confirmed by user
on 2026-09-17 — see decisions below).

Sub-project 1 of the larger interaction redesign described in
`docs/Redesign the application's user interfac.md` §"Text Selection, Speech, AI, and
External App Integration". That document bundles several independent subsystems (native
multi-word selection, a full contextual toolbar, an AI-assistant redesign, external-app
integration via `ACTION_PROCESS_TEXT`, a new visual design system). Per the decomposition
agreed with the user, this spec covers **only** the selection mechanics and a minimal
toolbar; the rest are separate specs, done later.

## 1. Problem

The Reading screen currently only supports long-pressing to select exactly one word
(`extractWordAt` in `ReadingScreen.kt`), which then opens the dictionary. There is no way
to select a phrase or sentence — no drag-to-extend, no anchor/extent state, nothing. The
user wants to be able to select more than one word (e.g. to copy it or hear it read aloud).

## 2. Scope boundaries (explicit non-goals for this pass)

- No Translate / Ask AI / Save vocabulary / Share / Search actions in the selection
  toolbar. The toolbar for a multi-word selection has exactly two actions: **Copy** and
  **Listen**.
- No AI-assistant redesign (separate spec, next).
- No external-app integration (`ACTION_PROCESS_TEXT`, Sharesheet, web search) — deferred.
- No new visual design system / selection color tokens beyond what's needed for a visible,
  legible selection highlight that's distinct from the existing TTS-playback highlight.
- No changes to accessibility beyond what the native selection framework already provides
  (TalkBack support for text selection is a platform feature, not something built here).

## 3. Decisions

1. **Mechanism: read-only `BasicTextField`, not `SelectionContainer`.** `SelectionContainer`
   wrapping the existing `Text` was considered and rejected: it only exposes
   copy/cut/paste/selectAll callbacks through `LocalTextToolbar`, with no supported way to
   read the actual selected text/range from application code. That makes it impossible to
   reliably tell "exactly one word" from "a phrase" — a hard requirement (open the
   dictionary only for a single word; never for an expanded selection). A hand-built
   selection system (custom drag handles) was also rejected — the source doc explicitly
   asks for platform-native selection APIs, and reimplementing handles/magnifier/RTL
   handling from scratch is unnecessary risk for no benefit.

   `BasicTextField(readOnly = true)` gives native long-press-to-select, drag handles, and
   magnifier for free, while exposing the live `TextFieldValue.selection: TextRange` (and
   therefore the exact selected substring) to app code reactively via `onValueChange`.
   Cursor is hidden (`cursorBrush = SolidColor(Color.Transparent)`) since there is no
   editing.

2. **Single tap vs. selection, disambiguated via `onValueChange`:** when the field reports a
   new `TextFieldValue` whose selection is *collapsed* (`start == end`), that is a plain tap
   — resolve the sentence at that offset and call the existing `onSentenceClick` (unchanged
   behavior), then immediately reset the field's selection back to collapsed/hidden so no
   cursor is ever shown. When the reported selection is a *real range* (`start != end`),
   that's a long-press-initiated selection (or a drag extending one) — do not trigger
   playback; route to word/phrase handling instead (§4).

3. **Word-boundary consistency with the dictionary:** the framework's own double-tap/long-
   press word selection may include a leading elided article apostrophe differently than
   the existing `extractWordAt` (which treats `'` as a hard boundary, e.g. `l'appartement` →
   `appartement`, matching what WordReference expects). When the resulting selection is
   exactly one word by whitespace/punctuation boundaries, normalize it the same way
   `extractWordAt` does before treating it as "one word" and handing it to the dictionary,
   so existing dictionary-lookup behavior is unaffected.

4. **Selection highlight color** must be visually distinct from the existing TTS-playback
   sentence highlight (`palette.highlightBg`/`highlightInk`) — reuse
   `LocalTextSelectionColors` with a new palette entry rather than the playback highlight
   color, so a user can't confuse "this sentence is playing" with "this text is selected."

## 4. Interaction behavior

- **Single tap on unselected text** — unchanged: identify the sentence at the tap point,
  play it via existing TTS. (Mechanically re-routed through `onValueChange` per decision 2,
  but the observable behavior is identical to today.)
- **Long-press on a word** — selects that word (native handles appear). Does not open
  anything yet; the user may now drag a handle to extend the selection.
- **Selection resolves to exactly one word** (whether from long-press alone, or a drag that
  snaps back to a single word) — show a minimal action affordance with a single **Define**
  action that opens the existing `DictionarySheet(word, sentence)`, unchanged. This
  preserves today's behavior exactly; only the entry gesture's plumbing changed.
- **Selection resolves to more than one word (a phrase or sentence)** — show a small
  floating toolbar (positioned above the selection, following the selected rect) with two
  actions:
  - **Copy** — copies the selected text to the system clipboard.
  - **Listen** — plays the selected text via the existing TTS pipeline (same engine used for
    sentence playback), from the timestamp/text mapping already used by the audio chunker.
    If the selection spans a partial sentence or crosses a sentence boundary, listen reads
    back the exact selected substring via TTS synthesis of that substring text (not the
    original sentence's pre-generated audio), since arbitrary ranges don't line up with
    existing per-sentence audio segments.
- **Dismissing selection** — tapping elsewhere, opening the dictionary, or completing a
  Copy/Listen action clears the selection back to collapsed. Scrolling must not clear or
  trigger a selection (gesture thresholds already used for scroll-vs-tap must keep working
  the same way they do today).

## 5. Architecture overview

```
SentenceFlowText (ReadingScreen.kt, modified)
  BasicTextField(value = TextFieldValue(annotated, selection), readOnly = true, ...)
    onValueChange:
      selection collapsed  → resolve sentence at offset → onSentenceClick (existing)
      selection is a range → resolve word/phrase → either:
          exactly one word  → Define affordance → DictionarySheet(word, sentence)  [existing]
          multiple words    → SelectionToolbar(onCopy, onListen)                    [new]
```

New pieces:

- `SelectionToolbar` composable (new, small): two `AssistChip`/`IconButton`s (Copy, Listen),
  positioned via the selection's bounding rect from `onTextLayout`.
- A small pure function to classify a `TextRange` selection against the existing sentence
  `ranges`/word-boundary logic as "collapsed / one word / multi-word", reusing
  `extractWordAt`'s boundary rules.
- `ListenSelection` capability: synthesize/play TTS for an arbitrary substring. Needs
  investigation of the existing TTS/audio-chunk pipeline (`ReadingViewModel` / whatever
  currently drives per-sentence playback) to determine the smallest correct way to play an
  arbitrary text range — this is explicitly called out as a "Technical Investigation" item
  for the implementation plan, not resolved in this spec.

No changes to `DictionarySheet.kt`, `AssistantSheet.kt`, or `LlmModelSetupSheet.kt` (already
fixed this session for the navigation-bar-padding and scroll issues). No changes outside
`ReadingScreen.kt` plus the two new small files above, except whatever the TTS
investigation above turns up.

## 6. Testing

- Unit test the selection-classification function (collapsed / one word / multi-word,
  including apostrophe/hyphen boundary cases matching `extractWordAtTest` equivalents).
- Manual QA on-device (this is a Compose UI/gesture change that unit tests can't fully
  cover): single tap still plays the right sentence; scrolling doesn't trigger playback or
  selection; long-press selects a word; dragging a handle extends to a phrase/sentence;
  Copy produces the right clipboard content; Listen plays the right audio; selection is
  visually distinct from the playback highlight; behavior holds with mixed French/Persian
  text and larger font-scale settings.

## 7. Out of scope / explicitly deferred (tracked for later specs)

- Translate, Ask AI, Save vocabulary, Share, Search toolbar actions.
- AI-assistant redesign (next spec after this one, per user's explicit request this
  session — the assistant currently only works on a whole sentence, has only 4 fixed
  actions, and has no free-form chat).
- `ACTION_PROCESS_TEXT` / external app integration.
- New visual design system / editorial color tokens beyond the one new selection-highlight
  color needed here.
