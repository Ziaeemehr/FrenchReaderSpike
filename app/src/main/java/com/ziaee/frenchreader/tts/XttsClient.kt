package com.ziaee.frenchreader.tts

import android.content.Context
import android.util.Base64
import com.ziaee.frenchreader.data.XttsPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object XttsClient {
    fun listSpeakers(context: Context): List<String> {
        val connection = openConnection(context, "/speakers").apply { requestMethod = "GET" }
        val array = JSONArray(readResponse(connection))
        return List(array.length()) { index -> array.getString(index) }
    }

    fun synthesizeSentences(
        context: Context,
        text: String,
        speaker: String,
        ratePercent: Int,
        outFile: File
    ): SynthesisResult {
        val connection = openConnection(context, "/synthesize").apply {
            requestMethod = "POST"
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val request = JSONObject().apply {
            put("text", text)
            put("speaker", speaker)
            put("rate_percent", ratePercent)
            put("language", "fr")
        }
        connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
        val response = readResponse(connection)
        val obj = JSONObject(response)
        val audio = Base64.decode(obj.getString("audio_b64"), Base64.DEFAULT)
        outFile.parentFile?.mkdirs()
        outFile.writeBytes(audio)

        val arr = obj.getJSONArray("sentences")
        val sentences = ArrayList<SentenceBoundary>(arr.length())
        for (i in 0 until arr.length()) {
            val sentence = arr.getJSONObject(i)
            sentences.add(
                SentenceBoundary(
                    sentence.getString("text"),
                    sentence.getDouble("offset_ms"),
                    sentence.getDouble("duration_ms")
                )
            )
        }
        return SynthesisResult(outFile, sentences)
    }

    fun ping(context: Context): Boolean = try {
        val connection = openConnection(context, "/health").apply { requestMethod = "GET" }
        JSONObject(readResponse(connection)).optBoolean("ok", false)
    } catch (_: Exception) {
        false
    }

    private fun openConnection(context: Context, path: String): HttpURLConnection {
        val serverUrl = XttsPrefs.getServerUrl(context).trim().trimEnd('/')
        if (serverUrl.isBlank()) throw IllegalStateException("Local XTTS server URL is blank")
        return (URL("$serverUrl$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            XttsPrefs.getToken(context).takeIf { it.isNotBlank() }?.let { setRequestProperty("X-Token", it) }
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        try {
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Local XTTS server returned HTTP $status${if (body.isBlank()) "" else ": $body"}")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}
