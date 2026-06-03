package Util

import com.TTT.Pipeline.Pipeline
import Globals.discussionStreamingChunk
import openrouterPipe.OpenRouterPipe

/**
 * Enable streaming on every OpenRouter pipe in the pipeline.
 *
 * Replaces the old Bedrock-specific `enableStreaming(callback, true)` + `disableStreaming()`
 * pattern with OpenRouter's `setStreamingCallback` / `setStreamingEnabled`.
 */
fun enablePipelineStreaming(pipeline: Pipeline) {
    pipeline.getPipes().forEach { pipe ->
        if (pipe is OpenRouterPipe) {
            pipe.setStreamingCallback(::discussionStreamingChunk)
        }
    }
}

/**
 * Disable streaming on every OpenRouter pipe in the pipeline.
 */
fun disablePipelineStreaming(pipeline: Pipeline) {
    pipeline.getPipes().forEach { pipe ->
        if (pipe is OpenRouterPipe) {
            pipe.setStreamingEnabled(false)
        }
    }
}
