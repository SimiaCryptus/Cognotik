package utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeout
import java.time.Duration

class NewStringUtilityTest {

    @Test
    fun testTrim() {
        assertEquals("Hello World", NewStringUtility.trim("  Hello World  "))
        assertEquals("", NewStringUtility.trim(null))
        assertEquals("", NewStringUtility.trim("   "))
        assertEquals("Hello", NewStringUtility.trim("\tHello\n"))
    }

    @Test
    fun testSplit() {
        assertEquals(listOf("apple", "banana", "cherry"), NewStringUtility.split("apple,banana,cherry", ","))
        assertEquals(emptyList<String>(), NewStringUtility.split(null, ","))
        assertEquals(listOf("apple"), NewStringUtility.split("apple", ","))
        assertEquals(listOf("apple", "", "banana"), NewStringUtility.split("apple,,banana", ","))
    }

    @Test
    fun testJoin() {
        assertEquals("apple, banana, cherry", NewStringUtility.join(listOf("apple", "banana", "cherry"), ", "))
        assertEquals("", NewStringUtility.join(null, ", "))
        assertEquals("apple", NewStringUtility.join(listOf("apple"), ", "))
        assertEquals("", NewStringUtility.join(emptyList(), ", "))
    }

    @Test
    fun testToUpperCase() {
        assertEquals("HELLO", NewStringUtility.toUpperCase("hello"))
        assertEquals("", NewStringUtility.toUpperCase(null))
        assertEquals("123", NewStringUtility.toUpperCase("123"))
        assertEquals("HELLO WORLD", NewStringUtility.toUpperCase("Hello World"))
    }

    @Test
    fun testToLowerCase() {
        assertEquals("hello", NewStringUtility.toLowerCase("HELLO"))
        assertEquals("", NewStringUtility.toLowerCase(null))
        assertEquals("123", NewStringUtility.toLowerCase("123"))
        assertEquals("hello world", NewStringUtility.toLowerCase("Hello World"))
    }

    @Test
    fun testCapitalizeFirst() {
        assertEquals("Hello world", NewStringUtility.capitalizeFirst("hello world"))
        assertEquals("", NewStringUtility.capitalizeFirst(null))
        assertEquals("123abc", NewStringUtility.capitalizeFirst("123abc"))
        assertEquals("Hello", NewStringUtility.capitalizeFirst("hello"))
    }

    @Test
    fun testToKebabCase() {
        assertEquals("hello-world-example", NewStringUtility.toKebabCase("HelloWorld Example"))
        assertEquals("", NewStringUtility.toKebabCase(null))
        assertEquals("singleword", NewStringUtility.toKebabCase("SingleWord"))
        assertEquals("hello-world", NewStringUtility.toKebabCase("Hello World"))
    }

    @Test
    fun testToSnakeCase() {
        assertEquals("hello_world_example", NewStringUtility.toSnakeCase("HelloWorld Example"))
        assertEquals("", NewStringUtility.toSnakeCase(null))
        assertEquals("singleword", NewStringUtility.toSnakeCase("SingleWord"))
        assertEquals("hello_world", NewStringUtility.toSnakeCase("Hello World"))
    }

    @Test
    fun testRepeat() {
        assertEquals("abcabcabc", NewStringUtility.repeat("abc", 3))
        assertThrows(IllegalArgumentException::class.java) { NewStringUtility.repeat("abc", -1) }
        assertEquals("", NewStringUtility.repeat(null, 3))
        assertEquals("", NewStringUtility.repeat("", 5))
    }

    @Test
    fun testContains() {
        assertTrue(NewStringUtility.contains("Hello World", "World"))
        assertFalse(NewStringUtility.contains("Hello World", "world"))
        assertFalse(NewStringUtility.contains(null, "World"))
        assertFalse(NewStringUtility.contains("Hello", ""))
    }

