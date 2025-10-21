import com.TTT.Context.ContextBank
import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

// --- LARGE DEFAULTS (tune as needed) ---
private const val BYTE_BUFFER_SIZE: Int = 20 * 1024 * 1024      // 1 MiB
private const val CHAR_BUFFER_SIZE: Int = 50 * 1024 * 1024      // 1 MiB
private const val LINEBUF_INITIAL_CAPACITY: Int = 20 * 1024 * 1024  // 1 MiB
private const val INITIAL_CAPACITY: Int = 20 * 1024 * 1024      // 8 MiB

fun readEnhancedInput(
    delimiter: String = "c", // accepts a single string of end tokens separated by ", "
    charset: Charset = Charsets.UTF_8,
    byteBufferSize: Int = BYTE_BUFFER_SIZE,
    charBufferSize: Int = CHAR_BUFFER_SIZE,
    lineBufInitialCapacity: Int = LINEBUF_INITIAL_CAPACITY,
    initialCapacity: Int = INITIAL_CAPACITY,
    maxChars: Long = Long.MAX_VALUE,
    removeDelimiterAtEnd: Boolean = false
): String {
    require(delimiter.isNotEmpty()) { "Delimiter must be non-empty." }
    require(byteBufferSize > 0 && charBufferSize > 0 && lineBufInitialCapacity >= 0 && initialCapacity >= 0)

    // Parse the incoming delimiter string into multiple end tokens, split by ", "
    val delimiters: List<String> = delimiter.split(", ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    require(delimiters.isNotEmpty()) { "No valid end delimiters parsed." }

    val inStream = BufferedInputStream(System.`in`, byteBufferSize)
    val reader = InputStreamReader(inStream, charset)

    // --- Read exactly the first line without overshooting ---
    val firstLineBuf = StringBuilder(lineBufInitialCapacity.coerceAtLeast(4096))
    var prevWasCR = false
    while (true) {
        val chInt = reader.read()
        if (chInt == -1) break
        val ch = chInt.toChar()
        if (ch == '\n') {
            if (prevWasCR && firstLineBuf.isNotEmpty() && firstLineBuf[firstLineBuf.length - 1] == '\r') {
                firstLineBuf.setLength(firstLineBuf.length - 1)
            }
            break
        } else {
            prevWasCR = (ch == '\r')
            firstLineBuf.append(ch)
        }
    }

    val processedLine = processClipboardMacros(firstLineBuf.toString())

    if (!detectMultilineContent(processedLine)) {
        return processedLine
    }

    println("Multiline input detected. Continue pasting or type any of ${delimiters.joinToString(", ")} to finish:")

    val result = StringBuilder(initialCapacity)
    val lineBuf = StringBuilder(lineBufInitialCapacity)
    val cbuf = CharArray(charBufferSize)

    fun ensureRoom(extra: Int) {
        if (result.length.toLong() + extra > maxChars) {
            throw IllegalStateException("Input exceeds maxChars limit ($maxChars).")
        }
    }

    // Append the already-processed first line + newline
    ensureRoom(processedLine.length + 1)
    result.append(processedLine).append('\n')

    fun isDelimiterLine(buf: StringBuilder, effectiveLen: Int): Boolean {
        // Check against all delimiter candidates
        for (d in delimiters) {
            if (d.length == effectiveLen) {
                var match = true
                for (i in 0 until effectiveLen) {
                    if (buf[i] != d[i]) {
                        match = false
                        break
                    }
                }
                if (match) return true
            }
        }
        return false
    }

    while (true) {
        val read = reader.read(cbuf, 0, cbuf.size)
        if (read == -1) {
            // EOF: flush last partial line if present and not the delimiter
            if (lineBuf.isNotEmpty()) {
                val effLen = if (lineBuf[lineBuf.length - 1] == '\r') lineBuf.length - 1 else lineBuf.length
                if (!isDelimiterLine(lineBuf, effLen)) {
                    ensureRoom(effLen)
                    result.append(lineBuf, 0, effLen)
                }
                lineBuf.setLength(0)
            }
            break
        }

        var i = 0
        while (i < read) {
            val ch = cbuf[i++]
            if (ch == '\n') {
                val effLen = if (lineBuf.isNotEmpty() && lineBuf[lineBuf.length - 1] == '\r') {
                    lineBuf.length - 1
                } else lineBuf.length

                if (isDelimiterLine(lineBuf, effLen)) {
                    return result.toString()
                }

                ensureRoom(effLen + 1)
                result.append(lineBuf, 0, effLen).append('\n')
                lineBuf.setLength(0)
            } else {
                lineBuf.append(ch)
                if (result.length.toLong() + lineBuf.length > maxChars) {
                    throw IllegalStateException("Input exceeds maxChars limit ($maxChars).")
                }
            }
        }
    }

    if(removeDelimiterAtEnd)
    {
        val resultString = stripMultiLineDelimiter(result.toString())
        return resultString
    }

    return result.toString()
}




fun detectMultilineContent(line: String): Boolean {
    val hasOpenBracket = line.count { it == '{' } > line.count { it == '}' } ||
            line.count { it == '[' } > line.count { it == ']' }
    val endsIncomplete = line.trimEnd().let {
        it.endsWith(",") || it.endsWith(":") || it.endsWith("\\")
    }
    val hasCodeBlock = line.contains("```") && line.count { it == '`' } % 6 != 0
    val isLargePaste = line.length > 1000

    return hasOpenBracket || endsIncomplete || hasCodeBlock || isLargePaste
}

fun processClipboardMacros(text: String): String {
    var result = text

    if (result.contains("@context")) {
        val context = ContextBank.getContextFromBank("main").toString()
        result = result.replace("@context", context)
    }

    if (result.contains("@lore")) {
        val lore = ContextBank.getContextFromBank("main").loreBookKeys.toString()
        result = result.replace("@lore", lore)
    }

    if (result.contains("@clip")) {
        val clipContent = try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.getData(DataFlavor.stringFlavor) as? String ?: ""
        } catch (e: Exception) { "" }
        result = result.replace("@clip", clipContent)
    }

    if (result.contains("@file")) {
        val fileIndex = result.indexOf("@file")
        val afterFile = result.substring(fileIndex + 5)
        val filePath = afterFile.substringBefore(" ").substringBefore("\n")
        val fileContent = try {
            java.io.File(filePath).readText()
        } catch (e: Exception) { "" }
        result = result.replace("@file$filePath", fileContent)
    }

    return result
}

/**
 * Get the last line out of the delimiter so it doesn't bleed into the string.
 */
fun stripMultiLineDelimiter(input: String, delimiters: List<String> = listOf<String>()): String
{
    if(delimiters.isEmpty())
    {
        val newResult = input.split("\n").toMutableList()
        if (newResult.isEmpty()) return ""
        newResult.removeAt(newResult.lastIndex)
        return newResult.joinToString("\n")
    }

    val newResult = input.split("\n").toMutableList()
    if (newResult.isEmpty()) return ""
    for(char in delimiters)
    {
        if(newResult.last().contains(char))
        {
            newResult.removeAt(newResult.lastIndex)
            return newResult.joinToString("\n")
        }
    }

    return ""

}