package Shell

import Chapter.ChapterManager
import Globals.ModelConfig
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Context.ContextBank
import com.TTT.Context.Dictionary
import com.TTT.Pipe.TruncationSettings
import readEnhancedInput

private data class TokenModelOption(
    val label: String,
    val modelName: String,
    val aliases: Set<String>
)

private val tokenModelOptions = listOf(
    TokenModelOption("DeepSeek R1", ModelConfig.deepseekModelName, setOf("1", "deepseek", "r1")),
    TokenModelOption("DeepSeek V3", ModelConfig.deepseekV31, setOf("2", "deepseek v3", "v3", "v31")),
    TokenModelOption("Nova Lite", ModelConfig.novaModelName, setOf("3", "nova", "nova lite")),
    TokenModelOption("Nova Pro", ModelConfig.novaProModelName, setOf("4", "nova pro")),
    TokenModelOption("GPT OSS 20B", ModelConfig.gptOssModelName, setOf("5", "gpt", "gpt oss", "gpt oss 20b")),
    TokenModelOption("GPT OSS 120B", ModelConfig.gptOss120bModelName, setOf("6", "gpt 120b", "gpt oss 120b")),
    TokenModelOption("Claude Sonnet 4", ModelConfig.claudeModelName, setOf("7", "claude")),
    TokenModelOption("Qwen 235B", ModelConfig.qwen235B, setOf("8", "qwen 235b")),
    TokenModelOption("Qwen 32B", ModelConfig.qwen32B, setOf("9", "qwen 32b")),
    TokenModelOption("Qwen Coder 480B", ModelConfig.qwenCoder480B, setOf("10", "qwen coder", "qwen coder 480b")),
    TokenModelOption("Qwen Coder 30B", ModelConfig.qwenCoder30B, setOf("11", "qwen coder 30b")),
    TokenModelOption("Palmyra X5", ModelConfig.PalmyraX5, setOf("12", "palmyra", "palmyra x5")),
    TokenModelOption("Llama 4 Maverick", ModelConfig.llamaMaverick, setOf("13", "llama 4", "maverick")),
    TokenModelOption("Llama 3.3 70B", ModelConfig.llama70B, setOf("14", "llama 70b")),
    TokenModelOption("Llama 3.1 405B", ModelConfig.llama405B, setOf("15", "llama 405b")),
    TokenModelOption("Jamba 1.5 Large", ModelConfig.jambaModelName, setOf("16", "jamba"))
)

fun tokenCountingSubshell()
{
    var currentModel = tokenModelOptions.first { it.modelName == ModelConfig.deepseekModelName }
    var currentSettings = buildTruncationSettings(currentModel.modelName)

    println("\n=== Token Counting Subshell ===")
    printTokenCountingHelp()

    while (true)
    {
        print("tokens> ")
        val input = readEnhancedInput(removeDelimiterAtEnd = true).trim()
        if (input.isEmpty()) continue

        val command = input.substringBefore(" ").lowercase()
        val args = input.substringAfter(" ", "").trim()

        when (command)
        {
            "chapter" -> countSingleChapter(args, currentSettings)
            "chapters" -> countChapterRange(args, currentSettings)
            "lorebook" -> countLorebook(args, currentSettings)
            "context" -> countContext(args, currentSettings)
            "model" ->
            {
                val modelSelection = if (args.isBlank())
                {
                    printModelOptions()
                    println("Enter a model name or number:")
                    readEnhancedInput(removeDelimiterAtEnd = true).trim()
                }
                else args

                val selected = resolveModelChoice(modelSelection)
                if (selected != null)
                {
                    currentModel = selected
                    currentSettings = buildTruncationSettings(currentModel.modelName)
                    println("Token counting model set to ${currentModel.label} (${currentModel.modelName})")
                }
                else
                {
                    println("Unknown model selection: $modelSelection")
                    printModelOptions()
                }
            }
            "models" -> printModelOptions()
            "settings" -> printCurrentSettings(currentModel, currentSettings)
            "help" -> printTokenCountingHelp()
            "exit", "back" -> return
            else ->
            {
                println("Unknown command: $command")
                printTokenCountingHelp()
            }
        }
    }
}

private fun buildTruncationSettings(modelName: String): TruncationSettings
{
    return BedrockMultimodalPipe()
        .setModel(modelName)
        .truncateModuleContext()
        .getTruncationSettings()
}

private fun resolveModelChoice(input: String): TokenModelOption?
{
    val normalized = input.trim().lowercase()
    if (normalized.isEmpty()) return null

    return tokenModelOptions.firstOrNull { option ->
        option.aliases.any { alias -> alias == normalized } ||
            option.modelName.lowercase() == normalized
    }
}

private fun printTokenCountingHelp()
{
    println(
        """
        Token Counting Commands:
          chapter <index>      - Count tokens for a single chapter
          chapters <s>-<e>     - Count tokens for a chapter range
          lorebook [key]       - Count tokens for all lorebook entries or one key
          context [text]       - Count story context, or custom text if provided
          model <name|number>   - Switch the counting tokenizer model
          models               - List available tokenizer models
          settings             - Show the current counting model and tokenizer settings
          help                 - Show this menu
          back | exit          - Leave the subshell
        """.trimIndent()
    )
}

