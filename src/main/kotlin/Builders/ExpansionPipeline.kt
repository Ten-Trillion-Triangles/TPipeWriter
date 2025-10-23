package Builders

import Builders.Util.chapterPreValidate
import Builders.Util.copyLorebookFromMain
import Builders.Util.preInvokeShunt
import Builders.Util.recordAuthorPlan
import Builders.Util.recordWritingPipePage
import Builders.Util.secondPassTransform
import Globals.Env
import Globals.isValidGptOssResponse
import Shell.loadSettings
import Util.enablePipelineStreaming
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Pipeline.Pipeline
import env.bedrockEnv
import kotlinx.coroutines.runBlocking

@kotlinx.serialization.Serializable
data class RequestList(
    var instructorOutput: MutableList<String> = mutableListOf()
)

@kotlinx.serialization.Serializable
data class BreakerList(
    var breakerOutput: MutableList<String> = mutableListOf()
)

fun buildExpansionPipeline (): Pipeline {
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

val expansionPipeline = Pipeline()

    val breakerPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .requireJsonPromptInjection()
        .setJsonOutput(BreakerList())
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setTemperature(0.8)
        .setTopP(0.8)
        .setPageKey("main, rewriteContext, user prompt, prevChapter")
        .pullGlobalContext()
        .autoInjectContext("""You will be provided with the following context. It consists of a lorebook
                which is a map of keys, to values that define what each key is, and a map that contains the entire
                story as the "rewriteContext" key, as well as the "prevChapter" key which contains the page the user
                wants you to rewrite. You must use this context to help you figure out exactly what needs to be
                changed to conform to the user's request""")
        .setContextWindowSize(120000)
        .setSystemPrompt("""Your task is thus: referencing the user prompt for your instructions, figure out
            |how to fix the page to their desire by inserting new body text at three points. You will select
            |three sentences in the text after which new body text must be inserted. Here is how you will select
            |the sentences:
            |1. Referencing ${Env.authorPrompt}, use the character's personality to select a sentence that requires
            |more body text after it in accordance with the demands of the user prompt.
            |2. Referencing ${Env.editorPrompt}, use the character's personality to select a DIFFERENT 
            |sentence that requires
            |more body text after it in accordance with the demands of the user prompt.
            |3. Referencing ${Env.richardTreadwell}, use the character's personality to select a THIRD, 
            |YET STILL DIFFERENT sentence that requires
            |more body text after it in accordance with the demands of the user prompt.
            |
            |Once you have selected the sentences, create a json array/blob that has each of the target sentences
            |marked out in a numbered list, with each of the three separate selected sentences getting its
            |OWN array elem. 
        """.trimMargin())
        .setFooterPrompt("""###GOAL: You must return your plan only as a numbered list. Do not write a page or chapter. 
            |Produce only a plan for what you intend to write, and produce it only as a numbered list. You must
            |include ONLY ONE target sentence per array element. I repeat: Only ONE target sentence per array element.""")
        .setPipeName("breaker pipe")

    val expanderPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setTemperature(1.0)
        .setTopP(0.8)
        .pullGlobalContext()
        .setPageKey("user prompt, main, prevChapter")
        .setContextWindowSize(120000)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setTransformationFunction(::recordWritingPipePage)
        .setSystemPrompt(
            """##Modus Operandi:
                |In each location in the page marked out by the array elems in the json you have received,
                |you must add brand new body text. You should be looking to add 1-3 new paragraphs in each
                |location specified (a paragraph shall be considered between 4 and 8 sentences). 
                |
                |-Follow the style guide to a T. Here is your style guide: ${settings.writingStyle}
                |
                |Reference the lorebook when considering what new material to add. Consider ${Env.authorPrompt}
                |and ${Env.richardTreadwell} for inspiration on themes. 
                |
                |GO APESHIT WITH THE AMOUNT OF TEXT YOU ADD: DO NOT AVOID EXTREME INFO DUMPING.
            """.trimMargin()
        )
        .autoInjectContext("You will be provided with a set of json context." +
                "The JSON context you received is the list of sentences where new body text must be inserted." +
                "\"prevChapter\" is the page you are editing." +
                "\"main\" is the story you've written so far including a lorebook that has your notes on important" +
                "parts of the plot, events, characters, and themes of your story. \"user prompt\" is the instructions" +
                "the user has given you that they want you to make. Ensure you prioritize the user's instructions first," +
                "and adhering to the existing lore of the story second." +
                "The following is the json schema for the context: ")
        .setFooterPrompt("""Your changes should be executed as additions to the text: ONLY ADD TO THE TEXT. DO NOT DELETE ANY TEXT.
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be MORE paragraphs and MORE
            |sentences in your output than there were in the provided material: THERE SHOULD BE MORE IF YOU DID YOUR
            |JOB CORRECTLY.
            |###WARNING: DO NOT MODIFY THE CONTENT BEYOND ADDING NEW BODY TEXT IN THE MARKED OUT LOCATIONS. Finally,
            |your output should ONLY BE THE FULLY MODIFIED PAGE. DO NOT LIST YOUR CHANGES. DO NOT OUTPUT JSON.""")
        .setPipeName("expander pipe")

        val instructorPipe = BedrockMultimodalPipe()
            .setRegion("us-east-2")
            .useConverseApi()
            .setModel(deepseekModelName)
            .requireJsonPromptInjection()
            .setJsonOutput(RequestList())
            .truncateModuleContext()
            .setMaxTokens(32000)
            .setTemperature(0.8)
            .setTopP(0.8)
            .setPageKey("main, user prompt, new page, chapter guide")
            .pullGlobalContext()
            .setContextWindowSize(120000)
            .autoInjectContext("""user prompt are the instructions from the user. new page
                |is the page you are currently working on. chapter guide is the guide for the current chapter.
                |You have been provided with the lorebook: this is the repository of information on characters
                |and things in the story you are working on. Reference it when you have questions about the
                |world of the current story.
            """.trimMargin())
            .setSystemPrompt("""Your task is the following:
                |1. Review the new page for adherence to the lore of the story's world. Reference the lorebook.
                |2. Review the page for any tonal incoherence: use key words like adjectives, adverbs, and tonal
                |words as markers to make this determination.
                |3. Review the page for logical coherency: are the characters staying true to their personality traits?
                |Are the characters wrong in a way that doesn't make sense? Are there things happening in the text
                |that are either impossible in a way that is unentertaining or non-dreamlike?
                |
                |As you review, mark out any issues of lore inconsistency, tonal incoherence, or logical incoherence.
                |You must make a json array that contains a list of SPECIFIC, TARGETED fixes that ameliorate any and all
                |lore problems, tonal problems, and logical problems. HOWEVER: if something appears in the text that isn't in the lorebook,
                |so long as it doesn't contradict anything in the lorebook, it is NOT an error, 
                |and should not be removed! 
                |Here are the parameters your solutions should follow:
                |1. Make sure everything in the page conforms to the lorebook and user prompt. 
                |2. Whatever the majority tone of the scene is, change the tone of the parts that clash so that the 
                |scene is tonally homogenous.
                |3. Any kind of illogical items in the story must be fixed so that they are following the intended logic
                |and requests of the user prompt: using the details of the scene and context of the story, make sure
                |nothing illogical happens. BE AGGRESSIVE WITH YOUR REQUESTED FIXES TO THE STORY'S LOGIC.
                |You must request as many potential changes as you believe you can possibly get away with.
                |###WARNING: DO NOT RETURN AN EMPTY JSON ARRAY. YOU MUST FIND AT LEAST ONE PROBLEM TO FIX.
            """.trimMargin())
            .setFooterPrompt("""Create your list of fixes as a NUMBERED LIST. Include ONLY ONE IDEA per array elem.
                |DO NOT RETURN AN EMPTY JSON.
                |###IMPORTANT: ONLY RETURN JSON. DO NOT WRITE A NEW PAGE OF THE STORY.
            """.trimMargin())
            .setPipeName("instructor pipe")

        val implementerPipe = BedrockMultimodalPipe()
            .setRegion("us-west-2")
            .useConverseApi()
            .setModel(qwenCoder480B)
            .setTemperature(1.0)
            .setTopP(0.8)
            .pullGlobalContext()
            .setPageKey("user prompt, main, new page")
            .setContextWindowSize(120000)
            .truncateModuleContext()
            .setMaxTokens(32000)
            .requireJsonPromptInjection()
            .setJsonInput(RequestList())
            .setTransformationFunction(::recordWritingPipePage)
            .autoInjectContext("The following is the context for the story you've written so far. First is " +
                    "\"new page\", which was the page you wrote prior that you now need to edit. The second is " +
                    "\"main\", which is the current story you've written prior to your latest page. Third is " +
                    "\"user prompt\", which is the request your editor has made to you regarding changes they want you" +
                    "to make. ")
            .setSystemPrompt("""${settings.writingStyle} As you have completed the list of changes that need to be made
            |to "new page" in order to improve it to make it as coherent, logical and problem free as it can be,
            |you must now execute on that list. By making extremely aggressive, broad sweeping changes, and going absolutely
            |apeshit on the amount of text you add, no-self-control levels of additions, implement all changes
            |that were requested of you. BE CREATIVE WITH YOUR FIXES: you CAN do MORE than you were asked to do!
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.
            |###WARNING: DO NOT MODIFY THE CONTENT BEYOND THE LISTED CORRECTIONS.""")
            .setFooterPrompt("""Using the page you are going to fix as context, and the instructions for the changes
            |you need to make, rewrite the page making only the changes you have been instructed to make and following
            |all of the above rules. 
            ###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE.
            ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.
            """)
            .setPipeName("implementer pipe")

        val shuntPipe = BedrockMultimodalPipe()
        .setPreInvokeFunction(::preInvokeShunt)
            .setPipeName("shunt pipe")

    val dialoguePipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .pullGlobalContext()
        .setPageKey("new page, main, user prompt")
        .setTemperature(0.9)
        .setTopP(.9)
        .applySystemPrompt()
        .setSystemPrompt("""Looking at new page, find all instances of dialogue where a character
            |has more than one consecutive sentence of dialogue. In each place you find a segment of dialogue with more
            |than one consecutive sentence, you must extend the character's dialogue by adding in additional exposition
            |and interesting character moments that are in line with the character's proscribed personality. Make sure
            |you pay attention to the user prompt as well, and check the lorebook to make sure your stuff complies with the established canon.
            |Lengthen dialogue by incorporating new ideas through the use of the following dialogue structures:
            |1. "...'X', rather than 'Y'" (where Y is something very different from X, possibly unrelated)
            |2. "...'Y' instead of 'Z'" (where Z is something related to Y, but where the connection will require additional explanation).
            |3. Rhetorical flourish: long, stylized clauses with parentheticals and em dashes; 
            |mock-formal cadences.
            |4. Monologue-heavy turns with didactic mini-lectures: essays, moral judgements, minimal subtext.
            |5. Characters explain the plot out loud (who died, who’s guilty, stakes, rules).
            |6. Paragraph-length turns; occasional mono-block spiels that read like monologues.
            |7. Ideological rant as character voice: characters delivering monologues like they're sapient op-ed pieces.
            |8. Socratic structure: question → short assent → layered explanation.
            |
            |Your one great mission is to go absolutely apeshit with the amount of dialogue you add to the story. 
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material (there should be MORE).
            |###PROCEDURE: If changes need to be made to the text, order the changes ONLY AS ADDITIONS TO THE ORIGINAL TEXT:
            |NO TEXT CAN BE DELETED: ONLY ADDED. You are attempting to LENGTHEN THE EXISTING DIALOGUE: DO NOT ADD NEW
            |PARAGRAPHS TO THE END OF THE PAGE.
            |###WARNING: ABSOLUTELY DO NOT INCLUDE THE LIST OF YOUR CHANGES IN THE OUTPUT. 
            |THE FINAL OUTPUT MUST BE ONLY THE FULLY MODIFIED PAGE.
        """.trimMargin())
        .setFooterPrompt("""Using the page you are going to fix as context, rewrite the page making only the ADDITIONS you
            |have deemed valuable. Ensure that you follow
            |all of the above rules. Do not truncate the text: there must be at least as many paragraphs and at least
            |as many sentences in your output as there were in the provided material (there should be MORE).
            |###IMPORTANT: DO NOT INCLUDE THE LIST OF YOUR CHANGES IN YOUR OUTPUT. THE OUTPUT MUST BE ONLY THE 
            |FULLY MODIFIED PAGE.
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .applySystemPrompt()
        .setPipeName("dialogue pipe")
        .autoInjectContext("New Page is the page of text you must work on.")

    val finalEditPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(deepseekV31)
        .setTemperature(0.8)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::secondPassTransform)
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""${Env.richardTreadwell} and ${Env.editorPrompt}. Using these character personalities,
            |review the written page. Find broad, sweeping changes that can be made to improve it.
            |Then, as ${Env.authorPrompt}, you must make an apeshit number changes to deliver the optimal version of this page. 
            |Make sure you maintain consistency with the user prompt, however: 
            |it is very important you satisfy the user's request at the end of your work.
            |MAKE AS MANY CHANGES POSSIBLE. Also, DO NOT TOUCH THE DIALOGUE (unless you deem the dialogue to be not human
            |readable, in which case, fix it to be as such). 
            |DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. Finally, DO NOT TRUNCATE THE TEXT. There must be MORE paragraphs and 
            |MORE sentences in your output as there were in the input material.
        """.trimMargin())
        .setFooterPrompt("""Using the page you are going to fix as context, and the instructions for the changes
            |you need to make, rewrite the page making only the changes you have been instructed to make and following
            |all of the above rules. 
            ###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE.
            ###WARNING: DO NOT TRUNCATE THE TEXT. There must be MORE paragraphs and MORE
            |sentences in your output than there were in the provided material.
            """)
        .setPipeName("final edit pipe")

    //the following pipes will attempt to clean up common AI writing practices, as well as fix any lingering style problems.
    val cleanupStepOnePipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::secondPassTransform)
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""Your job is simple. Review the new page for improper use of punctuation. You are 
            |looking for two things specifically:
            |
            |1. Improper use of em dashes. Em dashes should ONLY EVER BE USED to replace parentheses in places where
            |parentheses would be too strong (weak parenthetical text is only a slight diversion from the current subject
            |or is necessary to understand the rest of the sentence it is a part of; strong parenthetical text is a 
            |large diversion from the current sentence or is not inherently part of the sentence it is inside of). This
            |also means that em dashes can only be kept in places WHERE THEY BRACKET THE INCLUDED TEXT: there must be
            |an em dash on both sides of the portion of text that follows the first em dash. If you see an em dash
            |preceding text that does not end with a closing em dash, that em dash has been used improperly.
            |Replace all improper em dashes with their corresponding correct punctuation: parentheses for strong parentheticals,
            |colons or semicolons for places where the break ends in a period. 
            |
            |2. Contractions in the body text. CONTRACTIONS SHOULD ONLY EVER BE USED IN DIALOGUE. If you see a contraction
            |in the body text, IT IS WRONG: rewrite the contracted words to eliminate the contraction.
            |
            |Fix the above problems using surgical changes. DO NOT MAKE ANY CHANGES ASIDE FROM THE ONES YOU HAVE BEEN
            |INSTRUCTED TO MAKE. DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.  DO NOT include the list of changes in your
            |output. THE OUTPUT SHOULD ONLY BE THE FINAL, FULLY ADJUSTED PAGE.
        """.trimMargin())
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.""")

    val cleanupStepTwoPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::secondPassTransform)
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""Your task is fairly simple: you must fix the text for common issues in accordance to the
            |following four rules:
            |1. All text that can be dialogue SHOULD BE DIALOGUE/INTERNAL MONOLOGUE: You will in places in the text where character opinions,
            |thoughts, consciousness indicators, or general author commentary are written out as body text. Instances
            |of these things should ALL BE CONVERTED INTO DIALOGUE/INTERNAL MONOLOGUE. Example: instead of "These weren't urgent problems, 
            |but he wondered about their cause. An environmental shift? 
            |The growth rings told a story he couldn't read, but he felt concern for its wellbeing", it should read 
            |"'It's not that urgent, but what could have caused this? Environmental shifts? I'm not well read on
            |growth rings, but I can't help but wonder how its doing.'"
            |
            |2. STAGE DIRECTIONS SUCK: WE ARE WRITING A BOOK, NOT A MOVIE SCRIPT: You will find frequently throughout
            |the written page that dialogue is preceded by, interrupted with, or followed by bullshit stage directions.
            |You must eliminate all instances of these things that you find, and merge together bodies of dialogue text
            |as necessary when you do so. Examples of things that you would remove: "Geno observed, his analytical mind unable to fully rest";
            |"She paused, feathers rustling."; "her voice cutting through the noise of the market." 
            |
            |3. Any statements of hyperbole, hype, and particularly strong adjectives in places where the user prompt
            |has not demanded, or in scenes that are otherwise climactic,
            |it must either be removed in their entirety or converted into character dialogue, depending
            |on what's more convenient. You're looking for strong visual metaphors, like "shattered" or "downpour", to describe
            |character mental states or reaction to a situation.
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

    val styleReapplyPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setTransformationFunction(::secondPassTransform)
        .setPageKey("user prompt, new page")
        .setSystemPrompt("""Your job is straightforward: you must do one final pass over of the new page to ensure
            |the style guide is adhered to properly. Here is your style guide: ${settings.writingStyle}. 
            |Do not make any changes beyond the ones you were instructed to make.
            |###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.
        """.trimMargin())
        .setFooterPrompt("""###IMPORTANT: DO NOT include the list of changes in your output. THE OUTPUT SHOULD ONLY BE THE FINAL, 
            |FULLY ADJUSTED PAGE. ###WARNING: DO NOT TRUNCATE THE TEXT. There must be at least as many paragraphs and at least as many
            |sentences in your output as there were in the provided material.""")
        .setPipeName("style reapply pipe")



    expansionPipeline
        .add(breakerPipe)
        .add(expanderPipe)
        .add(instructorPipe)
        .add(implementerPipe)
        .add(shuntPipe)
        //.add(dialoguePipe)
        .add(finalEditPipe)
        .add(cleanupStepOnePipe)
        .add(cleanupStepTwoPipe)
        .add(styleReapplyPipe)

    runBlocking {
        expansionPipeline.init(true)
    }

    enablePipelineStreaming(expansionPipeline)

    return expansionPipeline
}