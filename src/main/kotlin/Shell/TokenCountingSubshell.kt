package Shell

import com.TTT.Context.ContextWindow
import com.TTT.Context.Dictionary
import com.TTT.Context.ContextBank
import com.TTT.Pipe.TruncationSettings
import bedrockPipe.BedrockMultimodalPipe
import Chapter.ChapterManager
import Structs.*
import readEnhancedInput
import stripMultiLineDelimiter

/**
 * Token counting subshell for analyzing token usage across chapters, 
 * lorebook contents, and different model configurations.
 * 
 * Provides comprehensive token analysis capabilities including:
 * - Individual chapter token counting with detailed breakdowns
 * - Chapter range analysis with aggregate statistics
 * - Lorebook entry token analysis with filtering support
 * - Custom text analysis with multiline input support
 * - Model switching for different AI tokenization schemes
 * - Budget simulation for context window planning
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

/**
 * Creates truncation settings for the specified model.
 * Uses TPipeWriter's actual AWS Bedrock model identifiers and configures
 * appropriate tokenization settings for each supported model.
 * 
 * @param modelName The model identifier (e.g., deepseek.r1-v1:0, amazon.nova-pro-v1:0)
 * @return TruncationSettings configured for the specified model
 * @throws Exception If model configuration fails, falls back to DeepSeek defaults
 */
private fun createTruncationSettings(modelName: String): TruncationSettings {
    return try {
        when (modelName) {
            deepSeekModelName() -> {
                val pipe = BedrockMultimodalPipe().setModel(deepSeekModelName())
                pipe.truncateModuleContext().getTruncationSettings()
            }
            novaModelName() -> {
                val pipe = BedrockMultimodalPipe().setModel(novaModelName())
                pipe.truncateModuleContext().getTruncationSettings()
            }
            novaLiteModelName() -> {
                val pipe = BedrockMultimodalPipe().setModel(novaLiteModelName())
                pipe.truncateModuleContext().getTruncationSettings()
            }
            claudeModelName() -> {
                val pipe = BedrockMultimodalPipe().setModel(claudeModelName())
                pipe.truncateModuleContext().getTruncationSettings()
            }
            gptModelName() -> {
                val pipe = BedrockMultimodalPipe().setModel(gptModelName())
                pipe.truncateModuleContext().getTruncationSettings()
            }
            gpt120bModelName() -> {
                val pipe = BedrockMultimodalPipe().setModel(gpt120bModelName())
                pipe.truncateModuleContext().getTruncationSettings()
            }
            // Qwen models
            "qwen.qwen3-235b-a22b-2507-v1:0" -> {
                val pipe = BedrockMultimodalPipe().setModel("qwen.qwen3-235b-a22b-2507-v1:0")
                pipe.truncateModuleContext().getTruncationSettings()
            }
            "qwen.qwen3-32b-v1:0" -> {
                val pipe = BedrockMultimodalPipe().setModel("qwen.qwen3-32b-v1:0")
                pipe.truncateModuleContext().getTruncationSettings()
            }
            "qwen.qwen3-coder-480b-a35b-v1:0" -> {
                val pipe = BedrockMultimodalPipe().setModel("qwen.qwen3-coder-480b-a35b-v1:0")
                pipe.truncateModuleContext().getTruncationSettings()
            }
            "qwen.qwen3-coder-30b-a3b-v1:0" -> {
                val pipe = BedrockMultimodalPipe().setModel("qwen.qwen3-coder-30b-a3b-v1:0")
                pipe.truncateModuleContext().getTruncationSettings()
            }
            // PalmyraX5 model
            "writer.palmyra-x5-v1:0" -> {
                val pipe = BedrockMultimodalPipe().setModel("writer.palmyra-x5-v1:0")
                pipe.truncateModuleContext().getTruncationSettings()
            }
            else -> {
                // Try to use the provided model name directly
                val pipe = BedrockMultimodalPipe().setModel(modelName)
                pipe.truncateModuleContext().getTruncationSettings()
            }
        }
    } catch (e: Exception) {
        println("Warning: Failed to configure model '$modelName', using DeepSeek defaults: ${e.message}")
        // Default to DeepSeek settings if model fails
        val pipe = BedrockMultimodalPipe().setModel(deepSeekModelName())
        pipe.truncateModuleContext().getTruncationSettings()
    }
}

