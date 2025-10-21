package Builders.Util

import Builders.LoreCheckOutput
import Globals.isValidGptOssResponse
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Pipe.MultimodalContent
import com.TTT.Util.deserialize

/**
 * Static library that holds the various validation, branch failure, and transformation
 * functions used by the Advanced writer Ncc pipeline.
 */

/**
 * Store the output of the chapter to global as "prevChapter". Then push the generated writing forward.
 */
suspend fun entryTransformFunction(content: MultimodalContent) :  MultimodalContent
{
    val prevChapterWindow = ContextWindow()
    prevChapterWindow.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("prevChapter", prevChapterWindow) //Push to global for branch failure later.
    return content
}


/**
 * Validate the output of the lore checker. Then proceed forward. Returning false will cause the pipe to trigger
 * a branch failer and attempt to correct. Returning true will move us forward towards the style guide.
 */
suspend fun validateLoreChecker(content: MultimodalContent) : Boolean
{
    val contentJson = content.text
    val results = deserialize<LoreCheckOutput>(contentJson) //Extract json to usable object.

    //Handle case where gpt-oss refuses to carry out our task.
    if(!validateGenericGptOss(content))
    {
        //Exit the pipeline for the time being. If this happens too often we'll need to pull some tricks here.
        content.terminatePipeline = true
        return false
    }

    if(results == null)
    {
        return false
    }

    //Replace the text input in the event we're valid by pulling from "prevChapter" to prep the input of the next pipe.
    if(results.isValid)
    {
        val prevChapter = ContextBank.getContextFromBank("prevChapter")

        /**Chapter data should be at index 0. We need to deal with kotlin not allowing the elvis operator for
         * lists, so we need this try catch block instead.
         */
        try{
            content.text = prevChapter.contextElements[0] //This should always be passing.
        }

        catch(e : Exception)
        {
            content.text = "" //Should not be possible as long as the transformation function was applied prior.
        }

        if(content.text.isEmpty())
        {
            return false
        }

        else
        {
            return true
        }
    }

    return false //Default exit. Consider not pulling the chapter or any other unexpected exit a failure.
}


/**
 * Generic validation function to validate any faulty gpt-oss responses.
 */
suspend fun validateGenericGptOss(content: MultimodalContent) : Boolean
{
    return isValidGptOssResponse(content)
}


/**
 * Transform the repaired lorebook and update prevChapter so that we can optimize the style output generators
 * token usage.
 */
suspend fun transformLoreRepair(content: MultimodalContent) : MultimodalContent
{
    val prevChapter = ContextBank.getContextFromBank("prevChapter")

    try{
        prevChapter.contextElements[0] = content.text
    }

    catch(e: Exception)
    {
        prevChapter.contextElements.add(content.text)
    }

    ContextBank.emplaceWithMutex("prevChapter", prevChapter)
    return content
}


/**
 * Convert the transformed style output and push it back to our main story.
 */
suspend fun transformStyle(content: MultimodalContent) : MultimodalContent
{
    /**
     * The system prompt instructs the llm to return "true". This is assuming it understands the quotes are
     * ephemeral. Of course, we can't ever really trust llm's can we? So we need to get the quotes out of there
     * in the event it took the requirement literally and wrapped it inside quotes.
     */
    val result = content.text.replace("\"", "")

    /**
     * We can save on tokens by only having the style checker return "true" if it doesn't need to fix it in any way.
     * If we do however, we'll need to fetch the actual content which we've thankfully been banking at "prevChapter".
     * So we just need to pull it, and emplace the text value of our content before exiting here.
     */
    if(result == "true")
    {
        val prevChapter = ContextBank.getContextFromBank("prevChapter")
        val mainBank = ContextBank.getContextFromBank("main")
        mainBank.merge(prevChapter)
        ContextBank.emplaceWithMutex("main", mainBank)

        try{
            content.text = prevChapter.contextElements[0]
        }

        catch(e: Exception)
        {
            content.terminatePipeline = true //This should never be hit. But if it is we need to just end the pipeline.
        }

        return content
    }

    /**
     * If we reach here, it means the pipe had to fix up the style of the writing. In this case we should emplace it
     * back into main which would be our chapter content.
     */
    val mainBank = ContextBank.getContextFromBank("main")
    mainBank.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("main", mainBank)
    return content
}