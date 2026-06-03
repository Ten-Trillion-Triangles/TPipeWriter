package Structs

import com.TTT.Enums.ProviderName
import com.TTT.Pipe.Pipe
import com.TTT.Pipeline.Pipeline
import env.OpenRouterEnv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import openrouterPipe.OpenRouterPipe

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
    var region = ""

    //Stupid name because of frustrating java garbage.
    fun getRegionV2() : String
    {
      return ""
    }

    /**
     * Auto set the region based on model name.
     */
    fun setRegion()
    {
        region = getRegionV2()
    }
}

/**
 * Convert from TPipe to TPipeWriter model structs.
 */
fun toModelSettings(pipe: Pipe) : ModelSettings
{
    val pipeSettings = pipe.toPipeSettings()
    val rawModel = pipeSettings.model ?: ""
    val afterArn = if (rawModel.contains('/')) rawModel.substringAfterLast('/') else rawModel
    val simpleModel = when {
        afterArn.startsWith("us.") -> afterArn.removePrefix("us.")
        afterArn.startsWith("eu.") -> afterArn.removePrefix("eu.")
        afterArn.startsWith("ap.") -> afterArn.removePrefix("ap.")
        else -> afterArn
    }
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
 * Convert a pipeline from any non-deepseek models to deepseek. This is useful when censorship based refusals
 * have occurred on a given pipeline.
 */
fun convertPipelineToDeepseek(pipeline: Pipeline) : Pipeline
{
    val pipes = pipeline.getPipes()
    for(pipe in pipes)
    {
        if(pipe is OpenRouterPipe)
        {
            pipe.setModel("deepseek/deepseek-r1")
            runBlocking { pipe.init() }
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

        /**
         * Split based on provider and then use the data class to populate its core settings. Then, invoke
         * the init function for its pipe.
         */
        when (pipe.getProviderEnum())
        {
            ProviderName.Aws -> {
                // Legacy branch: any persisted settings still tagged ProviderName.Aws are read-only
                // at this point — the BedrockPipe class no longer exists, so we cast to the
                // surviving OpenRouterPipe and skip the Bedrock-only setRegion() call.
                model.setRegion()
                val orPipe = pipe as OpenRouterPipe
                orPipe.setModel(model.modelName)
                    .setTopP(model.topP)
                    .setTemperature(model.temperature)
                    .setMaxTokens(model.maxTokens)

                runBlocking {
                    orPipe.init()
                }

            }
            ProviderName.Nai -> continue
            ProviderName.Gemini -> continue
            ProviderName.Gpt -> continue
            ProviderName.Ollama -> continue
            ProviderName.OpenRouter -> {
                val orPipe = pipe as OpenRouterPipe
                orPipe.setModel(model.modelName)
                    .setTemperature(model.temperature)
                    .setTopP(model.topP)
                    .setMaxTokens(model.maxTokens)
                orPipe.setApiKey(OpenRouterEnv.resolveApiKey())
                runBlocking { orPipe.init() }
            }
        }
    }
}

fun deepSeekModelName() : String = "deepseek/deepseek-r1"
fun deepSeekV3ModelName() : String = "deepseek/deepseek-v3.1-terminus"
fun novaModelName() : String = "amazon/nova-pro-v1"
fun novaLiteModelName() : String = "amazon/nova-lite-v1"
fun gptModelName() : String = "openai/gpt-oss-20b"
fun gpt120bModelName() : String = "openai/gpt-oss-120b"
fun claudeModelName() : String = "anthropic/claude-sonnet-4"
fun qwen235BModelName() : String = "qwen/qwen3-235b-a22b-2507"
fun qwen32BModelName() : String = "qwen/qwen3-32b"
fun qwenCoder480BModelName() : String = "qwen/qwen3-235b-a22b-2507"
fun qwenCoder30BModelName() : String = "qwen/qwen3-coder-30b-a3b-instruct"
fun palmyraX5ModelName() : String = "writer/palmyra-x5"
fun llamaMaverickModelName() : String = "meta-llama/llama-4-maverick"
fun llama70BModelName() : String = "meta-llama/llama-3.3-70b-instruct"
fun llama405BModelName() : String = "nousresearch/hermes-3-llama-3.1-405b"
fun jambaModelName() : String = "ai21/jamba-large-1.7"

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
