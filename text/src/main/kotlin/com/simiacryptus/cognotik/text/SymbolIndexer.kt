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
 * Referenced names are resolved lexically against every file's qualified names by
 * [SymbolResolver]; the result is stored in `resolutions` / `unresolvedNames` in both the
 * sidecars and the manifest.
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
     * Omit `ambiguous = true` resolutions from `project.json` and the `package.json` rollups.
     * The per-file sidecars always keep the full set.
     */
    val hideAmbiguousInReports: Boolean = true,
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
    /** Path relative to the crawl root, always '/'-separated. */
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
    val resolutions: List<SymbolResolver.Resolution> = emptyList(),
    /** Referenced names that matched no qualified name anywhere in the index. */
    val unresolvedNames: List<String> = emptyList(),
    val errors: List<GrammarValidator.ValidationError> = emptyList(),
  ) {
    /**
     * Manifest-friendly copy: drops the two huge detail lists (`symbols`, `references`).
     * Counts, [qualifiedNames], [referencedNames] and [resolutions] are preserved.
     */
    fun slim(): FileRecord = copy(symbols = emptyList(), references = emptyList())
    /**
     * Report-friendly copy: ambiguous resolutions are dropped, so `ambiguous = true`
     * never appears in `project.json` / `package.json`.
     */
    fun withoutAmbiguousResolutions(): FileRecord =
      if (resolutions.none { it.ambiguous }) this else copy(resolutions = resolutions.filterNot { it.ambiguous })
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Failure(val path: String = "", val error: String = "")

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class Manifest(
    val root: String = "",
    /**
     * Root-relative folder summarized by this report; `""` for the whole-project manifest.
     * [FileRecord.path] entries stay relative to [root], never to the folder.
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
      )
      else parsed.map { it.second }
    val indexed: List<Pair<Path, FileRecord>> = parsed.mapIndexed { i, (path, _) -> path to resolved[i] }


    val writeStream = if (config.parallel) indexed.parallelStream() else indexed.stream()
    writeStream.forEach { (path, record) ->
      try {
        writeJson(dataFileFor(path), record)
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
      "Indexed {} file(s) / {} symbol(s) / {} reference(s) / {} resolved / {} unresolved in {} ms -> {}",
      manifest.fileCount, manifest.symbolCount, manifest.referenceCount,
      manifest.resolvedNameCount, manifest.unresolvedNameCount,
      System.currentTimeMillis() - start, manifestFile()
    )
    return manifest
  }

  /**
   * Parse a single file and (re)write its `.data/<name>.json` sidecar.
   * References are resolved against this file's own symbols only - use [index] for
   * project-wide resolution.
   */
  fun indexFile(file: Path): FileRecord {
    val parsed = buildRecord(file)
    val record = if (config.resolveReferences)
      SymbolResolver.resolve(listOf(parsed), config.maxResolutionTargets, config.excludeSelfFileResolutions).first()
    else parsed
    writeJson(dataFileFor(file), record)
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
        return existing
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
      validator.validateGrammar(code)
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


  fun loadManifest(): Manifest? = readJson(manifestFile(), Manifest::class.java)
  /** `<folder>/<dataDirName>/<packageManifestName>`; `""` / blank means the crawl root. */
  fun packageManifestFile(folder: String): Path =
    (if (folder.isBlank()) root else root.resolve(folder)).resolve(config.dataDirName).resolve(config.packageManifestName)
  /** `dir/.data/package.json` for an absolute/relative directory [dir]. */
  fun packageManifestFile(dir: Path): Path =
    dir.resolve(config.dataDirName).resolve(config.packageManifestName)
  fun loadPackageManifest(dir: Path): Manifest? = readJson(packageManifestFile(dir), Manifest::class.java)
  /** Apply the report rules: hide ambiguous resolutions, then slim unless details were requested. */
  private fun reportRecord(record: FileRecord): FileRecord {
    val filtered = if (config.hideAmbiguousInReports) record.withoutAmbiguousResolutions() else record
    return if (config.includeDetailsInManifest) filtered else filtered.slim()
  }
  /** Build a report over [records] (already passed through [reportRecord]). */
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
    resolvedNameCount = records.sumOf { it.resolutions.size },
    unresolvedNameCount = records.sumOf { it.unresolvedNames.size },
    files = records,
    failures = failures,
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

    /** One-liner: index [root] with default settings. */
    @JvmStatic
    fun index(root: File): Manifest = SymbolIndexer(root).index()

    /**
     * CLI: `SymbolIndexer <root> [--clean] [--no-incremental] [--sequential] [--no-errors]
     *      [--no-references] [--no-reference-details] [--no-resolve] [--manifest-details]
      *      [--self-refs] [--keep-ambiguous] [--no-package-manifests] [--no-viewer]`
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
          hideAmbiguousInReports = "--keep-ambiguous" !in flags,
          includeDetailsInManifest = "--manifest-details" in flags,
        )
      )
      if ("--clean" in flags) indexer.clean()
      val manifest = indexer.index()
      println(
        "${manifest.fileCount} files, ${manifest.symbolCount} symbols, " +
            "${manifest.referenceCount} references, ${manifest.resolvedNameCount} resolved, " +
            "${manifest.unresolvedNameCount} unresolved -> ${indexer.manifestFile()}"
      )
      manifest.failures.forEach { println("FAILED ${it.path}: ${it.error}") }
    }
  }
}