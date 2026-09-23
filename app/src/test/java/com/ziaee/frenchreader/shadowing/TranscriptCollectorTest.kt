package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptCollectorTest {
    @Test fun finalResultWinsWhenPresent() {
        val c = TranscriptCollector()
        c.onPartial(listOf("je voudrais"))
        assertEquals("je voudrais vous demander", c.final(listOf("je voudrais vous demander", "alt")))
    }

    @Test fun emptyOrMissingFinal_fallsBackToLastPartialBestHypothesis() {
        // Google on-device recognizer fed via EXTRA_AUDIO_SOURCE: text only in partials, final bundle null.
        val c = TranscriptCollector()
        c.onPartial(listOf("je voudrais vous"))
        c.onPartial(listOf("un individu suspect a-t-elle", "un individu suspect a-t-elle dit"))
        c.onPartial(emptyList()) // a trailing empty partial must not erase what was heard
        assertEquals("un individu suspect a-t-elle", c.final(null))
        assertEquals("un individu suspect a-t-elle", c.final(listOf("")))
    }

    @Test fun errorAfterPartials_stillReturnsWhatWasHeard() {
        val c = TranscriptCollector()
        c.onPartial(listOf("bonjour"))
        assertEquals("bonjour", c.onError())
    }

    @Test fun nothingHeard_isEmpty() {
        val c = TranscriptCollector()
        assertEquals("", c.final(null))
        assertEquals("", c.onError())
    }
}
