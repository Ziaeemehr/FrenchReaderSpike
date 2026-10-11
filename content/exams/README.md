# Exam content

Source files for the in-app exam guides.

## Frequent questions (`<exam>/E?_Tache_N_YYYYMMDD.txt`)

Subjects that candidates reported after their exam, exported one file per task from
[french-tcf.com](https://french-tcf.com) (TCF Canada). Each entry has the number of reports
("Frequency"), the months it was reported in, the French subject and a Persian translation. The
counts are reports collected by that site, not figures from France Éducation international.

To update: drop the newer exports in the folder (the newest date per task wins) and run

```
python3 tools/build_exam_questions.py
```

which rewrites `app/src/main/assets/exams/<exam>-questions.json` and prints warnings for entries
that need a look (missing or cut-short translations, untranslated words, merged duplicates). The
script never edits the text of a subject; fix the export or the source instead.

The rules and formats of each exam are not generated: they live in
`app/src/main/assets/exams/exams.json`, with every statement citing an official page.
