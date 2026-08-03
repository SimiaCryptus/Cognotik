package com.simiacryptus.cognotik.text.validate

import PythonLexer
import PythonParser
import com.simiacryptus.cognotik.text.validate.GrammarValidator.SymbolKind
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenStream

/**
 * Validates Python source using the ANTLR Python grammar.
 */
class PythonGrammarValidator : AntlrGrammarValidator<PythonLexer, PythonParser>("Python") {
  override fun createLexer(input: CharStream) = PythonLexer(input)
  override fun createParser(tokens: TokenStream) = PythonParser(tokens)
  override fun parse(parser: PythonParser) = parser.file_input()

  /** Python has no visibility keywords; the leading-underscore convention is used instead. */
  override fun isPublicDeclaration(kind: SymbolKind, name: String, tokens: List<Token>) = when {
    name.startsWith("__") && name.endsWith("__") -> true
    else -> !name.startsWith("_")
  }
}