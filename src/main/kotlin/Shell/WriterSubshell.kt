package Shell

import Globals.Env
import Structs.constructModelSettingsList
import Structs.convertPipelineToDeepseek
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
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

/**
 * Writer pipeline subshell with advanced controls.
 * Allows level selection, advanced mode control, and context selection.
 */
fun writerSubshell(
    initialPrompt: String,
    writerLevelConnector: Connector,
    currentGlobalContext: ContextWindow,
    relevantContext: String,
    tokenCountSettings: TruncationSettings
)
{
    var writingStrength = "med" // Default to medium level
    var writerContext = ContextWindow()
    writerContext.loreBookKeys = currentGlobalContext.loreBookKeys
    var contextConfigured = false
    
    println("\n=== Writer Pipeline Subshell ===")
    showWriterSubshellMenu()
    
    while (true)
    {
        print("writer> ")
        val input = readEnhancedInput().trim()
        val parts = input.split(" ")
        val command = parts[0].lowercase()
        
        when (command) {
            "level" -> {
                if (parts.size > 1) {
                    val level = parts[1].lowercase()
                    globalWritingStrength = level
                    println("Pipeline level set to: $level")
                } else {
                    println("Current level: $globalWritingStrength")
                    println("Available levels: low, med")
                }
            }
            "context" -> {
                configureWriterContext(writerContext, currentGlobalContext, initialPrompt, tokenCountSettings)
                contextConfigured = true
            }
            "advanced" -> {
                val newMode = if (parts.size > 1) parts[1].lowercase() == "on" else !Env.advancedMode
                Env.advancedMode = newMode
                println("Advanced mode: ${if (Env.advancedMode) "ON" else "OFF"}")
            }
            "status" -> showWriterStatus(writingStrength, contextConfigured, writerContext)
            "write" -> {
                if (parts.size > 1) {
                    val writePrompt = parts.drop(1).joinToString(" ")
                    executeWriterPipeline(writePrompt, writingStrength, writerLevelConnector, 
                        if (contextConfigured) writerContext else currentGlobalContext, 
                        if (contextConfigured) writePrompt else relevantContext, tokenCountSettings)
                    return
                } else {
                    println("Usage: write <prompt>")
                }
            }
            "help" -> showWriterSubshellMenu()
            "back", "exit" -> return
            "" -> continue
            else -> {
                // Treat as write command if not a recognized command
                executeWriterPipeline(input, writingStrength, writerLevelConnector,
                    if (contextConfigured) writerContext else currentGlobalContext,
                    if (contextConfigured) input else relevantContext, tokenCountSettings)
                return
            }
        }
    }
}

/**
 * Display writer subshell menu options.
 */
fun showWriterSubshellMenu()
{
    println("""
        |Writer Subshell Commands:
        |  level [low|med]        - Set pipeline strength level
        |  context               - Configure advanced context selection
        |  advanced [on|off]     - Toggle advanced mode
        |  status                - Show current configuration
        |  write <prompt>        - Execute with current settings
        |  help                  - Show this menu
        |  back                  - Return to main shell
        |
        |Or enter any text to write directly and exit subshell.
    """.trimMargin())
}

/**
 * Configure writer context with advanced selection options.
 */