    @Test
    fun testPad() {
        assertEquals("**Hello***", NewStringUtility.pad("Hello", 10, "*"))
        assertEquals("", NewStringUtility.pad(null, 10, "*"))
        assertEquals("Hello", NewStringUtility.pad("Hello", 5, "*"))
        assertEquals("Hello", NewStringUtility.pad("Hello", 3, "*"))
    }

    @Test
    fun testReverse() {
        assertEquals("olleH", NewStringUtility.reverse("Hello"))
        assertEquals("", NewStringUtility.reverse(null))
        assertEquals("123", NewStringUtility.reverse("321"))
        assertEquals("", NewStringUtility.reverse(""))
    }

    @Test
    fun testIsPalindrome() {
        assertTrue(NewStringUtility.isPalindrome("madam"))
        assertFalse(NewStringUtility.isPalindrome("hello"))
        assertFalse(NewStringUtility.isPalindrome(null))
        assertTrue(NewStringUtility.isPalindrome("A man a plan a canal Panama".replace(" ", "").lowercase()))
        assertTrue(NewStringUtility.isPalindrome(" "))
    }

    @Test
    fun testCapitalizeWords() {
        assertEquals("Hello World", NewStringUtility.capitalizeWords("hello world"))
        assertEquals("", NewStringUtility.capitalizeWords(null))
        assertEquals("123 Abc", NewStringUtility.capitalizeWords("123 abc"))
        assertEquals("Hello", NewStringUtility.capitalizeWords("hello"))
    }

    @Test
    fun testTrimStart() {
        assertEquals("Hello", NewStringUtility.trimStart("  Hello"))
        assertEquals("", NewStringUtility.trimStart(null))
        assertEquals("Hello  ", NewStringUtility.trimStart("Hello  "))
        assertEquals("Hello", NewStringUtility.trimStart("Hello"))
    }

    @Test
    fun testTrimEnd() {
        assertEquals("Hello", NewStringUtility.trimEnd("Hello  "))
        assertEquals("", NewStringUtility.trimEnd(null))
        assertEquals("  Hello", NewStringUtility.trimEnd("  Hello"))
        assertEquals("Hello", NewStringUtility.trimEnd("Hello"))
    }

    @Test
    fun testReplace() {
        assertEquals("Hello Kotlin", NewStringUtility.replace("Hello World", "World", "Kotlin"))
        assertEquals("", NewStringUtility.replace(null, "World", "Kotlin"))
        assertEquals("Hello World", NewStringUtility.replace("Hello World", "Java", "Kotlin"))
        assertEquals("Hello", NewStringUtility.replace("Hello", "", "Kotlin"))
    }

    @Test
    fun testSubstring() {
        assertEquals("Hello", NewStringUtility.substring("Hello World", 0, 5))
        assertEquals("", NewStringUtility.substring(null, 0, 5))
        assertThrows(StringIndexOutOfBoundsException::class.java) { NewStringUtility.substring("Hello", 0, 10) }
        assertEquals("", NewStringUtility.substring("Hello", 0, 0))
    }

    @Test
    fun testIndexOf() {
        assertEquals(6, NewStringUtility.indexOf("Hello World", "World"))
        assertEquals(-1, NewStringUtility.indexOf(null, "World"))
        assertEquals(-1, NewStringUtility.indexOf("Hello", "world"))
        assertEquals(0, NewStringUtility.indexOf("Hello", ""))
    }

    @Test
    fun testLastIndexOf() {
        assertEquals(12, NewStringUtility.lastIndexOf("Hello World World", "World"))
        assertEquals(-1, NewStringUtility.lastIndexOf(null, "World"))
        assertEquals(-1, NewStringUtility.lastIndexOf("Hello", "world"))
        assertEquals(5, NewStringUtility.lastIndexOf("Hello", ""))
    }

    @Test
    fun testStartsWith() {
        assertTrue(NewStringUtility.startsWith("Hello World", "Hello"))
        assertFalse(NewStringUtility.startsWith(null, "Hello"))
        assertFalse(NewStringUtility.startsWith("World Hello", "Hello"))
        assertTrue(NewStringUtility.startsWith("Hello", ""))
    }

