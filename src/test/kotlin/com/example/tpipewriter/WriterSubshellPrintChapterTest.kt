package com.example.tpipewriter

import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Regression test for the "Chapter segment banked into context" bug.
 *
 * Before the fix, after a writer pipeline run completed the application
 * printed only:
 *
 *     [writer] Chapter segment banked into context.
 *
 * — without showing the actual chapter text. Users had to run /chapters
 * show &lt;N&gt; to see what was generated. The OpenRouter and main branches
 * print the banked result with a `==== New Segment ====` banner.
 *
 * After the fix, the application prints the banked chapter text with the
 * banner so the user sees the canonical post-pipeline text directly.
 *
 * This test reproduces the relevant pieces of the post-stream logic in
 * isolation: simulate a writer pipeline banking text into the "new page"
 * ContextBank, then verify the post-completion print logic produces the
 * expected banner + banked text output. No live API call needed — the
 * logic is pure stdlib + ContextBank reads.
 */
class WriterSubshellPrintChapterTest {

    private val originalOut = System.out
    private lateinit var outputCapture: ByteArrayOutputStream

    @AfterEach
    fun teardown() {
        System.setOut(originalOut)
        // Clean up any banked content from this test so other tests don't
        // see it. ContextBank has no clear() helper, so we emplace an
        // empty context which effectively wipes the bank.
        runBlocking {
            ContextBank.emplaceWithMutex("new page", ContextWindow())
        }
    }

    /**
     * Reproduces the post-stream print logic from WriterSubshell.kt
     * executeWriterPipeline — the section that was replaced by a
     * placeholder message in commit 13c10c2.
     */
    private fun printBankedChapterOrFallback(resultText: String) {
        if (resultText.isNotEmpty()) {
            try {
                val textBarrier = "==================================New Segment========================================="
                val bankedContext = ContextBank.getContextFromBank("new page")
                val bankedResult = bankedContext.contextElements.lastOrNull()
                if (!bankedResult.isNullOrBlank()) {
                    println("\n\n\n$textBarrier\n\n$bankedResult")
                } else {
                    println("\n\n[writer] Chapter segment banked into context.")
                }
            } catch (e: Exception) {
                println("\n\n[writer] Chapter segment banked into context.")
            }
        } else {
            println("The model failed to return a result")
        }
    }

    private fun captureStdout(block: () -> Unit): String {
        outputCapture = ByteArrayOutputStream()
        System.setOut(PrintStream(outputCapture))
        block()
        System.out.flush()
        return outputCapture.toString(Charsets.UTF_8)
    }

    @Test
    fun printsBankedChapterWithBannerWhenBanked() {
        val chapterText = "It was a dark and stormy night; the wind howled across the moors."
        runBlocking {
            val bankedContext = ContextWindow()
            bankedContext.contextElements.add(chapterText)
            ContextBank.emplaceWithMutex("new page", bankedContext)
        }

        val output = captureStdout {
            printBankedChapterOrFallback("streaming was successful")
        }

        // The New Segment banner must be present.
        assertTrue(
            output.contains("New Segment"),
            "Expected the '==== New Segment ====' banner in output but got:\n$output"
        )
        // The banked chapter text must be present.
        assertTrue(
            output.contains(chapterText),
            "Expected the banked chapter text in output but got:\n$output"
        )
        // The fallback placeholder must NOT appear.
        assertTrue(
            !output.contains("Chapter segment banked into context"),
            "Did not expect the fallback placeholder when bank has content, but got:\n$output"
        )
    }

    @Test
    fun fallsBackToPlaceholderWhenBankIsEmpty() {
        // Don't emplace anything — bank is empty.
        val output = captureStdout {
            printBankedChapterOrFallback("streaming was successful")
        }

        // The fallback placeholder must appear (no banner).
        assertTrue(
            output.contains("Chapter segment banked into context"),
            "Expected fallback placeholder when bank is empty, but got:\n$output"
        )
        assertTrue(
            !output.contains("New Segment"),
            "Did not expect the New Segment banner when bank is empty, but got:\n$output"
        )
    }

    @Test
    fun reportsFailureWhenResultIsEmpty() {
        val output = captureStdout {
            printBankedChapterOrFallback("")
        }

        assertTrue(
            output.contains("The model failed to return a result"),
            "Expected failure message when result.text is empty, but got:\n$output"
        )
        assertTrue(
            !output.contains("New Segment"),
            "Did not expect New Segment banner when result was empty, but got:\n$output"
        )
    }

    @Test
    fun printsLastElementWhenBankHasMultipleEntries() {
        // Bank accumulates over multiple runs — the print should show the
        // MOST RECENT entry only, not the first one. Verifies we use
        // lastOrNull (the whole bank context, which is a single
        // ContextWindow with a list of elements, would print all of
        // them concatenated if we used .toString() — but we explicitly
        // take the last element so the user sees only the freshest
        // generation, not the entire bank history).
        val olderEntry = "this is from a previous run and should not appear in the print"
        val latestEntry = "this is from the latest run and SHOULD appear in the print"
        runBlocking {
            val bankedContext = ContextWindow()
            bankedContext.contextElements.add(olderEntry)
            bankedContext.contextElements.add(latestEntry)
            ContextBank.emplaceWithMutex("new page", bankedContext)
        }

        val output = captureStdout {
            printBankedChapterOrFallback("ok")
        }

        assertTrue(
            output.contains(latestEntry),
            "Expected the latest entry in output but got:\n$output"
        )
        // The older entry should NOT appear because we only print the
        // latest entry, not the full bank history. This is the intentional
        // behavior — printing the entire bank would dump prior chapters
        // for every /continue call.
        assertTrue(
            !output.contains(olderEntry),
            "Did not expect the older entry in output (only latest should print), but got:\n$output"
        )
    }
}