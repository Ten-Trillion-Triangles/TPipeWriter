import Chapter.ChapterManager
import Chapter.ChapterMetadata
import Chapter.GlobalChapterManager
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.io.File

class ChapterSaveLoadTest 
{
    private lateinit var contextWindow: ContextWindow
    private lateinit var chapterManager: ChapterManager
    private val testDir = File(System.getProperty("java.io.tmpdir"), "tpipe-test")
    
    @BeforeEach
    fun setup() 
    {
        contextWindow = ContextWindow()
        contextWindow.contextElements.addAll(listOf(
            "This is the first chapter of our story.",
            "The second chapter continues the adventure.",
            "In the third chapter, everything changes."
        ))
        chapterManager = ChapterManager(contextWindow)
        
        // Set up some chapter metadata
        chapterManager.setChapterMetadata(0, ChapterMetadata(title = "The Beginning", tags = listOf("intro")))
        chapterManager.setChapterMetadata(1, ChapterMetadata(title = "The Journey", tags = listOf("adventure")))
        chapterManager.setChapterMetadata(2, ChapterMetadata(title = "The Change", tags = listOf("climax")))
        
        // Create test directory
        testDir.mkdirs()
    }
    
    @AfterEach
    fun cleanup() 
    {
        // Clean up test files
        testDir.deleteRecursively()
        GlobalChapterManager.clearAllMetadata()
    }
    
    @Test
    fun testExportStoryData() 
    {
        val storyData = GlobalChapterManager.exportStoryData(contextWindow.contextElements)
        
        assertEquals(3, storyData.contextElements.size)
        assertEquals(3, storyData.chapterMetadata.size)
        assertEquals("The Beginning", storyData.chapterMetadata[0]?.title)
        assertEquals("The Journey", storyData.chapterMetadata[1]?.title)
        assertEquals("The Change", storyData.chapterMetadata[2]?.title)
    }
    
    @Test
    fun testLoadMetadata() 
    {
        val originalMetadata = GlobalChapterManager.getChapterMetadata()
        
        // Clear and reload
        GlobalChapterManager.clearAllMetadata()
        assertEquals(0, GlobalChapterManager.getChapterMetadata().size)
        
        GlobalChapterManager.loadMetadata(originalMetadata)
        assertEquals(3, GlobalChapterManager.getChapterMetadata().size)
        assertEquals("The Beginning", GlobalChapterManager.getChapterMetadata()[0]?.title)
    }
    
    @Test
    fun testChapterMetadataPersistence() 
    {
        // Verify metadata is accessible through ChapterManager
        val chapters = chapterManager.listChapters()
        assertEquals("The Beginning", chapters[0].title)
        assertEquals("The Journey", chapters[1].title)
        assertEquals("The Change", chapters[2].title)
        
        // Test editing preserves metadata structure
        chapterManager.editChapter(1, "The second chapter has been edited.")
        val updatedChapters = chapterManager.listChapters()
        assertEquals("The Journey", updatedChapters[1].title) // Title should remain
        assertTrue(updatedChapters[1].lastModified.isNotEmpty()) // Should have timestamp
    }
    
    @Test
    fun testMetadataReindexingOnDelete() 
    {
        // Delete middle chapter
        chapterManager.deleteChapter(1)
        
        val remainingChapters = chapterManager.listChapters()
        assertEquals(2, remainingChapters.size)
        assertEquals("The Beginning", remainingChapters[0].title)
        assertEquals("The Change", remainingChapters[1].title) // Should be reindexed from 2 to 1
    }
    
    @Test
    fun testMetadataShiftingOnInsert() 
    {
        // Insert new chapter at position 1
        chapterManager.insertChapter(1, "This is an inserted chapter.")
        
        val chapters = chapterManager.listChapters()
        assertEquals(4, chapters.size)
        assertEquals("The Beginning", chapters[0].title)
        assertEquals("Chapter 2", chapters[1].title) // New chapter gets default title
        assertEquals("The Journey", chapters[2].title) // Original chapter 1 shifted to 2
        assertEquals("The Change", chapters[3].title) // Original chapter 2 shifted to 3
    }
}