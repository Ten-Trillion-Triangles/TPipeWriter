package com.example.tpipewriter

import com.TTT.Pipe.MultimodalContent
import env.OpenRouterEnv
import kotlinx.coroutines.runBlocking
import openrouterPipe.OpenRouterPipe
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class OpenRouterSmokeTest {
    @Test
    fun testOpenRouterConnection() {
        // Skip cleanly when no API key is set (CI without secrets still builds green).
        assumeTrue(OpenRouterEnv.hasApiKey(), "OPENROUTER_API_KEY not set; skipping smoke test")

        val input = MultimodalContent().apply { text = "Hello" }
        val pipe = OpenRouterPipe()
            .setApiKey(OpenRouterEnv.resolveApiKey())
            .setOpenRouterTitle("TPipeWriter-Test")
            .setHttpReferer("https://github.com/cage/TPipeWriter")
            .setModel("amazon/nova-pro-v1")

        runBlocking {
            pipe.init()
            val out = pipe.execute(input)
            println("OpenRouter response: ${out.text}")
            assertTrue(out.text.isNotBlank(), "OpenRouter returned blank response")
        }
    }
}
