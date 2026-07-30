# Reverse Specification — Chat UI Port (React → Modular ES6)

**Status:** normative for the port. Where this document and the current React code disagree, this document wins; every
intentional divergence is listed in §20.

**Goal:** re-implement the existing chat client in clean, modular, dependency-light ES6 (native ES modules, no React, no
Redux, no styled-components). Dropping the React/Redux layers removes the impedance mismatch with the libraries that
actually matter (Prism, Mermaid, MathJax) which all operate directly on the DOM.

---

## 0. Scope

### 0.1 In scope (must be preserved bit-for-bit at the protocol/DOM level)

1. WebSocket-based **message publishing** and **action posting** protocol (§3).
2. Server-emitted **interactive controls** (buttons/links/text inputs) that post back over the socket (§7).
3. Server-emitted **dynamic tabs**, including nested tabs and sticky per-container active-tab state (§8).
4. **Content rendering**: markdown-produced HTML with code syntax highlighting (Prism), Mermaid diagrams, and MathJax
   math (§10).
5. **Pointer-based div substitution**: `message-id` placeholders resolved against
   "reference" messages (ids beginning with `z`) (§5).
6. Everything strictly required to support the above: session identity, message store with versioning, DOM
   post-processing pipeline, collapsible sections, app config fetch, archive mode, modal fetch/host, verbose-mode gating
   (§2, §4, §6, §9, §11–§15).

### 0.2 Out of scope for this pass (spec extraction deferred — see §19)

- Theme system (colour themes, layout themes, CSS custom-property contract).
- Menubar / dropdown navigation.
- General UI polish concerns (composer chrome, spinners, modal chrome, error boundaries).
- Everything else (Redux slices that are not message/connection related, QR code, web-vitals, test harness, build
  scripts).

---

## 1. Runtime environment & bootstrap

### 1.1 Host page contract

The client is served under an arbitrary path prefix (e.g. `/coding/`, `/chat/`). It must not assume it is mounted at
`/`.

Required/optional DOM hooks present in the host page:

| Selector / id             | Purpose                                                       | Required     |
|---------------------------|---------------------------------------------------------------|--------------|
| `#root`                   | Mount point for the app                                       | yes          |
| `#archived-messages`      | `<script type="application/json">` payload for archive mode   | archive only |
| `html[data-archive]`      | Marks the document as an archive render                       | archive only |
| `#toolbar`, `#namebar`    | Legacy menubar containers hidden when `showMenubar === false` | no           |
| `#main-input`, `#session` | Legacy layout nodes repositioned when menubar hidden          | no           |

### 1.2 Boot sequence (normative order)

1. Resolve sessionId (§2)
2. Initialise renderers (Mermaid, MathJax loader, Prism manual mode)   (§10)
3. Mount shell DOM: #message-list-container, #chat-input-container, #modal-root
4. Attach global delegated listeners (message actions, tabs, collapsibles, shortcuts)
5. Open WebSocket (§3)
6. Fetch appInfo (non-blocking; failures use defaults)                 (§12)
7. If archive mode: hydrate from #archived-messages instead of steps 5–6 (§13)

Prism **must** be put in manual mode (`Prism.manual = true`) before any Prism plugin runs; Mermaid **must** be
initialised with `startOnLoad: false`. Both are currently double-initialised with conflicting options (see §20.3).

---

## 2. Session identity

Resolution order (first hit wins):

1. explicit sessionId passed by the embedder
2. URL query param ?session=<id>
3. URL fragment #<id>
4. freshly generated id

Generated **user** session id format:

U-YYYYMMDD-<6 chars of base36>        e.g. U-20240517-k3f9za

A **global** id generator also exists (used by the menubar for "new global session"):

G-YYYYMMDD-<4 chars, [A-Za-z0-9] filtered from a base64'd random int64>

Base64 alphabet is URL-munged: `=` stripped, `/` → `.`, `+` → `-`.

The session id is required for: WebSocket query string, `appInfo` fetch, and modal URL construction. It must be stable
for the lifetime of the page.

---

## 3. WebSocket transport (normative)

### 3.1 URL derivation

protocol := location.protocol === 'https:' ? 'wss:' : 'ws:'
host     := location.hostname port     := configuredPort ?? location.port ?? (https ? '443' : '8083')
path     := first path segment of location.pathname, wrapped in slashes, else '/' e.g. '/coding/agent' -> '/coding/'
'/' -> '/' url      := `${protocol}//${host}` + (portIsNonDefault ? `:${port}` : '') +
`${path}ws?sessionId=${sessionId}&lastMessageTime=${lastMessageTime}`

Port is omitted when it is the protocol default (`80` for `ws:`, `443` for `wss:`).

`lastMessageTime` is the highest timestamp of any message already received in this browser session, `0` on a cold start.
It allows the server to replay only the delta on reconnect. The port implementation **must** track this in the message
store rather than by probing handler objects (current implementation stashes `lastMessageTime` on handler functions and
computes `Math.max(...[])` → `-Infinity` when empty; see §20.1).

### 3.2 Lifecycle & reconnection

| Constant                 | Value     | Meaning                                                |
|--------------------------|-----------|--------------------------------------------------------|
| `CONNECT_TIMEOUT`        | 10 000 ms | No `open` within this window ⇒ force close + reconnect |
| `HEARTBEAT_INTERVAL`     | 30 000 ms | Outbound ping cadence                                  |
| `BASE_RECONNECT_DELAY`   | 1 000 ms  | Exponential backoff base                               |
| `MAX_RECONNECT_DELAY`    | 30 000 ms | Backoff ceiling                                        |
| `MAX_RECONNECT_ATTEMPTS` | 5         | After this, surface a terminal error                   |
| `SEND_QUEUE_INTERVAL`    | 50 ms     | Minimum gap between queued outbound frames             |