    @Test
    fun testEndsWith() {
        assertTrue(NewStringUtility.endsWith("Hello World", "World"))
        assertFalse(NewStringUtility.endsWith(null, "World"))
        assertFalse(NewStringUtility.endsWith("World Hello", "World"))
        assertTrue(NewStringUtility.endsWith("Hello", ""))
    }

    @Test
    fun testCharAt() {
        assertEquals('e', NewStringUtility.charAt("Hello", 1))
        assertThrows(StringIndexOutOfBoundsException::class.java) { NewStringUtility.charAt("Hello", 5) }
        assertThrows(StringIndexOutOfBoundsException::class.java) { NewStringUtility.charAt(null, 1) }
        assertThrows(StringIndexOutOfBoundsException::class.java) { NewStringUtility.charAt("", 0) }
    }

    @Test
    fun testLength() {
        assertEquals(5, NewStringUtility.length("Hello"))
        assertEquals(0, NewStringUtility.length(null))
        assertEquals(0, NewStringUtility.length(""))
        assertEquals(1, NewStringUtility.length(" "))
    }

    @Test
    fun testFormat() {
        assertEquals("Hello World", NewStringUtility.format("Hello %s", "World"))
        assertEquals("", NewStringUtility.format(null, "World"))
        assertEquals("Hello 123", NewStringUtility.format("Hello %d", 123))
        assertEquals("Hello %s", NewStringUtility.format("Hello %s"))
    }

    @Test
    fun testRemoveWhitespace() {
        assertEquals("Hello", NewStringUtility.removeWhitespace(" H e l l o "))
        assertEquals("", NewStringUtility.removeWhitespace(null))
        assertEquals("HelloWorld", NewStringUtility.removeWhitespace("Hello World"))
        assertEquals("", NewStringUtility.removeWhitespace(" "))
    }

    @Test
    fun testReplaceFirst() {
        assertEquals("Hello Kotlin World", NewStringUtility.replaceFirst("Hello World World", "World", "Kotlin"))
        assertEquals("", NewStringUtility.replaceFirst(null, "World", "Kotlin"))
        assertEquals("Hello World", NewStringUtility.replaceFirst("Hello World", "Java", "Kotlin"))
        assertEquals("Hello", NewStringUtility.replaceFirst("Hello", "", "Kotlin"))
    }

    @Test
    fun testReplaceLast() {
        assertEquals("Hello World Kotlin", NewStringUtility.replaceLast("Hello World World", "World", "Kotlin"))
        assertEquals("", NewStringUtility.replaceLast(null, "World", "Kotlin"))
        assertEquals("Hello World", NewStringUtility.replaceLast("Hello World", "Java", "Kotlin"))
        assertEquals("Hello", NewStringUtility.replaceLast("Hello", "", "Kotlin"))
    }

    @Test
    fun testTruncate() {
        assertEquals("Hello", NewStringUtility.truncate("Hello World", 5))
        assertEquals("", NewStringUtility.truncate(null, 5))
        assertEquals("Hello World", NewStringUtility.truncate("Hello World", 20))
        assertEquals("", NewStringUtility.truncate("", 5))
    }

    @Test
    fun testWrapText() {
        assertEquals("Hello\nWorld", NewStringUtility.wrapText("Hello World", 5))
        assertEquals("", NewStringUtility.wrapText(null, 5))
        assertEquals("Hello\nWorld\nExample", NewStringUtility.wrapText("Hello World Example", 6))
        assertEquals("Hello", NewStringUtility.wrapText("Hello", 10))
    }

    @Test
    fun testPerformance() {
        val largeString = "a".repeat(1000000)
        assertTimeout(Duration.ofMillis(100)) {
            NewStringUtility.reverse(largeString)
        }
        assertTimeout(Duration.ofMillis(100)) {
            NewStringUtility.toUpperCase(largeString)
        }
    }
}