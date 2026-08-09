package Builders

import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the contract that every pipe in every modernized pipeline has
 * disablePipe = false explicitly set after construction. This makes
 * the framework-level per-pipe enable/disable feature safe by default:
 * a future user who calls setDisablePipe(true) on a pipe knows the
 * baseline is enabled, not disabled.
 *
 * The framework feature lives in TPipe/TPipe/src/main/kotlin/Pipe/Pipe.kt
 * (var disablePipe = false; fun setDisablePipe(state: Boolean) : Pipe).
 * The TPipeWriter-side plumbing applies it via getPipes().forEach blocks
 * in each pipeline builder, mirroring the modernized per-pipe budget +
 * lore + tracking pattern.
 *
 * Pure reflection — does NOT call init() or hit the network.
 */
class DisablePipeDefaultTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-disable-pipe-default-test-only")
        }
    }

    @Test
    fun plusWriterPipesAreEnabledByDefault() {
        val pipeline = buildPlusWriterPipeline()
        assertAllPipesEnabled(pipeline.getPipes(), "PlusWriter")
    }

    @Test
    fun chapterRewritePipesAreEnabledByDefault() {
        val pipeline = buildChapterRewritePipeline()
        assertAllPipesEnabled(pipeline.getPipes(), "ChapterRewrite")
    }

    @Test
    fun expansionPipelinePipesAreEnabledByDefault() {
        val pipeline = buildExpansionPipeline()
        assertAllPipesEnabled(pipeline.getPipes(), "ExpansionPipeline")
    }

    @Test
    fun dialogueConnectorPipesAreEnabledByDefault() {
        val pair = buildDialogueConnector()
        // DialogueConnector returns Pair<Pipeline, Connector>; the pipes
        // live across evaluateDialoguePipeline + the Connector branches.
        val evaluate = pair.first
        assertAllPipesEnabled(evaluate.getPipes(), "DialogueConnector.evaluateDialogue")
    }

    @Test
    fun characterPipelinePipesAreEnabledByDefault() {
        val plain = buildCharacterPipeline("test")
        val withStory = buildCharacterPipelineWithStory("test")
        assertAllPipesEnabled(plain.getPipes(), "CharacterPipeline")
        assertAllPipesEnabled(withStory.getPipes(), "CharacterPipelineWithStory")
    }

    @Test
    fun advancedWriterPipelinePipesAreEnabledByDefault() {
        val pipeline = buildNccWriter()
        assertAllPipesEnabled(pipeline.getPipes(), "AdvancedWriterPipeline")
    }

    @Test
    fun pitchSlideWriterPipelinePipesAreEnabledByDefault() {
        val pipeline = buildPitchSlideWriterPipeline()
        assertAllPipesEnabled(pipeline.getPipes(), "PitchSlideWriterPipeline")
    }

    /**
     * Helper that asserts every pipe in the list has disablePipe = false
     * explicitly (not just the framework default — the test fails if the
     * forEach block that sets it false is ever removed from a pipeline
     * builder).
     */
    private fun assertAllPipesEnabled(pipes: List<com.TTT.Pipe.Pipe>, pipelineName: String) {
        assertTrue(
            pipes.isNotEmpty(),
            "$pipelineName returned no pipes"
        )
        for (pipe in pipes) {
            val disabled = pipe.disablePipe
            assertEquals(
                false, disabled,
                "Pipe '${pipe.pipeName}' in $pipelineName has disablePipe = true. " +
                "The forEach block that sets disablePipe = false on every pipe " +
                "must run for every pipeline builder. If you removed the forEach, " +
                "the framework-level per-pipe enable/disable feature won't have " +
                "the 'enabled by default' baseline the codebase assumes."
            )
        }
    }
}