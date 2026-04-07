# Cognotik DocOps App Developer Guide

## Overview

A **Cognotik DocOps App** is a single-page web application that orchestrates AI-powered document processing pipelines. Apps run inside the Cognotik platform and communicate through REST APIs for file I/O, operation execution, and status monitoring.

This guide accompanies the [Utility Modules Reference](../utils/README.md), which documents the shared JavaScript modules available for building DocOps apps.

### Key Concepts

| Concept           | Description                                                          |
|-------------------|----------------------------------------------------------------------|
| **Session**       | Isolated workspace (filesystem sandbox) for one app run              |
| **DocOp**         | Markdown-defined AI operation that reads inputs and produces outputs |
| **Target**        | Output file/directory a DocOp writes to                              |
| **Status File**   | `docops.status.json` tracks all running/completed tasks              |
| **Proxy Session** | Live monitoring view of AI agent processing                          |

---

## Architecture

```
Browser (Your App)
    ↓ fetch() calls
┌─────────────────┐   ┌──────────────────┐
│ File Index API  │   │ DocOps Servlet   │
│ GET/PUT/DELETE  │   │ POST /docops     │
└────────┬────────┘   └────────┬─────────┘
         ↓                     ↓
    Session Filesystem (Sandbox)
    /input  /ops/*.md  /output  docops.status.json
```

---

## Project Structure

```
apps/your-app-name/
├── app.html          # Entry point
├── app.js            # Application logic
├── style.css         # Styles
├── utils/            # Shared utility modules (see utils/README.md)
│   ├── docops.js     # DocOps execution & polling
│   ├── fileIO.js     # File read/write/delete/list
│   ├── git.js        # Git repository operations
│   ├── models.js     # AI model/provider management
│   ├── session.js    # Session URL parsing
│   ├── sessionLinks.js # Live session monitoring links
│   ├── ui.js         # Markdown, toasts, badges, logging
│   └── usage.js      # Token usage tracking
└── ops/              # DocOp definitions
    └── *.md
```

---

## Quick Start

### 1. Bootstrap Your App

Import utilities using ES module syntax:

```javascript
import { parseSessionUrl, getProxyUrl } from './utils/session.js';
import { loadApiProviders, populateModelDropdowns, loadModelSelections } from './utils/models.js';
import { readFile, writeFile, fileExists } from './utils/fileIO.js';

// Parse session from URL
const { basePath, sessionId, appId } = parseSessionUrl();

// Load available AI models
const availableModels = await loadApiProviders();

// Initialize UI
await loadInitialFiles();
await checkExistingState();
```

### 2. File Operations

Use `fileIO.js` for all file I/O:

```javascript
import { readFile, writeFile, fileExists, listFiles, deleteFile } from './utils/fileIO.js';

// Read/write files
const content = await readFile(basePath, 'input.md');
await writeFile(basePath, 'output.md', content);

// Check existence
if (await fileExists(basePath, 'config.json')) {
    // ...
}

// List directory contents
const files = await listFiles(basePath, 'results/');

// Delete a file
await deleteFile(basePath, 'temp.txt');
```

### 3. Run DocOps

Use `docops.js` for operation execution and monitoring:

```javascript
import { runDocOp, waitForTask, createStatusPoller } from './utils/docops.js';

// Run operation with model overrides
const taskId = await runDocOp(
    sessionId,
    'ops/analyze.md',
    'analysis.md',
    {
        smartModel: 'gpt-4o',
        fastModel: 'gpt-4o-mini'
    }
);

// Wait for completion with status updates
await waitForTask(basePath, 'analysis.md', 600000, (target, task) => {
    console.log(`${target}: ${task.status}`);
});
```

### 4. Status Monitoring

Create a status poller for real-time UI updates:

```javascript
import { createStatusPoller } from './utils/docops.js';
import { setBadge } from './utils/ui.js';
import { updateSessionLinks } from './utils/sessionLinks.js';
import { getProxyUrl } from './utils/session.js';

const poller = createStatusPoller(basePath, (target, taskInfo) => {
    // Update badge based on status
    const state = taskInfo.status === 'COMPLETED' ? 'done' :
                  taskInfo.status === 'RUNNING' ? 'running' : 'error';
    setBadge('step-badge', state);

    // Update session monitoring links
    updateSessionLinks(target, taskInfo, getProxyUrl);
});

poller.start();
// Later: poller.stop();
```

---

## API Reference

### REST Endpoints

#### File Operations

| Method   | Endpoint                       | Description     |
|----------|--------------------------------|-----------------|
| `GET`    | `{basePath}/{filePath}`        | Read file       |
| `PUT`    | `{basePath}/{filePath}`        | Write file      |
| `DELETE` | `{basePath}/{filePath}`        | Delete file     |
| `HEAD`   | `{basePath}/{filePath}`        | Check existence |
| `GET`    | `{basePath}/{dir}/_files.json` | List directory  |

#### DocOps Execution

```
POST /docops?sessionId={sid}&doc={opPath}&target={targetPath}&smartModel={model}&fastModel={model}
```

