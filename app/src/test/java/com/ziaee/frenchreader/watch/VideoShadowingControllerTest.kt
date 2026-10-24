package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.shadowing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoShadowingControllerTest {
    private val cues = listOf(SubtitleCue(1000, 2000, "Le chat dort."), SubtitleCue(3000, 4000, "Bonjour !"))

    private class Engine : SpeechEngine {
        override val kind = SpeechEngineKind.ANDROID
        var started = 0
        var released = false
        var pending: CompletableDeferred<Recording>? = null
        override fun start() { started++ }
        override suspend fun stop() = pending?.await() ?: Recording("le chat", null, 1000)
        override fun cancel() = Unit
        override fun release() { released = true }
    }

    @Test fun selectAtUsesContainingMergedUnitOrNextUnit() = runTest {
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, {})
        c.setCues(listOf(
            SubtitleCue(1000, 2000, "Le chat"),
            SubtitleCue(2100, 3000, "dort."),
            SubtitleCue(4000, 5000, "Bonjour !"),
            SubtitleCue(6000, 7000, "Au revoir.")
        ))
        c.selectAt(2500)
        assertEquals("Le chat dort.", c.state.value.unit?.text)
        c.selectAt(3000)
        assertEquals(1, c.state.value.index)
        c.selectAt(4000)
        assertEquals(1, c.state.value.index)
        c.selectAt(5500)
        assertEquals(2, c.state.value.index)
        c.selectAt(9000)
        assertEquals(2, c.state.value.index)
        c.selectAt(0)
        assertEquals(0, c.state.value.index)
    }

    @Test fun selectAtCancelsPlaybackAndClearsRecordingResult() = runTest {
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, {})
        c.setCues(cues); c.record(); c.stop(); advanceUntilIdle()
        assertTrue(c.state.value.phase is ShadowPhase.Result)
        c.playUnit()
        val generation = c.generation
        c.selectAt(3500)
        assertEquals(1, c.state.value.index)
        assertFalse(c.state.value.playingUnit)
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertFalse(c.state.value.hasRecording)
        assertTrue(c.generation > generation)
    }

    @Test fun selectAtWithoutCaptionsIsInert() = runTest {
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, {})
        val generation = c.generation
        c.selectAt(3500)
        assertNull(c.state.value.unit)
        assertEquals(generation, c.generation)
    }

    @Test fun normalPlaybackNeverRequestsRecording() = runTest {
        var requested = 0
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, { requested++ })
        c.setCues(cues)
        c.setAutoRecord(true)
        c.onTime(1000); c.onTime(3000)
        assertEquals(0, requested)
        assertFalse(c.state.value.playingUnit)
    }

    @Test fun linePlaybackIgnoresStaleTimeAndPausesOnce() = runTest {
        var requested = 0
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, { requested++ })
        c.setCues(cues); c.setAutoRecord(true); c.playUnit()
        c.onTime(9000)
        assertTrue(c.state.value.playingUnit)
        c.onTime(1000); c.onTime(2149)
        assertTrue(c.state.value.playingUnit)
        c.onTime(2150); c.onTime(2300)
        assertFalse(c.state.value.playingUnit)
        assertEquals(1, requested)
        c.repeat(); c.onTime(1000); c.onTime(2150)
        assertEquals(2, requested)
    }

    @Test fun shortUnitCanFinishBetweenPlayerSamples() = runTest {
        var requested = 0
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, { requested++ })
        c.setCues(listOf(SubtitleCue(1000, 1050, "Oui !")))
        c.setAutoRecord(true); c.playUnit()
        c.onTime(900)
        c.onTime(1200)
        assertTrue(c.state.value.playingUnit)
        assertEquals(0, requested)
        c.onTime(1000)
        c.onTime(1200)
        assertFalse(c.state.value.playingUnit)
        assertEquals(1, requested)
    }

    @Test fun repeatRequiresSeekSampleFromCurrentPlayback() = runTest {
        val requests = mutableListOf<Int>()
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, { requests.add(it) })
        c.setCues(cues); c.setAutoRecord(true); c.playUnit()
        val old = c.generation
        c.onTime(1000, old); c.onTime(2150, old)
        c.repeat()
        val current = c.generation
        c.onTime(2000, current)
        c.onTime(2150, current); c.onTime(2350, current)
        c.onTime(1000, old); c.onTime(2150, current)
        c.onPlayerState(0, old)
        assertTrue(c.state.value.playingUnit)
        assertEquals(listOf(old), requests)
        c.onTime(1999, current)
        c.onPlayerState(0, old)
        c.onTime(2150, old)
        assertTrue(c.state.value.playingUnit)
        c.onTime(2150, current)
        assertEquals(listOf(old, current), requests)
    }

    @Test fun staleRecordingRequestsDoNotCancelNewActionsOrStartMic() = runTest {
        for (action in listOf<(VideoShadowingController) -> Unit>({ it.next() }, { it.repeat() }, { it.stop() })) {
            val engine = Engine()
            var request: Int? = null
            val c = VideoShadowingController(this, YoutubePlayerController(), { engine }, { request = it })
            c.setCues(cues); c.setAutoRecord(true); c.playUnit()
            c.onTime(1000); c.onTime(2150)
            action(c)
            val generation = c.generation
            val state = c.state.value
            val accepted = c.beginRecordingRequest(request!!)
            if (accepted != null) c.record()
            assertNull(accepted)
            assertEquals(generation, c.generation)
            assertEquals(state, c.state.value)
            assertEquals(0, engine.started)
        }
    }

    @Test fun currentRecordingRequestCanStartMic() = runTest {
        val engine = Engine()
        var request: Int? = null
        val c = VideoShadowingController(this, YoutubePlayerController(), { engine }, { request = it })
        c.setCues(cues); c.setAutoRecord(true); c.playUnit()
        c.onTime(1000); c.onTime(2150)
        val expectedGeneration = c.generation + 1
        val accepted = c.beginRecordingRequest(request!!)
        assertNotNull(accepted)
        assertEquals(expectedGeneration, accepted!!)
        c.record()
        assertEquals(1, engine.started)
    }

    @Test fun endedCompletesArmedUnitOnceWithoutEndMargin() = runTest {
        val requests = mutableListOf<Int>()
        val c = VideoShadowingController(this, YoutubePlayerController(), { Engine() }, { requests.add(it) })
        c.setCues(cues); c.setAutoRecord(true)
        c.onPlayerState(0)
        c.playUnit()
        c.onPlayerState(0)
        assertTrue(c.state.value.playingUnit)
        assertTrue(requests.isEmpty())
        c.onTime(1000); c.onTime(2000)
        assertTrue(c.state.value.playingUnit)
        c.onPlayerState(1)
        assertTrue(c.state.value.playingUnit)
        val generation = c.generation
        c.onPlayerState(0); c.onPlayerState(0); c.onTime(2150)
        assertFalse(c.state.value.playingUnit)
        assertEquals(listOf(generation), requests)
        c.repeat(); c.onTime(1000); c.cancel(); c.onPlayerState(0)
        assertEquals(1, requests.size)
    }

    @Test fun recordingAlignsAndNavigationClearsResult() = runTest {
        val engine = Engine()
        val c = VideoShadowingController(this, YoutubePlayerController(), { engine }, {})
        c.setCues(cues); c.record(); c.stop(); advanceUntilIdle()
        val alignment = (c.state.value.phase as ShadowPhase.Result).alignment
        assertEquals(2, alignment.matched)
        assertEquals(3, alignment.total)
        assertFalse(c.state.value.hasRecording)
        c.next()
        assertEquals(1, c.state.value.index)
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        c.next(); assertEquals(1, c.state.value.index)
        c.previous(); assertEquals(0, c.state.value.index)
        c.release(); assertTrue(engine.released)
    }

    @Test fun navigationCancelsPendingRecognition() = runTest {
        val engine = Engine().apply { pending = CompletableDeferred() }
        val c = VideoShadowingController(this, YoutubePlayerController(), { engine }, {})
        c.setCues(cues); c.record(); c.stop(); runCurrent()
        c.next()
        engine.pending!!.complete(Recording("le chat dort", null, 1000))
        advanceUntilIdle()
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertEquals(1, c.state.value.index)
    }

    @Test fun noCaptionsAndUnsupportedSpeedsAreInert() = runTest {
        val engine = Engine()
        val c = VideoShadowingController(this, YoutubePlayerController(), { engine }, {})
        c.playUnit(); c.record(); c.next(); c.previous()
        assertEquals(0, engine.started)
        c.setSpeed(0.75f); c.setSpeed(2f)
        assertEquals(0.75f, c.state.value.speed)
    }
}
