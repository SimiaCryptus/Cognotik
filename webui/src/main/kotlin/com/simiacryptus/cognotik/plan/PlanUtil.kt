package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.plan.tools.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlanUtil {

    fun diagram(
        taskMap: Map<String, TaskExecutionConfig>
    ) = "## Sub-Plan Task Dependency Graph\n${TRIPLE_TILDE}mermaid\n${
        buildMermaidGraph(
            taskMap
        )
    }\n${TRIPLE_TILDE}".renderMarkdown


    fun executionOrder(tasks: Map<String, TaskExecutionConfig>): List<String> {
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

    private val mermaidGraphCache = ConcurrentHashMap<String, String>()
    private val mermaidExceptionCache = ConcurrentHashMap<String, Exception>()

    fun buildMermaidGraph(subTasks: Map<String, TaskExecutionConfig>, includeStyles: Boolean = true): String {

        val cacheKey = JsonUtil.toJson(subTasks)

        mermaidGraphCache[cacheKey]?.let { return it }
        mermaidExceptionCache[cacheKey]?.let { throw it }
        try {
            val graphBuilder = StringBuilder("graph TD;\n")
            subTasks.forEach { (taskId, task) ->
                val sanitizedTaskId = sanitizeForMermaid(taskId)
                val taskType = task.task_type ?: "Unknown"
                if(includeStyles) {
                    graphBuilder.append(
                        "    " + sanitizedTaskId + "[" + sanitizeForMermaid(
                            task.task_description ?: ""
                        ) + "]" + when (task.state) {
                            TaskState.Completed -> ":::completed"
                            TaskState.InProgress -> ":::inProgress"
                            else -> ":::$taskType"
                        } + ";\n")
                }
                task.task_dependencies?.forEach { dependency ->
                    val sanitizedDependency = sanitizeForMermaid(dependency)
                    graphBuilder.append("    $sanitizedDependency --> ${sanitizedTaskId};\n")
                }
            }
            if(includeStyles) {
                graphBuilder.append("    classDef default fill:#f9f9f9,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef NewFile fill:lightblue,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef EditFile fill:lightgreen,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef Documentation fill:lightyellow,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef Inquiry fill:orange,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef TaskPlanning fill:lightgrey,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef completed fill:#90EE90,stroke:#333,stroke-width:2px;\n")
                graphBuilder.append("    classDef inProgress fill:#FFA500,stroke:#333,stroke-width:2px;\n")
            }
            val graph = graphBuilder.toString()
            mermaidGraphCache[cacheKey] = graph
            return graph
        } catch (e: Exception) {
            mermaidExceptionCache[cacheKey] = e
            throw e
        }
    }

    fun filterPlan(
        retries: Int = 3,
        fn: () -> Map<String, TaskExecutionConfig>?
    ): Map<String, TaskExecutionConfig>? {
        val tasksByID = fn() ?: emptyMap()
        tasksByID.forEach {
            it.value.task_dependencies = it.value.task_dependencies?.filter { it in tasksByID.keys }?.toMutableList()
            it.value.state = TaskState.Pending
        }
        try {
            executionOrder(tasksByID)
        } catch (e: RuntimeException) {
            if (retries <= 0) {
                log.warn("Error filtering plan: " + JsonUtil.toJson(fn() ?: emptyMap<String, TaskExecutionConfig>()), e)
                throw e
            } else {
                log.info("Circular dependency detected in task breakdown")
                return filterPlan(retries - 1, fn)
            }
        }
        return if (tasksByID.size == (fn() ?: emptyMap()).size) {
            fn() ?: emptyMap()
        } else filterPlan {
            tasksByID
        }
    }

    fun getAllDependencies(
      subPlanTask: TaskExecutionConfig,
      subTasks: Map<String, TaskExecutionConfig>,
      visited: MutableSet<String>
    ): List<String> {
        val dependencies = subPlanTask.task_dependencies?.toMutableList() ?: mutableListOf()
        subPlanTask.task_dependencies?.forEach { dep ->
            if (dep in visited) return@forEach
            val subTask = subTasks[dep]
            if (subTask != null) {
                visited.add(dep)
                dependencies.addAll(
                    getAllDependencies(
                        subTask,
                        subTasks,
                        visited
                    )
                )
            }
        }
        return dependencies
    }

    val log = LoggerFactory.getLogger(PlanUtil::class.java)

}