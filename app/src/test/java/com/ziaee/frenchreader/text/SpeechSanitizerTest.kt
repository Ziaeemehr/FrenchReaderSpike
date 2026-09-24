package com.ziaee.frenchreader.text

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechSanitizerTest {
    @Test
    fun `removes pictographs symbols format characters and private use code points`() {
        val input = "Emoji 🍀 🔷 👍🏽 🇫🇷 👨‍👩‍👧 1️⃣ ❤️ → ⇒ ★ ✓ ■ ▶ ─═  fin"

        assertEquals("Emoji 1 fin", sanitizeForSpeech(input))
    }

    @Test
    fun `preserves French letters punctuation combining accents and speakable symbols`() {
        val input = "« é è ê ç œ Æ e\u0301 » — C’est 20 € ou 15 % ? + = < > & @ ° § © ® ™ × ÷ ± ½ ¼ ¾ # * ~ | / \\ ^ `"

        assertEquals(input, sanitizeForSpeech(input))
    }

    @Test
    fun `preserves Persian letters and diacritics`() {
        val input = "مَن فارسی می‌خوانم. ۱۲۳"

        assertEquals("مَن فارسی میخوانم. ۱۲۳", sanitizeForSpeech(input))
    }

    @Test
    fun `normalizes separators tabs and removed-symbol spacing while keeping newlines`() {
        assertEquals(
            "ACTIVITÉS\nBravo !",
            sanitizeForSpeech("  ACTIVITÉS\u00A0🍀  \n\tBravo 👏 !  ")
        )
    }
}
