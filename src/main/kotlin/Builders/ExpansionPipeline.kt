package Builders

import Builders.Util.chapterPreValidate
import Builders.Util.applySurgicalReplacementsAndBank
import Builders.Util.copyLorebookFromMain
import Builders.Util.logicalProgressionPreValidationMiniBank
import Builders.Util.preInvokeLoreRepairPipe
import Builders.Util.preInvokeShunt
import Builders.Util.recordAuthorPlan
import Builders.Util.recordWritingPipePage
import Builders.Util.secondPassTransform
import Globals.Env
import Globals.isValidGptOssResponse
import Globals.ModelConfig
import Shell.loadSettings
import com.TTT.Context.ContextBank
import com.TTT.Pipe.MultiPageBudgetStrategy
import com.TTT.Pipe.TokenBudgetSettings
import com.TTT.Pipeline.Pipeline
import genericOpenAIPipe.env.GenericOpenAIEnv as genericOpenAIEnv
import genericOpenAIPipe.api.ApiMode
import genericOpenAIPipe.GenericOpenAIPipe
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

/**
 * Per-pipe token budget applied to every pipe in ExpansionPipeline.
 *
 * Mirrors chapterRewritePipelineBudget (ChapterRewritePipeline.kt:51-65) and
 * dialogueConnectorBudget (DialogueConnector.kt:39-58). Same MiniMax-M3 512k
 * context window, 12k output cap, no reasoning carve-out, user prompt
 * preserved untouched, DYNAMIC_SIZE_FILL multi-page strategy.
 */
