# File

## DataIngest

Iteratively parse unstructured logs into structured data

Automates the creation of regex parsers for log files.
<ul>
  <li>Samples data to discover patterns using LLM</li>
  <li>Iteratively targets residual (unparsed) data</li>
  <li>Streams large files to produce JSONL output</li>
  <li>Generates an index linking data back to source lines</li>
</ul>

#### Planner Prompt Segment

```text
DataIngest - Iteratively parse unstructured logs/text into structured data
  ** Specify input_files patterns (glob) to process
  ** Iteratively discovers Regex patterns using LLM for residual data
  ** Generates structured artifacts: data.jsonl, data.csv, patterns.json, and index.csv
  ** Efficiently handles large files via streaming extraction
```

#### Default Execution Configuration

```json
{
  "task_type" : "DataIngest",
  "input_files" : null,
  "sample_size" : 1000,
  "max_iterations" : 10,
  "coverage_threshold" : 0.95,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "DataIngest"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "DataIngest",
  "name" : "DataIngest",
  "model" : null
}
```

---

## Discussion

Directly answer questions or provide insights using the LLM, optionally referencing files, with optional user feedback and iteration.

Provides direct answers and insights using the LLM, optionally referencing project files.
<ul>
  <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
  <li>Reading files is optional; the task can operate with or without file input</li>
  <li>User feedback and iterative refinement are supported but not required</li>
  <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
  <li>Can answer detailed questions about code, design, or project context</li>
  <li>Supports both one-shot and interactive discussion modes</li>
  <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
</ul>

#### Planner Prompt Segment

```text

  Discussion - Directly answer questions or provide insights using the LLM. Reading files is optional and can be included if relevant to the inquiry.
    * Specify the questions and the goal of the inquiry.
    * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
    * User response/feedback and iteration are supported.
  
```

#### Default Execution Configuration

```json
{
  "task_type" : "Discussion",
  "inquiry_questions" : null,
  "inquiry_goal" : null,
  "input_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "Discussion"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "Discussion",
  "name" : "Discussion",
  "model" : null
}
```

---

## FileAppend

Append content to the end of existing files

Allows for precise additions to the end of files without modifying existing content.
<ul>
  <li>Ideal for logs, exports, and list updates</li>
  <li>Supports AI-generated content based on context</li>
  <li>Provides reviewable previews before applying changes</li>
  <li>Integrates with project structure and standards</li>
</ul>

#### Planner Prompt Segment

```text
FileAppend - Append content to the end of an existing file
  * Specify the relative file path and the content or goal of the addition.
  * The target file is NOT provided to the AI; only related context files are.
  * Useful for adding log entries, updating lists, or adding new exports/imports at the end of a file
```

#### Default Execution Configuration

```json
{
  "task_type" : "FileAppend",
  "file" : null,
  "related_files" : null,
  "append_content" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "FileAppend"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FileAppend",
  "name" : "FileAppend",
  "model" : null
}
```

---

## FileModification

Create new files or modify existing code with AI-powered assistance

Creates or modifies source files with AI assistance while maintaining code quality.
<ul>
  <li>Shows proposed changes in diff format for easy review</li>
  <li>Supports both automated application and manual approval modes</li>
  <li>Maintains project coding standards and style consistency</li>
  <li>Handles complex multi-file operations and refactoring</li>
  <li>Provides clear documentation of all changes with rationale</li>
  <li>Implements proper error handling and edge cases</li>
  <li>Updates imports and dependencies automatically</li>
  <li>Preserves existing code formatting and structure</li>
</ul>

#### Planner Prompt Segment

```text
FileModification - Modify existing files or create new files
  * For each file, specify the relative file path and the goal of the modification or creation
  * List input files/tasks to be examined when designing the modifications or new files
```

#### Default Execution Configuration

```json
{
  "task_type" : "FileModification",
  "files" : null,
  "related_files" : null,
  "extractContent" : false,
  "modifications" : null,
  "includeGitDiff" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "FileModification"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FileModification",
  "name" : "FileModification",
  "model" : null
}
```

---

## FileSearch

Search project files using patterns with contextual results

Performs pattern-based searches across project files with context.
<ul>
  <li>Supports both substring and regex search patterns</li>
  <li>Shows configurable context lines around matches</li>
  <li>Groups results by file with line numbers</li>
  <li>Filters for text-based files automatically</li>
  <li>Provides organized, readable output format</li>
</ul>

#### Planner Prompt Segment

