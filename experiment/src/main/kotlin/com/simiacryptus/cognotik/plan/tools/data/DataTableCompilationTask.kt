package com.simiacryptus.cognotik.plan.tools.data

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.StringWriter
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class DataTableCompilationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: DataTableCompilationTaskExecutionConfigData?
) : AbstractTask<DataTableCompilationTask.DataTableCompilationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "task_type")
  @JsonTypeIdResolver(TaskExecutionConfig.PlanTaskTypeIdResolver::class)
  class DataTableCompilationTaskExecutionConfigData(
    @Description("List of file glob patterns to include in the data compilation")
    var file_patterns: List<String> = listOf(),
    @Description("Instructions for identifying rows in the data")
    var row_identification_instructions: String = "",
    @Description("Instructions for identifying columns in the data")
    var column_identification_instructions: String = "",
    @Description("Instructions for extracting cell data")
    var cell_extraction_instructions: String = "",
    @Description("Description of the task")
    task_description: String? = null,
    @Description("List of task IDs this task depends on")
    task_dependencies: List<String>? = null,
    @Description("The current state of the task")
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
        DataTableCompilation - Compile structured data tables from multiple files.
        - Use file glob patterns to select source files.
        - Provide instructions for row and column identification.
        - Define cell extraction logic.
        - Specify output file (JSON/CSV/MD).
    """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())
    try {
      log.info("Starting DataTableCompilationTask. Output: ${executionConfig?.main_file}")
      renderTaskHeader(task)
      transcript?.write("# Data Table Compilation Task\n\n".toByteArray())
      transcript?.write("## Configuration\n\n".toByteArray())
      transcript?.write("- File Patterns: ${executionConfig?.file_patterns?.joinToString(", ")}\n".toByteArray())
      transcript?.write("- Output File: ${executionConfig?.main_file}\n\n".toByteArray())

      task.header("Step 1: Collecting Files", level = 3)
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
        transcript?.write("### Error\n\n$errorMsg\n\n".toByteArray())
        task.error(Exception(errorMsg))
        resultFn(errorMsg)
        return
      }
      task.add("Found ${matchedFiles.size} files matching the patterns".renderMarkdown())
      transcript?.write(
        "## Step 1: File Collection\n<details><summary>Matched Files</summary>\n\n${
          matchedFiles.joinToString(
            "\n"
          ) { "- ${it.name}" }
        }\n</details>\n\n".toByteArray()
      )

      val fileContentString = matchedFiles.joinToString("\n\n") { file ->
        val content = readFileContent(file)
        "### ${file.name}\n```\n${content.take(1000)}${if (content.length > 1000) "..." else ""}\n```"
      }
      transcript?.write("### Source Content Preview\n<details><summary>Raw Content</summary>\n\n$fileContentString\n</details>\n\n".toByteArray())

      val typeConfig = typeConfig ?: throw RuntimeException()
      val chatter =
        (typeConfig.model?.let { it.instance(orchestrationConfig.user) }
          ?: defaultSmart).getChildClient(task)
      task.header("Step 2: Identifying Columns", level = 3)
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
      transcript?.write(
        "## Step 2: Column Identification\n<details><summary>Identified Columns</summary>\n\n${
          columnsList.joinToString(
            "\n"
          ) { "- **${it.name}** (${it.id}): ${it.description}" }
        }\n</details>\n\n".toByteArray()
      )

      task.header("Step 3: Identifying Rows", level = 3)

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

      task.add("Identified ${rowsList.obj.rows.size} rows".renderMarkdown())
      task.add("Identified ${columnsList.size} columns".renderMarkdown())
      transcript?.write(
        "## Step 3: Row Identification\n<details><summary>Identified Rows</summary>\n\n${
          rowsList.obj.rows.joinToString(
            "\n"
          ) { "- **${it.id}** (Sources: ${it.sourceFiles.joinToString(", ")})" }
        }\n</details>\n\n".toByteArray()
      )

      task.header("Step 4: Extracting Cell Data", level = 3)
      val tableData = mutableListOf<Map<String, Any>>()
      val progressTotal = rowsList.obj.rows.size
      var progressCurrent = 0
      val statusBuffer = task.add("Initializing extraction...")
      val tabs = TabbedDisplay(task)

      rowsList.obj.rows.forEach { row ->
        progressCurrent++
        statusBuffer?.setLength(0)
        statusBuffer?.append("Processing row $progressCurrent/$progressTotal: ${row.id}")
        val rowTask = tabs.newTask(row.id)
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
          model = chatter.getChildClient(rowTask),
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
        transcript?.write(
          "### Row: ${row.id}\n<details><summary>Data</summary>\n\n${
            rowData.data.entries.joinToString(
              "\n"
            ) { "- ${it.key}: ${it.value}" }
          }\n</details>\n\n".toByteArray()
        )
        rowTask.complete()
      }

      task.header("Step 5: Finalizing Table", level = 3)

      val outputPath = executionConfig?.main_file ?: "compiled_data.json"


      val markdownTable = StringWriter().use {
        BufferedWriter(it).use { bw ->
          writeMarkdown(columnsList, bw, tableData)
        }
        it.toString()
      }

      val finalizeAction = {
        val data = when {
          outputPath.endsWith(".json", ignoreCase = true) -> {
            val finalData = TableData(tableData, columnsList)
            jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(finalData)
          }


          outputPath.endsWith(".csv", ignoreCase = true) -> {
            val sw = StringWriter()
            BufferedWriter(sw).use { writer ->
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
            sw.toString().toByteArray()
          }

          outputPath.endsWith(".md", ignoreCase = true) -> {
            markdownTable.toByteArray()
          }

          else -> {
            val finalData = TableData(tableData, columnsList)
            jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(finalData)
          }
        }

        val fileUrl = task.saveFile(outputPath, data)
        val summary = """
                ### Compilation Summary
                - **Source Files:** ${matchedFiles.size}
                - **Rows:** ${tableData.size}
                - **Columns:** ${columnsList.size}
                - **Output:** [$outputPath]($fileUrl)
                
                ### Compiled Data
                
                $markdownTable
            """.trimIndent()

        val finalTabs = TabbedDisplay(task)
        finalTabs["Summary"] = summary.renderMarkdown()
        finalTabs["Markdown"] = "```markdown\n$markdownTable\n```".renderMarkdown()

        transcript?.write("## Step 5: Final Results\n\n".toByteArray())
        transcript?.write(summary.toByteArray())

        resultFn("Compiled table with ${tableData.size} rows to $outputPath. Download: $fileUrl")
        task.complete()
      }

      if (orchestrationConfig.autoFix) {
        finalizeAction()
      } else {
        task.add("### Preview of Compiled Data".renderMarkdown())
        task.add(markdownTable.renderMarkdown())
        task.add(task.ui.hrefLink("Save Table & Complete", "btn btn-primary") {
          finalizeAction()
        })
      }

    } catch (e: Exception) {
      task.error(e)
      log.error("Error in DataTableCompilationTask: ${e.message}", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      throw e
    } finally {
      transcript?.close()
    }
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
      getInputFileContent(listOf(path.toString()), root) ?: "ERROR: Could not read file content"
    } catch (e: Exception) {
      log.warn("Failed to read file: $path", e)
      "ERROR: Could not read file content"
    }
  }

  companion object {
    @JvmStatic
    val DataTableCompilation = TaskType(
      name = "DataTableCompilation",
      category = "Writing",
      taskClass = DataTableCompilationTask::class.java,
      executionConfigClass = DataTableCompilationTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Compile structured data tables from multiple files",
      tooltipHtml = "Compile structured data tables from multiple files"
    )
    private val log = LoggerFactory.getLogger(DataTableCompilationTask::class.java)
  }
}