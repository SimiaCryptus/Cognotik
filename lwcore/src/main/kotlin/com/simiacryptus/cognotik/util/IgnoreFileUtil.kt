package com.simiacryptus.cognotik.util

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

/**
 * Unified utility for handling ignore files such as `.gitignore`, `.llmignore`,
 * or any similar pattern-based ignore file format.
 *
 * Supports:
 *  - Comment lines (starting with `#`)
 *  - Negation patterns (starting with `!`)
 *  - Glob wildcards (`*`, `?`)
 *  - Cross-platform path separators
 *  - Pattern compilation caching keyed by file + lastModified
 *  - Walking up directory ancestry until a marker file/dir is found
 */
object IgnoreFileUtil {
  private val log = LoggerFactory.getLogger(IgnoreFileUtil::class.java)

  /**
   * Describes an ignore file specification.
   *
   * @param ignoreFileName  Name of the ignore file (e.g. ".gitignore", ".llmignore").
   * @param markerFileName  Name of a marker file/directory indicating the "root"
   *                        of the ignore scope (e.g. ".git" for gitignore, ".llm"
   *                        for llmignore). Walking up the directory tree stops
   *                        once this marker is found.
   * @param alwaysIgnored   Names of files/directories that should always be ignored
   *                        regardless of the patterns in the ignore file.
   */
  data class IgnoreSpec(
    val ignoreFileName: String,
    val markerFileName: String,
    val alwaysIgnored: Set<String> = DEFAULT_ALWAYS_IGNORED,
  )

  /** Default directory names that should always be considered ignored. */
  val DEFAULT_ALWAYS_IGNORED: Set<String> = setOf(
    "node_modules", "target", "build", ".gradle", "dist", "out", ".logs"
  )

  /** Predefined spec for `.gitignore`. */
  val GITIGNORE = IgnoreSpec(
    ignoreFileName = ".gitignore",
    markerFileName = ".git",
    alwaysIgnored = DEFAULT_ALWAYS_IGNORED + ".git",
  )

  /** Predefined spec for `.llmignore`. */
  val LLMIGNORE = IgnoreSpec(
    ignoreFileName = ".llmignore",
    markerFileName = ".llm",
    alwaysIgnored = DEFAULT_ALWAYS_IGNORED,
  )

  /**
   * Predefined spec for `.readonly`. Matches files/directories that should be
   * treated as read-only by the file server (PUT/POST/DELETE blocked).
   * Uses the `.readonly` file itself as the marker so the scope is bounded
   * to the directory tree containing it.
   */
  val READONLY = IgnoreSpec(
    ignoreFileName = ".readonly",
    markerFileName = ".readonly",
    alwaysIgnored = emptySet(),
  )

  /**
   * Predefined spec for `.hidden`. Matches files/directories that should be
   * hidden from the file server (treated as non-existent).
   * Uses the `.hidden` file itself as the marker so the scope is bounded
   * to the directory tree containing it.
   */
  val HIDDEN = IgnoreSpec(
    ignoreFileName = ".hidden",
    markerFileName = ".hidden",
    alwaysIgnored = emptySet(),
  )

  /**
   * Predefined spec for `.writeable`. When present, acts as a whitelist:
   * only paths matching patterns in this file are considered writeable;
   * everything else within the scope of the `.writeable` file is read-only.
   * Uses the `.writeable` file itself as the marker so the scope is bounded
   * to the directory tree containing it.
   */
  val WRITEABLE = IgnoreSpec(
    ignoreFileName = ".writeable",
    markerFileName = ".writeable",
    alwaysIgnored = emptySet(),
  )


  private data class IgnoreCache(
    val patterns: List<Regex>,
    val lastModified: Long,
  )

  private val ignorePatternCache = ConcurrentHashMap<File, IgnoreCache>()

