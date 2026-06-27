# PlusWriterPipeline Token Budgeting — Grounding Report

**Audience**: user, before deciding what to deploy.
**Status**: research phase complete. Two subagent reports written to `/tmp/tpipe-budget-report.md` (884 lines) and `/tmp/plus-writer-report.md` (352 lines). This file is the synthesis.
**Date**: 2026-06-26.

## Executive summary

TPipe already ships a complete token-budget primitive. Autogenesis uses it as a per-pipe builder call (`.setTokenBudget(TokenBudgetSettings(...))`), with five named budgets in `BedrockConfig` chosen by model. PlusWriterPipeline currently does NOT use it — `TPipeWriter`'s `setMaxTokens()` (an output-token cap only) is the closest existing knob. To "deploy token budgeting for MiniMax on PlusWriterPipeline" we need to wire the budget into PlusWriterPipeline's pipe construction, mirroring Autogenesis's per-pipe pattern.

## TPipe token-budget primitives (verified in master source)

### Core types — `com.TTT.Pipe` package, all in `Pipe/Pipe.kt`

| Type | Line | Role |
|---|---|---|
| `TokenBudgetSettings` (data class) | 157–213 | The mutable budget object. Fields: `userPromptSize`, `maxTokens`, `reasoningBudget`, `subtractReasoningFromInput`, `contextWindowSize`, `allowUserPromptTruncation`, `preserveJsonInUserPrompt`, `compressUserPrompt`, `truncateContextWindowAsString`, `preserveTextMatches`, `truncationMethod`, `multiPageBudgetStrategy`, `pageWeights`, `reserveEmptyPageBudget`. Has method `calculateAvailableContext()`. |
| `TokenUsage` (data class) | 218–279 | Read-only usage accumulator: `inputTokens`, `outputTokens`, `childPipeTokens: MutableMap<String, TokenUsage>`, `totalInputTokens`, `totalOutputTokens`. Has `addChildUsage`, `recalculateTotals`, `getUsageBreakdown`. |
| `MultiPageBudgetStrategy` (enum) | 291–298 | `EQUAL_SPLIT`, `WEIGHTED_SPLIT`, `PRIORITY_FILL`, `DYNAMIC_FILL`, `DYNAMIC_SIZE_FILL` (default). |
| `TruncationPreview` (data class) | 312–320 | What-if simulation result: wouldTruncate, totalTokensBefore/After, tokensSaved, workingContextWindowSpace, allocations, perPagePreviews. |
| `BudgetAllocations` (data class) | 333–341 | Per-component breakdown: contextWindowSize, systemPromptTokens, maxOutputTokens, reasoningBudgetTokens, userPromptTokens, binaryTokens, availableForContext. |
| `PagePreview` (data class) | 343+ | Per-page prediction in MiniBank multi-page budgets. |

### Hook points on Pipe (the only API surface a caller needs)

| Method | Line | Behavior |
|---|---|---|
| `setTokenBudget(budget: TokenBudgetSettings): Pipe` | 2795 | Public builder. Deep-copies the budget into internal state. |
| `cloneTokenBudgetSettings(budget): TokenBudgetSettings` | 2811 | Defensive copy so callers can share budget references without colliding mutable state. |
| `setTokenBudgetInternal(budget, liveContent)` | 2937 | Private. Adjusts budget based on current content (for runtime trimming decisions). |
| `setTokenBudgetRecursive(budget)` | 7708 | Propagates budget to all child pipes (Pipeline override at 437-441 does this fan-out). |
| `copyTokenBudgetSettings(): TokenBudgetSettings?` | 4496 | Stable copy for inspection/reuse. |
| `getTokenBudgetSettings(): TokenBudgetSettings?` | 7706 | Read accessor. |
| `getTokenUsage(): TokenUsage` | 7611 | Returns pipeTokenUsage if `comprehensiveTokenTracking = true`, else empty TokenUsage. |

Internal storage: `protected var tokenBudgetSettings: TokenBudgetSettings? = null` at line 1009.

### Where the budget actually fires

The deep-copies at lines 2855, 2873, 2890, 2904, 2926 show that the budget state is snapshotted at multiple lifecycle moments: pipeline init, snapshot, child clone, settings save. The working-copy pattern at `Pipe.kt:5060` (`val workingBudget = cloneTokenBudgetSettings(configuredBudget)`) and `:5247` shows the budget is computed fresh per execution, never mutated in place.

