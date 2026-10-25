package com.ziaee.frenchreader.translate

private const val ZWNJ = '‌'

// Present stems of common verbs, by infinitive. Compound verbs end in one of these light verbs,
// so the table covers most phrases Google gives as an imperative, subjunctive or present tense.
private val presentStems: Map<String, String> = mapOf(
    "کردن" to "کن", "دادن" to "ده", "گرفتن" to "گیر", "زدن" to "زن", "گذاشتن" to "گذار", "بودن" to "باش",
    "شدن" to "شو", "رفتن" to "رو", "آمدن" to "آی آ", "خوردن" to "خور", "گفتن" to "گوی گو", "خواندن" to "خوان",
    "آوردن" to "آور", "بردن" to "بر", "کشیدن" to "کش", "ماندن" to "مان", "خوابیدن" to "خواب", "نوشتن" to "نویس",
    "دیدن" to "بین", "شنیدن" to "شنو", "پوشیدن" to "پوش", "نشستن" to "نشین", "گشتن" to "گرد", "پرسیدن" to "پرس",
    "ساختن" to "ساز", "فرستادن" to "فرست", "بستن" to "بند", "پختن" to "پز", "خریدن" to "خر", "رسیدن" to "رس",
    "دانستن" to "دان", "خواستن" to "خواه", "انداختن" to "انداز", "رساندن" to "رسان", "فروختن" to "فروش",
    "گذراندن" to "گذران", "داشتن" to "دار", "توانستن" to "توان",
).flatMap { (infinitive, stems) -> stems.split(" ").map { it to infinitive } }.toMap()
private val personEndings = listOf("ند", "ید", "یم", "د", "ی", "م", "")

/** "بخورید", "می‌روند", "دارند" → the infinitive; null when the token is not a known verb form. */
private fun verbFormInfinitive(token: String): String? {
    val negativeContinuous = token.startsWith("نمی$ZWNJ")
    val stripped = token.removePrefix("نمی$ZWNJ").removePrefix("می$ZWNJ")
    // Each candidate base, and whether reaching it removed a negative ن.
    val bases = buildList {
        add(stripped to negativeContinuous)
        // "بیا" is ب + آ: بیاید → آید, بیاور → آور; "بینداز" is ب + انداز.
        if (stripped.startsWith("بیا")) add("آ" + stripped.removePrefix("بیا") to false)
        if (stripped.startsWith("بی")) add("ا" + stripped.removePrefix("بی") to false)
        if (stripped.startsWith("ب")) add(stripped.removePrefix("ب") to false)
        // "نیا" is ن + آ, like "بیا".
        if (stripped.startsWith("نیا")) add("آ" + stripped.removePrefix("نیا") to true)
        if (stripped.startsWith("ن")) add(stripped.removePrefix("ن") to true)
    }
    for ((base, negative) in bases) for (ending in personEndings) {
        if (!base.endsWith(ending)) continue
        val stem = base.removeSuffix(ending)
        // "بگوید"/"بیایید": the stem's own final ی merges with the ending.
        val infinitive = presentStems[stem] ?: presentStems[stem + "ی"] ?: continue
        return if (negative) negativeInfinitive(infinitive) else infinitive
    }
    return null
}

/** "پاسخ نده" must stay negative: ندادن, نیامدن. */
private fun negativeInfinitive(infinitive: String) =
    if (infinitive.startsWith("آ")) "نیا" + infinitive.removePrefix("آ") else "ن$infinitive"

private val infinitives = presentStems.values.toSet()
private val prepositions = setOf("به", "از", "در", "با", "برای", "تا", "بر")

/**
 * "بستن بند", "آویختن از دار": an infinitive before the last word means the last word is a noun.
 * A noun ending in دن is not one: "بدن را بشوی" has an object marker after it, "به لندن برو" a preposition before.
 */
private fun hasEarlierInfinitive(tokens: List<String>): Boolean = tokens.dropLast(1).withIndex().any { (i, token) ->
    val shaped = token in infinitives || (token.length >= 3 && (token.endsWith("دن") || token.endsWith("تن")))
    shaped && tokens.getOrNull(i - 1) !in prepositions && "را" !in tokens.subList(i + 1, tokens.size)
}

