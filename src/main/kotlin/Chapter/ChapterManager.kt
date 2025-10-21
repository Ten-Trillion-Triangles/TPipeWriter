package Chapter

import com.TTT.Context.ContextWindow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Main manager class for chapter operations within TPipeWriter.
 * Provides comprehensive CRUD operations for story chapters including
 * content management, metadata handling, search functionality, and statistics.
 * 
 * This class acts as the primary interface between the shell commands and
 * the underlying chapter data structures, coordinating between ContextWindow
 * content and GlobalChapterManager metadata.
 * 
 * @property contextWindow The ContextWindow containing the story's chapter content
 */
class ChapterManager(private val contextWindow: ContextWindow) 
{
    
    /**
     * Retrieves information about all chapters in the story.
     * Combines content from ContextWindow with metadata from GlobalChapterManager
     * to create comprehensive chapter information for display.
     * 
     * @return List of ChapterInfo objects containing display data for each chapter
     */
    fun listChapters(): List<ChapterInfo>
    {
        val chapters = mutableListOf<ChapterInfo>()
        contextWindow.contextElements.forEachIndexed { index, content ->
            // Get metadata for this chapter, if it exists
            val metadata = GlobalChapterManager.getChapterMetadata()[index]
            
            // Calculate word count by splitting on whitespace
            val wordCount = content.split("\\s+".toRegex()).size
            
            // Create preview text (first 100 characters)
            val preview = if (content.length > 100) content.take(100) + "..." else content
            
            // Use metadata title or generate default title
            val title = metadata?.title?.ifEmpty { "Chapter ${index + 1}" } ?: "Chapter ${index + 1}"
            
            // Get last modified timestamp from metadata
            val lastModified = metadata?.lastModified ?: ""
            
            chapters.add(ChapterInfo(index, title, wordCount, preview, lastModified))
        }
        return chapters
    }
    
    /**
     * Searches for text within all chapters.
     * Supports both plain text and regex search modes.
     * 
     * @param query The search query string
     * @param useRegex Whether to treat the query as a regex pattern
     * @return List of SearchResult objects containing matches and context
     */
    fun searchChapters(query: String, useRegex: Boolean = false): List<SearchResult>
    {
        return if (useRegex) performRegexSearch(query) else performTextSearch(query)
    }
    
    /**
     * Retrieves the content of a specific chapter.
     * 
     * @param index The chapter index (0-based)
     * @return The chapter content string, or null if index is invalid
     */
    fun getChapter(index: Int): String?
    {
        return if (index in 0 until contextWindow.contextElements.size) {
            contextWindow.contextElements[index]
        } else null
    }

    /**
     * Get valid chapters in a given range and return them as a list.
     *
     * @param start Starting chapter to begin our search. Must be greater than 0 and less than the end.
     * @param end Ending chapter to end our search. Must be greater than 0 and greater than the start.
     * @return List of valid chapters in the given range. Empty list if invalid range is inputted.
     */
    fun getChapterRange(start: Int, end: Int) : List<String>
    {
        //Exit if invalid range is inputted.
        if(start > end || end < 1 || start < 1) return listOf()

        //Declare now and add as we find any chapters in our range.
        val validChapters = mutableListOf<String>()

        for(i in start..end)
        {
            val chapter = getChapter(i - 1) ?: ""
            if(chapter.isNotEmpty())
            {
                validChapters.add(chapter)
            }

        }

        return validChapters
    }

    /**
     * Retrieve a list of specific chapter. Chapters are interfaced as 1 to max in the shell.
     *
     * @param list The list of chapters to retrieve formated as a string "1, 2, 3," etc. This function is
     * expected to be called from the shell so the formatting is not expecting a list at this stage.
     *
     * @return List of valid chapters we found at those locations.
     */
    fun getSpecificChapters(list: String) : List<String>
    {
        val validChapters = mutableListOf<String>()
        val seekingChapters = list.split(", ")

        for(chapter in seekingChapters)
        {
            val chapterIndex = chapter.toIntOrNull()
            if(chapterIndex != null)
            {
                val chapterContent = getChapter(chapterIndex - 1) ?: ""
                if(chapterContent.isNotEmpty())
                {
                    validChapters.add(chapterContent)
                }
            }
        }

        return validChapters
    }
    
    /**
     * Edits the content of an existing chapter.
     * Updates both the content in ContextWindow and the metadata timestamps.
     * 
     * @param index The chapter index to edit (0-based)
     * @param newContent The new content for the chapter
     * @return True if the edit was successful, false if index is invalid
     */
    fun editChapter(index: Int, newContent: String): Boolean
    {
        if (index !in 0 until contextWindow.contextElements.size) return false
        
        // Update the chapter content
        contextWindow.contextElements[index] = newContent
        
        // Update metadata with new word count and timestamp
        updateChapterMetadata(index, newContent)
        return true
    }
    
