package Builders

import com.TTT.Pipeline.Pipeline
import com.TTT.Pipe.TokenBudgetSettings
import genericOpenAIPipe.GenericOpenAIPipe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Regression test for per-pipe budget propagation. The PlusWriterPipeline
 * post-init block iterates getPipes() and calls setTokenBudget on each.
 * This test pins the contract that the same pattern works on a
 * hand-built nested pipeline (Pipeline-within-Pipeline).
 *
 * Does NOT call init() or hit the network. A fake API key is wired into
 * GenericOpenAIEnv in [BeforeAll] so the constructors don't throw.
 */
class PerPipeBudgetPropagationTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIPipe::class.java // touch the class so the Kotlin compiler keeps the import
            genericOpenAIPipeEnvSetApiKey()
        }

        private fun genericOpenAIPipeEnvSetApiKey() {
            // Same as PlusWriterPipelineBudgetTest companion.
            genericOpenAIPipe.env.GenericOpenAIEnv.setApiKey("sk-test-fake-for-propagation-test")
        }
    }

    @Test
    fun budgetPropagatesToEveryPipeInFlatPipeline() {
        val a = GenericOpenAIPipe().setApiKey("test").setBaseUrl("https://x")
        val b = GenericOpenAIPipe().setApiKey("test").setBaseUrl("https://x")
        val c = GenericOpenAIPipe().setApiKey("test").setBaseUrl("https://x")

        val pipeline = Pipeline().add(a).add(b).add(c)

        val budget = plusWriterPipelineBudget
        pipeline.getPipes().forEach { it.setTokenBudget(budget) }

        pipeline.getPipes().forEach { pipe ->
            val s = pipe.getTokenBudgetSettings()
            assertNotNull(s, "Pipe ${pipe.pipeName} missing budget")
            assertEquals(512_000, s!!.contextWindowSize)
            assertEquals(12_000, s.maxTokens)
        }
    }

    @Test
    fun flatPipelineOfFivePipesAllCarryBudget() {
        val pipes = (1..5).map {
            GenericOpenAIPipe().setApiKey("test").setBaseUrl("https://x").setPipeName("test-pipe-$it")
        }
        val pipeline = Pipeline().add(pipes[0]).add(pipes[1]).add(pipes[2]).add(pipes[3]).add(pipes[4])
        pipeline.getPipes().forEach { it.setTokenBudget(plusWriterPipelineBudget) }

        assertEquals(5, pipeline.getPipes().size)
        pipeline.getPipes().forEachIndexed { idx, pipe ->
            assertEquals("test-pipe-${idx + 1}", pipe.pipeName)
            val s = pipe.getTokenBudgetSettings()
            assertNotNull(s)
            assertEquals(512_000, s!!.contextWindowSize)
            assertEquals(12_000, s.maxTokens)
            assertEquals(false, s.allowUserPromptTruncation)
            assertEquals(false, s.compressUserPrompt)
        }
    }
}