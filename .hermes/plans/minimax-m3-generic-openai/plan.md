# MiniMax-M3 Generic OpenAI Refactor of TPipeWriter

> **For Hermes:** Use subagent-driven-development for the parallel-safe tasks (smoke test, README, model name table, dependency swap, util cleanups) and inline direct execution for the cross-file surgical tasks (Env.kt, ModelConfig.kt, WriterSettings.kt, PlusWriterPipeline.kt, ExpansionPipeline.kt). Two-stage review per task. Mark todos in this file as each task completes.

**Goal:** Strip AWS Bedrock from TPipeWriter's GenericAI branch. Replace every `BedrockMultimodalPipe` / `BedrockPipe` with `GenericOpenAIPipe` configured for the MiniMax Responses API (`https://api.minimax.io/v1` + `ApiMode.OpenAIResponses`, model `MiniMax-M3`, 512K context). Replace every `BedrockXxx` model id and ARN binding with a MiniMax model name (single model: `MiniMax-M3`). Update `TPipeBudgeting` settings for the 512K context window. Add live smoke test. Bug-test end-to-end via tmux-driven TUI.

**Architecture:** Single-provider, hard cutover, surgically identical to the existing `OpenRouter` branch pattern (commit `10978206fa2b2eccdf633983a1eeb0e5ae527b86`). Replace the `com.TTT:TPipe-Bedrock` Maven dependency with `com.TTT:TPipe-GenericOpenAI` from the local composite build at `../TPipe/TPipe/`. Every pipe is constructed as a `GenericOpenAIPipe` with `setApiMode(ApiMode.OpenAIResponses)`, `setBaseUrl("https://api.minimax.io/v1")`, `setApiKey(MINIMAX_API_KEY)`. The `bedrockEnv` global is replaced with `genericOpenAIEnv`. `ModelConfig.init()` becomes a no-op (no ARN binding). Bedrock-specific helpers (`bedrockPipe.*`, `bedrockEnv.*`) are removed.

**Tech Stack:** Kotlin 2.2.0 (JVM 24), kotlinx-serialization 1.9.0, kotlinx-coroutines 1.10.1, TPipe core (composite build from `../TPipe/TPipe`), `com.TTT:TPipe-GenericOpenAI:1.0.0` (replaces `TPipe-Bedrock`), `com.TTT:TPipe-Defaults:1.0.0`. JUnit 5.10.1 for tests.

**Provider rationale:** `MiniMax-M3` is the no-reasoning variant, so `useModelReasoning` stays false everywhere. `MiniMax-M3` does NOT support the `/anthropic/v1/messages` endpoint and does NOT support `setCacheControl()`, so we drop every `enableCaching()` / `.useConverseApi()` call from the Bedrock-era pipes. `MiniMax-M3` has 512K context, so every `.setContextWindowSize(N)` for N < 512000 gets bumped to 512000.

**Tracking mode:** `goal` (set in this session via `GoalManager.set(...)`). Lightweight persistent context marker. The plan file's todo list is the source of truth for per-task progress.

**Subagent delegation posture:** Plan-decides. Inline for cross-file surgical tasks (files that share assumptions about env vars, model names, pipe chaining, or persistent settings). Subagents for isolated files (smoke test, README, model name table, dependency swap, util cleanups).

---

## Current State (verified)

- Branch: `GenericAI` (clean, 8 commits ahead of `main` with surgical improvements)
- 44 Kotlin files, 1 Gradle build file, 1 README
- Provider: AWS Bedrock (`BedrockMultimodalPipe` / `BedrockPipe` with `.setModel(BEDROCK_MODEL_ID)` and `.bindInferenceProfile(...)` ARN registrations)
- Tests: `NovaTest.kt` (Bedrock live smoke test), `PlusWriterUtilTest.kt`, `ChapterManagerTest.kt`, `IdeaPipelineTest.kt`, `ChapterSaveLoadTest.kt`
- Composite build: `../TPipe/TPipe/` with `TPipe-GenericOpenAI` already present (verified via `ls /home/cage/Desktop/Workspaces/TPipe/TPipe/`)
- `MINIMAX_API_KEY` is set locally (confirmed by user)

## Files To Touch

