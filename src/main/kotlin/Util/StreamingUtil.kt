package Util

import com.TTT.Pipeline.Pipeline
import genericOpenAIPipe.GenericOpenAIPipe

/**
 * Enables streaming on all GenericOpenAIPipe pipes in a pipeline with real-time
 * screen output.
 *
 * MiniMax-M3 Generic OpenAI edition: every pipe is a GenericOpenAIPipe. Each
 * pipe's `setStreamingEnabled(true)` flips the SSE flag on the wire, and
 * `setStreamingCallback(...)` routes chunks to the terminal print-and-flush
 * callback. The legacy Bedrock-specific `enableStreaming(callback, true)` form
 * has been replaced with the typed `GenericOpenAIPipe` setters so the stream
 * is actually wired (the previous version of this file was a no-op that just
 * counted pipes).
 */
fun enablePipelineStreaming(pipeline: Pipeline) {
    val callback: suspend (String) -> Unit = { chunk ->
        print(chunk)
        System.out.flush()
    }

    var enabledCount = 0
    pipeline.getPipes().forEach { pipe ->
        if (pipe is GenericOpenAIPipe) {
            pipe.setStreamingEnabled(true)
            pipe.setStreamingCallback(callback)
            enabledCount++
        }
    }
    println("Streaming enabled on $enabledCount pipes")
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