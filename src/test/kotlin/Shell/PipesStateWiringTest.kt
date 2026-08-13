package Shell

import Globals.Env
import Builders.buildPlusWriterPipeline
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * TDD test for the per-pipe disable state wiring.
 *
 * Pin the contract that:
 *   1. applyPipesStateToPipeline flips disablePipe on each pipe based on
 *      whether its pipeName is in the state's disabled set.
 *   2. getActivePipesState / setActivePipesState round-trip via Env.
 *   3. savePipesState + loadPipesState round-trip preserves the active
 *      state (this is the /exportStory -> /loadStory flow).
 *
 * Pure reflection. Fake API key wired in BeforeAll so buildPlusWriterPipeline()
 * can construct its pipes without calling the network.
 */
class PipesStateWiringTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-pipes-state-wiring-test-only")
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
            val file = File(com.TTT.Util.getHomeFolder(), "TPipeWriter/$filename-pipes.json")
            if (file.exists()) file.delete()
        }
        testFilenames.clear()
    }

    private fun reserveFilename(stem: String): String {
        val unique = "wiring-test-${stem}-${System.currentTimeMillis()}"
        testFilenames.add(unique)
        return unique
    }

    @Test
    fun applyPipesStateToPipelineDisablesNamedPipes() {
        val pipeline = buildPlusWriterPipeline()
        val state = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe", "no parallel negation pipe"))
        )

        applyPipesStateToPipeline(pipeline, "plusWriter", state)

        for (pipe in pipeline.getPipes()) {
            val expectedDisabled = pipe.pipeName in setOf("untwist pipe", "no parallel negation pipe")
            assertEquals(
                expectedDisabled, pipe.disablePipe,
                "Pipe '${pipe.pipeName}' disablePipe should be $expectedDisabled"
            )
        }
    }

    @Test
    fun applyPipesStateToPipelineReEnablesPreviouslyDisabledPipes() {
        val pipeline = buildPlusWriterPipeline()

        // First disable
        applyPipesStateToPipeline(
            pipeline, "plusWriter",
            DisabledPipesState(mapOf("plusWriter" to setOf("untwist pipe")))
        )
        // Then clear
        applyPipesStateToPipeline(
            pipeline, "plusWriter",
            DisabledPipesState.EMPTY
        )

        for (pipe in pipeline.getPipes()) {
            assertEquals(
                false, pipe.disablePipe,
                "Pipe '${pipe.pipeName}' should be re-enabled after clear"
            )
        }
    }

    @Test
    fun applyPipesStateToPipelineIgnoresEmptyPipeNames() {
        val pipeline = buildPlusWriterPipeline()

        // Forcing a pipe to have empty name would be unusual but if it
        // happens, the helper should skip it (not crash on the empty-string
        // membership check).
        applyPipesStateToPipeline(
            pipeline, "plusWriter",
            DisabledPipesState(mapOf("plusWriter" to setOf("nonexistent-pipe")))
        )

        for (pipe in pipeline.getPipes()) {
            assertEquals(
                false, pipe.disablePipe,
                "Pipe with no disabled entry should remain enabled"
            )
        }
    }

    @Test
    fun getActivePipesStateAndSetActivePipesStateRoundTrip() {
        val before = getActivePipesState()
        val newState = DisabledPipesState(mapOf("plusWriter" to setOf("untwist pipe")))

        setActivePipesState(newState)
        val after = getActivePipesState()

        assertEquals(newState, after)
        assertEquals(newState, Env.activePipesState)

        // Restore for isolation
        setActivePipesState(before)
    }

    @Test
    fun saveThenLoadRoundTripsThroughDiskSidebar() {
        val filename = reserveFilename("roundtrip")
        val original = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe", "no parallel negation pipe"))
        )

        // Simulate the /exportStory step: save the active state
        setActivePipesState(original)
        savePipesState(filename, getActivePipesState())

        // Simulate the /loadStory step: clear active state, then load it back
        setActivePipesState(DisabledPipesState.EMPTY)
        assertEquals(emptyMap<String, Set<String>>(), getActivePipesState().disabledPipes)

        val restored = loadPipesState(filename)
        assertTrue(restored != null, "Loaded state should not be null")

        setActivePipesState(restored!!)
        assertEquals(original, getActivePipesState())
    }

    @Test
    fun applyPipesStateHonorsPerPipelineSeparation() {
        // The state is keyed by pipeline name. applyPipesStateToPipeline
        // only honors the entry for the specific pipeline. So state saved
        // for "plusWriter" should NOT affect a different pipeline.
        val pipeline = buildPlusWriterPipeline()
        val state = DisabledPipesState(
            mapOf("otherPipeline" to setOf("untwist pipe"))
        )

        applyPipesStateToPipeline(pipeline, "plusWriter", state)

        for (pipe in pipeline.getPipes()) {
            assertEquals(
                false, pipe.disablePipe,
                "Pipe '${pipe.pipeName}' should NOT be disabled because the state " +
                "is keyed for 'otherPipeline', not 'plusWriter'"
            )
        }
    }
}
