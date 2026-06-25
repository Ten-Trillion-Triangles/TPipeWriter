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
        // Streaming callback already wrote chunks to stdout. Print a marker
        // so the user sees the run completed and where the result landed.
        println("\n\n[results]========================================")
        val newPage = ContextBank.getContextFromBank("new page").contextElements
        if (newPage.isNotEmpty()) {
            println(newPage[0])
        }
    }
    catch (e: Exception)
    {
        println(e)
    }
}