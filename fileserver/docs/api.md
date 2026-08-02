# Cognotik File Server — HTTP API

The file server exposes **two independent surfaces** from the same servlet mount:

| Surface          | Purpose                                                                                 | Entry point                       |
|------------------|-----------------------------------------------------------------------------------------|-----------------------------------|
| **v1 "classic"** | Human-facing file browser, upload/edit/delete, markdown rendering, ZIP download, Git UI | `{mount}/{prefix}/...`            |
| **FS API v1**    | Machine-facing remote filesystem (a `node:fs` backing store)                            | `{mount}/{prefix}/.fsapi/v1/{op}` |

Both surfaces resolve paths against the same directory (`FileServlet.getDir` /
`FilesystemServlet.getFsApiRoot`) and enforce the same access-control rules (`.hidden` / `.readonly` / `.writeable_`).

---

## 1. Mounting and path shape

`FileServlet` is mounted with a wildcard (e.g. `/files/*`) and **drops the first path segment** of `pathInfo`. That
first segment is an opaque prefix — normally a session id — and is never part of a file path:

```
/files/{prefix}/a/b/c.txt      ->  <root>/a/b/c.txt
/files/{prefix}/               ->  <root>/            (directory listing)
```

`FileServerCli` uses the literal prefix `root`:

```
http://localhost:8081/files/root/
http://localhost:8081/files/root/.fsapi/v1/meta
```

### Reserved segment

`.fsapi` is a **reserved path segment**. It can never name a real file or directory; `PathUtils.parsePath` and
`FsPath.normalize` reject it with
`400 Invalid path` / `EINVAL`.

`FsApiRoute` recognises `.fsapi` *anywhere* in `pathInfo`, so all of these route to the FS API:

```
/files/.fsapi/v1/meta
/files/root/.fsapi/v1/meta
/files/{session}/.fsapi/v1/dir?path=/src
```

### Path validation rules

A path segment is rejected if it:

* is `..` and would escape the served root
* equals `.fsapi`
* contains `\`, `:`, `~`, NUL, or any character `< 0x20` (or `0x7F`)
* (FS API only) ends with `.` or a space — ambiguous on Windows hosts

After lexical normalisation, the FS API **re-checks the canonical path** for containment inside the root, which also
blocks symlink escapes (`EACCES`).

---

## 2. Access control

Access control is driven by `.gitignore`-style marker files discovered by walking from the target up to (and including)
the served root.

| Marker       | Effect                                                                                |
|--------------|---------------------------------------------------------------------------------------|
| `.hidden`    | Matched paths are treated as **non-existent** (`404` / `ENOENT`) for every operation. |
| `.readonly`  | Matched paths cannot be modified (`403` / `EACCES` / `EROFS`).                        |
| `.writeable_` | Acts as a **whitelist**: anything in scope that is *not* matched becomes read-only.   |

The marker files themselves are always hidden (`.hidden`) or read-only (`.readonly`, `.writeable_`) so they cannot be
tampered with through the server.

Additional guarantees:

* Hidden entries are omitted from directory listings, `_files.json`, `readdir`,
  `snapshot` archives, `watch` events and module resolution.
* `copy` silently skips hidden sources (it never leaks content past `.hidden`).
* Recursive `rm` / `rename` first assert that **no descendant** is read-only.
* Server-wide read-only mode (`--read-only`) answers `EROFS` for every mutation.

---

## 3. Classic (v1) surface

### 3.1 `GET {mount}/{prefix}/{path}`

| Condition                                                  | Behaviour                                                                                           |
|------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Path is hidden                                             | `404 File not found`                                                                                |
| Existing file                                              | Streams the file; `Content-Type` from `MimeTypeResolver` (files > 1 MiB use a memory-mapped writer) |
| Existing file + `?edit=1`                                  | Monaco editor page (read-only if `.readonly` applies)                                               |
| Existing directory, URL lacks trailing `/`                 | `302` redirect to `…/`                                                                              |
| Existing directory                                         | HTML directory listing (breadcrumbs, upload drop-zone, folders, files, optional Git section)        |
| `_files.json` that does not exist                          | Virtual JSON listing of the containing directory                                                    |
| Missing `*.html` / `*.pdf` / `*.txt` with a sibling `*.md` | Renders the markdown as HTML / PDF / plain text                                                     |
| Anything else missing                                      | `404 File not found`                                                                                |

`?edit=1` refuses binary files (`400 Cannot edit binary file: …`); a file is considered binary if a NUL byte appears in
the first 8 KiB.

MIME overrides: `.js`/`.mjs` → `application/javascript`, `.log` → `text/plain`; otherwise Jetty's `MimeTypes` table,
falling back to
`application/octet-stream`.

#### Virtual `_files.json`

```json
{
  "path": "src",
  "totalFiles": 2,
  "totalFolders": 1,
  "entries": [
    {
      "name": "lib",
      "type": "directory",
      "lastModified": 1730000000000
    },
    {
      "name": "main.kt",
      "type": "file",
      "size": 1234,
      "lastModified": 1730000000000,
      "mimeType": "text/plain"
    }
  ]
}
```

### 3.2 `HEAD {mount}/{prefix}/{path}`

Mirrors `GET` status/`Content-Type`/`Content-Length` without a body, including the markdown-substitution rules.
Directory URLs without a trailing slash answer
`301` with a `Location` header.

### 3.3 `POST {mount}/{prefix}/{dir}` — multipart upload

`multipart/form-data`, part name **`file`**.

| Status | Meaning                                                                         |
|--------|---------------------------------------------------------------------------------|
| `200`  | `{"success": true, "message": "File uploaded successfully", "filename": "..."}` |
| `400`  | Invalid target directory / no file part / no or invalid filename                |
| `403`  | Target directory or file is read-only, or the path is hidden                    |
| `404`  | Target path is hidden                                                           |
| `409`  | `File already exists. Overwriting is not allowed.`                              |

Limits (`@MultipartConfig`): 50 MB per file, 100 MB per request, 2 MB memory threshold.

### 3.4 `PUT {mount}/{prefix}/{path}` — raw write

The request body becomes the file contents (used by the Monaco editor's *Save*). Missing parent directories are created.

| Status | Body                                                                           |
|--------|--------------------------------------------------------------------------------|
| `200`  | `{"success": true, "message": "File updated successfully", "filename": "..."}` |
| `201`  | `{"success": true, "message": "File created successfully", "filename": "..."}` |
| `400`  | Invalid base directory / invalid filename / target is a directory              |
| `403`  | Path is read-only                                                              |
| `404`  | Path is hidden                                                                 |

### 3.5 `DELETE {mount}/{prefix}/{path}`

Deletes a file, or a directory recursively.

| Status | Body                                                        |
|--------|-------------------------------------------------------------|
| `200`  | `{"success": true, "message": "File deleted successfully"}` |
| `403`  | Read-only                                                   |
| `404`  | Missing or hidden                                           |
| `500`  | `Failed to delete file` / `Failed to delete directory`      |

### 3.6 Git UI endpoint — `POST {mount}/{prefix}/…?gitAction=<name>`

Form-encoded parameters; responses are JSON with an added `"success"` flag. Available only when `isGitEnabled(req)` is
true.

```
curl -X POST 'http://localhost:8081/files/root/' \
     -d 'gitAction=commit' -d 'message=wip'
