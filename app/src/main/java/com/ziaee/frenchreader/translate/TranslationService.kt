package com.ziaee.frenchreader.translate

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free, no-API-key paragraph translation. Three providers, tried in order:
 *
 * 1. Google Translate's public web endpoint (the same one many "gtx"
 *    translator libraries use) -- best quality, no key, but unofficial and
 *    could stop working or start rate-limiting without notice.
 * 2. Google Translate's Chrome dictionary endpoint.
 * 3. MyMemory (api.mymemory.translated.net) -- a real, documented free
 *    translation API (anonymous quota ~5000 words/day) used as a fallback
 *    if Google's endpoint fails.
 *
 * All are plain HTTPS GET calls, no SDK needed.
 */
object TranslationService {

    fun translate(text: String, targetLang: String, sourceLang: String = "fr"): String {
        return try {
            translateWithGoogle(text, targetLang, sourceLang)
        } catch (e: Exception) {
            try { translateWithChrome(text, targetLang, sourceLang) } catch (e: Exception) {
                translateWithMyMemory(text, targetLang, sourceLang)
            }
        }
    }

    /**
     * Translates with the source language auto-detected. Returns null when the text is
     * already in [targetLang] (e.g. a Persian note in a French lesson, translating to Persian).
     */
    fun translateUnlessSameLanguage(text: String, targetLang: String): String? {
        val translated = try {
            val body = googleRequest(text, targetLang, "auto")
            val detected = JSONArray(body).opt(2) as? String
            if (detected != null && baseLanguage(detected) == baseLanguage(targetLang)) return null
            parseGoogleResponse(body)
        } catch (e: Exception) {
            try { translateWithChrome(text, targetLang, "auto") } catch (e: Exception) {
                translateWithMyMemory(text, targetLang, "fr")
            }
        }
        return translated.takeUnless { isSameText(it, text) }
    }

    fun translateLines(lines: List<String>, targetLang: String, sourceLang: String = "fr"): List<String>? {
        if (lines.isEmpty()) return emptyList()
        val text = lines.joinToString("\n") { it.replace(Regex("[\\r\\n]+"), " ") }
        return splitTranslatedLines(translate(text, targetLang, sourceLang), lines.size)
    }

    fun wordSenses(word: String, targetLang: String, sourceLang: String = "fr"): List<WordSense> =
        runCatching {
            parseWordSenses(googleRequest(word, targetLang, sourceLang, dictionary = true))
        }.getOrDefault(emptyList())

    private fun translateWithChrome(text: String, targetLang: String, sourceLang: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val conn = URL("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sourceLang&tl=$targetLang&q=$encoded")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            val response = conn.inputStream.bufferedReader(Charsets.UTF_8).use { JSONArray(it.readText()) }
            val first = response.get(0)
            val translated = if (first is JSONArray) first.getString(0) else first as String
            check(translated.isNotBlank())
            return translated.trim()
        } finally {
            conn.errorStream?.close()
            conn.disconnect()
        }
    }

    /** "fa-IR" / "zh-CN" -> "fa" / "zh". */
    fun baseLanguage(code: String): String = code.substringBefore('-').substringBefore('_').lowercase()

    /** A "translation" that just echoes the source means the text was already in the target language. */
    fun isSameText(translated: String, source: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        return norm(translated) == norm(source)
    }

    private fun translateWithGoogle(text: String, targetLang: String, sourceLang: String): String =
        parseGoogleResponse(googleRequest(text, targetLang, sourceLang))

    private fun googleRequest(text: String, targetLang: String, sourceLang: String, dictionary: Boolean = false): String {
        return requestGoogleWithFallback { chrome ->
            googleSingleRequest(googleSingleUrl(text, targetLang, sourceLang, dictionary, chrome))
        }
    }

    private fun googleSingleRequest(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            // Google's endpoint sometimes 403s requests without a browser-like UA.
            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
            )
            conn.inputStream.use { stream ->
                return stream.bufferedReader(Charsets.UTF_8).readText()
            }
        } finally {
            conn.errorStream?.close()
            conn.disconnect()
        }
    }

    /** Response shape: [[["translated seg","original seg",null,null,...], ...], ...] */
    private fun parseGoogleResponse(body: String): String {
        val outer = JSONArray(body)
        val segments = outer.getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until segments.length()) {
            val seg = segments.optJSONArray(i) ?: continue
            val translatedPiece = seg.optString(0, "")
            sb.append(translatedPiece)
        }
        val result = sb.toString().trim()
        if (result.isEmpty()) throw IllegalStateException("Empty translation from Google endpoint")
        return result
    }

    private fun translateWithMyMemory(text: String, targetLang: String, sourceLang: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://api.mymemory.translated.net/get" +
            "?q=$encoded&langpair=$sourceLang|$targetLang"
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.inputStream.use { stream ->
                val body = stream.bufferedReader(Charsets.UTF_8).readText()
                val obj = org.json.JSONObject(body)
                val translated = obj.getJSONObject("responseData").getString("translatedText")
                if (translated.isBlank()) throw IllegalStateException("Empty translation from MyMemory")
                return translated
            }
        } finally {
            conn.errorStream?.close()
            conn.disconnect()
        }
    }
}

data class WordSense(val partOfSpeech: String, val term: String, val score: Double)

internal fun splitTranslatedLines(translated: String, count: Int): List<String>? {
    val lines = translated.split("\n").map { it.trim() }
    return lines.takeIf { it.size == count && it.none(String::isBlank) }
}

internal fun parseWordSenses(body: String): List<WordSense> = runCatching {
    val outer = JSONArray(body)
    if (outer.isNull(1) || outer.length() < 2) return@runCatching emptyList()
    val groups = outer.getJSONArray(1)
    val seen = mutableSetOf<String>()
    buildList {
        for (i in 0 until groups.length()) {
            val group = groups.getJSONArray(i)
            val pos = group.getString(0)
            val scored = if (group.isNull(2)) null else group.getJSONArray(2)
            val senses = if (scored != null && scored.length() > 0) {
                (0 until scored.length()).map { j ->
                    val entry = scored.getJSONArray(j)
                    WordSense(pos, entry.getString(0), entry.getDouble(3))
                }.sortedByDescending { it.score }
            } else {
                val terms = group.getJSONArray(1)
                (0 until terms.length()).map { WordSense(pos, terms.getString(it), 0.0) }
            }
            senses.filter { it.term.isNotBlank() && seen.add(it.term) }.forEach { add(it) }
        }
    }
}.getOrDefault(emptyList())

internal fun googleSingleUrl(text: String, targetLang: String, sourceLang: String, dictionary: Boolean, chrome: Boolean): String {
    val endpoint = if (chrome) "https://clients5.google.com/translate_a/single?client=dict-chrome-ex"
        else "https://translate.googleapis.com/translate_a/single?client=gtx"
    return "$endpoint&sl=$sourceLang&tl=$targetLang&dt=t${if (dictionary) "&dt=bd" else ""}&q=${URLEncoder.encode(text, "UTF-8")}"
}

internal fun requestGoogleWithFallback(request: (Boolean) -> String): String {
    fun validated(chrome: Boolean): String = request(chrome).also { JSONArray(it) }
    return try { validated(false) } catch (_: Exception) { validated(true) }
}
