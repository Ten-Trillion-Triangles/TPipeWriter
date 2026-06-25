# TPipeWriter — MiniMax-M3 Generic OpenAI Edition

**TPipeWriter requires a `MINIMAX_API_KEY` to run.**

TPipeWriter is a Kotlin/JVM demo application for the [TPipe](https://github.com/cage/TPipe) framework — a structured-pipeline library for orchestrating LLM-driven creative-writing workflows. This branch (`GenericAI`) is the MiniMax-M3 Generic OpenAI edition: every pipe is a `GenericOpenAIPipe` targeting the MiniMax OpenAI Responses API at `https://api.minimax.io/v1`, so any developer with a MiniMax API key can clone, build, and run the demo.

## Setup

This project depends on the TPipe library at `../TPipe/TPipe/`. Build it first:

```bash
cd ../TPipe/TPipe
./gradlew shadowJar
```

### MiniMax API Key Configuration

TPipeWriter uses [MiniMax](https://platform.minimax.io) for inference. Get a key at [https://platform.minimax.io](https://platform.minimax.io) and set:

```bash
export MINIMAX_API_KEY="sk-..."
```

The application targets `https://api.minimax.io/v1` with `ApiMode.OpenAIResponses`. All pipes use model `MiniMax-M3` with a 512K context window. No other configuration is required.

## Build and Run

```bash
./gradlew build
./gradlew run
# or
./run.sh
```

The macOS Finder double-clickable launcher at `run.command` does the same with one click (it also runs `./gradlew installDist` first).

## Model Selection

This is a single-model edition: every pipeline uses `MiniMax-M3`. There is no per-pipe model selection — `ModelConfig.init()` is a no-op and all model-id variables (`ModelConfig.deepseekModelName`, `ModelConfig.claudeModelName`, etc.) resolve to `"MiniMax-M3"`. The runtime `/settings` command has been simplified to a single menu entry.

### Why MiniMax-M3?

`MiniMax-M3` is the newest, no-reasoning variant of the MiniMax family. It has a 512K context window and uses passive auto-cache on the `/v1` endpoint — well-suited for long-form creative writing where the entire story needs to be in the context. It does **not** support the `/anthropic/v1/messages` endpoint, does **not** support `setCacheControl()`, and emits no reasoning blocks. The `reasonWithMiniMax` adapter in `Builders/MiniMaxReasoning.kt` forces both knobs (the base `Pipe.useModelReasoning` flag and the wire `ReasoningConfig.enabled` block) to false so trace metadata accurately reflects the model's no-reasoning behavior.

If you later want reasoning, swap `MiniMax-M3` for `MiniMax-M2.7` in `ModelConfig.kt` and delete the `pipe.disableReasoning()` call in `MiniMaxReasoning.kt`. The wire payload and trace metadata will then carry reasoning per the caller's `ReasoningSettings`.

### Bedrock → MiniMax Model Substitutions

The original `main` branch used 17 distinct AWS Bedrock model IDs (Claude, Nova, DeepSeek, Llama, Qwen, Jamba, Palmyra, etc.). This edition collapses all of them to `MiniMax-M3`:

| Original Bedrock model | MiniMax-M3 | Notes |
|---|---|---|
| `anthropic.claude-sonnet-4-20250514-v1:0` | `MiniMax-M3` | All pipelines now use M3. Claude Sonnet 4 can be re-introduced via per-pipe model overrides if needed. |
| `amazon.nova-pro-v1:0` | `MiniMax-M3` | |
| `amazon.nova-lite-v1:0` | `MiniMax-M3` | |
| `openai.gpt-oss-20b-1:0` | `MiniMax-M3` | |
| `openai.gpt-oss-120b-1:0` | `MiniMax-M3` | |
| `us.meta.llama4-maverick-17b-instruct-v1:0` | `MiniMax-M3` | |
| `us.meta.llama3-3-70b-instruct-v1:0` | `MiniMax-M3` | |
| `us.meta.llama3-1-405b-instruct-v1:0` | `MiniMax-M3` | |
| `deepseek.r1-v1:0` | `MiniMax-M3` | DeepSeek R1 had reasoning; M3 does not. |
| `deepseek.v3-v1:0` | `MiniMax-M3` | |
| `qwen.qwen3-235b-a22b-2507-v1:0` | `MiniMax-M3` | |
| `qwen.qwen3-32b-v1:0` | `MiniMax-M3` | |
| `qwen.qwen3-coder-480b-a35b-v1:0` | `MiniMax-M3` | Was actually used for writing/theming, not coding. |
| `qwen.qwen3-coder-30b-a3b-v1:0` | `MiniMax-M3` | |
| `writer.palmyra-x5-v1:0` | `MiniMax-M3` | |
| `ai21.jamba-1-5-large-v1:0` | `MiniMax-M3` | |

Per-pipe model overrides can be reintroduced if needed; for now YAGNI. Add a second `ModelConfig.miniMaxM27ModelName` constant and route specific pipes through it via `setModel()`.

## Dependencies

- TPipe core (`com.TTT:TPipe:1.0.0`) — composite build from `../TPipe/TPipe/`
- TPipe-GenericOpenAI (`com.TTT:TPipe-GenericOpenAI:1.0.0`) — the Generic OpenAI pipe implementation (transitively depends on `TPipe-Bedrock` for shared utilities, but the application-side pipe type is `GenericOpenAIPipe`)
- TPipe-Defaults (`com.TTT:TPipe-Defaults:1.0.0`) — reasoning pipeline defaults
- Kotlin 2.2 / JVM 24
- kotlinx-serialization, kotlinx-coroutines

## Architecture

This edition follows the same surgical pattern as the `OpenRouter` branch (commit `10978206fa2b2eccdf633983a1eeb0e5ae527b86`): hard cutover from one provider to another, with all existing pipeline behavior preserved.

### Pipe shape

Every pipe is constructed as a `GenericOpenAIPipe` with:

```kotlin
val pipe = GenericOpenAIPipe()
    .setBaseUrl("https://api.minimax.io/v1")
    .setApiKey(genericOpenAIEnv.resolveApiKey())
    .setApiMode(ApiMode.OpenAIResponses)
    .setModel(ModelConfig.primaryModelName)
    .setContextWindowSize(512000)
    // ... pipe-specific setters ...
```

Bedrock-only setters are dropped: `setRegion()`, `useConverseApi()`, `enableCaching()`, `setReadTimeout()`. `enableCaching` is dropped because `MiniMax-M3` does not support `setCacheControl()` on OpenAIResponses mode; the 512K context window makes caching unnecessary for typical story-writing workloads.

### Reasoning adapter

`Builders/MiniMaxReasoning.kt` provides a `reasonWithMiniMax(...)` adapter that constructs a `GenericOpenAIPipe`, calls `pipe.disableReasoning()` to flip both the base `Pipe.useModelReasoning` flag and the wire `ReasoningConfig.enabled` to false, then delegates to `ReasoningBuilder.assignDefaults(...)` for the rest of the reasoning-pipe plumbing. This keeps the reasoning pipeline sites uniform across providers while honoring MiniMax-M3's no-reasoning behavior.

### Surgical improvements preserved

The 8 commits ahead of `main` on this branch (the "Plus Writer surgical improvements") are preserved unchanged:

- `applySurgicalReplacementsAndBank` with length sanity checks
- `SurgicalChanges.mode` support (replace, delete, insertAfter)
- `preInvokeLoreRepairPipe` checks `SurgicalChangeList` instead of `WorldFixes`
- `secondPassTransform` rewritten with modular approach
- Plus all 24 `PlusWriterUtilTest` tests passing

## Variant Editions

This branch is one of three TPipeWriter editions:

- **`main`**: original AWS Bedrock edition — requires AWS credentials and Bedrock access
- **`OpenRouter` branch**: OpenRouter edition — swaps every `BedrockMultimodalPipe` for `OpenRouterPipe`, retargets all model IDs to OpenRouter's `vendor/model-name` format, replaces AWS credentials with `OPENROUTER_API_KEY`
- **`GenericAI` branch (this branch)**: MiniMax-M3 Generic OpenAI edition — swaps every `BedrockMultimodalPipe` for `GenericOpenAIPipe`, single model `MiniMax-M3`, replaces AWS credentials with `MINIMAX_API_KEY`, uses OpenAI Responses API mode

## Testing

```bash
./gradlew test                              # all unit + live tests
MINIMAX_API_KEY=sk-... ./gradlew test       # also runs the live smoke + streaming tests
```

68 tests total: 25 unit tests for the MiniMax-M3 refactor (`MiniMaxModelConfigTest` x19, `MiniMaxSettingsTest` x6), 2 live tests (`MiniMaxSmokeTest`, `MiniMaxStreamingTest` — gated on `MINIMAX_API_KEY`, skip cleanly when absent), plus 41 existing tests across `ChapterManagerTest`, `ChapterSaveLoadTest`, `IdeaPipelineTest`, `PlusWriterUtilTest` (the surgical-improvement suite). All 68 pass when `MINIMAX_API_KEY` is set.

## Development

The main class is at `src/main/kotlin/com/example/tpipewriter/Main.kt`. The application is an interactive shell — run it and type `/help` to see available commands. The plan that drove this refactor is at `.hermes/plans/minimax-m3-generic-openai/plan.md`.

## License

See LICENSE.