import Chapter.ChapterManager
import Chapter.ChapterMetadata
import Chapter.GlobalChapterManager
import com.TTT.Context.ContextWindow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class ChapterManagerTest 
{
    private lateinit var contextWindow: ContextWindow
    private lateinit var chapterManager: ChapterManager
    
    @BeforeEach
    fun setup() 
    {
        // Clear any existing metadata from previous tests
        GlobalChapterManager.clearAllMetadata()
        
        contextWindow = ContextWindow()
        contextWindow.contextElements.addAll(listOf(
            "This is the first chapter of our story.",
            "The second chapter continues the adventure.",
            "In the third chapter, everything changes."
        ))
        chapterManager = ChapterManager(contextWindow)
    }
    
    @Test
    fun testListChapters() 
    {
        val chapters = chapterManager.listChapters()
        assertEquals(3, chapters.size)
        assertEquals(0, chapters[0].index)
        assertEquals("Chapter 1", chapters[0].title)
        assertTrue(chapters[0].wordCount > 0)
    }
    
    @Test
    fun testGetChapter() 
    {
        val chapter = chapterManager.getChapter(0)
        assertEquals("This is the first chapter of our story.", chapter)
        
        val invalidChapter = chapterManager.getChapter(10)
        assertNull(invalidChapter)
    }
    
    @Test
    fun testEditChapter() 
    {
        val success = chapterManager.editChapter(0, "This is the edited first chapter.")
        assertTrue(success)
        assertEquals("This is the edited first chapter.", chapterManager.getChapter(0))
        
        val failure = chapterManager.editChapter(10, "Invalid edit")
        assertFalse(failure)
    }
    
    @Test
    fun testDeleteChapter() 
    {
        val success = chapterManager.deleteChapter(1)
        assertTrue(success)
        assertEquals(2, contextWindow.contextElements.size)
        assertEquals("This is the first chapter of our story.", contextWindow.contextElements[0])
        assertEquals("In the third chapter, everything changes.", contextWindow.contextElements[1])
    }
    
    @Test
    fun testInsertChapter() 
    {
        val success = chapterManager.insertChapter(1, "This is a new chapter inserted at position 1.")
        assertTrue(success)
        assertEquals(4, contextWindow.contextElements.size)
        assertEquals("This is a new chapter inserted at position 1.", contextWindow.contextElements[1])
    }
    
    @Test
    fun testSearchChapters() 
    {
        val results = chapterManager.searchChapters("chapter")
        assertEquals(3, results.size)
        
        val specificResults = chapterManager.searchChapters("adventure")
        assertEquals(1, specificResults.size)
        assertEquals(1, specificResults[0].chapterIndex)
    }
    
    @Test
    fun testGetChapterStats() 
    {
        val stats = chapterManager.getChapterStats()
        assertEquals(3, stats.totalChapters)
        assertTrue(stats.totalWords > 0)
        assertTrue(stats.averageWordsPerChapter > 0)
    }
    
    @Test
    fun testSetChapterMetadata() 
    {
        val metadata = ChapterMetadata(title = "Custom Title", tags = listOf("test"))
        val success = chapterManager.setChapterMetadata(0, metadata)
        assertTrue(success)
        
        val chapters = chapterManager.listChapters()
        assertEquals("Custom Title", chapters[0].title)
    }
}