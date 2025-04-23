import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NewStringUtilityTest {

    @Test
    fun testTrim() {
        assertEquals("hello", NewStringUtility.trim("  hello  "))
        assertEquals("world", NewStringUtility.trim("\tworld\n"))
    }

    @Test
    fun testSplit() {
        assertArrayEquals(arrayOf("a", "b", "c"), NewStringUtility.split("a,b,c", ","))
        assertArrayEquals(arrayOf("one", "two", "three"), NewStringUtility.split("one two three", " "))
    }

    @Test
    fun testJoin() {
        assertEquals("a,b,c", NewStringUtility.join(arrayOf("a", "b", "c"), ","))
        assertEquals("one two three", NewStringUtility.join(arrayOf("one", "two", "three"), " "))
    }

    @Test
    fun testCaseConversion() {
        assertEquals("HELLO", NewStringUtility.toUpperCase("hello"))
        assertEquals("world", NewStringUtility.toLowerCase("WORLD"))
    }

    @Test
    fun testRegexSupport() {
        assertTrue(NewStringUtility.matchesRegex("123-456", "\\d{3}-\\d{3}"))
        assertFalse(NewStringUtility.matchesRegex("abc-xyz", "\\d{3}-\\d{3}"))
    }

    @Test
    fun testEncodingDecoding() {
        val encoded = NewStringUtility.encodeBase64("hello")
        assertEquals("aGVsbG8=", encoded)
        assertEquals("hello", NewStringUtility.decodeBase64(encoded))
    }

    @Test
    fun testPalindromeCheck() {
        assertTrue(NewStringUtility.isPalindrome("madam"))
        assertFalse(NewStringUtility.isPalindrome("hello"))
    }

    @Test
    fun testLevenshteinDistance() {
        assertEquals(3, NewStringUtility.levenshteinDistance("kitten", "sitting"))
        assertEquals(0, NewStringUtility.levenshteinDistance("test", "test"))
    }

    @Test
    fun testPerformance() {
        val largeString = "a".repeat(1000000)
        val startTime = System.currentTimeMillis()
        NewStringUtility.toUpperCase(largeString)
        val endTime = System.currentTimeMillis()
        assertTrue(endTime - startTime < 1000, "Performance test failed")
    }
}