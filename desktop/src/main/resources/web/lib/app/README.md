# Resume Customizer — Utility Modules

This directory contains shared utility modules used throughout the Resume Customizer application (and related apps
such as Goal Planner). Each module is a self-contained ES module with named exports and a convenience namespace
export.

NOTE: These assets can be accessed via the path `/lib/app/` (absolute path to same host)

---

## Module Overview

| Module            | Namespace Export   | Purpose                                                            |
|-------------------|--------------------|--------------------------------------------------------------------|
| `docops.js`       | `DocOpsUtils`      | Run DocOps operations and poll task status                         |
| `fileIO.js`       | `FileIOUtils`      | Read, write, delete, and list session files                        |
| `git.js`          | `GitUtils`         | Git repository operations via the Git API                          |
| `marked.min.js`   | *(global)*         | Vendored markdown renderer (loaded via `<script>`, not ES module)  |
| `models.js`       | `ModelUtils`       | Load and manage AI model/provider selections                       |
| `session.js`      | `SessionUtils`     | Parse session URLs and build proxy links                           |
| `sessionLinks.js` | `SessionLinkUtils` | Render live session monitoring links in the DOM                    |
| `ui.js`           | `UIUtils`          | Markdown rendering, toasts, badges, logging                        |
| `usage.js`        | `UsageUtils`       | Fetch, aggregate, and render AI token usage                        |

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

### Loading `marked` locally

`ui.js` calls the global `marked` object for markdown rendering. **Do not load `marked` from a CDN** — a vendored
copy lives at `utils/marked.min.js`. Include it in your HTML **before** your module script:

```html
<!-- Local copy of marked — used by utils/ui.js renderMarkdown() -->
<script src="utils/marked.min.js"></script>
<script type="module" src="app.js"></script>
```

This keeps the app functional offline and avoids CDN/CSP issues.

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

> **Tip:** Only include model keys whose values are non-empty. The server rejects empty-string values for optional
> params like `imageModel`; omitting the key entirely lets the server use its default.

```js
const models = {};
if (smartVal) models.smartModel = smartVal;
if (fastVal)  models.fastModel  = fastVal;
if (imageVal) models.imageModel = imageVal;
const taskId = await runDocOp(sessionId, 'ops/analyze.md', 'job-analysis.md', models);
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

> **Best practice:** Inside `onStatusUpdate`, call **both** `linkManager.update(target, taskInfo)` and your own
> session-tracking logic so that session monitoring links remain visible for the whole lifetime of the page.

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

`selectElements` is an **array** of `<select>` elements, and `savedSelections` is an object keyed by the model role
(e.g. `{ smartModel: 'gpt-4o', fastModel: 'gpt-4o-mini' }`). Build these from your role→element map before calling:

```js
const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
const selects = {
smartModel: document.getElementById('smart-model'),
fastModel:  document.getElementById('fast-model'),
imageModel: document.getElementById('image-model')
};
const selectArray = MODEL_KEYS.map(k => selects[k]).filter(Boolean);
const savedByKey  = loadModelSelections('myApp', MODEL_KEYS);
populateModelDropdowns(availableModels, selectArray, savedByKey);
```

#### `saveModelSelections(prefix, selections)`

Persists model selections to `localStorage` under `{prefix}_{key}` keys.

#### `loadModelSelections(prefix, keys)`

Loads model selections from `localStorage`. Returns `{ [key]: value }`.

```js
saveModelSelections('resumeApp', { smartModel: 'gpt-4o', fastModel: 'gpt-4o-mini' });
const saved = loadModelSelections('resumeApp', ['smartModel', 'fastModel', 'imageModel']);
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

#### `updateSessionLinks(target, taskInfo, getProxyUrl, containerId?)`

Finds a container element and renders:

- **RUNNING** → "Monitor Live Session" link
- **COMPLETED** → "Monitor" + "View Session" links
- **ERROR/FAILED** → "View Error Log" link

Container resolution order:

1. Explicit `containerId` argument (if provided).
2. Built-in target→container mapping (see below).
3. Element matching `[data-session-links="<target>"]`.

Built-in target→container mappings:

| Target File                   | Container ID      |
|-------------------------------|-------------------|
| `job-analysis.md`             | `analyze-links`   |
| `company-research.md`         | `research-links`  |
| `resume-custom.json`          | `customize-links` |
| `standard.pdf` / `simple.pdf` | `render-links`    |
| `standard.tex` / `simple.tex` | `render-links`    |

For other targets, add a container with a data attribute:

```html
<div class="session-link-container" data-session-links="my-target.md"></div>
```

#### `createSessionLink(url, text, className?)`

Creates and returns an `<a>` element with `target="_blank"`.

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

> **Best practice — always-visible session links:**
> `updateSessionLinks()` clears and re-renders its container on every call, so a session link will *disappear* if
> you only call it from a running task's callback. To keep a session's monitoring link visible for the life of the
> page (even after the task completes), maintain your own `Map<target, taskInfo>` of every session you've seen and
> render a persistent "Active Sessions" panel from it. See `goal-planner/app.js` (`trackSession` +
> `renderActiveSessionsPanel`) for a working example.

---

### `ui.js` — UI Utilities

General-purpose DOM and display helpers.

#### `renderMarkdown(md)`

Renders markdown to HTML using the global `marked` library if available, otherwise falls back to `<pre>` with escaped
content.

**Requires the vendored `utils/marked.min.js` script tag to be present in your HTML** (see
[Loading `marked` locally](#loading-marked-locally) above). If `marked` isn't loaded, rendering degrades to plain
preformatted text — useful output, but without formatting.

Typical pattern for displaying a markdown file that was just generated by docops:

```js
import { renderMarkdown } from './utils/ui.js';
import { readFile, fileExists } from './utils/fileIO.js';

async function refreshPreview(target, previewElId) {
if (!(await fileExists(basePath, target))) return;
const md = await readFile(basePath, target);
if (md != null) {
 document.getElementById(previewElId).innerHTML = renderMarkdown(md);
}
}
```

> **Tip:** Style previews with a `.markdown-preview` container class (see `goal-planner/app.html` for example CSS).

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

## Best Practices for New Apps

When building a new app on top of these utilities, follow these conventions:

1. **Vendor `marked` locally.** Include `<script src="utils/marked.min.js"></script>` in your HTML so
`renderMarkdown()` works offline. Do **not** pull `marked` from a CDN.
2. **Render markdown previews inline.** For every `.md` artifact your app generates, give it a
`<div class="markdown-preview">` next to the step's action buttons, and call `renderMarkdown(readFile(...))`
whenever the file changes (on init, after a docops run, after a manual save). See `goal-planner/app.js`'s
`refreshMarkdownPreview()` and `MARKDOWN_PREVIEWS` map for the pattern.
3. **Keep session links visible for the life of the page.** Track every docops task you've started in a
`Map<target, taskInfo>` and render a persistent "Active Sessions" panel from it. Update that map from **every**
status callback (both per-step `waitForTask` and global `pollExistingTasks`), so links remain available even
after a task completes. `sessionLinks.js`'s built-in container helpers clear their DOM on every render — they
are not a substitute for a persistent panel.
4. **Call the status poller on a schedule** (e.g. `setInterval(pollExistingTasks, 15000)`) so the page recovers
state after a reload or navigation. Always re-render the active-sessions panel from the poller as well.
5. **Omit empty model keys** when calling `runDocOp` — send only the roles the user has actually chosen. The server
rejects empty-string model values for optional roles like `imageModel`.
6. **Mirror button state from files on disk.** Use `fileExists()` / `listFiles()` in `refreshStatus()` to decide
which buttons are enabled and which `View`/`Open` links are visible. This keeps the UI correct after reloads.
7. **Log verbosely to the console and to the on-page activity log.** The DocOps pipeline is asynchronous; users
benefit from a clear running narrative, and developers benefit from machine-readable console output.
8. **Handle unhandled errors.** Install `window.addEventListener('error', …)` and `'unhandledrejection'` handlers
that pipe messages into the activity log — this surfaces silent failures during async polling.

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

| Dependency | Source                        | Used By                      |
|------------|-------------------------------|------------------------------|
| `marked`   | Vendored: `utils/marked.min.js` | `ui.js` — `renderMarkdown()` |

All other utilities depend only on the browser Fetch API and standard DOM APIs. No build step is required.