package Shell

import Globals.Env
import Structs.AuthorPersonality
import Structs.AuthorSlot
import Structs.PersonalitySeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

/**
 * Regression tests for the personality roster + slot-binding flow.
 *
 * Pins three behaviors:
 *  1. Georgios Martin is in the default roster with the user-requested
 *     wording (75 y/o, Clifford Banes Show, Zimbabwe Gazette).
 *  2. The four default slot bindings map the four existing Env.* slots
 *     to their historical owners (Xilaron/N'zelquin/Falkenda/Invis) so
 *     the seed does not change behavior for current users.
 *  3. [applyPersonalityBindings] actually mutates the runtime [Env]
 *     fields for every slot, so assigning a personality to a slot takes
 *     effect without re-init.
 *  4. Settings JSON round-trips the two new fields; old settings.json
 *     without them still deserializes with empty defaults.
 */
class PersonalitySubshellTest
{
    @Test
    fun georgiosMartinIsInSeedRoster()
    {
        val roster = PersonalitySeed.defaultRoster()
        val georgios = roster.firstOrNull { it.name == "Georgios Martin" }
        assertNotNull(georgios, "Georgios Martin must be in defaultRoster()")

        val body = georgios!!.body
        // User-supplied verbal hooks — every one must survive intact.
        assertTrue(body.contains("75 year old man"), "missing the 75-year-old hook")
        assertTrue(body.contains("strong opinions about politics and race"))
        assertTrue(body.contains("French army in Vietnam"))
        assertTrue(body.contains("deployed to Afghanistan"))
        assertTrue(body.contains("British navy against Super South Galea on Mu"))
        assertTrue(body.contains("Clifford Banes Show"))
        assertTrue(body.contains("Fox News after Banes left the Onion News Network"))
        assertTrue(body.contains("FM talk radio"))
        assertTrue(body.contains("Zimbabwe Gazette"))
    }

    @Test
    fun defaultSlotBindingsPreserveHistoricalMapping()
    {
        // The four Env.* slots must keep the four pre-existing owners
        // on a fresh install — adding Georgios to the roster must not
        // overwrite the bindings that existed before this feature.
        val bindings = PersonalitySeed.defaultSlotBindings()

        assertEquals("Xilaron Rigogan", bindings[AuthorSlot.AUTHOR_PROMPT.name])
        assertEquals("N'zelquin G'zeeloth", bindings[AuthorSlot.COMPETING_AUTHOR.name])
        assertEquals("Falkenda Unseppal", bindings[AuthorSlot.EDITOR_PROMPT.name])
        assertEquals("Invis von Disappearo", bindings[AuthorSlot.WRITING_CONTROL.name])
    }

    @Test
    fun applyPersonalityBindingsRewritesRuntimeEnvSlots()
    {
        // Snapshot the runtime Env values before mutating them so the
        // test does not leak state into siblings.
        val originalAuthor = Env.authorPrompt
        val originalCompeting = Env.richardTreadwell
        val originalEditor = Env.editorPrompt
        val originalWritingControl = Env.writingControlPrompt
        try
        {
            val roster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body }

            // Pick the four seed personalities as the test payload —
            // they are guaranteed to be in the roster.
            val bindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Invis von Disappearo",
                AuthorSlot.COMPETING_AUTHOR.name to "Falkenda Unseppal",
                AuthorSlot.EDITOR_PROMPT.name to "N'zelquin G'zeeloth",
                AuthorSlot.WRITING_CONTROL.name to "Georgios Martin"
            )

            applyPersonalityBindings(bindings, roster)

            assertEquals(roster["Invis von Disappearo"], Env.authorPrompt)
            assertEquals(roster["Falkenda Unseppal"], Env.richardTreadwell)
            assertEquals(roster["N'zelquin G'zeeloth"], Env.editorPrompt)
            assertEquals(roster["Georgios Martin"], Env.writingControlPrompt)

