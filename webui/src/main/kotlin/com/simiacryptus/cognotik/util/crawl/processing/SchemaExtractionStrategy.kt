package com.simiacryptus.cognotik.util.crawl.processing

import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.parserCast
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
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

        val model = (context.typeConfig.model?.let { it.instance() }
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