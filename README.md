# French Reader

An Android app for learning French by reading. Import or fetch French texts, listen to them read aloud with sentence-level highlighting, look up words and phrases, and review saved vocabulary with a Leitner spaced-repetition box.

The interface is localized in English, French and Persian (with right-to-left layout and the Vazirmatn font for Persian). French reading content always renders left-to-right.

## Features

- **Reading** – Markdown-aware rendering (headings, lists, emphasis, EPUB images) with adjustable font size, reading background and highlight color. Text is shown formatted immediately, before audio is synthesized.
- **Text-to-speech** – sentence-synchronized playback with per-text voice and speed. Two engines:
  - Microsoft Edge neural voices via [`edge-tts`](https://github.com/rany2/edge-tts), run inside the app through Chaquopy.
  - An optional local **XTTS-v2** server (see [tools/xtts_server](tools/xtts_server/README.md)).
- **Lookup** – long-press a word for a dictionary sheet (WordReference, Larousse, Linguee, Wiktionary) with translation; select several words to save a whole phrase, copy it, listen to it, or send it to another app (Google Translate is listed first).
- **Vocabulary** – saved words and phrases are highlighted in the text and organized into lists, with status (new / learning / reviewing / learned).
- **Leitner review** – five boxes with configurable intervals, daily goal, streak, and review reminders. Words on the card are tappable to open the dictionary.
- **Content sources** – paste text, share from other apps, import files/EPUB, Wikisource, Vikidia, and daily news headlines (RFI Journal en français facile, France Info) with keyword filtering.
- **Library & statistics** – searchable, sortable library with folders; activity and accuracy stats.
- **Backup** – local backup and Google Drive backup/restore.

## Tech stack

- Kotlin, Jetpack Compose (Material 3), Room, Media3/ExoPlayer, Coil, jsoup
- [Chaquopy](https://chaquo.com/chaquopy/) for embedding Python (`app/src/main/python/tts_engine.py`)
- Gradle (Groovy DSL), Java 17, `minSdk 26`, `targetSdk 34`

## Project layout

```
app/src/main/java/com/ziaee/frenchreader/
  ui/          Compose screens (reading, vocab list/review, home, library, statistics, settings)
  data/        Room entities/DAOs and SharedPreferences wrappers (SRS logic in VocabSrs.kt)
  tts/         TTS engines, chunk repository and audio cache
  text/        Markdown parsing into blocks
  content/     Vocab highlighting and content helpers
  news/        Headline fetching and merging
  wikisource/  vikidia/  translate/  backup/  images/  util/
app/src/main/python/   Python TTS bridge (Chaquopy)
tools/xtts_server/     Optional local XTTS-v2 server (FastAPI)
docs/  ROADMAP.md      Design notes and plans
```

## Build and run

Requirements: Android Studio (or the Android SDK with JDK 17) and a device/emulator running Android 8.0+.

```bash
./gradlew :app:assembleDebug          # build a debug APK
./gradlew :app:installDebug           # install on a connected device
./gradlew :app:testDebugUnitTest      # run unit tests
```

Set the SDK path in `local.properties` (`sdk.dir=...`) if Android Studio hasn't done so.

## Local XTTS server (optional)

For higher-quality synthesis you can run XTTS-v2 on a computer on the same network:

```bash
cd tools/xtts_server
pip install coqui-tts fastapi uvicorn numpy   # plus ffmpeg
COQUI_TOS_AGREED=1 python server.py --host 0.0.0.0 --port 8020
```

Then choose **Local XTTS server** in the app settings and enter `http://<computer-ip>:8020`. Set `XTTS_TOKEN` to require a token, and `XTTS_THREADS` to control the number of CPU threads PyTorch uses. Full instructions are in [tools/xtts_server/README.md](tools/xtts_server/README.md).

## Documentation

- [ROADMAP.md](ROADMAP.md) – planned and implemented features, design decisions (in Persian)
- [docs/](docs/) – additional plans and design notes
