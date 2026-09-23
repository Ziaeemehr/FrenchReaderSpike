package com.ziaee.frenchreader.shadowing

import com.ziaee.frenchreader.data.ShadowAttempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MIN_RECORDING_MS = 300L

data class SentenceRef(val chunkIndex: Int, val sentenceIndex: Int)

enum class ShadowError { NOT_HEARD, ENGINE_FAILED }

sealed interface ShadowPhase {
    data object Idle : ShadowPhase
    data object Recording : ShadowPhase
    data object Recognizing : ShadowPhase
    data class Result(val alignment: AlignmentResult, val pace: PaceResult?) : ShadowPhase
    data class Error(val kind: ShadowError) : ShadowPhase
}

data class ShadowingState(
    val enabled: Boolean = false,
    val target: SentenceRef? = null,
    val sentenceText: String = "",
    val phase: ShadowPhase = ShadowPhase.Idle,
    val hasRecording: Boolean = false
)

/** Record -> recognize -> align -> save state machine. No Android deps, so it is unit-testable. */
class ShadowingController(
    private val scope: CoroutineScope,
    private val engineFactory: () -> SpeechEngine,
    private val saveAttempt: suspend (ShadowAttempt) -> Unit,
    private val addStudyTimeMs: (Long) -> Unit,
    private val now: () -> Long,
    private val isPlaying: () -> Boolean
) {
    private val _state = MutableStateFlow(ShadowingState())
    val state: StateFlow<ShadowingState> = _state.asStateFlow()

    private var engine: SpeechEngine? = null
    private var textId = 0L
    private var expectedMs = 0L
    private var pressedAtMs = 0L
    private var lastPcm: ShortArray? = null
    private var job: Job? = null

    fun setEnabled(enabled: Boolean) {
        if (!enabled) { cancel(); engine?.release(); engine = null; lastPcm = null }
        _state.value = if (enabled) _state.value.copy(enabled = true) else ShadowingState()
    }

    /** [expectedMs] = TTS sentence duration at the current playback speed. */
    fun setTarget(textId: Long, ref: SentenceRef, text: String, expectedMs: Long) {
        cancel()
        this.textId = textId
        this.expectedMs = expectedMs
        lastPcm = null
        _state.value = _state.value.copy(target = ref, sentenceText = text, phase = ShadowPhase.Idle, hasRecording = false)
    }

    /** Returns false (and does nothing) while TTS is playing or a result is being computed. */
    fun pressStart(): Boolean {
        val s = _state.value
        if (!s.enabled || s.target == null || isPlaying()) return false
        if (s.phase == ShadowPhase.Recording || s.phase == ShadowPhase.Recognizing) return false
        PcmPlayer.stop()
        val e = engine ?: engineFactory().also { engine = it }
        pressedAtMs = now()
        runCatching { e.start() }.onFailure {
            _state.value = s.copy(phase = ShadowPhase.Error(ShadowError.ENGINE_FAILED)); return false
        }
        _state.value = s.copy(phase = ShadowPhase.Recording)
        return true
    }

    fun pressEnd() {
        val e = engine ?: return
        if (_state.value.phase != ShadowPhase.Recording) return
        _state.value = _state.value.copy(phase = ShadowPhase.Recognizing)
        job = scope.launch {
            val rec = runCatching { e.stop() }.getOrElse {
                _state.value = _state.value.copy(phase = ShadowPhase.Error(ShadowError.ENGINE_FAILED)); return@launch
            }
            if (rec.durationMs < MIN_RECORDING_MS) {
                _state.value = _state.value.copy(phase = ShadowPhase.Idle); return@launch
            }
            addStudyTimeMs(now() - pressedAtMs)
            lastPcm = rec.pcm
            if (rec.transcript.isBlank()) {
                _state.value = _state.value.copy(phase = ShadowPhase.Error(ShadowError.NOT_HEARD), hasRecording = rec.pcm != null)
                return@launch
            }
            val target = _state.value.target ?: return@launch
            val alignment = TranscriptAligner.align(_state.value.sentenceText, rec.transcript)
            val pace = PaceAnalyzer.pace(rec.pcm, SAMPLE_RATE, expectedMs)
            saveAttempt(
                ShadowAttempt(textId = textId, chunkIndex = target.chunkIndex, sentenceIndex = target.sentenceIndex,
                    matched = alignment.matched, total = alignment.total, paceRatio = pace?.ratio,
                    engine = e.kind.id, timestampMs = now())
            )
            _state.value = _state.value.copy(phase = ShadowPhase.Result(alignment, pace), hasRecording = rec.pcm != null)
        }
    }

    fun retry() {
        cancel()
        lastPcm = null
        _state.value = _state.value.copy(phase = ShadowPhase.Idle, hasRecording = false)
    }

    fun replayMine() { lastPcm?.let { PcmPlayer.play(it) } }

    /** Stops any in-flight recording/recognition without saving. */
    fun cancel() {
        job?.cancel(); job = null
        if (_state.value.phase == ShadowPhase.Recording || _state.value.phase == ShadowPhase.Recognizing) {
            engine?.cancel()
            _state.value = _state.value.copy(phase = ShadowPhase.Idle)
        }
        PcmPlayer.stop()
    }

    fun release() { cancel(); engine?.release(); engine = null }
}
