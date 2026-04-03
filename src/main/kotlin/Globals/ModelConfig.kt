package Globals

import env.bedrockEnv

object ModelConfig
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

    val qwenNext80B = "qwen.qwen3-next-80b-a3b"

    val qwenVL = "qwen.qwen3-vl-235b-a22b"

    /**
     * Palmyra by Writer */
    val PalmyraX5 = "writer.palmyra-x5-v1:0"

    fun init()
    {
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
        bedrockEnv.bindInferenceProfile("amazon.nova-2-lite-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-2-lite-v1:0")
        bedrockEnv.bindInferenceProfile(deepseekV31, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.deepseek.v3-v1:0")
        bedrockEnv.bindInferenceProfile(qwen235B, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.qwen.qwen3-235b-a22b-2507-v1:0")
        bedrockEnv.bindInferenceProfile(qwen32B, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.qwen.qwen3-32b-v1:0")
        bedrockEnv.bindInferenceProfile(qwenCoder480B, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.qwen.qwen3-coder-480b-a35b-v1:0")
        bedrockEnv.bindInferenceProfile(qwenCoder30B, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.qwen.qwen3-coder-30b-a3b-v1:0")
        bedrockEnv.bindInferenceProfile(gptOssModelName, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.openai.gpt-oss-20b-1:0")
        bedrockEnv.bindInferenceProfile(gptOss120bModelName, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.openai.gpt-oss-120b-1:0")
        bedrockEnv.bindInferenceProfile(jambaModelName, "arn:aws:bedrock:us-east-1:521369004927:inference-profile/us.ai21.jamba-1-5-large-v1:0")
    }
}