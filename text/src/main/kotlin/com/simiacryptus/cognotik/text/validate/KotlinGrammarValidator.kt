package com.simiacryptus.cognotik.text.validate

import KotlinLexer
import KotlinParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.TokenStream

/**
 * Validates Kotlin source using the ANTLR Kotlin grammar.
 *
 * @param isScript when true the input is parsed with the `script` start rule
 *                 (used for `.kts` files) instead of the `kotlinFile` rule.
 */
class KotlinGrammarValidator(private val isScript: Boolean = false) :
  AntlrGrammarValidator<KotlinLexer, KotlinParser>("Kotlin") {
  override fun createLexer(input: CharStream) = KotlinLexer(input)
  override fun createParser(tokens: TokenStream) = KotlinParser(tokens)
  override fun parse(parser: KotlinParser): ParserRuleContext =
    if (isScript) parser.script() else parser.kotlinFile()
}