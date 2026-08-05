package com.simiacryptus.cognotik.plan.tools.writing

import com.google.gson.Gson
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.util.ErbTemplateEngine
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.jsonCast
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory.getLogger

class RenderErbTemplateTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: RenderErbTemplateTaskExecutionConfig?
) :
  AbstractTask<RenderErbTemplateTask.RenderErbTemplateTaskExecutionConfig, RenderErbTemplateTask.RenderErbTemplateTaskTypeConfig>(
    orchestrationConfig,
    planTask
  ) {

  class RenderErbTemplateTaskExecutionConfig(
    @Description("JSON data object to be used for template rendering. Keys should match template variables.")
    var data: Map<String, Any?>? = null,
    var related_files: List<String>? = null,
    @Description("Optional: Override the template file path from type config (*.erb)")
    var template_file: String? = null,
    task_description: String? = null,
    task_dependencies: MutableList<String>? = null,
    state: TaskState? = null
  ) : TaskExecutionConfig(
    task_type = RenderErbTemplate.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      return ValidatedObject.validateFields(this)
    }
  }

  class RenderErbTemplateTaskTypeConfig(
    @Description("Path to the ERB template file (relative to working directory)")
    var template_file: String? = null,
    @Description("Whether to enable strict validation of template schema")
    var strict_validation: Boolean = false,
    @Description("Model to use for generating missing data fields")
    model: ApiChatModel? = null,
  ) : TaskTypeConfig(model = model)

  override fun promptSegment() = """
RenderErbTemplate - Render ERB-style templates with dynamic data
  * Provide template_data as a JSON object with keys matching template variables
  * Template file is configured in the task type settings
  * Supports variable interpolation, loops, conditionals, and filters
  * Can optionally write output to a file
  * Use for generating documents, reports, or any templated content
""".trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newSystemFileStream(transcriptFile())
    val gson = Gson()

    try {
      transcript?.write("# ERB Template Rendering Task\n\n".toByteArray())

      // 1. Resolve template file path
      val templatePath = executionConfig?.template_file
        ?: typeConfig?.template_file
        ?: throw RuntimeException("No template file specified in execution config or type config")

      val templateFile = agent.root.resolve(templatePath).toFile()
      if (!templateFile.exists()) {
        throw RuntimeException("Template file not found: $templatePath")
      }

      transcript?.write("## Configuration\n- Template: $templatePath\n- Strict Validation: ${typeConfig?.strict_validation ?: false}\n\n".toByteArray())
      task.add("Loading template from: `$templatePath`".renderMarkdown())

      // 2. Load template content
      val templateContent = templateFile.readText()
      transcript?.write(
        """
<details>
<summary>Template Content</summary>

```erb
$templateContent
```
</details>

            """.toByteArray()
      )

      // 3. Initialize engine early so we can extract schema for data generation guidance
      val engine = ErbTemplateEngine()
      engine.strictValidation = typeConfig?.strict_validation ?: false

      // Extract schema upfront — used both for data generation guidance and for logging
      val schema = engine.extractSchema(templateContent)
      if (schema != null) {
        transcript?.write(
          """
## Template Schema
<details>
<summary>TypeScript Interface</summary>

```typescript
${schema.toTypeScript()}
```
</details>

                """.toByteArray()
        )
      }

      // 4. Prepare data
      val model = defaultFast.getChildClient(task)
      val templateData = parseData(schema, agent, model)
      val jsonData = gson.toJsonTree(templateData).asJsonObject

      transcript?.write(
        """
## Input Data
<details>
<summary>Template Data (JSON)</summary>

```json
${gson.toJson(jsonData)}
```
</details>

            """.toByteArray()
      )

      task.add("Rendering template with ${templateData.size} data fields...".renderMarkdown())

      // 5. Render template
      val renderedContent = try {
        engine.render(templateContent, jsonData)
      } catch (e: ErbTemplateEngine.TemplateValidationException) {
        val errorMsg = "Template validation failed:\n${e.errors.joinToString("\n") { "- ${it.path}: ${it.message}" }}"
        transcript?.write("## Validation Errors\n$errorMsg\n".toByteArray())
        task.error(e)
        throw e
      }

      transcript?.write(
        """
## Rendered Output
<details>
<summary>Full Output</summary>

```
$renderedContent
```
</details>

            """.toByteArray()
      )

      // 6. Write to file
      val outputPath = executionConfig?.let { listOf(it.main_file) }?.firstOrNull()?.ifBlank { null }
        ?: throw RuntimeException("No output file specified in execution config")
      val outputFile = agent.root.resolve(outputPath).toFile()
      outputFile.parentFile?.mkdirs()
      outputFile.writeText(renderedContent)
      transcript?.write("\n## Output File\nWritten to: $outputPath\n".toByteArray())
      task.add("Output written to: `$outputPath`".renderMarkdown())

      // 7. Display result
      val preview = if (renderedContent.length > 2000) {
        renderedContent.take(2000) + "\n\n... (truncated, ${renderedContent.length} total characters)"
      } else {
        renderedContent
      }

      task.complete(
        """
## Rendered Template

```
$preview
```

${if (!outputPath.isNullOrBlank()) "\n**Output saved to:** `$outputPath`" else ""}
            """.renderMarkdown()
      )

      // 8. Return result for downstream tasks
      val summary = buildString {
        appendLine("Template rendered successfully.")
        appendLine("- Template: $templatePath")
        appendLine("- Data fields: ${templateData.keys.joinToString(", ")}")
        if (!outputPath.isNullOrBlank()) {
          appendLine("- Output file: $outputPath")
        }
        appendLine("\n### Rendered Content:\n$renderedContent")
      }
      resultFn(summary)

    } catch (e: Throwable) {
      // Triple Log Rule
      task.error(e)
      log.error("Error in RenderErbTemplateTask", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      throw e
    } finally {
      transcript?.flush()
    }
  }

  private fun parseData(
    schema: ErbTemplateEngine.TypeSchema?,
    agent: TaskOrchestrator,
    model: ChatInterface
  ): Map<String, Any?> {
    var templateData = executionConfig?.data ?: emptyMap()
    if (templateData.isEmpty() && !executionConfig?.related_files.isNullOrEmpty()) {
      // Build a schema hint to guide the agent toward producing correctly-shaped data

      templateData = executionConfig?.related_files?.let {
        if (1 == it.count { it.endsWith(".json") }) {
          val jsonFile = it.first { it.endsWith(".json") }
          val content = agent.root.resolve(jsonFile).toFile().readText()
          try {
            content.jsonCast<Map<String, Any?>>()
          } catch (e: Throwable) {
            log.warn("Failed to parse JSON from $jsonFile, falling back to agent parsing", e)
            null
          }
        } else it.joinToString("\n\n") {
          val content = agent.root.resolve(it).toFile().readText()
          "# $it\n\n```\n$content\n```\n"
        }.let { relatedContent ->
          val schemaHint = if (schema != null) {
            """
The template expects data conforming to the following TypeScript interface:
```typescript
${schema.toTypeScript()}
```
Ensure the JSON object you generate has keys and value types that match this schema.
All required (non-optional) fields must be populated with appropriate values extracted from the related files.
"""
          } else {
            "No explicit schema was found in the template. Infer appropriate keys from the template content and related files."
          }
          val scriptAgent = ParsedAgent(
            resultClass = Map::class.java,
            prompt = """
                      You are a helpful assistant that generates data for ERB templates based on the content of related files.
                      Given the following content from related files, extract key information and generate a JSON object that can be used as template data.
                      
                      $schemaHint
                      
                      Use the content to infer any relevant details that could be useful for rendering the template.
                    """.trimIndent(),
            model = model,
            parsingModel = model,
            temperature = 0.0,
            singleStage = true,
          )
          scriptAgent.answer(listOf(relatedContent)).obj.jsonCast<Map<String, Any?>?>()
        }
      } ?: emptyMap()
    }
    return templateData
  }

  override fun getOutputFile(extension: String) = null

  companion object {
      private val log = getLogger(RenderErbTemplateTask::class.java)

    @JvmStatic
    val RenderErbTemplate = TaskType(
        name = "RenderErbTemplate",
        category = "Writing",
        taskClass = RenderErbTemplateTask::class.java,
        executionConfigClass = RenderErbTemplateTaskExecutionConfig::class.java,
        taskSettingsClass = RenderErbTemplateTaskTypeConfig::class.java,
        description = "Render ERB-style templates with dynamic data for document generation",
        tooltipHtml = """
                        Renders ERB-style templates using a powerful template engine.
                        <ul>
                            <li>Supports variable interpolation with <%= expression %></li>
                            <li>Control structures: for loops and if/else conditionals</li>
                            <li>Built-in filters: escape, markdown, upper, lower, join, default</li>
                            <li>Optional TypeScript-style schema validation</li>
                            <li>Can output to file or return rendered content</li>
                            <li>Useful for generating reports, documents, LaTeX files, etc.</li>
                        </ul>
                    """
    )
  }
}