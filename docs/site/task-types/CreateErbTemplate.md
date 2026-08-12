# CreateErbTemplate

**Generate ERB-style templates with AI-assisted schema design and multi-format output.**

`Side-Effect Safe` · `Writing` · `AI-Assisted` · `File Write`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "CreateErbTemplate",
  "template_description": "Invoice template that lists line items with quantity, unit price, and computed totals",
  "main_file": "templates/invoice.erb",
  "output_format": "latex",
  "example_data": "{\"customer\": \"Acme Corp\", \"items\": [{\"name\": \"Widget\", \"qty\": 3, \"price\": 9.99}]}",
  "include_schema": true,
  "features": ["loops", "conditionals", "filters"]
}
```

**Rendered Output**

The UI renders a `TabbedDisplay` with two tabs:

- **Configuration** — a compact markdown summary card showing Description, Format, and whether a Schema preamble is included.
- **Generated Template** — the AI-produced ERB source shown in a fenced code block preview. If `autoFix` is disabled, an **Accept** button footer appears; clicking it writes the file to `main_file` and completes the task. If `autoFix` is enabled, the file is written immediately and the tab shows a "Generated Template" summary with the file path and final content.

A full transcript (context, AI response, completion summary) is streamed to a companion transcript file for audit purposes.

---

## Documentation

### Configuration Table

| Field Name             | Required/Optional | Type            | Description                                                                                   |
|-------------------------|--------------------|------------------|-----------------------------------------------------------------------------------------------|
| `template_description` | Required           | `String?`        | Description of the template to generate, including its purpose and expected data structure.  |
| `main_file`             | Required           | `String`         | Output file path for the generated template (relative to working directory).                 |
| `output_format`         | Optional           | `String`         | Target format for the output (`latex`, `html`, `markdown`, `text`). Defaults to `latex`.      |
| `example_data`          | Optional           | `String?`        | Example data structure in JSON format to help define the schema.                              |
| `include_schema`        | Optional           | `Boolean`        | Whether to include a TypeScript-style schema preamble for data validation. Defaults to `true`.|
| `features`              | Optional           | `List<String>?`  | Specific features to include (e.g. `loops`, `conditionals`, `filters`).                       |

### Dependencies

No hard dependencies on other task types. The task calls `getPriorCode(agent.executionState)` to pull in context/output from upstream tasks in the plan graph, so it can integrate with any prior task that publishes code/context artifacts, but it does not require a specific task type to precede it.

### Token Usage

**Medium** — The system prompt plus format-specific guidelines and user prompt (description, example data, schema instructions, dependency context) form a moderately sized request. Output is typically a single template file of modest length (tens to low hundreds of lines), so total round-trip token usage is moderate rather than low or high.

---

## Config & Process

### Type Configuration (`CreateErbTemplateTaskTypeConfig`)

- `model: ApiChatModel?` — AI model used for generation; falls back to `defaultSmart` if unset.
- `defaultOutputFormat: String` — Default format when execution config omits `output_format` (default `"latex"`).
- `systemPrompt: String` — Base system prompt describing ERB template design principles (schema preambles, error handling, filters, control flow).

### Runtime Configuration (`CreateErbTemplateTaskExecutionConfigData`)

See the Configuration Table above — includes `template_description`, `main_file`, `output_format`, `example_data`, `include_schema`, and `features`.

### Lifecycle

**Initialization**
- Validates required fields (`template_description`, `main_file` non-blank) via `validate()`.
- Resolves the chat interface from `typeConfig.model` or falls back to `defaultSmart`, bound to the task via `getChildClient`.
- Opens a transcript stream and a `TabbedDisplay` for UI rendering.

**Execution**
- Pulls dependency context via `getPriorCode`.
- Assembles a detailed user prompt combining template purpose, output format, example data (if provided), schema instructions (if `include_schema`), requested features, and dependency context.
- Instantiates a `ChatAgent` with the type-config system prompt plus format-specific guidelines (LaTeX/HTML/Markdown/generic).
- Calls `chatAgent.answer(...)` and extracts the template body from a fenced code block in the response (`extractTemplate`).
- Depending on `orchestrationConfig.autoFix`:
  - **Auto mode:** writes the file immediately and completes the task tab.
  - **Manual mode:** shows a preview with an accept button; writing occurs only on user approval via `acceptButtonFooter`.
- Blocks on a `Semaphore` until the write completes (auto) or the user approves (manual).

**Error Handling**
- Any thrown exception is logged via `task.error(e)`, the SLF4J logger, and written to the transcript (triple-log rule), then rethrown to propagate failure to the orchestrator.
- The transcript stream is flushed in a `finally` block regardless of success or failure.

---

## Integration

### Registering in `OrchestrationConfig`

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings
    taskTypes = listOf(
        CreateErbTemplateTask.CreateErbTemplate
    ),
    taskTypeConfigs = mapOf(
        CreateErbTemplateTask.CreateErbTemplate.name to CreateErbTemplateTask.CreateErbTemplateTaskTypeConfig(
            model = ApiChatModel.GPT4o,
            defaultOutputFormat = "latex"
        )
    )
)
```

### Prompt Segment (Planner-Facing)

```text
CreateErbTemplate - Generate ERB-style templates for document generation
  * Specify the template purpose and expected data structure
  * Define the output format (latex, html, markdown, text)
  * Optionally provide example data to help define the schema
  * Supports variable interpolation, loops, conditionals, and filters
  * Can include TypeScript-style schema preambles for validation
```

### System Prompt (excerpt, sent to the model)

```text
You are an expert template designer specializing in ERB-style templates.
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
7. Follow the target format's conventions and best practices
```

Format-specific guidance (e.g. LaTeX escaping/`markdown` filter usage, HTML semantic structure, Markdown code fencing) is appended dynamically based on `output_format`.