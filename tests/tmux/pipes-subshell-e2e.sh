#!/bin/bash
# TMUX E2E harness for the /pipes subshell + /save / /export / /load sidecar.
#
# This script proves end-to-end behavior for the user's directive:
# "Must load when a story is loaded, and save when a story is saved to disk."
#
# Phase 1: /pipes subshell — toggle, save, load (already covered)
# Phase 2: /save + /exportStory write the sidecar (NEW)
# Phase 3: /loadStory reads the sidecar and applies it to the live pipeline (NEW)
#
# Captures the full transcript to /tmp/pipes-subshell-e2e.log
# Exits 0 on success, non-zero on any failure.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_FILE="/tmp/pipes-subshell-e2e.log"
SIDECAR_DIR="$HOME/TPipeWriter"
SIDECAR_FILE="$SIDECAR_DIR/test-pipes-e2e-pipes.json"
SESSION_NAME="tpipewriter-pipes-e2e"
TMUX_SOCKET="tpipewriter-e2e-test-$$"

# Ensure API key is set
if [ -z "$MINIMAX_API_KEY" ]; then
    if [ -z "$AUXILIARY_VISION_API_KEY" ]; then
        echo "ERROR: No API key configured. Set MINIMAX_API_KEY or AUXILIARY_VISION_API_KEY"
        exit 1
    fi
    export MINIMAX_API_KEY="$AUXILIARY_VISION_API_KEY"
fi

# Clean up any prior test artifacts
rm -f "$LOG_FILE"
rm -f "$SIDECAR_FILE"
rm -f "$HOME/.TPipeWriter/Pipes.json"
rm -f "$HOME/.TPipeWriter/MainStory.json"
rm -f "$HOME/.TPipeWriter/Summary.json"
rm -f "$HOME/.TPipeWriter/Chat.json"

# Use a private tmux socket so we don't collide with the user's tmux session
TMUX="tmux -S /tmp/$TMUX_SOCKET"

# Kill any prior session
$TMUX kill-server 2>/dev/null || true
rm -f "/tmp/$TMUX_SOCKET"

cleanup() {
    $TMUX kill-server 2>/dev/null || true
    rm -f "/tmp/$TMUX_SOCKET"
    rm -f "$SIDECAR_FILE"
    rm -f "$HOME/.TPipeWriter/Pipes.json"
}

trap cleanup EXIT

# Helper: capture pane contents
capture() {
    $TMUX capture-pane -t "$SESSION_NAME" -p > "$LOG_FILE"
}

# Helper: wait for prompt pattern
wait_for() {
    local pattern="$1"
    local attempts=0
    while [ $attempts -lt 30 ]; do
        sleep 1
        capture
        if grep -q "$pattern" "$LOG_FILE"; then
            return 0
        fi
        attempts=$((attempts + 1))
    done
    return 1
}

# Start a new detached session running TPipeWriter
echo "=== Starting TPipeWriter in tmux session $SESSION_NAME ==="
$TMUX new-session -d -s "$SESSION_NAME" -x 200 -y 50 "cd $PROJECT_DIR && ./run.sh 2>&1; echo 'EXIT_CODE:' \$?"

# Wait for the app to print the prompt
echo "=== Waiting for app to start ==="
if ! wait_for "TPipeWriter Interactive Shell"; then
    echo "FAIL: TPipeWriter did not start within 30 seconds"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: App started"

#######################
# PHASE 1: /pipes subshell
#######################
echo ""
echo "=== PHASE 1: /pipes subshell ==="

$TMUX send-keys -t "$SESSION_NAME" "/pipes" Enter
sleep 2
capture

if ! grep -q "Pipe Disable State" "$LOG_FILE"; then
    echo "FAIL: /pipes subshell did not open"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: /pipes subshell opened"

PIPE_COUNT=$(grep -cE "^  [0-9]+\. " "$LOG_FILE")
if [ "$PIPE_COUNT" -lt 10 ]; then
    echo "FAIL: Expected at least 10 pipes, got $PIPE_COUNT"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: Menu lists $PIPE_COUNT pipes"

