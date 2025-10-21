# Chat Context Refactor Plan

## Problem Statement

When the story exceeds the 105K token context window limit, the chat pipeline needs intelligent context selection rather than automatic truncation. Users should be able to choose which chapters/content to include for discussion.

## Current Issues

1. **Automatic Truncation**: Context is automatically truncated from top when exceeding 105K tokens
2. **No User Control**: Users can't specify which story parts are relevant to their question
3. **Loss of Context**: Important story elements may be truncated away
4. **Poor UX**: No feedback when context exceeds limits

## Solution Overview

Implement a **Context Selection Shell** that activates when story exceeds token limits, allowing users to:
- Select specific chapters by number or range
- Choose recent chapters (last N)
- Select chapters by keyword/character search
- View token usage and make informed decisions

## Implementation Plan

### Phase 1: Detection & Token Calculation

#### 1.1 Add Context Size Detection
```kotlin
data class ContextStats(
    val totalTokens: Int,
    val chapterCount: Int,
    val lorebookTokens: Int,
    val chatHistoryTokens: Int,
    val exceedsLimit: Boolean
)

fun calculateContextStats(context: ContextWindow): ContextStats {
    // IMPORTANT: Use pipe's truncation settings for consistent token counting
    val examplePipe = BedrockPipe().truncateModuleContext()
    val settings = examplePipe.getTruncationSettings()
    
    val chapterTokens = context.contextElements.sumOf { chapter ->
        Dictionary.countTokens(
            chapter,
            settings.countSubWordsInFirstWord,
            settings.favorWholeWords,
            settings.countOnlyFirstWordFound,
            settings.splitForNonWordChar,
            settings.alwaysSplitIfWholeWordExists,
            settings.countSubWordsIfSplit,
            settings.nonWordSplitCount
        )
    }
    
    val lorebookTokens = context.loreBookKeys.values.sumOf { loreEntry ->
        Dictionary.countTokens(
            loreEntry.value,
            settings.countSubWordsInFirstWord,
            settings.favorWholeWords,
            settings.countOnlyFirstWordFound,
            settings.splitForNonWordChar,
            settings.alwaysSplitIfWholeWordExists,
            settings.countSubWordsIfSplit,
            settings.nonWordSplitCount
        )
    }
    
    return ContextStats(
        totalTokens = chapterTokens + lorebookTokens,
        chapterCount = context.contextElements.size,
        lorebookTokens = lorebookTokens,
        chatHistoryTokens = 0, // Will be calculated separately
        exceedsLimit = (chapterTokens + lorebookTokens) > 105000
    )
}
```

#### 1.2 Modify callChatPipeline Entry Point
```kotlin
fun callChatPipeline(prompt: String) {
    val mainContext = ContextBank.getContextFromBank("main")
    val stats = calculateContextStats(mainContext)
    
    if (stats.exceedsLimit) {
        handleContextOverflow(prompt, mainContext, stats)
    } else {
        executeNormalChatPipeline(prompt, mainContext)
    }
}
```

### Phase 2: Context Selection Shell

#### 2.1 Context Selection Strategies
```kotlin
enum class ContextSelectionMode {
    RECENT_CHAPTERS,    // Last N chapters
    CHAPTER_RANGE,      // Specific range (e.g., 5-10)
    CHAPTER_LIST,       // Specific chapters (e.g., 1,3,7,12)
    KEYWORD_SEARCH,     // Chapters containing keywords
    MANUAL_SELECT       // Interactive chapter-by-chapter selection
}
```

#### 2.2 Context Selection Shell
```kotlin
fun handleContextOverflow(prompt: String, context: ContextWindow, stats: ContextStats) {
    println("Story exceeds 105K token limit (${stats.totalTokens} tokens)")
    println("Available chapters: ${stats.chapterCount}")
    showContextSelectionMenu()
    
    while (true) {
        val selection = getContextSelection()
        val selectedContext = buildSelectedContext(selection, context)
        val newStats = calculateContextStats(selectedContext)
        
        if (newStats.exceedsLimit) {
            println("Selection still exceeds limit (${newStats.totalTokens} tokens). Try again.")
            continue
        }
        
        executeNormalChatPipeline(prompt, selectedContext)
        break
    }
}
```

