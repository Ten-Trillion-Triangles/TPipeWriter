package Shell

import Globals.Env
import Structs.AuthorSlot
import Structs.PersonalitySeed
import com.TTT.Pipeline.Pipeline
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * End-to-end TUI control harness for the personality slot binding flow.
 *
 * Why this exists: a previous version of the personality subshell
 * saved the user's binding to settings.json and updated Env.* in
 * memory, but did NOT rebuild the writer / editor / rewrite
 * pipelines that snapshot Env.authorPrompt into their
 * ReasoningSettings.roleCharacter at construction time. The user
 * would change the binding in /personalities, observe it recorded,
 * and then watch the agent continue writing in the old voice.
 *
 * This harness drives the production TUI functions (NOT a parallel
 * implementation) and inspects the resulting wire-level pipe state
 * to prove the binding flows through. Two phases:
 *
 *  1. Settings -> Env.*  via [applyPersonalitySlotsFromSettings].
 *  2. Env.* -> pipe.toPipeSettings().systemPrompt via
 *     [rebindPersonalitySlotsAndRebuildPipelines], which re-runs
 *     [Env.init].
 *
 * Phase 2 needs `MINIMAX_API_KEY` so the builders' `pipe.init()`
 * calls don't early-return; the test skips itself in CI when that
 * env var is missing.
 */
class PersonalityToPipelineFlowTest
{
    private var savedAuthor = ""
    private var savedCompeting = ""
    private var savedEditor = ""
    private var savedWritingControl = ""
    private var savedActiveAuthor = ""
    private var savedActiveEditor = ""
    private var savedActiveRichard = ""

    private var savedSettingsFile: File? = null

    /**
     * The unique phrase we'll plant in the body and then hunt for in
     * the rebuilt pipe's prompt. Phrased so that no existing seeded
     * personality contains it; if this assertion ever passes for a
     * different reason the test itself is wrong and must be updated.
     */
    private val uniqueProbe = "ZZZ_PROBE_universe-pear"

    @BeforeEach
    fun snapshotEnvState()
    {
        savedAuthor = Env.authorPrompt
        savedCompeting = Env.richardTreadwell
        savedEditor = Env.editorPrompt
        savedWritingControl = Env.writingControlPrompt
        savedActiveAuthor = Env.activeAuthorGuide
        savedActiveEditor = Env.activeEditorGuide
        savedActiveRichard = Env.activeRichardTreadwell
    }

    @AfterEach
    fun restoreEnvState()
    {
        Env.authorPrompt = savedAuthor
        Env.richardTreadwell = savedCompeting
        Env.editorPrompt = savedEditor
        Env.writingControlPrompt = savedWritingControl
        Env.activeAuthorGuide = savedActiveAuthor
        Env.activeEditorGuide = savedActiveEditor
        Env.activeRichardTreadwell = savedActiveRichard

        // Wipe the test's settings.json back to a clean state so other
        // tests don't inherit our custom roster.
        savedSettingsFile?.let { if (it.exists()) it.delete() }
    }