# Toggle pipe #1
$TMUX send-keys -t "$SESSION_NAME" "1" Enter
sleep 1
capture
FIRST_LINE=$(grep -E "^  1\. " "$LOG_FILE" | head -1)
if ! echo "$FIRST_LINE" | grep -q "DISABLED"; then
    echo "FAIL: After toggling #1, the first pipe should be DISABLED"
    echo "First line: $FIRST_LINE"
    exit 1
fi
echo "OK: Pipe #1 toggled to DISABLED"

# Set filename and save via /pipes subshell
$TMUX send-keys -t "$SESSION_NAME" "n" Enter
sleep 1
$TMUX send-keys -t "$SESSION_NAME" "test-pipes-e2e" Enter
sleep 1
$TMUX send-keys -t "$SESSION_NAME" "s" Enter
sleep 1

if [ ! -f "$SIDECAR_FILE" ]; then
    echo "FAIL: /pipes save did not create $SIDECAR_FILE"
    exit 1
fi
echo "OK: /pipes save created sidecar at $SIDECAR_FILE"

SIDECAR_CONTENT=$(cat "$SIDECAR_FILE")
if ! echo "$SIDECAR_CONTENT" | grep -q "disabledPipes"; then
    echo "FAIL: Sidecar content does not contain 'disabledPipes'"
    echo "$SIDECAR_CONTENT"
    exit 1
fi
echo "OK: /pipes save wrote valid JSON"
echo "--- Sidecar from /pipes save ---"
cat "$SIDECAR_FILE"
echo "---"

# Exit subshell
$TMUX send-keys -t "$SESSION_NAME" "q" Enter
sleep 1
capture
if ! grep -q "Returning to main shell" "$LOG_FILE"; then
    echo "FAIL: Subshell did not return to main"
    exit 1
fi
echo "OK: Returned to main shell"

#######################
# PHASE 2: /save writes Pipes.json
#######################
echo ""
echo "=== PHASE 2: /save writes Pipes.json ==="

# /save uses Env.saveContextToFile() which writes MainStory.json +
# Summary.json + Chat.json + Pipes.json (the new addition).
$TMUX send-keys -t "$SESSION_NAME" "/save" Enter
sleep 2
capture

if [ ! -f "$HOME/.TPipeWriter/Pipes.json" ]; then
    echo "FAIL: /save did not write ~/.TPipeWriter/Pipes.json"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: /save wrote ~/.TPipeWriter/Pipes.json"

SAVE_PIPES_CONTENT=$(cat "$HOME/.TPipeWriter/Pipes.json")
if ! echo "$SAVE_PIPES_CONTENT" | grep -q "disabledPipes"; then
    echo "FAIL: ~/.TPipeWriter/Pipes.json content does not contain 'disabledPipes'"
    echo "$SAVE_PIPES_CONTENT"
    exit 1
fi
if ! echo "$SAVE_PIPES_CONTENT" | grep -q "pre guide pipe"; then
    echo "FAIL: ~/.TPipeWriter/Pipes.json content does not contain the disabled pipe name"
    echo "$SAVE_PIPES_CONTENT"
    exit 1
fi
echo "OK: ~/.TPipeWriter/Pipes.json contains disabledPipes + pipe name"
echo "--- ~/.TPipeWriter/Pipes.json ---"
cat "$HOME/.TPipeWriter/Pipes.json"
echo "---"

#######################
# PHASE 3: /export writes sidecar
#######################
echo ""
echo "=== PHASE 3: /export writes sidecar ==="

# /export writes the named file (with prompt) to ~/TPipeWriter/ alongside
# the existing story.txt / story.json / lorebook.json / settings.json
$TMUX send-keys -t "$SESSION_NAME" "/export" Enter
sleep 1
$TMUX send-keys -t "$SESSION_NAME" "test-pipes-export" Enter
sleep 2
capture

