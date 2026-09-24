#!/usr/bin/env python3
"""Build the French form->lemma table used for text comprehension scores.

Source: Lexique 3.83 (http://www.lexique.org, CC BY-SA 4.0). Download and unzip Lexique383.zip, then:
    python3 tools/build_lemmas.py path/to/Lexique383.tsv
Writes app/src/main/assets/lexicon/lemmas.tsv (only forms whose lemma differs from the form, as
"form<TAB>lemma[,lemma2]") and app/src/main/assets/lexicon/common_lemmas.txt (the most frequent
lemmas, one per line, treated as known for every learner).
"""
import csv
import sys
from collections import defaultdict
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/lexicon"
COMMON_LEMMAS = 1000
MIN_FORM_FREQ = 0.01  # per million words (films + books); drops very rare forms
# Elided function words the app tokenizes on their own; Lexique misses most of them.
ELISIONS = {"l'": "le", "d'": "de", "j'": "je", "m'": "me", "t'": "te", "s'": "se", "n'": "ne", "c'": "ce",
            "qu'": "que", "jusqu'": "jusque", "lorsqu'": "lorsque", "puisqu'": "puisque"}


def main():
    src = sys.argv[1]
    lemmas_by_form = defaultdict(dict)
    lemma_freq = defaultdict(float)
    with open(src, encoding="utf-8") as f:
        for row in csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            form, lemma = row["ortho"].strip().lower(), row["lemme"].strip().lower()
            if not form or not lemma or " " in form:
                continue
            form_freq = float(row["freqfilms2"] or 0) + float(row["freqlivres"] or 0)
            lemma_freq[lemma] = max(lemma_freq[lemma], float(row["freqlemfilms2"] or 0) + float(row["freqlemlivres"] or 0))
            if form_freq < MIN_FORM_FREQ and form != lemma:
                continue
            lemmas_by_form[form][lemma] = max(lemmas_by_form[form].get(lemma, 0.0), form_freq)

    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for form, lemma in ELISIONS.items():
        lemmas_by_form[form] = {lemma: float("inf")}
    for form, lemmas in sorted(lemmas_by_form.items()):
        ranked = sorted(lemmas, key=lambda l: (-lemma_freq[l], l))[:2]
        if ranked != [form]:
            rows.append(f"{form}\t{','.join(ranked)}")
    (OUT / "lemmas.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    common = [l for l, _ in sorted(lemma_freq.items(), key=lambda kv: -kv[1])[:COMMON_LEMMAS]]
    common += [w for w in list(ELISIONS) + list(ELISIONS.values()) if w not in common]
    (OUT / "common_lemmas.txt").write_text("\n".join(common) + "\n", encoding="utf-8")
    print(f"{len(rows)} forms, {len(common)} common lemmas")


if __name__ == "__main__":
    main()
