# Developer Guide: Model Management & DocProcessor Servlet

## Overview

This guide covers three key areas for UI developers working with the Cognotik frontend:

1. **How models are listed** — fetching and organizing available AI models
2. **How models are selected** — populating dropdowns and persisting user choices
3. **How to use the DocProcessor servlet** — invoking document processing with specific model overrides

---

## 1. Model Listing

### Data Flow

Models are loaded dynamically from the server at startup. The pipeline is:

```
Server (/apiProviders/?format=json)
  → loadApiProviders()
    → global `availableModels` object
      → UI dropdowns
```

### The `availableModels` Structure

The global `availableModels` object is a dictionary keyed by provider name. Each provider maps to an array of model
descriptors:

```js
// Shape of availableModels after loading:
{
  "OpenAI": [
    { id: "GPT4o",     name: "GPT-4o",      description: "Max tokens: 128000" },
    { id: "GPT4oMini", name: "GPT-4o Mini",  description: "Max tokens: 16000" }
  ],
  "Anthropic": [
    { id: "Claude45Haiku", name: "Claude 4.5 Haiku", description: "Max tokens: 200000" }
  ]
}
```

### How Loading Works

The `loadApiProviders()` function in `welcome.js` fetches from `/apiProviders/?format=json` and transforms the response:

```js
async function loadApiProviders() {
    const response = await fetch('/apiProviders/?format=json');
    const providersResponse = await response.json();

    const providers = providersResponse.configuredProviders || [];

    // Build the availableModels dictionary
    availableModels = {};
    providers.forEach(provider => {
        if (provider.models && provider.models.length > 0) {
            availableModels[provider.name] = provider.models.map(model => ({
                id: model.name,        // This is the model ID string used in API calls
                name: model.name,      // Human-readable display name
                description: model.maxTokens
                    ? `Max tokens: ${model.maxTokens}`
                    : 'No token limit specified'
            }));
        }
    });
}
```

**Important:** The `model.name` from the server response serves as both the display name and the `id`. This `id` is the
string you pass to the DocProcessor servlet and other backend endpoints.

### Authentication Gate

If the `/apiProviders/` endpoint returns HTTP 400+, the user is redirected to login:

```js
if (response.status >= 400) {
    window.location.href = 'login/';
    return;
}
```

### Filtering by Configured API Keys

Not all providers in `availableModels` are shown to the user. Models are only displayed for providers where the user has
configured an API key. This filtering happens at the point of dropdown population (see Section 2).

---

## 2. Model Selection

### Three Layers of Model Dropdowns

The UI has model selection in three different contexts:

| Context              | Dropdown IDs                                                       | Purpose                 |
|----------------------|--------------------------------------------------------------------|-------------------------|
| **Quick Settings**   | `default-smart-model`, `default-fast-model`, `default-image-model` | Global defaults         |
| **Basic Chat Modal** | `basic-chat-model`                                                 | Per-chat-session models |
| **Pipeline Wizard**  | `model-selection`, `fast-model`, `image-model`                     | Per-pipeline models     |

### Populating Dropdowns

All dropdown population follows the same pattern. Here's the canonical version from `populateQuickSettingsModels()`:

```js
function populateQuickSettingsModels() {
    const smartSelect = document.getElementById('default-smart-model');
    const fastSelect = document.getElementById('default-fast-model');
    const imageSelect = document.getElementById('default-image-model');

    // 1. Clear existing options
    [smartSelect, fastSelect, imageSelect].forEach(sel => sel.innerHTML = '');

    const addedModels = new Set();  // Deduplicate across providers

    // 2. Only show models for providers with configured API keys
    if (appState.apiSettings && appState.apiSettings.apiKeys) {
        for (const [provider, key] of Object.entries(appState.apiSettings.apiKeys)) {
            if (key && availableModels[provider]) {
                availableModels[provider].forEach(model => {
                    if (!addedModels.has(model.id)) {
                        [smartSelect, fastSelect, imageSelect].forEach(sel => {
                            const option = document.createElement('option');
                            option.value = model.id;   // ← This is the value sent to the server
                            option.textContent = `${model.name} (${provider})`;
                            sel.appendChild(option);
                        });
                        addedModels.add(model.id);
                    }
                });
            }
        }
    }

    // 3. Fallback if no models available
    if (smartSelect.options.length === 0) {
        // Show placeholder prompting user to configure API keys
    }

    // 4. Restore previously saved selections
    const savedSmart = localStorage.getItem('smartModel');
    if (savedSmart && Array.from(smartSelect.options).some(o => o.value === savedSmart)) {
        smartSelect.value = savedSmart;
    }
}
```

**Key pattern:** The `option.value` is always `model.id` — this is the string the backend understands.

### The `ModelManager` Class

