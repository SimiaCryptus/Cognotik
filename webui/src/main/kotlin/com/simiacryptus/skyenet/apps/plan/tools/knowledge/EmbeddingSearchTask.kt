package com.simiacryptus.skyenet.apps.plan.tools.knowledge

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.EmbeddingModels
import com.simiacryptus.jopenai.opt.DistanceType
import com.simiacryptus.skyenet.apps.parse.DocumentRecord
import com.simiacryptus.skyenet.apps.plan.AbstractTask
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskConfigBase
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.streams.asSequence

class EmbeddingSearchTask(
  planSettings: PlanSettings,
  planTask: EmbeddingSearchTaskConfigData?
) : AbstractTask<EmbeddingSearchTask.EmbeddingSearchTaskConfigData>(planSettings, planTask) {
  class EmbeddingSearchTaskConfigData(
    @Description("The positive search queries to look for in the embeddings")
    val positive_queries: List<String>,
    @Description("The negative search queries to avoid in the embeddings")
    val negative_queries: List<String> = emptyList(),
    @Description("The distance type to use for comparing embeddings (Euclidean, Manhattan, or Cosine)")
    val distance_type: DistanceType = DistanceType.Cosine,
    @Description("The number of top results to return")
    val count: Int = 5,
    @Description("The minimum length of the content to be considered")
    val min_length: Int = 0,
    @Description("List of regex patterns that must be present in the content")
    val required_regexes: List<String> = emptyList(),
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = "EmbeddingSearch",
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment() = """
EmbeddingSearch - Search for similar embeddings in index files and provide top results
    ** Specify the positive search queries
    ** Optionally specify negative search queries
    ** Specify the distance type (Euclidean, Manhattan, or Cosine)
    ** Specify the number of top results to return
    """.trim()

  override fun run(
    agent: PlanCoordinator,
    messages: List<String>,
    task: SessionTask,
    api: ChatClient,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings
  ) {
    val searchResults = performEmbeddingSearch(api2)
    val formattedResults = formatSearchResults(searchResults)
    task.add(MarkdownUtil.renderMarkdown(formattedResults, ui = agent.ui))
    resultFn(formattedResults)
  }

  private fun performEmbeddingSearch(api: OpenAIClient): List<EmbeddingSearchResult> {
    val positiveEmbeddings = taskConfig?.positive_queries?.map { query ->
      api.createEmbedding(
        ApiModel.EmbeddingRequest(
          input = query,
          model = EmbeddingModels.Large.modelName
        )
      ).data[0].embedding
    } ?: emptyList()
    val negativeEmbeddings = taskConfig?.negative_queries?.map { query ->
      api.createEmbedding(
        ApiModel.EmbeddingRequest(
          input = query,
          model = EmbeddingModels.Large.modelName
        )
      ).data[0].embedding
    } ?: emptyList()
    if (positiveEmbeddings.isEmpty()) {
      throw IllegalArgumentException("At least one positive query is required")
    }
    val distanceType = taskConfig?.distance_type ?: DistanceType.Cosine
    val filtered = Files.walk(root).asSequence()
      .filter { path ->
        path.toString().endsWith(".index.data")
      }.toList().toTypedArray()
    val minLength = taskConfig?.min_length ?: 0
    val requiredRegexes = taskConfig?.required_regexes?.map { Pattern.compile(it) } ?: emptyList()
    fun String.matchesAllRegexes(): Boolean {
      return requiredRegexes.all { regex -> regex.matcher(this).find() }
    }

    val searchResults = filtered
      .flatMap { path ->
        val records = DocumentRecord.readBinary(path.toString())
        records.mapNotNull { record ->
          record.vector?.let { vector ->
            val positiveDistances = positiveEmbeddings.filterNotNull().map { embedding ->
              distanceType.distance(vector, embedding)
            }
            val negativeDistances = negativeEmbeddings.filterNotNull().map { embedding ->
              distanceType.distance(vector, embedding)
            }
            val overallDistance = if (negativeDistances.isEmpty()) {
              positiveDistances.minOrNull() ?: Double.MAX_VALUE
            } else {
              (positiveDistances.minOrNull() ?: Double.MAX_VALUE) / (negativeDistances.minOrNull() ?: Double.MIN_VALUE)
            }
            val content = record.text ?: ""
            if (content.length >= minLength && content.matchesAllRegexes()) {
              EmbeddingSearchResult(
                file = root.relativize(path).toString(),
                record = record,
                distance = overallDistance
              )
            } else null
          }
        }
      }
      .toList()
    return searchResults
      .sortedBy { it.distance }
      .take(taskConfig?.count ?: 5)
  }

  private fun formatSearchResults(results: List<EmbeddingSearchResult>): String {
    return buildString {
      appendLine("# Embedding Search Results")
      appendLine()
      results.forEachIndexed { index, result ->
        appendLine("## Result ${index + 1}")
        appendLine("* Distance: %.3f".format(result.distance))
        appendLine("* File: ${result.record.sourcePath}")
        appendLine(getContextSummary(result.record.sourcePath, result.record.jsonPath))
        appendLine("Metadata:\n```json\n${result.record.metadata}\n```")
        appendLine()
      }
    }
  }

  private fun getContextSummary(sourcePath: String, jsonPath: String): String {
    val objectMapper = ObjectMapper()
    val jsonNode = objectMapper.readTree(File(sourcePath))
    val contextNode = getNodeAtPath(jsonNode, jsonPath)
    return buildString {
      appendLine("```json")
      appendLine(summarizeContext(contextNode, jsonPath, jsonNode))
      appendLine("```")
    }
  }

  private fun getNodeAtPath(jsonNode: JsonNode, path: String): JsonNode {
    var currentNode = jsonNode
    path.split(".").forEach { segment ->
      currentNode = when {
        segment.contains("[") -> {
          val (arrayName, indexPart) = segment.split("[", limit = 2)
          val index = indexPart.substringBefore("]").toInt()
          val field = currentNode.get(arrayName)
          val child = field?.get(index)
          if (child == null) {
            return currentNode
          }
          child
        }

        else -> {
          val child = currentNode.get(segment)
          if (child == null) {
            return currentNode
          }
          child
        }
      }
    }
    return currentNode
  }

  private fun summarizeContext(node: JsonNode, path: String, jsonNode: JsonNode): String {
    var summary = mutableMapOf<String, Any>()
    // Add siblings and descendants
    node.fields().forEach { (key, value) ->
      if (value.isPrimitive()) {
        summary[key] = value.asText()
      }
    }
    // Add siblings of parent nodes
    val pathSegments = path.split(".")
    for (i in pathSegments.size - 1 downTo 1) {
      val parentPath = pathSegments.subList(0, i).joinToString(".")
      val parentNode = getNodeAtPath(jsonNode, parentPath)
      summary = mutableMapOf(
        pathSegments[i] to summary
      )
      parentNode.fields().forEach { (key, value) ->
        when {
          value.isPrimitive() -> summary[key] = value.asText()
          key == "entities" || key == "tags" || key == "metadata" -> summary[key] = value
        }
      }
    }
    return JsonUtil.toJson(summary)
  }


  data class EmbeddingSearchResult(
    val file: String,
    val record: DocumentRecord,
    val distance: Double
  )

  companion object {
    private val log = LoggerFactory.getLogger(EmbeddingSearchTask::class.java)
  }
}

private fun JsonNode.isPrimitive(): Boolean {
  return this.isNumber || this.isTextual || this.isBoolean
}