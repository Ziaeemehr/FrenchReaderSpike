package com.ziaee.frenchreader.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptScrollTest {
    @Test fun shortSentenceIsCentered() {
        assertEquals(-70, transcriptCenterOffset(180, 40))
    }

    @Test fun leadingPaddingAllowsFirstAndLastSentencesToCenter() {
        assertEquals(20, transcriptCenterOffset(180, 40, 90))
        assertEquals(90, transcriptCenterOffset(180, 240, 90))
    }

    @Test fun translationHeightIsIncluded() {
        assertEquals(-50, transcriptCenterOffset(180, 80))
    }

    @Test fun tallSentenceStartsAtTopSoItsWordsCanBeScrolled() {
        assertEquals(0, transcriptCenterOffset(180, 240))
        assertEquals(0, transcriptCenterOffset(180, 180))
    }

    @Test fun unmeasuredViewportDoesNotProducePositiveOffset() {
        assertEquals(0, transcriptCenterOffset(0, 40))
        assertEquals(-70, transcriptCenterOffset(181, 40))
    }
    @Test fun paddingTracksActualViewport() {
        assertEquals(82, transcriptEdgePadding(180, 8))
        assertEquals(192, transcriptEdgePadding(400, 8))
        assertEquals(0, transcriptEdgePadding(0, 8))
    }

    @Test fun resizedViewportKeepsItemCenteredWithItsNewPadding() {
        assertEquals(12, transcriptCenterOffset(180, 40, transcriptEdgePadding(180, 8)))
        assertEquals(12, transcriptCenterOffset(400, 40, transcriptEdgePadding(400, 8)))
    }

    @Test fun itemCapReservesUnfadedNeighbourLinesAndSpacing() {
        assertEquals(72, transcriptItemHeight(200, 24, 32, 8))
        assertEquals(172, transcriptItemHeight(300, 24, 32, 8))
        assertEquals(1, transcriptItemHeight(80, 24, 32, 8))
    }

    @Test fun internalScrollCentersActiveRangeAndClampsAtEnds() {
        assertEquals(0, transcriptActiveOffset(0, 24, 80, 320))
        assertEquals(92, transcriptActiveOffset(120, 144, 80, 320))
        assertEquals(240, transcriptActiveOffset(296, 320, 80, 320))
        assertEquals(0, transcriptActiveOffset(0, 24, 400, 320))
    }
}
