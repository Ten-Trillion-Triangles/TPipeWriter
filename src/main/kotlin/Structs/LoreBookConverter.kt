package Structs

import com.TTT.Context.ContextWindow

fun convertToTPipeLoreBook(loreBookData: LoreBookData): ContextWindow
{
    val contextWindow = ContextWindow()
    
    loreBookData.entries.forEach { entry ->
        if (entry.enabled) {
            contextWindow.addLoreBookEntry(
                key = entry.displayName,
                value = entry.text,
                weight = entry.contextConfig.budgetPriority,
                linkedKeys = emptyList(),
                aliasKeys = entry.keys
            )
        }
    }
    
    return contextWindow
}