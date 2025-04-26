package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.util.RuleTreeBuilder.escape
import com.simiacryptus.cognotik.util.RuleTreeBuilder.safeSubstring
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RuleTreeBuilderTest {

    @Test
    fun testEscape() {
        Assertions.assertEquals("\\$100", "$100".escape)
        Assertions.assertEquals("NoSpecialCharacters", "NoSpecialCharacters".escape)
        Assertions.assertEquals("\\$\\$", "$$".escape)
    }

    @Test
    fun testSafeSubstring() {
        val testString = "HelloWorld"
        Assertions.assertEquals("", testString.safeSubstring(-1, 5))
        Assertions.assertEquals("", testString.safeSubstring(0, 11))
        Assertions.assertEquals("", testString.safeSubstring(5, 5))
        Assertions.assertEquals("", testString.safeSubstring(0, null))
        Assertions.assertEquals("Hello", testString.safeSubstring(0, 5))
        Assertions.assertEquals("World", testString.safeSubstring(5, 10))
    }

//  @Test
//  fun testBestNextPrefix() {
//    val remainingItems = mutableSetOf("apple", "apricot", "banana")
//    val doNotMatch = sortedSetOf("appetizer", "application")
//    val bestNextPrefix = RuleTreeBuilder.bestPrefix(remainingItems.toSortedSet(), doNotMatch)
//    Assertions.assertNotNull(bestNextPrefix)
//    Assertions.assertEquals("ap", bestNextPrefix)
//  }

//  @Test
//  fun testBestNextSuffix() {
//    val remainingItems = mutableSetOf("apple", "apricot", "banana")
//    val doNotMatchReversed = sortedSetOf("reztinappa", "noitacilppa")
//    val sortedItems = remainingItems.toSortedSet()
//    val bestNextSuffix = RuleTreeBuilder.bestNextSuffix(remainingItems, doNotMatchReversed, sortedItems)
//    Assertions.assertNotNull(bestNextSuffix)
//    Assertions.assertEquals("e", bestNextSuffix?.first)
//  }

    @Test
    fun testPrefixExpand() {
        val allowedPrefixes = setOf("app", "ban")
        val expandedPrefixes = RuleTreeBuilder.prefixExpand(allowedPrefixes)
        Assertions.assertTrue(expandedPrefixes.containsAll(setOf("a", "ap", "app", "b", "ba", "ban")))
    }

    @Test
    fun testAllowedPrefixes() {
        val items = listOf("apple", "apricot")
        val doNotMatch = sortedSetOf("application", "appetizer")
        val allowedPrefixes = RuleTreeBuilder.allowedPrefixes(items, doNotMatch)
        Assertions.assertEquals(sortedSetOf("apple", "apr"), allowedPrefixes)
    }

    @Test
    fun testLongestCommonPrefix() {
        Assertions.assertNull(RuleTreeBuilder.longestCommonPrefix(null, "test"))
        Assertions.assertNull(RuleTreeBuilder.longestCommonPrefix("test", null))
        Assertions.assertEquals("", RuleTreeBuilder.longestCommonPrefix("", "test"))
        Assertions.assertEquals("te", RuleTreeBuilder.longestCommonPrefix("test", "teapot"))
        Assertions.assertEquals("", RuleTreeBuilder.longestCommonPrefix("test", "best"))
    }
}