### Source (main)
- `build.gradle.kts` — swap `TPipe-Bedrock` for `TPipe-GenericOpenAI`
- `src/main/kotlin/Globals/ModelConfig.kt` — rewrite all model ids to `MiniMax-M3`; `init()` becomes no-op
- `src/main/kotlin/Globals/Env.kt` — replace `bedrockEnv` references, replace all `BedrockMultimodalPipe`/`BedrockPipe` constructions, bump context windows to 512000, drop `enableCaching`/`useConverseApi` calls
- `src/main/kotlin/Globals/Prompts.kt` — no provider changes expected, verify
- `src/main/kotlin/Builders/ExpansionPipeline.kt` — replace pipe type, update model id, drop caching, bump context window
- `src/main/kotlin/Builders/PlusWriterPipeline.kt` — replace pipe type, update model id, drop caching, bump context window
- `src/main/kotlin/Builders/CharacterPipeline.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/DialogueConnector.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/ChapterRewritePipeline.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/AdvancedWriterPipeline.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/ReasoningBuilders.kt` — replace pipe type; reasoning settings no-op for M3 (no reasoning model) — keep file intact for shape but disable reasoning
- `src/main/kotlin/Builders/PitchSlideWriterPipeline.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/Util/ChapterRewriteUtil.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/Util/AdvancedWriterUtil.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/Util/PlusWriterUtil.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/Util/RecursiveReasoning.kt` — replace pipe type, update model id
- `src/main/kotlin/Builders/MiniMaxReasoning.kt` (NEW) — adapter mirroring `OpenRouterReasoning.kt` for `GenericOpenAIPipe`
- `src/main/kotlin/Shell/Shell.kt` — replace `bedrockEnv` references
- `src/main/kotlin/Shell/SettingsSubshell.kt` — update settings persistence for new model names + 512K budget
- `src/main/kotlin/Shell/CharacterChatSubshell.kt` — replace pipe type, update model id
- `src/main/kotlin/Shell/TokenCountingSubshell.kt` — replace pipe type, update model id
- `src/main/kotlin/Shell/AuthorSubshell.kt` — replace pipe type, update model id
- `src/main/kotlin/Shell/GuideSubshell.kt` — replace pipe type, update model id
- `src/main/kotlin/Shell/WriterSubshell.kt` — replace pipe type, update model id
- `src/main/kotlin/Shell/PtichSubshell.kt` — replace pipe type, update model id
- `src/main/kotlin/Structs/WriterSettings.kt` — replace `BedrockPipe` cast with `GenericOpenAIPipe`, update model name table, add 512K budget constant
- `src/main/kotlin/Structs/RequestList.kt` — verify, likely no changes
- `src/main/kotlin/Structs/RewriteData.kt` — verify, likely no changes
- `src/main/kotlin/Util/StreamingUtil.kt` — replace Bedrock-specific streaming config
- `src/main/kotlin/Util/Util.kt` — verify, likely no changes
- `src/main/kotlin/Util/EnhancedInput.kt` — verify, likely no changes
- `src/main/kotlin/Util/Buffer.kt` — verify, likely no changes
- `src/main/kotlin/Chapter/*.kt` — verify, no provider-specific code expected
- `src/main/kotlin/com/example/tpipewriter/Main.kt` — replace `bedrockEnv` references, update startup config

### Tests (test)
- `src/test/kotlin/com/example/tpipewriter/NovaTest.kt` — DELETE
- `src/test/kotlin/com/example/tpipewriter/MiniMaxSmokeTest.kt` (NEW) — live test, gated on `MINIMAX_API_KEY`, asserts a non-streaming MiniMax-M3 call returns a non-blank response
- `src/test/kotlin/com/example/tpipewriter/MiniMaxStreamingTest.kt` (NEW) — live test, asserts SSE chunk ordering on MiniMax-M3
- `src/test/kotlin/com/example/tpipewriter/MiniMaxModelConfigTest.kt` (NEW) — unit test that `ModelConfig.init()` is no-op and `deepseekModelName == "MiniMax-M3"`
- `src/test/kotlin/com/example/tpipewriter/MiniMaxSettingsTest.kt` (NEW) — unit test that `WriterSettings.toModelSettings()` produces GenericOpenAIPipe-shaped settings with 512K context
- All other tests (`PlusWriterUtilTest.kt`, `ChapterManagerTest.kt`, `IdeaPipelineTest.kt`, `ChapterSaveLoadTest.kt`) — verify they still compile and pass

### Docs
- `README.md` — full rewrite following the OpenRouter branch README structure

---

## Todo List

```json
[
  {"id": "t01-dep-swap", "content": "Swap TPipe-Bedrock dep for TPipe-GenericOpenAI in build.gradle.kts", "status": "pending"},
  {"id": "t02-model-config", "content": "Rewrite Globals/ModelConfig.kt — single MiniMax-M3, no-op init()", "status": "pending"},
  {"id": "t03-env-rewrite", "content": "Rewrite Globals/Env.kt — replace BedrockMultimodalPipe constructions with GenericOpenAIPipe, drop enableCaching/useConverseApi, bump context windows to 512000", "status": "pending"},
  {"id": "t04-pipelines-batch", "content": "Replace pipe type in CharacterPipeline, DialogueConnector, ChapterRewritePipeline, AdvancedWriterPipeline, PitchSlideWriterPipeline, Util/*.kt (ChapterRewrite, Advanced, Plus, RecursiveReasoning)", "status": "pending"},
  {"id": "t05-plus-writer-pipeline", "content": "Rewrite Builders/PlusWriterPipeline.kt — GenericOpenAIPipe, 512K context, drop caching", "status": "pending"},
  {"id": "t06-expansion-pipeline", "content": "Rewrite Builders/ExpansionPipeline.kt — GenericOpenAIPipe, 512K context, drop caching", "status": "pending"},
  {"id": "t07-reasoning-adapter", "content": "Create Builders/MiniMaxReasoning.kt adapter mirroring OpenRouterReasoning pattern", "status": "pending"},
  {"id": "t08-shell-batch", "content": "Update Shell/*.kt — Shell, SettingsSubshell, CharacterChat, TokenCounting, Author, Guide, Writer, Ptich — replace pipe type, drop bedrockEnv", "status": "pending"},
  {"id": "t09-settings", "content": "Rewrite Structs/WriterSettings.kt — GenericOpenAIPipe cast, 512K budget constant, model name table", "status": "pending"},
  {"id": "t10-streaming-util", "content": "Update Util/StreamingUtil.kt — replace Bedrock streaming config", "status": "pending"},
  {"id": "t11-main", "content": "Update com/example/tpipewriter/Main.kt — replace bedrockEnv with genericOpenAIEnv", "status": "pending"},
  {"id": "t12-unit-tests", "content": "Add unit tests: MiniMaxModelConfigTest, MiniMaxSettingsTest", "status": "pending"},
  {"id": "t13-smoke-test", "content": "Replace NovaTest.kt with MiniMaxSmokeTest.kt and MiniMaxStreamingTest.kt (live, gated on MINIMAX_API_KEY)", "status": "pending"},
  {"id": "t14-build-verify", "content": "Run ./gradlew build — must pass cleanly with 0 errors", "status": "pending"},
  {"id": "t15-test-suite", "content": "Run ./gradlew test — must pass cleanly, including live smoke test if MINIMAX_API_KEY is set", "status": "pending"},
  {"id": "t16-readme", "content": "Rewrite README.md following the OpenRouter branch README structure", "status": "pending"},
  {"id": "t17-tui-verify", "content": "End-to-end TUI verification via tmux — drive ./run.sh, exercise each agent, verify no crashes, verify each pipe produces output", "status": "pending"},
  {"id": "t18-commit", "content": "Final atomic commit: chore(refactor): strip Bedrock, swap to MiniMax-M3 Generic OpenAI", "status": "pending"}
]
```

