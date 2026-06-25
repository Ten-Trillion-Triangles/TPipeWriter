package Util

import com.TTT.Pipeline.Pipeline
import genericOpenAIPipe.GenericOpenAIPipe
import java.io.FileDescriptor
import java.io.FileOutputStream

/**
 * Enables streaming on all GenericOpenAIPipe pipes in a pipeline with real-time
 * screen output.
 *
 * MiniMax-M3 Generic OpenAI edition: every pipe is a GenericOpenAIPipe. Each
 * pipe's `setStreamingEnabled(true)` flips the SSE flag on the wire, and
 * `setStreamingCallback(...)` routes chunks to the terminal in real time.
 *
 * **Why FileDescriptor.out instead of System.out.print:**
 * Java's `System.out` is a `PrintStream` that buffers stdout when stdout is
 * connected to a TTY (it waits for a newline before flushing the line buffer).
 * Streaming chunks from the API have no newlines (they're JSON delta tokens),
 * so they accumulate in the buffer until something else writes a newline —
 * which is why my earlier `print(chunk) + System.out.flush()` showed the full
 * response all at once instead of chunk-by-chunk.
 *
 * Writing directly to `FileDescriptor.out` (fd 1) bypasses the PrintStream
 * buffer entirely. Each `flush()` immediately pushes bytes to the tmux pane /
 * terminal. Verified: StreamTest3 with FileDescriptor.out streams chunk-by-chunk
 * with 1-second delays visible in real time. StreamTest using `System.out.print`
 * produces all chunks at once after the loop completes.
 *
 * The FileOutputStream is shared across all pipe callbacks (it's process-wide
 * stdout). We keep one reference and never close it — closing FileDescriptor.out
 * would permanently break stdout for the rest of the JVM.
 */
fun enablePipelineStreaming(pipeline: Pipeline) {
    val rawStdout = FileOutputStream(FileDescriptor.out)
    var totalChunks = 0
    var totalChars = 0

    val callback: suspend (String) -> Unit = { chunk ->
        if (chunk.isNotEmpty()) {
            totalChunks++
            totalChars += chunk.length
            rawStdout.write(chunk.toByteArray(Charsets.UTF_8))
            rawStdout.flush()
        }
    }

    var enabledCount = 0
    pipeline.getPipes().forEach { pipe ->
        if (pipe is GenericOpenAIPipe) {
            pipe.setStreamingEnabled(true)
            pipe.setStreamingCallback(callback)
            enabledCount++
        }
    }
    println("Streaming enabled on $enabledCount pipes (callbacks registered; chunks will appear in real time)")

    // Attach a shutdown hook to print final streaming stats when the JVM exits
    Runtime.getRuntime().addShutdownHook(Thread {
        println("[streaming] Total chunks emitted: $totalChunks, total chars: $totalChars")
    })
}

/**
 * Disables streaming on all GenericOpenAIPipe pipes in a pipeline.
 */
fun disablePipelineStreaming(pipeline: Pipeline) {
    pipeline.getPipes().forEach { pipe ->
        if (pipe is GenericOpenAIPipe) {
            pipe.setStreamingEnabled(false)
        }
    }
}