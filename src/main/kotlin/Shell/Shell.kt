package Shell

import Builders.buildChapterRewritePipeline
import Builders.buildExpansionPipeline
import Chapter.ChapterManager
import Globals.Env
import Structs.LoreBookData
import Structs.ModelSettings
import Structs.constructModelSettingsList
import Structs.convertPipelineToDeepseek
import Structs.toModelSettings

import bedrockPipe.BedrockPipe
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Context.Dictionary
import com.TTT.Debug.TraceConfig
import com.TTT.Debug.TraceDetailLevel
import com.TTT.Debug.TraceFormat
import com.TTT.Enums.ContextWindowSettings
import com.TTT.Pipe.MultimodalContent
import com.TTT.Pipe.TruncationSettings
import com.TTT.Pipeline.Connector
import com.TTT.Util.getHomeFolder
import com.TTT.Util.writeStringToFile
import kotlinx.coroutines.runBlocking
import readEnhancedInput
import stripMultiLineDelimiter
import java.io.File

/**
 * Command states for the interactive shell.
 * Determines which pipeline mode the shell is currently operating in.
 */
enum class CommandState
{
    Writer,
    Idea,
    Lorebook,
    Chat,
    Character,
    Summary
}

/**
 * Context selection strategies for writer pipeline.
 * Determines how to extract and process context for writing.
 */
enum class ContextSelectionStrategy
{
    SpecificPrompt,    // Use prompt for lorebook selection
    ContinueStory,     // Use last 8K tokens for lorebook
    PostIdea          // Include idea content + prompt
}

/**
 * Global writing strength setting that persists across subshell sessions
 */
var globalWritingStrength = "med"



/**
 * Determine context selection strategy based on prompt and previous state.
 * @param prompt User input prompt
 * @param previousState Previous command state
 * @return Strategy for context selection
 */
fun determineContextSelectionStrategy(prompt: String, previousState: CommandState): ContextSelectionStrategy
{
    return when {
        previousState == CommandState.Idea && ContextBank.getContextFromBank("idea").contextElements.isNotEmpty() -> ContextSelectionStrategy.PostIdea
        prompt.lowercase() == "continue" || prompt.lowercase() == "" -> ContextSelectionStrategy.ContinueStory
        else -> ContextSelectionStrategy.SpecificPrompt
    }
}

/**
 * Extract relevant context based on selection strategy.
 * @param strategy Context selection strategy
 * @param prompt User prompt
 * @return Relevant context text for lorebook selection
 */
fun extractRelevantContext(strategy: ContextSelectionStrategy, prompt: String): String
{
    val contextBank = ContextBank.getContextFromBank("main")
    val elements = contextBank.contextElements
    
    return when (strategy) {
        ContextSelectionStrategy.SpecificPrompt -> prompt
        ContextSelectionStrategy.ContinueStory -> {
            if (elements.isEmpty()) prompt
            else {
                // Use proper token counting to get last 8K tokens
                val allText = elements.joinToString(" ")
                val examplePipe = BedrockPipe().truncateModuleContext()
                val settings = examplePipe.getTruncationSettings()
                
                // Create temporary context window for truncation
                val tempContext = com.TTT.Context.ContextWindow()
                tempContext.contextElements.addAll(elements)
                tempContext.selectAndTruncateContext(
                    "",
                    8000,
                    0,
                    ContextWindowSettings.TruncateTop,
                    settings.countSubWordsInFirstWord,
                    settings.favorWholeWords,
                    settings.countOnlyFirstWordFound,
                    settings.splitForNonWordChar,
                    settings.alwaysSplitIfWholeWordExists,
                    settings.countSubWordsIfSplit,
                    settings.nonWordSplitCount
                )
                tempContext.contextElements.joinToString(" ")
            }
        }
        ContextSelectionStrategy.PostIdea -> {
            val ideaBank = ContextBank.getContextFromBank("idea")
            val ideaContent = ideaBank.contextElements.joinToString(" ")
            "$ideaContent $prompt"
        }
    }
}

/**
 * Safe input function that handles both single line and multiline input
 * Detects JSON/multiline content and prompts for completion
 * @return Complete input string
 */
@Deprecated("Use readEnhancedInput() instead")
fun safeReadInput(): String
{
    return readEnhancedInput()
}

/**
 * Select relevant lorebook keys based on context text.
 * @param contextText Text to scan for lorebook key matches
 * @param lorebookMap Available lorebook entries
 * @return Map of relevant lorebook keys and values
 */
fun selectLorebookKeys(contextText: String, lorebookMap: Map<String, String>): Map<String, String>
{
    val relevantKeys = mutableMapOf<String, String>()
    val lowerContextText = contextText.lowercase()
    
    for ((key, value) in lorebookMap) {
        if (lowerContextText.contains(key.lowercase())) {
            relevantKeys[key] = value
        }
    }
    
    return relevantKeys
}

/**
 * Main input parser for the interactive shell.
 * Handles slash commands and mode-based input routing.
 */
fun parseInput()
{
    // Read user input and split into command parameters
    val rawInput = try {
        readEnhancedInput(removeDelimiterAtEnd = true)
    } catch (e: Exception) {
        println("Input error: ${e.message}")
        return
    }
    val splitParams = rawInput.split(" ").toMutableList()
    var commandState = CommandState.Writer

    // Extract slash command if present and check for advanced mode
    var slashCommand = if(splitParams.isNotEmpty() && splitParams[0].startsWith("/")) {splitParams[0]} else {""}
    
    // Set advanced mode based on command prefix
    if (slashCommand.startsWith("//"))
    {
        Env.advancedMode = true
    }
    else if (slashCommand.startsWith("/"))
    {
        Env.advancedMode = false
    }

    // Process slash commands - these switch modes and execute pipelines
    if(slashCommand.isNotEmpty())
    {

        if(slashCommand == "//" || slashCommand == "/")
        {
            slashCommand = Env.lastCommand
        }

        // Remove slash prefix and extract remaining parameters
        val extractedSlashCommand = slashCommand.replace("/", "")
        splitParams.removeAt(0)
        val remainingText = splitParams.joinToString(" ")


        // Route commands to appropriate pipeline handlers
        when(extractedSlashCommand)
        {
            "write" -> {
                commandState = CommandState.Writer
                if (remainingText.isNotEmpty()) callWriterPipeline(remainingText)
            }
            "idea" -> {
                commandState = CommandState.Idea
                if (remainingText.isNotEmpty()) {
                    callIdeaPipeline(remainingText)
                } else {
                    manageIdeaPipeline()
                }
            }
            "chat" -> {
                commandState = CommandState.Chat
                if (remainingText.isNotEmpty()) callChatPipeline(remainingText)
            }
            "character" -> {
                commandState = CommandState.Character
                if (remainingText.isNotEmpty()) characterChatSubshell(remainingText)
                else characterChatSubshell()
            }
            "lorebook" -> {
                commandState = CommandState.Lorebook
                if (remainingText.isNotEmpty()) callLorebookPipeline(remainingText)
            }
            "summary" -> {
                commandState = CommandState.Summary
                if (remainingText.isNotEmpty()) callSummaryPipeline(remainingText)
            }
            "help" -> printHelp()
            "save" -> Env.saveContextToFile()
            "export" -> exportStory()
            "load" -> loadStory()
            "clear" -> clearStory()
            "settings" -> configureSettings()
            "llm-settings" -> manageLLMSettings()
            "lore" -> manageLorebook()
            "import-lorebook" -> {
                if (remainingText.isNotEmpty()) importLorebook(remainingText)
                else println("Usage: /import-lorebook <filename>")
            }
            "import-nai" -> {
                if (remainingText.isNotEmpty()) loadNaiStory(remainingText)
                else manageNaiImport()
            }
            "chapters" -> {
                commandState = CommandState.Writer
                manageChapters()
            }
            "tokens" -> {
                commandState = CommandState.Writer
                tokenCountingSubshell()
            }
            "clear-chat" -> clearChatHistory()
            "rewrite" -> {
                commandState = CommandState.Writer
                if (remainingText.isNotEmpty()) {
                    callChapterRewritePipeline(remainingText)
                } else {
                    manageChapterRewrite()
                }
            }
            "style" -> printCurrentStyle()
            "test" -> runTest()
            "exit" -> {
                println("Goodbye!")
                kotlin.system.exitProcess(0)
            }
            "guide" -> selectGuideMode()
            "author" -> selectAuthorMode()
            "pitch" -> callPitchSubShell()
            else -> println("Unknown command: $extractedSlashCommand. Type /help for available commands.")
        }

    }
    // Handle non-slash input based on current mode
    else if(rawInput.isNotEmpty())
    {
        when(Env.LoadedState)
        {
            CommandState.Writer -> callWriterPipeline(rawInput)
            CommandState.Idea -> callIdeaPipeline(rawInput)
            CommandState.Chat -> callChatPipeline(rawInput)
            CommandState.Character -> handleCharacterChatInput(rawInput)
            CommandState.Lorebook -> callLorebookPipeline(rawInput)
            CommandState.Summary -> callSummaryPipeline(rawInput)
        }
    }
    
    // Don't update global state if we're in a sub-shell
    if(slashCommand.isNotEmpty())
    {
        Env.LoadedState = commandState
    }

}


/**
 * Execute the writer pipeline for story generation.
 * Uses DeepSeek model with 107K token limit and smart context selection.
 * @param prompt User input for story generation
 */
fun callWriterPipeline(prompt: String)
{
    /**
     * Create a connector to allow the user to control the power, and cost of the writing. More powerful pipelines
     * may produce better results for a given task but will cost more in token usage.
     */
    val writerLevelConnector = Connector()
        .add("low", Env.writerPipeline)
        .add("med", Env.plusWriterPipe)

    val traceConfig = TraceConfig(detailLevel = TraceDetailLevel.VERBOSE)
    writerLevelConnector.enableTracing(traceConfig)

    var writingStrength = "" //Key for the pipeline connector.
    
    // Handle empty input: check for banked idea or use continue mode
    val finalInputPrompt = prompt.ifEmpty {
        val ideaBank = ContextBank.getContextFromBank("idea")

        if (ideaBank.contextElements.isNotEmpty())
        {
            Env.LoadedState = CommandState.Idea
            prompt // Will be processed as PostIdea strategy
        }
        else
        {
            "continue" // Default to continue mode
        }
    }

    // Determine context selection strategy
    val strategy = determineContextSelectionStrategy(finalInputPrompt, Env.LoadedState)
    val relevantContext = extractRelevantContext(strategy, finalInputPrompt)
    
    // Inject idea content into prompt if PostIdea strategy
    val finalPrompt = if (strategy == ContextSelectionStrategy.PostIdea) {
        val ideaBank = ContextBank.getContextFromBank("idea")
        val ideaContent = ideaBank.contextElements.joinToString(" ")
        ContextBank.clearBankedContext()
        if (finalInputPrompt.isEmpty()) {
            "Based on this idea: $ideaContent\n\nContinue the story by implementing the concepts from this idea. The chapter does not need to resolve within the output token limit."
        } else {
            "Based on this idea: $ideaContent\n\nImplement the following request by combining it with the concepts from the idea: $finalInputPrompt"
        }
    } else {
        finalInputPrompt
    }

    // Get context and configure token management for DeepSeek model
    val currentGlobalContext = ContextBank.getContextFromBank("main")
    val exampleBedrockPipe = BedrockPipe().truncateModuleContext()
    val tokenCountSettings = exampleBedrockPipe.getTruncationSettings()

    // Break into advanced context shell if "//" is the prompt or if advancedMode is true
    if (prompt == "//" || Env.advancedMode)
    {
        writerSubshell(finalPrompt, writerLevelConnector, currentGlobalContext, relevantContext, tokenCountSettings)
    }
    else
    {
        // Standard context selection for non-advanced mode
        currentGlobalContext.selectAndTruncateContext(
            relevantContext,
            107000,
            0,
            ContextWindowSettings.TruncateTop,
            tokenCountSettings.countSubWordsInFirstWord,
            tokenCountSettings.favorWholeWords,
            tokenCountSettings.countOnlyFirstWordFound,
            tokenCountSettings.splitForNonWordChar,
            tokenCountSettings.alwaysSplitIfWholeWordExists,
            tokenCountSettings.countSubWordsIfSplit,
            tokenCountSettings.nonWordSplitCount
        )
        
    }

    // Execute with global writing strength setting
    executeWriterPipeline(finalPrompt, globalWritingStrength, writerLevelConnector, currentGlobalContext, relevantContext, tokenCountSettings)
}


