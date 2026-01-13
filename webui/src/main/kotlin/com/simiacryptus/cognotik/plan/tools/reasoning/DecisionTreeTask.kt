package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
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
        @Description("Maximum depth of the tree")
        var max_depth: Int = 3,
        @Description("Number of candidate rules to generate per node")
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
            return null
        }
    }

    override fun promptSegment(): String {
        return """
 DecisionTree - Build an LLM-driven symbolic decision tree
  ** Specify the data file (CSV)
  ** Specify the target column to predict
  ** Configure max depth and candidate rules
  ** Uses LLM to propose semantic splitting rules
  ** Validates rules using Information Gain
  ** Generates executable code
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


      val transcript = task.transcript()
      try {
        val config = executionConfig ?: return
        val error = config.validate()
        if (error != null) {
          val msg = "Configuration Error: $error"
          task.error(Exception(msg))
          log.error(msg)
          transcript?.write("## Error\n$msg\n".toByteArray())
          task.complete()
          return
        }

        val tabs = TabbedDisplay(task)
        val executionTask = tabs.newTask("Execution")
        executionTask.header("Building Decision Tree")
        val statusBuffer = executionTask.add("Initializing...".renderMarkdown())

        task.ui.pool.submit {
          try {
            log.info("Starting DecisionTreeTask for ${config.data_file}")
            transcript?.write("## Decision Tree Construction Started\n".toByteArray())

            val dataFile = root.resolve(config.data_file!!).toFile()
            if (!dataFile.exists()) {
              val msg = "Data file not found: ${config.data_file}"
              executionTask.error(Exception(msg))
              log.error(msg)
              transcript?.write("### Error\n$msg\n".toByteArray())
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
              transcript?.write("### Error Loading Data\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
              return@submit
            }

            if (records.isEmpty()) {
              executionTask.add("Data file is empty".renderMarkdown())
              return@submit
            }

            val headers = records.first().keys
            if (!headers.contains(config.target_column)) {
              val msg = "Target column '${config.target_column}' not found. Available: $headers"
              executionTask.add(msg.renderMarkdown())
              return@submit
            }

            statusBuffer?.setLength(0)
            statusBuffer?.append("Loaded ${records.size} records. Analyzing...".renderMarkdown())
            executionTask.update()

            val chatAgent = ChatAgent(
              model = defaultSmart.getChildClient(task),
              temperature = 0.2,
              prompt = """
                            You are a Decision Tree Rule Generator.
                            Analyze the provided data samples and propose splitting rules to predict the target variable: '${config.target_column}'.
                            
                            Output Format:
                            Provide exactly ${config.candidate_rules} distinct rules.
                            Each rule must be in the format: `FIELD OPERATOR VALUE`
                            
                            Supported Operators:
                            - `==` (Exact match)
                            - `!=` (Not equal)
                            - `>` (Numeric greater than)
                            - `<` (Numeric less than)
                            - `contains` (String contains)
                            - `matches` (Regex match)
                            
                            Examples:
                            age > 25
                            status == active
                            description contains error
                            sku matches ^[A-Z]-123
                            
                            Do not include explanations. Just the rules, one per line.
                        """.trimIndent()
            )

            tabs["Configuration"] = """
                        ### Parameters
                        * **Target:** `${config.target_column}`
                        * **Data File:** `${config.data_file}`
                        * **Records:** ${records.size}
                        * **Max Depth:** ${config.max_depth}
                    """.trimIndent().renderMarkdown()

            val tree = buildTree(
              records,
              config.target_column!!,
              0,
              config.max_depth,
              config.candidate_rules,
              chatAgent,
              executionTask
            )
            val code = generateCode(tree)

            statusBuffer?.setLength(0)
            statusBuffer?.append("Tree construction complete.".renderMarkdown())
            executionTask.update()

            tabs["Generated Code"] = "```kotlin\n$code\n```".renderMarkdown()

            val fileUrl = task.saveFile("DecisionTree.kt", code.toByteArray())
            executionTask.add("Download: <a href='$fileUrl'>DecisionTree.kt</a>")

            transcript?.write("### Tree Construction Complete\n<details><summary>Generated Code</summary>\n\n```kotlin\n$code\n```\n</details>\n".toByteArray())
            log.info("DecisionTreeTask completed successfully.")

            resultFn(
              """
                        ## Decision Tree Generated
                        * Target: `${config.target_column}`
                        * Max Depth: `${config.max_depth}`
                        * Code saved to: `DecisionTree.kt`
                    """.trimIndent()
            )
          } catch (e: Exception) {
            executionTask.error(e)
            log.error("Error in DecisionTreeTask execution", e)
            transcript?.write("## Execution Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
          } finally {
            executionTask.complete()
            task.complete()
          }
        }
      } catch (e: Exception) {
        task.error(e)
        log.error("Error initializing DecisionTreeTask", e)
      } finally {
        transcript?.close()
      }
    }

    private fun loadCsv(file: File): List<Map<String, String>> {
        val lines = file.readLines()
        if (lines.isEmpty()) return emptyList()
        // Simple CSV parsing - assumes no commas in values for simplicity, or use a library if available
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
        task: SessionTask
    ): Node {
        val counts = data.groupingBy { it[target] ?: "Unknown" }.eachCount()
        val total = data.size
        val dominantClass = counts.maxByOrNull { it.value }?.key ?: "Unknown"
        val purity = (counts[dominantClass] ?: 0).toDouble() / total

        if (depth >= maxDepth || purity > 0.95 || total < 5) {
            return Leaf(dominantClass, purity, total)
        }

        // Sample data for LLM
        val sample = data.shuffled().take(10).joinToString("\n") { record ->
            record.entries.joinToString(", ") { "${it.key}=${it.value}" }
        }

        val prompt = """
            Target Variable: $target
            Current Data Sample (10 of $total records):
            $sample
            
            Propose $candidateRules splitting rules to separate '$target'.
        """.trimIndent()

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
            return Leaf(dominantClass, purity, total)
        }

        task.add("Depth $depth: Split on <b>${bestSplit.rule}</b> (Gain: ${"%.4f".format(bestGain)})")

        val leftNode = buildTree(bestLeftData, target, depth + 1, maxDepth, candidateRules, agent, task)
        val rightNode = buildTree(bestRightData, target, depth + 1, maxDepth, candidateRules, agent, task)

        return bestSplit.copy(left = leftNode, right = rightNode)
    }

    private fun parseRule(rule: String): Triple<String, String, String>? {
        // Simple regex to parse "field op value"
        // Allow spaces in value if it's the last part
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
                false
            }

            else -> false
        }
    }

    private fun entropy(data: List<Map<String, String>>, target: String): Double {
        val counts = data.groupingBy { it[target] }.eachCount()
        val total = data.size.toDouble()
        return -counts.values.sumOf {
            val p = it / total
            p * log2(p)
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
        val DecisionTree = TaskType(
          name = "DecisionTree",
          category = "Reasoning",
          taskClass = DecisionTreeTask::class.java,
          executionConfigClass = DecisionTreeTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Build an LLM-driven symbolic decision tree",
          tooltipHtml = """
                        Constructs a decision tree classifier using LLM for rule proposal and data for validation.
                        <ul>
                          <li>Handles unstructured text via semantic rules</li>
                          <li>Generates interpretable code</li>
                          <li>Uses Information Gain for split selection</li>
                        </ul>
                      """,
        )
    }
}