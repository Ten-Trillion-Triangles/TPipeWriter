package com.example.tpipewriter

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertTrue

/**
 * The lowest-level streaming test possible: uses java.net.HttpURLConnection
 * with chunked streaming to verify MiniMax-M3 actually streams. If this
 * works incrementally and Ktor/CIO doesn't, the bug is in Ktor — not in
 * MiniMax-M3 or in our pipe.
 */
class RawHttpStreamingTest {
    @Test
    fun testRawHttpUrlConnectionStreamsIncrementally() {
        val apiKey = System.getenv("MINIMAX_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("AUXILIARY_VISION_API_KEY")?.takeIf { it.isNotBlank() }
            ?: return
        assumeTrue(apiKey != null, "MINIMAX_API_KEY or AUXILIARY_VISION_API_KEY not set")

        val url = URL("https://api.minimax.io/v1/responses")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setChunkedStreamingMode(0)
        conn.setRequestProperty("Transfer-Encoding", "chunked")

        val payload = """
            {
                "model": "MiniMax-M3",
                "input": [{"role": "user", "content": "Write a 100 word poem about the sea."}],
                "stream": true,
                "max_tokens": 300
            }
        """.trimIndent()

        conn.outputStream.use { it.write(payload.toByteArray()) }

        val timings = mutableListOf<Long>()
        val chunks = mutableListOf<String>()
        val startNanos = System.nanoTime()

        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
                timings.add(elapsedMs)
                if (line.startsWith("data: ") && line.length > 6) {
                    val data = line.substring(6)
                    val deltaMatch = Regex("\"delta\":\"([^\"]*)\"").find(data)
                    if (deltaMatch != null) {
                        val delta = deltaMatch.groupValues[1].replace("\\n", "\n")
                        chunks.add(delta)
                        println("RAW_HTTP_CHUNK: +${elapsedMs}ms size=${delta.length} text='${delta.take(40)}'")
                    }
                }
            }
        }

        println("RAW_HTTP_TOTAL: chunks=${chunks.size} total_ms=${(System.nanoTime() - startNanos) / 1_000_000}")
        if (chunks.size >= 2) {
            for (i in 1 until chunks.size) {
                val gap = timings[i] - timings[i - 1]
                println("RAW_HTTP_GAP: chunk[$i] arrived ${gap}ms after chunk[${i-1}]")
            }
        }

        assertTrue(chunks.isNotEmpty(), "Should have received at least one delta")
    }
}