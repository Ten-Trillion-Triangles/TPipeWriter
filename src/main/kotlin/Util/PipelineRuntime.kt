package Util

import com.TTT.Pipeline.Pipeline
import genericOpenAIPipe.GenericOpenAIPipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.FileDescriptor
import java.io.FileOutputStream

/**
 * Single source of truth for streaming output and live trace flushing.
 *
 * Every command handler in Shell.kt / WriterSubshell.kt / CharacterChatSubshell.kt
 * / PtichSubshell.kt previously called pipeline.execute(...) directly. Each one
 * had its own ad-hoc enableTracing() call, sometimes with autoExport, sometimes
 * not, and never with periodic flushing — so trace files were only written
 * AFTER the command completed. Streaming was either not enabled or registered
 * per-call without ever being invoked.
 *
 * This helper fixes both problems in one place:
 *
 * 1. **Streaming**: registers a single shared callback that writes every SSE
 *    chunk from every pipe in the pipeline directly to FD.out via
 *    FileOutputStream. This bypasses Java's PrintStream line buffering that
 *    was causing chunks to accumulate until a newline arrived. Verified
 *    chunk-by-chunk delivery via StreamTest3.
 *
 * 2. **Live trace flushing**: spawns a GlobalScope coroutine that writes the
 *    pipeline's trace to the requested file every 2 seconds while the execute
 *    is running. The user can `tail -f` or `watch` the file to monitor
 *    progress. Cancelled when the execute completes.
 *
 * Usage:
 * ```
 * val result = runWithLiveTrace(Env.discussionPipeline, "Trace.html") {
 *     Env.discussionPipeline.execute(input)
 * }
 * ```
 *
 * The block is run on the calling thread; the trace flush coroutine runs on
 * Dispatchers.IO. They run concurrently. After the block returns, we cancel
 * the flush job and write a final trace so the file always has the complete
 * post-execute state.
 */
private val rawStdout: FileOutputStream by lazy {
    // Process-wide stdout handle. Never close — closing FileDescriptor.out
    // permanently breaks stdout for the rest of the JVM (verified: the loop
    // exits with "Stream Closed" on the next write).
    FileOutputStream(FileDescriptor.out)
}

private val streamingCallback: suspend (String) -> Unit = { chunk ->
    if (chunk.isNotEmpty()) {
        rawStdout.write(chunk.toByteArray(Charsets.UTF_8))
        rawStdout.flush()
    }
}

/**
 * Run `block` with streaming callbacks registered on every pipe in the
 * pipeline AND a live trace file flushing to `traceFileName` (resolved
 * relative to `~/TPipeWriter/`).
 *
 * @param pipeline the pipeline whose pipes should stream
 * @param traceFileName output filename (no path), resolved to ~/TPipeWriter/
 * @param block the work to run, typically `pipeline.execute(...)`
 * @return whatever `block` returns
 */
fun <T> runWithLiveTrace(
    pipeline: Pipeline,
    traceFileName: String,
    block: () -> T
): T {
    // 1. Wire streaming on every GenericOpenAIPipe in the pipeline.
    //    setStreamingEnabled(true) sets the SSE flag for the wire request;
    //    setStreamingCallback() also sets the flag AND registers our callback.
    var streamedCount = 0
    pipeline.getPipes().forEach { pipe ->
        if (pipe is GenericOpenAIPipe) {
            pipe.setStreamingCallback(streamingCallback)
            streamedCount++
        }
    }

    // 2. Enable tracing so PipeTracer records events. autoExport=true is fine
    //    here but we ALSO do live flushes via getTraceReport (which is what the
    //    existing TPipeWriter code already uses for the post-execute write).
    pipeline.enableTracing(
        com.TTT.Debug.TraceConfig(
            detailLevel = com.TTT.Debug.TraceDetailLevel.DEBUG,
            outputFormat = com.TTT.Debug.TraceFormat.HTML
        )
    )

    // 3. Spawn the live trace flush on Dispatchers.IO. GlobalScope so the
    //    coroutine isn't tied to a structured concurrency scope that might
    //    wait for it.
    val tracePath = "${com.TTT.Util.getHomeFolder()}/TPipeWriter/$traceFileName"
    val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val flushJob: Job = flushScope.launch {
        while (isActive) {
            try {
                val trace = pipeline.getTraceReport(com.TTT.Debug.TraceFormat.HTML)
                com.TTT.Util.writeStringToFile(tracePath, trace)
            } catch (_: Exception) {
                // Best-effort flush. Swallow so a transient write failure
                // doesn't kill the flush loop and doesn't crash the user-
                // visible command.
            }
            delay(2000)
        }
    }

    // 4. Run the block on the calling thread (matches the existing
    //    `runBlocking { pipeline.execute(...) }` semantics that callers
    //    expect).
    val result: T = try {
        block()
    } finally {
        // Always cancel and write the final trace, even on exception.
        flushJob.cancel()
        try {
            val finalTrace = pipeline.getTraceReport(com.TTT.Debug.TraceFormat.HTML)
            com.TTT.Util.writeStringToFile(tracePath, finalTrace)
        } catch (_: Exception) {
            // Ignore — best effort.
        }
    }

    return result
}

/**
 * Same as [runWithLiveTrace] but for multiple pipelines in sequence (used by
 * Connector.execute). Each pipeline gets the same callback wired and a
 * separate trace flush. The trace file is shared (last writer wins) since
 * Connector merges them into a single trace pipeline id.
 */
fun <T> runWithLiveTraceAll(
    pipelines: List<Pipeline>,
    traceFileName: String,
    block: () -> T
): T {
    pipelines.forEach { pipeline ->
        pipeline.getPipes().forEach { pipe ->
            if (pipe is GenericOpenAIPipe) {
                pipe.setStreamingCallback(streamingCallback)
            }
        }
        pipeline.enableTracing(
            com.TTT.Debug.TraceConfig(
                detailLevel = com.TTT.Debug.TraceDetailLevel.DEBUG,
                outputFormat = com.TTT.Debug.TraceFormat.HTML
            )
        )
    }

    val tracePath = "${com.TTT.Util.getHomeFolder()}/TPipeWriter/$traceFileName"
    val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val flushJob: Job = flushScope.launch {
        while (isActive) {
            try {
                // For connectors, flush the first pipeline's trace (the
                // outer one — child pipes share its trace id).
                val pipeline = pipelines.firstOrNull() ?: break
                val trace = pipeline.getTraceReport(com.TTT.Debug.TraceFormat.HTML)
                com.TTT.Util.writeStringToFile(tracePath, trace)
            } catch (_: Exception) {
                // best-effort
            }
            delay(2000)
        }
    }

    val result: T = try {
        block()
    } finally {
        flushJob.cancel()
        try {
            val pipeline = pipelines.firstOrNull()
            if (pipeline != null) {
                val finalTrace = pipeline.getTraceReport(com.TTT.Debug.TraceFormat.HTML)
                com.TTT.Util.writeStringToFile(tracePath, finalTrace)
            }
        } catch (_: Exception) {
            // ignore
        }
    }
    return result
}