    /**
     * Deletes a chapter from the story.
     * Removes both content and metadata, then reindexes remaining chapters.
     * 
     * @param index The chapter index to delete (0-based)
     * @return True if deletion was successful, false if index is invalid
     */
    fun deleteChapter(index: Int): Boolean
    {
        if (index !in 0 until contextWindow.contextElements.size) return false
        
        // Remove the chapter content from ContextWindow
        contextWindow.contextElements.removeAt(index)
        
        // Remove metadata for the deleted chapter
        GlobalChapterManager.removeChapterMetadata(index)
        
        // Reindex remaining metadata to fill the gap
        val updatedMetadata = mutableMapOf<Int, ChapterMetadata>()
        GlobalChapterManager.getChapterMetadata().forEach { (key, value) ->
            when {
                key < index -> updatedMetadata[key] = value // Keep same index
                key > index -> updatedMetadata[key - 1] = value // Shift down by 1
                // Skip the deleted index
            }
        }
        
        // Replace metadata with reindexed version
        GlobalChapterManager.clearAllMetadata()
        GlobalChapterManager.loadMetadata(updatedMetadata)
        
        return true
    }
    
    /**
     * Inserts a new chapter at the specified position.
     * Shifts existing chapters and their metadata to make room for the new chapter.
     * 
     * @param index The position to insert the new chapter (0-based)
     * @param content The content for the new chapter
     * @return True if insertion was successful, false if index is invalid
     */
    fun insertChapter(index: Int, content: String): Boolean
    {
        if (index < 0 || index > contextWindow.contextElements.size) return false
        
        // Insert the new chapter content at the specified position
        contextWindow.contextElements.add(index, content)
        
        // Shift existing metadata indices to make room for the new chapter
        val updatedMetadata = mutableMapOf<Int, ChapterMetadata>()
        GlobalChapterManager.getChapterMetadata().forEach { (key, value) ->
            if (key >= index) {
                updatedMetadata[key + 1] = value // Shift up by 1
            } else {
                updatedMetadata[key] = value // Keep same index
            }
        }
        
        // Replace metadata with shifted version
        GlobalChapterManager.clearAllMetadata()
        GlobalChapterManager.loadMetadata(updatedMetadata)
        
        // Create metadata for the new chapter
        updateChapterMetadata(index, content)
        return true
    }
    
    /**
     * Moves a chapter from one position to another.
     * Relocates both content and metadata to the new position.
     * 
     * @param fromIndex The current position of the chapter to move
     * @param toIndex The target position for the chapter
     * @return True if the move was successful, false if either index is invalid
     */
    fun moveChapter(fromIndex: Int, toIndex: Int): Boolean
    {
        if (fromIndex !in 0 until contextWindow.contextElements.size || 
            toIndex !in 0 until contextWindow.contextElements.size) return false
        
        // Move the chapter content
        val chapter = contextWindow.contextElements.removeAt(fromIndex)
        contextWindow.contextElements.add(toIndex, chapter)
        
        // Move the associated metadata
        val metadata = GlobalChapterManager.getChapterMetadata()[fromIndex]
        GlobalChapterManager.removeChapterMetadata(fromIndex)
        if (metadata != null) {
            GlobalChapterManager.setChapterMetadata(toIndex, metadata)
        }
        
        return true
    }
    
    /**
     * Sets metadata for a specific chapter.
     * 
     * @param index The chapter index (0-based)
     * @param metadata The metadata to associate with the chapter
     * @return True if metadata was set successfully, false if index is invalid
     */
    fun setChapterMetadata(index: Int, metadata: ChapterMetadata): Boolean
    {
        if (index !in 0 until contextWindow.contextElements.size) return false
        
        GlobalChapterManager.setChapterMetadata(index, metadata)
        return true
    }
    
    /**
     * Calculates and returns statistical information about all chapters.
     * Provides aggregate data including word counts and averages.
     * 
     * @return ChapterStats object containing statistical information
     */
    fun getChapterStats(): ChapterStats
    {
        val totalChapters = contextWindow.contextElements.size
        if (totalChapters == 0) {
            return ChapterStats(0, 0, 0, 0, 0)
        }
        
        // Calculate word count for each chapter
        val wordCounts = contextWindow.contextElements.map { it.split("\\s+".toRegex()).size }
        val totalWords = wordCounts.sum()
        val averageWords = totalWords / totalChapters
        val longestChapter = wordCounts.maxOrNull() ?: 0
        val shortestChapter = wordCounts.minOrNull() ?: 0
        
        return ChapterStats(totalChapters, totalWords, averageWords, longestChapter, shortestChapter)
    }
    
