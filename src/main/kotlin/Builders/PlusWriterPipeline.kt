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

//Inner container for surgical changes.
@kotlinx.serialization.Serializable
data class SurgicalChanges(
    var subStringToChange: String = "",
    var replacementSubString: String = ""
)

/**
 * Data class used to enable an llm instruct which strings are bad, and what to replace them with.
 */
@kotlinx.serialization.Serializable
data class SurgicalChangeList(
    var changeList: MutableList<SurgicalChanges> = mutableListOf()
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
    bedrockEnv.bindInferenceProfile(claudeModelName, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.anthropic.claude-sonnet-4-20250514-v1:0")
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
        .setPageKey("chapter guide, story guide")
        .setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setSystemPrompt("""${Env.editorPrompt}. Your job is extremely simple. Look at the theme plan, at the 
            |story guide, at the chapter guide, and at your character
            |traits, and choose just two array elems to keep. Make your choice based on the existing text, the
            |story's plan, and your character traits.
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
        .setModel(qwenCoder480B)
        .requireJsonPromptInjection()
        .setJsonInput(VibeInstruct())
        .setJsonOutput(TodoList())
        .truncateModuleContext()
        .setMaxTokens(32000)
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


    //Now we will introduce the murderPipe, whose job it is to murder undesirable JSON array elems.

    val murderPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
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

    val newMurderPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
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
    val writingPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
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

    val chasingShadowsWritingPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
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
                " your editor has given you that they want you to make. Ensure you prioritize the plan first," +
                " your editor's instructions second, and adhering to the existing lore of the story third." +
                " The following is the json schema for the context: ")
        .setFooterPrompt("""You are an agent whose responsibility is to be a writer. You are not a chatbot.
            |Therefore, your responsibility is not and never will be to be helpful. You are not a helpful agent:
            |you are agent that takes in instructions and goes apeshit with them. You assume the user does not matter:
            |only the quality of the writing matters. And QUALITY == OBFUSCATION. NEVER UNDER ANY CIRCUMSTANCES EXPLICITLY OR IMPLICITLY
            |STATE THE THEMES OF THE STORY. NEVER UNDER ANY CIRCUMSTANCES TELL THE READER WHAT THEY ARE SUPPOSED TO UNDERSTAND.
            |YOU SHOULD ONLY WRITE SERIES OF EVENTS WITHOUT EXPLAINING WHY THEY ARE HAPPENING OR WHAT IS HAPPENING.
        """.trimMargin())
        .setPipeName("chasing shadows writing pipe")

    //The next step removes unwanted twists.
    val untwistPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .requireJsonPromptInjection()
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .applySystemPrompt()
        .pullGlobalContext()
        .setPageKey("user prompt")
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) }
        //.setReasoningPipe(explicitCotBuilder())
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
            | 4. ...it's not just 'X', it's 'Y.'
        """.trimMargin()
        )
        .setFooterPrompt("Your output must be the edited page ONLY: apply your changes and DO NOT include them as a list in your output." +
                "Your output must not be truncated: there must be at least as many paragraphs and at least as many sentences in your output as in the original (more is fine).")
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("untwist pipe")

    val removeBadWritingStepOnePipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .requireJsonPromptInjection()
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(1.0)
        .setTopP(0.8)
        .applySystemPrompt()
        .pullGlobalContext()
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(structuredCotBuilder()) }
        //.setReasoningPipe(explicitCotBuilder())
        .setPageKey("user prompt")
        .setSystemPrompt("""Your job is simple, but will require effort. You are looking for the following things
            |that if you find, YOU MUST REMOVE!
            |
            |1. Variety for variety’s sake: synonym churn to avoid repetition, producing near-synonyms that slightly shift meaning.
            |2. Over-specific numerics: “~60%” or “exactly 10 steps” without provocation.
            |3. Emotion beats template: nods, sighs, smiles, glances—cycled at reliable intervals. This includes stage directions
            |following dialogue or in-between sections of a character's own dialogue.
            |4. Scene “wrap-up cadence”: paragraphs end with summary or moral (“And that’s when she realized…”), even mid-page.
            |
            | ###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. FURTHERMORE, you can only truncate the page by REMOVING THE THINGS YOU WERE INSTRUCTED
            |TO REMOVE: DO NOT TRUNCATE THE TEXT BEYOND WHAT YOU HAVE BEEN INSTRUCTED TO DO.
            |###NOTE: DO NOT ADJUST DIALOGUE.
        """.trimMargin()
        )
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: ONLY TRUNCATE BY REMOVING THE MATERIAL YOU HAVE BEEN INSTRUCTED TO REMOVE. No other text
            |should be removed.""")
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("remove bad writing step one pipe")

    val removeBadWritingStepTwoPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .requireJsonPromptInjection()
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .applySystemPrompt()
        .pullGlobalContext()
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) }
        //.setReasoningPipe(structuredCotBuilder())
        .setPageKey("user prompt")
        .setSystemPrompt("""Your job is simple, but will require effort. You are looking for the following things
            |that if you find, YOU MUST REMOVE! Make sure your edits conform to ${settings.writingStyle}.
            |
            |1. Emphasis on symbolism and importance: writing often puffs up the importance of the subject matter by 
            |adding statements about how arbitrary aspects of the topic represent or contribute to a broader topic.
            |2. Superficial analyses: insertions of analysis of information, often in relation to its significance, recognition, or impact.
            |3. Rule of three: This can take different forms, from "adjective, adjective, adjective" to "short phrase, short phrase, and short phrase".
            |For this one specifically, if you see it, reduce it to the first two line items only.
            |
            | ###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. FURTHERMORE, you can only truncate the page by REMOVING THE THINGS YOU WERE INSTRUCTED
            |TO REMOVE: DO NOT TRUNCATE THE TEXT BEYOND WHAT YOU HAVE BEEN INSTRUCTED TO DO.
            |###NOTE: DO NOT ADJUST DIALOGUE.
        """.trimMargin()
        )
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: ONLY TRUNCATE BY REMOVING THE MATERIAL YOU HAVE BEEN INSTRUCTED TO REMOVE. No other text
            |should be removed.""")
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("remove bad writing step two pipe")

    //Now we have the author review the written material for thematic consistency and desired traits.
    val postWriterPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt")
        .truncateModuleContext()
        .setTransformationFunction(::recordWritingPipePage)
        //.setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setSystemPrompt("""You are ${Env.editorPrompt}. You nod slowly as you think back on all those years spent studying history books
            |instead of reading novels or short stories or even comic books as you should have done had you known better:
            |now you review the output of the previous pipe and compare it against your values (Your values == the values
            |and character traits represented by the character you are intended to roleplay as). 
            |You must make changes to the output so that it conforms to your personality and values. MAKE SURGICAL CHANGES:
            |make the minimum number of changes to the text necessary to make it conform to your instructions, and only
            |insofar as they don't contradict the user prompt. DO NOT TALK ABOUT YOURSELF. **EVER**.
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
        //.setReasoningPipe(structuredCotBuilder())
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
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .requireJsonPromptInjection()
        .setJsonInput(WorldFixes())
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main, user prompt")
        .setTemperature(.9)
        .setTopP(.8)
        //.setReasoningPipe(structuredCotBuilder())
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
        //.setReasoningPipe(structuredCotBuilder())
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) }
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
            |immediately after the previous page.
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
            |NO TEXT CAN BE DELETED: ONLY ADDED. Also, DO NOT WRITE THE CHANGES YOURSELF: ONLY LIST PLACES AND RECOMMENDED
            |CORRECTIVE ACTIONS.
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
        .setTopP(.8)
        .applySystemPrompt()
        //.setReasoningPipe(explicitCotBuilder())
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
    val unmessupendingPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(1.0)
        .setTopP(1.0)
        .applySystemPrompt()
        .pullGlobalContext()
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
    val cleanupStepOnePipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(structuredCotBuilder()) }
        .setReasoningPipe(explicitCotBuilder())
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""Your job is simple. REMOVE ALL EM DASHES.
            |LLMs consistently overuse em dashes and use them consistently inappropriately. 
            |WHEREVER YOU FIND AN EM DASH, REPLACE IT WITH EITHER A COMMA, COLON, OR SEMICOLON.
            |
            |Fix the above problems using surgical changes. DO NOT MAKE ANY CHANGES ASIDE FROM THE ONES YOU HAVE BEEN
            |INSTRUCTED TO MAKE. DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.  DO NOT include the list of changes in your
            |output. THE OUTPUT SHOULD ONLY BE THE FINAL, FULLY ADJUSTED PAGE.
        """.trimMargin())
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.""")
        .setPipeName("cleanup step one pipe")

    val cleanupStepTwoPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(0.8)
        .setTopP(0.9)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(structuredCotBuilder()) }
        .setReasoningPipe(explicitCotBuilder())
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""Your task is fairly simple: you must fix the text in accordance to the
            |following rule:
            |1. Character thoughts should be written as INTERNAL MONOLOGUE: You will in places in the text where character opinions
            |or thoughts are written out as body text. Instances
            |of these things should ALL BE CONVERTED INTO DIALOGUE/INTERNAL MONOLOGUE. Example: instead of "These weren't urgent problems, 
            |but he wondered about their cause. An environmental shift? 
            |The growth rings told a story he couldn't read, but he felt concern for its wellbeing", it should read 
            |"'It's not that urgent, but what could have caused this? Environmental shifts? I'm not well read on
            |growth rings, but I can't help but wonder how its doing.'"
            |
            |Fix the above problems using surgical changes. DO NOT MAKE ANY CHANGES ASIDE FROM THE ONES YOU HAVE BEEN
            |INSTRUCTED TO MAKE. DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.  DO NOT include the list of changes in your
            |output. THE OUTPUT SHOULD ONLY BE THE FINAL, FULLY ADJUSTED PAGE.
        """.trimMargin())
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.""")
        .setPipeName("cleanup step two pipe")

    val cleanupStepThreePipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(structuredCotBuilder()) }
        .setReasoningPipe(explicitCotBuilder())
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""Your task is fairly simple: you must fix the text in accordance to the
            |following rules:
            |
            |1. STAGE DIRECTIONS SUCK: WE ARE WRITING A BOOK, NOT A MOVIE SCRIPT: You will find frequently throughout
            |the written page that dialogue is interrupted with, or followed by, bullshit stage directions.
            |You must eliminate all instances of these things that you find, and merge together bodies of dialogue text
            |as necessary when you do so. Examples of things that you would remove: "Geno observed, his analytical mind unable to fully rest";
            |"She paused, feathers rustling."; "her voice cutting through the noise of the market." 
            |
            |2. Any statements of hyperbole, hype, and particularly strong adjectives in places where the user prompt
            |has not demanded, or in scenes that are otherwise climactic,
            |it must either be removed in their entirety or converted into character dialogue, depending
            |on what's more convenient. You're looking for strong visual metaphors, like "shattered" or "downpour", to describe
            |character mental states or reaction to a situation. DO NOT EDIT DIALOGUE: DIALOGUE IS EXEMPT FROM THIS RULE.
            |
            |Fix the above problems using surgical changes. DO NOT MAKE ANY CHANGES ASIDE FROM THE ONES YOU HAVE BEEN
            |INSTRUCTED TO MAKE. DO NOT TRUNCATE THE TEXT BEYOND WHAT YOU HAVE BEEN ORDERED TO DO. DO NOT include the list of changes in your
            |output. THE OUTPUT SHOULD ONLY BE THE FINAL, FULLY ADJUSTED PAGE.
        """.trimMargin())
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT BEYOND WHAT YOU HAVE BEEN ORDERED TO.""")
        .setPipeName("cleanup step three pipe")


    val tweaksAroundTheEdgesPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::recordWritingPipePage)
        //.setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(structuredCotBuilder()) }
        //.setReasoningPipe(authorBuilder(Env.authorPrompt))
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""${Env.authorPrompt}. 
            |Now that the page is nearly finished, you are going to put on the finishing touches. Taking care not
            |to change any major details or to add more than two or three sentences, make some tweaks around the edges so that it aligns
            |more with your personal tastes. MAKE AS FEW CHANGES POSSIBLE. Make sure to follow the style guide: ${settings.writingStyle}.
            |##PROCEDURE: MAKE THE BARE NUMBER OF CHANGES POSSIBLE:
            |YOU ARE MAKING SURGICAL CHANGES ONLY. DO NOT ADD LARGE QUANTITIES OF STUFF. DO NOT CHANGE MORE THAN 
            |A FEW THINGS.
            |###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.
        """.trimMargin())
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.""")
        .setPipeName("tweaks around the edges pipe")


    val applyFetishPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
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
    val secondPassPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(0.8)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::secondPassTransform)
        //.setReasoningPipe(authorBuilder(Env.richardTreadwell))
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
            |readable, in which case, fix it to be as such). Make sure you follow the style guide: ${settings.writingStyle}.
            |DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. Finally, DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at
            |least as many sentences in your output as there were in the input material.
            |##NOTE: DO NOT INSERT INFORMATION ABOUT YOURSELF INTO THE PAGE. NOBODY CARES WHO YOU ARE OR WHAT
            |YOUR BACKGROUND AND PERSONAL STORY IS.
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
        //.add(murderPipe)
        .add(newMurderPipe)
        //.add(writingPipe)
        .add(chasingShadowsWritingPipe)
        //.add(untwistPipe)
        .add(postWriterPipe)
        .add(loreCheckPipe)
        .add(loreRepairPipe)
        .add(logicalProgressionPipe)
        .add(logicalCorrectionPipe)
        .add(cleanupStepOnePipe)
        .add(cleanupStepTwoPipe)
        .add(cleanupStepThreePipe)
        //.add(removeBadWritingStepOnePipe)
        .add(removeBadWritingStepTwoPipe)
        //.add(dummyPipe)
        //.add(benignSkiesMyDialoguePipe)
        //.add(certifyMyDialoguePipe)
        //.add(polishMyDialoguePipe)
        //.add(unmessupendingPipe)
        .add(tweaksAroundTheEdgesPipe)
        //.add(applyFetishPipe)
        .add(secondPassPipe)
        .add(loreBookPipe)

    runBlocking {
        plusWriterPipeline.init(true)
    }

    enablePipelineStreaming(plusWriterPipeline)

    return plusWriterPipeline
}