@kotlinx.serialization.Serializable
data class IdeaSettings(
    val chaptersLookback: Int = 8,
    val lorebookTokenBudget: Int = 3000,
    val forcedChapters: MutableList<Int> = mutableListOf()
)

var ideaSettings = IdeaSettings()

/**
 * Execute the idea generation pipeline for story brainstorming.
 * Uses DeepSeek model with reasoning capabilities for creative ideation.
 * @param prompt User request for idea generation, prompts for input if empty
 */
fun callIdeaPipeline(prompt: String)
{
    if (prompt.isEmpty())
    {
        manageIdeaPipeline()
        return
    }

    // Get context elements and apply chapter selection
    val currentGlobalContext = ContextBank.getContextFromBank("main")
    val allElements = currentGlobalContext.contextElements
    val selectedElements = mutableListOf<String>()
    
    // Add forced chapters first
    ideaSettings.forcedChapters.forEach { chapterIndex ->
        if (chapterIndex in 0 until allElements.size) {
            selectedElements.add(allElements[chapterIndex])
        }
    }
    
    // Add recent chapters based on lookback setting
    val startIndex = maxOf(0, allElements.size - ideaSettings.chaptersLookback)
    for (i in startIndex until allElements.size) {
        if (!ideaSettings.forcedChapters.contains(i)) {
            selectedElements.add(allElements[i])
        }
    }
    
    // Create context window with selected chapters
    val contextWindow = com.TTT.Context.ContextWindow()
    contextWindow.contextElements.addAll(selectedElements)
    contextWindow.loreBookKeys.putAll(currentGlobalContext.loreBookKeys)
    
    // Calculate available tokens (DeepSeek limit minus lorebook budget)
    val availableTokens = 107000 - ideaSettings.lorebookTokenBudget
    
    // Configure DeepSeek model for idea generation
    val deepSeekModelId = "deepseek.r1-v1:0"
    val exampleBedrockPipe = BedrockPipe().setModel(deepSeekModelId).truncateModuleContext()
    val tokenCountSettings = exampleBedrockPipe.getTruncationSettings()
    
    // Apply context truncation with lorebook token budget
    contextWindow.selectAndTruncateContext(
        prompt,
        availableTokens,
        0,
        ContextWindowSettings.TruncateTop,
        tokenCountSettings.countSubWordsInFirstWord,
        tokenCountSettings.favorWholeWords,
        tokenCountSettings.countOnlyFirstWordFound,
        tokenCountSettings.splitForNonWordChar,
        tokenCountSettings.alwaysSplitIfWholeWordExists,
        tokenCountSettings.countSubWordsIfSplit,
        tokenCountSettings.nonWordSplitCount,
    )
    
    // Set context and execute pipeline
    Env.ideaPipeline.context = contextWindow
    var result = MultimodalContent()
    result.addText(prompt)

    println("Thinking...")

    runBlocking {
        result = Env.ideaPipeline.execute(result)
    }

    // Display generated ideas or error message
    if(result.text.isNotEmpty())
    {
        println("\n\n\n" + result.text)
        // Store idea result in bankedContext for writer pipeline
        runBlocking {
            ContextBank.swapBankWithMutex("idea")
            val bankedContext = ContextBank.getBankedContextWindow()
            bankedContext.contextElements.clear()
            bankedContext.contextElements.add(result.text)
            ContextBank.emplaceWithMutex("idea", bankedContext)
        }
    }
    else
    {
        println("The model failed to return a result")
    }
}

/**
 * Interactive idea pipeline management system.
 * Provides sub-shell for configuring idea generation settings and forced chapters.
 */
fun manageIdeaPipeline()
{
    println("\n=== Idea Pipeline Management ===")
    showIdeaMenu()
    
    while (true)
    {
        print("idea> ")
        val input = readEnhancedInput(removeDelimiterAtEnd = true).trim()
        val parts = input.split(" ")
        val command = parts[0].lowercase()
        
        when (command) {
            "settings" -> configureIdeaSettings()
            "forced" -> manageForcedChapters(parts.drop(1))
            "status" -> showIdeaStatus()
            "generate" -> {
                if (parts.size > 1) {
                    val prompt = parts.drop(1).joinToString(" ")
                    callIdeaPipeline(prompt)
                } else {
                    println("Usage: generate <prompt>")
                }
            }
            "help" -> showIdeaMenu()
            "back", "exit" -> {
                Env.LoadedState = CommandState.Writer
                return
            }
            "" -> continue
            else -> {
                // Only call LLM if input is not a single valid command
                val validCommands = setOf("settings", "forced", "status", "generate", "help", "back", "exit")
                if (parts.size == 1 && validCommands.contains(command)) {
                    // Command was already handled above, don't call LLM
                    continue
                } else {
                    // Treat as idea generation prompt
                    callIdeaPipeline(input)
                }
            }
        }
    }
}

/**
 * Display idea pipeline management menu options.
 */
fun showIdeaMenu()
{
    println("""
        |Idea Pipeline Commands:
        |  settings              - Configure lookback chapters and lorebook budget
        |  forced add <index>    - Add chapter to forced context list
        |  forced remove <index> - Remove chapter from forced context list
        |  forced list           - Show forced chapters
        |  forced clear          - Clear all forced chapters
        |  status                - Show current settings and forced chapters
        |  generate <prompt>     - Generate ideas with current settings
        |  help                  - Show this menu
        |  back                  - Return to main shell
        |
        |Or enter any text to generate ideas directly.
    """.trimMargin())
}

/**
 * Configure idea pipeline settings.
 */
fun configureIdeaSettings()
{
    println("\n=== Idea Pipeline Settings ===")
    
    print("Chapters to look back (current: ${ideaSettings.chaptersLookback}): ")
    val lookback = readEnhancedInput().trim().toIntOrNull() ?: ideaSettings.chaptersLookback
    
    print("Lorebook token budget (current: ${ideaSettings.lorebookTokenBudget}): ")
    val budget = readEnhancedInput().trim().toIntOrNull() ?: ideaSettings.lorebookTokenBudget
    
    ideaSettings = ideaSettings.copy(
        chaptersLookback = lookback.coerceIn(1, 50),
        lorebookTokenBudget = budget.coerceIn(500, 10000)
    )
    
    println("Settings updated:")
    println("  Chapters lookback: ${ideaSettings.chaptersLookback}")
    println("  Lorebook budget: ${ideaSettings.lorebookTokenBudget} tokens")
}

/**
 * Manage forced chapters for idea context.
 * @param params Command parameters for forced chapter management
 */
fun manageForcedChapters(params: List<String>)
{
    val context = ContextBank.getContextFromBank("main")
    val totalChapters = context.contextElements.size
    
    when (params.firstOrNull()) {
        "add" -> {
            val index = params.getOrNull(1)?.toIntOrNull()?.minus(1)
            if (index == null) {
                println("Usage: forced add <chapter_number>")
                return
            }
            if (index !in 0 until totalChapters) {
                println("Chapter ${index + 1} does not exist (1-$totalChapters available)")
                return
            }
            if (!ideaSettings.forcedChapters.contains(index)) {
                ideaSettings.forcedChapters.add(index)
                println("Chapter ${index + 1} added to forced context")
            } else {
                println("Chapter ${index + 1} already in forced context")
            }
        }
        "remove" -> {
            val index = params.getOrNull(1)?.toIntOrNull()?.minus(1)
            if (index == null) {
                println("Usage: forced remove <chapter_number>")
                return
            }
            if (ideaSettings.forcedChapters.remove(index)) {
                println("Chapter ${index + 1} removed from forced context")
            } else {
                println("Chapter ${index + 1} not in forced context")
            }
        }
        "list" -> {
            if (ideaSettings.forcedChapters.isEmpty()) {
                println("No forced chapters configured")
            } else {
                println("Forced chapters: ${ideaSettings.forcedChapters.map { it + 1 }.sorted().joinToString(", ")}")
            }
        }
        "clear" -> {
            ideaSettings.forcedChapters.clear()
            println("All forced chapters cleared")
        }
        else -> {
            println("Usage: forced [add|remove|list|clear] [chapter_number]")
        }
    }
}

/**
 * Show current idea pipeline status and settings.
 */
fun showIdeaStatus()
{
    val context = ContextBank.getContextFromBank("main")
    val totalChapters = context.contextElements.size
    
    println("\n=== Idea Pipeline Status ===")
    println("Total chapters available: $totalChapters")
    println("Chapters lookback: ${ideaSettings.chaptersLookback}")
    println("Lorebook token budget: ${ideaSettings.lorebookTokenBudget}")
    println("Available context tokens: ${107000 - ideaSettings.lorebookTokenBudget}")
    
    if (ideaSettings.forcedChapters.isEmpty()) {
        println("Forced chapters: None")
    } else {
        println("Forced chapters: ${ideaSettings.forcedChapters.map { it + 1 }.sorted().joinToString(", ")}")
    }
    
    // Show which chapters would be selected
    val selectedChapters = mutableSetOf<Int>()
    selectedChapters.addAll(ideaSettings.forcedChapters)
    val startIndex = maxOf(0, totalChapters - ideaSettings.chaptersLookback)
    for (i in startIndex until totalChapters) {
        selectedChapters.add(i)
    }
    
    if (selectedChapters.isNotEmpty()) {
        println("Chapters that would be used: ${selectedChapters.map { it + 1 }.sorted().joinToString(", ")}")
    }
}

/**
 * Execute the chat/discussion pipeline for story Q&A.
 * Provides conversational interface for discussing story elements.
 * @param prompt User question about the story, prompts for input if empty
 */
