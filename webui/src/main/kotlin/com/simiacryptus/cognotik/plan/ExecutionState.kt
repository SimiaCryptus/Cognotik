package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.webui.session.SessionTask
import java.util.concurrent.Future

data class ExecutionState(
    val subTasks: Map<String, TaskExecutionConfig>,
    val tasksByDescription: MutableMap<String?, TaskExecutionConfig> = subTasks.entries.toTypedArray()
        .associate { (it.value.task_description ?: it.key) to it.value }.toMutableMap(),
    val taskIdProcessingQueue: MutableList<String> = PlanUtil.executionOrder(subTasks)
        .toMutableList(),
    val taskResult: MutableMap<String, String> = mutableMapOf(),
    val completedTasks: MutableList<String> = mutableListOf(),
    val taskFutures: MutableMap<String, Future<*>> = mutableMapOf(),
    val uitaskMap: MutableMap<String, SessionTask> = mutableMapOf()
)