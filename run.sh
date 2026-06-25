#!/bin/bash
# Start TPipeWriter (MiniMax-M3 Generic OpenAI edition).
#
# API key resolution order:
#   1. MINIMAX_API_KEY (the canonical name for MiniMax-M3)
#   2. AUXILIARY_VISION_API_KEY (dev fallback when MINIMAX_API_KEY is unset)
#
# On startup we print a single "API key: OK (sk-...xxx, N chars)" line so the
# user can verify at a glance that the key wired in is the one they expect.

if [ -z "$MINIMAX_API_KEY" ]; then
    if [ -z "$AUXILIARY_VISION_API_KEY" ]; then
        echo "============================================="
        echo "ERROR: No MiniMax API key configured"
        echo "============================================="
        echo "Set one of:"
        echo "  export MINIMAX_API_KEY=\"sk-...\"             # canonical"
        echo "  export AUXILIARY_VISION_API_KEY=\"sk-...\"   # dev fallback"
        echo ""
        echo "Get a key at https://platform.minimax.io"
        exit 1
    else
        echo "[run.sh] MINIMAX_API_KEY not set; using AUXILIARY_VISION_API_KEY fallback"
        export MINIMAX_API_KEY="$AUXILIARY_VISION_API_KEY"
    fi
fi

# Show key status (mask all but last 4 chars). We don't print the full key
# to avoid leaking it to logs/tmux scrollback.
KEY_LEN=${#MINIMAX_API_KEY}
KEY_LAST4="${MINIMAX_API_KEY: -4}"
echo "[run.sh] API key: OK (sk-...${KEY_LAST4}, ${KEY_LEN} chars)"

# Find and execute TPipeWriter jar (prefer shadow jar)
JAR_FILE=$(find . -name "*-all.jar" -type f | head -1)
if [ -z "$JAR_FILE" ]; then
    JAR_FILE=$(find . -name "TPipeWriter*.jar" -type f | head -1)
fi

if [ -z "$JAR_FILE" ]; then
    echo "[run.sh] TPipeWriter jar not found — run ./gradlew shadowJar first"
    exit 1
fi

echo "[run.sh] Starting TPipeWriter (MiniMax-M3 Generic OpenAI edition)..."
echo "[run.sh] Trace directory: ~/.TPipe-Debug/traces/ (auto-exported pipelines)"
echo "[run.sh] Per-command trace: ~/TPipeWriter/Trace.html (chat/lorebook/writer)"
java -jar "$JAR_FILE"