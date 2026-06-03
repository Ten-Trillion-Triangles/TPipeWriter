#!/bin/bash

# TPipeWriter (OpenRouter edition) launcher for macOS Finder double-click.
if [ -z "$OPENROUTER_API_KEY" ]; then
    echo "OPENROUTER_API_KEY is not set. Get a key at https://openrouter.ai and:"
    echo "  export OPENROUTER_API_KEY=sk-or-..."
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building TPipeWriter..."
./gradlew installDist

echo "Starting TPipeWriter (OpenRouter)..."
export JAVA_HOME=$(/usr/libexec/java_home -v 24)
exec "./build/install/TPipeWriter/bin/TPipeWriter"
