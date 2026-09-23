package com.ziaee.frenchreader.shadowing

import java.text.Normalizer

data class WordResult(val word: String, val matched: Boolean)
data class AlignmentResult(val words: List<WordResult>, val matched: Int, val total: Int)

/** Word-level comparison of a sentence with what the recognizer heard. Pure, no Android deps. */
object TranscriptAligner {
    private val APOSTROPHES = Regex("[’ʼ`´]")
    // Elided particle followed by an apostrophe, at a word start: l' j' qu' c' d' n' s' t' m'
    private val ELISION = Regex("(?i)\\b(qu|[ljcdnstm])'")
    private val SPLIT = Regex("[\\s\\-–—]+")
    private val EDGE_PUNCT = Regex("^[^\\p{L}\\p{N}']+|[^\\p{L}\\p{N}']+$")
    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Display tokens: original casing and accents, elisions kept with their apostrophe. */
    private fun displayTokens(text: String): List<String> =
        text.split(SPLIT).flatMap { raw ->
            val w = raw.replace(EDGE_PUNCT, "")
            if (w.isEmpty()) return@flatMap emptyList()
            val unified = w.replace(APOSTROPHES, "'")
            val m = ELISION.find(unified)
            if (m != null && m.range.first == 0 && m.range.last < unified.length - 1) {
                val cut = m.range.last + 1
                listOf(w.substring(0, cut), w.substring(cut))
            } else listOf(w)
        }

    private fun key(token: String): String =
        Normalizer.normalize(token.replace(APOSTROPHES, "'"), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .replace("'", "")

    fun normalizeTokens(text: String): List<String> =
        displayTokens(text).map(::key).filter { it.isNotEmpty() }

    fun align(original: String, transcript: String): AlignmentResult {
        val display = displayTokens(original).filter { key(it).isNotEmpty() }
        val a = display.map(::key)
        val b = normalizeTokens(transcript)
        // LCS table: extra spoken words cost nothing, and each original word is matched or not.
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) {
                dp[i + 1][j + 1] + 1
            } else {
                maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val matched = BooleanArray(a.size)
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> {
                    matched[i] = true
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> i++
                else -> j++
            }
        }
        val words = display.mapIndexed { idx, w -> WordResult(w, matched[idx]) }
        return AlignmentResult(words, matched.count { it }, words.size)
    }
}
