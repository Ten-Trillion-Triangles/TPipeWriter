package Chapter

import com.TTT.Context.ContextWindow
import kotlinx.serialization.Serializable

/**
 * Extended context window that includes chapter metadata alongside the standard ContextWindow.
 * This allows for enhanced chapter management without modifying the core TPipe library.
 * 
 * @property contextWindow The standard TPipe ContextWindow containing story elements
 * @property chapterMetadata Map of chapter indices to their associated metadata
 */
@Serializable
data class ExtendedContextWindow(
    val contextWindow: ContextWindow,
    val chapterMetadata: MutableMap<Int, ChapterMetadata> = mutableMapOf()
)

/**
 * Local chapter context manager for handling chapter metadata operations.
 * This class provides a wrapper around ContextWindow to add chapter-specific functionality
 * without modifying the core TPipe library classes.
 * 
 * @property contextWindow The ContextWindow instance to manage
 */
class ChapterContextManager(private val contextWindow: ContextWindow) 
{
    /** Local storage for chapter metadata indexed by chapter position */
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
     * Clears all chapter metadata.
     */
    fun clearChapterMetadata() 
    {
        chapterMetadata.clear()
    }
    
    /**
     * Reindexes chapter metadata after a chapter deletion.
     * Chapters after the deleted index are shifted down by one position.
     * 
     * @param deletedIndex The index of the deleted chapter
     */
    fun reindexMetadata(deletedIndex: Int) 
    {
        // Create new metadata map with adjusted indices
        val updatedMetadata = mutableMapOf<Int, ChapterMetadata>()
        chapterMetadata.forEach { (key, value) ->
            when {
                key < deletedIndex -> updatedMetadata[key] = value // Keep same index
                key > deletedIndex -> updatedMetadata[key - 1] = value // Shift down by 1
                // Skip the deleted index
            }
        }
        // Replace existing metadata with reindexed version
        chapterMetadata.clear()
        chapterMetadata.putAll(updatedMetadata)
    }
    
    /**
     * Shifts chapter metadata indices after a chapter insertion.
     * Chapters at or after the insert index are shifted up by one position.
     * 
     * @param insertIndex The index where a new chapter was inserted
     */
    fun shiftMetadataForInsert(insertIndex: Int) 
    {
        // Create new metadata map with adjusted indices
        val updatedMetadata = mutableMapOf<Int, ChapterMetadata>()
        chapterMetadata.forEach { (key, value) ->
            if (key >= insertIndex) {
                updatedMetadata[key + 1] = value // Shift up by 1
            } else {
                updatedMetadata[key] = value // Keep same index
            }
        }
        // Replace existing metadata with shifted version
        chapterMetadata.clear()
        chapterMetadata.putAll(updatedMetadata)
    }
}