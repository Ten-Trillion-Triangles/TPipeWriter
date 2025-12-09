package Builders

import Defaults.BedrockConfiguration
import Defaults.reasoning.ReasoningBuilder.reasonWithBedrock
import Shell.loadSettings
import Util.enablePipelineStreaming
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Context.ConverseHistory
import com.TTT.Context.ConverseRole
import com.TTT.Debug.withTracing
import com.TTT.Pipe.TokenBudgetSettings
import com.TTT.Pipeline.Pipeline
import env.bedrockEnv
import kotlinx.coroutines.runBlocking


fun buildCharacterPipeline(character: String) : Pipeline
{
    val deepseekModelName = "deepseek.r1-v1:0" //us-east-2
    val claudeModelName = "anthropic.claude-sonnet-4-20250514-v1:0" //us-east-1
    val novaModelName = "amazon.nova-lite-v1:0"
    val novaProModelName = "amazon.nova-pro-v1:0"
    val gptOssModelName = "openai.gpt-oss-20b-1:0" //us-west-2
    val gptOss120bModelName = "openai.gpt-oss-120b-1:0"

    //us-east-2
    val llamaMaverick = "us.meta.llama4-maverick-17b-instruct-v1:0"
    val llama70B = "us.meta.llama3-3-70b-instruct-v1:0"
    val llama405B = "us.meta.llama3-1-405b-instruct-v1:0"

    //us-east-1
    val jambaModelName = "ai21.jamba-1-5-large-v1:0"





    //us-west-2
    /**
     * General purpose version of R1 supposedly far better at creative writing. Supports reasoning being turned
     * on or off.
     */
    val deepseekV31 = "deepseek.v3-v1:0"


    //us-west-2
    /**
     * 235B parameter mixture of experts model. Supports reasoning. Instruct style assitant.
     */
    val qwen235B = "qwen.qwen3-235b-a22b-2507-v1:0"

    /**
     * Condensed version. Supposedly good at writing. Supports reasoning.
     */
    val qwen32B = "qwen.qwen3-32b-v1:0"

    /**
     * Supposedly optimized for coding. Supports reasoning.
     */
    val qwenCoder480B = "qwen.qwen3-coder-480b-a35b-v1:0"

    /**
     * Mixture of experts version of coder.
     */
    val qwenCoder30B = "qwen.qwen3-coder-30b-a3b-v1:0"

    /**
     * Palmyra by Writer */
    val PalmyraX5 = "writer.palmyra-x5-v1:0"

    val settings = loadSettings()

    /**
     * Required boilerplate to map us to the arn, or inference ID. This is because most models cannot be
     * invoked directly, and must be bound to a profile.
     */
    bedrockEnv.loadInferenceConfig()
    bedrockEnv.bindInferenceProfile("deepseek.r1-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0")
    bedrockEnv.bindInferenceProfile("amazon.nova-pro-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-pro-v1:0")
    bedrockEnv.bindInferenceProfile("amazon.nova-lite-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-lite-v1:0")
    bedrockEnv.bindInferenceProfile(claudeModelName, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.anthropic.claude-sonnet-4-20250514-v1:0")
    bedrockEnv.bindInferenceProfile(llamaMaverick, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama4-maverick-17b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(llama70B, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-3-70b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(llama405B, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-1-405b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(PalmyraX5, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.writer.palmyra-x5-v1:0")

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

    val chatPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
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
    val deepseekModelName = "deepseek.r1-v1:0" //us-east-2
    val claudeModelName = "anthropic.claude-sonnet-4-20250514-v1:0" //us-east-1
    val novaModelName = "amazon.nova-lite-v1:0"
    val novaProModelName = "amazon.nova-pro-v1:0"
    val gptOssModelName = "openai.gpt-oss-20b-1:0" //us-west-2
    val gptOss120bModelName = "openai.gpt-oss-120b-1:0"

    //us-east-2
    val llamaMaverick = "us.meta.llama4-maverick-17b-instruct-v1:0"
    val llama70B = "us.meta.llama3-3-70b-instruct-v1:0"
    val llama405B = "us.meta.llama3-1-405b-instruct-v1:0"

    //us-east-1
    val jambaModelName = "ai21.jamba-1-5-large-v1:0"





    //us-west-2
    /**
     * General purpose version of R1 supposedly far better at creative writing. Supports reasoning being turned
     * on or off.
     */
    val deepseekV31 = "deepseek.v3-v1:0"


    //us-west-2
    /**
     * 235B parameter mixture of experts model. Supports reasoning. Instruct style assitant.
     */
    val qwen235B = "qwen.qwen3-235b-a22b-2507-v1:0"

    /**
     * Condensed version. Supposedly good at writing. Supports reasoning.
     */
    val qwen32B = "qwen.qwen3-32b-v1:0"

    /**
     * Supposedly optimized for coding. Supports reasoning.
     */
    val qwenCoder480B = "qwen.qwen3-coder-480b-a35b-v1:0"

    /**
     * Mixture of experts version of coder.
     */
    val qwenCoder30B = "qwen.qwen3-coder-30b-a3b-v1:0"

    /**
     * Palmyra by Writer */
    val PalmyraX5 = "writer.palmyra-x5-v1:0"

    val settings = loadSettings()

    /**
     * Required boilerplate to map us to the arn, or inference ID. This is because most models cannot be
     * invoked directly, and must be bound to a profile.
     */
    bedrockEnv.loadInferenceConfig()
    bedrockEnv.bindInferenceProfile("deepseek.r1-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0")
    bedrockEnv.bindInferenceProfile("amazon.nova-pro-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-pro-v1:0")
    bedrockEnv.bindInferenceProfile("amazon.nova-lite-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-lite-v1:0")
    bedrockEnv.bindInferenceProfile(claudeModelName, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.anthropic.claude-sonnet-4-20250514-v1:0")
    bedrockEnv.bindInferenceProfile(llamaMaverick, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama4-maverick-17b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(llama70B, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-3-70b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(llama405B, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-1-405b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(PalmyraX5, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.writer.palmyra-x5-v1:0")

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

    val chatPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
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