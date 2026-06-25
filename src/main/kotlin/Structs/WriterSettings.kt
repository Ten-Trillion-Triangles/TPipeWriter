package Structs

import com.TTT.Enums.ProviderName
import com.TTT.Pipe.Pipe
import com.TTT.Pipeline.Pipeline
import Globals.ModelConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer

private val PRIMARY_MODEL: String = Globals.ModelConfig.primaryModelName

/**
 * Persistent settings for a single pipe.
 *
 * MiniMax-M3 Generic OpenAI edition:
 * - The `region` field is preserved for backward compatibility with persisted
 *   settings JSON, but it is no longer used. MiniMax is a hosted model at
 *   api.minimax.io/v1 — there is no AWS region.
 * - The `provider` enum is preserved; `ProviderName.OpenAI` (or whichever value
 *   the upstream TPipe pipe reports) identifies the GenericOpenAIPipe-backed
 *   pipe. The Bedrock-specific provider path has been removed.
 */
@Serializable
data class ModelSettings(
    var provider: ProviderName,
    var pipeName: String = "",
    var modelName: String = "",
    var temperature: Double = .7,
    var topP: Double = .7,
    var maxTokens: Int = 10000
)
{
    /**
     * Preserved for serialization compatibility. No longer used — MiniMax is
     * regionless. Callers that previously called `setRegion()` should drop the
     * call; MiniMax-M3 has no regional binding.
     */
    var region = ""

    /**
     * No-op stub preserved for serialization compatibility. MiniMax is regionless;
     * `region` stays at "" by default.
     */
    fun setRegion()
    {
        // No-op. MiniMax-M3 is hosted at api.minimax.io/v1; there is no AWS region
        // to set. The `region` field is preserved in the data class for JSON
        // backward compatibility with previously persisted settings files.
    }
}

/**
 * Convert from TPipe to TPipeWriter model structs.
 */
fun toModelSettings(pipe: Pipe) : ModelSettings
{
    val pipeSettings = pipe.toPipeSettings()
    val rawModel = pipeSettings.model ?: ""
    val simpleModel = if (rawModel.contains('/')) rawModel.substringAfterLast('/') else rawModel

    val newModelSettings = ModelSettings(
        provider = pipeSettings.provider!!,
        modelName = simpleModel,
        temperature = pipeSettings.temperature ?: .7,
        topP = pipeSettings.topP ?: .7,
        pipeName = pipeSettings.pipeName ?: "",
        maxTokens = pipeSettings.maxTokens ?: 10000)

    return newModelSettings
}

/**
 * Get and restore all known settings from a given pipeline so that we can save and restore them as needed.
 */
fun constructModelSettingsList(pipeline: Pipeline) : List<ModelSettings>
{
    val settingsList = mutableListOf<ModelSettings>()
    val pipes = pipeline.getPipes()

    for(pipe in pipes)
    {
        val modelSettings = toModelSettings(pipe)
        settingsList.add(modelSettings)
    }

    return settingsList
}


/**
 * Convert a pipeline's pipes to MiniMax-M3. Useful when a different model variant
 * has produced a refusal and we want to re-route the call through MiniMax-M3.
 * (The previous version of this function forced everything to deepseek; we keep
 * the same name for source compatibility but route through the canonical
 * ModelConfig.primaryModelName.)
 */
fun convertPipelineToDeepseek(pipeline: Pipeline) : Pipeline
{
    val pipes = pipeline.getPipes()
    for(pipe in pipes)
    {
        pipe.setModel(PRIMARY_MODEL)

        runBlocking {
            pipe.init()
        }
    }

    return pipeline
}

/**
 * Helper function to force update a pipeline's pipes based on settings. Exists to try to simplify ui settings
 * for changing model and other configuration settings for each pipe in a pipeline.
 */
fun updatePipeWithModelSettings(pipeline: Pipeline,  modelSettings: List<ModelSettings>)
{
    for(model in modelSettings)
    {
        val pipe = pipeline.getPipeByName(model.pipeName).second
        if(pipe == null) continue

        // MiniMax is regionless; the model.region field is preserved for
        // backward-compat but no longer affects pipe configuration.
        pipe.setModel(model.modelName)
            .setTopP(model.topP)
            .setTemperature(model.temperature)
            .setMaxTokens(model.maxTokens)

        runBlocking {
            pipe.init()
        }
    }
}

/**
 * Export ModelSettings map to JSON string.
 */
fun exportModelSettingsToJson(settingsMap: Map<String, ModelSettings>): String
{
    val kxMapSerializer = kotlinx.serialization.builtins.MapSerializer(
        String.serializer(),
        ModelSettings.serializer()
    )
    return try
    {
        val tp = com.TTT.Util.serialize(settingsMap)
        if (tp.isNotEmpty()) tp
        else kotlinx.serialization.json.Json.encodeToString(kxMapSerializer, settingsMap)
    }
    catch (e: Exception)
    {
        try
        {
            kotlinx.serialization.json.Json.encodeToString(kxMapSerializer, settingsMap)
        }
        catch (e2: Exception)
        {
            "{}"
        }
    }
}

/**
 * Import ModelSettings map from JSON string.
 */
fun importModelSettingsFromJson(jsonString: String): Map<String, ModelSettings>?
{
    if (jsonString.isBlank()) return null
    val kxMapSerializer = kotlinx.serialization.builtins.MapSerializer(
        String.serializer(),
        ModelSettings.serializer()
    )
    return try
    {
        val tp = com.TTT.Util.deserialize<Map<String, ModelSettings>>(jsonString)
        if (tp != null && tp.isNotEmpty()) tp
        else kotlinx.serialization.json.Json.decodeFromString(kxMapSerializer, jsonString)
    }
    catch (e: Exception)
    {
        try
        {
            kotlinx.serialization.json.Json.decodeFromString(kxMapSerializer, jsonString)
        }
        catch (e2: Exception)
        {
            null
        }
    }
}

/**
 * Get pipeline name mapping for display purposes.
 */
fun getPipelineDisplayName(internalName: String): String
{
    return when (internalName)
    {
        "Writer Pipeline" -> "Writer"
        "Idea Pipeline" -> "Idea"
        "Lorebook Pipeline" -> "Lorebook"
        "Rewrite Pipeline" -> "Rewrite"
        "Chat Pipeline" -> "Chat"
        "Summary Pipeline" -> "Summary"
        "Style Pipeline" -> "Style"
        "NCC Pipeline" -> "NCC"
        else -> internalName
    }
}