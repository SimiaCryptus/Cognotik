package utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdvancedStringUtilsTest {

    @Test
    fun testInterpolate() {
        assertEquals("Hello, John!", AdvancedStringUtils.interpolate("Hello, {name}!", mapOf("name" to "John")))
        assertEquals("Value: 42", AdvancedStringUtils.interpolate("Value: {value}", mapOf("value" to 42)))
    }

    @Test
    fun testNormalize() {
        assertEquals("Cafe", AdvancedStringUtils.normalize("Café"))
        assertEquals("resume", AdvancedStringUtils.normalize("résumé"))
    }

    @Test
    fun testEncodeBase64() {
        assertEquals("aGVsbG8=", AdvancedStringUtils.encodeBase64("hello"))
        assertEquals("", AdvancedStringUtils.encodeBase64(""))
    }

    @Test
    fun testDecodeBase64() {
        assertEquals("hello", AdvancedStringUtils.decodeBase64("aGVsbG8="))
        assertEquals("", AdvancedStringUtils.decodeBase64(""))
    }

    @Test
    fun testLocaleCompare() {
        assertTrue(AdvancedStringUtils.localeCompare("straße", "strasse", "de") < 0)
        assertEquals(0, AdvancedStringUtils.localeCompare("resume", "résumé", "en"))
    }

    @Test
    fun testPatternMatch() {
        assertTrue(AdvancedStringUtils.patternMatch("hello123", "\\w+\\d+"))
        assertFalse(AdvancedStringUtils.patternMatch("hello", "\\d+"))
    }

    @Test
    fun testRemoveDiacritics() {
        assertEquals("Cafe", AdvancedStringUtils.removeDiacritics("Café"))
        assertEquals("resume", AdvancedStringUtils.removeDiacritics("résumé"))
    }

    @Test
    fun testToCamelCase() {
        assertEquals("helloWorld", AdvancedStringUtils.toCamelCase("Hello World"))
        assertEquals("helloWorldExample", AdvancedStringUtils.toCamelCase("hello world example"))
    }

    @Test
    fun testToPascalCase() {
        assertEquals("HelloWorld", AdvancedStringUtils.toPascalCase("hello world"))
        assertEquals("HelloWorldExample", AdvancedStringUtils.toPascalCase("hello world example"))
    }

    @Test
    fun testToTitleCase() {
        assertEquals("Hello World", AdvancedStringUtils.toTitleCase("hello world"))
        assertEquals("Hello World Example", AdvancedStringUtils.toTitleCase("hello world example"))
    }

    @Test
    fun testSlugify() {
        assertEquals("hello-world", AdvancedStringUtils.slugify("Hello World!"))
        assertEquals("hello-world-example", AdvancedStringUtils.slugify("Hello World Example"))
    }

    @Test
    fun testTruncateWithEllipsis() {
        assertEquals("Hello...", AdvancedStringUtils.truncateWithEllipsis("Hello World", 8))
        assertEquals("Hello", AdvancedStringUtils.truncateWithEllipsis("Hello", 10))
    }

    @Test
    fun testIsNumeric() {
        assertTrue(AdvancedStringUtils.isNumeric("12345"))
        assertFalse(AdvancedStringUtils.isNumeric("123a45"))
    }

    @Test
    fun testIsAlpha() {
        assertTrue(AdvancedStringUtils.isAlpha("Hello"))
        assertFalse(AdvancedStringUtils.isAlpha("Hello123"))
    }

    @Test
    fun testIsAlphanumeric() {
        assertTrue(AdvancedStringUtils.isAlphanumeric("Hello123"))
        assertFalse(AdvancedStringUtils.isAlphanumeric("Hello 123"))
    }

    @Test
    fun testCountOccurrences() {
        assertEquals(2, AdvancedStringUtils.countOccurrences("hello world hello", "hello"))
        assertEquals(0, AdvancedStringUtils.countOccurrences("hello world", "hi"))
    }

    @Test
    fun testRemoveSubstring() {
        assertEquals("hello world", AdvancedStringUtils.removeSubstring("hello world hello", "hello"))
        assertEquals("hello world", AdvancedStringUtils.removeSubstring("hello world", "hi"))
    }
}