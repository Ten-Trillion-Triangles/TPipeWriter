package Builders

import Builders.Util.copyLorebookFromMain
import Builders.Util.recordWritingPipePage
import Globals.Env
import Globals.ModelConfig
import Shell.loadSettings
import com.TTT.Debug.TraceStreamMerger
import com.TTT.Pipe.MultiPageBudgetStrategy
import com.TTT.Pipe.MultimodalContent
import com.TTT.Pipe.TokenBudgetSettings
import com.TTT.Pipeline.Connector
import com.TTT.Pipeline.Pipeline
import com.TTT.Util.extractJson
import genericOpenAIPipe.env.GenericOpenAIEnv as genericOpenAIEnv
import genericOpenAIPipe.api.ApiMode
import genericOpenAIPipe.GenericOpenAIPipe
enum class DialogueType
{
    InformalCasual,
    InformalSerious,
    FormalFreeform,
    FormalRote,
}

@kotlinx.serialization.Serializable
data class dialogueClass (
    var dialogueType: DialogueType = DialogueType.FormalFreeform,
)

/**
 * Builder that generates the dialogue connector and required evaluation pipe.
 */
fun buildDialogueConnector() : Pair<Pipeline, Connector>
{


/**
 * Per-pipe token budget applied to every pipe in DialogueConnector.
 *
 * Mirrors chapterRewritePipelineBudget (ChapterRewritePipeline.kt:51-65).
 * Same MiniMax-M3 512k context window, 12k output cap, no reasoning
 * carve-out, user prompt preserved untouched, DYNAMIC_SIZE_FILL multi-page
 * strategy.
 */
val dialogueConnectorBudget: TokenBudgetSettings = TokenBudgetSettings(
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

    val settings = loadSettings()

    /**
     * Required boilerplate to map us to the arn, or inference ID. This is because most models cannot be
     * invoked directly, and must be bound to a profile.
     */
    val identifyMyDialogue = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .requireJsonPromptInjection()
        .setJsonOutput(dialogueClass())
        .setMaxTokens(32000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .setTemperature(0.8)
        .setTopP(.7)
        .applySystemPrompt()
        .autoInjectContext("")
        .setSystemPrompt("""Your job is to identify which type of dialogue is dominant on the given page. 
            There are four categories of dialogue you will be looking for. 
            1. Informal-Casual: when characters are talking to each other in a relatively low stakes 
            kind of way in a non-formal setting. Note: the topic can be serious, but still fit for
            Informal-Casual. The key is where the scene is taking place: in a home? Casual. In a
            school lunchroom? Probably casual. In a church or doctor's office? Serious.
            Examples include friends talking with each other at home; classmates messing around in a locker room;
            strangers chatting at a bar; a customer talking to an employee or business owner; etc.
            2. Informal-Serious: when characters are talking to each other in serious informal
            settings. Examples include someone talking to a trusted confidant (like a therapist or advisor) in their
            office; people talking after a funeral while leaving the church; business partners talking to each other 
            in a non-official capacity while still in their offices; etc.
            3. Formal-Freeform: when characters are talking to each other in an official capacity 
            (or at least one character in the scene is acting in an official capacity), 
            or the discussion is related to work or jobs. Examples include a court hearing; 
            a serious business or council meeting; an arbitration meeting; 
            a police officer talking to witnesses or to a victim or perpetrator; etc.
            4. Formal-Rote: when characters are in the act of working, and the job in question is one 
            where certain statements have certain specific correct responses. Additionally, if a page has
            little to no dialogue, it also qualifies as Formal-Rote (Formal-Rote is to be used when 
            there is no need to edit the dialogue, you see).
            Examples include ship pilots navigating; doctors in an emergency room working; 
            first responders speaking to dispatch; etc. OR IF THERE IS NO OR ALMOST NO DIALOGUE.
            ##NOTE: If the user prompt specifies one of the categories specifically, use the requested
            category, regardless of the text content.
            Depending on what type of dialogue the page predominantly has, assign the appropriate
            value to the json variable depending on what dialogue it is.
            """)
        .setPipeName("identify my dialogue pipe")


    val benignSkiesMyDialoguePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .setMaxTokens(8000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .setTemperature(0.8)
        .setTopP(.8)
        .applySystemPrompt()
        //.setReasoningPipe(authorBuilder(Env.authorPrompt))
        .setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.authorPrompt)) }
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
            |PARAGRAPHS OF BODY TEXT TO THE PAGE. DO NOT ADD STAGE DIRECTIONS!!! ONLY. ADD. DIALOGUE. YOU FUCK. AND ONLY ADD DIALOGUE TO PLACES
            |WHERE THERE IS ALREADY DIALOGUE!!!!!!!!!!!!!!!!!!!!!
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
        .setMaxTokens(8000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .autoInjectContext("New Page is the page of text you must work on.")
        .setTemperature(0.8)
        .setTopP(.8)
        .applySystemPrompt()
        //.setReasoningPipe(authorBuilder(Env.richardTreadwell))
        .setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.richardTreadwell)) }
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
            |DO NOT ADD STAGE DIRECTIONS!!! ONLY. ADD. DIALOGUE. YOU FUCK. AND ONLY ADD DIALOGUE TO PLACES
            |WHERE THERE IS ALREADY DIALOGUE!!!!!!!!!!!!!!!!!!!!!
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
        .setMaxTokens(8000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .setTemperature(0.8)
        .setTopP(.8)
        .applySystemPrompt()
        //.setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setReasoningPipe(explicitCotBuilder()).apply { setReasoningPipe(authorBuilder(Env.editorPrompt)) }
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
            |PARAGRAPHS OF BODY TEXT TO THE PAGE. DO NOT ADD STAGE DIRECTIONS!!! ONLY. ADD. DIALOGUE. YOU FUCK. AND ONLY ADD DIALOGUE TO PLACES
            |WHERE THERE IS ALREADY DIALOGUE!!!!!!!!!!!!!!!!!!!!!
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

        val evaluateDialoguePipeline = Pipeline()
            .add(identifyMyDialogue)
        val informalCasualPipeline = Pipeline()
            .add(benignSkiesMyDialoguePipe)
        val informalSeriousPipeline = Pipeline()
            .add(polishMyDialoguePipe)
        val formalFreeformPipeline = Pipeline()
            .add(certifyMyDialoguePipe)


        val dialogueConnector = Connector()
            .add(DialogueType.InformalCasual, informalCasualPipeline)
            .add(DialogueType.InformalSerious, informalSeriousPipeline)
            .add(DialogueType.FormalFreeform, formalFreeformPipeline)






    // Apply per-pipe TokenBudgetSettings to every pipe reachable through the connector.
    // Pattern mirrors ChapterRewritePipeline.kt's getPipes().forEach block.
    val allPipelines = mutableListOf<Pipeline>().apply {
        add(evaluateDialoguePipeline)
        dialogueConnector.getPipelinesFromInterface().forEach { add(it) }
    }
    allPipelines.forEach { pipeline ->
        pipeline.getPipes().forEach {
            it.useEntireContextForLoreSelection()
            it.setTokenBudget(dialogueConnectorBudget)
            it.enableComprehensiveTokenTracking()
        }
    }

    return Pair<Pipeline, Connector>(evaluateDialoguePipeline, dialogueConnector)
}

