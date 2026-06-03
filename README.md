# TPipeWriter

**TPipeWriter requires an OpenRouter API key to run.**

TPipeWriter is a Kotlin/JVM demo application for the [TPipe](https://github.com/cage/TPipe) framework — a structured-pipeline library for orchestrating LLM-driven creative-writing workflows. This branch is the OpenRouter-backed edition: every pipe goes through [OpenRouter](https://openrouter.ai) instead of Amazon Bedrock, so any developer with an OpenRouter key can clone, build, and run the demo.

## Setup

This project depends on the TPipe library at `../TPipe/TPipe/`. Build it first:

```bash
cd ../TPipe/TPipe
./gradlew shadowJar
```

### OpenRouter API Key Configuration

TPipeWriter uses OpenRouter for inference. Get a key at [https://openrouter.ai](https://openrouter.ai) and set:

```bash
export OPENROUTER_API_KEY="sk-or-..."
```

The application is registered to OpenRouter with the title `TPipeWriter` and the referer `https://github.com/cage/TPipeWriter` for usage analytics. No other configuration is required — OpenRouter aggregates 300+ models from Anthropic, OpenAI, Google, DeepSeek, Meta, Qwen, and more behind a single API key.

## Build and Run

```bash
./gradlew build
./gradlew run
# or
./run.sh
```

The macOS Finder double-clickable launcher at `run.command` does the same with one click (it also runs `./gradlew installDist` first).

## Model Selection

All models are routed through OpenRouter. See `src/main/kotlin/Globals/ModelConfig.kt` for the canonical list. You can swap models at runtime via the in-shell `/settings` command.

### Model Substitutions

Three Bedrock models didn't have direct OpenRouter equivalents and were substituted:

| Original Bedrock model | OpenRouter replacement | Why |
|---|---|---|
| `qwen.qwen3-coder-480b-a35b-v1:0` | `qwen/qwen3-235b-a22b-2507` | The "Coder" name was misleading — this model was used for writing and reasoning (theme analysis, body-text expansion, chain-of-thought planning), never code. The 235B is the same Qwen3 MoE family, slightly smaller scale, and a 1:1 OpenRouter match. |
| `us.meta.llama3-1-405b-instruct-v1:0` | `nousresearch/hermes-3-llama-3.1-405b` | The only 405B on OpenRouter is the NousResearch mirror (same base model, third-party fine-tune). |
| `ai21.jamba-1-5-large-v1:0` | `ai21/jamba-large-1.7` | AI21 no longer hosts Jamba 1.5 on OpenRouter; 1.7 is the new flagship (same vendor, same hybrid Mamba/Transformer family). |

The remaining 12 model IDs (DeepSeek R1, Claude Sonnet 4, Amazon Nova Lite/Pro, OpenAI gpt-oss 20B/120B, Llama 4 Maverick, Llama 3.3 70B, DeepSeek V3.1, Qwen 235B, Qwen 32B, Qwen Coder 30B, Writer Palmyra X5) map 1:1 to their OpenRouter equivalents.

## Dependencies

- TPipe core (`com.TTT:TPipe:1.0.0`) — composite build from `../TPipe/TPipe/`
- TPipe-OpenRouter (`com.TTT:TPipe-OpenRouter:1.0.0`) — the OpenRouter pipe implementations
- TPipe-Defaults (`com.TTT:TPipe-Defaults:1.0.0`) — reasoning pipeline defaults
- Kotlin 2.2 / JVM 24
- kotlinx-serialization, kotlinx-coroutines

## Development

The main class is at `src/main/kotlin/com/example/tpipewriter/Main.kt`. The application is an interactive shell — run it and type `/help` to see available commands.

## License

See LICENSE.
