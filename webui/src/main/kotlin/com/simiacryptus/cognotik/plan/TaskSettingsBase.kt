package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase

import com.simiacryptus.jopenai.models.ChatModel

@JsonTypeIdResolver(TaskSettingsBase.PlanTaskTypeIdResolver::class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, property = "task_type")
open class TaskSettingsBase(
  val task_type: String? = null,
  var enabled: Boolean = false,
  var model: ChatModel? = null
) {
  class PlanTaskTypeIdResolver : TypeIdResolverBase() {
    override fun idFromValue(value: Any) = when (value) {
      is TaskSettingsBase -> if (value.task_type != null) {
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
      val subType = context.constructType(taskType.taskSettingsClass)
      return subType
    }

    override fun getMechanism(): JsonTypeInfo.Id {
      return JsonTypeInfo.Id.CUSTOM
    }
  }
}