private fun printModelOptions()
{
    println("Available token counting models:")
    tokenModelOptions.forEachIndexed { index, option ->
        println("  ${index + 1}. ${option.label} -> ${option.modelName}")
    }
}

private fun printCurrentSettings(currentModel: TokenModelOption, settings: TruncationSettings)
{
    println("Current model: ${currentModel.label} (${currentModel.modelName})")
    println("Tokenizer settings:")
    println("  countSubWordsInFirstWord: ${settings.countSubWordsInFirstWord}")
    println("  favorWholeWords: ${settings.favorWholeWords}")
    println("  countOnlyFirstWordFound: ${settings.countOnlyFirstWordFound}")
    println("  splitForNonWordChar: ${settings.splitForNonWordChar}")
    println("  alwaysSplitIfWholeWordExists: ${settings.alwaysSplitIfWholeWordExists}")
    println("  countSubWordsIfSplit: ${settings.countSubWordsIfSplit}")
    println("  nonWordSplitCount: ${settings.nonWordSplitCount}")
}

private fun countSingleChapter(args: String, settings: TruncationSettings)
{
    val chapterIndex = parsePositiveInt(args) ?: promptForPositiveInt("Enter chapter number:")
    if (chapterIndex == null) return

    val chapter = ChapterManager(ContextBank.getContextFromBank("main")).getChapter(chapterIndex - 1)
    if (chapter.isNullOrEmpty())
    {
        println("Chapter $chapterIndex not found.")
        return
    }

    printTextStats("Chapter $chapterIndex", chapter, settings)
}

private fun countChapterRange(args: String, settings: TruncationSettings)
{
    val rangeText = if (args.isNotBlank()) args else {
        println("Enter a chapter range in the form start-end:")
        readEnhancedInput(removeDelimiterAtEnd = true).trim()
    }

    val parts = rangeText.split("-", limit = 2)
    if (parts.size != 2)
    {
        println("Use the form start-end, for example 1-5.")
        return
    }

    val start = parts[0].trim().toIntOrNull()
    val end = parts[1].trim().toIntOrNull()
    if (start == null || end == null || start < 1 || end < start)
    {
        println("Invalid range: start and end must be positive integers and start must be <= end.")
        return
    }

    val chapterManager = ChapterManager(ContextBank.getContextFromBank("main"))
    val chapters = chapterManager.getChapterRange(start, end)
    if (chapters.isEmpty())
    {
        println("No chapters found in that range.")
        return
    }

    val combined = chapters.joinToString("\n\n")
    printTextStats("Chapters $start-$end", combined, settings)

    println("Per-chapter counts:")
    chapters.forEachIndexed { index, chapter ->
        val chapterNumber = start + index
        val tokens = Dictionary.countTokens(chapter, settings)
        println("  Chapter $chapterNumber: $tokens tokens")
    }
}

private fun countLorebook(args: String, settings: TruncationSettings)
{
    val lorebook = ContextBank.getContextFromBank("main").loreBookKeys
    if (lorebook.isEmpty())
    {
        println("No lorebook entries are loaded.")
        return
    }

    if (args.isNotBlank())
    {
        val entry = lorebook.entries.firstOrNull { it.key.equals(args, ignoreCase = true) }
        if (entry == null)
        {
            println("No lorebook entry found for \"$args\".")
            return
        }

        printTextStats("Lorebook entry: ${entry.key}", entry.value.value, settings)
        return
    }

    var totalTokens = 0
    println("Lorebook entry counts:")
    lorebook.entries.sortedBy { it.key.lowercase() }.forEach { entry ->
        val tokens = Dictionary.countTokens(entry.value.value, settings)
        totalTokens += tokens
        println("  ${entry.key}: $tokens tokens")
    }
    println("Total lorebook tokens: $totalTokens")
}

private fun countContext(args: String, settings: TruncationSettings)
{
    if (args.isNotBlank())
    {
        printTextStats("Custom text", args, settings)
        return
    }

    val mainContext = ContextBank.getContextFromBank("main").contextElements.joinToString("\n\n")
    if (mainContext.isBlank())
    {
        println("No story context is loaded yet.")
        return
    }

    printTextStats("Current story context", mainContext, settings)
}

private fun printTextStats(label: String, text: String, settings: TruncationSettings)
{
    val tokens = Dictionary.countTokens(text, settings)
    val words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    val chars = text.length

    println(label)
    println("  Characters: $chars")
    println("  Words: $words")
    println("  Tokens: $tokens")

    if (chars > 0)
    {
        println("  Tokens per character: ${"%.3f".format(tokens.toDouble() / chars)}")
    }

    if (words > 0)
    {
        println("  Tokens per word: ${"%.2f".format(tokens.toDouble() / words)}")
    }
}

private fun parsePositiveInt(value: String): Int?
{
    val parsed = value.trim().toIntOrNull()
    return parsed?.takeIf { it > 0 }
}

private fun promptForPositiveInt(prompt: String): Int?
{
    println(prompt)
    return parsePositiveInt(readEnhancedInput(removeDelimiterAtEnd = true).trim())
}
