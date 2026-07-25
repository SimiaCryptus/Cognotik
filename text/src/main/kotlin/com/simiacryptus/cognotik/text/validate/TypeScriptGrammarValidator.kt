package com.simiacryptus.cognotik.text.validate

import TypeScriptLexer
import TypeScriptParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.TokenStream

/**
 * Validates TypeScript source using the ANTLR TypeScript grammar.
 */
class TypeScriptGrammarValidator : AntlrGrammarValidator<TypeScriptLexer, TypeScriptParser>("TypeScript") {
  override fun createLexer(input: CharStream) = TypeScriptLexer(input)
  override fun createParser(tokens: TokenStream) = TypeScriptParser(tokens)
  override fun parse(parser: TypeScriptParser) {
    parser.program()
  }
}