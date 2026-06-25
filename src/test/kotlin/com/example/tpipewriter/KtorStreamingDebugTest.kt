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

/**
 * Debug test — measures the time between successive readUTF8Line() calls
 * on a raw Ktor CIO stream to confirm whether chunks are arriving
 * incrementally or buffered until end-of-stream.
 */
class KtorStreamingDebugTest {
    @Test
    fun testMeasureChunkArrivalTiming() {
        val apiKey = System.getenv("MINIMAX_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("AUXILIARY_VISION_API_KEY")?.takeIf { it.isNotBlank() }
            ?: return
        assumeTrue(apiKey != null, "MINIMAX_API_KEY or AUXILIARY_VISION_API_KEY not set")

        val client = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
            engine {
                endpoint {
                    connectTimeout = 30_000
                    socketTimeout = 120_000
                    keepAliveTime = 60_000
                }
            }
        }

        val payload = """
            {
                "model": "MiniMax-M3",
                "input": [{"role": "user", "content": "Count to 10 slowly."}],
                "stream": true,
                "max_tokens": 200
            }
        """.trimIndent()

        val startNanos = System.nanoTime()
        val lineTimings = mutableListOf<Long>()
        val lineContents = mutableListOf<String>()

        runBlocking {
            val response: HttpResponse = client.post("https://api.minimax.io/v1/responses") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(payload)
            }
            println("DEBUG: response.headers.contentType = ${response.headers[HttpHeaders.ContentType]}")
            println("DEBUG: response.headers.contentLength = ${response.headers[HttpHeaders.ContentLength]}")
            println("DEBUG: response.headers.transferEncoding = ${response.headers[HttpHeaders.TransferEncoding]}")
            println("DEBUG: response.version = ${response.version}")

            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                lineTimings.add(elapsedMs)
                if (line.length > 0) {
                    lineContents.add(line)
                }
            }
        }

        println("DEBUG: total ${lineTimings.size} lines, ${lineContents.size} non-empty")
        if (lineTimings.size >= 2) {
            for (i in 1 until lineTimings.size) {
                val gap = lineTimings[i] - lineTimings[i - 1]
                println("DEBUG: line[$i] at +${lineTimings[i]}ms (gap=${gap}ms)")
            }
        }

        client.close()
    }
}