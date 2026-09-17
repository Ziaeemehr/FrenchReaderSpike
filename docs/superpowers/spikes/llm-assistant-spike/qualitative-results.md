# French quality spike results — Qwen3 0.6B vs 1.7B on-device

Ran the fixed prompt set from `quality-rubric-prompts.md` (3 texts × 4 tasks — comprehension
question and flashcard tasks skipped, not in v1 scope) against both models via `llama-cli`
on the connected Galaxy S24 FE, using `--chat-template-kwargs '{"enable_thinking": false}'`
(see finding below), `-c 2048 -t 4 --temp 0.2`.

## Critical finding: Qwen3 "thinking" mode must be disabled

By default Qwen3 emits an internal `[Start thinking]...[End thinking]` English reasoning
block before the actual answer. With a 200-token budget, the first test run (A1, thinking
enabled) spent the entire budget on English reasoning and was cut off mid-sentence before
producing any French output. Passing `--chat-template-kwargs '{"enable_thinking": false}'`
eliminates this entirely — clean, direct French output, no latency wasted on invisible
reasoning. **The in-app engine must always disable thinking mode.**

## Results by task (0.6B / 1.7B)

| Task | Text A | Text B | Text C |
|---|---|---|---|
| Summarize | 0.6B ✅ / 1.7B ✅ | 0.6B ✅ / 1.7B ✅ | 0.6B ✅ (terse) / 1.7B ✅ |
| Explain grammar | 0.6B ✅ / 1.7B ✅ | 0.6B ❌ / 1.7B ❌ | 0.6B ⚠️ (weak) / 1.7B ✅ |
| Simplify to A2 | 0.6B ❌ / 1.7B ⚠️ (better, imperfect) | 0.6B ❌ / 1.7B ❌ | 0.6B ⚠️ / 1.7B ⚠️ |
| Extract vocab (JSON) | 0.6B ❌ (1 item, not 5) / 1.7B ❌ (1 item, not 5) | 0.6B ✅ (5 items, weak defs) / 1.7B ❌ (1 item) | 0.6B ❌ (hallucinated word "marche") / 1.7B ❌ (1 item) |

✅ pass · ⚠️ borderline · ❌ fail, per the criteria in `quality-rubric-prompts.md`.

## Two systematic failure modes (not fixed by going 0.6B → 1.7B)

1. **Grammar-tense misidentification.** Both models, on text B, labeled a non-compound
   verb (0.6B: présent "viennent"; 1.7B: passé simple "fut") as "temps verbal composé."
   This is exactly the hallucination risk the roadmap already flags for anything requiring
   a definitive grammatical answer. Bigger model did not fix it on the same text.

2. **JSON array-count non-compliance.** Asked for a 5-item JSON array, 0.6B got the shape
   right once out of three (and hallucinated a word once); 1.7B *always* returned a single
   object instead of an array of 5, on all three texts. Going bigger made this worse, not
   better — this looks like a formatting/instruction-following issue orthogonal to model
   size, not something more parameters fixes.

## Speed/RAM tradeoff (1.7B fallback, tested per roadmap)

| | 0.6B | 1.7B |
|---|---|---|
| Decode speed | 30–36 tok/s | 12–14 tok/s |
| Peak RSS | 757 MiB | 1.34 GiB (1,408,648 kB VmHWM) |
| Load time | 230–680 ms | ~527 ms |

1.7B is ~2.5× slower to decode for a modest, inconsistent quality gain (better on grammar
text C, worse on JSON-shape compliance everywhere). Not a clear win.

## Recommendation

**Go with Qwen3 0.6B Q4_K_M on llama.cpp** (the roadmap's original starting point) — not the
1.7B fallback. The observed quality gaps are prompting/decoding-strategy problems, not raw
capacity problems, and 1.7B doesn't fix them while costing 2.5× the latency and ~1.8× the
RAM. Did not test LiteRT-LM: the failures found (tense mislabeling, JSON shape) are model
output-shape issues that a different runtime for the same model would not change.

Concretely, the in-app design must build in three mitigations rather than rely on prompting
alone:

1. **Grammar-constrained decoding for the vocab-extraction task.** llama.cpp supports GBNF
   grammars (`--grammar` / `--json-schema`) to force valid, exactly-N-item JSON — use this
   instead of asking nicely in the prompt.
2. **Don't ask the model to *find* a grammar point — have the user select the sentence, and
   only ask the model to *explain the tense the user already highlighted*.** This removes
   the failure mode where the model both picks and mislabels a sentence itself.
3. **CEFR simplification needs a stronger prompt (worked example / explicit sentence-length
   cap) than a bare instruction** — the naive prompt under-delivered at both model sizes.

These become explicit requirements in the architectural design, not just prompts to tweak
later.
