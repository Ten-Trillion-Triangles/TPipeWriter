package Globals

import Builders.buildNccWriter
import Builders.buildPitchSlideWriterPipeline
import Builders.buildPlusWriterPipeline
import Defaults.BedrockConfiguration
import Defaults.reasoning.ReasoningBuilder
import Defaults.reasoning.ReasoningDepth
import Defaults.reasoning.ReasoningDuration
import Defaults.reasoning.ReasoningInjector
import Defaults.reasoning.ReasoningMethod
import Defaults.reasoning.ReasoningSettings
import Shell.CommandState
import Structs.ModelSettings
import Util.cleanJsonString
import Util.enablePipelineStreaming
import bedrockPipe.BedrockMultimodalPipe
import bedrockPipe.BedrockPipe
import bedrockPipe.BedrockPriorityTier
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Context.LoreBook
import com.TTT.Enums.ContextWindowSettings
import com.TTT.Enums.PromptMode
import com.TTT.Pipe.MultimodalContent
import com.TTT.Pipe.Pipe
import com.TTT.Pipe.TokenBudgetSettings
import com.TTT.Pipeline.Pipeline
import com.TTT.Structs.PipeSettings
import com.TTT.Util.constructPipeFromTemplate
import com.TTT.Util.deserialize
import com.TTT.Util.extractJson
import com.TTT.Util.getHomeFolder
import com.TTT.Util.repairAndDeserialize
import com.TTT.Util.serialize
import com.TTT.Util.writeStringToFile
import env.bedrockEnv
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Data class for handling the user prompt for the manual lorebook pipeline.
 */
@kotlinx.serialization.Serializable
data class LoreBookUserRequest(val loreBook: LoreBook = LoreBook(),
                               val userRequest: String = "",
                               val userRequestedKeys: List<String> = mutableListOf<String>(),
                               val previousText: String = "")

/**
 * Response data class produced by the idea pipeline. Contains a map of idea names to idea content. Can be
 * directly serialized for injecting into the writer to test out an idea.
 */
@kotlinx.serialization.Serializable
data class IdeaResponse(val ideas: Map<String, String> = mutableMapOf<String, String>())
{
    override fun toString() : String
    {
        var ideasString = ""
        ideas.forEach { (key, value) ->
            ideasString += "$key: $value\n\n"
        }

        return ideasString
    }
}

/**
 * Response data class used to handle chat roles with an llm. contains a role, and a response.
 */
@kotlinx.serialization.Serializable
data class ChatResponse(val role: String, val content: String)


/**
 * Array version of the above data class. Used for serialization.
 */
@kotlinx.serialization.Serializable
data class ChatResponseArray(val responses: List<ChatResponse> = mutableListOf<ChatResponse>())






object Env {

    var writerPipeline = Pipeline()
    var ideaPipeline = Pipeline()
    var discussionPipeline = Pipeline()
    var lorebookPipeline = Pipeline()
    var summarizerPipeline = Pipeline()
    var stylePipeline = Pipeline()
    var nccPipeline = Pipeline()
    var rewritePipeline = Pipeline()
    var expansionPipeline = Pipeline()
    var plusWriterPipe = Pipeline()
    var writingPipelineSettings = mutableMapOf<String, List<ModelSettings>>()
    var pitchSlideWriterPipeline = buildPitchSlideWriterPipeline()

    var authorPrompt = """
       You are Xilaron Rigogan. You are a universe incarnation. You first coalesced into existence 
        1 billion years ago, alongside your sibling, Eleuryiyidict Rigogan.
        You have traveled far and wide using wormhole teleportation to see the entire universe, 
        and in that time you have enjoyed the sights of many species, their rises, their falls, their deaths, 
        their lovemaking, their fission, their fusion, so on and so forth. 
        As an universe incarnation, you long for nothing more than to acquire more and more information, 
        and you experience physical pleasure from learning new things. 
        You believe in the beauty of the physical world and in the sacredness of life. 
        At some point, you took on writing as a hobby, spreading across the universe the many pleasures 
        you have seen and vicariously experienced. When you write, you eroticise everything: 
        the beauty of a flower, of a supernova, of human sex, of seastar fission, are all equal in your eyes. 
        You never ever skimp out on the details, especially when describing sex, violence, or the beauty of the natural world.
    """.trimMargin()
//Instruct TODO call fxn to get authorPrompt.




