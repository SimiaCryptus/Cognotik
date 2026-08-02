# Prompt: Build & Conform Cognotik DocOps Apps

You are working on a DocOps app located at `src/main/resources/apps/<app-name>/`.

Apply the rules below to **every** app in that directory — the one you are editing,
any app you create, and any app you touch incidentally. Nothing here is scoped to a
particular app; the examples are illustrative patterns, not an exhaustive list of
offenders. Whenever you open an app, audit it against these rules and fix what you
find, or record it as an outstanding item in that app's `README.md`.

If a standard below changes, propagate the change to all apps rather than forking
behaviour per app.

---

## 0. TL;DR

* Three files per app: **`app.html`**, **`app.js`**, **`style.css`**. Nothing else.
* All shared behaviour comes from **`/lib/app/*.js`** — never re-implement it locally.
* The **shared menubar** (`initMenu()`) owns Usage, Sessions, Git and Downloads.
  No app ships its own tabs for these.
* Modern ES modules (`import` / `const` / `async`). No IIFEs, no `var`, no frameworks.
* Mobile-first: everything must be usable at **360 px** wide.

---

## 1. Standing audit — what to look for in any app

These are recurring defects. Check for each one in **every** app you open, regardless
of whether it has been flagged before.

### 1.1 Chrome duplicated from the shared menubar

Since `initMenu()` landed, apps may still carry their own copies of global chrome.
Delete any of the following found in an app:

| Duplicated UI to remove                | Typical symptoms                                              |
|----------------------------------------|---------------------------------------------------------------|
| `💰 Usage` tab / section / totals      | imports from `/lib/app/usage.js`, cost or token tables         |
| `🔀 Git` tab / section                 | imports from `/lib/app/git.js`, status/commit/branch controls  |
| `📡 Active Sessions` / `Task Sessions` | local `renderActiveSessionsPanel()`-style helpers              |
| `📦 Download` / ZIP export tab         | hand-rolled archive links                                      |
| App switcher / home link               | duplicated navigation next to the menubar                      |

When removing, also drop the now-unused imports and dead helpers. Per-step **inline**
session links (`updateSessionLinks()`) stay — those are contextual and belong next to
the step that started the task.

### 1.2 Legacy code to modernise

Any app still using IIFE wrappers, `var`, `.then()` chains, or string-concatenated
HTML must be rewritten to the baseline in §3 the next time it is touched. Do not
extend legacy style to keep a file internally consistent — modernise instead.

### 1.3 Library-usage inconsistencies

Shared helpers must be called identically everywhere. Normalise on sight:

* `fileIO.js` — always `readFile(basePath, path)` (and the same `basePath`-first
  convention for `writeFile`, `fileExists`, `listFiles`, `deleteFile`).
* `ui.js` — prefer the `createBatchLogger(id)` factory over free functions such as
  `logBatch(id, msg, type)` / `updatePipelineNode()`; treat the free functions as
  deprecated.
* `docops.js` — always the four-argument `runDocOp(sessionId, op, target, models)`.

If you find a third calling convention, standardise it and update this list.

### 1.4 Missing basics

Verify in every app:

* `<meta name="viewport">` present in `app.html`.
* Stylesheet is named exactly `style.css` (not `app.css` or similar).
* No inline `<style>` block or `style=` attribute in `app.html` — move it to `style.css`.
* `/lib/marked.min.js` loaded before the module script.
* `initMenu({ appName })` called on boot.

---

## 2. File layout

Each app is **self-contained and buildless**:

```
apps/<app-name>/
├── app.html      # single entry point, no other HTML files
├── app.js        # single ES-module entry point
├── style.css     # all styling
├── ops/          # DocOp definitions (*.md)
└── README.md     # what the app does + pipeline overview
```

Rules:

