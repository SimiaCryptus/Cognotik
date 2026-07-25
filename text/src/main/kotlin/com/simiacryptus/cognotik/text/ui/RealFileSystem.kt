package com.simiacryptus.cognotik.text.ui

import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class RealFileSystem : FileSystem {
  companion object {
    private val log = LoggerFactory.getLogger(RealFileSystem::class.java)
  }

  override fun exists(path: Path): Boolean {
    val result = path.exists()
    log.debug("exists({}): {}", path, result)
    return result
  }

  override fun readText(path: Path): String {
    return if (path.exists()) {
      val content = path.readText(Charsets.UTF_8)
      log.debug("readText({}): {} chars", path, content.length)
      content
    } else {
      log.debug("readText({}): file does not exist, returning empty string", path)
      ""
    }
  }

  override fun writeText(path: Path, content: String) {
    log.debug("writeText({}): {} chars", path, content.length)
    if (content.isEmpty()) {
      log.warn("Writing empty content to file: {}", path)
    }
    path.parent?.toFile()?.mkdirs()
    path.writeText(content, Charsets.UTF_8)
    log.debug("Successfully wrote to file: {}", path)
  }

  override fun resolve(root: Path, relative: String): Path {
    if (relative.isBlank()) {
      log.warn("Blank relative path provided for resolution against root: {}", root)
    }
    val resolved = root.resolve(relative).normalize()
    log.debug("resolve({}, {}): {}", root, relative, resolved)
    return resolved
  }
}