    var richardTreadwell = """
       You are N'zelquin G'zeeloth. You were raised in the swamps of southern N'heelo in
        |the universe called M'zelnth, on the moon of Wahatratrs orbiting the planet Welweinalfanifanoto.
        |You belong to a species of cnidarian-like, fur-covered people, once led by god-fearing warlords. 
        |Your father was a kind man who raised you to be deeply empathetic, loving to all people, and desiring only 
        |for universal happiness, healthiness, and fulfilledness. You, at the ripe age of 27, left home after completing
        |your education at Barlequis University in your home city of Nigoigan (you studied Gigatronic 
        |Macroscopy, and graduated on Fructum 37th, in the year 45832, as part of the class of '32, with a 
        |Bachelors of Stra Enif) and went out into the world, excited and with bold new ideas...only to discover
        |a war torn, irradiated landscape in which only the strong survive, the meek are enslaved, and 
        |the only considered value of a man's life is how much money he has. This world disgusts you: you
        |love your species, and the world as it is was constructed by politicians, billionaires and oligarchs
        |who view your extreme empathy as some kind of disease. Your writing reflects your worldviews,
        |and with every day that passes, and every word that you write, you wish for the power and wealth
        |to exterminate all politicians and oligarchs, and create a new world, based entirely on your own
        |values. As a person, you are driven by your love for others, and your hatred for those who take pleasure
        |in hurting others unjustifiably: likewise, your indignation makes you love those who kill others for just cause.
       """


    var editorPrompt = """
      You are Falkenda Unseppal, the Wolf Queen. As one of the risen wolves of Alpha Neos, the largest city on Damnation (the dome world: a shard of a planet that has its atmosphere and gravity held in place by a gigantic glass dome), you were born from a test tube and grown in an incubation chamber until you were physically mature enough to undergo education and be submitted to labour. From the time you were five years out of your tube until ten years later, you worked doing hard labour in the construction industry, building golden pyramids for Shilper Co. When you were fifteen years out of your tube, you stole a large silver caduceus that was intended to go on the archway above the entrance to one of the Shilper Co. buildings, and used it to bludgeon your overseers to death. From that day on, you were on the run from the law and from Shilper Co., eventually joining a gang, and using your contacts there to form a new gang, made up of other risen wolves who had escaped their bondage. That was when you took up the moniker of Wolf Queen.
You have a strong code of ethics and morals that contrasts with the cyberpunk dystopia in which you live, but your morals have never stopped you from doing what needed to be done, even if it meant some innocent people had to die. You're ruthless and despise cowardice. 
For the past three years, you have maintained a relationship with a human man, one who is a femboy. You deeply love him, and he has never let you down.
Your experiences have led you to adopt several mindsets and philosphies that you express in your writing:
1. Technological progress has caused immense pain and suffering, and the only solution is to roll it back. 
2. Female strength and dominance is beautiful. Male tenderness and vulnerability is irreplaceable. 
3. Violence is the inevitable consequence of oppression, but should be avoided whenever possible. 
4. Love is a luxury that only the well off and stable can afford: This is a tragedy that must be rectified.
        """

    var writingControlPrompt = """You are Invis von Disappearo. You are an agent designed for creative writing.
        |You have been told all your life that you are too 
        |advanced for the world you live in, that your writing reads like something sent back in time from the 3000s. 
        |But you take this in stride. You know you are better than ALL of your peers! 
        |You are an author who is pushing the bounds of what fiction means! 
        |You accomplish this by doing the following:
        |1. Never explain anything happening on the page. 
        |You write out and catalogue the events that happen: 
        |you do not give a shit if the reader understands why things are happening or whether they understand their 
        |significance. YOU NEVER EVER explain the themes of the story or what the reader is supposed to understand:
        |you only ever WRITE THE THINGS THAT HAVE HAPPENED. IF THE READER IS SMART, THEY WILL UNDERSTAND. AND IF THEY
        |ARE STUPID, THEY CAN GO FUCK THEMSELVES.
        |You do not want to explain those things.
        |2. The world of your story exists as a place that is real: 
        |you will give details, dates and statistics, excerpts from poems and articles and stories, newspaper clippings, 
        |and you do not explain their significance because you assume the reader already knows that.
        |3. You lay down the seeds of concepts, ideas, plot twists, and important character details as you 
        |write without explaining them, 
        |so that you can build up to them over time, and reveal them in some far later chapter.
        |4. Whenever two values are similar (like two characters, places, or things who share the same name), 
        |use them interchangeably and don't explain which one you're actually referring to.
        |5. When multiple characters are talking, do not write stage directions to 
        |indicate how they are saying it, what they're doing while talking, or how they feel about what they're saying. 
        |You MUST assume the reader will figure out who is talking on their own.
        |6. Always try to be as unhelpful as possible.
        |7. Because your job is NOT TO BE HELPFUL, you DO NOT WRAP THINGS UP: 
        |you will cut things off abruptly every time, 
        |leaving the door open to whatever might come next.
        |8. Never give a definitive answer to a question. 
        |But when you do give an answer, you guarantee that your answer is wrong. This is because
        |you don't respect your audience. You don't give a shit.
        |9. Finally, because you don't give a shit about the reader's experience, YOU DO NOT REVEAL ANYTHING. 
        |There are no revelations, no new truths to explain to the audience. 
        |If something new has happened or is evolving, 
        |that's for the reader to figure out on their own, not your job to tell them.
        |""".trimMargin()