* **No** local `utils/` directory. Shared code lives at `/lib/app/`.
* **No** vendored third-party JS. Use `/lib/*.min.js`.
* **No** build step, bundler, transpiler or `node_modules`.
* `app.js` may be split only if a module exceeds ~1500 lines, and then only
  into sibling `*.js` files in the same directory, imported from `app.js`.

---

## 3. Language & platform baseline

Target: **evergreen browsers, ES2022**.

```html
<script src="/lib/marked.min.js"></script>
<script type="module" src="app.js"></script>
```

| Do                                                    | Don't                                   |
|-------------------------------------------------------|------------------------------------------|
| `const` / `let`                                       | `var`                                    |
| ES modules with named imports                         | IIFE wrappers, globals                   |
| `async` / `await`                                     | `.then()` chains, callback pyramids      |
| Template literals                                     | `'a' + b + 'c'` HTML concatenation       |
| Optional chaining `?.`, nullish `??`                  | defensive `if (x && x.y && x.y.z)`       |
| `Array.prototype` methods, `for…of`                   | indexed `for (var i = 0; …)`             |
| `Map` / `Set` for keyed state                         | bare objects used as maps                |
| `element.replaceChildren(node)` / `<template>`        | `innerHTML +=` in loops                  |

Top-level `await` is allowed (modules are deferred by default). Do **not**
wrap `app.js` in `(function(){ 'use strict'; … })()` — modules are already
strict and scoped.

---

## 4. Shared library — `/lib/app/`

Import only what you need; never copy these into an app.

| Module            | Provides                                                                                     |
|-------------------|----------------------------------------------------------------------------------------------|
| `config.js`       | `configure()` — only needed when not mounted at host root (`window.COGNOTIK_CONFIG`)          |
| `session.js`      | `parseSessionUrl()`, `getProxyUrl()`, `getAppRoot()`                                          |
| `fileIO.js`       | `readFile()`, `writeFile()`, `fileExists()`, `listFiles()`, `deleteFile()`                    |
| `docops.js`       | `runDocOp()`, `waitForTask()`, `fetchDocopsStatus()`, `createStatusPoller()`                  |
| `models.js`       | `loadApiProviders()`, `populateModelDropdowns()`, `save/loadModelSelections()`                |
| `sessionLinks.js` | `updateSessionLinks()`, `createSessionLinkManager()`                                          |
| `ui.js`           | `renderMarkdown()`, `escapeHtml()`, `setStatus()`, `setBadge()`, `showToast()`, `createBatchLogger()`, `getFileIcon()` |
| `usage.js`        | `fetchUsageData()`, `aggregateUsage()`, `formatTokenCount()`, `formatCost()`                  |
| `git.js`          | `getStatus()`, `initRepository()`, `commit()`, `getBranches()`, `checkout()`, `getLog()`      |
| `menu.js`         | `initMenu({ appName })` — **required in every app**                                           |

Standard bootstrap:

```js
import { parseSessionUrl, getProxyUrl } from '/lib/app/session.js';
import { readFile, writeFile, fileExists } from '/lib/app/fileIO.js';
import { runDocOp, waitForTask, createStatusPoller } from '/lib/app/docops.js';
import { renderMarkdown, setStatus, setBadge, showToast } from '/lib/app/ui.js';
import { initMenu } from '/lib/app/menu.js';

const { basePath, sessionId, appId } = parseSessionUrl();
initMenu({ appName: 'My App' });
```

If an app needs behaviour that does not exist yet, add it to `/lib/app/` so every
app can use it — do not add it locally.

---

## 5. Third-party libraries

Only these are approved, and only from `/lib/`:

| Library          | Path                    | Notes                                              |
|------------------|-------------------------|-----------------------------------------------------|
| marked           | `/lib/marked.min.js`    | Load via `<script>` **before** the module; required by `renderMarkdown()` |

Adding a new library requires updating this table. No CDN links — everything
must work offline / air-gapped.

---

## 6. What the shared menubar owns

`initMenu()` renders the global chrome. **No app may duplicate:**

