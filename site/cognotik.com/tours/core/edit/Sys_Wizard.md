# 🧙 System Wizard

## Overview

The **System Wizard** is a Cognotik application that lets you accomplish system-level tasks — installing software, investigating disk usage, managing processes, and more — without writing shell scripts by hand. Instead, you describe your goal in plain language, and an AI model generates the appropriate shell script for you.

## How It Works

The System Wizard follows a straightforward pipeline: **define a goal → configure settings → generate a script → run and auto-fix**.

### 1. Define Your Goal

Start on the **Goal** tab and type a plain-language description of what you want to accomplish. For example:

> *List running processes*

Save the goal to proceed.

### 2. Choose Your Platform

The System Wizard supports multiple platforms:

| Platform       | Shell        | OS              |
|----------------|--------------|-----------------|
| **Windows**    | PowerShell   | Windows         |
| **Shell**      | Bash         | Linux / macOS   |

Select the platform that matches your environment.

### 3. Configure the AI Model

In the **Settings** tab, select the AI model that will generate your script. In the demo, **Gemini 3 Flash Preview** is used, but any configured model can be selected here.

### 4. Generate the Script

Navigate to the **Pipeline** tab and trigger script generation. The AI model interprets your goal and produces a shell script tailored to your selected platform. You can:

- **Copy the script** to use directly in your own terminal or shell session.
- **Click "Run and Fix"** to execute the script within Cognotik's managed environment.

### 5. Run and Auto-Fix

When you choose **Run and Fix**, Cognotik opens an interactive execution session:

- The generated script is executed immediately.
- If the script **succeeds**, the UI indicates that the command completed successfully.
- If an **error occurs**, the system automatically analyzes the error output and attempts to generate a corrected script. You can then retry execution from the same interface.

This iterative loop — run, detect errors, fix, retry — continues until the script succeeds or you choose to stop.

### 6. View Results

After a successful execution, the UI confirms that the command succeeded. Note that **command output is not displayed directly in the main UI**; to view the full output (e.g., the list of running processes), navigate into the **session log** for that execution.

## Demo Walkthrough

In the accompanying video, the following steps are demonstrated:

1. The goal **"List running processes"** is entered on the Goal tab.
2. The platform is set to **Windows** (PowerShell).
3. **Gemini 3 Flash Preview** is selected as the AI model in Settings.
4. A PowerShell script is generated via the Pipeline tab.
5. **Run and Fix** is clicked, launching an execution session.
6. The script executes successfully on the first attempt, listing all running processes.
7. The session log is opened to view the detailed process list output.

## Key Takeaways

- **No scripting knowledge required** — Describe what you want in natural language and let the AI handle the syntax.
- **Cross-platform support** — Works with PowerShell on Windows and Bash on Linux/macOS.
- **Automatic error recovery** — The auto-fix loop means you don't need to manually debug failed scripts.
- **Safe execution environment** — Review generated scripts before running, and inspect results in the session log.