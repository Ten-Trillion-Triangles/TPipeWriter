package Shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

/**
 * Tests for the [TPipeSettings] data class and its serialization contract.
 *
 * Pins three properties:
 *  - default constructor produces editorGuide == ""
 *  - copy(editorGuide = ...) round-trips through JSON
 *  - old settings.json without the editorGuide field deserializes with
 *    editorGuide == "" (backward compatibility)
 */
class TPipeSettingsTest
{
    @Test
    fun defaultHasEmptyEditorGuide()
    {
        val settings = TPipeSettings()
        assertEquals("", settings.editorGuide)
    }

    @Test
    fun copyWithEditorGuideRoundTripsThroughJson()
    {
        val original = TPipeSettings(editorGuide = "EDITOR_CONTENT")
        val json = Json.encodeToString(TPipeSettings.serializer(), original)
        val decoded = Json.decodeFromString(TPipeSettings.serializer(), json)
        assertEquals("EDITOR_CONTENT", decoded.editorGuide)
    }

    @Test
    fun oldSettingsJsonWithoutEditorGuideDeserializesWithEmptyDefault()
    {
        // JSON shape matching the pre-Task-2 field set. Missing editorGuide.
        val oldJson = """
            {
              "writingStyle": "",
              "temperature": 1.0,
              "topP": 0.9,
              "maxTokens": 5000,
              "useAutoLorebook": true,
              "authorGuide": "OLD_AUTHOR",
              "competingAuthorGuide": "OLD_TREADWELL",
              "chapterGuide": "",
              "storyGuide": ""
            }
        """.trimIndent()
        val decoded = Json.decodeFromString(TPipeSettings.serializer(), oldJson)
        assertEquals("", decoded.editorGuide,
            "Old settings.json must deserialize with editorGuide defaulting to empty")
        assertEquals("OLD_AUTHOR", decoded.authorGuide)
        assertEquals("OLD_TREADWELL", decoded.competingAuthorGuide)
    }

    @Test
    fun allFieldsCoexistOnCopy()
    {
        val settings = TPipeSettings(
            writingStyle = "WS",
            temperature = 0.7,
            topP = 0.5,
            maxTokens = 1234,
            useAutoLorebook = false,
            authorGuide = "A",
            competingAuthorGuide = "T",
            chapterGuide = "C",
            storyGuide = "S",
            editorGuide = "E"
        )
        assertEquals("WS", settings.writingStyle)
        assertEquals(0.7, settings.temperature)
        assertEquals(0.5, settings.topP)
        assertEquals(1234, settings.maxTokens)
        assertEquals(false, settings.useAutoLorebook)
        assertEquals("A", settings.authorGuide)
        assertEquals("T", settings.competingAuthorGuide)
        assertEquals("C", settings.chapterGuide)
        assertEquals("S", settings.storyGuide)
        assertEquals("E", settings.editorGuide)
    }

    @Test
    fun serializerIsNotNull()
    {
        // Sanity: the @kotlinx.serialization.Serializable annotation on
        // TPipeSettings must produce a non-null serializer after compile.
        assertNotNull(TPipeSettings.serializer())
    }
}