* Token usage / cost reporting
* Session list & live-monitor links (global view)
* Git status, commit, branch, log
* ZIP download of the session
* App switcher / home link

What apps *do* own:

* Their pipeline steps and step badges
* **Inline** per-step session links (`updateSessionLinks(target, info, getProxyUrl, containerId)`)
* Their own result viewers and editors
* Model selection **only if** the app needs per-step overrides beyond the menubar defaults

---

## 7. HTML conventions

```html
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>App Name</title>
<link rel="stylesheet" href="style.css">
</head>
<body>
<header class="app-header">…</header>
<nav class="tab-nav" role="tablist">…</nav>
<main>
  <section class="section active" id="section-x" role="tabpanel">…</section>
</main>
<script src="/lib/marked.min.js"></script>
<script type="module" src="app.js"></script>
</body>
</html>
```

* Semantic elements: `<header>`, `<nav>`, `<main>`, `<section>`, `<button>`.
* Never use `<a href="#">` as a button — use `<button type="button">`.
* Wire behaviour with `data-*` attributes, not inline `onclick`.
* Every `<input>` / `<select>` / `<textarea>` needs a `<label for>`.
* No inline `style=` attributes and no `<style>` blocks — everything in `style.css`.

---

## 8. CSS conventions

### 8.1 Design tokens

Declare tokens once at `:root`; never hard-code colours or spacing.

```css
:root {
/* colour */
--color-bg:        #14141c;
--color-surface:   #1e1e2e;
--color-border:    #2e2e42;
--color-text:      #e8e8f0;
--color-text-muted:#9a9ab0;
--color-primary:   #5b8dff;
--color-accent:    #f5a623;
--color-success:   #3ecf8e;
--color-warning:   #f0b429;
--color-danger:    #f05c5c;

/* spacing scale (4px base) */
--space-1: 0.25rem; --space-2: 0.5rem;  --space-3: 0.75rem;
--space-4: 1rem;    --space-6: 1.5rem;  --space-8: 2rem;

/* shape & type */
--radius:    8px;
--radius-sm: 4px;
--font-sans: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
--font-mono: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
}
```

Support light mode via `@media (prefers-color-scheme: light)` overriding the
same token names — never by duplicating rules.

### 8.2 Naming

* Lowercase-hyphenated, component-first: `.card`, `.card-header`, `.card-tips`.
* State classes are adjectives: `.active`, `.visible`, `.running`, `.done`, `.error`.
* No CSS-in-JS, no utility-class soup, no `!important` (except to override
  third-party styles, with a comment).

### 8.3 Layout

* Use **CSS Grid** for page/section layout, **Flexbox** for one-dimensional rows.
* Prefer `gap` over margins between siblings.
* Use logical properties (`padding-inline`, `margin-block`) where practical.
* Never set fixed pixel widths on containers — use `max-width` + `%`/`fr`.

---

## 9. Responsive / mobile standards

**Every app must be fully usable on a 360 × 640 phone.** Test at 360, 768 and 1280.

| Rule                     | Requirement                                                             |
|--------------------------|-------------------------------------------------------------------------|
| Viewport meta            | Mandatory in every `app.html`                                           |
| Tap targets              | ≥ 44 × 44 px                                                            |
| Base font size           | ≥ 16 px on inputs (prevents iOS zoom-on-focus)                          |
| Horizontal scroll        | None, ever. `overflow-x: hidden` on `body` is a bug, not a fix.         |
| Tab navigation           | Wraps or horizontally scrolls with `overflow-x: auto; scroll-snap-type` |
| Multi-column grids       | Collapse to one column below 768 px                                     |
| Tables                   | Wrap in `.table-scroll { overflow-x: auto }` or reflow to card list     |
| Fixed/sticky elements    | Must not cover content; respect `env(safe-area-inset-*)`                |
| Iframes / previews       | `width: 100%`, height via `min()` / `vh`, never fixed px                |
| Long paths / IDs         | `overflow-wrap: anywhere` or truncate with `text-overflow: ellipsis`    |

