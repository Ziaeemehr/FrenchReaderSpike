# Local LLM reading assistant — design spec

Status: approved for implementation (spike passed; scope, fallback policy, and stopping
point pre-authorized by user on 2026-09-17 — see decisions below).

Implements ROADMAP.md §9, "اولویت ۵: دستیار مطالعه با LLM محلی" — scoped to v1 as decided:

- **Capabilities in v1**: summarize, explain a grammar point, simplify to CEFR level,
  extract difficult vocabulary. (Comprehension-question generation and flashcard
  suggestions are explicitly out of scope for v1 — later slices.)
- **Input unit**: the user's current text selection (in practice: the sentence already
  resolved by the existing long-press-to-lookup gesture), not the whole article.
- **Model/runtime**: Qwen3 0.6B Instruct, Q4_K_M quantization, via llama.cpp — confirmed by
  the on-device spike (`docs/superpowers/spikes/llm-assistant-spike/`), not the 1.7B
  fallback (tested; not a clear win — see qualitative-results.md).
- **Stopping point for this pass**: design → plan → implementation, without further
  check-ins, per explicit user instruction.

## 1. Spike findings this design must encode

From `docs/superpowers/spikes/llm-assistant-spike/qualitative-results.md`:

1. **Qwen3 thinking mode must always be disabled** (`enable_thinking: false` in the chat
   template kwargs / equivalent llama.cpp call). Without this, generation burns its token
   budget on an invisible English reasoning block.
2. **Never ask the model to both find and label a grammar point.** The user already selects
   the sentence (via the existing long-press mechanism); the model is only asked to explain
   the tense *in that sentence*, never to pick which sentence illustrates a tense. This
   sidesteps the tense-misidentification failure observed in the spike.
3. **Vocabulary extraction must use grammar-constrained decoding** (llama.cpp GBNF grammar
   or JSON-schema flag), not a bare "reply with JSON" instruction — free-form prompting
   produced malformed shapes and one hallucinated word in testing.
4. **CEFR simplification needs a worked example and explicit sentence-length guidance in
   the prompt**, not just an instruction — the naive prompt barely changed the source text.
5. Only one heavy inference runs at a time; only one model resides in RAM at a time; model
   unloads on `onTrimMemory`/backgrounding. (Already a roadmap requirement, restated here
   because the spike confirmed peak RSS of ~757 MiB is real and worth respecting.)

## 2. Scope boundaries (explicit non-goals for this pass)

- No generalized "offline models" management screen (per user decision: build only the
  scaffolding this feature needs). No `TranslationEngine`/`EmbeddingEngine`/
  `SpeechRecognitionEngine` work.
- No whole-article processing / chunking across the context window.
- No comprehension-question generation, no flashcard suggestion generation.
- No GPU/NPU acceleration — CPU only, matching the spike.
- No multi-model chooser UI — Qwen3 0.6B is the only model a user can download in v1.

## 3. Architecture overview

```
ReadingScreen (existing)
  └─ long-press → DictionarySheet(word, sentence)     [existing, unchanged]
        └─ new: IconButton "Assistant IA" in its header
              └─ AssistantSheet(sentence)              [new]
                    ├─ 4 action chips (Résumer / Grammaire / Simplifier / Vocabulaire)
                    ├─ AssistantViewModel
                    │     └─ LocalLlmEngine (interface)
                    │           └─ LlamaCppEngine (impl) ── JNI ── llama.cpp (native, arm64-v8a)
                    └─ on vocab-extraction accept → reuses existing VocabList save path
```

Nothing in `ReadingScreen`'s gesture-detection code changes — the new entry point piggybacks
on the sentence text `DictionarySheet` already receives, avoiding new selection UI entirely.

### 3.1 `LocalLlmEngine` interface

New package `com.ziaee.frenchreader.llm`. Interface style follows `ContentSource`'s idiom
(plain `suspend fun`), but adds a small sealed result — unlike `ContentSource`, callers here
genuinely need to distinguish *why* a call failed (model not downloaded vs. OOM vs. timeout),
which the roadmap calls out explicitly, so a bare nullable return is insufficient:

```kotlin
sealed interface LlmResult {
    data class Success(val text: String) : LlmResult
    enum class Failure : LlmResult { NOT_DOWNLOADED, LOAD_FAILED, OUT_OF_MEMORY, TIMEOUT, GENERATION_FAILED }
}

interface LocalLlmEngine {
    suspend fun ensureLoaded(): Boolean
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        grammar: String? = null,   // GBNF grammar text, or null for unconstrained
    ): LlmResult
    fun unload()
}
```