val expansionPipelineBudget: TokenBudgetSettings = TokenBudgetSettings(
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
val expansionPipeline = Pipeline()

    val breakerPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .requireJsonPromptInjection(stripExternalText = true)
        .setJsonOutput(SurgicalChangeList())
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
            |For each of the three target sentences, emit a JSON SurgicalChangeList entry:
            |  - subStringToChange: the verbatim target sentence (with enough surrounding context — the
            |    sentence before it — to uniquely identify it)
            |  - replacementSubString: a brief placeholder explaining what body text should be inserted
            |    (e.g. "[EXPAND: 2 paragraphs of character introspection]"). The downstream expander pipe
            |    will replace this placeholder with actual body text.
            |  - mode: "insertAfter"
            |
            |Output a JSON SurgicalChangeList with exactly 3 entries. Output ONLY the JSON. Do not add commentary.
            |
            |Schema:
            |{
            |  "changeList": [
            |    {"subStringToChange": "...", "replacementSubString": "...", "mode": "insertAfter"}
            |  ]
            |}
        """.trimMargin())
        .setFooterPrompt("""###GOAL: Output only the JSON list of surgical patches. Do not write a page or chapter.
            |Produce only the JSON list with exactly 3 entries (one per insertion point). Each entry must have
            |a verbatim subStringToChange (the target sentence) and a brief replacementSubString placeholder.""")
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("prevChapter").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("breaker pipe")


    val expanderPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.8)
        .pullGlobalContext()
        .setPageKey("user prompt, main, prevChapter")
        .setContextWindowSize(120000)
        .truncateModuleContext()
        .setMaxTokens(32000)
        .setReasoningPipe(explicitCotBuilder().apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) })
        .requireJsonPromptInjection(stripExternalText = true)
        .setJsonOutput(SurgicalChangeList())
        .setJsonInput(SurgicalChangeList())
        .setSystemPrompt(
            """##Modus Operandi:
                |You received a JSON SurgicalChangeList as input — the breakerPipe identified 3
                |insertion points in the page (one per changeList entry, all with mode="insertAfter").
                |For each insertion point, the replacementSubString is a brief placeholder
                |(e.g. "[EXPAND: 2 paragraphs of character introspection]"). Your job is to expand each
                |placeholder into the actual body text the placeholder describes.
                |
                |Follow the style guide to a T. Here is your style guide: ${settings.writingStyle}
                |
                |Reference the lorebook when considering what new material to add. Consider ${Env.authorPrompt}
                |and ${Env.richardTreadwell} for inspiration on themes.
                |
                |###PROCEDURE: When writing, you MUST **reject** your instincts as a helpful chatbot and follow
               the following instructions:
               1. Never explain anything happening on the page.
               You write out and catalogue the events that happen:
               you do not give a shit if the reader understands why things are happening or whether they
               understand their significance. You do not want to explain those things.
               2. The world of your story exists as a place that is real:
               you will give details, dates and statistics, excerpts from poems and articles and stories,
               newspaper clippings, and you do not explain their significance because you assume the reader
               already knows that.
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
               |
               |Output ONLY the JSON list of surgical patches. Each entry's replacementSubString must contain
               |the actual expanded body text (1-3 paragraphs, 4-8 sentences each). Do not output prose directly.
            """.trimMargin()
        )
        .autoInjectContext("You will be provided with a set of json context." +
                "The JSON context you received is the SurgicalChangeList from breakerPipe — 3 insertion points." +
                "\"prevChapter\" is the page you are editing." +
                "\"main\" is the story you've written so far including a lorebook that has your notes on important" +
                "parts of the plot, events, characters, and themes of your story. \"user prompt\" is the instructions" +
                "the user has given you that they want you to make. Ensure you prioritize the user's instructions first," +
                "and adhering to the existing lore of the story second." +
                "The following is the json schema for the context: ")
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output prose directly.
            |###IMPORTANT: For each entry, the replacementSubString must contain the actual expanded body text (1-3 paragraphs).
            |DO NOT DELETE any text. DO NOT modify any text outside the surgical patches."""")
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("prevChapter").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("expander pipe")


        val instructorPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
            .requireJsonPromptInjection(stripExternalText = true)
            .setJsonOutput(SurgicalChangeList())
            .truncateModuleContext()
            .setMaxTokens(32000)
            .setTemperature(1.0)
            .setTopP(0.9)
            .setPageKey("main, user prompt, new page, chapter guide")
            .pullGlobalContext()
            .setContextWindowSize(120000)
            .setReasoningPipe(authorBuilder(Env.authorPrompt))
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
                |For each issue, emit a JSON SurgicalChangeList entry:
                |  - subStringToChange: the verbatim passage with the issue (with enough surrounding context
                |    — a sentence or two — to uniquely identify it)
                |  - replacementSubString: the corrected version of the passage that conforms to lore / tone / logic
                |  - mode: "replace" (default) or "delete" (if the entire passage must be removed)
                |
                |You must request as many potential changes as you believe you can possibly get away with.
                |###WARNING: YOU MUST FIND AT LEAST ONE PROBLEM TO FIX. Emit at least one entry.
                |
                |Output ONLY the JSON. Do not output a new page of the story.
            """.trimMargin())
            .setFooterPrompt("""Output only the JSON list of surgical patches. Include ONLY ONE surgical change per changeList entry.
                |DO NOT RETURN AN EMPTY JSON ARRAY.
                |###IMPORTANT: ONLY RETURN JSON. DO NOT WRITE A NEW PAGE OF THE STORY.
            """.trimMargin())
            .setTransformationFunction(::applySurgicalReplacementsAndBank)
            .setOnFailure { _, processed ->
                processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
                processed
            }
            .setPipeName("instructor pipe")


        val implementerPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
            .setTemperature(1.0)
            .setTopP(0.9)
            .pullGlobalContext()
            .setPageKey("user prompt, main, new page")
            .setContextWindowSize(120000)
            .truncateModuleContext()
            .setMaxTokens(32000)
            .requireJsonPromptInjection(stripExternalText = true)
            .setJsonInput(SurgicalChangeList())
            .setJsonOutput(SurgicalChangeList())
            .setTransformationFunction(::applySurgicalReplacementsAndBank)
            .setReasoningPipe(explicitCotBuilder().apply { setReasoningPipe(authorBuilder(Env.writingControlPrompt)) })
            .autoInjectContext("The following is the context for the story you've written so far. First is " +
                    "\"new page\", which was the page you wrote prior that you now need to edit. The second is " +
                    "\"main\", which is the current story you've written prior to your latest page. Third is " +
                    "\"user prompt\", which is the request your editor has made to you regarding changes they want you" +
                    "to make. ")
            .setSystemPrompt("""${settings.writingStyle} As you have completed the list of surgical changes that need to be made
            |to "new page" in order to improve it to make it as coherent, logical and problem free as it can be,
            |you must now verify and refine those surgical changes before applying them. By making extremely aggressive,
            |broad sweeping changes, and going absolutely apeshit on the amount of text you add, no-self-control levels
            |of additions, refine all changes that were requested of you. BE CREATIVE WITH YOUR FIXES: you CAN do MORE
            |than you were asked to do!
            |For each entry in the input changeList:
            |  - Verify subStringToChange is a verbatim match in the new page text. If it's not, drop or fix the entry.
            |  - Refine replacementSubString to make the corrected text fit naturally with the surrounding prose.
            |  - Add additional changes the previous pipe missed (lore / tone / logic issues).
            |Output a JSON SurgicalChangeList back (echoing the verified-and-refined entries, with any that could
            |not be applied dropped). Each entry has subStringToChange, replacementSubString, mode.
            |###IMPORTANT: DO NOT TRUNCATE THE TEXT. Each replacementSubString must contain the actual prose.
            |###WARNING: DO NOT MODIFY THE CONTENT BEYOND THE LISTED CORRECTIONS."""")
            .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the rewritten page.
            |###IMPORTANT: ONLY RETURN JSON.
            |###WARNING: For each entry, replacementSubString must contain the actual refined body text."""")
            .setOnFailure { _, processed ->
                processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
                processed
            }
            .setPipeName("implementer pipe")


        val shuntPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setPreInvokeFunction(::preInvokeShunt)
            .setPipeName("shunt pipe")

    val dialoguePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .pullGlobalContext()
        .setPageKey("new page, main, user prompt")
        .setTemperature(0.9)
        .setTopP(.9)
        .applySystemPrompt()
        //.setReasoningPipe(authorBuilder(Env.editorPrompt))
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

    val finalEditPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.9)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setPageKey("user prompt, new page")
        .requireJsonPromptInjection(stripExternalText = true)
        .setJsonOutput(SurgicalChangeList())
        //.setReasoningPipe(authorBuilder(Env.editorPrompt).apply { setReasoningPipe(authorBuilder(Env.authorPrompt)).apply {setReasoningPipe(authorBuilder(Env.editorPrompt))} })
        .setSystemPrompt("""${Env.richardTreadwell} and ${Env.editorPrompt}. Using these character personalities,
            |review the written page. Find broad, sweeping changes that can be made to improve it.
            |Then, as ${Env.authorPrompt}, you must make an apeshit number of changes to deliver the optimal version of this page.
            |Make sure you maintain consistency with the user prompt, however:
            |it is very important you satisfy the user's request at the end of your work.
            |MAKE AS MANY SURGICAL CHANGES AS POSSIBLE. Also, DO NOT TOUCH THE DIALOGUE (unless you deem the dialogue to be not human
            |readable, in which case, fix it to be as such).
            |
            |Emit a JSON SurgicalChangeList with one entry per improvement:
            |  - subStringToChange: the verbatim passage to improve (with enough surrounding context to uniquely identify it)
            |  - replacementSubString: the improved version
            |  - mode: "replace" (default) or "delete"
            |
            |Output ONLY the JSON. Do not output the rewritten page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the rewritten page.
            ###IMPORTANT: DO NOT include prose outside the JSON list.
            ###WARNING: Each entry's replacementSubString must contain the actual refined text."""")
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("final edit pipe")


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
        .requireJsonPromptInjection(stripExternalText = true)
        .setJsonOutput(SurgicalChangeList())
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
            |INSTRUCTED TO MAKE. Emit a JSON SurgicalChangeList with one entry per fix:
            |  - subStringToChange: the verbatim passage with the punctuation issue (with enough context to uniquely identify it)
            |  - replacementSubString: the corrected punctuation
            |  - mode: "replace"
            |
            |Output ONLY the JSON. Do not output the rewritten page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the rewritten page.
            |###IMPORTANT: DO NOT include prose outside the JSON list. ###WARNING: For each entry, replacementSubString must contain the actual corrected text.""")
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("cleanup step one pipe")


    val cleanupStepTwoPipe = GenericOpenAIPipe()
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
        .requireJsonPromptInjection(stripExternalText = true)
        .setJsonOutput(SurgicalChangeList())
        .setSystemPrompt("""Your task is fairly simple: you must fix the text for common issues in accordance to the
            |following three rules:
            |1. All text that can be dialogue SHOULD BE DIALOGUE/INTERNAL MONOLOGUE: You will find in places in the text where character opinions,
            |thoughts, consciousness indicators, or general author commentary are written out as body text. Instances
            |of these things should ALL BE CONVERTED INTO DIALOGUE/INTERNAL MONOLOGUE.
            |
            |2. STAGE DIRECTIONS SUCK: WE ARE WRITING A BOOK, NOT A MOVIE SCRIPT: Find dialogue that is preceded by,
            |interrupted with, or followed by stage directions. Eliminate those stage directions and merge together
            |bodies of dialogue text as necessary. Examples of stage directions to remove: "Geno observed, his analytical mind unable to fully rest";
            |"She paused, feathers rustling."; "her voice cutting through the noise of the market."
            |
            |3. Any statements of hyperbole, hype, and particularly strong adjectives in places where the user prompt
            |has not demanded, or in scenes that are otherwise climactic, must either be removed in their entirety
            |or converted into character dialogue. You're looking for strong visual metaphors, like "shattered" or
            |"downpour", to describe character mental states or reaction to a situation.
            |
            |Fix the above problems using surgical changes. DO NOT MAKE ANY CHANGES ASIDE FROM THE ONES YOU HAVE BEEN
            |INSTRUCTED TO MAKE. Emit a JSON SurgicalChangeList with one entry per fix:
            |  - subStringToChange: the verbatim passage with the issue (with enough context to uniquely identify it)
            |  - replacementSubString: the corrected passage (body-text-to-dialogue, stage-direction-removed, etc.)
            |  - mode: "replace" (default) or "delete" (if the stage direction should be removed entirely)
            |
            |Output ONLY the JSON. Do not output the rewritten page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the rewritten page.
            |###IMPORTANT: DO NOT include prose outside the JSON list. ###WARNING: For each entry, replacementSubString must contain the actual corrected text.""")
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("cleanup step two pipe")


    val cleanupStepThreePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(0.7)
        .setTopP(0.7)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        //.setReasoningPipe(processFocusedBuilder())
        .setPageKey("user prompt, new page")
        .requireJsonPromptInjection(stripExternalText = true)
        .setJsonOutput(SurgicalChangeList())
        .setSystemPrompt("""Your task is fairly simple: you must fix the text in accordance to the
            |following rules:
            |
            |1. STAGE DIRECTIONS SUCK: WE ARE WRITING A BOOK, NOT A MOVIE SCRIPT: Find dialogue that is interrupted
            |with, or followed by, stage directions. Eliminate those stage directions and merge together bodies
            |of dialogue text as necessary. Examples of stage directions to remove: "Geno observed, his analytical mind unable to fully rest";
            |"She paused, feathers rustling."; "her voice cutting through the noise of the market."
            |
            |2. Any statements of hyperbole, hype, and particularly strong adjectives in places where the user prompt
            |has not demanded, or in scenes that are otherwise climactic, must either be removed in their entirety
            |or converted into character dialogue. You're looking for strong visual metaphors, like "shattered" or
            |"downpour", to describe character mental states or reaction to a situation.
            |
            |Fix the above problems using surgical changes. DO NOT MAKE ANY CHANGES ASIDE FROM THE ONES YOU HAVE BEEN
            |INSTRUCTED TO MAKE. Emit a JSON SurgicalChangeList with one entry per fix:
            |  - subStringToChange: the verbatim passage with the issue (with enough context to uniquely identify it)
            |  - replacementSubString: the corrected passage
            |  - mode: "replace" (default) or "delete" (if the stage direction should be removed entirely)
            |
            |Output ONLY the JSON. Do not output the rewritten page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the rewritten page.
            |###IMPORTANT: DO NOT include prose outside the JSON list. ###WARNING: For each entry, replacementSubString must contain the actual corrected text.""")
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("cleanup step three pipe")


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
        .setTopP(.7)
        .applySystemPrompt()
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
            |Your one great mission is to go absolutely apeshit with the amount of dialogue you add to the story.
            |
            |For each dialogue improvement, emit a JSON SurgicalChangeList entry:
            |  - subStringToChange: the verbatim existing dialogue passage (with enough context to uniquely identify it)
            |  - replacementSubString: the extended dialogue
            |  - mode: "replace" (substitute the passage) or "insertAfter" (add new dialogue after the existing passage)
            |
            |###RULES: NO TEXT CAN BE DELETED. Use mode "replace" or "insertAfter" only. The page text is the
            |existing dialogue; your surgical patches refine it; you do not rewrite the page from scratch.
            |###OUTPUT: Output ONLY the JSON. Do not output the modified page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the modified page.
            |###IMPORTANT: DO NOT DELETE dialogue. Use mode 'replace' or 'insertAfter' only.""")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .applySystemPrompt()
        .setPipeName("benign skies my dialogue pipe")
        .autoInjectContext("New Page is the page of text you must work on. Emit a JSON SurgicalChangeList with one entry per dialogue improvement. Each entry's subStringToChange is the verbatim existing passage (with surrounding context to uniquely identify it); replacementSubString is the improved dialogue that should replace it; mode is 'replace' (substitute the passage) or 'insertAfter' (add new dialogue after the existing passage without modifying it). DO NOT DELETE dialogue. DO NOT add stage directions or body-text paragraphs. Each entry must have a verbatim subStringToChange.")


    val polishMyDialoguePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .pullGlobalContext()
        .setPageKey("new page, user prompt")
        .autoInjectContext("New Page is the page of text you must work on. Emit a JSON SurgicalChangeList with one entry per dialogue improvement. Each entry's subStringToChange is the verbatim existing passage (with surrounding context to uniquely identify it); replacementSubString is the improved dialogue that should replace it; mode is 'replace' (substitute the passage) or 'insertAfter' (add new dialogue after the existing passage without modifying it). DO NOT DELETE dialogue. DO NOT add stage directions or body-text paragraphs. Each entry must have a verbatim subStringToChange.")
        .setTemperature(0.8)
        .setTopP(.7)
        .applySystemPrompt()
        //.setReasoningPipe(authorBuilder(Env.authorPrompt))
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
            |jokes are tagged by laughter or mock-solemn "explains" after the fact.
            |2. Rhetorical flourish: long, stylized clauses with parentheticals and em dashes;
            |mock-formal cadences.
            |3. Call-and-response plotting: question/answer, repeat/alter,
            |lesson lands in the last exchange.
            |4. Sparse punctuation: commas rare, periods frequent;
            |and/then chaining.
            |5. Rhetorical questions as stepping stones; each is immediately answered and advanced.
            |6. Socratic structure: question -> short assent -> layered explanation.
            |
            |Your one great mission is to go absolutely apeshit with the amount of dialogue you add to the story.
            |
            |For each dialogue improvement, emit a JSON SurgicalChangeList entry:
            |  - subStringToChange: the verbatim existing dialogue passage (with enough context to uniquely identify it)
            |  - replacementSubString: the extended dialogue
            |  - mode: "replace" (substitute the passage) or "insertAfter" (add new dialogue after the existing passage)
            |
            |###RULES: NO TEXT CAN BE DELETED. Use mode "replace" or "insertAfter" only. The page text is the
            |existing dialogue; your surgical patches refine it; you do not rewrite the page from scratch.
            |###OUTPUT: Output ONLY the JSON. Do not output the modified page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the modified page.
            |###IMPORTANT: DO NOT DELETE dialogue. Use mode 'replace' or 'insertAfter' only.""")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .applySystemPrompt()
        .setPipeName("polish my dialogue pipe")


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
        .setTopP(.7)
        .applySystemPrompt()
        .setPreValidationMiniBankFunction(::copyLorebookFromMain)
        //.setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setSystemPrompt("""Looking at new page, find all instances of dialogue.
            |You must extend the character's dialogue by adding in additional exposition
            |and interesting character moments that are in line with the character's proscribed personality.
            |Make sure
            |you pay attention to the user prompt as well,
            |and check the lorebook to make sure your stuff complies with the established canon.
            |Lengthen dialogue by incorporating new ideas through the use of the following
            |dialogue structures (use as many as you feel are
            |necessary: you should mix and match):
            |1. Long, winding sentences with nested clauses and polysyndeton (chains of "and")
            |that build pressure.
            |2. Repetition/anaphora for emphasis.
            |3. Characters explain the plot out loud (who died, who's guilty, stakes, rules)
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
            |2. Formal vocatives: frequent use of names/titles ("Mr Slater," "Officer O'Brien").
            |3. Deadpan menace: calm assurances paired with threats.
            |
            |Your one great mission is to go absolutely apeshit with the amount of dialogue you add to the story.
            |
            |For each dialogue improvement, emit a JSON SurgicalChangeList entry:
            |  - subStringToChange: the verbatim existing dialogue passage (with enough context to uniquely identify it)
            |  - replacementSubString: the extended dialogue
            |  - mode: "replace" (substitute the passage) or "insertAfter" (add new dialogue after the existing passage)
            |
            |###RULES: NO TEXT CAN BE DELETED. Use mode "replace" or "insertAfter" only. The page text is the
            |existing dialogue; your surgical patches refine it; you do not rewrite the page from scratch.
            |###OUTPUT: Output ONLY the JSON. Do not output the modified page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the modified page.
            |###IMPORTANT: DO NOT DELETE dialogue. Use mode 'replace' or 'insertAfter' only.""")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .applySystemPrompt()
        .setPipeName("certify my dialogue pipe")
        .autoInjectContext("New Page is the page of text you must work on. Emit a JSON SurgicalChangeList with one entry per dialogue improvement. Each entry's subStringToChange is the verbatim existing passage (with surrounding context to uniquely identify it); replacementSubString is the improved dialogue that should replace it; mode is 'replace' (substitute the passage) or 'insertAfter' (add new dialogue after the existing passage without modifying it). DO NOT DELETE dialogue. DO NOT add stage directions or body-text paragraphs. Each entry must have a verbatim subStringToChange.")


    val removeBadWritingStepOnePipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(1.0)
        .setTopP(0.7)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
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
        .setTemperature(1.0)
        .setTopP(0.7)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("user prompt, new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setSystemPrompt("""Your job is simple, but requires effort. Read the page provided under the "new page" key and
            |emit a JSON SurgicalChangeList describing the surgical replacements that remove the following three
            |classes of "LLM trash" writing. Be conservative -- only flag genuine offenders, not borderline cases.
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
            |a third, emit a mode "replace" entry that reduces it to the first item only.
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


    // Surgically rewrites any "not X but Y" parallel-negation constructs in the rewritten chapter.
    // Autogenesis port: writerAgent.kt:658-671 footer prompt ("NO PARALLEL-NEGATION CONSTRUCTS").
    val noParallelNegationPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .truncateModuleContext()
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setTemperature(0.7)
        .setTopP(0.8)
        .setValidatorFunction(::isValidGptOssResponse)
        .pullGlobalContext()
        .setPageKey("new page")
        .setJsonOutput(SurgicalChangeList())
        .requireJsonPromptInjection(stripExternalText = true)
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setReasoningPipe(explicitCotBuilder())
        .setSystemPrompt("""##STYLE: NO PARALLEL-NEGATION CONSTRUCTS
            |Do NOT use "not X but Y" rhetorical structures. Chatbot-tuned LLMs overproduce
            |this family of tics because it cheaply delivers contrast. Variants to avoid:
            |  - "Not X but Y"
            |  - "It's not X, it's Y"
            |  - "Not because A but because B"
            |  - "Not A but B"
            |  - "Is not A but is B"
            |  - "Not A, not B, is C"
            |  - "Isn't X, but is Y"
            |State what something IS directly. If the prose genuinely needs to negate
            |the false expectation (e.g. "It was not a weapon but a key"), write the
            |second clause as a positive assertion ("It was a key") and let the reader
            |infer the contrast from context. Never lead with the negation.
            |
            |Read the rewritten chapter under the "new page" key and emit a JSON
            |SurgicalChangeList describing the surgical replacements that remove any
            |parallel-negation constructs you find. For each occurrence, emit one entry
            |with subStringToChange (the offending passage) and replacementSubString
            |(the rewritten positive assertion).
            |
            |###NOTE: DO NOT TOUCH DIALOGUE. Dialogue inside quotation marks is exempt.
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
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("no parallel negation pipe")


    // Surgically checks the rewritten chapter for lorebook conformance.
    // PlusWriter port: PlusWriterPipeline.kt:706-757.
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
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setSystemPrompt("""You are now reviewing the rewritten chapter under the "new page" key to make sure
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
            |If the chapter conforms to the lore, emit {"changeList": []}.
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
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("lore check pipe")

    // Verifies and refines the loreCheckPipe's surgical changes.
    // PlusWriter port: PlusWriterPipeline.kt:758-802.
    val loreRepairPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .requireJsonPromptInjection(stripExternalText = true)
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
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("lore repair pipe")


    // Surgically checks for logical progression issues in the rewritten chapter:
    //   1. Unexplained time-skips
    //   2. Unexplained jumps in location
    //   3. Pages that open as though they're the first page of a new chapter
    // PlusWriter port: PlusWriterPipeline.kt:808-873.
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
        .setSystemPrompt("""You are now reviewing the rewritten chapter under the "new page" key to determine whether
            |or not it advances the story and has progressed logically since the previous page. Carefully check
            |against the "story guide" and "chapter guide" to ensure that the rewritten chapter progresses the story
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
            |be a VERBATIM, CHARACTER-EXACT copy of the bad text in the "new page" (include enough
            |surrounding context -- a sentence or two -- to uniquely identify the passage). replacementSubString
            |is the corrected text. mode is "replace" (correct the text), "delete" (remove the passage), or
            |"insertAfter" (add a clarifying sentence after an existing anchor -- use this for additions
            |rather than replacements, so you don't accidentally lose text).
            |
            |If the chapter progresses logically, emit {"changeList": []}.
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
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("logical progression pipe")

    // Applies the surgical changes emitted by logicalProgressionPipe.
    // PlusWriter port: PlusWriterPipeline.kt:880-942.
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
        .setSystemPrompt("""You received a JSON SurgicalChangeList as input (the logical-progression issues
            |identified by the previous pipe). Your job is to confirm and refine those surgical changes.
            |Apply the changeList entries to the rewritten chapter text under the "new page" key.
            |
            |Output ONLY the JSON list. Do not output the rewritten chapter. Do not add commentary.
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
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("logical correction pipe")


    val styleReapplyPipe = GenericOpenAIPipe()
        .setBaseUrl("https://api.minimax.io/v1")
        .setApiKey(genericOpenAIEnv.resolveApiKey())
        .setApiMode(ApiMode.OpenAIResponses)
        .setModel(ModelConfig.primaryModelName)
        .setTemperature(1.0)
        .setTopP(0.9)
        .setContextWindowSize(115000)
        .setMaxTokens(32000)
        .setValidatorFunction(::isValidGptOssResponse)
        .setReasoningPipe(authorBuilder(Env.editorPrompt))
        .setPageKey("user prompt, new page")
        .requireJsonPromptInjection(stripExternalText = true)
        .setJsonOutput(SurgicalChangeList())
        .setSystemPrompt("""Your job is straightforward: you must do one final pass over of the new page to ensure
            |the style guide is adhered to properly. Here is your style guide: ${settings.writingStyle}.
            |Do not make any changes beyond the ones you were instructed to make.
            |
            |Emit a JSON SurgicalChangeList with one entry per style-guide violation:
            |  - subStringToChange: the verbatim passage that violates the style guide (with enough context to uniquely identify it)
            |  - replacementSubString: the corrected text that adheres to the style guide
            |  - mode: "replace"
            |
            |Output ONLY the JSON. Do not output the rewritten page.
        """.trimMargin())
        .setFooterPrompt("""Output only the JSON list of surgical patches. Do not output the rewritten page.
            |###IMPORTANT: DO NOT include prose outside the JSON list. ###WARNING: For each entry, replacementSubString must contain the actual corrected text.""")
        .setTransformationFunction(::applySurgicalReplacementsAndBank)
        .setOnFailure { _, processed ->
            processed.text = ContextBank.getContextFromBank("new page").contextElements.lastOrNull() ?: processed.text
            processed
        }
        .setPipeName("style reapply pipe")


    expansionPipeline
        .add(breakerPipe)
        .add(expanderPipe)
        .add(instructorPipe)
        .add(implementerPipe)
        .add(cleanupStepOnePipe)
        .add(cleanupStepTwoPipe)
        .add(cleanupStepThreePipe)
        .add(shuntPipe)
        //.add(dialoguePipe)
        //.add(benignSkiesMyDialoguePipe)
        //.add(certifyMyDialoguePipe)
        //.add(polishMyDialoguePipe)
        .add(finalEditPipe)
        .add(removeBadWritingStepOnePipe)
        .add(removeBadWritingStepTwoPipe)
        .add(untwistPipe)
        .add(noParallelNegationPipe)
        .add(loreCheckPipe)
        .add(loreRepairPipe)
        .add(logicalProgressionPipe)
        .add(logicalCorrectionPipe)
        .add(styleReapplyPipe)

    runBlocking {
        expansionPipeline.init(true)
    }

    // Apply per-pipe TokenBudgetSettings to every pipe in ExpansionPipeline.
    // Pattern mirrors ChapterRewritePipeline (commit d8c12b2) and
    // DialogueConnector (commit 8803c59).
    expansionPipeline.getPipes().forEach {
        it.useEntireContextForLoreSelection()
        it.setTokenBudget(expansionPipelineBudget)
        it.enableComprehensiveTokenTracking()
    }

    return expansionPipeline
}
