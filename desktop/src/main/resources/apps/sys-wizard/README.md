# 🧙 System Wizard

A wizard-style app that lets you describe a goal in plain language, then generates and iteratively fixes a shell script to accomplish it — all through an interactive web UI.

## Overview

System Wizard provides a three-stage pipeline:

1. **Define a Goal** — Describe what you want to accomplish in natural language.
2. **Generate Script** — AI reads your goal and writes a shell script to achieve it.
3. **Run & Auto-Fix** — The script is executed, and any errors are automatically fixed in a loop until it succeeds.

You can run each stage individually or execute the entire pipeline in one click.

## Structure

```
sys-wizard/
├── app.html              # Main application UI
├── app.js                # Application logic (navigation, file I/O, pipeline orchestration)
├── style.css             # Dark-themed responsive styles
├── marked.min.js         # Markdown rendering library
├── goal.md               # User-defined objective (created at runtime)
├── code/
│   ├── script.sh         # Generated shell script (created by code_op)
│   └── fix_log.md        # Execution & auto-fix log (created by run_op)
└── ops/
    ├── code_op.md        # Operation: generate script from goal
    └── run_op.md         # Operation: run script with auto-fix
```

## Operations

### code_op

- **Input:** `goal.md`
- **Output:** `code/script.sh`
- Reads the goal and generates a shell script that implements it.

### run_op

- **Input:** `code/script.sh`
- **Output:** `code/fix_log.md`
- Executes the generated shell script and automatically fixes any errors encountered. Uses the `AutoFix` task type to iteratively run and repair the script until it succeeds. May also modify `code/script.sh` during the fix cycle.

## UI Sections

### 📋 Goal
A text editor where you describe your objective in plain language. The goal is saved to `goal.md`.

### ⚙️ Pipeline
- **Pipeline Overview** — Visual diagram showing the status of each stage (Ready, Running, Done, Error).
- **Step 1: Generate Shell Script** — Triggers `code_op` and displays the generated script.
- **Step 2: Run & Auto-Fix** — Triggers `run_op`, shows the execution log and final script.
- **Run Entire Pipeline** — Saves the goal, generates the script, and runs it with auto-fix in sequence. Includes a live batch log with links to monitor running sessions.

### 📊 Results
Tabbed view of all outputs:
- **Script** — The generated (and possibly auto-fixed) shell script with copy-to-clipboard support.
- **Execution Log** — The full run and fix log rendered as Markdown.
- **Goal** — The original goal for reference.

## Features

- **Session monitoring** — While operations are running, live links are provided to monitor the AI session in real time via the proxy endpoint.
- **Status polling** — The UI polls `docops.status.json` to track task progress and update badges and stage indicators automatically.
- **Auto-save** — The goal is automatically saved before any operation runs.
- **Responsive design** — Works on desktop and mobile with a dark theme.
- **Markdown rendering** — Logs and goals are rendered as formatted Markdown; scripts are displayed with syntax-appropriate formatting.

## Usage

1. Open the app in your browser.
2. On the **Goal** tab, describe what you want the script to do.
3. Either:
   - Use the **Pipeline** tab to run each step individually, or
   - Click **▶ Run Entire Pipeline** to execute everything in one click.
4. View results on the **Results** tab, or monitor live sessions via the provided links.
5. Copy the final script to your clipboard from the Results tab.