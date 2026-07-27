## 0. One-paragraph summary

A static, build-free, **modular ES6** single-page application shipped from the module's
`resources` directory. It talks exclusively to the **FS API v1** (never to the classic HTML surface) and presents an
**IDE-like** workspace: a persistent, lazily-loaded, expandable/collapsable file tree on the left, a **tabbed editor
area** on the right, a command palette, a git panel, and a status bar. Every feature is a self-contained module
registered through a small contribution API, so new panels, editors, commands and context-menu entries can be added
without touching the shell. Accessibility (full keyboard operability, WAI-ARIA patterns, screen-reader announcements,
high-contrast + reduced-motion support) is a hard requirement, not a polish item.

---

## Table of contents

1. [Goals & non-goals](#1-goals--non-goals)
2. [Delivery: how the UI is served](#2-delivery-how-the-ui-is-served)
3. [Runtime topology](#3-runtime-topology)
4. [Resource layout](#4-resource-layout)
5. [Bootstrap sequence](#5-bootstrap-sequence)
6. [Core layer](#6-core-layer)
7. [Component model](#7-component-model)
8. [Components](#8-components)
9. [State model](#9-state-model)
10. [Key flows](#10-key-flows)
11. [Live updates](#11-live-updates)
12. [Accessibility](#12-accessibility)
13. [Keyboard map](#13-keyboard-map)
14. [Theming & design tokens](#14-theming--design-tokens)
15. [Performance budgets](#15-performance-budgets)
16. [Error handling & capability gating](#16-error-handling--capability-gating)
17. [Security](#17-security)
18. [Persistence & session restore](#18-persistence--session-restore)
19. [Extension points (actions, menus, contributions)](#19-extension-points-actions-menus-contributions)
20. [Testing strategy](#20-testing-strategy)
21. [Server-side work required](#21-server-side-work-required)
22. [Milestones](#22-milestones)
23. [Open questions](#23-open-questions)
24. [Appendix — sketches](#24-appendix--sketches)

---

## 1. Goals & non-goals

### 1.1 Goals

| #   | Goal                                                                                                                                                                                                                                           |
|-----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| G1  | **IDE-like traversal.** One persistent tree, lazy `readdir`, expand/collapse, multi-select, rename/move, drag & drop.                                                                                                                          |
| G2  | **Tabbed editing.** Multiple open files, dirty markers, preview tabs, MRU navigation, unsaved-changes guards.                                                                                                                                  |
| G3  | **Modular.** Every panel/editor/command is an ES module registered through a contribution API; the shell knows none.                                                                                                                           |
| G4  | **Accessible.** WCAG 2.2 AA; 100% keyboard operable; correct ARIA patterns; screen-reader announcements.                                                                                                                                       |
| G5  | **No build step.** Native ES modules, native CSS custom properties. `curl`-able, debuggable in DevTools as authored.                                                                                                                           |
| G6  | **FS API only.** All I/O goes through `.fsapi/v1`; the UI is a *client* of a documented contract, nothing more.                                                                                                                                |
| G7  | **Capability-driven.** Features enable/disable from `GET /meta`; a disabled capability degrades visibly, never silently.                                                                                                                       |
| G8  | **Embeddable.** Works under any mount/prefix (`/files/{session}/`), in an iframe, and offline of any CDN if required.                                                                                                                          |
| G9  | **A tool platform.** First-class *extension points* for main-menu items, file/folder context actions over **multi-selections**, and editor **text-selection** actions — expressive enough to host what are today JetBrains IDE plugin actions. |
| G10 | **Zero-JS tool authoring.** A tool declared server-side (Kotlin) renders itself in the menus, dialogs and result surfaces without shipping any client code.                                                                                    |

### 1.2 Non-goals (for v2.0)

* Not a replacement for the classic surface — the server-rendered pages stay for bookmarkability, `curl`, and no-JS
  fallback. The SPA is *additive* (API invariant A2).
* No collaborative/multi-cursor editing, no LSP, no terminal.
* No client-side VFS with `SharedArrayBuffer` sync (the `snapshot`/`sync` capabilities exist for a future
  Node-in-browser story; v2 uses plain `fetch`).
* No bundler, no TypeScript compile step, no framework (React/Vue/Svelte).
* No authentication — inherited from the deployment, exactly as today.
* **No plugin *loader*.** No manifest format, no discovery, no remote registry, no sandboxing, no versioned plugin ABI.
  This is a *library*: the embedding application registers contributions in-process at start-up (§19). "Extensible" here
  means *extension points*, not a marketplace.

---

## 2. Delivery: how the UI is served

The UI is a set of **static classpath resources** under
`fileserver/src/main/resources/webui/`, exposed by a tiny new servlet so that embedders get it for free:

```
GET /ui/                 -> webui/index.html
GET /ui/app/main.js      -> webui/app/main.js        (Content-Type: application/javascript)
GET /ui/css/tokens.css   -> webui/css/tokens.css
GET /modules/theme.js    -> existing ThemeManager (reused, unchanged)
```

Rules:

* **Immutable assets.** Requests may carry `?v=<buildId>`; when present the servlet answers
  `Cache-Control: public, max-age=31536000, immutable`. Without it,
  `Cache-Control: no-cache` + `ETag`.
* **`index.html` is never cached** (`no-store`) so a redeploy is picked up immediately.
* **No directory listing** and no `..` escapes: the servlet resolves against a fixed classpath prefix and rejects
  anything that normalises outside it.
* **Mount independence.** `index.html` contains *no* absolute API URL. The API base is discovered at runtime (§5), or
  injected by the host page:

   ```html
   <meta name="fs-api-base" content="/files/{session}/.fsapi/v1">
   <meta name="fs-classic-base" content="/files/{session}">
   ```

* **Deep links** use the hash so the servlet never has to route:
  `/ui/#/src/main.kt` (open file), `/ui/#/src/` (reveal folder),
  `/ui/#/src/main.kt:120:8` (line/column).
* **Monaco** loads from `cdn.jsdelivr.net` by default (as today) but the loader path is a single token in `config.js`; a
  deployment can point it at
  `/ui/vendor/monaco/` to run air-gapped. A load failure falls back to the
  `PlainTextEditor` (a `<textarea>` with accessible labelling) rather than an empty pane.

---

## 3. Runtime topology

```
                        browser
  ┌────────────────────────────────────────────────────────────────────┐
  │ index.html  (shell markup, skip links, <noscript> fallback)        │
  │   │                                                                │
  │   └── app/main.js  ── bootstrap ─────────────────────────────────┐  │
  │        │                                                        │  │
  │        ▼                                                        ▼  │
  │   core/ (no DOM)                                    components/ (DOM)
  │   ├── fsclient.js   ← the only place fetch() lives   ├── AppShell
  │   ├── capabilities.js                               ├── Explorer (tree)
  │   ├── store.js      (observable state)              ├── TabBar
  │   ├── bus.js        (pub/sub)                       ├── EditorArea
  │   ├── commands.js   (registry + palette source)     ├── StatusBar
│   ├── actions.js    (actions, menus, presentations) ├── MenuBar
│   ├── context.js    (DataContext + data keys)       ├── ActionDialog
  │   ├── keymap.js                                     ├── CommandPalette
  │   ├── watcher.js    (SSE → events)                  ├── GitPanel
  │   ├── errors.js     (FsError → UX)                  ├── ContextMenu
  │   ├── paths.js  persist.js  i18n.js  dom.js         └── Toasts/Dialogs
  └────────────────────────────┬───────────────────────────────────────┘
                               │ JSON / octet-stream / SSE / zip
                               ▼
                  {mount}/{prefix}/.fsapi/v1/{op}      (FilesystemServlet)
```

Hard layering rule, enforced by a lint test: **`components/` must not import
`fetch`**, and **`core/` must not import from `components/`**.

---

## 4. Resource layout

```
fileserver/src/main/resources/webui/
├── index.html
├── config.js                     # single mutable config object (monaco base, feature flags)
├── app/
│   ├── main.js                   # bootstrap: config → capabilities → shell → restore
│   ├── contributions.js          # imports every feature module (the only "wiring" file)
│   └── layout.js                 # splitters, panel visibility, zoom
├── core/
│   ├── bus.js                    # EventBus (on/off/emit, wildcard, once)
│   ├── store.js                  # createStore(initial): get/set/select/subscribe
│   ├── fsclient.js               # FS API v1 client
│   ├── capabilities.js           # /meta cache + has()/require()
│   ├── watcher.js                # SSE watch, poll fallback, coalescing
│   ├── commands.js               # register/execute/when-clauses
│   ├── actions.js                # action registry, menu anchors, groups, presentations
│   ├── context.js                # DataContext: data keys + provider chain (§19.3)
│   ├── keymap.js                 # chords → commands, platform normalisation
│   ├── errors.js                 # FsError class + code → message/severity table
│   ├── paths.js                  # join/normalise/basename/dirname/relative/segments
│   ├── mime.js                   # extension → language id / viewer kind
│   ├── persist.js                # namespaced localStorage with schema version
│   ├── i18n.js                   # t('key', {..}) with en fallback bundle
│   ├── a11y.js                   # live-region announce(), focus trap, roving tabindex
│   └── dom.js                    # h(), text(), classes(), delegate(), raf batching
├── components/
│   ├── base.js                   # Component base class (mount/update/destroy)
│   ├── AppShell.js
│   ├── ActivityBar.js
│   ├── explorer/
│   │   ├── Explorer.js           # panel: toolbar + tree + filter box
│   │   ├── TreeModel.js          # pure data model (no DOM), unit-testable
│   │   ├── TreeView.js           # virtualised renderer, ARIA tree pattern
│   │   ├── TreeItem.js
│   │   ├── InlineRename.js
│   │   └── DropTarget.js         # drag & drop + external file upload
│   ├── tabs/
│   │   ├── TabBar.js             # ARIA tablist, overflow menu, drag reorder
│   │   └── TabModel.js
│   ├── editors/
│   │   ├── EditorArea.js         # tabpanel host, editor lifecycle, focus routing
│   │   ├── EditorRegistry.js     # matcher → editor factory (priority ordered)
│   │   ├── MonacoEditor.js
│   │   ├── PlainTextEditor.js    # fallback / tiny files / no-Monaco mode
│   │   ├── MarkdownPreview.js    # split preview, sanitised
│   │   ├── ImageViewer.js
│   │   ├── DiffEditor.js         # git diff, external-change conflicts
│   │   └── BinaryPlaceholder.js  # size/mime + download button
│   ├── panels/
│   │   ├── GitPanel.js
│   │   ├── SearchPanel.js
│   │   ├── ProblemsPanel.js      # (optional) surfaced errors/notifications log
│   │   └── PropertiesPanel.js    # stat details for the selection
│   ├── overlays/
│   │   ├── CommandPalette.js
│   │   ├── QuickOpen.js
│   │   ├── ActionDialog.js       # declarative parameter form for actions (§19.7)
│   │   ├── ActionsForSelection.js# `Alt+Enter` "available actions" quick pick
│   │   ├── ContextMenu.js
│   │   ├── Modal.js              # focus-trapped <dialog>
│   │   └── Toasts.js             # aria-live region
│   ├── MenuBar.js                # main menu, built from `main/*` anchors (§8.9)
│   ├── StatusBar.js
│   └── Breadcrumbs.js
├── css/
│   ├── tokens.css                # design tokens (light/dark/hc), one source of truth
│   ├── base.css                  # reset, focus-visible, sr-only, reduced motion
│   ├── layout.css                # grid shell, splitters
│   └── components/*.css          # one file per component, imported by index.html
├── ext/                          # host-registered client contributions (empty by default)
├── i18n/en.json
└── vendor/                       # optional, empty by default (air-gapped Monaco)
```

Every file is < ~400 lines by convention; anything larger is split.

---

## 5. Bootstrap sequence

1. `index.html` renders the **static shell skeleton** (skip link, `<header>`, `<nav>`,
   `<main>`, `<footer>`, and a `<noscript>` block linking to the classic listing).
2. `main.js` resolves the **API base**:
    1. `<meta name="fs-api-base">` if present;
    2. else `?api=` query parameter;
    3. else derived from `location.pathname` by replacing a trailing `/ui/…` with
       `/files/root/.fsapi/v1` **only** when `document.baseURI` matches the CLI layout;
    4. else prompt once and remember in `persist`.
3. `GET {base}/meta` → `capabilities.init()`. On failure: render a full-page, accessible error card with the raw
   `X-Fs-Error` code and a *Retry* button.
4. `GET {base}/actions` → two jobs: (a) verify the client's assumptions in dev mode and auto-hide commands whose op is
   missing (forward compatibility); (b) ingest the **server-declared action descriptors** (§19.11) and register each as
   a first-class action, so a host-registered Kotlin tool appears in the main menu, the file context menu and the editor
   selection menu with no JavaScript. Cached by `actions.etag`
   from `/meta`; a mismatch refetches.
5. `contributions.js` registers built-in commands, editors, panels and menu items, then imports `ext/*` — the host's
   client-side contributions (§19.12).
6. `ThemeManager.init()` (reused `/modules/theme.js`), then `AppShell.mount(document.body)`.
7. `persist.restore()` → sidebar width, expanded folder set, open tabs, active tab, scroll/cursor positions — then the
   URL hash **overrides** the active tab if present.
8. `watcher.start('/', {recursive:true})` when `capabilities.watch === 'sse'`.
9. First paint target: shell + tree root visible in **≤ 400 ms** on localhost.

---

## 6. Core layer

### 6.1 `fsclient.js`

A thin, complete, promise-based mirror of [api.md §4](./api.md#4-fs-api-v1). One function per operation, no clever
abstractions:

```js
export function createFsClient({base, fetchImpl = fetch}) {
    // every mutating call adds `X-Fs-Api: 1` (CSRF mitigation, api.md §4.1)
    return {
        meta(), actions(),
        stat(path, {lstat, throwIfNoEntry} = {}),
        statBatch(paths, {lstat} = {}),          // POST /stat
        readdir(path, {recursive, depth, stat} = {}),
        readFile(path, {range, ifNoneMatch, ifMatch, signal} = {}),
        // -> {status, etag, mtimeMs, size, mimeType, bytes|null}
        readText(path, opts),                    // decode utf-8, tracks etag
        writeFile(path, bytes, {flag, position, ifMatch, ifNoneMatch} = {}),
        mkdir(path, {recursive = true} = {}),
        rm(path, {recursive, force} = {}),
        rename(from, to, {overwrite} = {}),
        copy(from, to, {recursive, force, preserveTimestamps} = {}),
        truncate(path, len), utimes(path, {atimeMs, mtimeMs}),
        realpath(path), resolve(request, from),
        snapshotUrl(path, {maxBytes} = {}),      // href for <a download>
        watch(path, {recursive} = {}),           // -> EventSource wrapper
        batch(ops, {stopOnError} = {}),
        exec(cmd, args, {cwd, signal} = {}),
        git(action, params = {}),
    };
}
```

Responsibilities beyond plumbing:

* **Error normalisation.** Any non-2xx JSON `{error:{code,errno,syscall,path,message}}`
  becomes `throw new FsError(...)`; network/parse failures become
  `FsError('ENETWORK')`. Callers never inspect `response.status`.
* **ETag bookkeeping.** `readText`/`readFile` return the `ETag`; `writeFile` accepts
  `ifMatch`. The client itself keeps **no hidden cache** — the tab model owns the etag.
* **Request de-duplication.** Identical in-flight `GET stat`/`dir` requests share a promise (keyed by URL) to make
  "reveal file" and "expand folder" cheap.
* **Cancellation.** Every read accepts an `AbortSignal`; closing a tab or collapsing a folder aborts its in-flight
  reads.
* **Batching helper.** `client.batch()` is used by the tree to `stat` a whole expanded level in one round trip, and by
  "delete selection" to issue N `rm`s at once (respecting `limits.maxBatchOps` by chunking).

### 6.2 `capabilities.js`

Caches `/meta` and exposes:

```js
caps.limits.maxFileSize          // numbers used for guards, not guesses
caps.readOnly                    // -> global read-only banner + disabled commands
caps.has('watch')                // 'sse' | 'poll' | 'none'
caps.has('git') / 'snapshot' / 'resolve' / 'utimes' / 'exec'
caps.require('snapshot')         // throws FsError('ENOSYS') with a UX-ready message
```

Every command declares `requires: ['git']`; the palette and menus **hide or disable with an explanatory tooltip** rather
than failing at click time (G7).

### 6.3 `store.js` + `bus.js`

* `store` is a single frozen-ish object tree with `select(selector, cb)` subscriptions, compared by reference. No
  proxies, no magic — mutations happen through small reducer-ish functions in the owning module.
* `bus` carries **facts, not commands**: `fs:changed`, `tab:opened`, `tab:dirty`,
  `selection:changed`, `caps:ready`, `error:raised`. Components subscribe; they never reach into each other.

### 6.4 `commands.js`

```js
registerCommand({
    id: 'file.save',
    title: 'File: Save',
    keys: ['Mod+S'],
    when: ctx => ctx.activeTab?.dirty && !caps.readOnly,
    requires: [],
    run: async ctx => { ...
    },
});
```

The registry is the **single source of truth** for the command palette, the keymap, the context menus and the toolbar
buttons — so a new feature gets keyboard access, discoverability and accessible labelling for free.
`actions.js` (§19) builds on top: an **action** is a command *plus* a presentation (`update()`), a selection contract
and one or more menu anchors. Registering an action registers a command, so every contributed tool inherits the palette,
remappable keybindings, `when`/`requires` gating and accessible naming automatically. The reverse does not hold — a
plain command has no menu placement and no data context.

### 6.5 `errors.js`

`FsError` carries `code`, `errno`, `syscall`, `path`, `message`, plus a UX mapping (§16). `raise(err, {context})` emits
`error:raised`, which the `Toasts` component renders and `ProblemsPanel` logs.

---

## 7. Component model

No framework. A 60-line base class:

```js
export class Component {
    constructor(props = {}) {
        this.props = props;
        this.el = null;
        this._subs = [];
    }

    mount(parent) {
        this.el = this.render();
        parent.appendChild(this.el);
        this.mounted?.();
        return this.el;
    }

    render() {
        throw new Error('render() required');
    }   // returns a DOM node
    track(unsubscribe) {
        this._subs.push(unsubscribe);
    } // store/bus/DOM listeners
    update(next) { /* opt-in, component-specific, surgical DOM patching */
    }

    destroy() {
        this._subs.forEach(u => u());
        this.el?.remove();
        this.el = null;
    }
}
```

Conventions:

* **`h()` not innerHTML.** `dom.js` exposes `h('div', {class, role, 'aria-level':2}, children)`. User-controlled strings
  only ever reach the DOM through `textContent`
  (kills the escaping bugs the current string-concatenated renderer is prone to).
* **Surgical updates.** Components diff their own small models; the tree and tab bar reuse row/tab nodes and mutate only
  changed attributes.
* **`raf` batching.** `dom.schedule(fn)` coalesces layout-affecting writes.
* **Custom elements only where the platform helps** (`<fs-splitter>`, `<fs-resizer>`); everything else is a plain class
  so it stays trivially unit-testable in jsdom.
* **Every component owns one CSS file** and uses a `.fs-<name>__<part>` class prefix. No global selectors, no
  `!important`.

---

## 8. Components

### 8.1 AppShell

CSS-grid layout with named areas and keyboard-resizable splitters:

```
┌───────────────────────────────────────────────────────────────┐
│ header  (breadcrumbs · toolbar · theme · read-only badge)      │
├──────┬──────────────────────┬─────────────────────────────────┤
│ act. │ sidebar              │ editor area                     │
│ bar  │ (Explorer/Git/Search)│ TabBar + tabpanel               │
│      │                      ├─────────────────────────────────┤
│      │                      │ bottom panel (Problems/Preview) │
├──────┴──────────────────────┴─────────────────────────────────┤
│ status bar (path · encoding · line:col · watcher · progress)   │
└───────────────────────────────────────────────────────────────┘
```

* Landmarks: `header[role=banner]`, `nav[aria-label="Activity"]`,
  `aside[aria-label="Explorer"]`, `main`, `footer[role=contentinfo]`.
* Splitters are `role="separator"` with `aria-orientation`, `aria-valuenow`,
  `tabindex="0"`, arrow-key resize (±16 px, ±64 px with Shift), `Home`/`End` to min/max, `Enter` to collapse/restore.
* `Mod+B` toggles the sidebar; `Mod+J` the bottom panel. Both are commands, both announce their new state.
* Layout is fully responsive: below 720 px the sidebar becomes a modal drawer (focus-trapped, `Escape` closes, focus
  returns to its toggle).

### 8.2 Explorer — tree

**Model (`TreeModel.js`, pure).** Nodes keyed by virtual path:

```js
{
    path:'/src', name
:
    'src', type
:
    'dir', level
:
    1,
        state
:
    'collapsed' | 'loading' | 'expanded' | 'error',
        childPaths
:
    [...] | null, size, mtimeMs, readOnly, mimeType
}
```

* **Lazy loading.** Expanding a folder issues `GET /dir?path=…&stat=true&depth=1`. Results are name-sorted with
  **folders first**, then a natural (numeric-aware)
  comparator; hidden entries are already excluded server-side.
* **Prefetch.** On hover/focus of a collapsed folder, prefetch after 250 ms (cancelled on blur) so expansion feels
  instant.
* **Virtualisation.** The flattened visible list is windowed once it exceeds **500 rows** (fixed 22 px row height ⇒
  trivial math, no measurement). Below that threshold everything is rendered so `Ctrl+F` browser find still works.
* **Truncation.** `truncated: true` from `/dir` renders a non-focus-stealing
  "Showing first N of many — refine with the filter" row.
* **Filter box** (`Explorer` toolbar) filters the *loaded* subtree by substring/fuzzy match, auto-expanding matches and
  announcing `"12 matches"` on a debounce.
* **Multi-select.** `Ctrl/Cmd+Click` toggle, `Shift+Click` range, `Ctrl+A` select visible siblings. Selection drives the
  context menu and `PropertiesPanel`.
* **Inline rename** (`F2`): the label becomes an `<input>` with
  `aria-label="New name for src"`; validation mirrors the server's rules (no `\ : ~`, no control chars, no `.fsapi`, no
  trailing dot/space) *before* the
  `POST /rename`, and surfaces `EEXIST` inline rather than as a toast.
* **Drag & drop.** Internal drag ⇒ `rename` (move) with `overwrite:false`;
  `Alt`/`Option` ⇒ `copy`. External OS files ⇒ `PUT /file` per file with a progress toast, `EEXIST` prompting
  *Overwrite / Keep both / Skip / Skip all*. Drag is fully mirrored by *Cut/Copy/Paste* commands so it is never the only
  path (a11y).
* **Read-only affordances.** Nodes with `readOnly:true` get a lock icon **and**
  `aria-describedby` pointing at a "read-only" hint; destructive commands are disabled.
* **Root actions.** New File, New Folder, Refresh, Collapse All, Download ZIP (`snapshot`, capability-gated), Reveal in
  classic view.

### 8.3 TabBar + EditorArea

* `TabBar` is a real `role="tablist"` with `aria-orientation="horizontal"`, roving
  `tabindex`, `Home/End`, `Delete` to close, and drag-to-reorder mirrored by
  `Mod+Shift+PageUp/PageDown` commands.
* **Preview tabs.** Single-click (or arrow-navigation) in the tree opens a *preview*
  tab (italic title) that is replaced by the next preview. Double-click, `Enter`, or any edit **pins** it. Pinned tabs
  never get replaced.
* **Tab state** per tab: `{path, etag, mtimeMs, dirty, viewState, editorKind, readOnly}`.
* **Dirty indicator** is `●` *plus* `aria-label="src/main.kt, unsaved changes"` — never colour or glyph alone.
* **Close semantics.** Dirty close ⇒ focus-trapped modal *Save / Don't save / Cancel*.
  `beforeunload` guards the window when any tab is dirty.
* **Overflow.** Beyond the available width, tabs collapse into an accessible
  "Open editors" menu (also reachable as `Mod+P` → *Open editors* group).
* **EditorArea** hosts exactly one `role="tabpanel"` per tab, labelled by its tab, and routes focus into the editor on
  activation.

### 8.4 Editors (registry-driven)

`EditorRegistry.register({id, priority, canOpen(stat), create(ctx)})`:

| Editor              | Matches                                     | Notes                                                             |
|---------------------|---------------------------------------------|-------------------------------------------------------------------|
| `MonacoEditor`      | text mime / known extension, `size ≤ 8 MiB` | language from `mime.js`, `Alt+F1` a11y help, `Mod+S` save         |
| `PlainTextEditor`   | fallback when Monaco fails or user opts out | `<textarea>` + label, no syntax highlighting                      |
| `MarkdownPreview`   | `*.md` (split or dedicated tab)             | sanitised render; **client-side**, never the server's `.md→.html` |
| `ImageViewer`       | `image/*`                                   | zoom/fit, alt text = filename, keyboard zoom                      |
| `DiffEditor`        | git diff, external-change conflict          | `aria-label`ed panes, "next/previous change" commands             |
| `BinaryPlaceholder` | NUL sniffed / unknown binary / oversized    | shows `stat` facts + Download; explains *why* it cannot be edited |

**Open guard rails** (all derived from `/meta`, never hard-coded):
files above `limits.maxFileSize` are refused with an explicit message; files above 2 MiB open read-only-by-default with
a "Edit anyway" affordance; binary detection reuses the server's rule (NUL byte in the first 8 KiB of a
`Range: bytes=0-8191` read), so the UI never has to download a 500 MB blob to find out.

**Saving** uses optimistic concurrency exactly as [api.md §4.10](./api.md#410-put-file--write):
`PUT /file?path=…` with `If-Match: <etag>`. `EBUSY` ⇒ conflict dialog offering *Compare (DiffEditor)*, *Overwrite (retry
without `If-Match`)*, *Reload*, *Cancel*.

### 8.5 Overlays

* **CommandPalette** (`Mod+Shift+P`): fuzzy over the command registry;
  `role="combobox"` + `role="listbox"`, `aria-activedescendant`, results announced.
* **QuickOpen** (`Mod+P`): fuzzy file finder primed by one
  `GET /dir?recursive=true&depth=<caps.maxDepth>&stat=false`, refreshed on `overflow`
  events, capped by `limits.maxDirEntries` with a visible truncation notice.
* **ContextMenu**: `role="menu"`, opened by right-click **and** `Shift+F10`/`ContextMenu`
  key, arrow navigation, type-ahead, `Escape` restores focus to the invoker. Its content is *never* hard-coded: it is
  the action registry filtered by anchor and by the DataContext of the invocation (§19), rendered as ordered groups
  separated by
  `role="separator"`, with submenus (`aria-haspopup="menu"`) up to depth 3 and disabled items carrying a`disabledReason`
  via `aria-describedby`.
* **ActionDialog**: a focus-trapped `<dialog>` containing a real `<form>` generated from an action's declared parameter
  schema (§19.7) — labels, help text, inline validation,
  `Enter` to submit, remembered values per action.
* **Actions for selection** (`Alt+Enter`): a quick pick of *every enabled action* for the current context (tree
  multi-selection, tab, or editor selection), grouped and searchable — the JetBrains "show intentions / find action for
  this thing" affordance, and the keyboard-only equivalent of the context menu.
* **Modal**: native `<dialog>` with focus trap, `aria-labelledby`/`aria-describedby`,
  `Escape` cancel, initial focus on the least destructive action.
* **Toasts**: `aria-live="polite"` (`assertive` for errors), auto-dismiss ≥ 6 s, pause on hover/focus, never the *only*
  channel for an error (also in `ProblemsPanel`).

### 8.6 GitPanel

Thin client over `POST /.fsapi/v1/git` ([api.md §5](./api.md#5-git-actions)); the panel **self-describes** from the
`describe` action / `GET /actions`, so a `GitAction`
registered downstream appears automatically as a button with its declared parameters.

* Status list (staged / unstaged / untracked) with per-file *Stage*, *Unstage*, *Discard* (confirmed), *Open diff*.
* Commit box with `Mod+Enter` submit, message length hint, amend checkbox if exposed.
* Branch switcher (`branches`, `create-branch`, `switch-branch`, `delete-branch`), with a force-delete confirmation
  path.
* Log list with `count` paging.
* If `isGitRepository` is false: a single *Initialize Git Repository* action.
* Entire panel is hidden when `caps.has('git')` is false.

### 8.7 SearchPanel

Two tiers, chosen by capability:

1. `exec` allowlists `git` ⇒ `git grep -n --untracked` for repo-backed roots (fast).
2. Otherwise a **client-side scan**: recursive `dir`, then bounded-concurrency (6) ranged reads with early binary
   rejection, streaming results as they arrive, cancellable, with a hard cap and a "searched N of M files" status.

Results are a flat, keyboard-navigable list (`role="list"`), each row opening the file at `line:col` via the
hash-deep-link machinery.

### 8.8 StatusBar

Path of the active tab · read-only/lock · cursor `Ln, Col` · selection count · encoding · EOL · language · watcher
indicator (`live` / `polling` / `off`) · background-task progress. Each cell is a button where it is actionable, and
each has a text label — never an icon alone.

### 8.9 MenuBar & action surfaces

A single `MenuBar` component renders the `main/*` anchors (§19.1) as a WAI-ARIA **menubar**: `role="menubar"` with
`role="menuitem"` / `menuitemcheckbox` children, roving `tabindex`, `←/→` between top-level menus, `↑/↓` within a menu,
type-ahead,
`Alt`/`F10` to focus it, `Escape` to close and restore focus. Top-level menus are *File · Edit · Selection · View · Go ·
Tools · Help*; `Tools` is where contributions land by default. All four menu surfaces — menubar, explorer context menu,
tab context menu and editor context menu — are the *same renderer* over the *same registry*, differing only in the
anchor they query and the DataContext they build. Consequences:

* one accessibility implementation to audit, not four;
* an action contributed to three anchors is written once;
* on narrow viewports the menubar collapses into a single **⋯ Menu** button (`aria-haspopup="menu"`) with the same tree
  of items;
* menus render from cached presentations in **≤ 50 ms**; anything slower is a bug in a contributed `update()` (§19.5),
  and the offending action is reported by id.

---

## 9. State model

```js
store = {
    caps,                                        // frozen after /meta
    base: '/files/root/.fsapi/v1',
    tree: {nodes: Map < path, Node >, expanded: Set < path >, selection: [paths], focus: path
},
    tabs
:
{
    order: [id], byId
:
    Map < id, Tab >, active
:
    id, preview
:
    id | null
}
,
panels: {
    sidebar: 'explorer' | 'git' | 'search' | null, bottom
:
    null, widths
:
    {...
    }
}
,
actions: {
    byId: Map < id, Action >, byAnchor
:
    Map < anchor, [placement] >,
        descriptorsEtag, presentations
:
    Map < id, Presentation >,
        running
:
    Map < invocationId, taskId >
}
,
context: {
    origin, anchor, resources
:
    [...], editorSelection, truncated
}
,
watcher: {
    state: 'live' | 'polling' | 'off', lastEventAt
}
,
tasks: [{id, label, progress, cancel}],    // uploads, searches, snapshots
    notifications
:
[{id, severity, message, code, actions}],
}
```

Invariants:

* **Paths are the identity** for tree nodes; **tab ids** are opaque (a file can be open in two editors, e.g. source +
  preview).
* The tree model is **append-only optimistic**: a mutation applies locally, then is confirmed or reverted by the
  response *and* by the subsequent `watch` event.
* The **DataContext is snapshotted at invocation time** (as in JetBrains): a running action never re-reads the live
  selection, so a `watch` refresh mid-run cannot silently retarget it. Paths that vanish mid-run surface as per-item
  `ENOENT`.
* Nothing derived is stored: breadcrumbs, dirty counts and titles are selectors.

---

## 10. Key flows

### 10.1 Open a file

```
tree row activated
   → tabs.openPreview(path)
   → stat(path)                              # size, mimeType, readOnly, etag
   → EditorRegistry.pick(stat)
   → readFile(path, {signal})                # ranged sniff first if size > 1 MiB
   → editor.setValue(text, {readOnly: stat.readOnly || caps.readOnly})
   → announce("Opened src/main.kt, 1234 bytes, read-only")
   → history.replaceState hash = #/src/main.kt
```

### 10.2 Save (optimistic concurrency)

```
Mod+S → command file.save
   → PUT /file?path=… , If-Match: tab.etag, X-Fs-Api: 1
   → 200/201 : tab.etag = ETag; dirty = false; status "Saved"
   → EBUSY   : conflict modal (Compare / Overwrite / Reload / Cancel)
   → EROFS   : "Server is read-only" + switch tab to read-only mode
   → EACCES  : "This file is marked read-only (.readonly)"
   → EFBIG   : "File exceeds the server limit of 50 MiB"
```

### 10.3 Delete a selection

Confirmation modal names the items (and counts descendants for folders) ⇒ one `POST /batch` of `rm {recursive:true}`
chunked to `limits.maxBatchOps` ⇒ per-item results reconcile the tree; partial failure lists the failures in
`ProblemsPanel` and leaves the survivors in place.

### 10.4 Create / upload

New File ⇒ inline "new row" input in the tree ⇒ `PUT /file` with
`If-None-Match: *` (so a race yields `EEXIST`, not a clobber) ⇒ open the new tab. Upload ⇒ per-file `PUT` with an
`XMLHttpRequest`-based progress task (fetch has no upload progress), respecting `limits.maxFileSize` **client-side
first**.

### 10.5 Invoke a tool action on a multi-selection

```
right-click on tree selection (2 dirs + 3 files)   # or Shift+F10 / Alt+Enter
    → context.build({origin:'explorer', anchor:'explorer/context'})
    → actions.forAnchor('explorer/context').map(a => a.update(ctx))   # sync, < 1 ms each
    → ContextMenu renders enabled/disabled groups (disabled items say why)
    → activate "Generate tests…"
    → params declared ⇒ ActionDialog (validated, remembered) ⇒ params
    → POST /.fsapi/v1/action/tests.generate  {context, params}   X-Fs-Api: 1
    → {invocationId} ⇒ task in status bar, Cancel wired to DELETE /action/{id}
    → SSE progress ("3 of 12 files") announced politely on a 2 s throttle
    → result {kind:'patch'} ⇒ preview list with per-file checkboxes
    → Apply ⇒ POST /batch of PUTs with If-Match ⇒ tree + open tabs reconcile
    → announce("Generated tests for 5 items, 12 files changed")
```

### 10.6 Act on an editor text selection

```
select 40 lines in MonacoEditor → Alt+Enter (or right-click)
    → context.build({origin:'editor', anchor:'editor/selection'})
       ctx.editorSelection = {path, languageId, ranges[], text, before, after}
    → "Explain / Refactor / Extract…" from the same registry
    → run ⇒ result {kind:'edits', edits:[...], undoLabel:'Refactor selection'}
    → editor.applyEdits(...) between pushStackElement() calls  # one Mod+Z undoes it all
    → tab becomes dirty (never written behind the user's back); Mod+S saves with If-Match
```

A `{kind:'diff'}` result instead opens a `DiffEditor` tab with **Apply / Discard**, and
`{kind:'document'}` opens a read-only `VirtualDocument` tab (explanations, reports).

---

## 11. Live updates

* `watcher.js` opens `GET /watch?path=/&recursive=true` (SSE) when
  `caps.watch === 'sse'`; `EMFILE` ⇒ fall back to polling with a visible status change.
* Events are **coalesced** on a 100 ms trailing window and applied per directory:
  only *loaded* directories are refreshed (`readdir` of the parent), so a busy build directory that nobody expanded
  costs nothing.
* `overflow` ⇒ invalidate everything loaded and refresh the visible window; QuickOpen's index is marked stale.
* **Editor reconciliation.** A `change` for an open path re-`stat`s it:
    * clean tab ⇒ silently reload, preserve view state, announce "Reloaded from disk";
    * dirty tab ⇒ non-modal banner *"Changed on disk — Compare / Reload / Keep mine"*.
* Heartbeats keep the status bar's `live` indicator honest; 45 s of silence downgrades it and attempts a reconnect with
  exponential backoff (1 s → 30 s, jittered).
* `watch` failures never break the UI — a manual *Refresh* is always available and the indicator says why
  (`off (ENOSYS)`).

---

## 12. Accessibility

Target: **WCAG 2.2 level AA**, plus the WAI-ARIA Authoring Practices patterns.

### 12.1 Patterns

| UI              | Pattern / roles                                                                                                                      |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------|
| File tree       | `role="tree"` + `treeitem`/`group`, `aria-expanded`, `aria-level`, `aria-selected`, `aria-multiselectable`, single roving `tabindex` |
| Tabs            | `role="tablist"`/`tab`/`tabpanel`, `aria-selected`, `aria-controls`/`aria-labelledby`                                                |
| Activity bar    | `role="tablist"` (vertical) or plain toggle buttons with `aria-pressed`                                                              |
| Command palette | `role="combobox"` + `listbox`/`option`, `aria-activedescendant`, `aria-expanded`                                                     |
| Context menu    | `role="menu"`/`menuitem`, `aria-haspopup`, type-ahead                                                                                |
| Dialogs         | native `<dialog>`, `aria-modal`, labelled, focus trap, focus restoration                                                             |
| Splitters       | `role="separator"`, `aria-valuemin/now/max`, `aria-controls`, keyboard resize                                                        |
| Toasts / status | `aria-live` regions (`polite` / `assertive`), `role="status"` for the status bar                                                     |
| Progress        | `role="progressbar"` with `aria-valuenow` + a text alternative                                                                       |

### 12.2 Non-negotiables

* **Every** action reachable by keyboard, with a discoverable shortcut in the palette.
* Visible focus indicator everywhere (`:focus-visible`, ≥ 2 px, ≥ 3:1 contrast against both the component and the
  adjacent background).
* No information conveyed by colour alone (dirty, git status, read-only, errors all carry text/glyph + accessible name).
* Contrast ≥ 4.5:1 for text, ≥ 3:1 for UI boundaries, in **all** themes; a dedicated
  `data-theme="hc"` high-contrast theme and `prefers-contrast: more` support.
* `prefers-reduced-motion: reduce` ⇒ no transitions/animations, instant panel toggles.
* Usable at **200 % zoom** and at 320 px logical width without horizontal scrolling of the shell (the editor may
  scroll).
* Screen-reader announcements via `a11y.announce()` for asynchronous outcomes:
  expanded/collapsed with child counts, saved, renamed, deleted, N results, errors.
* Skip links: *Skip to explorer*, *Skip to editor*.
* Accessible names on every icon-only control; tooltips are never the only label.
* **Contributed items are held to the same bar.** A text label is mandatory (icon-only registrations are rejected,
  §19.13); disabled items expose *why* via `disabledReason`
  → `aria-describedby`; every menu item is also reachable from the palette and from
  `Alt+Enter`, so no tool is mouse-only; long-running actions report progress through
  `role="progressbar"` plus throttled polite announcements.
* Monaco is initialised with `accessibilitySupport: 'auto'`, `ariaLabel` = file path, and the `Alt+F1` help hint is
  surfaced in the status bar when a screen reader is detected; `PlainTextEditor` is offered as an explicit user
  preference.
* Text spacing overrides (WCAG 1.4.12) must not clip content — no fixed-height rows with clipped text except the
  virtualised tree, which uses `em`-based row height.
* `lang` attribute set; all strings routed through `i18n` (single `en` bundle for v2, RTL-ready via logical CSS
  properties only).

---

## 13. Keyboard map

`Mod` = `Ctrl` on Windows/Linux, `Cmd` on macOS. Everything below is a command, so it is remappable and listed in the
palette. Browser-reserved chords are avoided.

| Chord                       | Command                                   |
|-----------------------------|-------------------------------------------|
| `Mod+P`                     | Quick open file                           |
| `Mod+Shift+P` / `F1`        | Command palette                           |
| `Mod+Shift+A`               | Find action (palette, actions only)       |
| `Alt+Enter`                 | Available actions for the current context |
| `F10` / `Alt`               | Focus the main menu bar                   |
| `Mod+S`                     | Save                                      |
| `Mod+Alt+S`                 | Save all                                  |
| `Alt+W`                     | Close tab (`Mod+W` is browser-reserved)   |
| `Mod+Alt+←` / `→`           | Previous / next tab                       |
| `Mod+B`                     | Toggle sidebar                            |
| `Mod+J`                     | Toggle bottom panel                       |
| `Mod+Shift+E`               | Focus explorer                            |
| `Mod+Shift+G`               | Focus git panel                           |
| `Mod+Shift+F`               | Search in files                           |
| `Mod+Shift+R`               | Refresh tree                              |
| `Alt+↑` / `Alt+↓`           | Move focus between tree and editor        |
| `F2`                        | Rename (tree)                             |
| `Delete`                    | Delete selection (tree, confirmed)        |
| `Mod+X` / `Mod+C` / `Mod+V` | Cut / copy / paste in tree (move / copy)  |
| `Shift+F10` / `ContextMenu` | Open context menu                         |
| `Escape`                    | Close overlay / cancel inline edit        |
| Tree: `↑ ↓`                 | Move focus                                |
| Tree: `→`                   | Expand, or focus first child              |
| Tree: `←`                   | Collapse, or focus parent                 |
| Tree: `Home` / `End`        | First / last visible node                 |
| Tree: `Enter`               | Open (pinned tab)                         |
| Tree: `Space`               | Toggle selection / preview                |
| Tree: `*`                   | Expand all siblings                       |
| Tree: type letters          | Type-ahead to node                        |

---

## 14. Theming & design tokens

* Single source of truth: `css/tokens.css`, one `:root` block per theme (`[data-theme=light|dark|hc]`) plus
  `@media (prefers-color-scheme: dark)` for `auto`.
* Tokens are semantic, not literal:
  `--fs-bg`, `--fs-bg-elevated`, `--fs-fg`, `--fs-fg-muted`, `--fs-accent`,
  `--fs-border`, `--fs-focus-ring`, `--fs-danger`, `--fs-warn`, `--fs-ok`,
  `--fs-row-h`, `--fs-space-1..6`, `--fs-radius`, `--fs-font-ui`, `--fs-font-mono`,
  `--fs-z-overlay`, `--fs-motion-fast`.
* Monaco's theme is derived from the same tokens (`defineTheme` from computed styles)
  so the editor never fights the shell.
* `ThemeManager` from `/modules/theme.js` is reused verbatim (`init()`,
  `bindSelector()`), keeping the SPA and the classic pages in sync via the same persisted preference.
* Density toggle (`comfortable` / `compact`) changes `--fs-row-h` only, respecting the WCAG 2.2 target-size minimums in
  `comfortable`.

---

## 15. Performance budgets

| Budget                                    | Target                       |
|-------------------------------------------|------------------------------|
| Initial transfer (shell, no Monaco), gzip | ≤ 60 KB JS + ≤ 20 KB CSS     |
| Module count on first paint               | ≤ 25                         |
| Time to interactive tree (localhost)      | ≤ 400 ms                     |
| Expand a 1 000-entry folder               | ≤ 1 request, ≤ 100 ms render |
| Tree scroll                               | 60 fps at 100 000 nodes      |
| Requests per folder expansion             | exactly 1                    |

Techniques: lazy `import()` for Monaco/GitPanel/SearchPanel/DiffEditor, one `readdir`
per expansion (`stat=true` inline), `POST /batch` for fan-out, request de-duplication,
`AbortController` everywhere, virtualised rows, `content-visibility: auto` on panels,
`ETag`-conditional reloads, and a `snapshot` (ZIP) fast path for priming QuickOpen on very large trees when the
capability is present.

---

## 16. Error handling & capability gating

Every FS API code has exactly one UX rule — no generic "something went wrong":

| Code                 | Severity | UX                                                                                                                                                       |
|----------------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ENOENT`             | info     | Remove node from tree / close tab with "No longer exists"; refresh parent                                                                                |
| `EACCES`             | warn     | "Read-only (marker file) / not permitted" + lock the affected UI                                                                                         |
| `EROFS`              | warn     | Global read-only banner; all mutating commands disabled with a reason                                                                                    |
| `EEXIST`             | prompt   | Overwrite / Keep both / Rename / Cancel                                                                                                                  |
| `EISDIR` / `ENOTDIR` | error    | Internal-ish: log to Problems, show precise message (a client bug)                                                                                       |
| `ENOTEMPTY`          | prompt   | "Folder is not empty — delete recursively?"                                                                                                              |
| `EINVAL`             | error    | Inline validation message on the offending field                                                                                                         |
| `EFBIG`              | warn     | "Exceeds server limit of X" (from `caps.limits`), offer download instead                                                                                 |
| `EBUSY`              | prompt   | Conflict dialog (etag) or "Command timed out" (exec)                                                                                                     |
| `EMFILE`             | info     | Downgrade watcher to polling, update status bar                                                                                                          |
| `ERANGE`             | error    | Retry the read unranged; log                                                                                                                             |
| `ENOSYS`             | info     | Hide/disable the feature permanently for the session with an explanation                                                                                 |
| `EIO`                | error    | Retry button + Problems entry with `syscall`/`path`                                                                                                      |
| `ENETWORK`           | error    | Offline banner, exponential-backoff retry, queue nothing destructive                                                                                     |
| `ECANCELED`          | info     | User-cancelled action/task: status-bar note only, never a toast                                                                                          |
| `EACTION`            | error    | A contributed action failed: names the **action title**, the operation and the paths; full `detail` copyable in Problems; the shell is never left broken |

Rules: errors always name the **path** and the **operation**; the raw code is available (copyable) in the Problems
panel; nothing is retried automatically except idempotent reads and the watcher.

---

## 17. Security

* The UI never sees or sends host paths — only `/`-relative virtual paths ([api.md §4.1](./api.md#41-conventions)).
  `paths.js` normalises and rejects `..`
  escapes, `\`, `:`, `~`, control characters and the reserved `.fsapi` segment *before* a request is made (defence in
  depth; the server is authoritative).
* Every mutating request carries `X-Fs-Api: 1` (CSRF mitigation).
* **Action invocation is server-authoritative.** Descriptors from `GET /actions` are a *UI hint only*: `requires`, the
  selection contract and `enablement` are re-checked server-side on `POST /action`; every path in the submitted context
  is re-resolved through the same virtual-path sandbox as the FS API; parameters are validated against the declared
  schema before the handler runs. A crafted client can reach nothing the FS API does not already expose.
* Contributed actions never receive a shell string — parameters arrive as typed JSON and any process launch goes through
  the fixed-argument `exec` allowlist.
* Action results are data, not markup: `document`/`toast`/`diff` payloads reach the DOM through `textContent` or the
  Monaco model, never `innerHTML`.
* **No `innerHTML` with server data.** Filenames, git output, error messages and
  `exec` stdout are inserted with `textContent`. The one exception, `MarkdownPreview`, runs through a sanitiser with an
  allowlist and blocks scripts, iframes, event handlers and non-`http(s)`/relative URLs.
* Recommended CSP for the UI route (relaxable per deployment):
  `default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'self'`.
* Downloads go through `<a download>` on an FS API URL or a `Blob`, never
  `window.open` of user-controlled strings.
* The UI **does not** attempt to hide `.hidden` content client-side — the server already treats it as non-existent; the
  UI simply never invents entries.
* `exec`-backed features (search, git) send fixed argument arrays only; no user string is ever concatenated into a
  command line.

---

## 18. Persistence & session restore

`persist.js` writes a single versioned record per mount (`cognotik.fs.ui.v1:<base>`) into `localStorage`:

```json
{
  "schema": 1,
  "expanded": [
    "/",
    "/src",
    "/src/main"
  ],
  "tabs": [
    {
      "path": "/src/main.kt",
      "pinned": true,
      "line": 42,
      "col": 8
    }
  ],
  "active": "/src/main.kt",
  "layout": {
    "sidebar": 280,
    "bottom": 0,
    "panel": "explorer",
    "density": "comfortable"
  },
  "prefs": {
    "editor": "monaco",
    "wordWrap": false,
    "theme": "auto"
  }
}
```

* Restore is **best-effort and non-blocking**: paths that now `ENOENT` are dropped with a single summary toast ("2
  previously open files are gone").
* A schema bump discards unknown records rather than migrating.
* *Reset workspace state* is a command (and a `?reset=1` escape hatch).
* Unsaved buffers are **not** persisted in v2 (explicitly documented in the UI);
  `beforeunload` guards instead.

---

## 19. Extension points (actions, menus, contributions)

This is the section the rest of the document exists to serve. `fileserver` is embedded as a **library**, so there is
deliberately no plugin loader — no manifests, no discovery, no sandbox. What we owe extenders instead is a set of
extension points with the expressive power of IntelliJ's `AnAction` / `ActionGroup` / `DataContext` /
`Presentation`, so that **an existing JetBrains plugin action can be ported by rewriting its `actionPerformed` body and
nothing else**.

Two registration surfaces produce identical UI:

| Surface                                                           | Where the code lives  | When to use                                                                                                                                             |
|-------------------------------------------------------------------|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Server-side (Kotlin)** `UiActions.register(...)`                | the embedding JVM app | the default — tool logic already runs on the JVM (file system, git, LLM clients, ported plugin code). **No JavaScript is written or shipped** (§19.11). |
| **Client-side (ES module)** `registerAction(...)` in `webui/ext/` | the browser           | only when the tool needs bespoke UI (grid editor, canvas panel) or must stay purely local (§19.12).                                                     |

### 19.1 Anchors — the three required surfaces (and the rest)

| Anchor id                                                                               | Renders in                                                                                          | JetBrains analogue             |
|-----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|--------------------------------|
| `main/file` `main/edit` `main/selection` `main/view` `main/go` `main/tools` `main/help` | main menu bar (§8.9)                                                                                | `MainMenu` groups              |
| `main/toolbar`                                                                          | header toolbar                                                                                      | `MainToolBar`                  |
| **`explorer/context`**                                                                  | tree right-click / `Shift+F10` / `Alt+Enter`, over a **multi-selection of files *and* directories** | `ProjectViewPopupMenu`         |
| `explorer/toolbar`, `explorer/empty`                                                    | Explorer toolbar; right-click on empty space (context = current folder)                             | project view toolbar           |
| `tab/context`                                                                           | editor tab right-click (context = that tab's file)                                                  | `EditorTabPopupMenu`           |
| `editor/context`                                                                        | editor right-click, caret only                                                                      | `EditorPopupMenu`              |
| **`editor/selection`**                                                                  | editor right-click **with a non-empty selection**, plus `Alt+Enter`                                 | `EditorPopupMenu` / intentions |
| `editor/gutter`                                                                         | gutter / line-number context menu                                                                   | `EditorGutterPopupMenu`        |
| `breadcrumb/context`, `statusbar`, `git/context`                                        | breadcrumb segment, status-bar cells, git file rows                                                 | status-bar widgets, VCS menus  |
| *(implicit)* `palette`                                                                  | every action, unless `paletteHidden: true`                                                          | *Find Action*                  |

Anchors are plain strings. Unknown anchors are **ignored, never fatal** (forward and backward compatibility), and a host
panel can declare its own with
`registerMenuAnchor('notes/context')`.

### 19.2 The action descriptor

```js
registerAction({
    id: 'tests.generate',                    // stable, namespaced, unique
    title: 'Generate Tests…',                // mandatory; icon-only is rejected
    description: 'Create unit tests for the selected files or folders',
    category: 'AI',                          // palette grouping
    icon: '🧪',                               // optional, decorative only
    keys: ['Mod+Alt+T'],                     // remappable; empty is fine
    requires: ['exec'],                      // FS API capabilities → hidden when absent
    menus: [
        {anchor: 'explorer/context', group: '5_tools', order: 10},
        {anchor: 'editor/selection', group: '5_tools', order: 10},
        {anchor: 'main/tools', group: '2_generate'},
    ],
    selection: {                             // §19.4 — declarative gating
        min: 1, max: 200, kinds: ['file', 'dir'], homogeneous: false,
        collapseDescendants: true, onTruncated: 'ancestor',
    },
    enablement: ctx => !ctx.readOnly,        // cheap predicate
    update: (ctx, p) => {                    // optional; sync, < 1 ms, no I/O
        p.text = ctx.resources.length > 1
            ? `Generate Tests for ${ctx.resources.length} items…`
            : `Generate Tests for ${ctx.resources[0].name}…`;
    },
    params: [ /* §19.7 */],
    modal: false, singleton: true, preview: true,
    run: async (ctx, params) => { /* → ActionResult, §19.8 */
    },
});
```

### 19.3 DataContext and data keys

The context handed to `update()` / `run()` is IntelliJ's `DataContext` with a smaller, documented key set:

```js
ctx.origin            // 'explorer'|'editor'|'tab'|'breadcrumb'|'palette'|'keybinding'|'menu'
ctx.anchor            // which anchor produced this invocation (null for palette)
ctx.resources         // [{path, name, type:'file'|'dir', size, mtimeMs, readOnly, mimeType}]
ctx.paths
ctx.files
ctx.dirs
ctx.commonAncestor
ctx.truncated
ctx.activeTab
ctx.editor          // EditorHandle | null
ctx.editorSelection               // null when there is no editor or no selection
ctx.caps
ctx.readOnly
ctx.get(key, fallback)            // DataContext.getData() equivalent — extensible
ctx.fs
ctx.ui
ctx.t
ctx.signal
ctx.progress
```

```js
ctx.editorSelection = {
    path, languageId, isEmpty, wholeDocument,
    ranges: [{startLine, startColumn, endLine, endColumn}],  // 1-based, multi-cursor aware
    text,                       // selected text, capped at limits.maxSelectionBytes
    before, after,              // ≤ 2 KiB of surrounding context, for prompt building
    documentText: () => Promise < string >,
};
```

New data can be supplied without touching core:
`registerDataProvider(elementOrAnchor, key => value)` — resolved by walking up the DOM exactly like JetBrains'
`DataProvider` chain, so a contributed panel can be a first-class context source for actions written by someone else.

### 19.4 The selection contract (files **and** directories, multi-select)

* Right-click **inside** the current selection ⇒ the context is the whole selection. Right-click **outside** it ⇒
  selection moves to that node first (IDE convention), and the change is announced.
* `resources` arrive in tree order, de-duplicated. With `collapseDescendants: true`
  (default) a selected folder swallows its selected descendants, so an action sees
  `/src` once rather than `/src` plus 400 children.
* **Directories are first-class.** Nothing is silently skipped because a folder was selected; `kinds` declares what is
  accepted and `homogeneous: true` forbids mixing. An action that wants a folder's contents expands it itself
  (server-side `readdir`) — the UI never ships 10 000 paths over the wire.
* Hard cap `limits.maxContextResources` (default 500). Beyond it the context carries
  `truncated: true`, `count` and `commonAncestor`, and `onTruncated` decides:
  `'reject'` (disabled with a reason), `'ancestor'` (send the common ancestor instead),
  `'allow'` (chunked by the action).
* Palette and keybinding invocations synthesise a context from the **last active data provider** (focused tree, focused
  editor), so `Alt+Enter` and `Mod+Shift+A` behave exactly like the mouse path.

### 19.5 `update()` and `Presentation`

```js
update(ctx, p)        // sync, budget < 1 ms, no I/O, no allocation-heavy work
// p: {visible, enabled, text, description, icon, checked, badge, disabledReason}
updateAsync(ctx)      // optional; 150 ms budget; item renders disabled with an ellipsis
// and resolves in place — it must never block menu open
```

Policy (mirrors `isEnabledAndVisible`, tuned for discoverability):

* capability missing (`requires`) ⇒ **hidden** permanently for the session;
* selection contract unmet or `enablement` false ⇒ **visible but disabled**, with a mandatory `disabledReason` shown as
  a tooltip *and* `aria-describedby`;
* a throwing `update()` disables just that item and logs once — never a broken menu.

### 19.6 Groups, ordering, separators

Standard group ids, sorted lexicographically with a `role="separator"` between groups:
`1_open`, `2_new`, `3_clipboard`, `4_refactor`, **`5_tools` (default for contributions)**,
`6_generate`, `7_vcs`, `8_export`, `9_danger`. Within a group, `order` (default 100), ties broken by title. Submenus: `registerGroup({id:'group:ai', title:'AI', anchor, group,
order})` then place items at `{anchor:'group:ai'}`; nesting is capped at depth 3. Contributions can target any group but
may not reorder core ones.

### 19.7 Parameters and dialogs

Most ported plugin actions open a configuration dialog first. That is declarative:

```js
params: [
    {
        id: 'instruction', type: 'text', label: 'Instruction', required: true, multiline: true,
        placeholder: 'Rewrite as…', remember: true
    },
    {id: 'model', type: 'enum', label: 'Model', options: ['fast', 'smart'], default: 'smart'},
    {id: 'target', type: 'path', label: 'Output folder', default: ctx => ctx.commonAncestor},
    {
        id: 'dryRun', type: 'boolean', label: 'Preview only', default: true,
        help: 'Show a diff instead of writing files'
    },
]
```

Types: `string | text | number | integer | boolean | enum | multi-enum | path | paths |
language | secret`, each with `required`, `default`, `validate(value, ctx)`,
`visibleWhen(values)`, `help`. `ActionDialog` renders a real `<form>` in a focus-trapped
`<dialog>`: one `<label>` per field, `aria-describedby` help, inline `aria-invalid`
messages (from client `validate` **and** from a server `EINVAL` naming the field),
`Enter` submits, `Escape` cancels, values remembered per action when `remember: true`
(never for `secret`). Server-declared parameter schemas render through the *same*
component — which is how `GitAction`'s declared parameters already behave.

### 19.8 Results — what a tool can put on screen

| `kind`     | Payload                                   | UI                                                                           |
|------------|-------------------------------------------|------------------------------------------------------------------------------|
| `none`     | —                                         | task completes silently                                                      |
| `toast`    | `{severity, message, actions?}`           | toast + Problems entry                                                       |
| `refresh`  | `{paths}`                                 | invalidate + `readdir` those dirs (for non-watched mounts)                   |
| `open`     | `{paths, line?, col?, pinned?}`           | open tabs and reveal in the tree                                             |
| `document` | `{title, languageId, content}`            | read-only `fs-virtual:<id>` tab (`VirtualDocument.js`), *Save As…* available |
| `diff`     | `{path, original, modified, applyable}`   | `DiffEditor` tab with **Apply** / **Discard**; apply = `PUT` with `If-Match` |
| `edits`    | `{path, edits:[{range,text}], undoLabel}` | applied to the open buffer as **one undo step**                              |
| `patch`    | `{unifiedDiff}`                           | multi-file preview with per-file checkboxes, applied via `POST /batch`       |
| `form`     | `{descriptor}`                            | chained dialog — wizards and follow-up questions                             |
| `panel`    | `{panelId, data}`                         | routes output to a registered panel / tool window                            |
| `stream`   | `{streamId}`                              | SSE appended live to a tool-output tab, cancellable                          |

`preview: true` (the default) forces `edits`/`patch`/`diff` through an explicit confirmation; a tool must opt out
deliberately. Every result is announced.

### 19.9 Progress, cancellation, background execution

* Invocations are **tasks** in `store.tasks`: status-bar entry, `role="progressbar"` with a text alternative, and a
  Cancel button wired to `ctx.signal` → `DELETE /action/{id}`.
* `modal: true` shows a progress dialog (rare); background is the default, and the user can keep editing — the JetBrains
  `Task.Backgroundable` equivalent.
* `singleton: true` refuses (with a reason) or queues a second invocation.
* Cancellation is cooperative and always surfaced as `ECANCELED` (§16). Timeouts belong to the action, never to a global
  watchdog; a stalled action stays cancellable.
* Progress messages are announced politely, throttled to one every 2 s.

### 19.10 Editor edits, previews and undo

* `editor.applyEdits(edits, {undoLabel})` wraps `pushEditOperations` in
  `pushStackElement()` calls ⇒ `Mod+Z` undoes an entire tool run atomically.
* Multi-file edits are previewed (`patch`) and applied with per-file `If-Match`; any
  `EBUSY` aborts the remainder and lists exactly what was and was not applied — never a silent half-application.
* A tool must not write to disk behind an **open, dirty** buffer: reconciliation refuses to clobber it and shows the
  standard "Changed on disk" banner (§11).
* `PlainTextEditor` implements a reduced `EditorHandle` (`getSelections`, `applyEdits`, no decorations); actions needing
  more declare `requiresEditor: 'monaco'` and are disabled elsewhere with a reason.

### 19.11 Server-declared actions — Kotlin API and wire protocol

The zero-JavaScript path, and the one a ported IDE plugin action should take:

```kotlin
object UiActions : DynamicEnum<UiAction>()      // same pattern as FsAction / GitAction

interface UiAction {
  val descriptor: ActionDescriptor            // serialised verbatim into GET /actions
  fun update(ctx: ActionContext): Presentation = Presentation.default
  suspend fun invoke(
    ctx: ActionContext,
    params: JsonObject,
    progress: ProgressSink
  ): ActionResult
}

data class ActionContext(
  val fs: VirtualFileSystem,          // the sandboxed root — identical to the FS API's
  val resources: List<Resource>,      // re-validated virtual paths + stat
  val editorSelection: EditorSelection?,
  val origin: String,
  val principal: Principal?,          // whatever the embedding app's auth produced
)

sealed interface ActionResult {          // mirrors §19.8 one-for-one
  data class Toast(...) : ActionResult
  data class Document(val title: String, val languageId: String, val content: String) : ActionResult
  data class Edits(val path: String, val edits: List<TextEdit>, val undoLabel: String) : ActionResult
  data class Patch(val unifiedDiff: String) : ActionResult
  data class Form(val next: ActionDescriptor) : ActionResult
  /* Refresh, Open, Diff, Panel, Stream */
}
```

Host registration:

```kotlin
fileServer.ui {
  actions {
    register(ExplainSelectionAction())     // editor/selection
    register(GenerateTestsAction())        // explorer/context: files *and* dirs
    register(ProjectAuditAction())         // main/tools
  }
  menus { group("group:ai", title = "AI", anchor = "explorer/context", order = 50) }
}
```

Wire protocol (purely additive to [api.md](./api.md); no existing op changes):

```http
GET    /.fsapi/v1/actions                       -> {schema, etag, actions:[…], groups:[…]}
POST   /.fsapi/v1/actions/update                -> {presentations:{id:{visible,enabled,text,disabledReason}}}
POST   /.fsapi/v1/action/{id}      X-Fs-Api: 1  -> ActionResult | {invocationId}
GET    /.fsapi/v1/action/{invocationId}/events  -> SSE: progress | log | result | error
DELETE /.fsapi/v1/action/{invocationId}         -> cancel
```

```json
{
  "context": {
    "origin": "explorer",
    "anchor": "explorer/context",
    "truncated": false,
    "resources": [
      {
        "path": "/src",
        "type": "dir"
      },
      {
        "path": "/README.md",
        "type": "file"
      }
    ],
    "editorSelection": null
  },
  "params": {
    "instruction": "add kdoc",
    "dryRun": true
  }
}
```

`POST /actions/update` is **optional** and issued at most once per menu open (coalesced, abortable); an action that does
not set `dynamicUpdate: true` is gated purely by its static contract, so the common case costs zero requests.
Descriptors are cached by
`etag`, echoed in `/meta` as `actions.etag`.

### 19.12 Other client-side contribution points

A downstream module drops one ES module into `webui/ext/` and adds one line to
`contributions.js` (or the host page imports it after boot). The API mirrors the server-side `FsAction`/`GitAction`
`DynamicEnum` philosophy:

```js
import {
    registerAction, registerGroup, registerMenuAnchor, registerDataProvider,
    registerCommand, registerPanel, registerEditor,
    registerStatusItem, registerTreeDecorator,
    on, fs, caps, ui, t, API_VERSION
} from '../app/api.js';

registerCommand({
    id: 'hash.sha256',
    title: 'File: Copy SHA-256',
    requires: ['exec'],                          // hidden when unavailable
    when: ctx => ctx.selection.length === 1,
    run: async ctx => {
        const {stdout} = await fs.exec('sha256sum', [ctx.selection[0]]);
        await navigator.clipboard.writeText(stdout.trim());
    },
});

registerPanel({
    id: 'notes', title: 'Notes', icon: '🗒',
    location: 'sidebar', order: 50,
    create: () => new NotesPanel(),              // any Component
});

registerEditor({
    id: 'csv', priority: 50,
    canOpen: stat => stat.path.endsWith('.csv'),
    create: ctx => new CsvGridEditor(ctx)
});

registerTreeDecorator(node => node.readOnly ? {badge: '🔒', tooltip: t('readOnly')} : null);
```

### 19.13 Rules and guarantees

* `fs` is the same capability-checked client; there is no privileged path.
* Registering an **action** automatically yields: a palette entry, an `Alt+Enter`
  listing, a remappable keybinding, an accessible name, `when`/`requires`/selection gating, disabled-reason plumbing, a
  progress task with cancellation, and FS-error mapping (§16). Nothing about menus, ARIA or focus is the extender's
  problem.
* **Text is mandatory.** Icon-only registrations are rejected at registration time (throws in dev, logs and falls back
  to the id in production).
* **Failure isolation.** A throwing `update()` disables one item; a throwing `run()`
  becomes an `EACTION` notification. The shell never breaks because a tool did.
* Anchors, data keys, parameter types and result kinds are **versioned**
  (`API_VERSION`); unknown values are ignored in both directions, so an older client and a newer server (or the reverse)
  degrade instead of exploding.
* Actions must not assume the DOM: client-side actions get `ctx.ui` and nothing else.
* Panels/editors get lifecycle (`mounted`/`destroy`), theme tokens and `i18n` for free.
* Extensions may **not** monkey-patch core; the lint test asserts imports come only from `app/api.js`.

---

## 20. Testing strategy

| Layer           | Tooling                     | What                                                                                                                                                                                     |
|-----------------|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Unit (pure)     | node:test, no DOM           | `paths`, `TreeModel`, `TabModel`, `errors`, fuzzy matcher, `mime`                                                                                                                        |
| Client contract | node:test + real server     | `fsclient` against `FileServerCli.start()` on a temp dir; asserts every documented status/code path in [api.md](./api.md)                                                                |
| Component       | jsdom                       | mount/update/destroy, ARIA attributes, keyboard handlers, no leaks                                                                                                                       |
| Actions & menus | node:test + jsdom           | anchor resolution, group ordering/separators, selection-contract gating, `update()` < 1 ms budget, disabled-reason presence, failure isolation                                           |
| Action contract | node:test + real server     | `/actions` descriptor schema, every `ActionResult` kind round-trips, parameter validation (`EINVAL` → field), progress SSE, cancellation (`ECANCELED`)                                   |
| Accessibility   | axe-core in Playwright      | zero violations on shell, tree, tabs, palette, dialogs, git panel                                                                                                                        |
| Keyboard-only   | Playwright                  | full happy path (open, edit, save, rename, delete, git commit) with no mouse events                                                                                                      |
| Ported action   | Playwright + fixture action | a reference tool registered server-side appears in all three surfaces, runs on a mixed file/dir multi-selection and on an editor selection, previews, applies, and undoes in one `Mod+Z` |
| Screen reader   | manual checklist            | NVDA + Firefox, VoiceOver + Safari; recorded per release                                                                                                                                 |
| Visual          | Playwright screenshots      | light/dark/hc × comfortable/compact                                                                                                                                                      |
| Perf            | Playwright trace            | budgets in §15 asserted on a synthetic 50 000-file tree                                                                                                                                  |

CI runs everything headless against `FileServerCli` with `--read-only` **and** writable modes, so read-only degradation
is covered.

---

## 21. Server-side work required

Small and additive — no change to the FS API contract:

1. **`WebUiServlet`** (new): serves `webui/**` from the classpath with correct MIME types (`.mjs`/`.js` →
   `application/javascript`, `.json`, `.css`, `.svg`, `.woff2`),
   `ETag`, conditional GET, and the caching rules from §2. Rejects traversal.
2. **`FileServerCli`**: mount it at `/ui/*`, and make `/` redirect to `/ui/` (keeping
   `/files/root/` reachable for the classic surface and no-JS clients).
3. **`FilesystemServlet`**: optional `getWebUiLink(req)` hook + a toolbar button ("Open in IDE view") injected via the
   existing `getToolbarActions`, so the classic listing links to the SPA at the equivalent path.
4. **`/meta`**: no change required; consider adding a `uiVersion`/`buildId` field to help cache-bust assets, and
   `features` for deployment-level UI toggles.
5. Nice-to-have for QuickOpen at scale: honour `Accept-Encoding: gzip` on `dir`
   responses (pure win, no contract change).
6. **`UiActions` registry + action endpoints** (new, additive — the substantial piece):
   descriptor serialisation on `GET /actions`, `POST /action/{id}` with a re-validated context/params payload, an SSE
   progress channel, `DELETE` for cancellation, and
   `ActionResult` serialisation (§19.11). This is what makes "JetBrains plugin action, outside the IDE" work without
   shipping JavaScript.
7. **Host registration surface**: `fileServer.ui { actions { register(...) } }` with the same `DynamicEnum` semantics as
   `FsAction`/`GitAction`, plus `menus { group(...) }`.
8. **`/meta`**: add `actions: {schema, count, etag}` (descriptor cache validation) and
   `limits.maxContextResources` / `limits.maxSelectionBytes` so the client's gating numbers come from the server rather
   than from constants.

---

## 22. Milestones

| Milestone | Content                                                                                                                                                                                                      |
|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **M0**    | `WebUiServlet` + `index.html` + `core/{fsclient,capabilities,errors,paths}` + contract tests.                                                                                                                |
| **M1**    | AppShell, Explorer tree (lazy, ARIA, keyboard), StatusBar, Toasts. Read-only browsing.                                                                                                                       |
| **M2**    | TabBar + MonacoEditor + save with `If-Match`, dirty guards, PlainText fallback, BinaryPlaceholder.                                                                                                           |
| **M3**    | Mutations: create/rename/delete/copy/move, upload drop zone, context menus, confirmations.                                                                                                                   |
| **M4**    | CommandPalette, QuickOpen, keymap, persistence/session restore, deep links.                                                                                                                                  |
| **M4.5**  | **Action framework (client)**: `actions.js`/`context.js`, MenuBar, anchor-driven ContextMenu, ActionDialog, `Alt+Enter`, task/progress/cancel plumbing, result surfaces (`toast`/`open`/`document`/`edits`). |
| **M5**    | Watcher (SSE) + reconciliation, GitPanel, MarkdownPreview, DiffEditor, SearchPanel.                                                                                                                          |
| **M6**    | Extension API freeze, a11y audit sign-off (axe + screen-reader checklist), perf budgets in CI, docs.                                                                                                         |
| **M7**    | **Action framework (server)**: `UiActions`, `POST /action` + SSE, `diff`/`patch`/`form`/`stream` results, VirtualDocument tabs, host registration API.                                                       |
| **M8**    | First real ports: two or three existing JetBrains plugin actions running against the SPA with no client code, as the proof that §19 is sufficient.                                                           |

Each milestone ships behind no flag — the SPA lives at `/ui/` and the classic surface remains the default until M6, at
which point the CLI's `/` redirect flips. M4.5–M8 are the payload the rest of the plan exists to carry; if resourcing
forces a choice, pull M4.5 ahead of M5 and validate §19 against a real ported action early — the extension points are
far more expensive to change after the first downstream user.

---

## 23. Open questions

1. **Prefix discovery.** Should `/meta` echo the mount prefix so the SPA can be served from a path unrelated to
   `{mount}/{prefix}`? (Currently solved by a `<meta>` tag.)
2. **Monaco vendoring.** Ship a pinned copy under `vendor/` for air-gapped installs, or keep the CDN default and
   document the override only?
3. **Search.** Is an `exec` allowlist entry for `rg`/`grep` acceptable in default deployments, or should client-side
   scanning be the only tier?
4. **Multi-root.** Do we ever need more than one FS API root in one window (workspace-style), or is one mount per window
   sufficient?
5. **Preview vs pinned defaults** — VS Code's preview-tab behaviour is powerful but surprising; ship it on or off by
   default?
6. **Unsaved-buffer durability.** Worth an opt-in IndexedDB draft store post-v2?
7. **Tool output home.** Virtual-document tab, bottom panel, or both — and who owns the scrollback for a long `stream`
   result?
8. **Scopes beyond the selection.** Do actions need JetBrains-style scopes (whole project, changed files, VCS scope) as
   a declared context kind, or is "select it in the tree" always enough?
9. **Async presentations.** Should `POST /actions/update` be enabled by default, or should the static `selection`/
   `requires` contract be the only gating in stock deployments (one fewer request per menu open, one less way to make
   menus slow)?
10. **Workspace undo.** Per-file editor undo only, or a workspace-level undo journal for multi-file tool edits (needs
    server support and a snapshot story)?
11. **Selection actions in `PlainTextEditor`** — degrade to whole-file, or hide?
12. **Action identity across restarts** — should invocations be resumable (long tools surviving a page reload), or is
    cancel-on-unload acceptable for v2?

### 24.5 A server-declared action, as it appears in `GET /actions`

```json
{
  "id": "tests.generate",
  "title": "Generate Tests…",
  "description": "Create unit tests for the selected files or folders",
  "category": "AI",
  "icon": "🧪",
  "keys": [
    "Mod+Alt+T"
  ],
  "requires": [],
  "menus": [
    {
      "anchor": "explorer/context",
      "group": "5_tools",
      "order": 10
    },
    {
      "anchor": "editor/selection",
      "group": "5_tools",
      "order": 10
    },
    {
      "anchor": "main/tools",
      "group": "2_generate"
    }
  ],
  "selection": {
    "min": 1,
    "max": 200,
    "kinds": [
      "file",
      "dir"
    ],
    "collapseDescendants": true,
    "onTruncated": "ancestor"
  },
  "params": [
    {
      "id": "style",
      "type": "enum",
      "label": "Framework",
      "options": [
        "junit5",
        "kotest"
      ],
      "default": "junit5"
    },
    {
      "id": "dryRun",
      "type": "boolean",
      "label": "Preview only",
      "default": true
    }
  ],
  "resultKinds": [
    "patch",
    "toast"
  ],
  "singleton": true,
  "modal": false,
  "preview": true,
  "dynamicUpdate": false
}
```

The client renders this into a main-menu item, a context-menu item over any mixed file/folder selection, an
editor-selection item, a palette entry, an `Alt+Enter` entry and a parameter dialog — with no JavaScript written for it.

### 24.6 Porting a JetBrains action (Kotlin)

```kotlin
class ExplainSelectionAction : UiAction {
  override val descriptor = ActionDescriptor(
    id = "ai.explainSelection",
    title = "Explain Selection",
    menus = listOf(MenuPlacement("editor/selection", group = "5_tools", order = 5)),
    selection = SelectionSpec.none,           // editor-selection driven
    requiresEditorSelection = true,
  )
  override fun update(ctx: ActionContext) = Presentation(
    enabled = ctx.editorSelection?.isEmpty == false,
    disabledReason = "Select some text first",
  )
  override suspend fun invoke(ctx: ActionContext, params: JsonObject, progress: ProgressSink):
      ActionResult {
    val sel = ctx.editorSelection!!
    progress.report("Explaining ${sel.ranges.size} range(s)…")
    val text = llm.explain(sel.text, sel.languageId)          // the old actionPerformed body
    return ActionResult.Document(
      title = "Explanation — ${sel.path.substringAfterLast('/')}",
      languageId = "markdown", content = text,
    )
  }
}
```

### 24.7 Grouped context menu markup (multi-selection)

```html

<div role="menu" aria-label="Actions for 5 selected items" class="fs-menu">
    <div role="group" aria-label="Open">
        <button role="menuitem" tabindex="0">Open</button>
    </div>
    <hr role="separator">
    <div role="group" aria-label="Tools">
        <button role="menuitem" tabindex="-1">Generate Tests for 5 items…</button>
        <button role="menuitem" tabindex="-1" aria-disabled="true"
                aria-describedby="why-audit">Audit Project…
        </button>
        <button role="menuitem" tabindex="-1" aria-haspopup="menu" aria-expanded="false">AI</button>
    </div>
    <hr role="separator">
    <div role="group" aria-label="Danger">
        <button role="menuitem" tabindex="-1">Delete 5 items…</button>
    </div>
</div>
<span id="why-audit" class="sr-only">Requires a single folder to be selected</span>
```

---

## 24. Appendix — sketches

### 24.1 Tree row markup (ARIA tree pattern)

```html

<ul role="tree" aria-label="Files" aria-multiselectable="true" class="fs-tree">
    <li role="treeitem" id="n-src" aria-level="1" aria-expanded="true"
        aria-selected="false" tabindex="0" class="fs-tree__row" data-path="/src">
        <span class="fs-tree__twisty" aria-hidden="true">▾</span>
        <span class="fs-tree__icon" aria-hidden="true">📁</span>
        <span class="fs-tree__label">src</span>
        <ul role="group">
            <li role="treeitem" id="n-src-main" aria-level="2" aria-selected="true"
                tabindex="-1" data-path="/src/main.kt" aria-describedby="ro-hint">
                <span class="fs-tree__icon" aria-hidden="true">📄</span>
                <span class="fs-tree__label">main.kt</span>
                <span class="fs-tree__badge" aria-hidden="true">🔒</span>
            </li>
        </ul>
    </li>
</ul>
<span id="ro-hint" class="sr-only">read-only</span>
```

### 24.2 Save with optimistic concurrency

```js
export async function saveTab(tab, fs) {
    const bytes = new TextEncoder().encode(tab.editor.getValue());
    try {
        const res = await fs.writeFile(tab.path, bytes, {ifMatch: tab.etag});
        Object.assign(tab, {etag: res.etag, mtimeMs: res.mtimeMs, dirty: false});
        announce(`Saved ${basename(tab.path)}`);
    } catch (e) {
        if (e.code === 'EBUSY') return resolveConflict(tab, fs);   // Compare/Overwrite/Reload
        throw e;                                                   // errors.js maps the rest
    }
}
```

### 24.3 Coalesced watch application

```js
const pending = new Set();
watcher.on('change', e => {
    pending.add(dirname(e.path));
    schedule(flush);
});
watcher.on('overflow', () => {
    treeModel.invalidateAll();
    schedule(flush);
});

const flush = debounce(async () => {
    const dirs = [...pending].filter(d => treeModel.isLoaded(d));
    pending.clear();
    for (const d of dirs) await treeModel.refresh(d);    // 1 readdir each, only if loaded
    bus.emit('fs:changed', {dirs});
}, 100);
```

### 24.4 `<noscript>` fallback

```html

<noscript>
    <p>This view needs JavaScript.
        <a href="../files/root/">Use the classic file browser</a> instead.</p>
</noscript>
```