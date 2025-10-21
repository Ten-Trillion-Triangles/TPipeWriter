# Chapter Editing System Implementation Plan

## Overview

This plan outlines the implementation of a comprehensive chapter editing system for TPipe that allows users to search, locate, edit, and delete story chapters through shell commands. The system will treat each context element in the ContextWindow as a potential chapter and provide tools for granular management.

## Core Architecture

### Chapter Identification Strategy

Since TPipe stores story content in `ContextWindow.contextElements` as a `MutableList<String>`, we'll treat each element as a chapter or story segment. The system will:

1. **Index-based identification**: Use array indices (0-based internally, 1-based for user display)
2. **Content-based search**: Search within chapter text for keywords/phrases
3. **Metadata tagging**: Optional chapter titles/tags stored in a separate map

### Data Structure Extensions

```kotlin
// New data class for chapter metadata
@Serializable
data class ChapterMetadata(
    val title: String = "",
    val tags: List<String> = listOf(),
    val wordCount: Int = 0,
    val createdAt: String = "",
    val lastModified: String = ""
)

// Extension to ContextWindow
class ContextWindow {
    // Existing fields...
    
    @Serializable
    var chapterMetadata = mutableMapOf<Int, ChapterMetadata>()
    
    // New methods for chapter management
    fun getChapterCount(): Int
    fun getChapter(index: Int): String?
    fun setChapter(index: Int, content: String)
    fun deleteChapter(index: Int)
    fun insertChapter(index: Int, content: String)
    fun searchChapters(query: String): List<Pair<Int, String>>
}
```

## Shell Command Interface

### New Slash Commands

#### `/chapters` - Chapter Management Mode
Switches to chapter management mode with subcommands:

```bash
/chapters list                    # List all chapters with indices and titles
/chapters search <query>          # Search chapters for text/keywords
/chapters show <index>           # Display specific chapter content
/chapters edit <index>           # Edit chapter at index
/chapters delete <index>         # Delete chapter at index
/chapters insert <index>         # Insert new chapter at index
/chapters move <from> <to>       # Move chapter from one index to another
/chapters title <index> <title>  # Set chapter title/metadata
/chapters stats                  # Show chapter statistics
/chapters export <index>         # Export single chapter to file
```

#### Enhanced Search Commands

```bash
/search <query>                  # Search all chapters for text
/search-regex <pattern>          # Regex search across chapters
/find-chapter <partial-text>     # Find chapter containing specific text
/locate <index>                  # Show context around specific chapter
```

## Implementation Components

### 1. ChapterManager Class

```kotlin
class ChapterManager(private val contextWindow: ContextWindow) {
    
    fun listChapters(): List<ChapterInfo>
    fun searchChapters(query: String, useRegex: Boolean = false): List<SearchResult>
    fun getChapter(index: Int): String?
    fun editChapter(index: Int, newContent: String): Boolean
    fun deleteChapter(index: Int): Boolean
    fun insertChapter(index: Int, content: String): Boolean
    fun moveChapter(fromIndex: Int, toIndex: Int): Boolean
    fun setChapterMetadata(index: Int, metadata: ChapterMetadata): Boolean
    fun getChapterStats(): ChapterStats
    fun exportChapter(index: Int, filePath: String): Boolean
    
    // Search utilities
    private fun performTextSearch(query: String): List<SearchResult>
    private fun performRegexSearch(pattern: String): List<SearchResult>
    private fun highlightMatches(text: String, query: String): String
}

data class ChapterInfo(
    val index: Int,
    val title: String,
    val wordCount: Int,
    val preview: String, // First 100 characters
    val lastModified: String
)

data class SearchResult(
    val chapterIndex: Int,
    val matchText: String,
    val contextBefore: String,
    val contextAfter: String,
    val position: Int
)

data class ChapterStats(
    val totalChapters: Int,
    val totalWords: Int,
    val averageWordsPerChapter: Int,
    val longestChapter: Int,
    val shortestChapter: Int
)
```

