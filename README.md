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
