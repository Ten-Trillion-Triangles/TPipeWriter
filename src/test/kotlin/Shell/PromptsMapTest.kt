package Shell

import Globals.Prompts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the contract of [Globals.Prompts.promptMap] as the roster
 * surfaced by `/character` -> `list` and `character <slug>`.
 *
 * Each character in the map is identified by a short slug (e.g. "fu"
 * for Falkenda Unseppal) keyed to the prose prompt used to build that
 * character's chat pipeline.
 */
class PromptsMapTest
{
    @Test
    fun georgiosMartinIsInPromptMap()
    {
        // The slug "gmn" (Georgios Martin) is the lookup key the user
        // types at the `/character` subshell.
        val body = Prompts.promptMap["gmn"]
        assertNotNull(body, "Georgios Martin must be in Prompts.promptMap under slug 'gmn'")
        assertTrue(body!!.contains("75 year old man"),
            "missing the 75-year-old hook")
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
    fun georgiosSlugResolvesCaseInsensitively()
    {
        // Match the lookup behavior in CharacterChatSubshell —
        // setActiveCharacter does .equals(name, ignoreCase = true),
        // so any case variant of "gmn" must resolve.
        val expected = Prompts.promptMap["gmn"]
        assertNotNull(expected)
        assertEquals(expected, Prompts.promptMap.entries
            .firstOrNull { it.key.equals("GMN", ignoreCase = true) }
            ?.value)
        assertEquals(expected, Prompts.promptMap.entries
            .firstOrNull { it.key.equals("Gmn", ignoreCase = true) }
            ?.value)
    }

    @Test
    fun existingCharactersAreStillPresent()
    {
        // Pre-existing roster — adding Georgios must not displace any
        // of these.
        assertTrue(Prompts.promptMap.containsKey("big wang"))
        assertTrue(Prompts.promptMap.containsKey("smarm"))
        assertTrue(Prompts.promptMap.containsKey("nzg"))
        assertTrue(Prompts.promptMap.containsKey("xrg"))
        assertTrue(Prompts.promptMap.containsKey("tg"))
        assertTrue(Prompts.promptMap.containsKey("mnr"))
        assertTrue(Prompts.promptMap.containsKey("parikga"))
        assertTrue(Prompts.promptMap.containsKey("wb"))
        assertTrue(Prompts.promptMap.containsKey("fu"))
        assertTrue(Prompts.promptMap.containsKey("ivd"))
        assertTrue(Prompts.promptMap.containsKey("hcn"))
        assertTrue(Prompts.promptMap.containsKey("zdml"))
        assertTrue(Prompts.promptMap.containsKey("qlb"))
        assertTrue(Prompts.promptMap.containsKey("erg"))
        assertTrue(Prompts.promptMap.containsKey("tvr"))
        assertTrue(Prompts.promptMap.containsKey("big googar"))
    }

    @Test
    fun promptMapKeysAreUnique()
    {
        // No duplicate slugs — slug uniqueness is what makes
        // CharacterChatSubshell's firstOrNull lookup unambiguous.
        val keys = Prompts.promptMap.keys.toList()
        assertEquals(keys.size, keys.toSet().size,
            "Every slug in promptMap must be unique")
    }

    @Test
    fun georgiosBodyIsNotEmpty()
    {
        // A non-empty body ensures the character-chat pipeline can
        // build against this entry instead of falling back to a
        // "no character prompt found" error.
        val body = Prompts.promptMap["gmn"]
        assertNotNull(body)
        assertTrue(body!!.trim().isNotEmpty())
    }
}