/**
 * Lists all supported models for token counting with their full identifiers.
 * Displays both the friendly names and actual AWS Bedrock model IDs,
 * along with usage instructions for model switching.
 */
private fun showAvailableModels() {
    println("\nSupported models:")
    println("  DeepSeek: ${deepSeekModelName()}")
    println("  Nova Pro: ${novaModelName()}")
    println("  Nova Lite: ${novaLiteModelName()}")
    println("  Claude: ${claudeModelName()}")
    println("  GPT: ${gptModelName()}")
    println("  GPT 120B: ${gpt120bModelName()}")
    println("  Qwen 235B: qwen.qwen3-235b-a22b-2507-v1:0")
    println("  Qwen 32B: qwen.qwen3-32b-v1:0")
    println("  Qwen Coder 480B: qwen.qwen3-coder-480b-a35b-v1:0")
    println("  Qwen Coder 30B: qwen.qwen3-coder-30b-a3b-v1:0")
    println("  PalmyraX5: writer.palmyra-x5-v1:0")
    println("\nUse 'model <model-name>' to switch models")
    println("You can use either the full model ID or the short name (e.g., 'deepseek' or '${deepSeekModelName()}')")
}

/**
 * Handles token counting for a single chapter with comprehensive analysis.
 * Leverages existing ChapterManager utilities and provides detailed breakdown
 * including character count, word count, token density, and budget simulation.
 * 
 * @param args Chapter index as string (1-based indexing)
 * @param settings TruncationSettings for the current model
 */
private fun handleChapterTokenCount(args: String, settings: TruncationSettings) {
    try {
        val index = validateChapterIndex(args)
        if (index == null) return
        
        // Get context and create ChapterManager following existing patterns
        val context = com.TTT.Context.ContextBank.getContextFromBank("main")
        val chapterManager = ChapterManager(context)
        
        val chapterContent = chapterManager.getChapter(index - 1)
        
        if (chapterContent == null) {
            println("Error: Chapter $index not found")
            return
        }
        
        println("\n=== Chapter $index Token Analysis ===")
        
        // Add token counting to existing stats
        val tokens = com.TTT.Context.Dictionary.countTokens(chapterContent, settings)
        println("Tokens: $tokens")
        
        // Basic content statistics
        println("Characters: ${chapterContent.length}")
        val words = chapterContent.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
        println("Words: $words")
        println("Lines: ${chapterContent.lines().size}")
        
        // Token density analysis
        if (chapterContent.isNotEmpty()) {
            val tokenDensity = tokens.toDouble() / chapterContent.length
            println("Token density: ${String.format("%.3f", tokenDensity)} tokens/char")
        }
        
        if (words > 0) {
            val tokensPerWord = tokens.toDouble() / words
            println("Tokens per word: ${String.format("%.2f", tokensPerWord)}")
        }
        
        // Context window simulation using existing utilities
        println("\nToken budget simulation:")
        val budgets = listOf(1000, 2000, 4000, 8000, 16000, 32000)
        for (budget in budgets) {
            val selectedChapters = chapterManager.selectChaptersWithinTokenBudget(budget, settings)
            val fits = selectedChapters.contains(chapterContent)
            val status = if (fits) "✓ fits" else "✗ truncated"
            println("  Budget $budget: $status")
        }
        
    } catch (e: Exception) {
        println("Error analyzing chapter: ${e.message}")
    }
}

/**
 * Handles token counting for a range of chapters with aggregate analysis.
 * Uses existing ChapterManager utilities for selection and provides both
 * individual chapter breakdowns and comprehensive aggregate statistics.
 * 
 * @param args Chapter range in format "start-end" (e.g., "1-5")
 * @param settings TruncationSettings for the current model
 */
