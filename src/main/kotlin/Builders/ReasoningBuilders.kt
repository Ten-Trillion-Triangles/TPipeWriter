package Builders

import Defaults.reasoning.ReasoningDepth
import Defaults.reasoning.ReasoningDuration
import Defaults.reasoning.ReasoningInjector
import Defaults.reasoning.ReasoningMethod
import Defaults.reasoning.ReasoningSettings
import Globals.ModelConfig
import com.TTT.Pipe.Pipe
import com.TTT.Structs.PipeSettings
import kotlinx.coroutines.runBlocking

/**
 * Reasoning pipe builders for the MiniMax-M3 Generic OpenAI edition.
 *
 * Each builder returns a reasoning pipe constructed via `reasonWithMiniMax`,
 * which forces reasoning OFF at the wire level (MiniMax-M3 is the no-reasoning
 * variant of the MiniMax family) while still applying the per-pipe
 * `ReasoningSettings` for non-reasoning-specific configuration (system prompt
 * injection, context window, pipe name, etc).
 *
 * Migration to MiniMax-M2.7+ (which DOES support reasoning) is a one-line
 * change in `MiniMaxReasoning.kt` — delete the `pipe.disableReasoning()` call.
 */

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
        model = ModelConfig.primaryModelName,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        pipeName = "author"
    )

    val pipe = reasonWithMiniMax(
        ModelConfig.primaryModelName,
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
        reasoningInjector = ReasoningInjector.SystemPrompt,
        numberOfRounds = 1
    )

    val pipeSettings = PipeSettings(
        model = ModelConfig.primaryModelName,
        temperature = 1.0,
        topP = .7,
        maxTokens = 32000,
        pipeName = "obsessive planner"
    )

    val pipe = reasonWithMiniMax(
        ModelConfig.primaryModelName,
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

    val pipeSettings = PipeSettings(
        model = ModelConfig.primaryModelName,
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 512000,
        pipeName = "best idea"
    )

    val pipe = reasonWithMiniMax(
        ModelConfig.primaryModelName,
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

    val pipeSettings = PipeSettings(
        model = ModelConfig.primaryModelName,
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 512000,
        pipeName = "structured cot"
    )

    val pipe = reasonWithMiniMax(
        ModelConfig.primaryModelName,
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

    val pipeSettings = PipeSettings(
        model = ModelConfig.primaryModelName,
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 512000,
        pipeName = "process focused"
    )

    val pipe = reasonWithMiniMax(
        ModelConfig.primaryModelName,
        reasoningSettings,
        pipeSettings
    )

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
        model = ModelConfig.primaryModelName,
        temperature = .7,
        topP = .7,
        maxTokens = 8000,
        contextWindowSize = 512000,
        pipeName = "explicit cot"
    )

    val pipe = reasonWithMiniMax(
        ModelConfig.primaryModelName,
        reasoningSettings,
        pipeSettings
    )

    runBlocking { pipe.init() }

    return pipe
}