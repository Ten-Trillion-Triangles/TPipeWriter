# Token Counting Subshell Implementation Plan

## Overview
This plan details the implementation of a comprehensive token counting subshell for TPipeWriter that allows users to count tokens for chapters, lorebook contents, and configure different model settings for accurate token counting across various AI models.

## Project Context Analysis

### TPipeWriter Structure
- **Shell System**: Located in `/src/main/kotlin/Shell/` with existing subshells (Character, Writer, Settings, etc.)
- **Main Entry**: `Main.kt` initializes environment and calls `startShell()`
- **Dependencies**: Uses TPipe library via composite build with comprehensive token counting capabilities
- **Patterns**: Subshells follow naming convention `functionName + "Subshell"` and use `readEnhancedInput()` for user interaction

### TPipe Library Capabilities
- **Token Counting**: `Dictionary.countTokens()` with configurable `TruncationSettings`
- **Context Management**: `ContextWindow.selectAndTruncateContext()` for processing context with token budgets
- **Model Configuration**: `truncateModuleContext()` abstract method for model-specific setup
- **Chapter System**: Existing chapter management in `/Chapter/` directory
- **LoreBook System**: Comprehensive lorebook handling in `/Structs/` directory

## Implementation Plan

### Phase 1: Core Token Counting Subshell Structure

#### Step 1.1: Create TokenCountingSubshell.kt
**File**: `/src/main/kotlin/Shell/TokenCountingSubshell.kt`

**Purpose**: Main subshell implementation with user interface and command routing

**Key Components**:
```kotlin
package Shell

import com.TTT.Context.ContextWindow
import com.TTT.Context.Dictionary
import com.TTT.Context.ContextBank
import com.TTT.Pipe.TruncationSettings
import bedrockPipe.BedrockMultimodalPipe
import Chapter.ChapterManager
import Structs.*
import readEnhancedInput

/**
 * Token counting subshell for analyzing token usage across chapters, 
 * lorebook contents, and different model configurations.
 */
fun tokenCountingSubshell() {
    println("\n=== Token Counting Subshell ===")
    println("Available commands:")
    println("  1. chapter <index> - Count tokens for specific chapter")
    println("  2. chapters <start>-<end> - Count tokens for chapter range")
    println("  3. lorebook [key] - Count tokens for lorebook (all or specific key)")
    println("  4. context <text> - Count tokens for custom text with current model")
    println("  5. model <model-name> - Set model for token counting")
    println("  6. models - List available models")
    println("  7. settings - Show current token counting settings")
    println("  8. help - Show this help")
    println("  9. exit - Return to main shell")
    
    var currentModel = deepSeekModelName() // Default to DeepSeek
    var truncationSettings = createTruncationSettings(currentModel)
    
    while (true) {
        print("token> ")
        val input = readEnhancedInput().trim()
        
        if (input.isEmpty()) continue
        
        val parts = input.split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1] else ""
        
        when (command) {
            "chapter" -> handleChapterTokenCount(args, truncationSettings)
            "chapters" -> handleChapterRangeTokenCount(args, truncationSettings)
            "lorebook" -> handleLorebookTokenCount(args, truncationSettings)
            "context" -> handleContextTokenCount(args, truncationSettings)
            "model" -> {
                currentModel = handleModelSelection(args)
                truncationSettings = createTruncationSettings(currentModel)
            }
            "models" -> showAvailableModels()
            "settings" -> showCurrentSettings(currentModel, truncationSettings)
            "help" -> showHelp()
            "exit" -> break
            else -> println("Unknown command: $command. Type 'help' for available commands.")
        }
    }
}
```

#### Step 1.2: Model Configuration System
**Purpose**: Support for TPipeWriter's actual AWS Bedrock models with appropriate truncation settings