```

```json
{
  "success": true,
  "message": "Changes committed",
  "output": "..."
}
```

Unknown actions return `400` with the list of known names:

```json
{
  "success": false,
  "message": "Unknown git action: 'foo' (known: add, branches, ...)",
  "available": [
    "add",
    "branches",
    "commit",
    "..."
  ]
}
```

See [§5 Git actions](#5-git-actions) for the registry.

### 3.7 ZIP download — `GET /zip?session=<name>&path=<path>`

Served by `StaticZipServlet(dataStoragePath)`; zips `<dataStoragePath>/<session>/<path>`.

* `400 Missing session parameter`
* `404 Directory not found`
* `200` `application/zip` with `Content-Disposition: attachment; filename="<name>.zip"`

Dot-files are excluded from the archive. `FileServlet.getZipLink` renders the toolbar link (empty string ⇒ no button).

---

## 4. FS API v1

```
{mount}/{prefix}/.fsapi/v1/{op}
```

### 4.1 Conventions

* **Paths** are always `/`-separated and relative to the mount root; `/` is the root. Never send host paths.
* **Responses** are JSON unless stated otherwise (`file` → octet-stream,
  `snapshot` → zip, `watch` → SSE).
* Every response carries `X-Fs-Api: 1`.
* Every **mutating** request must carry a `X-Fs-Api` request header (any value)
  when `requireApiHeader` is enabled (default). Missing ⇒ `EACCES`. This is CSRF mitigation, not authentication.
* Requests to a different major version answer `ENOSYS`.
* **No magic**: the FS API never performs `.md → .html/.pdf` substitution, so
  `stat("/foo.html")` is `ENOENT` unless `foo.html` really exists.

### 4.2 Errors

Errors are `{"error": {...}}` with an `X-Fs-Error: <CODE>` header:

```json
{
  "error": {
    "code": "ENOENT",
    "errno": -2,
    "syscall": "stat",
    "path": "/missing.txt",
    "message": "no such file or directory"
  }
}
```

| Code        | errno | HTTP | Meaning                                                                |
|-------------|-------|------|------------------------------------------------------------------------|
| `ENOENT`    | -2    | 404  | no such file or directory (also returned for hidden paths)             |
| `EACCES`    | -13   | 403  | permission denied (read-only marker, escape attempt, missing header)   |
| `EROFS`     | -30   | 403  | read-only file system (server-wide read-only mode)                     |
| `EEXIST`    | -17   | 409  | file already exists (`wx`/`ax`, `If-None-Match: *`, `overwrite:false`) |
| `EISDIR`    | -21   | 400  | illegal operation on a directory                                       |
| `ENOTDIR`   | -20   | 400  | not a directory                                                        |
| `ENOTEMPTY` | -39   | 409  | directory not empty                                                    |
| `EINVAL`    | -22   | 400  | invalid argument / malformed path or body                              |
| `EFBIG`     | -27   | 413  | exceeds `limits.maxFileSize`                                           |
| `EBUSY`     | -16   | 412  | ETag mismatch (optimistic concurrency) or exec timeout                 |
| `EMFILE`    | -24   | 429  | too many active watchers                                               |
| `ERANGE`    | -34   | 416  | unsatisfiable / malformed `Range`                                      |
| `ENOSYS`    | -38   | 501  | capability or operation not implemented                                |
| `EIO`       | -5    | 500  | I/O error                                                              |

### 4.3 Operation index

| Method       | Op         | Description                                       | Capability |
|--------------|------------|---------------------------------------------------|------------|
| `GET`/`HEAD` | `meta`     | version, platform, limits, capabilities           | —          |
| `GET`/`HEAD` | `actions`  | self-description of every registered action       | —          |
| `GET`/`HEAD` | `stat`     | `fs.stat` / `fs.lstat`                            | —          |
| `GET`/`HEAD` | `dir`      | `fs.readdir`                                      | —          |
| `GET`/`HEAD` | `file`     | `fs.readFile` / `createReadStream`                | —          |
| `GET`/`HEAD` | `realpath` | `fs.realpath`                                     | —          |
| `GET`/`HEAD` | `resolve`  | CommonJS/ESM module resolution                    | `resolve`  |
| `GET`/`HEAD` | `snapshot` | zip of a subtree                                  | `snapshot` |
| `GET`/`HEAD` | `watch`    | SSE change stream (`fs.watch`)                    | `watch`    |
| `POST`       | `stat`     | batch `fs.stat`                                   | —          |
| `POST`       | `dir`      | `fs.mkdir`                                        | —          |
| `POST`       | `rename`   | `fs.rename`                                       | —          |
| `POST`       | `copy`     | `fs.copyFile` / `fs.cp`                           | —          |
| `POST`       | `truncate` | `fs.truncate`                                     | —          |
| `POST`       | `utimes`   | `fs.utimes`                                       | `utimes`   |
| `POST`       | `batch`    | pipeline several ops                              | —          |
| `POST`       | `exec`     | allowlisted `child_process`                       | `exec`     |
| `POST`       | `git`      | registered git action                             | `git`      |
| `PUT`        | `file`     | `fs.writeFile` / `createWriteStream`              | —          |
| `DELETE`     | `file`     | `fs.unlink` / `fs.rm` / `fs.rmdir`                | —          |
| `OPTIONS`    | *(any)*    | `204` + `Allow: GET,HEAD,POST,PUT,DELETE,OPTIONS` | —          |

---

### 4.4 `GET /meta`

```json
{
  "apiVersion": 1,
  "platform": "linux",
  "sep": "/",
  "caseSensitive": true,
  "cwd": "/",
  "tmpdir": "/.tmp",
  "homedir": "/",
  "root": "/",
  "readOnly": false,
  "limits": {
    "maxFileSize": 52428800,
    "maxRequestSize": 104857600,
    "maxBatchOps": 256,
    "maxDirEntries": 50000,
    "maxDepth": 32,
    "maxSnapshotBytes": 33554432
  },
  "capabilities": {
    "range": true,
    "conditional": true,
    "batch": true,
    "statBatch": true,
    "actions": true,
    "git": true,
    "resolve": true,
    "snapshot": true,
    "watch": "sse",
    "utimes": true,
    "symlink": false,
    "chmod": false,
    "exec": [
      "git"
    ],
    "sync": "xhr",
    "crossOriginIsolated": false
  }
}
```

`platform` is one of `win32`, `darwin`, `sunos`, `aix`, `linux`.
`watch` is `"sse"`, `"poll"` or `"none"`. `sync` is `"sab"`, `"xhr"` or
`"snapshot"`. Clients should configure themselves from this document so a disabled capability yields a clean `ENOSYS`
instead of a silent no-op.

### 4.5 `GET /actions`

Returns the live registries — not a hard-coded list:

```json
{
  "apiVersion": 1,
  "capabilities": {
    "...": "as in /meta"
  },
  "fs": [
    {
      "name": "GET dir",
      "op": "dir",
      "method": "GET",
      "description": "fs.readdir",
      "mutating": false,
      "capability": null,
      "parameters": [
        {
          "name": "path",
          "type": "string",
          "required": true,
          "in": "query",
          "description": "virtual path, '/'-relative to the served root",
          "default": null
        },
        {
          "name": "recursive",
          "type": "boolean",
          "required": false,
          "in": "query",
          "description": "",
          "default": null
        }
      ]
    }
  ],
  "git": [
    {
      "name": "status",
      "description": "git status --porcelain, plus the current branch name",
      "mutating": false,
      "parameters": []
    }
  ]
}
```

### 4.6 `GET /stat`

| Param            | Type    | Default | Notes                                                |
|------------------|---------|---------|------------------------------------------------------|
| `path`           | string  | `/`     |                                                      |
| `lstat`          | boolean | `false` | do not follow symlinks                               |
| `throwIfNoEntry` | boolean | `true`  | `false` ⇒ `{path, exists:false}` instead of `ENOENT` |

The `etag` (when present) is also echoed in the `ETag` response header.

```json
{
  "path": "/src/main.kt",
  "type": "file",
  "size": 1234,
  "mtimeMs": 1730000000000,
  "atimeMs": 1730000000000,
  "ctimeMs": 1730000000000,
  "birthtimeMs": 1720000000000,
  "mode": 33188,
  "readOnly": false,
  "hidden": false,
  "dev": 0,
  "ino": 0,
  "nlink": 1,
  "uid": 0,
  "gid": 0,
  "rdev": 0,
  "blksize": 4096,
  "blocks": 3,
  "etag": "W/\"4d2-19297c1a800\"",
  "mimeType": "text/plain",
  "exists": true
}
```

* `type` ∈ `file` | `dir` | `symlink` | `other`.
* `mode` is **synthesised** from the type plus the read-only bit, so
  `isFile()`, `isDirectory()` and `mode & 0o200` behave sanely (`dir` 0755/0555, `file` 0644/0444, `symlink` 0777).
* `dev`, `ino`, `nlink`, `uid`, `gid`, `rdev` are always `0`/`1` and must not be relied upon.

### 4.7 `POST /stat` — batch stat

```json
{
  "paths": [
    "/a.txt",
    "/missing",
    "/src"
  ],
  "lstat": false
}
```

Returns an array, positionally aligned with `paths`:

```json
[
  {
    "ok": true,
    "stat": {
      "path": "/a.txt",
      "...": "..."
    }
  },
  {
    "ok": false,
    "error": {
      "code": "ENOENT",
      "...": "..."
    }
  },
  {
    "ok": true,
    "stat": {
      "path": "/src",
      "...": "..."
    }
  }
]
```

More than `limits.maxBatchOps` paths ⇒ `EINVAL`.

### 4.8 `GET /dir` — readdir

| Param       | Type    | Default                           | Notes                                          |
|-------------|---------|-----------------------------------|------------------------------------------------|
| `path`      | string  | `/`                               | must be a directory (`ENOTDIR` otherwise)      |
| `recursive` | boolean | `false`                           |                                                |
| `depth`     | int     | `maxDepth` if recursive, else `1` | clamped to `1..maxDepth`                       |
| `stat`      | boolean | `true`                            | include `size`/`mtimeMs`/`readOnly`/`mimeType` |

```json
{
  "path": "/src",
  "entries": [
    {
      "name": "lib",
      "path": "lib",
      "type": "dir",
      "size": 0,
      "mtimeMs": 1730000000000,
      "readOnly": false
    },
    {
      "name": "main.kt",
      "path": "lib/main.kt",
      "type": "file",
      "size": 1234,
      "mtimeMs": 1730000000000,
      "readOnly": false,
      "mimeType": "text/plain"
    }
  ],
  "truncated": false
}
```

`entry.path` is relative to the requested directory. Results are name-sorted, hidden entries omitted, and capped at
`limits.maxDirEntries` (`truncated: true`).

### 4.9 `GET`/`HEAD /file` — read

Query: `path` (**required**).

Request headers:

| Header                                     | Effect                            |
|--------------------------------------------|-----------------------------------|
| `Range: bytes=a-b`, `bytes=a-`, `bytes=-n` | single range only ⇒ `206`         |
| `If-None-Match`                            | matching ETag ⇒ `304`             |
| `If-Match`                                 | non-matching ETag ⇒ `EBUSY` (412) |

Response headers: `ETag` (weak, `size-mtime` in hex), `Last-Modified`,
`Accept-Ranges: bytes`, `Cache-Control: no-cache`, `X-Fs-Mime-Type`,
`X-Fs-Size`, `X-Fs-Mtime-Ms`, and `Content-Range` for `206`.
`Content-Type` is always `application/octet-stream`.

Malformed / unsatisfiable ranges (including any range on an empty file) ⇒
`ERANGE`. A directory ⇒ `EISDIR`.

```
curl -H 'Range: bytes=0-1023' \
  'http://localhost:8081/files/root/.fsapi/v1/file?path=/big.bin' -o head.bin
