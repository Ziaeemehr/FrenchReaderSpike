# Anki Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import Anki decks (front/back text only, no audio) into the app's vocab lists, optionally keeping Anki's review progress mapped onto the 5 Leitner boxes.

**Architecture:** A Python script (`tools/anki_export.py`) reads AnkiConnect on the computer and writes a versioned JSON file. The app parses it with pure, unit-tested functions (`AnkiImport.kt`), then a thin repository writes `VocabList`/`VocabEntry` rows. A dialog on the Vocab list screen picks the file and the "keep Anki progress" toggle. No schema change.

**Tech Stack:** Python 3 (stdlib only), Kotlin, Room, org.json (already a test dependency; built into Android), Compose.

**Spec:** `docs/superpowers/specs/2026-09-21-stats-leitner-anki-design.md` (sections 3 and 4)

## Global Constraints

- Audio is NOT imported: `[sound:...]` tags are stripped; only front/back text is kept.
- No Room schema change / no migration (DB stays at version 11).
- Every new user-visible string goes in `values/`, `values-fa/`, `values-fr/` strings.xml with identical keys (`LocalizationCompletenessTest`); no hardcoded Persian literals in Kotlin.
- Box mapping from Anki `interval` (days): 1d->1 (and anything <2), 2-3d->2, 4-7d->3, 8-15d->4, 16d+->5.
- Only Anki cards of `type == 2` (review) get their box/due from Anki; `type` 1 or 3 (learning/relearning) -> box 1, lastReviewed=now, due now; `type` 0 (new) -> defaults (box 1, never reviewed).
- Imported words use `textId = 0L`; the review screen already falls back to default voice when `textDao().getById` is null.
- Duplicates are skipped: same list + same word (case-insensitive).
- Implementation via `codex exec -c model_reasoning_effort="low"` when practical; Claude verifies diffs and builds. Unit tests: `./gradlew :app:testDebugUnitTest --tests "<class>"`.

## JSON contract (version 1)

```json
{"version": 1, "todayDay": 1030,
 "cards": [{"deck": "TCF Vocabulary", "front": "<html>", "back": "<html>",
            "type": 2, "interval": 48, "due": 1078, "reps": 6, "lapses": 0}]}
```
`todayDay` is Anki's current day number (may be `null`); for `type == 2` cards, `due - todayDay` is days until due (negative = overdue).

---

### Task 1: Export script

**Files:**
- Create: `tools/anki_export.py`
- Create: `tools/README_anki_export.md`

**Interfaces:**
- Produces: a JSON file in the contract above. CLI: `python3 tools/anki_export.py --out anki.json [--deck "TCF Vocabulary"]...` (no `--deck` = all decks; a deck name includes its subdecks).

- [ ] **Step 1: Write `tools/anki_export.py`**

