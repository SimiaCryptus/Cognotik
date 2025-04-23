package utils

/**
 * A utility class for common string operations.
 */
class NewStringUtils {

    companion object {

        /**
         * Trims whitespace from both ends of a string.
         * @param str The string to trim.
         * @return The trimmed string.
         */
        fun trim(str: String?): String {
            return str?.trim() ?: ""
        }

        /**
         * Splits a string by a given delimiter.
         * @param str The string to split.
         * @param delimiter The delimiter to use for splitting.
         * @return A list of substrings.
         */
        fun split(str: String?, delimiter: String): List<String> {
            return str?.split(delimiter) ?: emptyList()
        }

        /**
         * Joins a list of strings with a given delimiter.
         * @param list The list of strings to join.
         * @param delimiter The delimiter to use for joining.
         * @return The joined string.
         */
        fun join(list: List<String>?, delimiter: String): String {
            return list?.joinToString(delimiter) ?: ""
        }

        /**
         * Converts a string to uppercase.
         * @param str The string to convert.
         * @return The uppercase string.
         */
        fun toUpperCase(str: String?): String {
            return str?.toUpperCase() ?: ""
        }

        /**
         * Converts a string to lowercase.
         * @param str The string to convert.
         * @return The lowercase string.
         */
        fun toLowerCase(str: String?): String {
            return str?.toLowerCase() ?: ""
        }

        /**
         * Capitalizes the first character of a string.
         * @param str The string to capitalize.
         * @return The string with the first character capitalized.
         */
        fun capitalizeFirst(str: String?): String {
            return str?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: ""
        }

        /**
         * Converts a string to kebab-case.
         * @param str The string to convert.
         * @return The kebab-case string.
         */
        fun toKebabCase(str: String?): String {
            return str?.replace(Regex("([a-z])([A-Z])"), "$1-$2")
                ?.replace(Regex("\\s+"), "-")
                ?.toLowerCase() ?: ""
        }

        /**
         * Converts a string to snake_case.
         * @param str The string to convert.
         * @return The snake_case string.
         */
        fun toSnakeCase(str: String?): String {
            return str?.replace(Regex("([a-z])([A-Z])"), "$1_$2")
                ?.replace(Regex("\\s+"), "_")
                ?.toLowerCase() ?: ""
        }

        /**
         * Repeats a string a specified number of times.
         * @param str The string to repeat.
         * @param count The number of times to repeat.
         * @return The repeated string.
         * @throws IllegalArgumentException if count is negative.
         */
        fun repeat(str: String?, count: Int): String {
            require(count >= 0) { "Count must be non-negative" }
            return str?.repeat(count) ?: ""
        }

        /**
         * Checks if a string contains a substring.
         * @param str The string to check.
         * @param substring The substring to look for.
         * @return True if the string contains the substring, false otherwise.
         */
        fun contains(str: String?, substring: String): Boolean {
            return str?.contains(substring) ?: false
        }

        /**
         * Pads a string with another string until it reaches a specified length.
         * @param str The string to pad.
         * @param targetLength The target length of the padded string.
         * @param padString The string to pad with.
         * @return The padded string.
         */
        fun pad(str: String?, targetLength: Int, padString: String = " "): String {
            val paddingSize = (targetLength - (str?.length ?: 0)) / 2
            return str?.padStart((str.length + paddingSize).coerceAtLeast(0), padString.first())
                ?.padEnd(targetLength.coerceAtLeast(0), padString.first()) ?: ""
        }

        /**
         * Reverses a string.
         * @param str The string to reverse.
         * @return The reversed string.
         */
        fun reverse(str: String?): String {
            return str?.reversed() ?: ""
        }

        /**
         * Checks if a string is a palindrome.
         * @param str The string to check.
         * @return True if the string is a palindrome, false otherwise.
         */
        fun isPalindrome(str: String?): Boolean {
            return str != null && str == str.reversed()
        }

        /**
         * Capitalizes the first letter of each word in a string.
         * @param str The string to capitalize.
         * @return The string with each word capitalized.
         */
        fun capitalizeWords(str: String?): String {
            return str?.split(" ")?.joinToString(" ") { it.capitalize() } ?: ""
        }

        /**
         * Trims whitespace from the start of a string.
         * @param str The string to trim.
         * @return The trimmed string.
         */
        fun trimStart(str: String?): String {
            return str?.trimStart() ?: ""
        }

        /**
         * Trims whitespace from the end of a string.
         * @param str The string to trim.
         * @return The trimmed string.
         */
        fun trimEnd(str: String?): String {
            return str?.trimEnd() ?: ""
        }

        /**
         * Replaces occurrences of a substring with another substring.
         * @param str The string to modify.
         * @param oldValue The substring to replace.
         * @param newValue The substring to replace with.
         * @return The modified string.
         */
        fun replace(str: String?, oldValue: String, newValue: String): String {
            return str?.replace(oldValue, newValue) ?: ""
        }

        /**
         * Extracts a substring between two indices.
         * @param str The string to extract from.
         * @param startIndex The start index.
         * @param endIndex The end index.
         * @return The extracted substring.
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
         * @param str The string to search.
         * @param substring The substring to find.
         * @return The index of the first occurrence, or -1 if not found.
         */
        fun indexOf(str: String?, substring: String): Int {
            return str?.indexOf(substring) ?: -1
        }

        /**
         * Finds the index of the last occurrence of a substring.
         * @param str The string to search.
         * @param substring The substring to find.
         * @return The index of the last occurrence, or -1 if not found.
         */
        fun lastIndexOf(str: String?, substring: String): Int {
            return str?.lastIndexOf(substring) ?: -1
        }

        /**
         * Checks if a string starts with a specified substring.
         * @param str The string to check.
         * @param prefix The prefix to look for.
         * @return True if the string starts with the prefix, false otherwise.
         */
        fun startsWith(str: String?, prefix: String): Boolean {
            return str?.startsWith(prefix) ?: false
        }

        /**
         * Checks if a string ends with a specified substring.
         * @param str The string to check.
         * @param suffix The suffix to look for.
         * @return True if the string ends with the suffix, false otherwise.
         */
        fun endsWith(str: String?, suffix: String): Boolean {
            return str?.endsWith(suffix) ?: false
        }

        /**
         * Returns the character at a specified index.
         * @param str The string to check.
         * @param index The index of the character.
         * @return The character at the specified index.
         * @throws StringIndexOutOfBoundsException if the index is out of range.
         */
        fun charAt(str: String?, index: Int): Char {
            if (str == null || index < 0 || index >= str.length) {
                throw StringIndexOutOfBoundsException("Index $index out of bounds for length ${str?.length ?: 0}")
            }
            return str[index]
        }

        /**
         * Returns the length of a string.
         * @param str The string to check.
         * @return The length of the string.
         */
        fun length(str: String?): Int {
            return str?.length ?: 0
        }

        /**
         * Formats a string with placeholders.
         * @param template The string template with placeholders.
         * @param args The arguments to replace the placeholders.
         * @return The formatted string.
         */
        fun format(template: String?, vararg args: Any): String {
            return template?.format(*args) ?: ""
        }

        /**
         * Removes all whitespace from a string.
         * @param str The string to modify.
         * @return The string without whitespace.
         */
        fun removeWhitespace(str: String?): String {
            return str?.replace("\\s".toRegex(), "") ?: ""
        }

        /**
         * Replaces the first occurrence of a substring with another substring.
         * @param str The string to modify.
         * @param oldValue The substring to replace.
         * @param newValue The substring to replace with.
         * @return The modified string.
         */
        fun replaceFirst(str: String?, oldValue: String, newValue: String): String {
            return str?.replaceFirst(oldValue, newValue) ?: ""
        }

        /**
         * Replaces the last occurrence of a substring with another substring.
         * @param str The string to modify.
         * @param oldValue The substring to replace.
         * @param newValue The substring to replace with.
         * @return The modified string.
         */
        fun replaceLast(str: String?, oldValue: String, newValue: String): String {
            return str?.replaceLast(oldValue.toRegex(), newValue) ?: ""
        }

        /**
         * Truncates a string to a specified length.
         * @param str The string to truncate.
         * @param maxLength The maximum length of the string.
         * @return The truncated string.
         */
        fun truncate(str: String?, maxLength: Int): String {
            return if (str != null && str.length > maxLength) str.substring(0, maxLength) else str ?: ""
        }

        /**
         * Wraps text at a specified width.
         * @param str The string to wrap.
         * @param width The width to wrap at.
         * @return The wrapped string.
         */
        fun wrapText(str: String?, width: Int): String {
            if (str == null) return ""
            val regex = "(?<=\\G.{$width})".toRegex()
            return str.replace(regex, "\n")
        }
    }
}