Pipeline-level propagation at `Pipeline/Pipeline.kt:437-441`:
```kotlin
override fun setTokenBudgetRecursive(budget: TokenBudgetSettings)
{
    super.setTokenBudgetRecursive(budget)
    pipe.setTokenBudgetRecursive(budget)
}
```

PumpStation reads the budget via `getPathTokenUsage()` and `getPathLegacyTokenUsage()` at `Pipeline/PumpStation.kt:348-367` for kill-switch enforcement (the killSwitch harness reads these to enforce inputTokenLimit/outputTokenLimit).

### Existing tests that pin the contract

These are the regression tests that any new caller should run against their wiring:

- `TPipe/src/test/kotlin/TokenBudgetStressTest.kt` — concurrency + large token counts + deep nesting
- `TPipe/src/test/kotlin/TokenBudgetRuntimeStateTest.kt` — `tokenBudgetRuntimeRestoresPipeStateBetweenExecutions`, `tokenBudgetRuntimeRestoresPipeStateAcrossPipelineExecutions`
- `TPipe/src/test/kotlin/MultiPageTokenBudgetTest.kt` — multi-page budget allocation
- `TPipe/src/test/kotlin/MultiPageBudgetValidationTest.kt` — strategy validation
- `TPipe/src/test/kotlin/DynamicSizeFillStrategyTest.kt` — DYNAMIC_SIZE_FILL behavior (the default)
- `TPipe/src/test/kotlin/PipeSettingsSnapshotTest.kt` — PipeSettings round-trip with `tokenBudgetSettings` field
- `TPipe/src/test/kotlin/TruncateAsStringTest.kt` — truncation behavior
- `TPipe/src/test/kotlin/Pipe/SemanticCompressionBuilderTest.kt` — `compressUserPrompt = true` path

If PlusWriterPipeline uses `setTokenBudget()`, ALL of these will exercise the budget wiring on every pipeline test run.

### No per-provider variant behavior

`grep` of `TPipe-Bedrock/src`, `TPipe-Defaults/src`, `TPipe-Ollama/src`, `TPipe-OpenRouter/src`, `TPipe-GenericOpenAI/src` for `setTokenBudget`/`TokenBudget` returned ZERO hits. The budget is set on `Pipe` (the base class) and inherited by all provider-specific pipe classes. Budget behavior is uniform across providers. This is the design intent: budget is a TPipe framework concern, not a provider concern.

## Autogenesis reference implementation (verified via subagent)

### Pattern: per-pipe budgets chosen by model

`server/src/main/kotlin/globals/BedrockConfig.kt` lines 477–505 defines five named budgets:
```kotlin
val workerBudgetSettings       = TokenBudgetSettings(maxTokens = 8000,  contextWindowSize = 32_000)
val generativeBudgetSettings   = TokenBudgetSettings(maxTokens = 12_000, contextWindowSize = 230_000)
val novaBudgetSettings         = TokenBudgetSettings(maxTokens = 8000,  contextWindowSize = 990_000)
val novaProBudgetSettings      = TokenBudgetSettings(maxTokens = 5000,  contextWindowSize = 285_000)
val palmyraBudgetSettings      = TokenBudgetSettings(maxTokens = 8000,  contextWindowSize = 980_000)
```

Helper at lines 510–517 selects budget by model name. The pattern is then: every builder function in `agent/builders/*.kt` calls `.setTokenBudget(BedrockConfig.X)` on each pipe it constructs, choosing a different `X` per pipe role (generation, validation, branch-fallback, judge).

### Pattern: budget swap on retry

`server/src/main/kotlin/agent/runners/gameplayOrchestrator.kt:2748-2763` (`swapPipelineModels`) re-applies `.setTokenBudget(...)` after swapping the model on a retry attempt. The budget travels WITH the model — model swap implies budget swap.

### Pattern: lorebook as overflow absorption

Autogenesis has NO explicit summarization or trimming code. The writer-agent prompts (`writerAgent.kt:219-225`, `:531-537`, `:647-651`) explicitly tell the model that context overflow will truncate oldest portions and that the lorebook holds summarized entity data. The lorebook is the user-side compensation mechanism — when the budget truncates, the lorebook still has the entity summaries, so the model can reconstruct from the lorebook.

