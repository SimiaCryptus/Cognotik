# Node.js Compatibility Layer — Analysis & Plan

> **Status:** Design document. *Analysis and planning only — no implementation is
> described as done, and no code is committed as part of this document.*
>
> **Owner:** fileserver module
> **Supersedes:** the one-line placeholder previously in this file.

---

## 1. Goal

> *"We want this project to provide both an API and a programmatic drop-in wrapper so
> that Node.js scripts interacting with the filesystem can be loaded and run on a
> client/browser and interact with the served files in the same way with the same code."*

Concretely, this means three deliverables that must be designed together:

1. **A first-class remote-filesystem HTTP API** (`FS API v1`) exposed by `FileServlet`
   that is rich enough to express Node `fs` semantics (not just "GET a file / PUT a file").
2. **A drop-in `fs` implementation for the browser** (`@cognotik/fs`) that speaks that API and is behaviourally
   indistinguishable — for the supported subset — from Node's
   `node:fs`, `node:fs/promises`, and `node:path`.
3. **A loader/runtime** so that an *unmodified* Node script (`const fs = require('fs')`
   or `import fs from 'node:fs/promises'`) can be fetched from the server, resolved, evaluated in the browser, and
   transparently bound to the shim.

The acid test:

// scripts/report.js — written for Node, run unmodified in the browser const fs = require ('fs'); const path = require (
'path'); const files = fs.readdirSync ('data'); const rows = files .filter (f => f.endsWith ('.json'))
.map (f => JSON.parse (fs.readFileSync (path.join ('data', f), 'utf8'))); fs.writeFileSync ('report.md', render (rows));

This must produce byte-identical output whether executed by `node scripts/report.js`
against a local directory, or executed in the browser against the same directory served by `FileServlet`.

---

## 2. Non-goals (explicitly out of scope for v1)

| Not doing                                                                                  | Rationale                                                                                                             |
|--------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| Full POSIX fidelity (`chown`, `chmod` semantics, `symlink`, hard links, `mknod`, `statfs`) | Web clients cannot meaningfully use these; the Java layer only partially exposes them.                                |
| Running arbitrary native modules / N-API addons                                            | Impossible in-browser.                                                                                                |
| Full `net` / `http` / `dgram` server emulation                                             | Different problem; the wrapper is filesystem-oriented. Client-side `http.request` may be added later as a fetch shim. |
| Multi-user concurrent-write conflict *resolution*                                          | We will provide detection (ETag/`If-Match`); resolution is the application's job.                                     |
| Replacing the existing HTML file-browser UI                                                | FS API v1 is additive; the browser UI keeps working unchanged.                                                        |
| Executing the Node scripts *server-side*                                                   | The stated goal is client/browser execution. A server-side runner is a possible later phase.                          |

---

## 3. Current State Analysis

### 3.1 Server HTTP surface today

| Verb   | Path                                       | Behaviour                                                                              | Node-`fs` analogue                            |
|--------|--------------------------------------------|----------------------------------------------------------------------------------------|-----------------------------------------------|
| GET    | `/<seg>/<path>` (file)                     | Streams bytes, MIME from `MimeTypeResolver`, async `WriteListener`, `FileChannelCache` | `readFile` (whole-file only)                  |
| GET    | `/<seg>/<path>/` (dir)                     | Full HTML directory page                                                               | —                                             |
| GET    | `/<seg>/<path>/_files.json`                | Virtual JSON listing (`name`, `type`, `size`, `lastModified`, `mimeType`)              | `readdir({withFileTypes:true})` (approximate) |
| GET    | `?edit=1`                                  | Monaco editor page                                                                     | —                                             |
| GET    | `foo.html                                  | .pdf                                                                                   | .txt` (missing)                               | Renders sibling `foo.md` | — (a *surprise* for FS semantics) |
| HEAD   | any                                        | Content-type/length only                                                               | partial `stat`                                |
| POST   | dir (multipart, field `file`)              | Upload; **409 if exists**                                                              | `writeFile` with `flag:'wx'` only             |
| POST   | any (`gitAction=…`)                        | Git CLI wrapper, JSON reply                                                            | `child_process` (git only)                    |
| PUT    | file path                                  | Raw body write, creates parents, 200/201                                               | `writeFile` with `flag:'w'`                   |
| DELETE | file or dir                                | `delete()` / `deleteRecursively()`                                                     | `unlink` / `rm -r`                            |
| GET    | `/zip?session=&path=` (`StaticZipServlet`) | ZIP export                                                                             | —                                             |

### 3.2 Client-side JS today

* `fileserver/src/main/resources/fileserver/fileIO.js` — `readFile`, `writeFile`,
  `fileExists`, `listFiles`, `deleteFile`. Promise-based, string-oriented, no `Buffer`, no `Stats`, no error taxonomy
  (throws `Error` with a message), no directory creation, no rename/copy.
* `fileserver/src/main/resources/fileserver/git.js` — targets endpoints
  `<base>/.git/api/{status,init,commit,branches,checkout,log}` with JSON bodies.

### 3.3 Access control today

`FileAccessControl` enforces `.hidden` (→ 404), `.readonly` (→ 403) and `.writeable_`
(whitelist → everything else 403). This is the correct trust boundary and **must** be enforced identically by FS API
v1 — the shim is untrusted client code.

### 3.4 Defects / inconsistencies discovered during analysis

These should be fixed regardless of the Node work, and several *block* it:

| #   | Issue                                                                                                                                                             | Impact on Node layer                                                                                     |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| D1  | `git.js` calls `/.git/api/*` — **no such endpoint exists**; the server only understands `POST ?gitAction=`. Dead client code.                                     | Must pick one contract before wiring `child_process`.                                                    |
| D2  | `FileUploadHandler.handleUpload` (POST) does **not** call `FileChannelCache.invalidate`, unlike `handlePut`. Stale reads possible after upload.                   | Breaks read-after-write consistency, a hard requirement for `fs`.                                        |
| D3  | Responses mix `text/plain` and JSON for errors, with no machine-readable code.                                                                                    | Cannot map to `errno`/`code` (`ENOENT`, `EACCES`, …).                                                    |
| D4  | No `ETag`, `Last-Modified`, or conditional request support on GET/PUT.                                                                                            | No caching, no optimistic concurrency, no lost-update detection.                                         |
| D5  | No `Content-Length` on the streaming GET path (chunked).                                                                                                          | Cannot pre-size buffers; progress reporting impossible.                                                  |
| D6  | No `Range` request support.                                                                                                                                       | `fs.read(fd, buf, off, len, pos)` and `createReadStream({start,end})` cannot be implemented efficiently. |
| D7  | `_files.json` reports `"path": directory.name` (basename only) and omits `readOnly`, `isSymlink`, `ctime`, `mode`.                                                | Insufficient for `Dirent`/`Stats`.                                                                       |
| D8  | No `mkdir` (empty directories are uncreatable and invisible — a directory only exists if it has content or is created implicitly by PUT).                         | `fs.mkdir`, `fs.mkdtemp` impossible.                                                                     |
| D9  | No `rename`/`move`/`copy`/`truncate`/`append`.                                                                                                                    | Extremely common Node operations.                                                                        |
| D10 | `.md` → `.html`/`.pdf`/`.txt` virtual rendering means `fs.existsSync('foo.html')` can be **true** for a file that is not on disk, and `readdir` will not list it. | Namespace pollution; FS API v1 must opt **out** of this magic.                                           |
| D11 | `PathUtils.parsePath` drops segment 0 (session id) implicitly at every call site.                                                                                 | The API needs an explicit, documented root/prefix contract.                                              |
| D12 | POST-upload refuses overwrite (409) while PUT silently overwrites; two different write policies.                                                                  | `fs.writeFile` flags (`w`, `wx`, `a`, `ax`) need one coherent policy.                                    |
| D13 | `MonacoEditorRenderer.renderEditorPage(filePath = …)` parameter is unused.                                                                                        | Cosmetic.                                                                                                |

### 3.5 Gap analysis: what Node needs that we do not have

Grouped by severity for the stated goal.

**Blocking (no reasonable emulation):**
`mkdir`, `rmdir` (empty), `rename`, `copyFile`, `stat`/`lstat` with a real `Stats`
object, `appendFile`, `truncate`, exclusive-create semantics, byte ranges, error codes.

**Blocking for *"same code"* (sync APIs):**
`readFileSync`, `writeFileSync`, `readdirSync`, `existsSync`, `statSync`, `mkdirSync` — these are used by the
overwhelming majority of real Node scripts *and* by the CommonJS module resolution algorithm itself. See §6.4.

**Emulatable client-side:**
`open`/`read`/`write`/`close` (fd table over ranged reads + buffered writes),
`createReadStream`/`createWriteStream`, `cp -r`, `rm -rf`, `readdir` recursive,
`mkdtemp`, `realpath` (no symlinks ⇒ normalize), `glob`.

**Needs a server push channel:**
`fs.watch`, `fs.watchFile`, `fsPromises.watch`.

**Stub / throw `ENOSYS`:**
`symlink`, `link`, `readlink`, `chown`, `lchown`, `chmod` (may become a no-op),
`utimes` (optional support), `statfs`, `fchmod`, `opendir` with `bufferSize`.

---

## 4. Target Architecture

┌────────────────────────────────────────────────────────────────────┐ │ L4 User script (unmodified Node code)
│ │ require ('fs') / import 'node:fs/promises' / require ('./util')  │
├────────────────────────────────────────────────────────────────────┤ │ L3 Runtime + module loader
(@cognotik/node-runtime)               │ │ - CJS require () + ESM loader, resolution over the FS │ │ - globals: process,
Buffer, console, __dirname, __filename │ │ - builtin registry: fs, fs/promises, path, os, url, events, │ │ stream,
buffer, util, assert, child_process (allowlisted)    │
├────────────────────────────────────────────────────────────────────┤ │ L2 fs shim (@cognotik/fs)
│ │ promises API │ callback API │ *Sync API │ streams │ fd table │ │ Stats/Dirent │ errno errors │ path normalisation │
cache │ ├────────────────────────────────────────────────────────────────────┤ │ L1 Transport client
(@cognotik/fs-client)                         │ │ fetch/XHR │ batching │ ETag cache │ SharedArrayBuffer sync │ │ bridge
│ SSE watch client │ retry/backoff │ ├══════════════════════ HTTP (FS API v1) ═══════════════════════════┤ │ L0 Server:
FileServlet + FsApiHandler │ │ FileAccessControl │ PathUtils │ FileChannelCache │ WatchService│
└────────────────────────────────────────────────────────────────────┘

Key architectural decisions to ratify:

* **A1 — The API is the contract, not the shim.** All security, path resolution and access control live at L0. The shim
  is convenience only; a malicious client can call the HTTP API directly and must not gain anything.
* **A2 — Additive, versioned API.** New endpoints under `…/.fsapi/v1/*` (a reserved segment rejected by `PathUtils` for
  normal file paths). The existing HTML UI,
  `_files.json`, PUT/POST/DELETE behaviour stay untouched.
* **A3 — One canonical root per servlet mount.** The FS API operates on paths relative to `getDir(request, response)`;
  the "session" segment stays in the URL prefix and is never part of a Node-visible path. `/` in Node-space ==
  `getDir()`.
* **A4 — No magic.** FS API v1 never performs `.md → .html/.pdf` substitution; `stat`
  of a non-existent file is `ENOENT` even if a sibling `.md` exists.
