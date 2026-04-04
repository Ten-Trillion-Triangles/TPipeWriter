package Globals

import env.bedrockEnv

object ModelConfig
{
    data class ModelEntry(val id: String, val region: String, val kind: String, val lookupId: String)

    val manifest = listOf(
        ModelEntry("deepseek.r1-v1:0", "us-east-2", "profile", "us.deepseek.r1-v1:0"),
        ModelEntry("deepseek.v3-v1:0", "us-west-2", "profile", "us.deepseek.v3-v1:0"),
        ModelEntry("amazon.nova-pro-v1:0", "us-east-2", "profile", "us.amazon.nova-pro-v1:0"),
        ModelEntry("amazon.nova-lite-v1:0", "us-east-2", "profile", "us.amazon.nova-lite-v1:0"),
        ModelEntry("amazon.nova-2-lite-v1:0", "us-east-2", "profile", "us.amazon.nova-2-lite-v1:0"),
        ModelEntry("anthropic.claude-sonnet-4-20250514-v1:0", "us-east-2", "profile", "us.anthropic.claude-sonnet-4-20250514-v1:0"),
        ModelEntry("openai.gpt-oss-20b-1:0", "us-west-2", "profile", "us.openai.gpt-oss-20b-1:0"),
        ModelEntry("openai.gpt-oss-120b-1:0", "us-west-2", "profile", "us.openai.gpt-oss-120b-1:0"),
        ModelEntry("us.meta.llama4-maverick-17b-instruct-v1:0", "us-east-2", "profile", "us.meta.llama4-maverick-17b-instruct-v1:0"),
        ModelEntry("us.meta.llama3-3-70b-instruct-v1:0", "us-east-2", "profile", "us.meta.llama3-3-70b-instruct-v1:0"),
        ModelEntry("us.meta.llama3-1-405b-instruct-v1:0", "us-east-2", "profile", "us.meta.llama3-1-405b-instruct-v1:0"),
        ModelEntry("ai21.jamba-1-5-large-v1:0", "us-east-1", "profile", "us.ai21.jamba-1-5-large-v1:0"),
        ModelEntry("qwen.qwen3-235b-a22b-2507-v1:0", "us-west-2", "profile", "us.qwen.qwen3-235b-a22b-2507-v1:0"),
        ModelEntry("qwen.qwen3-32b-v1:0", "us-west-2", "profile", "us.qwen.qwen3-32b-v1:0"),
        ModelEntry("qwen.qwen3-coder-480b-a35b-v1:0", "us-west-2", "profile", "us.qwen.qwen3-coder-480b-a35b-v1:0"),
        ModelEntry("qwen.qwen3-coder-30b-a3b-v1:0", "us-west-2", "profile", "us.qwen.qwen3-coder-30b-a3b-v1:0"),
        ModelEntry("qwen.qwen3-next-80b-a3b", "us-west-2", "profile", "us.qwen.qwen3-next-80b-a3b"),
        ModelEntry("qwen.qwen3-vl-235b-a22b", "us-west-2", "profile", "us.qwen.qwen3-vl-235b-a22b"),
        ModelEntry("writer.palmyra-x5-v1:0", "us-west-2", "profile", "us.writer.palmyra-x5-v1:0")
    )

    fun getManifest(): String
    {
        return manifest.joinToString("\n") { "${it.id}|${it.region}|${it.kind}|${it.lookupId}" }
    }

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
    }
}
