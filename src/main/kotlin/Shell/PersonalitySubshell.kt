package Shell

import Globals.Env
import Structs.AuthorPersonality
import Structs.AuthorSlot
import Structs.PersonalitySeed
import readEnhancedInput

/**
 * Interactive subshell for managing the personality roster and the
 * mapping from roster entries to the four Env prompt slots
 * (AUTHOR_PROMPT, COMPETING_AUTHOR, EDITOR_PROMPT, WRITING_CONTROL).
 *
 * Behavior:
 *  - Lists every personality in the persisted roster.
 *  - Adds new personalities (name + body, written into settings.json).
 *  - Lets the user pick a personality and assign it to an Env slot.
 *    The assignment both updates the in-memory `Env.*` field AND
 *    persists the (slot -> name) binding so it survives restarts.
 *  - When the persisted roster is empty (fresh settings file or
 *    legacy install), the four seed personalities from
 *    [PersonalitySeed.defaultRoster] are loaded in.
 */
fun selectPersonalityMode()
{
    ensurePersonalityRosterSeeded()

    val entry = """
        Author Personalities

        1. List personalities
        2. Show current slot bindings
        3. Add a new personality
        4. Assign a personality to a slot
        5. View a personality's body
        6. Exit
        back - Return to main shell
    """.trimIndent()

    while (true)
    {
        println(entry)
        print("personality> ")

        val raw = readEnhancedInput().trim()
        if (raw.equals("back", ignoreCase = true) ||
            raw.equals("exit", ignoreCase = true) ||
            raw.equals("q", ignoreCase = true) ||
            raw == "6")
        {
            return
        }

        val choice = raw.toIntOrNull()
        when (choice)
        {
            1 -> listPersonalities()
            2 -> showSlotBindings()
            3 -> addPersonality()
            4 -> assignPersonalityToSlot()
            5 -> viewPersonality()
            null -> println("Invalid choice: '$raw'. Enter 1-6 or 'back'.")
            else -> println("Invalid choice: $raw. Enter 1-6 or 'back'.")
        }
    }
}

/**
 * Make sure the in-memory and persisted rosters include the four seed
 * personalities plus the user-requested Georgios Martin. Safe to call
 * on every entry to the subshell: only writes settings when the roster
 * was actually empty.
 */
fun ensurePersonalityRosterSeeded()
{
    val settings = loadSettings()
    if (settings.personalityRoster.isNotEmpty()) return

    val seededRoster = PersonalitySeed.defaultRoster()
        .associate { it.name to it.body }

    val seededBindings = PersonalitySeed.defaultSlotBindings()

    val updated = settings.copy(
        personalityRoster = seededRoster,
        personalitySlotBindings = seededBindings
    )
    saveSettings(updated)
    applyPersonalityBindings(updated.personalitySlotBindings, updated.personalityRoster)
}

/**
 * Resolve the roster from settings, defaulting to the seed if the map
 * is empty. Returning `Map<String, String>` keeps the call site simple
 * — callers that need a typed list call [personalityRosterAsList].
 */
fun rosterOrSeed(): Map<String, String>
{
    val settings = loadSettings()
    return if (settings.personalityRoster.isNotEmpty()) settings.personalityRoster
    else PersonalitySeed.defaultRoster().associate { it.name to it.body }
}

/**
 * Apply (slot -> personality name) bindings to the four `Env.*` fields.
 * Called whenever the user picks a slot, when the roster is seeded, or
 * from `loadSettings()` to restore on next launch. Falls back to the
 * text that already lives in Env when a slot key is missing or the
 * named personality isn't in the roster.
 *
 * @param bindings Persisted slot -> name map. Keys are the literal
 *                 strings used in [AuthorSlot] (e.g. "AUTHOR_PROMPT").
 * @param roster   Current roster name -> body map.
 */