fun callChatPipeline(prompt: String)
{
    var request = prompt

    // Request user input if no question provided
    if (prompt.isEmpty())
    {
        println("Enter your question about the story.")
        request = readEnhancedInput()
    }
    
    // Skip execution if still no prompt after input request
    if (request.isEmpty()) return

    // Configure DeepSeek for conversational responses
    val deepSeekModelId = "deepseek.r1-v1:0"
    val currentGlobalContext = ContextBank.getContextFromBank("main")
    val exampleBedrockPipe = BedrockPipe().setModel(deepSeekModelId).truncateModuleContext()
    val tokenCountSettings = exampleBedrockPipe.getTruncationSettings()
    
    // Create separate context for chat to prevent bleeding into main context
    val chatContextWindow = com.TTT.Context.ContextWindow()
    chatContextWindow.contextElements.addAll(currentGlobalContext.contextElements)
    chatContextWindow.loreBookKeys.putAll(currentGlobalContext.loreBookKeys)
    
    val chatContext = ContextBank.getContextFromBank("chat")
    chatContextWindow.merge(chatContext)

    // Execute discussion pipeline with isolated context
    var result = MultimodalContent()
    result.addText(request)

    Env.discussionPipeline.context = chatContextWindow
    Env.discussionPipeline.enableTracing()

    println("Thinking...")

    runBlocking {
        result = Env.discussionPipeline.execute(result)
    }

    // Output chat response without updating main context
    if(result.text.isNotEmpty())
    {
        println("\n\n\n" + result.text)
    }
    else
    {
        println("The model failed to return a result")
    }

    val trace = Env.discussionPipeline.getTraceReport(TraceFormat.HTML)
    writeStringToFile("${getHomeFolder()}/TPipeWriter/Trace.html", trace)
}

/**
 * Execute the lorebook management pipeline.
 * Updates story lorebook entries based on user instructions or key specifications.
 * @param prompt Lorebook keys or management instructions, prompts for input if empty
 */
fun callLorebookPipeline(prompt: String)
{
    var request = prompt

    // Get lorebook management instructions from user if needed
    if (prompt.isEmpty())
    {
        println("Enter lorebook keys to update or instructions for lorebook management.")
        request = readEnhancedInput()
    }

    // Setup DeepSeek for lorebook processing
    val deepSeekModelId = "deepseek.r1-v1:0"
    val currentGlobalContext = ContextBank.getContextFromBank("main")
    val exampleBedrockPipe = BedrockPipe().setModel(deepSeekModelId).truncateModuleContext()
    val tokenCountSettings = exampleBedrockPipe.getTruncationSettings()
    val tokenCount = Dictionary.countTokens(currentGlobalContext.contextElements.toString())

    currentGlobalContext.selectAndTruncateContext(
        request,
        107000,
        0,
        ContextWindowSettings.TruncateTop,
        tokenCountSettings.countSubWordsInFirstWord,
        tokenCountSettings.favorWholeWords,
        tokenCountSettings.countOnlyFirstWordFound,
        tokenCountSettings.splitForNonWordChar,
        tokenCountSettings.alwaysSplitIfWholeWordExists,
        tokenCountSettings.countSubWordsIfSplit,
        tokenCountSettings.nonWordSplitCount,
    )

    Env.lorebookPipeline.context = currentGlobalContext

    // Execute lorebook update pipeline
    var result = MultimodalContent()
    result.addText(request)

    println("thinking...")

    Env.lorebookPipeline.enableTracing(TraceConfig(detailLevel = TraceDetailLevel.DEBUG))

    runBlocking {
        result = Env.lorebookPipeline.execute(result)
    }

    val trace = Env.lorebookPipeline.getTraceReport(TraceFormat.HTML)
    writeStringToFile("${getHomeFolder()}/TPipeWriter/trace.html", trace)

    // Display lorebook update results
    if(result.text.isNotEmpty())
    {
        println("\n\n" + result.text)
    }

    else
    {
        println("The model failed to return a result")
    }
}

/**
 * Enhanced summary pipeline with element selection capabilities.
 * Supports range selection, last element, sequential processing, and custom prompts.
 * @param prompt Command options: "last", "all", "1-3", "5", or custom text
 */
fun callSummaryPipeline(prompt: String)
{
    // Get main context elements
    val currentGlobalContext = ContextBank.getContextFromBank("main")
    val elements = currentGlobalContext.contextElements
    
    // Check if context has any elements to summarize
    if (elements.isEmpty())
    {
        println("No context elements to summarize")
        return
    }
    
    // Parse command and route to appropriate handler
    when {
        prompt.isEmpty() -> showSummaryOptions(elements.size)
        prompt == "last" -> summarizeElements(elements, elements.size - 1, elements.size - 1)
        prompt == "all" -> summarizeAllSequentially(elements)
        prompt.contains("-") -> {
            // Parse range format (e.g., "1-3")
            val range = prompt.split("-")
            if (range.size == 2) {
                val start = range[0].toIntOrNull()?.minus(1) ?: 0
                val end = range[1].toIntOrNull()?.minus(1) ?: elements.size - 1
                summarizeElements(elements, start, end)
            } else {
                println("Invalid range format. Use: start-end (e.g., 1-3)")
            }
        }
        prompt.toIntOrNull() != null -> {
            // Single element by index
            val index = prompt.toInt() - 1
            summarizeElements(elements, index, index)
        }
        else -> summarizeWithCustomPrompt(prompt)
    }
}

/**
 * Display available summary options and current element count.
 * @param elementCount Total number of context elements available
 */
fun showSummaryOptions(elementCount: Int)
{
    println("""
        |Summary options:
        |  last        - Summarize the last element
        |  all         - Summarize all elements sequentially
        |  1-3         - Summarize elements 1 through 3
        |  5           - Summarize element 5 only
        |  custom text - Summarize with custom prompt
        |
        |Total elements available: $elementCount
    """.trimMargin())
}

/**
 * Summarize a specific range of context elements.
 * @param elements List of context elements
 * @param startIndex Starting index (0-based)
 * @param endIndex Ending index (0-based, inclusive)
 */
fun summarizeElements(elements: MutableList<String>, startIndex: Int, endIndex: Int)
{
    // Ensure indices are within valid bounds
    val validStart = startIndex.coerceIn(0, elements.size - 1)
    val validEnd = endIndex.coerceIn(validStart, elements.size - 1)
    
    // Extract selected elements and combine
    val selectedElements = elements.subList(validStart, validEnd + 1)
    val combinedText = selectedElements.joinToString("\n\n")
    
    executeSummary(combinedText)
}

/**
 * Process all elements sequentially, summarizing each individually.
 * @param elements List of context elements to process
 */
fun summarizeAllSequentially(elements: MutableList<String>)
{
    println("Summarizing ${elements.size} elements sequentially...")
    
    // Process each element with progress indication
    elements.forEachIndexed { index, element ->
        println("\n--- Summarizing element ${index + 1} ---")
        executeSummary(element)
    }
}

/**
 * Summarize using custom user-provided text.
 * @param prompt Custom text to summarize
 */
fun summarizeWithCustomPrompt(prompt: String)
{
    executeSummary(prompt)
}

/**
 * Execute the actual summarization using the Nova model pipeline.
 * @param text Text content to summarize
 */
fun executeSummary(text: String)
{
    // Configure Nova model for summarization (280K token limit)
    val novaModelId = "amazon.nova-pro-v1:0"
    val currentGlobalContext = ContextBank.getContextFromBank("main")
    val exampleBedrockPipe = BedrockPipe().setModel(novaModelId).truncateModuleContext()
    val tokenCountSettings = exampleBedrockPipe.getTruncationSettings()
    val tokenCount = Dictionary.countTokens(text)

    // Truncate if text exceeds Nova's context window
    if (tokenCount > 280000)
    {
        currentGlobalContext.selectAndTruncateContext(
            text,
            280000,
            0,
            ContextWindowSettings.TruncateTop,
            tokenCountSettings.countSubWordsInFirstWord,
            tokenCountSettings.favorWholeWords,
            tokenCountSettings.countOnlyFirstWordFound,
            tokenCountSettings.splitForNonWordChar,
            tokenCountSettings.alwaysSplitIfWholeWordExists,
            tokenCountSettings.countSubWordsIfSplit,
            tokenCountSettings.nonWordSplitCount,
        )
    }

    // Execute summarization pipeline
    var result = MultimodalContent()
    result.addText(text)

    runBlocking {
        result = Env.summarizerPipeline.execute(result)
    }

    // Output result or error message
    if(result.text.isNotEmpty())
    {
        println(result.text)
    }
    else
    {
        println("The model failed to return a result")
    }
}

/**
 * Load story content and lorebook from exported files.
 * Prompts user for filename and loads into main context bank.
 */
fun loadStory()
{
    // Request filename from user
    print("Enter filename (without extension): ")
    val filename = readEnhancedInput().trim()
    
    // Validate filename input
    if (filename.isEmpty())
    {
        println("Invalid filename")
        return
    }
    
    try
    {
        val homeDir = com.TTT.Util.getHomeFolder()
        val tpipeDir = java.io.File(homeDir, "TPipeWriter")
        
        // Try to load story data with chapter metadata first
        val storyDataFile = java.io.File(tpipeDir, "$filename-story.json")
        val contextElements: MutableList<String>
        
        if (storyDataFile.exists()) {
            // Load from new format with chapter metadata
            val storyDataJson = storyDataFile.readText()
            val storyData = com.TTT.Util.deserialize<Chapter.StoryData>(storyDataJson)
            if (storyData != null) {
                contextElements = storyData.contextElements.toMutableList()
                Chapter.GlobalChapterManager.loadMetadata(storyData.chapterMetadata)
                println("Chapter metadata loaded (${storyData.chapterMetadata.size} chapters)")
            } else {
                println("Failed to parse story data file")
                return
            }
        } else {
            // Fallback to old format
            val storyFile = java.io.File(tpipeDir, "$filename.txt")
            if (!storyFile.exists()) {
                println("Story file not found: ${storyFile.absolutePath}")
                return
            }
            
            val storyContent = storyFile.readText()
            contextElements = storyContent.split("***").filter { it.isNotEmpty() }.toMutableList()
            Chapter.GlobalChapterManager.clearAllMetadata()
            println("Loaded from legacy format (no chapter metadata)")
        }
        
        // Load lorebook
        val lorebookFile = java.io.File(tpipeDir, "$filename-lorebook.json")
        val lorebook = if (lorebookFile.exists()) {
            com.TTT.Util.deserialize<Map<String, com.TTT.Context.LoreBook>>(lorebookFile.readText()) ?: mutableMapOf()
        } else {
            mutableMapOf<String, com.TTT.Context.LoreBook>()
        }
        
        // Load settings if available
        val settingsFile = java.io.File(tpipeDir, "$filename-settings.json")
        val loadedSettings = if (settingsFile.exists()) {
            com.TTT.Util.deserialize<TPipeSettings>(settingsFile.readText()) ?: TPipeSettings()
        } else {
            TPipeSettings()
        }
        
        // Update main context bank
        runBlocking {
            val context = ContextBank.getContextFromBank("main")
            context.contextElements.clear()
            context.contextElements.addAll(contextElements)
            context.loreBookKeys.clear()
            context.loreBookKeys.putAll(lorebook)
            ContextBank.emplaceWithMutex("main", context)
        }
        
        // Apply loaded settings and reinitialize system
        if (settingsFile.exists()) {
            saveSettings(loadedSettings)
            Env.init(loadedSettings.writingStyle, loadedSettings.temperature, loadedSettings.topP, loadedSettings.maxTokens, loadedSettings.useAutoLorebook)
            
            // Load guides into runtime variables and context banks
            Env.activeChapterGuide = loadedSettings.chapterGuide
            Env.activeStoryGuide = loadedSettings.storyGuide
            
            // Store guides in context banks
            if (loadedSettings.chapterGuide.isNotEmpty()) {
                val chapterGuideWindow = com.TTT.Context.ContextWindow()
                chapterGuideWindow.contextElements.add(loadedSettings.chapterGuide)
                ContextBank.emplace("chapter guide", chapterGuideWindow)
            }
            
            if (loadedSettings.storyGuide.isNotEmpty()) {
                val storyGuideWindow = com.TTT.Context.ContextWindow()
                storyGuideWindow.contextElements.add(loadedSettings.storyGuide)
                ContextBank.emplace("story guide", storyGuideWindow)
            }
        }
        
        println("Story loaded successfully (${contextElements.size} chapters)")
        if (lorebookFile.exists()) {
            println("Lorebook loaded from ${lorebookFile.absolutePath}")
        }
        if (settingsFile.exists()) {
            println("Settings loaded from ${settingsFile.absolutePath}")
        }
    }
    catch (e: Exception)
    {
        println("Load failed: ${e.message}")
    }
}

