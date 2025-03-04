package com.simiacryptus.skyenet.core.util

import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.name

class FileValidationUtils {
  companion object {
    fun isCurlyBalanced(code: String): Boolean {
      var count = 0
      for (char in code) {
        when (char) {
          '{' -> count++
          '}' -> count--
        }
        if (count < 0) return false
      }
      return count == 0
    }

    fun isSingleQuoteBalanced(code: String): Boolean {
      var count = 0
      var escaped = false
      for (char in code) {
        when {
          char == '\\' -> escaped = !escaped
          char == '\'' && !escaped -> count++
          else -> escaped = false
        }
      }
      return count % 2 == 0
    }

    fun isSquareBalanced(code: String): Boolean {
      var count = 0
      for (char in code) {
        when (char) {
          '[' -> count++
          ']' -> count--
        }
        if (count < 0) return false
      }
      return count == 0
    }

    fun isParenthesisBalanced(code: String): Boolean {
      var count = 0
      for (char in code) {
        when (char) {
          '(' -> count++
          ')' -> count--
        }
        if (count < 0) return false
      }
      return count == 0
    }

    fun isQuoteBalanced(code: String): Boolean {
      var count = 0
      var escaped = false
      for (char in code) {
        when {
          char == '\\' -> escaped = !escaped
          char == '"' && !escaped -> count++
          else -> escaped = false
        }
      }
      return count % 2 == 0
    }

    fun filteredWalk(
      file: File,
      maxFilesPerDir: Int = 20,
      fn: (File) -> Boolean
    ): List<File> {
      val result = mutableListOf<File>()
      if (fn(file)) {
        if (file.isDirectory) {
          file.listFiles()?.take(maxFilesPerDir)?.forEach { child ->
            result.addAll(filteredWalk(child, maxFilesPerDir, fn))
          }
        } else {
          result.add(file)
        }
      }
      return result
    }

    fun isLLMIncludableFile(file: File): Boolean {
      return when {
        !file.exists() -> false
        file.isDirectory -> false
        file.name.startsWith(".") -> false
        file.name.endsWith(".data") -> true
        file.length() > 1e8 -> false
        isGitignore(file.toPath()) || isLLMIgnored(file.toPath()) -> false
        file.extension.lowercase(Locale.getDefault()) in setOf(
          "jar",
          "zip",
          "class",
          "png",
          "jpg",
          "jpeg",
          "gif",
          "ico",
          "stl"
        ) -> false
        isBinaryFile(file) -> false

        else -> true
      }
    }
    /**
     * Checks if a file is likely to be binary by examining its content
     * @param file The file to check
     * @return true if the file appears to be binary, false otherwise
     */
    fun isBinaryFile(file: File): Boolean {
      if (!file.exists() || file.isDirectory || file.length() == 0L) {
        return false
      }
      // Check common binary file extensions first for efficiency
      val binaryExtensions = setOf(
        "jar", "zip", "class", "png", "jpg", "jpeg", "gif", "ico", "stl",
        "exe", "dll", "so", "dylib", "bin", "dat", "pdf", "doc", "docx", "xls", "xlsx"
      )
      if (file.extension.lowercase(Locale.getDefault()) in binaryExtensions) {
        return true
      }
      // Sample the beginning of the file to check for binary content
      return try {
        file.inputStream().use { input ->
          isBinaryStream(input)
        }
      } catch (e: Exception) {
        // If we can't read the file, assume it's not binary
        false
      }
    }
    /**
     * Checks if an input stream contains binary data by sampling the first bytes
     * @param input The input stream to check
     * @return true if the stream appears to contain binary data, false otherwise
     */
    private fun isBinaryStream(input: InputStream): Boolean {
      val sampleSize = 1000
      val bytes = ByteArray(sampleSize)
      val bytesRead = input.read(bytes, 0, sampleSize)
      if (bytesRead <= 0) return false
      // Count the number of bytes that are outside the printable ASCII range
      // or are control characters other than common whitespace
      var binaryCount = 0
      for (i in 0 until bytesRead) {
        val b = bytes[i].toInt() and 0xFF
        if (b == 0 || (b < 9 && b != 7) || (b > 13 && b < 32) || b >= 127) {
          binaryCount++
        }
      }
      // If more than 10% of the bytes are binary, consider it a binary file
      return binaryCount.toDouble() / bytesRead > 0.1
    }

    fun expandFileList(vararg data: File): Array<File> {
      return data.flatMap {
        (when {
          it.name.startsWith(".") -> arrayOf()
          it.name.endsWith(".data") -> arrayOf(it)
          isGitignore(it.toPath()) || isLLMIgnored(it.toPath()) -> arrayOf()
          it.length() > 1e8 -> arrayOf()
          it.extension.lowercase(Locale.getDefault()) in
              setOf("jar", "zip", "class", "png", "jpg", "jpeg", "gif", "ico") -> arrayOf()
          isBinaryFile(it) -> arrayOf()

          it.isDirectory -> expandFileList(*it.listFiles() ?: arrayOf())
          else -> arrayOf(it)
        }).toList()
      }.toTypedArray()
    }
    
    fun isLLMIgnored(path: Path): Boolean {
      // Check common directories to ignore
      when {
        path.name == "node_modules" -> return true
        path.name == "target" -> return true
        path.name == "build" -> return true
        path.name.startsWith(".") -> return true
      }
      var currentDir = path.toFile().parentFile
      currentDir ?: return false
      while (!currentDir.resolve(".llm").exists()) { // When a '.llm' folder is found, we stop searching up
        currentDir.resolve(".llmignore").let {
          if (it.exists()) {
            val llmignore = it.readText()
            if (llmignore.split("\n").any { line ->
                try {
                  val trimmedLine = line.trim()
                  if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false
                  // Convert .llmignore pattern to regex
                  val regexPattern = "^" + Regex.escape(trimmedLine)
                    .replace("\\*", ".*")
                    .replace("\\?", ".") + "$"
                  return@any path.fileName.toString().matches(Regex(regexPattern))
                } catch (e: Throwable) {
                  return@any false
                }
              }) return true
          }
        }
        currentDir = currentDir.parentFile ?: return false
      }
      // Check the final directory's .llmignore if present
      currentDir.resolve(".llmignore").let {
        if (it.exists()) {
          val llmignore = it.readText()
          if (llmignore.split("\n").any { line ->
              val trimmedLine = line.trim()
              if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false
              val regexPattern = "^" + Regex.escape(trimmedLine)
                .replace("\\*", ".*")
                .replace("\\?", ".") + "$"
              path.fileName.toString().matches(Regex(regexPattern))
            }) {
            return true
          }
        }
      }
      return false
    }

    fun isGitignore(path: Path): Boolean {
      when {
        path.name == "node_modules" -> return true
        path.name == "target" -> return true
        path.name == "build" -> return true
        path.name.startsWith(".") -> return true
      }
      var currentDir = path.toFile().parentFile
      currentDir ?: return false
      while (!currentDir.resolve(".git").exists()) {
        currentDir.resolve(".gitignore").let {
          if (it.exists()) {
            val gitignore = it.readText()
            if (gitignore.split("\n").any { line ->
                try {
                 val trimmedLine = line.trim()
                 if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false
                 // Convert gitignore pattern to regex
                 val regexPattern = "^" + Regex.escape(trimmedLine)
                   .replace("\\*", ".*")
                   .replace("\\?", ".") + "$"
                 return@any path.fileName.toString().matches(Regex(regexPattern))
                } catch (e: Throwable) {
                  return@any false
                }
              }) return true
          }
        }
        currentDir = currentDir.parentFile ?: return false
      }
      // After .git is found, check the final directory's .gitignore
      currentDir.resolve(".gitignore").let {
        if (it.exists()) {
          val gitignore = it.readText()
          if (gitignore.split("\n").any { line ->
              val trimmedLine = line.trim()
              if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) return@any false
              val regexPattern = "^" + Regex.escape(trimmedLine)
                .replace("\\*", ".*")
                .replace("\\?", ".") + "$"
              path.fileName.toString().matches(Regex(regexPattern))
            }) {
            return true
          }
        }
      }
      return false
    }
  }
}