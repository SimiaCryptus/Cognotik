package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.core.util.FileSelectionUtils
import com.simiacryptus.skyenet.core.util.SimpleDiffApplier
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class ValidationPatchApp(
    root: File,
    settings: Settings,
    api: ChatClient,
    val files: Array<out File>?,
    model: ChatModel,
    parsingModel: ChatModel,
) : PatchApp(root, settings, api, model, parsingModel) {

    companion object {
        private val log = LoggerFactory.getLogger(ValidationPatchApp::class.java)

        // Validation error messages
        private val validationMessages = mapOf(
            "curly" to "Unbalanced curly braces",
            "square" to "Unbalanced square brackets",
            "parenthesis" to "Unbalanced parentheses",
            "quote" to "Unbalanced quotes",
            "singleQuote" to "Unbalanced single quotes",
            "kotlin" to "Invalid Kotlin syntax"
        )
    }

    override fun output(
        task: SessionTask,
        settings: Settings,
        ui: ApplicationInterface,
        tabs: TabbedDisplay
    ): OutputResult {
        val validationErrors = mutableListOf<ValidationError>()
        // Use the provided tabs instance; avoid shadowing variable names.
        val filePaths = getFiles(files)
        filePaths.forEach { file ->
            val fileTask = ui.newTask(false).apply { tabs[file.toString()] = placeholder }
            try {
                val validator = SimpleDiffApplier.getValidator(file.toFile().toString()) ?: return@forEach
                val content = file.toFile().readText()
                val errors = validator.validateGrammar(content)
                if (errors.isNotEmpty()) {
                    errors.forEach {
                        val validationError = ValidationError(
                            file = file,
                            validator = validator::class.simpleName ?: "Unknown",
                            message = it.message,
                            line = it.line ?: 0,
                            column = it.column ?: 0
                        )
                        validationErrors.add(validationError)
                    }
                    fileTask.add("Validation failed: ${errors.joinToString("\n") { "* " + it.message }}".renderMarkdown)
                } else {
                    fileTask.add("Validation passed: ${validator::class.simpleName}")
                }
                if (errors.isEmpty()) tabs.delete(file.toString())
                fileTask.complete("File passed validation checks.")
            } catch (e: Exception) {
                fileTask.error(null, e)
                log.error("Error validating file: $file", e)
                validationErrors.add(
                    ValidationError(
                        file = file,
                        validator = "FileRead",
                        message = "Error reading/validating file: ${e.message}",
                        line = 0,
                        column = 0
                    )
                )
            }
        }
        val output = buildString {
            if (validationErrors.isEmpty()) {
                append("All files passed validation checks.")
            } else {
                append("Found ${validationErrors.size} validation errors:\n\n")
                validationErrors.groupBy { it.file }.forEach { (file, errors) ->
                    append("File: $file\n")
                    errors.forEach { error ->
                        append("- [${error.validator}] Line ${error.line}, Column ${error.column}: ${error.message}\n")
                    }
                    append("\n")
                }
            }
        }

        task.complete(output.renderMarkdown)
        return OutputResult(
            exitCode = if (validationErrors.isEmpty()) 0 else 1,
            output = output,
            errors = ParsedErrors(validationErrors.groupBy { it.file }.map { (file, errors) ->
                ParsedError(
                    message = file.toString(),
                    details = errors.joinToString("\n") { "- [${it.validator}] Line ${it.line}, Column ${it.column}: ${it.message}" },
                    severity = 5,
                    complexity = 5,
                    research = ResearchNotes(
                        fixFiles = listOf(file.toString())
                    ),
                    locations = errors.map { CodeLocation(it.file.toString(), listOf(it.line)) }
                )
            })
        )

    }
    
    private fun getFiles(virtualFiles: Array<out File>?): Set<Path> {
        val codeFiles = mutableSetOf<Path>()
        codeFiles.addAll(
          FileSelectionUtils.expandFileList(*(virtualFiles?.toList()?.toTypedArray() ?: emptyArray()))
                .map { it.toPath() }
        )
        return codeFiles
    }
    
    override fun codeFiles(): Set<Path> = getFiles(files)
        .filter { it.toFile().length() < 1024 * 1024 / 2 } // Limit to 0.5MB
        .map { root.toPath().relativize(it) ?: it }.toSet()
    
    override fun projectSummary(): String = codeFiles()
        .asSequence()
        .filter { root.toPath().resolve(it).toFile().exists() }
        .distinct()
        .sorted()
        .joinToString("\n") { path ->
            "* ${path} - ${root.toPath().resolve(path).toFile().length() ?: "?"} bytes"
        }

    override fun searchFiles(searchStrings: List<String>): Set<Path> = 
        searchStrings.flatMap { searchString ->
            getFiles(files)
                .filter { it.toFile().readText().contains(searchString, ignoreCase = true) }
                .toList()
        }.toSet()

    data class ValidationError(
        val file: Path,
        val validator: String,
        val message: String,
        val line: Int = 0,
        val column: Int = 0
    )
}

fun File.findAbsolute(vararg roots: File?): File {
    if (this.absoluteFile.exists()) {
        return this.absoluteFile
    }
    if (this.isAbsolute) return File(toString().trimStart('/')).findAbsolute(*roots)
    for (root in roots.filterNotNull()) {
        val resolved = root.resolve(this)
        if (resolved.exists()) {
            return resolved
        }
    }
    return this
}