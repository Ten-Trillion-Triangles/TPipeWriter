package Shell

import Builders.buildPlusWriterPipeline
import Globals.Env
import Structs.ModelSettings
import Structs.updatePipeWithModelSettings
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Util.getHomeFolder
import com.TTT.Util.readStringFromFile
import com.TTT.Util.writeStringToFile
import kotlinx.coroutines.runBlocking
import readEnhancedInput

fun selectGuideMode()
{
    val guideModeEntry = """
        
        Select one of the following:
        
        1. Save Chapter Guide
        2. Load Chapter Guide
        3. Save Story Guide
        4. Load Story Guide
        5. Save Author Guide
        6. Load Author Guide
    """.trimIndent()

    println(guideModeEntry)
    
    val userChoice = readEnhancedInput().toInt()

    when (userChoice)
    {
        1 -> saveChapterGuide()
        2 -> loadChapterGuide()
        3 -> saveStoryGuide()
        4 -> loadStoryGuide()
        5 -> saveAuthorGuide()
        6 -> loadAuthorGuide()
    }
}


/**
 * Allow the user to pull in a new chapter guide and save it to a given path name.
 */
fun saveChapterGuide()
{
    println("\n\nEnter the contents of your chapter guide here:\n\n")

    /**
     * Accept enhanced input and read the data from the user to import their guide.
     */
    val guide = readEnhancedInput(delimiter = "save", removeDelimiterAtEnd = true)

    println("\n\nEnter the name of your new chapter guide\n\n")
    val guideName = readln()

    //Save our guide to disk at the standard TPipeWriter location on the users hard drive.
    try{
        writeStringToFile("${getHomeFolder()}/TPipeWriter/${guideName}-Chapter-Guide.txt", guide)
        val bankWin = ContextWindow()
        bankWin.contextElements.add(guide)
        ContextBank.emplace("chapter guide", bankWin)
    }
    catch (e: Exception)
    {

    }

    Env.activeChapterGuide = guide
    
    // Update settings to persist the guide
    val currentSettings = loadSettings().copy(chapterGuide = guide)
    saveSettings(currentSettings)
    
    println("\n\nChapter guide has been saved.")

}


/**
 * Load the chapter guide by its guide name. Returns an empty string if it cannot.
 */
fun loadChapterGuide() : String
{
    /**
     * Attempt to load the file specified by the guide name from the main TPipeWriter folder. Returns an empty string
     * if we cannot find it.
     */

    println("\n\nEnter the name of the chapter guide you wish to load\n\n")
    val guideName = readEnhancedInput()

    return try {
        val guide = readStringFromFile("${getHomeFolder()}/TPipeWriter/${guideName}-Chapter-Guide.txt")

        if(guide.isEmpty())
        {
            return ""
        }

        //Save as our current chapter guide after we've loaded it.
        Env.activeChapterGuide = guide
        
        // Update settings to persist the guide
        val currentSettings = loadSettings().copy(chapterGuide = guide)
        saveSettings(currentSettings)
        
        println("\n\nChapter Guide:\n\n${guide}")
        val bankWin = ContextWindow()
        bankWin.contextElements.add(guide)
        ContextBank.emplace("chapter guide", bankWin)
        return guide
    }
    catch (e: Exception)
    {
        ""
    }
}

/**
 * Allow the user to pull in a new story guide and save it to a given path name.
 */
fun saveStoryGuide()
{
    println("\n\nEnter the contents of your chapter guide here:\n\n")

    /**
     * Accept enhanced input and read the data from the user to import their guide.
     */
    val guide = readEnhancedInput(delimiter = "save", removeDelimiterAtEnd = true)

    println("\n\nEnter the name of your new chapter guide\n\n")
    val guideName = readln()

    //Save our guide to disk at the standard TPipeWriter location on the users hard drive.
    try{
        writeStringToFile("${getHomeFolder()}/TPipeWriter/${guideName}-Story-Guide.txt", guide)
        val bankWin = ContextWindow()
        bankWin.contextElements.add(guide)
        ContextBank.emplace("story guide", bankWin)
    }
    catch (e: Exception)
    {

    }

    Env.activeStoryGuide = guide
    
    // Update settings to persist the guide
    val currentSettings = loadSettings().copy(storyGuide = guide)
    saveSettings(currentSettings)
    
    println("\n\nStory guide has been saved.")

}


/**
 * Load the story guide by its guide name. Returns an empty string if it cannot.
 */
fun loadStoryGuide() : String
{
    /**
     * Attempt to load the file specified by the guide name from the main TPipeWriter folder. Returns an empty string
     * if we cannot find it.
     */

    println("\n\nEnter the name of the story guide you wish to load\n\n")
    val guideName = readEnhancedInput()

    return try {
        val guide = readStringFromFile("${getHomeFolder()}/TPipeWriter/${guideName}-Story-Guide.txt")

        if(guide.isEmpty())
        {
            return ""
        }

        //Save as our current chapter guide after we've loaded it.
        Env.activeStoryGuide = guide
        
        // Update settings to persist the guide
        val currentSettings = loadSettings().copy(storyGuide = guide)
        saveSettings(currentSettings)
        
        println("\n\nStory Guide:\n\n${guide}")
        val bankWin = ContextWindow()
        bankWin.contextElements.add(guide)
        ContextBank.emplace("story guide", bankWin)
        return guide
    }
    catch (e: Exception)
    {
        ""
    }
}






