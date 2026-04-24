package com.simiacryptus.cognotik.util

import java.io.File

fun File.relativeTo_smart(root: File): File {
  val canonicalRelativeTo = try {
    this.relativeTo(root)
  } catch (e: IllegalArgumentException) {
    null
  }?.path?.split(File.separator)?.toMutableList() ?: mutableListOf()
  val rootList = root.path.split(File.separator).toMutableList()
  while(canonicalRelativeTo.isNotEmpty() && rootList.isNotEmpty() && canonicalRelativeTo.first() == rootList.last()) {
    canonicalRelativeTo.removeAt(0)
    rootList.removeAt(rootList.size - 1)
  }
  val relativePath = File(canonicalRelativeTo.joinToString(File.separator))
  return relativePath
}