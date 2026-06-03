package Builders

import Shell.loadSettings
import Util.enablePipelineStreaming
import com.TTT.Context.ConverseHistory
import com.TTT.Context.ConverseRole
import com.TTT.Debug.withTracing
import com.TTT.Pipe.TokenBudgetSettings
import com.TTT.Pipeline.Pipeline
import kotlinx.coroutines.runBlocking
import openrouterPipe.OpenRouterPipe


fun buildCharacterPipeline(character: String) : Pipeline
{
    val deepseekModelName = "deepseek/deepseek-r1"
    val claudeModelName = "anthropic/claude-sonnet-4"
    val novaModelName = "amazon/nova-lite-v1"
    val novaProModelName = "amazon/nova-pro-v1"
    val gptOssModelName = "openai/gpt-oss-20b"
    val gptOss120bModelName = "openai/gpt-oss-120b"

   
    val llamaMaverick = "meta-llama/llama-4-maverick"
    val llama70B = "meta-llama/llama-3.3-70b-instruct"
    val llama405B = "nousresearch/hermes-3-llama-3.1-405b"

   
    val jambaModelName = "ai21/jamba-large-1.7"





   
    /**
     * General purpose version of R1 supposedly far better at creative writing. Supports reasoning being turned
     * on or off.
     */
    val deepseekV31 = "deepseek/deepseek-v3.1-terminus"


   
    /**
     * 235B parameter mixture of experts model. Supports reasoning. Instruct style assitant.
     */
    val qwen235B = "qwen/qwen3-235b-a22b-2507"

    /**
     * Condensed version. Supposedly good at writing. Supports reasoning.
     */
    val qwen32B = "qwen/qwen3-32b"

    /**
     * Supposedly optimized for coding. Supports reasoning.
     */
    val qwenCoder480B = "qwen/qwen3-235b-a22b-2507"

    /**
     * Mixture of experts version of coder.
     */
    val qwenCoder30B = "qwen/qwen3-coder-30b-a3b-instruct"

    /**
     * Palmyra by Writer */
    val PalmyraX5 = "writer/palmyra-x5"

    val settings = loadSettings()

    val reasoningPipe = authorBuilder(character)

    val writerBudgetSettings = TokenBudgetSettings(
        maxTokens = 8000,
        contextWindowSize = 980000,
        allowUserPromptTruncation = true,
        )

    val standardBudgetSettings = TokenBudgetSettings(
        maxTokens = 8000,
        contextWindowSize = 120000,
        allowUserPromptTruncation = true,
        )

    val chatPipe = OpenRouterPipe()
        .enableTracing()
        .setModel(PalmyraX5)
        .setTemperature(1.0)
        .setTopP(.8)
        .truncateModuleContext()
        .autoTruncateContext()
        .setTokenBudget(writerBudgetSettings)
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
        .setFooterPrompt("""You must always stay in character at all
            |times and answer as $character would in all tasks given to you.""")

    val chatPipeline = Pipeline()
        .add(chatPipe)

    runBlocking {
        enablePipelineStreaming(chatPipeline)
        chatPipeline.init(true)
    }

    return chatPipeline
}



fun buildCharacterPipelineWithStory(character: String) : Pipeline
{
    val deepseekModelName = "deepseek/deepseek-r1"
    val claudeModelName = "anthropic/claude-sonnet-4"
    val novaModelName = "amazon/nova-lite-v1"
    val novaProModelName = "amazon/nova-pro-v1"
    val gptOssModelName = "openai/gpt-oss-20b"
    val gptOss120bModelName = "openai/gpt-oss-120b"

   
    val llamaMaverick = "meta-llama/llama-4-maverick"
    val llama70B = "meta-llama/llama-3.3-70b-instruct"
    val llama405B = "nousresearch/hermes-3-llama-3.1-405b"

   
    val jambaModelName = "ai21/jamba-large-1.7"





   
    /**
     * General purpose version of R1 supposedly far better at creative writing. Supports reasoning being turned
     * on or off.
     */
    val deepseekV31 = "deepseek/deepseek-v3.1-terminus"


   
    /**
     * 235B parameter mixture of experts model. Supports reasoning. Instruct style assitant.
     */
    val qwen235B = "qwen/qwen3-235b-a22b-2507"

    /**
     * Condensed version. Supposedly good at writing. Supports reasoning.
     */
    val qwen32B = "qwen/qwen3-32b"

    /**
     * Supposedly optimized for coding. Supports reasoning.
     */
    val qwenCoder480B = "qwen/qwen3-235b-a22b-2507"

    /**
     * Mixture of experts version of coder.
     */
    val qwenCoder30B = "qwen/qwen3-coder-30b-a3b-instruct"

    /**
     * Palmyra by Writer */
    val PalmyraX5 = "writer/palmyra-x5"

    val settings = loadSettings()

    val writerBudgetSettings = TokenBudgetSettings(
        maxTokens = 8000,
        contextWindowSize = 990000,
        allowUserPromptTruncation = true,
    )

    val standardBudgetSettings = TokenBudgetSettings(
        maxTokens = 8000,
        contextWindowSize = 120000,
        allowUserPromptTruncation = true,
    )

    val reasoningPipe = authorBuilder(character)
        .setTokenBudget(writerBudgetSettings)
        .setPipeName("Thinking pipe")

    val chatPipe = OpenRouterPipe()
        .enableTracing()
        .setModel(PalmyraX5)
        .setTemperature(1.0)
        .setTopP(.8)
        .pullGlobalContext()
        .setPageKey("main")
        .truncateModuleContext()
        .autoTruncateContext()
        .setTokenBudget(writerBudgetSettings)
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
        .setFooterPrompt("""You must always stay in character at all
            |times and answer as $character would in all tasks given to you.""")
        .autoInjectContext("""The "main" key has a story the user wishes you to help them with. If they
            |make any requests regarding the story, examine the context data you have located at the "main" key.
            |The "story guide" key is the guide for the story as a whole. The "chapter guide" key is the guide
            |for the current chapter.
        """.trimMargin())

    val chatPipeline = Pipeline()
        .add(chatPipe)

    runBlocking {
        enablePipelineStreaming(chatPipeline)
        chatPipeline.init(true)
    }

    return chatPipeline
}