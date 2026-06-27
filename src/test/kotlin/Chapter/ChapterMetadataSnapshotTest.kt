package Chapter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

/**
 * Tests for the per-chapter personality snapshot fields on [ChapterMetadata].
 *
 * Pins four properties:
 *  - default constructor produces empty strings for the three snapshot fields.
 *  - copy(...) round-trips through JSON.
 *  - old serialized JSON without the snapshot fields deserializes with
 *    empty-string defaults (backward compatibility with existing saves).
 */
class ChapterMetadataSnapshotTest
{
    @Test
    fun defaultHasEmptySnapshotFields()
    {
        val metadata = ChapterMetadata()
        assertEquals("", metadata.authorPromptSnapshot)
        assertEquals("", metadata.editorPromptSnapshot)
        assertEquals("", metadata.richardTreadwellSnapshot)
    }

    @Test
    fun copyWithSnapshotsRoundTripsThroughJson()
    {
        val metadata = ChapterMetadata(
            title = "Chapter 1",
            authorPromptSnapshot = "AUTHOR",
            editorPromptSnapshot = "EDITOR",
            richardTreadwellSnapshot = "TREADWELL"
        )
        val json = Json.encodeToString(ChapterMetadata.serializer(), metadata)
        val decoded = Json.decodeFromString(ChapterMetadata.serializer(), json)
        assertEquals("AUTHOR", decoded.authorPromptSnapshot)
        assertEquals("EDITOR", decoded.editorPromptSnapshot)
        assertEquals("TREADWELL", decoded.richardTreadwellSnapshot)
        assertEquals("Chapter 1", decoded.title)
    }

    @Test
    fun oldJsonWithoutSnapshotsDeserializesWithEmptyDefaults()
    {
        val oldJson = """
            {
              "title": "Legacy Chapter",
              "tags": [],
              "wordCount": 100,
              "createdAt": "2026-01-01",
              "lastModified": "2026-01-02"
            }
        """.trimIndent()
        val decoded = Json.decodeFromString(ChapterMetadata.serializer(), oldJson)
        assertEquals("Legacy Chapter", decoded.title)
        assertEquals("", decoded.authorPromptSnapshot,
            "Old ChapterMetadata JSON must deserialize with authorPromptSnapshot defaulting to empty")
        assertEquals("", decoded.editorPromptSnapshot)
        assertEquals("", decoded.richardTreadwellSnapshot)
    }

    @Test
    fun partialSnapshotsLeaveOthersEmpty()
    {
        val metadata = ChapterMetadata(
            authorPromptSnapshot = "ONLY_AUTHOR"
        )
        assertEquals("ONLY_AUTHOR", metadata.authorPromptSnapshot)
        assertEquals("", metadata.editorPromptSnapshot)
        assertEquals("", metadata.richardTreadwellSnapshot)
    }
}
