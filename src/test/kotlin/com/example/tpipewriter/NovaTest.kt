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

       return

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
           println(input.text)
       }
   }
}
