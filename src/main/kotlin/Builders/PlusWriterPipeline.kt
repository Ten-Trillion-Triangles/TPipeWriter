package Builders

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
import Shell.loadSettings
import Util.enablePipelineStreaming
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Context.ContextWindow
import com.TTT.Enums.PromptMode
import com.TTT.Pipeline.Pipeline
import env.bedrockEnv
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


fun buildPlusWriterPipeline() : Pipeline
{
    val deepseekModelName = "deepseek.r1-v1:0" //us-east-2
    val claudeModelName = "anthropic.claude-sonnet-4-20250514-v1:0" //us-east-1
    val novaModelName = "amazon.nova-lite-v1:0"
    val novaProModelName = "amazon.nova-pro-v1:0"
    val gptOssModelName = "openai.gpt-oss-20b-1:0" //us-west-2
    val gptOss120bModelName = "openai.gpt-oss-120b-1:0"

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
    bedrockEnv.loadInferenceConfig()
    bedrockEnv.bindInferenceProfile("deepseek.r1-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0")
    bedrockEnv.bindInferenceProfile("amazon.nova-pro-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-pro-v1:0")
    bedrockEnv.bindInferenceProfile("amazon.nova-lite-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-lite-v1:0")
    bedrockEnv.bindInferenceProfile(claudeModelName, "arn:aws:bedrock:us-east-1:521369004927:inference-profile/us.anthropic.claude-sonnet-4-20250514-v1:0")
    bedrockEnv.bindInferenceProfile(llamaMaverick, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama4-maverick-17b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(llama70B, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-3-70b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(llama405B, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.meta.llama3-1-405b-instruct-v1:0")
    bedrockEnv.bindInferenceProfile(PalmyraX5, "arn:aws:bedrock:us-west-2:521369004927:inference-profile/us.writer.palmyra-x5-v1:0")



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

    val preGuidePipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
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

    val simplifierPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(1.0)
        .setTopP(0.3)
        .pullGlobalContext()
        .setContextWindowSize(100500)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(VibeInstruct())
        .setJsonOutput(VibeInstruct())
        .setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setSystemPrompt("""${Env.editorPrompt}. Your job is extremely simple. Look at the theme plan, and at your character
            |traits, and choose just two array elems to keep. Make your choice based on the existing text and 
            |your character traits.
        """.trimMargin())
        .setPipeName("simplifier pipe")
        .applySystemPrompt()


    /**
     * This pipe is responsible for loading the chapter guide, and testing the current
     * story's progress against the chapter guide
     */
    val guidePipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .requireJsonPromptInjection()
        .setJsonInput(VibeInstruct())
        .setJsonOutput(TodoList())
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setPreValidationMiniBankFunction(::logicalProgressionPreValidationMiniBank)
        .setSystemPrompt("""
                ##Modus Operandi
                -Create plan for next page of story based on "chapter guide" and "story guide"
                -Next page plan takes into account user prompt and executes on all requests
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


    //Now we will introduce the murderPipe, whose job it is to murder undesirable JSON array elems.

    val murderPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setTemperature(1.0)
        .setTopP(0.3)
        .pullGlobalContext()
        .setContextWindowSize(100500)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(TodoList())
        .setJsonOutput(TodoList())
        .setPageKey("user prompt")
        .setSystemPrompt("""Your job is extremely simple. Look at the provided page plan, and ask the question:
            |Is the planned sequence an action sequence? Or is it a primarily descriptive sequence? These are the
            |only two types of sequences that exist, so anything you think is something else, is actually one or the other:
            |you can assume that anything that isn't high stakes action (like combat, or a car chase, etc.) is a descriptive
            |sequence. Once you have figured out which one of these things what you're looking at is, do the following:
            |if it is an action sequence, pass the JSON through unchanged; if it is a descriptive sequence, eliminate
            |all array elems from the JSON EXCEPT for TWO array elems: use your best judgement to decide which array elems:
            |it's best if you focus first on array elems that contain things the user prompt is asking for, 
            |and if the user prompt is ambiguous, then focus on the main characters and continue the established narrative.
            |IMPORTANT NOTE: Unless a SPECIFIC twist or revelation is specifically requested by the user prompt, 
            |DO NOT INCLUDE AN ARRAY ELEM THAT INCLUDES A TWIST OR REVELATION.
            |Then pass the new JSON file forwards as your output.
        """.trimMargin())
        .setPipeName("murder pipe")
        .applySystemPrompt()




    /**
     * Second step. After the plan has been created the writing pipe will write the given page using the plan,
     * existing story content, and the "editors note" to execute on the plan for the next page of the story.
     */
    val writingPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(1.0)
        .setTopP(0.6)
        .pullGlobalContext()
        .setPageKey("main, user prompt")
        .setContextWindowSize(120000)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        .setSystemPrompt(
                "##Modus Operandi:\n" +
                "-Write the next page of the story\n\n" +
                "-Follow the plan you wrote for this next page, executing on every part of your plan\n" +
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

    //The next step removes unwanted twists.
    val untwistPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .requireJsonPromptInjection()
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.7)
        .setTopP(0.7)
        .applySystemPrompt()
        .pullGlobalContext()
        .setPageKey("user prompt")
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setSystemPrompt("""Your job is simple, but will require effort. Seek out all twists in the written page 
            |THAT ARE NOT SPECIFICALLY REQUESTED BY THE USER PROMPT OR SUBSTANTIATED BY THE LOREBOOK
            |and remove them. You will have to rewrite certain passages in order to accomplish this. 
            |When you rewrite a passage
            |make sure that the page still has a natural flow and strong progression. 
            |###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. Finally, DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at
            |least as many sentences in your output as there were in the input material.
            | Here are examples of key phrases that indicate you're dealing with an unwanted twist:
            | 1. ...it's not 'X', it's 'Y.' ; ...it wasn't 'X', it was 'Y.'
            | 2. ...it's actually an 'X'. ; ...not 'X': 'Y.'
            | 3. ...it's possible that actually 'Z.'
        """.trimMargin()
        )
        .setFooterPrompt("Your output must be the edited page ONLY: apply your changes and DO NOT include them as a list in your output." +
                "Your output must not be truncated: there must be at least as many paragraphs and at least as many sentences in your output as in the original (more is fine).")
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("untwist pipe")

    //Now we have the author review the written material for thematic consistency and desired traits.
    val postWriterPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(deepseekV31)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt")
        .truncateModuleContext()
        .setTransformationFunction(::recordWritingPipePage)
        .setSystemPrompt("""You are ${Env.editorPrompt}. You nod slowly as you think back on all those years spent studying history books
            |instead of reading novels or short stories or even comic books as you should have done had you known better:
            |now you review the output of the previous pipe and compare it against your values (Your values == the values
            |and character traits represented by the character in ${Env.editorPrompt} who you are intended to roleplay as). 
            |You must make changes to the output so that it conforms to your personality and values. MAKE SURGICAL CHANGES:
            |make the minimum number of changes to the text necessary to make it conform to your instructions, and only
            |insofar as they don't contradict the user prompt.
            |###WARNING: DO NOT MODIFY THE DIALOGUE: LEAVE ALL DIALOGUE UNMODIFIED.
            |###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. Finally, DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at
            |least as many sentences in your output as there were in the input material.
        """.trimMargin())
        .setFooterPrompt("Your output must be the edited page ONLY: apply your changes and DO NOT include them as a list in your output." +
                "Your output must not be truncated: there must be at least as many paragraphs and at least as many sentences in your output as in the original (more is fine).")
        .setPipeName("post writer pipe")

    val loreCheckPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setContextWindowSize(120000)
        .setMaxTokens(20000)
        .setTopP(.8)
        .setTemperature(.8)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main, user prompt")
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setValidatorFunction(::isValidGptOssResponse)
        .requireJsonPromptInjection()
        .setJsonOutput(WorldFixes())
        .setSystemPrompt("You are now reviewing your work to make sure that what you have written " +
                "conforms to your existing world building. You are attempting, and desire at all costs, to avoid plot " +
                "holes. Therefor, you shall now make a plan on how to fix any lore and world building errors in the" +
                "page you have just written. HOWEVER: if something appears in the text that isn't in the lorebook, " +
                "so long as it doesn't contradict anything in the lorebook, it is NOT an error, " +
                "and should not be removed! Likewise, if something is NOT mentioned in the text that has " +
                "an associated lorebook key, it not being there is NOT a lore error, and it should not be added in!")
        .autoInjectContext("You will be provided with a json context object that contains the following: " +
                "\"new page\": This the page you just wrote. \"main\": This is the lorebook that contains all of" +
                "your world building, characters, events, and other important notes about your story so far. " +
                "\"user prompt\": This the request from your editor regarding changes they want to see you make " +
                "to the page you wrote. When writing your plan you must check for the following: \n" +
                "- Check that \"new page\" conforms to the request of \"user prompt\".\n" +
                "- Check that \"new page\" does not contain plot holes with the existing lore in \"main\"\n" +
                "\n If either case confirms that there are issues you need to fix. Include the changes that need" +
                " to be made in your output.")
        .setPipeName("lore check pipe")


    val loreRepairPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .requireJsonPromptInjection()
        .setJsonInput(WorldFixes())
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main, user prompt")
        .setTemperature(.7)
        .setTopP(.7)
        .setPreInvokeFunction(::preInvokeLoreRepairPipe) //Skip this pipe if we don't need any actual changes.
        .setTransformationFunction(::recordWritingPipePage)
        .setSystemPrompt("Currently, you are looking at a revision request that you wrote up prior in the " +
                "form of json. You need to edit the new page you've written and return the edit as your output.")
        .autoInjectContext("The following is the context for the story you've written so far. First is " +
                "\"new page\", which was the page you wrote prior that you now need to edit. The second is " +
                "\"main\", which is the current story you've written prior to your latest page. Third is " +
                "\"user prompt\", which is the request your editor has made to you regarding changes they want you" +
                "to make. ")
        .setFooterPrompt("Using the provided context, you must now make surgical changes to \"new page\" in accordance with the revision request." +
                "Make only the changes specified in the revision request, and NO OTHER CHANGES. Furthermore, do NOT truncate the text:" +
                "there must at least as many paragraphs and at least as many sentences in your output as there were in the provided material." +
                "You must return only the edited story: do NOT output JSON; do not output JSON of ANY KIND.")
        .applySystemPrompt()
        .setPipeName("lore repair pipe")


    /**
     * Logical progression pipe.
     */
    val logicalProgressionPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
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
        .setSystemPrompt("""You are now reviewing your work to determine whether or not it
            |advances the story and has progressed logically since the previous page. Carefully check against
            |the "story guide" and "chapter guide" to ensure that the written page progresses the story towards
            |its next stage and conclusion, and to ensure that the content itself actually makes sense from a 
            |human readable point of view, including making sure written dialogue is written in the way humans
            |normally write dialogue, unless not talking like a human is a feature of a specific character. Carefully
            |check against "last page" to make sure that the content logically follows from and is easy to read 
            |immediately after the previous page. BE AGGRESSIVE with your additions: better to make something that even 
            |could be illogical more clear than to leave it broken.
            |NOTABLE TYPES OF ILLOGICAL PROGRESSION:
            |1. Unexplained time-skips (if we are all of a sudden at a different time of day, 
            |that needs to be either eliminated or explained)
            |2. Unexplained jumps in location (if the character is inexplicably in an entirely different location, 
            |we need to be told how they got there:
            |for example, on a bus when they were in their apartment on the last page, 
            |their transit from their living quarters to the bus needs to be demonstrated)
            |3. Pages that open as though they're the first page of a new chapter rather than a page that follows from the previous
            |existing page.
            |NOTABLE TYPES OF ILLOGICAL WRITING:
            |1. If something has serious ambiguity problems, it should be corrected to eliminate them.
            |###PROCEDURE: If changes need to be made to the text, order the changes ONLY AS ADDITIONS TO THE ORIGINAL TEXT:
            |NO TEXT CAN BE DELETED: ONLY ADDED.
            |Produce requested additions as a numbered list.
            |""".trimMargin())
        .setJsonOutput(WorldFixes())
        .setFooterPrompt("""If no changes need to be made, set the "needsChanges" boolean to false. Also,
            | when suggesting changes, ensure that you are not requesting changes that will result in truncation
            | or substantial content changes.
        """.trimMargin())
        .setPreValidationMiniBankFunction(::logicalProgressionPreValidationMiniBank)
        .setValidatorFunction(::isValidGptOssResponse)
        .applySystemPrompt()
        .setPipeName("logical progression pipe")


    /**
     * Called after the logical progression pipe. If the boolean to ask for changes is true this will be run. If it's false
     * this pipe will be skipped over.
     */
    val logicalCorrectionPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(WorldFixes())
        .setPreInvokeFunction(::preInvokeLoreRepairPipe)
        .setPreValidationMiniBankFunction(::chapterPreValidate)
        .pullGlobalContext()
        .setPageKey("new page")
        .setTemperature(0.8)
        .setTopP(.7)
        .applySystemPrompt()
        .setSystemPrompt("""${settings.writingStyle} As you have completed the list of additions that need to be made
            |to "new page" in order to make it logically follow from the previous parts of the story, 
            |you must now execute on that list. With surgical precision and taking care not to change anything else, 
            |implement all listed additions as listed in your instructions. Ensure that implemented corrections obey
            |both existing lore and your style guide. Again, your listed changes should be executed as additions to the
            |text: ONLY ADD TO THE TEXT. DO NOT DELETE ANY TEXT.
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.
            |###WARNING: DO NOT MODIFY THE CONTENT BEYOND THE LISTED CORRECTIONS. 
        """.trimMargin())
        .autoInjectContext("""You have been provided with a context object that contains the 
            |page you are working on fixing. The json schema for the context is as follows: 
        """.trimMargin())
        .setFooterPrompt("""Using the page you are going to fix as context, and the instructions for the changes
            |you need to make, rewrite the page making only the changes you have been instructed to make and following
            |all of the above rules. Do not truncate the text: there must be at least as many paragraphs and at least
            |as many sentences in your output as there were in the provided material.
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .applySystemPrompt()
        .setPipeName("logical correction pipe")


    val dummyPipe = BedrockMultimodalPipe()
        .setPreInvokeFunction(::preInvokeShunt)
        .setPipeName("dummy pipe")
    

    //This pipe removes the attempt to forcefully wrap up the chapter when the user does not tell the llm to do so.
    val unwrapPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .applySystemPrompt()
        .pullGlobalContext()
        .setPageKey("user prompt, story guide, chapter guide")
        .setSystemPrompt("""Your job is relatively simple. Look at the last 2 to 4 sentences of the written page.
            |Unless the user prompt explicitly says to end the chapter or scene, you are looking for the following issues:
            |
            |1. Fate/summary pronouncement.
“last hope,” “fate hung,” “echoes of this hour,” “rested in their hands.” This is a thesis restatement—perfective aspect, big abstractions, end-stopped cadence.
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
|           
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
        .setPipeName("unfuckery pipe")

    /**
     * Final step. Author sweeps over the result and makes any final tweaks and desired changes.
     */
    val secondPassPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(deepseekV31)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::secondPassTransform)
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""${Env.richardTreadwell} Now that the new page is finished, it is time to do a second pass.
            |You are ${Env.richardTreadwell}.
            You are sitting in your office, seated opposite ${Env.authorPrompt}, each working on this manuscript as though
            |you are competing authors rather than colleagues working together towards the same goal. You have been
            |friends for many years but only recently have you begun collaborating on manuscripts together. You
            |each learned early on that working together requires you both to write as though each of you has written
            |every word the other one has wrote. In accordance with these facts and your values, you must make a 
            |surgical list of changes to deliver the optimal version of this page. Make sure you maintain consistency
            |with the user prompt, however: it is very important you satisfy the user's request at the end of your work.
            |MAKE AS FEW CHANGES POSSIBLE. Also, DO NOT TOUCH THE DIALOGUE (unless you deem the dialogue to be not human
            |readable, in which case, fix it to be as such). 
            |DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. Finally, DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at
            |least as many sentences in your output as there were in the input material.
        """.trimMargin())
        .setPipeName("second pass pipe")

    val loreBookPipeSystemPrompt = """You are a lore book assistant. Your job is to look at a user's story
            |and update their provided lorebook, appending new information to existing entries, and adding new entries
            |when found. When deciding if a new lore book key must be added at least one of the following conditions
            |must be true:
            |
            |- The potential key is a new named character that does not exist yet in the lorebook. 
            |If the character does not have a name they should not be given a lorebook entry.
            |
            |- The potential key is a new place or setting in the story that's relevant to the main plot, or
            |the characters in the story.
            |
            |- The potential key is a very major event in the plot of the story that needs to be remembered.
            |
            |- The potential key is a power, ability, deity, or other intangible but highly important trait or
            |force that a character possesses.
            |
            |- The potential key is a major revelation, invention, or action a character takes that affects the
            |world they live in.
            |
            |- The potential key is a major discovery that affects the world the characters live in.
            |
            |- The potential key is a thing, concept, creature, object, or other concept that has been introduced by
            |either the narrator of the story, or one of the characters and has been defined explaining what it is.
            |
            |When updating existing lorebook keys. Create new text that should be appended to the original, rather than
            |entirely rewriting the original key. If a new piece of context entirely contradicts and old one, make
            |a note of it in the new context you generate to append to it.
        """.trimMargin()

    val blankLoreBookExample = ContextWindow()

    val loreBookPipe = BedrockMultimodalPipe()
        .setRegion("")
        .useConverseApi()
        .enableCaching()
        .setReadTimeout(400)
        .requireJsonPromptInjection()
        .setModel(gptOss120bModelName)
        .setPromptMode(PromptMode.singlePrompt)
        .setTemperature(0.5)
        .setTopP(.5)
        .setMaxTokens(20000)
        .truncateModuleContext()
        .updatePipelineContextOnExit()
        .enableAppendLoreBookScheme()
        .setJsonOutput(blankLoreBookExample)
        .setSystemPrompt(loreBookPipeSystemPrompt)
        .autoInjectContext("The following json schema will be used to supply context for the story. " +
                "The context will be provided in the user's prompt. Use it to assist in deciding how to generate " +
                "lorebook keys and values. MULTIPLY THE WEIGHTS OF ALL LOREBOOK ENTRIES BY 10. THERE SHOULD BE NO NUMBERS LESS THAN 1.")
        .setContextWindowSize(107000)
        .setTransformationFunction(::recordLoreBook)
        .setPipeName("Lorebook pipe")


    plusWriterPipeline
        .add(preGuidePipe)
        .add(simplifierPipe)
        .add(guidePipe)
        .add(murderPipe)
        .add(writingPipe)
        .add(untwistPipe)
        .add(postWriterPipe)
        .add(loreCheckPipe)
        .add(loreRepairPipe)
        .add(logicalProgressionPipe)
        .add(logicalCorrectionPipe)
        .add(dummyPipe)
        .add(unwrapPipe)
        .add(secondPassPipe)
        .add(loreBookPipe)

    runBlocking {
        plusWriterPipeline.init(true)
    }

    enablePipelineStreaming(plusWriterPipeline)

    return plusWriterPipeline
}