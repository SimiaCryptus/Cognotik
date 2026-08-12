# RenderErbTemplate

**Render ERB-style templates with dynamic data**

`Side-Effect Safe` · `Writing` · `File Output`

Renders an ERB-style template (variable interpolation, loops, conditionals, filters) against a JSON data object, optionally derived automatically from related source files via an LLM, and writes the result to an output file.

---

## Reality Check

### Input Configuration

```json
{
  "task_type": "RenderErbTemplate",
  "task_description": "Generate release notes document from changelog data",
  "template_file": "templates/release_notes.erb",
  "data": {
    "version": "2.3.0",
    "date": "2024-06-01",
    "changes": [
      "Added ERB template rendering task",
      "Fixed schema validation edge case"
    ]
  },
  "related_files": [],
  "main_file": "output/release_notes_2.3.0.md"
}
```

### Rendered Output (UI)

The task streams a live transcript panel showing:

- **Configuration** block (template path, strict-validation flag)
- Collapsible `<details>` section with the raw **Template Content** (`erb` code block)
- Collapsible **Template Schema** section rendering the inferred TypeScript interface (if the template declares one)
- Collapsible **Input Data** section with the resolved JSON payload
- Collapsible **Rendered Output** section with the full rendered text
- A final **completion card** showing a truncated preview (max 2000 chars) of the rendered content plus a bolded `**Output saved to:** <path>` line
- On failure, an inline error block with the stack trace and any ERB schema validation errors (path + message per field)

---

## Documentation

### Configuration

| Field Name       | Type                    | Required/Optional | Description |
|------------------|--------------------------|--------------------|--------------|
| `data`           | `Map<String, Any?>`     | Optional           | JSON data object used for template rendering; keys must match template variables. |
| `related_files`  | `List<String>`          | Optional           | Source files used to auto-generate `data` via an LLM when `data` is not supplied. If exactly one `.json` file is present it is parsed directly; otherwise all files are concatenated and passed to an LLM agent. |
| `template_file`  | `String`                | Optional           | Overrides the type-config `template_file` path (must resolve to an `.erb` file relative to the working directory). |
| `main_file`      | `String` (inherited)     | Required           | Output path the rendered content is written to. |
| `task_description` | `String` (inherited)  | Optional           | Standard task description field. |
| `task_dependencies` | `List<String>` (inherited) | Optional        | Standard task dependency list. |

### Dependencies

- No hard dependency on other `Task` classes, but `related_files`-based data generation uses `ParsedAgent` (a general-purpose structured-output LLM agent) to infer JSON data when explicit `data` isn't provided.
- Relies on `ErbTemplateEngine` for schema extraction, rendering, and strict validation.

### Token Usage

**Medium** — No LLM call is made if `data` is supplied directly. When `related_files` is used without a single JSON file, a full agent call (with schema hint + concatenated file contents) is required to synthesize the data object, which can be sizable depending on related file volume.

---

## Config & Process

### Type Configuration (`RenderErbTemplateTaskTypeConfig`)

| Field | Type | Description |
|---|---|---|
| `template_file` | `String?` | Default path to the ERB template file (relative to working directory), used unless overridden per-execution. |
| `strict_validation` | `Boolean` (default `false`) | Enables strict schema validation during rendering. |
| `model` | `ApiChatModel?` | Model used for generating missing data fields when `related_files` requires LLM inference. |

### Runtime Configuration (`RenderErbTemplateTaskExecutionConfig`)

Fields: `data`, `related_files`, `template_file` (override), plus inherited `task_description`, `task_dependencies`, `main_file`.

### Lifecycle

1. **Initialization**
   - Resolves template path from execution config, falling back to type config; throws if neither is set.
   - Validates the template file exists on disk.
   - Loads template content and logs it to the transcript.
   - Initializes `ErbTemplateEngine`, applying `strict_validation` from type config.
   - Extracts the template's declared schema (if any) and logs it as a TypeScript interface.

2. **Execution**
   - Resolves `data`: uses `executionConfig.data` if present; otherwise, if `related_files` is set, either parses a single `.json` file directly or invokes a `ParsedAgent` (using `defaultFast` model) with a schema-hint prompt to synthesize a JSON object from concatenated related file contents.
   - Renders the template via `ErbTemplateEngine.render()`.
   - Writes rendered content to `main_file` (creating parent directories as needed).
   - Emits a truncated preview (2000-char cap) as the completed task result.
   - Returns a summary string (template path, data field names, output path, full rendered content) via `resultFn`.

3. **Error Handling**
   - `ErbTemplateEngine.TemplateValidationException` is caught specifically to log field-level validation errors (path + message) to the transcript before re-throwing.
   - All other throwables follow the "Triple Log Rule": logged via `task.error()`, SLF4J logger, and written to the transcript stack trace block — then re-thrown.
   - `finally` block always flushes the transcript stream, regardless of outcome.

---

## Integration

### Registering in an `OrchestrationConfig`

```kotlin
import com.simiacryptus.cognotik.plan.tools.writing.RenderErbTemplateTask

val orchestrationConfig = OrchestrationConfig(
    // ...other configuration...
    taskSettings = mapOf(
        RenderErbTemplateTask.RenderErbTemplate.name to RenderErbTemplateTask.RenderErbTemplateTaskTypeConfig(
            template_file = "templates/release_notes.erb",
            strict_validation = true,
            model = ApiChatModel.GPT4o
        )
    )
)
```

### Prompt Segment (injected into planning LLM)

```
RenderErbTemplate - Render ERB-style templates with dynamic data
  * Provide template_data as a JSON object with keys matching template variables
  * Template file is configured in the task type settings
  * Supports variable interpolation, loops, conditionals, and filters
  * Can optionally write output to a file
  * Use for generating documents, reports, or any templated content
```

### Data-Inference Prompt (used only when `related_files` supplies data)

```
You are a helpful assistant that generates data for ERB templates based on the content of related files.
Given the following content from related files, extract key information and generate a JSON object that can be used as template data.

The template expects data conforming to the following TypeScript interface:
```typescript
<inferred schema>
```
Ensure the JSON object you generate has keys and value types that match this schema.
All required (non-optional) fields must be populated with appropriate values extracted from the related files.

Use the content to infer any relevant details that could be useful for rendering the template.
```