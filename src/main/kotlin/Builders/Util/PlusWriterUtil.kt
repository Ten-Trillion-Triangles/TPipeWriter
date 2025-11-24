package Builders.Util

import Builders.WorldFixes
import Builders.shunt
import Util.cleanJsonString
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Context.Dictionary
import com.TTT.Context.LoreBook
import com.TTT.Context.MiniBank
import com.TTT.Enums.ContextWindowSettings
import com.TTT.Pipe.MultimodalContent
import com.TTT.Util.deserialize
import com.TTT.Util.extractJson

/**
 * Cache the user prompt so that we can reference it later on in more pipes in this pipeline.
 */
suspend fun storeUserPrompt(content: MultimodalContent)
{
    val userPrompt = content.text //Copy the user prompt.
    val newContext = ContextWindow() //Declare context object because we need it for storing things in the bank.
    newContext.contextElements.add(userPrompt) //Save the user prompt on the generic area of the context object.
    ContextBank.emplaceWithMutex("user prompt", newContext)  //Save the user prompt to the bank.
}

/**
 * Transformation function to store the plan the author bot has come up with based on the chapter plan so far.
 */
suspend fun recordAuthorPlan(content: MultimodalContent) : MultimodalContent
{
    val plan = content.text //Fetch the llm's response.
    val newContext = ContextWindow() //Construct new context window object to store into our bank.
    newContext.contextElements.add(plan) //Write the plan down.
    ContextBank.emplaceWithMutex("page plan", newContext) //Store the plan our context bank.
    return content //Required to compile the function correctly. But isn't actually changed here.
}

/**
 * Record the result of step 2 such that if we get truncated, we can compare this to the style pipe's output
 * and help the anti-truncation pipe restore and un-fuck the truncation issue that has been often known to
 * occur with the style pipe system.
 */
suspend fun recordWritingPipePage(content: MultimodalContent) : MultimodalContent
{
    val result = content.text //Get the new page the llm wrote.
    val newContext = ContextWindow() //Declare for boilerplate reasons.
    newContext.contextElements.add(result) //Store the new page in the generic storage area of the class.
    ContextBank.emplaceWithMutex("new page", newContext) //Save to the bank as a new global key.

    return content //Return content to correctly exit.
}


/**
 * Pre-Validation function to copy the lorebook and replace "main" with it prior to the llm getting called.
 * This allows us to ensure the accurate source of truth: the lorebook itself, is contained as the only value of main.
 * Having the entire story content plus only the keys in the lorebook that were hit is likely to cause problems
 * and even confusion to the lore checker. The lorebook is not only fewer tokens, but it's more of an effective and
 * very blunt description of events which is more useful for testing against lore conflicts than having the llm
 * read the entire story itself.
 */
suspend fun copyLorebookFromMain(bank: MiniBank, content: MultimodalContent? = null) : MiniBank
{
    //Pull the full context from main which has our actual lorebook thus far.
    val mainContext = ContextBank.getContextFromBank("main")
    val onlyLorebook = ContextWindow() //Create new object to store our full lorebook copy into.
    onlyLorebook.loreBookKeys = mainContext.loreBookKeys //Copy only the lorebook keys.
    bank.contextMap["main"] = onlyLorebook //Emplace back "main" with this lorebook to exclude the rest.
    return bank //Return the bank back replacing it right before we proceed into the llm itself.
}

/**
 * Pre invoke call for lore repair. If we don't need changes exit true and bail on this pipe moving us forward.
 */
suspend fun preInvokeLoreRepairPipe(content: MultimodalContent) : Boolean
{
    val output = content.text
    val json = extractJson<WorldFixes>(output)

    if(json != null)
    {
        if(!json.needsChanges)
        {
            //Restore prior work as current writing before moving forward.
            try{
                val prevPage = ContextBank.getContextFromBank("new page")
                content.text = prevPage.contextElements[0]
                return true
            }

            catch (e: Exception)
            {
                return false
            }

        }
    }

    //Blow up the pipeline if we can't deserialize the json.
    content.terminate()
    return false
}

