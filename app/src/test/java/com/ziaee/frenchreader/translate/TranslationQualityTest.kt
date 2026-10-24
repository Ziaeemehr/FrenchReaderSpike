package com.ziaee.frenchreader.translate

import org.junit.Assert.*
import org.junit.Test

class TranslationQualityTest {
    @Test fun englishSenseHeadWordSkipsLeadingFunctionWords() {
        assertEquals("listen", englishSenseHeadWord("listen to"))
        assertEquals("able", englishSenseHeadWord("be able"))
        assertEquals("put", englishSenseHeadWord("put out"))
        assertEquals("publish", englishSenseHeadWord("publish"))
        assertEquals("able", englishSenseHeadWord("  TO  be   able  "))
        assertNull(englishSenseHeadWord("to be a an the"))
    }

    @Test fun frenchContextGuessesPartOfSpeech() {
        assertEquals("verb", guessFrenchPartOfSpeech("J'avais envie de pouvoir publier un podcast de façon plus régulière.", "pouvoir"))
        for (prefix in listOf("le", "LA", "ce", "mon", "plusieurs", "de la")) {
            assertEquals(prefix, "noun", guessFrenchPartOfSpeech("$prefix pouvoir politique", "pouvoir"))
        }
        for (prefix in listOf("je", "ne", "n’", "d'", "qu'il peut", "pour", "avant de", "après", "avais")) {
            assertEquals(prefix, "verb", guessFrenchPartOfSpeech("$prefix publier", "publier"))
        }
        for (prefix in listOf("elle", "ne", "n’", "peut", "avais", "pour", "d’", "qu’il")) {
            assertEquals(prefix, "verb", guessFrenchPartOfSpeech("$prefix marche", "marche"))
        }
        assertEquals("noun", guessFrenchPartOfSpeech("l’autorité", "autorité"))
        assertEquals("noun", guessFrenchPartOfSpeech("ma marche", "marche"))
        assertEquals("verb", guessFrenchPartOfSpeech("Publier demain", "publier"))
        assertNull(guessFrenchPartOfSpeech("Bonjour monde", "monde"))
        assertNull(guessFrenchPartOfSpeech("le pouvoir", "absent"))
    }

    @Test fun objectPronounsBeforeInfinitivesAreVerbContext() {
        assertEquals("verb", guessFrenchPartOfSpeech("vous devriez l'écouter", "écouter"))
        assertEquals("verb", guessFrenchPartOfSpeech("vous devriez l’entendre", "entendre"))
        for (pronoun in listOf("le", "la", "les")) {
            assertEquals("verb", guessFrenchPartOfSpeech("je vais $pronoun faire", "faire"))
            assertEquals("verb", guessFrenchPartOfSpeech("vous devriez $pronoun choisir", "choisir"))
        }
        assertEquals("noun", guessFrenchPartOfSpeech("le pouvoir politique", "pouvoir"))
    }

    @Test fun plainSenseFallbackRejectsLongEnglishPhrases() {
        assertTrue(allowPlainSenseTranslation("free"))
        assertTrue(allowPlainSenseTranslation("  be   free  "))
        assertFalse(allowPlainSenseTranslation("be at a loose end"))
        assertFalse(allowPlainSenseTranslation("to be free"))
    }

    @Test fun posRankingIsStableAndSentenceMatchStillWins() {
        val senses = listOf(WordSense("noun", "power", 0.9), WordSense("verb", "can", 0.5),
            WordSense("verb", "be able", 0.4), WordSense("noun", "authority", 0.3))
        assertEquals(listOf(senses[1], senses[2], senses[0], senses[3]), rankWordSenses(senses, "unrelated", "verb"))
        assertEquals(listOf(senses[0], senses[3], senses[1], senses[2]), rankWordSenses(senses, null, "noun"))
        assertEquals(senses, rankWordSenses(senses, "power", "verb"))
    }

