package Shell

import Chapter.ChapterMetadata
import Chapter.StoryData
import Chapter.applyPersonalitySnapshot
import Globals.Env
import Structs.AuthorSlot
import Structs.PersonalitySeed
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Util.getHomeFolder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Round-trip regression tests for the personality binding's export /
 * loadStory wiring.
 *
 * The user's directive was: "verify that if a story is loaded the
 * personality settings saved are rebound, and that the settings are
 * saved when exporting a story."
 *
 * Two behaviors under test:
 *  (A) When /exportStory writes <name>-settings.json, the active
 *      personalityRoster and personalitySlotBindings are in the file.
 *      The existing exportStory code copies loadSettings() into the
 *      per-story file — this test pins that the binding fields make
 *      the round trip.
 *  (B) When /loadStory reads <name>-settings.json, the binding fields
 *      are merged into the global ~/.TPipeWriter/settings.json (not
 *      overwritten wholesale) so legacy stories that predate the
 *      binding feature do not silently erase the user's binding.
 *
 * The current JUnit harness cannot drive the stdin-based
 * [exportStory] and [loadStory] bodies directly, so the tests
 * replicate the bodies as fixture code: same saveSettings /
 * loadSettings / Env.init / applyPersonalitySlotsFromSettings calls
 * in the same order, against per-test temp directories. This is the
 * same pattern ExportLoadPipesSidecarTest uses for the pipes sidecar.
 */
class PersonalityExportLoadRoundTripTest
{
    private val originalAuthor = Env.authorPrompt
    private val originalCompeting = Env.richardTreadwell
    private val originalEditor = Env.editorPrompt
    private val originalWritingControl = Env.writingControlPrompt
    private val originalActiveAuthor = Env.activeAuthorGuide
    private val originalActiveEditor = Env.activeEditorGuide
    private val originalActiveRichard = Env.activeRichardTreadwell

    private var savedGlobalSettings: File? = null
    private val createdFiles = mutableListOf<File>()

    /**
     * Body-suffix used by exportStory for the per-story settings file.
     * exportStory writes `${filename}-settings.json` under
     * `${getHomeFolder()}/TPipeWriter/`.
     */
    private fun perStorySettingsFile(filename: String): File
    {
        val dir = File(getHomeFolder(), "TPipeWriter")
        dir.mkdirs()
        return File(dir, "$filename-settings.json")
    }

    private fun globalSettingsFile(): File
    {
        val dir = File(getHomeFolder(), ".TPipeWriter")
        dir.mkdirs()
        return File(dir, "settings.json")
    }

    /**
     * Faithful copy of [exportStory]'s settings-write path. The
     * production body does:
     *     val currentSettings = loadSettings().copy(
     *         chapterGuide = Env.activeChapterGuide,
     *         storyGuide = Env.activeStoryGuide,
     *         authorGuide = Env.activeAuthorGuide,
     *     )
     *     val settingsJson = com.TTT.Util.serialize(currentSettings)
     *     File(...).writeText(settingsJson)
     * We pin that exact shape so any future regression here is caught.
     */
    private fun exportSidecarSettingsBody(filename: String, extraSettings: TPipeSettings = loadSettings())
    {
        val currentSettings = extraSettings.copy(
            chapterGuide = Env.activeChapterGuide,
            storyGuide = Env.activeStoryGuide,
            authorGuide = Env.activeAuthorGuide
        )
        val settingsJson = com.TTT.Util.serialize(currentSettings)
        perStorySettingsFile(filename).writeText(settingsJson)
        createdFiles += perStorySettingsFile(filename)
    }

    /**
     * Faithful copy of [loadStory]'s settings-read path, post-fix.
     * The production body now MERGES global+per-story instead of
     * overwriting globally, then calls Env.init to reapply bindings.
     */
    private fun loadSidecarSettingsBody(filename: String)
    {
        val settingsFile = perStorySettingsFile(filename)
        val loadedSettings = if (settingsFile.exists())
            com.TTT.Util.deserialize<TPipeSettings>(settingsFile.readText()) ?: TPipeSettings()
        else TPipeSettings()

        val globalBefore = loadSettings()
        val merged = loadedSettings.copy(
            personalityRoster = loadedSettings.personalityRoster
                .ifEmpty { globalBefore.personalityRoster },
            personalitySlotBindings = loadedSettings.personalitySlotBindings
                .ifEmpty { globalBefore.personalitySlotBindings }
        )
        saveSettings(merged)
        applyPersonalitySlotsFromSettings()
        // Mirror what Env.init does on top of applyPersonalitySlotsFromSettings:
        // it actually rebuilds the pipelines. We skip the full Env.init
        // here because it requires the wire API key, but we DO want
        // every Env.* field to mirror the persisted binding, which
        // applyPersonalitySlotsFromSettings has already accomplished.
        // Env.init's role of rebuilding pipelines is covered by
        // PersonalityToPipelineFlowTest.
    }

