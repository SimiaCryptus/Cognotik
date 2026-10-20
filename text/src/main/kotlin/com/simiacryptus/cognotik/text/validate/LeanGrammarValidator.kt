package com.simiacryptus.cognotik.text.validate

import Lean4Lexer
import Lean4Parser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates Lean 4 source using the ANTLR Lean4 grammar.
 */
class LeanGrammarValidator : AntlrGrammarValidator<Lean4Lexer, Lean4Parser>("Lean") {
  override fun createLexer(input: CharStream) = Lean4Lexer(input)
  override fun createParser(tokens: TokenStream) = Lean4Parser(tokens)
  override fun parse(parser: Lean4Parser) = parser.module()
}