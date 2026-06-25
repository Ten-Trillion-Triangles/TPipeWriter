package com.example.tpipewriter

import genericOpenAIPipe.GenericOpenAIPipe
import genericOpenAIPipe.api.ApiMode
import genericOpenAIPipe.env.GenericOpenAIEnv
import com.TTT.Pipe.MultimodalContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Live smoke test for MiniMax-M3 via the GenericOpenAIPipe targeting
 * api.minimax.io/v1 with ApiMode.OpenAIResponses.
 *
 * Pattern mirrors the OpenRouterSmokeTest on the OpenRouter branch: gated on
 * the MINIMAX_API_KEY environment variable so the test skips cleanly in CI
 * without secrets. When the key is present, this proves a real MiniMax-M3
 * call completes end-to-end through the GenericOpenAI pipe.
 */
class MiniMaxSmokeTest {
    @Test
    fun testMiniMaxConnection() {
        assumeTrue(
            System.getenv("MINIMAX_API_KEY")?.isNotBlank() == true,
            "MINIMAX_API_KEY not set; skipping smoke test"
        )

        GenericOpenAIEnv.setApiKey(System.getenv("MINIMAX_API_KEY")!!)

        val input = MultimodalContent().apply { text = "Hello" }
        val pipe = GenericOpenAIPipe()
            .setBaseUrl("https://api.minimax.io/v1")
            .setApiKey(GenericOpenAIEnv.resolveApiKey())
            .setApiMode(ApiMode.OpenAIResponses)
            .setModel("MiniMax-M3")
            .setMaxTokens(256)
            .setTemperature(0.0)

        runBlocking {
            pipe.init()
            val out = pipe.execute(input)
            println("MiniMax-M3 response: ${out.text}")
            assertTrue(out.text.isNotBlank(), "MiniMax-M3 returned blank response")
        }
    }
}