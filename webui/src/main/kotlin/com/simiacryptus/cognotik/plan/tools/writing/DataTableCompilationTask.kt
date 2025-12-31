package com.simiacryptus.cognotik.plan.tools.writing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.*
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class DataTableCompilationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DataTableCompilationTaskExecutionConfigData?
) : AbstractTask<DataTableCompilationTask.DataTableCompilationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class DataTableCompilationTaskExecutionConfigData(
        @Description("List of file glob patterns to include in the data compilation")
        val file_patterns: List<String> = listOf(),
        @Description("REQUIRED: Output file path where the compiled data table will be saved (CSV or JSON)")
        val output_file: String = "compiled_data.json",
        @Description("Instructions for identifying rows in the data")
        val row_identification_instructions: String = "",
        @Description("Instructions for identifying columns in the data")
        val column_identification_instructions: String = "",
        @Description("Instructions for extracting cell data")
        val cell_extraction_instructions: String = "",
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = "DataTableCompilation",
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class Rows(val rows: List<Row> = listOf())
    data class Row(val id: String = "", val sourceFiles: List<String> = listOf())
    data class Columns(val columns: List<Column> = listOf())
    data class Column(val id: String = "", val name: String = "", val description: String = "")
    data class RowData(val rowId: String, val data: Map<String, String>)
    data class TableData(val rows: List<Map<String, Any>>, val columns: List<Column>)

    override fun promptSegment() = """
        DataTableCompilation - Compile structured data tables from multiple files
          ** Specify file glob patterns to include in the compilation
          ** Define instructions for identifying rows in the data
          ** Define instructions for identifying columns in the data
          ** Define instructions for extracting cell data
          ** Specify output file path for the compiled table
    """.trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = transcript(task)
        transcript?.let { out ->
            out.write("# Data Table Compilation Task\n\n".toByteArray())
            out.write("## Configuration\n\n".toByteArray())
            out.write("- File Patterns: ${executionConfig?.file_patterns?.joinToString(", ")}\n".toByteArray())
            out.write("- Output File: ${executionConfig?.output_file}\n\n".toByteArray())
        }

        task.header("Step 1: Collecting files from patterns", level = 2)
        val result = mutableListOf<Path>()
        val basePath = Paths.get(orchestrationConfig.absoluteWorkingDir ?: ".")
        executionConfig?.file_patterns?.forEach { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            Files.walk(basePath).use { paths ->
                paths
                    .filter { it.isRegularFile() }
                    .filter { matcher.matches(basePath.relativize(it)) }
                    .forEach { result.add(it) }
            }
        }
        val matchedFiles = result.distinct()
        if (matchedFiles.isEmpty()) {
            val errorMsg =
                "No files matched the provided patterns: ${executionConfig?.file_patterns?.joinToString(", ")}"
            transcript?.let { out ->
                out.write("### Error\n\n".toByteArray())
                out.write("$errorMsg\n\n".toByteArray())
            }
            task.error(Exception(errorMsg))
            resultFn(errorMsg)
            return
        }
        task.add("Found ${matchedFiles.size} files matching the patterns")
        transcript?.let { out ->
            out.write("## Step 1: File Collection\n\n".toByteArray())
            out.write("Found ${matchedFiles.size} files:\n\n".toByteArray())
            matchedFiles.forEach { file ->
                out.write("- ${file.name}\n".toByteArray())
            }
            out.write("\n".toByteArray())
        }

        val fileContentString = matchedFiles.joinToString("\n\n") { file ->
            val content = readFileContent(file)
            "### ${file.name}\n```\n${content.take(1000)}${if (content.length > 1000) "..." else ""}\n```"
        }

        val typeConfig = typeConfig ?: throw RuntimeException()
        val chatter =
            (typeConfig.model?.let { orchestrationConfig.instance(it) }
                ?: defaultSmart).getChildClient(task)
        val columnsResponse = ParsedAgent(
            name = "ColumnIdentifier",
            resultClass = Columns::class.java,
            exampleInstance = Columns(
                listOf(
                    Column(
                        id = "Name",
                        name = "Name of the fruit",
                        description = "The name of the fruit in the row"
                    ),
                    Column(
                        id = "Color",
                        name = "Color of the fruit",
                        description = "The color of the fruit in the row"
                    ),
                    Column(
                        id = "Taste",
                        name = "Taste of the fruit",
                        description = "The taste of the fruit in the row"
                    )
                )
            ),
            prompt = """
                Analyze the provided files and identify distinct columns for a data table based on the following instructions:
                ${executionConfig?.column_identification_instructions}

                For each column you identify:
                1. Assign a unique column ID - should be a short, descriptive string
                2. Provide a detailed description of what the column represents
            """.trimIndent(),
            model = chatter,
            parsingChatter = defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = TaskContextYamlDescriber(orchestrationConfig),
        ).answer(
            listOf(
                fileContentString
            ),
        )
        val columns = columnsResponse.obj
        val columnsList = columns.columns.map {
            Column(
                id = it.id,
                name = it.name,
                description = it.description,
            )
        }
        transcript?.let { out ->
            out.write("## Step 2: Column Identification\n\n".toByteArray())
            out.write("Identified ${columnsList.size} columns:\n\n".toByteArray())
            columnsList.forEach { col ->
                out.write("- **${col.name}** (${col.id}): ${col.description}\n".toByteArray())
            }
            out.write("\n".toByteArray())
        }

        val rowsList = ParsedAgent(
            name = "RowIdentifier",
            resultClass = Rows::class.java,
            exampleInstance = Rows(
                listOf(
                    Row(
                        id = "Apple",
                        sourceFiles = listOf("apples.md", "apple_recipes.md")
                    ),
                    Row(
                        id = "Banana",
                        sourceFiles = listOf("bananas.md", "banana_recipes.md")
                    )
                )
            ),
            prompt = """
                You are a data extraction agent that is building a data table.
                Analyze the provided files and identify ALL distinct rows found in the data:

                Special Instructions:
                ${executionConfig?.row_identification_instructions}

                For each row you identify:
                1. Assign a unique row ID - should be a short, descriptive string
                2. List the source files that contain data for this row
            """.trimIndent(),
            model = chatter,
            parsingChatter = defaultFast,
            temperature = orchestrationConfig.temperature,
            describer = TaskContextYamlDescriber(orchestrationConfig),
        ).answer(
            listOf(
                fileContentString,
                "Columns:\n" + columnsList.joinToString("\n") { "- ${it.id}: ${it.name} (${it.description})" }
            ),
        )

        task.add("Identified ${rowsList.obj.rows.size} rows")
        task.add("Identified ${columnsList.size} columns")
        transcript?.let { out ->
            out.write("## Step 3: Row Identification\n\n".toByteArray())
            out.write("Identified ${rowsList.obj.rows.size} rows:\n\n".toByteArray())
            rowsList.obj.rows.forEach { row ->
                out.write("- **${row.id}** (Sources: ${row.sourceFiles.joinToString(", ")})\n".toByteArray())
            }
            out.write("\n".toByteArray())
        }

        task.header("Step 4: Extracting cell data for each row", level = 2)
        val tableData = mutableListOf<Map<String, Any>>()
        val progressTotal = rowsList.obj.rows.size
        var progressCurrent = 0
        val statusBuffer = task.add("Initializing extraction...")

        rowsList.obj.rows.forEach { row ->
            progressCurrent++
            statusBuffer?.setLength(0)
            statusBuffer?.append("Processing row ${progressCurrent}/${progressTotal}: ${row.id}")
            task.update()
            val rowDataResponse = ParsedAgent(
                name = "CellExtractor",
                resultClass = RowData::class.java,
                exampleInstance = RowData(
                    rowId = "Apple",
                    data = mapOf(
                        "Name" to "Apple",
                        "Color" to "Red",
                        "Taste" to "Sweet"
                    )
                ),
                prompt = "Extract data for a data row for `${row.id}` from the provided source files.\n\n" +
                        "Expected Columns:\n${columnsList.joinToString("\n") { "- ${it.id}: ${it.name} (${it.description})" }}\n\n" +
                        "Special Instructions:\n${executionConfig?.cell_extraction_instructions}\n\n" +
                        "IMPORTANT: Respond with ONLY the single JSON object for the row `${row.id}`. Do NOT return a JSON array.",
                model = chatter,
                parsingChatter = defaultFast,
                temperature = orchestrationConfig.temperature,
                describer = TaskContextYamlDescriber(orchestrationConfig),
            ).answer(
                listOf(
                    "Source Files:\n" + row.sourceFiles.mapNotNull { fileName ->
                        matchedFiles.find { it.name == fileName || it.toString().endsWith(fileName) }
                    }.joinToString("\n\n") { file ->
                        "### ${file.name}\n```\n${readFileContent(file).indent("  ")}\n```"
                    }
                ),
            )

            val rowData = rowDataResponse.obj
            val rowMap = mutableMapOf<String, Any>()
            rowMap["rowId"] = row.id
            rowMap.putAll(rowData.data)

            tableData.add(rowMap)
            transcript?.let { out ->
                out.write("### Row: ${row.id}\n\n".toByteArray())
                rowData.data.forEach { (key, value) ->
                    out.write("- $key: $value\n".toByteArray())
                }
                out.write("\n".toByteArray())
            }
        }

        task.header("Step 5: Compiling and saving data table", level = 2)

        val outputPath = executionConfig?.output_file ?: "compiled_data.json"
        val outputFile = if (orchestrationConfig.absoluteWorkingDir != null) {
            File(orchestrationConfig.absoluteWorkingDir, outputPath)
        } else {
            File(outputPath)
        }

        outputFile.parentFile?.mkdirs()

        when {
            outputPath.endsWith(".json", ignoreCase = true) -> {

                val finalData = TableData(tableData, columnsList)
                val mapper = jacksonObjectMapper()
                mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, finalData)
            }

            outputPath.endsWith(".csv", ignoreCase = true) -> {

                BufferedWriter(FileWriter(outputFile)).use { writer ->

                    val header = columnsList.joinToString(",") { "\"${it.name.replace("\"", "\"\"")}\"" }
                    writer.write(header)
                    writer.newLine()

                    tableData.forEach { row ->
                        val rowValues = columnsList.map { column ->
                            val value = row[column.id]?.toString() ?: "N/A"
                            "\"${value.replace("\"", "\"\"")}\""
                        }
                        writer.write(rowValues.joinToString(","))
                        writer.newLine()
                    }
                }
            }

            outputPath.endsWith(".md", ignoreCase = true) -> {

                BufferedWriter(FileWriter(outputFile)).use { writer ->
                    writeMarkdown(columnsList, writer, tableData)
                }
            }

            outputPath.isBlank() -> {

            }

            else -> {

                val finalData = TableData(tableData, columnsList)
                val mapper = jacksonObjectMapper()
                mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, finalData)
            }
        }

        val resultMessage = ("""
      Data table compilation complete!
      - Processed ${matchedFiles.size} source files
      - Identified ${rowsList.obj.rows.size} rows and ${columnsList.size} columns
      - Saved compiled data to: ${outputFile.absolutePath}
    """.trimIndent() + "\n\n" + "### Compiled Data\n\n${
            StringWriter().use {
                BufferedWriter(it).use {
                    writeMarkdown(columnsList, it, tableData)
                }
                it.toString()
            }
        }").renderMarkdown()
        transcript?.let { out ->
            out.write("## Step 5: Final Results\n\n".toByteArray())
            out.write("### Summary\n\n".toByteArray())
            out.write("- Processed ${matchedFiles.size} source files\n".toByteArray())
            out.write("- Identified ${rowsList.obj.rows.size} rows and ${columnsList.size} columns\n".toByteArray())
            out.write("- Saved compiled data to: ${outputFile.absolutePath}\n\n".toByteArray())
            out.write("### Compiled Data Table\n\n".toByteArray())
            StringWriter().use { sw ->
                BufferedWriter(sw).use { bw ->
                    writeMarkdown(columnsList, bw, tableData)
                }
                out.write(sw.toString().toByteArray())
            }
        }

        resultFn(resultMessage)
        task.complete()
    }

    private fun writeMarkdown(
        columnsList: List<Column>,
        writer: BufferedWriter,
        tableData: MutableList<Map<String, Any>>
    ) {

        val header = columnsList.joinToString(" | ") { it.name }
        writer.write("| $header |")
        writer.newLine()

        val separator = columnsList.joinToString(" | ") { "---" }
        writer.write("| $separator |")
        writer.newLine()

        tableData.forEach { row ->
            val rowValues = columnsList.joinToString(" | ") { column ->
                val value = row[column.id]?.toString() ?: "N/A"
                value
            }
            writer.write("| $rowValues |")
            writer.newLine()
        }
    }

    private fun readFileContent(path: Path): String {
        return try {
            Files.readString(path)
        } catch (e: Exception) {
            log.warn("Failed to read file: $path", e)
            "ERROR: Could not read file content"
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DataTableCompilationTask::class.java)
    }
}