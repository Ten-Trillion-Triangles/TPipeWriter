package Shell

import Globals.Env
import Structs.*
import com.TTT.Enums.ProviderName
import com.TTT.Pipeline.Pipeline
import com.TTT.Util.getHomeFolder
import readEnhancedInput
import java.io.File

/**
 * Main entry point for LLM settings management subshell.
 */
fun manageLLMSettings()
{
    println("\n=== LLM Settings Management ===")
    
    while (true)
    {
        showLLMSettingsMenu()
        print("llm> ")
        val input = readEnhancedInput().trim()
        val parts = input.split(" ")
        val command = parts.getOrNull(0)?.lowercase() ?: ""
        
        when (command)
        {
            "config", "c" -> {
                val pipelineNumber = parts.getOrNull(1)?.toIntOrNull()
                if (pipelineNumber != null)
                {
                    configurePipelineByNumber(pipelineNumber)
                }
                else
                {
                    println("Usage: config <pipeline_number> (1-${getAllPipelines().size})")
                    println("Example: config 1 (configure Writer Pipeline)")
                }
            }
            "export", "e" -> exportLLMSettings()
            "import", "i" -> importLLMSettings()
            "reset", "r" -> resetAllSettings()
            "status", "s" -> showDetailedStatus()
            "bulk", "b" -> manageBulkOperations()
            "backup" -> backupCurrentSettings()
            "help", "?" -> showLLMSettingsMenu()
            "back", "exit", "q" -> return
            "" -> continue
            else -> {
                // Try to parse as direct pipeline number for quick config
                val pipelineNum = command.toIntOrNull()
                if (pipelineNum != null && pipelineNum in 1..getAllPipelines().size)
                {
                    configurePipelineByNumber(pipelineNum)
                }
                else
                {
                    println("Unknown command: $command. Type 'help' for available commands.")
                }
            }
        }
    }
}

/**
 * Display the main LLM settings menu with current pipeline configurations.
 * Shows all pipes within each pipeline with their individual model settings.
 * Updated to handle multi-pipe pipelines by displaying each pipe's configuration separately.
 */
fun showLLMSettingsMenu()
{
    println("\nCurrent Pipeline Settings:")
    
    // Get all available pipelines from cache
    val pipelines = getAllPipelines()
    pipelines.forEachIndexed { index, (name, pipeline) ->
        if (pipeline != null)
        {
            // Extract settings from all pipes in the pipeline (not just the first)
            val allSettings = extractAllPipelineSettings(pipeline)
            if (allSettings.isNotEmpty())
            {
                println("  ${index + 1}. $name:")
                // Display each pipe's settings individually
                allSettings.forEachIndexed { pipeIndex, settings ->
                    println("     Pipe ${pipeIndex + 1}: ${settings.modelName} (temp: ${settings.temperature}, topP: ${settings.topP}, maxTokens: ${settings.maxTokens})")
                }
            }
            else
            {
                println("  ${index + 1}. $name - Not configured")
            }
        }
        else
        {
            println("  ${index + 1}. $name - Not available")
        }
    }
    
    println("\nCommands:")
    println("  config <number>  - Configure specific pipeline")
    println("  export          - Export all settings to file")
    println("  import          - Import settings from file")
    println("  reset           - Reset all to defaults")
    println("  status          - Show detailed current settings")
    println("  bulk            - Bulk operations (apply to all, copy settings)")
    println("  backup          - Create automatic backup of current settings")
    println("  help, ?         - Show this menu")
    println("  back, exit      - Return to main shell")
    println("\nShortcuts: c <num> (config), e (export), i (import), s (status), b (bulk)")
    println("Quick access: Just type pipeline number (e.g., '1' to configure Writer Pipeline)")
}

// Cache pipelines list for performance
private val pipelinesCache by lazy {
    listOf(
        "Writer Pipeline" to Env.writerPipeline,
        "Idea Pipeline" to Env.ideaPipeline,
        "Lorebook Pipeline" to Env.lorebookPipeline,
        "Rewrite Pipeline" to Env.rewritePipeline,
        "Chat Pipeline" to Env.discussionPipeline,
        "Summary Pipeline" to Env.summarizerPipeline,
        "Style Pipeline" to Env.stylePipeline,
        "NCC Pipeline" to Env.nccPipeline,
        "Plus Writer Pipeline" to Env.plusWriterPipe
    )
}

