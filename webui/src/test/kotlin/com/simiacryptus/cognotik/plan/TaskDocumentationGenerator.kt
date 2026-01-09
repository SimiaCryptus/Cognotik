package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File

object TaskDocumentationGenerator {
    val log = LoggerFactory.getLogger(TaskDocumentationGenerator::class.java)
    @JvmStatic
    fun main(args: Array<String>) {
        log.info("Starting TaskDocumentationGenerator...")
        val taskTypes = TaskType.values()
        log.info("Found ${taskTypes.size} task types.")
        val grouped = taskTypes.groupBy { it.category }.toSortedMap()
        
        val sb = StringBuilder()
        sb.append("# Task Types\n\n")
        sb.append("This document lists all available task types in the system, categorized and alphabetized.\n\n")
        
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

        // Attempt to mock OrchestrationConfig to generate prompt segments
        // This requires Mockito to be in the classpath
        val orchestrationConfig = OrchestrationConfig()
        grouped.forEach { (category, tasks) ->
            log.info("Processing category: $category")
            sb.append("## $category\n\n")
            tasks.sortedBy { it.name }.forEach { taskType ->
                log.info("Processing task: ${taskType.name}")
                sb.append("### ${taskType.name}")
                sb.append("\n\n")

                if (!taskType.description.isNullOrBlank()) {
                    sb.append(taskType.description)
                    sb.append("\n\n")
                }
                
                if (!taskType.tooltipHtml.isNullOrBlank()) {
                    sb.append(taskType.tooltipHtml.trimIndent())
                    sb.append("\n\n")
                }
                
                // Prompt Segment
                if (orchestrationConfig != null) {
                    try {
                        val taskConfig = taskType.executionConfigClass.getDeclaredConstructor().newInstance()
                        val constructor = taskType.getConstructor()
                        @Suppress("UNCHECKED_CAST")
                        val taskInstance = constructor(orchestrationConfig, taskConfig)
                        val prompt = taskInstance.promptSegment()
                        if (prompt.isNotBlank()) {
                            sb.append("#### Planner Prompt Segment\n\n")
                            sb.append("```text\n$prompt\n```\n\n")
                        }
                    } catch (e: Throwable) {
                        log.warn("Error generating prompt for task ${taskType.name}", e)
                        // Ignore errors during prompt generation (e.g. if task requires specific config or real OrchestrationConfig)
                    }
                }

                // Default Execution Config
                try {
                    val execConfig = taskType.executionConfigClass.getDeclaredConstructor().newInstance()
                    val json = mapper.writeValueAsString(execConfig)
                    sb.append("#### Default Execution Configuration\n\n")
                    sb.append("```json\n$json\n```\n\n")
                } catch (e: Throwable) {
                    log.warn("Error generating execution config for task ${taskType.name}", e)
                    sb.append("<!-- Could not generate execution config: ${e.message} -->\n\n")
                }

                // Default Type Config
                try {
                    val typeConfig = taskType.newSettings() ?: taskType.taskSettingsClass.getDeclaredConstructor().newInstance()
                    val json = mapper.writeValueAsString(typeConfig)
                    sb.append("#### Default Type Configuration\n\n")
                    sb.append("```json\n$json\n```\n\n")
                } catch (e: Throwable) {
                    log.warn("Error generating type config for task ${taskType.name}", e)
                    sb.append("<!-- Could not generate type config: ${e.message} -->\n\n")
                }
                
                sb.append("---\n\n")
            }
        }
        
        val docsDir = File("docs")
        if (!docsDir.exists()) docsDir.mkdirs()
        val outputFile = File(docsDir, "TaskTypes.md")
        outputFile.writeText(sb.toString())
        log.info("Documentation generated at ${outputFile.absolutePath}")
    }
}