private val notInfinitives = setOf("pour", "sur", "par", "leur", "hier", "premier", "dernier", "cher", "fier",
    "mer", "hiver", "soir", "noir", "entre", "contre", "notre", "votre", "autre", "lettre", "livre", "quatre",
    "cuir", "désir", "loisir", "plaisir", "avenir", "souvenir", "espoir", "miroir", "fer", "amer", "clair",
    "trottoir", "couloir", "tiroir", "mouchoir", "rasoir", "comptoir", "arrosoir", "bonsoir", "boulanger",
    "étranger", "papier", "panier", "cahier", "escalier", "quartier", "métier", "janvier", "février", "fermier", "pompier", "policier", "infirmier", "ouvrier", "cuisinier",
    "épicier", "pâtissier", "banquier", "plombier", "danger", "verger", "léger", "chevalier")

private fun firstToken(word: String) = word.trim().split(Regex("\\s+")).first().lowercase()

/** A French dictionary entry is a sentence when it carries end punctuation or is very long. */
private fun isSentence(word: String) =
    word.trim().let { it.endsWith(".") || it.endsWith("!") || it.endsWith("?") || it.endsWith("…") ||
        it.split(Regex("\\s+")).size > 8 }

// "un kilo de farine" keeps its number: "یک کیلو آرد".
private val quantityNouns = setOf("kilo", "litre", "gramme", "mètre", "morceau", "peu", "tas", "verre", "tasse",
    "bouteille", "paquet", "boîte", "tranche", "part", "pot", "sac", "douzaine", "centaine", "millier", "million",
    "milliard", "demi", "quart", "seul", "seule", "jour", "an", "mois", "heure", "minute", "fois")

internal fun isFrenchInfinitivePhrase(word: String): Boolean {
    val first = firstToken(word)
    if (first == "se" || first.startsWith("s'") || first.startsWith("s’") || first == "être") return true
    // Nouns such as "devoir" or "souvenir" are left as Google gives them.
    return first !in notInfinitives && Regex("(er|ir|re|oir)$").containsMatchIn(first) &&
        !word.trim().lowercase().startsWith("un ") && !word.trim().lowercase().startsWith("une ")
}

/**
 * Google translates a French dictionary entry as a phrase, so "un quartier" becomes "یک محله"
 * and "faire du sport" the imperative "ورزش کن". A meaning reads as a dictionary headword:
 * no article, and verbs as infinitives ("ورزش کردن"). Whole sentences are kept as they are.
 */
internal fun polishPersianMeaning(word: String, meaning: String, partOfSpeech: String? = null): String {
    if (isSentence(word)) return meaning
    var s = meaning.trim().trimEnd('.', '!', '؟', '?').trim()
    val lower = word.trim().lowercase()
    val article = lower.startsWith("un ") || lower.startsWith("une ")
    val counted = lower.split(Regex("\\s+")).getOrNull(1) in quantityNouns
    if (article && !counted && s.startsWith("یک ")) s = s.removePrefix("یک ").trim()
    // A dictionary option's own part of speech wins: the noun "بده" (debt) is not the verb "بده" (give).
    val verb = partOfSpeech == null || partOfSpeech.equals("verb", ignoreCase = true)
    if (verb && isFrenchInfinitivePhrase(word)) {
        s = s.removePrefix("برای ").trim()
        val tokens = s.split(' ').toMutableList()
        val last = tokens.lastOrNull().orEmpty()
        // A meaning that already has its infinitive ("بستن بند") ends in a noun, not a verb form.
        if (hasEarlierInfinitive(tokens)) {
            // Nothing to convert.
        } else if (tokens.size >= 2 && tokens[tokens.size - 2] == "داشته" && last.startsWith("باش")) {
            tokens.removeAt(tokens.lastIndex)
            tokens[tokens.lastIndex] = "داشتن"
        } else if (tokens.size >= 2 && tokens[tokens.size - 2] in setOf("می", "نمی") && verbFormInfinitive(last) != null) {
            tokens.removeAt(tokens.size - 2)
            tokens[tokens.lastIndex] = verbFormInfinitive(last)!!
        } else {
            verbFormInfinitive(last)?.let { tokens[tokens.lastIndex] = it }
        }
        s = tokens.joinToString(" ")
    }
    return s.ifBlank { meaning }
}

internal fun polishMeaning(word: String, meaning: String, targetLang: String, partOfSpeech: String? = null): String =
    if (TranslationService.baseLanguage(targetLang) == "fa") polishPersianMeaning(word, meaning, partOfSpeech) else meaning
