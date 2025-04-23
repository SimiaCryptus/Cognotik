package utils

/**
 * A utility class for common string manipulation operations.
 */
object CommonStringUtils {

    /**
     * Trims whitespace from both ends of a string.
     */
    fun trim(str: String?): String {
        return str?.trim() ?: ""
    }

    /**
     * Splits a string by a given delimiter.
     */
    fun split(str: String?, delimiter: String): List<String> {
        return str?.split(delimiter) ?: emptyList()
    }

    /**
     * Joins a list of strings with a given delimiter.
     */
    fun exampleJoinUsage(list: List<String>?, delimiter: String): String {
        return StringOperations.join(list, delimiter)
    }

    /**
     * Converts a string to uppercase.
     */
    fun toUpperCase(str: String?): String {
        return str?.toUpperCase() ?: ""
    }

    /**
     * Converts a string to lowercase.
     */
    fun toLowerCase(str: String?): String {
        return str?.toLowerCase() ?: ""
    }

    /**
     * Capitalizes the first character of a string.
     */
    fun capitalizeFirst(str: String?): String {
        return str?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: ""
    }

    /**
     * Converts a string to kebab-case.
     */
    fun toKebabCase(str: String?): String {
        return str?.replace(Regex("([a-z])([A-Z])"), "$1-$2")
            ?.replace(Regex("\\s+"), "-")
            ?.toLowerCase() ?: ""
    }

    /**
     * Converts a string to snake_case.
     */
    fun toSnakeCase(str: String?): String {
        return str?.replace(Regex("([a-z])([A-Z])"), "$1_$2")
            ?.replace(Regex("\\s+"), "_")
            ?.toLowerCase() ?: ""
    }

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
    fun contains(str: String?, substring: String): Boolean {
        return str?.contains(substring) ?: false
    }

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
    fun reverse(str: String?): String {
        return str?.reversed() ?: ""
    }

    /**
     * Checks if a string is a palindrome.
     */
    fun isPalindrome(str: String?): Boolean {
        return str != null && str == str.reversed()
    }

    /**
     * Capitalizes the first letter of each word in a string.
     */
    fun capitalizeWords(str: String?): String {
        return str?.split(" ")?.joinToString(" ") { it.capitalize() } ?: ""
    }

    /**
     * Trims whitespace from the start of a string.
     */
    fun trimStart(str: String?): String {
        return str?.trimStart() ?: ""
    }

    /**
     * Trims whitespace from the end of a string.
     */
    fun trimEnd(str: String?): String {
        return str?.trimEnd() ?: ""
    }

    /**
     * Replaces occurrences of a substring with another substring.
     */
    fun replace(str: String?, oldValue: String, newValue: String): String {
        return str?.replace(oldValue, newValue) ?: ""
    }

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
    fun indexOf(str: String?, substring: String): Int {
        return str?.indexOf(substring) ?: -1
    }

    /**
     * Finds the index of the last occurrence of a substring.
     */
    fun lastIndexOf(str: String?, substring: String): Int {
        return str?.lastIndexOf(substring) ?: -1
    }

    /**
     * Checks if a string starts with a specified substring.
     */
    fun startsWith(str: String?, prefix: String): Boolean {
        return str?.startsWith(prefix) ?: false
    }

    /**
     * Checks if a string ends with a specified substring.
     */
    fun endsWith(str: String?, suffix: String): Boolean {
        return str?.endsWith(suffix) ?: false
    }

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
    fun length(str: String?): Int {
        return str?.length ?: 0
    }

    /**
     * Formats a string with placeholders.
     */
    fun format(template: String?, vararg args: Any): String {
        return template?.format(*args) ?: ""
    }

    /**
     * Removes all whitespace from a string.
     */
    fun removeWhitespace(str: String?): String {
        return str?.replace("\\s".toRegex(), "") ?: ""
    }

    /**
     * Replaces the first occurrence of a substring with another substring.
     */
    fun replaceFirst(str: String?, oldValue: String, newValue: String): String {
        return str?.replaceFirst(oldValue, newValue) ?: ""
    }

    /**
     * Replaces the last occurrence of a substring with another substring.
     */
    fun replaceLast(str: String?, oldValue: String, newValue: String): String {
        return str?.replaceLast(oldValue.toRegex(), newValue) ?: ""
    }

    /**
     * Truncates a string to a specified length.
     */
    fun truncate(str: String?, maxLength: Int): String {
        return if (str != null && str.length > maxLength) str.substring(0, maxLength) else str ?: ""
    }

    /**
     * Wraps text at a specified width.
     */
    fun wrapText(str: String?, width: Int): String {
        if (str == null) return ""
        val regex = "(?<=\\G.{$width})".toRegex()
        return str.replace(regex, "\n")
    }
}