```python
#!/usr/bin/env python3
"""Export Anki decks to a JSON file for the French Reader app (AnkiConnect, read-only)."""
import argparse
import json
import sys
import urllib.request

URL = "http://localhost:8765"


def call(action, **params):
    body = json.dumps({"action": action, "version": 6, "params": params}).encode()
    with urllib.request.urlopen(urllib.request.Request(URL, body), timeout=60) as r:
        reply = json.load(r)
    if reply.get("error"):
        sys.exit(f"AnkiConnect error in {action}: {reply['error']}")
    return reply["result"]


def today_day():
    """Anki's current day number, read off any card due today; None if there is none."""
    ids = call("findCards", query="prop:due=0")
    for card in call("cardsInfo", cards=ids[:20]):
        if card["type"] == 2:
            return card["due"]
    return None


def card_row(card):
    fields = sorted(card["fields"].values(), key=lambda f: f["order"])
    front = fields[0]["value"] if fields else ""
    back = "<br>".join(f["value"] for f in fields[1:])
    return {
        "deck": card["deckName"], "front": front, "back": back,
        "type": card["type"], "interval": card["interval"], "due": card["due"],
        "reps": card["reps"], "lapses": card["lapses"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--deck", action="append", help="deck name (repeatable); default: all decks")
    args = parser.parse_args()

    decks = args.deck or call("deckNames")
    seen, rows = set(), []
    for deck in decks:
        ids = call("findCards", query=f'deck:"{deck}"')
        for i in range(0, len(ids), 500):
            for card in call("cardsInfo", cards=ids[i:i + 500]):
                if card["cardId"] not in seen:
                    seen.add(card["cardId"])
                    rows.append(card_row(card))
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "todayDay": today_day(), "cards": rows}, f, ensure_ascii=False)
    print(f"Wrote {len(rows)} cards to {args.out}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Write `tools/README_anki_export.md`** (5-10 lines): Anki must be open with the AnkiConnect add-on; run the command above; copy the JSON to the phone; in the app open Vocabulary > Import from Anki.

- [ ] **Step 3: Verify against the live Anki** (Anki is open on the dev machine)

Run: `python3 tools/anki_export.py --deck "TCF Vocabulary" --out /tmp/tcf.json && python3 -c "import json;d=json.load(open('/tmp/tcf.json'));print(d['version'],d['todayDay'],len(d['cards']));print(d['cards'][0])"`
Expected: `1 <int or None> 565` and a card dict whose `front` contains `[sound:` and `back` contains `<b>EN:</b>`.

- [ ] **Step 4: Commit** `git add tools && git commit -m "feat(anki): add AnkiConnect export script"`

---

### Task 2: Pure parsing, mapping and progress logic

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/content/AnkiImport.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/content/AnkiImportTest.kt`

**Interfaces:**
- Consumes: `VocabEntry`, `VocabSrs.dueAtMs(box: Int, nowMs: Long, intervals: List<Long>, zoneId: ZoneId)`, `VocabSrs.DEFAULT_INTERVAL_DAYS`, `VocabSrs.startOfDayMs(nowMs, zoneId)`, `VocabSrs.DAY_MS`.
- Produces:
  - `data class AnkiCard(val deck: String, val front: String, val back: String, val type: Int, val interval: Int, val due: Int, val reps: Int, val lapses: Int)`
  - `data class AnkiExport(val todayDay: Int?, val cards: List<AnkiCard>)`
  - `internal fun parseAnkiExport(json: String): AnkiExport` (throws `IllegalArgumentException` if `version != 1`)
  - `internal fun cleanAnkiText(html: String): String`
  - `data class MappedAnkiCard(val word: String, val meaning: String?, val sentence: String)`
  - `internal fun mapAnkiCard(card: AnkiCard): MappedAnkiCard?` (null when the front is blank)
  - `internal fun boxForInterval(days: Int): Int`
  - `internal fun buildVocabEntry(card: AnkiCard, mapped: MappedAnkiCard, listId: Long, keepProgress: Boolean, todayDay: Int?, nowMs: Long, dictionaryUrl: String, zone: ZoneId = ZoneId.systemDefault()): VocabEntry`
  - `data class AnkiImportPlan(val newListNames: List<String>, val words: List<PlannedAnkiWord>, val skipped: Int)` and `data class PlannedAnkiWord(val listName: String, val card: AnkiCard, val mapped: MappedAnkiCard)`
  - `internal fun planAnkiImport(export: AnkiExport, existingWordKeys: Set<Pair<String, String>>): AnkiImportPlan` where a key is `(listName.lowercase(), word.lowercase())`; also dedupes within the file itself.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.ziaee.frenchreader.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AnkiImportTest {
    private val front = "au moment opportun<br>[sound:a.mp3]"
    private val back = "<div><b>EN:</b> at the right moment</div>\n<div><b>FA:</b> در زمان مناسب</div>\n" +
        "<div style=\"margin-top:8px\">Il est venu au moment opportun.</div><br>[sound:b.mp3]"

    private fun card(type: Int = 2, interval: Int = 20, due: Int = 1050, front: String = this.front, back: String = this.back) =
        AnkiCard("TCF Vocabulary", front, back, type, interval, due, reps = 6, lapses = 0)

    @Test fun `cleanAnkiText strips sound tags html and entities`() {
        assertEquals("au moment opportun", cleanAnkiText(front))
        assertEquals("a & b\nc", cleanAnkiText("<div>a&nbsp;&amp; b</div><div> c </div>[sound:x.mp3]"))
    }

    @Test fun `mapAnkiCard splits EN FA and example sentence`() {
        val m = mapAnkiCard(card())!!
        assertEquals("au moment opportun", m.word)
        assertEquals("at the right moment\nدر زمان مناسب", m.meaning)
        assertEquals("Il est venu au moment opportun.", m.sentence)
    }

    @Test fun `mapAnkiCard without EN FA puts whole back in meaning`() {
        val m = mapAnkiCard(card(front = "un dentiste", back = "la dentisterie / la profession"))!!
        assertEquals("la dentisterie / la profession", m.meaning)
        assertEquals("", m.sentence)
    }

    @Test fun `mapAnkiCard returns null for blank front`() {
        assertNull(mapAnkiCard(card(front = "[sound:a.mp3]")))
    }

    @Test fun `boxForInterval boundaries`() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5),
            listOf(0, 1, 2, 3, 4, 7, 8, 15, 16, 400).map { boxForInterval(it) })
    }

    private val now = 1_800_000_000_000L
    private fun entry(c: AnkiCard, keep: Boolean, todayDay: Int? = 1030) =
        buildVocabEntry(c, mapAnkiCard(c)!!, listId = 7, keepProgress = keep, todayDay = todayDay,
            nowMs = now, dictionaryUrl = "u", zone = ZoneOffset.UTC)

    @Test fun `review card keeps box and due date when keepProgress`() {
        val e = entry(card(type = 2, interval = 20, due = 1050), keep = true)
        assertEquals(5, e.leitnerBox)
        assertEquals(now, e.lastReviewedAtMs)
        val startOfDay = now - now % 86_400_000L
        assertEquals(startOfDay + 20 * 86_400_000L, e.nextReviewAtMs)
        assertEquals(7L, e.listId)
        assertEquals(0L, e.textId)
    }

    @Test fun `review card without todayDay falls back to box interval`() {
        val e = entry(card(type = 2, interval = 3, due = 99), keep = true, todayDay = null)
        assertEquals(2, e.leitnerBox)
        val startOfDay = now - now % 86_400_000L
        assertEquals(startOfDay + 2 * 86_400_000L, e.nextReviewAtMs)
    }

    @Test fun `new learning and no-progress cards use defaults or box 1`() {
        val fresh = entry(card(type = 0), keep = true)
        assertEquals(1, fresh.leitnerBox); assertNull(fresh.lastReviewedAtMs)
        val learning = entry(card(type = 1), keep = true)
        assertEquals(1, learning.leitnerBox); assertEquals(now, learning.lastReviewedAtMs); assertEquals(now, learning.nextReviewAtMs)
        val ignored = entry(card(type = 2, interval = 40), keep = false)
        assertEquals(1, ignored.leitnerBox); assertNull(ignored.lastReviewedAtMs)
    }

    @Test fun `parseAnkiExport reads cards and rejects other versions`() {
        val json = """{"version":1,"todayDay":null,"cards":[{"deck":"D","front":"f","back":"b","type":2,"interval":5,"due":9,"reps":1,"lapses":0}]}"""
        val e = parseAnkiExport(json)
        assertNull(e.todayDay); assertEquals(1, e.cards.size); assertEquals("D", e.cards[0].deck)
        assertTrue(runCatching { parseAnkiExport("""{"version":2,"cards":[]}""") }.isFailure)
    }

    @Test fun `plan skips duplicates blanks and existing words`() {
        val a = card(); val dup = card(); val blank = card(front = "[sound:a.mp3]")
        val b = card(front = "un dentiste", back = "x")
        val plan = planAnkiImport(AnkiExport(1030, listOf(a, dup, blank, b)),
            existingWordKeys = setOf("tcf vocabulary" to "un dentiste"))
        assertEquals(listOf("TCF Vocabulary"), plan.newListNames)
        assertEquals(1, plan.words.size)
        assertEquals(3, plan.skipped)
    }
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests "com.ziaee.frenchreader.content.AnkiImportTest"` — expect FAIL (unresolved references).

- [ ] **Step 3: Implement `AnkiImport.kt`**

```kotlin
package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabSrs
import org.json.JSONObject
import java.time.ZoneId

