# Privacy Policy

_Last updated: 23 September 2026_

French Reader is a free, open-source Android app for learning French by reading. It has no user accounts, no ads, no analytics and no crash reporting. The developer runs no server and never receives your data.

## What the app stores

Everything you create in the app stays on your phone:

- texts you import or save, their images, and your reading position
- saved words and phrases, their meanings, lists and review history
- highlights, listening and study time, and your settings

The app's cache (audio, translations, images) is also stored on the phone. Uninstalling the app deletes all of this data unless you have made a backup.

## When the app goes online

The app connects to outside services only to do things you ask for. Each service handles what it receives under its own privacy policy.

| Feature | Sent to | What is sent |
|---|---|---|
| Text-to-speech (default engine) | Microsoft Edge online speech service | The sentence being read aloud |
| Text-to-speech (local XTTS server, optional) | The computer address you enter in settings | The sentence being read aloud |
| Translating a word or paragraph | Google Translate's web service; MyMemory (api.mymemory.translated.net) if that fails | The word or text to translate |
| Dictionary lookup | The dictionary site you choose (WordReference, Larousse, Linguee, Wiktionary, B-amooz) | The word you look up |
| News, Wikisource, Vikidia, importing an article | The site the content comes from (e.g. RFI, France Info, Wikisource) | A normal page or feed request |
| Google Drive backup (optional) | Google | Your backup file (see below) |

These requests include standard network information such as your IP address, as any web request does. Nothing is sent to the developer.

## Backups

- **Local backup** saves a file to a place on your device you choose. It contains your texts, images, saved words, review history and settings.
- **Google Drive backup** runs only after you sign in with your Google account and tap Back up. The app requests only the `drive.appdata` permission: a hidden folder in your own Drive that only this app can use. It cannot see or change any other files in your Drive. The app keeps your five most recent backups there. You can sign out in Settings, and you can delete the app's stored data from Google Drive's settings (Manage apps).
- **Android device backup:** if Android's own backup is turned on in your phone's settings, Android may also include this app's data in your device backup, which is managed by Google under your account.

## Permissions

- **Internet / network state:** for the online features listed above.
- **Notifications:** for the optional daily review reminder.
- **Run at startup:** to reschedule that reminder after the phone restarts.
- **Foreground service / media playback:** so read-aloud playback keeps working when the screen is off.

## Children

The app does not knowingly collect personal information from anyone, including children.

## Changes

If this policy changes, the updated version will be published in this file in the app's source repository, with a new date at the top.

## Contact

Questions can be asked by opening an issue at <https://github.com/Ziaeemehr/FrenchReaderSpike/issues>.