---

## Task Detail

### Task 1: Dependency swap (subagent)

**Objective:** Replace `TPipe-Bedrock` with `TPipe-GenericOpenAI` in `build.gradle.kts`.

**Files:**
- Modify: `build.gradle.kts:20`

**Step 1:** Edit `build.gradle.kts` line 20:
```kotlin
implementation("com.TTT:TPipe-GenericOpenAI:1.0.0")
```
(Drop the `com.TTT:TPipe-Bedrock:1.0.0` line.)

**Step 2:** Verify with:
```bash
./gradlew dependencies --configuration runtimeClasspath | grep -E "TPipe-(Bedrock|GenericOpenAI)"
```
Expected: only `TPipe-GenericOpenAI` present, no `TPipe-Bedrock`.

**Step 3:** Commit: `chore(deps): swap TPipe-Bedrock for TPipe-GenericOpenAI`

---

### Task 2: Model config (inline — cross-file context)

**Objective:** Rewrite `Globals/ModelConfig.kt` so that all model constants resolve to `MiniMax-M3` (since this branch uses one model for everything), and `init()` is a no-op (no ARN binding, no region, no inference profile — MiniMax uses API key + base URL, not AWS infra).

**Files:**
- Modify: `src/main/kotlin/Globals/ModelConfig.kt` (entire file, ~83 lines)

**Step 1:** Write failing test `src/test/kotlin/com/example/tpipewriter/MiniMaxModelConfigTest.kt`:
```kotlin
package com.example.tpipewriter

import Globals.ModelConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MiniMaxModelConfigTest {
    @Test
    fun `deepseek model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.deepseekModelName)
    }
    @Test
    fun `claude model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.claudeModelName)
    }
    @Test
    fun `nova model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.novaModelName)
    }
    @Test
    fun `gptOss model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.gptOssModelName)
    }
    @Test
    fun `init is no-op`() {
        // No exception means no-op succeeded
        ModelConfig.init()
    }
}
```

**Step 2:** Run `./gradlew test --tests "*MiniMaxModelConfigTest*"`. Expected: 5 failures (constants still Bedrock ARNs).

**Step 3:** Rewrite `ModelConfig.kt`:
```kotlin
package Globals

object ModelConfig
{
    // Single-model edition: every pipeline uses MiniMax-M3.
    // The variable names are preserved from the Bedrock/OpenRouter branches so the
    // downstream Env.kt and pipeline files don't need their call sites renamed —
    // they keep calling ModelConfig.deepseekModelName etc. but now resolve to M3.
    const val deepseekModelName = "MiniMax-M3"
    const val claudeModelName = "MiniMax-M3"
    const val novaModelName = "MiniMax-M3"
    const val novaProModelName = "MiniMax-M3"
    const val gptOssModelName = "MiniMax-M3"
    const val gptOss120bModelName = "MiniMax-M3"
    const val llamaMaverick = "MiniMax-M3"
    const val llama70B = "MiniMax-M3"
    const val llama405B = "MiniMax-M3"
    const val jambaModelName = "MiniMax-M3"
    const val deepseekV31 = "MiniMax-M3"
    const val qwen235B = "MiniMax-M3"
    const val qwen32B = "MiniMax-M3"
    const val qwenCoder480B = "MiniMax-M3"
    const val qwenCoder30B = "MiniMax-M3"
    const val qwenNext80B = "MiniMax-M3"
    const val qwenVL = "MiniMax-M3"
    const val PalmyraX5 = "MiniMax-M3"

    fun init()
    {
        // No-op: MiniMax-M3 is a hosted model on api.minimax.io. No ARN binding, no
        // region, no inference profile. Authentication is via the MINIMAX_API_KEY
        // environment variable resolved by GenericOpenAIEnv.
    }
}
```

**Step 4:** Run `./gradlew test --tests "*MiniMaxModelConfigTest*"`. Expected: 5 passes.

