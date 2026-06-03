package Builders

import Defaults.OpenRouterConfiguration
import Defaults.reasoning.ReasoningBuilder
import Defaults.reasoning.ReasoningSettings
import com.TTT.Pipe.Pipe
import com.TTT.Structs.PipeSettings
import env.OpenRouterEnv
import openrouterPipe.OpenRouterPipe

fun reasonWithOpenRouter(
    config: OpenRouterConfiguration,
    reasoningSettings: ReasoningSettings,
    pipeSettings: PipeSettings?
): Pipe {
    // Note: setModel() is inherited from Pipe and returns Pipe, not OpenRouterPipe,
    // so we can't keep a fluent chain — call each OpenRouter-only setter on `pipe`
    // directly.
    val pipe = OpenRouterPipe()
    pipe.setModel(config.model)
    pipe.setApiKey(config.apiKey.ifBlank { OpenRouterEnv.resolveApiKey() })
    pipe.setOpenRouterTitle(config.openRouterTitle)
    pipe.setHttpReferer(config.httpReferer)
    ReasoningBuilder.assignDefaults(reasoningSettings, pipeSettings, pipe)
    return pipe
}

fun reasonWithOpenRouter(
    model: String,
    reasoningSettings: ReasoningSettings,
    pipeSettings: PipeSettings?
): Pipe = reasonWithOpenRouter(
    OpenRouterConfiguration(
        model = model,
        apiKey = OpenRouterEnv.resolveApiKey(),
        openRouterTitle = "TPipeWriter",
        httpReferer = "https://github.com/cage/TPipeWriter"
    ),
    reasoningSettings,
    pipeSettings
)