/**
 * Build our required pipelines, and then execute our connector and return its result.
 * All of this will be self-contained in this function.
 */
suspend fun shunt(content: MultimodalContent) : MultimodalContent
{
    val originalText = content.text

    val (dialogueSelectionPipeline, connector) = buildDialogueConnector()
    connector.enableTracing()
    dialogueSelectionPipeline.enableTracing()

    // Capture the host pipeline before the dialogue connector reassigns currentPipe
    val hostPipeline = content.currentPipe?.getPipelinesFromInterface()?.firstOrNull()

    dialogueSelectionPipeline.init(true)
    connector.getPipelinesFromInterface().forEach { it.init(true) }

    var result = dialogueSelectionPipeline.execute(content)

    val json = extractJson<dialogueClass>(result.text)

    if(json == null)
    {
        throw Exception("dialogueConnector did not return valid json, or we were unable to extract it.")
    }


    if(json.dialogueType == DialogueType.FormalRote)
    {
        content.text = originalText
        return content
    }

    val finalResult = connector.execute(json.dialogueType, content)

    val dialoguePipeline = connector.get(json.dialogueType)

    if(hostPipeline != null && dialoguePipeline != null)
    {
        TraceStreamMerger.bubbleMerge(
            hostPipeline,
            dialogueSelectionPipeline,
            dialoguePipeline
        )
    }
    return finalResult
}