private fun handleChapterRangeTokenCount(args: String, settings: TruncationSettings) {
    try {
        val rangeParts = args.split("-")
        if (rangeParts.size != 2) {
            println("Error: Please use format 'start-end' (e.g., '1-5')")
            return
        }
        
        val startIndex = rangeParts[0].trim().toIntOrNull()
        val endIndex = rangeParts[1].trim().toIntOrNull()
        
        if (startIndex == null || endIndex == null) {
            println("Error: Invalid range format - both start and end must be numbers")
            return
        }
        
        if (startIndex < 1 || endIndex < 1) {
            println("Error: Chapter indices must be positive")
            return
        }
        
        if (startIndex > endIndex) {
            println("Error: Start index must be less than or equal to end index")
            return
        }
        
        // Get context and create ChapterManager following existing patterns
        val context = com.TTT.Context.ContextBank.getContextFromBank("main")
        val chapterManager = ChapterManager(context)
        
        // Use existing getChapterRange utility
        val chapters = chapterManager.getChapterRange(startIndex, endIndex)
        
        if (chapters.isEmpty()) {
            println("Error: No chapters found in range $startIndex-$endIndex")
            return
        }
        
        println("\n=== Chapters $startIndex-$endIndex Token Analysis ===")
        
        // Calculate individual and total statistics
        var totalTokens = 0
        var totalCharacters = 0
        var totalWords = 0
        var totalLines = 0
        
        println("\nIndividual chapter breakdown:")
        chapters.forEachIndexed { index, chapter ->
            val chapterNum = startIndex + index
            val tokens = com.TTT.Context.Dictionary.countTokens(chapter, settings)
            val words = chapter.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            val lines = chapter.lines().size
            
            totalTokens += tokens
            totalCharacters += chapter.length
            totalWords += words
            totalLines += lines
            
            println("  Chapter $chapterNum: $tokens tokens, $words words, ${chapter.length} chars")
        }
        
        // Aggregate statistics
        println("\nAggregate statistics:")
        println("  Chapters in range: ${chapters.size}")
        println("  Total tokens: $totalTokens")
        println("  Total characters: $totalCharacters")
        println("  Total words: $totalWords")
        println("  Total lines: $totalLines")
        
        if (chapters.isNotEmpty()) {
            println("  Average tokens per chapter: ${totalTokens / chapters.size}")
            println("  Average words per chapter: ${totalWords / chapters.size}")
        }
        
        if (totalCharacters > 0) {
            val tokenDensity = totalTokens.toDouble() / totalCharacters
            println("  Overall token density: ${String.format("%.3f", tokenDensity)} tokens/char")
        }
        
        // Budget analysis using existing selectChaptersWithinTokenBudget
        println("\nBudget analysis:")
        val budgets = listOf(5000, 10000, 20000, 50000, 100000)
        for (budget in budgets) {
            val selectedChapters = chapterManager.selectChaptersWithinTokenBudget(budget, settings)
            val selectedFromRange = selectedChapters.intersect(chapters.toSet())
            val selectedTokens = selectedFromRange.sumOf { com.TTT.Context.Dictionary.countTokens(it, settings) }
            println("  Budget $budget: ${selectedFromRange.size}/${chapters.size} chapters fit ($selectedTokens tokens)")
        }
        
    } catch (e: Exception) {
        println("Error analyzing chapter range: ${e.message}")
    }
}

/**
 * Handles token counting for lorebook content with filtering capabilities.
 * Can analyze entire lorebook or filter by specific key names using case-insensitive
 * matching. Provides individual entry analysis and aggregate statistics.
 * 
 * @param args Optional filter string to match against lorebook key names
 * @param settings TruncationSettings for the current model
 */
