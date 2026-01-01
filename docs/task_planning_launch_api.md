### Overview

A **Task Planning Session** in Cognotik is an orchestrated interaction where an AI Agent breaks down a high-level user
request into smaller, executable steps. The configuration determines the "brain" (Cognitive Mode), the "tools" (Task
Types), and the "environment" (Models/Limits) the AI uses to solve the problem.

---

### 1. Accessing the Configuration

Depending on your interface, the entry point differs slightly:

* **Web UI:** You navigate through a 4-step wizard starting at the "Welcome" screen (`welcome.html`).
* **IntelliJ Plugin:** You open the **Plan Config Dialog** (`PlanConfigDialog.kt`), usually accessible via the plugin's
  action menu when starting a new task.

---

### 2. Global Orchestration Settings

Before defining specific tasks, you must configure the environment in which the AI operates.

#### A. Model Selection

You must define the AI models used for different aspects of the session:

* **Smart Model (Default):** The "Brain." Used for high-level reasoning, planning, and complex code generation (e.g.,
  GPT-4o).
* **Parsing Model (Fast):** A cheaper, faster model used for structuring data, parsing outputs, and simple logic (e.g.,
  GPT-3.5 Turbo or GPT-4o Mini).
* **Image Model:** A multimodal model used specifically for tasks involving image analysis or generation.

#### B. Execution Limits & Safety

To prevent infinite loops or excessive API costs, you configure the following limits (found in `PlanConfigDialog.kt` and
`OrchestrationConfig.kt`):

* **Max Iterations:** The maximum number of planning rounds the AI can perform (Default: 10).
* **Max Tasks Per Iteration:** How many parallel tasks the AI can schedule in a single round (Default: 1).
* **Max Task History Chars:** The size of the context window retained from previous steps (Default: 10,000 chars).
* **Budget ($):** A hard limit on API spending for the session.
* **Temperature:** Controls creativity (0.0 = Deterministic/Code, 1.0 = Creative/Writing).
* **Auto-Fix:** If checked, the system will automatically attempt to fix errors without asking for user confirmation.

---

### 3. Cognitive Mode Configuration

The **Cognitive Mode** determines the strategy the AI uses to approach the problem. You select this from a dropdown (
e.g., "Auto Plan", "Waterfall", "Chat").

* **Configuration:** Clicking "Configure" next to the mode selection opens the `CognitiveConfigDialog`.
* **Customization:** Depending on the mode, you can tweak internal prompts, descriptions, or specific boolean flags (
  e.g., enabling specific reasoning steps) via reflection-based forms.

---

### 4. Task Configuration (The "Tools")

This is the most critical part of the setup. You define which capabilities are available to the AI.

#### A. Adding and Enabling Tasks

You select from a list of available **Task Types** (e.g., `FileModificationTask`, `CrawlerAgentTask`, `PdfFormTask`).

* **Single Selection:** Adds a specific tool to the AI's toolkit.
* **Multiple Selection:** You can add multiple instances of the same task type with different configurations (e.g.,
  two "Crawler" tasks configured for different domains).

#### B. Configuring Specific Tasks

Double-clicking a task in the list opens the **Task Config Dialog**. The settings available depend on the specific
Kotlin class of the task:

1. **General Settings:**
    * **Name:** A unique identifier for this configuration (e.g., "Documentation_Crawler").
    * **Model Override:** You can force a specific task to use a different model than the global default (e.g., use a
      specialized coding model just for the `FileModificationTask`).

2. **Task-Specific Fields:**
   The UI dynamically generates fields based on the underlying code. Examples include:
    * **Crawler Task:** `max_pages`, `allowed_domains`, `concurrent_processing`.
    * **Persuasive Essay Task:** `thesis`, `target_word_count`, `revision_passes`.
    * **MCP Tool Task:** `timeout`, `max_retries`.

#### C. Sub-Planning (Recursive Planning)

The `SubPlanTask` is a special task type that allows the AI to spawn a child session.

* **Purpose:** You define a specific purpose for this sub-planner.
* **Cognitive Mode:** The sub-plan can use a different strategy (e.g., "Waterfall") than the parent session.
* **Sub-Task List:** You must explicitly add which tools (Tasks) are available to the sub-planner. This allows for
  granular control (e.g., the main planner can browse the web, but the sub-planner can only write files).

---

### 5. Managing Configurations (Save/Load)

To avoid re-configuring complex setups every time:

* **Save:** In the IntelliJ dialog, you can name and save your current configuration (stored in `AppSettingsState`).
* **Load:** Select a previously saved configuration from the dropdown to instantly restore all settings, models, and
  task lists.
* **Export/Import:** You can copy the entire configuration as a JSON string to your clipboard to share with other users
  or move between machines.

---

### 6. Launching the Session

Once configured:

1. **Review:** Ensure the correct models are selected and the budget is set.
2. **Launch:**
    * **Web UI:** Click "Launch AI Session" in Step 4.
    * **IntelliJ:** Click "OK" on the Plan Config Dialog.
3. **Execution:** The system initializes the `OrchestrationConfig` object, instantiates the `PatchProcessor`, and begins
   the planning loop based on your user prompt.

### Summary Checklist for a Robust Session

1. **Model:** Ensure you have a "Smart" model (GPT-4 class) selected for the default.
2. **Context:** Increase `Max Task History Chars` if you are dealing with large codebases.
3. **Tools:** Only enable the tasks strictly necessary for the job to reduce AI confusion.
4. **Safety:** Set a reasonable `Budget` and `Max Iterations` to prevent runaway processes.

---

### 7. HTTP API Reference

To implement a custom client (CLI, Web, or IDE Plugin) that launches tasks via HTTP, you need to interact with the
configuration servlets to discover capabilities and then submit an `OrchestrationConfig` JSON payload to the launch
endpoint.

#### A. Discovery Endpoints (Metadata)

Before constructing a plan, clients should fetch available configurations to render forms or validate input.
**1. Task Types Metadata**

* **Endpoint:** `/taskConfig` (GET)
* **Response:** JSON Array of Task Definitions.
* **Structure:**
  ```json
  [
    {
      "id": "CrawlerAgent",
      "name": "Crawler Agent Task",
      "description": "Crawls websites...",
      "category": "Online",
      "configFields": [
        {
          "id": "max_pages_per_task",
          "label": "Max Pages Per Task",
          "type": "number",
          "default": 10
        },
        {
          "id": "allowed_domains",
          "label": "Allowed Domains",
          "type": "text",
          "tooltip": "Comma separated list..."
        }
      ]
    }
  ]
  ```
* **Field Types:** `text`, `textarea`, `number`, `checkbox`, `select` (includes `options` array).
  **2. Cognitive Modes Metadata**
* **Endpoint:** `/cognitiveConfig` (GET)
* **Response:** JSON Array of Cognitive Mode Definitions.
* **Structure:** Similar to Task Types, but defines strategies (e.g., "Waterfall", "Auto Plan").

#### B. Launch Payload (`OrchestrationConfig`)

To start a session, send a POST request (endpoint depends on server implementation, typically `/api/session/launch`)
with the `OrchestrationConfig` JSON.
**Root Object Structure:**

| Field               | Type   | Description                                        |
|:--------------------|:-------|:---------------------------------------------------|
| `sessionId`         | String | Unique identifier for the session.                 |
| `defaultSmartModel` | Object | The main reasoning model (see Model Object below). |
| `defaultFastModel`  | Object | The cheaper parsing model.                         |
| `taskSettings`      | Map    | **Crucial.** A map of `Key -> Configuration`.      |
| `cognitiveSettings` | Object | The strategy configuration.                        |
| `budget`            | Number | Max cost in USD.                                   |
| `temperature`       | Number | 0.0 to 1.0.                                        |
| `maxIterations`     | Number | Loop limit.                                        |

**1. Model Object (`ApiChatModel`)**

```json
{
  "provider": "OpenAI",
  "modelName": "gpt-4o"
}
```

**2. Task Settings Map (Polymorphic)**
The `taskSettings` map keys are unique identifiers (strings). The values are polymorphic objects. You **must** include
the `task_type` field (discriminator) matching the `id` from the metadata endpoint.

```json
"taskSettings": {
"MyCrawler_1": {
"task_type": "CrawlerAgent", <-- REQUIRED DISCRIMINATOR
"name": "MyCrawler_1",
"max_pages_per_task": 50,
"allowed_domains": "example.com"
},
"CodeRunner": {
"task_type": "RunCode",
"name": "CodeRunner"
}
}
```

**3. Cognitive Settings (Polymorphic)**
Similar to tasks, this requires a `type` discriminator.

```json
"cognitiveSettings": {
"type": "Waterfall", <-- REQUIRED DISCRIMINATOR
"feedback_rounds": 2
}
```

#### C. Complete Example Payload

```json
{
  "sessionId": "custom-client-session-123",
  "budget": 5.0,
  "temperature": 0.2,
  "maxIterations": 15,
  "autoFix": true,
  "defaultSmartModel": {
    "provider": "OpenAI",
    "modelName": "gpt-4"
  },
  "defaultFastModel": {
    "provider": "OpenAI",
    "modelName": "gpt-3.5-turbo"
  },
  "cognitiveSettings": {
    "type": "Waterfall"
  },
  "taskSettings": {
    "WebSearch": {
      "task_type": "CrawlerAgent",
      "name": "WebSearch",
      "max_pages_per_task": 5
    },
    "FileEdit": {
      "task_type": "FileModification",
      "name": "FileEdit"
    }
  }
}
```