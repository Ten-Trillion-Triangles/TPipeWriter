package Globals

import com.TTT.Enums.ContextWindowSettings
import com.TTT.Pipe.TokenBudgetSettings

/**
 * Central token budget presets for the writer module.
 *
 * The values intentionally leave slack under the published model limits so the app
 * stays comfortably below provider caps even if token counting is slightly off.
 */
object WriterTokenBudgets
{
    private const val DEEPSEEK_CONTEXT = 120_000
    private const val GPT_OSS_CONTEXT = 120_000
    private const val NOVA_CONTEXT = 290_000
    private const val QWEN3_CONTEXT = 124_000
    private const val QWEN_CODER_CONTEXT = 252_000

    private fun buildBudget(
        contextWindowSize: Int,
        maxTokens: Int,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return TokenBudgetSettings(
            contextWindowSize = contextWindowSize,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput,
            allowUserPromptTruncation = true,
            truncationMethod = ContextWindowSettings.TruncateTop
        )
    }

    fun deepSeekR1(
        maxTokens: Int = 8_000,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return buildBudget(
            contextWindowSize = DEEPSEEK_CONTEXT,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput
        )
    }

    fun deepSeekV31(
        maxTokens: Int = 8_000,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return buildBudget(
            contextWindowSize = DEEPSEEK_CONTEXT,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput
        )
    }

    fun gptOss20b(
        maxTokens: Int = 5_000,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return buildBudget(
            contextWindowSize = GPT_OSS_CONTEXT,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput
        )
    }

    fun gptOss120b(
        maxTokens: Int = 20_000,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return buildBudget(
            contextWindowSize = GPT_OSS_CONTEXT,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput
        )
    }

    fun novaPro(
        maxTokens: Int = 4_000,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return buildBudget(
            contextWindowSize = NOVA_CONTEXT,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput
        )
    }

    fun qwen3(
        maxTokens: Int = 32_000,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return buildBudget(
            contextWindowSize = QWEN3_CONTEXT,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput
        )
    }

    fun qwenCoder480B(
        maxTokens: Int = 32_000,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return buildBudget(
            contextWindowSize = QWEN_CODER_CONTEXT,
            maxTokens = maxTokens,
            reasoningBudget = reasoningBudget,
            subtractReasoningFromInput = subtractReasoningFromInput
        )
    }

    fun qwenCoder480BReasoning(maxTokens: Int = 8_000): TokenBudgetSettings
    {
        return qwenCoder480B(maxTokens = maxTokens)
    }

    fun forModel(
        model: String,
        maxTokens: Int,
        reasoningBudget: Int? = null,
        subtractReasoningFromInput: Boolean = false
    ): TokenBudgetSettings
    {
        return when(model)
        {
            ModelConfig.deepseekModelName -> deepSeekR1(maxTokens, reasoningBudget, subtractReasoningFromInput)
            ModelConfig.deepseekV31 -> deepSeekV31(maxTokens, reasoningBudget, subtractReasoningFromInput)
            ModelConfig.gptOssModelName -> gptOss20b(maxTokens, reasoningBudget, subtractReasoningFromInput)
            ModelConfig.gptOss120bModelName -> gptOss120b(maxTokens, reasoningBudget, subtractReasoningFromInput)
            ModelConfig.novaModelName,
            ModelConfig.novaProModelName -> novaPro(maxTokens, reasoningBudget, subtractReasoningFromInput)
            ModelConfig.qwenCoder480B,
            ModelConfig.qwenCoder30B -> qwenCoder480B(maxTokens, reasoningBudget, subtractReasoningFromInput)
            ModelConfig.qwen235B,
            ModelConfig.qwen32B,
            ModelConfig.qwenNext80B,
            ModelConfig.qwenVL -> qwen3(maxTokens, reasoningBudget, subtractReasoningFromInput)
            else -> buildBudget(
                contextWindowSize = maxTokens,
                maxTokens = maxTokens,
                reasoningBudget = reasoningBudget,
                subtractReasoningFromInput = subtractReasoningFromInput
            )
        }
    }
}
