package com.simiacryptus.cognotik.text.validate

import css3Lexer
import css3Parser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates CSS source using the ANTLR CSS3 grammar.
 */
class CssGrammarValidator : AntlrGrammarValidator<css3Lexer, css3Parser>("CSS") {
  override fun createLexer(input: CharStream) = css3Lexer(input)
  override fun createParser(tokens: TokenStream) = css3Parser(tokens)
  override fun parse(parser: css3Parser) = parser.stylesheet()
}