    @Test fun missingDictionaryIsEmpty() {
        assertTrue(parseWordSenses("[[[\"سلام\",\"salut\"]],null,\"fr\"]").isEmpty())
        assertTrue(parseWordSenses("[[[\"سلام\",\"salut\"]]]").isEmpty())
    }

    @Test fun scoredTermsKeepPosOrderAndRemoveDuplicates() {
        val body = """[[],[
            ["noun",["بانک","ساحل"],[["ساحل",["rive"],null,0.2],["بانک",["banque"],null,0.9],["بانک",[],null,0.1]],"banque",1],
            ["verb",["ذخیره کردن","بانک"],null,"banquer",2]
        ],"fr"]"""
        assertEquals(listOf(WordSense("noun", "بانک", 0.9), WordSense("noun", "ساحل", 0.2),
            WordSense("verb", "ذخیره کردن", 0.0)), parseWordSenses(body))
    }

    @Test fun malformedDictionaryIsEmpty() {
        listOf("not json", "{}", "[[],[null]]", "[[],[[\"noun\",[],42]]]", "[[],[[\"noun\",[],[[\"term\"]]]]]").forEach {
            assertTrue(parseWordSenses(it).isEmpty())
        }
    }

    @Test fun translatedLinesRequireExactNonblankAlignment() {
        assertEquals(listOf("یک", "دو"), splitTranslatedLines(" یک \n دو ", 2))
        assertNull(splitTranslatedLines("یک", 2))
        assertNull(splitTranslatedLines("یک\n\nدو", 3))
        assertNull(splitTranslatedLines("یک\nدو\n", 2))
    }

    @Test fun persianNormalization() {
        assertEquals("یک کتاب", normalizePersian("  يَك\u200C   كِتاب  "))
    }

    @Test fun contextRankingMatchesPrefixesAndAllSignificantWordsStably() {
        val senses = listOf(WordSense("noun", "power", 0.9), WordSense("verb", "be able to", 0.3),
            WordSense("verb", "publish", 0.2), WordSense("verb", "issue a book", 0.1))
        assertEquals(listOf(senses[1], senses[2], senses[0], senses[3]),
            rankWordSenses(senses, "I wanted to be ABLE to start publishing a podcast."))
        assertEquals(senses, rankWordSenses(senses, null))
        assertEquals(senses, rankWordSenses(senses, "   "))
        assertEquals(senses, rankWordSenses(senses, "powerful issues"))
    }

    @Test fun contextRankingIgnoresArticlesButRequiresWholeShortWords() {
        val senses = listOf(WordSense("verb", "issue a book", 0.5), WordSense("verb", "to publish", 0.4),
            WordSense("noun", "an apple", 0.3), WordSense("verb", "be", 0.2))
        assertEquals(listOf(senses[1], senses[0], senses[2], senses[3]), rankWordSenses(senses, "publishing"))
        assertEquals(listOf(senses[0], senses[1], senses[2], senses[3]), rankWordSenses(senses, "issued books"))
        assertEquals(listOf(senses[3], senses[0], senses[1], senses[2]), rankWordSenses(senses, "be"))
        assertEquals(senses, rankWordSenses(senses, "app"))
    }

    @Test fun filteringUsesEachPosTopScoreAndKeepsZerosAndThreshold() {
        val senses = listOf(WordSense("verb", "publish", 0.4), WordSense("verb", "issue", 0.004),
            WordSense("verb", "unlikely", 0.0039), WordSense("verb", "unknown", 0.0),
            WordSense("noun", "power", 0.002), WordSense("noun", "ability", 0.00002),
            WordSense("noun", "unlikely noun", 0.000019))
        assertEquals(listOf(senses[0], senses[1], senses[3], senses[4], senses[5]), filterWordSenses(senses))
    }

    @Test fun filteringCapsPerPosAndTotalWithoutReordering() {
        val verbs = (1..6).map { WordSense("verb", "verb$it", 0.0) }
        val nouns = (1..6).map { WordSense("noun", "noun$it", 0.0) }
        val adjectives = listOf(WordSense("adjective", "last", 1.0))
        assertEquals(verbs.take(4) + nouns.take(4), filterWordSenses(verbs + nouns + adjectives))
        assertTrue(filterWordSenses(emptyList()).isEmpty())
    }

