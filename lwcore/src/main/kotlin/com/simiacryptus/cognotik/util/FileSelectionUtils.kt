package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.docs.isDocumentFile
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.name

private const val MAX_TEXT_SIZE = 1024 * 1024

object FileSelectionUtils {
  val log = getLogger(FileSelectionUtils::class.java)

  fun isLLMTextFile(file: File, treatDocumentsAsText: Boolean = false): Boolean {
    return when {
      !file.exists() -> false
      file.isDirectory -> false
      file.name.endsWith(".data") -> true
      treatDocumentsAsText && file.isDocumentFile() -> true
      file.length() > 100_000_000L -> false // 100MB limit
      isGitignore(file.toPath()) -> false
      isLLMIgnored(file.toPath()) -> false
      file.extension.lowercase(Locale.getDefault()) in FileExtensions.BINARY_EXTENSIONS -> false

      isBinaryFile(file) -> false

      else -> true
    }
  }

  fun getAvailableFiles(
    path: Path,
    treatDocumentsAsText: Boolean = false,
  ): List<String> {
    return try {
      listOf(
        filteredWalkAsciiTree(
          path.toFile(),
          20,
          treatDocumentsAsText = treatDocumentsAsText,
          render = { file: File ->
            val name = file.name
            val size: String? = if (file.isFile) {
              val length = file.length()
              when {
                length < 1024 -> "$length B"
                length < 1024 * 1024 -> String.format("%.2f KB", length / 1024.0)
                length < 1024 * 1024 * 1024 -> String.format("%.2f MB", length / (1024.0 * 1024.0))
                else -> String.format("%.2f GB", length / (1024.0 * 1024.0 * 1024.0))
              }
            } else {
              null
            }
            if (size != null) "$name ($size)" else name
          }
        )
      )
    } catch (e: Exception) {
      log.error("Error listing available files", e)
      listOf("Error listing files: ${e.message}")
    }
  }

  fun filteredWalkAsciiTree(
    rootFile: File,
    maxFilesPerDir: Int = 20,
    treatDocumentsAsText: Boolean = false,
    filter: (File) -> Boolean = { !isLLMIgnored(it.toPath()) },
    render: (File) -> String = { it.name }
  ): String {
    val sb = StringBuilder()
    val filterFn = if (treatDocumentsAsText) {
      { file: File -> filter(file) && file.isDocumentFile() }
    } else filter
    if (!filterFn(rootFile)) {
      log.debug("Skipping root file for tree: ${rootFile.absolutePath}")
      return "" // Root itself doesn't match, so empty tree
    }
    if (rootFile.isDirectory) {
      val children = rootFile.listFiles()?.toList() ?: emptyList()
      val entriesToConsider = children.take(maxFilesPerDir)
      entriesToConsider.forEachIndexed { index, child ->
        buildAsciiSubTree(
          child, "", // Initial parentContinuationPrefix for children of the root
          index == entriesToConsider.size - 1, maxFilesPerDir, filterFn, sb, render
        )
      }
    } else {
      // If rootFile is not a directory, just show its name
      sb.append(render(rootFile))
      sb.appendLine()
    }
    return sb.toString()
  }

  private fun buildAsciiSubTree(
    currentFile: File,
    parentContinuationPrefix: String, // Prefix like "│   " or "    "
    isLastInSiblings: Boolean,
    maxFilesPerDir: Int,
    filterFn: (File) -> Boolean,
    sb: StringBuilder,
    render: (File) -> String = { it.name }
  ) {
    if (!filterFn(currentFile)) {
      // If the current file is filtered out, do not display it or its children.
      // log.debug("Skipping in tree (sub): ${currentFile.absolutePath}") // Optional: for more verbose logging
      return
    }
    sb.append(parentContinuationPrefix)
    sb.append(if (isLastInSiblings) "└── " else "├── ")
    sb.append(render(currentFile))
    if (currentFile.isDirectory) {
      sb.append("/")
    }
    sb.appendLine()
    if (currentFile.isDirectory) {
      val children = currentFile.listFiles()?.toList() ?: emptyList()
      val entriesToConsider = children.take(maxFilesPerDir)
      // The new continuation prefix for the children of currentFile
      val childContinuationPrefix = parentContinuationPrefix + (if (isLastInSiblings) "    " else "│   ")
      entriesToConsider.forEachIndexed { index, child ->
        buildAsciiSubTree(
          child, childContinuationPrefix, index == entriesToConsider.size - 1, maxFilesPerDir, filterFn, sb
        )
      }
    }
  }

  fun filteredWalk(
    file: File,
    maxFilesPerDir: Int = 20,
    treatDocumentsAsText: Boolean = false,
    fn: (File) -> Boolean = { !isLLMIgnored(it.toPath()) }
  ): List<File> {
    val filterFn = if (treatDocumentsAsText) {
      { f: File -> fn(f) || f.isDocumentFile() }
    } else fn
    val result = mutableListOf<File>()
    filteredWalkInternal(file, maxFilesPerDir, filterFn, result)
    return result
  }