/**
 * Clear all story content and lorebook data from main context bank.
 */
fun clearStory()
{
    try
    {
        runBlocking {
            val context = ContextBank.getContextFromBank("main")
            context.contextElements.clear()
            context.loreBookKeys.clear()
            ContextBank.emplaceWithMutex("main", context)
        }
        Chapter.GlobalChapterManager.clearAllMetadata()
        println("Story, lorebook, and chapter metadata cleared successfully")
    }
    catch (e: Exception)
    {
        println("Clear failed: ${e.message}")
    }
}

@kotlinx.serialization.Serializable
data class TPipeSettings(
    val writingStyle: String = "",
    val temperature: Double = 1.0,
    val topP: Double = 0.9,
    val maxTokens: Int = 5000,
    val useAutoLorebook: Boolean = true,
    var authorGuide: String = "",
    var competingAuthorGuide: String = "",
    var chapterGuide: String = "",
    var storyGuide: String = ""
)

/**
 * Configure general system settings (non-LLM specific).
 */
fun configureSettings()
{
    val currentSettings = loadSettings()
    
    println("\n=== TPipeWriter General Settings ===")
    
    print("\n\nWriting style (current: ${currentSettings.writingStyle.ifEmpty { "empty" }}): ")
    val writingStyle = readEnhancedInput(removeDelimiterAtEnd = true).trim().ifEmpty { currentSettings.writingStyle }
    
    print("\n\nMax tokens (current: ${currentSettings.maxTokens}): ")
    val maxTokens = readEnhancedInput().trim().toIntOrNull() ?: currentSettings.maxTokens
    
    print("\n\nUse automatic lorebook updates? (y/n, current: ${if (currentSettings.useAutoLorebook) "y" else "n"}): ")
    val useAutoLorebook = readEnhancedInput().trim().lowercase().let { 
        when (it) {
            "y", "yes" -> true
            "n", "no" -> false
            else -> currentSettings.useAutoLorebook
        }
    }
    
    val newSettings = TPipeSettings(writingStyle, currentSettings.temperature, currentSettings.topP, maxTokens, useAutoLorebook)
    
    try
    {
        saveSettings(newSettings)
        println("\nReinitializing system with new settings...")
        Env.init(writingStyle, currentSettings.temperature, currentSettings.topP, maxTokens, useAutoLorebook)
        println("Settings updated successfully!")
        println("\nFor LLM model settings (temperature, topP, models), use /llm-settings")
    }
    catch (e: Exception)
    {
        println("Settings update failed: ${e.message}")
    }
}

/**
 * Save TPipeWriter settings to the user's configuration directory.
 * Creates the .TPipeWriter directory if it doesn't exist and serializes settings to JSON.
 * 
 * @param settings The TPipeSettings object to save
 */
fun saveSettings(settings: TPipeSettings)
{
    val configDir = File(com.TTT.Util.getHomeFolder(), ".TPipeWriter")
    configDir.mkdirs()
    val settingsFile = File(configDir, "settings.json")
    settingsFile.writeText(com.TTT.Util.serialize(settings))
}

/**
 * Load TPipeWriter settings from the user's configuration directory.
 * Returns default settings if the configuration file doesn't exist.
 * 
 * @return TPipeSettings object with loaded or default configuration
 */
fun loadSettings(): TPipeSettings
{
    val configDir = File(com.TTT.Util.getHomeFolder(), ".TPipeWriter")
    val settingsFile = File(configDir, "settings.json")
    return if (settingsFile.exists()) {
        com.TTT.Util.deserialize<TPipeSettings>(settingsFile.readText()) ?: TPipeSettings()
    } else {
        TPipeSettings()
    }
}

/**
 * Run test function for development and debugging.
 */
fun runTest()
{
    println("Input your prompt")
    val prompt = readEnhancedInput()

    Env.stylePipeline.enableTracing()

    runBlocking {
        val result = Env.stylePipeline.execute(prompt)

        if(result.isEmpty())
        {
            println("The model failed to return a result")

            val trace = Env.stylePipeline.getTraceReport(format = TraceFormat.HTML)
            writeStringToFile("${getHomeFolder()}/TPipeWriter/trace.html", trace)
        }

        else
        {
            println("\n\n\n" + result)
        }
    }

}

/**
 * Clear chat history from the chat context bank.
 */
fun clearChatHistory()
{
    try {
        runBlocking {
            val chatContext = ContextBank.getContextFromBank("chat")
            chatContext.contextElements.clear()
            ContextBank.emplaceWithMutex("chat", chatContext)
        }
        println("Chat history cleared successfully")
    } catch (e: Exception) {
        println("Failed to clear chat history: ${e.message}")
    }
}

/**
 * Display help information for all available shell commands.
 * Shows command syntax and current mode status.
 */
fun printHelp()
{
    println("""
        |TPipeWriter Interactive Shell Commands:
        |
        |/write             - Generate story content using the writer pipeline
        |/idea              - Enter idea pipeline sub-shell or generate ideas with prompt
        |/chat              - Chat about the story using the discussion pipeline
        |/character         - Chat with a selected character prompt (uses converse history)
        |/lorebook          - Update lorebook entries using the lorebook pipeline
        |/summary           - Summarize content (last/all/1-3/5/custom text)
        |/save              - Save current context to file
        |/export            - Export story content to text file
        |/load              - Load story content from exported files
        |/clear             - Clear all story content and lorebook data
        |/clear-chat        - Clear chat history only
        |/test              - Run test function for development
        |/settings          - Configure general system settings
        |/llm-settings      - Configure LLM model settings for all pipelines
        |/lore              - Manage lorebook entries
        |/import-lorebook   - Import lorebook from JSON file
        |/import-nai        - Import Novel AI story from JSON file
        |/chapters          - Enter chapter management sub-shell
        |/tokens            - Enter token counting sub-shell for analysis
        |/rewrite           - Rewrite existing chapters with lore and style fixes
        |/style             - Show current writing style
        |/guide             - Open the guide settings menu.
        |/help              - Show this help message
        |/exit              - Exit the application
        |
        |You can also enter commands without the slash prefix when in a specific mode.
        |Current mode: ${Env.LoadedState}
    """.trimMargin())
}

/**
 * Export current story context to a text file.
 * Prompts user for filename and saves all context elements.
 */
fun exportStory()
{
    // Get story context from main bank
    val context = ContextBank.getContextFromBank("main")
    var storyContent = ""

    for(chapter in context.contextElements)
    {
        storyContent += "${chapter}***\n\n"
    }
    
    // Request filename from user
    print("Enter filename (without extension): ")
    val filename = readEnhancedInput().trim()
    
    // Validate filename input
    if (filename.isEmpty())
    {
        println("Invalid filename")
        return
    }
    
    // Write story content and lorebook to files in TPipeWriter directory
    try
    {
        val homeDir = com.TTT.Util.getHomeFolder()
        val tpipeDir = java.io.File(homeDir, "TPipeWriter")
        if (!tpipeDir.exists()) tpipeDir.mkdirs()
        
        // Export story content
        val storyFile = java.io.File(tpipeDir, "$filename.txt")
        storyFile.writeText(storyContent)
        
        // Export story data with chapter metadata
        val storyData = Chapter.GlobalChapterManager.exportStoryData(context.contextElements)
        val storyDataJson = com.TTT.Util.serialize(storyData)
        val storyDataFile = java.io.File(tpipeDir, "$filename-story.json")
        storyDataFile.writeText(storyDataJson)
        
        // Export lorebook as JSON
        val lorebookJson = com.TTT.Util.serialize(context.loreBookKeys)
        val lorebookFile = java.io.File(tpipeDir, "$filename-lorebook.json")
        lorebookFile.writeText(lorebookJson)
        
        // Export settings as JSON with current guides
        val currentSettings = loadSettings().copy(
            chapterGuide = Env.activeChapterGuide,
            storyGuide = Env.activeStoryGuide
        )
        val settingsJson = com.TTT.Util.serialize(currentSettings)
        val settingsFile = java.io.File(tpipeDir, "$filename-settings.json")
        settingsFile.writeText(settingsJson)
        
        println("Story exported to ${storyFile.absolutePath}")
        println("Story data with chapters exported to ${storyDataFile.absolutePath}")
        println("Lorebook exported to ${lorebookFile.absolutePath}")
        println("Settings exported to ${settingsFile.absolutePath}")
    }
    catch (e: Exception)
    {
        println("Export failed: ${e.message}")
    }
}

/**
 * Interactive lorebook management system.
 * Provides commands for viewing, editing, and managing lorebook entries.
 */
fun manageLorebook()
{
    println("\n=== Lorebook Management ===")
    showLorebookMenu()
    
    while (true)
    {
        print("lore> ")
        val input = readEnhancedInput().trim()
        val parts = input.split(" ")
        val command = parts[0].lowercase()
        
        when (command) {
            "list" -> listLorebookKeys()
            "view" -> if (parts.size > 1) viewLorebookKey(parts.drop(1).joinToString(" ")) else println("Usage: view <keyname>")
            "edit" -> if (parts.size > 1) editLorebookKey(parts.drop(1).joinToString(" ")) else println("Usage: edit <keyname>")
            "weight" -> if (parts.size > 2) {
                val keyName = parts.drop(1).dropLast(1).joinToString(" ")
                val weightValue = parts.last().toIntOrNull() ?: 1
                adjustLorebookWeight(keyName, weightValue)
            } else println("Usage: weight <keyname> <value>")
            "search" -> if (parts.size > 1) searchLorebookKeys(parts.drop(1).joinToString(" ")) else println("Usage: search <term>")
            "add" -> if (parts.size > 1) addLorebookKey(parts.drop(1).joinToString(" ")) else println("Usage: add <keyname>")
            "delete" -> if (parts.size > 1) deleteLorebookKey(parts.drop(1).joinToString(" ")) else println("Usage: delete <keyname>")
            "link" -> if (parts.size > 2) linkLorebookKeys(parts[1], parts.drop(2)) else println("Usage: link <keyname> <linked_key1> [linked_key2...]")
            "unlink" -> if (parts.size > 2) unlinkLorebookKeys(parts[1], parts.drop(2)) else println("Usage: unlink <keyname> <linked_key1> [linked_key2...]")
            "alias" -> if (parts.size > 2) addLorebookAlias(parts[1], parts.drop(2)) else println("Usage: alias <keyname> <alias1> [alias2...]")
            "unalias" -> if (parts.size > 2) removeLorebookAlias(parts[1], parts.drop(2)) else println("Usage: unalias <keyname> <alias1> [alias2...]")
            "help" -> showLorebookMenu()
            "back", "exit" -> return
            "" -> continue
            else -> println("Unknown command: $command. Type 'help' for available commands.")
        }
    }
}