fun configureWriterContext(
    writerContext: ContextWindow,
    currentGlobalContext: ContextWindow,
    finalPrompt: String,
    tokenCountSettings: TruncationSettings
)
{
    writerContext.contextElements.clear()
    writerContext.loreBookKeys = currentGlobalContext.loreBookKeys
    
    val subShellEntryMessage = """
        |
        |Please select one of the following context strategies:
        |
        |1. Use last 8K tokens for context selection.
        |2. Specify a range of chapters.
        |3. Specify specific chapter numbers.
        |4. Use all available context.
        """.trimMargin()
    
    println(subShellEntryMessage)
    
    val chapterSelectionSetting = readEnhancedInput().toIntOrNull() ?: 1
    
    when(chapterSelectionSetting)
    {
        1 -> {
            val tempContext = ContextWindow()
            tempContext.contextElements.addAll(currentGlobalContext.contextElements)
            tempContext.selectAndTruncateContext(
                "",
                8000,
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
            writerContext.contextElements = tempContext.contextElements
        }
        
        2 -> {
            println("Please enter the start chapter number.")
            val startRange = readEnhancedInput().toIntOrNull() ?: 1
            
            println("Enter the end of the chapter range.")
            val endRange = readEnhancedInput().toIntOrNull() ?: currentGlobalContext.contextElements.size
            
            val adjustedStart = (startRange - 1).coerceIn(0, currentGlobalContext.contextElements.size - 1)
            val adjustedEnd = (endRange - 1).coerceIn(adjustedStart, currentGlobalContext.contextElements.size - 1)
            
            for (i in adjustedStart..adjustedEnd) {
                if (i < currentGlobalContext.contextElements.size) {
                    writerContext.contextElements.add(currentGlobalContext.contextElements[i])
                }
            }
        }
        
        3 -> {
            println("Enter each chapter you want to load separated by a \",\" and a space.")
            println("EX: 1, 2, 3")
            
            val chapters = readEnhancedInput()
            val chapterIndices = chapters.split(",").mapNotNull { it.trim().toIntOrNull() }
            
            chapterIndices.forEach { chapterNum ->
                val adjustedIndex = chapterNum - 1
                if (adjustedIndex in 0 until currentGlobalContext.contextElements.size) {
                    writerContext.contextElements.add(currentGlobalContext.contextElements[adjustedIndex])
                }
            }
        }
        
        4 -> {
            writerContext.contextElements.addAll(currentGlobalContext.contextElements)
        }
    }
    
    // Secondary subshell for lorebook selection
    val secondarySubShellEntryMessage = """
        
        Please select one of the following lorebook strategies:
        
        1. Match keys only based on the user prompt.
        2. Match keys based only on the selected chapters.
        3. Match keys based on the user prompt and selected chapters.
        4. Select which keys to match individually.
    """.trimIndent()
    
    println(secondarySubShellEntryMessage)
    
    val lorebookSelectionStrategy = readEnhancedInput().toIntOrNull() ?: 3
    
    when(lorebookSelectionStrategy)
    {
        1 -> {
            writerContext.selectAndTruncateContext(
                finalPrompt,
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
        
        2 -> {
            val selectionPrompt = writerContext.contextElements.joinToString(" ")
            writerContext.selectAndTruncateContext(
                selectionPrompt,
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
        
        3 -> {
            val userPlusChapterPrompt = finalPrompt + " " + writerContext.contextElements.joinToString(" ")
            writerContext.selectAndTruncateContext(
                userPlusChapterPrompt,
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
        
        4 -> {
            println("Select each key you want to import. Use \",\" separated by a space to input keys.")
            println("EX: Ben, Louis, Judd")
            
            val keys = readEnhancedInput()
            val keyList = keys.split(",").map { it.trim() }
            
            keyList.forEach { key ->
                val lorebookEntry = currentGlobalContext.findLoreBookEntry(key)
                if (lorebookEntry != null) {
                    writerContext.loreBookKeys[key] = lorebookEntry
                }
            }
            
            writerContext.selectAndTruncateContext(
                finalPrompt,
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
    }
    
    println("Context configuration completed.")
}

/**
 * Show current writer subshell status.
 */
fun showWriterStatus(writingStrength: String, contextConfigured: Boolean, writerContext: ContextWindow)
{
    println("\n=== Writer Status ===")
    println("Pipeline level: $writingStrength")
    println("Advanced mode: ${if (Env.advancedMode) "ON" else "OFF"}")
    println("Context configured: ${if (contextConfigured) "YES" else "NO"}")
    if (contextConfigured) {
        println("Context chapters: ${writerContext.contextElements.size}")
        println("Lorebook keys: ${writerContext.loreBookKeys.size}")
    }
}

/**
 * Execute the writer pipeline with specified settings.
 */
fun executeWriterPipeline(
    finalPrompt: String,
    writingStrength: String,
    writerLevelConnector: Connector,
    contextWindow: ContextWindow,
    relevantContext: String,
    tokenCountSettings: TruncationSettings
)
{
    val selectedPipeline = writerLevelConnector.get(writingStrength)
    selectedPipeline?.context = contextWindow
    
    val entryPipe = selectedPipeline?.getPipes()[0]
    
    println("Thinking...")
    
    try {
        val traceConfig = TraceConfig(detailLevel = TraceDetailLevel.DEBUG,
            outputFormat = TraceFormat.HTML,
            autoExport = true)
        
        writerLevelConnector.enableTracing(traceConfig)
        
        runBlocking {
            val result = writerLevelConnector.execute(writingStrength, MultimodalContent(text = finalPrompt))
            
            if(result.text.isNotEmpty())
            {
                try {
                    val textBarrier = """==================================New Segment=========================================
                        |
                        |
                        |
                    """.trimMargin()
                    val bankedResult = ContextBank.getContextFromBank("main", false).contextElements.last()
                    println("\n\n\n" + textBarrier + bankedResult)
                } catch (exception: Exception) {
                    println(exception)
                }
            } else {
                println("The model failed to return a result")
            }

            val refusalWarning = result.metadata["refusalWarning"] as Boolean?

            //Handle any refusals that may have occurred.
            if(refusalWarning != null && refusalWarning)
            {
                println("\n\nWARNING!!! \n\n A refusal by an llm in this pipeline has occurred. " +
                        "Would you like to convert this entire pipeline to deepseek to evade model censorship?")

                val answer = readln()

                //Force it to deepseek to reduce refusal rate.
                if(answer.lowercase() == "y")
                {
                    Env.rewritePipeline = convertPipelineToDeepseek(Env.rewritePipeline)
                    val updatedSettings = constructModelSettingsList(Env.rewritePipeline)
                    Env.writingPipelineSettings["rewritePipeline"] = updatedSettings

                }
            }
        }
    } catch(exception: Exception) {
        println(exception)
        return
    }


    
    val traceOutput = writerLevelConnector.getTrace(TraceFormat.HTML)
    writeStringToFile("${getHomeFolder()}/TPipeWriter/Trace.html", traceOutput)
}