**Step 5:** Commit: `refactor(model-config): collapse to single MiniMax-M3, no-op init()`

---

### Task 3: Env.kt rewrite (inline — high cross-file context)

**Objective:** Replace every `BedrockMultimodalPipe()` and `BedrockPipe()` instantiation with a `GenericOpenAIPipe()` configured for `ApiMode.OpenAIResponses` + `https://api.minimax.io/v1` + `MINIMAX_API_KEY`. Drop `enableCaching()` calls (M3 has no `setCacheControl` support on OpenAI mode). Drop `useConverseApi()` calls (Converse is Bedrock-specific). Bump `.setContextWindowSize(N)` from 106K/107K/108K to 512000.

**Files:**
- Modify: `src/main/kotlin/Globals/Env.kt` (~900 lines, surgical throughout)

**Step 1:** Identify every `BedrockMultimodalPipe()` / `BedrockPipe()` / `bedrockEnv` reference:
```bash
grep -n "BedrockMultimodalPipe\|BedrockPipe\|bedrockEnv\|useConverseApi\|enableCaching\|bindInferenceProfile\|bedrockPipe\." src/main/kotlin/Globals/Env.kt
```

**Step 2:** For each pipe construction, rewrite:
```kotlin
// OLD (Bedrock)
val writerEntryPipe = BedrockMultimodalPipe()
    .setRegion("us-east-2")
    .useConverseApi()
    .setReadTimeout(800)
    .setModel(deepSeekModelId)
    ...

// NEW (Generic OpenAI / MiniMax-M3)
val writerEntryPipe = GenericOpenAIPipe()
    .setBaseUrl("https://api.minimax.io/v1")
    .setApiKey(genericOpenAIEnv.resolveApiKey())
    .setApiMode(ApiMode.OpenAIResponses)
    .setModel(ModelConfig.deepseekModelName)
    .setReadTimeout(800)
    ...
```

**Step 3:** For every `.setContextWindowSize(N)` where N < 512000, bump to 512000:
```kotlin
.setContextWindowSize(512000)
```

**Step 4:** Replace `bedrockEnv.loadInferenceConfig()` and `bedrockEnv.bindInferenceProfile(...)` calls with a single `ModelConfig.init()` (which is now a no-op).

**Step 5:** Replace `import bedrockPipe.*` with `import genericOpenAIPipe.*` and `import genericOpenAIPipe.GenericOpenAIPipe` and `import genericOpenAIPipe.ApiMode` and `import env.genericOpenAIEnv`. Drop `import env.bedrockEnv`.

**Step 6:** Build: `./gradlew compileKotlin`. Iterate on compile errors. Do not commit until build passes.

**Step 7:** Commit: `refactor(env): replace Bedrock pipes with GenericOpenAIPipe targeting MiniMax-M3`

---

### Task 4: Pipeline batch — Character, Dialogue, ChapterRewrite, Advanced, PitchSlide, Util (subagent)

**Objective:** Replace `BedrockMultimodalPipe()` constructions in the remaining pipeline files.

**Files:** (all under `src/main/kotlin/Builders/`)
- `CharacterPipeline.kt`
- `DialogueConnector.kt`
- `ChapterRewritePipeline.kt`
- `AdvancedWriterPipeline.kt`
- `PitchSlideWriterPipeline.kt`
- `Util/ChapterRewriteUtil.kt`
- `Util/AdvancedWriterUtil.kt`
- `Util/PlusWriterUtil.kt`
- `Util/RecursiveReasoning.kt`

**Step 1:** For each file, do the same rewrite as Task 3:
- Replace `BedrockMultimodalPipe()` / `BedrockPipe()` with `GenericOpenAIPipe()`
- Add `.setBaseUrl("https://api.minimax.io/v1")`, `.setApiKey(...)`, `.setApiMode(ApiMode.OpenAIResponses)`
- Drop `useConverseApi()`, drop `enableCaching()` (unless inside `ApiMode.Anthropic` context, which we are not using)
- Bump context windows < 512K to 512000
- Update imports: drop `bedrockPipe.*`, add `genericOpenAIPipe.*`

**Step 2:** Build: `./gradlew compileKotlin`. Iterate until clean.

**Step 3:** Run existing unit tests: `./gradlew test --tests "*Util*Test*"`. Expected: pass (PlusWriterUtilTest already has surgical-improvement tests that should still pass).

**Step 4:** Commit: `refactor(pipelines): strip Bedrock from all Builders/* pipelines`

---

### Task 5: PlusWriterPipeline.kt (inline — surgical complexity)

**Objective:** Replace `BedrockMultimodalPipe` constructions in `PlusWriterPipeline.kt` (717 lines, surgical improvements already applied — preserve them all).

**Files:**
- Modify: `src/main/kotlin/Builders/PlusWriterPipeline.kt` (entire file)

**Step 1:** Identify pipe constructions:
```bash
grep -n "BedrockMultimodalPipe\|BedrockPipe\|useConverseApi\|enableCaching\|setModel" src/main/kotlin/Builders/PlusWriterPipeline.kt
```

**Step 2:** Apply the same surgical rewrite as Task 3. Preserve every `applySurgicalReplacementsAndBank` call, every `SurgicalChangeList` check, every `preInvokeLoreRepairPipe` — these are surgical-mode improvements from the recent 8 commits and must remain intact.

