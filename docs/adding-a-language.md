# Adding a target language

Use this checklist when adding target language N:

- Add one `LanguageSupport` entry to `LanguageCatalog`, including its BCP-47 code, locale, STT locale, default voice, XTTS support, and capability flags.
- Verify every Edge voice by running `edge-tts --voice X --text "..." --write-media output.mp3` and listening to the result.
- Add and test the dictionary route for the new target/known-language combinations.
- Add text-rule tests for language-specific casing, punctuation, elision, and word-boundary behavior.
- Enable only the content-source flags that have a working source for the language.
- Add the language and voice labels to `values`, `values-fr`, and `values-fa`.
- Run the unit tests, then perform one device test covering selection, reading, speech, translation, dictionary lookup, and available content sources.