`LlamaCppEngine` is the only implementation in v1. A `Mutex` inside it ensures only one
`generate` call runs at a time (per spike: only one heavy inference at once).

### 3.2 Native layer

llama.cpp is cross-compiled for `arm64-v8a` only in v1 (matches the spike; the app's existing
`abiFilters` also includes `x86_64` for Chaquopy, but shipping a second llama.cpp ABI is
deferred — the assistant feature disables itself on non-arm64-v8a devices rather than
doubling native-build scope now).

Rather than writing a JNI bridge from scratch, adapt llama.cpp's own official Android JNI
sample (`examples/llama.android` in the llama.cpp source tree already pulled down during the
spike, at `/private/tmp/llama.cpp-build`) — trimmed to exactly what's needed: load a GGUF
model from a file path, run a single-turn chat-templated generation with an optional GBNF
grammar, and unload. No server, no multi-turn history, no streaming UI (v1 shows a spinner
then the full result, not token-by-token — simpler, and the ~3-9 second generation times
measured in the spike don't demand streaming for a good UX).

Build wiring: new `app/src/main/cpp/` with `CMakeLists.txt` (vendoring llama.cpp as a git
submodule or a pinned source snapshot — pin to the exact commit tested in the spike,
`05f2dcfdba3879c55f735efa0f124b1a56f7ed11`) and `externalNativeBuild { cmake { ... } }` in
`app/build.gradle`, targeting `android-26` per the existing `minSdk`. Static-link
ggml/llama into a single `libllm_jni.so` (no separate `.so` files to package, matching what
the spike's standalone build already showed works with zero runtime `.so` dependencies
beyond the Android system libs).

### 3.3 Model download & storage

New `data/LlmAssistantPrefs.kt`, following the existing `VocabPrefs`/`AppearancePrefs`
object+`Context`-parameter idiom (no DataStore, no DI):

```kotlin
object LlmAssistantPrefs {
    fun isModelDownloaded(context: Context): Boolean
    fun setModelDownloaded(context: Context, downloaded: Boolean)
}
```

Model file lives at `filesDir/llm_models/qwen3-0.6b-q4_k_m.gguf`. Download flow:

1. A new small screen/sheet, `LlmModelSetupSheet` (reachable from the new "Assistant IA"
   icon when the model isn't downloaded yet): shows fixed copy — file size (461.79 MiB, the
   exact spike-measured size), "runs fully offline, nothing is sent to a server," and a
   download button.
2. Plain `HttpURLConnection` GET (matching the existing `NewsFetcher`/`VikidiaClient` house
   style — no new HTTP library) to the exact URL verified in the spike
   (`https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF/resolve/main/Qwen_Qwen3-0.6B-Q4_K_M.gguf`),
   streamed to a temp file, progress shown via `ContentLength` vs. bytes-read.
3. SHA-256 verified against the exact digest recorded in the spike
   (`9acfc1e001311f34b4252001b626f2e466d592a42065f66571bff3790d4e1b14`) before the temp file
   is renamed into place; mismatch → delete temp file, show error, do not mark as downloaded.
4. A delete button (once downloaded) removes the file and clears the pref flag. No
   auto-download, ever — matches roadmap policy.

Resuming an interrupted download is out of scope for v1 (single non-resumable GET; on
failure the user just retries from zero) — the roadmap's general "must be resumable"
requirement is written for the eventual generalized models screen (explicitly out of scope
here), not mandated for this feature's minimal scaffolding.

### 3.4 Prompts

New `llm/Prompts.kt`, one function per capability, each returning `(systemPrompt, userPrompt,
grammar)`. All system prompts fix the output language to French and forbid restating the
instruction (matches what worked in the spike). Fixed content, not user-editable in v1.

- **Summarize**: `maxTokens = 120`. No grammar constraint (free text was reliable in
  testing).
- **Explain grammar**: input is the *already-selected* sentence only; prompt explicitly says
  "this sentence" rather than asking the model to search for one. `maxTokens = 150`.
- **Simplify CEFR**: prompt embeds one short worked example (a fixed before/after pair
  distinct from the app's real content) and an explicit instruction to keep each sentence
  under ~12 words. `maxTokens = 200`.
- **Extract vocabulary**: GBNF grammar constrains output to a JSON array of 1–5 objects with
  exactly the keys `mot` and `definition_simple` (see draft grammar below); `maxTokens = 250`.

Draft GBNF sketch (finalized during implementation against the actual llama.cpp grammar
syntax):

```
root ::= "[" ws item (ws "," ws item)* ws "]"
item ::= "{" ws "\"mot\"" ws ":" ws string ws "," ws "\"definition_simple\"" ws ":" ws string ws "}"
```

### 3.5 UI flow and the "AI suggestion" requirement

- `AssistantSheet` opens already scoped to one sentence (no free-text entry in v1). Four
  chips; tapping one runs that capability's prompt. While running: a spinner replaces the
  result area (no partial/streaming text in v1, see §3.2). On `LlmResult.Failure`, a short
  French message maps each failure enum to user-facing text (e.g. `NOT_DOWNLOADED` → routes
  to `LlmModelSetupSheet` instead of showing a generic error).
- Every result is shown under a small header labeled **"Suggestion IA"** (fixed label, all
  three UI locales get a translation) — per ROADMAP.md §9's explicit requirement, applying
  to all four capabilities uniformly, not just the data-mutating one.
- Only **extract vocabulary** mutates app data. Its result renders as a list of
  word/definition rows, each with its own Accept/Reject affordance (not a single blanket
  accept for the whole list) — accepting a word reuses `DictionaryViewModel`'s existing
  list-picker + save path (same `VocabList` target chip/dropdown already built for the
  dictionary flow), pre-filling the definition with `definition_simple`, editable before
  save (never silently written).
- Summarize / explain grammar / simplify are pure read-only display — no accept/reject
  needed since nothing is written, per roadmap's confirmation requirement applying only to
  tag/flashcard/text changes.

### 3.6 Lifecycle and resource management

- `LlamaCppEngine` loads the model lazily on first `generate()` call, not on app start.
- Idle unload: a 2-minute idle timer (no calls in-flight) unloads the model; also unloads
  immediately on `onTrimMemory` (any level) and on the hosting `Activity`'s `onStop`.
- Load failure (OOM, corrupt file) surfaces as `LlmResult.Failure.LOAD_FAILED`/
  `OUT_OF_MEMORY` and the UI falls back to "assistant unavailable right now" without
  affecting any other part of the app — reading, TTS, dictionary, etc. must keep working
  exactly as today even if the assistant fails outright.

## 4. Data model changes

No changes to `TextDocument`/`Entities.kt` — v1 output is ephemeral (shown, not stored)
except vocabulary, which flows through the *existing* `VocabList`/vocab-entry save path
unchanged. No new migration is needed for this feature.

## 5. Testing

The project's existing pattern is JVM unit tests (no Robolectric/Mockito, per `app/build.gradle`)
plus a migration-test harness for `AppDatabase`. Since this feature adds no migration, that
harness is untouched. New unit-testable surfaces:

- `Prompts.kt` functions are pure (text in, `(String, String, String?)` out) — straightforward
  unit tests asserting exact expected system/user prompt shape per capability, and that the
  vocabulary grammar string is well-formed.
- `LlmAssistantPrefs` — same shape as existing `VocabPrefs` tests (if any exist; mirror them).
- The native JNI engine itself is not unit-testable in a JVM test; verification is manual,
  on the connected physical device, re-running the fixed rubric prompts from the spike
  (`quality-rubric-prompts.md`) against the shipped grammar-constrained prompts to confirm
  the mitigations actually close the gaps found in the spike (especially: vocabulary JSON
  shape compliance, which was the clearest failure).
- No automated UI test infra exists for bottom sheets elsewhere in the app (confirmed during
  research) — manual verification only, matching house practice.

## 6. Files touched (new unless noted)

- `app/src/main/cpp/CMakeLists.txt`, `app/src/main/cpp/llm_jni.cpp` (new native module)
- `app/build.gradle` (change: add `externalNativeBuild`)
- `app/src/main/java/com/ziaee/frenchreader/llm/LocalLlmEngine.kt`
- `app/src/main/java/com/ziaee/frenchreader/llm/LlamaCppEngine.kt`
- `app/src/main/java/com/ziaee/frenchreader/llm/Prompts.kt`
- `app/src/main/java/com/ziaee/frenchreader/data/LlmAssistantPrefs.kt`
- `app/src/main/java/com/ziaee/frenchreader/ui/AssistantSheet.kt`
- `app/src/main/java/com/ziaee/frenchreader/ui/LlmModelSetupSheet.kt`
- `app/src/main/java/com/ziaee/frenchreader/ui/DictionarySheet.kt` (change: add "Assistant IA" entry icon)
- `app/src/main/res/values*/strings.xml` (change: new strings, all three locales)
