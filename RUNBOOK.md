# TPipeWriter (GenericAI branch) — Operator Runbook

This runbook walks a new operator from a fresh checkout to a working
`./run.sh` session against the MiniMax-M3 endpoint, then gives them the
diagnostic recipes they need when something goes wrong.

The **GenericAI** branch is the MiniMax-M3 Generic OpenAI edition of
TPipeWriter — it replaces the AWS Bedrock runtime on `main` with
`GenericOpenAIPipe` targeting `https://api.minimax.io/v1/responses`.
You only need a MiniMax API key. No AWS account, no Bedrock IAM, no
region pinning.

For code-level documentation of how this branch differs from `main`
and why, see [README.md](README.md). This runbook is about getting
running and staying running.

---

## 1. Prerequisites

| Requirement | Why | How to verify |
|-------------|-----|---------------|
| JDK 24 (Temurin/Zulu) | Kotlin 2.2 + Gradle 8.14 toolchain | `java -version` |
| Linux or macOS | Tested platforms; Windows untested | `uname -a` |
| 2 GB free RAM | JVM heap + shadowJar build artifacts | `free -h` |
| 1 GB disk | TPipe library build + TPipeWriter build artifacts | `df -h .` |
| Network access to `api.minimax.io` | The whole point | `curl -I https://api.minimax.io` |
| A MiniMax API key | Inference | `echo "$MINIMAX_API_KEY" \| head -c 6` |

If you have JDK 17 or 21 the Gradle toolchain will refuse to compile.
If you have only JDK 25 you may hit Kotlin compiler bugs — 24 is the
sweet spot for Kotlin 2.2.

---

## 2. First-time setup

### 2.1 Clone and enter

```bash
git clone <repo-url> TPipeWriter
cd TPipeWriter
git checkout GenericAI
```