/**
 * Get all available pipelines with their names.
 */
fun getAllPipelines(): List<Pair<String, Pipeline?>> = pipelinesCache

/**
 * Configure a specific pipeline by its menu number.
 */
fun configurePipelineByNumber(pipelineNumber: Int)
{
    val pipelines = getAllPipelines()
    if (pipelineNumber < 1 || pipelineNumber > pipelines.size)
    {
        println("Invalid pipeline number. Choose 1-${pipelines.size}")
        return
    }
    
    val (pipelineName, pipeline) = pipelines[pipelineNumber - 1]
    if (pipeline == null)
    {
        println("Pipeline $pipelineName is not available")
        return
    }
    
    configurePipelineSettings(pipelineName, pipeline)
}

/**
 * Configure settings for a specific pipeline.
 * Updated to handle multi-pipe pipelines by allowing users to select which specific pipe to configure.
 * 
 * @param pipelineName Display name of the pipeline being configured
 * @param pipeline The Pipeline object containing one or more pipes to configure
 */
fun configurePipelineSettings(pipelineName: String, pipeline: Pipeline)
{
    println("\n=== Configuring $pipelineName ===")
    
    // Extract settings from all pipes in the pipeline
    val allSettings = extractAllPipelineSettings(pipeline)
    if (allSettings.isEmpty())
    {
        println("Unable to extract current settings for $pipelineName")
        return
    }
    
    // Display all pipes and let user select which one to configure
    println("\nPipes in this pipeline:")
    allSettings.forEachIndexed { index, settings ->
        println("  ${index + 1}. ${settings.pipeName}: ${settings.modelName} (temp: ${settings.temperature}, topP: ${settings.topP}, maxTokens: ${settings.maxTokens})")
    }
    
    // Get user selection for which pipe to configure
    print("\nSelect pipe to configure (1-${allSettings.size}): ")
    val pipeChoice = readEnhancedInput().trim().toIntOrNull()
    if (pipeChoice == null || pipeChoice < 1 || pipeChoice > allSettings.size)
    {
        println("Invalid pipe selection")
        return
    }
    
    // Set up working settings for the selected pipe
    val selectedPipeIndex = pipeChoice - 1
    var workingSettings = allSettings[selectedPipeIndex].copy()
    val pipeName = workingSettings.pipeName
    
    while (true)
    {
        showPipelineConfigMenu(pipelineName, workingSettings)
        print("config> ")
        val input = readEnhancedInput().trim()
        val parts = input.split(" ")
        val command = parts.getOrNull(0)?.lowercase() ?: ""
        
        when (command)
        {
            "model", "m" -> {
                val modelNumber = parts.getOrNull(1)?.toIntOrNull()
                if (modelNumber != null)
                {
                    workingSettings = selectModel(workingSettings, modelNumber)
                }
                else
                {
                    println("Usage: model <number> (1-6)")
                    println("Example: model 1 (select DeepSeek R1)")
                }
            }
            "temp", "t" -> {
                val tempValue = parts.getOrNull(1)?.toDoubleOrNull()
                if (tempValue != null && tempValue >= 0.0)
                {
                    workingSettings = workingSettings.copy(temperature = tempValue)
                    println("Temperature set to $tempValue")
                }
                else
                {
                    println("Invalid temperature. Use value >= 0.0 (no upper limit)")
                    println("Example: temp 0.8")
                }
            }
            "topp", "p" -> {
                val toppValue = parts.getOrNull(1)?.toDoubleOrNull()
                if (toppValue != null && toppValue in 0.0..1.0)
                {
                    workingSettings = workingSettings.copy(topP = toppValue)
                    println("Top P set to $toppValue")
                }
                else
                {
                    println("Invalid top P. Use value between 0.0 and 1.0")
                    println("Example: topp 0.9")
                }
            }
            "tokens", "tk" -> {
                val tokensValue = parts.getOrNull(1)?.toIntOrNull()
                if (tokensValue != null && tokensValue > 0)
                {
                    workingSettings = workingSettings.copy(maxTokens = tokensValue)
                    println("Max tokens set to $tokensValue")
                }
                else
                {
                    println("Invalid max tokens. Use value > 0")
                    println("Example: tokens 2000")
                }
            }
            "apply", "a" -> {
                if (applySettingsToPipe(pipeline, pipeName, workingSettings))
                {
                    println("Settings applied successfully to $pipeName in $pipelineName")
                    return
                }
                else
                {
                    println("Failed to apply settings")
                }
            }
            "cancel" -> {
                println("Changes cancelled")
                return
            }
            "back" -> return
            "" -> continue
            else -> println("Unknown command: $command")
        }
    }
}