  /**
   * Compile patterns for the given ignore file, using a cache keyed by lastModified.
   *
   * NOTE: Negation (`!pattern`) semantics are not fully implemented here; negation
   * patterns are recognized and compiled, but the matcher currently treats any
   * matched pattern as an ignore hit. This mirrors the previous behavior in
   * FileSelectionUtils. Extend [isIgnored] if full negation support is needed.
   */
  fun compileIgnorePatterns(ignoreFile: File): List<Regex> {
    val lastModified = ignoreFile.lastModified()
    val cached = ignorePatternCache[ignoreFile]
    if (cached != null && cached.lastModified == lastModified) {
      return cached.patterns
    }

    val lines = try {
      ignoreFile.readLines()
    } catch (e: Exception) {
      log.warn("Error reading ignore file: ${ignoreFile.absolutePath}", e)
      emptyList()
    }

    val patterns = lines
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .mapNotNull { pattern ->
        try {
          // Handle negation patterns (starting with !)
          val isNegation = pattern.startsWith("!")
          val rawPattern = if (isNegation) pattern.substring(1) else pattern
          // A trailing slash (gitignore convention) means "directory":
          // match the directory itself and anything under it.
          val dirOnly = rawPattern.endsWith("/")
          val cleanPattern = if (dirOnly) rawPattern.trimEnd('/') else rawPattern

          val regexPattern = buildString {
            append("^")
            cleanPattern.forEach { char ->
              when (char) {
                '*' -> append(".*")
                '?' -> append(".")
                '.' -> append("\\.")
                '/' -> append("[/\\\\]") // Handle both forward and back slashes
                else -> append(Regex.escape(char.toString()))
              }
            }
            if (dirOnly) {
              // Match the directory itself OR any path under it
              append("([/\\\\].*)?$")
            } else {
              append("$")
            }
          }
          Regex(regexPattern)
        } catch (e: Exception) {
          log.warn("Invalid ignore pattern: $pattern", e)
          null
        }
      }

    ignorePatternCache[ignoreFile] = IgnoreCache(patterns, lastModified)
    return patterns
  }

  /**
   * Determine whether the given path should be ignored according to the given spec.
   */
  fun isIgnored(path: Path, spec: IgnoreSpec): Boolean {
    // Always-ignored common directories
    if (path.name in spec.alwaysIgnored) return true

    var currentDir = path.toFile().parentFile ?: return false
    val checkedDirs = mutableSetOf<File>() // Prevent infinite loops

    // Walk up directory tree until we find the marker file/dir
    while (!currentDir.resolve(spec.markerFileName).exists() && currentDir !in checkedDirs) {
      checkedDirs.add(currentDir)
      if (matchesIgnoreFile(currentDir, path, spec.ignoreFileName)) {
        return true
      }
      currentDir = currentDir.parentFile ?: break
    }

    // Check ignore file in the resolved root directory
    if (matchesIgnoreFile(currentDir, path, spec.ignoreFileName)) {
      return true
    }
    return false
  }

  /**
   * Generalized form of [isIgnored] accepting any number of specs; a path is ignored
   * if *any* of the given specs ignores it. Prefer [IgnoreFilter] for call sites that
   * also need the ad-hoc (non pattern-file) rules.
   */
  fun isIgnoredByAny(path: Path, vararg specs: IgnoreSpec): Boolean = specs.any { isIgnored(path, it) }

  /** Generalized form of [isIgnored] driven by [IgnoreFilter] strategy selection. */
  fun isIgnored(path: Path, vararg filters: IgnoreFilter): Boolean = IgnoreFilter.matchesAny(path, *filters)


  private fun matchesIgnoreFile(dir: File, path: Path, ignoreFileName: String): Boolean {
    val ignoreFile = dir.resolve(ignoreFileName)
    if (!ignoreFile.exists()) return false
    val patterns = compileIgnorePatterns(ignoreFile)
    val relativePath = try {
      dir.toPath().relativize(path).toString()
    } catch (e: Exception) {
      path.fileName.toString()
    }
    return patterns.any { it.matches(relativePath) || it.matches(path.fileName.toString()) }
  }

  /** Clear the internal pattern cache. Useful for tests. */
  fun clearCache() {
    ignorePatternCache.clear()
  }
}