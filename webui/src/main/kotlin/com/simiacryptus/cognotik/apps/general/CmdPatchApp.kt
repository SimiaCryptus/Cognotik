package com.simiacryptus.cognotik.apps.general


import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.set
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ChatModel
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

val String.renderMarkdown: String get() = MarkdownUtil.renderMarkdown(this)
fun String.renderMarkdown(tabs: Boolean = false): String = MarkdownUtil.renderMarkdown(this, tabs = tabs)

class CmdPatchApp(
    root: Path,
    settings: Settings,
    api: ChatClient,
    val files: Array<out File>?,
    model: ChatModel,
    parsingModel: ChatModel,
) : PatchApp(root.toFile(), settings, api, model, parsingModel = parsingModel) {

    companion object {
        private val log = LoggerFactory.getLogger(CmdPatchApp::class.java)

        fun truncate(output: String, kb: Int = 32): String {
            var returnVal = output
            if (returnVal.length > 1024 * 2 * kb) {
                returnVal = returnVal.substring(0, 1024 * kb) +
                        "\n\n... Output truncated ...\n\n" +
                        returnVal.substring(returnVal.length - 1024 * kb)
            }
            return returnVal
        }
    }

    private fun getFiles(
        virtualFiles: Array<out File>?
    ): MutableSet<Path> {
        val codeFiles = mutableSetOf<Path>()    // Set to avoid duplicates
        virtualFiles?.forEach { file ->
            if (file.isDirectory) {
                if (file.name.startsWith(".")) return@forEach
                if (FileSelectionUtils.isGitignore(file.toPath())) return@forEach
                codeFiles.addAll(getFiles(file.listFiles()))
            } else {
                codeFiles.add((file.toPath()))
            }
        }
        return codeFiles
    }

    override fun codeFiles() = getFiles(files)
        .filter { it.toFile().length() < 1024 * 1024 / 2 } // Limit to 0.5MB
        .map { root.toPath().relativize(it) ?: it }.toSet()


    private val tripleTilde = "```"

    override fun projectSummary(): String {
        val codeFiles = codeFiles()
        val str = codeFiles
            .asSequence()
            .filter { root.toPath().resolve(it).toFile().exists() }
            .distinct().sorted()
            .joinToString("\n") { path ->
                "* ${path} - ${
                    root.toPath().resolve(path).toFile().length() ?: "?"
                } bytes".trim()
            }
        return str
    }

    override fun output(
        task: SessionTask,
        settings: Settings,
        ui: ApplicationInterface,
        tabs: TabbedDisplay
    ): OutputResult {
        run {
            var exitCode = 0
            for ((index, cmdSettings) in settings.commands.withIndex()) {
                val processBuilder = ProcessBuilder(
                    listOf(cmdSettings.executable.absolutePath) + cmdSettings.arguments.split(" ")
                        .filter(String::isNotBlank)
                ).directory(cmdSettings.workingDirectory)
                processBuilder.environment().putAll(System.getenv())
                val cmdString = processBuilder.command().joinToString(" ")
                val task = ui.newTask(false).apply { tabs[cmdString] = placeholder }
                task.add("Working Directory: ${cmdSettings.workingDirectory}")
                task.add("Command: ${cmdString}")
                task.add("Model: ${model} / ${parsingModel}")
                val process = processBuilder.start()
                task.add("Started at: ${java.time.Instant.now()}")
                val cancelButton = task.add(task.hrefLink("Stop") {
                    process.destroy()
                })
                val taskOutput = task.add("")
                val buffer = StringBuilder()
                fun addOutput(taskOutput: StringBuilder?, task: SessionTask) {
                    synchronized(task) {
                        val extraInfo =
                            "[Verbose Info] - Updated at: ${java.time.Instant.now()} | Buffer size: ${buffer.length} chars"
                        taskOutput?.set("```\n${truncate(buffer.toString()).indent("  ")}\n\n${extraInfo}\n```".renderMarkdown)
                        task.update()
                    }
                }

                // Extracted function to read the process stream (error or input)
                fun readStream(stream: java.io.InputStream) {
                    var lastUpdate = 0L
                    stream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            buffer.append(line).append("\n")
                            if (lastUpdate + TimeUnit.SECONDS.toMillis(15) < System.currentTimeMillis()) {
                                addOutput(taskOutput, task)
                                lastUpdate = System.currentTimeMillis()
                            }
                        }
                    }
                    addOutput(taskOutput, task)
                    cancelButton?.clear()
                }
                Thread { readStream(process.errorStream) }.start()
                Thread { readStream(process.inputStream) }.start()


                if (!process.waitFor(5, TimeUnit.MINUTES)) {
                    process.destroy()
                    cancelButton?.clear()
                    throw RuntimeException("Process timed out")
                }
                exitCode = process.exitValue()
                cancelButton?.clear()
                task.update()
                if (exitCode != 0) return OutputResult(exitCode, outputString(buffer))
            }
        }
        return OutputResult(1, "No commands to execute")
    }


    private fun outputString(buffer: StringBuilder): String {
        var output = buffer.toString()
        output = output.replace(Regex("\\x1B\\[[0-?]*[ -/]*[@-~]"), "") // Remove terminal escape codes
        output = truncate(output)
        return output
    }

    override fun searchFiles(searchStrings: List<String>) = searchStrings.flatMap { searchString ->
        FileSelectionUtils.filteredWalk(settings.workingDirectory!!) { !FileSelectionUtils.isGitignore(it.toPath()) }
            .filter { FileSelectionUtils.isLLMIncludableFile(it) }
            .filter { it.readText().contains(searchString, ignoreCase = true) }
            .map { it.toPath() }
            .toList()
    }.toSet()
}