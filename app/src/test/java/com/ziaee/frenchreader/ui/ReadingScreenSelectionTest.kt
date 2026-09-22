package com.ziaee.frenchreader.ui

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingScreenSelectionTest {
    private val sentence = "Le chat noir dort dans l'appartement."

    @Test
    fun extractWordAtIsolatesWordTouchingOffset() {
        // "chat" spans indices 3..6 (0-indexed, end-exclusive) in the sentence above.
        assertEquals("chat", extractWordAt(sentence, 4))
    }

    @Test
    fun extractWordAtStripsElidedArticleAcrossApostrophe() {
        val offsetInsideAppartement = sentence.indexOf("appartement") + 2
        assertEquals("appartement", extractWordAt(sentence, offsetInsideAppartement))
    }

    @Test
    fun classifySelectionReturnsNullForCollapsedSelection() {
        assertNull(classifySelection(sentence, TextRange(4, 4)))
    }

    @Test
    fun classifySelectionReturnsWordWhenRangeIsWithinOneWord() {
        // Selecting just "ha" inside "chat" (indices 4..6) must snap to the whole word.
        val kind = classifySelection(sentence, TextRange(4, 6))
        assertTrue(kind is SelectionKind.Word)
        assertEquals("chat", (kind as SelectionKind.Word).word)
        assertEquals(TextRange(3, 7), kind.range)
    }

    @Test
    fun classifySelectionReturnsWordAndDropsElidedArticleForApostropheSpan() {
        // A raw selection covering the whole "l'appartement" token (as a native
        // double-tap word-selection might report it) must resolve to just
        // "appartement", matching extractWordAt's existing dictionary-lookup rule.
        val start = sentence.indexOf("l'appartement")
        val end = start + "l'appartement".length
        val kind = classifySelection(sentence, TextRange(start, end))
        assertTrue(kind is SelectionKind.Word)
        assertEquals("appartement", (kind as SelectionKind.Word).word)
    }

    @Test
    fun classifySelectionReturnsPhraseForMultiWordRange() {
        val start = sentence.indexOf("chat")
        val end = sentence.indexOf("dort") + "dort".length
        val kind = classifySelection(sentence, TextRange(start, end))
        assertTrue(kind is SelectionKind.Phrase)
        assertEquals("chat noir dort", (kind as SelectionKind.Phrase).text)
        assertEquals(TextRange(start, end), kind.range)
    }

    @Test
    fun classifySelectionSnapsPartialWordsAtBothEndsOfAPhrase() {
        // Selection starts mid-"chat" and ends mid-"dort" -- both ends must
        // snap outward to their full word boundaries.
        val start = sentence.indexOf("chat") + 2
        val end = sentence.indexOf("dort") + 2
        val kind = classifySelection(sentence, TextRange(start, end))
        assertTrue(kind is SelectionKind.Phrase)
        assertEquals("chat noir dort", (kind as SelectionKind.Phrase).text)
    }

    @Test
    fun classifySelectionReturnsNullWhenRangeHasNoWordCharacters() {
        // A selection that lands entirely on punctuation/whitespace with no
        // adjacent word character at either edge is not classifiable.
        assertNull(classifySelection("   ", TextRange(0, 2)))
    }
}
