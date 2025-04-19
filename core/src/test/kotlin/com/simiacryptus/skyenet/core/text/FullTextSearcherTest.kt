// src/test/kotlin/fulltextsearch/FullTextSearcherTest.kt

package com.simiacryptus.skyenet.core.text

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Unit tests for the fulltextsearch package:
 * - FlyweightCharSequence: slicing, indexing and toString functionality.
 * - SuffixArray: correct construction of the lexicographically sorted suffix index.
 * - FullTextSearcher: contains(), findAll(), and countOccurrences() under normal and edge conditions.
 */
class FullTextSearcherTest {
  private val logger = LoggerFactory.getLogger(FullTextSearcherTest::class.java)
  
  @BeforeEach
  fun setUp() {
    logger.info("Setting up test case")
  }
  
  @Test
  fun testFlyweightCharSequenceBasicOperations() {
    logger.debug("Starting FlyweightCharSequence basic operations test")
    val source = "Hello, World!"
    // Take the "World!" part (6 letters)
    val seq = FlyweightCharSequence(source, offset = 7, size = 6)
    logger.debug("Created FlyweightCharSequence with source='{}', offset={}", source, 7)
    
    // length, indexing and toString (should include the '!' at the end)
    assertEquals(6, seq.length, "Length should match the provided length")
    logger.debug("Verified sequence length: {}", seq.length)
    assertEquals('W', seq[0], "First character should be 'W'")
    logger.debug("Verified first character: {}", seq[0])
    assertEquals('d', seq[4], "Last character should be 'd'")
    logger.debug("Verified last character: {}", seq[4])
    assertEquals("World!", seq.toString(), "toString() should reproduce the substring")
    logger.debug("Verified toString() result: {}", seq.toString())
    
    // subSequence returns a FlyweightCharSequence and correct content
    val sub = seq.subSequence(1, 4)
    logger.debug("Created subsequence from {} to {}: {}", 1, 4, sub)
    assertTrue(sub is FlyweightCharSequence, "subSequence should return a FlyweightCharSequence")
    assertEquals("orl", sub.toString(), "subSequence toString should match expected substring")
    logger.info("FlyweightCharSequence basic operations test completed successfully")
  }
  
  @Test
  fun testSuffixArrayConstruction() {
    logger.debug("Starting SuffixArray construction test")
    val text = "banana"
    logger.debug("Test text: {}", text)
    val sa = SuffixArray(text)
    logger.debug("Created SuffixArray for text '{}'", text)
    // Expected sorted order of suffixes:
    // a(5), ana(3), anana(1), banana(0), na(4), nana(2)
    val expected = intArrayOf(5, 3, 1, 0, 4, 2)
    logger.debug("Expected suffix array: {}", expected.contentToString())
    logger.debug("Actual suffix array: {}", sa.suffixArray.contentToString())
    assertArrayEquals(expected, sa.suffixArray, "Suffix array should sort suffix start positions lexicographically")
    logger.info("SuffixArray construction test completed successfully")
  }
  
  @Test
  fun testFullTextSearcherBasicPatterns() {
    logger.debug("Starting FullTextSearcher basic patterns test")
    val searcher = FullTextSearcher("banana")
    logger.debug("Created FullTextSearcher with text: 'banana'")
    
    // contains()
    logger.debug("Testing contains() with pattern 'ana'")
    assertTrue(searcher.contains("ana"), "'ana' should be found in 'banana'")
    
    // findAll() and countOccurrences()
    logger.debug("Testing findAll() with pattern 'ana'")
    assertEquals(listOf(1, 3), searcher.findAll("ana"), "findAll should return starting indices [1,3]")
    logger.debug("Testing countOccurrences() with pattern 'ana'")
    assertEquals(2, searcher.countOccurrences("ana"), "There are 2 occurrences of 'ana'")
    logger.debug("Testing findAll() with pattern 'banana'")
    assertEquals(listOf(0), searcher.findAll("banana"), "Searching the full string returns [0]")
    logger.debug("Testing countOccurrences() with pattern 'banana'")
    assertEquals(1, searcher.countOccurrences("banana"), "Full-string search count should be 1")
    logger.info("FullTextSearcher basic patterns test completed successfully")
  }
  
  @Test
  fun testFullTextSearcherEdgeCases() {
    logger.debug("Starting FullTextSearcher edge cases test")
    // Pattern not in text
    val s1 = FullTextSearcher("apple")
    logger.debug("Created FullTextSearcher with text: 'apple'")
    logger.debug("Testing pattern not in text: 'banana'")
    assertFalse(s1.contains("banana"))
    assertTrue(s1.findAll("banana").isEmpty(), "Non-existent pattern should yield empty list")
    assertEquals(0, s1.countOccurrences("banana"))
    
    // Pattern longer than text
    val s2 = FullTextSearcher("hi")
    logger.debug("Created FullTextSearcher with text: 'hi'")
    logger.debug("Testing pattern longer than text: 'hello'")
    assertFalse(s2.contains("hello"))
    assertTrue(s2.findAll("hello").isEmpty())
    assertEquals(0, s2.countOccurrences("hello"))
    
    // Single-character pattern
    val text = "abracadabra"
    logger.debug("Created FullTextSearcher with text: '{}'", text)
    val s3 = FullTextSearcher(text)
    logger.debug("Testing single-character pattern: 'a'")
    val expectedA = listOf(0, 3, 5, 7, 10)
    logger.debug("Expected positions: {}", expectedA)
    assertEquals(expectedA, s3.findAll("a"), "All positions of 'a' should be found")
    assertEquals(expectedA.size, s3.countOccurrences("a"))
    logger.info("FullTextSearcher edge cases test completed successfully")
  }
  
  @Test
  fun testEmptyText() {
    logger.debug("Starting empty text test")
    val emptySearcher = FullTextSearcher("")
    logger.debug("Created FullTextSearcher with empty text")
    logger.debug("Testing contains() with pattern 'a'")
    assertFalse(emptySearcher.contains("a"), "No substring can be found in empty text")
    logger.debug("Testing findAll() with pattern 'a'")
    assertTrue(emptySearcher.findAll("a").isEmpty())
    logger.debug("Testing countOccurrences() with pattern 'a'")
    assertEquals(0, emptySearcher.countOccurrences("a"))
    logger.info("Empty text test completed successfully")
  }
}