/**
 * Display lorebook management menu options.
 */
fun showLorebookMenu()
{
    println("""
        |Lorebook Commands:
        |  list                         - Show all lorebook keys
        |  view <keyname>              - Display key content
        |  edit <keyname>              - Edit key content
        |  weight <keyname> <val>      - Set key weight (0-100)
        |  search <term>               - Search keys and content
        |  add <keyname>               - Create new key
        |  delete <keyname>            - Remove key
        |  link <key> <linked_keys...> - Link keys together
        |  unlink <key> <keys...>      - Remove key links
        |  alias <key> <aliases...>    - Add aliases to key
        |  unalias <key> <aliases...>  - Remove aliases from key
        |  help                        - Show this menu
        |  back                        - Return to main shell
    """.trimMargin())
}

/**
 * List all lorebook keys with their weights.
 */
fun listLorebookKeys()
{
    val context = ContextBank.getContextFromBank("main")
    val lorebook = context.loreBookKeys
    
    if (lorebook.isEmpty()) {
        println("No lorebook entries found.")
        return
    }
    
    println("\n=== Lorebook Keys (${lorebook.size} total) ===")
    println("Key Name".padEnd(50) + "Weight")
    println("-".repeat(60))
    
    lorebook.forEach { (key, loreEntry) ->
        val displayKey = key.padEnd(50)
        println("$displayKey${loreEntry.weight}")
    }
}

/**
 * View the content of a specific lorebook key.
 * @param keyName Name of the lorebook key to view
 */
fun viewLorebookKey(keyName: String)
{
    val context = ContextBank.getContextFromBank("main")
    val loreEntry = context.loreBookKeys[keyName]
    
    if (loreEntry == null) {
        println("Key '$keyName' not found.")
        return
    }
    
    println("\n=== Lorebook Entry: $keyName ===")
    println("Weight: ${loreEntry.weight}")
    println("Content:")
    println("-".repeat(40))
    println(loreEntry.value)
    println("-".repeat(40))
}

/**
 * Edit the content of a lorebook key.
 * @param keyName Name of the lorebook key to edit
 */
fun editLorebookKey(keyName: String)
{
    val context = ContextBank.getContextFromBank("main")
    val loreEntry = context.loreBookKeys[keyName]
    
    if (loreEntry == null) {
        println("Key '$keyName' not found.")
        return
    }
    
    println("\n=== Editing: $keyName ===")
    println("Current content:")
    println(loreEntry.value)
    println("\nEnter new content (type 'SAVE' on a new line to save, 'CANCEL' to cancel):")
    
    val result = readEnhancedInput("SAVE, CANCEL")
    val lastLine = result.split("\n").lastOrNull()?.trim()?.uppercase() ?: ""
    
    if (lastLine == "CANCEL") {
        println("Edit cancelled.")
        return
    }
    
    loreEntry.value = stripMultiLineDelimiter(result).trim()
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
    println("Key '$keyName' updated successfully.")
}

/**
 * Adjust the weight of a lorebook key.
 * @param keyName Name of the lorebook key
 * @param weight New weight value (0.0 to 1.0)
 */
fun adjustLorebookWeight(keyName: String, weight: Int)
{
    if (weight < 0 || weight > 100) {
        println("Weight must be between 0 and 100")
        return
    }
    
    val context = ContextBank.getContextFromBank("main")
    val loreEntry = context.loreBookKeys[keyName]
    
    if (loreEntry == null) {
        println("Key '$keyName' not found.")
        return
    }
    
    val oldWeight = loreEntry.weight
    loreEntry.weight = weight.toInt()
    
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
    
    println("Weight for '$keyName' changed from $oldWeight to $weight")
}

/**
 * Search lorebook keys and content.
 * @param searchTerm Term to search for
 */
fun searchLorebookKeys(searchTerm: String)
{
    val context = ContextBank.getContextFromBank("main")
    val lorebook = context.loreBookKeys
    val results = mutableListOf<String>()
    
    lorebook.forEach { (key, loreEntry) ->
        if (key.contains(searchTerm, ignoreCase = true) || 
            loreEntry.value.contains(searchTerm, ignoreCase = true)) {
            results.add(key)
        }
    }
    
    if (results.isEmpty()) {
        println("No matches found for '$searchTerm'")
    } else {
        println("\n=== Search Results for '$searchTerm' (${results.size} found) ===")
        results.forEach { key ->
            val weight = lorebook[key]?.weight ?: 1
            println("$key (weight: $weight)")
        }
    }
}

/**
 * Add a new lorebook key.
 * @param keyName Name of the new key
 */
fun addLorebookKey(keyName: String)
{
    val context = ContextBank.getContextFromBank("main")
    
    if (context.loreBookKeys.containsKey(keyName)) {
        println("Key '$keyName' already exists.")
        return
    }
    
    println("Enter content for new key '$keyName' (type 'SAVE' on a new line to save, 'CANCEL' to cancel):")
    
    val result = readEnhancedInput("SAVE, CANCEL")
    val lastLine = result.split("\n").lastOrNull()?.trim()?.uppercase() ?: ""
    
    if (lastLine == "CANCEL") {
        println("Creation cancelled.")
        return
    }
    
    val newLoreEntry = com.TTT.Context.LoreBook()
    newLoreEntry.key = keyName
    newLoreEntry.value = stripMultiLineDelimiter(result).trim()
    newLoreEntry.weight = 1
    
    context.addLoreBookEntry(newLoreEntry.key, newLoreEntry.value)
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
    println("Key '$keyName' created successfully.")
}

/**
 * Delete a lorebook key.
 * @param keyName Name of the key to delete
 */
fun deleteLorebookKey(keyName: String)
{
    val context = ContextBank.getContextFromBank("main")
    
    if (!context.loreBookKeys.containsKey(keyName)) {
        println("Key '$keyName' not found.")
        return
    }
    
    print("Are you sure you want to delete '$keyName'? (y/N): ")
    val confirmation = readEnhancedInput().lowercase()
    
    if (confirmation == "y" || confirmation == "yes") {
        context.loreBookKeys.remove(keyName)
        runBlocking {
            ContextBank.emplaceWithMutex("main", context)
        }
        println("Key '$keyName' deleted successfully.")
    } else {
        println("Deletion cancelled.")
    }
}

/**
 * Interactive chapter management system.
 * Provides sub-shell for managing story chapters.
 */
/**
 * Save the context back to the context bank after chapter modifications
 */
fun saveChapterContext(context: com.TTT.Context.ContextWindow)
{
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
}

/**
 * Interactive chapter management system.
 * Provides sub-shell for managing story chapters with commands for listing, editing,
 * searching, and organizing chapter content.
 */
fun manageChapters()
{
    println("\n=== Chapter Management ===")
    printChapterHelp()
    
    // Get context once and reuse the same ChapterManager instance
    val context = ContextBank.getContextFromBank("main")
    val chapterManager = Chapter.ChapterManager(context)
    
    while (true)
    {
        print("chapters> ")
        val input = readEnhancedInput().trim()
        val parts = input.split(" ")
        val command = parts[0].lowercase()
        
        when (command) {
            "list" -> displayChapterList(chapterManager)
            "search" -> if (parts.size > 1) handleChapterSearch(chapterManager, parts.drop(1)) else println("Usage: search <query>")
            "show" -> showChapter(chapterManager, parts.getOrNull(1)?.toIntOrNull())
            "edit" -> editChapter(chapterManager, parts.getOrNull(1)?.toIntOrNull(), context)
            "delete" -> deleteChapter(chapterManager, parts.getOrNull(1)?.toIntOrNull(), context)
            "insert" -> insertChapter(chapterManager, parts.getOrNull(1)?.toIntOrNull(), context)
            "add" -> addNewChapter(chapterManager, context)
            "move" -> moveChapter(chapterManager, parts, context)
            "title" -> setChapterTitle(chapterManager, parts)
            "stats" -> showChapterStats(chapterManager)
            "export" -> exportChapter(chapterManager, parts)
            "help" -> printChapterHelp()
            "back", "exit" -> return
            "" -> continue
            else -> println("Unknown command: $command. Type 'help' for available commands.")
        }
    }
}

/**
 * Display a formatted list of all chapters with their metadata.
 * Shows chapter index, title, word count, last modified date, and content preview.
 * 
 * @param chapterManager The ChapterManager instance containing chapter data
 */
fun displayChapterList(chapterManager: Chapter.ChapterManager)
{
    // Get all chapters from the manager
    val chapters = chapterManager.listChapters()
    if (chapters.isEmpty()) {
        println("No chapters found.")
        return
    }
    
    // Get overall statistics for the header
    val stats = chapterManager.getChapterStats()
    println("\nChapters (${stats.totalChapters} total, ${stats.totalWords} words):")
    
    // Display each chapter with formatted information
    chapters.forEach { chapter ->
        // Format the chapter index with consistent padding
        val indexStr = (chapter.index + 1).toString().padStart(3)
        
        // Truncate long titles to fit display width
        val titlePreview = if (chapter.title.length > 50) chapter.title.take(47) + "..." else chapter.title
        
        // Format word count and modification date
        val wordsStr = chapter.wordCount.toString()
        val modifiedStr = chapter.lastModified.ifEmpty { "Never" }
        
        // Print chapter header with metadata
        println("[$indexStr] $titlePreview ($wordsStr words) - $modifiedStr")
        
        // Print content preview with indentation
        println("      \"${chapter.preview}\"")
        println()
    }
}

/**
 * Handle chapter search functionality with query validation and result display.
 * Searches through chapter content and displays matches with context.
 * 
 * @param chapterManager The ChapterManager instance to search through
 * @param params List of search parameters (query terms)
 */
fun handleChapterSearch(chapterManager: Chapter.ChapterManager, params: List<String>)
{
    // Validate that search parameters were provided
    if (params.isEmpty()) {
        println("Usage: /chapters search <query>")
        return
    }
    
    // Combine all parameters into a single search query
    val query = params.joinToString(" ")
    
    // Execute the search through the chapter manager
    val results = chapterManager.searchChapters(query)
    
    // Handle case where no matches were found
    if (results.isEmpty()) {
        println("No matches found for '$query'")
        return
    }
    
    // Display search results with context
    println("\nSearch results for '$query' (${results.size} matches):")
    results.forEach { result ->
        // Show chapter number (convert from 0-based to 1-based indexing)
        println("\nChapter ${result.chapterIndex + 1}:")
        
        // Display the match with surrounding context for better understanding
        println("  ...${result.contextBefore}${result.matchText}${result.contextAfter}...")
    }
}

/**
 * Display the full content of a specific chapter with metadata.
 * Shows chapter title, word count, last modified date, and complete content.
 * 
 * @param chapterManager The ChapterManager instance containing chapter data
 * @param index The 1-based chapter index to display (null triggers usage message)
 */
