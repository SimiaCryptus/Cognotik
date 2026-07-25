package com.simiacryptus.cognotik.text.ui

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class InMemoryFileSystem : FileSystem {
  private val files = ConcurrentHashMap<String, String>()

  override fun exists(path: Path): Boolean {
    return files.containsKey(path.normalize().toString())
  }

  override fun readText(path: Path): String {
    return files[path.normalize().toString()] ?: ""
  }

  override fun writeText(path: Path, content: String) {
    files[path.normalize().toString()] = content
  }

  override fun resolve(root: Path, relative: String): Path {
    return root.resolve(relative).normalize()
  }
}