**⚠️ Model parameters are required!** The servlet doesn't inherit defaults.

#### Git Operations

```
GET/POST {basePath}/.git/api/{endpoint}
```

See `git.js` for available endpoints.

---

## Utility Module Usage

For complete API documentation, see [utils/README.md](../utils/README.md).

### Session Management (`session.js`)

```javascript
import { parseSessionUrl, getProxyUrl, getAppRoot } from './utils/session.js';

const { basePath, sessionId, appId } = parseSessionUrl();
const monitorUrl = getProxyUrl(taskId);
const appRoot = getAppRoot(); // For ZIP/Git endpoints
```

### Model Selection (`models.js`)

```javascript
import {
    loadApiProviders,
    populateModelDropdowns,
    saveModelSelections,
    loadModelSelections
} from './utils/models.js';

// Load and populate
const models = await loadApiProviders();
populateModelDropdowns(
    models,
    [smartSelect, fastSelect],
    loadModelSelections('myapp', ['smartModel', 'fastModel'])
);

// Save on change
saveModelSelections('myapp', {
    smartModel: smartSelect.value,
    fastModel: fastSelect.value
});
```

### Git Integration (`git.js`)

```javascript
import { getStatus, initRepository, commit, formatStatus } from './utils/git.js';

// Check repository status
const status = await getStatus(basePath);
document.getElementById('git-status').innerHTML = formatStatus(status);

// Initialize and commit
await initRepository(basePath);
await commit(basePath, 'Initial commit');
```

### UI Helpers (`ui.js`)

```javascript
import {
    renderMarkdown,
    setStatus,
    setBadge,
    showToast,
    createBatchLogger,
    getFileIcon
} from './utils/ui.js';

// Render markdown content
document.getElementById('output').innerHTML = renderMarkdown(content);

// Status messages (auto-clear after 5s)
setStatus('save-status', 'Saved successfully', 'success');

// Badge states: pending, running, done, error
setBadge('step1-badge', 'running');

// Toast notifications
showToast('Pipeline complete!', 'success', 4000);

// Batch logging
const logger = createBatchLogger('batch-log');
logger.log('Starting...', 'info');
logger.logHtml('<strong>Done!</strong>', 'success');
logger.clear();

// File icons
const icon = getFileIcon('resume.pdf'); // 📕
```

### Session Links (`sessionLinks.js`)

```javascript
import { updateSessionLinks, createSessionLinkManager } from './utils/sessionLinks.js';
import { getProxyUrl } from './utils/session.js';

// Direct update
updateSessionLinks('analysis.md', taskInfo, getProxyUrl);

// Or use a manager for tracking multiple sessions
const linkManager = createSessionLinkManager(getProxyUrl);
linkManager.update('analysis.md', taskInfo);
const sessionId = linkManager.getSessionId('analysis.md');
```

### Usage Tracking (`usage.js`)

```javascript
import {
    fetchUsageData,
    aggregateUsage,
    renderUsageSummary,
    createUsageTableHtml
} from './utils/usage.js';

// Single session
const usage = await fetchUsageData(sessionId);

// Multiple sessions
const { models, totals } = await aggregateUsage([id1, id2, id3]);

// Render summary
renderUsageSummary(totals, {
    prompt: document.getElementById('prompt-tokens'),
    completion: document.getElementById('completion-tokens'),
    total: document.getElementById('total-tokens'),
    cost: document.getElementById('total-cost')
});

// Full table
document.getElementById('usage-table').innerHTML = createUsageTableHtml(models, totals);
```

---

## UI Patterns

### Sequential Pipeline

```javascript
import { runDocOp, waitForTask } from './utils/docops.js';
import { setBadge, showToast } from './utils/ui.js';

async function runPipeline() {
    const steps = [
        { op: 'ops/step1.md', output: 'step1.md', badge: 'badge-step1' },
        { op: 'ops/step2.md', output: 'step2.md', badge: 'badge-step2' }
    ];

    for (const step of steps) {
        setBadge(step.badge, 'running');
        try {
            await runDocOp(sessionId, step.op, step.output, { smartModel, fastModel });
            await waitForTask(basePath, step.output);
            setBadge(step.badge, 'done');
        } catch (e) {
            setBadge(step.badge, 'error');
            throw e;
        }
    }

    showToast('Pipeline complete!', 'success');
}
```

### Parallel Fan-Out

```javascript
async function processMultiple(items) {
    const promises = items.map(async item => {
        const output = `results/${item.name}.md`;
        await runDocOp(sessionId, 'ops/process.md', output, { smartModel, fastModel });
        return waitForTask(basePath, output);
    });

    await Promise.all(promises);
}
```

### Real-Time Monitoring