/**
 * Show the pipeline configuration menu for a specific pipe.
 * Updated to display the specific pipe name being configured and include GPT-OSS-20B model option.
 * 
 * @param pipelineName Display name of the parent pipeline
 * @param settings Current ModelSettings for the specific pipe being configured
 */
fun showPipelineConfigMenu(pipelineName: String, settings: ModelSettings)
{
    // Display current settings for the specific pipe (not just pipeline)
    println("\nCurrent Settings for ${settings.pipeName}:")
    println("  Provider: ${settings.provider}")
    println("  Model: ${settings.modelName}")
    println("  Region: ${settings.getRegionV2()} (auto-set)")
    println("  Temperature: ${settings.temperature}")
    println("  Top P: ${settings.topP}")
    println("  Max Tokens: ${settings.maxTokens}")
    
    // Display all available models including the newly added GPT-OSS-20B
    println("\nAvailable Models:")
    println("  1. ${deepSeekModelName()} (DeepSeek R1)")
    println("  2. ${claudeModelName()} (Claude Sonnet 4)")
    println("  3. ${novaModelName()} (Nova Pro)")
    println("  4. ${novaLiteModelName()} (Nova Lite)")
    println("  5. ${gptModelName()} (GPT OSS 20B)")  // Added missing model
    println("  6. ${gpt120bModelName()} (GPT OSS 120B)")
    
    println("\nCommands:")
    println("  model <number>     - Select model (shortcut: m)")
    println("  temp <value>       - Set temperature >= 0.0 (shortcut: t)")
    println("  topp <value>       - Set top P 0.0-1.0 (shortcut: p)")
    println("  tokens <value>     - Set max tokens > 0 (shortcut: tk)")
    println("  apply             - Apply changes and reinitialize (shortcut: a)")
    println("  cancel            - Cancel changes")
    println("  back              - Return to main menu")
}

/**
 * Select a model by number and update settings.
 * Updated to include GPT-OSS-20B as option 5, expanding total options to 6.
 * 
 * @param settings Current ModelSettings to update
 * @param modelNumber User-selected model number (1-6)
 * @return Updated ModelSettings with new model name and auto-set region
 */
fun selectModel(settings: ModelSettings, modelNumber: Int): ModelSettings
{
    // Map user selection to model name, including newly added GPT-OSS-20B
    val newModelName = when (modelNumber)
    {
        1 -> deepSeekModelName()
        2 -> claudeModelName()
        3 -> novaModelName()
        4 -> novaLiteModelName()
        5 -> gptModelName()        // GPT-OSS-20B (newly added)
        6 -> gpt120bModelName()    // GPT-OSS-120B
        else -> {
            println("Invalid model number. Choose 1-6")
            return settings
        }
    }
    
    // Create new settings with updated model and auto-set region
    val newSettings = settings.copy(modelName = newModelName)
    newSettings.setRegion()  // Auto-set region based on model requirements
    println("Model set to $newModelName (region: ${newSettings.getRegionV2()})")
    return newSettings
}

/**
 * Extract current settings from all pipes in a pipeline.
 * New function to support multi-pipe configuration - gets settings from every pipe instead of just the first.
 * 
 * @param pipeline The Pipeline object containing one or more pipes
 * @return List of ModelSettings, one for each pipe in the pipeline
 */
fun extractAllPipelineSettings(pipeline: Pipeline): List<ModelSettings>
{
    // Get all pipes from the pipeline (not just the first one)
    val pipes = pipeline.getPipes()
    // Convert each pipe to ModelSettings for configuration
    return pipes.map { toModelSettings(it) }
}

