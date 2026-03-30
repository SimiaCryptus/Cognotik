# Cognotik DocOps App Developer Guide

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Project Structure](#project-structure)
4. [URL Conventions & Session Management](#url-conventions--session-management)
5. [File I/O API](#file-io-api)
6. [DocOps Operations](#docops-operations)
7. [Task Status & Polling](#task-status--polling)
8. [Building the UI](#building-the-ui)
9. [Pipeline Execution Patterns](#pipeline-execution-patterns)
10. [Live Session Monitoring](#live-session-monitoring)
11. [Complete App Walkthrough](#complete-app-walkthrough)
12. [API Reference](#api-reference)
13. [Best Practices](#best-practices)
14. [Troubleshooting](#troubleshooting)

---

## Overview

A **Cognotik DocOps App** is a single-page web application that orchestrates AI-powered document processing pipelines.
Each app provides a user-facing interface for:

- **Collecting input** (text, structured data, requirements)
- **Executing AI operations** (brainstorming, analysis, research, code generation)
- **Displaying results** (rendered Markdown, live previews, file browsers)

Apps run inside the Cognotik platform and communicate with the server through a simple REST-based file I/O and operation
execution API. The platform handles AI model interaction, web research, code execution, and all backend processing —
your app just needs to manage the UI and orchestrate the pipeline.

### Key Concepts

| Concept           | Description                                                                                              |
|-------------------|----------------------------------------------------------------------------------------------------------|
| **Session**       | An isolated workspace (filesystem sandbox) for one run of your app                                       |
| **DocOp**         | A Markdown-defined AI operation (prompt + instructions) that reads input files and produces output files |
| **Target**        | The output file or directory a DocOp writes its results to                                               |
| **Status File**   | A JSON file (`docops.status.json`) that tracks the state of all running/completed tasks                  |
| **Proxy Session** | A live monitoring view into an AI agent's processing session                                             |

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Browser (Your App)                │
│  ┌───────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │  app.html  │  │  app.js  │  │    style.css     │ │
│  └───────────┘  └──────────┘  └──────────────────┘ │
└──────────┬──────────────────────────┬───────────────┘
           │ fetch() calls            │
           ▼                          ▼
┌─────────────────────┐   ┌─────────────────────────┐
│   File Index API    │   │     DocOps Servlet       │
│  GET/PUT/HEAD files │   │  POST /docops?...        │
│  GET _files.json    │   │  (triggers AI ops)       │
└─────────┬───────────┘   └───────────┬─────────────┘
          │                           │
          ▼                           ▼
┌─────────────────────────────────────────────────────┐
│              Session Filesystem (Sandbox)            │
│  /input files    /ops/*.md    /output files          │
│  docops.status.json                                  │
└─────────────────────────────────────────────────────┘
```

### Request Flow

1. User interacts with the app UI
2. App saves input files via `PUT` requests
3. App triggers DocOps via `POST /docops`
4. App polls `docops.status.json` for task progress
5. App reads output files via `GET` requests and renders results

---

## Project Structure

Every DocOps app lives in a directory under `src/main/resources/apps/` and consists of at minimum three files:

```
apps/
└── your-app-name/
    ├── app.html          # Main HTML page (entry point)
    ├── app.js            # Application logic
    ├── style.css         # Styles (optional but recommended)
    ├── marked.min.js     # Markdown renderer (include if rendering .md output)
    └── ops/              # DocOp definitions (Markdown files)
        ├── step_one_op.md
        ├── step_two_op.md
        └── ...
```

### Runtime Session Filesystem

When a user launches your app, the platform creates a session with a copy of your app's files. The session filesystem
looks like:

```
{sessionId}/
├── app.html              # Your app (served to browser)
├── app.js
├── style.css
├── ops/                  # Your operation definitions
│   ├── step_one_op.md
│   └── step_two_op.md
├── docops.status.json    # Auto-managed task status (created by platform)
├── input.md              # User-created input files
├── output/               # Operation output files/directories
│   ├── result.md
│   └── ...
└── ...
```

---

## URL Conventions & Session Management

### URL Pattern

Apps are served at:

```
/{app-context}/{app-name}/fileIndex/{sessionId}/app.html
```

For example:

```
/health-improvement/fileIndex/abc-123-def/app.html
```

### Parsing the URL

Every app must parse the URL to extract the `sessionId` and `basePath`. This is the standard bootstrap code:

```javascript
(function() {
    'use strict';

    // Parse URL to extract session info
    const pathParts = window.location.pathname.split('/');
    const fileIndexIdx = pathParts.indexOf('fileIndex');
    let basePath = '';
    let sessionId = '';
    let appId = '';

    if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
        sessionId = pathParts[fileIndexIdx + 1];
        basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
        appId = pathParts[fileIndexIdx - 1] || 'my-app';
    } else {
        console.warn('Could not determine session from URL path.');
        basePath = window.location.pathname.replace(/\/[^/]*$/, '');
    }

    // All file operations use basePath as the root
    // e.g., basePath + '/input.md'
    // e.g., basePath + '/ops/my_op.md'

    // ... rest of your app
})();
```

### Important Variables

| Variable    | Purpose                                         | Example Value                           |
|-------------|-------------------------------------------------|-----------------------------------------|
| `basePath`  | Root URL for all file operations                | `/health-improvement/fileIndex/abc-123` |
| `sessionId` | Unique session identifier, passed to DocOps API | `abc-123-def`                           |
| `appId`     | Your app's name (from URL)                      | `health-improvement`                    |

---

## File I/O API

All file operations are relative to the session's `basePath`.

### Read a File

```javascript
async function readFile(filePath) {
    const url = basePath + '/' + filePath;
    const resp = await fetch(url);
    if (!resp.ok) {
        if (resp.status === 404) return null;  // File doesn't exist
        throw new Error(`Failed to read ${filePath}: ${resp.status}`);
    }
    return await resp.text();
}
```

**Usage:**

```javascript
const content = await readFile('symptoms.md');       // Read a text/markdown file
const json = await readFile('notes.json');           // Read JSON (as text)
const result = await readFile('output/report.md');   // Read from subdirectory
```

### Write a File

```javascript
async function writeFile(filePath, content) {
    const url = basePath + '/' + filePath;
    const resp = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': 'text/plain; charset=utf-8' },
        body: content
    });
    if (!resp.ok) {
        throw new Error(`Failed to write ${filePath}: ${resp.status}`);
    }
    return true;
}
```

**Usage:**

```javascript
await writeFile('requirements.md', 'I want a small, friendly dog...');
await writeFile('notes.json', JSON.stringify(data, null, 2));
```

> **Note:** The platform automatically creates intermediate directories. You can write to `round_2/answers.md` without
> explicitly creating the `round_2/` directory.

### Check if a File Exists

```javascript
async function fileExists(filePath) {
    const resp = await fetch(basePath + '/' + filePath, { method: 'HEAD' });
    return resp.ok;
}
```

### List Files in a Directory

```javascript
async function listFiles(dirPath) {
    const url = basePath + '/' + dirPath + '/_files.json';
    const resp = await fetch(url);
    if (!resp.ok) {
        if (resp.status === 404) return [];
        throw new Error(`Failed to list ${dirPath}: ${resp.status}`);
    }
    const data = await resp.json();
    return (data.entries || []).filter(e => e.type === 'file');
}
```

The `_files.json` endpoint returns:

```json
{
  "entries": [
    {
      "name": "golden_retriever.md",
      "type": "file"
    },
    {
      "name": "labrador.md",
      "type": "file"
    },
    {
      "name": "subdir",
      "type": "directory"
    }
  ]
}
```

---

## DocOps Operations

### What is a DocOp?

A DocOp is an AI operation defined as a Markdown file in your `ops/` directory. It contains instructions for the AI
agent — what to read, how to process it, and where to write the output. The platform handles all AI model interaction.

### Triggering a DocOp

```javascript
async function runDocOp(opPath, targetPath) {
    const url = `/docops?sessionId=${encodeURIComponent(sessionId)}` +
                `&doc=${encodeURIComponent(opPath)}` +
                `&target=${encodeURIComponent(targetPath)}`;
    const resp = await fetch(url, { method: 'POST' });
    if (!resp.ok) {
        const errText = await resp.text().catch(() => '');
        throw new Error(`DocOps failed: ${resp.status} ${resp.statusText}\n${errText}`);
    }
    return await resp.text(); // Returns the task session ID
}
```

**Parameters:**

| Parameter   | Description                                                      | Example                           |
|-------------|------------------------------------------------------------------|-----------------------------------|
| `sessionId` | Your app's session ID (from URL)                                 | `abc-123-def`                     |
| `doc`       | Path to the operation definition file (relative to session root) | `ops/brainstorm_op.md`            |
| `target`    | Output file or directory the operation writes to                 | `output/brainstorm.md` or `code/` |

**Usage:**

```javascript
// Single file output
await runDocOp('ops/brainstorm_op.md', 'round_1/brainstorm.md');

// Directory output (note trailing slash)
await runDocOp('ops/render_op.md', 'code/');
```

### Important: DocOps are Asynchronous

The `POST /docops` call returns **immediately** (or shortly after) with a task session ID. The actual AI processing
happens in the background. You **must poll** for completion.

```javascript
// ❌ WRONG — operation is not complete when POST returns
await runDocOp('ops/my_op.md', 'output.md');
const result = await readFile('output.md'); // May not exist yet!

// ✅ CORRECT — poll for completion
const taskId = await runDocOp('ops/my_op.md', 'output.md');
await waitForTask('output.md');
const result = await readFile('output.md'); // Now safe to read
```

---

## Task Status & Polling

### The Status File

The platform maintains `docops.status.json` in your session root. It tracks all tasks:

```json
{
  "tasks": {
    "round_1/brainstorm.md": {
      "status": "COMPLETED",
      "sessionId": "task-session-xyz",
      "completedAt": "2024-01-15T10:30:00Z"
    },
    "round_1/perspectives.md": {
      "status": "RUNNING",
      "sessionId": "task-session-abc"
    },
    "plan/doctor.md": {
      "status": "ERROR",
      "sessionId": "task-session-err"
    }
  }
}
```

### Task Statuses

| Status      | Meaning                                               |
|-------------|-------------------------------------------------------|
| `RUNNING`   | AI agent is actively processing                       |
| `COMPLETED` | Operation finished successfully; output file is ready |
| `ERROR`     | Operation failed                                      |
| `FAILED`    | Operation failed (alternate status)                   |
| `PENDING`   | Operation queued but not yet started                  |

### Reading the Status File

```javascript
async function fetchDocopsStatus() {
    try {
        const resp = await fetch(basePath + '/docops.status.json');
        if (!resp.ok) return null;
        return await resp.json();
    } catch (e) {
        return null;
    }
}
```

### Waiting for a Task to Complete

This is the standard pattern for waiting on a single task:

```javascript
async function waitForTask(targetPath, maxWaitMs) {
    const maxWait = maxWaitMs || 600000; // 10 minutes default
    const pollInterval = 2000;           // 2 seconds
    const startTime = Date.now();

    while (Date.now() - startTime < maxWait) {
        const statusData = await fetchDocopsStatus();
        if (statusData && statusData.tasks && statusData.tasks[targetPath]) {
            const task = statusData.tasks[targetPath];
            if (task.status === 'COMPLETED') return task;
            if (task.status === 'ERROR' || task.status === 'FAILED') {
                throw new Error(`Task ${targetPath} failed`);
            }
        }
        await new Promise(resolve => setTimeout(resolve, pollInterval));
    }
    throw new Error(`Task ${targetPath} timed out`);
}
```

### Background Status Polling

For a richer UI, poll the status file on a timer and update all UI elements:

```javascript
let statusPollTimer = null;
const STATUS_POLL_INTERVAL = 3000;

function startStatusPolling() {
    if (statusPollTimer) return;
    statusPollTimer = setInterval(async () => {
        const statusData = await fetchDocopsStatus();
        if (!statusData || !statusData.tasks) return;

        for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
            updateBadgeForTask(target, taskInfo);
            updateSessionLink(target, taskInfo);
        }
    }, STATUS_POLL_INTERVAL);
    // Also poll immediately
    fetchDocopsStatus().then(/* ... */);
}

function stopStatusPolling() {
    if (statusPollTimer) {
        clearInterval(statusPollTimer);
        statusPollTimer = null;
    }
}
```

### Flexible Task Lookup

Sometimes the status file keys don't exactly match your target path. Use a flexible lookup:

```javascript
function getTaskStatus(statusData, targetPath) {
    if (!statusData || !statusData.tasks) return null;

    // Exact match
    if (statusData.tasks[targetPath]) return statusData.tasks[targetPath];

    // Match by filename only
    const filename = targetPath.split('/').pop();
    if (statusData.tasks[filename]) return statusData.tasks[filename];

    // Search all tasks by target field
    for (const [key, task] of Object.entries(statusData.tasks)) {
        if (task.target === targetPath || task.target === filename) return task;
    }
    return null;
}
```

---

## Building the UI

### HTML Structure

DocOps apps follow a consistent layout pattern with tabbed navigation:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My DocOps App</title>
    <link rel="stylesheet" href="../style.css">
</head>
<body>
<!-- Header -->
<header class="app-header">
    <h1>🚀 My App Name</h1>
    <p class="subtitle">Brief description of what this app does</p>
</header>

<!-- Navigation Tabs -->
<nav class="tab-nav">
    <a href="#" class="nav-link active" data-section="section-input">📝 Input</a>
    <a href="#" class="nav-link" data-section="section-pipeline">⚙️ Pipeline</a>
    <a href="#" class="nav-link" data-section="section-results">📊 Results</a>
</nav>

<!-- Sections -->
<main>
    <div id="section-input" class="section active">
        <!-- Input editors -->
    </div>
    <div id="section-pipeline" class="section">
        <!-- Pipeline steps -->
    </div>
    <div id="section-results" class="section">
        <!-- Results display -->
    </div>
</main>

<!-- Loading overlay -->
<div id="loading-overlay" class="hidden">
    <div id="loading-text">Processing...</div>
</div>

<!-- Batch execution log -->
<div id="batch-log" class="batch-log"></div>

<!-- Scripts -->
<script src="marked.min.js"></script>
<script src="../app.js"></script>
</body>
</html>
```

### Tab Navigation

```javascript
document.querySelectorAll('.nav-link').forEach(link => {
    link.addEventListener('click', function(e) {
        e.preventDefault();
        const sectionId = this.dataset.section;
        // Deactivate all
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
        // Activate selected
        this.classList.add('active');
        document.getElementById(sectionId).classList.add('active');
    });
});
```

### Input Section Pattern

Provide a textarea for user input with save functionality:

```html

<div class="card">
    <h3>Your Input</h3>
    <p>Describe what you want...</p>
    <textarea id="input-editor" rows="12" placeholder="Enter your text here..."></textarea>
    <div class="button-row">
        <button id="save-input" class="btn btn-primary">💾 Save</button>
        <span id="input-status" class="status-msg"></span>
    </div>
</div>
```

```javascript
document.getElementById('save-input').addEventListener('click', async function() {
    const content = document.getElementById('input-editor').value;
    try {
        this.disabled = true;
        await writeFile('input.md', content);
        setStatus('input-status', '✓ Saved successfully', 'success');
    } catch (e) {
        setStatus('input-status', '✗ ' + e.message, 'error');
    } finally {
        this.disabled = false;
    }
});
```

### Pipeline Step Pattern

Each pipeline step has a consistent structure:

```html

<div class="step">
    <div class="step-header">
        <span class="step-number">1</span>
        <span class="step-title">Brainstorm Ideas</span>
        <span id="badge-brainstorm" class="step-badge pending">pending</span>
    </div>
    <p class="step-description">
        Cast a wide net of possible ideas based on your input.
    </p>
    <div class="button-row">
        <button class="btn btn-primary btn-run"
                data-op="ops/brainstorm_op.md"
                data-output="ideas.md"
                data-badge="badge-brainstorm"
                data-viewer="viewer-brainstorm">
            ▶ Run
        </button>
        <button class="btn btn-secondary btn-view"
                data-file="ideas.md"
                data-viewer="viewer-brainstorm">
            👁 View
        </button>
    </div>
    <div id="viewer-brainstorm" class="viewer"></div>
</div>
```

### Status Badges

```javascript
function setBadge(badgeId, state) {
    const el = document.getElementById(badgeId);
    if (!el) return;
    el.className = 'step-badge ' + state;
    const labels = {
        'pending': 'pending',
        'running': 'running…',
        'done': 'done',
        'error': 'error',
        'action': 'action needed'
    };
    el.textContent = labels[state] || state;
}
```

### Status Messages

```javascript
function setStatus(elemId, message, type) {
    const el = document.getElementById(elemId);
    if (!el) return;
    el.textContent = message;
    el.className = 'status-msg' + (type ? ' ' + type : '');
    // Auto-clear success/error messages after 5 seconds
    if (type === 'success' || type === 'error') {
        setTimeout(() => {
            el.textContent = '';
            el.className = 'status-msg';
        }, 5000);
    }
}
```

### Markdown Rendering

Include `marked.min.js` and use this helper:

```javascript
function renderMarkdown(md) {
    if (typeof marked !== 'undefined') {
        if (typeof marked.parse === 'function') return marked.parse(md);
        return marked(md);
    }
    // Fallback: plain text
    return '<pre>' + escapeHtml(md) + '</pre>';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
```

### File Viewer Pattern

Toggle-able viewers that load and render file content:

```javascript
async function viewFile(filePath, viewerId) {
    const viewer = document.getElementById(viewerId);
    if (!viewer) return;

    // Toggle: if already visible, hide it
    if (viewer.classList.contains('visible')) {
        viewer.classList.remove('visible');
        return;
    }

    try {
        const content = await readFile(filePath);
        if (content === null) {
            viewer.innerHTML = '<p class="placeholder">File not found. Run the operation first.</p>';
        } else {
            viewer.innerHTML = renderMarkdown(content);
        }
        viewer.classList.add('visible');
    } catch (e) {
        viewer.innerHTML = '<p class="placeholder" style="color: var(--color-danger);">' +
            'Error: ' + escapeHtml(e.message) + '</p>';
        viewer.classList.add('visible');
    }
}

// Attach to all view buttons
document.querySelectorAll('.btn-view').forEach(btn => {
    btn.addEventListener('click', function() {
        viewFile(this.dataset.file, this.dataset.viewer);
    });
});
```

### Results Tabs

For multi-tab results display:

```html

<div class="results-tabs">
    <button class="results-tab active" data-tab="tab-report">📄 Report</button>
    <button class="results-tab" data-tab="tab-details">📁 Details</button>
</div>
<div id="tab-report" class="tab-panel active">
    <!-- Report content -->
</div>
<div id="tab-details" class="tab-panel">
    <!-- Details content -->
</div>
```

```javascript
document.querySelectorAll('.results-tab').forEach(tab => {
    tab.addEventListener('click', function() {
        document.querySelectorAll('.results-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
        this.classList.add('active');
        document.getElementById(this.dataset.tab).classList.add('active');
    });
});
```

---

## Pipeline Execution Patterns

### Single Operation Run

The standard pattern for running one operation via a button:

```javascript
document.querySelectorAll('.btn-run').forEach(btn => {
    btn.addEventListener('click', async function() {
        const opPath = this.dataset.op;
        const badgeId = this.dataset.badge;
        const outputPath = this.dataset.output;
        const viewerId = this.dataset.viewer;

        setBadge(badgeId, 'running');
        this.disabled = true;
        startStatusPolling();

        try {
            // Fire the operation (returns immediately with task ID)
            const taskId = await runDocOp(opPath, outputPath);

            // Show monitoring link if we got a valid session ID
            const cleanTaskId = taskId ? taskId.trim() : '';
            if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                updateSessionLinks(outputPath, {
                    status: 'RUNNING',
                    sessionId: cleanTaskId
                });
            }

            // Wait for actual completion
            await waitForTask(outputPath);
            setBadge(badgeId, 'done');

            // Auto-show result
            if (viewerId) {
                const viewer = document.getElementById(viewerId);
                if (viewer) {
                    const content = await readFile(outputPath);
                    if (content) {
                        viewer.innerHTML = renderMarkdown(content);
                        viewer.classList.add('visible');
                    }
                }
            }
        } catch (e) {
            setBadge(badgeId, 'error');
            alert('Operation failed: ' + e.message);
        } finally {
            this.disabled = false;
        }
    });
});
```

### Sequential Batch Execution

Run multiple operations in order, stopping on failure:

```javascript
async function runSequential(steps) {
    for (const step of steps) {
        logBatch(`Starting: ${step.label}`, 'info');
        setBadge(step.badge, 'running');

        try {
            const taskId = await runDocOp(step.op, step.output);

            // Log monitoring

             const cleanTaskId = taskId ? taskId.trim() : '';
             if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                 const proxyUrl = getProxyUrl(cleanTaskId);
                 logBatchHtml(`Session: <a href="${proxyUrl}" target="_blank" class="monitor-link">📡 Monitor (${cleanTaskId})</a>`, 'info');
                 updateSessionLinks(step.output, { status: 'RUNNING', sessionId: cleanTaskId });
             }
             // Wait for completion
             await waitForTask(step.output);
             setBadge(step.badge, 'done');
             logBatch(`✓ Completed: ${step.label}`, 'success');
             // Auto-show result in viewer
             if (step.viewer) {
                 try {
                     const content = await readFile(step.output);
                     if (content) {
                         const viewer = document.getElementById(step.viewer);
                         if (viewer) {
                             viewer.innerHTML = renderMarkdown(content);
                             viewer.classList.add('visible');
                         }
                     }
                 } catch (e) { /* non-critical */ }
             }
             // Run optional post-step callback
             if (step.afterFn) await step.afterFn();
         } catch (e) {
             setBadge(step.badge, 'error');
             logBatch(`✗ Failed: ${step.label} — ${e.message}`, 'error');
             throw e; // Stop the sequence
         }
     }
}
```

**Usage:**

```javascript
document.getElementById('run-all').addEventListener('click', async function() {
     this.disabled = true;
     startStatusPolling();
     batchLog.innerHTML = '';
     try {
         await runSequential([
             {
                 op: 'ops/brainstorm_op.md',
                 output: 'round_1/brainstorm.md',
                 badge: 'badge-brainstorm',
                 viewer: 'viewer-brainstorm',
                 label: 'Initial Brainstorm'
             },
             {
                 op: 'ops/analysis_op.md',
                 output: 'round_1/analysis.md',
                 badge: 'badge-analysis',
                 viewer: 'viewer-analysis',
                 label: 'Deep Analysis'
             },
             {
                 op: 'ops/report_op.md',
                 output: 'plan/report.md',
                 badge: 'badge-report',
                 viewer: 'viewer-report',
                 label: 'Final Report',
                 afterFn: () => refreshResultsList()  // Optional post-step hook
             },
         ]);
         logBatch('🎉 Pipeline complete!', 'success');
     } catch (e) {
         logBatch('Pipeline stopped due to error.', 'error');
     } finally {
         this.disabled = false;
     }
});
```

### Parallel Fan-Out Execution

When you need to run the same operation for multiple inputs (e.g., researching each breed separately):

```javascript
async function runFanOut(opPath, items, getOutputPath, label) {
     let successCount = 0;
     let errorCount = 0;
     for (const item of items) {
         const outputPath = getOutputPath(item);
         logBatch(`  Processing: ${item.name}`, 'info');
         try {
             const taskId = await runDocOp(opPath, outputPath);
             const cleanTaskId = taskId ? taskId.trim() : '';
             if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                 logBatchHtml(`  Session: <a href="${getProxyUrl(cleanTaskId)}" target="_blank" class="monitor-link">📡 Monitor (${cleanTaskId})</a>`, 'info');
             }
             await waitForTask(outputPath);
             logBatch(`  ✓ ${item.name}`, 'success');
             successCount++;
         } catch (e) {
             logBatch(`  ✗ ${item.name}: ${e.message}`, 'error');
             errorCount++;
             // Continue with remaining items (don't throw)
         }
     }
     logBatch(`${label}: ${successCount} succeeded, ${errorCount} failed`, errorCount > 0 ? 'warn' : 'success');
     return { successCount, errorCount };
}
```

**Usage (from the puppy-finder app):**

```javascript
const breedFiles = await listFiles('breeds');
await runFanOut(
     'ops/breeder_research_op.md',
     breedFiles,
     (file) => 'breeder_research/' + file.name.replace(/\.md$/, '') + '.md',
     'Breeder Research'
);
```

### Mixed Pipeline (Sequential + Fan-Out)

Real apps often combine sequential steps with fan-out steps:

```javascript
document.getElementById('run-all').addEventListener('click', async function() {
     this.disabled = true;
     startStatusPolling();
     batchLog.innerHTML = '';
     try {
         // Sequential steps 1-2
         await runSequential([
             { op: 'ops/brainstorm_op.md', output: 'ideas.md', badge: 'badge-brainstorm', label: 'Brainstorm' },
             { op: 'ops/expand_op.md', output: 'expand_status.md', badge: 'badge-expand', label: 'Expand Details' },
         ]);
         // Fan-out step 3: process each generated file
         const files = await listFiles('items');
         setBadge('badge-research', 'running');
         for (const file of files) {
             const outputPath = 'research/' + file.name;
             try {
                 await runDocOp('ops/research_op.md', outputPath);
                 await waitForTask(outputPath);
                 logBatch(`✓ Researched: ${file.name}`, 'success');
             } catch (e) {
                 logBatch(`✗ Failed: ${file.name}: ${e.message}`, 'error');
             }
         }
         setBadge('badge-research', 'done');
         // Sequential step 4: final summary
         await runSequential([
             { op: 'ops/summary_op.md', output: 'final_summary.md', badge: 'badge-summary', label: 'Final Summary' },
         ]);
         logBatch('🎉 Entire pipeline complete!', 'success');
     } catch (e) {
         logBatch('Pipeline stopped due to error.', 'error');
     } finally {
         this.disabled = false;
     }
});
```

---

## Live Session Monitoring

Every running DocOp task has an associated **proxy session** — a live view into the AI agent's processing. You can link
users to this for transparency.

### Proxy URL Format

```javascript
const proxyBase = '/proxy/';
function getProxyUrl(taskSessionId) {
     return proxyBase + '#' + taskSessionId;
}
```

This opens a real-time view of the AI agent's work — what it's reading, thinking, and writing.

### Displaying Session Links

Show clickable monitoring links next to running tasks:

```javascript
function updateSessionLinks(target, taskInfo) {
     const status = taskInfo.status;
     const taskSessionId = taskInfo.sessionId;
     // Find or create a container for the link
     const safeTarget = target.replace(/[^a-zA-Z0-9]/g, '-');
     const linkContainerId = 'session-link-' + safeTarget;
     let container = document.getElementById(linkContainerId);
     if (!container) {
         container = document.createElement('div');
         container.id = linkContainerId;
         container.className = 'session-link-container';
         // Insert near the relevant step's viewer
         const viewer = document.getElementById('viewer-' + safeTarget);
         if (viewer && viewer.parentElement) {
             viewer.parentElement.insertBefore(container, viewer);
         }
     }
     if (!container) return;
     if (status === 'RUNNING' && taskSessionId) {
         const proxyUrl = getProxyUrl(taskSessionId);
         container.innerHTML = `
             <div class="session-monitor-link">
                 <span class="monitor-pulse">●</span>
                 <span>Processing… </span>
                 <a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
                     📡 Monitor Live Session (${escapeHtml(taskSessionId)})
                 </a>
             </div>`;
         container.style.display = 'block';
     } else if (status === 'COMPLETED' && taskSessionId) {
         const proxyUrl = getProxyUrl(taskSessionId);
         container.innerHTML = `
             <div class="session-completed-link">
                 <span>✅ Completed — </span>
                 <a href="${escapeHtml(proxyUrl)}" target="_blank" rel="noopener" class="monitor-link">
                     📋 View Session Log (${escapeHtml(taskSessionId)})
                 </a>
             </div>`;
         container.style.display = 'block';
     } else if (status === 'ERROR' || status === 'FAILED') {
         const proxyUrl = taskSessionId ? getProxyUrl(taskSessionId) : '#';
         container.innerHTML = `
             <div class="session-error-link">
                 <span>❌ Failed — </span>
                 ${taskSessionId
                     ? `<a href="${escapeHtml(proxyUrl)}" target="_blank" class="monitor-link">
                            🔍 View Error Log (${escapeHtml(taskSessionId)})
                        </a>`
                     : '<span>No session available</span>'}
             </div>`;
         container.style.display = 'block';
     } else {
         container.style.display = 'none';
     }
}
```

### Batch Log with Session Links

Log monitoring links in the batch execution log:

```javascript
const batchLog = document.getElementById('batch-log');
function logBatch(message, type) {
     batchLog.classList.add('visible');
     const entry = document.createElement('div');
     entry.className = 'log-entry log-' + (type || 'info');
     const ts = new Date().toLocaleTimeString();
     entry.textContent = `[${ts}] ${message}`;
     batchLog.appendChild(entry);
     batchLog.scrollTop = batchLog.scrollHeight;
}
function logBatchHtml(html, type) {
     batchLog.classList.add('visible');
     const entry = document.createElement('div');
     entry.className = 'log-entry log-' + (type || 'info');
     const ts = new Date().toLocaleTimeString();
     entry.innerHTML = `[${ts}] ${html}`;
     batchLog.appendChild(entry);
     batchLog.scrollTop = batchLog.scrollHeight;
}
```

**Usage in pipeline execution:**

```javascript
const taskId = await runDocOp(step.op, step.output);
const cleanTaskId = taskId ? taskId.trim() : '';
if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
     const proxyUrl = getProxyUrl(cleanTaskId);
     logBatchHtml(
         `Session started: <a href="${proxyUrl}" target="_blank" class="monitor-link">` +
         `📡 Monitor Live Session (${cleanTaskId})</a>`,
         'info'
     );
}
```

---

## Complete App Walkthrough

This section walks through building a complete DocOps app from scratch. We'll build a simplified version of the
puppy-finder app.

### Step 1: Create the Directory Structure

```
apps/
└── my-app/
     ├── app.html
     ├── app.js
     ├── style.css
     ├── marked.min.js      # Copy from another app
     └── ops/
         ├── brainstorm_op.md
         └── report_op.md
```

### Step 2: Write the HTML Shell

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My DocOps App</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
<div id="app">
    <header>
        <h1>🚀 My App</h1>
        <p class="subtitle">AI-powered analysis pipeline</p>
    </header>
    <nav id="pipeline-nav">
        <ul>
            <li><a class="nav-link active" data-section="section-input" href="#">📝 Input</a></li>
            <li><a class="nav-link" data-section="section-pipeline" href="#">⚙️ Pipeline</a></li>
            <li><a class="nav-link" data-section="section-results" href="#">📊 Results</a></li>
        </ul>
    </nav>
    <!-- Input Section -->
    <section class="section active" id="section-input">
        <h2>📝 Input</h2>
        <div class="card">
            <h3>Describe Your Request</h3>
            <textarea id="input-editor" rows="15" placeholder="Enter your request..."></textarea>
            <div class="button-row">
                <button class="btn btn-primary" id="save-input">💾 Save</button>
                <span class="status-msg" id="input-status"></span>
            </div>
        </div>
    </section>
    <!-- Pipeline Section -->
    <section class="section" id="section-pipeline">
        <h2>⚙️ Pipeline</h2>
        <!-- Pipeline Diagram -->
        <div class="card">
            <h3>Pipeline Overview</h3>
            <div id="pipeline-diagram">
                <div class="pipeline-stage" data-stage="input">
                    <div class="stage-icon">📋</div>
                    <div class="stage-label">Input</div>
                    <div class="stage-status">Ready</div>
                </div>
                <div class="pipeline-arrow">→</div>
                <div class="pipeline-stage" data-stage="brainstorm">
                    <div class="stage-icon">🧠</div>
                    <div class="stage-label">Brainstorm</div>
                    <div class="stage-status">Pending</div>
                </div>
                <div class="pipeline-arrow">→</div>
                <div class="pipeline-stage" data-stage="report">
                    <div class="stage-icon">📄</div>
                    <div class="stage-label">Report</div>
                    <div class="stage-status">Pending</div>
                </div>
            </div>
        </div>
        <!-- Step 1 -->
        <div class="card">
            <div class="step">
                <div class="step-header">
                    <span class="step-number">1</span>
                    <span class="step-title">Brainstorm</span>
                    <span class="step-badge" id="badge-brainstorm">pending</span>
                </div>
                <p class="step-desc">Generate initial ideas based on your input.</p>
                <div class="button-row">
                    <button class="btn btn-run" data-badge="badge-brainstorm"
                            data-op="ops/brainstorm_op.md"
                            data-output="ideas.md" data-viewer="viewer-brainstorm">▶ Run
                    </button>
                    <button class="btn btn-view" data-file="ideas.md"
                            data-viewer="viewer-brainstorm">👁 View
                    </button>
                </div>
                <div class="viewer" id="viewer-brainstorm"></div>
            </div>
        </div>
        <!-- Step 2 -->
        <div class="card">
            <div class="step">
                <div class="step-header">
                    <span class="step-number">2</span>
                    <span class="step-title">Final Report</span>
                    <span class="step-badge" id="badge-report">pending</span>
                </div>
                <p class="step-desc">Generate a comprehensive report.</p>
                <div class="button-row">
                    <button class="btn btn-run" data-badge="badge-report"
                            data-op="ops/report_op.md"
                            data-output="report.md" data-viewer="viewer-report">▶ Run
                    </button>
                    <button class="btn btn-view" data-file="report.md"
                            data-viewer="viewer-report">👁 View
                    </button>
                </div>
                <div class="viewer" id="viewer-report"></div>
            </div>
        </div>
        <!-- Batch Execution -->
        <div class="card">
            <h3>🚀 Batch Execution</h3>
            <div class="button-row">
                <button class="btn btn-accent" id="run-all">▶ Run Entire Pipeline</button>
            </div>
            <div class="log-output" id="batch-log"></div>
        </div>
    </section>
    <!-- Results Section -->
    <section class="section" id="section-results">
        <h2>📊 Results</h2>
        <div class="results-tabs">
            <button class="results-tab active" data-tab="tab-report">📄 Report</button>
            <button class="results-tab" data-tab="tab-ideas">🧠 Ideas</button>
        </div>
        <div class="results-content">
            <div class="tab-panel active" id="tab-report">
                <div class="card">
                    <div class="button-row">
                        <button class="btn btn-secondary" data-file="report.md"
                                data-viewer="result-report">🔄 Refresh
                        </button>
                    </div>
                    <div class="result-viewer" id="result-report">
                        <p class="placeholder">Run the pipeline to generate the report.</p>
                    </div>
                </div>
            </div>
            <div class="tab-panel" id="tab-ideas">
                <div class="card">
                    <div class="button-row">
                        <button class="btn btn-secondary" data-file="ideas.md"
                                data-viewer="result-ideas">🔄 Refresh
                        </button>
                    </div>
                    <div class="result-viewer" id="result-ideas">
                        <p class="placeholder">Run the pipeline to generate ideas.</p>
                    </div>
                </div>
            </div>
        </div>
    </section>
</div>
<script src="marked.min.js"></script>
<script src="app.js"></script>
</body>
</html>
```

### Step 3: Write the JavaScript

Here's a complete, minimal `app.js` incorporating all the patterns:

```javascript
(function() {
     'use strict';
     // === URL Parsing & Session Setup ===
     const pathParts = window.location.pathname.split('/');
     const fileIndexIdx = pathParts.indexOf('fileIndex');
     let basePath = '';
     let sessionId = '';
     if (fileIndexIdx >= 0 && fileIndexIdx + 1 < pathParts.length) {
         sessionId = pathParts[fileIndexIdx + 1];
         basePath = pathParts.slice(0, fileIndexIdx + 2).join('/');
     } else {
         console.warn('Could not determine session from URL path.');
         basePath = window.location.pathname.replace(/\/[^/]*$/, '');
     }
     const proxyBase = '/proxy/';
     function getProxyUrl(id) { return proxyBase + '#' + id; }
     // === File I/O ===
     async function readFile(filePath) {
         const resp = await fetch(basePath + '/' + filePath);
         if (!resp.ok) {
             if (resp.status === 404) return null;
             throw new Error(`Failed to read ${filePath}: ${resp.status}`);
         }
         return await resp.text();
     }
     async function writeFile(filePath, content) {
         const resp = await fetch(basePath + '/' + filePath, {
             method: 'PUT',
             headers: { 'Content-Type': 'text/plain; charset=utf-8' },
             body: content
         });
         if (!resp.ok) throw new Error(`Failed to write ${filePath}: ${resp.status}`);
         return true;
     }
     async function runDocOp(opPath, targetPath) {
         const url = `/docops?sessionId=${encodeURIComponent(sessionId)}` +
                     `&doc=${encodeURIComponent(opPath)}` +
                     `&target=${encodeURIComponent(targetPath)}`;
         const resp = await fetch(url, { method: 'POST' });
         if (!resp.ok) {
             const errText = await resp.text().catch(() => '');
             throw new Error(`DocOps failed: ${resp.status}\n${errText}`);
         }
         return await resp.text();
     }
     // === Status Polling ===
     async function fetchDocopsStatus() {
         try {
             const resp = await fetch(basePath + '/docops.status.json');
             if (!resp.ok) return null;
             return await resp.json();
         } catch (e) { return null; }
     }
     async function waitForTask(targetPath, maxWaitMs) {
         const maxWait = maxWaitMs || 600000;
         const pollInterval = 2000;
         const startTime = Date.now();
         while (Date.now() - startTime < maxWait) {
             const statusData = await fetchDocopsStatus();
             if (statusData?.tasks?.[targetPath]) {
                 const task = statusData.tasks[targetPath];
                 if (task.status === 'COMPLETED') return task;
                 if (task.status === 'ERROR' || task.status === 'FAILED') {
                     throw new Error(`Task ${targetPath} failed`);
                 }
             }
             await new Promise(r => setTimeout(r, pollInterval));
         }
         throw new Error(`Task ${targetPath} timed out`);
     }
     let statusPollTimer = null;
     function startStatusPolling() {
         if (statusPollTimer) return;
         statusPollTimer = setInterval(() => pollStatus(), 3000);
         pollStatus();
     }
     async function pollStatus() {
         const statusData = await fetchDocopsStatus();
         if (!statusData?.tasks) return;
         for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
             // Update badges based on target
             const badgeMap = { 'ideas.md': 'badge-brainstorm', 'report.md': 'badge-report' };
             const badgeId = badgeMap[target];
             if (badgeId) {
                 if (taskInfo.status === 'RUNNING') setBadge(badgeId, 'running');
                 else if (taskInfo.status === 'COMPLETED') setBadge(badgeId, 'done');
                 else if (taskInfo.status === 'ERROR') setBadge(badgeId, 'error');
             }
         }
     }
     // === UI Helpers ===
     function renderMarkdown(md) {
         if (typeof marked !== 'undefined') {
             return typeof marked.parse === 'function' ? marked.parse(md) : marked(md);
         }
         return '<pre>' + escapeHtml(md) + '</pre>';
     }
     function escapeHtml(text) {
         const div = document.createElement('div');
         div.textContent = text;
         return div.innerHTML;
     }
     function setStatus(elemId, message, type) {
         const el = document.getElementById(elemId);
         if (!el) return;
         el.textContent = message;
         el.className = 'status-msg' + (type ? ' ' + type : '');
         if (type === 'success' || type === 'error') {
             setTimeout(() => { el.textContent = ''; el.className = 'status-msg'; }, 5000);
         }
     }
     function setBadge(badgeId, state) {
         const el = document.getElementById(badgeId);
         if (!el) return;
         el.className = 'step-badge ' + state;
         const labels = { pending: 'pending', running: 'running…', done: 'done', error: 'error' };
         el.textContent = labels[state] || state;
     }
     // === Batch Log ===
     const batchLog = document.getElementById('batch-log');
     function logBatch(message, type) {
         batchLog.classList.add('visible');
         const entry = document.createElement('div');
         entry.className = 'log-entry log-' + (type || 'info');
         entry.textContent = `[${new Date().toLocaleTimeString()}] ${message}`;
         batchLog.appendChild(entry);
         batchLog.scrollTop = batchLog.scrollHeight;
     }
     function logBatchHtml(html, type) {
         batchLog.classList.add('visible');
         const entry = document.createElement('div');
         entry.className = 'log-entry log-' + (type || 'info');
         entry.innerHTML = `[${new Date().toLocaleTimeString()}] ${html}`;
         batchLog.appendChild(entry);
         batchLog.scrollTop = batchLog.scrollHeight;
     }
     // === Navigation ===
     document.querySelectorAll('.nav-link').forEach(link => {
         link.addEventListener('click', function(e) {
             e.preventDefault();
             document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
             document.querySelectorAll('.section').forEach(s => s.classList.remove('active'));
             this.classList.add('active');
             document.getElementById(this.dataset.section).classList.add('active');
         });
     });
     // === Results Tabs ===
     document.querySelectorAll('.results-tab').forEach(tab => {
         tab.addEventListener('click', function() {
             document.querySelectorAll('.results-tab').forEach(t => t.classList.remove('active'));
             document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
             this.classList.add('active');
             document.getElementById(this.dataset.tab).classList.add('active');
         });
     });
     // === Save Input ===
     document.getElementById('save-input').addEventListener('click', async function() {
         try {
             this.disabled = true;
             await writeFile('input.md', document.getElementById('input-editor').value);
             setStatus('input-status', '✓ Saved', 'success');
         } catch (e) {
             setStatus('input-status', '✗ ' + e.message, 'error');
         } finally {
             this.disabled = false;
         }
     });
     // === View File Buttons ===
     async function viewFile(filePath, viewerId) {
         const viewer = document.getElementById(viewerId);
         if (!viewer) return;
         if (viewer.classList.contains('visible')) { viewer.classList.remove('visible'); return; }
         try {
             const content = await readFile(filePath);
             viewer.innerHTML = content === null
                 ? '<p class="placeholder">File not found. Run the operation first.</p>'
                 : renderMarkdown(content);
             viewer.classList.add('visible');
         } catch (e) {
             viewer.innerHTML = '<p class="placeholder" style="color:red;">Error: ' + escapeHtml(e.message) + '</p>';
             viewer.classList.add('visible');
         }
     }
     document.querySelectorAll('.btn-view').forEach(btn => {
         btn.addEventListener('click', function() { viewFile(this.dataset.file, this.dataset.viewer); });
     });
     document.querySelectorAll('.results-content .btn-secondary[data-file]').forEach(btn => {
         btn.addEventListener('click', async function() {
             const viewer = document.getElementById(this.dataset.viewer);
             if (!viewer) return;
             try {
                 const content = await readFile(this.dataset.file);
                 viewer.innerHTML = content === null
                     ? '<p class="placeholder">File not found.</p>'
                     : renderMarkdown(content);
             } catch (e) {
                 viewer.innerHTML = '<p class="placeholder" style="color:red;">Error: ' + escapeHtml(e.message) + '</p>';
             }
         });
     });
     // === Run Operation Buttons ===
     document.querySelectorAll('.btn-run').forEach(btn => {
         btn.addEventListener('click', async function() {
             const opPath = this.dataset.op;
             const badgeId = this.dataset.badge;
             const outputPath = this.dataset.output;
             const viewerId = this.dataset.viewer;
             setBadge(badgeId, 'running');
             this.disabled = true;
             startStatusPolling();
             try {
                 const taskId = await runDocOp(opPath, outputPath);
                 await waitForTask(outputPath);
                 setBadge(badgeId, 'done');
                 if (viewerId) {
                     const viewer = document.getElementById(viewerId);
                     if (viewer) {
                         const content = await readFile(outputPath);
                         if (content) {
                             viewer.innerHTML = renderMarkdown(content);
                             viewer.classList.add('visible');
                         }
                     }
                 }
             } catch (e) {
                 setBadge(badgeId, 'error');
                 alert('Operation failed: ' + e.message);
             } finally {
                 this.disabled = false;
             }
         });
     });
     // === Batch Execution ===
     async function runSequential(steps) {
         for (const step of steps) {
             logBatch(`Starting: ${step.label}`, 'info');
             setBadge(step.badge, 'running');
             try {
                 const taskId = await runDocOp(step.op, step.output);
                 const cleanTaskId = taskId ? taskId.trim() : '';
                 if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
                     logBatchHtml(`Session: <a href="${getProxyUrl(cleanTaskId)}" target="_blank">📡 Monitor (${cleanTaskId})</a>`, 'info');
                 }
                 await waitForTask(step.output);
                 setBadge(step.badge, 'done');
                 logBatch(`✓ Completed: ${step.label}`, 'success');
                 if (step.viewer) {
                     try {
                         const content = await readFile(step.output);
                         if (content) {
                             const viewer = document.getElementById(step.viewer);
                             if (viewer) { viewer.innerHTML = renderMarkdown(content); viewer.classList.add('visible'); }
                         }
                     } catch (e) { /* non-critical */ }
                 }
             } catch (e) {
                 setBadge(step.badge, 'error');
                 logBatch(`✗ Failed: ${step.label} — ${e.message}`, 'error');
                 throw e;
             }
         }
     }
     document.getElementById('run-all').addEventListener('click', async function() {
         this.disabled = true;
         startStatusPolling();
         batchLog.innerHTML = '';
         try {
             await runSequential([
                 { op: 'ops/brainstorm_op.md', output: 'ideas.md', badge: 'badge-brainstorm', viewer: 'viewer-brainstorm', label: 'Brainstorm' },
                 { op: 'ops/report_op.md', output: 'report.md', badge: 'badge-report', viewer: 'viewer-report', label: 'Final Report' },
             ]);
             logBatch('🎉 Pipeline complete!', 'success');
         } catch (e) {
             logBatch('Pipeline stopped due to error.', 'error');
         } finally {
             this.disabled = false;
         }
     });
     // === Check Existing Files on Load ===
     async function checkExistingFiles() {
         const statusData = await fetchDocopsStatus();
         let anyRunning = false;
         if (statusData?.tasks) {
             for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
                 const badgeMap = { 'ideas.md': 'badge-brainstorm', 'report.md': 'badge-report' };
                 const badgeId = badgeMap[target];
                 if (badgeId) {
                     if (taskInfo.status === 'RUNNING') { setBadge(badgeId, 'running'); anyRunning = true; }
                     else if (taskInfo.status === 'COMPLETED') setBadge(badgeId, 'done');
                     else if (taskInfo.status === 'ERROR') setBadge(badgeId, 'error');
                 }
             }
         }
         // Fall back to file existence checks
         const checks = [
             { file: 'ideas.md', badge: 'badge-brainstorm' },
             { file: 'report.md', badge: 'badge-report' },
         ];
         for (const check of checks) {
             const badge = document.getElementById(check.badge);
             if (badge && (badge.classList.contains('running') || badge.textContent === 'done')) continue;
             try {
                 const content = await readFile(check.file);
                 if (content !== null && content.trim().length > 0) setBadge(check.badge, 'done');
             } catch (e) { /* leave as pending */ }
         }
         if (anyRunning) startStatusPolling();
     }
     // === Load Initial Files ===
     async function loadInitialFiles() {
         try {
             const content = await readFile('input.md');
             if (content !== null) document.getElementById('input-editor').value = content;
         } catch (e) { console.warn('Could not load input.md:', e); }
     }
     // === Initialize ===
     loadInitialFiles();
     checkExistingFiles();
     startStatusPolling();
})();
```

### Step 4: Write the Operation Definitions

Operation files are Markdown documents in `ops/` that instruct the AI agent. The platform reads these and executes them.
The exact format depends on your platform configuration, but they typically contain:

- A description of what the operation should do
- Which input files to read
- What format the output should be in
- Any specific instructions or constraints
  Example `ops/brainstorm_op.md`:

```markdown
# Brainstorm Ideas

Read the user's input from `input.md` and generate a comprehensive brainstorm
of ideas, approaches, and possibilities.

## Output Format

Write the results as a Markdown document with:

- A summary section
- Numbered list of ideas with brief descriptions
- Pros and cons for each idea
```

---

## API Reference

### File Operations

| Method | URL Pattern                        | Description             |
|--------|------------------------------------|-------------------------|
| `GET`  | `{basePath}/{filePath}`            | Read a file's contents  |
| `PUT`  | `{basePath}/{filePath}`            | Write/create a file     |
| `HEAD` | `{basePath}/{filePath}`            | Check if a file exists  |
| `GET`  | `{basePath}/{dirPath}/_files.json` | List directory contents |

### DocOps Execution

| Method | URL Pattern                                                | Description             |
|--------|------------------------------------------------------------|-------------------------|
| `POST` | `/docops?sessionId={sid}&doc={opPath}&target={targetPath}` | Trigger an AI operation |

**Parameters:**

| Parameter   | Type   | Required | Description                                                  |
|-------------|--------|----------|--------------------------------------------------------------|
| `sessionId` | string | Yes      | Session ID from URL                                          |
| `doc`       | string | Yes      | Path to operation definition (e.g., `ops/brainstorm_op.md`)  |
| `target`    | string | Yes      | Output file or directory path (e.g., `output.md` or `code/`) |

**Response:** Returns the task session ID as plain text. The operation runs asynchronously.

### Status File

| Method | URL Pattern                     | Description                         |
|--------|---------------------------------|-------------------------------------|
| `GET`  | `{basePath}/docops.status.json` | Read task status for all operations |

**Response format:**

```json
{
  "tasks": {
    "{targetPath}": {
      "status": "RUNNING|COMPLETED|ERROR|FAILED|PENDING",
      "sessionId": "task-session-id",
      "completedAt": "ISO-8601 timestamp (when completed)"
    }
  }
}
```

### Proxy/Monitoring

| URL Pattern               | Description                                         |
|---------------------------|-----------------------------------------------------|
| `/proxy/#{taskSessionId}` | Live monitoring view for a running AI agent session |

---

## Best Practices

### 1. Always Poll for Completion

Never assume a DocOp is complete when the POST returns. Always use `waitForTask()`:

```javascript
// ✅ Correct
await runDocOp('ops/my_op.md', 'output.md');
await waitForTask('output.md');
const result = await readFile('output.md');
// ❌ Wrong — race condition
await runDocOp('ops/my_op.md', 'output.md');
const result = await readFile('output.md'); // May not exist yet!
```

### 2. Auto-Save Before Running Operations

Save user input before triggering operations that depend on it:

```javascript
document.getElementById('run-pipeline').addEventListener('click', async function() {
     // Auto-save input first
     const content = document.getElementById('input-editor').value;
     if (!content.trim()) {
         alert('Please enter your input first.');
         return;
     }
     await writeFile('input.md', content);
     // Now run the pipeline
     await runDocOp('ops/step1_op.md', 'output.md');
     await waitForTask('output.md');
});
```

### 3. Check Existing State on Page Load

When the page loads (or reloads), check for existing files and running tasks:

```javascript
async function checkExistingFiles() {
     // 1. Check docops.status.json for running/completed tasks
     const statusData = await fetchDocopsStatus();
     if (statusData?.tasks) {
         for (const [target, taskInfo] of Object.entries(statusData.tasks)) {
             updateBadgeForTask(target, taskInfo);
             if (taskInfo.status === 'RUNNING') startStatusPolling();
         }
     }
     // 2. Fall back to file existence checks
     const checks = [
         { file: 'output.md', badge: 'badge-step1' },
     ];
     for (const check of checks) {
         const badge = document.getElementById(check.badge);
         if (badge?.classList.contains('running')) continue; // Don't override running
         try {
             const content = await readFile(check.file);
             if (content?.trim().length > 0) setBadge(check.badge, 'done');
         } catch (e) { /* leave as pending */ }
     }
}
```

### 4. Provide Session Monitoring Links

Always show proxy links for running tasks so users can see what the AI is doing:

```javascript
const taskId = await runDocOp(opPath, outputPath);
const cleanTaskId = taskId?.trim();
if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
     updateSessionLinks(outputPath, { status: 'RUNNING', sessionId: cleanTaskId });
}
```

### 5. Handle Errors Gracefully

- Show error badges on failed steps
- Log errors to the batch log with session links for debugging
- Don't let one failed step crash the entire UI
- Provide "View Error Log" links via the proxy URL

```javascript
try {
     await waitForTask(outputPath);
} catch (e) {
     setBadge(badgeId, 'error');
     const statusData = await fetchDocopsStatus();
     const failedTask = statusData?.tasks?.[outputPath];
     if (failedTask?.sessionId) {
         logBatchHtml(`Failed — <a href="${getProxyUrl(failedTask.sessionId)}" target="_blank">🔍 View Error Log</a>`, 'error');
     }
}
```

### 6. Use Consistent Data Attributes

Use `data-*` attributes on buttons to keep the HTML declarative and the JS generic:

```html

<button class="btn btn-run"
        data-op="ops/my_op.md"
        data-output="result.md"
        data-badge="badge-step1"
        data-viewer="viewer-step1">▶ Run
</button>
```

This lets you use a single event handler for all run buttons.

### 7. Wrap Everything in an IIFE

Prevent global namespace pollution:

```javascript
(function() {
     'use strict';
     // All your app code here
})();
```

### 8. Start Background Polling

Start status polling on page load (at a slow rate) to catch tasks started externally or from a previous page load:

```javascript
// At the end of your app initialization
loadInitialFiles();
checkExistingFiles();
startStatusPolling(); // Catches external changes
```

### 9. Validate User Input

For structured data (like JSON), validate before saving:

```javascript
document.getElementById('save-json').addEventListener('click', async function() {
     const content = document.getElementById('json-editor').value;
     try {
         JSON.parse(content); // Validate
     } catch (e) {
         setStatus('json-status', '✗ Invalid JSON: ' + e.message, 'error');
         return;
     }
     await writeFile('data.json', content);
     setStatus('json-status', '✓ Saved', 'success');
});
```

### 10. Design for Resumability

Users may close and reopen the page. Your app should:

- Load existing input files into editors on startup
- Check which output files already exist and mark those steps as done
- Detect running tasks and resume monitoring them
- Never require the user to re-run completed steps

---

## Troubleshooting

### Common Issues

| Problem                                              | Cause                                             | Solution                                                      |
|------------------------------------------------------|---------------------------------------------------|---------------------------------------------------------------|
| "Could not determine session from URL path"          | URL doesn't match expected pattern                | Ensure app is accessed via the platform's URL routing         |
| File writes fail with 403                            | Session may be read-only or expired               | Check session validity                                        |
| DocOp POST returns 404                               | Operation file path is wrong                      | Verify `ops/` directory contains the referenced `.md` file    |
| Task stays in RUNNING forever                        | AI agent may be stuck or very slow                | Check the proxy session link for details; consider timeout    |
| Status file returns 404                              | No operations have been run yet                   | This is normal — the file is created on first DocOp execution |
| Output file is empty after COMPLETED                 | Operation may have produced no output             | Check the proxy session log for the AI agent's activity       |
| Badge shows "done" but viewer shows "File not found" | File path mismatch between badge check and viewer | Ensure `data-output` and `data-file` attributes match         |

### Debugging Tips

1. **Check the browser console** for fetch errors and warnings
2. **Open the proxy session link** to see exactly what the AI agent did
3. **Manually fetch the status file** in the browser: `{basePath}/docops.status.json`
4. **Manually fetch output files** to verify they exist: `{basePath}/output.md`
5. **Check the operation definition** (`ops/*.md`) for correctness
6. **Look at the batch log** for timestamped execution history

### Network Tab Debugging

Open browser DevTools → Network tab and look for:

- `PUT` requests to verify file saves
- `POST /docops` requests to verify operation triggers
- `GET docops.status.json` requests to verify polling
- `GET` requests for output files to verify result loading

### Task Session ID Validation

The task ID returned by `POST /docops` may contain whitespace or unexpected content. Always clean it:

```javascript
const taskId = await runDocOp(opPath, outputPath);
const cleanTaskId = taskId ? taskId.trim() : '';
if (cleanTaskId && /^[a-zA-Z0-9-]+$/.test(cleanTaskId)) {
     // Valid session ID — safe to use in URLs
} else {
     console.warn('Unexpected task ID format:', taskId);
}
```