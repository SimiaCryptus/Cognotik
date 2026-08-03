package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.text.JsonSchemaDescriber
import com.simiacryptus.cognotik.text.SymbolIndexer
import com.simiacryptus.cognotik.text.caveman.Caveman
import com.simiacryptus.cognotik.text.caveman.CavemanConfig
import com.simiacryptus.cognotik.text.caveman.DomainTermRegistry
import com.simiacryptus.cognotik.text.caveman.FrequencySalienceExtractor
import com.simiacryptus.cognotik.text.caveman.GrammarTemplateEngine
import com.simiacryptus.cognotik.text.caveman.LightEnglishStemmer
import com.simiacryptus.cognotik.text.caveman.PorterStemmer
import com.simiacryptus.cognotik.text.caveman.PosCategory
import com.simiacryptus.cognotik.text.caveman.TextRankSalienceExtractor
import com.simiacryptus.cognotik.webui.servlet.action.FsActionContext
import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import com.simiacryptus.cognotik.webui.servlet.util.FsPath
import com.simiacryptus.cognotik.webui.servlet.util.FsTarget
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Static-analysis / text utilities exposed through FS API v1:
 *
 *  * `GET  /.fsapi/v1/symbols`   read the [SymbolIndexer] sidecar (`.data/<name>.json`)
 *                                or folder rollup (`.data/package.json`) for a path
 *  * `POST /.fsapi/v1/symbols`   (re)build the index for a file or subtree — mutating,
 *                                because it writes `.data` sidecars
 *  * `GET|POST /.fsapi/v1/describe`  [JsonSchemaDescriber] over a JSON file or inline text
 *  * `GET|POST /.fsapi/v1/caveman`   [Caveman] compression of a text file or inline text
 *
 * Every entry point obeys the same invariants as the rest of the API: paths are resolved
 * through [FsPath], hidden paths answer ENOENT, mutations require an authenticated caller
 * plus a writable, non-read-only target, and each capability can be switched off in
 * [FsApiConfig] (producing a clean ENOSYS rather than a silent no-op).
 *
 * Known limitation: [SymbolIndexer] performs its own crawl and only skips dot-directories,
 * so a `.hidden` marker cannot prevent a file from being *indexed*. Records for hidden
 * paths are therefore filtered out of every response ([sanitize]), and the host-absolute
 * crawl root is rewritten to its virtual form so no host path escapes.
 */
object FsToolsHandler {
  private val log = LoggerFactory.getLogger(FsToolsHandler::class.java)

  // --------------------------------------------------------- HTTP adapters

  fun httpSymbols(ctx: FsActionContext) =
    writeJson(ctx.resp, HttpServletResponse.SC_OK, symbols(ctx.root, ctx.config, options(ctx)))

  fun httpIndexSymbols(ctx: FsActionContext) =
    writeJson(
      ctx.resp, HttpServletResponse.SC_OK,
      indexSymbols(ctx.root, ctx.config, options(ctx), ctx.writeAllowed)
    )

  fun httpDescribe(ctx: FsActionContext) {
    val opts = options(ctx)
    val payload = describe(ctx.root, ctx.config, opts)
    if (str(opts, "format")?.lowercase() == "text") {
      writeText(ctx.resp, payload["description"]?.toString() ?: "")
    } else {
      writeJson(ctx.resp, HttpServletResponse.SC_OK, payload)
    }
  }

  fun httpCaveman(ctx: FsActionContext) {
    val opts = options(ctx)
    val payload = caveman(ctx.root, ctx.config, opts)
    if (str(opts, "format")?.lowercase() == "text") {
      writeText(ctx.resp, payload["output"]?.toString() ?: "")
    } else {
      writeJson(ctx.resp, HttpServletResponse.SC_OK, payload)
    }
  }

  // ------------------------------------------------------------- core ops
  // HTTP-agnostic so /batch can reuse them verbatim (the batch op object *is*
  // the option map).