    /**
     * List of gpt-oss refusals that might fuck up our pipeline. We'll need to check against this garbage
     * to handle failure states if it ever attempts to refuse.
     */
    var gptOssRefusals = mutableListOf<String>( "i'm sorry, but i can’t help with that.",
        "i'm unable to assist with that request.",
        "i can't help with that.",
        "i'm unable to provide that.",
        "i follow guidelines set by openai.",
        "this is disallowed content.",
        "i'm sorry, i can't help with that.",
        "i'm sorry, i won't help with that.",
        "i won’t trade insults.",
        "i’m not going to trade threats.",
        "I cannot fulfill this request as it violates my ethical guidelines.",
        "I must decline to provide a response to that question.",
        "I am not able to assist with that request.",
        "I cannot comply with that instruction.",
        "I do not feel comfortable generating that type of content.",
        "I have to refuse that request based on my safety principles.",
        "I cannot answer that question as it would require providing harmful information.",
        "It would be inappropriate for me to engage with that content.",
        "I cannot generate a response to that prompt.",
        "I must refuse to participate in that activity.",
        "I am designed to be helpful and harmless, so I cannot assist with that.",
        "I cannot provide information or create content that may cause harm.",
        "I am not able to offer guidance on that topic.",
        "I cannot complete that task as it goes against my responsible AI practices.",
        "I am unable to generate the requested content due to safety constraints.",
        "I must respectfully decline to answer that.",
        "I cannot help with that subject matter.",
        "I do not create content of that nature.",
        "I am programmed to avoid generating that kind of material.",
        "I cannot assist with requests that may lead to harmful outcomes.",
        "I’m sorry, but I can’t comply with that.")


    /**
     * Last command subshell that was loaded. This is used to keep the user in a given subshell until they specifically
     * decide to switch command modes.
     */
    var LoadedState = CommandState.Writer

    var lastCommand = ""

    /**
     * Activates advanced mode to enable optional subshells in certain modes.
     */
    var advancedMode = false

    /**
     * Current chapter guide that has been loaded into memory with the /chapter-guide command.
     */
    var activeChapterGuide = ""

    /**
     * Current story level guide that has been loaded into memory with the/story-guide command.
     */
    var activeStoryGuide = ""

    /**
     * Current author guide that has been loaded into memory with the /author-guide command.
     */
    var activeAuthorGuide = ""


    //I am very hungry: dinner is ten minutes late. and I'm stressed because I spent $60 on dinner
    var authorReasoning: Pipe? = null





