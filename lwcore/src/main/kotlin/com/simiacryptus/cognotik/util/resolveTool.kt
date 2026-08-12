package com.simiacryptus.cognotik.util

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

private val log = LoggerFactory.getLogger("ToolResolver")

fun String.resolveTool(root: Path): File? {
  if (isBlank()) {
    log.debug("Tool name is blank; returning null")
    return null
  }
  log.debug("Resolving tool '{}' with root '{}'", this, root)

  // 1. Check relative to provided root
  val rootResolved = root.resolve(this)
  if (rootResolved.exists()) {
    val file = rootResolved.toFile().absoluteFile
    log.debug("Resolved tool '{}' relative to root: {}", this, file)
    return file
  }

  // 2. Check as an absolute or working-directory-relative path
  val direct = File(this)
  if (direct.exists()) {
    val file = direct.absoluteFile
    log.debug("Resolved tool '{}' as direct file: {}", this, file)
    return file
  }

  // 3. Search the system PATH
  val pathResolved = findOnPath(this)
  if (pathResolved != null) {
    log.debug("Resolved tool '{}' on PATH: {}", this, pathResolved)
    return pathResolved
  }

  log.warn("Failed to resolve tool '{}' (root='{}')", this, root)
  return null
}

private fun findOnPath(name: String): File? {
  val pathEnv = System.getenv("PATH") ?: return null
  val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
  val pathExtensions: List<String> = if (isWindows) {
    val pathext = System.getenv("PATHEXT")
    if (pathext.isNullOrBlank()) {
      listOf("", ".EXE", ".BAT", ".CMD", ".COM")
    } else {
      // Always include the no-extension case first in case the name already has one
      listOf("") + pathext.split(File.pathSeparator).filter { it.isNotBlank() }
    }
  } else {
    listOf("")
  }

  for (dir in pathEnv.split(File.pathSeparator)) {
    if (dir.isBlank()) continue
    for (ext in pathExtensions) {
      val candidate = File(dir, name + ext)
      if (candidate.isFile && (isWindows || candidate.canExecute())) {
        return candidate.absoluteFile
      }
    }
  }
  return null
}