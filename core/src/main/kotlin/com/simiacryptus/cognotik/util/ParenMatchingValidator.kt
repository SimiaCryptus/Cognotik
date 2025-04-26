package com.simiacryptus.cognotik.util

/**
 * A grammar validator that checks for balanced parentheses, brackets, braces, and quotes
 */
class ParenMatchingValidator : GrammarValidator {
    override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> {
        val errors = mutableListOf<GrammarValidator.ValidationError>()
        if (!isCurlyBalanced(code)) {
            errors.add(
                GrammarValidator.ValidationError(
                    message = "Unbalanced curly braces",
                    severity = GrammarValidator.Severity.ERROR
                )
            )
        }
        if (!isSquareBalanced(code)) {
            errors.add(
                GrammarValidator.ValidationError(
                    message = "Unbalanced square brackets",
                    severity = GrammarValidator.Severity.ERROR
                )
            )
        }
        if (!isParenthesisBalanced(code)) {
            errors.add(
                GrammarValidator.ValidationError(
                    message = "Unbalanced parentheses",
                    severity = GrammarValidator.Severity.ERROR
                )
            )
        }
        if (!isQuoteBalanced(code)) {
            errors.add(
                GrammarValidator.ValidationError(
                    message = "Unbalanced double quotes",
                    severity = GrammarValidator.Severity.ERROR
                )
            )
        }
        if (!isSingleQuoteBalanced(code)) {
            errors.add(
                GrammarValidator.ValidationError(
                    message = "Unbalanced single quotes",
                    severity = GrammarValidator.Severity.ERROR
                )
            )
        }
        return errors
    }

    companion object {

        fun isCurlyBalanced(code: String): Boolean {
            var count = 0
            for (char in code) {
                when (char) {
                    '{' -> count++
                    '}' -> count--
                }
                if (count < 0) return false
            }
            return count == 0
        }

        fun isSingleQuoteBalanced(code: String): Boolean {
            var count = 0
            var escaped = false
            for (char in code) {
                when {
                    char == '\\' -> escaped = !escaped
                    char == '\'' && !escaped -> count++
                    else -> escaped = false
                }
            }
            return count % 2 == 0
        }

        fun isSquareBalanced(code: String): Boolean {
            var count = 0
            for (char in code) {
                when (char) {
                    '[' -> count++
                    ']' -> count--
                }
                if (count < 0) return false
            }
            return count == 0
        }

        fun isParenthesisBalanced(code: String): Boolean {
            var count = 0
            for (char in code) {
                when (char) {
                    '(' -> count++
                    ')' -> count--
                }
                if (count < 0) return false
            }
            return count == 0
        }

        fun isQuoteBalanced(code: String): Boolean {
            var count = 0
            var escaped = false
            for (char in code) {
                when {
                    char == '\\' -> escaped = !escaped
                    char == '"' && !escaped -> count++
                    else -> escaped = false
                }
            }
            return count % 2 == 0
        }

    }
}