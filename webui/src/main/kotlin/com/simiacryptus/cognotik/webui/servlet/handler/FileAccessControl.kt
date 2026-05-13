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
    private val log = LoggerFactory.getLogger(FileAccessControl::class.java)

    private const val READONLY_FILE = ".readonly"
    private const val HIDDEN_FILE = ".hidden"

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
        if (file.name == HIDDEN_FILE) return true
        return matchesAny(baseDir, file, IgnoreFileUtil.HIDDEN)
    }

    /**
     * Returns true if the given file is read-only and modifications
     * (PUT/POST upload/DELETE) should be rejected.
     *
     * A file is read-only if:
     *  - Its name equals `.readonly` (the marker file itself), OR
     *  - Any ancestor directory (up to and including [baseDir]) contains
     *    a `.readonly` file with a pattern that matches this path.
     */
    fun isReadOnly(baseDir: File?, file: File): Boolean {
        if (file.name == READONLY_FILE) return true
        return matchesAny(baseDir, file, IgnoreFileUtil.READONLY)
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