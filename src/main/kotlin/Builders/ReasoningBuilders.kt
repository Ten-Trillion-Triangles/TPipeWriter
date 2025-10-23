package Builders

import Defaults.BedrockConfiguration
import Defaults.reasoning.ReasoningBuilder.reasonWithBedrock
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
    region: String = "us-west-2",
    model: String = "qwen.qwen3-coder-480b-a35b-v1:0",
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

    val bedrockSettings = BedrockConfiguration(
        region = region,
        model = model
    )

    val pipeSettings = PipeSettings(
        model = model,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
    )

    val pipe = reasonWithBedrock(
        bedrockSettings,
        reasoningSettings,
        pipeSettings
    )

    runBlocking { pipe.init() }

    return pipe
}


fun obsessivePlannerBuilder(): Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.ComprehensivePlan,
        depth = ReasoningDepth.High,
        duration = ReasoningDuration.Long,
        reasoningInjector = ReasoningInjector.AfterUserPrompt,
        numberOfRounds = 1
    )

    val config = BedrockConfiguration(
        region = "us-west-2",
        model = "qwen.qwen3-coder-480b-a35b-v1:0"
    )

    val pipeSettings = PipeSettings(
        model = "qwen.qwen3-coder-480b-a35b-v1:0",
        temperature = 1.0,
        topP = .7,
        maxTokens = 32000,
    )

    val pipe = reasonWithBedrock(
        config,
        reasoningSettings,
        pipeSettings
    )

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

    val config = BedrockConfiguration(
        region = "us-west-2",
        model = "qwen.qwen3-coder-480b-a35b-v1:0"
    )

    val pipeSettings = PipeSettings(
        model = "qwen.qwen3-coder-480b-a35b-v1:0",
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000
    )

    val pipe = reasonWithBedrock(
        config,
        reasoningSettings,
        pipeSettings
    )

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

    val bedrockSettings = BedrockConfiguration(
        region = "us-west-2",
        model = "qwen.qwen3-coder-480b-a35b-v1:0"
    )

    val pipeSettings = PipeSettings(
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000
    )

    val pipe = reasonWithBedrock(
        bedrockSettings,
        reasoningSettings,
        pipeSettings
    )

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

    val bedrockSettings = BedrockConfiguration(
        region = "us-west-2",
        model = "qwen.qwen3-coder-480b-a35b-v1:0"
    )

    val pipeSettings = PipeSettings(
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000
    )

    val pipe = reasonWithBedrock(
        bedrockSettings,
        reasoningSettings,
        pipeSettings
    )

    runBlocking { pipe.init() }

    return pipe
}

fun explicitCotBuilder() : Pipe
{
    val reasoningSettings = ReasoningSettings(
        reasoningMethod = ReasoningMethod.ExplicitCot,
        depth = ReasoningDepth.High,
        duration = ReasoningDuration.Long,
        reasoningInjector = ReasoningInjector.AfterUserPrompt,
        numberOfRounds = 1
    )

    val bedrockSettings = BedrockConfiguration(
        region = "us-west-2",
        model = "qwen.qwen3-coder-480b-a35b-v1:0"
    )

    val pipeSettings = PipeSettings(
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 115000
    )

    val pipe = reasonWithBedrock(
        bedrockSettings,
        reasoningSettings,
        pipeSettings
    )

    runBlocking { pipe.init() }

    return pipe
}
