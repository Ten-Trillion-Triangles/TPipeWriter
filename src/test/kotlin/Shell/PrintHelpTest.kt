package Shell

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the contract of [printHelp]'s output. The /help command must
 * surface every slash command the shell actually accepts, including
 * the runtime-overridable personality commands /author and /editor
 * that were added by the personality-runtime-overrides feature.
 *
 * We capture stdout via [setOut] so we don't depend on a TTY.
 */
class PrintHelpTest
{

    @Test
    fun helpListsAuthorCommand()
    {
        val out = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(out))
        try {
            printHelp()
        }
        finally {
            System.setOut(original)
        }
        val text = out.toString()
        assertTrue(
            text.contains("/author"),
            "printHelp must mention /author so users can discover the " +
                    "author-personality save/load subshell"
        )
    }

    @Test
    fun helpListsEditorCommand()
    {
        val out = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(out))
        try {
            printHelp()
        }
        finally {
            System.setOut(original)
        }
        val text = out.toString()
        assertTrue(
            text.contains("/editor"),
            "printHelp must mention /editor so users can discover the " +
                    "editor-personality save/load subshell"
        )
    }

    @Test
    fun helpListsGuideCommand()
    {
        val out = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(out))
        try {
            printHelp()
        }
        finally {
            System.setOut(original)
        }
        val text = out.toString()
        assertTrue(
            text.contains("/guide"),
            "printHelp must continue to mention /guide"
        )
    }
}