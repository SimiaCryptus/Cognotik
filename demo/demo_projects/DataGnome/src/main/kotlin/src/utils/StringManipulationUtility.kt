package utils

/**
 * A utility class for common string manipulation operations.
 */
class StringManipulationUtility {

    /**
     * Converts a string to camelCase.
     */
    fun toCamelCase(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        return str.split(" ", "-", "_").mapIndexed { index, word ->
            if (index == 0) word.lowercase() else word.capitalize()
        }.joinToString("")
    }

    /**
     * Converts a string to PascalCase.
     */
    fun toPascalCase(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        return str.split(" ", "-", "_").joinToString("") { it.capitalize() }
    }

    /**
     * Checks if a string is a valid email address.
     */
    fun isValidEmail(str: String?): Boolean {
        if (str.isNullOrEmpty()) return false
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
        return emailRegex.matches(str)
    }

    /**
     * Reverses the words in a string.
     */
    fun reverseWords(str: String?): String {
        if (str.isNullOrEmpty()) return ""
        return str.split(" ").reversed().joinToString(" ")
    }

    /**
     * Counts the number of vowels in a string.
     */
    fun countVowels(str: String?): Int {
        if (str.isNullOrEmpty()) return 0
        return str.count { it.lowercaseChar() in "aeiou" }
    }

    /**
     * Counts the number of consonants in a string.
     */
    fun countConsonants(str: String?): Int {
        if (str.isNullOrEmpty()) return 0
        return str.count { it.lowercaseChar() in "bcdfghjklmnpqrstvwxyz" }
    }

    /**
     * Checks if a string is a valid URL.
     */
    fun isValidUrl(str: String?): Boolean {
        if (str.isNullOrEmpty()) return false
        val urlRegex = "^(http|https)://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/\\S*)?$".toRegex()
        return urlRegex.matches(str)
    }

    /**
     * Converts a string to a list of ASCII values.
     */
    fun toAsciiValues(str: String?): List<Int> {
        if (str.isNullOrEmpty()) return emptyList()
        return str.map { it.code }
    }

    /**
     * Converts a list of ASCII values to a string.
     */
    fun fromAsciiValues(asciiValues: List<Int>?): String {
        if (asciiValues.isNullOrEmpty()) return ""
        return asciiValues.map { it.toChar() }.joinToString("")
    }
}