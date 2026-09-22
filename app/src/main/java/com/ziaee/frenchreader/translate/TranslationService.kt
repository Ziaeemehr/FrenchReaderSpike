package com.ziaee.frenchreader.translate

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free, no-API-key paragraph translation. Two providers, tried in order:
 *
 * 1. Google Translate's public web endpoint (the same one many "gtx"
 *    translator libraries use) -- best quality, no key, but unofficial and
 *    could stop working or start rate-limiting without notice.
 * 2. MyMemory (api.mymemory.translated.net) -- a real, documented free
 *    translation API (anonymous quota ~5000 words/day) used as a fallback
 *    if Google's endpoint fails.
 *
 * Both are plain HTTPS GET calls, no SDK needed.
 */
object TranslationService {

    fun translate(text: String, targetLang: String, sourceLang: String = "fr"): String {
        return try {
            translateWithGoogle(text, targetLang, sourceLang)
        } catch (e: Exception) {
            translateWithMyMemory(text, targetLang, sourceLang)
        }
    }

    private fun translateWithGoogle(text: String, targetLang: String, sourceLang: String): String {
        val encoded = URLEncoder.encode(text, "UTF-8")
        val urlStr = "https://translate.googleapis.com/translate_a/single" +
            "?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t&q=$encoded"
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
                val body = stream.bufferedReader(Charsets.UTF_8).readText()
                return parseGoogleResponse(body)
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
