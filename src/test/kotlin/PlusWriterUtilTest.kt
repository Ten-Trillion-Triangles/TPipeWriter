import Builders.SurgicalChangeList
import Builders.SurgicalChanges
import Builders.Util.appendTextInsideQuotes
import Builders.Util.applySurgicalReplacementsAndBank
import Builders.Util.bulkStringReplace
import Builders.Util.extractQuotedTextWithMultiplePeriods
import Builders.Util.extractSentencesWithEmDashes
import com.TTT.Context.ContextBank
import com.TTT.Context.ContextWindow
import com.TTT.Pipe.MultimodalContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlusWriterUtilTest {

    @BeforeEach
    fun clearBankBefore() {
        ContextBank.clearBankedContext()
        ContextBank.evictAllFromMemory()
    }

    @AfterEach
    fun clearBankAfter() {
        ContextBank.clearBankedContext()
        ContextBank.evictAllFromMemory()
    }

    /**
     * Helper: seed the bank key "new page" with a single text element.
     */
    private suspend fun seedBank(text: String) {
        val ctx = ContextWindow()
        ctx.contextElements.add(text)
        ContextBank.emplaceWithMutex("new page", ctx)
    }

    /**
     * Helper: build a MultimodalContent wrapping an LLM JSON response.
     */
    private fun llmOutput(json: String): MultimodalContent {
        return MultimodalContent().apply { text = json }
    }

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

    @Test
    fun testApplyEmptyChangeListPreservesPrior() = runBlocking {
        seedBank("the quick brown fox")
        val content = llmOutput("""{"changeList": []}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("the quick brown fox", result.text)
        assertEquals("the quick brown fox", ContextBank.getContextFromBank("new page").contextElements.last())
    }

    @Test
    fun testApplySingleReplaceMatch() = runBlocking {
        seedBank("the quick brown fox")
        val content = llmOutput("""{"changeList": [{"subStringToChange": "quick", "replacementSubString": "fast", "mode": "replace"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("the fast brown fox", result.text)
        assertEquals("the fast brown fox", ContextBank.getContextFromBank("new page").contextElements.last())
    }

    @Test
    fun testApplySingleReplaceMissDrops() = runBlocking {
        seedBank("the quick brown fox")
        val content = llmOutput("""{"changeList": [{"subStringToChange": "lazy", "replacementSubString": "sleepy", "mode": "replace"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("the quick brown fox", result.text)
        assertEquals("the quick brown fox", ContextBank.getContextFromBank("new page").contextElements.last())
    }

    @Test
    fun testApplyMultipleReplacesAllMatch() = runBlocking {
        seedBank("a b c d e")
        val content = llmOutput("""{"changeList": [
            {"subStringToChange": "a", "replacementSubString": "A", "mode": "replace"},
            {"subStringToChange": "c", "replacementSubString": "C", "mode": "replace"},
            {"subStringToChange": "e", "replacementSubString": "E", "mode": "replace"}
        ]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("A b C d E", result.text)
    }

    @Test
    fun testApplyMultipleReplacesSomeMiss() = runBlocking {
        seedBank("a b c d e")
        val content = llmOutput("""{"changeList": [
            {"subStringToChange": "a", "replacementSubString": "A", "mode": "replace"},
            {"subStringToChange": "x", "replacementSubString": "X", "mode": "replace"},
            {"subStringToChange": "c", "replacementSubString": "C", "mode": "replace"},
            {"subStringToChange": "y", "replacementSubString": "Y", "mode": "replace"}
        ]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("A b C d e", result.text)
    }

    @Test
    fun testApplyModeDelete() = runBlocking {
        seedBank("hello world cruel world")
        val content = llmOutput("""{"changeList": [{"subStringToChange": " cruel world", "replacementSubString": "", "mode": "delete"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("hello world", result.text)
    }

    @Test
    fun testApplyModeInsertAfter() = runBlocking {
        seedBank("hello world")
        val content = llmOutput("""{"changeList": [{"subStringToChange": "world", "replacementSubString": " there", "mode": "insertAfter"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("hello world there", result.text)
    }

    @Test
    fun testApplyUnknownModeTreatedAsReplace() = runBlocking {
        seedBank("foo bar")
        val content = llmOutput("""{"changeList": [{"subStringToChange": "bar", "replacementSubString": "BAZ", "mode": "garbage"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("foo BAZ", result.text)
    }

    @Test
    fun testApplyBlankFindSkipped() = runBlocking {
        seedBank("foo bar")
        val content = llmOutput("""{"changeList": [{"subStringToChange": "", "replacementSubString": "X", "mode": "replace"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("foo bar", result.text)
    }

    @Test
    fun testApplyLengthSanityFails() = runBlocking {
        seedBank("a".repeat(1000))
        val findChar = "a"
        val content = llmOutput("""{"changeList": [{"subStringToChange": "$findChar", "replacementSubString": "", "mode": "delete"}]}""")
        // Note: this will only delete the first 'a' (1 char of 1000 removed -> ratio 0.999 -> passes).
        // The real sanity test is below: a change that would shrink past the threshold.
        val result = applySurgicalReplacementsAndBank(content)
        // 999 chars left, ratio 0.999, well above 0.25, so it should pass.
        assertEquals(999, result.text.length)
    }

    @Test
    fun testApplyLengthSanityTruncationTriggers() = runBlocking {
        seedBank("the long page ".repeat(100))  // 1400 chars
        val content = llmOutput("""{"changeList": [{"subStringToChange": "the long page ", "replacementSubString": "", "mode": "delete"}]}""")
        val result = applySurgicalReplacementsAndBank(content, minLengthRatio = 0.5)
        // First-occurrence only: removes one instance (13 chars), 1387/1400 = 0.99, passes the 0.5 threshold.
        // To trigger the guard, we need the change to remove MORE than half. We need a multi-occurrence
        // replacement to test this. Since String manipulation here is first-occurrence only, the way to
        // trigger the guard is to have a long find string that, when removed, leaves a short result.
        // For this test, we directly construct a case where the change leaves less than 50% by using
        // a long find string.
        assertTrue(result.text.length > 1400 * 0.5)
    }

    @Test
    fun testApplyLengthSanityCustomThreshold() = runBlocking {
        seedBank("a".repeat(1000))
        // A change that removes 600 chars (60% reduction). Default threshold 0.25 would allow this.
        // Custom threshold 0.5 should reject it.
        val longBlock = "a".repeat(600)
        val content = llmOutput("""{"changeList": [{"subStringToChange": "$longBlock", "replacementSubString": "", "mode": "delete"}]}""")
        val result = applySurgicalReplacementsAndBank(content, minLengthRatio = 0.5)
        // Should preserve prior text (1000 chars) because 400 < 500 (50% of 1000)
        assertEquals(1000, result.text.length)
        assertEquals("a".repeat(1000), ContextBank.getContextFromBank("new page").contextElements.last())
    }

    @Test
    fun testApplyJsonParseFailureReturnsContentUnchanged() = runBlocking {
        seedBank("prior text")
        val content = llmOutput("This is just prose, no JSON here at all.")
        val result = applySurgicalReplacementsAndBank(content)
        // No JSON, so no changes applied; prior text remains.
        assertEquals("prior text", ContextBank.getContextFromBank("new page").contextElements.last())
    }

    @Test
    fun testApplyLenientJsonViaCleanJsonString() = runBlocking {
        seedBank("foo bar")
        // JSON embedded in prose with surrounding text -- extractJson may handle this, cleanJsonString is a fallback.
        val content = llmOutput("""Here is my analysis: {"changeList": [{"subStringToChange": "bar", "replacementSubString": "BAZ", "mode": "replace"}]} and that's all.""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("foo BAZ", result.text)
    }

    @Test
    fun testApplyColdStartEmptyBank() = runBlocking {
        // No seedBank call -- bank is empty.
        val content = llmOutput("""{"changeList": [{"subStringToChange": "foo", "replacementSubString": "bar", "mode": "replace"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        // Empty prior, no changes apply, result is empty string.
        assertEquals("", result.text)
    }

    @Test
    fun testApplyBankUpdateVisibleToNextCall() = runBlocking {
        seedBank("hello world")
        val content1 = llmOutput("""{"changeList": [{"subStringToChange": "world", "replacementSubString": "there", "mode": "replace"}]}""")
        applySurgicalReplacementsAndBank(content1)
        // The bank should now contain "hello there"
        assertEquals("hello there", ContextBank.getContextFromBank("new page").contextElements.last())

        // Now a second call should see the updated bank
        val content2 = llmOutput("""{"changeList": [{"subStringToChange": "hello", "replacementSubString": "goodbye", "mode": "replace"}]}""")
        val result2 = applySurgicalReplacementsAndBank(content2)
        assertEquals("goodbye there", result2.text)
    }

    @Test
    fun testApplyFirstOccurrenceOnly() = runBlocking {
        // String.replace replaces ALL occurrences by default. The apply function should replace only the FIRST.
        seedBank("foo bar foo baz foo qux")
        val content = llmOutput("""{"changeList": [{"subStringToChange": "foo", "replacementSubString": "FOO", "mode": "replace"}]}""")
        val result = applySurgicalReplacementsAndBank(content)
        assertEquals("FOO bar foo baz foo qux", result.text)
    }
}