data class AnkiCard(
    val deck: String, val front: String, val back: String,
    val type: Int, val interval: Int, val due: Int, val reps: Int, val lapses: Int
)
data class AnkiExport(val todayDay: Int?, val cards: List<AnkiCard>)
data class MappedAnkiCard(val word: String, val meaning: String?, val sentence: String)
data class PlannedAnkiWord(val listName: String, val card: AnkiCard, val mapped: MappedAnkiCard)
data class AnkiImportPlan(val newListNames: List<String>, val words: List<PlannedAnkiWord>, val skipped: Int)

private val SOUND = Regex("""\[sound:[^\]]*]""")
private val BREAKS = Regex("""<\s*(br|/div|/p)\s*/?>""", RegexOption.IGNORE_CASE)
private val TAGS = Regex("<[^>]+>")
private val GLOSS = Regex("""^(EN|FA):\s*(.*)$""")

internal fun parseAnkiExport(json: String): AnkiExport {
    val root = JSONObject(json)
    require(root.optInt("version", 0) == 1) { "Unsupported Anki export version" }
    val array = root.getJSONArray("cards")
    val cards = (0 until array.length()).map { i ->
        val c = array.getJSONObject(i)
        AnkiCard(
            deck = c.getString("deck"), front = c.getString("front"), back = c.getString("back"),
            type = c.getInt("type"), interval = c.getInt("interval"), due = c.getInt("due"),
            reps = c.getInt("reps"), lapses = c.getInt("lapses")
        )
    }
    return AnkiExport(if (root.isNull("todayDay")) null else root.getInt("todayDay"), cards)
}

