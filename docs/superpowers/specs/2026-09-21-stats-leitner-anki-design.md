# Statistics, Leitner redesign, Anki import — design

Four sub-projects, built in order, each with its own plan, tests and commit.
Existing pieces: `VocabSrs` (5 boxes, intervals 1/2/4/8/16d), `review_log`,
`activity_log`, `VocabEntry`, `VocabList`, `ui/statistics/`, `ui/VocabReviewScreen.kt`.

## 1. Statistics
More charts from existing data, no schema change.
- Review heatmap calendar, cards per Leitner box, 30-day due forecast,
  accuracy trend, words added per week, listening minutes per day, retention by box.
- Queries live in DAOs; aggregation in pure functions in `StatisticsUiState` for unit testing.

## 2. Leitner redesign
- Box overview (boxes 1-5 with counts; tap a box to review it).
- Card flip animation, session progress bar, end-of-session summary.
- Daily review goal; editable intervals (feeds `VocabSrs` `intervalDays`).
- Shares visual style with the new stats screen.

## 3. Anki import (exported file)
- `tools/anki_export.py` reads AnkiConnect (localhost:8765, read-only actions)
  and writes JSON: per note `deck`, `front`, `back`, `tags`, plus scheduling
  (`interval`, `due`, `reps`, `lapses`).
- Audio is NOT imported: `[sound:...]` tags are stripped; only front/back text is kept, HTML cleaned.
- App imports the JSON via the file picker. Each Anki deck becomes a `VocabList`;
  front -> `word`; back -> `meaning` (and `sentence` when an example line exists,
  e.g. TCF Vocabulary's EN/FA/italic-example layout). Duplicates skipped.

## 4. Progress import
- Optional "keep Anki progress" toggle at import.
- `interval` -> `leitnerBox`: 1d->1, 2-3d->2, 4-7d->3, 8-15d->4, 16d+->5;
  `due` -> `nextReviewAtMs`; new cards stay box 1 / NEW.
- Per-answer history is unavailable (AnkiConnect returned no revlog rows), so stats
  for imported cards start at the import date.

## Constraints
Implementation via `codex exec` at low effort; diffs and builds verified by Claude.
