package Builders

import com.TTT.Pipe.Pipe
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the contract that all 4 pipes in DialogueConnector emit the
 * uniform SurgicalChangeList JSON schema (or, for the classify-and-route
 * flow, are correctly shaped for surgical-edit application).
 *
 * Mirrors the spirit of PlusWriterPipelineBudgetTest — pure reflection,
 * no init(), no network.
 *
 * Contract:
 * - identifyMyDialogue: emits SurgicalChangeList (1 entry carrying
 *   dialogueType classification in replacementSubString)
 * - benignSkiesMyDialoguePipe, polishMyDialoguePipe, certifyMyDialoguePipe:
 *   emit SurgicalChangeList with applySurgicalReplacementsAndBank transformer
 *
 * The 3 dialogue-improvers use surgical-edit mode (find/replace patches)
 * rather than prose-producer mode, giving per-change auditability.
 */
class DialogueConnectorShapeTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-shape-test-only")
        }

        /** Helper: collect all 4 pipes from the connector (1 classifier + 3 dialogue-improvers). */
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
    fun connectorReturnsFourPipes() {
        // 1 classifier + 3 dialogue-improvers (informal-casual, informal-serious, formal-freeform)
        val pipes = allPipes()
        assertTrue(
            pipes.size >= 4,
            "Expected >= 4 pipes (1 classifier + 3 dialogue-improvers), got ${pipes.size}"
        )
    }

    @Test
    fun everyPipeHasAName() {
        val pipes = allPipes()
        pipes.forEach { pipe ->
            assertNotNull(pipe.pipeName, "Pipe is missing pipeName")
            assertTrue(pipe.pipeName.isNotBlank(), "Pipe name is blank: ${pipe.pipeName}")
        }
    }

    @Test
    fun everyPipeEmitsSurgicalChangeListJson() {
        // All 4 pipes now emit SurgicalChangeList (uniform with modernized ChapterRewritePipeline)
        val pipes = allPipes()
        pipes.forEach { pipe ->
            // Every pipe MUST have a non-empty jsonOutput (the SurgicalChangeList schema)
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
    fun threeDialogueImproversHaveNonNullTransformationFunction() {
        // The Kotlin lambda's .toString() returns a generic FunctionN signature,
        // not the source function name. We verify the transformer is non-null
        // (the surgical-edit mode wiring is also confirmed via the jsonOutput
        // 'changeList' check in everyPipeEmitsSurgicalChangeListJson above).
        val pipes = allPipes()
        val dialogueImprovers = setOf(
            "benign skies my dialogue pipe",
            "polish my dialogue pipe",
            "certify my dialogue pipe"
        )

        pipes.forEach { pipe ->
            if (dialogueImprovers.contains(pipe.pipeName)) {
                assertNotNull(
                    pipe.transformationFunction,
                    "Dialogue-improver pipe '${pipe.pipeName}' has no transformationFunction"
                )
            }
        }
    }

    @Test
    fun identifyMyDialogueHasNonNullTransformationFunction() {
        val pipes = allPipes()
        val classifier = pipes.firstOrNull { it.pipeName == "identify my dialogue pipe" }
        assertNotNull(classifier, "identify my dialogue pipe not found in connector's pipes")

        assertNotNull(
            classifier!!.transformationFunction,
            "identifyMyDialogue has no transformationFunction"
        )
    }
}