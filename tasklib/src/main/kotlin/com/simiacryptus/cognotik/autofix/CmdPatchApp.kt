package com.simiacryptus.cognotik.autofix

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.platform.ChatInterface
import com.simiacryptus.cognotik.text.patch.PatchProcessor
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.ui.set
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

class CmdPatchApp(
  root: Path,
  settings: Settings,
  val files: Array<out File>?,
  model: ChatInterface,
  fastModel: ChatInterface,
  processor: PatchProcessor,
) : PatchApp(
  root.toFile(),
  settings,
  model,
  fastModel = fastModel,
  processor = processor,
) {

  companion object {
    private val log = getLogger(CmdPatchApp::class.java)

    fun truncate(output: String, kb: Int = 32): String {
      var returnVal = output
      if (returnVal.length > 1024 * 2 * kb) {
        returnVal =
          returnVal.substring(0, 1024 * kb) + "\n\n... Output truncated ...\n\n" + returnVal.substring(
            returnVal.length - 1024 * kb
          )
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

  override fun codeFiles() =
    getFiles(files).filter { it.toFile().length() < 1024 * 1024 / 2 }.map { root.toPath().relativize(it) ?: it }
      .toSet()

  override fun projectSummary(): String {
    log.info("Generating project summary")
    val codeFiles = codeFiles()
    log.debug("Found ${codeFiles.size} code files for project summary")
    val str = codeFiles.asSequence().filter { root.toPath().resolve(it).toFile().exists() }.distinct().sorted()
      .joinToString("\n") { path ->
        "* $path - ${
          root.toPath().resolve(path).toFile().length()
        } bytes".trim()
      }
    log.debug("Project summary generated (${str.length} chars)")
    return str
  }

  override fun output(
    task: ISessionTask, settings: Settings, tabs: TabbedDisplay
  ): OutputResult {
    log.info("Starting command execution with ${settings.commands.size} commands")
    run {
      val model = model.getChildClient(task)
      var exitCode = 0
      for ((index, cmdSettings) in settings.commands.withIndex()) {
        try {
          log.info("Executing command ${index + 1}/${settings.commands.size}: ${cmdSettings.executable} ${cmdSettings.arguments}")
          val cmd = cmdSettings.executable.toString()
          val commandList = when {
            cmd.endsWith(".ps1", ignoreCase = true) -> {
              // If it's a PowerShell script, build the command to run with powershell.exe
              listOf(
                "powershell.exe",
                "-ExecutionPolicy", "Bypass", // Good practice to avoid execution policy issues
                "-File", cmd
              ) + cmdSettings.arguments.split(" ").filter(String::isNotBlank)
            }

            cmd.endsWith(".bat", ignoreCase = true) || cmd.endsWith(".cmd", ignoreCase = true) -> {
              // If it's a batch script, build the command to run with cmd.exe
              listOf(
                "cmd.exe",
                "/c", cmd
              ) + cmdSettings.arguments.split(" ").filter(String::isNotBlank)
            }

            else -> {
              // Original logic for other executables
              listOf(cmd) + cmdSettings.arguments.split(" ").filter(String::isNotBlank)
            }
          }

          val processBuilder = ProcessBuilder(commandList).directory(cmdSettings.workingDirectory)
          processBuilder.environment().putAll(System.getenv())
          val cmdString = processBuilder.command().joinToString(" ")
          log.debug("Full command string: $cmdString")
          log.debug("Working directory: {}", cmdSettings.workingDirectory)
          val task = task.newTask(false).apply { tabs[cmdString] = placeholder }
          task.add("Working Directory: ${cmdSettings.workingDirectory}")
          task.add("Command: $cmdString")
          task.add("Model: $model / $fastModel")
          val process = processBuilder.start()
          task.add("Started at: ${Instant.now()}")
          val cancelButton = task.add(task.hrefLink("Stop") {
            log.info("Process manually stopped by user")
            process.destroy()
          })
          val taskOutput = task.add("")
          val buffer = StringBuilder()
          fun addOutput(taskOutput: StringBuilder?, task: ISessionTask) {
            synchronized(task) {
              log.debug("Updating output display (buffer size: ${buffer.length})")
              val extraInfo =
                "[Verbose Info] - Updated at: ${Instant.now()} | Buffer size: ${buffer.length} chars"
              taskOutput?.set(
                "```\n${truncate(buffer.toString()).indent("  ")}\n\n${extraInfo}\n```".renderMarkdown(
                  true
                )
              )
              task.update()
            }
          }

          fun readStream(stream: InputStream) {
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
              taskOutput?.set(
                "```\n${truncate(buffer.toString()).indent("  ")}\n\n[Process Status] - Running for ${(System.currentTimeMillis() - startTime) / 1000}s | PID: $pid | Alive: ${process.isAlive}\n```".renderMarkdown(
                  true
                )
              )
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
        } catch (e: Throwable) {
          task.error(e)
          return OutputResult(1, "Error executing command: ${e.message}")
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
      .filter { it.readText().contains(searchString, ignoreCase = true) }.map { it.toPath() }.toList()
  }.toSet()

}