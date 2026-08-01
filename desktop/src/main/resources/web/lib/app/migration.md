---
specifies: **/app.js
---

# Migration: `<app>/utils/*.js` → `/lib/app/*.js`

NOTE: The previous `utils` directory is being removed.

The shared front-end modules previously lived next to each application
(`<app>/utils/<module>.js`) and were duplicated per app. They now live in **one** place, served from the
host-absolute path:

```
/lib/app/<module>.js
```

This document describes what changes for app pages, what the "fileserver basis" problem is, and how it is
solved by the new `config.js` module.

---

## 1. What changed

| Before                                | After                     |
|---------------------------------------|---------------------------|
| `<app>/utils/docops.js`               | `/lib/app/docops.js`      |
| `<app>/utils/fileIO.js`               | `/lib/app/fileIO.js`      |
| `<app>/utils/git.js`                  | `/lib/app/git.js`         |
| `<app>/utils/marked.min.js`           | `/lib/app/marked.min.js`  |
| `<app>/utils/models.js`               | `/lib/app/models.js`      |
| `<app>/utils/session.js`              | `/lib/app/session.js`     |
| `<app>/utils/sessionLinks.js`         | `/lib/app/sessionLinks.js`|
| `<app>/utils/ui.js`                   | `/lib/app/ui.js`          |
| `<app>/utils/usage.js`                | `/lib/app/usage.js`       |
| —                                     | `/lib/app/config.js` *(new)* |
| —                                     | `/lib/app/menu.js` *(new)*   |

### Import updates

```diff
- import { runDocOp, waitForTask } from './utils/docops.js';
- import { readFile, writeFile }   from './utils/fileIO.js';
+ import { runDocOp, waitForTask } from '/lib/app/docops.js';
+ import { readFile, writeFile }   from '/lib/app/fileIO.js';
```

```diff
- <script src="/lib/marked.min.js"></script>
+ <script src="/lib/marked.min.js"></script>
<script type="module" src="app.js"></script>
```

> Intra-library imports (e.g. `git.js` importing `./ui.js`) are **unchanged** — they are relative to
> `/lib/app/` and continue to resolve correctly.

---

## 2. The "fileserver basis" problem

An app page is served from a *session file index*, e.g.:

```
https://hosted.cognotik.com/presentation-creator/fileIndex/U-20260801-TH2s9UEo/app.html
```

while the library is served from the *host root* (`/lib/app/...`). Three different notions of "base" are in
play:

| Base            | Meaning                                                   | How it is obtained                     |
|-----------------|-----------------------------------------------------------|----------------------------------------|
| **Library base**| Where the JS modules live                                 | Fixed: `/lib/app/`                     |
| **Session base**| Read/write root for the session's files                   | `parseSessionUrl().basePath` (from URL)|
| **Server base** | Prefix for absolute API endpoints (`/docops`, `/proxy/…`) | **Was assumed to be `''`**             |

* **Session base is safe.** `session.js` derives `basePath`, `sessionId`, and `appId` from
`window.location.pathname`, so relocating the *modules* does not change it. `fileIO.js` and `git.js`
build every URL from that `basePath`, so they keep working unchanged.
* **Server base is not safe.** `docops.js` (`/docops`), `models.js` (`/apiProviders/`) and `usage.js`
(`/proxy/usage`) hard-coded host-absolute paths. That works only when the app server is mounted at the
host root. Now that a single library instance is shared by every app (and potentially by deployments
behind a reverse-proxy path prefix), this must be configurable.

### Answer: yes — a variable may need to be set from the HTML page

A new module, `config.js`, resolves the server base (and a few optional overrides) in this order:

1. `configure({ serverBase: '…' })` called at runtime from your page script.
2. `window.COGNOTIK_CONFIG` — set by the HTML page *before* the module script runs.
3. `<meta name="cognotik-server-base" content="…">`.
4. `<html data-server-base="…">`.
5. Default: `''` (host root) — **identical to the previous behaviour**, so existing apps need no change.

All of `docops.js`, `models.js`, `usage.js`, and `session.js#getProxyUrl` now route their absolute
endpoints through `serverUrl(path)`.

#### Only required when *not* deployed at the host root

```html
<!-- app.html -->
<script>
window.COGNOTIK_CONFIG = {
serverBase: '/cognotik',              // reverse-proxy prefix, no trailing slash
// sessionsEndpoint: '/cognotik/api/sessions?format=json',  // optional, used by menu.js
// ideUrlTemplate: '{appRoot}/ui/?session={sessionId}#/'    // optional
};
</script>
<script src="/lib/marked.min.js"></script>
<script type="module" src="app.js"></script>
```

or, equivalently, via meta tags:

```html
<meta name="cognotik-server-base" content="/cognotik">
```

or from JS:

```js
import { configure } from '/lib/app/config.js';
configure({ serverBase: '/cognotik' });
```

#### When the page cannot be parsed for session context

If a page lives outside the standard `…/fileIndex/<session>/…` layout (custom landing pages, embedded
views), set the context explicitly instead of relying on URL parsing:

```js
configure({
appId: 'presentation-creator',
sessionId: 'U-20260801-TH2s9UEo',
basePath: '/presentation-creator/fileIndex/U-20260801-TH2s9UEo'
});
```

`menu.js` honours these overrides; `session.js#parseSessionUrl()` intentionally still derives its values
purely from the URL for backwards compatibility.

---

## 3. Caching, CSP and CORS notes

* **Same origin required.** ES modules and `fetch()` calls in this library assume the library and the app
are on the same origin. Do **not** host `/lib/app/` on a separate CDN domain unless you also add CORS
headers *and* `crossorigin` attributes.
* **CSP.** `script-src 'self'` is sufficient; nothing is loaded from a CDN. `menu.js` injects a `<style>`
element, so `style-src` must allow `'unsafe-inline'` **or** you should ship the menu CSS in your app
stylesheet and skip the injected block.
* **Cache-busting.** Because one copy is now shared by all apps, a bad deploy affects everything. Serve
`/lib/app/*` with a short `max-age` plus `ETag`, or version the path (`/lib/app/v1/...`) for pinned apps.
* **`marked` is still vendored** (`/lib/app/marked.min.js`) and still loaded as a classic script, not a
module — `ui.js#renderMarkdown()` looks for the global `marked`.

---

## 4. Backwards-compatibility shim (optional)

To migrate apps one at a time, leave a stub behind at the old location:

```js
// <app>/utils/docops.js  (deprecated shim)
export * from '/lib/app/docops.js';
```

Remove the shims once every page imports from `/lib/app/`.

---

## 5. Per-app migration checklist

- [ ] Replace `./utils/<module>.js` imports with `/lib/app/<module>.js`.
- [ ] Replace `<script src="/lib/marked.min.js">`.
- [ ] Delete the app-local `utils/` directory (or replace with re-export shims).
- [ ] If the deployment is **not** at the host root, set `window.COGNOTIK_CONFIG.serverBase` (or a meta tag).
- [ ] Add the shared menubar: `import { initMenu } from '/lib/app/menu.js'; initMenu({ appName: '…' });`
- [ ] Smoke-test: markdown preview renders, docops run starts, proxy/monitor links resolve, git panel loads,
  usage totals populate.