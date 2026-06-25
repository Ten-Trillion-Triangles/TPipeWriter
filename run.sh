#!/bin/bash

# Find MiniMax API key from environment, or fall back to a check
if [ -z "$MINIMAX_API_KEY" ]; then
    if [ -z "$AUXILIARY_VISION_API_KEY" ]; then
        echo "MINIMAX_API_KEY not set and AUXILIARY_VISION_API_KEY fallback absent"
        echo "Get a key at https://platform.minimax.io and:"
        echo "  export MINIMAX_API_KEY=\"sk-...\""
        exit 1
    else
        echo "MINIMAX_API_KEY not set; using AUXILIARY_VISION_API_KEY as fallback"
        export MINIMAX_API_KEY="$AUXILIARY_VISION_API_KEY"
    fi
fi

# Find and execute TPipeWriter jar (prefer shadow jar)
JAR_FILE=$(find . -name "*-all.jar" -type f | head -1)
if [ -z "$JAR_FILE" ]; then
    JAR_FILE=$(find . -name "TPipeWriter*.jar" -type f | head -1)
fi

if [ -z "$JAR_FILE" ]; then
    echo "TPipeWriter jar not found — run ./gradlew shadowJar first"
    exit 1
fi

echo "Starting TPipeWriter (MiniMax-M3 Generic OpenAI edition)..."
java -jar "$JAR_FILE"