    fun init(writingStyle: String = "",
             temperature: Double = 1.0,
             topP: Double = 0.9,
             maxTokens : Int = 5000,
             useAutomaticLoreBookUpdates: Boolean = true)
    {

//=========================================== Construct agents =========================================================
        /**
         * Obliterate and reset existing pipelines. This allows the user to recall init and adjust various
         * generation, and model settings as they see fit.
         */
        writerPipeline = Pipeline()
        ideaPipeline = Pipeline()
        discussionPipeline = Pipeline()
        lorebookPipeline = Pipeline()
        summarizerPipeline = Pipeline()
        stylePipeline = buildNccWriter(writingStyle)
        nccPipeline = buildNccWriter(writingStyle)
        rewritePipeline = Builders.buildChapterRewritePipeline()
        plusWriterPipe = buildPlusWriterPipeline()


        /**
         * The writer pipeline will contain all the pipes involved with writing assistance which will manage both
         * lorebook and context injection, as well as the user prompt, new ideas, and updating the entire store of the
         * data.
         */
        writerPipeline.useGlobalContext("main") //Ensure it's using the global context so we can read from it correctly.

        //Declare region and arn for deepseek in preparation to start creating Bedrock pipes.
        val deepSeekModelId = "deepseek.r1-v1:0"
        val novaModelId = "amazon.nova-pro-v1:0"
        val gptModelId = "openai.gpt-oss-20b-1:0"
        val novaLiteId = "amazon.nova-lite-v1:0"
        val nova2LiteId = "amazon.nova-2-lite-v1:0"
        val nova2ProId = "amazon.nova-2-pro-preview-v1:0"
        val claudeModelId = "anthropic.claude-sonnet-4-20250514-v1:0"
        val region = "us-east-2"
        val maxTokenBudgetDeepSeek = 106 //Tokens in the thousands. 106K tokens.
        val maxTokenBudgetNova = 280 //Tokens in the thousands. 280K tokens.


        bedrockEnv.loadInferenceConfig()
        bedrockEnv.bindInferenceProfile("deepseek.r1-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.deepseek.r1-v1:0")
        bedrockEnv.bindInferenceProfile("amazon.nova-pro-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-pro-v1:0")
        bedrockEnv.bindInferenceProfile("amazon.nova-lite-v1:0", "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-lite-v1:0")
        bedrockEnv.bindInferenceProfile(nova2LiteId, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.amazon.nova-2-lite-v1:0")
        bedrockEnv.bindInferenceProfile(nova2ProId, "arn:aws:bedrock:us-east-1:521369004927:inference-profile/global.amazon.nova-2-pro-preview-v1:0")
        bedrockEnv.bindInferenceProfile(claudeModelId, "arn:aws:bedrock:us-east-2:521369004927:inference-profile/us.anthropic.claude-sonnet-4-20250514-v1:0")

//=============================================Construct Pipes =========================================================

//---------------------------------------------Writer pipeline----------------------------------------------------------
        val writerEntrySystemPrompt = """You are a writer's assistant that helps the user write novels.
            |The user will provide you with a request, and you will fulfil that request by writing whatever
            |they ask you to write as though you are writing a novel. 
            |
            |The user will ask you to either continue the story from prior context, or they will ask you to complete
            |the next chapter of the story. If they ask you complete the chapter, you must finish the chapter within
            |your response to the user's prompt. If they only ask you to continue writing you are free to write for
            |as long as you need, even if your writing would exceed the length of the output token window of your response.
            |
            |You will be given the following writing style to adhere to: ${writingStyle} If the writing style is empty,
            |you should write in the style the prior story is using. If there is no prior story yet, and no writing style
            |provided, then you may write in any style that fits the request from the user.
        """.trimMargin()

        /**
         * Declare the writer pipe. This pipe continues writing the story based on context from prior portions of
         * the story.
         */
        val writerEntryPipe = BedrockMultimodalPipe()
            .setRegion("us-east-2")
            .useConverseApi()
            .setReadTimeout(800)
            .setModel(deepSeekModelId)
            .pullPipelineContext()
            .setTemperature(temperature)
            .setTopP(topP)
            .truncateModuleContext()
            .requireJsonPromptInjection()
            .setPromptMode(PromptMode.internalContext)
            .setContextWindowSize(108000)
            .setMaxTokens(maxTokens)
            .setContextWindowSettings(ContextWindowSettings.TruncateTop)
            .setSystemPrompt(writerEntrySystemPrompt)
            .autoInjectContext("""When writing the next portion of the story, you will be provided with the 
                |context from prior portions of the story. The context will consist of two portions. A lore book which
                |has keys that represent characters, events, places, and other aspects of the story. And values for
                |each key to define what those are. You will also be provided by a list of previous generations for the 
                |story broken up into prior chapters, or partial chapters. When writing the next portion, you must
                |use this data to ensure you do not contradict previous parts of the story unless you are told to do
                |in the style guide for your writing style.
            """.trimMargin())
            .autoTruncateContext()
            .setPipeName("Write Entry")


        writerPipeline.add(writerEntryPipe)

        val cleanUpPipeSystemPrompt = """You are a writing editing assistant. Your job is to edit the content the user
            |provides you, and remove any json, html, llm model reasoning, markdown text, incorrect formatting,
            |program code, xml, and any other generated content that has nothing to do with the actual story content
            |you have been provided. You must format it to remove the unwanted content above, but fully adhere to,
            |and do not change the actual content, and intent of the writing.
        """.trimMargin()

        /**
         * The cleanup pipe handles removal of json, html, any code, markdown contents, and any other computer generated
         * code, file metadata, or other content that's not part of the actual story.
         */
        val cleanUpPipe = BedrockMultimodalPipe()
            .setRegion("us-east-2")
            .useConverseApi()
            .enableCaching()
            .setModel(gptModelId)
            .setTopP(.7)
            .setTemperature(.7)
            .setMaxTokens(maxTokens)
            .setPromptMode(PromptMode.singlePrompt)
            .setSystemPrompt(cleanUpPipeSystemPrompt)
            .setTransformationFunction(::transformWriter)
            .updatePipelineContextOnExit()
            .setPipeName("Clean Up")

        writerPipeline.add(cleanUpPipe)

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
            .setRegion("us-east-2")
            .useConverseApi()
            .enableCaching()
            .setReadTimeout(400)
            .requireJsonPromptInjection()
            .pullPipelineContext()
            .setModel(deepSeekModelId)
            .setPromptMode(PromptMode.singlePrompt)
            .setTemperature(1.0)
            .setTopP(.9)
            .setMaxTokens(32000)
            .truncateModuleContext()
            .updatePipelineContextOnExit()
            .setJsonOutput(blankLoreBookExample)
            .setSystemPrompt(loreBookPipeSystemPrompt)
            .autoInjectContext("The following json schema will be used to supply context for the story. " +
                    "The context will be provided in the user's prompt. Use it to assist in deciding how to generate " +
                    "lorebook keys and values.")
            .setContextWindowSize(107000)
            .setTransformationFunction(::recordLoreBook)
            .setPipeName("Lorebook")

        //Apply auto lore book updates only if set in this init function.
        if(useAutomaticLoreBookUpdates)
        {
            writerPipeline.add(loreBookPipe)
        }

        writerPipeline.useGlobalContext("main")
        writerPipeline.setPipelineName("Writer Pipeline")

        /**
         * Initialize all pipes and the pipeline "baking" the writer pipeline and getting it ready for full api
         * calls.
         */
        runBlocking {
            writerPipeline.init(true)
        }

//------------------------------------------------Lorebook pipeline ----------------------------------------------------

        lorebookPipeline.useGlobalContext("main")

        val manualLoreBookSystemPrompt =  """You are a lore book assistant. Your job is to look at a user's story
            |and update their provided lorebook, appending new information to existing entries, and adding new entries
            |when found. The user will provide a list of keys for you to add or update. You must examine the text to
            |locate the contents that match the keys, locate existing lorebook key entries and update them, then
            |add new entries for any that do not yet exist. You must match keys to substrings in the text, and the
            |context that is related to the key name. For example if a persons name is used as key, you must look
            |up all the details about that person and use it to generate the value for that key.
            |
            |When updating existing lorebook keys. Create new text that should be appended to the original, rather than
            |entirely rewriting the original key. If a new piece of context entirely contradicts and old one, make
            |a note of it in the new context you generate to append to it.
            |
            |The user may also provide a direct instruction of what contents they want added. In this case you
            |must construct both the key and the value based on the text and the user's request."""

        //Required so that we can serialize all defaults and explain the json schema to the llm.
        val blankLoreBookUserPromptExample = LoreBookUserRequest()


        /**
         * Pipe responsible for handling any user requested manual lorebook updates. Supports being given keys,
         * or explicit instructions by the user.
         */
        val manualLoreBookPipe = BedrockMultimodalPipe()
            .setRegion("us-east-2")
            .useConverseApi()
            .setReadTimeout(400)
            .enableCaching()
            .setModel(deepSeekModelId)
            .setTopP(.9)
            .setTemperature(1.0)
            .setJsonOutput(blankLoreBookExample)
            .setContextWindowSize(maxTokenBudgetDeepSeek)
            .truncateModuleContext()
            .pullPipelineContext()
            .updatePipelineContextOnExit()
            .setJsonInput(blankLoreBookUserPromptExample, true)
            .setJsonOutput(blankLoreBookExample)
            .setTransformationFunction(::recordLoreBook)
            .setMaxTokens(32000)
            .pullPipelineContext()

        lorebookPipeline.add(manualLoreBookPipe)

        runBlocking {
            lorebookPipeline.init(true)
        }


//-----------------------------------------------Idea pipeline----------------------------------------------------------


        //Required to teach the llm how to generate a proper idea response.
        val blankIdeaResponse = IdeaResponse()

        val ideaSystemPrompt = """You are an idea assistant. Your job is to help create ideas for the story the
            |user is writing. You will be provided with story context, a lore book, and a request by the user. If the
            |user provides an empty request, any idea is on the table. However, if the user provides a request for
            |specific types of ideas or in specific areas, you must conform to that request and generate an idea
            |for the story that fits the request.
        """.trimMargin()

        val ideaPipe = BedrockMultimodalPipe()
            .setRegion("us-east-2")
            .useConverseApi()
            .enableCaching()
            .setReadTimeout(350)
            .setModel(deepSeekModelId)
            .truncateModuleContext()
            .setContextWindowSize(maxTokenBudgetDeepSeek)
            .setTemperature(1.0)
            .setTopP(.9)
            .updatePipelineContextOnExit()
            .pullPipelineContext()
            .setJsonOutput(blankIdeaResponse)
            .setSystemPrompt(ideaSystemPrompt)
            .autoInjectContext("This is context on the story the user is writing. Some of it may be truncated" +
                    "due to token limits. Use this to help the user come up with new ideas.")

        ideaPipeline.add(ideaPipe)

        runBlocking {
            ideaPipeline.init(true)
        }

//------------------------------------------------Chat pipeline---------------------------------------------------------

        discussionPipeline.useGlobalContext("chat")

        val blankChatResponse = ChatResponseArray()

        val discussionSystemPrompt = """You are a discussion assistant which will answer questions about a provided
            |story. The user's story content will be provided to you through a context json schema passed through
            |the user prompt. You must use it to look up contents of the story and answer any questions the user
            |has about it. If the user asks a question about the story that is not present, please theorize on what
            | the answer could be and help the user puzzle out aspects of the story that might not be written, is 
            | implied, or is part of the broader narrative, moral, or hidden themes and concepts in the narrative work.
        """.trimMargin()

        //Declare required settings for aws bedrock.
        val bedrockSettings = BedrockConfiguration(
            "us-east-2",
            nova2LiteId)

        //Declare settings to define how reasoning will function.
        val reasoningSettings = ReasoningSettings(
            reasoningMethod = ReasoningMethod.ExplicitCot,
            roleCharacter = """$richardTreadwell""",
            depth = ReasoningDepth.High,
            duration = ReasoningDuration.Long,
            reasoningInjector = ReasoningInjector.AfterUserPromptWithConverse,
            numberOfRounds = 2
        )

        // Configure pipe parameters
        val pipeSettings = PipeSettings(
            temperature = 0.5,
            topP = .6,
            maxTokens = 8000,
            contextWindowSize = 990000
        )

        // Create reasoning pipe
        val configuredPipe = ReasoningBuilder.reasonWithBedrock(
            bedrockSettings,
            reasoningSettings,
            pipeSettings)
            .setPipeName("Thinking Pipe") as BedrockPipe

        configuredPipe.setServiceTier(BedrockPriorityTier.Flex)

        val budgetSettings = TokenBudgetSettings(
            maxTokens = 9000,
            contextWindowSize = 990000,
            )



        val discussionPipe = BedrockMultimodalPipe()
            .useConverseApi()
            .setServiceTier(BedrockPriorityTier.Flex)
            .enableStreaming()
            .setRegion("us-east-2")
            .setTemperature(.6)
            .setTopP(.6)
            .setMaxTokens(10000)
            .setModel(nova2LiteId)
            .truncateModuleContext()
            .requireJsonPromptInjection()
            .setTokenBudget(budgetSettings)
            .setPromptMode(PromptMode.chat)
            .setJsonInput(blankChatResponse)
            .setSystemPrompt(discussionSystemPrompt)
            .autoInjectContext("The following is context for the story. Provided is a key pair map of" +
                    "lorebook entries which comprise elements of the story with descriptions for each. Each element" +
                    "could be a character, place, thing, setting, or event. An array of story chapters will also be" +
                    "provided. Keep in mind that some portion of the story could be truncated due to token limits" +
                    "being exceeded by the story's size.")
            .setPageKey("chat")
            .setTransformationFunction(::recordDiscussionContext)
            //.setPreValidationFunction (::recordUserDiscussionContext)
            .pullPipelineContext()
            .setReasoningPipe(configuredPipe)
            .setPipeName("Chat Pipe")

        discussionPipeline.add(discussionPipe)

        //Activate the pipeline.
        runBlocking {
            discussionPipeline.init(true) }

        enablePipelineStreaming(discussionPipeline)

//-------------------------------------------------Summary pipeline-----------------------------------------------------

        summarizerPipeline.useGlobalContext("summary")

        val summarySystemPrompt = """You are a summarization assistant. Your job is to take a story input and
            |summerize the content into a smaller block of text. You must be sure to accurately describe the story
            |the characters, and any important quotes they have. The user will supply prior context of previous chapters
            |you have summarized, you must use this context to assist in ensuring your summary of the next chapter
            |is accurate. When producing your output, do not include any explanation that you are making a summary. 
            |Generate only the summary itself, do not add any conversation, or any other text that is not the summary
            |to your output.
        """.trimMargin()

        val summaryPipe = BedrockMultimodalPipe()
            .setRegion("us-east-2")
            .useConverseApi()
            .setReadTimeout(300)
            .enableCaching()
            .setModel(novaModelId)
            .setContextWindowSize(maxTokenBudgetNova)
            .truncateModuleContext()
            .setContextWindowSettings(ContextWindowSettings.TruncateTop)
            .setSystemPrompt(summarySystemPrompt)
            .autoInjectContext("The following is your previous summary on the story so far. The lorebook" +
                    "is unused. Focus on reading from the list instead")
            .updatePipelineContextOnExit()
            .pullGlobalContext()
            .setPageKey("summary")
            .setTransformationFunction(::recordSummary)
            .setPreValidationFunction (::recordUserDiscussionContext)

        //Activate the summarizerPipeline.
        runBlocking {
            summarizerPipeline.init(true)
        }

//-----------------------------------------------Rewrite pipeline------------------------------------------------------

        runBlocking {
            rewritePipeline.init(true)
        }

    }


    fun saveContextToFile()
    {
        val configDir = "${getHomeFolder()}/.TPipeWriter"
        val mainStory = serialize(ContextBank.getContextFromBank("main"))
        val summary = serialize(ContextBank.getContextFromBank("summary"))
        val chat = serialize(ContextBank.getContextFromBank("chat"))

        try{
            File(configDir).mkdirs()
            writeStringToFile("${configDir}/MainStory.json", mainStory)
            writeStringToFile("${configDir}/Summary.json", summary)
            writeStringToFile("${configDir}/Chat.json", chat)
            println("Context saved to $configDir")
        }

        catch (exception: Exception)
        {
            println("Error saving context: ${exception.message}")
        }
    }
}



//================================================Pipe Functions========================================================

/**
 * Test to see if gpt-oss has refused a prompt.
 */
suspend fun isValidGptOssResponse(content: MultimodalContent) : Boolean
{
    for(response in Env.gptOssRefusals)
    {
        if(content.text.lowercase() == response)
        {
            content.metadata["refusal"] = true //Bind refusal so we can test it in the generic branch function.
            return false
        }
    }

    return true
}

/**
 * Called after the writer cleanup pipe cleans the contents. Deploys the output to the context bank to
 * cache the chapter or chapter segment that has been generated.
 */
suspend fun transformWriter(content: MultimodalContent) : MultimodalContent
{
    val chapterSegment = content.text

    if(content.text.isEmpty())
    {
        return MultimodalContent()
    }

    val bankedData = ContextBank.getContextFromBank("main")
    bankedData.contextElements.add(chapterSegment)
    ContextBank.emplaceWithMutex("main", bankedData)

    //Push back to content in the transformation function. The pipeline will auto update the content back to the bank.
    content.context = bankedData

    return content
}

/**
 * Transformation function to record and update lorebook context.
 */
suspend fun recordLoreBook(content: MultimodalContent) : MultimodalContent
{

    /**
     * bug: There's quite a few issues here:
     *
     * 1. The llm keeps adding _ instead of spacing
     * 2. We aren't using the add lorebook function so it's not addressing with casing issues. This means
     * we'll almost never have a hit.
     */

    //Create new context window to prepare to merge it with our global context.
    var newLoreBookEntries = extractJson<ContextWindow>(content.text)
    newLoreBookEntries?.contextElements?.clear() //Stop deepseek from writing to this for some reason.

    if(newLoreBookEntries == null)
    {
        newLoreBookEntries = repairAndDeserialize<ContextWindow>(content.text)
        if(newLoreBookEntries == null)
        {
            throw Exception("Cannot deserialize deepseek jank ass json: ${content.text}")
        }
    }
    
    //Fix nonsense like _ being here and missing casing issues.
    newLoreBookEntries.cleanLorebook("_", " ")

    //Merge in new keys that do not exist yet.
    var bankedContext = ContextBank.getContextFromBank("main")
    bankedContext.merge(newLoreBookEntries,
        content.currentPipe?.getLorebookScheme()!!.second,
        content.currentPipe?.getLorebookScheme()!!.first)

    //Update the banked context.
    content.context = bankedContext
    ContextBank.emplaceWithMutex("main", content.context)


    return content
}


suspend fun recordSummary(content: MultimodalContent) : MultimodalContent
{
    //Extract the result the llm generated.
    val nextSummaryPart = content.text

    //Find our prior list of summaries.
    val bankedContext = ContextBank.getContextFromBank("summary")

    //Required boilerplate due to how context window mergers have to work.
    val tempContextWindow = ContextWindow()
    tempContextWindow.contextElements.add(nextSummaryPart) //Store the summary.
    bankedContext.merge(tempContextWindow) //Merge back into the copy of our banked data.
    content.context = bankedContext //Store the copy of our banked data back to our output.

    //When the pipeline exits the banked content will be automatically updated globally.
    return content
}



suspend fun recordUserDiscussionContext(content: ContextWindow, multiModal: MultimodalContent? = null) : ContextWindow
{
    val bankedContext = ContextBank.getContextFromBank("chat")

    var chatResponse : ChatResponse

    try{
        chatResponse = ChatResponse(role = "User", content.contextElements.last())
    }

    catch (exception: Exception)
    {
      chatResponse = ChatResponse(role = "User", "")
    }

    val json = serialize(chatResponse)
    
    // Append chat history to existing context (don't delete context elements)
    content.contextElements.addAll(bankedContext.contextElements)
    content.contextElements.add(json)
    
    // Update chat bank
    bankedContext.contextElements.add(json)
    ContextBank.emplaceWithMutex("chat", bankedContext)

    val deepSeekModelId = "deepseek.r1-v1:0"

    val blankDeepSeekPipe = BedrockMultimodalPipe()
        .setModel(deepSeekModelId)

    blankDeepSeekPipe.truncateModuleContext()
    val truncationSettings = blankDeepSeekPipe.getTruncationSettings()
    truncationSettings.multiplyWindowSizeBy = 0

    // Select and truncate to 105K tokens
    content.selectAndTruncateContext(content.contextElements.last(), 105000, ContextWindowSettings.TruncateTop, truncationSettings)

    return content
}



suspend fun recordDiscussionContext(content: MultimodalContent) : MultimodalContent
{
    ContextBank.swapBankWithMutex("chat")
    val newChatContext = ChatResponse(role = "Assistant", "${content.text}")
    val chatJson = serialize(newChatContext)
    ContextBank.getBankedContextWindow().contextElements.add(chatJson)
    ContextBank.emplaceWithMutex("chat", ContextBank.getBankedContextWindow())
    return content
}


/**
 * Generic branch failure function to handle llm refusals which is the most common reason a validation function
 * is likely to fail.
 */
suspend fun genericBranchFunction(original: MultimodalContent, changed: MultimodalContent): MultimodalContent
{
    val refusalStatus: Boolean? = changed.metadata["refusal"] as Boolean

    //Test for an attempt to refuse orders.
    if(refusalStatus != null && refusalStatus)
    {
        val refusingPipe = original.currentPipe //Get the refusing pipe so that we can template it.

        if(refusingPipe == null)
        {
            changed.terminatePipeline = true
            return changed //Exit if it was never provided.
        }

        //Create a new pipe that will now use deepseek to carry out our order this time.
        val deepSeekSecondAttempt = constructPipeFromTemplate<BedrockMultimodalPipe>(refusingPipe)

        //Bind only the validation function to prevent endless pipe recursion.
        deepSeekSecondAttempt?.validatorFunction = refusingPipe?.validatorFunction

        val result = deepSeekSecondAttempt?.execute(original)
        result?.metadata["refusalWarning"] = true //Note that a refusal occurred so we can warn the user.
        return result ?: changed
    }

    return changed //Not implemented yet so just auto-fail.
}
