package utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NewStringManipulationUtilsTest {

    @Test
    fun testRegexReplace() {
        assertEquals("hxllo", NewStringManipulationUtils.regexReplace("hello", "e", "x"))
        assertEquals("hello", NewStringManipulationUtils.regexReplace("hello", "z", "x"))
        assertEquals("h3llo", NewStringManipulationUtils.regexReplace("hello", "[aeiou]", "3"))
    }

    @Test
    fun testIsNumeric() {
        assertTrue(NewStringManipulationUtils.isNumeric("12345"))
        assertFalse(NewStringManipulationUtils.isNumeric("123a5"))
        assertFalse(NewStringManipulationUtils.isNumeric(""))
    }

    @Test
    fun testIsAlphabetic() {
        assertTrue(NewStringManipulationUtils.isAlphabetic("hello"))
        assertFalse(NewStringManipulationUtils.isAlphabetic("hello123"))
        assertFalse(NewStringManipulationUtils.isAlphabetic(""))
    }

    @Test
    fun testBase64Encode() {
        assertEquals("aGVsbG8=", NewStringManipulationUtils.base64Encode("hello"))
        assertEquals("", NewStringManipulationUtils.base64Encode(""))
    }

    @Test
    fun testBase64Decode() {
        assertEquals("hello", NewStringManipulationUtils.base64Decode("aGVsbG8="))
        assertEquals("", NewStringManipulationUtils.base64Decode(""))
        assertThrows(IllegalArgumentException::class.java) {
            NewStringManipulationUtils.base64Decode("invalid_base64")
        }
    }

    @Test
    fun testUrlEncode() {
        assertEquals("hello%20world", NewStringManipulationUtils.urlEncode("hello world"))
        assertEquals("", NewStringManipulationUtils.urlEncode(""))
    }

    @Test
    fun testUrlDecode() {
        assertEquals("hello world", NewStringManipulationUtils.urlDecode("hello%20world"))
        assertEquals("", NewStringManipulationUtils.urlDecode(""))
        assertThrows(IllegalArgumentException::class.java) {
            NewStringManipulationUtils.urlDecode("%invalid")
        }
    }

    @Test
    fun testCaseInsensitiveCompare() {
        assertEquals(0, NewStringManipulationUtils.caseInsensitiveCompare("hello", "HELLO"))
        assertTrue(NewStringManipulationUtils.caseInsensitiveCompare("abc", "ABC") == 0)
        assertTrue(NewStringManipulationUtils.caseInsensitiveCompare("abc", "def") < 0)
    }

    @Test
    fun testNaturalOrderCompare() {
        assertTrue(NewStringManipulationUtils.naturalOrderCompare("file1", "file2") < 0)
        assertTrue(NewStringManipulationUtils.naturalOrderCompare("file10", "file2") > 0)
        assertEquals(0, NewStringManipulationUtils.naturalOrderCompare("file1", "file1"))
    }

    @Test
    fun testNormalize() {
        assertEquals("cafe", StringManipulationUtils.normalize("café"))
        assertEquals("hello world", NewStringManipulationUtils.normalize("Hello World"))
        assertEquals("naive", NewStringManipulationUtils.normalize("naïve"))
    }

    @Test
    fun testLevenshteinDistance() {
        assertEquals(0, NewStringManipulationUtils.levenshteinDistance("kitten", "kitten"))
        assertEquals(3, NewStringManipulationUtils.levenshteinDistance("kitten", "sitting"))
        assertEquals(1, NewStringManipulationUtils.levenshteinDistance("flaw", "flaws"))
        assertEquals(2, NewStringManipulationUtils.levenshteinDistance("gumbo", "gambol"))
    }
}