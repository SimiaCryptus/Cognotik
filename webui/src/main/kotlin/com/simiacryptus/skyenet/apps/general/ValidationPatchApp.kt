package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.core.util.*
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import java.io.File
import java.nio.file.Path
import org.slf4j.LoggerFactory

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
        val tabs = TabbedDisplay(task)
        val paths = getFiles(files)
        paths.forEach { file ->
            val task = ui.newTask(false).apply { tabs[file.toString()] = placeholder }
            
            try {
                val validator = SimpleDiffApplier.getValidator(file.toFile().toString()) ?: return@forEach
                val content = file.toFile().readText()
                val errors = validator.validateGrammar(content)
                if (errors.isNotEmpty()) {
                    errors.forEach {
                        val validationError = ValidationError(
                            file = file,
                            validator = validator::class.simpleName ?: "Unknown",
                            message = it.message
                        )
                        validationErrors.add(validationError)
                    }
                    task.add("Validation failed: ${getValidationMessage(validator)}")
                } else {
                    task.add("Validation passed: ${validator::class.simpleName}")
                }
                task.complete("File passed validation checks.")
            } catch (e: Exception) {
                task.error(null, e)
                log.error("Error validating file: ${file}", e)
                validationErrors.add(
                    ValidationError(
                        file = file,
                        validator = "FileRead",
                        message = "Error reading/validating file: ${e.message}"
                    )
                )
            }
        }

        // Format validation results
        val output = buildString {
            if (validationErrors.isEmpty()) {
                append("All files passed validation checks.")
            } else {
                append("Found ${validationErrors.size} validation errors:\n\n")
                validationErrors.groupBy { it.file }.forEach { (file, errors) ->
                    append("File: ${file}\n")
                    errors.forEach { error ->
                        append("- [${error.validator}] ${error.message}\n")
                    }
                    append("\n")
                }
            }
        }

        task.complete(output)
        return OutputResult(
            exitCode = if (validationErrors.isEmpty()) 0 else 1,
            output = output
        )
    }

    private fun getFiles(virtualFiles: Array<out File>?): Set<Path> {
        val codeFiles = mutableSetOf<Path>()
        FileValidationUtils.expandFileList(*virtualFiles?.map { it.toPath().toFile() }?.toTypedArray() ?: emptyArray()).apply { codeFiles.addAll(this.map { it.toPath() }) }
        virtualFiles?.forEach { file ->
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    file.listFiles()?.let { codeFiles.addAll(getFiles(it)) }
                }
            } else {
                codeFiles.add(file.toPath())
            }
        }
        return codeFiles
    }

    private fun getValidationMessage(validator: GrammarValidator): String {
        return when(validator) {
            is KotlinGrammarValidator -> validationMessages["kotlin"] ?: "Invalid Kotlin syntax"
            is ParenMatchingValidator -> validationMessages["parenthesis"] ?: "Syntax validation failed"
            else -> "Validation failed"
        }
    }

    override fun codeFiles(): Set<Path> = getFiles(files)
        .filter { it.toFile().length() < 1024 * 1024 / 2 } // Limit to 0.5MB
        .map { root.toPath().relativize(it) ?: it }.toSet()

    override fun codeSummary(paths: List<Path>): String {
        val a = paths.map { it.toFile().findAbsolute(settings.workingDirectory, root, File(".")) }
        val b = a.filter { it.exists() && !it.isDirectory && it.length() < (256 * 1024) }
        return b.joinToString("\n\n") { path ->
                try {
                    "# ${path}\n```${path.toString().split('.').lastOrNull()}\n${
                        path.readText()
                    }\n```"
                } catch (e: Exception) {
                    log.warn("Error reading file", e)
                    "Error reading file `${path}` - ${e.message}"
                }
            }
    }

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
        val message: String
    )
}

fun File.findAbsolute(vararg roots: File?): File {
    if (this.absoluteFile.exists()) {
        return this.absoluteFile
    }
    for (root in roots.filterNotNull()) {
        val resolved = root.resolve(this)
        if (resolved.exists()) {
            return resolved
        }
    }
    return this
}