You should see `On branch GenericAI` with 15 commits ahead of `main`
plus 1 TPipe library fork commit (see [README.md §Library fork
notes](README.md#library-fork-notes)).

### 2.2 Build the TPipe library first

TPipeWriter uses a **composite build** (`includeBuild("../TPipe/TPipe")`)
that points at a sibling checkout. If you only have TPipeWriter, the
library is missing and Gradle will fail with "Project ':TPipe' not found".

The TPipe library **must be cloned as a sibling**:

```bash
# From the same parent directory as TPipeWriter
cd ..
git clone <tpipe-repo-url> TPipe
cd TPipe/TPipe
./gradlew shadowJar
```

The TPipe repo must contain the streaming-fix commit (see [README.md
§Library fork notes](README.md#library-fork-notes) for the commit hash).
Without it, `GenericOpenAIPipe` will buffer the entire SSE response
before emitting any chunks — you'll see output appearing all at once
after the full response arrives instead of streaming token-by-token.

### 2.3 Get a MiniMax API key

1. Sign up at https://platform.minimax.io
2. Create an API key in the dashboard
3. Copy it (it starts with `eyJ...` or `sk-...` depending on tier)

The key is your credential — anyone with it can spend your quota.
Do not commit it to git. Do not paste it into bug reports.

### 2.4 Set the key

```bash
# Add this to ~/.bashrc, ~/.zshrc, or your secrets manager
export MINIMAX_API_KEY="eyJhbGciOi...your-key-here"
```

If you only have an older `AUXILIARY_VISION_API_KEY` set (the variable
some MiniMax tutorials use for visual-models), `run.sh` will fall
back to it automatically and print a warning.

### 2.5 Build TPipeWriter

```bash
cd TPipeWriter
./gradlew shadowJar
```

First build takes ~45 seconds. Subsequent builds take ~3 seconds with
the Gradle daemon warm. The output jar is at
`build/libs/TPipeWriter-1.0.0-all.jar`.

### 2.6 Launch

```bash
./run.sh
```

You should see, within ~12 seconds:

```
[run.sh] API key: OK (sk-...XXXX, 125 chars)
[run.sh] Starting TPipeWriter (MiniMax-M3 Generic OpenAI edition)...
[run.sh] Trace directory: ~/.TPipe-Debug/traces/ (auto-exported pipelines)
[run.sh] Per-command trace: ~/TPipeWriter/Trace.html (chat/lorebook/writer)
TPipeWriter - Initializing...
[main] API key in env: OK (sk-...XXXX, 125 chars)
Streaming enabled on 19 pipes (callbacks registered; chunks will appear in real time)
Streaming enabled on 1 pipes (callbacks registered; chunks will appear in real time)
Environment initialized successfully!
[main] GenericOpenAIEnv.resolveApiKey(): OK (sk-...XXXX, 125 chars)
TPipeWriter Interactive Shell
Type /help for available commands
Current mode: Writer
[Writer]>
```

The prompt `[Writer]>` is your entry point. Type `/help` to list
commands.

### 2.7 Smoke test

The fastest end-to-end verification that everything is wired
correctly:

```bash
[Writer]> /chat what is 2 plus 2
```

You should see a response stream in over ~5-30 seconds, with each
chunk appearing as MiniMax-M3 emits it. If you see the response
appear all at once after a long delay, your TPipe library build
doesn't include the streaming-fix commit — see §6 Troubleshooting.

---

## 3. Common tasks

### 3.1 Run all tests (requires API key)

```bash
MINIMAX_API_KEY="$MINIMAX_API_KEY" ./gradlew test
```

Expected: `BUILD SUCCESSFUL`. **80 tests, 0 failures.**

Test breakdown:
- 19 `MiniMaxModelConfigTest` (unit, no API key needed)
- 6 `MiniMaxSettingsTest` (unit, no API key needed)
- 1 `MiniMaxSmokeTest` (live API — gated with `assumeTrue`)
- 1 `MiniMaxStreamingTest` (live API)
- 1 `MiniMaxStreamingTimingTest` (live API — verifies real-time delivery)
- 4 `IdeaPipelineTest` (unit)
- 24 `PlusWriterUtilTest` (unit — surgical improvements preserved from main)
- 5 `ChapterManagerTest` (unit)
- 5 `ChapterSaveLoadTest` (unit)
- 7 `StreamingPropagationTest` (unit — callback propagation regression coverage)
- 4 raw HTTP diagnostic tests (live API):
  - `RawHttpStreamingTest` (java.net.HttpURLConnection)
  - `RawKtorStreamingTest` (Ktor bodyAsChannel — proves the bug)
  - `KtorSsePluginTest` (Ktor SSE plugin — alternative we considered)
- The MiniMax smoke + streaming tests are guarded with `assumeTrue` —
  if you don't have `MINIMAX_API_KEY` set, they skip cleanly without
  failing the build.

### 3.2 Run just the live tests

```bash
MINIMAX_API_KEY="$MINIMAX_API_KEY" ./gradlew test --tests "*Live*" --tests "*Streaming*" --tests "*Smoke*"
```

### 3.3 Run a TUI session in tmux

The canonical TUI test pattern (used during the streaming-fix
verification):

```bash
tmux new-session -d -s tpw -x 240 -y 60
tmux send-keys -t tpw 'cd /path/to/TPipeWriter && MINIMAX_API_KEY="$MINIMAX_API_KEY" ./run.sh' Enter
sleep 12  # let JVM warm up
tmux send-keys -t tpw '/chat write a 100 word poem about the sea' Enter
# Watch the TUI for ~30 seconds
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    sleep 2
    TUI=$(tmux capture-pane -t tpw -p -S -3 | tail -1 | head -c 80)
    SIZE=$(stat -c%s ~/TPipeWriter/Trace.html 2>/dev/null)
    echo "$(date +%T) $i: Trace=${SIZE}B TUI='${TUI}'"
done
```

You should see the trace file grow incrementally and the TUI show
new text every ~2 seconds.

### 3.4 Inspect a trace file

```bash
# Find the most recent trace
ls -lt ~/.TPipe-Debug/traces/*.html | head -1

# Parse it
python3 ~/.hermes/skills/software-development/tpipe-trace-parser/scripts/parse_html_trace.py \
    --input ~/.TPipe-Debug/traces/trace-XXXXXXXX-html.html \
    --output /tmp/trace.json
python3 ~/.hermes/skills/software-development/tpipe-trace-parser/scripts/generate_report.py \
    --input /tmp/trace.json \
    --output /tmp/trace-report.md
cat /tmp/trace-report.md
```

Look for `failure` and `exception` events — there should be zero.
Look for `streaming: true` on every API call — that's how you
verify the streaming fix is active.

### 3.5 Switch model (advanced)

The default is `MiniMax-M3`. To experiment with reasoning:

1. Edit `src/main/kotlin/Globals/ModelConfig.kt`: change
   `primaryModelName = "MiniMax-M3"` to `"MiniMax-M2.7"`
2. Edit `src/main/kotlin/Builders/MiniMaxReasoning.kt`: delete the
   `pipe.disableReasoning()` call so the wire payload carries
   reasoning per the caller's settings
3. Rebuild: `./gradlew shadowJar`
4. Restart: `./run.sh`

`MiniMax-M2.7` supports both `/v1/responses` and `/anthropic/v1/messages`
and emits reasoning blocks. The pipe's `streaming` mode shows the
reasoning content first, then the final answer — same as Bedrock
Claude with extended thinking.

---

## 4. Environment variables

| Variable | Default | Required | Purpose |
|----------|---------|----------|---------|
| `MINIMAX_API_KEY` | (none) | Yes | MiniMax API key. The application refuses to start without it (or `AUXILIARY_VISION_API_KEY` as fallback). |
| `AUXILIARY_VISION_API_KEY` | (none) | No | Fallback when `MINIMAX_API_KEY` is unset. The `run.sh` script will copy it into `MINIMAX_API_KEY` and print a warning. |
| `GENERIC_OPENAI_API_KEY` | (none) | No | Alternative name that the underlying `GenericOpenAIEnv` recognizes. We never set this; documented here for completeness. |

The application does NOT read `.env` files, `~/.config/` directories,
or any other credential store. It reads only the env vars above.
Put your real key in your shell profile (`~/.bashrc`,
`~/.zshrc`, or whatever your OS uses) or your secrets manager
(`pass`, `1Password CLI`, `aws-vault`, etc.).

---

## 5. File-system locations

| Path | Owner | Notes |
|------|-------|-------|
| `~/TPipeWriter/Trace.html` | TPipeWriter (chat/lorebook/writer) | Most recent per-command trace. Overwritten on each command. |
| `~/.TPipe-Debug/traces/trace-*.html` | TPipe library (auto-exported) | Historical traces for pipelines that explicitly enable auto-export. |
| `~/.TPipeWriter/settings.json` | TPipeWriter (`/settings`) | Your writing style, max-tokens, etc. |
| `~/.TPipeWriter/MainStory.json` | TPipeWriter | The active story's context window (lorebook, converse history). |
| `~/TPipeWriter/load-*.json` | TPipeWriter (`/export`) | Exported stories you can re-load with `/load`. |
| `~/.gradle/caches/modules-2/files-2.1/io.ktor/...` | Gradle | Downloaded Ktor artifacts. Safe to delete to force re-download. |
| `build/` (TPipeWriter) | Gradle | Build artifacts. `rm -rf build` is safe. |
| `build/` (TPipe library) | Gradle | Same. |

---

## 6. Troubleshooting

### 6.1 "ERROR: No MiniMax API key configured"

```
=============================================
ERROR: No MiniMax API key configured
=============================================
Set one of:
  export MINIMAX_API_KEY="sk-..."             # canonical
  export AUXILIARY_VISION_API_KEY="sk-..."   # dev fallback
Get a key at https://platform.minimax.io
```

Fix: `export MINIMAX_API_KEY=...` then re-run.

### 6.2 "Project ':TPipe' not found" during build

You forgot to clone the TPipe library as a sibling. See §2.2.

### 6.3 Streaming response arrives all at once (the big one)

If `/chat` or `/write` produces output only at the END of a long wait
rather than progressively, your TPipe library build doesn't have the
streaming fix.

Verify:

```bash
cd ../TPipe/TPipe
git log --oneline | grep -i "streaming\|bypass\|httpurl"
# Should show: 8e4b8d76 fix(tpipe-genericopenai): bypass Ktor for streaming ...
```

If that commit is missing, pull or cherry-pick:

```bash
cd ../TPipe/TPipe
git fetch origin
git cherry-pick 8e4b8d76    # or git pull if you're on main
cd ../TPipe
./gradlew shadowJar
```

Then rebuild TPipeWriter and retry.

### 6.4 Live tests skip silently

```
Assumption failed: MINIMAX_API_KEY not set; skipping streaming test
```

The test was skipped because `MINIMAX_API_KEY` is not in the env.
Set it and re-run.

### 6.5 401 Unauthorized from MiniMax

The key is wrong, expired, or doesn't have permission for `MiniMax-M3`.
Check the key on the MiniMax dashboard and verify it works with curl:

```bash
curl -X POST "https://api.minimax.io/v1/responses" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $MINIMAX_API_KEY" \
    -d '{"model":"MiniMax-M3","input":[{"role":"user","content":"hi"}],"max_tokens":5}'
```

A valid key returns JSON with `"output"`. An invalid key returns 401.

### 6.6 High latency / timeouts

MiniMax-M3 with `max_tokens=8000` can take 30-60 seconds per call.
The Ktor client is configured with `requestTimeoutMillis = 120000`
(2 minutes). If you're hitting timeouts, lower the `maxTokens` in
`~/.TPipeWriter/settings.json` (the `maxTokens` field under your
style).

### 6.7 "Stream Closed" errors mid-response

Stale connections. The Ktor HTTP client pool reuses connections for
keep-alive efficiency, but MiniMax's load balancer occasionally
closes idle sockets. The pipe retries the request internally; if it
keeps failing, restart `run.sh` to reset the client.

### 6.8 JDWP debugging for hangs

If a command hangs at `Thinking...` with the JVM at 0% CPU (the
classic Kotlin coroutines deadlock), attach a debugger:

```bash
# Build without shadowJar for unobfuscated stack traces
./gradlew installDist

# Launch with JDWP on port 5005
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar build/install/TPipeWriter/lib/TPipeWriter-1.0.0.jar
```

Then attach your IDE's remote debugger to `localhost:5005`. Pause the
JVM and inspect the thread dump — usually one of the `DefaultDispatcher-worker-*`
threads is parked in `kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.park`,
which means a coroutine is suspended waiting for a continuation that
never resumes.

### 6.9 Reset everything

If state gets corrupted:

```bash
# Clear TPipeWriter user state
rm -rf ~/.TPipeWriter/

# Clear TPipe library trace history
rm -rf ~/.TPipe-Debug/traces/

# Clear Gradle build artifacts
./gradlew clean
cd ../TPipe/TPipe && ./gradlew clean

# Rebuild from scratch
./gradlew shadowJar
cd ../TPipe/TPipe && ./gradlew shadowJar
```

---

## 7. Upgrading

This branch tracks `main` via occasional rebases. To pull new commits
from `main` while keeping the MiniMax-M3 changes:

```bash
git fetch origin
git rebase origin/main
# Resolve any conflicts (usually in Globals/Env.kt and ModelConfig.kt)
# Verify:
./gradlew test
```

If `main` adds new pipes, you may need to add `.setApiKey(genericOpenAIEnv.resolveApiKey())`
calls to their constructors — the `GenericOpenAIEnv` wiring is what
connects each pipe to your `MINIMAX_API_KEY`.

---

## 8. Where to get help

- **Streaming-fix details:** [README.md §Streaming fix
  history](README.md#streaming-fix-history)
- **Trace parser skill:** `~/.hermes/skills/software-development/tpipe-trace-parser/`
- **MiniMax API quirks:** `~/.hermes/skills/software-development/tpipe-generic-openai/references/minimax-api-quirks.md`
- **MiniMax model references:** `~/.hermes/skills/software-development/tpipe-generic-openai/references/minimax-model-references.md`
- **GitHub issues:** file at the repo's issue tracker with the
  diagnostic output from `MiniMaxStreamingTimingTest` and your
  `~/TPipeWriter/Trace.html` from the failing run.