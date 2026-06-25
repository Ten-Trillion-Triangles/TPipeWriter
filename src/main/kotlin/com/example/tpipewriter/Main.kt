package com.example.tpipewriter
import com.TTT.Pipeline.Pipeline
import com.TTT.Pipe.Pipe
import com.TTT.Context.ContextBank
import Globals.Env
import Shell.startShell
import genericOpenAIPipe.env.GenericOpenAIEnv

fun main(args: Array<String>) {
    println("TPipeWriter - Initializing...")

    try {
        // Surface the API key status up-front so the user can verify the
        // wired key matches what they expect. Without this, a missing or
        // invalid key produces a silent hang on the first API call.
        val envKey = System.getenv("MINIMAX_API_KEY")
        if (envKey.isNullOrBlank()) {
            println("[main] WARNING: MINIMAX_API_KEY environment variable is not set.")
            println("[main]   Wire one via: export MINIMAX_API_KEY=\"sk-...\" before running.")
            println("[main]   The pipes will fail at the first API call until this is set.")
        } else {
            val masked = "sk-..." + envKey.takeLast(4)
            println("[main] API key in env: OK ($masked, ${envKey.length} chars)")
        }

        // Load saved settings or use defaults
        val settings = Shell.loadSettings()

        // Initialize the environment with loaded settings.
        // Env.init wires MINIMAX_API_KEY into genericOpenAIEnv BEFORE the pipes
        // are built, so by the time the pipes call .setApiKey(genericOpenAIEnv
        // .resolveApiKey()) the key is present.
        Env.init(
            writingStyle = settings.writingStyle,
            temperature = settings.temperature,
            topP = settings.topP,
            maxTokens = settings.maxTokens,
            useAutomaticLoreBookUpdates = settings.useAutoLorebook
        )

        println("Environment initialized successfully!")
        // Surface post-init API key state so the user can confirm the env-var
        // got into the pipes.
        val resolvedKey = GenericOpenAIEnv.resolveApiKey()
        if (resolvedKey.isBlank()) {
            println("[main] WARNING: GenericOpenAIEnv.resolveApiKey() is BLANK after init.")
            println("[main]   Pipes will fail with 'API key is required' on first call.")
        } else {
            println("[main] GenericOpenAIEnv.resolveApiKey(): OK (sk-..." +
                    resolvedKey.takeLast(4) + ", ${resolvedKey.length} chars)")
        }
        
        // Start the interactive shell
        startShell()
        
    } catch (e: Exception) {
        println("Failed to initialize TPipeWriter: ${e.message}")
        e.printStackTrace()
    }
}