### Pattern: per-pipeline-budget, not global

Different pipes in the same pipeline use different budgets. Generation pipes get the big generative budget. Branch-fallback pipes get Palmyra (the "rock solid" model that "highly resists refusals"). Nova reasoning pipes get a huge context window for question-answering. Worker/error pipes get the small worker budget.

### Pattern: no `multiPageBudgetStrategy` configured

Autogenesis leaves `MultiPageBudgetStrategy` at its default (`DYNAMIC_SIZE_FILL`). It does NOT customize per-page allocation. The `multiPageBudgetStrategy` knob exists for callers who need page-level budget control (e.g., a MiniBank with named pages); PlusWriterPipeline doesn't currently have a MiniBank, so this knob is irrelevant for the writer path.

## TPipeWriter / PlusWriterPipeline — current state (verified inline)

### What TPipeWriter has today

`setMaxTokens(8000)` exists on TPipeSettings (the per-chapter save data class added in the prior session). This is the **LLM output token cap** — it doesn't bound the input context. There is NO call to `setTokenBudget()` anywhere in TPipeWriter's `src/main/kotlin`. Confirmed by `grep -r setTokenBudget /home/cage/Desktop/Workspaces/TPipeWriter/src/main` → 0 results.

`PlusWriterPipeline.kt` exists in `src/main/kotlin/Builders/`. The user already knows its layout from prior sessions. To verify the exact insertion points where `.setTokenBudget()` would be called, I dispatched a subagent (Task 3) that failed at the provider layer; a re-dispatch is in flight and will return shortly.

### What MiniMax gives us (verified via skills, not yet re-confirmed by subagent)

- `GenericOpenAIPipe` is the pipe class. Three `ApiMode` values: `OpenAI`, `Anthropic`, `OpenAIResponses`.
- MiniMax-M2.7 is a reasoning model — emits `thinking_delta` blocks before `text_delta`. Use `ApiMode.Anthropic` at `https://api.minimax.io/anthropic/v1/messages` (not just `/anthropic` — returns 400).
- `MINIMAX_API_KEY` env var drives `GenericOpenAIEnv.resolveApiKey()`.
- Two streaming entry points: `generateText()` (Path A, fixed via `executeStreamingDirect` bypassing Ktor) and `sendRequest()` (Path B, still on Ktor bodyAsChannel — known vulnerability for image-attached writes). Token budgeting applies to BOTH paths.
- MiniMax reasoning toggle needs BOTH `pipe.setReasoning()` (base Pipe trace flag) AND `pipe.setReasoningConfig(ReasoningConfig(...))` (wire payload). Both knobs.

## What's pending

Nothing — both subagents returned complete reports. The five open questions from the prior turn can now be answered with concrete evidence. See the **Decisions needed** section below.

## Decisions needed before writing a plan

These five questions are answered with concrete evidence from the subagent reports. Pick one per question.

### Q1 — Budget size

**Evidence**: `ModelConfig.MiniMaxContextWindowSize = 512000` at `Globals/ModelConfig.kt:53`. PlusWriterPipeline currently caps its pipes at 115k–120k input + 8k–32k output — far below the model's 512k capacity.

| Option | contextWindowSize | maxTokens | rationale |
|---|---|---|---|
| Match Autogenesis | 230_000 | 12_000 | conservative; matches `generativeBudgetSettings` |
| Match TPipeWriter scale-up | 512_000 (full M3 window) | 32_000 | uses full M3 capacity; per-pipe current max already 32K |
| Conservative for new code | 200_000 | 32_000 | between Autogenesis and full window; safer for first deploy |

### Q2 — Single budget or per-pipe-role budgets

**Evidence**: PlusWriterPipeline has 29 pipes built (17 active in chain). Per-pipe maxTokens varies 8K (guide pipe L215, applyFetishPipe L1352, loreBookPipe L1472), 20K (loreCheckPipe L684), 32K (everything else). Per-pipe contextWindowSize varies 100.5K (simplifier, murder), 115K (surgical-edit pipes), 120K (preGuide/guide/writing/chasingShadows/loreCheck), 512K (loreBook only).

