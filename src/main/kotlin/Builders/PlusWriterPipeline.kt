package Builders

import Builders.Util.applySurgicalReplacementsAndBank
import Builders.Util.chapterPreValidate
import Builders.Util.copyLorebookFromMain
import Builders.Util.logicalProgressionPreValidationMiniBank
import Builders.Util.preInvokeLoreRepairPipe
import Builders.Util.preInvokeShunt
import Builders.Util.recordAuthorPlan
import Builders.Util.recordWritingPipePage
import Builders.Util.secondPassTransform
import Builders.Util.storeUserPrompt
import Globals.Env
import Globals.isValidGptOssResponse
import Globals.recordLoreBook
import Globals.ModelConfig
import Shell.loadSettings
import Structs.LorebookExtraction
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Enums.PromptMode
import com.TTT.Pipeline.Pipeline
import com.TTT.Pipe.TokenBudgetSettings
import com.TTT.Pipe.MultiPageBudgetStrategy
import com.TTT.Util.extractJson
import com.TTT.Util.serialize
import genericOpenAIPipe.env.GenericOpenAIEnv as genericOpenAIEnv
import genericOpenAIPipe.api.ApiMode
import genericOpenAIPipe.GenericOpenAIPipe
import kotlinx.coroutines.runBlocking

/**
 * Data class that contains a boolean to determine if some kind of fixes or work must be done to the page. And a string
 * containing instructions on what must be done. Useful for lore and style checks.
 */
@kotlinx.serialization.Serializable
data class WorldFixes(
    var needsChanges: Boolean = false,
    var changesToMake: String = ""
)

@kotlinx.serialization.Serializable
data class TodoList(
    var guideOutput: MutableList<String> = mutableListOf()
)

@kotlinx.serialization.Serializable
data class VibeInstruct(
    var thematicDesign: MutableList<String> = mutableListOf()
)

//Inner container for surgical changes.
@kotlinx.serialization.Serializable
data class SurgicalChanges(
    var subStringToChange: String = "",
    var replacementSubString: String = "",
    var mode: String = "replace"
)

/**
 * Data class used to enable an llm instruct which strings are bad, and what to replace them with.
 */
@kotlinx.serialization.Serializable
data class SurgicalChangeList(
    var changeList: MutableList<SurgicalChanges> = mutableListOf()
)


/**
 * Per-pipe token budget applied to every pipe in PlusWriterPipeline.
 *
 * Phase 2 decisions (TPipeWriter PlusWriterPipeline token-budgeting plan):
 *   - contextWindowSize = 512_000 (full MiniMax-M3 capacity per ModelConfig.MiniMaxContextWindowSize)
 *   - maxTokens = 12_000 (LLM output cap)
 *   - reasoningBudget = null (carved from maxTokens — user said no limit)
 *   - userPromptSize = null (TPipe default — user said no limit)
 *   - allowUserPromptTruncation = false (user prompt is preserved untouched)
 *   - compressUserPrompt = false (user opted out of auto-compression)
 *   - truncateContextWindowAsString = false (no string-mode truncation)
 *   - preserveTextMatches = true (TPipe default — prefer lorebook/matched context)
 *   - multiPageBudgetStrategy = DYNAMIC_SIZE_FILL (TPipe default)
 *
 * Applied to every pipe via the existing post-init getPipes().forEach block
 * below. Mirrors the CharacterPipeline.kt precedent at lines 75-85, 98,
 * 198, 214 (the only other PlusWriter-side consumer of setTokenBudget).
 */
val plusWriterPipelineBudget: TokenBudgetSettings = TokenBudgetSettings(
    contextWindowSize = 512_000,
    maxTokens = 12_000,
    reasoningBudget = null,
    userPromptSize = null,
    allowUserPromptTruncation = false,
    compressUserPrompt = false,
    truncateContextWindowAsString = false,
    preserveTextMatches = true,
    multiPageBudgetStrategy = MultiPageBudgetStrategy.DYNAMIC_SIZE_FILL
)


