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
