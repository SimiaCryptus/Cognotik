package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.util.IgnoreFileUtil
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Centralized access-control checks for the file server based on
 * optional `.readonly` and `.hidden` marker files. These behave like
 * `.gitignore`-style files: patterns inside them match paths within
 * the directory tree containing the file.
 *
 * - `.hidden`: matched paths are treated as non-existent.
 * - `.readonly`: matched paths cannot be modified (no PUT/POST/DELETE).
 *
 * The marker filenames themselves (`.readonly`, `.hidden`) are also
 * always hidden / read-only respectively so they cannot be tampered
 * with through the file server.
 */
object FileAccessControl {
  @Suppress("unused")
  private val log = LoggerFactory.getLogger(FileAccessControl::class.java)

  private const val READONLY_FILE = ".readonly"
  private const val HIDDEN_FILE = ".hidden"
  private const val WRITEABLE_FILE = ".writeable"

  /**
   * Returns true if the given file should be considered hidden by the
   * file server (i.e. treated as non-existent for all operations).
   *
   * A file is hidden if:
   *  - Its name equals `.hidden` (the marker file itself), OR
   *  - Any ancestor directory (up to and including [baseDir]) contains
   *    a `.hidden` file with a pattern that matches this path.
   */
  fun isHidden(baseDir: File?, file: File): Boolean {
    return file.name == HIDDEN_FILE || matchesAny(baseDir, file, IgnoreFileUtil.HIDDEN)
  }

  /**
   * Returns true if the given file is read-only and modifications
   * (PUT/POST upload/DELETE) should be rejected.
   *
   * A file is read-only if:
   *  - Its name equals `.readonly` (the marker file itself), OR
   *  - Any ancestor directory (up to and including [baseDir]) contains
   *    a `.readonly` file with a pattern that matches this path, OR
   *  - Any ancestor directory (up to and including [baseDir]) contains
   *    a `.writeable` file whose patterns do NOT match this path
   *    (`.writeable` acts as a whitelist: everything not matched is
   *    treated as read-only).
   */
  fun isReadOnly(baseDir: File?, file: File): Boolean {
    if (file.name == READONLY_FILE) return true
    if (file.name == WRITEABLE_FILE) return true
    if (matchesAny(baseDir, file, IgnoreFileUtil.READONLY)) return true
    // If a .writeable file governs this path and does NOT whitelist it,
    // then the file is read-only.
    if (isOutsideWriteableWhitelist(baseDir, file)) return true
    return false
  }

  /**
   * Walk from [file]'s parent up to (and including) [baseDir], checking
   * for an ignore file matching the given [spec]. This is similar to
   * [IgnoreFileUtil.isIgnored] but is bounded by [baseDir] so the
   * marker file does not need to also live at the search root.
   */
  private fun matchesAny(baseDir: File?, file: File, spec: IgnoreFileUtil.IgnoreSpec): Boolean {
    val base = baseDir?.absoluteFile ?: return false
    val target = file.absoluteFile
    var current: File? = if (target.isDirectory) target else target.parentFile
    val visited = mutableSetOf<File>()
    while (current != null && current !in visited) {
      visited.add(current)
      val markerFile = File(current, spec.ignoreFileName)
      if (markerFile.exists() && markerFile.isFile) {
        if (matchesIgnoreFile(current, target, markerFile)) {
          return true
        }
      }
      if (current == base) break
      current = current.parentFile
    }
    return false
  }

  /**
   * Returns true if there is a `.writeable` file in an ancestor directory
   * (up to and including [baseDir]) AND the [file] does NOT match any of
   * its patterns. In other words, `.writeable` acts as a whitelist and
   * anything not matched is considered read-only.
   *
   * If no `.writeable` file is found in scope, returns false (no whitelist
   * applies, so this check does not impose read-only).
   */
  private fun isOutsideWriteableWhitelist(baseDir: File?, file: File): Boolean {
    val base = baseDir?.absoluteFile ?: return false
    val target = file.absoluteFile
    // Find the nearest .writeable file walking up from the target.
    var current: File? = if (target.isDirectory) target else target.parentFile
    val visited = mutableSetOf<File>()
    while (current != null && current !in visited) {
      visited.add(current)
      val writeableFile = File(current, WRITEABLE_FILE)
      if (writeableFile.exists() && writeableFile.isFile) {
        // The directory containing .writeable is itself always considered
        // "inside" the whitelist scope, but its writeability is governed
        // by whether its own path (relative to itself = "") is whitelisted.
        // For practical purposes: if the target IS the directory containing
        // the .writeable file, treat it as read-only (cannot modify the
        // governed root itself) unless explicitly whitelisted by "." or "/".
        val matched = matchesIgnoreFile(current, target, writeableFile)
        return !matched
      }
      if (current == base) break
      current = current.parentFile
    }
    return false
  }


  private fun matchesIgnoreFile(dir: File, target: File, ignoreFile: File): Boolean {
    val patterns = IgnoreFileUtil.compileIgnorePatterns(ignoreFile)
    if (patterns.isEmpty()) return false
    val relativePath = try {
      dir.toPath().relativize(target.toPath()).toString().replace('\\', '/')
    } catch (e: Exception) {
      target.name
    }
    return patterns.any { regex ->
      regex.matches(relativePath) ||
          regex.matches(target.name) ||
          // Also match any path segment so a pattern like "secret" matches "a/secret/b.txt"
          relativePath.split('/').any { seg -> regex.matches(seg) }
    }
  }
}