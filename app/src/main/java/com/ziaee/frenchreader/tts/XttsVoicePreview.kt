package com.ziaee.frenchreader.tts

import android.content.Context
import android.media.MediaPlayer
import com.ziaee.frenchreader.data.XttsPrefs
import com.ziaee.frenchreader.net.LocalServerPrefs
import java.io.File
import java.security.MessageDigest

/** A few seconds of each XTTS speaker, generated once and kept, so the settings list can be listened to. */
object XttsVoicePreview {
    private const val SAMPLE = "Bonjour ! Voici ma voix. Je peux lire vos textes en français."
    // The server's speed is 1 + rate/100, so 0 is the normal speed (100 would be double).
    private const val NORMAL_RATE = 0
    private var player: MediaPlayer? = null

    private fun sampleFile(context: Context, speaker: String): File {
        val paired = if (XttsPrefs.getUsePairedComputer(context)) LocalServerPrefs(context).paired else null
        val server = paired?.let { "paired:${it.id}" }
            ?: "url:${XttsPrefs.getServerUrl(context).trim().trimEnd('/')}"
        val hash = MessageDigest.getInstance("SHA-256").digest("$server|$speaker|$NORMAL_RATE|$SAMPLE".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        return File(context.cacheDir, "xtts_preview/$hash.mp3")
    }

    fun isCached(context: Context, speaker: String): Boolean = sampleFile(context, speaker).length() > 0

    /** Network call: run off the main thread. A failed synthesis leaves no partial file behind. */
    fun prepare(context: Context, speaker: String): File {
        val file = sampleFile(context, speaker)
        if (file.length() > 0) return file
        file.parentFile?.mkdirs()
        val partial = File.createTempFile(file.name, ".part", file.parentFile)
        try {
            XttsClient.synthesizeSentences(context, SAMPLE, speaker, NORMAL_RATE, partial)
            check(partial.length() > 0)
            check(partial.renameTo(file))
        } finally {
            partial.delete()
        }
        return file
    }

    /** Main thread. Starting another sample stops the one playing. */
    fun play(file: File, onDone: () -> Unit) {
        stop()
        val current = MediaPlayer()
        player = current
        try {
            current.setDataSource(file.path)
            current.setOnCompletionListener {
                if (player === current) { stop(); onDone() }
            }
            current.setOnErrorListener { _, _, _ ->
                if (player === current) { stop(); onDone() }
                true
            }
            current.prepare()
            current.start()
        } catch (error: Exception) {
            stop()
            throw error
        }
    }

    fun stop() {
        val current = player
        player = null
        current?.release()
    }
}
