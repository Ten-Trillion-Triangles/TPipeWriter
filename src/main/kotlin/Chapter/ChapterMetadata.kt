package Chapter

import kotlinx.serialization.Serializable

/**
 * Metadata associated with a story chapter.
 * Contains information about the chapter beyond just its content.
 * 
 * @property title The chapter title or name
 * @property tags List of tags or categories associated with the chapter
 * @property wordCount Number of words in the chapter content
 * @property createdAt Timestamp when the chapter was created
 * @property lastModified Timestamp when the chapter was last modified
 */
@Serializable
data class ChapterMetadata(
    val title: String = "",
    val tags: List<String> = listOf(),
    val wordCount: Int = 0,
    val createdAt: String = "",
    val lastModified: String = ""
)

/**
 * Display information for a chapter in list views.
 * Contains both content and metadata for user-friendly display.
 * 
 * @property index The chapter's position in the story (0-based internally)
 * @property title The chapter title for display
 * @property wordCount Number of words in the chapter
 * @property preview Short preview of the chapter content
 * @property lastModified When the chapter was last modified
 */
@Serializable
data class ChapterInfo(
    val index: Int,
    val title: String,
    val wordCount: Int,
    val preview: String,
    val lastModified: String
)

/**
 * Result of a search operation within chapter content.
 * Contains the match and surrounding context for display.
 * 
 * @property chapterIndex Index of the chapter containing the match
 * @property matchText The actual text that matched the search query
 * @property contextBefore Text appearing before the match
 * @property contextAfter Text appearing after the match
 * @property position Character position of the match within the chapter
 */
@Serializable
data class SearchResult(
    val chapterIndex: Int,
    val matchText: String,
    val contextBefore: String,
    val contextAfter: String,
    val position: Int
)

/**
 * Statistical information about all chapters in a story.
 * Provides aggregate data for analysis and display.
 * 
 * @property totalChapters Total number of chapters in the story
 * @property totalWords Total word count across all chapters
 * @property averageWordsPerChapter Average words per chapter
 * @property longestChapter Word count of the longest chapter
 * @property shortestChapter Word count of the shortest chapter
 */
@Serializable
data class ChapterStats(
    val totalChapters: Int,
    val totalWords: Int,
    val averageWordsPerChapter: Int,
    val longestChapter: Int,
    val shortestChapter: Int
)