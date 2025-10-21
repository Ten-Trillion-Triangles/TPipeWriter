package Builders.Util


import Builders.RewriteStyleActions
import Globals.isValidGptOssResponse
import Structs.RewriteAnalysis
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Context.MiniBank
import com.TTT.Enums.ContextWindowSettings
import com.TTT.Pipe.MultimodalContent
import com.TTT.Util.constructPipeFromTemplate
import com.TTT.Util.deserialize
import com.TTT.Util.repairAndDeserialize
import kotlinx.coroutines.runBlocking

suspend fun storeRewritePlan(content: MultimodalContent): MultimodalContent
{
    val originalWindow = ContextWindow()
    originalWindow.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("rewritePlan", originalWindow)
    return content
}


/**
 * Cache and store the idea the rewrite pipe came up with before we proceed. We need to do this because the lore
 * checker might decide nothing is wrong and return false. If this is the case we'll need to replace the text
 * of the multimodal object with the original idea at the next transformation function.
 */
suspend fun loreCheckPreInvoke(content: MultimodalContent) : Boolean
{
    val loreCheckWindow = ContextWindow()
    loreCheckWindow.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("rewritePlanLore", loreCheckWindow)
    return false
}

/**
 * Safety check to ensure that our output from the lore checker is valid json and not junk or a refusal.
 */
suspend fun validateRewriteStyleActionsCheck(content: MultimodalContent) : Boolean
{
    if(!isValidGptOssResponse(content)) return false //Must not refuse.
    var loreCheck = deserialize<RewriteStyleActions>(content.text)
    if(loreCheck == null) loreCheck = repairAndDeserialize<RewriteStyleActions>(content.text)
    if(loreCheck == null) return false //Must be valid json.
    if(loreCheck.needsChanges && loreCheck.changesToMake.isEmpty()) return false //Must not contradict.
    return true
}

/**
 * Check the output of the lore checker pipe. If it's false, we need to pull the old idea and return it. Otherwise
 * let's proceed forward out of this.
 */
suspend fun loreCheckTransform(content: MultimodalContent) : MultimodalContent
{
    //Get old idea in case we need it.
    val oldRewriteIdea = ContextBank.getContextFromBank("rewritePlanLore")

    /**
     * If we're unable to restore this data if needed, this will result in a possible chance of passing junk
     * forward. As such we need to kill the pipeline right here if that occurs.
     */
    if(oldRewriteIdea.contextElements.isEmpty())
    {
        content.terminatePipeline = true
        return content
    }

    //Get result of the lore checker pipe as json.
    val result = deserialize<RewriteStyleActions>(content.text)

    //Fetch and return old result if the lore checker decided changes were not needed.
    if(result != null && !result.needsChanges)
    {
        return try {
            content.text = oldRewriteIdea.contextElements[0]
            content
        } catch (e: Exception) {
            content
        }
    }

    //Return forward if changes were needed.
    return content
}



suspend fun validateRewriteAnalysis(content: MultimodalContent): Boolean
{
    val analysis = deserialize<RewriteAnalysis>(content.text)
    return analysis != null && analysis.identifiedIssues.isNotEmpty()
}

suspend fun transformRewriteResult(content: MultimodalContent): MultimodalContent
{
    /**
     * Deal with gpt-oss refusing over the most mundane of things.
     */
    if(!isValidGptOssResponse(content))
    {
        content.terminatePipeline = true
        return content
    }

    val rewriteWindow = ContextWindow()
    rewriteWindow.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("rewrittenChapter", rewriteWindow)
    return content
}

/**
 * Check the writing style and pass its assessment forward. Kill the pipeline if gpt-oss decides to refuse.
 * Otherwise, move onto the next pipe.
 */
suspend fun checkWritingStyle(content: MultimodalContent): MultimodalContent
{
    //Gpt-oss attempted to refuse the order. Exit the pipeline.
    if(!isValidGptOssResponse(content))
    {
        content.terminatePipeline = true
        return content
    }

    //Style checker passed this as conforming. We can now exit the pipeline early.
    val actions = deserialize<RewriteStyleActions>(content.text)
    if(actions != null && !actions.needsChanges)
    {
        content.passPipeline = true
        return content
    }

    //Otherwise pass it forward to the next step.
   return content

}

/**
 * Pre validation function for the style suggest pipe. Chops the main story to 8K tokens to reduce waste tokens
 * that will not improve the style suggest pipe's ability to determine how to fix the style.
 */
fun styleSuggestPreValidate(context: MiniBank, content: MultimodalContent? = null) : MiniBank
{
    //Required for us to get the correct token settings for gpt-oss
    val bedrockExamplePipe = BedrockMultimodalPipe()
    bedrockExamplePipe.setModel("gpt-oss")
        .truncateModuleContext()

    //Pull the copied main context from the mini bank.
    val mainContext = context.contextMap["main"] ?: ContextWindow()

    //Chop out excess context since it wastes tokens and does not improve the example output.
    val truncationSettings = bedrockExamplePipe.getTruncationSettings()
    mainContext.selectAndTruncateContext("", 8000, ContextWindowSettings.TruncateTop, truncationSettings)

    //Emplace the mini bank again.
    context.contextMap["main"] = mainContext
    return context
}

/**
 * Transform the rewrite result into a context window
 */
suspend fun transformRewriteStyle(content: MultimodalContent): MultimodalContent
{
    if(!isValidGptOssResponse(content))
    {
        content.terminatePipeline = true
        return content
    }

    val newChapter = ContextWindow()
    newChapter.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("rewrittenChapter", newChapter)
    return content
}


/**
 * Constructor to make a templated deepseek version of any given pipe. This allows us to use this in a branch failure
 * in the event we have gotten a refusal from a model for any reason. Deepseek is far less likely to refuse and will
 * allow us to fix this unacceptable behavior for now until we can find a way to get more uncensored models into
 * available inferencing.
 */
fun replacePipeWithDeepseek(pipe: BedrockMultimodalPipe) : BedrockMultimodalPipe?
{
    val deepseekModelId = "deepseek.r1-v1:0"
    val newPipe = constructPipeFromTemplate<BedrockMultimodalPipe>(pipe)

    if(newPipe != null)
    {
        newPipe.setRegion("us-east-2")
            .setModel(deepseekModelId)

        runBlocking {
            newPipe.init()
        }

        return newPipe
    }

    return null
}


