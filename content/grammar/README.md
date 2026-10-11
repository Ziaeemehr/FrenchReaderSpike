# Grammar content

Source files for the in-app grammar reference. One note per file in `notes/<id>.md`.
The app does not read these files yet; a build script will compile them to JSON later.

## Note format

Front matter (YAML):

| Key | Meaning |
| --- | --- |
| `id` | Stable kebab-case id, equal to the file name. Never rename after publishing. |
| `group` | `basics`, `tenses`, `pronouns`, `structures` or `comparisons`. |
| `level` | Suggested level (`A1`–`B2`); a study hint, not an official classification. |
| `title_fr`, `title_fa` | Searchable titles. |
| `aliases_fr`, `aliases_fa` | Alternative names and common questions, for search. |
| `related` | Ids of related notes. Must exist in `notes/`. |
| `status` | `draft`, `reviewed` or `needs-fix`. Only `reviewed` is published. |
| `review_note`, `review_group` | Date of the per-note and the end-of-group review, or `null`. |

Body: fixed `##` sections, in this order. `mistake`, `more` and `infographic` are optional.

- `## summary` — the quick answer, two or three lines, Persian.
- `## usage` — when it is used and what it means, Persian.
- `## structure` — the pattern in a code span, then a short Persian explanation.
- `## examples` — three to five examples. Each is a `### exN` heading followed by an
  `FR:` line and an `FA:` line. The target part of the French sentence is wrapped in `**…**`.
- `## mistake` — `WRONG:`, `RIGHT:` and `WHY:` lines (`WHY:` in Persian).
- `## more` — exceptions and contrasts with similar structures, Persian.
- `## infographic` — a `type:` line (`comparison`, `timeline`, `decision` or `steps`)
  followed by a Markdown table holding the data.

Example ids (`ex1`, `ex2`, …) are stable within a note; translations attach to them.

## Review checklist

Per note:

1. Every French sentence is correct and natural (spelling, accents, agreement, elision).
2. The rule as stated is true; nothing important is missing or overgeneralised.
3. The Persian explanation is accurate, clear and natural, and each `FA:` line matches its `FR:` line.
4. The `**…**` highlight marks exactly the part the note is about.
5. The `WRONG:` sentence is really wrong, for the reason given in `WHY:`.
6. The format above is followed and every `related` id exists.

End of group:

1. The same Persian term is used for the same concept in every note.
2. No two notes contradict each other.
3. Notes that should link to each other do.
