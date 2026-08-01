# Cognotik chat client — v2 (modular ES6)

A dependency-light, framework-free chat UI for the Cognotik web server. Plain ES modules, no virtual DOM: the server
streams HTML fragments over a WebSocket and this client reconciles, post-processes and delegates interactions back.

---

## Run

```bash
npm install
npm run dev        # vite dev server
npm run build      # -> build/ (base: './', safe under any path prefix)
./build.sh         # build + stage into ../webui/src/main/resources
```

---

## URL parameters

| Parameter | Values                  | Purpose                                                                                                                                         |
|-----------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `session` | any session id          | Attach to an existing session. Also accepted as the URL fragment (`#U-20240101-ab12cd`). Falls back to a freshly generated `U-YYYYMMDD-xxxxxx`. |
| `theme`   | `light`, `dark`, `auto` | Select the colour theme (see below). Remembered in `localStorage`.                                                                              |

Examples:

```
/coding/?session=U-20240101-ab12cd&theme=dark
/coding/#U-20240101-ab12cd?theme=light
/coding/?theme=auto
```

---

## Theming

Themes are **token-only**: a theme is a set of `--theme-*` CSS custom properties declared under
`html[data-theme="<id>"]`. No JavaScript and no component stylesheet contains a literal colour, so themes can be added
without touching behaviour.

Built-in themes: `light` (default) and `dark`, plus the pseudo-theme `auto`
(follows the OS `prefers-color-scheme` and keeps tracking it live).

### Selection order

`config/theme.js` resolves the preference once, at boot step 0 — before any renderer initialises or any content is
mounted, so there is no flash of the wrong palette:

1. explicit preference passed by an embedder — `boot({theme: 'dark'})`
2. `?theme=<id>` (query string, or a hash query such as `#session?theme=dark`)
3. persisted preference (`localStorage['theme']`)
4. `auto` → `prefers-color-scheme`

An unknown id is logged (with the list of known ids) and ignored rather than applied.

### Runtime API

```js
import {setTheme, toggleTheme, getTheme, listThemes, isDarkTheme} from './config/theme.js';

setTheme('dark');            // persists the choice
setTheme('auto');            // hand control back to the OS
toggleTheme();               // light <-> dark
```

From the browser console:

```js
__cognotik.setTheme('dark');
__cognotik.listThemes();     // [{id:'light',…}, {id:'dark',…}]
```

A `theme-changed` event (`Events.THEME_CHANGED`) is published on the bus with
`{theme, requested}` whenever the applied theme changes.

### Adding a theme

1. Copy the `[data-theme="dark"]` block in `src/styles/themes.css`, rename the selector (e.g.
   `[data-theme="high-contrast"]`) and override the tokens you care about — unspecified tokens inherit the light
   defaults.
2. Register it in `THEMES` in `src/config/theme.js`:

   ```js
   export const THEMES = Object.freeze({
       light: Object.freeze({id: 'light', label: 'Light', dark: false}),
       dark: Object.freeze({id: 'dark', label: 'Dark', dark: true}),
       'high-contrast': Object.freeze({id: 'high-contrast', label: 'High contrast', dark: true})
   });
   ```

That is all: `?theme=high-contrast` now works, `color-scheme` follows the `dark` flag, and Mermaid picks its dark
variant automatically.

### Token reference

Layout/typography tokens (`--font-*`, `--spacing-*`, `--border-radius-*`) live in
`styles/base.css`; colour tokens live in `styles/themes.css`:

`--theme-background`, `--theme-surface`, `--theme-surface-alt`, `--theme-border`,
`--theme-text`, `--theme-text-muted`, `--theme-primary`, `--theme-primary-contrast`,
`--theme-accent`, `--theme-accent-contrast`, `--theme-error`, `--theme-user-bubble`,
`--theme-code-background`, `--theme-warning-background`, `--theme-warning-text`,
`--theme-overlay`, `--theme-archive-background`, `--theme-archive-text`.

The active theme is also mirrored as `body.theme-dark` for the rare rule that needs a boolean rather than a token.

---