/**
 * Extract current settings from a pipeline (first pipe only - for compatibility).
 */
fun extractPipelineSettings(pipeline: Pipeline): ModelSettings?
{
    val pipes = pipeline.getPipes()
    if (pipes.isEmpty()) return null
    
    return toModelSettings(pipes[0])
}

/**
 * Apply settings to a specific pipe in a pipeline and reinitialize.
 * New function to support individual pipe configuration within multi-pipe pipelines.
 * 
 * @param pipeline The Pipeline object containing the target pipe
 * @param pipeName Name of the specific pipe to update
 * @param settings New ModelSettings to apply to the pipe
 * @return Boolean indicating success or failure of the operation
 */
fun applySettingsToPipe(pipeline: Pipeline, pipeName: String, settings: ModelSettings): Boolean
{
    return try
    {
        // Find the specific pipe by name within the pipeline
        val pipe = pipeline.getPipeByName(pipeName).second
        if (pipe == null)
        {
            println("Pipe $pipeName not found in pipeline")
            return false
        }
        
        // Apply settings using existing update mechanism
        val settingsList = listOf(settings)
        updatePipeWithModelSettings(pipeline, settingsList)
        true
    }
    catch (e: Exception)
    {
        println("Error applying settings: ${e.message}")
        false
    }
}

/**
 * Apply settings to a pipeline and reinitialize (compatibility function).
 */
fun applySettingsToPipeline(pipeline: Pipeline, settings: ModelSettings): Boolean
{
    return try
    {
        val settingsList = listOf(settings)
        updatePipeWithModelSettings(pipeline, settingsList)
        true
    }
    catch (e: Exception)
    {
        println("Error applying settings: ${e.message}")
        false
    }
}

/**
 * Export all LLM settings to a file.
 */
fun exportLLMSettings()
{
    print("Enter filename (without extension): ")
    val filename = readEnhancedInput().trim()
    
    if (filename.isEmpty())
    {
        println("Invalid filename")
        return
    }
    
    try
    {
        val settingsMap = mutableMapOf<String, ModelSettings>()
        val pipelines = getAllPipelines()
        
        pipelines.forEach { (name, pipeline) ->
            if (pipeline != null)
            {
                val allSettings = extractAllPipelineSettings(pipeline)
                allSettings.forEachIndexed { index, settings ->
                    val key = if (allSettings.size == 1) name else "$name-Pipe${index + 1}"
                    settingsMap[key] = settings
                }
            }
        }
        
        val homeDir = getHomeFolder()
        val tpipeDir = File(homeDir, "TPipeWriter")
        if (!tpipeDir.exists()) tpipeDir.mkdirs()
        
        val settingsFile = File(tpipeDir, "$filename-llm-settings.json")
        val jsonContent = com.TTT.Util.serialize(settingsMap)
        settingsFile.writeText(jsonContent)
        
        println("LLM settings exported to ${settingsFile.absolutePath}")
    }
    catch (e: Exception)
    {
        println("Export failed: ${e.message}")
    }
}

/**
 * Import LLM settings from a file.
 */
fun importLLMSettings()
{
    print("Enter filename (without extension): ")
    val filename = readEnhancedInput().trim()
    
    if (filename.isEmpty())
    {
        println("Invalid filename")
        return
    }
    
    try
    {
        val homeDir = getHomeFolder()
        val tpipeDir = File(homeDir, "TPipeWriter")
        val settingsFile = File(tpipeDir, "$filename-llm-settings.json")
        
        if (!settingsFile.exists())
        {
            println("Settings file not found: ${settingsFile.absolutePath}")
            return
        }
        
        val jsonContent = settingsFile.readText()
        val settingsMap = com.TTT.Util.deserialize<Map<String, ModelSettings>>(jsonContent)
        
        if (settingsMap == null)
        {
            println("Failed to parse settings file")
            return
        }
        
        var appliedCount = 0
        val pipelines = getAllPipelines()
        
        settingsMap.forEach { (key, settings) ->
            val pipelineName = if (key.contains("-Pipe")) key.substringBefore("-Pipe") else key
            val pipeline = pipelines.find { it.first == pipelineName }?.second
            if (pipeline != null)
            {
                if (key.contains("-Pipe"))
                {
                    val pipeName = settings.pipeName
                    if (applySettingsToPipe(pipeline, pipeName, settings))
                    {
                        appliedCount++
                    }
                }
                else if (applySettingsToPipeline(pipeline, settings))
                {
                    appliedCount++
                }
            }
        }
        
        println("Settings imported successfully ($appliedCount pipelines updated)")
    }
    catch (e: Exception)
    {
        println("Import failed: ${e.message}")
    }
}

