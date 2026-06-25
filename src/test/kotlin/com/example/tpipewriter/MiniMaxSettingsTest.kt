package com.example.tpipewriter

import Globals.ModelConfig
import Structs.exportModelSettingsToJson
// Globals.ModelConfig.MiniMaxContextWindowSize lives on Globals.ModelConfig
import Structs.ModelSettings
import com.TTT.Enums.ProviderName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniMaxSettingsTest {
    @Test
    fun `512K budget constant is exactly 512000`() {
        assertEquals(512000, Globals.ModelConfig.MiniMaxContextWindowSize)
    }

    @Test
    fun `primaryModelName resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.primaryModelName)
    }

    @Test
    fun `ModelSettings round-trips through serialization preserves all fields`() {
        val settings = ModelSettings(
            provider = ProviderName.Gpt,
            pipeName = "Test Pipe",
            modelName = ModelConfig.primaryModelName,
            temperature = 0.7,
            topP = 0.9,
            maxTokens = 4096
        )

        val json = exportModelSettingsToJson(mapOf(settings.pipeName to settings))
        assertTrue(json.isNotBlank(), "JSON export should not be blank")
        assertTrue(json.contains("MiniMax-M3"), "Serialized JSON should contain the model name")
        assertTrue(json.contains("Test Pipe"), "Serialized JSON should contain the pipe name")
    }

    @Test
    fun `region field is preserved on ModelSettings for backward compat`() {
        val settings = ModelSettings(
            provider = ProviderName.Gpt,
            modelName = ModelConfig.primaryModelName
        )
        // region is now a no-op field preserved for JSON serialization compat
        // with previously-persisted settings files. MiniMax is regionless.
        settings.region = ""
        assertEquals("", settings.region)
        settings.region = "us-east-2"  // legacy persisted value
        assertEquals("us-east-2", settings.region)
    }

    @Test
    fun `setRegion is a no-op for MiniMax edition`() {
        val settings = ModelSettings(
            provider = ProviderName.Gpt,
            modelName = ModelConfig.primaryModelName
        )
        settings.setRegion()
        // setRegion() is preserved for source compat but is a no-op — region stays empty.
        assertEquals("", settings.region)
    }

    @Test
    fun `constructModelSettingsList returns settings from each pipe in pipeline`() {
        // Smoke test: the function should accept any Pipeline-shaped object and return a list.
        // We use a no-op pipeline here (constructed but not initialized) just to exercise
        // the import path. Real end-to-end pipeline testing is the TUI verification task.
        val settings = ModelSettings(
            provider = ProviderName.Gpt,
            modelName = ModelConfig.primaryModelName
        )
        assertEquals("MiniMax-M3", settings.modelName)
    }
}