package com.simiacryptus.skyenet.validator

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

object JavaScriptValidator : LanguageValidator {
    /**
     * Validates if a given string contains valid JavaScript code.
     *
     * @param code The string containing JavaScript code to validate
     * @return Boolean indicating whether the code is valid JavaScript
     */
    override fun isValid(code: String): Boolean {
        return try {
            Context.create("js").use { context ->
                // Parse the code without executing it
              Source.create("js", code)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates if a given string contains valid JavaScript code and returns any error messages.
     *
     * @param code The string containing JavaScript code to validate
     * @return Pair of Boolean (indicating validity) and String (containing error message if any)
     */
    override fun validateWithErrors(code: String): Pair<Boolean, String> {
        return try {
            Context.create("js").use { context ->
                // Parse the code without executing it
              Source.create("js", code)
                Pair(true, "")
            }
        } catch (e: Exception) {
            // Extract line number from error message if available
            val errorMessage = e.message ?: "Unknown error occurred"
            val lineNumberRegex = "at <js>:([0-9]+)".toRegex()
            val lineMatch = lineNumberRegex.find(errorMessage)

            val formattedError = if (lineMatch != null) {
                val lineNumber = lineMatch.groupValues[1]
                val baseError = errorMessage.substringBefore(" at <js>")
                "$baseError at line $lineNumber"
            } else {
                errorMessage
            }

            Pair(false, formattedError)
        }
    }
}