**Step 3:** Build: `./gradlew compileKotlin`. Run `./gradlew test --tests "*PlusWriter*"`. Expected: all pass.

**Step 4:** Commit: `refactor(plus-writer): strip Bedrock, swap to GenericOpenAIPipe`

---

### Task 6: ExpansionPipeline.kt (inline — surgical complexity)

**Objective:** Same as Task 5 but for `ExpansionPipeline.kt`.

**Files:**
- Modify: `src/main/kotlin/Builders/ExpansionPipeline.kt`

**Step 1:** Apply the same rewrite. Preserve the `ContextBank` import that was added in the OpenRouter merge commit.

**Step 2:** Build + run `PlusWriterUtilTest` to verify integration.

**Step 3:** Commit: `refactor(expansion): strip Bedrock, swap to GenericOpenAIPipe`

---

### Task 7: Reasoning adapter (subagent — isolated)

**Objective:** Create `Builders/MiniMaxReasoning.kt` mirroring the `OpenRouterReasoning.kt` pattern. Since `MiniMax-M3` is a no-reasoning model, this adapter exists for shape consistency but does NOT set reasoning on the pipe.

**Files:**
- Create: `src/main/kotlin/Builders/MiniMaxReasoning.kt`

**Step 1:** Write the adapter:
```kotlin
package Builders

import Defaults.GenericOpenAIConfiguration
import Defaults.reasoning.ReasoningBuilder
import Defaults.reasoning.ReasoningSettings
import com.TTT.Pipe.Pipe
import com.TTT.Structs.PipeSettings
import env.GenericOpenAIEnv
import genericOpenAIPipe.GenericOpenAIPipe

/**
 * MiniMax-M3 reasoning adapter. Mirrors the OpenRouterReasoning pattern but, since
 * MiniMax-M3 is the no-reasoning model variant, we explicitly DISABLE reasoning on
 * the pipe regardless of what ReasoningSettings.request was passed. This keeps the
 * Pipeline construction sites uniform across providers — they call reasonWithMiniMax
 * the same way they'd call reasonWithOpenRouter — without accidentally emitting a
 * `reasoning` block in the wire payload that M3 would ignore.
 */
fun reasonWithMiniMax(
    config: GenericOpenAIConfiguration,
    reasoningSettings: ReasoningSettings,
    pipeSettings: PipeSettings?
): Pipe {
    val pipe = GenericOpenAIPipe()
    pipe.setModel(config.model)
    pipe.setApiKey(config.apiKey.ifBlank { GenericOpenAIEnv.resolveApiKey() })
    pipe.setBaseUrl(config.baseUrl.ifBlank { GenericOpenAIEnv.resolveBaseUrl() })
    // MiniMax-M3 is no-reasoning. Force-disable both knobs regardless of caller intent.
    pipe.disableReasoning()
    // ReasoningBuilder.assignDefaults is still called so future migrations to M2.7+ are
    // a one-line change (delete the disableReasoning() above).
    ReasoningBuilder.assignDefaults(reasoningSettings, pipeSettings, pipe)
    return pipe
}

fun reasonWithMiniMax(
    model: String,
    reasoningSettings: ReasoningSettings,
    pipeSettings: PipeSettings?
): Pipe = reasonWithMiniMax(
    GenericOpenAIConfiguration(
        model = model,
        apiKey = GenericOpenAIEnv.resolveApiKey(),
        baseUrl = GenericOpenAIEnv.resolveBaseUrl()
    ),
    reasoningSettings,
    pipeSettings
)
```

**Step 2:** Commit: `feat(minimax-reasoning): add MiniMax-M3 reasoning adapter`

---

### Task 8: Shell batch (inline — shared state with Env.kt)

**Objective:** Update all `Shell/*.kt` files that reference `bedrockEnv` or build pipes.

**Files:**
- `src/main/kotlin/Shell/Shell.kt`
- `src/main/kotlin/Shell/SettingsSubshell.kt`
- `src/main/kotlin/Shell/CharacterChatSubshell.kt`
- `src/main/kotlin/Shell/TokenCountingSubshell.kt`
- `src/main/kotlin/Shell/AuthorSubshell.kt`
- `src/main/kotlin/Shell/GuideSubshell.kt`
- `src/main/kotlin/Shell/WriterSubshell.kt`
- `src/main/kotlin/Shell/PtichSubshell.kt`

**Step 1:** For each file, replace:
- `bedrockEnv.*` → `genericOpenAIEnv.*`
- `BedrockMultimodalPipe()` → `GenericOpenAIPipe()` (with the 3-call setter preamble)
- `import bedrockPipe.*` → `import genericOpenAIPipe.*`
- `import env.bedrockEnv` → `import env.genericOpenAIEnv`

**Step 2:** Build: `./gradlew compileKotlin`. Iterate until clean.

**Step 3:** Commit: `refactor(shell): strip Bedrock references from Shell/*`

---

### Task 9: WriterSettings.kt (inline — settings persistence)

**Objective:** Update `WriterSettings.kt` so the persisted settings model uses `GenericOpenAIPipe` and includes the 512K budget constant.

**Files:**
- Modify: `src/main/kotlin/Structs/WriterSettings.kt`

