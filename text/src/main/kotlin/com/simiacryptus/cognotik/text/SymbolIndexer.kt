package com.simiacryptus.cognotik.text

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.simiacryptus.cognotik.text.validate.FileValidators
import com.simiacryptus.cognotik.text.validate.GrammarValidator
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Instant
import java.util.Collections
import java.util.stream.Collectors

/**
 * Crawls a source tree, finds every file that has a language-specific [com.simiacryptus.cognotik.text.validate.GrammarValidator],
 * extracts its public symbols and records the result as JSON.
 *
 * Layout produced (with the default [Config.dataDirName] of `.data`):
 *
 * ```
 * package/foo.java              -> package/.data/foo.java.json
 * package/sub/bar.kt            -> package/sub/.data/bar.kt.json
 * package/.data/package.json    -> rollup summary of package/ (and everything below it)
 * <root>/.data/project.json     -> manifest of every indexed file (slim: no symbol/reference detail)
 * <root>/.data/viewer.html      -> standalone HTML report viewer (reads project.json)
 * ```
 *
  * **Every path stored in a report is relative to what that report describes**, so a report can be
  * moved/served together with the code it documents:
  *
  * ```
  * package/.data/foo.java.json   -> paths relative to package/      ("foo.java", "../util/Bar.java")
  * package/.data/package.json    -> paths relative to package/      ("sub/bar.kt", "../other/Baz.kt")
  * <root>/.data/project.json     -> paths relative to <root>        ("package/foo.java")
  * ```
  *
  * This applies to [FileRecord.path], [SymbolResolver.Target.path],
  * [RelatedFileAnalyzer.RelatedFile.path], [IncomingReference.path] and [Failure.path]. Internally
  * (and in the [Manifest] returned by [index]) everything is crawl-root relative; use
  * [relativeTo] / [resolvePath] / [rootRelative] to convert. [loadManifest] /
  * [loadPackageManifest] convert back automatically.
  *
 * Referenced names are resolved lexically against every file's qualified names by
 * [SymbolResolver]; the result is stored in `resolutions` / `unresolvedNames` in both the
* sidecars and the manifest. Resolution never crosses grammars: a reference is only matched
* against declarations extracted by the same
* [com.simiacryptus.cognotik.text.validate.GrammarValidator] (see [Config.sameGrammarResolutionOnly]),
* so identically-named symbols in different languages are never linked.
 *
* [index] additionally inverts the resolution graph: every record gets a `referencesFrom` list
* naming the **upstream** files that point at its declarations (see [IncomingReference] and
* [Config.computeIncomingReferences]), so the sidecars/reports can be navigated in both
* directions without re-scanning the project.
*
 * Every reported file also carries a `relatedFiles` list produced by [RelatedFileAnalyzer]:
 * a TF-IDF/cosine ranking over the grammatically-extracted declarations and references, so
 * files that share distinctive (high-IDF) names are linked to each other.
 *
 * Reports (`project.json` and the per-folder `package.json` rollups) hide ambiguous
 * resolutions; same-file resolutions are hidden everywhere (see [Config]).
 */
