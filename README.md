# TPipeWriter — MiniMax-M3 Generic OpenAI Edition

**TPipeWriter requires a `MINIMAX_API_KEY` to run.**

TPipeWriter is a Kotlin/JVM demo application for the [TPipe](https://github.com/cage/TPipe) framework — a structured-pipeline library for orchestrating LLM-driven creative-writing workflows. This branch (`GenericAI`) is the MiniMax-M3 Generic OpenAI edition: every pipe is a `GenericOpenAIPipe` targeting the MiniMax OpenAI Responses API at `https://api.minimax.io/v1`, so any developer with a MiniMax API key can clone, build, and run the demo.

**New operators: start at [RUNBOOK.md](RUNBOOK.md)** for the from-scratch setup checklist, common pitfalls, and diagnostic recipes. This README is the developer-facing reference for code-level details.

## How this branch differs from `main`

This is a **single-model, single-provider hard cutover** from `main`:

| Aspect | `main` | `GenericAI` (this branch) |
|--------|--------|---------------------------|
| Provider | AWS Bedrock | MiniMax |
| API key env var | AWS credentials chain | `MINIMAX_API_KEY` |
| Pipe type | `BedrockMultimodalPipe` | `GenericOpenAIPipe` |
| Models | 17 distinct Bedrock model IDs (Claude, Nova, DeepSeek, Llama, Qwen, Jamba, Palmyra) | Single model: `MiniMax-M3` |
| API mode | Converse API | OpenAI Responses (`/v1/responses`) |
| Endpoint | AWS Bedrock region URLs | `https://api.minimax.io/v1` |
| Context window | Varies per model | 512K (fixed) |
| Reasoning | Per-model, with extended thinking on Claude | Forced off (M3 has no reasoning) |
| Tests | 41 (unit only) | 80 (unit + 7 live API tests + 4 streaming diagnostics) |
| Library dependency | `com.TTT:TPipe-Bedrock:1.0.0` | `com.TTT:TPipe-GenericOpenAI:1.0.0` + **TPipe library fork with streaming fix (see below)** |

The `OpenRouter` branch (referenced in earlier docs) made a similar
cutover from Bedrock to OpenRouter. This branch follows the same
surgical pattern but targets the OpenAI Responses API.

## Library fork notes

This branch depends on a **fork of the TPipe library** that contains a
critical streaming-fix commit. The TPipe library lives at
`../TPipe/TPipe/` (composite build) and MUST include this commit or
the streaming output will buffer until the entire response arrives.

**Required TPipe library commit:**

```
8e4b8d76 fix(tpipe-genericopenai): bypass Ktor for streaming — restore real-time SSE delivery
```

If your `../TPipe/TPipe/` doesn't have this commit, the application
will still RUN but will appear non-streaming (the entire LLM response
arrives in one batch at the end). See
[§Streaming fix history](#streaming-fix-history) for the technical
details and [RUNBOOK.md §6.3](RUNBOOK.md) for how to verify the fix
is active.

## Setup

This project depends on the TPipe library at `../TPipe/TPipe/`. Build
it first:

```bash
cd ../TPipe/TPipe
./gradlew shadowJar
```

### MiniMax API Key Configuration

TPipeWriter uses [MiniMax](https://platform.minimax.io) for inference.
Get a key at [https://platform.minimax.io](https://platform.minimax.io) and set:

```bash
export MINIMAX_API_KEY="sk-..."
```

The application targets `https://api.minimax.io/v1` with `ApiMode.OpenAIResponses`. All pipes use model `MiniMax-M3` with a 512K context window. No other configuration is required.

`run.sh` also accepts `AUXILIARY_VISION_API_KEY` as a fallback for
developers whose environment uses that variable name. If neither is
set, `run.sh` exits with a clear error message.

## Build and Run

```bash
./gradlew build
./gradlew run
# or
./run.sh
```

The macOS Finder double-clickable launcher at `run.command` does the same with one click (it also runs `./gradlew installDist` first).

For step-by-step setup, prerequisites, and verification recipes, see
[RUNBOOK.md](RUNBOOK.md).

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

## Streaming fix history

**This section documents a critical bug that was fixed in this branch's
TPipe library fork.** It belongs here (and not in RUNBOOK.md) because
it explains the code-level reason the TPipe library had to be forked.

### The bug

When this branch was first merged, `/chat` and `/write` produced
output that appeared all at once after a long wait, instead of
streaming token-by-token like Bedrock streaming. The user-visible
symptom: "Thinking..." for 30 seconds, then the entire 200-word
response appears in one frame.

### The investigation

Four diagnostic tests were added to `src/test/kotlin/com/example/tpipewriter/`
to isolate the failure:

| Test | What it proves |
|------|----------------|
| `RawHttpStreamingTest` | `java.net.HttpURLConnection` with chunked transfer encoding streams MiniMax-M3 correctly. Chunks arrive 200-700ms apart. The model and endpoint are fine. |
| `RawKtorStreamingTest` | Raw Ktor 3.3.3 CIO with `bodyAsChannel()` returns all chunks in one batch at the moment the response stream closes. 0ms gaps between every chunk. The bug is in the HTTP transport. |
| `KtorSsePluginTest` | Ktor 3.3.3's SSE plugin (`client.sse { incoming.collect }`) streams correctly. Documents an alternative approach we considered. |
| `MiniMaxStreamingTimingTest` | End-to-end test of `GenericOpenAIPipe` with timing. BEFORE the fix: 14 chunks at +4541ms with 0ms gaps. AFTER the fix: 15 chunks 43-715ms apart over 7.1 seconds. |

The tests proved the bug was real, isolated it to `bodyAsChannel()`,
and gave us a regression net.

### Root cause

Ktor 3.x's `bodyAsChannel()` returns a `ByteReadChannel` that does
**not** deliver bytes incrementally for chunked transfer-encoded
SSE responses. All bytes arrive in one batch when the response
stream closes.

Tracing through `ktor-http-cio/HttpBody.kt::parseHttpBody`, the
channel is read via `skipCancels` which uses
`HttpClientDefaultPool.useInstance { buffer -> ... }` to copy bytes
from the socket into a buffered pool. The line-level
`readUTF8Line()` reads from the **buffered** ByteReadChannel, not
from the socket. By the time `readUTF8Line()` unblocks, all the data
has been pulled into the pool buffer — defeating the whole point of
streaming.

Bedrock streaming worked because the AWS SDK uses an event-stream
model (`response.body?.collect { event -> ... }`) that yields
individual events as they arrive. Ktor doesn't have the equivalent
for plain body channels.

### The fix

The TPipe library fork adds `executeStreamingDirect()` to
`GenericOpenAIPipe` (commit `8e4b8d76`). When `streamingEnabled`
is true, the streaming call now bypasses Ktor entirely and opens a
direct `java.net.HttpURLConnection` with `setChunkedStreamingMode(0)`:

```kotlin
val conn = (URL("$baseUrl${getEndpoint()}").openConnection() as HttpURLConnection).apply {
    requestMethod = "POST"
    doOutput = true
    setChunkedStreamingMode(0)
    setRequestProperty("Authorization", "Bearer $apiKey")
    setRequestProperty("Content-Type", "application/json")
}
conn.outputStream.use { it.write(jsonRequest.toByteArray()) }

BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
    reader.lineSequence().forEach { line ->
        // ... parse SSE data: lines, emit chunks per delta ...
    }
}
```

Each `readLine()` blocks per-line, so `emitStreamingChunk` fires
per SSE delta as the bytes arrive on the socket. This preserves
real-time streaming semantics.

The non-streaming code path still uses Ktor (which is fine — the
buffering only matters when you care about streaming). Only the
streaming path bypasses Ktor.

### Regression coverage

The four diagnostic tests above prevent future regressions. Any of
them that produces 0ms inter-chunk gaps is a regression. The
`StreamingPropagationTest` (7 unit tests, no live API) also covers
the callback propagation that propagates streaming callbacks from
parent pipes to child pipes (reasoning, validation, etc.) — another
sibling bug fixed in commit `db38bbba`.

## Dependencies

- TPipe core (`com.TTT:TPipe:1.0.0`) — composite build from `../TPipe/TPipe/`
- TPipe-GenericOpenAI (`com.TTT:TPipe-GenericOpenAI:1.0.0`) — the Generic OpenAI pipe implementation, **with the streaming-fix commit `8e4b8d76`**
- TPipe-Defaults (`com.TTT:TPipe-Defaults:1.0.0`) — reasoning pipeline defaults
- Kotlin 2.2 / JVM 24
- kotlinx-serialization, kotlinx-coroutines

The GenericOpenAI pipe transitively depends on `TPipe-Bedrock` for
shared utilities, but the application-side pipe type is
`GenericOpenAIPipe` everywhere.

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
- **`GenericAI` branch (this branch)**: MiniMax-M3 Generic OpenAI edition — swaps every `BedrockMultimodalPipe` for `GenericOpenAIPipe`, single model `MiniMax-M3`, replaces AWS credentials with `MINIMAX_API_KEY`, uses OpenAI Responses API mode, **real-time streaming verified live**

## Testing

```bash
./gradlew test                              # all unit + live tests
MINIMAX_API_KEY=sk-... ./gradlew test       # also runs the live smoke + streaming tests
```

**80 tests total** when `MINIMAX_API_KEY` is set, 0 skipped:

- **19** `MiniMaxModelConfigTest` (unit, no API key needed)
- **6** `MiniMaxSettingsTest` (unit, no API key needed)
- **1** `MiniMaxSmokeTest` (live API — non-streaming round-trip)
- **1** `MiniMaxStreamingTest` (live API — chunk arrival via streaming callback)
- **1** `MiniMaxStreamingTimingTest` (live API — measures inter-chunk gaps; the regression net for the streaming fix)
- **4** `IdeaPipelineTest` (unit)
- **24** `PlusWriterUtilTest` (unit — surgical improvements preserved from main)
- **5** `ChapterManagerTest` (unit)
- **5** `ChapterSaveLoadTest` (unit)
- **7** `StreamingPropagationTest` (unit — callback propagation to child pipes; regression net for the propagation fix)
- **1** `RawHttpStreamingTest` (live API — proves MiniMax-M3 streams when given a working HTTP transport)
- **1** `RawKtorStreamingTest` (live API — proves Ktor `bodyAsChannel` buffers everything until end)
- **1** `KtorSsePluginTest` (live API — proves Ktor SSE plugin streams correctly)
- **4** additional helper tests in `IdeaPipelineTest` and `ChapterManagerTest`

When `MINIMAX_API_KEY` is not set, the 4 live tests skip cleanly via
`Assumptions.assumeTrue`. All 76 unit tests still run.

The 4 streaming tests are documented in detail in
[§Streaming fix history](#streaming-fix-history).

## Development

The main class is at `src/main/kotlin/com/example/tpipewriter/Main.kt`. The application is an interactive shell — run it and type `/help` to see available commands. The plan that drove this refactor is at `.hermes/plans/minimax-m3-generic-openai/plan.md`.

For day-to-day operation, see [RUNBOOK.md](RUNBOOK.md). For the verification campaign that produced this branch, see [TUI_TEST_REPORT.md](TUI_TEST_REPORT.md).

## License

See LICENSE.