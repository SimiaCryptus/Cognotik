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

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/DataTableAccumulationStrategy.kt

```
package com.simiacryptus.cognotik.crawl.processing

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class DataTableAccumulationStrategy : DefaultSummarizerStrategy() {

    data class DataTableConfig(
        @Description("Column names for the data table (comma-separated)")
        val column_names: String = "",
        @Description("Description of what data to extract for each column")
        val column_descriptions: Map<String, String> = emptyMap(),
        @Description("Data types for each column (string, number, boolean, date)")
        val column_types: Map<String, String> = emptyMap(),
        @Description("Instructions for extracting data from pages")
        val extraction_instructions: String = "",
        @Description("Whether to automatically detect and extract HTML tables")
        val auto_detect_tables: Boolean = true,
        @Description("Minimum number of rows to consider a table valid")
        val min_rows: Int = 1,
        @Description("Maximum number of rows to extract per page (null for unlimited)")
        val max_rows_per_page: Int? = null,
        @Description("Whether to deduplicate rows based on key columns")
        val deduplicate: Boolean = true,
        @Description("Column names to use as unique keys for deduplication (comma-separated)")
        val key_columns: String? = null,
        @Description("Whether to validate data types for each column")
        val validate_types: Boolean = true,
        @Description("Whether to normalize/clean data values")
        val normalize_data: Boolean = true,
        @Description("Export format for final table (csv, json, markdown)")
        val export_format: String = "csv",
        @Description("Whether to include source URLs in the output")
        val include_source_urls: Boolean = true
    )

    override val description: String
        get() = "Extracts and accumulates tabular data from web pages, building a comprehensive dataset with configurable columns and validation."

    data class ExtractedTableData(
        @Description("List of rows extracted from the page, each row is a map of column_name -> value")
        val rows: List<Map<String, Any?>> = emptyList(),
        @Description("Confidence score for the extraction (0.0-1.0)")
        val confidence: Double = 1.0,
        @Description("Any validation errors or warnings for the extracted data")
        val validation_notes: List<String> = emptyList(),
        @Description("Metadata about the extracted table")
        val metadata: Map<String, Any> = emptyMap()
    )

    private val accumulatedRows = ConcurrentHashMap<String, MutableList<Map<String, Any?>>>()
    private val seenRowKeys = ConcurrentHashMap.newKeySet<String>()
    private val columnStats = ConcurrentHashMap<String, ColumnStatistics>()

    data class ColumnStatistics(
        var totalValues: Int = 0,
        var nullValues: Int = 0,
        var uniqueValues: MutableSet<String> = mutableSetOf(),
        var typeViolations: Int = 0
    )

    companion object {
        private val log = LoggerFactory.getLogger(DataTableAccumulationStrategy::class.java)
    }

    override fun processPage(
        url: String,
        content: String,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.PageProcessingResult {
        log.debug("Processing page for data table accumulation: $url")

        val config = try {
            val chatInterface = context.orchestrationConfig.defaultFast.getChildClient(context.task)
            context.executionConfig.content_queries?.let { queries ->
                queries.parserCast<DataTableConfig>(chatInterface)
            } ?: run {
                log.warn("No data table config provided, using default")
                DataTableConfig()
            }
        } catch (e: Exception) {
            log.error("Failed to parse data table config", e)
            return PageProcessingStrategy.PageProcessingResult(
                url = url,
                pageType = CrawlerAgentTask.PageType.Error,
                content = "Configuration error: ${e.message}",
                error = e
            )
        }

        // Extract table data
        val extractionResult = try {
            extractTableData(url, content, config, context)
        } catch (e: Exception) {
            log.error("Failed to extract table data from: $url", e)
            return PageProcessingStrategy.PageProcessingResult(
                url = url,
                pageType = CrawlerAgentTask.PageType.Error,
                content = "Extraction error: ${e.message}",
                error = e
            )
        }

        // Validate and normalize rows
        val processedRows = extractionResult.rows.mapNotNull { row ->
            processRow(row, config, url)
        }

        // Store accumulated data
        if (processedRows.isNotEmpty()) {
            storeTableData(url, processedRows, config)
            updateColumnStatistics(processedRows, config)
        }

        // Generate summary
        val summary = buildString {
            appendLine("## Data Table Extraction Results")
            appendLine()
            appendLine("**URL:** [$url]($url)")
            appendLine("**Confidence:** ${(extractionResult.confidence * 100).toInt()}%")
            appendLine("**Rows Extracted:** ${processedRows.size}")
            appendLine("**Total Accumulated Rows:** ${accumulatedRows.values.sumOf { it.size }}")
            appendLine()

            if (extractionResult.validation_notes.isNotEmpty()) {
                appendLine("### Validation Notes")
                extractionResult.validation_notes.forEach { note ->
                    appendLine("- ⚠️ $note")
                }
                appendLine()
            }

            if (processedRows.isNotEmpty()) {
                appendLine("### Sample Data (First 3 Rows)")
                appendLine()
                appendLine(formatRowsAsMarkdownTable(processedRows.take(3), config))
                appendLine()
            } else {
                appendLine("*No valid data extracted from this page*")
                appendLine()
            }
        }

        // Also get standard link extraction
        val standardResult = super.processPage(url, content, context)

        return PageProcessingStrategy.PageProcessingResult(
            url = url,
            pageType = if (processedRows.isNotEmpty()) CrawlerAgentTask.PageType.OK else CrawlerAgentTask.PageType.Irrelevant,
            content = summary,
            extractedLinks = standardResult.extractedLinks,
            metadata = mapOf(
                "rows_extracted" to processedRows.size,
                "total_rows" to accumulatedRows.values.sumOf { it.size },
                "confidence" to extractionResult.confidence,
                "validation_notes" to extractionResult.validation_notes
            )
        )
    }

    private fun extractTableData(
        url: String,
        content: String,
        config: DataTableConfig,
        context: PageProcessingStrategy.ProcessingContext
    ): ExtractedTableData {
        log.debug("Extracting table data from: $url")

        val columns = config.column_names.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val prompt = buildString {
            appendLine("Extract tabular data from the following web page content.")
            appendLine()
            appendLine("REQUIRED COLUMNS:")
            columns.forEach { column ->
                appendLine("- $column")
                config.column_descriptions[column]?.let { desc ->
                    appendLine("  Description: $desc")
                }
                config.column_types[column]?.let { type ->
                    appendLine("  Type: $type")
                }
            }
            appendLine()
            appendLine("EXTRACTION INSTRUCTIONS:")
            appendLine(config.extraction_instructions.ifBlank { "Extract all relevant data matching the column definitions" })
            appendLine()

            if (config.auto_detect_tables) {
                appendLine("Look for HTML tables, lists, or structured data that matches these columns.")
                appendLine()
            }

            if (config.max_rows_per_page != null) {
                appendLine("Extract up to ${config.max_rows_per_page} rows from this page.")
                appendLine()
            }

            appendLine("Return:")
            appendLine("1. A list of rows, where each row is a map of column_name -> value")
            appendLine("2. A confidence score (0.0-1.0) for the extraction quality")
            appendLine("3. Any validation notes or warnings")
            appendLine()
            appendLine("If a column value is not found or not applicable, use null.")
            appendLine("Ensure all rows have the same columns.")
        }

        val model = (context.typeConfig.model?.let { context.orchestrationConfig.instance(it) }
            ?: context.orchestrationConfig.defaultFast).getChildClient(context.task)

        val result = ParsedAgent(
            prompt = prompt,
            resultClass = ExtractedTableData::class.java,
            model = model,
            parsingChatter = model
        ).answer(listOf(content.take(50000))).obj

        // Apply row limit if configured
        val limitedRows = if (config.max_rows_per_page != null && result.rows.size > config.max_rows_per_page) {
            result.rows.take(config.max_rows_per_page)
        } else {
            result.rows
        }

        return result.copy(rows = limitedRows)
    }

    private fun processRow(
        row: Map<String, Any?>,
        config: DataTableConfig,
        sourceUrl: String
    ): Map<String, Any?>? {
        // Validate minimum data presence
        val nonNullValues = row.values.count { it != null && it.toString().isNotBlank() }
        if (nonNullValues == 0) {
            log.debug("Skipping empty row from: $sourceUrl")
            return null
        }

        // Check for duplicates if enabled
        if (config.deduplicate && config.key_columns != null) {
            val keyColumns = config.key_columns.split(",").map { it.trim() }
            val rowKey = keyColumns.mapNotNull { col ->
                row[col]?.toString()
            }.joinToString("|")

            if (rowKey.isNotBlank()) {
                if (seenRowKeys.contains(rowKey)) {
                    log.debug("Skipping duplicate row with key: $rowKey")
                    return null
                }
                seenRowKeys.add(rowKey)
            }
        }

        // Normalize and validate data
        val processedRow = row.toMutableMap()

        if (config.normalize_data) {
            processedRow.replaceAll { key, value ->
                normalizeValue(value, config.column_types[key])
            }
        }

        if (config.validate_types) {
            processedRow.forEach { (column, value) ->
                config.column_types[column]?.let { expectedType ->
                    if (!validateType(value, expectedType)) {
                        log.warn("Type validation failed for column '$column': expected $expectedType, got ${value?.javaClass?.simpleName}")
                    }
                }
            }
        }

        // Add source URL if configured
        if (config.include_source_urls) {
            processedRow["_source_url"] = sourceUrl
        }

        return processedRow
    }

    private fun normalizeValue(value: Any?, expectedType: String?): Any? {
        if (value == null) return null

        val stringValue = value.toString().trim()
        if (stringValue.isEmpty()) return null

        return when (expectedType?.lowercase()) {
            "number" -> stringValue.replace(Regex("[^0-9.-]"), "").toDoubleOrNull()
            "boolean" -> when (stringValue.lowercase()) {
                "true", "yes", "1", "y" -> true
                "false", "no", "0", "n" -> false
                else -> null
            }

            "date" -> stringValue // Keep as string, could add date parsing
            else -> stringValue
        }
    }

    private fun validateType(value: Any?, expectedType: String): Boolean {
        if (value == null) return true // null is valid for any type

        return when (expectedType.lowercase()) {
            "string" -> value is String
            "number" -> value is Number || value.toString().toDoubleOrNull() != null
            "boolean" -> value is Boolean
            "date" -> true // Accept any string for dates
            else -> true
        }
    }

    private fun storeTableData(
        url: String,
        rows: List<Map<String, Any?>>,
        config: DataTableConfig
    ) {
        accumulatedRows.computeIfAbsent(url) { mutableListOf() }.addAll(rows)
        log.info("Stored ${rows.size} rows from: $url (total: ${accumulatedRows.values.sumOf { it.size }})")
    }

    private fun updateColumnStatistics(
        rows: List<Map<String, Any?>>,
        config: DataTableConfig
    ) {
        val columns = config.column_names.split(",").map { it.trim() }

        rows.forEach { row ->
            columns.forEach { column ->
                val stats = columnStats.computeIfAbsent(column) { ColumnStatistics() }
                stats.totalValues++

                val value = row[column]
                if (value == null || value.toString().isBlank()) {
                    stats.nullValues++
                } else {
                    stats.uniqueValues.add(value.toString())

                    // Check type violations
                    config.column_types[column]?.let { expectedType ->
                        if (!validateType(value, expectedType)) {
                            stats.typeViolations++
                        }
                    }
                }
            }
        }
    }

    private fun formatRowsAsMarkdownTable(
        rows: List<Map<String, Any?>>,
        config: DataTableConfig
    ): String {
        if (rows.isEmpty()) return "*No data*"

        val columns = config.column_names.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (columns.isEmpty()) return "*No columns defined*"

        return buildString {
            // Header
            appendLine("| ${columns.joinToString(" | ")} |")
            appendLine("| ${columns.joinToString(" | ") { "---" }} |")

            // Rows
            rows.forEach { row ->
                val values = columns.map { column ->
                    row[column]?.toString()?.take(50) ?: ""
                }
                appendLine("| ${values.joinToString(" | ")} |")
            }
        }
    }

    override fun shouldContinueCrawling(
        currentResults: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.ContinuationDecision {
        val totalRows = accumulatedRows.values.sumOf { it.size }
        val successfulExtractions = currentResults.count {
            (it.metadata["rows_extracted"] as? Int ?: 0) > 0
        }

        log.debug("Crawling status: $successfulExtractions successful extractions, $totalRows total rows")

        return PageProcessingStrategy.ContinuationDecision(
            shouldContinue = context.processedCount.get() < context.maxPages,
            reason = "Accumulated $totalRows rows from $successfulExtractions pages so far"
        )
    }

    override fun generateFinalOutput(
        results: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): String {
        log.info("Generating final data table output")

        val config = try {
            context.executionConfig.content_queries?.let { queries ->
                queries.parserCast<DataTableConfig>(context.orchestrationConfig.defaultFast.getChildClient(context.task))
            } ?: DataTableConfig()
        } catch (e: Exception) {
            log.error("Failed to parse config for final output", e)
            DataTableConfig()
        }

        // Aggregate all rows
        val allRows = accumulatedRows.values.flatten()

        // Export data in requested format
        exportDataTable(allRows, config, context)

        // Generate summary report
        return buildString {
            appendLine("# Data Table Accumulation Results")
            appendLine()
            appendLine(
                "**Extraction Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }"
            )
            appendLine()

            appendLine("## Summary Statistics")
            appendLine()
            appendLine("- **Total Pages Processed:** ${results.size}")
            appendLine("- **Successful Extractions:** ${accumulatedRows.size}")
            appendLine("- **Total Rows Accumulated:** ${allRows.size}")
            appendLine("- **Unique Sources:** ${accumulatedRows.keys.size}")
            if (config.deduplicate) {
                appendLine("- **Deduplication:** Enabled (${seenRowKeys.size} unique keys)")
            }
            appendLine()

            appendLine("## Column Statistics")
            appendLine()
            appendLine("| Column | Total Values | Null Values | Unique Values | Type Violations |")
            appendLine("|--------|--------------|-------------|---------------|-----------------|")

            val columns = config.column_names.split(",").map { it.trim() }
            columns.forEach { column ->
                val stats = columnStats[column] ?: ColumnStatistics()
                val nullPct = if (stats.totalValues > 0) {
                    (stats.nullValues * 100.0 / stats.totalValues).toInt()
                } else 0
                appendLine("| $column | ${stats.totalValues} | ${stats.nullValues} ($nullPct%) | ${stats.uniqueValues.size} | ${stats.typeViolations} |")
            }
            appendLine()

            appendLine("## Data Quality")
            appendLine()
            val avgConfidence = results.mapNotNull {
                it.metadata["confidence"] as? Double
            }.average().takeIf { !it.isNaN() } ?: 0.0
            appendLine("- **Average Confidence:** ${(avgConfidence * 100).toInt()}%")

            val allValidationNotes = results.flatMap {
                (it.metadata["validation_notes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            }.distinct()

            if (allValidationNotes.isEmpty()) {
                appendLine("- **Validation Issues:** None detected ✅")
            } else {
                appendLine("- **Validation Issues:** ${allValidationNotes.size} warnings")
                allValidationNotes.take(5).forEach { note ->
                    appendLine("  - ⚠️ $note")
                }
                if (allValidationNotes.size > 5) {
                    appendLine("  - *... and ${allValidationNotes.size - 5} more*")
                }
            }
            appendLine()

            appendLine("## Exported Files")
            appendLine()
            appendLine("The complete dataset has been exported to:")
            when (config.export_format.lowercase()) {
                "csv" -> appendLine("- `${context.webSearchDir.name}/data_table.csv`")
                "json" -> appendLine("- `${context.webSearchDir.name}/data_table.json`")
                "markdown" -> appendLine("- `${context.webSearchDir.name}/data_table.md`")
                else -> appendLine("- `${context.webSearchDir.name}/data_table.${config.export_format}`")
            }
            appendLine()

            appendLine("## Sample Data (First 10 Rows)")
            appendLine()
            appendLine(formatRowsAsMarkdownTable(allRows.take(10), config))
            appendLine()

            if (allRows.size > 10) {
                appendLine("*... and ${allRows.size - 10} more rows*")
                appendLine()
            }

            appendLine("## Data Sources")
            appendLine()
            accumulatedRows.forEach { (url, rows) ->
                appendLine("- [$url]($url) - ${rows.size} rows")
            }
            appendLine()

            appendLine("## Next Steps")
            appendLine()
            appendLine("1. Review the exported data file for completeness")
            appendLine("2. Validate data quality and handle any null values")
            appendLine("3. Import data into your analysis tools or database")
            appendLine("4. Consider additional crawling if coverage is incomplete")
        }
    }

    private fun exportDataTable(
        rows: List<Map<String, Any?>>,
        config: DataTableConfig,
        context: PageProcessingStrategy.ProcessingContext
    ) {
        if (rows.isEmpty()) {
            log.warn("No rows to export")
            return
        }

        val columns = config.column_names.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (columns.isEmpty()) {
            log.warn("No columns defined for export")
            return
        }

        try {
            when (config.export_format.lowercase()) {
                "csv" -> exportAsCSV(rows, columns, context)
                "json" -> exportAsJSON(rows, context)
                "markdown" -> exportAsMarkdown(rows, columns, config, context)
                else -> {
                    log.warn("Unknown export format: ${config.export_format}, defaulting to CSV")
                    exportAsCSV(rows, columns, context)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to export data table", e)
        }
    }

    private fun exportAsCSV(
        rows: List<Map<String, Any?>>,
        columns: List<String>,
        context: PageProcessingStrategy.ProcessingContext
    ) {
        val csvFile = File(context.webSearchDir, "data_table.csv")

        csvFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            // Write header
            writer.write(columns.joinToString(",") { escapeCSV(it) })
            writer.newLine()

            // Write rows
            rows.forEach { row ->
                val values = columns.map { column ->
                    escapeCSV(row[column]?.toString() ?: "")
                }
                writer.write(values.joinToString(","))
                writer.newLine()
            }
        }

        log.info("Exported ${rows.size} rows to CSV: ${csvFile.absolutePath}")
    }

    private fun exportAsJSON(
        rows: List<Map<String, Any?>>,
        context: PageProcessingStrategy.ProcessingContext
    ) {
        val jsonFile = File(context.webSearchDir, "data_table.json")
        jsonFile.writeText(rows.toJson(), StandardCharsets.UTF_8)
        log.info("Exported ${rows.size} rows to JSON: ${jsonFile.absolutePath}")
    }

    private fun exportAsMarkdown(
        rows: List<Map<String, Any?>>,
        columns: List<String>,
        config: DataTableConfig,
        context: PageProcessingStrategy.ProcessingContext
    ) {
        val mdFile = File(context.webSearchDir, "data_table.md")

        mdFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.write("# Data Table Export\n\n")
            writer.write("**Generated:** ${LocalDateTime.now()}\n\n")
            writer.write("**Total Rows:** ${rows.size}\n\n")
            writer.write("## Data\n\n")
            writer.write(formatRowsAsMarkdownTable(rows, config))
        }

        log.info("Exported ${rows.size} rows to Markdown: ${mdFile.absolutePath}")
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    override fun validateConfig(config: Any?): String? {
        if (config == null) return "Data table config is required"

        return try {
            val tableConfig = when (config) {
                is DataTableConfig -> config
                is String -> JsonUtil.fromJson(config, DataTableConfig::class.java)
                else -> return "Invalid config type: ${config.javaClass.name}"
            }

            if (tableConfig.column_names.isBlank()) {
                return "column_names is required and cannot be blank"
            }

            val columns = tableConfig.column_names.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (columns.isEmpty()) {
                return "At least one column must be defined"
            }

            if (tableConfig.min_rows < 0) {
                return "min_rows must be non-negative"
            }

            if (tableConfig.max_rows_per_page != null && tableConfig.max_rows_per_page <= 0) {
                return "max_rows_per_page must be greater than 0"
            }

            val validExportFormats = setOf("csv", "json", "markdown")
            if (tableConfig.export_format.lowercase() !in validExportFormats) {
                return "export_format must be one of: ${validExportFormats.joinToString(", ")}"
            }

            null
        } catch (e: Exception) {
            "Config validation error: ${e.message}"
        }
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/DefaultSummarizerStrategy.kt

```
package com.simiacryptus.cognotik.crawl.processing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import kotlin.math.min