fun showChapter(chapterManager: Chapter.ChapterManager, index: Int?)
{
    // Validate that an index was provided
    if (index == null) {
        println("Usage: /chapters show <index>")
        return
    }
    
    // Convert from 1-based user input to 0-based internal indexing
    val adjustedIndex = index - 1
    
    // Retrieve the chapter content
    val chapter = chapterManager.getChapter(adjustedIndex)
    if (chapter == null) {
        println("Chapter $index not found.")
        return
    }
    
    // Get chapter metadata for display
    val chapters = chapterManager.listChapters()
    val chapterInfo = chapters.find { it.index == adjustedIndex }
    
    // Display chapter header with metadata
    println("\n=== Chapter $index: ${chapterInfo?.title ?: "Untitled"} ===")
    println("Word count: ${chapterInfo?.wordCount ?: 0}")
    println("Last modified: ${chapterInfo?.lastModified ?: "Never"}")
    
    // Display chapter content with visual separators
    println("─".repeat(64))
    println(chapter)
    println("─".repeat(64))
}

/**
 * Edit a specific chapter with interactive input handling.
 * Supports both single chapter editing and multi-chapter insertion when content contains delimiters.
 * 
 * @param chapterManager The ChapterManager instance to modify
 * @param index The 1-based chapter index to edit (null triggers usage message)
 * @param context The context window to save changes to
 */
fun editChapter(chapterManager: Chapter.ChapterManager, index: Int?, context: com.TTT.Context.ContextWindow)
{
    // Validate that an index was provided
    if (index == null) {
        println("Usage: /chapters edit <index>")
        return
    }
    
    // Convert from 1-based user input to 0-based internal indexing
    val adjustedIndex = index - 1
    
    // Retrieve the current chapter content
    val currentChapter = chapterManager.getChapter(adjustedIndex)
    if (currentChapter == null) {
        println("Chapter $index not found.")
        return
    }
    
    // Display current content and prompt for new content
    println("\n=== Editing Chapter $index ===")
    println("Current content:")
    println(currentChapter)
    println("\nEnter new content (type 'SAVE' on a new line to save, 'CANCEL' to cancel):")
    
    // Get user input with enhanced input handling
    val result = readEnhancedInput("SAVE, CANCEL")
    val lastLine = result.split("\n").lastOrNull()?.trim()?.uppercase() ?: ""
    
    // Handle cancellation
    if (lastLine == "CANCEL") {
        println("Edit cancelled.")
        return
    }
    
    // Process the input content and remove delimiter markers
    val contentText = stripMultiLineDelimiter(result).trim()
    
    // Check if content contains chapter delimiters for multi-chapter insertion
    if (contentText.contains("***\n\n")) {
        // Split content into multiple chapters
        val chapters = contentText.split("***\n\n").filter { it.isNotEmpty() }
        
        if (chapters.isNotEmpty()) {
            // Replace the current chapter with the first chapter
            chapterManager.editChapter(adjustedIndex, chapters[0])
            
            // Insert additional chapters after the current one
            chapters.drop(1).forEachIndexed { i, chapter ->
                chapterManager.insertChapter(adjustedIndex + 1 + i, chapter)
            }
            
            // Save changes to context bank
            saveChapterContext(context)
            println("Chapter $index updated and ${chapters.size - 1} additional chapters inserted.")
        }
    } else {
        // Single chapter edit
        if (chapterManager.editChapter(adjustedIndex, contentText)) {
            saveChapterContext(context)
            println("Chapter $index updated successfully.")
        } else {
            println("Failed to update chapter $index.")
        }
    }
}

/**
 * Delete a specific chapter with confirmation prompt.
 * Requires user confirmation before permanently removing chapter content.
 * 
 * @param chapterManager The ChapterManager instance to modify
 * @param index The 1-based chapter index to delete (null triggers usage message)
 * @param context The context window to save changes to
 */
fun deleteChapter(chapterManager: Chapter.ChapterManager, index: Int?, context: com.TTT.Context.ContextWindow)
{
    // Validate that an index was provided
    if (index == null) {
        println("Usage: /chapters delete <index>")
        return
    }
    
    // Convert from 1-based user input to 0-based internal indexing
    val adjustedIndex = index - 1
    
    // Verify the chapter exists before attempting deletion
    val chapter = chapterManager.getChapter(adjustedIndex)
    if (chapter == null) {
        println("Chapter $index not found.")
        return
    }
    
    // Require explicit confirmation for destructive operation
    print("Are you sure you want to delete chapter $index? (y/N): ")
    val confirmation = readEnhancedInput().lowercase()
    
    // Process confirmation response
    if (confirmation == "y" || confirmation == "yes") {
        // Attempt to delete the chapter
        if (chapterManager.deleteChapter(adjustedIndex)) {
            // Save changes to context bank
            saveChapterContext(context)
            println("Chapter $index deleted successfully.")
        } else {
            println("Failed to delete chapter $index.")
        }
    } else {
        // User declined or provided invalid input
        println("Deletion cancelled.")
    }
}

/**
 * Insert a new chapter at a specific position with interactive input.
 * Supports both single chapter insertion and multi-chapter insertion when content contains delimiters.
 * 
 * @param chapterManager The ChapterManager instance to modify
 * @param index The 1-based position to insert at (null triggers usage message)
 * @param context The context window to save changes to
 */
fun insertChapter(chapterManager: Chapter.ChapterManager, index: Int?, context: com.TTT.Context.ContextWindow)
{
    // Validate that an index was provided
    if (index == null) {
        println("Usage: insert <index>")
        return
    }
    
    // Convert from 1-based user input to 0-based internal indexing
    val adjustedIndex = index - 1
    
    // Prompt user for chapter content
    println("\n=== Inserting New Chapter at Position $index ===")
    println("Enter chapter content (type 'SAVE' on a new line to save, 'CANCEL' to cancel):")
    
    // Get user input with enhanced input handling
    val result = readEnhancedInput("SAVE, CANCEL")
    val lastLine = result.split("\n").lastOrNull()?.trim()?.uppercase() ?: ""
    
    // Handle cancellation
    if (lastLine == "CANCEL") {
        println("Insert cancelled.")
        return
    }
    
    // Process the input content and remove delimiter markers
    val contentText = stripMultiLineDelimiter(result).trim()
    
    // Check if content contains chapter delimiters for multi-chapter insertion
    if (contentText.contains("***\n\n")) {
        // Split content into multiple chapters
        val chapters = contentText.split("***\n\n").filter { it.isNotEmpty() }
        
        // Insert chapters sequentially starting at the specified index
        chapters.forEachIndexed { i, chapter ->
            chapterManager.insertChapter(adjustedIndex + i, chapter)
        }
        
        // Save changes to context bank
        saveChapterContext(context)
        println("${chapters.size} chapters inserted successfully starting at position $index.")
    } else {
        // Single chapter insertion
        if (chapterManager.insertChapter(adjustedIndex, contentText)) {
            saveChapterContext(context)
            println("Chapter inserted successfully at position $index.")
        } else {
            println("Failed to insert chapter at position $index.")
        }
    }
}

/**
 * Add a new chapter at the end of the story with interactive input.
 * Supports both single chapter addition and multi-chapter addition when content contains delimiters.
 * 
 * @param chapterManager The ChapterManager instance to modify
 * @param context The context window to save changes to
 */
fun addNewChapter(chapterManager: Chapter.ChapterManager, context: com.TTT.Context.ContextWindow)
{
    // Prompt user for chapter content
    println("\n=== Adding New Chapter ===")
    println("Enter chapter content (type 'SAVE' on a new line to save, 'CANCEL' to cancel):")
    
    // Get user input with enhanced input handling
    val result = readEnhancedInput("SAVE, CANCEL")
    val lastLine = result.split("\n").lastOrNull()?.trim()?.uppercase() ?: ""
    
    // Handle cancellation
    if (lastLine == "CANCEL") {
        println("Add cancelled.")
        return
    }
    
    // Process the input content and remove delimiter markers
    val contentText = stripMultiLineDelimiter(result).trim()
    
    // Check if content contains chapter delimiters for multi-chapter addition
    if (contentText.contains("***\n\n")) {
        // Split content into multiple chapters and add them all
        val chapters = contentText.split("***\n\n").filter { it.isNotEmpty() }
        context.contextElements.addAll(chapters)
        println("${chapters.size} chapters added successfully.")
    } else {
        // Single chapter addition
        context.contextElements.add(contentText)
        println("Chapter added successfully.")
    }
    
    // Save changes to context bank
    saveChapterContext(context)
}

/**
 * Move a chapter from one position to another with validation.
 * Reorders chapters by moving the specified chapter to a new position.
 * 
 * @param chapterManager The ChapterManager instance to modify
 * @param params List containing the move command parameters [from_index, to_index]
 * @param context The context window to save changes to
 */
fun moveChapter(chapterManager: Chapter.ChapterManager, params: List<String>, context: com.TTT.Context.ContextWindow)
{
    // Validate that both from and to indices were provided
    if (params.size < 3) {
        println("Usage: /chapters move <from> <to>")
        return
    }
    
    // Parse and validate the from and to indices
    val fromIndex = params[1].toIntOrNull()?.minus(1) // Convert to 0-based indexing
    val toIndex = params[2].toIntOrNull()?.minus(1)   // Convert to 0-based indexing
    
    if (fromIndex == null || toIndex == null) {
        println("Invalid indices. Use numbers for from and to positions.")
        return
    }
    
    // Attempt to move the chapter
    if (chapterManager.moveChapter(fromIndex, toIndex)) {
        // Save changes to context bank
        saveChapterContext(context)
        println("Chapter moved from position ${fromIndex + 1} to ${toIndex + 1}.")
    } else {
        println("Failed to move chapter. Check that indices are valid.")
    }
}

/**
 * Set the title metadata for a specific chapter.
 * Updates chapter metadata with the provided title string.
 * 
 * @param chapterManager The ChapterManager instance to modify
 * @param params List containing the title command parameters [index, title_words...]
 */
fun setChapterTitle(chapterManager: Chapter.ChapterManager, params: List<String>)
{
    // Validate that both index and title were provided
    if (params.size < 3) {
        println("Usage: /chapters title <index> <title>")
        return
    }
    
    // Parse and validate the chapter index
    val index = params[1].toIntOrNull()?.minus(1) // Convert to 0-based indexing
    if (index == null) {
        println("Invalid index. Use a number for the chapter position.")
        return
    }
    
    // Combine remaining parameters into the title string
    val title = params.drop(2).joinToString(" ")
    
    // Create metadata object with the new title
    val metadata = Chapter.ChapterMetadata(title = title)
    
    // Attempt to set the chapter metadata
    if (chapterManager.setChapterMetadata(index, metadata)) {
        println("Title set for chapter ${index + 1}: '$title'")
    } else {
        println("Failed to set title. Chapter ${index + 1} not found.")
    }
}

/**
 * Display comprehensive statistics about all chapters in the story.
 * Shows total count, word counts, and chapter length statistics.
 * 
 * @param chapterManager The ChapterManager instance to analyze
 */
fun showChapterStats(chapterManager: Chapter.ChapterManager)
{
    // Get comprehensive statistics from the chapter manager
    val stats = chapterManager.getChapterStats()
    
    // Display formatted statistics
    println("\n=== Chapter Statistics ===")
    println("Total chapters: ${stats.totalChapters}")
    println("Total words: ${stats.totalWords}")
    println("Average words per chapter: ${stats.averageWordsPerChapter}")
    println("Longest chapter: ${stats.longestChapter} words")
    println("Shortest chapter: ${stats.shortestChapter} words")
}

/**
 * Export a specific chapter to a text file.
 * Saves the chapter content to the TPipeWriter directory with optional custom filename.
 * 
 * @param chapterManager The ChapterManager instance containing chapter data
 * @param params List containing export parameters [index, optional_filename...]
 */
