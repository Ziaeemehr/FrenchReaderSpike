#!/usr/bin/env python3
"""Export Anki decks to a JSON file for the French Reader app (AnkiConnect, read-only)."""
import argparse
import json
import sys
import urllib.error
import urllib.request

URL = "http://localhost:8765"


def call(action, **params):
    body = json.dumps({"action": action, "version": 6, "params": params}).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request(URL, body), timeout=60) as r:
            reply = json.load(r)
    except (urllib.error.URLError, OSError):
        sys.exit("Cannot reach AnkiConnect at localhost:8765 - is Anki open with the AnkiConnect add-on installed?")
    if reply.get("error"):
        sys.exit(f"AnkiConnect error in {action}: {reply['error']}")
    return reply["result"]


def today_day():
    """Anki's current day number, read off any card due today; None if there is none."""
    ids = call("findCards", query="prop:due=0")
    for card in call("cardsInfo", cards=ids[:20]):
        if card["type"] == 2:
            return card["due"]
    return None


def card_row(card):
    fields = sorted(card["fields"].values(), key=lambda f: f["order"])
    front = fields[0]["value"] if fields else ""
    back = "<br>".join(f["value"] for f in fields[1:])
    return {
        "deck": card["deckName"], "front": front, "back": back,
        "type": card["type"], "interval": card["interval"], "due": card["due"],
        "reps": card["reps"], "lapses": card["lapses"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--deck", action="append", help="deck name (repeatable); default: all decks")
    args = parser.parse_args()

    decks = args.deck or call("deckNames")
    seen, rows = set(), []
    for deck in decks:
        ids = call("findCards", query=f'deck:"{deck}"')
        for i in range(0, len(ids), 500):
            for card in call("cardsInfo", cards=ids[i:i + 500]):
                if card["cardId"] not in seen:
                    seen.add(card["cardId"])
                    rows.append(card_row(card))
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"version": 1, "todayDay": today_day(), "cards": rows}, f, ensure_ascii=False)
    print(f"Wrote {len(rows)} cards to {args.out}")


if __name__ == "__main__":
    main()