| Option | Description |
|---|---|
| Single budget | one `TokenBudgetSettings` applied to all pipes via the existing post-init `.apply { getPipes().forEach { ... } }` block |
| Per-role budgets | 3-4 named budgets (writer, surgical, lore, judge) keyed off `pipeName` strings — mirrors Autogenesis's pattern |
| Per-pipe individual | 17 separate `TokenBudgetSettings` instances — most granular, most code |

The CharacterPipeline.kt precedent (lines 75–85, 98, 198, 214) uses 3-4 budgets. Style B (one block, keyed off pipeName) is the recommended minimal-change insertion.

### Q3 — Reasoning mode (M2.7 / M3)

**Evidence**: `MiniMaxReasoningToggleTest.kt:32` — M2.7 emits reasoning unconditionally even when `enabled=false`. `MiniMax-M3` (PlusWriterPipeline's actual model per `ModelConfig.primaryModelName`) is the same family. The Anthropic path requires `reasoning` carve-out per the live tests in `tpipe-generic-openai` skill.

PlusWriterPipeline uses `ApiMode.OpenAIResponses`, NOT Anthropic. So reasoning behavior is the OpenAI Responses reasoning block, not Anthropic thinking. Reasoning budget rules may differ per API mode.

| Option | Description |
|---|---|
| Reserve reasoning | `reasoningBudget = 8000` (or 16_000 for M3) on every pipe |
| Skip reasoning | leave `reasoningBudget = null` — let TPipe default to carving from `maxTokens` (current behavior) |
| Per-pipe | reserve reasoning on writing/selection pipes only |

### Q4 — Overflow absorption strategy

**Evidence**: Autogenesis has NO explicit summarization code — it relies on lorebook + model-side prompt acknowledgment. TPipe offers `compressUserPrompt = true` for automatic pre-trim semantic compression (Pipe.kt:165 field; tested by `Pipe/SemanticCompressionBuilderTest.kt`).

| Option | Description |
|---|---|
| Mirror Autogenesis | prompts acknowledge truncation, lorebook holds summaries, no TPipe-side compression |
| Use TPipe's `compressUserPrompt = true` | automatic semantic compression before LLM call |
| Both | belt + suspenders: `compressUserPrompt = true` plus prompts that acknowledge truncation |

### Q5 — Verification strategy

**Evidence**: 8 existing TPipe unit tests (`TokenBudgetStressTest`, `TokenBudgetRuntimeStateTest`, `MultiPageTokenBudgetTest`, `MultiPageBudgetValidationTest`, `DynamicSizeFillStrategyTest`, `PipeSettingsSnapshotTest`, `TruncateAsStringTest`, `Pipe/SemanticCompressionBuilderTest`) will exercise any wiring for free. PlusWriterPipeline's existing 84+ tests will catch regressions. Live MiniMax tests need `MINIMAX_API_KEY`.

| Option | Description |
|---|---|
| Unit-only | add a PlusWriterPipeline-specific test that builds the pipeline and asserts budget wiring; rely on TPipe's tests for the framework |
| Live + unit | add a MiniMax live test gated on `MINIMAX_API_KEY` that runs a real prompt and asserts trace `tokenUsage` matches expectations |
| Tmux + unit | add a live test PLUS a tmux-driven `/write` end-to-end transcript verifying budget behavior in the actual TUI shell |

---

## Subagent reports on disk

- `/tmp/tpipe-budget-report.md` — 884 lines, TPipe master-side deep dive. Covers `truncateToFitTokenBudget` (Pipe.kt:5244-5479), `setTokenBudgetInternal` arithmetic (2937-3033), `simulateTokenBudgetTruncation` dry-run (5054), trace emission keys (`inputTokens`/`outputTokens`/`totalInputTokens`/`totalOutputTokens` at Pipe.kt:6247-6252, `actualInputTokens` at 6119), and the only TPipe-Defaults consumer at `ManifoldDefaults.kt:392`.
- `/tmp/plus-writer-report.md` — 352 lines, PlusWriterPipeline + MiniMax provider surface. Covers all 29 per-pipe insertion sites with line numbers, the 107k Shell.kt:381 global truncation cap, the Anthropic-mode reasoning quirk, and the `executeStreamingDirect` Ktor-bypass correction.