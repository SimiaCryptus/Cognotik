package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.skyenet.core.util.FileValidationUtils
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.regex.Pattern
import kotlin.streams.asSequence

class FileSearchTask(
  planSettings: PlanSettings,
  planTask: SearchTaskConfigData?
) : AbstractTask<FileSearchTask.SearchTaskConfigData>(planSettings, planTask) {
  class SearchTaskConfigData(
    @Description("The search pattern (substring or regex) to look for in the files")
    val search_pattern: String = "",
    @Description("Whether the search pattern is a regex (true) or a substring (false)")
    val is_regex: Boolean = false,
    @Description("The number of context lines to include before and after each match")
    val context_lines: Int = 2,
    @Description("The specific files (or file patterns) to be searched")
    val input_files: List<String>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : TaskConfigBase(
    task_type = TaskType.Search.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    state = state
  )

  override fun promptSegment() = """
    Search - Search for patterns in files and provide results with context
      ** Specify the search pattern (substring or regex)
      ** Specify whether the pattern is a regex or a substring
      ** Specify the number of context lines to include
      ** List input files or file patterns to be searched
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
    val searchResults = performSearch()
    val formattedResults = formatSearchResults(searchResults)
    task.add(MarkdownUtil.renderMarkdown(formattedResults, ui = agent.ui))
    resultFn(formattedResults)
  }

  private fun performSearch(): List<SearchResult> {
    val pattern = if (taskConfig?.is_regex == true) {
      Pattern.compile(taskConfig.search_pattern)
    } else {
      Pattern.compile(Pattern.quote(taskConfig?.search_pattern))
    }

    return (taskConfig?.input_files ?: listOf())
      .flatMap { filePattern ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$filePattern")
        Files.walk(root).asSequence()
          .filter { path ->
            matcher.matches(root.relativize(path)) &&
                FileValidationUtils.isLLMIncludableFile(path.toFile())
          }
          .flatMap { path ->
            val relativePath = root.relativize(path).toString()
            val lines = Files.readAllLines(path)
            lines.mapIndexed { index, line ->
              if (pattern.matcher(line).find()) {
                SearchResult(
                  file = relativePath,
                  lineNumber = index + 1,
                  matchedLine = line,
                  context = getContext(lines, index, taskConfig?.context_lines ?: 2)
                )
              } else null
            }.filterNotNull()
          }
          .toList()
      }
  }

  private fun getContext(lines: List<String>, matchIndex: Int, contextLines: Int): List<String> {
    val start = (matchIndex - contextLines).coerceAtLeast(0)
    val end = (matchIndex + contextLines + 1).coerceAtMost(lines.size)
    return lines.subList(start, end)
  }

  private fun formatSearchResults(results: List<SearchResult>): String {
    return buildString {
      appendLine("# Search Results")
      appendLine()
      results.groupBy { it.file }.forEach { (file, fileResults) ->
        appendLine("## $file")
        appendLine()
        fileResults.forEach { result ->
          appendLine("### Line ${result.lineNumber}")
          appendLine()
          appendLine("```")
          result.context.forEachIndexed { index, line ->
            val lineNumber = result.lineNumber - (result.context.size / 2) + index
            val prefix = if (lineNumber == result.lineNumber) ">" else " "
            appendLine("$prefix $lineNumber: $line")
          }
          appendLine("```")
          appendLine()
        }
      }
    }
  }

  data class SearchResult(
    val file: String,
    val lineNumber: Int,
    val matchedLine: String,
    val context: List<String>
  )

  companion object {
    private val log = LoggerFactory.getLogger(FileSearchTask::class.java)
  }
}