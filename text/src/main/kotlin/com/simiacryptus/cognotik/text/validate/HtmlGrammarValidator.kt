package com.simiacryptus.cognotik.text.validate

import HTMLLexer
import HTMLParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates HTML source using the ANTLR HTML grammar.
 */
class HtmlGrammarValidator : AntlrGrammarValidator<HTMLLexer, HTMLParser>("HTML") {
  override fun createLexer(input: CharStream) = HTMLLexer(input)
  override fun createParser(tokens: TokenStream) = HTMLParser(tokens)
  override fun parse(parser: HTMLParser) {
    parser.htmlDocument()
  }
}