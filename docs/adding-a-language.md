# Adding a target language

Use this checklist when adding target language N:

- Add one `LanguageSupport` entry to `LanguageCatalog`, including its BCP-47 code, locale, STT locale, default voice, XTTS support, and capability flags.
- Verify every Edge voice by running `edge-tts --voice X --text "..." --write-media output.mp3` and listening to the result.
- Add and test the dictionary route for the new target/known-language combinations.
- Add text-rule tests for language-specific casing, punctuation, elision, and word-boundary behavior.
- Enable only the content-source flags that have a working source for the language.
- Add the language and voice labels to `values`, `values-fr`, and `values-fa`.
- Run the unit tests, then perform one device test covering selection, reading, speech, translation, dictionary lookup, and available content sources.

## Where each part lives

| Part | Place |
| --- | --- |
| Codes, locales, voices, home title (`readingTitle`) | `language/LanguageCatalog.kt` |
| Voice labels | `tts/Voices.kt` (`EDGE_VOICES`) + `voice_label_*` strings |
| Dictionaries | `DICTIONARY_PROVIDERS` in `ui/DictionarySheet.kt` |
| News feeds + default enabled set | `NEWS_SOURCES` (with `language`) in `news/NewsRepository.kt`, `DEFAULT_ENABLED_BY_LANGUAGE` in `data/NewsPrefs.kt` |
| Topic search sources | `topicSearchSources()` in `ui/home/HomeViewModel.kt` |
| Built-in resources | `DEFAULT_RESOURCES` (with `language`) in `resources/ResourceCategory.kt` |
| Language name in pickers | `language_name_*` strings + `languageNameResource()` |

Capability flags (`LanguageFeature`) hide what a language doesn't have instead of blocking it:
`NEWS`, `VIKIDIA`, `WIKISOURCE`, `SHADOWING`, `FRENCH_ELISION`, `DATASET` (bundled decks and
stories), `OFFLINE_STT` (a Vosk model exists), `LEMMA_LEXICON` (dictionary-form suggestions and
comprehension scores). Articles from feeds without a dedicated extractor are read by
`ArticleExtractor.extractGenericArticle`; check two or three real article pages of each new feed.
Learning data needs no migration: every row carries a free-form `language` code.