    @Test fun translatedLinesSupportWindowsNewlines() {
        assertEquals(listOf("one", "two"), splitTranslatedLines("one\r\ntwo", 2))
        assertNull(splitTranslatedLines("one\ntwo", 1))
        assertNull(splitTranslatedLines("  ", 1))
    }

    @Test fun dictionarySelectionUsesMatchingPosAndHighestScore() {
        val senses = listOf(WordSense("noun", "انتشار", 0.9),
            WordSense("verb", "چاپ کردن", 0.000245), WordSense("verb", "منتشر کردن", 0.0013))
        assertEquals("منتشر کردن", selectDictionaryTerm(senses, "verb"))
        assertEquals("منتشر کردن", selectDictionaryTerm(senses, "VERB"))
        assertEquals("انتشار", selectDictionaryTerm(senses, "adjective"))
        assertNull(selectDictionaryTerm(emptyList(), "verb"))
    }

    @Test fun publishDictionaryParsesInfinitivesInsteadOfPurposeTranslation() {
        val body = """[[["منتشر کند","publish"]],[["verb",["منتشر کردن","چاپ کردن"],
            [["چاپ کردن",["print","publish"],null,2.45E-4],
             ["منتشر کردن",["publish","broadcast"],null,0.0013]]]],"en"]"""
        assertEquals("منتشر کردن", selectDictionaryTerm(parseWordSenses(body), "verb"))
    }

    @Test fun contextRetainsCachedMiddleTargetsAndOnlyStoresMissingTranslations() {
        val targets = listOf("B", "C", "D")
        assertEquals(listOf("A", "B", "C", "D"), contextTranslationLines(targets, "A"))
        assertEquals(listOf("D" to "translated D"), missingContextTranslations(targets, "A",
            listOf("translated A", "translated B", "translated C", "translated D"), setOf("D")))
        assertEquals(listOf("B" to "translated B"), missingContextTranslations(targets, null,
            listOf("translated B", "translated C", "translated D"), setOf("B")))
        assertEquals(listOf("B", "B", "C"), contextTranslationLines(listOf("B", "C"), "B"))
    }

    @Test fun googleSingleFallbackPreservesQueryAndDictionaryParameters() {
        val primary = googleSingleUrl("publish a book", "fa", "en", true, false)
        val fallback = googleSingleUrl("publish a book", "fa", "en", true, true)
        assertTrue(primary.startsWith("https://translate.googleapis.com/translate_a/single?client=gtx&"))
        assertTrue(fallback.startsWith("https://clients5.google.com/translate_a/single?client=dict-chrome-ex&"))
        assertEquals(primary.substringAfter("&sl="), fallback.substringAfter("&sl="))
        assertTrue(fallback.contains("&dt=t&dt=bd&q=publish+a+book"))
        assertFalse(googleSingleUrl("hello", "fr", "auto", false, true).contains("dt=bd"))
    }

    @Test fun googleSingleRetriesHtmlAndExceptionsButKeepsValidPrimary() {
        val valid = "[[[\"منتشر کند\",\"publish\"]],null,\"en\"]"
        assertEquals(valid, requestGoogleWithFallback { chrome ->
            assertFalse(chrome)
            valid
        })
        val attempts = mutableListOf<Boolean>()
        assertEquals(valid, requestGoogleWithFallback { chrome ->
            attempts.add(chrome)
            if (chrome) valid else "<html>Sorry</html>"
        })
        assertEquals(listOf(false, true), attempts)
        assertEquals(valid, requestGoogleWithFallback { chrome ->
            if (!chrome) throw java.io.IOException("blocked")
            valid
        })
    }

    @Test(expected = java.io.IOException::class)
    fun googleSinglePropagatesFallbackFailure() {
        requestGoogleWithFallback { throw java.io.IOException("blocked") }
    }
}
