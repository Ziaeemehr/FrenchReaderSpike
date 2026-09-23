# Shadowing mode — design

Date: 2026-09-23
Status: approved; revised 2026-09-23 (pace score + per-text summary)

## Goal

Let the learner practice speaking with the text they are reading: the app plays
one sentence with TTS, the learner repeats it, and the app shows which words were
recognized (pronunciation feedback) and lets them compare their recording with
the original (fluency/rhythm practice). Each sentence gets a word-accuracy
score and a pace rating, and finishing a session shows a summary for the text.

## Decisions

| Topic | Decision |
|---|---|
| Purpose | Both pronunciation feedback and fluency practice |
| Recognition | Two engines behind a setting: Vosk (offline, default) and Android `SpeechRecognizer` |
| Loop | Manual: user holds the mic button to speak; result stays until Next / Retry |
| Persistence | Per-sentence score and study time are stored; recordings are never stored |
| Placement | Inside the reading screen (no separate section) |
| Rhythm | Simple pace measure: speech length (silence trimmed) vs. the TTS sentence length |
| End of text | Per-text session summary: accuracy, pace, weakest sentences |

## User experience

- The reading screen's player bar gets a **Shadow** toggle (mic icon).
- Turning it on pauses normal continuous playback. Playing now stops at the end
  of each sentence. The current sentence stays highlighted as it is today.
- A **shadowing panel** appears at the bottom of the reading screen, below the text:
  - The current sentence's words, colored after an attempt: green = matched,
    red = missing or misrecognized. Before an attempt they are neutral.
  - Score `matched / total` (e.g. `7 / 9`).
  - Pace line, when a recording is available: *good pace (1.1×)*,
    *a bit slow (1.4×)* or *a bit fast (0.7×)*.
  - Buttons: **Play original** (replays the TTS sentence), **Replay mine**,
    **Retry** (clears the result), **Next** (advances with the existing
    `nextSentence`, then plays it).
  - A large **hold-to-speak** mic button: recording runs while pressed and
    recognition runs on release.
- The mic button is disabled while TTS audio is playing, and TTS is paused while
  recording, so the two never overlap.
- Tapping a sentence in the text while in shadowing mode jumps to that sentence
  (existing `seekToSentence`) and plays it.
- Turning the toggle off, or pressing Next on the last sentence, ends the
  session. If at least one attempt was saved in it, a **summary dialog** for
  this text appears:
  - sentences practiced and word accuracy (word-weighted over the latest attempt
    per sentence in this session)
  - average pace ratio and the share of sentences at a good pace
  - up to 3 weakest sentences (lowest accuracy below 100%). Tapping one turns
    shadowing back on at that sentence.
- Closing the summary hides the panel and restores normal playback.

## Pace measure

- Only computed when the app has the PCM (Vosk, or Android engine on API 33+).
  Otherwise the pace is absent and only accuracy is shown.
- Speech length: split the PCM into 20 ms frames and compute RMS for each. A frame
  counts as speech when its RMS is above `max(300, 0.15 × loudest frame RMS)`.
  Speech length runs from the first speech frame to the last one, so silence
  before and after speaking is not counted.
- Expected length: the TTS sentence duration divided by the current playback speed.
- `ratio = speech / expected`. Below 0.8 is *a bit fast*, 0.8–1.3 is *good pace*,
  and above 1.3 is *a bit slow*.
- Speech shorter than 300 ms gives no pace.

## Architecture

New package `com.ziaee.frenchreader.shadowing`:

- **`MicRecorder`**: records mono 16 kHz PCM with `AudioRecord` into an
  in-memory buffer (capped at 30 s). It plays the buffer back with `AudioTrack`
  for **Replay mine**, and the buffer is discarded on Retry/Next.
- **`SpeechEngine`** interface:
  `suspend fun recognize(pcm: ShortArray, sampleRate: Int): String`, plus
  `val canUseRecordedAudio: Boolean`.
  - **`VoskEngine`** (default): runs the Vosk French model on the recorded
    buffer. Fully offline.
  - **`AndroidSpeechEngine`**: on API 33+ it passes the recorded audio via
    `EXTRA_AUDIO_SOURCE`, so replay works. On API < 33 the recognizer takes the
    mic itself, recording happens inside the engine, and **Replay mine** is
    hidden. Language `fr-FR`, `EXTRA_PREFER_OFFLINE = true`.
- **`TranscriptAligner`** (pure Kotlin, no Android deps):
  - Normalizes both strings: lowercase, strip accents and punctuation, split
    elisions (`l'`, `j'`, `qu'`, `c'`, `d'`, `n'`, `s'`, `t'`, `m'`) into
    separate tokens, collapse whitespace.
  - Word-level Levenshtein alignment. It returns one `WordResult(originalWord,
    matched)` per original word, plus `matched` and `total`.
  - A word counts as matched when the normalized tokens are equal.
- **`VoskModelManager`**: on first use of the Vosk engine it asks for confirmation,
  then downloads the small French model (about 40 MB) into `filesDir/vosk/` with
  a progress indicator. It verifies and unpacks the model, and exposes
  `isInstalled`, `download()` and `delete()`.
- **`ShadowingViewModel` state** (held by `ReadingViewModel` or a sibling VM
  scoped to the reading screen): `enabled`, `phase` (Idle / PlayingOriginal /
  Recording / Recognizing / Result / Error), `result`, `hasRecording`.

Reading-screen changes stay small: a toggle in the player bar, a
stop-at-sentence-end flag in playback, and the new panel composable
(`ShadowingPanel.kt`), kept in its own file.

## Data

- New Room entity `ShadowAttempt(id, textId, chunkIndex, sentenceIndex, matched,
  total, paceRatio?, engine, timestamp)` plus a DAO. `SCHEMA_VERSION` + 1 with a migration.
- Only attempts with a non-empty transcript are stored.
- Shadowing time (from pressing the mic until the result is shown) is added to the
  daily study time used by the review screen (`VocabPrefs` study-time counters).
- Statistics screen: a **Shadowing** card with sentences practiced, average
  accuracy, and a 7-day trend.
- Backups include the table automatically, since it is in the same database.

## Settings

A new "Shadowing" group:
- Recognition engine: Vosk (offline) / Android.
- Vosk model: status, download, and delete with the size shown.

## Permissions and errors

- Add `RECORD_AUDIO` and request it the first time Shadow is turned on. If it is
  denied, show a short explanation and turn the toggle back off.
- Vosk model missing and no network: offer to switch to the Android engine.
- Android engine unavailable (`SpeechRecognizer.isRecognitionAvailable` false):
  hide the option in Settings.
- Empty transcript: show "Didn't catch that — try again". Save nothing.
- Recording shorter than 300 ms: ignore it (treat as an accidental tap).
- `PRIVACY.md`: describe both engines. Vosk audio never leaves the device. The
  Android engine may send audio to Google unless offline French is installed.
  Recordings are never saved.

## Out of scope

Automatic silence detection, phoneme-level scoring, saving recordings,
shadowing from vocab review cards, a separate practice section.

## Testing

- Unit tests for the pace analyzer (silence padding, quiet noise, missing PCM)
  and the per-text summary (latest attempt wins, weakest ordering, no pace data).
- Unit tests for `TranscriptAligner`: exact match, elisions (`l'avion`), accents,
  punctuation, extra words, missing words, empty transcript.
- DAO test and Room migration test for the new schema version.
- Manual check on the phone with a signed release build (`assembleRelease` +
  `adb install -r`): permission flow, Vosk download, both engines, replay, and
  stats card.
