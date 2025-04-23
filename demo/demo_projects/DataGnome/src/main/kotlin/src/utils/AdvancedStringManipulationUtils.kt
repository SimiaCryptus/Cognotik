package utils

import java.text.Normalizer
import java.util.Locale

class AdvancedStringManipulationUtils {

    /**
     * Compares two strings ignoring case and diacritics.
     */
    fun compareIgnoringCaseAndDiacritics(str1: String, str2: String): Boolean {
        val normalizedStr1 = normalizeString(str1)
        val normalizedStr2 = normalizeString(str2)
        return normalizedStr1.equals(normalizedStr2, ignoreCase = true)
    }

    /**
     * Normalizes a string by removing diacritics.
     */
    private fun normalizeString(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace("\\p{M}".toRegex(), "")
    }

    /**
     * Converts a string to title case.
     */
    fun toTitleCase(input: String): String {
        return input.split(" ").joinToString(" ") { it.capitalize(Locale.getDefault()) }
    }

    /**
     * Calculates the Jaro-Winkler distance between two strings.
     */
    fun jaroWinklerDistance(s1: String, s2: String): Double {
        // Implementation of Jaro-Winkler distance calculation
        // This is a placeholder for the actual algorithm
        return 0.0
    }

    /**
     * Sanitizes a string for HTML to prevent injection attacks.
     */
    fun sanitizeForHtml(input: String): String {
        /**
         * Validates if the provided email is in a correct format using DataValidationUtils.
         * @param email The email string to validate.
         * @return True if the email is valid, false otherwise.
         */
        fun validateEmail(email: String): Boolean {
            return DataValidationUtils.isValidEmail(email)
        }
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
    }

    /**
     * Checks if a string is a palindrome.
     */
    fun isPalindrome(input: String): Boolean {
        val sanitized = input.replace("\\s".toRegex(), "").toLowerCase(Locale.getDefault())
        return sanitized == sanitized.reversed()
    }

    /**
     * Provides advanced regex support with named groups.
     */
    fun matchWithNamedGroups(pattern: String, input: String): Map<String, String>? {
        val regex = Regex(pattern)
        val matchResult = regex.find(input) ?: return null
        return matchResult.groups.filterKeys { it != null }.mapValues { it.value!!.value }
    }
}