suspend fun transformLoreRewrite(content: MultimodalContent) : MultimodalContent
{
    val newPage = ContextWindow()
    newPage.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("new fixed page", newPage)
    return content
}

/**
 * Determine if any style fixes need to be made. If not clear and skip this pipe to the one forward by returning true.
 * When we do this, pipe will exit early and move onto the next pipe without calling its llm.
 */
suspend fun loreRepairPreInvoke(content: MultimodalContent) : Boolean
{
    val json = extractJson<WorldFixes>(content.text)

    if(json != null)
    {
        if(!json.needsChanges) return true //Exit and skip ahead
        return false //Do not exit and run the llm.
    }

    //Blow up pipeline if we can't deserialize the json.
    content.terminate()
    return true
}

/**
 * Record style change as another chapter in the bank. This way we can deal with the issue of truncation if
 * and when it occurs.
 */
suspend fun recordStyleRewriteTransform(content: MultimodalContent) : MultimodalContent
{
    val newChapter = content.text
    val newContext = ContextWindow()
    newContext.contextElements.add(newChapter)
    ContextBank.emplaceWithMutex("new style page", newContext)
    return content
}

/**
 * Critical step to save our chapter from the plus writer pipeline.
 */
suspend fun secondPassTransform(content: MultimodalContent) : MultimodalContent
{
    var result = content.text //Get the writtten page.
    result = result.replace("*", "\"")
    val newContext = ContextWindow() //Construct to store page.
    newContext.contextElements.add(result) //Store page.
    val chapters = ContextBank.getContextFromBank("main") //Get existing text.
    chapters.merge(newContext)  //Merge the two together.
    ContextBank.emplaceWithMutex("main", chapters) //Emplace back this will be printed by the UI.
    return content
}

suspend fun recordLoreBookPlus(content: MultimodalContent) : MultimodalContent
{
    //Clean up nonsense Deepseek always tries to inject for some reason.
    content.text = cleanJsonString(content.text)

    //Create new context window to prepare to merge it with our global context.
    val newLoreBookEntries = deserialize<ContextWindow>(content.text) ?: ContextWindow()
    newLoreBookEntries.contextElements.clear() //Stop deepseek from writing to this for some reason.


    //Merge in new keys that do not exist yet.
    var bankedContext = ContextBank.getContextFromBank("main")
    bankedContext.merge(newLoreBookEntries)

    //Update the banked context.
    content.context = bankedContext
    ContextBank.emplaceWithMutex("main", content.context)


    return content
}

/**
 * Compare word counts between two strings and return true if the second string is smaller
 * than the first by the specified percentage threshold.
 *
 * @param original The original string to compare against
 * @param modified The modified string to check if it's smaller
 * @param percentageThreshold The percentage by which modified should be smaller than original
 *
 * @return True if modified has fewer words than original by at least the specified percentage
 */
fun isWordCountSmallerByPercentage(original: String, modified: String, percentageThreshold: Double): Boolean
{
    val originalWordCount = original.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    val modifiedWordCount = modified.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    
    if (originalWordCount == 0) return false
    
    val reductionPercentage = ((originalWordCount - modifiedWordCount).toDouble() / originalWordCount) * 100
    return reductionPercentage >= percentageThreshold
}

/**
 * Function that pulls the entire lorebook, or as much as possible if greater than a 13K token budget.
 * Uses the pipe's internal settings to decide on how token counting needs to be handled and then
 * attempts to pull the entire lorebook within a 13K budget. If the size exceeds 13K the last 8K of tokens
 * in the story will be used to determine which keys to match, and then based on weight selection will occur
 * to bring it back into the required 13K token budget.
 *
 * Typical llm context window sizes for most writing llm's in TPipeWriter are between 128K and 200K. Commonly
 * we budget just over 100K for everything when pulling in initially. This gives us about 28K of slack for the lorebook,
 * user prompt, and any other pages from the banks we want to pull in. This just barely enough to fit under large
 * story contexts so truncation will have to occur if we exceed the expected amount of space that we have left over
 * to fill that remainder.
 */