#### 2.3 Selection Menu Interface
```kotlin
fun showContextSelectionMenu() {
    println("""
        Context Selection Options:
        1. recent <N>           - Use last N chapters
        2. range <start>-<end>  - Use chapter range
        3. list <1,3,5,7>      - Use specific chapters
        4. search <keywords>    - Find chapters with keywords
        5. manual              - Select chapters interactively
        6. stats               - Show detailed token breakdown
        7. help                - Show this menu
    """.trimIndent())
}
```

### Phase 3: Selection Implementations

#### 3.1 Recent Chapters Selection
```kotlin
fun selectRecentChapters(context: ContextWindow, count: Int): ContextWindow {
    val selectedContext = ContextWindow()
    selectedContext.loreBookKeys.putAll(context.loreBookKeys)
    
    val elements = context.contextElements
    val startIndex = maxOf(0, elements.size - count)
    selectedContext.contextElements.addAll(elements.subList(startIndex, elements.size))
    
    return selectedContext
}
```

#### 3.2 Range Selection
```kotlin
fun selectChapterRange(context: ContextWindow, start: Int, end: Int): ContextWindow {
    val selectedContext = ContextWindow()
    selectedContext.loreBookKeys.putAll(context.loreBookKeys)
    
    val elements = context.contextElements
    val validStart = (start - 1).coerceIn(0, elements.size - 1)
    val validEnd = (end - 1).coerceIn(validStart, elements.size - 1)
    
    selectedContext.contextElements.addAll(elements.subList(validStart, validEnd + 1))
    return selectedContext
}
```

#### 3.3 Keyword Search Selection
```kotlin
fun selectChaptersByKeywords(context: ContextWindow, keywords: List<String>): ContextWindow {
    val selectedContext = ContextWindow()
    selectedContext.loreBookKeys.putAll(context.loreBookKeys)
    
    context.contextElements.forEachIndexed { index, chapter ->
        val containsKeyword = keywords.any { keyword ->
            chapter.contains(keyword, ignoreCase = true)
        }
        if (containsKeyword) {
            selectedContext.contextElements.add(chapter)
        }
    }
    
    return selectedContext
}
```

#### 3.4 Manual Interactive Selection
```kotlin
fun selectChaptersManually(context: ContextWindow): ContextWindow {
    val selectedContext = ContextWindow()
    selectedContext.loreBookKeys.putAll(context.loreBookKeys)
    
    println("Select chapters to include (y/n for each):")
    
    context.contextElements.forEachIndexed { index, chapter ->
        val preview = chapter.take(100) + if (chapter.length > 100) "..." else ""
        val tokens = Dictionary.countTokens(chapter)
        
        println("\nChapter ${index + 1} ($tokens tokens):")
        println("\"$preview\"")
        print("Include? (y/n): ")
        
        val response = readLineWithClipboard().lowercase()
        if (response == "y" || response == "yes") {
            selectedContext.contextElements.add(chapter)
        }
    }
    
    return selectedContext
}
```

### Phase 4: Enhanced User Experience

#### 4.1 Token Budget Display
```kotlin
fun showTokenBreakdown(stats: ContextStats) {
    println("""
        Token Usage Breakdown:
        ├─ Story Chapters: ${stats.totalTokens - stats.lorebookTokens - stats.chatHistoryTokens} tokens
        ├─ Lorebook: ${stats.lorebookTokens} tokens  
        ├─ Chat History: ${stats.chatHistoryTokens} tokens
        └─ Total: ${stats.totalTokens} tokens
        
        Limit: 105,000 tokens
        Overflow: ${stats.totalTokens - 105000} tokens
    """.trimIndent())
}
```

#### 4.2 Smart Suggestions
```kotlin
fun suggestOptimalSelection(context: ContextWindow): String {
    val elements = context.contextElements
    val totalTokens = calculateContextStats(context).totalTokens
    
    return when {
        totalTokens < 120000 -> "Try 'recent ${elements.size - 2}' to remove oldest chapters"
        totalTokens < 150000 -> "Try 'recent ${elements.size / 2}' to use recent half"
        else -> "Try 'recent 5' or use keyword search to find relevant chapters"
    }
}
```

