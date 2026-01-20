package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.util.concurrent.Executors
import java.util.regex.Pattern
import java.util.LinkedList

/**
 * DocProcessor processes markdown documentation files that specify target files via frontmatter.
 *
 * Each markdown file can contain a YAML frontmatter block with a 'specifies' key that contains
 * a glob pattern (or multiple patterns). Files matching this pattern will be updated based on the markdown content.
 *
 * Additionally, a 'transforms' key can specify source->destination file transformations using regex
 * patterns with capture groups and backreferences.
 */
open class DocProcessor(
  val root: File,
  val docsFolder: File,
  val overwriteMode: OverwriteMode = OverwriteModes.PatchToUpdate,
  val additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
  val concurrencyLimit: Int = 4,
  val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
  val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview
) {

  data class DocSpec(
    val docFile: File,
    val specifies: List<String>,
    val documents: List<String>,
    val transforms: List<TransformSpec>,
    val generates: List<GenerateSpec>,
    val related: List<String>,
    val content: String,
    val frontmatter: Map<String, Any>
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


  data class ModificationTask(
    val data: FileModificationTaskExecutionConfigData,
    val patchProcessor: PatchProcessors,
    val shouldDeleteTarget: Boolean = false
  )

  data class DocumentMatch(
    val docSpec: DocSpec,
    val supportingFiles: List<File>
  )

  fun run() {
    val concurrencyProcessor = FixedConcurrencyProcessor(Executors.newCachedThreadPool(), concurrencyLimit)
    val markdownFiles = docsFolder.listFilesRecursively()
      .filter { it.isFile && it.extension in setOf("md", "markdown") }
    log.info("Found ${markdownFiles.size} markdown files in ${docsFolder.absolutePath}")
    runAll(getAll(*markdownFiles.toTypedArray()), concurrencyProcessor)
  }

  open fun getAll(
    vararg markdownFiles: File,
  ): List<ModificationTask> {
    val docSpecs = markdownFiles.mapNotNull { parseMarkdownWithFrontmatter(it) }
    log.info("Found ${docSpecs.size} markdown files with 'specifies' frontmatter")
    val fileToSpecs = fileToSpecs(docSpecs)
    val documentMatches = documentMatches(docSpecs)
    val transformMatches = transformMatches(docSpecs)
    val generateMatches = generateMatches(docSpecs)
    val allTargetFiles = (fileToSpecs.keys + transformMatches.keys + documentMatches.keys + generateMatches.keys).distinct()
    log.info("Total unique target files: ${allTargetFiles.size}")
    return allTargetFiles.map { targetFile ->
      val specs = fileToSpecs[targetFile] ?: emptyList()
      val transforms = transformMatches[targetFile] ?: emptyList()
      val documents = documentMatches[targetFile] ?: emptyList()
      val generates = generateMatches[targetFile] ?: emptyList()
      val targetFile = File(targetFile)
      val relativeTarget = targetFile.relativeTo(root.absoluteFile)
      try {
        log.info(
          "Processing ${relativeTarget} based on ${specs.size} spec(s), ${transforms.size} transform(s), ${documents.size} document(s), and ${generates.size} generate(s): ${
            (specs.map { it.docFile.name } +
                transforms.map { "${it.spec.docFile.name}(${it.sourceFile.name})" } +
                documents.map<DocumentMatch, String> { "${it.docSpec.docFile.name}(${it.supportingFiles.size} files)" } +
                generates.map { "${it.spec.docFile.name}(${it.inputFiles.size} inputs)" }
                ).joinToString(", ")
          }")
        overwriteMode.prepare(
          source = primarySource(transforms, specs, documents, generates) ?: return@map null,
          target = targetFile,
          relatedFiles = allRelatedFiles(specs, targetFile, transforms, documents, generates)
        )?.let { patchProcessor ->
          ModificationTask(data(relativeTarget, specs, targetFile, transforms, documents, generates), patchProcessor)
        }
      } catch (e: Exception) {
        log.error("Error processing ${relativeTarget}", e)
        null
      }
    }.filterNotNull()
      .let { sortByDependencies(it) }
  }

  open fun data(
    relativeTarget: File,
    specs: List<DocSpec>,
    targetFile: File,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>,
    generates: List<GenerateMatch>
  ): FileModificationTaskExecutionConfigData = FileModificationTaskExecutionConfigData(
    files = listOf(relativeTarget.toString()),
    related_files = (specs.flatMap { spec ->
      listOf(spec.docFile.relativeTo(root.absoluteFile).toString()) +
          spec.related.map { relatedPath ->
            spec.docFile.parentFile.resolve(relatedPath).relativeTo(root.absoluteFile).toString()
          } +
          additionalContext(spec, targetFile)
    } + transforms.flatMap { match ->
      listOf(
        match.spec.docFile.relativeTo(root.absoluteFile).toString(),
        match.sourceFile.relativeTo(root.absoluteFile).toString()
      ) + match.spec.related.map { relatedPath ->
        match.spec.docFile.parentFile.resolve(relatedPath).relativeTo(root.absoluteFile).toString()
      } + additionalContext(match.spec, targetFile)
    } + documents.flatMap { docMatch ->
      docMatch.supportingFiles.map { it.relativeTo(root.absoluteFile).toString() } +
          docMatch.docSpec.related.map { relatedPath ->
            docMatch.docSpec.docFile.parentFile.resolve(relatedPath).relativeTo(root.absoluteFile).toString()
          } +
          additionalContext(docMatch.docSpec, targetFile)
    } + generates.flatMap { genMatch ->
      listOf(genMatch.spec.docFile.relativeTo(root.absoluteFile).toString()) +
          genMatch.inputFiles.map { it.relativeTo(root.absoluteFile).toString() } +
          genMatch.spec.related.map { relatedPath ->
            genMatch.spec.docFile.parentFile.resolve(relatedPath).relativeTo(root.absoluteFile).toString()
          } +
          additionalContext(genMatch.spec, targetFile)
    }).distinct(),
    task_description = buildCombinedTaskDescription(specs, transforms, documents, generates, targetFile),
  )

  open fun primarySource(
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

  open fun allRelatedFiles(
    specs: List<DocSpec>,
    targetFile: File,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>,
    generates: List<GenerateMatch>
  ): List<File> = (specs.flatMap { spec ->
    listOf(spec.docFile) +
        spec.related.map { spec.docFile.parentFile.resolve(it) } +
        additionalContext(spec, targetFile).map { File(it) }
  } + transforms.flatMap { match ->
    listOf(match.spec.docFile, match.sourceFile) +
        match.spec.related.map { match.spec.docFile.parentFile.resolve(it) } +
        additionalContext(match.spec, targetFile).map { File(it) }
  } + documents.flatMap { docMatch ->
    docMatch.supportingFiles +
        docMatch.docSpec.related.map { docMatch.docSpec.docFile.parentFile.resolve(it) } +
        additionalContext(docMatch.docSpec, targetFile).map { File(it) }
  } + generates.flatMap { genMatch ->
    listOf(genMatch.spec.docFile) +
        genMatch.inputFiles +
        genMatch.spec.related.map { genMatch.spec.docFile.parentFile.resolve(it) } +
        additionalContext(genMatch.spec, targetFile).map { File(it) }
  }).distinct()

  open fun transformMatches(docSpecs: List<DocSpec>): Map<String, List<TransformMatch>> {
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
  open fun generateMatches(docSpecs: List<DocSpec>): Map<String, List<GenerateMatch>> {
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


  open fun documentMatches(docSpecs: List<DocSpec>): Map<String, List<DocumentMatch>> {
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

  open fun fileToSpecs(docSpecs: List<DocSpec>): Map<String, List<DocSpec>> {
    val fileToSpecs = docSpecs
      .filter { it.specifies.isNotEmpty() }
      .flatMap { spec ->
        val baseDir = spec.docFile.parentFile
        spec.specifies.flatMap { pattern ->
          val targetFiles =     // If the pattern contains ** or other complex globs, handle differently
            if (pattern.contains("**")) {
              expandRecursiveGlob(baseDir, pattern)
            } else {
              expandSimpleGlob(baseDir, pattern)
            }
          log.info("Doc ${spec.docFile.name} specifies pattern '$pattern' -> ${targetFiles.size} files")
          targetFiles.map { targetFile -> targetFile.canonicalFile to spec }
        }
      }.groupBy({ it.first.absolutePath }, { it.second })
    log.info("Grouped into ${fileToSpecs.size} unique target files from 'specifies'")
    return fileToSpecs
  }

  open fun runAll(
    fileMods: List<ModificationTask>,
    concurrencyProcessor: FixedConcurrencyProcessor
  ) {
    withHarness(root, javaClass.simpleName, fastModel, smartModel) { harness ->
      fileMods.map { mod ->
        concurrencyProcessor.submit {
          harness.runTask(
            taskType = FileModification,
            typeConfig = TaskTypeConfig(task_type = FileModification.name),
            executionConfig = mod.data,
            timeoutMinutes = 5,
            workspace = root.absoluteFile,
            initSettings = { session ->
              harness.initSettings(
                session = session,
                workspace = root.absoluteFile,
                autoFix = true,
                taskType = FileModification,
                typeConfig = TaskTypeConfig(task_type = FileModification.name)
              ).apply {
                processor = mod.patchProcessor
              }
            }
          )
        }
      }.toTypedArray().forEach { it.get() }
    }
  }
  /**
   * Sorts modification tasks so that dependencies are processed before dependents.
   * Uses topological sorting with cycle detection - when cycles are encountered,
   * the cycle is broken by processing one of the cycle members to allow progress.
   *
   * @param tasks The list of modification tasks to sort
   * @return A sorted list where dependencies come before their dependents
   */
  open fun sortByDependencies(tasks: List<ModificationTask>): List<ModificationTask> {
    if (tasks.isEmpty()) return tasks
    // Build a map from target file path to task
    val taskByTarget = tasks.associateBy { task ->
      task.data.files?.firstOrNull()?.let { File(root, it).canonicalPath } ?: ""
    }.filterKeys { it.isNotEmpty() }
    // Build adjacency list: task -> tasks it depends on (tasks that modify files in its related_files)
    val dependencies = tasks.associateWith { task ->
      task.data.related_files?.mapNotNull { relatedFile ->
        val canonicalPath = File(root, relatedFile).canonicalPath
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
   * Build a combined task description from multiple specs and transforms
   */
  open fun buildCombinedTaskDescription(
    specs: List<DocSpec>,
    transforms: List<TransformMatch>,
    documents: List<Any>, // Using Any to avoid inner class reference issues
    generates: List<Any>,
    target: File
  ): String {
    val parts = mutableListOf<String>()

    if (specs.isNotEmpty() || transforms.isNotEmpty()) {
      parts.add("Update the file ${target.name} based on the included documentation and specifications.")
      parts.add("Ensure the file conforms to all the patterns, standards, and requirements described.")
      parts.add("If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.")
    }

    if (documents.isNotEmpty()) {
      parts.add("Update the documentation file ${target.name} based on the supporting source files included as context.")
      parts.add("The documentation should accurately reflect the current state of the code.")
      parts.add("Update any outdated information, add documentation for new features, and ensure consistency with the actual implementation.")
    }
    if (generates.isNotEmpty()) {
      parts.add("Generate or update the file ${target.name} based on the documentation and input files provided as context.")
      parts.add("The output should follow the patterns and requirements described in the documentation.")
      parts.add("Use the input files as source material to create the appropriate output.")
    }


    return parts.joinToString("\n")
  }


  companion object {
    private val log = LoggerFactory.getLogger(DocProcessor::class.java)

    /**
     * Expand a simple glob pattern (e.g., *.kt) in a specific directory
     */
    fun expandSimpleGlob(baseDir: File, pattern: String): List<File> {
      // Resolve the pattern relative to the base directory (document's parent)
      val patternFile = File(pattern)
      val directory = if (patternFile.parent != null) {
        baseDir.resolve(patternFile.parent).canonicalFile
      } else {
        baseDir.canonicalFile
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
        baseDir.resolve(beforeGlob).canonicalFile
      } else {
        baseDir.canonicalFile
      }
      val remainingPattern = pattern.substringAfter("**").removePrefix("/").removePrefix("\\")

      if (!resolvedBase.exists()) {
        log.warn("Base directory does not exist: ${resolvedBase.absolutePath}")
        return emptyList()
      }

      // If remainingPattern is empty, match all files; otherwise use the glob
      val matcher: PathMatcher = if (remainingPattern.isNotEmpty()) {
        FileSystems.getDefault().getPathMatcher("glob:$remainingPattern")
      } else {
        PathMatcher { true }
      }

      return resolvedBase.listFilesRecursively()
        .filter { it.isFile && matcher.matches(it.toPath().fileName) }
    }

    /**
     * Parse a markdown file and extract frontmatter with 'specifies' key
     */
    fun parseMarkdownWithFrontmatter(file: File): DocSpec? {
      val content = file.readText()

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
        frontmatter = frontmatter
      )
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
          parseGenerateSpec(generateValue)?.let { listOf(it) } ?: emptyList()
        }
        is List<*> -> {
          // List of generate specs
          generateValue.mapNotNull { item ->
            when (item) {
              is Map<*, *> -> parseGenerateSpec(item)
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
            sourceFile = sourceFile.canonicalFile,
            destinationFile = spec.docFile.parentFile.resolve(destPath).canonicalFile,
            spec = spec
          )
        } else {
          null
        }
      }
    }
  }
}