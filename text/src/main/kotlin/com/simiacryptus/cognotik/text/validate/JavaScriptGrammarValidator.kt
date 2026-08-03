package com.simiacryptus.cognotik.text.validate

import ECMAScriptLexer
import ECMAScriptParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates JavaScript source using the ANTLR ECMAScript grammar.
 */
class JavaScriptGrammarValidator : AntlrGrammarValidator<ECMAScriptLexer, ECMAScriptParser>("JavaScript") {
  override fun createLexer(input: CharStream) = ECMAScriptLexer(input)
  override fun createParser(tokens: TokenStream) = ECMAScriptParser(tokens)
  override fun parse(parser: ECMAScriptParser) = parser.program()
}