Backoff: `min(BASE * 2^attempt, MAX)`.

Rules:

- A **user-initiated** `disconnect()` sets a `forcedClose` flag and suppresses reconnection.
- Any *unexpected* close or error triggers reconnection unless already reconnecting.
- On successful open: reset `attempts = 0`, clear `forcedClose`, record `connectionStartTime`, start heartbeat, notify
  connection listeners with `true`.
- `send()` while not `OPEN` must **queue** the frame, kick a reconnect, and flush the queue once open. No frame may be
  silently dropped.
- Reaching `MAX_RECONNECT_ATTEMPTS` emits a terminal error
  `"Maximum reconnection attempts (5) reached"` and sets `forcedClose = true`.

The React layer contained a *second*, competing reconnect loop (`useWebSocket`: base 1000 ms, max 60 000 ms, 5 s
connection timeout, 10 attempts, 100 ms debounce). The port must have **exactly one** reconnect authority: the transport
module. See §20.2.

### 3.3 Heartbeat

Every `HEARTBEAT_INTERVAL`, if `readyState === OPEN`, send:

{"type":"ping","timestamp":<epoch ms>}

Inbound JSON control frames:

- `{"type":"pong"}` → ignore.
- `{"type":"ping"}` → reply `{"type":"pong"}`.
- `{"type":"connect", ...}` → ignore (never rendered).

A frame that fails `JSON.parse` is *not* an error: it is a data frame (§3.4). Control-frame detection must therefore be
a `try/parse/dispatch/else fall through`.

### 3.4 Inbound data frame format

Frames are **plain text**, not JSON:

<id>,<version>,<content>

Parsing rules (normative):

- Split on the **first two** commas only. `content` may contain any number of commas, newlines, and raw HTML.
- `id` and `version` must both be non-empty; otherwise log and drop the frame.
- `version` parses as an integer; if `NaN`, substitute `Date.now()`.
- `content` is treated as HTML when it matches `/<[a-z][\s\S]*>/i`, otherwise as text.
- Derived message record:

  { id, // server-assigned, stable across versions version:   <int>, content:   <string>, isHtml:    <bool>, rawHtml:
  isHtml ? content : null, timestamp: Date.now (), type:      derived (§4.2), sanitized: false }

### 3.5 Outbound action frame format

All user interactions are posted as text frames prefixed with `!`:

!<messageId>,<action>                       // e.g. !m17,run
!<messageId>,userTxt,<encodeURIComponent (text)>   // free-text reply

Canonical actions observed: `link`, `run`, `regen`, `stop`, `userTxt`, plus arbitrary server-defined action strings
carried in `data-message-action`. The client must **not**
whitelist actions — any `(messageId, action)` pair extracted from the DOM is forwarded verbatim.

Composer submissions (the main chat input) are sent as raw text with **no** `!` prefix.

### 3.6 Inbound buffering & aggregation

Two distinct regimes, both required to keep the DOM from thrashing during replay:

**Cold-start burst** — while `Date.now() - connectionStartTime < 10 000 ms`:
push messages into a `replayBuffer`; a 1 000 ms idle timer (reset on each message) flushes the whole buffer as one
batch.

**Steady state** — push into an `aggregateBuffer`; a 100 ms debounce drains it.

Draining a batch processes messages in **chunks of 10**, yielding to the event loop (`setTimeout(..., 0)`) between
chunks. A throwing handler must not abort the remaining chunk (wrap each dispatch in `try/catch`, log
`{messageId, messageType}`).

Both buffers and their timers must be cleared on close.

### 3.7 Public transport API (target shape)

class Transport extends EventTarget { connect (sessionId | config)
disconnect ()            // forced, no reconnect reconnect ()             // manual, resets attempts send
(text)              // queued if not open get isConnected ()
get readyState ()
get sessionId ()
} // Events: 'message' (detail: Message[]), 'open', 'close', //         'reconnecting' (detail: attemptNumber), 'error'
(detail: Error)

Replaces the current handler-array API (`addMessageHandler` / `addConnectionHandler` /
`addErrorHandler` / `on` / `off`), which duplicates event-emitter semantics three ways.

---

## 4. Message store & versioning

### 4.1 Upsert semantics

Keyed by `id`. `messageVersions: Map<id, version>`.

- Missing `version` ⇒ `Date.now()`.
- If `id` already exists: **replace** the record wholesale (do not merge).
- If `id` starts with `z` (a reference, §5), bump `version` to `Date.now()` on write so that every host message
  re-expanding it is invalidated.
- Ordering is insertion order of first sighting; version updates must **not** reorder.

### 4.2 Type derivation

From the id prefix:

'u...' -> 'user'
's...' -> 'system'
'z...' -> 'reference' (never rendered directly)
else -> 'assistant'

Full type union: `user | assistant | system | error | loading | reference | response | log | alert`. (`response` is a
legacy alias produced by the transport; the port should emit only the derived types above.)

### 4.3 Render filter

A message is rendered iff:

message.id is truthy && !message.id.startsWith ('z')
&& message.content.length > 0

### 4.4 Sanitisation

Current behaviour is a **no-op**: `sanitizeHtmlContent` extracts math spans into placeholders and immediately restores
them, and `DOMPurify` is imported but never called. The port must either (a) genuinely sanitise while preserving `$…$`,
`$$…$$`, `\(…\)`, `\[…\]`, or (b) document that server HTML is trusted. Do not ship the current accidental middle
ground. See §20.4.

Math-delimiter protection order, if sanitisation is enabled:

