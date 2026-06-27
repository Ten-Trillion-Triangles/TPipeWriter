package Chapter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [Chapter.capturePersonalitySnapshot] — the pure function that
 * captures the three active writer personalities into a ChapterMetadata
 * snapshot at chapter save time.
 *
 * Decoupled from [Globals.Env] via an injected snapshot provider so the
 * test runs without spinning up the Env singleton.
 */
class ChapterPersonalitySnapshotTest
{
    @Test
    fun capturesAllThreeFields()
    {
        val metadata = Chapter.capturePersonalitySnapshot(
            existing = ChapterMetadata(title = "Chapter 1"),
            authorPrompt = "AUTHOR_TEXT",
            editorPrompt = "EDITOR_TEXT",
            richardTreadwell = "TREADWELL_TEXT"
        )
        assertEquals("AUTHOR_TEXT", metadata.authorPromptSnapshot)
        assertEquals("EDITOR_TEXT", metadata.editorPromptSnapshot)
        assertEquals("TREADWELL_TEXT", metadata.richardTreadwellSnapshot)
        assertEquals("Chapter 1", metadata.title,
            "Existing fields must be preserved")
    }

    @Test
    fun emptyStringsArePreserved()
    {
        val metadata = Chapter.capturePersonalitySnapshot(
            existing = ChapterMetadata(),
            authorPrompt = "",
            editorPrompt = "",
            richardTreadwell = ""
        )
        assertEquals("", metadata.authorPromptSnapshot)
        assertEquals("", metadata.editorPromptSnapshot)
        assertEquals("", metadata.richardTreadwellSnapshot)
    }

    @Test
    fun partialSnapshotsLeaveOthersUntouched()
    {
        val metadata = Chapter.capturePersonalitySnapshot(
            existing = ChapterMetadata(),
            authorPrompt = "ONLY_AUTHOR",
            editorPrompt = "",
            richardTreadwell = ""
        )
        assertEquals("ONLY_AUTHOR", metadata.authorPromptSnapshot)
        assertEquals("", metadata.editorPromptSnapshot)
        assertEquals("", metadata.richardTreadwellSnapshot)
    }

    //==== applyPersonalitySnapshot =======================================

    @Test
    fun applyReplacesAllWhenAllSnapshotsPresent()
    {
        val metadata = ChapterMetadata(
            authorPromptSnapshot = "NEW_AUTHOR",
            editorPromptSnapshot = "NEW_EDITOR",
            richardTreadwellSnapshot = "NEW_TREADWELL"
        )
        val (author, editor, treadwell) = Chapter.applyPersonalitySnapshot(
            metadata = metadata,
            currentAuthorPrompt = "OLD_AUTHOR",
            currentEditorPrompt = "OLD_EDITOR",
            currentRichardTreadwell = "OLD_TREADWELL"
        )
        assertEquals("NEW_AUTHOR", author)
        assertEquals("NEW_EDITOR", editor)
        assertEquals("NEW_TREADWELL", treadwell)
    }

    @Test
    fun applyLeavesEmptySnapshotsUntouched()
    {
        val metadata = ChapterMetadata() // all snapshots empty
        val (author, editor, treadwell) = Chapter.applyPersonalitySnapshot(
            metadata = metadata,
            currentAuthorPrompt = "CURRENT_AUTHOR",
            currentEditorPrompt = "CURRENT_EDITOR",
            currentRichardTreadwell = "CURRENT_TREADWELL"
        )
        assertEquals("CURRENT_AUTHOR", author,
            "Empty snapshot must leave current value alone")
        assertEquals("CURRENT_EDITOR", editor)
        assertEquals("CURRENT_TREADWELL", treadwell)
    }

    @Test
    fun applyReplacesOnlyNonEmptySnapshots()
    {
        val metadata = ChapterMetadata(
            authorPromptSnapshot = "NEW_AUTHOR",
            editorPromptSnapshot = "",
            richardTreadwellSnapshot = ""
        )
        val (author, editor, treadwell) = Chapter.applyPersonalitySnapshot(
            metadata = metadata,
            currentAuthorPrompt = "CURRENT_AUTHOR",
            currentEditorPrompt = "CURRENT_EDITOR",
            currentRichardTreadwell = "CURRENT_TREADWELL"
        )
        assertEquals("NEW_AUTHOR", author)
        assertEquals("CURRENT_EDITOR", editor,
            "Empty editor snapshot must leave current editor alone")
        assertEquals("CURRENT_TREADWELL", treadwell)
    }
}