  private fun filteredWalkInternal(
    file: File,
    maxFilesPerDir: Int,
    filterFn: (File) -> Boolean,
    result: MutableList<File>
  ) {
    if (!filterFn(file)) {
      log.trace("Skipping file: ${file.absolutePath}")
      return
    }
    if (file.isDirectory) {
      val children = file.listFiles() ?: return
      val limit = minOf(children.size, maxFilesPerDir)
      for (i in 0 until limit) {
        filteredWalkInternal(children[i], maxFilesPerDir, filterFn, result)
      }
    } else {
      result.add(file)
    }
  }

  fun File.listFilesRecursively(): List<File> {
    val files = mutableListOf<File>()
    listFilesRecursivelyInternal(this, files)
    return files
  }

  private fun listFilesRecursivelyInternal(dir: File, files: MutableList<File>) {
    val children = dir.listFiles() ?: return
    for (child in children) {
      val name = child.name
      if (name.startsWith(".") || name == "node_modules" || isGitignore(child.toPath())) {
        continue
      }
      files.add(child.absoluteFile)
      if (child.isDirectory) {
        listFilesRecursivelyInternal(child, files)
      }
    }
  }

  fun expandFileList(vararg data: File, treatDocumentsAsText: Boolean = false): Array<File> {
    return data.flatMap {
      if (!it.exists()) {
        log.debug("File does not exist during expansion: ${it.absolutePath}")
        return@flatMap emptyList<File>()
      }
      (when {
        it.name.endsWith(".data") -> arrayOf(it)
        treatDocumentsAsText && it.isDocumentFile() -> arrayOf(it)
        isGitignore(it.toPath()) -> {
          log.debug("File ignored by gitignore: ${it.absolutePath}")
          arrayOf()
        }

        isLLMIgnored(it.toPath()) -> {
          log.debug("File ignored by llmignore: ${it.absolutePath}")
          arrayOf()
        }

        it.length() > 100_000_000L -> {
          log.debug("File too large (>100MB): ${it.absolutePath}")
          arrayOf()
        }

        it.extension.lowercase(Locale.getDefault()) in FileExtensions.BINARY_EXTENSIONS -> {
          log.debug("File is a binary type: ${it.absolutePath}")
          arrayOf()
        }

        isBinaryFile(it) -> {
          log.debug("File is detected as binary: ${it.absolutePath}")
          arrayOf()
        }

        it.isDirectory -> expandFileList(
          *it.listFiles() ?: arrayOf(),
          treatDocumentsAsText = treatDocumentsAsText
        )

        else -> arrayOf(it)
      }).toList()
    }.toTypedArray()
  }

  fun isBinaryFile(file: File): Boolean {
    if (!file.exists() || file.isDirectory || file.length() == 0L) {
      return false
    }

    if (file.extension.lowercase(Locale.getDefault()) in FileExtensions.BINARY_EXTENSIONS) {
      return true
    }
    // Small files that passed extension check are likely text
    if (file.length() < 32) {
      return false
    } else if (file.length() > MAX_TEXT_SIZE) {
      return false
    }

    return try {
      file.inputStream().use { input ->
        isBinaryStream(input)
      }
    } catch (e: Exception) {
      log.debug("Error reading file for binary detection: ${file.absolutePath}", e)
      false
    }
  }

  private val COMMON_TEXT_CONTROL_CHARS = BooleanArray(256).apply {
    this[9] = true   // tab
    this[10] = true  // newline
    this[12] = true  // form feed
    this[13] = true  // carriage return
  }

  private fun isBinaryStream(input: InputStream): Boolean {
    val sampleSize = 8192 // Increased sample size for better detection
    val bytes = ByteArray(sampleSize)
    val bytesRead = input.read(bytes, 0, sampleSize)
    if (bytesRead <= 0) return false
    // Quick check for UTF-8 BOM
    if (bytesRead >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
      return false // UTF-8 with BOM is text
    }

    var nullCount = 0
    var controlCharCount = 0
    var printableCount = 0
    var i = 0

    while (i < bytesRead) {
      val b = bytes[i].toInt() and 0xFF

      when {
        b == 0 -> {
          nullCount++
          i++
        }
        // Allow common control characters: tab(9), newline(10), form feed(12), carriage return(13)
        b < 256 && COMMON_TEXT_CONTROL_CHARS[b] -> {
          printableCount++
          i++
        }
        // Other control characters (but not as strict)
        b in 1..8 || b in 11..11 || b in 14..31 -> {
          controlCharCount++
          i++
        }
        // ASCII printable characters
        b in 32..126 -> {
          printableCount++
          i++
        }
        // Handle UTF-8 sequences properly
        b and 0x80 != 0 -> {
          val utfLength = when {
            b and 0xE0 == 0xC0 -> 2  // 110xxxxx - 2 byte sequence
            b and 0xF0 == 0xE0 -> 3  // 1110xxxx - 3 byte sequence
            b and 0xF8 == 0xF0 -> 4  // 11110xxx - 4 byte sequence
            else -> 1 // Invalid UTF-8 start byte, treat as single byte
          }

          // Validate UTF-8 sequence
          var validUtf8 = true
          if (utfLength > 1 && i + utfLength <= bytesRead) {
            for (j in 1 until utfLength) {
              val continuationByte = bytes[i + j].toInt() and 0xFF
              if (continuationByte and 0xC0 != 0x80) {
                validUtf8 = false
                break
              }
            }
          } else if (utfLength > 1) {
            validUtf8 = false // Incomplete sequence at end of buffer
          }

          if (validUtf8 && utfLength > 1) {
            printableCount++
            i += utfLength
          } else {
            controlCharCount++
            i++
          }
        }
        // High ASCII (128-255) - could be extended ASCII or invalid UTF-8
        else -> {
          controlCharCount++
          i++
        }
      }

    }

    // More lenient binary detection logic
    val nullRatio = nullCount.toDouble() / bytesRead
    val controlRatio = controlCharCount.toDouble() / bytesRead
    val printableRatio = printableCount.toDouble() / bytesRead

    return when {
      nullRatio > 0.05 -> true // More than 5% null bytes (more lenient)
      printableRatio > 0.70 -> false // More than 70% printable characters (including UTF-8)
      controlRatio > 0.50 -> true // More than 50% control/invalid characters
      else -> false
    }
  }









