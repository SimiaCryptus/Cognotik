package com.simiacryptus.cognotik.util

import java.io.File
import java.nio.file.Path

fun Array<Path>.commonRoot(): Path = when {
  isEmpty() -> error("No paths")
  size == 1 && first().toFile().isFile -> first().parent
  size == 1 -> first()
  else -> this.reduce { a, b ->
    when {
      a.startsWith(b) -> b
      b.startsWith(a) -> a
      else -> when (val common = a.commonPrefixWith(b)) {
        a -> a
        b -> b
        else -> common.toAbsolutePath()
      }
    }
  }
}

private fun Path.commonPrefixWith(b: Path): Path {
  val a = this
  val aParts = a.toAbsolutePath().toString().split(File.separator)
  val bParts = b.toAbsolutePath().toString().split(File.separator)
  val common = aParts.zip(bParts).takeWhile { (a, b) -> a == b }.map { it.first }
  return File(File.separator + common.joinToString(File.separator)).toPath()
}

