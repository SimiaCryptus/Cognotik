package utils

/**
 * Reverses the words in a given string. Words are assumed to be separated by spaces.
 *
 * @param input The string whose words are to be reversed.
 * @return A new string with the words in reverse order.
 */
fun reverseWords(input: String): String {
    return input.split(" ").reversed().joinToString(" ")
}

object StringManipulationUtils {
    /**
    /**
     * Finds all matches of a regex pattern in a string.
    */
    fun regexFindAll(input: String, pattern: String): List<String> {
    return Regex(pattern).findAll(input).map { it.value }.toList()
    }
    /**
     * Encodes a string to Hex.
    */
    fun hexEncode(input: String): String {
    return input.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }
    }
    /**
     * Decodes a Hex encoded string.
    */
    fun hexDecode(input: String): String {
    return input.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(StandardCharsets.UTF_8)
    }
     * Finds all matches of a regex pattern in a string.
     */
    fun regexFindAll(input: String, pattern: String): List<String> {
        return Regex(pattern).findAll(input).map { it.value }.toList()
    }

    /**
     * Encodes a string to Hex.
     */
    fun hexEncode(input: String): String {
        return input.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Decodes a Hex encoded string.
     */
    fun hexDecode(input: String): String {
        return input.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(StandardCharsets.UTF_8)
    }

    companion object {

        /**
         * Trims whitespace from both ends of a string.
         * @param str The string to trim.
         * @return The trimmed string.
         */
        fun trim(str: String?): String {
            return StringOperations.trim(str)
        }

        /**
         * Splits a string by a given delimiter.
         * @param str The string to split.
         * @param delimiter The delimiter to use for splitting.
         * @return A list of substrings.
         */
        fun split(str: String?, delimiter: String): List<String> {
            return str.split(delimiter)
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
        /**
         * Finds all matches of a regex pattern in a string.
        */
        fun regexFindAll(input: String, pattern: String): List<String> {
        return Regex(pattern).findAll(input).map { it.value }.toList()
        }
        /**
         * Encodes a string to Hex.
        */
        fun hexEncode(input: String): String {
        return input.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }
        }
        /**
         * Decodes a Hex encoded string.
        */
        fun hexDecode(input: String): String {
        return input.chunked(2).map { it.toInt(16).toByte() }.toByteArray().toString(StandardCharsets.UTF_8)
        }
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
                .toLowerCase()
        }

        /**
         * Converts a string to snake_case.
         * @param str The string to convert.
         * @return The snake_case string.
         */
        fun toSnakeCase(str: String?): String {
            return str?.replace(Regex("([a-z])([A-Z])"), "$1_$2")
                ?.replace(Regex("\\s+"), "_")
                .toLowerCase()
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
                .padEnd(targetLength, padString.first())
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
            return str == str.reversed()
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
            return str.substring(startIndex, endIndex)
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

        // Method to remove all whitespace from a string
        fun removeWhitespace(str: String?): String {
            return str?.replace("\\s".toRegex(), "") ?: ""
        }

        // Method to replace the first occurrence of a substring with another substring
        fun replaceFirst(str: String?, oldValue: String, newValue: String): String {
            return str?.replaceFirst(oldValue, newValue) ?: ""
        }

        // Method to replace the last occurrence of a substring with another substring
        fun replaceLast(str: String?, oldValue: String, newValue: String): String {
            return str?.replaceLast(oldValue.toRegex(), newValue) ?: ""
        }

        // Method to truncate a string to a specified length
        fun truncate(str: String?, maxLength: Int): String {
            return if (str != null && str.length > maxLength) str.substring(0, maxLength) else str ?: ""
        }

        // Method to wrap text at a specified width
        fun wrapText(str: String?, width: Int): String {
            if (str == null) return ""
            val regex = "(?<=\\G.{$width})".toRegex()
            return str.replace(regex, "\n")
        }
    }
}