* **A5 — Prefer adapting an existing VFS.** Rather than re-deriving Node `fs` semantics, implement a **backend** for an
  existing, well-tested browser VFS (see §14) and expose its `fs` façade. This buys `Stats`, `Dirent`, streams, flags,
  and a conformance baseline for free.

---

## 5. Wire Protocol Design — FS API v1

### 5.1 Principles

* One HTTP request ≈ one filesystem syscall, plus a **batch** endpoint to amortise RTT.
* Metadata is JSON; file content is raw `application/octet-stream` (never base64 on the hot path).
* Every error carries a stable machine code that maps 1:1 to a Node `err.code`.
* Everything is idempotent where POSIX is idempotent; use conditional headers otherwise.
* All paths are `/`-separated, relative to the servlet root, and are validated by the existing `PathUtils` rules (no
  `..`, `~`, `:`, `\`, control chars).

### 5.2 Endpoint catalogue (proposed)

Base: `{mount}/.fsapi/v1`

| Op          | Method + path                                               | Notes                                                                                 |
|-------------|-------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `meta`      | `GET /meta`                                                 | Capabilities, separator, platform, cwd, tmpdir, case-sensitivity, limits, API version |
| `stat`      | `GET /stat?path=&lstat=&throwIfNoEntry=`                    | Returns `Stats` JSON (§5.3)                                                           |
| `statBatch` | `POST /stat` `{paths:[…]}`                                  | For module resolution storms                                                          |
| `readdir`   | `GET /dir?path=&withFileTypes=&recursive=&depth=`           | Returns entries with type + optional stat                                             |
| `read`      | `GET /file?path=` (+ `Range:`)                              | Octet-stream; `ETag`, `Last-Modified`, `Content-Length`                               |
| `write`     | `PUT /file?path=&flag=w                                     | wx                                                                                    |a|ax|r+&mode=` (+ `If-Match`, `Content-Range`) | Raw body |
| `truncate`  | `POST /truncate` `{path,len}`                               |                                                                                       |
| `mkdir`     | `POST /dir` `{path, recursive}`                             | 201 / `EEXIST`                                                                        |
| `rm`        | `DELETE /file?path=&recursive=&force=`                      | maps `unlink`/`rmdir`/`rm`                                                            |
| `rename`    | `POST /rename` `{from,to,overwrite}`                        | atomic where the JVM allows                                                           |
| `copy`      | `POST /copy` `{from,to,recursive,force,preserveTimestamps}` |                                                                                       |
| `utimes`    | `POST /utimes` `{path,atime,mtime}`                         | optional capability                                                                   |
| `realpath`  | `GET /realpath?path=`                                       | normalisation + existence check                                                       |
| `watch`     | `GET /watch?path=&recursive=` (SSE)                         | change events; polling fallback                                                       |
| `batch`     | `POST /batch` `{ops:[…]}`                                   | ordered, `stopOnError` flag                                                           |
| `exec`      | `POST /exec` `{cmd,args,cwd}` (+ SSE stream)                | **allowlisted** commands only (git)                                                   |

### 5.3 `Stats` representation

{
"path": "src/index.js",
"type": "file", // file | dir | symlink | other
"size": 1234,
"mtimeMs": 1712345678901,
"ctimeMs": 1712345678901,
"atimeMs": 1712345678901,
"birthtimeMs": 1712000000000,
"mode": 33188, // synthesised from readable/writable/type
"readOnly": false, // from FileAccessControl
"hidden": false, // never true in responses (hidden ⇒ ENOENT)
"etag": "W/\"1234-1712345678901\"",
"mimeType": "application/javascript"
}

`mode` is synthesised (e.g. `0o100644`, or `0o100444` when `readOnly`) so that
`stats.isFile()`, `stats.isDirectory()` and `mode & 0o200` behave sanely. `dev`, `ino`,
`nlink`, `uid`, `gid`, `rdev`, `blksize`, `blocks` are reported as `0` and documented as unreliable.

### 5.4 Error model

Uniform error body for every FS API endpoint:

HTTP/1.1 404 Not Found Content-Type: application/json

{ "error": { "code": "ENOENT", "errno": -2, "syscall": "stat",
"path": "missing.txt", "message": "no such file or directory" } }

| Condition                                  | HTTP | `code`                                          |
|--------------------------------------------|------|-------------------------------------------------|
| Path missing, or hidden by `.hidden`       | 404  | `ENOENT`                                        |
| `.readonly` / `.writeable_` denies write    | 403  | `EACCES`                                        |
| Server read-only mode (`--read-only`)      | 403  | `EROFS`                                         |
| Exclusive create on existing path (`wx`)   | 409  | `EEXIST`                                        |
| Write to a directory                       | 400  | `EISDIR`                                        |
| Descend through a file                     | 400  | `ENOTDIR`                                       |
| `rmdir` on non-empty dir                   | 409  | `ENOTEMPTY`                                     |
| Invalid segment (`..`, `:`, control chars) | 400  | `EINVAL`                                        |
| Body exceeds `maxFileSize`                 | 413  | `EFBIG`                                         |
| `If-Match` mismatch (lost update)          | 412  | `EBUSY` (client surfaces a typed `EStaleWrite`) |
| Unsupported op / capability off            | 501  | `ENOSYS`                                        |
| Unexpected                                 | 500  | `EIO`                                           |

Full mapping table in Appendix B.

### 5.5 Concurrency & caching

* Every `read`/`stat` returns a **weak ETag** derived from `(size, mtimeMs)` and
  `Last-Modified`.
* `GET /file` honours `If-None-Match` → `304`, enabling a browser-native cache.
* `PUT /file` accepts `If-Match: <etag>` (lost-update detection) and `If-None-Match: *`
  (exclusive create, i.e. Node flags `wx`/`ax`).
* Any successful mutation returns the **new** ETag, so the client cache can be updated without a re-read.
* **D2 must be fixed**: every mutation path invalidates `FileChannelCache`.

### 5.6 Binary transport & encodings

* Reads: `Content-Type: application/octet-stream`, consumed as `ArrayBuffer`.
* `Buffer` in the browser: use the `buffer` (feross) polyfill, or a thin `Uint8Array`
  subclass implementing the subset actually used (`toString`, `slice`, `equals`,
  `write`, `Buffer.from/alloc/concat`). Decide in Phase 2 — polyfill size (~40 KB gz)
  vs. fidelity.
* Text encodings: `utf8`, `utf-16le`, `latin1`, `base64`, `base64url`, `hex`, `ascii`
  must all round-trip; `TextDecoder` covers the first three, the rest are hand-rolled.
* Never JSON-encode file bytes except inside `/batch` (where they are base64 with an explicit `encoding` field) — and
  document the ~33 % overhead.

### 5.7 Batching

Module resolution and directory walking generate dozens of tiny `stat` calls. `/batch`
takes an ordered op list and returns an ordered result list (each with its own status/error). The client transport
auto-coalesces `stat`/`readdir` calls issued inside the same microtask tick (a "syscall micro-batcher"), which is the
single biggest latency win available.

### 5.8 Capability negotiation

`GET /meta` returns, e.g.:

{ "apiVersion": 1,
"platform": "linux", "sep": "/", "caseSensitive": true,
"cwd": "/", "tmpdir": "/.tmp", "homedir": "/",
"limits": { "maxFileSize": 52428800, "maxRequestSize": 104857600,
"maxBatchOps": 256 },
"capabilities": { "range": true, "watch": "sse", "utimes": false,
"symlink": false, "exec": ["git"], "sync": "sab",
"crossOriginIsolated": true } }

The shim configures itself from this: e.g. if `utimes` is absent, `fs.utimes` throws
`ENOSYS` rather than silently no-op'ing; if `sync !== "sab"`, the main-thread sync bridge is disabled and sync calls
throw a clear, actionable error.

### 5.9 Watch / change notification

* Server: `java.nio.file.WatchService` registered per subscription (bounded count, TTL, coalescing) → SSE stream of
  `{type: "rename"|"change", path}` events.
* Client: `fs.watch` returns a `FSWatcher` (an `EventEmitter`) over that SSE stream;
  `fs.watchFile` is polling on top of `stat` ETags.
* Fallback when `capabilities.watch === "poll"`: client-side `stat` polling with exponential backoff, driven by the same
  code path so behaviour is uniform.
* Watch is **Phase 5** — most scripts do not need it, and it carries real server cost.

### 5.10 Versioning

`/.fsapi/v1`. Breaking changes ⇒ `/v2` alongside. The client sends
`X-Fs-Api: 1` and refuses to run against a mismatched `meta.apiVersion` major.

---

## 6. Client Library Design

### 6.1 Package layout (proposed)

fileserver/src/main/resources/fileserver/node/ client/transport.js // fetch, batching, retry, ETag cache
client/errors.js // errno → Error factory, Node-shaped errors client/sync-bridge.js // SAB+Atomics (main thread) /
sync-XHR (worker)
fs/promises.js // fsPromises surface fs/callbacks.js // callback surface (wraps promises)
fs/sync.js // *Sync surface (via sync-bridge)
fs/streams.js // createReadStream / createWriteStream fs/stats.js // Stats, Dirent, constants fs/fd.js // descriptor
table fs/index.js // assembles node:fs shims/path.js os.js process.js url.js util.js events.js shims/child_process.js //
allowlisted exec → /exec runtime/loader.js // CJS require + ESM runtime/resolve.js // node_modules resolution over the
FS runtime/sandbox.js // Worker bootstrap index.js // public entry: createNodeEnv ({baseUrl})

Also shipped as an npm-consumable ESM bundle so the *same* shim can be used by bundlers/tests outside the servlet.

### 6.2–6.3 Async surfaces

`fs/promises` is the source of truth; the callback API is a mechanical wrapper (`(…args, cb)` →
`promise.then(v => cb(null, v), e => cb(e))`), preserving Node's
"callback is invoked asynchronously, exactly once, `null` first arg on success" rules, and `util.promisify.custom`
markers.

### 6.4 The synchronous API problem (the crux)

Node scripts — and CommonJS `require()` itself — are synchronous. The browser main thread cannot block on network I/O.
Options:

| Strategy                                                                                                                                                 | Where it works                            | Pros                                                 | Cons                                                                                                                     |
|----------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------|------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| **S1 Async-only** (user must rewrite to `await`)                                                                                                         | everywhere                                | trivial                                              | *fails the stated goal* ("same code")                                                                                    |
| **S2 Sync XHR inside a Web Worker**                                                                                                                      | Worker only                               | Simple, no special headers, exact blocking semantics | Deprecated-ish but still spec'd in workers; one HTTP RTT per call (mitigate with prefetch/cache); no main-thread support |
| **S3 `SharedArrayBuffer` + `Atomics.wait`** in a Worker, with a *proxy* Worker doing `fetch`                                                             | Worker; main thread cannot `Atomics.wait` | Fast, modern, allows binary transfer without copies  | Requires **COOP/COEP** cross-origin isolation headers on the server; more moving parts                                   |
| **S4 Preload snapshot + write-back** (fetch a recursive manifest + all needed files up-front into an in-memory VFS, run fully sync, flush on completion) | everywhere incl. main thread              | Zero per-call latency; enables main-thread execution | Not live; unsuitable for huge trees; write visibility deferred; conflict risk                                            |
| **S5 Source transform** (Babel/SWC: rewrite `*Sync` calls into `await`, make functions async)                                                            | everywhere                                | no infra                                             | Viral async colouring, breaks `require`, breaks generators/`for` loops subtly; high risk                                 |

**Recommendation:** run user scripts **in a Web Worker** and implement sync via **S3 where cross-origin isolation is
available, falling back to S2**, with **S4 as an opt-in "snapshot mode"** for main-thread/offline execution. Expose the
choice via
`meta.capabilities.sync` and a `createNodeEnv({ sync: 'auto'|'sab'|'xhr'|'snapshot' })`
option. Document that main-thread execution supports the async API only unless snapshot mode is used.

Server work implied: an option to emit `Cross-Origin-Opener-Policy: same-origin` and
`Cross-Origin-Embedder-Policy: require-corp` (a `FileServerCli` flag `--cross-origin-isolated`), since these break
third-party embeds (Monaco CDN!) and therefore cannot be unconditional.

### 6.5 Streams

`createReadStream` maps to a ranged `GET` consumed via `ReadableStream` → Node-`Readable` adapter (`highWaterMark`,
`pause`/`resume`, `start`/`end`, `autoClose`).
`createWriteStream` buffers and flushes with `Content-Range`-style appends (`flag=a`) at a configurable chunk size,
emitting `drain` correctly, and `finish`
only after the last flush is acknowledged. Requires a minimal `stream` shim (`readable-stream` is a reasonable
dependency).

### 6.6 File descriptors

No server-side handles in v1: an fd is a client-side record
`{path, flags, position, etag, dirty, writeBuffer}`. Reads use `Range`; writes buffer until `fs.close`/`fsync` or a size
threshold, then `PUT` with `If-Match`. Documented deviations: writes are not visible to other clients until flush; two
fds on the same path in one page do not share a position; `fs.ftruncate` flushes first.

### 6.7 `path`, `os`, `process`, `url`

* `path` — vendored/ported `path-browserify` **plus** `path.win32`/`path.posix`, with
  `path.sep` fixed to `/` regardless of server OS (the API is always `/`-separated; the server maps to its own
  separator).
* `os` — `platform()`, `EOL`, `tmpdir()`, `homedir()`, `type()`, `arch()` from `/meta`.
* `process` — `cwd()` (mutable via `chdir`), `argv`, `env` (from an injected map, **never** the server's real env),
  `platform`, `exit`, `nextTick`, `stdout/stderr`
  (→ console / a UI console pane), `hrtime`, `on('exit')`.
* `url` — `fileURLToPath`/`pathToFileURL` over the virtual root (`file:///…`).

### 6.8 `child_process` and git

`child_process.execFile('git', [...], {cwd})` → `POST /exec` with a **server-side allowlist** (initially `git`, with a
sub-command allowlist) reusing
`GitOperationHandler.executeGitCommand`. `spawn` returns a `ChildProcess`-like object whose `stdout`/`stderr` are fed by
an SSE stream. Everything else throws `ENOENT`
(matching Node's behaviour for a missing binary) rather than a confusing error.

**Decision required (D1):** consolidate the two git contracts. Proposal — keep
`POST ?gitAction=` for the existing HTML UI (no churn), and implement `/exec` as the general mechanism; rewrite `git.js`
to use `/exec` (or delete it) so there is no dead code pointing at `/.git/api/*`.

### 6.9 Caching & coherence

Three tiers, all keyed by normalised absolute virtual path:

1. **Negative/positive `stat` cache** — short TTL (default 1 s) + explicit invalidation on any local mutation; essential
   for `require` resolution.
2. **Content cache** — ETag-validated; `readFile` of an unchanged file costs one conditional GET (or zero within TTL).
3. **Directory listing cache** — invalidated on any mutation under the directory.

Coherence rules to specify precisely: *read-your-writes is guaranteed*; *cross-tab and cross-user visibility is
eventually consistent* (bounded by TTL or a watch event).

---

## 7. Module Loading & Execution Model

### 7.1 Requirements

* `require('./x')`, `require('../y.json')`, `require('pkg')`, `require('node:fs')`.
* `module.exports` / `exports` / `__dirname` / `__filename` / `module.parent`.
* ESM: `import`, dynamic `import()`, `import.meta.url`, top-level `await`.
* `package.json` fields: `main`, `exports`, `type`, `browser` (optional).
* JSON modules; `.node` → clear `ERR_DLOPEN_FAILED`-style error.
* Circular-dependency semantics identical to Node's (partial exports).

### 7.2 Options

| Option                                                                                     | Description                                                                                      | Verdict                                                                                                            |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| **M1 Server-side bundling** (esbuild/rollup invoked by the servlet, shim aliased for `fs`) | Fast startup, one request                                                                        | Requires a Node toolchain on the server (or a JVM bundler); breaks dynamic `require(variable)`; poor debuggability |
| **M2 Runtime CJS loader over the FS API**                                                  | Implement Node's resolution algorithm in the browser using `fs` sync calls                       | Highest fidelity, zero build step, dogfoods the shim; needs sync I/O (§6.4) and heavy `stat` batching              |
| **M3 Native ESM + import maps**                                                            | Let the browser fetch modules; generate an import map from a server-computed resolution manifest | Native debugging, no interpreter overhead; cannot do CJS, and CJS is what most scripts are                         |

**Recommendation:** **M2 as the primary path** (it is the only one that satisfies
"same code" for CJS), with a **server-computed resolution manifest** endpoint (`GET /.fsapi/v1/resolve?from=&request=`
and a bulk pre-resolution for a whole entry graph) as a latency optimisation — one round trip instead of ~15 `stat`s per
specifier. **M1 remains an opt-in "production mode"** for large dependency trees.

### 7.3 Sandbox

Execute in a **dedicated Worker** (`runtime/sandbox.js`) — gives us sync XHR, isolates crashes and infinite loops
(terminable), and keeps the UI responsive. Evaluation via
`new Function(...)` on fetched source with a `//# sourceURL=` suffix so DevTools shows real filenames and breakpoints
work. (CSP: this requires `script-src 'unsafe-eval'` in the worker context — call this out explicitly as a deployment
consideration; a
`Worker` created from a Blob with its own CSP is the mitigation.)

### 7.4 Globals injection

A per-realm `globalThis` decoration: `process`, `Buffer`, `console`, `setImmediate`,
`clearImmediate`, `queueMicrotask`, `TextEncoder/Decoder`, `URL`, `structuredClone`.
`__dirname`/`__filename` are per-module wrapper parameters, exactly as in Node.

---

## 8. Compatibility Matrix (target for v1)

Tier **A** = behaviourally equivalent; **B** = supported with documented deviations; **C** = throws `ENOSYS`/
`ERR_METHOD_NOT_IMPLEMENTED` with a clear message.

| Tier  | APIs                                                                                                                                                                                                                                                                                               |
|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **A** | `readFile`, `writeFile`, `appendFile`, `readdir`, `mkdir`, `rm`, `rmdir`, `unlink`, `rename`, `copyFile`, `cp`, `stat`, `lstat`, `access`, `exists`, `existsSync`, `truncate`, `mkdtemp`, `realpath`, `constants`, `Stats`, `Dirent`, `path.*`, all `*Sync` counterparts (in Worker/snapshot mode) |
| **B** | `open`/`read`/`write`/`close`/`fstat` (client fd table), `createReadStream`, `createWriteStream`, `opendir`, `watch`, `watchFile`, `utimes`, `chmod` (readOnly bit only), `glob`, `cp --preserveTimestamps`                                                                                        |
| **C** | `symlink`, `readlink`, `link`, `chown`, `lchmod`, `statfs`, `fdatasync` guarantees, `O_DIRECT`-style flags, `fs.promises.watch` recursion on non-supporting servers                                                                                                                                |

Deviations must be enumerated in a `COMPATIBILITY.md` shipped with the package, and each Tier-B/C item must have a
corresponding conformance test asserting the *documented*
behaviour (including the exact error `code`).

---

## 9. Security Analysis

1. **The shim is not a security boundary.** All checks remain in `FileAccessControl` +
   `PathUtils` at L0. Every new FS API endpoint must call the same helpers; add a servlet-level test that enumerates
   endpoints and asserts each one consults
   `isHidden`/`isReadOnly`.
2. **New verbs = new attack surface.** `rename`/`copy` take *two* paths — both must be validated and both must pass
   access control (`rename` out of a `.readonly` tree is a delete; `copy` into a `.hidden` tree is an information leak
   vector).
3. **`mkdir`/`mkdtemp` enable resource exhaustion** — add quotas: max entries per directory, max total bytes per root,
   max depth.
4. **`/exec` is remote code execution by design.** Allowlist the binary *and*
   sub-commands; never interpolate a shell; reject `-c`, `--upload-pack`,
   `--exec`, `core.sshCommand`-style config injection in git args; default the capability to **off** unless
   `isGitEnabled(req)`.
5. **Cross-origin isolation trade-off** — COOP/COEP breaks the Monaco CDN; if enabled, Monaco must be self-hosted. Make
   it a flag, not a default.
6. **CSRF** — the FS API mutates state with cookie-authenticated requests. Require a
   `X-Fs-Api` header (forces a preflight for cross-origin) *and* `SameSite=Strict`
   session cookies, or an explicit CSRF token from `/meta`.
7. **`eval` in the runtime** — user scripts execute with the page's privileges *in the Worker*. Do not evaluate
   untrusted third-party scripts in a realm that has access to a privileged transport; consider per-run scoped tokens.
8. **Path canonicalisation parity** — the client normalises (`.`/`..` collapsing) *and*
   the server re-validates; never trust client normalisation. Guard against canonicalisation mismatches (Unicode
   NFC/NFD, case-insensitive filesystems, trailing dots/spaces on Windows).
9. **Symlink escape** — since `.hidden`/`.readonly` walk the *lexical* tree, a symlink inside the root pointing outside
   it bypasses them. FS API v1 should refuse to traverse symlinks that resolve outside `getDir()` (`realpath`
   containment check).

---

## 10. Performance Plan

| Concern                  | Mitigation                                                                                                              |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------|
| One RTT per syscall      | Microtask-level auto-batching; `/batch`; bulk `stat`                                                                    |
| Module resolution storms | Server-side `/resolve` + prefetched resolution manifest                                                                 |
| Repeated reads           | ETag/`304` + in-memory content cache; HTTP cache headers                                                                |
| Large files              | `Range` reads, streaming writes, no base64                                                                              |
| Directory walks          | `readdir?recursive=&depth=` returning stats inline                                                                      |
| Cold start               | Optional snapshot bundle (`GET /.fsapi/v1/snapshot?path=&maxBytes=`) returning a tar/zip of a subtree for S4 mode       |
| Head-of-line blocking    | HTTP/2 (Jetty ALPN) — verify enabled                                                                                    |
| Server file handles      | Existing `FileChannelCache`; ensure the new endpoints reuse it and that its 100-entry/10 s policy suits burst workloads |

Target budgets (to be validated): cached `statSync` < 1 ms; uncached `statSync` <
1 RTT; `require` of a 50-module tree < 500 ms warm.

---

## 11. Testing & Conformance Strategy

1. **Shared conformance suite** — a single suite of ~300 assertions written against the
   `fs` API, executed in three environments:

* real Node against a temp dir (**the oracle**),
* the browser shim (Playwright, headless) against a live `FileServerCli`,
* (optionally) the shim in Node against a live server, to isolate browser issues. Any divergence is either a bug or an
  entry in `COMPATIBILITY.md` — enforced by the suite (`expectDeviation(...)`).

2. **Server API tests** (JUnit) — one per endpoint × {happy, hidden, readonly, writeable-whitelist, traversal, oversize,
   conditional-header} matrix.
3. **Property/fuzz tests** — random operation sequences applied to both a local dir and the remote FS, comparing
   resulting trees (a model-based test).
4. **Real-script corpus** — run a handful of genuine Node scripts unmodified (a Markdown build script, a JSON transform,
   a small `commander` CLI, something using
   `glob`/`fast-glob`) as end-to-end acceptance tests.
5. **Performance regression tests** — timed macro-benchmarks in CI with thresholds.
6. **Security tests** — traversal (`..%2f`, unicode, null bytes), symlink escape,
   `.hidden`/`.readonly` bypass attempts via `rename`/`copy`, `/exec` argument injection.

---

## 12. Phased Rollout Plan

Each phase is independently shippable and independently useful.

### Phase 0 — Specification & harness *(no user-visible change)*

* Freeze the FS API v1 OpenAPI spec and the errno mapping table.
* Stand up the conformance harness with Node as the oracle (all tests initially
  "expected to fail" for the shim).
* Fix D2 (cache invalidation on POST) and D3 (uniform JSON error envelope) as prerequisites.
* **Done when:** the spec is reviewed and the harness runs green against Node.

### Phase 1 — Server: FS API v1 core

* `FsApiHandler` + routing under `{mount}/.fsapi/v1`; reuse `PathUtils`,
  `FileAccessControl`, `FileChannelCache`.
* `meta`, `stat`(+batch), `dir`, `file` GET (with `Range`, `ETag`, `Content-Length`),
  `file` PUT (flags + conditional), `mkdir`, `rm`, `rename`, `copy`, `truncate`,
  `realpath`, `batch`.
* Quotas, symlink containment, CSRF header requirement.
* **Done when:** JUnit matrix green; `curl` can perform every op; existing UI unaffected.

### Phase 2 — Client: async `fs` + `path` + `Buffer`

* `transport.js` (batching, ETag cache, retries), `errors.js`, `stats.js`.
* `fs/promises` + callback API, `path`, `Buffer`, `os`, `url`.
* **Done when:** the async portion of the conformance suite is green in Playwright.

### Phase 3 — Sync layer

* Worker bootstrap; sync-XHR bridge; SAB/`Atomics` bridge; optional COOP/COEP flag in
  `FileServerCli`; snapshot mode.
* `fs/sync.js` complete.
* **Done when:** the full sync conformance suite is green in a Worker; documented, non-crashing failure mode on the main
  thread.

### Phase 4 — Module loader / runtime

* CJS `require` with Node resolution, ESM loader, `package.json exports`, JSON modules, circular deps, `process` global,
  `sourceURL` debugging.
* Server `/resolve` fast path.
* **Done when:** the real-script corpus runs unmodified; a script with a small
  `node_modules` tree loads in < 1 s warm.

### Phase 5 — Streams, fds, watch, child_process/git

* `createReadStream`/`createWriteStream`, fd table, `fs.watch` over SSE,
  `child_process` → `/exec`, git allowlist; retire/rewrite `git.js` (D1).
* **Done when:** Tier-B matrix green; git status/commit works through
  `child_process.execFile`.

### Phase 6 — Performance, DX, docs

* Auto-batching tuning, snapshot endpoint, HTTP/2 verification, benchmarks.
* `COMPATIBILITY.md`, API reference, examples, a "Run this script" button in the directory listing UI (an
  `getToolbarActions`/`getFileActions` override — note this slots neatly into the existing extension points), and an
  output console panel.
* **Done when:** docs published, benchmarks within budget, demo works end-to-end.

---

## 13. Risks & Open Questions

| #   | Risk / question                                                                                            | Mitigation / decision needed                                                                           |
|-----|------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| R1  | Sync I/O in the browser is fundamentally awkward; sync XHR may be removed from workers eventually.         | Keep S3 (SAB) as the strategic path; abstract behind `sync-bridge.js` so the mechanism is swappable.   |
| R2  | COOP/COEP conflicts with the CDN-hosted Monaco editor.                                                     | Self-host Monaco, or keep isolation opt-in per deployment.                                             |
| R3  | Latency makes chatty scripts feel slow.                                                                    | Batching + caching + snapshot mode; publish guidance.                                                  |
| R4  | Semantic drift between shim and Node over time.                                                            | The conformance suite is the contract; run it in CI against a pinned Node version.                     |
| R5  | `/exec` is a large security surface.                                                                       | Off by default; strict allowlist; separate review.                                                     |
| R6  | Scope creep into a full Node runtime (`http`, `net`, `worker_threads`).                                    | Explicit non-goals; revisit only with a concrete use case.                                             |
| R7  | Two write policies today (POST 409 vs PUT overwrite).                                                      | **Decide:** FS API v1 uses explicit `flag=`; legacy endpoints keep current behaviour.                  |
| R8  | Should `getDir()` per-request resolution be re-evaluated on every FS API call (session expiry mid-script)? | **Decide:** yes; return `401`/`EACCES` and surface a distinct `ESESSION` error the runtime can report. |
| R9  | Case-insensitive host filesystems (macOS/Windows) vs. case-sensitive Node expectations.                    | Report `caseSensitive` in `/meta`; conformance tests parameterised on it.                              |
| R10 | Do we also want to run these scripts **server-side** (Nashorn/GraalJS/Node sidecar)?                       | Out of scope now, but keep L1 transport pluggable so a "direct" (in-JVM) transport could be added.     |
| R11 | Where does `cwd` live for a script — the servlet root or the directory being browsed?                      | **Decide:** default `cwd` = the directory the script was launched from; root = `/`.                    |
| R12 | Bundle size of the full shim (Buffer + streams + loader).                                                  | Budget ≤ 150 KB gzipped; lazy-load streams/loader.                                                     |

---

## 14. Prior Art to Evaluate (build-vs-adopt)

| Project                                                       | Relevance | Notes                                                                                                                                                    |
|---------------------------------------------------------------|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| **ZenFS** (successor to BrowserFS)                            | Very high | Pluggable backends, provides `fs`, `fs/promises`, sync via a store abstraction. Writing a `CognotikBackend` may be far cheaper than reimplementing `fs`. |
| **memfs**                                                     | High      | Excellent in-memory `fs` implementation; ideal for snapshot mode (S4) and as a cache layer.                                                              |
| **BrowserFS `XmlHttpRequest` backend**                        | High      | Precedent for a read-only HTTP-backed FS with an index manifest.                                                                                         |
| **`@wasmer/wasmfs`, WASI shims**                              | Medium    | Error-code and `Stats` handling patterns.                                                                                                                |
| **StackBlitz WebContainers**                                  | Medium    | Proof that in-browser Node is viable; closed source, different (in-browser kernel) architecture.                                                         |
| **`path-browserify`, `buffer`, `readable-stream`, `process`** | High      | Vendor directly rather than reimplement.                                                                                                                 |
| **Node's own `test/parallel/test-fs-*.js`**                   | High      | A ready-made conformance corpus to port selectively.                                                                                                     |

**Action for Phase 0:** spike a ZenFS backend against the *existing* endpoints (read/write/readdir/delete only) to
measure how much of §8 Tier A falls out for free. If the spike is convincing, §6 becomes "implement a ZenFS backend +
thin extras"
rather than "implement `fs`", cutting Phases 2–3 substantially.

---

## Appendix A — Proposed endpoint reference (summary)

| Method | Path                  | Query / Body                                       | Success           |
|--------|-----------------------|----------------------------------------------------|-------------------|
| GET    | `/.fsapi/v1/meta`     | —                                                  | 200 JSON          |
| GET    | `/.fsapi/v1/stat`     | `path`, `lstat`                                    | 200 Stats         |
| POST   | `/.fsapi/v1/stat`     | `{paths:[]}`                                       | 200 `[{ok,stat    |error}]` |
| GET    | `/.fsapi/v1/dir`      | `path`, `withFileTypes`, `recursive`, `depth`      | 200 entries       |
| POST   | `/.fsapi/v1/dir`      | `{path,recursive}`                                 | 201               |
| GET    | `/.fsapi/v1/file`     | `path` (+`Range`,`If-None-Match`)                  | 200/206/304 bytes |
| PUT    | `/.fsapi/v1/file`     | `path`,`flag`,`mode` (+`If-Match`,`If-None-Match`) | 200/201 + ETag    |
| DELETE | `/.fsapi/v1/file`     | `path`,`recursive`,`force`                         | 204               |
| POST   | `/.fsapi/v1/rename`   | `{from,to,overwrite}`                              | 204               |
| POST   | `/.fsapi/v1/copy`     | `{from,to,recursive,force}`                        | 204               |
| POST   | `/.fsapi/v1/truncate` | `{path,len}`                                       | 204               |
| POST   | `/.fsapi/v1/utimes`   | `{path,atime,mtime}`                               | 204 / 501         |
| GET    | `/.fsapi/v1/realpath` | `path`                                             | 200 `{path}`      |
| GET    | `/.fsapi/v1/resolve`  | `from`,`request`                                   | 200 `{path,type}` |
| GET    | `/.fsapi/v1/watch`    | `path`,`recursive`                                 | 200 SSE           |
| POST   | `/.fsapi/v1/batch`    | `{ops:[],stopOnError}`                             | 200 `[results]`   |
| POST   | `/.fsapi/v1/exec`     | `{cmd,args,cwd}`                                   | 200 JSON or SSE   |
| GET    | `/.fsapi/v1/snapshot` | `path`,`maxBytes`                                  | 200 zip           |

## Appendix B — errno mapping (excerpt)

| `code`      | `errno` | Typical trigger                   |
|-------------|---------|-----------------------------------|
| `ENOENT`    | -2      | missing path, hidden path         |
| `EACCES`    | -13     | `.readonly` / `.writeable_` denial |
| `EEXIST`    | -17     | `wx`/`ax`, `mkdir` non-recursive  |
| `EISDIR`    | -21     | write/read a directory as a file  |
| `ENOTDIR`   | -20     | traverse through a file           |
| `ENOTEMPTY` | -39     | `rmdir` on non-empty              |
| `EINVAL`    | -22     | invalid path segment / bad flag   |
| `EROFS`     | -30     | server read-only mode             |
| `EFBIG`     | -27     | exceeds `maxFileSize`             |
| `ENOSYS`    | -38     | capability disabled               |
| `EIO`       | -5      | unexpected server error           |

## Appendix C — New/changed server files (anticipated)

handler/FsApiHandler.kt // routing + dispatch for /.fsapi/v1 handler/FsStatSerializer.kt // Stats JSON
handler/FsErrors.kt // errno envelope + status mapping handler/FsWatchHandler.kt // SSE via java.nio WatchService (Phase
5)
handler/ExecHandler.kt // allowlisted process exec (Phase 5)
util/EtagUtil.kt // weak etag from (size, mtime)
util/RangeUtil.kt // Range header parsing (modified) FileServlet.kt // intercept the /.fsapi/ prefix before path parsing
(modified) FileUploadHandler.kt // D2: invalidate cache on POST (modified) PathUtils.kt // reserve the ".fsapi" segment

## Appendix D — Illustrative target usage (not yet implemented)

  <script type="module">
    import { createNodeEnv } from '/modules/node/index.js';
    const env = await createNodeEnv({
      baseUrl: '/files/root',   // servlet mount
      cwd: '/',
      sync: 'auto',             // sab -> xhr -> snapshot
      env: { NODE_ENV: 'production' },
      stdout: chunk => console.log(String(chunk)),
    });
    await env.run('scripts/report.js');   // unmodified Node source
  </script>

Equivalent programmatic ("drop-in wrapper") usage without the loader:

import fs from '/modules/node/fs/promises.js'; const files = await fs.readdir ('data', { withFileTypes: true });