```text
FileSearch - Search for patterns in files and provide results with context
* Specify the search pattern (substring or regex)
* Specify whether the pattern is a regex or a substring
* Specify the number of context lines to include
* List files (incl glob patterns) to be searched
```

#### Default Execution Configuration

```json
{
  "task_type" : "FileSearch",
  "search_pattern" : "",
  "is_regex" : false,
  "context_lines" : 2,
  "input_files" : null,
  "extractContent" : false,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "FileSearch"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "FileSearch",
  "name" : "FileSearch",
  "model" : null
}
```

---

## ImageTable

Generate a table/grid of AI-generated images

Creates a grid of images by generating each cell using AI image generation.
<ul>
  <li>Define rows and columns as labels for the grid</li>
  <li>Provide a prompt template with {row} and {column} placeholders</li>
  <li>Optionally specify a base style for consistent aesthetics</li>
  <li>Generates individual images and an HTML table view</li>
  <li>Useful for style comparisons, product variations, character sheets</li>
</ul>

#### Default Execution Configuration

```json
{
  "task_type" : "ImageTable",
  "rows" : null,
  "columns" : null,
  "image_prompt_template" : null,
  "base_style" : null,
  "output_directory" : "generated_images",
  "image_format" : "png",
  "task_description" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "ImageTable"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ImageTable",
  "parallel_generation" : 2,
  "image_width" : 512,
  "image_height" : 512,
  "model" : null,
  "name" : "ImageTable"
}
```

---

## LanguageServer

Interact with Language Servers (LSP)

Provides code intelligence capabilities via the Language Server Protocol.
<ul>
    <li><b>Definition:</b> Locate where a symbol is defined.</li>
    <li><b>References:</b> Find all usages of a symbol.</li>
    <li><b>Diagnostics:</b> Check files for syntax errors and warnings.</li>
    <li><b>Hover:</b> Get documentation or type information at a specific position.</li>
</ul>
Requires language servers (e.g., pylsp, typescript-language-server) to be installed in the environment.

#### Planner Prompt Segment

```text
LanguageServer - Query code intelligence (LSP)
  * Use to find definitions, references, or check for syntax errors (diagnostics).
  * Supported extensions: py, js, ts, kt, java, c, cpp, go, rs, sh, tex, yaml, dockerfile
  * Actions: 'diagnostics' (file-wide), 'definition' (specific pos), 'references' (specific pos), 'hover' (specific pos).
```

#### Default Execution Configuration

```json
{
  "task_type" : "LanguageServer",
  "action" : null,
  "file" : null,
  "line" : null,
  "character" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "LanguageServer"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "LanguageServer",
  "model" : null,
  "name" : "LanguageServer"
}
```

---

## PdfForm

Fills out a specific PDF form template with provided data.

Fills fields in a pre-configured PDF template.
<ul>
  <li><b>Requires:</b> A template PDF file defined in the global Type Config.</li>
  <li><b>Output:</b> A new PDF file with the fields populated.</li>
  <li>Automatically lists available fields from the template to the Planner.</li>
</ul>

#### Planner Prompt Segment

```text
PdfForm - Configuration Error: No template_file specified in TypeConfig.
```

#### Default Execution Configuration

```json
{
  "task_type" : "PdfForm",
  "output_file" : null,
  "fields" : null,
  "flatten" : true,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "PdfForm"
}
```

#### Default Type Configuration

```json
{
  "template_file" : null,
  "task_type" : "PdfForm",
  "name" : "PdfForm",
  "model" : null
}
```

---

## ReadDocuments

Deeply analyze project files and provide comprehensive technical insights or answers to specific questions.

Analyzes project files and provides detailed technical insights using the LLM.
<ul>
  <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
  <li>Reading files is optional; the task can operate with or without file input</li>
  <li>User feedback and iterative refinement are supported but not required</li>
  <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
  <li>Can answer detailed questions about code, design, or project context</li>
  <li>Supports both one-shot and interactive discussion modes</li>
  <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
</ul>

#### Planner Prompt Segment

```text

  ReadDocuments - Directly answer questions or provide insights using the LLM.
    * inquiry_questions: Specific questions to address.
    * inquiry_goal: The goal of the inquiry.
    * input_files: File patterns (e.g. **/*.kt) to use as input.
  
```

#### Default Execution Configuration

```json
{
  "task_type" : "ReadDocuments",
  "inquiry_questions" : null,
  "inquiry_goal" : null,
  "input_files" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "ReadDocuments"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "ReadDocuments",
  "name" : "ReadDocuments",
  "model" : null
}
```

---

