package Structs

import kotlinx.serialization.Serializable

/**
 * Typed lorebook entity representing a character in the story.
 * Serialized as JSON value for `LorebookExtraction.characters[]`.
 */
@Serializable
data class CharacterEntry(
    var name: String = "",
    var description: String = "",
    var aliases: List<String> = listOf(),
    var status: String = "",
    var lastSeen: String = ""
)

/**
 * Typed lorebook entity representing a significant story event.
 * Serialized as JSON value for `LorebookExtraction.events[]`.
 */
@Serializable
data class EventEntry(
    var name: String = "",
    var description: String = "",
    var participants: List<String> = listOf(),
    var location: String = "",
    var aliases: List<String> = listOf()
)

/**
 * Typed lorebook entity representing a location / place / setting.
 * Serialized as JSON value for `LorebookExtraction.locations[]`.
 */
@Serializable
data class LocationEntry(
    var name: String = "",
    var description: String = "",
    var controller: String = "",
    var aliases: List<String> = listOf()
)

/**
 * Typed lorebook entity representing a concept — magic systems, world rules,
 * themes, technologies, abilities. Anything load-bearing for the story that
 * isn't a character, event, or place.
 *
 * Serialized as JSON value for `LorebookExtraction.concepts[]`.
 */
@Serializable
data class ConceptEntry(
    var name: String = "",
    var description: String = "",
    var aliases: List<String> = listOf()
)

/**
 * Typed extraction contract for the loreBookPipe LLM call.
 *
 * The LLM emits this schema; the transformation function in Env.kt
 * (recordLoreBook) walks each list, runs a typed per-entity merge against
 * the existing banked lorebook, and writes the merged result back via
 * ContextBank.emplaceWithMutex.
 *
 * Replaces the previous raw-ContextWindow output schema that allowed the
 * LLM to write into contextElements / converseHistory by mistake.
 */
@Serializable
data class LorebookExtraction(
    var characters: List<CharacterEntry> = listOf(),
    var events: List<EventEntry> = listOf(),
    var locations: List<LocationEntry> = listOf(),
    var concepts: List<ConceptEntry> = listOf()
)
