package Shell

import readEnhancedInput

/**
 * Typed result of [parseEditorCommand]. The selectEditorMode loop dispatches
 * on this enum so the parser can be unit-tested without a TTY.
 */
enum class EditorCommand
{
    SAVE,
    LOAD,
    BACK,
    INVALID
}

/**
 * Pure parser for the /editor subshell menu. Returns the typed command for
 * known input; returns [EditorCommand.INVALID] for anything else.
 *
 * Accepted forms:
 *   "1"          -> SAVE
 *   "2"          -> LOAD
 *   "3"          -> BACK
 *   "back" | "exit" | "q" (case-insensitive, optionally surrounded by whitespace)
 *                 -> BACK
 *   anything else (empty string, non-numeric, out-of-range number)
 *                 -> INVALID
 */
fun parseEditorCommand(rawInput: String): EditorCommand
{
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return EditorCommand.INVALID

    return when (trimmed.lowercase())
    {
        "1" -> EditorCommand.SAVE
        "2" -> EditorCommand.LOAD
        "3" -> EditorCommand.BACK
        "back", "exit", "q" -> EditorCommand.BACK
        else -> when (val n = trimmed.toIntOrNull())
        {
            null -> EditorCommand.INVALID
            in 1..3 -> when (n)
            {
                1 -> EditorCommand.SAVE
                2 -> EditorCommand.LOAD
                else -> EditorCommand.BACK
            }
            else -> EditorCommand.INVALID
        }
    }
}

/**
 * Interactive subshell loop that lets the user save or load the editor
 * guide (Falkenda Unseppal persona). Mirrors selectAuthorMode but loops
 * instead of returning after one command — that was bug #10 in the
 * June 2 settings-subshell bug hunt.
 */
fun selectEditorMode()
{
    val editorModeEntry = """

        Select one of the following:

        1. Save Editor Guide
        2. Load Editor Guide
        3. Exit
        back - Return to main shell
    """.trimIndent()

    while (true)
    {
        println(editorModeEntry)
        print("editor> ")

        val raw = readEnhancedInput()
        when (parseEditorCommand(raw))
        {
            EditorCommand.SAVE -> saveEditorGuide()
            EditorCommand.LOAD -> loadEditorGuide()
            EditorCommand.BACK -> return
            EditorCommand.INVALID ->
                println("Invalid choice: '$raw'. Enter 1, 2, or 3 (or 'back' to exit).")
        }
    }
}
