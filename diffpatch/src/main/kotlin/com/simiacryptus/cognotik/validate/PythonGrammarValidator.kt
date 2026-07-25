package com.simiacryptus.cognotik.validate

import PythonLexer
import PythonParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates Python source using the ANTLR Python grammar.
 */
class PythonGrammarValidator : AntlrGrammarValidator<PythonLexer, PythonParser>("Python") {
  override fun createLexer(input: CharStream) = PythonLexer(input)
  override fun createParser(tokens: TokenStream) = PythonParser(tokens)
  override fun parse(parser: PythonParser) {
    parser.file_input()
  }
}