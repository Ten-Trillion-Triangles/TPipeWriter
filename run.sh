#!/bin/bash

# TPipeWriter (OpenRouter edition) launcher
if [ -z "$OPENROUTER_API_KEY" ]; then
    echo "OPENROUTER_API_KEY is not set. Get a key at https://openrouter.ai and:"
    echo "  export OPENROUTER_API_KEY=sk-or-..."
    exit 1
fi

# Find and execute the TPipeWriter jar (prefer the shadow jar)
JAR_FILE=$(find . -name "*-all.jar" -type f | head -1)
if [ -z "$JAR_FILE" ]; then
    JAR_FILE=$(find . -name "TPipeWriter*.jar" -type f | head -1)
fi

if [ -z "$JAR_FILE" ]; then
    echo "TPipeWriter jar not found. Build it with: ./gradlew shadowJar"
    exit 1
fi

echo "Starting TPipeWriter (OpenRouter)..."
exec java -jar "$JAR_FILE"
