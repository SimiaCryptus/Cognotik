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

/**
 * DocProcessor processes markdown documentation files that specify target files via frontmatter.
 *
 * Each markdown file can contain a YAML frontmatter block with a 'specifies' key that contains
 * a glob pattern. Files matching this pattern will be updated based on the markdown content.
 */
open class DocProcessor {

    data class DocSpec(
        val docFile: File,
        val specifies: String,
        val content: String,
        val frontmatter: Map<String, String>
    )

    fun run(
        root: File,
        docsFolder: File,
        overwriteMode: OverwriteMode = OverwriteModes.PatchExisting,
        additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
        taskDescription: (DocSpec, File) -> String = { spec, target ->
            """
            |Update the file ${target.name} based on the following documentation/specification:
            |
            |${spec.content}
            |
            |Ensure the file conforms to the patterns, standards, and requirements described in the documentation.
            |If the file already exists, update it to match the specification while preserving existing functionality where appropriate.
            """.trimMargin()
        },
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

        withHarness(root, javaClass.simpleName, fastModel, smartModel) { harness ->
            docSpecs.flatMap { spec ->
                // Resolve the glob pattern relative to the markdown file's directory
                val baseDir = spec.docFile.parentFile
                val targetFiles = expandGlobPattern(root, baseDir, spec.specifies)

                log.info("Doc ${spec.docFile.name} specifies pattern '${spec.specifies}' -> ${targetFiles.size} files")

                targetFiles.map { targetFile ->
                    val relativeTarget = targetFile.relativeTo(root.absoluteFile)
                    val relatedFiles = listOf(spec.docFile) + additionalContext(spec, targetFile).map { File(it) }

                    overwriteMode.prepare(
                        source = spec.docFile,
                        target = targetFile,
                        relatedFiles = relatedFiles
                    )?.let { patchProcessor ->
                        concurrencyProcessor.submit {
                            try {
                                log.info("Processing ${relativeTarget} based on ${spec.docFile.name}")
                                harness.runTask(
                                    taskType = FileModification,
                                    typeConfig = TaskTypeConfig(task_type = FileModification.name),
                                    executionConfig = FileModificationTaskExecutionConfigData(
                                        files = listOf(relativeTarget.toString()),
                                        related_files = listOf(spec.docFile.relativeTo(root.absoluteFile).toString()) +
                                                additionalContext(spec, targetFile),
                                        task_description = taskDescription(spec, targetFile),
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
                }
            }.filterNotNull().toTypedArray().forEach { it.get() }
        }
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

        // Parse simple YAML frontmatter (key: value pairs)
        val frontmatter = parseFrontmatter(frontmatterText)

        val specifies = frontmatter["specifies"] ?: return null

        return DocSpec(
            docFile = file,
            specifies = specifies,
            content = bodyContent,
            frontmatter = frontmatter
        )
    }

    /**
     * Parse simple YAML frontmatter into a map
     */
    private fun parseFrontmatter(text: String): Map<String, String> {
        return text.lines()
            .filter { it.contains(":") }
            .associate { line ->
                val colonIndex = line.indexOf(":")
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                key to value
            }
    }

    /**
     * Expand a glob pattern relative to a base directory
     */
    private fun expandGlobPattern(root: File, baseDir: File, pattern: String): List<File> {
        // Resolve the pattern relative to the base directory
        val resolvedBase = baseDir.resolve(pattern).parentFile ?: baseDir
        val normalizedBase = resolvedBase.canonicalFile

        // Extract the glob part (filename pattern)
        val globPattern = File(pattern).name
        val pathPattern = File(pattern).parent ?: ""

        // If the pattern contains ** or other complex globs, handle differently
        return if (pattern.contains("**")) {
            expandRecursiveGlob(root, baseDir, pattern)
        } else {
            expandSimpleGlob(normalizedBase, globPattern)
        }
    }

    /**
     * Expand a simple glob pattern (e.g., *.kt) in a specific directory
     */
    private fun expandSimpleGlob(directory: File, pattern: String): List<File> {
        if (!directory.exists() || !directory.isDirectory) {
            log.warn("Directory does not exist: ${directory.absolutePath}")
            return emptyList()
        }

        val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

        return directory.listFiles()
            ?.filter { it.isFile && matcher.matches(it.toPath().fileName) }
            ?: emptyList()
    }

    private fun expandRecursiveGlob(root: File, baseDir: File, pattern: String): List<File> {
        val resolvedBase = baseDir.resolve(pattern.substringBefore("**")).canonicalFile
        val remainingPattern = pattern.substringAfter("**").removePrefix("/").removePrefix("\\")

        if (!resolvedBase.exists()) {
            log.warn("Base directory does not exist: ${resolvedBase.absolutePath}")
            return emptyList()
        }

        val matcher: PathMatcher = FileSystems.getDefault().getPathMatcher("glob:$remainingPattern")

        return resolvedBase.listFilesRecursively()
            .filter { it.isFile && matcher.matches(it.toPath().fileName) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DocProcessor::class.java)
    }
}