private fun handleLorebookTokenCount(args: String, settings: TruncationSettings) {
    try {
        // Load lorebook data from ContextBank
        val contextWindow = com.TTT.Context.ContextBank.getContextFromBank("main")
        val lorebookEntries = contextWindow.loreBookKeys
        
        if (lorebookEntries.isEmpty()) {
            println("Error: No lorebook data found")
            return
        }
        
        val keyFilter = args.trim()
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
        var totalCharacters = 0
        var totalWords = 0
        var totalEntries = 0
        
        // Individual key analysis
        println("\nLorebook entries:")
        for (key in keysToAnalyze.sorted()) {
            val loreEntry = lorebookEntries[key]
            if (loreEntry == null) continue
            
            val content = loreEntry.value
            val tokens = com.TTT.Context.Dictionary.countTokens(content, settings)
            val words = content.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            
            totalTokens += tokens
            totalCharacters += content.length
            totalWords += words
            totalEntries++
            
            val preview = if (content.length > 50) {
                content.take(47) + "..."
            } else {
                content
            }
            
            println("  '$key': $tokens tokens, $words words, ${content.length} chars (weight: ${loreEntry.weight})")
            println("    Content: $preview")
        }
        
        // Aggregate statistics
        println("\nAggregate statistics:")
        println("  Total entries analyzed: $totalEntries")
        println("  Total tokens: $totalTokens")
        println("  Total characters: $totalCharacters")
        println("  Total words: $totalWords")
        
        if (totalEntries > 0) {
            println("  Average tokens per entry: ${totalTokens / totalEntries}")
            println("  Average words per entry: ${totalWords / totalEntries}")
        }
        
        if (totalCharacters > 0) {
            val tokenDensity = totalTokens.toDouble() / totalCharacters
            println("  Overall token density: ${String.format("%.3f", tokenDensity)} tokens/char")
        }
        
        // Context window simulation - simplified approach
        println("\nContext window simulation:")
        val budgets = listOf(1000, 2000, 4000, 8000, 16000)
        for (budget in budgets) {
            // Calculate how many entries fit within budget
            var usedTokens = 0
            var fittingEntries = 0
            
            for (key in keysToAnalyze.sorted()) {
                val loreEntry = lorebookEntries[key]
                if (loreEntry == null) continue
                
                val content = loreEntry.value
                val tokens = com.TTT.Context.Dictionary.countTokens(content, settings)
                
                if (usedTokens + tokens <= budget) {
                    usedTokens += tokens
                    fittingEntries++
                } else {
                    break
                }
            }
            
            println("  Budget $budget: $fittingEntries/$totalEntries keys fit ($usedTokens tokens)")
        }
        
    } catch (e: Exception) {
        println("Error analyzing lorebook: ${e.message}")
    }
}

/**
 * Handles token counting for custom user-provided text with multiline support.
 * Supports both inline text input and multiline input mode using existing
 * readEnhancedInput patterns. Provides comprehensive analysis including
 * token density and budget simulation.
 * 
 * @param args Optional inline text to analyze, or empty for multiline mode
 * @param settings TruncationSettings for the current model
 */
private fun handleContextTokenCount(args: String, settings: TruncationSettings) {
    val text = if (args.isNotBlank()) {
        // Use provided text directly
        args
    } else {
        // Enter multiline mode
        println("Enter text to analyze (type 'END' on a new line to finish):")
        val result = readEnhancedInput("END")
        val lastLine = result.split("\n").lastOrNull()?.trim()?.uppercase() ?: ""
        
        if (lastLine == "END") {
            // Remove the END delimiter and get the actual content
            stripMultiLineDelimiter(result).trim()
        } else {
            result.trim()
        }
    }
    
    if (text.isBlank()) {
        println("Error: No text provided")
        return
    }
    
    println("\n=== Custom Text Token Analysis ===")
    
    // Basic token counting
    val tokens = com.TTT.Context.Dictionary.countTokens(text, settings)
    println("Tokens: $tokens")
    println("Characters: ${text.length}")
    
    val words = text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    println("Words: $words")
    println("Lines: ${text.lines().size}")
    
    // Token density analysis
    if (text.isNotEmpty()) {
        val tokenDensity = tokens.toDouble() / text.length
        println("Token density: ${String.format("%.3f", tokenDensity)} tokens/char")
    }
    
    if (words > 0) {
        val tokensPerWord = tokens.toDouble() / words
        println("Tokens per word: ${String.format("%.2f", tokensPerWord)}")
    }
    
    // Budget simulation using ContextWindow
    println("\nBudget simulation:")
    val budgets = listOf(500, 1000, 2000, 4000, 8000, 16000)
    for (budget in budgets) {
        val contextWindow = com.TTT.Context.ContextWindow()
        contextWindow.contextElements = mutableListOf(text)
        
        // Simulate truncation by checking if text fits within budget
        val textTokens = com.TTT.Context.Dictionary.countTokens(text, settings)
        if (textTokens <= budget) {
            println("  Budget $budget: ✓ fits completely ($textTokens tokens)")
        } else {
            // Calculate approximate truncation
            val ratio = budget.toDouble() / textTokens
            val truncatedLength = (text.length * ratio).toInt()
            val truncatedText = text.take(truncatedLength)
            val truncatedTokens = com.TTT.Context.Dictionary.countTokens(truncatedText, settings)
            val percentage = String.format("%.1f", (truncatedTokens.toDouble() / textTokens) * 100)
            
            println("  Budget $budget: ✗ truncated to $truncatedTokens tokens ($percentage%)")
        }
    }
}

