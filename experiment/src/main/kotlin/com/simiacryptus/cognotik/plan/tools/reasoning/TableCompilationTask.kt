package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.ISessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger

class TableCompilationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: TableCompilationTaskExecutionConfigData?
) :
  AbstractTask<TableCompilationTask.TableCompilationTaskExecutionConfigData, TableCompilationTask.TableCompilationTaskTypeConfig>(
    orchestrationConfig, planTask
  ) {

  class TableCompilationTaskExecutionConfigData(
    @Description("Row headers for the table")
    var rows: List<String>? = null,
    @Description("Column headers for the table")
    var columns: List<String>? = null,
    @Description("Query template for generating cell content. Use {row} and {column} as placeholders.")
    var cell_query: String? = null,
    @Description("Overall context or description for the table generation")
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, TaskExecutionConfig(
    task_type = TableCompilation.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ) {
    override fun validate(): String? {
      if (rows.isNullOrEmpty()) {
        return "TableCompilationTaskExecutionConfigData: rows list cannot be null or empty"
      }
      if (columns.isNullOrEmpty()) {
        return "TableCompilationTaskExecutionConfigData: columns list cannot be null or empty"
      }
      if (cell_query.isNullOrBlank()) {
        return "TableCompilationTaskExecutionConfigData: cell_query cannot be null or blank"
      }
      return null
    }
  }

  class TableCompilationTaskTypeConfig(
    task_type: String? = TableCompilation.name,
    @Description("Maximum partition size for parallel processing (e.g., 2 means 2x2 partitions)")
    var partition_size: Int = 2,
  ) : TaskTypeConfig(task_type = task_type), ValidatedObject {
    override fun validate(): String? {
      if (partition_size < 1 || partition_size > 10) {
        return "TableCompilationTaskExecutionConfigData: partition_size must be between 1 and 10"
      }
      return null
    }
  }

  override fun promptSegment(): String {
    return """
TableCompilation - Generate structured tables with AI-computed cell values
  ** Specify row headers in the 'rows' array
  ** Specify column headers in the 'columns' array
  ** Provide a cell_query template using {row} and {column} placeholders
  ** Cells are computed in partitions for efficiency (configurable partition_size)
  ** Supports output formats: markdown, html, csv
  ** Example use cases:
     - Comparison matrices (features vs products)
     - Data analysis tables (metrics vs categories)
     - Decision matrices (options vs criteria)
     - Cross-reference tables
        """
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())










    task.pool.submit {
      try {
        executionConfig?.validate()?.let { errorMessage ->
          val e = RuntimeException(errorMessage)
          task.error(e)
          log.error("Validation error in TableCompilationTask: $errorMessage")
          transcript?.write("## Validation Error\n<details><summary>Details</summary>\n$errorMessage\n</details>".toByteArray())
          task.complete()
          resultFn("VALIDATION ERROR: $errorMessage")
          return@submit
        }

        val rows = executionConfig?.rows ?: emptyList()
        val columns = executionConfig?.columns ?: emptyList()
        val cellQuery = executionConfig?.cell_query ?: ""
        val partitionSize = typeConfig?.partition_size ?: 2
        val api = defaultSmart.getChildClient(task)

        log.info("Starting TableCompilationTask: ${rows?.size}x${columns?.size}")
        transcript?.write("## Table Compilation Intent\nGenerating table with ${executionConfig?.rows?.size} rows and ${executionConfig?.columns?.size} columns.\n".toByteArray())
        renderTaskHeader(task)



        task.add(
          """
                    Generating **${rows.size}x${columns.size}** table using partition size **$partitionSize**.
                    Query Template: `$cellQuery`
                """.trimIndent().renderMarkdown()
        )

        // Initialize the results table
        val cellResults = Array(rows.size) { Array(columns.size) { "" } }

        // Create partitions
        val rowPartitions = rows.indices.chunked(partitionSize)
        val colPartitions = columns.indices.chunked(partitionSize)

        val totalPartitions = rowPartitions.size * colPartitions.size
        var completedPartitions = 0
        val statusBuffer = task.add("Starting processing...")

        // Process each partition
        for (rowPartition in rowPartitions) {
          for (colPartition in colPartitions) {
            completedPartitions++
            statusBuffer?.setLength(0)
            statusBuffer?.append("Processing partition $completedPartitions/$totalPartitions...")
            task.update()

            val partitionCells = mutableListOf<Triple<Int, Int, String>>() // rowIdx, colIdx, query
            for (rowIdx in rowPartition) {
              for (colIdx in colPartition) {
                val query = cellQuery
                  .replace("{row}", rows[rowIdx])
                  .replace("{column}", columns[colIdx])
                partitionCells.add(Triple(rowIdx, colIdx, query))
              }
            }

            // Build batch prompt for this partition
            val batchPrompt = buildBatchPrompt(
              partitionCells,
              rows,
              columns,
              executionConfig?.task_description
            )

            val cellActor = ChatAgent(
              prompt = """
        You are a precise data analyst. Generate concise cell values for a table.
        Each cell should contain a brief, relevant response based on the row and column context.
        Keep responses concise (typically 1-3 sentences or a few words/numbers as appropriate).
                            """.trimIndent(),
              model = api,
            )

            try {
              transcript?.write("<details><summary>Partition $completedPartitions Prompt</summary>\n\n```\n$batchPrompt\n```\n</details>\n".toByteArray())
              val response = cellActor.answer(listOf(batchPrompt))
              transcript?.write("<details><summary>Partition $completedPartitions Response</summary>\n\n```\n$response\n```\n</details>\n".toByteArray())
              val parsedResults = parseBatchResponse(response, partitionCells.size)

              // Store results
              partitionCells.forEachIndexed { index, (rowIdx, colIdx, _) ->
                cellResults[rowIdx][colIdx] = parsedResults.getOrElse(index) { "Error" }
              }
            } catch (e: Exception) {
              log.error("Error processing partition $completedPartitions", e)
              transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
              partitionCells.forEach { (rowIdx, colIdx, _) ->
                cellResults[rowIdx][colIdx] = "Error: ${e.message}"
              }
            }
          }
        }







        statusBuffer?.append("Processing complete.")
        task.update()

        val formattedTable = formatAsHtml(rows, columns, cellResults)
        val csvResult = formatAsCsv(rows, columns, cellResults)
        val jsonResult = formatAsJson(rows, columns, cellResults)

        val csvUrl = task.saveFile("output/table_${System.currentTimeMillis()}.csv", csvResult.toByteArray())
        val jsonUrl = task.saveFile("output/table_${System.currentTimeMillis()}.json", jsonResult.toByteArray())
        transcript?.write(
          """
                    ## Compilation Results
                    <details>
                    <summary>HTML Table Preview</summary>
                    $formattedTable
                    </details>
                    * CSV Artifact: `$csvUrl`
                    * JSON Artifact: `$jsonUrl`
                """.trimIndent().toByteArray()
        )


        val tabs = TabbedDisplay(task)
        tabs.newTask("Table").apply { add(formattedTable); complete() }
        tabs.newTask("CSV")
          .apply { add("<pre>$csvResult</pre>"); add("<a href='$csvUrl'>Download CSV</a>"); complete() }
        tabs.newTask("JSON")
          .apply { add("<pre>$jsonResult</pre>"); add("<a href='$jsonUrl'>Download JSON</a>"); complete() }

        val summary =
          "## Table Generation Complete\nGenerated ${rows.size}x${columns.size} table. Artifacts: [CSV]($csvUrl), [JSON]($jsonUrl)"
        if (orchestrationConfig.autoFix) {
          task.complete()
          resultFn(summary)
        } else {
          task.add(summary.renderMarkdown() + acceptButtonFooter(task) {
            task.complete()
            resultFn(summary)
          })
        }
      } catch (e: Exception) {
        task.error(e)
        log.error("TableCompilationTask failed", e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      } finally {
        transcript?.close()
      }
    }
  }

  private fun buildBatchPrompt(
    cells: List<Triple<Int, Int, String>>,
    rows: List<String>,
    columns: List<String>,
    context: String?
  ): String {
    return buildString {
      appendLine("Generate cell values for the following table cells.")
      if (!context.isNullOrBlank()) {
        appendLine("Context: $context")
      }
      appendLine()
      appendLine("For each cell below, provide a concise value. Format your response as:")
      appendLine("CELL_1: [value]")
      appendLine("CELL_2: [value]")
      appendLine("etc.")
      appendLine()
      cells.forEachIndexed { index, (rowIdx, colIdx, query) ->
        appendLine("CELL_${index + 1}:")
        appendLine("  Row: ${rows[rowIdx]}")
        appendLine("  Column: ${columns[colIdx]}")
        appendLine("  Query: $query")
        appendLine()
      }
    }
  }

  private fun parseBatchResponse(response: String, expectedCount: Int): List<String> {
    val results = mutableListOf<String>()
    val cellPattern = """CELL_(\d+):\s*(.+?)(?=CELL_\d+:|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val matches = cellPattern.findAll(response)

    val matchMap = matches.associate { match ->
      match.groupValues[1].toInt() to match.groupValues[2].trim()
    }

    for (i in 1..expectedCount) {
      results.add(matchMap[i] ?: "N/A")
    }

    // If parsing failed, try line-by-line fallback
    if (results.all { it == "N/A" }) {
      val lines = response.lines().filter { it.isNotBlank() }
      return lines.take(expectedCount).map { it.trim() }
    }

    return results
  }


  private fun formatAsHtml(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
    return buildString {
      appendLine("<table border=\"1\" cellpadding=\"5\" cellspacing=\"0\">")
      appendLine("  <thead>")
      appendLine("    <tr>")
      appendLine("      <th></th>")
      columns.forEach { col -> appendLine("      <th>$col</th>") }
      appendLine("    </tr>")
      appendLine("  </thead>")
      appendLine("  <tbody>")
      rows.forEachIndexed { rowIdx, rowHeader ->
        appendLine("    <tr>")
        appendLine("      <th>$rowHeader</th>")
        columns.indices.forEach { colIdx ->
          appendLine("      <td>${cells[rowIdx][colIdx]}</td>")
        }
        appendLine("    </tr>")
      }
      appendLine("  </tbody>")
      appendLine("</table>")
    }
  }

  private fun formatAsCsv(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
    return buildString {
      // Header row
      append(",")
      appendLine(columns.joinToString(",") { "\"$it\"" })

      // Data rows
      rows.forEachIndexed { rowIdx, rowHeader ->
        append("\"$rowHeader\",")
        appendLine(columns.indices.joinToString(",") { colIdx ->
          "\"${cells[rowIdx][colIdx].replace("\"", "\"\"")}\""
        })
      }
    }
  }


  private fun formatAsJson(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
    val tableData = rows.mapIndexed { rowIdx, rowHeader ->
      val rowMap = mutableMapOf<String, String>()
      rowMap["Row"] = rowHeader
      columns.forEachIndexed { colIdx, colHeader ->
        rowMap[colHeader] = cells[rowIdx][colIdx]
      }
      rowMap
    }
    return tableData.toJson()
  }

  override fun acceptButtonFooter(ui: ISessionTask, fn: () -> Unit): String {
    val acceptLink = ui.hrefLink("Accept Table") {
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
      private val log: Logger = getLogger(TableCompilationTask::class.java)

    @JvmStatic
    val TableCompilation = TaskType(
      name = "TableCompilation",
      category = "Reasoning",
      taskClass = TableCompilationTask::class.java,
      executionConfigClass = TableCompilationTaskExecutionConfigData::class.java,
      taskSettingsClass = TableCompilationTaskTypeConfig::class.java,
      description = "Generate structured tables with AI-computed cell values",
      tooltipHtml = """
                        Generates tables by computing each cell value using AI.
                        <ul>
                          <li>Define rows and columns as headers</li>
                          <li>Provide a query template with {row} and {column} placeholders</li>
                          <li>Cells are computed in configurable partitions for efficiency</li>
                          <li>Supports markdown, HTML, and CSV output formats</li>
                          <li>Useful for comparison matrices, analysis tables, decision matrices</li>
                        </ul>
                      """,
    )
  }
}