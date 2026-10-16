# Project rules

- Claude uses LOW reasoning effort. Codex uses default (medium) effort (low pin lifted by the user 2026-09-21); standard service tier only.
- Write implementation code via `codex exec` when practical; verify diffs and build yourself.
- Reply in the language the user writes in (Persian question → Persian reply).
- Never install, restore or migrate anything over the real app (`com.ziaee.frenchreader`) on the user's phone without first taking a backup of its data and checking the build's Room `SCHEMA_VERSION` is not lower than the installed one. The installed `versionName` does not identify the commit. Test on the `.debug` package instead whenever possible.
- UI: never use horizontally scrolling menus or chip rows. Options either wrap so all of them are visible on screen (e.g. `FlowRow`) or go in a dropdown (owner's rule, 2026-10-11).
