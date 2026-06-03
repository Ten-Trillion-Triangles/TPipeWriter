package com.example.tpipewriter
import com.TTT.Pipeline.Pipeline
import com.TTT.Pipe.Pipe
import com.TTT.Context.ContextBank
import env.OpenRouterEnv
import Globals.Env
import Shell.startShell

fun main(args: Array<String>) {
    println("TPipeWriter - Initializing...")

    // Propagate the API key from the env var into OpenRouterEnv up front so any
    // pipes constructed before the env init still see a usable key. Surfacing a
    // loud warning at startup is intentional: the app is unusable without one.
    val envKey = System.getenv("OPENROUTER_API_KEY") ?: ""
    OpenRouterEnv.setApiKey(envKey)
    if (envKey.isBlank()) {
        println("WARNING: OPENROUTER_API_KEY is not set. TPipeWriter requires an OpenRouter API key to run.")
        println("         Get one at https://openrouter.ai and re-launch with the env var set.")
    }

    try {
        // Load saved settings or use defaults
        val settings = Shell.loadSettings()

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