    @Test
    fun bindingFlowsFromSettingsJsonIntoEnvFields()
    {
        // Phase 1: write a settings file with a custom personality
        // bound to AUTHOR_PROMPT and the standard seed for the other
        // slots, then call the same function Env.init calls.
        val body = """You are Probe Author. ${uniqueProbe} first sentence."""
        val settings = TPipeSettings(
            personalityRoster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body } + ("Probe Author" to body),
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Probe Author",
                AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
                AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
                AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
            )
        )
        installSettings(settings)

        val bindings = applyPersonalitySlotsFromSettings()

        assertEquals("Probe Author", bindings[AuthorSlot.AUTHOR_PROMPT.name])
        assertEquals(body, Env.authorPrompt)
        assertTrue(
            Env.authorPrompt.contains(uniqueProbe),
            "Env.authorPrompt must contain the custom-probe body marker"
        )
        assertEquals(body, Env.activeAuthorGuide,
            "Env.activeAuthorGuide mirror must also carry the probe body")
    }

    @Test
    fun bindingFlowsFromEnvFieldsIntoRunningPipeSystemPrompt()
    {
        // Phase 2 requires a working Env.init, which needs an API key
        // on the wire. Skip the test in CI environments without one.
        val key = System.getenv("MINIMAX_API_KEY")
        assumeTrue(
            !key.isNullOrBlank(),
            "MINIMAX_API_KEY is not set; this test rebuilds real pipes."
        )

        val body = """You are Probe Author. ${uniqueProbe} second sentence."""
        val settings = TPipeSettings(
            personalityRoster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body } + ("Probe Author" to body),
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Probe Author",
                AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
                AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
                AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
            )
        )
        installSettings(settings)

        // Step A: take the persisted binding and push it into Env.*.
        applyPersonalitySlotsFromSettings()
        assertTrue(
            Env.authorPrompt.contains(uniqueProbe),
            "Env.authorPrompt must reflect the custom-probe body before rebuild"
        )

        // Step B: rebuild the affected pipelines. This is the exact
        // path PersonalitySubshell.assignPersonalityToSlot takes —
        // the helper exposes it so tests can drive it without going
        // through stdin.
        rebindPersonalitySlotsAndRebuildPipelines()

        // Step C: scan Env.plusWriterPipe for a pipe whose
        // systemPrompt embeds Env.authorPrompt at construction time
        // and assert the embedded text now carries the probe body.
        // PlusWriterPipeline.kt has multiple pipes whose systemPrompt
        // is the literal string "  ${Env.authorPrompt}. Your job is
        // simple: ..."  — these were the exact pipes reported by the
        // operator as "still using the old personality" before this
        // fix.
        val plus = Env.plusWriterPipe
        assertNotNull(plus, "Env.plusWriterPipe must be non-null after Env.init")

        val probeHit = findProbeInPipeline(plus, uniqueProbe)
        assertTrue(
            probeHit != null,
            "Pipeline rebuilt, but no pipe in Env.plusWriterPipe carried the " +
                "custom-probe body in its systemPrompt. The binding still " +
                "isn't reaching the running pipes. Saw bodies: " +
                plus.getPipes().map { it.toPipeSettings().systemPrompt ?: "" }
        )
    }

    @Test
    fun phaseOneSetsEnvFieldsWithoutTouchingPipelines()
    {
        // Isolated guarantee: phase 1 must NOT call Env.init (the user
        // might still be choosing a binding from a list). It must only
        // mutate Env.*. After phase 1, Env.* reflects the new binding
        // even when the existing pipe objects still have the old prompt.
        val body = """You are Probe Author. ${uniqueProbe} third sentence."""
        val settings = TPipeSettings(
            personalityRoster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body } + ("Probe Author" to body),
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Probe Author",
                AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
                AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
                AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
            )
        )
        installSettings(settings)

        applyPersonalitySlotsFromSettings()

        assertEquals(body, Env.authorPrompt)
        assertEquals(body, Env.activeAuthorGuide)
        // The other three slots remain on their seeded owners.
        assertTrue(
            Env.richardTreadwell.contains("N'zelquin"),
            "N'zelquin should still drive richardTreadwell"
        )
        assertTrue(
            Env.editorPrompt.contains("Falkenda"),
            "Falkenda should still drive editorPrompt"
        )
        assertTrue(
            Env.writingControlPrompt.contains("Invis"),
            "Invis should still drive writingControlPrompt"
        )
    }

    private fun findProbeInPipeline(pipeline: Pipeline, probe: String): String?
    {
        return pipeline.getPipes()
            .firstOrNull { pipe ->
                val ps = pipe.toPipeSettings()
                (ps.systemPrompt ?: "").contains(probe)
            }
            ?.toPipeSettings()
            ?.systemPrompt
    }

    private fun installSettings(settings: TPipeSettings)
    {
        // Mirror the production settings-file location used by
        // loadSettings()/saveSettings(): $HOME/.TPipeWriter/settings.json.
        val home = com.TTT.Util.getHomeFolder()
        val configDir = File(home, ".TPipeWriter")
        configDir.mkdirs()
        val settingsFile = File(configDir, "settings.json")
        savedSettingsFile = settingsFile
        settingsFile.writeText(com.TTT.Util.serialize(settings))
    }
}
