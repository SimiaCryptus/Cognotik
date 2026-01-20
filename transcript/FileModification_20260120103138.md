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

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/AbstractFileTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.FileTaskExecutionConfig
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.exists

abstract class AbstractFileTask<T : FileTaskExecutionConfig>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(orchestrationConfig, planTask) {

    protected val codeFiles = mutableMapOf<Path, String>()

    abstract class FileTaskExecutionConfig(
        task_type: String? = null,
        task_description: String? = null,
        @Description("REQUIRED: The files to be generated as output for the task (relative paths)") var files: List<String>? = null,
        @Description("Additional files used to inform the change, including relevant files created by previous tasks") var related_files: List<String>? = null,
        @Description("Whether to extract text content from non-text files (PDF, HTML, etc.)") var extractContent: Boolean = false,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = task_type,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    protected fun getInputFileCode(
        fn: (File) -> (CharSequence?) = ::formatFileForLLM
    ) = getInputFiles()
        .mapNotNull { fn(it) }
        .joinToString("\n\n")

    protected fun getInputFiles(): List<File> =
        ((executionConfig?.related_files ?: listOf()) + (executionConfig?.files ?: listOf()))
            .flatMap { pattern: String ->
                if (root.resolve(pattern).exists()) {
                    return@flatMap listOf(root.resolve(pattern).toFile())
                }
                val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                (FileSelectionUtils.filteredWalk(root.toFile()) {
                    //path -> matcher.matches(root.relativize(path.toPath())) && !FileSelectionUtils.isLLMIgnored(path.toPath())
                    when {
                        FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                        it.isDirectory -> true
                        !matcher.matches(root.relativize(it.toPath())) -> false
                        else -> true
                    }
                })
            }.filter { file ->
                file.isFile && file.exists() && !isIgnored(file)
            }
            .distinct()
            .filterNotNull()
            .sortedBy { it }


    protected open fun isIgnored(file: File): Boolean = when (file.extension) {
        /* Common Binary Files */
        "class", "jar", "exe", "dll", "bin", "img", "iso", "zip", "tar", "gz", "7z" -> true
        /* Common Image and Media */
        "png", "jpg", "jpeg", "gif", "bmp", "tiff", "mp4", "mp3", "avi", "mov", "wmv", "flv", "mkv" -> true
        else -> false
    }

    /**
     * Formats the content of a file for inclusion in the LLM context.
     * Uses Markdown headers and code blocks.
     */
    protected open fun formatFileForLLM(relativePath: File): CharSequence? = try {
        val file = root.toFile().resolve(relativePath)
        val content = if (executionConfig?.extractContent == true && !isTextFile(file)) {
            extractDocumentContent(file)
        } else {
            codeFiles[file.toPath()] ?: file.readText()
        }
        "# $relativePath\n\n$TRIPLE_TILDE\n$content\n$TRIPLE_TILDE"
    } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
    }

