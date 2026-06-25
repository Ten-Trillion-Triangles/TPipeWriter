package Builders

import Defaults.reasoning.ReasoningBuilder
import Defaults.reasoning.ReasoningSettings
import com.TTT.Pipe.Pipe
import com.TTT.Structs.PipeSettings
import genericOpenAIPipe.env.GenericOpenAIEnv as genericOpenAIEnv
import genericOpenAIPipe.api.ApiMode
import genericOpenAIPipe.GenericOpenAIPipe

/**
 * MiniMax-M3 reasoning adapter — mirrors the `reasonWithOpenRouter` pattern
 * from the OpenRouter branch but explicitly DISABLES reasoning because MiniMax-M3
 * is the no-reasoning variant of the MiniMax family.
 *
 * Without this adapter, the ReasoningBuilder would default-enable reasoning on
 * the pipe (writing a `reasoning: {effort, max_tokens, enabled=true}` block into
 * the wire payload). MiniMax-M3 is hardwired to ignore that block — but the
 * wire traffic and the trace metadata would still record `reasoningEnabled=true`,
 * which is misleading. This adapter forces both knobs (the base Pipe flag and the
 * wire `ReasoningConfig.enabled`) to false so the trace shows the true state.
 *
 * Migration to MiniMax-M2.7+ (which DOES support reasoning) is a one-line change:
 * delete the `pipe.disableReasoning()` call below. The wire payload will then
 * carry reasoning per the caller's `ReasoningSettings`.
 */
fun reasonWithMiniMax(
    model: String,
    reasoningSettings: ReasoningSettings,
    pipeSettings: PipeSettings?,
    baseUrl: String = "https://api.minimax.io/v1",
    apiKey: String = "",
    apiMode: ApiMode = ApiMode.OpenAIResponses
): Pipe {
    val pipe = GenericOpenAIPipe()
    pipe.setBaseUrl(baseUrl)
    pipe.setApiKey(apiKey.ifBlank { genericOpenAIEnv.resolveApiKey() })
    pipe.setApiMode(apiMode)
    pipe.setModel(model)
    // MiniMax-M3 is the no-reasoning variant. Force both knobs off.
    pipe.disableReasoning()
    // ReasoningBuilder.assignDefaults still runs so per-pipe ReasoningSettings
    // are honored for non-reasoning-specific config (system prompts, context
    // window, pipe name, etc).
    ReasoningBuilder.assignDefaults(reasoningSettings, pipeSettings, pipe)
    return pipe
}