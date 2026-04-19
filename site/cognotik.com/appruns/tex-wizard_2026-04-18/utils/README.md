# Resume Customizer — Utility Modules

This directory contains shared utility modules used throughout the Resume Customizer application. Each module is a
self-contained ES module with named exports and a convenience namespace export.

---

## Module Overview

| Module            | Namespace Export   | Purpose                                          |
|-------------------|--------------------|--------------------------------------------------|
| `docops.js`       | `DocOpsUtils`      | Run DocOps operations and poll task status       |
| `fileIO.js`       | `FileIOUtils`      | Read, write, delete, and list session files      |
| `git.js`          | `GitUtils`         | Git repository operations via the Git API        |
| `models.js`       | `ModelUtils`       | Load and manage AI model/provider selections     |
| `session.js`      | `SessionUtils`     | Parse session URLs and build proxy links         |
| `sessionLinks.js` | `SessionLinkUtils` | Render live session monitoring links in the DOM  |
| `sessionLinks.js` | `SessionLinkUtils` | Lightweight session link DOM helpers (alternate) |
| `ui.js`           | `UIUtils`          | Markdown rendering, toasts, badges, logging      |
| `usage.js`        | `UsageUtils`       | Fetch, aggregate, and render AI token usage      |

---

## Usage

All modules use ES module syntax. Import individual functions or the namespace object:

```js
// Named import (preferred)
import { runDocOp, waitForTask } from './utils/docops.js';

// Namespace import
import { DocOpsUtils } from './utils/docops.js';
DocOpsUtils.runDocOp(sessionId, opPath, targetPath);
```

---

## Module Reference

### `docops.js` — DocOps Execution

Utilities for triggering DocOps pipeline operations and polling their status.

#### `runDocOp(sessionId, opPath, targetPath, models?)`

Starts a DocOps operation via `POST /docops`.

| Parameter    | Type     | Description                                       |
|--------------|----------|---------------------------------------------------|
| `sessionId`  | `string` | Active session ID                                 |
| `opPath`     | `string` | Path to the operation document                    |
| `targetPath` | `string` | Output target path                                |
| `models`     | `Object` | Optional: `{ smartModel, fastModel, imageModel }` |

Returns a `Promise<string>` — the task/session ID.

```js
const taskId = await runDocOp(sessionId, 'ops/analyze.md', 'job-analysis.md', {
  smartModel: 'gpt-4o',
  fastModel: 'gpt-4o-mini'
});
```

#### `fetchDocopsStatus(basePath)`

Fetches the current `docops.status.json` for a session.

Returns `Promise<Object|null>`.

#### `waitForTask(basePath, targetPath, maxWaitMs?, onStatusUpdate?)`

Polls until a specific task reaches `COMPLETED` or `ERROR`/`FAILED` status.

| Parameter        | Type       | Default  | Description                                          |
|------------------|------------|----------|------------------------------------------------------|
| `basePath`       | `string`   | —        | Session base path                                    |
| `targetPath`     | `string`   | —        | Target file to monitor                               |
| `maxWaitMs`      | `number`   | `600000` | Timeout in milliseconds (10 min)                     |
| `onStatusUpdate` | `Function` | `null`   | Called on each status change: `(target, task) => {}` |

Throws on timeout or task failure.

#### `createStatusPoller(basePath, onUpdate, interval?)`

Creates a reusable poller that fires `onUpdate(target, taskInfo)` for every task in the status file.

```js
const poller = createStatusPoller(basePath, (target, taskInfo) => {
  console.log(target, taskInfo.status);
});
poller.start();
// later...
poller.stop();
```

---

### `fileIO.js` — File I/O

Read and write files in session storage over HTTP.

#### `readFile(basePath, filePath)`

Returns `Promise<string|null>` — file content, or `null` if not found (404/400).

#### `writeFile(basePath, filePath, content)`

`PUT`s content to the given path. Returns `Promise<boolean>`.

