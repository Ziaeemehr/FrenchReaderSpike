package com.ziaee.frenchreader.shadowing

import com.ziaee.frenchreader.data.ShadowAttempt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine(var next: Recording) : SpeechEngine {
    override val kind = SpeechEngineKind.VOSK
    var started = 0; var cancelled = 0
    override fun start() { started++ }
    override suspend fun stop() = next
    override fun cancel() { cancelled++ }
    override fun release() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShadowingControllerTest {
    private val saved = mutableListOf<ShadowAttempt>()
    private var studyMs = 0L
    private var clock = 10_000L
    private var playing = false

    private fun TestScope.controller(engine: FakeEngine) = ShadowingController(
        scope = this, engineFactory = { engine },
        saveAttempt = { saved += it }, addStudyTimeMs = { studyMs += it },
        now = { clock }, isPlaying = { playing }
    ).apply {
        setEnabled(true)
        setTarget(textId = 7, ref = SentenceRef(1, 2), text = "le chat dort", expectedMs = 1000)
    }

    @Test fun pressAndRelease_scoresAndSaves() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("le chat", ShortArray(8000), 500)))
        assertTrue(c.pressStart()); clock += 2_000; c.pressEnd(); advanceUntilIdle()
        val phase = c.state.value.phase as ShadowPhase.Result
        assertEquals(2, phase.alignment.matched); assertEquals(3, phase.alignment.total)
        assertEquals(1, saved.size)
        assertEquals(ShadowAttempt(textId = 7, chunkIndex = 1, sentenceIndex = 2, matched = 2, total = 3, engine = "vosk", timestampMs = clock), saved[0])
        assertEquals(2_000L, studyMs)
        assertTrue(c.state.value.hasRecording)
    }

    @Test fun paceMeasuredFromRecording() = runTest(StandardTestDispatcher()) {
        // 0.5 s silence + 1.2 s loud signal: speech 1200 ms vs expected 1000 ms -> 1.2, GOOD.
        val pcm = ShortArray(8000) + ShortArray(19_200) { (if (it % 2 == 0) 6000 else -6000).toShort() }
        val c = controller(FakeEngine(Recording("le chat dort", pcm, 1700)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        val pace = (c.state.value.phase as ShadowPhase.Result).pace!!
        assertEquals(1.2f, pace.ratio, 0.05f)
        assertEquals(PaceRating.GOOD, pace.rating)
        assertEquals(1.2f, saved.single().paceRatio!!, 0.05f)
    }

    @Test fun noPcm_noPaceButStillScored() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("le chat dort", null, 1500)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        assertEquals(null, (c.state.value.phase as ShadowPhase.Result).pace)
        assertEquals(null, saved.single().paceRatio)
        assertFalse(c.state.value.hasRecording)
    }

    @Test fun emptyTranscript_errorAndNothingSaved() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("", ShortArray(8000), 500)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        assertEquals(ShadowPhase.Error(ShadowError.NOT_HEARD), c.state.value.phase)
        assertTrue(saved.isEmpty())
    }

    @Test fun tooShortRecording_ignored() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("le", ShortArray(100), 120)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertTrue(saved.isEmpty())
    }

    @Test fun cannotRecordWhilePlaying() = runTest(StandardTestDispatcher()) {
        val engine = FakeEngine(Recording("x", ShortArray(8000), 500))
        val c = controller(engine)
        playing = true
        assertFalse(c.pressStart())
        assertEquals(0, engine.started)
    }

    @Test fun cancel_whileRecording_savesNothing() = runTest(StandardTestDispatcher()) {
        val engine = FakeEngine(Recording("le chat dort", ShortArray(8000), 500))
        val c = controller(engine)
        c.pressStart(); c.cancel(); advanceUntilIdle()
        assertEquals(1, engine.cancelled)
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertTrue(saved.isEmpty())
    }

    @Test fun newTarget_clearsResultAndRecording() = runTest(StandardTestDispatcher()) {
        val c = controller(FakeEngine(Recording("le chat dort", ShortArray(8000), 500)))
        c.pressStart(); c.pressEnd(); advanceUntilIdle()
        c.setTarget(7, SentenceRef(1, 3), "autre phrase", expectedMs = 1000)
        assertEquals(ShadowPhase.Idle, c.state.value.phase)
        assertFalse(c.state.value.hasRecording)
    }
}
