---
documents:
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/FileServlet.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/webui/servlet/SessionFileServlet.kt
---

# FileServelet

The `FileServlet` and `SessionFileServlet` classes provide HTTP-based file browsing, serving, uploading, and management
capabilities for the Cognotik web UI. `SessionFileServlet` extends `FileServlet` with session-aware directory resolution
and integrated Git version control operations.

## FileServlet

`FileServlet` is an abstract `HttpServlet` that serves files from a directory, renders Markdown content, generates
directory listings, supports file uploads, file creation/update via PUT, file/directory deletion via DELETE, and
integrated Git version control. It is annotated with `@MultipartConfig` supporting uploads up to 50MB per file and
100MB per request (with a 2MB file size threshold for in-memory buffering).

### Core Concepts

- **Abstract `getDir(req)`** — Subclasses must implement this to resolve the base directory for a given request.
- **Path Parsing** — The `parsePath()` companion function splits URL paths into segments and validates them, rejecting
- **File Channel Caching** — A Guava `LoadingCache` (`channelCache`) maintains open `FileChannel` instances keyed by
  path traversal attempts (`..`), special characters (`:`, `/`, `~`, `\\`), and non-printable or non-ASCII characters
  (code < 32 or > 126).
  `File`, with a maximum size of 100 entries and a 10-second expiry after last access. Channels are automatically closed
  on eviction.

### HTTP Methods

#### GET — File Serving and Directory Listing

The `doGet` method handles several scenarios based on the resolved file:

1. **Virtual `_files.json`** — If the requested file is named `_files.json` and does not exist on disk, a JSON directory
   listing is generated dynamically for the parent directory. The JSON includes:
    - `path` — directory name
    - `totalFiles` / `totalFolders` — counts
    - `entries` — array of objects with `name`, `type`, `size` (files only), `lastModified`, and `mimeType` (files only)
2. **Markdown Rendering** — If a requested `.html`, `.pdf`, or `.txt` file does not exist but a corresponding `.md` file
   does:
    - `.html` — Renders the Markdown to HTML with a simple styled wrapper page
    - `.pdf` — Renders the Markdown to HTML, then converts to PDF using OpenHTMLToPDF (`PdfRendererBuilder`)
    - `.txt` — Serves the raw Markdown content as `text/plain`
3. **Direct File Serving** — Existing files are served with appropriate MIME types. The servlet uses two strategies
   based on file size:
    - **Small files (≤ 1MB)** — Read via `FileChannel` into a 16KB buffer using async I/O (`WriteListener`)
    - **Large files (> 1MB)** — Memory-mapped via `MappedByteBuffer` with a 256KB buffer using async I/O
4. **Directory Listing** — If the path resolves to a directory, an HTML page is rendered with:
    - A navigation bar with an optional ZIP download link
    - Configurable toolbar actions (via `getToolbarActions()` and Git toolbar when enabled)
    - Breadcrumb navigation
    - A file upload section with drag-and-drop, click-to-select, and clipboard paste (Ctrl+V) support
    - Folders and files sections with icons (📁 for folders, 📄 for files)
    - Markdown files get an additional "View as HTML" link (🌐)
    - Per-file and per-folder action links (via `getFileActions()` and `getFolderActions()`)
    - Git version control section (when `isGitEnabled()` returns true)
    - Additional configurable sections, styles, and scripts (via extension points)
5. **Directory Redirect** — Paths to directories without a trailing `/` are redirected with a trailing slash appended.

#### POST — File Upload

The `doPost` method handles multipart file uploads and Git operations:

- Extracts the `file` part from the multipart request
- Validates the filename (rejects path traversal, special characters, non-printable characters)
- **Does not allow overwriting** — returns `409 Conflict` if the file already exists
- Returns a JSON success response on completion
- **Git operations** — If a `gitAction` parameter is present and Git is enabled, the request is dispatched to
  `handleGitOperation()` (see Git Version Control section below)

#### PUT — File Create/Update

The `doPut` method supports creating or updating files by writing the request body directly:

- Validates the target path and filename
- Creates parent directories if they don't exist
- Invalidates the channel cache for existing files before overwriting
- Returns `201 Created` for new files or `200 OK` for updates
- Returns a JSON response indicating success
- Rejects writes to directories

#### DELETE — File/Directory Deletion
The `doDelete` method supports deleting files and directories:
- Validates the target path
- Returns `404 Not Found` if the target does not exist
- For directories, uses `deleteRecursively()` to remove the directory and all contents
- For files, invalidates the channel cache before deletion
- Returns a JSON success response on completion

### MIME Type Resolution

Custom MIME type mappings are applied for:

- `.js` and `.mjs` → `application/javascript`
- `.log` → `text/plain`
- All others fall back to Jetty's `MimeTypes.getDefaultMimeByExtension()`, defaulting to `application/octet-stream`

### Git Version Control (Base Class)
`FileServlet` includes built-in Git version control support that can be enabled or disabled per request via the
`isGitEnabled()` method (defaults to `true`). When enabled, the directory listing page includes a Git section with a
full-featured UI.
#### Git UI Features
When the directory is already a Git repository:
- **Status** — Shows current branch and changed files with color-coded status indicators
- **Diff** — Displays unstaged and staged changes with syntax-highlighted diff output (tabbed view)
- **Log** — Shows commit history in oneline format
- **Stage All** — Stages all changes (`git add .`)
- **Commit** — Opens a dialog to enter a commit message, stages all changes and commits
- **Pull / Push** — Pull from or push to remote
- **Stash / Stash Pop** — Stash and restore changes
- **Reset** — Discard all uncommitted changes (with confirmation)
- **Branches** — List all branches with switch and delete actions
- **New Branch** — Create a new branch with optional checkout
When the directory is not yet a Git repository:
- An initialization prompt is shown with a button to initialize a new repository
#### Git POST Actions (via `gitAction` parameter)
| Action           | Parameters                                    | Description                                                    |
|------------------|-----------------------------------------------|----------------------------------------------------------------|
| `init`           | *(none)*                                      | Initializes a git repository                                   |
| `status`         | *(none)*                                      | Returns porcelain status and current branch                    |
| `add`            | `filePath` (default `.`)                      | Stages files                                                   |
| `commit`         | `message` (default `"Commit from web UI"`)    | Stages all changes and commits                                 |
| `pull`           | *(none)*                                      | Pulls from remote                                              |
| `push`           | *(none)*                                      | Pushes to remote                                               |
| `log`            | `count` (default `"20"`)                      | Returns oneline log                                            |
| `diff`           | *(none)*                                      | Returns unstaged and staged diffs                              |
| `reset`          | `filePath` (optional)                         | Discards changes for a file or all files                       |
| `stash`          | *(none)*                                      | Stashes changes                                                |
| `stash-pop`      | *(none)*                                      | Applies stashed changes                                        |
| `branches`       | *(none)*                                      | Lists all branches with current branch indicator               |
| `create-branch`  | `branchName`, `checkout` (default `"true"`)   | Creates a new branch, optionally checking it out               |
| `switch-branch`  | `branchName`                                  | Switches to the specified branch                               |
| `delete-branch`  | `branchName`, `force` (optional)              | Deletes a branch (`-d` or `-D` if force)                       |
Git commands are executed as subprocesses with `redirectErrorStream(true)`.

### Extension Points

| Method                                          | Purpose                                                                                                    |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `getDir(req)`                                   | Abstract — resolve the base directory for a request                                                        |
| `getFile(dir, pathSegments, req)`               | Construct the file path from segments (default joins segments after the first)                             |
| `listContents(file, req)`                       | Generate HTML list items for files and folders; returns a `Pair<String, String>` of (files HTML, folders HTML) |
| `getZipLink(req, filePath)`                     | Return a ZIP download link for the current directory (default returns empty string)                        |
| `getFileActions(file, req)`                     | Return additional HTML action links/buttons for individual files in directory listings                     |
| `getFolderActions(folder, req)`                 | Return additional HTML action links/buttons for individual folders in directory listings                   |
| `getToolbarActions(req, currentPath)`           | Return additional HTML toolbar items in the navbar area                                                    |
| `getAdditionalSections(dir, req, currentPath)`  | Return additional HTML sections inserted after the upload section                                          |
| `getAdditionalStyles()`                         | Return additional CSS styles (without style tags) appended to the page                                     |
| `getAdditionalScripts()`                        | Return additional JavaScript (without script tags) appended to the page                                    |
| `isGitEnabled(req)`                             | Whether Git features should be enabled for this request (default `true`)                                   |
| `getGitRoot(req)`                               | Return the root directory for Git operations (default returns `getDir(req)`)                               |