1. $$ ... $$     (display, greedy-safe, non-greedy body)
2. \[ ... \] (display)
3. \( ... \) (inline)
4. $ ... $       (inline; body must not start with $ or contain newlines)

Each match is swapped for `__MATH_PLACEHOLDER_<n>__`, the remainder sanitised, then placeholders restored.

---

## 5. Pointer-based substitution (`z*` references)

The server streams large or repeated fragments as separate **reference messages** whose ids begin with `z`, and embeds
placeholders inside host messages:

  <div class="referenced-message" message-id="z1234"></div>

### 5.1 Expansion algorithm (normative)

expand (html, store, seen = new Set ()):
root <- parse html into a detached container queue <- [root]
while queue not empty:
node <- queue.shift ()
id   <- node.getAttribute ('message-id')
if id and id not in seen and id.startsWith ('z'):
seen.add (id)
ref <- store.get (id)
if ref and ref.content:
node.innerHTML <- ref.content // may itself contain placeholders else if ref:
node.innerHTML <- '<span class="reference-error">Referenced content unavailable</span>'
else:
node.innerHTML <- '<span class="reference-error">Referenced message not found</span>'
for child of node.children: queue.push (child)
return root.innerHTML

Notes:

- Breadth-first, single pass. Because newly injected children are enqueued as the walk proceeds, nested references
  expand transitively.
- `seen` is per top-level expansion and prevents infinite recursion on cyclic references.
- Only `z`-prefixed ids are substituted; other `message-id` attributes are inert markers.
- Expansion is **not** applied to reference messages themselves.

### 5.2 Invalidation

A host message must be re-expanded whenever any `z*` message it references changes version. The React version
approximated this with a `Record<zId, version>` memo key over *all*
reference messages, re-expanding everything on any change. The port should build a
`refId -> Set<hostId>` dependency index during expansion and re-render only affected hosts.

---

## 6. DOM post-processing pipeline

Every time rendered content changes, run the pipeline. It is **debounced at 250 ms** and must be idempotent.

### 6.1 Order (normative — later steps depend on earlier ones)

1. restoreTabStates (snapshot)     // reapply remembered active tabs
2. updateTabs ()                   // discover/initialise new .tabs-container nodes
3. initCollapsibles ()             // idempotent expandable-header wiring
4. requestIdleCallback:
   highlight `pre code:not(.prismjs-processed)` that are visible; mark processed
5. renderMermaid (container)
6. setTimeout (100ms): renderMathJax (container)

MathJax is deliberately deferred one macrotask past Mermaid because Mermaid mutates layout and MathJax measures it.

### 6.2 Triggers

- Message list mutated (add/update/remove).
- Verbose mode toggled.
- Theme changed (Prism theme swap invalidates highlighting; collapsibles re-init).
- `MutationObserver` on the message list (`childList: true, subtree: true`) detecting an added node that is or contains
  `.tabs-container` — this catches tabs injected by server HTML after the initial paint.

### 6.3 Visibility guard

Steps 4 and 5 must skip elements with `offsetParent === null` (inactive tab panes, collapsed sections). They are
re-processed when the containing tab/section becomes visible, which is why the pipeline must re-run on tab activation
and section expansion.

---

## 7. Interactive controls

Server HTML embeds controls that the client turns into socket frames. Handling is by **event delegation** on the
message-list root; never per-element listeners.

### 7.1 Identifier extraction

messageId := el.getAttribute ('data-message-id')
?? closest ('[data-message-id]').getAttribute ('data-message-id')
?? el.getAttribute ('data-id')

### 7.2 Action extraction

Explicit attributes first:

action := el.getAttribute ('data-message-action') ?? el.getAttribute ('data-action')

Then class-based fallback (checked on the target, then on ancestors for `.href-link`):

| class               | action      |
  |---------------------|-------------|
| .href-link          | link        |
| .play-button        | run         |
| .regen-button       | regen       |
| .cancel-button      | stop        |
| .text-submit-button | text-submit |

If both `messageId` and `action` resolve, `preventDefault()`, `stopPropagation()`, dispatch.

### 7.3 Dispatch

action === 'text-submit':
input := document.querySelector (`.reply-input[data-id="${messageId}"]`)
text  := input.value; if blank -> no-op send (`!${messageId},userTxt,${encodeURIComponent(text)}`)
input.value = ''; input.style.height = 'auto' otherwise:
send (`!${messageId},${action}`)

### 7.4 Tab-click exclusion

A click whose target is inside `.tabs .tab-button` must be ignored by the action handler and left to the tab system
(§8), otherwise tab labels that happen to carry `data-id` would fire spurious actions.

### 7.5 Reply form

For `assistant` messages the client renders its own reply affordance:

  <div class="reply-form">
    <textarea class="reply-input" data-id="<messageId>" placeholder="Type your reply..."></textarea>
    <button class="text-submit-button" data-id="<messageId>" data-message-action="text-submit">Send</button>
  </div>

`Enter` (without `Shift`) in `.reply-input` submits.

---

## 8. Dynamic tabs

Tabs arrive as server-rendered HTML at arbitrary depth and may be nested arbitrarily.

### 8.1 DOM contract

  <div class="tabs-container" id="<optional>">
    <div class="tabs">
      <button class="tab-button" data-for-tab="A">A</button>
      <button class="tab-button" data-for-tab="B">B</button>
    </div>
    <div class="tab-content" data-tab="A"> ... </div>
    <div class="tab-content" data-tab="B"> ... </div>
  </div>

Invariants:

- `.tab-content` panes are **direct children** of `.tabs-container`. Selection must use
  `container.children` filtered by `.matches('.tab-content')`, *not* `querySelectorAll`, so nested containers' panes are
  not stolen.
