# TPipeWriter

**⚠️ NOTICE: AWS Bedrock is required to run this application.**
You must have an AWS account with Bedrock access and an API key configured before running TPipeWriter.

## Setup

This project depends on the TPipe library located at `../TPipe/TPipe/`. Make sure TPipe is built before running this project:

```bash
cd ../TPipe/TPipe
./gradlew shadowJar
```

### AWS Bedrock API Key Configuration

TPipeWriter uses AWS Bedrock for inference. Set your AWS credentials via environment variables:

```bash
export AWS_ACCESS_KEY_ID="your-access-key-id"
export AWS_SECRET_ACCESS_KEY="your-secret-access-key"
export AWS_REGION="us-west-2"  # or your preferred region
```

Alternatively, you can use AWS profile credentials (`~/.aws/credentials`) or any other method supported by the AWS SDK.

## Build and Run

```bash
./gradlew build
./gradlew run
```

## Dependencies

- TPipe library (via shadow jar)
- Kotlin Serialization
- Kotlin Coroutines
- JVM 24 (same as TPipe)

## Development

The main class is located at `src/main/kotlin/com/example/tpipewriter/Main.kt` and is ready for you to build out your TPipe-powered application.

## Variant Editions

This `main` branch is the original **Amazon Bedrock** edition. A parallel port to **OpenRouter** lives on a separate branch for anyone who doesn't have (or doesn't want to set up) an AWS account:

- **OpenRouter edition:** [`OpenRouter` branch](https://github.com/Ten-Trillion-Triangles/TPipeWriter/tree/OpenRouter)

The OpenRouter edition swaps every `BedrockMultimodalPipe` for `OpenRouterPipe` (from `com.TTT:TPipe-OpenRouter`), retargets all model IDs to OpenRouter's `vendor/model-name` format, and replaces the AWS credential setup with a single `OPENROUTER_API_KEY` env var. All the same pipelines, shells, and features work — you just need an OpenRouter key from [https://openrouter.ai](https://openrouter.ai) instead of an AWS Bedrock account.

To use it:

```bash
git checkout OpenRouter
cd ../TPipe/TPipe && ./gradlew shadowJar && cd -
export OPENROUTER_API_KEY="sk-or-..."
./run.sh
```

The 1:1 model mappings are documented at the top of `Globals/ModelConfig.kt` on that branch. A small number of Bedrock models (the Qwen 480B coder, Llama 3.1 405B, and Jamba 1.5 Large) don't have direct OpenRouter equivalents and are substituted — see the README on the `OpenRouter` branch for the full substitution table.
