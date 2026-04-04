import Globals.ModelConfig
import Globals.WriterTokenBudgets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WriterTokenBudgetsTest
{
    @Test
    fun deepSeekBudgetKeepsSlack()
    {
        val budget = WriterTokenBudgets.deepSeekR1(maxTokens = 8_000)

        assertEquals(120_000, budget.contextWindowSize)
        assertEquals(8_000, budget.maxTokens)
        assertTrue(budget.allowUserPromptTruncation)
    }

    @Test
    fun qwenCoderBudgetSupportsReasoningReserve()
    {
        val budget = WriterTokenBudgets.qwenCoder480B(
            maxTokens = 32_000,
            reasoningBudget = 8_000,
            subtractReasoningFromInput = true
        )

        assertEquals(252_000, budget.contextWindowSize)
        assertEquals(32_000, budget.maxTokens)
        assertEquals(8_000, budget.reasoningBudget)
        assertTrue(budget.subtractReasoningFromInput)
    }

    @Test
    fun modelLookupMapsKnownWriterModels()
    {
        val gptBudget = WriterTokenBudgets.forModel(ModelConfig.gptOss120bModelName, 20_000)
        val novaBudget = WriterTokenBudgets.forModel(ModelConfig.novaProModelName, 4_000)

        assertEquals(20_000, gptBudget.maxTokens)
        assertEquals(120_000, gptBudget.contextWindowSize)
        assertEquals(4_000, novaBudget.maxTokens)
        assertEquals(290_000, novaBudget.contextWindowSize)
    }
}
