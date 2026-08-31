package com.simiacryptus.cognotik.plan.tools.data

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
import java.io.File
import java.io.OutputStream
import kotlin.math.log2

class DecisionTreeTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: DecisionTreeTaskExecutionConfigData?
) : AbstractTask<DecisionTreeTask.DecisionTreeTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class DecisionTreeTaskExecutionConfigData(
    @Description("The data file to analyze (CSV)")
    var data_file: String? = null,
    @Description("The target column name to predict")
    var target_column: String? = null,
    @Description("Maximum depth of the decision tree. Must be between 1 and 10.")
    var max_depth: Int = 3,
    @Description("Number of candidate splitting rules the LLM should propose per tree node. Must be between 2 and 20.")
    var candidate_rules: Int = 5,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = DecisionTree.name,
    task_description = "Build a decision tree for '$target_column' from '$data_file'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (data_file.isNullOrBlank()) return "data_file is required"
      if (target_column.isNullOrBlank()) return "target_column is required"
      max_depth = max_depth.coerceIn(1, 10)
      candidate_rules = candidate_rules.coerceIn(2, 20)
      return null
    }
  }

  override fun promptSegment(): String = buildString {
    appendLine("DecisionTree - Build an LLM-driven symbolic decision tree")
    appendLine("  ** Specify the data file (CSV or JSONL)")
    appendLine("  ** Specify the target column to predict")
    appendLine("  ** Configure max depth and candidate rules")
    appendLine("  ** Uses LLM to propose semantic splitting rules")
    appendLine("  ** Validates rules using Information Gain")
    appendLine("  ** Generates executable Kotlin code")
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
      executionTask.header("Building Decision Tree", level = 2)
      val statusBuffer = executionTask.add("Initializing...".renderMarkdown())

      task.pool.submit {
        try {
          log.info("Starting DecisionTreeTask for ${config.data_file}")
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

          val headers = records.first().keys
          if (!headers.contains(config.target_column)) {
            val msg = "Target column '${config.target_column}' not found. Available: $headers"
            executionTask.add(msg.renderMarkdown())
            log.error(msg)
            transcript?.write("\n## Error\n$msg\n".toByteArray())
            resultFn("Error: $msg")
            return@submit
          }

          statusBuffer?.setLength(0)
          statusBuffer?.append("Loaded ${records.size} records. Analyzing...".renderMarkdown())
          executionTask.update()
          val smartApi = orchestrationConfig.defaultSmart.getChildClient(task)

          val chatAgent = ChatAgent(
            model = smartApi,
            temperature = 0.2,
            prompt = buildString {
              appendLine("You are a Decision Tree Rule Generator.")
              appendLine("Analyze the provided data samples and propose splitting rules to predict the target variable: '${config.target_column}'.")
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
              appendLine("sku matches ^[A-Z]-123")
              appendLine()
              appendLine("Do not include explanations. Just the rules, one per line.")
            }
          )

          tabs["Configuration"] = buildString {
            appendLine("### Parameters")
            appendLine("* **Target:** `${config.target_column}`")
            appendLine("* **Data File:** `${config.data_file}`")
            appendLine("* **Records:** ${records.size}")
            appendLine("* **Max Depth:** `${config.max_depth}`")
            appendLine("* **Candidate Rules:** `${config.candidate_rules}`")
          }.renderMarkdown()

          val tree = buildTree(
            records,
            config.target_column!!,
            0,
            config.max_depth,
            config.candidate_rules,
            chatAgent,
            executionTask,
            transcript
          )
          val code = generateCode(tree)

          statusBuffer?.setLength(0)
          statusBuffer?.append("Tree construction complete.".renderMarkdown())
          executionTask.update()

          resultTab.header("Decision Tree Results", level = 2)
          resultTab.add("```kotlin\n$code\n```".renderMarkdown())

          val outputBaseName = getOutputFile(".md")?.let {
            if (it.endsWith(".md")) it.removeSuffix(".md") else null
          }
          val codeFileName = if (outputBaseName != null) {
            "${outputBaseName}.kt"
          } else {
            "DecisionTree.kt"
          }
          val fileUrl = task.saveFile(codeFileName, code.toByteArray())
          resultTab.add("Download: <a href='$fileUrl'>$codeFileName</a>".renderMarkdown())

          transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
          transcript?.write("## Decision Tree Results\n\n".toByteArray())
          transcript?.write("<details><summary>Generated Code</summary>\n\n```kotlin\n$code\n```\n</details>\n\n".toByteArray())
          transcript?.write("</div>\n\n".toByteArray())
          log.info("DecisionTreeTask completed successfully.")

          resultFn(buildString {
            appendLine("## Decision Tree Generated")
            appendLine("* Target: `${config.target_column}`")
            appendLine("* Max Depth: `${config.max_depth}`")
            appendLine("* Records: ${records.size}")
            appendLine("* Code saved to: `$codeFileName`")
          })
        } catch (e: Exception) {
          executionTask.error(e)
          log.error("Error in DecisionTreeTask execution", e)
          transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
          resultFn("Error: ${e.message}")
        } finally {
          executionTask.complete()
          resultTab.complete()
          task.complete()
        }
      }
    } catch (e: Exception) {
      task.error(e)
      log.error("Error initializing DecisionTreeTask", e)
      transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
      resultFn("Error: ${e.message}")
      task.complete()
    } finally {
      transcript?.close()
    }
  }

  private fun writeTranscriptHeader(transcript: OutputStream?, config: DecisionTreeTaskExecutionConfigData) {
    transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
    transcript?.write(buildString {
      appendLine("## Decision Tree Construction")
      appendLine()
      appendLine("* **Data File:** `${config.data_file}`")
      appendLine("* **Target Column:** `${config.target_column}`")
      appendLine("* **Max Depth:** ${config.max_depth}")
      appendLine("* **Candidate Rules:** ${config.candidate_rules}")
      appendLine()
    }.toByteArray())
  }

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


  private sealed class Node
  private data class Leaf(val prediction: String, val probability: Double, val count: Int) : Node()
  private data class Split(
    val rule: String,
    val left: Node,
    val right: Node,
    val feature: String,
    val operator: String,
    val value: String
  ) : Node()

  private fun buildTree(
    data: List<Map<String, String>>,
    target: String,
    depth: Int,
    maxDepth: Int,
    candidateRules: Int,
    agent: ChatAgent,
    task: ISessionTask,
    transcript: OutputStream?
  ): Node {
    val counts = data.groupingBy { it[target] ?: "Unknown" }.eachCount()
    val total = data.size
    val dominantClass = counts.maxByOrNull { it.value }?.key ?: "Unknown"
    val purity = (counts[dominantClass] ?: 0).toDouble() / total

    if (depth >= maxDepth || purity > 0.95 || total < 5) {
      transcript?.write("Leaf at depth $depth: predict='$dominantClass', purity=${"%.2f".format(purity)}, n=$total\n".toByteArray())
      return Leaf(dominantClass, purity, total)
    }

    val sample = data.shuffled().take(10).joinToString("\n") { record ->
      record.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }

    val prompt = buildString {
      appendLine("Target Variable: $target")
      appendLine("Current Data Sample (10 of $total records):")
      appendLine(sample)
      appendLine()
      appendLine("Propose $candidateRules splitting rules to separate '$target'.")
    }

    val response = agent.answer(listOf(prompt))
    val rules = response.lines().filter { it.isNotBlank() }.map { it.trim() }

    var bestGain = -1.0
    var bestSplit: Split? = null
    var bestLeftData: List<Map<String, String>> = emptyList()
    var bestRightData: List<Map<String, String>> = emptyList()

    val currentEntropy = entropy(data, target)

    for (ruleStr in rules) {
      val parts = parseRule(ruleStr) ?: continue
      val (feature, op, value) = parts

      val (left, right) = data.partition { record ->
        evaluate(record, feature, op, value)
      }

      if (left.isEmpty() || right.isEmpty()) continue

      val pLeft = left.size.toDouble() / total
      val pRight = right.size.toDouble() / total
      val gain = currentEntropy - (pLeft * entropy(left, target) + pRight * entropy(right, target))

      if (gain > bestGain) {
        bestGain = gain
        bestSplit = Split(ruleStr, Leaf("temp", 0.0, 0), Leaf("temp", 0.0, 0), feature, op, value)
        bestLeftData = left
        bestRightData = right
      }
    }

    if (bestSplit == null || bestGain < 0.01) {
      transcript?.write("Leaf at depth $depth (no good split): predict='$dominantClass', purity=${"%.2f".format(purity)}, n=$total\n".toByteArray())
      return Leaf(dominantClass, purity, total)
    }

    val splitMsg =
      "Depth $depth: Split on **${bestSplit.rule}** (Gain: ${"%.4f".format(bestGain)}, Left: ${bestLeftData.size}, Right: ${bestRightData.size})"
    task.add(splitMsg.renderMarkdown())
    transcript?.write("$splitMsg\n".toByteArray())

    val leftNode = buildTree(bestLeftData, target, depth + 1, maxDepth, candidateRules, agent, task, transcript)
    val rightNode = buildTree(bestRightData, target, depth + 1, maxDepth, candidateRules, agent, task, transcript)

    return bestSplit.copy(left = leftNode, right = rightNode)
  }

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

  private fun entropy(data: List<Map<String, String>>, target: String): Double {
    val counts = data.groupingBy { it[target] }.eachCount()
    val total = data.size.toDouble()
    if (total == 0.0) return 0.0
    return -counts.values.sumOf {
      val p = it / total
      if (p > 0.0) p * log2(p) else 0.0
    }
  }

  private fun generateCode(node: Node): String {
    val sb = StringBuilder()
    sb.append("fun predict(record: Map<String, String>): String {\n")

    fun traverse(n: Node, indent: String) {
      when (n) {
        is Leaf -> {
          sb.append("$indent// Probability: ${"%.2f".format(n.probability)} (n=${n.count})\n")
          sb.append("${indent}return \"${n.prediction}\"\n")
        }

        is Split -> {
          val condition = when (n.operator) {
            "==" -> "record[\"${n.feature}\"] == \"${n.value}\""
            "!=" -> "record[\"${n.feature}\"] != \"${n.value}\""
            ">" -> "(record[\"${n.feature}\"]?.toDoubleOrNull() ?: 0.0) > ${n.value}"
            "<" -> "(record[\"${n.feature}\"]?.toDoubleOrNull() ?: 0.0) < ${n.value}"
            "contains" -> "record[\"${n.feature}\"]?.contains(\"${n.value}\", ignoreCase = true) == true"
            "matches" -> "Regex(\"${n.value}\").matches(record[\"${n.feature}\"] ?: \"\")"
            else -> "false"
          }
          sb.append("${indent}if ($condition) {\n")
          traverse(n.left, indent + "    ")
          sb.append("$indent} else {\n")
          traverse(n.right, indent + "    ")
          sb.append("$indent}\n")
        }
      }
    }

    traverse(node, "    ")
    sb.append("}")
    return sb.toString()
  }

  companion object {
    private val log = LoggerFactory.getLogger(DecisionTreeTask::class.java)

    @JvmStatic
    val DecisionTree = TaskType(
      name = "DecisionTree",
      category = "Reasoning",
      taskClass = DecisionTreeTask::class.java,
      executionConfigClass = DecisionTreeTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Build an LLM-driven symbolic decision tree from CSV or JSONL data. Specify the data file and target column to predict. The LLM proposes semantic splitting rules which are validated using Information Gain. Outputs executable Kotlin code.",
      tooltipHtml = "<ul><li>Constructs a decision tree classifier using LLM for rule proposal and data for validation</li><li>Handles unstructured text via semantic rules</li><li>Generates interpretable Kotlin code</li><li>Uses Information Gain for split selection</li><li>Supports CSV and JSONL input formats</li></ul>",
    )
  }
}