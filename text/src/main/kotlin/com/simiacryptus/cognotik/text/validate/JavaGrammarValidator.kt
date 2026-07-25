package com.simiacryptus.cognotik.text.validate

import Java20Lexer
import Java20Parser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates Java source using the ANTLR Java 20 grammar.
 */
class JavaGrammarValidator : AntlrGrammarValidator<Java20Lexer, Java20Parser>("Java") {
  override fun createLexer(input: CharStream) = Java20Lexer(input)
  override fun createParser(tokens: TokenStream) = Java20Parser(tokens)
  override fun parse(parser: Java20Parser) {
    parser.start_()
  }
}

