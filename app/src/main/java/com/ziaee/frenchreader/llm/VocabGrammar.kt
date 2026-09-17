package com.ziaee.frenchreader.llm

import org.json.JSONArray

object VocabGrammar {
    // Constrains output to a JSON array of 1-5 {"mot": ..., "definition_simple": ...}
    // objects. Written against llama.cpp's GBNF syntax (spike finding: free-form "reply
    // with JSON" prompting produced malformed shapes and a hallucinated word).
    const val GBNF = """root ::= "[" ws item (ws "," ws item){0,4} ws "]"
item ::= "{" ws "\"mot\"" ws ":" ws string ws "," ws "\"definition_simple\"" ws ":" ws string ws "}"
string ::= "\"" ([^"\\])* "\""
ws ::= [ \t\n]*"""

    data class VocabItem(val mot: String, val definitionSimple: String)

    fun parse(rawJson: String): List<VocabItem> {
        return try {
            val array = JSONArray(rawJson.trim())
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val mot = obj.optString("mot", "")
                val def = obj.optString("definition_simple", "")
                if (mot.isBlank() || def.isBlank()) null else VocabItem(mot, def)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
