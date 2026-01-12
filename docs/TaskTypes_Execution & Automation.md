# Execution & Automation

## AutoFix

Run a command and automatically fix any issues that arise

Executes a command and automatically fixes any issues that arise.
<ul>
  <li>Specify commands and working directories</li>
  <li>Supports multiple commands and directories</li>
  <li>Interactive approval mode</li>
  <li>Output diff formatting</li>
</ul>

#### Default Execution Configuration

```json
{
  "task_type" : "AutoFix",
  "commands" : [ ],
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "AutoFix"
}
```

#### Default Type Configuration

```json
{
  "name" : "AutoFix",
  "model" : null,
  "promptTemplate" : "SelfHealing - Run a command and automatically fix any issues that arise\n* Specify the commands to be executed along with their working directories\n* Each command's working directory should be specified relative to the root directory\n* Provide the commands and their arguments in the 'commands' field\n* Each command should be a list of strings\n* Available commands:\n{executables}",
  "task_type" : "AutoFix"
}
```

---

## RunCode

Execute code snippets with oversight

Executes code snippets in an interactive environment.
<ul>
  <li>User-approved code execution</li>
  <li>Working directory configuration</li>
  <li>Output capture and formatting</li>
  <li>Error handling and reporting</li>
  <li>Interactive result review</li>
</ul>

#### Planner Prompt Segment

```text
RunCode - Use a code interpreter to solve and complete the user's request.
  * Do not directly write code (yet)
  * Include detailed technical requirements for the needed solution
```

#### Default Execution Configuration

```json
{
  "task_type" : "RunCode",
  "goal" : null,
  "workingDir" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "RunCode"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "RunCode",
  "codeRuntime" : null,
  "model" : null,
  "name" : "RunCode"
}
```

---

## RunTool

Execute external tools

Executes configured external tools.

#### Planner Prompt Segment

```text
RunTool - Execute a tool with custom arguments
  * Available tools: 

```

#### Default Execution Configuration

```json
{
  "task_type" : "RunTool",
  "tool" : null,
  "args" : null,
  "workingDir" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "RunTool",
  "executable" : null
}
```

#### Default Type Configuration

```json
{
  "task_type" : "RunTool",
  "model" : null,
  "name" : "RunTool"
}
```

---

## SubPlan

Create and execute sub-plans using recursive planning

Enables recursive planning and execution with configurable cognitive modes.
<ul>
  <li>Create sub-plans with different cognitive strategies</li>
  <li>Support for multiple recursion levels</li>
  <li>Context propagation to sub-plans</li>
  <li>Configurable recursion depth limits</li>
  <li>Automatic result aggregation and summarization</li>
  <li>Flexible cognitive mode selection per sub-plan</li>
  <li>Useful for complex multi-stage problems</li>
</ul>

#### Planner Prompt Segment

```text
SubPlanningTask - Create and execute sub-plans using recursive planning with configurable cognitive modes.
** Specify a planning goal or objective
** Optionally provide context information
** Can override the cognitive mode for the sub-plan
** Supports multiple levels of recursion up to configured depth
** Results are aggregated and optionally summarized
```

#### Default Execution Configuration

```json
{
  "task_type" : "SubPlan",
  "planning_goal" : null,
  "context" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "SubPlan"
}
```

#### Default Type Configuration

```json
{
  "cognitiveSettings" : null,
  "taskSettings" : { },
  "purpose" : "",
  "summaryPrompt" : "Create a comprehensive summary of the sub-planning results below.\n\nOriginal Goal: {goal}\n\nThe summary should:\n- Highlight key findings and accomplishments\n- Identify any issues or blockers encountered\n- Provide actionable next steps if applicable\n- Be concise but complete\n\nUse markdown formatting with headers and bullet points.",
  "model" : null,
  "name" : "SubPlan",
  "task_type" : "SubPlan",
  "cognitiveMode" : null
}
```

---

## SymbolsDbCodeTask

Execute code snippets with predefined symbols

Executes code snippets in an interactive environment with access to a symbol graph.
<ul>
  <li>Access to `symbols_db` (SymbolGraphService)</li>
  <li>Query code symbols and relationships</li>
  <li>User-approved code execution</li>
  <li>Interactive result review</li>
</ul>

#### Planner Prompt Segment

```text
RunCode - Use a code interpreter to solve and complete the user's request.
  * Do not directly write code (yet)
  * Include detailed technical requirements for the needed solution
You have access to a `symbols_db` object (SymbolGraphService) to assist with code execution.
```

#### Default Execution Configuration

```json
{
  "task_type" : "SymbolsDbCodeTask",
  "goal" : null,
  "workingDir" : null,
  "task_description" : null,
  "task_dependencies" : null,
  "state" : null,
  "task_type" : "SymbolsDbCodeTask"
}
```

#### Default Type Configuration

```json
{
  "codeRuntime" : "GroovyRuntime",
  "symbolFile" : "symbol_graph.json",
  "task_type" : "SymbolsDbCodeTask",
  "model" : null,
  "name" : "SymbolsDbCodeTask"
}
```

---

