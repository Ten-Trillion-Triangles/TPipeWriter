package Shell

import Globals.Env
import Util.enablePipelineStreaming
import com.TTT.Context.ContextBank
import com.TTT.Debug.TraceFormat
import com.TTT.Pipe.MultimodalContent
import com.TTT.Util.getHomeFolder
import com.TTT.Util.writeStringToFile
import kotlinx.coroutines.runBlocking
import readEnhancedInput


fun callPitchSubShell()
{
    println("\n\nEnter your command to the pitch writer")
    val userPrompt = readEnhancedInput()

    Env.pitchSlideWriterPipeline.enableTracing()
    enablePipelineStreaming(Env.pitchSlideWriterPipeline)

    try{
        runBlocking {
            val result = Env.pitchSlideWriterPipeline.execute(MultimodalContent(text = userPrompt))
            println("\n\n\n============================================Results========================================")
            println(ContextBank.getContextFromBank("new page").contextElements[0])
        }
    }

    catch (e: Exception)
    {
        println(e)
    }

    val trace = Env.pitchSlideWriterPipeline.getTraceReport(TraceFormat.HTML)
    writeStringToFile("${getHomeFolder()}/TPipeWriter/PitchTrace.html", trace)

}