#### `fileExists(basePath, filePath)`

`HEAD` request to check existence. Returns `Promise<boolean>`.

#### `listFiles(basePath, dirPath)`

Fetches `_files.json` for a directory. Returns `Promise<Array>` of file entries.

#### `deleteFile(basePath, filePath)`

`DELETE` request. Returns `Promise<boolean>` (true if deleted or already gone).

```js
import { readFile, writeFile } from './utils/fileIO.js';

const content = await readFile(basePath, 'resume.json');
await writeFile(basePath, 'resume-custom.json', JSON.stringify(data, null, 2));
```

---

### `git.js` — Git Operations

Wraps the session Git API (`/.git/api/`) for repository management.

#### `gitApiCall(basePath, endpoint, options?)`

Low-level fetch wrapper. Throws on HTTP errors or `{ success: false }` responses.

#### `getStatus(basePath)`

Returns current repo status including branch, clean/dirty state, and changed files.

#### `initRepository(basePath)`

Initializes a new Git repository in the session.

#### `commit(basePath, message)`

Stages all changes and creates a commit.

```js
await commit(basePath, 'Customized resume for Acme Corp');
```

#### `getBranches(basePath)`

Returns branch list data.

#### `checkout(basePath, branch, create?)`

Checks out an existing branch, or creates and checks out a new one when `create = true`.

#### `getLog(basePath, maxCount?)`

Returns commit history (default: last 20 commits).

#### `formatStatus(statusData)`

Converts a status API response into an HTML string suitable for direct `innerHTML` injection.

---

### `models.js` — AI Model Management

Handles loading available AI providers/models and persisting user selections.

#### `loadApiProviders()`

Fetches `/apiProviders/?format=json` and returns models grouped by provider name.

```js
const models = await loadApiProviders();
// { 'OpenAI': [{ id: 'gpt-4o', name: 'gpt-4o', description: '...' }], ... }
```

#### `populateModelDropdowns(availableModels, selectElements, savedSelections?)`

Populates one or more `<select>` elements with optgroups per provider, and restores any previously saved selections.

#### `saveModelSelections(prefix, selections)`

Persists model selections to `localStorage` under `{prefix}_{key}` keys.

#### `loadModelSelections(prefix, keys)`

Loads model selections from `localStorage`. Returns `{ [key]: value }`.

```js
saveModelSelections('resumeApp', { smartModel: 'gpt-4o', fastModel: 'gpt-4o-mini' });
const saved = loadModelSelections('resumeApp', ['smartModel', 'fastModel']);
```

---

### `session.js` — Session & URL Utilities

Parses the current page URL to extract session context.

#### `parseSessionUrl()`

Reads `window.location.pathname` and returns:

```js
{
  basePath: '/apps/resume-customizer/fileIndex/abc123',
  sessionId: 'abc123',
  appId: 'resume-customizer'
}
```

#### `getProxyUrl(id)`

Returns `/proxy/#<id>` — the URL for monitoring a session in the proxy viewer.

#### `getAppRoot()`

Returns the path prefix before `/fileIndex/`, used for ZIP and Git endpoints.

---

### `sessionLinks.js` — Session Monitoring Links

Injects live session monitoring links into the DOM based on task status.

#### `updateSessionLinks(target, taskInfo, getProxyUrl)`

Finds or creates a `div.session-link-container` near the relevant output element and renders:

- **RUNNING** → animated pulse + "Monitor Live Session" link
- **COMPLETED** → completion time + "View Session Log" link
- **ERROR/FAILED** → "View Error Log" link

#### `createSessionLinkManager(getProxyUrl)`

Returns a manager object that tracks session IDs per target:

```js
const linkManager = createSessionLinkManager(getProxyUrl);

// In your status poller callback:
linkManager.update(target, taskInfo);

// Retrieve tracked session IDs:
linkManager.getSessionId('job-analysis.md');
linkManager.getAllSessions();
linkManager.clear();
```

