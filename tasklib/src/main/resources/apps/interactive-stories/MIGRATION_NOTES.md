# interactive-stories — `/lib/app` migration notes

  * `app.html`: `./utils/marked.min.js` → `/app/marked.min.js` (still a classic
    script, since `ui.js#renderMarkdown()` looks for the global `marked`).
  * `app.js`: all six `./utils/*.js` imports rewritten to `/app/*.js`, plus a new
    `initMenu()` call from `/app/menu.js`.
  * `apps/interactive-stories/utils/` can now be deleted — nothing else in this app
    references it (`style.css` and the `ops/*.md` prompts contain no `utils/` paths).
  * No `configure()` call is needed: this deployment is mounted at the host root, so
    `config.js` falls back to `serverBase: ''`, which is the previous behaviour for
    `runDocOp` (`/docops`), `loadApiProviders` (`/apiProviders/`) and `getProxyUrl`.
    If you later move behind a reverse-proxy prefix, uncomment the
    `window.COGNOTIK_CONFIG` block in `app.html` — it must run *before*
    `<script type="module" src="app.js">`.

  ## Smoke test
  - [ ] Model dropdowns populate (`models.js` → `/apiProviders/`).
  - [ ] "Begin Story" starts a docop and `story/0.md` renders (`docops.js`, `fileIO.js`).
  - [ ] Choice A/B/C generates branches; tree refreshes.
  - [ ] Image / audio generation writes `story/<id>.png` / `.wav` and reloads inline.
  - [ ] Session/monitor links resolve (`sessionLinks.js` + `getProxyUrl`).
  - [ ] Read-aloud + sentence highlighting still work (local to `app.js`).
  - [ ] Stylesheet updater targets `style.css` and reports COMPLETED.
  - [ ] Menubar renders; if your CSP forbids inline styles, allow `style-src 'unsafe-inline'`
        or ship the menu CSS in `style.css` and skip the injected block.