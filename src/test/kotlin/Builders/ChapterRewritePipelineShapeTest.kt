package Builders

import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the contract that every pipe in ChapterRewritePipeline emits the
 * uniform SurgicalChangeList schema (or, for the analysisPipe and rewritePipe
 * which produce or modify prose, a different but well-defined role).
 *
 * Mirrors the spirit of PlusWriterPipelineBudgetTest — pure reflection,
 * no init(), no network.
 *
 * Contract:
 * - The analysisPipe is the PLAN producer: its jsonOutput describes the
 *   SurgicalChangeList schema (it produces the plan for downstream pipes).
 * - The rewritePipe is the PRODUCER: it generates the rewritten chapter
 *   prose. Its transformer is a bank-write lambda (writes to
 *   ContextBank['rewrittenChapter']); its jsonOutput is empty (no JSON
 *   schema, just prose).
 * - All other pipes (untwistPipe, noParallelNegationPipe,
 *   removeBadWritingStepOnePipe, removeBadWritingStepTwoPipe, loreCheckPipe,
 *   loreRepairPipe, logicalProgressionPipe, logicalCorrectionPipe,
 *   styleCheckPipe, styleSuggestPipe, styleFixPipe) emit SurgicalChangeList
 *   and apply patches via applySurgicalReplacementsAndBank (or, for the
 *   styleCheckPipe, use a passPipeline gate based on the changeList).
 */
class ChapterRewritePipelineShapeTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-shape-test-only")
        }
    }

    @Test
    fun pipelineHasFourteenPipes() {
        // 6 original pipes (analysis, loreValidation, rewrite, styleCheck,
        // styleSuggest, styleFix) + 8 new defensive pipes (untwist,
        // noParallelNegation, removeBadWriting1, removeBadWriting2,
        // loreCheck, loreRepair, logicalProgression, logicalCorrection)
        val pipeline = buildChapterRewritePipeline()
        val pipes = pipeline.getPipes()
        assertTrue(
            pipes.size >= 14,
            "Expected >= 14 pipes (6 original + 8 new defensive), got ${pipes.size}"
        )
    }

    @Test
    fun everyPipeHasAName() {
        val pipeline = buildChapterRewritePipeline()
        pipeline.getPipes().forEach { pipe ->
            assertNotNull(pipe.pipeName, "Pipe is missing pipeName")
            assertTrue(pipe.pipeName.isNotBlank(), "Pipe name is blank: ${pipe.pipeName}")
        }
    }

    @Test
    fun everyPipeHasAJsonOutputSchema() {
        // The rewritePipe is the only pipe that produces prose (not JSON),
        // so it has an empty jsonOutput. Every other pipe emits either a
        // SurgicalChangeList or, for analysisPipe/loreValidationPipe which
        // are plan producers, also SurgicalChangeList.
        val pipeline = buildChapterRewritePipeline()
        pipeline.getPipes().forEach { pipe ->
            // Every pipe MUST have a jsonOutput field set, even if empty
            // for the rewritePipe.
            val jsonOutput = pipe.jsonOutput
            // rewritePipe has empty jsonOutput (produces prose, not JSON)
            // every other pipe has a non-empty jsonOutput
            if (pipe.pipeName != "Rewrite Pipe") {
                assertTrue(
                    jsonOutput.isNotEmpty(),
                    "Pipe '${pipe.pipeName}' has empty jsonOutput"
                )
            }
        }
    }

    @Test
    fun surgicalPipesEmbedChangeListSchema() {
        // Pipes that emit SurgicalChangeList should embed the changeList
        // field name in their jsonOutput (examplePromptFor serializes the
        // data class fields into a JSON example).
        val pipeline = buildChapterRewritePipeline()
        val surgicalPipeNames = setOf(
            "Analysis pipe",
            "Lore Validation Pipe",
            "untwist pipe",
            "no parallel negation pipe",
            "remove bad writing step one pipe",
            "remove bad writing step two pipe",
            "lore check pipe",
            "lore repair pipe",
            "logical progression pipe",
            "logical correction pipe",
            "Style Check Pipe",
            "Style suggest pipe",
            "Style repair pipe"
        )

        pipeline.getPipes().forEach { pipe ->
            if (surgicalPipeNames.contains(pipe.pipeName)) {
                assertTrue(
                    pipe.jsonOutput.contains("changeList"),
                    "Pipe '${pipe.pipeName}' jsonOutput should contain 'changeList' " +
                    "(SurgicalChangeList field). Got: ${pipe.jsonOutput.take(200)}"
                )
            }
        }
    }

    @Test
    fun everyPipeHasATransformationFunction() {
        // Every pipe should have a transformer — either
        // applySurgicalReplacementsAndBank (surgical patches),
        // recordAuthorPlan (plan storage), or a bank-write lambda
        // (rewritePipe). Judge pipes (logicalProgressionPipe) use a
        // bank-shape pre-validation hook instead, but still emit JSON
        // that the next pipe transforms.
        //
        // Pipe.transformationFunction is a public `var` on
        // com.TTT.Pipe.Pipe (Pipe.kt:1708) so we can read it directly.
        val pipeline = buildChapterRewritePipeline()
        val judgePipes = setOf(
            // Pipes that judge but don't transform — their output is read
            // by the next pipe (which DOES transform).
            "logical progression pipe"
        )
        pipeline.getPipes().forEach { pipe ->
            if (judgePipes.contains(pipe.pipeName)) {
                // Judge pipes can have null transformationFunction
                // (they're pure judges; the next pipe applies the result).
                return@forEach
            }
            assertNotNull(
                pipe.transformationFunction,
                "Pipe '${pipe.pipeName}' has no transformationFunction"
            )
        }
    }
}