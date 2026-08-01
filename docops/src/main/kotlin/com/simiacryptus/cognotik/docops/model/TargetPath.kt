package com.simiacryptus.cognotik.docops.model

import java.io.File

class TargetPath private constructor(val file: File) : Comparable<TargetPath> {

  /** Case-insensitive identity key (sane on Windows/macOS, stable on Linux). */
  val key: String = file.path.lowercase()

  val absolutePath: String get() = file.absolutePath
  val name: String get() = file.name
  val parentFile: File? get() = file.parentFile

  fun isUnder(root: File): Boolean = try {
    val r = root.canonicalFile.path
    val p = file.canonicalFile.path
    p == r || p.startsWith(r + File.separator)
  } catch (_: Exception) {
    false
  }

  fun relativeToOrAbsolute(root: File): String = try {
    file.canonicalFile.relativeTo(root.canonicalFile).path
  } catch (_: Exception) {
    file.absolutePath
  }

  override fun compareTo(other: TargetPath): Int = key.compareTo(other.key)
  override fun equals(other: Any?): Boolean = other is TargetPath && other.key == key
  override fun hashCode(): Int = key.hashCode()
  override fun toString(): String = file.path

  companion object {
    fun of(file: File): TargetPath =
      TargetPath(runCatching { file.canonicalFile }.getOrElse { file.absoluteFile })

    fun of(path: String): TargetPath = of(File(path))
  }
}