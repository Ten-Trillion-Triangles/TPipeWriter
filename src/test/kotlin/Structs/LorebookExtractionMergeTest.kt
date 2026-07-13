package Structs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LorebookExtractionMergeTest
{
    @Test
    fun `mergeCharacterEntry appends descriptions and dedupes aliases`() {
        val existing = CharacterEntry(
            name = "Shepard",
            description = "Alliance commander.",
            aliases = listOf("Commander", "Shep"),
            status = "alive",
            lastSeen = "Citadel"
        )
        val newEntry = CharacterEntry(
            name = "Shepard",
            description = "Defeats the Reapers.",
            aliases = listOf("Shep", "The Commander"),
            status = "",
            lastSeen = "Earth"
        )

        val merged = mergeCharacterEntry(existing, newEntry)

        assertEquals("Shepard", merged.name)
        assertTrue(merged.description.contains("Alliance commander"))
        assertTrue(merged.description.contains("Defeats the Reapers"))
        assertEquals(listOf("Commander", "Shep", "The Commander"), merged.aliases)
        assertEquals("alive", merged.status)
        assertEquals("Earth", merged.lastSeen)
    }

    @Test
    fun `mergeEventEntry appends participants and dedupes aliases`() {
        val existing = EventEntry(
            name = "Battle of Omega",
            description = "Heavy losses on both sides.",
            participants = listOf("Shepard", "Aria"),
            location = "Omega",
            aliases = listOf("Omega battle")
        )
        val newEntry = EventEntry(
            name = "Battle of Omega",
            description = "Cerberus retreats.",
            participants = listOf("Shepard", "Miranda"),
            location = "",
            aliases = listOf("Omega conflict")
        )

        val merged = mergeEventEntry(existing, newEntry)

        assertEquals("Battle of Omega", merged.name)
        assertTrue(merged.description.contains("Heavy losses"))
        assertTrue(merged.description.contains("Cerberus retreats"))
        assertEquals(listOf("Shepard", "Aria", "Miranda"), merged.participants)
        assertEquals("Omega", merged.location)
        assertEquals(listOf("Omega battle", "Omega conflict"), merged.aliases)
    }

    @Test
    fun `mergeLocationEntry appends aliases and preserves controller`() {
        val existing = LocationEntry(
            name = "Omega",
            description = "Terminus station.",
            controller = "Aria",
            aliases = listOf("station")
        )
        val newEntry = LocationEntry(
            name = "Omega",
            description = "Lawless district.",
            controller = "",
            aliases = listOf("Omega station")
        )

        val merged = mergeLocationEntry(existing, newEntry)

        assertEquals("Omega", merged.name)
        assertTrue(merged.description.contains("Terminus station"))
        assertTrue(merged.description.contains("Lawless district"))
        assertEquals("Aria", merged.controller)
        assertEquals(listOf("station", "Omega station"), merged.aliases)
    }

    @Test
    fun `mergeConceptEntry appends aliases and description`() {
        val existing = ConceptEntry(
            name = "Mass Effect",
            description = "Element zero residue.",
            aliases = listOf("biotics")
        )
        val newEntry = ConceptEntry(
            name = "Mass Effect",
            description = "Enables faster-than-light travel.",
            aliases = listOf("EEzo", "biotics")
        )

        val merged = mergeConceptEntry(existing, newEntry)

        assertEquals("Mass Effect", merged.name)
        assertTrue(merged.description.contains("Element zero residue"))
        assertTrue(merged.description.contains("Enables faster-than-light travel"))
        assertEquals(listOf("biotics", "EEzo"), merged.aliases)
    }

    @Test
    fun `mergeWithNullExisting returns new entry unchanged`() {
        val newEntry = CharacterEntry(
            name = "Garrus",
            description = "Turian sniper.",
            aliases = listOf("Archangel")
        )

        val merged = mergeCharacterEntry(null, newEntry)

        assertEquals(newEntry, merged)
    }
}
