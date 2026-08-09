package Builders

import com.TTT.Pipe.Pipe
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the contract that every pipe in ExpansionPipeline emits the
 * uniform SurgicalChangeList JSON schema (or, for the dialog classifier
 * and prose-judge pipes, are correctly shaped for surgical-edit application).
 *
 * Mirrors the spirit of PlusWriterPipelineBudgetTest and
 * DialogueConnectorShapeTest — pure reflection, no init(), no network.
 *
 * Contract:
 * - All non-prosifier pipes emit SurgicalChangeList (the LLM emits JSON
 *   describing surgical patches)
 * - Prosifier pipes (none — ExpansionPipeline is all-judges + appliers;
 *   the rewrite is generated upstream by PlusWriter)
 * - Defensive passes: 8 total (untwist, noParallelNegation,
 *   removeBadWritingStep1, removeBadWritingStep2, loreCheck,
 *   loreRepair, logicalProgression, logicalCorrection)
 */
class ExpansionPipelineShapeTest
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
    fun pipelineReturnsAtLeastEighteenPipes() {
        // 15 original pipes + 6 new defensive passes (untwist, noParallelNegation,
        // loreCheck, loreRepair, logicalProgression, logicalCorrection) - the 3
        // in-pipeline dialogue-improvers (benignSkies, polish, certify) are still
        // declared but commented out in the chain (see //.add(dialoguePipe) etc.)
        // so they don't reach getPipes() via the chain. Chain has 18 pipes.
        val pipeline = buildExpansionPipeline()
        val pipes = pipeline.getPipes()
        assertTrue(
            pipes.size >= 18,
            "Expected >= 18 pipes (15 declared + 6 new defensive - 3 commented dialogue-improvers), got ${pipes.size}"
        )
    }

    @Test
    fun everyPipeHasAName() {
        val pipeline = buildExpansionPipeline()
        pipeline.getPipes().forEach { pipe ->
            assertNotNull(pipe.pipeName, "Pipe is missing pipeName")
            assertTrue(pipe.pipeName.isNotBlank(), "Pipe name is blank: ${pipe.pipeName}")
        }
    }

    @Test
    fun everyPipeExceptProducersEmitsSurgicalChangeListJson() {
        // All ExpansionPipeline pipes emit SurgicalChangeList EXCEPT
        // routing pipes (shuntPipe — pre-invoke hook that routes to the
        // dialogue-connector; it has no jsonOutput/transformer because it
        // is a pass-through host for the dialogue-connector flow).
        val pipeline = buildExpansionPipeline()
        val routingPipes = setOf("shunt pipe")
        pipeline.getPipes().forEach { pipe ->
            if (routingPipes.contains(pipe.pipeName)) {
                return@forEach
            }
            val jsonOutput = pipe.jsonOutput
            assertTrue(
                jsonOutput.isNotEmpty(),
                "Pipe '${pipe.pipeName}' has empty jsonOutput"
            )
            assertTrue(
                jsonOutput.contains("changeList"),
                "Pipe '${pipe.pipeName}' jsonOutput should contain 'changeList' " +
                "(SurgicalChangeList field). Got: ${jsonOutput.take(200)}"
            )
        }
    }

    @Test
    fun eightDefensivePassesArePresent() {
        val pipeline = buildExpansionPipeline()
        val pipeNames = pipeline.getPipes().map { it.pipeName }.toSet()

        val expectedDefensivePasses = setOf(
            "untwist pipe",
            "no parallel negation pipe",
            "remove bad writing step one pipe",
            "remove bad writing step two pipe",
            "lore check pipe",
            "lore repair pipe",
            "logical progression pipe",
            "logical correction pipe"
        )

        expectedDefensivePasses.forEach { expectedName ->
            assertTrue(
                pipeNames.contains(expectedName),
                "Missing defensive pass: '$expectedName'. " +
                "Found pipeNames: $pipeNames"
            )
        }
    }

    @Test
    fun everySurgicalPipeHasNonNullTransformationFunction() {
        // Every pipe should have a transformer EXCEPT:
        // - routing pipes (shuntPipe — pre-invoke hook; uses preInvokeShunt)
        // - judge pipes (logicalProgressionPipe — emits surgical changes
        //   that the next pipe applies; judge-only, no transform)
        val pipeline = buildExpansionPipeline()
        val nonTransformerPipes = setOf("shunt pipe", "logical progression pipe")
        pipeline.getPipes().forEach { pipe ->
            if (nonTransformerPipes.contains(pipe.pipeName)) {
                return@forEach
            }
            assertNotNull(
                pipe.transformationFunction,
                "Pipe '${pipe.pipeName}' has no transformationFunction"
            )
        }
    }
}