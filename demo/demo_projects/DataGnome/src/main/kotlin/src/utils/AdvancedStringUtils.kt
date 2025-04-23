package utils

import java.text.Normalizer
import java.util.Base64
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * A comprehensive utility class for advanced string manipulation operations.
 */
object AdvancedStringUtils {

    /**
     * Trims whitespace from both ends of a string.
     */
    fun trim(str: String?): String = str?.trim() ?: ""

    /**
     * Splits a string by a given delimiter.
     */
    fun split(str: String?, delimiter: String): List<String> = str?.split(delimiter) ?: emptyList()

    /**
     * Joins a list of strings with a given delimiter.
     */
    fun join(list: List<String>?, delimiter: String): String = list?.joinToString(delimiter) ?: ""

    /**
     * Converts a string to uppercase.
     */
    fun toUpperCase(str: String?): String = str?.uppercase() ?: ""

    /**
     * Converts a string to lowercase.
     */
    fun toLowerCase(str: String?): String = str?.lowercase() ?: ""

    /**
     * Capitalizes the first character of a string.
     */
    fun capitalizeFirst(str: String?): String = str?.replaceFirstChar { it.titlecase() } ?: ""

    /**
     * Converts a string to kebab-case.
     */
    fun toKebabCase(str: String?): String = str?.replace(Regex("([a-z])([A-Z])"), "$1-$2")
        ?.replace(Regex("\\s+"), "-")
        ?.lowercase() ?: ""

    /**
     * Converts a string to snake_case.
     */
    fun toSnakeCase(str: String?): String = str?.replace(Regex("([a-z])([A-Z])"), "$1_$2")
        ?.replace(Regex("\\s+"), "_")
        ?.lowercase() ?: ""

    /**
     * Repeats a string a specified number of times.
     */
    fun repeat(str: String?, count: Int): String {
        require(count >= 0) { "Count must be non-negative" }
        return str?.repeat(count) ?: ""
    }

    /**
     * Checks if a string contains a substring.
     */
    fun contains(str: String?, substring: String): Boolean = str?.contains(substring) ?: false

    /**
     * Pads a string with another string until it reaches a specified length.
     */
    fun pad(str: String?, targetLength: Int, padString: String = " "): String {
        val paddingSize = (targetLength - (str?.length ?: 0)) / 2
        return str?.padStart((str.length + paddingSize).coerceAtLeast(0), padString.first())
            ?.padEnd(targetLength.coerceAtLeast(0), padString.first()) ?: ""
    }

    /**
     * Reverses a string.
     */
    fun reverse(str: String?): String = str?.reversed() ?: ""

    /**
     * Checks if a string is a palindrome.
     */
    fun isPalindrome(str: String?): Boolean {
        val sanitized = str?.filter { it.isLetterOrDigit() }?.lowercase() ?: ""
        return sanitized == sanitized.reversed()
    }

    /**
     * Capitalizes the first letter of each word in a string.
     */
    fun capitalizeWords(str: String?): String = str?.split(" ")?.joinToString(" ") { it.capitalize() } ?: ""

    /**
     * Trims whitespace from the start of a string.
     */
    fun trimStart(str: String?): String = str?.trimStart() ?: ""

    /**
     * Trims whitespace from the end of a string.
     */
    fun trimEnd(str: String?): String = str?.trimEnd() ?: ""

    /**
     * Replaces occurrences of a substring with another substring.
     */
    fun replace(str: String?, oldValue: String, newValue: String): String = str?.replace(oldValue, newValue) ?: ""

    /**
     * Extracts a substring between two indices.
     */
    fun substring(str: String?, startIndex: Int, endIndex: Int): String {
        return if (str != null && startIndex in 0..str.length && endIndex in startIndex..str.length) {
            str.substring(startIndex, endIndex)
        } else {
            ""
        }
    }

    /**
     * Finds the index of the first occurrence of a substring.
     */
    fun indexOf(str: String?, substring: String): Int = str?.indexOf(substring) ?: -1

