package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.util.concurrent.Semaphore

class CreateErbTemplateTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: CreateErbTemplateTaskExecutionConfigData?
) :
  AbstractTask<CreateErbTemplateTask.CreateErbTemplateTaskExecutionConfigData, CreateErbTemplateTask.CreateErbTemplateTaskTypeConfig>(
    orchestrationConfig,
    planTask
  ) {

  class CreateErbTemplateTaskExecutionConfigData(
    @Description("Description of the template to generate, including its purpose and expected data structure")
    var template_description: String? = null,
    @Description("The output file path for the generated template (relative to working directory)")
    var output_file: String? = null,
    @Description("The target format for the template output (e.g., 'latex', 'html', 'markdown', 'text')")
    var output_format: String = "latex",
    @Description("Example data structure in JSON format to help define the schema")
    var example_data: String? = null,
    @Description("Whether to include a TypeScript-style schema preamble for data validation")
    var include_schema: Boolean = true,
    @Description("List of specific features to include (e.g., 'loops', 'conditionals', 'filters')")
    var features: List<String>? = null,
    task_description: String? = null,
    task_dependencies: MutableList<String>? = null,
    state: TaskState? = null
  ) : TaskExecutionConfig(
    task_type = CreateErbTemplate.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (template_description.isNullOrBlank()) {
        return "Template description is required"
      }
      if (output_file.isNullOrBlank()) {
        return "Output file path is required"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  class CreateErbTemplateTaskTypeConfig(
    @Description("The AI model to use for template generation")
    model: ApiChatModel? = null,
    @Description("Default output format when not specified in execution config")
    var defaultOutputFormat: String = "latex",
    @Description("System prompt template for the AI")
    var systemPrompt: String = DEFAULT_SYSTEM_PROMPT
  ) : TaskTypeConfig(model = model) {
    companion object {
      const val DEFAULT_SYSTEM_PROMPT = """You are an expert template designer specializing in ERB-style templates.
Your task is to create well-structured, maintainable templates that follow best practices.

When creating templates:
1. Use clear, descriptive variable names
2. Include appropriate schema preambles for data validation using the following format:
   <%#
   @type TemplateData = {
     fieldName: string;
     optionalField?: number;
     nestedArray: {
       subField: string;
     }[];
   };
   %>
3. Implement proper error handling with default values
4. Use filters appropriately for data transformation
5. Structure control flow (loops, conditionals) for readability
6. Add comments to explain complex logic
7. Follow the target format's conventions and best practices"""
    }
  }

  override fun promptSegment() = """
CreateErbTemplate - Generate ERB-style templates for document generation
  * Specify the template purpose and expected data structure
  * Define the output format (latex, html, markdown, text)
  * Optionally provide example data to help define the schema
  * Supports variable interpolation, loops, conditionals, and filters
  * Can include TypeScript-style schema preambles for validation
""".trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val typeConfig = typeConfig ?: CreateErbTemplateTaskTypeConfig()
    val chatInterface =
      (typeConfig.model?.let<ApiChatModel, ChatInterface> { this.orchestrationConfig.instance(it) }
        ?: defaultSmart).getChildClient(task)

    val semaphore = Semaphore(0)
    val transcript = task.newFileOutputStream(transcriptFile())
    val tabs = TabbedDisplay(task)

    try {
      transcript?.write("# Create ERB Template Task Transcript\n\n".toByteArray())

      // 1. Prepare Context
      val dependencyContext = getPriorCode(agent.executionState)
      val templateDesc = executionConfig?.template_description ?: ""
      val outputFormat = executionConfig?.output_format ?: typeConfig.defaultOutputFormat
      val exampleData = executionConfig?.example_data ?: ""
      val includeSchema = executionConfig?.include_schema ?: true
      val features = executionConfig?.features?.joinToString(", ") ?: "all available"

      // 2. Log Context to Transcript
      transcript?.write(
        """
## Context Data
<details>
<summary>Task Configuration</summary>

### Template Description
$templateDesc

### Output Format
$outputFormat

### Example Data
${exampleData.ifBlank { "Not provided" }}

### Include Schema
$includeSchema

### Requested Features
$features

### Dependencies
${dependencyContext.ifBlank { "None" }}
</details>

            """.toByteArray()
      )

      val contextTab = tabs.newTask("Configuration")
      contextTab.add(
        """
# Template Configuration
**Description:** $templateDesc
**Format:** $outputFormat
**Schema:** ${if (includeSchema) "Yes" else "No"}
            """.renderMarkdown()
      )
      contextTab.complete()

      // 3. Build the prompt
      val userPrompt = buildString {
        appendLine("Create an ERB-style template with the following requirements:")
        appendLine()
        appendLine("## Template Purpose")
        appendLine(templateDesc)
        appendLine()
        appendLine("## Output Format")
        appendLine(outputFormat)
        appendLine()
        if (exampleData.isNotBlank()) {
          appendLine("## Example Data Structure")
          appendLine("```json")
          appendLine(exampleData)
          appendLine("```")
          appendLine()
        }
        if (includeSchema) {
          appendLine("## Schema Requirements")
          appendLine("Include a TypeScript-style schema preamble at the beginning of the template using this format:")
          appendLine("```erb")
          appendLine("<%#")
          appendLine("@type TemplateData = {")
          appendLine("  fieldName: string;")
          appendLine("  optionalField?: type;")
          appendLine("  arrayField: {")
          appendLine("    nestedField: string;")
          appendLine("  }[];")
          appendLine("};")
          appendLine("%>")
          appendLine("```")
          appendLine("The schema should define all expected fields with their types using TypeScript syntax.")
          appendLine("Use `?` suffix for optional fields and `[]` suffix for arrays.")
          appendLine()
        }
        appendLine("## Features to Include")
        appendLine(features)
        appendLine()
        if (dependencyContext.isNotBlank()) {
          appendLine("## Additional Context from Dependencies")
          appendLine(dependencyContext)
        }
      }

      val chatAgent = ChatAgent(
        name = "CreateErbTemplate",
        prompt = typeConfig.systemPrompt + getFormatSpecificGuidelines(outputFormat),
        model = chatInterface,
        temperature = this.orchestrationConfig.temperature,
      )

      val mainTask = tabs.newTask("Generated Template")
      mainTask.add("Generating template...".renderMarkdown())

      // 4. Execute AI
      val templateResult = chatAgent.answer(
        (messages + listOf(userPrompt)).filter { it.isNotBlank() }
      )

      // 5. Log Response to Transcript
      transcript?.write(
        """
## AI Response
<details>
<summary>Generated Template</summary>

$templateResult
</details>

            """.toByteArray()
      )

      // 6. Extract template from response
      val extractedTemplate = extractTemplate(templateResult)

      val autoFix = orchestrationConfig.autoFix
      val outputFile = executionConfig?.output_file ?: "template.erb"
      val outputPath = agent.root.resolve(outputFile)

      if (autoFix) {
        // Auto-apply: write the file directly
        outputPath.toFile().parentFile?.mkdirs()
        outputPath.toFile().writeText(extractedTemplate)
        transcript?.write("\n**Auto-applied: Template written to $outputFile**\n".toByteArray())
        mainTask.complete(
          """
## Generated Template
File: `$outputFile`

```
$extractedTemplate
```
                """.renderMarkdown()
        )
        semaphore.release()
      } else {
        // Manual mode: show preview and approval button
        mainTask.add(
          """
## Generated Template Preview
File: `$outputFile`

```
$extractedTemplate
```
                """.renderMarkdown()
        )

        mainTask.complete(acceptButtonFooter(mainTask.ui) {
          outputPath.toFile().parentFile?.mkdirs()
          outputPath.toFile().writeText(extractedTemplate)
          transcript?.write("\n**User approved: Template written to $outputFile**\n".toByteArray())
          task.complete()
          semaphore.release()
        })
      }

      transcript?.flush()
      semaphore.acquire()

      // 7. Finalize
      val summary = "Template created: $outputFile"
      transcript?.write("\n## Completion\n$summary\n".toByteArray())
      resultFn(summary)

    } catch (e: Throwable) {
      // Triple Log Rule
      task.error(e)
      log.error("Error in CreateErbTemplateTask", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      throw e
    } finally {
      transcript?.flush()
    }
  }

  private fun getFormatSpecificGuidelines(format: String): String = when (format.lowercase()) {
    "latex" -> """

## LaTeX-Specific Guidelines
- Use the `escape` filter for user-provided text to handle special LaTeX characters
- Use the `markdown` filter to convert markdown content to LaTeX
- Structure the document with proper LaTeX commands (\documentclass, \begin{document}, etc.)
- Use appropriate LaTeX packages for the content type
"""

    "html" -> """

## HTML-Specific Guidelines
- Use the `escape` filter for user-provided text to prevent XSS
- Structure with proper HTML5 semantic elements
- Include appropriate meta tags and styling hooks
"""

    "markdown" -> """

## Markdown-Specific Guidelines
- Use proper markdown syntax for headers, lists, and formatting
- Consider GitHub-flavored markdown extensions if appropriate
- Use code blocks with language hints for syntax highlighting
"""

    else -> """

## General Guidelines
- Use appropriate escaping for the target format
- Structure content logically with clear sections
"""
  }

  private fun extractTemplate(response: String): String {
    // Try to extract template from code blocks
    val codeBlockPattern =
      Regex("""```(?:erb|template|latex|html|markdown|text)?\s*\n(.*?)```""", RegexOption.DOT_MATCHES_ALL)
    val match = codeBlockPattern.find(response)
    return match?.groupValues?.get(1)?.trim() ?: response.trim()
  }

  companion object {
    private val log = LoggerFactory.getLogger(CreateErbTemplateTask::class.java)

    @JvmStatic
    val CreateErbTemplate = TaskType(
      "CreateErbTemplate",
      "Writing",
      CreateErbTemplateTask::class.java,
      CreateErbTemplateTaskExecutionConfigData::class.java,
      CreateErbTemplateTaskTypeConfig::class.java,
      "Generate ERB-style templates for dynamic document generation",
      """
                Creates ERB-style templates with AI assistance for generating dynamic documents.
                <ul>
                    <li>Supports variable interpolation with <%= expression %></li>
                    <li>Includes control structures (for loops, if/else conditionals)</li>
                    <li>Provides built-in filters (escape, markdown, upper, lower, join, default)</li>
                    <li>Optional TypeScript-style schema preambles for data validation</li>
                    <li>Supports multiple output formats (LaTeX, HTML, Markdown, text)</li>
                    <li>Generates well-documented, maintainable templates</li>
                    <li>Handles complex nested data structures</li>
                </ul>
            """
    )
  }
}