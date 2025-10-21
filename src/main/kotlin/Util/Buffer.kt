package Util

import java.io.BufferedInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Read arbitrarily large input from STDIN until a line equals [delimiter].
 *
 * - No readln()/readLine()
 * - Efficient chunked reads from System.in
 * - Delimiter must be alone on a line; CRLF/LF handled
 * - Returns the entire captured text as a String
 *
 * @param delimiter Line that terminates input (must be non-empty), not included in the result.
 * @param charset   Charset for decoding bytes from STDIN.
 * @param byteBufferSize Size of the byte buffer used by the underlying BufferedInputStream.
 * @param charBufferSize Size of the char buffer used when reading decoded chars.
 * @param initialCapacity Initial StringBuilder capacity to reduce copying for large inputs.
 * @param maxChars Optional hard ceiling on total characters (defensive). Long.MAX_VALUE for "no limit".
 */
fun readUntilDelimiter(
    delimiter: String,
    charset: Charset = Charsets.UTF_8,
    byteBufferSize: Int = 256 * 1024,   // tune for your environment (e.g., 64K..1M)
    charBufferSize: Int = 256 * 1024,   // usually similar to byteBufferSize is fine
    initialCapacity: Int = 2 * 1024 * 1024, // e.g., 2MB initial capacity for "novel-sized" pastes
    maxChars: Long = Long.MAX_VALUE
): String {
    require(delimiter.isNotEmpty()) { "Delimiter must be non-empty." }
    require(byteBufferSize > 0) { "byteBufferSize must be > 0." }
    require(charBufferSize > 0) { "charBufferSize must be > 0." }
    require(initialCapacity >= 0) { "initialCapacity must be >= 0." }

    val inStream = BufferedInputStream(System.`in`, byteBufferSize)
    val reader = InputStreamReader(inStream, charset)

    val result = StringBuilder(initialCapacity)
    val lineBuf = StringBuilder(4096)
    val cbuf = CharArray(charBufferSize)

    fun ensureRoom(extra: Int) {
        if (result.length.toLong() + extra > maxChars) {
            throw IllegalStateException("Input exceeds maxChars limit ($maxChars).")
        }
    }

    fun equalsDelimiter(buf: StringBuilder, effectiveLen: Int): Boolean {
        if (effectiveLen != delimiter.length) return false
        for (i in 0 until effectiveLen) {
            if (buf[i] != delimiter[i]) return false
        }
        return true
    }

    while (true) {
        val read = reader.read(cbuf, 0, cbuf.size)
        if (read == -1) {
            // EOF: flush any pending line (without newline), unless it's exactly the delimiter
            if (lineBuf.isNotEmpty()) {
                val effectiveLen = if (lineBuf[lineBuf.length - 1] == '\r') lineBuf.length - 1 else lineBuf.length
                if (!equalsDelimiter(lineBuf, effectiveLen)) {
                    ensureRoom(effectiveLen)
                    result.append(lineBuf, 0, effectiveLen)
                }
                lineBuf.setLength(0)
            }
            break
        }

        var i = 0
        while (i < read) {
            val ch = cbuf[i++]
            if (ch == '\n') {
                // Normalize CRLF: if previous char was '\r', ignore it for comparison/output
                val effectiveLen = if (lineBuf.isNotEmpty() && lineBuf[lineBuf.length - 1] == '\r') {
                    lineBuf.length - 1
                } else lineBuf.length

                // Stop if the line matches the delimiter exactly
                if (equalsDelimiter(lineBuf, effectiveLen)) {
                    return result.toString()
                }

                // Append the line and a newline to the result
                ensureRoom(effectiveLen + 1)
                result.append(lineBuf, 0, effectiveLen).append('\n')
                lineBuf.setLength(0)
            } else {
                lineBuf.append(ch)
                // Guard for pathological cases: append in-progress line size against max
                if (result.length.toLong() + lineBuf.length > maxChars) {
                    throw IllegalStateException("Input exceeds maxChars limit ($maxChars).")
                }
            }
        }
    }

    return result.toString()
}