package com.ziaee.frenchreader.shadowing

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechEngineHelpersTest {
    @Test fun parseVoskText_readsTextField() {
        assertEquals("le chat dort", parseVoskText("{\n  \"text\" : \"le chat dort\"\n}"))
    }

    @Test fun parseVoskText_emptyOrBroken() {
        assertEquals("", parseVoskText("{\"text\" : \"\"}"))
        assertEquals("", parseVoskText("not json"))
    }

    @Test fun durationMsOf_uses16k() {
        assertEquals(1000L, durationMsOf(16_000))
        assertEquals(250L, durationMsOf(4_000))
    }
}
