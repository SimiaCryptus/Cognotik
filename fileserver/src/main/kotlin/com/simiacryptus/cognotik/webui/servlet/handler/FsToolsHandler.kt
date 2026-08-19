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
import com.simiacryptus.cognotik.webui.servlet.FileServlet.Companion.getUser
import com.simiacryptus.cognotik.webui.servlet.FileServlet.Companion.isWriteAllowed
import com.simiacryptus.cognotik.webui.servlet.action.FsActionContext
import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import com.simiacryptus.cognotik.webui.servlet.util.FsPath
import com.simiacryptus.cognotik.webui.servlet.util.FsTarget
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Static-analysis / text utilities exposed through FS API v1:
 *
 *  * `GET  /.fsapi/v1/symbols`   read the [SymbolIndexer] sidecar (`.data/<name>.json`)
 *                                or folder rollup (`.data/package.json`) for a path
 *  * `POST /.fsapi/v1/symbols`   (re)build the index for a file or subtree — mutating,
 *                                because it writes `.data` sidecars
 *  * `POST /.fsapi/v1/describe`  [JsonSchemaDescriber] over a JSON file **or a whole
 *                                subtree**, writing `<name>.describe.md` beside each input
 *  * `POST /.fsapi/v1/caveman`   [Caveman] compression of a text file **or a whole
 *                                subtree**, writing `<name>.caveman.txt` beside each input
 *
 * `describe` and `caveman` are *producers*, not reporters: the interesting artifact is the
 * sidecar file next to the source, and the HTTP response is only a manifest
 * (`inputs/written/skipped/failed` plus a per-file `path -> output` record). This keeps the
 * result addressable by every other FS API verb (read, diff, download, index) instead of
 * being trapped inside a one-shot JSON envelope.
 *
 * Useful options (query string or JSON body; the body wins):
 *
 *  * `path`        file or folder to process (required unless inline `text` is supplied)
 *  * `text`        inline input; returns the rendering in the response unless `output` is set
 *  * `output`      explicit destination for inline input
 *  * `suffix`      override the sidecar suffix (default `.describe.md` / `.caveman.txt`)
 *  * `recursive`   descend into sub-folders (default true)
 *  * `ext`         comma-separated extension allow-list, e.g. `ext=json,jsonl`
 *  * `pattern`     regex matched against the file name
 *  * `maxFiles`    crawl ceiling (default [DEFAULT_MAX_FILES])
 *  * `overwrite`   replace existing sidecars (default true)
 *  * `dryRun`      plan only; nothing is written and no write permission is required
 *  * `preview`     also inline the generated text in the manifest
 *  * `format=text` render a human-readable report (or the content itself, for a single result)
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

  const val DESCRIBE_SUFFIX = ".describe.md"
  const val CAVEMAN_SUFFIX = ".caveman.txt"
  private const val DEFAULT_MAX_FILES = 500

  /** Never re-process something we generated — keeps folder runs idempotent. */
  private val GENERATED_SUFFIXES = listOf(DESCRIBE_SUFFIX, CAVEMAN_SUFFIX)

  /** What a tool produced for one input: the file body plus manifest-only metadata. */
  private data class ToolOutput(val content: String, val extra: Map<String, Any?> = emptyMap())

  // --------------------------------------------------------- HTTP adapters

  fun httpSymbols(ctx: FsActionContext) =
    writeJson(ctx.resp, HttpServletResponse.SC_OK, symbols(ctx.root, ctx.config, options(ctx)))

  fun httpIndexSymbols(ctx: FsActionContext) =
    writeJson(
      ctx.resp, HttpServletResponse.SC_OK,
      indexSymbols(ctx.req, ctx.resp, ctx.root, ctx.config, options(ctx), ctx.writeAllowed)
    )

  fun httpDescribe(ctx: FsActionContext) {
    val opts = options(ctx)
    respond(ctx, opts, describe(ctx.req, ctx.resp, ctx.root, ctx.config, opts, ctx.writeAllowed))
  }

  fun httpCaveman(ctx: FsActionContext) {
    val opts = options(ctx)
    respond(ctx, opts, caveman(ctx.req, ctx.resp, ctx.root, ctx.config, opts, ctx.writeAllowed))
  }

  /** `format=text` yields the single generated body when there is exactly one, else a report. */
  private fun respond(ctx: FsActionContext, opts: Map<String, Any?>, payload: Map<String, Any?>) {
    if (str(opts, "format")?.lowercase() == "text") {
      writeText(ctx.resp, soleContent(payload) ?: renderReport(payload))
    } else {
      writeJson(ctx.resp, HttpServletResponse.SC_OK, payload)
    }
  }

  // ------------------------------------------------------------- core ops
  // HTTP-agnostic apart from the write-permission check, so /batch can reuse
  // them verbatim (the batch op object *is* the option map).

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
    request: HttpServletRequest, response: HttpServletResponse,
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
    if (FileAccessControl.isReadOnly(root, target.file) || !isWriteAllowed(getUser(request, response), request)) {
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

  /**
   * Describes one JSON file or every matching file in a subtree, writing
   * `<name>.describe.md` beside each input. Returns the run manifest.
   */
  fun describe(
    request: HttpServletRequest?, response: HttpServletResponse?,
    root: File,
    config: FsApiConfig,
    options: Map<String, Any?>,
    writeAllowed: Boolean,
  ): Map<String, Any?> {
    requireCapability(config.describeEnabled, "describe")
    val mode = (str(options, "mode") ?: "auto").lowercase()
    if (mode !in setOf("auto", "data", "schema")) {
      throw FsException(FsErrorCode.EINVAL, "describe", null, "unknown mode '$mode' (auto|data|schema)")
    }
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
    return runTool(
      request, response, root, config, options,
      syscall = "describe",
      defaultSuffix = DESCRIBE_SUFFIX,
      writeAllowed = writeAllowed,
      extras = linkedMapOf("mode" to mode)
    ) { text, source ->
      val description = try {
        when (mode) {
          "schema" -> describer.describeSchemaJson(text)
          "data" -> describer.describeDataJson(text, samples)
          else -> describer.describe(text, samples)
        }
      } catch (e: Exception) {
        log.debug("describe failed for {}", source, e)
        throw FsException(FsErrorCode.EINVAL, "describe", source, "not valid JSON: ${e.message}")
      }
      ToolOutput(description)
    }
  }

  /**
   * Compresses one text file or every matching file in a subtree, writing
   * `<name>.caveman.txt` beside each input. Returns the run manifest.
   */
  fun caveman(
    request: HttpServletRequest?, response: HttpServletResponse?,
    root: File,
    config: FsApiConfig,
    options: Map<String, Any?>,
    writeAllowed: Boolean,
  ): Map<String, Any?> {
    requireCapability(config.cavemanEnabled, "caveman")
    val mode = (str(options, "mode") ?: "run").lowercase()
    if (mode !in setOf("run", "compress")) {
      throw FsException(FsErrorCode.EINVAL, "caveman", null, "unknown mode '$mode' (run|compress)")
    }
    val cavemanConfig = cavemanConfig(options)
    val trace = flag(options, "trace", false)
    return runTool(
      request, response, root, config, options,
      syscall = "caveman",
      defaultSuffix = CAVEMAN_SUFFIX,
      writeAllowed = writeAllowed,
      extras = linkedMapOf("mode" to mode)
    ) { text, source ->
      try {
        if (mode == "compress") {
          ToolOutput(Caveman.compress(text, cavemanConfig))
        } else {
          val result = Caveman.run(text, cavemanConfig)
          val extra = linkedMapOf<String, Any?>(
            "intent" to result.intent,
            "template" to result.template
          )
          if (trace) {
            extra["trace"] = result.trace.map { stage ->
              linkedMapOf(
                "stage" to stage.stage.toString(),
                "decisions" to stage.decisions.map {
                  linkedMapOf("token" to it.token, "action" to it.action.toString())
                }
              )
            }
            extra["explain"] = result.explain()
          }
          ToolOutput(result.output, extra)
        }
      } catch (e: FsException) {
        throw e
      } catch (e: Exception) {
        log.warn("caveman pipeline failed for {}", source, e)
        throw FsException(FsErrorCode.EIO, "caveman", source, e.message ?: e.javaClass.simpleName)
      }
    }
  }

  // ------------------------------------------- read-only compatibility shims

  @Deprecated(
    "describe now writes result files; use the overload taking request/response/writeAllowed",
    ReplaceWith("describe(null, null, root, config, options, false)")
  )
  fun describe(root: File, config: FsApiConfig, options: Map<String, Any?>): Map<String, Any?> =
    describe(null, null, root, config, previewOnly(options), false)

  @Deprecated(
    "caveman now writes result files; use the overload taking request/response/writeAllowed",
    ReplaceWith("caveman(null, null, root, config, options, false)")
  )
  fun caveman(root: File, config: FsApiConfig, options: Map<String, Any?>): Map<String, Any?> =
    caveman(null, null, root, config, previewOnly(options), false)

  private fun previewOnly(options: Map<String, Any?>): Map<String, Any?> =
    LinkedHashMap(options).apply {
      put("dryRun", true)
      put("preview", true)
    }

  // --------------------------------------------------------- the producer

  /**
   * Shared driver for the file-producing tools: resolve inputs, run [transform] over each,
   * write the sidecar, and accumulate a manifest. A failure on a *single explicit file*
   * propagates (so the caller sees the real error code); failures inside a folder crawl are
   * recorded per entry so one bad file cannot abort the batch.
   */
  private fun runTool(
    request: HttpServletRequest?,
    response: HttpServletResponse?,
    root: File,
    config: FsApiConfig,
    options: Map<String, Any?>,
    syscall: String,
    defaultSuffix: String,
    writeAllowed: Boolean,
    extras: Map<String, Any?>,
    transform: (String, String?) -> ToolOutput,
  ): Map<String, Any?> {
    val suffix = str(options, "suffix") ?: defaultSuffix
    val dryRun = flag(options, "dryRun", false)
    val overwrite = flag(options, "overwrite", true)
    val preview = flag(options, "preview", false)
    val limit = minOf(config.maxToolInputBytes, config.maxFileSize)

    val results = ArrayList<Map<String, Any?>>()
    var written = 0
    var planned = 0
    var skipped = 0
    var failed = 0
    var scope: String? = null

    val inline = (options["text"] as? String)?.takeIf { it.isNotEmpty() }
    if (inline != null) {
      if (inline.length > limit) {
        throw FsException(FsErrorCode.EFBIG, syscall, null, "inline text exceeds maxToolInputBytes ($limit)")
      }
      val produced = transform(inline, null)
      val destination = str(options, "output")
      if (destination == null) {
        // Nothing to write beside — hand the rendering back directly.
        results += resultMap(null, null, inline.length, produced, produced.content)
      } else {
        val out = resolveOutput(root, destination, syscall)
        if (!dryRun) {
          requireWritable(request, response, config, syscall, writeAllowed)
          if (FileAccessControl.isReadOnly(root, out.file)) {
            throw FsException(FsErrorCode.EACCES, syscall, out.virtual, "target is read-only")
          }
          if (!overwrite && out.file.exists()) {
            throw FsException(FsErrorCode.EEXIST, syscall, out.virtual, "output exists (set overwrite=true)")
          }
          writeResult(out.file, produced.content, syscall, out.virtual)
          written++
        } else {
          planned++
        }
        results += resultMap(
          null, out.virtual, inline.length, produced,
          if (preview || dryRun) produced.content else null
        )
      }
    } else {
      val requested = str(options, "path")
        ?: throw FsException(FsErrorCode.EINVAL, syscall, null, "provide 'text' or 'path'")
      val target = visible(root, requested, syscall)
      if (!target.file.exists()) throw FsException(FsErrorCode.ENOENT, syscall, target.virtual)
      scope = target.virtual
      if (!dryRun) requireWritable(request, response, config, syscall, writeAllowed)

      val inputs = collectInputs(root, target, options, syscall, suffix)
      if (inputs.isEmpty()) {
        throw FsException(FsErrorCode.ENOENT, syscall, target.virtual, "no matching input files")
      }
      val single = inputs.size == 1 && !target.file.isDirectory
      val started = System.nanoTime()
      for (input in inputs) {
        val inVirtual = virtual(root, input)
        val outFile = File(input.parentFile, input.name + suffix)
        val outVirtual = virtual(root, outFile)
        try {
          if (input.length() > limit) {
            skipped++
            results += noteMap(inVirtual, outVirtual, "skipped", "exceeds maxToolInputBytes ($limit)")
            continue
          }
          if (!overwrite && outFile.exists()) {
            skipped++
            results += noteMap(inVirtual, outVirtual, "skipped", "output exists (set overwrite=true)")
            continue
          }
          if (!dryRun && FileAccessControl.isReadOnly(root, outFile)) {
            skipped++
            results += noteMap(inVirtual, outVirtual, "skipped", "target is read-only")
            continue
          }
          val text = try {
            input.readText(Charsets.UTF_8)
          } catch (e: Exception) {
            throw FsException(FsErrorCode.EIO, syscall, inVirtual, e.message)
          }
          val produced = transform(text, inVirtual)
          if (dryRun) {
            planned++
          } else {
            writeResult(outFile, produced.content, syscall, outVirtual)
            written++
          }
          results += resultMap(
            inVirtual, outVirtual, text.length, produced,
            if (preview || (dryRun && single)) produced.content else null
          )
        } catch (e: FsException) {
          if (single) throw e
          failed++
          results += noteMap(inVirtual, outVirtual, "error", e.message ?: e.code.name, e.code.name)
        } catch (e: Exception) {
          if (single) throw FsException(FsErrorCode.EIO, syscall, inVirtual, e.message ?: e.javaClass.simpleName)
          failed++
          results += noteMap(inVirtual, outVirtual, "error", e.message ?: e.javaClass.simpleName)
        }
      }
      log.info(
        "{}: {} input(s) -> {} written / {} skipped / {} failed under {} in {}ms",
        syscall, results.size, written, skipped, failed, scope, (System.nanoTime() - started) / 1_000_000
      )
    }

    val payload = linkedMapOf<String, Any?>(
      "op" to syscall,
      "path" to scope,
      "suffix" to suffix
    )
    payload.putAll(extras)
    payload["dryRun"] = dryRun
    payload["inputs"] = results.size
    payload["written"] = written
    if (dryRun) payload["planned"] = planned
    payload["skipped"] = skipped
    payload["failed"] = failed
    payload["results"] = results
    return payload
  }

  private fun resultMap(
    path: String?, output: String?, inputChars: Int, produced: ToolOutput, content: String?,
  ): Map<String, Any?> {
    val map = linkedMapOf<String, Any?>(
      "path" to path,
      "output" to output,
      "inputChars" to inputChars,
      "outputChars" to produced.content.length,
      "ratio" to if (inputChars == 0) 0.0 else produced.content.length.toDouble() / inputChars
    )
    map.putAll(produced.extra)
    if (content != null) map["content"] = content
    return map
  }

  private fun noteMap(
    path: String?, output: String?, key: String, message: String, code: String? = null,
  ): Map<String, Any?> = linkedMapOf<String, Any?>(
    "path" to path,
    "output" to output,
    key to message
  ).also { if (code != null) it["code"] = code }

  /** Single-result bodies are worth handing back verbatim for `format=text`. */
  private fun soleContent(payload: Map<String, Any?>): String? {
    val results = payload["results"] as? List<*> ?: return null
    val only = results.singleOrNull() as? Map<*, *> ?: return null
    return only["content"] as? String
  }

  private fun renderReport(payload: Map<String, Any?>): String {
    val sb = StringBuilder()
    (payload["results"] as? List<*>)?.forEach { entry ->
      val row = entry as? Map<*, *> ?: return@forEach
      val src = row["path"] ?: "(inline)"
      val out = row["output"]
      sb.append(if (out != null) "$src -> $out" else "$src")
      (row["skipped"] ?: row["error"])?.let { sb.append("  [").append(it).append(']') }
      sb.append('\n')
    }
    sb.append("written=").append(payload["written"])
    payload["planned"]?.let { sb.append(" planned=").append(it) }
    sb.append(" skipped=").append(payload["skipped"])
    sb.append(" failed=").append(payload["failed"])
    sb.append('\n')
    return sb.toString()
  }

  /** File → itself; folder → every visible, non-generated match, capped at `maxFiles`. */
  private fun collectInputs(
    root: File,
    target: FsTarget,
    options: Map<String, Any?>,
    syscall: String,
    suffix: String,
  ): List<File> {
    if (!target.file.isDirectory) return listOf(target.file)
    val recursive = flag(options, "recursive", true)
    val maxFiles = (int(options, "maxFiles") ?: DEFAULT_MAX_FILES).coerceIn(1, 100_000)
    val extensions = str(options, "ext")
      ?.split(',')?.map { it.trim().removePrefix(".").lowercase() }?.filter { it.isNotEmpty() }?.toSet()
    val pattern = str(options, "pattern")?.let {
      try {
        Regex(it)
      } catch (e: Exception) {
        throw FsException(FsErrorCode.EINVAL, syscall, target.virtual, "bad pattern: ${e.message}")
      }
    }
    val suffixes = (GENERATED_SUFFIXES + suffix).distinct()
    val found = ArrayList<File>()

    fun visit(dir: File) {
      if (found.size >= maxFiles) return
      val children = dir.listFiles() ?: return
      for (child in children.sortedBy { it.name }) {
        if (found.size >= maxFiles) return
        if (child.name.startsWith(".")) continue
        if (FileAccessControl.isHidden(root, child)) continue
        if (child.isDirectory) {
          if (recursive) visit(child)
          continue
        }
        if (suffixes.any { child.name.endsWith(it) }) continue
        if (extensions != null && child.extension.lowercase() !in extensions) continue
        if (pattern != null && !pattern.containsMatchIn(child.name)) continue
        found += child
      }
    }
    visit(target.file)
    return found
  }

  // ------------------------------------------------------------- plumbing

  private fun requireCapability(enabled: Boolean, syscall: String) {
    if (!enabled) {
      throw FsException(FsErrorCode.ENOSYS, syscall, null, "$syscall capability disabled for this mount")
    }
  }

  /** Same gate as [indexSymbols]; `dryRun` callers skip it entirely. */
  private fun requireWritable(
    request: HttpServletRequest?,
    response: HttpServletResponse?,
    config: FsApiConfig,
    syscall: String,
    writeAllowed: Boolean,
  ) {
    if (!writeAllowed) {
      throw FsException(FsErrorCode.EACCES, syscall, null, "authentication required to write results")
    }
    if (config.readOnly) throw FsException(FsErrorCode.EROFS, syscall, null)
    if (request == null || response == null) {
      throw FsException(FsErrorCode.EACCES, syscall, null, "no request context; retry with dryRun=true")
    }
    if (!isWriteAllowed(getUser(request, response), request)) {
      throw FsException(FsErrorCode.EACCES, syscall, null, "write access denied")
    }
  }

  private fun visible(root: File, path: String, syscall: String): FsTarget {
    val resolved = FsPath.resolve(root, path, syscall)
    if (FileAccessControl.isHidden(root, resolved.file)) {
      throw FsException(FsErrorCode.ENOENT, syscall, resolved.virtual)
    }
    return resolved
  }

  private fun resolveOutput(root: File, path: String, syscall: String): FsTarget {
    val resolved = FsPath.resolve(root, path, syscall)
    if (resolved.file.isDirectory) throw FsException(FsErrorCode.EISDIR, syscall, resolved.virtual)
    return resolved
  }

  private fun virtual(root: File, file: File): String = try {
    FsPath.virtualPath(root, file)
  } catch (e: Exception) {
    log.debug("unable to virtualize {}: {}", file, e.message)
    file.name
  }

  /** Write via a temp file so a crash never leaves a half-written sidecar behind. */
  private fun writeResult(file: File, content: String, syscall: String, virtual: String) {
    try {
      file.parentFile?.mkdirs()
      val tmp = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
      tmp.writeText(content, Charsets.UTF_8)
      try {
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
      } catch (e: Exception) {
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
      }
    } catch (e: Exception) {
      throw FsException(FsErrorCode.EIO, syscall, virtual, e.message ?: e.javaClass.simpleName)
    }
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
      resolvedNameCount = visible.sumOf { it.referencesTo.size },
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