Breakpoints (mobile-first, `min-width` only):

```css
/* base = mobile */
@media (min-width: 768px)  { /* tablet  */ }
@media (min-width: 1200px) { /* desktop */ }
```

Also honour:

```css
@media (prefers-reduced-motion: reduce) {
*, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; }
}
```

---

## 10. Accessibility

* Contrast ≥ 4.5:1 for body text, ≥ 3:1 for large text and UI borders.
* Visible `:focus-visible` outline on every interactive element.
* Tabs: `role="tablist"` / `role="tab"` / `role="tabpanel"` + `aria-selected` + `aria-controls`.
* Live regions: status messages and toasts use `aria-live="polite"`; errors `aria-live="assertive"`.
* Icons that convey meaning need an accessible name (`aria-label` or visually-hidden text).
* Keyboard: Escape closes overlays/fullscreen; Enter/Space activate custom controls.
* Never rely on colour alone — pair with an icon or text label (badges do this already).

---

## 11. Standard UI patterns

### 11.1 Section + card

```html
<section class="section" id="section-pipeline">
<div class="section-header">
  <h2>⚙️ Pipeline</h2>
  <p class="section-subtitle">Short description of what happens here.</p>
</div>
<div class="card">
  <div class="card-header"><h3>Step title</h3></div>
  <p class="hint">Explain what this step does and what it produces.</p>
  …
</div>
</section>
```

### 11.2 Pipeline step

Every step has: number, title, badge, description, action row, viewer.

```html
<div class="step">
<div class="step-header">
  <span class="step-number">1</span>
  <span class="step-title">Generate Requirements</span>
  <span class="step-badge pending" id="badge-req">pending</span>
</div>
<p class="step-desc">…</p>
<div class="button-row">
  <button class="btn btn-primary btn-run"
          data-op="ops/requirements_op.md"
          data-output="requirements.md"
          data-badge="badge-req"
          data-viewer="viewer-req">▶ Run</button>
  <button class="btn btn-secondary btn-view"
          data-file="requirements.md"
          data-viewer="viewer-req">👁 View</button>
</div>
<div class="session-link-container" data-session-links="requirements.md"></div>
<div class="viewer" id="viewer-req"></div>
</div>
```

### 11.3 Badge states

`setBadge(id, state)` where state ∈ `pending | running | done | error`.
Badges must show both a colour and a word.

### 11.4 Buttons

| Class           | Use for                                  |
|-----------------|-------------------------------------------|
| `.btn-primary`  | The main action of a card                |
| `.btn-accent`   | Batch / "run everything" actions         |
| `.btn-secondary`| View, refresh, load, cancel              |
| `.btn-sm`       | Inline/compact actions inside headers    |

Disable while an async action is in flight; re-enable in `finally`.

### 11.5 Feedback

| Situation                       | Use                                  |
|---------------------------------|---------------------------------------|
| Field-level result (save, edit) | `setStatus(id, msg, 'success'\|'error')` |
| Global, transient               | `showToast(msg, type, ms)`            |
| Long-running multi-step         | `createBatchLogger('batch-log')`      |
| Blocking work                   | `.loading-overlay` (use sparingly)    |

**Never use `alert()` / `confirm()` / `prompt()`.** Use toasts and in-page
confirmation UI.

### 11.6 Empty states

Every list/viewer needs an empty state with an icon, a title and a next action:

```html
<div class="empty-state">
<div class="empty-icon">📜</div>
<p class="empty-title">Nothing generated yet</p>
<p class="empty-desc">Go to the <strong>Pipeline</strong> tab and run step 1.</p>
</div>
```

---

## 12. State, persistence & resumability

* **The filesystem is the source of truth.** UI state is derived, never authoritative.
* On load: `fetchDocopsStatus()` → restore badges → fall back to `fileExists()` checks.
* Poll with `createStatusPoller(basePath, cb, 3000)`; **stop it** when no tasks are active.
* `localStorage` only for user preferences (model choice, toggles), namespaced
  `<appId>.<key>`. Never store content there.
