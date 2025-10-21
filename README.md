# TPipeWriter

A Kotlin JVM console application that uses TPipe as a library dependency.

## Setup

This project depends on the TPipe library located at `../TPipe/TPipe/`. Make sure TPipe is built before running this project:

```bash
cd ../TPipe/TPipe
./gradlew shadowJar
```

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
