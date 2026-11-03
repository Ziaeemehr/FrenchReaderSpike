package com.ziaee.frenchreader.content.pdf

import java.text.Normalizer
import java.util.Locale

/** Offsets are UTF-16, matching Compose's text offsets; end is exclusive. */
data class OcrChange(val start: Int, val end: Int, val original: String, val replacement: String)
data class OcrTokenConfidence(val start: Int, val end: Int, val confidence: Float)
data class OcrCorrection(val text: String, val changes: List<OcrChange>) {
    fun revert(change: OcrChange): OcrCorrection {
        if (change !in changes) return this
        val delta = change.original.length - (change.end - change.start)
        return OcrCorrection(text.replaceRange(change.start, change.end, change.original), changes.filter { it != change }.map {
            if (it.start >= change.end) it.copy(start = it.start + delta, end = it.end + delta) else it
        })
    }
}

private val ocrTokens = Regex("[\\p{L}\\p{N}]+")
// Compiled once: the skeleton runs for every one of ~110k lexicon words while indexing.
private val combiningMarks = Regex("\\p{M}+")

/** Simple word-level LCS diff. Replacements carry the previous form for individual reversion. */
fun ocrWordDiff(original: String, corrected: String): OcrCorrection {
    val before = ocrTokens.findAll(original).toList()
    val after = ocrTokens.findAll(corrected).toList()
    // Bound quadratic work on unexpectedly large model responses.
    if (before.size.toLong() * after.size > 1_000_000) return OcrCorrection(corrected, emptyList())
    val lengths = Array(before.size + 1) { IntArray(after.size + 1) }
    for (i in before.indices.reversed()) for (j in after.indices.reversed()) {
        lengths[i][j] = if (before[i].value == after[j].value) 1 + lengths[i+1][j+1]
        else maxOf(lengths[i+1][j], lengths[i][j+1])
    }
    val changes = mutableListOf<OcrChange>()
    var i = 0; var j = 0
    while (i < before.size || j < after.size) {
        if (i < before.size && j < after.size && before[i].value == after[j].value) { i++; j++; continue }
        val startI = i; val startJ = j
        while (i < before.size || j < after.size) {
            if (i < before.size && j < after.size && before[i].value == after[j].value) break
            if (i < before.size && (j == after.size || lengths[i+1][j] >= lengths[i][j+1])) i++ else j++
        }
        if (j > startJ) {
            if (i - startI == j - startJ) {
                // Independent replacements stay independently tappable, including adjacent words.
                for (offset in 0 until j - startJ) {
                    val token = after[startJ + offset]
                    changes += OcrChange(token.range.first, token.range.last + 1, before[startI + offset].value, token.value)
                }
            } else {
                val start = after[startJ].range.first; val end = after[j-1].range.last + 1
                val old = if (i > startI) original.substring(before[startI].range.first, before[i-1].range.last + 1) else ""
                changes += OcrChange(start, end, old, corrected.substring(start, end))
            }
        }
    }
    return OcrCorrection(corrected, changes)
}

