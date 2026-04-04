package com.example.tpipewriter
import com.TTT.Pipeline.Pipeline
import com.TTT.Pipe.Pipe
import bedrockPipe.BedrockPipe
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Context.ContextBank
import env.bedrockEnv
import Globals.Env
import Shell.startShell

fun main(args: Array<String>) {
    println("TPipeWriter - Initializing...")
    
    try {
        // Load saved settings or use defaults
        val settings = Shell.loadSettings()
        
        // Set active personas from settings
        Env.activeAuthorPersona = settings.activeAuthorPersona
        Env.activeEditorPersona = settings.activeEditorPersona
        Env.activeRichardTreadwellPersona = settings.activeRichardTreadwellPersona
        Env.activeControlPersona = settings.activeControlPersona
        
        // Initialize the environment with loaded settings
        Env.init(
            writingStyle = settings.writingStyle,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            useAutomaticLoreBookUpdates = settings.useAutoLorebook
        )
        
        println("Environment initialized successfully!")
        
        // Start the interactive shell
        startShell()
        
    } catch (e: Exception) {
        println("Failed to initialize TPipeWriter: ${e.message}")
        e.printStackTrace()
    }
}
