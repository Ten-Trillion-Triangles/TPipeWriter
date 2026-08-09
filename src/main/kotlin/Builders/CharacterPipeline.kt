package Builders

import com.TTT.Context.ConverseHistory
import com.TTT.Context.ConverseRole
import com.TTT.Pipe.MultiPageBudgetStrategy
import com.TTT.Pipe.TokenBudgetSettings
import com.TTT.Pipeline.Pipeline
import genericOpenAIPipe.env.GenericOpenAIEnv as genericOpenAIEnv
import Globals.ModelConfig
import Shell.loadSettings
import genericOpenAIPipe.api.ApiMode
import genericOpenAIPipe.GenericOpenAIPipe
import kotlinx.coroutines.runBlocking

/**
 * Token budget applied to every pipe in CharacterPipeline (both
 * buildCharacterPipeline and buildCharacterPipelineWithStory). Values
 * mirror plusWriterPipelineBudget / expansionPipelineBudget: 512K
 * context window, 12K max output tokens, no silent truncation of the
 * user prompt, no semantic compression. Chat is short-output so 12K
 * is plenty; 8K (the old value) was cutting off longer character
 * responses mid-sentence.
 *
 * Applied per-pipe via the getPipes().forEach block at the end of
 * each build function (mirror of PlusWriter Task-2/3 pattern).
 */
val characterPipelineBudget: TokenBudgetSettings = TokenBudgetSettings(
    contextWindowSize = 512_000,
    maxTokens = 12_000,
    reasoningBudget = null,
    userPromptSize = null,
    allowUserPromptTruncation = false,
    compressUserPrompt = false,
    truncateContextWindowAsString = false,
    preserveTextMatches = true,
    multiPageBudgetStrategy = MultiPageBudgetStrategy.DYNAMIC_SIZE_FILL
)

/**
 * Build a single-pipe pipeline that chats in character with no story
 * context. ConverseHistory wraps multi-turn chat; the LLM stays in
 * character via the system prompt + the authorBuilder reasoning pipe.
 */
fun buildCharacterPipeline(character: String): Pipeline
{
    val settings = loadSettings()

    val reasoningPipe = authorBuilder(character)

    val chatPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .enableTracing()
        .setTemperature(1.0)
        .setTopP(.8)
        .truncateModuleContext()
        .autoTruncateContext()
        .setTokenBudget(characterPipelineBudget)
        .requireJsonPromptInjection()
        .setJsonInput(ConverseHistory())
        .wrapContentWithConverse(ConverseRole.assistant)
        .setReasoningPipe(reasoningPipe)
        .setSystemPrompt("""$character
            |
            |Your job is to assist with whatever the user's request might be.
        """.trimMargin())
        .setMiddlePrompt("""The history of your conversation with the user is provided as your input along with the
            |last turn which is the request the user has just made. Your role in this input is: Assistant.
        """.trimMargin())
        .setPipeName("Character Chat Pipe")
        .setFooterPrompt("""You must always stay in character at all
            |times and answer as $character would in all tasks given to you.""")

    val chatPipeline = Pipeline()
        .add(chatPipe)

    runBlocking {
        chatPipeline.init(true)
    }

    // Apply per-pipe budget + lore + tracking (PlusWriter Task-2/3 mirror).
    return chatPipeline.apply {
        getPipes().forEach {
            it.useEntireContextForLoreSelection()
            it.setTokenBudget(characterPipelineBudget)
            it.enableComprehensiveTokenTracking()
        }
    }
}

/**
 * Build a single-pipe pipeline that chats in character WITH story
 * context pulled from the bank. Adds settings.writingStyle as a
 * writing-style guide into the system prompt when present, so the
 * character-stays-in-character behavior also conforms to the user's
 * chosen prose style. Pulls main + story guide + chapter guide via
 * the pageKey.
 */
fun buildCharacterPipelineWithStory(character: String): Pipeline
{
    val settings = loadSettings()

    val reasoningPipe = authorBuilder(character)
        .setTokenBudget(characterPipelineBudget)
        .setPipeName("Thinking pipe")

    // Compose the writing-style block only when settings.writingStyle is
    // non-empty. The story-guide and chapter-guide refs are injected
    // separately via autoInjectContext (the pageKey already includes
    // 'main' so the bank lookup covers them).
    val writingStyleBlock =
        if (settings.writingStyle.isNotBlank())
            "\nYou will be given the following writing style to adhere to: ${settings.writingStyle}\n" +
            "If the writing style is empty, write in the style the prior story is using. If there is no prior story yet, " +
            "and no writing style provided, then you may write in any style that fits the request from the user.\n"
        else ""

    val chatPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .enableTracing()
        .setTemperature(1.0)
        .setTopP(.8)
        .pullGlobalContext()
        .setPageKey("main")
        .truncateModuleContext()
        .autoTruncateContext()
        .setTokenBudget(characterPipelineBudget)
        .requireJsonPromptInjection()
        .setJsonInput(ConverseHistory())
        .wrapContentWithConverse(ConverseRole.assistant)
        .setReasoningPipe(reasoningPipe)
        .setSystemPrompt("""$character
            |
            |Your job is to assist with whatever the user's request might be.
            |$writingStyleBlock
        """.trimMargin())
        .setMiddlePrompt("""The history of your conversation with the user is provided as your input along with the
            |last turn which is the request the user has just made. Your role in this input is: Assistant.
        """.trimMargin())
        .setPipeName("Character Chat Pipe")
        .setFooterPrompt("""You must always stay in character at all
            |times and answer as $character would in all tasks given to you.""")

    val chatPipeline = Pipeline()
        .add(chatPipe)

    runBlocking {
        chatPipeline.init(true)
    }

    // Apply per-pipe budget + lore + tracking (PlusWriter Task-2/3 mirror).
    return chatPipeline.apply {
        getPipes().forEach {
            it.useEntireContextForLoreSelection()
            it.setTokenBudget(characterPipelineBudget)
            it.enableComprehensiveTokenTracking()
        }
    }
}