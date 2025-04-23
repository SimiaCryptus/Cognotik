package utils

import java.util.Base64
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object NewStringManipulationUtils {

    /**
     * Replaces all occurrences of a regex pattern in a string with a replacement.
     */
    fun regexReplace(input: String, pattern: String, replacement: String): String {
        return input.replace(Regex(pattern), replacement)
    }

    /**
     * Checks if a string is numeric.
     */
    fun isNumeric(input: String): Boolean {
        return input.matches(Regex("\\d+"))
    }

    /**
     * Checks if a string is alphabetic.
     */
    fun isAlphabetic(input: String): Boolean {
        return input.matches(Regex("[a-zA-Z]+"))
    }

    /**
     * Encodes a string to Base64.
     */
    fun base64Encode(input: String): String {
        return Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Decodes a Base64 encoded string.
     */
    fun base64Decode(input: String): String {
        return String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8)
    }

    /**
     * URL encodes a string.
     */
    fun urlEncode(input: String): String {
        return URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
    }

    /**
     * URL decodes a string.
     */
    fun urlDecode(input: String): String {
        return URLDecoder.decode(input, StandardCharsets.UTF_8.toString())
    }

    /**
     * Compares two strings case-insensitively.
     */
    fun caseInsensitiveCompare(str1: String, str2: String): Int {
        return str1.compareTo(str2, ignoreCase = true)
    }

    /**
     * Compares two strings using natural order.
     */
    fun naturalOrderCompare(str1: String, str2: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val str1Parts = regex.findAll(str1).map { it.value }.toList()
        val str2Parts = regex.findAll(str2).map { it.value }.toList()

        for (i in 0 until minOf(str1Parts.size, str2Parts.size)) {
            val part1 = str1Parts[i]
            val part2 = str2Parts[i]

            val comparison = if (part1.isDigitsOnly() && part2.isDigitsOnly()) {
                part1.toInt().compareTo(part2.toInt())
            } else {
                part1.compareTo(part2)
            }

            if (comparison != 0) return comparison
        }

        return str1Parts.size.compareTo(str2Parts.size)
    }

    /**
     * Helper function to check if a string contains only digits.
     */
    private fun String.isDigitsOnly(): Boolean {
        return this.matches(Regex("\\d+"))
    }

    /**
     * Normalizes a string by removing diacritics and converting to lowercase.
     */
    fun normalize(input: String): String {
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").toLowerCase()
    }

    /**
     * Calculates the Levenshtein distance between two strings.
     */
    fun levenshteinDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }
        for (i in 0..str1.length) {
            for (j in 0..str2.length) {
                if (i == 0) {
                    dp[i][j] = j
                } else if (j == 0) {
                    dp[i][j] = i
                } else {
                    dp[i][j] = minOf(
                        dp[i - 1][j - 1] + if (str1[i - 1] == str2[j - 1]) 0 else 1,
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1
                    )
                }
            }
        }
        return dp[str1.length][str2.length]
    }
}