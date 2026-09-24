#!/usr/bin/env python3
"""Build the bundled starter dataset (app/src/main/assets/dataset) from Anki decks and story folders.

Anki must be open with AnkiConnect. Card audio is not exported. Re-run and bump DATASET_VERSION
whenever the content changes. `--reuse-decks` (or Anki not running) keeps the existing deck files
and only rebuilds stories and the manifest.
"""
import html
import io
import json
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path

from PIL import Image

DATASET_VERSION = 2
UNLEVELED = "Divers"  # level folder for stories without a CEFR level
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/dataset"
STORIES_ROOT = ROOT.parent

# (Anki deck, pack id, display name shown as the vocab list name / card corner label, level, kind)
DECKS = [
    ("Grammaire en dialogues A1", "gram-dial-a1-vocab", "Grammaire en dialogues A1 · Mots", "A1", "words"),
    ("Grammaire en dialogues B1", "gram-dial-b1-vocab", "Grammaire en dialogues B1 · Mots", "B1", "words"),
    ("Gram_diag_B1", "gram-dial-b1-phrases", "Grammaire en dialogues B1 · Phrases", "B1", "phrases"),
    ("Vocabulaire en dialogues A1", "voc-dial-a1-vocab", "Vocabulaire en dialogues A1 · Mots", "A1", "words"),
    ("Vocabulaire progressif du français A2-B1", "voc-prog-a2b1-vocab", "Vocabulaire progressif A2-B1 · Mots", "A2-B1", "words"),
    ("Vocab_Prog_A2B1", "voc-prog-a2b1-phrases", "Vocabulaire progressif A2-B1 · Phrases", "A2-B1", "phrases"),
    ("Communication essentielle A1", "comm-ess-a1-phrases", "Communication essentielle A1 · Phrases", "A1", "phrases"),
    ("Communication essentielle A2", "comm-ess-a2-phrases", "Communication essentielle A2 · Phrases", "A2", "phrases"),
]

SOUND = re.compile(r"\[sound:[^\]]*]")
BREAKS = re.compile(r"<\s*(br|/div|/p)\s*/?>", re.I)
TAGS = re.compile(r"<[^>]+>")
GLOSS = re.compile(r"^(EN|FA):\s*(.*)$")
LESSON = re.compile(r"^(?:Leçon\s*)?(\d+(?:\.\d+)?)\b")


def call(action, **params):
    body = json.dumps({"action": action, "version": 6, "params": params}).encode()
    reply = json.load(urllib.request.urlopen(urllib.request.Request("http://localhost:8765", body), timeout=10))
    if reply.get("error"):
        sys.exit(f"AnkiConnect error in {action}: {reply['error']}")
    return reply["result"]


def anki_available():
    try:
        call("version")
        return True
    except OSError:
        return False


def clean(value):
    text = TAGS.sub("", BREAKS.sub("\n", SOUND.sub("", value)))
    text = html.unescape(text).replace(" ", " ")
    return [line.strip() for line in text.splitlines() if line.strip()]


def lesson_of(deck_name, root):
    sub = deck_name[len(root) + 2:].split("::")[0] if deck_name.startswith(root + "::") else ""
    m = LESSON.match(sub)
    return m.group(1) if m else None


def export_deck(deck, kind):
    ids = call("findCards", query=f'deck:"{deck}"')
    cards, seen = [], set()
    for i in range(0, len(ids), 500):
        for card in sorted(call("cardsInfo", cards=ids[i:i + 500]), key=lambda c: c["cardId"]):
            fields = sorted(card["fields"].values(), key=lambda f: f["order"])
            front, back = clean(fields[0]["value"]), clean(fields[1]["value"]) if len(fields) > 1 else []
            lesson = lesson_of(card["deckName"], deck)
            if kind == "phrases":
                # Anki side is Persian -> French; the app shows French on the front.
                word, meaning, sentence = " ".join(back), " ".join(front), ""
            else:
                word = " ".join(front)
                gloss = [m.group(2) for m in map(GLOSS.match, back) if m and m.group(2)]
                meaning = "\n".join(gloss) if gloss else "\n".join(back)
                sentence = " ".join(l for l in back if not GLOSS.match(l)) if gloss else ""
            key = word.lower()  # the app dedupes by word within a list
            if not word or not meaning or key in seen:
                continue
            seen.add(key)
            row = {"w": word, "m": meaning}
            if sentence:
                row["s"] = sentence
            if lesson:
                row["l"] = lesson
            cards.append(row)
    cards.sort(key=lambda r: float(r.get("l", "0")))  # stable: keeps Anki order within a lesson
    return cards