  fun isLLMIgnored(path: Path): Boolean {
    if (path.toFile().name == ".llmignore") return true
     return IgnoreFileUtil.isIgnored(path, IgnoreFileUtil.LLMIGNORE)
  }

  fun isGitignore(path: Path): Boolean {
    if (path.fileName.toString() == ".gitignore") return true
     return IgnoreFileUtil.isIgnored(path, IgnoreFileUtil.GITIGNORE)
  }

  fun String.relativizeFrom(root: Path) = try {
    root.relativize(File(this).toPath()).toString()
  } catch (e: Throwable) {
    this
  }

  fun resolveToRelativePath(root: Path, filename: String): String? {
    log.debug("Resolving filename '{}' relative to root '{}'", filename, root)
    if (!root.toFile().exists() || !root.toFile().isDirectory) {
      log.debug("Root path does not exist or is not a directory: {}", root)
      return null
    }

    var returnValue = prefilterFilename(filename) ?: return null
    if (root.resolve(returnValue).toFile().exists()) return returnValue

    // Handle absolute paths
    try {
      val path = File(returnValue).toPath()
      if (path.startsWith(root)) {
        returnValue = path.toString().relativizeFrom(root)
        log.debug("Relativized path to: {}", returnValue)
      }
    } catch (e: Throwable) {
      log.debug("Error resolving filename '{}': {}", returnValue, e.message)
    }
    if (root.resolve(returnValue).toFile().exists()) return returnValue

    // Recursive search with better performance
    try {
      val resolvedPath = root.resolve(returnValue)
      if (!resolvedPath.toFile().exists() || !resolvedPath.toFile().isFile) {
        log.debug("File not found directly under root, searching recursively")
        val targetFileName = File(returnValue).name
        val foundFile = root.toFile().listFilesRecursively()
          .asSequence()
          .filter { it.isFile }
          .find {
            val normalizedPath = it.toString().replace("\\", "/")
            val normalizedTarget = returnValue.replace("\\", "/")
            if (normalizedTarget.contains("/")) {
              normalizedPath.endsWith(normalizedTarget)
            } else {
              it.name.equals(targetFileName, ignoreCase = true)
            }
          }
        if (foundFile != null) {
          returnValue = foundFile.toString().relativizeFrom(root)
          log.debug("Found file recursively at: {}", returnValue)
        }
      }
    } catch (e: Throwable) {
      log.debug("Error searching for file '{}' recursively: {}", returnValue, e.message)
    }
    if (root.resolve(returnValue).toFile().exists()) return returnValue

    return null
  }

  fun prefilterFilename(text: String): String? {
    var returnValue = text.trim()
    if (returnValue.isEmpty()) return null
    returnValue = returnValue.split("\\s+".toRegex()).filterNot { it.isBlank() }.firstOrNull() ?: return null
    val backtickPattern = "`([^`]+)`".toRegex()
    if (backtickPattern.containsMatchIn(returnValue)) {
      returnValue = backtickPattern.find(returnValue)?.groupValues?.get(1) ?: returnValue
    }
    return returnValue
  }

  private object FileExtensions {
    val BINARY_EXTENSIONS = setOf(
      // Archives
      "jar", "zip", "tar", "gz", "7z", "rar", "bz2", "xz", "war", "ear",
      // Compiled/Binary
      "class", "exe", "dll", "so", "dylib", "bin", "dat", "o", "obj", "lib", "a",
      // Images
      "png", "jpg", "jpeg", "gif", "ico", "bmp", "tiff", "webp", "avif", "heic",
      // Documents
      "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf",
      // Media
      "mp3", "mp4", "avi", "mov", "wav", "flac", "mkv", "webm", "m4a", "aac", "ogg",
      // 3D/CAD
      "stl", "obj", "fbx", "blend", "max", "3ds", "dae",
      // Fonts
      "ttf", "otf", "woff", "woff2", "eot",
      // Database
      "db", "sqlite", "sqlite3", "mdb",
      // Other
      "pyc", "pyo", "pyd", "wasm"
    )
  }
}