fun chapterPreValidate(context: MiniBank, content: MultimodalContent?) : MiniBank
{
    //Fetch entire story raw.
    val storyContent = ContextBank.getContextFromBank("main")

    //Get the content and construct a blank non-null object if its null.
    val inputContent = content ?: MultimodalContent()

    val pipe = content?.currentPipe
    if(pipe == null) throw Exception("Pipe can't be null because now we don't know how to count tokens")

    /**
     * This defines exactly how the dictionary needs to count tokens to be close to the actual llm's tokenizer
     */
    val tokenCountingSettings = pipe.getTruncationSettings()


    //Delete all the lorebook keys prior to us selecting the remainder as a string.
    storyContent.loreBookKeys.clear()

    /**
     * Collect this as our key selection string for the lorebook. We'll need this if we count over 13K of tokens
     * spent on the lorebook. Give 2K slack for safety vs the llm's own tokenizer. The key selectors if we can't
     * fit our budget will be based on the last 3K tokens we have found in story.
     */
    val truncatedStoryString8K = storyContent.combineAndTruncateAsStringWithSettings(
        "",
        8000,
        tokenCountingSettings,
        ContextWindowSettings.TruncateTop)

    //Copy original lorebook in full.
    val lorebook = ContextBank.getContextFromBank("main").loreBookKeys

    //Copy entire lorebook in full.
    storyContent.loreBookKeys = lorebook

    /**
     * Count lorebook size and truncate if over 13K to give us enough slack to not overflow context windows and
     * crash our pipeline.
     */
    val loreBookAsString = LoreBook.toString()
    if(Dictionary.countTokens(loreBookAsString, tokenCountingSettings) > 13000)
    {
        storyContent.selectAndTruncateContext(
            truncatedStoryString8K,
            13000,
            ContextWindowSettings.TruncateTop,
            tokenCountingSettings)
    }

    /**
     * Replace context of main in the mini bank object. This swap will be invisible to the pipe ensuring everything
     * works as the expected when pulling the "main" key.
     */
    context.contextMap["main"] = storyContent
    return context
}

suspend fun logicalProgressionPreValidationMiniBank(bank: MiniBank, content: MultimodalContent?) : MiniBank
{
    /**
     * Get the full story so that we can collect any data we need on it.
     */
    val fullStory = ContextBank.getContextFromBank("main")

    try{
        //Copy the last page of our story into memory.
        val lastPage : String = fullStory.contextElements[fullStory.contextElements.lastIndex]
        val lastPageWindow = ContextWindow() //Construct to store our copy later.
        lastPageWindow.contextElements.add(lastPage) //Store to the window so that we can push it later.
        bank.contextMap["last page"] = lastPageWindow //Push into the pipe's mini bank object.
    }

    catch (e: Exception)
    {

    }

    return bank
}

/**
 * Replace data with the shunt call masking this as a passing pre-invoke call. Should be placed inside a dummy pipe
 * to allow us to actually hide the logic that manages the connector and picking its path.
 */
suspend fun preInvokeShunt(content: MultimodalContent) : Boolean
{
    val result = shunt(content)
    content.text = result.text
    return true
}

//=========================================Generic Util functions for plus writer=======================================

/**
 * Extracts quoted text that contains two or more periods, typically dialogue with multiple sentences.
 * 
 * @param text The input text to search for quoted segments
 * @return List of quoted text segments that contain 2+ periods, with quotes preserved
 */
fun extractQuotedTextWithMultiplePeriods(text: String): List<String>
{
    val quotedSegments = mutableListOf<String>()
    val regex = "\"([^\"]*?)\"".toRegex()
    
    regex.findAll(text).forEach { match ->
        val quotedText = match.value
        val innerText = match.groupValues[1]
        if (innerText.count { it == '.' } >= 2)
        {
            quotedSegments.add(quotedText)
        }
    }
    
    return quotedSegments
}

