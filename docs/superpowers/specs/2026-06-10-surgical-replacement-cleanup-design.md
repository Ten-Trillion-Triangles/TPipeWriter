# Surgical-Replacement Cleanup for Plus Writer Pipeline

**Date:** 2026-06-10
**Branch:** `plus-writer-test`
**Status:** Design (awaiting user approval)

## Goal

Convert every "fix" pipe in the Plus Writer pipeline from **full-text rewrite** to **TPipe DITL surgical-replacement mode**: the LLM emits a `SurgicalChangeList` of `find → replace` pairs, and a post-LLM function applies them to the *previous* `new page` text from the bank.

**Why:** the current rewrite-based pipeline is self-defeating. Each fix pipe (em-dash removal, internal-monologue conversion, stage-direction stripping, etc.) re-emits the full page, which (a) costs more tokens and (b) re-introduces issues that an earlier fix pipe already corrected. Surgical mode keeps each pipe's input byte-identical to its output outside the changes the pipe explicitly made.

## Scope

**In scope (v1):**
- All 8 fix-class pipes: `postWriterPipe`, `cleanupStepOnePipe`, `cleanupStepTwoPipe`, `cleanupStepThreePipe`, `tweaksAroundTheEdgesPipe`, `secondPassPipe`, `loreRepairPipe`, `logicalCorrectionPipe`.
- All 2 judge pipes: `loreCheckPipe`, `logicalProgressionPipe` (their output schema changes from `WorldFixes` to `SurgicalChangeList`).
- The shared `preInvokeLoreRepairPipe` short-circuit (updated to check the new schema).
- The shared `applySurgicalReplacementsAndBank` utility (new, replaces broken `surgicalReplace`).
- The `SurgicalChanges` data class (extended with a `mode` field).
- Unit tests for the new function and the updated short-circuit.

**Out of scope (explicit non-goals):**
- The 7 currently-commented-out pipes (`murderPipe`, `untwistPipe`, `removeBadWritingStepOnePipe/TwoPipe`, `benignSkiesMyDialoguePipe`, `polishMyDialoguePipe`, `certifyMyDialoguePipe`, `unmessupendingPipe`, `applyFetishPipe`).
- Other pipelines (`ExpansionPipeline.kt`, `ChapterRewritePipeline.kt`, `AdvancedWriterPipeline.kt`, `PitchSlideWriterPipeline.kt`).
- A `SurgicalPipe` class added to the TPipe library itself.
- Fuzzy matching or whitespace normalization on the find step.

## Architecture & data flow

```
  ┌──────────────────────┐     ┌──────────────────────────┐     ┌──────────────────────┐
  │  Previous pipe's     │     │  New fix pipe            │     │  Next pipe           │
  │  output, banked at   │ ──▶ │  (e.g. cleanupStepOne)   │ ──▶ │  reads new page,     │
  │  "new page"          │     │                          │     │  continues           │
  └──────────────────────┘     └──────────────────────────┘     └──────────────────────┘
                                       │
                                       │  Inside the pipe:
                                       ▼
   ┌──────────────────────────────────────────────────────────────────────────────────────┐
   │ 1. setPageKey("user prompt, new page")         ── reads prior new page into context │
   │ 2. setJsonOutput(SurgicalChangeList()) +       ── constrains LLM to JSON            │
   │    requireJsonPromptInjection(stripExt=true)                                          │
   │ 3. LLM call produces JSON { changeList: [ ... ] }                                    │
   │ 4. setTransformationFunction(::applySurgical…):                                       │
   │      a. Read prior "new page" from bank                                              │
   │      b. extractJson<SurgicalChangeList>(content.text)                                │
   │      c. For each change: strict-match find; apply if found, drop if not              │
   │      d. Length sanity check (no < minLengthRatio of prior)                            │
   │      e. Bank patched text to "new page"; set content.text = patched                  │
   │ 5. setOnFailure: keep prior "new page" unchanged on JSON parse failure               │
   │ 6. setValidatorFunction(::isValidGptOssResponse):  existing, kept                    │
   └──────────────────────────────────────────────────────────────────────────────────────┘
```

Three invariants:
1. Each fix pipe's LLM output is JSON, parsed cleanly (enforced by `setJsonOutput` + `setOnFailure` fallback).
2. Each fix pipe's application is idempotent on the same input (apply function is pure given inputs).
3. Each fix pipe's output length is at least `minLengthRatio` of its input (length sanity guard catches wholesale truncation).

## Components

### Component A: data shapes (PlusWriterPipeline.kt:45-58, extended)