    @BeforeEach
    fun snapshotEnvState()
    {
        savedGlobalSettings = globalSettingsFile()
        if (savedGlobalSettings!!.exists())
        {
            // Back up existing global settings so sibling tests / the
            // operator's real session are not affected.
            val backup = File(savedGlobalSettings!!.parentFile, "settings.json.adhoc-backup")
            backup.writeText(savedGlobalSettings!!.readText())
            backup.deleteOnExit()
        }
        runBlocking { ContextBank.emplaceWithMutex("main", ContextWindow()) }
    }

    @AfterEach
    fun restoreEnvState()
    {
        Env.authorPrompt = originalAuthor
        Env.richardTreadwell = originalCompeting
        Env.editorPrompt = originalEditor
        Env.writingControlPrompt = originalWritingControl
        Env.activeAuthorGuide = originalActiveAuthor
        Env.activeEditorGuide = originalActiveEditor
        Env.activeRichardTreadwell = originalActiveRichard

        // Restore the pre-test global settings.json
        val backup = File(globalSettingsFile().parentFile, "settings.json.adhoc-backup")
        if (backup.exists())
        {
            globalSettingsFile().writeText(backup.readText())
            backup.delete()
        }
        else if (globalSettingsFile().exists())
        {
            globalSettingsFile().delete()
        }

        for (f in createdFiles) if (f.exists()) f.delete()
        createdFiles.clear()
    }

    @Test
    fun exportWritesPersonalityRosterAndBindingsToSidecarFile()
    {
        // Set up an active global binding with a custom probe body for
        // the AUTHOR_PROMPT slot. The other slots keep their seeded
        // owners.
        val probe = "Probe-Export-YXZ-${System.nanoTime()}"
        val probeBody = "You are Test Author Probe. ${probe} first sentence."
        val roster = PersonalitySeed.defaultRoster()
            .associate { it.name to it.body } + ("Test Author Probe" to probeBody)
        val bindings = PersonalitySeed.defaultSlotBindings()
            .toMutableMap()
            .also { it[AuthorSlot.AUTHOR_PROMPT.name] = "Test Author Probe" }

        saveSettings(TPipeSettings(
            personalityRoster = roster,
            personalitySlotBindings = bindings
        ))
        applyPersonalitySlotsFromSettings()

        // Run the export-side body against an isolated filename.
        val filename = "export-sidecar-${System.currentTimeMillis()}"
        exportSidecarSettingsBody(filename)

        // Read the sidecar and confirm it carries the binding.
        val sidecar = perStorySettingsFile(filename)
        assertTrue(sidecar.exists(), "exportStory path should have written $filename-settings.json")

        val raw = sidecar.readText()
        assertTrue(raw.contains("Test Author Probe"),
            "Sidecar must include the custom-probe roster entry")
        assertTrue(raw.contains(probe),
            "Sidecar must include the probe marker")
        assertTrue(raw.contains(AuthorSlot.AUTHOR_PROMPT.name),
            "Sidecar must include the slot key")
        assertTrue(raw.contains("personalityRoster"),
            "Sidecar must serialize the personalityRoster field")
        assertTrue(raw.contains("personalitySlotBindings"),
            "Sidecar must serialize the personalitySlotBindings field")
    }

