package Builders

import com.TTT.Pipe.TokenBudgetSettings
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the contract that every pipe in ChapterRewritePipeline carries a
 * TokenBudgetSettings with the per-pipe-deployment values agreed in
 * Phase 2 of the modernization plan: contextWindowSize = 512_000,
 * maxTokens = 12_000, reasoningBudget = null, userPromptSize = null,
 * allowUserPromptTruncation = false, compressUserPrompt = false.
 *
 * Mirrors PlusWriterPipelineBudgetTest. Does NOT call init() or hit
 * the network. Pure reflection on the built pipeline's pipe list.
 * A fake API key is wired into [GenericOpenAIEnv] in [BeforeAll] so
 * that buildChapterRewritePipeline() can construct its reasoning
 * sub-pipes without throwing.
 */
class ChapterRewritePipelineBudgetTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            // buildChapterRewritePipeline() instantiates GenericOpenAIPipe via
            // its reasoning sub-pipes (authorBuilder, explicitCotBuilder);
            // those sub-pipes' init() validates the API key is present via
            // GenericOpenAIEnv.resolveApiKey(). Wire a fake directly so the
            // constructor path doesn't throw.
            // The tests below do NOT call init() on the pipeline, so the
            // fake key never reaches the network.
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-budget-test-only")
        }
    }

    @Test
    fun everyPipeHasTokenBudgetSettings() {
        val pipeline = buildChapterRewritePipeline()
        val pipes = pipeline.getPipes()
        assert(pipes.isNotEmpty()) { "buildChapterRewritePipeline() returned no pipes" }

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
        val pipeline = buildChapterRewritePipeline()
        val expected = TokenBudgetSettings(
            contextWindowSize = 512_000,
            maxTokens = 12_000
        )

        pipeline.getPipes().forEach { pipe ->
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
        val pipeline = buildChapterRewritePipeline()
        pipeline.getPipes().forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings() ?: return@forEach
            assertEquals(
                false, settings.allowUserPromptTruncation,
                "allowUserPromptTruncation must be false (user prompt must never be truncated)"
            )
        }
    }

    @Test
    fun budgetDisablesSemanticCompression() {
        val pipeline = buildChapterRewritePipeline()
        pipeline.getPipes().forEach { pipe ->
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
        val pipeline = buildChapterRewritePipeline()
        pipeline.getPipes().forEach { pipe ->
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