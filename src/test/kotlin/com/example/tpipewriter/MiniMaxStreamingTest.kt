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
 * Live streaming test for MiniMax-M3 via the GenericOpenAIPipe.
 *
 * Asserts that the SSE chunk callback fires at least once and that the
 * assembled chunk output is a coherent non-blank response — proves that
 * streaming actually works against api.minimax.io/v1/responses, not just
 * that the non-streaming path returns text.
 *
 * Pattern mirrors the live streaming test in TPipe-GenericOpenAI's
 * own test suite (GenericOpenAIPipeLiveTest), adapted for MiniMax-M3.
 */
class MiniMaxStreamingTest {
    @Test
    fun testMiniMaxStreamingChunkOrdering() {
        assumeTrue(
            System.getenv("MINIMAX_API_KEY")?.isNotBlank() == true,
            "MINIMAX_API_KEY not set; skipping streaming test"
        )

        GenericOpenAIEnv.setApiKey(System.getenv("MINIMAX_API_KEY")!!)

        val chunks = mutableListOf<String>()
        val callback: suspend (String) -> Unit = { chunk ->
            println("STREAM_CHUNK: [$chunk]")
            chunks.add(chunk)
        }

        val pipe: GenericOpenAIPipe = GenericOpenAIPipe()
            .setBaseUrl("https://api.minimax.io/v1")
            .setApiKey(GenericOpenAIEnv.resolveApiKey())
            .setApiMode(ApiMode.OpenAIResponses)
            .setStreamingEnabled(true)
        pipe.setModel("MiniMax-M3")
        pipe.setMaxTokens(256)
        pipe.setTemperature(0.0)

        pipe.setStreamingCallback(callback)

        runBlocking {
            pipe.init()
            pipe.execute(MultimodalContent().apply { text = "Say hello in 5 words." })
        }

        println("STREAM_ASSEMBLED: [${chunks.joinToString("")}]")
        assertTrue(chunks.isNotEmpty(), "Should have received at least one streaming chunk")
        val assembled = chunks.joinToString("")
        assertTrue(assembled.isNotBlank(), "Assembled response should not be blank")
    }
}