For the pipeline wizard, model population is handled by `ModelManager` (in `modules/models.js`). It follows the same
logic but is encapsulated:

```js
const modelManager = new ModelManager({
    appState: appState,
    document: document,
    getAvailableModels: () => availableModels  // Closure over the global
});

// Called when the pipeline modal opens:
modelManager.populateModelSelections();
```

`ModelManager.populateModelSelections()` targets `#model-selection`, `#fast-model`, and `#image-model` in the
pipeline wizard.

### Persisting Model Choices

Model selections are saved to `localStorage` under these keys:

| localStorage Key        | Purpose                              |
|-------------------------|--------------------------------------|
| `smartModel`     | Smart model for all contexts         |
| `fastModel`      | Fast model                   |
| `imageModel`     | Image generation model               |

When saving quick settings:

```js
document.getElementById('save-quick-settings')?.addEventListener('click', function () {
    const smartModel = document.getElementById('default-smart-model')?.value;
    if (smartModel) localStorage.setItem('smartModel', smartModel);
    // ... same for fastModel, imageModel
});
```

### Task-Level Model Override

Individual task configurations (in the pipeline wizard) can override the default model. The `TaskConfigManager` creates
a per-task model dropdown:

```js
// In TaskConfigManager.createTaskConfigModal():
<select id="task-config-model" class="form-control">
    <option value="">Use Default Model</option>
    <!-- Populated dynamically -->
</select>
```

When the user selects a model here, it's stored in the task config object:

```js
config = {
    task_type: "CodeReview",
    name: "my-review-config",
    model: "Claude45Haiku"   // ← null means "use default"
};
```

---

## 3. Using the DocProcessor Servlet

### What It Does

The `DocProcessorServlet` parses a markdown file containing frontmatter specifications and executes documentation
processing tasks. It can generate, transform, or update files based on the instructions in the markdown.
### ⚠️ Model Parameters Are Required
**You MUST provide model parameters when calling the DocProcessor servlet.** The servlet does not automatically inherit
any globally configured defaults — if you omit the `smartModel`, `fastModel`, and `imageModel` parameters, the server
will fall back to hardcoded defaults that may not match the user's configured API keys or preferred models. This will
result in failed requests or unexpected behavior.
**Every call to `/docops` should include `smartModel` and `fastModel` at minimum.** These values should come from
the user's selection (either from `localStorage` or directly from the model dropdown elements). Never call the
DocProcessor without resolving and attaching model parameters.


### Endpoint

```
GET or POST /docops
```

### Required Parameters