    private fun isTextFile(file: File): Boolean {
        val textExtensions = setOf(
            "txt",
            "md",
            "kt",
            "java",
            "js",
            "ts",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "css",
            "html",
            "xml",
            "json",
            "yaml",
            "yml",
            "sh",
            "bat",
            "ps1",
            "sql",
            "properties",
            "gradle",
            "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }


    companion object {
        private val log = LoggerFactory.getLogger(AbstractFileTask::class.java)
        const val TRIPLE_TILDE = "```"

        fun extractDocumentContent(file: File): String {
            return try {
                file.getDocumentReader().use { reader ->
                    when (reader) {
                        is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                        else -> {
                            val text = reader.getText()
                            when {
                                text.isAsciiPrintable() -> text
                                text.length > (1024 * 512) -> ""
                                else -> text
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
                try {
                    file.readText()
                } catch (e2: Exception) {
                    "Error reading file: ${e2.message}"
                }
            }
        }
    }
}

fun String.isAsciiPrintable(): Boolean {
    return this.all { it.code in 32..126 || it == '\n' || it == '\r' || it == '\t' }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/DataIngestTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.util.*
import java.util.regex.Pattern

class DataIngestTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DataIngestTaskExecutionConfigData?
) : AbstractTask<DataIngestTask.DataIngestTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    data class RegexSuggestion(
        @Description("Suggested Regex Pattern - Java compatible; use named capture groups (?<name>...) without underscores")
        val regex: String = "",
        val fields: List<String> = emptyList(),
        val explanation: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (regex.isBlank()) return "Regex cannot be blank"
            try {
                Pattern.compile(regex)
            } catch (e: Exception) {
                return "Invalid Regex: ${e.message}"
            }
            return null
        }
    }

    data class PatternRegistryItem(
        val id: String,
        val regex: String,
        val fields: List<String>,
        var matchCount: Int = 0
    )

    class DataIngestTaskExecutionConfigData(
        @Description("File patterns to ingest (e.g. **/*.log)")
        var input_files: List<String>? = null,
        @Description("Number of lines to sample for pattern discovery")
        var sample_size: Int = 1000,
        @Description("Maximum number of discovery iterations")
        var max_iterations: Int = 10,
        @Description("Stop discovery when this percentage of the sample is covered (0.0 - 1.0)")
        var coverage_threshold: Double = 0.95,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending
    ) : TaskExecutionConfig(
        task_type = DataIngest.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (input_files.isNullOrEmpty()) return "Input files must be specified"
            return super.validate()
        }
    }

    override fun promptSegment(): String {
        return """
DataIngest - Iteratively parse unstructured logs/text into structured data
  ** Specify input_files patterns (glob) to process
  ** Iteratively discovers Regex patterns using LLM for residual data
  ** Generates structured artifacts: data.jsonl, data.csv, patterns.json, and index.csv
  ** Efficiently handles large files via streaming extraction
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val ui = task.ui
        val tabs = TabbedDisplay(task)
        val logTask = task.newTask()
        val transcript = task.transcript()
        tabs["Log"] = logTask.placeholder

        fun log(msg: String) {
            logTask.add(msg.renderMarkdown())
            logTask.update()
        }

        task.ui.pool.submit {
            try {
                log.info("DataIngestTask started for patterns: ${executionConfig?.input_files}")
                transcript?.write("## Data Ingest Task Started\n".toByteArray())
                val priorContext = getPriorCode(agent.executionState)
                transcript?.write("### Prior Context\n<details><summary>Upstream Data</summary>\n\n$priorContext\n</details>\n".toByteArray())

                // 1. Identify Files
            val files = resolveFiles(executionConfig?.input_files ?: emptyList())
            if (files.isEmpty()) {
                throw RuntimeException("No files found matching patterns: ${executionConfig?.input_files}")
            }
            log("Found ${files.size} files to process.")

            // 2. Sampling Phase
            logTask.header("Phase 1: Sampling", level = 2)
            logTask.update()
            val sampleLines = mutableListOf<String>()

            for (file in files) {
                if (sampleLines.size >= (executionConfig?.sample_size ?: 1000)) break
                file.useLines { lines ->
                    for (line in lines) {
                        if (line.isNotBlank()) {
                            sampleLines.add(line)
                        }
                        if (sampleLines.size >= (executionConfig?.sample_size ?: 1000)) break
                    }
                }
            }
            log("Loaded ${sampleLines.size} sample lines.")

            // 3. Discovery Loop
            logTask.header("Phase 2: Pattern Discovery", level = 2)
            logTask.update()
            val registry = mutableListOf<PatternRegistryItem>()
            val unparsedSample = sampleLines.toMutableList()
            val parsingChatter = defaultFast.getChildClient(task)
            val defaultChatter = defaultSmart.getChildClient(task)

            val discoveryTask = task.newTask()
            tabs["Discovery"] = discoveryTask.placeholder
            val statusBuffer = discoveryTask.add("Initializing discovery...")


            var iteration = 0
            while (iteration < (executionConfig?.max_iterations ?: 10)) {
                val coverage = 1.0 - (unparsedSample.size.toDouble() / sampleLines.size.toDouble())
                statusBuffer?.setLength(0)
                statusBuffer?.append(
                    "**Iteration ${iteration + 1}** | Coverage: ${(coverage * 100).toInt()}% | Residuals: ${unparsedSample.size}".renderMarkdown()
                )
                task.update()

                if (coverage >= (executionConfig?.coverage_threshold ?: 0.95) || unparsedSample.isEmpty()) {
                    log("Coverage threshold reached.")
                    break
                }

                // Take a chunk of residuals to analyze
                val residualsToAnalyze = unparsedSample.take(20)
                val knownFields = registry.flatMap { it.fields }.distinct().sorted()

                val prompt = """
                    You are a Data Engineering expert. Your goal is to write a Java/Kotlin compatible Regular Expression (Regex) to parse the following log lines.
                    ${if (!executionConfig?.task_description.isNullOrBlank()) "\n**Context:** ${executionConfig?.task_description}\n" else ""}

                    **Requirements:**
                    1. Use named capture groups `(?<name>...)` for all extractable fields. Note: Java regex group names must be alphanumeric only (no underscores).
                    2. Prefer specific character classes (e.g., `[^\]]+`) over greedy `.*`.
                    3. Anchor the regex with `^` and `$` if the pattern covers the whole line.
                    4. The regex must match the provided lines.
                    ${
                    if (knownFields.isNotEmpty()) "5. Reuse existing field names where appropriate: ${
                        knownFields.joinToString(
                            ", "
                        )
                    }" else ""
                }

                    **Residual Log Lines:**
                    ${residualsToAnalyze.joinToString("\n")}
                """.trimIndent()

                val agent = ParsedAgent(
                    resultClass = RegexSuggestion::class.java,
                    prompt = prompt,
                    model = defaultChatter,
                    parsingChatter = parsingChatter
                )

                val suggestion = agent.answer(listOf(prompt)).obj

                // Validate
                try {
                    val compiled = Pattern.compile(suggestion.regex)
                    var matchedCount = 0
                    val it = unparsedSample.iterator()
                    while (it.hasNext()) {
                        val line = it.next()
                        if (compiled.matcher(line).find()) {
                            matchedCount++
                            it.remove()
                        }
                    }

                    if (matchedCount > 0) {
                        val id = UUID.randomUUID().toString()
                        registry.add(PatternRegistryItem(id, suggestion.regex, suggestion.fields))
                        log("✅ Discovered Pattern ($matchedCount matches in sample): `${suggestion.regex}`")
                    } else {
                        log("⚠️ Generated regex matched 0 lines in residual sample. Retrying...")
                    }
                } catch (e: Exception) {
                    log("❌ Invalid Regex generated: ${e.message}")
                }
                iteration++
            }
            statusBuffer?.setLength(0)
                statusBuffer?.append("Discovery Complete".renderMarkdown())
                task.update()

            // 4. Bulk Extraction
                log("### Phase 3: Bulk Extraction")
            logTask.update()
            val (dataFileLink, dataFile) = task.createFile("data.jsonl")
            val (dataCsvLink, dataCsvFile) = task.createFile("data.csv")
            val (indexFileLink, indexFile) = task.createFile("index.csv")
            val (patternsFileLink, patternsFile) = task.createFile("patterns.json")

            val mapper = jacksonObjectMapper()

            // Save Patterns
            patternsFile?.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registry))
                transcript?.write(
                    """
                ### Discovered Patterns
                <details>
                <summary>Pattern Registry JSON</summary>
                ```json
                ${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(registry)}
                ```
                </details>
            """.trimIndent().toByteArray()
                )

            var totalExtracted = 0
            var totalBytes = 0L
            val allFields = (listOf("timestamp") + registry.flatMap { it.fields }).distinct().sorted()

            dataFile?.outputStream()?.buffered()?.use { dataOut ->


                dataCsvFile?.outputStream()?.buffered()?.use { csvOut ->
                    csvOut.write(allFields.joinToString(",").toByteArray(StandardCharsets.UTF_8))
                    csvOut.write("\n".toByteArray(StandardCharsets.UTF_8))
                    indexFile?.outputStream()?.buffered()?.use { indexOut ->
                        // Write CSV Header
                        indexOut.write("message_id,pattern_id,source_file,source_offset_start,source_offset_end,data_offset\n".toByteArray())

                        files.forEach { file ->
                            var fileOffset = 0L
                            file.forEachLine { line ->
                                val lineBytes = line.toByteArray(StandardCharsets.UTF_8)
                                val lineLength = lineBytes.size + 1 // +1 for newline approximation

                                // Try patterns
                                for (pattern in registry) {
                                    val matcher = Pattern.compile(pattern.regex).matcher(line)
                                    if (matcher.find()) {
                                        val recordId = UUID.randomUUID().toString()
                                        val dataMap = mutableMapOf<String, Any>(
                                            "timestamp" to LocalDateTime.now().toString()
                                        ) // Default

                                        pattern.fields.forEach { field ->
                                            try {
                                                dataMap[field] = matcher.group(field)
                                            } catch (e: Exception) {
                                                // Group might be optional and missing
                                            }
                                        }

                                        val json = mapper.writeValueAsString(dataMap)
                                        val jsonBytes = (json + "\n").toByteArray(StandardCharsets.UTF_8)

                                        // Write Data
                                        dataOut.write(jsonBytes)

                                        // Write CSV
                                        val csvRow = allFields.joinToString(",") { field ->
                                            val value = dataMap[field]?.toString() ?: ""
                                            if (value.any { it in ",\"\n" }) {
                                                "\"" + value.replace("\"", "\"\"") + "\""
                                            } else {
                                                value
                                            }
                                        }
                                        csvOut.write((csvRow + "\n").toByteArray(StandardCharsets.UTF_8))

                                        // Write Index
                                        // message_id, pattern_id, source_file, src_start, src_end, data_offset
                                        val indexRow =
                                            "$recordId,${pattern.id},${file.name},$fileOffset,${fileOffset + lineBytes.size},$totalBytes\n"
                                        indexOut.write(indexRow.toByteArray(StandardCharsets.UTF_8))

                                        totalBytes += jsonBytes.size
                                        totalExtracted++
                                        pattern.matchCount++
                                        break // Stop at first match
                                    }
                                }
                                fileOffset += lineLength
                            }
                        }
                    }
                }
            }
                val summaryTask = task.newTask()
                tabs["Summary"] = summaryTask.placeholder
            summaryTask.header("Ingestion Summary")
                summaryTask.add("**Total Extracted Records:** $totalExtracted".renderMarkdown())
                summaryTask.add("**Patterns Discovered:** ${registry.size}".renderMarkdown())
            summaryTask.complete()
                log.info("DataIngestTask completed: $totalExtracted records processed.")


            // Final Report
            val summary = buildString {
                appendLine("\n\n# Data Ingestion Complete")
                appendLine("**Total Extracted Records:** $totalExtracted")
                appendLine("**Patterns Discovered:** ${registry.size}")
                appendLine()
                appendLine("## Artifacts")
                appendLine("- [Structured Data (JSONL)]($dataFileLink)")
                appendLine("- [Structured Data (CSV)]($dataCsvLink)")
                appendLine("- [Search Index (CSV)]($indexFileLink)")
                appendLine("- [Pattern Registry (JSON)]($patternsFileLink)")
                appendLine()
                appendLine("## Pattern Stats")
                registry.forEach {
                    appendLine("- **${it.fields}**: ${it.matchCount} matches")
                    appendLine("  - Regex: `${it.regex}`")
                }
            }
            logTask.complete()
            discoveryTask.complete()

                task.complete()
                resultFn("Successfully ingested $totalExtracted records from ${files.size} files using ${registry.size} patterns. Artifacts: data.jsonl, data.csv, patterns.json, index.csv.")

            } catch (e: Exception) {
                task.error(e)
                log.error("DataIngestTask failed: ${e.message}", e)
                transcript?.write(
                    """
                    ## Error
                    <details>
                    <summary>Stack Trace</summary>
                    
                    ```
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
                )
                resultFn("Error during data ingestion: ${e.message}")
            } finally {
                transcript?.close()
            }
        }
    }

    private fun resolveFiles(patterns: List<String>): List<File> {
        return patterns.flatMap { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            FileSelectionUtils.filteredWalk(root.toFile()) {
                matcher.matches(root.relativize(it.toPath())) || it.isDirectory
            }
        }.filter {
            it.exists() && it.isFile
        }.distinct()
    }

    companion object {
        private val log = LoggerFactory.getLogger(DataIngestTask::class.java)
        @JvmStatic val DataIngest = TaskType(
            name = "DataIngest",
            category = "File",
            taskClass = DataIngestTask::class.java,
            executionConfigClass = DataIngestTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Iteratively parse unstructured logs into structured data",
            tooltipHtml = """
                        Automates the creation of regex parsers for log files.
                        <ul>
                          <li>Samples data to discover patterns using LLM</li>
                          <li>Iteratively targets residual (unparsed) data</li>
                          <li>Streams large files to produce JSONL output</li>
                          <li>Generates an index linking data back to source lines</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/DiscussionTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class DiscussionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DiscussionTaskExecutionConfigData?
) : AbstractTask<DiscussionTask.DiscussionTaskExecutionConfigData, DiscussionTask.DiscussionTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    protected val codeFiles = mutableMapOf<Path, String>()

    class DiscussionTaskTypeConfig(
        task_type: String? = Discussion.name,
        name: String? = null
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name
    ), ValidatedObject

    class DiscussionTaskExecutionConfigData(
        @Description("The specific questions or topics to be addressed in the inquiry")
        var inquiry_questions: List<String>? = null,
        @Description("The goal or purpose of the inquiry")
        var inquiry_goal: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("A description of the task")
        task_description: String? = null,
        @Description("List of task IDs this task depends on")
        task_dependencies: List<String>? = null,
        @Description("The current state of the task")
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = Discussion.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject

    override fun promptSegment() = (if (!orchestrationConfig.autoFix) """
  Discussion - Directly answer questions or provide insights using the LLM. Reading files is optional and can be included if relevant to the inquiry.
    * Specify the questions and the goal of the inquiry.
    * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
    * User response/feedback and iteration are supported.
  """ else """
  Discussion - Directly answer questions or provide a report using the LLM. Reading files is optional and can be included if relevant to the inquiry.
    * Specify the questions and the goal of the inquiry.
    * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
  """)

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


        val transcript = task.transcript()
        task.ui.pool.submit {
            try {
                log.info("Starting DiscussionTask: ${executionConfig?.task_description ?: "Unnamed"}")
                task.add("### Initializing Discussion\nGathering context and preparing inquiry...".renderMarkdown())

                val toInput = { it: String ->
                    messages + listOf(
                        getInputFileCode(),
                        it,
                    ).filter { it.isNotBlank() }
                }

                val taskConfig: DiscussionTaskExecutionConfigData? = this.executionConfig
                val typeConfig = typeConfig ?: throw RuntimeException("Task configuration is missing")
                val insightActor = ChatAgent(
                    name = "Insight",
                    prompt = """
                        Create code for a new file that fulfills the specified requirements and context.
                        Given a detailed user request, break it down into smaller, actionable tasks suitable for software development.
                        Compile comprehensive information and insights on the specified topic.
                        Provide a comprehensive overview, including key concepts, relevant technologies, best practices, and any potential challenges or considerations.
                        Ensure the information is accurate, up-to-date, and well-organized to facilitate easy understanding.
                        """.trimIndent(),
                    model = (typeConfig.model?.let<ApiChatModel, ChatInterface> { this.orchestrationConfig.instance(it) }
                        ?: defaultSmart).getChildClient(task),
                    temperature = this.orchestrationConfig.temperature,
                )

                transcript?.write(
                    """
                    # Discussion Task Flow
                    ```mermaid
                    graph TD
                        Start((Start)) --> Context[Gather Context]
                        Context --> Mode{AutoFix?}
                        Mode -- Yes --> Direct[Direct LLM Answer]
                        Mode -- No --> Interactive[Interactive Discussion]
                        Direct --> End((End))
                        Interactive --> End
                    ```
                """.trimIndent().toByteArray()
                )

                val inquiryResult = if (orchestrationConfig.autoFix) {
                    task.add("Processing inquiry automatically...".renderMarkdown())
                    val inputStr = "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                        taskConfig?.inquiry_questions?.joinToString("\n")
                    }\nGoal: ${taskConfig?.inquiry_goal}\n\n${JsonUtil.toJson(executionConfig)}"
                    val input = toInput(inputStr)

                    transcript?.write(
                        "# Analysis Request\n<details><summary>Input Context</summary>\n\n${
                            input.joinToString(
                                "\n\n"
                            )
                        }\n\n</details>\n".toByteArray()
                    )
                    insightActor.answer(input)
                } else {
                    task.add("Opening interactive discussion...".renderMarkdown())
                    Discussable(
                        task = task,
                        userMessage = {
                            "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                                taskConfig?.inquiry_questions?.joinToString("\n")
                            }\nGoal: ${taskConfig?.inquiry_goal}\n${this.executionConfig?.toJson()}"
                        },
                        heading = taskConfig?.task_description ?: "Discussion",
                        initialResponse = { it: String ->
                            transcript?.write("# Initial Request\n\n$it\n\n".toByteArray())
                            insightActor.answer(toInput(it)).also { response ->
                                transcript?.write("# Initial Response\n\n$response\n\n".toByteArray())
                            }
                        },
                        outputFn = { design: String ->
                            design.renderMarkdown()
                        },
                        reviseResponse = { usermessages: List<Pair<String, Role>> ->
                            val inStr = "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                                taskConfig?.inquiry_questions?.joinToString("\n")
                            }\nGoal: ${taskConfig?.inquiry_goal}\n${this.executionConfig?.toJson()}"
                            val chatMessages =
                                usermessages.map { ModelSchema.ChatMessage(it.second, it.first.toContentList()) }
                                    .toTypedArray<ModelSchema.ChatMessage>()
                            transcript?.write("# Revision Request\n\n${usermessages.joinToString("\n") { "${it.second}: ${it.first}" }}\n\n".toByteArray())
                            insightActor.respond(
                                messages = chatMessages,
                                input = toInput(inStr),
                            ).also { response ->
                                transcript?.write("# Revision Response\n\n$response\n\n".toByteArray())
                            }
                        },
                        atomicRef = AtomicReference(),
                        semaphore = Semaphore(0),
                    ).call()
                }

                log.info("DiscussionTask completed successfully.")
                task.complete()
                resultFn(inquiryResult ?: "(no response)")
            } catch (e: Exception) {
                task.error(e)
                log.error("Error in DiscussionTask: ${e.message}", e)
                transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                resultFn("Error: ${e.message}")
            } finally {
                transcript?.close()
            }
        }
    }


    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .filterNotNull()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = if (!isTextFile(file)) {
                    extractDocumentContent(file)
                } else {
                    codeFiles[file.toPath()] ?: file.readText()
                }
                "# $relativePath\n\n$TRIPLE_TILDE\n$content\n$TRIPLE_TILDE"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(DiscussionTask::class.java)
        @JvmStatic val Discussion = TaskType(
            name = "Discussion",
            category = "File",
            taskClass = DiscussionTask::class.java,
            executionConfigClass = DiscussionTaskExecutionConfigData::class.java,
            taskSettingsClass = DiscussionTaskTypeConfig::class.java,
            description = "Directly answer questions or provide insights using the LLM, optionally referencing files, with optional user feedback and iteration.",
            tooltipHtml = """
                      Provides direct answers and insights using the LLM, optionally referencing project files.
                      <ul>
                        <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
                        <li>Reading files is optional; the task can operate with or without file input</li>
                        <li>User feedback and iterative refinement are supported but not required</li>
                        <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
                        <li>Can answer detailed questions about code, design, or project context</li>
                        <li>Supports both one-shot and interactive discussion modes</li>
                        <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
                      </ul>
                      """,
        )


        private val textExtensions = setOf(
            "txt",
            "md",
            "kt",
            "java",
            "js",
            "ts",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "css",
            "html",
            "xml",
            "json",
            "yaml",
            "yml",
            "properties",
            "gradle",
            "maven"
        )

        fun isTextFile(file: File): Boolean {
            return textExtensions.contains(file.extension.lowercase())
        }

        fun extractDocumentContent(file: File) = try {
            file.getDocumentReader().use { reader ->
                when (reader) {
                    is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                    else -> reader.getText()
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
            try {
                file.readText()
            } catch (e2: Exception) {
                "Error reading file: ${e2.message}"
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/FileAppendTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileAppendTask.FileAppendTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.util.concurrent.Semaphore

class FileAppendTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FileAppendTaskExecutionConfigData?
) : AbstractTask<FileAppendTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

    class FileAppendTaskExecutionConfigData(
        @Description("The file to append content to")
        var file: String? = null,
        @Description("Additional files to provide context for the append operation")
        var related_files: List<String>? = null,
        @Description("The specific content to append or a description of what to add")
        var append_content: String? = null,
        task_description: String? = mutableListOf<String>().toString(),
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = FileAppend.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (file.isNullOrBlank()) {
                return "A file must be specified in 'file'"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment() = """
FileAppend - Append content to the end of an existing file
  * Specify the relative file path and the content or goal of the addition.
  * The target file is NOT provided to the AI; only related context files are.
  * Useful for adding log entries, updating lists, or adding new exports/imports at the end of a file
""".trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val typeConfig = typeConfig ?: throw RuntimeException("Type configuration missing")
        val targetPath = executionConfig?.file ?: throw RuntimeException("Target file missing")
        val chatInterface = (typeConfig.model?.let<ApiChatModel, ChatInterface> { this.orchestrationConfig.instance(it) }
            ?: defaultSmart).getChildClient(task)
        val semaphore = Semaphore(0)
        val completionNotes = mutableListOf<String>()
        val transcript = task.transcript()
        val tabs = TabbedDisplay(task)
        val overviewTab = tabs.newTask("Overview")

        try {
            overviewTab.header("File Append Task: $targetPath")
          val status = overviewTab.add("🔄 Preparing append operation...".renderMarkdown())
            
            transcript?.write("# File Append Task Transcript\n\n".toByteArray())
          Retryable(task, process = { subTask: SessionTask ->
                completionNotes.clear()
                val context = getInputFileContent(executionConfig?.related_files, root)
                if (context.isNotBlank()) {
                    val contextTab = tabs.newTask("Context")
                  contextTab.add("### Context Files\n\n$context".renderMarkdown())
                    contextTab.complete()
                  transcript?.write(
                    """
                        <details>
                        <summary>Context Files Used</summary>
                        $context
                        </details>
                    """.trimIndent().toByteArray()
                  )
                }
                status?.setLength(0)
            status?.append("🔄 Generating content to append...".renderMarkdown())
            subTask.update()

                val chatAgent = ChatAgent(
                    name = "FileAppend",
                    prompt = """
                        You are an assistant specialized in appending content to files.
                        Based on the requirements, generate the exact text to be added to the END of the specified file.
                        
                        
                        Provide ONLY the content to be appended. 
                        If you use a markdown code block to wrap the content, ensure it is the only thing in your response.
                        Do not include headers or explanations.
                    """.trimIndent(),
                    model = chatInterface,
                    temperature = this.orchestrationConfig.temperature,
                )

                val codeResult = chatAgent.answer(
                    (messages + listOf(
                        "Context Files:\n$context",
                        "Target File Path: $targetPath",
                        "Append Goal/Content: ${executionConfig?.append_content ?: executionConfig?.task_description ?: ""}",
                    )).filter { it.isNotBlank() }
                ).let { extractCode(it) }
                val proposedTab = tabs.newTask("Proposed Append")
            proposedTab.add("### Proposed Append to `$targetPath`\n\n```\n$codeResult\n```".renderMarkdown())


                transcript?.write("\n## AI Proposed Append to $targetPath\n\n".toByteArray())
                transcript?.write(codeResult.toByteArray())

                val appendAction = {
                    val file = root.resolve(targetPath).toFile()
                    file.appendText(codeResult)
                    completionNotes += ("<a href='fileIndex/${agent.session}/$targetPath'>$targetPath</a> Appended")
                }

                if (orchestrationConfig.autoFix) {
                    appendAction()
                    status?.setLength(0)
                  status?.append("✅ **Auto-applied append to `$targetPath`.**".renderMarkdown())
                    proposedTab.complete()
                    semaphore.release()
                } else {
                    val footer = acceptButtonFooter(task.ui) {
                        appendAction()
                        status?.setLength(0)
                      status?.append("✅ **Appended successfully to `$targetPath`.**".renderMarkdown())
                        proposedTab.complete()
                        semaphore.release()
                    }
                    proposedTab.add(footer)
                }

                transcript?.flush()
            }.async(task.ui))

            semaphore.acquire()
            overviewTab.complete()
            transcript?.write("\n## Completion Notes\n\n".toByteArray())
            transcript?.write(completionNotes.joinToString("\n").toByteArray())
            resultFn(completionNotes.joinToString("\n"))
        } catch (e: Throwable) {
          // Triple Log Rule
            task.error(e)
          log.error("Error in FileAppendTask for $targetPath: ${e.message}", e)
          transcript?.write(
            """
                <details>
                <summary>Stack Trace</summary>
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray()
          )
          overviewTab.add("❌ **Error:** ${e.message}".renderMarkdown())
          overviewTab.complete()
        } finally {
            transcript?.close()
        }
    }
    private fun extractCode(text: String): String {
        val regex = """(?s)```(?:\w+)?\n(.*?)\n```""".toRegex()
        return regex.find(text)?.groupValues?.get(1) ?: text
    }


    companion object {
        private val log = LoggerFactory.getLogger(FileAppendTask::class.java)

        @JvmStatic val FileAppend = TaskType(
          name = "FileAppend",
          category = "File",
          taskClass = FileAppendTask::class.java,
          executionConfigClass = FileAppendTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Append content to the end of existing files",
          tooltipHtml = """
                          Allows for precise additions to the end of files without modifying existing content.
                          <ul>
                            <li>Ideal for logs, exports, and list updates</li>
                            <li>Supports AI-generated content based on context</li>
                            <li>Provides reviewable previews before applying changes</li>
                            <li>Integrates with project structure and standards</li>
                          </ul>
                      """.trimIndent(),
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/FileModificationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class FileModificationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FileModificationTaskExecutionConfigData?
) : AbstractFileTask<FileModificationTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class FileModificationTaskExecutionConfigData(
        files: List<String>? = null,
        related_files: List<String>? = null,
        extractContent: Boolean = false,
        @Description("Specific modifications to be made to the files")
        val modifications: Any? = null,
        @Description("Whether to include git diff with HEAD")
        val includeGitDiff: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : FileTaskExecutionConfig(
        task_type = FileModification.name,
        task_description = task_description,
        task_dependencies = task_dependencies,
        related_files = related_files,
        files = files,
        extractContent = extractContent,
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (files.isNullOrEmpty() && related_files.isNullOrEmpty()) {
                return "At least one file must be specified in either 'files' or 'related_files'"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    private fun getInputFileWithDiff() = if (!executionConfig?.includeGitDiff!!) getInputFileCode()
    else getInputFileCode { file ->
        formatFileForLLM(file).toString() + (file.toString().getGitDiff()?.let { diff ->
            "\n\nGit diff for $file:\n$diff"
        } ?: "")
    }

    override fun promptSegment() = """
FileModification - Modify existing files or create new files
  * For each file, specify the relative file path and the goal of the modification or creation
  * List input files/tasks to be examined when designing the modifications or new files
""".trimIndent()


    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val defaultFile = getDefaultFile()
        val typeConfig = typeConfig ?: throw RuntimeException("TypeConfig is missing")
        val chatInterface =
            (typeConfig.model?.let<ApiChatModel, ChatInterface> { this.orchestrationConfig.instance(it) }
                ?: defaultSmart).getChildClient(task)

        val semaphore = Semaphore(0)
        val completionNotes = mutableListOf<String>()
        val transcript = task.transcript()
        val tabs = TabbedDisplay(task)

        try {
            transcript?.write("# File Modification Task Transcript\n\n".toByteArray())

            Retryable(task, process = { task: SessionTask ->
                completionNotes.clear()

                // 1. Prepare Context
                val dependencyContext = agent.executionState?.tasksByDescription?.filter {
                    executionConfig?.task_dependencies?.contains(it.key) == true && it.value is FileModificationTaskExecutionConfigData
                }?.entries?.joinToString("\n\n") {
                    (it.value as FileModificationTaskExecutionConfigData).files?.joinToString("\n") { path ->
                        val file = root.resolve(path).toFile()
                        if (file.exists()) {
                            val relativePath = root.relativize(file.toPath())
                            "## $relativePath\n\n${(codeFiles[file.toPath()] ?: file.readText()).let { content -> "${TRIPLE_TILDE}\n${content}\n${TRIPLE_TILDE}" }}"
                        } else {
                            "File not found: $path"
                        }
                    } ?: ""
                } ?: ""

                val fileContext = getInputFileWithDiff()
                val taskDesc = executionConfig?.task_description ?: ""

                // 2. Log Context to Transcript & UI Tabs
                transcript?.write("""
## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
${dependencyContext.ifBlank { "None" }}

### File Context
${fileContext.ifBlank { "None" }}

### Task Description
$taskDesc
</details>

                """.toByteArray())
                val contextTab = tabs.newTask("Context")
                contextTab.add("""
# Task Context
$taskDesc
                """.renderMarkdown())
                contextTab.complete()

                val chatAgent = ChatAgent(
                    name = "FileModification",
                    prompt = getSystemPrompt(), // Extracted for readability
                    model = chatInterface,
                    temperature = this.orchestrationConfig.temperature,
                )

                val mainTask = tabs.newTask("Proposed Changes")
                mainTask.add("Generating modifications...".renderMarkdown())

                // 3. Execute AI
                val codeResult = chatAgent.answer(
                    (messages + listOf(
                        dependencyContext,
                        fileContext,
                        taskDesc,
                    )).filter { it.isNotBlank() }
                )

                // 4. Log Response to Transcript (Best Practice: Use <details>)
                transcript?.write("""
## AI Response
<details>
<summary>Raw Output</summary>

$codeResult
</details>

                """.toByteArray())

                val autoFix = orchestrationConfig.autoFix

                // 5. Render and Instrument
                val markdown = renderMarkdown(codeResult, ui = mainTask.ui) {
                    AddApplyFileDiffLinks.instrumentFileDiffs(
                        task.ui,
                        root = agent.root,
                        response = it,
                        handle = { newCodeMap ->
                            newCodeMap.forEach<Path, String> { (path, _) ->
                                val note = "<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated"
                                completionNotes += note
                                // Log individual file updates to transcript
                                transcript?.write("- $note\n".toByteArray())
                            }
                        },
                        shouldAutoApply = { autoFix },
                        model = chatInterface,
                        defaultFile = defaultFile,
                        processor = orchestrationConfig.processor
                    )
                }

                if (autoFix) {
                    transcript?.write("\n**Auto-applying changes...**\n".toByteArray())
                    mainTask.complete(markdown)
                    semaphore.release()
                } else {
                    mainTask.add(markdown)
                    // Best Practice: Use acceptButtonFooter for manual review
                    mainTask.complete(acceptButtonFooter(mainTask.ui) {
                        task.complete()
                        semaphore.release()
                    })
                }
                transcript?.flush()
            }.async(task.ui))

            semaphore.acquire()

            // 6. Finalize
            val summary = if (completionNotes.isNotEmpty()) {
                "### Modifications Applied\n" + completionNotes.joinToString("\n") { "* $it" }
            } else {
                "No modifications were applied."
            }

            transcript?.write("\n## Completion\n$summary\n".toByteArray())
            resultFn(summary)

        } catch (e: Throwable) {
            // Best Practice: Triple Log Rule
            task.error(e) // 1. UI
            log.error("Error in FileModificationTask", e) // 2. Log

            // 3. Transcript
            transcript?.write("""

## Error
<details>
<summary>Stack Trace</summary>

```
${e.stackTraceToString()}
```
</details>
            """.toByteArray())

            throw e
        } finally {
            transcript?.close()
        }
    }

    private fun getSystemPrompt(): String {
        return """
        Generate precise code modifications and new files based on requirements:
        For modifying existing files:
        - Write efficient, readable, and maintainable code changes
        - Ensure modifications integrate smoothly with existing code
        - Follow project coding standards and patterns
        - Consider dependencies and potential side effects
        - Provide clear context and rationale for changes

        For creating new files:
        - Choose appropriate file locations and names
        - Structure code according to project conventions
        - Include necessary imports and dependencies
        - Add comprehensive documentation
        - Ensure no duplication of existing functionality

        Provide a clear summary explaining:
        - What changes were made and why
        - Any important implementation details
        - Potential impacts on other code
        - Required follow-up actions

        Response format:
        For existing files: Use ${TRIPLE_TILDE}diff code blocks with a header specifying the file path.
        For new files: Use ${TRIPLE_TILDE} code blocks with a header specifying the new file path.
        The diff format should use + for line additions, - for line deletions.
        Include 2 lines of context before and after every change in diffs.
        Separate code blocks with a single blank line.
        For new files, specify the language for syntax highlighting after the opening triple backticks.

        Example:

        Here are the modifications:

        ### src/utils/existingFile.js
        ${TRIPLE_TILDE}diff

        function existingFunction() {
        return 'old result';
        return 'new result';
        }
        ${TRIPLE_TILDE}

        ### src/utils/newFile.js
        ${TRIPLE_TILDE}js

        function newFunction() {
         return 'new functionality';
        }
        ${TRIPLE_TILDE}
        """.trimIndent()
    }

    fun getDefaultFile() =
        if (((executionConfig?.related_files ?: listOf()) + (executionConfig?.files ?: listOf())).isEmpty()) {
            null
        } else if (((executionConfig?.related_files ?: listOf()) + (executionConfig?.files
                ?: listOf())).distinct().size == 1
        ) {
            ((executionConfig?.related_files ?: listOf()) + (executionConfig?.files ?: listOf())).last()
        } else if ((executionConfig?.files ?: listOf()).distinct().size == 1) {
            (executionConfig?.files ?: listOf()).first()
        } else {
            null
        }

    companion object {
        private val log = LoggerFactory.getLogger(FileModificationTask::class.java)

        @JvmStatic val FileModification = TaskType(
            "FileModification",
            "File",
            FileModificationTask::class.java,
            FileModificationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Create new files or modify existing code with AI-powered assistance",
            """
                      Creates or modifies source files with AI assistance while maintaining code quality.
                      <ul>
                        <li>Shows proposed changes in diff format for easy review</li>
                        <li>Supports both automated application and manual approval modes</li>
                        <li>Maintains project coding standards and style consistency</li>
                        <li>Handles complex multi-file operations and refactoring</li>
                        <li>Provides clear documentation of all changes with rationale</li>
                        <li>Implements proper error handling and edge cases</li>
                        <li>Updates imports and dependencies automatically</li>
                        <li>Preserves existing code formatting and structure</li>
                      </ul>
                    """,
        )

        fun String.getGitDiff(): String? {
            return try {
                val process = ProcessBuilder("git", "diff", "HEAD", "--", File(this).name)
                    .directory(File(this).parentFile)
                    .start()
                if (process.waitFor(10, TimeUnit.SECONDS)) {
                    process.inputStream.bufferedReader().readText()
                } else {
                    process.destroy()
                    log.warn("Git diff command timed out for file: ${this}")
                    null
                }
            } catch (e: Exception) {
                log.warn("Failed to get git diff for file: ${this}", e)
                null
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/FileSearchTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.extractDocumentContent
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.math.max

class FileSearchTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SearchTaskExecutionConfigData?
) : AbstractTask<FileSearchTask.SearchTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
    // SearchTaskConfigData remains the same
    class SearchTaskExecutionConfigData(
        @Description("The search pattern (substring or regex) to look for in the files")
        var search_pattern: String = "",
        @Description("Whether the search pattern is a regex (true) or a substring (false)")
        var is_regex: Boolean = false,
        @Description("The number of context lines to include before and after each match")
        var context_lines: Int = 2,
        @Description("The specific files (or file patterns) to be searched")
        var input_files: List<String>? = null,
        @Description("Whether to extract and search text content from non-text files (PDF, HTML, etc.)")
        var extractContent: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = FileSearch.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (search_pattern.isBlank()) {
                return "search_pattern cannot be blank"
            }
            if (context_lines < 0) {
                return "context_lines must be non-negative"
            }
            // Delegate to parent validation for nested objects
            return ValidatedObject.validateFields(this)
        }
    }
    // promptSegment remains the same

    override fun promptSegment() = """
FileSearch - Search for patterns in files and provide results with context
* Specify the search pattern (substring or regex)
* Specify whether the pattern is a regex or a substring
* Specify the number of context lines to include
* List files (incl glob patterns) to be searched
""".trimIndent()
    // run remains the same

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        renderTaskHeader(task)



        log.info("Starting FileSearchTask with pattern: ${executionConfig?.search_pattern}")
        task.ui.pool.submit {
            val transcript = task.transcript()
            try {
                val searchResults = performSearch()
                val formattedResults = formatSearchResults(searchResults)

                transcript?.write("## Search Results\n<details><summary>Raw Results</summary>\n\n$formattedResults\n</details>\n".toByteArray())

                if (searchResults.isEmpty()) {
                    task.add("No matches found.".renderMarkdown())
                } else {
                    val totalMatches = searchResults.sumOf { it.matches.size }
                    val filesWithMatches = searchResults.distinctBy { it.file }.size
                    val summaryText = "Found $totalMatches match(es) in $filesWithMatches file(s)."
                    task.add(summaryText.renderMarkdown())

                    val tabs = TabbedDisplay(task)
                    tabs["Summary"] = "# Search Summary\n\n$summaryText\n\n$formattedResults".renderMarkdown()

                    val files = searchResults.groupBy { it.file }
                    if (files.size <= 20) {
                        files.forEach { (file, blocks) ->
                            val fileMarkdown = blocks.joinToString("\n\n") { block ->
                                val blockEndLine = block.firstLineNumberInFile + block.contextLines.size - 1
                                val sb = StringBuilder()
                                sb.append("### Lines ${block.firstLineNumberInFile} - $blockEndLine\n\n")
                                sb.append("```\n")
                                block.contextLines.forEachIndexed { indexInBlock, lineContent ->
                                    val actualLineNumber = block.firstLineNumberInFile + indexInBlock
                                    val isMatchedLine =
                                        block.matches.any { it.indexInDisplayBlockContext == indexInBlock }
                                    val prefix = if (isMatchedLine) ">" else " "
                                    sb.append("$prefix ${actualLineNumber.toString().padStart(5)}: $lineContent\n")
                                }
                                sb.append("```")
                                sb.toString()
                            }
                            tabs[file] = fileMarkdown.renderMarkdown()
                        }
                    }
                }
                log.info("FileSearchTask completed successfully.")
                resultFn(formattedResults)
            } catch (e: Exception) {
                task.error(e)
                log.error("Error in FileSearchTask", e)
                transcript?.write(
                    """
                    <details>
                    <summary>Stack Trace</summary>
                    ```
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
                )
            } finally {
                transcript?.close()
                task.complete()
            }
        }
    }

    // Temporary holder for a raw match within a file
    private data class RawMatch(val lineNumber: Int, val lineContent: String) // lineNumber is 1-based

    // Represents a block of context that might contain multiple matches
    private data class DisplayBlock(
        val file: String,
        val contextLines: List<String>, // The actual lines of the combined context
        val firstLineNumberInFile: Int, // 1-based line number in the original file for contextLines[0]
        val matches: List<MatchInBlock> // Original matches that fall into this block
    )

    // Info about each original match within a DisplayBlock
    private data class MatchInBlock(
        val originalLineNumber: Int, // 1-based line number in the file
        val indexInDisplayBlockContext: Int // 0-based index within DisplayBlock.contextLines
    )

    private fun performSearch(): List<DisplayBlock> {
        val currentConfig = executionConfig ?: return emptyList()
        if (currentConfig == null) {
            log.warn("FileSearchTask taskConfig is null. Cannot perform search.")
            return emptyList()
        }
        val pattern = if (currentConfig.is_regex) {
            Pattern.compile(currentConfig.search_pattern)
        } else {
            Pattern.compile(Pattern.quote(currentConfig.search_pattern))
        }

        return (currentConfig.input_files ?: emptyList())
            .flatMap { filePattern ->
                val matcher = FileSystems.getDefault().getPathMatcher("glob:$filePattern")
                FileSelectionUtils.filteredWalk(root.toFile()) { path ->
                    matcher.matches(root.relativize(path.toPath())) && !FileSelectionUtils.isLLMIgnored(path.toPath())
                }.map { it.toPath() }.flatMap { path ->
                    try {
                        val fileContentLines = if (currentConfig.extractContent && !isTextFile(path.toFile())) {
                            extractDocumentContent(path.toFile()).lines()
                        } else {
                            Files.readAllLines(path)
                        }
                        val relativePath = root.relativize(path).toString()

                        // 1. Find all individual raw matches (line number and content)
                        val rawMatches = fileContentLines.mapIndexedNotNull { index, line ->
                            if (pattern.matcher(line).find()) {
                                RawMatch(lineNumber = index + 1, lineContent = line) // 1-based line number
                            } else null
                        }
                        if (rawMatches.isEmpty()) return@flatMap emptyList<DisplayBlock>()
                        // 2. Group raw matches into DisplayBlocks
                        val combinedBlocks = mutableListOf<DisplayBlock>()
                        var currentBlockAggregatedMatches = mutableListOf<RawMatch>()
                        var currentBlockContextStartLineInFile = 0 // 1-based
                        var currentBlockContextEndLineInFile = 0   // 1-based
                        val contextLinesCount = currentConfig.context_lines
                        for (match in rawMatches) { // rawMatches are already sorted by line number
                            val matchIdealContextStart = (match.lineNumber - contextLinesCount).coerceAtLeast(1)
                            val matchIdealContextEnd =
                                (match.lineNumber + contextLinesCount).coerceAtMost(fileContentLines.size)
                            if (currentBlockAggregatedMatches.isEmpty() || matchIdealContextStart > currentBlockContextEndLineInFile + 1) {
                                // Finalize previous block if it exists
                                if (currentBlockAggregatedMatches.isNotEmpty()) {
                                    val actualContext = fileContentLines.subList(
                                        (currentBlockContextStartLineInFile - 1).coerceAtLeast(0), // to 0-based index
                                        currentBlockContextEndLineInFile.coerceAtMost(fileContentLines.size) // exclusive end
                                    )
                                    combinedBlocks.add(
                                        DisplayBlock(
                                            file = relativePath,
                                            contextLines = actualContext,
                                            firstLineNumberInFile = currentBlockContextStartLineInFile,
                                            matches = currentBlockAggregatedMatches.map { aggMatch ->
                                                MatchInBlock(
                                                    originalLineNumber = aggMatch.lineNumber,
                                                    indexInDisplayBlockContext = aggMatch.lineNumber - currentBlockContextStartLineInFile
                                                )
                                            }
                                        ))
                                }
                                // Start a new block
                                currentBlockAggregatedMatches = mutableListOf(match)
                                currentBlockContextStartLineInFile = matchIdealContextStart
                                currentBlockContextEndLineInFile = matchIdealContextEnd
                            } else {
                                // Merge with current block
                                currentBlockAggregatedMatches.add(match)
                                // currentBlockContextStartLineInFile remains the earliest start (already set)
                                currentBlockContextEndLineInFile =
                                    max(currentBlockContextEndLineInFile, matchIdealContextEnd)
                            }
                        }
                        // Add the last processed block
                        if (currentBlockAggregatedMatches.isNotEmpty()) {
                            val actualContext = fileContentLines.subList(
                                (currentBlockContextStartLineInFile - 1).coerceAtLeast(0),
                                currentBlockContextEndLineInFile.coerceAtMost(fileContentLines.size)
                            )
                            combinedBlocks.add(
                                DisplayBlock(
                                    file = relativePath,
                                    contextLines = actualContext,
                                    firstLineNumberInFile = currentBlockContextStartLineInFile,
                                    matches = currentBlockAggregatedMatches.map { aggMatch ->
                                        MatchInBlock(
                                            originalLineNumber = aggMatch.lineNumber,
                                            indexInDisplayBlockContext = aggMatch.lineNumber - currentBlockContextStartLineInFile
                                        )
                                    }
                                ))
                        }
                        combinedBlocks // Return list of blocks for this file
                    } catch (e: Exception) {
                        log.warn("Error processing file ${root.relativize(path)} for search: ${e.message}", e)
                        emptyList()
                    }
                }
            }
    }

    private fun isTextFile(file: java.io.File): Boolean {
        val textExtensions = setOf(
            "txt",
            "md",
            "kt",
            "java",
            "js",
            "ts",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "css",
            "html",
            "xml",
            "json",
            "yaml",
            "yml",
            "properties",
            "gradle",
            "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }


    private fun formatSearchResults(results: List<DisplayBlock>, maxLength: Int = 500000): String {
        if (results.isEmpty()) {
            return "# Search Results\n\nNo matches found."
        }

        val sb = StringBuilder()
        val truncationMessage = "\n\n... (results truncated due to length limit)"
        // Effective max length for content, allowing space for truncation message if needed.
        // If maxLength is too small to even hold the truncation message, effectiveMaxLength might be 0 or negative.
        val effectiveMaxLength = if (maxLength > truncationMessage.length) maxLength - truncationMessage.length else 0

        var outputTruncated = false

        // Helper to append string segments, checking against effectiveMaxLength
        fun StringBuilder.appendCheckingLength(str: String): Boolean {
            if (this.length + str.length > effectiveMaxLength && effectiveMaxLength > 0) { // Check effectiveMaxLength > 0 to avoid issues if it's 0
                val remainingSpace = effectiveMaxLength - this.length
                if (remainingSpace > 0) {
                    this.append(str.take(remainingSpace))
                }
                outputTruncated = true
                return false // Signal to stop further appends to main content
            } else if (effectiveMaxLength <= 0 && maxLength > 0) { // Not enough space for content + truncation message
                // This case means we can only fit a small part of the content or just the truncation message
                outputTruncated = true
                return false
            }
            this.append(str)
            return true // Signal to continue
        }

        // Handle extremely small maxLength
        if (maxLength <= 0) return ""
        if (maxLength < 20 && results.isNotEmpty()) { // Arbitrary small number, too small for meaningful output
            return truncationMessage.trimStart().take(maxLength) // Show a part of truncation message if possible
        }

        if (!sb.appendCheckingLength("# Search Results\n\n")) {
            if (outputTruncated) { // Append truncation message if space allows, within original maxLength
                val finalMsg = truncationMessage.trimStart()
                sb.clear() // Clear partially added header
                sb.append(finalMsg.take(maxLength))
            }
            return sb.toString()
        }

        val totalMatches = results.sumOf { it.matches.size }
        val filesWithMatches = results.distinctBy { it.file }.size // Correctly counts files based on DisplayBlock.file
        val summary = "Found $totalMatches match(es) in $filesWithMatches file(s).\n\n"
        if (!sb.appendCheckingLength(summary)) {
            if (outputTruncated) { // Append truncation message, ensuring total length <= maxLength
                val spaceForMessage = maxLength - sb.length
                if (spaceForMessage > 0) sb.append(truncationMessage.take(spaceForMessage))
            }
            return sb.toString().take(maxLength) // Ensure final length constraint
        }

        results.groupBy { it.file }.forEach { (file, fileBlocks) -> // fileBlocks is List<DisplayBlock>
            if (outputTruncated) return@forEach

            val fileHeader = "## $file\n\n"
            if (!sb.appendCheckingLength(fileHeader)) return@forEach

            fileBlocks.forEach { block -> // Iterate over each DisplayBlock
                if (outputTruncated) return@forEach

                val blockEndLine = block.firstLineNumberInFile + block.contextLines.size - 1
                val resultHeader = "### Lines ${block.firstLineNumberInFile} - $blockEndLine\n\n"

                val contextBlockString = buildString {
                    appendLine("```")
                    block.contextLines.forEachIndexed { indexInBlock, lineContent ->
                        val actualLineNumber = block.firstLineNumberInFile + indexInBlock
                        // Check if this line is one of the actual matches
                        val isMatchedLine = block.matches.any { it.indexInDisplayBlockContext == indexInBlock }
                        val prefix = if (isMatchedLine) ">" else " "
                        appendLine("$prefix ${actualLineNumber.toString().padStart(5)}: $lineContent")
                    }
                    appendLine("```")
                    appendLine() // Extra newline after the block
                }
                val fullResultBlock = resultHeader + contextBlockString
                if (!sb.appendCheckingLength(fullResultBlock)) return@forEach
            }
        }

        if (outputTruncated) {
            val spaceForMessage = maxLength - sb.length
            if (spaceForMessage > 0) {
                sb.append(truncationMessage.take(spaceForMessage))
            }
        }

        return sb.toString().take(maxLength) // Final safeguard
    }


    companion object {
        private val log = LoggerFactory.getLogger(FileSearchTask::class.java)

        @JvmStatic val FileSearch = TaskType(
            name = "FileSearch",
            category = "File",
            taskClass = FileSearchTask::class.java,
            executionConfigClass = SearchTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Search project files using patterns with contextual results",
            tooltipHtml = """
                                Performs pattern-based searches across project files with context.
                                <ul>
                                  <li>Supports both substring and regex search patterns</li>
                                  <li>Shows configurable context lines around matches</li>
                                  <li>Groups results by file with line numbers</li>
                                  <li>Filters for text-based files automatically</li>
                                  <li>Provides organized, readable output format</li>
                                </ul>
                              """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/GeneratePresentationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import javax.imageio.ImageIO

private const val TT = """```"""

class GeneratePresentationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GeneratePresentationTaskExecutionConfigData?
) : AbstractFileTask<GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData>(
    orchestrationConfig,
    planTask
) {

    class GeneratePresentationTaskExecutionConfigData(
        @Description("The HTML presentation file to be created (relative path, must end with .html)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., existing presentations, reference materials)")
        related_files: List<String>? = null,
        @Description("Detailed description of the presentation including topic, key points, target audience, and desired style")
        task_description: String? = null,
        @Description("Whether to generate images for key slides")
        val generate_images: Boolean = false,
        @Description("Width of generated images in pixels")
        val image_width: Int = 1024,
        @Description("Height of generated images in pixels")
        val image_height: Int = 1024,
        @Description("Maximum number of images to generate (1-10)")
        val max_images: Int = 5,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GeneratePresentation.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            // Validate that at least one file is specified
            if (files.isNullOrEmpty()) {
                return "GeneratePresentationTask requires at least one file to be specified"
            }

            // Validate that the file has .html extension
            val htmlFile = files!!.first()
            if (!htmlFile.endsWith(".html", ignoreCase = true)) {
                return "GeneratePresentationTask file must have .html extension: $htmlFile"
            }
            if (image_width < 256 || image_width > 2048) {
                return "Image width must be between 256 and 2048, got: $image_width"
            }
            if (image_height < 256 || image_height > 2048) {
                return "Image height must be between 256 and 2048, got: $image_height"
            }
            if (max_images < 1 || max_images > 10) {
                return "Max images must be between 1 and 10, got: $max_images"
            }

            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
 GeneratePresentation - Create a Reveal.js presentation with custom styling
  ** Specify the HTML presentation file path in the files array (must end with .html)
  ** Provide a detailed description including:
     - Presentation topic and title
     - Key points and sections to cover
     - Target audience and tone (professional, casual, technical, etc.)
     - Number of slides desired
     - Any specific visual style preferences
  ** The generated presentation will include:
     - Complete HTML structure using Reveal.js framework
     - Multiple slides with proper structure and speaker notes
     - Custom CSS file (presentation.css) for styling
     - Autoplay controls and voice selection UI
     - Proper accessibility features
     - Optional AI-generated images for key slides
  ** Related files can include reference materials or existing presentations
  ** Output will be presented for review before being written to disk
        """.trimIndent()
    }
    data class PresentationSlide(
        @Description("The title of the slide")
        val title: String = "",
        @Description("The HTML content of the slide (Reveal.js compatible)")
        val html_content: String = "",
        @Description("Detailed speaker notes for this slide")
        val speaker_notes: String = "",
        @Description("A prompt for an AI image generator to create a visual for this slide")
        val image_prompt: String? = null
    )
    data class PresentationStructure(
        val slides: List<PresentationSlide> = emptyList()
    )


    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val htmlFiles = executionConfig?.files ?: emptyList()
        if (htmlFiles.isEmpty()) {
            resultFn("CONFIGURATION ERROR: No presentation file specified")
            return
        }

        val htmlFile = htmlFiles.first()
        if (!htmlFile.endsWith(".html", ignoreCase = true)) {
            resultFn("CONFIGURATION ERROR: File must have .html extension: $htmlFile")
            return
        }


        val filesToWrite = mutableListOf<Pair<String, String>>()
        val standardCss = this::class.java.getResource("/presentations/presentation.css")?.readText() ?: ""

        val standardJs = this::class.java.getResource("/presentations/presentation.js")?.readText() ?: ""
        filesToWrite.add("presentation.js" to standardJs)

        val revealInitCode = this::class.java.getResource("/presentations/reveal_init.js")?.readText() ?: ""
        filesToWrite.add("reveal_init.js" to revealInitCode)

        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")
        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = defaultSmart

        overviewTask.header("Creating Presentation: $htmlFile", level = 2)

        val contextFiles = getInputFileCode()
        val priorCode = getPriorCode(agent.executionState)

        // Step 1: Generate slide content only
        val outlinePrompt = """
 You are an expert presentation designer tasked with creating a Reveal.js presentation.

## Standard CSS Already Included:
The following standard CSS is already included and should not be duplicated:
${TT}css
$standardCss
$TT

 ## Requirements:
 ${executionConfig?.task_description ?: "Create a presentation as specified"}

 ## Context from Related Files:
 $contextFiles

 ## Previous Task Results:
 $priorCode

 ## Instructions:
1. Generate ONLY the slide content as a sequence of HTML <section> tags:
   - A compelling title slide
   - 5-10 content slides covering the main points
   - Logical flow and transitions between topics
2. Each <section> tag should contain:
   - A clear heading
   - 2-4 key points or visual elements
   - An <aside class="notes"> element with detailed speaker notes (2-3 sentences)
 3. Use appropriate Reveal.js features:
   - data-auto-animate for smooth transitions
   - Fragments for progressive disclosure
4. Include emojis or icons where appropriate for visual interest

## Output Format:
Provide ONLY the slide sections within a code block (no DOCTYPE, html, head, or body tags):
${TT}html
<section>
    <h1>Title</h1>
    <p class="subtitle">Subtitle</p>
    <aside class="notes">
        Speaker notes for this slide go here.
    </aside>
</section>

<section>
    <h2>Slide Title</h2>
    <ul>
        <li class="fragment">Point 1</li>
        <li class="fragment">Point 2</li>
    </ul>
    <aside class="notes">
        Detailed speaker notes explaining the content.
    </aside>
</section>
$TT
        """.trimIndent()

        val slideAgent = ParsedAgent(
            resultClass = PresentationStructure::class.java,
            prompt = outlinePrompt,
            model = api,
            parsingChatter = defaultFast
        )



        overviewTask.header("Step 1: Generating Presentation Structure", level = 3)
        val presentationStructure = slideAgent.answer(listOf("Generate the presentation.")).obj

        if (presentationStructure.slides.isEmpty()) {
            resultFn("ERROR: Failed to generate presentation structure")
            return
        }

        // Step 1.5: Generate images for key slides if enabled
        val imageMap = mutableMapOf<Int, String>()
        if (executionConfig?.generate_images != false) {

            val imageTask = tabs.newTask("Images")
            imageTask.header("Generating Images for Key Slides", level = 3)
            imageMap.putAll(generateSlideImages(presentationStructure, task, orchestrationConfig, imageTask))
            imageTask.complete()
        }

        val presentationTitle = presentationStructure.slides.firstOrNull()?.title ?: "Presentation"
        // Inject images into slide content
        val enhancedSlideContent = buildSlideHtml(presentationStructure, imageMap)

        // Wrap slides in the HTML template
        val htmlStructure = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta content="width=device-width, initial-scale=1.0" name="viewport">
    <meta content="$presentationTitle" name="description">
    <title>$presentationTitle</title>
    <link href="https://cdnjs.cloudflare.com" rel="preconnect">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/reset.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/reveal.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/theme/black.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/highlight/monokai.min.css" rel="stylesheet">
    <link href="presentation.css" rel="stylesheet">
</head>
<body>
<div aria-label="Presentation controls" id="controlsContainer" role="toolbar">
    <button aria-label="Toggle autoplay" aria-pressed="false" id="autoplayButton">Autoplay: Off</button>
    <label class="sr-only" for="voiceSelect">Select voice</label>
    <select aria-label="Voice selection" id="voiceSelect"></select>
</div>
<div class="reveal">
    <div class="slides">
$enhancedSlideContent
    </div>
</div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/reveal.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/notes/notes.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/markdown/markdown.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/highlight/highlight.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/zoom/zoom.min.js"></script>
<script src="presentation.js"></script>
<script src="reveal_init.js"></script>
</body>
</html>
    """.trimIndent()


// Step 2: Generate custom CSS
        val cssPrompt = """
Based on the following Reveal.js presentation HTML, generate custom CSS styling.

 ## Slide Content:
 ${TT}html
$enhancedSlideContent
$TT

## Requirements:
${executionConfig?.task_description ?: "Create appropriate styling for the presentation"}

## Instructions:
1. Create ONLY additional custom CSS that enhances the presentation
2. DO NOT duplicate any styles already present in the standard CSS above
3. Focus on adding custom styles for:
   - .subtitle class for subtitle text
   - .fade-in-text for animated text
   - .intro-points for bullet point lists
   - Custom slide transitions and animations
   - Any slide-specific styling based on the content
4. Only add new styles that complement the existing CSS, such as:
   - Font sizes and weights
   - Color contrasts
   - Spacing and padding
   - Hover effects for interactive elements
5. Keep the CSS minimal and avoid conflicts with existing styles
6. Use CSS variables defined in the standard CSS where applicable

## Output Format:
Provide only the ADDITIONAL custom CSS code within a code block (no duplicates):
${TT}css
/* Custom Presentation Styles */
$TT
        """.trimIndent()

        val stylingTask = tabs.newTask("Styling")
        stylingTask.header("Generating Custom CSS", level = 3)
        val chatAgent = ChatAgent(prompt = cssPrompt, model = api)

        val cssCode = extractCodeFromResponse(chatAgent.answer(toInput(cssPrompt)), "css")

        if (cssCode.isEmpty()) {
            resultFn("ERROR: Failed to generate CSS styling")
            return
        }

        stylingTask.complete()

        overviewTask.header("Step 3: Finalizing Files", level = 3)
        filesToWrite.add(htmlFile to htmlStructure)
        filesToWrite.add("presentation.css" to (standardCss + "\n\n" + cssCode))
        val transcriptStream = task.transcript("${presentationTitle.replace(Regex("[^a-zA-Z0-9]"), "_")}")
        transcriptStream?.close()


        // Display preview
        val filesTask = tabs.newTask("Files")
        filesTask.header("Generated Files Preview", level = 3)
        filesToWrite.forEach { (filename, content) ->
            filesTask.header(filename, level = 4)
            val codeBlock = "$TT${getFileExtension(filename)}\n$content\n$TT"
            filesTask.expandable("View Content", MarkdownUtil.renderMarkdown(codeBlock, ui = ui))
        }
        filesTask.complete()

        try {
            val outputPath = root.resolve(htmlFile)
            outputPath.toFile().parentFile?.mkdirs()
            val writtenFiles = mutableListOf<String>()

            filesToWrite.forEach { (filename, content) ->
                val path = when (filename) {
                    executionConfig?.files?.firstOrNull() -> outputPath
                    else -> outputPath.resolveSibling(filename)
                }
                path.toFile().parentFile?.mkdirs()
                path.toFile().writeText(content)
                writtenFiles.add(filename)
                overviewTask.add("""<a href="${task.linkTo(filename)}">${filename}</a> created""")
            }

            val summary = "Successfully wrote ${writtenFiles.joinToString(", ")}"
            overviewTask.complete(summary)
            resultFn(summary)
        } catch (e: Exception) {
            log.error("Error writing presentation files", e)
            overviewTask.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun getFileExtension(filename: String): String {
        return when {
            filename.endsWith(".html") -> "html"
            filename.endsWith(".css") -> "css"
            filename.endsWith(".js") -> "javascript"
            else -> ""
        }
    }

    private fun extractCodeFromResponse(response: String, vararg languages: String): String {
        // Try to extract code from code blocks with specified languages
        for (lang in languages) {
            val codeBlockRegex = "$TT$lang\\s*([\\s\\S]*?)${TT}".toRegex()
            val match = codeBlockRegex.find(response)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }

        // Try generic code block
        val genericBlockRegex = "$TT\\s*([\\s\\S]*?)${TT}".toRegex()
        val genericMatch = genericBlockRegex.find(response)
        if (genericMatch != null) {
            return genericMatch.groupValues[1].trim()
        }

        return ""
    }

    private fun generateSlideImages(
        structure: PresentationStructure,
        task: SessionTask,
        orchestrationConfig: OrchestrationConfig,
        imageTask: SessionTask
    ): Map<Int, String> {
        val imageMap = mutableMapOf<Int, String>()
        try {
            val slides = structure.slides
            val maxImages = executionConfig?.max_images?.coerceIn(1, 10) ?: 3
            val slideIndices = selectSlidesForImages(slides.size, maxImages)
            imageTask.add("Generating images for ${slideIndices.size} slides (indices: ${slideIndices.joinToString(", ")})")
            slideIndices.forEachIndexed { idx, slideIndex ->
                if (slideIndex >= slides.size) return@forEachIndexed
                val slide = slides[slideIndex]
                val sectionContent = slide.html_content
                val heading = slide.title
                
                // Extract text content (remove HTML tags)
                val textContent = sectionContent
                    .replace(Regex("<aside[^>]*>.*?</aside>", RegexOption.DOT_MATCHES_ALL), "")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(200)
                val imageFilename = "slide_${slideIndex + 1}_image.png"
                try {
                    imageTask.add("Generating image ${idx + 1}/${slideIndices.size}: <b>$heading</b>")
                    val imageAgent = ImageProcessingAgent(
                        prompt = "Create a professional, visually appealing image for a presentation slide",
                        model = orchestrationConfig.defaultImage,
                        temperature = 0.7,
                    )
                    val imagePrompt = """
Create a professional presentation slide image for:
Title: $heading
Context: ${slide.image_prompt ?: ""}
Content: $textContent
Style: Clean, modern, professional presentation aesthetic
          """.trimIndent()
                    val result = imageAgent.answer(listOf(ImageAndText(imagePrompt)))
                    val image = result.image
                    // Save image
                    val imageFile = task.resolveUserFile(imageFilename)!!
                    ImageIO.write(image, "png", imageFile)
                    imageMap[slideIndex] = imageFilename
                    imageTask.image(image!!)
                    imageTask.add("✅ Generated image for slide ${slideIndex + 1}: <a href='${task.linkTo(imageFilename)}' target='_blank'>$imageFilename</a>")
                    log.debug("Generated image for slide ${slideIndex + 1}: $imageFilename")
                } catch (e: Exception) {
                    log.error("Failed to generate image for slide ${slideIndex + 1}", e)
                    imageTask.add(
                        "⚠️ Failed to generate image for slide ${slideIndex + 1}: ${e.message}",
                        additionalClasses = "text-danger"
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error during image generation", e)
            imageTask.add("⚠️ Image generation encountered errors: ${e.message}", additionalClasses = "text-danger")
        }
        return imageMap
    }

    private fun selectSlidesForImages(totalSlides: Int, maxImages: Int): List<Int> {
        if (totalSlides <= 1) return emptyList()
        // Skip title slide (index 0), select evenly distributed slides
        val availableSlides = totalSlides - 1
        val numImages = minOf(maxImages, availableSlides)
        if (numImages <= 0) return emptyList()
        val indices = mutableListOf<Int>()
        val step = availableSlides.toDouble() / numImages
        for (i in 0 until numImages) {
            val index = (1 + (i * step)).toInt().coerceIn(1, totalSlides - 1)
            if (!indices.contains(index)) {
                indices.add(index)
            }
        }
        return indices.sorted()
    }

    private fun buildSlideHtml(structure: PresentationStructure, imageMap: Map<Int, String>): String {
        val result = StringBuilder()
        structure.slides.forEachIndexed { index, slide ->
            result.append("<section>\n")
            result.append("    <h1>${slide.title}</h1>\n")
            
            if (imageMap.containsKey(index)) {
                val imageFilename = imageMap[index]!!
                result.append("""
        <div class="slide-image">
            <img src="$imageFilename" alt="Slide visual" style="max-width: 80%; max-height: 400px; margin: 20px auto; display: block; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);">
        </div>
""".trimIndent()).append("\n")
            }
            
            result.append(slide.html_content).append("\n")
            
            if (slide.speaker_notes.isNotBlank()) {
                result.append("    <aside class=\"notes\">\n")
                result.append("        ${slide.speaker_notes}\n")
                result.append("    </aside>\n")
            }
            result.append("</section>\n\n")
        }
        return result.toString()
    }


    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Write Files") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GeneratePresentationTask::class.java)
        @JvmStatic val GeneratePresentation = TaskType(
            "GeneratePresentation",
            "Writing",
            GeneratePresentationTask::class.java,
            GeneratePresentationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Create complete Reveal.js presentations with narration support",
            """
              Creates professional Reveal.js presentations with speaker notes.
              <ul>
                <li>Generates complete, self-contained HTML presentations</li>
                <li>Includes Reveal.js framework integration</li>
                <li>Adds speaker notes for each slide</li>
                <li>Supports custom styling and themes</li>
                <li>Optional AI-generated images for key slides</li>
                <li>Interactive approval or auto-apply mode</li>
                <li>Includes navigation and progress indicators</li>
                <li>Optional audio narration support</li>
              </ul>
            """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/GenerateQRImageTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.google.zxing.*
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GenerateQRImageTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GenerateQRImageTaskExecutionConfigData?
) : AbstractFileTask<GenerateQRImageTask.GenerateQRImageTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class GenerateQRImageTaskExecutionConfigData(
        @Description("The output image file to be created (relative path, must end with .png, .jpg, or .jpeg)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., reference images, style guides)")
        related_files: List<String>? = null,
        @Description("The data/text content to encode in the QR code")
        val qr_content: String? = null,
        @Description("Artistic style directive for the Image Agent to apply to the QR code (e.g., 'Make it look like a watercolor painting', 'Add a forest background')")
        val style_directive: String? = null,
        @Description("Size of the QR code in pixels (default: 500)")
        val qr_size: Int? = 500,
        @Description("Maximum number of retry attempts if QR verification fails (default: 3)")
        val max_retries: Int? = 3,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GenerateQRImage.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            if (files.isNullOrEmpty()) {
                return "GenerateQRImageTask requires at least one output file to be specified"
            }
            val imageFile = files!!.first()
            if (!imageFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
                return "GenerateQRImageTask file must have .png, .jpg, or .jpeg extension: $imageFile"
            }
            if (qr_content.isNullOrBlank()) {
                return "GenerateQRImageTask requires qr_content to be specified"
            }
            if (style_directive.isNullOrBlank()) {
                return "GenerateQRImageTask requires style_directive to be specified"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
GenerateQRImage - Generate artistic QR codes using AI image processing
  ** files: The output image file to be created (relative path, must end with .png, .jpg, or .jpeg)
  ** qr_content: The data/text content to encode in the QR code
  ** style_directive: Artistic style directive for the Image Agent (e.g., 'watercolor painting')
  ** qr_size: Size of the QR code in pixels (default: 500)
  ** max_retries: Maximum number of retry attempts if QR verification fails (default: 3)
  ** related_files: Additional files for context (e.g., reference images)
        """.trimIndent()
    }

    override fun formatFileForLLM(relativePath: File): CharSequence? {
        return when (relativePath.name.split('.').last()) {
            "png", "jpg", "jpeg" -> null
            else -> super.formatFileForLLM(relativePath)
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val imageFiles = executionConfig?.files ?: emptyList()
        if (imageFiles.isEmpty()) {
            resultFn("CONFIGURATION ERROR: No output file specified")
            return
        }

        val qrContent = executionConfig?.qr_content
        if (qrContent.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No QR content specified")
            return
        }

        val styleDirective = executionConfig?.style_directive
        if (styleDirective.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No style directive specified")
            return
        }

        val qrSize = executionConfig?.qr_size ?: 500
        val maxRetries = executionConfig?.max_retries ?: 3
        val imageOutputFile = imageFiles.first()

        if (!imageOutputFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
            resultFn("CONFIGURATION ERROR: File must have .png, .jpg, or .jpeg extension: $imageOutputFile")
            return
        }

        val tabs = TabbedDisplay(task)
        val overviewTab = tabs.newTask("Overview")
        overviewTab.header("Generating Artistic QR Code: `$imageOutputFile`", level = 2)
        overviewTab.add(MarkdownUtil.renderMarkdown("### QR Content\n```\n$qrContent\n```", ui = task.ui))
        overviewTab.add(MarkdownUtil.renderMarkdown("### Style Directive\n$styleDirective", ui = task.ui))

        try {
            // Step 1: Generate base QR code with high error correction
            val baseQrTab = tabs.newTask("Base QR")
            baseQrTab.header("Step 1: Generating Base QR Code (Error Correction Level H)", level = 3)
            val baseQrImage = generateQRCode(qrContent, qrSize)

            // Display base QR
            baseQrTab.add("Base QR Code:")
            baseQrTab.image(baseQrImage)

            // Verify base QR is readable
            val baseVerification = verifyQRCode(baseQrImage)
            if (baseVerification != qrContent) {
                resultFn("ERROR: Base QR code verification failed")
                return
            }
            baseQrTab.add("✓ Base QR code verified successfully", additionalClasses = "text-success")

            // Load reference images if any
            val inputImageFiles = executionConfig?.related_files?.filter {
                it.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))
            } ?: emptyList()
            val inputImages = inputImageFiles.mapNotNull { filePath ->
                val file = root.resolve(filePath)
                if (file.toFile().exists()) {
                    val image = ImageIO.read(file.toFile())
                    ImageAndText(image = image, text = "Reference image: $filePath")
                } else null
            }

            // Step 2: Process with Image Agent
            val stylingTab = tabs.newTask("Styling")
            stylingTab.header("Step 2: Applying Artistic Style", level = 3)

            val imageAgent = ImageProcessingAgent(
                prompt = """You are an artistic QR code designer. Your task is to stylize a QR code while ensuring it remains scannable.
                    
CRITICAL REQUIREMENTS:
- The QR code MUST remain scannable after your modifications
- Preserve the overall structure and contrast of the QR code
- The three large corner squares (finder patterns) must remain clearly visible
- Maintain sufficient contrast between light and dark modules
- You can add artistic elements, colors, gradients, and decorations
- You can modify the style of the modules (squares) but keep their positions
- Background elements should not obscure the QR pattern""",
                name = "QRCodeArtist",
                model = orchestrationConfig.defaultImage,
            )

            var styledImage: BufferedImage? = null
            var verifiedContent: String? = null
            var attempt = 0

            while (attempt < maxRetries && verifiedContent != qrContent) {
                attempt++
                stylingTab.header("Attempt $attempt of $maxRetries", level = 4)

                val prompt = if (attempt == 1) {
                    """Apply this artistic style to the QR code: $styleDirective
                    
The QR code encodes: "$qrContent"
Ensure the result remains scannable."""
                } else {
                    """Apply this artistic style to the QR code: $styleDirective
                    
The QR code encodes: "$qrContent"
IMPORTANT: Previous attempt failed verification. Please be more conservative with modifications.
- Keep higher contrast between dark and light areas
- Ensure finder patterns (corner squares) are very clear
- Reduce artistic modifications that might interfere with scanning"""
                }

                val result = imageAgent.answer(listOf(ImageAndText(image = baseQrImage, text = prompt)) + inputImages)
                styledImage = result.image

                // Display styled image
                stylingTab.add("Styled QR Code (Attempt $attempt):")
                stylingTab.image(styledImage!!)

                // Step 3: Verify the styled QR code
                stylingTab.add("Verifying Styled QR Code...")
                verifiedContent = try {
                    styledImage?.let { verifyQRCode(it) }
                } catch (e: Exception) {
                    log.warn("QR verification failed on attempt $attempt", e)
                    null
                }

                if (verifiedContent == qrContent) {
                    stylingTab.add("✓ Verification successful! QR code is readable.", additionalClasses = "text-success")
                } else {
                    stylingTab.add(
                        "✗ Verification failed. " +
                                if (verifiedContent == null) "QR code could not be read."
                                else "Content mismatch: got '$verifiedContent'",
                        additionalClasses = "text-danger"
                    )
                }
            }

            if (verifiedContent != qrContent || styledImage == null) {
                stylingTab.add("⚠️ Warning: Final QR code may not be scannable", additionalClasses = "alert alert-warning")
                stylingTab.add("After $maxRetries attempts, the styled QR code could not be verified. Saving the last attempt anyway.")
            }

            // Save the final image
            val outputPath = root.resolve(imageOutputFile)
            outputPath.toFile().parentFile?.mkdirs()

            val format = when {
                imageOutputFile.endsWith(".png", ignoreCase = true) -> "png"
                imageOutputFile.endsWith(".jpg", ignoreCase = true) -> "jpg"
                imageOutputFile.endsWith(".jpeg", ignoreCase = true) -> "jpeg"
                else -> "png"
            }

            ImageIO.write(styledImage ?: baseQrImage, format, outputPath.toFile())

            val verificationStatus =
                if (verifiedContent == qrContent) "verified and scannable" else "may not be scannable"
            val summary =
                "Generated artistic QR code ($verificationStatus) saved to <a href=\"${task.linkTo(imageOutputFile)}\">$imageOutputFile</a>."

            val finalTab = tabs.newTask("Final Result")
            finalTab.header("Final Artistic QR Code", level = 3)
            finalTab.image(styledImage ?: baseQrImage)
            finalTab.add(MarkdownUtil.renderMarkdown(summary, ui = task.ui))

            if (orchestrationConfig.autoFix) {
                task.complete(summary)
                resultFn(summary)
            } else {
                finalTab.add(
                    MarkdownUtil.renderMarkdown(
                        acceptButtonFooter(task.ui) {
                            try {
                                task.complete(summary)
                                resultFn(summary)
                            } catch (e: Exception) {
                                log.error("Error accepting QR image", e)
                                task.error(e)
                                resultFn("ERROR: ${e.message}")
                            }
                        }, ui = task.ui
                    )
                )
            }

        } catch (e: Exception) {
            log.error("Error generating QR image", e)
            task.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun generateQRCode(content: String, size: Int) = MatrixToImageWriter.toBufferedImage(
        QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size, mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, // Highest error correction (30%)
                EncodeHintType.MARGIN to (size / 35).coerceAtLeast(5),
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
    )

    private fun verifyQRCode(image: BufferedImage): String? {
        // Try multiple verification strategies to better match real-world phone scanners
        val strategies = listOf(
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(img) },
            { img: BufferedImage -> verifyWithHybridBinarizer(img) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(thresholdImage(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(img)) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(enhanceContrast(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(enhanceContrast(img)) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(convertToGrayscale(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(convertToGrayscale(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(convertToGrayscale(img))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(enhanceContrast(convertToGrayscale(img))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(sharpenImage(img)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(sharpenImage(img))) },
            // Scale variations
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 2.0)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 1.5)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 0.5)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(scaleImage(img, 0.75)) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(scaleImage(img, 2.0))) },
            // Aggressive preprocessing combinations
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(thresholdImage(enhanceContrast(img))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(thresholdImage(enhanceContrast(convertToGrayscale(img)))) },
            { img: BufferedImage -> verifyWithHybridBinarizer(adaptiveThreshold(convertToGrayscale(img))) },
            { img: BufferedImage -> verifyWithGlobalHistogramBinarizer(adaptiveThreshold(convertToGrayscale(img))) },
        )
        for (strategy in strategies) {
            try {
                val result = strategy(image)
                if (result != null) {
                    log.debug("QR verification succeeded with strategy")
                    return result
                }
            } catch (e: Exception) {
                log.trace("QR verification strategy failed", e)
            }
        }
        log.debug("All QR verification strategies failed")
        return null
    }

    private fun verifyWithHybridBinarizer(image: BufferedImage): String? {
        return try {
            val luminanceSource = BufferedImageLuminanceSource(image)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(luminanceSource))
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.PURE_BARCODE to false,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
            val result = MultiFormatReader().decode(binaryBitmap, hints)
            result.text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun verifyWithGlobalHistogramBinarizer(image: BufferedImage): String? {
        return try {
            val luminanceSource = BufferedImageLuminanceSource(image)
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(luminanceSource))
            val hints = mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.PURE_BARCODE to false,
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
            )
            val result = MultiFormatReader().decode(binaryBitmap, hints)
            result.text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleImage(image: BufferedImage, scale: Double): BufferedImage {
        val newWidth = (image.width * scale).toInt()
        val newHeight = (image.height * scale).toInt()
        val scaled = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB)
        val g2d = scaled.createGraphics()
        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g2d.drawImage(image, 0, 0, newWidth, newHeight, null)
        g2d.dispose()
        return scaled
    }

    private fun convertToGrayscale(image: BufferedImage): BufferedImage {
        val grayscale = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        val g2d = grayscale.createGraphics()
        g2d.drawImage(image, 0, 0, null)
        g2d.dispose()
        return grayscale
    }

    private fun enhanceContrast(image: BufferedImage): BufferedImage {
        val enhanced = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        // First pass: find min/max values for histogram stretching
        var minVal = 255
        var maxVal = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                minVal = minOf(minVal, luminance)
                maxVal = maxOf(maxVal, luminance)
            }
        }
        val range = (maxVal - minVal).coerceAtLeast(1)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                // Histogram stretching combined with contrast enhancement
                val newR = (((r - minVal) * 255) / range).coerceIn(0, 255)
                val newG = (((g - minVal) * 255) / range).coerceIn(0, 255)
                val newB = (((b - minVal) * 255) / range).coerceIn(0, 255)
                enhanced.setRGB(x, y, (newR shl 16) or (newG shl 8) or newB)
            }
        }
        return enhanced
    }

    private fun sharpenImage(image: BufferedImage): BufferedImage {
        try {
            val kernel = java.awt.image.Kernel(
                3, 3, floatArrayOf(
                    0f, -1f, 0f,
                    -1f, 5f, -1f,
                    0f, -1f, 0f
                )
            )
            val op = java.awt.image.ConvolveOp(kernel, java.awt.image.ConvolveOp.EDGE_NO_OP, null)
            val dest = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
            op.filter(image, dest)
            return dest
        } catch (e: Exception) {
            log.warn("Error sharpening image", e)
            return image
        }
    }

    private fun thresholdImage(image: BufferedImage): BufferedImage {
        val grayscale = convertToGrayscale(image)
        val thresholded = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        // Calculate average luminance for adaptive threshold
        var totalLuminance = 0L
        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                totalLuminance += grayscale.getRGB(x, y) and 0xFF
            }
        }
        val avgLuminance = (totalLuminance / (grayscale.width * grayscale.height)).toInt()
        // Use a threshold slightly below average to better capture dark modules
        val threshold = (avgLuminance * 0.9).toInt()
        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                val luminance = grayscale.getRGB(x, y) and 0xFF
                val newValue = if (luminance < threshold) 0x000000 else 0xFFFFFF
                thresholded.setRGB(x, y, newValue)
            }
        }
        return thresholded
    }

    private fun adaptiveThreshold(image: BufferedImage): BufferedImage {
        // Use local adaptive thresholding for better results with varying lighting/colors
        val grayscale = if (image.type == BufferedImage.TYPE_BYTE_GRAY) image else convertToGrayscale(image)
        val thresholded = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val blockSize = 15 // Size of local neighborhood
        val c = 10 // Constant subtracted from mean
        for (y in 0 until grayscale.height) {
            for (x in 0 until grayscale.width) {
                // Calculate local mean
                var sum = 0
                var count = 0
                val halfBlock = blockSize / 2
                for (dy in -halfBlock..halfBlock) {
                    for (dx in -halfBlock..halfBlock) {
                        val nx = (x + dx).coerceIn(0, grayscale.width - 1)
                        val ny = (y + dy).coerceIn(0, grayscale.height - 1)
                        sum += grayscale.getRGB(nx, ny) and 0xFF
                        count++
                    }
                }
                val localMean = sum / count
                val threshold = localMean - c
                val luminance = grayscale.getRGB(x, y) and 0xFF
                val newValue = if (luminance < threshold) 0x000000 else 0xFFFFFF
                thresholded.setRGB(x, y, newValue)
            }
        }
        return thresholded
    }


    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Save QR Image") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GenerateQRImageTask::class.java)
        @JvmStatic val GenerateQRImage = TaskType(
            "GenerateQRImage",
            "Writing",
            GenerateQRImageTask::class.java,
            GenerateQRImageTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate artistic QR codes with AI styling",
            """
              Creates stylized QR codes using AI image processing while maintaining scannability.
              <ul>
                <li>Generates QR codes with high error correction (30% redundancy)</li>
                <li>Applies artistic styles using AI image generation</li>
                <li>Verifies the resulting QR code remains readable</li>
                <li>Retries with more conservative styling if verification fails</li>
              </ul>
            """
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/GenerateSpriteSheetTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GenerateSpriteSheetTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GenerateSpriteSheetTaskExecutionConfigData?
) : AbstractFileTask<GenerateSpriteSheetTask.GenerateSpriteSheetTaskExecutionConfigData>(
    orchestrationConfig,
    planTask
) {

    class GenerateSpriteSheetTaskExecutionConfigData(
        @Description("The sprite sheet image file to be created (relative path, must end with .png)")
        files: List<String>? = null,
        @Description("The JSON metadata file to be created (relative path, must end with .json)")
        var metadata_file: String? = null,
        @Description("Detailed description of the sprites to generate (e.g., 'A pixel art warrior walking animation, 4 frames, side view')")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GenerateSpriteSheet.name,
        task_description = task_description,
        files = files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            if (files.isNullOrEmpty()) return "Sprite sheet image file must be specified"
            if (metadata_file.isNullOrBlank()) return "Metadata JSON file must be specified"
          if (!files!!.first().endsWith(".png", ignoreCase = true)) return "Image file must be .png"
          if (!metadata_file!!.endsWith(".json", ignoreCase = true)) return "Metadata file must be .json"
            return ValidatedObject.validateFields(this)
        }
    }

    data class SpriteLocation(
        @Description("Name or tag of the specific sprite")
        var name: String = "",
        @Description("X coordinate of the top-left corner")
        var x: Int = 0,
        @Description("Y coordinate of the top-left corner")
        var y: Int = 0,
        @Description("Width of the sprite")
        var width: Int = 0,
        @Description("Height of the sprite")
        var height: Int = 0
    )

    data class SpriteSheetMetadata(
        @Description("List of sprites identified in the sheet")
        var sprites: List<SpriteLocation> = emptyList(),
        @Description("General description of the sprite sheet content")
        var description: String = ""
    )

    override fun promptSegment(): String {
        return """
GenerateSpriteSheet - Create a sprite sheet image and corresponding JSON metadata
  * Generates an image containing multiple sprites based on a description
  * Automatically identifies sprite locations (x, y, width, height)
  * Outputs both a .png image and a .json metadata file
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val imageFile = executionConfig?.files?.firstOrNull() ?: return resultFn("No image file specified")
        val metadataFile = executionConfig?.metadata_file ?: return resultFn("No metadata file specified")
        val description = executionConfig?.task_description ?: "Generate a sprite sheet"

      val transcript = task.transcript()

      try {


        transcript?.write("## Generating Sprite Sheet: $imageFile\n".toByteArray())
        task.header("Generating Sprite Sheet: $imageFile", level = 2)

        task.ui.pool.submit {
          try {
            log.info("Starting sprite sheet generation for $imageFile")
            // Step 1: Generate the Image
            task.add("### Step 1: Drawing Sprites...".renderMarkdown())

            val imageGenPrompt = """
                        Create a sprite sheet based on this description: $description.
                        Requirements:
                        - The image is 1:1 aspect ratio, minimum 512x512 pixels.
                        - Arrange sprites in a grid or logical layout.
                        - CRITICAL: Use a solid MAGENTA (Hex #FF00FF) background.
                        - Ensure sprites do not overlap.
                        - Maintain consistent style and scale.
                    """.trimIndent()

            val imageAgent = ImageProcessingAgent(
              prompt = "You are a pixel artist and game asset designer.",
              name = "SpriteGenerator",
              model = orchestrationConfig.defaultImage.getChildClient(task),
            )


            val imageResult = imageAgent.answer(listOf(ImageAndText(imageGenPrompt)))
            var generatedImage = imageResult.image ?: throw RuntimeException("Failed to generate image")

            // Process transparency
            task.add("### Processing Transparency...".renderMarkdown())
            generatedImage = makeTransparent(generatedImage)

            // Preview Image
            val previewUrl = task.saveFile("previews/${File(imageFile).name}", generatedImage.toByteArray())
            task.add("""<a href="$previewUrl" target="_blank"><img src="$previewUrl" style="max-width: 100%; border: 1px solid #ccc;" /></a>""")

            // Step 2: Parse Metadata
            task.add("### Step 2: Analyzing Sprite Locations...".renderMarkdown())

            val parserAgent = ParsedImageAgent(
              resultClass = SpriteSheetMetadata::class.java,
              model = (typeConfig?.model?.let { orchestrationConfig.instance(it) }
                ?: defaultSmart).getChildClient(task),
              prompt = """
                            Identify all distinct sprites in this image.
                            Output coordinates assuming a 1000x1000 image size.
                            For each sprite, provide:
                            1. A descriptive name (e.g., 'walk_frame_1').
                            2. The exact bounding box (x, y, width, height) in pixels.
                            Ignore the background color.
                        """.trimIndent(),
            )

            val parseResult =
              parserAgent.answer(listOf(ImageAndText(text = "Extract sprite metadata.", image = generatedImage)))
            val rawMetadata = parseResult.obj
            val metadata = rawMetadata.copy(sprites = rawMetadata.sprites.map { sprite ->
              val scaledX = (sprite.x * generatedImage.width / 1000.0).toInt().coerceIn(0, generatedImage.width - 1)
              val scaledY = (sprite.y * generatedImage.height / 1000.0).toInt().coerceIn(0, generatedImage.height - 1)
              val scaledW =
                (sprite.width * generatedImage.width / 1000.0).toInt().coerceAtMost(generatedImage.width - scaledX)
              val scaledH =
                (sprite.height * generatedImage.height / 1000.0).toInt().coerceAtMost(generatedImage.height - scaledY)
              sprite.copy(x = scaledX, y = scaledY, width = scaledW, height = scaledH)
            })

            transcript?.write(
              "<details><summary>Raw Sprite Metadata</summary>\n\n```json\n${
                jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata)
              }\n```\n</details>\n".toByteArray()
            )

            // Prepare Debug View
            val debugImage = BufferedImage(generatedImage.width, generatedImage.height, BufferedImage.TYPE_INT_ARGB)
            val g = debugImage.createGraphics()
            g.drawImage(generatedImage, 0, 0, null)
            g.color = Color.RED
            g.stroke = BasicStroke(2f)
            val spriteHtml = StringBuilder()
            val individualSprites = mutableMapOf<String, BufferedImage>()

            metadata.sprites.forEach { sprite ->
              try {
                g.drawRect(sprite.x, sprite.y, sprite.width, sprite.height)
                if (sprite.width > 0 && sprite.height > 0) {
                  val subImage = generatedImage.getSubimage(sprite.x, sprite.y, sprite.width, sprite.height)
                  val safeName = sprite.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                  individualSprites[safeName] = subImage
                  val spritePreviewUrl = task.saveFile("previews/sprites/$safeName.png", subImage.toByteArray())
                  spriteHtml.append("""<div style="display:inline-block;margin:2px;border:1px solid #ccc;padding:2px"><img src="$spritePreviewUrl" title="${sprite.name}" style="max-height:50px"/></div>""")
                }
              } catch (e: Exception) {
                log.warn("Failed to process sprite ${sprite.name}", e)
              }
            }
            g.dispose()
            val debugPreviewUrl = task.saveFile("previews/debug_view.png", debugImage.toByteArray())

            // Display Results in Tabs
            val tabs = TabbedDisplay(task)
            tabs["Overview"] = """
                        <ul>
                            <li><b>Target Image:</b> $imageFile</li>
                            <li><b>Target Metadata:</b> $metadataFile</li>
                            <li><b>Sprite Count:</b> ${metadata.sprites.size}</li>
                        </ul>
                        <div style="margin-top: 10px;">
                            <img src="$previewUrl" style="max-width: 100%; border: 1px solid #ccc;" />
                        </div>
                    """.trimIndent()
            tabs["Bounding Boxes"] =
              """<img src="$debugPreviewUrl" style="max-width: 100%; border: 1px solid #ccc;" />"""
            tabs["Sprites"] = spriteHtml.toString()
            tabs["Data"] = MarkdownUtil.renderMarkdown(
              "| Name | Position | Size |\n|------|----------|------|\n" +
                metadata.sprites.joinToString("\n") { "| ${it.name} | ${it.x}, ${it.y} | ${it.width}x${it.height} |" },
              ui = task.ui
            )

            // Commit Action
            val commitAction = {
              log.info("Committing sprite sheet assets to disk...")
              val imageOutputPath = root.resolve(imageFile)
              imageOutputPath.toFile().parentFile?.mkdirs()
              ImageIO.write(generatedImage, "png", imageOutputPath.toFile())

              val jsonOutputPath = root.resolve(metadataFile)
              jsonOutputPath.toFile()
                .writeText(jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(metadata))

              val baseName = File(imageFile).nameWithoutExtension
              val parentDir = File(imageFile).parent ?: ""
              val spriteDir = root.resolve(if (parentDir.isEmpty()) baseName else "$parentDir/$baseName")
              spriteDir.toFile().mkdirs()
              individualSprites.forEach { (name, img) ->
                ImageIO.write(img, "png", spriteDir.resolve("$name.png").toFile())
              }

              task.safeComplete("Generated sprite sheet with ${metadata.sprites.size} sprites", log)
              resultFn("Generated sprite sheet: $imageFile and $metadataFile. Found ${metadata.sprites.size} sprites.")
            }

            if (orchestrationConfig.autoFix) {
              commitAction()
            } else {
              task.add(acceptButtonFooter(task.ui, commitAction).renderMarkdown())
            }

          } catch (e: Exception) {
            task.error(e)
            log.error("Error in GenerateSpriteSheetTask async execution", e)
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
            resultFn("ERROR: ${e.message}")
          }
        }


      } catch (e: Exception) {
            task.error(e)
        log.error("Critical error in GenerateSpriteSheetTask", e)
        transcript?.write("\n## Critical Error\n```\n${e.message}\n```\n".toByteArray())
            resultFn("ERROR: ${e.message}")
      } finally {
        transcript?.close()
        }
    }

  private fun BufferedImage.toByteArray(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    ImageIO.write(this, "png", out)
    return out.toByteArray()
  }

  private fun makeTransparent(source: BufferedImage): BufferedImage {
        // 1. Determine Background Color (assume top-left pixel is background)
        val bgRgb = source.getRGB(0, 0)
        val bgColor = Color(bgRgb)
        val width = source.width
        val height = source.height
        val dest = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        // Configuration for "Soft Keying"
        val tolerance = 15.0 // Pixels this close to BG color are fully transparent
        val softness = 60.0  // The fade-in range (gradient)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val rgb = source.getRGB(x, y)
                val c = Color(rgb)
                // 2. Calculate Euclidean distance in RGB space
                val distance = Math.sqrt(
                    Math.pow((c.red - bgColor.red).toDouble(), 2.0) +
                            Math.pow((c.green - bgColor.green).toDouble(), 2.0) +
                            Math.pow((c.blue - bgColor.blue).toDouble(), 2.0)
                )
                // 3. Calculate Alpha based on distance
                var alpha: Int
                if (distance < tolerance) {
                    alpha = 0 // Fully transparent
                } else if (distance < tolerance + softness) {
                    // Gradient / Anti-aliased edge
                    val factor = (distance - tolerance) / softness
                    alpha = (factor * 255).toInt().coerceIn(0, 255)
                } else {
                    alpha = 255 // Fully opaque
                }
                // 4. Reconstruct Pixel
                // Note: For high-end spill removal, you would adjust RGB here to un-mix the background.
                // For now, keeping original RGB with calculated Alpha is usually sufficient for sprites.
                val newCol = (alpha shl 24) or (c.red shl 16) or (c.green shl 8) or c.blue
                dest.setRGB(x, y, newCol)
            }
        }
        return dest
    }


    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        return ui.hrefLink("Accept Sprite Sheet") { fn() }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GenerateSpriteSheetTask::class.java)
        @JvmStatic val GenerateSpriteSheet = TaskType(
          name = "GenerateSpriteSheet",
          category = "Writing",
          taskClass = GenerateSpriteSheetTask::class.java,
          executionConfigClass = GenerateSpriteSheetTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate a sprite sheet and associated JSON metadata",
          tooltipHtml = """
                        Creates game assets by generating a sprite sheet image and extracting coordinate data.
                        <ul>
                          <li>Generates visual sprite sheet using AI image models</li>
                          <li>Analyzes the generated image to find sprite bounding boxes</li>
                          <li>Exports standard JSON metadata for game engine integration</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/IllustrateDocumentTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

class IllustrateDocumentTask(
    orchestrationConfig: OrchestrationConfig, planTask: IllustrateDocumentTaskExecutionConfigData?
) : AbstractFileTask<IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData>(orchestrationConfig, planTask) {

    data class ImageSuggestion(
        @Description("Descriptive name for the image file (without extension)") val imageName: String = "",
        @Description("Detailed prompt for generating the image") val imagePrompt: String = "",
        @Description("Location in document where image should be inserted (section heading or paragraph start)") val insertionPoint: String = "",
        @Description("Caption or alt text for the image") val caption: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (imageName.isBlank()) return "ImageSuggestion imageName cannot be blank"
            if (imagePrompt.isBlank()) return "ImageSuggestion imagePrompt cannot be blank"
            if (insertionPoint.isBlank()) return "ImageSuggestion insertionPoint cannot be blank"
            return null
        }
    }

    data class DocumentAnalysis(
        @Description("List of suggested images to enhance the document") val suggestions: List<ImageSuggestion> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (suggestions.isEmpty()) return "DocumentAnalysis must contain at least one suggestion"
            suggestions.forEach { suggestion ->
                suggestion.validate()?.let { return it }
            }
            return null
        }
    }

    class IllustrateDocumentTaskExecutionConfigData(
        @Description("The document file to illustrate (must be .md or .html)") files: List<String>? = null,
        @Description("Maximum number of images to generate (default: 5)") val maxImages: Int = 5,
        @Description("Image format to use (png or jpg, default: png)") val imageFormat: String = "png",
        @Description("Whether to automatically insert image references into the document") val autoInsert: Boolean = true,
        @Description("Additional instructions to append to image generation prompts (e.g., style preferences, constraints)") val imageInstructions: String? = null,
        @Description("Directive for the image composer on how to generate images (e.g., 'Generate a background wallpaper', 'Create hero images', 'Focus on technical diagrams')") val composerDirective: String? = null,
        @Description("Directive for the image integrator on how to insert images (e.g., 'Insert as page background', 'Place in sidebars', 'Create galleries')") val integratorDirective: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = IllustrateDocument.name,
        task_description = task_description,
        files = files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            if (files.isNullOrEmpty()) {
                return "IllustrateDocumentTask requires exactly one file to be specified"
            }
          if (files!!.size > 1) {
                return "IllustrateDocumentTask can only process one file at a time"
            }
          val file = files!!.first()
            if (!file.matches(Regex(".*\\.(md|html)$", RegexOption.IGNORE_CASE))) {
                return "IllustrateDocumentTask file must have .md or .html extension: $file"
            }
            if (maxImages < 1 || maxImages > 20) {
                return "maxImages must be between 1 and 20"
            }
            if (imageFormat !in listOf("png", "jpg", "jpeg")) {
                return "imageFormat must be 'png', 'jpg', or 'jpeg'"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
IllustrateDocument - Analyze a document and generate images to enhance its content
  - Specify a markdown or HTML file to illustrate
  - Configure maximum number of images (default: 5)
  - Choose image format (png/jpg)
  - Analyzes document structure and content
  - Generates contextually appropriate images
  - Saves images with descriptive names in the same folder
  - Optionally inserts image references at appropriate locations
""".trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
      task.ui.pool.submit {
        val startTime = System.currentTimeMillis()
        val documentFile = executionConfig?.files?.firstOrNull()
        val transcript = task.transcript()

        try {
          if (documentFile == null) {
            val errorMsg = "CONFIGURATION ERROR: No document file specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return@submit
          }

          val documentPath = root.resolve(documentFile)
          if (!documentPath.toFile().exists()) {
            val errorMsg = "ERROR: Document file not found: $documentFile"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return@submit
          }

          val maxImages = executionConfig.maxImages.coerceIn(1, 20)
          val imageFormat = executionConfig.imageFormat.lowercase()
          val autoInsert = executionConfig.autoInsert

          log.info("Starting IllustrateDocumentTask for: $documentFile (maxImages=$maxImages, format=$imageFormat, autoInsert=$autoInsert)")
          transcript?.write("## Illustrating Document: $documentFile\n".toByteArray())

          val ui = task.ui
          val tabs = TabbedDisplay(task)

          // Read document content
          val documentContent = documentPath.toFile().readText()
          val isMarkdown = documentFile.endsWith(".md", ignoreCase = true)

          transcript?.write(
            """
                    <details>
                    <summary>Raw Document Content</summary>
                    
                    ```
                    $documentContent
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )

          val overviewTask = tabs.newTask("Overview")
          overviewTask.header("Illustrating Document: $documentFile", level = 2)
          overviewTask.add(
            """
                    * **Format:** ${if (isMarkdown) "Markdown" else "HTML"}
                    * **Max Images:** $maxImages
                    * **Image Format:** $imageFormat
                """.trimIndent().renderMarkdown()
          )

          if (!executionConfig.composerDirective.isNullOrBlank()) {
            overviewTask.add("**Composer Directive:** ${executionConfig.composerDirective}".renderMarkdown())
          }
          if (!executionConfig.integratorDirective.isNullOrBlank()) {
            overviewTask.add("**Integrator Directive:** ${executionConfig.integratorDirective}".renderMarkdown())
          }

          // Step 1: Analyze document and suggest images
          log.info("Analyzing document to suggest images for $documentFile")
          val analysisTask = tabs.newTask("Analysis")
          analysisTask.header("🔍 Analyzing Document", level = 3)
          analysisTask.add("Identifying sections that would benefit from visual enhancement...".renderMarkdown())

          val analysisPrompt = buildAnalysisPrompt(
            documentContent, maxImages, isMarkdown, executionConfig.composerDirective
          )

          val api = defaultSmart ?: return@submit
          val parsingChatter = defaultFast.getChildClient(analysisTask)
          val defaultChatter = api.getChildClient(analysisTask)

          val analysisAgent = ParsedAgent(
            resultClass = DocumentAnalysis::class.java,
            prompt = analysisPrompt,
            model = defaultChatter,
            temperature = 0.5,
            parsingChatter = parsingChatter
          )

          val analysis = analysisAgent.answer(listOf(analysisPrompt))
          val suggestions = analysis.obj.suggestions.take(maxImages)

          log.info("Generated ${suggestions.size} image suggestions for $documentFile")
          transcript?.write(
            """
                    <details>
                    <summary>Image Suggestions Analysis</summary>
                    
                    ```json
                    ${JsonUtil.toJson(analysis.obj)}
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )

          analysisTask.add("✅ Identified **${suggestions.size}** opportunities for images".renderMarkdown())

          // Display suggestions
          analysisTask.header("📋 Planned Images", level = 3)
          suggestions.forEachIndexed { index, suggestion ->
            analysisTask.add(
              """
                        ### ${index + 1}. ${suggestion.imageName}
                        * **Location:** ${suggestion.insertionPoint}
                        * **Caption:** ${suggestion.caption}
                    """.trimIndent().renderMarkdown()
            )
          }

          if (!orchestrationConfig.autoFix) {
            val semaphore = Semaphore(0)
            analysisTask.header("✋ Approval Required", level = 3)
            analysisTask.add("Please review the planned images above.".renderMarkdown())
            analysisTask.add(ui.hrefLink("🚀 Proceed with Generation", "btn btn-primary") {
              semaphore.release()
            })
            semaphore.acquire()
            analysisTask.add("✅ **User Approved**. Starting generation...".renderMarkdown())
          }


          // Step 2: Generate images
          log.info("Generating ${suggestions.size} images for $documentFile")
          val generationTask = tabs.newTask("Generation")
          generationTask.header("🎨 Generating Images", level = 3)

          val imageAgent = ImageProcessingAgent(
            prompt = "Transform the user request into an image that enhances document content",
            name = "DocumentIllustrator",
            model = orchestrationConfig.defaultImage,
          )

          val generatedImages = mutableListOf<Triple<String, String, ImageSuggestion>>()
          val documentFolder = documentPath.parent

          suggestions.forEachIndexed { index, suggestion ->
            try {
              generationTask.header("Generating: ${suggestion.imageName}", level = 4)

              // Build enhanced prompt with all supplemental instructions
              val enhancedPrompt = buildString {
                append(suggestion.imagePrompt)
                if (!executionConfig.composerDirective.isNullOrBlank()) {
                  append("\n\nComposer Directive: ${executionConfig.composerDirective}")
                }
                if (!executionConfig.imageInstructions.isNullOrBlank()) {
                  append("\n\nAdditional Instructions: ${executionConfig.imageInstructions}")
                }
              }

              val result = imageAgent.answer(listOf(ImageAndText(enhancedPrompt)))
              val generatedImage = result.image

              val sanitizedName =
                suggestion.imageName.replace(Regex("[^a-zA-Z0-9_-]"), "_").replace(Regex("_+"), "_").trim('_')
              val imageFileName = "${sanitizedName}.$imageFormat"
              val imagePath = documentFolder.resolve(imageFileName)

              ImageIO.write(generatedImage, imageFormat, imagePath.toFile())
              log.info("Saved image: $imageFileName")

              val previewFile = task.resolveUserFile(imageFileName)
              ImageIO.write(generatedImage, imageFormat, previewFile!!)
              val previewLink = task.linkTo(imageFileName)
              generationTask.add("""<a href="$previewLink" target="_blank"><img src="$previewLink" style="max-width: 400px; border-radius: 4px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);" /></a>""")
              generationTask.add("✅ Saved as ` $imageFileName `".renderMarkdown())

              generatedImages.add(Triple(imageFileName, imagePath.toString(), suggestion))


            } catch (e: Exception) {
              log.error("Failed to generate image: ${suggestion.imageName}", e)
              generationTask.add(
                "❌ Failed to generate: ${suggestion.imageName} - ${e.message}".renderMarkdown(),
                additionalClasses = "text-danger"
              )
            }
          }
          val integrationTask = tabs.newTask("Integration")
          integrationTask.header("📝 Generating Document Patches", level = 3)
          integrationTask.complete(
            generateImageInsertionPatches(
              documentContent,
              generatedImages,
              isMarkdown,
              executionConfig.integratorDirective,
              integrationTask,
              defaultChatter,
              documentFile
            ) ?: ""
          )
          val totalTime = System.currentTimeMillis() - startTime
          val summary = buildString {
            appendLine("# Document Illustration Complete")
            appendLine()
            appendLine("**Document:** $documentFile")
            appendLine()
            appendLine("**Images Generated:** ${generatedImages.size}")
            appendLine()
            appendLine("**Time:** ${totalTime / 1000.0}s")
            appendLine()
            if (autoInsert) {
              appendLine("**Status:** Document updated with image references")
            } else {
              appendLine("**Status:** Images generated (manual insertion required)")
            }
            appendLine()
            appendLine("## Generated Images")
            appendLine()
            generatedImages.forEach { (fileName, _, suggestion) ->
              appendLine("- **$fileName**: ${suggestion.caption}")
            }
          }

          log.info("IllustrateDocumentTask completed for $documentFile in ${totalTime}ms")
          task.safeComplete("Generated ${generatedImages.size} images in ${totalTime / 1000}s", log)
          resultFn(summary)

        } catch (e: Exception) {
          val duration = System.currentTimeMillis() - startTime
          log.error("IllustrateDocumentTask failed for $documentFile after ${duration}ms", e)
          task.error(e)
          transcript?.write(
            """
                    <details>
                    <summary>Stack Trace</summary>
                    
                    ```
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )

          val errorOutput = buildString {
            appendLine("# Error Illustrating Document")
            appendLine()
            appendLine("**Document:** $documentFile")
            appendLine()
            appendLine("**Error:** ${e.message}")
            appendLine()
            appendLine("**Type:** ${e.javaClass.simpleName}")
          }
          task.safeComplete("Document illustration failed: ${e.message}", log)
          resultFn(errorOutput)
        } finally {
          transcript?.close()
        }


      }
    }

    private fun buildAnalysisPrompt(
        documentContent: String, maxImages: Int, isMarkdown: Boolean, composerDirective: String?
    ): String {
        val formatInfo = if (isMarkdown) "Markdown" else "HTML"
        val directiveSection = if (!composerDirective.isNullOrBlank()) {
            """
## Composer Directive:
${composerDirective}
**Important:** Follow this directive when suggesting images and creating prompts.
""".trimIndent()
        } else {
            ""
        }

        return """
You are a document enhancement expert. Analyze this $formatInfo document and suggest images that would enhance its content.
${directiveSection}

## Document Content:
```
${documentContent.take(10000)}
```

## Your Task:
Identify up to $maxImages locations in the document where images would add significant value. For each suggestion:

1. **imageName**: Create a descriptive, filesystem-safe name (e.g., "user_authentication_flow", "data_pipeline_diagram")
2. **imagePrompt**: Write a detailed prompt for generating the image, including:
    * Subject matter and key elements
    * Visual style (diagram, illustration, photo-realistic, etc.)
    * Color scheme and mood
    * Specific details that match the document context
3. **insertionPoint**: Identify where to insert the image by providing:
    * The exact heading text, or
    * The first few words of the paragraph where it should appear
4. **caption**: Write a clear, informative caption or alt text

## Guidelines:
    * Prioritize sections with complex concepts that benefit from visualization
    * Consider diagrams for processes, workflows, and architectures
    * Suggest illustrations for abstract concepts
    * Ensure images complement rather than duplicate text content
    * Focus on high-impact locations that enhance understanding
    * Make image prompts specific and detailed for best results

Generate suggestions now.
""".trimIndent()
    }

    private fun generateImageInsertionPatches(
        documentContent: String,
        images: List<Triple<String, String, ImageSuggestion>>,
        isMarkdown: Boolean,
        integratorDirective: String?,
        task: SessionTask,
        chatChatter: ChatInterface,
        documentFile: String
    ): String? {
        val semaphore = Semaphore(0)
        var patchResult: String? = null
        try {
            val formatInfo = if (isMarkdown) "Markdown" else "HTML"
            val directiveSection = if (!integratorDirective.isNullOrBlank()) {
                """
## Integrator Directive:
${integratorDirective}
**Important:** Follow this directive when inserting images into the document.
""".trimIndent()
            } else {
                ""
            }
            val imageList = images.joinToString("\n") { (fileName, _, suggestion) ->
                """
* **File**: $fileName
**Caption**: ${suggestion.caption}
**Suggested Location**: ${suggestion.insertionPoint}
**Context**: ${suggestion.imagePrompt.take(200)}
""".trimIndent()
            }
            val patchPrompt = """
You are a document integration expert. Insert image references into this $formatInfo document using diff patches.
${directiveSection}
## Document Content:
```
${documentContent}
```
## Images to Insert:
 ${imageList}
**IMPORTANT**: All images are located in the SAME DIRECTORY as the document.
Use ONLY the filename (e.g., "image.png") without any path prefix.
Do NOT use paths like "/assets/images/" or any other directory structure.

 ## Your Task:
 Generate diff patches to insert image references at appropriate locations in the document.
For ${if (isMarkdown) "Markdown" else "HTML"} format:
${if (isMarkdown) "- Use EXACTLY: ![caption](filename) where filename is the exact filename provided" else "- Use EXACTLY: <img src=\"filename\" alt=\"caption\" /> where filename is the exact filename provided"}

**CRITICAL**: Use ONLY the exact filenames provided in the image list above. Do NOT add any path prefixes like "/assets/images/" or any other directory paths.
The images are saved in the same directory as the document, so use relative filenames only.

**WRONG**: ![caption](/assets/images/filename.png)
**CORRECT**: ![caption](filename.png)

* Include 2-3 lines of context before and after each insertion
* Place images where they enhance understanding
* Ensure proper spacing and formatting
* Consider document flow and readability
Response format:
Use ```diff code blocks with a header specifying the file path.
The diff format should use + for line additions, - for line deletions.
Include 2 lines of context before and after every change.
Example:
 ### $documentFile
 ```diff
 existing line 1
 existing line 2
+![Image Caption](exact_filename_from_list.png)

+
existing line 3
existing line 4
```
Generate the patches now.
""".trimIndent()
            val subTask = task.newTask().apply { add("Generating patches...") }
            subTask.ui.pool.submit {
                try {
                    val chatAgent = ChatAgent(
                        name = "DocumentImageIntegrator", prompt = patchPrompt, model = chatChatter, temperature = 0.3
                    )
                    val response = chatAgent.answer(listOf(patchPrompt))
                    log.debug("Patch generation response: $response")
                    if (orchestrationConfig.autoFix) {
                        subTask.complete(MarkdownUtil.renderMarkdown(response, ui = subTask.ui) {
                            AddApplyFileDiffLinks.instrumentFileDiffs(
                                subTask.ui,
                                root = root,
                                response = it,
                                handle = { newCodeMap ->
                                    newCodeMap.forEach { (path, _) ->
                                        log.info("Applied patch to: $path")
                                    }
                                    patchResult = "Patches applied successfully"
                                },
                                shouldAutoApply = { true },
                                model = chatChatter,
                                defaultFile = documentFile,
                                orchestrationConfig.processor
                            ) + "\n\n## Auto-applied image insertion patches"
                        })
                        semaphore.release()
                    } else {
                        subTask.complete(MarkdownUtil.renderMarkdown(response, ui = subTask.ui) {
                            AddApplyFileDiffLinks.instrumentFileDiffs(
                                subTask.ui,
                                root = root,
                                response = it,
                                handle = { newCodeMap ->
                                    newCodeMap.forEach { (path, _) ->
                                        log.info("Applied patch to: $path")
                                    }
                                    patchResult = "Patches applied successfully"
                                },
                                model = chatChatter,
                                defaultFile = documentFile,
                                processor = orchestrationConfig.processor
                            ) + acceptButtonFooter(subTask.ui) {
                                subTask.complete()
                                semaphore.release()
                            }
                        })
                    }
                } catch (e: Exception) {
                    log.error("Failed to generate or apply patches", e)
                    subTask.error(e)
                    semaphore.release()
                }
            }
// Wait for completion
            if (!semaphore.tryAcquire(5, java.util.concurrent.TimeUnit.MINUTES)) {
                log.warn("Patch generation timed out")
                return null
            }
            return patchResult
        } catch (e: Exception) {
            log.error("Error in patch generation process", e)
            return null
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(IllustrateDocumentTask::class.java)

        @JvmStatic val IllustrateDocument = TaskType(
          name = "IllustrateDocument",
          category = "Writing",
          taskClass = IllustrateDocumentTask::class.java,
          executionConfigClass = IllustrateDocumentTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Analyze a document and generate images to enhance its content",
          tooltipHtml = """
          Intelligently analyzes document content and generates contextually appropriate images.
          <ul>
          <li>Analyzes document structure to identify optimal image locations</li>
          <li>Generates images that enhance understanding of complex concepts</li>
          <li>Saves images with descriptive names in the document's folder</li>
          <li>Automatically inserts image references at appropriate locations</li>
          <li>Supports both Markdown and HTML formats</li>
          <li>Creates diagrams, illustrations, and visual aids</li>
          <li>Provides meaningful captions and alt text</li>
          <li>Configurable image count and format</li>
          </ul>
          """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/ImageDecompositionTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.RenderableDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import javax.imageio.ImageIO

class ImageDecompositionTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ImageDecompositionConfig?
) : AbstractFileTask<ImageDecompositionTask.ImageDecompositionConfig>(
  orchestrationConfig,
  planTask
) {

  class ImageDecompositionConfig(
    @Description("The image file to analyze (relative path)")
    files: List<String>? = null,
    @Description("The goal of the analysis (e.g., 'Find Waldo', 'Read all text', 'Describe every person')")
    var segmentation_query: String? = "Describe the contents of this image in detail",
    @Description("The specific query for detailed analysis of identified regions")
    var analysis_query: String? = "Describe the contents of this image in detail",
    @Description("DPI for rendering document pages (default: 150)")
    var dpi: Float = 150f,
    @Description("Maximum recursion depth (1-5). Higher is slower but more detailed.")
    var max_depth: Int = 2,
    @Description("Minimum width/height (in pixels) of a region to trigger recursive analysis.")
    var min_region_size: Int = 100,
    @Description("The output JSON file to save the analysis tree to.")
    var output_file: String? = "analysis_result.json",
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = ImageDecomposition.name,
    task_description = segmentation_query,
    files = files,
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (files.isNullOrEmpty()) return "Input image file must be specified"
      if (output_file.isNullOrBlank()) return "Output JSON file must be specified"
      if (max_depth < 1) return "Max depth must be at least 1"
      return ValidatedObject.Companion.validateFields(this)
    }
  }

  // Data classes for the LLM interaction
  class IdentifiedRegion(
    @Description("A descriptive label or text content found in this region")
    val label: String = "",
    @Description("Detailed description of what is happening or visible in this specific region")
    val description: String = "",
    @Description("True if this region requires further zooming/recursion to answer the query")
    val requires_zoom: Boolean = false
  ) : ImageBounds()

  data class RegionAnalysisResult(
    @Description("List of distinct regions identified in the image")
    val regions: List<IdentifiedRegion> = emptyList(),
    @Description("Summary of the current view")
    val summary: String = ""
  )

  data class LeafAnalysisResult(
    @Description("Detailed analysis of the image segment")
    val analysis: String = ""
  )

  // Internal data structure for the final tree
  data class AnalysisNode(
    val id: String,
    val label: String,
    val description: String,
    val bounds: ImageBounds, // Global coordinates
    val depth: Int,
    val children: MutableList<AnalysisNode> = mutableListOf(),
    var analysis: String? = null
  )

  open class ImageBounds(
    @Description("X coordinate of the top-left corner (0-1000 scale relative to parent)")
    var x: Int = 0,
    @Description("Y coordinate of the top-left corner (0-1000 scale relative to parent)")
    var y: Int = 0,
    @Description("Width of the region (0-1000 scale relative to parent)")
    var width: Int = 0,
    @Description("Height of the region (0-1000 scale relative to parent)")
    var height: Int = 0,
  ) {
    fun isValid(
      currentImage: BufferedImage,
      minSize: Int
    ): Boolean {
      val localX = (x * currentImage.width / 1000.0).toInt()
      val localY = (y * currentImage.height / 1000.0).toInt()
      val localW = (width * currentImage.width / 1000.0).toInt()
      val localH = (height * currentImage.height / 1000.0).toInt()
      val safeX = localX.coerceIn(0, currentImage.width - 1)
      val safeY = localY.coerceIn(0, currentImage.height - 1)
      val safeW = localW.coerceAtMost(currentImage.width - safeX)
      val safeH = localH.coerceAtMost(currentImage.height - safeY)
      return localW > minSize && localH > minSize && safeW > 0 && safeH > 0
    }

    fun subImage(currentImage: BufferedImage): BufferedImage {
      val localX = (x * currentImage.width / 1000.0).toInt()
      val localY = (y * currentImage.height / 1000.0).toInt()
      val localW = (width * currentImage.width / 1000.0).toInt()
      val localH = (height * currentImage.height / 1000.0).toInt()
      val safeX = localX.coerceIn(0, currentImage.width - 1)
      val safeY = localY.coerceIn(0, currentImage.height - 1)
      val safeW = localW.coerceAtMost(currentImage.width - safeX)
      val safeH = localH.coerceAtMost(currentImage.height - safeY)
      return currentImage.getSubimage(safeX, safeY, safeW, safeH)
    }
  }

  override fun promptSegment() = """
    IterativeImageDecomposition - Recursively analyzes images for fine details.
    * Use for: OCR on complex documents, finding small objects, or detailed scene analysis.
    * Inputs: Image file path, segmentation query, and analysis query.
    * Outputs: A hierarchical JSON report and annotated debug images.
    * Mechanism: Recursively crops and re-prompts the vision model on regions of interest.
      """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val imagePath = executionConfig?.files?.firstOrNull() ?: return resultFn("No image file specified")
    val segmentation_query = executionConfig.segmentation_query ?: "Analyze image"
    val analysis_query = executionConfig.analysis_query ?: "Describe the contents of this image in detail"
    val maxDepth = executionConfig.max_depth
    val minSize = executionConfig.min_region_size
    val outputFile = executionConfig.output_file ?: "analysis.json"

    val transcript = task.transcript()
    val tabs = TabbedDisplay(task)
    val logTab = tabs.newTask("Live Log")

    task.ui.pool.submit {
      log.info("Starting ImageDecompositionTask for $imagePath")
      try {
        task.add("## Starting Iterative Analysis\nProcessing image: `$imagePath`".renderMarkdown())
        transcript?.write("## Image Decomposition Analysis: $imagePath\n".toByteArray())
        logTab.add("Loading image...".renderMarkdown())

        val inputFile = root.resolve(imagePath).toFile()
        if (!inputFile.exists()) throw RuntimeException("File not found: $inputFile")


        val pages: Int = inputFile.getDocumentReader().use { reader ->
          if (reader is RenderableDocumentReader) {
            reader.getPageCount()
          } else {
            1
          }
        }

        val allNodes = ConcurrentLinkedQueue<AnalysisNode>()
        val rootNodes = mutableListOf<AnalysisNode>()
        val model =
          (typeConfig?.model?.let { orchestrationConfig.instance(it) } ?: defaultSmart).getChildClient(task)

        (0 until pages).forEach { page ->
          logTab.add("Processing page ${page + 1} of $pages".renderMarkdown())
          transcript?.write(
            "\n## Page ${page + 1} of $pages\n".toByteArray()
          )
          val originalImage = loadImage(inputFile, executionConfig, page)

          // Root node of our analysis tree
          val rootID = if (pages > 1) "page_$page" else "root"
          val rootNode = AnalysisNode(
            id = rootID,
            label = "Full Image",
            description = "Root level analysis",
            bounds = ImageBounds(0, 0, originalImage.width, originalImage.height),
            depth = 0
          )

          // Shared list for visualization later
          allNodes.add(rootNode)
          rootNodes.add(rootNode)

          fun analyzeLeaf(image: BufferedImage, node: AnalysisNode) {
            try {
              logTab.add("Analyzing leaf node: ${node.label}".renderMarkdown())
              val result = ParsedImageAgent(
                resultClass = LeafAnalysisResult::class.java,
                model = model,
                prompt = "Analyze this image segment for the query: \"$analysis_query\"",
              ).answer(listOf(ImageAndText(text = "", image = image))).obj
              node.analysis = result.analysis
              transcript?.write(
                """

### Leaf Analysis - ${node.label}
${result.analysis}
""".toByteArray()
              )
            } catch (e: Exception) {
              log.error("Error analyzing leaf node ${node.label}", e)
              transcript?.write(
                "Error analyzing leaf node ${node.label}: ${e.message}\n".toByteArray()
              )
            }
          }


          // Recursive function
          fun processRegion(
            currentImage: BufferedImage,
            currentNode: AnalysisNode,
            currentDepth: Int,
            globalOffsetX: Int,
            globalOffsetY: Int
          ) {
            if (currentDepth >= maxDepth) {
              transcript?.write(
                "Reached max depth at node ${currentNode.label}, performing leaf analysis.\n".toByteArray()
              )
              analyzeLeaf(currentImage, currentNode)
              return
            }

            try {
              logTab.add("Analyzing depth $currentDepth: ${currentNode.label} (${currentNode.bounds.width}x${currentNode.bounds.height})".renderMarkdown())
              transcript?.write(
                """
### Depth $currentDepth - ${currentNode.label}
Analyzing region at depth $currentDepth with bounds (${currentNode.bounds.x}, ${currentNode.bounds.y}, ${currentNode.bounds.width}, ${currentNode.bounds.height})
""".toByteArray()
              )

              val result = ParsedImageAgent(
                resultClass = RegionAnalysisResult::class.java,
                model = model,
                prompt = """
Analyze this image segment for the query: "$segmentation_query".
Identify distinct regions that are relevant or contain dense information.
Output coordinates on a 0-1000 scale relative to this specific image segment.
If a region looks like it contains smaller details (text, faces, objects) that are hard to see, mark 'requires_zoom' as true.
                    """
              ).answer(listOf(ImageAndText(text = "", image = currentImage))).obj

              // Log raw result to transcript
              transcript?.write(
                """

                ### Depth $currentDepth - ${currentNode.label}
                <details>
                <summary>Raw Region Analysis JSON</summary>

                ```json
                ${result.toJson()}
                ```
                </details>
                        """.toByteArray()
              )

              if (result.regions.isNotEmpty()) {
                result.regions.forEachIndexed { index, region: IdentifiedRegion ->
                  val localX = (region.x * currentImage.width / 1000.0).toInt()
                  val localY = (region.y * currentImage.height / 1000.0).toInt()
                  val localW = (region.width * currentImage.width / 1000.0).toInt()
                  val localH = (region.height * currentImage.height / 1000.0).toInt()
                  val globalX = globalOffsetX + localX
                  val globalY = globalOffsetY + localY

                  val childNode = AnalysisNode(
                    id = "${currentNode.id}_$index",
                    label = region.label,
                    description = region.description,
                    bounds = ImageBounds(globalX, globalY, localW, localH),
                    depth = currentDepth + 1
                  )
                  currentNode.children.add(childNode)
                  allNodes.add(childNode)


                  val canRecurse = (currentDepth + 1) <= maxDepth
                  val validForRecursion = region.isValid(currentImage, minSize)
                  val validForAnalysis = region.isValid(currentImage, 0)

                  if (region.requires_zoom && canRecurse && validForRecursion) {
                    val subImage = region.subImage(currentImage)
                    task.resolveUserFile("${currentNode.id}_$index.png")?.also { imgFile ->
                      ImageIO.write(subImage, "png", imgFile)
                      val imgLink = task.linkTo(imgFile.name)
                      logTab.add(
                        """Saved sub-image for ${childNode.label} at depth ${currentDepth + 1}: 
                                                                  |<a href="$imgLink" target="_blank">${imgFile.name}</a>""".trimMargin()
                          .renderMarkdown()
                      )
                      transcript?.write(
                        """
                    |
                    |#### Sub-image for ${childNode.label} at depth ${currentDepth + 1} 
                    |<a href="$imgLink" target="_blank"><img src="$imgLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
                    |""".trimMargin().toByteArray()
                      )
                    }
                    processRegion(subImage, childNode, currentDepth + 1, globalX, globalY)
                  } else if (validForAnalysis) {
                    if (region.requires_zoom && !validForRecursion) {
                      logTab.add("Skipped recursion for ${childNode.label} (too small) at depth ${currentDepth + 1}".renderMarkdown())
                      transcript?.write(
                        "Skipped recursion for ${childNode.label} (too small) at depth ${currentDepth + 1}\n".toByteArray()
                      )
                    }
                    analyzeLeaf(region.subImage(currentImage), childNode)
                  }
                }
              } else {
                logTab.add("No regions identified at depth $currentDepth for ${currentNode.label}".renderMarkdown())
                transcript?.write("No regions identified at depth $currentDepth for ${currentNode.label}\n".toByteArray())
                analyzeLeaf(currentImage, currentNode)
              }
            } catch (e: Exception) {
              log.error("Error processing region at depth $currentDepth for ${currentNode.label}", e)
              transcript?.write(
                "Error processing region at depth $currentDepth for ${currentNode.label}: ${e.message}\n".toByteArray()
              )
            }
          }
          processRegion(originalImage, rootNode, 0, 0, 0)

          task.add("### Generating Visual Report...".renderMarkdown())

          // 1. Draw Debug Image
          val debugImage = BufferedImage(originalImage.width, originalImage.height, BufferedImage.TYPE_INT_ARGB)
          val g = debugImage.createGraphics()
          g.drawImage(originalImage, 0, 0, null)

          // Color map for depth
          val depthColors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.ORANGE, Color.MAGENTA)

          allNodes.forEach { node ->
            if (node.depth > 0) { // Don't draw root
              g.color = depthColors[node.depth % depthColors.size]
              g.stroke = BasicStroke((maxDepth - node.depth + 1).toFloat()) // Thicker lines for higher levels
              g.drawRect(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height)

              // Draw label background
              if (node.bounds.width > 20) {
                g.color = Color(0, 0, 0, 150)
                g.fillRect(node.bounds.x, node.bounds.y, node.bounds.width.coerceAtMost(100), 15)
                g.color = Color.WHITE
                g.font = g.font.deriveFont(10f)
                g.drawString(node.label, node.bounds.x + 2, node.bounds.y + 12)
              }
            }
          }
          g.dispose()

          val debugFileName = "${File(imagePath).nameWithoutExtension}_${rootID}_analysis.png"
          ImageIO.write(debugImage, "png", root.resolve(debugFileName).toFile())
          val debugLink = task.linkTo(debugFileName)

          // 3. Update UI Tabs
          tabs["Visual Analysis"] = """
                    <a href="$debugLink" target="_blank"><img src="$debugLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
                """.trimIndent()

          // 4. Flattened Summary for LLM
          val flatSummary = allNodes.joinToString("\n") { node ->
            val analysisText = node.analysis?.let { " Analysis: $it" } ?: ""
            if (node.depth == 0) "Root Image: ${node.description}$analysisText" else
              "- [Depth ${node.depth}] ${node.label} at (${node.bounds.x},${node.bounds.y}): ${node.description}$analysisText"
          }

          tabs["Summary"] = MarkdownUtil.renderMarkdown(flatSummary, ui = task.ui)
        }


        val jsonOutput = rootNodes.toJson()
        root.resolve(outputFile).toFile().writeText(jsonOutput)
        tabs["Structured Data"] = """
            <p>Full analysis saved to <a href="${task.linkTo(outputFile)}">$outputFile</a></p>
            <details>
            <summary>Raw JSON Tree</summary>
            <pre style="max-height: 400px; overflow: auto;">$jsonOutput</pre>
            </details>
        """.trimIndent()

        task.add("### Finalizing Analysis...".renderMarkdown())
        val finalResult = ChatAgent(
          model = model,
          prompt = """Review the following hierarchical analysis of the image regions and answer the query: "$analysis_query" """.trimIndent()
        ).answer(listOf(rootNodes.toJson()))

        tabs["Final Report"] = MarkdownUtil.renderMarkdown(finalResult, ui = task.ui)
        task.safeComplete("### Analysis Complete\nFound **${allNodes.size - 1}** regions.".renderMarkdown(), log)
        root.resolve("final_analysis_${WaterfallMode.Companion.now()}.md").toFile().writeText(finalResult)

        log.info("ImageDecompositionTask completed successfully for $imagePath")
        resultFn(
          """
            ## Image Analysis Complete
            * **Source:** `$imagePath`
            * **Regions Identified:** ${allNodes.size - 1}
            * **Structured Data:** `$outputFile`
            
            ### Summary
            $finalResult
        """.trimIndent()
        )

      } catch (e: Exception) {
        log.error("Error in IterativeImageDecomposition", e)
        task.error(e)
        transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
        resultFn("Error analyzing image: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }


  private fun loadImage(
    inputFile: File,
    executionConfig: ImageDecompositionConfig,
    page: Int
  ): BufferedImage {
    var originalImage: BufferedImage? = null
    try {
      inputFile.getDocumentReader().use { reader ->
        if (reader is RenderableDocumentReader) {
          originalImage = reader.renderImage(page, executionConfig.dpi)
        }
      }
    } catch (e: Throwable) {
      log.warn("Error reading as document: ${e.message}")
    }
    if (originalImage == null) {
      originalImage = ImageIO.read(inputFile)
    }
    if (originalImage == null) throw RuntimeException("Failed to load image from $inputFile")
    return originalImage
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    return ui.hrefLink("Accept Analysis") { fn() }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ImageDecompositionTask::class.java)
    @JvmStatic val ImageDecomposition = TaskType(
      name = "ImageDecomposition",
      category = "File",
      taskClass = ImageDecompositionTask::class.java,
      executionConfigClass = ImageDecompositionConfig::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Recursively analyze an image to find details, text, or specific objects.",
      tooltipHtml = """
      Performs a deep-dive analysis of an image by:
      <ul>
          <li>Identifying regions of interest based on a query</li>
          <li>Recursively cropping and re-analyzing those regions</li>
          <li>Stitching results into a hierarchical dataset</li>
      </ul>
      Useful for OCR on complex forms, crowd analysis, or finding small details.
                  """,
    )
  }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/ImageGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.File
import javax.imageio.ImageIO

class ImageGenerationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GenerateImageTaskExecutionConfigData?
) : AbstractFileTask<ImageGenerationTask.GenerateImageTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class GenerateImageTaskExecutionConfigData(
        @Description("The image file to be created (relative path, must end with .png, .jpg, or .jpeg)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., reference images, style guides)")
        related_files: List<String>? = null,
        @Description("Detailed description of the image to generate including subject, style, composition, colors, mood, and any specific requirements")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GenerateImage.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            // Validate that at least one file is specified
            val files = files
            if (files.isNullOrEmpty()) {
                return "GenerateImageTask requires at least one file to be specified"
            }
            if (files.size > 1) {
                return "GenerateImageTask currently supports generating only one image at a time"
            }

            // Validate that the file has a valid image extension
            val imageFile = files.first()
            if (!imageFile.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))) {
                return "GenerateImageTask file must have .png, .jpg, or .jpeg extension: $imageFile"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
        GenerateImage - Create high-quality images using AI generation models.
          * Specify a single output file path (png, jpg, or jpeg).
          * Provide a detailed task_description covering style, composition, and mood.
          * Use related_files to provide visual context or style references.
          * Useful for UI mockups, illustrations, and visual assets.
        """.trimIndent()
    }

    override fun formatFileForLLM(relativePath: File): CharSequence? {
        return when (relativePath.name.split('.').last()) {
            "png", "jpg", "jpeg" -> null
            else -> super.formatFileForLLM(relativePath)
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        try {
            transcript?.write("# Generate Image Task\n\n".toByteArray())
            val tabs = TabbedDisplay(task)

            val imageFiles = executionConfig?.files ?: emptyList()
            if (imageFiles.isEmpty()) {
                val err = "CONFIGURATION ERROR: No image file specified"
                task.add(err.renderMarkdown())
                resultFn(err)
                return
            }

            val imageOutputFile = imageFiles.first()
            val previewTask = tabs.newTask("Preview")
            val promptTask = tabs.newTask("Prompt")

            task.ui.pool.submit {
                try {
                    log.info("Starting image generation for $imageOutputFile")
                    previewTask.header("Generating Image: $imageOutputFile", level = 2)

                    val inputImageFiles = executionConfig?.related_files?.filter {
                        it.matches(Regex(".*\\.(png|jpg|jpeg)$", RegexOption.IGNORE_CASE))
                    } ?: emptyList()
                    val inputImages = inputImageFiles.mapNotNull { filePath ->
                        val file = root.resolve(filePath)
                        if (file.toFile().exists()) {
                            val image = ImageIO.read(file.toFile())
                            ImageAndText(image = image, text = "Reference image: $filePath")
                        } else {
                            null
                        }
                    }

                    val contextFiles = getInputFileCode()
                    val priorCode = getPriorCode(agent.executionState)

                    val imagePrompt = buildString {
                        append(executionConfig?.task_description ?: "Generate an image")
                        if (contextFiles.isNotEmpty()) {
                            append("\n\nContext from related files:\n")
                            append(contextFiles)
                        }
                        if (priorCode.isNotEmpty()) {
                            append("\n\nPrevious task results:\n")
                            append(priorCode)
                        }
                    }

                    promptTask.add("### Image Generation Prompt\n\n```\n$imagePrompt\n```".renderMarkdown())
                    transcript?.write("## Prompt\n\n$imagePrompt\n\n".toByteArray())

                    previewTask.add("Generating image...".renderMarkdown())

                    val imageAgent = ImageProcessingAgent(
                        prompt = "Transform the user request into an image",
                        name = "ImageGenerator",
                        model = orchestrationConfig.defaultImage.getChildClient(task),
                    )

                    val result = imageAgent.answer(listOf(ImageAndText(imagePrompt)) + inputImages)
                    val generatedImage = result.image ?: throw RuntimeException("No image generated by the agent")
                    val optimizedPrompt = result.text

                    promptTask.add("### Optimized Prompt Used\n\n```\n$optimizedPrompt\n```".renderMarkdown())
                    transcript?.write("## Optimized Prompt\n\n$optimizedPrompt\n\n".toByteArray())

                    previewTask.header("Generated Image Preview", level = 3)
                    previewTask.image(generatedImage)

                    val saveAction = {
                        val outputPath = root.resolve(imageOutputFile)
                        outputPath.toFile().parentFile?.mkdirs()

                        val format = when {
                            imageOutputFile.endsWith(".png", ignoreCase = true) -> "png"
                            imageOutputFile.endsWith(".jpg", ignoreCase = true) -> "jpg"
                            imageOutputFile.endsWith(".jpeg", ignoreCase = true) -> "jpeg"
                            else -> "png"
                        }

                        ImageIO.write(generatedImage, format, outputPath.toFile())
                        val link = task.linkTo(imageOutputFile)
                        val summary =
                            "Successfully generated and saved image to <a href=\"$link\">$imageOutputFile</a>."

                        previewTask.add(summary.renderMarkdown())
                        transcript?.write("## Result\n\n$summary\n\n".toByteArray())
                        log.info("Image saved successfully to $imageOutputFile")

                        previewTask.complete()
                        resultFn(summary)
                    }





                    if (orchestrationConfig.autoFix) {
                        saveAction()
                    } else {
                        previewTask.add("Image generated. Click below to save to workspace.".renderMarkdown())
                        previewTask.add(acceptButtonFooter(task.ui) {
                            saveAction()
                        })
                    }

                } catch (e: Exception) {
                    // Triple Log Rule
                    log.error("Error in ImageGenerationTask for $imageOutputFile", e)
                    previewTask.error(e)
                    val errorDetails = """
                        <details>
                        <summary>Stack Trace</summary>

                        ```
                        ${e.stackTraceToString()}
                        ```
                        </details>
                    """.trimIndent()
                    transcript?.write("## Error\n\n${e.message}\n$errorDetails\n".toByteArray())
                    resultFn("ERROR: ${e.message}")
                } finally {
                    transcript?.close()
                }
            }
        } catch (e: Exception) {
            log.error("Failed to schedule ImageGenerationTask", e)
            task.error(e)
            transcript?.close()
            resultFn("ERROR: ${e.message}")
        }
    }

    override fun isIgnored(file: File) = when (file.extension.lowercase()) {
        "png", "jpg", "jpeg" -> true
        else -> super.isIgnored(file)
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(ImageGenerationTask::class.java)
        @JvmStatic val GenerateImage = TaskType(
          name = "GenerateImage",
          category = "Writing",
          taskClass = ImageGenerationTask::class.java,
          executionConfigClass = GenerateImageTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate images using AI image generation models",
          tooltipHtml = """
                        Creates images from text descriptions using AI models like DALL-E.
                        <ul>
                          <li>Generates high-quality images from detailed prompts</li>
                          <li>Context-aware generation using related files</li>
                          <li>Integration with previous task results</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/ImageTableTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

class ImageTableTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ImageTableTaskExecutionConfigData?
) : AbstractTask<ImageTableTask.ImageTableTaskExecutionConfigData, ImageTableTask.ImageTableTaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class ImageTableTaskExecutionConfigData(
        @Description("Row labels for the image table (these can be descriptive text or image file paths)")
        var rows: List<String>? = null,
        @Description("Column labels for the image table (these can be descriptive text or image file paths)")
        var columns: List<String>? = null,
        @Description("Prompt template for generating each image. Use {row} and {column} as placeholders.")
        var image_prompt_template: String? = null,
        @Description("Base style or context to apply to all generated images")
        var base_style: String? = null,
        @Description("Output directory for generated images (relative path)")
        var output_directory: String? = "generated_images",
        @Description("Image format: 'png', 'jpg', or 'jpeg'")
        var image_format: String = "png",
        @Description("Overall description of the image table purpose")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = ImageTable.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (rows.isNullOrEmpty()) {
                return "ImageTableTask: rows list cannot be null or empty"
            }
            if (columns.isNullOrEmpty()) {
                return "ImageTableTask: columns list cannot be null or empty"
            }
            if (image_prompt_template.isNullOrBlank()) {
                return "ImageTableTask: image_prompt_template cannot be null or blank"
            }
            if (image_format !in listOf("png", "jpg", "jpeg")) {
                return "ImageTableTask: image_format must be 'png', 'jpg', or 'jpeg'"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    class ImageTableTaskTypeConfig(
        task_type: String? = ImageTable.name,
        @Description("Maximum number of images to generate in parallel")
        var parallel_generation: Int = 2,
        @Description("Image width in pixels")
        var image_width: Int = 512,
        @Description("Image height in pixels")
        var image_height: Int = 512,
    ) : TaskTypeConfig(task_type = task_type), ValidatedObject {
        override fun validate(): String? {
            if (parallel_generation < 1 || parallel_generation > 10) {
                return "ImageTableTask: parallel_generation must be between 1 and 10"
            }
            if (image_width < 64 || image_width > 2048) {
                return "ImageTableTask: image_width must be between 64 and 2048"
            }
            if (image_height < 64 || image_height > 2048) {
                return "ImageTableTask: image_height must be between 64 and 2048"
            }
            return null
        }
    }

    init {
        planTask?.validate()?.let { errorMessage ->
            throw ValidatedObject.ValidationError(errorMessage, planTask)
        }
    }

    override fun promptSegment(): String {
        return """
ImageTable - Generate a table/grid of AI-generated images
  ** Specify row labels in the 'rows' array (these can be descriptive text or image file paths)
  ** Specify column labels in the 'columns' array (these can be descriptive text or image file paths)
  ** Provide an image_prompt_template using {row} and {column} placeholders
  ** Optionally specify a base_style for consistent styling across all images
  ** Images are saved to the specified output_directory
  ** Generates an HTML table displaying all images with labels
  ** Example use cases:
     - Style comparison grids (subjects vs art styles)
     - Product variation displays (colors vs sizes)
     - Character emotion charts (characters vs emotions)
     - Concept exploration matrices (themes vs settings)
      """
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


      val transcript = task.transcript()
      log.info("Starting ImageTableTask execution.")
      task.ui.pool.submit {
        try {
          executionConfig?.validate()?.let { errorMessage ->
            resultFn("VALIDATION ERROR: $errorMessage")
            return@submit
          }

          val rows = executionConfig?.rows ?: emptyList()
          val columns = executionConfig?.columns ?: emptyList()
          val promptTemplate = executionConfig?.image_prompt_template ?: ""
          val baseStyle = executionConfig?.base_style ?: ""
          val outputDir = executionConfig?.output_directory ?: "generated_images"
          val imageFormat = executionConfig?.image_format ?: "png"

          transcript?.write("## Image Table Generation Intent\nGenerating a ${rows.size}x${columns.size} grid.\n".toByteArray())

          val tabs = TabbedDisplay(task)
          val configTab = tabs.newTask("Configuration")
          configTab.add(buildString {
                      appendLine("# Image Table Configuration")
                      appendLine("- **Rows:** ${rows.size}")
                      appendLine("- **Columns:** ${columns.size}")
                      appendLine("- **Total Images:** ${rows.size * columns.size}")
                      appendLine("- **Prompt Template:** `$promptTemplate`")
                      appendLine("- **Base Style:** `${baseStyle.ifBlank { "None" }}`")
                      appendLine("- **Output Directory:** `$outputDir`")
                    }.renderMarkdown(true))

          val progressTab = tabs.newTask("Generation Progress")

          // Create output directory
          val outputPath = task.resolveUserFile(outputDir)
          outputPath?.mkdirs()

          // Initialize the results table to store image paths
          val imageResults = Array(rows.size) { Array(columns.size) { "" } }
          val totalImages = rows.size * columns.size
          var completedImages = 0

          // Create the image generation agent
          val imageChatChatter = orchestrationConfig.defaultImage.getChildClient(task)
          val imageAgent = ImageProcessingAgent(
            prompt = "Transform the user request into an image. Generate exactly what is described.",
            name = "ImageTableGenerator",
            model = imageChatChatter,
          )

          val parallelSemaphore = Semaphore(typeConfig?.parallel_generation ?: 2)

          // Process each cell
          for (rowIdx in rows.indices) {
            for (colIdx in columns.indices) {
              parallelSemaphore.acquire()
              try {
                completedImages++
                val rowLabel = rows[rowIdx]
                val colLabel = columns[colIdx]

                progressTab.add(buildString {
                                  appendLine("### Generating $completedImages/$totalImages")
                                  appendLine("- **Row:** $rowLabel")
                                  appendLine("- **Column:** $colLabel")
                                }.renderMarkdown(true))
                progressTab.update()

                // Build the prompt for this cell
                var prompt = promptTemplate.replace("{row}", rowLabel).replace("{column}", colLabel)
                prompt = if (baseStyle.isNotBlank()) {
                  "$prompt. Style: $baseStyle"
                } else {
                  prompt
                }
                val imagePrompt = mutableListOf(ImageAndText(prompt))
                when (rowLabel.split('.').last()) {
                  "jpg", "jpeg", "png" -> {
                    val imagePath = agent.root.resolve(rowLabel)
                    if (imagePath.toFile().exists()) {
                      imagePrompt.add(ImageAndText(imagePath.toUri().toString(), image = imagePath.loadImage()))
                    } else {
                      log.warn("Image file for row label not found: $imagePath")
                    }
                  }
                }
                when (colLabel.split('.').last()) {
                  "jpg", "jpeg", "png" -> {
                    val imagePath = agent.root.resolve(colLabel)
                    if (imagePath.toFile().exists()) {
                      imagePrompt.add(ImageAndText(imagePath.toUri().toString(), image = imagePath.loadImage()))
                    } else {
                      log.warn("Image file for column label not found: $imagePath")
                    }
                  }
                }

                try {
                  // Generate the image
                  val result = imageAgent.answer(imagePrompt)
                  val generatedImage = result.image

                  // Create filename
                  val safeRowLabel = sanitizeFilename(rowLabel)
                  val safeColLabel = sanitizeFilename(colLabel)
                  val filename = "${safeRowLabel}_${safeColLabel}.$imageFormat"
                  val imagePath = outputPath?.resolve(filename)

                  // Save the image
                  ImageIO.write(generatedImage, imageFormat, imagePath)

                  // Store the relative path
                  val relativePath = "$outputDir/$filename"
                  imageResults[rowIdx][colIdx] = relativePath

                  // Show preview
                  progressTab.image(generatedImage!!)
                  progressTab.update()

                } catch (e: Exception) {
                  log.error("Error generating image for row='$rowLabel', column='$colLabel'", e)
                  imageResults[rowIdx][colIdx] = "ERROR"
                  progressTab.error(e)
                }
              } finally {
                parallelSemaphore.release()
              }
            }
          }


          // Generate the HTML table output
          val galleryTab = tabs.newTask("Gallery")
          val htmlTable = formatAsHtmlTable(rows, columns, imageResults, task)
          galleryTab.add(htmlTable)

          // Save the HTML table to a file
          val htmlFilename = "image_table.html"
          val htmlPath = outputPath?.resolve(htmlFilename)
          val standaloneHtml = generateStandaloneHtml(rows, columns, imageResults)
          htmlPath?.writeText(standaloneHtml)

          transcript?.write("\n### Generation Results\n<details><summary>HTML Table Source</summary>\n\n```html\n$standaloneHtml\n```\n</details>\n".toByteArray())

          val summary = buildString {
            appendLine("Successfully generated ${rows.size}x${columns.size} image table.")
            appendLine("Images saved to: `$outputDir/`")
            appendLine("HTML table saved to: `$outputDir/$htmlFilename`")
          }

          if (orchestrationConfig.autoFix) {
            task.complete(summary.renderMarkdown(true))
            resultFn(summary)
          } else {
            val footer = acceptButtonFooter(task.ui) {
              task.complete(summary.renderMarkdown(true))
              resultFn(summary)
            }
            task.add(summary.renderMarkdown(true) + footer)
          }
          log.info("ImageTableTask completed successfully.")
        } catch (e: Exception) {
          task.error(e)
          log.error("Error in ImageTableTask", e)
          transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
        } finally {
          transcript?.close()
        }
      }
    }

    private fun Path.loadImage(): BufferedImage? {
        return try {
            ImageIO.read(this.toFile())
        } catch (e: Exception) {
            log.error("Error loading image from path: $this", e)
            null
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(50)
    }

    private fun formatAsHtmlTable(
        rows: List<String>,
        columns: List<String>,
        images: Array<Array<String>>,
        task: SessionTask
    ): String {
        return buildString {
            appendLine("""<table style="border-collapse: collapse; margin: 20px 0;">""")
            appendLine("  <thead>")
            appendLine("    <tr>")
            appendLine("""      <th style="border: 1px solid #ddd; padding: 12px; background: #f5f5f5;"></th>""")
            columns.forEach { col ->
                appendLine("""      <th style="border: 1px solid #ddd; padding: 12px; background: #f5f5f5; text-align: center;">$col</th>""")
            }
            appendLine("    </tr>")
            appendLine("  </thead>")
            appendLine("  <tbody>")
            rows.forEachIndexed { rowIdx, rowHeader ->
                appendLine("    <tr>")
                appendLine("""      <th style="border: 1px solid #ddd; padding: 12px; background: #f5f5f5;">$rowHeader</th>""")
                columns.indices.forEach { colIdx ->
                    val imagePath = images[rowIdx][colIdx]
                    val cellContent = if (imagePath == "ERROR") {
                        """<span style="color: red;">Error</span>"""
                    } else {
                        val link = task.linkTo(imagePath)
                        """<a href="$link" target="_blank"><img src="$link" style="max-width: 150px; max-height: 150px; border-radius: 4px;" /></a>"""
                    }
                    appendLine("""      <td style="border: 1px solid #ddd; padding: 8px; text-align: center;">$cellContent</td>""")
                }
                appendLine("    </tr>")
            }
            appendLine("  </tbody>")
            appendLine("</table>")
        }
    }

    private fun formatAsMarkdownSummary(
        rows: List<String>,
        columns: List<String>,
        images: Array<Array<String>>
    ): String {
        return buildString {
            appendLine("### Image Table Summary")
            appendLine()
            appendLine("| Row | Column | Image Path |")
            appendLine("|-----|--------|------------|")
            rows.forEachIndexed { rowIdx, rowHeader ->
                columns.forEachIndexed { colIdx, colHeader ->
                    val imagePath = images[rowIdx][colIdx]
                    appendLine("| $rowHeader | $colHeader | $imagePath |")
                }
            }
        }
    }

    private fun generateStandaloneHtml(
        rows: List<String>,
        columns: List<String>,
        images: Array<Array<String>>
    ): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("  <title>Generated Image Table</title>")
            appendLine("  <style>")
            appendLine("    body { font-family: Arial, sans-serif; padding: 20px; }")
            appendLine("    table { border-collapse: collapse; margin: 20px auto; }")
            appendLine("    th, td { border: 1px solid #ddd; padding: 12px; text-align: center; }")
            appendLine("    th { background: #f5f5f5; }")
            appendLine("    img { max-width: 200px; max-height: 200px; border-radius: 4px; cursor: pointer; }")
            appendLine("    img:hover { transform: scale(1.05); transition: transform 0.2s; }")
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <h1>Generated Image Table</h1>")
            appendLine("  <table>")
            appendLine("    <thead>")
            appendLine("      <tr>")
            appendLine("        <th></th>")
            columns.forEach { col ->
                appendLine("        <th>$col</th>")
            }
            appendLine("      </tr>")
            appendLine("    </thead>")
            appendLine("    <tbody>")
            rows.forEachIndexed { rowIdx, rowHeader ->
                appendLine("      <tr>")
                appendLine("        <th>$rowHeader</th>")
                columns.indices.forEach { colIdx ->
                    val imagePath = images[rowIdx][colIdx]
                    val filename = imagePath.substringAfterLast("/")
                    val cellContent = if (imagePath == "ERROR") {
                        """<span style="color: red;">Error</span>"""
                    } else {
                        """<a href="$filename" target="_blank"><img src="$filename" alt="$rowHeader - ${columns[colIdx]}" /></a>"""
                    }
                    appendLine("        <td>$cellContent</td>")
                }
                appendLine("      </tr>")
            }
            appendLine("    </tbody>")
            appendLine("  </table>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept Image Table") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ImageTableTask::class.java)
        @JvmStatic val ImageTable = TaskType(
          name = "ImageTable",
          category = "File",
          taskClass = ImageTableTask::class.java,
          executionConfigClass = ImageTableTaskExecutionConfigData::class.java,
          taskSettingsClass = ImageTableTaskTypeConfig::class.java,
          description = "Generate a table/grid of AI-generated images",
          tooltipHtml = """
                        Creates a grid of images by generating each cell using AI image generation.
                        <ul>
                          <li>Define rows and columns as labels for the grid</li>
                          <li>Provide a prompt template with {row} and {column} placeholders</li>
                          <li>Optionally specify a base style for consistent aesthetics</li>
                          <li>Generates individual images and an HTML table view</li>
                          <li>Useful for style comparisons, product variations, character sheets</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/ImageVariationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class ImageVariationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ImageVariationConfig?
) : AbstractFileTask<ImageVariationTask.ImageVariationConfig>(
  orchestrationConfig,
  planTask
) {

  class ImageVariationConfig(
    @Description("The input image file path")
    var input_file: String? = null,
    @Description("Number of distinct regions to modify")
    var num_subimages: Int = 7,
    @Description("Number of alternate versions per region")
    var num_subimage_alternates: Int = 2,
    @Description("Number of changes to apply per variation")
    var num_changes_per_variation: Int = 5,
    @Description("Number of alternative images to produce (M)")
    var num_variations: Int = 11,
    @Description("Output filename prefix")
    var output_prefix: String = "variation",
    @Description("Output image format (png/jpg)")
    var extension: String = "png",
    @Description("Whether to use image patch localization to align the generated variation with the original image")
    var retarget_subimages: Boolean = false,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = ImageVariation.name,
    task_description = "Generate ${num_variations} variations with ${num_subimages}x${num_subimage_alternates} potential changes from $input_file",
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (input_file.isNullOrBlank()) return "Input file must be specified"
      if (num_subimages < 1) return "Must identify at least 1 region"
      if (num_subimage_alternates < 1) return "Must generate at least 1 alternate per region"
      if (num_variations < 1) return "Must generate at least 1 variation"
      return ValidatedObject.validateFields(this)
    }
  }

  data class DetectedRegion(
    @Description("Label of the object or region")
    val label: String = "",
    @Description("X coordinate (0-1000)")
    val x: Int = 0,
    @Description("Y coordinate (0-1000)")
    val y: Int = 0,
    @Description("Width (0-1000)")
    val width: Int = 0,
    @Description("Height (0-1000)")
    val height: Int = 0
  )

  data class RegionAnalysis(
    @Description("List of distinct objects or regions suitable for modification")
    val regions: List<DetectedRegion> = emptyList()
  )

  data class ChangeProposal(
    @Description("The specific visual change to apply (e.g., 'Change the red car to blue', 'Remove the hat')")
    val change_instruction: String = "",
    @Description("Description of the result")
    val result_description: String = ""
  )

  data class AppliedChange(
    val id: String,
    val region: DetectedRegion,
    val instruction: String,
    val description: String,
    val imagePath: String,
    @Transient val image: BufferedImage? = null
  )

  data class VariationManifest(
    val base_image: String,
    val changes_applied: List<ChangeDescription>
  )

  data class ChangeDescription(
    val region_label: String,
    val description: String,
    val bounds: List<Int>, // x, y, w, h
    val changeId: String? = null
  )

  override fun promptSegment() = """
ImageVariation - Creates 'Find the Differences' style image sets.
* Use for: Game asset generation, data augmentation.
* Mechanism: Decomposes image, generates N specific sub-image changes, and recombines them into M variations.
      """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {


    val transcript = task.transcript()


































    task.ui.pool.submit {
      try {
        val inputFile = executionConfig?.input_file ?: return@submit resultFn("No input file")
        val numSubimages = executionConfig.num_subimages
        val numSubimageAlternates = executionConfig.num_subimage_alternates
        val numChangesPerVariation = executionConfig.num_changes_per_variation
        val numVariations = executionConfig.num_variations
        val prefix = executionConfig.output_prefix
        val ext = executionConfig.extension

        val analysisModel = orchestrationConfig.defaultSmart.getChildClient(task)
        val imageModel = orchestrationConfig.defaultImage.getChildClient(task)

        val tabs = TabbedDisplay(task)
        val logTab = tabs.newTask("Progress")

        log.info("Starting Image Variation Task for $inputFile")
        task.header("Starting Image Variation Task", level = 2)

        // 1. Load Base Image
        logTab.add("Loading base image: $inputFile".renderMarkdown())
        val baseFile = root.resolve(inputFile).toFile()
        if (!baseFile.exists()) throw RuntimeException("Input file not found: $inputFile")
        val baseImage = ImageIO.read(baseFile)
        val baseLink = task.linkTo(inputFile)
        tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""

        // 2. Analyze Structure (Decomposition)
        logTab.add("Analyzing image structure...".renderMarkdown())
        val imageAgent = ParsedImageAgent(
          resultClass = RegionAnalysis::class.java,
          model = analysisModel,
          prompt = """
              Analyze this image to identify distinct objects or regions that could be modified for a "Find the Differences" game.
              Identify at least ${numSubimages + 2} distinct regions.
              Avoid overlapping regions if possible.
              Output coordinates on a 0-1000 scale.
            """.trimIndent()
        )
        val answer = imageAgent.answer(listOf(ImageAndText(text = "", image = baseImage)))
        val analysis = answer.obj

        transcript?.write(buildString {
          appendLine("## Structural Analysis")
          appendLine("Found ${analysis.regions.size} regions.")
          appendLine("<details><summary>Raw Analysis JSON</summary>\n\n```json")
          appendLine(analysis.toJson())
          appendLine("```\n</details>")
        }.toByteArray())

        // Draw debug map
        val debugImg = BufferedImage(baseImage.width, baseImage.height, BufferedImage.TYPE_INT_ARGB)
        val gDebug = debugImg.createGraphics()
        gDebug.drawImage(baseImage, 0, 0, null)
        gDebug.stroke = BasicStroke(3f)
        analysis.regions.forEach { r ->
          val x = (r.x * baseImage.width / 1000.0).toInt()
          val y = (r.y * baseImage.height / 1000.0).toInt()
          val w = (r.width * baseImage.width / 1000.0).toInt()
          val h = (r.height * baseImage.height / 1000.0).toInt()
          gDebug.color = Color.CYAN
          gDebug.drawRect(x, y, w, h)
          gDebug.color = Color.BLACK
          gDebug.drawString(r.label, x, y - 5)
          gDebug.color = Color.WHITE
          gDebug.drawString(r.label, x - 1, y - 6)
        }
        gDebug.dispose()
        val debugLink = saveImage(debugImg, "${prefix}_analysis.$ext", task)
        tabs["Analysis Map"] = """<img src="$debugLink" style="max-width: 100%;" />"""

        // 3. Generate N Changes
        val generatedChanges = mutableListOf<AppliedChange>()
        val regionsToProcess = analysis.regions.shuffled().take(numSubimages)

        regionsToProcess.forEachIndexed { regionIdx, region ->
          logTab.add("Processing region ${regionIdx + 1}/$numSubimages: **${region.label}**".renderMarkdown())

          // Crop
          val rx = (region.x * baseImage.width / 1000.0).toInt()
          val ry = (region.y * baseImage.height / 1000.0).toInt()
          val rw = (region.width * baseImage.width / 1000.0).toInt().coerceAtLeast(64)
          val rh = (region.height * baseImage.height / 1000.0).toInt().coerceAtLeast(64)

          // Safety check for bounds
          if (rx + rw > baseImage.width || ry + rh > baseImage.height) return@forEachIndexed

          val crop = baseImage.getSubimage(rx, ry, rw, rh)

          for (altIdx in 1..numSubimageAlternates) {
            // Plan Change
            val proposal = ParsedImageAgent(
              resultClass = ChangeProposal::class.java,
              model = analysisModel,
              prompt = """
                  Suggest a single, distinct visual change for this image region (Label: ${region.label}).
                  This is variation $altIdx of $numSubimageAlternates.
                  This is for a "Find the Differences" game.
                  Examples: Change color, remove object, change facial expression, rotate object.
                  The change should be visible but retain the same lighting and style.
                """.trimIndent()
            ).answer(listOf(ImageAndText(text = "", image = crop))).obj


            // Render Change
            val renderAgent = ImageProcessingAgent(
              prompt = "Apply the requested change",
              model = imageModel
            )


            val prompt = "Modify this image: ${proposal.change_instruction}. Maintain style and background."
            val result = renderAgent.answer(listOf(ImageAndText(text = prompt, image = crop)))

            if (result.image != null) {
              val changeId = "change_${regionIdx}_${altIdx}_${region.label.replace(" ", "_")}"
              val changeFilename = "${prefix}_$changeId.$ext"
              saveImage(result.image!!, changeFilename, task)

              generatedChanges.add(
                AppliedChange(
                  id = changeId,
                  region = region,
                  instruction = proposal.change_instruction,
                  description = proposal.result_description,
                  imagePath = changeFilename,
                  image = result.image
                )
              )

              transcript?.write(buildString {
                appendLine("### Change: ${region.label} ($altIdx/$numSubimageAlternates)")
                appendLine("**Instruction:** ${proposal.change_instruction}")
                appendLine("![Original](${task.linkTo(changeFilename)})")
              }.toByteArray())
            }
          }
        }

        // 4. Generate M Variations
        logTab.add("Composing $numVariations variations...".renderMarkdown())

        val variations = (1..numVariations).map { varIdx ->
          val canvas = BufferedImage(baseImage.width, baseImage.height, BufferedImage.TYPE_INT_ARGB)
          val g = canvas.createGraphics()
          g.drawImage(baseImage, 0, 0, null)

          // Randomly select subset of changes (e.g., 1 to N changes)
          val changesToApply = generatedChanges
            .groupBy { it.region }
            .values
            .shuffled()
            .take(numChangesPerVariation)
            .map { it.random() }
          val appliedDescriptions = mutableListOf<ChangeDescription>()

          changesToApply.forEach { change ->
            val r = change.region
            val rx = (r.x * baseImage.width / 1000.0).toInt()
            val ry = (r.y * baseImage.height / 1000.0).toInt()
            val rw = (r.width * baseImage.width / 1000.0).toInt()
            val rh = (r.height * baseImage.height / 1000.0).toInt()


            var patch = change.image!!
            if (executionConfig.retarget_subimages) {
              val originalCrop = baseImage.getSubimage(rx, ry, rw, rh)
              val bounds = ImagePatchLocalization.findBounds(
                patch,
                originalCrop,
                ImagePatchLocalization.SubImageBounds(0, 0, patch.width, patch.height, 0.0)
              )
              val bx = bounds.x.coerceIn(0, patch.width)
              val by = bounds.y.coerceIn(0, patch.height)
              val bw = bounds.width.coerceAtMost(patch.width - bx)
              val bh = bounds.height.coerceAtMost(patch.height - by)
              if (bw > 0 && bh > 0) {
                patch = patch.getSubimage(bx, by, bw, bh)
              }
            }

            // Feathering
            val featherSize = (Math.min(rw, rh) * 0.1).toInt().coerceAtLeast(1)
            val featheredPatch = featherImage(patch, featherSize)

            // Resize patch if dimensions drifted slightly (though they shouldn't with standard img2img)
            g.drawImage(featheredPatch, rx, ry, rw, rh, null)

            appliedDescriptions.add(
              ChangeDescription(
                region_label = change.region.label,
                description = change.description,
                bounds = listOf(rx, ry, rw, rh),
                changeId = change.id
              )
            )
          }
          g.dispose()

          val varFilename = "${prefix}_v${varIdx}.$ext"
          val varLink = saveImage(canvas, varFilename, task)

          // Save JSON Manifest
          val manifest = VariationManifest(inputFile, appliedDescriptions)
          val jsonFile = root.resolve("${prefix}_v${varIdx}.json").toFile()
          jsonFile.writeText(manifest.toJson())

          tabs["Variation $varIdx"] = """
              <p><b>Changes:</b> ${appliedDescriptions.joinToString { it.region_label }}</p>
              <a href="$varLink" target="_blank"><img src="$varLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
              <br/><a href="${task.linkTo(jsonFile.name)}">View JSON Manifest</a>
            """.trimIndent()

          varFilename to manifest
        }
        generateGame(inputFile, variations, prefix, task)

        val completionMsg =
          "Generated ${variations.size} variations based on ${generatedChanges.size} unique modifications."
        if (orchestrationConfig.autoFix) {
          task.safeComplete(completionMsg.renderMarkdown(), log)
          resultFn(completionMsg)
        } else {
          val footer = acceptButtonFooter(task.ui) {
            task.safeComplete(completionMsg.renderMarkdown(), log)
            resultFn(completionMsg)
          }
          task.add(footer)
        }

      } catch (e: Throwable) {
        log.error("Error in ImageVariationTask: ${e.message}", e)
        task.error(e)
        transcript?.write(
          """
          ## Error
          <details>
          <summary>Stack Trace</summary>

          ```
          ${e.stackTraceToString()}
          ```
          </details>
        """.trimIndent().toByteArray()
        )
        resultFn("Error: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  private fun generateGame(
    baseImage: String,
    variations: List<Pair<String, VariationManifest>>,
    prefix: String,
    task: SessionTask
  ) {
    try {
      val templateStream = javaClass.getResourceAsStream("diff_game.html") ?: return
      val template = templateStream.bufferedReader().use { it.readText() }

      // Create manifest for base image
      val baseJsonName = "${prefix}_base.json"
      root.resolve(baseJsonName).toFile().writeText(VariationManifest(baseImage, emptyList()).toJson())

      val assets = mutableListOf<Map<String, String>>()
      assets.add(
        mapOf(
          "image" to baseImage,
          "json" to baseJsonName,
          "type" to "base"
        )
      )

      variations.forEach { (imgName, _) ->
        val jsonName = imgName.substringBeforeLast(".") + ".json"
        assets.add(
          mapOf(
            "image" to imgName,
            "json" to jsonName,
            "type" to "variation"
          )
        )
      }

      val html = template.replace("/*GAME_DATA*/", assets.toJson())
      val gameFile = root.resolve("${prefix}_game.html").toFile()
      gameFile.writeText(html)
      task.transcript()?.write(buildString {
        appendLine("## Interactive Game")
        appendLine("[Play 'Find the Differences'](${task.linkTo(gameFile.name)})")
      }.toByteArray())
    } catch (e: Exception) {
      log.warn("Failed to generate game", e)
    }
  }


  private fun saveImage(image: BufferedImage, name: String, task: SessionTask): String {
    val file = root.resolve(name)
    ImageIO.write(image, executionConfig?.extension ?: "png", file.toFile())
    return task.linkTo(name)
  }

  private fun featherImage(image: BufferedImage, feather: Int): BufferedImage {
    val w = image.width
    val h = image.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(image, 0, 0, null)
    g.dispose()

    for (y in 0 until h) {
      for (x in 0 until w) {
        var factor = 1.0f
        if (x < feather) factor = Math.min(factor, x.toFloat() / feather)
        if (x >= w - feather) factor = Math.min(factor, (w - x).toFloat() / feather)
        if (y < feather) factor = Math.min(factor, y.toFloat() / feather)
        if (y >= h - feather) factor = Math.min(factor, (h - y).toFloat() / feather)

        if (factor < 1.0f) {
          val rgb = out.getRGB(x, y)
          val alpha = (rgb shr 24) and 0xFF
          val newAlpha = (alpha * factor).toInt()
          out.setRGB(x, y, (rgb and 0x00FFFFFF) or (newAlpha shl 24))
        }
      }
    }
    return out
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    return ui.hrefLink("Accept Variations") { fn() }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ImageVariationTask::class.java)
    @JvmStatic val ImageVariation = TaskType(
      name = "ImageVariation",
      category = "File",
      taskClass = ImageVariationTask::class.java,
      executionConfigClass = ImageVariationConfig::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generates 'Find the Differences' style image variations.",
      tooltipHtml = """
            Analyzes an image to find distinct regions, generates specific visual changes for those regions,
            and creates multiple alternative images by randomly combining these changes.
            Outputs images and JSON manifests describing the differences.
            """,
    )
  }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/OCRTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.RenderableDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

class OCRTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: OCRTaskExecutionConfigData?
) : AbstractFileTask<OCRTask.OCRTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class OCRTaskExecutionConfigData(
        @Description("The files to process (PDF or images)") files: List<String>? = null,
        @Description("DPI for rendering pages (default: 150)") val dpi: Float = 150f,
        @Description("Extract figures as images") val extract_figures: Boolean = false,
        @Description("Extract form fields and metadata") val extract_metadata: Boolean = false,
        @Description("Extract existing text content") val extract_text: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = OCR.name,
        task_description = task_description,
        files = files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            if (files.isNullOrEmpty()) return "OCRTask requires at least one file"
            return ValidatedObject.validateFields(this)
        }
    }
    data class FigureLocation(
        @Description("Description/Name of the figure") val name: String = "",
        @Description("X coordinate (0-1000)") val x: Int = 0,
        @Description("Y coordinate (0-1000)") val y: Int = 0,
        @Description("Width (0-1000)") val width: Int = 0,
        @Description("Height (0-1000)") val height: Int = 0
    )
    data class PageAnalysis(
        @Description("List of figures/images/charts found on the page") val figures: List<FigureLocation> = emptyList(),
        @Description("Form fields, key-value pairs, and other metadata") val metadata: Map<String, String> = emptyMap()
    )


    override fun promptSegment(): String {
        return """
OCR - Convert documents (PDF, Images) to Markdown text.
* Extracts text from images and PDFs using Vision models.
* Preserves formatting as Markdown.
* Optionally extracts figures as images and metadata/form fields.
* Saves output to a .md file with the same name.
""".trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val files = executionConfig?.files ?: emptyList()

        if (files.isEmpty()) {
            val msg = "No files specified"
            task.safeComplete(msg, log)
            resultFn(msg)
            return
        }

        val transcript = task.transcript()
















        task.ui.pool.submit {
            val tabs = TabbedDisplay(task)
            val summaryTask = tabs.newTask("Summary")
            val results = mutableMapOf<File, String>()
            try {
                log.info("Starting OCR task for ${files.size} files")
                transcript?.write("## OCR Task Started\nProcessing files: ${files.joinToString(", ")}\n".toByteArray())
                files.forEachIndexed { index, filePath ->
                    val file = root.resolve(filePath).toFile()
                    if (!file.exists()) {
                        val errorMsg = "❌ File not found: $filePath"
                        log.warn("File not found: $filePath")
                        task.add(errorMsg.renderMarkdown(), additionalClasses = "text-danger")
                        transcript?.write("- [ERROR] File not found: $filePath\n".toByteArray())
                        return@forEachIndexed
                    }
                    val fileTask = tabs.newTask(file.name)
                    fileTask.header("Processing ${file.name}", level = 3)
                    transcript?.write("### Processing ${file.name}\n".toByteArray())
                    val sb = StringBuilder()
                    if (executionConfig?.extract_text == true && file.extension.equals("pdf", ignoreCase = true)) {
                        try {
                            PDDocument.load(file).use { doc ->
                                val stripper = PDFTextStripper()
                                val text = stripper.getText(doc)
                                val textFile = File(file.parentFile, file.nameWithoutExtension + "_text.txt")
                                textFile.writeText(text)
                                fileTask.add("✅ Extracted text to `${textFile.name}`".renderMarkdown())
                                transcript?.write("- Extracted raw text to `${textFile.name}`\n".toByteArray())
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to extract text from PDF: ${file.name}", e)
                            fileTask.add(
                                "❌ Failed to extract text: ${e.message}".renderMarkdown(),
                                additionalClasses = "text-danger"
                            )
                            transcript?.write("- [ERROR] Text extraction failed: ${e.message}\n".toByteArray())
                        }
                    }
                    try {
                        file.getDocumentReader().use { reader ->
                            if (reader is PaginatedDocumentReader && reader is RenderableDocumentReader) {
                                val pageCount = reader.getPageCount()
                                val progressBuffer = fileTask.add("Initializing...")
                                val allMetadata = mutableListOf<PageAnalysis>()
                                for (page in 0 until pageCount) {
                                    progressBuffer?.setLength(0)
                                    progressBuffer?.append("Processing page ${page + 1}/$pageCount...")
                                    fileTask.update()
                                    val image = reader.renderImage(page, executionConfig!!.dpi)
                                    val response = orchestrationConfig.defaultSmart.chat(
                                        listOfNotNull(
                                            ModelSchema.ChatMessage(
                                                ModelSchema.Role.system,
                                                listOf(ModelSchema.ContentPart("You are an OCR engine. Convert the image to Markdown. Output only the markdown content."))
                                            ),
                                            ModelSchema.ChatMessage(
                                                ModelSchema.Role.user,
                                                listOf(
                                                    ModelSchema.ContentPart("Convert this page")
                                                        .apply { this.image = image })
                                            )
                                        )
                                    ).choices.first().message?.content ?: ""
                                    sb.append(response).append("\n\n")
                                    transcript?.write("<details><summary>Page ${page + 1} OCR Result</summary>\n\n$response\n\n</details>\n".toByteArray())
                                    if (executionConfig?.extract_figures == true || executionConfig?.extract_metadata == true) {
                                        val config = executionConfig!!
                                        try {
                                            val analysisAgent = ParsedImageAgent(
                                                resultClass = PageAnalysis::class.java,
                                                model = orchestrationConfig.defaultSmart.getChildClient(task),
                                                prompt = """
                                                    Analyze this page.
                                                    ${if (config.extract_figures) "Identify any figures, charts, or diagrams. Provide bounding boxes (0-1000 scale)." else ""}
                                                    ${if (config.extract_metadata) "Extract any form fields or key-value metadata." else ""}
                                                """.trimIndent()
                                            )
                                            val analysis = analysisAgent.answer(
                                                listOf(ImageAndText(image = image, text = "Analyze page"))
                                            ).obj
                                            if (config.extract_metadata) {
                                                allMetadata.add(analysis)
                                            }
                                            if (config.extract_figures) {
                                                val figuresDir =
                                                    File(file.parentFile, "${file.nameWithoutExtension}_figures")
                                                figuresDir.mkdirs()
                                                analysis.figures.forEachIndexed { i, fig ->
                                                    val scaledX = (fig.x * image.width / 1000.0).toInt()
                                                        .coerceIn(0, image.width - 1)
                                                    val scaledY = (fig.y * image.height / 1000.0).toInt()
                                                        .coerceIn(0, image.height - 1)
                                                    val scaledW = (fig.width * image.width / 1000.0).toInt()
                                                        .coerceAtMost(image.width - scaledX)
                                                    val scaledH = (fig.height * image.height / 1000.0).toInt()
                                                        .coerceAtMost(image.height - scaledY)
                                                    if (scaledW > 10 && scaledH > 10) {
                                                        val subImage =
                                                            image.getSubimage(scaledX, scaledY, scaledW, scaledH)
                                                        val safeName =
                                                            fig.name.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(50)
                                                        val figFile =
                                                            File(figuresDir, "p${page + 1}_${i + 1}_$safeName.png")
                                                        ImageIO.write(subImage, "png", figFile)
                                                    }
                                                }
                                                if (analysis.figures.isNotEmpty()) {
                                                    fileTask.add("  - Saved ${analysis.figures.size} figures from page ${page + 1}".renderMarkdown())
                                                }
                                            }
                                        } catch (e: Exception) {
                                            log.warn("Analysis failed for page $page of ${file.name}", e)
                                        }
                                    }
                                }
                                if (executionConfig?.extract_metadata == true && allMetadata.isNotEmpty()) {
                                    val metaFile = File(file.parentFile, "${file.nameWithoutExtension}_metadata.json")
                                    val mapper = jacksonObjectMapper().writerWithDefaultPrettyPrinter()
                                    metaFile.writeText(mapper.writeValueAsString(allMetadata))
                                    fileTask.add("✅ Saved metadata to `${metaFile.name}`".renderMarkdown())
                                    transcript?.write("- Saved metadata to `${metaFile.name}`\n".toByteArray())
                                }
                                progressBuffer?.setLength(0)
                                progressBuffer?.append("✅ Processed $pageCount pages")
                                fileTask.update()
                                fileTask.add(sb.toString().renderMarkdown())
                                fileTask.complete()
                            } else {
                                fileTask.add(
                                    "❌ Cannot read document: `${file.name}`".renderMarkdown(),
                                    additionalClasses = "text-danger"
                                )
                                return@forEachIndexed
                            }
                        }
                        val outputFileName = file.nameWithoutExtension + ".md"
                        val outputFile = File(file.parentFile, outputFileName)
                        results[outputFile] = sb.toString()
                    } catch (e: Exception) {
                        log.error("Error processing ${file.name}", e)
                        task.error(e)
                        transcript?.write("\n## Error processing ${file.name}\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                    }
                }
                if (results.isEmpty()) {
                    summaryTask.safeComplete("No documents processed successfully", log)
                    resultFn("Failed to process documents")
                    return@submit
                }
                if (orchestrationConfig.autoFix) {
                    results.forEach { (file, content) ->
                        file.writeText(content)
                        summaryTask.add("✅ Saved `${file.name}`".renderMarkdown())
                        transcript?.write("- Auto-saved `${file.name}`\n".toByteArray())
                    }
                    val summary = "OCR Completed for ${results.size} files."
                    summaryTask.safeComplete(summary, log)
                    resultFn(summary)
                } else {
                    val semaphore = Semaphore(0)
                    summaryTask.header("Review OCR Results", level = 3)
                    results.forEach { (file, content) ->
                        summaryTask.expandable("Preview: ${file.name}", "<pre>${content.take(500)}...</pre>")
                    }
                    val footer = acceptButtonFooter(task.ui) {
                        results.forEach { (file, content) ->
                            file.writeText(content)
                            summaryTask.add("✅ Saved `${file.name}`".renderMarkdown())
                            transcript?.write("- User approved and saved `${file.name}`\n".toByteArray())
                        }
                        semaphore.release()
                    }
                    summaryTask.add(footer)
                    summaryTask.update()
                    semaphore.acquire()
                    val summary = "OCR Completed for ${results.size} files."
                    task.safeComplete(summary, log)
                    resultFn(summary)
                }
            } catch (e: Exception) {
                log.error("OCR Task failed", e)
                task.error(e)
                transcript?.write("\n## Critical Task Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                resultFn("Error: ${e.message}")
            } finally {
                transcript?.close()
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(OCRTask::class.java)
        @JvmStatic val OCR = TaskType(
          name = "OCRTask",
          category = "File",
          taskClass = OCRTask::class.java,
          executionConfigClass = OCRTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Convert documents (PDF, Images) to Markdown text",
          tooltipHtml = """
          Uses Vision models to extract text and formatting from documents.
          <ul>
          <li>Supports PDF and Image files</li>
          <li>Converts to Markdown format</li>
          <li>Preserves layout and structure where possible</li>
          <li>Optionally extracts figures and metadata</li>
          </ul>
          """
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/PdfFormTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ParsedAgent

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice
import org.apache.pdfbox.pdmodel.interactive.form.PDField
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton
import java.io.ByteArrayOutputStream
import kotlin.io.path.exists

class PdfFormTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: PdfFormExecutionConfig?
) : AbstractTask<PdfFormTask.PdfFormExecutionConfig, PdfFormTask.PdfFormTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class PdfFormTypeConfig(
        @Description("The path to the PDF template file to be used for all executions of this task type")
        var template_file: String? = null,
        task_type: String? = PdfForm.name,
        name: String? = null,
        @Description("Template for the result message sent back to the planner")
        var resultTemplate: String = "Successfully created {output_file} with {count} fields filled."
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name
    )

    class PdfFormExecutionConfig(
        @Description("The output filename for the filled PDF")
        var output_file: String? = null,
        @Description("Map of field names (as defined in the template schema) to the values to fill")
        var fields: Map<String, String>? = null,
        @Description("Whether to flatten the form fields after filling (making them read-only)")
        var flatten: Boolean = true,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = PdfForm.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class FormData(
        @Description("Map of field names to values extracted from context")
        var fields: Map<String, String> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? = null
    }


    override fun promptSegment(): String {
        val templatePath = typeConfig?.template_file
        if (templatePath.isNullOrBlank()) {
            return "PdfForm - Configuration Error: No template_file specified in TypeConfig."
        }

        val templateFile = root.resolve(templatePath)
        if (!templateFile.exists()) {
            return "PdfForm - Configuration Error: Template file '$templatePath' not found in working directory."
        }

        return try {
            PDDocument.load(templateFile.toFile()).use { doc ->
                val acroForm = doc.documentCatalog.acroForm
                val fieldList = acroForm?.fields?.joinToString("\n") { field ->
                    "  - ${getFieldDescription(field)}"
                } ?: "  (No fields found)"

                """


PdfForm - Fill out a PDF form template.
- template_file: $templatePath
- output_file: Path for the generated PDF.
- fields: Map of field names to values.
- flatten: (Boolean) Make fields read-only.
Available Fields:
${fieldList.lines().take(10).joinToString("\n")}${if (fieldList.lines().size > 10) "\n  ... (truncated)" else ""}
                """.trimIndent()
            }
        } catch (e: Exception) {
            log.warn("Error reading PDF template for prompt generation", e)
            "PdfForm - Error reading template '$templatePath': ${e.message}"
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


      task.ui.pool.submit {
        val transcript = task.transcript()
        val tabs = TabbedDisplay(task)
        val statusTask = tabs.newTask("Status")
        statusTask.header("PDF Form Filler")
        val statusBuffer = statusTask.add("Initializing PDF task...".renderMarkdown())
        try {
          val templatePath = typeConfig?.template_file
            ?: throw IllegalStateException("Template file not configured in TaskTypeConfig")

          val templateFile = root.resolve(templatePath).toFile()
          if (!templateFile.exists()) throw IllegalStateException("Template file not found: $templatePath")

          val outputPath = executionConfig?.output_file ?: "filled_form.pdf"
          val outputFile = root.resolve(outputPath).toFile()

          val availableFields = PDDocument.load(templateFile).use { doc ->
            doc.documentCatalog.acroForm?.fields?.map { getFieldDescription(it) } ?: emptyList()
          }

          val api = defaultSmart
          val extractedFields = if (api != null && messages.isNotEmpty()) {
            statusBuffer?.setLength(0)
            statusBuffer?.append("Analyzing context to extract form data...".renderMarkdown())
            statusTask.update()
            val parsingChatter = defaultFast.getChildClient(task)
            val defaultChatter = api.getChildClient(task)

            val prompt = """
                        Analyze the provided context and extract values for the following PDF form fields.
                        
                        Available Fields:
                        ${availableFields.joinToString("\n") { "* $it" }}
                        
                        Context:
                        ${messages.joinToString("\n\n")}
                        
                        Return a JSON object with a 'fields' map containing the extracted values.
                        Only include fields where a value can be confidently determined from the context.
                    """.trimIndent()

            val parsedAgent = ParsedAgent(
              resultClass = FormData::class.java,
              prompt = prompt,
              model = defaultChatter,
              parsingChatter = parsingChatter,
              temperature = 0.1
            )

            parsedAgent.answer(listOf(prompt)).obj.fields
          } else {
            emptyMap()
          }

          val configFields = executionConfig?.fields ?: emptyMap()
          val fieldData = extractedFields + configFields
          val fieldDataJson = fieldData.entries.joinToString(",\n  ", "{\n  ", "\n}") {
            "\"${it.key}\": \"${it.value.replace("\"", "\\\"")}\""
          }
          val extractionTask = tabs.newTask("Extraction")
          extractionTask.expandable("Extracted Fields", "<pre>$fieldDataJson</pre>")

          transcript?.write("## PDF Form Fill Execution\n".toByteArray())
          transcript?.write("* **Template:** `$templatePath`\n".toByteArray())
          transcript?.write("* **Output:** `$outputPath`\n".toByteArray())
          transcript?.write(
            """
                    <details>
                    <summary>Field Data JSON</summary>
                    
                    ```json
                    $fieldDataJson
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )

          statusBuffer?.setLength(0)
          statusBuffer?.append("Ready to fill ${fieldData.size} fields into $outputPath.".renderMarkdown())
          statusTask.update()


          val performFill = {
            outputFile.parentFile?.mkdirs()
            val bytes = ByteArrayOutputStream().use { baos ->
              PDDocument.load(templateFile).use { doc ->
                val acroForm = doc.documentCatalog.acroForm
                  ?: throw IllegalStateException("No AcroForm found in template PDF")

                val missingFields = mutableListOf<String>()
                fieldData.forEach { (key, value) ->
                  val field = acroForm.getField(key)
                  if (field != null) {
                    field.setValue(value)
                  } else {
                    missingFields.add(key)
                  }
                }

                if (missingFields.isNotEmpty()) {
                  val msg = "Warning: Fields not found in PDF: $missingFields"
                  log.warn(msg)
                  transcript?.write("\n> $msg\n".toByteArray())
                  task.verbose(msg)
                }

                if (executionConfig?.flatten == true) acroForm.flatten()
                doc.save(baos)
              }




              baos.toByteArray()
            }



            outputFile.writeBytes(bytes)
            val fileUrl = task.saveFile(outputPath, bytes)
            val successMsg = typeConfig?.resultTemplate
              ?.replace("{output_file}", outputPath)
              ?.replace("{count}", fieldData.size.toString())
              ?: "Successfully created $outputPath with ${fieldData.size} fields filled."
            transcript?.write("\n### Success\n$successMsg\n".toByteArray())
            statusBuffer?.setLength(0)
            statusBuffer?.append("**Complete!**".renderMarkdown())
            statusTask.update()
            val resultTask = tabs.newTask("Result")
            resultTask.add(
              """
                        ### PDF Generated
                        $successMsg
                        [Download PDF]($fileUrl){.btn .btn-primary .mt-2}
                    """.trimIndent().renderMarkdown()
            )
            task.complete()
            resultFn(successMsg)
          }
          if (orchestrationConfig.autoFix) {
            performFill()
          } else {
            statusTask.add(acceptButtonFooter(task.ui) { performFill() })
          }

        } catch (e: Exception) {
          task.error(e)
          log.error("Error in PdfFormTask", e)
          transcript?.write(
            """
                    <details>
                    <summary>Stack Trace</summary>
                    
                    ```
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )
          throw e
        } finally {
          transcript?.close()
        }

      }
    }

    private fun getFieldDescription(field: PDField): String {
        val type = field.javaClass.simpleName.replace("PD", "").replace("Field", "")
        val options = when (field) {
            is PDChoice -> " [Options: ${field.options.joinToString(", ")}]"
            is PDCheckBox -> " [Checked: ${field.onValue}, Unchecked: Off]"
            is PDRadioButton -> " [Options: ${field.exportValues.joinToString(", ")}]"
            else -> ""
        }
        return "\"${field.fullyQualifiedName}\" ($type)$options"
    }

    companion object {
        private val log = LoggerFactory.getLogger(PdfFormTask::class.java)

        @JvmStatic val PdfForm = TaskType(
          name = "PdfForm",
          category = "File",
          taskClass = PdfFormTask::class.java,
          executionConfigClass = PdfFormExecutionConfig::class.java,
          taskSettingsClass = PdfFormTypeConfig::class.java,
          description = "Fills out a specific PDF form template with provided data.",
          tooltipHtml = """
                      Fills fields in a pre-configured PDF template.
                      <ul>
                        <li><b>Requires:</b> A template PDF file defined in the global Type Config.</li>
                        <li><b>Output:</b> A new PDF file with the fields populated.</li>
                        <li>Automatically lists available fields from the template to the Planner.</li>
                      </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/ReadDocumentsTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

class ReadDocumentsTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ReadDocumentsTaskExecutionConfigData?
) : AbstractTask<ReadDocumentsTask.ReadDocumentsTaskExecutionConfigData, ReadDocumentsTask.ReadDocumentsTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    protected val codeFiles = mutableMapOf<Path, String>()

    class ReadDocumentsTaskTypeConfig(
        task_type: String? = ReadDocuments.name,
        name: String? = null
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name
    ), ValidatedObject

    class ReadDocumentsTaskExecutionConfigData(
        @Description("The specific questions or topics to be addressed in the inquiry")
        val inquiry_questions: List<String>? = null,
        @Description("The goal or purpose of the inquiry")
        val inquiry_goal: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("A description of the task to be performed")
        task_description: String? = null,
        @Description("List of task IDs that this task depends on")
        task_dependencies: List<String>? = null,
        @Description("The current state of the task")
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = ReadDocuments.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (inquiry_questions.isNullOrEmpty() && inquiry_goal.isNullOrBlank()) return "Either inquiry_questions or inquiry_goal must be provided"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment() = (if (!orchestrationConfig.autoFix) """
  ReadDocuments - Deeply analyze project files and provide comprehensive technical insights or answers to specific questions.
    * inquiry_questions: Specific technical questions to address.
    * inquiry_goal: The high-level goal of the inquiry.
    * input_files: File patterns (e.g. **/*.kt) to examine.
  """ else """
  ReadDocuments - Directly answer questions or provide a report using the LLM. Reading files is optional.
    * inquiry_questions: Specific technical questions to address.
    * inquiry_goal: The high-level goal of the inquiry.
    * input_files: Optional file patterns to examine if relevant.
  """)

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


      val transcript = task.transcript()
      try {
        task.ui.pool.submit {
          try {
            val tabs = TabbedDisplay(task)
            val analysisTask = tabs.newTask("Analysis")
            val filesTask = tabs.newTask("Files")

            log.info("Starting ReadDocumentsTask execution")
            val fileContext = getInputFileCode()

            transcript?.write("## Files Read\n<details><summary>File Context</summary>\n\n$fileContext\n\n</details>\n".toByteArray())

            filesTask.header("Files Read", level = 3)
            filesTask.add(fileContext.renderMarkdown())
            filesTask.complete()

            val toInput = { it: String ->
              messages + listOf(
                fileContext,
                it,
              ).filter { it.isNotBlank() }
            }

            val taskConfig: ReadDocumentsTaskExecutionConfigData? = this.executionConfig
            val typeConfig = typeConfig ?: throw RuntimeException("Type configuration is missing")
            val insightActor = ChatAgent(
              name = "Insight",
              prompt = """
                            Create code for a new file that fulfills the specified requirements and context.
                            Given a detailed user request, break it down into smaller, actionable tasks suitable for software development.
                            Compile comprehensive information and insights on the specified topic.
                            Provide a comprehensive overview, including key concepts, relevant technologies, best practices, and any potential challenges or considerations.
                            Ensure the information is accurate, up-to-date, and well-organized to facilitate easy understanding.
                            """.trimIndent(),
              model = (typeConfig.model?.let { this.orchestrationConfig.instance(it) }
                ?: defaultSmart).getChildClient(analysisTask),
              temperature = this.orchestrationConfig.temperature,
            )
            val inquiryResult = Discussable(
              task = analysisTask,
              heading = "Read Documents",
              userMessage = {
                "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                  taskConfig?.inquiry_questions?.joinToString("\n")
                }\nGoal: ${taskConfig?.inquiry_goal}\n${JsonUtil.toJson(data = this)}"
              },
              initialResponse = { prompt ->
                val input = toInput(prompt)
                transcript?.write("# Analysis Request\n\n${input.joinToString("\n\n")}\n\n".toByteArray())
                insightActor.answer(input)
              },
              outputFn = { it.renderMarkdown() },
              reviseResponse = { history ->
                val contextMessages = (messages + listOf(fileContext))
                  .filter { it.isNotBlank() }
                  .map { it to Role.user }
                insightActor.answer((contextMessages + history).map { it.first }.toList())
              }
            ).call()

            analysisTask.complete()
            log.info("ReadDocumentsTask completed successfully")
            resultFn(inquiryResult!!)
          } catch (e: Exception) {
            task.error(e)
            log.error("Error in ReadDocumentsTask: ${e.message}", e)
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
            throw e
          }
        }
      } finally {
        transcript?.close()
      }
    }


    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .filterNotNull()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = if (!isTextFile(file)) {
                    extractDocumentContent(file)
                } else {
                    codeFiles[file.toPath()] ?: file.readText()
                }
                "# $relativePath\n\n$TRIPLE_TILDE\n$content\n$TRIPLE_TILDE"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(ReadDocumentsTask::class.java)
        @JvmStatic val ReadDocuments = TaskType(
          name = "ReadDocuments",
          category = "File",
          taskClass = ReadDocumentsTask::class.java,
          executionConfigClass = ReadDocumentsTaskExecutionConfigData::class.java,
          taskSettingsClass = ReadDocumentsTaskTypeConfig::class.java,
          description = "Deeply analyze project files and provide comprehensive technical insights or answers to specific questions.",
          tooltipHtml = """
                      Analyzes project files and provides detailed technical insights using the LLM.
                      <ul>
                        <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
                        <li>Reading files is optional; the task can operate with or without file input</li>
                        <li>User feedback and iterative refinement are supported but not required</li>
                        <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
                        <li>Can answer detailed questions about code, design, or project context</li>
                        <li>Supports both one-shot and interactive discussion modes</li>
                        <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
                      </ul>
                      """,
        )

        fun getAvailableFiles(
            path: Path,
            treatDocumentsAsText: Boolean = false,
        ): List<String> {
            return try {
                listOf(
                    FileSelectionUtils.filteredWalkAsciiTree(
                        path.toFile(),
                        20,
                        treatDocumentsAsText = treatDocumentsAsText,
                        render = { file: File ->
                            val name = file.name
                            val size: String? = if (file.isFile) {
                                val length = file.length()
                                when {
                                    length < 1024 -> "$length B"
                                    length < 1024 * 1024 -> String.format("%.2f KB", length / 1024.0)
                                    length < 1024 * 1024 * 1024 -> String.format("%.2f MB", length / (1024.0 * 1024.0))
                                    else -> String.format("%.2f GB", length / (1024.0 * 1024.0 * 1024.0))
                                }
                            } else {
                                null
                            }
                            if (size != null) "$name ($size)" else name
                        }
                    )
                )
            } catch (e: Exception) {
                log.error("Error listing available files", e)
                listOf("Error listing files: ${e.message}")
            }
        }

        private val textExtensions = setOf(
            "txt",
            "md",
            "kt",
            "java",
            "js",
            "ts",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "css",
            "html",
            "xml",
            "json",
            "yaml",
            "yml",
            "properties",
            "gradle",
            "maven"
        )

        fun isTextFile(file: File): Boolean {
            return textExtensions.contains(file.extension.lowercase())
        }

        fun extractDocumentContent(file: File) = try {
            file.getDocumentReader().use { reader ->
                when (reader) {
                    is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                    else -> reader.getText()
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
            try {
                file.readText()
            } catch (e2: Exception) {
                "Error reading file: ${e2.message}"
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/SegmentedImageGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

class SegmentedImageGenerationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: SegmentedImageGenerationConfig?
) : AbstractFileTask<SegmentedImageGenerationTask.SegmentedImageGenerationConfig>(
  orchestrationConfig,
  planTask
) {

  class SegmentedImageGenerationConfig(
    @Description("The output file path for the final high-res image")
    var output_file: String? = "",
    @Description("List of prompts, one for each level of detail. The first is for the base image.")
    var prompts: List<String>? = null,
    @Description("Optional input file path to use as the base image instead of generating one.")
    var input_file: String? = null,
    @Description("Upscale factor per level (e.g., 2.0 for 2x size, 4.0 for 4x size)")
    var upscale_factor: Double = 2.0,
    @Description("Minimum width/height (in pixels) of a region to trigger refinement.")
    var min_region_size: Int = 128,
    @Description("Maximum aspect ratio for regions (e.g., 3.0 means max 3:1 or 1:3).")
    var max_aspect_ratio: Double = 3.0,
    @Description("Output image file extension (e.g., 'jpg', 'png').")
    var extension: String = "png",
    @Description("Overlap between tiles as a fraction of tile size (0.0-1.0). Defaults to 0.15.")
    var tile_overlap: Double = 0.15,
    @Description("Whether to attempt to re-align sub-images to the base image to prevent drift.")
    var retarget_subimages: Boolean = true,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = SegmentedImageGeneration.name,
    task_description = prompts?.firstOrNull(),
    task_dependencies = task_dependencies,
    state = state
  ) {

    override fun validate(): String? {
      if (output_file.isNullOrBlank()) return "Output file must be specified"
      if (prompts.isNullOrEmpty()) return "Prompts must be specified"
      return ValidatedObject.validateFields(this)
    }
  }

  data class GenerationRegion(
    @Description("A descriptive label for this region")
    val label: String = "",
    @Description("Detailed visual description of what should be in this region")
    val visual_description: String = "",
    @Description("X coordinate (0-1000 scale)")
    val x: Int = 0,
    @Description("Y coordinate (0-1000 scale)")
    val y: Int = 0,
    @Description("Width (0-1000 scale)")
    val width: Int = 0,
    @Description("Height (0-1000 scale)")
    val height: Int = 0
  )

  data class RegionPlan(
    @Description("List of regions that require higher resolution detail")
    val regions: List<GenerationRegion> = emptyList()
  )

  override fun promptSegment() = """
SegmentedImageGeneration - Generates ultra-high-resolution images via recursive upscaling and semantic segmentation
* Use for: Creating complex scenes where specific objects need high detail.
* Mechanism: Generates a base image, uses AI to identify regions needing detail, upscales them, and refines recursively.
      """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val outputFile = executionConfig?.output_file ?: "output.${executionConfig?.extension ?: "jpg"}"
    val prompts = executionConfig?.prompts ?: return resultFn("No prompts specified")
    val inputFile = executionConfig?.input_file
    val maxDepth = prompts.size - 1
    val upscaleFactor = executionConfig.upscale_factor
    val minSize = executionConfig.min_region_size
    val maxAspectRatio = executionConfig.max_aspect_ratio
    val tileOverlap = executionConfig.tile_overlap

    val writeModel = orchestrationConfig.defaultImage.getChildClient(task)
    val planModel = orchestrationConfig.defaultSmart.getChildClient(task)

    val transcript = task.transcript()
    val tabs = TabbedDisplay(task)
    val logTab = tabs.newTask("Progress")
    val semaphore = Semaphore(0)

    task.ui.pool.submit {
      try {
        task.header("Starting Segmented Generation: $outputFile", level = 2)
        val configInfo = buildString {
          appendLine("**Configuration**")
          appendLine("* **Output File:** $outputFile")
          if (inputFile != null) appendLine("* **Input File:** $inputFile")
          appendLine("* **Prompts:** ${prompts.joinToString(", ")}")
          appendLine("* **Max Depth:** $maxDepth")
          appendLine("* **Upscale Factor:** $upscaleFactor")
          appendLine("* **Min Region Size:** $minSize")
          appendLine("* **Retarget Sub-images:** ${executionConfig.retarget_subimages}")
        }
        logTab.add(configInfo.renderMarkdown())
        transcript?.write("# Segmented Image Generation\n\n$configInfo\n\n".toByteArray())


        val imageAgent = ImageProcessingAgent(
          prompt = "Generate an image based on the user description",
          name = "BaseGenerator",
          model = writeModel
        )
        // 1. Generate Root Image


        var currentImage: BufferedImage
        if (inputFile != null) {
          logTab.add("Loading base image from $inputFile...".renderMarkdown())
          val file = root.resolve(inputFile).toFile()
          if (!file.exists()) throw RuntimeException("Input file not found: $inputFile")
          currentImage = ImageIO.read(file) ?: throw RuntimeException("Failed to read input image")
          val baseLink = task.linkTo(inputFile)
          tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""
          transcript?.write("## Base Image\nLoaded from: $inputFile\n\n![Base Image]($baseLink)\n\n".toByteArray())
        } else {
          logTab.add("Generating base image...".renderMarkdown())

          val baseResult = imageAgent.answer(listOf(ImageAndText(prompts[0])))
          currentImage = baseResult.image ?: throw RuntimeException("Failed to generate base image")

          val baseLink = saveImage(currentImage, "base_generation.${executionConfig.extension}", task)
          tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""
          transcript?.write("## Base Generation\nPrompt: ${prompts[0]}\n\n![Base Image]($baseLink)\n\n".toByteArray())
          logTab.add("Base image generated (${currentImage.width}x${currentImage.height})".renderMarkdown())
        }

        // 2. Recursive Refinement
        fun processLevel(
          sourceImage: BufferedImage,
          currentDepth: Int,
          parentDescription: String,
          path: String
        ): BufferedImage {
          if (currentDepth >= maxDepth) {
            logTab.add("Max depth $maxDepth reached. Returning image.".renderMarkdown())
            return sourceImage
          }
          // Calculate target dimensions for this level's return (Max Depth resolution)
          val levelsRemaining = maxDepth - currentDepth
          val totalScale = Math.pow(upscaleFactor, levelsRemaining.toDouble())
          val fullWidth = (sourceImage.width * totalScale).toInt()
          val fullHeight = (sourceImage.height * totalScale).toInt()
          val fullResCanvas = BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB)
          val gFull = fullResCanvas.createGraphics()
          gFull.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          gFull.drawImage(sourceImage, 0, 0, fullWidth, fullHeight, null)

          logTab.add("Analyzing depth $currentDepth for refinement regions...".renderMarkdown())

          // Analyze for regions using LLM
          val plan = try {
            ParsedImageAgent(
              resultClass = RegionPlan::class.java,
              model = planModel,
              prompt = """
                Analyze this image (Depth $currentDepth).
                The goal is to refine the image to match the prompt: "${prompts.getOrElse(currentDepth + 1) { prompts.last() }}".
                Identify rectangular regions that contain significant detail, objects, or textures that would benefit from upscaling and refinement.
                Focus on main subjects, faces, complex textures, or text.
                Output coordinates on a 0-1000 scale.
              """.trimIndent()
            ).answer(listOf(ImageAndText(text = "", image = sourceImage))).obj
          } catch (e: Exception) {
            log.error("Failed to plan regions", e)
            RegionPlan(emptyList())
          }

          if (plan.regions.isEmpty()) {
            logTab.add("No regions found at depth $currentDepth, stopping recursion.".renderMarkdown())
            gFull.dispose()
            return fullResCanvas
          }

          transcript?.write(buildString {
            appendLine("### Depth $currentDepth Analysis")
            appendLine("Found ${plan.regions.size} regions to refine.")
            appendLine("<details><summary>Regions JSON</summary>")
            appendLine()
            appendLine("```json")
            appendLine(plan.toJson())
            appendLine("```")
            appendLine("</details>")
            appendLine()
            appendLine("| Label | Description | Coords (x,y,w,h) |")
            appendLine("|---|---|---|")
            plan.regions.forEach {
              appendLine("| ${it.label} | ${it.visual_description} | ${it.x}, ${it.y}, ${it.width}, ${it.height} |")
            }
            appendLine()
          }.toByteArray())
          
          // Generate segmentation debug image
          val debugImage = BufferedImage(sourceImage.width, sourceImage.height, BufferedImage.TYPE_INT_ARGB)
          val gDebug = debugImage.createGraphics()
          gDebug.drawImage(sourceImage, 0, 0, null)
          gDebug.stroke = BasicStroke(2f)
          plan.regions.forEach { region ->
            val rx = (region.x * sourceImage.width / 1000.0).toInt()
            val ry = (region.y * sourceImage.height / 1000.0).toInt()
            val rw = (region.width * sourceImage.width / 1000.0).toInt()
            val rh = (region.height * sourceImage.height / 1000.0).toInt()
            gDebug.color = Color.RED
            gDebug.drawRect(rx, ry, rw, rh)
            if (rw > 40 && rh > 20) {
              gDebug.color = Color(0, 0, 0, 150)
              gDebug.fillRect(rx, ry, rw.coerceAtMost(200), 20)
              gDebug.color = Color.WHITE
              gDebug.drawString(region.label, rx + 5, ry + 15)
            }
          }
          gDebug.dispose()
          val debugName = "${path}_segmentation.${executionConfig.extension}"
          val debugLink = saveImage(debugImage, debugName, task)
          tabs["Depth $currentDepth Map"] = """<img src="$debugLink" style="max-width: 100%;" />"""
          logTab.add("Saved segmentation map for depth $currentDepth".renderMarkdown())
          transcript?.write("\n![Segmentation Map]($debugLink)\n\n".toByteArray())


          // Create intermediate image for next level processing (1 step up)
          val nextLevelWidth = (sourceImage.width * upscaleFactor).toInt()
          val nextLevelHeight = (sourceImage.height * upscaleFactor).toInt()
          val nextLevelImage = BufferedImage(nextLevelWidth, nextLevelHeight, BufferedImage.TYPE_INT_ARGB)
          val gNext = nextLevelImage.createGraphics()
          gNext.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          gNext.drawImage(sourceImage, 0, 0, nextLevelWidth, nextLevelHeight, null)
          gNext.dispose()

          // Process each region
          plan.regions.sortedBy { it.width * it.height }.forEachIndexed { idx, region ->

            // Convert 0-1000 coordinates to the next level dimensions
            var regionX = (region.x * nextLevelWidth / 1000.0).toInt()
            var regionY = (region.y * nextLevelHeight / 1000.0).toInt()
            var regionW = (region.width * nextLevelWidth / 1000.0).toInt()
            var regionH = (region.height * nextLevelHeight / 1000.0).toInt()

            // Enforce aspect ratio
            val currentRatio = regionW.toDouble() / regionH
            if (currentRatio > maxAspectRatio) {
              val newH = (regionW / maxAspectRatio).toInt()
              regionY -= (newH - regionH) / 2
              regionH = newH
            } else if (currentRatio < 1.0 / maxAspectRatio) {
              val newW = (regionH / maxAspectRatio).toInt()
              regionX -= (newW - regionW) / 2
              regionW = newW
            }

            // Fit to bounds
            if (regionX < 0) regionX = 0
            if (regionY < 0) regionY = 0
            if (regionX + regionW > nextLevelWidth) regionX = nextLevelWidth - regionW
            if (regionY + regionH > nextLevelHeight) regionY = nextLevelHeight - regionH
            if (regionX < 0) regionX = 0
            if (regionY < 0) regionY = 0
            val startX = regionX
            val startY = regionY
            val actualW = (regionW).coerceAtMost(nextLevelWidth - startX)
            val actualH = (regionH).coerceAtMost(nextLevelHeight - startY)

            if (actualW < minSize || actualH < minSize) {
              logTab.add("Skipping region '${region.label}' (too small: ${actualW}x${actualH} < $minSize)".renderMarkdown())
              return@forEachIndexed
            }

            logTab.add("Refining region: **${region.label}** at depth ${currentDepth + 1}".renderMarkdown())

            // Crop from the ALREADY UPSCALED canvas
            val crop = nextLevelImage.getSubimage(startX, startY, actualW, actualH)

            // Refine using Img2Img
            val levelPrompt = prompts[currentDepth + 1]
            val refinePrompt = """
                $levelPrompt.
                Subject: ${region.label}.
                Description: ${region.visual_description}.
                Keep the content, composition, and structure largely the same as the input.
                Enhance fine details, textures, and sharpness.
            """.trimIndent()

            val regionSlug = region.label.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
            val nextPath = "${path}_${idx}_$regionSlug"

            transcript?.write(buildString {
              appendLine("#### Cropped Region: ${region.label}")
              appendLine("*Prompt:* $refinePrompt")
              appendLine("![Refined Region](${
                saveImage(
                  crop,
                  "${nextPath}_crop.${executionConfig.extension}",
                  task
                )
              })")
              appendLine()
            }.toByteArray())

            try {
              val refinedResult = imageAgent.answer(
                listOf(
                  ImageAndText(
                    text = refinePrompt,
                    image = crop // Pass the blurry crop as the init image
                  )
                )
              )

              val refinedImage = refinedResult.image
              if (refinedImage != null) {

                // Recurse to get max resolution version of this region
                val highResRegion = processLevel(refinedImage, currentDepth + 1, levelPrompt, nextPath)

                // Align refinedCrop to crop to find valid bounds
                val bounds = if(executionConfig.retarget_subimages) ImagePatchLocalization.findBounds(
                  refinedImage,
                  crop,
                  ImagePatchLocalization.SubImageBounds(0, 0, refinedImage.width, refinedImage.height, 0.0)
                ) else {
                  ImagePatchLocalization.SubImageBounds(0, 0, refinedImage.width, refinedImage.height, 0.0)
                }

                // Scale bounds to max resolution
                val regionScale = Math.pow(upscaleFactor, (maxDepth - (currentDepth + 1)).toDouble())
                val srcX = (bounds.x * regionScale).toInt().coerceIn(0, highResRegion.width)
                val srcY = (bounds.y * regionScale).toInt().coerceIn(0, highResRegion.height)
                val srcW = (bounds.width * regionScale).toInt().coerceAtMost(highResRegion.width - srcX)
                val srcH = (bounds.height * regionScale).toInt().coerceAtMost(highResRegion.height - srcY)
                val sourceRegion = highResRegion.getSubimage(srcX, srcY, srcW, srcH)

                // Paste into fullResCanvas
                val destX = (startX * regionScale).toInt()
                val destY = (startY * regionScale).toInt()
                val destW = (actualW * regionScale).toInt()
                val destH = (actualH * regionScale).toInt()

                val featherSize = (Math.min(destW, destH) * 0.05).toInt().coerceAtLeast(1)
                val feathered = featherImage(sourceRegion, featherSize,
                  destX <= 1, destY <= 1,
                  destX + destW >= fullWidth - 1, destY + destH >= fullHeight - 1
                )
                gFull.drawImage(feathered, destX, destY, destW, destH, null)

                // Save debug snippet
                transcript?.write(buildString {
                  appendLine("#### Refined Region: ${region.label}")
                  appendLine("*Prompt:* $refinePrompt")
                  appendLine("![Refined Region](${
                    saveImage(
                      highResRegion,
                      "${nextPath}_refined.${executionConfig.extension}",
                      task
                    )
                  })")
                  appendLine()
                }.toByteArray())
              }
            } catch (e: Exception) {
              log.error("Failed to refine region ${region.label}", e)
              logTab.add("Failed to refine ${region.label}: ${e.message}".renderMarkdown())
              transcript?.write("\n**Error refining ${region.label}:** ${e.message}\n".toByteArray())
            }
          }
          gFull.dispose()
          return fullResCanvas
        }

        val finalImage = processLevel(currentImage, 0, prompts[0], "root")

        // Save Final
        val finalPath = root.resolve(outputFile)
        ImageIO.write(finalImage, executionConfig.extension, finalPath.toFile())
        val finalLink = task.linkTo(outputFile)

        tabs["Final Result"] = buildString {
          appendLine("<p>Saved to <a href=\"$finalLink\">$outputFile</a> (${finalImage.width}x${finalImage.height})</p>")
          appendLine("<a href=\"$finalLink\" target=\"_blank\"><img src=\"$finalLink\" style=\"max-width: 100%; border: 1px solid #ccc;\" /></a>")
        }
        transcript?.write(buildString {
          appendLine("## Final Result")
          appendLine("**File:** $outputFile")
          appendLine("**Dimensions:** ${finalImage.width}x${finalImage.height}")
          appendLine("![Final Image]($finalLink)")
        }.toByteArray())

        val completionMsg =
          "Generated ultra-high-resolution image saved to $outputFile. Final dimensions: ${finalImage.width}x${finalImage.height}."
        if (orchestrationConfig.autoFix) {
          task.safeComplete(completionMsg.renderMarkdown(), log)
          resultFn(completionMsg)
        } else {
          val footer = acceptButtonFooter(task.ui) {
            task.complete()
            semaphore.release()
          }
          task.add(footer)
          semaphore.acquire()
          resultFn(completionMsg)
        }

      } catch (e: Exception) {
        task.error(e)
        log.error("Error in SegmentedImageGeneration", e)
        transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
        resultFn("Error generating image: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  private fun saveImage(image: BufferedImage, name: String, task: SessionTask): String {
    val file = root.resolve(name)
    ImageIO.write(image, executionConfig?.extension, file.toFile())
    return task.linkTo(name)
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    return ui.hrefLink("Accept Image") { fn() }
  }
  private fun featherImage(image: BufferedImage, feather: Int, left: Boolean, top: Boolean, right: Boolean, bottom: Boolean): BufferedImage {
    val w = image.width
    val h = image.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(image, 0, 0, null)
    g.dispose()
    for (y in 0 until h) {
      for (x in 0 until w) {
        var factor = 1.0f
        if (!left && x < feather) factor = Math.min(factor, x.toFloat() / feather)
        if (!right && x >= w - feather) factor = Math.min(factor, (w - x).toFloat() / feather)
        if (!top && y < feather) factor = Math.min(factor, y.toFloat() / feather)
        if (!bottom && y >= h - feather) factor = Math.min(factor, (h - y).toFloat() / feather)
        if (factor < 1.0f) {
          val rgb = out.getRGB(x, y)
          val alpha = (rgb shr 24) and 0xFF
          val newAlpha = (alpha * factor).toInt()
          out.setRGB(x, y, (rgb and 0x00FFFFFF) or (newAlpha shl 24))
        }
      }
    }
    return out
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(SegmentedImageGenerationTask::class.java)
    @JvmStatic val SegmentedImageGeneration = TaskType(
      name = "SegmentedImageGeneration",
      category = "Writing",
      taskClass = SegmentedImageGenerationTask::class.java,
      executionConfigClass = SegmentedImageGenerationConfig::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Recursively generates and upscales images using semantic segmentation for high detail.",
      tooltipHtml = """
      Generates a base image, identifies semantic regions of interest using AI, and recursively upscales and refines them.
      Useful for:
      <ul>
          <li>Complex illustrations with many characters</li>
          <li>Detailed landscapes with distinct focal points</li>
          <li>Images where grid-based upscaling might cut through objects</li>
      </ul>
                  """,
    )
  }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/TiledImageGenerationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class TiledImageGenerationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: TiledImageGenerationConfig?
) : AbstractFileTask<TiledImageGenerationTask.TiledImageGenerationConfig>(
  orchestrationConfig,
  planTask
) {

  class TiledImageGenerationConfig(
    @Description("The output file path for the final high-res image")
    var output_file: String? = "",
    @Description("List of prompts, one for each level of detail. The first is for the base image.")
    var prompts: List<String>? = null,
    @Description("Optional input file path to use as the base image instead of generating one.")
    var input_file: String? = null,
    @Description("Upscale factor per level (e.g., 2.0 for 2x size, 4.0 for 4x size)")
    var upscale_factor: Double = 2.0,
    @Description("Minimum width/height (in pixels) of a region to trigger refinement.")
    var min_region_size: Int = 128,
    @Description("Maximum aspect ratio for regions (e.g., 3.0 means max 3:1 or 1:3).")
    var max_aspect_ratio: Double = 3.0,
    @Description("Output image file extension (e.g., 'jpg', 'png').")
    var extension: String = "png",
    @Description("Grid schedule (e.g. ['2x2', '3x3']). Defaults to alternating.")
    var grid_schedule: List<Int>? = null,
    @Description("Overlap between tiles as a fraction of tile size (0.0-1.0). Defaults to 0.15.")
    var tile_overlap: Double = 0.15,
    @Description("Task dependencies")
    task_dependencies: List<String>? = null,
    @Description("Task state")
    state: TaskState? = TaskState.Pending,
    @Description("Whether to retarget sub-images for better alignment")
    var retarget_subimages: Boolean = true,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = TiledImageGeneration.name,
    task_description = prompts?.firstOrNull(),
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (output_file.isNullOrBlank()) return "Output file must be specified"
      if (prompts.isNullOrEmpty()) return "Prompts must be specified"
      return ValidatedObject.validateFields(this)
    }
  }

  // Reuse the region definition from Decomposition, but simplified for generation
  data class GenerationRegion(
    @Description("A descriptive label for this region")
    val label: String = "",
    @Description("Detailed visual description of what should be in this region")
    val visual_description: String = "",
    @Description("X coordinate (0-1000 scale)")
    val x: Int = 0,
    @Description("Y coordinate (0-1000 scale)")
    val y: Int = 0,
    @Description("Width (0-1000 scale)")
    val width: Int = 0,
    @Description("Height (0-1000 scale)")
    val height: Int = 0
  )

  data class RegionPlan(
    @Description("List of regions that require higher resolution detail")
    val regions: List<GenerationRegion> = emptyList()
  )

  override fun promptSegment() = """
    ### TiledImageGeneration
    Generates ultra-high-resolution images via recursive upscaling and refinement.
    * **Use when:** You need posters, detailed maps, or scenes where standard generation lacks resolution.
    * **Mechanism:** Generates a base image, tiles it, and uses AI to refine each tile at higher resolution recursively.
  """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val outputFile = executionConfig?.output_file ?: "output.${executionConfig?.extension ?: "jpg"}"
    val prompts = executionConfig?.prompts ?: return resultFn("No prompts specified")
    val inputFile = executionConfig?.input_file
    val maxDepth = prompts.size - 1
    val upscaleFactor = executionConfig.upscale_factor
    val minSize = executionConfig.min_region_size
    val maxAspectRatio = executionConfig.max_aspect_ratio
    val gridSchedule = executionConfig.grid_schedule
    val tileOverlap = executionConfig.tile_overlap

    val writeModel = orchestrationConfig.defaultImage.getChildClient(task)


    task.ui.pool.submit {
      val transcript = task.transcript()
      val tabs = TabbedDisplay(task)
      val logTab = tabs.newTask("Progress")
      try {
        log.info("Starting Iterative Generation for $outputFile")
        val configInfo = buildString {
          appendLine("**Configuration**")
          appendLine("* **Output File:** $outputFile")
          if (inputFile != null) appendLine("* **Input File:** $inputFile")
          appendLine("* **Prompts:** ${prompts.joinToString(", ")}")
          appendLine("* **Max Depth:** $maxDepth")
          appendLine("* **Upscale Factor:** $upscaleFactor")
          appendLine("* **Min Region Size:** $minSize")
          appendLine("* **Tile Overlap:** $tileOverlap")
        }
        task.header("Iterative Generation: $outputFile", level = 2)
        logTab.add(configInfo.renderMarkdown())
        transcript?.write("# Iterative Image Generation\n\n$configInfo\n\n".toByteArray())


        val imageAgent = ImageProcessingAgent(
          prompt = "Generate an image based on the user description",
          name = "BaseGenerator",
          model = writeModel
        )
        // 1. Generate Root Image


        var currentImage: BufferedImage
        if (inputFile != null) {
          logTab.add("Loading base image from $inputFile...".renderMarkdown())
          val file = root.resolve(inputFile).toFile()
          if (!file.exists()) throw RuntimeException("Input file not found: $inputFile")
          currentImage = ImageIO.read(file) ?: throw RuntimeException("Failed to read input image")
          val baseLink = task.linkTo(inputFile)
          tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""
          transcript?.write("## Base Image\nLoaded from: $inputFile\n\n![Base Image]($baseLink)\n\n".toByteArray())
        } else {
          logTab.add("Generating base image...".renderMarkdown())

          val baseResult = imageAgent.answer(listOf(ImageAndText(prompts[0])))
          currentImage = baseResult.image ?: throw RuntimeException("Failed to generate base image")

          val baseLink = saveImage(currentImage, "base_generation.${executionConfig.extension}", task)
          tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""
          transcript?.write("## Base Generation\nPrompt: ${prompts[0]}\n\n![Base Image]($baseLink)\n\n".toByteArray())
          logTab.add("Base image generated (${currentImage.width}x${currentImage.height})".renderMarkdown())
        }

        // 2. Recursive Refinement
        fun processLevel(
          sourceImage: BufferedImage,
          currentDepth: Int,
          parentDescription: String,
          path: String
        ): BufferedImage {
          if (currentDepth >= maxDepth) {
            logTab.add("Max depth $maxDepth reached. Returning image.".renderMarkdown())
            return sourceImage
          }
          // Calculate target dimensions for this level's return (Max Depth resolution)
          val levelsRemaining = maxDepth - currentDepth
          val totalScale = Math.pow(upscaleFactor, levelsRemaining.toDouble())
          val fullWidth = (sourceImage.width * totalScale).toInt()
          val fullHeight = (sourceImage.height * totalScale).toInt()
          val fullResCanvas = BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB)
          val gFull = fullResCanvas.createGraphics()
          gFull.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          gFull.drawImage(sourceImage, 0, 0, fullWidth, fullHeight, null)

          logTab.add("Analyzing depth $currentDepth for refinement regions...".renderMarkdown())

          // Analyze for regions

          val regions = mutableListOf<GenerationRegion>()
          val (cols, rows) = if (gridSchedule != null && gridSchedule.isNotEmpty()) {
            val spec = gridSchedule.getOrElse(currentDepth) { gridSchedule.last() }
            val parts = spec.let { listOf(it,it) }
            if (parts.size == 2) {
              parts[0] to parts[1]
            } else {
              val n = (1.0 / executionConfig.upscale_factor).toInt()
              n to n
            }
          } else {
            if (currentDepth % 2 == 0) 2 to 2 else 3 to 3
          }
          val overlap = tileOverlap
          val widthStep = 1000.0 / cols
          val heightStep = 1000.0 / rows
          for (y in 0 until rows) {
            for (x in 0 until cols) {
              val x1 = (x * widthStep - widthStep * overlap).toInt().coerceAtLeast(0)
              val y1 = (y * heightStep - heightStep * overlap).toInt().coerceAtLeast(0)
              val x2 = ((x + 1) * widthStep + widthStep * overlap).toInt().coerceAtMost(1000)
              val y2 = ((y + 1) * heightStep + heightStep * overlap).toInt().coerceAtMost(1000)
              regions.add(
                GenerationRegion(
                  label = "Region ($x,$y)",
                  visual_description = "Detail of $parentDescription",
                  x = x1,
                  y = y1,
                  width = x2 - x1,
                  height = y2 - y1
                )
              )
            }
          }
          val plan = RegionPlan(regions)

          if (plan.regions.isEmpty()) {
            logTab.add("No regions found at depth $currentDepth, stopping recursion.".renderMarkdown())
            gFull.dispose()
            return fullResCanvas
          }



          transcript?.write(buildString {
            appendLine("### Depth $currentDepth Analysis")
            appendLine("Found ${plan.regions.size} regions to refine.")
            appendLine("<details><summary>Regions JSON</summary>")
            appendLine()
            appendLine("```json")
            appendLine(plan.toJson())
            appendLine("```")
            appendLine("</details>")
            appendLine()
            appendLine("| Label | Description | Coords (x,y,w,h) |")
            appendLine("|---|---|---|")
            plan.regions.forEach {
              appendLine("| ${it.label} | ${it.visual_description} | ${it.x}, ${it.y}, ${it.width}, ${it.height} |")
            }
            appendLine()
          }.toByteArray())
          // Generate segmentation debug image
          val debugImage = BufferedImage(sourceImage.width, sourceImage.height, BufferedImage.TYPE_INT_ARGB)
          val gDebug = debugImage.createGraphics()
          gDebug.drawImage(sourceImage, 0, 0, null)
          gDebug.stroke = BasicStroke(2f)
          plan.regions.forEach { region ->
            val rx = (region.x * sourceImage.width / 1000.0).toInt()
            val ry = (region.y * sourceImage.height / 1000.0).toInt()
            val rw = (region.width * sourceImage.width / 1000.0).toInt()
            val rh = (region.height * sourceImage.height / 1000.0).toInt()
            gDebug.color = Color.RED
            gDebug.drawRect(rx, ry, rw, rh)
            if (rw > 40 && rh > 20) {
              gDebug.color = Color(0, 0, 0, 150)
              gDebug.fillRect(rx, ry, rw.coerceAtMost(200), 20)
              gDebug.color = Color.WHITE
              gDebug.drawString(region.label, rx + 5, ry + 15)
            }
          }
          gDebug.dispose()
          val debugName = "${path}_segmentation.${executionConfig.extension}"
          val debugLink = saveImage(debugImage, debugName, task)
          tabs["Depth $currentDepth Map"] = """<img src="$debugLink" style="max-width: 100%;" />"""
          logTab.add("Saved segmentation map for depth $currentDepth".renderMarkdown())
          transcript?.write("\n![Segmentation Map]($debugLink)\n\n".toByteArray())


          // Create intermediate image for next level processing (1 step up)
          val nextLevelWidth = (sourceImage.width * upscaleFactor).toInt()
          val nextLevelHeight = (sourceImage.height * upscaleFactor).toInt()
          val nextLevelImage = BufferedImage(nextLevelWidth, nextLevelHeight, BufferedImage.TYPE_INT_ARGB)
          val gNext = nextLevelImage.createGraphics()
          gNext.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          gNext.drawImage(sourceImage, 0, 0, nextLevelWidth, nextLevelHeight, null)
          gNext.dispose()

          // Process each region
          plan.regions.forEachIndexed { idx, region ->

            // Convert 0-1000 coordinates to the next level dimensions
            var regionX = (region.x * nextLevelWidth / 1000.0).toInt()
            var regionY = (region.y * nextLevelHeight / 1000.0).toInt()
            var regionW = (region.width * nextLevelWidth / 1000.0).toInt()
            var regionH = (region.height * nextLevelHeight / 1000.0).toInt()

            // Enforce aspect ratio
            val currentRatio = regionW.toDouble() / regionH
            if (currentRatio > maxAspectRatio) {
              val newH = (regionW / maxAspectRatio).toInt()
              regionY -= (newH - regionH) / 2
              regionH = newH
            } else if (currentRatio < 1.0 / maxAspectRatio) {
              val newW = (regionH / maxAspectRatio).toInt()
              regionX -= (newW - regionW) / 2
              regionW = newW
            }

            // Fit to bounds
            if (regionX < 0) regionX = 0
            if (regionY < 0) regionY = 0
            if (regionX + regionW > nextLevelWidth) regionX = nextLevelWidth - regionW
            if (regionY + regionH > nextLevelHeight) regionY = nextLevelHeight - regionH
            if (regionX < 0) regionX = 0
            if (regionY < 0) regionY = 0
            val startX = regionX
            val startY = regionY
            val actualW = (regionW).coerceAtMost(nextLevelWidth - startX)
            val actualH = (regionH).coerceAtMost(nextLevelHeight - startY)

            if (actualW < minSize || actualH < minSize) {
              logTab.add("Skipping region '${region.label}' (too small: ${actualW}x${actualH} < $minSize)".renderMarkdown())
              return@forEachIndexed
            }

            logTab.add("Refining region: **${region.label}** at depth ${currentDepth + 1}".renderMarkdown())

            // Crop from the ALREADY UPSCALED canvas (which currently holds the blurry bicubic version)
            val crop = nextLevelImage.getSubimage(startX, startY, actualW, actualH)

            // Refine using Img2Img
            // We use a prompt that combines the specific detail with the global context
            val levelPrompt = prompts[currentDepth + 1]
            val refinePrompt = """
                $levelPrompt.
                Subject: ${region.label}.
                Description: ${region.visual_description}.
                Keep the content, composition, and structure largely the same as the input.
                Enhance fine details, textures, and sharpness.
            """.trimIndent()

            try {
              val refinedResult = imageAgent.answer(
                listOf(
                  ImageAndText(
                    text = refinePrompt,
                    image = crop // Pass the blurry crop as the init image
                  )
                )
              )

              val refinedCrop = refinedResult.image
              if (refinedCrop != null) {
                val regionSlug = region.label.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
                val nextPath = "${path}_${idx}_$regionSlug"

                transcript?.write(buildString {
                  appendLine("#### Cropped Region: ${region.label}")
                  appendLine("*Prompt:* $refinePrompt")
                  appendLine("![Refined Region](${
                    saveImage(
                      refinedCrop,
                      "${nextPath}_crop.${executionConfig.extension}",
                      task
                    )
                  })")
                  appendLine()
                }.toByteArray())

                // Recurse to get max resolution version of this region
                val highResRegion = processLevel(refinedCrop, currentDepth + 1, levelPrompt, nextPath)

                // Align refinedCrop to crop to find valid bounds
                val bounds = if(executionConfig.retarget_subimages) ImagePatchLocalization.findBounds(
                  refinedCrop,
                  crop,
                  ImagePatchLocalization.SubImageBounds(0, 0, refinedCrop.width, refinedCrop.height, 0.0)
                ) else {
                  ImagePatchLocalization.SubImageBounds(0, 0, crop.width, crop.height, 0.0)
                }

                // Scale bounds to max resolution
                val regionScale = Math.pow(upscaleFactor, (maxDepth - (currentDepth + 1)).toDouble())
                val srcX = (bounds.x * regionScale).toInt().coerceIn(0, highResRegion.width)
                val srcY = (bounds.y * regionScale).toInt().coerceIn(0, highResRegion.height)
                val srcW = (bounds.width * regionScale).toInt().coerceAtMost(highResRegion.width - srcX)
                val srcH = (bounds.height * regionScale).toInt().coerceAtMost(highResRegion.height - srcY)
                val sourceRegion = highResRegion.getSubimage(srcX, srcY, srcW, srcH)

                // Paste into fullResCanvas
                val destX = (startX * regionScale).toInt()
                val destY = (startY * regionScale).toInt()
                val destW = (actualW * regionScale).toInt()
                val destH = (actualH * regionScale).toInt()

                val featherSize = (Math.min(destW, destH) * 0.05).toInt().coerceAtLeast(1)
                val feathered = featherImage(sourceRegion, featherSize,
                  destX <= 1, destY <= 1,
                  destX + destW >= fullWidth - 1, destY + destH >= fullHeight - 1
                )
                gFull.drawImage(feathered, destX, destY, destW, destH, null)

                // Save debug snippet
                transcript?.write(buildString {
                  appendLine("#### Refined Region: ${region.label}")
                  appendLine("*Prompt:* $refinePrompt")
                  appendLine("![Refined Region](${
                    saveImage(
                      highResRegion,
                      "${nextPath}_refined.${executionConfig.extension}",
                      task
                    )
                  })")
                  appendLine()
                }.toByteArray())
              }
            } catch (e: Exception) {
              log.error("Failed to refine region ${region.label}", e)
              logTab.add("Failed to refine ${region.label}: ${e.message}".renderMarkdown())
              transcript?.write("\n**Error refining ${region.label}:** ${e.message}\n".toByteArray())
            }
          }
          gFull.dispose()
          return fullResCanvas
        }

        val finalImage = processLevel(currentImage, 0, prompts[0], "root")

        // Save Final
        val finalPath = root.resolve(outputFile)
        ImageIO.write(finalImage, executionConfig.extension, finalPath.toFile())
        val finalLink = task.linkTo(outputFile)

        tabs["Final Result"] = buildString {
          appendLine("<p>Saved to <a href=\"$finalLink\">$outputFile</a> (${finalImage.width}x${finalImage.height})</p>")
          appendLine("<a href=\"$finalLink\" target=\"_blank\"><img src=\"$finalLink\" style=\"max-width: 100%; border: 1px solid #ccc;\" /></a>")
        }
        transcript?.write(buildString {
          appendLine("## Final Result")
          appendLine("**File:** $outputFile")
          appendLine("**Dimensions:** ${finalImage.width}x${finalImage.height}")
          appendLine("![Final Image]($finalLink)")
        }.toByteArray())


        val completionMsg =
          "Generated ultra-high-resolution image saved to $outputFile. Final dimensions: ${finalImage.width}x${finalImage.height}."
        if (orchestrationConfig.autoFix) {
          task.safeComplete(completionMsg.renderMarkdown(), log)
          resultFn(completionMsg)
        } else {
          val footer = acceptButtonFooter(task.ui) {
            task.safeComplete(completionMsg.renderMarkdown(), log)
            resultFn(completionMsg)
          }
          task.add(footer.renderMarkdown())
        }

      } catch (e: Exception) {
        // Triple Log Rule
        task.error(e)
        log.error("Error in TiledImageGenerationTask: ${e.message}")
        transcript?.write(
          """
          <details>
          <summary>Stack Trace</summary>

          ```
          ${e.stackTraceToString()}
          ```
          </details>
        """.trimIndent().toByteArray()
        )
        resultFn("Error generating image: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }


  private fun saveImage(image: BufferedImage, name: String, task: SessionTask): String {
    val file = root.resolve(name)
    ImageIO.write(image, executionConfig?.extension, file.toFile())
    return task.linkTo(name)
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    return ui.hrefLink("Accept Image") { fn() }
  }
  private fun featherImage(image: BufferedImage, feather: Int, left: Boolean, top: Boolean, right: Boolean, bottom: Boolean): BufferedImage {
    val w = image.width
    val h = image.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(image, 0, 0, null)
    g.dispose()
    for (y in 0 until h) {
      for (x in 0 until w) {
        var factor = 1.0f
        if (!left && x < feather) factor = Math.min(factor, x.toFloat() / feather)
        if (!right && x >= w - feather) factor = Math.min(factor, (w - x).toFloat() / feather)
        if (!top && y < feather) factor = Math.min(factor, y.toFloat() / feather)
        if (!bottom && y >= h - feather) factor = Math.min(factor, (h - y).toFloat() / feather)
        if (factor < 1.0f) {
          val rgb = out.getRGB(x, y)
          val alpha = (rgb shr 24) and 0xFF
          val newAlpha = (alpha * factor).toInt()
          out.setRGB(x, y, (rgb and 0x00FFFFFF) or (newAlpha shl 24))
        }
      }
    }
    return out
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(TiledImageGenerationTask::class.java)
    @JvmStatic val TiledImageGeneration = TaskType(
      name = "TiledImageGeneration",
      category = "Writing",
      taskClass = TiledImageGenerationTask::class.java,
      executionConfigClass = TiledImageGenerationConfig::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Recursively generates and upscales images for high detail.",
      tooltipHtml = """
      Generates a base image, identifies regions of interest, and recursively upscales and refines them using generative AI.
      Useful for:
      <ul>
          <li>Large format posters</li>
          <li>Detailed maps or "Where's Waldo" style scenes</li>
          <li>Images requiring text or small details legible at high zoom</li>
      </ul>
                  """,
    )
  }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/WriteHtmlTask.kt

```
package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

class WriteHtmlTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: WriteHtmlTaskExecutionConfigData?
) : AbstractFileTask<WriteHtmlTask.WriteHtmlTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class WriteHtmlTaskExecutionConfigData(
        @Description("The HTML file to be created (relative path, must end with .html)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., existing HTML templates, related files)")
        related_files: List<String>? = null,
        @Description("Detailed description of the HTML page to create, including layout, styling, and functionality requirements")
        task_description: String? = null,
        @Description("Whether to generate images for the HTML page")
        val generate_images: Boolean = false,
        @Description("Number of images to generate (valid range: 0-10)")
        var image_count: Int = 0,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = WriteHtml.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            // Validate that files list is not empty
            if (files.isNullOrEmpty()) {
                return "WriteHtmlTaskExecutionConfigData: files list cannot be null or empty"
            }

            // Validate that the file has .html extension
          val htmlFile = files!!.first()
            if (!htmlFile.endsWith(".html", ignoreCase = true)) {
                return "WriteHtmlTaskExecutionConfigData: file must have .html extension, got: $htmlFile"
            }

            // Validate image count
            if (image_count < 0 || image_count > 10) {
                image_count = image_count.coerceIn(0, 10)
            }

            // Call parent validation
            return super.validate()
        }
    }

    init {
        // Validate the configuration on initialization
        planTask?.validate()?.let { errorMessage ->
            throw ValidatedObject.ValidationError(errorMessage, planTask)
        }
    }

    override fun promptSegment(): String {
        return """
WriteHtml - Create a complete HTML file with embedded CSS and JavaScript
  ** Specify the HTML file path in the files array (must end with .html)
  ** Provide a detailed description of the page requirements including:
     - Layout and structure
     - Styling requirements (colors, fonts, spacing, etc.)
     - Interactive functionality needed
     - Any specific HTML5 features to use
     - Image requirements (if generate_images is enabled)
  ** The generated HTML will be a complete, self-contained document with:
     - Proper HTML5 structure (<!DOCTYPE html>, <html>, <head>, <body>)
     - Embedded CSS within <style> tags in the <head>
     - Embedded JavaScript within <script> tags (typically before </body>)
     - Responsive design considerations
     - Modern best practices
     - Generated images (if enabled) embedded as base64 or saved as separate files
  ** Related files can include existing HTML templates or reference files
  ** Output will be presented for review before being written to disk
        """
    }


    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        // Validate configuration before execution
        executionConfig?.validate()?.let { errorMessage ->
            resultFn("VALIDATION ERROR: $errorMessage")
            return
        }

        val htmlFiles = executionConfig?.files ?: emptyList()
        if (htmlFiles.isEmpty()) {
            resultFn("CONFIGURATION ERROR: No HTML file specified")
            return
        }

        val htmlFile = htmlFiles.first()
        if (!htmlFile.endsWith(".html", ignoreCase = true)) {
            resultFn("CONFIGURATION ERROR: File must have .html extension: $htmlFile")
            return
        }

        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")
        val transcriptStream = task.transcript("html_generation_${htmlFile.substringBeforeLast(".")}")
        val transcriptWriter = transcriptStream?.bufferedWriter()

        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = defaultSmart.getChildClient(task)

        overviewTask.header("Creating HTML File: $htmlFile", level = 2)
        overviewTask.add(MarkdownUtil.renderMarkdown("""
            **Status:** 🔄 Initializing generation process...
        """.trimIndent(), ui = ui))

        val contextFiles = getInputFileCode()
        transcriptWriter?.write("# HTML Generation Transcript\n\n")
        transcriptWriter?.write("## Creating HTML File: `$htmlFile`\n\n")
        val priorCode = getPriorCode(agent.executionState)
        // Create directory for images if needed
        val imageDir = if (executionConfig?.generate_images == true && executionConfig.image_count > 0) {
            val dir = root.resolve(htmlFile).parent.resolve("images")
            dir.toFile().mkdirs()
            dir
        } else null

        // Step 1: Generate HTML structure with classes
        val htmlPrompt = """
You are an expert web developer tasked with creating a complete, self-contained HTML file.

## Requirements:
${executionConfig?.task_description ?: "Create an HTML page as specified"}

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Create a complete HTML5 document structure with proper semantic elements
2. Include appropriate meta tags (viewport, charset, etc.)
3. Add class names to elements that will need styling or JavaScript interaction
4. Use descriptive, semantic class names (e.g., "nav-menu", "hero-section", "card-container")
5. Include placeholder comments for where CSS and JavaScript will be added
6. Do NOT include any CSS or JavaScript yet - just the HTML structure with classes
7. Add comments to explain the purpose of major sections

## Output Format:
Provide the HTML structure within a code block:
```html
<!DOCTYPE html>
<html>
<head>
    <!-- CSS will be added here -->
</head>
<body>
    <!-- HTML structure with classes -->
</body>
<!-- JavaScript will be added here -->
</html>
```
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = htmlPrompt,
            model = api,
        )

        val htmlTask = tabs.newTask("HTML Structure")
        htmlTask.header("Step 1: Generating HTML Structure", level = 3)
        transcriptWriter?.write("### Step 1: Generating HTML Structure\n\n")
        transcriptWriter?.write("**Prompt:**\n```\n$htmlPrompt\n```\n\n")

        val htmlResponse = chatAgent.answer(listOf("Generate the HTML structure as per the requirements."))
        transcriptWriter?.write("**Response:**\n$htmlResponse\n\n")

        val htmlStructure = extractCodeFromResponse(htmlResponse, "html")

        if (htmlStructure.isEmpty()) {
            transcriptWriter?.close()
            resultFn("ERROR: Failed to generate HTML structure")
            return
        }
        htmlTask.add(MarkdownUtil.renderMarkdown("```html\n$htmlStructure\n```", ui = ui))

        // Step 1.5: Generate images if enabled
        val generatedImages = mutableListOf<Pair<String, String>>() // filename to description
        if (executionConfig?.generate_images == true && executionConfig.image_count > 0 && imageDir != null) {
            val imageTask = tabs.newTask("Images")
            imageTask.header("Step 1.5: Generating Images", level = 3)
            transcriptWriter?.write("### Step 1.5: Generating Images\n\n")
            val imagePrompt = """
Based on the following HTML page description and structure, identify ${executionConfig.image_count} key images that should be generated.
## Page Description:
${executionConfig.task_description}
## HTML Structure:
```html
$htmlStructure
```
For each image, provide:
1. A descriptive filename ending in .png (e.g., "hero-banner.png", "product-showcase.png")
2. A detailed visual description for image generation (be specific about style, colors, composition)

Note: All images will be generated as PNG files by an AI image model.

Format your response as:
IMAGE: filename.png
DESCRIPTION: detailed visual description
IMAGE: another-image.png
DESCRIPTION: another detailed description
      """.trimIndent()
            transcriptWriter?.write("**Prompt:**\n```\n$imagePrompt\n```\n\n")
            val imageSpecResponse = chatAgent.answer(toInput(imagePrompt))
            transcriptWriter?.write("**Response:**\n$imageSpecResponse\n\n")
            // Parse image specifications
            val imageSpecs = parseImageSpecs(imageSpecResponse)
            val imageChat = orchestrationConfig.defaultImage.getChildClient(task)
            // Generate each image
            imageSpecs.take(executionConfig.image_count).forEach { (filename, description) ->
                val filename = filename
                try {
                    imageTask.add("Generating image: <b>$filename</b>...", additionalClasses = "text-info")
                    val imageAgent = ImageProcessingAgent(
                        prompt = "Create a high-quality image for a web page based on the description",
                        model = imageChat,
                        temperature = 0.7,
                    )
                    val result = imageAgent.answer(
                        listOf(
                            ImageAndText(
                                """
 Create an image for a web page with the following description:
 $description
Output format: PNG image
 Style: Modern, professional, web-optimized
          """
                            )
                        )
                    )
                    val image = result.image
                    val imageFile = task.resolveUserFile(filename)
                    ImageIO.write(image, "png", imageFile)
                    generatedImages.add(filename to description)
                    val imageLink = task.linkTo(filename)
                    val markdown = "✅ Generated: [$filename]($imageLink)"
                    imageTask.add(MarkdownUtil.renderMarkdown(markdown, ui = ui))
                    imageTask.image(image!!)

                    transcriptWriter?.write("**Generated Image:** $filename\n")
                    transcriptWriter?.write("**Description:** $description\n")
                    transcriptWriter?.write("**Prompt Used:** ${result.text}\n\n$markdown\n\n".transcriptFilter())
                    log.debug("Generated image: $filename")
                } catch (e: Exception) {
                    log.error("Failed to generate image: $filename", e)
                    imageTask.error(e)
                    transcriptWriter?.write("**Error generating $filename:** ${e.message}\n\n")
                }
            }
        }

        // Step 2: Generate JavaScript
        val jsPrompt = """
Based on the following HTML structure, generate the JavaScript code needed for interactivity.

## HTML Structure:
```html
$htmlStructure
```

## Requirements:
${executionConfig?.task_description ?: "Add appropriate JavaScript functionality"}

## Instructions:
1. Generate JavaScript that adds interactivity to the HTML elements
2. Use modern JavaScript (ES6+) features
3. Add event listeners for user interactions
4. Include any necessary DOM manipulation
5. Add comments to explain the functionality
6. Ensure the code is efficient and follows best practices

## Output Format:
Provide only the JavaScript code within a code block:
```javascript
// JavaScript code here
```
        """.trimIndent()

        val jsTask = tabs.newTask("JavaScript")
        jsTask.header("Step 2: Generating JavaScript", level = 3)
        transcriptWriter?.write("### Step 2: Generating JavaScript\n\n")
        transcriptWriter?.write("**Prompt:**\n```\n$jsPrompt\n```\n\n")

        val jsResponse = chatAgent.answer(toInput(jsPrompt))
        transcriptWriter?.write("**Response:**\n$jsResponse\n\n")
        val jsCode = extractCodeFromResponse(jsResponse, "javascript", "js")
        jsTask.add(MarkdownUtil.renderMarkdown("```javascript\n$jsCode\n```", ui = ui))

        // Step 3: Generate CSS
        val cssPrompt = """
Based on the following HTML structure, generate the CSS styling.

## HTML Structure:
```html
$htmlStructure
```

## Requirements:
${executionConfig?.task_description ?: "Create appropriate styling"}

## Instructions:
1. Generate CSS that styles all the HTML elements
2. Create a visually appealing, modern design
3. Ensure responsive design (mobile-first approach)
4. Use CSS Grid and/or Flexbox for layouts
5. Include hover effects and transitions where appropriate
6. Use a consistent color scheme and typography
7. Add comments to organize the CSS sections
8. Follow CSS best practices and naming conventions

## Output Format:
Provide only the CSS code within a code block:
```css
/* CSS code here */
```
        """.trimIndent()

        val cssTask = tabs.newTask("CSS")
        cssTask.header("Step 3: Generating CSS", level = 3)

        transcriptWriter?.write("### Step 3: Generating CSS\n\n")
        transcriptWriter?.write("**Prompt:**\n```\n$cssPrompt\n```\n\n")

        val cssResponse = chatAgent.answer(toInput(cssPrompt))
        transcriptWriter?.write("**Response:**\n$cssResponse\n\n")
        val cssCode = extractCodeFromResponse(cssResponse, "css")
        cssTask.add(MarkdownUtil.renderMarkdown("```css\n$cssCode\n```", ui = ui))

        // Step 4: Combine everything into a complete HTML file
        val htmlWithImages =
            insertImageReferences(htmlStructure, generatedImages, chatAgent, toInput, transcriptWriter, htmlTask, ui)
        val completeHtml = combineHtmlComponents(htmlWithImages, cssCode, jsCode, generatedImages)

        if (completeHtml.isEmpty()) {
            transcriptWriter?.close()
            resultFn("ERROR: Failed to generate valid HTML content")
            return
        }

        task.add("""<a href="${task.linkTo(htmlFile)}">${htmlFile}</a> created""")
        val outputPath = root.resolve(htmlFile)
        transcriptWriter?.write("### Step 4: Final HTML Output\n\n")
        transcriptWriter?.write("```html\n$completeHtml\n```\n\n")


        outputPath.toFile().parentFile?.mkdirs()
        outputPath.toFile().writeText(completeHtml)
        transcriptWriter?.write("**Result:** Successfully wrote $htmlFile (auto-applied)\n")
        transcriptWriter?.close()

        val finalSummary = """
            # HTML Generation Complete
            - **File:** [$htmlFile](${task.linkTo(htmlFile)})
            - **Images:** ${generatedImages.size} generated
            - **Components:** HTML5, CSS3 (embedded), JavaScript (embedded)
            - **Timestamp:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            
            The file has been successfully written to the workspace.
        """.trimIndent()
        
        overviewTask.add(MarkdownUtil.renderMarkdown(finalSummary, ui = ui))
        task.safeComplete("Successfully wrote $htmlFile", log)
        resultFn(finalSummary)
    }

    private fun extractCodeFromResponse(response: String, vararg languages: String): String {
        // Try to extract code from code blocks with specified languages
        for (lang in languages) {
            val codeBlockRegex = "```$lang\\s*([\\s\\S]*?)```".toRegex()
            val match = codeBlockRegex.find(response)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }

        // Try generic code block
        val genericBlockRegex = "```\\s*([\\s\\S]*?)```".toRegex()
        val genericMatch = genericBlockRegex.find(response)
        if (genericMatch != null) {
            return genericMatch.groupValues[1].trim()
        }

        return ""
    }

    private fun combineHtmlComponents(
        htmlStructure: String,
        cssCode: String,
        jsCode: String,
        generatedImages: List<Pair<String, String>> = emptyList()
    ): String {
        // Parse the HTML structure and insert CSS and JavaScript
        val headEndIndex = htmlStructure.indexOf("</head>", ignoreCase = true)
        val bodyEndIndex = htmlStructure.indexOf("</body>", ignoreCase = true)

        if (headEndIndex == -1 || bodyEndIndex == -1) {
            log.error("Invalid HTML structure: missing </head> or </body> tags")
            return ""
        }

        val beforeHead = htmlStructure.substring(0, headEndIndex)
        var afterHeadBeforeBody = htmlStructure.substring(headEndIndex, bodyEndIndex)
        val afterBody = htmlStructure.substring(bodyEndIndex)
        // Insert image references if images were generated
        if (generatedImages.isNotEmpty()) {
            val imageComment = """
    <!-- Generated Images:
${generatedImages.joinToString("\n") { (filename, desc) -> "         - $filename: $desc" }}
    -->
""".trimIndent()
            afterHeadBeforeBody = afterHeadBeforeBody.replace("</head>", "$imageComment\n</head>")
        }

        return buildString {
            append(beforeHead)
            if (cssCode.isNotEmpty()) {
                append("\n    <style>\n")
                append(cssCode.prependIndent("        "))
                append("\n    </style>\n")
            }
            append(afterHeadBeforeBody)
            if (jsCode.isNotEmpty()) {
                append("\n    <script>\n")
                append(jsCode.prependIndent("        "))
                append("\n    </script>\n")
            }
            append(afterBody)
        }
    }

    private fun parseImageSpecs(response: String): List<Pair<String, String>> {
        val specs = mutableListOf<Pair<String, String>>()
        val lines = response.lines()
        var currentFilename: String? = null
        var currentDescription: String? = null
        for (line in lines) {
            when {
                line.startsWith("IMAGE:", ignoreCase = true) -> {
                    // Save previous spec if exists
                    if (currentFilename != null && currentDescription != null) {
                        specs.add(currentFilename to currentDescription)
                    }
                    currentFilename = line.substringAfter(":", "").trim()
                    currentDescription = null
                }

                line.startsWith("DESCRIPTION:", ignoreCase = true) -> {
                    currentDescription = line.substringAfter(":", "").trim()
                }

                currentDescription != null && line.isNotBlank() -> {
                    // Continue multi-line description
                    currentDescription += " " + line.trim()
                }
            }
        }
        // Save last spec
        if (currentFilename != null && currentDescription != null) {
            specs.add(currentFilename to currentDescription)
        }
        return specs
    }

    private fun insertImageReferences(
        htmlStructure: String,
        generatedImages: List<Pair<String, String>>,
        chatAgent: ChatAgent,
        toInput: (String) -> List<String>,
        transcriptWriter: java.io.BufferedWriter?,
        newTask: SessionTask,
        ui: SocketManager
    ): String {
        if (generatedImages.isEmpty()) {
            return htmlStructure
        }
        newTask.header("Step 3.5: Inserting Image References", level = 3)
        transcriptWriter?.write("### Step 3.5: Inserting Image References\n\n")
        val imageList = generatedImages.joinToString("\n") { (filename, description) ->
            "- $filename: $description"
        }
        val imageInsertPrompt = """
You need to insert image references into the HTML structure.
## Current HTML Structure:
```html
$htmlStructure
```
## Generated Images:
$imageList
## Instructions:
1. Insert <img> tags at appropriate locations in the HTML where these images should appear
2. Use the given PNG filename (e.g., "filename.png") for the src attribute
3. Add appropriate alt text based on the image description
4. Add appropriate class names for styling
5. Consider the semantic meaning of where each image should go (hero sections, content areas, etc.)
6. Maintain the existing HTML structure and class names
7. Do NOT add any CSS or JavaScript - just insert the <img> tags
## Output Format:
Provide the complete updated HTML structure within a code block:
```html
<!DOCTYPE html>
...
```
        """.trimIndent()
        transcriptWriter?.write("**Prompt:**\n```\n$imageInsertPrompt\n```\n\n")
        val imageInsertResponse = chatAgent.answer(toInput(imageInsertPrompt))
        transcriptWriter?.write("**Response:**\n$imageInsertResponse\n\n")
        val updatedHtml = extractCodeFromResponse(imageInsertResponse, "html")
        return if (updatedHtml.isNotEmpty()) {
            newTask.add("✅ Successfully inserted ${generatedImages.size} image reference(s)")
            updatedHtml
        } else {
            log.warn("Failed to insert image references, using original HTML structure")
            newTask.add(
                "⚠️ Failed to insert image references, using original structure",
                additionalClasses = "text-warning"
            )
            htmlStructure
        }
    }


    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Write File") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(WriteHtmlTask::class.java)
        @JvmStatic val WriteHtml = TaskType(
          name = "WriteHtml",
          category = "Writing",
          taskClass = WriteHtmlTask::class.java,
          executionConfigClass = WriteHtmlTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Create complete HTML files with embedded CSS and JavaScript",
          tooltipHtml = """
                        Creates standalone HTML files with embedded CSS and JavaScript.
                        <ul>
                          <li>Generates complete, self-contained HTML documents</li>
                          <li>Embeds CSS styles within &lt;style&gt; tags</li>
                          <li>Embeds JavaScript within &lt;script&gt; tags</li>
                          <li>Supports modern HTML5 features</li>
                          <li>Can generate images using AI image models</li>
                          <li>Automatically creates image directory and references</li>
                          <li>Interactive approval or auto-apply mode</li>
                          <li>Proper HTML structure and formatting</li>
                        </ul>
                      """,
        )
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

Here is the updated `README.md` for the `com.simiacryptus.cognotik.plan.tools.file` package, summarizing the extensive set of file-related tasks provided in the documentation.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/README.md
```markdown
# File and Content Tools

The `com.simiacryptus.cognotik.plan.tools.file` package provides a comprehensive suite of tools for interacting with the file system, analyzing documents, and generating rich media content using AI. These tasks are designed to be used within the Cognotik orchestration framework to automate complex workflows involving code, data, and visual assets.

## Core File Operations

These tasks handle fundamental file system interactions with AI-powered intelligence.

*   **[FileModificationTask](FileModificationTask.kt)**: The primary tool for code generation and refactoring. It creates or modifies files using AI-generated diffs, maintaining project standards and handling complex multi-file operations.
*   **[FileSearchTask](FileSearchTask.kt)**: Performs pattern-based searches (substring or regex) across project files, providing results with configurable context lines and organized by file.
*   **[FileAppendTask](FileAppendTask.kt)**: Allows for precise additions to the end of files without modifying existing content, ideal for logs, exports, or list updates.

## Document Analysis & Discussion

Tools for extracting knowledge and insights from existing project files and documents.

*   **[ReadDocumentsTask](ReadDocumentsTask.kt)** / **[DiscussionTask](DiscussionTask.kt)**: Deeply analyzes project files to provide technical insights, answer specific questions, or generate comprehensive reports and architectural reviews.
*   **[OCRTask](OCRTask.kt)**: Converts PDFs and images into Markdown text. It can also extract figures as separate images and capture form fields or metadata.
*   **[ImageDecompositionTask](ImageDecompositionTask.kt)**: Performs recursive, deep-dive analysis of images or document pages to find fine details, perform OCR on complex layouts, or identify specific objects.

## Data & Form Processing

Tasks focused on structured data extraction and document automation.

*   **[DataIngestTask](DataIngestTask.kt)**: An iterative tool that discovers Regex patterns to parse unstructured logs or text into structured formats like JSONL and CSV, including a search index.
*   **[PdfFormTask](PdfFormTask.kt)**: Automates the filling of PDF form templates using data extracted from the conversation context or provided configuration.

## Web & Presentation Generation

Tools for creating high-level content formats for communication and display.

*   **[WriteHtmlTask](WriteHtmlTask.kt)**: Generates complete, self-contained HTML5 documents with embedded CSS and JavaScript, supporting modern responsive design and optional AI-generated images.
*   **[GeneratePresentationTask](GeneratePresentationTask.kt)**: Creates professional Reveal.js presentations including speaker notes, custom styling, and optional AI-generated slide imagery.
*   **[IllustrateDocumentTask](IllustrateDocumentTask.kt)**: Intelligently analyzes Markdown or HTML documents to identify optimal locations for visual aids and generates/inserts contextually appropriate images.

## Image Generation & Artistic Tools

A variety of tasks for creating visual assets using generative AI models.

*   **[ImageGenerationTask](ImageGenerationTask.kt)**: Creates high-quality images from detailed text descriptions, utilizing reference files for style or context.
*   **[ImageTableTask](ImageTableTask.kt)**: Generates a grid of images based on a matrix of row and column labels, useful for style comparisons or product variations.
*   **[GenerateQRImageTask](GenerateQRImageTask.kt)**: Creates artistic, stylized QR codes that remain scannable, using AI to blend data with visual aesthetics.
*   **[GenerateSpriteSheetTask](GenerateSpriteSheetTask.kt)**: Generates game assets by creating a sprite sheet image and automatically extracting coordinate metadata into a JSON file.
*   **[ImageVariationTask](ImageVariationTask.kt)**: Decomposes an image to create "Find the Differences" style variations by applying specific visual changes to identified regions.

## Advanced High-Resolution Generation

Specialized tasks for creating ultra-high-resolution imagery through recursive refinement.

*   **[SegmentedImageGenerationTask](SegmentedImageGenerationTask.kt)**: Uses semantic segmentation to identify regions of interest and recursively upscales them to create complex, highly detailed scenes.
*   **[TiledImageGenerationTask](TiledImageGenerationTask.kt)**: Employs a recursive tiling strategy to generate images with extreme detail, suitable for large-format posters or detailed maps.

## Base Implementation

*   **[AbstractFileTask](AbstractFileTask.kt)**: The base class for file-oriented tasks, providing common utility functions for file selection, glob pattern matching, and content extraction from various file types (text, PDF, etc.).
```

### Summary of Changes
- Created a new `README.md` file in the `com.simiacryptus.cognotik.plan.tools.file` package directory.
- Categorized all 20 tasks found in the provided source code into logical groups (Core Operations, Analysis, Data Processing, Content Generation, Image Tools, etc.).
- Provided concise descriptions for each task based on their `TaskType` definitions and implementation details.
- Included links to the source files for easy navigation.
- Followed the project's documentation patterns as implied by the `index_docs.md` configuration.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/README.md</a> Updated
