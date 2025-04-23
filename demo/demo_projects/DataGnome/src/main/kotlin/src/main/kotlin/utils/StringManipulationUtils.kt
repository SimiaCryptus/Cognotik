package utils

class StringManipulationUtils {

    /**
     * Reverses the given string.
     * @param input The string to reverse.
     * @return The reversed string.
     */
    fun reverse(input: String): String {
        return input.reversed()
    }

    /**
     * Converts the given string to uppercase.
     * @param input The string to convert.
     * @return The uppercase string.
     */
    fun toUpperCase(input: String): String {
        return input.uppercase()
    }

    /**
     * Converts the given string to lowercase.
     * @param input The string to convert.
     * @return The lowercase string.
     */
    fun toLowerCase(input: String): String {
        return input.lowercase()
    }

    /**
     * Checks if the given string is a palindrome.
     * @param input The string to check.
     * @return True if the string is a palindrome, false otherwise.
     */
    fun isPalindrome(input: String): Boolean {
        val cleanedInput = input.filter { it.isLetterOrDigit() }.lowercase()
        return cleanedInput == cleanedInput.reversed()
    }

    /**
     * Replaces all occurrences of a substring with another substring.
     * @param input The original string.
     * @param target The substring to be replaced.
     * @param replacement The substring to replace with.
     * @return The modified string.
     */
    fun replace(input: String, target: String, replacement: String): String {
        return input.replace(target, replacement)
    }

    /**
     * Splits the string by a delimiter.
     * @param input The string to split.
     * @param delimiter The delimiter to split by.
     * @return A list of substrings.
     */
    fun split(input: String, delimiter: String): List<String> {
        return input.split(delimiter)
    }

    /**
     * Joins a list of strings into a single string with a delimiter.
     * @param parts The list of strings to join.
     * @param delimiter The delimiter to use.
     * @return The joined string.
     */
    fun join(parts: List<String>, delimiter: String): String {
        return parts.joinToString(delimiter)
    }
}