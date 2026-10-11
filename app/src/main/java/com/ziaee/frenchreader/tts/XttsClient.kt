package com.ziaee.frenchreader.tts

import android.content.Context
import android.util.Base64
import com.ziaee.frenchreader.data.XttsPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
        val response = try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            readResponse(connection)
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
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

    /**
     * Like [synthesizeSentences], but the server sends one JSON line per
     * sentence as soon as it is generated. Each sentence's audio is appended
     * to [outFile] and [onSentence] is told what has arrived so far, so
     * playback can start long before a long paragraph is finished.
     * Returns null when the server is too old to have the streaming endpoint.
     */
    fun synthesizeSentencesStreaming(
        context: Context,
        text: String,
        speaker: String,
        ratePercent: Int,
        outFile: File,
        onSentence: (received: List<SentenceBoundary>, plannedTexts: List<String>) -> Unit
    ): SynthesisResult? {
        val connection = openConnection(context, "/synthesize_stream").apply {
            requestMethod = "POST"
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            val request = JSONObject().apply {
                put("text", text)
                put("speaker", speaker)
                put("rate_percent", ratePercent)
                put("language", "fr")
            }
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_BAD_METHOD) return null
            if (status !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("Local XTTS server returned HTTP $status${if (body.isBlank()) "" else ": $body"}")
            }

            outFile.parentFile?.mkdirs()
            var plannedTexts = emptyList<String>()
            val received = ArrayList<SentenceBoundary>()
            var finished = false
            FileOutputStream(outFile, false).use { audio ->
                connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val event = JSONObject(line)
                        when (event.getString("type")) {
                            "start" -> plannedTexts = event.getJSONArray("sentences").let { array ->
                                List(array.length()) { array.getString(it) }
                            }
                            "sentence" -> {
                                audio.write(Base64.decode(event.getString("audio_b64"), Base64.DEFAULT))
                                audio.flush()
                                received.add(
                                    SentenceBoundary(
                                        event.getString("text"),
                                        event.getDouble("offset_ms"),
                                        event.getDouble("duration_ms")
                                    )
                                )
                                onSentence(received.toList(), plannedTexts)
                            }
                            "end" -> finished = true
                        }
                    }
                }
            }
            if (!finished || received.isEmpty()) throw IOException("Local XTTS server ended the stream early")
            return SynthesisResult(outFile, received)
        } finally {
            connection.disconnect()
        }
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
