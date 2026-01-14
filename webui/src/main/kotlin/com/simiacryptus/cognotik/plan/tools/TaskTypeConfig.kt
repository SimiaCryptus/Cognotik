package com.simiacryptus.cognotik.plan.tools

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.simiacryptus.cognotik.platform.model.ApiChatModel

@JsonTypeIdResolver(TaskTypeConfig.PlanTaskTypeIdResolver::class)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    property = "task_type",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = true
)
open class TaskTypeConfig(
    var task_type: String? = null,
    name: String? = task_type,
    var model: ApiChatModel? = null
) {
    var name: String? = name
        get() = field ?: task_type

    class PlanTaskTypeIdResolver : TypeIdResolverBase() {
        override fun idFromValue(value: Any): String? {
            return when (value) {
                is TaskTypeConfig -> value.task_type ?: return null
                else -> throw IllegalArgumentException("Unexpected value type: ${value.javaClass}")
            }
        }

        override fun idFromValueAndType(value: Any, suggestedType: Class<*>): String? {
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

fun TaskType<*, *>.newSettings(): TaskTypeConfig? =
    taskSettingsClass.declaredConstructors.firstOrNull { it.parameters.isEmpty() }?.let {
        it.isAccessible = true
        val defaultConfig = it.newInstance() as TaskTypeConfig
        defaultConfig.task_type = name
        defaultConfig.name = null
        defaultConfig.model = null
        defaultConfig
    }
