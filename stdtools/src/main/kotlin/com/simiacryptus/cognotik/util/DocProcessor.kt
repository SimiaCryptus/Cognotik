package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.cognitive.ConversationalMode
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.FileTaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.writing.RenderErbTemplateTask.RenderErbTemplateTaskExecutionConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.asApiChatModel
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
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
import java.time.Instant
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture.allOf
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Status of a single target generation task
 */
enum class TaskStatus {
  PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

/**
 * Status entry for a single target generation task in docops.status.json
 */
data class TaskStatusEntry(
  val target: String,
  val status: TaskStatus,
  val sessionId: String? = null,
  val startedAt: String? = null,
  val completedAt: String? = null,
  val error: String? = null
)

/**
 * Root structure for docops.status.json
 */
data class DocOpsStatus(
  val lastUpdated: String,
  val tasks: Map<String, TaskStatusEntry>
)


/**
 * DocProcessor processes markdown documentation files that specify target files via frontmatter.
 *
 * Each markdown file can contain a YAML frontmatter block with a 'specifies' key that contains
 * a glob pattern (or multiple patterns). Files matching this pattern will be updated based on the markdown content.
 *
 * Additionally, a 'transforms' key can specify source->destination file transformations using regex
 * patterns with capture groups and backreferences.
 */
class DocProcessor(
  val root: File,
  val docsFolder: File,
  val updateMode: UpdateMode = UpdateModes.PatchToUpdate,
  val additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
  val fastModel: ChatModel,
  val smartModel: ChatModel,
  val imageModel: ChatModel,
  val serverless: Boolean = false,
  val openBrowser: Boolean = false,
  val urlCacheDir: File = File(root, ".doc-processor-cache/url-cache"),
  val autoFix: Boolean,
  val user: User,
  val parentSession: Session? = null,
) {
  private val statusFile = File(root, "docops.status.json")

  private fun readStatusLocked(): DocOpsStatus {
    return try {
      if (statusFile.exists()) {
        JsonUtil.fromJson(statusFile.readText(), DocOpsStatus::class.java)
      } else {
        DocOpsStatus(lastUpdated = nowTimestamp(), tasks = emptyMap())
      }
    } catch (e: Exception) {
      log.warn("Failed to read docops.status.json, starting fresh", e)
      DocOpsStatus(lastUpdated = nowTimestamp(), tasks = emptyMap())
    }
  }

  /**
   * Update the status of a specific target task in a thread-safe manner.
   */
  private fun updateTaskStatus(
    targetKey: String,
    status: TaskStatus,
    sessionId: String? = null,
    error: String? = null
  ) {
    synchronized(statusLock) {
      val current1 = readStatusLocked()
      val existingEntry1 = current1.tasks[targetKey]
      val now1 = nowTimestamp()
      val updatedEntry1 = TaskStatusEntry(
        target = targetKey,
        status = status,
        sessionId = sessionId ?: existingEntry1?.sessionId,
        startedAt = if (status == TaskStatus.RUNNING) now1 else existingEntry1?.startedAt,
        completedAt = if (status in setOf(
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.CANCELLED
          )
        ) now1 else existingEntry1?.completedAt,
        error = error
      )
      val updatedTasks1 = current1.tasks.toMutableMap()
      updatedTasks1[targetKey] = updatedEntry1
      statusFile.writeText(JsonUtil.toJson(DocOpsStatus(lastUpdated = now1, tasks = updatedTasks1)))
      log.info("Updated status for target '$targetKey' to $status${error?.let { " with error: $it" } ?: ""}")
      return
    }
  }

  /**
   * Mark all tasks currently in RUNNING status as the given status.
   * Used for cleanup when the overall process times out or is interrupted.
   */
  private fun markRunningTasksAs(status: TaskStatus, error: String? = null) {
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
      statusFile.writeText(JsonUtil.toJson(DocOpsStatus(lastUpdated = now1, tasks = updatedTasks1)))
      log.info("Marked all RUNNING tasks as $status${error?.let { " with error: $it" } ?: ""}")
      return
    }
  }


