import Shell.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*

class IdeaPipelineTest 
{
    @BeforeEach
    fun setUp() 
    {
        // Reset idea settings before each test
        ideaSettings = IdeaSettings()
    }

    @Test
    fun testIdeaSettingsDefaults() 
    {
        assertEquals(8, ideaSettings.chaptersLookback)
        assertEquals(3000, ideaSettings.lorebookTokenBudget)
        assertTrue(ideaSettings.forcedChapters.isEmpty())
    }

    @Test
    fun testForcedChaptersManagement() 
    {
        // Test adding forced chapters
        ideaSettings.forcedChapters.add(0)
        ideaSettings.forcedChapters.add(2)
        
        assertEquals(2, ideaSettings.forcedChapters.size)
        assertTrue(ideaSettings.forcedChapters.contains(0))
        assertTrue(ideaSettings.forcedChapters.contains(2))
        
        // Test removing forced chapters
        ideaSettings.forcedChapters.remove(0)
        assertEquals(1, ideaSettings.forcedChapters.size)
        assertFalse(ideaSettings.forcedChapters.contains(0))
        assertTrue(ideaSettings.forcedChapters.contains(2))
    }

    @Test
    fun testIdeaSettingsUpdate() 
    {
        val newSettings = IdeaSettings(
            chaptersLookback = 5,
            lorebookTokenBudget = 3000,
            forcedChapters = mutableListOf(1, 3, 5)
        )
        
        ideaSettings = newSettings
        
        assertEquals(5, ideaSettings.chaptersLookback)
        assertEquals(3000, ideaSettings.lorebookTokenBudget)
        assertEquals(3, ideaSettings.forcedChapters.size)
        assertTrue(ideaSettings.forcedChapters.containsAll(listOf(1, 3, 5)))
    }

    @Test
    fun testTokenBudgetCalculation() 
    {
        val totalTokens = 107000
        val lorebookBudget = 2500
        ideaSettings = ideaSettings.copy(lorebookTokenBudget = lorebookBudget)
        
        val availableTokens = totalTokens - ideaSettings.lorebookTokenBudget
        assertEquals(104500, availableTokens)
    }
}