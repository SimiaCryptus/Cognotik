# 🏭 WebApp Factory

Generate complete, runnable web applications from natural language descriptions — no coding required. The Web App Factory transforms your idea into a full project with HTML, CSS, and JavaScript, ready to launch in the browser.

## Overview

The Web App Factory application lets you describe what you want to build in plain language, then uses AI to generate a complete web application. The demo walks through building a **graphing calculator** from scratch, launching it, and updating it with new features — all through natural language instructions.

## Getting Started

### 1. Describe Your Idea

Open the Web App Factory and enter a description of the application you want to build. For this demo, the input is:

> **Implements a graphing calculator**

Save your idea to proceed.

### 2. Select Models

Before building, ensure your models are configured:

- **Text Model** — Select an AI model for code generation. In this demo, **Gemini 3 Flash Preview** is used.
- **Image Model** — An image model slot must be selected even if the project doesn't use image generation. Any model can fill this slot.

### 3. Build the Web App

Navigate to the **Pipeline** and select **Build Web App**. This is the only step in the pipeline, but it is a comprehensive one:

1. **Project Plan Generation** — The system creates a structured project plan broken into tasks.
2. **Dependency-Aware Execution** — Tasks are executed in order, respecting their dependencies.

In the demo, the project plan consists of **5 tasks**:

| Task | Description |
|------|-------------|
| 1 | Create foundational project documents |
| 2 | Implement HTML structure |
| 3 | Implement visual styling |
| 4 | Implement JavaScript functionality |
| 5 | Finalize and integrate |

The task graph nodes are visible in the UI, and individual task details can be inspected as they execute.

### 4. Review the Results

Once implementation completes:

- The **Web App UI** updates to reflect the generated project.
- A **README** file appears in the project root describing the application.
- **Git support** is integrated — the project has version control from the start.
- A **Download ZIP** option is available to export the project.

### 5. Launch the App

Click **Launch** to open the generated web application in the browser. The graphing calculator is now live and functional.

## Testing the Graphing Calculator

The generated calculator supports mathematical expressions plotted as graphs. Several expressions are tested in the demo:

| Expression | Result |
|------------|--------|
| `sin(x)` | Standard sine wave |
| `sin(x) + 10(x)` | Sine wave combined with linear function |
| `sin(x) / x` | Sinc-like function |
| `sin(x) * x` | Amplitude-modulated sine wave |
| `sin(x) ^ x` | Exponential sine — produces an interesting visual pattern |

All expressions render correctly, confirming the calculator works as expected.

## Updating the Web App

The Web App Factory includes an **Updater** that applies changes to an existing project using natural language instructions. This is useful for:

- Adding new features
- Fixing bugs
- Changing visual design
- Any other modification

### Requesting an Update

In the demo, the generated calculator is functional but uses a bright default theme. An update is requested:

> **Implement a dark theme and a theme toggle button**

Save the update notes and run the updater. The system modifies the existing project files to incorporate the requested changes.

### Viewing the Update

After the update completes, refresh the application in the browser:

- A **theme toggle button** now appears in the UI.
- Clicking it switches between **light** and **dark** themes.
- The dark theme is fully applied across all calculator elements.

## Usage Tracking

The Web App Factory tracks token usage and costs for each build and update operation. After completing the demo, usage statistics can be reviewed to see the total cost of the entire project — including both the initial build and the subsequent update.

## Key Features

- **Natural Language Input** — Describe your app idea in plain English.
- **Automated Project Planning** — AI generates a structured task plan with dependency management.
- **Complete Code Generation** — Produces HTML, CSS, and JavaScript for a fully functional application.
- **Built-in Git Support** — Projects include version control from creation.
- **In-Browser Launch** — Test your application immediately without any external tooling.
- **Iterative Updates** — Modify and extend your app through natural language update requests.
- **Project Export** — Download the complete project as a ZIP file.
- **Usage Tracking** — Monitor AI model usage and costs per operation.