internal fun cleanAnkiText(html: String): String {
    val text = TAGS.replace(BREAKS.replace(SOUND.replace(html, ""), "\n"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&amp;", "&")
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}

internal fun mapAnkiCard(card: AnkiCard): MappedAnkiCard? {
    val word = cleanAnkiText(card.front).lines().joinToString(" ").trim()
    if (word.isEmpty()) return null
    val lines = cleanAnkiText(card.back).lines().filter { it.isNotEmpty() }
    val gloss = lines.mapNotNull { GLOSS.matchEntire(it)?.groupValues?.get(2)?.takeIf { v -> v.isNotBlank() } }
    if (gloss.isEmpty()) {
        return MappedAnkiCard(word, lines.joinToString("\n").ifEmpty { null }, "")
    }
    val sentence = lines.filter { GLOSS.matchEntire(it) == null }.joinToString(" ")
    return MappedAnkiCard(word, gloss.joinToString("\n"), sentence)
}

internal fun boxForInterval(days: Int): Int = when {
    days >= 16 -> 5
    days >= 8 -> 4
    days >= 4 -> 3
    days >= 2 -> 2
    else -> 1
}

internal fun buildVocabEntry(
    card: AnkiCard, mapped: MappedAnkiCard, listId: Long, keepProgress: Boolean,
    todayDay: Int?, nowMs: Long, dictionaryUrl: String, zone: ZoneId = ZoneId.systemDefault()
): VocabEntry {
    val base = VocabEntry(
        word = mapped.word, sentence = mapped.sentence, textId = 0L, dictionaryUrl = dictionaryUrl,
        meaning = mapped.meaning, createdAtMs = nowMs, listId = listId,
        leitnerBox = 1, nextReviewAtMs = nowMs, lastReviewedAtMs = null
    )
    if (!keepProgress) return base
    return when (card.type) {
        2 -> {
            val box = boxForInterval(card.interval)
            val dueMs = if (todayDay != null) {
                VocabSrs.startOfDayMs(nowMs, zone) + (card.due - todayDay) * VocabSrs.DAY_MS
            } else {
                VocabSrs.dueAtMs(box, nowMs, VocabSrs.DEFAULT_INTERVAL_DAYS, zone)
            }
            base.copy(leitnerBox = box, nextReviewAtMs = dueMs, lastReviewedAtMs = nowMs)
        }
        1, 3 -> base.copy(lastReviewedAtMs = nowMs)
        else -> base
    }
}

internal fun planAnkiImport(export: AnkiExport, existingWordKeys: Set<Pair<String, String>>): AnkiImportPlan {
    val seen = existingWordKeys.toMutableSet()
    val words = mutableListOf<PlannedAnkiWord>()
    val listNames = linkedSetOf<String>()
    var skipped = 0
    for (card in export.cards) {
        val mapped = mapAnkiCard(card)
        val key = mapped?.let { card.deck.lowercase() to it.word.lowercase() }
        if (mapped == null || key in seen) { skipped++; continue }
        seen += key!!
        listNames += card.deck
        words += PlannedAnkiWord(card.deck, card, mapped)
    }
    return AnkiImportPlan(listNames.toList(), words, skipped)
}
```

- [ ] **Step 4: Run tests, expect PASS** (same command; then the whole `content` package).
- [ ] **Step 5: Commit** `git add app/src && git commit -m "feat(anki): add parsing, mapping and progress logic"`

---

### Task 3: Repository and UI wiring

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/content/AnkiImportRepository.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/data/Daos.kt` (`VocabListDao`: add `getAllOnce`)
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/VocabListScreen.kt` (ViewModel + top-bar action + dialog)
- Modify: `app/src/main/res/values/strings.xml`, `values-fa/strings.xml`, `values-fr/strings.xml`
- Modify (only if private): `wordReferenceUrl` in `ui/DictionarySheet.kt` -> `internal`

**Interfaces:**
- Consumes: Task 2 functions/types; `VocabListDao.insert(list): Long`; `VocabDao.getAllOnce()`, `VocabDao.insert(entry): Long`; `AppDatabase.get(context)`; `wordReferenceUrl(word: String): String`.
- Produces: `VocabListDao.getAllOnce(): List<VocabList>` (`@Query("SELECT * FROM vocab_lists")`); `data class AnkiImportResult(val added: Int, val skipped: Int, val lists: Int)`; `class AnkiImportRepository(private val db: AppDatabase) { suspend fun import(json: String, keepProgress: Boolean, nowMs: Long = System.currentTimeMillis()): AnkiImportResult }`.

- [ ] **Step 1: DAO** — add to `VocabListDao`: `@Query("SELECT * FROM vocab_lists") suspend fun getAllOnce(): List<VocabList>`.

- [ ] **Step 2: Repository**

```kotlin
package com.ziaee.frenchreader.content

import androidx.room.withTransaction
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.VocabList
import com.ziaee.frenchreader.ui.wordReferenceUrl

data class AnkiImportResult(val added: Int, val skipped: Int, val lists: Int)

class AnkiImportRepository(private val db: AppDatabase) {
    suspend fun import(json: String, keepProgress: Boolean, nowMs: Long = System.currentTimeMillis()): AnkiImportResult {
        val export = parseAnkiExport(json)
        return db.withTransaction {
            val lists = db.vocabListDao().getAllOnce()
            val listIdByName = lists.associate { it.name.lowercase() to it.id }.toMutableMap()
            val nameById = lists.associate { it.id to it.name.lowercase() }
            val existingKeys = db.vocabDao().getAllOnce().mapNotNull { e ->
                e.listId?.let { nameById[it] }?.let { it to e.word.lowercase() }
            }.toSet()

            val plan = planAnkiImport(export, existingKeys)
            var newLists = 0
            for (name in plan.newListNames) {
                if (name.lowercase() !in listIdByName) {
                    listIdByName[name.lowercase()] = db.vocabListDao().insert(VocabList(name = name))
                    newLists++
                }
            }
            for (w in plan.words) {
                val listId = listIdByName.getValue(w.listName.lowercase())
                db.vocabDao().insert(
                    buildVocabEntry(w.card, w.mapped, listId, keepProgress, export.todayDay, nowMs, wordReferenceUrl(w.mapped.word))
                )
            }
            AnkiImportResult(added = plan.words.size, skipped = plan.skipped, lists = newLists)
        }
    }
}
```
(If `wordReferenceUrl` is `private`, make it `internal` in `DictionarySheet.kt`.)

- [ ] **Step 3: UI** — in `VocabListViewModel` add `fun importAnki(json: String, keepProgress: Boolean, onDone: (AnkiImportResult?) -> Unit)` that launches in `viewModelScope`, calls `AnkiImportRepository(db).import(...)`, and calls `onDone(result)` or `onDone(null)` on any exception. In `VocabListScreen`: an `Upload` icon action in the top bar (`Icons.Default.FileUpload` or nearest available) launching `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` with `arrayOf("application/json", "*/*")`; the picked `Uri` is held in state and shows an `AlertDialog` with a `Checkbox` row "Keep Anki progress" (default on) and Import/Cancel buttons; on Import read the file with `context.contentResolver.openInputStream(uri)?.bufferedReader().use { it.readText() }` on `Dispatchers.IO`, call `vm.importAnki`, and show a `Snackbar`-style result (reuse the screen's existing snackbar host if present, otherwise add a `SnackbarHostState` to the `Scaffold`): `anki_import_done` with added/skipped/lists, or `anki_import_failed`.

- [ ] **Step 4: Strings** (same keys in all three files)

| key | en | fa | fr |
|---|---|---|---|
| anki_import_action | Import from Anki | ایمپورت از انکی | Importer depuis Anki |
| anki_import_title | Import Anki deck | ایمپورت دک انکی | Importer un paquet Anki |
| anki_import_keep_progress | Keep Anki progress | نگه‌داشتن پیشرفت انکی | Conserver la progression Anki |
| anki_import_confirm | Import | ایمپورت | Importer |
| anki_import_done | Added %1$d words (%2$d skipped) in %3$d new lists | %1$d لغت اضافه شد (%2$d ردشده) در %3$d لیست جدید | %1$d mots ajoutés (%2$d ignorés) dans %3$d nouvelles listes |
| anki_import_failed | Could not read this Anki export file | خواندن فایل خروجی انکی ممکن نشد | Impossible de lire ce fichier d'export Anki |

- [ ] **Step 5: Verify** — `./gradlew :app:testDebugUnitTest :app:assembleDebug` (includes `LocalizationCompletenessTest`) must pass. Manual (needs a device; if unavailable, say so): export `/tmp/tcf.json` (Task 1), open Vocabulary > Import from Anki, expect a "TCF Vocabulary" list with ~565 words, EN/FA meanings, examples, and box/due carried over.

- [ ] **Step 6: Commit** `git commit -am "feat(anki): import Anki export into vocab lists"`
