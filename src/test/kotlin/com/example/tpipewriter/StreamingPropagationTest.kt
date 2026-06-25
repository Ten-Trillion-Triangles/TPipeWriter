package com.example.tpipewriter

import com.TTT.Pipe.Pipe
import com.TTT.Pipe.MultimodalContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Unit tests for streaming-callback propagation in TPipe's Pipe hierarchy.
 *
 * Regression coverage for the bug where the reasoning pipe's API call did
 * NOT stream chunks to the parent pipe's callback. The root cause was that
 * each pipe has its own streaming-callback manager; without propagation, a
 * callback registered on a parent pipe was silently ignored when a child
 * pipe's API call streamed. Fix: Pipe.propagateStreamingCallback walks the
 * pipe tree and adds the callback to every descendant.
 *
 * Note: the streaming API (setStreamingCallback, emitStreamingChunk) is
 * defined on [genericOpenAIPipe.GenericOpenAIPipe] rather than the base
 * [Pipe]. For testing the BASE Pipe propagation logic, we access the
 * callback manager via [obtainStreamingCallbackManager] (which IS on the
 * base Pipe) and trigger it via the protected emit method through the
 * callback manager's emitToAll. The propagation behavior itself is the
 * Pipe-level concern under test.
 */
class StreamingPropagationTest {

    /**
     * Minimal pipe implementation. Implements the two abstract methods from
     * the Pipe base class (truncateModuleContext, generateText) plus
     * generateContent. We don't override setStreamingCallback — we test
     * propagation via the manager directly.
     */
    private class TestPipe(val label: String) : Pipe() {
        override suspend fun generateContent(content: MultimodalContent): MultimodalContent {
            return content
        }
        override suspend fun generateText(promptInjector: String): String = promptInjector
        override fun truncateModuleContext(): Pipe = this
    }

    /**
     * Test helper: emit a chunk by invoking emitStreamingChunk on the pipe.
     * The base Pipe's emitStreamingChunk is protected and calls the manager's
     * emitToAll — this is exactly what GenericOpenAIPipe subclasses call when
     * they receive an SSE chunk. Uses reflection to bypass the protected
     * visibility from the test class.
     */
    private fun Pipe.emitChunkSync(chunk: String) {
        // emitStreamingChunk is protected and suspend. We can't easily call
        // it via reflection (suspend functions compile to a different
        // signature). Instead, drive the manager directly via the
        // StreamingCallbackManager API. The manager has a public emitToAll
        // method that takes a suspend lambda; wrap in runBlocking.
        val mgr = this.obtainStreamingCallbackManager()
        // Access the private 'callbacks' list via reflection to invoke each
        // callback directly. This avoids needing the suspend manager API.
        val callbacksField = mgr::class.java.getDeclaredField("callbacks")
        callbacksField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val callbacks = callbacksField.get(mgr) as List<suspend (String) -> Unit>
        kotlinx.coroutines.runBlocking {
            for(cb in callbacks) cb(chunk)
        }
    }

    private fun newCallback(received: MutableList<String>): suspend (String) -> Unit {
        return { chunk: String -> received.add(chunk) }
    }

    @Test
    fun callbackFiresForOwnPipeChunks() {
        val pipe = TestPipe("root")
        val received = mutableListOf<String>()
        pipe.obtainStreamingCallbackManager().addCallback(newCallback(received))

        pipe.emitChunkSync("hello ")
        pipe.emitChunkSync("world")

        assertEquals(listOf("hello ", "world"), received)
    }

    @Test
    fun callbackFiresForChildPipeChunksAfterPropagation() {
        val parent = TestPipe("parent")
        val child = TestPipe("child")
        parent.setTransformationPipe(child)

        val received = mutableListOf<String>()
        val cb: suspend (String) -> Unit = newCallback(received)
        parent.propagateStreamingCallback(cb)

        // Emit on the CHILD — the callback should fire because propagation
        // registered it on the child. Without propagation this assertion
        // fails because the child's manager has no callbacks.
        child.emitChunkSync("from child")

        assertEquals(listOf("from child"), received)
    }

    @Test
    fun callbackPropagatesToAllDescendants() {
        val parent = TestPipe("parent")
        val validator = TestPipe("validator")
        val transformation = TestPipe("transformation")
        val branch = TestPipe("branch")
        val reasoning = TestPipe("reasoning")
        parent.setValidatorPipe(validator)
        parent.setTransformationPipe(transformation)
        parent setBranchPipe branch
        parent.setReasoningPipe(reasoning)

        val received = mutableListOf<String>()
        parent.propagateStreamingCallback(newCallback(received))

        validator.emitChunkSync("v")
        transformation.emitChunkSync("t")
        branch.emitChunkSync("b")
        reasoning.emitChunkSync("r")

        assertEquals(4, received.size)
        assertEquals(listOf("v", "t", "b", "r"), received.toList())
    }

    @Test
    fun childAttachedAfterParentCallbackInheritsIt() {
        val parent = TestPipe("parent")
        val received = mutableListOf<String>()
        parent.propagateStreamingCallback(newCallback(received))

        // Attach child AFTER the parent already has a callback. The setter
        // should propagate the parent's callbacks to the new child.
        val child = TestPipe("child")
        parent.setTransformationPipe(child)

        child.emitChunkSync("late child")

        assertEquals(listOf("late child"), received)
    }

    @Test
    fun addingSameCallbackTwiceDoesNotDuplicateOutput() {
        val pipe = TestPipe("root")
        val received = mutableListOf<String>()
        val cb: suspend (String) -> Unit = newCallback(received)
        pipe.obtainStreamingCallbackManager().addCallback(cb)
        pipe.obtainStreamingCallbackManager().addCallback(cb)

        pipe.emitChunkSync("once")

        assertEquals(listOf("once"), received)
    }

    @Test
    fun reasoningPipeStreamsAfterCallbackSetOnParent() {
        // The actual scenario that motivated this fix: MiniMax-M3 chat
        // pipelines have a "Chat Pipe" with a "Thinking Pipe" reasoning
        // child. When the user sets a streaming callback on the Chat Pipe,
        // the Thinking Pipe's SSE chunks from /v1/responses MUST also flow
        // through that callback — otherwise the reasoning content (often
        // longer than the final answer) is invisible to the user.
        val chatPipe = TestPipe("chat")
        val thinkingPipe = TestPipe("thinking")
        chatPipe.setReasoningPipe(thinkingPipe)

        val received = mutableListOf<String>()
        chatPipe.propagateStreamingCallback(newCallback(received))

        thinkingPipe.emitChunkSync("reasoning chunk")
        chatPipe.emitChunkSync("final chunk")

        assertEquals(listOf("reasoning chunk", "final chunk"), received)
    }

    @Test
    fun cycleInPipeTreeDoesNotInfiniteLoop() {
        // Synthesize a cycle: parent -> child -> grandchild where grandchild
        // has parent as its validator (nonsensical but possible if a builder
        // makes a mistake). The propagation should detect cycles via the
        // visited set and not loop forever.
        val parent = TestPipe("p")
        val child = TestPipe("c")
        val grandchild = TestPipe("g")
        parent.setTransformationPipe(child)
        child.setReasoningPipe(grandchild)
        grandchild.setValidatorPipe(parent) // cycle

        val received = mutableListOf<String>()
        // Should not hang.
        parent.propagateStreamingCallback(newCallback(received))

        parent.emitChunkSync("p")
        child.emitChunkSync("c")
        grandchild.emitChunkSync("g")

        assertEquals(3, received.size)
    }
}