**Step 1:** Identify the cast and model name table:
```bash
grep -n "BedrockPipe\|OpenRouterPipe\|deepseek\|claude\|nova\|setRegion" src/main/kotlin/Structs/WriterSettings.kt
```

**Step 2:** Replace `BedrockPipe` cast (or `OpenRouterPipe` cast, if any was merged from the OpenRouter branch) with `GenericOpenAIPipe` cast. Update the model name table so all known models resolve to `MiniMax-M3`.

**Step 3:** Add a 512K budget constant:
```kotlin
const val MiniMaxContextWindowSize: Int = 512000
```

**Step 4:** Write failing test `MiniMaxSettingsTest` (covered in Task 12).

**Step 5:** Commit: `refactor(settings): persist GenericOpenAIPipe + 512K budget`

---

### Task 10: StreamingUtil.kt (subagent — isolated)

**Objective:** Replace Bedrock-specific streaming config in `Util/StreamingUtil.kt`.

**Files:**
- Modify: `src/main/kotlin/Util/StreamingUtil.kt`

**Step 1:** Identify Bedrock references and replace with GenericOpenAIPipe equivalents. Use the streaming callback pattern documented in the `tpipe-generic-openai` skill (suspend function type, set on the typed `GenericOpenAIPipe` reference not the parent `Pipe`).

**Step 2:** Build: `./gradlew compileKotlin`. Commit.

---

### Task 11: Main.kt (inline — startup path)

**Objective:** Update `Main.kt` to replace `bedrockEnv` with `genericOpenAIEnv`.

**Files:**
- Modify: `src/main/kotlin/com/example/tpipewriter/Main.kt`

**Step 1:** Replace `bedrockEnv` references, update the AWS Bedrock notice to a MiniMax notice. Drop any AWS-region selection code. Add a startup check: if `MINIMAX_API_KEY` is not set, print a warning with setup instructions.

**Step 2:** Build. Commit.

---

### Task 12: Unit tests (subagent)

**Objective:** Add unit tests for the new shape.

**Files:**
- Create: `src/test/kotlin/com/example/tpipewriter/MiniMaxModelConfigTest.kt` (already drafted in Task 2)
- Create: `src/test/kotlin/com/example/tpipewriter/MiniMaxSettingsTest.kt`

**Step 1:** `MiniMaxSettingsTest`:
```kotlin
package com.example.tpipewriter

import Structs.WriterSettings
import Structs.MiniMaxContextWindowSize
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniMaxSettingsTest {
    @Test
    fun `512K budget constant is exactly 512000`() {
        assertEquals(512000, MiniMaxContextWindowSize)
    }
    @Test
    fun `default settings round-trip through serialization preserves pipe type`() {
        val settings = WriterSettings()
        // ... assert that the persisted settings shape uses GenericOpenAIPipe
        // and not BedrockPipe or OpenRouterPipe
    }
}
```

**Step 2:** Run: `./gradlew test --tests "*MiniMax*"`. Expected: all pass.

**Step 3:** Commit.

---

### Task 13: Live smoke + streaming tests (subagent)

**Objective:** Replace `NovaTest.kt` (Bedrock live test) with `MiniMaxSmokeTest.kt` and `MiniMaxStreamingTest.kt`. Both gated on `MINIMAX_API_KEY` env var via `assumeTrue`.

**Files:**
- Delete: `src/test/kotlin/com/example/tpipewriter/NovaTest.kt`
- Create: `src/test/kotlin/com/example/tpipewriter/MiniMaxSmokeTest.kt`
- Create: `src/test/kotlin/com/example/tpipewriter/MiniMaxStreamingTest.kt`

**Step 1:** `MiniMaxSmokeTest.kt`:
```kotlin
package com.example.tpipewriter

import genericOpenAIPipe.ApiMode
import genericOpenAIPipe.GenericOpenAIPipe
import genericOpenAIPipe.GenericOpenAIEnv
import com.TTT.Pipe.MultimodalContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MiniMaxSmokeTest {
    @Test
    fun testMiniMaxConnection() {
        assumeTrue(
            System.getenv("MINIMAX_API_KEY")?.isNotBlank() == true,
            "MINIMAX_API_KEY not set; skipping smoke test"
        )

        val input = MultimodalContent().apply { text = "Hello" }
        val pipe: GenericOpenAIPipe = GenericOpenAIPipe()
        pipe.setApiKey(GenericOpenAIEnv.resolveApiKey())
           .setBaseUrl(GenericOpenAIEnv.resolveBaseUrl())
           .setApiMode(ApiMode.OpenAIResponses)
           .setModel("MiniMax-M3")

        runBlocking {
            pipe.init()
            val out = pipe.execute(input)
            println("MiniMax-M3 response: ${out.text}")
            assertTrue(out.text.isNotBlank(), "MiniMax-M3 returned blank response")
        }
    }
}
```