fun exportChapter(chapterManager: Chapter.ChapterManager, params: List<String>)
{
    // Validate that at least the chapter index was provided
    if (params.size < 2) {
        println("Usage: /chapters export <index> [filename]")
        return
    }
    
    // Parse and validate the chapter index
    val index = params[1].toIntOrNull()?.minus(1) // Convert to 0-based indexing
    if (index == null) {
        println("Invalid index. Use a number for the chapter position.")
        return
    }
    
    // Determine filename - use provided name or generate default
    val filename = if (params.size > 2) {
        params.drop(2).joinToString(" ")
    } else {
        "chapter_${index + 1}.txt" // Default filename format
    }
    
    // Construct the full file path in the TPipeWriter directory
    val homeDir = com.TTT.Util.getHomeFolder()
    val tpipeDir = java.io.File(homeDir, "TPipeWriter")
    if (!tpipeDir.exists()) tpipeDir.mkdirs() // Ensure directory exists
    val filePath = java.io.File(tpipeDir, filename).absolutePath
    
    // Attempt to export the chapter
    if (chapterManager.exportChapter(index, filePath)) {
        println("Chapter ${index + 1} exported to $filePath")
    } else {
        println("Failed to export chapter ${index + 1}.")
    }
}

/**
 * Display help information for chapter management commands.
 * Shows all available chapter management commands with their syntax and descriptions.
 */
fun printChapterHelp()
{
    println("""
        |Chapter Management Commands:
        |  list                    - List all chapters with indices and titles
        |  search <query>         - Search chapters for text/keywords
        |  show <index>           - Display specific chapter content
        |  edit <index>           - Edit chapter at index
        |  delete <index>         - Delete chapter at index
        |  insert <index>         - Insert new chapter at index
        |  add                     - Add new chapter at the end
        |  move <from> <to>       - Move chapter from one index to another
        |  title <index> <title>  - Set chapter title/metadata
        |  stats                  - Show chapter statistics
        |  export <index> [file]  - Export single chapter to file
        |  help                   - Show this menu
        |  back                   - Return to main shell
    """.trimMargin())
}

/**
 * Import lorebook from JSON file and merge into main context.
 * @param filename Path to the JSON file containing lorebook data
 */
fun importLorebook(filename: String)
{
    try
    {
        val file = File(filename)
        if (!file.exists()) {
            println("File not found: $filename")
            return
        }
        
        val jsonContent = file.readText()

        var loreBookData: LoreBookData? = null

        try {
             loreBookData = com.TTT.Util.deserialize<Structs.LoreBookData>(jsonContent)
        }

        catch (e: Exception)
        {
            println(e)
        }

        
        if (loreBookData == null) {
            println("Failed to parse lorebook JSON")
            return
        }
        
        val tpipeLorebook = Structs.convertToTPipeLoreBook(loreBookData)
        val mainContext = ContextBank.getContextFromBank("main")
        
        runBlocking {
            mainContext.merge(tpipeLorebook)
            ContextBank.emplaceWithMutex("main", mainContext)
        }
        
        println("Lorebook imported successfully (${loreBookData.entries.size} entries)")
    }
    catch (e: Exception)
    {
        println("Import failed: ${e.message}")
    }
}

/**
 * Load NAI story data from JSON file and extract story contents.
 * @param filename Path to the JSON file containing NAI story data
 * @return Story document content or null if failed
 */
fun loadNaiStoryData(filename: String): String?
{
    try
    {
        val file = File(filename)
        if (!file.exists()) {
            println("File not found: $filename")
            return null
        }
        
        val jsonContent = file.readText()
        val naiStoryData = com.TTT.Util.deserialize<Structs.NaiStoryData>(jsonContent)
        
        if (naiStoryData == null) {
            println("Failed to parse NAI story JSON")
            return null
        }
        
        return naiStoryData.content.document
    }
    catch (e: Exception)
    {
        println("NAI story load failed: ${e.message}")
        return null
    }
}

/**
 * Load NAI story format and map to TPipeWriter context and metadata.
 * @param filename Path to the NAI JSON file
 */
fun loadNaiStory(filename: String)
{
    val storyContent = loadNaiStoryData(filename)
    if (storyContent == null) {
        return
    }
    
    try
    {
        // Split story content by chapter delimiters
        val contextElements = if (storyContent.contains("***")) {
            storyContent.split("***").filter { it.isNotEmpty() }.toMutableList()
        } else {
            mutableListOf(storyContent)
        }
        
        // Update main context bank
        runBlocking {
            val context = ContextBank.getContextFromBank("main")
            context.contextElements.clear()
            context.contextElements.addAll(contextElements)
            ContextBank.emplaceWithMutex("main", context)
        }
        
        // Clear chapter metadata since NAI format doesn't map directly
        Chapter.GlobalChapterManager.clearAllMetadata()
        
        println("NAI story loaded successfully (${contextElements.size} chapters)")
    }
    catch (e: Exception)
    {
        println("NAI story load failed: ${e.message}")
    }
}

/**
 * Interactive NAI story import management system.
 */
fun manageNaiImport()
{
    println("\n=== NAI Story Import ===")
    
    while (true)
    {
        print("Enter path to NAI story file (or 'back' to exit): ")
        val input = readEnhancedInput().trim()
        
        when (input.lowercase()) {
            "back", "exit" -> return
            "" -> continue
            else -> {
                loadNaiStory(input)
                return
            }
        }
    }
}

/**
 * Start the interactive shell with continuous input loop.
 * Handles exceptions and maintains shell state until exit.
 */
fun startShell()
{
    // Display shell startup information
    println("TPipeWriter Interactive Shell")
    println("Type /help for available commands")
    println("Current mode: ${Env.LoadedState}")
    
    // Main shell loop with error handling
    while (true)
    {
        print("[${Env.LoadedState}]> ")
        try
        {
            parseInput()
        }
        catch (e: Exception)
        {
            println("Error: ${e.message}")
        }
    }
}


/**
 * Link lorebook keys together
 * @param keyName Primary key name
 * @param linkedKeys List of keys to link
 */
fun linkLorebookKeys(keyName: String, linkedKeys: List<String>)
{
    val context = ContextBank.getContextFromBank("main")
    val loreEntry = context.loreBookKeys[keyName]
    
    if (loreEntry == null) {
        println("Key '$keyName' not found.")
        return
    }
    
    val allLinkedKeys = mutableListOf<String>()
    linkedKeys.forEach { linkedKey ->
        if (!context.loreBookKeys.containsKey(linkedKey)) {
            println("Linked key '$linkedKey' not found.")
            return
        }
        allLinkedKeys.add(linkedKey)
        allLinkedKeys.add(linkedKey.lowercase())
        allLinkedKeys.add(linkedKey.uppercase())
    }
    
    allLinkedKeys.forEach { linkedKey ->
        println("Linked '$keyName' to '$linkedKey'")
    }
    
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
}

/**
 * Unlink lorebook keys
 * @param keyName Primary key name
 * @param linkedKeys List of keys to unlink
 */
fun unlinkLorebookKeys(keyName: String, linkedKeys: List<String>)
{
    val context = ContextBank.getContextFromBank("main")
    val loreEntry = context.loreBookKeys[keyName]
    
    if (loreEntry == null) {
        println("Key '$keyName' not found.")
        return
    }
    
    linkedKeys.forEach { linkedKey ->
        println("Unlinked '$keyName' from '$linkedKey'")
    }
    
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
}

/**
 * Add aliases to lorebook key
 * @param keyName Primary key name
 * @param aliases List of aliases to add
 */
fun addLorebookAlias(keyName: String, aliases: List<String>)
{
    val context = ContextBank.getContextFromBank("main")
    val loreEntry = context.loreBookKeys[keyName]
    
    if (loreEntry == null) {
        println("Key '$keyName' not found.")
        return
    }
    
    val allAliases = mutableListOf<String>()
    aliases.forEach { alias ->
        allAliases.add(alias)
        allAliases.add(alias.lowercase())
        allAliases.add(alias.uppercase())
    }
    
    allAliases.forEach { alias ->
        println("Added alias '$alias' to '$keyName'")
    }
    
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
}

/**
 * Remove aliases from lorebook key
 * @param keyName Primary key name
 * @param aliases List of aliases to remove
 */
fun removeLorebookAlias(keyName: String, aliases: List<String>)
{
    val context = ContextBank.getContextFromBank("main")
    val loreEntry = context.loreBookKeys[keyName]
    
    if (loreEntry == null) {
        println("Key '$keyName' not found.")
        return
    }
    
    aliases.forEach { alias ->
        println("Removed alias '$alias' from '$keyName'")
    }
    
    runBlocking {
        ContextBank.emplaceWithMutex("main", context)
    }
}

/**
 * Print the current writing style setting.
 */
fun printCurrentStyle()
{
    val settings = loadSettings()
    if (settings.writingStyle.isEmpty())
    {
        println("No writing style currently set")
    }
    else
    {
        println("Current writing style: ${settings.writingStyle}")
    }
}

/**
 * Interactive chapter rewrite management system.
 */
fun manageChapterRewrite()
{
    //Collect the story so far and all the chapters that exist inside of it.
    val context = ContextBank.getContextFromBank("main")
    val chapterManager = Chapter.ChapterManager(context)
    val chapters = chapterManager.listChapters()
    
    if (chapters.isEmpty()) {
        println("No chapters available to rewrite.")
        return
    }

    //List all chapters to the user.
    println("\n=== Available Chapters ===")
    println("\n=== Chapter Rewrite ===")
    println("Available chapters:")
    chapters.forEach { chapter ->
        val indexStr = (chapter.index + 1).toString().padStart(3)
        println("[$indexStr] ${chapter.title} (${chapter.wordCount} words)")
    }

    //Collect user input on which chapter we need to rewrite.
    print("\nEnter chapter number to rewrite: ")
    val chapterIndex = readEnhancedInput().trim().toIntOrNull()
    if (chapterIndex == null || chapterIndex < 1 || chapterIndex > chapters.size)
    {
        println("Invalid chapter number.")
        return
    }

    //Collect instructions on how the user wants the chapter rewritten.
    print("Enter rewrite instructions: ")
    val instructions = readEnhancedInput().trim()
    if (instructions.isEmpty())
    {
        println("No instructions provided.")
        return
    }

    //Step 2. Move onward to the secondary subshell phase and rewrite pipeline.
    callChapterRewritePipeline("$chapterIndex $instructions")
}

/**
 * Execute chapter rewrite pipeline with approval system.
 */
