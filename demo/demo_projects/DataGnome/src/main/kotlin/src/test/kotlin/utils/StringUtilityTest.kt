package utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StringUtilityTest {

    @Test
    fun testTrim() {
        assertEquals("Hello World", StringUtility.trim("  Hello World  "))
        assertEquals("", StringUtility.trim(null))
    }

    @Test
    fun testSplit() {
        assertEquals(listOf("apple", "banana", "cherry"), StringUtility.split("apple,banana,cherry", ","))
        assertEquals(emptyList<String>(), StringUtility.split(null, ","))
    }

    @Test
    fun testJoin() {
        assertEquals("apple, banana, cherry", StringUtility.join(listOf("apple", "banana", "cherry"), ", "))
        assertEquals("", StringUtility.join(null, ", "))
    }

    @Test
    fun testToUpperCase() {
        assertEquals("HELLO", StringUtility.toUpperCase("hello"))
        assertEquals("", StringUtility.toUpperCase(null))
    }

    @Test
    fun testToLowerCase() {
        assertEquals("hello", StringUtility.toLowerCase("HELLO"))
        assertEquals("", StringUtility.toLowerCase(null))
    }

    @Test
    fun testCapitalizeFirst() {
        assertEquals("Hello world", StringUtility.capitalizeFirst("hello world"))
        assertEquals("", StringUtility.capitalizeFirst(null))
    }

    @Test
    fun testToKebabCase() {
        assertEquals("hello-world-example", StringUtility.toKebabCase("HelloWorld Example"))
        assertEquals("", StringUtility.toKebabCase(null))
    }

    @Test
    fun testToSnakeCase() {
        assertEquals("hello_world_example", StringUtility.toSnakeCase("HelloWorld Example"))
        assertEquals("", StringUtility.toSnakeCase(null))
    }

    @Test
    fun testRepeat() {
        assertEquals("abcabcabc", StringUtility.repeat("abc", 3))
        assertThrows(IllegalArgumentException::class.java) { StringUtility.repeat("abc", -1) }
        assertEquals("", StringUtility.repeat(null, 3))
    }

    @Test
    fun testContains() {
        assertTrue(StringUtility.contains("Hello World", "World"))
        assertFalse(StringUtility.contains("Hello World", "world"))
        assertFalse(StringUtility.contains(null, "World"))
    }

    @Test
    fun testPad() {
        assertEquals("**Hello***", StringUtility.pad("Hello", 10, "*"))
        assertEquals("", StringUtility.pad(null, 10, "*"))
    }

    @Test
    fun testReverse() {
        assertEquals("olleH", StringUtility.reverse("Hello"))
        assertEquals("", StringUtility.reverse(null))
    }

    @Test
    fun testIsPalindrome() {
        assertTrue(StringUtility.isPalindrome("madam"))
        assertFalse(StringUtility.isPalindrome("hello"))
        assertFalse(StringUtility.isPalindrome(null))
    }

    @Test
    fun testCapitalizeWords() {
        assertEquals("Hello World", StringUtility.capitalizeWords("hello world"))
        assertEquals("", StringUtility.capitalizeWords(null))
    }

    @Test
    fun testTrimStart() {
        assertEquals("Hello", StringUtility.trimStart("  Hello"))
        assertEquals("", StringUtility.trimStart(null))
    }

    @Test
    fun testTrimEnd() {
        assertEquals("Hello", StringUtility.trimEnd("Hello  "))
        assertEquals("", StringUtility.trimEnd(null))
    }

    @Test
    fun testReplace() {
        assertEquals("Hello Kotlin", StringUtility.replace("Hello World", "World", "Kotlin"))
        assertEquals("", StringUtility.replace(null, "World", "Kotlin"))
    }

    @Test
    fun testSubstring() {
        assertEquals("Hello", StringUtility.substring("Hello World", 0, 5))
        assertEquals("", StringUtility.substring(null, 0, 5))
    }

    @Test
    fun testIndexOf() {
        assertEquals(6, StringUtility.indexOf("Hello World", "World"))
        assertEquals(-1, StringUtility.indexOf(null, "World"))
    }

    @Test
    fun testLastIndexOf() {
        assertEquals(12, StringUtility.lastIndexOf("Hello World World", "World"))
        assertEquals(-1, StringUtility.lastIndexOf(null, "World"))
    }

    @Test
    fun testStartsWith() {
        assertTrue(StringUtility.startsWith("Hello World", "Hello"))
        assertFalse(StringUtility.startsWith(null, "Hello"))
    }

    @Test
    fun testEndsWith() {
        assertTrue(StringUtility.endsWith("Hello World", "World"))
        assertFalse(StringUtility.endsWith(null, "World"))
    }

    @Test
    fun testCharAt() {
        assertEquals('e', StringUtility.charAt("Hello", 1))
        assertThrows(StringIndexOutOfBoundsException::class.java) { StringUtility.charAt("Hello", 5) }
        assertThrows(StringIndexOutOfBoundsException::class.java) { StringUtility.charAt(null, 1) }
    }

    @Test
    fun testLength() {
        assertEquals(5, StringUtility.length("Hello"))
        assertEquals(0, StringUtility.length(null))
    }

    @Test
    fun testFormat() {
        assertEquals("Hello World", StringUtility.format("Hello %s", "World"))
        assertEquals("", StringUtility.format(null, "World"))
    }

    @Test
    fun testRemoveWhitespace() {
        assertEquals("Hello", StringUtility.removeWhitespace(" H e l l o "))
        assertEquals("", StringUtility.removeWhitespace(null))
    }

    @Test
    fun testReplaceFirst() {
        assertEquals("Hello Kotlin World", StringUtility.replaceFirst("Hello World World", "World", "Kotlin"))
        assertEquals("", StringUtility.replaceFirst(null, "World", "Kotlin"))
    }

    @Test
    fun testReplaceLast() {
        assertEquals("Hello World Kotlin", StringUtility.replaceLast("Hello World World", "World", "Kotlin"))
        assertEquals("", StringUtility.replaceLast(null, "World", "Kotlin"))
    }

    @Test
    fun testTruncate() {
        assertEquals("Hello", StringUtility.truncate("Hello World", 5))
        assertEquals("", StringUtility.truncate(null, 5))
    }

    @Test
    fun testWrapText() {
        assertEquals("Hello\nWorld", StringUtility.wrapText("Hello World", 5))
        assertEquals("", StringUtility.wrapText(null, 5))
    }
}