fun applyPersonalityBindings(bindings: Map<String, String>, roster: Map<String, String>)
{
    bindings.forEach { (slotKey, name) ->
        val slot = runCatching { AuthorSlot.valueOf(slotKey) }.getOrNull() ?: return@forEach
        val body = roster[name] ?: return@forEach
        when (slot)
        {
            AuthorSlot.AUTHOR_PROMPT -> { Env.authorPrompt = body; Env.activeAuthorGuide = body }
            AuthorSlot.COMPETING_AUTHOR -> { Env.richardTreadwell = body; Env.activeRichardTreadwell = body }
            AuthorSlot.EDITOR_PROMPT -> { Env.editorPrompt = body; Env.activeEditorGuide = body }
            AuthorSlot.WRITING_CONTROL -> { Env.writingControlPrompt = body }
        }
    }
}

/**
 * Mirror the supplied slot bindings into the matching Env.* fields.
 * Public entry point used by `configureSettings` and the startup
 * initialization path. No-op if either map is missing required keys.
 */
fun syncEnvFromSlotBindings()
{
    val settings = loadSettings()
    if (settings.personalityRoster.isEmpty() ||
        settings.personalitySlotBindings.isEmpty()) return
    applyPersonalityBindings(settings.personalitySlotBindings, settings.personalityRoster)
}

private fun listPersonalities()
{
    val roster = rosterOrSeed()
    println("\nPersonalities in roster (${roster.size}):")
    roster.keys.sorted().forEachIndexed { idx, name ->
        println("  ${idx + 1}. $name")
    }
    println("\nTip: '4' to assign, '5' to view a body's full text.\n")
}

private fun showSlotBindings()
{
    val settings = loadSettings()
    val bindings = settings.personalitySlotBindings.ifEmpty { PersonalitySeed.defaultSlotBindings() }
    val roster = rosterOrSeed()

    println("\nCurrent slot bindings:")
    AuthorSlot.values().forEachIndexed { idx, slot ->
        val name = bindings[slot.name] ?: "<unbound>"
        val bodyPreview = roster[name]?.let { previewBody(it) } ?: "(body unavailable)"
        println("  ${idx + 1}. ${slot.displayName}")
        println("       driven by: $name")
        println("       preview: $bodyPreview")
    }
    println()
}

private fun addPersonality()
{
    println("\nEnter a name for the new personality (must be unique in the roster):")
    print("name> ")
    val name = readEnhancedInput().trim()
    if (name.isEmpty())
    {
        println("Name cannot be empty. Cancelled.")
        return
    }

    val settings = loadSettings()
    if (settings.personalityRoster.containsKey(name))
    {
        println("A personality named '$name' already exists. Use a different name.")
        return
    }

    println("\nEnter the personality body. End with a line containing only 'save':")
    val body = readEnhancedInput(delimiter = "save", removeDelimiterAtEnd = true).trim()
    if (body.isEmpty())
    {
        println("Body cannot be empty. Cancelled.")
        return
    }

    val newRoster = settings.personalityRoster + (name to body)
    saveSettings(settings.copy(personalityRoster = newRoster))
    println("Added '$name' to the personality roster.")
}

private fun assignPersonalityToSlot()
{
    val roster = rosterOrSeed()
    val names = roster.keys.sorted()

    println("\nChoose a personality to assign:")
    names.forEachIndexed { idx, name -> println("  ${idx + 1}. $name") }
    print("personality> ")
    val pick = readEnhancedInput().trim().toIntOrNull()
    if (pick == null || pick < 1 || pick > names.size)
    {
        println("Invalid choice.")
        return
    }
    val selectedName = names[pick - 1]

    println("\nChoose a slot to drive with '$selectedName':")
    AuthorSlot.values().forEachIndexed { idx, slot ->
        println("  ${idx + 1}. ${slot.displayName} — ${slot.description}")
    }
    print("slot> ")
    val slotPick = readEnhancedInput().trim().toIntOrNull()
    val slot = slotPick?.let { AuthorSlot.values().getOrNull(it - 1) }
    if (slot == null)
    {
        println("Invalid slot.")
        return
    }

    val settings = loadSettings()
    val newBindings = settings.personalitySlotBindings + (slot.name to selectedName)
    saveSettings(settings.copy(
        personalityRoster = roster,
        personalitySlotBindings = newBindings
    ))

    // Mirror the binding into the runtime Env fields so the change
    // takes effect for the current session without re-init.
    applyPersonalityBindings(mapOf(slot.name to selectedName), roster)

    // Re-run Env.init so every writer / editor / rewrite pipeline
    // rebuilds against the new binding. The pipeline objects hold
    // their role-character reference at construction time, so a
    // binding change that only updates Env.* in-memory is invisible
    // to them until they are rebuilt.
    rebindPersonalitySlotsAndRebuildPipelines()

    println("Slot '${slot.displayName}' is now driven by '$selectedName'.")
}

