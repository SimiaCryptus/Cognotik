# Empty Project

A blank Cognotik workspace. There is no source code here yet — just the **Dev Tools** catalog, a set of dependency-light
HTML pages that drive Cognotik doc-op pipelines.

Start from `app.html`. It is a thin shim that forwards to
`cognotik-tools/index.html`, where the real catalog lives.

## Layout

    app.html                      redirect shim + no-JS fallback listing
    cognotik-tools/
      index.html                  the catalog UI (search, filters, README viewer)
      apps.js                     the catalog *data* — meta, categories, tools
      <tool>/index.html           one folder per registered tool
      <tool>/README.md            rendered in place by the catalog

Everything is served straight from this folder: no build step, no bundler, no `npm install`.

## The tools

| Tool                      | Category | What it does                                                                    |
|---------------------------|----------|---------------------------------------------------------------------------------|
| 🌱 Greenfield Implementer | Plan     | One paragraph of product idea → spec, stack, architecture, phases, build tasks. |
| 🔍 Reviewer               | Review   | Focused code review: findings → follow-up tasks → generated doc-ops.            |
| 🧭 Coder                  | Review   | Research an existing codebase, then drive changes from the answers.             |
| 🔨 Builder                | Build    | Research how a repo builds, then generate and repair one standard `build.sh`.   |
| 🗂️ Issues                 | Track    | A session-local issue tracker backed by a single `issues.json`.                 |

Since this project starts empty, **Greenfield Implementer** is usually the right entry point: it plans a project from
scratch and writes real source under the `target_root` chosen by its stack plan.

## Using the catalog

* `/` focuses the search box; `Enter` opens the first result.
* Category chips and tag chips are cumulative filters; **Clear filters** resets them.
* Filter state lives in the URL hash (`#q=…&cat=…&tags=…`), so views are shareable.
* **Grid** / **List** toggles the layout; the choice is remembered in `localStorage`.
* **README** renders a tool's markdown docs in a modal instead of navigating away.
* A card whose entry point returns 404 is dimmed and badged *not found* rather than hidden — the catalog is the source
  of truth, a missing file is a deployment problem worth seeing.

## Adding a tool

Adding a tool is a data change. Drop the page under `cognotik-tools/<id>/`
and append one object to the `tools` array in `cognotik-tools/apps.js`:

    {
        id: 'my-tool',                        // stable slug, used for deep links
        name: 'My Tool',
        icon: '✨',                            // a single emoji
        category: 'plan',                     // an id from `categories`
        status: 'experimental',               // stable | beta | experimental
        tagline: 'One line, shown under the title.',
        description: 'One short paragraph, shown in the card body.',
        entry: 'my-tool/index.html',          // relative to apps.js; null for op-only tools
        docs: 'my-tool/README.md',            // optional, rendered in place
        tags: ['doc-ops', 'json-schema'],
        pipeline: ['stage one', 'stage two'], // optional
        artifacts: [{path: 'my-tool/out/', note: 'what it generates'}],
        requires: ['/app/docops.js', '/app/fileIO.js'],
        storage: ['myTool_state']
    }

If it does not fit an existing bucket, add an object to `categories` first (`id`, `name`, `accent`, `blurb`). Nothing in
`index.html` or `app.html`
needs to change.

## Runtime dependencies

Most tools expect the host to serve a few shared modules:

* `/app/docops.js` — `runDocOp`, `waitForTask`, `createStatusPoller`
* `/app/fileIO.js` — `readFile`, `writeFile`, `listFiles`, `deleteFile`
* `/lib/marked.min.js` — markdown rendering (optional; falls back to `<pre>`)
* `/lib/purify.min.js` — HTML sanitising (optional)

Doc-op paths and status locations are derived from the page URL, so keep a tool's `folder:` front-matter and its
`ROOT_HOPS` constant in sync if you move it to a different depth.

## House rules

* **Schema-first** — stages emit JSON conforming to a code-defined schema, never prose.
* **One document per unit** — per file, per package, per phase; small contexts, parallel runs.
* **Advisory, not authoritative** — dependencies and readiness are hints, never gatekeepers.
* **Nothing implicit** — roots, status paths and generated op files are derived and displayed.
* **Docs live beside the tool** — READMEs are markdown files rendered in place, not a wiki.