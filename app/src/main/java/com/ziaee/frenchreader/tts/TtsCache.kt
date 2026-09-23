package com.ziaee.frenchreader.tts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Disk cache keyed by (text, voice, rate) so re-reading a text, or resuming
 * after a restart, doesn't re-call edge-tts for chunks we already have.
 * Per the design doc: "کش بر اساس متن، صدا و تنظیمات تولید است."
 */
class TtsCache(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "tts_cache"))

    init {
        dir.mkdirs()
    }

    private fun keyFor(text: String, voice: String, ratePercent: Int): String {
        val raw = "$text|$voice|$ratePercent"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun get(text: String, voice: String, ratePercent: Int): SynthesisResult? {
        val key = keyFor(text, voice, ratePercent)
        val audio = File(dir, "$key.mp3")
        val meta = File(dir, "$key.json")
        if (!audio.exists() || !meta.exists()) return null
        return try {
            val arr = JSONArray(meta.readText())
            val sentences = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SentenceBoundary(o.getString("text"), o.getDouble("offset_ms"), o.getDouble("duration_ms"))
            }
            audio.setLastModified(System.currentTimeMillis())
            SynthesisResult(audio, sentences)
        } catch (e: Exception) {
            null
        }
    }

    fun put(text: String, voice: String, ratePercent: Int, result: SynthesisResult) {
        val key = keyFor(text, voice, ratePercent)
        val meta = File(dir, "$key.json")
        val arr = JSONArray()
        result.sentences.forEach {
            arr.put(JSONObject().apply {
                put("text", it.text)
                put("offset_ms", it.offsetMs)
                put("duration_ms", it.durationMs)
            })
        }
        meta.writeText(arr.toString())
        // audio file is already written by PyTts to the cache-keyed path
        // (see TtsChunkRepository), nothing more to do here.
    }

    fun audioPathFor(text: String, voice: String, ratePercent: Int): File {
        val key = keyFor(text, voice, ratePercent)
        return File(dir, "$key.mp3")
    }

    fun pin(text: String, voice: String, ratePercent: Int) {
        File(dir, "${keyFor(text, voice, ratePercent)}.pinned").createNewFile()
    }

    fun unpin(text: String, voice: String, ratePercent: Int) {
        File(dir, "${keyFor(text, voice, ratePercent)}.pinned").delete()
    }

    fun isPinned(text: String, voice: String, ratePercent: Int): Boolean =
        File(dir, "${keyFor(text, voice, ratePercent)}.pinned").exists()

    fun enforceMaxSize(maxBytes: Long) {
        var totalSize = sizeBytes()
        if (totalSize <= maxBytes) return
        entries()
            .filter { !it.pinned && it.audio != null }
            .sortedBy { it.audio?.lastModified() }
            .forEach { entry ->
                if (totalSize <= maxBytes) return
                val entrySize = entry.files.sumOf { it.length() }
                entry.files.forEach { it.delete() }
                totalSize -= entrySize
            }
    }

    fun pruneOlderThan(maxAgeMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        entries()
            .filter { !it.pinned && it.audio?.lastModified()?.let { modified -> modified < cutoff } == true }
            .forEach { entry -> entry.files.forEach { it.delete() } }
    }

    fun clearAll() {
        entries()
            .filterNot { it.pinned }
            .forEach { entry -> entry.files.forEach { it.delete() } }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun entries(): List<CacheEntry> = dir.listFiles()
        ?.groupBy { it.nameWithoutExtension }
        ?.map { (_, files) ->
            CacheEntry(
                files = files,
                audio = files.firstOrNull { it.extension == "mp3" },
                pinned = files.any { it.extension == "pinned" }
            )
        }
        .orEmpty()

    private data class CacheEntry(
        val files: List<File>,
        val audio: File?,
        val pinned: Boolean
    )
}