```

### 4.10 `PUT /file` — write

Body is the raw bytes. Query parameters:

| Param      | Type   | Default | Notes                                           |
|------------|--------|---------|-------------------------------------------------|
| `path`     | string | —       | required                                        |
| `flag`     | string | `w`     | one of `w`, `wx`, `a`, `ax`, `r+`, `w+`, `a+`   |
| `position` | long   | —       | seek before writing (overrides `Content-Range`) |

Also honoured: `Content-Range: bytes <start>-…` (start offset) and
`If-Match` / `If-None-Match: *` for optimistic concurrency.

Semantics:

* `w` / `w+` truncate; `a` / `a+` append; `r+` writes in place at `position` (or 0).
* `wx` / `ax` or `If-None-Match: *` on an existing file ⇒ `EEXIST`.
* `r*` flags on a missing file ⇒ `ENOENT`.
* `If-Match` on a missing file ⇒ `ENOENT`; mismatched ETag ⇒ `EBUSY`.
* Missing parent directories are created.
* `Content-Length` or actual bytes above `limits.maxFileSize` ⇒ `EFBIG`.

`201` when the file was created, `200` otherwise; `ETag` and `Last-Modified`
headers are set.

```json
{
  "path": "/notes.txt",
  "bytesWritten": 12,
  "size": 12,
  "etag": "W/\"c-19297c1a800\"",
  "mtimeMs": 1730000000000,
  "created": true
}
```

### 4.11 `DELETE /file` — unlink / rm / rmdir

| Param       | Type    | Default | Notes                                            |
|-------------|---------|---------|--------------------------------------------------|
| `path`      | string  | —       | required                                         |
| `recursive` | boolean | `false` | required for a non-empty directory               |
| `force`     | boolean | `false` | missing path becomes a no-op instead of `ENOENT` |

`204 No Content` on success. Non-empty directory without `recursive` ⇒
`ENOTEMPTY`. Recursive deletes first verify no descendant is read-only.

### 4.12 `POST /dir` — mkdir

```json
{
  "path": "/a/b/c",
  "recursive": true
}
```

`201` + `{"path":"/a/b/c","created":true}`, or `200` +
`{"created":false}` when the directory already existed and `recursive` is true. Existing path with `recursive:false`, or
an existing non-directory ⇒ `EEXIST`.

### 4.13 `POST /rename`

```json
{
  "from": "/old.txt",
  "to": "/new.txt",
  "overwrite": true
}
```

`204`. Both endpoints are validated for hidden/read-only status; the destination parent must already exist.
`overwrite:false` with an existing destination ⇒
`EEXIST`. Uses `ATOMIC_MOVE` where supported, falling back to a plain move.

### 4.14 `POST /copy`

```json
{
  "from": "/src",
  "to": "/dst",
  "recursive": true,
  "force": true,
  "preserveTimestamps": false
}
```

`204`. Directory source without `recursive` ⇒ `EISDIR`; existing destination without `force` ⇒ `EEXIST`. Missing
destination parents are created. Any single file above `limits.maxFileSize` ⇒ `EFBIG`. Hidden sources are skipped.

### 4.15 `POST /truncate`

```json
{
  "path": "/log.txt",
  "len": 0
}
```

`204`. Negative `len` ⇒ `EINVAL`; above `maxFileSize` ⇒ `EFBIG`; directory ⇒
`EISDIR`.

### 4.16 `POST /utimes`

```json
{
  "path": "/a.txt",
  "atimeMs": 1730000000000,
  "mtimeMs": 1730000000000
}
```

`atime` / `mtime` are accepted as aliases. `204` on success; `ENOSYS` when the
`utimes` capability is disabled or the platform lacks
`BasicFileAttributeView`. `null` fields are left unchanged.

### 4.17 `GET /realpath`

Query `path` (default `/`). Returns the canonical **virtual** path:

```json
{
  "path": "/src/main.kt",
  "type": "file"
}
```

### 4.18 `GET /resolve` — module resolution

Server-side CommonJS/ESM resolution, so a bare `require('pkg')` costs one round trip instead of ~15 `stat` calls.

| Param     | Type   | Default                 |
|-----------|--------|-------------------------|
| `request` | string | required                |
| `from`    | string | `/` (file or directory) |

Builtins (including `node:` prefixed):

```json
{
  "type": "builtin",
  "id": "path",
  "request": "node:path"
}
```

Files:

```json
{
  "type": "file",
  "path": "/node_modules/left-pad/index.js",
  "request": "left-pad",
  "format": "commonjs",
  "size": 512,
  "mtimeMs": 1730000000000
}
```

Algorithm: relative/absolute specifiers resolve as file-then-directory; bare specifiers walk `node_modules` up to the
root. Tried extensions:
`""`, `.js`, `.json`, `.mjs`, `.cjs`, `.node`; index files: `index.{js,json,mjs,cjs,node}`.
`package.json` `exports["."]` (`import`/`require`/`default`) is preferred over
`main`. `format` ∈ `json` | `module` | `commonjs` | `addon`, derived from the extension or the nearest `package.json`
`"type"`. Failure ⇒ `ENOENT`.

### 4.19 `GET /snapshot`

Streams a ZIP of a subtree — used to prime an in-memory client VFS.

| Param      | Type   | Default                                        |
|------------|--------|------------------------------------------------|
| `path`     | string | `/` (must be a directory)                      |
| `maxBytes` | long   | `limits.maxSnapshotBytes` (clamped down to it) |

Response: `application/zip`, `Content-Disposition: attachment; filename="<name>.zip"`,
`X-Fs-Snapshot-Root: <virtual path>`. Hidden entries are excluded. Files that would exceed the budget are skipped and
reported via
`X-Fs-Snapshot-Truncated: true` — note this trailer can be lost once the response is committed, so clients should also
verify against `stat`/`dir`.

### 4.20 `GET /watch` — SSE change stream

| Param       | Type    | Default |
|-------------|---------|---------|
| `path`      | string  | `/`     |
| `recursive` | boolean | `false` |

Response: `text/event-stream`, `Cache-Control: no-cache, no-transform`,
`X-Accel-Buffering: no`.

```
event: ready
data: {"path":"/src","recursive":true}