**Key Functions**:
```kotlin
import Structs.*

/**
 * Creates truncation settings for the specified model.
 * Uses TPipeWriter's actual AWS Bedrock model identifiers.
 */
private fun createTruncationSettings(modelName: String): TruncationSettings {
    return when (modelName) {
        deepSeekModelName() -> {
            val pipe = BedrockMultimodalPipe().setModel(deepSeekModelName())
            pipe.truncateModuleContext().getTruncationSettings()
        }
        novaModelName(), novaLiteModelName(), "amazon.nova-2-lite-v1:0", "amazon.nova-2-pro-preview-v1:0" -> {
            val pipe = BedrockMultimodalPipe().setModel(modelName)
            pipe.truncateModuleContext().getTruncationSettings()
        }
        claudeModelName() -> {
            val pipe = BedrockMultimodalPipe().setModel(claudeModelName())
            pipe.truncateModuleContext().getTruncationSettings()
        }
        gptModelName(), gpt120bModelName() -> {
            val pipe = BedrockMultimodalPipe().setModel(modelName)
            pipe.truncateModuleContext().getTruncationSettings()
        }
        else -> {
            // Default to DeepSeek settings
            val pipe = BedrockMultimodalPipe().setModel(deepSeekModelName())
            pipe.truncateModuleContext().getTruncationSettings()
        }
    }
}

/**
 * Lists all supported models for token counting.
 */
private fun showAvailableModels() {
    println("\nSupported models:")
    println("  DeepSeek: ${deepSeekModelName()}")
    println("  Nova Pro: ${novaModelName()}")
    println("  Nova Lite: ${novaLiteModelName()}")
    println("  Nova 2 Lite: amazon.nova-2-lite-v1:0")
    println("  Nova 2 Pro: amazon.nova-2-pro-preview-v1:0")
    println("  Claude: ${claudeModelName()}")
    println("  GPT: ${gptModelName()}")
    println("  GPT 120B: ${gpt120bModelName()}")
    println("\nUse 'model <model-name>' to switch models")
}
```

### Phase 2: Chapter Token Counting Implementation

#### Step 2.1: Single Chapter Analysis
**Purpose**: Count tokens for individual chapters with detailed breakdown

**Implementation**:
```kotlin
/**
 * Handles token counting for a single chapter.
 * Leverages existing ChapterManager utilities and adds token analysis.
 */
private fun handleChapterTokenCount(chapterIndex: String, settings: TruncationSettings) {
    try {
        val index = chapterIndex.toIntOrNull()
        if (index == null) {
            println("Error: Please provide a valid chapter number")
            return
        }
        
        val chapterManager = ChapterManager()
        val chapterContent = chapterManager.getChapter(index - 1)
        
        if (chapterContent == null) {
            println("Error: Chapter $index not found")
            return
        }
        
        println("\n=== Chapter $index Token Analysis ===")
        
        // Add token counting to existing stats
        val tokens = Dictionary.countTokens(chapterContent, settings)
        println("Tokens: $tokens")
        
        // Reuse existing chapter stats functionality
        val stats = chapterManager.getChapterStats()
        println("Characters: ${chapterContent.length}")
        println("Words: ${chapterContent.split("\\s+".toRegex()).size}")
        println("Token density: ${String.format("%.2f", tokens.toDouble() / chapterContent.length)} tokens/char")
        
        // Context window simulation using existing utilities
        val budgets = listOf(1000, 2000, 4000, 8000, 16000, 32000)
        println("\nToken budget simulation:")
        for (budget in budgets) {
            val selectedChapters = chapterManager.selectChaptersWithinTokenBudget(budget, settings)
            val fits = selectedChapters.contains(chapterContent)
            println("  Budget $budget: ${if (fits) "fits" else "truncated"}")
        }
        
    } catch (e: Exception) {
        println("Error analyzing chapter: ${e.message}")
    }
}
```

#### Step 2.2: Chapter Range Analysis
**Purpose**: Analyze token usage across multiple chapters