private fun viewPersonality()
{
    val roster = rosterOrSeed()
    val names = roster.keys.sorted()

    println("\nChoose a personality to view:")
    names.forEachIndexed { idx, name -> println("  ${idx + 1}. $name") }
    print("personality> ")
    val pick = readEnhancedInput().trim().toIntOrNull()
    if (pick == null || pick < 1 || pick > names.size)
    {
        println("Invalid choice.")
        return
    }
    val name = names[pick - 1]
    println("\n=== $name ===\n")
    println(roster[name])
    println()
}

private fun previewBody(body: String): String
{
    val firstLine = body.lineSequence().firstOrNull()?.trim().orEmpty()
    return if (firstLine.length <= 80) firstLine else firstLine.take(77) + "..."
}

/**
 * Convert the persisted roster (name -> body) into a typed
 * [List<AuthorPersonality>]. Useful for tests and any future caller
 * that wants the stronger type.
 */
fun personalityRosterAsList(): List<AuthorPersonality>
{
    return rosterOrSeed().map { (name, body) -> AuthorPersonality(name, body) }
}

/**
 * Read the persisted personality slot bindings from ~/.TPipeWriter/settings.json
 * and apply them to the four Env.* prompt fields
 * (Env.authorPrompt, Env.richardTreadwell, Env.editorPrompt,
 * Env.writingControlPrompt).
 *
 * This is the function `Env.init` calls at the very top of its body so
 * the downstream builders — which snapshot those fields into a pipe's
 * ReasoningSettings.roleCharacter — reflect the user's persisted choice.
 *
 * Idempotent: calling it twice has the same effect as calling it once.
 * Safe on a fresh settings file: if no binding map is persisted yet, the
 * defaults from [PersonalitySeed.defaultSlotBindings] are written to the
 * Env.* fields (the seeded personalities), and the seeding side-effect is
 * applied to settings.json via [ensurePersonalityRosterSeeded].
 *
 * @return The applied bindings, useful for tests verifying that the
 *         wire between settings.json and Env.* is intact.
 */
fun applyPersonalitySlotsFromSettings(): Map<String, String>
{
    ensurePersonalityRosterSeeded()

    val settings = loadSettings()
    val bindings = settings.personalitySlotBindings
        .ifEmpty {
            val seeded = PersonalitySeed.defaultSlotBindings()
            saveSettings(settings.copy(personalitySlotBindings = seeded))
            seeded
        }
    val roster = settings.personalityRoster.ifEmpty { rosterOrSeed() }

    applyPersonalityBindings(bindings, roster)
    return bindings
}

/**
 * Public entry point the TUI calls after the user changes a slot binding.
 *
 * The sequence is:
 *   1. Save the new binding to settings.json (already done by
 *      [assignPersonalityToSlot]).
 *   2. Apply the binding to Env.* (in-memory swap of the prompt strings,
 *      plus the Env.active* mirrors).
 *   3. Re-run [Env.init] using the persisted settings, so every
 *      [buildPlusWriterPipeline], [buildChapterRewritePipeline],
 *      [buildNccWriter], etc. callsite rebuilds its pipes against the
 *      new Env.* value. The rebuilt pipes' ReasoningSettings.roleCharacter
 *      and embedded string-interpolations ("${Env.authorPrompt}…") now
 *      contain the user-picked body.
 *
 * Calling this on the wire is what makes the TUI binding actually take
 * effect at runtime — without it the change would only be visible after
 * a process restart.
 */
fun rebindPersonalitySlotsAndRebuildPipelines()
{
    val s = loadSettings()
    applyPersonalityBindings(s.personalitySlotBindings, s.personalityRoster)
    Env.init(
        writingStyle = s.writingStyle,
        temperature = s.temperature,
        topP = s.topP,
        maxTokens = s.maxTokens,
        useAutomaticLoreBookUpdates = s.useAutoLorebook
    )
}