**Step 2:** `MiniMaxStreamingTest.kt`:
```kotlin
package com.example.tpipewriter

import genericOpenAIPipe.ApiMode
import genericOpenAIPipe.GenericOpenAIPipe
import genericOpenAIPipe.GenericOpenAIEnv
import com.TTT.Pipe.MultimodalContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MiniMaxStreamingTest {
    @Test
    fun testMiniMaxStreamingChunkOrdering() {
        assumeTrue(
            System.getenv("MINIMAX_API_KEY")?.isNotBlank() == true,
            "MINIMAX_API_KEY not set; skipping streaming test"
        )

        val chunks = mutableListOf<String>()
        val callback: suspend (String) -> Unit = { chunk ->
            println("STREAM_CHUNK: [$chunk]")
            chunks.add(chunk)
        }

        val pipe: GenericOpenAIPipe = GenericOpenAIPipe()
        pipe.setApiKey(GenericOpenAIEnv.resolveApiKey())
           .setBaseUrl(GenericOpenAIEnv.resolveBaseUrl())
           .setApiMode(ApiMode.OpenAIResponses)
           .setModel("MiniMax-M3")
           .setMaxTokens(256)
           .setTemperature(0.0)

        pipe.setStreamingCallback(callback)

        runBlocking {
            pipe.init()
            pipe.execute(MultimodalContent().apply { text = "Say hello in 5 words." })
        }

        assertTrue(chunks.isNotEmpty(), "Should have received at least one streaming chunk")
        val assembled = chunks.joinToString("")
        println("ASSEMBLED: [$assembled]")
        assertTrue(assembled.isNotBlank(), "Assembled response should not be blank")
    }
}
```

**Step 3:** Delete `NovaTest.kt`:
```bash
git rm src/test/kotlin/com/example/tpipewriter/NovaTest.kt
```

**Step 4:** Run: `./gradlew test --tests "*MiniMax*"`. Expected: both pass when `MINIMAX_API_KEY` is set, both skip cleanly when not.

**Step 5:** Commit: `test(minimax): add live smoke + streaming tests for MiniMax-M3, drop NovaTest`

---

### Task 14: Full build verification (inline — final gate)

**Objective:** Verify the entire project compiles cleanly.

**Files:** none

**Step 1:** Run:
```bash
./gradlew clean build
```

**Step 2:** Expected: BUILD SUCCESSFUL, 0 compile errors. Iterate on any compile errors before proceeding.

---

### Task 15: Full test suite (inline — final gate)

**Objective:** Verify all tests pass, including live tests.

**Files:** none

**Step 1:** Run:
```bash
./gradlew test
```

**Step 2:** Expected: all tests pass (or skip cleanly if `MINIMAX_API_KEY` not set). The smoke test should produce a real `MiniMax-M3 response: <text>` line in stdout.

**Step 3:** If any test fails, debug root cause, fix, re-run.

---

### Task 16: README rewrite (subagent — isolated)

**Objective:** Rewrite `README.md` following the OpenRouter branch README structure.

**Files:**
- Modify: `README.md` (entire file)

**Step 1:** Mirror the OpenRouter README structure:
```markdown
# TPipeWriter — MiniMax-M3 Generic OpenAI Edition

**⚠️ NOTICE:** MiniMax-M3 is required. Set `MINIMAX_API_KEY` before running.

## Setup
1. Ensure TPipe is built: `cd ../TPipe/TPipe && ./gradlew shadowJar`
2. Get a MiniMax API key from https://platform.minimax.io
3. `export MINIMAX_API_KEY="..."`

## Build and Run
```bash
./gradlew build
./run.sh
```

## Architecture
This edition swaps AWS Bedrock for GenericOpenAIPipe targeting the MiniMax
OpenAI Responses API. All pipes use model `MiniMax-M3` with a 512K context
window. ...

## Variant Editions
- `main`: original AWS Bedrock edition
- `OpenRouter`: OpenRouter edition
- `GenericAI` (this branch): MiniMax-M3 Generic OpenAI edition
```

**Step 2:** Commit: `docs: rewrite README for MiniMax-M3 Generic OpenAI edition`

---

### Task 17: End-to-end TUI verification via tmux (inline — final user-facing gate)

**Objective:** Drive the TUI via tmux, exercise every agent (writer, idea, character, lorebook, summarizer, style, ncc, rewrite, expansion, plus-writer, pitch-slide, character-chat, author, guide, settings, token-counting), verify each pipe produces output, no crashes.

**Files:** none (this is verification only)

**Step 1:** Start tmux session:
```bash
tmux new-session -d -s tpipe-verify -x 200 -y 50
tmux send-keys -t tpipe-verify './run.sh' Enter
sleep 5  # let startup complete
tmux capture-pane -t tpipe-verify -p | head -50  # verify prompt shows
```

**Step 2:** For each subshell command, send the command and capture the output:
```bash
for cmd in "/writer" "/idea" "/character" "/lorebook" "/summarize" "/style" "/ncc" "/rewrite" "/expand" "/plus-writer" "/pitch-slide" "/chat" "/author" "/guide" "/settings" "/token-count"; do
  tmux send-keys -t tpipe-verify "$cmd" Enter
  sleep 3
  echo "=== $cmd ==="
  tmux capture-pane -t tpipe-verify -p | tail -20
done
```

**Step 3:** For at least one subshell, send a real prompt and verify output is generated:
```bash
tmux send-keys -t tpipe-verify "/writer" Enter
sleep 2
tmux send-keys -t tpipe-verify "Write a single sentence: 'The sky is blue.'" Enter
sleep 15  # let streaming complete
tmux capture-pane -t tpipe-verify -p | tail -40
# Expected: a coherent sentence, not an error, not a blank line
```

**Step 4:** Verify no crashes in any captured pane. If any subshell crashes or returns an error, debug root cause and fix.

