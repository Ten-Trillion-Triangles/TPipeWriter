package Util

import bedrockPipe.BedrockMultimodalPipe
import bedrockPipe.BedrockPipe
import com.TTT.Pipeline.Pipeline

/**
 * Enables streaming on all Bedrock pipes in a pipeline with real-time screen output.
 */
fun enablePipelineStreaming(pipeline: Pipeline) {
    val callback: (String) -> Unit = { chunk -> 
        print(chunk)
        System.out.flush()
    }
    
    var enabledCount = 0
    pipeline.getPipes().forEach { pipe ->
        when (pipe) {
            is BedrockMultimodalPipe -> {
                pipe.enableStreaming(callback, true)
                enabledCount++
            }
            is BedrockPipe -> {
                pipe.enableStreaming(callback, true)
                enabledCount++
            }
        }
    }
    println("Streaming enabled on $enabledCount pipes")
}

/**
 * Disables streaming on all Bedrock pipes in a pipeline.
 */
fun disablePipelineStreaming(pipeline: Pipeline) {
    pipeline.getPipes().forEach { pipe ->
        when (pipe) {
            is BedrockMultimodalPipe -> pipe.disableStreaming()
            is BedrockPipe -> pipe.disableStreaming()
        }
    }
}
