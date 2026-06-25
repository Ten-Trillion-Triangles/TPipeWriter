package com.example.tpipewriter

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Raw Ktor streaming test — verifies Ktor's CIO engine actually streams
 * the MiniMax-M3 SSE response incrementally, without the rest of the
 * GenericOpenAIPipe machinery in the way.
 *
 * If Ktor itself is fine, the GenericOpenAIPipe is the issue.
 * If Ktor is buffering, we need to look at HttpClient config.
 */
class RawKtorStreamingTest {
    @Test
    fun testRawKtorStreamsFromMiniMaxIncrementally() {
        val apiKey = System.getenv("MINIMAX_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("AUXILIARY_VISION_API_KEY")?.takeIf { it.isNotBlank() }
            ?: return // skip if neither set
        assumeTrue(apiKey != null, "MINIMAX_API_KEY or AUXILIARY_VISION_API_KEY not set; skipping")

        val client = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
        }

        val payload = """
            {
                "model": "MiniMax-M3",
                "input": [{"role": "user", "content": "Write a 100 word poem about the sea."}],
                "stream": true,
                "max_tokens": 300
            }
        """.trimIndent()

        val timings = mutableListOf<Long>()
        val chunks = mutableListOf<String>()
        val startNanos = System.nanoTime()

        runBlocking {
            val response: HttpResponse = client.post("https://api.minimax.io/v1/responses") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(payload)
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                timings.add(elapsedMs)
                if (line.startsWith("data: ") && line.length > 6) {
                    val data = line.substring(6)
                    // Extract delta from the OpenAI Responses format.
                    val deltaMatch = Regex("\"delta\":\"([^\"]*)\"").find(data)
                    if (deltaMatch != null) {
                        val delta = deltaMatch.groupValues[1].replace("\\n", "\n")
                        chunks.add(delta)
                        println("RAW_KTOR_CHUNK: +${elapsedMs}ms size=${delta.length} text='${delta.take(40)}'")
                    }
                }
            }
        }

        println("RAW_KTOR_TOTAL: chunks=${chunks.size} total_ms=${(System.nanoTime() - startNanos) / 1_000_000}")
        if (chunks.size >= 2) {
            for (i in 1 until chunks.size) {
                val gap = timings[i] - timings[i - 1]
                println("RAW_KTOR_GAP: chunk[$i] arrived ${gap}ms after chunk[${i-1}]")
            }
        }

        client.close()
        assertTrue(chunks.isNotEmpty(), "Should have received at least one delta")
    }
}