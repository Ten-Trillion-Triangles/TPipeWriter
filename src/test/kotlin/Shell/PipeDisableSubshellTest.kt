package Shell

import Globals.Env
import Builders.buildPlusWriterPipeline
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * TDD test for the /pipes subshell's pure logic.
 *
 * The interactive [pipeDisableSubshell] function reads from stdin via
 * the shell's [readEnhancedInput] — that path is exercised by the TMUX
 * E2E harness (tests/tmux/pipes-subshell-e2e.sh). These unit tests
 * cover the pure functions: menu rendering, toggle logic, and apply.
 *
 * Pure reflection. Fake API key wired in BeforeAll so buildPlusWriterPipeline
 * can construct its pipes without calling the network.
 */
class PipeDisableSubshellTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-pipes-subshell-test-only")
        }
    }

    private var priorActiveState: DisabledPipesState = DisabledPipesState.EMPTY

    @BeforeEach
    fun snapshotState() {
        priorActiveState = Env.activePipesState
        Env.activePipesState = DisabledPipesState.EMPTY
    }

    @AfterEach
    fun restoreState() {
        Env.activePipesState = priorActiveState
    }

    @Test
    fun renderPipesMenuListsAllPipesWithState() {
        val pipeline = buildPlusWriterPipeline()
        val state = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe"))
        )

        val menu = renderPipesMenu(state, pipeline)

        assertTrue(menu.contains("=== Pipe Disable State ==="))
        assertTrue(menu.contains("Pipeline: plusWriter"))
        assertTrue(menu.contains("untwist pipe"))
        assertTrue(menu.contains("DISABLED"))
        assertTrue(menu.contains("enabled"))
        assertTrue(menu.contains("back"))
    }

    @Test
    fun renderPipesMenuNumbersPipesSequentially() {
        val pipeline = buildPlusWriterPipeline()

        val menu = renderPipesMenu(DisabledPipesState.EMPTY, pipeline)

        // Every numbered entry should have a number followed by a dot
        // and a pipe name.
        for (i in 1..pipeline.getPipes().count { it.pipeName.isNotEmpty() }) {
            assertTrue(
                menu.contains("  $i. "),
                "Menu should contain entry numbered $i. Menu:\n$menu"
            )
        }
    }

    @Test
    fun togglePipeInStateAddsDisabledPipe() {
        val before = DisabledPipesState.EMPTY
        val after = togglePipeInState(before, "untwist pipe")
        assertTrue("untwist pipe" in after.disabledFor("plusWriter"))
    }

    @Test
    fun togglePipeInStateRemovesAlreadyDisabledPipe() {
        val before = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe"))
        )
        val after = togglePipeInState(before, "untwist pipe")
        assertTrue("untwist pipe" !in after.disabledFor("plusWriter"))
    }

    @Test
    fun togglePipeInStatePreservesOtherPipes() {
        val before = DisabledPipesState(
            mapOf("plusWriter" to setOf("other pipe"))
        )
        val after = togglePipeInState(before, "untwist pipe")
        assertTrue("untwist pipe" in after.disabledFor("plusWriter"))
        assertTrue("other pipe" in after.disabledFor("plusWriter"))
    }

    @Test
    fun togglePipeByIndexDisablesCorrectPipe() {
        val pipeline = buildPlusWriterPipeline()
        val pipes = pipeline.getPipes().filter { it.pipeName.isNotEmpty() }
        assertTrue(pipes.isNotEmpty(), "PlusWriter should have named pipes")

        val target = pipes[0].pipeName
        val result = togglePipeByIndex(DisabledPipesState.EMPTY, pipeline, 0)
        assertNotNull(result, "togglePipeByIndex(0) should return a non-null result")
        val (newState, returnedName) = result!!
        assertEquals(target, returnedName)
        assertTrue(target in newState.disabledFor("plusWriter"))
    }

    @Test
    fun togglePipeByIndexReturnsNullForOutOfRangeIndex() {
        val pipeline = buildPlusWriterPipeline()
        val result = togglePipeByIndex(DisabledPipesState.EMPTY, pipeline, 999)
        assertNull(result, "Out-of-range index should return null")
    }

    @Test
    fun togglePipeByIndexWithNegativeIndexReturnsNull() {
        val pipeline = buildPlusWriterPipeline()
        val result = togglePipeByIndex(DisabledPipesState.EMPTY, pipeline, -1)
        assertNull(result, "Negative index should return null")
    }

    @Test
    fun toggleAndApplyRoundTripDisablesPipeOnPipeline() {
        val pipeline = buildPlusWriterPipeline()
        val state = DisabledPipesState.EMPTY

        // Toggle by index 0
        val (newState, name) = togglePipeByIndex(state, pipeline, 0)!!
        // Apply to pipeline
        applyPipesStateToPipeline(pipeline, "plusWriter", newState)

        // Verify the live pipe is now disabled
        val pipe = pipeline.getPipes().first { it.pipeName == name }
        assertEquals(true, pipe.disablePipe, "Pipe '$name' should be disabled after toggle + apply")
    }

    @Test
    fun toggleAndApplyRoundTripReEnablesPipeOnPipeline() {
        val pipeline = buildPlusWriterPipeline()
        val pipes = pipeline.getPipes().filter { it.pipeName.isNotEmpty() }
        val target = pipes[0].pipeName

        // First disable
        val (disabled1, _) = togglePipeByIndex(DisabledPipesState.EMPTY, pipeline, 0)!!
        applyPipesStateToPipeline(pipeline, "plusWriter", disabled1)

        // Then re-enable
        val (disabled2, _) = togglePipeByIndex(disabled1, pipeline, 0)!!
        applyPipesStateToPipeline(pipeline, "plusWriter", disabled2)

        val pipe = pipeline.getPipes().first { it.pipeName == target }
        assertEquals(false, pipe.disablePipe, "Pipe '$target' should be re-enabled after toggle + apply")
    }
}
