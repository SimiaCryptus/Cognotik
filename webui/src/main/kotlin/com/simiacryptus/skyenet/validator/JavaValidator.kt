package com.simiacryptus.skyenet.validator

import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit

object JavaValidator : LanguageValidator {
    /**
     * Validates if a given string contains valid Java code.
     *
     * @param code The string containing Java code to validate
     * @return Boolean indicating whether the code is valid Java
     */
    override fun isValid(code: String): Boolean {
        return try {
            val parser = createParser()
            parser.setSource(code.toCharArray())
            val cu = parser.createAST(null) as CompilationUnit

            // If there are no syntax errors, return true
            cu.problems.isEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validates if a given string contains valid Java code and returns any error messages.
     *
     * @param code The string containing Java code to validate
     * @return Pair of Boolean (indicating validity) and String (containing error message if any)
     */
    override fun validateWithErrors(code: String): kotlin.Pair<Boolean, String> {
        return try {
            val parser = createParser()
            parser.setSource(code.toCharArray())
            val cu = parser.createAST(null) as CompilationUnit

            val problems = cu.problems
            if (problems.isNotEmpty()) {
                val errorMessages = StringBuilder()
                for (problem in problems) {
                    errorMessages.append(problem.message)
                            .append(" at line ")
                            .append(problem.sourceLineNumber)
                            .append("\n")
                }
                kotlin.Pair(false, errorMessages.toString())
            } else {
                kotlin.Pair(true, "")
            }
        } catch (e: Exception) {
            kotlin.Pair(false, e.message ?: "Unknown error occurred")
        }
    }

    private fun createParser(): ASTParser {
        val parser = ASTParser.newParser(AST.JLS19)
        val options = JavaCore.getOptions()
      JavaCore.setComplianceOptions(JavaCore.VERSION_19, options)
        parser.setCompilerOptions(options)
        parser.setKind(ASTParser.K_COMPILATION_UNIT)
        parser.setResolveBindings(true)
        parser.setBindingsRecovery(true)
        return parser
    }

    data class Pair<T, U>(val first: T, val second: U)
}