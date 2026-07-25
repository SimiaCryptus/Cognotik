package com.simiacryptus.cognotik.docops

import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import com.simiacryptus.cognotik.util.HtmlSimplifier
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.jsonCast
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.allOf
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.get
import kotlin.collections.iterator

/**
 * Platform independent implementation of the doc-ops planning pipeline.
 *
 * All knowledge of task execution, agent dispatch and user/session identification is delegated to:
 *  - [K] the task-kind descriptor ([DocTaskKind]) plus [taskKinds] for name resolution,
 *  - [S] the opaque session handle produced by the host,
 *  - [P] the opaque patch-processor type used by the host,
 *  - [newScheduler] / [newExecutionContext] for concurrency and execution.
 */
abstract class DocProcessorBase<K : DocTaskKind, S : Any, P : Any>(
  val root: File,
  val docsFolder: File,
  val updateMode: UpdateMode = UpdateModes.PatchToUpdate,
  val additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
  val urlCacheDir: File = File(root, ".doc-processor-cache/url-cache"),
  val templateVarOverrides: Map<String, String> = emptyMap(),
) : DocStatus(root) {

  /*
   * ------------------------------------------------------------------
   * Host bindings
   * ------------------------------------------------------------------
   */

  /** Resolution of `task_type:` frontmatter values into concrete task kinds. */
  protected abstract val taskKinds: DocTaskKindResolver<K>

  /** Creates the concurrency scheduler used to run independent queues. */
  protected abstract fun newScheduler(): DocTaskScheduler

  /** Creates a fresh execution scope; one is created (and closed) per work queue. */
  protected abstract fun newExecutionContext(): DocExecutionContext<K, S, P>

  protected open val taskTimeoutMinutes: Int = 30
  protected open val overallTimeoutMinutes: Long = 90

  /*
   * ------------------------------------------------------------------
   * Status bookkeeping
   * ------------------------------------------------------------------
   */

  protected fun targetKeyOf(mod: ModificationTask<K>): String = targetKeyOf(mod.data.main_file)

  protected fun targetKeyOf(file: File?): String = file?.let { filePath ->
    try {
      filePath.canonicalFile.relativeTo(root.canonicalFile).toString()
    } catch (_: IllegalArgumentException) {
      filePath.canonicalFile.absolutePath
    }
  } ?: "unknown"

  protected fun updateTaskStatus(
    targetKey: String,
    status: TaskStatus,
    sessionId: String? = null,
    error: String? = null
  ) {
    setTaskStatus(targetKey, status, sessionId, error)
  }

  protected fun markRunningTasksAs(status: TaskStatus, error: String? = null) {
    synchronized(statusLock) {
      val current1 = readStatusLocked()
      val now1 = nowTimestamp()
      val updatedTasks1 = current1.tasks.toMutableMap()
      updatedTasks1.forEach { (key, entry) ->
        if (entry.status == TaskStatus.RUNNING) {
          updatedTasks1[key] = entry.copy(
            status = status,
            completedAt = now1,
            error = error
          )
        }
      }
      writeStatusLocked(DocOpsStatus(lastUpdated = now1, tasks = updatedTasks1))
      log.info("Marked all RUNNING tasks as $status${error?.let { " with error: $it" } ?: ""}")
      return
    }
  }

  /**
   * Initialize the status file with all planned tasks set to PENDING.
   */
  fun initializeStatus(tasks: List<ModificationTask<K>>) {
    synchronized(statusLock) {
      val now = nowTimestamp()
      val taskEntries = tasks.associate { task ->
        val targetKey = targetKeyOf(task)
        targetKey to TaskStatusEntry(
          target = targetKey,
          status = TaskStatus.PENDING
        )
      }
      // Merge with existing status (preserve completed tasks from previous runs)
      val existing = readStatusLocked()
      val merged = existing.tasks.toMutableMap()
      taskEntries.forEach { (key, entry) ->
        merged[key] = entry
      }
      log.info("Initialized status for ${taskEntries.size} tasks, preserving ${existing.tasks.count { it.value.status == TaskStatus.COMPLETED }} completed tasks from previous status")
      writeStatusLocked(DocOpsStatus(lastUpdated = now, tasks = merged))
    }
  }

  /*
   * ------------------------------------------------------------------
   * URL / related-resource resolution
   * ------------------------------------------------------------------
   */

  fun isUrl(path: String): Boolean {
    return path.startsWith("http://") || path.startsWith("https://")
  }

  private fun fetchAndCacheUrl(url: String): File? {
    try {
      urlCacheDir.mkdirs()
      val hash = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)
      val safeName = url.substringAfterLast("/")
        .substringBefore("?")
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        .take(50)
        .ifEmpty { "index" }
      val cachedFile = File(urlCacheDir, "${hash}_${safeName}")
      val metaFile = File(urlCacheDir, "${hash}_${safeName}.meta")

      // Return cached version if it exists and is less than 1 hour old
      if (cachedFile.exists() && metaFile.exists()) {
        val cacheAge = System.currentTimeMillis() - cachedFile.lastModified()
        if (cacheAge < 3600_000) {
          log.debug("Using cached URL content for: $url")
          return cachedFile
        }
      }

      log.info("Fetching URL for related resource: $url")
      val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
      val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(60))
        .header("User-Agent", "Mozilla/5.0 (compatible; CognotikBot/1.0)")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7")
        .header("Accept-Language", "en-US,en;q=0.5")
        .header("Accept-Charset", "utf-8, iso-8859-1;q=0.5")
        .GET()
        .build()

      val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      if (response.statusCode() !in 200..299) {
        log.warn("Failed to fetch URL $url: HTTP ${response.statusCode()}")
        return null
      }

      val contentType = response.headers().firstValue("Content-Type").orElse("")!!
      val body = response.body() ?: ""

      val content = if (contentType.startsWith("text/html") || contentType.isEmpty()) {
        try {
          HtmlSimplifier.scrubHtml(
            str = body,
            baseUrl = url,
            includeCssData = false,
            simplifyStructure = true,
            keepObjectIds = false,
            preserveWhitespace = false,
            keepScriptElements = false,
            keepInteractiveElements = false,
            keepMediaElements = false,
            keepEventHandlers = false
          )
        } catch (e: Exception) {
          log.warn("HTML simplification failed for URL: $url, using raw content", e)
          body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        }
      } else {
        body
      }

      cachedFile.writeText(content)
      metaFile.writeText("url=$url\nfetched=${System.currentTimeMillis()}\ncontent-type=$contentType\n")
      log.info("Cached URL content for $url (${content.length} chars) at ${cachedFile.absolutePath}")
      return cachedFile
    } catch (e: Exception) {
      log.error("Failed to fetch and cache URL: $url", e)
      return null
    }
  }

  fun resolveRelatedResource(baseDirOrDocFile: File, relatedPath: String): File? {
    return if (isUrl(relatedPath)) {
      fetchAndCacheUrl(relatedPath)
    } else {
      val resolved = baseDirOrDocFile.resolve(relatedPath)
      if (resolved.exists()) resolved else {
        log.debug("Related file does not exist: ${resolved.absolutePath}")
        resolved // Return it anyway; downstream code may handle non-existent files
      }
    }
  }

  fun resolveRelatedResources(baseDir: File, relatedPath: String): List<File> {
    return if (isUrl(relatedPath)) {
      listOfNotNull(fetchAndCacheUrl(relatedPath)?.absoluteFile)
    } else if (isGlobPattern(relatedPath)) {
      val expanded = expandPatternOrLiteral(baseDir, relatedPath)
      if (expanded.isEmpty()) {
        log.debug("Related glob pattern matched no files: $relatedPath (base: ${baseDir.absolutePath})")
      } else {
        log.debug("Related glob pattern '$relatedPath' matched ${expanded.size} files")
      }
      expanded
    } else {
      val resolved = baseDir.resolve(relatedPath)
      if (!resolved.exists()) {
        log.debug("Related file does not exist: ${resolved.absolutePath}")
      }
      listOf(resolved)
    }
  }

  /*
   * ------------------------------------------------------------------
   * Model
   * ------------------------------------------------------------------
   */

  data class DocSpec(
    val docFile: File,
    val specifies: List<String>,
    val documents: List<String>,
    val transforms: List<TransformSpec>,
    val generates: List<GenerateSpec>,
    val related: List<String>,
    val content: String,
    val frontmatter: Map<String, Any>,
    val taskConfigJson: String? = null,
    val taskType: String? = null,
    val updateMode: String? = null,
    val targetFolder: String? = null,
  )

  data class TransformSpec(
    val sourcePattern: String,
    val destinationPattern: String
  )

  data class GenerateSpec(
    val output: String,
    val inputs: List<String>
  )

  data class TransformMatch(
    val sourceFile: File,
    val destinationFile: File,
    val spec: DocSpec
  )

  data class GenerateMatch(
    val outputFile: File,
    val inputFiles: List<File>,
    val spec: DocSpec
  )

  data class ModificationTaskConfig(
    val root: File,
    val main_file: File? = null,
    val related_files: List<File>? = null,
    val task_description: String = "",
    val taskConfigOverrides: Map<String, Any>? = null,
    val doc_files: List<File> = emptyList(),
  ) {
    val relative_files: List<String>?
      get() = main_file?.let { listOf(it) }?.map { main_file ->
        try {
          main_file.canonicalFile.relativeTo(root.canonicalFile).toString()
        } catch (_: IllegalArgumentException) {
          // File is outside root, return absolute path
          main_file.canonicalFile.absolutePath
        }
      }
    val relative_related_files: List<String>?
      get() = related_files?.map { filePath ->
        try {
          filePath.canonicalFile.relativeTo(root.canonicalFile).toString()
        } catch (_: IllegalArgumentException) {
          // File is outside root, return absolute path
          filePath.canonicalFile.absolutePath
        }
      }
  }

  data class ModificationTask<K : DocTaskKind>(
    val data: ModificationTaskConfig,
    val message: (File) -> String = { "" },
    val patchProcessor: Any? = null,
    val shouldDeleteTarget: Boolean = false,
    val taskType: K
  ) {
    /** JSON-compatible per-task-type settings. */
    val typeConfig: Map<String, Any>
      get() = data.taskConfigOverrides
        ?: taskType.defaultConfig()
        ?: mapOf("task_type" to taskType.name)

    fun rebase(prevRoot: File, newRoot: File) =
      if (newRoot == prevRoot) this else copy(data = data.copy(root = newRoot))

    fun message(): String {
      return message(data.root)
    }
  }

  data class DocumentMatch(
    val docSpec: DocSpec,
    val supportingFiles: List<File> = emptyList()
  )

  /** Convenience accessor that narrows the erased patch processor back to [P]. */
  @Suppress("UNCHECKED_CAST")
  protected fun ModificationTask<K>.patchProcessorAs(): P? = patchProcessor as? P

  /*
   * ------------------------------------------------------------------
   * Planning
   * ------------------------------------------------------------------
   */

  fun run() {
    val markdownFiles = docsFolder.listFilesRecursively()
      .filter { it.isFile && it.extension in setOf("md", "markdown") }
    log.info("Found ${markdownFiles.size} markdown files in ${docsFolder.absolutePath}")
    if (markdownFiles.isEmpty()) {
      log.warn("No markdown files found in ${docsFolder.absolutePath}")
      return
    }
    val docSpecs = markdownFiles.mapNotNull { parseMarkdownWithFrontmatter(it) }
    val fileMods = modificationTasks(docSpecs)
    runAll(fileMods)
  }

  fun getAll(
    vararg markdownFiles: File,
  ) = modificationTasks(markdownFiles.mapNotNull { parseMarkdownWithFrontmatter(it) })

  fun modificationTasks(docSpecs: List<DocSpec>): List<ModificationTask<K>> {
    log.info("Found ${docSpecs.size} markdown files with 'specifies' frontmatter")
    return modificationTasksRecursive(docSpecs, emptySet(), 0)
  }

  private fun normalizePath(path: String): String = path.lowercase()

  private fun modificationTasksRecursive(
    docSpecs: List<DocSpec>,
    knownTargets: Set<String>,
    depth: Int
  ): List<ModificationTask<K>> {
    val maxDepth = 10
    if (depth > maxDepth) {
      log.warn("Recursive planning reached max depth ($maxDepth), stopping expansion. This may indicate circular generation rules.")
      return emptyList()
    }

    val fileToSpecs = fileToSpecs(docSpecs)
    val documentMatches = documentMatches(docSpecs)
    val transformMatches = transformMatches(docSpecs)
    val generateMatches = generateMatches(docSpecs)
    val allTargetFiles =
      (fileToSpecs.keys + transformMatches.keys + documentMatches.keys + generateMatches.keys).distinct()
    log.info("Recursive planning depth $depth: ${allTargetFiles.size} target files discovered")

    // Identify newly discovered targets that weren't known before
    val newTargets = allTargetFiles.filter { it !in knownTargets }.toSet()
    val allKnownTargets = knownTargets + allTargetFiles.toSet()

    // Build tasks for current level targets
    val currentTasks = allTargetFiles.mapNotNull { targetFile ->
      buildModificationTask(targetFile, fileToSpecs, transformMatches, documentMatches, generateMatches)
    }

    // Check if any new targets would cause additional doc specs to match new files.
    if (newTargets.isNotEmpty() && depth < maxDepth) {
      val transitiveTargets =
        discoverTransitiveTargets(docSpecs, newTargets.map { normalizePath(it) }.toSet(), allKnownTargets)
      if (transitiveTargets.isNotEmpty()) {
        log.info("Depth $depth: discovered ${transitiveTargets.size} transitive target(s) from ${newTargets.size} new target(s)")
        val transitiveTasks = modificationTasksRecursive(docSpecs, allKnownTargets, depth + 1)
        val currentTargetPaths = currentTasks.flatMap { it.data.relative_files ?: emptyList() }.toSet()
        val newTransitiveTasks = transitiveTasks.filter { task ->
          task.data.relative_files?.any { it !in currentTargetPaths } ?: false
        }
        return currentTasks + newTransitiveTasks
      }
    }

    return currentTasks
  }

  private fun discoverTransitiveTargets(
    docSpecs: List<DocSpec>,
    newTargetPaths: Set<String>,
    allKnownTargets: Set<String>
  ): Set<String> {
    val transitiveTargets = mutableSetOf<String>()

    for (spec in docSpecs) {
      for (transform in spec.transforms) {
        val sourceRegex = try {
          Pattern.compile(transform.sourcePattern)
        } catch (_: Exception) {
          continue
        }
        for (targetPath in newTargetPaths) {
          val targetFile = File(targetPath)
          val relativePath = try {
            targetFile.relativeTo(spec.docFile.parentFile.absoluteFile).path.replace("\\", "/")
          } catch (_: IllegalArgumentException) {
            continue
          }
          val matcher = sourceRegex.matcher(relativePath)
          if (matcher.matches()) {
            val destPath = applyBackreferences(transform.destinationPattern, matcher)
            val destFile = try {
              spec.docFile.parentFile.resolve(destPath).canonicalFile
            } catch (_: Exception) {
              spec.docFile.parentFile.resolve(destPath)
            }
            val destAbsPath = destFile.absolutePath
            if (normalizePath(destAbsPath) !in allKnownTargets) {
              log.debug("Transitive target discovered: $destAbsPath (from transform ${transform.sourcePattern} -> ${transform.destinationPattern} matching hypothetical $relativePath)")
              transitiveTargets.add(normalizePath(destAbsPath))
            }
          }
        }
      }
    }

    return transitiveTargets
  }

  private fun buildModificationTask(
    targetFile: String,
    fileToSpecs: Map<String, List<DocSpec>>,
    transformMatches: Map<String, List<TransformMatch>>,
    documentMatches: Map<String, List<DocumentMatch>>,
    generateMatches: Map<String, List<GenerateMatch>>
  ): ModificationTask<K>? {
    val normalizedTarget = normalizePath(targetFile)
    val specs =
      (fileToSpecs.entries.filter { normalizePath(it.key) == normalizedTarget }
        .flatMap { it.value }).toMutableList()
    val transforms =
      transformMatches.entries.filter { normalizePath(it.key) == normalizedTarget }.flatMap { it.value }
    val documents =
      documentMatches.entries.filter { normalizePath(it.key) == normalizedTarget }.flatMap { it.value }
    val generates =
      generateMatches.entries.filter { normalizePath(it.key) == normalizedTarget }.flatMap { it.value }
    val targetFileObj = File(targetFile)
    val relativeTarget = try {
      targetFileObj.relativeTo(root.absoluteFile)
    } catch (_: IllegalArgumentException) {
      log.warn("Target file is outside root: $targetFile")
      return null
    }
    try {
      log.info(
        "Processing ${relativeTarget} based on ${specs.size} spec(s), ${transforms.size} transform(s), ${documents.size} document(s), and ${generates.size} generate(s): ${
          (specs.map { it.docFile.name } +
              transforms.map { "${it.spec.docFile.name}(${it.sourceFile.name})" } +
              documents.map { "${it.docSpec.docFile.name}(${it.supportingFiles.size} files)" } +
              generates.map { "${it.spec.docFile.name}(${it.inputFiles.size} inputs)" }
              ).joinToString(", ")
        }")
      val source = primarySource(transforms, specs, documents, generates)
      // For targetFolder-only specs, the doc file itself is the source
      val effectiveSource = source ?: specs.firstOrNull { it.targetFolder != null }?.docFile ?: return null
      val relatedFiles = allRelatedFiles(specs, targetFileObj, transforms, documents, generates)
      val effectiveUpdateMode = resolveUpdateMode(specs, transforms, documents, generates)
      val effectiveRoot = resolveEffectiveRoot(specs, transforms, documents, generates)
      val prepareResult = effectiveUpdateMode.prepare(
        source = effectiveSource,
        target = targetFileObj,
        relatedFiles = relatedFiles
      )
      if (prepareResult == null) {
        log.debug("Update mode returned null for {}, skipping", relativeTarget)
        return null
      }
      if (prepareResult.shouldDeleteTarget && targetFileObj.exists()) {
        log.info("Deleting target file before processing: ${targetFileObj.absolutePath}")
        targetFileObj.delete()
      }
      val taskType = resolveTaskType(specs, transforms, documents, generates)
      val targetFile1 = File(targetFile)
      return ModificationTask(
        data = ModificationTaskConfig(
          main_file = targetFileObj.absoluteFile,
          doc_files = specs.map { it.docFile.absoluteFile }.distinct(),
          related_files = relatedFiles.map { file ->
            file.canonicalFile.absoluteFile
          }.distinct(),
          task_description = buildCombinedTaskDescription(
            specs,
            transforms,
            documents,
            generates,
            targetFile1,
            taskType
          ),
          root = effectiveRoot,
          taskConfigOverrides = resolveTaskConfigJson(specs, transforms, documents, generates),
        ),
        message = { root: File ->
          buildString {
            when {
              taskType.isFileTask -> this.appendLine("Execute task.")

              else -> {
                relatedFiles.forEach { relatedFile ->
                  val resolvedFile = if (File(relatedFile.toString()).isAbsolute) {
                    File(relatedFile.toString())
                  } else {
                    root.resolve(relatedFile)
                  }
                  this.appendLine("# Context file: ${resolvedFile.relativeTo(root)}")
                  this.appendLine("```")
                  if (resolvedFile.exists()) {
                    var text = resolvedFile.readText().trim()
                    if (relatedFile.name.endsWith(".md") && text.startsWith("---\n")) {
                      try {
                        text = text.substring(text.indexOf("\n---\n"))
                      } catch (_: Exception) {
                        // If parsing fails, just use the full content
                      }
                    }
                    this.appendLine(text)
                  } else {
                    this.appendLine("<!-- File not found: $relatedFile -->")
                  }
                  this.appendLine("```")
                }
              }
            }
          }
        },
        patchProcessor = prepareResult.patchProcessor,
        taskType = taskType,
      )
    } catch (e: Exception) {
      log.error("Error processing ${relativeTarget}", e)
      return null
    }
  }

  private fun resolveTaskConfigJson(
    specs: List<DocSpec>,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>,
    generates: List<GenerateMatch>
  ): Map<String, Any>? {
    val configJsonPath = specs.firstNotNullOfOrNull { spec ->
      spec.taskConfigJson?.let {
        spec.docFile.parentFile.resolve(it)
      }
    } ?: transforms.firstNotNullOfOrNull { match ->
      match.spec.taskConfigJson?.let {
        match.spec.docFile.parentFile.resolve(it)
      }
    } ?: documents.firstNotNullOfOrNull { docMatch ->
      docMatch.docSpec.taskConfigJson?.let {
        docMatch.docSpec.docFile.parentFile.resolve(it)
      }
    } ?: generates.firstNotNullOfOrNull { genMatch ->
      genMatch.spec.taskConfigJson?.let {
        genMatch.spec.docFile.parentFile.resolve(it)
      }
    }
    if (configJsonPath != null && configJsonPath.exists()) {
      try {
        val fromJson = JsonUtil.fromJson<Map<String, Any>>(configJsonPath.readText(), Map::class.java)
        return fromJson
      } catch (e: Exception) {
        log.warn("Failed to parse task config JSON: ${configJsonPath.absolutePath}", e)
      }
    } else {
      if (configJsonPath != null) {
        log.warn("Task config JSON file not found: ${configJsonPath.absolutePath}")
      }
    }
    return null
  }

  fun resolveTaskType(
    specs: List<DocSpec>,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>,
    generates: List<GenerateMatch>
  ): K {
    val taskTypeName = specs.firstNotNullOfOrNull { it.taskType }
      ?: transforms.firstNotNullOfOrNull { it.spec.taskType }
      ?: documents.firstNotNullOfOrNull { it.docSpec.taskType }
      ?: generates.firstNotNullOfOrNull { it.spec.taskType }
    return if (taskTypeName != null) {
      taskKinds.byName(taskTypeName) ?: run {
        log.warn("Unknown task type '$taskTypeName', defaulting to ${taskKinds.default.name}")
        taskKinds.default
      }
    } else {
      taskKinds.default
    }
  }

  fun primarySource(
    transforms: List<TransformMatch>,
    specs: List<DocSpec>,
    documents: List<DocumentMatch>,
    generates: List<GenerateMatch>
  ): File? = when {
    transforms.isNotEmpty() -> transforms.first().sourceFile
    specs.isNotEmpty() -> specs.first().docFile
    documents.isNotEmpty() -> documents.first().supportingFiles.firstOrNull() ?: documents.first().docSpec.docFile
    generates.isNotEmpty() -> generates.first().inputFiles.firstOrNull() ?: generates.first().spec.docFile
    else -> null
  }

  fun allRelatedFiles(
    specs: List<DocSpec>,
    targetFile: File,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>,
    generates: List<GenerateMatch>
  ): List<File> {
    val distinct = (specs.flatMap { spec ->
      spec.related.flatMap { relatedPath ->
        resolveRelatedResources(spec.docFile.parentFile, relatedPath)
      } +
          additionalContext(spec, targetFile).map { File(it) }
    } + transforms.flatMap { match ->
      listOf(
        match.sourceFile
      ) + match.spec.related.flatMap { relatedPath ->
        resolveRelatedResources(
          match.spec.docFile.parentFile,
          relatedPath
        )
      } + additionalContext(match.spec, targetFile).map { File(it) }
    } + documents.flatMap { docMatch ->
      docMatch.supportingFiles + docMatch.docSpec.related.flatMap { relatedPath ->
        resolveRelatedResources(
          docMatch.docSpec.docFile.parentFile,
          relatedPath
        )
      } + additionalContext(docMatch.docSpec, targetFile).map { File(it) }
    } + generates.flatMap { genMatch ->
      genMatch.inputFiles +
          genMatch.spec.related.flatMap { relatedPath ->
            resolveRelatedResources(genMatch.spec.docFile.parentFile, relatedPath)
          } +
          additionalContext(genMatch.spec, targetFile).map { File(it) }
    }).distinct()
    return distinct
  }

  fun transformMatches(docSpecs: List<DocSpec>): Map<String, List<TransformMatch>> {
    val transformMatches = docSpecs
      .filter { it.transforms.isNotEmpty() }
      .flatMap { spec ->
        spec.transforms.flatMap { transform ->
          expandTransformPattern(root, transform, spec)
        }
      }.groupBy { normalizePath(it.destinationFile.absolutePath) }
      // Re-key with original path
      .let { normalizedMap ->
        val originalPaths = docSpecs
          .filter { it.transforms.isNotEmpty() }
          .flatMap { spec ->
            spec.transforms.flatMap { transform ->
              expandTransformPattern(
                root,
                transform,
                spec
              )
            }
          }
          .associate { normalizePath(it.destinationFile.absolutePath) to it.destinationFile.absolutePath }
        normalizedMap.mapKeys { (normalizedKey, _) -> originalPaths[normalizedKey] ?: normalizedKey }
      }
    log.info("Found ${transformMatches.size} transform matches")
    return transformMatches
  }

  fun generateMatches(docSpecs: List<DocSpec>): Map<String, List<GenerateMatch>> {
    val generateMatches = docSpecs
      .filter { it.generates.isNotEmpty() }
      .flatMap { spec ->
        spec.generates.map { genSpec ->
          val baseDir = spec.docFile.parentFile
          val outputFile = baseDir.resolve(genSpec.output).canonicalFile
          val inputFiles = genSpec.inputs.flatMap { pattern ->
            if (pattern.contains("**")) {
              expandRecursiveGlob(baseDir, pattern)
            } else {
              expandSimpleGlob(baseDir, pattern)
            }
          }.distinct()
          log.info("Doc ${spec.docFile.name} generates '${genSpec.output}' from ${inputFiles.size} input files")
          GenerateMatch(
            outputFile = outputFile,
            inputFiles = inputFiles,
            spec = spec
          )
        }
      }.groupBy { normalizePath(it.outputFile.absolutePath) }
      // Re-key with original path
      .let { normalizedMap ->
        val originalPaths = docSpecs
          .filter { it.generates.isNotEmpty() }
          .flatMap { spec -> spec.generates.map { genSpec -> spec.docFile.parentFile.resolve(genSpec.output).canonicalFile.absolutePath } }
          .associateBy { normalizePath(it) }
        normalizedMap.mapKeys { (normalizedKey, _) -> originalPaths[normalizedKey] ?: normalizedKey }
      }
    log.info("Found ${generateMatches.size} generate matches")
    return generateMatches
  }

  fun documentMatches(docSpecs: List<DocSpec>): Map<String, List<DocumentMatch>> {
    val documentMatches = docSpecs
      .filter { it.documents.isNotEmpty() }
      .map { spec ->
        val baseDir = spec.docFile.parentFile
        val supportingFiles = spec.documents.flatMap { pattern ->
          val matchedFiles =
            if (pattern.contains("**")) {
              expandRecursiveGlob(baseDir, pattern)
            } else {
              expandSimpleGlob(baseDir, pattern)
            }
          log.info("Doc ${spec.docFile.name} documents pattern '$pattern' -> ${matchedFiles.size} supporting files")
          matchedFiles
        }.distinct()
        DocumentMatch(spec, supportingFiles)
      }.groupBy { normalizePath(it.docSpec.docFile.absolutePath) }
      // Re-key with original path
      .let { normalizedMap ->
        val originalPaths = docSpecs
          .filter { it.documents.isNotEmpty() }
          .associate { normalizePath(it.docFile.absolutePath) to it.docFile.absolutePath }
        normalizedMap.mapKeys { (normalizedKey, _) -> originalPaths[normalizedKey] ?: normalizedKey }
      }
    log.info("Found ${documentMatches.size} document specs")
    return documentMatches
  }

  fun fileToSpecs(docSpecs: List<DocSpec>): Map<String, List<DocSpec>> {
    // First, collect specs from 'specifies' patterns
    val specsFromSpecifies = docSpecs
      .filter { it.specifies.isNotEmpty() }
      .flatMap { spec ->
        val baseDir = spec.docFile.parentFile
        spec.specifies.flatMap { pattern ->
          val targetFiles = expandPatternOrLiteral(baseDir, pattern)
          log.info("Doc ${spec.docFile.name} specifies pattern '$pattern' -> ${targetFiles.size} files")
          targetFiles.map { targetFile -> targetFile.canonicalFile to spec }
        }
      }

    // Also collect specs from 'transforms' patterns
    val specsFromTransforms = docSpecs
      .filter { it.transforms.isNotEmpty() }
      .flatMap { spec ->
        spec.transforms.flatMap { transform ->
          expandTransformPattern(root, transform, spec).map { match ->
            match.destinationFile to spec
          }
        }
      }

    // Collect specs that have a targetFolder but no other target patterns
    val specsFromTargetFolder = docSpecs
      .filter { it.targetFolder != null && it.specifies.isEmpty() && it.transforms.isEmpty() && it.generates.isEmpty() && it.documents.isEmpty() }
      .map { spec ->
        val resolvedFolder = try {
          spec.docFile.parentFile.resolve(spec.targetFolder!!).canonicalFile
        } catch (_: Exception) {
          spec.docFile.parentFile.resolve(spec.targetFolder!!)
        }
        log.info("Doc ${spec.docFile.name} targets folder '${spec.targetFolder}' -> ${resolvedFolder.absolutePath}")
        resolvedFolder to spec
      }

    // Combine all sources and group by target file
    val fileToSpecs = (specsFromSpecifies + specsFromTransforms + specsFromTargetFolder)
      .groupBy({ it.first.absolutePath }, { it.second })
      .mapValues { it.value.distinct() }

    log.info("Grouped into ${fileToSpecs.size} unique target files from 'specifies'")
    return fileToSpecs
  }

  fun separateQueues(
    fileMods: List<ModificationTask<K>>,
  ): List<List<ModificationTask<K>>> {
    val queues = mutableListOf<List<ModificationTask<K>>>()
    val processedFiles = mutableSetOf<String>()

    for (mod in fileMods) {
      val targetFile = mod.data.relative_files?.firstOrNull()?.let { File(root, it).canonicalPath }
      if (targetFile != null) {
        val relatedFiles = mod.data.relative_related_files?.mapNotNull { relatedPath ->
          try {
            File(root, relatedPath).canonicalPath
          } catch (e: Exception) {
            log.warn("Failed to resolve related file path: $relatedPath", e)
            null
          }
        } ?: emptyList()

        if (targetFile in processedFiles || relatedFiles.any { it in processedFiles }) {
          queues.add(mutableListOf(mod))
          processedFiles.add(targetFile)
          processedFiles.addAll(relatedFiles)
        } else {
          if (queues.isEmpty()) {
            queues.add(mutableListOf(mod))
          } else {
            @Suppress("UNCHECKED_CAST")
            (queues.last() as MutableList<ModificationTask<K>>).add(mod)
          }
          processedFiles.add(targetFile)
          processedFiles.addAll(relatedFiles)
        }
      } else {
        if (queues.isEmpty()) {
          queues.add(mutableListOf(mod))
        } else {
          @Suppress("UNCHECKED_CAST")
          (queues.last() as MutableList<ModificationTask<K>>).add(mod)
        }
      }
    }

    log.info("Separated into ${queues.size} queues based on file relationships")
    return queues
  }

  /*
   * ------------------------------------------------------------------
   * Execution
   * ------------------------------------------------------------------
   */

  fun runAll(
    fileMods: List<ModificationTask<K>>,
    scheduler: DocTaskScheduler = newScheduler(),
    cancelFlag: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (S) -> Unit = { _ -> }
  ): List<S> {
    initializeStatus(fileMods)
    val sessions: MutableList<S> = Collections.synchronizedList(mutableListOf())
    val futures: Array<CompletableFuture<*>> =
      separateQueues(fileMods).map { sortByDependencies(it) }.filter { it.isNotEmpty() }.map { mods ->
        scheduler.submit {
          newExecutionContext().use { ctx ->
            if (cancelFlag.get()) {
              log.info("Cancellation requested, skipping execution of remaining tasks")
              mods.forEach { mod -> updateTaskStatus(targetKeyOf(mod), TaskStatus.CANCELLED) }
              return@submit
            }
            val remainingMods = mods.toMutableList()
            try {
              for (mod in mods) {
                remainingMods.remove(mod)
                run(mod, ctx, cancelFlag, onNewSession, sessions)
              }
            } catch (e: CancellationException) {
              // Mark any remaining unprocessed tasks as cancelled
              remainingMods.forEach { mod ->
                updateTaskStatus(targetKeyOf(mod), TaskStatus.CANCELLED)
              }
            } catch (e: Throwable) {
              // Mark any remaining unprocessed tasks as failed
              remainingMods.forEach { mod ->
                updateTaskStatus(
                  targetKeyOf(mod),
                  TaskStatus.FAILED,
                  error = "Queue aborted: ${e.message ?: e.javaClass.simpleName}"
                )
              }
            }
          }
        }
      }.toTypedArray()
    try {
      allOf(*futures).get(overallTimeoutMinutes, TimeUnit.MINUTES)
    } catch (e: TimeoutException) {
      log.error("DocProcessor timed out after $overallTimeoutMinutes minutes, marking remaining RUNNING tasks as FAILED")
      markRunningTasksAs(TaskStatus.FAILED, "Timed out after $overallTimeoutMinutes minutes")
      throw e
    } catch (e: ExecutionException) {
      log.error("DocProcessor execution failed", e)
      markRunningTasksAs(TaskStatus.FAILED, "Execution failed: ${e.cause?.message ?: e.message}")
      throw e
    } catch (e: InterruptedException) {
      log.error("DocProcessor interrupted", e)
      markRunningTasksAs(TaskStatus.CANCELLED, "Interrupted")
      throw e
    }
    return sessions.toList()
  }

  fun run(
    mod: ModificationTask<K>,
    ctx: DocExecutionContext<K, S, P>,
    cancelFlag: AtomicBoolean,
    onNewSession: (S) -> Unit,
    sessions: MutableList<S>
  ) {
    val targetKey = targetKeyOf(mod)
    val effectiveRoot = mod.data.root
    val needsRebase = try {
      effectiveRoot.canonicalPath != this.root.canonicalPath
    } catch (e: Exception) {
      log.warn("Failed to compare root paths: ${effectiveRoot.absolutePath} vs ${this.root.absolutePath}", e)
      false
    }
    val rebasedMod = if (needsRebase) {
      log.info("Rebasing task into target folder: ${effectiveRoot.canonicalPath}")
      mod.rebase(this.root, effectiveRoot)
    } else {
      mod
    }
    val newRoot = rebasedMod.data.main_file?.parentFile ?: root

    ctx.reset()
    if (cancelFlag.get()) {
      log.info("Cancellation requested, skipping execution of remaining tasks")
      updateTaskStatus(targetKey, TaskStatus.CANCELLED)
      throw CancellationException("Execution cancelled")
    }
    updateTaskStatus(targetKey, TaskStatus.RUNNING)
    try {
      ctx.execute(
        DocTaskRequest(
          taskKind = rebasedMod.taskType,
          message = rebasedMod.message(),
          executionConfig = executionConfig(rebasedMod, ctx),
          typeConfig = rebasedMod.typeConfig,
          patchProcessor = rebasedMod.patchProcessorAs(),
          workingDir = newRoot,
          timeoutMinutes = taskTimeoutMinutes,
        ),
        object : DocTaskCallbacks<S> {
          override fun onSessionStarted(session: S, sessionId: String) {
            if (cancelFlag.get()) {
              log.info("Cancellation requested, skipping execution of remaining tasks")
              updateTaskStatus(targetKey, TaskStatus.CANCELLED, sessionId = sessionId)
              throw CancellationException("Execution cancelled")
            }
            updateTaskStatus(targetKey, TaskStatus.RUNNING, sessionId = sessionId)
            onNewSession(session)
            sessions += session
          }

          override fun onCompleted(sessionId: String) {
            log.info("Task completed for target '$targetKey' in session $sessionId")
            updateTaskStatus(targetKey, TaskStatus.COMPLETED, sessionId = sessionId)
          }

          override fun onFailed(error: Throwable) {
            log.warn("Task failed for target '$targetKey' with error: ${error.message}", error)
            updateTaskStatus(
              targetKey,
              TaskStatus.FAILED,
              error = error.message ?: error.javaClass.simpleName,
              sessionId = null
            )
          }
        }
      )
    } catch (e: CancellationException) {
      updateTaskStatus(targetKey, TaskStatus.CANCELLED, error = e.message)
      throw e
    } catch (e: Throwable) {
      log.warn("Error executing task for target '$targetKey'", e)
      updateTaskStatus(targetKey, TaskStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
      throw e
    }
  }

  /**
   * Builds the JSON-compatible execution configuration for a task. Anything that cannot be derived
   * declaratively is delegated to [DocExecutionContext.inferTaskConfig].
   */
  fun executionConfig(
    mod: ModificationTask<K>,
    ctx: DocExecutionContext<K, S, P>,
  ): MutableMap<String, Any> {
    val data = mod.data.copy()
    val config = when {
      mod.taskType.isFileTask -> {
        val baseCfgJson = mapOf(
          "task_type" to mod.taskType.name,
        ) + data.jsonCast<Map<String, Any>>()
        data.taskConfigOverrides?.let { baseCfgJson + it } ?: baseCfgJson
      }

      mod.taskType.isTemplateTask -> {
        val baseCfgJson = mapOf(
          "task_type" to mod.taskType.name,
          "template_file" to data.related_files?.firstOrNull { it.endsWith(".erb") }
        ) + data.jsonCast<Map<String, Any>>()
        data.taskConfigOverrides?.let { baseCfgJson + it } ?: baseCfgJson
      }

      else -> {
        // For non-file tasks (e.g. ImageVariation), ask the host to infer a proper config
        val newRoot = data.main_file?.parentFile ?: root
        val rebasedData = data.copy(root = newRoot)
        val taskConfig = ctx.inferTaskConfig(
          DocTaskInferenceRequest(
            taskKind = mod.taskType,
            taskDescription = rebasedData.task_description,
            prompt = "Execute the following task based on the provided context. Task type: ${mod.taskType.name}",
            history = buildList {
              add("Task type: ${mod.taskType.name}")
              add("Task description: ${rebasedData.task_description}")
              rebasedData.relative_files?.forEach { text -> add("Output file: $text") }
              rebasedData.relative_related_files?.forEach { relatedFile ->
                val resolvedFile =
                  if (File(relatedFile).isAbsolute) File(relatedFile) else newRoot.resolve(relatedFile)
                if (resolvedFile.exists()) {
                  add("Related file ($relatedFile):\n```\n${resolvedFile.readText()}\n```")
                }
              }
              val message = mod.message()
              if (message.isNotBlank()) add(message)
            },
            workingDir = newRoot,
            patchProcessor = mod.patchProcessorAs(),
            typeConfig = mod.typeConfig,
          )
        )
        JsonUtil.merge(taskConfig, mod.data.taskConfigOverrides ?: emptyMap<String, Any>())
      }
    }.jsonCast<MutableMap<String, Any>>()
    config["related_files"] = (config["related_files"] ?: emptyList<String>()).let {
      when {
        it is List<*> -> it.filterIsInstance<String>()
        else -> emptyList()
      }
    }
    config["task_description"] = buildString {
      appendLine(data.task_description)
      ((config["doc_files"] as? List<*> ?: emptyList<String>()).firstOrNull() as? String)?.let { docFile ->
        (root.resolve(docFile).readText().trim().trimIndent().let { docContent ->
          if (docContent.startsWith("---")) {
            // If the doc content has frontmatter, try to strip it for a cleaner description
            val endOfFrontmatter = docContent.indexOf("\n---\n")
            if (endOfFrontmatter != -1) {
              (docContent.substring(endOfFrontmatter + 5).trim())
            } else {
              (docContent)
            }
          } else {
            (docContent)
          }
        }.ifBlank { null }?.let {
          if (templateVarOverrides.isNotEmpty()) {
            applyTemplateSubstitutions(it, templateVarOverrides)
          } else {
            it
          }
        })?.let { append(it) }
      }
    }
    return config
  }

  fun sortByDependencies(tasks: List<ModificationTask<K>>): List<ModificationTask<K>> {
    if (tasks.isEmpty()) return tasks
    // Build a map from target file path to task
    val taskByTarget = tasks.associateBy { task ->
      task.data.main_file?.let { normalizePath(it.canonicalPath) } ?: ""
    }.filterKeys { it.isNotEmpty() }
    // Build adjacency list: task -> tasks it depends on
    val dependencies = tasks.associateWith { task ->
      task.data.related_files?.mapNotNull { relatedFile ->
        val canonicalPath = try {
          normalizePath(relatedFile.canonicalPath)
        } catch (e: Exception) {
          log.warn("Failed to resolve related file path: $relatedFile", e)
          return@mapNotNull null
        }
        taskByTarget[canonicalPath]
      }?.filter { it != task }?.distinct() ?: emptyList()
    }
    // Kahn's algorithm for topological sort with cycle handling
    val result = mutableListOf<ModificationTask<K>>()
    val queue = LinkedList<ModificationTask<K>>()
    tasks.filter { (dependencies[it]?.size ?: 0) == 0 }.forEach { queue.add(it) }
    val remaining = tasks.toMutableSet()
    remaining.removeAll(queue.toSet())
    while (queue.isNotEmpty() || remaining.isNotEmpty()) {
      if (queue.isNotEmpty()) {
        val task = queue.poll()
        result.add(task)
        remaining.filter { dependencies[it]?.contains(task) == true }.forEach { dependent ->
          val newDeps = (dependencies[dependent] ?: emptyList()).filter { it in remaining }
          if (newDeps.isEmpty()) {
            queue.add(dependent)
            remaining.remove(dependent)
          }
        }
      } else {
        // Cycle detected - break it by picking a task with minimum remaining dependencies
        val taskToBreak = remaining.minByOrNull { task ->
          (dependencies[task] ?: emptyList()).count { it in remaining }
        }
        if (taskToBreak != null) {
          log.warn("Dependency cycle detected, breaking cycle by processing: ${taskToBreak.data.relative_files?.firstOrNull()}")
          queue.add(taskToBreak)
          remaining.remove(taskToBreak)
        } else {
          log.warn("Unable to resolve remaining tasks, adding them in original order")
          result.addAll(remaining)
          remaining.clear()
        }
      }
    }
    log.info("Sorted ${tasks.size} tasks by dependencies")
    return result
  }

  /*
   * ------------------------------------------------------------------
   * Frontmatter parsing
   * ------------------------------------------------------------------
   */

  fun parseMarkdownWithFrontmatter(file: File): DocSpec? {
    if (!file.exists() || !file.isFile) {
      log.warn("File does not exist or is not a file: ${file.absolutePath}")
      return null
    }
    val content = try {
      file.readText()
    } catch (e: Exception) {
      log.error("Failed to read file: ${file.absolutePath}", e)
      return null
    }
    // Check for YAML frontmatter (starts with ---)
    if (!content.startsWith("---")) {
      return null
    }
    val endOfFrontmatter = content.indexOf("---", 3)
    if (endOfFrontmatter == -1) {
      return null
    }
    val frontmatterText = content.substring(3, endOfFrontmatter).trim()
    val rawFrontmatter = parseFrontmatter(frontmatterText)
    val declaredTemplateVars = parseTemplateVars(rawFrontmatter)
    val templateVars = if (templateVarOverrides.isEmpty()) {
      declaredTemplateVars
    } else {
      val merged = linkedMapOf<String, String>()
      merged.putAll(declaredTemplateVars)
      templateVarOverrides.forEach { (k, v) -> merged[k] = v }
      merged
    }
    val frontmatter = if (templateVars.isNotEmpty()) {
      val stripped = rawFrontmatter.filterKeys { it !in TEMPLATE_VAR_KEYS }
      val substitutedFrontmatterText = applyTemplateSubstitutions(
        renderFrontmatterToYaml(stripped),
        templateVars
      )
      parseFrontmatter(substitutedFrontmatterText)
    } else {
      rawFrontmatter
    }
    val bodyText = content.substring(endOfFrontmatter + 3).trim()
    val substitutedBody = if (templateVars.isNotEmpty()) {
      applyTemplateSubstitutions(bodyText, templateVars)
    } else {
      bodyText
    }
    val specifies = parseSpecifies(frontmatter)
    val documents = parseDocuments(frontmatter)
    val transforms = parseTransforms(frontmatter)
    val generates = parseGenerates(frontmatter)
    val targetFolder = parseFolder(frontmatter)
    if (specifies.isEmpty() && transforms.isEmpty() && documents.isEmpty() && generates.isEmpty() && targetFolder == null) {
      return null
    }
    return DocSpec(
      docFile = file,
      specifies = specifies,
      documents = documents,
      transforms = transforms,
      generates = generates,
      related = parseRelated(frontmatter),
      content = substitutedBody,
      frontmatter = frontmatter,
      taskType = parseTaskType(frontmatter),
      taskConfigJson = parseTaskConfigJson(frontmatter),
      updateMode = parseUpdateMode(frontmatter),
      targetFolder = targetFolder
    )
  }

  fun parseUpdateMode(frontmatter: Map<String, Any>): String? {
    return frontmatter["update_mode"] as? String
  }

  fun parseFolder(frontmatter: Map<String, Any>): String? {
    return frontmatter["folder"] as? String
  }

  fun resolveEffectiveRoot(
    specs: List<DocSpec>,
    transforms: List<TransformMatch> = emptyList(),
    documents: List<DocumentMatch> = emptyList(),
    generates: List<GenerateMatch> = emptyList()
  ): File {
    val overrideSpec = specs.firstOrNull { it.targetFolder != null }
      ?: transforms.firstOrNull { it.spec.targetFolder != null }?.spec
      ?: documents.firstOrNull { it.docSpec.targetFolder != null }?.docSpec
      ?: generates.firstOrNull { it.spec.targetFolder != null }?.spec
    if (overrideSpec == null) return root
    val rootOverridePath = overrideSpec.targetFolder ?: return root
    val resolvedRoot = overrideSpec.docFile.parentFile.resolve(rootOverridePath).canonicalFile
    val canonicalDefaultRoot = root.canonicalFile
    if (!resolvedRoot.canonicalPath.startsWith(canonicalDefaultRoot.canonicalPath + File.separator) &&
      resolvedRoot.canonicalPath != canonicalDefaultRoot.canonicalPath
    ) {
      throw IllegalArgumentException(
        "root_override '$rootOverridePath' resolves to '${resolvedRoot.canonicalPath}' " +
            "which is not under the default root '${canonicalDefaultRoot.canonicalPath}'. " +
            "The root override must be strictly under the default root directory."
      )
    }
    log.info("Using root override: ${resolvedRoot.canonicalPath} (from ${overrideSpec.docFile.name})")
    return resolvedRoot
  }

  fun resolveUpdateMode(
    specs: List<DocSpec>,
    transforms: List<TransformMatch> = emptyList(),
    documents: List<DocumentMatch> = emptyList(),
    generates: List<GenerateMatch> = emptyList()
  ): UpdateMode {
    val perDocMode = specs.firstNotNullOfOrNull { it.updateMode }
      ?: transforms.firstNotNullOfOrNull { it.spec.updateMode }
      ?: documents.firstNotNullOfOrNull { it.docSpec.updateMode }
      ?: generates.firstNotNullOfOrNull { it.spec.updateMode }
    return if (perDocMode != null) {
      val resolved = UpdateModes.fromName(perDocMode)
      if (resolved != null) {
        log.info("Using per-doc update mode: $perDocMode")
        resolved
      } else {
        log.warn("Unknown per-doc update mode '$perDocMode', falling back to global: ${updateMode}")
        updateMode
      }
    } else {
      updateMode
    }
  }

  fun parseTaskType(frontmatter: Map<String, Any>): String? {
    return frontmatter["task_type"] as? String
  }

  fun parseTaskConfigJson(frontmatter: Map<String, Any>): String? {
    return frontmatter["task_config_json"] as? String
  }

  fun buildCombinedTaskDescription(
    specs: List<DocSpec>,
    transforms: List<TransformMatch>,
    documents: List<Any>,
    generates: List<Any>,
    target: File,
    taskType: K
  ) = buildString {
    when {
      specs.size == 1 && specs.first().let { it.frontmatter["prompt"] } is String -> {
        appendLine(specs.first().let { it.frontmatter["prompt"] } as String)
      }

      taskType.isSubPlanTask -> {
        appendLine("Perform ${taskType.name} generation.")
        appendLine("Use the provided documentation and specifications as context for the processing.")
      }

      !taskType.isFileTask -> {
        appendLine("Produce the file ${target.name} according to the task type ${taskType.name}.")
        appendLine("Use the provided documentation and specifications as context for the processing.")
      }

      specs.isNotEmpty() || transforms.isNotEmpty() -> {
        appendLine("Update the file ${target.name} based on the included documentation and specifications.")
        appendLine("Ensure the file conforms to all the patterns, standards, and requirements described.")
        appendLine("If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.")
      }

      documents.isNotEmpty() -> {
        appendLine("Update the documentation file ${target.name} based on the supporting source files included as context.")
        appendLine("The documentation should accurately reflect the current state of the code.")
        appendLine("Update any outdated information, add documentation for new features, and ensure consistency with the actual implementation.")
      }

      generates.isNotEmpty() -> {
        appendLine("Generate or update the file ${target.name} based on the documentation and input files provided as context.")
        appendLine("The output should follow the patterns and requirements described in the documentation.")
        appendLine("Use the input files as source material to create the appropriate output.")
      }
    }
  }

  companion object {
    internal val log = LoggerFactory.getLogger(DocProcessorBase::class.java)

    private val MARKDOWN_LINK_REGEX = Regex("""^\s*\[[^\]]*]\(\s*([^)\s]+)\s*\)\s*$""")

    fun extractPathFromMarkdownLink(value: String): String {
      val match = MARKDOWN_LINK_REGEX.matchEntire(value) ?: return value.trim()
      return match.groupValues[1].trim()
    }

    fun extractPathsFromMarkdownLinks(values: List<String>): List<String> =
      values.map { extractPathFromMarkdownLink(it) }

    val TEMPLATE_VAR_KEYS = setOf("template_vars", "template_variables", "vars", "variables")

    fun listTemplateVarKeys(file: File): Map<String, String> {
      if (!file.exists() || !file.isFile) {
        log.debug("listTemplateVarKeys: file does not exist or is not a file: ${file.absolutePath}")
        return emptyMap()
      }
      val content = try {
        file.readText()
      } catch (e: Exception) {
        log.warn("listTemplateVarKeys: failed to read file: ${file.absolutePath}", e)
        return emptyMap()
      }
      if (!content.startsWith("---")) return emptyMap()
      val end = content.indexOf("---", 3)
      if (end == -1) return emptyMap()
      val frontmatterText = content.substring(3, end).trim()
      return try {
        val frontmatter = parseFrontmatter(frontmatterText)
        parseTemplateVars(frontmatter)
      } catch (e: Exception) {
        log.warn("listTemplateVarKeys: failed to parse frontmatter for ${file.absolutePath}", e)
        emptyMap()
      }
    }

    fun listTemplateVarKeys(files: Iterable<File>): Map<String, String> {
      val merged = linkedMapOf<String, String>()
      for (file in files) {
        val vars = listTemplateVarKeys(file)
        for ((k, v) in vars) {
          if (k !in merged) merged[k] = v
        }
      }
      return merged
    }

    fun parseTemplateVars(frontmatter: Map<String, Any>): Map<String, String> {
      val result = linkedMapOf<String, String>()
      for (key in TEMPLATE_VAR_KEYS) {
        val value = frontmatter[key] ?: continue
        when (value) {
          is Map<*, *> -> {
            value.forEach { (k, v) ->
              if (k != null) result[k.toString()] = v?.toString() ?: ""
            }
          }

          is List<*> -> {
            value.filterIsInstance<String>().forEach { entry ->
              val trimmed = entry.trim()
              val sepIdx = trimmed.indexOfAny(charArrayOf(':', '='))
              if (sepIdx > 0) {
                val k = trimmed.substring(0, sepIdx).trim()
                val v = trimmed.substring(sepIdx + 1).trim()
                if (k.isNotEmpty()) result[k] = v
              } else if (trimmed.isNotEmpty()) {
                result[trimmed] = ""
              }
            }
          }

          is String -> {
            val trimmed = value.trim()
            val sepIdx = trimmed.indexOfAny(charArrayOf(':', '='))
            if (sepIdx > 0) {
              val k = trimmed.substring(0, sepIdx).trim()
              val v = trimmed.substring(sepIdx + 1).trim()
              if (k.isNotEmpty()) result[k] = v
            }
          }

          else -> {
            log.warn("Unsupported template variables value type for key '$key': ${value.javaClass.name}")
          }
        }
      }
      return result
    }

    fun applyTemplateSubstitutions(text: String, vars: Map<String, String>): String {
      if (vars.isEmpty()) return text
      val pattern = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*}}""")
      return pattern.replace(text) { match ->
        val name = match.groupValues[1]
        val replacement = vars[name]
        if (replacement != null) {
          Regex.escapeReplacement(replacement)
        } else {
          match.value
        }
      }
    }

    fun renderFrontmatterToYaml(frontmatter: Map<String, Any>): String {
      val sb = StringBuilder()
      for ((key, value) in frontmatter) {
        when (value) {
          is List<*> -> {
            sb.append(key).append(":\n")
            for (item in value) {
              sb.append("  - ").append(item?.toString() ?: "").append('\n')
            }
          }

          is Map<*, *> -> {
            sb.append(key).append(":\n")
            for ((k, v) in value) {
              sb.append("  ").append(k?.toString() ?: "").append(": ")
                .append(v?.toString() ?: "").append('\n')
            }
          }

          else -> {
            sb.append(key).append(": ").append(value.toString()).append('\n')
          }
        }
      }
      return sb.toString()
    }

    fun isGlobPattern(pattern: String): Boolean {
      return pattern.contains("*") || pattern.contains("?") || pattern.contains("[")
    }

    fun expandPatternOrLiteral(baseDir: File, pattern: String): List<File> {
      return if (isGlobPattern(pattern)) {
        if (pattern.contains("**")) {
          expandRecursiveGlob(baseDir, pattern)
        } else {
          expandSimpleGlob(baseDir, pattern)
        }
      } else {
        val targetFile = try {
          baseDir.resolve(pattern).canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to resolve literal path '$pattern'", e)
          baseDir.resolve(pattern)
        }
        listOf(targetFile)
      }
    }

    fun expandSimpleGlob(baseDir: File, pattern: String): List<File> {
      val patternFile = File(pattern)
      val directory = if (patternFile.parent != null) {
        try {
          baseDir.resolve(patternFile.parent).canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to resolve directory for pattern '$pattern'", e)
          return emptyList()
        }
      } else {
        try {
          baseDir.canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to get canonical path for baseDir", e)
          return emptyList()
        }
      }
      val globPattern = patternFile.name

      if (!directory.exists() || !directory.isDirectory) {
        log.warn("Directory does not exist: ${directory.absolutePath}")
        return emptyList()
      }

      val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$globPattern")

      return directory.listFiles()
        ?.filter { it.isFile && matcher.matches(it.toPath().fileName) }
        ?: emptyList()
    }

    fun expandRecursiveGlob(baseDir: File, pattern: String): List<File> {
      val beforeGlob = pattern.substringBefore("**").removeSuffix("/").removeSuffix("\\")
      val resolvedBase = if (beforeGlob.isNotEmpty()) {
        try {
          baseDir.resolve(beforeGlob).canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to resolve base directory for pattern '$pattern'", e)
          return emptyList()
        }
      } else {
        try {
          baseDir.canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to get canonical path for baseDir", e)
          return emptyList()
        }
      }
      val remainingPattern = pattern.substringAfter("**").removePrefix("/").removePrefix("\\")

      if (!resolvedBase.exists()) {
        log.warn("Base directory does not exist: ${resolvedBase.absolutePath}")
        return emptyList()
      }

      val matcher: PathMatcher = if (remainingPattern.isNotEmpty()) {
        try {
          FileSystems.getDefault().getPathMatcher("glob:$remainingPattern")
        } catch (e: Exception) {
          log.warn("Invalid glob pattern: $remainingPattern", e)
          PathMatcher { false }
        }
      } else {
        PathMatcher { true }
      }

      return resolvedBase.listFilesRecursively()
        .filter { it.isFile && matcher.matches(it.toPath().fileName) }
    }

    fun parseTransforms(frontmatter: Map<String, Any>): List<TransformSpec> {
      val transformValue = frontmatter["transforms"] ?: return emptyList()
      val transformStrings = when (transformValue) {
        is String -> listOf(transformValue)
        is List<*> -> transformValue.filterIsInstance<String>()
        else -> emptyList()
      }
      return transformStrings.mapNotNull { str ->
        val parts = str.split("->").map { it.trim() }
        if (parts.size == 2) {
          TransformSpec(
            sourcePattern = extractPathFromMarkdownLink(parts[0]),
            destinationPattern = extractPathFromMarkdownLink(parts[1])
          )
        } else {
          log.warn("Invalid transform format: $str (expected 'pattern -> destination')")
          null
        }
      }
    }

    fun parseDocuments(frontmatter: Map<String, Any>): List<String> {
      return when (val value = frontmatter["documents"]) {
        is String -> listOf(extractPathFromMarkdownLink(value))
        is List<*> -> extractPathsFromMarkdownLinks(value.filterIsInstance<String>())
        else -> emptyList()
      }
    }

    fun parseRelated(frontmatter: Map<String, Any>): List<String> {
      return when (val value = frontmatter["related"]) {
        is String -> listOf(extractPathFromMarkdownLink(value))
        is List<*> -> extractPathsFromMarkdownLinks(value.filterIsInstance<String>())
        else -> emptyList()
      }
    }

    fun parseGenerates(frontmatter: Map<String, Any>): List<GenerateSpec> {
      val generateValue = frontmatter["generates"] ?: return emptyList()
      return when (generateValue) {
        is Map<*, *> -> {
          @Suppress("UNCHECKED_CAST")
          parseGenerateSpec(generateValue as Map<String, Any>)?.let { listOf(it) } ?: emptyList()
        }

        is List<*> -> {
          generateValue.mapNotNull { item ->
            when (item) {
              is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                parseGenerateSpec(item as Map<String, Any>)
              }

              else -> null
            }
          }
        }

        else -> emptyList()
      }
    }

    private fun parseGenerateSpec(map: Map<*, *>): GenerateSpec? {
      val output = (map["output"] as? String)?.let { extractPathFromMarkdownLink(it) } ?: return null
      val inputs = when (val inputsValue = map["inputs"]) {
        is String -> listOf(extractPathFromMarkdownLink(inputsValue))
        is List<*> -> extractPathsFromMarkdownLinks(inputsValue.filterIsInstance<String>())
        else -> emptyList()
      }
      if (inputs.isEmpty()) {
        log.warn("Generate spec for '$output' has no inputs")
        return null
      }
      return GenerateSpec(output = output, inputs = inputs)
    }

    fun parseSpecifies(frontmatter: Map<String, Any>): List<String> {
      return when (val value = frontmatter["specifies"]) {
        is String -> listOf(extractPathFromMarkdownLink(value))
        is List<*> -> extractPathsFromMarkdownLinks(value.filterIsInstance<String>())
        else -> emptyList()
      }
    }

    fun parseFrontmatter(text: String): Map<String, Any> {
      val result = mutableMapOf<String, Any>()
      val lines = text.lines()
      var i = 0

      while (i < lines.size) {
        val line = lines[i]
        if (line.contains(":")) {
          val colonIndex = line.indexOf(":")
          val key = line.substring(0, colonIndex).trim()
          val valueAfterColon = line.substring(colonIndex + 1).trim()

          if (valueAfterColon.isEmpty()) {
            val listItems = mutableListOf<String>()
            i++
            while (i < lines.size && lines[i].trimStart().startsWith("- ")) {
              listItems.add(lines[i].trimStart().removePrefix("- ").trim())
              i++
            }
            if (listItems.isNotEmpty()) {
              result[key] = listItems
            }
            continue
          } else {
            result[key] = valueAfterColon
          }
        }
        i++
      }
      return result
    }

    fun applyBackreferences(destPattern: String, matcher: Matcher): String {
      var result = destPattern
      val backrefRegex = Regex("""\$(\d+)([+-]\d+)?""")
      val matches = backrefRegex.findAll(result).toList().sortedByDescending { it.range.first }
      for (match in matches) {
        val groupIndex = match.groupValues[1].toInt()
        val arithmeticPart = match.groupValues[2]
        if (groupIndex > matcher.groupCount()) continue
        val groupValue = matcher.group(groupIndex) ?: ""
        val replacement = if (arithmeticPart.isNotEmpty()) {
          val numericGroup = groupValue.toLongOrNull()
          if (numericGroup != null) {
            val operator = arithmeticPart[0]
            val operand = arithmeticPart.substring(1).toLongOrNull() ?: 0L
            val computed = when (operator) {
              '+' -> numericGroup + operand
              '-' -> numericGroup - operand
              else -> numericGroup
            }
            computed.toString()
          } else {
            groupValue + arithmeticPart
          }
        } else {
          groupValue
        }
        result = result.substring(0, match.range.first) + replacement + result.substring(match.range.last + 1)
      }
      return result
    }

    fun expandTransformPattern(
      root: File,
      transform: TransformSpec,
      spec: DocSpec
    ): List<TransformMatch> {
      val sourceRegex = try {
        Pattern.compile(transform.sourcePattern)
      } catch (e: Exception) {
        log.warn("Invalid regex pattern: ${transform.sourcePattern}", e)
        return emptyList()
      }
      return root.listFilesRecursively().filter { it.isFile }.mapNotNull { sourceFile ->
        val relativePath = sourceFile.relativeTo(spec.docFile.parentFile.absoluteFile).path.replace("\\", "/")
        val matcher = sourceRegex.matcher(relativePath)
        if (matcher.matches()) {
          val destPath = applyBackreferences(transform.destinationPattern, matcher)
          TransformMatch(
            sourceFile = try {
              sourceFile.canonicalFile
            } catch (e: Exception) {
              log.warn("Failed to get canonical file for source", e)
              sourceFile
            },
            destinationFile = try {
              spec.docFile.parentFile.resolve(destPath).canonicalFile
            } catch (e: Exception) {
              log.warn("Failed to resolve destination file for transform", e)
              spec.docFile.parentFile.resolve(destPath)
            },
            spec = spec
          )
        } else {
          null
        }
      }
    }
  }
}