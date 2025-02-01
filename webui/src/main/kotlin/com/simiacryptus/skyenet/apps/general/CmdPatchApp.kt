package com.simiacryptus.skyenet.apps.general


import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.core.util.FileValidationUtils
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class CmdPatchApp(
  root: Path,
  settings: Settings,
  api: ChatClient,
  val files: Array<out File>?,
  model: ChatModel
) : PatchApp(root.toFile(), settings, api, model) {
  private var stopRequested = false

  companion object {
    private val log = LoggerFactory.getLogger(CmdPatchApp::class.java)

    val String.htmlEscape: String
      get() = this.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

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
        if (FileValidationUtils.isGitignore(file.toPath())) return@forEach
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

  override fun codeSummary(paths: List<Path>): String = paths
    .filter {
      val file = settings.workingDirectory?.resolve(it.toFile())
      file?.exists() == true && !file.isDirectory && file.length() < (256 * 1024)
    }
    .joinToString("\n\n") { path ->
      try {
        "# ${path}\n${tripleTilde}${path.toString().split('.').lastOrNull()}\n${
          settings.workingDirectory?.resolve(path.toFile())?.readText(Charsets.UTF_8)
        }\n${tripleTilde}"
      } catch (e: Exception) {
        log.warn("Error reading file", e)
        "Error reading file `${path}` - ${e.message}"
      }
    }

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

  override fun output(task: SessionTask, settings: Settings): OutputResult = run {
    var exitCode = 0
    lateinit var buffer: StringBuilder
    for ((index, cmdSettings) in settings.commands.withIndex()) {
      buffer = StringBuilder()
      val details = StringBuilder()
      val processBuilder = ProcessBuilder(
        (listOf(cmdSettings.executable.absolutePath) + cmdSettings.arguments.split(" ").filter(String::isNotBlank)).apply {
          details.appendLine(withIndex().joinToString("\n") { "${it.index}: ${it.value}" })
        }
      ).directory(cmdSettings.workingDirectory.apply {
        details.appendLine("Working Directory: ${this}")
      })
      processBuilder.environment().putAll(System.getenv().apply {
        details.appendLine(entries.joinToString("\n") { "${it.key}=${it.value}" })
      })
      task.header(processBuilder.command().joinToString(" "))
      val taskOutput = task.add("Executing command ${index + 1}/${settings.commands.size}")
      log.info("Executing command ${index + 1}/${settings.commands.size}:\n  ${details.toString().replace("\n", "\n  ")}")
      val process = processBuilder.start()
      Thread {
        var lastUpdate = 0L
        process.errorStream.bufferedReader().use { reader ->
          var line: String?
          while (reader.readLine().also { line = it } != null) {
            buffer.append(line).append("\n")
            if (lastUpdate + TimeUnit.SECONDS.toMillis(15) < System.currentTimeMillis()) {
              taskOutput?.set("<pre>\n${truncate(buffer.toString())}\n</pre>")
              task.update()
              lastUpdate = System.currentTimeMillis()
            }
          }
          task.update()
        }
      }.start()
      process.inputStream.bufferedReader().use { reader ->
        var line: String?
        var lastUpdate = 0L
        while (reader.readLine().also { line = it } != null) {
          buffer.append(line).append("\n")
          if (lastUpdate + TimeUnit.SECONDS.toMillis(15) < System.currentTimeMillis()) {
            taskOutput?.set("<pre>\n${truncate(buffer.toString())}\n</pre>")
            task.update()
            lastUpdate = System.currentTimeMillis()
          }
        }
        task.update()
      }
      if (!process.waitFor(5, TimeUnit.MINUTES)) {
        process.destroy()
        throw RuntimeException("Process timed out")
      }
      exitCode = process.exitValue()
      if (exitCode != 0) break
    }
    task.complete()
    val output = outputString(buffer)
    return OutputResult(exitCode, output)
  }

  fun stop() {
    stopRequested = true
  }

  private fun outputString(buffer: StringBuilder): String {
    var output = buffer.toString()
    output = output.replace(Regex("\\x1B\\[[0-?]*[ -/]*[@-~]"), "") // Remove terminal escape codes
    output = truncate(output)
    return output
  }

  override fun searchFiles(searchStrings: List<String>) = searchStrings.flatMap { searchString ->
    FileValidationUtils.filteredWalk(settings.workingDirectory!!) { !FileValidationUtils.isGitignore(it.toPath()) }
      .filter { FileValidationUtils.isLLMIncludableFile(it) }
      .filter { it.readText().contains(searchString, ignoreCase = true) }
      .map { it.toPath() }
      .toList()
  }.toSet()
}