/** Build once on an IO/default dispatcher, then share. No Android dependencies. */
class OcrTextCorrector(forms: Set<String>, commonForms: Set<String>) {
    private val lexicon = (forms + commonForms).mapTo(HashSet()) { it.lowercase(Locale.FRENCH) }
    private val common = commonForms.mapTo(HashSet()) { it.lowercase(Locale.FRENCH) }
    // Canonical OCR families make the candidate search narrow even for multi-letter confusions.
    private fun skeleton(value: String): String = Normalizer.normalize(value.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(combiningMarks, "").replace("rn", "m").replace("nn", "m")
        .replace("cl", "d").replace("li", "h").replace("ii", "n").replace("vv", "w")
        .replace('i', 'l').replace('n', 'u')
    private val buckets = lexicon.groupBy { skeleton(it) }
    private val alphabet = buckets.keys.flatMapTo(linkedSetOf()) { it.toList() }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun candidates(token: String): Sequence<String> {
        val key = skeleton(token)
        val keys = linkedSetOf(key)
        // Overlapping families (e.g. li inside conseilIe) need explicit alternatives
        // before canonicalization; one greedy skeleton cannot represent both readings.
        for (group in confusionGroups) for (from in group) for (to in group) {
            if (from == to) continue
            var at = token.indexOf(from)
            while (at >= 0) {
                keys += skeleton(token.replaceRange(at, at + from.length, to))
                at = token.indexOf(from, at + 1)
            }
        }
        // Hash lookups enumerate one ordinary edit, after collapsing cheap OCR families.
        // This avoids scanning 86k forms for each unknown token.
        for (i in key.indices) {
            keys += key.removeRange(i, i + 1)
            for (letter in alphabet) keys += key.substring(0, i) + letter + key.substring(i + 1)
        }
        for (i in 0..key.length) for (letter in alphabet) keys += key.substring(0, i) + letter + key.substring(i)
        return keys.asSequence().flatMap { buckets[it].orEmpty().asSequence() }
    }

    fun correct(raw: String, confidences: List<OcrTokenConfidence> = emptyList()): OcrCorrection {
        // Confidence offsets refer to raw OCR. Carry protected spans through formatting by
        // replacing them with their original tokens in the final spelling pass via token order.
        val protected = ocrTokens.findAll(raw).map { token ->
            confidences.any { it.confidence >= .9f && token.range.first < it.end && token.range.last + 1 > it.start }
        }.toList()
        val hadProtected = protected.any { it }
        val rawTokenCount = protected.size
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = Regex("([\\p{L}]+)-[ \\t]*\\n[ \\t]*([\\p{L}]+)").replace(text) { match ->
            val a = match.groupValues[1]; val b = match.groupValues[2]
            val join = (a+b).lowercase(Locale.FRENCH) in lexicon ||
                (a.lowercase(Locale.FRENCH) !in lexicon && b.lowercase(Locale.FRENCH) !in lexicon)
            a + (if (join) "" else "-") + b
        }
        text = text.split(Regex("\\n[ \\t]*\\n+")).joinToString("\n\n") { it.replace(Regex("\\s+"), " ").trim() }
        text = text.replace(Regex("[*|]+(?=[\\p{L}])"), "")
            .replace(Regex("[ \\t]*([.,)])"), "$1")
            .replace(Regex("[ \\t]*([;:!?»])"), " $1")
            .replace(Regex("«[ \\t]*"), "« ")
            .trim()
        val tokenCount = ocrTokens.findAll(text).count()
        var index = 0
        val changes = mutableListOf<OcrChange>()
        val output = StringBuilder()
        var previous = 0
        for (match in ocrTokens.findAll(text)) {
            output.append(text, previous, match.range.first)
            val token = match.value
            val lower = token.lowercase(Locale.FRENCH)
            val preceding = text.substring(0, match.range.first).trimEnd()
            val capitalized = token.first().isUpperCase() && token.drop(1).all { it.isLowerCase() }
            val midSentence = preceding.isNotEmpty() && preceding.last() !in ".!?\n"
            // If line joining changed token counts, conservatively protect all spelling when
            // confidence exists; never accidentally alter a high-confidence word.
            val protect = hadProtected && (protected.getOrNull(index) == true || rawTokenCount != tokenCount)
            val replacement = if (protect || (lower in lexicon && !(token.any(Char::isLowerCase) && token.drop(1).any(Char::isUpperCase))) || token.length < 3 || token.length > 40 ||
                token.any(Char::isDigit) || (capitalized && midSentence)) token else {
                val corrected = cache.getOrPut(lower) { choose(lower) }
                when {
                    token.all(Char::isUpperCase) -> corrected.uppercase(Locale.FRENCH)
                    token.first().isUpperCase() -> corrected.replaceFirstChar { it.titlecase(Locale.FRENCH) }
                    else -> corrected
                }
            }
            if (replacement != token) changes += OcrChange(output.length, output.length + replacement.length, token, replacement)
            output.append(replacement)
            previous = match.range.last + 1
            index++
        }
        output.append(text, previous, text.length)
        val corrected = output.toString()
        // Preserve original OCR forms for joined line-break words as well as spelling edits.
        val joins = ocrWordDiff(raw, text).changes
        val spelling = OcrCorrection(corrected, changes)
        if (joins.isEmpty()) return spelling
        return ocrWordDiff(raw, corrected)
    }

    private fun choose(token: String): String {
        val threshold = if (token.length >= 7) 1.5 else 1.0
        val ranked = candidates(token).distinct().map { it to weightedOcrDistance(token, it) }
            .filter { it.second <= threshold + .5 }.sortedWith(compareBy<Pair<String, Double>> { it.second }
                .thenByDescending { it.first in common }.thenBy { it.first }).toList()
        val best = ranked.firstOrNull()?.takeIf { it.second <= threshold } ?: return token
        // Common forms break equal-cost ties, but a genuinely close alternative remains unsafe.
        val second = ranked.drop(1).firstOrNull { !(it.second == best.second && best.first in common && it.first !in common) }
        return if (second == null || second.second - best.second >= .5 - 1e-8) best.first else token
    }
}

private val confusionGroups = listOf(listOf("rn", "m", "nn"), listOf("n", "u"), listOf("cl", "d"),
    listOf("li", "h"), listOf("l", "i", "1"), listOf("0", "o"), listOf("5", "s"),
    listOf("e", "é", "è", "ê", "ë"), listOf("a", "à", "â"), listOf("c", "ç"),
    listOf("u", "ù", "û", "ü"), listOf("i", "î", "ï"), listOf("o", "ô", "ö"),
    listOf("ii", "ü", "n"), listOf("vv", "w"))

/** Multi-character weighted Levenshtein, including symmetric OCR substitutions. */
fun weightedOcrDistance(source: String, target: String): Double {
    val a = source.lowercase(Locale.FRENCH); val b = target.lowercase(Locale.FRENCH)
    val dp = Array(a.length + 1) { DoubleArray(b.length + 1) { Double.POSITIVE_INFINITY } }
    dp[0][0] = 0.0
    for (i in 0..a.length) for (j in 0..b.length) {
        val cost = dp[i][j]
        if (i < a.length) dp[i+1][j] = minOf(dp[i+1][j], cost + 1)
        if (j < b.length) dp[i][j+1] = minOf(dp[i][j+1], cost + 1)
        if (i < a.length && j < b.length) dp[i+1][j+1] = minOf(dp[i+1][j+1], cost + if (a[i] == b[j]) 0.0 else 1.0)
        for (group in confusionGroups) for (from in group) {
            if (!a.startsWith(from, i)) continue
            for (to in group) if (from != to && b.startsWith(to, j)) {
                dp[i+from.length][j+to.length] = minOf(dp[i+from.length][j+to.length], cost + if (group.any { value -> value.any { it in "éèêëàâçùûüîïôö" } } && group.none { it == "ii" }) .5 else .2)
            }
        }
    }
    return dp[a.length][b.length]
}
