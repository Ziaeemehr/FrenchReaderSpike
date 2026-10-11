# Project rules

- Claude uses LOW reasoning effort. Codex uses default (medium) effort (low pin lifted by the user 2026-09-21); standard service tier only.
- Write implementation code via `codex exec` when practical; verify diffs and build yourself.
- Reply in English.
- Never install, restore or migrate anything over the real app (`com.ziaee.frenchreader`) on the user's phone without first taking a backup of its data and checking the build's Room `SCHEMA_VERSION` is not lower than the installed one. The installed `versionName` does not identify the commit. Test on the `.debug` package instead whenever possible.
