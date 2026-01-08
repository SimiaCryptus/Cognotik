package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger

class TableCompilationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: TableCompilationTaskExecutionConfigData?
) : AbstractTask<TableCompilationTask.TableCompilationTaskExecutionConfigData, TableCompilationTask.TableCompilationTaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class TableCompilationTaskExecutionConfigData(
        @Description("Row headers for the table")
        val rows: List<String>? = null,
        @Description("Column headers for the table")
        val columns: List<String>? = null,
        @Description("Query template for generating cell content. Use {row} and {column} as placeholders.")
        val cell_query: String? = null,
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
        val partition_size: Int = 2,
    ) : TaskTypeConfig(task_type = task_type), ValidatedObject {
        override fun validate(): String? {
            if (partition_size < 1 || partition_size > 10) {
                return "TableCompilationTaskExecutionConfigData: partition_size must be between 1 and 10"
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
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        executionConfig?.validate()?.let { errorMessage ->
            task.error(RuntimeException(errorMessage))
            task.complete()
            resultFn("VALIDATION ERROR: $errorMessage")
            return
        }
        renderTaskHeader(task)

        val rows = executionConfig?.rows ?: emptyList()
        val columns = executionConfig?.columns ?: emptyList()
        val cellQuery = executionConfig?.cell_query ?: ""
        val partitionSize = typeConfig?.partition_size ?: 2

        val api = defaultSmart.getChildClient(task)

        task.add("Generating **${rows.size}x${columns.size}** table using partition size **$partitionSize**.")
        task.add("Query Template: `$cellQuery`")

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
                    val response = cellActor.answer(listOf(batchPrompt))
                    val parsedResults = parseBatchResponse(response, partitionCells.size)

                    // Store results
                    partitionCells.forEachIndexed { index, (rowIdx, colIdx, _) ->
                        cellResults[rowIdx][colIdx] = parsedResults.getOrElse(index) { "Error" }
                    }
                } catch (e: Exception) {
                    log.error("Error processing partition", e)
                    partitionCells.forEach { (rowIdx, colIdx, _) ->
                        cellResults[rowIdx][colIdx] = "Error: ${e.message}"
                    }
                }
            }
        }
        statusBuffer?.setLength(0)
        statusBuffer?.append("Processing complete.")
        task.update()
        val formattedTable = formatAsHtml(rows, columns, cellResults)
        val csvResult = formatAsCsv(rows, columns, cellResults)

        val jsonResult = formatAsJson(rows, columns, cellResults)

        val tabs = TabbedDisplay(task)
        tabs.newTask("Table").apply { add(formattedTable); complete() }
        tabs.newTask("CSV").apply { add("<pre>$csvResult</pre>"); complete() }
        tabs.newTask("JSON").apply { add("<pre>$jsonResult</pre>"); complete() }

        task.complete()
        resultFn(formattedTable)
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

    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
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
        private val log: Logger = LoggerFactory.getLogger(TableCompilationTask::class.java)
        val TableCompilation = TaskType(
            "TableCompilation",
            "Reasoning",
            TableCompilationTask::class.java,
            TableCompilationTaskExecutionConfigData::class.java,
            TableCompilationTaskTypeConfig::class.java,
            "Generate structured tables with AI-computed cell values",
            """
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