/**
 * Reset all pipelines to default settings.
 */
fun resetAllSettings()
{
    print("Are you sure you want to reset all LLM settings to defaults? (y/N): ")
    val confirmation = readEnhancedInput().trim().lowercase()
    
    if (confirmation != "y" && confirmation != "yes")
    {
        println("Reset cancelled")
        return
    }
    
    val defaultSettings = ModelSettings(
        provider = ProviderName.Aws,
        modelName = deepSeekModelName(),
        temperature = 0.7,
        topP = 0.7
    )
    defaultSettings.setRegion()
    
    var resetCount = 0
    val pipelines = getAllPipelines()
    
    pipelines.forEach { (_, pipeline) ->
        if (pipeline != null && applySettingsToPipeline(pipeline, defaultSettings))
        {
            resetCount++
        }
    }
    
    println("Reset completed ($resetCount pipelines reset to defaults)")
}

/**
 * Show detailed status of all pipeline settings.
 */
fun showDetailedStatus()
{
    println("\n=== Detailed LLM Settings Status ===")
    
    val pipelines = getAllPipelines()
    pipelines.forEach { (name, pipeline) ->
        println("\n$name:")
        if (pipeline == null)
        {
            println("  Status: Not available")
        }
        else
        {
            val allSettings = extractAllPipelineSettings(pipeline)
            if (allSettings.isNotEmpty())
            {
                allSettings.forEachIndexed { index, settings ->
                    println("  Pipe ${index + 1} (${settings.pipeName}):")
                    println("    Provider: ${settings.provider}")
                    println("    Model: ${settings.modelName}")
                    println("    Region: ${settings.getRegionV2()}")
                    println("    Temperature: ${settings.temperature}")
                    println("    Top P: ${settings.topP}")
                    println("    Max Tokens: ${settings.maxTokens}")
                }
                println("  Status: Configured")
            }
            else
            {
                println("  Status: Unable to extract settings")
            }
        }
    }
}

/**
 * Manage bulk operations on multiple pipelines.
 */
fun manageBulkOperations()
{
    println("\n=== Bulk Operations ===")
    println("1. Apply same settings to all pipelines")
    println("2. Copy settings from one pipeline to others")
    println("3. Back to main menu")
    
    print("bulk> ")
    val choice = readEnhancedInput().trim()
    
    when (choice)
    {
        "1" -> applySettingsToAll()
        "2" -> copyPipelineSettings()
        "3" -> return
        else -> println("Invalid choice")
    }
}

/**
 * Apply same settings to all pipelines.
 */
