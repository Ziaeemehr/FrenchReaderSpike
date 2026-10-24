package com.ziaee.frenchreader.watch

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CaptionCache(private val filesDir: File) {
    private fun file(videoId: String): AtomicFile {
        require(YoutubeUrl.videoId(videoId) == videoId)
        return AtomicFile(File(filesDir, "watch_captions/$videoId.json"))
    }

    internal fun read(videoId: String): CachedCaptions? = runCatching {
        val json = JSONObject(file(videoId).openRead().bufferedReader().use { it.readText() })
        val array = json.getJSONArray("cues")
        val cues = (0 until array.length()).map {
            val cue = array.getJSONObject(it)
            SubtitleCue(cue.getLong("startMs"), cue.getLong("endMs"), cue.getString("text"))
        }
        val captions = VideoCaptions(json.optString("title"), normalizeCues(cues), json.optString("channel"))
        CachedCaptions(captions, if (json.has("version")) json.getInt("version") else null,
            if (json.has("source")) json.getString("source") else null).takeIf { captions.cues.isNotEmpty() }
    }.getOrNull()

    fun write(videoId: String, captions: VideoCaptions, source: String) {
        require(source == "youtube" || source == "file")
        val array = JSONArray()
        captions.cues.forEach { cue ->
            array.put(JSONObject().put("startMs", cue.startMs).put("endMs", cue.endMs).put("text", cue.text))
        }
        val target = file(videoId)
        target.baseFile.parentFile?.mkdirs()
        val stream = target.startWrite()
        try {
            stream.write(JSONObject().put("version", 2).put("source", source).put("title", captions.title).put("channel", captions.channel).put("cues", array).toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }
}