fun callChapterRewritePipeline(prompt: String)
{

//--------------------------------------------Load Chapter--------------------------------------------------------------

    //Load our settings to grab the defined writing style.
    val settings = loadSettings()
    val style = settings.writingStyle
    
    //Activate pipeline tracing for debugging. Enable it to debug level to dump all content.
    val traceConfig = TraceConfig(detailLevel = TraceDetailLevel.DEBUG,
        outputFormat = TraceFormat.HTML,
        autoExport = true)
    
    Env.expansionPipeline = buildExpansionPipeline()
    Env.expansionPipeline.enableTracing(traceConfig)
    
    // Initialize pipeline after tracing is enabled
    runBlocking {
        Env.expansionPipeline.init(true)
    }


    //Extract distinct prompt components.
    val parts = prompt.split(" ", limit = 2)
    if (parts.size < 2)
    {
        println("Usage: /rewrite <chapter_number> <instructions>")
        return
    }

    //Ask for chapter if not provided in the command.
    val chapterIndex = parts[0].toIntOrNull()
    if (chapterIndex == null)
    {
        println("Invalid chapter number: ${parts[0]}")
        return
    }

    //Get the chapter from the context bank.
    val instructions = parts[1]
    val context = ContextBank.getContextFromBank("main")
    val chapterManager = ChapterManager(context)
    val adjustedIndex = chapterIndex - 1
    
    val originalChapter = chapterManager.getChapter(adjustedIndex)
    if (originalChapter == null)
    {
        println("Chapter $chapterIndex not found.")
        return
    }

//----------------------------------------Setup context window narrowing------------------------------------------------

    //We need to have a fresh copy for context control. Since the user may need to control the exact scope of selection.
    val rewriteContext = ContextWindow()
    rewriteContext.loreBookKeys = context.loreBookKeys //Copy lorebook keys so we actually have them later.

    println("Would you like to narrow down the visible context?  y/n")
    val activateFineContextControl = readln().lowercase()

    if (activateFineContextControl == "y")
    {
        //Break into sub-shell if the user wants to exercise advanced context control.
        val subShellEntryMessage = """
        |
        |Please select one of the following context strategies:
        |
        |1. Pull only the previous chapter behind this chapter.
        |2. Pull the last 8K tokens of the most recent chapters.
        |3. Specify a range of chapters.
        |4. Specify specific chapter numbers.
        """.trimMargin()

        println(subShellEntryMessage)

        //Collect user input on chapter selection strategy.
        val chapterSelectionSetting = readln().toInt()

        //Required because we need the truncation settings to limit context visibility.
        val pipe = Env.expansionPipeline.getPipeByName("Rewrite pipe").second
        val truncationSettings = pipe?.getTruncationSettings() ?: TruncationSettings()

        /**
         * Handle the user's chapter selection strategy. This is the first step before addressing lorebook settings
         * which will require its own now second subshell within this command.
         */
        when(chapterSelectionSetting)
        {
            //Get the chapter prior to the chapter we're about to rewrite.
            1 -> rewriteContext.contextElements.add(chapterManager.getChapter(adjustedIndex - 1).toString())

            //Get the last 8K tokens of chapter context.
            2 -> rewriteContext.contextElements = chapterManager.selectChaptersWithinTokenBudget(8000, truncationSettings).toMutableList()

            //Get chapters by range from low to high.
            3 -> {
                println("Please enter the start chapter number.")
                val startRange = readln().toInt()

                println("\n\nEnter the end of the chapter range.")
                val endRange = readln().toInt()

                rewriteContext.contextElements = chapterManager.getChapterRange(startRange, endRange).toMutableList()
            }

            4 -> {
                println("Enter each chapter you want to load separated by a \",\" and a space." +
                        "\nEX: 1, 2, 3")

                val chapters = readln()

                rewriteContext.contextElements = chapterManager.getSpecificChapters(chapters).toMutableList()

            }
        }


        /**
         * Secondary subshell to determine the lorebook selection strategy. Allows the user to determine the context
         * of which keys are visible to narrow down the behavior of the rewrite pipeline.
         */
        val secondarySubShellEntryMessage = """
        
        Please select one of the following lorebook strategies:
        
        1. Match keys only based on the user prompt.
        2. Match keys based only on the chapter content.
        3. Match keys based on the user prompt and the chapter content.
        4. Select which keys to match individually.
    """.trimIndent()

        println(secondarySubShellEntryMessage)

        val lorebookSelectionStrategy = readln().toInt()

        /**
         * Handle lorebook selection strategy and populate rewriteContext object's lorebook based on the given
         * strategy.
         */
        when(lorebookSelectionStrategy)
        {
            //Match keys only based on the user prompt.
            1 -> {
                rewriteContext.selectAndTruncateContext(prompt, 105000, ContextWindowSettings.TruncateTop, truncationSettings)
            }

            //Match keys based only on the chapters that were selected by the user ignoring the user prompt.
            2 -> {
                val selectionPrompt = rewriteContext.contextElements.joinToString { it }
                rewriteContext.selectAndTruncateContext(selectionPrompt, 105000, ContextWindowSettings.TruncateTop, truncationSettings)
            }

            //Combine user prompt and selected chapters to determine lorebook keys.
            3 -> {
                val userPlusChapterPrompt = prompt + rewriteContext.contextElements.joinToString { it }
                rewriteContext.selectAndTruncateContext(userPlusChapterPrompt, 105000, ContextWindowSettings.TruncateTop, truncationSettings)
            }

            //Manually store lorebook keys.
            4 -> {
                println("Select each key you want to import. Use \",\" separated by a space to input keys." +
                        "\nEX: Ben, Louis, Judd")

                val keys = readln() //Get each key the user has inputted.

                val keyList = keys.split(", ") //Split the keys into a list.

                //Try to match the key and add it manually if we can.
                for(key in keyList)
                {
                    val lorebookEntry = context.findLoreBookEntry(key)

                    if(lorebookEntry != null)
                    {
                        rewriteContext.loreBookKeys[key] = lorebookEntry
                    }
                }

            }
        }

        //Final step: Add our adjusted context to the context bank.
        ContextBank.emplace("rewriteContext", rewriteContext)
    }

    /**
     * Standard story pull, lorebook from user prompt, and select and truncate method for basic handling.
     */
    else
    {
        val truncationSettings = Env.expansionPipeline.getPipeByName("").second?.getTruncationSettings()
        truncationSettings?.multiplyWindowSizeBy = 0
        context.selectAndTruncateContext(prompt, 108000, ContextWindowSettings.TruncateTop, truncationSettings ?: TruncationSettings())
        ContextBank.emplace("rewriteContext", context)
    }




//----------------------------------------------Rewriting the chapter---------------------------------------------------

    println("Rewriting chapter $chapterIndex...")
    
    try {
        runBlocking {

            val prevChapterWindow = ContextWindow()
            prevChapterWindow.contextElements.add(originalChapter)
            ContextBank.emplaceWithMutex("prevChapter", prevChapterWindow)
            
            val userPromptWindow = ContextWindow()
            userPromptWindow.contextElements.add(instructions)
            ContextBank.emplaceWithMutex("user prompt", userPromptWindow)
            val result = Env.expansionPipeline.execute(instructions)
            
            println("DEBUG: Pipeline execution completed, result length: ${result.length}")
            
            if (result.isNotEmpty())
            {
                //Handle results.
                presentRewriteResults(chapterManager, adjustedIndex, originalChapter, result, context)

                //Handle warning the user that refusals occurred.
                val refusalWarning = Env.expansionPipeline.content.metadata["refusalWarning"] as? Boolean

                if(refusalWarning != null && refusalWarning)
                {
                    println("\n\nWARNING!!! \n\n A refusal by an llm in this pipeline has occurred. " +
                            "Would you like to convert this entire pipeline to deepseek to evade model censorship?")

                    val answer = readln()

                    //Force it to deepseek to reduce refusal rate.
                    if(answer.lowercase() == "y")
                    {
                        /**todo: We need to update the map of pipeline names to settings. Also make getters for
                         * for certain pipe settings to help simplify constructing our object.
                         * Perhaps we should consider actually moving the data class that handles this task
                         * into TPipe itself? I'm not sure honestly...
                         */

                        Env.expansionPipeline = convertPipelineToDeepseek(Env.expansionPipeline)
                        val updatedSettings = constructModelSettingsList(Env.expansionPipeline)
                        Env.writingPipelineSettings["rewritePipeline"] = updatedSettings

                    }
                }
                
            } else {
                println("Rewrite pipeline failed to produce results.")

                val trace = Env.expansionPipeline.getTraceReport(TraceFormat.HTML)

                println("DEBUG: Writing trace to file...")
                writeStringToFile("${getHomeFolder()}/TPipeWriter/trace.html", trace)

            }


        }
    } catch (e: Exception) {
        println("Rewrite failed: ${e.message}")
        println("DEBUG: Exception occurred, getting trace report...")
        try {
            val trace = Env.expansionPipeline.getTraceReport(TraceFormat.HTML)
            writeStringToFile("${getHomeFolder()}/TPipeWriter/trace.html", trace)
            println("DEBUG: Exception trace written successfully")
        } catch (traceException: Exception) {
            println("DEBUG: Failed to get trace report: ${traceException.message}")
        }
    }

    println("DEBUG: Getting final trace report...")
    try {
        val trace = Env.expansionPipeline.getTraceReport(TraceFormat.HTML)
        writeStringToFile("${getHomeFolder()}/TPipeWriter/trace.html", trace)

        val traceTxt = Env.expansionPipeline.getTraceReport(TraceFormat.CONSOLE)
        writeStringToFile("${getHomeFolder()}/TPipeWriter/trace.txt", traceTxt)
        println("DEBUG: Final trace written successfully")
    } catch (e: Exception) {
        println("DEBUG: Failed to get final trace report: ${e.message}")
    }

}

/**
 * Present rewrite results and handle user approval.
 */
fun presentRewriteResults(
    chapterManager: Chapter.ChapterManager,
    chapterIndex: Int,
    originalChapter: String,
    rewrittenChapter: String,
    context: com.TTT.Context.ContextWindow
)
{
    println("\n=== Chapter Rewrite Results ===")
    println("Original (first 200 chars): ${originalChapter.take(200)}...")
    println("\nRewritten chapter:")
    println("-".repeat(60))
    println(rewrittenChapter)
    println("-".repeat(60))
    
    println("\nOptions:")
    println("[A]ccept - Replace original chapter with rewritten version")
    println("[R]eject - Keep original chapter unchanged")
    println("[V]iew - Show full comparison side-by-side")
    
    while (true) {
        print("\nChoose option (A/R/V): ")
        val choice = readEnhancedInput().trim().uppercase()
        
        when (choice) {
            "A", "ACCEPT" -> {
                if (chapterManager.editChapter(chapterIndex, rewrittenChapter)) {
                    saveChapterContext(context)
                    println("Chapter ${chapterIndex + 1} has been replaced with the rewritten version.")
                } else {
                    println("Failed to update chapter.")
                }
                return
            }
            "R", "REJECT" -> {
                println("Original chapter kept unchanged.")
                return
            }
            "V", "VIEW" -> {
                showFullComparison(originalChapter, rewrittenChapter)
            }
            else -> println("Invalid option. Please choose A, R, or V.")
        }
    }
}

/**
 * Show side-by-side comparison of original and rewritten chapters.
 */
fun showFullComparison(original: String, rewritten: String)
{
    println("\n=== Full Comparison ===")
    println("ORIGINAL:".padEnd(50) + "REWRITTEN:")
    println("=".repeat(100))
    
    val originalLines = original.split("\n")
    val rewrittenLines = rewritten.split("\n")
    val maxLines = maxOf(originalLines.size, rewrittenLines.size)
    
    for (i in 0 until maxLines) {
        val origLine = originalLines.getOrNull(i) ?: ""
        val rewriteLine = rewrittenLines.getOrNull(i) ?: ""
        
        val origDisplay = if (origLine.length > 48) origLine.take(45) + "..." else origLine.padEnd(50)
        println("$origDisplay$rewriteLine")
    }
    
    println("\nWord counts: Original: ${original.split("\\s+".toRegex()).size}, Rewritten: ${rewritten.split("\\s+".toRegex()).size}")
}

