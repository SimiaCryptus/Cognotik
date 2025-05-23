package com.simiacryptus.text

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Identifies and abbreviates repeated subsequences in text while preserving uniqueness.
 */
class TextCompressor(
    val minLength: Int = 20,
    val minOccurrences: Int = 2,
) {
    private val log: Logger = LoggerFactory.getLogger(TextCompressor::class.java)

    /**
     * Compresses text by abbreviating repeated subsequences.
     *
     * @param text The input text to compress
     * @param minOccurrences Minimum number of occurrences required to abbreviate (default: 2)
     * @return Compressed text with abbreviated repeated subsequences
     */
    fun compress(text: String): String {
        log.debug(
            "Starting compression of text with length {}, minLength={}, minOccurrences={}",
            text.length, minLength, minOccurrences
        )
        val startTime = System.currentTimeMillis()
        if (text.length < minLength * 2) {
            log.debug("Text too short for compression (length={}), returning original", text.length)
            return text
        }

        val suffixArray = SuffixArray(text)
        log.debug("Suffix array created in {}ms", System.currentTimeMillis() - startTime)
        val searcher = FullTextSearcher(text, suffixArray)
        log.debug("FullTextSearcher initialized (reusing suffix array) in {}ms", System.currentTimeMillis() - startTime)
        val candidateStartTime = System.currentTimeMillis()
        val candidates = findRepeatingSubsequences(text, searcher, minOccurrences, suffixArray)
        log.debug(
            "Found {} candidate subsequences in {}ms",
            candidates.size, System.currentTimeMillis() - candidateStartTime
        )

        val sortedCandidates = candidates.sortedWith(
            compareByDescending<Pair<String, List<Int>>> { it.first.length }
                .thenByDescending { it.second.size }
        )
        log.debug("Sorted {} candidates by length and frequency", sortedCandidates.size)
        if (log.isTraceEnabled) {
            sortedCandidates.take(5).forEach { (pattern, occurrences) ->
                log.trace(
                    "Top candidate: '{}' (length={}, occurrences={})",
                    pattern.take(20) + (if (pattern.length > 20) "..." else ""),
                    pattern.length, occurrences.size
                )
            }
        }

        val abbreviationStartTime = System.currentTimeMillis()
        val result = applyAbbreviations(text, sortedCandidates)
        val compressionRatio = if (text.isNotEmpty()) (result.length.toDouble() / text.length) * 100 else 100.0
        log.debug(
            "Applied abbreviations in {}ms. Original length: {}, Compressed length: {}, Ratio: {:.2f}%",
            System.currentTimeMillis() - abbreviationStartTime, text.length, result.length, compressionRatio
        )
        log.debug("Compression completed in {}ms", System.currentTimeMillis() - startTime)

        return result
    }

    /**
     * Finds repeating subsequences in the text.
     */
    private fun findRepeatingSubsequences(
        text: String,
        searcher: FullTextSearcher,
        minOccurrences: Int,
        suffixArray: SuffixArray
    ): List<Pair<String, List<Int>>> {
        log.debug("Finding repeating subsequences with minLength={}, minOccurrences={}", minLength, minOccurrences)
        val startTime = System.currentTimeMillis()
        val suffixes = suffixArray.getArray()

        val lcp = suffixArray.lcpArray
        val candidates = mutableListOf<Pair<String, List<Int>>>()

        var comparisonCount = 0
        var candidatesFound = 0

        for (i in 0 until suffixes.size - 1) {
            val pos1 = suffixes[i]

            comparisonCount++
            val commonLength = lcp[i]

            if (commonLength >= minLength) {
                val pattern = text.substring(pos1, pos1 + commonLength)
                val occurrences = searcher.findAll(pattern)

                if (occurrences.size >= minOccurrences) {
                    candidatesFound++
                    log.trace(
                        "Found candidate: '{}' (length={}, occurrences={})",
                        pattern.take(20) + (if (pattern.length > 20) "..." else ""),
                        pattern.length, occurrences.size
                    )
                    candidates.add(pattern to occurrences)
                }
            }
        }
        log.debug(
            "Completed subsequence search in {}ms. Made {} comparisons, found {} candidates",
            System.currentTimeMillis() - startTime, comparisonCount, candidatesFound
        )

        return candidates
    }

    /**
     * Applies abbreviations to the text based on the identified repeating subsequences.
     */
    private fun applyAbbreviations(
        text: String,
        candidates: List<Pair<String, List<Int>>>
    ): String {
        log.debug("Applying abbreviations from {} candidates", candidates.size)

        val applyStartTime = System.currentTimeMillis()
        if (candidates.isEmpty()) {
            log.debug("No candidates for abbreviation, returning original text")
            return text
        }
        val isReplaced = BooleanArray(text.length) { false }
        val selectedReplacements = mutableListOf<Triple<Int, Int, String>>()



        candidates.forEach { (pattern, positions) ->

            positions.drop(1).forEach { origPos ->
                val origStart = origPos
                val origEnd = origPos + pattern.length

                var overlaps = false
                for (i in origStart until origEnd) {

                    if (i >= 0 && i < isReplaced.size && isReplaced[i]) {
                        overlaps = true
                        break
                    }
                }

                if (!overlaps) {

                    val prefixLength = minOf(5, pattern.length / 4)
                    val suffixLength = minOf(5, pattern.length / 4)
                    val abbr = if (pattern.length <= prefixLength + suffixLength + 5) {
                        log.trace("Pattern too short to abbreviate (length={})", pattern.length)

                        pattern
                    } else {

                        val prefix = pattern.substring(0, prefixLength)
                        val suffix = pattern.substring(pattern.length - suffixLength)
                        log.trace("Created abbreviation: '{}'", "$prefix...$suffix")
                        "$prefix...$suffix"
                    }
                    val diff = abbr.length - pattern.length
                    if (diff < 0) {


                        selectedReplacements.add(Triple(origStart, origEnd, abbr))

                        for (i in origStart until origEnd) {

                            if (i < isReplaced.size) {

                                isReplaced[i] = true
                            }
                        }
                        log.trace(
                            "Selected abbreviation for '{}' @{} → '{}' (saved {} chars)",
                            pattern.take(20) + if (pattern.length > 20) "..." else "",
                            origStart, abbr, -diff
                        )
                    }
                }
            }
        }

        if (selectedReplacements.isEmpty()) {
            log.debug("No effective abbreviations found after overlap check, returning original text")
            return text
        }

        selectedReplacements.sortBy { it.first }

        val result = StringBuilder()
        var lastEnd = 0
        selectedReplacements.forEach { (start, end, abbr) ->

            if (start > lastEnd) {

                result.append(text.substring(lastEnd, start))
            }

            result.append(abbr)

            lastEnd = minOf(end, text.length)

        }

        if (lastEnd < text.length) {
            result.append(text.substring(lastEnd))
        }

        log.debug(
            "Applied {} abbreviations in {}ms. Original length={}, compressed length={}",
            selectedReplacements.size,
            System.currentTimeMillis() - applyStartTime,
            text.length, result.length
        )
        return result.toString()
    }

}