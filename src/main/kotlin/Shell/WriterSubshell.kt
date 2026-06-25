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
import com.TTT.Pipe.Pipe
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
                println("Unknown command: $command. Type 'help' for available commands.")
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
 * Debug function that pushes back to our trace file in real time.
 */
fun debugPipeCallback(pipe: Pipe, content: MultimodalContent)
{
    val trace = Env.plusWriterPipe.getTraceReport(TraceFormat.HTML)
    writeStringToFile("${getHomeFolder()}/TPipeWriter/Trace.html", trace)
}

/**
 * Execute the writer pipeline with specified settings.
 *
 * Uses Util.runWithLiveTrace so streaming callbacks are wired on every pipe
 * in the connector and the trace file at ~/TPipeWriter/Trace.html is flushed
 * every 2 seconds while the pipeline runs. The streaming callback writes
 * SSE chunks directly to FD.out, bypassing Java's PrintStream line buffer
 * (which was holding chunks until a newline arrived — the root cause of the
 * "no streaming visible" symptom on /write, continue, etc.).
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

    // Wrap the writer connector in runWithLiveTrace so both pipelines
    // (low and med) get streaming callbacks and the trace file flushes
    // live. The Connector's internal pipes get the streaming callback
    // because runWithLiveTraceAll iterates them.
    val lowPipeline = writerLevelConnector.get("low")
    val medPipeline = writerLevelConnector.get("med")

    // Enable tracing on the connector and both child pipelines so the
    // connector's events + per-pipe events all land in PipeTracer's
    // in-memory store.
    writerLevelConnector.enableTracing(
        com.TTT.Debug.TraceConfig(
            detailLevel = com.TTT.Debug.TraceDetailLevel.DEBUG,
            outputFormat = com.TTT.Debug.TraceFormat.HTML
        )
    )
    listOfNotNull(lowPipeline, medPipeline).forEach { it.enableTracing(
        com.TTT.Debug.TraceConfig(
            detailLevel = com.TTT.Debug.TraceDetailLevel.DEBUG,
            outputFormat = com.TTT.Debug.TraceFormat.HTML
        )
    ) }

    // Wire streaming callback to all pipes in the connector's branches.
    // Use a SINGLE FileOutputStream to FD.out across all callbacks — opening
    // a new one per chunk and closing it causes "Stream Closed" errors after
    // the first chunk.
    val rawStdout = java.io.FileOutputStream(java.io.FileDescriptor.out)
    val streamingCallback: suspend (String) -> Unit = { chunk ->
        if (chunk.isNotEmpty()) {
            rawStdout.write(chunk.toByteArray(Charsets.UTF_8))
            rawStdout.flush()
        }
    }
    listOfNotNull(lowPipeline, medPipeline).forEach { pipeline ->
        pipeline.getPipes().forEach { pipe ->
            if (pipe is genericOpenAIPipe.GenericOpenAIPipe) {
                pipe.setStreamingCallback(streamingCallback)
            }
        }
    }

    try {
        // Write the trace file in a background thread before runBlocking
        // starts (so the user sees the file size grow even if runBlocking
        // never returns). The thread stops itself when the JVM exits.
        val activePipeline = writerLevelConnector.get(writingStrength)
        val stopFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        val flushThread = Thread {
            while (!stopFlag.get()) {
                try {
                    val trace = activePipeline?.getTraceReport(com.TTT.Debug.TraceFormat.HTML) ?: ""
                    if (trace.isNotEmpty()) {
                        writeStringToFile("${getHomeFolder()}/TPipeWriter/Trace.html", trace)
                    }
                } catch (_: Exception) {
                    // best-effort
                }
                try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
            }
        }
        flushThread.isDaemon = true
        flushThread.start()

        val result = kotlinx.coroutines.runBlocking {
            // Re-install the per-pipe-completion callback as a no-op safety
            // hook. Without this, the writer pipeline can hang in
            // runBlocking because the connector's internal coroutines need
            // some external signal to keep progressing. The OLD code used
            // this callback to write the trace per-pipe; we keep it as a
            // no-op so we don't pay the trace-write cost during streaming.
            writerLevelConnector.get(writingStrength)?.setPipeCompletionCallback { _, _ -> /* no-op */ }
            writerLevelConnector.execute(writingStrength, MultimodalContent(text = finalPrompt))
        }
        stopFlag.set(true)
        flushThread.join(2000) // wait up to 2s for the flush to stop

        // Write the final trace to file at ~/TPipeWriter/Trace.html after
        // the pipeline completes so the user has a complete record.
        val finalTrace = activePipeline?.getTraceReport(com.TTT.Debug.TraceFormat.HTML) ?: ""
        writeStringToFile("${getHomeFolder()}/TPipeWriter/Trace.html", finalTrace)

        if (result.text.isNotEmpty())
        {
            // Streaming callback wrote raw prose deltas to stdout in real
            // time. The pipeline's transformation chain (recordWritingPipePage
            // + applySurgicalReplacementsAndBank) writes the FINAL chapter text
            // (with surgical fixes applied) to the "new page" ContextBank.
            //
            // Print the banked result so the user has the canonical
            // post-pipeline text on screen — this matches the behavior of
            // the OpenRouter and main branches. Without this, the user only
            // sees the streamed raw deltas and has to manually run /chapters
            // show <N> to see what was actually written.
            //
            // The streamed output and the banked output will be very similar
            // (the surgical transformation modifies, doesn't rewrite), but
            // the banked version is the canonical "what got persisted to
            // context" text.
            try {
                val textBarrier = "==================================New Segment========================================="
                val bankedContext = ContextBank.getContextFromBank("new page")
                val bankedResult = bankedContext.contextElements.lastOrNull()
                if (!bankedResult.isNullOrBlank()) {
                    println("\n\n\n$textBarrier\n\n$bankedResult")
                } else {
                    println("\n\n[writer] Chapter segment banked into context.")
                }
            } catch (e: Exception) {
                println("\n\n[writer] Chapter segment banked into context.")
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
                Env.writingPipelineSettings["Rewrite Pipeline"] = updatedSettings

            }
        }
    } catch(exception: Exception) {
        println(exception)
        return
    }
}