def parse_story(path):
    text = path.read_text(encoding="utf-8")
    meta = {}
    if text.startswith("---"):
        head, text = text[3:].split("\n---", 1)
        for line in head.splitlines():
            if ":" in line:
                k, v = line.split(":", 1)
                meta[k.strip()] = v.strip()
    lines = text.strip().splitlines()
    source = meta.get("source")
    if lines and lines[-1].startswith("Source:"):
        source = lines[-1].split(":", 1)[1].strip()
        lines = lines[:-1]
        while lines and lines[-1].strip() in ("", "---"):
            lines.pop()
    title = meta.get("title")
    if lines and lines[0].startswith("# "):
        title = title or lines[0][2:].strip()
        lines = lines[1:]
    elif not title and len(lines) > 1 and lines[0].strip() and not lines[1].strip():
        # Plain .txt sources (Lingua, Podcast Français Facile): first line is the title.
        title = lines[0].strip()
        lines = lines[1:]
    image = None
    body = []
    for line in lines:
        m = re.match(r"^!\[[^]]*]\(([^)]+)\)$", line.strip())
        if m:
            image = path.parent / m.group(1)
            continue
        body.append(line)
    body = re.sub(r"\n{3,}", "\n\n", "\n".join(body)).strip()
    return title or path.stem.replace("-", " "), body, source, image


def write_image(src, name):
    img = Image.open(src).convert("RGB")
    if img.width > 720:
        img = img.resize((720, round(img.height * 720 / img.width)), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=72, optimize=True)
    (OUT / "images" / name).write_bytes(buf.getvalue())
    return f"images/{name}"


def build_stories():
    packs = []
    groups = [("fabulang", "Fabulang", f"fabulang/fabulang_{lvl}", lvl) for lvl in ("A1", "A2", "B1", "B2", "C1", "C2")]
    groups += [("fluencydrop", "FluencyDrop", f"fluencydrop/fluencydrop-french-{lvl.lower()}", lvl) for lvl in ("A2", "B1", "B2")]
    groups += [("lingua", "Lingua", f"lingua_{lvl}", lvl) for lvl in ("A1", "A2", "B1", "B2")]
    groups += [("podcastfrancaisfacile", "Podcast Français Facile", f"podcastfrancaisfacile_{lvl}", lvl)
               for lvl in ("A1", "A2", "B1", "B2", "C1")]
    groups += [("podcastfrancaisfacile", "Podcast Français Facile", "podcastfrancaisfacile_unknown", UNLEVELED)]
    for source_id, source_name, rel, level in groups:
        stories = []
        paths = sorted([*(STORIES_ROOT / rel).glob("*.md"), *(STORIES_ROOT / rel).glob("*.txt")])
        for path in paths:
            title, body, url, image = parse_story(path)
            if not body:
                continue
            ascii_stem = unicodedata.normalize("NFKD", path.stem).encode("ascii", "ignore").decode()
            slug = re.sub(r"[^a-z0-9]+", "-", ascii_stem.lower()).strip("-") or str(len(stories))
            story = {"key": f"dataset:{source_id}:{level.lower()}:{slug}", "title": title, "body": body}
            if url:
                story["sourceUrl"] = url
            if image and image.exists():
                story["image"] = write_image(image, f"{source_id}-{slug}.jpg")
            stories.append(story)
        pack_id = f"stories-{source_id}-{level.lower()}"
        (OUT / "stories" / f"{pack_id}.json").write_text(json.dumps(stories, ensure_ascii=False), encoding="utf-8")
        packs.append({"id": pack_id, "name": f"{source_name} {level}", "level": level, "source": source_name,
                      "file": f"stories/{pack_id}.json", "count": len(stories)})
        print(f"{pack_id}: {len(stories)} stories")
    return packs


def main():
    for sub in ("decks", "stories", "images"):
        (OUT / sub).mkdir(parents=True, exist_ok=True)
    for sub in ("stories", "images"):
        for f in (OUT / sub).iterdir():
            f.unlink()
    anki = "--reuse-decks" not in sys.argv and anki_available()
    if not anki:
        print("AnkiConnect not reachable: reusing the existing deck files")
    deck_packs = []
    for deck, pack_id, name, level, kind in DECKS:
        path = OUT / "decks" / f"{pack_id}.json"
        if anki:
            cards = export_deck(deck, kind)
            path.write_text(json.dumps(cards, ensure_ascii=False), encoding="utf-8")
        else:
            cards = json.loads(path.read_text(encoding="utf-8"))
        deck_packs.append({"id": pack_id, "name": name, "level": level, "kind": kind,
                           "file": f"decks/{pack_id}.json", "count": len(cards)})
        print(f"{pack_id}: {len(cards)} cards")
    manifest = {"version": DATASET_VERSION, "decks": deck_packs, "stories": build_stories()}
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
