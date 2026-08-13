package Shell

import Globals.Env
import com.TTT.Util.serialize
import com.TTT.Util.deserialize
import com.TTT.Util.getHomeFolder
import com.TTT.Pipeline.Pipeline
import kotlinx.serialization.Serializable
import java.io.File
import readEnhancedInput

/**
 * Project-scoped state carrying the disabled-pipes set per pipeline.
 *
 * Keyed by pipeline name so the persisted state can contain multiple
 * pipelines' disable sets even though the /pipes subshell currently
 * only edits one at a time (the active writer pipeline).
 *
 * Persisted to disk as `$filename-pipes.json` in `~/TPipeWriter/`
 * alongside the existing story.txt / story.json / lorebook.json /
 * settings.json export artifacts. Loaded back when the user runs
 * `/load <​filename>`.
 *
 * Serialized via com.TTT.Util.serialize / deserialize (the project's
 * JSON wrapper which handles malformed AI-generated JSON via the same
 * repair path).
 */
@Serializable
data class DisabledPipesState(
    val disabledPipes: Map<String, Set<String>> = emptyMap()
) {
    /**
     * Returns the disabled set for [pipelineName], or empty if the pipeline
     * has no record in this state.
     */
    fun disabledFor(pipelineName: String): Set<String> =
        disabledPipes[pipelineName] ?: emptySet()

    /**
     * Returns a new state with [pipelineName]'s disabled set replaced by
     * [disabled]. The original state is unmodified (data class immutability).
     */
    fun withPipeline(pipelineName: String, disabled: Set<String>): DisabledPipesState =
        copy(disabledPipes = disabledPipes + (pipelineName to disabled))

    fun toJson(): String = serialize(this)

    companion object {
        /**
         * Deserializes a [DisabledPipesState] from JSON. Returns null if
         * the JSON is malformed or empty.
         */
        fun fromJson(json: String): DisabledPipesState? =
            deserialize<DisabledPipesState>(json)

        val EMPTY = DisabledPipesState(emptyMap())
    }
}

/**
 * Resolves the sidecar file path for the given [filename] stem within the
 * project's `~/TPipeWriter/` directory. Mirrors the existing settings.json
 * / lorebook.json / story.json export pattern.
 */
fun pipesStatePath(filename: String): File {
    val dir = File("${getHomeFolder()}/TPipeWriter")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "$filename-pipes.json")
}

/**
 * Persists [state] to `~/TPipeWriter/$filename-pipes.json`.
 */
fun savePipesState(filename: String, state: DisabledPipesState) {
    pipesStatePath(filename).writeText(state.toJson())
}

/**
 * Loads the project-scoped pipes state from
 * `~/TPipeWriter/$filename-pipes.json`. Returns null if the file does not
 * exist or if the JSON is malformed.
 */
fun loadPipesState(filename: String): DisabledPipesState? {
    val file = pipesStatePath(filename)
    if (!file.exists()) return null
    return DisabledPipesState.fromJson(file.readText())
}

/**
 * Accessor for the live project-scoped pipes state. The /pipes subshell
 * reads/writes this when the user toggles a pipe.
 */
fun getActivePipesState(): DisabledPipesState = Env.activePipesState

/**
 * Mutator for the live project-scoped pipes state. The /pipes subshell
 * calls this when the user toggles a pipe.
 */
fun setActivePipesState(state: DisabledPipesState) {
    Env.activePipesState = state
}

/**
 * Applies a [DisabledPipesState] to a [Pipeline] by setting `disablePipe`
 * = true on every pipe whose name appears in [state.disabledFor] for the
 * pipeline's canonical name. Inverse: pipes NOT in the disabled set are
 * set to `disablePipe = false` (enabled).
 *
 * This is hot-apply — the pipeline is not rebuilt. The framework checks
 * `disablePipe` only at the start of each pipe's `execute()` call (see
 * Pipeline.kt:1496 in the TPipe framework), so flipping the flag mid-run
 * takes effect on the NEXT pipe invocation.
 */
fun applyPipesStateToPipeline(
    pipeline: Pipeline,
    pipelineName: String,
    state: DisabledPipesState
) {
    val disabled = state.disabledFor(pipelineName)
    for (pipe in pipeline.getPipes()) {
        if (pipe.pipeName.isNotEmpty()) {
            pipe.setDisablePipe(pipe.pipeName in disabled)
        }
    }
}

/**
 * Canonical pipeline name used by the /pipes subshell. Matches the
 * conventional name used in other subshell code (`Env.plusWriterPipe`).
 */
const val ACTIVE_PIPELINE_NAME = "plusWriter"

/**
 * Renders the /pipes subshell's main menu to a string. The user sees
 * this printed each loop iteration. Pure function (no side effects)
 * so it can be unit-tested.
 */
