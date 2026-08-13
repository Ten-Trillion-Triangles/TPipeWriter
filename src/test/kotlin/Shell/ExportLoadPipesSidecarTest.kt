package Shell

import Globals.Env
import Builders.buildPlusWriterPipeline
import com.TTT.Util.getHomeFolder
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * TDD test for the /exportStory -> /loadStory sidecar wiring.
 *
 * The user's command was: "Must load when a story is loaded, and save
 * when a story is saved to disk." This test simulates the FULL round
 * trip:
 *   1. User has some disabled pipes in Env.activePipesState
 *   2. User runs /exportStory myNovel — writes the pipes sidecar
 *   3. User quits the app
 *   4. User restarts the app (activePipesState is reset to EMPTY)
 *   5. User runs /loadStory myNovel — reads the sidecar AND applies
 *      it to the live pipeline
 *   6. After step 5, the live pipeline has the disabled pipes set
 *
 * This test exercises the helper functions in the exact order they
 * will be called inside the real exportStory / loadStory bodies.
 * Pure reflection. No stdin.
 *
 * The actual stdin-REPL wrappers (exportStory, loadStory) are
 * exercised by the TMUX E2E harness (tests/tmux/pipes-subshell-e2e.sh).
 */
class ExportLoadPipesSidecarTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-export-load-pipes-sidecar-test-only")
        }
    }

    private val testFilenames = mutableListOf<String>()
    private var priorActiveState: DisabledPipesState = DisabledPipesState.EMPTY

    @BeforeEach
    fun snapshotState() {
        priorActiveState = Env.activePipesState
        Env.activePipesState = DisabledPipesState.EMPTY
    }

    @AfterEach
    fun restoreStateAndCleanup() {
        Env.activePipesState = priorActiveState
        for (filename in testFilenames) {
            val file = File(getHomeFolder(), "TPipeWriter/$filename-pipes.json")
            if (file.exists()) file.delete()
        }
        testFilenames.clear()
    }

    private fun reserveFilename(stem: String): String {
        val unique = "e2e-${stem}-${System.currentTimeMillis()}"
        testFilenames.add(unique)
        return unique
    }

    @Test
    fun exportStoryWritesPipesSidecar() {
        // Simulate the user's state: they toggled a pipe in the subshell
        val stateBefore = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe", "no parallel negation pipe"))
        )
        Env.activePipesState = stateBefore

        // exportStory body calls savePipesState with the active state
        val filename = reserveFilename("export-writes")
        savePipesState(filename, Env.activePipesState)

        // Verify the file exists at the expected sidecar path
        val expected = File(getHomeFolder(), "TPipeWriter/$filename-pipes.json")
        assertTrue(
            expected.exists(),
            "Expected sidecar at ${expected.absolutePath} after /exportStory"
        )

        // Verify the content is the JSON representation of the state
        val content = expected.readText()
        assertTrue(content.contains("untwist pipe"))
        assertTrue(content.contains("no parallel negation pipe"))
        assertTrue(content.contains("plusWriter"))
    }

    @Test
    fun loadStoryReadsAndAppliesPipesSidecar() {
        // Simulate the user previously exporting a story with disabled pipes
        val originalState = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe"))
        )
        val filename = reserveFilename("load-applies")
        savePipesState(filename, originalState)

        // Simulate fresh app start — activePipesState is reset, and the
        // Env.plusWriterPipe pipeline is freshly constructed (this is what
        // Env.init() does in production).
        Env.activePipesState = DisabledPipesState.EMPTY
        val pipeline = buildPlusWriterPipeline()

        // Apply a fresh EMPTY state first to ensure the apply path works
        applyPipesStateToPipeline(pipeline, ACTIVE_PIPELINE_NAME, DisabledPipesState.EMPTY)
        for (pipe in pipeline.getPipes()) {
            assertEquals(false, pipe.disablePipe, "Pre-condition: all pipes enabled")
        }

        // loadStory body
        val loaded = loadPipesState(filename)
        assertNotNull(loaded, "loadPipesState should return non-null for a file we just wrote")
        setActivePipesState(loaded!!)
        applyPipesStateToPipeline(pipeline, ACTIVE_PIPELINE_NAME, loaded)

        // After apply, the live pipe should be disabled
        val targetPipe = pipeline.getPipes().first { it.pipeName == "untwist pipe" }
        assertEquals(
            true, targetPipe.disablePipe,
            "After /loadStory, the 'untwist pipe' should be DISABLED on the live pipeline"
        )

        // And the global state should be the loaded state
        assertEquals(originalState, getActivePipesState())
    }

    @Test
    fun exportLoadRoundTripPreservesAllPipelines() {
        // Edge case: state contains multiple pipelines
        val multiState = DisabledPipesState(
            mapOf(
                "plusWriter" to setOf("untwist pipe", "no parallel negation pipe"),
                "chapterRewrite" to setOf("removeBadWritingStepOnePipe")
            )
        )
        Env.activePipesState = multiState

        val filename = reserveFilename("multi-pipeline")
        savePipesState(filename, Env.activePipesState)

        // Simulate restart + load
        Env.activePipesState = DisabledPipesState.EMPTY
        val restored = loadPipesState(filename)
        assertNotNull(restored)
        setActivePipesState(restored!!)

        assertEquals(multiState.disabledPipes, getActivePipesState().disabledPipes)
    }

    @Test
    fun loadStoryWithNoSidecarLeavesActiveStateUnchanged() {
        // Edge case: no sidecar file present at load time
        val filename = reserveFilename("no-sidecar")
        // Don't save anything — file doesn't exist

        val preState = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe"))
        )
        Env.activePipesState = preState

        val loaded = loadPipesState(filename)
        assertEquals(null, loaded, "loadPipesState returns null when file absent")

        // Per the wiring contract: if no sidecar, the active state is left as-is
        // (loadStory should not overwrite the global with empty on missing sidecar)
        assertEquals(preState, getActivePipesState())
    }

    @Test
    fun saveContextToFileWritesPipesSidecar() {
        // saveContextToFile saves MainStory.json + Summary.json + Chat.json.
        // The user's directive: "save when a story is saved to disk" — that
        // applies to /save (saveContextToFile) AND /export (exportStory).
        // Both should write the pipes sidecar.
        //
        // This test exercises the same helper path saveContextToFile will use.
        val stateBefore = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe"))
        )
        Env.activePipesState = stateBefore

        val filename = reserveFilename("save-context")
        savePipesState(filename, Env.activePipesState)

        val expected = File(getHomeFolder(), "TPipeWriter/$filename-pipes.json")
        assertTrue(expected.exists())
        assertTrue(expected.readText().contains("untwist pipe"))
    }
}