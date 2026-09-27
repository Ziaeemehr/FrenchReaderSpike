package com.ziaee.frenchreader.text

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDirectionDetectorTest {
    @Test
    fun `Persian text is right-to-left`() {
        assertTrue(isRtlText("این یک پیام دوستانه است."))
    }

    @Test
    fun `French text is left-to-right`() {
        assertFalse(isRtlText("Comment s'est passée la soirée au restaurant ?"))
    }

    @Test
    fun `leading neutrals are skipped before Persian`() {
        assertTrue(isRtlText("📩 ۱. متن سؤال"))
        assertTrue(isRtlText("⚠️ 12 - « نکته »"))
        assertTrue(isRtlText("[ ] فعل‌ها"))
    }

    @Test
    fun `first strong character decides mixed text`() {
        assertFalse(isRtlText("Passé composé → برای اتفاقات مشخص"))
        assertTrue(isRtlText("استفاده از tu، نه vous"))
    }

    @Test
    fun `text without strong characters is left-to-right`() {
        assertFalse(isRtlText(""))
        assertFalse(isRtlText("123 — 🍀 !"))
    }
}