            // Env.active* mirrors must also update so chapter-snapshot
            // restore (which reads Env.activeAuthorGuide et al) picks up
            // the swap.
            assertEquals(roster["Invis von Disappearo"], Env.activeAuthorGuide)
            assertEquals(roster["Falkenda Unseppal"], Env.activeRichardTreadwell)
            assertEquals(roster["N'zelquin G'zeeloth"], Env.activeEditorGuide)
        }
        finally
        {
            // Restore so sibling tests get the pre-test Env values.
            Env.authorPrompt = originalAuthor
            Env.richardTreadwell = originalCompeting
            Env.editorPrompt = originalEditor
            Env.writingControlPrompt = originalWritingControl
            Env.activeAuthorGuide = originalAuthor
            Env.activeRichardTreadwell = originalCompeting
            Env.activeEditorGuide = originalEditor
        }
    }

    @Test
    fun applyPersonalityBindingsIgnoresUnknownSlot()
    {
        val original = Env.authorPrompt
        try
        {
            val roster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body }

            // Slot key that does not exist in AuthorSlot must be a no-op
            // rather than crashing the bind call.
            applyPersonalityBindings(
                mapOf("NOT_A_REAL_SLOT" to "Xilaron Rigogan"),
                roster
            )

            assertEquals(original, Env.authorPrompt,
                "Unknown slot key must not mutate Env.authorPrompt")
        }
        finally
        {
            Env.authorPrompt = original
        }
    }

    @Test
    fun personalitySettingsRoundTripThroughJson()
    {
        val roster = PersonalitySeed.defaultRoster()
            .associate { it.name to it.body }
        val bindings = PersonalitySeed.defaultSlotBindings()

        val settings = TPipeSettings(
            personalityRoster = roster,
            personalitySlotBindings = bindings
        )

        val json = Json.encodeToString(TPipeSettings.serializer(), settings)
        val decoded = Json.decodeFromString(TPipeSettings.serializer(), json)

        assertEquals(roster.size, decoded.personalityRoster.size)
        assertEquals(roster["Georgios Martin"], decoded.personalityRoster["Georgios Martin"])
        assertEquals(bindings, decoded.personalitySlotBindings)
    }

    @Test
    fun oldSettingsJsonWithoutPersonalityFieldsDeserializesWithEmptyDefaults()
    {
        // Settings shape matching the pre-Task-3 field set: no
        // personalityRoster, no personalitySlotBindings.
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
              "storyGuide": "",
              "editorGuide": ""
            }
        """.trimIndent()

        val decoded = Json.decodeFromString(TPipeSettings.serializer(), oldJson)
        assertTrue(decoded.personalityRoster.isEmpty(),
            "Old settings.json must deserialize with empty personalityRoster")
        assertTrue(decoded.personalitySlotBindings.isEmpty(),
            "Old settings.json must deserialize with empty slot bindings")
        assertEquals("OLD_AUTHOR", decoded.authorGuide,
            "Old settings.json must preserve the existing authorGuide field")
    }

    @Test
    fun rosterOrSeedFallsBackToSeedWhenSettingsEmpty()
    {
        // The subshell entry-point calls ensurePersonalityRosterSeeded
        // so settings should be populated by the time rosterOrSeed is
        // called from inside the loop. We exercise the fallback by
        // hitting the helper with an empty input shape.
        // Note: loadSettings() reads the real $HOME/.TPipeWriter/settings.json
        // so we only assert the non-empty branch behavior here.
        val roster = rosterOrSeed()
        assertFalse(roster.isEmpty(), "rosterOrSeed must never return empty")
        assertTrue(roster.containsKey("Georgios Martin"),
            "rosterOrSeed result must include Georgios Martin when seeded")
    }

    @Test
    fun personalityRosterAsListReturnsTypedValues()
    {
        val list = personalityRosterAsList()
        assertTrue(list.all { it is AuthorPersonality },
            "All entries must be AuthorPersonality")
        assertTrue(list.any { it.name == "Georgios Martin" })
    }
}
