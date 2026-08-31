package com.simiacryptus.cognotik.plan.tools.data

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.log2

class EntropyReductionTreeTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: EntropyReductionTreeTaskExecutionConfigData?
) : AbstractTask<EntropyReductionTreeTask.EntropyReductionTreeTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {
  class EntropyReductionTreeTaskExecutionConfigData(
    @Description("The data file to analyze (CSV or JSONL)")
    var data_file: String? = null,
    @Description("Fields to include in entropy analysis. If empty, all fields are used.")
    var selected_fields: List<String>? = null,
    @Description("Fields to ignore/exclude from entropy analysis.")
    var ignored_fields: List<String>? = null,
    @Description("Maximum depth of the entropy reduction tree. Must be between 1 and 10.")
    var max_depth: Int = 3,
    @Description("Number of candidate splitting rules the LLM should propose per tree node. Must be between 2 and 20.")
    var candidate_rules: Int = 5,
    @Description("Number of top values to show in descriptive statistics per field.")
    var top_n_values: Int = 5,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = EntropyReductionTree.name,
    task_description = "Build an entropy reduction tree for data in '$data_file'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (data_file.isNullOrBlank()) return "data_file is required"
      max_depth = max_depth.coerceIn(1, 10)
      candidate_rules = candidate_rules.coerceIn(2, 20)
      top_n_values = top_n_values.coerceIn(1, 20)
      return null
    }
  }

  override fun promptSegment(): String = buildString {
    appendLine("EntropyReductionTree - Build an LLM-driven tree that reduces overall data entropy")
    appendLine("  ** Specify the data file (CSV or JSONL)")
    appendLine("  ** Optionally specify fields to include or ignore")
    appendLine("  ** No single target column — reduces entropy across all selected fields")
    appendLine("  ** Provides descriptive statistics per field at each node")
    appendLine("  ** Uses LLM to propose semantic splitting rules")
    appendLine("  ** LLM can terminate branches with natural language descriptions of data subsets")
    appendLine("  ** Validates rules using weighted multi-field entropy reduction")
    appendLine("  ** Generates a JSON output with tree structure, splitting rules, leaf descriptions, and detailed statistics")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())
    try {
      val config = executionConfig ?: run {
        val msg = "No execution configuration provided"
        task.error(Exception(msg))
        log.error(msg)
        transcript?.write("## Error\n$msg\n".toByteArray())
        resultFn("Error: $msg")
        task.complete()
        return
      }
      val error = config.validate()
      if (error != null) {
        val msg = "Configuration Error: $error"
        task.error(Exception(msg))
        log.error(msg)
        transcript?.write("## Error\n$msg\n".toByteArray())
        resultFn("Error: $msg")
        task.complete()
        return
      }
      val tabs = TabbedDisplay(task)
      val resultTab = tabs.newTask("Results")
      val executionTask = tabs.newTask("Work Details")
      val statsTab = tabs.newTask("Statistics")
      executionTask.header("Building Entropy Reduction Tree", level = 2)
      val statusBuffer = executionTask.add("Initializing...".renderMarkdown())
      task.pool.submit {
        try {
          log.info("Starting EntropyReductionTreeTask for ${config.data_file}")
          writeTranscriptHeader(transcript, config)
          val dataFile = root.resolve(config.data_file!!).toFile()
          if (!dataFile.exists()) {
            val msg = "Data file not found: ${config.data_file}"
            val ex = Exception(msg)
            executionTask.error(ex)
            log.error(msg)
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${ex.stackTraceToString()}\n```\n</details>\n".toByteArray())
            resultFn("Error: $msg")
            return@submit
          }
          val records = try {
            if (dataFile.extension.equals("jsonl", ignoreCase = true)) {
              loadJsonl(dataFile)
            } else {
              loadCsv(dataFile)
            }
          } catch (e: Exception) {
            executionTask.error(e)
            log.error("Error loading data", e)
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
            resultFn("Error loading data: ${e.message}")
            return@submit
          }
          if (records.isEmpty()) {
            val msg = "Data file is empty"
            executionTask.add(msg.renderMarkdown())
            log.warn(msg)
            transcript?.write("\n## Warning\n$msg\n".toByteArray())
            resultFn("Error: $msg")
            return@submit
          }
          val allHeaders = records.first().keys.toList()
          val ignoredSet = (config.ignored_fields ?: emptyList()).toSet()
          val analysisFields = if (!config.selected_fields.isNullOrEmpty()) {
            config.selected_fields!!.filter { it !in ignoredSet && it in allHeaders }
          } else {
            allHeaders.filter { it !in ignoredSet }
          }
          if (analysisFields.isEmpty()) {
            val msg = "No analysis fields remaining after applying selected/ignored filters. Available: $allHeaders"
            executionTask.add(msg.renderMarkdown())
            log.error(msg)
            transcript?.write("\n## Error\n$msg\n".toByteArray())
            resultFn("Error: $msg")
            return@submit
          }
          statusBuffer?.setLength(0)
          statusBuffer?.append("Loaded ${records.size} records with ${analysisFields.size} analysis fields. Analyzing...".renderMarkdown())
          executionTask.update()
// Show initial statistics
          val initialStats = computeFieldStatistics(records, analysisFields, config.top_n_values)
          val initialEntropy = computeMultiFieldEntropy(records, analysisFields)
          statsTab.header("Initial Data Statistics", level = 2)
          statsTab.add(formatStatistics(initialStats, initialEntropy, records.size).renderMarkdown())
          transcript?.write(
            "\n## Initial Statistics\n${
              formatStatisticsPlain(
                initialStats,
                initialEntropy,
                records.size
              )
            }\n".toByteArray()
          )
          val smartApi = orchestrationConfig.defaultSmart.getChildClient(task)
          val chatAgent = ChatAgent(
            model = smartApi,
            temperature = 0.2,
            prompt = buildString {
              appendLine("You are a Data Entropy Reduction Rule Generator.")
              appendLine("Your goal is to propose splitting rules that partition data into more homogeneous subsets.")
              appendLine(
                "The goal is NOT to predict a single target — instead, reduce overall entropy across ALL of these fields: ${
                  analysisFields.joinToString(
                    ", "
                  )
                }"
              )
              appendLine()
              appendLine("Output Format:")
              appendLine("Provide exactly ${config.candidate_rules} distinct rules.")
              appendLine("Each rule must be in the format: `FIELD OPERATOR VALUE`")
              appendLine()
              appendLine("Supported Operators:")
              appendLine("- `==` (Exact match)")
              appendLine("- `!=` (Not equal)")
              appendLine("- `>` (Numeric greater than)")
              appendLine("- `<` (Numeric less than)")
              appendLine("- `contains` (String contains)")
              appendLine("- `matches` (Regex match)")
              appendLine()
              appendLine("Examples:")
              appendLine("age > 25")
              appendLine("status == active")
              appendLine("description contains error")
              appendLine()
              appendLine("You may use ANY field from the data (including fields not in the analysis set) as the splitting field.")
              appendLine("Do not include explanations. Just the rules, one per line.")
            }
          )
          tabs["Configuration"] = buildString {
            appendLine("### Parameters")
            appendLine("* **Data File:** `${config.data_file}`")
            appendLine("* **Records:** ${records.size}")
            appendLine("* **Analysis Fields:** ${analysisFields.joinToString(", ") { "`$it`" }}")
            appendLine(
              "* **Ignored Fields:** ${
                (config.ignored_fields ?: emptyList()).joinToString(", ") { "`$it`" }.ifEmpty { "none" }
              }"
            )
            appendLine("* **Max Depth:** `${config.max_depth}`")
            appendLine("* **Candidate Rules:** `${config.candidate_rules}`")
            appendLine("* **Top N Values:** `${config.top_n_values}`")
            appendLine("* **Initial MDL Entropy (bits/rec):** ${"%.4f".format(initialEntropy)}")
          }.renderMarkdown()
          val tree = buildTree(
            records,
            analysisFields,
            allHeaders,
            0,
            config.max_depth,
            config.candidate_rules,
            config.top_n_values,
            chatAgent,
            executionTask,
            statsTab,
            transcript
          )
          val jsonOutput = generateJsonOutput(tree, analysisFields, initialEntropy, records.size, config)
          val jsonString = jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput)
          statusBuffer?.setLength(0)
          statusBuffer?.append("Tree construction complete.".renderMarkdown())
          executionTask.update()
          resultTab.header("Entropy Reduction Tree Results", level = 2)
          resultTab.add("```json\n$jsonString\n```".renderMarkdown())
          val outputBaseName = getOutputFile(".json")?.let {
            if (it.endsWith(".json")) it.removeSuffix(".json") else null
          }
          val jsonFileName = if (outputBaseName != null) {
            "${outputBaseName}.json"
          } else {
            "EntropyReductionTree.json"
          }
          val fileUrl = task.saveFile(jsonFileName, jsonString.toByteArray())
          resultTab.add("Download: <a href='$fileUrl'>$jsonFileName</a>".renderMarkdown())
// Tree summary
          val treeSummary = summarizeTree(tree)
          resultTab.add(buildString {
            appendLine("### Tree Summary")
            appendLine("* **Total Leaves:** ${treeSummary.leafCount}")
            appendLine("* **Max Depth Reached:** ${treeSummary.maxDepth}")
            appendLine("* **Initial Entropy:** ${"%.4f".format(initialEntropy)}")
            appendLine("* **Weighted Leaf Entropy:** ${"%.4f".format(treeSummary.weightedEntropy)}")
            appendLine("* **Entropy Reduction:** ${"%.4f".format(initialEntropy - treeSummary.weightedEntropy)}")
          }.renderMarkdown())
          transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
          transcript?.write("## Entropy Reduction Tree Results\n\n".toByteArray())
          transcript?.write("<details><summary>Generated JSON</summary>\n\n```json\n$jsonString\n```\n</details>\n\n".toByteArray())
          transcript?.write("</div>\n\n".toByteArray())
          log.info("EntropyReductionTreeTask completed successfully.")
          resultFn(buildString {
            appendLine("## Entropy Reduction Tree Generated")
            appendLine("* Analysis Fields: ${analysisFields.joinToString(", ") { "`$it`" }}")
            appendLine("* Max Depth: `${config.max_depth}`")
            appendLine("* Records: ${records.size}")
            appendLine("* Leaves: ${treeSummary.leafCount}")
            appendLine("* Entropy Reduction: ${"%.4f".format(initialEntropy - treeSummary.weightedEntropy)}")
            appendLine("* JSON saved to: `$jsonFileName`")
          })
        } catch (e: Exception) {
          executionTask.error(e)
          log.error("Error in EntropyReductionTreeTask execution", e)
          transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
          resultFn("Error: ${e.message}")
        } finally {
          executionTask.complete()
          resultTab.complete()
          statsTab.complete()
          task.complete()
        }
      }
    } catch (e: Exception) {
      task.error(e)
      log.error("Error initializing EntropyReductionTreeTask", e)
      transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
      resultFn("Error: ${e.message}")
      task.complete()
    }
  }

  private fun writeTranscriptHeader(transcript: OutputStream?, config: EntropyReductionTreeTaskExecutionConfigData) {
    transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
    transcript?.write(buildString {
      appendLine("## Entropy Reduction Tree Construction")
      appendLine()
      appendLine("* **Data File:** `${config.data_file}`")
      appendLine("* **Selected Fields:** ${config.selected_fields?.joinToString(", ") ?: "all"}")
      appendLine("* **Ignored Fields:** ${config.ignored_fields?.joinToString(", ") ?: "none"}")
      appendLine("* **Max Depth:** ${config.max_depth}")
      appendLine("* **Candidate Rules:** ${config.candidate_rules}")
      appendLine("* **Top N Values:** ${config.top_n_values}")
      appendLine()
    }.toByteArray())
  }

  // --- Data Loading ---
  private fun loadCsv(file: File): List<Map<String, String>> {
    val lines = file.readLines()
    if (lines.isEmpty()) return emptyList()
    val headers = lines.first().split(",").map { it.trim() }
    return lines.drop(1).mapNotNull { line ->
      val values = line.split(",").map { it.trim() }
      if (values.size == headers.size) {
        headers.zip(values).toMap()
      } else null
    }
  }

  private fun loadJsonl(file: File): List<Map<String, String>> {
    return file.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
      try {
        line.trim().removeSurrounding("{", "}").split(",").associate {
          val (k, v) = it.split(":", limit = 2)
          k.trim().removeSurrounding("\"") to v.trim().removeSurrounding("\"")
        }
      } catch (e: Exception) {
        log.warn("Failed to parse JSONL line: ${e.message}")
        null
      }
    }
  }

  // --- Statistics ---
  data class FieldStats(
    val fieldName: String,
    val distinctCount: Int,
    val topValues: List<Pair<String, Int>>,
    val entropy: Double,
    val nullCount: Int,
    val isNumeric: Boolean,
    val numericMin: Double?,
    val numericMax: Double?,
    val numericMean: Double?,
    val encodingStrategy: String = "unknown"
  )

  private fun computeFieldStatistics(
    data: List<Map<String, String>>,
    fields: List<String>,
    topN: Int
  ): List<FieldStats> {
    return fields.map { field ->
      val values = data.map { it[field] ?: "" }
      val nonNull = values.filter { it.isNotBlank() }
      val nullCount = values.size - nonNull.size
      val counts = nonNull.groupingBy { it }.eachCount()
      val topValues = counts.entries.sortedByDescending { it.value }.take(topN).map { it.key to it.value }
      val distinctCount = counts.size
      val fieldEntropy = mdlEntropy(data, field)
      val numericValues = nonNull.mapNotNull { it.toDoubleOrNull() }
      val isNumeric = numericValues.size > nonNull.size / 2
      val encodingStrategy = bestEncodingStrategy(data, field)
      FieldStats(
        fieldName = field,
        distinctCount = distinctCount,
        topValues = topValues,
        entropy = fieldEntropy,
        nullCount = nullCount,
        isNumeric = isNumeric,
        numericMin = if (isNumeric && numericValues.isNotEmpty()) numericValues.min() else null,
        numericMax = if (isNumeric && numericValues.isNotEmpty()) numericValues.max() else null,
        numericMean = if (isNumeric && numericValues.isNotEmpty()) numericValues.average() else null,
        encodingStrategy = encodingStrategy
      )
    }
  }

  private fun formatStatistics(stats: List<FieldStats>, multiFieldEntropy: Double, totalRecords: Int): String =
    buildString {
      appendLine("| Field | Distinct | MDL Entropy (bits/rec) | Encoding | Null | Top Values |")
      appendLine("|-------|----------|------------------------|----------|------|------------|")
      for (s in stats) {
        val topStr = s.topValues.joinToString("; ") { "`${it.first}` (${it.second})" }
        val numericInfo =
          if (s.isNumeric) " [min=${"%.2f".format(s.numericMin)}, max=${"%.2f".format(s.numericMax)}, mean=${
            "%.2f".format(s.numericMean)
          }]" else ""
        appendLine("| `${s.fieldName}` | ${s.distinctCount} | ${"%.4f".format(s.entropy)} | ${s.encodingStrategy} | ${s.nullCount} | $topStr$numericInfo |")
      }
      appendLine()
      appendLine(
        "**Total Records:** $totalRecords &nbsp; | &nbsp; **Combined MDL Entropy (bits/rec):** ${
          "%.4f".format(
            multiFieldEntropy
          )
        }"
      )
    }

  private fun formatStatisticsPlain(stats: List<FieldStats>, multiFieldEntropy: Double, totalRecords: Int): String =
    buildString {
      appendLine("Total Records: $totalRecords")
      appendLine("Combined MDL Entropy (bits/rec): ${"%.4f".format(multiFieldEntropy)}")
      appendLine()
      for (s in stats) {
        appendLine("Field: ${s.fieldName}")
        appendLine("  Distinct: ${s.distinctCount}, MDL Entropy: ${"%.4f".format(s.entropy)} (${s.encodingStrategy}), Null: ${s.nullCount}")
        if (s.isNumeric) {
          appendLine(
            "  Numeric: min=${"%.2f".format(s.numericMin)}, max=${"%.2f".format(s.numericMax)}, mean=${
              "%.2f".format(
                s.numericMean
              )
            }"
          )
        }
        appendLine("  Top values: ${s.topValues.joinToString("; ") { "${it.first} (${it.second})" }}")
      }
    }
// --- Entropy ---
  /**
   * Encoding strategy result: total bits to describe all values of a field,
   * plus the name of the strategy used.
   */
  private data class EncodingResult(val totalBits: Double, val strategy: String)

  /**
   * Compute the MDL (Minimum Description Length) entropy for a single field.
   * Returns bits per record using the best of three encoding strategies:
   * 1. Ranged numeric encoding (uniform over range + precision)
   * 2. Discrete string encoding (definition table + choice entropy)
   * 3. Concatenated gzip encoding
   */
  private fun mdlEntropy(data: List<Map<String, String>>, field: String): Double {
    if (data.isEmpty()) return 0.0
    val values = data.map { it[field] ?: "" }
    val n = values.size.toDouble()
    val candidates = mutableListOf<EncodingResult>()

    // Strategy 1: Ranged numeric encoding
    candidates.add(rangedNumericEncoding(values))

    // Strategy 2: Discrete string encoding (definition table + choice)
    candidates.add(discreteStringEncoding(values))

    // Strategy 3: Concatenated gzip encoding
    candidates.add(gzipEncoding(values))

    val best = candidates.minByOrNull { it.totalBits } ?: candidates.first()
    return best.totalBits / n
  }

  /**
   * Strategy 1: Ranged numeric encoding.
   * For numeric values, encode each value as an offset within [min, max] range
   * with observed precision. Non-numeric values fall back to a very large cost.
   *
   * Total bits = overhead + n * log2(distinctLevels)
   * where distinctLevels = (max - min) / precision + 1
   * Overhead includes encoding min, max, and precision (fixed 96 bits = 3 doubles).
   */
  private fun rangedNumericEncoding(values: List<String>): EncodingResult {
    val numericValues = values.mapNotNull { it.toDoubleOrNull() }
    // If less than half are numeric, this strategy is not viable
    if (numericValues.size < values.size / 2 || numericValues.isEmpty()) {
      return EncodingResult(Double.MAX_VALUE, "numeric")
    }
    val n = values.size
    val min = numericValues.min()
    val max = numericValues.max()
    val range = max - min

    if (range == 0.0) {
      // All same value: just encode the constant (64 bits) + 1 bit flag per non-numeric
      val nonNumericCount = values.size - numericValues.size
      // For non-numeric values, we need to encode them separately
      val nonNumericCost = if (nonNumericCount > 0) {
        val nonNumericVals = values.filter { it.toDoubleOrNull() == null }
        discreteStringEncoding(nonNumericVals).totalBits
      } else 0.0
      // 64 bits for the constant + 1 bit per record (is-numeric flag) + non-numeric cost
      return EncodingResult(64.0 + n.toDouble() + nonNumericCost, "numeric")
    }

    // Determine precision from the data: smallest nonzero difference
    val sorted = numericValues.sorted()
    var minDiff = range
    for (i in 1 until sorted.size) {
      val diff = sorted[i] - sorted[i - 1]
      if (diff > 0 && diff < minDiff) minDiff = diff
    }
    // Clamp precision to avoid absurd values
    val precision = if (minDiff > 0) minDiff else 1.0

    val distinctLevels = (range / precision).toLong() + 1
    val bitsPerValue = if (distinctLevels > 1) log2(distinctLevels.toDouble()) else 0.0

    // Overhead: encode min (64), max (64), precision (64) = 192 bits
    val overhead = 192.0

    // For non-numeric values, add a flag bit per record + separate encoding
    val nonNumericCount = values.size - numericValues.size
    val flagBits = if (nonNumericCount > 0) n.toDouble() else 0.0 // 1 bit per record: is-numeric?
    val nonNumericCost = if (nonNumericCount > 0) {
      val nonNumericVals = values.filter { it.toDoubleOrNull() == null }
      discreteStringEncoding(nonNumericVals).totalBits
    } else 0.0

    val totalBits = overhead + flagBits + numericValues.size * bitsPerValue + nonNumericCost
    return EncodingResult(totalBits, "numeric")
  }

  /**
   * Strategy 2: Discrete string encoding.
   * Build a definition table of unique values, then encode each record as a choice index.
   *
   * Table cost = sum of (8 * length_of_each_unique_value) bits (UTF-8 approximation)
   *            + log2(n) bits per entry for the count/index overhead
   * Choice cost = n * log2(distinctCount) if uniform, or actual Shannon entropy * n
   *
   * We use Shannon entropy for the choice cost (optimal prefix code).
   */
  private fun discreteStringEncoding(values: List<String>): EncodingResult {
    if (values.isEmpty()) return EncodingResult(0.0, "discrete")
    val n = values.size.toDouble()
    val counts = values.groupingBy { it }.eachCount()
    val distinctCount = counts.size

    if (distinctCount == 0) return EncodingResult(0.0, "discrete")

    // Table cost: for each unique value, encode its bytes + a length prefix
    // Length prefix: log2(maxLen+1) bits per entry, value: 8 bits per byte
    val maxLen = counts.keys.maxOfOrNull { it.length } ?: 0
    val lengthPrefixBits = if (maxLen > 0) log2((maxLen + 1).toDouble()) else 1.0
    val tableCost = counts.keys.sumOf { key ->
      lengthPrefixBits + key.toByteArray(Charsets.UTF_8).size * 8.0
    }

    // Choice cost: Shannon entropy * n (optimal prefix code lower bound)
    val shannonEntropy = if (distinctCount == 1) 0.0 else {
      -counts.values.sumOf { count ->
        val p = count / n
        if (p > 0.0) p * log2(p) else 0.0
      }
    }
    val choiceCost = shannonEntropy * n

    // Also need to encode the table size (log2(n) bits)
    val tableSizeOverhead = if (n > 0) log2(n + 1.0) * 2 else 0.0 // encode distinctCount and n

    val totalBits = tableSizeOverhead + tableCost + choiceCost
    return EncodingResult(totalBits, "discrete")
  }

  /**
   * Strategy 3: Concatenated gzip encoding.
   * Concatenate all values (with a delimiter) and gzip compress.
   * Total bits = compressed size in bits.
   * This captures any patterns, repetitions, or structure in the data.
   */
  private fun gzipEncoding(values: List<String>): EncodingResult {
    if (values.isEmpty()) return EncodingResult(0.0, "gzip")
    return try {
      val concatenated = values.joinToString("\n")
      val bytes = concatenated.toByteArray(Charsets.UTF_8)
      val baos = ByteArrayOutputStream()
      GZIPOutputStream(baos).use { gzip ->
        gzip.write(bytes)
      }
      val compressedBits = baos.size() * 8.0
      EncodingResult(compressedBits, "gzip")
    } catch (e: Exception) {
      log.warn("Gzip encoding failed: ${e.message}")
      EncodingResult(Double.MAX_VALUE, "gzip")
    }
  }

  /**
   * Compute the best encoding strategy name for a field (for display purposes).
   */
  private fun bestEncodingStrategy(data: List<Map<String, String>>, field: String): String {
    if (data.isEmpty()) return "none"
    val values = data.map { it[field] ?: "" }
    val candidates = listOf(
      rangedNumericEncoding(values),
      discreteStringEncoding(values),
      gzipEncoding(values)
    )
    return candidates.minByOrNull { it.totalBits }?.strategy ?: "unknown"
  }

  /**
   * Compute the average MDL entropy (bits per record) across all analysis fields (equally weighted).
   * Uses minimum description length: for each field, picks the best of
   * ranged numeric, discrete string, or gzip encoding.
   */
  private fun computeMultiFieldEntropy(data: List<Map<String, String>>, fields: List<String>): Double {
    if (fields.isEmpty() || data.isEmpty()) return 0.0
    return fields.sumOf { mdlEntropy(data, it) } / fields.size
  }

  // --- Tree Structures ---
  private sealed class Node
  private data class Leaf(
    val stats: List<FieldStats>,
    val multiFieldEntropy: Double,
    val count: Int,
    val description: String? = null
  ) : Node()

  private data class Split(
    val rule: String,
    val left: Node,
    val right: Node,
    val feature: String,
    val operator: String,
    val value: String
  ) : Node()

  data class TreeSummary(val leafCount: Int, val maxDepth: Int, val weightedEntropy: Double, val totalRecords: Int)

  private fun summarizeTree(node: Node, depth: Int = 0): TreeSummary {
    return when (node) {
      is Leaf -> TreeSummary(1, depth, node.multiFieldEntropy * node.count, node.count)
      is Split -> {
        val leftSummary = summarizeTree(node.left, depth + 1)
        val rightSummary = summarizeTree(node.right, depth + 1)
        val totalRecords = leftSummary.totalRecords + rightSummary.totalRecords
        TreeSummary(
          leafCount = leftSummary.leafCount + rightSummary.leafCount,
          maxDepth = maxOf(leftSummary.maxDepth, rightSummary.maxDepth),
          weightedEntropy = (leftSummary.weightedEntropy + rightSummary.weightedEntropy) / totalRecords.coerceAtLeast(1),
          totalRecords = totalRecords
        )
      }
    }
  }

  // --- Tree Building ---
  private fun buildTree(
    data: List<Map<String, String>>,
    analysisFields: List<String>,
    allHeaders: List<String>,
    depth: Int,
    maxDepth: Int,
    candidateRules: Int,
    topN: Int,
    agent: ChatAgent,
    executionTask: ISessionTask,
    statsTab: ISessionTask,
    transcript: OutputStream?
  ): Node {
    val topN = executionConfig?.top_n_values ?: 10
    val total = data.size
    val currentEntropy = computeMultiFieldEntropy(data, analysisFields)
    val stats = computeFieldStatistics(data, analysisFields, topN)
// Check if all fields have very low entropy (nearly pure)
    val allLowEntropy = stats.all { it.entropy < 0.1 }
    if (depth >= maxDepth || allLowEntropy || total < 5) {
      // Ask agent to describe this terminal leaf
      val sample = data.shuffled().take(topN).joinToString("\n") { record ->
        record.entries.joinToString(", ") { "${it.key}=${it.value}" }
      }
      val descPrompt = buildString {
        appendLine("This data subset has $total records and has reached a terminal node (depth=$depth).")
        appendLine()
        appendLine("Per-field statistics:")
        for (s in stats) {
          appendLine(
            "  ${s.fieldName}: distinct=${s.distinctCount}, entropy=${"%.3f".format(s.entropy)}, top=${
              s.topValues.joinToString(
                ", "
              ) { "${it.first}(${it.second})" }
            }"
          )
          if (s.isNumeric) {
            appendLine(
              "    numeric: min=${"%.2f".format(s.numericMin)}, max=${"%.2f".format(s.numericMax)}, mean=${
                "%.2f".format(
                  s.numericMean
                )
              }"
            )
          }
        }
        appendLine()
        appendLine("Data Sample (up to ${total} records):")
        appendLine(sample)
        appendLine()
        appendLine("Please provide a detailed natural language description of this data subset.")
        appendLine("Explain what makes these records similar, what patterns exist, and any notable characteristics.")
        appendLine("Respond with just the description text.")
      }
      val description = try {
        agent.answer(listOf(descPrompt)).trim()
      } catch (e: Exception) {
        log.warn("Failed to get leaf description at depth $depth: ${e.message}")
        "Terminal leaf at depth $depth with $total records and average entropy ${"%.4f".format(currentEntropy)}"
      }
      val leafMsg =
        "Leaf at depth $depth: entropy=${"%.4f".format(currentEntropy)}, n=$total, description=${description.take(100)}..."
      transcript?.write("$leafMsg\n".toByteArray())
      return Leaf(stats, currentEntropy, total, description)
    }
// Show statistics for this node
    val nodeLabel = "Depth $depth (n=$total)"
    statsTab.add(buildString {
      appendLine("### Node: $nodeLabel")
      append(formatStatistics(stats, currentEntropy, total))
    }.renderMarkdown())
    transcript?.write("\n### Node: $nodeLabel\n${formatStatisticsPlain(stats, currentEntropy, total)}\n".toByteArray())
// Build a sample with statistics for the LLM
    val sample = data.shuffled().take(10).joinToString("\n") { record ->
      record.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
    val statsPromptSection = buildString {
      appendLine("Per-field statistics for the current ${topN} records:")
      for (s in stats) {
        appendLine(
          "  ${s.fieldName}: distinct=${s.distinctCount}, entropy=${"%.3f".format(s.entropy)}, top=${
            s.topValues.joinToString(
              ", "
            ) { "${it.first}(${it.second})" }
          }"
        )
        if (s.isNumeric) {
          appendLine(
            "    numeric: min=${"%.2f".format(s.numericMin)}, max=${"%.2f".format(s.numericMax)}, mean=${
              "%.2f".format(
                s.numericMean
              )
            }"
          )
        }
      }
    }
    val prompt = buildString {
      appendLine("Analysis Fields: ${analysisFields.joinToString(", ")}")
      appendLine("All Available Fields: ${allHeaders.joinToString(", ")}")
      appendLine()
      appendLine(statsPromptSection)
      appendLine()
      appendLine("Current Data Sample (10 of $total records):")
      appendLine(sample)
      appendLine()
      appendLine("Current average multi-field entropy: ${"%.4f".format(currentEntropy)}")
      appendLine()
      appendLine("You have two options:")
      appendLine()
      appendLine("OPTION A: If this data subset is already sufficiently homogeneous or has a clear characterization,")
      appendLine("respond with a line starting with 'DESCRIBE:' followed by a detailed natural language description")
      appendLine("of this data subset. Explain what makes these records similar, what patterns or clusters exist,")
      appendLine("and any notable characteristics. This terminates splitting for this branch.")
      appendLine()
      appendLine("OPTION B: Propose $candidateRules splitting rules that will best reduce the overall entropy across ALL analysis fields.")
      appendLine("Choose rules that create the most homogeneous subgroups across all fields simultaneously.")
      appendLine("Each rule must be in the format: FIELD OPERATOR VALUE")
      appendLine()
      appendLine("Choose Option A if the data is already fairly uniform or if further splitting would not be meaningful.")
      appendLine("Choose Option B if there are clear heterogeneous subgroups that can be separated.")
    }
    val response = agent.answer(listOf(prompt))
    val responseLines = response.lines().filter { it.isNotBlank() }.map { it.trim() }

    // Check if the agent chose to describe (terminate) this node
    val describeLine = responseLines.firstOrNull { it.startsWith("DESCRIBE:", ignoreCase = true) }
    if (describeLine != null) {
      val description = describeLine.removePrefix("DESCRIBE:").removePrefix("describe:").trim() +
          responseLines.filter { !it.startsWith("DESCRIBE:", ignoreCase = true) }.joinToString(" ") { it.trim() }.let {
            if (it.isNotBlank()) "\n$it" else ""
          }
      val leafMsg =
        "Leaf at depth $depth (agent described): entropy=${"%.4f".format(currentEntropy)}, n=$total, description=${
          description.take(100)
        }..."
      executionTask.add("**Described Leaf** at depth $depth (n=$total): ${description.take(200)}...".renderMarkdown())
      transcript?.write("$leafMsg\n".toByteArray())
      return Leaf(stats, currentEntropy, total, description)
    }

    val rules = responseLines.map { it.trim() }
    var bestGain = -1.0
    var bestSplit: Split? = null
    var bestLeftData: List<Map<String, String>> = emptyList()
    var bestRightData: List<Map<String, String>> = emptyList()
    for (ruleStr in rules) {
      val parts = parseRule(ruleStr) ?: continue
      val (feature, op, value) = parts
      val (left, right) = data.partition { record ->
        evaluate(record, feature, op, value)
      }
      if (left.isEmpty() || right.isEmpty()) continue
      val pLeft = left.size.toDouble() / total
      val pRight = right.size.toDouble() / total
      val leftEntropy = computeMultiFieldEntropy(left, analysisFields)
      val rightEntropy = computeMultiFieldEntropy(right, analysisFields)
      val gain = currentEntropy - (pLeft * leftEntropy + pRight * rightEntropy)
      if (gain > bestGain) {
        bestGain = gain
        bestSplit = Split(ruleStr, Leaf(emptyList(), 0.0, 0), Leaf(emptyList(), 0.0, 0), feature, op, value)
        bestLeftData = left
        bestRightData = right
      }
    }
    if (bestSplit == null || bestGain < 0.005) {
      val leafMsg =
        "Leaf at depth $depth (no good split): entropy=${"%.4f".format(currentEntropy)}, n=$total. Generating description..."
      transcript?.write("$leafMsg\n".toByteArray())
      // Ask agent to describe this leaf since no good split was found
      val descPrompt = buildString {
        appendLine("No good splitting rule was found for this data subset of $total records.")
        appendLine()
        appendLine(statsPromptSection)
        appendLine()
        appendLine("Data Sample (10 of $total records):")
        appendLine(sample)
        appendLine()
        appendLine("Please provide a detailed natural language description of this data subset.")
        appendLine("Explain what makes these records similar, what patterns exist, and any notable characteristics.")
        appendLine("Respond with just the description text.")
      }
      val descResponse = agent.answer(listOf(descPrompt))
      return Leaf(stats, currentEntropy, total, descResponse.trim())
    }
    val splitMsg =
      "Depth $depth: Split on **${bestSplit.rule}** (Entropy Gain: ${"%.4f".format(bestGain)}, Left: ${bestLeftData.size}, Right: ${bestRightData.size})"
    executionTask.add(splitMsg.renderMarkdown())
    transcript?.write("$splitMsg\n".toByteArray())
    val leftNode = buildTree(
      bestLeftData,
      analysisFields,
      allHeaders,
      depth + 1,
      maxDepth,
      candidateRules,
      topN,
      agent,
      executionTask,
      statsTab,
      transcript
    )
    val rightNode = buildTree(
      bestRightData,
      analysisFields,
      allHeaders,
      depth + 1,
      maxDepth,
      candidateRules,
      topN,
      agent,
      executionTask,
      statsTab,
      transcript
    )
    return bestSplit.copy(left = leftNode, right = rightNode)
  }

  // --- Rule Parsing & Evaluation ---
  private fun parseRule(rule: String): Triple<String, String, String>? {
    val regex = Regex("""^([a-zA-Z0-9_]+)\s+(==|!=|>|<|contains|matches)\s+(.+)$""")
    val match = regex.find(rule) ?: return null
    val (feature, op, value) = match.destructured
    return Triple(feature, op, value)
  }

  private fun evaluate(record: Map<String, String>, feature: String, op: String, value: String): Boolean {
    val recordValue = record[feature] ?: return false
    return when (op) {
      "==" -> recordValue == value
      "!=" -> recordValue != value
      ">" -> (recordValue.toDoubleOrNull() ?: 0.0) > (value.toDoubleOrNull() ?: 0.0)
      "<" -> (recordValue.toDoubleOrNull() ?: 0.0) < (value.toDoubleOrNull() ?: 0.0)
      "contains" -> recordValue.contains(value, ignoreCase = true)
      "matches" -> try {
        Regex(value).matches(recordValue)
      } catch (e: Exception) {
        log.warn("Invalid regex in rule: $value", e)
        false
      }

      else -> false
    }
  }


  // --- JSON Output Generation ---
  private fun generateJsonOutput(
    node: Node,
    analysisFields: List<String>,
    initialEntropy: Double,
    totalRecords: Int,
    config: EntropyReductionTreeTaskExecutionConfigData
  ): Map<String, Any?> {
    val treeSummary = summarizeTree(node)
    var leafId = 0

    fun fieldStatsToMap(s: FieldStats): Map<String, Any?> = buildMap {
      put("field_name", s.fieldName)
      put("distinct_count", s.distinctCount)
      put("entropy_bits_per_record", "%.4f".format(s.entropy).toDouble())
      put("encoding_strategy", s.encodingStrategy)
      put("null_count", s.nullCount)
      put("is_numeric", s.isNumeric)
      if (s.isNumeric) {
        put("numeric_min", s.numericMin)
        put("numeric_max", s.numericMax)
        put("numeric_mean", s.numericMean?.let { "%.4f".format(it).toDouble() })
      }
      put("top_values", s.topValues.map { mapOf("value" to it.first, "count" to it.second) })
    }

    fun nodeToMap(n: Node, path: List<String> = emptyList()): Map<String, Any?> {
      return when (n) {
        is Leaf -> {
          val currentId = leafId++
          buildMap {
            put("type", "leaf")
            put("cluster_id", currentId)
            put("record_count", n.count)
            put("multi_field_entropy", "%.4f".format(n.multiFieldEntropy).toDouble())
            put("description", n.description)
            put("path", path)
            put("field_statistics", n.stats.map { fieldStatsToMap(it) })
          }
        }

        is Split -> {
          buildMap {
            put("type", "split")
            put("rule", n.rule)
            put("feature", n.feature)
            put("operator", n.operator)
            put("value", n.value)
            put("match", nodeToMap(n.left, path + listOf("${n.feature} ${n.operator} ${n.value}")))
            put("no_match", nodeToMap(n.right, path + listOf("NOT(${n.feature} ${n.operator} ${n.value})")))
          }
        }
      }
    }

    return buildMap {
      put("entropy_reduction_tree", buildMap {
        put("metadata", buildMap {
          put("data_file", config.data_file)
          put("total_records", totalRecords)
          put("analysis_fields", analysisFields)
          put("ignored_fields", config.ignored_fields ?: emptyList<String>())
          put("max_depth", config.max_depth)
          put("candidate_rules", config.candidate_rules)
        })
        put("summary", buildMap {
          put("total_leaves", treeSummary.leafCount)
          put("max_depth_reached", treeSummary.maxDepth)
          put("initial_entropy", "%.4f".format(initialEntropy).toDouble())
          put("weighted_leaf_entropy", "%.4f".format(treeSummary.weightedEntropy).toDouble())
          put("entropy_reduction", "%.4f".format(initialEntropy - treeSummary.weightedEntropy).toDouble())
        })
        put("tree", nodeToMap(node))
      })
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(EntropyReductionTreeTask::class.java)

    @JvmStatic
    val EntropyReductionTree = TaskType(
      name = "EntropyReductionTree",
      category = "Reasoning",
      taskClass = EntropyReductionTreeTask::class.java,
      executionConfigClass = EntropyReductionTreeTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Build an LLM-driven tree that reduces overall data entropy across multiple fields. Unlike a decision tree classifier, this does not predict a single target — it partitions data into homogeneous clusters. Specify fields to include/ignore. Provides per-field descriptive statistics at each node. Supports CSV and JSONL input.",
      tooltipHtml = "<ul><li>Reduces entropy across all selected fields simultaneously</li><li>No single target column — finds natural data clusters</li><li>Provides descriptive statistics (top values, distinct counts, entropy) per field at each node</li><li>LLM proposes semantic splitting rules validated by multi-field entropy gain</li><li>LLM can terminate branches with natural language descriptions of homogeneous subsets</li><li>Generates JSON output with tree structure, rules, descriptions, and statistics</li><li>Supports CSV and JSONL input formats</li></ul>",
    )
  }
}