**Implementation**:
```kotlin
/**
 * Handles token counting for a range of chapters.
 * Uses existing ChapterManager utilities for selection and analysis.
 */
private fun handleChapterRangeTokenCount(range: String, settings: TruncationSettings) {
    try {
        val rangeParts = range.split("-")
        if (rangeParts.size != 2) {
            println("Error: Please use format 'start-end' (e.g., '1-5')")
            return
        }
        
        val startIndex = rangeParts[0].toIntOrNull()
        val endIndex = rangeParts[1].toIntOrNull()
        
        if (startIndex == null || endIndex == null || startIndex > endIndex) {
            println("Error: Invalid range format")
            return
        }
        
        val chapterManager = ChapterManager()
        
        // Use existing getChapterRange utility
        val chapters = chapterManager.getChapterRange(startIndex, endIndex)
        
        if (chapters.isEmpty()) {
            println("Error: No chapters found in range $startIndex-$endIndex")
            return
        }
        
        println("\n=== Chapters $startIndex-$endIndex Token Analysis ===")
        
        // Calculate total tokens using existing utilities
        val totalTokens = chapters.sumOf { Dictionary.countTokens(it, settings) }
        
        // Reuse existing stats functionality
        val stats = chapterManager.getChapterStats()
        println("Chapters in range: ${chapters.size}")
        println("Total tokens: $totalTokens")
        println("Average tokens per chapter: ${totalTokens / chapters.size}")
        
        // Use existing selectChaptersWithinTokenBudget for budget analysis
        println("\nBudget analysis:")
        val budgets = listOf(5000, 10000, 20000, 50000)
        for (budget in budgets) {
            val selectedChapters = chapterManager.selectChaptersWithinTokenBudget(budget, settings)
            val selectedFromRange = selectedChapters.intersect(chapters.toSet())
            println("  Budget $budget: ${selectedFromRange.size}/${chapters.size} chapters fit")
        }
        
    } catch (e: Exception) {
        println("Error analyzing chapter range: ${e.message}")
    }
}
```

### Phase 3: Lorebook Token Counting Implementation

#### Step 3.1: Complete Lorebook Analysis
**Purpose**: Analyze token usage for entire lorebook or specific keys

**Implementation**:
```kotlin
/**
 * Handles token counting for lorebook content.
 * Can analyze entire lorebook or specific keys.
 */
private fun handleLorebookTokenCount(keyFilter: String, settings: TruncationSettings) {
    try {
        // Load lorebook data from ContextBank
        val contextWindow = ContextBank.getContextFromBank("main")
        val lorebookEntries = contextWindow.loreBookKeys
        
        if (lorebookEntries.isEmpty()) {
            println("Error: No lorebook data found")
            return
        }
        
        val keysToAnalyze = if (keyFilter.isBlank()) {
            lorebookEntries.keys.toList()
        } else {
            lorebookEntries.keys.filter { 
                it.contains(keyFilter, ignoreCase = true) 
            }
        }
        
        if (keysToAnalyze.isEmpty()) {
            println("Error: No lorebook keys match '$keyFilter'")
            return
        }
        
        println("\n=== Lorebook Token Analysis ===")
        if (keyFilter.isNotBlank()) {
            println("Filter: '$keyFilter'")
        }
        
        var totalTokens = 0
        var totalEntries = 0
        
        // Individual key analysis
        println("\nLorebook entries:")
        for (key in keysToAnalyze.sorted()) {
            val content = lorebookEntries[key] ?: ""
            val tokens = Dictionary.countTokens(content, settings)
            
            totalTokens += tokens
            totalEntries++
            
            val preview = if (content.length > 50) {
                content.take(47) + "..."
            } else {
                content
            }
            
            println("  '$key': $tokens tokens")
            println("    Content: $preview")
        }
        
        // Aggregate statistics
        println("\nAggregate statistics:")
        println("  Total entries analyzed: $totalEntries")
        println("  Total tokens: $totalTokens")
        println("  Average tokens per entry: ${if (totalEntries > 0) totalTokens / totalEntries else 0}")
        
        // Context window simulation
        println("\nContext window simulation:")
        val budgets = listOf(1000, 2000, 4000, 8000)
        for (budget in budgets) {
            val testWindow = ContextWindow()
            testWindow.loreBookKeys = lorebookEntries.toMutableMap()
            testWindow.selectAndTruncateContext("", budget, settings)
            
            val selectedKeys = testWindow.loreBookKeys.size
            val selectedTokens = testWindow.loreBookKeys.values.sumOf { content ->
                Dictionary.countTokens(content, settings)
            }
            
            println("  Budget $budget: $selectedKeys keys, $selectedTokens tokens")
        }
        
    } catch (e: Exception) {
        println("Error analyzing lorebook: ${e.message}")
    }
}
```

### Phase 4: Custom Context Analysis

#### Step 4.1: Custom Text Token Counting
**Purpose**: Allow users to input custom text for token analysis