```kotlin
@Serializable
data class SurgicalChanges(
    var subStringToChange: String = "",
    var replacementSubString: String = "",
    var mode: String = "replace"  // "replace" | "delete" | "insertAfter"
)

@Serializable
data class SurgicalChangeList(
    var changeList: MutableList<SurgicalChanges> = mutableListOf()
)
```

`mode` behavior:

| `mode` | `subStringToChange` | `replacementSubString` | Result |
|---|---|---|---|
| `replace` (default) | `"foo bar"` | `"foo baz"` | Replace first occurrence of `foo bar` with `foo baz` |
| `delete` | `"foo bar"` | `""` (ignored) | Remove first occurrence of `foo bar` |
| `insertAfter` | `"foo"` | `" bar"` | Replace first occurrence of `foo` with `foo bar` |

Unknown `mode` values are treated as `replace` with a log entry.

### Component B: `applySurgicalReplacementsAndBank` (PlusWriterUtil.kt, new)

```kotlin
suspend fun applySurgicalReplacementsAndBank(
    content: MultimodalContent,
    minLengthRatio: Double = 0.25
): MultimodalContent
```

Steps:
1. Read prior `new page` from `ContextBank`. Empty string if not banked.
2. Parse the LLM's `SurgicalChangeList` from `content.text` via `extractJson<SurgicalChangeList>(...)`. Fallback to `cleanJsonString(...)` then retry. Returns `content` unchanged if both fail.
3. For each `SurgicalChanges` in `changeList`:
   - Skip if `subStringToChange.isBlank()`.
   - Skip if `subStringToChange` not found in current `patched` (strict match, drop-on-miss).
   - Apply according to `mode`.
4. Length sanity check: if `patched.length < prior.length * minLengthRatio`, return `content` unchanged.
5. Bank `patched` to `new page`. Set `content.text = patched`. Return `content`.

### Component C: per-pipe DITL configuration (PlusWriterPipeline.kt, in-place edits)

Every fix pipe gets the same configuration delta:

```kotlin
.setPageKey("user prompt, new page")
.setJsonOutput(SurgicalChangeList())
.requireJsonPromptInjection(stripExternalText = true)
.setTransformationFunction(::applySurgicalReplacementsAndBank)
.setOnFailure { _, processed ->
    processed.text = ContextBank.getContextFromBank("new page")
        .contextElements.lastOrNull() ?: processed.text
    processed
}
```

Per-pipe thresholds and page keys:

| Pipe | Line | Page key | Min ratio | Modes used |
|---|---|---|---|---|
| `postWriterPipe` | 569 | `user prompt, new page` | 0.5 | replace, delete |
| `cleanupStepOnePipe` | 1010 | `user prompt, new page` | 0.25 | replace |
| `cleanupStepTwoPipe` | 1037 | `user prompt, new page` | 0.25 | replace, insertAfter |
| `cleanupStepThreePipe` | 1070 | `user prompt, new page` | 0.25 | delete, replace |
| `tweaksAroundTheEdgesPipe` | 1107 | `user prompt, new page, themes` | 0.5 | insertAfter, replace |
| `secondPassPipe` | 1175 | `user prompt, new page` | 0.5 | replace, delete |
| `loreCheckPipe` (judge) | 600 | `new page, main, user prompt` | n/a (emits, doesn't apply) | replace, delete |
| `loreRepairPipe` | 635 | `new page, main, user prompt` | 0.5 | replace, delete |
| `logicalProgressionPipe` (judge) | 669 | `new page, story guide, chapter guide, user prompt` | n/a | replace, insertAfter |
| `logicalCorrectionPipe` | 726 | `new page` | 0.5 | insertAfter, replace |

For the two **judge pipes**: their output schema changes from `WorldFixes(needsChanges, changesToMake: String)` to `SurgicalChangeList(changeList)`. The LLM is told to enumerate the bad passages verbatim alongside the proposed corrections. The `needsChanges` field is dropped (empty `changeList` already means "no changes needed").

### What does NOT change

- `chasingShadowsWritingPipe` (line 388) — the actual writer. Stays as a full-text generation. Its job is to produce the initial draft, not to fix things.
- `preGuidePipe`, `simplifierPipe`, `guidePipe`, `newMurderPipe` — all planning pipes. They emit JSON plans, not prose, so surgical doesn't apply.
- `dummyPipe` — pass-through. Unchanged.
- The 7 currently-commented-out pipes — out of scope.
- `loreBookPipe` — uses `recordLoreBook`, not a writing pipe. Unchanged.

## Error handling

**Principle: when in doubt, preserve the prior `new page` text unchanged.** The LLM gets many chances across 10 pipes; if one pipe fails, the next 9 still have their input intact.

| Failure | Likelihood | Defense | Net effect |
|---|---|---|---|
| Malformed JSON | HIGH | `setOnFailure` on every fix pipe reads prior text and overwrites `processed.text` | Pipe becomes no-op; prior text preserved |
| `subStringToChange` not found in source | HIGH | Strict-match drop-on-miss inside apply; missed change added to `unmatched` list, logged | Other changes in the list still apply |
| Empty `changeList` | MEDIUM | Treated as "no changes" — prior text preserved | For judge pipes, triggers downstream short-circuit (`preInvokeLoreRepairPipe`) |
| Apply produces much shorter result | LOW | Length sanity check at end of apply (uses existing `isWordCountSmallerByPercentage` at PlusWriterUtil.kt:212-221) | Prior text preserved; warning logged |
| LLM hits `maxTokens` mid-JSON | LOW | TPipe's lenient `extractJson` + `repairJsonString` | Partial list applied; pipeline continues |

**Match strictness: strict, drop on miss.** No retry, no fuzzy normalization, no whitespace canonicalization. A miss is logged and dropped. If the unmatched rate proves too high in practice, add a normalization pass later as a follow-up.

**Length sanity threshold: 0.25 by default, 0.5 for editorial pipes.** Tunable per pipe via the `minLengthRatio` parameter. The 25% threshold sits in the middle of "loose enough to allow legitimate cleanup that significantly shortens text" and "tight enough to catch the 'pretend-surgical full rewrite' failure mode."

## Testing

### Layer 1: Unit tests for `applySurgicalReplacementsAndBank`

Location: `src/test/kotlin/PlusWriterUtilTest.kt` (extend).

Cases (15+):
- Empty `changeList` → preserves prior text
- Single replace, match → applies
- Single replace, miss → drops, logs
- Multiple replaces, all match → applies in order
- Multiple replaces, some miss → applies the matches, logs the misses
- `mode = "delete"` → removes the find
- `mode = "insertAfter"` → appends after the find
- `mode = "garbage"` → treated as replace, applied
- Length sanity, fails → preserves prior text
- Length sanity, passes (within threshold) → applies
- Length sanity, per-pipe threshold (e.g. 0.5) → respects it
- JSON parse failure → returns content unchanged
- Lenient JSON, `cleanJsonString` fallback → parses
- Bank is empty (cold start) → applies against empty string
- Bank update visible to next pipe (integration test of two pipes in sequence)

### Layer 2: Judge-pipe short-circuit regression test

Location: `src/test/kotlin/PlusWriterUtilTest.kt` (new function).

Verifies the updated `preInvokeLoreRepairPipe` correctly signals "skip the repair pipe" when the upstream `SurgicalChangeList` is empty, and "do not skip" when it has entries. Also verifies that the prior `new page` text is restored to `content.text` on the skip path.

### Layer 3: Manual pipeline smoke test

Location: `src/test/kotlin/PlusWriterPipelineSmokeTest.kt` (new, marked `@Ignore` by default).

Picks a small known seed (2-paragraph story + chapter guide + story guide), runs the full pipeline against `"Continue the story"`, and asserts:
- Output is non-empty, ≥500 chars
- Output contains no em-dashes (verifies cleanup step 1)
- Running twice produces similar (not identical) output

Requires AWS Bedrock credentials. Manual gate, not a coverage target.

### What we explicitly do NOT test

- That the LLM emits good JSON (we defend against bad JSON; we don't assume it).
- That the LLM emits matching `subStringToChange` strings (strict-match drop-on-miss defends).
- The system prompts themselves (prompts are a tuning surface, not a tested invariant).

### Coverage target

Layer 1 + Layer 2 should hit >90% line coverage of the new function and the modified `preInvokeLoreRepairPipe`.

## Migration & rollout

Four phases, with hard ordering:

### Phase 1: Foundation (one commit, "surgical-cleanup: add apply function and extend schema")

Files:
- `Builders/PlusWriterPipeline.kt:45-58` — add `mode` field to `SurgicalChanges`.
- `Builders/Util/PlusWriterUtil.kt` — add `applySurgicalReplacementsAndBank` function.
- `src/test/kotlin/PlusWriterUtilTest.kt` — add Layer 1 unit tests.

Acceptance: `./gradlew test` green.

### Phase 2: Delete the broken code (one commit, "surgical-cleanup: remove broken surgicalReplace")

Files:
- `Builders/Util/PlusWriterUtil.kt:407-415` — delete the broken `surgicalReplace` function.
- `Builders/Util/PlusWriterUtil.kt:49-55` — remove the dead `SurgicalChangeList` branch inside `recordWritingPipePage`.
- `Builders/Util/PlusWriterUtil.kt:163-170` — remove the dead `SurgicalChangeList` branch inside `secondPassTransform`.

Acceptance: `./gradlew build` green. No behavioral change (the dead branches were never hit by any active pipe).

### Phase 3a: Convert the 3 cleanup pipes (one commit, "surgical-cleanup: convert cleanupStepOne/Two/Three to surgical mode")

Files: `cleanupStepOnePipe` (1010), `cleanupStepTwoPipe` (1037), `cleanupStepThreePipe` (1070).

Acceptance: `./gradlew test` green. Manual smoke test against known seed; verify cleanup step 1 produced an em-dash-free output.

### Phase 3b: Convert the editorial pipes (one commit, "surgical-cleanup: convert post-writer, tweaks, second-pass to surgical mode")

Files: `postWriterPipe` (569), `tweaksAroundTheEdgesPipe` (1107), `secondPassPipe` (1175).

Acceptance: `./gradlew test` green. Manual smoke test.

### Phase 3c: Convert the judge-pipe-driven repairs (one commit, "surgical-cleanup: convert lore and logical-progression judge+repair pairs to surgical mode")

Files: `loreCheckPipe` (600), `loreRepairPipe` (635), `logicalProgressionPipe` (669), `logicalCorrectionPipe` (726), `preInvokeLoreRepairPipe` (PlusWriterUtil.kt:86-113), and the Layer 2 test.

Acceptance: `./gradlew test` green. Manual smoke test. The judge pipes now emit `SurgicalChangeList`; the repair pipes consume it; the short-circuit still works.

### Verification end-to-end (after Phase 3c)

1. `./gradlew test` — all unit tests pass.
2. `./gradlew build` — no compilation errors.
3. Manual: run the full pipeline against a 2-paragraph seed. Verify:
   - Output is a coherent continuation.
   - No em-dashes (cleanup step 1 worked).
   - No bracket stage directions (cleanup step 3 worked).
   - Trace.html shows all 10 fix pipes emitted JSON.
   - Trace.html shows the bank state for `new page` evolving sensibly between pipes.
4. Manual regression: take the prior version of `PlusWriterPipeline.kt` (before the refactor) and run the same seed. Compare output. The refactor should produce prose of equal or better quality, with notably fewer "previously-fixed issue reappeared" artifacts.

### Rollback

Phases 1 and 2 are independently safe to revert. Phase 3 sub-phases can be reverted individually to bisect a regression. If the foundation itself breaks, revert Phases 1-2 — the pipeline reverts to its prior behavior (the broken `surgicalReplace` was a no-op).

## Open design decisions resolved during brainstorming

- **v1 scope:** all 8 fix-class pipes + 2 judge pipes (most ambitious option).
- **`secondPassPipe`:** convert to surgical (smallest, safest, most surgical-by-nature).
- **Match strictness:** strict, drop on miss (lowest complexity, safest).
- **`mode` field on `SurgicalChanges`:** add `replace | delete | insertAfter` (5-line addition, useful for the dialogue-extension pipes when they come back).

## References

- TPipe DITL documentation: `../TPipe/TPipe/docs/core-concepts/developer-in-the-loop.md`
- TPipe JSON utilities: `../TPipe/TPipe/src/main/kotlin/Util/JsonExtractor.kt:377` (`extractJson<T>`), `Util/Util.kt:143` (`repairJsonString`)
- TPipe Pipe API: `../TPipe/TPipe/src/main/kotlin/Pipe/Pipe.kt:4064` (`setTransformationFunction`), `:4120` (`setPreInvokeFunction`), `:4157` (`setOnFailure`), `:2453` (`setJsonOutput`), `:2515` (`requireJsonPromptInjection`)
- TPipe flow control: `../TPipe/TPipe/src/main/kotlin/Pipe/BinaryContent.kt:153, 220, 234, 293, 308`
- Current state: `Builders/PlusWriterPipeline.kt`, `Builders/Util/PlusWriterUtil.kt`, `Builders/Util/AdvancedWriterUtil.kt:115-156` (sibling `transformStyle` for comparison)
- Existing test file: `src/test/kotlin/PlusWriterUtilTest.kt`
- Existing utility: `Builders/Util/PlusWriterUtil.kt:212-221` (`isWordCountSmallerByPercentage` — currently unused, will be reused)
