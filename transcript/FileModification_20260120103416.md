# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/embed/DocProcessor.kt

```
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

  data class TransformMatch(
    val sourceFile: File,
    val destinationFile: File,
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
    val allTargetFiles = (fileToSpecs.keys + transformMatches.keys + documentMatches.keys).distinct()
      .associateWith { Triple(fileToSpecs[it], transformMatches[it], documentMatches[it]) }
    log.info("Total unique target files: ${allTargetFiles.size}")
    return allTargetFiles.keys.map { targetFile ->
      val specs = fileToSpecs[targetFile] ?: emptyList()
      val transforms = transformMatches[targetFile] ?: emptyList()
      val documents = documentMatches[targetFile] ?: emptyList()
      val targetFile = File(targetFile)
      val relativeTarget = targetFile.relativeTo(root.absoluteFile)
      try {
        log.info(
          "Processing ${relativeTarget} based on ${specs.size} spec(s), ${transforms.size} transform(s), and ${documents.size} document(s): ${
            (specs.map { it.docFile.name } +
                transforms.map { "${it.spec.docFile.name}(${it.sourceFile.name})" } +
                documents.map<DocumentMatch, String> { "${it.docSpec.docFile.name}(${it.supportingFiles.size} files)" }
                ).joinToString(", ")
          }")
        overwriteMode.prepare(
          source = primarySource(transforms, specs, documents) ?: return@map null,
          target = targetFile,
          relatedFiles = allRelatedFiles(specs, targetFile, transforms, documents)
        )?.let { patchProcessor ->
          ModificationTask(data(relativeTarget, specs, targetFile, transforms, documents), patchProcessor)
        }
      } catch (e: Exception) {
        log.error("Error processing ${relativeTarget}", e)
        null
      }
    }.filterNotNull()
  }

  open fun data(
    relativeTarget: File,
    specs: List<DocSpec>,
    targetFile: File,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>
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
    }).distinct(),
    task_description = buildCombinedTaskDescription(specs, transforms, documents, targetFile),
  )

  open fun primarySource(
    transforms: List<TransformMatch>,
    specs: List<DocSpec>,
    documents: List<DocumentMatch>
  ): File? = when {
    transforms.isNotEmpty() -> transforms.first().sourceFile
    specs.isNotEmpty() -> specs.first().docFile
    documents.isNotEmpty() -> documents.first().supportingFiles.firstOrNull() ?: documents.first().docSpec.docFile
    else -> null
  }

  open fun allRelatedFiles(
    specs: List<DocSpec>,
    targetFile: File,
    transforms: List<TransformMatch>,
    documents: List<DocumentMatch>
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
   * Build a combined task description from multiple specs and transforms
   */
  open fun buildCombinedTaskDescription(
    specs: List<DocSpec>,
    transforms: List<TransformMatch>,
    documents: List<Any>, // Using Any to avoid inner class reference issues
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
      val related = parseRelated(frontmatter)

      // Return null if neither specifies nor transforms are present
      if (specifies.isEmpty() && transforms.isEmpty() && documents.isEmpty()) {
        return null
      }

      return DocSpec(
        docFile = file,
        specifies = specifies,
        documents = documents,
        transforms = transforms,
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/embed/ExceptionFixer.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

class ExceptionFixer(
  val projectRoot: File = File(".").gitRoot() ?: throw IllegalStateException("Could not find .git folder in any parent directory"),
  val related_files: List<String> = emptyList()
) {
  fun fix(throwable: Throwable) {
    val codeFiles = throwable.getCodeFiles(projectRoot)
    val eAsString = throwable.toFullString() ?: return
    withHarness(
      root = projectRoot,
      testName = "SmartFixingExceptions",
    ) { harness ->
      try {
        harness.runTask(
          taskType = FileModificationTask.Companion.FileModification,
          typeConfig = TaskTypeConfig(task_type = FileModificationTask.Companion.FileModification.name),
          executionConfig = FileModificationTask.FileModificationTaskExecutionConfigData(
            files = codeFiles.map { it.relativeTo(projectRoot).toString() },
            related_files = related_files,
            task_description = eAsString,
          ),
          timeoutMinutes = 5,
          workspace = projectRoot.absoluteFile,
          initSettings = { session ->
            harness.initSettings(
              session = session,
              workspace = projectRoot.absoluteFile,
              autoFix = true,
              taskType = FileModificationTask.Companion.FileModification,
              typeConfig = TaskTypeConfig(task_type = FileModificationTask.Companion.FileModification.name)
            ).apply {
              processor = PatchProcessors.Fuzzy
            }
          }
        )
      } catch (e: Exception) {
        log.error("Error running task", e)
      }
    }
  }
  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(ExceptionFixer::class.java)
  }
}

fun Throwable.toFullString(): String? {
  val outputStream = ByteArrayOutputStream()
  this.printStackTrace(PrintStream(outputStream))
  return outputStream.toString("UTF-8") ?: this.toString()
}

fun Throwable.getCodeFiles(projectRoot: File): List<File> {
  val visited = mutableSetOf<Throwable>()
  fun helper(t: Throwable): List<File> {
    if (visited.contains(t)) return emptyList()
    visited.add(t)
    val files = t.stackTrace?.mapNotNull { element ->
      val classPath = element.className.replace('.', '/') + ".kt"
      projectRoot.walkTopDown().filter { file ->
        file.isDirectory && file.name == "kotlin" &&
            file.parentFile?.name == "main" &&
            file.parentFile?.parentFile?.name == "src"
      }.asSequence().mapNotNull { root ->
        val potentialFile = File(root, classPath)
        if (potentialFile.exists()) potentialFile else null
      }.firstOrNull()
    }?.distinct() ?: emptyList()
    t.cause?.let { helper(it) }
    t.suppressed.forEach { helper(it) }
    return files
  }
  return helper(this)
}

fun File.gitRoot(): File? {
  var current: File? = this.absoluteFile
  while (current != null) {
    if (File(current, ".git").exists()) {
      return current
    }
    current = current.parentFile
  }
  return null
}

```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/embed/FileGenerator.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.FileSelectionUtils.listFilesRecursively
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Executors

