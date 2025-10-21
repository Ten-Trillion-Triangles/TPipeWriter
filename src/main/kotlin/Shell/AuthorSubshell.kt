package Shell

import readEnhancedInput

fun selectAuthorMode()
{
    val authorModeEntry = """
        
        Select one of the following:
        
        1. Save Author Guide
        2. Load Author Guide
        3. Save Richard Treadwell
        4. Load Richard Treadwell
        5. Exit
    """.trimIndent()

    println(authorModeEntry)
    
    val userChoice = readEnhancedInput().toIntOrNull() ?: return

    when (userChoice)
    {
        1 -> saveAuthorGuide()
        2 -> loadAuthorGuide()
        3 -> saveRichardTreadwell()
        4 -> loadRichardTreadwell()
        5 -> return
    }
}
