package utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StringUtilsTest {

    @Test
    fun testTrim() {
        assertEquals("Hello World", StringUtils.trim("  Hello World  "))
        assertEquals("hello", StringUtils.trim("hello"))
        assertEquals("", StringUtils.trim(""))
        assertEquals("", StringUtils.trim(null))
    }

    @Test
    fun testSplit() {
        assertEquals(listOf("a", "b", "c"), StringUtils.split("a,b,c", ","))
        assertEquals(listOf("abc"), StringUtils.split("abc", ","))
        assertEquals(emptyList<String>(), StringUtils.split("", ","))
        assertEquals(emptyList<String>(), StringUtils.split(null, ","))
    }

    @Test
    fun testJoin() {
        assertEquals("a,b,c", StringUtils.join(listOf("a", "b", "c"), ","))
        assertEquals("", StringUtils.join(emptyList(), ","))
        assertEquals("a", StringUtils.join(listOf("a"), ","))
        assertEquals("", StringUtils.join(null, ","))
    }

    @Test
    fun testToUpperCase() {
        assertEquals("HELLO", StringUtils.toUpperCase("hello"))
        assertEquals("", StringUtils.toUpperCase(""))
        assertEquals("HELLO", StringUtils.toUpperCase("HELLO"))
        assertEquals("", StringUtils.toUpperCase(null))
    }

    @Test
    fun testToLowerCase() {
        assertEquals("hello", StringUtils.toLowerCase("HELLO"))
        assertEquals("", StringUtils.toLowerCase(""))
        assertEquals("hello", StringUtils.toLowerCase("hello"))
        assertEquals("", StringUtils.toLowerCase(null))
    }

    @Test
    fun testCapitalizeFirst() {
        assertEquals("Hello world", StringUtils.capitalizeFirst("hello world"))
        assertEquals("Hello", StringUtils.capitalizeFirst("Hello"))
        assertEquals("", StringUtils.capitalizeFirst(""))
        assertEquals("", StringUtils.capitalizeFirst(null))
    }

    @Test
    fun testToKebabCase() {
        assertEquals("hello-world-example", StringUtils.toKebabCase("HelloWorld Example"))
        assertEquals("hello-world", StringUtils.toKebabCase("helloWorld"))
        assertEquals("", StringUtils.toKebabCase(""))
        assertEquals("", StringUtils.toKebabCase(null))
    }

    @Test
    fun testToSnakeCase() {
        assertEquals("hello_world_example", StringUtils.toSnakeCase("HelloWorld Example"))
        assertEquals("hello_world", StringUtils.toSnakeCase("helloWorld"))
        assertEquals("", StringUtils.toSnakeCase(""))
        assertEquals("", StringUtils.toSnakeCase(null))
    }

    @Test
    fun testRepeat() {
        assertEquals("abcabcabc", StringUtils.repeat("abc", 3))
        assertEquals("", StringUtils.repeat("a", 0))
        assertEquals("", StringUtils.repeat(null, 3))
        assertThrows(IllegalArgumentException::class.java) {
            StringUtils.repeat("a", -1)
        }
    }

    @Test
    fun testContains() {
        assertTrue(StringUtils.contains("Hello World", "World"))
        assertFalse(StringUtils.contains("Hello World", "world"))
        assertFalse(StringUtils.contains(null, "World"))
    }

    @Test
    fun testPad() {
        assertEquals("**Hello***", StringUtils.pad("Hello", 10, "*"))
        assertEquals("hello", StringUtils.pad("hello", 5))
        assertEquals("", StringUtils.pad(null, 10, "*"))
    }

    @Test
    fun testReverse() {
        assertEquals("olleH", StringUtils.reverse("Hello"))
        assertEquals("", StringUtils.reverse(""))
        assertEquals("", StringUtils.reverse(null))
    }

    @Test
    fun testIsPalindrome() {
        assertTrue(StringUtils.isPalindrome("madam"))
        assertFalse(StringUtils.isPalindrome("hello"))
        assertFalse(StringUtils.isPalindrome(null))
    }

    @Test
    fun testCapitalizeWords() {
        assertEquals("Hello World", StringUtils.capitalizeWords("hello world"))
        assertEquals("", StringUtils.capitalizeWords(""))
        assertEquals("", StringUtils.capitalizeWords(null))
    }

    @Test
    fun testTrimStart() {
        assertEquals("Hello", StringUtils.trimStart("  Hello"))
        assertEquals("hello", StringUtils.trimStart("hello"))
        assertEquals("", StringUtils.trimStart(""))
        assertEquals("", StringUtils.trimStart(null))
    }

    @Test
    fun testTrimEnd() {
        assertEquals("Hello", StringUtils.trimEnd("Hello  "))
        assertEquals("hello", StringUtils.trimEnd("hello"))
        assertEquals("", StringUtils.trimEnd(""))
        assertEquals("", StringUtils.trimEnd(null))
    }

    @Test
    fun testReplace() {
        assertEquals("hxllo", StringUtils.replace("hello", "e", "x"))
        assertEquals("hello", StringUtils.replace("hello", "z", "x"))
        assertEquals("", StringUtils.replace(null, "World", "Kotlin"))
    }

    @Test
    fun testSubstring() {
        assertEquals("Hello", StringUtils.substring("Hello World", 0, 5))
        assertEquals("", StringUtils.substring(null, 0, 5))
        assertEquals("", StringUtils.substring(null, 1, 4))
    }

    @Test
    fun testIndexOf() {
        assertEquals(6, StringUtils.indexOf("Hello World", "World"))
        assertEquals(-1, StringUtils.indexOf(null, "World"))
        assertEquals(-1, StringUtils.indexOf(null, "z"))
    }

    @Test
    fun testLastIndexOf() {
        assertEquals(1, StringUtils.lastIndexOf("hello", "e"))
        assertEquals(-1, StringUtils.lastIndexOf("hello", "z"))
        assertEquals(-1, StringUtils.lastIndexOf(null, "World"))
    }

    @Test
    fun testStartsWith() {
        assertTrue(StringUtils.startsWith("Hello World", "Hello"))
        assertFalse(StringUtils.startsWith(null, "Hello"))
        assertFalse(StringUtils.startsWith(null, "he"))
    }

    @Test
    fun testEndsWith() {
        assertTrue(StringUtils.endsWith("Hello World", "World"))
        assertFalse(StringUtils.endsWith(null, "World"))
        assertFalse(StringUtils.endsWith(null, "lo"))
    }

    @Test
    fun testCharAt() {
        assertEquals('e', StringUtils.charAt("Hello", 1))
        assertThrows(StringIndexOutOfBoundsException::class.java) {
            StringUtils.charAt("hello", 5)
        }
        assertThrows(StringIndexOutOfBoundsException::class.java) {
            StringUtils.charAt(null, 0)
        }
    }

    @Test
    fun testLength() {
        assertEquals(5, StringUtils.length("Hello"))
        assertEquals(0, StringUtils.length(""))
        assertEquals(0, StringUtils.length(null))
    }

    @Test
    fun testFormat() {
        assertEquals("Hello World", StringUtils.format("Hello %s", "World"))
        assertEquals("Number: 42", StringUtils.format("Number: %d", 42))
        assertEquals("", StringUtils.format(null, "World"))
    }

    @Test
    fun testRemoveWhitespace() {
        assertEquals("Hello", StringUtils.removeWhitespace(" H e l l o "))
        assertEquals("helloworld", StringUtils.removeWhitespace("  hello  world  "))
        assertEquals("", StringUtils.removeWhitespace("   "))
        assertEquals("", StringUtils.removeWhitespace(null))
    }

    @Test
    fun testReplaceFirst() {
        assertEquals("hxllo", StringUtils.replaceFirst("hello", "e", "x"))
        assertEquals("hello", StringUtils.replaceFirst("hello", "z", "x"))
        assertEquals("", StringUtils.replaceFirst(null, "World", "Kotlin"))
    }

    @Test
    fun testReplaceLast() {
        assertEquals("hellx", StringUtils.replaceLast("hello", "o", "x"))
        assertEquals("hello", StringUtils.replaceLast("hello", "z", "x"))
        assertEquals("", StringUtils.replaceLast(null, "World", "Kotlin"))
    }

    @Test
    fun testTruncate() {
        assertEquals("Hello", StringUtils.truncate("Hello World", 5))
        assertEquals("hello", StringUtils.truncate("hello", 10))
        assertEquals("", StringUtils.truncate("", 5))
        assertEquals("", StringUtils.truncate(null, 5))
    }

    @Test
    fun testWrapText() {
        assertEquals("Hello\nWorld", StringUtils.wrapText("Hello World", 5))
        assertEquals("hello", StringUtils.wrapText("hello", 10))
        assertEquals("", StringUtils.wrapText("", 5))
        assertEquals("", StringUtils.wrapText(null, 5))
    }
}