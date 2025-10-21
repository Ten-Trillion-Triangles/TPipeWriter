package Structs

import bedrockPipe.BedrockPipe
import com.TTT.Enums.ProviderName
import com.TTT.Pipe.Pipe
import com.TTT.Pipeline.Pipeline
import kotlinx.coroutines.runBlocking

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
      return when (modelName)
        {
            deepSeekModelName() -> "us-east-2"
            novaModelName() -> "us-east-2"
            novaLiteModelName() -> "us-east-2"
            gptModelName() -> "us-west-2"
            gpt120bModelName() -> "us-west-2"
            claudeModelName() -> "us-east-1"
          else -> {""}
      }
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
    val newModelSettings = ModelSettings(
        provider = pipeSettings.provider,
        modelName = pipeSettings.model,
        temperature = pipeSettings.temperature,
        topP = pipeSettings.topP,
        pipeName = pipeSettings.pipeName,
        maxTokens = pipeSettings.maxTokens)

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
        if(pipe is BedrockPipe)
        {
            pipe.setRegion("us-east-2")
                .setModel("deepseek.r1-v1:0")

            runBlocking {
                pipe.init()
            }
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
                model.setRegion()
                val bedrockPipe = pipe as BedrockPipe
                bedrockPipe.setRegion(model.region)
                    .setModel(model.modelName)
                    .setTopP(model.topP)
                    .setTemperature(model.temperature)
                    .setMaxTokens(model.maxTokens)

                runBlocking {
                    bedrockPipe.init()
                }

            }
            ProviderName.Nai -> continue
            ProviderName.Gemini -> continue
            ProviderName.Gpt -> continue
            ProviderName.Ollama -> continue
        }
    }
}

fun deepSeekModelName() : String = "deepseek.r1-v1:0"
fun novaModelName() : String = "amazon.nova-pro-v1:0"
fun novaLiteModelName() : String = "amazon.nova-lite-v1:0"
fun gptModelName() : String = "openai.gpt-oss-20b-1:0"
fun gpt120bModelName() : String = "openai.gpt-oss-120b-1:0"
fun claudeModelName() : String = "anthropic.claude-sonnet-4-20250514-v1:0"

/**
 * Export ModelSettings map to JSON string.
 */
fun exportModelSettingsToJson(settingsMap: Map<String, ModelSettings>): String
{
    return com.TTT.Util.serialize(settingsMap)
}

/**
 * Import ModelSettings map from JSON string.
 */
fun importModelSettingsFromJson(jsonString: String): Map<String, ModelSettings>?
{
    return com.TTT.Util.deserialize<Map<String, ModelSettings>>(jsonString)
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