fun saveAuthorGuide()
{
    //Print subshell entry point to the terminal.
    println("""
        
        Enter the contents of your author guide here:
        
        
    """.trimIndent())

    //Read the user's input to pull the author's guide. Accepts paste macros or direct input.
    val authorGuide = readEnhancedInput()

    //Store to env as first step of our saving process.
    Env.authorPrompt = authorGuide

    println("\n\nEnter the name of your guide file here.")

    val filePath = readln()

    try {
        writeStringToFile("${getHomeFolder()}/TPipeWriter/${filePath}-author-guide.txt", authorGuide)
    }

    catch (e: Exception)
    {
        println(e)
    }

    //Save settings back to disk.
    val settings = loadSettings().copy(authorGuide = authorGuide)
    saveSettings(settings)

    println("\n\nAuthor guide has been set.")
}

/**
 * Load the author guide from settings and display it.
 */
fun loadAuthorGuide()
{
    println("""
        
        Enter the name of the author guide you wish to load.
    """.trimIndent())

    val guideName = readEnhancedInput()

    val guide = readStringFromFile("${getHomeFolder()}/TPipeWriter/${guideName}-author-guide.txt")

    val settings = loadSettings()
    settings.authorGuide = guide

}


fun saveRichardTreadwell()
{
    println("""
        
        Input your Richard Treadwell here:
    """.trimIndent())

    /**
     * The very heart and soul of Richard. Once inputted he'll be ready to read Benign Skies as a competing author
     * and write as if every word he wrote was the word you had written.
     */
    val richardTreadwell = readEnhancedInput()

    println("""
        
        Enter the name of the file you are saving Richard Treadwell to:
    """.trimIndent())

    /**
     * I looked at Christophe Black and felt ashamed of myself for not realizing that I already had a home, and that I was looking for another one, and wondering why I hadn't realized sooner that I was already home.
     * "Home is where love resides."
     * "Love?"
     * "Yes, Louis. Love. In whatever form it takes, whether it's a person, or a thing, or a place—whether it's a memory of a place, or a feeling of a moment, or an emotion that makes you feel warm and safe and comfortable inside yourself, wherever it is, that's home."
     * "Why did you say 'home'?"
     * "Because I thought you might understand."
     * I nodded.
     */
    val whereHomeIsLouis = readln()

    try {
        /**
         * And now we must immortalize Richard Treadwell forever, so that he may be called upon whenever we need help.
         * Help in whatever form it takes, and wherever that help lies. Help reading the book, and saving everyone,
         * and reassuring Derrik, and informing Derrick that he shall soon die.
         *
         * Derrick washington... You are going to die. That's how it is.
         */
        writeStringToFile("${getHomeFolder()}/TPipeWriter/${whereHomeIsLouis}-Richard.treadwell", richardTreadwell)

        /**
         * To forever remember, I hereby declare the expansion of the Christophe Black memorial Dormitory.
         * And so thus forth the Richard Treadwell reading room shall be built.
         */
        val settings = loadSettings()

        /**
         *  as though they were competing authors rather than colleagues working together toward the same goal;
         *  they had been friends for many years but only recently had they begun collaborating on an academic
         *  paper that would eventually become part of Ben Mendelson's second published novel
         *  rather than one of his unpublished manuscripts that would never see print again;
         *  they had learned early on that working together required them both to speak as though each other
         *  had written every word they themselves had wrote.
         *  I was there in the room with them, watching as they read.
         */
        settings.competingAuthorGuide = richardTreadwell

        /**
         * Could this really save everyone? Was trusting Ben after everything he's done a good idea?
         * I didn't know. I didn't know, but I was out of options.
         */
        saveSettings(settings)
    }
    catch (e: Exception)
    {
        println(e)
    }
}

fun loadRichardTreadwell()
{
    println("""
        
        Enter the name of the Richard Treadwell file you wish to load:
    """.trimIndent())

    val whereHomeIsLouis = readln()

    try {
        /**
         * And now we must resurrect Richard Treadwell from his eternal slumber, so that he may once again
         * help us in whatever form it takes, and wherever that help lies. Help reading the book, and saving everyone,
         * and reassuring Derrik, and informing Derrick that he shall soon die.
         */
        val richardTreadwell = readStringFromFile("${getHomeFolder()}/TPipeWriter/${whereHomeIsLouis}-Richard.treadwell")

        if(richardTreadwell.isEmpty())
        {
            println("Richard Treadwell could not be found. The home where love resides remains empty.")
            return
        }

        /**
         * To forever remember, I hereby declare the reopening of the Richard Treadwell reading room.
         * The competing author has returned to guide us once more.
         */
        val settings = loadSettings()
        settings.competingAuthorGuide = richardTreadwell

        /**
         * Could this really save everyone? Was trusting Ben after everything he's done a good idea?
         * I didn't know. I didn't know, but I was out of options.
         */
        saveSettings(settings)

        println("""
        
        Richard Treadwell has been loaded:
        
        ${richardTreadwell}
        """.trimIndent())
    }
    catch (e: Exception)
    {
        println("Error loading Richard Treadwell: ${e.message}")
    }
}
