package Shell

import Globals.Env
import com.TTT.Context.ContextBank
import com.TTT.Pipe.MultimodalContent
import kotlinx.coroutines.runBlocking
import readEnhancedInput


fun callPitchSubShell()
{
    println("\n\nEnter your command to the pitch writer")
    val userPrompt = readEnhancedInput()

    println("Thinking...")

    try {
        Util.runWithLiveTrace(Env.pitchSlideWriterPipeline, "PitchTrace.html") {
            runBlocking {
                Env.pitchSlideWriterPipeline.execute(MultimodalContent(text = userPrompt))
            }
        }
        // Streaming callback wrote deltas to stdout. The pipeline's
        // transformation chain writes the final pitch text to the "new page"
        // ContextBank — print the banked result with a banner so the user
        // sees the canonical post-pipeline text. Mirrors the writer
        // subshell behavior and the main/OpenRouter branches.
        val textBarrier = "============================================Results========================================="
        val newPage = ContextBank.getContextFromBank("new page").contextElements
        if (newPage.isNotEmpty() && newPage.last().isNotBlank()) {
            println("\n\n\n$textBarrier\n\n${newPage.last()}")
        } else {
            println("\n\n$textBarrier\n\n[pitch] No content banked.")
        }
    }
    catch (e: Exception)
    {
        println(e)
    }
}