## Layout

```
src/
  main.js                boot sequence (normative order, see below)
  core/                  transport + state. MUST NOT import render/ or ui/
    bus.js               cross-cutting event bus (the only "upward" channel)
    session.js           session id generation/resolution
    protocol.js          frame encode/decode
    transport.js         the single WebSocket + reconnect authority
    store.js             message store, versioning, reference dependency index
  render/                DOM production & post-processing
    message-list.js      reconcile by id/version; delegate §7 interactions
    references.js        `z*` reference expansion
    pipeline.js          debounced tabs → collapsibles → Prism → Mermaid → MathJax
    tabs.js              nested tabs with sticky per-container state
    collapsible.js       expandable sections
    prism.js             syntax highlighting (lazy, IntersectionObserver)
    mermaid.js           diagrams (initialised exactly once)
    mathjax.js           math typesetting
    verbose.js           verbose-mode gating
  ui/                    chrome
    shell.js             mounts the app skeleton
    composer.js          input area, markdown toolbar, preview
    modal.js             modal fetch/host
  config/
    env.js               all build-time env access
    theme.js             theme registry/resolution/application
    app-config.js        `appInfo` fetch + effects
    archive.js           archive mode detection & hydration
  styles/
    themes.css           colour tokens, one block per theme
    base.css             structural CSS
  util/                  logger, dom helpers, debounce
```

**Dependency rule:** `core/` never imports `render/` or `ui/`; anything that must travel upwards goes through
`core/bus.js`.

---

## Boot sequence

`boot({root, sessionId, theme})`:

0. **theme** — resolve and apply `data-theme` (before any paint)
1. **session identity** — explicit → `?session=` → `#fragment` → generated
2. **renderers** — Mermaid/MathJax configured *before* content is mounted (`Prism.manual = true` is set in its own
   module so it precedes plugin evaluation)
3. **shell DOM** — scroller, message list, composer host, modal root
4. **global listeners** — pipeline hooks, verbose shortcut, modal triggers, tab observer
5. **socket** — `transport.connect(sessionId)`
6. **appInfo** — non-blocking; failures fall back to `DEFAULT_APP_CONFIG`
7. **archive mode** short-circuits 5 & 6 entirely

---

## Protocol

* **Inbound data frame:** `<id>,<version>,<content>` — split on the *first two* commas only; content may contain commas,
  newlines and raw HTML. Malformed frames are counted and dropped.
* **Control frames** are JSON (`ping`/`pong`/`connect`). A frame that fails `JSON.parse` is not an error — it is a data
  frame.
* **Outbound:** `!<messageId>,<action>`, `!<messageId>,userTxt,<encodeURIComponent(text)>`, and raw text (no `!` prefix)
  for composer submissions. Actions are forwarded verbatim, never whitelisted.
* **Message type** is derived from the id prefix exactly once, in the store:
  `u*` user, `s*` system, `z*` reference (never rendered directly), everything else assistant.
* **Pending state** is a property of the delivered content: the server embeds a
  `spinner-border` element while it is still working.

`transport.js` is the single reconnect authority: exponential backoff (1s → 30s, 5 attempts), 10s connect timeout, 30s
heartbeat, a send queue whose promises resolve only once a frame is actually written (and reject on terminal failure),
plus cold-start replay buffering and steady-state aggregation drained in chunks of 10.

---

## Diagnostics

* Per-subsystem loggers with bracketed prefixes and counters; `debug` is level-gated (`VITE_LOG_LEVEL`, default `debug`
  in dev / `warn` in prod).
* `__cognotik.diagnostics()` — counter snapshot for every subsystem.
* `__cognotik.transport.stats()` — socket url/readyState/queue/buffer/uptime.
* Global `error` / `unhandledrejection` traps, and a fatal-error panel instead of a blank page.

---

## Archive mode

Enabled by `<html data-archive>` or a `/archive/` path. Messages are hydrated from the
`#archived-messages` JSON payload, the socket and `appInfo` are skipped, and interactive affordances are disabled via
`body.archive-mode`. Themes still apply.