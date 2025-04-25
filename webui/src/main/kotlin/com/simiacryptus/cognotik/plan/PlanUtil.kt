package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.plan.AbstractTask.TaskState
import com.simiacryptus.cognotik.util.AgentPatterns
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.util.JsonUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap


object PlanUtil {

  fun diagram(
    ui: ApplicationInterface,
    taskMap: Map<String, com.simiacryptus.cognotik.plan.TaskConfigBase>
  ) = MarkdownUtil.renderMarkdown(
      "## Sub-Plan Task Dependency Graph\n${com.simiacryptus.cognotik.plan.TRIPLE_TILDE}mermaid\n${
          com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph(
              taskMap
          )
      }\n${com.simiacryptus.cognotik.plan.TRIPLE_TILDE}",
    ui = ui
  )

  fun render(
      withPrompt: com.simiacryptus.cognotik.plan.TaskBreakdownWithPrompt,
      ui: ApplicationInterface
  ) = AgentPatterns.displayMapInTabs(
    mapOf(
      "Text" to MarkdownUtil.renderMarkdown(withPrompt.planText, ui = ui),
      "JSON" to MarkdownUtil.renderMarkdown(
          "${com.simiacryptus.cognotik.plan.TRIPLE_TILDE}json\n${JsonUtil.toJson(withPrompt)}\n${com.simiacryptus.cognotik.plan.TRIPLE_TILDE}",
        ui = ui
      ),
      "Diagram" to MarkdownUtil.renderMarkdown(
          "```mermaid\n" + com.simiacryptus.cognotik.plan.PlanUtil.buildMermaidGraph(
              (com.simiacryptus.cognotik.plan.PlanUtil.filterPlan {
                  withPrompt.plan
              } ?: emptyMap()).toMutableMap()
        ) + "\n```\n", ui = ui
      )
    )
  )

    fun executionOrder(tasks: Map<String, com.simiacryptus.cognotik.plan.TaskConfigBase>): List<String> {
    val taskIds: MutableList<String> = mutableListOf()
    val taskMap = tasks.toMutableMap()
    while (taskMap.isNotEmpty()) {
      val nextTasks =
        taskMap.filter { (_, task) ->
          task.task_dependencies?.filter { entry ->
            entry in tasks.keys
          }?.all { taskIds.contains(it) } ?: true
        }
      if (nextTasks.isEmpty()) {
        throw RuntimeException("Circular dependency detected in task breakdown")
      }
      taskIds.addAll(nextTasks.keys)
      nextTasks.keys.forEach { taskMap.remove(it) }
    }
    return taskIds
  }

  val isWindows = System.getProperty("os.name").lowercase(Locale.getDefault()).contains("windows")
  private fun sanitizeForMermaid(input: String) = input
    .replace(" ", "_")
    .replace("\"", "\\\"")
    .replace("[", "\\[")
    .replace("]", "\\]")
    .replace("(", "\\(")
    .replace(")", "\\)")
    .let { "`$it`" }

  private fun escapeMermaidCharacters(input: String) = input
    .replace("\"", "\\\"")
    .let { '"' + it + '"' }

  // Cache for memoizing buildMermaidGraph results
  private val mermaidGraphCache = ConcurrentHashMap<String, String>()
  private val mermaidExceptionCache = ConcurrentHashMap<String, Exception>()

    fun buildMermaidGraph(subTasks: Map<String, com.simiacryptus.cognotik.plan.TaskConfigBase>): String {
    // Generate a unique key based on the subTasks map
    val cacheKey = JsonUtil.toJson(subTasks)
    // Return cached result if available
        com.simiacryptus.cognotik.plan.PlanUtil.mermaidGraphCache[cacheKey]?.let { return it }
        com.simiacryptus.cognotik.plan.PlanUtil.mermaidExceptionCache[cacheKey]?.let { throw it }
    try {
      val graphBuilder = StringBuilder("graph TD;\n")
      subTasks.forEach { (taskId, task) ->
          val sanitizedTaskId = com.simiacryptus.cognotik.plan.PlanUtil.sanitizeForMermaid(taskId)
        val taskType = task.task_type ?: "Unknown"
          val escapedDescription =
              com.simiacryptus.cognotik.plan.PlanUtil.escapeMermaidCharacters(task.task_description ?: "")
        val style = when (task.state) {
          TaskState.Completed -> ":::completed"
          TaskState.InProgress -> ":::inProgress"
          else -> ":::$taskType"
        }
        graphBuilder.append("    ${sanitizedTaskId}[$escapedDescription]$style;\n")
        task.task_dependencies?.forEach { dependency ->
            val sanitizedDependency = com.simiacryptus.cognotik.plan.PlanUtil.sanitizeForMermaid(dependency)
          graphBuilder.append("    $sanitizedDependency --> ${sanitizedTaskId};\n")
        }
      }
      graphBuilder.append("    classDef default fill:#f9f9f9,stroke:#333,stroke-width:2px;\n")
      graphBuilder.append("    classDef NewFile fill:lightblue,stroke:#333,stroke-width:2px;\n")
      graphBuilder.append("    classDef EditFile fill:lightgreen,stroke:#333,stroke-width:2px;\n")
      graphBuilder.append("    classDef Documentation fill:lightyellow,stroke:#333,stroke-width:2px;\n")
      graphBuilder.append("    classDef Inquiry fill:orange,stroke:#333,stroke-width:2px;\n")
      graphBuilder.append("    classDef TaskPlanning fill:lightgrey,stroke:#333,stroke-width:2px;\n")
      graphBuilder.append("    classDef completed fill:#90EE90,stroke:#333,stroke-width:2px;\n")
      graphBuilder.append("    classDef inProgress fill:#FFA500,stroke:#333,stroke-width:2px;\n")
      val graph = graphBuilder.toString()
        com.simiacryptus.cognotik.plan.PlanUtil.mermaidGraphCache[cacheKey] = graph
      return graph
    } catch (e: Exception) {
        com.simiacryptus.cognotik.plan.PlanUtil.mermaidExceptionCache[cacheKey] = e
      throw e
    }
  }

    fun filterPlan(
        retries: Int = 3,
        fn: () -> Map<String, com.simiacryptus.cognotik.plan.TaskConfigBase>?
    ): Map<String, com.simiacryptus.cognotik.plan.TaskConfigBase>? {
    val obj = fn() ?: emptyMap()
    val tasksByID = obj.filter { (k, v) ->
      when {
          v.task_type == com.simiacryptus.cognotik.plan.TaskType.Companion.TaskPlanningTask.name && v.task_dependencies.isNullOrEmpty() ->
          if (retries <= 0) {
              com.simiacryptus.cognotik.plan.PlanUtil.log.warn(
                  "TaskPlanning task $k has no dependencies: " + JsonUtil.toJson(
                      obj
                  )
              )
            true
          } else {
              com.simiacryptus.cognotik.plan.PlanUtil.log.info("TaskPlanning task $k has no dependencies")
              return com.simiacryptus.cognotik.plan.PlanUtil.filterPlan(retries - 1, fn)
          }

        else -> true
      }
    }
    tasksByID.forEach {
      it.value.task_dependencies = it.value.task_dependencies?.filter { it in tasksByID.keys }?.toMutableList()
      it.value.state = TaskState.Pending
    }
    try {
        com.simiacryptus.cognotik.plan.PlanUtil.executionOrder(tasksByID)
    } catch (e: RuntimeException) {
      if (retries <= 0) {
          com.simiacryptus.cognotik.plan.PlanUtil.log.warn("Error filtering plan: " + JsonUtil.toJson(obj), e)
        throw e
      } else {
          com.simiacryptus.cognotik.plan.PlanUtil.log.info("Circular dependency detected in task breakdown")
          return com.simiacryptus.cognotik.plan.PlanUtil.filterPlan(retries - 1, fn)
      }
    }
    return if (tasksByID.size == obj.size) {
      obj
    } else com.simiacryptus.cognotik.plan.PlanUtil.filterPlan {
        tasksByID
    }
  }

  fun getAllDependencies(
      subPlanTask: com.simiacryptus.cognotik.plan.TaskConfigBase,
      subTasks: Map<String, com.simiacryptus.cognotik.plan.TaskConfigBase>,
      visited: MutableSet<String>
  ): List<String> {
    val dependencies = subPlanTask.task_dependencies?.toMutableList() ?: mutableListOf()
    subPlanTask.task_dependencies?.forEach { dep ->
      if (dep in visited) return@forEach
      val subTask = subTasks[dep]
      if (subTask != null) {
        visited.add(dep)
          dependencies.addAll(com.simiacryptus.cognotik.plan.PlanUtil.getAllDependencies(subTask, subTasks, visited))
      }
    }
    return dependencies
  }

    val log = org.slf4j.LoggerFactory.getLogger(com.simiacryptus.cognotik.plan.PlanUtil::class.java)

}