| Parameter    | Type   | Required | Description                                                                  |
|--------------|--------|----------|------------------------------------------------------------------------------|
| `sessionId`  | string | **Yes**  | Session ID (e.g., `U-20250310-i2oc2f`) that identifies the working directory |
| `doc`        | string | **Yes**  | Path to the markdown file, relative to the session root                      |
| `smartModel` | string | **Yes**  | Model ID for the primary/smart model (from user's model selection)           |
| `fastModel`  | string | **Yes**  | Model ID for the fast/secondary model (from user's model selection)          |

### Optional Parameters  

| Parameter    | Type   | Default         | Description                                                                                                          |
|--------------|--------|-----------------|----------------------------------------------------------------------------------------------------------------------|
| `target`     | string | *(all tasks)*   | Specific output file path to process (relative to session root). If omitted, all tasks in the document are executed. |
| `mode`       | string | `PatchExisting` | Update mode. Must match a value from `UpdateModes`.                                                                  |
| `imageModel` | string | *(none)*        | Model ID for image processing (required if pipeline includes image tasks)                                            |

### Model Resolution on the Server

The servlet resolves model ID strings through this process (see `resolveModel()` in the Kotlin source):

```kotlin
private fun resolveModel(modelId: String?): ChatModel? {
  if (modelId.isNullOrBlank()) return null
  // 1. Look up in registered ChatModel values
  ChatModel.values().values.find { it.modelId == modelId }?.let { return it }
  // 2. Fall back: create an unregistered model reference
  return object : ChatModel(modelId = modelId, provider = null) { ... }
}
```

**This means:** The `model.id` values from `availableModels` (which come from the server's registered models) will
resolve correctly. If you pass an unrecognized string, the server creates a generic wrapper and logs a warning.

### Example: Calling from JavaScript

```js
// Basic call — uses default models
function runDocProcessor(sessionId, docPath) {
    const params = new URLSearchParams({
        sessionId: sessionId,
        doc: docPath
    });
    return fetch(`/docops?${params.toString()}`);
}

// Full call — with model overrides from user selections
function runDocProcessorWithModels(sessionId, docPath, targetPath) {
    const params = new URLSearchParams({
        sessionId: sessionId,
        doc: docPath
    });

    // Add optional target
    if (targetPath) {
        params.set('target', targetPath);
    }

    // Add model overrides from localStorage (or dropdown values)
    const smartModel = localStorage.getItem('smartModel');
    const fastModel = localStorage.getItem('fastModel');
    const imageModel = localStorage.getItem('imageModel');

    if (smartModel) params.set('smartModel', smartModel);
    if (fastModel) params.set('fastModel', fastModel);
    if (imageModel) params.set('imageModel', imageModel);

    // Optionally set the update mode
    params.set('mode', 'PatchExisting');

    return fetch(`/docops?${params.toString()}`);
}
```

### Example: Using Dropdown Values Directly

```js
// Wire up a "Run DocOps" button that reads from the quick settings dropdowns
document.getElementById('run-docops-btn').addEventListener('click', async () => {
    const sessionId = appState.sessionId;
    const docPath = document.getElementById('doc-path-input').value;

    const smartModel = document.getElementById('default-smart-model')?.value;
    const fastModel = document.getElementById('default-fast-model')?.value;
    const imageModel = document.getElementById('default-image-model')?.value;

    const params = new URLSearchParams({ sessionId, doc: docPath });
    if (smartModel) params.set('smartModel', smartModel);
    if (fastModel) params.set('fastModel', fastModel);
    if (imageModel) params.set('imageModel', imageModel);

    try {
        const response = await fetch(`/docops?${params}`);
        const result = await response.json();

        if (result.success) {
            console.log(`Processed ${result.tasksExecuted} tasks`);
            console.log('Files:', result.processedFiles);
            if (result.content) {
                console.log('Output content:', result.content);
            }
        } else {
            console.error('DocOps error:', result.error);
        }
    } catch (err) {
        console.error('Request failed:', err);
    }
});
```

### Response Format

#### Success Response (HTTP 200)

```json
{
  "success": true,
  "tasksExecuted": 3,
  "sessions": 2,
  "processedFiles": [
    "output/report.md",
    "output/summary.md"
  ],
  "content": "# Generated Report\n..."
}
```

- `tasksExecuted` — Number of tasks that were run
- `sessions` — Number of processing sessions created
- `processedFiles` — List of files that were processed
- `content` — *(Only present when `target` was specified and the file exists after processing)* The content of the
  target file

#### Error Responses

| HTTP Status | Condition                        | Example Response                                                           |
|-------------|----------------------------------|----------------------------------------------------------------------------|
| 400         | Missing `sessionId` or `doc`     | `{"error": "Missing required parameter: sessionId"}`                       |
| 400         | No valid frontmatter in document | `{"error": "No valid frontmatter found in document: ops/foo.md..."}`       |
| 400         | No matching tasks found          | `{"error": "No tasks found for target 'out.md' in document 'ops/foo.md'"}` |
| 403         | Path traversal attempt           | `{"error": "Access denied: document path is outside session directory"}`   |
| 404         | Session directory not found      | `{"error": "Session directory not found: U-20250310-xxxx"}`                |
| 404         | Document file not found          | `{"error": "Document file not found: ops/foo.md"}`                         |
| 500         | Processing exception             | `{"error": "Processing failed: <message>"}`                                |

### How the Existing UI Launches DocOps

In the app card setup (`setupAppCards()`), DocOps-type apps are launched by navigating directly to a file index page:

```js
if (app.type === 'docops') {
    element.addEventListener('click', function (e) {
        e.preventDefault();
        const docopsSessionId = Utils.generateSessionId();
        window.location.href = `${app.path}/fileIndex/${docopsSessionId}/app.html`;
    });
}
```

This navigates to the DocOps application's own UI, which internally calls the servlet. If you want to call the servlet
directly (e.g., for programmatic processing), use the fetch-based approach shown above.

### Security Notes

- The servlet validates that the `doc` path resolves to a location **within** the session directory (canonical path
  check). Path traversal attempts (e.g., `doc=../../etc/passwd`) return HTTP 403.
- The `sessionId` is used to look up the user's session directory via `dataStorage.getSessionDir()`. The user is
  authenticated via the `AUTH_COOKIE`.

---

## Quick Reference: Model ID Flow

```
Server registers models
        ↓
/apiProviders/?format=json returns model list
        ↓
loadApiProviders() → availableModels[provider] = [{id, name, description}]
        ↓
populateQuickSettingsModels()     → <option value="model.id">
populateBasicChatModelSelections() → <option value="model.id">
modelManager.populateModelSelections() → <option value="model.id">
        ↓
User selects → localStorage.setItem('smartModel', model.id)
        ↓
DocProcessor call: /docops?smartModel=<model.id>&fastModel=<model.id>
        ↓
Server: resolveModel(model.id) → ChatModel instance
```

The `model.id` string is the single consistent identifier used across the entire stack — from server registration,
through the API response, into dropdown `option.value`, into `localStorage`, and back to the server as a query
parameter.