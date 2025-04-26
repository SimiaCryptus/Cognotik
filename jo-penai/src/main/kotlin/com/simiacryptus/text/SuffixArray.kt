package com.simiacryptus.text

import org.slf4j.Logger
import org.slf4j.LoggerFactory


/**
 * Builds a suffix array from the provided text.
 * Uses FlyweightCharSequence for efficient substring comparisons.
 */
class SuffixArray(private val text: String) {
    // Logger for this class
    private val log: Logger = LoggerFactory.getLogger(SuffixArray::class.java)

    // Underlying array of suffix start positions
    val suffixArray: IntArray = IntArray(text.length) { it }

    /** LCP[i] = longest common prefix of suffixes at suffixArray[i] and suffixArray[i+1] */
    val lcpArray: IntArray by lazy { buildLCP() }

    init {
        log.debug("Initializing SuffixArray with text of length {}", text.length)

        // Sort suffix indices lexicographically
        log.debug("Starting suffix array sorting")
        val startTime = System.currentTimeMillis()

        // Sort suffix indices lexicographically and write back into the array
        val sortedList = suffixArray.toMutableList().apply {
            sortWith { i, j ->
                val a = FlyweightCharSequence(text, i, text.length - i)
                val b = FlyweightCharSequence(text, j, text.length - j)
                compareSequences(a, b)
            }
        }
        sortedList.forEachIndexed { idx, suffix ->
            suffixArray[idx] = suffix
        }
        val endTime = System.currentTimeMillis()
        log.debug("Suffix array sorting completed in {} ms", endTime - startTime)
        if (log.isTraceEnabled) {
            log.trace("First 10 suffixes (or fewer if text is shorter):")
            val limit = minOf(10, text.length)
            for (i in 0 until limit) {
                val suffixStart = suffixArray[i]
                val suffixPreview = text.substring(suffixStart, minOf(suffixStart + 20, text.length))
                log.trace("  {}: {} (starting at index {})", i, suffixPreview, suffixStart)
            }
        }
    }

    /**
     * Build the LCP array in O(n) using Kasai’s algorithm.
     */
    private fun buildLCP(): IntArray {
        val n = text.length
        val lcp = IntArray(maxOf(0, n - 1))
        val rank = IntArray(n)
        // rank[sa[i]] = i
        for (i in 0 until n) rank[suffixArray[i]] = i
        var h = 0
        for (i in 0 until n) {
            val r = rank[i]
            if (r == n - 1) {
                h = 0
                continue
            }
            val j = suffixArray[r + 1]
            // Compare the two suffixes starting at i and j
            while (i + h < n && j + h < n && text[i + h] == text[j + h]) {
                h++
            }
            lcp[r] = h
            if (h > 0) h--
        }
        return lcp
    }

    /**
     * Returns a copy of the sorted suffix indices.
     */
    fun getArray(): IntArray = suffixArray.copyOf()

    /**
     * Compare two CharSequences lexicographically.
     */
    private fun compareSequences(a: CharSequence, b: CharSequence): Int {
        if (log.isTraceEnabled) {
            val aPreview = if (a.length <= 10) a.toString() else a.subSequence(0, 10).toString() + "..."
            val bPreview = if (b.length <= 10) b.toString() else b.subSequence(0, 10).toString() + "..."
            log.trace("Comparing sequences: '{}' and '{}'", aPreview, bPreview)
        }

        val minLen = minOf(a.length, b.length)
        for (k in 0 until minLen) {
            val diff = a[k].compareTo(b[k])
            if (log.isTraceEnabled) {
                log.trace(
                    "Difference found at position {}: '{}' vs '{}', returning {}",
                    k, a[k], b[k], diff
                )
            }
            if (diff != 0) return diff
        }
        val result = a.length - b.length
        if (log.isTraceEnabled && result != 0) {
            log.trace("Sequences match up to position {}, length difference: {}", minLen, result)
        }
        return a.length - b.length
    }
}