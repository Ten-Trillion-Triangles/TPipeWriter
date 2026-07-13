package Builders

import com.TTT.Pipe.MultimodalContent
import com.TTT.Util.serialize
import genericOpenAIPipe.env.GenericOpenAIEnv
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import Structs.LorebookExtraction
import Globals.recordLoreBook

/**
 * Pins the no-throw contract of the PlusWriterPipeline loreBookPipe:
 *  - bad-JSON LLM output must not propagate as an exception
 *  - on-failure synthesis of empty LorebookExtraction() keeps the pipeline alive
 *  - the transformation function still writes an empty-merge result to the bank
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlusWriterLorebookAgentSmokeTest
{
    companion object
    {
        @JvmStatic
        @BeforeAll
        fun setUpApiKey()
        {
            GenericOpenAIEnv.setApiKey("sk-test-fake-for-lorebook-smoke-test-only")
        }
    }

    @Test
    fun recordLoreBookHandlesBadJsonGracefully()
    {
        val bad = MultimodalContent().apply { text = "this is not json at all, just prose" }

        assertDoesNotThrow {
            runBlocking {
                recordLoreBook(bad)
            }
        }
    }

    @Test
    fun recordLoreBookHandlesEmptyExtraction()
    {
        val empty = MultimodalContent().apply { text = serialize(LorebookExtraction()) }

        val result = runBlocking {
            recordLoreBook(empty)
        }

        assertNotNull(result.context)
    }
}
