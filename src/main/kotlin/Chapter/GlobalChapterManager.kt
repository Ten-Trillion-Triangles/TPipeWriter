package Chapter

import kotlinx.serialization.Serializable

/**
 * Serializable container for complete story data including content and metadata.
 * Used for save/load operations to preserve both story text and chapter information.
 * 
 * @property contextElements List of chapter content strings
 * @property chapterMetadata Map of chapter indices to their metadata
 */
@Serializable
data class StoryData(
    val contextElements: List<String> = listOf(),
    val chapterMetadata: Map<Int, ChapterMetadata> = mapOf()
)

/**
 * Global singleton manager for chapter metadata persistence.
 * Provides centralized storage and management of chapter metadata across
 * the application lifecycle, ensuring data survives save/load operations.
 */
object GlobalChapterManager 
{
    /** Global storage for chapter metadata indexed by chapter position */
    private val chapterMetadata = mutableMapOf<Int, ChapterMetadata>()
    
    /**
     * Retrieves a copy of all chapter metadata.
     * 
     * @return Immutable map of chapter indices to metadata
     */
    fun getChapterMetadata(): Map<Int, ChapterMetadata> = chapterMetadata.toMap()
    
    /**
     * Sets metadata for a specific chapter index.
     * 
     * @param index The chapter index (0-based)
     * @param metadata The metadata to associate with the chapter
     */
    fun setChapterMetadata(index: Int, metadata: ChapterMetadata) 
    {
        chapterMetadata[index] = metadata
    }
    
    /**
     * Removes metadata for a specific chapter index.
     * 
     * @param index The chapter index to remove metadata for
     */
    fun removeChapterMetadata(index: Int) 
    {
        chapterMetadata.remove(index)
    }
    
    /**
     * Clears all chapter metadata from global storage.
     * Used when clearing the entire story or starting fresh.
     */
    fun clearAllMetadata() 
    {
        chapterMetadata.clear()
    }
    
    /**
     * Loads chapter metadata from external source.
     * Replaces all existing metadata with the provided data.
     * 
     * @param metadata Map of chapter indices to metadata to load
     */
    fun loadMetadata(metadata: Map<Int, ChapterMetadata>) 
    {
        // Clear existing data and load new metadata
        chapterMetadata.clear()
        chapterMetadata.putAll(metadata)
    }
    
    /**
     * Exports complete story data including content and metadata.
     * Creates a serializable container for save operations.
     * 
     * @param contextElements List of chapter content strings
     * @return StoryData container with content and metadata
     */
    fun exportStoryData(contextElements: List<String>): StoryData 
    {
        return StoryData(contextElements, chapterMetadata.toMap())
    }
}