---

### `sessionLinks.js` — Lightweight Session Link Helpers

A simpler alternative to `sessionLinks.js` that maps known target filenames to fixed container element IDs.

#### `SessionLinkUtils.updateSessionLinks(target, taskInfo, getProxyUrl, containerId?)`

Updates a named container element. Built-in target→container mappings:

| Target File                   | Container ID      |
|-------------------------------|-------------------|
| `job-analysis.md`             | `analyze-links`   |
| `company-research.md`         | `research-links`  |
| `resume-custom.json`          | `customize-links` |
| `standard.pdf` / `simple.pdf` | `render-links`    |

#### `SessionLinkUtils.createSessionLink(url, text, className?)`

Creates and returns an `<a>` element with `target="_blank"`.

---

### `ui.js` — UI Utilities

General-purpose DOM and display helpers.

#### `renderMarkdown(md)`

Renders markdown to HTML using the global `marked` library if available, otherwise falls back to `<pre>` with escaped
content.

#### `escapeHtml(text)`

Safely escapes `<`, `>`, `&`, and `"` for HTML injection.

#### `setStatus(elemId, message, type?, autoClearMs?)`

Sets a status message element's text and CSS class. Auto-clears `success`/`error` messages after `autoClearMs` (default:
5000ms). Types: `success`, `error`, `info`, `warning`.

#### `setBadge(badgeId, state)`

Updates a step badge element. States: `pending`, `running`, `done`, `error`.

#### `showToast(message, type?, duration?)`

Appends a toast notification to `#toast-container` (created if absent). Types: `success`, `error`, `info`, `warning`.
Default duration: 4000ms.

#### `createBatchLogger(logId)`

Returns a logger bound to a DOM element:

```js
const log = createBatchLogger('batch-log');
log.log('Starting analysis…', 'info');
log.logHtml('<strong>Done!</strong>', 'success');
log.clear();
```

#### `getFileIcon(filename)`

Returns an emoji icon for a file based on its extension (e.g. `📝` for `.md`, `📕` for `.pdf`).

---

### `usage.js` — Token Usage Tracking

Fetches and displays AI token usage and cost data.

#### `fetchUsageData(sessionId)`

Fetches usage from `/proxy/usage?sessionId=<id>&format=json`. Returns `Promise<Object|null>`.

#### `formatTokenCount(n)`

Formats a number as `1.2K`, `3.45M`, or a plain locale string.

#### `formatCost(cost)`

Formats a cost value as a dollar string (e.g. `$0.0042`, `< $0.001`).

#### `aggregateUsage(sessionIds)`

Fetches usage for multiple sessions in parallel and merges the results:

```js
const { models, totals, sessionUsageMap } = await aggregateUsage([id1, id2, id3]);
```

#### `renderUsageSummary(totals, elements)`

Populates DOM elements with formatted token/cost values:

```js
renderUsageSummary(totals, {
  prompt: document.getElementById('prompt-tokens'),
  completion: document.getElementById('completion-tokens'),
  total: document.getElementById('total-tokens'),
  cost: document.getElementById('total-cost')
});
```

#### `createUsageTableHtml(models, totals)`

Returns an HTML string for a full usage breakdown table, sorted by cost descending, with a totals row.

---

## Adding a New Utility Module

1. Create `utils/myUtil.js` with named exports.
2. Add a namespace export at the bottom:
   ```js
   export const MyUtils = { myFunction, anotherFunction };
   ```
3. Import in your page script:
   ```js
   import { myFunction } from './utils/myUtil.js';
   ```
4. Document it in this README.

---

## Dependencies

| Dependency | Source            | Used By                      |
|------------|-------------------|------------------------------|
| `marked`   | Global CDN script | `ui.js` — `renderMarkdown()` |

All other utilities depend only on the browser Fetch API and standard DOM APIs. No build step is required.