EXPORT_SIDECAR="$SIDECAR_DIR/test-pipes-export-pipes.json"
if [ ! -f "$EXPORT_SIDECAR" ]; then
    echo "FAIL: /export did not write $EXPORT_SIDECAR"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: /export wrote sidecar at $EXPORT_SIDECAR"

EXPORT_CONTENT=$(cat "$EXPORT_SIDECAR")
if ! echo "$EXPORT_CONTENT" | grep -q "pre guide pipe"; then
    echo "FAIL: Export sidecar content missing disabled pipe name"
    echo "$EXPORT_CONTENT"
    exit 1
fi
echo "OK: Export sidecar contains the disabled pipe name"
echo "--- Export sidecar ---"
cat "$EXPORT_SIDECAR"
echo "---"

#######################
# PHASE 4: Quit and restart, then /load restores the state
#######################
echo ""
echo "=== PHASE 4: Restart + /load restores state ==="

# Quit the app
$TMUX send-keys -t "$SESSION_NAME" "/exit" Enter
sleep 2
capture

# Restart the app
$TMUX kill-server 2>/dev/null
rm -f "/tmp/$TMUX_SOCKET"

$TMUX new-session -d -s "$SESSION_NAME" -x 200 -y 50 "cd $PROJECT_DIR && ./run.sh 2>&1; echo 'EXIT_CODE:' \$?"
if ! wait_for "TPipeWriter Interactive Shell"; then
    echo "FAIL: TPipeWriter did not restart"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: App restarted"

# Confirm /pipes shows all enabled (fresh start)
$TMUX send-keys -t "$SESSION_NAME" "/pipes" Enter
sleep 2
capture
if grep -q "DISABLED" "$LOG_FILE"; then
    echo "FAIL: Fresh app should have all pipes enabled before /load"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: Fresh app shows all pipes enabled"

# Exit subshell
$TMUX send-keys -t "$SESSION_NAME" "q" Enter
sleep 1
capture

# Now /load
$TMUX send-keys -t "$SESSION_NAME" "/load" Enter
sleep 1
$TMUX send-keys -t "$SESSION_NAME" "test-pipes-export" Enter
sleep 3
capture

# /load should report the pipes state was loaded
if ! grep -q "Pipes state loaded" "$LOG_FILE"; then
    echo "FAIL: /load did not report 'Pipes state loaded'"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: /load reported pipes state loaded"
echo "--- /load transcript ---"
sed -n '/Enter filename/,/\[Writer\]/p' "$LOG_FILE" | tail -20
echo "---"

# Verify the live pipeline now has the disabled pipe
$TMUX send-keys -t "$SESSION_NAME" "/pipes" Enter
sleep 2
capture
if ! grep -E "^  1\. " "$LOG_FILE" | head -1 | grep -q "DISABLED"; then
    echo "FAIL: After /load, pipe #1 should be DISABLED in /pipes menu"
    cat "$LOG_FILE"
    exit 1
fi
echo "OK: After /load, pipe #1 is DISABLED on the live pipeline"

# Exit
$TMUX send-keys -t "$SESSION_NAME" "q" Enter
sleep 1
$TMUX send-keys -t "$SESSION_NAME" "/exit" Enter
sleep 1

# Final capture
capture

echo ""
echo "=== E2E PASS ==="
echo ""
echo "Log file: $LOG_FILE"
echo ""
echo "Summary:"
echo "  Phase 1: /pipes subshell toggle + save works ($PIPE_COUNT pipes listed)"
echo "  Phase 2: /save writes ~/.TPipeWriter/Pipes.json sidecar"
echo "  Phase 3: /export writes ~/TPipeWriter/test-pipes-export-pipes.json sidecar"
echo "  Phase 4: /load restores sidecar → live pipe is DISABLED on restart"
echo ""
echo "Sidecar files written:"
echo "  $HOME/.TPipeWriter/Pipes.json (from /save)"
echo "  $SIDECAR_DIR/test-pipes-export-pipes.json (from /export)"
echo ""
echo "Final log tail:"
echo "==="
tail -50 "$LOG_FILE"