fun renderPipesMenu(state: DisabledPipesState, pipeline: Pipeline): String {
    val sb = StringBuilder()
    sb.appendLine("=== Pipe Disable State ===")
    sb.appendLine("Pipeline: $ACTIVE_PIPELINE_NAME")
    sb.appendLine()
    val pipes = pipeline.getPipes().filter { it.pipeName.isNotEmpty() }
    val disabled = state.disabledFor(ACTIVE_PIPELINE_NAME)
    pipes.forEachIndexed { idx, pipe ->
        val state_marker = if (pipe.pipeName in disabled) "[DISABLED]" else "[enabled] "
        sb.appendLine("  ${idx + 1}. ${pipe.pipeName.padEnd(40)} $state_marker")
    }
    sb.appendLine()
    sb.appendLine("Enter a number to toggle, or one of:")
    sb.appendLine("  s = save to current sidecar")
    sb.appendLine("  r = reload from current sidecar")
    sb.appendLine("  c = clear all (re-enable every pipe)")
    sb.appendLine("  n = enter a new filename to load/save")
    sb.appendLine("  back / exit / q = return to main shell")
    return sb.toString()
}

/**
 * Toggles the pipe at [index] in the active pipeline's disable list.
 * Returns the new state. Pure function.
 */
fun togglePipeInState(state: DisabledPipesState, pipeName: String): DisabledPipesState {
    val disabled = state.disabledFor(ACTIVE_PIPELINE_NAME)
    val newDisabled = if (pipeName in disabled) {
        disabled - pipeName
    } else {
        disabled + pipeName
    }
    return state.withPipeline(ACTIVE_PIPELINE_NAME, newDisabled)
}

/**
 * Toggles the pipe at zero-indexed [index] in the pipeline's pipe list.
 * Returns the new state and the pipe's name (for the caller to print).
 * Returns null if the index is out of range.
 */
fun togglePipeByIndex(
    state: DisabledPipesState,
    pipeline: Pipeline,
    index: Int
): Pair<DisabledPipesState, String>? {
    val pipes = pipeline.getPipes().filter { it.pipeName.isNotEmpty() }
    if (index < 0 || index >= pipes.size) return null
    val pipeName = pipes[index].pipeName
    return togglePipeInState(state, pipeName) to pipeName
}

/**
 * Main loop for the /pipes subshell. Reads commands from stdin and
 * toggles/saves/loads the disabled-pipes state. Interacts with the
 * active pipeline (Env.plusWriterPipe) live.
 */
fun pipeDisableSubshell(initialFilename: String = "") {
    var filename = initialFilename
    println("=== /pipes subshell ===")
    println("Active pipeline: $ACTIVE_PIPELINE_NAME (Env.plusWriterPipe)")
    if (filename.isNotBlank()) {
        println("Project sidecar: $filename-pipes.json")
    } else {
        println("No project sidecar loaded. Use 'n' to set a filename.")
    }
    println("Type 'back', 'exit', or 'q' to return to main shell.")
    println()

    while (true) {
        val pipeline = Env.plusWriterPipe
        val state = getActivePipesState()
        println(renderPipesMenu(state, pipeline))

        print("pipes> ")
        val input = readEnhancedInput(removeDelimiterAtEnd = true).trim().lowercase()

        when {
            input.isEmpty() -> continue
            input == "back" || input == "exit" || input == "q" -> {
                println("Returning to main shell.")
                return
            }
            input == "s" -> {
                if (filename.isBlank()) {
                    println("No filename set. Use 'n' to enter a filename first.")
                    continue
                }
                savePipesState(filename, getActivePipesState())
                println("Saved to ${pipesStatePath(filename).absolutePath}")
            }
            input == "r" -> {
                if (filename.isBlank()) {
                    println("No filename set. Use 'n' to enter a filename first.")
                    continue
                }
                val loaded = loadPipesState(filename)
                if (loaded == null) {
                    println("No sidecar found at ${pipesStatePath(filename).absolutePath}")
                } else {
                    setActivePipesState(loaded)
                    applyPipesStateToPipeline(Env.plusWriterPipe, ACTIVE_PIPELINE_NAME, loaded)
                    println("Loaded $filename-pipes.json")
                }
            }
            input == "c" -> {
                val cleared = getActivePipesState().withPipeline(ACTIVE_PIPELINE_NAME, emptySet())
                setActivePipesState(cleared)
                applyPipesStateToPipeline(Env.plusWriterPipe, ACTIVE_PIPELINE_NAME, cleared)
                println("All pipes re-enabled.")
            }
            input == "n" -> {
                print("Enter filename (without extension): ")
                val newName = readEnhancedInput(removeDelimiterAtEnd = true).trim()
                if (newName.isBlank()) {
                    println("Invalid filename.")
                } else {
                    filename = newName
                    println("Project sidecar set to: $filename-pipes.json")
                }
            }
            input.toIntOrNull() != null -> {
                val idx = input.toInt() - 1
                val pipes = pipeline.getPipes().filter { it.pipeName.isNotEmpty() }
                if (idx < 0 || idx >= pipes.size) {
                    println("Invalid pipe number. Valid range: 1..${pipes.size}")
                    continue
                }
                val pipe = pipes[idx]
                val newState = togglePipeInState(getActivePipesState(), pipe.pipeName)
                setActivePipesState(newState)
                pipe.setDisablePipe(pipe.pipeName in newState.disabledFor(ACTIVE_PIPELINE_NAME))
                val nowState = if (pipe.disablePipe) "DISABLED" else "enabled"
                println("Toggled '${pipe.pipeName}' -> $nowState")
            }
            else -> println("Unknown command: '$input'. Enter a number, s, r, c, n, or back.")
        }
    }
}
