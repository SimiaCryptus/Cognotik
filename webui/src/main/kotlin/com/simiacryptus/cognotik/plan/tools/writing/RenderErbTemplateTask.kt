package com.simiacryptus.cognotik.plan.tools.writing

import com.google.gson.Gson
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.ErbTemplateEngine
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask

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
    var template_data: Map<String, Any?>? = null,
    @Description("Optional: Override the template file path from type config")
    var template_file_override: String? = null,
    @Description("Optional: Output file path to write the rendered content")
    var output_file: String? = null,
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
      if (template_data == null || template_data!!.isEmpty()) {
        return "template_data must be provided and non-empty"
      }
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
    val transcript = task.transcript()
    val gson = Gson()

    try {
      transcript?.write("# ERB Template Rendering Task\n\n".toByteArray())

      // 1. Resolve template file path
      val templatePath = executionConfig?.template_file_override
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

      // 3. Prepare data
      val templateData = executionConfig?.template_data ?: emptyMap()
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

      // 4. Initialize engine and render
      val engine = ErbTemplateEngine()
      engine.strictValidation = typeConfig?.strict_validation ?: false

      // Extract and log schema if present
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

      // 5. Optionally write to file
      val outputPath = executionConfig?.output_file
      if (!outputPath.isNullOrBlank()) {
        val outputFile = agent.root.resolve(outputPath).toFile()
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(renderedContent)
        transcript?.write("\n## Output File\nWritten to: $outputPath\n".toByteArray())
        task.add("Output written to: `$outputPath`".renderMarkdown())
      }

      // 6. Display result
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

      // 7. Return result for downstream tasks
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
      transcript?.write(
        """

## Error
<details>
<summary>Stack Trace</summary>

```
${e.stackTraceToString()}
```
</details>
            """.toByteArray()
      )
      throw e
    } finally {
      transcript?.flush()
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(RenderErbTemplateTask::class.java)

    @JvmStatic
    val RenderErbTemplate = TaskType(
      "RenderErbTemplate",
      "Writing",
      RenderErbTemplateTask::class.java,
      RenderErbTemplateTaskExecutionConfig::class.java,
      RenderErbTemplateTaskTypeConfig::class.java,
      "Render ERB-style templates with dynamic data for document generation",
      """
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