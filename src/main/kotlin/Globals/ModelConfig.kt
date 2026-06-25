package Globals

/**
 * Model registry for the MiniMax-M3 Generic OpenAI edition.
 *
 * Single-model edition: every pipeline uses MiniMax-M3 (hosted at
 * https://api.minimax.io/v1 via the OpenAI Responses API). The variable names
 * are preserved from the Bedrock/OpenRouter branches so downstream code in
 * Env.kt and the Builders slash-star pipelines does not need its call sites renamed —
 * they keep calling ModelConfig.deepseekModelName, ModelConfig.claudeModelName,
 * etc., but the values now all resolve to MiniMax-M3.
 *
 * This collapses the Bedrock-era registry of 17 distinct models (Claude, Nova,
 * DeepSeek, Llama, Qwen, Jamba, Palmyra, etc.) down to one. Per-pipe model
 * overrides can be reintroduced later if needed; for now YAGNI.
 *
 * init() is a no-op because MiniMax-M3 requires no ARN binding, no region,
 * and no inference profile. Authentication is the MINIMAX_API_KEY environment
 * variable, resolved by GenericOpenAIEnv.
 */
object ModelConfig
{
    const val deepseekModelName = "MiniMax-M3"
    const val claudeModelName = "MiniMax-M3"
    const val novaModelName = "MiniMax-M3"
    const val novaProModelName = "MiniMax-M3"
    const val gptOssModelName = "MiniMax-M3"
    const val gptOss120bModelName = "MiniMax-M3"
    const val llamaMaverick = "MiniMax-M3"
    const val llama70B = "MiniMax-M3"
    const val llama405B = "MiniMax-M3"
    const val jambaModelName = "MiniMax-M3"
    const val deepseekV31 = "MiniMax-M3"
    const val qwen235B = "MiniMax-M3"
    const val qwen32B = "MiniMax-M3"
    const val qwenCoder480B = "MiniMax-M3"
    const val qwenCoder30B = "MiniMax-M3"
    const val qwenNext80B = "MiniMax-M3"
    const val qwenVL = "MiniMax-M3"
    const val PalmyraX5 = "MiniMax-M3"

    /**
     * Canonical model id for the MiniMax-M3 Generic OpenAI edition.
     * Use this when wiring a new pipe that does not have a legacy model-name
     * variable already in this registry.
     */
    const val primaryModelName = "MiniMax-M3"

    /**
     * Canonical 512K context window size for MiniMax-M3.
     * Used by TPipeBudgeting calculations across all pipes.
     */
    const val MiniMaxContextWindowSize: Int = 512000

    fun init()
    {
        // No-op: MiniMax is a hosted model. No ARN binding, no region, no
        // inference profile. Authentication is via MINIMAX_API_KEY.
    }
}