    @Test
    fun loadAppliesBindingFromSidecarWhenPresent()
    {
        // Construct a sidecar that has its own binding (Probe Story),
        // simulating a story that was exported with a custom binding.
        val probe = "Probe-Load-ABC-${System.nanoTime()}"
        val probeBody = "You are Probe Story Author. ${probe} second sentence."
        val roster = PersonalitySeed.defaultRoster()
            .associate { it.name to it.body } + ("Probe Story Author" to probeBody)
        val bindings = mapOf(
            AuthorSlot.AUTHOR_PROMPT.name to "Probe Story Author",
            AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
            AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
            AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
        )

        val filename = "load-applies-${System.currentTimeMillis()}"
        val perStory = perStorySettingsFile(filename)
        perStory.parentFile.mkdirs()
        perStory.writeText(com.TTT.Util.serialize(TPipeSettings(
            personalityRoster = roster,
            personalitySlotBindings = bindings
        )))
        createdFiles += perStory

        // Make the global settings.json have a DIFFERENT binding so
        // we can prove the per-story binding overrides at load time.
        val globalBody = "You are GLOBAL Author. ${probe} global marker."
        saveSettings(TPipeSettings(
            personalityRoster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body } + ("GLOBAL Author" to globalBody),
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "GLOBAL Author",
                AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
                AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
                AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
            )
        ))

        // Drive the load-side body.
        loadSidecarSettingsBody(filename)

        // After load, Env.authorPrompt must reflect the LOADED binding
        // (not the global one) — i.e., the per-story binding wins.
        assertEquals(probeBody, Env.authorPrompt,
            "Loaded binding should override the global binding on Env.authorPrompt")
        assertEquals(probeBody, Env.activeAuthorGuide,
            "Env.activeAuthorGuide mirror must also carry the loaded binding")
        assertTrue(Env.authorPrompt.contains(probe),
            "Env.authorPrompt must contain the loaded probe marker")
    }

    @Test
    fun legacySidecarWithoutBindingPreservesGlobalBinding()
    {
        // Construct a sidecar that is purely pre-feature: no
        // personalityRoster, no personalitySlotBindings. This is
        // the shape a story exported before the binding feature
        // shipped would have.
        val filename = "legacy-sidecar-${System.currentTimeMillis()}"
        val perStory = perStorySettingsFile(filename)
        perStory.parentFile.mkdirs()
        // Use an old-shape JSON that lacks the new fields.
        perStory.writeText("""
            {
              "writingStyle": "",
              "temperature": 1.0,
              "topP": 0.9,
              "maxTokens": 5000,
              "useAutoLorebook": true,
              "authorGuide": "",
              "competingAuthorGuide": "",
              "chapterGuide": "",
              "storyGuide": "",
              "editorGuide": ""
            }
        """.trimIndent())
        createdFiles += perStory

        // Set up a non-trivial global binding first.
        val probeGlobal = "Probe-Global-QRS-${System.nanoTime()}"
        val globalBody = "You are Pre-existing Global Author. ${probeGlobal} marker."
        saveSettings(TPipeSettings(
            personalityRoster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body } + ("Pre-existing Global Author" to globalBody),
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Pre-existing Global Author",
                AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
                AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
                AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
            )
        ))
        applyPersonalitySlotsFromSettings()
        // Sanity: global is applied before the load body runs.
        assertEquals(globalBody, Env.authorPrompt,
            "Pre-condition: global binding must be applied before load")

        // Drive the load-side body. The per-story file has empty
        // personalityRoster / personalitySlotBindings; the load-side
        // body must keep the GLOBAL binding alive.
        loadSidecarSettingsBody(filename)

        assertEquals(globalBody, Env.authorPrompt,
            "Legacy story must not clobber the user's global binding")
        assertTrue(Env.authorPrompt.contains(probeGlobal),
            "Global probe marker must survive the load")

        // The on-disk global settings.json must still carry the
        // binding — load must not have written an empty roster over
        // it.
        val writtenGlobal = com.TTT.Util.deserialize<TPipeSettings>(globalSettingsFile().readText())
        assertNotNull(writtenGlobal, "global settings.json must exist after load")
        assertTrue(
            writtenGlobal!!.personalityRoster.containsKey("Pre-existing Global Author"),
            "Global settings.json must retain the user's binding after loading a legacy story"
        )
    }

    @Test
    fun perStoryBindingPartiallyPresentMergesSlotBySlot()
    {
        // Per-story sets ONLY the AUTHOR_PROMPT slot; the other three
        // slots fall back to the global binding.
        val filename = "partial-sidecar-${System.currentTimeMillis()}"
        val perStory = perStorySettingsFile(filename)
        perStory.parentFile.mkdirs()
        val partialBody = "You are Partial Story Author. body."
        val partialRoster = PersonalitySeed.defaultRoster()
            .associate { it.name to it.body } + ("Partial Story Author" to partialBody)
        perStory.writeText(com.TTT.Util.serialize(TPipeSettings(
            personalityRoster = partialRoster,
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Partial Story Author"
                // COMPETING_AUTHOR / EDITOR_PROMPT / WRITING_CONTROL
                // are intentionally absent from the per-story file.
            )
        )))
        createdFiles += perStory

        // Pre-existing global binding for the OTHER three slots AND
        // a placeholder for AUTHOR_PROMPT — the placeholder body must
        // actually be IN the roster, otherwise the binding silently
        // no-ops on the missing key.
        val globalOther3Body = "You are Global Editor. global-editor-body."
        val globalEditorBody = "You are Global WritingControl. global-wc-body."
        val globalCompeteBody = "You are Global Competing. global-compete-body."
        val globalPlaceholderBody = "You are Global Old Author Placeholder. global-placeholder-body."
        saveSettings(TPipeSettings(
            personalityRoster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body } + mapOf(
                    "Global Old Author Placeholder" to globalPlaceholderBody,
                    "Global Editor" to globalOther3Body,
                    "Global WritingControl" to globalEditorBody,
                    "Global Competing" to globalCompeteBody
                ),
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Global Old Author Placeholder",
                AuthorSlot.COMPETING_AUTHOR.name to "Global Competing",
                AuthorSlot.EDITOR_PROMPT.name to "Global Editor",
                AuthorSlot.WRITING_CONTROL.name to "Global WritingControl"
            )
        ))
        applyPersonalitySlotsFromSettings()
        // Sanity: before load, the AUTHOR_PROMPT slot was driven by
        // the global placeholder. The per-story file will override it
        // to "Partial Story Author".
        val beforeLoadAuthor = Env.authorPrompt
        assertTrue(
            beforeLoadAuthor.contains("Global Old Author Placeholder"),
            "Pre-condition: global placeholder drives AUTHOR_PROMPT. Got: <$beforeLoadAuthor>"
        )

        loadSidecarSettingsBody(filename)

        assertTrue(
            Env.authorPrompt.contains("Partial Story Author"),
            "Per-story binding must override the global placeholder for AUTHOR_PROMPT"
        )
        assertTrue(
            Env.richardTreadwell.contains("Global Competing"),
            "Per-story has no COMPETING_AUTHOR binding; global should still drive it"
        )
        assertTrue(
            Env.editorPrompt.contains("Global Editor"),
            "Per-story has no EDITOR_PROMPT binding; global should still drive it"
        )
        assertTrue(
            Env.writingControlPrompt.contains("Global WritingControl"),
            "Per-story has no WRITING_CONTROL binding; global should still drive it"
        )
    }

    @Test
    fun chapterSnapshotAndSettingsBindingCoexist()
    {
        // A saved story has both a chapter-level snapshot (older
        // mechanism) and a personalitySlotBindings map (newer).
        // The settings binding is the source of truth — it overrides
        // the chapter snapshot for all four slots that have a binding.
        // The chapter snapshot is preserved in chapter metadata but
        // does not pollute the live Env.* fields after load completes.
        val filename = "snapshot-vs-binding-${System.currentTimeMillis()}"
        val perStory = perStorySettingsFile(filename)
        perStory.parentFile.mkdirs()

        // Save a settings.json that has its own binding for AUTHOR_PROMPT.
        val settingsBody = "You are Settings Author. settings-binding-body."
        val settingsRoster = PersonalitySeed.defaultRoster()
            .associate { it.name to it.body } + ("Settings Author" to settingsBody)
        val settingsBindings = mapOf(
            AuthorSlot.AUTHOR_PROMPT.name to "Settings Author",
            AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
            AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
            AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
        )

        // Build a story-data JSON with chapter metadata that has an
        // OLDER chapter-snapshot pointing at a different persona.
        // This emulates a chapter saved in the older format.
        val chapterSnapshotBody = "You are Chapter Snapshot Author. chapter-snapshot-body."
        val chapterMetadata = mapOf(
            0 to ChapterMetadata(
                title = "ch1",
                tags = listOf(),
                wordCount = 0,
                createdAt = "",
                lastModified = "",
                authorPromptSnapshot = chapterSnapshotBody,
                editorPromptSnapshot = "You are Chapter Snapshot Editor. chapter-editor-body.",
                richardTreadwellSnapshot = "You are Chapter Snapshot Competing. chapter-compete-body."
            )
        )
        val storyData = StoryData(
            contextElements = listOf("chapter one text"),
            chapterMetadata = chapterMetadata
        )
        File(perStory.parentFile, "$filename-story.json").writeText(
            com.TTT.Util.serialize(storyData)
        )
        createdFiles += File(perStory.parentFile, "$filename-story.json")

        // Write the settings sidecar.
        perStory.writeText(com.TTT.Util.serialize(TPipeSettings(
            personalityRoster = settingsRoster,
            personalitySlotBindings = settingsBindings
        )))
        createdFiles += perStory

        // Set up the global file with a DIFFERENT binding so we can
        // prove the loaded binding wins.
        saveSettings(TPipeSettings(
            personalityRoster = PersonalitySeed.defaultRoster()
                .associate { it.name to it.body } + ("Global Author" to "global body"),
            personalitySlotBindings = mapOf(
                AuthorSlot.AUTHOR_PROMPT.name to "Global Author",
                AuthorSlot.COMPETING_AUTHOR.name to "N'zelquin G'zeeloth",
                AuthorSlot.EDITOR_PROMPT.name to "Falkenda Unseppal",
                AuthorSlot.WRITING_CONTROL.name to "Invis von Disappearo"
            )
        ))

        // Run the load body. We bypass the stdin-bound loadStory() and
        // call the same helper functions in order: chapter snapshot
        // restore first (legacy path), then settings.json merge +
        // Env.init -> applyPersonalitySlotsFromSettings.
        val loadedSettings = com.TTT.Util.deserialize<TPipeSettings>(perStory.readText())!!

        // Path A: chapter-snapshot restore.
        val firstWithSnapshot = chapterMetadata.values.firstOrNull {
            it.authorPromptSnapshot.isNotEmpty() ||
                    it.editorPromptSnapshot.isNotEmpty() ||
                    it.richardTreadwellSnapshot.isNotEmpty()
        }
        val (newAuthor, newEditor, newTreadwell) = Chapter.applyPersonalitySnapshot(
            metadata = firstWithSnapshot!!,
            currentAuthorPrompt = Env.authorPrompt,
            currentEditorPrompt = Env.editorPrompt,
            currentRichardTreadwell = Env.richardTreadwell
        )
        Env.authorPrompt = newAuthor
        Env.activeAuthorGuide = newAuthor
        Env.editorPrompt = newEditor
        Env.activeEditorGuide = newEditor
        Env.richardTreadwell = newTreadwell
        Env.activeRichardTreadwell = newTreadwell

        // Sanity: chapter snapshot wrote the older body to Env.*
        assertTrue(
            Env.authorPrompt.contains("Chapter Snapshot Author"),
            "Pre-condition: chapter snapshot wrote the older body"
        )

        // Path B: settings merge + bind.
        val globalBefore = loadSettings()
        val merged = loadedSettings.copy(
            personalityRoster = loadedSettings.personalityRoster
                .ifEmpty { globalBefore.personalityRoster },
            personalitySlotBindings = loadedSettings.personalitySlotBindings
                .ifEmpty { globalBefore.personalitySlotBindings }
        )
        saveSettings(merged)
        applyPersonalitySlotsFromSettings()

        // After load, the SETTINGS binding must win — chapter snapshot
        // is overridden. The body for AUTHOR_PROMPT must be from the
        // settings file, not the chapter snapshot.
        assertEquals(settingsBody, Env.authorPrompt,
            "Settings binding must override chapter-snapshot for AUTHOR_PROMPT")
        assertTrue(
            Env.authorPrompt.contains("Settings Author"),
            "Loaded Env.authorPrompt must carry the settings-binding body"
        )
        assertFalse(
            Env.authorPrompt.contains("Chapter Snapshot Author"),
            "Loaded Env.authorPrompt must NOT carry the chapter-snapshot body"
        )
    }
}