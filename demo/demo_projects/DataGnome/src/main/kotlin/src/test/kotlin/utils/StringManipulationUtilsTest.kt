package utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StringManipulationUtilsTest {
    @Test
    fun testReverseString() {

        assertEquals("dcba", StringManipulationUtils.reverseString("abcd"))
        assertEquals("olleh", StringManipulationUtils.reverseString("hello"))

        assertEquals("", StringManipulationUtils.reverseString(""))

        assertEquals("!@#$%^&*()", StringManipulationUtils.reverseString(")(*&^%$#@!"))
        assertEquals("12345!@# ", StringManipulationUtils.reverseString(" #@!54321"))
    }

    @Test
    fun testRemoveWhitespace() {
        assertEquals("helloworld", StringManipulationUtils.removeWhitespace("hello world"))
        assertEquals("helloworld", StringManipulationUtils.removeWhitespace("  hello  world  "))
        assertEquals("", StringManipulationUtils.removeWhitespace("   "))
        assertEquals("", StringManipulationUtils.removeWhitespace(null))
    }

    @Test
    fun testReplaceFirst() {
        assertEquals("hxllo", StringManipulationUtils.replaceFirst("hello", "e", "x"))
        assertEquals("hello", StringManipulationUtils.replaceFirst("hello", "z", "x"))
        assertEquals("", StringManipulationUtils.replaceFirst(null, "e", "x"))
    }

    @Test
    fun testReplaceLast() {
        assertEquals("hellx", StringManipulationUtils.replaceLast("hello", "o", "x"))
        assertEquals("hello", StringManipulationUtils.replaceLast("hello", "z", "x"))
        assertEquals("", StringManipulationUtils.replaceLast(null, "o", "x"))
    }

    @Test
    fun testTruncate() {
        assertEquals("hel...", StringManipulationUtils.truncate("hello world", 6))
        assertEquals("hello", StringManipulationUtils.truncate("hello", 10))
        assertEquals("", StringManipulationUtils.truncate("", 5))
        assertEquals("", StringManipulationUtils.truncate(null, 5))
    }

    @Test
    fun testWrapText() {
        assertEquals("hello\nworld", StringManipulationUtils.wrapText("hello world", 5))
        assertEquals("hello", StringManipulationUtils.wrapText("hello", 10))
        assertEquals("", StringManipulationUtils.wrapText("", 5))
        assertEquals("", StringManipulationUtils.wrapText(null, 5))
    }

    @Test
    fun testRemoveWhitespace() {
        assertEquals("helloworld", StringManipulationUtils.removeWhitespace("hello world"))
        assertEquals("helloworld", StringManipulationUtils.removeWhitespace("  hello  world  "))
        assertEquals("", StringManipulationUtils.removeWhitespace("   "))
    }

    @Test
    fun testReplaceFirst() {
        assertEquals("hxllo", StringManipulationUtils.replaceFirst("hello", "e", "x"))
        assertEquals("hello", StringManipulationUtils.replaceFirst("hello", "z", "x"))
    }

    @Test
    fun testReplaceLast() {
        assertEquals("hellx", StringManipulationUtils.replaceLast("hello", "o", "x"))
        assertEquals("hello", StringManipulationUtils.replaceLast("hello", "z", "x"))
    }

    @Test
    fun testTruncate() {
        assertEquals("hel...", StringManipulationUtils.truncate("hello world", 6))
        assertEquals("hello", StringManipulationUtils.truncate("hello", 10))
        assertEquals("", StringManipulationUtils.truncate("", 5))
    }

    @Test
    fun testWrapText() {
        assertEquals("hello\nworld", StringManipulationUtils.wrapText("hello world", 5))
        assertEquals("hello", StringManipulationUtils.wrapText("hello", 10))
        assertEquals("", StringManipulationUtils.wrapText("", 5))
    }

    @Test
    fun testRemoveWhitespace() {
        assertEquals("helloworld", StringManipulationUtils.removeWhitespace("hello world"))
        assertEquals("helloworld", StringManipulationUtils.removeWhitespace("  hello  world  "))
        assertEquals("", StringManipulationUtils.removeWhitespace("   "))
    }

    @Test
    fun testReplaceFirst() {
        assertEquals("hxllo", StringManipulationUtils.replaceFirst("hello", "e", "x"))
        assertEquals("hello", StringManipulationUtils.replaceFirst("hello", "z", "x"))
    }

    @Test
    fun testReplaceLast() {
        assertEquals("hellx", StringManipulationUtils.replaceLast("hello", "o", "x"))
        assertEquals("hello", StringManipulationUtils.replaceLast("hello", "z", "x"))
    }

    @Test
    fun testTruncate() {
        assertEquals("hel...", StringManipulationUtils.truncate("hello world", 6))
        assertEquals("hello", StringManipulationUtils.truncate("hello", 10))
        assertEquals("", StringManipulationUtils.truncate("", 5))
    }

    @Test
    fun testWrapText() {
        assertEquals("hello\nworld", StringManipulationUtils.wrapText("hello world", 5))
        assertEquals("hello", StringManipulationUtils.wrapText("hello", 10))
        assertEquals("", StringManipulationUtils.wrapText("", 5))
    }

    @Test
    fun testTrim() {
        assertEquals("hello", StringManipulationUtils.trim("  hello  "))
        assertEquals("hello", StringManipulationUtils.trim("hello"))
        assertEquals("", StringManipulationUtils.trim(""))
        assertEquals("", StringManipulationUtils.trim(null))
    }

    @Test
    fun testSplit() {
        assertEquals(listOf("a", "b", "c"), StringManipulationUtils.split("a,b,c", ","))
        assertEquals(listOf("a"), StringManipulationUtils.split("a", ","))
        assertEquals(listOf(""), StringManipulationUtils.split("", ","))
        assertEquals(emptyList<String>(), StringManipulationUtils.split(null, ","))
    }

    @Test
    fun testJoin() {
        assertEquals("a,b,c", StringManipulationUtils.join(listOf("a", "b", "c"), ","))
        assertEquals("", StringManipulationUtils.join(emptyList(), ","))
        assertEquals("a", StringManipulationUtils.join(listOf("a"), ","))
        assertEquals("", StringManipulationUtils.join(null, ","))
    }

    @Test
    fun testToUpperCase() {
        assertEquals("HELLO", StringManipulationUtils.toUpperCase("hello"))
        assertEquals("", StringManipulationUtils.toUpperCase(""))
        assertEquals("HELLO WORLD", StringManipulationUtils.toUpperCase("hello world"))
        assertEquals("", StringManipulationUtils.toUpperCase(null))
    }

    @Test
    fun testToLowerCase() {
        assertEquals("hello", StringManipulationUtils.toLowerCase("HELLO"))
        assertEquals("", StringManipulationUtils.toLowerCase(""))
        assertEquals("hello world", StringManipulationUtils.toLowerCase("HELLO WORLD"))
        assertEquals("", StringManipulationUtils.toLowerCase(null))
    }

    @Test
    fun testCapitalizeFirst() {
        assertEquals("Hello", StringManipulationUtils.capitalizeFirst("hello"))
        assertEquals("Hello", StringManipulationUtils.capitalizeFirst("Hello"))
        assertEquals("", StringManipulationUtils.capitalizeFirst(""))
        assertEquals("", StringManipulationUtils.capitalizeFirst(null))
    }

    @Test
    fun testToKebabCase() {
        assertEquals("hello-world", StringManipulationUtils.toKebabCase("Hello World"))
        assertEquals("hello", StringManipulationUtils.toKebabCase("Hello"))
        assertEquals("", StringManipulationUtils.toKebabCase(""))
        assertEquals("", StringManipulationUtils.toKebabCase(null))
    }

    @Test
    fun testToSnakeCase() {
        assertEquals("hello_world", StringManipulationUtils.toSnakeCase("Hello World"))
        assertEquals("hello", StringManipulationUtils.toSnakeCase("Hello"))
        assertEquals("", StringManipulationUtils.toSnakeCase(""))
        assertEquals("", StringManipulationUtils.toSnakeCase(null))
    }

    @Test
    fun testRepeat() {
        assertEquals("aaa", StringManipulationUtils.repeat("a", 3))
        assertEquals("", StringManipulationUtils.repeat("a", 0))
        assertThrows(IllegalArgumentException::class.java) {
            StringManipulationUtils.repeat("a", -1)
        }
        assertEquals("", StringManipulationUtils.repeat(null, 3))
    }

    @Test
    fun testContains() {
        assertTrue(StringManipulationUtils.contains("hello", "ell"))
        assertFalse(StringManipulationUtils.contains("hello", "xyz"))
        assertFalse(StringManipulationUtils.contains(null, "ell"))
    }

    @Test
    fun testPad() {
        assertEquals("  hello  ", StringManipulationUtils.pad("hello", 9))
        assertEquals("hello", StringManipulationUtils.pad("hello", 5))
        assertEquals("", StringManipulationUtils.pad(null, 9))
    }

    @Test
    fun testReverse() {
        assertEquals("dcba", StringManipulationUtils.reverse("abcd"))
        assertEquals("", StringManipulationUtils.reverse(""))
        assertEquals("", StringManipulationUtils.reverse(null))
    }

    @Test
    fun testIsPalindrome() {
        assertTrue(StringManipulationUtils.isPalindrome("madam"))
        assertFalse(StringManipulationUtils.isPalindrome("hello"))
        assertFalse(StringManipulationUtils.isPalindrome(null))
    }

    @Test
    fun testCapitalizeWords() {
        assertEquals("Hello World", StringManipulationUtils.capitalizeWords("hello world"))
        assertEquals("Hello", StringManipulationUtils.capitalizeWords("hello"))
        assertEquals("", StringManipulationUtils.capitalizeWords(null))
    }

    @Test
    fun testTrimStart() {
        assertEquals("hello  ", StringManipulationUtils.trimStart("  hello  "))
        assertEquals("", StringManipulationUtils.trimStart("   "))
        assertEquals("", StringManipulationUtils.trimStart(null))
    }

    @Test
    fun testTrimEnd() {
        assertEquals("  hello", StringManipulationUtils.trimEnd("  hello  "))
        assertEquals("", StringManipulationUtils.trimEnd("   "))
        assertEquals("", StringManipulationUtils.trimEnd(null))
    }

    @Test
    fun testReplace() {
        assertEquals("hxllo", StringManipulationUtils.replace("hello", "e", "x"))
        assertEquals("hello", StringManipulationUtils.replace("hello", "z", "x"))
        assertEquals("", StringManipulationUtils.replace(null, "e", "x"))
    }

    @Test
    fun testSubstring() {
        assertEquals("ell", StringManipulationUtils.substring("hello", 1, 4))
        assertEquals("", StringManipulationUtils.substring("hello", 1, 1))
        assertEquals("", StringManipulationUtils.substring(null, 1, 4))
    }

    @Test
    fun testIndexOf() {
        assertEquals(1, StringManipulationUtils.indexOf("hello", "e"))
        assertEquals(-1, StringManipulationUtils.indexOf("hello", "z"))
        assertEquals(-1, StringManipulationUtils.indexOf(null, "e"))
    }

    @Test
    fun testLastIndexOf() {
        assertEquals(1, StringManipulationUtils.lastIndexOf("hello", "e"))
        assertEquals(-1, StringManipulationUtils.lastIndexOf("hello", "z"))
        assertEquals(-1, StringManipulationUtils.lastIndexOf(null, "e"))
    }

    @Test
    fun testStartsWith() {
        assertTrue(StringManipulationUtils.startsWith("hello", "he"))
        assertFalse(StringManipulationUtils.startsWith("hello", "lo"))
        assertFalse(StringManipulationUtils.startsWith(null, "he"))
    }

    @Test
    fun testEndsWith() {
        assertTrue(StringManipulationUtils.endsWith("hello", "lo"))
        assertFalse(StringManipulationUtils.endsWith("hello", "he"))
        assertFalse(StringManipulationUtils.endsWith(null, "lo"))
    }

    @Test
    fun testCharAt() {
        assertEquals('e', StringManipulationUtils.charAt("hello", 1))
        assertThrows(StringIndexOutOfBoundsException::class.java) {
            StringManipulationUtils.charAt("hello", 5)
        }
        assertThrows(StringIndexOutOfBoundsException::class.java) {
            StringManipulationUtils.charAt(null, 0)
        }
    }

    @Test
    fun testLength() {
        assertEquals(5, StringManipulationUtils.length("hello"))
        assertEquals(0, StringManipulationUtils.length(""))
        assertEquals(0, StringManipulationUtils.length(null))
    }

    @Test
    fun testFormat() {
        assertEquals("Hello, John!", StringManipulationUtils.format("Hello, %s!", "John"))
        assertEquals("Value: 42", StringManipulationUtils.format("Value: %d", 42))
        assertEquals("", StringManipulationUtils.format(null, "John"))
    }
}