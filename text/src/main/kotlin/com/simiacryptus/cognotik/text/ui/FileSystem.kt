package com.simiacryptus.cognotik.text.ui

import java.nio.file.Path

interface FileSystem {
  fun exists(path: Path): Boolean
  fun readText(path: Path): String
  fun writeText(path: Path, content: String)
  fun resolve(root: Path, relative: String): Path
}