**Implementation**:
```kotlin
/**
 * Handles token counting for custom user-provided text.
 * Uses existing input patterns from Shell.kt.
 */
private fun handleContextTokenCount(initialText: String, settings: TruncationSettings) {
    val text = if (initialText.isNotBlank()) {
        initialText
    } else {
        println("Enter text to analyze (type 'END' on a new line to finish):")
        val result = readEnhancedInput("END")
        result.substringBeforeLast("END").trim()
    }
    
    if (text.isBlank()) {
        println("Error: No text provided")
        return
    }
    
    println("\n=== Custom Text Token Analysis ===")
    
    // Basic token counting
    val tokens = Dictionary.countTokens(text, settings)
    println("Tokens: $tokens")
    println("Characters: ${text.length}")
    println("Words: ${text.split("\\s+".toRegex()).size}")
    println("Lines: ${text.lines().size}")
    println("Token density: ${String.format("%.2f", tokens.toDouble() / text.length)} tokens/char")
    
    // Use existing ContextWindow for budget simulation
    val budgets = listOf(500, 1000, 2000, 4000, 8000)
    println("\nBudget simulation:")
    for (budget in budgets) {
        val contextWindow = ContextWindow()
        contextWindow.contextElements = mutableListOf(text)
        contextWindow.selectAndTruncateContext("", budget, settings)
        
        val remainingText = contextWindow.contextElements.firstOrNull() ?: ""
        val remainingTokens = Dictionary.countTokens(remainingText, settings)
        val percentage = String.format("%.1f", (remainingTokens.toDouble() / tokens) * 100)
        
        println("  Budget $budget: $remainingTokens tokens ($percentage%)")
    }
}
```

### Phase 5: Integration with Main Shell

#### Step 5.1: Add Command to Main Shell
**File**: `/src/main/kotlin/Shell/Shell.kt`
**Location**: Add to command processing section (around line 270)

**Changes**:
```kotlin
// Add to the command processing switch statement
"tokens" -> tokenCountingSubshell()
```

#### Step 5.2: Update Help System
**File**: `/src/main/kotlin/Shell/Shell.kt`
**Location**: Update help text to include new command

**Changes**:
```kotlin
// Add to help text
println("  /tokens - Enter token counting subshell")
```

### Phase 6: Settings and Configuration

#### Step 6.1: Token Counting Settings Display
**Purpose**: Show current model and truncation settings

**Implementation**:
```kotlin
/**
 * Displays current token counting settings and model configuration.
 */
private fun showCurrentSettings(modelName: String, settings: TruncationSettings) {
    println("\n=== Current Token Counting Settings ===")
    println("Model: $modelName")
    println("Truncation Settings:")
    println("  Count subwords in first word: ${settings.countSubWordsInFirstWord}")
    println("  Favor whole words: ${settings.favorWholeWords}")
    println("  Count only first word found: ${settings.countOnlyFirstWordFound}")
    println("  Split for non-word characters: ${settings.splitForNonWordChar}")
    println("  Always split if whole word exists: ${settings.alwaysSplitIfWholeWordExists}")
    println("  Count subwords if split: ${settings.countSubWordsIfSplit}")
    println("  Non-word split count: ${settings.nonWordSplitCount}")
    println("  Multiply window size by: ${settings.multiplyWindowSizeBy}")
}

/**
 * Shows help information for the token counting subshell.
 */
private fun showHelp() {
    println("\n=== Token Counting Subshell Help ===")
    println("Commands:")
    println("  chapter <index>")
    println("    Count tokens for a specific chapter")
    println("    Example: chapter 5")
    println()
    println("  chapters <start>-<end>")
    println("    Count tokens for a range of chapters")
    println("    Example: chapters 1-10")
    println()
    println("  lorebook [filter]")
    println("    Count tokens for lorebook entries")
    println("    Example: lorebook character (filter by 'character')")
    println("    Example: lorebook (analyze all entries)")
    println()
    println("  context [text]")
    println("    Count tokens for custom text")
    println("    Example: context Hello world")
    println("    Example: context (enter multiline mode)")
    println()
    println("  model <model-name>")
    println("    Switch to a different model for token counting")
    println("    Example: model claude-3-haiku")
    println()
    println("  models")
    println("    List all available models")
    println()
    println("  settings")
    println("    Show current model and truncation settings")
    println()
    println("  help")
    println("    Show this help message")
    println()
    println("  exit")
    println("    Return to main shell")
}
```

### Phase 7: Error Handling and Validation

#### Step 7.1: Robust Error Handling
**Purpose**: Handle edge cases and provide helpful error messages

