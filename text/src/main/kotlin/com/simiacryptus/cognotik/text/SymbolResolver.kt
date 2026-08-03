package com.simiacryptus.cognotik.text

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Purely lexical (non-semantic) symbol resolution.
 *
 * Every [SymbolIndexer.FileRecord.qualifiedNames] entry is registered under all of its
 * dot-boundary suffixes, e.g. `com.foo.Bar.baz` is registered as
 * `baz`, `Bar.baz`, `foo.Bar.baz` and `com.foo.Bar.baz`.
 *
 * A [SymbolIndexer.FileRecord.referencedNames] entry therefore "resolves" to every declaration
 * whose qualified name ends with the referenced text (at a dot boundary).
 * No imports, scoping or overload resolution is attempted - the result is a best-effort
 * candidate list, flagged [Resolution.ambiguous] whenever more than one declaration matched.
*
* Declarations found in the **referencing file itself** are dropped by default
* ([DEFAULT_EXCLUDE_SELF_FILE]); a name whose only declarations are same-file is hidden
* completely - it appears neither in `resolutions` nor in `unresolvedNames`.
*
* Candidates are ranked by **file-path distance**: the number of directory traversals
* (`..` hops upwards plus descents downwards) needed to walk from the referencing file to
* the declaring file. The declaration in the same file wins, then the closest one on disk.
* By default only that single best candidate is stored ([DEFAULT_MAX_TARGETS] `= 1`), while
* [Resolution.ambiguous] and [Resolution.candidateCount] still report the full match set.
 */
object SymbolResolver {

  /**
   * Only the nearest candidate is kept by default;
   * [Resolution.candidateCount] preserves the raw number of matches.
   */
  const val DEFAULT_MAX_TARGETS = 1
  /**
   * Same-file declarations are hidden by default: a reference that only points at a symbol of
   * its own file carries no cross-file information. When *every* candidate was same-file the
   * name is omitted from [Resolution] **and** from [SymbolIndexer.FileRecord.unresolvedNames].
   */
  const val DEFAULT_EXCLUDE_SELF_FILE = true


  /** Distance reported/used when a path is unknown - sorts after every real candidate. */
  const val UNKNOWN_DISTANCE = Int.MAX_VALUE / 4

