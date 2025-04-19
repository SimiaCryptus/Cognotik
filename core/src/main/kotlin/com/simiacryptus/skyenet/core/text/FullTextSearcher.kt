package com.simiacryptus.skyenet.core.text

import org.slf4j.LoggerFactory

class FullTextSearcher(
  val text: String,
  val suffixes: SuffixArray = SuffixArray(text),
) {
  companion object {
    val log = LoggerFactory.getLogger(FullTextSearcher::class.java)
  }
  
  
  /**
   * Returns true if [pattern] occurs at least once in the text.
   */
  fun contains(pattern: String): Boolean = findFirst(pattern) != -1
  
  /**
   * Returns all starting indices where [pattern] appears (in ascending order).
   */
  fun findAll(pattern: String): List<Int> {
    log.debug("Finding all occurrences of pattern with length {}", pattern.length)
    val startTime = System.currentTimeMillis()
    val first = findFirst(pattern)
    if (first == -1) return emptyList()
    log.debug("First occurrence found at index {} in {}ms", first, System.currentTimeMillis() - startTime)
    val last = findLast(pattern)
    // extract matching suffix‐start indices and return them in ascending numeric order
    // slice out the matching range from the lex‐sorted suffix array,
    // then re‐sort numerically to satisfy "ascending order" in the API docs/tests
    val result = suffixes.suffixArray.slice(first..last).sorted()
    log.debug("Found {} occurrences in {}ms", result.size, System.currentTimeMillis() - startTime)
    if (log.isTraceEnabled && result.isNotEmpty()) {
      log.trace("Occurrences at positions: {}", result)
    }
    return result
  }
  
  /**
   * Returns how many times [pattern] occurs in the text.
   */
  fun countOccurrences(pattern: String): Int {
    log.debug("Counting occurrences of pattern with length {}", pattern.length)
    val startTime = System.currentTimeMillis()
    val first = findFirst(pattern)
    if (first == -1) return 0
    log.debug("First occurrence found at index {} in {}ms", first, System.currentTimeMillis() - startTime)
    val last = findLast(pattern)
    val count = last - first + 1
    log.debug("Found {} occurrences in {}ms", count, System.currentTimeMillis() - startTime)
    return count
  }
  
  private fun findFirst(pattern: String): Int {
    log.trace("Finding first occurrence of pattern with length {}", pattern.length)
    val startTime = System.currentTimeMillis()
    var low = 0
    var high = suffixes.suffixArray.lastIndex
    var result = -1
    var iterations = 0
    while (low <= high) {
      iterations++
      val mid = (low + high).ushr(1)
      val cmp = compareSuffixAt(mid, pattern)
      if (cmp >= 0) {
        if (cmp == 0) result = mid
        high = mid - 1
      } else {
        low = mid + 1
      }
    }
    log.trace(
      "First occurrence search completed in {}ms after {} iterations, result: {}",
      System.currentTimeMillis() - startTime, iterations, result
    )
    return result
  }
  
  private fun findLast(pattern: String): Int {
    log.trace("Finding last occurrence of pattern with length {}", pattern.length)
    val startTime = System.currentTimeMillis()
    var low = 0
    var high = suffixes.suffixArray.lastIndex
    var result = -1
    var iterations = 0
    while (low <= high) {
      iterations++
      val mid = (low + high).ushr(1)
      val cmp = compareSuffixAt(mid, pattern)
      if (cmp <= 0) {
        if (cmp == 0) result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    log.trace(
      "Last occurrence search completed in {}ms after {} iterations, result: {}",
      System.currentTimeMillis() - startTime, iterations, result
    )
    return result
  }
  
  /**
   * Lexicographically compares the suffix starting at suffixes[idx] with [pattern].
   * @return negative if suffix < pattern, zero if pattern is prefix, positive if suffix > pattern.
   */
  private fun compareSuffixAt(idx: Int, pattern: String): Int {
    if (log.isTraceEnabled) {
      log.trace("Comparing suffix at index {} (position {}) with pattern", idx, suffixes.suffixArray[idx])
    }
    val start = suffixes.suffixArray[idx]
    val textLen = text.length
    val minLen = minOf(textLen - start, pattern.length)
    for (i in 0 until minLen) {
      val diff = text[start + i] - pattern[i]
      if (log.isTraceEnabled) {
        log.trace("Comparison result: {} (different at position {})", diff, i)
      }
      if (diff != 0) return diff
    }
    // if suffix is shorter than pattern, it's “less”; otherwise if pattern matches as prefix, treat as equal
    val suffixLen = textLen - start
    val result = if (suffixLen < pattern.length) {
      suffixLen - pattern.length
    } else {
      0
    }
    if (log.isTraceEnabled) {
      if (suffixLen < pattern.length) {
        log.trace("Comparison result: {} (suffix shorter than pattern)", result)
      } else {
        log.trace("Comparison result: {} (pattern is prefix of suffix)", result)
      }
    }
    return result
  }
}