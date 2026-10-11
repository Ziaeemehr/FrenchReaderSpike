package com.ziaee.frenchreader.grammar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GrammarSearchTest {
    private val notes = parseGrammarNotes(File("src/main/assets/grammar/notes.json").readText())
    private val index = GrammarSearchIndex(notes)
    private fun ids(query: String) = index.search(query).map { it.note.id }

    @Test
    fun `golden queries rank first`() {
        val queries = listOf(
            "bien que" to "bien-que", "Bien Que" to "bien-que",
            "passé composé" to "passe-compose", "passe compose" to "passe-compose",
            "فرق qui و que" to "qui-vs-que", "فرق qui و que كاربرد" to "qui-vs-que",
            "qui que" to "qui-vs-que", "depuis" to "depuis-pendant-il-y-a",
            "dont" to "dont-et-ou", "en" to "pronom-en", "y" to "pronom-y",
            "ماضی بعید" to "plus-que-parfait", "imparfait" to "imparfait",
            "savoir connaître" to "savoir-vs-connaitre", "savoir connaitre" to "savoir-vs-connaitre",
            "impératif" to "imperatif", "négation" to "negation", "ne pas" to "negation",
            "passé composé imparfait" to "passe-compose-vs-imparfait"
        )
        queries.forEach { (query, expected) -> assertEquals(query, expected, ids(query).firstOrNull()) }
    }

    @Test
    fun `golden queries appear in top three`() {
        val queries = listOf(
            "subjonctif" to "subjonctif-emplois", "وجه التزامی" to "subjonctif-emplois",
            "il faut que" to "subjonctif-emplois", "si" to "phrases-avec-si", "اگر" to "phrases-avec-si",
            "j'aurais" to "conditionnel-present", "j’aurais" to "conditionnel-present",
            "que" to "qui-vs-que", "جمله شرطی" to "phrases-avec-si",
            "est-ce que" to "interrogation", "مذکر مؤنث" to "genre-et-nombre"
        )
        queries.forEach { (query, expected) -> assertTrue("$query: ${ids(query)}", expected in ids(query).take(3)) }
        assertTrue(ids("ضمیر").take(3).any { it in setOf("pronom-en", "pronom-y") })
        assertEquals(ids("ضمیر"), ids("ضمير"))
        val joined = ids("میشود")
        assertTrue(joined.isNotEmpty())
        assertEquals(joined, ids("می‌شود"))
        assertEquals(joined, ids("می شود"))
    }

    @Test
    fun `folding normalizes both languages without changing display text`() {
        assertEquals("e c i oe ae", foldForGrammarSearch("É Ç Ï Œ Æ"))
        assertEquals("j'aurais qu'il l'ami d'iran", foldForGrammarSearch("j’aurais qu‘il lʼami d`Iran"))
        assertEquals("ی ی ک ه", foldForGrammarSearch("ي ى ك ة"))
        assertEquals("میشود", foldForGrammarSearch("مِی‌ـشَوُد\u0670\u200B\u200D\u200E\u200F\u2066\u2069"))
        assertEquals("0123456789 0123456789", foldForGrammarSearch("۰۱۲۳۴۵۶۷۸۹ ٠١٢٣٤٥٦٧٨٩"))
        assertEquals("bien que", foldForGrammarSearch(" **«Bien» … → Que** "))
        assertEquals("e", foldForGrammarSearch("e\u0301"))
        assertEquals("bien que", foldForGrammarSearch("`Bien` **que**"))
    }

    @Test
    fun `empty and nonsense queries have no results and stop words remain searchable`() {
        assertTrue(ids(" \n ").isEmpty())
        assertTrue(ids("xyzzzznotagrammarnote").isEmpty())
        listOf("en", "y", "que", "si", "de").forEach { assertTrue(it, ids(it).isNotEmpty()) }
        assertEquals(ids("qui que"), ids("فرق qui و que"))
        assertEquals(ids("qui que"), GrammarSearchIndex(notes.reversed()).search("qui que").map { it.note.id })
    }

    @Test
    fun `snippets highlight original accents ligatures and removed marks`() {
        val text = "Préface **cœur** et `école`, e\u0301cole; می‌شود."
        val note = notes.first().copy(
            titleFr = "Autre", titleFa = "دیگر", aliasesFr = emptyList(), aliasesFa = emptyList(),
            blocks = listOf(GrammarBlock.Prose("summary", listOf(GrammarParagraph("p", text))))
        )
        val search = GrammarSearchIndex(listOf(note))
        listOf("coeur", "ecole", "میشود").forEach { query ->
            val result = search.search(query).single()
            assertEquals(3, result.tier)
            val snippet = result.snippet!!
            assertTrue(snippet.highlights.isNotEmpty())
            snippet.highlights.forEach { range ->
                assertTrue(range.start >= 0 && range.end <= snippet.text.length)
                assertEquals(query, foldForGrammarSearch(snippet.text.substring(range.start, range.end)))
            }
            assertFalse(snippet.text.contains("**"))
            assertFalse(snippet.text.contains('`'))
        }
    }

    @Test
    fun `short tokens require exact matches and elisions keep their suffix`() {
        val note = notes.first().copy(
            titleFr = "L'ami qu'il connaît", titleFa = "دیگر", aliasesFr = emptyList(), aliasesFa = emptyList(),
            blocks = listOf(GrammarBlock.Prose("usage", listOf(GrammarParagraph("p", "enfant j'aurais d'Iran"))))
        )
        val search = GrammarSearchIndex(listOf(note))
        listOf("ami", "il", "aurais", "j’aurais", "iran", "conn").forEach {
            assertEquals(it, 1, search.search(it).size)
        }
        listOf("en", "j", "qu", "l", "d", "co", "ami nonsense").forEach {
            assertTrue(it, search.search(it).isEmpty())
        }
        assertEquals(4, search.search("ami aurais").single().tier)
        assertEquals(2, search.search("ami il").single().tier)
    }

    @Test
    fun `ranking prioritizes tiers then exact tokens and title matches`() {
        fun note(id: String, title: String, alias: String, summary: String, prose: String) = notes.first().copy(
            id = id, titleFr = title, titleFa = "", aliasesFr = listOf(alias), aliasesFa = emptyList(),
            blocks = listOf(
                GrammarBlock.Prose("summary", listOf(GrammarParagraph("p", summary))),
                GrammarBlock.Prose("usage", listOf(GrammarParagraph("p", prose)))
            )
        )
        val search = GrammarSearchIndex(listOf(
            note("body", "Other", "", "", "target"),
            note("summary", "Other", "", "target", ""),
            note("prefix", "Targeting topic", "", "", ""),
            note("alias", "Other topic", "target topic", "", ""),
            note("title", "Target topic", "", "", ""),
            note("exact", "Target", "", "", "")
        ))
        val results = search.search("target")
        assertEquals(listOf("exact", "title", "alias", "prefix", "summary", "body"), results.map { it.note.id })
        assertEquals(listOf(1, 2, 2, 2, 3, 4), results.map { it.tier })
    }

    @Test
    fun `real snippets have valid ranges and no markdown markers`() {
        listOf("aurais", "می‌شود", "mang", "déjà", "participe").forEach { query ->
            val snippets = index.search(query).mapNotNull { it.snippet }
            assertTrue(query, snippets.isNotEmpty())
            snippets.forEach { snippet ->
                assertFalse(snippet.text.contains("**"))
                assertFalse(snippet.text.contains('`'))
                assertTrue(snippet.highlights.isNotEmpty())
                snippet.highlights.forEach { range ->
                    assertTrue(range.start >= 0 && range.end > range.start && range.end <= snippet.text.length)
                    assertEquals(foldForGrammarSearch(query), foldForGrammarSearch(snippet.text.substring(range.start, range.end)))
                }
            }
        }
    }
}
