package Shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for the pure parser that backs the /editor subshell.
 *
 * [parseEditorCommand] takes raw user input and returns the typed command
 * the subshell loop should execute. This isolates the dispatch logic from
 * the interactive I/O (readEnhancedInput / readln) so we can test it
 * exhaustively without a TTY.
 */
class EditorSubshellTest
{
    @Test
    fun numberOneIsSaveEditorGuide()
    {
        assertEquals(EditorCommand.SAVE, parseEditorCommand("1"))
    }

    @Test
    fun numberTwoIsLoadEditorGuide()
    {
        assertEquals(EditorCommand.LOAD, parseEditorCommand("2"))
    }

    @Test
    fun numberThreeIsBack()
    {
        assertEquals(EditorCommand.BACK, parseEditorCommand("3"))
    }

    @Test
    fun backKeywordReturnsBack()
    {
        assertEquals(EditorCommand.BACK, parseEditorCommand("back"))
        assertEquals(EditorCommand.BACK, parseEditorCommand("BACK"))
        assertEquals(EditorCommand.BACK, parseEditorCommand("Back"))
    }

    @Test
    fun exitKeywordReturnsBack()
    {
        assertEquals(EditorCommand.BACK, parseEditorCommand("exit"))
    }

    @Test
    fun qKeywordReturnsBack()
    {
        assertEquals(EditorCommand.BACK, parseEditorCommand("q"))
        assertEquals(EditorCommand.BACK, parseEditorCommand("Q"))
    }

    @Test
    fun whitespaceTolerated()
    {
        assertEquals(EditorCommand.SAVE, parseEditorCommand("  1  "))
        assertEquals(EditorCommand.LOAD, parseEditorCommand("\t2"))
    }

    @Test
    fun unknownNumberIsInvalid()
    {
        assertEquals(EditorCommand.INVALID, parseEditorCommand("0"))
        assertEquals(EditorCommand.INVALID, parseEditorCommand("4"))
        assertEquals(EditorCommand.INVALID, parseEditorCommand("99"))
    }

    @Test
    fun nonNumericIsInvalid()
    {
        assertEquals(EditorCommand.INVALID, parseEditorCommand(""))
        assertEquals(EditorCommand.INVALID, parseEditorCommand("hello"))
        assertEquals(EditorCommand.INVALID, parseEditorCommand("save"))
    }
}