event: change
data: {"type":"change","path":"/src/main.kt","name":"main.kt","isDirectory":false}

event: overflow
data: {"path":"/src"}

: heartbeat
```

* `type` is `change` (`ENTRY_MODIFY`) or `rename` (create/delete).
* A comment heartbeat is emitted every 15 s of idleness.
* New sub-directories are auto-registered when `recursive=true`.
* Hidden paths never produce events.
* Limits: 32 concurrent watchers per JVM (`EMFILE`), 512 registered directories per stream (deeper directories are
  silently not watched).
* `ENOSYS` when `capabilities.watch != "sse"`.

### 4.21 `POST /exec`

**Remote code execution by design** — disabled unless an allowlist is configured.

```json
{
  "cmd": "git",
  "args": [
    "status",
    "--porcelain"
  ],
  "cwd": "/"
}
```

```json
{
  "cmd": "git",
  "args": [
    "status",
    "--porcelain"
  ],
  "cwd": "/",
  "code": 0,
  "signal": null,
  "stdout": " M a.txt\n",
  "stderr": "",
  "truncated": false
}
```

Hardening:

* `cmd` must be a bare allowlisted name (no `/`, `\`, control chars) ⇒ `EACCES`.
* When the allowlist entry declares sub-commands, `args[0]` must be one of them.
* Never routed through a shell.
* Rejected arguments (exact or `=`-prefixed): `-c`, `--exec`, `--upload-pack`,
  `--receive-pack`, `--upload-archive`, `--config`, `core.sshcommand`,
  `core.pager`, `core.editor`, `--output`.
* Control characters in arguments ⇒ `EINVAL`.
* `GIT_ASKPASS` removed, `GIT_TERMINAL_PROMPT=0`.
* `cwd` is resolved/contained like any other path; not a directory ⇒ `ENOTDIR`.
* Output capped at 1 MiB per stream (`truncated`); wall-clock timeout
  `execTimeoutMs` (default 30 s) ⇒ `EBUSY`.
* Empty allowlist ⇒ `ENOSYS`; non-allowlisted command ⇒ `EACCES`.

### 4.22 `POST /git`

Bridges the FS API onto the [git action registry](#5-git-actions).

```json
{
  "action": "commit",
  "params": {
    "message": "wip"
  }
}
```

If `params` is omitted the whole body is used as the parameter bag. Response is the action's own map (no `success`flag —
errors are FS API errors). Requires `"git"` in the exec allowlist, otherwise `ENOSYS`.

### 4.23 `POST /batch`

Pipelines several operations in one round trip.

```json
{
  "stopOnError": false,
  "ops": [
    {
      "op": "mkdir",
      "path": "/out",
      "recursive": true
    },
    {
      "op": "write",
      "path": "/out/a.txt",
      "data": "aGk=",
      "encoding": "base64"
    },
    {
      "op": "read",
      "path": "/out/a.txt",
      "offset": 0,
      "length": 2
    },
    {
      "op": "stat",
      "path": "/out/a.txt"
    }
  ]
}
```

```json
[
  {
    "ok": true,
    "value": {
      "path": "/out",
      "created": true
    }
  },
  {
    "ok": true,
    "value": {
      "path": "/out/a.txt",
      "bytesWritten": 2,
      "...": "..."
    }
  },
  {
    "ok": true,
    "value": {
      "path": "/out/a.txt",
      "encoding": "base64",
      "offset": 0,
      "size": 2,
      "etag": "...",
      "data": "aGk="
    }
  },
  {
    "ok": false,
    "error": {
      "code": "ENOENT",
      "...": "..."
    }
  }
]
```

Supported `op` values:

| `op`                    | Parameters                                                                      |
|-------------------------|---------------------------------------------------------------------------------|
| `stat`, `lstat`         | `path`, `throwIfNoEntry`                                                        |
| `exists`                | `path` → `{path, exists}`                                                       |
| `readdir`               | `path`, `recursive`, `depth`, `withFileTypes`                                   |
| `mkdir`                 | `path`, `recursive`                                                             |
| `rm`, `unlink`, `rmdir` | `path`, `recursive` (defaults true for `rm`), `force`                           |
| `rename`                | `from`, `to`, `overwrite`                                                       |
| `copy`                  | `from`, `to`, `recursive`, `force`, `preserveTimestamps`                        |
| `truncate`              | `path`, `len`                                                                   |
| `utimes`                | `path`, `atime`/`atimeMs`, `mtime`/`mtimeMs`                                    |
| `realpath`              | `path`                                                                          |
| `read`                  | `path`, `offset`, `length` → base64 payload                                     |
| `write`                 | `path`, `flag`, `data`, `encoding` (`base64` \| `base64url` \| `utf8` \| `hex`) |
| `resolve`               | `from`, `request`                                                               |
| `git`                   | `action`, `params`                                                              |

More than `limits.maxBatchOps` entries ⇒ `EINVAL`. Unknown `op` ⇒ `ENOSYS`.
`stopOnError: true` stops after the first failing entry (the array is short).

---

## 5. Git actions

Exposed through both `?gitAction=<name>` (form params) and
`POST /.fsapi/v1/git` (JSON `params`). All commands run in the repository root returned by `getGitRoot`; stderr is
merged into stdout.

| Action          | Params                                   | Mutating | Result keys                                                         |
|-----------------|------------------------------------------|----------|---------------------------------------------------------------------|
| `init`          | —                                        | yes      | `message`, `output`                                                 |
| `status`        | —                                        | no       | `branch`, `status` (porcelain)                                      |
| `add`           | `filePath` (default `.`)                 | yes      | `message`, `output`                                                 |
| `commit`        | `message`                                | yes      | `message`, `output` (runs `git add -A` first)                       |
| `log`           | `count` (default 20)                     | no       | `log`                                                               |
| `diff`          | —                                        | no       | `unstaged`, `staged`                                                |
| `reset`         | `filePath`                               | yes      | `message`, `output` (`checkout --` + `reset --hard` + `clean -fdx`) |
| `branches`      | —                                        | no       | `currentBranch`, `branches`                                         |
| `create-branch` | `branchName`*, `checkout` (default true) | yes      | `message`, `output`                                                 |
| `switch-branch` | `branchName`*                            | yes      | `message`, `output`                                                 |
| `delete-branch` | `branchName`*, `force`                   | yes      | `message`, `output`                                                 |
| `describe`      | —                                        | no       | `actions` (full self-description)                                   |

Branch names must match `[A-Za-z0-9._/-]+`, must not start with `-` and must not contain `..` — this blocks
option/argument injection.

`GitOperationHandler.isGitRepository` walks up from the directory looking for
`.git`, which is what drives the "Initialize Git Repository" prompt in the UI.

---

## 6. Configuration

### 6.1 `FsApiConfig`

Returned per-request by `FilesystemServlet.getFsApiConfig(req)`.

| Field                 | Default          | Purpose                                         |
|-----------------------|------------------|-------------------------------------------------|
| `readOnly`            | `false`          | all mutations ⇒ `EROFS`                         |
| `execAllowlist`       | `{}`             | `cmd -> allowed sub-commands` (empty set = any) |
| `watchMode`           | `"sse"`          | `sse` \| `poll` \| `none`                       |
| `utimesEnabled`       | `true`           |                                                 |
| `snapshotEnabled`     | `true`           |                                                 |
| `resolveEnabled`      | `true`           |                                                 |
| `maxFileSize`         | 50 MiB           |                                                 |
| `maxRequestSize`      | 100 MiB          |                                                 |
| `maxBatchOps`         | 256              |                                                 |
| `maxDirEntries`       | 50 000           |                                                 |
| `maxDepth`            | 32               |                                                 |
| `maxSnapshotBytes`    | 32 MiB           |                                                 |
| `execTimeoutMs`       | 30 000           |                                                 |
| `requireApiHeader`    | `true`           | CSRF mitigation for mutating requests           |
| `cwd` / `tmpdir`      | `/` / `/.tmp`    | advertised to the client                        |
| `caseSensitive`       | platform-derived |                                                 |
| `crossOriginIsolated` | `false`          |                                                 |
| `syncStrategy`        | `"xhr"`          | `sab` \| `xhr` \| `snapshot`                    |

Default git allowlist (`FilesystemServlet.GIT_SUBCOMMANDS`): `init`, `status`,
`log`, `diff`, `show`, `branch`, `checkout`, `switch`, `restore`, `add`,
`commit`, `reset`, `clean`, `rev-parse`, `ls-files`, `ls-tree`, `describe`,
`merge-base`, `stash`, `tag`, `blame`, `shortlog`, `config`.

### 6.2 Servlet extension points

`FileServlet` (classic surface):

* `getDir(req, resp)` — **required**; the served directory.
* `getZipLink(req, path)`, `getToolbarActions`, `getFileActions`,
  `getFolderActions`, `getAdditionalSections`, `getAdditionalStyles`,
  `getAdditionalScripts`, `listContents`
* `isGitEnabled(req)`, `getGitRoot(req, resp)`

`FilesystemServlet` (adds FS API v1):

* `getFsApiRoot(req, resp)` — defaults to `getDir`
* `getFsApiConfig(req)`
* `isFsApiEnabled(req)` — `false` ⇒ `ENOSYS` for the whole FS API

`SimpleFilesystemServlet(baseDir, gitEnabled, readOnly, zipEndpoint)` is a ready-made implementation.

> **Note** — the FS API is dispatched from `service()` and therefore bypasses
> `doPost`/`doPut`/`doDelete` overrides. Read-only mode must be declared through
> `getFsApiConfig(readOnly = true)`, not by overriding the `do*` methods.

### 6.3 Extending the operation set

`FsAction` and `GitAction` are `DynamicEnum`s, so downstream modules can add or replace operations at runtime; they are
dispatched automatically and appear in
`GET /.fsapi/v1/actions`.

```kt
FsAction.register(
  FsAction(
    op = "hash",
    method = "GET",
    description = "sha256 of a file",
    parameters = listOf(ActionParam("path", required = true))
  ) { ctx ->
    /* ctx.root is validated; ctx.config carries limits/capabilities */
  }
)

