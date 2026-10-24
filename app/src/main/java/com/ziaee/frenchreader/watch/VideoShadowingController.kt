package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.shadowing.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoShadowingState(
    val units: List<SubtitleCue> = emptyList(),
    val index: Int = 0,
    val playingUnit: Boolean = false,
    val speed: Float = 1f,
    val autoRecord: Boolean = false,
    val phase: ShadowPhase = ShadowPhase.Idle,
    val hasRecording: Boolean = false
) {
    val unit: SubtitleCue? get() = units.getOrNull(index)
}

/** Owns only explicitly requested line playback; ordinary YouTube playback is never armed. */
class VideoShadowingController(
    private val scope: CoroutineScope,
    private val player: YoutubePlayerController,
    private val engineFactory: () -> SpeechEngine,
    private val requestRecording: (Int) -> Unit,
    private val beforePlayback: () -> Unit = {}
) {
    private val mutableState = MutableStateFlow(VideoShadowingState())
    val state = mutableState.asStateFlow()
    private var engine: SpeechEngine? = null
    private var pcm: ShortArray? = null
    private var job: Job? = null
    var generation = 0
        private set
    private var seekObserved = false

    fun setCues(cues: List<SubtitleCue>) {
        cancel(pauseVideo = state.value.playingUnit || state.value.phase == ShadowPhase.Recording)
        pcm = null
        mutableState.value = mutableState.value.copy(units = mergeIntoSentences(cues), index = 0,
            phase = ShadowPhase.Idle, hasRecording = false)
    }

    // Passive transcript following clears old feedback without interrupting ordinary playback.
    fun selectAt(timeMs: Long, pauseVideo: Boolean = true) {
        val units = state.value.units
        if (units.isEmpty()) return
        val containing = units.indexOfFirst { timeMs >= it.startMs && timeMs < it.endMs }
        val index = if (containing >= 0) containing
            else units.indexOfFirst { it.startMs > timeMs }.takeIf { it >= 0 } ?: units.lastIndex
        select(index, pauseVideo)
    }

    fun playUnit() {
        val unit = state.value.unit ?: return
        cancel()
        seekObserved = false
        mutableState.value = state.value.copy(playingUnit = true)
        player.seekTo(unit.startMs, generation)
        player.setPlaybackRate(state.value.speed)
        beforePlayback()
        player.play()
    }

    fun repeat() = playUnit()
    fun previous() = select(state.value.index - 1)
    fun next() = select(state.value.index + 1)
    private fun select(index: Int, pauseVideo: Boolean = true) {
        if (index !in state.value.units.indices) return
        cancel(pauseVideo = pauseVideo)
        pcm = null
        mutableState.value = state.value.copy(index = index, phase = ShadowPhase.Idle, hasRecording = false)
    }

    fun setSpeed(speed: Float) {
        if (speed !in listOf(0.5f, 0.75f, 1f)) return
        mutableState.value = state.value.copy(speed = speed)
        player.setPlaybackRate(speed)
    }

    fun setAutoRecord(enabled: Boolean) { mutableState.value = state.value.copy(autoRecord = enabled) }

    fun onTime(timeMs: Long, playbackGeneration: Int = generation) {
        val s = state.value
        val unit = s.unit ?: return
        if (playbackGeneration != generation || !s.playingUnit) return
        // Only a sample inside this unit acknowledges the seek for this playback.
        if (timeMs >= unit.startMs && timeMs < unit.endMs) seekObserved = true
        if (shouldAutoPause(timeMs, unit, s.playingUnit, seekObserved)) completeUnit()
    }

    fun onPlayerState(value: Int, playbackGeneration: Int = generation) {
        if (value == 0 && playbackGeneration == generation && state.value.playingUnit && seekObserved) completeUnit()
    }

    private fun completeUnit() {
        val s = state.value
        mutableState.value = s.copy(playingUnit = false)
        seekObserved = false
        player.pause()
        if (s.autoRecord) requestRecording(generation)
    }

    fun beginRecordingRequest(originGeneration: Int): Int? {
        if (originGeneration != generation || state.value.unit == null) return null
        cancel()
        return generation
    }

    fun record() {
        if (state.value.unit == null || state.value.phase == ShadowPhase.Recording || state.value.phase == ShadowPhase.Recognizing) return
        cancel()
        pcm = null
        mutableState.value = state.value.copy(hasRecording = false)
        try {
            val selectedEngine = engine ?: engineFactory().also { engine = it }
            selectedEngine.start()
            mutableState.value = state.value.copy(phase = ShadowPhase.Recording)
        } catch (_: Exception) {
            engine?.release(); engine = null
            mutableState.value = state.value.copy(phase = ShadowPhase.Error(ShadowError.ENGINE_FAILED))
        }
    }

    fun stop() {
        if (state.value.phase != ShadowPhase.Recording) { cancel(); return }
        val selectedEngine = engine ?: return
        val text = state.value.unit?.text ?: return
        val token = generation
        mutableState.value = state.value.copy(phase = ShadowPhase.Recognizing)
        job = scope.launch {
            try {
                val recording = selectedEngine.stop()
                if (token != generation) return@launch
                pcm = recording.pcm
                val phase = when {
                    recording.durationMs < 300 -> ShadowPhase.Idle
                    recording.transcript.isBlank() -> ShadowPhase.Error(ShadowError.NOT_HEARD)
                    else -> ShadowPhase.Result(TranscriptAligner.align(text, recording.transcript), null)
                }
                mutableState.value = state.value.copy(phase = phase, hasRecording = pcm != null)
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                if (token == generation) mutableState.value = state.value.copy(phase = ShadowPhase.Error(ShadowError.ENGINE_FAILED))
            }
        }
    }

    fun playMyRecording() {
        cancel()
        pcm?.let { PcmPlayer.play(it) }
    }

    fun cancel(pauseVideo: Boolean = true) {
        generation++
        job?.cancel(); job = null
        engine?.cancel()
        PcmPlayer.stop()
        if (pauseVideo) player.pause()
        seekObserved = false
        mutableState.value = state.value.copy(playingUnit = false,
            phase = if (state.value.phase == ShadowPhase.Recording || state.value.phase == ShadowPhase.Recognizing)
                ShadowPhase.Idle else state.value.phase)
    }

    fun release() {
        cancel()
        engine?.release(); engine = null
        player.setPlaybackRate(1f)
    }
}
