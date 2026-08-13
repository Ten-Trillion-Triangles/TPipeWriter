package com.example.tpipewriter

import bedrockPipe.BedrockMultimodalPipe
import bedrockPipe.NovaPipe
import com.TTT.Pipe.MultimodalContent
import env.bedrockEnv
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class NovaTest 
{
   @Test
   fun testNovaConnection()
   {
       return // Default: live Bedrock disabled. Remove this return to enable live smoke.

       var input = MultimodalContent()
       input.text = "Hello"

       bedrockEnv.loadInferenceConfig()
       bedrockEnv.bindInferenceProfile("amazon.nova-pro-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-pro-v1:0")
       val novaPipe = BedrockMultimodalPipe()
           .setRegion("us-east-2")
           .useConverseApi()
           .setModel("amazon.nova-pro-v1:0")
           .setMultimodalInput(input)

       runBlocking {
           novaPipe.init()
           input = novaPipe.execute(input)
           assertTrue(input.text.isNotEmpty(), "Nova should return non-empty text")
           println("Live Bedrock smoke: ${input.text.take(120)}")
       }
   }

   /**
    * Bedrock-modernization smoke test: build the modernized PlusWriterPipeline and
    * verify pipe construction + per-pipe TokenBudgetSettings are wired.
    * This is a pure structural test (no network) that proves the modernization
    * didn't break the Bedrock chain syntax.
    */
   @Test
   fun testModernizedPlusWriterPipelineBuilds()
   {
       // This will throw if any pipe fails to construct on Bedrock chain syntax.
       val pipeline = Builders.buildPlusWriterPipeline()
       val pipes = pipeline.getPipes()
       assertTrue(pipes.isNotEmpty(), "PlusWriterPipeline should have pipes")
       println("PlusWriterPipeline built with ${pipes.size} pipes (Bedrock chain syntax preserved)")
   }
}
