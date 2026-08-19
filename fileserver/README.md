# Cognotik Web UI File Servlet Module

A lightweight, extensible, embeddable **file browser / file manager / editor / Git UI** built on top of the Jakarta
Servlet API. It provides a full-featured directory browsing experience (upload, download, delete, edit, ZIP export, Git
operations, Markdown rendering) with almost no external dependencies beyond a servlet container, Monaco Editor (loaded
from CDN), and a couple of small utility libraries. This module is designed to be **subclassed** — you provide a
directory resolution strategy (`getDir(request, response): File?`) and the abstract `FileServlet` handles the rest:
directory listings, uploads, downloads, in-browser editing, Git integration, and access control.
---

## Table of Contents

- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [Package Layout](#package-layout)
- [Getting Started](#getting-started)
- [Quickstart & Standalone IDE](#quickstart--standalone-ide)
    - [Minimal Example](#minimal-example)
    - [Registering the Servlet](#registering-the-servlet)
- [Core Concepts](#core-concepts)
    - [FileServlet Lifecycle](#fileservlet-lifecycle)
    - [Directory Listing Rendering](#directory-listing-rendering)
    - [File Editing (Monaco)](#file-editing-monaco)
    - [Markdown Rendering](#markdown-rendering)
    - [ZIP Export](#zip-export)
    - [Git Integration](#git-integration)
    - [File Access Control (.hidden / .readonly / .writeable)](#file-access-control-hidden--readonly--writeable)
    - [Large File Streaming](#large-file-streaming)
- [Extension Points](#extension-points)
- [HTTP API Reference](#http-api-reference)
    - [GET](#get)
    - [HEAD](#head)
    - [POST](#post)
    - [PUT](#put)
    - [DELETE](#delete)
    - [Git Actions (POST gitAction=...)](#git-actions-post-gitaction)
- [Security Considerations](#security-considerations)
- [Customization Cookbook](#customization-cookbook)
- [Dependencies](#dependencies)
- [License](#license)

---

## Quickstart & Standalone IDE

To launch the standalone web IDE in any workspace directory using the one-line shell runner script:

```bash
# Launch in current directory with default settings (http://localhost:8081)
curl -sSL -o cognotik https://raw.githubusercontent.com/SimiaCryptus/Cognotik/refs/heads/main/cli/bin/cognotik
chmod +x cognotik
./cognotik fileserver .
```

Or execute via Gradle within the project:

```bash
./gradlew :fileserver:fileserver -PserverArgs="--port 8081 /path/to/workspace"
```

The standalone server is powered by `com.simiacryptus.cognotik.webui.servlet.FileServerCli`. For complete CLI parameter reference, security lockdown flags (`--secure`, `--read-only`, `--no-terminal`, `--no-exec`), and detailed launcher documentation, see **[README-cli.md](README-cli.md)**.

---

## Features

- 📁 **Directory listings** with a clean, responsive, dark/light/auto-themed UI (no build step, inline CSS/JS).
- ⬆️ **File upload** via drag-and-drop, click-to-browse, or clipboard paste (Ctrl+V).
- ⬇️ **ZIP export** of any directory (or single file) via `StaticZipServlet`.
- ✏️ **In-browser code editing** using the Monaco Editor (VS Code's editor), with language auto-detection, dirty-state
  tracking, Ctrl+S save, and read-only enforcement.
- 📄 **Markdown rendering** to HTML or PDF (via `openhtmltopdf` + `flexmark`) — request `foo.html`, `foo.pdf`, or
  `foo.txt` and it will transparently render `foo.md` if the literal file doesn't exist.
- 🔀 **Git integration** — status, diff (staged/unstaged), log, add, commit, reset, branch list/create/switch/delete, and
  repository initialization, all driven through a small AJAX + JSON API and rendered in-page.
- 🔒 **Fine-grained access control** using `.gitignore`-style marker files: `.hidden`, `.readonly`, and `.writeable_`.
- 🚀 **Efficient file serving** via a shared `FileChannel` cache (Guava `LoadingCache`) with async, non-blocking writes
  for both small and large files (memory-mapped I/O for files > 1MB).
- 🧩 **Fully extensible** — override hooks let you inject custom toolbar buttons, per-file/per-folder actions, additional
  sections, styles, and scripts without forking the core logic.

---

## Architecture Overview

```
                      ┌─────────────────────────┐
                      │      FileServlet         │  (abstract, extend this)
                      │  doGet/doHead/doPost/     │
                      │  doPut/doDelete           │
                      └────────────┬─────────────┘
                                   │ delegates to
      ┌────────────────┬──────────┼───────────────┬───────────────────┐
      ▼                ▼          ▼               ▼                   ▼
FileRequestHandler  FileUploadHandler  FileDeleteHandler  GitOperationHandler  FileAccessControl
(serve/stream files, (multipart upload,  (delete file/dir)  (git CLI wrapper,   (.hidden/.readonly/
 _files.json)         PUT raw body)                          JSON responses)    .writeable rules)
      │
      ▼
render/
├── DirectoryListingRenderer  – HTML shell for directory pages
├── MonacoEditorRenderer      – standalone editor page
├── MarkdownRenderer          – Markdown → HTML/PDF
└── git/
    ├── GitHtml     – Git panel markup
    ├── GitStyles   – Git panel CSS
    └── GitScripts  – Git panel JS (fetch-based AJAX calls back into gitAction handler)

util/
├── PathUtils          – path parsing/validation, JSON escaping
├── MimeTypeResolver    – content-type resolution
└── FileChannelCache    – Guava-backed cache of open FileChannels

StaticZipServlet – standalone servlet for on-demand ZIP downloads (session/path based)
```

---

## Package Layout

```
com.simiacryptus.cognotik.webui.servlet
├── FileServlet.kt                 // abstract servlet — the main entry point
├── StaticZipServlet.kt            // standalone ZIP-download servlet
├── handler/
│   ├── FileAccessControl.kt       // .hidden / .readonly / .writeable enforcement
│   ├── FileDeleteHandler.kt       // DELETE logic
│   ├── FileRequestHandler.kt      // streaming GET, _files.json
│   ├── FileUploadHandler.kt       // POST (multipart) and PUT (raw body)
│   └── GitOperationHandler.kt     // git CLI wrapper + JSON responses
├── render/
│   ├── DirectoryListingRenderer.kt
│   ├── MonacoEditorRenderer.kt
│   ├── MarkdownRenderer.kt
│   └── git/
│       ├── GitHtml.kt
│       ├── GitStyles.kt
│       └── GitScripts.kt
└── util/
    ├── PathUtils.kt
    ├── MimeTypeResolver.kt
    └── FileChannelCache.kt
```

---

## Getting Started

### Minimal Example

`FileServlet` is abstract — you must implement `getDir()` to resolve the base directory to serve for a given request
(e.g. per-session sandboxing, per-user home directory, etc.).

```kotlin
import com.simiacryptus.cognotik.webui.servlet.FileServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File

class MyFileBrowserServlet(private val root: File) : FileServlet() {
  override fun getDir(request: HttpServletRequest, response: HttpServletResponse): File? {
    // Return the directory this request is allowed to browse.
    // You could key this off a session id, auth principal, etc.
    return root
  }
}
```

### Registering the Servlet

With a plain Jetty `ServletContextHandler`:

```kotlin
val context = ServletContextHandler(ServletContextHandler.SESSIONS)
context.contextPath = "/"
context.addServlet(ServletHolder(MyFileBrowserServlet(File("/data/workspace"))), "/files/*")
context.addServlet(ServletHolder(StaticZipServlet(dataStoragePath = "/data/sessions")), "/zip")
```

Because paths are matched with `/*`, `request.pathInfo` drives the relative file path inside the servlet (e.g.
`GET /files/subdir/file.txt`). For the ZIP servlet, call it like:

```
GET /zip?session=<sessionId>&path=/some/subdir
```

---

## Core Concepts

### FileServlet Lifecycle

`FileServlet` overrides all five relevant HTTP verbs:

| Verb     | Behavior                                                                                                                                                                                                               |
|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET`    | Serves files, virtual `_files.json` listings, Markdown-to-HTML/PDF/TXT fallback rendering, the Monaco editor (`?edit=1`), or a full directory listing page. Redirects bare directory requests to add a trailing slash. |
| `HEAD`   | Mirrors `GET`'s content-type/length decisions without a body.                                                                                                                                                          |
| `POST`   | Either a Git action (`gitAction` parameter) or a multipart file upload into the resolved directory.                                                                                                                    |
| `PUT`    | Raw-body file write/overwrite at the target path (used by the Monaco editor's Save button).                                                                                                                            |
| `DELETE` | Deletes a file or recursively deletes a directory.                                                                                                                                                                     |

All path segments are parsed and validated through `PathUtils.parsePath`, which rejects `..`, `:`, `/`, `~`,
`\`, and control characters in individual path segments — preventing path traversal.

### Directory Listing Rendering

`DirectoryListingRenderer` produces a self-contained HTML page (no external CSS/JS framework) featuring:

- A themeable navbar (`data-theme="auto|light|dark"`, backed by `/modules/theme.js` + CSS custom properties and
  `prefers-color-scheme` media query).
- Breadcrumb navigation (`generateBreadcrumbs`).
- A drag-and-drop / click / paste **upload widget**.
- **Folders** and **Files** sections, each item annotated with:
    - a link to browse/download,
    - (for Markdown files) a "View as HTML" link,
    - an "✏️ Edit" link (opens Monaco editor via `?edit=1`),
    - optional custom actions (see [Extension Points](#extension-points)),
    - a delete button (hidden if the item is read-only).
- Extension slots: `toolbarActions`, `additionalSections`, `additionalStyles`, `additionalScripts`.

### File Editing (Monaco)

Requesting any existing file with `?edit=1` (or any non-`"false"` value) serves a full-page Monaco Editor
(`MonacoEditorRenderer`) instead of the raw file:

- Automatically detects language from file extension (`.kt` → kotlin, `.ts` → typescript, etc.).
- Populates a language dropdown from Monaco's full language registry.
- Tracks dirty state (`●` indicator) and warns on navigation away with unsaved changes.
- **Ctrl/Cmd+S** or the **💾 Save** button issues a `PUT` back to the same URL with the raw editor content.
- Enforces read-only mode transparently when `FileAccessControl.isReadOnly` is true for the file.
- Binary files are rejected server-side (`isBinaryFile` sniffs for NUL bytes in the first 8KB) with a 400.

### Markdown Rendering

If a request targets `foo.html`, `foo.pdf`, or `foo.txt` and that literal file does not exist, but a sibling
`foo.md` does, `MarkdownRenderer` will:

- For `.html`: parse Markdown (via `flexmark-java`, with the Tables extension) and wrap it in a themed HTML shell
  (light/dark/auto via CSS variables + `/modules/theme.js`).
- For `.pdf`: render the same HTML via `openhtmltopdf` (`PdfRendererBuilder`) and stream a PDF.
- For `.txt`: stream the raw Markdown source as `text/plain`.

### ZIP Export

`StaticZipServlet` is a **standalone servlet** (not part of `FileServlet`) intended for exporting whole session
directories or subdirectories as a ZIP archive:

```
GET /zip?session=<sessionId>&path=<relative-path-or-/>
```

- Resolves `dataStoragePath/<session>/<path>`.
- Recursively zips all files (skipping dot-files) into a temp file, then streams it with
  `Content-Disposition: attachment`.
- Returns `404` if the target doesn't exist, `400` if `session` is missing.

> **Note:** `FileServlet` itself exposes a `getZipLink()` hook so subclasses can wire the "Download as ZIP"
> button in the navbar to a URL like the one above (or their own implementation).

### Git Integration

When `isGitEnabled(req)` returns `true` (default) and `getGitRoot(req, response)` resolves to a Git repository (walking
up parent directories looking for `.git`), the directory listing page includes a **Git panel**
(`GitHtml`, styled by `GitStyles`, scripted by `GitScripts`) offering:

- **Status** (`git status --porcelain` + current branch badge)
- **Diff** (unstaged / staged tabs, syntax-highlighted +/-/@@ lines)
- **Log** (`git log --oneline -n <count>`)
- **Stage All** / **Commit** (with a modal dialog, Ctrl+Enter to submit)
- **Reset** (discard all changes — `checkout --`, `reset --hard`, `clean -fdx`, with a confirmation prompt)
- **Branches**: list, create (`checkout -b` or `branch`), switch (`checkout`), delete (`-d`/`-D` with a force-delete
  fallback if the branch isn't merged)
- If no repository exists, an **"🚀 Initialize Git Repository"** prompt is shown instead (`git init`). All Git operations
  are invoked client-side via `fetch` to the *same page URL* with `POST` body
  `gitAction=<action>&...params`, handled server-side by `GitOperationHandler`, which shells out to the `git`
  CLI (`ProcessBuilder`) and returns JSON (`{"success": true, "output": "...", ...}`).

### File Access Control (.hidden / .readonly / .writeable)

`FileAccessControl` implements `.gitignore`-style access rules using marker files that can be placed anywhere in the
directory tree between the served base directory and the target path:

| Marker file  | Effect                                                                                                                                              |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `.hidden`    | Paths matching the patterns inside are treated as **non-existent** (404) for all HTTP verbs. The marker file itself is always hidden.               |
| `.readonly`  | Paths matching the patterns inside **cannot be modified** (upload/PUT/DELETE all rejected with 403). The marker file itself is always read-only.    |
| `.writeable_` | Acts as a **whitelist** — when present, anything *not* matched by its patterns is treated as read-only. The marker file itself is always read-only. |

Pattern matching and file discovery reuse `IgnoreFileUtil` (compiled ignore-style regex patterns), matched against the
path relative to the directory containing the marker file, the bare filename, and each path segment individually — so a
single pattern like `secrets` will match `secrets`, `a/secrets/b.txt`, etc. These checks are consistently enforced
across `doGet` (hidden ⇒ 404), `doPost`/upload,
`doPut`, and `doDelete` (readonly ⇒ 403, hidden ⇒ 404), as well as suppressing the "🗑️ Delete" button in the rendered
listing for read-only items.

### Large File Streaming

`FileRequestHandler.serveFile` uses `FileChannelCache` (a Guava `LoadingCache<File, FileChannel>`, max size 100, 10s
expire-after-access) to avoid repeatedly opening/closing file handles for hot files. Serving is fully asynchronous
(`AsyncContext` + `WriteListener`):

- Files **≤ 1MB**: streamed in 16KB chunks directly from the `FileChannel`.
- Files **> 1MB**: memory-mapped (`FileChannel.map`) and streamed in 256KB chunks for reduced syscall overhead. Channels
  are returned to the cache after use (or on error) so subsequent requests can reuse them; if a cached channel has been
  closed externally, it's transparently refreshed.

---

## Extension Points

`FileServlet` exposes several `open` methods with no-op defaults, meant to be overridden by subclasses:

| Method                                                    | Purpose                                                                                              |
|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `getFileActions(file, req): String`                       | Extra HTML injected after each file's link/edit-link in the listing.                                 |
| `getFolderActions(folder, req): String`                   | Extra HTML injected after each folder's link in the listing.                                         |
| `getToolbarActions(req, currentPath): String`             | Extra buttons in the top navbar (alongside ZIP link / theme selector).                               |
| `getAdditionalSections(dir, req, currentPath): String`    | Extra `<div class="section">` blocks rendered between the upload widget and the Folders/Files lists. |
| `getAdditionalStyles(): String`                           | Extra raw CSS appended into the page `<style>` block.                                                |
| `getAdditionalScripts(): String`                          | Extra raw JS appended into the page `<script>` block.                                                |
| `isGitEnabled(req): Boolean`                              | Toggle the Git panel entirely (default `true`).                                                      |
| `getGitRoot(req, response): File?`                        | Override the directory used for Git repo discovery (default: same as `getDir`).                      |
| `getZipLink(req, filePath): String`                       | Provide the href for the "Download as ZIP" button (default: empty ⇒ button hidden).                  |
| `listContents(file, req, response): Pair<String, String>` | Override the entire files/folders HTML generation strategy if needed.                                |

---

## HTTP API Reference

All paths below are relative to wherever you mount the servlet (e.g. `/files/*`).

### GET

- `GET /<path>/` → directory listing page (HTML).
- `GET /<path>/file.ext` → serves the file with a resolved MIME type (see `MimeTypeResolver`).
- `GET /<path>/file.ext?edit=1` → Monaco editor page for that file (any non-`"false"` value works).
- `GET /<path>/_files.json` (when no literal `_files.json` file exists) → JSON directory metadata:
  ```json
  {
    "path": "dirname",
    "totalFiles": 3,
    "totalFolders": 1,
    "entries": [
      {"name": "a.txt", "type": "file", "size": 123, "lastModified": 169..., "mimeType": "text/plain"},
      {"name": "sub", "type": "directory", "lastModified": 169...}
    ]
  }
  ```
- `GET /<path>/foo.html|.pdf|.txt` (when literal file doesn't exist) → renders sibling `foo.md` if present.
- Non-trailing-slash directory requests are redirected (`302`) to add the trailing slash.
- Hidden paths (`.hidden` rules) always return `404`.

### HEAD

Mirrors `GET`'s content-type/length/status decisions without writing a response body.

### POST

- **Multipart file upload** (default, `enctype="multipart/form-data"`, field name `file`):
    - `400` if no file/filename, or filename fails `PathUtils.isValidFileName`.
    - `403` if target directory/file is read-only or hidden.
    - `409` if the target file already exists (no silent overwrite via POST).
    - `200` + `{"success": true, "message": "...", "filename": "..."}` on success.
- **Git action** (`Content-Type: application/x-www-form-urlencoded`, parameter `gitAction=<action>`):
  see [Git Actions](#git-actions-post-gitaction) below.

### PUT

- Raw request body is written verbatim to the target file (creating parent directories as needed).
- `400` for invalid/empty filename or if the target is an existing directory.
- `403` if hidden or read-only.
- `200` with `"message": "File updated successfully"` if the file existed, or `201` with
  `"message": "File created successfully"` otherwise.
- Invalidates the `FileChannelCache` entry for the file so subsequent GETs see fresh content.

### DELETE

- Deletes a file, or recursively deletes a directory (`File.deleteRecursively()`).
- `404` if hidden/nonexistent, `403` if read-only.
- `200` + `{"success": true, "message": "..."}` on success, `500` on failure.

### Git Actions (`POST gitAction=...`)

All responses are `application/json`. Common shape: `{"success": bool, "message"?: string, ...action-specific fields}`.

| `gitAction`     | Params                                        | Response fields                               |
|-----------------|-----------------------------------------------|-----------------------------------------------|
| `init`          | —                                             | `output`                                      |
| `status`        | —                                             | `branch`, `status` (porcelain)                |
| `add`           | `filePath` (default `.`)                      | `output`                                      |
| `commit`        | `message`                                     | `output` (runs `git add -A` first)            |
| `log`           | `count` (default `20`)                        | `log`                                         |
| `diff`          | —                                             | `unstaged`, `staged`                          |
| `reset`         | `filePath` (optional; defaults to whole tree) | `output` (checkout, reset --hard, clean -fdx) |
| `branches`      | —                                             | `currentBranch`, `branches`                   |
| `create-branch` | `branchName`, `checkout` (`"true"`/`"false"`) | `output`                                      |
| `switch-branch` | `branchName`                                  | `output`                                      |
| `delete-branch` | `branchName`, `force` (`"true"`/`"false"`)    | `output` (`-d`/`-D`)                          |

Errors return `500` with `{"success": false, "message": "<exception message>"}`; unknown actions return `400`.
---

## Security Considerations

- **Path traversal**: `PathUtils.parsePath` rejects `..`, `:`, `/`, `~`, `\`, and control characters in any path segment
  before it's ever turned into a `File`.
- **Filename validation**: uploads and PUT targets are validated with `PathUtils.isValidFileName` (no `..`,
  `/`, `\`, `:`, `~`, blank, or control characters).
- **Hidden/read-only enforcement** is applied consistently on every verb (see
  [File Access Control](#file-access-control-hidden--readonly--writeable)) — always check `FileAccessControl`
  before trusting a resolved `File` in custom overrides.
- **Binary file protection**: the Monaco editor refuses to open files containing NUL bytes in their first 8KB,
  preventing accidental corruption of binaries via the text editor / PUT round-trip.
- **No overwrite via POST upload**: existing files return `409 Conflict`; use `PUT` to intentionally overwrite.
- Git commands are executed via `ProcessBuilder` with explicit argument arrays (no shell interpolation), but since this
  exposes a **generic git CLI wrapper** over HTTP, you should ensure this servlet is only reachable by
  trusted/authenticated users in your deployment (there is no built-in auth layer here).
- The `.readonly` / `.hidden` / `.writeable_` marker files themselves are always protected (cannot be viewed, edited, or
  deleted through the servlet), preventing users from disabling their own restrictions.

---

## Customization Cookbook

**Add a custom toolbar button:**

```kotlin
override fun getToolbarActions(req: HttpServletRequest, currentPath: String): String =
  """<button class="zip-link" onclick="alert('Hi!')" style="background-color:#20c997;">👋 Hello</button>"""
```

**Provide a working ZIP download link (wired to `StaticZipServlet`):**

```kotlin
override fun getZipLink(req: HttpServletRequest, filePath: String): String =
  "/zip?session=${req.session.id}&path=${java.net.URLEncoder.encode(filePath, "UTF-8")}"
```

**Disable Git for a particular deployment:**

```kotlin
override fun isGitEnabled(req: HttpServletRequest): Boolean = false
```

**Add a per-file "Download" action distinct from the default link:**

```kotlin
override fun getFileActions(file: File, req: HttpServletRequest): String =
  """<a class="action-link" href="${file.name}" download>⬇️ Download</a>"""
```

**Mark a subdirectory read-only:** create an empty `.readonly` file at the root of that directory containing a single
line `*` (matches everything under that directory), or target specific patterns:

```
# .readonly
*.lock
generated/
```

**Whitelist only specific writeable paths** (everything else becomes read-only) by adding a `.writeable_`
file with the allowed patterns.
---

## Dependencies

- **Jakarta Servlet API** (`jakarta.servlet.*`) — servlet contracts, multipart handling.
- **Guava** (`com.google.common.cache`) — `FileChannelCache` LRU/TTL cache.
- **Eclipse Jetty** (`org.eclipse.jetty.http.MimeTypes`) — default MIME type lookups.
- **flexmark-java** (`com.vladsch.flexmark`) — Markdown parsing (with Tables extension).
- **openhtmltopdf** (`com.openhtmltopdf`) — HTML → PDF rendering for Markdown-to-PDF.
- **SLF4J** — logging throughout.
- **Monaco Editor** (loaded client-side from `cdn.jsdelivr.net`) — in-browser code editing.
- Client-side theme toggling expects a `/modules/theme.js` script exposing a global `ThemeManager` with
  `init()` and `bindSelector(selectElement)`.
- Git integration shells out to the **`git`** CLI binary — it must be installed and on `PATH` for the host process.

---

## License

This module is part of the `cognotik` project. See the top-level project license for terms.