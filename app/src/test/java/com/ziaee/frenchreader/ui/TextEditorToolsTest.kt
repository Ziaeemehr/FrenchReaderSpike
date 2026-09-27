package com.ziaee.frenchreader.ui

import com.ziaee.frenchreader.ui.shared.findMatches
import com.ziaee.frenchreader.ui.shared.joinWrappedLines
import com.ziaee.frenchreader.ui.shared.replaceAll
import com.ziaee.frenchreader.ui.shared.tidyWhitespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorToolsTest {
    @Test
    fun findMatchesIsLiteralCaseOptionalAndNonOverlapping() {
        assertEquals(listOf(0..2, 4..6), findMatches("a.b A.B aab", "a.b", false, false))
        assertEquals(listOf(0..2), findMatches("a.b A.B", "a.b", true, false))
        assertEquals(listOf(0..1, 2..3), findMatches("aaaa", "aa", true, false))
        assertTrue(findMatches("text", "", false, false).isEmpty())
    }

    @Test
    fun wholeWordTreatsAccentedLettersAndDigitsAsWordCharacters() {
        val text = "été été2 préété été!"
        assertEquals(listOf(0..2, 16..18), findMatches(text, "été", true, true))
        assertEquals(listOf(3..5, 14..16), findMatches("un mot, motus mot", "mot", true, true))
    }

    @Test
    fun replaceAllUsesTheSameMatchingRules() {
        assertEquals("chat chien2 chaton" to 1, replaceAll("Chien chien2 chaton", "chien", "chat", false, true))
        assertEquals("x.x.x" to 3, replaceAll("a.a.a", "a", "x", true, false))
        assertEquals("unchanged" to 0, replaceAll("unchanged", "", "x", false, false))
    }

    @Test
    fun joinWrappedLinesPreservesParagraphsAndRepairsLowercaseHyphenation() {
        val input = "Un exem-\nple simple.\nSuite\n\n\nMot-\nParis\nfin"
        assertEquals("Un exemple simple. Suite\n\nMot- Paris fin", joinWrappedLines(input))
        assertEquals("début suite", joinWrappedLines("début\r\nsuite"))
        assertEquals("un deux\n\ntrois", joinWrappedLines("un  \n deux\n  \t\ntrois"))
    }

    @Test
    fun tidyWhitespaceNormalizesHorizontalAndExcessVerticalWhitespace() {
        val input = "  Un\t\ttexte   ici   \nligne\t \n\n\n\n  fin  "
        assertEquals("Un texte ici\nligne\n\nfin", tidyWhitespace(input))
    }
}