open class FileGenerator {
  fun run(
    root: File,
    folder: File,
    listFiles: (File, File) -> List<File> = { root, folder ->
      folder.listFilesRecursively()
        .filter { it.isFile && it.extension in setOf("kt") }
        .map { it.relativeTo(root.absoluteFile) }
    },
    targetFile: (File) -> File = { it },
    overwriteMode: OverwriteMode = OverwriteModes.SkipExisting,
    relatedFiles: (File) -> List<String>,
    generationPrompt: (File, File) -> String,
    concurrencyLimit: Int = 4
  ) {
    val concurrencyProcessor = FixedConcurrencyProcessor(
      pool = Executors.newCachedThreadPool(),
      concurrencyLimit = concurrencyLimit
    )
    withHarness(root, javaClass.simpleName) { harness ->
      (listFiles)(root, folder).shuffled()
        .map { source ->
          val target = (targetFile)(source)
          overwriteMode.prepare(source, target)?.let { patchProcessor ->
            concurrencyProcessor.submit {
              try {
                harness.runTask(
                  taskType = FileModification,
                  typeConfig = TaskTypeConfig(task_type = FileModification.name),
                  executionConfig = FileModificationTaskExecutionConfigData(
                    files = listOf(target.toString()),
                    related_files = (relatedFiles)(source),
                    task_description = (generationPrompt)(source, target),
                  ),
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
                      processor = patchProcessor
                    }
                  }
                )
              } catch (e: Exception) {
                log.error("Error running task", e)
              }
            }
          }
        }.toTypedArray().forEach { it?.get() }
    }
  }


  companion object {
    private val log = LoggerFactory.getLogger(FileGenerator::class.java)
    fun File.lastModified(
      relatedFiles: List<File>,
    ): Long = maxOf(this.lastModified(), relatedFiles.maxOfOrNull { it.lastModified() } ?: 0L)
  }
}


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/embed/OverwriteModes.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.util.FileGenerator.Companion.lastModified
import java.io.File

enum class OverwriteModes : OverwriteMode {