fun applySettingsToAll()
{
    println("\n=== Apply Settings to All Pipelines ===")
    
    val settings = ModelSettings(
        provider = ProviderName.Aws,
        modelName = deepSeekModelName(),
        temperature = 0.7,
        topP = 0.7
    )
    
    println("Configure settings to apply to all pipelines:")
    
    // Model selection
    println("\nSelect model:")
    println("1. ${deepSeekModelName()} (DeepSeek R1)")
    println("2. ${claudeModelName()} (Claude Sonnet 4)")
    println("3. ${novaModelName()} (Nova Pro)")
    println("4. ${novaLiteModelName()} (Nova Lite)")
    println("5. ${gptModelName()} (GPT OSS 20B)")
    println("6. ${gpt120bModelName()} (GPT OSS 120B)")
    
    print("Model choice (1-6): ")
    val modelChoice = readEnhancedInput().trim().toIntOrNull() ?: 1
    settings.modelName = when (modelChoice)
    {
        2 -> claudeModelName()
        3 -> novaModelName()
        4 -> novaLiteModelName()
        5 -> gptModelName()
        6 -> gpt120bModelName()
        else -> deepSeekModelName()
    }
    
    // Temperature
    print("Temperature (current: ${settings.temperature}): ")
    val temp = readEnhancedInput().trim().toDoubleOrNull()
    if (temp != null && temp >= 0.0) settings.temperature = temp
    
    // Top P
    print("Top P (current: ${settings.topP}): ")
    val topP = readEnhancedInput().trim().toDoubleOrNull()
    if (topP != null && topP in 0.0..1.0) settings.topP = topP
    
    // Max Tokens
    print("Max Tokens (current: ${settings.maxTokens}): ")
    val maxTokens = readEnhancedInput().trim().toIntOrNull()
    if (maxTokens != null && maxTokens > 0) settings.maxTokens = maxTokens
    
    settings.setRegion()
    
    print("Apply these settings to all pipelines? (y/N): ")
    val confirm = readEnhancedInput().trim().lowercase()
    
    if (confirm == "y" || confirm == "yes")
    {
        var appliedCount = 0
        val pipelines = getAllPipelines()
        
        pipelines.forEach { (_, pipeline) ->
            if (pipeline != null && applySettingsToPipeline(pipeline, settings))
            {
                appliedCount++
            }
        }
        
        println("Settings applied to $appliedCount pipelines")
    }
    else
    {
        println("Operation cancelled")
    }
}

/**
 * Copy settings from one pipeline to others.
 */
fun copyPipelineSettings()
{
    println("\n=== Copy Pipeline Settings ===")
    
    val pipelines = getAllPipelines()
    pipelines.forEachIndexed { index, (name, _) ->
        println("  ${index + 1}. $name")
    }
    
    print("\nSelect source pipeline (1-${pipelines.size}): ")
    val sourceIndex = readEnhancedInput().trim().toIntOrNull()
    
    if (sourceIndex == null || sourceIndex < 1 || sourceIndex > pipelines.size)
    {
        println("Invalid pipeline selection")
        return
    }
    
    val (sourceName, sourcePipeline) = pipelines[sourceIndex - 1]
    if (sourcePipeline == null)
    {
        println("Source pipeline not available")
        return
    }
    
    val sourceSettings = extractPipelineSettings(sourcePipeline)
    if (sourceSettings == null)
    {
        println("Unable to extract settings from source pipeline")
        return
    }
    
    print("Copy settings from $sourceName to all other pipelines? (y/N): ")
    val confirm = readEnhancedInput().trim().lowercase()
    
    if (confirm == "y" || confirm == "yes")
    {
        var copiedCount = 0
        pipelines.forEach { (name, pipeline) ->
            if (name != sourceName && pipeline != null && applySettingsToPipeline(pipeline, sourceSettings))
            {
                copiedCount++
            }
        }
        
        println("Settings copied from $sourceName to $copiedCount other pipelines")
    }
    else
    {
        println("Operation cancelled")
    }
}

/**
 * Create automatic backup of current settings.
 */
fun backupCurrentSettings()
{
    try
    {
        val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        val backupFilename = "backup_$timestamp"
        
        val settingsMap = mutableMapOf<String, ModelSettings>()
        val pipelines = getAllPipelines()
        
        pipelines.forEach { (name, pipeline) ->
            if (pipeline != null)
            {
                val allSettings = extractAllPipelineSettings(pipeline)
                allSettings.forEachIndexed { index, settings ->
                    val key = if (allSettings.size == 1) name else "$name-Pipe${index + 1}"
                    settingsMap[key] = settings
                }
            }
        }
        
        val homeDir = getHomeFolder()
        val tpipeDir = File(homeDir, "TPipeWriter")
        val backupDir = File(tpipeDir, "backups")
        if (!backupDir.exists()) backupDir.mkdirs()
        
        val backupFile = File(backupDir, "$backupFilename-llm-settings.json")
        val jsonContent = com.TTT.Util.serialize(settingsMap)
        backupFile.writeText(jsonContent)
        
        println("Backup created: ${backupFile.absolutePath}")
    }
    catch (e: Exception)
    {
        println("Backup failed: ${e.message}")
    }
}