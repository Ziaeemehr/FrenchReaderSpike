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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
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

class VoskEngine(private val modelDir: File) : SpeechEngine {
    override val kind = SpeechEngineKind.VOSK
    private val recorder = MicRecorder()
    private var model: Model? = null

    override fun start() = recorder.start()

    override suspend fun stop(): Recording {
        val pcm = recorder.stop()
        val text = withContext(Dispatchers.Default) {
            val m = model ?: Model(modelDir.absolutePath).also { model = it }
            Recognizer(m, SAMPLE_RATE.toFloat()).use { rec ->
                rec.acceptWaveForm(pcm, pcm.size)
                parseVoskText(rec.finalResult)
            }
        }
        return Recording(text, pcm, durationMsOf(pcm.size))
    }

    override fun cancel() = recorder.cancel()
    override fun release() { recorder.cancel(); model?.close(); model = null }
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
        newRecognizer().startListening(intent)
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
                val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                bytes.asShortBuffer().put(pcm)
                out.write(bytes.array())
            }
        }
        pipe[0].close()
        val text = result?.await().orEmpty()
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
