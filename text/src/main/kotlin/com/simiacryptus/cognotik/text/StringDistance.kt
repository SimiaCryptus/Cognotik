package com.simiacryptus.cognotik.text

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Collection of string distance / similarity metrics.
 *
 * Provided metrics:
 *  1. [maxCommonSubstringLength] / [longestCommonSubstring] - length (and location) of the
 *     longest contiguous block of characters shared by two sequences.
 *  2. [editDistance] - classic Levenshtein distance (insert / delete / substitute, unit cost),
 *     with an optional early-exit threshold.
 *  3. [substringEditDistance] / [bestSubstringMatch] - "fuzzy contains": the minimum edit
 *     distance between the shorter sequence and *any* substring of the longer one, i.e. the
 *     extraneous head/tail of the longer input is free.
 *
 * All algorithms operate on [CharSequence] (so [FlyweightCharSequence] slices can be compared
 * without copying) and use O(min(n, m)) auxiliary memory.
 */
object StringDistance {

  private val log: Logger = LoggerFactory.getLogger(StringDistance::class.java)

  /** Location/length of a common substring; [aStart]/[bStart] are indices into the two inputs. */
  data class Match(
    val length: Int,
    val aStart: Int,
    val bStart: Int,
  ) {
    val aEnd: Int get() = aStart + length
    val bEnd: Int get() = bStart + length
    val isEmpty: Boolean get() = length == 0
  }

  /**
   * Result of an approximate substring search:
   * the window `text[start, end)` is the closest match to the pattern, at cost [distance].
   */
  data class SubstringMatch(
    val distance: Int,
    val start: Int,
    val end: Int,
  ) {
    val length: Int get() = end - start
  }

  // ---------------------------------------------------------------------------------------
  // 1) Maximum common substring
  // ---------------------------------------------------------------------------------------

  /**
   * @return the length of the longest contiguous subsequence present in both [a] and [b].
   */
  fun maxCommonSubstringLength(a: CharSequence, b: CharSequence): Int =
    longestCommonSubstring(a, b).length

  /**
   * Dynamic-programming longest-common-substring. O(n*m) time, O(min(n,m)) memory.
   *
   * @return a [Match] describing the longest shared block (length 0 if there is none).
   */
  fun longestCommonSubstring(a: CharSequence, b: CharSequence): Match {
    if (a.isEmpty() || b.isEmpty()) return Match(0, 0, 0)
    val startTime = System.currentTimeMillis()

    // Keep the shorter sequence on the inner (memory-bound) axis.
    val swapped = b.length > a.length
    val outer = if (swapped) b else a
    val inner = if (swapped) a else b

    val width = inner.length + 1
    var prev = IntArray(width)
    var cur = IntArray(width)
    var best = 0
    var bestOuter = 0
    var bestInner = 0

    for (i in 1..outer.length) {
      val ci = outer[i - 1]
      for (j in 1..inner.length) {
        cur[j] = if (ci == inner[j - 1]) prev[j - 1] + 1 else 0
        if (cur[j] > best) {
          best = cur[j]
          bestOuter = i - best
          bestInner = j - best
        }
      }
      val tmp = prev
      prev = cur
      cur = tmp
      java.util.Arrays.fill(cur, 0)
    }
    if (log.isDebugEnabled) {
      log.debug(
        "longestCommonSubstring({}, {}) = {} in {}ms",
        a.length, b.length, best, System.currentTimeMillis() - startTime
      )
    }
    return if (swapped) Match(best, bestInner, bestOuter) else Match(best, bestOuter, bestInner)
  }

  /**
   * Convenience accessor returning the shared block itself as a zero-copy
   * [FlyweightCharSequence] over [a].
   */
  fun longestCommonSubstringText(a: String, b: CharSequence): CharSequence {
    val match = longestCommonSubstring(a, b)
    return FlyweightCharSequence(a, match.aStart, match.length)
  }

