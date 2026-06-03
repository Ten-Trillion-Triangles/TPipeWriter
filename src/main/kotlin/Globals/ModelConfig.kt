package Globals

object ModelConfig
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
     * Replaces the previous 480B Coder reference. Same Qwen3 MoE family, used for writing and
     * reasoning tasks (theme analysis, body text expansion, CoT planning) — not code.
     */
    val qwenCoder480B = "qwen/qwen3-235b-a22b-2507"

    /**
     * Mixture of experts version of coder.
     */
    val qwenCoder30B = "qwen/qwen3-coder-30b-a3b-instruct"

    /**
     * Palmyra by Writer */
    val PalmyraX5 = "writer/palmyra-x5"

    fun init()
    {
        /* no-op: OpenRouter needs no inference profile bindings */
    }
}
