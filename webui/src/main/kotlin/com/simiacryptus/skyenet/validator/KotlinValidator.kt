package com.simiacryptus.skyenet.validator

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

object KotlinValidator : LanguageValidator {
    /**
     * Validates if a given string contains valid Kotlin code.
     *
     * @param code The string containing Kotlin code to validate
     * @return Boolean indicating whether the code is valid Kotlin
     */
    override fun isValid(code: String): Boolean {
        try {
            val configuration = CompilerConfiguration().apply {
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }

            val disposable: Disposable = Disposer.newDisposable()
            val environment = KotlinCoreEnvironment.createForProduction(
              disposable,
              configuration,
              EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            val psiFactory = KtPsiFactory(environment.project)
            val file: KtFile = psiFactory.createFile(code)

            // If we get here without exceptions, the syntax is valid
            disposable.dispose()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Validates if a given string contains valid Kotlin code and returns any error messages.
     *
     * @param code The string containing Kotlin code to validate
     * @return Pair of Boolean (indicating validity) and String (containing error message if any)
     */
    override fun validateWithErrors(code: String): Pair<Boolean, String> {
        try {
            val messageCollector = MessageCollectorWrapper()
            val configuration = CompilerConfiguration().apply {
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            }

            val disposable = Disposer.newDisposable()
            val environment = KotlinCoreEnvironment.createForProduction(
              disposable,
              configuration,
              EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            val psiFactory = KtPsiFactory(environment.project)
            val file: KtFile = psiFactory.createFile(code)

            disposable.dispose()
            return if (messageCollector.hasErrors()) {
                Pair(false, messageCollector.getMessages())
            } else {
                Pair(true, "")
            }
        } catch (e: Exception) {
            return Pair(false, e.message ?: "Unknown error occurred")
        }
    }

    private class MessageCollectorWrapper : MessageCollector {
        private val messages = mutableListOf<String>()
        private var hasErrors = false

        override fun clear() {
            messages.clear()
            hasErrors = false
        }

        override fun hasErrors(): Boolean = hasErrors
        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
            messages.add("$severity: $message${location?.let { " at $it" } ?: ""}")
            if (severity.isError) {
                hasErrors = true
            }
        }

        fun getMessages(): String = messages.joinToString("\n")
    }
}