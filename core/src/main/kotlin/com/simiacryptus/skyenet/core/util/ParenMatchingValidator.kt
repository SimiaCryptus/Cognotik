package com.simiacryptus.skyenet.core.util

/**
 * A grammar validator that checks for balanced parentheses, brackets, braces, and quotes
 */
class ParenMatchingValidator : GrammarValidator {
    override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> {
        val errors = mutableListOf<GrammarValidator.ValidationError>()
        if (!FileValidationUtils.isCurlyBalanced(code)) {
            errors.add(GrammarValidator.ValidationError(
                message = "Unbalanced curly braces",
                severity = GrammarValidator.Severity.ERROR
            ))
        }
        if (!FileValidationUtils.isSquareBalanced(code)) {
            errors.add(GrammarValidator.ValidationError(
                message = "Unbalanced square brackets",
                severity = GrammarValidator.Severity.ERROR
            ))
        }
        if (!FileValidationUtils.isParenthesisBalanced(code)) {
            errors.add(GrammarValidator.ValidationError(
                message = "Unbalanced parentheses",
                severity = GrammarValidator.Severity.ERROR
            ))
        }
        if (!FileValidationUtils.isQuoteBalanced(code)) {
            errors.add(GrammarValidator.ValidationError(
                message = "Unbalanced double quotes",
                severity = GrammarValidator.Severity.ERROR
            ))
        }
        if (!FileValidationUtils.isSingleQuoteBalanced(code)) {
            errors.add(GrammarValidator.ValidationError(
                message = "Unbalanced single quotes",
                severity = GrammarValidator.Severity.ERROR
            ))
        }
        return errors
    }
}