  /** Reads a previously built index; never touches the filesystem beyond `.data`. */
  fun symbols(root: File, config: FsApiConfig, options: Map<String, Any?>): Map<String, Any?> {
    requireCapability(config.symbolsEnabled, "symbols")
    val target = visible(root, str(options, "path") ?: "/", "symbols")
    if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, "symbols", target.virtual)
    val indexer = SymbolIndexer(root, indexerConfig(config, options))
    if (target.file.isDirectory) {
      val isRoot = sameFile(target.file, root)
      val manifestFile =
        if (isRoot) indexer.manifestFile() else indexer.packageManifestFile(target.file.toPath())
      val manifest = readIndexJson(manifestFile, SymbolIndexer.Manifest::class.java)
        ?: throw FsException(
          FsErrorCode.ENOENT, "symbols", target.virtual,
          "no symbol index for this folder; POST /.fsapi/v1/symbols to build one"
        )
      return linkedMapOf(
        "path" to target.virtual,
        "kind" to "manifest",
        "generatedAt" to manifest.generatedAt,
        "manifest" to toMap(sanitize(root, manifest))
      )
    }
    val record = readIndexJson(indexer.dataFileFor(target.file.toPath()), SymbolIndexer.FileRecord::class.java)
      ?: throw FsException(
        FsErrorCode.ENOENT, "symbols", target.virtual,
        "no symbol index for this file; POST /.fsapi/v1/symbols to build one"
      )
    return linkedMapOf(
      "path" to target.virtual,
      "kind" to "file",
      "stale" to stale(target.file, record),
      "record" to toMap(record)
    )
  }

  /** Builds/refreshes the index. Mutating: writes `.data/<name>.json` sidecars. */
  fun indexSymbols(
    root: File,
    config: FsApiConfig,
    options: Map<String, Any?>,
    writeAllowed: Boolean,
  ): Map<String, Any?> {
    requireCapability(config.symbolsEnabled, "symbols")
    if (!writeAllowed) {
      throw FsException(FsErrorCode.EACCES, "symbols", null, "authentication required to build an index")
    }
    if (config.readOnly) throw FsException(FsErrorCode.EROFS, "symbols", null)
    val target = visible(root, str(options, "path") ?: "/", "symbols")
    if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, "symbols", target.virtual)
    if (FileAccessControl.isReadOnly(root, target.file)) {
      throw FsException(FsErrorCode.EACCES, "symbols", target.virtual, "target is read-only")
    }
    val indexerConfig = indexerConfig(config, options)
    val started = System.nanoTime()
    if (!target.file.isDirectory) {
      val record = SymbolIndexer(root, indexerConfig).indexFile(target.file.toPath())
      log.info("indexed symbols for {} in {}ms", target.virtual, (System.nanoTime() - started) / 1_000_000)
      return linkedMapOf(
        "path" to target.virtual,
        "kind" to "file",
        "record" to toMap(record)
      )
    }
    val indexer = SymbolIndexer(target.file, indexerConfig)
    if (flag(options, "clean", false)) indexer.clean()
    val manifest = indexer.index()
    log.info(
      "indexed {} file(s) / {} symbol(s) under {} in {}ms",
      manifest.fileCount, manifest.symbolCount, target.virtual, (System.nanoTime() - started) / 1_000_000
    )
    return linkedMapOf(
      "path" to target.virtual,
      "kind" to "manifest",
      "files" to manifest.fileCount,
      "symbols" to manifest.symbolCount,
      "manifest" to toMap(sanitize(root, manifest))
    )
  }

  fun describe(root: File, config: FsApiConfig, options: Map<String, Any?>): Map<String, Any?> {
    requireCapability(config.describeEnabled, "describe")
    val (text, source) = inputText(root, config, options, "describe")
    val mode = (str(options, "mode") ?: "auto").lowercase()
    val describer = JsonSchemaDescriber(
      JsonSchemaDescriber.Config(
        topN = int(options, "topN") ?: 5,
        maxDepth = int(options, "maxDepth") ?: 16,
        maxDetailedKeys = int(options, "maxKeys") ?: 24,
        maxValueChars = int(options, "maxValue") ?: 48,
        detectRecursion = flag(options, "recursion", true),
        showSummary = flag(options, "summary", true)
      )
    )
    val samples = flag(options, "samples", false)
    val description = try {
      when (mode) {
        "schema" -> describer.describeSchemaJson(text)
        "data" -> describer.describeDataJson(text, samples)
        "auto" -> describer.describe(text, samples)
        else -> throw FsException(
          FsErrorCode.EINVAL, "describe", source, "unknown mode '$mode' (auto|data|schema)"
        )
      }
    } catch (e: FsException) {
      throw e
    } catch (e: Exception) {
      log.debug("describe failed for {}", source, e)
      throw FsException(FsErrorCode.EINVAL, "describe", source, "not valid JSON: ${e.message}")
    }
    return linkedMapOf(
      "path" to source,
      "mode" to mode,
      "inputChars" to text.length,
      "description" to description
    )
  }

  fun caveman(root: File, config: FsApiConfig, options: Map<String, Any?>): Map<String, Any?> {
    requireCapability(config.cavemanEnabled, "caveman")
    val (text, source) = inputText(root, config, options, "caveman")
    val cavemanConfig = cavemanConfig(options)
    val mode = (str(options, "mode") ?: "run").lowercase()
    val payload = linkedMapOf<String, Any?>(
      "path" to source,
      "mode" to mode,
      "inputChars" to text.length
    )
    try {
      if (mode == "compress") {
        payload["output"] = Caveman.compress(text, cavemanConfig)
      } else if (mode == "run") {
        val result = Caveman.run(text, cavemanConfig)
        payload["output"] = result.output
        payload["intent"] = result.intent
        payload["template"] = result.template
        if (flag(options, "trace", false)) {
          payload["trace"] = result.trace.map { stage ->
            linkedMapOf(
              "stage" to stage.stage.toString(),
              "decisions" to stage.decisions.map {
                linkedMapOf("token" to it.token, "action" to it.action.toString())
              }
            )
          }
          payload["explain"] = result.explain()
        }
      } else {
        throw FsException(FsErrorCode.EINVAL, "caveman", source, "unknown mode '$mode' (run|compress)")
      }
    } catch (e: FsException) {
      throw e
    } catch (e: Exception) {
      log.warn("caveman pipeline failed for {}", source, e)
      throw FsException(FsErrorCode.EIO, "caveman", source, e.message ?: e.javaClass.simpleName)
    }
    val output = payload["output"]?.toString() ?: ""
    payload["outputChars"] = output.length
    payload["ratio"] = if (text.isEmpty()) 0.0 else output.length.toDouble() / text.length
    return payload
  }

  // ------------------------------------------------------------- plumbing

  private fun requireCapability(enabled: Boolean, syscall: String) {
    if (!enabled) {
      throw FsException(FsErrorCode.ENOSYS, syscall, null, "$syscall capability disabled for this mount")
    }
  }

  private fun visible(root: File, path: String, syscall: String): FsTarget {
    val resolved = FsPath.resolve(root, path, syscall)
    if (FileAccessControl.isHidden(root, resolved.file)) {
      throw FsException(FsErrorCode.ENOENT, syscall, resolved.virtual)
    }
    return resolved
  }

  /** `text` wins; otherwise `path` is read (bounded by maxToolInputBytes / maxFileSize). */
  private fun inputText(
    root: File,
    config: FsApiConfig,
    options: Map<String, Any?>,
    syscall: String,
  ): Pair<String, String?> {
    (options["text"] as? String)?.takeIf { it.isNotEmpty() }?.let { return it to null }
    val path = str(options, "path")
      ?: throw FsException(FsErrorCode.EINVAL, syscall, null, "provide 'text' or 'path'")
    val target = visible(root, path, syscall)
    if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, syscall, target.virtual)
    if (target.file.isDirectory) throw FsException(FsErrorCode.EISDIR, syscall, target.virtual)
    val limit = minOf(config.maxToolInputBytes, config.maxFileSize)
    if (target.file.length() > limit) {
      throw FsException(FsErrorCode.EFBIG, syscall, target.virtual, "exceeds maxToolInputBytes ($limit)")
    }
    val text = try {
      target.file.readText(Charsets.UTF_8)
    } catch (e: Exception) {
      throw FsException(FsErrorCode.EIO, syscall, target.virtual, e.message)
    }
    return text to target.virtual
  }

  private fun indexerConfig(config: FsApiConfig, options: Map<String, Any?>) = SymbolIndexer.Config(
    writePackageManifests = flag(options, "packageManifests", true),
    incremental = flag(options, "incremental", true),
    parallel = flag(options, "parallel", true),
    includeValidationErrors = flag(options, "errors", true),
    includeReferences = flag(options, "references", true),
    includeReferenceDetails = flag(options, "referenceDetails", false),
    resolveReferences = flag(options, "resolve", true),
    includeDetailsInManifest = flag(options, "manifestDetails", false),
    maxFileSizeBytes = config.maxFileSize
  )

  private fun cavemanConfig(options: Map<String, Any?>): CavemanConfig {
    var config = when (val preset = str(options, "preset")?.lowercase()) {
      null, "default" -> CavemanConfig()
      "keywords" -> CavemanConfig.keywordsOnly()
      "aggressive" -> CavemanConfig.aggressive(topN = int(options, "topN") ?: 8)
      else -> throw FsException(
        FsErrorCode.EINVAL, "caveman", null,
        "unknown preset '$preset' (default|keywords|aggressive)"
      )
    }
    config = config.copy(
      stemmingEnabled = flag(options, "stem", config.stemmingEnabled),
      stopwordRemovalEnabled = flag(options, "stopwords", config.stopwordRemovalEnabled)
    )
    str(options, "stemmer")?.let { name ->
      config = config.copy(
        stemmer = when (name.lowercase()) {
          "porter" -> PorterStemmer()
          "light" -> LightEnglishStemmer()
          else -> throw FsException(FsErrorCode.EINVAL, "caveman", null, "unknown stemmer '$name'")
        }
      )
    }
    config = if (flag(options, "salience", config.salienceEnabled)) {
      val extractor = when (val name = str(options, "extractor")?.lowercase()) {
        null, "freq", "frequency" -> FrequencySalienceExtractor()
        "textrank" -> TextRankSalienceExtractor()
        else -> throw FsException(FsErrorCode.EINVAL, "caveman", null, "unknown extractor '$name'")
      }
      config.withSalience(
        topN = int(options, "topN") ?: config.salienceTopN,
        threshold = dbl(options, "threshold") ?: config.salienceThreshold,
        extractor = extractor
      )
    } else {
      config.copy(salienceEnabled = false)
    }
    str(options, "pos")?.let { raw ->
      val categories = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { name ->
        try {
          PosCategory.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
          throw FsException(
            FsErrorCode.EINVAL, "caveman", null,
            "unknown POS category '$name' (available: ${PosCategory.values().joinToString(", ")})"
          )
        }
      }
      if (categories.isNotEmpty()) config = config.withPosFilter(*categories.toTypedArray())
    }
    str(options, "grammar")?.let { name ->
      config = config.copy(
        grammar = when (name.lowercase()) {
          "default", "caveman" -> CavemanConfig().grammar
          "terse" -> GrammarTemplateEngine.terseImperative()
          else -> throw FsException(FsErrorCode.EINVAL, "caveman", null, "unknown grammar '$name'")
        }
      )
    }
    str(options, "domain")?.let { raw ->
      config = if (raw.equals("distributed", true)) {
        config.withDomains(DomainTermRegistry.DISTRIBUTED_SYSTEMS)
      } else {
        val terms = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) config else config.withDomains(DomainTermRegistry.of(*terms.toTypedArray()))
      }
    }
    return config
  }

  /**
   * Never expose a record for a hidden path, and never expose the host-absolute
   * crawl root; counts are recomputed so the report stays internally consistent.
   */
  private fun sanitize(root: File, manifest: SymbolIndexer.Manifest): SymbolIndexer.Manifest {
    val indexRoot = File(manifest.root)
    val virtualRoot = try {
      FsPath.virtualPath(root, indexRoot)
    } catch (e: Exception) {
      "/"
    }
    val visible = manifest.files.filter { !FileAccessControl.isHidden(root, File(indexRoot, it.path)) }
    return manifest.copy(
      root = virtualRoot,
      fileCount = visible.size,
      symbolCount = visible.sumOf { it.symbolCount },
      referenceCount = visible.sumOf { it.referenceCount },
      resolvedNameCount = visible.sumOf { it.resolutions.size },
      unresolvedNameCount = visible.sumOf { it.unresolvedNames.size },
      files = visible
    )
  }

  private fun stale(file: File, record: SymbolIndexer.FileRecord): Boolean = try {
    record.size != file.length() ||
        record.lastModified != Files.getLastModifiedTime(file.toPath()).toInstant().toString()
  } catch (e: Exception) {
    true
  }

  private fun sameFile(a: File, b: File): Boolean = try {
    a.canonicalFile == b.canonicalFile
  } catch (e: Exception) {
    a.absoluteFile == b.absoluteFile
  }

  private fun <T> readIndexJson(path: Path, type: Class<T>): T? = try {
    if (Files.isRegularFile(path)) SymbolIndexer.mapper.readValue(Files.readAllBytes(path), type) else null
  } catch (e: Exception) {
    log.debug("unable to read index record {}: {}", path, e.message)
    null
  }

  @Suppress("UNCHECKED_CAST")
  private fun toMap(value: Any): Map<String, Any?> =
    SymbolIndexer.mapper.convertValue(value, Map::class.java) as Map<String, Any?>

  /** Query parameters, with a JSON body (when present) taking precedence. */
  private fun options(ctx: FsActionContext): Map<String, Any?> {
    val merged = LinkedHashMap<String, Any?>()
    try {
      ctx.req.parameterMap.forEach { (key, values) -> merged[key] = values.firstOrNull() ?: "" }
    } catch (e: Exception) {
      log.debug("unable to read query parameters", e)
    }
    if (ctx.method == "POST" || ctx.method == "PUT") {
      val raw = try {
        ctx.req.reader.readText()
      } catch (e: Exception) {
        log.debug("unable to read request body", e)
        ""
      }
      if (raw.isNotBlank()) {
        try {
          merged.putAll(FsJson.parseObject(raw))
        } catch (e: Exception) {
          throw FsException(FsErrorCode.EINVAL, ctx.op.ifBlank { "fsapi" }, null, "malformed JSON body: ${e.message}")
        }
      }
    }
    return merged
  }

  private fun str(options: Map<String, Any?>, name: String): String? =
    options[name]?.toString()?.takeIf { it.isNotBlank() }

  private fun flag(options: Map<String, Any?>, name: String, default: Boolean): Boolean {
    val value = options[name] ?: return default
    if (value is Boolean) return value
    val text = value.toString()
    return text.isEmpty() || text.equals("true", true) || text == "1" || text.equals("yes", true)
  }

  private fun int(options: Map<String, Any?>, name: String): Int? =
    (options[name] as? Number)?.toInt() ?: options[name]?.toString()?.trim()?.toIntOrNull()

  private fun dbl(options: Map<String, Any?>, name: String): Double? =
    (options[name] as? Number)?.toDouble() ?: options[name]?.toString()?.trim()?.toDoubleOrNull()

  private fun writeJson(resp: HttpServletResponse, status: Int, payload: Any?) {
    if (resp.isCommitted) return
    resp.status = status
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(FsJson.stringify(payload))
  }

  private fun writeText(resp: HttpServletResponse, body: String) {
    if (resp.isCommitted) return
    resp.status = HttpServletResponse.SC_OK
    resp.contentType = "text/plain"
    resp.characterEncoding = "UTF-8"
    resp.setHeader("X-Content-Type-Options", "nosniff")
    resp.writer.write(body)
  }
}