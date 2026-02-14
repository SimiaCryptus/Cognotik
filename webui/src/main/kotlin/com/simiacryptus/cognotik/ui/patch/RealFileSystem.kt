package com.simiacryptus.cognotik.ui.patch

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class RealFileSystem : FileSystem {
  override fun exists(path: Path): Boolean {
    return path.exists()
  }

  override fun readText(path: Path): String {
    return if (path.exists()) path.readText(Charsets.UTF_8) else ""
  }

  override fun writeText(path: Path, content: String) {
    path.parent?.toFile()?.mkdirs()
    path.writeText(content, Charsets.UTF_8)
  }

  override fun resolve(root: Path, relative: String): Path {
    return root.resolve(relative).normalize()
  }
}