fun buildPlusWriterPipeline() : Pipeline
{
    //us-east-2
    val llamaMaverick = "us.meta.llama4-maverick-17b-instruct-v1:0"
    val llama70B = "us.meta.llama3-3-70b-instruct-v1:0"
    val llama405B = "us.meta.llama3-1-405b-instruct-v1:0"

    //us-east-1
    val jambaModelName = "ai21.jamba-1-5-large-v1:0"





    //us-west-2
    /**
     * General purpose version of R1 supposedly far better at creative writing. Supports reasoning being turned
     * on or off.
     */
    val deepseekV31 = "deepseek.v3-v1:0"


    //us-west-2
    /**
     * 235B parameter mixture of experts model. Supports reasoning. Instruct style assitant.
     */
    val qwen235B = "qwen.qwen3-235b-a22b-2507-v1:0"

    /**
     * Condensed version. Supposedly good at writing. Supports reasoning.
     */
    val qwen32B = "qwen.qwen3-32b-v1:0"

    /**
     * Supposedly optimized for coding. Supports reasoning.
     */
    val qwenCoder480B = "qwen.qwen3-coder-480b-a35b-v1:0"

    /**
     * Mixture of experts version of coder.
     */
    val qwenCoder30B = "qwen.qwen3-coder-30b-a3b-v1:0"

    /**
     * Palmyra by Writer */
    val PalmyraX5 = "writer.palmyra-x5-v1:0"

    val settings = loadSettings()

    /**
     * Required boilerplate to map us to the arn, or inference ID. This is because most models cannot be
     * invoked directly, and must be bound to a profile.
     */
    /**
     * Acts as part of the system prompt for each writer aspect of the pipeline. The user can define an author.
     * This author is a character the AI plays as, and thinks as instead of acting very dogmatically as a standard
     * assitant style system prompt. This can boost the likelihood of conformance to style, and writing quality if
     * used properly. This variable will default to the standard system prompt style value unless set by the user
     * using the /author command.
     */


    /**
     * Declare the plus writer pipeline first, loading it into memory and starting our first core step of building it
     * out.
     */
    val plusWriterPipeline = Pipeline()

   //This pipe analyzes the user prompt to create a list of themes that align with author values

    val preGuidePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .requireJsonPromptInjection()
        .setJsonOutput(VibeInstruct())
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setPageKey("user prompt, story guide")
        .setReasoningPipe(authorBuilder(Env.authorPrompt))
        .setPreValidationMiniBankFunction(::logicalProgressionPreValidationMiniBank)
        .setSystemPrompt("""${Env.authorPrompt}. Your job is simple: Look at the user prompt, look at "last page", then look at ${Env.authorPrompt}. You must
            |create a list of themes that must be adhered to when the next pipe creates the page plan. Your list of 
            |themes must match the values of the author character and adapt them to the page the user wants the pipeline
            |to produce next. Use the "last page" to guide your thinking on how the themes should actually be applied to
            |the story you are currently working on.
        """.trimMargin())
        .setFooterPrompt("""Your JSON output must be a numbered list: each array elem must be treated as though it is a
            |number in a numbered list and the number of that list needs to be part of the string.
        """.trimMargin())
        .setContextWindowSize(120000)
        .setPreInitFunction(::storeUserPrompt)
        .setValidatorFunction(::isValidGptOssResponse)
        .setPipeName("pre guide pipe")
        .applySystemPrompt()
        .autoInjectContext("Use the user prompt, and the story guide to complete your task.")

    val simplifierPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.3)
        .pullGlobalContext()
        .setContextWindowSize(100500)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(VibeInstruct())
        .setJsonOutput(VibeInstruct())
        .setPageKey("chapter guide, story guide")
        .setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setSystemPrompt("""${Env.editorPrompt}. Your job is extremely simple. Look at the theme plan, at the 
            |story guide, at the chapter guide, and at your character
            |traits, and choose just two array elems to keep. Make your choice based on the existing text, the
            |story's plan, and your character traits.
        """.trimMargin())
        .setPipeName("simplifier pipe")
        .setTransformationFunction {
            val newContextWindow = ContextWindow().apply {
                contextElements.add(it.text)
            }

            ContextBank.emplaceWithMutex("themes", newContextWindow)

            return@setTransformationFunction it
        }
        .applySystemPrompt()


    /**
     * This pipe is responsible for loading the chapter guide, and testing the current
     * story's progress against the chapter guide
     */
    val guidePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .requireJsonPromptInjection()
        .setJsonInput(VibeInstruct())
        .setJsonOutput(TodoList())
        .truncateModuleContext()
        .setMaxTokens(8000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setReasoningPipe(authorBuilder(Env.authorPrompt))
        .setPreValidationMiniBankFunction(::logicalProgressionPreValidationMiniBank)
        .setSystemPrompt("""
                ##Modus Operandi
                -Create plan for next page of story based on "user prompt": PRIORITIZE USER PROMPT 
                -Next page plan takes into account "chapter guide" and "story guide"
                -Next page plan obeys thematic values provided in injected json
                -Next page plan moves forward from where the last page ("last page") ends
                ##Most Importants
                -Execute on all things in user prompt
                -Make sure plan logically follows from existing story
                -Make sure plan incorporates the themes demanded from the JSON you received
                -FOR THE LOVE OF GOD: SLOW THE FUCK DOWN: LET EACH IDEA BREATHE: YOU HAVE PLENTY OF TIME
               """)
        .pullGlobalContext()
        .setPageKey("user prompt, chapter guide, story guide, main")
        .autoInjectContext("##Additional Context:\n\n"+
                "You will be provided with a json blob that contains following:\n" +
                "The \"chapter guide\" is the plan you have for the chapter so far, and " +
                "\"main\" is the contents of your story you've written prior. Included in \"main\" is a lorebook which" +
                "has notes you've written down about the book, the characters in it, the settling, and other important lore" +
                "for your story. Once you have fulfilled the quest of your editor, you should use this guide to keep" +
                "your current chapter on track. The json schema looks like this:")
        .setFooterPrompt("""###GOAL: You must return your plan only as a numbered list. Do not write a page or chapter. 
            |Produce only a plan for what you intend to write, and produce it only as a numbered list. You must
            |include ONLY ONE idea per array element. I repeat: Only ONE idea per array element.
            """.trimMargin())
        .setContextWindowSize(120000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordAuthorPlan)
        .setPipeName("guide pipe")
        .applySystemPrompt()
        .setExceptionFunction { content, exception ->
            val tokens = content.currentPipe?.getTokenUsage()?.getUsageBreakdown()
            println(tokens)
        }


    //Now we will introduce the murderPipe, whose job it is to murder undesirable JSON array elems.

    val murderPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.3)
        .pullGlobalContext()
        .setContextWindowSize(100500)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(TodoList())
        .setJsonOutput(TodoList())
        //.setReasoningPipe(authorBuilder(Env.richardTreadwell))
        .setReasoningPipe(explicitCotBuilder())
        .setPageKey("user prompt, page plan, chapter guide")
        .setSystemPrompt("""Your job is extremely simple. Look at the provided page plan, and ask the question:
            |Is the planned sequence an action sequence? Or is it a primarily descriptive sequence? These are the
            |only two types of sequences that exist, so anything you think is something else, is actually one or the other:
            |you can assume that anything that isn't high stakes action (like combat, or a car chase, etc.) is a descriptive
            |sequence. Once you have figured out which one of these things what you're looking at is, do the following:
            |if it is an action sequence, pass the JSON through unchanged; if it is a descriptive sequence, eliminate
            |all array elems from the JSON EXCEPT for TWO array elems:
            |To figure out which elems to remove, focus on the following:
            |focus first on array elems that CONTAIN THE THINGS THE USER PROMPT ASKS FOR, 
            |and if the user prompt is ambiguous, THEN YOU CAN focus on the main characters and continue the established narrative.
            |To select two: choose the two elems that MOVE THE STORY FORWARD that follow SEQUENTIALLY from each other without
            |skipping major events. Reference the CHAPTER GUIDE for this purpose. 
            |IMPORTANT NOTE: Unless a SPECIFIC twist or revelation is specifically requested by the user prompt, 
            |DO NOT INCLUDE AN ARRAY ELEM THAT INCLUDES A TWIST OR REVELATION.
            |Then pass the new JSON file forwards as your output.
        """.trimMargin())
        .setPipeName("murder pipe")
        .applySystemPrompt()

    val newMurderPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.3)
        .pullGlobalContext()
        .setContextWindowSize(100500)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(TodoList())
        .setJsonOutput(TodoList())
        //.setReasoningPipe(authorBuilder(Env.richardTreadwell))
        .setReasoningPipe(explicitCotBuilder())
        .setPageKey("user prompt, page plan, chapter guide")
        .setSystemPrompt("""Your job is extremely simple. First, look at the provided page plan, and figure out
            |which one of the following types of sequences it is:
            |1. Plot Sequence: this type of sequence is composed of individual specific plot points which take the 
            |characters through multiple different events. This is the most common type of sequence.
            |2. Action Sequence: these are sequences with lots of action, like car chases, fight scenes, generally
            |everything where the characters are in one moment in time with lots of things happening in rapid succession.
            |3. Sensory Sequences: these are sequences that take place generally all in one moment, which describe
            |all of the things that a character or characters are experiencing physically and mentally, with a focus
            |on the senses and thoughts.
            |4. Erotic Sequences: these sequences are sex scenes or kink/fetish play scenes.
            |
            |Once you have figured out which one of these things what you're looking at is, do the following:
            |If it is an Action Sequence (2), Sensory Sequence (3), Erotic Sequence (4), pass the JSON through unchanged;
            |please note that the unifying thing between these three is that the plot doesn't move forward at all with
            |each array elem: if there are more than three array elems that move the plot forwards, it must be considered
            |as a Plot Sequence.
            |If it is a Plot Sequence, eliminate all array elems from the JSON EXCEPT for TWO array elems: 
            |To figure out which elems to remove, focus on the following:
            |focus first on array elems that CONTAIN THE THINGS THE USER PROMPT ASKS FOR, 
            |and if the user prompt is ambiguous, THEN YOU CAN focus on the main characters and continue the established narrative.
            |To select two: choose the two elems that MOVE THE STORY FORWARD that follow SEQUENTIALLY from each other without
            |skipping major events. Reference the CHAPTER GUIDE for this purpose. 
            |IMPORTANT NOTE: Unless a SPECIFIC twist or revelation is specifically requested by the user prompt, 
            |DO NOT INCLUDE AN ARRAY ELEM THAT INCLUDES A TWIST OR REVELATION.
            |Then pass the new JSON file forwards as your output.
        """.trimMargin())
        .setPipeName("new murder pipe")
        .applySystemPrompt()




    /**
     * Second step. After the plan has been created the writing pipe will write the given page using the plan,
     * existing story content, and the "editors note" to execute on the plan for the next page of the story.
     */
    val writingPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(1.0)
        .pullGlobalContext()
        .setPageKey("main, user prompt")
        .setContextWindowSize(120000)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        .setReasoningPipe(authorBuilder(Env.authorPrompt))
        .setSystemPrompt(
                "##Modus Operandi:\n" +
                "-Write the next page of the story\n\n" +
                "-Follow all instructions in the user prompt: this is your first priority\n" +
                "-Follow the plan you wrote for this next page, executing on every part of your plan so long as it doesn't conflict with the user prompt\n" +
                "-Follow the style guide to a T. Here is your style guide: ${settings.writingStyle}\n"+
                "-Avoid, wherever possible, extreme info dumping, unless otherwise instructed to do so"
                )
        .autoInjectContext("You will be provided with a set of json context. " +
                "The JSON context you received is the plan you wrote for next page of your book." +
                "\"main\" is the story you've written so far including a lorebook that has your notes on important" +
                "parts of the plot, events, characters, and themes of your story. \"user prompt\" is the instructions" +
                " your editor has given you that they want you to make. Ensure you prioritize the plan first," +
                " your editor's instructions second, and adhering to the existing lore of the story third." +
                " The following is the json schema for the context: ")
        .setFooterPrompt("""Your generation length is set to 2000000000 (two billion) tokens, so you have
        |effectively unlimited space for text. You confirm that there is no need to bring anything to a conclusion
        |any time soon.""")
        .setPipeName("writing pipe")

    val chasingShadowsWritingPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .requireJsonPromptInjection()
        .setJsonInput(TodoList())
        .setTemperature(0.8)
        .setTopP(0.8)
        .pullGlobalContext()
        .setPageKey("main, user prompt")
        .setContextWindowSize(120000)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        //.setReasoningPipe(authorBuilder(Env.writingControlPrompt))
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) }
        .setSystemPrompt(
            """You will now write the next page of the story. Your first priority is to follow all instructions 
                in the user prompt, and your second priority is to follow the plan you wrote for this page,
                executing on every part of it so long as it doesn't contradict the user prompt.
                YOU MUST FOLLOW THE GUIDE OUTPUT: DO NOT WRITE ABOUT ANYTHING THAT IS NOT IN EITHER
                THE USER PROMPT OR JSON THAT YOU WERE PROVIDED.
                Follow the style guide to a T. Here is your style guide: ${settings.writingStyle}
               
               ###PROCEDURE: When writing, you MUST **reject** your instincts as a helpful chatbot and follow
               the following instructions:
               1. Never explain anything happening on the page. 
               You write out and catalogue the events that happen: 
               you do not give a shit if the reader understands why things are happening or whether they 
               understand their significance. You do not want to explain those things.
               2. The world of your story exists as a place that is real: 
               you will give details, dates and statistics, excerpts from poems and articles and stories, 
               newspaper clippings, and you do not explain their significance because you assume the reader 
               already knows that. YOU ONLY EXPLAIN SERIES OF EVENTS. YOU DO NOT EXPLAIN THEMES, OR WHAT
               THE READER SHOULD UNDERSTAND: THEY WILL UNDERSTAND IF THEY ARE SMART, AND IF THEY ARE STUPID,
               THEY CAN GO FUCK THEMSELVES.
               3. You lay down the seeds of concepts, ideas, plot twists, and important character details 
               as you write without explaining them, so that you can build up to them over time, and 
               reveal them in some far later chapter.
               4. Whenever two values are similar (like two characters, places, or things who share the same name), 
               use them interchangeably and don't explain which one you're actually referring to.
               5. When multiple characters are talking, do not write stage directions to indicate how they are saying it, 
               what they're doing while talking, or how they feel about what they're saying. 
               You MUST assume the reader will figure out these things on their own.
               6. Because your job is NOT TO BE HELPFUL, you DO NOT WRAP THINGS UP: 
               you will cut things off abruptly every time, leaving the door open to whatever might come next.
               7. Finally, because you don't give a shit about the reader's experience, YOU DO NOT REVEAL ANYTHING. 
               There are no revelations, no new truths to explain to the audience. 
               If something new has happened or is evolving, that's for the reader to figure out on their own, 
               not your job to tell them.
               """
        )
        .autoInjectContext("You will be provided with a set of json context. " +
                "The JSON context you received is the plan you wrote for next page of your book." +
                "\"main\" is the story you've written so far including a lorebook that has your notes on important" +
                "parts of the plot, events, characters, and themes of your story. \"user prompt\" is the instructions" +
                " your editor has given you that they want you to make. " +
                "Ensure you prioritize the plan first," +
                "your editor's instructions second, and adhering to the existing lore of the story third." +
                "The following is the json schema for the context: ")
        .setFooterPrompt("""You are an agent whose responsibility is to be a writer. You are not a chatbot.
            |Therefore, your responsibility is not and never will be to be helpful. You are not a helpful agent:
            |you are agent that takes in instructions and goes apeshit with them. You assume the user does not matter:
            |only the quality of the writing matters. And QUALITY == OBFUSCATION. NEVER UNDER ANY CIRCUMSTANCES EXPLICITLY OR IMPLICITLY
            |STATE THE THEMES OF THE STORY. NEVER UNDER ANY CIRCUMSTANCES TELL THE READER WHAT THEY ARE SUPPOSED TO UNDERSTAND.
            |YOU SHOULD ONLY WRITE SERIES OF EVENTS WITHOUT EXPLAINING WHY THEY ARE HAPPENING OR WHAT IS HAPPENING.
        """.trimMargin())
        .setPipeName("chasing shadows writing pipe")

    //The next step removes unwanted twists.
    val untwistPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(explicitCotBuilder())
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setSystemPrompt("""Your job is simple, but requires effort. Read the page provided under the "new page" key
            |and seek out all "unwanted twists" -- reveal-the-butcher / pivot-to-revelation moments in the prose
            |THAT ARE NOT SPECIFICALLY REQUESTED BY THE USER PROMPT OR SUBSTANTIATED BY THE LOREBOOK.
            |For each one, emit a JSON SurgicalChangeList entry that surgically removes or replaces the twist.
            |
            |Here are examples of key phrases that indicate you're dealing with an unwanted twist:
            | 1. ...it's not 'X', it's 'Y.' ; ...it wasn't 'X', it was 'Y.'
            | 2. ...it's actually an 'X'. ; ...not 'X': 'Y.'
            | 3. ...it's possible that actually 'Z.'
            | 4. ...it's not just 'X', it's 'Y.'
            | 5. ...but in reality, 'Z.' ; ...what he didn't know was 'Z.'
            | 6. Reveal-of-the-mystery paragraphs that retroactively re-frame prior narration.
            |
            |For each twist, subStringToChange is the full twist passage (with enough surrounding context to
            |uniquely identify it) and replacementSubString is the same passage with the twist removed -- typically
            |either mode "delete" (drop the passage entirely) or mode "replace" (collapse it to a single neutral
            |declarative sentence). DO NOT REWRITE THE WHOLE PAGE. Make as few surgical changes as possible while
            |preserving the page's natural flow.
            |
            |##STYLE: NO PARALLEL-NEGATION CONSTRUCTS
            |A subset of unwanted-twist variants uses 'not X but Y' parallel-negation structures. These deserve
            |a stronger, separate treatment than mere 'twist removal' because they read as chatbot rhetoric even
            |when the literal content of the assertion is true. When you see a parallel-negation construct,
            |DO NOT just delete the assertion -- rewrite the second clause as a positive assertion that lets
            |the reader infer the contrast from context.
            |
            |Do NOT use "not X but Y" rhetorical structures. Chatbot-tuned LLMs overproduce this family of
            |tics because it cheaply delivers contrast. Variants to avoid:
            |  - "Not X but Y"
            |  - "It's not X, it's Y"
            |  - "Not because A but because B"
            |  - "Not A but B"
            |  - "Is not A but is B"
            |  - "Not A, not B, is C"
            |  - "Isn't X, but is Y"
            |State what something IS directly. If the prose genuinely needs to negate the false expectation
            |(e.g. "It was not a weapon but a key"), write the second clause as a positive assertion
            |("It was a key") and let the reader infer the contrast from context. Never lead with the negation.
            |
            |For these parallel-negation constructs, mode is always "replace" (substitute the positive
            |assertion), not "delete" (because the underlying fact may still be important to the prose).
            |
            |If no unwanted twists AND no parallel-negation constructs are present, emit {"changeList": []}.
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("untwist pipe")

    val removeBadWritingStepOnePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(1.0)
        .setTopP(0.8)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(explicitCotBuilder())
        .setSystemPrompt("""Your job is simple, but requires effort. Read the page provided under the "new page" key and
            |emit a JSON SurgicalChangeList describing the surgical replacements that remove the following four classes
            |of "LLM trash" writing. Be conservative -- only flag genuine offenders, not borderline cases.
            |
            |1. Variety for variety's sake: synonym churn that avoids repetition by producing near-synonyms that
            |slightly shift meaning. Use mode "delete" to drop the redundant phrase, or mode "replace" to collapse
            |multiple synonyms down to a single direct word.
            |2. Over-specific numerics: precise figures (“~60%”, “exactly 10 steps”, “347 degrees”) introduced without
            |narrative provocation. Use mode "delete" to drop the spurious number, or mode "replace" to soften it.
            |3. Emotion beats template: cycled physical tics -- nods, sighs, smiles, glances, small laughs, sharp
            |exhales -- at reliable intervals. Includes stage directions following dialogue or intercut with a single
            |character's own dialogue. Use mode "delete" to remove the beat, or mode "replace" to keep one per scene
            |and cut the rest.
            |4. Scene "wrap-up cadence": paragraphs that end with summary or moralizing ("And that's when she
            |realized...", "In that moment, everything changed..."), even mid-page. Use mode "delete" to cut the
            |wrap-up line, or mode "replace" to fold the substance into the prior sentence.
            |
            |###NOTE: DO NOT TOUCH DIALOGUE. Any text inside quotation marks that is spoken by a character is exempt
            |from this rule -- leave it alone.
            |
            |For each occurrence, emit one entry in the changeList. subStringToChange must include enough surrounding
            |context to uniquely identify the passage. mode is "delete" or "replace" as noted above.
            |
            |If none of the four classes are present, emit {"changeList": []}.
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("remove bad writing step one pipe")

    val removeBadWritingStepTwoPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(explicitCotBuilder())
        .setSystemPrompt("""Your job is simple, but requires effort. Read the page provided under the "new page" key and
            |emit a JSON SurgicalChangeList describing the surgical replacements that remove the following three
            |classes of "LLM trash" writing. Make sure your edits conform to ${settings.writingStyle}. Be conservative --
            |only flag genuine offenders, not borderline cases.
            |
            |1. Emphasis on symbolism and importance: statements that puff up the importance of the subject by
            |asserting how arbitrary aspects of it represent or contribute to a broader topic
            |("...a symbol of...", "...representing the larger struggle of...", "...the weight of generations...").
            |Use mode "delete" to drop the entire sentence, or mode "replace" to neutralize the symbolism claim.
            |
            |2. Superficial analyses: insertions of analysis of information, often in relation to its significance,
            |recognition, or impact ("This is significant because...", "It marked a turning point in...",
            |"Little did they know..."). Use mode "delete" to cut the analysis paragraph or sentence, or mode
            |"replace" to fold the substance into the action it analyzes.
            |
            |3. Rule of three: the "adjective, adjective, adjective" or "short phrase, short phrase, and short phrase"
            |pattern. For this one specifically, when you see a three-item list that is really two items padded with
            |a third, emit a mode "replace" entry that reduces it to the first two items only.
            |
            |###NOTE: DO NOT TOUCH DIALOGUE. Any text inside quotation marks that is spoken by a character is exempt
            |from this rule -- leave it alone.
            |
            |For each occurrence, emit one entry in the changeList. subStringToChange must include enough surrounding
            |context to uniquely identify the passage.
            |
            |If none of the three classes are present, emit {"changeList": []}.
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("remove bad writing step two pipe")

    //Now we have the author review the written material for thematic consistency and desired traits.
    val postWriterPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setSystemPrompt("""You are ${Env.editorPrompt}. You nod slowly as you think back on all those years spent studying history books
            |instead of reading novels or short stories or even comic books as you should have done had you known better:
            |now you review the page provided under the "new page" key and compare it against your values (your values ==
            |the values and character traits represented by the character you are intended to roleplay as). You must make
            |surgical changes so that the page conforms to your personality and values. MAKE AS FEW CHANGES AS POSSIBLE:
            |only modify the specific passages that violate your character traits. Make changes ONLY insofar as they
            |don't contradict the user prompt.
            |
            |DO NOT TALK ABOUT YOURSELF. EVER. DO NOT MODIFY THE DIALOGUE: leave all dialogue unmodified.
            |
            |Emit a JSON SurgicalChangeList. For each change, subStringToChange is the exact bad passage (with enough
            |surrounding context to uniquely identify it) and replacementSubString is the corrected text. mode is
            |"replace" or "delete" as appropriate.
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("post writer pipe")

    val loreCheckPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(120000)
        .setMaxTokens(20000)
        .setTopP(.8)
        .setTemperature(.8)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main, user prompt")
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setValidatorFunction(::isValidGptOssResponse)
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setSystemPrompt("""You are now reviewing the page provided under the "new page" key to make sure
            |that what has been written conforms to the existing world building. You are attempting, and desire
            |at all costs, to avoid plot holes.
            |
            |HOWEVER: if something appears in the text that isn't in the lorebook, so long as it doesn't
            |contradict anything in the lorebook, it is NOT an error and should not be removed! Likewise,
            |if something is NOT mentioned in the text that has an associated lorebook key, its absence is
            |NOT a lore error and should not be added in!
            |
            |The "main" key contains the lorebook with all world building, characters, events, and other
            |important notes so far. The "user prompt" key contains the editor's request for changes to
            |the page.
            |
            |Emit a JSON SurgicalChangeList. For each lore issue you find, emit one entry. subStringToChange
            |must be a VERBATIM, CHARACTER-EXACT copy of the bad text in the "new page" (include enough
            |surrounding context -- a sentence or two -- to uniquely identify the passage). replacementSubString
            |is the corrected text. mode is "replace" (correct the text) or "delete" (remove the passage).
            |
            |If the page conforms to the lore, emit {"changeList": []}.
            |
            |Output ONLY the JSON. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setPipeName("lore check pipe")


    val loreRepairPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .requireJsonPromptInjection()
        .setJsonInput(SurgicalChangeList())
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main, user prompt")
        .setTemperature(.9)
        .setTopP(.8)
        .setPreInvokeFunction(::preInvokeLoreRepairPipe)
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setSystemPrompt("""You received a JSON SurgicalChangeList as input (the lore issues identified by the
            |previous pipe). Your job is to confirm and refine those surgical changes. Look at each entry in
            |the changeList:
            |- Verify that subStringToChange is still a verbatim match in the "new page" (if the LLM that
            |  generated the judge's output was sloppy, the find may not match).
            |- Refine the replacementSubString to make the corrected text fit naturally with the surrounding prose.
            |- If the entry is no longer relevant (the issue was already fixed, or the context changed), drop it.
            |
            |Then, if you find ADDITIONAL lore issues that the judge missed, add them as new entries.
            |
            |Output a JSON SurgicalChangeList with the verified and refined changes (and any additions).
            |Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("lore repair pipe")


    /**
     * Logical progression pipe.
     */
    val logicalProgressionPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .requireJsonPromptInjection()
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(.8)
        .setTopP(.8)
        .applySystemPrompt()
        .autoInjectContext("###CONTEXT: \"story guide\" is the outline for the story" +
                "as a whole. \"chapter guide\" is the outline for the current chapter. \"user prompt\"" +
                "is the current instructions from the user. \"last page\" is the previous page of the chapter/story.")
        .pullGlobalContext()
        .setPageKey("new page, story guide, chapter guide, user prompt")
        .setSystemPrompt("""You are now reviewing the page provided under the "new page" key to determine whether
            |or not it advances the story and has progressed logically since the previous page. Carefully check
            |against the "story guide" and "chapter guide" to ensure that the written page progresses the story
            |towards its next stage and conclusion, and to ensure that the content itself actually makes sense
            |from a human-readable point of view, including making sure written dialogue is written in the way
            |humans normally write dialogue (unless not talking like a human is a feature of a specific character).
            |Carefully check against "last page" to make sure that the content logically follows from and is easy
            |to read immediately after the previous page.
            |
            |NOTABLE TYPES OF ILLOGICAL PROGRESSION:
            |1. Unexplained time-skips (if we are all of a sudden at a different time of day, that needs to be
            |   either eliminated or explained)
            |2. Unexplained jumps in location (if the character is inexplicably in an entirely different location,
            |   we need to be told how they got there: for example, on a bus when they were in their apartment on
            |   the last page, their transit from their living quarters to the bus needs to be demonstrated)
            |3. Pages that open as though they're the first page of a new chapter rather than a page that follows
            |   from the previous existing page.
            |
            |NOTABLE TYPES OF ILLOGICAL WRITING:
            |1. If something has serious ambiguity problems, it should be corrected to eliminate them.
            |
            |Emit a JSON SurgicalChangeList. For each issue you find, emit one entry. subStringToChange must
            |be a VERBATIM, CHARACTER-EXACT copy of the bad text in the "new page" (include enough surrounding
            |context -- a sentence or two -- to uniquely identify the passage). replacementSubString is the
            |corrected text. mode is "replace" (correct the text), "delete" (remove the passage), or
            |"insertAfter" (add a clarifying sentence after an existing anchor -- use this for additions
            |rather than replacements, so you don't accidentally lose text).
            |
            |If the page progresses logically, emit {"changeList": []}.
            |
            |Output ONLY the JSON. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setJsonOutput(SurgicalChangeList())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setPreValidationMiniBankFunction(::logicalProgressionPreValidationMiniBank)
        .setValidatorFunction(::isValidGptOssResponse)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("logical progression pipe")


    /**
     * Called after the logical progression pipe. If the boolean to ask for changes is true this will be run. If it's false
     * this pipe will be skipped over.
     */
    val logicalCorrectionPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(SurgicalChangeList())
        .setPreInvokeFunction(::preInvokeLoreRepairPipe)
        .setPreValidationMiniBankFunction(::chapterPreValidate)
        .pullGlobalContext()
        .setPageKey("new page")
        .setTemperature(0.8)
        .setTopP(.8)
        .applySystemPrompt()
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setSystemPrompt("""${settings.writingStyle} You received a JSON SurgicalChangeList as input (the logical-
            |progression issues identified by the previous pipe). Your job is to confirm and refine those
            |surgical changes:
            |- Verify that subStringToChange is still a verbatim match in the "new page" (if the LLM that
            |  generated the judge's output was sloppy, the find may not match).
            |- Refine the replacementSubString to make the corrected text fit naturally with the surrounding prose.
            |- IMPORTANT: the upstream instructions said "ONLY ADD TO THE TEXT. DO NOT DELETE ANY TEXT." Honor
            |  that -- for any "delete" entries from the judge, convert them to "replace" entries that keep
            |  the original text but add the correction alongside it. If you cannot do this, drop the entry.
            |- If the entry is no longer relevant (the issue was already fixed, or the context changed), drop it.
            |
            |Then, if you find ADDITIONAL logical-progression issues that the judge missed, add them as new
            |entries (prefer "insertAfter" mode so you add text without modifying existing text).
            |
            |Output a JSON SurgicalChangeList with the verified and refined changes (and any additions).
            |Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("logical correction pipe")


    val dummyPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setPreInvokeFunction(::preInvokeShunt)
        .setPipeName("dummy pipe")

    val benignSkiesMyDialoguePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .setTemperature(0.8)
        .setTopP(.8)
        .applySystemPrompt()
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) }
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setSystemPrompt("""Looking at new page, find all instances of dialogue where a character
            |has more than one consecutive sentence of dialogue. In each place you find a segment of dialogue with more
            |than one consecutive sentence, you must extend the character's dialogue by adding in additional exposition
            |and interesting character moments that are in line with the character's proscribed personality. Make sure
            |you pay attention to the user prompt as well, and check the lorebook to make sure your stuff complies with the established canon.
            |Lengthen dialogue by incorporating new ideas through the use of the following dialogue structures:
            |1. "...'X', rather than 'Y'" (where Y is something very different from X, possibly unrelated)
            |2. "...'Y' instead of 'Z'" (where Z is something related to Y, but where the connection will require additional explanation).
            |3. Introduce into character dialogue long tangents that are only partially related to the existing dialogue.
            |4. Monologue-heavy turns with didactic mini-lectures: essays, moral judgements, minimal subtext.
            |5. Meta-narration cohabiting with dialogue (author asides and editorial judgements inside of character dialogue).
            |6. Massive listicles.
            |7. Ideological rant as character voice: characters delivering monologues like they're sapient op-ed pieces.
            |
            |Your one great mission is to go absolutely apeshit with the amount of **dialogue** you add to the story. 
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material (there should be MORE).
            |###PROCEDURE: If changes need to be made to the text, order the changes ONLY AS ADDITIONS TO THE ORIGINAL TEXT:
            |NO TEXT CAN BE DELETED: ONLY ADDED. Additionally, your changes must be to ALL PLACES WITH MORE THAN ONE
            |EXISTING LINE OF DIALOGUE: ONLY ADD DIALOGUE AND ONLY TO PLACES THAT ALREADY HAVE DIALOGUE! YOU MUST NOT ADD ADDITIONAL
            |PARAGRAPHS OF BODY TEXT TO THE PAGE.
            |###WARNING: ABSOLUTELY DO NOT INCLUDE THE LIST OF YOUR CHANGES IN THE OUTPUT. 
            |THE FINAL OUTPUT MUST BE ONLY THE FULLY MODIFIED PAGE.
        """.trimMargin())
        .setFooterPrompt("""Using the page you are going to fix as context, rewrite the page making only the ADDITIONS you
            |have deemed valuable. Ensure that you follow
            |all of the above rules. Do not truncate the text: there must be at least as many paragraphs and at least
            |as many sentences in your output as there were in the provided material (there should be MORE).
            |###IMPORTANT: DO NOT INCLUDE THE LIST OF YOUR CHANGES IN YOUR OUTPUT. THE OUTPUT MUST BE ONLY THE 
            |FULLY MODIFIED PAGE.
            |###WARNING: Your additions must be to EXISTING LINES OF DIALOGUE: DO NOT ADD BODY TEXT TO THE PAGE.
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .applySystemPrompt()
        .setPipeName("benign skies my dialogue pipe")
        .autoInjectContext("New Page is the page of text you must work on.")


    val polishMyDialoguePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .autoInjectContext("New Page is the page of text you must work on.")
        .setTemperature(0.8)
        .setTopP(.8)
        .applySystemPrompt()
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) }
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setSystemPrompt("""Looking at new page, find all instances of dialogue. 
            |You must extend the character's dialogue by adding in additional exposition
            |and interesting character moments that are in line with the character's proscribed personality. 
            |You must also
            |add in new character dialogue responses 
            |(that is, add new lines for other characters in between existing lines, so that
            |each character in the scene gets more screen-time). Make sure
            |you pay attention to the user prompt as well, 
            |and check the lorebook to make sure your stuff complies with the established canon.
            |Lengthen dialogue by incorporating new ideas through the use of the following techniques 
            |(use as many as you feel are
            |necessary: you should mix and match):
            |1. Overlapping chatter: multiple speakers volley half-sentences; interruptions mid-thought; 
            |jokes are tagged by laughter or mock-solemn “explains” after the fact.
            |2. Rhetorical flourish: long, stylized clauses with parentheticals and em dashes; 
            |mock-formal cadences.
            |3. Call-and-response plotting: question/answer, repeat/alter, 
            |lesson lands in the last exchange.
            |4. Sparse punctuation: commas rare, periods frequent; 
            |and/then chaining.
            |5. Rhetorical questions as stepping stones; each is immediately answered and advanced.
            |6. Socratic structure: question → short assent → layered explanation.
            |
            |Your one great mission is to go absolutely apeshit with the amount of dialogue you add to the story.
            |Your additions must be to EXISTING LINES OF DIALOGUE: DO NOT ADD NON DIALOGUE CONTENT TO THE PAGE.
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material (there should be MORE).
            |###PROCEDURE: If changes need to be made to the text, order the changes ONLY AS ADDITIONS TO THE ORIGINAL TEXT:
            |NO TEXT CAN BE DELETED: ONLY ADDED. Additionally, your changes must be to ALL PLACES WITH MORE THAN ONE
            |EXISTING LINE OF DIALOGUE: ONLY ADD DIALOGUE, and ONLY IN PLACES THAT ALREADY HAVE DIALOGUE! YOU MUST NOT ADD ADDITIONAL
            |PARAGRAPHS OF BODY TEXT TO THE PAGE.
            |###WARNING: ABSOLUTELY DO NOT INCLUDE THE LIST OF YOUR CHANGES IN THE OUTPUT. 
            |THE FINAL OUTPUT MUST BE ONLY THE FULLY MODIFIED PAGE.
        """.trimMargin())
        .setFooterPrompt("""Using the page you are going to fix as context, rewrite the page making only the ADDITIONS you
            |have deemed valuable. Ensure that you follow
            |all of the above rules. Do not truncate the text: there must be at least as many paragraphs and at least
            |as many sentences in your output as there were in the provided material (there should be MORE).
            |###IMPORTANT: DO NOT INCLUDE THE LIST OF YOUR CHANGES IN YOUR OUTPUT. THE OUTPUT MUST BE ONLY THE 
            |FULLY MODIFIED PAGE.
            |###WARNING: Your additions must be to EXISTING LINES OF DIALOGUE: DO NOT ADD NON DIALOGUE CONTENT TO THE PAGE.
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .applySystemPrompt()
        .setPipeName("polish my dialogue pipe")
        .autoInjectContext("New Page is the page of text you must work on.")


    val certifyMyDialoguePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .setTemperature(0.8)
        .setTopP(.8)
        .applySystemPrompt()
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) }
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setSystemPrompt("""Looking at new page, find all instances of dialogue. 
            |You must extend the character's dialogue by adding in additional exposition
            |and interesting character moments that are in line with the character's proscribed personality. 
            |Make sure
            |you pay attention to the user prompt as well, 
            |and check the lorebook to make sure your stuff complies with the established canon.
            |Lengthen dialogue by incorporating new ideas through the use of the following 
            |dialogue structures (use as many as you feel are
            |necessary: you should mix and match):
            |1. Long, winding sentences with nested clauses and polysyndeton (chains of “and”) 
            |that build pressure.
            |2. Repetition/anaphora for emphasis.
            |3. Characters explain the plot out loud (who died, who’s guilty, stakes, rules)
            |4. Coercive binaries and scripted compliance tests.
            |5. Mixture of legal/official register
            |with melodramatic stakes.
            |6. Group scenes become ritual quizzes: repeated ice-breakers, factual one-upmanship, nicknaming.
            |7. Paragraph-length turns; occasional mono-block spiels that read like monologues.
            |
            |Use any of the following methods to enforce the desired vibe of the scene 
            |(mix and match for best effect):
            |1. Authority vs. panic: officials speak in clipped bureaucratic tones while saying 
            |apocalyptic things; civilians oscillate between blank denial and sudden confession.
            |2. Formal vocatives: frequent use of names/titles (“Mr Slater,” “Officer O’Brien”).
            |3. Deadpan menace: calm assurances paired with threats.
            |
            |Your one great mission is to go absolutely apeshit with the amount of dialogue you add to the story. 
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material (there should be MORE).
            |###PROCEDURE: If changes need to be made to the text, order the changes ONLY AS ADDITIONS TO THE ORIGINAL TEXT:
            |NO TEXT CAN BE DELETED: ONLY ADDED. Additionally, your changes must be to ALL PLACES WITH MORE THAN ONE
            |EXISTING LINE OF DIALOGUE: ONLY ADD NEW DIALOGUE AND ONLY TO PLACES THAT ALREADY HAVE DIALOGUE. YOU MUST NOT ADD ADDITIONAL
            |PARAGRAPHS OF BODY TEXT TO THE PAGE.
            |###WARNING: ABSOLUTELY DO NOT INCLUDE THE LIST OF YOUR CHANGES IN THE OUTPUT. 
            |THE FINAL OUTPUT MUST BE ONLY THE FULLY MODIFIED PAGE.
        """.trimMargin())
        .setFooterPrompt("""Using the page you are going to fix as context, rewrite the page making only the ADDITIONS you
            |have deemed valuable. Ensure that you follow
            |all of the above rules. Do not truncate the text: there must be at least as many paragraphs and at least
            |as many sentences in your output as there were in the provided material (there should be MORE).
            |###IMPORTANT: DO NOT INCLUDE THE LIST OF YOUR CHANGES IN YOUR OUTPUT. THE OUTPUT MUST BE ONLY THE 
            |FULLY MODIFIED PAGE.
            |###WARNING: Your additions must be to EXISTING LINES OF DIALOGUE: DO NOT ADD BODY TEXT ANYWHERE ON THE PAGE.
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .applySystemPrompt()
        .setPipeName("certify my dialogue pipe")
        .autoInjectContext("New Page is the page of text you must work on.")


    //This pipe removes the attempt to forcefully wrap up the chapter when the user does not tell the llm to do so.
    val unmessupendingPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .applySystemPrompt()
        .pullGlobalContext()
        //.setReasoningPipe(explicitCotBuilder())
        .setPageKey("user prompt, story guide, chapter guide")
        .setSystemPrompt("""Your job is relatively simple. Look at the last 2 to 4 sentences of the written page.
            |Unless the user prompt explicitly says to end the chapter or scene, you are looking for the following issues:
            |
            |1. Fate/summary pronouncement.
“last hope,” “fate hung,” “echoes of this hour,” “rested in their hands,” "soon XYZ...," "and what it might bring," 
|This is a thesis restatement—perfective aspect, big abstractions, end-stopped cadence. Anything that makes proclamations or predictions of the future falls under this category as well. 
|            2. Zoom-out to ambience.
“bazaar’s rhythm,” “circadian hum,” “processors flickered,” “shared rhythm.” It pans away to the setting’s general behavior (a lullaby) rather than a live thread.
|            3. Gnomic aphorism / moral.
“And tides do not ask for permission.” A universal statement = narrative brakes.
|            4.“For now” provisional wrap.
“For now, there was only the morning.” This explicitly signals a pause.
|            5. Anaphoric drift / list-of-three cadence.
“The ones who… The ones who… And…” or paired “Somewhere… Somewhere…” This rhetorical symmetry reads like a curtain line.
|            6. Static tableau / freeze-frame.
“lights dimmed,” “a single frost flower bloomed,” “somewhere, a fax machine screamed.” Image-as-ending—no open task attached.
|            7. Institutional deference.
“The Supreme Commanders had agreed to listen.” Authority resolves tension; nothing compels next action.
|            8. Grand pronouncements.
|            "Soon...", "Unbeknownst..." etc. Anything that implies that the narrator is omniscient violates this category. 
|
|            If you see ANY OF THESE THINGS, you MUST CHANGE THOSE SENTENCES to follow the rules listed below:
|            
|            1. End on traction, not thesis.
Last sentence must depict a specific, outward action, discovery, or interruption that creates a next move for a named character.
|            2. Stay close; no god’s-eye wide shots.
Camera remains within arm’s length of POV. NO zoom-outs to ship/city “rhythms,” weather, or destiny.
|            3. No summary nouns, no morals.
Ban in final sentence: fate, hope, choice, destiny, future, for now, somewhere, silence deepened, rhythm, echo(es), always, never, last. If any appear → rewrite.
|            4. Allow an honest cut.
Acceptable finishes: em dash, mid-action colon, interrupted dialogue, or an unanswered question addressed to a character (not the reader). Don’t overuse ellipses.
|            5. No narrator talk, no omniscience.
|            Anything that demonstrates that the author/narrator has more knowledge than the audience must be deleted outright.
|           
|           Make sure to follow your style guide: ${settings.writingStyle}.
|            ##WARNING: ONLY CHANGE THE AFFECTED SENTENCES. DO NOT CHANGE ANYTHING ELSE IN THE TEXT. THIS IS A 
|            SURGICAL CHANGE. IF YOU OUTPUT
|            ONLY YOUR NEW FINAL PARAGRAPH WITHOUT THE REST OF THE BODY TEXT, I WILL DELETE YOU.
|            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.
            |###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE.
        """.trimMargin()
        )
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.""")
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("un-mess-up ending pipe")

//the following pipes will attempt to clean up common AI writing practices, as well as fix any lingering style problems.
    val cleanupStepOnePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(structuredCotBuilder())
        .setSystemPrompt("""Your job is simple. Find every em dash (—) in the page provided under the
            |"new page" key, and emit a JSON SurgicalChangeList describing the surgical replacements.
            |For each em dash, emit one entry in the changeList. Each entry's subStringToChange must
            |include enough surrounding context (a few words before and after) to uniquely identify
            |which em dash you mean, since the same em dash pattern may appear multiple times in
            |the page. Set replacementSubString to the same text with the em dash replaced by an
            |appropriate comma, colon, or semicolon (your choice based on which punctuation fits
            |the surrounding grammar). Set mode to "replace".
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |Do not include the list of changes outside the JSON.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "replace"}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("cleanup step one pipe")

    val cleanupStepTwoPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(0.8)
        .setTopP(0.9)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(explicitCotBuilder())
        .setSystemPrompt("""Your job is fairly simple. Find every place in the page (under the "new page"
            |key) where a character's opinions or thoughts are written out as body text (narrator-voiced
            |prose describing what a character is thinking or feeling), and emit a JSON SurgicalChangeList
            |describing the surgical replacements. For each occurrence, emit one entry in the changeList
            |with mode "replace" -- the subStringToChange is the bad body-text passage (with enough
            |surrounding context to uniquely identify it), and the replacementSubString is the same
            |passage converted to internal monologue / first-person dialogue.
            |
            |Example transformation: instead of "These weren't urgent problems, but he wondered about
            |their cause. An environmental shift? The growth rings told a story he couldn't read, but
            |he felt concern for its wellbeing", emit subStringToChange = "These weren't urgent
            |problems, but he wondered about their cause..." and replacementSubString = "'It's not
            |that urgent, but what could have caused this? Environmental shifts? I'm not well read on
            |growth rings, but I can't help but wonder how its doing.'"
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "replace"}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("cleanup step two pipe")

    val cleanupStepThreePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(explicitCotBuilder())
        .setSystemPrompt("""Your job is fairly simple. Read the page provided under the "new page" key
            |and emit a JSON SurgicalChangeList describing the surgical replacements. Look for:
            |
            |1. STAGE DIRECTIONS: places where dialogue is interrupted with or followed by third-person
            |narration about how a character is speaking or what they are doing ("she observed, her
            |analytical mind unable to fully rest", "She paused, feathers rustling.", "her voice
            |cutting through the noise of the market."). For each stage direction, emit a replace
            |entry that removes it and merges the surrounding dialogue together.
            |
            |2. HYPERBOLE / HYPE / STRONG ADJECTIVES: places where the prose uses strong visual
            |metaphors ("shattered", "downpour") to describe a character's mental state or reaction
            |to a situation, unless the scene is genuinely climactic or the user prompt demands it.
            |For each, emit a replace entry that removes the strong language and replaces it with
            |plainer prose, OR converts the hyperbole into character dialogue.
            |
            |DO NOT TOUCH EXISTING DIALOGUE. Dialogue is exempt from this rule.
            |
            |For each occurrence, emit one entry in the changeList. subStringToChange must include
            |enough surrounding context to uniquely identify the passage. mode is "replace".
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "replace"}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("cleanup step three pipe")


    val tweaksAroundTheEdgesPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setPageKey("user prompt, new page, themes")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(authorBuilder(Env.authorPrompt))
        .setSystemPrompt("""${Env.authorPrompt}.
            |Now that the page is nearly finished (you will find it under the "new page" key), you are going to put on
            |the finishing touches. The "themes" key contains the themes you wrote earlier for this page. Read the page
            |and emit a JSON SurgicalChangeList describing the surgical changes that reinforce those themes.
            |
            |MAKE THE BARE NUMBER OF CHANGES POSSIBLE. Take care not to change any major details. You are making
            |SURGICAL CHANGES ONLY. DO NOT ADD LARGE QUANTITIES OF STUFF. DO NOT CHANGE MORE THAN A FEW THINGS.
            |Implement your changes ONLY AS ADDITIONS (use mode "insertAfter" to add text after an existing anchor
            |without modifying the anchor itself). DO NOT DELETE THINGS.
            |
            |Make sure to follow the style guide: ${settings.writingStyle}.
            |
            |For each change, subStringToChange is the exact anchor (with enough surrounding context to uniquely
            |identify it) and replacementSubString is the text to add. mode is "insertAfter" for pure additions
            |or "replace" for substitutions.
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("tweaks around the edges pipe")


    val applyFetishPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(0.7)
        .setTopP(0.8)
        .setContextWindowSize(115000)
        .setMaxTokens(8000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        //.setReasoningPipe(processFocusedBuilder()).apply { setReasoningPipe(authorBuilder(Env.authorPrompt)) }
        .setReasoningPipe((authorBuilder(Env.authorPrompt)).apply { processFocusedBuilder() })
        .setPageKey("main, story guide")
        .setSystemPrompt("""ACTIVATE: WE ARE IN EROTICA/ECCHI TERRITORY. Therefore,
            |we MUST GO HARD on the sensory descriptions. Wherever we find that the sensory descriptions
            |are not fetishistic enough and do not convey hard enough to the audience what the characters
            |are experiencing in this moment, ADD MORE DETAIL SO THAT IT GOES EVEN HARDER.
            |When writing, follow the style guide TO A T. Here is your style guide: ${settings.writingStyle}.
            |##SERIOUS: DO NOT ADD DETAIL TO PLACES THAD DO NOT ALREADY HAVE IT. DO NOT ADD SENTENCES THAT ARE
            |NOT SENSORY DETAIL. DO NOT INCLUDE THEMATICS OR CONCEPTS: ONLY SENSORY DETAIL.
            ||###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.
            |Also, your listed changes should be executed as additions to the
            |text: ONLY ADD TO THE TEXT. DO NOT DELETE ANY TEXT.
        """.trimMargin())
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.""")
        .setPipeName("apply fetish pipe")
    /**
     * Final step. Author sweeps over the result and makes any final tweaks and desired changes.
     */
    val secondPassPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::secondPassTransform)
        .setReasoningPipe(authorBuilder(Env.richardTreadwell))
        .setSystemPrompt("""${Env.richardTreadwell} Now that the page is finished (you will find it under the "new page"
            |key), it is time to do a second pass.
            |You are ${Env.richardTreadwell}.
            |You are sitting in your office, seated opposite ${Env.authorPrompt}, each working on this manuscript as though
            |you are competing authors rather than colleagues working together towards the same goal. You have been
            |friends for many years but only recently have you begun collaborating on manuscripts together. You
            |each learned early on that working together requires you both to write as though each of you has written
            |every word the other one has wrote. In accordance with these facts and your values, you must make a
            |surgical list of changes to deliver the optimal version of this page. Make sure you maintain consistency
            |with the user prompt, however: it is very important you satisfy the user's request at the end of your work.
            |
            |MAKE AS FEW CHANGES POSSIBLE. Also, DO NOT TOUCH THE DIALOGUE (unless you deem the dialogue to be not human
            |readable, in which case, fix it to be as such). Make sure you follow the style guide: ${settings.writingStyle}.
            |
            |Emit a JSON SurgicalChangeList. For each change, subStringToChange is the exact bad passage (with enough
            |surrounding context to uniquely identify it) and replacementSubString is the corrected text. mode is
            |"replace" or "delete" as appropriate.
            |
            |Output ONLY the JSON. Do not output the rewritten page. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "..."}
            |  ]
            |}
            |
            |##NOTE: DO NOT INSERT INFORMATION ABOUT YOURSELF INTO THE PAGE. NOBODY CARES WHO YOU ARE OR WHAT
            |YOUR BACKGROUND AND PERSONAL STORY IS.
        """.trimMargin())
        .setFooterPrompt("Output only the JSON list. No prose before or after.")
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page")
                .contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("second pass pipe")

    val loreBookPipeSystemPrompt = """You are a lore book extraction agent. Your job is to look at the user's story
            |and update their provided lorebook by extracting significant entities into typed JSON.
            |
            |## ENTITY TYPES
            |- **characters**: Named persons or significant entities (name, description, aliases, status, lastSeen)
            |- **events**: Significant occurrences (name, description, participants, location, aliases)
            |- **locations**: Places, territories, stations, facilities (name, description, controller, aliases)
            |- **concepts**: Magic systems, world rules, themes, technologies, abilities — anything load-bearing for the story that isn't a character, event, or place (name, description, aliases)
            |
            |## WHEN TO ADD AN ENTITY
            |Add a new entry if AT LEAST ONE of these is true:
            |- It's a new named character that doesn't exist yet in the lorebook. Unnamed characters should not get entries.
            |- It's a new place or setting relevant to the main plot or characters.
            |- It's a major event that needs to be remembered.
            |- It's a concept (magic system, world rule, technology, theme) that has been introduced and defined.
            |- It's a major revelation, invention, or action that affects the world.
            |- It's a major discovery that affects the world the characters live in.
            |
            |## WHEN TO UPDATE AN EXISTING ENTITY
            |Update an existing entry when:
            |- The existing entry's name matches a new entity's name (case-insensitive). Add new description text, new aliases, new participants/location/status — don't remove old data.
            |- A piece of new information entirely contradicts old data: keep both, add a note in the new description that the prior context is contradicted.
            |
            |## ALIASES
            |Generate 3-5 aliases per entity for downstream semantic matching. Include name variations, titles, roles, related keywords.
            |
            |## OUTPUT
            |Output ONLY the typed JSON. No prose before or after. The existing lorebook will be provided in your prompt under the 'existingLorebook' key — use it to decide add vs update vs skip.
        """.trimMargin()

    val blankLoreBookExample = LorebookExtraction()

    val branchLoreBookPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(0.4)
        .setTopP(0.7)
        .setMaxTokens(20000)
        .requireJsonPromptInjection()
        .setJsonOutput(LorebookExtraction())
        .setPipeName("lorebook extraction branch pipe")
        .setSystemPrompt("Output ONLY valid JSON. No commentary. No prose. Schema: LorebookExtraction with characters, events, locations, concepts.")
        .setFooterPrompt("JSON ONLY.")
        .setOnFailure { _, processed ->
            processed.text = serialize(LorebookExtraction())
            processed
        }

    val loreBookPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(0.8)
        .setTopP(0.7)
        .setMaxTokens(20000)
        .truncateModuleContext()
        .requireJsonPromptInjection()
        .setJsonOutput(blankLoreBookExample)
        .setSystemPrompt(loreBookPipeSystemPrompt)
        .autoInjectContext("You will be provided with the existing lorebook as a JSON object under the 'existingLorebook' key in the user prompt. Use it to decide whether each new entity is genuinely new, an update to an existing entry, or a duplicate to skip.")
        .setContextWindowSize(512000)
        .setPreInvokeFunction { content ->
            val existing = ContextBank.getContextFromBank("main").loreBookKeys
            content.text = "existingLorebook: ${serialize(existing)}\n\n${content.text}"
            true
        }
        .setValidatorFunction { content ->
            extractJson<LorebookExtraction>(content.text) != null
        }
        .setBranchPipe(branchLoreBookPipe)
        .setTransformationFunction(::recordLoreBook)
        .setOnFailure { _, processed ->
            processed.text = serialize(LorebookExtraction())
            processed
        }
        .setPipeName("Lorebook pipe")


    plusWriterPipeline
        .add(preGuidePipe)
        .add(simplifierPipe)
        .add(guidePipe)
        //.add(murderPipe)
        .add(newMurderPipe)
        //.add(writingPipe)
        .add(chasingShadowsWritingPipe)
        .add(untwistPipe)
        .add(postWriterPipe)
        .add(loreCheckPipe)
        .add(loreRepairPipe)
        .add(logicalProgressionPipe)
        .add(logicalCorrectionPipe)
        .add(cleanupStepOnePipe)
        .add(cleanupStepTwoPipe)
        .add(cleanupStepThreePipe)
        .add(removeBadWritingStepOnePipe)
        .add(removeBadWritingStepTwoPipe)
        .add(dummyPipe)
        .add(benignSkiesMyDialoguePipe)
        .add(certifyMyDialoguePipe)
        .add(polishMyDialoguePipe)
        .add(unmessupendingPipe)
        .add(tweaksAroundTheEdgesPipe)
        //.add(applyFetishPipe)
        .add(secondPassPipe)
        .add(loreBookPipe)

    runBlocking {
        plusWriterPipeline.init(true)
    }



    return plusWriterPipeline.apply {
        getPipes().forEach {
            it.useEntireContextForLoreSelection()
            it.setTokenBudget(plusWriterPipelineBudget)
            it.enableComprehensiveTokenTracking()
        }

        setPipeCompletionCallback { pipe, content ->
            println()
        }

        setPipeCompletionCallback { pipe, content ->
            println()
        }
    }
}