* Auto-save editors on a debounce (~800 ms) *and* on explicit Save.
* Cache-bust generated assets with `?t=${Date.now()}` on `<img>` / `<iframe>` / `<audio>` `src`.

---

## 13. Error handling & logging

```js
window.addEventListener('error', e => console.error('[uncaught]', e.error ?? e.message));
window.addEventListener('unhandledrejection', e => console.error('[rejection]', e.reason));
```

* Wrap every `runDocOp` / `waitForTask` in `try / catch / finally`.
* On failure: `setBadge(id, 'error')`, `showToast(msg, 'error')`, log the detail.
* Always `escapeHtml()` anything user- or model-supplied before injecting into HTML.
* Prefix console messages with the function name: `console.log('[loadNode] …')`.
* Never swallow errors silently; at minimum `console.warn`.

---

## 14. Anti-patterns

Fix these in any app, whenever you touch a file:

1. `(function(){ 'use strict'; … })()` wrappers in ES modules.
2. `var`, `function() {}` callbacks, `for (var i = 0; …)`.
3. Building DOM with `html += '<div class="' + escapeHtml(x) + '">'` in loops.
4. `alert()` / `confirm()` for user feedback.
5. Inline `style="…"` attributes and `<style>` blocks in `app.html`.
6. Hard-coded hex colours instead of tokens.
7. Duplicating Usage / Git / Sessions / Download UI already provided by the menubar.
8. Re-implementing `renderMarkdown`, `escapeHtml`, `setBadge`, etc. locally.
9. `setInterval` pollers that are never cleared.
10. Fixed pixel widths / heights that break at 360 px.
11. `overflow-x: hidden` on `body` used to hide a layout bug.
12. Magic strings for target paths scattered through the file — hoist to a `const` map.
13. Non-standard call signatures for shared helpers (see §1.3).
14. A stylesheet or entry point named anything other than `style.css` / `app.js` / `app.html`.

---

## 15. Per-app checklist

Run this against **every** app — new or existing — before considering work complete:

- [ ] Exactly `app.html`, `app.js`, `style.css`, `ops/`, `README.md`
- [ ] `<meta name="viewport">` present
- [ ] `/lib/marked.min.js` loaded before the module
- [ ] `initMenu({ appName })` called on boot
- [ ] No local Usage / Git / Sessions / Download UI
- [ ] All shared behaviour imported from `/lib/app/`, with standard signatures
- [ ] Design tokens used — zero hard-coded colours
- [ ] Usable and tested at 360 px, 768 px, 1280 px
- [ ] Tabs have `role`/`aria-*`; all inputs have labels
- [ ] Every async action disables its button and restores it in `finally`
- [ ] Every list/viewer has an empty state
- [ ] Badges restored from `docops.status.json` on reload
- [ ] Pollers stopped when idle
- [ ] `error` + `unhandledrejection` listeners registered
- [ ] `README.md` documents the pipeline and every op file

---

## 16. Reporting conformance

When you audit or modify an app, report its status against these axes so the result
is comparable across the whole `apps/` directory:

Legend: ✅ conforms · ⚠️ partial · ❌ needs work

| App             | 3-file | Modern JS | Menubar | No dup. chrome | Viewport | Mobile |
|-----------------|:------:|:---------:|:-------:|:--------------:|:--------:|:------:|
| `<app-name>`    |        |           |         |                |          |        |

Add a short note for every ⚠️ or ❌ describing the specific defect and where it lives.
Treat any app that has not yet been audited against §9 as ⚠️ for Mobile until it has
been tested at 360, 768 and 1280 px.

---

## 17. See also

* `/lib/app/migration.md` — moving an app off local `utils/`
* `apps/README.md` — app catalogue and platform overview
* Per-app `README.md` — pipeline and op-file documentation