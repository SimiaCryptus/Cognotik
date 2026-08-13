# webapp-v2

*A dependency-light, framework-free chat UI that streams live HTML from your Cognotik server straight into the browser.*

## Overview

`webapp-v2` is the chat client that sits in front of a Cognotik session. Instead of shipping a heavyweight SPA framework
and reconciling a virtual DOM, the server streams HTML fragments over a WebSocket and the client reconciles,
post-processes (syntax highlighting, diagrams, math, collapsible sections, tabs), and delegates user interactions back —
all with plain ES modules and no build-time framework lock-in. If you're evaluating Cognotik, this is the module that
gives you a fast, themeable, embeddable chat surface without dragging in React/Vue/Angular or a bundler-specific
runtime. It builds to a static `build/` directory with relative asset paths, so it can be dropped behind any
reverse-proxy path prefix.

## Key Features

* **Framework-free rendering** — server-sent HTML fragments reconciled by id/version, no virtual DOM diffing overhead.
* **Rich content pipeline out of the box** — Prism syntax highlighting (lazy, `IntersectionObserver`-driven), Mermaid
  diagrams, MathJax typesetting, nested tabs, and collapsible sections, all debounced into a single pipeline pass.
* **Token-only theming** — themes are pure `--theme-*` CSS custom properties; add a new theme by copying one CSS block
  and one registry entry, no JS or component changes required. Ships with `light`, `dark`, and an `auto`
  mode that follows OS `prefers-color-scheme` live.
* **Resilient transport layer** — a single WebSocket authority per session with exponential backoff (1s → 30s, 5
  attempts), 10s connect timeout, 30s heartbeat, a send queue with promise resolution on delivery, and cold-start replay
  buffering.
* **Session-addressable URLs** — sessions are resolvable via `?session=`, URL fragment, or auto-generated ids, so chats
  are linkable and bookmarkable.
* **Archive mode** — render a fully static, read-only transcript from an embedded JSON payload with no socket or backend
  calls, for shareable or exported conversations.
* **Built-in diagnostics** — per-subsystem counters, `__cognotik.diagnostics()`, and `__cognotik.transport.stats()`
  exposed on the console for live debugging, plus a fatal-error panel instead of a blank white screen.

## Example

Run the dev server, or build for embedding under any path prefix:

```bash
npm install
npm run dev        # vite dev server
npm run build      # -> build/ (base: './', safe under any path prefix)
./build.sh         # build + stage into ../webui/src/main/resources
```

Attach to a session and pick a theme via URL parameters:

```
/coding/?session=U-20240101-ab12cd&theme=dark
/coding/?theme=auto
```

Control theming at runtime from JavaScript or the browser console:

```js
import {setTheme, toggleTheme} from './config/theme.js';

setTheme('dark');   // persists the choice
toggleTheme();       // light <-> dark
```

```js
// from the browser devtools console
__cognotik.setTheme('dark');
__cognotik.listThemes();   // [{id:'light',…}, {id:'dark',…}]
```

## Integration

`webapp-v2` is the front-end counterpart to Cognotik's server-driven session model:

* **Protocol** — consumes a simple line protocol over WebSocket (`<id>,<version>,<content>` inbound;
  `!<messageId>,<action>` outbound), so any backend that speaks this framing can drive the UI.
* **Message typing by convention** — id prefixes (`u*` user, `s*` system, `z*` reference, otherwise assistant) are
  interpreted client-side, keeping the wire format minimal.
* **Strict layering** — `core/` (transport, session, store) never imports `render/` or `ui/`; anything that needs to
  bubble upward goes through a single event bus (`core/bus.js`), making it straightforward to swap or extend the chrome
  without touching the transport layer.
* **Config-driven app info** — `config/app-config.js` fetches server-provided `appInfo` non-blockingly, falling back to
  sane defaults, so the client degrades gracefully if the backend is slow or unavailable. No external UI framework
  dependency is required to embed `webapp-v2` — it is plain ES modules bundled by Vite, making it a lightweight option
  for teams that want Cognotik's chat experience without adopting a specific frontend stack.