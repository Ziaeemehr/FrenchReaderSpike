package com.ziaee.frenchreader.shadowing

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Recording(val transcript: String, val pcm: ShortArray?, val durationMs: Long)

interface SpeechEngine {
    val kind: SpeechEngineKind
    fun start()
    suspend fun stop(): Recording
    fun cancel()
    fun release()
}

internal fun parseVoskText(json: String): String =
    runCatching { JSONObject(json).optString("text", "") }.getOrDefault("").trim()

internal suspend fun awaitResultWhileWriting(
    write: suspend () -> Unit,
    result: Deferred<String>,
    onDone: () -> Unit,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
): String = coroutineScope {
    val writer = launch(ioDispatcher) {
        try {
            write()
        } catch (_: IOException) {
            // The recognizer may stop reading before all audio has been written.
        }
    }
    try {
        result.await()
    } finally {
        try {
            onDone()
        } finally {
            writer.cancel()
        }
    }
}

class VoskEngine(private val modelDir: File) : SpeechEngine {
    override val kind = SpeechEngineKind.VOSK
    private val recorder = MicRecorder()

    override fun start() = recorder.start()

    override suspend fun stop(): Recording {
        val pcm = recorder.stop()
        val text = withContext(Dispatchers.Default) {
            VoskModels.forDir(modelDir).withModel { model ->
                Recognizer(model, SAMPLE_RATE.toFloat()).use { rec ->
                    rec.acceptWaveForm(pcm, pcm.size)
                    parseVoskText(rec.finalResult)
                }
            }
        }
        return Recording(text, pcm, durationMsOf(pcm.size))
    }

    override fun cancel() = recorder.cancel()
    override fun release() = recorder.cancel()
}

/** Android's recognizer. API 33+: we record, then feed our PCM through EXTRA_AUDIO_SOURCE, so
 * replay works. Older: the recognizer owns the mic live, so pcm is null (no replay). Main thread only. */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    override val kind = SpeechEngineKind.ANDROID
    private val feedsAudio = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    private val recorder = MicRecorder()
    private var recognizer: SpeechRecognizer? = null
    private var result: CompletableDeferred<String>? = null
    private var startedAtMs = 0L

    private fun baseIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }

    private fun newRecognizer(): SpeechRecognizer {
        recognizer?.destroy()
        val deferred = CompletableDeferred<String>().also { result = it }
        return SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(b: Bundle?) {
                    deferred.complete(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onError(error: Int) { deferred.complete("") }
                override fun onReadyForSpeech(p: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(v: Float) = Unit
                override fun onBufferReceived(b: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(b: Bundle?) = Unit
                override fun onEvent(t: Int, b: Bundle?) = Unit
            })
        }.also { recognizer = it }
    }

    override fun start() {
        startedAtMs = System.currentTimeMillis()
        if (feedsAudio) recorder.start() else newRecognizer().startListening(baseIntent())
    }

    override suspend fun stop(): Recording {
        if (!feedsAudio) {
            recognizer?.stopListening()
            val text = result?.await().orEmpty()
            return Recording(text.trim(), null, System.currentTimeMillis() - startedAtMs)
        }
        val pcm = recorder.stop()
        val pipe = ParcelFileDescriptor.createPipe()
        val intent = baseIntent().apply {
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
        }
        val output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        val closePipe = {
            runCatching { output.close() }
            runCatching { pipe[0].close() }
            Unit
        }
        val text = try {
            newRecognizer().startListening(intent)
            val pendingResult = checkNotNull(result)
            awaitResultWhileWriting(
                write = {
                    val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    bytes.asShortBuffer().put(pcm)
                    // Closing signals end-of-audio so the recognizer finalizes instead of waiting for more.
                    try { output.write(bytes.array()) } finally { runCatching { output.close() } }
                },
                result = pendingResult,
                onDone = closePipe
            )
        } finally {
            closePipe()
        }
        return Recording(text.trim(), pcm, durationMsOf(pcm.size))
    }

    override fun cancel() {
        recorder.cancel()
        recognizer?.cancel()
        result?.complete("")
    }

    override fun release() { cancel(); recognizer?.destroy(); recognizer = null }

    companion object {
        fun isAvailable(context: Context) = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
