import Builders.Util.extractQuotedTextWithMultiplePeriods
import Builders.Util.appendTextInsideQuotes
import Builders.Util.extractSentencesWithEmDashes
import Builders.Util.bulkStringReplace
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PlusWriterUtilTest {

    @Test
    fun testExtractQuotedTextWithMultiplePeriods() {
        // Test case 1: Quoted text with 2+ periods should be included
        val text1 = """He said "Hello there. How are you today. I hope you're well." and left."""
        val result1 = extractQuotedTextWithMultiplePeriods(text1)
        assertEquals(1, result1.size)
        assertEquals("\"Hello there. How are you today. I hope you're well.\"", result1[0])

        // Test case 2: Quoted text with 0-1 periods should be excluded
        val text2 = """She replied "Fine thanks" and "See you later." to him."""
        val result2 = extractQuotedTextWithMultiplePeriods(text2)
        assertEquals(0, result2.size)

        // Test case 3: Multiple quoted segments, some matching
        val text3 = """First "Hello. Good morning. Nice day." then "Bye" and finally "Yes. No. Maybe so." was said."""
        val result3 = extractQuotedTextWithMultiplePeriods(text3)
        assertEquals(2, result3.size)
        assertTrue(result3.contains("\"Hello. Good morning. Nice day.\""))
        assertTrue(result3.contains("\"Yes. No. Maybe so.\""))

        // Test case 4: No quoted text
        val text4 = "This has no quotes at all."
        val result4 = extractQuotedTextWithMultiplePeriods(text4)
        assertEquals(0, result4.size)

        // Test case 5: Empty quotes
        val text5 = """He said "" and left."""
        val result5 = extractQuotedTextWithMultiplePeriods(text5)
        assertEquals(0, result5.size)
    }

    @Test
    fun testAppendTextInsideQuotes() {
        // Test case 1: Single quoted segment
        val text1 = """He said "I said the sky is blue." and left."""
        val result1 = appendTextInsideQuotes(text1, "But I also said the water is blue.")
        assertEquals("""He said "I said the sky is blue. But I also said the water is blue." and left.""", result1)

        // Test case 2: Multiple quoted segments
        val text2 = """She said "Hello." and then "Goodbye." to me."""
        val result2 = appendTextInsideQuotes(text2, "Nice to see you.")
        assertEquals("""She said "Hello. Nice to see you." and then "Goodbye. Nice to see you." to me.""", result2)

        // Test case 3: No quoted text
        val text3 = "This has no quotes at all."
        val result3 = appendTextInsideQuotes(text3, "Extra text.")
        assertEquals("This has no quotes at all.", result3)

        // Test case 4: Empty quotes
        val text4 = """He said "" and left."""
        val result4 = appendTextInsideQuotes(text4, "Something new.")
        assertEquals("""He said " Something new." and left.""", result4)
    }

    @Test
    fun testExtractSentencesWithEmDashes() {
        // Test case 1: Sentences with em dashes should be included with punctuation
        val text1 = "This is normal. This has an em dash—like this. Another normal sentence."
        val result1 = extractSentencesWithEmDashes(text1)
        assertEquals(1, result1.size)
        assertEquals("This has an em dash—like this.", result1[0])

        // Test case 2: Multiple sentences with em dashes, mixed with normal sentences
        val text2 = "First—with dash. Normal sentence. Second—also with dash! No dash here?"
        val result2 = extractSentencesWithEmDashes(text2)
        assertEquals(2, result2.size)
        assertTrue(result2.contains("First—with dash."))
        assertTrue(result2.contains("Second—also with dash!"))

        // Test case 3: No em dashes - should return empty list
        val text3 = "This has no em dashes. Neither does this one."
        val result3 = extractSentencesWithEmDashes(text3)
        assertEquals(0, result3.size)

        // Test case 4: Multiple em dashes in one sentence
        val text4 = "This sentence—has multiple—em dashes in it. Normal sentence."
        val result4 = extractSentencesWithEmDashes(text4)
        assertEquals(1, result4.size)
        assertEquals("This sentence—has multiple—em dashes in it.", result4[0])

        // Test case 5: Mixed punctuation with em dashes
        val text5 = "Question with dash—right? Statement with dash—here. Exclamation with dash—wow!"
        val result5 = extractSentencesWithEmDashes(text5)
        assertEquals(3, result5.size)
        assertTrue(result5.contains("Question with dash—right?"))
        assertTrue(result5.contains("Statement with dash—here."))
        assertTrue(result5.contains("Exclamation with dash—wow!"))
    }

    @Test
    fun testBulkStringReplace() {
        // Test case 1: Single replacement
        val text1 = "Hello world, this is a test."
        val replacements1 = mapOf("world" to "universe")
        val result1 = bulkStringReplace(text1, replacements1)
        assertEquals("Hello universe, this is a test.", result1)

        // Test case 2: Multiple replacements
        val text2 = "The quick brown fox jumps over the lazy dog."
        val replacements2 = mapOf(
            "quick" to "fast",
            "brown" to "red",
            "lazy" to "sleepy"
        )
        val result2 = bulkStringReplace(text2, replacements2)
        assertEquals("The fast red fox jumps over the sleepy dog.", result2)

        // Test case 3: No matches
        val text3 = "This text has no matches."
        val replacements3 = mapOf("xyz" to "abc")
        val result3 = bulkStringReplace(text3, replacements3)
        assertEquals("This text has no matches.", result3)

        // Test case 4: Empty map
        val text4 = "This text should remain unchanged."
        val replacements4 = emptyMap<String, String>()
        val result4 = bulkStringReplace(text4, replacements4)
        assertEquals("This text should remain unchanged.", result4)

        // Test case 5: Overlapping replacements
        val text5 = "abc def abc"
        val replacements5 = mapOf("abc" to "xyz", "def" to "uvw")
        val result5 = bulkStringReplace(text5, replacements5)
        assertEquals("xyz uvw xyz", result5)
    }
}
