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
    val lastModified: String = "",
    /**
     * Snapshot of Env.authorPrompt captured at the time this chapter was
     * saved. Empty string means "no snapshot was taken". On chapter load,
     * if this is non-empty, Env.authorPrompt is restored to this value.
     */
    val authorPromptSnapshot: String = "",
    /**
     * Snapshot of Env.editorPrompt (Falkenda Unseppal) captured at chapter
     * save time. Same semantics as [authorPromptSnapshot].
     */
    val editorPromptSnapshot: String = "",
    /**
     * Snapshot of Env.richardTreadwell (N'zelquin G'zeeloth, the competing
     * author) captured at chapter save time. Same semantics.
     */
    val richardTreadwellSnapshot: String = ""
)

/**
 * Build a [ChapterMetadata] that captures the active writer personalities
 * alongside any existing metadata fields. Pure function — no side effects,
 * no singleton reads. The caller passes the three personality strings
 * explicitly so this is testable in isolation.
 *
 * Empty strings are valid (a personality that has never been overridden
 * keeps the default hardcoded value, but at chapter save time we always
 * capture whatever is currently active so the snapshot reflects the
 * save-time state).
 *
 * @param existing The chapter metadata to preserve (title, tags, etc.).
 * @param authorPrompt Current value of Env.authorPrompt.
 * @param editorPrompt Current value of Env.editorPrompt.
 * @param richardTreadwell Current value of Env.richardTreadwell.
 * @return A new ChapterMetadata with the three snapshot fields populated.
 */
fun capturePersonalitySnapshot(
    existing: ChapterMetadata,
    authorPrompt: String,
    editorPrompt: String,
    richardTreadwell: String
): ChapterMetadata = existing.copy(
    authorPromptSnapshot = authorPrompt,
    editorPromptSnapshot = editorPrompt,
    richardTreadwellSnapshot = richardTreadwell
)

/**
 * Apply a [ChapterMetadata]'s personality snapshots to the caller-supplied
 * state. Empty snapshot strings are ignored — they mean "no snapshot was
 * taken", and we leave the existing value alone rather than blanking it
 * out. This preserves backward compatibility for chapters saved before
 * this feature shipped.
 *
 * Pure function — returns a triple of the new values to assign.
 *
 * @return Triple of (newAuthorPrompt, newEditorPrompt, newRichardTreadwell).
 */
fun applyPersonalitySnapshot(
    metadata: ChapterMetadata,
    currentAuthorPrompt: String,
    currentEditorPrompt: String,
    currentRichardTreadwell: String
): Triple<String, String, String> = Triple(
    if (metadata.authorPromptSnapshot.isNotEmpty()) metadata.authorPromptSnapshot else currentAuthorPrompt,
    if (metadata.editorPromptSnapshot.isNotEmpty()) metadata.editorPromptSnapshot else currentEditorPrompt,
    if (metadata.richardTreadwellSnapshot.isNotEmpty()) metadata.richardTreadwellSnapshot else currentRichardTreadwell
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