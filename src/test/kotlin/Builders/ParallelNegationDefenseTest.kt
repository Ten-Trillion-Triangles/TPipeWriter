package Builders

import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Pins the contract that the untwistPipe in PlusWriterPipeline,
 * ChapterRewritePipeline, AND ExpansionPipeline contains the
 * '##STYLE: NO PARALLEL-NEGATION CONSTRUCTS' teaching block.
 *
 * Background: in commit 5044bf4 the parallel-negation defense was
 * added to the modernized pipelines as a separate dedicated pipe
 * (noParallelNegationPipe) for ChapterRewrite + ExpansionPipeline.
 * But the user identified that PlusWriter's untwistPipe never got
 * the same teaching block added to its own system prompt, even
 * though untwistPipe covers the overlapping "it's not X, it's Y"
 * family of tics.
 *
 * This test pins the canonical wording of the teaching block
 * (variant list, positive-assertion instruction, mode='replace'
 * directive) across all three pipelines so the upgrade cannot
 * regress.
 *
 * Does NOT call init() or hit the network. Pure reflection on the
 * built pipeline's pipe list. Fake API key wired in BeforeAll.
 */
class ParallelNegationDefenseTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey() {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-parallel-negation-test-only")
        }

        /**
         * Substring assertions. Each piece is a load-bearing fragment
         * of the teaching block; losing any one of them degrades the
         * defense. Tests assert each fragment independently so that
         * a regression on one fragment fails clearly.
         */
        private val requiredFragments = listOf(
            "##STYLE: NO PARALLEL-NEGATION CONSTRUCTS",
            "positive assertion",
            "\"Not X but Y\"",
            "\"It's not X, it's Y\"",
            "\"Not because A but because B\"",
            "\"Not A but B\"",
            "\"Is not A but is B\"",
            "\"Not A, not B, is C\"",
            "\"Isn't X, but is Y\"",
            "\"It's not A, it's actually a B\"",
            "chimney permit violation",
            // PlusWriter-specific framing: distinguishes parallel-negation
            // from twist removal and directs the LLM to use mode=replace
            // (not mode=delete) because the underlying fact may still
            // matter to the prose.
            "a stronger, separate treatment than mere 'twist removal'",
            "mode is always \"replace\""
        )

        private fun assertUntwistTeachesParallelNegation(pipeName: String, systemPrompt: String) {
            for (fragment in requiredFragments) {
                assertTrue(
                    systemPrompt.contains(fragment),
                    "[$pipeName] untwistPipe system prompt is missing required fragment: " +
                    "\"'${fragment}'\". This indicates the parallel-negation teaching " +
                    "block was lost during a refactor."
                )
            }
        }
    }

    @Test
    fun plusWriterUntwistPipeTeachesParallelNegation() {
        val pipeline = buildPlusWriterPipeline()
        val untwistPipe = pipeline.getPipes().firstOrNull { it.pipeName == "untwist pipe" }
            ?: throw AssertionError("PlusWriter has no 'untwist pipe'")
        assertUntwistTeachesParallelNegation(
            "PlusWriter",
            untwistPipe.getSystemPromptForTest()
        )
    }

    @Test
    fun chapterRewriteUntwistPipeTeachesParallelNegation() {
        val pipeline = buildChapterRewritePipeline()
        val untwistPipe = pipeline.getPipes().firstOrNull { it.pipeName == "untwist pipe" }
            ?: throw AssertionError("ChapterRewrite has no 'untwist pipe'")
        assertUntwistTeachesParallelNegation(
            "ChapterRewrite",
            untwistPipe.getSystemPromptForTest()
        )
    }

    @Test
    fun expansionPipelineUntwistPipeTeachesParallelNegation() {
        val pipeline = buildExpansionPipeline()
        val untwistPipe = pipeline.getPipes().firstOrNull { it.pipeName == "untwist pipe" }
            ?: throw AssertionError("ExpansionPipeline has no 'untwist pipe'")
        assertUntwistTeachesParallelNegation(
            "ExpansionPipeline",
            untwistPipe.getSystemPromptForTest()
        )
    }

    @Test
    fun chapterRewriteNoParallelNegationPipeAlsoHasTheTeaching() {
        // Defense-in-depth: ChapterRewrite has a separate dedicated
        // noParallelNegationPipe that ALSO must teach the rule. If the
        // user ever unhooks the dedicated pipe, this test fails loudly
        // so they know to fall back to the untwistPipe teaching alone.
        val pipeline = buildChapterRewritePipeline()
        val pipe = pipeline.getPipes().firstOrNull { it.pipeName == "no parallel negation pipe" }
            ?: throw AssertionError("ChapterRewrite has no 'no parallel negation pipe'")
        val prompt = pipe.getSystemPromptForTest()
        assertTrue(
            prompt.contains("##STYLE: NO PARALLEL-NEGATION CONSTRUCTS"),
            "ChapterRewrite noParallelNegationPipe is missing the canonical " +
            "teaching header. The pipe exists but the prompt was gutted."
        )
        assertTrue(
            prompt.contains("Never lead with the negation"),
            "ChapterRewrite noParallelNegationPipe is missing the canonical " +
            "positive-assertion instruction."
        )
    }

    @Test
    fun expansionPipelineNoParallelNegationPipeAlsoHasTheTeaching() {
        val pipeline = buildExpansionPipeline()
        val pipe = pipeline.getPipes().firstOrNull { it.pipeName == "no parallel negation pipe" }
            ?: throw AssertionError("ExpansionPipeline has no 'no parallel negation pipe'")
        val prompt = pipe.getSystemPromptForTest()
        assertTrue(
            prompt.contains("##STYLE: NO PARALLEL-NEGATION CONSTRUCTS"),
            "ExpansionPipeline noParallelNegationPipe is missing the canonical " +
            "teaching header."
        )
        assertTrue(
            prompt.contains("Never lead with the negation"),
            "ExpansionPipeline noParallelNegationPipe is missing the canonical " +
            "positive-assertion instruction."
        )
    }
}