**Step 5:** Kill tmux session:
```bash
tmux kill-session -t tpipe-verify
```

**Step 6:** Document findings in commit message (Task 18).

---

### Task 18: Final atomic commit (inline — wrapping up)

**Objective:** Single atomic commit summarizing the refactor, or amend the previous commit if everything is already in clean history.

**Files:** none

**Step 1:** Verify clean tree:
```bash
git status
```

**Step 2:** If anything unstaged, commit with:
```bash
git add -A
git commit -m "chore(refactor): strip Bedrock from TPipeWriter, swap to MiniMax-M3 Generic OpenAI

- TPipe-Bedrock dependency removed; TPipe-GenericOpenAI added
- All BedrockMultimodalPipe/BedrockPipe constructions replaced with GenericOpenAIPipe
- ApiMode.OpenAIResponses, baseUrl=https://api.minimax.io/v1, model=MiniMax-M3
- Context windows bumped to 512000 across all pipes
- enableCaching/useConverseApi calls dropped (M3 has no Anthropic endpoint support, no cache_control on OpenAIResponses mode)
- bedrockEnv replaced with genericOpenAIEnv
- ModelConfig.init() is now a no-op (no ARN binding, no region, no inference profile)
- MiniMaxReasoning adapter added (forces disableReasoning on M3 — no-reasoning model)
- NovaTest.kt deleted; MiniMaxSmokeTest + MiniMaxStreamingTest added (live tests gated on MINIMAX_API_KEY)
- README rewritten

Pattern matches OpenRouter branch (commit 10978206). Surgical improvements
(applySurgicalReplacementsAndBank, SurgicalChanges.mode, secondPassTransform,
preInvokeLoreRepairPipe) preserved across all pipelines."
```

---

## Verification (end-to-end)

After all 18 tasks complete:

1. `./gradlew clean build` returns BUILD SUCCESSFUL with 0 errors.
2. `./gradlew test` returns all tests passing (live tests pass when `MINIMAX_API_KEY` is set, skip cleanly when not).
3. `git log --oneline main..GenericAI` shows a clean linear history with one or two surgical commits (matching the OpenRouter branch pattern: one major refactor commit, possibly one or two follow-up commits for surgical fixes).
4. `grep -rn "bedrockEnv\|BedrockMultimodalPipe\|BedrockPipe\|useConverseApi\|bindInferenceProfile\|com\\.TTT:TPipe-Bedrock" src/` returns ZERO matches.
5. `grep -rn "MiniMax-M3\|GenericOpenAIPipe\|api.minimax.io" src/` returns matches in every pipe file (proves the refactor was complete).
6. `./run.sh` boots the TUI without crashing. Each subshell command (`/writer`, `/idea`, `/character`, etc.) is reachable and produces output. At least one end-to-end prompt (e.g., `/writer` → real prompt → response) returns a coherent non-blank response.
7. The trace metadata shows `reasoningEnabled=false` everywhere (M3 is no-reasoning).
8. The trace metadata shows `apiType=ResponsesAPI` (proves we're hitting the OpenAI Responses endpoint, not chat-completions).

---

## Risks and Tradeoffs

- **Loss of Bedrock-specific features.** `enableCaching()` / `useConverseApi()` are Bedrock-only. M3 does not have caching on OpenAIResponses mode, so we lose prompt caching. This is acceptable because M3 has 512K context — the user is unlikely to need caching.
- **Loss of reasoning.** All M2.x reasoning capabilities are unavailable on M3. This is intentional: M3 is the no-reasoning variant, chosen because it's the host's newest model. If the user later wants reasoning, they swap M3 → M2.7 in `ModelConfig.kt` and the wire payload changes are minimal.
- **Single-model edition.** All pipes use the same model. This is a deliberate simplification of the OpenRouter pattern (which kept multi-model flexibility). If the user later wants per-pipe models, they introduce a second `ModelConfig.miniMaxM27ModelName` constant and pipe-specific overrides — but YAGNI for now.
- **Composite build coupling.** TPipe must be rebuilt before TPipeWriter builds. The README will note this. Mitigation: `./run.sh` already builds TPipe first.
- **Live tests skip in CI.** When `MINIMAX_API_KEY` is not set, the live tests skip via `assumeTrue`. This is identical to the OpenRouterSmokeTest pattern — CI without secrets still builds green.

## Open Questions (none blocking)

- Should the rationale paragraph at the top of the README be longer? (Yes if the user wants marketing copy; no if they want it terse. Default: terse.)
- Should we keep the `OpenRouter` and `main` branches listed in the README's "Variant Editions" section? (Yes — the README is the user-facing doc; the branch list is for discoverability.)
- Should the `MiniMaxContextWindowSize` constant be configurable via env var? (YAGNI — keep it as a const. If the user later wants runtime configurability, that's a separate task.)

## Reference Material

- `tpipe-generic-openai` skill — full GenericOpenAIPipe architecture, SSE formats, MiniMax provider quirks, canonical live-test patterns
- `references/minimax-model-references.md` — MiniMax-M3 model properties (no reasoning, no Anthropic endpoint, 512K context, passive auto-cache)
- OpenRouter branch commit `10978206fa2b2eccdf633983a1eeb0e5ae527b86` — surgical pattern this refactor mirrors
- `docs/` directory — original Bedrock docs (read for context, do not copy)