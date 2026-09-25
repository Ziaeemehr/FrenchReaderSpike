package com.ziaee.frenchreader.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvWordImportTest {
    @Test
    fun parsesQuotedFieldsWithCommasNewlinesAndQuotes() {
        val rows = parseDelimited("﻿a,\"b, c\",\"line1\nline2\",\"say \"\"hi\"\"\"\r\nx,y\r\n\r\n")
        assertEquals(listOf(listOf("a", "b, c", "line1\nline2", "say \"hi\""), listOf("x", "y")), rows)
    }

    @Test
    fun detectsTabAndSemicolonDelimiters() {
        assertEquals(listOf(listOf("chat", "cat")), parseDelimited("chat\tcat"))
        assertEquals(listOf(listOf("mot", "sens"), listOf("chat", "cat, feline")), parseDelimited("mot;sens\nchat;cat, feline"))
    }

    @Test
    fun headerColumnsCanBeInAnyOrderAndLanguage() {
        val file = parseCsvWords("معنی,کلمه,جمله\nگربه,chat,Le chat dort.\n,chien,\n")
        assertEquals(CsvWord("chat", "گربه", "Le chat dort.", null), file.words[0])
        assertEquals(CsvWord("chien", null, "", null), file.words[1])
        assertFalse(file.hasDeckColumn)
    }

    @Test
    fun withoutHeaderColumnsAreWordMeaningSentenceDeck() {
        val file = parseCsvWords("bonjour,hello,Bonjour !,Basics\n  ,skipped\nmerci,thanks\n")
        assertEquals(listOf("bonjour", "merci"), file.words.map { it.word })
        assertEquals("Basics", file.words[0].deck)
        assertNull(file.words[1].deck)
        assertTrue(file.hasDeckColumn)
    }

    @Test
    fun planSkipsWordsAlreadyInTheDeckAndRepeatsInTheFile() {
        val file = parseCsvWords("word,deck\nChat,Animaux\nchien,Animaux\nchat,Animaux\nbleu,\n")
        val existing = setOf("animaux" to "chien")

        val byColumn = planCsvImport(file, CsvImportTarget.DeckColumn("Mes mots"), existing)
        assertEquals(listOf("Animaux" to "Chat", "Mes mots" to "bleu"), byColumn.words.map { it.deck to it.word.word })
        assertEquals(listOf("Animaux", "Mes mots"), byColumn.deckNames)
        assertEquals(2, byColumn.skipped)

        val single = planCsvImport(file, CsvImportTarget.Deck("Nouveau"), existing)
        assertEquals(listOf("Chat", "chien", "bleu"), single.words.map { it.word.word })
        assertEquals(1, single.skipped)
    }
}
