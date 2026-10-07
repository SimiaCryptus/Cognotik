package com.simiacryptus.cognotik.util

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.simiacryptus.cognotik.text.validate.GrammarValidator
import org.slf4j.LoggerFactory

/**
 * Validates code snippets using IntelliJ's PSI parsers. Parsing happens under a read action only;
 * nothing is written, and no undo history is recorded.
 */
class IntelliJPsiValidator(
    private val project: Project,
    val extension: String,
    val filename: String
) : GrammarValidator {

    override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> {
        if (project.isDisposed) return emptyList()
        val fileType = resolveFileType() ?: return emptyList()
        return try {
            ReadAction.compute<List<GrammarValidator.ValidationError>, RuntimeException> {
                val psiFile = PsiFileFactory.getInstance(project).createFileFromText(validationFileName(), fileType, code)
                collectErrors(psiFile, code)
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            log.warn("PSI validation failed for $filename", e)
            listOf(
                GrammarValidator.ValidationError(
                    message = "Error validating ${SUPPORTED_LANGUAGES[normalizedExtension()] ?: extension} grammar: ${e.message}",
                    severity = GrammarValidator.Severity.ERROR
                )
            )
        }
    }

    private fun normalizedExtension() = extension.removePrefix(".").lowercase()

    /** Returns null when IntelliJ has no real parser for this extension (avoids false "valid" results). */
    private fun resolveFileType(): FileType? =
        FileTypeRegistry.getInstance().getFileTypeByExtension(normalizedExtension())
            .takeUnless { it is UnknownFileType || it is PlainTextFileType || it.isBinary }

    private fun validationFileName(): String =
        filename.substringAfterLast('/').substringAfterLast('\\').ifBlank { "validation.${normalizedExtension()}" }

    private fun collectErrors(psiFile: PsiFile, text: String): List<GrammarValidator.ValidationError> {
        val lineStarts = lineStartOffsets(text)
        val errors = mutableListOf<GrammarValidator.ValidationError>()
        psiFile.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitErrorElement(element: PsiErrorElement) {
                val offset = element.textRange.startOffset
                val lineIndex = lineIndexFor(lineStarts, offset)
                errors.add(
                    GrammarValidator.ValidationError(
                        message = element.errorDescription,
                        line = lineIndex + 1,
                        column = offset - lineStarts[lineIndex] + 1,
                        severity = GrammarValidator.Severity.ERROR
                    )
                )
                super.visitErrorElement(element)
            }
        })
        return errors
    }

    companion object {
        private val log = LoggerFactory.getLogger(IntelliJPsiValidator::class.java)

        private val SUPPORTED_LANGUAGES = mapOf(
            "kt" to "Kotlin", "java" to "Java", "py" to "Python", "js" to "JavaScript", "jsx" to "JavaScript",
            "ts" to "TypeScript", "tsx" to "TypeScript", "go" to "Go", "rs" to "Rust", "cpp" to "C++",
            "c" to "C", "cs" to "C#", "scala" to "Scala", "rb" to "Ruby", "php" to "PHP", "swift" to "Swift",
            "vue" to "Vue", "html" to "HTML", "css" to "CSS", "scss" to "SCSS", "sass" to "SASS",
            "less" to "LESS", "json" to "JSON", "xml" to "XML", "yaml" to "YAML", "yml" to "YAML",
            "md" to "Markdown"
        )

        fun isLanguageSupported(extension: String?): Boolean =
            extension?.removePrefix(".")?.lowercase()?.let { SUPPORTED_LANGUAGES.containsKey(it) } ?: false

        private fun lineStartOffsets(text: String): List<Int> {
            val starts = mutableListOf(0)
            text.forEachIndexed { i, c -> if (c == '\n') starts.add(i + 1) }
            return starts
        }

        private fun lineIndexFor(lineStarts: List<Int>, offset: Int): Int {
            val idx = lineStarts.binarySearch(offset)
            return (if (idx >= 0) idx else -idx - 2).coerceAtLeast(0)
        }
    }
}