    /**
     * Selects chapters starting from the latest that fit within the specified token budget.
     * Uses the provided truncation settings for accurate token counting.
     * 
     * @param tokenBudget Maximum number of tokens allowed
     * @param settings Truncation settings from a Pipe class for token counting
     * @return List of chapter content strings that fit within the budget, ordered from latest to oldest
     */
    fun selectChaptersWithinTokenBudget(
        tokenBudget: Int,
        settings: com.TTT.Pipe.TruncationSettings
    ): List<String>
    {
        if (tokenBudget <= 0 || contextWindow.contextElements.isEmpty()) return emptyList()
        
        val selectedChapters = mutableListOf<String>()
        var usedTokens = 0
        
        // Start from the latest chapter (last index) and work backwards
        for (i in contextWindow.contextElements.indices.reversed())
        {
            val chapter = contextWindow.contextElements[i]
            val chapterTokens = com.TTT.Context.Dictionary.countTokens(chapter, settings)
            
            // Add chapter if it fits within remaining budget
            if (usedTokens + chapterTokens <= tokenBudget)
            {
                selectedChapters.add(chapter)
                usedTokens += chapterTokens
            }
            else
            {
                // Stop if chapter doesn't fit - we want complete chapters only
                break
            }
        }
        
        return selectedChapters
    }
    
    /**
     * Exports a single chapter to a text file.
     * 
     * @param index The chapter index to export (0-based)
     * @param filePath The file path where the chapter should be saved
     * @return True if export was successful, false if chapter doesn't exist or file write failed
     */
    fun exportChapter(index: Int, filePath: String): Boolean
    {
        val chapter = getChapter(index) ?: return false
        
        try {
            // Write chapter content to the specified file
            java.io.File(filePath).writeText(chapter)
            return true
        } catch (e: Exception) {
            // Return false if file write operation fails
            return false
        }
    }
    
    /**
     * Performs plain text search across all chapters.
     * Searches for exact text matches (case-insensitive) and returns results with context.
     * 
     * @param query The text to search for
     * @return List of SearchResult objects containing matches and surrounding context
     */
    private fun performTextSearch(query: String): List<SearchResult>
    {
        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()
        
        contextWindow.contextElements.forEachIndexed { index, content ->
            val lowerContent = content.lowercase()
            var position = 0
            
            // Find all occurrences of the query in this chapter
            while (true) {
                val matchIndex = lowerContent.indexOf(lowerQuery, position)
                if (matchIndex == -1) break // No more matches in this chapter
                
                // Extract context around the match for display
                val (contextBefore, contextAfter) = extractContext(content, matchIndex, 50)
                
                // Get the actual matched text (preserving original case)
                val matchText = content.substring(matchIndex, matchIndex + query.length)
                
                results.add(SearchResult(index, matchText, contextBefore, contextAfter, matchIndex))
                
                // Move to next potential match position
                position = matchIndex + 1
            }
        }
        
        return results
    }
    
    /**
     * Performs regex pattern search across all chapters.
     * Searches using regular expressions and returns matches with context.
     * 
     * @param pattern The regex pattern to search for
     * @return List of SearchResult objects, empty if pattern is invalid
     */
    private fun performRegexSearch(pattern: String): List<SearchResult>
    {
        val results = mutableListOf<SearchResult>()
        
        try {
            val regex = Regex(pattern)
            contextWindow.contextElements.forEachIndexed { index, content ->
                // Find all regex matches in this chapter
                regex.findAll(content).forEach { match ->
                    // Extract context around the match
                    val (contextBefore, contextAfter) = extractContext(content, match.range.first, 50)
                    
                    // Add the match result
                    results.add(SearchResult(index, match.value, contextBefore, contextAfter, match.range.first))
                }
            }
        } catch (e: Exception) {
            // Invalid regex pattern - return empty results
        }
        
        return results
    }
    
    /**
     * Extracts context text around a specific position for search result display.
     * 
     * @param text The full text to extract context from
     * @param position The position of the match within the text
     * @param contextSize Number of characters to include before and after the match
     * @return Pair of (contextBefore, contextAfter) strings
     */
    private fun extractContext(text: String, position: Int, contextSize: Int): Pair<String, String>
    {
        // Calculate safe start and end positions within text bounds
        val start = maxOf(0, position - contextSize)
        val end = minOf(text.length, position + contextSize)
        
        // Extract text before and after the match position
        val before = text.substring(start, position)
        val after = text.substring(position, end)
        
        return Pair(before, after)
    }
    
    /**
     * Updates or creates metadata for a chapter based on its current content.
     * Preserves existing title and tags while updating word count and timestamps.
     * 
     * @param index The chapter index to update metadata for
     * @param content The current content of the chapter
     */
    private fun updateChapterMetadata(index: Int, content: String)
    {
        // Generate current timestamp for modification tracking
        val currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        
        // Calculate current word count
        val wordCount = content.split("\\s+".toRegex()).size
        
        // Get existing metadata to preserve user-set values
        val existingMetadata = GlobalChapterManager.getChapterMetadata()[index]
        
        // Create updated metadata preserving existing title/tags but updating counts/timestamps
        val updatedMetadata = ChapterMetadata(
            title = existingMetadata?.title ?: "Chapter ${index + 1}", // Keep existing title or generate default
            tags = existingMetadata?.tags ?: listOf(), // Preserve existing tags
            wordCount = wordCount, // Update with current word count
            createdAt = existingMetadata?.createdAt ?: currentTime, // Keep creation time or set now
            lastModified = currentTime // Always update modification time
        )
        
        // Save the updated metadata
        GlobalChapterManager.setChapterMetadata(index, updatedMetadata)
    }
}