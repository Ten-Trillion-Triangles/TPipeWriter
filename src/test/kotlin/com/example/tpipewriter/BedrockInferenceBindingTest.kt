package com.example.tpipewriter

import bedrockPipe.BedrockPipe
import env.bedrockEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BedrockInferenceBindingTest
{
    @TempDir
    lateinit var tempDir: File

    private lateinit var configFile: File

    @BeforeEach
    fun setUp()
    {
        configFile = File(tempDir, "inference.txt")
        configFile.writeText(
            """
            deepseek.r1-v1:0=arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0
            amazon.nova-pro-v1:0=arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-pro-v1:0
            amazon.nova-lite-v1:0=arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-lite-v1:0
            openai.gpt-oss-20b-1:0=arn:aws:bedrock:us-west-2::foundation-model/openai.gpt-oss-20b-1:0
            openai.gpt-oss-120b-1:0=arn:aws:bedrock:us-west-2::foundation-model/openai.gpt-oss-120b-1:0
            deepseek.v3-v1:0=arn:aws:bedrock:us-west-2::foundation-model/deepseek.v3-v1:0
            us.meta.llama4-maverick-17b-instruct-v1:0=arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama4-maverick-17b-instruct-v1:0
            us.meta.llama3-3-70b-instruct-v1:0=arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-3-70b-instruct-v1:0
            us.meta.llama3-1-405b-instruct-v1:0=arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-1-405b-instruct-v1:0
            amazon.nova-2-lite-v1:0=arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-2-lite-v1:0
            qwen.qwen3-coder-480b-a35b-v1:0=arn:aws:bedrock:us-west-2::foundation-model/qwen.qwen3-coder-480b-a35b-v1:0
            writer.palmyra-x5-v1:0=arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.writer.palmyra-x5-v1:0
            """.trimIndent()
        )

        bedrockEnv.resetInferenceConfig()
        bedrockEnv.setInferenceConfigFile(configFile)
        bedrockEnv.loadInferenceConfig()
    }

    @AfterEach
    fun tearDown()
    {
        bedrockEnv.resetInferenceConfig()
    }

    @Test
    fun loadInferenceConfigMapsScriptBindings()
    {
        assertEquals(
            "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0",
            bedrockEnv.getInferenceProfileId("deepseek.r1-v1:0")
        )
        assertEquals(
            "arn:aws:bedrock:us-west-2::foundation-model/deepseek.v3-v1:0",
            bedrockEnv.getInferenceProfileId("deepseek.v3-v1:0")
        )
        assertEquals(
            "arn:aws:bedrock:us-west-2::foundation-model/qwen.qwen3-coder-480b-a35b-v1:0",
            bedrockEnv.getInferenceProfileId("qwen.qwen3-coder-480b-a35b-v1:0")
        )
        assertEquals(
            "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.writer.palmyra-x5-v1:0",
            bedrockEnv.getInferenceProfileId("writer.palmyra-x5-v1:0")
        )

        assertTrue(
            bedrockEnv.getAllModels().containsAll(
                listOf(
                    "deepseek.r1-v1:0",
                    "amazon.nova-pro-v1:0",
                    "amazon.nova-lite-v1:0",
                    "openai.gpt-oss-20b-1:0",
                    "openai.gpt-oss-120b-1:0",
                    "deepseek.v3-v1:0",
                    "us.meta.llama4-maverick-17b-instruct-v1:0",
                    "us.meta.llama3-3-70b-instruct-v1:0",
                    "us.meta.llama3-1-405b-instruct-v1:0",
                    "qwen.qwen3-coder-480b-a35b-v1:0",
                    "writer.palmyra-x5-v1:0"
                )
            ),
            "The inference loader should populate every model ID that the helper script binds"
        )
    }

    @Test
    fun bedrockPipeInitRewritesModelToLoadedArn()
    {
        runBlocking(Dispatchers.IO)
        {
            val profilePipe = BedrockPipe()
            profilePipe.setModel("deepseek.r1-v1:0")
            profilePipe.setRegion("us-east-2")
            profilePipe.init()

            assertEquals(
                "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0",
                profilePipe.getModelName(),
                "BedrockPipe.init() should swap the requested model ID for the loaded inference profile ARN"
            )

            val foundationPipe = BedrockPipe()
            foundationPipe.setModel("qwen.qwen3-coder-480b-a35b-v1:0")
            foundationPipe.setRegion("us-west-2")
            foundationPipe.init()

            assertEquals(
                "arn:aws:bedrock:us-west-2::foundation-model/qwen.qwen3-coder-480b-a35b-v1:0",
                foundationPipe.getModelName(),
                "BedrockPipe.init() should swap a direct model ID for the loaded foundation-model ARN"
            )
        }
    }

    @Test
    fun novaRequestBuilderKeepsLogicalModelFamilyAfterArnRewrite()
    {
        runBlocking(Dispatchers.IO)
        {
            val pipe = BedrockPipe()
            pipe.setModel("amazon.nova-2-lite-v1:0")
            pipe.setRegion("us-east-2")
            pipe.setReasoning("high")
            pipe.setMaxTokens(123)

            pipe.init()

            assertEquals(
                "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-2-lite-v1:0",
                pipe.getModelName(),
                "BedrockPipe.init() should rewrite the stored model to the loaded ARN"
            )

            val requestJson = Json.parseToJsonElement(pipe.buildNovaRequest("Explain recursion.")).jsonObject
            val inferenceConfig = requestJson["inferenceConfig"]!!.jsonObject
            val reasoningConfig = inferenceConfig["reasoningConfig"]!!.jsonObject

            assertEquals(
                "enabled",
                reasoningConfig["type"]!!.jsonPrimitive.content,
                "Nova reasoning should stay enabled when the logical model family is preserved"
            )
            assertEquals(
                "high",
                reasoningConfig["maxReasoningEffort"]!!.jsonPrimitive.content,
                "Nova reasoning effort should come from the logical Nova model family"
            )
            assertFalse(
                inferenceConfig.containsKey("maxTokens"),
                "High-reasoning Nova models should suppress maxTokens in the request body"
            )
        }
    }
}
