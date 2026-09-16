# Dictionary panel redesign report

Status: BLOCKED — implementation written; compilation and git staging blocked by sandbox permissions.

## Changes

- 1a: Applied exactly. Added the private DictionaryProvider class and four providers with the supplied labels, URLs, encoding, and documentation. wordReferenceUrl and DictionaryViewModel remain unchanged.
- 1b: Applied the supplied modal wrapper and shared panel, including close button, provider selection, failure reset, and AndroidView update. One necessary deviation: DictionaryPanelContent is internal instead of private. Kotlin top-level private is file-private, even within the same package; the supplied private declaration would prevent ReadingScreen.kt from calling it. The public DictionarySheet signature and defaults are unchanged. Existing translation effects, list guard, meaning field, list picker/save behavior, and new-list dialog are preserved.
- 1c: Added exactly the two requested foundation imports; no existing imports removed or changed.
- 2a: Added kotlinx.coroutines.launch.
- 2b: Added the supplied coroutine scope and scaffold state, initially Hidden with skipHiddenState = false.
- 2c: Added the explicit expand() coroutine in onWordLookup, including for repeated equal word/sentence selections.
- 2d: Added the supplied nested BottomSheetScaffold with zero peek height, shared panel content, close/hide callback, outer padding, and inner sheet padding. Removed the old sibling DictionarySheet call. Outer topBar/bottomBar and existing article content are preserved. The independent autoscroll fix was not introduced.

## Verification

Read both complete source files before editing. Reviewed the resulting whitespace-insensitive diff against the supplied design. git diff --check passed.

Programmatic preservation checks passed for:
- Entire DictionaryViewModel.
- New-list dialog.
- DictionarySheet public parameter names, types, and defaults.
- Outer Scaffold including top bar, playback bottom bar, and loading branch.
- ReadingScreen content from the source-info sheet onward.
- VocabListScreen.kt remains byte-for-byte identical to HEAD; no call-site changes required.
- No DictionarySheet invocation remains in ReadingScreen.kt.

Only the two requested Kotlin source files were changed. This report is the explicitly requested additional artifact. Existing untracked .claude content was left intact.

Ran ./gradlew compileDebugKotlin after applying the edits. It exited 1 before compilation:
java.io.FileNotFoundException: /Users/tng/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3-bin.zip.lck (Operation not permitted).

No compiler success is claimed. No automated Compose tests were added, as instructed. Controller verification remains: compile, long-press lookup, article scrolling while open, all four providers, close button, swipe-down dismissal, repeated same-word reopening, reachable playback controls, and unchanged saved-vocab editing flow.

## Concerns and deviations

The sole code deviation from the supplied snippets is internal visibility for the cross-file shared composable, required by Kotlin visibility rules. Device behavior is unverified in this sandbox.

Commit: none - left uncommitted. Attempting to stage only the two Kotlin files failed:
fatal: Unable to create '/Users/tng/Downloads/language_app/FrenchReaderSpike/.git/index.lock': Operation not permitted.

## Round 2: revert to modal + WebView scroll fix

Status: BLOCKED — requested implementation complete; compilation blocked by sandbox.

Folded DictionaryPanelContent into the single public DictionarySheet composable. State and both effects precede ModalBottomSheet; the existing Column is directly inside it, and the new-list dialog remains outside. Removed onClose and used onDismiss for the retained close button. Removed stale split/non-modal documentation.

Added the supplied ACTION_MOVE touch listener inside WebView(ctx).apply in AndroidView factory, after WebViewClient setup. It requests that the parent not intercept touch events and returns false so WebView default handling continues. The WebView retains its 340.dp Box.

Preservation checks passed against the pre-round file: imports, wordReferenceUrl, DictionaryProvider/DICTIONARY_PROVIDERS, DictionaryViewModel, public signature/defaults, all state and both effects, meaning/list-picker/save UI, four provider tabs, failure banner, URL update, and new-list dialog. DictionaryPanelContent and onClose no longer occur. VocabListScreen's existing named arguments, including isNew = false and onDismiss = { editing = null }, match the unchanged signature.

ReadingScreen.kt and VocabListScreen.kt were not edited: before/after SHA-1 hashes match, and both have zero diff against main. Only DictionarySheet.kt source was edited in this round, plus this explicitly requested report append. git diff --check passed.

Ran ./gradlew compileDebugKotlin; it exited 1 before compilation because /Users/tng/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3-bin.zip.lck could not be opened (Operation not permitted). Controller compilation and real-device internal scrolling verification across all four tabs remain required. No compilation or device-success claim is made.

## Round 3: hide known ad slots on Larousse via injected CSS

Status: BLOCKED — supplied onPageFinished override added exactly; compilation blocked by sandbox.

Added the override inside the existing WebViewClient to inject CSS hiding .pub-top, .pub-bottom, .pub-pave, .pub-gtm, and .ads-core-placer after every page load.

Self-review against the pre-round snapshot confirmed that removing only this added override reproduces the original file exactly. Both onReceivedHttpError and onReceivedError, the touch listener, and all other existing content are untouched. git diff --check passed. Only DictionarySheet.kt source was changed, plus this requested report append.

Ran ./gradlew compileDebugKotlin; it exited 1 before compilation because /Users/tng/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3-bin.zip.lck could not be opened (Operation not permitted). Controller compilation remains required.

## Round 4: block known ad-network requests via shouldInterceptRequest

Added the supplied AD_BLOCK_HOSTS/isAdHost block and shouldInterceptRequest override exactly as requested. Self-review against the pre-edit snapshot confirms these are the only DictionarySheet.kt changes; provider tabs, close button, touch listener, and onPageFinished CSS injection are untouched.

Compile: BLOCKED. ./gradlew compileDebugKotlin failed before compilation because the sandbox denied access to the Gradle wrapper zip lock under /Users/tng/.gradle (Operation not permitted). Controller verification required.
