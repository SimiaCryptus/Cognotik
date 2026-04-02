# Cognotik DocOps App Developer Guide

## Overview

A **Cognotik DocOps App** is a single-page web application that orchestrates AI-powered document processing pipelines.
Apps run inside the Cognotik platform and communicate through REST APIs for file I/O, operation execution, and status
monitoring.

### Key Concepts

| Concept           | Description                                                          |
|-------------------|----------------------------------------------------------------------|
| **Session**       | Isolated workspace (filesystem sandbox) for one app run              |
| **DocOp**         | Markdown-defined AI operation that reads inputs and produces outputs |
| **Target**        | Output file/directory a DocOp writes to                              |
| **Status File**   | `docops.status.json` tracks all running/completed tasks              |
| **Proxy Session** | Live monitoring view of AI agent processing                          |

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

## Project Structure

```
apps/your-app-name/
├── app.html          # Entry point
├── app.js            # Application logic
├── style.css         # Styles
├── utils/            # Utility modules
│   ├── docops.js     # DocOps execution
│   ├── fileIO.js     # File operations
│   ├── git.js        # Git integration
│   ├── models.js     # Model management
│   ├── session.js    # Session/URL parsing
│   ├── ui.js         # UI helpers
│   └── usage.js      # Usage tracking
└── ops/              # DocOp definitions
    └── *.md
```

## Quick Start

### 1. Bootstrap Your App

```javascript
// Parse session from URL
const { basePath, sessionId, appId } = SessionUtils.parseSessionUrl();

// Load available models
const availableModels = await ModelUtils.loadApiProviders();

// Initialize UI
await loadInitialFiles();
await checkExistingState();
```

### 2. File Operations

All file operations use the utilities in `fileIO.js`:

```javascript
// Read/write files
const content = await FileIOUtils.readFile(basePath, 'input.md');
await FileIOUtils.writeFile(basePath, 'output.md', content);

// Check existence
if (await FileIOUtils.fileExists(basePath, 'config.json')) {
    // ...
}

// List directory
const files = await FileIOUtils.listFiles(basePath, 'results/');
```

### 3. Run DocOps

Use `docops.js` utilities for operation execution:

```javascript
// Run operation with model overrides
const taskId = await DocOpsUtils.runDocOp(
    sessionId,
    'ops/analyze.md',
    'analysis.md',
    {
        smartModel: 'GPT4o',
        fastModel: 'GPT4oMini'
    }
);

// Wait for completion
await DocOpsUtils.waitForTask(basePath, 'analysis.md');
```

### 4. Status Monitoring

Create a status poller for real-time updates:

```javascript
const poller = DocOpsUtils.createStatusPoller(basePath, (target, taskInfo) => {
    // Update UI based on task status
    UIUtils.setBadge(badgeId, taskInfo.status === 'COMPLETED' ? 'done' : 'running');
    SessionLinkUtils.updateSessionLinks(target, taskInfo, SessionUtils.getProxyUrl);
});

poller.start();
```

## API Reference

### File Operations

| Method   | Endpoint                       | Description     |
|----------|--------------------------------|-----------------|
| `GET`    | `{basePath}/{filePath}`        | Read file       |
| `PUT`    | `{basePath}/{filePath}`        | Write file      |
| `DELETE` | `{basePath}/{filePath}`        | Delete file     |
| `HEAD`   | `{basePath}/{filePath}`        | Check existence |
| `GET`    | `{basePath}/{dir}/_files.json` | List directory  |

### DocOps Execution

```
POST /docops?sessionId={sid}&doc={opPath}&target={targetPath}&smartModel={model}&fastModel={model}
```

**⚠️ Model parameters are required!** The servlet doesn't inherit defaults.

### Git Operations

Use the Git REST API via `git.js`:

```javascript
// Check status
const status = await GitUtils.getStatus(basePath);

// Initialize repo
await GitUtils.initRepository(basePath);

// Commit changes
await GitUtils.commit(basePath, 'Updated configuration');

// View formatted status
const statusHtml = GitUtils.formatStatus(status);
```

### Usage Tracking

Track token usage and costs:

```javascript
// Get usage for current session
const usage = await UsageUtils.fetchUsageData(sessionId);

// Aggregate multiple sessions
const aggregated = await UsageUtils.aggregateUsage([sessionId1, sessionId2]);

// Display usage table
const tableHtml = UsageUtils.createUsageTableHtml(aggregated.models, aggregated.totals);
```

## UI Patterns

### Model Selection

```javascript
// Populate dropdowns with available models
ModelUtils.populateModelDropdowns(
    availableModels,
    [smartSelect, fastSelect, imageSelect],
    savedSelections
);

// Save selections
ModelUtils.saveModelSelections('myapp', {
    smartModel: smartSelect.value,
    fastModel: fastSelect.value
});
```

### Pipeline Execution

#### Sequential Pipeline

```javascript
async function runPipeline() {
    const steps = [
        {
            op: 'ops/step1.md',
            output: 'step1.md',
            badge: 'badge-step1',
            label: 'Step 1'
        },
        {
            op: 'ops/step2.md',
            output: 'step2.md',
            badge: 'badge-step2',
            label: 'Step 2'
        }
    ];

    for (const step of steps) {
        UIUtils.setBadge(step.badge, 'running');
        try {
            await DocOpsUtils.runDocOp(sessionId, step.op, step.output);
            await DocOpsUtils.waitForTask(basePath, step.output);
            UIUtils.setBadge(step.badge, 'done');
        } catch (e) {
            UIUtils.setBadge(step.badge, 'error');
            throw e;
        }
    }
}
```

