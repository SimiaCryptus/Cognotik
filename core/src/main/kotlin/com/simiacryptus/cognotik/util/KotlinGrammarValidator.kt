package com.simiacryptus.cognotik.util

import KotlinLexer
import KotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.slf4j.LoggerFactory

class KotlinGrammarValidator : GrammarValidator {
    companion object {
        private val log = LoggerFactory.getLogger(KotlinGrammarValidator::class.java)
    }

    override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> {
        try {
            val lexer = KotlinLexer(CharStreams.fromString(code))
            val tokens = CommonTokenStream(lexer)
            val parser = KotlinParser(tokens)
            parser.kotlinFile()
            return if (parser.numberOfSyntaxErrors == 0) {
                emptyList()
            } else {
                listOf(
                    GrammarValidator.ValidationError(
                        message = "Kotlin syntax errors detected",
                        severity = GrammarValidator.Severity.ERROR
                    )
                )
            }
        } catch (e: Exception) {
            log.error("Error validating Kotlin grammar", e)
            return listOf(
                GrammarValidator.ValidationError(
                    message = "Error validating Kotlin grammar: ${e.message}",
                    severity = GrammarValidator.Severity.ERROR
                )
            )
        }
    }
}