  /**
   * Suffix-array based longest common substring; preferable when the inputs are large enough
   * that the O(n*m) table would be prohibitive.
   *
   * Builds the generalized suffix array of `a + separator + b` and scans the LCP array for
   * adjacent suffixes originating from different inputs.
   */
  fun longestCommonSubstringViaSuffixArray(a: CharSequence, b: CharSequence): Match {
    if (a.isEmpty() || b.isEmpty()) return Match(0, 0, 0)
    val separator = pickSeparator(a, b)
    val aLen = a.length
    val combined = StringBuilder(aLen + b.length + 1)
      .append(a).append(separator).append(b).toString()

    val suffixArray = SuffixArray(combined)
    val sa = suffixArray.suffixArray
    val lcp = suffixArray.lcpArray

    var best = 0
    var bestA = 0
    var bestB = 0
    for (i in 0 until sa.size - 1) {
      val p = sa[i]
      val q = sa[i + 1]
      if (p == aLen || q == aLen) continue // the separator suffix itself
      val pInA = p < aLen
      val qInA = q < aLen
      if (pInA == qInA) continue // both from the same input
      val len = lcp[i]
      if (len > best) {
        best = len
        if (pInA) {
          bestA = p; bestB = q - aLen - 1
        } else {
          bestA = q; bestB = p - aLen - 1
        }
      }
    }
    return Match(best, bestA, bestB)
  }

  private fun pickSeparator(vararg sequences: CharSequence): Char {
    var candidate = '\u0001'
    while (sequences.any { seq -> (0 until seq.length).any { seq[it] == candidate } }) {
      candidate++
      require(candidate != '\uFFFF') { "Unable to find an unused separator character" }
    }
    return candidate
  }

  // ---------------------------------------------------------------------------------------
  // 2) Edit (Levenshtein) distance
  // ---------------------------------------------------------------------------------------

  /**
   * Levenshtein distance: minimum number of single character insertions, deletions and
   * substitutions required to turn [a] into [b]. O(n*m) time, O(min(n,m)) memory.
   */
  fun editDistance(a: CharSequence, b: CharSequence): Int =
    editDistance(a, b, Int.MAX_VALUE)

  /**
   * Levenshtein distance with an early-exit threshold.
   *
   * @param maxDistance abandon the computation once every alignment is known to cost more than
   *        this; in that case `maxDistance + 1` is returned.
   */
  fun editDistance(a: CharSequence, b: CharSequence, maxDistance: Int): Int {
    require(maxDistance >= 0) { "maxDistance must be non-negative: $maxDistance" }
    if (a === b) return 0
    if (a.isEmpty()) return capped(b.length, maxDistance)
    if (b.isEmpty()) return capped(a.length, maxDistance)
    if (abs(a.length - b.length) > maxDistance) return maxDistance + 1

    // Keep the shorter sequence on the inner axis.
    val outer = if (b.length > a.length) b else a
    val inner = if (b.length > a.length) a else b

    var prev = IntArray(inner.length + 1) { it }
    var cur = IntArray(inner.length + 1)

    for (i in 1..outer.length) {
      cur[0] = i
      val ci = outer[i - 1]
      var rowMin = cur[0]
      for (j in 1..inner.length) {
        val cost = if (ci == inner[j - 1]) 0 else 1
        var value = prev[j - 1] + cost
        val deletion = prev[j] + 1
        if (deletion < value) value = deletion
        val insertion = cur[j - 1] + 1
        if (insertion < value) value = insertion
        cur[j] = value
        if (value < rowMin) rowMin = value
      }
      if (rowMin > maxDistance) return maxDistance + 1
      val tmp = prev
      prev = cur
      cur = tmp
    }
    return capped(prev[inner.length], maxDistance)
  }

  private fun capped(value: Int, maxDistance: Int) =
    if (value > maxDistance) maxDistance + 1 else value

  /** Edit distance scaled to `[0,1]` by the length of the longer input. */
  fun normalizedEditDistance(a: CharSequence, b: CharSequence): Double {
    val longest = max(a.length, b.length)
    if (longest == 0) return 0.0
    return editDistance(a, b).toDouble() / longest
  }

  /** `1.0 - normalizedEditDistance`; 1.0 means identical. */
  fun similarity(a: CharSequence, b: CharSequence): Double = 1.0 - normalizedEditDistance(a, b)

  // ---------------------------------------------------------------------------------------
  // 3) Substring edit distance (free head/tail)
  // ---------------------------------------------------------------------------------------

