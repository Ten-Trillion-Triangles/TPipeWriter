package Shell

import com.TTT.Util.getHomeFolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * TDD red test for [savePipesState] / [loadPipesState].
 *
 * The sidecar file lives at `~/TPipeWriter/$filename-pipes.json` (mirrors
 * the existing settings.json / lorebook.json / story.json export pattern).
 * The save/load functions take a filename stem (no extension) and resolve
 * the full path themselves via [getHomeFolder].
 *
 * Pure reflection on disk state. Tests use a unique filename per test
 * (to avoid collisions) and clean up in AfterEach.
 */
class PipesStateFileTest
{
    private val testFilenames = mutableListOf<String>()

    @BeforeEach
    fun setup() {
        // Ensure the target directory exists so savePipesState can write to it
        File("${getHomeFolder()}/TPipeWriter").mkdirs()
    }

    @AfterEach
    fun cleanup() {
        // Remove any test sidecar files we created
        for (filename in testFilenames) {
            val file = File("${getHomeFolder()}/TPipeWriter/$filename-pipes.json")
            if (file.exists()) file.delete()
        }
        testFilenames.clear()
    }

    private fun reserveFilename(stem: String): String {
        val unique = "test-${stem}-${System.currentTimeMillis()}"
        testFilenames.add(unique)
        return unique
    }

    @Test
    fun savePipesStateWritesFileToExpectedPath() {
        val filename = reserveFilename("save-path")
        val state = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe", "no parallel negation pipe"))
        )

        savePipesState(filename, state)

        val expectedFile = File("${getHomeFolder()}/TPipeWriter/$filename-pipes.json")
        assertTrue(
            expectedFile.exists(),
            "Expected sidecar file at ${expectedFile.absolutePath} but it does not exist"
        )
        assertTrue(
            expectedFile.length() > 0,
            "Saved sidecar file is empty"
        )
    }

    @Test
    fun savedFileContainsSerializedJson() {
        val filename = reserveFilename("save-content")
        val state = DisabledPipesState(
            mapOf("plusWriter" to setOf("untwist pipe"))
        )

        savePipesState(filename, state)

        val content = File("${getHomeFolder()}/TPipeWriter/$filename-pipes.json").readText()
        assertTrue(
            content.contains("untwist pipe"),
            "Saved file should contain the disabled pipe name. Got: $content"
        )
        assertTrue(
            content.contains("plusWriter"),
            "Saved file should contain the pipeline name. Got: $content"
        )
    }

    @Test
    fun loadPipesStateReadsBackSavedState() {
        val filename = reserveFilename("load-roundtrip")
        val original = DisabledPipesState(
            mapOf(
                "plusWriter" to setOf("untwist pipe", "no parallel negation pipe"),
                "chapterRewrite" to setOf("removeBadWritingStepOnePipe")
            )
        )

        savePipesState(filename, original)
        val restored = loadPipesState(filename)

        assertEquals(
            original.disabledPipes,
            restored?.disabledPipes,
            "Saved and loaded state should match"
        )
    }

    @Test
    fun loadPipesStateReturnsNullWhenFileAbsent() {
        val filename = reserveFilename("absent")
        val restored = loadPipesState(filename)
        assertEquals(null, restored, "Loading a non-existent file should return null")
    }

    @Test
    fun emptyStateSavesAndLoads() {
        val filename = reserveFilename("empty")
        val original = DisabledPipesState.EMPTY

        savePipesState(filename, original)
        val restored = loadPipesState(filename)

        assertEquals(
            original.disabledPipes,
            restored?.disabledPipes,
            "Empty state should round-trip"
        )
    }
}
