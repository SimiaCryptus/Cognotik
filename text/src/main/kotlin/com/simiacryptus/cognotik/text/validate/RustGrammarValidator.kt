package com.simiacryptus.cognotik.text.validate

import RustLexer
import RustParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

class RustGrammarValidator : AntlrGrammarValidator<RustLexer, RustParser>("Rust") {
  override fun createLexer(input: CharStream) = RustLexer(input)
  override fun createParser(tokens: TokenStream) = RustParser(tokens)
  override fun parse(parser: RustParser) {
    parser.crate()
  }
}