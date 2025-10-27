package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskContextYamlDescriber
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.util.ValidatedObject

data class Tasks(
  val tasks: MutableList<TaskExecutionConfig>? = null
) : ValidatedObject {
  override fun validate(): String? {
    val errors = mutableListOf<String>()
    if (tasks == null || tasks.isEmpty()) {
      errors.add("Tasks list cannot be null or empty.")
    } else {
      tasks.forEachIndexed { index, task ->
        if (task is ValidatedObject) task.validate()?.let { errors.add(it) }
      }
    }
    return errors.ifEmpty { null }?.joinToString("; ")
  }

  companion object {
    fun initDescriber(orchestrationConfig: OrchestrationConfig, describer: TaskContextYamlDescriber) {
      describer.clearSubTypes(TaskExecutionConfig::class.java)
      TaskType.getAvailableTaskTypes(orchestrationConfig).forEach { taskType ->
        describer.registerSubType(TaskExecutionConfig::class.java, taskType.executionConfigClass)
      }
    }
  }
}