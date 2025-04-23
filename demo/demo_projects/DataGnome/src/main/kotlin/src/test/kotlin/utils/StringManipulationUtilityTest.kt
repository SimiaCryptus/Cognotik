package utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StringManipulationUtilityTest {

    private val utility = StringManipulationUtility()

    @Test
    fun testTrim() {
        assertEquals("hello", utility.trim("  hello  "))
    }

    @Test
    fun testToUpperCase() {
        assertEquals("HELLO", utility.toUpperCase("hello"))
    }

    @Test
    fun testToLowerCase() {
        assertEquals("hello", utility.toLowerCase("HELLO"))
    }

    @Test
    fun testReverse() {
        assertEquals("olleh", utility.reverse("hello"))
    }

    @Test
    fun testIsPalindrome() {
        assertTrue(utility.isPalindrome("A man a plan a canal Panama"))
        assertFalse(utility.isPalindrome("hello"))
    }

    @Test
    fun testJoin() {
        assertEquals("a,b,c", utility.join(listOf("a", "b", "c"), ","))
    }

    @Test
    fun testSplit() {
        assertEquals(listOf("a", "b", "c"), utility.split("a,b,c", ","))
    }
}