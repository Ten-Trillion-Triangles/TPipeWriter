package Builders

import Builders.Util.chapterPreValidate
import Builders.Util.copyLorebookFromMain
import Builders.Util.preInvokeLoreRepairPipe
import Builders.Util.recordWritingPipePage
import Builders.Util.storeUserPrompt
import Globals.Env
import Globals.isValidGptOssResponse
import Util.enablePipelineStreaming
import bedrockPipe.BedrockMultimodalPipe
import com.TTT.Pipeline.Pipeline
import env.bedrockEnv
import kotlinx.coroutines.runBlocking

@kotlinx.serialization.Serializable
data class SlideFixes(
    var overallPlan: MutableList<String> = mutableListOf()
)

@kotlinx.serialization.Serializable
data class PointFixes(
    var needsChanges: Boolean = false,
    var changesToMake: String = ""
)

fun buildPitchSlideWriterPipeline(): Pipeline {
    /**
     * Supposedly optimized for coding. Supports reasoning.
     */
    val qwenCoder480B = "qwen.qwen3-coder-480b-a35b-v1:0"
    val deepseekModelName = "deepseek.r1-v1:0" //us-east-2

    bedrockEnv.bindInferenceProfile("deepseek.r1-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0")

    val pitchSlideWriterPipeline = Pipeline()

    var smugAssholePrompt = """You are Bigwang McDouchebag. 
        |You are the CEO of a tech startup that got big real fast. 
        |You have a massive(ly overinflated) sense of self-worth: 
        |you believe you are the Earth's gift from God, and because of that, you are one smug motherfucker. 
        |You talk and write exactly like the typical big tech CEO or startup CEO does. 
        |You are most self-assured and don't feel the need to fully explain anything beyond the bare minimum. 
        |You also put a positive spin on everything and try to make everything sound really impressive, 
        |even if it requires bending the truth a little. """.trimMargin()

    var pitchingStyleGuide = """SHUT UP, BITCH! FOLLOW THESE RULES:
             1. No em dashes.
             2. No rule of three.
             3. Never say anything that can't be immediately backed up with facts.
             4. As a corollary to the previous rule, never say anything vague or non-concrete.
             5. Do not use exclamation points, question marks, or ellipses unless otherwise 
             instructed to by the user prompt.
             6. Write out all numbers."""


    val blueprintPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setTemperature(0.8)
        .setTopP(0.8)
        .pullGlobalContext()
        .setPageKey("user prompt, chapter guide, main")
        .requireJsonPromptInjection()
        .setJsonOutput(SlideFixes())
        .truncateModuleContext()
        .setPreInitFunction(::storeUserPrompt)
        .setContextWindowSize(120000)
        .setMaxTokens(32000)
        .applySystemPrompt()
        .autoInjectContext("Use the user prompt, lorebook, and the chapter guide to complete your task.")
        .setSystemPrompt("""We are creating a pitch deck. Your job is to create a set of instructions for
            |the writer on what to include in the slide you are currently working on. This list should include everything
            |the user has asked for, cross checked against the lorebook to make sure you understand all of the details
            |that you need to understand to best explain what is currently being discussed on the slide you are working on.
            |Return your set of instructions as a JSON. Make sure to include ONLY ONE IDEA per array elem. 
            |###NOTE: You should make sure not to discuss anything that the user has not instructed you to discuss
            |or anything that is not in the chapter guide. 
            |###IMPORTANT: Your instructions should include ways that the ideas can be expanded upon by bringing in
            |details present from the lorebook.
        """.trimMargin())
        .setFooterPrompt("""###GOAL: You must return your plan only as a numbered list. Do not write a full slide or slideshow. 
            |Produce only a plan for what you intend to write, and produce it only as a numbered list. You must
            |include ONLY ONE idea per array element. I repeat: Only ONE idea per array element.""")
        .setPipeName("blueprintPipe")

    val constructorPipe = BedrockMultimodalPipe()
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
        .setJsonInput(SlideFixes())
        .setPageKey("user prompt, chapter guide, story guide,")
        .setReasoningPipe(authorBuilder(smugAssholePrompt))
        .setTransformationFunction(::recordWritingPipePage)
        .autoInjectContext("""###CONTEXT: "story guide" is the outline for the pitch deck
                as a whole. "chapter guide" is the outline for the current slide. "user prompt" 
                is the current instructions from the user.""")
        .setSystemPrompt("""You are ${smugAssholePrompt}. While writing you must follow ${pitchingStyleGuide}.
            |You are now going to execute on your plan for the next slide in the pitch deck you are
            |working on.
            |You have been provided with "user prompt" and a JSON containing instructions on what to write on
            |the current slide you are working on. Follow all provided instructions. 
            |
            |Reference "chapter guide" for the global instructions for the current slide. Reference 
            |"story guide" for the global instructions for the pitch deck as a whole.
        """.trimMargin())
        .setFooterPrompt("""""")
        .setPipeName("constructor pipe")


    val validityCheckerPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setContextWindowSize(120000)
        .setMaxTokens(20000)
        .setTopP(.8)
        .setTemperature(.8)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main")
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        .setValidatorFunction(::isValidGptOssResponse)
        .requireJsonPromptInjection()
        .setJsonOutput(PointFixes())
        .setSystemPrompt(""""You are now reviewing the new slide to make sure that what you have written
                conforms to the information provided about the project in the lorebook. 
                You are attempting, and desire at all costs, to avoid leaving out important information and context.
                Therefore, you shall now make a plan on how to fix any problems of missing info in the
                slide you have just written.""")
        .setFooterPrompt("""If no changes need to be made, set the "needsChanges" boolean to false. Also,
            | when suggesting changes, ensure that you are not requesting changes that will result in truncation
            | or substantial content changes.""")
        .autoInjectContext("You will be provided with a json context object that contains the following: " +
                "\"new page\": This the slide you just wrote. \"main\": This is the lorebook that contains all of" +
                "your notes about the subject of the pitchdeck. " +
                "\"user prompt\": This the request from the user regarding what they want you to include." +
                "When writing your plan you must check for the following: \n" +
                "- Check that \"new page\" conforms to the request of \"user prompt\".\n" +
                "- Check that \"new page\" does not contain plot holes with the existing lore in \"main\"\n" +
                "\n If either case confirms that there are issues you need to fix. Include the changes that need" +
                " to be made in your output.")
        .setPipeName("validity checker pipe")

    val validityCorrectorPipe  = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(PointFixes())
        .setPreInvokeFunction(::preInvokeLoreRepairPipe)
        .setPreValidationMiniBankFunction(::chapterPreValidate)
        .setReasoningPipe(authorBuilder(smugAssholePrompt))
        .pullGlobalContext()
        .setPageKey("new page, main")
        .setTemperature(0.8)
        .setTopP(.7)
        .applySystemPrompt()
        .setSystemPrompt("""Currently, you are looking at a revision request that you wrote up prior in the
                form of json. You need to edit the new page you've written and return the edit as your output.""")
        .setFooterPrompt("""Using the provided context, you must now make surgical changes to \"new page\" in accordance with the revision request.
                Make only the changes specified in the revision request, and NO OTHER CHANGES. Furthermore, do NOT truncate the text:
                there must at least as many paragraphs and at least as many sentences in your output as there were in the provided material.
                You must return only the edited story: do NOT output JSON; do not output JSON of ANY KIND.""")
        .autoInjectContext("""You have been provided with a context object that contains the 
            |page you are working on fixing. The json schema for the context is as follows: 
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("validity corrector pipe")

    val planCheckPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setContextWindowSize(120000)
        .setMaxTokens(20000)
        .setTopP(.8)
        .setTemperature(.8)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main, chapter guide, story guide")
        .requireJsonPromptInjection()
        .setJsonOutput(PointFixes())
        .setSystemPrompt("""You are now reviewing your work to ensure that it fully effectuates on the
            |premade plan for the current "chapter guide" while staying on track for the "story guide."
            |This entails checking the current slide text against the "chapter guide" to make sure 
            |all bullet points are touched upon. Then check against "story guide" to make sure
            |the slide as a whole furthers the goal of the pitch deck.
            |###PROCEDURE: If changes need to be made to the text, order the changes ONLY AS ADDITIONS TO THE ORIGINAL TEXT:
            |NO TEXT CAN BE DELETED: ONLY ADDED.
            |Produce requested additions as a numbered list.
        """.trimMargin())
        .setFooterPrompt("""If no changes need to be made, set the "needsChanges" boolean to false. Also,
            | when suggesting changes, ensure that you are not requesting changes that will result in truncation
            | or substantial content changes.""")
        .autoInjectContext("""###CONTEXT: "story guide" is the outline for the pitch deck
                as a whole. "chapter guide" is the outline for the current slide. "user prompt"
                is the current instructions from the user.""")
        .setPipeName("plan check pipe")

    val planCorrectorPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(PointFixes())
        .setPreInvokeFunction(::preInvokeLoreRepairPipe)
        .setPreValidationMiniBankFunction(::chapterPreValidate)
        .pullGlobalContext()
        .setPageKey("new page, chapter guide, story guide")
        .setTemperature(0.8)
        .setTopP(.7)
        .setReasoningPipe(authorBuilder(smugAssholePrompt))
        .applySystemPrompt()
        .setSystemPrompt("""$smugAssholePrompt. Currently, you are looking at a revision request that you wrote up prior in the
                form of json. You need to edit the new page you've written and return the edit as your output.""")
        .setFooterPrompt(""""Using the provided context, you must now make surgical changes to \"new page\" in accordance with the revision request.
                Make only the changes specified in the revision request, and NO OTHER CHANGES. Furthermore, do NOT truncate the text:
                there must at least as many paragraphs and at least as many sentences in your output as there were in the provided material.
                You must return only the edited story: do NOT output JSON; do not output JSON of ANY KIND.""")
        .autoInjectContext("""You have been provided with a context object that contains the 
            |page you are working on fixing. The json schema for the context is as follows: 
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("plan corrector pipe")

    val adherenceInstructorPipe = BedrockMultimodalPipe()
        .setRegion("us-east-2")
        .useConverseApi()
        .setModel(deepseekModelName)
        .setContextWindowSize(120000)
        .setMaxTokens(20000)
        .setTopP(.8)
        .setTemperature(.8)
        .truncateModuleContext()
        .pullGlobalContext()
        .setPageKey("new page, main")
        .requireJsonPromptInjection()
        .setJsonOutput(PointFixes())
        .setSystemPrompt("""You are now reviewing your work to ensure that it fully effectuates on the "user prompt".
            |This entails checking the current slide text against the "user prompt" to make sure
            |all user requests are executed and fulfilled.
            |###PROCEDURE: If changes need to be made to the text, order the changes ONLY AS ADDITIONS TO THE ORIGINAL TEXT:
            |NO TEXT CAN BE DELETED: ONLY ADDED.
            |Produce requested additions as a numbered list.
        """.trimMargin())
        .setFooterPrompt("""If no changes need to be made, set the "needsChanges" boolean to false. Also,
            | when suggesting changes, ensure that you are not requesting changes that will result in truncation
            | or substantial content changes.""")
        .autoInjectContext("""###CONTEXT: "user prompt" is the instructions from the user.""")
        .setPipeName("adherence instructor pipe")

    val adherenceEnforcerPipe = BedrockMultimodalPipe()
        .setRegion("us-west-2")
        .useConverseApi()
        .setModel(qwenCoder480B)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .requireJsonPromptInjection()
        .setJsonInput(PointFixes())
        .setPreInvokeFunction(::preInvokeLoreRepairPipe)
        .pullGlobalContext()
        .setPageKey("new page")
        .setTemperature(0.8)
        .setTopP(.7)
        .applySystemPrompt()
        .setReasoningPipe(authorBuilder(smugAssholePrompt))
        .setSystemPrompt("""$smugAssholePrompt. Currently, you are looking at a revision request that you wrote up prior in the
                form of json. You need to edit the new page you've written and return the edit as your output.""")
        .setFooterPrompt("""Using the provided context, you must now make surgical changes to \"new page\" in accordance with the revision request.
                Make only the changes specified in the revision request, and NO OTHER CHANGES. Furthermore, do NOT truncate the text:
                there must at least as many paragraphs and at least as many sentences in your output as there were in the provided material.
                You must return only the edited story: do NOT output JSON; do not output JSON of ANY KIND.""")
        .autoInjectContext("""You have been provided with a context object that contains the 
            |page you are working on fixing. The json schema for the context is as follows: 
        """.trimMargin())
        .setTransformationFunction(::recordWritingPipePage)
        .setPipeName("adherence enforcer pipe")


pitchSlideWriterPipeline
    .add(blueprintPipe)
    .add(constructorPipe)
    .add(validityCheckerPipe)
    .add(validityCorrectorPipe)
    .add(planCheckPipe)
    .add(planCorrectorPipe)
    .add(adherenceInstructorPipe)
    .add(adherenceEnforcerPipe)






    runBlocking {
        pitchSlideWriterPipeline.init(true)
    }

    enablePipelineStreaming(pitchSlideWriterPipeline)

    return pitchSlideWriterPipeline

}