/**
 * Appends text inside quoted segments, keeping the new text within the quotation marks.
 * 
 * @param text The input text containing quoted segments
 * @param appendText The text to append inside each quoted segment
 * @return Modified text with appendText added inside quotes
 */
fun appendTextInsideQuotes(text: String, appendText: String): String {
    val regex = "\"([^\"]*?)\"".toRegex()
    return regex.replace(text) { match ->
        val innerText = match.groupValues[1]
        "\"$innerText $appendText\""
    }
}

/**
 * Extracts sentences that contain em dashes (Unicode U+2014: —).
 * 
 * @param text The input text to parse for sentences with em dashes
 * @return List of complete sentences that contain one or more em dashes
 */
fun extractSentencesWithEmDashes(text: String): List<String> {
    val emDash = '\u2014' // Unicode em dash character: —
    val sentences = text.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
    
    return sentences.filter { sentence ->
        sentence.contains(emDash)
    }
}

/**
 * Performs bulk string replacement using a map where keys are search strings and values are replacements.
 * 
 * @param text The input text to perform replacements on
 * @param replacements Map where keys are substrings to find and values are replacement strings
 * @return Modified text with all replacements applied
 */
fun bulkStringReplace(text: String, replacements: Map<String, String>): String {
    var result = text
    replacements.forEach { (searchFor, replaceWith) ->
        result = result.replace(searchFor, replaceWith)
    }
    return result
}

/**
 * Transformation function to store the chapter goals.
 */
suspend fun recordChapterGoals(content: MultimodalContent) : MultimodalContent
{
    val goals = content.text //Fetch the llm's response.
    val newContext = ContextWindow() //Construct new context window object to store into our bank.
    newContext.contextElements.add(goals) //Write the goals down.
    ContextBank.emplaceWithMutex("chapter goals", newContext) //Store the plan our context bank.
    return content //Required to compile the function correctly. But isn't actually changed here.
}

/**
 * Transformation function to store simulated user action.
 */
suspend fun recordUserAction(content: MultimodalContent) : MultimodalContent
{
    val goals = content.text //Fetch the llm's response.
    val newContext = ContextWindow() //Construct new context window object to store into our bank.
    newContext.contextElements.add(goals) //Write the goals down.
    ContextBank.emplaceWithMutex("user action", newContext) //Store the plan our context bank.
    return content //Required to compile the function correctly. But isn't actually changed here.
}

/**
 * Transformation function to store simulated main.
 */
suspend fun recordMainSim(content: MultimodalContent) : MultimodalContent
{
    val goals = content.text //Fetch the llm's response.
    val newContext = ContextWindow() //Construct new context window object to store into our bank.
    newContext.contextElements.add(goals) //Write the goals down.
    ContextBank.emplaceWithMutex("main", newContext) //Store the plan our context bank.
    return content //Required to compile the function correctly. But isn't actually changed here.
}

/**
 * SimulateUpdate of lorebook.
 */
suspend fun recordLoreBook2(content: MultimodalContent) : MultimodalContent
{
    val goals = content.text //Fetch the llm's response.
    val newContext = ContextWindow() //Construct new context window object to store into our bank.
    newContext.contextElements.add(goals) //Write the goals down.
    ContextBank.emplaceWithMutex("main", newContext) //Store the plan our context bank.
    return content //Required to compile the function correctly. But isn't actually changed here.
}

/**
 * Transformation function to store the plot summary.
 */
suspend fun recordPlotSummary(content: MultimodalContent) : MultimodalContent
{
    val plot = content.text //Fetch the llm's response.
    val newContext = ContextWindow() //Construct new context window object to store into our bank.
    newContext.contextElements.add(plot) //Write the plot summary down.
    ContextBank.emplaceWithMutex("plot summary", newContext) //Store the plan our context bank.
    return content //Required to compile the function correctly. But isn't actually changed here.
}
