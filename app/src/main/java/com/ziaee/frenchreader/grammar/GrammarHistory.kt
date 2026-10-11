package com.ziaee.frenchreader.grammar

import java.net.URLDecoder
import java.net.URLEncoder

data class GrammarRecent(val id: String, val openedAt: Long)
data class GrammarPrefsState(
    val favourites: Set<String> = emptySet(), val recents: List<GrammarRecent> = emptyList()
)

fun toggleGrammarFavourite(ids: Set<String>, id: String): Set<String> =
    if (id in ids) ids - id else ids + id

fun normalizeGrammarRecents(recents: List<GrammarRecent>): List<GrammarRecent> =
    recents.sortedByDescending { it.openedAt }.distinctBy { it.id }.take(20)

fun openGrammarRecent(recents: List<GrammarRecent>, id: String, time: Long): List<GrammarRecent> =
    (listOf(GrammarRecent(id, time)) + normalizeGrammarRecents(recents.filterNot { it.id == id })).take(20)

// Versioned, URL-encoded lines leave ids independent of the bundled content version.
fun encodeGrammarPrefs(state: GrammarPrefsState): String = buildString {
    append("v1\n")
    state.favourites.sorted().forEach { append("f\t${URLEncoder.encode(it, "UTF-8")}\n") }
    normalizeGrammarRecents(state.recents).forEach {
        append("r\t${it.openedAt}\t${URLEncoder.encode(it.id, "UTF-8")}\n")
    }
}

private fun decodeGrammarId(value: String): String {
    val id = URLDecoder.decode(value, "UTF-8")
    require(id.isNotEmpty() && URLEncoder.encode(id, "UTF-8").equals(value, ignoreCase = true))
    return id
}

fun decodeGrammarPrefs(value: String): GrammarPrefsState = runCatching {
    val lines = value.lineSequence().toList()
    require(lines.first() == "v1")
    val favourites = linkedSetOf<String>()
    val recents = mutableListOf<GrammarRecent>()
    lines.drop(1).filter { it.isNotEmpty() }.forEach { line ->
        val fields = line.split('\t')
        when (fields.first()) {
            "f" -> { require(fields.size == 2); favourites.add(decodeGrammarId(fields[1])) }
            "r" -> {
                require(fields.size == 3)
                recents.add(GrammarRecent(decodeGrammarId(fields[2]), fields[1].toLong()))
            }
            else -> Unit
        }
    }
    GrammarPrefsState(favourites, normalizeGrammarRecents(recents))
}.getOrDefault(GrammarPrefsState())
