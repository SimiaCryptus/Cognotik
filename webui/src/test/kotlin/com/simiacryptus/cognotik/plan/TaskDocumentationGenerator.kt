package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.simiacryptus.cognotik.describe.Description
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
        
        
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)

        // Attempt to mock OrchestrationConfig to generate prompt segments
        // This requires Mockito to be in the classpath
        val orchestrationConfig = OrchestrationConfig()
        val docsDir = File("docs")
        if (!docsDir.exists()) docsDir.mkdirs()
        val siteDir = File("site/cognotik.com")
        if (!siteDir.exists()) siteDir.mkdirs()

        grouped.forEach { (category, tasks) ->
            log.info("Processing category: $category")
            val sb = StringBuilder()
            sb.append("# $category\n\n")
            tasks.sortedBy { it.name }.forEach { taskType ->
                log.info("Processing task: ${taskType.name}")
                sb.append("## ${taskType.name}")
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
                // Generate HTML Product Page
                generateHtmlPage(taskType, siteDir, mapper)
            }
            val outputFile = File(docsDir, "TaskTypes_${category}.md")
            outputFile.writeText(sb.toString())
            log.info("Documentation generated at ${outputFile.absolutePath}")
        }
        
    }
    private fun generateHtmlPage(taskType: TaskType<*, *>, outputDir: File, mapper: ObjectMapper) {
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${taskType.name} - Cognotik</title>
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.2.3/dist/css/bootstrap.min.css">
                <style>
                    body { padding: 20px; }
                    .task-header { margin-bottom: 30px; }
                    .task-description { font-size: 1.2em; margin-bottom: 20px; }
                    .config-table { margin-top: 20px; }
                </style>
            </head>
            <body>
            <div class="container">
                <div class="task-header">
                    <h1>${taskType.name}</h1>
                    <span class="badge bg-primary">${taskType.category}</span>
                </div>
                <div class="row">
                    <div class="col-md-8">
                        <div class="task-description">
                            ${taskType.description ?: ""}
                        </div>
                        <div class="task-tooltip">
                            ${taskType.tooltipHtml ?: ""}
                        </div>
                        <h3>Configuration</h3>
                        <table class="table table-striped config-table">
                            <thead>
                                <tr>
                                    <th>Parameter</th>
                                    <th>Type</th>
                                    <th>Description</th>
                                </tr>
                            </thead>
                            <tbody>
        """.trimIndent())
        try {
            val fields = taskType.executionConfigClass.declaredFields
            fields.sortBy { it.name }
            fields.forEach { field ->
                if (field.name != "Companion" && !field.name.startsWith("$")) {
                    val desc = field.getAnnotation(Description::class.java)?.value ?: ""
                    sb.append("<tr><td><code>${field.name}</code></td><td>${field.type.simpleName}</td><td>$desc</td></tr>")
                }
            }
        } catch (e: Exception) {
            log.warn("Error generating config table for ${taskType.name}", e)
        }
        sb.append("""
                            </tbody>
                        </table>
                    </div>
                    <div class="col-md-4">
                        <div class="card">
                            <div class="card-header">Example Configuration</div>
                            <div class="card-body">
                                <pre><code class="language-json">
        """.trimIndent())
        try {
            val execConfig = taskType.executionConfigClass.getDeclaredConstructor().newInstance()
            sb.append(mapper.writeValueAsString(execConfig))
        } catch (e: Exception) {
            sb.append("{}")
        }
        sb.append("""
                                </code></pre>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            </body>
            </html>
        """.trimIndent())
        File(outputDir, "${taskType.name}.html").writeText(sb.toString())
    }
}