- `.tab-button` nodes are selected as `container.querySelectorAll('.tabs > .tab-button')`
  for the same reason.
- The button's owning `.tabs` group is found via `button.closest('.tabs')`; only buttons in that group have their
  `active` class rewritten.
- Missing `id` on a container is auto-generated: `tab-container-${Date.now()}-${rand4}` and a warning is logged.

### 8.2 State model

tabStates:        Map<containerId, { containerId, activeTab }>
tabStateHistory:  Map<containerId, string[]>   // last 10 distinct activations stateVersion:     monotonically
increasing counter, recorded per container on save

State lives in memory only (not localStorage) and survives re-renders of the surrounding HTML, which is the entire
point: the server frequently replaces a message's HTML wholesale and the previously selected tab must be restored.

### 8.3 Activation (`setActiveTab(button, container)`)

1. forTab := button.dataset.forTab; abort+warn if absent
2. abort+warn if container has no id
3. no-op if forTab is already active AND its pane already has .active
4. save state (tabStates + history + version)
5. re-initialise any nested .tabs-container inside the owning .tabs group
6. for each button in the owning group: toggle .active on match (force reflow via `void btn.offsetWidth` on the newly
   active button)
7. for each direct-child .tab-content:
   match -> add .active, style.display = ''
   other -> remove .active, style.display = 'none' On the newly activated pane, in requestAnimationFrame:
   for each nested .tabs-container: setupTabContainer + restoreTabState

### 8.4 Restoration (`restoreTabState(container)`)

