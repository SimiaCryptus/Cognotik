package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.FileGenerator.OverwriteMode
import com.simiacryptus.cognotik.util.FileGenerator.OverwriteModes
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
open class DocProcessor {

    data class DocSpec(
        val docFile: File,
        val specifies: List<String>,
        val transforms: List<TransformSpec>,
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
     * Represents a matched transformation with resolved source and destination files.
     */
    data class TransformMatch(
        val sourceFile: File,
        val destinationFile: File,
        val spec: DocSpec
    )

    fun run(
        root: File,
        docsFolder: File,
        overwriteMode: OverwriteMode = OverwriteModes.PatchToUpdate,
        additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
        concurrencyLimit: Int = 4,
        fastModel: ChatModel = GeminiModels.GeminiFlash_30_Preview,
        smartModel: ChatModel = GeminiModels.GeminiFlash_30_Preview
    ) {
        val concurrencyProcessor = FixedConcurrencyProcessor(
            pool = Executors.newCachedThreadPool(),
            concurrencyLimit = concurrencyLimit
        )

        // Find all markdown files in the docs folder
        val markdownFiles = docsFolder.listFilesRecursively()
            .filter { it.isFile && it.extension in setOf("md", "markdown") }

        log.info("Found ${markdownFiles.size} markdown files in ${docsFolder.absolutePath}")

        // Parse each markdown file for frontmatter
        val docSpecs = markdownFiles.mapNotNull { mdFile ->
            parseMarkdownWithFrontmatter(mdFile)
        }

        log.info("Found ${docSpecs.size} markdown files with 'specifies' frontmatter")

        // Expand all doc specs to their target files and group by target file (for specifies)
        val fileToSpecs = docSpecs
            .filter { it.specifies.isNotEmpty() }
            .flatMap { spec ->
                val baseDir = spec.docFile.parentFile
                spec.specifies.flatMap { pattern ->
                    val targetFiles = expandGlobPattern(baseDir, pattern)
                    log.info("Doc ${spec.docFile.name} specifies pattern '$pattern' -> ${targetFiles.size} files")
                    targetFiles.map { targetFile -> targetFile.canonicalFile to spec }
                }
            }.groupBy({ it.first.absolutePath }, { it.second })
        log.info("Grouped into ${fileToSpecs.size} unique target files from 'specifies'")

        // Expand all transform specs
        val transformMatches: List<TransformMatch> = docSpecs
            .filter { it.transforms.isNotEmpty() }
            .flatMap { spec ->
                spec.transforms.flatMap { transform ->
                    expandTransformPattern(root, transform, spec)
                }
            }
        log.info("Found ${transformMatches.size} transform matches")
        // Group transform matches by destination file
        val fileToTransforms = transformMatches.groupBy { it.destinationFile.absolutePath }
        // Combine all target files from both specifies and transforms
        val allTargetFiles = (fileToSpecs.keys + fileToTransforms.keys).distinct()
            .associateWith { Pair(fileToSpecs[it], fileToTransforms[it]) }
        log.info("Total unique target files: ${allTargetFiles.size}")


        withHarness(root, javaClass.simpleName, fastModel, smartModel) { harness ->
            // Process all target files (combining specifies and transforms)
            allTargetFiles.keys.map { targetFile ->
                val specs = fileToSpecs[targetFile] ?: emptyList()
                val transforms = fileToTransforms[targetFile] ?: emptyList()

                val targetFile = File(targetFile)
                val relativeTarget = targetFile.relativeTo(root.absoluteFile)

                // Collect all related files from all specs and transforms
                val allRelatedFiles = (specs.flatMap { spec ->
                    listOf(spec.docFile) + additionalContext(spec, targetFile).map { File(it) }
                } + transforms.flatMap { match ->
                    listOf(match.spec.docFile, match.sourceFile) + additionalContext(match.spec, targetFile).map {
                        File(
                            it
                        )
                    }
                }).distinct()

                // Determine the primary source file for overwrite mode preparation
                val primarySource = when {
                    transforms.isNotEmpty() -> transforms.first().sourceFile
                    specs.isNotEmpty() -> specs.first().docFile
                    else -> return@map null
                }

                overwriteMode.prepare(
                    source = primarySource,
                    target = targetFile,
                    relatedFiles = allRelatedFiles
                )?.let { patchProcessor ->
                    concurrencyProcessor.submit {
                        try {
                            val specNames = specs.map { it.docFile.name }
                            val transformNames = transforms.map { "${it.spec.docFile.name}(${it.sourceFile.name})" }
                            val allNames = (specNames + transformNames).joinToString(", ")
                            log.info("Processing ${relativeTarget} based on ${specs.size} spec(s) and ${transforms.size} transform(s): $allNames")

                            // Build combined task description
                            val description = buildCombinedTaskDescription(specs, transforms, targetFile)

                            // Collect all related file paths from specs and transforms
                            val allRelatedFilePaths = (specs.flatMap { spec ->
                                listOf(spec.docFile.relativeTo(root.absoluteFile).toString()) +
                                    additionalContext(spec, targetFile)
                            } + transforms.flatMap { match ->
                                listOf(
                                    match.spec.docFile.relativeTo(root.absoluteFile).toString(),
                                    match.sourceFile.relativeTo(root.absoluteFile).toString()
                                ) + additionalContext(match.spec, targetFile)
                            }).distinct()

                            harness.runTask(
                                taskType = FileModification,
                                typeConfig = TaskTypeConfig(task_type = FileModification.name),
                                executionConfig = FileModificationTaskExecutionConfigData(
                                    files = listOf(relativeTarget.toString()),
                                    related_files = allRelatedFilePaths,
                                    task_description = description,
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
                            log.error("Error processing ${relativeTarget}", e)
                        }
                    }
                }
            }.filterNotNull().toTypedArray().forEach { it.get() }
        }
    }

    /**
     * Build a combined task description from multiple specs and transforms
     */
    open fun buildCombinedTaskDescription(
        specs: List<DocSpec>,
        transforms: List<TransformMatch>,
        target: File
    ): String {
        val parts = mutableListOf<String>()
        parts.add("Update the file ${target.name} based on the included documentation and specifications")
        parts.add("\nEnsure the file conforms to all the patterns, standards, and requirements described.")
        parts.add("If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.")
        return parts.joinToString("\n")
    }


    /**
     * Parse a markdown file and extract frontmatter with 'specifies' key
     */
    private fun parseMarkdownWithFrontmatter(file: File): DocSpec? {
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
        val transforms = parseTransforms(frontmatter)

        // Return null if neither specifies nor transforms are present
        if (specifies.isEmpty() && transforms.isEmpty()) {
            return null
        }

        return DocSpec(
            docFile = file,
            specifies = specifies,
            transforms = transforms,
            content = bodyContent,
            frontmatter = frontmatter
        )
    }

    /**
     * Parse YAML frontmatter into a map (supports simple values and lists)
     */
    private fun parseFrontmatter(text: String): Map<String, Any> {
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
                    while (i < lines.size && lines[i].startsWith("  - ")) {
                        listItems.add(lines[i].removePrefix("  - ").trim())
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
     * Parse 'specifies' from frontmatter (supports single value or list)
     */
    private fun parseSpecifies(frontmatter: Map<String, Any>): List<String> {
        return when (val value = frontmatter["specifies"]) {
            is String -> listOf(value)
            is List<*> -> value.filterIsInstance<String>()
            else -> emptyList()
        }
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
    private fun parseTransforms(frontmatter: Map<String, Any>): List<TransformSpec> {
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
     * Expand a single transform pattern to file matches
     */
    private fun expandTransformPattern(
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

    /**
     * Expand a glob pattern relative to a base directory
     */
    private fun expandGlobPattern(baseDir: File, pattern: String): List<File> {
        // If the pattern contains ** or other complex globs, handle differently
        return if (pattern.contains("**")) {
            expandRecursiveGlob(baseDir, pattern)
        } else {
            expandSimpleGlob(baseDir, pattern)
        }
    }

    /**
     * Expand a simple glob pattern (e.g., *.kt) in a specific directory
     */
    private fun expandSimpleGlob(baseDir: File, pattern: String): List<File> {
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

    private fun expandRecursiveGlob(baseDir: File, pattern: String): List<File> {
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

    companion object {
        private val log = LoggerFactory.getLogger(DocProcessor::class.java)
    }
}