package com.simiacryptus.cognotik.text.validate

import HTMLLexer
import HTMLParser
import com.simiacryptus.cognotik.text.validate.GrammarValidator.SymbolKind
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates HTML source using the ANTLR HTML grammar.
 */
class HtmlGrammarValidator : AntlrGrammarValidator<HTMLLexer, HTMLParser>("HTML") {
  override fun createLexer(input: CharStream) = HTMLLexer(input)
  override fun createParser(tokens: TokenStream) = HTMLParser(tokens)
  override fun parse(parser: HTMLParser) = parser.htmlDocument()

  /** HTML has no declarations worth reporting as public symbols. */
  override val symbolRules: Map<String, SymbolKind> get() = emptyMap()

  /** Markup contains tag/attribute names, not symbol references. */
  override fun extractSymbolReferences(
    code: String,
    symbols: List<GrammarValidator.SymbolInfo>
  ): List<GrammarValidator.SymbolReference> = emptyList()
}