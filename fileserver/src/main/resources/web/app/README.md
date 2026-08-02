# Cognotik Shared Web Modules (`/app/`)

This directory contains the shared front-end utility modules used by the Cognotik web apps
(Resume Customizer, Goal Planner, Presentation Creator, …). Each module is a self-contained ES module
with named exports plus a convenience namespace export.

> **These assets are served from the host-absolute path `/app/`.** Import them as
> `/app/<module>.js` — *not* as `./utils/<module>.js`. If your deployment is **not** mounted at the
> host root, see [Deploying behind a path prefix](#deploying-behind-a-path-prefix).

---

## Module Overview

| Module      | Namespace Export | Purpose                                                          |
|-------------|------------------|------------------------------------------------------------------|
| `config.js` | `ConfigUtils`    | Runtime configuration: server base URL, app/session overrides     |
| `docops.js` | `DocOpsUtils`    | Run DocOps operations and poll task status                        |
| `fileIO.js` | `FileIOUtils`    | Read, write, delete, and list session files                       |
| `menu.js`   | `MenuUtils`      | Common application menubar: nav, IDE link, git, sessions, usage   |
| `models.js` | `ModelUtils`     | Load and manage AI model/provider selections                      |

Other files exist in this directory (`ui.js`, `session.js`, `marked.min.js`, …). They are used
internally by the modules above or loaded directly by apps, and are out of scope for this document.

---

## Quick Start

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <!-- Optional: only needed if the server is NOT mounted at the host root -->
  <!-- <meta name="cognotik-server-base" content="/cognotik"> -->
</head>
<body>
  <!-- Local copy of marked — required by /app/ui.js renderMarkdown() -->
  <script src="/app/marked.min.js"></script>
  <script type="module" src="app.js"></script>
</body>
</html>
```

```js
// app.js — named imports (preferred)
import { initMenu }               from '/app/menu.js';
import { runDocOp, waitForTask }  from '/app/docops.js';
import { readFile, writeFile }    from '/app/fileIO.js';

// …or a namespace import
import { DocOpsUtils } from '/app/docops.js';
DocOpsUtils.runDocOp(sessionId, opPath, targetPath);
```

### Loading `marked` locally

`ui.js` calls the global `marked` object for markdown rendering. **Do not load `marked` from a CDN** —
a vendored copy lives at `/app/marked.min.js`. Include it with a classic `<script>` tag **before** your
module script so the global is defined by the time modules run. This keeps the app functional offline
and avoids CDN/CSP issues.

### Deploying behind a path prefix

Every module that talks to an *absolute* server endpoint (`/docops`, `/apiProviders/`, `/proxy/…`)
routes through `serverUrl()`. Set the base **before** your module script executes, via any of:

```html
<script>window.COGNOTIK_CONFIG = { serverBase: '/cognotik' };</script>
<!-- or --> <meta name="cognotik-server-base" content="/cognotik">
<!-- or --> <html data-server-base="/cognotik">
```

```js
// or at runtime, before any other module makes a request
import { configure } from '/app/config.js';
configure({ serverBase: '/cognotik' });
```

Note that `basePath`-relative calls (`fileIO.js`, `fetchDocopsStatus`) are **not** rewritten — the
`basePath` you pass in must already be correct for your deployment.

See [`migration.md`](./migration.md) for the full migration checklist.

---

## Module Reference

### `config.js` — Runtime Configuration

Resolves the *server base* used by every absolute API endpoint, plus optional app/session overrides.
Defaults to `''` (host root), so apps deployed at the root need no changes.

#### `getConfig()`

Returns the effective configuration, merging in priority order:

1. values passed to `configure()`
2. `window.COGNOTIK_CONFIG`
3. `<meta name="cognotik-*">` tags
4. `<html data-server-base>`
5. built-in defaults

| Key                | Default                                 | Purpose                                     |
|--------------------|-----------------------------------------|---------------------------------------------|
| `serverBase`       | `''`                                    | Prefix for absolute server endpoints        |
| `appId`            | `null`                                  | Override URL-derived app id                 |
| `sessionId`        | `null`                                  | Override URL-derived session id             |
| `basePath`         | `null`                                  | Override URL-derived session base path      |
| `sessionsEndpoint` | `null`                                  | Session list endpoint used by `menu.js`     |
| `ideUrlTemplate`   | `'{appRoot}/ui/?session={sessionId}#/'` | Template for the filesystem IDE link        |

> The result is **cached** after the first call. Only `configure()` invalidates the cache — mutating
> `window.COGNOTIK_CONFIG` after the first `getConfig()` has no effect.

#### `configure(overrides)`

Sets configuration at runtime, clears the cache, and returns the new effective config.

```js
import { configure } from '/app/config.js';
configure({ serverBase: '/cognotik' });
```

#### `serverUrl(path)`

Joins `path` onto `serverBase`. Absolute URLs (`http://`, `https://`, `//host/…`) are returned
unchanged. An empty `path` yields `serverBase` (or `'/'` when unset).

```js
serverUrl('/docops');   // '' base -> '/docops';  '/cognotik' base -> '/cognotik/docops'
```

---

### `docops.js` — DocOps Execution

Utilities for triggering DocOps pipeline operations and polling their status.

#### `runDocOp(sessionId, opPath, targetPath, models?)`

Starts a DocOps operation via `POST {serverBase}/docops`.

| Parameter    | Type     | Description                                       |
|--------------|----------|---------------------------------------------------|
| `sessionId`  | `string` | Active session ID                                 |
| `opPath`     | `string` | Path to the operation document                    |
| `targetPath` | `string` | Output target path                                |
| `models`     | `Object` | Optional: `{ smartModel, fastModel, imageModel }` |

Returns `Promise<string>` — the task/session ID. Rejects with an `Error` containing the status code and
response body on a non-2xx response. If the response is JSON but contains none of `sessionId`,
`taskId`, or a non-empty `sessions` map, an **empty string** is returned — check for it before using
the value to build monitor links.

> **Tip:** Only include model keys whose values are non-empty. The server rejects empty-string values
> for optional params like `imageModel`; omitting the key entirely lets the server use its default.
> `runDocOp` already skips falsy values, so the guards below are belt-and-braces for readability.

```js
const models = {};
if (smartVal) models.smartModel = smartVal;
if (fastVal)  models.fastModel  = fastVal;
if (imageVal) models.imageModel = imageVal;

const taskId = await runDocOp(sessionId, 'ops/analyze.md', 'job-analysis.md', models);
```

#### `fetchDocopsStatus(basePath)`

Fetches `{basePath}/docops.status.json` for a session. Returns `Promise<Object|null>` — `null` on any
network error or non-2xx response (it never throws).

#### `waitForTask(basePath, targetPath, maxWaitMs?, onStatusUpdate?)`

Polls every 2s until a specific task reaches `COMPLETED` (resolves with the task object) or
`ERROR`/`FAILED` (throws).

| Parameter        | Type       | Default  | Description                                          |
|------------------|------------|----------|------------------------------------------------------|
| `basePath`       | `string`   | —        | Session base path                                    |
| `targetPath`     | `string`   | —        | Target file to monitor                               |
| `maxWaitMs`      | `number`   | `600000` | Timeout in milliseconds (10 min)                     |
| `onStatusUpdate` | `Function` | `null`   | Called on each poll that finds the task: `(target, task) => {}` |

Task lookup tolerates path variations: the exact `targetPath`, the same path with a trailing slash
added/removed, and finally the bare filename.

> **Best practice:** `onStatusUpdate` fires on *every* successful poll, not only on transitions — make
> it idempotent. If your app renders session-monitor links, update them here **and** in your own
> session-tracking state so the links stay visible for the whole lifetime of the page.

#### `createStatusPoller(basePath, onUpdate, interval?)`

Creates a reusable poller that fires `onUpdate(target, taskInfo)` for **every** task in the status
file on each tick (default `interval` 3000 ms). `start()` performs an immediate poll and is a no-op if
already running.

```js
const poller = createStatusPoller(basePath, (target, taskInfo) => {
  console.log(target, taskInfo.status);
});
poller.start();
poller.isRunning();  // true
poller.stop();       // remember to call this on page teardown
```

---

### `fileIO.js` — File I/O

Read and write files in session storage over HTTP. All paths are resolved as
`` `${basePath}/${filePath}` `` — pass a `basePath` that is already valid for your deployment.

| Function                             | Method   | Returns                | Notes                                                   |
|--------------------------------------|----------|------------------------|---------------------------------------------------------|
| `readFile(basePath, filePath)`       | `GET`    | `Promise<string\|null>` | `null` on 404/400; **throws** on any other error status |
| `writeFile(basePath, filePath, content)` | `PUT` | `Promise<true>`        | Sends `text/plain; charset=utf-8`; **throws** on failure |
| `fileExists(basePath, filePath)`     | `HEAD`   | `Promise<boolean>`     | Never throws — network errors resolve to `false`         |
| `listFiles(basePath, dirPath)`       | `GET`    | `Promise<Array>`       | Reads `<dirPath>/_files.json`; `[]` on error             |
| `deleteFile(basePath, filePath)`     | `DELETE` | `Promise<boolean>`     | `true` if deleted **or** already gone (404)              |

```js
import { readFile, writeFile } from '/app/fileIO.js';

const content = await readFile(basePath, 'resume.json');
await writeFile(basePath, 'resume-custom.json', JSON.stringify(data, null, 2));
```

---

### `models.js` — AI Model Management

Handles loading available AI providers/models and persisting user selections.

#### `loadApiProviders()`

Fetches `{serverBase}/apiProviders/?format=json` and returns models grouped by provider name.
**Never throws** — returns `{}` and logs a warning on error or on a 4xx/5xx response, so a missing
provider config degrades gracefully.

```js
const models = await loadApiProviders();
// { 'OpenAI': [{ id: 'gpt-4o', name: 'gpt-4o' }, …], 'Anthropic': [ … ] }
```

Only providers with a non-empty `models` array are included. Each entry is `{ id, name }` (both taken
from the server's `model.name`). A `description` property is *not* currently produced by this
function, though `populateModelDropdowns` will surface one as a tooltip if you inject it yourself.

#### `populateModelDropdowns(availableModels, selectElements, savedSelections?)`

Clears and repopulates one or more `<select>` elements with an `<optgroup>` per provider, prepends a
`— Select a model —` placeholder, and restores previously saved selections. If no provider has any
models, each select instead gets a single disabled `No models available — configure API keys first`
option.

A model `id` is only added **once across all providers** — the first provider (in
`Object.entries` order) that offers a given id wins.

> ⚠️ **Saved selections are matched by position, not by name.** The implementation pairs
> `selectElements[i]` with `Object.keys(savedSelections)[i]`. You must therefore build both from the
> *same ordered key list*, and you must **not** compact the array — `.filter(Boolean)` will shift
> every subsequent element and restore the wrong values.

```js
const MODEL_KEYS = ['smartModel', 'fastModel', 'imageModel'];
const selects = {
  smartModel: document.getElementById('smart-model'),
  fastModel:  document.getElementById('fast-model'),
  imageModel: document.getElementById('image-model')
};

// Keep indexes aligned with MODEL_KEYS: filter *both* sides together.
const presentKeys  = MODEL_KEYS.filter(k => selects[k]);
const selectArray  = presentKeys.map(k => selects[k]);
const savedAll     = loadModelSelections('myApp', MODEL_KEYS);
const savedByKey   = Object.fromEntries(presentKeys.map(k => [k, savedAll[k]]));

populateModelDropdowns(availableModels, selectArray, savedByKey);
```

A saved value is only re-applied if it still exists as an option, so removing a provider's API key
cleanly falls back to the placeholder.

#### `saveModelSelections(prefix, selections)`

Persists selections to `localStorage` under `{prefix}_{key}` keys. A falsy value **removes** the key
rather than storing an empty string.

#### `loadModelSelections(prefix, keys)`

Loads selections from `localStorage`. Always returns an entry for every requested key, defaulting to
`''`.

```js
saveModelSelections('resumeApp', { smartModel: 'gpt-4o', fastModel: 'gpt-4o-mini' });
const saved = loadModelSelections('resumeApp', ['smartModel', 'fastModel', 'imageModel']);
// { smartModel: 'gpt-4o', fastModel: 'gpt-4o-mini', imageModel: '' }
```

---

### `menu.js` — Common Application Menubar

Renders a shared menubar at the top of any app page. It is fully self-contained (styles are injected)
and context-aware.

```js
import { initMenu } from '/app/menu.js';
const menu = initMenu({ appName: 'Resume Customizer' });
```

#### `initMenu(options?)`

| Option             | Type                    | Default            | Description                                     |
|--------------------|-------------------------|--------------------|-------------------------------------------------|
| `mount`            | `HTMLElement \| string` | top of `<body>`    | Where to insert the bar                         |
| `appName`          | `string`                | derived app id     | Label shown in the context chip                 |
| `showGit`          | `boolean`               | `true`             | Show the Git panel                              |
| `showSessions`     | `boolean`               | `true`             | Show the Sessions panel                         |
| `showUsage`        | `boolean`               | `true`             | Show the Usage panel                            |
| `showIde`          | `boolean`               | `true`             | Show the filesystem IDE link                    |
| `sticky`           | `boolean`               | `true`             | `position: sticky` at the top                   |
| `newSessionPath`   | `string`                | `'new'`            | Path segment appended to the app root           |
| `getProxyUrl`      | `Function`              | `session.js` impl. | Builds monitor links                            |
| `sessionIds`       | `Array \| Function`     | `null`             | Extra session IDs folded into usage totals      |
| `extraLinks`       | `Array`                 | `[]`               | `[{ href, label, target }]` appended to the nav |
| `sessionsEndpoint` | `string`                | auto-probed        | Endpoint returning the list of sessions         |

Returns a controller:

```js
menu.open('usage');       // 'git' | 'sessions' | 'usage'
menu.close();
await menu.refresh();     // refreshGit() + refreshSessions() + refreshUsage()
menu.context;             // { view, appId, sessionId, appRoot, basePath, pathname }
menu.destroy();           // remove the bar and its listeners
```

#### `getMenuContext()`

Classifies the current URL into one of the following views:

| `view`    | Matches                              | Derived values            |
|-----------|--------------------------------------|---------------------------|
| `home`    | `/`                                  | —                         |
| `app`     | `/<app>/…`                           | `appId`, `appRoot`        |
| `new`     | `/<app>/new`                         | `appId`, `appRoot`        |
| `session` | `/<app>/fileIndex/<session>/…`       | + `sessionId`, `basePath` |
| `ide`     | `/<app>/ui/?session=<session>#/`     | + `sessionId`             |
| `proxy`   | `/proxy/?session=<session>`          | `sessionId`               |

Values supplied via `configure({ appId, sessionId, basePath })` take precedence over URL-derived ones.

#### `getIdeUrl(ctx)`

Builds the filesystem IDE URL for a context, e.g.
`/presentation-creator/ui/?session=U-20260801-TH2s9UEo#/`. Override the shape with
`configure({ ideUrlTemplate })` using the `{appRoot}` and `{sessionId}` placeholders.

#### `fetchSessionList(ctx, endpoint?)`

Returns `[{ sessionId, name, active }]`. When no endpoint is configured it probes, in order:
`<appRoot>/api/sessions?format=json`, `<appRoot>/sessions?format=json`, `/api/sessions?format=json`.
Pin it with `configure({ sessionsEndpoint })` or the `sessionsEndpoint` option to avoid the extra
round trips (and the 404s in the console).

#### `fetchRunningTasks(basePath)`

Reads `docops.status.json` and returns `[{ target, status, taskId }]` — used to render the *currently
running* list and to fold task session IDs into usage aggregation.

---

## Common Pitfalls

1. **Importing from `./utils/…`** — modules now live at `/app/`. Update relative imports.
2. **Loading `marked` from a CDN or `/lib/`** — use `/app/marked.min.js`, loaded as a classic script
 *before* your module script.
3. **Passing empty-string model overrides** to `runDocOp` — omit the key entirely instead.
4. **Compacting `selectElements` without compacting `savedSelections`** — see the warning under
 `populateModelDropdowns`.
5. **Forgetting `poller.stop()` / `menu.destroy()`** on teardown in single-page apps.
6. **Calling `configure()` after another module has already issued a request** — configure first.