  /**
   * Symmetric convenience wrapper: the shorter input is treated as the pattern and matched
   * against the best window of the longer input, so a leading/trailing mismatch in the longer
   * input costs nothing.
   */
  fun substringEditDistance(a: CharSequence, b: CharSequence): Int {
    val pattern = if (a.length <= b.length) a else b
    val text = if (a.length <= b.length) b else a
    return bestSubstringMatch(pattern, text).distance
  }

  /**
   * Sellers' algorithm ("approximate substring matching"): distance between [pattern] and the
   * closest substring of [text]. Deleting the text prefix and suffix is free; everything inside
   * the matched window uses standard unit-cost edits.
   *
   * @return the cost together with the `[start, end)` window of [text] that achieved it.
   */
  fun bestSubstringMatch(pattern: CharSequence, text: CharSequence): SubstringMatch {
    val m = pattern.length
    val n = text.length
    if (m == 0) return SubstringMatch(0, 0, 0)
    if (n == 0) return SubstringMatch(m, 0, 0)

    // dp[j] = cost of aligning pattern[0,i) with some window of text ending at j
    var prev = IntArray(n + 1)                 // row 0: empty pattern matches anywhere for free
    var prevStart = IntArray(n + 1) { it }     // window start that produced prev[j]
    var cur = IntArray(n + 1)
    var curStart = IntArray(n + 1)

    for (i in 1..m) {
      cur[0] = i
      curStart[0] = 0
      val pc = pattern[i - 1]
      for (j in 1..n) {
        val cost = if (pc == text[j - 1]) 0 else 1
        var best = prev[j - 1] + cost          // match / substitute
        var bestStart = prevStart[j - 1]
        val skipPattern = prev[j] + 1          // delete a pattern character
        if (skipPattern < best) {
          best = skipPattern
          bestStart = prevStart[j]
        }
        val skipText = cur[j - 1] + 1          // insert a text character inside the window
        if (skipText < best) {
          best = skipText
          bestStart = curStart[j - 1]
        }
        cur[j] = best
        curStart[j] = bestStart
      }
      var tmp = prev; prev = cur; cur = tmp
      tmp = prevStart; prevStart = curStart; curStart = tmp
    }

    var bestEnd = 0
    var bestDistance = prev[0]
    for (j in 1..n) {
      val d = prev[j]
      if (d < bestDistance || (d == bestDistance && (j - prevStart[j]) < (bestEnd - prevStart[bestEnd]))) {
        bestDistance = d
        bestEnd = j
      }
    }
    val start = prevStart[bestEnd]
    if (log.isTraceEnabled) {
      log.trace("bestSubstringMatch: distance={} window=[{},{})", bestDistance, start, bestEnd)
    }
    return SubstringMatch(bestDistance, start, bestEnd)
  }

  /**
   * Zero-copy view of the window of [text] that best matches [pattern].
   */
  fun bestMatchingSubstring(pattern: CharSequence, text: String): CharSequence {
    val match = bestSubstringMatch(pattern, text)
    return FlyweightCharSequence(text, match.start, match.length)
  }

  /**
   * Substring edit distance scaled by the pattern length; 0.0 == exact occurrence,
   * 1.0 == nothing usable was found.
   */
  fun normalizedSubstringEditDistance(pattern: CharSequence, text: CharSequence): Double {
    if (pattern.isEmpty()) return 0.0
    return min(1.0, bestSubstringMatch(pattern, text).distance.toDouble() / pattern.length)
  }

  /**
   * Fuzzy `contains`: true when [pattern] occurs in [text] with at most [maxDistance] edits.
   * Exact occurrences are short-circuited through [FullTextSearcher] when a prebuilt
   * [searcher] is supplied.
   */
  fun containsApproximately(
    pattern: String,
    text: String,
    maxDistance: Int,
    searcher: FullTextSearcher? = null,
  ): Boolean {
    if (pattern.isEmpty()) return true
    if (searcher != null && searcher.contains(pattern)) return true
    return bestSubstringMatch(pattern, text).distance <= maxDistance
  }
}

/** Extension sugar. */
fun CharSequence.maxCommonSubstringLength(other: CharSequence): Int =
  StringDistance.maxCommonSubstringLength(this, other)

fun CharSequence.editDistance(other: CharSequence): Int =
  StringDistance.editDistance(this, other)

fun CharSequence.substringEditDistance(other: CharSequence): Int =
  StringDistance.substringEditDistance(this, other)