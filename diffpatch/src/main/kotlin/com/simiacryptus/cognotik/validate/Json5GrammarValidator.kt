package com.simiacryptus.cognotik.validate

import JSON5Lexer
import JSON5Parser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates JSON / JSON5 source using the ANTLR JSON5 grammar
 * (JSON5 is a superset of JSON, so plain JSON is accepted as well).
 */
class Json5GrammarValidator : AntlrGrammarValidator<JSON5Lexer, JSON5Parser>("JSON5") {
  override fun createLexer(input: CharStream) = JSON5Lexer(input)
  override fun createParser(tokens: TokenStream) = JSON5Parser(tokens)
  override fun parse(parser: JSON5Parser) {
    parser.json5()
  }
}