package com.ziaee.frenchreader.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    private val sentence = "Le Mont-Blanc est la plus haute montagne d'Europe occidentale."

    @Test
    fun summarizeAsksForAtMostTwoSentencesInFrench() {
        val prompt = Prompts.summarize(sentence)
        assertTrue(prompt.userPrompt.contains(sentence))
        assertTrue(prompt.userPrompt.contains("2 phrases maximum"))
        assertTrue(prompt.systemPrompt.contains("français"))
        assertNull(prompt.grammar)
        assertEquals(120, prompt.maxTokens)
    }

    @Test
    fun explainGrammarNeverAsksModelToPickTheSentence() {
        // Regression guard for the spike's tense-misidentification failure: the prompt must
        // frame the given sentence as already chosen, never ask the model to find one.
        val prompt = Prompts.explainGrammar(sentence)
        assertTrue(prompt.userPrompt.contains(sentence))
        assertTrue(prompt.userPrompt.contains("cette phrase"))
        assertTrue(!prompt.userPrompt.contains("Choisis une phrase"))
        assertNull(prompt.grammar)
        assertEquals(150, prompt.maxTokens)
    }

    @Test
    fun simplifyToA2IncludesAWorkedExample() {
        // Regression guard for the spike's "barely simplified" failure.
        val prompt = Prompts.simplifyToA2(sentence)
        assertTrue(prompt.userPrompt.contains("Exemple"))
        assertTrue(prompt.userPrompt.contains(sentence))
        assertNull(prompt.grammar)
        assertEquals(200, prompt.maxTokens)
    }

    @Test
    fun extractVocabularyAttachesTheGbnfGrammar() {
        val prompt = Prompts.extractVocabulary(sentence)
        assertTrue(prompt.userPrompt.contains(sentence))
        assertEquals(VocabGrammar.GBNF, prompt.grammar)
        assertEquals(250, prompt.maxTokens)
    }
}
