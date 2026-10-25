package com.ziaee.frenchreader.translate

private const val ZWNJ = "‌"
// Explicit ranges: Android's ICU regex and the JVM disagree on script property names.
private const val LETTER = "[\\u0621-\\u063A\\u0641-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06C0\\u06CC]"
// Only before a word ending like a verb (م ی د ت: می‌روم، می‌رفت), so the noun می ("می ناب") stays apart.
private val verbPrefix = Regex("(?<![\\p{L}\\u200C])(ن?می) +(?=$LETTER+[میدت](?![\\p{L}\\u200C]))")
private val joinedSuffix = Regex("(?<=$LETTER) +(ها|های|هایی|هایم|هایت|هایش|هایمان|هایتان|هایشان|ترین)(?![\\p{L}\\u200C])")
// "مکالمه ای" → "مکالمه‌ای": after a final silent ه these endings are written with a half-space.
private val afterSilentHe = Regex("(?<!\\S)(\\S*ه) +(ای|ایی|ام|ات|اش|ایم|اید|اند)(?![\\p{L}\\u200C])( +کاش)?")
// Function words, and words whose final ه is pronounced, are followed by a separate word instead
// ("که ای کاش", "به ماه ای دوست").
private val notSilentHe = setOf("به", "که", "نه", "چه", "آنچه", "هرچه", "آنکه", "ماه", "راه", "شاه", "کوه", "گروه",
    "نگاه", "گاه", "تباه", "اشتباه", "سیاه", "گیاه", "پناه", "کوتاه", "دانشگاه", "فروشگاه", "ایستگاه", "باشگاه",
    "آگاه", "جایگاه", "کلاه", "گناه", "چاه", "سپاه", "شبه", "فرودگاه", "آرامگاه", "پایگاه", "کارگاه", "نمایشگاه")
private val comma = Regex("(?<=$LETTER) *, *")
private val semicolon = Regex("(?<=$LETTER) *; *")
private val question = Regex("(?<=$LETTER) *\\?")
private val spaceBeforeStop = Regex("(?<=$LETTER) +(?=[.!،؛؟])")
private val repeatedSpaces = Regex("[ \\t]+")

/**
 * Machine translations into Persian often write "می خواهم" for "می‌خواهم", Arabic ي/ك,
 * and Latin punctuation. Only Persian-adjacent text changes, so French and numbers stay as
 * they are; line breaks are kept because line batches are split on them.
 */
internal fun polishPersian(text: String): String {
    // Spaces first, so a fresh result already looks like the same text polished again on a cache read.
    var s = repeatedSpaces.replace(text, " ").replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
    s = verbPrefix.replace(s) { it.groupValues[1] + ZWNJ }
    s = joinedSuffix.replace(s) { ZWNJ + it.groupValues[1] }
    s = afterSilentHe.replace(s) { m ->
        val (word, ending, kash) = m.destructured
        if (word in notSilentHe || kash.isNotEmpty()) m.value else word + ZWNJ + ending
    }
    s = comma.replace(s, "، ")
    s = semicolon.replace(s, "؛ ")
    s = question.replace(s, "؟")
    s = spaceBeforeStop.replace(s, "")
    return s.lines().joinToString("\n") { it.trimEnd() }
}

internal fun polishTranslation(text: String, targetLang: String): String =
    if (TranslationService.baseLanguage(targetLang) == "fa") polishPersian(text) else text
