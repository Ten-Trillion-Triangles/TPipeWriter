package Builders

import com.TTT.Context.ContextWindow
import com.TTT.Context.ConverseRole
import com.TTT.Context.buildLorebookScanText
import com.TTT.Pipe.MultimodalContent
import genericOpenAIPipe.env.GenericOpenAIEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Regression for the PlusWriterPipeline lorebook scan-surface contract.
 *
 * Pinned behavior (TPipeWriter PlusWriterPipeline lorebook-scan plan):
 *  - every pipe in PlusWriterPipeline has `useEntireContextForLoreSelection = true`
 *  - that flag flips on `useEntireContextForLoreSelection` in `PipeSettings`,
 *    which causes every lorebook selection/truncation call site in Pipe.kt
 *    to call `ContextWindow.buildLorebookScanText(userPrompt, true)` instead
 *    of the historical `buildLorebookScanText(userPrompt, false)`.
 *  - the helper returns `userPrompt` + `contextElements` + `converseHistory.text`
 *    newline-joined when `useEntireContext = true`.
 *
 * These tests pin BOTH sides of the contract — the per-pipe setting on the
 * live pipeline AND the helper-level behavior on a fresh ContextWindow —
 * because the helper is the single source of truth that the per-pipe flag
 * routes through (see ContextWindow.kt:2292).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlusWriterPipelineLorebookScanTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey()
        {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-lorebook-scan-test-only")
        }
    }

    @Test
    fun everyPipeHasUseEntireContextForLoreSelectionEnabled()
    {
        val pipeline = buildPlusWriterPipeline()
        val pipes = pipeline.getPipes()

        assertTrue(pipes.isNotEmpty(), "PlusWriterPipeline must contain at least one pipe")

        pipes.forEach { pipe ->
            val settings = pipe.toPipeSettings()
            assertNotNull(
                settings.useEntireContextForLoreSelection,
                "Pipe '${pipe.pipeName}' has no useEntireContextForLoreSelection setting"
            )
            assertTrue(
                settings.useEntireContextForLoreSelection!!,
                "Pipe '${pipe.pipeName}' must have useEntireContextForLoreSelection = true"
            )
        }
    }

    @Test
    fun buildLorebookScanTextReturnsUserPromptOnlyWhenUseEntireContextFalse()
    {
        val window = ContextWindow()
        window.contextElements.add("Lyra visited Silverbrook on the autumn equinox.")
        window.converseHistory.add(
            ConverseRole.user,
            MultimodalContent("Tell me about the last time you were in Silverbrook.")
        )
        window.converseHistory.add(
            ConverseRole.agent,
            MultimodalContent("The dust on the southern stacks was almost gold in the lamplight.")
        )

        val userPrompt = "What did Lyra find in the restricted wing?"
        val scanText = window.buildLorebookScanText(userPrompt, false)

        assertEquals(
            userPrompt,
            scanText,
            "When useEntireContext = false, helper must return userPrompt verbatim"
        )
    }

    @Test
    fun buildLorebookScanTextIncludesContextElementsAndConverseHistoryWhenTrue()
    {
        val window = ContextWindow()
        val contextElement = "Lyra first visited the Silverbrook archives on the eve of the autumn equinox."
        window.contextElements.add(contextElement)
        window.converseHistory.add(
            ConverseRole.user,
            MultimodalContent("Tell me about the last time you were in Silverbrook.")
        )
        window.converseHistory.add(
            ConverseRole.agent,
            MultimodalContent("I remember the archives well — the dust on the southern stacks was almost gold in the lamplight.")
        )

        val userPrompt = "What did Lyra find in the restricted wing?"
        val scanText = window.buildLorebookScanText(userPrompt, true)

        val expected = """
            |What did Lyra find in the restricted wing?
            |Lyra first visited the Silverbrook archives on the eve of the autumn equinox.
            |Tell me about the last time you were in Silverbrook.
            |I remember the archives well — the dust on the southern stacks was almost gold in the lamplight.
        """.trimMargin()

        assertEquals(expected, scanText)
        assertTrue(
            scanText.contains(contextElement),
            "Scan text must include contextElements when useEntireContext = true"
        )
    }

    @Test
    fun buildLorebookScanTextSkipsEmptyContextElementsWhenTrue()
    {
        val window = ContextWindow()
        window.converseHistory.add(
            ConverseRole.user,
            MultimodalContent("Hello there.")
        )

        val userPrompt = "Hi."
        val scanText = window.buildLorebookScanText(userPrompt, true)

        assertEquals(
            """
                |Hi.
                |Hello there.
            """.trimMargin(),
            scanText,
            "Empty contextElements must be skipped (no trailing newline)"
        )
        assertFalse(
            scanText.endsWith("\n"),
            "Scan text must not end with newline when last source is non-empty"
        )
    }

    @Test
    fun buildLorebookScanTextWithEmptyContextAndHistoryReturnsUserPrompt()
    {
        val window = ContextWindow()
        val userPrompt = "Just the prompt."

        val scanText = window.buildLorebookScanText(userPrompt, true)

        assertEquals(
            userPrompt,
            scanText,
            "When contextElements and converseHistory are empty, scan text is just userPrompt"
        )
    }
}
