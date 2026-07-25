package com.simiacryptus.cognotik.text.validate

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.TokenStream
import org.slf4j.LoggerFactory.getLogger

/**
 * Common plumbing for grammar validators backed by an ANTLR lexer/parser pair.
 *
 * Sub classes only need to supply the lexer/parser factories and the start rule
 * to invoke; all error collection (including line/column information) is handled
 * here.
 */
abstract class AntlrGrammarValidator<L : Lexer, P : Parser>(
  private val languageName: String
) : GrammarValidator {
  companion object {
    private val log = getLogger(AntlrGrammarValidator::class.java)
  }

  protected abstract fun createLexer(input: CharStream): L
  protected abstract fun createParser(tokens: TokenStream): P

  /** Invoke the start rule of the grammar. */
  protected abstract fun parse(parser: P)

  override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> {
    return try {
      val errorCollector = ErrorCollector()
      val lexer = createLexer(CharStreams.fromString(code)).apply {
        removeErrorListeners()
        addErrorListener(errorCollector)
      }
      val parser = createParser(CommonTokenStream(lexer)).apply {
        removeErrorListeners()
        addErrorListener(errorCollector)
      }
      parse(parser)
      errorCollector.errors.toList()
    } catch (e: Throwable) {
      log.error("Error validating $languageName grammar", e)
      listOf(
        GrammarValidator.ValidationError(
          message = "Error validating $languageName grammar: ${e.message}",
          severity = GrammarValidator.Severity.ERROR
        )
      )
    }
  }

  protected class ErrorCollector : BaseErrorListener() {
    val errors = mutableListOf<GrammarValidator.ValidationError>()
    override fun syntaxError(
      recognizer: Recognizer<*, *>?,
      offendingSymbol: Any?,
      line: Int,
      charPositionInLine: Int,
      msg: String?,
      e: RecognitionException?
    ) {
      errors.add(
        GrammarValidator.ValidationError(
          message = msg ?: "Syntax error",
          line = line,
          column = charPositionInLine,
          severity = GrammarValidator.Severity.ERROR
        )
      )
    }
  }
}