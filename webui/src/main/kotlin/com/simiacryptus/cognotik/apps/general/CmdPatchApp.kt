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
        log.debug("Getting files from ${virtualFiles?.size ?: 0} input files")
        val codeFiles = mutableSetOf<Path>()
        virtualFiles?.forEach { file ->
            if (file.isDirectory) {
                if (file.name.startsWith(".")) return@forEach
                if (FileSelectionUtils.isGitignore(file.toPath())) return@forEach
                log.debug("Scanning directory: ${file.absolutePath}")
                codeFiles.addAll(getFiles(file.listFiles()))
            } else {
                log.debug("Adding file: ${file.absolutePath}")
                codeFiles.add((file.toPath()))
            }
        }
        log.debug("Found ${codeFiles.size} code files")
        return codeFiles
    }

    override fun codeFiles() = getFiles(files)
        .filter { it.toFile().length() < 1024 * 1024 / 2 }
        .map { root.toPath().relativize(it) ?: it }.toSet()

    override fun projectSummary(): String {
        log.info("Generating project summary")
        val codeFiles = codeFiles()
        log.debug("Found ${codeFiles.size} code files for project summary")
        val str = codeFiles
            .asSequence()
            .filter { root.toPath().resolve(it).toFile().exists() }
            .distinct().sorted()
            .joinToString("\n") { path ->
                "* ${path} - ${
                    root.toPath().resolve(path).toFile().length() ?: "?"
                } bytes".trim()
            }
        log.debug("Project summary generated (${str.length} chars)")
        return str
    }

    override fun output(
        task: SessionTask,
        settings: Settings,
        ui: ApplicationInterface,
        tabs: TabbedDisplay
    ): OutputResult {
        log.info("Starting command execution with ${settings.commands.size} commands")
        run {
            var exitCode = 0
            for ((index, cmdSettings) in settings.commands.withIndex()) {
                log.info("Executing command ${index+1}/${settings.commands.size}: ${cmdSettings.executable} ${cmdSettings.arguments}")
                val processBuilder = ProcessBuilder(
                    listOf(cmdSettings.executable.toString()) + cmdSettings.arguments.split(" ")
                        .filter(String::isNotBlank)
                ).directory(cmdSettings.workingDirectory)
                processBuilder.environment().putAll(System.getenv())
                val cmdString = processBuilder.command().joinToString(" ")
                log.debug("Full command string: $cmdString")
                log.debug("Working directory: ${cmdSettings.workingDirectory}")
                val task = ui.newTask(false).apply { tabs[cmdString] = placeholder }
                task.add("Working Directory: ${cmdSettings.workingDirectory}")
                task.add("Command: ${cmdString}")
                task.add("Model: ${model} / ${parsingModel}")
                val process = processBuilder.start()
                task.add("Started at: ${java.time.Instant.now()}")
                val cancelButton = task.add(task.hrefLink("Stop") {
                    log.info("Process manually stopped by user")
                    process.destroy()
                })
                val taskOutput = task.add("")
                val buffer = StringBuilder()
                fun addOutput(taskOutput: StringBuilder?, task: SessionTask) {
                    synchronized(task) {
                        log.debug("Updating output display (buffer size: ${buffer.length})")
                        val extraInfo =
                            "[Verbose Info] - Updated at: ${java.time.Instant.now()} | Buffer size: ${buffer.length} chars"
                        taskOutput?.set("```\n${truncate(buffer.toString()).indent("  ")}\n\n${extraInfo}\n```".renderMarkdown)
                        task.update()
                    }
                }

                fun readStream(stream: java.io.InputStream) {
                    var lastUpdate = 0L
                    try {
                        log.debug("Starting stream reader thread")
                        stream.bufferedReader().use { reader ->
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isBlank()) continue
                                buffer.append(line).append("\n")
                                if (lastUpdate + TimeUnit.SECONDS.toMillis(15) < System.currentTimeMillis()) {
                                    log.debug("Periodic output update (${buffer.length} chars)")
                                    addOutput(taskOutput, task)
                                    lastUpdate = System.currentTimeMillis()
                                }
                            }
                        }
                    } finally {
                        log.debug("Stream reader thread completed")
                        addOutput(taskOutput, task)
                    }
                }
                Thread { readStream(process.errorStream) }.start()
                Thread { readStream(process.inputStream) }.start()

                val startTime = System.currentTimeMillis()
                val timeoutMillis = TimeUnit.MINUTES.toMillis(5)
                val checkIntervalSeconds = 15L
                var processCompleted = false
                
                while (System.currentTimeMillis() - startTime < timeoutMillis) {
                    if (process.waitFor(checkIntervalSeconds, TimeUnit.SECONDS)) {
                        processCompleted = true
                        break
                    }
                    
                    // Log process status every interval
                    log.info("Process still running after ${(System.currentTimeMillis() - startTime) / 1000} seconds")
                    try {
                        val pid = process.pid()
                        log.info("Process PID: $pid, alive: ${process.isAlive}")
                        
                        // Log memory usage if available
                        val runtime = Runtime.getRuntime()
                        log.info("JVM Memory - Total: ${runtime.totalMemory() / 1024 / 1024}MB, Free: ${runtime.freeMemory() / 1024 / 1024}MB")
                        
                        // Add diagnostic info to the task output
                        taskOutput?.set("```\n${truncate(buffer.toString()).indent("  ")}\n\n[Process Status] - Running for ${(System.currentTimeMillis() - startTime) / 1000}s | PID: $pid | Alive: ${process.isAlive}\n```".renderMarkdown)
                        task.update()
                    } catch (e: Exception) {
                        log.warn("Failed to get process diagnostics", e)
                    }
                }
                
                if (!processCompleted) {
                    log.warn("Process timed out after 5 minutes")
                    process.destroy()
                    cancelButton?.clear()
                    throw RuntimeException("Process timed out after 5 minutes")
                } else {
                    exitCode = process.exitValue()
                    log.info("Process completed with exit code: $exitCode")
                    cancelButton?.clear()
                    task.update()
                    if (exitCode != 0) {
                        log.info("Command failed with exit code $exitCode, returning output")
                        return OutputResult(exitCode, outputString(buffer))
                    }
                }
            }
        }
        log.info("All commands completed successfully")
        return OutputResult(0, "All commands completed successfully")
    }

    private fun outputString(buffer: StringBuilder): String {
        log.debug("Processing output string (${buffer.length} chars)")
        var output = buffer.toString()
        output = output.replace(Regex("\\x1B\\[[0-?]*[ -/]*[@-~]"), "")

        output = truncate(output)
        log.debug("Processed output string (${output.length} chars)")
        return output
    }

    override fun searchFiles(searchStrings: List<String>) = searchStrings.flatMap { searchString ->
        log.debug("Searching for pattern: $searchString")
        FileSelectionUtils.filteredWalk(settings.workingDirectory!!)
            .filter { it.readText().contains(searchString, ignoreCase = true) }
            .map { it.toPath() }
            .toList()
    }.toSet()
}