### 2. Interactive Chapter Editor

```kotlin
class InteractiveChapterEditor {
    
    fun editChapterInteractive(chapterManager: ChapterManager, index: Int) {
        // Launch interactive editing session
        // Show current content
        // Allow line-by-line editing
        // Provide save/cancel options
    }
    
    private fun showEditingInterface(content: String)
    private fun processEditCommands(content: String): String
    private fun validateChapterContent(content: String): Boolean
}
```

### 3. Shell Command Handlers

```kotlin
// Extension to existing Shell.kt
fun handleChapterCommands(params: List<String>) {
    val chapterManager = ChapterManager(ContextBank.getBankedContextWindow())
    
    when (params.firstOrNull()) {
        "list" -> displayChapterList(chapterManager)
        "search" -> handleChapterSearch(chapterManager, params.drop(1))
        "show" -> showChapter(chapterManager, params.getOrNull(1)?.toIntOrNull())
        "edit" -> editChapter(chapterManager, params.getOrNull(1)?.toIntOrNull())
        "delete" -> deleteChapter(chapterManager, params.getOrNull(1)?.toIntOrNull())
        "insert" -> insertChapter(chapterManager, params.getOrNull(1)?.toIntOrNull())
        "move" -> moveChapter(chapterManager, params)
        "title" -> setChapterTitle(chapterManager, params)
        "stats" -> showChapterStats(chapterManager)
        "export" -> exportChapter(chapterManager, params)
        else -> printChapterHelp()
    }
}
```

### 4. Search and Filter System

```kotlin
class ChapterSearchEngine {
    
    fun textSearch(chapters: List<String>, query: String): List<SearchResult>
    fun regexSearch(chapters: List<String>, pattern: String): List<SearchResult>
    fun fuzzySearch(chapters: List<String>, query: String): List<SearchResult>
    fun searchByWordCount(chapters: List<String>, minWords: Int, maxWords: Int): List<Int>
    fun searchByDateRange(metadata: Map<Int, ChapterMetadata>, startDate: String, endDate: String): List<Int>
    
    private fun extractContext(text: String, position: Int, contextSize: Int = 50): Pair<String, String>
    private fun calculateRelevanceScore(text: String, query: String): Double
}
```

## User Interface Design

### Chapter List Display

```
Chapters (12 total, 45,230 words):
┌─────┬──────────────────────────────────┬───────┬─────────────────────┐
│ #   │ Title/Preview                    │ Words │ Last Modified       │
├─────┼──────────────────────────────────┼───────┼─────────────────────┤
│ 1   │ The Beginning                    │ 1,234 │ 2024-01-15 14:30    │
│     │ "It was a dark and stormy..."    │       │                     │
├─────┼──────────────────────────────────┼───────┼─────────────────────┤
│ 2   │ Chapter 2: The Journey           │ 2,156 │ 2024-01-16 09:15    │
│     │ "The next morning brought..."    │       │                     │
└─────┴──────────────────────────────────┴───────┴─────────────────────┘
```

### Search Results Display

```
Search results for "dragon" (3 matches):

Chapter 2 (Match 1/2):
  ...the ancient castle where a mighty dragon once lived. The beast had...
                                    ^^^^^^
  
Chapter 5 (Match 1/1):
  ...she could hear the dragon's roar echoing through the valley...
                        ^^^^^^

Chapter 8 (Match 1/1):
  ...the dragon egg began to crack, revealing a small but fierce...
         ^^^^^^
```

### Interactive Editor Interface

```
Editing Chapter 3: "The Discovery"
Current content (1,456 words):
────────────────────────────────────────────────────────────────
[Content displayed with line numbers]
1: The morning sun cast long shadows across the ancient ruins.
2: Sarah carefully stepped through the crumbling archway,
3: her heart racing with anticipation of what lay ahead.
...
────────────────────────────────────────────────────────────────

Commands:
  :save          - Save changes and exit
  :cancel        - Discard changes and exit
  :replace <n>   - Replace line n with new content
  :insert <n>    - Insert new line after line n
  :delete <n>    - Delete line n
  :append        - Add content to end
  :prepend       - Add content to beginning
  :help          - Show all commands

Edit> 
```

