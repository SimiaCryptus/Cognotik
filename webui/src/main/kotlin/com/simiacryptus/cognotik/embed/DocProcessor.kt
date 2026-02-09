package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.ConversationalMode
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.FileTaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.regex.Pattern

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
  val overwriteMode: OverwriteMode = OverwriteModes.PatchToUpdate,
  val additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
  val concurrencyLimit: Int = 4,
  val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  val serverless: Boolean = false,
  val openBrowser: Boolean = false,
  val urlCacheDir: File = File(root, ".doc-processor-cache/url-cache")
) {

  /**
   * Check if a string is a URL (http or https)
   */
  fun isUrl(path: String): Boolean {
    return path.startsWith("http://") || path.startsWith("https://")
  }

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

      val contentType = response.headers().firstValue("Content-Type").orElse("")
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
  ) {
    fun rebase(prevRoot: File, newRoot: File) = DocSpec(
      docFile = rebasePath(prevRoot, newRoot, docFile.relativeTo(prevRoot.canonicalFile).toString())
        .let { newRoot.resolve(it) },
      specifies = specifies.map { pattern ->
        rebasePatternOrPath(prevRoot, newRoot, pattern)
      },
      documents = documents.map { pattern ->
        rebasePatternOrPath(prevRoot, newRoot, pattern)
      },
      transforms = transforms.map { transform ->
        // Transform patterns are regex, not file paths — they are relative to the doc file's parent,
        // so they don't need rebasing since they're resolved relative to docFile at usage time
        transform
      },
      generates = generates.map { generate ->
        GenerateSpec(
          output = rebasePath(prevRoot, newRoot, generate.output),
          inputs = generate.inputs.map { inputPattern ->
            rebasePatternOrPath(prevRoot, newRoot, inputPattern)
          }
        )
      },
      related = related.map { relatedPath ->
        if (relatedPath.startsWith("http://") || relatedPath.startsWith("https://")) {
          relatedPath // URLs are not rebased
        } else {
          rebasePath(prevRoot, newRoot, relatedPath)
        }
      },
      content = content,
      frontmatter = frontmatter,
      taskConfigJson = taskConfigJson?.let { rebasePath(prevRoot, newRoot, it) },
      taskType = taskType
    )

    companion object {
      /**
       * Rebase a concrete file path from prevRoot to newRoot.
       * The path is assumed to be relative to prevRoot; the result is relative to newRoot.
       */
      private fun rebasePath(prevRoot: File, newRoot: File, relativePath: String): String {
        val absoluteFile = prevRoot.canonicalFile.resolve(relativePath).canonicalFile
        return absoluteFile.relativeTo(newRoot.canonicalFile).toString()
      }

      /**
       * Rebase a pattern or literal path. Glob patterns (containing *, ?, [) are rebased
       * by rebasing only the literal prefix directory portion. Literal paths are rebased directly.
       */
      private fun rebasePatternOrPath(prevRoot: File, newRoot: File, pattern: String): String {
        if (!isGlobPattern(pattern) && !pattern.contains("**")) {
          return rebasePath(prevRoot, newRoot, pattern)
        }
        // For glob patterns, rebase the literal directory prefix
        val separatorIndex = maxOf(
          pattern.lastIndexOf('/', pattern.indexOfFirst { it == '*' || it == '?' || it == '[' }),
          pattern.lastIndexOf('\\', pattern.indexOfFirst { it == '*' || it == '?' || it == '[' }),
          0
        )
        if (separatorIndex <= 0) {
          // Pattern has no directory prefix (e.g., "*.kt"), no rebasing needed
          return pattern
        }
        val dirPrefix = pattern.substring(0, separatorIndex)
        val globSuffix = pattern.substring(separatorIndex)
        val rebasedPrefix = rebasePath(prevRoot, newRoot, dirPrefix)
        return rebasedPrefix + globSuffix
      }
    }
  }

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
    val files: List<String>? = null,
    val related_files: List<String>? = null,
    val task_description: String = "",
    val template_file: String? = null,
    val data: Map<String, Any>? = null,
  ) {
    fun rebase(prevRoot: File, newRoot: File) = ModificationTaskConfig(
      files = files?.map { filePath ->
        prevRoot.canonicalFile.resolve(filePath).canonicalFile
          .relativeTo(newRoot.canonicalFile).toString()
      },
      related_files = related_files?.map { relatedPath ->
        if (relatedPath.startsWith("http://") || relatedPath.startsWith("https://")) {
          relatedPath // URLs are not rebased
        } else {
          val resolvedFile = prevRoot.canonicalFile.resolve(relatedPath).canonicalFile
          try {
            resolvedFile.relativeTo(newRoot.canonicalFile).toString()
          } catch (e: IllegalArgumentException) {
            // File is outside newRoot, use absolute path
            resolvedFile.absolutePath
          }
        }
      },
      task_description = task_description,
      template_file = template_file?.let {
        try {
          prevRoot.canonicalFile.resolve(it).canonicalFile
            .relativeTo(newRoot.canonicalFile).toString()
        } catch (e: IllegalArgumentException) {
          prevRoot.canonicalFile.resolve(it).canonicalFile.absolutePath
        }
      },
      data = data
    )
  }

  data class ModificationTask(
    val data: ModificationTaskConfig = ModificationTaskConfig(),
    val message: String = "",
    val patchProcessor: PatchProcessors = PatchProcessors.Fuzzy,
    val shouldDeleteTarget: Boolean = false,
    val taskType: TaskType<*, *> = FileModification
  ) {
    fun rebase(prevRoot: File, newRoot: File) = ModificationTask(
      data = data.rebase(prevRoot, newRoot),
      message = message,
      patchProcessor = patchProcessor,
      shouldDeleteTarget = shouldDeleteTarget,
      taskType = taskType
    )
  }

  data class DocumentMatch(
    val docSpec: DocSpec,
    val supportingFiles: List<File> = emptyList()
  )

  fun run() {
    val concurrencyProcessor = FixedConcurrencyProcessor(Executors.newCachedThreadPool(), concurrencyLimit)
    val markdownFiles = docsFolder.listFilesRecursively()
      .filter { it.isFile && it.extension in setOf("md", "markdown") }
    log.info("Found ${markdownFiles.size} markdown files in ${docsFolder.absolutePath}")
    if (markdownFiles.isEmpty()) {
      log.warn("No markdown files found in ${docsFolder.absolutePath}")
      return
    }
    val docSpecs = markdownFiles.mapNotNull { parseMarkdownWithFrontmatter(it) }
    val fileMods = modificationTasks(docSpecs)
    runAll(fileMods, concurrencyProcessor)
  }

  fun getAll(
    vararg markdownFiles: File,
  ) = modificationTasks(markdownFiles.mapNotNull { parseMarkdownWithFrontmatter(it) })

  fun modificationTasks(docSpecs: List<DocSpec>): List<ModificationTask> {
    log.info("Found ${docSpecs.size} markdown files with 'specifies' frontmatter")
    val fileToSpecs = fileToSpecs(docSpecs)
    val documentMatches = documentMatches(docSpecs)
    val transformMatches = transformMatches(docSpecs)
    val generateMatches = generateMatches(docSpecs)
    val allTargetFiles =
      (fileToSpecs.keys + transformMatches.keys + documentMatches.keys + generateMatches.keys).distinct()
    log.info("Total unique target files: ${allTargetFiles.size}")
    return allTargetFiles.map { targetFile ->
      val specs = (fileToSpecs[targetFile] ?: emptyList()).toMutableList()
      val transforms = transformMatches[targetFile] ?: emptyList()
      val documents = documentMatches[targetFile] ?: emptyList()
      val generates = generateMatches[targetFile] ?: emptyList()
      val targetFileObj = File(targetFile)
      val relativeTarget = targetFileObj.relativeTo(root.absoluteFile)
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
        if (source == null) {
          return@map null
        }
        val relatedFiles = allRelatedFiles(specs, targetFileObj, transforms, documents, generates)
        val patchProcessor = overwriteMode.prepare(
          source = source,
          target = targetFileObj,
          relatedFiles = relatedFiles
        )
        if (patchProcessor == null) {
          return@map null
        }
        val taskType = resolveTaskType(specs, transforms, documents, generates)
        val targetFile1 = File(targetFile)
        ModificationTask(
          data = ModificationTaskConfig(
            files = listOf(relativeTarget.toString()),
            related_files = relatedFiles.map { file ->
              try {
                file.canonicalFile.relativeTo(root.canonicalFile).toString()
              } catch (e: IllegalArgumentException) {
                // File is outside the root (e.g., cached URL content), use absolute path
                file.canonicalFile.absolutePath
              }
            }.distinct(),
            task_description = buildCombinedTaskDescription(
              specs,
              transforms,
              documents,
              generates,
              targetFile1,
              taskType
            ),
            template_file = specs.mapNotNull { spec ->
              (spec.frontmatter["template_file"] as? String)?.let { templatePath ->
                spec.docFile.parentFile.resolve(templatePath).relativeTo(root.absoluteFile).toString()
              }
            }.firstOrNull(),
            data = this.run {
              // First check for explicit data_file in frontmatter
              val explicitDataFile = specs.mapNotNull { spec ->
                (spec.frontmatter["data_file"] as? String)?.let { dataPath ->
                  spec.docFile.parentFile.resolve(dataPath).absolutePath
                }
              }.firstOrNull()
              // If no explicit data_file, check if we have a transform with a JSON source file
              val implicitDataFile = if (explicitDataFile == null && transforms.isNotEmpty()) {
                transforms.firstOrNull {
                  it.sourceFile.extension.equals(
                    "json",
                    ignoreCase = true
                  )
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
          message = buildString {
            when {
              FileTaskExecutionConfig::class.java.isAssignableFrom(taskType.executionConfigClass) -> this.appendLine("Execute task.")
              else -> {
                relatedFiles.forEach { relatedFile ->
                  this.appendLine("# Context file: $relatedFile")
                  this.appendLine("```")
                  val resolvedFile = if (File(relatedFile.toString()).isAbsolute) {
                    File(relatedFile.toString())
                  } else {
                    root.resolve(relatedFile)
                  }
                  if (resolvedFile.exists()) {
                    this.appendLine(resolvedFile.readText())
                  } else {
                    this.appendLine("<!-- File not found: $relatedFile -->")
                  }
                  this.appendLine("```")
                }
              }
            }
          },
          patchProcessor = patchProcessor,
          taskType = taskType
        )
      } catch (e: Exception) {
        log.error("Error processing ${relativeTarget}", e)
        null
      }
    }.filterNotNull().let { sortByDependencies(it) }
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
        spec.related.mapNotNull { relatedPath ->
          resolveRelatedResource(spec.docFile.parentFile, relatedPath)?.let { file ->
            // For URL-fetched files, return the absolute file so it won't be incorrectly relativized
            if (isUrl(relatedPath)) file.absoluteFile else file
          }
        } +
        additionalContext(spec, targetFile).map { File(it) }
  } + transforms.flatMap { match ->
    listOf(match.spec.docFile, match.sourceFile) +
        match.spec.related.mapNotNull { relatedPath ->
          resolveRelatedResource(match.spec.docFile.parentFile, relatedPath)?.let { file ->
            if (isUrl(relatedPath)) file.absoluteFile else file
          }
        } +
        additionalContext(match.spec, targetFile).map { File(it) }
  } + documents.flatMap { docMatch ->
    docMatch.supportingFiles +
        docMatch.docSpec.related.mapNotNull { relatedPath ->
          resolveRelatedResource(docMatch.docSpec.docFile.parentFile, relatedPath)?.let { file ->
            if (isUrl(relatedPath)) file.absoluteFile else file
          }
        } +
        additionalContext(docMatch.docSpec, targetFile).map { File(it) }
  } + generates.flatMap { genMatch ->
    listOf(genMatch.spec.docFile) +
        genMatch.inputFiles +
        genMatch.spec.related.mapNotNull { relatedPath ->
          resolveRelatedResource(genMatch.spec.docFile.parentFile, relatedPath)?.let { file ->
            if (isUrl(relatedPath)) file.absoluteFile else file
          }
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
      }.groupBy { it.destinationFile.absolutePath }
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
      }.groupBy { it.outputFile.absolutePath }
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
      }.groupBy { it.docSpec.docFile.absolutePath }
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

    // Combine both sources and group by target file
    val fileToSpecs = (specsFromSpecifies + specsFromTransforms)
      .groupBy({ it.first.absolutePath }, { it.second })
      .mapValues { it.value.distinct() }

    log.info("Grouped into ${fileToSpecs.size} unique target files from 'specifies'")
    return fileToSpecs
  }

  fun runAll(
    fileMods: List<ModificationTask>,
    concurrencyProcessor: FixedConcurrencyProcessor
  ): Session? {
    val sessionPromise = CompletableFuture<Session>()
    Thread{
      if (fileMods.isNotEmpty()) {
        object : UnifiedHarness(
          fastModel = fastModel,
          smartModel = smartModel,
          serverless = serverless,
          openBrowser = openBrowser,
        ) {
          override fun createTempDirectory(prefix: String) = root
            .resolve("workspaces/${javaClass.simpleName}/test-${PlanHarness.now()}")
            .apply { mkdirs() }
        }.use { harness: UnifiedHarness ->
          try {
            fileMods
              .map { mod ->
                val newRoot = mod.root() ?: root
                val mod = if(newRoot != root) mod.rebase(root, newRoot) else mod
                concurrencyProcessor.submit {
                  val executionConfig = if (FileTaskExecutionConfig::class.java.isAssignableFrom(mod.taskType.executionConfigClass)) {
                    // For file-based tasks, directly cast the config
                    val cfgJson = mapOf(
                      "task_type" to mod.taskType.name,
                    ) + mod.data.jsonCast<Map<String, Any>>()
                    cfgJson.jsonCast<TaskExecutionConfig>(mod.taskType.executionConfigClass)
                  } else {
                    // For non-file tasks (e.g. ImageVariation), use requestToTask to generate proper config
                    val orchestrationConfig = harness.initSettings(
                      session = sessionPromise.getNow(null) ?: Session.newGlobalID(),
                      autoFix = true,
                      typeConfig = TaskTypeConfig(task_type = mod.taskType.name),
                      workingDir = newRoot.toString()
                    )
                    val defaultModel = orchestrationConfig.defaultSmart
                    val fastModelClient = orchestrationConfig.defaultFast
                    val contextMessages = buildList {
                      add("Task type: ${mod.taskType.name}")
                      add("Task description: ${mod.data.task_description}")
                      mod.data.files?.forEach { add("Target file: $it") }
                      mod.data.related_files?.forEach { relatedFile ->
                        val resolvedFile = if (File(relatedFile).isAbsolute) File(relatedFile) else newRoot.resolve(relatedFile)
                        if (resolvedFile.exists()) {
                          add("Related file ($relatedFile):\n```\n${resolvedFile.readText()}\n```")
                        }
                      }
                      if (mod.message.isNotBlank()) add(mod.message)
                    }
                    val (_, taskConfig) = ConversationalMode.requestToTask(
                      defaultModel = defaultModel,
                      fastModel = fastModelClient,
                      userMessage = mod.data.task_description,
                      orchestrationConfig = orchestrationConfig,
                      prompt = "Execute the following task based on the provided context.",
                      history = contextMessages,
                      singleStage = true
                    )
                    taskConfig
                  }
                  harness.runTask(
                    taskType = mod.taskType,
                    timeoutMinutes = 30,
                    message = mod.message,
                    executionConfig = executionConfig
                  ) { session ->
                    sessionPromise.complete(session)
                    harness.initSettings(
                      session = session,
                      autoFix = true,
                      typeConfig = TaskTypeConfig(task_type = mod.taskType.name),
                      workingDir = newRoot.toString()
                    ).apply {
                      processor = mod.patchProcessor
                    }
                  }
                }
              }.toTypedArray().forEach { it.get() }
          } finally {
            concurrencyProcessor.shutdown()
          }
        }
      } else {
        log.info("No modification tasks to execute")
      }
    }.start()
    return sessionPromise.join()
  }

  private fun ModificationTask.root(): File? = data.files?.firstOrNull()?.let { root.resolve(it).parentFile }

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
      task.data.files?.firstOrNull()?.let { File(root, it).canonicalPath } ?: ""
    }.filterKeys { it.isNotEmpty() }
    // Build adjacency list: task -> tasks it depends on (tasks that modify files in its related_files)
    val dependencies = tasks.associateWith { task ->
      task.data.related_files?.mapNotNull { relatedFile ->
        val canonicalPath = try {
          File(root, relatedFile).canonicalPath
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
          log.warn("Dependency cycle detected, breaking cycle by processing: ${taskToBreak.data.files?.firstOrNull()}")
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
    val bodyContent = content.substring(endOfFrontmatter + 3).trim()
    // Parse YAML frontmatter (supports simple key: value and lists)
    val frontmatter = parseFrontmatter(frontmatterText)
    val specifies = parseSpecifies(frontmatter)
    val documents = parseDocuments(frontmatter)
    val transforms = parseTransforms(frontmatter)
    val generates = parseGenerates(frontmatter)
    val related = parseRelated(frontmatter)
    val taskType = parseTaskType(frontmatter)
    val taskConfigJson = parseTaskConfigJson(frontmatter)
    // Return null if neither specifies nor transforms are present
    if (specifies.isEmpty() && transforms.isEmpty() && documents.isEmpty() && generates.isEmpty()) {
      return null
    }
    return DocSpec(
      docFile = file,
      specifies = specifies,
      documents = documents,
      transforms = transforms,
      generates = generates,
      related = related,
      content = bodyContent,
      frontmatter = frontmatter,
      taskType = taskType,
      taskConfigJson = taskConfigJson
    )
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
          // Apply backreferences to destination pattern
          var destPath = transform.destinationPattern
          // Replace $0, $1, $2, etc. with captured groups
          for (i in 0..matcher.groupCount()) {
            val group = matcher.group(i) ?: ""
            destPath = destPath.replace("\$$i", group)
          }
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