**Key Areas**:
- Invalid chapter indices
- Missing lorebook data
- Empty or invalid text input
- Model configuration errors
- File system access issues

#### Step 7.2: Input Validation
**Purpose**: Validate user input before processing

**Implementation**:
```kotlin
/**
 * Validates chapter index input.
 */
private fun validateChapterIndex(input: String): Int? {
    val index = input.toIntOrNull()
    if (index == null) {
        println("Error: '$input' is not a valid chapter number")
        return null
    }
    if (index < 1) {
        println("Error: Chapter index must be positive")
        return null
    }
    return index
}

/**
 * Validates model name input using existing Shell.kt patterns.
 */
private fun handleModelSelection(modelName: String): String {
    if (modelName.isBlank()) {
        println("Error: Please specify a model name")
        showAvailableModels()
        return deepSeekModelName()
    }
    
    val supportedModels = listOf(
        deepSeekModelName(), novaModelName(), novaLiteModelName(),
        "amazon.nova-2-lite-v1:0", "amazon.nova-2-pro-preview-v1:0",
        claudeModelName(), gptModelName(), gpt120bModelName()
    )
    
    if (modelName !in supportedModels) {
        println("Warning: Model '$modelName' not explicitly supported, using default settings")
    }
    
    println("Switched to model: $modelName")
    return modelName
}
```

## Implementation Timeline

### Week 1: Core Infrastructure (Minimal Implementation)
- **Days 1-2**: Create `TokenCountingSubshell.kt` with basic structure reusing existing patterns
- **Days 3-4**: Implement model configuration using existing `BedrockMultimodalPipe` patterns
- **Days 5-7**: Develop token counting using existing `ChapterManager` and `Dictionary` utilities

### Week 2: Integration (Leverage Existing Code)
- **Days 1-3**: Integrate with existing `ContextBank` for lorebook access
- **Days 4-5**: Use existing `readEnhancedInput()` and validation patterns from Shell.kt
- **Days 6-7**: Add to main shell using existing command routing patterns

### Week 3: Polish (Minimal Custom Code)
- **Days 1-3**: Use existing error handling patterns from Shell.kt
- **Days 4-5**: Reuse existing help and settings display patterns
- **Days 6-7**: Testing with existing utilities

## Validation Criteria

### Functional Requirements
✅ **Chapter Analysis**: Count tokens for individual chapters and chapter ranges
✅ **Lorebook Analysis**: Count tokens for entire lorebook or filtered keys
✅ **Custom Text Analysis**: Support for user-provided text with multiline input
✅ **Model Configuration**: Support for different AI models with appropriate settings
✅ **Context Window Simulation**: Show how content fits in different token budgets
✅ **Integration**: Seamless integration with existing shell system

### Quality Requirements
✅ **User Experience**: Intuitive command interface with helpful error messages
✅ **Performance**: Efficient token counting without significant delays
✅ **Accuracy**: Precise token counts matching model-specific tokenization
✅ **Extensibility**: Easy to add new models and analysis features
✅ **Maintainability**: Clean code following existing project patterns

## Risk Mitigation

### Technical Risks
- **Model Compatibility**: Mitigate by providing fallback to default settings for unsupported models
- **Large Content Processing**: Implement streaming or chunked processing for very large chapters
- **Memory Usage**: Use efficient data structures and avoid loading all content simultaneously

### User Experience Risks
- **Complex Interface**: Provide comprehensive help and intuitive command structure
- **Performance Issues**: Show progress indicators for long-running operations
- **Data Access**: Graceful handling of missing or corrupted chapter/lorebook data

## Future Enhancements

### Phase 2 Features (Post-MVP)
- **Export Functionality**: Save token analysis results to files
- **Batch Processing**: Analyze multiple chapters/lorebook entries in background
- **Visualization**: Generate charts and graphs for token usage patterns
- **Comparison Mode**: Compare token usage across different models
- **Optimization Suggestions**: Recommend content modifications to fit token budgets

### Integration Opportunities
- **Writer Pipeline Integration**: Use token analysis to optimize writing prompts
- **Context Selection**: Automatically select optimal context based on token analysis
- **Budget Management**: Real-time token budget tracking during writing sessions

This comprehensive plan provides a robust foundation for implementing a powerful token counting subshell that enhances TPipeWriter's capabilities while maintaining consistency with existing patterns and user experience.
