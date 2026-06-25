package com.example.tpipewriter

import Globals.ModelConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MiniMaxModelConfigTest {
    @Test
    fun `deepseek model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.deepseekModelName)
    }

    @Test
    fun `claude model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.claudeModelName)
    }

    @Test
    fun `nova model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.novaModelName)
    }

    @Test
    fun `novaPro model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.novaProModelName)
    }

    @Test
    fun `gptOss model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.gptOssModelName)
    }

    @Test
    fun `gptOss120b model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.gptOss120bModelName)
    }

    @Test
    fun `llamaMaverick resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.llamaMaverick)
    }

    @Test
    fun `llama70B resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.llama70B)
    }

    @Test
    fun `llama405B resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.llama405B)
    }

    @Test
    fun `jamba model name resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.jambaModelName)
    }

    @Test
    fun `deepseekV31 resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.deepseekV31)
    }

    @Test
    fun `qwen235B resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.qwen235B)
    }

    @Test
    fun `qwen32B resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.qwen32B)
    }

    @Test
    fun `qwenCoder480B resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.qwenCoder480B)
    }

    @Test
    fun `qwenCoder30B resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.qwenCoder30B)
    }

    @Test
    fun `qwenNext80B resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.qwenNext80B)
    }

    @Test
    fun `qwenVL resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.qwenVL)
    }

    @Test
    fun `PalmyraX5 resolves to MiniMax-M3`() {
        assertEquals("MiniMax-M3", ModelConfig.PalmyraX5)
    }

    @Test
    fun `init is no-op — does not throw`() {
        // MiniMax is a hosted model on api.minimax.io. No ARN binding, no region,
        // no inference profile. The init() call should succeed silently.
        ModelConfig.init()
    }
}