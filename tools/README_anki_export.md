# Anki Export to JSON

This script exports Anki decks to a JSON file compatible with the French Reader app.

## Prerequisites

- Anki must be open on your machine
- [AnkiConnect add-on](https://ankiweb.net/shared/info/2055492159) must be installed

## Usage

```bash
python3 tools/anki_export.py --out anki.json [--deck "Deck Name"]...
```

- Omit `--deck` to export all decks
- Repeat `--deck` multiple times to export specific decks
- A deck name includes all subdecks
- Anki must stay open while the script runs
- Note: audio files are not exported or imported; only card text is transferred

## Import to App

1. Copy the generated `anki.json` to your Android device
2. Open the French Reader app
3. Go to **Vocabulary > Import from Anki**
4. Select the JSON file to import
