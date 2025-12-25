# HTML Generation Transcript

## Creating HTML File: `TableCompilationTask.html`

### Step 1: Generating HTML Structure

**Prompt:**
```
You are an expert web developer tasked with creating a complete, self-contained HTML file.

## Requirements:
# User Product Page Guidelines

This document outlines the standards for creating "User Product Pages" for Cognotik tasks. These pages serve as marketing and documentation hybrids, designed to bridge the gap between technical implementation and user understanding.

## 1. Design Philosophy

*   **Aesthetic:** "Future-Professional." Dark mode by default, using deep blues/slates (`#0f172a`, `#1e293b`) with high-contrast neon accents (Cyan, Gold, Purple) specific to the task category.
*   **Typography:** Clean sans-serif (Inter, System UI) for body text; Serif (Cinzel, Merriweather) allowed for narrative/creative tasks.
*   **Interactivity:** The page must feel alive. Static documentation is discouraged. Use hover states, tab switching, and reactive forms.

## 2. Page Structure

Every product page must contain the following sections in order:

### A. Header & Navigation
*   **Logo:** Cognotik Icon + Text.
*   **Links:** Features, Workflow/Specs, Demo.
*   **Style:** Glassmorphism (blur background), fixed position.

### B. Hero Section
*   **Title:** The Task Name (e.g., "Neural Network Layer Designer").
*   **Subtitle:** A compelling one-paragraph summary derived from the `Summary` and `Description` fields in `task_type_docs.md`.
*   **Visual:** A high-quality, abstract 3D illustration representing the task concept (e.g., DNA for genetics, glowing nodes for networks).
    *   *Requirement:* Include the prompt description for this image in an HTML comment at the top of the file.
*   **CTA:** A "Try the Simulator" button that scrolls to the Demo section.

### C. Features Grid
*   **Layout:** 3-column grid.
*   **Content:** Extract 3-6 key points from the "Key features include" list in the task documentation.
*   **Icons:** Use SVG icons (Lucide/Feather style) inside a glowing container.

### D. Interactive Simulator (The Core)
This is the most important section. It mocks the `ExecutionConfigData` input and the Task `Output`.

*   **Layout:** Split screen. Left side = Configuration; Right side = Visualization/Output.
*   **Left Column (Inputs):**
    *   Map the `Execution Configuration` table from the docs to HTML form elements.
    *   *Strings:* Text inputs or Textareas.
    *   *Booleans:* Toggles or Checkboxes.
    *   *Lists/Enums:* Select dropdowns.
    *   *Numbers:* Range sliders with value displays.
*   **Right Column (Outputs):**
    *   Use Tabs to organize the output (e.g., "Overview", "Code", "Logs", "Visuals").
    *   **Code Blocks:** Use syntax highlighting colors for code outputs.
    *   **Visuals:** If the task produces data, use `<canvas>` or CSS-based charts.
    *   **Logs:** If the task is a process (like `SelfHealing`), show a terminal-like log window.
*   **Functionality:** Write vanilla JavaScript to make the inputs update the outputs (or mock the update process with loading states).

### E. Workflow / Process (Optional)
*   If the task involves multiple steps (e.g., `NarrativeGeneration` or `SubPlanning`), visualize the pipeline using a step-stepper or flow diagram.

### F. Use Cases
*   Derive this from the "When to Use" section of the task documentation.
*   Format as cards or a list.

## 3. Content Mapping Guide

Use the `task_type_docs.md` to populate the page content:

| Product Page Element | Source in `task_type_docs.md` |
| :--- | :--- |
| **Hero Title** | Task Name |
| **Hero Tagline** | `Summary` field |
| **Feature Cards** | Bullet points under "Key features include" |
| **Simulator Inputs** | `Execution Configuration` Table |
| **Simulator Output** | `Output` Section (Mock the format described here) |
| **Use Case Section** | "When to Use" Section |

## 4. Technical Implementation Standards

*   **Single File:** The output must be a single `.html` file containing HTML, CSS, and JS.
*   **CSS Variables:** Define a `:root` block for easy theming.
    ```css
    :root {
        --bg-dark: #0f172a;
        --accent-primary: #38bdf8; /* Change per task type */
        --font-sans: system-ui, ...;
    }
    ```
*   **No External Heavy Libs:** Do not require `npm install`. Use CDN links for Fonts (Google Fonts) or Icons (FontAwesome) if necessary, but prefer inline SVGs.
*   **Responsive:** The Simulator must stack vertically on mobile devices.


## Context from Related Files:
# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/TableCompilationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.AbstractTask
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger

class TableCompilationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: TableCompilationTaskExecutionConfigData?
) : AbstractTask<TableCompilationTask.TableCompilationTaskExecutionConfigData, TableCompilationTask.TableCompilationTaskTypeConfig>(
  orchestrationConfig, planTask
) {

  class TableCompilationTaskExecutionConfigData(
    @Description("Row headers for the table")
    val rows: List<String>? = null,
    @Description("Column headers for the table")
    val columns: List<String>? = null,
    @Description("Query template for generating cell content. Use {row} and {column} as placeholders.")
    val cell_query: String? = null,
    @Description("Overall context or description for the table generation")
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, TaskExecutionConfig(
    task_type = TableCompilation.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ) {
    override fun validate(): String? {
      if (rows.isNullOrEmpty()) {
        return "TableCompilationTaskExecutionConfigData: rows list cannot be null or empty"
      }
      if (columns.isNullOrEmpty()) {
        return "TableCompilationTaskExecutionConfigData: columns list cannot be null or empty"
      }
      if (cell_query.isNullOrBlank()) {
        return "TableCompilationTaskExecutionConfigData: cell_query cannot be null or blank"
      }
      return null
    }
  }

  class TableCompilationTaskTypeConfig(
    task_type: String? = TableCompilation.name,
    @Description("Maximum partition size for parallel processing (e.g., 2 means 2x2 partitions)")
    val partition_size: Int = 2,
  ) : TaskTypeConfig(task_type = task_type), ValidatedObject {
    override fun validate(): String? {
      if (partition_size < 1 || partition_size > 10) {
        return "TableCompilationTaskExecutionConfigData: partition_size must be between 1 and 10"
      }
      return null
    }
  }

  init {
    planTask?.validate()?.let { errorMessage ->
      throw ValidatedObject.ValidationError(errorMessage, planTask)
    }
  }

  override fun promptSegment(): String {
    return """
TableCompilation - Generate structured tables with AI-computed cell values
  ** Specify row headers in the 'rows' array
  ** Specify column headers in the 'columns' array
  ** Provide a cell_query template using {row} and {column} placeholders
  ** Cells are computed in partitions for efficiency (configurable partition_size)
  ** Supports output formats: markdown, html, csv
  ** Example use cases:
     - Comparison matrices (features vs products)
     - Data analysis tables (metrics vs categories)
     - Decision matrices (options vs criteria)
     - Cross-reference tables
        """
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    executionConfig?.validate()?.let { errorMessage ->
      resultFn("VALIDATION ERROR: $errorMessage")
      return
    }

    val rows = executionConfig?.rows ?: emptyList()
    val columns = executionConfig?.columns ?: emptyList()
    val cellQuery = executionConfig?.cell_query ?: ""
    val partitionSize = typeConfig?.partition_size ?: 2

    val ui = task.ui
    val api = defaultSmart.getChildClient(task)

    task.add(MarkdownUtil.renderMarkdown("## Table Compilation", ui = ui))
    task.add(
      MarkdownUtil.renderMarkdown(
        "Generating ${rows.size}x${columns.size} table with partition size $partitionSize",
        ui = ui
      )
    )

    // Initialize the results table
    val cellResults = Array(rows.size) { Array(columns.size) { "" } }

    // Create partitions
    val rowPartitions = rows.indices.chunked(partitionSize)
    val colPartitions = columns.indices.chunked(partitionSize)

    val totalPartitions = rowPartitions.size * colPartitions.size
    var completedPartitions = 0

    // Process each partition
    for (rowPartition in rowPartitions) {
      for (colPartition in colPartitions) {
        completedPartitions++
        task.add(
          MarkdownUtil.renderMarkdown(
            "Processing partition $completedPartitions/$totalPartitions...",
            ui = ui
          )
        )

        val partitionCells = mutableListOf<Triple<Int, Int, String>>() // rowIdx, colIdx, query
        for (rowIdx in rowPartition) {
          for (colIdx in colPartition) {
            val query = cellQuery
              .replace("{row}", rows[rowIdx])
              .replace("{column}", columns[colIdx])
            partitionCells.add(Triple(rowIdx, colIdx, query))
          }
        }

        // Build batch prompt for this partition
        val batchPrompt = buildBatchPrompt(
          partitionCells,
          rows,
          columns,
          executionConfig?.task_description
        )

        val cellActor = ChatAgent(
          prompt = """
You are a precise data analyst. Generate concise cell values for a table.
Each cell should contain a brief, relevant response based on the row and column context.
Keep responses concise (typically 1-3 sentences or a few words/numbers as appropriate).
                    """.trimIndent(),
          model = api,
        )

        try {
          val response = cellActor.answer(listOf(batchPrompt))
          val parsedResults = parseBatchResponse(response, partitionCells.size)

          // Store results
          partitionCells.forEachIndexed { index, (rowIdx, colIdx, _) ->
            cellResults[rowIdx][colIdx] = parsedResults.getOrElse(index) { "Error" }
          }
        } catch (e: Exception) {
          log.error("Error processing partition", e)
          partitionCells.forEach { (rowIdx, colIdx, _) ->
            cellResults[rowIdx][colIdx] = "Error: ${e.message}"
          }
        }
      }
    }

    val jsonResult = formatAsJson(rows, columns, cellResults)
    task.hideable("```json\n$jsonResult\n```")
    // Format output
    val formattedTable = formatAsHtml(rows, columns, cellResults)

    task.add(MarkdownUtil.renderMarkdown("### Generated Table\n\n$formattedTable", ui = ui))
    task.complete("Table compilation complete")
    resultFn(formattedTable)
  }

  private fun buildBatchPrompt(
    cells: List<Triple<Int, Int, String>>,
    rows: List<String>,
    columns: List<String>,
    context: String?
  ): String {
    return buildString {
      appendLine("Generate cell values for the following table cells.")
      if (!context.isNullOrBlank()) {
        appendLine("Context: $context")
      }
      appendLine()
      appendLine("For each cell below, provide a concise value. Format your response as:")
      appendLine("CELL_1: [value]")
      appendLine("CELL_2: [value]")
      appendLine("etc.")
      appendLine()
      cells.forEachIndexed { index, (rowIdx, colIdx, query) ->
        appendLine("CELL_${index + 1}:")
        appendLine("  Row: ${rows[rowIdx]}")
        appendLine("  Column: ${columns[colIdx]}")
        appendLine("  Query: $query")
        appendLine()
      }
    }
  }

  private fun parseBatchResponse(response: String, expectedCount: Int): List<String> {
    val results = mutableListOf<String>()
    val cellPattern = """CELL_(\d+):\s*(.+?)(?=CELL_\d+:|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
    val matches = cellPattern.findAll(response)

    val matchMap = matches.associate { match ->
      match.groupValues[1].toInt() to match.groupValues[2].trim()
    }

    for (i in 1..expectedCount) {
      results.add(matchMap[i] ?: "N/A")
    }

    // If parsing failed, try line-by-line fallback
    if (results.all { it == "N/A" }) {
      val lines = response.lines().filter { it.isNotBlank() }
      return lines.take(expectedCount).map { it.trim() }
    }

    return results
  }


  private fun formatAsHtml(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
    return buildString {
      appendLine("<table border=\"1\" cellpadding=\"5\" cellspacing=\"0\">")
      appendLine("  <thead>")
      appendLine("    <tr>")
      appendLine("      <th></th>")
      columns.forEach { col -> appendLine("      <th>$col</th>") }
      appendLine("    </tr>")
      appendLine("  </thead>")
      appendLine("  <tbody>")
      rows.forEachIndexed { rowIdx, rowHeader ->
        appendLine("    <tr>")
        appendLine("      <th>$rowHeader</th>")
        columns.indices.forEach { colIdx ->
          appendLine("      <td>${cells[rowIdx][colIdx]}</td>")
        }
        appendLine("    </tr>")
      }
      appendLine("  </tbody>")
      appendLine("</table>")
    }
  }

  private fun formatAsCsv(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
    return buildString {
      // Header row
      append(",")
      appendLine(columns.joinToString(",") { "\"$it\"" })

      // Data rows
      rows.forEachIndexed { rowIdx, rowHeader ->
        append("\"$rowHeader\",")
        appendLine(columns.indices.joinToString(",") { colIdx ->
          "\"${cells[rowIdx][colIdx].replace("\"", "\"\"")}\""
        })
      }
    }
  }


  private fun formatAsJson(rows: List<String>, columns: List<String>, cells: Array<Array<String>>): String {
    val tableData = rows.mapIndexed { rowIdx, rowHeader ->
      val rowMap = mutableMapOf<String, String>()
      rowMap["Row"] = rowHeader
      columns.forEachIndexed { colIdx, colHeader ->
        rowMap[colHeader] = cells[rowIdx][colIdx]
      }
      rowMap
    }
    return tableData.toJson()
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    val acceptLink = ui.hrefLink("Accept Table") {
      fn()
    }
    return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(TableCompilationTask::class.java)
    val TableCompilation = TaskType(
      "TableCompilation",
      "Reasoning",
      TableCompilationTaskExecutionConfigData::class.java,
      TableCompilationTaskTypeConfig::class.java,
      "Generate structured tables with AI-computed cell values",
      """
              Generates tables by computing each cell value using AI.
              <ul>
                <li>Define rows and columns as headers</li>
                <li>Provide a query template with {row} and {column} placeholders</li>
                <li>Cells are computed in configurable partitions for efficiency</li>
                <li>Supports markdown, HTML, and CSV output formats</li>
                <li>Useful for comparison matrices, analysis tables, decision matrices</li>
              </ul>
            """
    )
  }
}
```

## Previous Task Results:


## Instructions:
1. Create a complete HTML5 document structure with proper semantic elements
2. Include appropriate meta tags (viewport, charset, etc.)
3. Add class names to elements that will need styling or JavaScript interaction
4. Use descriptive, semantic class names (e.g., "nav-menu", "hero-section", "card-container")
5. Include placeholder comments for where CSS and JavaScript will be added
6. Do NOT include any CSS or JavaScript yet - just the HTML structure with classes
7. Add comments to explain the purpose of major sections

## Output Format:
Provide the HTML structure within a code block:
```html
<!DOCTYPE html>
<html>
<head>
    <!-- CSS will be added here -->
</head>
<body>
    <!-- HTML structure with classes -->
</body>
<!-- JavaScript will be added here -->
</html>
```
```

**Response:**
```html
<!-- 
IMAGE PROMPT DESCRIPTION: 
A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. 
The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. 
Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). 
The style is cyberpunk-minimalist, utilizing deep slate blues and vibrant neon accents to convey "Reasoning" and "Structure".
-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Cognotik Table Compilation Task - Generate structured tables with AI-computed cell values.">
    <title>Table Compilation Engine | Cognotik</title>
    
    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- Icons: Phosphor Icons or similar lightweight SVG library via CDN -->
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <style>
        /* 
           CSS PLACEHOLDER 
           - :root variables for colors (Dark Slate, Neon Cyan, Gold)
           - Reset & Typography
           - Utility classes (flex, grid, glassmorphism)
           - Component styles (Hero, Cards, Simulator, Tabs)
           - Responsive media queries
        */
    </style>
</head>
<body>

    <!-- Navigation -->
    <nav class="navbar glass-panel">
        <div class="container nav-container">
            <div class="logo">
                <i class="ph-fill ph-brain"></i>
                <span>Cognotik</span>
            </div>
            <div class="nav-links">
                <a href="#features">Features</a>
                <a href="#workflow">Workflow</a>
                <a href="#simulator" class="btn-nav-cta">Try Simulator</a>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <header class="hero-section">
        <div class="container hero-content">
            <div class="hero-text">
                <div class="badge">Reasoning Engine</div>
                <h1 class="hero-title">Table Compilation Task</h1>
                <p class="hero-subtitle">
                    Generate structured tables by computing each cell value using AI. 
                    Transform raw dimensions into insightful matrices automatically by defining rows, columns, and context-aware queries.
                </p>
                <div class="hero-actions">
                    <a href="#simulator" class="btn btn-primary">Launch Simulator <i class="ph-bold ph-arrow-right"></i></a>
                    <a href="#docs" class="btn btn-secondary">View Documentation</a>
                </div>
            </div>
            <div class="hero-visual">
                <!-- Abstract 3D Matrix Illustration Placeholder -->
                <div class="visual-placeholder glow-effect">
                    <div class="abstract-grid-art"></div>
                </div>
            </div>
        </div>
    </header>

    <!-- Features Grid -->
    <section id="features" class="features-section">
        <div class="container">
            <h2 class="section-title">Key Capabilities</h2>
            <div class="features-grid">
                <!-- Feature 1 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-table"></i>
                    </div>
                    <h3>Structured Generation</h3>
                    <p>Define specific row and column headers to create precise, rigid output formats suitable for data analysis.</p>
                </div>
                <!-- Feature 2 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-magic-wand"></i>
                    </div>
                    <h3>Contextual Computation</h3>
                    <p>Utilize dynamic query templates with <code>{row}</code> and <code>{column}</code> placeholders to generate context-aware cell content.</p>
                </div>
                <!-- Feature 3 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-cpu"></i>
                    </div>
                    <h3>Partitioned Processing</h3>
                    <p>Efficiently handles large matrices by breaking tasks into configurable partition sizes for parallel execution.</p>
                </div>
            </div>
        </div>
    </section>

    <!-- Interactive Simulator -->
    <section id="simulator" class="simulator-section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">Interactive Simulator</h2>
                <p>Configure the execution parameters to generate a live table.</p>
            </div>

            <div class="simulator-interface">
                <!-- Left Column: Inputs -->
                <div class="sim-config glass-panel">
                    <div class="panel-header">
                        <h3><i class="ph-bold ph-sliders-horizontal"></i> Configuration</h3>
                    </div>
                    <form id="task-config-form">
                        
                        <!-- Task Description -->
                        <div class="form-group">
                            <label for="task_description">Context / Description</label>
                            <textarea id="task_description" rows="2" placeholder="E.g., Compare the following smartphone models based on technical specifications."></textarea>
                        </div>

                        <!-- Row Headers -->
                        <div class="form-group">
                            <label for="rows_input">Row Headers (One per line)</label>
                            <textarea id="rows_input" rows="4" placeholder="iPhone 15&#10;Samsung Galaxy S24&#10;Google Pixel 8"></textarea>
                        </div>

                        <!-- Column Headers -->
                        <div class="form-group">
                            <label for="cols_input">Column Headers (One per line)</label>
                            <textarea id="cols_input" rows="4" placeholder="Price&#10;Battery Life&#10;Camera MP"></textarea>
                        </div>

                        <!-- Cell Query Template -->
                        <div class="form-group">
                            <label for="cell_query">Cell Query Template</label>
                            <div class="input-hint">Use {row} and {column} as placeholders.</div>
                            <input type="text" id="cell_query" value="What is the {column} of the {row}? Provide a short value.">
                        </div>

                        <!-- Partition Size (Type Config) -->
                        <div class="form-group">
                            <label for="partition_size">Partition Size: <span id="partition-val">2</span></label>
                            <input type="range" id="partition_size" min="1" max="10" value="2" class="slider">
                        </div>

                        <button type="submit" class="btn btn-primary btn-block" id="run-simulation">
                            <i class="ph-bold ph-play"></i> Generate Table
                        </button>
                    </form>
                </div>

                <!-- Right Column: Outputs -->
                <div class="sim-output glass-panel">
                    <div class="tabs-nav">
                        <button class="tab-btn active" data-tab="preview">Preview</button>
                        <button class="tab-btn" data-tab="json">JSON Data</button>
                        <button class="tab-btn" data-tab="logs">Execution Logs</button>
                    </div>

                    <div class="tab-content">
                        <!-- Tab: Preview (HTML Table) -->
                        <div id="preview" class="tab-pane active">
                            <div class="empty-state">
                                <i class="ph-duotone ph-table"></i>
                                <p>Configure inputs and run simulation to see results.</p>
                            </div>
                            <div id="table-container" class="table-responsive hidden">
                                <!-- Table will be injected here via JS -->
                            </div>
                        </div>

                        <!-- Tab: JSON -->
                        <div id="json" class="tab-pane">
                            <pre><code id="json-output" class="language-json">// JSON output will appear here...</code></pre>
                        </div>

                        <!-- Tab: Logs -->
                        <div id="logs" class="tab-pane">
                            <div class="terminal-window">
                                <div id="log-output" class="terminal-content">
                                    <span class="log-line text-dim">Waiting for execution...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Use Cases -->
    <section id="use-cases" class="use-cases-section">
        <div class="container">
            <h2 class="section-title">When to Use</h2>
            <div class="use-case-list">
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-scales"></i></div>
                    <div class="uc-content">
                        <h4>Comparison Matrices</h4>
                        <p>Perfect for comparing features across multiple products or services side-by-side.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-chart-bar"></i></div>
                    <div class="uc-content">
                        <h4>Data Analysis Tables</h4>
                        <p>Generate metrics vs. categories to spot trends or summarize complex datasets.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-tree-structure"></i></div>
                    <div class="uc-content">
                        <h4>Decision Matrices</h4>
                        <p>Evaluate various options against a set of strict criteria to aid in decision making.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-brand">
                    <i class="ph-fill ph-brain"></i> Cognotik
                </div>
                <div class="footer-links">
                    <span>&copy; 2023 Simiacryptus. All rights reserved.</span>
                </div>
            </div>
        </div>
    </footer>

    <script>
        /* 
           JAVASCRIPT PLACEHOLDER
           - Tab switching logic
           - Range slider value update
           - Simulation logic:
             1. Prevent default form submit
             2. Read inputs (Rows, Cols, Query)
             3. Simulate "Processing partition X/Y" in logs
             4. Generate dummy data based on inputs
             5. Render HTML Table
             6. Render JSON Output
        */
    </script>
</body>
</html>
```

### Step 1.5: Generating Images

**Prompt:**
```
Based on the following HTML page description and structure, identify 3 key images that should be generated.
## Page Description:
# User Product Page Guidelines

This document outlines the standards for creating "User Product Pages" for Cognotik tasks. These pages serve as marketing and documentation hybrids, designed to bridge the gap between technical implementation and user understanding.

## 1. Design Philosophy

*   **Aesthetic:** "Future-Professional." Dark mode by default, using deep blues/slates (`#0f172a`, `#1e293b`) with high-contrast neon accents (Cyan, Gold, Purple) specific to the task category.
*   **Typography:** Clean sans-serif (Inter, System UI) for body text; Serif (Cinzel, Merriweather) allowed for narrative/creative tasks.
*   **Interactivity:** The page must feel alive. Static documentation is discouraged. Use hover states, tab switching, and reactive forms.

## 2. Page Structure

Every product page must contain the following sections in order:

### A. Header & Navigation
*   **Logo:** Cognotik Icon + Text.
*   **Links:** Features, Workflow/Specs, Demo.
*   **Style:** Glassmorphism (blur background), fixed position.

### B. Hero Section
*   **Title:** The Task Name (e.g., "Neural Network Layer Designer").
*   **Subtitle:** A compelling one-paragraph summary derived from the `Summary` and `Description` fields in `task_type_docs.md`.
*   **Visual:** A high-quality, abstract 3D illustration representing the task concept (e.g., DNA for genetics, glowing nodes for networks).
    *   *Requirement:* Include the prompt description for this image in an HTML comment at the top of the file.
*   **CTA:** A "Try the Simulator" button that scrolls to the Demo section.

### C. Features Grid
*   **Layout:** 3-column grid.
*   **Content:** Extract 3-6 key points from the "Key features include" list in the task documentation.
*   **Icons:** Use SVG icons (Lucide/Feather style) inside a glowing container.

### D. Interactive Simulator (The Core)
This is the most important section. It mocks the `ExecutionConfigData` input and the Task `Output`.

*   **Layout:** Split screen. Left side = Configuration; Right side = Visualization/Output.
*   **Left Column (Inputs):**
    *   Map the `Execution Configuration` table from the docs to HTML form elements.
    *   *Strings:* Text inputs or Textareas.
    *   *Booleans:* Toggles or Checkboxes.
    *   *Lists/Enums:* Select dropdowns.
    *   *Numbers:* Range sliders with value displays.
*   **Right Column (Outputs):**
    *   Use Tabs to organize the output (e.g., "Overview", "Code", "Logs", "Visuals").
    *   **Code Blocks:** Use syntax highlighting colors for code outputs.
    *   **Visuals:** If the task produces data, use `<canvas>` or CSS-based charts.
    *   **Logs:** If the task is a process (like `SelfHealing`), show a terminal-like log window.
*   **Functionality:** Write vanilla JavaScript to make the inputs update the outputs (or mock the update process with loading states).

### E. Workflow / Process (Optional)
*   If the task involves multiple steps (e.g., `NarrativeGeneration` or `SubPlanning`), visualize the pipeline using a step-stepper or flow diagram.

### F. Use Cases
*   Derive this from the "When to Use" section of the task documentation.
*   Format as cards or a list.

## 3. Content Mapping Guide

Use the `task_type_docs.md` to populate the page content:

| Product Page Element | Source in `task_type_docs.md` |
| :--- | :--- |
| **Hero Title** | Task Name |
| **Hero Tagline** | `Summary` field |
| **Feature Cards** | Bullet points under "Key features include" |
| **Simulator Inputs** | `Execution Configuration` Table |
| **Simulator Output** | `Output` Section (Mock the format described here) |
| **Use Case Section** | "When to Use" Section |

## 4. Technical Implementation Standards

*   **Single File:** The output must be a single `.html` file containing HTML, CSS, and JS.
*   **CSS Variables:** Define a `:root` block for easy theming.
    ```css
    :root {
        --bg-dark: #0f172a;
        --accent-primary: #38bdf8; /* Change per task type */
        --font-sans: system-ui, ...;
    }
    ```
*   **No External Heavy Libs:** Do not require `npm install`. Use CDN links for Fonts (Google Fonts) or Icons (FontAwesome) if necessary, but prefer inline SVGs.
*   **Responsive:** The Simulator must stack vertically on mobile devices.

## HTML Structure:
```html
<!-- 
IMAGE PROMPT DESCRIPTION: 
A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. 
The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. 
Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). 
The style is cyberpunk-minimalist, utilizing deep slate blues and vibrant neon accents to convey "Reasoning" and "Structure".
-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Cognotik Table Compilation Task - Generate structured tables with AI-computed cell values.">
    <title>Table Compilation Engine | Cognotik</title>
    
    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- Icons: Phosphor Icons or similar lightweight SVG library via CDN -->
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <style>
        /* 
           CSS PLACEHOLDER 
           - :root variables for colors (Dark Slate, Neon Cyan, Gold)
           - Reset & Typography
           - Utility classes (flex, grid, glassmorphism)
           - Component styles (Hero, Cards, Simulator, Tabs)
           - Responsive media queries
        */
    </style>
</head>
<body>

    <!-- Navigation -->
    <nav class="navbar glass-panel">
        <div class="container nav-container">
            <div class="logo">
                <i class="ph-fill ph-brain"></i>
                <span>Cognotik</span>
            </div>
            <div class="nav-links">
                <a href="#features">Features</a>
                <a href="#workflow">Workflow</a>
                <a href="#simulator" class="btn-nav-cta">Try Simulator</a>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <header class="hero-section">
        <div class="container hero-content">
            <div class="hero-text">
                <div class="badge">Reasoning Engine</div>
                <h1 class="hero-title">Table Compilation Task</h1>
                <p class="hero-subtitle">
                    Generate structured tables by computing each cell value using AI. 
                    Transform raw dimensions into insightful matrices automatically by defining rows, columns, and context-aware queries.
                </p>
                <div class="hero-actions">
                    <a href="#simulator" class="btn btn-primary">Launch Simulator <i class="ph-bold ph-arrow-right"></i></a>
                    <a href="#docs" class="btn btn-secondary">View Documentation</a>
                </div>
            </div>
            <div class="hero-visual">
                <!-- Abstract 3D Matrix Illustration Placeholder -->
                <div class="visual-placeholder glow-effect">
                    <div class="abstract-grid-art"></div>
                </div>
            </div>
        </div>
    </header>

    <!-- Features Grid -->
    <section id="features" class="features-section">
        <div class="container">
            <h2 class="section-title">Key Capabilities</h2>
            <div class="features-grid">
                <!-- Feature 1 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-table"></i>
                    </div>
                    <h3>Structured Generation</h3>
                    <p>Define specific row and column headers to create precise, rigid output formats suitable for data analysis.</p>
                </div>
                <!-- Feature 2 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-magic-wand"></i>
                    </div>
                    <h3>Contextual Computation</h3>
                    <p>Utilize dynamic query templates with <code>{row}</code> and <code>{column}</code> placeholders to generate context-aware cell content.</p>
                </div>
                <!-- Feature 3 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-cpu"></i>
                    </div>
                    <h3>Partitioned Processing</h3>
                    <p>Efficiently handles large matrices by breaking tasks into configurable partition sizes for parallel execution.</p>
                </div>
            </div>
        </div>
    </section>

    <!-- Interactive Simulator -->
    <section id="simulator" class="simulator-section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">Interactive Simulator</h2>
                <p>Configure the execution parameters to generate a live table.</p>
            </div>

            <div class="simulator-interface">
                <!-- Left Column: Inputs -->
                <div class="sim-config glass-panel">
                    <div class="panel-header">
                        <h3><i class="ph-bold ph-sliders-horizontal"></i> Configuration</h3>
                    </div>
                    <form id="task-config-form">
                        
                        <!-- Task Description -->
                        <div class="form-group">
                            <label for="task_description">Context / Description</label>
                            <textarea id="task_description" rows="2" placeholder="E.g., Compare the following smartphone models based on technical specifications."></textarea>
                        </div>

                        <!-- Row Headers -->
                        <div class="form-group">
                            <label for="rows_input">Row Headers (One per line)</label>
                            <textarea id="rows_input" rows="4" placeholder="iPhone 15&#10;Samsung Galaxy S24&#10;Google Pixel 8"></textarea>
                        </div>

                        <!-- Column Headers -->
                        <div class="form-group">
                            <label for="cols_input">Column Headers (One per line)</label>
                            <textarea id="cols_input" rows="4" placeholder="Price&#10;Battery Life&#10;Camera MP"></textarea>
                        </div>

                        <!-- Cell Query Template -->
                        <div class="form-group">
                            <label for="cell_query">Cell Query Template</label>
                            <div class="input-hint">Use {row} and {column} as placeholders.</div>
                            <input type="text" id="cell_query" value="What is the {column} of the {row}? Provide a short value.">
                        </div>

                        <!-- Partition Size (Type Config) -->
                        <div class="form-group">
                            <label for="partition_size">Partition Size: <span id="partition-val">2</span></label>
                            <input type="range" id="partition_size" min="1" max="10" value="2" class="slider">
                        </div>

                        <button type="submit" class="btn btn-primary btn-block" id="run-simulation">
                            <i class="ph-bold ph-play"></i> Generate Table
                        </button>
                    </form>
                </div>

                <!-- Right Column: Outputs -->
                <div class="sim-output glass-panel">
                    <div class="tabs-nav">
                        <button class="tab-btn active" data-tab="preview">Preview</button>
                        <button class="tab-btn" data-tab="json">JSON Data</button>
                        <button class="tab-btn" data-tab="logs">Execution Logs</button>
                    </div>

                    <div class="tab-content">
                        <!-- Tab: Preview (HTML Table) -->
                        <div id="preview" class="tab-pane active">
                            <div class="empty-state">
                                <i class="ph-duotone ph-table"></i>
                                <p>Configure inputs and run simulation to see results.</p>
                            </div>
                            <div id="table-container" class="table-responsive hidden">
                                <!-- Table will be injected here via JS -->
                            </div>
                        </div>

                        <!-- Tab: JSON -->
                        <div id="json" class="tab-pane">
                            <pre><code id="json-output" class="language-json">// JSON output will appear here...</code></pre>
                        </div>

                        <!-- Tab: Logs -->
                        <div id="logs" class="tab-pane">
                            <div class="terminal-window">
                                <div id="log-output" class="terminal-content">
                                    <span class="log-line text-dim">Waiting for execution...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Use Cases -->
    <section id="use-cases" class="use-cases-section">
        <div class="container">
            <h2 class="section-title">When to Use</h2>
            <div class="use-case-list">
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-scales"></i></div>
                    <div class="uc-content">
                        <h4>Comparison Matrices</h4>
                        <p>Perfect for comparing features across multiple products or services side-by-side.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-chart-bar"></i></div>
                    <div class="uc-content">
                        <h4>Data Analysis Tables</h4>
                        <p>Generate metrics vs. categories to spot trends or summarize complex datasets.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-tree-structure"></i></div>
                    <div class="uc-content">
                        <h4>Decision Matrices</h4>
                        <p>Evaluate various options against a set of strict criteria to aid in decision making.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-brand">
                    <i class="ph-fill ph-brain"></i> Cognotik
                </div>
                <div class="footer-links">
                    <span>&copy; 2023 Simiacryptus. All rights reserved.</span>
                </div>
            </div>
        </div>
    </footer>

    <script>
        /* 
           JAVASCRIPT PLACEHOLDER
           - Tab switching logic
           - Range slider value update
           - Simulation logic:
             1. Prevent default form submit
             2. Read inputs (Rows, Cols, Query)
             3. Simulate "Processing partition X/Y" in logs
             4. Generate dummy data based on inputs
             5. Render HTML Table
             6. Render JSON Output
        */
    </script>
</body>
</html>
```
For each image, provide:
1. A descriptive filename ending in .png (e.g., "hero-banner.png", "product-showcase.png")
2. A detailed visual description for image generation (be specific about style, colors, composition)

Note: All images will be generated as PNG files by an AI image model.

Format your response as:
IMAGE: filename.png
DESCRIPTION: detailed visual description
IMAGE: another-image.png
DESCRIPTION: another detailed description
```

**Response:**
IMAGE: table-compilation-hero-matrix.png
DESCRIPTION: A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). The style is cyberpunk-minimalist, utilizing deep slate blues (#0f172a) and vibrant neon accents to convey "Reasoning" and "Structure". Cinematic lighting with a shallow depth of field.

IMAGE: partitioned-processing-grid.png
DESCRIPTION: An isometric 3D illustration representing parallel processing. A dark, glass-like surface is etched with a vast grid pattern. Specific rectangular blocks (partitions) within the grid are illuminated in bright neon purple and electric blue, indicating active computation, while surrounding blocks remain dim or matte dark grey. Faint, glowing data streams or binary particles flow vertically into the active partitions. The background is a gradient of deep midnight blue and charcoal.

IMAGE: holographic-data-projection.png
DESCRIPTION: A futuristic, semi-transparent holographic interface displaying a complex comparison table. The table floats in a dark, atmospheric space. The grid lines are sharp beams of white light, and the "data" inside the cells appears as glowing abstract glyphs or bars in cyan and gold. The perspective is slightly angled, looking up at the data structure to give it a sense of scale and importance. Soft volumetric fog surrounds the projection to emphasize the light beams.

**Generated Image:** table-compilation-hero-matrix.png
**Description:** A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). The style is cyberpunk-minimalist, utilizing deep slate blues (#0f172a) and vibrant neon accents to convey "Reasoning" and "Structure". Cinematic lighting with a shallow depth of field.
**Prompt Used:** 

✅ Generated: [table-compilation-hero-matrix.png](table-compilation-hero-matrix.png)

![A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). The style is cyberpunk-minimalist, utilizing deep slate blues (#0f172a) and vibrant neon accents to convey "Reasoning" and "Structure". Cinematic lighting with a shallow depth of field.](table-compilation-hero-matrix.png)

**Generated Image:** partitioned-processing-grid.png
**Description:** An isometric 3D illustration representing parallel processing. A dark, glass-like surface is etched with a vast grid pattern. Specific rectangular blocks (partitions) within the grid are illuminated in bright neon purple and electric blue, indicating active computation, while surrounding blocks remain dim or matte dark grey. Faint, glowing data streams or binary particles flow vertically into the active partitions. The background is a gradient of deep midnight blue and charcoal.
**Prompt Used:** 

✅ Generated: [partitioned-processing-grid.png](partitioned-processing-grid.png)

![An isometric 3D illustration representing parallel processing. A dark, glass-like surface is etched with a vast grid pattern. Specific rectangular blocks (partitions) within the grid are illuminated in bright neon purple and electric blue, indicating active computation, while surrounding blocks remain dim or matte dark grey. Faint, glowing data streams or binary particles flow vertically into the active partitions. The background is a gradient of deep midnight blue and charcoal.](partitioned-processing-grid.png)

**Generated Image:** holographic-data-projection.png
**Description:** A futuristic, semi-transparent holographic interface displaying a complex comparison table. The table floats in a dark, atmospheric space. The grid lines are sharp beams of white light, and the "data" inside the cells appears as glowing abstract glyphs or bars in cyan and gold. The perspective is slightly angled, looking up at the data structure to give it a sense of scale and importance. Soft volumetric fog surrounds the projection to emphasize the light beams.
**Prompt Used:** 

✅ Generated: [holographic-data-projection.png](holographic-data-projection.png)

![A futuristic, semi-transparent holographic interface displaying a complex comparison table. The table floats in a dark, atmospheric space. The grid lines are sharp beams of white light, and the "data" inside the cells appears as glowing abstract glyphs or bars in cyan and gold. The perspective is slightly angled, looking up at the data structure to give it a sense of scale and importance. Soft volumetric fog surrounds the projection to emphasize the light beams.](holographic-data-projection.png)

### Step 2: Generating JavaScript

**Prompt:**
```
Based on the following HTML structure, generate the JavaScript code needed for interactivity.

## HTML Structure:
```html
<!-- 
IMAGE PROMPT DESCRIPTION: 
A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. 
The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. 
Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). 
The style is cyberpunk-minimalist, utilizing deep slate blues and vibrant neon accents to convey "Reasoning" and "Structure".
-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Cognotik Table Compilation Task - Generate structured tables with AI-computed cell values.">
    <title>Table Compilation Engine | Cognotik</title>
    
    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- Icons: Phosphor Icons or similar lightweight SVG library via CDN -->
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <style>
        /* 
           CSS PLACEHOLDER 
           - :root variables for colors (Dark Slate, Neon Cyan, Gold)
           - Reset & Typography
           - Utility classes (flex, grid, glassmorphism)
           - Component styles (Hero, Cards, Simulator, Tabs)
           - Responsive media queries
        */
    </style>
</head>
<body>

    <!-- Navigation -->
    <nav class="navbar glass-panel">
        <div class="container nav-container">
            <div class="logo">
                <i class="ph-fill ph-brain"></i>
                <span>Cognotik</span>
            </div>
            <div class="nav-links">
                <a href="#features">Features</a>
                <a href="#workflow">Workflow</a>
                <a href="#simulator" class="btn-nav-cta">Try Simulator</a>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <header class="hero-section">
        <div class="container hero-content">
            <div class="hero-text">
                <div class="badge">Reasoning Engine</div>
                <h1 class="hero-title">Table Compilation Task</h1>
                <p class="hero-subtitle">
                    Generate structured tables by computing each cell value using AI. 
                    Transform raw dimensions into insightful matrices automatically by defining rows, columns, and context-aware queries.
                </p>
                <div class="hero-actions">
                    <a href="#simulator" class="btn btn-primary">Launch Simulator <i class="ph-bold ph-arrow-right"></i></a>
                    <a href="#docs" class="btn btn-secondary">View Documentation</a>
                </div>
            </div>
            <div class="hero-visual">
                <!-- Abstract 3D Matrix Illustration Placeholder -->
                <div class="visual-placeholder glow-effect">
                    <div class="abstract-grid-art"></div>
                </div>
            </div>
        </div>
    </header>

    <!-- Features Grid -->
    <section id="features" class="features-section">
        <div class="container">
            <h2 class="section-title">Key Capabilities</h2>
            <div class="features-grid">
                <!-- Feature 1 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-table"></i>
                    </div>
                    <h3>Structured Generation</h3>
                    <p>Define specific row and column headers to create precise, rigid output formats suitable for data analysis.</p>
                </div>
                <!-- Feature 2 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-magic-wand"></i>
                    </div>
                    <h3>Contextual Computation</h3>
                    <p>Utilize dynamic query templates with <code>{row}</code> and <code>{column}</code> placeholders to generate context-aware cell content.</p>
                </div>
                <!-- Feature 3 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-cpu"></i>
                    </div>
                    <h3>Partitioned Processing</h3>
                    <p>Efficiently handles large matrices by breaking tasks into configurable partition sizes for parallel execution.</p>
                </div>
            </div>
        </div>
    </section>

    <!-- Interactive Simulator -->
    <section id="simulator" class="simulator-section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">Interactive Simulator</h2>
                <p>Configure the execution parameters to generate a live table.</p>
            </div>

            <div class="simulator-interface">
                <!-- Left Column: Inputs -->
                <div class="sim-config glass-panel">
                    <div class="panel-header">
                        <h3><i class="ph-bold ph-sliders-horizontal"></i> Configuration</h3>
                    </div>
                    <form id="task-config-form">
                        
                        <!-- Task Description -->
                        <div class="form-group">
                            <label for="task_description">Context / Description</label>
                            <textarea id="task_description" rows="2" placeholder="E.g., Compare the following smartphone models based on technical specifications."></textarea>
                        </div>

                        <!-- Row Headers -->
                        <div class="form-group">
                            <label for="rows_input">Row Headers (One per line)</label>
                            <textarea id="rows_input" rows="4" placeholder="iPhone 15&#10;Samsung Galaxy S24&#10;Google Pixel 8"></textarea>
                        </div>

                        <!-- Column Headers -->
                        <div class="form-group">
                            <label for="cols_input">Column Headers (One per line)</label>
                            <textarea id="cols_input" rows="4" placeholder="Price&#10;Battery Life&#10;Camera MP"></textarea>
                        </div>

                        <!-- Cell Query Template -->
                        <div class="form-group">
                            <label for="cell_query">Cell Query Template</label>
                            <div class="input-hint">Use {row} and {column} as placeholders.</div>
                            <input type="text" id="cell_query" value="What is the {column} of the {row}? Provide a short value.">
                        </div>

                        <!-- Partition Size (Type Config) -->
                        <div class="form-group">
                            <label for="partition_size">Partition Size: <span id="partition-val">2</span></label>
                            <input type="range" id="partition_size" min="1" max="10" value="2" class="slider">
                        </div>

                        <button type="submit" class="btn btn-primary btn-block" id="run-simulation">
                            <i class="ph-bold ph-play"></i> Generate Table
                        </button>
                    </form>
                </div>

                <!-- Right Column: Outputs -->
                <div class="sim-output glass-panel">
                    <div class="tabs-nav">
                        <button class="tab-btn active" data-tab="preview">Preview</button>
                        <button class="tab-btn" data-tab="json">JSON Data</button>
                        <button class="tab-btn" data-tab="logs">Execution Logs</button>
                    </div>

                    <div class="tab-content">
                        <!-- Tab: Preview (HTML Table) -->
                        <div id="preview" class="tab-pane active">
                            <div class="empty-state">
                                <i class="ph-duotone ph-table"></i>
                                <p>Configure inputs and run simulation to see results.</p>
                            </div>
                            <div id="table-container" class="table-responsive hidden">
                                <!-- Table will be injected here via JS -->
                            </div>
                        </div>

                        <!-- Tab: JSON -->
                        <div id="json" class="tab-pane">
                            <pre><code id="json-output" class="language-json">// JSON output will appear here...</code></pre>
                        </div>

                        <!-- Tab: Logs -->
                        <div id="logs" class="tab-pane">
                            <div class="terminal-window">
                                <div id="log-output" class="terminal-content">
                                    <span class="log-line text-dim">Waiting for execution...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Use Cases -->
    <section id="use-cases" class="use-cases-section">
        <div class="container">
            <h2 class="section-title">When to Use</h2>
            <div class="use-case-list">
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-scales"></i></div>
                    <div class="uc-content">
                        <h4>Comparison Matrices</h4>
                        <p>Perfect for comparing features across multiple products or services side-by-side.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-chart-bar"></i></div>
                    <div class="uc-content">
                        <h4>Data Analysis Tables</h4>
                        <p>Generate metrics vs. categories to spot trends or summarize complex datasets.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-tree-structure"></i></div>
                    <div class="uc-content">
                        <h4>Decision Matrices</h4>
                        <p>Evaluate various options against a set of strict criteria to aid in decision making.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-brand">
                    <i class="ph-fill ph-brain"></i> Cognotik
                </div>
                <div class="footer-links">
                    <span>&copy; 2023 Simiacryptus. All rights reserved.</span>
                </div>
            </div>
        </div>
    </footer>

    <script>
        /* 
           JAVASCRIPT PLACEHOLDER
           - Tab switching logic
           - Range slider value update
           - Simulation logic:
             1. Prevent default form submit
             2. Read inputs (Rows, Cols, Query)
             3. Simulate "Processing partition X/Y" in logs
             4. Generate dummy data based on inputs
             5. Render HTML Table
             6. Render JSON Output
        */
    </script>
</body>
</html>
```

## Requirements:
# User Product Page Guidelines

This document outlines the standards for creating "User Product Pages" for Cognotik tasks. These pages serve as marketing and documentation hybrids, designed to bridge the gap between technical implementation and user understanding.

## 1. Design Philosophy

*   **Aesthetic:** "Future-Professional." Dark mode by default, using deep blues/slates (`#0f172a`, `#1e293b`) with high-contrast neon accents (Cyan, Gold, Purple) specific to the task category.
*   **Typography:** Clean sans-serif (Inter, System UI) for body text; Serif (Cinzel, Merriweather) allowed for narrative/creative tasks.
*   **Interactivity:** The page must feel alive. Static documentation is discouraged. Use hover states, tab switching, and reactive forms.

## 2. Page Structure

Every product page must contain the following sections in order:

### A. Header & Navigation
*   **Logo:** Cognotik Icon + Text.
*   **Links:** Features, Workflow/Specs, Demo.
*   **Style:** Glassmorphism (blur background), fixed position.

### B. Hero Section
*   **Title:** The Task Name (e.g., "Neural Network Layer Designer").
*   **Subtitle:** A compelling one-paragraph summary derived from the `Summary` and `Description` fields in `task_type_docs.md`.
*   **Visual:** A high-quality, abstract 3D illustration representing the task concept (e.g., DNA for genetics, glowing nodes for networks).
    *   *Requirement:* Include the prompt description for this image in an HTML comment at the top of the file.
*   **CTA:** A "Try the Simulator" button that scrolls to the Demo section.

### C. Features Grid
*   **Layout:** 3-column grid.
*   **Content:** Extract 3-6 key points from the "Key features include" list in the task documentation.
*   **Icons:** Use SVG icons (Lucide/Feather style) inside a glowing container.

### D. Interactive Simulator (The Core)
This is the most important section. It mocks the `ExecutionConfigData` input and the Task `Output`.

*   **Layout:** Split screen. Left side = Configuration; Right side = Visualization/Output.
*   **Left Column (Inputs):**
    *   Map the `Execution Configuration` table from the docs to HTML form elements.
    *   *Strings:* Text inputs or Textareas.
    *   *Booleans:* Toggles or Checkboxes.
    *   *Lists/Enums:* Select dropdowns.
    *   *Numbers:* Range sliders with value displays.
*   **Right Column (Outputs):**
    *   Use Tabs to organize the output (e.g., "Overview", "Code", "Logs", "Visuals").
    *   **Code Blocks:** Use syntax highlighting colors for code outputs.
    *   **Visuals:** If the task produces data, use `<canvas>` or CSS-based charts.
    *   **Logs:** If the task is a process (like `SelfHealing`), show a terminal-like log window.
*   **Functionality:** Write vanilla JavaScript to make the inputs update the outputs (or mock the update process with loading states).

### E. Workflow / Process (Optional)
*   If the task involves multiple steps (e.g., `NarrativeGeneration` or `SubPlanning`), visualize the pipeline using a step-stepper or flow diagram.

### F. Use Cases
*   Derive this from the "When to Use" section of the task documentation.
*   Format as cards or a list.

## 3. Content Mapping Guide

Use the `task_type_docs.md` to populate the page content:

| Product Page Element | Source in `task_type_docs.md` |
| :--- | :--- |
| **Hero Title** | Task Name |
| **Hero Tagline** | `Summary` field |
| **Feature Cards** | Bullet points under "Key features include" |
| **Simulator Inputs** | `Execution Configuration` Table |
| **Simulator Output** | `Output` Section (Mock the format described here) |
| **Use Case Section** | "When to Use" Section |

## 4. Technical Implementation Standards

*   **Single File:** The output must be a single `.html` file containing HTML, CSS, and JS.
*   **CSS Variables:** Define a `:root` block for easy theming.
    ```css
    :root {
        --bg-dark: #0f172a;
        --accent-primary: #38bdf8; /* Change per task type */
        --font-sans: system-ui, ...;
    }
    ```
*   **No External Heavy Libs:** Do not require `npm install`. Use CDN links for Fonts (Google Fonts) or Icons (FontAwesome) if necessary, but prefer inline SVGs.
*   **Responsive:** The Simulator must stack vertically on mobile devices.


## Instructions:
1. Generate JavaScript that adds interactivity to the HTML elements
2. Use modern JavaScript (ES6+) features
3. Add event listeners for user interactions
4. Include any necessary DOM manipulation
5. Add comments to explain the functionality
6. Ensure the code is efficient and follows best practices

## Output Format:
Provide only the JavaScript code within a code block:
```javascript
// JavaScript code here
```
```

**Response:**
```javascript
document.addEventListener('DOMContentLoaded', () => {
    // --- UI Initialization ---

    // 1. Tab Switching Logic
    const tabButtons = document.querySelectorAll('.tab-btn');
    const tabPanes = document.querySelectorAll('.tab-pane');

    function switchTab(tabId) {
        // Remove active state from all buttons and panes
        tabButtons.forEach(btn => btn.classList.remove('active'));
        tabPanes.forEach(pane => pane.classList.remove('active'));

        // Activate target
        const targetBtn = document.querySelector(`.tab-btn[data-tab="${tabId}"]`);
        const targetPane = document.getElementById(tabId);

        if (targetBtn && targetPane) {
            targetBtn.classList.add('active');
            targetPane.classList.add('active');
        }
    }

    tabButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            switchTab(btn.dataset.tab);
        });
    });

    // 2. Range Slider Logic
    const partitionSlider = document.getElementById('partition_size');
    const partitionDisplay = document.getElementById('partition-val');

    if (partitionSlider && partitionDisplay) {
        partitionSlider.addEventListener('input', (e) => {
            partitionDisplay.textContent = e.target.value;
        });
    }

    // --- Simulation Logic ---

    const form = document.getElementById('task-config-form');
    const runBtn = document.getElementById('run-simulation');
    const logOutput = document.getElementById('log-output');
    const tableContainer = document.getElementById('table-container');
    const emptyState = document.querySelector('.empty-state');
    const jsonOutput = document.getElementById('json-output');

    // Helper: Add line to terminal log
    function log(message, type = 'info') {
        const line = document.createElement('div');
        line.className = `log-line ${type}`;
        const timestamp = new Date().toLocaleTimeString('en-US', { hour12: false });
        
        // Style based on type
        let colorClass = 'text-dim'; // default
        if (type === 'success') colorClass = 'text-success'; // You'd need to add this CSS or use inline style
        if (type === 'error') colorClass = 'text-error';
        
        line.innerHTML = `<span style="opacity:0.5">[${timestamp}]</span> ${message}`;
        logOutput.appendChild(line);
        
        // Auto-scroll to bottom
        const terminalWindow = document.querySelector('.terminal-window');
        if (terminalWindow) terminalWindow.scrollTop = terminalWindow.scrollHeight;
    }

    // Helper: Mock AI Value Generation
    // Generates plausible-looking data based on column headers to make the demo feel real
    function generateMockCell(row, col, query) {
        const q = (query + " " + col).toLowerCase();
        
        if (q.includes('price') || q.includes('cost')) return '$' + (Math.floor(Math.random() * 500) + 500);
        if (q.includes('battery')) return (Math.floor(Math.random() * 10) + 15) + ' hours';
        if (q.includes('camera') || q.includes('mp')) return (Math.floor(Math.random() * 50) + 12) + ' MP';
        if (q.includes('screen') || q.includes('display')) return (Math.random() * 1 + 6).toFixed(1) + '" OLED';
        if (q.includes('processor') || q.includes('cpu')) return 'Snapdragon ' + (Math.floor(Math.random() * 8) + 8) + ' Gen ' + (Math.floor(Math.random() * 3) + 1);
        if (q.includes('ram')) return [8, 12, 16][Math.floor(Math.random() * 3)] + ' GB';
        
        // Fallback generic value
        return `Computed(${row.substring(0,3)}:${col.substring(0,3)})`;
    }

    // Main Execution Handler
    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        // 1. Get Inputs
        const rowsRaw = document.getElementById('rows_input').value;
        const colsRaw = document.getElementById('cols_input').value;
        const queryTemplate = document.getElementById('cell_query').value;
        const partitionSize = parseInt(partitionSlider.value);
        const description = document.getElementById('task_description').value;

        // Parse lists (split by newline and remove empty)
        const rows = rowsRaw.split('\n').map(s => s.trim()).filter(s => s.length > 0);
        const cols = colsRaw.split('\n').map(s => s.trim()).filter(s => s.length > 0);

        // Validation
        if (rows.length === 0 || cols.length === 0) {
            alert("Please define at least one row and one column.");
            return;
        }

        // 2. Reset UI State
        runBtn.disabled = true;
        runBtn.innerHTML = '<i class="ph-bold ph-spinner ph-spin"></i> Processing...';
        logOutput.innerHTML = ''; // Clear logs
        switchTab('logs'); // Auto-switch to logs to show progress
        
        log(`Initializing TableCompilationTask...`);
        log(`Configuration: ${rows.length} rows, ${cols.length} columns.`);
        log(`Partition Size: ${partitionSize}x${partitionSize}`);
        if(description) log(`Context loaded: "${description.substring(0, 30)}..."`);

        // 3. Simulate Partitioned Processing
        // Calculate chunks based on partition size (mimicking Kotlin logic)
        const rowChunks = Math.ceil(rows.length / partitionSize);
        const colChunks = Math.ceil(cols.length / partitionSize);
        const totalPartitions = rowChunks * colChunks;
        
        // Data storage
        const tableData = Array(rows.length).fill(null).map(() => Array(cols.length).fill(null));

        let completedPartitions = 0;

        // Async Simulation Loop
        for (let rChunk = 0; rChunk < rowChunks; rChunk++) {
            for (let cChunk = 0; cChunk < colChunks; cChunk++) {
                
                // Simulate network/processing delay
                await new Promise(resolve => setTimeout(resolve, 600)); 
                
                completedPartitions++;
                log(`Processing partition ${completedPartitions}/${totalPartitions}...`);

                // Determine bounds for this partition
                const rStart = rChunk * partitionSize;
                const rEnd = Math.min(rStart + partitionSize, rows.length);
                const cStart = cChunk * partitionSize;
                const cEnd = Math.min(cStart + partitionSize, cols.length);

                // "Process" cells in this partition
                for (let r = rStart; r < rEnd; r++) {
                    for (let c = cStart; c < cEnd; c++) {
                        const cellValue = generateMockCell(rows[r], cols[c], queryTemplate);
                        tableData[r][c] = cellValue;
                        
                        // Occasionally log specific query generation to show "Reasoning"
                        if (Math.random() > 0.7) {
                            const specificQuery = queryTemplate
                                .replace('{row}', rows[r])
                                .replace('{column}', cols[c]);
                            log(`> Querying Agent: "${specificQuery}"`, 'dim');
                        }
                    }
                }
            }
        }

        log(`Compilation complete. Formatting output...`, 'success');

        // 4. Render Outputs

        // A. HTML Table
        let htmlTable = `<table class="generated-table">`;
        // Header
        htmlTable += `<thead><tr><th></th>${cols.map(c => `<th>${c}</th>`).join('')}</tr></thead>`;
        // Body
        htmlTable += `<tbody>`;
        rows.forEach((rowHeader, rIdx) => {
            htmlTable += `<tr><th>${rowHeader}</th>`;
            tableData[rIdx].forEach(cellVal => {
                htmlTable += `<td>${cellVal}</td>`;
            });
            htmlTable += `</tr>`;
        });
        htmlTable += `</tbody></table>`;

        tableContainer.innerHTML = htmlTable;
        tableContainer.classList.remove('hidden');
        emptyState.style.display = 'none';

        // B. JSON Output
        const jsonResult = rows.map((rowHeader, rIdx) => {
            const rowObj = { "Row": rowHeader };
            cols.forEach((colHeader, cIdx) => {
                rowObj[colHeader] = tableData[rIdx][cIdx];
            });
            return rowObj;
        });
        jsonOutput.textContent = JSON.stringify(jsonResult, null, 2);

        // 5. Finalize
        runBtn.disabled = false;
        runBtn.innerHTML = '<i class="ph-bold ph-play"></i> Generate Table';
        
        // Switch to preview to show result
        setTimeout(() => switchTab('preview'), 500);
    });
});
```

### Step 3: Generating CSS

**Prompt:**
```
Based on the following HTML structure, generate the CSS styling.

## HTML Structure:
```html
<!-- 
IMAGE PROMPT DESCRIPTION: 
A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. 
The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. 
Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). 
The style is cyberpunk-minimalist, utilizing deep slate blues and vibrant neon accents to convey "Reasoning" and "Structure".
-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Cognotik Table Compilation Task - Generate structured tables with AI-computed cell values.">
    <title>Table Compilation Engine | Cognotik</title>
    
    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- Icons: Phosphor Icons or similar lightweight SVG library via CDN -->
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <style>
        /* 
           CSS PLACEHOLDER 
           - :root variables for colors (Dark Slate, Neon Cyan, Gold)
           - Reset & Typography
           - Utility classes (flex, grid, glassmorphism)
           - Component styles (Hero, Cards, Simulator, Tabs)
           - Responsive media queries
        */
    </style>
</head>
<body>

    <!-- Navigation -->
    <nav class="navbar glass-panel">
        <div class="container nav-container">
            <div class="logo">
                <i class="ph-fill ph-brain"></i>
                <span>Cognotik</span>
            </div>
            <div class="nav-links">
                <a href="#features">Features</a>
                <a href="#workflow">Workflow</a>
                <a href="#simulator" class="btn-nav-cta">Try Simulator</a>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <header class="hero-section">
        <div class="container hero-content">
            <div class="hero-text">
                <div class="badge">Reasoning Engine</div>
                <h1 class="hero-title">Table Compilation Task</h1>
                <p class="hero-subtitle">
                    Generate structured tables by computing each cell value using AI. 
                    Transform raw dimensions into insightful matrices automatically by defining rows, columns, and context-aware queries.
                </p>
                <div class="hero-actions">
                    <a href="#simulator" class="btn btn-primary">Launch Simulator <i class="ph-bold ph-arrow-right"></i></a>
                    <a href="#docs" class="btn btn-secondary">View Documentation</a>
                </div>
            </div>
            <div class="hero-visual">
                <!-- Abstract 3D Matrix Illustration Placeholder -->
                <div class="visual-placeholder glow-effect">
                    <div class="abstract-grid-art"></div>
                </div>
            </div>
        </div>
    </header>

    <!-- Features Grid -->
    <section id="features" class="features-section">
        <div class="container">
            <h2 class="section-title">Key Capabilities</h2>
            <div class="features-grid">
                <!-- Feature 1 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-table"></i>
                    </div>
                    <h3>Structured Generation</h3>
                    <p>Define specific row and column headers to create precise, rigid output formats suitable for data analysis.</p>
                </div>
                <!-- Feature 2 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-magic-wand"></i>
                    </div>
                    <h3>Contextual Computation</h3>
                    <p>Utilize dynamic query templates with <code>{row}</code> and <code>{column}</code> placeholders to generate context-aware cell content.</p>
                </div>
                <!-- Feature 3 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-cpu"></i>
                    </div>
                    <h3>Partitioned Processing</h3>
                    <p>Efficiently handles large matrices by breaking tasks into configurable partition sizes for parallel execution.</p>
                </div>
            </div>
        </div>
    </section>

    <!-- Interactive Simulator -->
    <section id="simulator" class="simulator-section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">Interactive Simulator</h2>
                <p>Configure the execution parameters to generate a live table.</p>
            </div>

            <div class="simulator-interface">
                <!-- Left Column: Inputs -->
                <div class="sim-config glass-panel">
                    <div class="panel-header">
                        <h3><i class="ph-bold ph-sliders-horizontal"></i> Configuration</h3>
                    </div>
                    <form id="task-config-form">
                        
                        <!-- Task Description -->
                        <div class="form-group">
                            <label for="task_description">Context / Description</label>
                            <textarea id="task_description" rows="2" placeholder="E.g., Compare the following smartphone models based on technical specifications."></textarea>
                        </div>

                        <!-- Row Headers -->
                        <div class="form-group">
                            <label for="rows_input">Row Headers (One per line)</label>
                            <textarea id="rows_input" rows="4" placeholder="iPhone 15&#10;Samsung Galaxy S24&#10;Google Pixel 8"></textarea>
                        </div>

                        <!-- Column Headers -->
                        <div class="form-group">
                            <label for="cols_input">Column Headers (One per line)</label>
                            <textarea id="cols_input" rows="4" placeholder="Price&#10;Battery Life&#10;Camera MP"></textarea>
                        </div>

                        <!-- Cell Query Template -->
                        <div class="form-group">
                            <label for="cell_query">Cell Query Template</label>
                            <div class="input-hint">Use {row} and {column} as placeholders.</div>
                            <input type="text" id="cell_query" value="What is the {column} of the {row}? Provide a short value.">
                        </div>

                        <!-- Partition Size (Type Config) -->
                        <div class="form-group">
                            <label for="partition_size">Partition Size: <span id="partition-val">2</span></label>
                            <input type="range" id="partition_size" min="1" max="10" value="2" class="slider">
                        </div>

                        <button type="submit" class="btn btn-primary btn-block" id="run-simulation">
                            <i class="ph-bold ph-play"></i> Generate Table
                        </button>
                    </form>
                </div>

                <!-- Right Column: Outputs -->
                <div class="sim-output glass-panel">
                    <div class="tabs-nav">
                        <button class="tab-btn active" data-tab="preview">Preview</button>
                        <button class="tab-btn" data-tab="json">JSON Data</button>
                        <button class="tab-btn" data-tab="logs">Execution Logs</button>
                    </div>

                    <div class="tab-content">
                        <!-- Tab: Preview (HTML Table) -->
                        <div id="preview" class="tab-pane active">
                            <div class="empty-state">
                                <i class="ph-duotone ph-table"></i>
                                <p>Configure inputs and run simulation to see results.</p>
                            </div>
                            <div id="table-container" class="table-responsive hidden">
                                <!-- Table will be injected here via JS -->
                            </div>
                        </div>

                        <!-- Tab: JSON -->
                        <div id="json" class="tab-pane">
                            <pre><code id="json-output" class="language-json">// JSON output will appear here...</code></pre>
                        </div>

                        <!-- Tab: Logs -->
                        <div id="logs" class="tab-pane">
                            <div class="terminal-window">
                                <div id="log-output" class="terminal-content">
                                    <span class="log-line text-dim">Waiting for execution...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Use Cases -->
    <section id="use-cases" class="use-cases-section">
        <div class="container">
            <h2 class="section-title">When to Use</h2>
            <div class="use-case-list">
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-scales"></i></div>
                    <div class="uc-content">
                        <h4>Comparison Matrices</h4>
                        <p>Perfect for comparing features across multiple products or services side-by-side.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-chart-bar"></i></div>
                    <div class="uc-content">
                        <h4>Data Analysis Tables</h4>
                        <p>Generate metrics vs. categories to spot trends or summarize complex datasets.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-tree-structure"></i></div>
                    <div class="uc-content">
                        <h4>Decision Matrices</h4>
                        <p>Evaluate various options against a set of strict criteria to aid in decision making.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-brand">
                    <i class="ph-fill ph-brain"></i> Cognotik
                </div>
                <div class="footer-links">
                    <span>&copy; 2023 Simiacryptus. All rights reserved.</span>
                </div>
            </div>
        </div>
    </footer>

    <script>
        /* 
           JAVASCRIPT PLACEHOLDER
           - Tab switching logic
           - Range slider value update
           - Simulation logic:
             1. Prevent default form submit
             2. Read inputs (Rows, Cols, Query)
             3. Simulate "Processing partition X/Y" in logs
             4. Generate dummy data based on inputs
             5. Render HTML Table
             6. Render JSON Output
        */
    </script>
</body>
</html>
```

## Requirements:
# User Product Page Guidelines

This document outlines the standards for creating "User Product Pages" for Cognotik tasks. These pages serve as marketing and documentation hybrids, designed to bridge the gap between technical implementation and user understanding.

## 1. Design Philosophy

*   **Aesthetic:** "Future-Professional." Dark mode by default, using deep blues/slates (`#0f172a`, `#1e293b`) with high-contrast neon accents (Cyan, Gold, Purple) specific to the task category.
*   **Typography:** Clean sans-serif (Inter, System UI) for body text; Serif (Cinzel, Merriweather) allowed for narrative/creative tasks.
*   **Interactivity:** The page must feel alive. Static documentation is discouraged. Use hover states, tab switching, and reactive forms.

## 2. Page Structure

Every product page must contain the following sections in order:

### A. Header & Navigation
*   **Logo:** Cognotik Icon + Text.
*   **Links:** Features, Workflow/Specs, Demo.
*   **Style:** Glassmorphism (blur background), fixed position.

### B. Hero Section
*   **Title:** The Task Name (e.g., "Neural Network Layer Designer").
*   **Subtitle:** A compelling one-paragraph summary derived from the `Summary` and `Description` fields in `task_type_docs.md`.
*   **Visual:** A high-quality, abstract 3D illustration representing the task concept (e.g., DNA for genetics, glowing nodes for networks).
    *   *Requirement:* Include the prompt description for this image in an HTML comment at the top of the file.
*   **CTA:** A "Try the Simulator" button that scrolls to the Demo section.

### C. Features Grid
*   **Layout:** 3-column grid.
*   **Content:** Extract 3-6 key points from the "Key features include" list in the task documentation.
*   **Icons:** Use SVG icons (Lucide/Feather style) inside a glowing container.

### D. Interactive Simulator (The Core)
This is the most important section. It mocks the `ExecutionConfigData` input and the Task `Output`.

*   **Layout:** Split screen. Left side = Configuration; Right side = Visualization/Output.
*   **Left Column (Inputs):**
    *   Map the `Execution Configuration` table from the docs to HTML form elements.
    *   *Strings:* Text inputs or Textareas.
    *   *Booleans:* Toggles or Checkboxes.
    *   *Lists/Enums:* Select dropdowns.
    *   *Numbers:* Range sliders with value displays.
*   **Right Column (Outputs):**
    *   Use Tabs to organize the output (e.g., "Overview", "Code", "Logs", "Visuals").
    *   **Code Blocks:** Use syntax highlighting colors for code outputs.
    *   **Visuals:** If the task produces data, use `<canvas>` or CSS-based charts.
    *   **Logs:** If the task is a process (like `SelfHealing`), show a terminal-like log window.
*   **Functionality:** Write vanilla JavaScript to make the inputs update the outputs (or mock the update process with loading states).

### E. Workflow / Process (Optional)
*   If the task involves multiple steps (e.g., `NarrativeGeneration` or `SubPlanning`), visualize the pipeline using a step-stepper or flow diagram.

### F. Use Cases
*   Derive this from the "When to Use" section of the task documentation.
*   Format as cards or a list.

## 3. Content Mapping Guide

Use the `task_type_docs.md` to populate the page content:

| Product Page Element | Source in `task_type_docs.md` |
| :--- | :--- |
| **Hero Title** | Task Name |
| **Hero Tagline** | `Summary` field |
| **Feature Cards** | Bullet points under "Key features include" |
| **Simulator Inputs** | `Execution Configuration` Table |
| **Simulator Output** | `Output` Section (Mock the format described here) |
| **Use Case Section** | "When to Use" Section |

## 4. Technical Implementation Standards

*   **Single File:** The output must be a single `.html` file containing HTML, CSS, and JS.
*   **CSS Variables:** Define a `:root` block for easy theming.
    ```css
    :root {
        --bg-dark: #0f172a;
        --accent-primary: #38bdf8; /* Change per task type */
        --font-sans: system-ui, ...;
    }
    ```
*   **No External Heavy Libs:** Do not require `npm install`. Use CDN links for Fonts (Google Fonts) or Icons (FontAwesome) if necessary, but prefer inline SVGs.
*   **Responsive:** The Simulator must stack vertically on mobile devices.


## Instructions:
1. Generate CSS that styles all the HTML elements
2. Create a visually appealing, modern design
3. Ensure responsive design (mobile-first approach)
4. Use CSS Grid and/or Flexbox for layouts
5. Include hover effects and transitions where appropriate
6. Use a consistent color scheme and typography
7. Add comments to organize the CSS sections
8. Follow CSS best practices and naming conventions

## Output Format:
Provide only the CSS code within a code block:
```css
/* CSS code here */
```
```

**Response:**
```css
/* =========================================
   1. VARIABLES & THEME SETUP
   ========================================= */
:root {
    /* Colors: Deep Slate & Neon Cyan/Gold */
    --bg-dark: #0f172a;       /* Slate 900 */
    --bg-card: #1e293b;       /* Slate 800 */
    --bg-glass: rgba(30, 41, 59, 0.6);
    
    --accent-primary: #06b6d4; /* Cyan 500 */
    --accent-glow: #22d3ee;    /* Cyan 400 */
    --accent-secondary: #f59e0b; /* Amber 500 */
    
    --text-main: #f8fafc;      /* Slate 50 */
    --text-muted: #94a3b8;     /* Slate 400 */
    
    --border-color: rgba(148, 163, 184, 0.15);
    --border-focus: rgba(6, 182, 212, 0.5);
    
    /* Typography */
    --font-sans: 'Inter', system-ui, -apple-system, sans-serif;
    --font-mono: 'JetBrains Mono', monospace;
    
    /* Spacing & Radius */
    --radius-sm: 6px;
    --radius-md: 12px;
    --radius-lg: 16px;
    --container-width: 1200px;
    
    /* Transitions */
    --transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* =========================================
   2. RESET & BASE STYLES
   ========================================= */
* {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

html {
    scroll-behavior: smooth;
}

body {
    background-color: var(--bg-dark);
    color: var(--text-main);
    font-family: var(--font-sans);
    line-height: 1.6;
    overflow-x: hidden;
    background-image: 
        radial-gradient(circle at 15% 50%, rgba(6, 182, 212, 0.08), transparent 25%),
        radial-gradient(circle at 85% 30%, rgba(245, 158, 11, 0.05), transparent 25%);
}

a {
    text-decoration: none;
    color: inherit;
    transition: var(--transition);
}

ul {
    list-style: none;
}

/* Utility Classes */
.container {
    max-width: var(--container-width);
    margin: 0 auto;
    padding: 0 2rem;
}

.glass-panel {
    background: var(--bg-glass);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-lg);
    box-shadow: 0 4px 30px rgba(0, 0, 0, 0.1);
}

.section-title {
    font-size: 2rem;
    font-weight: 700;
    margin-bottom: 1rem;
    background: linear-gradient(to right, var(--text-main), var(--text-muted));
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}

/* =========================================
   3. NAVIGATION
   ========================================= */
.navbar {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    z-index: 1000;
    border-bottom: 1px solid var(--border-color);
    border-radius: 0;
}

.nav-container {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 70px;
}

.logo {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-weight: 700;
    font-size: 1.25rem;
    color: var(--text-main);
}

.logo i {
    color: var(--accent-primary);
    font-size: 1.5rem;
}

.nav-links {
    display: flex;
    align-items: center;
    gap: 2rem;
}

.nav-links a {
    color: var(--text-muted);
    font-size: 0.95rem;
    font-weight: 500;
}

.nav-links a:hover {
    color: var(--accent-primary);
}

.btn-nav-cta {
    padding: 0.5rem 1.25rem;
    background: rgba(6, 182, 212, 0.1);
    color: var(--accent-primary) !important;
    border: 1px solid var(--accent-primary);
    border-radius: var(--radius-sm);
}

.btn-nav-cta:hover {
    background: var(--accent-primary);
    color: var(--bg-dark) !important;
    box-shadow: 0 0 15px rgba(6, 182, 212, 0.4);
}

/* =========================================
   4. HERO SECTION
   ========================================= */
.hero-section {
    padding: 140px 0 80px;
    position: relative;
    overflow: hidden;
}

.hero-content {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 4rem;
    align-items: center;
}

.badge {
    display: inline-block;
    padding: 0.25rem 0.75rem;
    background: rgba(245, 158, 11, 0.1);
    color: var(--accent-secondary);
    border: 1px solid rgba(245, 158, 11, 0.3);
    border-radius: 100px;
    font-size: 0.8rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 1px;
    margin-bottom: 1.5rem;
}

.hero-title {
    font-size: 3.5rem;
    line-height: 1.1;
    margin-bottom: 1.5rem;
    font-weight: 800;
}

.hero-subtitle {
    font-size: 1.125rem;
    color: var(--text-muted);
    margin-bottom: 2.5rem;
    max-width: 540px;
}

.hero-actions {
    display: flex;
    gap: 1rem;
}

.btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    padding: 0.75rem 1.5rem;
    border-radius: var(--radius-sm);
    font-weight: 600;
    cursor: pointer;
    transition: var(--transition);
    border: none;
    font-family: var(--font-sans);
}

.btn-primary {
    background: var(--accent-primary);
    color: var(--bg-dark);
}

.btn-primary:hover {
    background: var(--accent-glow);
    box-shadow: 0 0 20px rgba(6, 182, 212, 0.4);
    transform: translateY(-2px);
}

.btn-secondary {
    background: transparent;
    border: 1px solid var(--border-color);
    color: var(--text-main);
}

.btn-secondary:hover {
    border-color: var(--text-muted);
    background: rgba(255, 255, 255, 0.05);
}

/* Hero Visual - Abstract Matrix */
.visual-placeholder {
    width: 100%;
    height: 400px;
    position: relative;
    border-radius: var(--radius-lg);
    background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
    border: 1px solid var(--border-color);
    overflow: hidden;
    box-shadow: 0 20px 50px rgba(0,0,0,0.5);
}

.abstract-grid-art {
    position: absolute;
    inset: -50%;
    background-image: 
        linear-gradient(rgba(6, 182, 212, 0.3) 1px, transparent 1px),
        linear-gradient(90deg, rgba(6, 182, 212, 0.3) 1px, transparent 1px);
    background-size: 40px 40px;
    transform: perspective(500px) rotateX(60deg) translateY(-100px) translateZ(-100px);
    animation: gridMove 20s linear infinite;
    mask-image: radial-gradient(circle, black 30%, transparent 70%);
    -webkit-mask-image: radial-gradient(circle, black 30%, transparent 70%);
}

@keyframes gridMove {
    0% { transform: perspective(500px) rotateX(60deg) translateY(0) translateZ(-100px); }
    100% { transform: perspective(500px) rotateX(60deg) translateY(40px) translateZ(-100px); }
}

.glow-effect::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    width: 150px;
    height: 150px;
    background: var(--accent-primary);
    filter: blur(80px);
    opacity: 0.4;
    transform: translate(-50%, -50%);
    animation: pulse 4s ease-in-out infinite;
}

@keyframes pulse {
    0%, 100% { opacity: 0.3; transform: translate(-50%, -50%) scale(1); }
    50% { opacity: 0.5; transform: translate(-50%, -50%) scale(1.2); }
}

/* =========================================
   5. FEATURES GRID
   ========================================= */
.features-section {
    padding: 80px 0;
}

.features-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 2rem;
    margin-top: 3rem;
}

.feature-card {
    padding: 2rem;
    transition: var(--transition);
    position: relative;
    overflow: hidden;
}

.feature-card:hover {
    transform: translateY(-5px);
    border-color: var(--accent-primary);
    box-shadow: 0 10px 30px rgba(6, 182, 212, 0.1);
}

.icon-container {
    width: 50px;
    height: 50px;
    background: rgba(6, 182, 212, 0.1);
    border-radius: var(--radius-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 1.5rem;
    color: var(--accent-primary);
    font-size: 1.5rem;
}

.feature-card h3 {
    font-size: 1.25rem;
    margin-bottom: 0.75rem;
    color: var(--text-main);
}

.feature-card p {
    color: var(--text-muted);
    font-size: 0.95rem;
}

code {
    background: rgba(0, 0, 0, 0.3);
    padding: 0.1rem 0.3rem;
    border-radius: 4px;
    font-family: var(--font-mono);
    color: var(--accent-secondary);
    font-size: 0.85em;
}

/* =========================================
   6. INTERACTIVE SIMULATOR
   ========================================= */
.simulator-section {
    padding: 80px 0;
}

.section-header {
    text-align: center;
    margin-bottom: 3rem;
}

.section-header p {
    color: var(--text-muted);
}

.simulator-interface {
    display: grid;
    grid-template-columns: 350px 1fr;
    gap: 1.5rem;
    align-items: start;
}

/* Left Column: Config */
.sim-config {
    padding: 1.5rem;
}

.panel-header {
    margin-bottom: 1.5rem;
    padding-bottom: 1rem;
    border-bottom: 1px solid var(--border-color);
}

.panel-header h3 {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 1.1rem;
    color: var(--text-main);
}

.form-group {
    margin-bottom: 1.25rem;
}

.form-group label {
    display: block;
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--text-muted);
    margin-bottom: 0.5rem;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.input-hint {
    font-size: 0.75rem;
    color: var(--accent-secondary);
    margin-bottom: 0.5rem;
    margin-top: -0.25rem;
}

input[type="text"],
textarea,
select {
    width: 100%;
    background: rgba(15, 23, 42, 0.6);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-sm);
    padding: 0.75rem;
    color: var(--text-main);
    font-family: var(--font-sans);
    font-size: 0.9rem;
    transition: var(--transition);
}

textarea {
    resize: vertical;
}

input:focus,
textarea:focus {
    outline: none;
    border-color: var(--accent-primary);
    box-shadow: 0 0 0 2px rgba(6, 182, 212, 0.2);
}

/* Range Slider */
.slider {
    -webkit-appearance: none;
    width: 100%;
    height: 6px;
    background: var(--bg-dark);
    border-radius: 5px;
    outline: none;
}

.slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: var(--accent-primary);
    cursor: pointer;
    box-shadow: 0 0 10px rgba(6, 182, 212, 0.5);
}

.btn-block {
    width: 100%;
    margin-top: 1rem;
}

/* Right Column: Output */
.sim-output {
    min-height: 600px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.tabs-nav {
    display: flex;
    border-bottom: 1px solid var(--border-color);
    background: rgba(0, 0, 0, 0.2);
}

.tab-btn {
    padding: 1rem 1.5rem;
    background: transparent;
    border: none;
    color: var(--text-muted);
    font-weight: 600;
    cursor: pointer;
    border-bottom: 2px solid transparent;
    transition: var(--transition);
    font-family: var(--font-sans);
}

.tab-btn:hover {
    color: var(--text-main);
    background: rgba(255, 255, 255, 0.02);
}

.tab-btn.active {
    color: var(--accent-primary);
    border-bottom-color: var(--accent-primary);
    background: rgba(6, 182, 212, 0.05);
}

.tab-content {
    flex: 1;
    padding: 1.5rem;
    position: relative;
    overflow-y: auto;
}

.tab-pane {
    display: none;
    height: 100%;
}

.tab-pane.active {
    display: block;
    animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
    from { opacity: 0; transform: translateY(5px); }
    to { opacity: 1; transform: translateY(0); }
}

/* Empty State */
.empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--text-muted);
    text-align: center;
}

.empty-state i {
    font-size: 4rem;
    margin-bottom: 1rem;
    opacity: 0.3;
}

/* Generated Table Styles */
.table-responsive {
    overflow-x: auto;
}

table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
}

th, td {
    padding: 1rem;
    text-align: left;
    border-bottom: 1px solid var(--border-color);
}

th {
    background: rgba(0, 0, 0, 0.2);
    color: var(--accent-primary);
    font-weight: 600;
    white-space: nowrap;
}

td:first-child {
    font-weight: 600;
    color: var(--text-main);
    background: rgba(0, 0, 0, 0.1);
}

tr:hover td {
    background: rgba(255, 255, 255, 0.02);
}

/* JSON Output */
pre {
    background: rgba(0, 0, 0, 0.3);
    padding: 1rem;
    border-radius: var(--radius-sm);
    overflow-x: auto;
    font-family: var(--font-mono);
    font-size: 0.85rem;
    color: #a5b4fc;
    height: 100%;
}

/* Terminal Logs */
.terminal-window {
    background: #000;
    border-radius: var(--radius-sm);
    padding: 1rem;
    height: 100%;
    font-family: var(--font-mono);
    font-size: 0.85rem;
    overflow-y: auto;
    border: 1px solid #333;
}

.log-line {
    display: block;
    margin-bottom: 0.25rem;
}

.text-dim { color: #666; }
.text-info { color: var(--accent-primary); }
.text-success { color: #10b981; }
.text-warn { color: var(--accent-secondary); }

/* =========================================
   7. USE CASES
   ========================================= */
.use-cases-section {
    padding: 80px 0;
    background: rgba(0, 0, 0, 0.2);
}

.use-case-list {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
    max-width: 800px;
    margin: 0 auto;
}

.use-case-item {
    display: flex;
    gap: 1.5rem;
    padding: 1.5rem;
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: var(--radius-md);
    align-items: flex-start;
}

.uc-icon {
    flex-shrink: 0;
    width: 40px;
    height: 40px;
    background: var(--bg-dark);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--accent-secondary);
    border: 1px solid rgba(245, 158, 11, 0.2);
}

.uc-content h4 {
    font-size: 1.1rem;
    margin-bottom: 0.5rem;
    color: var(--text-main);
}

.uc-content p {
    color: var(--text-muted);
    font-size: 0.95rem;
}

/* =========================================
   8. FOOTER
   ========================================= */
.footer {
    padding: 3rem 0;
    border-top: 1px solid var(--border-color);
    margin-top: 4rem;
    background: var(--bg-dark);
}

.footer-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.footer-brand {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-weight: 700;
    color: var(--text-muted);
}

.footer-links {
    color: var(--text-muted);
    font-size: 0.9rem;
}

/* =========================================
   9. RESPONSIVE DESIGN
   ========================================= */
@media (max-width: 992px) {
    .hero-content {
        grid-template-columns: 1fr;
        text-align: center;
    }

    .hero-subtitle {
        margin: 0 auto 2.5rem;
    }

    .hero-actions {
        justify-content: center;
    }

    .simulator-interface {
        grid-template-columns: 1fr;
    }
    
    .sim-output {
        min-height: 500px;
    }
}

@media (max-width: 768px) {
    .hero-title {
        font-size: 2.5rem;
    }
    
    .nav-links {
        display: none; /* Simple hide for mobile, would need hamburger menu in full impl */
    }
    
    .features-grid {
        grid-template-columns: 1fr;
    }
    
    .footer-content {
        flex-direction: column;
        gap: 1rem;
    }
}
```

### Step 3.5: Inserting Image References

**Prompt:**
```
You need to insert image references into the HTML structure.
## Current HTML Structure:
```html
<!-- 
IMAGE PROMPT DESCRIPTION: 
A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. 
The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. 
Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). 
The style is cyberpunk-minimalist, utilizing deep slate blues and vibrant neon accents to convey "Reasoning" and "Structure".
-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Cognotik Table Compilation Task - Generate structured tables with AI-computed cell values.">
    <title>Table Compilation Engine | Cognotik</title>
    
    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- Icons: Phosphor Icons or similar lightweight SVG library via CDN -->
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <style>
        /* 
           CSS PLACEHOLDER 
           - :root variables for colors (Dark Slate, Neon Cyan, Gold)
           - Reset & Typography
           - Utility classes (flex, grid, glassmorphism)
           - Component styles (Hero, Cards, Simulator, Tabs)
           - Responsive media queries
        */
    </style>
</head>
<body>

    <!-- Navigation -->
    <nav class="navbar glass-panel">
        <div class="container nav-container">
            <div class="logo">
                <i class="ph-fill ph-brain"></i>
                <span>Cognotik</span>
            </div>
            <div class="nav-links">
                <a href="#features">Features</a>
                <a href="#workflow">Workflow</a>
                <a href="#simulator" class="btn-nav-cta">Try Simulator</a>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <header class="hero-section">
        <div class="container hero-content">
            <div class="hero-text">
                <div class="badge">Reasoning Engine</div>
                <h1 class="hero-title">Table Compilation Task</h1>
                <p class="hero-subtitle">
                    Generate structured tables by computing each cell value using AI. 
                    Transform raw dimensions into insightful matrices automatically by defining rows, columns, and context-aware queries.
                </p>
                <div class="hero-actions">
                    <a href="#simulator" class="btn btn-primary">Launch Simulator <i class="ph-bold ph-arrow-right"></i></a>
                    <a href="#docs" class="btn btn-secondary">View Documentation</a>
                </div>
            </div>
            <div class="hero-visual">
                <!-- Abstract 3D Matrix Illustration Placeholder -->
                <div class="visual-placeholder glow-effect">
                    <div class="abstract-grid-art"></div>
                </div>
            </div>
        </div>
    </header>

    <!-- Features Grid -->
    <section id="features" class="features-section">
        <div class="container">
            <h2 class="section-title">Key Capabilities</h2>
            <div class="features-grid">
                <!-- Feature 1 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-table"></i>
                    </div>
                    <h3>Structured Generation</h3>
                    <p>Define specific row and column headers to create precise, rigid output formats suitable for data analysis.</p>
                </div>
                <!-- Feature 2 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-magic-wand"></i>
                    </div>
                    <h3>Contextual Computation</h3>
                    <p>Utilize dynamic query templates with <code>{row}</code> and <code>{column}</code> placeholders to generate context-aware cell content.</p>
                </div>
                <!-- Feature 3 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-cpu"></i>
                    </div>
                    <h3>Partitioned Processing</h3>
                    <p>Efficiently handles large matrices by breaking tasks into configurable partition sizes for parallel execution.</p>
                </div>
            </div>
        </div>
    </section>

    <!-- Interactive Simulator -->
    <section id="simulator" class="simulator-section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">Interactive Simulator</h2>
                <p>Configure the execution parameters to generate a live table.</p>
            </div>

            <div class="simulator-interface">
                <!-- Left Column: Inputs -->
                <div class="sim-config glass-panel">
                    <div class="panel-header">
                        <h3><i class="ph-bold ph-sliders-horizontal"></i> Configuration</h3>
                    </div>
                    <form id="task-config-form">
                        
                        <!-- Task Description -->
                        <div class="form-group">
                            <label for="task_description">Context / Description</label>
                            <textarea id="task_description" rows="2" placeholder="E.g., Compare the following smartphone models based on technical specifications."></textarea>
                        </div>

                        <!-- Row Headers -->
                        <div class="form-group">
                            <label for="rows_input">Row Headers (One per line)</label>
                            <textarea id="rows_input" rows="4" placeholder="iPhone 15&#10;Samsung Galaxy S24&#10;Google Pixel 8"></textarea>
                        </div>

                        <!-- Column Headers -->
                        <div class="form-group">
                            <label for="cols_input">Column Headers (One per line)</label>
                            <textarea id="cols_input" rows="4" placeholder="Price&#10;Battery Life&#10;Camera MP"></textarea>
                        </div>

                        <!-- Cell Query Template -->
                        <div class="form-group">
                            <label for="cell_query">Cell Query Template</label>
                            <div class="input-hint">Use {row} and {column} as placeholders.</div>
                            <input type="text" id="cell_query" value="What is the {column} of the {row}? Provide a short value.">
                        </div>

                        <!-- Partition Size (Type Config) -->
                        <div class="form-group">
                            <label for="partition_size">Partition Size: <span id="partition-val">2</span></label>
                            <input type="range" id="partition_size" min="1" max="10" value="2" class="slider">
                        </div>

                        <button type="submit" class="btn btn-primary btn-block" id="run-simulation">
                            <i class="ph-bold ph-play"></i> Generate Table
                        </button>
                    </form>
                </div>

                <!-- Right Column: Outputs -->
                <div class="sim-output glass-panel">
                    <div class="tabs-nav">
                        <button class="tab-btn active" data-tab="preview">Preview</button>
                        <button class="tab-btn" data-tab="json">JSON Data</button>
                        <button class="tab-btn" data-tab="logs">Execution Logs</button>
                    </div>

                    <div class="tab-content">
                        <!-- Tab: Preview (HTML Table) -->
                        <div id="preview" class="tab-pane active">
                            <div class="empty-state">
                                <i class="ph-duotone ph-table"></i>
                                <p>Configure inputs and run simulation to see results.</p>
                            </div>
                            <div id="table-container" class="table-responsive hidden">
                                <!-- Table will be injected here via JS -->
                            </div>
                        </div>

                        <!-- Tab: JSON -->
                        <div id="json" class="tab-pane">
                            <pre><code id="json-output" class="language-json">// JSON output will appear here...</code></pre>
                        </div>

                        <!-- Tab: Logs -->
                        <div id="logs" class="tab-pane">
                            <div class="terminal-window">
                                <div id="log-output" class="terminal-content">
                                    <span class="log-line text-dim">Waiting for execution...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Use Cases -->
    <section id="use-cases" class="use-cases-section">
        <div class="container">
            <h2 class="section-title">When to Use</h2>
            <div class="use-case-list">
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-scales"></i></div>
                    <div class="uc-content">
                        <h4>Comparison Matrices</h4>
                        <p>Perfect for comparing features across multiple products or services side-by-side.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-chart-bar"></i></div>
                    <div class="uc-content">
                        <h4>Data Analysis Tables</h4>
                        <p>Generate metrics vs. categories to spot trends or summarize complex datasets.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-tree-structure"></i></div>
                    <div class="uc-content">
                        <h4>Decision Matrices</h4>
                        <p>Evaluate various options against a set of strict criteria to aid in decision making.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-brand">
                    <i class="ph-fill ph-brain"></i> Cognotik
                </div>
                <div class="footer-links">
                    <span>&copy; 2023 Simiacryptus. All rights reserved.</span>
                </div>
            </div>
        </div>
    </footer>

    <script>
        /* 
           JAVASCRIPT PLACEHOLDER
           - Tab switching logic
           - Range slider value update
           - Simulation logic:
             1. Prevent default form submit
             2. Read inputs (Rows, Cols, Query)
             3. Simulate "Processing partition X/Y" in logs
             4. Generate dummy data based on inputs
             5. Render HTML Table
             6. Render JSON Output
        */
    </script>
</body>
</html>
```
## Generated Images:
- table-compilation-hero-matrix.png: A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). The style is cyberpunk-minimalist, utilizing deep slate blues (#0f172a) and vibrant neon accents to convey "Reasoning" and "Structure". Cinematic lighting with a shallow depth of field.
- partitioned-processing-grid.png: An isometric 3D illustration representing parallel processing. A dark, glass-like surface is etched with a vast grid pattern. Specific rectangular blocks (partitions) within the grid are illuminated in bright neon purple and electric blue, indicating active computation, while surrounding blocks remain dim or matte dark grey. Faint, glowing data streams or binary particles flow vertically into the active partitions. The background is a gradient of deep midnight blue and charcoal.
- holographic-data-projection.png: A futuristic, semi-transparent holographic interface displaying a complex comparison table. The table floats in a dark, atmospheric space. The grid lines are sharp beams of white light, and the "data" inside the cells appears as glowing abstract glyphs or bars in cyan and gold. The perspective is slightly angled, looking up at the data structure to give it a sense of scale and importance. Soft volumetric fog surrounds the projection to emphasize the light beams.
## Instructions:
1. Insert <img> tags at appropriate locations in the HTML where these images should appear
2. Use the given PNG filename (e.g., "filename.png") for the src attribute
3. Add appropriate alt text based on the image description
4. Add appropriate class names for styling
5. Consider the semantic meaning of where each image should go (hero sections, content areas, etc.)
6. Maintain the existing HTML structure and class names
7. Do NOT add any CSS or JavaScript - just insert the <img> tags
## Output Format:
Provide the complete updated HTML structure within a code block:
```html
<!DOCTYPE html>
...
```
```

**Response:**
```html
<!-- 
IMAGE PROMPT DESCRIPTION: 
A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. 
The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. 
Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). 
The style is cyberpunk-minimalist, utilizing deep slate blues and vibrant neon accents to convey "Reasoning" and "Structure".
-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Cognotik Table Compilation Task - Generate structured tables with AI-computed cell values.">
    <title>Table Compilation Engine | Cognotik</title>
    
    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- Icons: Phosphor Icons or similar lightweight SVG library via CDN -->
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <style>
        /* 
           CSS PLACEHOLDER 
           - :root variables for colors (Dark Slate, Neon Cyan, Gold)
           - Reset & Typography
           - Utility classes (flex, grid, glassmorphism)
           - Component styles (Hero, Cards, Simulator, Tabs)
           - Responsive media queries
        */
    </style>
</head>
<body>

    <!-- Navigation -->
    <nav class="navbar glass-panel">
        <div class="container nav-container">
            <div class="logo">
                <i class="ph-fill ph-brain"></i>
                <span>Cognotik</span>
            </div>
            <div class="nav-links">
                <a href="#features">Features</a>
                <a href="#workflow">Workflow</a>
                <a href="#simulator" class="btn-nav-cta">Try Simulator</a>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <header class="hero-section">
        <div class="container hero-content">
            <div class="hero-text">
                <div class="badge">Reasoning Engine</div>
                <h1 class="hero-title">Table Compilation Task</h1>
                <p class="hero-subtitle">
                    Generate structured tables by computing each cell value using AI. 
                    Transform raw dimensions into insightful matrices automatically by defining rows, columns, and context-aware queries.
                </p>
                <div class="hero-actions">
                    <a href="#simulator" class="btn btn-primary">Launch Simulator <i class="ph-bold ph-arrow-right"></i></a>
                    <a href="#docs" class="btn btn-secondary">View Documentation</a>
                </div>
            </div>
            <div class="hero-visual">
                <!-- Abstract 3D Matrix Illustration -->
                <img src="table-compilation-hero-matrix.png" alt="Abstract 3D visualization of a glowing data matrix floating in a deep void" class="hero-image glow-effect">
            </div>
        </div>
    </header>

    <!-- Features Grid -->
    <section id="features" class="features-section">
        <div class="container">
            <h2 class="section-title">Key Capabilities</h2>
            <div class="features-grid">
                <!-- Feature 1 -->
                <div class="feature-card glass-panel">
                    <img src="holographic-data-projection.png" alt="Holographic interface displaying a complex comparison table" class="card-image">
                    <div class="icon-container">
                        <i class="ph-duotone ph-table"></i>
                    </div>
                    <h3>Structured Generation</h3>
                    <p>Define specific row and column headers to create precise, rigid output formats suitable for data analysis.</p>
                </div>
                <!-- Feature 2 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-magic-wand"></i>
                    </div>
                    <h3>Contextual Computation</h3>
                    <p>Utilize dynamic query templates with <code>{row}</code> and <code>{column}</code> placeholders to generate context-aware cell content.</p>
                </div>
                <!-- Feature 3 -->
                <div class="feature-card glass-panel">
                    <img src="partitioned-processing-grid.png" alt="Isometric 3D illustration representing parallel processing partitions" class="card-image">
                    <div class="icon-container">
                        <i class="ph-duotone ph-cpu"></i>
                    </div>
                    <h3>Partitioned Processing</h3>
                    <p>Efficiently handles large matrices by breaking tasks into configurable partition sizes for parallel execution.</p>
                </div>
            </div>
        </div>
    </section>

    <!-- Interactive Simulator -->
    <section id="simulator" class="simulator-section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">Interactive Simulator</h2>
                <p>Configure the execution parameters to generate a live table.</p>
            </div>

            <div class="simulator-interface">
                <!-- Left Column: Inputs -->
                <div class="sim-config glass-panel">
                    <div class="panel-header">
                        <h3><i class="ph-bold ph-sliders-horizontal"></i> Configuration</h3>
                    </div>
                    <form id="task-config-form">
                        
                        <!-- Task Description -->
                        <div class="form-group">
                            <label for="task_description">Context / Description</label>
                            <textarea id="task_description" rows="2" placeholder="E.g., Compare the following smartphone models based on technical specifications."></textarea>
                        </div>

                        <!-- Row Headers -->
                        <div class="form-group">
                            <label for="rows_input">Row Headers (One per line)</label>
                            <textarea id="rows_input" rows="4" placeholder="iPhone 15&#10;Samsung Galaxy S24&#10;Google Pixel 8"></textarea>
                        </div>

                        <!-- Column Headers -->
                        <div class="form-group">
                            <label for="cols_input">Column Headers (One per line)</label>
                            <textarea id="cols_input" rows="4" placeholder="Price&#10;Battery Life&#10;Camera MP"></textarea>
                        </div>

                        <!-- Cell Query Template -->
                        <div class="form-group">
                            <label for="cell_query">Cell Query Template</label>
                            <div class="input-hint">Use {row} and {column} as placeholders.</div>
                            <input type="text" id="cell_query" value="What is the {column} of the {row}? Provide a short value.">
                        </div>

                        <!-- Partition Size (Type Config) -->
                        <div class="form-group">
                            <label for="partition_size">Partition Size: <span id="partition-val">2</span></label>
                            <input type="range" id="partition_size" min="1" max="10" value="2" class="slider">
                        </div>

                        <button type="submit" class="btn btn-primary btn-block" id="run-simulation">
                            <i class="ph-bold ph-play"></i> Generate Table
                        </button>
                    </form>
                </div>

                <!-- Right Column: Outputs -->
                <div class="sim-output glass-panel">
                    <div class="tabs-nav">
                        <button class="tab-btn active" data-tab="preview">Preview</button>
                        <button class="tab-btn" data-tab="json">JSON Data</button>
                        <button class="tab-btn" data-tab="logs">Execution Logs</button>
                    </div>

                    <div class="tab-content">
                        <!-- Tab: Preview (HTML Table) -->
                        <div id="preview" class="tab-pane active">
                            <div class="empty-state">
                                <i class="ph-duotone ph-table"></i>
                                <p>Configure inputs and run simulation to see results.</p>
                            </div>
                            <div id="table-container" class="table-responsive hidden">
                                <!-- Table will be injected here via JS -->
                            </div>
                        </div>

                        <!-- Tab: JSON -->
                        <div id="json" class="tab-pane">
                            <pre><code id="json-output" class="language-json">// JSON output will appear here...</code></pre>
                        </div>

                        <!-- Tab: Logs -->
                        <div id="logs" class="tab-pane">
                            <div class="terminal-window">
                                <div id="log-output" class="terminal-content">
                                    <span class="log-line text-dim">Waiting for execution...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Use Cases -->
    <section id="use-cases" class="use-cases-section">
        <div class="container">
            <h2 class="section-title">When to Use</h2>
            <div class="use-case-list">
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-scales"></i></div>
                    <div class="uc-content">
                        <h4>Comparison Matrices</h4>
                        <p>Perfect for comparing features across multiple products or services side-by-side.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-chart-bar"></i></div>
                    <div class="uc-content">
                        <h4>Data Analysis Tables</h4>
                        <p>Generate metrics vs. categories to spot trends or summarize complex datasets.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-tree-structure"></i></div>
                    <div class="uc-content">
                        <h4>Decision Matrices</h4>
                        <p>Evaluate various options against a set of strict criteria to aid in decision making.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-brand">
                    <i class="ph-fill ph-brain"></i> Cognotik
                </div>
                <div class="footer-links">
                    <span>&copy; 2023 Simiacryptus. All rights reserved.</span>
                </div>
            </div>
        </div>
    </footer>

    <script>
        /* 
           JAVASCRIPT PLACEHOLDER
           - Tab switching logic
           - Range slider value update
           - Simulation logic:
             1. Prevent default form submit
             2. Read inputs (Rows, Cols, Query)
             3. Simulate "Processing partition X/Y" in logs
             4. Generate dummy data based on inputs
             5. Render HTML Table
             6. Render JSON Output
        */
    </script>
</body>
</html>
```

### Step 4: Final HTML Output

```html
<!-- 
IMAGE PROMPT DESCRIPTION: 
A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. 
The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. 
Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). 
The style is cyberpunk-minimalist, utilizing deep slate blues and vibrant neon accents to convey "Reasoning" and "Structure".
-->

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="Cognotik Table Compilation Task - Generate structured tables with AI-computed cell values.">
    <title>Table Compilation Engine | Cognotik</title>
    
    <!-- Fonts: Inter for UI, JetBrains Mono for Code -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    
    <!-- Icons: Phosphor Icons or similar lightweight SVG library via CDN -->
    <script src="https://unpkg.com/@phosphor-icons/web"></script>

    <style>
        /* 
           CSS PLACEHOLDER 
           - :root variables for colors (Dark Slate, Neon Cyan, Gold)
           - Reset & Typography
           - Utility classes (flex, grid, glassmorphism)
           - Component styles (Hero, Cards, Simulator, Tabs)
           - Responsive media queries
        */
    </style>

    <style>
        /* =========================================
           1. VARIABLES & THEME SETUP
           ========================================= */
        :root {
            /* Colors: Deep Slate & Neon Cyan/Gold */
            --bg-dark: #0f172a;       /* Slate 900 */
            --bg-card: #1e293b;       /* Slate 800 */
            --bg-glass: rgba(30, 41, 59, 0.6);
        
            --accent-primary: #06b6d4; /* Cyan 500 */
            --accent-glow: #22d3ee;    /* Cyan 400 */
            --accent-secondary: #f59e0b; /* Amber 500 */
        
            --text-main: #f8fafc;      /* Slate 50 */
            --text-muted: #94a3b8;     /* Slate 400 */
        
            --border-color: rgba(148, 163, 184, 0.15);
            --border-focus: rgba(6, 182, 212, 0.5);
        
            /* Typography */
            --font-sans: 'Inter', system-ui, -apple-system, sans-serif;
            --font-mono: 'JetBrains Mono', monospace;
        
            /* Spacing & Radius */
            --radius-sm: 6px;
            --radius-md: 12px;
            --radius-lg: 16px;
            --container-width: 1200px;
        
            /* Transitions */
            --transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        
        /* =========================================
           2. RESET & BASE STYLES
           ========================================= */
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }
        
        html {
            scroll-behavior: smooth;
        }
        
        body {
            background-color: var(--bg-dark);
            color: var(--text-main);
            font-family: var(--font-sans);
            line-height: 1.6;
            overflow-x: hidden;
            background-image: 
                radial-gradient(circle at 15% 50%, rgba(6, 182, 212, 0.08), transparent 25%),
                radial-gradient(circle at 85% 30%, rgba(245, 158, 11, 0.05), transparent 25%);
        }
        
        a {
            text-decoration: none;
            color: inherit;
            transition: var(--transition);
        }
        
        ul {
            list-style: none;
        }
        
        /* Utility Classes */
        .container {
            max-width: var(--container-width);
            margin: 0 auto;
            padding: 0 2rem;
        }
        
        .glass-panel {
            background: var(--bg-glass);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-lg);
            box-shadow: 0 4px 30px rgba(0, 0, 0, 0.1);
        }
        
        .section-title {
            font-size: 2rem;
            font-weight: 700;
            margin-bottom: 1rem;
            background: linear-gradient(to right, var(--text-main), var(--text-muted));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        
        /* =========================================
           3. NAVIGATION
           ========================================= */
        .navbar {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            z-index: 1000;
            border-bottom: 1px solid var(--border-color);
            border-radius: 0;
        }
        
        .nav-container {
            display: flex;
            justify-content: space-between;
            align-items: center;
            height: 70px;
        }
        
        .logo {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-weight: 700;
            font-size: 1.25rem;
            color: var(--text-main);
        }
        
        .logo i {
            color: var(--accent-primary);
            font-size: 1.5rem;
        }
        
        .nav-links {
            display: flex;
            align-items: center;
            gap: 2rem;
        }
        
        .nav-links a {
            color: var(--text-muted);
            font-size: 0.95rem;
            font-weight: 500;
        }
        
        .nav-links a:hover {
            color: var(--accent-primary);
        }
        
        .btn-nav-cta {
            padding: 0.5rem 1.25rem;
            background: rgba(6, 182, 212, 0.1);
            color: var(--accent-primary) !important;
            border: 1px solid var(--accent-primary);
            border-radius: var(--radius-sm);
        }
        
        .btn-nav-cta:hover {
            background: var(--accent-primary);
            color: var(--bg-dark) !important;
            box-shadow: 0 0 15px rgba(6, 182, 212, 0.4);
        }
        
        /* =========================================
           4. HERO SECTION
           ========================================= */
        .hero-section {
            padding: 140px 0 80px;
            position: relative;
            overflow: hidden;
        }
        
        .hero-content {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 4rem;
            align-items: center;
        }
        
        .badge {
            display: inline-block;
            padding: 0.25rem 0.75rem;
            background: rgba(245, 158, 11, 0.1);
            color: var(--accent-secondary);
            border: 1px solid rgba(245, 158, 11, 0.3);
            border-radius: 100px;
            font-size: 0.8rem;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 1.5rem;
        }
        
        .hero-title {
            font-size: 3.5rem;
            line-height: 1.1;
            margin-bottom: 1.5rem;
            font-weight: 800;
        }
        
        .hero-subtitle {
            font-size: 1.125rem;
            color: var(--text-muted);
            margin-bottom: 2.5rem;
            max-width: 540px;
        }
        
        .hero-actions {
            display: flex;
            gap: 1rem;
        }
        
        .btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
            padding: 0.75rem 1.5rem;
            border-radius: var(--radius-sm);
            font-weight: 600;
            cursor: pointer;
            transition: var(--transition);
            border: none;
            font-family: var(--font-sans);
        }
        
        .btn-primary {
            background: var(--accent-primary);
            color: var(--bg-dark);
        }
        
        .btn-primary:hover {
            background: var(--accent-glow);
            box-shadow: 0 0 20px rgba(6, 182, 212, 0.4);
            transform: translateY(-2px);
        }
        
        .btn-secondary {
            background: transparent;
            border: 1px solid var(--border-color);
            color: var(--text-main);
        }
        
        .btn-secondary:hover {
            border-color: var(--text-muted);
            background: rgba(255, 255, 255, 0.05);
        }
        
        /* Hero Visual - Abstract Matrix */
        .visual-placeholder {
            width: 100%;
            height: 400px;
            position: relative;
            border-radius: var(--radius-lg);
            background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
            border: 1px solid var(--border-color);
            overflow: hidden;
            box-shadow: 0 20px 50px rgba(0,0,0,0.5);
        }
        
        .abstract-grid-art {
            position: absolute;
            inset: -50%;
            background-image: 
                linear-gradient(rgba(6, 182, 212, 0.3) 1px, transparent 1px),
                linear-gradient(90deg, rgba(6, 182, 212, 0.3) 1px, transparent 1px);
            background-size: 40px 40px;
            transform: perspective(500px) rotateX(60deg) translateY(-100px) translateZ(-100px);
            animation: gridMove 20s linear infinite;
            mask-image: radial-gradient(circle, black 30%, transparent 70%);
            -webkit-mask-image: radial-gradient(circle, black 30%, transparent 70%);
        }
        
        @keyframes gridMove {
            0% { transform: perspective(500px) rotateX(60deg) translateY(0) translateZ(-100px); }
            100% { transform: perspective(500px) rotateX(60deg) translateY(40px) translateZ(-100px); }
        }
        
        .glow-effect::after {
            content: '';
            position: absolute;
            top: 50%;
            left: 50%;
            width: 150px;
            height: 150px;
            background: var(--accent-primary);
            filter: blur(80px);
            opacity: 0.4;
            transform: translate(-50%, -50%);
            animation: pulse 4s ease-in-out infinite;
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 0.3; transform: translate(-50%, -50%) scale(1); }
            50% { opacity: 0.5; transform: translate(-50%, -50%) scale(1.2); }
        }
        
        /* =========================================
           5. FEATURES GRID
           ========================================= */
        .features-section {
            padding: 80px 0;
        }
        
        .features-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 2rem;
            margin-top: 3rem;
        }
        
        .feature-card {
            padding: 2rem;
            transition: var(--transition);
            position: relative;
            overflow: hidden;
        }
        
        .feature-card:hover {
            transform: translateY(-5px);
            border-color: var(--accent-primary);
            box-shadow: 0 10px 30px rgba(6, 182, 212, 0.1);
        }
        
        .icon-container {
            width: 50px;
            height: 50px;
            background: rgba(6, 182, 212, 0.1);
            border-radius: var(--radius-sm);
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 1.5rem;
            color: var(--accent-primary);
            font-size: 1.5rem;
        }
        
        .feature-card h3 {
            font-size: 1.25rem;
            margin-bottom: 0.75rem;
            color: var(--text-main);
        }
        
        .feature-card p {
            color: var(--text-muted);
            font-size: 0.95rem;
        }
        
        code {
            background: rgba(0, 0, 0, 0.3);
            padding: 0.1rem 0.3rem;
            border-radius: 4px;
            font-family: var(--font-mono);
            color: var(--accent-secondary);
            font-size: 0.85em;
        }
        
        /* =========================================
           6. INTERACTIVE SIMULATOR
           ========================================= */
        .simulator-section {
            padding: 80px 0;
        }
        
        .section-header {
            text-align: center;
            margin-bottom: 3rem;
        }
        
        .section-header p {
            color: var(--text-muted);
        }
        
        .simulator-interface {
            display: grid;
            grid-template-columns: 350px 1fr;
            gap: 1.5rem;
            align-items: start;
        }
        
        /* Left Column: Config */
        .sim-config {
            padding: 1.5rem;
        }
        
        .panel-header {
            margin-bottom: 1.5rem;
            padding-bottom: 1rem;
            border-bottom: 1px solid var(--border-color);
        }
        
        .panel-header h3 {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-size: 1.1rem;
            color: var(--text-main);
        }
        
        .form-group {
            margin-bottom: 1.25rem;
        }
        
        .form-group label {
            display: block;
            font-size: 0.8rem;
            font-weight: 600;
            color: var(--text-muted);
            margin-bottom: 0.5rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .input-hint {
            font-size: 0.75rem;
            color: var(--accent-secondary);
            margin-bottom: 0.5rem;
            margin-top: -0.25rem;
        }
        
        input[type="text"],
        textarea,
        select {
            width: 100%;
            background: rgba(15, 23, 42, 0.6);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-sm);
            padding: 0.75rem;
            color: var(--text-main);
            font-family: var(--font-sans);
            font-size: 0.9rem;
            transition: var(--transition);
        }
        
        textarea {
            resize: vertical;
        }
        
        input:focus,
        textarea:focus {
            outline: none;
            border-color: var(--accent-primary);
            box-shadow: 0 0 0 2px rgba(6, 182, 212, 0.2);
        }
        
        /* Range Slider */
        .slider {
            -webkit-appearance: none;
            width: 100%;
            height: 6px;
            background: var(--bg-dark);
            border-radius: 5px;
            outline: none;
        }
        
        .slider::-webkit-slider-thumb {
            -webkit-appearance: none;
            appearance: none;
            width: 18px;
            height: 18px;
            border-radius: 50%;
            background: var(--accent-primary);
            cursor: pointer;
            box-shadow: 0 0 10px rgba(6, 182, 212, 0.5);
        }
        
        .btn-block {
            width: 100%;
            margin-top: 1rem;
        }
        
        /* Right Column: Output */
        .sim-output {
            min-height: 600px;
            display: flex;
            flex-direction: column;
            overflow: hidden;
        }
        
        .tabs-nav {
            display: flex;
            border-bottom: 1px solid var(--border-color);
            background: rgba(0, 0, 0, 0.2);
        }
        
        .tab-btn {
            padding: 1rem 1.5rem;
            background: transparent;
            border: none;
            color: var(--text-muted);
            font-weight: 600;
            cursor: pointer;
            border-bottom: 2px solid transparent;
            transition: var(--transition);
            font-family: var(--font-sans);
        }
        
        .tab-btn:hover {
            color: var(--text-main);
            background: rgba(255, 255, 255, 0.02);
        }
        
        .tab-btn.active {
            color: var(--accent-primary);
            border-bottom-color: var(--accent-primary);
            background: rgba(6, 182, 212, 0.05);
        }
        
        .tab-content {
            flex: 1;
            padding: 1.5rem;
            position: relative;
            overflow-y: auto;
        }
        
        .tab-pane {
            display: none;
            height: 100%;
        }
        
        .tab-pane.active {
            display: block;
            animation: fadeIn 0.3s ease;
        }
        
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(5px); }
            to { opacity: 1; transform: translateY(0); }
        }
        
        /* Empty State */
        .empty-state {
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            height: 100%;
            color: var(--text-muted);
            text-align: center;
        }
        
        .empty-state i {
            font-size: 4rem;
            margin-bottom: 1rem;
            opacity: 0.3;
        }
        
        /* Generated Table Styles */
        .table-responsive {
            overflow-x: auto;
        }
        
        table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.9rem;
        }
        
        th, td {
            padding: 1rem;
            text-align: left;
            border-bottom: 1px solid var(--border-color);
        }
        
        th {
            background: rgba(0, 0, 0, 0.2);
            color: var(--accent-primary);
            font-weight: 600;
            white-space: nowrap;
        }
        
        td:first-child {
            font-weight: 600;
            color: var(--text-main);
            background: rgba(0, 0, 0, 0.1);
        }
        
        tr:hover td {
            background: rgba(255, 255, 255, 0.02);
        }
        
        /* JSON Output */
        pre {
            background: rgba(0, 0, 0, 0.3);
            padding: 1rem;
            border-radius: var(--radius-sm);
            overflow-x: auto;
            font-family: var(--font-mono);
            font-size: 0.85rem;
            color: #a5b4fc;
            height: 100%;
        }
        
        /* Terminal Logs */
        .terminal-window {
            background: #000;
            border-radius: var(--radius-sm);
            padding: 1rem;
            height: 100%;
            font-family: var(--font-mono);
            font-size: 0.85rem;
            overflow-y: auto;
            border: 1px solid #333;
        }
        
        .log-line {
            display: block;
            margin-bottom: 0.25rem;
        }
        
        .text-dim { color: #666; }
        .text-info { color: var(--accent-primary); }
        .text-success { color: #10b981; }
        .text-warn { color: var(--accent-secondary); }
        
        /* =========================================
           7. USE CASES
           ========================================= */
        .use-cases-section {
            padding: 80px 0;
            background: rgba(0, 0, 0, 0.2);
        }
        
        .use-case-list {
            display: flex;
            flex-direction: column;
            gap: 1.5rem;
            max-width: 800px;
            margin: 0 auto;
        }
        
        .use-case-item {
            display: flex;
            gap: 1.5rem;
            padding: 1.5rem;
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            align-items: flex-start;
        }
        
        .uc-icon {
            flex-shrink: 0;
            width: 40px;
            height: 40px;
            background: var(--bg-dark);
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--accent-secondary);
            border: 1px solid rgba(245, 158, 11, 0.2);
        }
        
        .uc-content h4 {
            font-size: 1.1rem;
            margin-bottom: 0.5rem;
            color: var(--text-main);
        }
        
        .uc-content p {
            color: var(--text-muted);
            font-size: 0.95rem;
        }
        
        /* =========================================
           8. FOOTER
           ========================================= */
        .footer {
            padding: 3rem 0;
            border-top: 1px solid var(--border-color);
            margin-top: 4rem;
            background: var(--bg-dark);
        }
        
        .footer-content {
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .footer-brand {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-weight: 700;
            color: var(--text-muted);
        }
        
        .footer-links {
            color: var(--text-muted);
            font-size: 0.9rem;
        }
        
        /* =========================================
           9. RESPONSIVE DESIGN
           ========================================= */
        @media (max-width: 992px) {
            .hero-content {
                grid-template-columns: 1fr;
                text-align: center;
            }
        
            .hero-subtitle {
                margin: 0 auto 2.5rem;
            }
        
            .hero-actions {
                justify-content: center;
            }
        
            .simulator-interface {
                grid-template-columns: 1fr;
            }
        
            .sim-output {
                min-height: 500px;
            }
        }
        
        @media (max-width: 768px) {
            .hero-title {
                font-size: 2.5rem;
            }
        
            .nav-links {
                display: none; /* Simple hide for mobile, would need hamburger menu in full impl */
            }
        
            .features-grid {
                grid-template-columns: 1fr;
            }
        
            .footer-content {
                flex-direction: column;
                gap: 1rem;
            }
        }
    </style>
<!-- Generated Images:
     - table-compilation-hero-matrix.png: A high-tech, abstract 3D visualization of a glowing data matrix floating in a deep void. The matrix consists of translucent cubic nodes connected by neon cyan and gold laser lines, forming a structured grid. Some nodes are fully illuminated (representing computed data), while others are wireframes being filled by streams of light (representing AI generation). The style is cyberpunk-minimalist, utilizing deep slate blues (#0f172a) and vibrant neon accents to convey "Reasoning" and "Structure". Cinematic lighting with a shallow depth of field.
     - partitioned-processing-grid.png: An isometric 3D illustration representing parallel processing. A dark, glass-like surface is etched with a vast grid pattern. Specific rectangular blocks (partitions) within the grid are illuminated in bright neon purple and electric blue, indicating active computation, while surrounding blocks remain dim or matte dark grey. Faint, glowing data streams or binary particles flow vertically into the active partitions. The background is a gradient of deep midnight blue and charcoal.
     - holographic-data-projection.png: A futuristic, semi-transparent holographic interface displaying a complex comparison table. The table floats in a dark, atmospheric space. The grid lines are sharp beams of white light, and the "data" inside the cells appears as glowing abstract glyphs or bars in cyan and gold. The perspective is slightly angled, looking up at the data structure to give it a sense of scale and importance. Soft volumetric fog surrounds the projection to emphasize the light beams.
-->
</head>
<body>

    <!-- Navigation -->
    <nav class="navbar glass-panel">
        <div class="container nav-container">
            <div class="logo">
                <i class="ph-fill ph-brain"></i>
                <span>Cognotik</span>
            </div>
            <div class="nav-links">
                <a href="#features">Features</a>
                <a href="#workflow">Workflow</a>
                <a href="#simulator" class="btn-nav-cta">Try Simulator</a>
            </div>
        </div>
    </nav>

    <!-- Hero Section -->
    <header class="hero-section">
        <div class="container hero-content">
            <div class="hero-text">
                <div class="badge">Reasoning Engine</div>
                <h1 class="hero-title">Table Compilation Task</h1>
                <p class="hero-subtitle">
                    Generate structured tables by computing each cell value using AI. 
                    Transform raw dimensions into insightful matrices automatically by defining rows, columns, and context-aware queries.
                </p>
                <div class="hero-actions">
                    <a href="#simulator" class="btn btn-primary">Launch Simulator <i class="ph-bold ph-arrow-right"></i></a>
                    <a href="#docs" class="btn btn-secondary">View Documentation</a>
                </div>
            </div>
            <div class="hero-visual">
                <!-- Abstract 3D Matrix Illustration -->
                <img src="table-compilation-hero-matrix.png" alt="Abstract 3D visualization of a glowing data matrix floating in a deep void" class="hero-image glow-effect">
            </div>
        </div>
    </header>

    <!-- Features Grid -->
    <section id="features" class="features-section">
        <div class="container">
            <h2 class="section-title">Key Capabilities</h2>
            <div class="features-grid">
                <!-- Feature 1 -->
                <div class="feature-card glass-panel">
                    <img src="holographic-data-projection.png" alt="Holographic interface displaying a complex comparison table" class="card-image">
                    <div class="icon-container">
                        <i class="ph-duotone ph-table"></i>
                    </div>
                    <h3>Structured Generation</h3>
                    <p>Define specific row and column headers to create precise, rigid output formats suitable for data analysis.</p>
                </div>
                <!-- Feature 2 -->
                <div class="feature-card glass-panel">
                    <div class="icon-container">
                        <i class="ph-duotone ph-magic-wand"></i>
                    </div>
                    <h3>Contextual Computation</h3>
                    <p>Utilize dynamic query templates with <code>{row}</code> and <code>{column}</code> placeholders to generate context-aware cell content.</p>
                </div>
                <!-- Feature 3 -->
                <div class="feature-card glass-panel">
                    <img src="partitioned-processing-grid.png" alt="Isometric 3D illustration representing parallel processing partitions" class="card-image">
                    <div class="icon-container">
                        <i class="ph-duotone ph-cpu"></i>
                    </div>
                    <h3>Partitioned Processing</h3>
                    <p>Efficiently handles large matrices by breaking tasks into configurable partition sizes for parallel execution.</p>
                </div>
            </div>
        </div>
    </section>

    <!-- Interactive Simulator -->
    <section id="simulator" class="simulator-section">
        <div class="container">
            <div class="section-header">
                <h2 class="section-title">Interactive Simulator</h2>
                <p>Configure the execution parameters to generate a live table.</p>
            </div>

            <div class="simulator-interface">
                <!-- Left Column: Inputs -->
                <div class="sim-config glass-panel">
                    <div class="panel-header">
                        <h3><i class="ph-bold ph-sliders-horizontal"></i> Configuration</h3>
                    </div>
                    <form id="task-config-form">
                        
                        <!-- Task Description -->
                        <div class="form-group">
                            <label for="task_description">Context / Description</label>
                            <textarea id="task_description" rows="2" placeholder="E.g., Compare the following smartphone models based on technical specifications."></textarea>
                        </div>

                        <!-- Row Headers -->
                        <div class="form-group">
                            <label for="rows_input">Row Headers (One per line)</label>
                            <textarea id="rows_input" rows="4" placeholder="iPhone 15&#10;Samsung Galaxy S24&#10;Google Pixel 8"></textarea>
                        </div>

                        <!-- Column Headers -->
                        <div class="form-group">
                            <label for="cols_input">Column Headers (One per line)</label>
                            <textarea id="cols_input" rows="4" placeholder="Price&#10;Battery Life&#10;Camera MP"></textarea>
                        </div>

                        <!-- Cell Query Template -->
                        <div class="form-group">
                            <label for="cell_query">Cell Query Template</label>
                            <div class="input-hint">Use {row} and {column} as placeholders.</div>
                            <input type="text" id="cell_query" value="What is the {column} of the {row}? Provide a short value.">
                        </div>

                        <!-- Partition Size (Type Config) -->
                        <div class="form-group">
                            <label for="partition_size">Partition Size: <span id="partition-val">2</span></label>
                            <input type="range" id="partition_size" min="1" max="10" value="2" class="slider">
                        </div>

                        <button type="submit" class="btn btn-primary btn-block" id="run-simulation">
                            <i class="ph-bold ph-play"></i> Generate Table
                        </button>
                    </form>
                </div>

                <!-- Right Column: Outputs -->
                <div class="sim-output glass-panel">
                    <div class="tabs-nav">
                        <button class="tab-btn active" data-tab="preview">Preview</button>
                        <button class="tab-btn" data-tab="json">JSON Data</button>
                        <button class="tab-btn" data-tab="logs">Execution Logs</button>
                    </div>

                    <div class="tab-content">
                        <!-- Tab: Preview (HTML Table) -->
                        <div id="preview" class="tab-pane active">
                            <div class="empty-state">
                                <i class="ph-duotone ph-table"></i>
                                <p>Configure inputs and run simulation to see results.</p>
                            </div>
                            <div id="table-container" class="table-responsive hidden">
                                <!-- Table will be injected here via JS -->
                            </div>
                        </div>

                        <!-- Tab: JSON -->
                        <div id="json" class="tab-pane">
                            <pre><code id="json-output" class="language-json">// JSON output will appear here...</code></pre>
                        </div>

                        <!-- Tab: Logs -->
                        <div id="logs" class="tab-pane">
                            <div class="terminal-window">
                                <div id="log-output" class="terminal-content">
                                    <span class="log-line text-dim">Waiting for execution...</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Use Cases -->
    <section id="use-cases" class="use-cases-section">
        <div class="container">
            <h2 class="section-title">When to Use</h2>
            <div class="use-case-list">
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-scales"></i></div>
                    <div class="uc-content">
                        <h4>Comparison Matrices</h4>
                        <p>Perfect for comparing features across multiple products or services side-by-side.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-chart-bar"></i></div>
                    <div class="uc-content">
                        <h4>Data Analysis Tables</h4>
                        <p>Generate metrics vs. categories to spot trends or summarize complex datasets.</p>
                    </div>
                </div>
                <div class="use-case-item">
                    <div class="uc-icon"><i class="ph-fill ph-tree-structure"></i></div>
                    <div class="uc-content">
                        <h4>Decision Matrices</h4>
                        <p>Evaluate various options against a set of strict criteria to aid in decision making.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-brand">
                    <i class="ph-fill ph-brain"></i> Cognotik
                </div>
                <div class="footer-links">
                    <span>&copy; 2023 Simiacryptus. All rights reserved.</span>
                </div>
            </div>
        </div>
    </footer>

    <script>
        /* 
           JAVASCRIPT PLACEHOLDER
           - Tab switching logic
           - Range slider value update
           - Simulation logic:
             1. Prevent default form submit
             2. Read inputs (Rows, Cols, Query)
             3. Simulate "Processing partition X/Y" in logs
             4. Generate dummy data based on inputs
             5. Render HTML Table
             6. Render JSON Output
        */
    </script>

    <script>
        document.addEventListener('DOMContentLoaded', () => {
            // --- UI Initialization ---
        
            // 1. Tab Switching Logic
            const tabButtons = document.querySelectorAll('.tab-btn');
            const tabPanes = document.querySelectorAll('.tab-pane');
        
            function switchTab(tabId) {
                // Remove active state from all buttons and panes
                tabButtons.forEach(btn => btn.classList.remove('active'));
                tabPanes.forEach(pane => pane.classList.remove('active'));
        
                // Activate target
                const targetBtn = document.querySelector(`.tab-btn[data-tab="${tabId}"]`);
                const targetPane = document.getElementById(tabId);
        
                if (targetBtn && targetPane) {
                    targetBtn.classList.add('active');
                    targetPane.classList.add('active');
                }
            }
        
            tabButtons.forEach(btn => {
                btn.addEventListener('click', () => {
                    switchTab(btn.dataset.tab);
                });
            });
        
            // 2. Range Slider Logic
            const partitionSlider = document.getElementById('partition_size');
            const partitionDisplay = document.getElementById('partition-val');
        
            if (partitionSlider && partitionDisplay) {
                partitionSlider.addEventListener('input', (e) => {
                    partitionDisplay.textContent = e.target.value;
                });
            }
        
            // --- Simulation Logic ---
        
            const form = document.getElementById('task-config-form');
            const runBtn = document.getElementById('run-simulation');
            const logOutput = document.getElementById('log-output');
            const tableContainer = document.getElementById('table-container');
            const emptyState = document.querySelector('.empty-state');
            const jsonOutput = document.getElementById('json-output');
        
            // Helper: Add line to terminal log
            function log(message, type = 'info') {
                const line = document.createElement('div');
                line.className = `log-line ${type}`;
                const timestamp = new Date().toLocaleTimeString('en-US', { hour12: false });
        
                // Style based on type
                let colorClass = 'text-dim'; // default
                if (type === 'success') colorClass = 'text-success'; // You'd need to add this CSS or use inline style
                if (type === 'error') colorClass = 'text-error';
        
                line.innerHTML = `<span style="opacity:0.5">[${timestamp}]</span> ${message}`;
                logOutput.appendChild(line);
        
                // Auto-scroll to bottom
                const terminalWindow = document.querySelector('.terminal-window');
                if (terminalWindow) terminalWindow.scrollTop = terminalWindow.scrollHeight;
            }
        
            // Helper: Mock AI Value Generation
            // Generates plausible-looking data based on column headers to make the demo feel real
            function generateMockCell(row, col, query) {
                const q = (query + " " + col).toLowerCase();
        
                if (q.includes('price') || q.includes('cost')) return '$' + (Math.floor(Math.random() * 500) + 500);
                if (q.includes('battery')) return (Math.floor(Math.random() * 10) + 15) + ' hours';
                if (q.includes('camera') || q.includes('mp')) return (Math.floor(Math.random() * 50) + 12) + ' MP';
                if (q.includes('screen') || q.includes('display')) return (Math.random() * 1 + 6).toFixed(1) + '" OLED';
                if (q.includes('processor') || q.includes('cpu')) return 'Snapdragon ' + (Math.floor(Math.random() * 8) + 8) + ' Gen ' + (Math.floor(Math.random() * 3) + 1);
                if (q.includes('ram')) return [8, 12, 16][Math.floor(Math.random() * 3)] + ' GB';
        
                // Fallback generic value
                return `Computed(${row.substring(0,3)}:${col.substring(0,3)})`;
            }
        
            // Main Execution Handler
            form.addEventListener('submit', async (e) => {
                e.preventDefault();
        
                // 1. Get Inputs
                const rowsRaw = document.getElementById('rows_input').value;
                const colsRaw = document.getElementById('cols_input').value;
                const queryTemplate = document.getElementById('cell_query').value;
                const partitionSize = parseInt(partitionSlider.value);
                const description = document.getElementById('task_description').value;
        
                // Parse lists (split by newline and remove empty)
                const rows = rowsRaw.split('\n').map(s => s.trim()).filter(s => s.length > 0);
                const cols = colsRaw.split('\n').map(s => s.trim()).filter(s => s.length > 0);
        
                // Validation
                if (rows.length === 0 || cols.length === 0) {
                    alert("Please define at least one row and one column.");
                    return;
                }
        
                // 2. Reset UI State
                runBtn.disabled = true;
                runBtn.innerHTML = '<i class="ph-bold ph-spinner ph-spin"></i> Processing...';
                logOutput.innerHTML = ''; // Clear logs
                switchTab('logs'); // Auto-switch to logs to show progress
        
                log(`Initializing TableCompilationTask...`);
                log(`Configuration: ${rows.length} rows, ${cols.length} columns.`);
                log(`Partition Size: ${partitionSize}x${partitionSize}`);
                if(description) log(`Context loaded: "${description.substring(0, 30)}..."`);
        
                // 3. Simulate Partitioned Processing
                // Calculate chunks based on partition size (mimicking Kotlin logic)
                const rowChunks = Math.ceil(rows.length / partitionSize);
                const colChunks = Math.ceil(cols.length / partitionSize);
                const totalPartitions = rowChunks * colChunks;
        
                // Data storage
                const tableData = Array(rows.length).fill(null).map(() => Array(cols.length).fill(null));
        
                let completedPartitions = 0;
        
                // Async Simulation Loop
                for (let rChunk = 0; rChunk < rowChunks; rChunk++) {
                    for (let cChunk = 0; cChunk < colChunks; cChunk++) {
                
                        // Simulate network/processing delay
                        await new Promise(resolve => setTimeout(resolve, 600)); 
                
                        completedPartitions++;
                        log(`Processing partition ${completedPartitions}/${totalPartitions}...`);
        
                        // Determine bounds for this partition
                        const rStart = rChunk * partitionSize;
                        const rEnd = Math.min(rStart + partitionSize, rows.length);
                        const cStart = cChunk * partitionSize;
                        const cEnd = Math.min(cStart + partitionSize, cols.length);
        
                        // "Process" cells in this partition
                        for (let r = rStart; r < rEnd; r++) {
                            for (let c = cStart; c < cEnd; c++) {
                                const cellValue = generateMockCell(rows[r], cols[c], queryTemplate);
                                tableData[r][c] = cellValue;
                        
                                // Occasionally log specific query generation to show "Reasoning"
                                if (Math.random() > 0.7) {
                                    const specificQuery = queryTemplate
                                        .replace('{row}', rows[r])
                                        .replace('{column}', cols[c]);
                                    log(`> Querying Agent: "${specificQuery}"`, 'dim');
                                }
                            }
                        }
                    }
                }
        
                log(`Compilation complete. Formatting output...`, 'success');
        
                // 4. Render Outputs
        
                // A. HTML Table
                let htmlTable = `<table class="generated-table">`;
                // Header
                htmlTable += `<thead><tr><th></th>${cols.map(c => `<th>${c}</th>`).join('')}</tr></thead>`;
                // Body
                htmlTable += `<tbody>`;
                rows.forEach((rowHeader, rIdx) => {
                    htmlTable += `<tr><th>${rowHeader}</th>`;
                    tableData[rIdx].forEach(cellVal => {
                        htmlTable += `<td>${cellVal}</td>`;
                    });
                    htmlTable += `</tr>`;
                });
                htmlTable += `</tbody></table>`;
        
                tableContainer.innerHTML = htmlTable;
                tableContainer.classList.remove('hidden');
                emptyState.style.display = 'none';
        
                // B. JSON Output
                const jsonResult = rows.map((rowHeader, rIdx) => {
                    const rowObj = { "Row": rowHeader };
                    cols.forEach((colHeader, cIdx) => {
                        rowObj[colHeader] = tableData[rIdx][cIdx];
                    });
                    return rowObj;
                });
                jsonOutput.textContent = JSON.stringify(jsonResult, null, 2);
        
                // 5. Finalize
                runBtn.disabled = false;
                runBtn.innerHTML = '<i class="ph-bold ph-play"></i> Generate Table';
        
                // Switch to preview to show result
                setTimeout(() => switchTab('preview'), 500);
            });
        });
    </script>
</body>
</html>
```

**Result:** Successfully wrote TableCompilationTask.html (auto-applied)
