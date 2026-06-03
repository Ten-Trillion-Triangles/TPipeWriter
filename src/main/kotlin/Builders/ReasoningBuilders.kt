package Builders

import Defaults.reasoning.ReasoningDepth
import Defaults.reasoning.ReasoningDuration
import Defaults.reasoning.ReasoningInjector
import Defaults.reasoning.ReasoningMethod
import Defaults.reasoning.ReasoningSettings
import com.TTT.Pipe.Pipe
import com.TTT.Structs.PipeSettings
import kotlinx.coroutines.runBlocking

/**
 * Create an author role play reasoning pipe that will take in a given character.
 */
fun authorBuilder(
    author: String,
    depth: ReasoningDepth = ReasoningDepth.Med,
    duration: ReasoningDuration = ReasoningDuration.Med,
    injectionMethod: ReasoningInjector = ReasoningInjector.AfterUserPrompt,
    rounds: Int = 1,
    focusPoints: MutableMap<Int, String> = mutableMapOf(),
    @Suppress("UNUSED_PARAMETER") region: String = "us-west-2", // deprecated: OpenRouter has no region concept
    model: String = "writer/palmyra-x5",
    maxTokens: Int = 8000,
    temperature: Double = 1.0,
    topP: Double = .7
) : Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.RolePlay,
        roleCharacter = author,
        depth = depth,
        duration = duration,
        reasoningInjector = injectionMethod,
        numberOfRounds = rounds,
        focusPoints = focusPoints
    )

    val pipeSettings = PipeSettings(
        model = model,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        pipeName = "author"
    )

    val pipe = reasonWithOpenRouter(model, reasoningSettings, pipeSettings)

    runBlocking { pipe.init() }

    return pipe
}


fun obsessivePlannerBuilder(): Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.ComprehensivePlan,
        depth = ReasoningDepth.High,
        duration = ReasoningDuration.Long,
        reasoningInjector = ReasoningInjector.SystemPrompt,
        numberOfRounds = 1
    )

    val pipeSettings = PipeSettings(
        model = "qwen/qwen3-235b-a22b-2507",
        temperature = 1.0,
        topP = .7,
        maxTokens = 32000,
        pipeName = "obsessive planner"
    )

    val pipe = reasonWithOpenRouter("qwen/qwen3-235b-a22b-2507", reasoningSettings, pipeSettings)

    runBlocking { pipe.init() }

    return pipe
}


fun bestIdeaBuilder(): Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.BestIdea,
        depth = ReasoningDepth.High,
        duration = ReasoningDuration.Long,
        reasoningInjector = ReasoningInjector.AfterUserPrompt
    )

    val pipeSettings = PipeSettings(
        model = "qwen/qwen3-235b-a22b-2507",
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000,
        pipeName = "best idea"
    )

    val pipe = reasonWithOpenRouter("qwen/qwen3-235b-a22b-2507", reasoningSettings, pipeSettings)

    runBlocking { pipe.init() }

    return pipe
}

fun structuredCotBuilder() : Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.StructuredCot,
        depth = ReasoningDepth.High,
        duration = ReasoningDuration.Long,
        reasoningInjector = ReasoningInjector.AfterUserPrompt,
        numberOfRounds = 1
    )

    val pipeSettings = PipeSettings(
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000,
        pipeName = "structured cot"
    )

    val pipe = reasonWithOpenRouter("qwen/qwen3-235b-a22b-2507", reasoningSettings, pipeSettings)

    runBlocking { pipe.init() }

    return pipe
}

fun processFocusedBuilder() : Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.processFocusedCot,
        depth = ReasoningDepth.High,
        duration = ReasoningDuration.Long,
        reasoningInjector = ReasoningInjector.AfterUserPrompt,
        numberOfRounds = 1
    )

    val pipeSettings = PipeSettings(
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000,
        pipeName = "process focused"
    )

    val pipe = reasonWithOpenRouter("qwen/qwen3-235b-a22b-2507", reasoningSettings, pipeSettings)

    runBlocking { pipe.init() }

    return pipe
}

fun explicitCotBuilder(focusPoints: MutableMap<Int, String> = mutableMapOf()) : Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.ExplicitCot,
        depth = ReasoningDepth.High,
        duration = ReasoningDuration.Long,
        reasoningInjector = ReasoningInjector.AfterUserPrompt,
        numberOfRounds = 1,
        focusPoints = focusPoints
    )

    val pipeSettings = PipeSettings(
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000,
        pipeName = "explicit cot"
    )

    val pipe = reasonWithOpenRouter("qwen/qwen3-235b-a22b-2507", reasoningSettings, pipeSettings)

    runBlocking { pipe.init() }

    return pipe
}
