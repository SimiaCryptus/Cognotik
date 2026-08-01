package com.simiacryptus.cognotik.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StringDistanceTest {

  @Test
  fun `longest common substring`() {
    val match = StringDistance.longestCommonSubstring("the quick brown fox", "a quick brown dog")
    assertEquals(" quick brown ", "the quick brown fox".substring(match.aStart, match.aEnd))
    assertEquals(13, match.length)
    assertEquals(0, StringDistance.maxCommonSubstringLength("abc", "xyz"))
    assertEquals(0, StringDistance.maxCommonSubstringLength("", "xyz"))
  }

  @Test
  fun `suffix array agrees with dynamic programming`() {
    val a = "banana bandana"
    val b = "ananas in a bandanna"
    assertEquals(
      StringDistance.maxCommonSubstringLength(a, b),
      StringDistance.longestCommonSubstringViaSuffixArray(a, b).length
    )
  }

  @Test
  fun `edit distance`() {
    assertEquals(0, StringDistance.editDistance("kitten", "kitten"))
    assertEquals(3, StringDistance.editDistance("kitten", "sitting"))
    assertEquals(6, StringDistance.editDistance("", "kitten"))
    assertEquals(2, StringDistance.editDistance("flaw", "lawn"))
  }

  @Test
  fun `edit distance honours threshold`() {
    assertEquals(3, StringDistance.editDistance("kitten", "sitting", 5))
    assertEquals(3, StringDistance.editDistance("kitten", "sitting", 2)) // 2 + 1 => exceeded
    assertTrue(StringDistance.editDistance("kitten", "sitting", 2) > 2)
  }

  @Test
  fun `substring edit distance ignores head and tail`() {
    val text = "xxxxxx the quick brown fox yyyyyyy"
    assertEquals(0, StringDistance.substringEditDistance("quick brown", text))
    assertEquals(1, StringDistance.substringEditDistance("quick br0wn", text))
    val match = StringDistance.bestSubstringMatch("quick brown", text)
    assertEquals("quick brown", text.substring(match.start, match.end))
  }

  @Test
  fun `best matching substring is a flyweight view`() {
    val text = "prefix----needle in haystack----suffix"
    val view = StringDistance.bestMatchingSubstring("needle in haystack", text)
    assertTrue(view is FlyweightCharSequence)
    assertEquals("needle in haystack", view.toString())
  }

  @Test
  fun `normalized metrics`() {
    assertEquals(1.0, StringDistance.similarity("abc", "abc"))
    assertEquals(0.0, StringDistance.normalizedSubstringEditDistance("abc", "zzzabczzz"))
  }
}