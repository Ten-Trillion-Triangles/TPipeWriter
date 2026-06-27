# Writer Personality Runtime Overrides — TPipeWriter

> For Apex (implementer): use test-driven-development (RED→GREEN→REFACTOR) and the systematic-debugging skill on any failure. The user has a hard requirement: TDD + LLDB verification for any change touching personality wiring. Each personality field must round-trip through (a) unit tests, (b) tmux end-to-end, (c) JDWP live session.

## Goal

Make the three writer personalities — `Env.authorPrompt` (Xilaron Rigogan), `Env.editorPrompt` (Falkenda Unseppal), `Env.richardTreadwell` (N'zelquin G'zeeloth) — runtime-overridable from the TUI/CLI subshells, persisted per-story, and round-trippable through save/load. Today they are compile-time constants in `Globals/Env.kt:109-162` and only `authorPrompt` and `richardTreadwell` have save/load paths (both surfaced via `/author` subshell). The editor has zero save/load.

## Current Context (verified by code read)

- Three personalities: `Globals/Env.kt:109-162` (`authorPrompt`, `richardTreadwell`, `editorPrompt`, plus `writingControlPrompt` which is *not* user-overridable).
- `Env.activeAuthorGuide` already exists (`Env.kt:264`) but `activeEditorGuide` and `activeRichardTreadwell` do not — only `richardTreadwell` itself is used (per Pipeline reads at `PlusWriterPipeline.kt:184, 357, 647, 1310, 1396` and `ExpansionPipeline.kt:357`).
- `TPipeSettings` data class: `Shell/Shell.kt:1081-1091`. Fields: `writingStyle, temperature, topP, maxTokens, useAutoLorebook, authorGuide, competingAuthorGuide, chapterGuide, storyGuide`. **No `editorGuide` field.** Adding one is a `TPipeSettings.copy(...)` migration — must default to "" so old `settings.json` files still deserialize.
- Save/load plumbing for author + treadwell: `Shell/GuideSubshell.kt:223-417`. Both functions exist with bugs already fixed in the June 2 sweep (`docs/maestro/reports/2026-06-02-tpipe-settings-bughunt-report.md` bugs #1-#4).
- Slash dispatch: `Shell/Shell.kt:282` → `/guide`, `:283` → `/author`. `selectAuthorMode` (`Shell/AuthorSubshell.kt:5-30`) is single-shot, not a loop — known bug #10-class issue.
- Per-chapter metadata: `Chapter/ChapterMetadata.kt:14-22`. Already `@Serializable` with title/tags/wordCount/timestamps. Add three String fields (defaults empty) and they round-trip automatically via `GlobalChapterManager.saveMetadata` / `loadMetadata` (`Shell/Shell.kt:975`).
- Test infrastructure: 22 unit tests under `src/test/kotlin/`. `./gradlew test`, `./gradlew installDist`. JDWP-capable JVM. Tmux + JDWP verification pattern documented in `docs/maestro/plans/archive/2026-06-02-tpipe-settings-bughunt-design.md`.

## User Decisions (Phase 2 interview)

1. **Binding: per-story.** Personality snapshots are bundled with each saved chapter via `ChapterMetadata`. Loading a chapter restores the three personalities; saving a chapter captures whichever personalities are active at save time.
2. **Scope: editor + per-story only.** Add full `/editor` save/load parity AND extend chapter save/load to snapshot all three. Do not modify the `/author` subshell UX; expose editor save/load via a new `/editor` subshell entry.

## Architecture

Three orthogonal changes:

1. **`TPipeSettings` + `Env`**: add `editorGuide: String` field to `TPipeSettings`; add `activeEditorGuide` and `activeRichardTreadwell` mirrors of `activeAuthorGuide` so the rest of the codebase reads from a single source-of-truth pattern. Existing `Env.richardTreadwell` stays as the *consumer* reference (pipelines read it directly today); `activeRichardTreadwell` is the persistence-side mirror.
2. **`/editor` subshell**: new `Shell/EditorSubshell.kt` mirroring `GuideSubshell.kt:223-295` for saveAuthorGuide/loadAuthorGuide. Three menu options: Save Editor / Load Editor / back. Single-shot loop (same UX as the fixed `selectGuideMode`).
3. **Per-chapter snapshot**: extend `ChapterMetadata` with `authorPromptSnapshot`, `editorPromptSnapshot`, `richardTreadwellSnapshot: String = ""`. On chapter save, capture `Env.authorPrompt` / `Env.editorPrompt` / `Env.richardTreadwell`. On chapter load, set the `Env` fields if the snapshot is non-empty.

## Tech Stack

Kotlin 2.x, Gradle 8.14.3+, kotlinx.serialization, TPipe framework, JUnit 5 (per `build.gradle.kts`). Tmux 3.x, JDWP via `./gradlew run --debug-jvm` or `JAVA_TOOL_OPTIONS=-agentlib:jdwp=...`. No new dependencies.

## TDD Discipline (applies to every task)

For each new function:
1. Write the failing JUnit test that pins the wire-level contract (input → expected Env state + settings file state).
2. Run `./gradlew test --tests "<TestName>"` — confirm RED.
3. Write minimal implementation.
4. Run again — confirm GREEN.
5. Commit.

For the existing buggy paths (`saveAuthorGuide`, `loadAuthorGuide`, `saveRichardTreadwell`, `loadRichardTreadwell`): regression tests that lock in the June 2 bug-hunt fix set must run before any new code lands.

For the runtime tmux + JDWP verification: drive `./build/install/TPipeWriter/bin/TPipeWriter` under tmux, set a breakpoint at `GuideSubshell.kt:saveEditorGuide`, send `/editor`, `1`, paste text, `save`, `back`, `/author`, `5` (Export Settings) — confirm `editorGuide` round-trips in `~/.TPipeWriter/settings.json` and re-import restores `Env.editorPrompt`.

---

## Task 1: Pin existing save/load behaviour with regression tests

**Objective:** Lock in June 2 bug-hunt fixes so future refactors can't silently regress them.

**Files:**
- Create: `src/test/kotlin/Shell/GuideSubshellRegressionTest.kt`

**Step 1 — write failing test.** Test four things:
- `saveAuthorGuide` writes `TPipeSettings.authorGuide` AND sets `Env.authorPrompt` AND sets `Env.activeAuthorGuide`.
- `loadAuthorGuide` does the same after round-trip.
- `saveRichardTreadwell` writes `TPipeSettings.competingAuthorGuide` AND sets `Env.richardTreadwell`.
- `loadRichardTreadwell` does the same.

Use a temp directory via `System.setProperty("user.home", tmpDir)` or a `HOME` env override if `getHomeFolder()` reads it. (Read `com.TTT.Util.getHomeFolder()` source first; if it's hardcoded, refactor to honour a `TPIPEWRITER_HOME` env var with fallback — but only if needed for the test. Otherwise use `@TempDir` and adjust test to copy `settings.json` in/out.)

**Step 2 — run.** `./gradlew test --tests "Shell.GuideSubshellRegressionTest"` — expected RED on any branch with the pre-fix bug.

**Step 3 — verify on `main` first.** The June 2 sweep is already merged (`docs/maestro/reports/2026-06-02-tpipe-settings-bughunt-report.md` confirms). Run test on current HEAD — expected GREEN. If RED, the previous fixes have regressed; that becomes Task 1.1 (re-fix before proceeding).

**Step 4 — commit.** `git commit -m "test(writer, guide): regression tests pinning June 2 bug-hunt fixes"`.

**Verification:** `./gradlew test --tests "Shell.GuideSubshellRegressionTest"` → 4/4 pass. Build clean.

---

## Task 2: Extend `TPipeSettings` + `Env` for editor guide

**Objective:** Add `editorGuide` persistence field and the `activeEditorGuide` / `activeRichardTreadwell` mirror fields.

**Files:**
- Modify: `src/main/kotlin/Shell/Shell.kt:1081-1091` (add `val editorGuide: String = ""` field — default empty so old settings.json deserializes)
- Modify: `src/main/kotlin/Globals/Env.kt:254-264` (add `var activeEditorGuide = ""` and `var activeRichardTreadwell = ""` next to `activeAuthorGuide`)

**Step 1 — write failing test.**
`src/test/kotlin/Shell/TPipeSettingsTest.kt`:
- `TPipeSettings()` default → `editorGuide == ""`.
- `TPipeSettings(...).copy(editorGuide = "X")` round-trips through `kotlinx.serialization.json.Json.encodeToString` / `decodeFromString`.
- An old settings.json without the new field deserializes with `editorGuide == ""` (backward compatibility).

**Step 2 — run.** Expected RED (field missing).

**Step 3 — implementation.** Add the three fields exactly as described. Do not reorder existing fields.

**Step 4 — run.** Expected GREEN.

**Step 5 — commit.** `git commit -m "feat(settings): add editorGuide + activeEditorGuide/activeRichardTreadwell mirrors"`.

**Verification:** All 22 + 1 (Task 1) + 1 (Task 2) tests pass.

---

## Task 3: `saveEditorGuide` + `loadEditorGuide`

**Objective:** Mirror the June-2-fixed author/treadwell patterns for the editor personality.

**Files:**
- Modify: `src/main/kotlin/Shell/GuideSubshell.kt` (append `saveEditorGuide()` and `loadEditorGuide()` after the `loadAuthorGuide` block at line 295)

**Step 1 — write failing test.**
Extend `src/test/kotlin/Shell/GuideSubshellRegressionTest.kt` (or new `EditorGuideSubshellTest.kt`):
- `saveEditorGuide` writes `settings.editorGuide`, sets `Env.editorPrompt = guide`, sets `Env.activeEditorGuide = guide`.
- `loadEditorGuide` reads file → sets `Env.editorPrompt`, sets `Env.activeEditorGuide`, persists `settings.editorGuide`.
- Empty / missing file path returns gracefully (no throw).

**Step 2 — run.** Expected RED.

**Step 3 — implementation.** Copy `saveAuthorGuide` and `loadAuthorGuide` (lines 223-295) verbatim, change `"author-guide"` → `"editor-guide"`, `authorGuide` → `editorGuide`, `authorPrompt` → `editorPrompt`, `activeAuthorGuide` → `activeEditorGuide`. File naming convention follows existing `-author-guide.txt`.

**Step 4 — run.** Expected GREEN.

**Step 5 — commit.** `git commit -m "feat(guide): save/load editor guide with full Env propagation"`.

**Verification:** All tests pass; manual code-read confirms no `authorPrompt`/`authorGuide` references leaked.

---

## Task 4: `selectEditorMode` subshell + `/editor` dispatch

**Objective:** Add the TUI entry point.

**Files:**
- Create: `src/main/kotlin/Shell/EditorSubshell.kt` (mirror `Shell/AuthorSubshell.kt:5-30`)
- Modify: `src/main/kotlin/Shell/Shell.kt:282-284` (add `"editor" -> selectEditorMode()` line)

**Step 1 — write failing test.**
`src/test/kotlin/Shell/EditorSubshellTest.kt`:
- Subshell menu shows three numbered options (Save / Load / back).
- Integer dispatch to `saveEditorGuide` / `loadEditorGuide` / return works.
- Invalid input rejected without throwing.

(Use a thin wrapper that intercepts `readEnhancedInput()` via a constructor parameter — or test the dispatch logic by extracting a `parseEditorCommand(input: String): EditorCommand` enum and unit-testing the parser directly. The latter is cleaner. Push parser into a pure function so the I/O wrapper stays thin.)

**Step 2 — run.** Expected RED.

**Step 3 — implementation.** Pure parser enum, then thin `selectEditorMode()` loop. Wire `/editor` in `Shell.kt` dispatch (find the right offset — read `Shell.kt:281-292` first to confirm the exact dispatch block).

**Step 4 — run.** Expected GREEN.

**Step 5 — commit.** `git commit -m "feat(shell): /editor subshell entry point"`.

**Verification:** `./gradlew test --tests "Shell.EditorSubshellTest"` → pass.

---

## Task 5: Per-chapter personality snapshot — model fields

**Objective:** Extend `ChapterMetadata` with three personality snapshot fields.

**Files:**
- Modify: `src/main/kotlin/Chapter/ChapterMetadata.kt:14-22` (add `authorPromptSnapshot`, `editorPromptSnapshot`, `richardTreadwellSnapshot: String = ""`)

**Step 1 — write failing test.**
`src/test/kotlin/Chapter/ChapterMetadataSnapshotTest.kt`:
- `ChapterMetadata()` defaults → all three snapshot fields are empty strings.
- Round-trip through `Json.encodeToString` / `decodeFromString` preserves non-empty snapshots.
- Old serialized JSON without these fields deserializes with empty defaults (backward compat).

**Step 2 — run.** Expected RED.

**Step 3 — implementation.** Add the three fields. KDoc on each: `/** Snapshot of Env.authorPrompt captured at chapter save time. Restored on chapter load if non-empty. */`.

**Step 4 — run.** Expected GREEN.

**Step 5 — commit.** `git commit -m "feat(chapter): per-chapter personality snapshot fields"`.

**Verification:** All existing chapter tests still pass; no `ChapterMetadata` field reorder broke any serialization test.

---

## Task 6: Chapter save — capture personality snapshots

**Objective:** When a chapter is saved, snapshot the three active personalities.

**Files:**
- Locate the chapter-save handler. Read `Shell/Shell.kt:940-985` (the `saveCurrentStory` / chapter save path mentioned in `Shell.kt:975` for load). Identify the function that writes `ChapterMetadata` to disk.

**Step 1 — write failing test.**
- Construct `ChapterMetadata` for the active chapter; populate `authorPromptSnapshot = Env.authorPrompt`, `editorPromptSnapshot = Env.editorPrompt`, `richardTreadwellSnapshot = Env.richardTreadwell`; call save; reload from disk; assert snapshots match.

If chapter-save is glued to other I/O (e.g. reads stdin for chapter content), extract a pure function `captureChapterPersonalitySnapshot(env: Env): ChapterMetadata` that returns a `ChapterMetadata` with snapshots populated. Test that. Then have the save handler call it.

**Step 2 — run.** Expected RED.

**Step 3 — implementation.** Pure capture function in `Chapter/ChapterMetadata.kt` (or `Chapter/GlobalChapterManager.kt` — pick whichever the test layout suggests; don't add new files unless necessary).

**Step 4 — run.** Expected GREEN.

**Step 5 — commit.** `git commit -m "feat(chapter): snapshot active personalities on chapter save"`.

**Verification:** Round-trip: save → reload → snapshots match `Env.*` at save time.

---

## Task 7: Chapter load — restore personality snapshots

**Objective:** On chapter load, if the snapshot is non-empty, set `Env.authorPrompt` / `Env.editorPrompt` / `Env.richardTreadwell`.

**Files:**
- Locate the chapter-load handler (same region as Task 6).
- Modify the load path: after `loadMetadata`, call `Env.applyPersonalitySnapshot(metadata)` if any snapshot field is non-empty.

**Step 1 — write failing test.**
- Given a `ChapterMetadata` with all three snapshots populated, calling the load handler sets `Env.authorPrompt`, `Env.editorPrompt`, `Env.richardTreadwell`.
- Given a `ChapterMetadata` with empty snapshots, calling the load handler leaves `Env` unchanged (does not blank out the current personalities).
- Empty-snapshot path: if only `authorPromptSnapshot` is set and the other two are empty, only `Env.authorPrompt` is overwritten; editor and treadwell are unchanged.

**Step 2 — run.** Expected RED.

**Step 3 — implementation.** Pure function `Env.applyPersonalitySnapshot(metadata: ChapterMetadata)` that sets fields only when the snapshot string is non-empty. Document this behaviour in KDoc.

**Step 4 — run.** Expected GREEN.

**Step 5 — commit.** `git commit -m "feat(chapter): restore personality snapshots on chapter load"`.

**Verification:** Round-trip: set custom personalities → save → change personalities → load → restored.

---

## Task 8: Tmux end-to-end verification

**Objective:** Drive the actual binary to prove the new `/editor` subshell works in the real shell.

**Files:**
- Capture transcripts under `docs/maestro/transcripts/personality-runtime-overrides/` (mkdir if absent).

**Steps:**
1. `./gradlew installDist --console=plain --no-daemon`.
2. `tmux new-session -d -s tpipe './build/install/TPipeWriter/bin/TPipeWriter'`.
3. `sleep 3`. Capture screen via `tmux capture-pane -t tpipe -p > 01-startup.txt`.
4. Send `/editor`, `1`, paste a known marker string + `save`, name `transcript-editor`, `back`.
5. Capture `02-after-save-editor.txt`. Verify prompt reports success.
6. Send `/author`, `5` (Export Settings — verify the export path includes `editorGuide`; if not, that's a regression to flag).
7. Capture `03-export-after-editor.txt`. Grep for `editorGuide` in the export.
8. Restart TPipeWriter in a fresh tmux session; send `/editor`, `2`, load `transcript-editor`. Verify `Env.editorPrompt` is restored by sending a write prompt that includes the marker; grep output for the marker.
9. Capture all transcripts.
10. `tmux kill-session -t tpipe`.

**Verification:** All four transcripts captured; `editorGuide` appears in the export file; load restores the editor prompt.

---

## Task 9: JDWP live verification

**Objective:** Set a breakpoint at `saveEditorGuide` and `Env.applyPersonalitySnapshot`; confirm the new code paths execute in the live JVM.

**Steps:**
1. Launch with JDWP: `JAVA_TOOL_OPTIONS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005' ./build/install/TPipeWriter/bin/TPipeWriter`.
2. Attach via `jdb -attach 5005` (or the `mcp_jdwp_debug_*` tools already available).
3. `stop in com.TTT...Shell.GuideSubshellKt.saveEditorGuide` (use the actual class name — confirm via `javap` or by reading the compiled class).
4. Send `/editor`, `1`, `BREAKPOINT_MARKER save`, `transcript-jdwp`, `back`. Expect breakpoint hit.
5. Step through; verify `Env.editorPrompt = guide` executes.
6. Set a breakpoint at `applyPersonalitySnapshot`. Repeat with chapter save+load.
7. Capture JDWP session log under `docs/maestro/transcripts/personality-runtime-overrides/jdwp.log`.

**Verification:** Both breakpoints hit; variables confirm correct strings; no unexpected exceptions.

---

## Task 10: Final integration sweep

**Objective:** Run the full test suite, install dist, capture state, write final report.

**Steps:**
1. `./gradlew test --rerun-tasks --console=plain --no-daemon` — expect all tests pass.
2. `./gradlew installDist --console=plain --no-daemon` — expect clean build.
3. Final tmux sweep: full round-trip author → editor → treadwell → save chapter → reload chapter → confirm all three restored.
4. Update `docs/maestro/reports/2026-06-26-personality-runtime-overrides-report.md` with: fixed bugs, new tests, transcripts, JDWP evidence.

**Verification:** All green. Report file exists with evidence.

---

## Files Likely to Change (consolidated)

- `src/main/kotlin/Shell/Shell.kt` — `TPipeSettings` field, `/editor` dispatch.
- `src/main/kotlin/Shell/GuideSubshell.kt` — `saveEditorGuide` + `loadEditorGuide`.
- `src/main/kotlin/Shell/EditorSubshell.kt` — NEW.
- `src/main/kotlin/Globals/Env.kt` — `activeEditorGuide`, `activeRichardTreadwell`, `applyPersonalitySnapshot`.
- `src/main/kotlin/Chapter/ChapterMetadata.kt` — three snapshot fields.
- `src/test/kotlin/Shell/GuideSubshellRegressionTest.kt` — NEW.
- `src/test/kotlin/Shell/TPipeSettingsTest.kt` — NEW.
- `src/test/kotlin/Shell/EditorSubshellTest.kt` — NEW.
- `src/test/kotlin/Chapter/ChapterMetadataSnapshotTest.kt` — NEW.

## Risks & Tradeoffs

- **TPipeSettings serialization forward-compat**: adding `editorGuide` with default `""` keeps old `settings.json` files loadable. Verified by Task 2 test. No migration script needed.
- **Empty-snapshot semantics**: an empty snapshot means "do not override on load" (rather than "blank out the personality"). This preserves current behaviour for chapters saved before this change. Documented in KDoc on `applyPersonalitySnapshot`.
- **`Env.richardTreadwell` vs `activeRichardTreadwell`**: the active mirror is the persistence-side variable. `Env.richardTreadwell` remains the consumer-side variable that pipelines read. `applyPersonalitySnapshot` writes to `Env.richardTreadwell`. `saveRichardTreadwell` writes both. Don't consolidate — separation is intentional and the regression tests pin it.
- **TPipe framework under `../TPipe/TPipe/`**: NOT in scope per June 2 bug-hunt precedent.

## Verification Checklist

- [ ] All 22 pre-existing tests pass.
- [ ] All new tests pass (Tasks 1, 2, 3, 4, 5, 6, 7).
- [ ] `./gradlew installDist` succeeds.
- [ ] Tmux round-trip succeeds for all three personalities.
- [ ] JDWP breakpoints hit at expected sites with expected values.
- [ ] Final report at `docs/maestro/reports/2026-06-26-personality-runtime-overrides-report.md`.
- [ ] No regression in any of the 13 June 2 bug-hunt fixes.
- [ ] Old `~/.TPipeWriter/settings.json` files still load (forward compat).

## Out of Scope

- Changing the `/author` subshell UX.
- Refactoring TPipeSettings into a Map<String, String>.
- Adding `writingControlPrompt` (Invis von Disappearo) as user-overridable — it has no save/load today and isn't part of the three personalities the user named.
- Touching `../TPipe/TPipe/`.