#### 4.3 Selection Persistence
```kotlin
data class ChatContextPreferences(
    val defaultMode: ContextSelectionMode = ContextSelectionMode.RECENT_CHAPTERS,
    val defaultRecentCount: Int = 10,
    val savedSelections: Map<String, List<Int>> = emptyMap()
)

fun saveContextSelection(name: String, selection: List<Int>) {
    // Save user's selection for reuse
}

fun loadContextSelection(name: String): List<Int>? {
    // Load previously saved selection
}
```

### Phase 5: Integration Points

#### 5.1 Modify recordUserDiscussionContext
```kotlin
suspend fun recordUserDiscussionContext(content: ContextWindow): ContextWindow {
    // Check if context will exceed limits after adding chat history
    val chatContext = ContextBank.getContextFromBank("chat")
    val estimatedTokens = calculateEstimatedTokens(content, chatContext)
    
    if (estimatedTokens > 105000) {
        println("Warning: Adding chat history will exceed token limit")
        // Offer to truncate chat history or reselect context
    }
    
    // ... existing implementation
}
```

#### 5.2 Add Context Commands to Chat Mode
```kotlin
// In chat mode, add commands:
// /context stats    - Show current context stats
// /context select   - Reselect context
// /context recent N - Switch to recent N chapters
// /context save     - Save current selection
```

### Phase 6: Testing & Validation

#### 6.1 Test Cases
- [ ] Story under 105K tokens (normal flow)
- [ ] Story over 105K tokens (selection required)
- [ ] Various selection modes work correctly
- [ ] Token counting is accurate
- [ ] Chat history preservation
- [ ] Lorebook always included
- [ ] Edge cases (empty story, single chapter, etc.)

#### 6.2 Performance Considerations
- Cache token counts for chapters
- Lazy load chapter content for previews
- Optimize context window operations

## Implementation Order

1. **Phase 1**: Add token detection and basic overflow handling
2. **Phase 2**: Implement context selection shell with basic menu
3. **Phase 3**: Add all selection modes (start with recent/range)
4. **Phase 4**: Enhance UX with token display and suggestions
5. **Phase 5**: Integrate with existing chat pipeline
6. **Phase 6**: Test and optimize

## Critical Implementation Notes

### Token Counting Consistency
**IMPORTANT**: All token counting must use `Pipe.getTruncationSettings()` to ensure consistency with the actual pipeline behavior:

```kotlin
// Always use this pattern for token counting:
val examplePipe = BedrockPipe().truncateModuleContext()
val settings = examplePipe.getTruncationSettings()

val tokenCount = Dictionary.countTokens(
    text,
    settings.countSubWordsInFirstWord,
    settings.favorWholeWords,
    settings.countOnlyFirstWordFound,
    settings.splitForNonWordChar,
    settings.alwaysSplitIfWholeWordExists,
    settings.countSubWordsIfSplit,
    settings.nonWordSplitCount
)
```

This ensures that:
- Context selection uses the same token counting as the actual pipeline
- No discrepancies between estimated and actual token usage
- Consistent behavior across all context operations

## Files to Modify

### Existing Files
- `/home/cage/Desktop/Workspaces/TPipeWriter/src/main/kotlin/Shell/Shell.kt` - Add context selection functions
- `/home/cage/Desktop/Workspaces/TPipeWriter/src/main/kotlin/Globals/Env.kt` - Modify recordUserDiscussionContext

### New Files to Create
- `/home/cage/Desktop/Workspaces/TPipeWriter/src/main/kotlin/Shell/ContextSelector.kt` - Context selection logic and interactive shell
- `/home/cage/Desktop/Workspaces/TPipeWriter/src/main/kotlin/Shell/ContextStats.kt` - Token calculation utilities (using getTruncationSettings())
- `/home/cage/Desktop/Workspaces/TPipeWriter/src/main/kotlin/Shell/ContextSelectionMode.kt` - Enums and data classes for context selection

## Success Criteria

✅ Users can chat about large stories without losing context  
✅ Intelligent context selection based on user needs  
✅ Clear feedback about token usage and limits  
✅ Preserved chat history and lorebook access  
✅ Intuitive interface for context management  
✅ Performance remains acceptable for large stories  
✅ **Token counting matches actual pipeline behavior (using getTruncationSettings())**  
✅ **No discrepancies between estimated and actual token usage**