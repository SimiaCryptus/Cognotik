package com.simiacryptus.cognotik.plan

import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber

class TaskContextYamlDescriber(
    val orchestrationConfig : OrchestrationConfig
) : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "aicoder.actions"
) {
    override val includeMethods: Boolean get() = false

    override fun getEnumValues(clazz: Class<*>): List<String> {
        return if (clazz == TaskType::class.java) {
            orchestrationConfig.taskSettings.filter { it.value.enabled }.map { it.key }
        } else {
            super.getEnumValues(clazz)
        }
    }
}