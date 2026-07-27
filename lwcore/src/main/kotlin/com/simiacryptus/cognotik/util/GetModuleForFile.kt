package com.simiacryptus.cognotik.util

import java.io.File

fun getModuleRootForFile(file: File): File {
  if (file.isFile) {
    return getModuleRootForFile(file.parentFile)
  }
  var current = file
  do {
    if (current.resolve(".git").exists()) {
      return current
    }
    current = current.parentFile ?: break
  } while (true)
  return file
}