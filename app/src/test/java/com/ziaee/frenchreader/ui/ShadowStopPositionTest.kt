package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.tts.SentenceBoundary
import org.junit.Assert.assertEquals
import org.junit.Test

class ShadowStopPositionTest {
    @Test fun midParagraphSentence_stopsAtItsEnd() =
        assertEquals(3000L, shadowStopPositionMs(SentenceBoundary("s", 1000.0, 2000.0), itemDurationMs = 9000))

    @Test fun lastSentenceEndingAtOrPastItemEnd_stopsJustBeforeItemEnd() =
        assertEquals(4950L, shadowStopPositionMs(SentenceBoundary("s", 2000.0, 3100.0), itemDurationMs = 5000))

    @Test fun unknownDuration_usesSentenceEnd() =
        assertEquals(3000L, shadowStopPositionMs(SentenceBoundary("s", 1000.0, 2000.0), itemDurationMs = -1))

    @Test fun neverBeforeSentenceStart() =
        assertEquals(1000L, shadowStopPositionMs(SentenceBoundary("s", 1000.0, 10.0), itemDurationMs = 1020))
}
