package Util

import bedrockPipe.BedrockMultimodalPipe
import bedrockPipe.BedrockPipe
import com.TTT.Context.ContextBank
import com.TTT.Pipeline.Pipeline
import kotlinx.coroutines.runBlocking
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.DataFlavor

/**
 * Copy text to system clipboard
 * @param text Text to copy
 */
fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    } catch (e: Exception) {
        // Silently fail if clipboard unavailable
    }
}

/**
 * Replace @clip placeholder with clipboard contents
 * @param text Input text that may contain @clip placeholder
 * @return Text with @clip replaced by clipboard contents
 */
fun replaceClipboardPlaceholder(text: String): String {
    if (!text.contains("@clip")) return text
    
    val clipboardContent = try {
        val originalErr = System.err
        System.setErr(java.io.PrintStream(java.io.ByteArrayOutputStream()))
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val result = clipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
        System.setErr(originalErr)
        result
    } catch (e: Throwable) {
        System.setErr(System.err)
        ""
    }
    
    return text.replace("@clip", clipboardContent)
}

/**
 * Read line from input and process clipboard placeholders
 * @return Input string with @clip placeholders replaced
 */
@Deprecated("Use readEnhancedInput() instead")
fun readLineWithClipboard(): String
{
    val input = readln()
    if (input.contains("@context"))
    {
        val context = ContextBank.getContextFromBank("main").toString()
        return input + context
    }

    else if(input.contains("@lore"))
    {
        val context = ContextBank.getContextFromBank("main").loreBookKeys.toString()
        return input + context
    }

    else if(input.contains("@file"))
    {
        val fileIndex = input.indexOf("@file")
        val beforeFile = input.substring(0, fileIndex)
        val filePath = input.substring(fileIndex + 5).trim()
        val fileContent = try {
            java.io.File(filePath).readText()
        } catch (e: Exception) {
            ""
        }
        return beforeFile + fileContent
    }

    return input
}

