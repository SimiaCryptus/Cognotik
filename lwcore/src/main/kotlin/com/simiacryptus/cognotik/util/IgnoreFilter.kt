package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.util.IgnoreFileUtil.IgnoreSpec
import java.io.File
import java.nio.file.Path
import java.util.*
import kotlin.io.path.name

/**
 * Functional strategy selection for path filtering.
 *
 * Each entry is either:
 *  - backed by an [IgnoreSpec] (pattern file such as `.gitignore` / `.llmignore`), or
 *  - backed by an ad-hoc [strategy] lambda (name based rules, size limits, binary detection).
 *
 * Callers combine them via the `vararg` helpers ([matchesAny], [matchesNone], [predicate]),
 * which is the single generalized mechanism all of [FileSelectionUtils] delegates to.
 */
enum class IgnoreFilter(
  val spec: IgnoreSpec? = null,
  private val strategy: (Path) -> Boolean = { false },
) {

  /* ---------- pattern-file backed strategies ---------- */

  GITIGNORE(spec = IgnoreFileUtil.GITIGNORE),
  LLMIGNORE(spec = IgnoreFileUtil.LLMIGNORE),
  READONLY(spec = IgnoreFileUtil.READONLY),
  HIDDEN(spec = IgnoreFileUtil.HIDDEN),
  WRITEABLE(spec = IgnoreFileUtil.WRITEABLE),

  /* ---------- ad-hoc name based strategies ---------- */

  GITIGNORE_FILE(strategy = { it.name == ".gitignore" }),
  LLMIGNORE_FILE(strategy = { it.name == ".llmignore" }),
  IGNORE_FILE(strategy = { it.name in IGNORE_FILE_NAMES }),
  DOT_FILE(strategy = { it.name.startsWith(".") }),
  NODE_MODULES(strategy = { it.name == "node_modules" }),

  /* ---------- ad-hoc content based strategies ---------- */

  MISSING(strategy = { !it.toFile().exists() }),
  DIRECTORY(strategy = { it.toFile().isDirectory }),
  OVERSIZE(strategy = { it.toFile().length() > MAX_FILE_SIZE }),
  BINARY_EXTENSION(strategy = { it.toFile().extension.lowercase(Locale.getDefault()) in BINARY_EXTENSIONS }),
  BINARY_CONTENT(strategy = { FileSelectionUtils.isBinaryFile(it.toFile()) }),
  ;

  fun matches(path: Path): Boolean = spec?.let { IgnoreFileUtil.isIgnored(path, it) } ?: strategy(path)
  fun matches(file: File): Boolean = matches(file.toPath())
  operator fun invoke(file: File): Boolean = matches(file)
  operator fun invoke(path: Path): Boolean = matches(path)

  companion object {

    /** Hard file size ceiling for "text" candidates (100MB). */
    const val MAX_FILE_SIZE = 100_000_000L

    val IGNORE_FILE_NAMES: Set<String> =
      setOf(".gitignore", ".llmignore", ".readonly", ".hidden", ".writeable")

    /* ---------- reusable filter selections ---------- */

    /** `.llmignore` self + `.llmignore` patterns. */
    val LLM: Array<IgnoreFilter> = arrayOf(LLMIGNORE_FILE, LLMIGNORE)

    /** `.gitignore` self + `.gitignore` patterns. */
    val GIT: Array<IgnoreFilter> = arrayOf(GITIGNORE_FILE, GITIGNORE)

    /** Default "is this ignored" selection (llmignore then gitignore). */
    val DEFAULT: Array<IgnoreFilter> = arrayOf(LLMIGNORE_FILE, LLMIGNORE, GITIGNORE)

    /** Selection used when recursively listing a project tree. */
    val RECURSIVE_LISTING: Array<IgnoreFilter> = arrayOf(DOT_FILE, NODE_MODULES, GITIGNORE_FILE, GITIGNORE)

    /** Binary detection selection (cheap extension check first). */
    val BINARY: Array<IgnoreFilter> = arrayOf(BINARY_EXTENSION, BINARY_CONTENT)

    /** Everything that disqualifies a file from being sent to an LLM. */
    val TEXT_SELECTION: Array<IgnoreFilter> = DEFAULT + OVERSIZE + BINARY

    /* ---------- generalized combinators ---------- */

    fun matchesAny(path: Path, vararg filters: IgnoreFilter): Boolean = filters.any { it.matches(path) }
    fun matchesAny(file: File, vararg filters: IgnoreFilter): Boolean = filters.any { it.matches(file) }
    fun matchesNone(path: Path, vararg filters: IgnoreFilter): Boolean = filters.none { it.matches(path) }
    fun matchesNone(file: File, vararg filters: IgnoreFilter): Boolean = filters.none { it.matches(file) }

    /** Predicate that is `true` when the file matches at least one filter. */
    fun predicate(vararg filters: IgnoreFilter): (File) -> Boolean = { file -> matchesAny(file, *filters) }

    /** Predicate that is `true` when the file matches none of the filters (i.e. "keep it"). */
    fun accepting(vararg filters: IgnoreFilter): (File) -> Boolean = { file -> matchesNone(file, *filters) }

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