package com.ziaee.frenchreader.tts

import com.ziaee.frenchreader.language.LanguageCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicesTest {
    @Test fun `voicesFor returns German catalog voices and XTTS`() {
        val voices = voicesFor("de").map { it.id }
        assertEquals(LanguageCatalog.forCode("de").edgeVoiceIds, voices.dropLast(1))
        assertTrue(voices.last().startsWith(XTTS_VOICE_PREFIX))
        assertFalse(voices.any { it.startsWith("fr-") })
    }

    @Test fun `resolveVoice replaces a voice from another language`() {
        assertEquals("de-DE-KatjaNeural", resolveVoice("fr-FR-HenriNeural", "de"))
        assertEquals("de-DE-ConradNeural", resolveVoice("de-DE-ConradNeural", "de"))
        assertEquals("xtts:local", resolveVoice("xtts:local", "de"))
    }
}