---

## SessionFileServlet

`SessionFileServlet` extends `FileServlet` to provide session-aware file serving with directory resolution based on the
authenticated user and session ID, plus integrated Git version control via a REST API.

### Directory Resolution

The `getDir()` implementation:

1. Extracts the session ID from the first path segment
2. Resolves the authenticated user from the request cookie via `ApplicationServices.authenticationManager`
3. Obtains both the **session directory** and **data directory** from `StorageInterface`
4. Searches for the requested file across both directories (if they differ)
5. For `.html`, `.pdf`, or `.txt` requests, also checks for a corresponding `.md` file in either directory
6. Returns `null` if no matching file or directory is found (triggering the combined listing from both directories in
   `listContents`)

### Combined Directory Listing

When `getDir()` returns `null` (no exact match found), `listContents()` merges the contents of both the session
directory and data directory, presenting a unified view.

### Git Version Control API

`SessionFileServlet` intercepts both GET and POST requests containing `/.git/api/` in the path and routes them to Git
operations executed as subprocess commands against the session directory. Git environment variables
(`GIT_AUTHOR_NAME`, `GIT_COMMITTER_NAME`, `GIT_AUTHOR_EMAIL`, `GIT_COMMITTER_EMAIL`) are set to
`SessionFileServlet` / `noreply@localhost`. Error streams are captured separately (not merged with stdout).

#### GET Endpoints

| Endpoint                   | Description                                                                                                                        |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `/.git/api/status`         | Returns repository status: whether initialized, current branch, whether clean, and a list of changed files with their status codes. Returns `initialized: false` if no `.git` directory exists |
| `/.git/api/branches`       | Lists all branches (local and remote) with a `current` flag, plus the current branch name                                          |
| `/.git/api/log?maxCount=N` | Returns commit history (default 20, max 100) with hash, author, email, date, and message                                           |

#### POST Endpoints

| Endpoint             | Request Body                              | Description                                                                                                                      |
|----------------------|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `/.git/api/init`     | *(none)*                                  | Initializes a git repository with an initial empty commit. No-op if already initialized                                          |
| `/.git/api/commit`   | `{"message": "..."}`                      | Stages all changes (`git add -A`) and commits. Returns the commit hash. Reports "nothing to commit" if the working tree is clean |
| `/.git/api/checkout` | `{"branch": "...", "create": true/false}` | Checks out a branch, optionally creating it with `-b`. Validates branch names against git naming rules                           |
#### Response Format
All Git API endpoints return JSON responses. Successful responses include `"success": true` along with
operation-specific fields. Error responses include `"success": false` (or `"error"`) with descriptive messages.
The `commit` endpoint uses `--author=SessionFileServlet <noreply@localhost>` for the commit author.


#### Auto-initialization

The `ensureGitRepo()` method is called before `branches`, `log`, `commit`, and `checkout` operations. If no `.git`
directory exists, it automatically runs `git init`, `git add -A`, and an initial commit.

#### Branch Name Validation

Branch names are validated to reject:

- Blank names, names starting with `-`, names ending with `.lock` or `.`
- Characters: `..`, `~`, `^`, `:`, `\\`, spaces, `@{`
- Non-printable or non-ASCII characters

### Extension Points

| Method               | Purpose                                                                                                                          |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `onSession(session)` | Called when a session is resolved from the request path. Default is a no-op; subclasses can override for session lifecycle hooks  |