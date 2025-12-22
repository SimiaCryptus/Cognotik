package com.simiacryptus.cognotik.plan.tools.online.processing

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
            val chatInterface = context.orchestrationConfig.parsingChatter.getChildClient(context.task)
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
            ?: context.orchestrationConfig.parsingChatter).getChildClient(context.task)

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
                queries.parserCast<DataTableConfig>(context.orchestrationConfig.parsingChatter.getChildClient(context.task))
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