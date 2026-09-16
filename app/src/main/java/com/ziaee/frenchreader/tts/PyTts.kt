package com.ziaee.frenchreader.tts

import com.chaquo.python.Python
import org.json.JSONObject
import java.io.File

data class SentenceBoundary(val text: String, val offsetMs: Double, val durationMs: Double)

data class SynthesisResult(val audioFile: File, val sentences: List<SentenceBoundary>)

/**
 * Thin Kotlin wrapper around tts_engine.py's synthesize_sentences(). Must be
 * called off the main thread -- Chaquopy calls block, and this one also
 * does network I/O (calling edge-tts) inside the Python side.
 */
object PyTts {

    fun synthesizeSentences(
        text: String,
        voice: String,
        ratePercent: Int,
        outFile: File
    ): SynthesisResult {
        val rateStr = if (ratePercent >= 0) "+${ratePercent}%" else "${ratePercent}%"
        val py = Python.getInstance()
        val module = py.getModule("tts_engine")
        val resultJson = module.callAttr(
            "synthesize_sentences", text, voice, rateStr, outFile.absolutePath
        ).toString()

        val obj = JSONObject(resultJson)
        val arr = obj.getJSONArray("sentences")
        val sentences = ArrayList<SentenceBoundary>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            sentences.add(
                SentenceBoundary(
                    text = o.getString("text"),
                    offsetMs = o.getDouble("offset_ms"),
                    durationMs = o.getDouble("duration_ms")
                )
            )
        }
        return SynthesisResult(outFile, sentences)
    }
}
