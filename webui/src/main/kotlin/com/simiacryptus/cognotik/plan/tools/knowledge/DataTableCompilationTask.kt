package com.simiacryptus.cognotik.plan.tools.knowledge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.StringWriter
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

class DataTableCompilationTask(
  planSettings: PlanSettings,
  planTask: DataTableCompilationTaskConfigData?
) : AbstractTask<DataTableCompilationTask.DataTableCompilationTaskConfigData>(planSettings, planTask) {
  
  class DataTableCompilationTaskConfigData(
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
  ) : TaskConfigBase(
    task_type = TaskType.DataTableCompilationTask.name,
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
        DataTableCompilationTask - Compile structured data tables from multiple files
          ** Specify file glob patterns to include in the compilation
          ** Define instructions for identifying rows in the data
          ** Define instructions for identifying columns in the data
          ** Define instructions for extracting cell data
          ** Specify output file path for the compiled table
    """.trimIndent()
  
  override fun run(
    agent: PlanCoordinator,
    messages: List<String>,
    task: SessionTask,
    api: ChatClient,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings
  ) {
    // Step 1: Collect all files matching the glob patterns
    task.add(MarkdownUtil.renderMarkdown("## Step 1: Collecting files from patterns", ui = agent.ui))
    val result = mutableListOf<Path>()
    val basePath = Paths.get(planSettings.workingDir ?: ".")
    taskConfig?.file_patterns?.forEach { pattern ->
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
      val errorMsg = "No files matched the provided patterns: ${taskConfig?.file_patterns?.joinToString(", ")}"
      task.error(ui = agent.ui, Exception(errorMsg))
      resultFn(errorMsg)
      return
    }
    task.add(MarkdownUtil.renderMarkdown("Found ${matchedFiles.size} files matching the patterns", ui = agent.ui))
    
    val fileContentString = matchedFiles.joinToString("\n\n") { file ->
      val content = readFileContent(file)
      "### ${file.name}\n```\n${content.take(1000)}${if (content.length > 1000) "..." else ""}\n```"
    }
    
    val columnsResponse = ParsedActor(
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
                ${taskConfig?.column_identification_instructions}
                
                For each column you identify:
                1. Assign a unique column ID - should be a short, descriptive string
                2. Provide a detailed description of what the column represents
            """.trimIndent(),
      model = taskSettings.model ?: planSettings.defaultModel,
      parsingModel = planSettings.parsingModel,
      temperature = planSettings.temperature,
      describer = agent.describer,
    ).answer(
      listOf(
        fileContentString
      ),
      api = api
    )
    val columns = columnsResponse.obj
    val columnsList = columns.columns.map {
      Column(
        id = it.id,
        name = it.name,
        description = it.description,
      )
    }
    val rowsList = ParsedActor(
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
                ${taskConfig?.row_identification_instructions}
                
                For each row you identify:
                1. Assign a unique row ID - should be a short, descriptive string
                2. List the source files that contain data for this row
            """.trimIndent(),
      model = taskSettings.model ?: planSettings.defaultModel,
      parsingModel = planSettings.parsingModel,
      temperature = planSettings.temperature,
      describer = agent.describer,
    ).answer(
      listOf(
        fileContentString,
        "Columns:\n" + columnsList.joinToString("\n") { "- ${it.id}: ${it.name} (${it.description})" }
      ),
      api = api
    )
    
    task.add(MarkdownUtil.renderMarkdown("Identified ${rowsList.obj.rows.size} rows", ui = agent.ui))
    task.add(MarkdownUtil.renderMarkdown("Identified ${columnsList.size} columns", ui = agent.ui))
    
    // Step 4: Extract rows
    task.add(MarkdownUtil.renderMarkdown("## Step 4: Extracting cell data for each row", ui = agent.ui))
    val tableData = mutableListOf<Map<String, Any>>()
    val progressTotal = rowsList.obj.rows.size
    var progressCurrent = 0
    
    rowsList.obj.rows.forEach { row ->
      progressCurrent++
      task.add(MarkdownUtil.renderMarkdown("Processing row ${progressCurrent}/${progressTotal}: ${row.id}", ui = agent.ui))
      val rowDataResponse = ParsedActor(
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
            "Special Instructions:\n${taskConfig?.cell_extraction_instructions}\n\n" +
            "IMPORTANT: Respond with ONLY the single JSON object for the row `${row.id}`. Do NOT return a JSON array.",
        model = taskSettings.model ?: planSettings.defaultModel,
        parsingModel = planSettings.parsingModel,
        temperature = planSettings.temperature,
        describer = agent.describer,
      ).answer(
        listOf(
          "Source Files:\n" + row.sourceFiles.mapNotNull { fileName ->
            matchedFiles.find { it.name == fileName || it.toString().endsWith(fileName) }
          }.joinToString("\n\n") { file ->
            "### ${file.name}\n```\n${readFileContent(file).indent("  ")}\n```"
          }
        ),
        api = api
      )
      
      val rowData = rowDataResponse.obj
      val rowMap = mutableMapOf<String, Any>()
      rowMap["rowId"] = row.id
      rowMap.putAll(rowData.data)
      
      tableData.add(rowMap)
    }
    
    // Step 5: Compile and save the table
    task.add(MarkdownUtil.renderMarkdown("## Step 5: Compiling and saving data table", ui = agent.ui))
    
    val outputPath = taskConfig?.output_file ?: "compiled_data.json"
    val outputFile = if (planSettings.workingDir != null) {
      File(planSettings.workingDir, outputPath)
    } else {
      File(outputPath)
    }
    
    // Create parent directories if they don't exist
    outputFile.parentFile?.mkdirs()
    
    // Save the data based on file extension
    when {
      outputPath.endsWith(".json", ignoreCase = true) -> {
        // Save as JSON
        val finalData = TableData(tableData, columnsList)
        val mapper = jacksonObjectMapper()
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, finalData)
      }
      
      outputPath.endsWith(".csv", ignoreCase = true) -> {
        // Save as CSV
        BufferedWriter(FileWriter(outputFile)).use { writer ->
          // Write header
          val header = columnsList.joinToString(",") { "\"${it.name.replace("\"", "\"\"")}\"" }
          writer.write(header)
          writer.newLine()
          
          // Write rows
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
        // Save as Markdown table
        BufferedWriter(FileWriter(outputFile)).use { writer ->
          writeMarkdown(columnsList, writer, tableData)
        }
      }
      
      outputPath.isBlank() -> {
        // Don't save the data
      }
      
      else -> {
        // Default to JSON if extension not recognized
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
    
    //task.add(MarkdownUtil.renderMarkdown(resultMessage, ui = agent.ui))
    resultFn(resultMessage)
  }
  
  private fun writeMarkdown(
    columnsList: List<Column>,
    writer: BufferedWriter,
    tableData: MutableList<Map<String, Any>>
  ) {
    // Write header
    val header = columnsList.joinToString(" | ") { it.name }
    writer.write("| $header |")
    writer.newLine()
    
    // Write separator
    val separator = columnsList.joinToString(" | ") { "---" }
    writer.write("| $separator |")
    writer.newLine()
    
    // Write rows
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