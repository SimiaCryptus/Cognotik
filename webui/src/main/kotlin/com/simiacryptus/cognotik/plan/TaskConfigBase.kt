package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.simiacryptus.jopenai.describe.Description

@JsonTypeIdResolver(TaskConfigBase.PlanTaskTypeIdResolver::class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "task_type")
open class TaskConfigBase(
    @Description("An enumeration indicating the type of task to be executed. Must be a single value from the TaskType enum.")
    val task_type: String? = null,
    @Description("A brief user-facing description of the task")
    var task_description: String? = null,
    @Description("A list of IDs of tasks that must be completed before this task can be executed. This defines upstream dependencies ensuring proper task order and information flow.")
    var task_dependencies: MutableList<String>? = null,
    @Description("Ignore.")
    var state: AbstractTask.TaskState? = null
) {

    class PlanTaskTypeIdResolver : TypeIdResolverBase() {
        override fun idFromValue(value: Any) = when (value) {
            is TaskConfigBase -> if (value.task_type != null) {
                value.task_type
            } else {
                throw IllegalArgumentException("Unknown task type")
            }

            else -> throw IllegalArgumentException("Unexpected value type: ${value.javaClass}")
        }

        override fun idFromValueAndType(value: Any, suggestedType: Class<*>): String {
            return idFromValue(value)
        }

        override fun typeFromId(context: DatabindContext, id: String): JavaType {
            val taskType = TaskType.valueOf(id.replace(" ", ""))
            val subType = context.constructType(taskType.taskDataClass)
            return subType
        }

        override fun getMechanism(): JsonTypeInfo.Id {
            return JsonTypeInfo.Id.CUSTOM
        }
    }
}