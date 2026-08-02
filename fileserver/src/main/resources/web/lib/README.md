# Vendored web libraries

Everything the embedded web UI loads at runtime lives here, so the server works with **no CDN and no network
access**. The third-party files are **generated** by `download.js` – do not edit them by hand; re-run the
downloader instead and commit the resulting diff (including `manifest.json`).

> Not generated / hand-written (safe to edit): `app/**`, `monaco.js`, this `README.md` and `download.js` itself.
> `download.js` only ever writes the files listed in [Layout](#layout) plus `manifest.json`.

## Requirements

* Node.js **18 or newer** (only core modules are used – no `npm install`, no `package.json`).
* Outbound HTTPS to `cdn.jsdelivr.net` and `data.jsdelivr.com` (except for `--check`, which is offline).

## Quick start

```sh
cd fileserver/src/main/resources/web/lib

node download.js               # fetch anything that is missing
node download.js --force       # refresh to the latest patch release of each pinned major
node download.js --fonts       # also vendor the MathJax CHTML web fonts
node download.js --check       # offline: verify local files against manifest.json
node download.js --list        # print the resolved URLs without downloading
node download.js --tags        # print the <script>/<link> snippet to paste into the HTML
```

Existing files are hashed and left alone unless `--force` is given, so re-running the plain command is cheap
and idempotent. A full download is ~84 files / ≈16 MiB (107 files / ≈17 MiB with `--fonts`).

### Exit codes

| Code | Meaning                                                    |
|-----:|------------------------------------------------------------|
|  `0` | success (all files downloaded / up to date / verified)      |
|  `1` | one or more files failed to download or failed verification |
|  `2` | bad usage, or an unexpected error                           |

## CLI reference

| Option              | Default            | Description                                                      |
|---------------------|--------------------|------------------------------------------------------------------|
| `--out=DIR`         | script directory   | Target directory (`--out-dir` is an alias).                       |
| `--only=A,B`        | –                  | Restrict to the given groups **or** package names.                |
| `--fonts`           | off                | Include the optional MathJax CHTML fonts.                         |
| `-f`, `--force`     | off                | Re-download files that already exist.                             |
| `-n`, `--dry-run`   | off                | Show the plan, write nothing (manifest is **not** rewritten).     |
| `--check`           | off                | Offline SHA-256 verification against `manifest.json`.             |
| `--list`            | off                | List resolved URLs → destinations and exit.                       |
| `--tags`            | off                | Print the HTML include snippet and exit.                          |
| `--no-resolve`      | off                | Keep the major range in the URL instead of pinning an exact version. |
| `--concurrency=N`   | `6`                | Parallel downloads.                                               |
| `--timeout=MS`      | `30000`            | Per-request timeout (min 1000).                                   |
| `--retries=N`       | `3`                | Retries per file (exponential backoff + jitter; 4xx other than 429 are not retried). |
| `-q`, `--quiet`     | off                | Only errors and the final summary.                                |
| `-h`, `--help`      | –                  | Usage text.                                                       |

⚠️ **`--only` and the manifest:** every non-dry run rewrites `manifest.json` with *only* the assets of that
run. Use `--only` for quick experiments, but do a full run (`node download.js` – add `--fonts` if the fonts are
vendored) before committing, otherwise `--check` will stop covering the rest of the tree.

## Asset groups

`--only` accepts either the group or the npm package name.

| Group       | Package         | Pinned  | Contents                                              |
|-------------|-----------------|---------|-------------------------------------------------------|
| `mermaid`   | `mermaid`       | `11`    | `mermaid.min.js`                                       |
| `marked`    | `marked`        | `15`    | `marked.min.js`                                        |
| `dompurify` | `dompurify`     | `3`     | `purify.min.js`                                        |
| `mathjax`   | `mathjax`       | `3`     | `mathjax/tex-mml-chtml.js`                             |
| `prismjs`   | `prismjs`       | `1`     | core, `prism-tomorrow` theme, 21 languages, 3 plugins  |
| `monaco`    | `monaco-editor` | `0.52`  | AMD loader, editor bundle, 38 tokenizers, 4 workers    |
| `fonts`     | `mathjax`       | `3`     | 23 CHTML `.woff` fonts (**optional**, needs `--fonts`) |

Majors are resolved to an exact version at run time via
`https://data.jsdelivr.com/v1/packages/npm/<pkg>/resolved?specifier=<range>`; the resolved version is recorded
in `manifest.json`. Monaco has no stable major yet, so the **minor** is pinned – a `0.53` bump must be
deliberate.

## Layout

```
lib/
manifest.json                       # generated: versions, sizes, SHA-256, SRI
download.js                         # this downloader
README.md
marked.min.js
mermaid.min.js
purify.min.js
mathjax/
  tex-mml-chtml.js
  output/chtml/fonts/woff-v2/*.woff # only with --fonts
prism/
  prism.min.js
  prism-tomorrow.min.css
  components/prism-<lang>.min.js
  plugins/prism-<plugin>.min.(js|css)
monaco/
  vs/                               # keep the package layout: the AMD loader resolves against it
    loader.js
    editor/editor.main.(js|css|nls.js)
    base/worker/workerMain.js
    base/browser/ui/codicons/codicon/codicon.ttf
    basic-languages/<lang>/<lang>.js
    language/{json,css,html,typescript}/*(Mode|Worker).js
monaco.js                           # hand-written bootstrap for the editor (not generated)
app/                                # hand-written UI modules (not generated)
```

## Including them in a page

`node download.js --tags` prints a ready-to-paste snippet for the current asset set:

```html
<link rel="stylesheet" href="lib/prism/prism-tomorrow.min.css">
<script src="lib/mermaid.min.js" defer></script>
<script src="lib/marked.min.js" defer></script>
<script src="lib/purify.min.js" defer></script>
<script src="lib/mathjax/tex-mml-chtml.js" defer></script>
<script src="lib/prism/prism.min.js" defer></script>

<!-- Monaco: the AMD loader must NOT be deferred so require.config() can run right after it -->
<script src="lib/monaco/vs/loader.js"></script>
<script>
  require.config({paths: {vs: 'lib/monaco/vs'}});
  self.MonacoEnvironment = {getWorkerUrl: () => 'lib/monaco/vs/base/worker/workerMain.js'};
  require(['vs/editor/editor.main'], () => { /* window.monaco is ready */ });
</script>
```

Notes:

* Paths are **relative** (`lib/…`). Use `/lib/…` instead if the page is not served from the context root.
* Prism components/plugins are not emitted as tags – load only the ones a page needs, *after* `prism.min.js`.
* Everything except the Monaco loader is deferred, so order between those tags does not matter.
* Want Subresource Integrity? Copy the `integrity` value of the file from `manifest.json`
(`integrity="sha384-…"`); it is recomputed on every download.

## Monaco specifics

* Monaco ships as an **AMD** bundle. Only `min/vs/loader.js` goes into the HTML; the loader pulls in
`editor.main.js`, `editor.main.css`, the NLS strings, tokenizers and workers at runtime – which is why the
on-disk layout under `monaco/vs` must mirror the npm package exactly.
* Web workers: `workerMain.js` is the generic host. `getWorkerUrl` above points every language worker at it;
the loader then loads `language/<x>/<x>Worker.js` inside the worker. Serving the whole tree from the same
origin avoids the cross-origin worker shim entirely.
* `codicon.ttf` is referenced from `editor.main.css` – make sure the server sends it (`font/ttf`) and, if you
use CSP, allow `font-src 'self'` plus `worker-src 'self'` / `script-src 'self'`.
* `tsWorker.js` is ~5.7 MiB (it embeds the TypeScript compiler). Drop `typescript` from
`MONACO_LANGUAGE_SERVICES` in `download.js` if you only need syntax highlighting for TS.

## MathJax fonts

`tex-mml-chtml.js` lazily loads its CHTML web fonts. Either vendor them:

```sh
node download.js --fonts        # → mathjax/output/chtml/fonts/woff-v2/*.woff
```

MathJax looks for them next to the script by default, which matches the layout above. Alternatively, point
MathJax at the CDN *before* the script tag:

```html
<script>
  window.MathJax = {chtml: {fontURL: 'https://cdn.jsdelivr.net/npm/mathjax@3/es5/output/chtml/fonts/woff-v2'}};
</script>
```

## `manifest.json`

Records the resolved version, byte size, SHA-256 and SRI (`sha384-…`) hash of every file, so upgrades are
reviewable in a diff and reproducible:

```json
{
"generatedAt": "2026-08-02T17:21:14.755Z",
"cdn": "https://cdn.jsdelivr.net/npm",
"packages": {
  "prismjs": {"specifier": "1", "version": "1.30.0"}
},
"assets": [
  {
    "name": "prismjs",
    "group": "prismjs",
    "file": "prism/prism.min.js",
    "url": "https://cdn.jsdelivr.net/npm/prismjs@1.30.0/prism.min.js",
    "version": "1.30.0",
    "bytes": 19683,
    "sha256": "ed5ea2ce…",
    "integrity": "sha384-Cn/s7dpCMIb2rgIjtCYcpcv3LPJjUciybJ5G/sGMK025lFiqdJ4pRgUEgIcolGuJ"
  }
]
}
```

Entries are sorted by `file`, so diffs stay stable across runs. `generatedAt` changes on every run – that is
the only expected churn when nothing else moved.

### Verifying in CI

```sh
node src/main/resources/web/lib/download.js --check --quiet \
--out=src/main/resources/web/lib
```

`--check` needs no network: it re-hashes every file listed in the manifest and reports `MISMATCH` / `MISSING`
lines, exiting `1` if anything is off. Run it after checkout to catch truncated or accidentally edited
vendored files.

## Upgrading

1. **Patch/minor within the pinned range** – `node download.js --force`, review the `manifest.json` diff,
smoke-test the UI, commit.
2. **Major version** – edit the `VERSIONS` map at the top of `download.js` (and `PRISM_THEME`,
`PRISM_LANGUAGES`, `PRISM_PLUGINS`, `MONACO_*` if the package layout changed), then
`node download.js --force`. Check the upstream changelog for renamed paths – a wrong `src` shows up as a
`FAILED … HTTP 404` line, which is never retried.
3. **Adding a Prism language/plugin** – append to `PRISM_LANGUAGES` / `PRISM_PLUGINS`
(`['name', hasCss]`), run `node download.js`, add the `<script>` tag to the page.
4. **Adding a Monaco language** – append to `MONACO_BASIC_LANGUAGES` (tokenizer only) or
`MONACO_LANGUAGE_SERVICES` (mode + worker); no HTML change is needed, the loader picks it up.

Always re-run a full download (plus `--fonts` if applicable) before committing so the manifest covers
everything, and use `--dry-run`/`--list` first if you just want to see what a change would fetch.

## Air-gapped environments and proxies

* The downloader talks HTTP(S) directly and **ignores** `HTTP_PROXY`/`HTTPS_PROXY`. On a proxied network,
download on a connected machine and copy `lib/` over, then validate with `--check`.
* To mirror from a different host, either change `CDN`/`DATA_API` in `download.js`, or run with
`--no-resolve` so the URLs keep the plain major range.

## Troubleshooting

| Symptom                                                | Fix                                                                                      |
|--------------------------------------------------------|------------------------------------------------------------------------------------------|
| `FAILED … HTTP 404`                                    | Path moved upstream – fix the `src` entry in `download.js`.                                |
| `could not resolve <pkg>@<range>` warning              | data.jsdelivr.com unreachable; the range is used verbatim and the manifest records it.     |
| `MISSING` in `--check`                                 | File never downloaded (e.g. fonts) – re-run the downloader, or regenerate the manifest.    |
| `MISMATCH` in `--check`                                | File was edited/truncated – `node download.js --force --only=<group>`.                     |
| `timeout after 30000 ms`                               | Raise `--timeout` and/or lower `--concurrency`; large Monaco/Mermaid bundles are slow.     |
| Monaco loads but is unstyled / icons are boxes         | `editor.main.css` or `codicon.ttf` is not being served from `lib/monaco/vs/...`.           |
| `Uncaught ReferenceError: require is not defined`      | The loader tag was deferred or reordered – it must run before `require.config(...)`.       |

## Licences

These are unmodified upstream builds: Mermaid (MIT), Marked (MIT), DOMPurify (Apache-2.0 / MPL-2.0),
MathJax (Apache-2.0), Prism (MIT), Monaco Editor (MIT). Keep the bundled licence banners intact.