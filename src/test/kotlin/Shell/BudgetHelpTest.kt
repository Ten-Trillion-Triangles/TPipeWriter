package Shell

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BudgetHelpTest
{
    @Test
    fun helpMentionsTokenBudget() {
        val out = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(out))
        try { printHelp() } finally { System.setOut(original) }
        val text = out.toString()
        assertTrue(
            text.contains("budget", ignoreCase = true) ||
            text.contains("token", ignoreCase = true),
            "printHelp must mention token budgeting so users can discover the budget feature"
        )
    }

    @Test
    fun helpListsBudgetInfoCommand() {
        val out = java.io.ByteArrayOutputStream()
        val original = System.out
        System.setOut(java.io.PrintStream(out))
        try { printHelp() } finally { System.setOut(original) }
        val text = out.toString()
        assertTrue(
            text.contains("/budget-info"),
            "printHelp must mention /budget-info"
        )
    }
}