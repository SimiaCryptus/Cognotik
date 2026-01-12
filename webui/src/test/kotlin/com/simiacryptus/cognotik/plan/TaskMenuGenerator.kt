package com.simiacryptus.cognotik.plan

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.util.toJson
import org.slf4j.LoggerFactory
import java.io.File

object TaskMenuGenerator {
    private val log = LoggerFactory.getLogger(TaskMenuGenerator::class.java)
    private val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .enable(SerializationFeature.INDENT_OUTPUT)

    @JvmStatic
    fun main(args: Array<String>) {
        log.info("Starting TaskMenuGenerator...")
        val taskTypes = TaskType.values()
        val grouped = taskTypes.groupBy { it.category }
        
        val outputDir = File("site/cognotik.com/assets/data")
        if (!outputDir.exists()) outputDir.mkdirs()

        val menuStructure = mutableListOf<Map<String, Any>>()

        grouped.toSortedMap().forEach { (category, tasks) ->
            val filename = "${category.lowercase().replace(" ", "_")}.json"
            val fileTasks = tasks.sortedBy { it.name }.map { task ->
                mapOf(
                    "id" to task.name.lowercase(),
                    "label" to task.name.replace(Regex("([a-z])([A-Z]+)"), "$1 $2"),
                    "url" to "${task.taskClass.simpleName}.html",
                    "description" to buildString {
                        appendLine(task.description ?: "")
                        appendLine()
                        appendLine(task.tooltipHtml ?: "")
                    },
                    "code" to getGitHubLink(task.taskClass)
                )
            }
            
            mapper.writeValue(File(outputDir, filename), fileTasks)
            log.info("Generated $filename with ${fileTasks.size} tasks")
            
            menuStructure.add(mapOf(
                "label" to category,
                "type" to "dropdown",
                "src" to filename
            ))
        }

        val tasksmenuFile = File(outputDir, "tasks.json")
        tasksmenuFile.writeText("""
{
  "siteName": "Cognotik",
  "navigation": [
    {
      "label": "IDE",
      "url": "intellij-plugin.html",
      "type": "link",
      "code": "https://github.com/SimiaCryptus/Cognotik/tree/main/intellij"
    },
    {
      "label": "Modes",
      "type": "dropdown",
      "url": "TaskPlanning.html",
      "src": "modes.json",
      "code": "https://github.com/SimiaCryptus/Cognotik/tree/main/webui/src/main/kotlin/com/simiacryptus/cognotik/plan"
    },
    {
      "label": "Tasks",
      "type": "submenu",
      "items": ${menuStructure.toJson().indent("      ")}
    }
  ]
}
        """)
        log.info("Generated tasks_menu.json")
    }

    private fun getGitHubLink(clazz: Class<*>): String {
        val base = "https://github.com/SimiaCryptus/Cognotik/tree/main/webui/src/main/kotlin/"
        val path = clazz.name.replace(".", "/") + ".kt"
        return base + path
    }
}