/**
 * Validates chapter index input with comprehensive error checking.
 * Ensures the input is a valid positive integer suitable for chapter indexing.
 * 
 * @param input Raw string input from user
 * @return Validated chapter index as Int, or null if invalid
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
 * Handles model selection with support for both full model IDs and short names.
 * Validates model configuration and provides fallback to default model if needed.
 * Supports convenient short names like 'deepseek', 'nova', 'claude' in addition
 * to full AWS Bedrock model identifiers.
 * 
 * @param modelName Model identifier (full ID or short name)
 * @return Validated model name for use with createTruncationSettings
 */
private fun handleModelSelection(modelName: String): String {
    if (modelName.isBlank()) {
        println("Error: Please specify a model name")
        showAvailableModels()
        return deepSeekModelName()
    }
    
    // Support short names for convenience
    val actualModelName = when (modelName.lowercase()) {
        "deepseek" -> deepSeekModelName()
        "nova", "nova-pro" -> novaModelName()
        "nova-lite" -> novaLiteModelName()
        "claude" -> claudeModelName()
        "gpt" -> gptModelName()
        "gpt-120b", "gpt120b" -> gpt120bModelName()
        "qwen235b", "qwen-235b" -> "qwen.qwen3-235b-a22b-2507-v1:0"
        "qwen32b", "qwen-32b" -> "qwen.qwen3-32b-v1:0"
        "qwencoder480b", "qwen-coder-480b" -> "qwen.qwen3-coder-480b-a35b-v1:0"
        "qwencoder30b", "qwen-coder-30b" -> "qwen.qwen3-coder-30b-a3b-v1:0"
        "palmyrax5", "palmyra-x5", "palmyra" -> "writer.palmyra-x5-v1:0"
        else -> modelName // Use provided name directly
    }
    
    // Validate that the model can be configured
    try {
        createTruncationSettings(actualModelName)
        println("Switched to model: $actualModelName")
        return actualModelName
    } catch (e: Exception) {
        println("Error: Failed to configure model '$actualModelName': ${e.message}")
        println("Keeping current model")
        return deepSeekModelName()
    }
}

/**
 * Displays current token counting settings and model configuration details.
 * Shows the active model and all truncation settings parameters for transparency
 * and debugging purposes.
 * 
 * @param modelName Currently active model identifier
 * @param settings Current TruncationSettings configuration
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
 * Shows comprehensive help information for the token counting subshell.
 * Displays all available commands with usage examples and detailed descriptions
 * to guide users through the token analysis capabilities.
 */
private fun showHelp() {
    println("\n=== Token Counting Subshell Help ===")
    println("Commands:")
    println("  chapter <index> - Count tokens for a specific chapter")
    println("  chapters <start>-<end> - Count tokens for a range of chapters")
    println("  lorebook [filter] - Count tokens for lorebook entries")
    println("  context [text] - Count tokens for custom text")
    println("  model <model-name> - Switch to a different model")
    println("  models - List all available models")
    println("  settings - Show current settings")
    println("  help - Show this help message")
    println("  exit - Return to main shell")
}
