package Shell

import Builders.buildCharacterPipeline
import Builders.buildCharacterPipelineWithStory
import Globals.Env
import Globals.Prompts
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Context.ContextBank
import com.TTT.Context.ConverseHistory
import com.TTT.Context.ConverseRole
import com.TTT.Debug.TraceFormat
import com.TTT.Enums.ContextWindowSettings
import com.TTT.Pipe.MultimodalContent
import com.TTT.Pipe.TruncationSettings
import com.TTT.Pipeline.Pipeline
import com.TTT.Util.deserialize
import com.TTT.Util.getHomeFolder
import com.TTT.Util.serialize
import com.TTT.Util.writeStringToFile
import kotlinx.coroutines.runBlocking
import readEnhancedInput

private const val characterChatPagePrefix = "character-chat"
private var activeCharacterName: String? = null
private var activeCharacterPipeline: Pipeline? = null

private fun characterPageKey(): String {
    val cleaned = activeCharacterName
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), "-")
        ?.ifBlank { null }

    return if (cleaned != null) {
        "$characterChatPagePrefix-$cleaned"
    } else {
        characterChatPagePrefix
    }
}

private fun buildTruncationSettings(): TruncationSettings {
    val template = BedrockMultimodalPipe().truncateModuleContext()
    val settings = template.getTruncationSettings()
    settings.multiplyWindowSizeBy = 0
    return settings
}

private fun setActiveCharacter(name: String): Boolean {
    val promptEntry = Prompts.promptMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
    val prompt = promptEntry?.value

    if (prompt.isNullOrEmpty()) {
        println("No character prompt found for \"$name\" in Prompts.promptMap.")
        println("Available characters: ${if (Prompts.promptMap.isEmpty()) "none loaded" else Prompts.promptMap.keys.joinToString(", ")}")
        return false
    }

    activeCharacterName = promptEntry.key
    activeCharacterPipeline = buildCharacterPipeline(prompt)
    println("Active character set to \"${activeCharacterName}\".")
    return true
}

fun characterChatSubshell(initialInput: String = "") {
    println("\n=== Character Chat Subshell ===")
    printCharacterChatHelp()

    if (initialInput.isNotBlank()) {
        processCharacterChatInput(initialInput, allowExit = false)
    }

    while (true) {
        print("character> ")
        val input = readEnhancedInput(removeDelimiterAtEnd = true).trim()
        if (input.isEmpty()) continue
        val shouldExit = processCharacterChatInput(input, allowExit = true)
        if (shouldExit) return
    }
}

fun handleCharacterChatInput(input: String) {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return
    processCharacterChatInput(trimmed, allowExit = false)
}

private fun processCharacterChatInput(input: String, allowExit: Boolean): Boolean {
    val lower = input.lowercase()

    when {
        lower == "back" || lower == "exit" -> {
            if (allowExit) return true
            Env.LoadedState = CommandState.Writer
            println("Exiting character chat mode.")
            return false
        }
        lower == "help" -> {
            printCharacterChatHelp()
            return false
        }
        lower == "list" -> {
            listAvailableCharacters()
            return false
        }
        lower == "clear" -> {
            clearCharacterChatHistory()
            return false
        }
        lower.startsWith("character ") || lower.startsWith("use ") || lower.startsWith("set ") -> {
            val name = input.substringAfter(" ").trim()
            setActiveCharacter(name)
            return false
        }

        lower == "story" -> {
            activeCharacterPipeline = buildCharacterPipelineWithStory(activeCharacterName!!)
            return false
        }

        activeCharacterName == null && Prompts.promptMap.keys.any { it.equals(input, ignoreCase = true) } -> {
            setActiveCharacter(input)
            return false
        }
        activeCharacterName == null -> {
            println("Select a character first with \"character <name>\". Use \"list\" to view options.")
            return false
        }
        else -> {
            runCharacterChat(input)
            return false
        }
    }
}

private fun clearCharacterChatHistory() {
    val pageKey = characterPageKey()
    val window = ContextBank.getContextFromBank(pageKey)
    window.converseHistory.history.clear()
    runBlocking { ContextBank.emplaceWithMutex(pageKey, window) }
    println("Cleared character chat history for \"$pageKey\".")
}

private fun listAvailableCharacters() {
    if (Prompts.promptMap.isEmpty()) {
        println("No character prompts have been loaded into Prompts.promptMap yet.")
        return
    }
    println("Available characters:")
    Prompts.promptMap.keys.sorted().forEach { println(" - $it") }
}

private fun runCharacterChat(message: String) {
    val pipeline = activeCharacterPipeline
    if (pipeline == null) {
        println("No active character pipeline. Set one with \"character <name>\".")
        return
    }

    val pageKey = characterPageKey()
    val storedContext = ContextBank.getContextFromBank(pageKey)
    val existingHistory = deserialize<ConverseHistory>(serialize(storedContext.converseHistory, encodedefault = false))
        ?: ConverseHistory()

    existingHistory.add(ConverseRole.user, MultimodalContent(text = message))

    val truncationSettings = buildTruncationSettings()
    storedContext.converseHistory = existingHistory

    val serializedHistory = serialize(storedContext.converseHistory, encodedefault = false)

    var result = MultimodalContent()
    println("Thinking...\n\n\n\n")

    try {
        runBlocking {
            pipeline.context = storedContext
            pipeline.enableTracing()
            result = pipeline.execute(MultimodalContent(text = serializedHistory))
        }
    } catch (e: Exception) {
        println("Character chat failed: ${e.message}")
        return
    }

    val updatedHistory = deserialize<ConverseHistory>(result.text) ?: storedContext.converseHistory
    storedContext.converseHistory = updatedHistory
    runBlocking { ContextBank.emplaceWithMutex(pageKey, storedContext) }

    val assistantReply = updatedHistory.history.lastOrNull { it.role == ConverseRole.assistant }?.content?.text
        ?: updatedHistory.history.lastOrNull { it.role == ConverseRole.agent }?.content?.text
        ?: result.text

    println("\n\n\n------------------------------")
    println("\n\n\n" + assistantReply)

    val trace = pipeline.getTraceReport(TraceFormat.HTML)
    val traceFile = writeStringToFile("${getHomeFolder()}/TPipeWriter/CharTrace.html", trace)
}

private fun printCharacterChatHelp() {
    println(
        """
        Character Chat Commands:
          character <name> | use <name> | set <name>  - select a character from Prompts.promptMap
          list                                         - list available characters
          clear                                        - clear conversation history for the active character
          help                                         - show this menu
          back | exit                                  - leave the subshell

        Type any other text to chat with the selected character.
        """.trimIndent()
    )
}
