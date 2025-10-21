### **Documentation Standards for Task Types**

#### 1. Introduction

The goal of these standards is to create consistent, comprehensive, and user-friendly documentation for every `TaskType` in the Cognitive Task Planning Framework. Good documentation is essential for users to understand what a task does, for planners to know when to use it, and for administrators to configure it correctly.

This document provides a template and best practices for documenting tasks, leveraging the information already present in the source code (such as `@Description` annotations) as a single source of truth.

#### 2. Core Principles

*   **Audience-First:** Documentation should be written with two primary audiences in mind:
  *   **End-Users:** Need to know what the task does, when to use it, and what kind of goal or prompt will invoke it.
  *   **Administrators/Developers:** Need to know how to configure the task at the system level (`OrchestrationConfig`) and understand its specific parameters.
*   **Source-Driven:** Documentation should derive directly from the code wherever possible. The `shortDescription`, `longDescription`, and `@Description` annotations in the task's definition are the source of truth.
*   **Example-Oriented:** Abstract descriptions are not enough. Every task must be documented with clear, practical examples of its configuration and usage.
*   **Discoverable:** Documentation should make it easy to find related tasks or alternatives, helping users build more effective plans.

#### 3. Documentation Template (Markdown)

Each `TaskType` should have a dedicated section or page in the user manual that follows this structure.

```markdown
## Task: <TaskName>

**Category:** <e.g., Code & Execution | File Operations | Reasoning>
**Summary:** <One-sentence summary from `shortDescription`>

### Description

<A user-friendly expansion of the `longDescription`. Explain the task's purpose, what problems it solves, and its key features in prose. This can be adapted from the HTML `longDescription` in the code.>

### When to Use

<Provide clear scenarios for using this task. For example:>
*   Use this task when you need to execute a script and see its output.
*   This is the best choice for running build commands like `mvn install` or `npm run build` and automatically fixing any compilation errors.
*   Use this task to analyze a complex problem by breaking it down into a series of questions and answers.

### Execution Configuration (`<TaskName>ExecutionConfigData`)

These parameters are specified when the task is added to a plan.

| Parameter          | Type                | Required | Default | Description                                            |
|:-------------------|:--------------------|:---------|:--------|:-------------------------------------------------------|
| `<param_name_1>`   | `<String>`          | Yes      | `null`  | <Description from the `@Description` annotation.>      |
| `<param_name_2>`   | `List<String>`      | No       | `[]`    | <Description from the `@Description` annotation.>      |
| `<param_name_3>`   | `Boolean`           | No       | `false` | <Description from the `@Description` annotation.>      |

**Example (Plan Snippet):**
```json
{
  "task_type": "<TaskName>",
  "task_description": "A human-readable description of this step.",
  "task_dependencies": ["previous_task_id"],
  "<param_name_1>": "Example Value",
  "<param_name_2>": ["value1", "value2"]
}
```

### Type Configuration (`<TaskName>TypeConfig`)

These parameters are set by an administrator in the global `OrchestrationConfig` to define the task's default behavior.

| Parameter          | Type                | Required | Default | Description                                            |
|:-------------------|:--------------------|:---------|:--------|:-------------------------------------------------------|
| `<type_param_1>`   | `ApiChatModel`      | No       | `null`  | <Description from the `@Description` annotation.>      |
| `<type_param_2>`   | `List<String>`      | No       | `[]`    | <Description from the `@Description` annotation.>      |

**Example (`OrchestrationConfig` Snippet):**
```json
{
  "taskSettings": {
    "<TaskName>": {
      "task_type": "<TaskName>",
      "<type_param_1>": { "model": "gpt-4" },
      "<type_param_2>": ["/usr/bin/mvn", "/usr/bin/npm"]
    }
  }
}
```

### Output

<Describe the result that the task returns via `resultFn`. Explain its format and content. For example:>

The task returns a string containing the standard output and standard error from the executed command. This output can be used as input for a subsequent `AnalysisTask` to check for specific keywords or summarize the result.

### Related Tasks

*   **`<RelatedTaskName1>`:** <Briefly explain the relationship, e.g., "Use for running shell commands instead of interpreted code.">
*   **`<RelatedTaskName2>`:** <e.g., "A more specialized version of this task for handling build processes.">
```

#### 4. Example: Applying the Standard to `SelfHealingTask`

Here is how the `SelfHealingTask` documentation would look using the template.

---

## Task: SelfHealing

**Category:** Code & Execution
**Summary:** Run a command and automatically fix any issues that arise.

### Description

The `SelfHealingTask` is a powerful tool for executing shell commands that might fail, such as build scripts, linters, or test runners. When a command fails (i.e., returns a non-zero exit code), the task captures the output, analyzes the error, and attempts to generate and apply a code patch to fix the underlying issue. It then re-runs the command to verify the fix.

This is ideal for automating CI/CD pipelines, code maintenance, and complex build processes. Key features include:
*   Execution of one or more shell commands in specified working directories.
*   Automatic error analysis and patch generation using an AI model.
*   Interactive mode for approving or revising suggested fixes.
*   Support for a configurable list of allowed command executables.

### When to Use

*   Use this task to run a build process (e.g., `mvn install`) and automatically fix compilation errors.
*   Use it to run a test suite (e.g., `npm test`) and have the AI attempt to fix failing tests.
*   This is the right choice for any script or command where failures are possible and you want to attempt an automated recovery.

### Execution Configuration (`SelfHealingTaskExecutionConfigData`)

These parameters are specified when the task is added to a plan.

| Parameter | Type | Required | Default | Description |
|:---|:---|:---|:---|:---|
| `commands` | `List<CommandWithWorkingDir>` | Yes | `null` | The commands to be executed with their respective working directories. |
| `task_description` | `String` | No | `null` | A human-readable description of the task's purpose. |
| `task_dependencies` | `List<String>` | No | `[]` | A list of task IDs that must be completed before this one starts. |

**Example (Plan Snippet):**
```json
{
  "task_type": "SelfHealing",
  "task_description": "Build the Java project and fix any errors.",
  "commands": [
    {
      "command": ["mvn", "clean", "install"],
      "workingDir": "backend/java-app"
    }
  ]
}
```

### Type Configuration (`SelfHealingTaskTypeConfig`)

These parameters are set by an administrator in the global `OrchestrationConfig` to define the task's default behavior.

| Parameter | Type | Required | Default | Description |
|:---|:---|:---|:---|:---|
| `model` | `ApiChatModel` | No | `null` | The AI model to use for generating fixes. Defaults to the system's `defaultChatter`. |
| `commandAutoFixCommands` | `MutableList<String>` | No | `[]` | List of command executables that can be used for auto-fixing. This acts as a security whitelist. |

**Example (`OrchestrationConfig` Snippet):**
```json
{
  "taskSettings": {
    "SelfHealing": {
      "task_type": "SelfHealing",
      "model": { "model": "gpt-4-turbo" },
      "commandAutoFixCommands": ["/usr/bin/mvn", "/usr/local/bin/npm", "/usr/bin/git"]
    }
  }
}
```

### Output

If all commands execute successfully (or are successfully patched), the task returns the string `"All Commands completed"`. If a command fails and cannot be fixed, or if the user chooses to ignore the error, it returns an error message like `"Error: <exitCode>"`.
