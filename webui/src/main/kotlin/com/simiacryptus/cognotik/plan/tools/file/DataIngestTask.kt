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
      val transcript = task.newUserFileStream(transcriptFile())
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
                transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
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