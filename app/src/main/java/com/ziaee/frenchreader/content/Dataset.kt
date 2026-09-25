package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.data.VocabEntry
import org.json.JSONArray
import org.json.JSONObject

data class DatasetDeckPack(
    val id: String,
    val name: String,
    val level: String,
    val kind: String,
    val file: String,
    val count: Int
)

data class DatasetStoryPack(
    val id: String,
    val name: String,
    val level: String,
    val source: String,
    val file: String,
    val count: Int
)

data class DatasetManifest(
    val version: Int,
    val decks: List<DatasetDeckPack>,
    val stories: List<DatasetStoryPack>
)

data class DatasetDeckRow(
    val word: String,
    val meaning: String,
    val sentence: String,
    val lesson: String?
)

data class DatasetStoryRow(
    val key: String,
    val title: String,
    val body: String,
    val sourceUrl: String?,
    val image: String?
)

internal fun parseDatasetManifest(json: String): DatasetManifest {
    val root = JSONObject(json)
    val decks = root.getJSONArray("decks").mapObjects { deck ->
        DatasetDeckPack(
            id = deck.getString("id"),
            name = deck.getString("name"),
            level = deck.getString("level"),
            kind = deck.getString("kind"),
            file = deck.getString("file"),
            count = deck.getInt("count")
        )
    }
    val stories = root.getJSONArray("stories").mapObjects { story ->
        DatasetStoryPack(
            id = story.getString("id"),
            name = story.getString("name"),
            level = story.getString("level"),
            source = story.getString("source"),
            file = story.getString("file"),
            count = story.getInt("count")
        )
    }
    return DatasetManifest(root.getInt("version"), decks, stories)
}

internal fun parseDatasetDeck(json: String): List<DatasetDeckRow> =
    JSONArray(json).mapObjects { row ->
        DatasetDeckRow(
            word = row.getString("w"),
            meaning = row.getString("m"),
            sentence = row.optString("s", ""),
            lesson = row.optString("l").takeIf(String::isNotBlank)
        )
    }

internal fun parseDatasetStories(json: String): List<DatasetStoryRow> =
    JSONArray(json).mapObjects { row ->
        DatasetStoryRow(
            key = row.getString("key"),
            title = row.getString("title"),
            body = row.getString("body"),
            sourceUrl = row.optString("sourceUrl").takeIf(String::isNotBlank),
            image = row.optString("image").takeIf(String::isNotBlank)
        )
    }

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { transform(getJSONObject(it)) }

internal fun datasetMeaning(meaning: String, lesson: String?): String =
    lesson?.takeIf { it.isNotBlank() && meaning.isNotBlank() }?.let { "$meaning — Leçon $it" } ?: meaning

/** Uncategorized dataset cards to delete, and ones to move back into their (re-added) deck. */
internal data class DatasetCardCleanup(val deleteIds: Set<Long>, val moveToList: Map<Long, Long>) {
    val removed get() = deleteIds.size
}

/**
 * Uncategorized ([VocabEntry.listId] null, textId 0) copies of dataset deck cards, left behind when a
 * deck list was deleted. [packs] pairs each deck's rows with its current list id (null = list gone).
 * - list gone: the copy is deleted.
 * - list exists: the copy is a duplicate. If it has review progress and the deck's copy has none, it
 *   replaces the deck's copy (keeping progress); otherwise it is deleted.
 * - the legacy swapped form of the old gram-dial-b1-phrases deck (French word, Persian meaning) is
 *   treated the same way, but only merges into a deck that still holds that legacy card; otherwise
 *   (list gone, or the deck was re-added Persian-first) it is deleted.
 */
internal fun planDatasetCardCleanup(
    packs: List<Pair<List<DatasetDeckRow>, Long?>>,
    entries: List<VocabEntry>
): DatasetCardCleanup {
    val deleteIds = mutableSetOf<Long>()
    val moves = mutableMapOf<Long, Long>()
    val loose = entries.filter { it.listId == null && it.textId == 0L }
    packs.forEach { (rows, listId) ->
        val current = HashSet<Pair<String, String>>()
        val legacy = HashSet<Pair<String, String>>()
        rows.forEach { row ->
            current += row.word.lowercase() to datasetMeaning(row.meaning, row.lesson)
            if (row.sentence.isNotBlank()) legacy += row.sentence.lowercase() to datasetMeaning(row.word, row.lesson)
        }
        val inDeck = listId?.let { id ->
            entries.filter { it.listId == id }.associateByTo(mutableMapOf()) { it.word.lowercase() }
        }
        loose.forEach { entry ->
            if (entry.id in deleteIds || entry.id in moves) return@forEach
            val key = entry.word.lowercase() to entry.meaning
            val isLegacy = key in legacy
            when {
                !isLegacy && key !in current -> Unit
                listId == null || inDeck == null -> deleteIds += entry.id
                isLegacy && key.first !in inDeck -> deleteIds += entry.id
                else -> {
                    val occupant = inDeck[key.first]
                    if (occupant == null || (entry.hasProgress() && !occupant.hasProgress())) {
                        occupant?.let { deleteIds += it.id }
                        moves[entry.id] = listId
                        inDeck[key.first] = entry
                    } else {
                        deleteIds += entry.id
                    }
                }
            }
        }
    }
    return DatasetCardCleanup(deleteIds, moves)
}

private fun VocabEntry.hasProgress() = lastReviewedAtMs != null || leitnerBox > 1
