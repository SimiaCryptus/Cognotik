package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.plan.tools.TaskType

class TaskContextYamlDescriber(
    val orchestrationConfig: OrchestrationConfig
) : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "cognotik.actions"
) {
    override val includeMethods: Boolean get() = false

    override fun getEnumValues(clazz: Class<*>): List<String> {
        return if (clazz == TaskType::class.java) {
            orchestrationConfig.taskSettings.keys.toList()
        } else {
            super.getEnumValues(clazz)
        }
    }
}