## Implementation Steps

### Phase 1: Core Infrastructure
1. **Extend ContextWindow** with chapter metadata support
2. **Create ChapterManager** class with basic CRUD operations
3. **Add chapter command parsing** to Shell.kt
4. **Implement basic list/show/delete** functionality

### Phase 2: Search System
1. **Implement ChapterSearchEngine** with text search
2. **Add regex search** capabilities
3. **Create search result formatting** and display
4. **Add fuzzy search** for approximate matches

### Phase 3: Interactive Editing
1. **Create InteractiveChapterEditor** class
2. **Implement line-by-line editing** interface
3. **Add content validation** and backup systems
4. **Create undo/redo** functionality

### Phase 4: Advanced Features
1. **Add chapter moving/reordering** capabilities
2. **Implement chapter merging/splitting** tools
3. **Create export/import** functionality
4. **Add chapter statistics** and analytics

### Phase 5: Integration & Polish
1. **Integrate with existing save/load** system
2. **Add comprehensive error handling**
3. **Create help documentation**
4. **Performance optimization** for large stories

## File Structure

```
src/main/kotlin/
├── Shell/
│   ├── Shell.kt (existing - add chapter commands)
│   ├── ChapterManager.kt (new)
│   ├── InteractiveChapterEditor.kt (new)
│   └── ChapterSearchEngine.kt (new)
├── Context/
│   └── ContextWindow.kt (extend existing)
└── Util/
    └── ChapterUtils.kt (new - utilities)
```

## Configuration Options

### Settings for Chapter Management
```kotlin
data class ChapterSettings(
    val autoNumberChapters: Boolean = true,
    val defaultChapterTitle: String = "Chapter {index}",
    val maxChapterLength: Int = 10000, // words
    val enableAutoSave: Boolean = true,
    val backupOnEdit: Boolean = true,
    val searchContextSize: Int = 50, // characters before/after match
    val previewLength: Int = 100 // characters for chapter preview
)
```

## Error Handling

### Common Error Scenarios
1. **Invalid chapter index** - Show available range
2. **Empty chapter content** - Confirm deletion intent
3. **Search with no results** - Suggest alternative queries
4. **Edit conflicts** - Backup and recovery options
5. **Memory constraints** - Pagination for large stories

## Testing Strategy

**Note: All test files for TPipeWriter module must be stored at `/src/test/kotlin`**

### Unit Tests
- ChapterManager CRUD operations
- Search engine accuracy
- Content validation
- Index boundary checking

### Integration Tests
- Shell command parsing
- Context window integration
- Save/load functionality
- Multi-chapter operations

### User Acceptance Tests
- Complete editing workflows
- Search and replace scenarios
- Large story performance
- Error recovery procedures

## Future Enhancements

### Advanced Features
1. **Chapter templates** - Predefined chapter structures
2. **Version control** - Track chapter changes over time
3. **Collaborative editing** - Multi-user chapter management
4. **AI-assisted editing** - Suggest improvements/continuations
5. **Export formats** - PDF, EPUB, HTML generation
6. **Chapter linking** - Cross-references and dependencies
7. **Outline view** - Hierarchical chapter organization
8. **Word count goals** - Progress tracking per chapter

### Performance Optimizations
1. **Lazy loading** - Load chapters on demand
2. **Indexing** - Pre-built search indices
3. **Caching** - Cache frequently accessed chapters
4. **Streaming** - Handle very large stories efficiently

This comprehensive plan provides a robust foundation for chapter editing functionality while maintaining compatibility with the existing TPipe architecture and following the established coding patterns.