saved := tabStates.get (container.id)
if saved and a matching button exists -> setActiveTab (button, container)
else -> fall back to the first `.tabs > .tab-button` and log (distinguish "saved tab's button missing" from "no saved
state at all")

### 8.5 Discovery (`updateTabs`, debounced 250 ms)

if mutationInProgress: return // re-entrancy guard initCollapsibles ()
snapshot := getAllTabStates ()
for each document `.tabs-container` (deduped by id):
setupTabContainer (container)
active := tabStates[id] ?? snapshot[id] ?? container.querySelector ('.tabs .tab-button.active')?.dataset.forTab if
active: record + restoreTabState (container)
else:      activate first button, or warn if none finally: mutationInProgress = false

### 8.6 Container initialisation (`setupTabContainer`, idempotent)

Guarded by `container.dataset.tabSystemInitialized`. Performs:

- id backfill,
- initial active resolution (remembered state, else first button),
- class/display sync for buttons and panes (all panes hidden if nothing resolves),
- a **single delegated click listener** on the container:

  const button = event.target.closest ('.tab-button'); if (button && container.contains (button) && !
  button.classList.contains ('active')) { if (!button.closest ('.tabs')) return; setActiveTab (button, container);
  updateTabs (); event.stopPropagation (); event.preventDefault (); }

`stopPropagation` is what prevents an outer container from also handling a click on a nested container's button.

### 8.7 Required after activation

Activating a tab reveals previously hidden content, so §6 steps 4–6 (Prism, Mermaid, MathJax)
must run for the newly visible subtree.

---

## 9. Collapsible sections

DOM contract:

  <div class="expandable-header"> Title <span class="expand-icon">▼</span> </div>
  <div class="expandable-content"> ... </div>       <!-- nextElementSibling -->

Behaviour on header click:

content := header.nextElementSibling icon    := header.querySelector ('.expand-icon')
content.classList.toggle ('expanded')
icon?.classList.toggle ('expanded')
icon.textContent = content.classList.contains ('expanded') ? '▲' : '▼'

Initialisation must be **idempotent**: mark wired headers (dataset flag, not an expando property) and on re-encounter
only re-sync the icon glyph to the content state. Called from the pipeline (§6.1 step 3) and after theme changes.

Expanding a section reveals content ⇒ re-run §6 steps 4–6 for that subtree.

---

## 10. Content renderers

### 10.1 Prism (syntax highlighting)

- `Prism.manual = true`.
- Languages: javascript, typescript, jsx, tsx, css, markup, markdown, diff, json-ish, kotlin, java, scala, python,
  mermaid.
- Plugins: toolbar, copy-to-clipboard, show-language, line-numbers, line-highlight, diff-highlight, normalize-whitespace
  (each with its CSS).
- Highlighting strategy: `IntersectionObserver` over `pre code`; on intersection,
  `requestIdleCallback(() => Prism.highlightElement(el))`, then `unobserve`.
- Skip elements already highlighted: `.language-none`, `.prismjs-processed`, or containing
  `.token` descendants.
- Modal content is highlighted with `Prism.highlightAllUnder(modalRoot)` (§14).
- Prism theme CSS is swapped on colour-theme change (deferred to §19.1).

### 10.2 Mermaid

mermaid.initialize ({ startOnLoad: false, securityLevel: 'loose', theme: 'default', logLevel: 3 })

Render loop, per pipeline pass:

for el of container.querySelectorAll ('.mermaid:not (.mermaid-processed)'):
if el.offsetParent === null: skip // not visible yet source := el.textContent.trim ()
if !source: el.classList.add ('mermaid-error','mermaid-empty'); continue id := `mermaid-${Date.now()}-${index}`
el.innerHTML = ''
mermaid.render (id, source)
.then (({svg}) => { el.innerHTML = svg; el.classList.add ('mermaid-processed') })
.catch (err => { el.classList.add ('mermaid-error'); el.textContent = source })

On failure the original source is restored so it remains debuggable. Mermaid must be initialised exactly once, from the
renderer module (currently it is initialised in
`messageSlice.ts` *and* `index.tsx` with conflicting `startOnLoad`; see §20.3).

### 10.3 MathJax v3

Configured **before** the script loads, via `window.MathJax`:

tex: { inlineMath:  [['$','$'], ['\\ (','\\)']], displayMath: [['$$','$$'], ['\\[','\\]']], processEscapes: true,
processEnvironments: true, tags: 'ams' } options: { skipHtmlTags: ['script','noscript','style','textarea','pre','code'],
ignoreHtmlClass: 'tex2jax_ignore', processHtmlClass: 'tex2jax_process', renderActions: { addMenu: [0,'',''] } // menu
disabled } svg: { fontCache: 'global' } startup.ready: default ready, then dispatch window event 'mathjax-ready'

Script: `https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js`, async, `id="MathJax-script"`.

Typeset routine:

if (!window.MathJax?.typesetPromise) -> wait for 'mathjax-ready' (plus a 1000 ms poll fallback in case the event already
fired), then retry window.MathJax.typesetClear?. ([container])     // allow re-processing after re-render
window.MathJax.typesetPromise ([container]).catch (log)

`skipHtmlTags` includes `pre`/`code`, which is what keeps Prism and MathJax from fighting over code blocks.

---

## 11. Composer (input area)

### 11.1 Behaviour

- Submit on `Enter`; newline on `Shift+Enter`; submit disabled while a send is in flight, while the message is blank, or
  while disconnected.
- Sends the raw text (no `!` prefix).
- Clears only after the send resolves.
- Autofocus on mount and after expanding from collapsed state.
- Collapsible: a chevron toggles between the full composer and a one-line
  "Click to expand input" placeholder.
- Live markdown **preview** toggle; preview re-runs Prism.
- Connection-lost banner: `"Connection lost. Reconnecting… (Your message will be preserved)"`. The draft must survive
  reconnects.

### 11.2 Conditional hiding

hidden := appConfig.inputCnt > 0 && renderedMessageCount > appConfig.inputCnt

This implements "single-shot" apps that accept a fixed number of user turns.

### 11.3 Markdown toolbar

Insertions operate on the current selection, substituting it for `$1` (or the literal
`text` when the selection is empty), then re-selecting the inserted body:

Heading      `# $1`
Bold         `**$1**`
Italic       `*$1*`
Inline code  `` `$1` ``
Code block   ```` ```\n$1\n``` ````
Bullet       `- $1`
Quote        `> $1`
Task         `- [ ] $1`
Link         `[$1](url)`
Image        `![$1](image-url)`
Table 3×3 GFM table template

Icon set and toolbar chrome are a UI concern (§19.3).

---

## 12. App configuration (`appInfo`)

Request (once per session, memoised by an in-flight promise):

GET `${baseUrl}appInfo?session=${sessionId}`      Accept: application/json

where `baseUrl` is `REACT_APP_API_URL` or `location.origin + location.pathname`, normalised to a trailing slash.

Validation: non-2xx ⇒ throw; `Content-Type` must include `application/json` or `text/json`.

On failure, log and continue with defaults:

{ applicationName: 'Chat App', inputCnt: 0, stickyInput: true, loadImages: true, showMenubar: true }

Recognised keys and effects:

| Key               | Effect                                                                    |
|-------------------|---------------------------------------------------------------------------|
| `applicationName` | sets `document.title`                                                     |
| `inputCnt`        | composer hide threshold (§11.2)                                           |
| `stickyInput`     | composer pinned to viewport bottom                                        |
| `loadImages`      | image loading toggle                                                      |
| `showMenubar`     | `false` ⇒ hide `#toolbar`/`#namebar`, reposition `#main-input`/`#session` |
| `websocket`       | partial transport config override                                         |

In non-development builds, transport config is derived from `location` and is **not**
user-overridable; in development it may be persisted to `localStorage['websocketConfig']`.

---

## 13. Archive mode

Detection is currently inconsistent (`utils/constants.ts` hardcodes `false`;
`services/appConfig.ts` checks `pathname.includes('/archive/')`). The port must have a single source of truth:

isArchive := document.documentElement.hasAttribute ('data-archive')
|| location.pathname.includes ('/archive/')

When archived:

- Do **not** open a WebSocket; do **not** fetch `appInfo`.
- Hydrate the store from `JSON.parse(document.getElementById('archived-messages').textContent || '[]')`
  exactly once; parse failure logs and yields no messages.
- Add body class `archive-mode`, which (per CSS) hides the composer and all
  `.websocket-dependent` nodes, disables `.interactive-element` pointer events, and injects a banner: "This is an
  archived version of the chat".
- Do not attach the message-action click handler (§7); tabs, collapsibles, Prism, Mermaid and MathJax all remain active.

---

## 14. Modals

### 14.1 URL construction

base := `${location.protocol}//${location.hostname}:${location.port}`
url  := endpoint.startsWith ('/')
? base + endpoint
: base + location.pathname + endpoint // session propagation if endpoint endsWith '/':  url += sessionId + '/' else:
url += (endpoint.includes ('?') ? '&' : '?') + 'sessionId=' + sessionId

### 14.2 Open flow

1. show modal shell with title = endpoint
2. set body to `<div class="loading">Loading...</div>`
3. if endpoint === 'fileIndex/':
   requestAnimationFrame -> body = <iframe src=url style="width:90vw;height:80vh;border:none" title="File Index">
   (sandboxed; no Prism pass)
   else:
   fetch (url, { mode:'cors', credentials:'include', headers:{Accept:'text/html,application/json, */*'} })
   non-ok -> throw `HTTP error! status: <n>`
   ok -> requestAnimationFrame -> body = text; Prism.highlightAllUnder ('.modal-content')
   catch -> body = `<div class="error">Error loading content: <msg><br><br>Attempted URL: <url></div>`

Modal HTML may itself contain tabs/collapsibles/math; the port should run the §6 pipeline scoped to the modal root after
injection (the React version only ran Prism — see §20.5).

Clicking the overlay closes; clicks inside the content must `stopPropagation`. Modal chrome/styling is a UI concern
(§19.3).

---

## 15. Verbose mode

Server HTML marks optional detail with any class matching `verbose` (`[class*="verbose"]`).

Transform applied during render: each matching element is wrapped in

  <span class="verbose-wrapper"> ... </span>          // hidden (display:none)
  <span class="verbose-wrapper verbose-visible"> ... </span>   // display:inline

Toggle: `Ctrl/Cmd + Shift + V`, debounced 250 ms. Also togglable from the menubar. Persisted in
`localStorage['verboseMode']` as `"true"`/`"false"`. Toggling also flips body class `verbose-mode`. Changing it re-runs
the render + pipeline.

The wrapper approach (rather than a pure CSS rule) exists because the server-emitted classes are unpredictable; keep it,
but prefer toggling a single ancestor class over rewrapping the DOM on every toggle.

---

## 16. Logging & error-handling conventions

- Bracketed subsystem prefixes: `[WebSocket]`, `[TabSystem]`, `[MessageList]`, `[Mermaid]`,
  `[MathJax]`, `[AppConfig]`, `[Modal]`, `[InputArea]`.
- `debug` output gated behind a build-time development flag; `warn`/`error` always emitted.
- Every subsystem keeps error counters (setup/restore/save/update) and tab diagnostics (`saveCount`, `restoreCount`,
  `restoreSuccess`, `restoreFail`) — retain these; they are the only observability into tab-state bugs.
- Structured second argument (object) for anything with context: ids, versions, counts.
- Renderer failures (Mermaid/MathJax/Prism) are **non-fatal**: mark the element, keep the source, log a warning,
  continue.
- A top-level error trap must render a fallback panel with the message (plus stack in development) rather than a blank
  page.

---

## 17. Target module layout (ES6)

src/ main.js // bootstrap (§1.2)
core/ session.js // §2 transport.js // §3 — the single reconnect authority protocol.js // §3.4/§3.5 frame encode/decode
store.js // §4 message store, versioning, ref index bus.js // tiny EventTarget-based pub/sub render/ message-list.js //
§4.3 render loop + delegated clicks (§7)
references.js // §5 pipeline.js // §6 debounced post-processing orchestrator tabs.js // §8 collapsible.js // §9
prism.js // §10.1 mermaid.js // §10.2 mathjax.js // §10.3 verbose.js // §15 ui/ composer.js // §11 modal.js // §14
menubar.js // deferred, §19.2 theme.js // deferred, §19.1 config/ app-config.js // §12 archive.js // §13 util/
debounce.js // leading-edge-off, trailing debounce dom.js logger.js // §16

Dependency rule: `core/` must not import from `render/` or `ui/`. `render/` may import
`core/`. Cross-cutting notifications go through `core/bus.js`.

Retained third-party dependencies: `prismjs`, `mermaid`, MathJax (CDN), a markdown renderer for the composer preview
(e.g. `marked` + `remark-gfm`-equivalent). Dropped: react, react-dom, react-redux, @reduxjs/toolkit, styled-components,
@mui/icons-material, react-markdown.

---

## 18. Conformance checklist

Protocol:

- [ ] Connects to the correct path prefix and port, with `sessionId` and `lastMessageTime`.
- [ ] Parses `id,version,content` with commas/newlines/HTML inside `content`.
- [ ] Ignores `pong`/`connect`; answers `ping` with `pong`; sends heartbeats every 30 s.
- [ ] Emits `!id,action` and `!id,userTxt,<encoded>` exactly.
- [ ] Queues sends while disconnected and flushes in order after reconnect.
- [ ] Backoff 1s→30s, 5 attempts, then terminal error; forced close suppresses reconnect.
- [ ] Cold-start replay batches (10 s window, 1 s idle flush); steady-state 100 ms aggregation in chunks of 10.

Store/rendering:

- [ ] Same-id newer version replaces in place without reordering.
- [ ] `z*` messages never render standalone; empty-content messages never render.
- [ ] Nested `z*` references expand transitively; cycles terminate; missing refs show
  `.reference-error`.
- [ ] Updating a `z*` message updates every host that embeds it.

Tabs:

- [ ] Nested tabs work; clicking an inner tab does not change the outer tab.
- [ ] Active tab survives a full HTML replacement of the containing message.
- [ ] Containers without ids get generated ids and still persist state.
- [ ] Panes are matched only among direct children.
- [ ] Code/diagrams/math inside a newly revealed pane render correctly.

Renderers:

- [ ] Each code block highlighted exactly once (no double-tokenising on re-render).
- [ ] Mermaid diagrams render once, survive tab switches, and show source on error.
- [ ] `$…$`, `$$…$$`, `\(…\)`, `\[…\]` typeset; math inside `pre`/`code` left alone.
- [ ] Re-render of a message re-typesets it (`typesetClear` called).

Behavioural:

- [ ] Collapsibles toggle, glyph tracks state, no duplicate listeners after re-render.
- [ ] `Ctrl/Cmd+Shift+V` toggles verbose; state persists across reloads.
- [ ] Composer hides once `inputCnt` is exceeded; drafts survive reconnects.
- [ ] `fileIndex/` opens as an iframe; other modals fetch inline with credentials.
- [ ] Archive pages render fully with no socket and no composer.

---

## 19. Code map for follow-up spec extraction

These areas are deliberately excluded from the primary port pass. Each entry lists the files to read and the questions
the extracted spec must answer.

### 19.1 Theme support

Files:

themes/themes.ts // colour themes + layout themes + baseTheme themes/ThemeProvider.tsx // CSS custom-property injection,
Prism theme swap themes/index.ts types/theme.ts // ThemeColors / ThemeSizing / ThemeTypography / ThemeLogging
styled.d.ts, types/styled.d.ts hooks/useTheme.ts store/slices/uiSlice.ts // theme + layoutTheme state & persistence
store/slices/configSlice.ts // theme config, loadSavedTheme, isValidTheme services/appConfig.ts // themeStorage
(localStorage['theme'])
styles/GlobalStyles.ts // :root fallbacks, list/heading/code/spinner rules styles/prism.css App.css, index.css,
components/MessageList.css components/Menu/ThemeMenu.tsx // selector UI + Alt/Ctrl+T modal

Must produce:

1. The complete **CSS custom-property contract** actually consumed by stylesheets:
   `--theme-{background,text,text-secondary-color,surface,primary,secondary,warning,success,info,border,disabled,hover,primary-dark,shadow-small/medium/large,text-on-primary/secondary/error}`,
   `--font-{primary,heading,mono,display}`, `--font-weight-*`, `--font-size-{xs..2xl}`,
   `--line-height-*`, `--letter-spacing-*`, `--spacing-{xs..xl}`, `--border-radius-{sm,md,lg}`,
   `--console-max-height`.
2. The two orthogonal axes: **colour theme**
   (`default|main|night|forest|pony|alien|sunset|ocean|cyberpunk|synthwave|paper`) × **layout theme**
   (`default|compact|spacious|ultra-compact|content-focused`), including the composite body classes
   `theme-color-<x> theme-layout-<y>` and composite name `<x>-<y>`.
3. The **Prism theme map** (`main→prism`, `night→prism-dark`, `forest→prism-okaidia`,
   `pony/sunset→prism-twilight`, `alien/cyberpunk/synthwave→prism-tomorrow`,
   `ocean→prism-okaidia`, `default/paper→prism`) and the re-highlight + collapsible re-init required after a swap.
4. Persistence keys (`localStorage['theme']`, `['layoutTheme']`) and the quota-exceeded fallback that clears storage
   while preserving
   `['theme','layoutTheme','verboseMode']`.
5. Validation/fallback rules (unknown colour theme → `main`, unknown layout → `default`) and the fact that
   `isValidTheme` in `configSlice`/`appConfig` is missing the newer themes.
6. How the single injected `<style>` element is created/replaced/removed, and how to reproduce it without a React
   effect.

### 19.2 Menubar support

Files:

* components/Menu/Menu.tsx // container, dropdowns, home link, session menu 
* components/Menu/ThemeMenu.tsx // colour + layout selectors, keyboard shortcut 
* components/Menu/WebSocketMenu.tsx // dev-only transport config + status indicator
* components/Menu/index.ts hooks/useModal.ts // openModal / getModalUrl 
* store/slices/uiSlice.ts // modalOpen/modalType/modalContent, verboseMode 
* store/slices/configSlice.ts // showMenubar + applyMenubarConfig (legacy DOM ids)
* utils/uiHandlers.ts // Ctrl/Cmd+Shift+V, [data-modal], data-message-action

Must produce:

1. The menu tree: Home (`/`), Session → {Settings, Files (`fileIndex/`), Usage, Threads, Cancel, Show/Hide Verbose},
   Theme, Layout, and dev-only Config. Note the commented-out Share/Delete items.
2. Dropdown mechanics: `data-dropdown` attributes, single-open invariant, outside-click and
   `Escape` closing, and the `stopPropagation` discipline required so item clicks are not swallowed by the overlay.
3. Keyboard shortcuts: `Alt+T` (Windows/Linux) / `Ctrl+T` (macOS) opens a combined theme+layout modal built from raw
   HTML strings that dispatch `themeChange` /
   `layoutThemeChange` window events. Decide whether to keep this string-HTML bridge.
4. Connection status surface: indicator colours for
   `connected|disconnected|connecting|error`, reconnect attempt counter, last error text, Save/Reconnect/Disconnect
   buttons and their disabled predicates.
5. `showMenubar === false` legacy behaviour: hide `#toolbar`/`#namebar`, set
   `#main-input.style.top = 0`, and force `#session` to
   `top:0; width:100%; position:absolute`.
6. `utils/uiHandlers.ts` contains **unreachable code** after its `return` (the `[data-modal]`
   and `data-message-action` document listeners never attach). Decide whether those handlers are required and, if so,
   reinstate them exactly once (they overlap §7).

### 19.3 UI concerns

Files:

* components/InputArea.tsx // composer chrome, toolbar icons, preview, collapse 
* components/Modal/Modal.tsx // overlay/content chrome 
* components/common/Spinner.tsx // sizes small|medium|large, .sr-only label components/ErrorBoundary/*           // fallback panel, log shape 
* components/ChatInterface.tsx // layout shell, connection banner 
* components/MessageList.css // message bubbles, buttons, reply form, MathJax rules App.css, index.css, styles/GlobalStyles.ts

Must produce:

1. Message bubble taxonomy and alignment per type (`user` right/primary,
   `system` left/secondary, `error` gradient, `assistant|loading|reference` left/surface)
   plus hover elevation.
2. Class contract for server-emitted controls: `.href-link`, `.play-button`, `.regen-button`,
   `.cancel-button`, `.text-submit-button`, `.referenced-message[.expanded]`,
   `.reference-error`, `.reply-form`, `.reply-input`, `.mermaid[.mermaid-processed|.mermaid-error|.mermaid-empty]`,
   `.verbose-wrapper[.verbose-visible]`, `.expandable-{guide,header,content,section-title,description,example,footer}`,
   `.expand-icon[.expanded]`, `.tabs`, `.tabs-container`, `.tab-button[.active]`,
   `.tab-content[.active]`, `.code-display-container`, `.feedback-controls`, `.revise-form`,
   `.action-button`, `.action-status`, `.execution-result`, `.execution-error`,
   `.console-log*`, `.math-block`, `.archive-mode`, `.interactive-element`,
   `.websocket-dependent`, `.spinner-border[.small|.large]`, `.sr-only`.
3. Spinner/loading and connection-status affordances, including the `[role="status"]` +
   `.sr-only` accessibility pattern and the `contain`/`aspect-ratio` sizing trick.
4. Icon inventory currently supplied by `@mui/icons-material` and the chosen replacement (inline SVG sprite
   recommended).
5. Scroll behaviour: custom scrollbar styling, `contain: content`, smooth scrolling, and the (currently absent)
   **auto-scroll-to-bottom-unless-user-scrolled-up** policy that needs specifying.
6. Focus/keyboard accessibility: `*:focus-visible` outline rules, tab-button focus ring, heading focus styles, and
   `prefers-reduced-motion` handling.

### 19.4 Other

Files:

* store/index.ts, 
* store/slices/{connectionSlice,userSlice,configSlice}.ts 
* types/{index,messages,config,websocket,global,qrcode}.d.ts 
* utils/logger.ts, 
* utils/constants.ts 
* App.tsx (QRCode usage, Prism plugin imports, archived-message hydration) index.tsx, 
* reportWebVitals.js, 
* setupTests.js, 
* App.test.js, 
* scripts/convertLogo.js

Must produce:

1. Which state actually needs to survive the Redux removal: messages + versions, connection status, theme/layout,
   verbose, modal, app config. `userSlice`
   (name/isAuthenticated/preferences) appears unused — confirm and delete.
2. Logging configuration surface (`LoggingConfig`, `ConsoleConfig`, per-level styles,
   `maxEntries`, `persistLogs`, `minLogLevel`, include/exclude filters) — decide how much of
   `ThemeLogging`/`LoggingConfig` is live versus dead configuration.
3. Fate of the `qrcode-generator` dependency: `App.tsx` builds a QR for
   `https://example.com` and discards it. Remove or specify a real feature.
4. Test strategy: `App.test.js` still asserts "learn react" (guaranteed failure). Specify the replacement suite,
   anchored on §18, and port the console-capture harness from
   `setupTests.js` (`LOG_LEVEL` env gate, `[TEST <LEVEL>][ts]` prefixes).
5. Build/asset tooling: `reportWebVitals.js` is empty; `scripts/convertLogo.js` (sharp,
   `logo.svg` → `logo256.png`) is the only remaining build script.
6. Environment flags: `process.env.NODE_ENV` and `REACT_APP_API_URL` must be replaced with an explicit build-time config
   module.

---

## 20. Known defects to fix during the port

1. **`lastMessageTime` is bogus.** It is computed as
   `Math.max(...handlers.map(h => h.lastMessageTime || 0).filter(t => t > 0))`, which yields
   `-Infinity` with no prior messages (and the property is never set anywhere). Track the high-water mark in the store
   and send `0` when empty.
2. **Two competing reconnect loops.** `services/websocket.ts` (base 1 s, max 30 s, 5 attempts, 10 s connect timeout) and
   `hooks/useWebSocket.ts` (base 1 s, max 60 s, 10 attempts, 5 s connect timeout) both schedule reconnects, and
   `useWebSocket`'s cleanup calls
   `disconnect()` on every `sessionId`/`dispatch` change. Collapse to one authority.
3. **Renderers initialised twice with conflicting options.** `mermaid.initialize` runs in
   `store/slices/messageSlice.ts` (`startOnLoad: false`) *and* `index.tsx`
   (`startOnLoad: true`); `ThemeProvider` calls `loadPrismTheme` twice per theme change and calls `Prism.highlightAll()`
   on the whole document. Initialise once, highlight scoped.
4. **Sanitisation is a no-op.** `DOMPurify` is imported and unused; `sanitizeHtmlContent`
   only round-trips math placeholders. Either sanitise for real (preserving math) or declare server HTML trusted and
   delete the theatre.
5. **Modal content is under-processed.** Fetched modal HTML gets Prism only — no tabs, collapsibles, Mermaid, or
   MathJax. Run the §6 pipeline scoped to the modal root.
6. **`utils/uiHandlers.ts` has unreachable code** after an early `return`; the `[data-modal]`
   and `data-message-action` document listeners are never installed.
7. **`isArchive` has two contradictory definitions** (`utils/constants.ts` → `false`;
   `services/appConfig.ts` → path check). Unify per §13.
8. **Message type is derived twice, differently.** The transport stamps `type: 'response'`
   while `ChatInterface` re-derives `user|system|assistant` from the id prefix and also mangles ids
   (`${id}-${timestamp}`), which breaks version-based upsert and reference resolution. Derive once, in the store, and
   never rewrite ids.
9. **`ChatInterface` keeps a shadow `messages` state** that is written but never read.
10. **`isValidTheme` is stale** — it rejects `synthwave` and `paper`, silently downgrading saved themes to `main`.
11. **Reference re-expansion is O (all messages)** on any `z*` change. Build a reverse dependency index (§5.2).
12. **Collapsible listener flag is an expando property** (`__collapsibleListenerAttached`)
    on the element; prefer `dataset` so it survives clone/serialise round-trips.
13. **`App.test.js` asserts removed content** ("learn react") and will always fail.