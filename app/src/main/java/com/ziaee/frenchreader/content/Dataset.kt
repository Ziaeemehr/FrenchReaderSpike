package com.ziaee.frenchreader.content

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
    lesson?.takeIf(String::isNotBlank)?.let { "$meaning — Leçon $it" } ?: meaning