```javascript
import { createStatusPoller } from './utils/docops.js';
import { createSessionLinkManager } from './utils/sessionLinks.js';
import { setBadge, createBatchLogger } from './utils/ui.js';
import { getProxyUrl } from './utils/session.js';

const logger = createBatchLogger('batch-log');
const linkManager = createSessionLinkManager(getProxyUrl);

const poller = createStatusPoller(basePath, (target, taskInfo) => {
    // Update badge
    const badgeMap = { 'output.md': 'badge-main', 'analysis.md': 'badge-analysis' };
    if (badgeMap[target]) {
        setBadge(badgeMap[target], taskInfo.status === 'COMPLETED' ? 'done' : 'running');
    }

    // Update session links
    linkManager.update(target, taskInfo);

    // Log status changes
    logger.log(`${target}: ${taskInfo.status}`,
               taskInfo.status === 'COMPLETED' ? 'success' : 'info');
});

poller.start();
```

---

## Complete Example

```javascript
import { parseSessionUrl, getProxyUrl } from './utils/session.js';
import { loadApiProviders, populateModelDropdowns, saveModelSelections, loadModelSelections } from './utils/models.js';
import { readFile, writeFile } from './utils/fileIO.js';
import { runDocOp, waitForTask, createStatusPoller } from './utils/docops.js';
import { renderMarkdown, setStatus, setBadge, showToast, createBatchLogger } from './utils/ui.js';
import { updateSessionLinks } from './utils/sessionLinks.js';

(async function() {
    'use strict';

    // Initialize session
    const { basePath, sessionId } = parseSessionUrl();
    let statusPoller = null;

    // Load models and populate UI
    const availableModels = await loadApiProviders();
    const modelSelect = document.getElementById('model-select');
    populateModelDropdowns(
        availableModels,
        [modelSelect],
        loadModelSelections('myapp', ['smartModel'])
    );

    // Load existing input
    const input = await readFile(basePath, 'input.md');
    if (input) {
        document.getElementById('input-editor').value = input;
    }

    // Start status monitoring
    statusPoller = createStatusPoller(basePath, (target, taskInfo) => {
        const badgeMap = { 'output.md': 'badge-main', 'analysis.md': 'badge-analysis' };
        if (badgeMap[target]) {
            setBadge(badgeMap[target], taskInfo.status === 'COMPLETED' ? 'done' : 'running');
        }
        updateSessionLinks(target, taskInfo, getProxyUrl);
    });
    statusPoller.start();

    // Save input handler
    document.getElementById('save-input').addEventListener('click', async () => {
        try {
            await writeFile(basePath, 'input.md', document.getElementById('input-editor').value);
            setStatus('input-status', 'Saved', 'success');
        } catch (e) {
            setStatus('input-status', e.message, 'error');
        }
    });

    // Run pipeline handler
    document.getElementById('run-pipeline').addEventListener('click', async function() {
        const logger = createBatchLogger('batch-log');
        this.disabled = true;

        try {
            const model = modelSelect.value;
            saveModelSelections('myapp', { smartModel: model });

            logger.log('Starting analysis...');
            const taskId = await runDocOp(sessionId, 'ops/analyze.md', 'analysis.md', {
                smartModel: model,
                fastModel: model
            });

            logger.logHtml(`Monitor: <a href="${getProxyUrl(taskId)}" target="_blank">View Live</a>`);

            await waitForTask(basePath, 'analysis.md');
            logger.log('Analysis complete!', 'success');
            showToast('Pipeline completed successfully', 'success');

            const result = await readFile(basePath, 'analysis.md');
            document.getElementById('results').innerHTML = renderMarkdown(result);

        } catch (e) {
            logger.log(`Error: ${e.message}`, 'error');
            showToast('Pipeline failed', 'error');
        } finally {
            this.disabled = false;
        }
    });
})();
```

---

## Best Practices

1. **Always poll for completion** — Never assume DocOps complete immediately
2. **Handle existing state** — Check for files/tasks on page load
3. **Provide monitoring links** — Show proxy URLs for transparency
4. **Save before running** — Auto-save inputs before operations
5. **Validate inputs** — Check JSON/structured data before saving
6. **Design for resumability** — Users may reload mid-pipeline
7. **Use named imports** — Prefer `import { fn }` over namespace imports for tree-shaking
8. **Handle errors gracefully** — Show clear messages and recovery options

---

## Troubleshooting

| Issue                         | Cause                        | Solution                                    |
|-------------------------------|------------------------------|---------------------------------------------|
| "Could not determine session" | Invalid URL pattern          | Check URL matches `/fileIndex/{sessionId}/` |
| DocOps 404                    | Wrong operation path         | Verify `ops/` directory contains the file   |
| Empty output after COMPLETED  | Operation produced no output | Check proxy session log                     |
| Model not found               | Missing API key              | Configure provider API keys first           |

**Debugging tips:**

- Check browser console for errors
- View proxy session logs for AI activity
- Inspect Network tab for API calls
- Manually fetch `docops.status.json` to verify state

---

## Related Documentation

- [Utility Modules Reference](../utils/README.md) — Complete API documentation for all utility modules
- DocOps Operation Authoring Guide — How to write `.md` operation files
- Platform Configuration — API keys and provider setup
