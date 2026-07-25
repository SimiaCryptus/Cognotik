package com.simiacryptus.cognotik.text.validate

interface GrammarValidator {
  fun validateGrammar(code: String): List<ValidationError>
  data class ValidationError(
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
    val severity: Severity = Severity.ERROR
  )

  enum class Severity {
    ERROR,
  }
}