package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

class TaskSettingsMapDeserializer : JsonDeserializer<MutableMap<String, TaskTypeConfig>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MutableMap<String, TaskTypeConfig> {
        val codec = p.codec as ObjectMapper
        val node: JsonNode = codec.readTree(p)
        val result = mutableMapOf<String, TaskTypeConfig>()
        if (node.isObject) {
            node.fields().forEach { (key, valueNode) ->
                if (valueNode.isObject) {
                    // Add/overwrite the task_type field in the value node
                    // This ensures the PlanTaskTypeIdResolver in TaskSettingsBase can find the type ID
                    (valueNode as ObjectNode).put("task_type", key)
                    try {
                        val taskSettingsEntry = codec.treeToValue(valueNode, TaskTypeConfig::class.java)
                        if (taskSettingsEntry != null) {
                            result[key] = taskSettingsEntry
                        } else {
                            // Log or handle error: Deserialization returned null
                            ctxt.reportInputMismatch(
                                TaskTypeConfig::class.java,
                                "Failed to deserialize TaskSettingsBase for key '$key', got null"
                            )
                        }
                    } catch (e: Exception) {
                        // Log or handle error: Deserialization threw an exception
                        ctxt.reportInputMismatch(
                            TaskTypeConfig::class.java,
                            "Failed to deserialize TaskSettingsBase for key '$key': ${e.message}"
                        )
                    }
                } else {
                    // Log or handle error: Value is not an object
                    ctxt.reportInputMismatch(
                        Map::class.java,
                        "Value for key '$key' in taskSettings is not a JSON object, but ${valueNode.nodeType}"
                    )
                }
            }
        } else {
            // Log or handle error: taskSettings is not a JSON object
            ctxt.reportInputMismatch(Map::class.java, "taskSettings is not a JSON object, but ${node.nodeType}")
        }
        return result
    }
}