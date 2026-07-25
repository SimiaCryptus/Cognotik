package com.simiacryptus.cognotik.validate

import KotlinLexer
import KotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.slf4j.LoggerFactory.getLogger

/**
 * Validates Kotlin source using the ANTLR Kotlin grammar.
 *
 * @param isScript when true the input is parsed with the `script` start rule
 *                 (used for `.kts` files) instead of the `kotlinFile` rule.
 */
class KotlinGrammarValidator(private val isScript: Boolean = false) : GrammarValidator {
  companion object {
    private val log = getLogger(KotlinGrammarValidator::class.java)
  }

  override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> {
    try {
      val lexer = KotlinLexer(CharStreams.fromString(code))
      val tokens = CommonTokenStream(lexer)
      val parser = KotlinParser(tokens)
      if (isScript) parser.script() else parser.kotlinFile()
      return if (parser.numberOfSyntaxErrors == 0) {
        emptyList()
      } else {
        listOf(
          GrammarValidator.ValidationError(
            message = "Kotlin syntax errors detected",
            severity = GrammarValidator.Severity.ERROR
          )
        )
      }
    } catch (e: Throwable) {
      log.error("Error validating Kotlin grammar", e)
      return listOf(
        GrammarValidator.ValidationError(
          message = "Error validating Kotlin grammar: ${e.message}",
          severity = GrammarValidator.Severity.ERROR
        )
      )
    }
  }
}