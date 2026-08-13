package Shell

import com.TTT.Util.serialize
import com.TTT.Util.deserialize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * TDD red test for [DisabledPipesState] round-trip.
 *
 * The data class carries the disabled-pipes set per project. It is keyed
 * by pipeline name so the persisted state can contain multiple pipelines'
 * disable sets even though the /pipes subshell currently only edits one
 * at a time (the active writer pipeline).
 *
 * This test pins the JSON serialization contract using the project
 * pattern (com.TTT.Util.serialize / deserialize).
 *
 * Pure reflection. No init, no network, no file IO.
 */
class DisabledPipesStateTest
{
    @Test
    fun emptyStateRoundTripsAsEmpty() {
        val state = DisabledPipesState(emptyMap())
        val json = serialize(state)
        val restored = deserialize<DisabledPipesState>(json)
        assertEquals(emptyMap<String, Set<String>>(), restored?.disabledPipes)
    }

    @Test
    fun singlePipelineSingleDisabledPipeRoundTrips() {
        val state = DisabledPipesState(mapOf("plusWriter" to setOf("untwist pipe")))
        val json = serialize(state)
        val restored = deserialize<DisabledPipesState>(json)
        assertEquals(setOf("untwist pipe"), restored?.disabledPipes?.get("plusWriter"))
    }

    @Test
    fun multiplePipelinesMultiplePipesRoundTrip() {
        val state = DisabledPipesState(
            mapOf(
                "plusWriter" to setOf("untwist pipe", "no parallel negation pipe"),
                "chapterRewrite" to setOf("removeBadWritingStepOnePipe"),
                "expansionPipeline" to emptySet()
            )
        )
        val json = serialize(state)
        val restored = deserialize<DisabledPipesState>(json)
        assertEquals(
            setOf("untwist pipe", "no parallel negation pipe"),
            restored?.disabledPipes?.get("plusWriter")
        )
        assertEquals(
            setOf("removeBadWritingStepOnePipe"),
            restored?.disabledPipes?.get("chapterRewrite")
        )
        assertEquals(
            emptySet<String>(),
            restored?.disabledPipes?.get("expansionPipeline")
        )
    }

    @Test
    fun emptyStateProducesNonEmptyJson() {
        val state = DisabledPipesState(emptyMap())
        val json = serialize(state)
        assertTrue(json.isNotBlank(), "Serialized JSON should be non-blank")
        assertTrue(json.contains("{"), "Serialized JSON should be an object")
        assertTrue(json.contains("}"), "Serialized JSON should be an object")
    }

    @Test
    fun deserializeOfNullReturnsNull() {
        // Defensive: passing null/empty to the project's deserialize should
        // not crash. The Kotlinx serializer returns null for empty input.
        val restored = deserialize<DisabledPipesState>("")
        assertEquals(null, restored)
    }
}
