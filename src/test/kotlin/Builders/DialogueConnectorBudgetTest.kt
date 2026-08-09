package Builders

import com.TTT.Pipe.Pipe
import com.TTT.Pipeline.Pipeline
import com.TTT.Pipe.TokenBudgetSettings
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the contract that every pipe in DialogueConnector carries a
 * TokenBudgetSettings with the per-pipe-deployment values agreed in
 * Phase 2 of the modernization plan: contextWindowSize = 512_000,
 * maxTokens = 12_000, reasoningBudget = null, userPromptSize = null,
 * allowUserPromptTruncation = false, compressUserPrompt = false.
 *
 * Mirrors PlusWriterPipelineBudgetTest. Does NOT call init() or hit
 * the network. Pure reflection on the built connector's pipe list.
 * A fake API key is wired into [GenericOpenAIEnv] in [BeforeAll] so
 * that buildDialogueConnector() can construct its reasoning sub-pipes
 * without throwing.
 *
 * Since buildDialogueConnector() returns a Pair<Pipeline, Connector>,
 * the test reaches through both — the evaluateDialoguePipeline is the
 * first element of the Pair; the dialogue-improvement sub-pipelines are
 * reachable through connector.getPipelinesFromInterface().
 */
class DialogueConnectorBudgetTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            // buildDialogueConnector() instantiates GenericOpenAIPipe via
            // its reasoning sub-pipes (explicitCotBuilder + authorBuilder);
            // those sub-pipes' init() validates the API key is present via
            // GenericOpenAIEnv.resolveApiKey(). Wire a fake directly so the
            // constructor path doesn't throw.
            // The tests below do NOT call init() on the pipeline, so the
            // fake key never reaches the network.
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-budget-test-only")
        }

        /**
         * Helper: collect all 4 pipes from the connector (1 classifier +
         * 3 dialogue-improvers).
         */
        private fun allPipes(): List<Pipe> {
            val (evalPipeline, connector) = buildDialogueConnector()
            val pipes = mutableListOf<Pipe>()
            pipes.addAll(evalPipeline.getPipes())
            connector.getPipelinesFromInterface().forEach { sub ->
                pipes.addAll(sub.getPipes())
            }
            return pipes
        }
    }

    @Test
    fun everyPipeHasTokenBudgetSettings() {
        val pipes = allPipes()
        assert(pipes.isNotEmpty()) { "buildDialogueConnector() returned no pipes" }

        pipes.forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings()
            assertNotNull(
                settings,
                "Pipe ${pipe.pipeName} is missing TokenBudgetSettings"
            )
        }
    }

    @Test
    fun everyPipeHas512kContextWindowAnd12kMaxTokens() {
        val pipes = allPipes()
        val expected = TokenBudgetSettings(
            contextWindowSize = 512_000,
            maxTokens = 12_000
        )

        pipes.forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings()
            assertNotNull(settings, "Pipe ${pipe.pipeName} missing budget")
            assertEquals(
                expected.contextWindowSize, settings!!.contextWindowSize,
                "contextWindowSize on pipe ${pipe.pipeName}"
            )
            assertEquals(
                expected.maxTokens, settings.maxTokens,
                "maxTokens on pipe ${pipe.pipeName}"
            )
        }
    }

    @Test
    fun budgetDisablesUserPromptTruncation() {
        val pipes = allPipes()
        pipes.forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings() ?: return@forEach
            assertEquals(
                false, settings.allowUserPromptTruncation,
                "allowUserPromptTruncation must be false (user prompt must never be truncated)"
            )
        }
    }

    @Test
    fun budgetDisablesSemanticCompression() {
        val pipes = allPipes()
        pipes.forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings() ?: return@forEach
            assertEquals(
                false, settings.compressUserPrompt,
                "compressUserPrompt must be false (no auto-compression)"
            )
            assertEquals(
                false, settings.truncateContextWindowAsString,
                "truncateContextWindowAsString must be false (no string-mode truncation)"
            )
        }
    }

    @Test
    fun reasoningBudgetIsNull() {
        val pipes = allPipes()
        pipes.forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings() ?: return@forEach
            assertEquals(
                null, settings.reasoningBudget,
                "reasoningBudget on pipe ${pipe.pipeName} must be null"
            )
            assertEquals(
                null, settings.userPromptSize,
                "userPromptSize on pipe ${pipe.pipeName} must be null"
            )
        }
    }
}