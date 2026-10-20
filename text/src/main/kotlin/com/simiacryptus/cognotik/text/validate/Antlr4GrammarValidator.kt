package com.simiacryptus.cognotik.text.validate

import ANTLRv4Lexer
import ANTLRv4Parser
import com.simiacryptus.cognotik.text.validate.GrammarValidator.SymbolKind
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.Vocabulary

/**
 * Validates ANTLR v4 grammar (`.g4`) source using the ANTLRv4 grammar.
 */
class Antlr4GrammarValidator : AntlrGrammarValidator<ANTLRv4Lexer, ANTLRv4Parser>("ANTLRv4") {
  override fun createLexer(input: CharStream) = ANTLRv4Lexer(input)
  override fun createParser(tokens: TokenStream) = ANTLRv4Parser(tokens)
  override fun parse(parser: ANTLRv4Parser) = parser.grammarSpec()

  override val bodyStartTokens: Set<String>
    get() = setOf(":", "{", ";")

  override fun symbolName(
    ctx: ParserRuleContext,
    kind: SymbolKind,
    tokens: List<Token>,
    vocabulary: Vocabulary,
    code: String
  ): String? {
    val colonIndex = tokens.indexOfFirst { it.text == ":" }
    val head = if (colonIndex > 0) tokens.subList(0, colonIndex) else tokens
    return head.firstOrNull { token ->
      val symbolic = vocabulary.getSymbolicName(token.type)?.uppercase() ?: ""
      symbolic.contains("IDENT")
    }?.text
  }

  /** Fragment lexer rules and private/protected parser rules are non-public. */
  override fun isPublicDeclaration(kind: SymbolKind, name: String, tokens: List<Token>) =
    super.isPublicDeclaration(kind, name, tokens) && tokens.none { it.text == "fragment" }
}