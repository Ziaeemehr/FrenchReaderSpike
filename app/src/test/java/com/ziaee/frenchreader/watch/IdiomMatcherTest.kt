package com.ziaee.frenchreader.watch

import com.ziaee.frenchreader.comprehension.InMemoryLemmaLexicon
import org.junit.Assert.*
import org.junit.Test

class IdiomMatcherTest {
    private val lemmas = InMemoryLemmaLexicon(mapOf(
        "pris" to setOf("prendre"), "prise" to setOf("prendre"), "ai" to setOf("avoir"),
        "a" to setOf("avoir"), "est" to setOf("être"), "tapé" to setOf("taper"),
        "coûte" to setOf("coûter"), "sommes" to setOf("être"), "allés" to setOf("aller"),
        "substitut" to setOf("en"), "ailleurs" to setOf("y")
    ), setOf("bon", "jour", "avoir", "faim"))
    private val entries = listOf(
        IdiomEntry("prendre la mouche", "verb", "to get offended"),
        IdiomEntry("avoir faim", "verb", "to be hungry"),
        IdiomEntry("se taper la cloche", "verb", "to eat well"),
        IdiomEntry("en avoir marre", "verb", "to be fed up"),
        IdiomEntry("coûter les yeux de la tête", "verb", "to cost a fortune"),
        IdiomEntry("tout de suite", "adv", "immediately"),
        IdiomEntry("s'en aller", "verb", "to leave"),
        IdiomEntry("y aller", "verb", "to go there")
    )
    private fun matcher(extra: List<IdiomEntry> = emptyList()) = IdiomMatcher(InMemoryIdiomLexicon(entries + extra), lemmas)

    @Test fun requiredExamplesAndExactSpans() {
        listOf(
            Triple("Il a pris la mouche hier", "prendre la mouche", "pris la mouche"),
            Triple("je n'ai pas faim", "avoir faim", "ai pas faim"),
            Triple("Elle s'est tapé la cloche", "se taper la cloche", "s'est tapé la cloche"),
            Triple("j'en ai vraiment marre", "en avoir marre", "en ai vraiment marre"),
            Triple("ça coûte les yeux de la tête", "coûter les yeux de la tête", "coûte les yeux de la tête"),
            Triple("tout de suite", "tout de suite", "tout de suite")
        ).forEach { (cue, expression, span) ->
            val match = matcher().match(cue).single()
            assertEquals(expression, match.expression)
            assertEquals(span, cue.substring(match.startChar, match.endChar))
            assertTrue(match.gloss.isNotBlank())
        }
    }

    @Test fun negativesAndGapLimit() {
        listOf("tout le monde a de la suite", "la mouche a pris", "ai vraiment bien trop faim",
            "pris une la mouche", "en ai quelque marre").forEach { assertTrue(it, matcher().match(it).isEmpty()) }
        assertEquals("avoir faim", matcher().match("ai vraiment bien faim").single().expression)
    }

    @Test fun reflexivesElisionsAndOffsets() {
        listOf("me", "m'", "te", "t'", "se", "s'", "nous", "vous").forEach {
            assertEquals("se taper la cloche", matcher().match("$it tapé la cloche").single().expression)
        }
        val cue = "😀 Nous nous en sommes allés !"
        val match = matcher().match(cue).single()
        assertEquals("s'en aller", match.expression)
        assertEquals("Nous nous en sommes allés", cue.substring(match.startChar, match.endChar))
        listOf("'", "’", "ʼ").forEach { apostrophe ->
            assertEquals("se taper la cloche", matcher().match("Elle s${apostrophe}est tapé la cloche").single().expression)
        }
        assertEquals(listOf("s'", "en", "aller"), entries.first { it.expression == "s'en aller" }.words)
    }

    @Test fun enAndYMustMatchLiterally() {
        assertTrue(matcher().match("substitut ai marre").isEmpty())
        assertTrue(matcher().match("ailleurs aller").isEmpty())
        assertEquals("y aller", matcher().match("y aller").single().expression)
    }

    @Test fun longestWinsAndMatchesDoNotOverlap() {
        val result = matcher(listOf(IdiomEntry("prendre la mouche hier", "verb", "longer"),
            IdiomEntry("la mouche", "noun", "fly"))).match("pris la mouche hier puis ai faim")
        assertEquals(listOf("prendre la mouche hier", "avoir faim"), result.map { it.expression })
        assertTrue(result[0].endChar <= result[1].startChar)
    }

    @Test fun noisyPairsAndNonverbPairsCannotHaveGaps() {
        val matcher = matcher(listOf(IdiomEntry("bon jour", "noun", "noisy"), IdiomEntry("à côté", "adv", "nearby")))
        assertTrue(matcher.match("bon jour").isEmpty())
        assertTrue(matcher.match("à vraiment côté").isEmpty())
        assertEquals("à côté", matcher.match("à côté").single().expression)
        assertEquals("avoir faim", matcher.match("ai faim").single().expression)
    }

    @Test fun expressionsDoNotCrossSentenceEnds() {
        val matcher = matcher(listOf(IdiomEntry("prendre la mouche", "verb", "to get offended")))
        assertTrue(matcher.match("Il va prendre. La mouche est là.").isEmpty())
        assertTrue(matcher.match("Il va prendre\nla mouche").isEmpty())
        assertEquals("prendre la mouche", matcher.match("Il va prendre la mouche.").single().expression)
    }
}
