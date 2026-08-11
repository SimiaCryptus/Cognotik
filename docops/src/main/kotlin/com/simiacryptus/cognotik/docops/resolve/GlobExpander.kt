package com.simiacryptus.cognotik.docops.resolve

import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/** Literal / simple-glob / recursive-glob path expansion. Pure apart from directory listings. */
object GlobExpander {

  private val log = LoggerFactory.getLogger(GlobExpander::class.java)

  fun isGlobPattern(pattern: String): Boolean =
    pattern.contains("*") || pattern.contains("?") || pattern.contains("[")

  fun expandPatternOrLiteral(
    baseDir: File,
    pattern: String,
    lister: (File) -> List<File> = { it: File -> it.listFilesRecursively() },
  ): List<File> = if (isGlobPattern(pattern)) {
    if (pattern.contains("**")) expandRecursiveGlob(baseDir, pattern, lister)
    else expandSimpleGlob(baseDir, pattern)
  } else {
    val resolved = try {
      baseDir.resolve(pattern).canonicalFile
    } catch (e: Exception) {
      log.warn("Failed to canonicalize literal path '$pattern' against ${baseDir.absolutePath}", e)
      baseDir.resolve(pattern)
    }
    if (resolved.exists()) {
      log.info("Literal path '$pattern' -> ${resolved.absolutePath} (exists)")
    } else {
      log.info("Literal path '$pattern' -> ${resolved.absolutePath} (does not exist yet; kept as prospective target)")
    }
    listOf(resolved)
  }

  fun expandSimpleGlob(baseDir: File, pattern: String): List<File> {
    val patternFile = File(pattern)
    val directory = try {
      if (patternFile.parent != null) baseDir.resolve(patternFile.parent).canonicalFile
      else baseDir.canonicalFile
    } catch (e: Exception) {
      log.warn("Failed to resolve directory for pattern '$pattern'", e)
      return emptyList()
    }
    if (!directory.exists() || !directory.isDirectory) {
      log.warn("Glob '$pattern' matched nothing: directory does not exist or is not a directory: ${directory.absolutePath}")
      return emptyList()
    }
    val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:${patternFile.name}")
    val children = directory.listFiles()?.toList() ?: emptyList()
    val matched = children.filter { it.isFile && matcher.matches(it.toPath().fileName) }
    if (matched.isEmpty()) {
      log.info(
        "Glob '$pattern' matched 0 of ${children.size} entr(ies) in ${directory.absolutePath}" +
            (if (children.isEmpty()) " (directory is empty)"
            else "; name filter='${patternFile.name}', candidates: ${children.take(25).joinToString { it.name }}")
      )
    } else {
      log.info("Glob '$pattern' matched ${matched.size} file(s) in ${directory.absolutePath}")
    }
    return matched
  }

  fun expandRecursiveGlob(
    baseDir: File,
    pattern: String,
    lister: (File) -> List<File> = { it: File -> it.listFilesRecursively() },
  ): List<File> {
    val beforeGlob = pattern.substringBefore("**").removeSuffix("/").removeSuffix("\\")
    val resolvedBase = try {
      if (beforeGlob.isNotEmpty()) baseDir.resolve(beforeGlob).canonicalFile else baseDir.canonicalFile
    } catch (e: Exception) {
      log.warn("Failed to resolve base directory for pattern '$pattern'", e)
      return emptyList()
    }
    if (!resolvedBase.exists()) {
      log.warn("Recursive glob '$pattern' matched nothing: base directory does not exist: ${resolvedBase.absolutePath}")
      return emptyList()
    }
    val remainingPattern = pattern.substringAfter("**").removePrefix("/").removePrefix("\\")
    val matcher: PathMatcher = if (remainingPattern.isNotEmpty()) {
      try {
        FileSystems.getDefault().getPathMatcher("glob:$remainingPattern")
      } catch (e: Exception) {
        log.warn("Invalid glob pattern: $remainingPattern", e)
        PathMatcher { false }
      }
    } else PathMatcher { true }

    val scanned = lister(resolvedBase)
    val matched = scanned.filter { it.isFile && matcher.matches(it.toPath().fileName) }
    if (matched.isEmpty()) {
      log.info(
        "Recursive glob '$pattern' matched 0 of ${scanned.size} file(s) under ${resolvedBase.absolutePath} " +
            "(name filter='${remainingPattern.ifEmpty { "*" }}')"
      )
    } else {
      log.info(
        "Recursive glob '$pattern' matched ${matched.size} of ${scanned.size} file(s) under ${resolvedBase.absolutePath}"
      )
    }
    return matched
  }
}