class SymbolIndexer(
  val root: Path,
  val config: Config = Config(),
) {

  constructor(root: File, config: Config = Config()) : this(root.toPath(), config)

  data class Config(
    /** Name of the hidden folder written next to each source file. */
    val dataDirName: String = ".data",
    /** File name (inside `<root>/<dataDirName>`) of the whole-project manifest. */
    val manifestName: String = "project.json",
    /** File name (inside `<folder>/<dataDirName>`) of the per-folder rollup summary. */
    val packageManifestName: String = "package.json",
    /**
     * Write a rollup report for every folder that contains indexed files, summarizing that
     * folder **and all of its subfolders** with the same rules as the project manifest.
     */
    val writePackageManifests: Boolean = true,
    /** Copy the bundled HTML viewer into `<root>/<dataDirName>`, next to the manifest. */
    val writeViewer: Boolean = true,
    /** File name written next to the manifest for the HTML viewer. */
    val viewerName: String = "viewer.html",
    /** Classpath resource holding the HTML viewer. */
    val viewerResource: String = "/symbol-indexer/viewer.html",
    /** Directory names never descended into. */
    val excludedDirNames: Set<String> = setOf(
      ".git", ".hg", ".svn", ".gradle", ".idea", ".vscode",
      "build", "out", "target", "dist", "node_modules", "venv", "__pycache__"
    ),
    /** Skip any directory whose name starts with `.` (this also skips the data folders). */
    val skipHiddenDirs: Boolean = true,
    /** Files larger than this are ignored. */
    val maxFileSizeBytes: Long = 4L * 1024 * 1024,
    /** Also run [com.simiacryptus.cognotik.text.validate.GrammarValidator.validateGrammar] and store the diagnostics. */
    val includeValidationErrors: Boolean = true,
    /** Also collect grammar-level (unresolved) symbol references. */
    val includeReferences: Boolean = true,
    /** Store the full reference list; when false only counts and distinct names are kept. */
    val includeReferenceDetails: Boolean = true,
    /** Truncate the stored reference list (counts still reflect everything found). */
    val maxReferencesPerFile: Int = 50_000,
    /** Match referenced names against the qualified names of every indexed file. */
    val resolveReferences: Boolean = true,
    /** Maximum number of candidate declarations stored per resolved name. */
    val maxResolutionTargets: Int = SymbolResolver.DEFAULT_MAX_TARGETS,
    /**
     * Drop candidate declarations that live in the referencing file itself; a name whose only
     * declarations are same-file is hidden entirely (neither resolved nor unresolved).
     */
    val excludeSelfFileResolutions: Boolean = SymbolResolver.DEFAULT_EXCLUDE_SELF_FILE,
     /**
      * Only match references against declarations produced by the **same grammar** - i.e. by the
      * same [com.simiacryptus.cognotik.text.validate.GrammarValidator]. A Java `Buffer` can then
      * never resolve to a C++ or TypeScript `Buffer`. Names that only match in another language are
      * reported as unresolved. Turn off to get language-agnostic (lexical-only) matching.
      */
     val sameGrammarResolutionOnly: Boolean = SymbolResolver.DEFAULT_SAME_GRAMMAR_ONLY,
    /**
     * Populate [FileRecord.referencesFrom] - the reverse of [FileRecord.referencesTo] - by
     * inverting the project-wide resolution graph. Only meaningful for [index]; [indexFile]
     * cannot see the rest of the project and leaves whatever the sidecar already held.
     */
    val computeIncomingReferences: Boolean = true,
    /** Maximum number of referencing files stored per record. */
    val maxIncomingReferencesPerFile: Int = 500,
    /** Maximum number of names stored per incoming link ([IncomingReference.count] stays exact). */
    val maxIncomingReferenceNames: Int = 100,
    /**
     * Omit `ambiguous = true` resolutions from `project.json` and the `package.json` rollups.
     * The per-file sidecars always keep the full set.
     */
    val hideAmbiguousInReports: Boolean = true,
    /**
     * Populate [FileRecord.relatedFiles] by IDF-weighted similarity of the extracted
     * tokens/symbols. Computed project-wide by [index]; kept in the sidecars *and* in the
     * reports (it survives [FileRecord.slim]).
     */
    val computeRelatedFiles: Boolean = true,
    /** Maximum number of neighbours stored per file. */
    val maxRelatedFiles: Int = RelatedFileAnalyzer.DEFAULT_MAX_RELATED,
    /** Minimum cosine similarity required before a neighbour is reported. */
    val minRelatedFileScore: Double = RelatedFileAnalyzer.DEFAULT_MIN_SCORE,
    /** Highest-IDF shared tokens stored as evidence with each relation. */
    val maxSharedTokensPerRelatedFile: Int = RelatedFileAnalyzer.DEFAULT_MAX_SHARED_TOKENS,
    /** Tokens appearing in more than this fraction of the indexed files are ignored. */
    val relatedFileMaxDocumentFrequency: Double = RelatedFileAnalyzer.DEFAULT_MAX_DOCUMENT_FREQUENCY_RATIO,
    /**
     * Keep the verbose `symbols` / `references` lists in the project manifest.
     * Off by default - the sidecars already hold the detail and the manifest would explode.
     */
    val includeDetailsInManifest: Boolean = false,
    /** Reuse an existing record when the source file's size and timestamp are unchanged. */
    val incremental: Boolean = true,
    /** Parse files on the common fork-join pool. */
    val parallel: Boolean = true,
    val followSymlinks: Boolean = false,
  )

  /** Everything known about one indexed source file. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class FileRecord(
    /**
     * Path of the described file, always '/'-separated and **relative to the report that holds
     * this record**: to the source file's own folder in the sidecars, to [Manifest.folder] in a
     * `package.json` rollup and to the crawl root in `project.json`.
     * In memory (and in the [Manifest] returned by [index]) it is always crawl-root relative.
     */
    val path: String = "",
    val name: String = "",
    val extension: String = "",
    val size: Long = 0,
    /** ISO-8601 instant of the source file's last modification. */
    val lastModified: String = "",
    /** SHA-256 of the file contents (hex). */
    val contentHash: String = "",
    /** Simple class name of the validator used. */
    val validator: String? = null,
    /** Total number of symbols including nested ones. */
    val symbolCount: Int = 0,
    val symbols: List<GrammarValidator.SymbolInfo> = emptyList(),
    /** Flattened dotted names, handy for quick lookups/greps. */
    val qualifiedNames: List<String> = emptyList(),
    /** Total number of grammatical references found, before any truncation. */
    val referenceCount: Int = 0,
    /** Grammar-level references to other symbols (no resolution performed). */
    val references: List<GrammarValidator.SymbolReference> = emptyList(),
    /** Distinct referenced names (sorted) - cheap enough to always keep. */
    val referencedNames: List<String> = emptyList(),
    /** Lexical resolutions of [referencedNames] against the project's [qualifiedNames]. */
    val referencesTo: List<SymbolResolver.Resolution> = emptyList(),
    /** Referenced names that matched no qualified name anywhere in the index. */
    val unresolvedNames: List<String> = emptyList(),
    /**
     * Upstream files that reference the declarations of this file, strongest link first.
     * The exact inverse of [referencesTo] across the whole index; self-references are omitted.
     */
    val referencesFrom: List<IncomingReference> = emptyList(),
    /**
     * Files with a similar symbol/reference vocabulary, nearest first.
     * Produced by [RelatedFileAnalyzer] (TF-IDF cosine over grammar-extracted tokens).
     */
    val relatedFiles: List<RelatedFileAnalyzer.RelatedFile> = emptyList(),
    val errors: List<GrammarValidator.ValidationError> = emptyList(),
  ) {
    /**
     * Manifest-friendly copy: drops the two huge detail lists (`symbols`, `references`).
     * Counts, [qualifiedNames], [referencedNames], [referencesTo], [referencesFrom] and
     * [relatedFiles] are preserved.
     */
    fun slim(): FileRecord = copy(symbols = emptyList(), references = emptyList())

    /**
     * Report-friendly copy: ambiguous resolutions are dropped, so `ambiguous = true`
     * never appears in `project.json` / `package.json` - in either direction.
     */
    fun withoutAmbiguousResolutions(): FileRecord {
      val ambiguousOut = referencesTo.any { it.ambiguous }
      val ambiguousIn = referencesFrom.any { it.ambiguousNames.isNotEmpty() }
      if (!ambiguousOut && !ambiguousIn) return this
      return copy(
        referencesTo = if (ambiguousOut) referencesTo.filterNot { it.ambiguous } else referencesTo,
        referencesFrom = if (ambiguousIn) referencesFrom.mapNotNull { it.withoutAmbiguous() } else referencesFrom,
      )
    }
  }
  /**
   * One incoming edge of the resolution graph: the file [path] resolves [names] to declarations
   * of the record holding this entry. The mirror image of [SymbolResolver.Resolution].
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  data class IncomingReference(
    /**
     * Path of the *referencing* file, '/'-separated and relative to the report that holds it
     * (same rules as [FileRecord.path]).
     */
    val path: String = "",
    /** Names declared here that [path] refers to, sorted; truncated to [Config.maxIncomingReferenceNames]. */
    val names: List<String> = emptyList(),
    /** Number of distinct referenced names, before any truncation of [names]. */
    val count: Int = 0,
    /** Subset of [names] whose resolution was ambiguous (the name matched several declarations). */
    val ambiguousNames: List<String> = emptyList(),
  ) {
    /** Report-friendly copy, or `null` when nothing unambiguous is left. */
    fun withoutAmbiguous(): IncomingReference? {
      if (ambiguousNames.isEmpty()) return this
      val drop = ambiguousNames.toSet()
      val kept = names.filterNot { it in drop }
      if (kept.isEmpty()) return null
      return copy(names = kept, count = maxOf(kept.size, count - ambiguousNames.size), ambiguousNames = emptyList())
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Failure(val path: String = "", val error: String = "")

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Manifest(
    val root: String = "",
    /**
     * Root-relative folder summarized by this report; `""` for the whole-project manifest.
     * Every path inside [files] / [failures] is relative to **this folder** (so `project.json`,
     * with `folder = ""`, keeps crawl-root relative paths).
     */
    val folder: String = "",
    val generatedAt: String = "",
    val fileCount: Int = 0,
    val symbolCount: Int = 0,
    val referenceCount: Int = 0,
    /** Number of referenced names that matched at least one declaration. */
    val resolvedNameCount: Int = 0,
    /** Number of referenced names that matched nothing. */
    val unresolvedNameCount: Int = 0,
    /** Total number of incoming (`referencesFrom`) links reported across [files]. */
    val incomingReferenceCount: Int = 0,
    /** Total number of related-file links reported across [files]. */
    val relatedFileCount: Int = 0,
    /** Slim records unless [Config.includeDetailsInManifest] is set. */
    val files: List<FileRecord> = emptyList(),
    val failures: List<Failure> = emptyList(),
  )

  /**
   * Crawl, parse, resolve references project-wide, write per-file records and the manifest.
   * The returned/persisted manifest omits the per-symbol and per-reference detail
   * (see [Config.includeDetailsInManifest]).
   */
  fun index(): Manifest {
    val start = System.currentTimeMillis()
    val files = collectFiles()
    log.info("Indexing {} file(s) under {}", files.size, root)
    val failures = Collections.synchronizedList(mutableListOf<Failure>())
    val stream = if (config.parallel) files.parallelStream() else files.stream()
    val parsed: List<Pair<Path, FileRecord>> = stream
      .map { file ->
        try {
          file to buildRecord(file)
        } catch (e: Throwable) {
          log.warn("Failed to index $file", e)
          failures.add(Failure(relativePath(file), e.message ?: e.javaClass.simpleName))
          null
        }
      }
      .filter { it != null }
      .map { it!! }
      .collect(Collectors.toList())
      .sortedBy { it.second.path }


    val resolved: List<FileRecord> =
      if (config.resolveReferences) SymbolResolver.resolve(
        parsed.map { it.second },
        config.maxResolutionTargets,
        config.excludeSelfFileResolutions,
         config.sameGrammarResolutionOnly,
      )
      else parsed.map { it.second }
    val related: List<FileRecord> =
      if (config.computeRelatedFiles) RelatedFileAnalyzer.analyze(resolved, relatedFileOptions())
      else resolved.map { if (it.relatedFiles.isEmpty()) it else it.copy(relatedFiles = emptyList()) }
    val linked: List<FileRecord> =
      if (config.computeIncomingReferences) linkIncomingReferences(related)
      else related.map { if (it.referencesFrom.isEmpty()) it else it.copy(referencesFrom = emptyList()) }
    val indexed: List<Pair<Path, FileRecord>> = parsed.mapIndexed { i, (path, _) -> path to linked[i] }


    val writeStream = if (config.parallel) indexed.parallelStream() else indexed.stream()
    writeStream.forEach { (path, record) ->
      try {
        writeJson(dataFileFor(path), folderRelative(record, folderOf(record.path)))
      } catch (e: Throwable) {
        log.warn("Unable to write record for $path", e)
        failures.add(Failure(record.path, e.message ?: e.javaClass.simpleName))
      }
    }
    val records = indexed.map { it.second }
    val sortedFailures = failures.sortedBy { it.path }
    val reportRecords = records.map { reportRecord(it) }
    val manifest = buildManifest("", reportRecords, sortedFailures, Instant.now().toString())
    writeJson(manifestFile(), manifest)
    if (config.writePackageManifests) writePackageManifests(reportRecords, sortedFailures, manifest.generatedAt)
    if (config.writeViewer) writeViewer()
    log.info(
      "Indexed {} file(s) / {} symbol(s) / {} reference(s) / {} resolved / {} unresolved / {} incoming link(s) / {} related link(s) in {} ms -> {}",
      manifest.fileCount, manifest.symbolCount, manifest.referenceCount,
      manifest.resolvedNameCount, manifest.unresolvedNameCount,
      manifest.incomingReferenceCount, manifest.relatedFileCount,
      System.currentTimeMillis() - start, manifestFile()
    )
    return manifest
  }

  /**
   * Parse a single file and (re)write its `.data/<name>.json` sidecar.
   * References are resolved against this file's own symbols only - use [index] for
   * project-wide resolution; [FileRecord.referencesFrom] therefore keeps whatever the previous
   * (project-wide) run stored and is not recomputed here.
   */
  fun indexFile(file: Path): FileRecord {
    val parsed = buildRecord(file)
    val record = if (config.resolveReferences)
       SymbolResolver.resolve(
         listOf(parsed),
         config.maxResolutionTargets,
         config.excludeSelfFileResolutions,
         config.sameGrammarResolutionOnly,
       ).first()
    else parsed
    writeJson(dataFileFor(file), folderRelative(record, folderOf(record.path)))
    return record
  }

  /** Parse a single file without touching the sidecar. */
  private fun buildRecord(file: Path): FileRecord {
    val fileName = file.fileName.toString()
    val validator = FileValidators.getLanguageValidator(fileName)
      ?: throw IllegalArgumentException("No language validator for $fileName")
    val attrs = Files.readAttributes(file, BasicFileAttributes::class.java)
    val lastModified = attrs.lastModifiedTime().toInstant().toString()
    val dataFile = dataFileFor(file)
    if (config.incremental) {
      val existing = readRecord(dataFile)
      if (existing != null && existing.size == attrs.size() && existing.lastModified == lastModified) {
        // sidecars store folder-relative paths - bring them back to crawl-root relative form
        val relPath = relativePath(file)
        val restored = rootRelative(existing, folderOf(relPath))
        return if (restored.path == relPath) restored else restored.copy(path = relPath)
      }
    }
    val bytes = Files.readAllBytes(file)
    val code = String(bytes, StandardCharsets.UTF_8)
    val symbols = try {
      validator.extractPublicSymbols(code)
    } catch (e: Throwable) {
      log.warn("Error extracting symbols from $file", e)
      emptyList()
    }
    val errors = if (!config.includeValidationErrors) emptyList() else try {
      val validateGrammar = validator.validateGrammar(code.trim().sanitizeSource())
      if(validateGrammar.isNotEmpty()) {
        log.debug("Validation errors for {}: {}", file, validateGrammar.joinToString("; ") { it.message })
      }
      validateGrammar
    } catch (e: Throwable) {
      log.warn("Error validating $file", e)
      emptyList()
    }
    val references = if (!config.includeReferences) emptyList() else try {
      validator.extractSymbolReferences(code, symbols)
    } catch (e: Throwable) {
      log.warn("Error extracting references from $file", e)
      emptyList()
    }
    val record = FileRecord(
      path = relativePath(file),
      name = fileName,
      extension = fileName.substringAfterLast('.', ""),
      size = attrs.size(),
      lastModified = lastModified,
      contentHash = sha256(bytes),
      validator = validator.javaClass.simpleName,
      symbolCount = symbols.sumOf { it.flatten().size },
      symbols = symbols,
      qualifiedNames = symbols.flatMap { it.qualifiedNames() },
      referenceCount = references.size,
      references = if (config.includeReferenceDetails) references.take(config.maxReferencesPerFile) else emptyList(),
      referencedNames = references.map { it.name }.distinct().sorted(),
      errors = errors,
    )
    return record
  }

  /** All indexable files under [root] (or [root] itself when it is a file). */
  fun collectFiles(): List<Path> {
    val result = mutableListOf<Path>()
    if (!Files.isDirectory(root)) {
      if (Files.isRegularFile(root) && FileValidators.isSupported(root.fileName.toString())) result.add(root)
      return result
    }
    val options = if (config.followSymlinks) setOf(FileVisitOption.FOLLOW_LINKS) else emptySet()
    Files.walkFileTree(root, options, Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (dir == root) return FileVisitResult.CONTINUE
        val name = dir.fileName?.toString() ?: return FileVisitResult.CONTINUE
        if (name == config.dataDirName) return FileVisitResult.SKIP_SUBTREE
        if (name in config.excludedDirNames) return FileVisitResult.SKIP_SUBTREE
        if (config.skipHiddenDirs && name.startsWith(".")) return FileVisitResult.SKIP_SUBTREE
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (!attrs.isRegularFile) return FileVisitResult.CONTINUE
        if (attrs.size() > config.maxFileSizeBytes) {
          log.debug("Skipping oversized file {} ({} bytes)", file, attrs.size())
          return FileVisitResult.CONTINUE
        }
        if (FileValidators.isSupported(file.fileName.toString())) result.add(file)
        return FileVisitResult.CONTINUE
      }

      override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
        log.debug("Unable to visit {}: {}", file, exc.message)
        return FileVisitResult.CONTINUE
      }
    })
    result.sort()
    return result
  }

  /** `package/foo.java` -> `package/.data/foo.java.json` */
  fun dataFileFor(file: Path): Path =
    (file.parent ?: root).resolve(config.dataDirName).resolve(file.fileName.toString() + ".json")

  fun manifestFile(): Path = root.resolve(config.dataDirName).resolve(config.manifestName)

  /** `<root>/<dataDirName>/<viewerName>` - sibling of [manifestFile]. */
  fun viewerFile(): Path = root.resolve(config.dataDirName).resolve(config.viewerName)

  /**
   * Copy [Config.viewerResource] from the classpath to [viewerFile].
   * Missing resources / IO failures are logged and ignored - the index itself is still valid.
   */
  fun writeViewer(): Path? {
    val bytes = loadResource(config.viewerResource)
    if (bytes == null) {
      log.warn("Viewer resource {} not found on the classpath; skipping {}", config.viewerResource, config.viewerName)
      return null
    }
    return try {
      val target = viewerFile()
      target.parent?.let { Files.createDirectories(it) }
      Files.write(target, bytes)
      log.info("Wrote viewer -> {}", target)
      target
    } catch (e: Throwable) {
      log.warn("Unable to write viewer ${config.viewerName}", e)
      null
    }
  }

  private fun loadResource(resource: String): ByteArray? {
    val name = resource.removePrefix("/")
    val streams = sequenceOf(
      { SymbolIndexer::class.java.getResourceAsStream(resource) },
      { SymbolIndexer::class.java.classLoader?.getResourceAsStream(name) },
      { Thread.currentThread().contextClassLoader?.getResourceAsStream(name) },
    )
    for (open in streams) {
      val stream = try {
        open()
      } catch (e: Throwable) {
        null
      } ?: continue
      stream.use { return it.readBytes() }
    }
    return null
  }


  /** Loads `project.json`; paths are converted back to crawl-root relative form. */
  fun loadManifest(): Manifest? = readJson(manifestFile(), Manifest::class.java)?.let { rootRelative(it) }

  /** `<folder>/<dataDirName>/<packageManifestName>`; `""` / blank means the crawl root. */
  fun packageManifestFile(folder: String): Path =
    (if (folder.isBlank()) root else root.resolve(folder)).resolve(config.dataDirName)
      .resolve(config.packageManifestName)

  /** `dir/.data/package.json` for an absolute/relative directory [dir]. */
  fun packageManifestFile(dir: Path): Path =
    dir.resolve(config.dataDirName).resolve(config.packageManifestName)

  /** Loads a folder rollup; paths are converted back to crawl-root relative form. */
  fun loadPackageManifest(dir: Path): Manifest? =
    readJson(packageManifestFile(dir), Manifest::class.java)?.let { rootRelative(it) }

  /** Apply the report rules: hide ambiguous resolutions, then slim unless details were requested. */
  private fun reportRecord(record: FileRecord): FileRecord {
    val filtered = if (config.hideAmbiguousInReports) record.withoutAmbiguousResolutions() else record
    return if (config.includeDetailsInManifest) filtered else filtered.slim()
  }
  /**
   * Invert the resolution graph: every record learns which other files reference its declarations.
   * Runs over the already-resolved [records] (crawl-root relative paths); self-references are
   * dropped and stale [FileRecord.referencesFrom] entries (e.g. restored from a sidecar by the
   * incremental path) are cleared.
   */
  private fun linkIncomingReferences(records: List<FileRecord>): List<FileRecord> {
    if (records.isEmpty()) return records
    val byTarget = HashMap<String, MutableMap<String, MutableSet<String>>>()
    val ambiguousByTarget = HashMap<String, MutableMap<String, MutableSet<String>>>()
    records.forEach { source ->
      source.referencesTo.forEach { resolution ->
        resolution.targets.asSequence().map { it.path }.distinct().forEach { target ->
          if (target.isBlank() || target == source.path) return@forEach
          byTarget.getOrPut(target) { HashMap() }.getOrPut(source.path) { HashSet() }.add(resolution.name)
          if (resolution.ambiguous) ambiguousByTarget.getOrPut(target) { HashMap() }
            .getOrPut(source.path) { HashSet() }.add(resolution.name)
        }
      }
    }
    return records.map { record ->
      val bySource = byTarget[record.path]
      if (bySource.isNullOrEmpty())
        return@map if (record.referencesFrom.isEmpty()) record else record.copy(referencesFrom = emptyList())
      val ambiguousBySource = ambiguousByTarget[record.path] ?: emptyMap()
      val links = bySource.entries
        .map { (source, referenced) ->
          val sorted = referenced.sorted()
          val kept = sorted.take(config.maxIncomingReferenceNames)
          val keptSet = kept.toSet()
          IncomingReference(
            path = source,
            names = kept,
            count = sorted.size,
            ambiguousNames = (ambiguousBySource[source] ?: emptySet()).filter { it in keptSet }.sorted(),
          )
        }
        .sortedWith(compareByDescending<IncomingReference> { it.count }.thenBy { it.path })
        .take(config.maxIncomingReferencesPerFile)
      record.copy(referencesFrom = links)
    }
  }


  /** [RelatedFileAnalyzer] settings derived from [config]. */
  private fun relatedFileOptions() = RelatedFileAnalyzer.Options(
    maxRelated = config.maxRelatedFiles,
    minScore = config.minRelatedFileScore,
    maxSharedTokens = config.maxSharedTokensPerRelatedFile,
    maxDocumentFrequencyRatio = config.relatedFileMaxDocumentFrequency,
  )

  /**
   * Build a report over [records] (already passed through [reportRecord]).
   * [records] / [failures] arrive crawl-root relative and are rewritten relative to [folder].
   */
  private fun buildManifest(
    folder: String,
    records: List<FileRecord>,
    failures: List<Failure>,
    generatedAt: String,
  ): Manifest = Manifest(
    root = root.toAbsolutePath().normalize().toString(),
    folder = folder,
    generatedAt = generatedAt,
    fileCount = records.size,
    symbolCount = records.sumOf { it.symbolCount },
    referenceCount = records.sumOf { it.referenceCount },
    resolvedNameCount = records.sumOf { it.referencesTo.size },
    unresolvedNameCount = records.sumOf { it.unresolvedNames.size },
    incomingReferenceCount = records.sumOf { it.referencesFrom.size },
    relatedFileCount = records.sumOf { it.relatedFiles.size },
    files = records.map { folderRelative(it, folder) },
    failures = failures.map { folderRelative(it, folder) },
  )

  /** `a/b/Foo.kt` -> `["a", "a/b"]`; the crawl root itself (`""`, i.e. `project.json`) is excluded. */
  private fun folderAncestors(path: String): List<String> {
    val segments = path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }.dropLast(1)
    return segments.indices.map { i -> segments.subList(0, i + 1).joinToString("/") }
  }

  private fun isRootRelative(path: String): Boolean =
    path.isNotBlank() && !path.startsWith("/") && !path.contains("..") && !path.contains(':')

  /** One rollup per folder, summarizing that folder and everything beneath it. */
  private fun writePackageManifests(records: List<FileRecord>, failures: List<Failure>, generatedAt: String) {
    val recordsByFolder = LinkedHashMap<String, MutableList<FileRecord>>()
    records.filter { isRootRelative(it.path) }.forEach { record ->
      folderAncestors(record.path).forEach { recordsByFolder.getOrPut(it) { mutableListOf() }.add(record) }
    }
    val failuresByFolder = HashMap<String, MutableList<Failure>>()
    failures.filter { isRootRelative(it.path) }.forEach { failure ->
      folderAncestors(failure.path).forEach { failuresByFolder.getOrPut(it) { mutableListOf() }.add(failure) }
    }
    val folders = LinkedHashSet<String>().apply {
      addAll(recordsByFolder.keys)
      addAll(failuresByFolder.keys)
    }
    var written = 0
    folders.sorted().forEach { folder ->
      val rollup = buildManifest(
        folder,
        recordsByFolder[folder]?.sortedBy { it.path } ?: emptyList(),
        failuresByFolder[folder]?.sortedBy { it.path } ?: emptyList(),
        generatedAt,
      )
      try {
        writeJson(packageManifestFile(folder), rollup)
        written++
      } catch (e: Throwable) {
        log.warn("Unable to write folder rollup for $folder", e)
      }
    }
    log.info("Wrote {} folder rollup(s) named {}", written, config.packageManifestName)
  }


  /** Remove every generated data folder under [root]. */
  fun clean() {
    if (!Files.isDirectory(root)) return
    val dataDirs = mutableListOf<Path>()
    Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (dir.fileName?.toString() == config.dataDirName) {
          dataDirs.add(dir)
          return FileVisitResult.SKIP_SUBTREE
        }
        return FileVisitResult.CONTINUE
      }
    })
    dataDirs.forEach { dir ->
      Files.walk(dir).use { walk ->
        walk.sorted(Comparator.reverseOrder()).forEach { p ->
          try {
            Files.deleteIfExists(p)
          } catch (e: Exception) {
            log.warn("Unable to delete $p", e)
          }
        }
      }
    }
    log.info("Removed {} data folder(s) under {}", dataDirs.size, root)
  }

  private fun relativePath(file: Path): String =
    try {
      root.relativize(file).toString().replace(File.separatorChar, '/')
    } catch (e: IllegalArgumentException) {
      file.toString().replace(File.separatorChar, '/')
    }

  private fun readRecord(dataFile: Path): FileRecord? = readJson(dataFile, FileRecord::class.java)

  private fun <T> readJson(path: Path, type: Class<T>): T? = try {
    if (Files.isRegularFile(path)) mapper.readValue(Files.readAllBytes(path), type) else null
  } catch (e: Throwable) {
    log.debug("Unable to read {}: {}", path, e.message)
    null
  }

  private fun writeJson(path: Path, value: Any) {
    path.parent?.let { Files.createDirectories(it) }
    Files.write(path, mapper.writeValueAsBytes(value))
  }

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

  companion object {
    private val log = LoggerFactory.getLogger(SymbolIndexer::class.java)

    val mapper: ObjectMapper by lazy {
      ObjectMapper()
        .findAndRegisterModules()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
    }
    /* ------------------------------------------------------------------ *
     *  report-relative path plumbing                                     *
     * ------------------------------------------------------------------ */
    private fun pathSegments(path: String): List<String> =
      path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
    private fun isAbsoluteish(path: String) = path.startsWith("/") || path.contains(':')
    /** Folder part of a root-relative '/'-separated path (`""` for files directly in the root). */
    @JvmStatic
    fun folderOf(path: String): String = path.replace('\\', '/').substringBeforeLast('/', "")
    /**
     * [path] as seen from the folder [base] (both crawl-root relative, '/'-separated):
     * `relativeTo("a/b", "a/b/Foo.kt") == "Foo.kt"`, `relativeTo("a/b", "a/c/Bar.kt") == "../c/Bar.kt"`.
     * A blank [base] (the crawl root) or an absolute [path] is returned unchanged.
     */
    @JvmStatic
    fun relativeTo(base: String, path: String): String {
      if (base.isBlank() || path.isBlank() || isAbsoluteish(path)) return path
      val from = pathSegments(base)
      val to = pathSegments(path)
      if (to.isEmpty()) return path
      var common = 0
      while (common < from.size && common < to.size - 1 && from[common] == to[common]) common++
      val up = List(from.size - common) { ".." }
      return (up + to.subList(common, to.size)).joinToString("/")
    }
    /** Inverse of [relativeTo]: `resolvePath("a/b", "../c/Bar.kt") == "a/c/Bar.kt"`. */
    @JvmStatic
    fun resolvePath(base: String, path: String): String {
      if (path.isBlank() || isAbsoluteish(path)) return path
      val segments = pathSegments(base).toMutableList()
      pathSegments(path).forEach {
        if (it == "..") {
          if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
        } else segments.add(it)
      }
      return segments.joinToString("/")
    }
    /** Report-local copy of [record]: every stored path becomes relative to the folder [base]. */
    @JvmStatic
    fun folderRelative(record: FileRecord, base: String): FileRecord =
      if (base.isBlank()) record else record.copy(
        path = relativeTo(base, record.path),
        referencesTo = record.referencesTo.map { r ->
          r.copy(targets = r.targets.map { t -> t.copy(path = relativeTo(base, t.path)) })
        },
        referencesFrom = record.referencesFrom.map { it.copy(path = relativeTo(base, it.path)) },
        relatedFiles = record.relatedFiles.map { it.copy(path = relativeTo(base, it.path)) },
      )
    @JvmStatic
    fun folderRelative(failure: Failure, base: String): Failure =
      if (base.isBlank()) failure else failure.copy(path = relativeTo(base, failure.path))
    /** Undo [folderRelative] for a record stored in the report of folder [base]. */
    @JvmStatic
    fun rootRelative(record: FileRecord, base: String): FileRecord =
      if (base.isBlank()) record else record.copy(
        path = resolvePath(base, record.path),
        referencesTo = record.referencesTo.map { r ->
          r.copy(targets = r.targets.map { t -> t.copy(path = resolvePath(base, t.path)) })
        },
        referencesFrom = record.referencesFrom.map { it.copy(path = resolvePath(base, it.path)) },
        relatedFiles = record.relatedFiles.map { it.copy(path = resolvePath(base, it.path)) },
      )
    /** Rewrite a loaded report's [Manifest.folder]-relative paths back to crawl-root relative ones. */
    @JvmStatic
    fun rootRelative(manifest: Manifest): Manifest {
      val base = manifest.folder
      if (base.isBlank()) return manifest
      return manifest.copy(
        files = manifest.files.map { rootRelative(it, base) },
        failures = manifest.failures.map { it.copy(path = resolvePath(base, it.path)) },
      )
    }


    /** One-liner: index [root] with default settings. */
    @JvmStatic
    fun index(root: File): Manifest = SymbolIndexer(root).index()

    /**
     * CLI: `SymbolIndexer <root> [--clean] [--no-incremental] [--sequential] [--no-errors]
     *      [--no-references] [--no-reference-details] [--no-resolve] [--manifest-details]
      *      [--self-refs] [--cross-grammar] [--keep-ambiguous] [--no-package-manifests]
      *      [--no-viewer] [--no-related] [--no-incoming]`
     */
    @JvmStatic
    fun main(args: Array<String>) {
      val flags = args.filter { it.startsWith("--") }.toSet()
      val rootArg = args.firstOrNull { !it.startsWith("--") } ?: "."
      val root = Paths.get(rootArg).toAbsolutePath().normalize()
      val indexer = SymbolIndexer(
        root, Config(
          writePackageManifests = "--no-package-manifests" !in flags,
          writeViewer = "--no-viewer" !in flags,
          incremental = "--no-incremental" !in flags,
          parallel = "--sequential" !in flags,
          includeValidationErrors = "--no-errors" !in flags,
          includeReferences = "--no-references" !in flags,
          includeReferenceDetails = "--no-reference-details" !in flags,
          resolveReferences = "--no-resolve" !in flags,
          excludeSelfFileResolutions = "--self-refs" !in flags,
           sameGrammarResolutionOnly = "--cross-grammar" !in flags,
          computeIncomingReferences = "--no-incoming" !in flags,
          hideAmbiguousInReports = "--keep-ambiguous" !in flags,
          includeDetailsInManifest = "--manifest-details" in flags,
          computeRelatedFiles = "--no-related" !in flags,
        )
      )
      if ("--clean" in flags) indexer.clean()
      val manifest = indexer.index()
      println(
        "${manifest.fileCount} files, ${manifest.symbolCount} symbols, " +
            "${manifest.referenceCount} references, ${manifest.resolvedNameCount} resolved, " +
            "${manifest.unresolvedNameCount} unresolved, ${manifest.incomingReferenceCount} incoming links, " +
            "${manifest.relatedFileCount} related links " +
            "-> ${indexer.manifestFile()}"
      )
      manifest.failures.forEach { println("FAILED ${it.path}: ${it.error}") }
    }
  }
}




 /**
  * Characters that are invisible (or effectively invisible) and are never meaningful in source code,
  * but which `String.trim()` will NOT remove because `Char.isWhitespace()` returns false for them.
  * The classic offender is the UTF-8 BOM (U+FEFF, "ZERO WIDTH NO-BREAK SPACE") left at the head of a file.
  */
 private val INVISIBLE_JUNK: Set<Char> = setOf(
   '\uFEFF', // BOM / ZERO WIDTH NO-BREAK SPACE
   '\uFFFE', // byte-swapped BOM (mis-decoded file)
   '\uFFFD', // replacement char from a bad decode
   '\u0000', // stray NUL
   '\u200B', // zero width space
   '\u200C', // zero width non-joiner
   '\u200D', // zero width joiner
   '\u2060', // word joiner
   '\u00AD', // soft hyphen
   '\u180E', // mongolian vowel separator
   '\u200E', '\u200F', // LRM / RLM
   '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', // bidi embedding/override (trojan-source)
   '\u2066', '\u2067', '\u2068', '\u2069' // bidi isolates
 )

 /** Whitespace-ish characters that should be normalized to a plain space rather than deleted. */
 private val EXOTIC_SPACES: Set<Char> = setOf(
   '\u00A0', // no-break space
   '\u2007', // figure space
   '\u202F', // narrow no-break space
   '\u2028', '\u2029' // line/paragraph separator
 )

 private fun Char.isInvisibleJunk(): Boolean =
   this in INVISIBLE_JUNK || Character.getType(this) == Character.FORMAT.toInt()

 /**
  * Strips BOM/zero-width/bidi-control characters, normalizes exotic spaces, and trims.
  * Safe to call on any source text before handing it to a parser/validator.
  */
 fun String.sanitizeSource(): String {
   if (isEmpty()) return this
   val needsWork = any { it.isInvisibleJunk() || it in EXOTIC_SPACES }
   val cleaned = if (!needsWork) this else buildString(length) {
     for (c in this@sanitizeSource) when {
       c.isInvisibleJunk() -> {} // drop
       c == '\u2028' || c == '\u2029' -> append('\n')
       c in EXOTIC_SPACES -> append(' ')
       else -> append(c)
     }
   }
   return cleaned.trim()
 }