  /** A declaration a reference may point at. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Target(
    val qualifiedName: String = "",
    /** Root-relative path of the file declaring the symbol. */
    val path: String = "",
  )

  /** One referenced name and the declarations it matched. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Resolution(
    val name: String = "",
    /** Nearest candidates first (by file-path distance), truncated to `maxTargets`. */
    val targets: List<Target> = emptyList(),
    /** True when more than one declaration matched the name. */
    val ambiguous: Boolean = false,
    /** Number of matches found before truncation (same-file candidates already removed). */
    val candidateCount: Int = 0,
    /**
     * Directory-traversal distance from the referencing file to `targets.first()`:
     * `0` for the same directory, `1` for a parent/child, `2` for a sibling directory, etc.
     */
    val distance: Int = 0,
  )

  /** suffix (at dot boundaries) -> declarations registered under it. */
  fun buildIndex(records: List<SymbolIndexer.FileRecord>): Map<String, List<Target>> {
    val index = HashMap<String, MutableList<Target>>()
    records.forEach { record ->
      record.qualifiedNames.forEach { qualifiedName ->
        val target = Target(qualifiedName, record.path)
        suffixes(qualifiedName).forEach { suffix ->
          index.getOrPut(suffix) { mutableListOf() }.add(target)
        }
      }
    }
    return index
  }

  /** Resolve every record against the qualified names of all [records]; order is preserved. */
  fun resolve(
    records: List<SymbolIndexer.FileRecord>,
    maxTargets: Int = DEFAULT_MAX_TARGETS,
    excludeSelfFile: Boolean = DEFAULT_EXCLUDE_SELF_FILE,
  ): List<SymbolIndexer.FileRecord> {
    val index = buildIndex(records)
    return records.map { resolve(it, index, maxTargets, excludeSelfFile) }
  }

  /** Resolve a single record against a previously [buildIndex]ed name table. */
  fun resolve(
    record: SymbolIndexer.FileRecord,
    index: Map<String, List<Target>>,
    maxTargets: Int = DEFAULT_MAX_TARGETS,
    excludeSelfFile: Boolean = DEFAULT_EXCLUDE_SELF_FILE,
  ): SymbolIndexer.FileRecord {
    val resolutions = mutableListOf<Resolution>()
    val unresolved = mutableListOf<String>()
    record.referencedNames.forEach { name ->
      val allMatches = lookup(name, index)
      val matches =
        if (excludeSelfFile) allMatches.filter { it.path != record.path } else allMatches
      when {

        allMatches.isEmpty() -> unresolved.add(name)

        matches.isEmpty() -> Unit
        else -> {
          val ordered = rank(matches, record.path)
          resolutions.add(
            Resolution(
              name = name,
              targets = ordered.take(maxTargets.coerceAtLeast(1)),
              ambiguous = matches.size > 1,
              candidateCount = matches.size,
              distance = pathDistance(record.path, ordered.first().path),
            )
          )
        }
      }
    }
    return record.copy(
      resolutions = resolutions.sortedBy { it.name },
      unresolvedNames = unresolved.distinct().sorted(),
    )
  }

  /** Recompute the resolutions of an already-built manifest (sidecars are left untouched). */
  fun resolve(
    manifest: SymbolIndexer.Manifest,
    maxTargets: Int = DEFAULT_MAX_TARGETS,
    excludeSelfFile: Boolean = DEFAULT_EXCLUDE_SELF_FILE,
  ): SymbolIndexer.Manifest {
    val files = resolve(manifest.files, maxTargets, excludeSelfFile)
    return manifest.copy(
      files = files,
      resolvedNameCount = files.sumOf { it.resolutions.size },
      unresolvedNameCount = files.sumOf { it.unresolvedNames.size },
    )
  }

  /** All declarations whose qualified name ends with [name] (at a dot boundary). */
  fun lookup(name: String, index: Map<String, List<Target>>): List<Target> {
    val key = normalize(name)
    if (key.isEmpty()) return emptyList()
    return (index[key] ?: emptyList()).distinct()
  }

  /** Strip generics, argument lists, array/nullability decorations and stray dots. */
  fun normalize(name: String): String {
    var s = name.trim()
    s.indexOf('(').let { if (it >= 0) s = s.substring(0, it) }
    s.indexOf('<').let { if (it >= 0) s = s.substring(0, it) }
    s = s.trim().removeSuffix("!!").removeSuffix("?").trim()
    while (s.endsWith("[]")) s = s.dropLast(2).trim()
    return s.trim().trim('.')
  }
  /**
   * Number of directory traversals needed to walk from [fromPath] to [toPath]:
   * the `..` hops up to the common ancestor plus the descents back down.
   *
   * `0` when both files live in the same directory (or are the same file),
   * `1` for a parent/child directory, `2` for a sibling directory, and so on.
   * Returns [UNKNOWN_DISTANCE] when either path is unknown.
   */
  fun pathDistance(fromPath: String?, toPath: String?): Int {
    if (fromPath == null || toPath == null) return UNKNOWN_DISTANCE
    if (fromPath == toPath) return 0
    val from = dirSegments(fromPath)
    val to = dirSegments(toPath)
    var common = 0
    while (common < from.size && common < to.size && from[common] == to[common]) common++
    return (from.size - common) + (to.size - common)
  }
  /** Directory segments of a '/'-separated (root-relative) file path, file name dropped. */
  private fun dirSegments(path: String): List<String> =
    path.replace('\\', '/').split('/')
      .filter { it.isNotBlank() && it != "." }
      .dropLast(1)


  private fun suffixes(qualifiedName: String): List<String> {
    val parts = qualifiedName.split('.').filter { it.isNotBlank() }
    if (parts.isEmpty()) return emptyList()
    return parts.indices.map { i -> parts.subList(i, parts.size).joinToString(".") }
  }

  /**
   * Nearest-first ordering: same file, then fewest directory traversals
   * ([pathDistance]), then the shallowest / alphabetically-first qualified name.
   */
  fun rank(matches: List<Target>, selfPath: String?): List<Target> {
    if (matches.size < 2) return matches
    val distances = HashMap<String, Int>()
    fun distanceOf(target: Target) = distances.getOrPut(target.path) { pathDistance(selfPath, target.path) }
    return matches.sortedWith(
      compareBy(
        { t: Target -> if (t.path == selfPath) 0 else 1 },
        { t: Target -> distanceOf(t) },
        { t: Target -> t.qualifiedName.count { c -> c == '.' } },
        { t: Target -> t.qualifiedName },
        { t: Target -> t.path },
      )
    )
  }
}