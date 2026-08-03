package com.simiacryptus.cognotik.text.validate

import RustLexer
import RustParser
import com.simiacryptus.cognotik.text.validate.GrammarValidator.SymbolKind
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenStream

class RustGrammarValidator : AntlrGrammarValidator<RustLexer, RustParser>("Rust") {
  override fun createLexer(input: CharStream) = RustLexer(input)
  override fun createParser(tokens: TokenStream) = RustParser(tokens)
  override fun parse(parser: RustParser) = parser.crate()

  /** Rust items are private unless explicitly marked `pub`. */
  override fun isPublicDeclaration(kind: SymbolKind, name: String, tokens: List<Token>) =
    tokens.any { it.text == "pub" }
}