package com.ziaee.frenchreader.translate

import android.content.Context
import com.ziaee.frenchreader.translate.llm.LlmCache
import com.ziaee.frenchreader.translate.llm.LlmTranslator
import kotlinx.coroutines.CancellationException
import com.ziaee.frenchreader.text.isRtlText
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TranslationRepository(private val context: Context) {
    private val llmCache = LlmCache(context)
    private val cache = TranslationCache(context)
    private val overrides = TranslationOverrides(context)

    /** Only flashcard examples consult bundled translations; reader/meaning paths stay separate. */
    suspend fun getOrTranslateExample(text: String, targetLang: String, forceModel: Boolean = false): Result<ExampleTranslation> = withContext(Dispatchers.IO) {
        try {
            val bundled by lazy { com.ziaee.frenchreader.content.BundledSentenceTranslations.forContext(context).get(text, targetLang) }
            val translatorResult by lazy {
                try { Result.success(LlmTranslator.forContext(context)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Result.failure(error) }
            }
            Result.success(resolveExampleTranslation(
                override = { overrides.getOverride(text, targetLang) },
                bundled = { bundled },
                personal = { translatorResult.getOrNull()?.let { llmCache.getRegenerated(text, targetLang, it.cacheId) } },
                live = {
                    translatorResult.getOrThrow()?.let { model ->
                        (if (forceModel) null else llmCache.get(text, targetLang, model.cacheId))
                            ?: model.translateLines(listOf(text), null, null, targetLang).single().also {
                                if (forceModel) llmCache.putRegenerated(text, targetLang, model.cacheId, it)
                                else llmCache.put(text, targetLang, model.cacheId, it)
                            }
                    }
                },
                google = { cache.get(text, targetLang) ?: TranslationService.translate(text, targetLang).also {
                    cache.put(text, targetLang, it)
                } },
                forceModel = forceModel
            ))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { Result.failure(error) }
    }

    suspend fun getOrTranslate(
        text: String,
        targetLang: String,
        attempt: Int = 0
    ): Result<String> = withContext(Dispatchers.IO) {
        overrides.getOverride(text, targetLang)?.let { return@withContext Result.success(it) }
        val translator = try { LlmTranslator.forContext(context) } catch (_: Exception) { null }
        translator?.let { llmCache.get(text, targetLang, it.cacheId) }?.let {
            return@withContext Result.success(it)
        }
        cache.get(text, targetLang)?.let { return@withContext Result.success(it) }
        try {
            val translated = try {
                translator?.translateLines(listOf(text), null, null, targetLang)?.single()?.also {
                    llmCache.put(text, targetLang, translator.cacheId, it)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { null }
            if (translated != null) return@withContext Result.success(translated)
            val google = TranslationService.translate(text, targetLang)
            cache.put(text, targetLang, google)
            Result.success(google)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (e: Exception) {
            if (attempt < 1) getOrTranslate(text, targetLang, attempt + 1)
            else Result.failure(e)
        }
    }

    /**
     * Paragraph translation for the reader: null when the paragraph is already in
     * [targetLang], so it isn't shown translated into itself. A same-language result is
     * cached as the source text itself, and older cached echoes read back as null too.
     */
    suspend fun getOrTranslateParagraph(
        text: String,
        targetLang: String,
        attempt: Int = 0
    ): Result<String?> = withContext(Dispatchers.IO) {
        overrides.getOverride(text, targetLang)?.let { return@withContext Result.success(it) }
        if (isRtlTarget(targetLang) && isRtlText(text)) return@withContext Result.success(null)
        llmParagraph(text, null, targetLang)?.let { translated ->
            return@withContext Result.success(translated.takeUnless { TranslationService.isSameText(it, text) })
        }
        googleParagraph(text, targetLang, attempt)
    }

    private suspend fun googleParagraph(
        text: String,
        targetLang: String,
        attempt: Int = 0
    ): Result<String?> = withContext(Dispatchers.IO) {
        overrides.getOverride(text, targetLang)?.let { return@withContext Result.success(it) }
        if (isRtlTarget(targetLang) && isRtlText(text)) return@withContext Result.success(null)
        cache.get(text, targetLang)?.let { cached ->
            return@withContext Result.success(cached.takeUnless { TranslationService.isSameText(it, text) })
        }
        try {
            val translated = TranslationService.translateUnlessSameLanguage(text, targetLang)
            cache.put(text, targetLang, translated ?: text)
            Result.success(translated)
        } catch (e: Exception) {
            if (attempt < 1) googleParagraph(text, targetLang, attempt + 1)
            else Result.failure(e)
        }
    }

    /** Paragraph cache and overrides are shared with the context-free reader path. */
    suspend fun getOrTranslateParagraphInContext(
        text: String, before: String?, targetLang: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        overrides.getOverride(text, targetLang)?.let { return@withContext Result.success(it) }
        if (isRtlTarget(targetLang) && isRtlText(text)) return@withContext Result.success(null)
        llmParagraph(text, paragraphTranslationContext(text, before), targetLang)?.let {
            return@withContext Result.success(it.takeUnless { value -> TranslationService.isSameText(value, text) })
        }
        cache.get(text, targetLang)?.let { cached ->
            return@withContext Result.success(cached.takeUnless { TranslationService.isSameText(it, text) })
        }
        val context = paragraphTranslationContext(text, before)
            ?: return@withContext googleParagraph(text, targetLang)
        repeat(2) {
            try {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val lines = TranslationService.translateLines(listOf(context, text), targetLang, sourceLang = "auto")
                if (lines?.size == 2) {
                    val translated = lines[1]
                    if (shouldFallbackFromParagraphContext(text, translated)) {
                        return@withContext googleParagraph(text, targetLang)
                    }
                    cache.put(text, targetLang, translated)
                    return@withContext Result.success(translated)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Retry once, then use the established paragraph fallback.
            }
        }
        googleParagraph(text, targetLang)
    }

    suspend fun getOrTranslateInContext(
        targets: List<String>, before: String?, targetLang: String, after: String? = null
    ): Map<String, String?> = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, String?>()
        val missing = mutableListOf<String>()
        for (text in targets.distinct()) {
            val override = overrides.getOverride(text, targetLang)
            when {
                override != null -> results[text] = override
                isRtlTarget(targetLang) && isRtlText(text) -> results[text] = null
                else -> missing.add(text)
            }
        }
        if (missing.isNotEmpty()) {
            try {
                val translator = LlmTranslator.forContext(context)
                if (translator != null) {
                    for (text in missing.toList()) {
                        llmCache.get(text, targetLang, translator.cacheId)?.let { value ->
                            results[text] = value.takeUnless { TranslationService.isSameText(it, text) }
                            missing.remove(text)
                        }
                    }
                    // A Google hit is still available offline, after overrides and the model cache.
                    for (text in missing.toList()) {
                        cache.get(text, targetLang)?.let { value ->
                            results[text] = value.takeUnless { TranslationService.isSameText(it, text) }
                            missing.remove(text)
                        }
                    }
                    if (missing.isNotEmpty()) {
                        val pending = missing.toList()
                        val translated = translator.translateLines(pending, before, after, targetLang)
                        pending.zip(translated).forEach { (text, value) ->
                            llmCache.put(text, targetLang, translator.cacheId, value)
                            results[text] = value.takeUnless { TranslationService.isSameText(it, text) }
                            missing.remove(text)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Missing entries continue through the established Google path.
            }
            for (text in missing.toList()) {
                cache.get(text, targetLang)?.let { cached ->
                    results[text] = cached.takeUnless { TranslationService.isSameText(it, text) }
                    missing.remove(text)
                }
            }
        }
        if (missing.isNotEmpty()) {
            val lines = contextTranslationLines(targets, before)
            val translated = runCatching { TranslationService.translateLines(lines, targetLang) }.getOrNull()
            if (translated != null) {
                missingContextTranslations(targets, before, translated, missing.toSet()).forEach { (text, value) ->
                    val result = value.takeUnless { TranslationService.isSameText(it, text) }
                    cache.put(text, targetLang, result ?: text)
                    results[text] = result
                }
            } else {
                missing.forEach { text ->
                    googleParagraph(text, targetLang).onSuccess { results[text] = it }
                }
            }
        }
        results
    }

    suspend fun setUserTranslation(text: String, targetLang: String, translation: String?) = withContext(Dispatchers.IO) {
        if (translation.isNullOrBlank()) overrides.removeOverride(text, targetLang)
        else overrides.putOverride(text, targetLang, translation.trim())
    }

    suspend fun resetTranslation(text: String, targetLang: String) = withContext(Dispatchers.IO) {
        overrides.removeOverride(text, targetLang)
        cache.remove(text, targetLang)
        LlmTranslator.forContext(context)?.let { llmCache.remove(text, targetLang, it.cacheId) }
    }

    suspend fun hasOverride(text: String, targetLang: String): Boolean = withContext(Dispatchers.IO) {
        overrides.getOverride(text, targetLang) != null
    }

    private fun llmParagraph(text: String, before: String?, targetLang: String): String? = try {
        // Texts are French: a French target is the same-language case, which Google's detection handles.
        val translator = LlmTranslator.forContext(context)
            ?.takeUnless { TranslationService.baseLanguage(targetLang) == "fr" }
        if (translator == null) null else llmCache.get(text, targetLang, translator.cacheId)
            ?: cache.get(text, targetLang)
            ?: translator.translateLines(listOf(text), before, null, targetLang).single().also {
                llmCache.put(text, targetLang, translator.cacheId, it)
            }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    suspend fun meaningCandidates(
        word: String, lemmas: List<String>, sentence: String?, targetLang: String
    ): MeaningCandidates = withContext(Dispatchers.IO) {
        overrides.getOverride(word, targetLang)?.let { return@withContext MeaningCandidates(it, emptyList()) }
        val llmMeaning = try {
            val translator = LlmTranslator.forContext(context)
            if (translator == null) null else {
                val cached = llmCache.getMeaning(word, sentence, targetLang, translator.cacheId)
                if (cached != null) {
                    // Prepared meanings must be immediately usable offline; fetching Google's
                    // alternative senses here would block a perfectly good model-cache hit.
                    val meaning = polishMeaning(word, cached, targetLang)
                    return@withContext MeaningCandidates(meaning, listOf(MeaningOption(meaning, null, null)))
                }
                val meaning = translator.meaning(word, sentence, targetLang).also {
                    llmCache.putMeaning(word, sentence, targetLang, translator.cacheId, it)
                }
                polishMeaning(word, meaning, targetLang)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (llmMeaning == null) return@withContext googleMeaningCandidates(word, lemmas, sentence, targetLang)
        val googleOptions = try {
            googleMeaningCandidates(word, lemmas, sentence, targetLang).options
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        MeaningCandidates(llmMeaning, (listOf(MeaningOption(llmMeaning, null, null)) + googleOptions)
            .distinctBy { normalizePersian(it.text) })
    }

    private suspend fun googleMeaningCandidates(
        word: String, lemmas: List<String>, sentence: String?, targetLang: String
    ): MeaningCandidates = withContext(Dispatchers.IO) {
        fun fallback(best: String?) = MeaningCandidates(best?.let { polishMeaning(word, it, targetLang) }, emptyList())
        try {
            // The user's own meaning is shown exactly as written.
            overrides.getOverride(word, targetLang)?.let { return@withContext MeaningCandidates(it, emptyList()) }
            var senses = TranslationService.wordSenses(word, "en")
            for (lemma in lemmas) {
                if (senses.isNotEmpty()) break
                senses = TranslationService.wordSenses(lemma, "en")
            }
            val kept = filterWordSenses(senses)
            if (kept.isEmpty()) return@withContext fallback(getOrTranslate(word, targetLang).getOrNull())
            val translatedSentence = sentence?.takeIf { it.isNotBlank() }?.let {
                getOrTranslate(it, "en").getOrNull()
            }
            val ranked = rankWordSenses(kept, translatedSentence, sentence?.let { guessFrenchPartOfSpeech(it, word) }).take(6)
            val english = TranslationService.baseLanguage(targetLang) == "en"
            val translations = if (english) ranked.map { it.term } else {
                coroutineScope {
                    ranked.map { sense ->
                        async(Dispatchers.IO) {
                            val dictionary = TranslationService.wordSenses(sense.term, targetLang, sourceLang = "en")
                            val headTerm = if (dictionary.isEmpty() && sense.term.trim().split(Regex("\\s+")).size > 1) {
                                englishSenseHeadWord(sense.term)?.let { head ->
                                    val headSenses = TranslationService.wordSenses(head, targetLang, sourceLang = "en")
                                        .filter { it.partOfSpeech.equals(sense.partOfSpeech, ignoreCase = true) }
                                    selectDictionaryTerm(headSenses, sense.partOfSpeech)
                                }
                            } else null
                            selectDictionaryTerm(dictionary, sense.partOfSpeech) ?: headTerm
                                ?: if (allowPlainSenseTranslation(sense.term)) {
                                    TranslationService.translate(sense.term, targetLang, sourceLang = "en")
                                } else null
                        }
                    }.awaitAll()
                }
            }
            if (translations.any { it != null && it.isBlank() }) {
                return@withContext fallback(getOrTranslate(word, targetLang).getOrNull())
            }
            val options = ranked.zip(translations).mapNotNull { (sense, text) ->
                text ?: return@mapNotNull null
                MeaningOption(polishMeaning(word, text, targetLang, sense.partOfSpeech), sense.term.takeUnless { english }, sense.partOfSpeech)
            }.distinctBy { normalizePersian(it.text) }
            if (options.isEmpty()) fallback(getOrTranslate(word, targetLang).getOrNull())
            else MeaningCandidates(options.first().text, options)
        } catch (error: Exception) {
            fallback(runCatching { getOrTranslate(word, targetLang).getOrNull() }.getOrNull())
        }
    }

    private fun isRtlTarget(targetLang: String) =
        TranslationService.baseLanguage(targetLang) in setOf("fa", "ar", "he", "iw", "ur", "ps")
}

data class MeaningOption(val text: String, val gloss: String?, val partOfSpeech: String?)

data class MeaningCandidates(val best: String?, val options: List<MeaningOption>)

internal fun normalizePersian(text: String): String = text.replace('ي', 'ی').replace('ك', 'ک')
    .replace("\u200C", "")
    .replace(Regex("\\p{M}+"), "")
    .replace(Regex("\\s+"), " ").trim().lowercase(java.util.Locale.ROOT)

internal fun filterWordSenses(senses: List<WordSense>): List<WordSense> {
    val topScores = senses.groupBy { it.partOfSpeech }.mapValues { (_, group) -> group.maxOf { it.score } }
    val counts = mutableMapOf<String, Int>()
    return senses.filter { sense ->
        val count = counts.getOrDefault(sense.partOfSpeech, 0)
        val keep = (sense.score == 0.0 || sense.score >= topScores.getValue(sense.partOfSpeech) * 0.01) && count < 4
        if (keep) counts[sense.partOfSpeech] = count + 1
        keep
    }.take(8)
}

internal fun rankWordSenses(senses: List<WordSense>, translatedSentence: String?, guessedPos: String? = null): List<WordSense> {
    fun words(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(text.lowercase(java.util.Locale.ROOT)).map { it.value }.toList()
    val sentenceWords = words(translatedSentence.orEmpty())
    val ignored = setOf("to", "a", "an", "the", "be")
    fun matches(sense: WordSense): Boolean {
        val allWords = words(sense.term)
        val significant = allWords.filterNot { it in ignored }.ifEmpty { allWords }
        return significant.isNotEmpty() && significant.all { word ->
            val prefix = word.take(minOf(word.length, 5))
            sentenceWords.any { it.startsWith(prefix) }
        }
    }
    return if (senses.any(::matches)) senses.sortedByDescending(::matches)
    else senses.sortedByDescending { guessedPos != null && it.partOfSpeech.equals(guessedPos, ignoreCase = true) }
}

internal fun contextTranslationLines(targets: List<String>, before: String?): List<String> =
    listOfNotNull(before) + targets

internal fun selectDictionaryTerm(senses: List<WordSense>, partOfSpeech: String): String? {
    val pos = senses.firstOrNull { it.partOfSpeech.equals(partOfSpeech, ignoreCase = true) }?.partOfSpeech
        ?: senses.firstOrNull()?.partOfSpeech ?: return null
    return senses.filter { it.partOfSpeech == pos }.maxByOrNull { it.score }?.term
}

internal fun missingContextTranslations(
    targets: List<String>, before: String?, translated: List<String>, missing: Set<String>
): List<Pair<String, String>> = targets.zip(translated.drop(if (before != null) 1 else 0))
    .filter { it.first in missing }

/** A small context hint; dictionary sentence matches remain authoritative. */
internal fun guessFrenchPartOfSpeech(sentence: String, word: String): String? {
    fun tokens(text: String) = Regex("[\\p{L}]+['’]?")
        .findAll(text.lowercase(java.util.Locale.ROOT)).map { it.value.replace('’', '\'') }.toList()
    val sentenceTokens = tokens(sentence)
    val target = tokens(word).singleOrNull() ?: return null
    val index = sentenceTokens.indexOf(target)
    if (index < 0) return null
    val previous = sentenceTokens.getOrNull(index - 1)
    val determiners = setOf("le", "la", "les", "l'", "un", "une", "des", "du", "au", "aux",
        "ce", "cet", "cette", "ces", "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses",
        "notre", "nos", "votre", "vos", "leur", "leurs", "quel", "quelle", "quels", "quelles",
        "chaque", "plusieurs", "certains", "certaines")
    val verbContexts = setOf("je", "j'", "tu", "il", "elle", "on", "nous", "vous", "ils", "elles",
        "ne", "n'", "de", "d'", "à", "pour", "sans", "par", "après")
    val conjugatedVerbs = setOf("ai", "as", "a", "avons", "avez", "ont", "avais", "avait", "avions", "aviez", "avaient",
        "suis", "es", "est", "sommes", "êtes", "sont", "étais", "était", "étions", "étiez", "étaient",
        "été", "être", "avoir", "eu", "serai", "seras", "sera", "serons", "serez", "seront",
        "aurai", "auras", "aura", "aurons", "aurez", "auront", "serais", "serait", "serions", "seriez", "seraient",
        "aurais", "aurait", "aurions", "auriez", "auraient", "peux", "peut", "pouvons", "pouvez", "peuvent",
        "pouvais", "pouvait", "pouvions", "pouviez", "pouvaient", "pourrais", "pourrait", "pourrions", "pourriez", "pourraient",
        "dois", "doit", "devons", "devez", "doivent", "devais", "devait", "devions", "deviez", "devaient",
        "veux", "veut", "voulons", "voulez", "veulent", "voulais", "voulait", "voulions", "vouliez", "voulaient",
        "vais", "vas", "va", "allons", "allez", "vont", "faut",
        "devrais", "devrait", "devrions", "devriez", "devraient")
    val infinitive = listOf("er", "ir", "re", "oir").any { target.endsWith(it) }
    if (infinitive && (previous == "l'" ||
            previous in setOf("le", "la", "les") && sentenceTokens.getOrNull(index - 2) in conjugatedVerbs)) return "verb"
    if (previous in determiners) return "noun"
    return if (previous in verbContexts || previous in conjugatedVerbs || infinitive) "verb" else null
}

/** Long dictionary senses translate literally too often when no dictionary entry exists. */
internal fun allowPlainSenseTranslation(englishSense: String): Boolean =
    englishSense.trim().split(Regex("\\s+")).size <= 2

internal fun englishSenseHeadWord(englishSense: String): String? =
    englishSense.trim().split(Regex("\\s+")).firstOrNull {
        it.isNotBlank() && it.lowercase(java.util.Locale.ROOT) !in setOf("to", "be", "a", "an", "the")
    }
