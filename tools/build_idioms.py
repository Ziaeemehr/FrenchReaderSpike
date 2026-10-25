#!/usr/bin/env python3
"""Build the French multi-word expression table used to highlight idioms in video subtitles.

Source: English Wiktionary via kaikki.org (https://kaikki.org, CC BY-SA 4.0 / GFDL). Download
kaikki.org-dictionary-French.jsonl.gz from https://kaikki.org/dictionary/French/ , then:
    python3 tools/build_idioms.py path/to/kaikki.org-dictionary-French.jsonl.gz
Writes app/src/main/assets/lexicon/idioms.tsv as "expression<TAB>pos<TAB>gloss", one expression
per line in its dictionary (lemma) form, e.g. "prendre la mouche<TAB>verb<TAB>to get angry".
"""
import gzip
import json
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/lexicon/idioms.tsv"
# Proper names, numbers and contractions are not expressions a learner looks up; grammatical
# locutions ("parce que", "il y a") occur in nearly every line and would underline everything.
SKIP_POS = {"name", "num", "contraction", "conj", "prep", "pron", "det", "article", "particle", "character", "symbol", "suffix", "prefix", "infix", "affix"}
MAX_GLOSSES = 2
MAX_GLOSS_CHARS = 160
MAX_WORDS = 8
# Two-word entries ending in a function word ("être à", "faire avec") or starting with "y" ("y avoir") match
# ordinary grammar in almost every subtitle line rather than an expression.
FUNCTION_WORDS = {"à", "de", "en", "y", "avec", "pour", "par", "sur", "dans", "sans", "bien", "mal", "le", "la", "les",
                  "un", "une", "du", "des", "que", "qui", "ça", "ce", "se", "si", "pas", "plus", "là"}
# Very frequent fillers that are not worth highlighting.
STOPLIST = {"et cetera", "et caetera", "ça va", "vous savez", "tu sais", "c'est ça"}


def clean(text):
    return " ".join(text.replace("\t", " ").split())


def glosses(entry):
    out = []
    for sense in entry.get("senses") or []:
        tags = set(sense.get("tags") or [])
        if "form-of" in tags or "alt-of" in tags or sense.get("form_of") or sense.get("alt_of"):
            continue
        for gloss in sense.get("glosses") or []:
            gloss = clean(gloss)
            if gloss and gloss not in out:
                out.append(gloss)
                break
        if len(out) == MAX_GLOSSES:
            break
    return "; ".join(out)[:MAX_GLOSS_CHARS]


def main():
    src = sys.argv[1]
    best = {}
    opener = gzip.open if src.endswith(".gz") else open
    with opener(src, "rt", encoding="utf-8") as f:
        for line in f:
            entry = json.loads(line)
            word = clean(entry.get("word", "")).replace("’", "'")
            pos = entry.get("pos", "")
            if " " not in word or pos in SKIP_POS or len(word.split()) > MAX_WORDS:
                continue
            if any(ch.isdigit() for ch in word) or word.isupper():
                continue
            words = word.lower().split()
            if word.lower() in STOPLIST or (len(words) == 2 and (words[1] in FUNCTION_WORDS or words[0] == "y")):
                continue
            gloss = glosses(entry)
            if not gloss:
                continue
            key = word.lower()
            # Keep the first entry per expression; Wiktionary lists the main sense first.
            best.setdefault(key, (word, pos, gloss))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as out:
        for key in sorted(best):
            word, pos, gloss = best[key]
            out.write(f"{word}\t{pos}\t{gloss}\n")
    print(f"{len(best)} expressions -> {OUT}")


if __name__ == "__main__":
    main()
