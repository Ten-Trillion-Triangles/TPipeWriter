package Structs

import com.TTT.Context.ContextWindow
import com.TTT.Util.deserialize
import com.TTT.Util.serialize
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

/**
 * Merge a new CharacterEntry against an existing one (or null). Description is
 * newline-joined when both are non-blank; aliases are deduped preserving
 * first-seen order; status/lastSeen fall back to existing when new is blank.
 *
 * @param existing Previously-stored entry, or null if this is the first time
 *                 we have seen this character name.
 * @param new New entry produced by the LLM extraction.
 * @return Merged entry to persist.
 */
fun mergeCharacterEntry(existing: CharacterEntry?, new: CharacterEntry): CharacterEntry
{
    if (existing == null) return new
    return CharacterEntry(
        name = new.name,
        description = if (new.description.isNotBlank()) "${existing.description}\n${new.description}" else existing.description,
        aliases = (existing.aliases + new.aliases).distinct(),
        status = new.status.ifBlank { existing.status },
        lastSeen = new.lastSeen.ifBlank { existing.lastSeen }
    )
}

/**
 * Merge a new EventEntry against an existing one (or null). Description is
 * newline-joined when both are non-blank; participants are deduped preserving
 * first-seen order; location falls back to existing when new is blank;
 * aliases are deduped.
 *
 * @param existing Previously-stored entry, or null.
 * @param new New entry produced by the LLM extraction.
 * @return Merged entry to persist.
 */
fun mergeEventEntry(existing: EventEntry?, new: EventEntry): EventEntry
{
    if (existing == null) return new
    return EventEntry(
        name = new.name,
        description = if (new.description.isNotBlank()) "${existing.description}\n${new.description}" else existing.description,
        participants = (existing.participants + new.participants).distinct(),
        location = new.location.ifBlank { existing.location },
        aliases = (existing.aliases + new.aliases).distinct()
    )
}

/**
 * Merge a new LocationEntry against an existing one (or null). Description is
 * newline-joined when both are non-blank; aliases are deduped; controller
 * falls back to existing when new is blank.
 *
 * @param existing Previously-stored entry, or null.
 * @param new New entry produced by the LLM extraction.
 * @return Merged entry to persist.
 */
fun mergeLocationEntry(existing: LocationEntry?, new: LocationEntry): LocationEntry
{
    if (existing == null) return new
    return LocationEntry(
        name = new.name,
        description = if (new.description.isNotBlank()) "${existing.description}\n${new.description}" else existing.description,
        controller = new.controller.ifBlank { existing.controller },
        aliases = (existing.aliases + new.aliases).distinct()
    )
}

/**
 * Merge a new ConceptEntry against an existing one (or null). Description is
 * newline-joined when both are non-blank; aliases are deduped.
 *
 * @param existing Previously-stored entry, or null.
 * @param new New entry produced by the LLM extraction.
 * @return Merged entry to persist.
 */
fun mergeConceptEntry(existing: ConceptEntry?, new: ConceptEntry): ConceptEntry
{
    if (existing == null) return new
    return ConceptEntry(
        name = new.name,
        description = if (new.description.isNotBlank()) "${existing.description}\n${new.description}" else existing.description,
        aliases = (existing.aliases + new.aliases).distinct()
    )
}

/**
 * Apply a typed extraction to an existing banked ContextWindow.
 *
 * For each entity in the extraction, look up the existing lorebook entry by
 * name, deserialize the banked value as the typed struct, run the typed merge
 * function, and re-add via [ContextWindow.addLoreBookEntry]. Entries that
 * don't exist yet are added fresh. Aliases are populated from the merged
 * entity so downstream [ContextWindow.findLoreBookEntry] matches against
 * LLM-emitted aliases.
 *
 * @param extraction Typed extraction produced by the loreBookPipe LLM call.
 * @param bank Existing banked ContextWindow (e.g. ContextBank.getContextFromBank("main")).
 * @return The same ContextWindow reference, mutated in place. Caller is responsible for emplaceWithMutex.
 */
fun applyExtractionToBank(extraction: LorebookExtraction, bank: ContextWindow): ContextWindow
{
    extraction.characters.forEach { entry ->
        val existing = bank.findLoreBookEntry(entry.name)
        val merged = mergeCharacterEntry(existing?.value?.let { deserialize<CharacterEntry>(it) }, entry)
        bank.addLoreBookEntry(
            key = merged.name,
            value = serialize(merged),
            aliasKeys = merged.aliases
        )
    }

    extraction.events.forEach { entry ->
        val existing = bank.findLoreBookEntry(entry.name)
        val merged = mergeEventEntry(existing?.value?.let { deserialize<EventEntry>(it) }, entry)
        bank.addLoreBookEntry(
            key = merged.name,
            value = serialize(merged),
            aliasKeys = merged.aliases
        )
    }

    extraction.locations.forEach { entry ->
        val existing = bank.findLoreBookEntry(entry.name)
        val merged = mergeLocationEntry(existing?.value?.let { deserialize<LocationEntry>(it) }, entry)
        bank.addLoreBookEntry(
            key = merged.name,
            value = serialize(merged),
            aliasKeys = merged.aliases
        )
    }

    extraction.concepts.forEach { entry ->
        val existing = bank.findLoreBookEntry(entry.name)
        val merged = mergeConceptEntry(existing?.value?.let { deserialize<ConceptEntry>(it) }, entry)
        bank.addLoreBookEntry(
            key = merged.name,
            value = serialize(merged),
            aliasKeys = merged.aliases
        )
    }

    return bank
}