    /**
     * Finds the index of the last occurrence of a substring.
     */
    fun lastIndexOf(str: String?, substring: String): Int = str?.lastIndexOf(substring) ?: -1

    /**
     * Checks if a string starts with a specified substring.
     */
    fun startsWith(str: String?, prefix: String): Boolean = str?.startsWith(prefix) ?: false

    /**
     * Checks if a string ends with a specified substring.
     */
    fun endsWith(str: String?, suffix: String): Boolean = str?.endsWith(suffix) ?: false

    /**
     * Returns the character at a specified index.
     */
    fun charAt(str: String?, index: Int): Char {
        if (str == null || index < 0 || index >= str.length) {
            throw StringIndexOutOfBoundsException("Index $index out of bounds for length ${str?.length ?: 0}")
        }
        return str[index]
    }

    /**
     * Returns the length of a string.
     */
    fun length(str: String?): Int = str?.length ?: 0

    /**
     * Formats a string with placeholders.
     */
    fun format(template: String?, vararg args: Any): String = template?.format(*args) ?: ""

    /**
     * Removes all whitespace from a string.
     */
    fun removeWhitespace(str: String?): String = str?.replace("\\s".toRegex(), "") ?: ""

    /**
     * Replaces the first occurrence of a substring with another substring.
     */
    fun replaceFirst(str: String?, oldValue: String, newValue: String): String =
        str?.replaceFirst(oldValue, newValue) ?: ""

    /**
     * Replaces the last occurrence of a substring with another substring.
     */
    fun replaceLast(str: String?, oldValue: String, newValue: String): String =
        str?.replaceLast(oldValue.toRegex(), newValue) ?: ""

    /**
     * Truncates a string to a specified length.
     */
    fun truncate(str: String?, maxLength: Int): String =
        if (str != null && str.length > maxLength) str.substring(0, maxLength) else str ?: ""

    /**
     * Wraps text at a specified width.
     */
    fun wrapText(str: String?, width: Int): String {
        if (str == null) return ""
        val regex = "(?<=\\G.{$width})".toRegex()
        return str.replace(regex, "\n")
    }

    /**
     * Replaces all occurrences of a regex pattern in a string with a replacement.
     */
    fun regexReplace(input: String, pattern: String, replacement: String): String =
        input.replace(Regex(pattern), replacement)

    /**
     * Finds all matches of a regex pattern in a string.
     */
    fun regexFindAll(input: String, pattern: String): List<String> =
        Regex(pattern).findAll(input).map { it.value }.toList()

    /**
     * Encodes a string to Base64.
     */
    fun base64Encode(input: String): String =
        Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))

    /**
     * Decodes a Base64 encoded string.
     */
    fun base64Decode(input: String): String = String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8)

    /**
     * URL encodes a string.
     */
    fun urlEncode(input: String): String = URLEncoder.encode(input, StandardCharsets.UTF_8.toString())

    /**
     * URL decodes a string.
     */
    fun urlDecode(input: String): String = URLDecoder.decode(input, StandardCharsets.UTF_8.toString())

    /**
     * Encodes a string to Hex.
     */
    fun hexEncode(input: String): String =
        input.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }

    /**
     * Decodes a Hex encoded string.
     */
    fun hexDecode(input: String): String =
        input.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(StandardCharsets.UTF_8)

    /**
     * Normalizes a string by removing diacritics and converting to lowercase.
     */
    fun normalize(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
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

    /**
     * Checks if a string is numeric.
     */
    fun isNumeric(input: String): Boolean = input.matches(Regex("\\d+"))

    /**
     * Checks if a string is alphabetic.
     */
    fun isAlphabetic(input: String): Boolean = input.matches(Regex("[a-zA-Z]+"))

    /**
     * Compares two strings case-insensitively.
     */
    fun caseInsensitiveCompare(str1: String, str2: String): Int = str1.compareTo(str2, ignoreCase = true)

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
    private fun String.isDigitsOnly(): Boolean = this.matches(Regex("\\d+"))
}