  /**
   * Initialize the status file with all planned tasks set to PENDING.
   */
  private fun initializeStatus(tasks: List<ModificationTask>) {
    synchronized(statusLock) {
      val now = nowTimestamp()
      val taskEntries = tasks.associate { task ->
        val targetKey = task.data.main_file?.let { filePath ->
          try {
            filePath.canonicalFile.relativeTo(root.canonicalFile).toString()
          } catch (_: IllegalArgumentException) {
            filePath.canonicalFile.absolutePath
          }
        } ?: "unknown"
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
      statusFile.writeText(JsonUtil.toJson(DocOpsStatus(lastUpdated = now, tasks = merged)))
    }
  }

  private fun nowTimestamp(): String =
    Instant.now().toString()


  /**
   * Check if a string is a URL (http or https)
   */
  fun isUrl(path: String): Boolean {
    return path.startsWith("http://") || path.startsWith("https://")
  }

  var showMenubar: Boolean = true

  /**
   * Fetch a URL and cache its content locally. Returns the local cached file.
   * If the URL has already been fetched, returns the cached version.
   */
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

  /**
   * Resolve a related resource path. If it's a URL, fetch and cache it.
   * If it's a local path, resolve it relative to the base directory.
   * Returns the resolved File, or null if the resource couldn't be resolved.
   */
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

  /**
   * Resolve a related resource path, supporting glob patterns, URLs, and literal paths.
   * For glob patterns (containing *, ?, or [), expands the pattern against the filesystem.
   * For URLs, fetches and caches the content.
   * For literal paths, returns the file even if it doesn't exist.
   */
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

  /**
   * Represents a file transformation specification.
   * @param sourcePattern Regex pattern to match source files
   * @param destinationPattern Destination pattern with backreferences (e.g., $1, $2)
   */
  data class TransformSpec(
    val sourcePattern: String,
    val destinationPattern: String
  )

  /**
   * Represents a non-pattern-based generation specification.
   * @param output The single output file path (relative to doc file)
   * @param inputs List of glob patterns for input files to include as context
   */
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
    val data: Map<String, Any>? = null,
    val taskConfigOverrides: Map<String, Any>? = null,
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

  data class ModificationTask(
    val data: ModificationTaskConfig,
    val message: (File) -> String = { "" },
    val patchProcessor: PatchProcessor? = null,
    val shouldDeleteTarget: Boolean = false,
    val taskType: TaskType<*, *> = FileModification
  ) {
    val typeConfig: TaskTypeConfig
      get() {
        val jsonCast = data.taskConfigOverrides?.jsonCast<TaskTypeConfig>()
        return jsonCast ?: taskType.newSettings() ?: TaskTypeConfig(task_type = taskType.name)
      }

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

  fun modificationTasks(docSpecs: List<DocSpec>): List<ModificationTask> {
    log.info("Found ${docSpecs.size} markdown files with 'specifies' frontmatter")

    return modificationTasksRecursive(docSpecs, emptySet(), 0)
  }

  /**
   * Normalize a file path for case-insensitive comparison.
   * This ensures that targets are aggregated consistently regardless of case differences in paths.
   */
  private fun normalizePath(path: String): String = path.lowercase()


  /**
   * Recursively compute modification tasks. After computing the initial set of targets,
   * check if any newly-generated target files would match additional doc specs (via specifies,
   * transforms, generates, or documents patterns). If so, treat those hypothetical files as
   * existing and re-expand to discover transitive targets. This continues until a fixed-point
   * is reached (no new targets are discovered), enabling proper dependency ordering for
   * multi-stage build pipelines with intermediate artifacts.
   *
   * @param docSpecs The doc specifications to process
   * @param knownTargets Set of target file paths already discovered in previous iterations
   * @param depth Current recursion depth (bounded to prevent infinite loops)
   * @return Complete list of modification tasks including transitively discovered ones
   */
  private fun modificationTasksRecursive(
    docSpecs: List<DocSpec>,
    knownTargets: Set<String>,
    depth: Int
  ): List<ModificationTask> {
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
    // We do this by checking if hypothetical new files (the targets we just discovered)
    // would match any doc spec patterns when treated as existing files.
    if (newTargets.isNotEmpty() && depth < maxDepth) {
      val transitiveTargets =
        discoverTransitiveTargets(docSpecs, newTargets.map { normalizePath(it) }.toSet(), allKnownTargets)
      if (transitiveTargets.isNotEmpty()) {
        log.info("Depth $depth: discovered ${transitiveTargets.size} transitive target(s) from ${newTargets.size} new target(s)")
        // Create synthetic DocSpecs or re-expand with the knowledge of hypothetical files
        val transitiveTasks = modificationTasksRecursive(docSpecs, allKnownTargets, depth + 1)
        // Merge: transitive tasks may overlap with current tasks for the same target.
        // Current-level tasks take priority; only add truly new transitive tasks.
        val currentTargetPaths = currentTasks.flatMap { it.data.relative_files ?: emptyList() }.toSet()
        val newTransitiveTasks = transitiveTasks.filter { task ->
          task.data.relative_files?.any { it !in currentTargetPaths } ?: false
        }
        return currentTasks + newTransitiveTasks
      }
    }

    return currentTasks
  }

  /**
   * Discover transitive targets: given a set of newly discovered target files (which don't
   * exist on disk yet), check if any doc spec transform/specifies/generates patterns would
   * match those hypothetical files and produce additional targets.
   *
   * @param docSpecs All doc specifications
   * @param newTargetPaths Absolute paths of newly discovered targets
   * @param allKnownTargets All targets discovered so far (to avoid re-discovering)
   * @return Set of new transitive target absolute paths not yet in allKnownTargets
   */
  private fun discoverTransitiveTargets(
    docSpecs: List<DocSpec>,
    newTargetPaths: Set<String>,
    allKnownTargets: Set<String>
  ): Set<String> {
    val transitiveTargets = mutableSetOf<String>()

    for (spec in docSpecs) {
      // Check transforms: if a new target matches a transform's source pattern,
      // it would produce a new destination
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

      // Check specifies: if a new target's path matches a specifies glob,
      // it's already covered by the spec. But the spec itself might generate
      // targets that are new. This is handled by re-running fileToSpecs with
      // hypothetical files, which happens in the recursive call.

      // Check generates: generates have fixed output paths, so they're already
      // discovered in the first pass. But if a generate's input glob matches
      // a new target, the generate task gains new context (handled by re-expansion).
    }

    return transitiveTargets
  }

  /**
   * Build a single ModificationTask for a given target file path.
   * Extracted from the inner loop of modificationTasks for reuse in recursive planning.
   */
  private fun buildModificationTask(
    targetFile: String,
    fileToSpecs: Map<String, List<DocSpec>>,
    transformMatches: Map<String, List<TransformMatch>>,
    documentMatches: Map<String, List<DocumentMatch>>,
    generateMatches: Map<String, List<GenerateMatch>>
  ): ModificationTask? {
    val normalizedTarget = normalizePath(targetFile)
    val specs =
      (fileToSpecs.entries.filter { normalizePath(it.key) == normalizedTarget }.flatMap { it.value }).toMutableList()
    val transforms = transformMatches.entries.filter { normalizePath(it.key) == normalizedTarget }.flatMap { it.value }
    val documents = documentMatches.entries.filter { normalizePath(it.key) == normalizedTarget }.flatMap { it.value }
    val generates = generateMatches.entries.filter { normalizePath(it.key) == normalizedTarget }.flatMap { it.value }
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
          data = this.run {
            // First check for explicit data_file in frontmatter
            val explicitDataFile = specs.firstNotNullOfOrNull { spec ->
              (spec.frontmatter["data_file"] as? String)?.let { dataPath ->
                spec.docFile.parentFile.resolve(dataPath).absolutePath
              }
            }
            // If no explicit data_file, check if we have a transform with a JSON source file
            val implicitDataFile = if (explicitDataFile == null && transforms.isNotEmpty()) {
              transforms.firstOrNull {
                it.sourceFile.extension.equals("json", ignoreCase = true)
              }?.sourceFile?.absolutePath
            } else null
            (explicitDataFile ?: implicitDataFile)?.let { dataFilePath ->
              val dataFile = File(dataFilePath)
              if (dataFile.exists()) {
                JsonUtil.fromJson(dataFile.readText(), Map::class.java) as Map<String, Any>
              } else {
                log.warn("Data file not found: $dataFilePath")
                null
              }
            }
          }
        ),
        message = { root: File ->
          buildString {
            when {
              FileTaskExecutionConfig::class.java.isAssignableFrom(taskType.executionConfigClass) -> this.appendLine(
                "Execute task."
              )

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

  /**
   * Resolve task config JSON overrides from frontmatter 'task_config_json' paths.
   * Loads the referenced JSON file and returns its contents as a map, or null if not specified.
   */
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

  /**
   * Resolves the task type to use based on frontmatter specifications.
   * Priority: specs > transforms > documents > generates, first non-null wins.
   * Defaults to FileModification if no task type is specified.
   */
  fun resolveTaskType(
    specs: List<DocSpec>,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>,
    generates: List<GenerateMatch>
  ): TaskType<*, *> {
    val taskTypeName = specs.firstNotNullOfOrNull { it.taskType }
      ?: transforms.firstNotNullOfOrNull { it.spec.taskType }
      ?: documents.firstNotNullOfOrNull { it.docSpec.taskType }
      ?: generates.firstNotNullOfOrNull { it.spec.taskType }
    return if (taskTypeName != null) {
      try {
        TaskType.valueOf(taskTypeName.replace(" ", ""))
      } catch (e: IllegalArgumentException) {
        log.warn("Unknown task type '$taskTypeName', defaulting to FileModification", e)
        FileModification
      }
    } else {
      FileModification
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
  ): List<File> = (specs.flatMap { spec ->
    listOf(spec.docFile) +
        spec.related.flatMap { relatedPath ->
          resolveRelatedResources(spec.docFile.parentFile, relatedPath)
        } +
        additionalContext(spec, targetFile).map { File(it) }
  } + transforms.flatMap { match ->
    listOf(match.spec.docFile, match.sourceFile) +
        match.spec.related.flatMap { relatedPath ->
          resolveRelatedResources(match.spec.docFile.parentFile, relatedPath)
        } +
        additionalContext(match.spec, targetFile).map { File(it) }
  } + documents.flatMap { docMatch ->
    docMatch.supportingFiles +
        docMatch.docSpec.related.flatMap { relatedPath ->
          resolveRelatedResources(docMatch.docSpec.docFile.parentFile, relatedPath)
        } +
        additionalContext(docMatch.docSpec, targetFile).map { File(it) }
  } + generates.flatMap { genMatch ->
    listOf(genMatch.spec.docFile) +
        genMatch.inputFiles +
        genMatch.spec.related.flatMap { relatedPath ->
          resolveRelatedResources(genMatch.spec.docFile.parentFile, relatedPath)
        } +
        additionalContext(genMatch.spec, targetFile).map { File(it) }
  }).distinct()

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
          .flatMap { spec -> spec.transforms.flatMap { transform -> expandTransformPattern(root, transform, spec) } }
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
          val matchedFiles =     // If the pattern contains ** or other complex globs, handle differently
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
    // These use the resolved targetFolder directory as the target key
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
    fileMods: List<ModificationTask>,
  ): List<List<ModificationTask>> {
    val queues = mutableListOf<List<ModificationTask>>()
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

        // Check if this mod is related to any already processed file
        if (targetFile in processedFiles || relatedFiles.any { it in processedFiles }) {
          // If so, start a new queue
          queues.add(mutableListOf(mod))
          processedFiles.add(targetFile)
          processedFiles.addAll(relatedFiles)
        } else {
          // Otherwise, add to the current queue
          if (queues.isEmpty()) {
            queues.add(mutableListOf(mod))
          } else {
            (queues.last() as MutableList).add(mod)
          }
          processedFiles.add(targetFile)
          processedFiles.addAll(relatedFiles)
        }
      } else {
        // If no target file, just add to the last queue
        if (queues.isEmpty()) {
          queues.add(mutableListOf(mod))
        } else {
          (queues.last() as MutableList).add(mod)
        }
      }
    }

    log.info("Separated into ${queues.size} queues based on file relationships")
    return queues
  }

  fun runAll(
    fileMods: List<ModificationTask>,
    pool: FixedConcurrencyProcessor = newProcessor(user = user),
    cancelFlag: AtomicBoolean = AtomicBoolean(false),
    onNewSession: (Session) -> Unit = { _ -> }
  ): Array<Session> {
    initializeStatus(fileMods)
    val sessions = mutableListOf<Session>()
    separateQueues(fileMods).map { sortByDependencies(it) }.filter { it.isNotEmpty() }.map { mods ->
      pool.submit {
        object : UnifiedHarness(
          serverless = serverless,
          openBrowser = openBrowser,
          fastModel = fastModel,
          smartModel = smartModel,
          imageModel = imageModel,
          showMenubar = showMenubar,
          user = user
        ) {
          override fun createTempDirectory(prefix: String) = root
            .resolve("workspaces/${javaClass.simpleName}/test-${PlanHarness.now()}")
            .apply { mkdirs() }
        }.use { harness ->
          if (cancelFlag.get()) {
            log.info("Cancellation requested, skipping execution of remaining tasks")
            mods.forEach { mod ->
              val targetKey = mod.data.main_file?.let { filePath ->
                try {
                  filePath.canonicalFile.relativeTo(root.canonicalFile).toString()
                } catch (_: IllegalArgumentException) {
                  filePath.canonicalFile.absolutePath
                }
              } ?: "unknown"
              updateTaskStatus(targetKey, TaskStatus.CANCELLED)
            }
            return@submit
          }
          val remainingMods = mods.toMutableList()
          try {
            for (mod in mods) {
              remainingMods.remove(mod)
              run(mod, harness, cancelFlag, onNewSession, sessions)
            }
          } catch (e: CancellationException) {
            // Mark any remaining unprocessed tasks as cancelled
            remainingMods.forEach { mod ->
              val targetKey = mod.data.main_file?.let { filePath ->
                try {
                  filePath.canonicalFile.relativeTo(root.canonicalFile).toString()
                } catch (_: IllegalArgumentException) {
                  filePath.canonicalFile.absolutePath
                }
              } ?: "unknown"
              updateTaskStatus(targetKey, TaskStatus.CANCELLED)
            }
          } catch (e: Throwable) {
            // Mark any remaining unprocessed tasks as failed
            remainingMods.forEach { mod ->
              val targetKey = mod.data.main_file?.let { filePath ->
                try {
                  filePath.canonicalFile.relativeTo(root.canonicalFile).toString()
                } catch (_: IllegalArgumentException) {
                  filePath.canonicalFile.absolutePath
                }
              } ?: "unknown"
              updateTaskStatus(
                targetKey,
                TaskStatus.FAILED,
                error = "Queue aborted: ${e.message ?: e.javaClass.simpleName}"
              )
            }
          }
        }
      }
    }.toTypedArray().let { futures ->
      try {
        allOf(*futures).get(90, TimeUnit.MINUTES)
      } catch (e: java.util.concurrent.TimeoutException) {
        log.error("DocProcessor timed out after 90 minutes, marking remaining RUNNING tasks as FAILED")
        markRunningTasksAs(TaskStatus.FAILED, "Timed out after 90 minutes")
        throw e
      } catch (e: java.util.concurrent.ExecutionException) {
        log.error("DocProcessor execution failed", e)
        markRunningTasksAs(TaskStatus.FAILED, "Execution failed: ${e.cause?.message ?: e.message}")
        throw e
      } catch (e: InterruptedException) {
        log.error("DocProcessor interrupted", e)
        markRunningTasksAs(TaskStatus.CANCELLED, "Interrupted")
        throw e
      }
    }
    return sessions.toTypedArray()
  }

  fun run(
    mod: ModificationTask,
    harness: UnifiedHarness,
    cancelFlag: AtomicBoolean,
    onNewSession: (Session) -> Unit,
    sessions: MutableList<Session>
  ) {
    val targetKey = mod.data.main_file?.let { filePath ->
      try {
        filePath.canonicalFile.relativeTo(root.canonicalFile).toString()
      } catch (_: IllegalArgumentException) {
        filePath.canonicalFile.absolutePath
      }
    } ?: "unknown"
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

    harness.resetSession()
    if (cancelFlag.get()) {
      log.info("Cancellation requested, skipping execution of remaining tasks")
      updateTaskStatus(targetKey, TaskStatus.CANCELLED)
      throw CancellationException("Execution cancelled")
    }
    updateTaskStatus(targetKey, TaskStatus.RUNNING)
    try {
      harness.runTask(
        taskType = rebasedMod.taskType,
        timeoutMinutes = 30,
        message = rebasedMod.message(),
        executionConfig = executionConfig(rebasedMod, harness),
        parentSession = parentSession,
        onComplete = { _: String, task: SessionTask ->
          val sessionId = task.ui.sessionId.toString()
          log.info("Task completed for target '$targetKey' in session $sessionId")
          updateTaskStatus(targetKey, TaskStatus.COMPLETED, sessionId = sessionId)
        },
        onError = { error: Throwable ->
          log.warn("Task failed for target '$targetKey' with error: ${error.message}", error)
          updateTaskStatus(targetKey, TaskStatus.FAILED, error = error.message ?: error.javaClass.simpleName, sessionId = null)
        }
      ) { session ->
        if (cancelFlag.get()) {
          log.info("Cancellation requested, skipping execution of remaining tasks")
          updateTaskStatus(targetKey, TaskStatus.CANCELLED, sessionId = session.toString())
          throw CancellationException("Execution cancelled")
        }
        updateTaskStatus(targetKey, TaskStatus.RUNNING, sessionId = session.toString())
        onNewSession(session)
        sessions += session
        harness.createSettings(
          session = session,
          autoFix = autoFix,
          typeConfig = rebasedMod.typeConfig,
          workingDir = newRoot.toString()
        ).apply {
          processor = rebasedMod.patchProcessor ?: processor
        }
      }
    } catch (e: CancellationException) {
      updateTaskStatus(targetKey, TaskStatus.CANCELLED, error = e.message)
      throw e
    } catch (e: Throwable) {
      log.warn("Error executing task for target '$targetKey'", e)
      updateTaskStatus(targetKey, TaskStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
      throw e
    }
  }

  fun executionConfig(
    mod: ModificationTask,
    harness: UnifiedHarness,
    task: SessionTask? = null,
    model: ChatInterface? = null
  ): TaskExecutionConfig {
    val model = model ?: harness.fastModel.asApiChatModel(user).let {
      if (task != null) it.getChildClient(task) else it
    }
    val data = mod.data.copy()
    return when {
      FileTaskExecutionConfig::class.java.isAssignableFrom(mod.taskType.executionConfigClass) -> {
        val baseCfgJson = mapOf(
          "task_type" to mod.taskType.name,
        ) + data.jsonCast<Map<String, Any>>()
        data.taskConfigOverrides?.let { baseCfgJson + it } ?: baseCfgJson
      }

      RenderErbTemplateTaskExecutionConfig::class.java.isAssignableFrom(mod.taskType.executionConfigClass) -> {
        val baseCfgJson = mapOf(
          "task_type" to mod.taskType.name,
          "template_file" to data.related_files?.firstOrNull { it.endsWith(".erb") }
        ) + data.jsonCast<Map<String, Any>>()
        data.taskConfigOverrides?.let { baseCfgJson + it } ?: baseCfgJson
      }

      else -> {
        // For non-file tasks (e.g. ImageVariation), use requestToTask to generate proper config
        mod.patchProcessor?.apply {
          harness.processor = this
        }
        val newRoot = data.main_file?.parentFile ?: root
        val data = data.copy(root = newRoot)
        val orchestrationConfig = harness.createSettings(
          session = Session.newGlobalID(),
          autoFix = true,
          typeConfig = mod.taskType.newSettings() ?: TaskTypeConfig(task_type = mod.taskType.name),
          workingDir = newRoot.toString(),
        )
        val contextMessages = buildList {
          add("Task type: ${mod.taskType.name}")
          add("Task description: ${data.task_description}")
          data.relative_files?.forEach { text -> add("Target file: $text") }
          data.relative_related_files?.forEach { relatedFile ->
            val resolvedFile =
              if (File(relatedFile).isAbsolute) File(relatedFile) else newRoot.resolve(relatedFile)
            if (resolvedFile.exists()) {
              add("Related file ($relatedFile):\n```\n${resolvedFile.readText()}\n```")
            }
          }
          val message = mod.message()
          if (message.isNotBlank()) add(message)
        }
        val (_, taskConfig) = ConversationalMode.requestToTask(
          defaultModel = model,
          fastModel = model,
          userMessage = data.task_description,
          orchestrationConfig = orchestrationConfig,
          prompt = "Execute the following task based on the provided context. Task type: ${mod.taskType.name}",
          history = contextMessages,
          singleStage = true,
          taskTypes = listOf(mod.taskType)
        )
        taskConfig
      }
    }.jsonCast(mod.taskType.executionConfigClass)
  }


  /**
   * Sorts modification tasks so that dependencies are processed before dependents.
   * Uses topological sorting with cycle detection - when cycles are encountered,
   * the cycle is broken by processing one of the cycle members to allow progress.
   *
   * @param tasks The list of modification tasks to sort
   * @return A sorted list where dependencies come before their dependents
   */
  fun sortByDependencies(tasks: List<ModificationTask>): List<ModificationTask> {
    if (tasks.isEmpty()) return tasks
    // Build a map from target file path to task
    val taskByTarget = tasks.associateBy { task ->
      task.data.main_file?.let { normalizePath(it.canonicalPath) } ?: ""
    }.filterKeys { it.isNotEmpty() }
    // Build adjacency list: task -> tasks it depends on (tasks that modify files in its related_files)
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
    val result = mutableListOf<ModificationTask>()
    val queue = LinkedList<ModificationTask>()
    // Tasks with no dependencies should be processed first
    tasks.filter { (dependencies[it]?.size ?: 0) == 0 }.forEach { queue.add(it) }
    val remaining = tasks.toMutableSet()
    remaining.removeAll(queue.toSet())
    while (queue.isNotEmpty() || remaining.isNotEmpty()) {
      if (queue.isNotEmpty()) {
        val task = queue.poll()
        result.add(task)
        // For each task that depends on this one, reduce its dependency count
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
          // Should not happen, but handle gracefully
          log.warn("Unable to resolve remaining tasks, adding them in original order")
          result.addAll(remaining)
          remaining.clear()
        }
      }
    }
    log.info("Sorted ${tasks.size} tasks by dependencies")
    return result
  }

  /**
   * Parse a markdown file and extract frontmatter with 'specifies' key
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
    // Parse YAML frontmatter (supports simple key: value and lists)
    val frontmatter = parseFrontmatter(frontmatterText)
    val specifies = parseSpecifies(frontmatter)
    val documents = parseDocuments(frontmatter)
    val transforms = parseTransforms(frontmatter)
    val generates = parseGenerates(frontmatter)
    val targetFolder = parseFolder(frontmatter)
    // Return null if neither specifies nor transforms are present
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
      content = content.substring(endOfFrontmatter + 3).trim(),
      frontmatter = frontmatter,
      taskType = parseTaskType(frontmatter),
      taskConfigJson = parseTaskConfigJson(frontmatter),
      updateMode = parseUpdateMode(frontmatter),
      targetFolder = targetFolder
    )
  }

  /**
   * Parse 'update_mode' from frontmatter.
   * This allows individual doc files to override the global update mode.
   * Supported values: SkipExisting, OverwriteExisting, OverwriteToUpdate,
   * PatchExisting, PatchToUpdate, ForceUpdate, ForceOverwrite
   */
  fun parseUpdateMode(frontmatter: Map<String, Any>): String? {
    return frontmatter["update_mode"] as? String
  }

  /**
   * Parse 'root_override' from frontmatter.
   * This allows individual doc files to specify a different root directory for task processing.
   * The value is a relative path from the document file's parent directory.
   * The resolved path must be strictly under the default root directory.
   */
  fun parseFolder(frontmatter: Map<String, Any>): String? {
    return frontmatter["folder"] as? String
  }

  /**
   * Resolve the effective root directory for a given set of specs.
   * Per-doc root_override in frontmatter takes priority.
   * The resolved root must be strictly under (or equal to) the default root directory.
   *
   * @param specs The doc specifications to check for root_override
   * @param transforms Transform matches to check for root_override
   * @param documents Document matches to check for root_override
   * @param generates Generate matches to check for root_override
   * @return The resolved root directory
   * @throws IllegalArgumentException if the resolved root is not under the default root
   */
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

  /**
   * Resolve the effective update mode for a given set of specs.
   * Per-doc update_mode in frontmatter takes priority over the global updateMode.
   * If multiple specs specify different update modes, the first one wins.
   */
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

  /**
   * Parse 'task_type' from frontmatter.
   * This specifies which task type to use for processing (defaults to FileModification).
   */
  fun parseTaskType(frontmatter: Map<String, Any>): String? {
    return frontmatter["task_type"] as? String
  }

  /**
   * Parse 'task_config_json' from frontmatter.
   * This specifies a relative file path to a JSON file containing task type configuration.
   */
  fun parseTaskConfigJson(frontmatter: Map<String, Any>): String? {
    return frontmatter["task_config_json"] as? String
  }

  fun buildCombinedTaskDescription(
    specs: List<DocSpec>,
    transforms: List<TransformMatch>,
    documents: List<Any>, // Using Any to avoid inner class reference issues
    generates: List<Any>,
    target: File,
    taskType: TaskType<*, *>
  ) = buildString {
    when {
      specs.size == 1 && specs.first().let { it.frontmatter["prompt"] } is String -> {
        appendLine(specs.first().let { it.frontmatter["prompt"] } as String)
      }

      SubPlanTask.SubPlanTaskExecutionConfigData::class.java.isAssignableFrom(taskType.executionConfigClass) -> {
        appendLine("Perform ${taskType.name} generation.")
        appendLine("Use the provided documentation and specifications as context for the processing.")
      }

      !FileTaskExecutionConfig::class.java.isAssignableFrom(taskType.executionConfigClass) -> {
        appendLine("Process the file ${target.name} according to the task type ${taskType.name}.")
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
    private val log = LoggerFactory.getLogger(DocProcessor::class.java)

    /**
     * Check if a pattern contains glob wildcards
     */
    fun isGlobPattern(pattern: String): Boolean {
      return pattern.contains("*") || pattern.contains("?") || pattern.contains("[")
    }

    /**
     * Expand a pattern that may be either a glob or a literal file path.
     * For literal paths (no wildcards), returns the file even if it doesn't exist yet.
     * For glob patterns, only returns existing files that match.
     */
    fun expandPatternOrLiteral(baseDir: File, pattern: String): List<File> {
      return if (isGlobPattern(pattern)) {
        // It's a glob pattern - only return existing files
        if (pattern.contains("**")) {
          expandRecursiveGlob(baseDir, pattern)
        } else {
          expandSimpleGlob(baseDir, pattern)
        }
      } else {
        // It's a literal file path - return it even if it doesn't exist
        val targetFile = try {
          baseDir.resolve(pattern).canonicalFile
        } catch (e: Exception) {
          log.warn("Failed to resolve literal path '$pattern'", e)
          baseDir.resolve(pattern)
        }
        listOf(targetFile)
      }
    }

    /**
     * Expand a simple glob pattern (e.g., *.kt) in a specific directory
     */
    fun expandSimpleGlob(baseDir: File, pattern: String): List<File> {
      // Resolve the pattern relative to the base directory (document's parent)
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
      // Pattern like "../src/**/*.kt" or "src/**/*.kt"
      // Split into the part before ** and the part after
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

      // If remainingPattern is empty, match all files; otherwise use the glob
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

    /**
     * Parse 'transforms' from frontmatter.
     * Supports formats:
     * - Single: "transforms: pattern -> destination"
     * - List:
     *   transforms:
     *     - "pattern1 -> destination1"
     *     - "pattern2 -> destination2"
     */
    fun parseTransforms(frontmatter: Map<String, Any>): List<TransformSpec> {
      val transformValue = frontmatter["transforms"] ?: return emptyList()
      val transformStrings = when (transformValue) {
        is String -> listOf(transformValue)
        is List<*> -> transformValue.filterIsInstance<String>()
        else -> emptyList()
      }
      return transformStrings.mapNotNull { str ->
        // Parse "sourcePattern -> destinationPattern"
        val parts = str.split("->").map { it.trim() }
        if (parts.size == 2) {
          TransformSpec(
            sourcePattern = parts[0],
            destinationPattern = parts[1]
          )
        } else {
          log.warn("Invalid transform format: $str (expected 'pattern -> destination')")
          null
        }
      }
    }

    /**
     * Parse 'documents' from frontmatter (supports single value or list)
     * This is the reverse of 'specifies' - the document itself is the target,
     * and the matching files are the supporting context.
     */
    fun parseDocuments(frontmatter: Map<String, Any>): List<String> {
      return when (val value = frontmatter["documents"]) {
        is String -> listOf(value)
        is List<*> -> value.filterIsInstance<String>()
        else -> emptyList()
      }
    }

    /**
     * Parse 'related' from frontmatter (supports single value or list)
     * These are additional files to include as context in modification tasks.
     */
    fun parseRelated(frontmatter: Map<String, Any>): List<String> {
      return when (val value = frontmatter["related"]) {
        is String -> listOf(value)
        is List<*> -> value.filterIsInstance<String>()
        else -> emptyList()
      }
    }

    fun parseGenerates(frontmatter: Map<String, Any>): List<GenerateSpec> {
      val generateValue = frontmatter["generates"] ?: return emptyList()
      return when (generateValue) {
        is Map<*, *> -> {
          // Single generate spec
          @Suppress("UNCHECKED_CAST")
          parseGenerateSpec(generateValue as Map<String, Any>)?.let { listOf(it) } ?: emptyList()
        }

        is List<*> -> {
          // List of generate specs
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

    /**
     * Parse a single generate spec from a map
     */
    private fun parseGenerateSpec(map: Map<*, *>): GenerateSpec? {
      val output = map["output"] as? String ?: return null
      val inputs = when (val inputsValue = map["inputs"]) {
        is String -> listOf(inputsValue)
        is List<*> -> inputsValue.filterIsInstance<String>()
        else -> emptyList()
      }
      if (inputs.isEmpty()) {
        log.warn("Generate spec for '$output' has no inputs")
        return null
      }
      return GenerateSpec(output = output, inputs = inputs)
    }

    /**
     * Parse 'specifies' from frontmatter (supports single value or list)
     */
    fun parseSpecifies(frontmatter: Map<String, Any>): List<String> {
      return when (val value = frontmatter["specifies"]) {
        is String -> listOf(value)
        is List<*> -> value.filterIsInstance<String>()
        else -> emptyList()
      }
    }

    /**
     * Parse YAML frontmatter into a map (supports simple values and lists)
     */
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
            // Could be a list or multi-line value
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

    /**
     * Apply backreference substitutions to a destination pattern using regex matcher groups.
     * Supports arithmetic modifiers on capture groups, e.g.:
     * - $1 -> replaced with group 1
     * - $2+1 -> if group 2 is numeric, replaced with (group2 + 1); otherwise replaced with group2 + "+1"
     * - $0-5 -> if group 0 is numeric, replaced with (group0 - 5); otherwise replaced with group0 + "-5"
     */
    fun applyBackreferences(destPattern: String, matcher: Matcher): String {
      var result = destPattern
      // Match backreferences like $0, $1, $2+1, $3-10, etc.
      val backrefRegex = Regex("""\$(\d+)([+-]\d+)?""")
      // Process in reverse order of match position to avoid offset issues
      val matches = backrefRegex.findAll(result).toList().sortedByDescending { it.range.first }
      for (match in matches) {
        val groupIndex = match.groupValues[1].toInt()
        val arithmeticPart = match.groupValues[2] // e.g. "+1", "-5", or ""
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
            // Not a number, append the arithmetic part as a literal suffix
            groupValue + arithmeticPart
          }
        } else {
          groupValue
        }
        result = result.substring(0, match.range.first) + replacement + result.substring(match.range.last + 1)
      }
      return result
    }


    /**
     * Expand a single transform pattern to file matches
     */
    fun expandTransformPattern(
      root: File,
      transform: TransformSpec,
      spec: DocSpec
    ): List<TransformMatch> {
      // Get all files that could potentially match
      // Compile the source pattern as a regex
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
          // Apply backreferences (with optional arithmetic) to destination pattern
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

    fun newProcessor(
      session: Session = Session.newUserID(), concurrency: Int = 4, user: User
    ): FixedConcurrencyProcessor =
      FixedConcurrencyProcessor(
        ApplicationServices.threadPoolManager.getPool(
          session,
          user
        ), concurrency
      )

    private val statusLock = Any()
  }
}

fun ChatModel.asApiChatModel(
  user: User
): ChatInterface {
  val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user)
  val name = provider?.name
  if (name == null) {
      throw IllegalStateException("Provider not specified for model $modelId")
  }
  val secureString = (userSettings.apis.find { it.provider?.name == name }?.key
    ?: throw IllegalStateException("API key for model provider $name not found in user settings"))
  return asApiChatModel((secureString).decrypt!!).instance(user)
}