  SkipExisting {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? = null
  },
  
  OverwriteExisting {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? {
      target.delete()
      return PatchProcessors.FullReplacement
    }
  },
  
  OverwriteToUpdate {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? {
      return if (source.lastModified(relatedFiles) > target.lastModified()) {
        target.delete()
        PatchProcessors.FullReplacement
      } else {
        null
      }
    }
  },
  
  PatchExisting {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? = PatchProcessors.Fuzzy
  },
  
  PatchToUpdate {
    override fun prepare(
      source: File,
      target: File,
      relatedFiles: List<File>,
    ): PatchProcessors? {
      return if (source.lastModified(relatedFiles) > target.lastModified()) {
        PatchProcessors.Fuzzy
      } else {
        null
      }
    }
  };
}


interface OverwriteMode {
  fun prepare(
    source: File,
    target: File,
    relatedFiles: List<File> = emptyList(),
  ): PatchProcessors?
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/embed/PlanHarness.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.cognitive.CognitiveSchemaStrategy
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.*
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import kotlin.random.Random

open class PlanHarness(
    val prompt: String,
    val cognitiveSettings: CognitiveModeConfig,
    val modelInstanceFn: (ApiChatModel, Session) -> ChatInterface = { model, session ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
            onUsage = { model, usage ->
                ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                    session = session,
                    defaultUser,
                    model,
                    usage
                )
            },
        )
    },
    val port: Int = Random.nextInt(1024, 65535),
    val serverless: Boolean = true,
    val openBrowser: Boolean = false,
    val timeoutMinutes: Long = 30,
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    var smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
//    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiFlash_25_Image_Generation,
    val workspace: File? = null,
) {

    private val harness = object : UnifiedHarness(
        port = port,
        openBrowser = openBrowser,
        serverless = serverless,
        modelInstanceFn = modelInstanceFn,
        fastModel = fastModel,
        smartModel = smartModel,
        imageModel = imageModel
    ) {
        override fun createTempDirectory(prefix: String) = createTempDirectory()
    }

    fun run() {
        try {
            harness.start()
            try {
                harness.runPlan(
                    prompt = prompt,
                    cognitiveSettings = cognitiveSettings,
                    timeoutMinutes = timeoutMinutes,
                    autoFix = !openBrowser,
                    workspace = workspace,
                    config = { session: Session, finalWorkspace: File ->
                        OrchestrationConfig.instanceFn = instanceFn(session)
                        newConfig(session, finalWorkspace)
                    }
                )
            } finally {
                harness.stop()
            }
        } catch (e: Exception){
            fix(e)
            throw RuntimeException(e)
        }
    }

    open fun newConfig(
        session: Session,
        finalWorkspace: File
    ): OrchestrationConfig = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = workspace?.absolutePath ?: finalWorkspace.absolutePath,
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = !openBrowser,
        cognitiveSettings = cognitiveSettings,
    )

    open fun createTempDirectory(): File = File(".").resolve("workspaces/${cognitiveSettings.type!!.name}/test-${now()}")
        .apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
    }

    companion object {
        fun configurePlatform(session: Session) {
            OrchestrationConfig.instanceFn = instanceFn(session)
            UnifiedHarness.configurePlatform()
        }

        fun instanceFn(session: Session): (ApiChatModel) -> ChatInterface = { model ->
            val api = model.findApi()
            val model =
                model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
            model.instance(
                key = api?.key
                    ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
                base = api.baseUrl,
                onUsage = { model, usage ->
                    ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                        session = session,
                        defaultUser,
                        model,
                        usage
                    )
                },
            )
        }

        @JvmStatic
        fun initDynamicEnums() {
            require(APIProvider.values().isNotEmpty())
            require(TaskType.values().isNotEmpty())
            require(ToolProvider.values().isNotEmpty())
            require(CognitiveModeType.values().isNotEmpty())
            require(CognitiveSchemaStrategy.values().isNotEmpty())
            require(CodeRuntimes.values().isNotEmpty())
        }

        fun trayIcon(): Pair<CountDownLatch, TrayIcon?> {
            val shutdownLatch = CountDownLatch(1)
            var trayIcon: TrayIcon? = null
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
                val g = image.createGraphics()
                g.color = Color.GREEN
                g.fillRect(0, 0, 16, 16)
                g.dispose()

                val popup = PopupMenu()
                val exitItem = MenuItem("Exit")
                exitItem.addActionListener { shutdownLatch.countDown() }
                popup.add(exitItem)

                trayIcon = TrayIcon(image, "Plan Test Harness", popup)
                trayIcon.isImageAutoSize = true
                try {
                    tray.add(trayIcon)
                } catch (e: AWTException) {
                    log.warn("TrayIcon could not be added.")
                }
            }

            val inputThread = Thread {
                try {
                    log.info("Press Enter to shut down...")
                    System.`in`.read()
                } catch (e: Exception) {
                    // ignore
                } finally {
                    shutdownLatch.countDown()
                }
            }
            inputThread.isDaemon = true
            inputThread.start()
            return Pair(shutdownLatch, trayIcon)
        }

        private val log = LoggerFactory.getLogger(PlanHarness::class.java)
        fun now(): String? = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        var fix : (Exception) -> Unit = { e ->
            log.error("Error during task execution", e)
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/embed/TaskHarness.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import java.io.File
import java.text.SimpleDateFormat
import kotlin.random.Random

open class TaskHarness<T : TaskExecutionConfig, U : TaskTypeConfig>(
    val taskType: TaskType<T, U>,
    val typeConfig: U,
    val executionConfig: T,
    val modelInstanceFn: (ApiChatModel, Session) -> ChatInterface = { model,session ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
            onUsage = { model, usage ->
                ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                    session,
                    UserSettingsManager.defaultUser,
                    model,
                    usage
                )
            },
        )
    },
    val port: Int = Random.nextInt(1024, 65535),
    val serverless: Boolean = true,
    val openBrowser: Boolean = false,
    val timeoutMinutes: Long = 30,
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiFlash_25_Image_Generation,
    val workspace: File? = null,
    val temperature: Double = 0.0,
) {
    val dataDir: File by lazy { createWorkspace() }
    fun run() {
        val harness = object : UnifiedHarness(
            port = port,
            openBrowser = openBrowser,
            serverless = serverless,
            modelInstanceFn = modelInstanceFn,
            fastModel = fastModel,
            smartModel = smartModel,
            imageModel = imageModel,
            temperature = temperature,
        ) {
            override fun createTempDirectory(prefix: String) = dataDir
        }
        try {
            harness.start()
            try {
                harness.runTask(
                    taskType = taskType,
                    typeConfig = typeConfig,
                    executionConfig = executionConfig,
                    timeoutMinutes = timeoutMinutes,
                    autoFix = !openBrowser,
                    workspace = workspace,
                    initSettings = { initSettings(it, workspace, !openBrowser, taskType, typeConfig, harness) }
                )
            } finally {
                harness.stop()
            }
        } catch (e: Exception) {
            fix(e)
            throw RuntimeException(e)
        }
    }

    open fun <T : TaskExecutionConfig, U : TaskTypeConfig> initSettings(
        session: Session,
        workspace: File?,
        autoFix: Boolean,
        taskType: TaskType<T, U>,
        typeConfig: U,
        harness: UnifiedHarness
    ) = harness.initSettings(
        session = session,
        workspace = workspace,
        autoFix = autoFix,
        taskType = taskType,
        typeConfig = typeConfig,
    )

    open fun createWorkspace(): File {
        val name = this.taskType.name
        val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        return File(".").resolve("workspaces/$name/test-$time").apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TaskHarness::class.java)
        var fix : (Exception) -> Unit = { e ->
            log.error("Error during task execution", e)
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/embed/UnifiedHarness.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.apps.UnifiedPlanApp
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.asApiChatModel
import com.simiacryptus.cognotik.util.PlanHarness.Companion.initDynamicEnums
import com.simiacryptus.cognotik.util.PlanHarness.Companion.now
import com.simiacryptus.cognotik.util.PlanHarness.Companion.trayIcon
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.session.ServerlessSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.eclipse.jetty.server.Server
import java.awt.Desktop
import java.awt.SystemTray
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

open class UnifiedHarness(
    val port: Int = Random.nextInt(1024, 65535),
    val serverless: Boolean = false,
    val openBrowser: Boolean = false,
    val captureMessages: Boolean = serverless,
    val redirectData: Boolean = serverless,
    val modelInstanceFn: (ApiChatModel, Session) -> ChatInterface = { model,session ->
        val api = model.findApi()
        val model =
            model.model ?: throw IllegalArgumentException("No model found for provider: ${model.provider?.name}")
        model.instance(
            key = api?.key ?: throw IllegalArgumentException("No API key found for provider: ${model.provider?.name}"),
            base = api.baseUrl,
            onUsage = { model, usage ->
                ApplicationServices.fileApplicationServices().usageManager.incrementUsage(
                    session = session,
                    UserSettingsManager.defaultUser,
                    model,
                    usage
                )
            },
        )
    },
    val fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    val imageModel: ChatModel = GeminiModels.GeminiPro_30_Image_Preview,
    val temperature: Double = 0.0,
) {
    private var jettyServer: Any? = null
    private var appServer: CognotikAppServer? = null

    open fun start() {
        if (serverless) {
            log.info("Starting in serverless mode - skipping Jetty startup")
            return
        }
        if (jettyServer == null) {
            appServer = CognotikAppServer(
                localName = "localhost",
                port = port
            )
            jettyServer = appServer?.start()
            log.info("Server started on port $port")
        }
    }

    open fun stop() {
        if (serverless) return
        try {
            (jettyServer as? Server)?.stop()
            jettyServer = null
            appServer = null
            log.info("Server stopped")
        } catch (e: Exception) {
            log.warn("Error stopping server", e)
        }
    }

    var session = Session.newGlobalID()
        private set

    fun resetSession() {
        session = Session.newGlobalID()
    }

    open fun runPlan(
        prompt: String,
        cognitiveSettings: CognitiveModeConfig,
        timeoutMinutes: Long = 30,
        autoFix: Boolean = !openBrowser,
        workspace: File? = null,
        config: (Session, File) -> OrchestrationConfig = { session: Session, finalWorkspace: File ->
            initSettings(
                session,
                finalWorkspace,
                autoFix,
                cognitiveSettings
            )
        }
    ) {
        val completionLatch = CountDownLatch(1)
        val session = this.session
        val planApp = object : UnifiedPlanApp(
            path = "/test",
            applicationName = "Plan Test App",
            showMenubar = false,
            useExpansionSyntax = true
        ) {
            override fun instance(model: ApiChatModel) = modelInstanceFn(model,session)

            override fun onComplete(mode: CognitiveMode<*>, task: SessionTask) {
                task.resolveUserFile("results.md")?.writeText(mode.contextData().joinToString("\n\n"))
                val usageManager = ApplicationServices.fileApplicationServices().usageManager
                task.resolveUserFile("usage.json")?.writeText(usageManager.getSessionUsageSummary(session).toJson())
                super.onComplete(mode, task)
            }

            override fun <T : Any> initSettings(session: Session): T {
                val orchestrationConfig = config(session, getRoot(workspace, session, cognitiveSettings.type?.name ?: "plan"))
                val settingsFile = getSettingsFile(session, defaultUser)
                val json = orchestrationConfig.toJson()
                settingsFile.writeText(json)
                @Suppress("UNCHECKED_CAST")
                return orchestrationConfig as T
            }

            override fun newSession(user: User, session: Session): SocketManager {
                if (serverless) {
                    val socketManager = ServerlessSocketManager(
                        session = session,
                        messageEvents = getMessageLog(workspace),
                        owner = user,
                        clazz = this.javaClass
                    )
                    // Manually trigger execution since we don't have a UI to send the first message
                    // We use a thread to simulate async execution
                    Thread {
                        try {
                            userMessage(session, user, prompt, socketManager)
                            completionLatch.countDown()
                        } catch (e: Throwable) {
                            log.error("Error running plan", e)
                            completionLatch.countDown() // Ensure we don't hang on error
                        }
                    }.start()
                    return socketManager
                } else {
                    val socketManager = super.newSession(user, session)
                    socketManager.pool.submit {
                        try {
                            Thread.sleep(1000)
                            userMessage(session, user, prompt, socketManager)
                            completionLatch.countDown()
                        } catch (e: Throwable) {
                            log.error("Error running plan", e)
                        }
                    }
                    return socketManager
                }
            }
        }

        if (!serverless) {
            SessionProxyServer.chats[session] = planApp
            ApplicationServer.appInfoMap[session] = AppInfoData(
                applicationName = "Plan Test App",
                inputCnt = 4,
                stickyInput = true,
                showMenubar = false
            )
        }

        try {
            planApp.initSettings<Any>(session)
            val socketManager = planApp.newSession(defaultUser, session)
            if (!serverless) {
                SessionProxyServer.agents[session] = socketManager
                val url = "http://localhost:$port/#$session"
                log.info("Plan available at $url")

                if (openBrowser) {
                    try {
                        Desktop.getDesktop().browse(URI(url))
                    } catch (e: Exception) {
                        log.warn("Failed to open browser", e)
                    }
                }
            }

            log.info("Waiting for plan completion (or timeout)...")
            if (!completionLatch.await(timeoutMinutes, TimeUnit.MINUTES)) {
                log.warn("Plan timed out")
            }

        } finally {
            handleBrowserShutdown(session)
        }
    }


    open fun <T : TaskExecutionConfig, U : TaskTypeConfig> runTask(
        taskType: TaskType<T, U>,
        typeConfig: U,
        executionConfig: T,
        timeoutMinutes: Long = 30,
        autoFix: Boolean = !openBrowser,
        workspace: File? = null,
        initSettings : (Session) -> OrchestrationConfig = { session ->
            initSettings(session, workspace, autoFix, taskType, typeConfig)
        }
    ) {
        val completionLatch = CountDownLatch(1)
        var error: Throwable? = null
        val session = this.session

        val singleTaskApp = object : SingleTaskApp(
            path = "/test",
            taskType = taskType,
            taskConfig = listOf(executionConfig),
            instanceFn = { model -> modelInstanceFn(model,session) },
        ) {
            override fun instance(model: ApiChatModel) = modelInstanceFn(model,session)

            override fun onTaskComplete(result: String, task: SessionTask) {
                log.info("Task completed successfully")
                task.resolveUserFile("result.md")?.writeText(result)
                val usageManager = ApplicationServices.fileApplicationServices().usageManager
                task.resolveUserFile("usage.json")?.writeText(usageManager.getSessionUsageSummary(session).toJson())
                completionLatch.countDown()
            }

            override fun onTaskError(e: Throwable) {
                log.error("Task failed", e)
                error = e
                completionLatch.countDown()
            }

            override fun <T : Any> initSettings(session: Session): T {
                val orchestrationConfig = initSettings(session)
                val json = orchestrationConfig.toJson()
                getSettingsFile(session, defaultUser).writeText(json)
                @Suppress("UNCHECKED_CAST")
                return orchestrationConfig as T
            }

            override fun newSession(user: User, session: Session): SocketManager {
                if (serverless) {
                    val socketManager = ServerlessSocketManager(
                        session = session,
                        messageEvents = getMessageLog(workspace),
                        owner = user,
                        clazz = this.javaClass
                    )
                    startSession(session, user, socketManager)
                    socketManager.resolveUserFile("task_${now()}.json")?.writeText(mapOf(
                        "typeConfig" to typeConfig,
                        "exeConfig" to executionConfig
                    ).toJson())
                    return socketManager
                } else {
                    return super.newSession(user, session)
                }
            }
        }

        if (!serverless) {
            SessionProxyServer.chats[session] = singleTaskApp
            ApplicationServer.appInfoMap[session] = AppInfoData(
                applicationName = "Single Task App",
                inputCnt = 0,
                stickyInput = false,
                showMenubar = false
            )
        }

        try {
            singleTaskApp.initSettings<Any>(session)
            val socketManager = singleTaskApp.newSession(defaultUser, session)
            
            if (!serverless) {
                SessionProxyServer.agents[session] = socketManager
                val url = "http://localhost:$port/#$session"
                log.info("Task available at $url")

                if (openBrowser) {
                    try {
                        Desktop.getDesktop().browse(URI(url))
                    } catch (e: Exception) {
                        log.warn("Failed to open browser", e)
                    }
                }
            }

            log.info("Waiting for task completion...")
            if (!completionLatch.await(timeoutMinutes, TimeUnit.MINUTES)) {
                throw RuntimeException("Task timed out after $timeoutMinutes minutes")
            }

            if (error != null) {
                throw RuntimeException("Task failed", error)
            }

        } finally {
            handleBrowserShutdown(session)
        }
    }
    open fun initSettings(
        session: Session,
        finalWorkspace: File,
        autoFix: Boolean,
        cognitiveSettings: CognitiveModeConfig
    ): OrchestrationConfig = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = finalWorkspace.absolutePath,
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = autoFix,
        temperature = temperature,
        cognitiveSettings = cognitiveSettings,
    )

    open fun <T : TaskExecutionConfig, U : TaskTypeConfig> initSettings(
        session: Session,
        workspace: File?,
        autoFix: Boolean,
        taskType: TaskType<T, U>,
        typeConfig: U
    ): OrchestrationConfig = OrchestrationConfig(
        sessionId = session.sessionId,
        workingDir = getRoot(workspace, session, taskType.name).absolutePath,
        taskSettings = mutableMapOf(
            typeConfig.name!! to typeConfig
        ),
        defaultFastModel = fastModel.asApiChatModel(),
        defaultSmartModel = smartModel.asApiChatModel(),
        defaultImageModel = imageModel.asApiChatModel(),
        autoFix = autoFix,
        temperature = temperature,
    )

    open fun getRoot(
        workspace: File?,
        session: Session,
        name: String
    ): File {
        val tempDirectory = createTempDirectory(name)
        log.info("Running task in workspace: ${tempDirectory.absolutePath}")
        DataStorage.sessionPaths[session] = tempDirectory
        if (redirectData) DataStorage.dataPaths[session] = tempDirectory
        return workspace ?: tempDirectory
    }

    private fun getMessageLog(workspace: File?): OutputStream? =
        if (captureMessages) workspace?.resolve(".logs/messageEvents_${time()}.log")?.apply {
            parentFile?.mkdirs()
        }?.outputStream()?.buffered() else null

    protected open fun handleBrowserShutdown(session: Session) {

        if (openBrowser && !serverless) {
            val pair = trayIcon()
            val shutdownLatch = pair.first
            val trayIcon = pair.second

            try {
                shutdownLatch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }

            if (trayIcon != null && SystemTray.isSupported()) {
                SystemTray.getSystemTray().remove(trayIcon)
            }
        }
    }

    protected open fun createTempDirectory(prefix: String): File {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss").format(System.currentTimeMillis())
        return File(".").resolve("workspaces/$prefix/test-$time").apply {
            mkdirs()
            log.debug("Created temp directory: ${this.absolutePath}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(UnifiedHarness::class.java)
        fun time(): String {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
            return sdf.format(System.currentTimeMillis())
        }


        @JvmStatic
        fun configurePlatform() {
            initDynamicEnums()
            ApplicationServices.authenticationManager = object : AuthenticationInterface {
                override fun getUser(accessToken: String?) = defaultUser
                override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
                override fun logout(accessToken: String, user: User) {}
            }
            ApplicationServices.authorizationManager = object : AuthorizationManager() {
                override fun isAuthorized(
                    applicationClass: Class<*>?,
                    user: User?,
                    operationType: AuthorizationInterface.OperationType
                ): Boolean = true
            }
        }
    }
}

fun ApiChatModel.findApi(): ApiData? {
    val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
    return (userSettings.apis.find { api -> api.provider?.name == provider?.name })
}

fun withHarness(
    root: File,
    testName: String,
    fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
    function: (UnifiedHarness) -> Unit
) {
    val workingDir = root.resolve("workspaces/${testName}/test-${PlanHarness.Companion.now()}")
    val harness = object : UnifiedHarness(fastModel = fastModel, smartModel = smartModel) {
        override fun createTempDirectory(prefix: String): File {
            return workingDir.apply { mkdirs() }
        }
    }
    harness.start()
    try {
        function(harness)
    } finally {
        harness.stop()
    }
}

```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the Cognotik project, reflecting the functionality and architecture described in the provided source files.

### README.md
```markdown
# Cognotik

Cognotik is an AI-driven development automation platform designed to synchronize documentation, automate code repairs, and execute structured AI tasks. It leverages advanced Large Language Models (LLMs), specifically the Gemini family, to bridge the gap between natural language specifications and source code.

## Core Features

### 1. Documentation Synchronization (`DocProcessor`)
The `DocProcessor` ensures that your documentation and code never diverge. It processes markdown files containing YAML frontmatter to drive automation:
- **`specifies`**: Automatically updates target source files based on the requirements defined in the markdown.
- **`transforms`**: Uses regex-based mapping to generate or update documentation from source files (e.g., auto-generating `README.md` from `.kt` files).
- **`documents`**: Updates documentation files by analyzing the current state of supporting source code.
- **`related`**: Provides additional context to the AI to ensure high-fidelity updates.

### 2. Automated Exception Fixing (`ExceptionFixer`)
The `ExceptionFixer` provides a "self-healing" capability for applications. When a `Throwable` is caught:
- It extracts the stack trace and identifies the relevant source files within the project.
- It passes the full error context to the AI.
- It automatically applies fuzzy patches to the source code to resolve the identified bug.

### 3. AI Execution Harnesses
Cognotik provides a robust infrastructure for running AI tasks in various environments:
- **`UnifiedHarness`**: The foundation for both serverless (CLI) and interactive (Web UI) execution.
- **`PlanHarness`**: Designed for complex, multi-step cognitive orchestrations.
- **`TaskHarness`**: Optimized for single, well-defined tasks like file modifications or code generation.
- **Interactive Mode**: Supports a Jetty-based web interface with real-time message logging and browser integration.

### 4. Intelligent File Management
- **`FileGenerator`**: A utility for bulk generation of code or documentation based on custom prompts and related file context.
- **`OverwriteModes`**: Configurable strategies for file updates, including:
    - `PatchToUpdate`: Uses fuzzy patching to merge AI suggestions into existing files.
    - `OverwriteToUpdate`: Replaces files only if the source or related context is newer.
    - `SkipExisting`: Preserves existing manual changes.

## Configuration

Automation is primarily controlled via YAML frontmatter in markdown files. Example:

```yaml
---
specifies: 
  - "src/main/kotlin/com/example/MyService.kt"
related:
  - "docs/api_standards.md"
transforms:
  - "src/main/kotlin/(.+)/([^/]+)\\.kt -> docs/api/$1/$2.md"
---
```

## Technical Stack

- **Language**: Kotlin
- **AI Integration**: Optimized for Google Gemini (Flash 1.5/2.0 and Pro models).
- **Patching**: Supports fuzzy patching and full replacement via `PatchProcessors`.
- **Platform**: Extensible architecture with support for custom API providers and tool definitions.

## Getting Started

Cognotik can be integrated into development workflows as a library or run as a standalone server to provide an interactive AI development environment. It requires API keys for the supported LLM providers (configured via `UserSettings`).
```

### Summary of Changes
- **Created/Updated `README.md`**: Synthesized a comprehensive project overview based on the provided Kotlin source files.
- **Component Documentation**: Detailed the roles of `DocProcessor`, `ExceptionFixer`, and the various `Harness` classes.
- **Feature Highlights**: Included information on the patching system, overwrite modes, and the frontmatter-driven configuration pattern.
- **Technical Context**: Specified the reliance on Kotlin and Gemini models as identified in the code.

This file now serves as the primary entry point for understanding the Cognotik automation framework.
</details>

                - <a href='fileIndex/G-20260120-RPFC/README.md'>README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/README.md'>README.md</a> Updated