open class DefaultSummarizerStrategy : PageProcessingStrategy {
    companion object {
        private val log = LoggerFactory.getLogger(DefaultSummarizerStrategy::class.java)
    }

    override val description: String // Describe both the strategy and its configuration
        get() = ""

    open override fun processPage(
        url: String,
        content: String,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.PageProcessingResult {
        val analysisGoal = analysisGoal(context)

        val analysis = try {
            transformContent(content, analysisGoal, context)
        } catch (e: Exception) {
            log.error("Error transforming content for URL: $url", e)
            return PageProcessingStrategy.PageProcessingResult(
                url = url,
                pageType = CrawlerAgentTask.PageType.Error,
                content = "Error analyzing content: ${e.message}",
                extractedLinks = null,
                metadata = mapOf("error" to (e.message ?: "Unknown error"))
            )
        }

        return PageProcessingStrategy.PageProcessingResult(
            url = url,
            pageType = analysis.obj.page_type,
            content = analysis.text,
            extractedLinks = analysis.obj.link_data,
            metadata = mapOf(
                "tags" to (analysis.obj.tags ?: emptyList<String>())
            ),
            shouldTerminate = false
        )
    }

    open fun analysisGoal(context: PageProcessingStrategy.ProcessingContext): String = when {
        context.executionConfig.content_queries != null -> context.executionConfig.content_queries.toJson()
        context.executionConfig.task_description?.isNotBlank() == true -> context.executionConfig.task_description!!
        else -> "Analyze the content and provide insights."
    }

    override fun shouldContinueCrawling(
        currentResults: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.ContinuationDecision {
        // Never early-terminate, use existing page limit logic
        return PageProcessingStrategy.ContinuationDecision(
            shouldContinue = context.processedCount.get() < context.maxPages,
            reason = "Processing up to max_pages limit"
        )
    }

    override fun generateFinalOutput(
        results: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): String {
        val analysisResults = results.joinToString("\n") { it.content }
        return createFinalSummary(analysisResults, context)
    }

    private fun createFinalSummary(analysisResults: String, context: PageProcessingStrategy.ProcessingContext): String {
        val maxFinalOutputSize = context.typeConfig.max_final_output_size ?: 15000

        if (analysisResults.length < maxFinalOutputSize * 1.2) {
            return analysisResults.substring(0, min(analysisResults.length, maxFinalOutputSize)) +
                    "\n\n---\n\n*Note: Some content has been truncated due to length limitations.*"
        }

        val summary = ChatAgent(
            prompt = listOf(
                "Create a comprehensive summary of the following web search results and analyses.",
                "Analysis goal: ${context.executionConfig.content_queries ?: context.executionConfig.task_description ?: "Provide key insights"}",
                "For each source, extract the most important insights, facts, and conclusions.",
                "Organize information by themes rather than by source when possible.",
                "Use markdown formatting with headers, bullet points, and emphasis where appropriate.",
                "Include the most important links that should be followed up on.",
                "Keep your response under ${maxFinalOutputSize / 1000}K characters."
            ).joinToString("\n\n"),
            model = (context.typeConfig.model?.let { context.orchestrationConfig.instance(it) }
                ?: context.orchestrationConfig.defaultFast).getChildClient(context.task),
        ).answer(
            listOf("Here are summaries of each analyzed page:\n${analysisResults}"),
        )

        return summary
    }

    override fun validateConfig(config: Any?): String? {
        // Default strategy doesn't require specific config validation
        return null
    }

    private fun transformContent(
        content: String,
        analysisGoal: String,
        context: PageProcessingStrategy.ProcessingContext
    ): ParsedResponse<CrawlerAgentTask.ParsedPage> {
        val describer = TaskContextYamlDescriber(context.orchestrationConfig)
        val maxChunkSize = 50000
        if (content.length <= maxChunkSize) {
            return pageParsedResponse(context, analysisGoal, content, describer)
        }
        val chunks = splitContentIntoChunks(content, maxChunkSize)
        val chunkResults = chunks.mapIndexed { index, chunk ->
            val chunkGoal = "$analysisGoal (Part ${index + 1}/${chunks.size})"
            pageParsedResponse(context, chunkGoal, chunk, describer)
        }
        if (chunkResults.size == 1) {
            return chunkResults[0]
        }
        val combinedAnalysis = chunkResults.joinToString("\n\n---\n\n") { it.text }
        return pageParsedResponse(context, analysisGoal, combinedAnalysis, describer)
    }

    private fun pageParsedResponse(
        context: PageProcessingStrategy.ProcessingContext,
        analysisGoal: String,
        content: String,
        describer: TypeDescriber
    ): ParsedResponse<CrawlerAgentTask.ParsedPage> {
        return try {
            val model = (context.typeConfig.model?.let { context.orchestrationConfig.instance(it) }
                ?: context.orchestrationConfig.defaultFast).getChildClient(context.task)
            ParsedAgent(
                prompt = listOf(
                    "Below are analyses of different parts of a web page related to this goal: $analysisGoal",
                    "Create a unified summary that combines the key insights from all parts.",
                    "Use markdown formatting for your response, with * characters for bullets.",
                    "Identify the most important links that should be followed up on according to the goal."
                ).joinToString("\n\n"),
                resultClass = CrawlerAgentTask.ParsedPage::class.java,
                model = model,
                describer = describer,
                parsingChatter = model,
            ).answer(listOf(content))
        } catch (e: Exception) {
            log.error("Error during content transformation", e)
            object : ParsedResponse<CrawlerAgentTask.ParsedPage>(
                clazz = CrawlerAgentTask.ParsedPage::class.java
            ) {
                override val obj: CrawlerAgentTask.ParsedPage
                    get() = CrawlerAgentTask.ParsedPage(
                        page_type = CrawlerAgentTask.PageType.Error,
                        page_information = "Error during analysis: ${e.message}"
                    )
                override val text: String
                    get() = "Error during analysis: ${e.message}"
            }
        }
    }

    private fun splitContentIntoChunks(content: String, maxChunkSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var remainingContent = content
        while (remainingContent.isNotEmpty()) {
            val chunkSize = if (remainingContent.length <= maxChunkSize) {
                remainingContent.length
            } else {
                findBreakPoint(remainingContent, maxChunkSize)
            }
            chunks.add(remainingContent.substring(0, chunkSize))
            remainingContent = remainingContent.substring(chunkSize)
        }
        return chunks
    }

    private fun findBreakPoint(text: String, maxSize: Int): Int {
        val paragraphBreakSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf("\n\n")
        if (paragraphBreakSearch > maxSize * 0.7) {
            return paragraphBreakSearch + 2
        }
        val newlineSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf("\n")
        if (newlineSearch > maxSize * 0.7) {
            return newlineSearch + 1
        }
        val sentenceSearch = text.substring(0, minOf(maxSize, text.length)).lastIndexOf(". ")
        if (sentenceSearch > maxSize * 0.7) {
            return sentenceSearch + 2
        }
        return minOf(maxSize, text.length)
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/FactCheckingStrategy.kt

```
package com.simiacryptus.cognotik.crawl.processing

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.jsonCast
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class FactCheckingStrategy : PageProcessingStrategy {

    data class FactCheckingConfig(
        @Description("Claims to verify")
        val claims_to_verify: List<String>,
        @Description("Required confidence level (0.0-1.0)")
        val confidence_threshold: Double = 0.8,
        @Description("Stop after finding N supporting sources")
        val required_sources: Int = 3,
        @Description("Stop after finding N contradicting sources")
        val contradiction_threshold: Int = 2
    )

    override val description: String // Describe both the strategy and its configuration
        get() = """Fact-Checking Strategy: Verifies specified claims against web page content. 
      |Configuration options include claims to verify, confidence thresholds, and source requirements.""".trimMargin()

    data class FactCheckResult(
        val claim: String,
        val verdict: FactVerdict,
        val confidence: Double,
        val supporting_evidence: List<Evidence>,
        val contradicting_evidence: List<Evidence>,
        val neutral_evidence: List<Evidence>
    )

    enum class FactVerdict {
        SUPPORTED,
        CONTRADICTED,
        INSUFFICIENT_EVIDENCE,
        MIXED
    }

    data class Evidence(
        val source_url: String,
        val excerpt: String,
        val relevance_score: Double,
        val credibility_score: Double
    )

    private val verificationResults = ConcurrentHashMap<String, MutableList<FactCheckResult>>()

    companion object {
        private val log = LoggerFactory.getLogger(FactCheckingStrategy::class.java)
    }

    override fun processPage(
        url: String,
        content: String,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.PageProcessingResult {
        val config =
            context.executionConfig.content_queries?.parserCast<FactCheckingConfig>(context.orchestrationConfig.defaultFast)
                ?: return PageProcessingStrategy.PageProcessingResult(
                    url = url,
                    pageType = CrawlerAgentTask.PageType.Error,
                    content = "Missing FactCheckingConfig",
                    extractedLinks = null,
                    metadata = emptyMap(),
                    shouldTerminate = true,
                    terminationReason = "Configuration error"
                )

        // Analyze page for each claim
        val pageResults = config.claims_to_verify.map { claim ->
            analyzeClaimEvidence(claim, content, url, context)
        }

        // Update global verification state
        pageResults.forEach { result ->
            verificationResults.getOrPut(result.claim) { mutableListOf() }.add(result)
        }

        // Check if we have enough evidence to terminate
        val shouldTerminate = checkTerminationConditions(config)

        return PageProcessingStrategy.PageProcessingResult(
            url = url,
            pageType = CrawlerAgentTask.PageType.OK,
            content = formatFactCheckResults(pageResults),
            extractedLinks = extractRelevantLinks(content, config.claims_to_verify),
            metadata = mapOf(
                "fact_check_results" to pageResults,
                "claims_analyzed" to config.claims_to_verify.size
            ),
            shouldTerminate = shouldTerminate,
            terminationReason = if (shouldTerminate) "Sufficient evidence gathered" else null
        )
    }

    private fun formatFactCheckResults(results: List<FactCheckResult>): String {
        return buildString {
            results.forEach { result ->
                appendLine("### Claim: ${result.claim}")
                appendLine("**Verdict:** ${result.verdict.name}")
                appendLine("**Confidence:** ${result.confidence}")
                appendLine()
                if (result.supporting_evidence.isNotEmpty()) {
                    appendLine("**Supporting Evidence:**")
                    result.supporting_evidence.forEach { evidence ->
                        appendLine("- ${evidence.excerpt} (relevance: ${evidence.relevance_score})")
                    }
                    appendLine()
                }
                if (result.contradicting_evidence.isNotEmpty()) {
                    appendLine("**Contradicting Evidence:**")
                    result.contradicting_evidence.forEach { evidence ->
                        appendLine("- ${evidence.excerpt} (relevance: ${evidence.relevance_score})")
                    }
                    appendLine()
                }
            }
        }
    }

    private fun extractRelevantLinks(content: String, claims: List<String>): List<CrawlerAgentTask.LinkData> {
        val linkPattern = Pattern.compile("""\[([^]]+)]\(([^)]+)\)""")
        val matcher = linkPattern.matcher(content)
        val links = mutableListOf<CrawlerAgentTask.LinkData>()
        while (matcher.find()) {
            val linkText = matcher.group(1)
            val linkUrl = matcher.group(2)
            // Check if link text is relevant to any claim
            val isRelevant = claims.any { claim ->
                linkText.contains(claim, ignoreCase = true) ||
                        claim.split(" ").any { word -> linkText.contains(word, ignoreCase = true) }
            }
            if (isRelevant) {
                links.add(
                    CrawlerAgentTask.LinkData(
                        url = linkUrl,
                        title = linkText,
                        relevance_score = 80.0
                    )
                )
            }
        }
        return links
    }

    private fun analyzeClaimEvidence(
        claim: String,
        content: String,
        url: String,
        context: PageProcessingStrategy.ProcessingContext
    ): FactCheckResult {
        val prompt = """
            Analyze the following content for evidence related to this claim:

            CLAIM: $claim

            Determine if the content:
            1. Supports the claim
            2. Contradicts the claim
            3. Is neutral/irrelevant

            Extract specific excerpts that serve as evidence.
            Rate the relevance (0.0-1.0) and credibility (0.0-1.0) of the source.
        """.trimIndent()

        val analysis = ParsedAgent(
            prompt = prompt,
            resultClass = FactCheckResult::class.java,
            model = context.orchestrationConfig.defaultFast.getChildClient(context.task),
            parsingChatter = context.orchestrationConfig.defaultFast.getChildClient(context.task)
        ).answer(listOf(content))

        return analysis.obj.copy(
            supporting_evidence = analysis.obj.supporting_evidence.map {
                it.copy(source_url = url)
            },
            contradicting_evidence = analysis.obj.contradicting_evidence.map {
                it.copy(source_url = url)
            }
        )
    }

    private fun checkTerminationConditions(config: FactCheckingConfig): Boolean {
        return config.claims_to_verify.all { claim ->
            val results = verificationResults[claim] ?: return@all false

            val supportCount = results.count { it.verdict == FactVerdict.SUPPORTED }
            val contradictCount = results.count { it.verdict == FactVerdict.CONTRADICTED }

            // Terminate if we have enough supporting OR contradicting evidence
            supportCount >= config.required_sources ||
                    contradictCount >= config.contradiction_threshold
        }
    }

    override fun shouldContinueCrawling(
        currentResults: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.ContinuationDecision {
        val anyTermination = currentResults.any { it.shouldTerminate }

        return PageProcessingStrategy.ContinuationDecision(
            shouldContinue = !anyTermination && context.processedCount.get() < context.maxPages,
            reason = if (anyTermination) {
                currentResults.first { it.shouldTerminate }.terminationReason ?: "Early termination"
            } else {
                "Continue gathering evidence"
            }
        )
    }

    override fun generateFinalOutput(
        results: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): String {
        val config = context.executionConfig.content_queries?.jsonCast<FactCheckingConfig>()
            ?: return "Error: Missing FactCheckingConfig"

        return buildString {
            appendLine("# Fact-Checking Report")
            appendLine()
            appendLine("**Generated:** ${LocalDateTime.now()}")
            appendLine("**Pages Analyzed:** ${results.size}")
            appendLine()

            config.claims_to_verify.forEach { claim ->
                appendLine("## Claim: $claim")
                appendLine()

                val claimResults = verificationResults[claim] ?: emptyList()
                val verdict = determineOverallVerdict(claimResults, config.confidence_threshold)

                appendLine("**Verdict:** ${verdict.name}")
                appendLine()

                appendLine("### Supporting Evidence (${claimResults.flatMap { it.supporting_evidence }.size})")
                claimResults.flatMap { it.supporting_evidence }.forEach { evidence ->
                    appendLine("- [${evidence.source_url}](${evidence.source_url})")
                    appendLine("  - Relevance: ${evidence.relevance_score}")
                    appendLine("  - Credibility: ${evidence.credibility_score}")
                    appendLine("  - Excerpt: \"${evidence.excerpt}\"")
                    appendLine()
                }

                appendLine("### Contradicting Evidence (${claimResults.flatMap { it.contradicting_evidence }.size})")
                claimResults.flatMap { it.contradicting_evidence }.forEach { evidence ->
                    appendLine("- [${evidence.source_url}](${evidence.source_url})")
                    appendLine("  - Relevance: ${evidence.relevance_score}")
                    appendLine("  - Credibility: ${evidence.credibility_score}")
                    appendLine("  - Excerpt: \"${evidence.excerpt}\"")
                    appendLine()
                }

                appendLine("---")
                appendLine()
            }
        }
    }

    private fun determineOverallVerdict(
        results: List<FactCheckResult>,
        threshold: Double
    ): FactVerdict {
        if (results.isEmpty()) return FactVerdict.INSUFFICIENT_EVIDENCE

        val avgConfidence = results.map { it.confidence }.average()
        val supportCount = results.count { it.verdict == FactVerdict.SUPPORTED }
        val contradictCount = results.count { it.verdict == FactVerdict.CONTRADICTED }

        return when {
            avgConfidence < threshold -> FactVerdict.INSUFFICIENT_EVIDENCE
            supportCount > contradictCount * 2 -> FactVerdict.SUPPORTED
            contradictCount > supportCount * 2 -> FactVerdict.CONTRADICTED
            else -> FactVerdict.MIXED
        }
    }

    override fun validateConfig(config: Any?): String? {
        return null
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/JobMatchingStrategy.kt

```
package com.simiacryptus.cognotik.crawl.processing

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class JobMatchingStrategy : DefaultSummarizerStrategy() {

    data class JobMatchingConfig(
        @Description("User's resume/experience summary")
        val user_experience: String = "",
        @Description("Desired job titles or keywords")
        val target_roles: List<String> = listOf(),
        @Description("Required skills to match")
        val required_skills: List<String>? = null,
        @Description("Preferred locations (cities, states, countries, or 'Remote')")
        val preferred_locations: List<String>? = null,
        @Description("Acceptable locations (if different from preferred)")
        val acceptable_locations: List<String>? = null,
        @Description("Excluded locations")
        val excluded_locations: List<String>? = null,
        @Description("Minimum match score (0.0-1.0)")
        val min_match_score: Double = 0.6,
        @Description("(Optional) Stop after finding N good matches")
        val target_matches: Int? = null,
        @Description("Automatically adjust match threshold based on results")
        val adaptive_threshold: Boolean = false,
        @Description("Preferred industries")
        val preferred_industries: List<String>? = null,
        @Description("Excluded companies")
        val excluded_companies: List<String>? = null,
        @Description("Minimum acceptable salary (annual)")
        val min_salary: Int? = null,
        @Description("Target salary (annual)")
        val target_salary: Int? = null,
        @Description("Maximum salary expectation (annual)")
        val max_salary: Int? = null,
        @Description("Currency for salary (e.g., USD, EUR, GBP)")
        val salary_currency: String = "USD",
        @Description("Work arrangement preference: 'remote', 'hybrid', 'onsite', or 'flexible'")
        val work_arrangement_preference: String? = null,
        @Description("Maximum acceptable days in office per week (for hybrid roles)")
        val max_days_in_office: Int? = null,
        @Description("Willing to travel (percentage or 'none', 'occasional', 'frequent')")
        val travel_willingness: String? = null,
        @Description("Maximum acceptable travel percentage (0-100)")
        val max_travel_percentage: Int? = null,
        @Description("Willing to relocate")
        val willing_to_relocate: Boolean = false,
        @Description("Relocation assistance required")
        val requires_relocation_assistance: Boolean = false
    )


    override val description: String
        get() = "Analyzes job postings against user experience to find strong matches, generates application materials, and saves detailed reports."

    data class JobAnalysis(
        @Description("Job title/position name")
        val job_title: String? = null,
        @Description("Company/organization name")
        val company: String? = null,
        @Description("Primary job location (city, state, country)")
        val location: String? = null,
        @Description("Additional locations or service areas")
        val additional_locations: List<String>? = null,
        @Description("Work arrangement: 'remote', 'hybrid', 'onsite'")
        val work_arrangement: String? = null,
        @Description("Days in office per week (for hybrid)")
        val days_in_office: Int? = null,
        @Description("Travel requirements description")
        val travel_requirements: String? = null,
        @Description("Travel percentage (0-100)")
        val travel_percentage: Int? = null,
        @Description("Relocation offered")
        val relocation_offered: Boolean? = null,
        @Description("Relocation assistance details")
        val relocation_assistance: String? = null,
        @Description("URL where the candidate can apply for the position")
        val application_url: String? = null,
        @Description("URL of the original job description page")
        val job_description_url: String? = null,
        @Description("Full text of the job description")
        val job_description: String? = null,
        @Description("Minimum salary offered (if disclosed)")
        val salary_min: Int? = null,
        @Description("Maximum salary offered (if disclosed)")
        val salary_max: Int? = null,
        @Description("Salary currency")
        val salary_currency: String? = null,
        @Description("Salary period: 'annual', 'hourly', 'monthly'")
        val salary_period: String? = null,
        @Description("Additional compensation details (bonus, equity, etc.)")
        val compensation_details: String? = null,
        @Description("List of skills explicitly required for the position")
        val required_skills: List<String> = listOf(),
        @Description("List of skills that are preferred but not required")
        val preferred_skills: List<String> = listOf(),
        @Description("Overall match score between candidate and position (0.0-1.0)")
        val match_score: Double = 0.0,
        @Description("Location compatibility score (0.0-1.0)")
        val location_score: Double = 0.0,
        @Description("Salary compatibility score (0.0-1.0)")
        val salary_score: Double = 0.0,
        @Description("Work arrangement compatibility score (0.0-1.0)")
        val work_arrangement_score: Double = 0.0,
        @Description("Detailed analysis of how well the candidate matches the position")
        val match_analysis: String = "",
        @Description("Analysis of location and work arrangement fit")
        val location_analysis: String = "",
        @Description("Analysis of compensation fit")
        val compensation_analysis: String = "",
        @Description("Skills the candidate lacks that are required or preferred")
        val skill_gaps: List<String> = listOf(),
        @Description("Skills the candidate has that match the job requirements")
        val skill_matches: List<String> = listOf(),
        @Description("Draft cover letter tailored to this specific position")
        val cover_letter: String? = null,
        @Description("Strategic notes and recommendations for the application")
        val application_notes: String? = null
    )

    private val goodMatches = ConcurrentHashMap<String, JobAnalysis>()

    companion object {
        private val log = LoggerFactory.getLogger(JobMatchingStrategy::class.java)
    }

    override fun processPage(
        url: String,
        content: String,
        context: PageProcessingStrategy.ProcessingContext
    ) = try {
        log.debug("Processing page: $url")
        val chatInterface = context.orchestrationConfig.defaultFast.getChildClient(context.task)
        val config = context.executionConfig.content_queries?.parserCast<JobMatchingConfig>(chatInterface)
            ?: run {
                val errorMsg = "Missing JobMatchingConfig for job matching strategy"
                log.error(errorMsg)
                writeToTranscript(context, "**ERROR:** $errorMsg\n")
                throw IllegalArgumentException(errorMsg)
            }

        if (detectJobPosting(content, chatInterface)) {
            log.info("Job posting detected at: $url")
            processJD(url, content, config, context, chatInterface).let { result ->
                val standardProcessing = super.processPage(url, content, context)
                result.copy(
                    extractedLinks = standardProcessing.extractedLinks
                )
            }
        } else {
            log.debug("Page is not a job posting, using default processing: $url")
            super.processPage(url, content, context)
        }
    } catch (e: Exception) {
        val errorMsg = "Error processing page for URL: $url - ${e.message}"
        log.error(errorMsg, e)
        context.task.error(e)
        writeToTranscript(context, "\n**ERROR:** $errorMsg\n```text\n${e.stackTraceToString().indent("  ")}\n```\n\n")
        super.processPage(url, content, context)
    }

    private fun writeToTranscript(context: PageProcessingStrategy.ProcessingContext, message: String) {
        context.transcriptStream?.let { stream ->
            try {
                stream.write(message.toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            } catch (e: IOException) {
                log.warn("Failed to write to transcript stream", e)
            }
        }
    }

    private fun processJD(
        url: String,
        content: String,
        config: JobMatchingConfig,
        context: PageProcessingStrategy.ProcessingContext,
        chatInterface: ChatInterface
    ): PageProcessingStrategy.PageProcessingResult {
        log.debug("Processing job description for URL: $url")

        // Log job detection to transcript
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        writeToTranscript(context, "\n#### Job Posting Detected at $timestamp\n**URL:** [$url]($url)\n\n")

        // Extract and analyze job details
        val jobAnalysis = try {
            analyzeJobMatch(url, content, config, context, chatInterface)
        } catch (e: Exception) {
            val errorMsg = "Failed to analyze job match for URL: $url - ${e.message}"
            log.error(errorMsg, e)
            context.task.error(e)
            writeToTranscript(context, "\n**ERROR:** $errorMsg\n")
            throw e
        }

        // If it's a good match, save detailed report
        val shouldTerminate = if (jobAnalysis.match_score >= config.min_match_score) {
            goodMatches[url] = jobAnalysis
            try {
                saveJobReport(jobAnalysis, context)
            } catch (e: Exception) {
                val errorMsg =
                    "Failed to save job report for ${jobAnalysis.company} - ${jobAnalysis.job_title}: ${e.message}"
                log.error(errorMsg, e)
                context.task.error(e)
                writeToTranscript(context, "\n**ERROR:** \n```text\n${errorMsg.indent("  ")}\n```\n")
                // Don't throw - we still want to continue processing
            }

            // Log good match to transcript
            writeToTranscript(context, buildString {

                appendLine("<details>")
                appendLine("<summary>**✅ GOOD MATCH FOUND** (Score: ${(jobAnalysis.match_score * 100).toInt()}%)</summary>")
                appendLine("\n\n```json\n${jobAnalysis.toJson()}\n```\n\n")
                appendLine("</details>\n")
                appendLine("- **URL:** [$url]($url)\n\n")
                appendLine("- **Position:** ${jobAnalysis.job_title}")
                appendLine("- **Company:** ${jobAnalysis.company}")
                appendLine("- **Location:** ${jobAnalysis.location ?: "Not specified"}")
                appendLine("- **Total Matches Found:** ${goodMatches.size}/${config.target_matches}\n")
            })

            log.info("Good match found: ${jobAnalysis.company} - ${jobAnalysis.job_title} (Score: ${jobAnalysis.match_score})")

            // Check if we've found enough good matches
            config.target_matches != null && config.target_matches > 0 && goodMatches.size >= config.target_matches
        } else {
            // Log weak match to transcript
            writeToTranscript(context, buildString {
                appendLine("<details>")
                appendLine("<summary>**⚠️ Weak Match** (Score: ${(jobAnalysis.match_score * 100).toInt()}%)</summary>")
                appendLine("\n\n```json\n${jobAnalysis.toJson()}\n```\n\n")
                appendLine("</details>\n")
                appendLine("- **URL:** [$url]($url)\n\n")
                appendLine("- **Position:** ${jobAnalysis.job_title}")
                appendLine("- **Company:** ${jobAnalysis.company}\n")
            })

            log.debug("Weak match: ${jobAnalysis.company} - ${jobAnalysis.job_title} (Score: ${jobAnalysis.match_score})")
            false
        }

        return PageProcessingStrategy.PageProcessingResult(
            url = url,
            pageType = CrawlerAgentTask.PageType.OK,
            content = formatJobAnalysis(jobAnalysis),
            metadata = mapOf(
                "job_analysis" to jobAnalysis,
                "match_score" to jobAnalysis.match_score,
                "is_good_match" to (jobAnalysis.match_score >= config.min_match_score)
            ),
            shouldTerminate = shouldTerminate,
            terminationReason = if (shouldTerminate) {
                "Found ${config.target_matches} good job matches"
            } else null
        )
    }

    data class JobDetection(
        val is_job_posting: Boolean = false,
        val confidence: Double = 0.0,
        val detected_title: String? = null,
    )

    private fun detectJobPosting(
        content: String,
        chatInterface: ChatInterface
    ): Boolean {
        log.debug("Detecting if content is a job posting")

        val prompt = """
            Analyze if this content is a job posting/description.

            Look for:
            - Job title
            - Company name
            - Job responsibilities
            - Required qualifications
            - Application instructions
        """.trimIndent()
        val detection = try {
            ParsedAgent(
                prompt = prompt,
                resultClass = JobDetection::class.java,
                model = chatInterface,
                parsingChatter = chatInterface
            ).getParser().apply(content.take(5000)) // Only analyze first 5K chars for detection, using 1 pass (faster)
        } catch (e: Exception) {
            log.error("Failed to detect job posting", e)
            throw e
        }

        log.debug("Job posting detection result: is_job=${detection.is_job_posting}, confidence=${detection.confidence}")
        return detection.is_job_posting && detection.confidence > 0.7
    }

    private fun analyzeJobMatch(
        url: String,
        content: String,
        config: JobMatchingConfig,
        context: PageProcessingStrategy.ProcessingContext,
        chatInterface: ChatInterface
    ): JobAnalysis {
        log.debug("Analyzing job match for URL: $url")

        // Log analysis start to transcript
        writeToTranscript(context, "**Analyzing job match...**\n")

        // Build enriched context from messages
        val additionalContext = if (context.messages.isNotEmpty()) {
            buildString {
                appendLine()
                appendLine("ADDITIONAL CONTEXT FROM USER:")
                context.messages.forEach { message ->
                    appendLine(message)
                    appendLine()
                }
            }
        } else ""
        val locationContext = buildString {
            appendLine()
            appendLine("LOCATION PREFERENCES:")
            config.preferred_locations?.let { appendLine("Preferred: ${it.joinToString(", ")}") }
            config.acceptable_locations?.let { appendLine("Acceptable: ${it.joinToString(", ")}") }
            config.excluded_locations?.let { appendLine("Excluded: ${it.joinToString(", ")}") }
            appendLine("Willing to relocate: ${config.willing_to_relocate}")
            if (config.requires_relocation_assistance) appendLine("Requires relocation assistance")
        }
        val compensationContext = buildString {
            appendLine()
            appendLine("COMPENSATION EXPECTATIONS:")
            config.min_salary?.let { appendLine("Minimum: ${config.salary_currency} $it/year") }
            config.target_salary?.let { appendLine("Target: ${config.salary_currency} $it/year") }
            config.max_salary?.let { appendLine("Maximum: ${config.salary_currency} $it/year") }
        }
        val workArrangementContext = buildString {
            appendLine()
            appendLine("WORK ARRANGEMENT PREFERENCES:")
            config.work_arrangement_preference?.let { appendLine("Preference: $it") }
            config.max_days_in_office?.let { appendLine("Max days in office: $it/week") }
            config.travel_willingness?.let { appendLine("Travel willingness: $it") }
            config.max_travel_percentage?.let { appendLine("Max travel: $it%") }
        }


        val prompt = """
            Analyze this job posting and compare it to the candidate's experience.

            CANDIDATE EXPERIENCE:
            ${config.user_experience}

            TARGET ROLES: ${config.target_roles.joinToString(", ")}
            REQUIRED SKILLS: ${config.required_skills?.joinToString(", ") ?: "Not specified"}
            ${locationContext}
            ${compensationContext}
            ${workArrangementContext}
            ${additionalContext}

            Extract:
            1. Job title, company, location
            2. Work arrangement (remote/hybrid/onsite), days in office if hybrid
            3. Travel requirements and percentage
            4. Relocation information
            5. Salary range and compensation details (if disclosed)
            6. Application URL
            7. Required and preferred skills
            8. Overall match score (0.0-1.0) based on experience alignment
            9. Location compatibility score (0.0-1.0) considering preferences and work arrangement
            10. Salary compatibility score (0.0-1.0) if salary disclosed
            11. Work arrangement compatibility score (0.0-1.0)
            12. Detailed match analysis
            13. Location and work arrangement analysis
            14. Compensation analysis (if salary disclosed)
            15. Skill gaps and matches
            16. Draft a compelling cover letter (200-300 words) that incorporates the additional context and highlights relevant experience
            17. Application strategy notes
            
            When drafting the cover letter, pay special attention to any specific requirements, preferences, or context 
            provided in the additional context section. Tailor the letter to address these points directly.
            IMPORTANT: When calculating scores, consider:
            - Location score: Match against preferred/acceptable locations, work arrangement fit, relocation needs
            - Salary score: Only calculate if salary is disclosed; compare against min/target/max expectations
            - Work arrangement score: Match remote/hybrid/onsite preference, travel requirements, days in office
            - Overall match score: Weight skills heavily, but factor in location, salary, and work arrangement
        """.trimIndent()

        val analysis = try {
            ParsedAgent(
                prompt = prompt,
                resultClass = JobAnalysis::class.java,
                model = chatInterface,
                parsingChatter = chatInterface
            ).answer(listOf(content))
        } catch (e: Exception) {
            log.error("Failed to analyze job match", e)
            throw e
        }

        log.debug("Job analysis completed with match score: ${analysis.obj.match_score}")
        return analysis.obj.copy(
            job_description_url = url,
        )
    }

    private fun saveJobReport(
        jobAnalysis: JobAnalysis,
        context: PageProcessingStrategy.ProcessingContext
    ) {
        log.debug("Saving job report for: ${jobAnalysis.company} - ${jobAnalysis.job_title}")

        // Log report save to transcript
        writeToTranscript(context, "**Saving detailed job report...**\n")

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val companySafe = jobAnalysis.company?.replace(Regex("[^a-zA-Z0-9]"), "_")?.take(30) ?: "UnknownCompany"
        val titleSafe = jobAnalysis.job_title?.replace(Regex("[^a-zA-Z0-9]"), "_")?.take(30)

        val reportDir = File(context.webSearchDir, "job_matches")

        try {
            if (!reportDir.exists() && !reportDir.mkdirs()) {
                throw IOException("Failed to create report directory: ${reportDir.absolutePath}")
            }
        } catch (e: Exception) {
            log.error("Failed to create report directory", e)
            context.task.error(e)
            throw e
        }

        val reportFile = File(reportDir, "${companySafe}_${titleSafe}_${timestamp}.md")

        val report = buildString {
            appendLine("# Job Application Report")
            appendLine()
            appendLine("**Generated:** ${LocalDateTime.now()}")
            appendLine("**Match Score:** ${jobAnalysis.match_score}")
            appendLine("**Job URL:** ${jobAnalysis.application_url}")
            appendLine()

            appendLine("## Position Details")
            appendLine("- **Title:** ${jobAnalysis.job_title}")
            appendLine("- **Company:** ${jobAnalysis.company}")
            appendLine("- **Location:** ${jobAnalysis.location ?: "Not specified"}")
            jobAnalysis.additional_locations?.let {
                if (it.isNotEmpty()) {
                    appendLine("- **Additional Locations:** ${it.joinToString(", ")}")
                }
            }
            appendLine("- **Work Arrangement:** ${jobAnalysis.work_arrangement ?: "Not specified"}")
            jobAnalysis.days_in_office?.let {
                appendLine("- **Days in Office:** $it/week")
            }
            jobAnalysis.travel_percentage?.let {
                appendLine("- **Travel Required:** $it%")
            }
            jobAnalysis.travel_requirements?.let {
                appendLine("- **Travel Details:** $it")
            }
            appendLine("- **Job Description URL:** ${jobAnalysis.job_description_url}")
            appendLine("- **Application URL:** [Apply Here](${jobAnalysis.application_url})")
            appendLine()
            appendLine("## Compensation")
            if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
                val salaryRange = buildString {
                    jobAnalysis.salary_min?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
                    if (jobAnalysis.salary_min != null && jobAnalysis.salary_max != null) append(" - ")
                    jobAnalysis.salary_max?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
                    jobAnalysis.salary_period?.let { append(" ($it)") }
                }
                appendLine("- **Salary Range:** $salaryRange")
            } else {
                appendLine("- **Salary Range:** Not disclosed")
            }
            jobAnalysis.compensation_details?.let {
                appendLine("- **Additional Compensation:** $it")
            }
            if (jobAnalysis.relocation_offered == true) {
                appendLine("- **Relocation:** Offered")
                jobAnalysis.relocation_assistance?.let {
                    appendLine("  - $it")
                }
            }
            appendLine()

            appendLine("## Match Analysis")
            appendLine("### Overall Match Score: ${(jobAnalysis.match_score * 100).toInt()}%")
            appendLine(jobAnalysis.match_analysis)
            appendLine()
            appendLine("### Location Compatibility: ${(jobAnalysis.location_score * 100).toInt()}%")
            appendLine(jobAnalysis.location_analysis)
            appendLine()
            if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
                appendLine("### Salary Compatibility: ${(jobAnalysis.salary_score * 100).toInt()}%")
                appendLine(jobAnalysis.compensation_analysis)
                appendLine()
            }
            appendLine("### Work Arrangement Fit: ${(jobAnalysis.work_arrangement_score * 100).toInt()}%")
            appendLine()

            appendLine("## Skills Assessment")
            appendLine("### Matching Skills (${jobAnalysis.skill_matches.size})")
            jobAnalysis.skill_matches.forEach { skill ->
                appendLine("- ✅ $skill")
            }
            appendLine()

            appendLine("### Skill Gaps (${jobAnalysis.skill_gaps.size})")
            jobAnalysis.skill_gaps.forEach { skill ->
                appendLine("- ⚠️ $skill")
            }
            appendLine()

            appendLine("## Cover Letter Draft")
            appendLine()
            appendLine(jobAnalysis.cover_letter)
            appendLine()

            appendLine("## Application Strategy")
            appendLine(jobAnalysis.application_notes)
            appendLine()

            appendLine("---")
            appendLine()
            appendLine("## Job Description")
            appendLine()
            appendLine(jobAnalysis.job_description)
            appendLine("---")
            appendLine()

            appendLine("<details>")
            appendLine("<summary>Job Analysis Data (JSON)</summary>")
            appendLine()
            appendLine("```json")
            appendLine(jobAnalysis.toJson())
            appendLine("```")
            appendLine()
            appendLine("</details>")
        }

        try {
            reportFile.writeText(report)
            log.info("Saved job report: ${reportFile.absolutePath}")
        } catch (e: IOException) {
            val errorMsg = "Failed to write job report to file: ${reportFile.absolutePath}"
            log.error(errorMsg, e)
            context.task.error(e)
            throw IOException(errorMsg, e)
        }

        // Log report location to transcript
    }

    private fun formatJobAnalysis(jobAnalysis: JobAnalysis): String {
        return buildString {
            appendLine("### ${jobAnalysis.job_title} at ${jobAnalysis.company}")
            appendLine()
            appendLine("**Match Score:** ${(jobAnalysis.match_score * 100).toInt()}%")
            appendLine("**Location:** ${jobAnalysis.location ?: "Not specified"}")
            jobAnalysis.work_arrangement?.let {
                appendLine("**Work Arrangement:** $it")
            }
            if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
                val salaryRange = buildString {
                    jobAnalysis.salary_min?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
                    if (jobAnalysis.salary_min != null && jobAnalysis.salary_max != null) append(" - ")
                    jobAnalysis.salary_max?.let { append("${jobAnalysis.salary_currency ?: "USD"} $it") }
                }
                appendLine("**Salary:** $salaryRange")
            }
            appendLine("**Application:** [Apply Here](${jobAnalysis.application_url})")
            appendLine()

            if (jobAnalysis.match_score >= 0.6) {
                appendLine("✅ **Good Match** - Detailed report saved")
            } else {
                appendLine("⚠️ **Weak Match** - Consider other opportunities")
            }
            appendLine()
            appendLine("**Compatibility Scores:**")
            appendLine("- Skills: ${(jobAnalysis.match_score * 100).toInt()}%")
            appendLine("- Location: ${(jobAnalysis.location_score * 100).toInt()}%")
            if (jobAnalysis.salary_min != null || jobAnalysis.salary_max != null) {
                appendLine("- Salary: ${(jobAnalysis.salary_score * 100).toInt()}%")
            }
            appendLine("- Work Arrangement: ${(jobAnalysis.work_arrangement_score * 100).toInt()}%")
            appendLine()

            appendLine("<details>")
            appendLine("<summary>Match Analysis</summary>")
            appendLine()
            appendLine(jobAnalysis.match_analysis)
            appendLine()
            appendLine("</details>")
            appendLine()
        }
    }

    override fun analysisGoal(context: PageProcessingStrategy.ProcessingContext): String = when {
        context.executionConfig.content_queries != null -> context.executionConfig.content_queries.toJson()
        context.executionConfig.task_description?.isNotBlank() == true -> context.executionConfig.task_description!!
        else -> "Analyze the content and provide insights."
    } + " - Identify pages that contain or are likely to lead to job postings matching the user's experience and target roles."

    override fun shouldContinueCrawling(
        currentResults: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.ContinuationDecision {
        val anyTermination = currentResults.any { it.shouldTerminate }
        val reason = if (anyTermination) {
            currentResults.first { it.shouldTerminate }.terminationReason ?: "Target matches found"
        } else {
            "Continue searching for job matches (${goodMatches.size} found so far)"
        }
        val shouldContinue = !anyTermination && context.processedCount.get() < context.maxPages
        return PageProcessingStrategy.ContinuationDecision(shouldContinue, reason)
    }

    override fun generateFinalOutput(
        results: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): String {
        // Log final summary generation to transcript
        context.transcriptStream?.let { stream ->
            try {
                stream.write("\n\n## Job Search Final Summary\n\n".toByteArray(StandardCharsets.UTF_8))
                stream.write("**Total Pages Analyzed:** ${results.size}\n".toByteArray(StandardCharsets.UTF_8))
                stream.write(
                    "**Job Postings Found:** ${results.count { it.metadata["is_job_posting"] == true }}\n".toByteArray(
                        StandardCharsets.UTF_8
                    )
                )
                stream.write("**Good Matches:** ${goodMatches.size}\n\n".toByteArray(StandardCharsets.UTF_8))
                stream.flush()
            } catch (e: Exception) {
                log.debug("Failed to write final summary to transcript", e)
            }
        }

        return buildString {
            appendLine("# Job Search Results")
            appendLine()
            appendLine("**Search Completed:** ${LocalDateTime.now()}")
            appendLine("**Pages Analyzed:** ${results.size}")
            appendLine("**Job Postings Found:** ${results.count { it.metadata["is_job_posting"] == true }}")
            appendLine("**Good Matches:** ${goodMatches.size}")
            appendLine()

            if (goodMatches.isEmpty()) {
                appendLine("⚠️ No strong matches found. Consider:")
                appendLine("- Broadening search criteria")
                appendLine("- Adjusting required skills")
                appendLine("- Expanding target roles")
                appendLine("- Relaxing location or work arrangement preferences")
                appendLine("- Adjusting salary expectations")
                appendLine()
                return@buildString
            }

            appendLine("## Top Matches")
            appendLine()

            goodMatches.values
                .sortedByDescending { it.match_score }
                .forEach { job ->
                    appendLine("### ${job.job_title} at ${job.company}")
                    appendLine()
                    appendLine("**Match Score:** ${(job.match_score * 100).toInt()}%")
                    appendLine("**Location:** ${job.location ?: "Not specified"}")
                    job.work_arrangement?.let {
                        appendLine("**Work Arrangement:** $it")
                    }
                    if (job.salary_min != null || job.salary_max != null) {
                        val salaryRange = buildString {
                            job.salary_min?.let { append("${job.salary_currency ?: "USD"} $it") }
                            if (job.salary_min != null && job.salary_max != null) append(" - ")
                            job.salary_max?.let { append("${job.salary_currency ?: "USD"} $it") }
                        }
                        appendLine("**Salary:** $salaryRange")
                    }
                    appendLine("**Application:** [Apply Here](${job.application_url})")
                    appendLine()
                    appendLine("**Compatibility:**")
                    appendLine("- Skills: ${(job.match_score * 100).toInt()}%")
                    appendLine("- Location: ${(job.location_score * 100).toInt()}%")
                    if (job.salary_min != null || job.salary_max != null) {
                        appendLine("- Salary: ${(job.salary_score * 100).toInt()}%")
                    }
                    appendLine("- Work Arrangement: ${(job.work_arrangement_score * 100).toInt()}%")
                    appendLine()
                    appendLine("**Skills Match:** ${job.skill_matches.size}/${job.skill_matches.size + job.skill_gaps.size}")
                    appendLine()

                    appendLine()
                    appendLine(job.match_analysis.take(300) + "...")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }

            appendLine("## Next Steps")
            appendLine()
            appendLine("1. Review detailed reports in the `job_matches` directory")
            appendLine("2. Customize cover letters for each application")
            appendLine("3. Prepare for interviews by reviewing skill gaps")
            appendLine("4. Track application status")
            appendLine("5. Verify work arrangement and compensation details during screening")
            appendLine("6. Prepare questions about travel requirements and relocation assistance")
            // Log completion to transcript
            context.transcriptStream?.let { stream ->
                try {
                    stream.write("\n**Job search crawling completed successfully**\n".toByteArray(StandardCharsets.UTF_8))
                    stream.write("**Good matches found:** ${goodMatches.size}\n\n".toByteArray(StandardCharsets.UTF_8))
                    stream.flush()
                } catch (e: Exception) {
                    log.debug("Failed to write completion to transcript", e)
                }
            }
        }
    }

    override fun validateConfig(config: Any?): String? {
        return null
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/PageProcessingStrategy.kt

```
package com.simiacryptus.cognotik.crawl.processing

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

interface PageProcessingStrategy {
    val description: String

    /**
     * Process a single page and return results
     *
     * @param url The URL of the page being processed
     * @param content The content of the page
     * @param context The processing context containing configuration and state
     * @return PageProcessingResult containing the processing outcome
     * @throws Exception if processing fails critically
     */
    fun processPage(
        url: String,
        content: String,
        context: ProcessingContext
    ): PageProcessingResult

    /**
     * Determine if crawling should continue
     *
     * @param currentResults The results from pages processed so far
     * @param context The processing context
     * @return ContinuationDecision indicating whether to continue and why
     */
    fun shouldContinueCrawling(
        currentResults: List<PageProcessingResult>,
        context: ProcessingContext
    ): ContinuationDecision

    /**
     * Generate final output from all processed pages
     *
     * @param results All page processing results
     * @param context The processing context
     * @return String containing the final formatted output
     */
    fun generateFinalOutput(
        results: List<PageProcessingResult>,
        context: ProcessingContext
    ): String

    /**
     * Strategy-specific configuration validation
     *
     * @param config The configuration to validate
     * @return Error message if validation fails, null if valid
     */
    fun validateConfig(config: Any?): String?


    data class ProcessingContext(
        val executionConfig: CrawlerTaskExecutionConfigData,
        val typeConfig: CrawlerTaskTypeConfig,
        val orchestrationConfig: OrchestrationConfig,
        val messages: List<String> = emptyList(),
        val task: SessionTask,
        val webSearchDir: File = File("websearch"),
        val processedCount: AtomicInteger = AtomicInteger(0),
        val maxPages: Int = Int.MAX_VALUE,
        val transcriptStream: FileOutputStream? = null
    )

    data class PageProcessingResult(
        val url: String = "",
        val pageType: PageType = PageType.Error,
        val content: String = "",
        val extractedLinks: List<LinkData>? = null,
        val metadata: Map<String, Any> = emptyMap(),
        val shouldTerminate: Boolean = false,
        val terminationReason: String? = null,
        val error: Throwable? = null
    )

    data class ContinuationDecision(
        val shouldContinue: Boolean = true,
        val reason: String = "No specific reason",
    )
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/ProcessingStrategyType.kt

```
package com.simiacryptus.cognotik.crawl.processing

enum class ProcessingStrategyType {

    DefaultSummarizer {
        override fun createStrategy(): PageProcessingStrategy = DefaultSummarizerStrategy()
    },
    FactChecking {
        override fun createStrategy(): PageProcessingStrategy = FactCheckingStrategy()
    },
    JobMatching {
        override fun createStrategy(): PageProcessingStrategy = JobMatchingStrategy()
    },
    SchemaExtraction {
        override fun createStrategy(): PageProcessingStrategy = SchemaExtractionStrategy()
    },
    DataTableAccumulation {;
        override fun createStrategy(): PageProcessingStrategy = DataTableAccumulationStrategy()
    };

    abstract fun createStrategy(): PageProcessingStrategy
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md

```
# Web Crawling and Processing Package

## Overview

This package provides a flexible and powerful framework for web crawling, content extraction, and intelligent data
processing. It combines AI-powered analysis with configurable processing strategies to extract structured information
from websites.

## Core Components

### Page Processing Strategies

The package implements a strategy pattern for processing web pages, allowing different extraction and analysis
approaches:

#### 1. **DefaultSummarizerStrategy**

Basic content analysis and summarization strategy.

**Use Cases:**

- General web content analysis
- Research and information gathering
- Content summarization

**Features:**

- AI-powered content transformation
- Automatic link extraction
- Markdown-formatted output
- Chunked processing for large pages

**Configuration:**

```json
{
  "task_description": "Analyze content and provide insights",
  "content_queries": "Optional specific queries"
}
```

#### 2. **FactCheckingStrategy**

Verifies claims against web content with evidence tracking.

**Use Cases:**

- Claim verification
- Research validation
- Source credibility assessment

**Features:**

- Multi-source evidence collection
- Confidence scoring
- Supporting/contradicting evidence tracking
- Automatic termination when sufficient evidence is gathered

**Configuration:**

```json
{
  "claims_to_verify": ["Claim 1", "Claim 2"],
  "confidence_threshold": 0.8,
  "required_sources": 3,
  "contradiction_threshold": 2
}
```

**Example:**

```kotlin
val config = FactCheckingConfig(
  claims_to_verify = listOf(
    "Company X has 10,000 employees",
    "Product Y was released in 2023"
  ),
  confidence_threshold = 0.8,
  required_sources = 3
)
```

#### 3. **JobMatchingStrategy**

Analyzes job postings and matches them against candidate profiles.

**Use Cases:**

- Job search automation
- Resume matching
- Application material generation

**Features:**

- Automatic job posting detection
- Multi-dimensional matching (skills, location, salary, work arrangement)
- Cover letter generation
- Detailed application reports
- Location and work arrangement compatibility scoring
- Salary range analysis

**Configuration:**

```json
{
  "user_experience": "Your resume/experience summary",
  "target_roles": ["Software Engineer", "Senior Developer"],
  "required_skills": ["Python", "AWS", "Docker"],
  "preferred_locations": ["San Francisco", "Remote"],
  "acceptable_locations": ["California", "New York"],
  "excluded_locations": ["International"],
  "min_match_score": 0.6,
  "target_matches": 10,
  "work_arrangement_preference": "remote",
  "max_days_in_office": 2,
  "min_salary": 120000,
  "target_salary": 150000,
  "max_salary": 180000,
  "salary_currency": "USD",
  "willing_to_relocate": false
}
```

**Output:**

- Individual job reports in `job_matches/` directory
- Cover letters tailored to each position
- Compatibility scores (skills, location, salary, work arrangement)
- Application strategy notes

#### 4. **SchemaExtractionStrategy**

Extracts structured data according to user-defined schemas.

**Use Cases:**

- Data mining
- Structured data extraction
- API-like data collection from websites

**Features:**

- Custom JSON schema definition
- Automatic data validation
- Deduplication
- Aggregated JSON output
- Confidence-based filtering

**Configuration:**

```json
{
  "schema_definition": "{\"name\": \"string\", \"price\": \"number\"}",
  "extraction_instructions": "Extract product information",
  "aggregate_results": true,
  "min_confidence": 0.7,
  "max_items_per_page": 50,
  "validate_schema": true,
  "deduplicate": true,
  "deduplication_keys": "name,id"
}
```

**Example Schema:**

```json
{
  "product_name": "string",
  "price": "number",
  "rating": "number",
  "availability": "boolean",
  "specifications": {
    "weight": "string",
    "dimensions": "string"
  }
}
```

**Output:**

- `aggregated_data.json` - All extracted data
- `aggregated_data_pretty.json` - Pretty-printed version
- `extraction_metadata.json` - Extraction statistics

#### 5. **DataTableAccumulationStrategy**

Builds comprehensive datasets from web pages with configurable columns.

**Use Cases:**

- Competitive analysis
- Market research
- Price comparison
- Feature matrices

**Features:**

- Configurable column definitions
- Type validation
- Data normalization
- Multiple export formats (CSV, JSON, Markdown)
- Automatic HTML table detection
- Row deduplication

**Configuration:**

```json
{
  "column_names": "Product,Price,Rating,Availability",
  "column_descriptions": {
    "Product": "Product name or title",
    "Price": "Price in USD",
    "Rating": "Customer rating out of 5",
    "Availability": "In stock status"
  },
  "column_types": {
    "Product": "string",
    "Price": "number",
    "Rating": "number",
    "Availability": "boolean"
  },
  "extraction_instructions": "Extract product comparison data",
  "auto_detect_tables": true,
  "min_rows": 1,
  "max_rows_per_page": 100,
  "deduplicate": true,
  "key_columns": "Product",
  "validate_types": true,
  "normalize_data": true,
  "export_format": "csv",
  "include_source_urls": true
}
```

**Output:**

- `data_table.csv` / `.json` / `.md` - Exported table
- Column statistics
- Data quality metrics

## Architecture

### ProcessingContext

Shared context for all strategies:

```kotlin
data class ProcessingContext(
  val executionConfig: CrawlerTaskExecutionConfigData,
  val typeConfig: CrawlerTaskTypeConfig,
  val orchestrationConfig: OrchestrationConfig,
  val messages: List<String>,
  val task: SessionTask,
  val webSearchDir: File,
  val processedCount: AtomicInteger,
  val maxPages: Int,
  val transcriptStream: FileOutputStream?
)
```

### PageProcessingResult

Standard result format:

```kotlin
data class PageProcessingResult(
  val url: String,
  val pageType: PageType,
  val content: String,
  val extractedLinks: List<LinkData>?,
  val metadata: Map<String, Any>,
  val shouldTerminate: Boolean,
  val terminationReason: String?,
  val error: Throwable?
)
```

## Strategy Selection

Strategies are selected via the `ProcessingStrategyType` enum:

```kotlin
enum class ProcessingStrategyType {
  DefaultSummarizer,
  FactChecking,
  JobMatching,
  SchemaExtraction,
  DataTableAccumulation
}
```

## Common Features

### Early Termination

All strategies support early termination based on:

- Target achievement (e.g., finding N job matches)
- Confidence thresholds
- Evidence sufficiency
- Custom strategy-specific conditions

### Link Extraction

Intelligent link prioritization based on:

- Relevance scoring
- Content analysis
- Strategy-specific criteria

### Progress Tracking

- Real-time transcript updates
- Processing statistics
- Error handling and reporting

### Output Formats

- Markdown reports
- JSON data exports
- CSV tables
- Structured metadata

## Usage Examples

### Basic Crawling with Summarization

```kotlin
val strategy = DefaultSummarizerStrategy()
val context = ProcessingContext(
  executionConfig = config,
  typeConfig = typeConfig,
  orchestrationConfig = orchestrationConfig,
  task = task,
  webSearchDir = File("output")
)

val result = strategy.processPage(url, content, context)
```

### Job Search Automation

```kotlin
val strategy = JobMatchingStrategy()
val config = JobMatchingConfig(
  user_experience = resumeText,
  target_roles = listOf("Senior Engineer", "Tech Lead"),
  required_skills = listOf("Kotlin", "AWS", "Kubernetes"),
  preferred_locations = listOf("Remote", "San Francisco"),
  min_match_score = 0.7,
  target_matches = 5,
  work_arrangement_preference = "remote",
  min_salary = 150000
)

// Strategy will automatically:
// 1. Detect job postings
// 2. Analyze compatibility
// 3. Generate cover letters
// 4. Save detailed reports
// 5. Terminate after finding 5 good matches
```

### Data Extraction

```kotlin
val strategy = SchemaExtractionStrategy()
val config = SchemaExtractionConfig(
  schema_definition = """
    {
      "title": "string",
      "price": "number",
      "features": ["string"]
    }
  """,
  aggregate_results = true,
  deduplicate = true
)

// Extracts structured data matching schema
// Outputs aggregated JSON file
```

### Fact Verification

```kotlin
val strategy = FactCheckingStrategy()
val config = FactCheckingConfig(
  claims_to_verify = listOf(
    "The company was founded in 2010",
    "The product has 1M+ users"
  ),
  required_sources = 3,
  confidence_threshold = 0.8
)

// Collects evidence from multiple sources
// Terminates when sufficient evidence found
```

## Error Handling

All strategies implement robust error handling:

```kotlin
try {
  val result = strategy.processPage(url, content, context)
  if (result.error != null) {
    // Handle processing error
  }
} catch (e: Exception) {
  // Handle critical failure
}
```

## Configuration Validation

Each strategy validates its configuration:

```kotlin
val error = strategy.validateConfig(config)
if (error != null) {
  throw IllegalArgumentException(error)
}
```

## Best Practices

1. **Choose the Right Strategy**

- Use `DefaultSummarizer` for general content analysis
- Use `JobMatching` for recruitment automation
- Use `SchemaExtraction` for structured data mining
- Use `FactChecking` for claim verification
- Use `DataTableAccumulation` for comparative datasets

2. **Configure Appropriately**

- Set realistic confidence thresholds
- Define clear extraction criteria
- Use deduplication for large datasets
- Set page limits to control costs

3. **Monitor Progress**

- Check transcript streams for real-time updates
- Review metadata for extraction statistics
- Handle early termination gracefully

4. **Handle Errors**

- Implement retry logic for transient failures
- Log errors for debugging
- Validate configurations before execution

5. **Optimize Performance**

- Use appropriate page limits
- Enable deduplication when needed
- Set confidence thresholds to filter noise
- Leverage early termination

## Output Structure

```
output/
├── transcript.md              # Real-time processing log
├── final_report.md           # Final summary
├── aggregated_data.json      # Extracted data (SchemaExtraction)
├── data_table.csv            # Tabular data (DataTableAccumulation)
└── job_matches/              # Job reports (JobMatching)
    ├── Company_Position_timestamp.md
    └── ...
```

## Dependencies

- AI/LLM integration via `ChatInterface`
- JSON parsing via Jackson
- Markdown generation
- Concurrent processing support

## Thread Safety

All strategies use thread-safe data structures:

- `ConcurrentHashMap` for shared state
- `AtomicInteger` for counters
- Synchronized file I/O

```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/SchemaExtractionStrategy.kt

```
package com.simiacryptus.cognotik.crawl.processing

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class SchemaExtractionStrategy : DefaultSummarizerStrategy() {

    data class SchemaExtractionConfig(
        @Description("JSON schema definition describing the data structure to extract from each page")
        val schema_definition: String = "{}",
        @Description("Human-readable description of what data to extract")
        val extraction_instructions: String = "",
        @Description("Whether to aggregate all extracted data into a single JSON array")
        val aggregate_results: Boolean = true,
        @Description("Minimum confidence score (0.0-1.0) for extracted data to be included")
        val min_confidence: Double = 0.7,
        @Description("Maximum number of items to extract per page (null for unlimited)")
        val max_items_per_page: Int? = null,
        @Description("Whether to validate extracted data against the schema")
        val validate_schema: Boolean = true,
        @Description("Whether to deduplicate extracted items based on key fields")
        val deduplicate: Boolean = true,
        @Description("Field names to use as unique keys for deduplication (comma-separated)")
        val deduplication_keys: String? = null
    )

    override val description: String
        get() = "Extracts structured data from web pages according to a user-defined schema and aggregates results into a comprehensive JSON dataset."

    data class ExtractedData(
        @Description("The extracted data matching the schema")
        val data: Any? = null,
        @Description("Confidence score for the extraction (0.0-1.0)")
        val confidence: Double = 1.0,
        @Description("Additional metadata about the extraction")
        val metadata: Map<String, Any> = emptyMap(),
        @Description("Any validation errors or warnings")
        val validation_notes: List<String> = emptyList()
    )

    private val extractedDataStore = ConcurrentHashMap<String, MutableList<Map<String, Any>>>()
    private val seenKeys = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private val log = LoggerFactory.getLogger(SchemaExtractionStrategy::class.java)
    }

    override fun processPage(
        url: String,
        content: String,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.PageProcessingResult {
        log.debug("Processing page with schema extraction: $url")
        val config = try {
            context.executionConfig.content_queries?.let { queries ->
                queries.parserCast<SchemaExtractionConfig>(
                    context.orchestrationConfig.defaultFast.getChildClient(
                        context.task
                    )
                )
            } ?: run {
                log.warn("No schema extraction config provided, using default")
                SchemaExtractionConfig()
            }
        } catch (e: Exception) {
            log.error("Failed to parse schema extraction config", e)
            return PageProcessingStrategy.PageProcessingResult(
                url = url,
                pageType = CrawlerAgentTask.PageType.Error,
                content = "Configuration error: ${e.message}",
                error = e
            )
        }

        // Extract data using the schema
        val extractionResult = try {
            extractSchemaData(url, content, config, context)
        } catch (e: Exception) {
            log.error("Failed to extract schema data from: $url", e)
            return PageProcessingStrategy.PageProcessingResult(
                url = url,
                pageType = CrawlerAgentTask.PageType.Error,
                content = "Extraction error: ${e.message}",
                error = e
            )
        }

        // Filter by confidence
        val filteredData = extractionResult.data?.let { data ->
            if (extractionResult.confidence >= config.min_confidence) {
                data
            } else {
                log.debug("Extracted data below confidence threshold: ${extractionResult.confidence} < ${config.min_confidence}")
                null
            }
        }

        // Store extracted data
        if (filteredData != null) {
            storeExtractedData(url, filteredData, config)
        }

        // Generate summary of extraction
        val summary = buildString {
            appendLine("## Data Extraction Results")
            appendLine()
            appendLine("**URL:** [$url]($url)")
            appendLine("**Confidence:** ${(extractionResult.confidence * 100).toInt()}%")
            appendLine()

            if (extractionResult.validation_notes.isNotEmpty()) {
                appendLine("### Validation Notes")
                extractionResult.validation_notes.forEach { note ->
                    appendLine("- $note")
                }
                appendLine()
            }

            if (filteredData != null) {
                appendLine("### Extracted Data")
                appendLine()
                appendLine("```json")
                appendLine(filteredData.toJson())
                appendLine("```")
                appendLine()

                val itemCount = when (filteredData) {
                    is List<*> -> filteredData.size
                    is Map<*, *> -> 1
                    else -> 1
                }
                appendLine("**Items Extracted:** $itemCount")
                appendLine("**Total Items in Store:** ${extractedDataStore.values.sumOf { it.size }}")
            } else {
                appendLine("*No data met the confidence threshold*")
            }
            appendLine()
        }

        // Also get standard link extraction
        val standardResult = super.processPage(url, content, context)

        return PageProcessingStrategy.PageProcessingResult(
            url = url,
            pageType = if (filteredData != null) CrawlerAgentTask.PageType.OK else CrawlerAgentTask.PageType.Irrelevant,
            content = summary,
            extractedLinks = standardResult.extractedLinks,
            metadata = mapOf(
                "extracted_data" to (filteredData ?: emptyMap<String, Any>()),
                "confidence" to extractionResult.confidence,
                "validation_notes" to extractionResult.validation_notes,
                "total_items" to extractedDataStore.values.sumOf { it.size }
            )
        )
    }

    private fun extractSchemaData(
        url: String,
        content: String,
        config: SchemaExtractionConfig,
        context: PageProcessingStrategy.ProcessingContext
    ): ExtractedData {
        log.debug("Extracting schema data from: $url")

        val prompt = buildString {
            appendLine("Extract structured data from the following web page content according to the schema provided.")
            appendLine()
            appendLine("SCHEMA DEFINITION:")
            appendLine(config.schema_definition)
            appendLine()
            appendLine("EXTRACTION INSTRUCTIONS:")
            appendLine(config.extraction_instructions.ifBlank { "Extract all data matching the schema" })
            appendLine()
            if (config.max_items_per_page != null) {
                appendLine("Extract up to ${config.max_items_per_page} items from this page.")
                appendLine()
            }
            appendLine("Provide:")
            appendLine("1. The extracted data matching the schema (as 'data' field)")
            appendLine("2. A confidence score (0.0-1.0) indicating extraction quality")
            appendLine("3. Any validation notes or warnings")
            appendLine()
            appendLine("If multiple items match the schema, return them as a list in the 'data' field.")
            appendLine("If no data matches the schema, return null for 'data' with an explanation in validation_notes.")
        }

        val model = (context.typeConfig.model?.let { context.orchestrationConfig.instance(it) }
            ?: context.orchestrationConfig.defaultFast).getChildClient(context.task)

        return ParsedAgent(
            prompt = prompt,
            resultClass = ExtractedData::class.java,
            model = model,
            parsingChatter = model
        ).answer(listOf(content.take(50000))).obj // Limit content size for processing
    }

    private fun storeExtractedData(
        url: String,
        data: Any,
        config: SchemaExtractionConfig
    ) {
        val items = when (data) {
            is List<*> -> data.filterIsInstance<Map<String, Any>>()
            is Map<*, *> -> listOf(data as Map<String, Any>)
            else -> {
                log.warn("Unexpected data type: ${data.javaClass.name}")
                return
            }
        }

        val deduplicationKeys = config.deduplication_keys?.split(",")?.map { it.trim() } ?: emptyList()

        items.forEach { item ->
            // Check for duplicates if enabled
            if (config.deduplicate && deduplicationKeys.isNotEmpty()) {
                val key = deduplicationKeys.mapNotNull { keyField ->
                    item[keyField]?.toString()
                }.joinToString("|")

                if (key.isNotBlank()) {
                    if (seenKeys.contains(key)) {
                        log.debug("Skipping duplicate item with key: $key")
                        return@forEach
                    }
                    seenKeys.add(key)
                }
            }

            // Store the item
            extractedDataStore.computeIfAbsent(url) { mutableListOf() }.add(item)
        }

        log.info("Stored ${items.size} items from: $url (total: ${extractedDataStore.values.sumOf { it.size }})")
    }

    override fun shouldContinueCrawling(
        currentResults: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): PageProcessingStrategy.ContinuationDecision {
        val totalItems = extractedDataStore.values.sumOf { it.size }
        val successfulExtractions = currentResults.count {
            it.metadata["extracted_data"] != null && it.metadata["extracted_data"] != emptyMap<String, Any>()
        }

        log.debug("Crawling status: $successfulExtractions successful extractions, $totalItems total items")

        return PageProcessingStrategy.ContinuationDecision(
            shouldContinue = context.processedCount.get() < context.maxPages,
            reason = "Extracted $totalItems items from $successfulExtractions pages so far"
        )
    }

    override fun generateFinalOutput(
        results: List<PageProcessingStrategy.PageProcessingResult>,
        context: PageProcessingStrategy.ProcessingContext
    ): String {
        log.info("Generating final aggregated output")
        val config = try {
            val chatInterface = context.orchestrationConfig.defaultFast.getChildClient(context.task)
            context.executionConfig.content_queries?.parserCast<SchemaExtractionConfig>(chatInterface)
                ?: SchemaExtractionConfig()
        } catch (e: Exception) {
            log.error("Failed to parse config for final output", e)
            SchemaExtractionConfig()
        }

        // Aggregate all extracted data
        val allData = if (config.aggregate_results) {
            extractedDataStore.values.flatten()
        } else {
            extractedDataStore.map { (url, items) ->
                mapOf(
                    "source_url" to url,
                    "items" to items,
                    "count" to items.size
                )
            }
        }

        // Save aggregated JSON
        saveAggregatedJson(allData, context)

        // Generate summary report
        return buildString {
            appendLine("# Schema Extraction Results")
            appendLine()
            appendLine(
                "**Extraction Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }"
            )
            appendLine()

            appendLine("## Summary Statistics")
            appendLine()
            appendLine("- **Total Pages Processed:** ${results.size}")
            appendLine("- **Successful Extractions:** ${extractedDataStore.size}")
            appendLine("- **Total Items Extracted:** ${allData.size}")
            appendLine("- **Unique Sources:** ${extractedDataStore.keys.size}")
            if (config.deduplicate) {
                appendLine("- **Deduplication:** Enabled")
                appendLine("- **Unique Items After Deduplication:** ${allData.size}")
            }
            appendLine()

            appendLine("## Schema Definition")
            appendLine()
            appendLine("```json")
            appendLine(config.schema_definition)
            appendLine("```")
            appendLine()

            appendLine("## Extraction Statistics by Source")
            appendLine()
            appendLine("| Source | Items Extracted | Confidence |")
            appendLine("|--------|-----------------|------------|")

            results.filter { it.metadata.containsKey("extracted_data") }.forEach { result ->
                val itemCount = when (val data = result.metadata["extracted_data"]) {
                    is List<*> -> data.size
                    is Map<*, *> -> if (data.isEmpty()) 0 else 1
                    else -> 0
                }
                val confidence = result.metadata["confidence"] as? Double ?: 0.0
                val shortUrl = result.url.take(50) + if (result.url.length > 50) "..." else ""
                appendLine("| [$shortUrl](${result.url}) | $itemCount | ${(confidence * 100).toInt()}% |")
            }
            appendLine()

            appendLine("## Aggregated Data")
            appendLine()
            appendLine("The complete extracted dataset has been saved to:")
            appendLine("- `${context.webSearchDir.name}/aggregated_data.json`")
            appendLine()

            appendLine("### Sample Data (First 3 Items)")
            appendLine()
            appendLine("```json")
            appendLine(allData.take(3).toJson())
            appendLine("```")
            appendLine()

            if (allData.size > 3) {
                appendLine("*... and ${allData.size - 3} more items*")
                appendLine()
            }

            appendLine("## Data Quality Notes")
            appendLine()
            val allValidationNotes = results.flatMap {
                (it.metadata["validation_notes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            }.distinct()

            if (allValidationNotes.isEmpty()) {
                appendLine("✅ No validation issues detected")
            } else {
                allValidationNotes.forEach { note ->
                    appendLine("- ⚠️ $note")
                }
            }
            appendLine()

            appendLine("## Next Steps")
            appendLine()
            appendLine("1. Review the aggregated JSON file for completeness")
            appendLine("2. Validate data quality and schema compliance")
            appendLine("3. Import data into your target system")
            appendLine("4. Consider additional crawling if coverage is incomplete")
        }
    }

    private fun saveAggregatedJson(
        data: Any,
        context: PageProcessingStrategy.ProcessingContext
    ) {
        try {
            val jsonFile = File(context.webSearchDir, "aggregated_data.json")
            val jsonContent = data.toJson()
            jsonFile.writeText(jsonContent, StandardCharsets.UTF_8)
            log.info("Saved aggregated JSON to: ${jsonFile.absolutePath} (${jsonContent.length} bytes)")

            // Also save a pretty-printed version
            val prettyFile = File(context.webSearchDir, "aggregated_data_pretty.json")
            val prettyJson = ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data)
            prettyFile.writeText(prettyJson, StandardCharsets.UTF_8)

            // Save metadata
            val metadataFile = File(context.webSearchDir, "extraction_metadata.json")
            val metadata = mapOf(
                "timestamp" to LocalDateTime.now().toString(),
                "total_items" to when (data) {
                    is List<*> -> data.size
                    else -> 1
                },
                "sources" to extractedDataStore.keys.toList(),
                "schema" to (context.executionConfig.content_queries?.toString() ?: "")
            )
            metadataFile.writeText(metadata.toJson(), StandardCharsets.UTF_8)

        } catch (e: Exception) {
            log.error("Failed to save aggregated JSON", e)
        }
    }

    override fun validateConfig(config: Any?): String? {
        if (config == null) return "Schema extraction config is required"

        return try {
            val schemaConfig = when (config) {
                is SchemaExtractionConfig -> config
                is String -> ObjectMapper().readValue(config, SchemaExtractionConfig::class.java)
                else -> return "Invalid config type: ${config.javaClass.name}"
            }

            if (schemaConfig.schema_definition.isBlank()) {
                return "schema_definition is required and cannot be blank"
            }

            if (schemaConfig.min_confidence < 0.0 || schemaConfig.min_confidence > 1.0) {
                return "min_confidence must be between 0.0 and 1.0"
            }

            if (schemaConfig.max_items_per_page != null && schemaConfig.max_items_per_page <= 0) {
                return "max_items_per_page must be greater than 0"
            }

            null
        } catch (e: Exception) {
            "Config validation error: ${e.message}"
        }
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

Here is the updated `README.md` for the `com.simiacryptus.cognotik.crawl.processing` package. The documentation has been refined to more accurately reflect the configuration options available in the strategy implementations, particularly for the `JobMatchingStrategy`.

### webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md
```diff
--- webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md
+++ webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md
@@ -101,11 +101,15 @@
   "target_matches": 10,
   "work_arrangement_preference": "remote",
   "max_days_in_office": 2,
   "min_salary": 120000,
   "target_salary": 150000,
   "max_salary": 180000,
   "salary_currency": "USD",
-  "willing_to_relocate": false
+  "willing_to_relocate": false,
+  "requires_relocation_assistance": false,
+  "travel_willingness": "occasional",
+  "max_travel_percentage": 20,
+  "adaptive_threshold": false
 }
 ```
 
```

### Summary of Changes

- **JobMatchingStrategy Documentation**: Updated the JSON configuration example to include additional fields found in the `JobMatchingConfig` data class, such as `requires_relocation_assistance`, `travel_willingness`, `max_travel_percentage`, and `adaptive_threshold`.
- **Consistency Check**: Verified that all strategies (`DefaultSummarizer`, `FactChecking`, `JobMatching`, `SchemaExtraction`, and `DataTableAccumulation`) are correctly described according to their respective Kotlin implementations.
- **Structure Preservation**: Maintained the existing structure of the README, including the Overview, Core Components, Architecture, and Usage Examples sections, ensuring it remains a comprehensive guide for the package.
- **Technical Accuracy**: Ensured that the `ProcessingContext`, `PageProcessingResult`, and `ProcessingStrategyType` definitions in the documentation match the provided source code.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/crawl/processing/README.md</a> Updated