#### Parallel Fan-Out

```javascript
async function processMultiple(items) {
    const promises = items.map(async item => {
        const output = `results/${item.name}.md`;
        await DocOpsUtils.runDocOp(sessionId, 'ops/process.md', output);
        return DocOpsUtils.waitForTask(basePath, output);
    });

    await Promise.all(promises);
}
```

### Status Display

```javascript
// Create batch logger
const logger = UIUtils.createBatchLogger('batch-log');

// Log with monitoring links
logger.logHtml(`Processing: <a href="${SessionUtils.getProxyUrl(taskId)}">Monitor</a>`);

// Show toast notifications
UIUtils.showToast('Pipeline complete!', 'success');

// Update status messages
UIUtils.setStatus('save-status', 'Saved successfully', 'success');
```

## Complete Example

```javascript
(function() {
    'use strict';

    // Initialize
    const { basePath, sessionId } = SessionUtils.parseSessionUrl();
    let availableModels = {};
    let statusPoller = null;

    async function init() {
        // Load models
        availableModels = await ModelUtils.loadApiProviders();

        // Populate dropdowns
        ModelUtils.populateModelDropdowns(
            availableModels,
            [document.getElementById('model-select')],
            ModelUtils.loadModelSelections('myapp', ['smartModel'])
        );

        // Load existing files
        const input = await FileIOUtils.readFile(basePath, 'input.md');
        if (input) {
            document.getElementById('input-editor').value = input;
        }

        // Start monitoring
        statusPoller = DocOpsUtils.createStatusPoller(basePath, updateTaskUI);
        statusPoller.start();
    }

    function updateTaskUI(target, taskInfo) {
        // Update badges
        const badgeMap = {
            'output.md': 'badge-main',
            'analysis.md': 'badge-analysis'
        };

        const badgeId = badgeMap[target];
        if (badgeId) {
            const state = taskInfo.status === 'COMPLETED' ? 'done' :
                         taskInfo.status === 'RUNNING' ? 'running' : 'error';
            UIUtils.setBadge(badgeId, state);
        }

        // Update session links
        SessionLinkUtils.updateSessionLinks(target, taskInfo, SessionUtils.getProxyUrl);
    }

    // Save input
    document.getElementById('save-input').addEventListener('click', async function() {
        try {
            await FileIOUtils.writeFile(basePath, 'input.md',
                document.getElementById('input-editor').value);
            UIUtils.setStatus('input-status', 'Saved', 'success');
        } catch (e) {
            UIUtils.setStatus('input-status', e.message, 'error');
        }
    });

    // Run pipeline
    document.getElementById('run-pipeline').addEventListener('click', async function() {
        const logger = UIUtils.createBatchLogger('batch-log');
        this.disabled = true;

        try {
            // Get selected model
            const model = document.getElementById('model-select').value;

            // Run operation
            logger.log('Starting analysis...');
            const taskId = await DocOpsUtils.runDocOp(
                sessionId,
                'ops/analyze.md',
                'analysis.md',
                { smartModel: model, fastModel: model }
            );

            // Show monitoring link
            logger.logHtml(`Monitor: <a href="${SessionUtils.getProxyUrl(taskId)}" target="_blank">View Live</a>`);

            // Wait for completion
            await DocOpsUtils.waitForTask(basePath, 'analysis.md');

            logger.log('Analysis complete!', 'success');
            UIUtils.showToast('Pipeline completed successfully', 'success');

            // Display results
            const result = await FileIOUtils.readFile(basePath, 'analysis.md');
            document.getElementById('results').innerHTML = UIUtils.renderMarkdown(result);

        } catch (e) {
            logger.log(`Error: ${e.message}`, 'error');
            UIUtils.showToast('Pipeline failed', 'error');
        } finally {
            this.disabled = false;
        }
    });

    // Initialize on load
    init();
})();
```

## Best Practices

1. **Always poll for completion** - Never assume DocOps complete immediately
2. **Handle existing state** - Check for files/tasks on page load
3. **Provide monitoring links** - Show proxy URLs for transparency
4. **Save before running** - Auto-save inputs before operations
5. **Validate inputs** - Check JSON/structured data before saving
6. **Design for resumability** - Users may reload mid-pipeline
7. **Use data attributes** - Keep HTML declarative with `data-*`
8. **Handle errors gracefully** - Show clear messages and recovery options

## Troubleshooting

| Issue                         | Cause                        | Solution                                    |
|-------------------------------|------------------------------|---------------------------------------------|
| "Could not determine session" | Invalid URL pattern          | Check URL matches `/fileIndex/{sessionId}/` |
| DocOps 404                    | Wrong operation path         | Verify `ops/` directory contains the file   |
| Empty output after COMPLETED  | Operation produced no output | Check proxy session log                     |
| Model not found               | Missing API key              | Configure provider API keys first           |

For debugging:

- Check browser console for errors
- View proxy session logs for AI activity
- Inspect Network tab for API calls
- Manually fetch `docops.status.json` to verify state
