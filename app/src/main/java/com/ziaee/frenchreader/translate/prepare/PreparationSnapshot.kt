package com.ziaee.frenchreader.translate.prepare

import org.json.JSONArray
import org.json.JSONObject

/** Explicit nulls preserve meaning-cache identity: an empty sentence differs from a null sentence. */
internal fun encodePreparationJob(job: PreparationJob): String {
    val array = JSONArray()
    job.items.forEach { item -> array.put(JSONObject().put("text", item.text).put("language", item.language)
        .put("kind", item.kind.name).put("before", item.before ?: JSONObject.NULL)
        .put("after", item.after ?: JSONObject.NULL).put("group", item.group).put("position", item.position)
        .put("sentence", item.sentence ?: JSONObject.NULL).put("deckId", item.deckId ?: JSONObject.NULL)) }
    val decks = job.selectedDecks?.let { ids -> JSONArray().apply { ids.forEach { put(it ?: JSONObject.NULL) } } }
    return JSONObject().put("id", job.id).put("model", job.model).put("items", array)
        .put("selectedDecks", decks ?: JSONObject.NULL).toString()
}

internal fun decodePreparationJob(raw: String): PreparationJob {
    val json = JSONObject(raw)
    val array = json.getJSONArray("items")
    val decks = if (json.isNull("selectedDecks")) null else json.getJSONArray("selectedDecks").let { ids ->
        (0 until ids.length()).map { if (ids.isNull(it)) null else ids.getLong(it) }.toSet()
    }
    return PreparationJob(json.getString("id"), json.getString("model"), (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        fun optional(key: String) = if (item.isNull(key)) null else item.getString(key)
        PreparationItem(item.getString("text"), item.getString("language"),
            PreparationKind.valueOf(item.getString("kind")), optional("before"), optional("after"),
            item.getString("group"), item.getInt("position"), optional("sentence"),
            if (item.isNull("deckId")) null else item.getLong("deckId"))
    }, decks)
}

/** A missing preference predates deck selection; an explicitly empty set means no decks. */
internal fun decodePreparationDeckSelection(values: Set<String>?): Set<Long?>? =
    values?.map { if (it == "unfiled") null else it.toLong() }?.toSet()

internal fun encodePreparationDeckSelection(ids: Set<Long?>?): Set<String>? =
    ids?.map { it?.toString() ?: "unfiled" }?.toSet()
