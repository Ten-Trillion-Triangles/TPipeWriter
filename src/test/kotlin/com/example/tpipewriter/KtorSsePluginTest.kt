package com.example.tpipewriter

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Test using Ktor's SSE plugin which is designed for streaming event consumption.
 * Available in Ktor 3.2+.
 */
class KtorSsePluginTest {
    @Test
    fun testSsePluginStreamsIncrementally() {
        val apiKey = System.getenv("MINIMAX_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("AUXILIARY_VISION_API_KEY")?.takeIf { it.isNotBlank() }
            ?: return
        assumeTrue(apiKey != null, "MINIMAX_API_KEY or AUXILIARY_VISION_API_KEY not set")

        val client = HttpClient(CIO) {
            install(SSE) {
                showCommentEvents()
                showRetryEvents()
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
            }
        }

        val payload = """
            {
                "model": "MiniMax-M3",
                "input": [{"role": "user", "content": "Write a long detailed 200-word poem about the sea with vivid sensory details."}],
                "stream": true,
                "max_tokens": 500
            }
        """.trimIndent()

        val timings = mutableListOf<Long>()
        val chunks = mutableListOf<String>()
        val startNanos = System.nanoTime()

        runBlocking {
            client.sse(
                urlString = "https://api.minimax.io/v1/responses",
                request = {
                    method = io.ktor.http.HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(payload)
                }
            ) {
                incoming.collect { event ->
                    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                    timings.add(elapsedMs)
                    val data = event.data
                    if (data != null) {
                        val deltaMatch = Regex("\"delta\":\"([^\"]*)\"").find(data)
                        if (deltaMatch != null) {
                            val delta = deltaMatch.groupValues[1].replace("\\n", "\n")
                            chunks.add(delta)
                            println("SSE_PLUGIN_CHUNK: +${elapsedMs}ms size=${delta.length}")
                        }
                    }
                }
            }
        }

        println("SSE_PLUGIN_TOTAL: chunks=${chunks.size} total_ms=${(System.nanoTime() - startNanos) / 1_000_000}")
        println("SSE_PLUGIN_TIMINGS: $timings")
        if (chunks.size >= 2) {
            for (i in 1 until chunks.size) {
                val gap = timings[i] - timings[i - 1]
                println("SSE_PLUGIN_GAP: chunk[$i] (t=${timings[i]}) arrived ${gap}ms after chunk[${i-1}] (t=${timings[i-1]})")
            }
        }
        client.close()
    }
}