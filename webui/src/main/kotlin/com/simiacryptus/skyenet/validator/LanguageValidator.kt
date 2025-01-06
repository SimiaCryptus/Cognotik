package com.simiacryptus.skyenet.validator

interface LanguageValidator {
    /**
     * Validates if a given string contains valid code in the target language.
     *
     * @param code The string containing code to validate
     * @return Boolean indicating whether the code is valid
     */
    fun isValid(code: String): Boolean

    /**
     * Validates if a given string contains valid code and returns any error messages.
     *
     * @param code The string containing code to validate
     * @return Pair of Boolean (indicating validity) and String (containing error message if any)
     */
    fun validateWithErrors(code: String): Pair<Boolean, String>
}