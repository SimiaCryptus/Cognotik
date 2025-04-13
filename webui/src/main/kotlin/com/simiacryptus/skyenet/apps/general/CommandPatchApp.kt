package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.util.FileSelectionUtils
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import java.io.File
import java.nio.file.Path

class CommandPatchApp(
  root: File,
  session: Session,
  settings: Settings,
  api: ChatClient,
  model: ChatModel,
  parsingModel: ChatModel,
  private val files: Array<out File>?,
  val command: String,
) : PatchApp(root, settings, api, model, parsingModel) {
  override fun codeFiles() = getFiles(files)
    .filter { it.toFile().length() < 1024 * 1024 / 2 } // Limit to 0.5MB
    .map { root.toPath().relativize(it) ?: it }.toSet()

  override fun output(
    task: SessionTask,
    settings: Settings,
    ui: ApplicationInterface,
    tabs: TabbedDisplay
  ) = OutputResult(
    exitCode = 1,
    output = command
  )

  override fun projectSummary(): String {
    val codeFiles = codeFiles()
    return codeFiles
      .asSequence()
      .map { it.toFile().findAbsolute(settings.workingDirectory, root, File(".")) }
      .filter { it.exists() }.distinct().sorted()
      .joinToString("\n") { path ->
        "* ${path} - ${
          settings.workingDirectory?.resolve(path)?.length() ?: "?"
        } bytes".trim()
      }
  }

  override fun searchFiles(searchStrings: List<String>): Set<Path> {
    return searchStrings.flatMap { searchString ->
      FileSelectionUtils.filteredWalk(settings.workingDirectory!!) { !FileSelectionUtils.isGitignore(it.toPath()) }
        .filter { FileSelectionUtils.isLLMIncludableFile(it) }
        .filter { it.readText().contains(searchString, ignoreCase = true) }
        .map { it.toPath() }
        .toList()
    }.toSet()
  }

  companion object {
    fun getFiles(
      files: Array<out File>?
    ): MutableSet<Path> {
      val codeFiles = mutableSetOf<Path>()    // Set to avoid duplicates
      files?.forEach { file ->
        if (file.isDirectory) {
          if (file.name.startsWith(".")) return@forEach
          if (FileSelectionUtils.isGitignore(file.toPath())) return@forEach
          if (file.name.endsWith(".png")) return@forEach
          if (file.length() > 1024 * 256) return@forEach
          codeFiles.addAll(getFiles(file.listFiles()))
        } else {
          codeFiles.add((file.toPath()))
        }
      }
      return codeFiles
    }
  }
}