GitAction.register(
  GitAction("fetch", "git fetch --all", mutating = true) { ctx ->
    mapOf("output" to ctx.git("git", "fetch", "--all"))
  }
)
```

Pass `replace = true` to override a built-in with the same key (`"<METHOD> <op>"` for `FsAction`, the name for
`GitAction`).

---

## 7. CLI

```
Usage: FileServerCli [options] [directory]

  -p, --port <n>     Port to listen on (default 8081, 0 = random free port)
  -h, --host <addr>  Interface to bind (default 127.0.0.1, 0.0.0.0 for all)
      --no-git       Disable Git UI/API features
      --read-only    Disable uploads, edits and deletes
      --help         Show this message
```

It mounts:

| Path          | Servlet                                                         |
|---------------|-----------------------------------------------------------------|
| `/files/*`    | `SimpleFileServlet` (or `ReadOnlyFileServlet`)                  |
| `/zip`        | `StaticZipServlet` rooted at the parent of the served directory |
| `/`, `/files` | redirect to `/files/root/`                                      |

On start it prints the browser URL and the FS API meta URL:

```
Serving /home/me/project
  ->  http://localhost:8081/
  FS API v1 -> http://localhost:8081/files/root/.fsapi/v1/meta
```

`--read-only` sets both `FsApiConfig(readOnly = true)` (⇒ `EROFS`) and a blanket
`403 Server is running in read-only mode` for classic `POST`/`PUT`/`DELETE`.

---

## 8. Cookbook

```bash
BASE=http://localhost:8081/files/root/.fsapi/v1

# capabilities
curl -s "$BASE/meta" | jq .

# list a tree
curl -s "$BASE/dir?path=/src&recursive=true&depth=3" | jq '.entries | length'

# read a byte range
curl -s -H 'Range: bytes=0-99' "$BASE/file?path=/big.bin" -o head.bin

# create a file (mutating -> X-Fs-Api header required)
curl -s -X PUT -H 'X-Fs-Api: 1' --data-binary 'hello' "$BASE/file?path=/hi.txt"

# optimistic-concurrency update
ETAG=$(curl -sI "$BASE/file?path=/hi.txt" | awk '/^ETag/{print $2}' | tr -d '\r')
curl -s -X PUT -H 'X-Fs-Api: 1' -H "If-Match: $ETAG" \
     --data-binary 'hello again' "$BASE/file?path=/hi.txt"

# mkdir -p
curl -s -X POST -H 'X-Fs-Api: 1' -H 'Content-Type: application/json' \
     -d '{"path":"/a/b/c","recursive":true}' "$BASE/dir"

# rm -rf
curl -s -X DELETE -H 'X-Fs-Api: 1' "$BASE/file?path=/a&recursive=true&force=true"

# resolve a module
curl -s "$BASE/resolve?request=left-pad&from=/src/index.js" | jq .

# follow changes
curl -N "$BASE/watch?path=/src&recursive=true"

# git via the FS API
curl -s -X POST -H 'X-Fs-Api: 1' -H 'Content-Type: application/json' \
     -d '{"action":"status"}' "$BASE/git" | jq .
```

---

## 9. Design invariants

1. **The client shim is not a security boundary.** All access control lives in
   `FileAccessControl` + `FsPath` and is applied identically to every operation on both surfaces.
2. **Additive and versioned.** The FS API never changes the classic v1 surface; breaking changes bump `/.fsapi/v<N>/`.
3. **One canonical root per mount.** `/` in the FS API is exactly
   `getFsApiRoot()`; two-path operations validate *both* endpoints.
4. **No magic.** The FS API performs no content substitution or rendering — what
   `stat` reports is what is on disk.
5. **Cache coherency.** Every mutation invalidates `FileChannelCache` for the affected path (recursively for trees), so
   a deleted-and-recreated file is never served from a stale channel.