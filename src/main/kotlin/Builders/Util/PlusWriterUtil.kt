package Builders.Util

import Builders.SurgicalChangeList
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
    val newContext = ContextWindow()
    newContext.contextElements.add(content.text)
    ContextBank.emplaceWithMutex("new page", newContext)
    return content
}

/**
 * Read the prior "new page" text from the bank, apply a list of surgical replacements
 * emitted by an LLM, and bank the result. Designed to be used as a pipe's
 * setTransformationFunction so each fix pipe sees only the changes it explicitly made.
 *
 * The LLM is expected to emit a JSON object with shape:
 *   {"changeList": [{"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}]}
 * (See Builders.SurgicalChangeList and Builders.SurgicalChanges.)
 *
 * Semantics:
 *  - mode "replace"     (default): replace the first occurrence of subStringToChange with replacementSubString
 *  - mode "delete"     : remove the first occurrence of subStringToChange
 *  - mode "insertAfter": replace the first occurrence with subStringToChange + replacementSubString
 *  - unknown mode:      treated as "replace" (lenient, log via the existing per-pipe token report)
 *  - blank subStringToChange: skipped
 *  - subStringToChange not found in current text: skipped, logged in the per-pipe token report
 *
 * Length sanity: if the patched text is less than minLengthRatio of the prior text, the prior text
 * is preserved and returned unchanged. This catches the "LLM pretends to be surgical by replacing
 * everything at once" failure mode and any apply-function bug.
 *
 * On JSON parse failure (both extractJson and the cleanJsonString fallback), the prior text is
 * preserved. The pipe effectively becomes a no-op; the next pipe sees the unchanged bank.
 */
suspend fun applySurgicalReplacementsAndBank(
    content: MultimodalContent,
    minLengthRatio: Double = 0.25
): MultimodalContent
{
    // 1. Read prior "new page" from the bank. Empty string if not banked or bank is empty.
    val prior: String = try {
        ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: ""
    } catch (e: Exception) {
        ""
    }

    // 2. Parse the LLM's SurgicalChangeList. Two-pass: extractJson first, then cleanJsonString fallback.
    val list: SurgicalChangeList? = extractJson<SurgicalChangeList>(content.text)
        ?: extractJson<SurgicalChangeList>(cleanJsonString(content.text))

    // 3. Apply each change in array order with strict-match drop-on-miss.
    var patched = prior
    if (list != null) {
        for (change in list.changeList) {
            if (change.subStringToChange.isBlank()) continue
            val idx = patched.indexOf(change.subStringToChange)
            if (idx < 0) continue
            val replacement: String = when (change.mode) {
                "delete" -> ""
                "insertAfter" -> change.subStringToChange + change.replacementSubString
                else -> change.replacementSubString
            }
            patched = patched.substring(0, idx) +
                replacement +
                patched.substring(idx + change.subStringToChange.length)
        }
    }

    // 4. Length sanity check. If the prior was non-empty and the result is too small, preserve prior.
    val result: String = if (prior.isNotEmpty() && patched.length < (prior.length * minLengthRatio).toInt()) {
        prior
    } else {
        patched
    }

    // 5. Bank the result and propagate to content.text.
    val newContext = ContextWindow()
    newContext.contextElements.add(result)
    ContextBank.emplaceWithMutex("new page", newContext)
    content.text = result
    return content
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
 * Pre-invoke check for the lore-repair and logical-correction pipes. If the upstream judge pipe
 * emitted an empty SurgicalChangeList (i.e. "no changes needed"), skip the repair pipe's LLM
 * call by restoring the prior "new page" text to content.text and returning true.
 *
 * If the JSON can't be deserialized, terminate the pipeline -- the judge output is malformed
 * and the repair pipe cannot operate on it.
 */
suspend fun preInvokeLoreRepairPipe(content: MultimodalContent) : Boolean
{
    val json = extractJson<SurgicalChangeList>(content.text)

    if (json != null)
    {
        if (json.changeList.isEmpty())
        {
            try {
                val prevPage = ContextBank.getContextFromBank("new page")
                content.text = prevPage.contextElements[0]
                return true
            } catch (e: Exception) {
                return false
            }
        }
        // Non-empty list: let the repair pipe run.
        return false
    }

    // JSON could not be parsed -- the judge output is malformed; abort.
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
    // 1. Quote fix: replace * with " (LLMs sometimes emit * for dialogue quotes).
    val quoted = content.text.replace("*", "\"")

    // 2. Apply any surgical changes embedded in the LLM output. The LLM emits a SurgicalChangeList
    //    describing edits against the prior "new page" text. The quote fix above does NOT change
    //    "new page" -- it only normalizes content.text so the JSON parses cleanly. Then we apply
    //    the surgical changes to the prior "new page" (NOT to the quote-fixed text) so the
    //    surgical changes operate against the actual bank state.
    val normalizedContent = MultimodalContent().apply { text = quoted }
    val patched = applySurgicalReplacementsAndBank(normalizedContent).text

    // 3. Merge into "main".
    val newContext = ContextWindow()
    newContext.contextElements.add(patched)
    val chapters = ContextBank.getContextFromBank("main")
    chapters.merge(newContext)
    ContextBank.emplaceWithMutex("main", chapters)

    // 4. Return content with the patched text so downstream sees the result.
    content.text = patched
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


