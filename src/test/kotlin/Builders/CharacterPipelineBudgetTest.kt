package Builders

import com.TTT.Pipe.MultiPageBudgetStrategy
import com.TTT.Pipe.TokenBudgetSettings
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the modernized TokenBudgetSettings pattern across both
 * CharacterPipeline build functions (buildCharacterPipeline +
 * buildCharacterPipelineWithStory).
 *
 * Per the 2026-08-09 audit (c8e19dc) CharacterPipeline was stuck
 * with a stale writerBudgetSettings object that used maxTokens=8000,
 * allowUserPromptTruncation=TRUE, and was missing the modernized
 * reasoningBudget/userPromptSize/compressUserPrompt flags. This test
 * class pins the canonical values from characterPipelineBudget so
 * the regression cannot recur.
 *
 * Mirrors PlusWriterPipelineBudgetTest / ChapterRewritePipelineBudgetTest
 * / ExpansionPipelineBudgetTest. Pure reflection — does NOT call
 * init() or hit the network. Fake API key wired in BeforeAll so the
 * constructor path (authorBuilder reasoning sub-pipe init) doesn't
 * throw.
 */
class CharacterPipelineBudgetTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-character-budget-test-only")
        }

        private val expectedBudget = TokenBudgetSettings(
            contextWindowSize = 512_000,
            maxTokens = 12_000,
            reasoningBudget = null,
            userPromptSize = null,
            allowUserPromptTruncation = false,
            compressUserPrompt = false,
            truncateContextWindowAsString = false,
            preserveTextMatches = true,
            multiPageBudgetStrategy = MultiPageBudgetStrategy.DYNAMIC_SIZE_FILL
        )

        private fun assertPipeHasCanonicalBudget(pipeName: String, pipeline: com.TTT.Pipeline.Pipeline) {
            val pipes = pipeline.getPipes()
            assertTrue(pipes.isNotEmpty(), "$pipeName returned no pipes")

            pipes.forEach { pipe ->
                val settings = pipe.getTokenBudgetSettings()
                assertNotNull(settings, "Pipe ${pipe.pipeName} missing TokenBudgetSettings")
                assertEquals(
                    expectedBudget.contextWindowSize, settings!!.contextWindowSize,
                    "$pipeName: contextWindowSize on pipe ${pipe.pipeName}"
                )
                assertEquals(
                    expectedBudget.maxTokens, settings.maxTokens,
                    "$pipeName: maxTokens on pipe ${pipe.pipeName}"
                )
                assertEquals(
                    expectedBudget.allowUserPromptTruncation, settings.allowUserPromptTruncation,
                    "$pipeName: allowUserPromptTruncation on pipe ${pipe.pipeName} (must be false)"
                )
                assertEquals(
                    expectedBudget.compressUserPrompt, settings.compressUserPrompt,
                    "$pipeName: compressUserPrompt on pipe ${pipe.pipeName} (must be false)"
                )
                assertNull(
                    settings.reasoningBudget,
                    "$pipeName: reasoningBudget on pipe ${pipe.pipeName} must be null"
                )
                assertNull(
                    settings.userPromptSize,
                    "$pipeName: userPromptSize on pipe ${pipe.pipeName} must be null"
                )
            }
        }
    }

    @Test
    fun buildCharacterPipelineBudgetIsCanonical() {
        val pipeline = buildCharacterPipeline("You are a helpful assistant.")
        assertPipeHasCanonicalBudget("buildCharacterPipeline", pipeline)
    }

    @Test
    fun buildCharacterPipelineWithStoryBudgetIsCanonical() {
        val pipeline = buildCharacterPipelineWithStory("You are a helpful assistant.")
        assertPipeHasCanonicalBudget("buildCharacterPipelineWithStory", pipeline)
    }

    @Test
    fun allowUserPromptTruncationIsFalseEverywhere() {
        // This was the load-bearing regression the audit caught: the old
        // writerBudgetSettings had allowUserPromptTruncation = TRUE which
        // would silently truncate the user prompt if it got too long.
        val plain = buildCharacterPipeline("x").getPipes()
        val withStory = buildCharacterPipelineWithStory("x").getPipes()

        (plain + withStory).forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings()
            assertNotNull(settings, "Pipe ${pipe.pipeName} missing budget")
            assertEquals(
                false, settings!!.allowUserPromptTruncation,
                "Pipe ${pipe.pipeName} allowUserPromptTruncation = TRUE would " +
                "silently truncate the user prompt. The 2026-08-09 audit " +
                "catched this regression in the old CharacterPipeline; the " +
                "modernized characterPipelineBudget must keep it FALSE."
            )
        }
    }

    @Test
    fun maxTokensIsTwelveThousand() {
        // Old value was 8000 which cut off longer character responses
        // mid-sentence. Modernized value is 12000.
        val plain = buildCharacterPipeline("x").getPipes()
        val withStory = buildCharacterPipelineWithStory("x").getPipes()

        (plain + withStory).forEach { pipe ->
            val settings = pipe.getTokenBudgetSettings()
            assertNotNull(settings, "Pipe ${pipe.pipeName} missing budget")
            assertEquals(
                12_000, settings!!.maxTokens,
                "Pipe ${pipe.pipeName} maxTokens must be 12000 (was 8000 in old writerBudgetSettings)"
            )
        }
    }

    @Test
    fun characterPipelineBudgetObjectMatchesPerPipeBudget() {
        // The pipeline budget object and the per-pipe budget applied via
        // the forEach block must agree — otherwise the forEach block is
        // not actually doing anything (or someone changed one and not
        // the other). This pins the contract.
        val pipeline = buildCharacterPipeline("x")
        val pipeBudget = pipeline.getPipes().first().getTokenBudgetSettings()
        val objectBudget = characterPipelineBudget

        assertNotNull(pipeBudget, "Per-pipe budget is null")
        assertEquals(
            objectBudget.contextWindowSize, pipeBudget!!.contextWindowSize,
            "Pipeline budget object and per-pipe budget disagree on contextWindowSize"
        )
        assertEquals(
            objectBudget.maxTokens, pipeBudget.maxTokens,
            "Pipeline budget object and per-pipe budget disagree on maxTokens"
        )
        assertEquals(
            objectBudget.allowUserPromptTruncation, pipeBudget.allowUserPromptTruncation,
            "Pipeline budget object and per-pipe budget disagree on allowUserPromptTruncation"
        )
    }
}