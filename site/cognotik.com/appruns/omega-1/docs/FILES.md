# Filesystem API Client Guide

## Overview

This guide documents the client-side HTTP APIs for interacting with the session-based filesystem, including file browsing, upload/download, ZIP export, and Git version control operations. All endpoints are relative to your application's base path (e.g., `/myapp/`).

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [File Browsing & Download](#2-file-browsing--download)
3. [File Upload](#3-file-upload)
4. [File Update (PUT)](#4-file-update-put)
5. [File Deletion](#5-file-deletion)
6. [ZIP Download](#6-zip-download)
7. [Git Operations (Form-Based)](#7-git-operations-form-based)
8. [Git REST API](#8-git-rest-api)
9. [Session & Directory Structure](#9-session--directory-structure)
10. [Error Handling](#10-error-handling)

---

## 1. Authentication

All API requests require authentication. The server uses cookie-based authentication via a cookie named per your `AuthenticationInterface.AUTH_COOKIE` configuration.

### How It Works

- If a valid authentication cookie is present, the request proceeds normally.
- If the cookie is missing or invalid, the server responds with **HTTP 307 (Temporary Redirect)** to `/login/?target=<encoded_original_url>`.
- After successful login, the user is redirected back to the original URL.

### Authorization

After authentication, the server checks whether the user has **Read** permission for the application. If not, the server returns:

```
HTTP 403 Forbidden
Body: "Access Denied"
```

---

## 2. File Browsing & Download

### Endpoint

```
GET /fileIndex/{sessionId}/{path...}
```

### Behavior

| Scenario | Response |
|---|---|
| Path points to a **file** | Serves the file with appropriate `Content-Type` |
| Path points to a **directory** (ending with `/`) | Returns an HTML directory listing page |
| Path points to a **directory** (no trailing `/`) | Redirects (302) to the same path with trailing `/` |
| File does not exist but a `.md` equivalent does | See Markdown rendering below |
| `_files.json` requested but doesn't exist | Returns a JSON listing of the parent directory |
| File not found | HTTP 404 |

### Examples

**Browse a session's root directory:**
```http
GET /myapp/fileIndex/abc-123-session/ HTTP/1.1
```

**Download a specific file:**
```http
GET /myapp/fileIndex/abc-123-session/output/results.json HTTP/1.1
```

**Download a nested file:**
```http
GET /myapp/fileIndex/abc-123-session/logs/2024/debug.log HTTP/1.1
```

### Markdown Rendering

If you request a `.html`, `.pdf`, or `.txt` file that doesn't exist, but a corresponding `.md` file does, the server automatically renders it:

| Requested | Source | Behavior |
|---|---|---|
| `report.html` | `report.md` | Renders Markdown as HTML |
| `report.pdf` | `report.md` | Renders Markdown as PDF |
| `report.txt` | `report.md` | Serves raw Markdown as `text/plain` |

**Example:**
```http
GET /myapp/fileIndex/abc-123-session/report.html HTTP/1.1
```
If `report.html` doesn't exist but `report.md` does, you'll receive the rendered HTML.

### Virtual `_files.json`

Request `_files.json` in any directory to get a JSON listing of files, even if the JSON file doesn't physically exist:

```http
GET /myapp/fileIndex/abc-123-session/output/_files.json HTTP/1.1
```

---

## 3. File Upload

### Endpoint

```
POST /fileIndex/{sessionId}/{path...}
```

### Content Type

```
Content-Type: multipart/form-data
```

### Constraints

| Parameter | Limit |
|---|---|
| Max file size | 50 MB |
| Max request size | 100 MB |
| In-memory threshold | 2 MB (files larger than this are written to temp disk) |

### Example (JavaScript)

```javascript
async function uploadFile(sessionId, targetPath, file) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`/myapp/fileIndex/${sessionId}/${targetPath}`, {
    method: 'POST',
    body: formData,
    credentials: 'include' // include auth cookie
  });

  if (!response.ok) {
    throw new Error(`Upload failed: ${response.status} ${response.statusText}`);
  }
  return response;
}
```

### Example (cURL)

```bash
curl -X POST \
  -F "file=@/local/path/to/document.pdf" \
  -b "auth_cookie=YOUR_TOKEN" \
  https://example.com/myapp/fileIndex/abc-123-session/uploads/
```

---

## 4. File Update (PUT)

### Endpoint

```
PUT /fileIndex/{sessionId}/{path/to/file}
```

Use PUT to create or overwrite a specific file with raw body content.

### Example (JavaScript)

```javascript
async function writeFile(sessionId, filePath, content) {
  const response = await fetch(`/myapp/fileIndex/${sessionId}/${filePath}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'text/plain' // or appropriate type
    },
    body: content,
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Write failed: ${response.status}`);
  }
  return response;
}

// Usage
await writeFile('abc-123-session', 'config/settings.json', JSON.stringify(myConfig));
```

### Example (cURL)

```bash
curl -X PUT \
  -H "Content-Type: application/json" \
  -d '{"key": "value"}' \
  -b "auth_cookie=YOUR_TOKEN" \
  https://example.com/myapp/fileIndex/abc-123-session/config/settings.json
```

---

## 5. File Deletion

### Endpoint

```
DELETE /fileIndex/{sessionId}/{path/to/file}
```

Deletes a file or directory.

### Example (JavaScript)

```javascript
async function deleteFile(sessionId, filePath) {
  const response = await fetch(`/myapp/fileIndex/${sessionId}/${filePath}`, {
    method: 'DELETE',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Delete failed: ${response.status}`);
  }
  return response;
}
```

### Example (cURL)

```bash
curl -X DELETE \
  -b "auth_cookie=YOUR_TOKEN" \
  https://example.com/myapp/fileIndex/abc-123-session/temp/old-file.txt
```

---

## 6. ZIP Download

### Endpoint

```
GET /fileZip?session={sessionId}&path={encodedPath}
```

Downloads a directory (or file) as a ZIP archive.

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `session` | Yes | The session ID |
| `path` | No | URL-encoded path within the session directory. Defaults to `/` (entire session) |

### Response

| Header | Value |
|---|---|
| `Content-Type` | `application/zip` |
| `Content-Disposition` | `attachment; filename="{name}.zip"` |

### Behavior

- If the path points to a **directory**, the entire directory tree is zipped (excluding dotfiles starting with `.`).
- If the path points to a **file**, that single file is zipped.
- If the path doesn't exist, returns **HTTP 404**.
- If the `session` parameter is missing, returns **HTTP 400**.

### Examples

**Download entire session as ZIP:**
```http
GET /myapp/fileZip?session=abc-123-session&path=%2F HTTP/1.1
```

**Download a specific subdirectory:**
```http
GET /myapp/fileZip?session=abc-123-session&path=%2Foutput%2Fresults HTTP/1.1
```

**JavaScript example:**
```javascript
function downloadZip(sessionId, path = '/') {
  const encodedPath = encodeURIComponent(path);
  window.location.href = `/myapp/fileZip?session=${sessionId}&path=${encodedPath}`;
}
```

### Notes

- Hidden files/directories (names starting with `.`) are excluded from the ZIP.
- The ZIP is created as a temporary file on the server and streamed to the client, then deleted.

---

## 7. Git Operations (Form-Based)

The directory listing UI includes built-in Git controls. These are submitted as POST requests with a `gitAction` form parameter. You can also invoke them programmatically.

### Endpoint

```
POST /fileIndex/{sessionId}/{path...}?gitAction={action}
```

### Available Actions

| `gitAction` Value | Additional Parameters | Description |
|---|---|---|
| `init` | — | Initialize a new Git repository |
| `commit` | `commitMessage` (form field) | Stage all changes and commit |
| `checkout` | `branchName`, optionally `createBranch=true` | Switch or create a branch |

### Example: Initialize a Repository

```javascript
async function gitInit(sessionId) {
  const formData = new FormData();
  formData.append('gitAction', 'init');

  const response = await fetch(`/myapp/fileIndex/${sessionId}/?gitAction=init`, {
    method: 'POST',
    body: formData,
    credentials: 'include'
  });
  // After success, the server typically redirects back to the directory listing
}
```

### Example: Commit Changes

```javascript
async function gitCommit(sessionId, message) {
  const formData = new FormData();
  formData.append('gitAction', 'commit');
  formData.append('commitMessage', message);

  const response = await fetch(`/myapp/fileIndex/${sessionId}/?gitAction=commit`, {
    method: 'POST',
    body: formData,
    credentials: 'include'
  });
}
```

### Example: Create and Checkout a New Branch

```javascript
async function gitCreateBranch(sessionId, branchName) {
  const formData = new FormData();
  formData.append('gitAction', 'checkout');
  formData.append('branchName', branchName);
  formData.append('createBranch', 'true');

  const response = await fetch(`/myapp/fileIndex/${sessionId}/?gitAction=checkout`, {
    method: 'POST',
    body: formData,
    credentials: 'include'
  });
}
```

---

## 8. Git REST API

A JSON-based REST API is available for Git operations, accessed through a special path pattern within the file index.

### Base Path

```
/fileIndex/{sessionId}/.git/api/{action}
```

### 8.1 Get Repository Status

```
GET /fileIndex/{sessionId}/.git/api/status
```

**Response (not initialized):**
```json
{
  "success": true,
  "initialized": false,
  "message": "Not a git repository"
}
```

**Response (initialized, with changes):**
```json
{
  "success": true,
  "initialized": true,
  "currentBranch": "main",
  "clean": false,
  "changes": [
    {"status": "M", "file": "config.json"},
    {"status": "??", "file": "new-file.txt"}
  ]
}
```

**Response (initialized, clean):**
```json
{
  "success": true,
  "initialized": true,
  "currentBranch": "main",
  "clean": true,
  "changes": []
}
```

**Git status codes:**

| Code | Meaning |
|---|---|
| `M` | Modified |
| `A` | Added |
| `D` | Deleted |
| `R` | Renamed |
| `??` | Untracked |

### 8.2 List Branches

```
GET /fileIndex/{sessionId}/.git/api/branches
```

**Response:**
```json
{
  "success": true,
  "currentBranch": "main",
  "branches": [
    {"name": "main", "current": true},
    {"name": "feature-x", "current": false},
    {"name": "remotes/origin/main", "current": false}
  ]
}
```

> **Note:** If the repository is not yet initialized, it will be auto-initialized before listing branches.

### 8.3 Get Commit Log

```
GET /fileIndex/{sessionId}/.git/api/log?maxCount={n}
```

**Parameters:**

| Parameter | Required | Default | Range | Description |
|---|---|---|---|---|
| `maxCount` | No | 20 | 1–100 | Maximum number of commits to return |

**Response:**
```json
{
  "success": true,
  "commits": [
    {
      "hash": "a1b2c3d4e5f6...",
      "author": "SessionFileServlet",
      "email": "noreply@localhost",
      "date": "2024-01-15T10:30:00+00:00",
      "message": "Updated configuration"
    },
    {
      "hash": "f6e5d4c3b2a1...",
      "author": "SessionFileServlet",
      "email": "noreply@localhost",
      "date": "2024-01-15T09:00:00+00:00",
      "message": "Initial commit"
    }
  ]
}
```

### 8.4 Initialize Repository

```
POST /fileIndex/{sessionId}/.git/api/init
```

No request body required.

**Response (new repo):**
```json
{
  "success": true,
  "message": "Git repository initialized",
  "output": "Initialized empty Git repository in ...",
  "path": "/data/sessions/abc-123-session"
}
```

**Response (already initialized):**
```json
{
  "success": true,
  "message": "Git repository already initialized",
  "path": "/data/sessions/abc-123-session"
}
```

> **Note:** Initialization automatically creates an initial empty commit so that `HEAD` is valid.

### 8.5 Commit Changes

```
POST /fileIndex/{sessionId}/.git/api/commit
Content-Type: application/json
```

**Request Body:**
```json
{
  "message": "Describe your changes here"
}
```

If `message` is omitted, defaults to `"Auto-commit"`.

**Response (changes committed):**
```json
{
  "success": true,
  "message": "Changes committed",
  "commitHash": "a1b2c3d4e5f6...",
  "output": "[main abc1234] Describe your changes here\n 2 files changed..."
}
```

**Response (nothing to commit):**
```json
{
  "success": true,
  "message": "Nothing to commit, working tree clean"
}
```

**Response (error):**
```json
{
  "success": false,
  "error": "Failed to stage changes: ...",
  "output": "..."
}
```

> **Note:** This automatically stages all changes (`git add -A`) before committing. Commits are authored as `SessionFileServlet <noreply@localhost>`.

### 8.6 Checkout Branch

```
POST /fileIndex/{sessionId}/.git/api/checkout
Content-Type: application/json
```

**Request Body (switch to existing branch):**
```json
{
  "branch": "feature-x",
  "create": false
}
```

**Request Body (create and switch to new branch):**
```json
{
  "branch": "my-new-branch",
  "create": true
}
```

**Response (success):**
```json
{
  "success": true,
  "message": "Checked out branch 'feature-x'",
  "output": "Switched to branch 'feature-x'"
}
```

**Response (invalid branch name):**
```json
{
  "success": false,
  "error": "Invalid branch name: my bad branch"
}
```

### Branch Name Validation

Branch names must satisfy all of the following:

- Not blank
- No `..`, `~`, `^`, `:`, `\`, or spaces
- Does not start with `-`
- Does not end with `.lock` or `.`
- Does not contain `@{`
- All characters are printable ASCII (codes 33–126)

---

## 9. Session & Directory Structure

### Understanding Sessions

Each interaction creates a **session** identified by a unique session ID. All files for a session are stored in a session-specific directory on the server.

### Typical Directory Layout

```
{sessionId}/
├── info.json              # Session metadata (auto-created)
├── settings.json          # Session settings (if applicable)
├── .git/                  # Git repository (if initialized)
├── output/
│   ├── results.json
│   ├── report.md
│   └── images/
│       └── chart.png
└── logs/
    └── debug.log
```

### Related Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/sessions` | GET | List all sessions for the current user |
| `/settings` | GET/POST | Get or update session settings |
| `/threads` | GET | View active threads for a session |
| `/delete` | POST | Delete a session |
| `/cancel` | POST | Cancel running threads in a session |
| `/appInfo` | GET | Get application metadata |
| `/userInfo` | GET | Get current user information |
| `/usage` | GET | Get usage statistics |

---

## 10. Error Handling

### Standard HTTP Status Codes

| Code | Meaning | When |
|---|---|---|
| 200 | OK | Successful request |
| 302 | Found (Redirect) | Directory path without trailing `/` |
| 307 | Temporary Redirect | Authentication required (redirects to login) |
| 400 | Bad Request | Missing required parameters, invalid paths |
| 403 | Forbidden | User lacks authorization |
| 404 | Not Found | File or directory doesn't exist |
| 500 | Internal Server Error | Server-side failure |

### Git API Error Responses

All Git API endpoints return JSON with a consistent structure:

```json
{
  "success": false,
  "error": "Description of what went wrong",
  "output": "Any stdout from the git command (if applicable)"
}
```

### Recommended Client Error Handling

```javascript
async function apiCall(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    credentials: 'include'
  });

  // Handle authentication redirect
  if (response.redirected && response.url.includes('/login/')) {
    window.location.href = response.url;
    return null;
  }

  // Handle forbidden
  if (response.status === 403) {
    throw new Error('Access denied. You do not have permission for this operation.');
  }

  // Handle not found
  if (response.status === 404) {
    throw new Error('The requested resource was not found.');
  }

  // Handle server errors
  if (response.status >= 500) {
    const text = await response.text();
    throw new Error(`Server error: ${text}`);
  }

  // For JSON endpoints, parse and check success field
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    const data = await response.json();
    if (data.success === false) {
      throw new Error(data.error || 'Operation failed');
    }
    return data;
  }

  return response;
}
```

---

## Complete Workflow Example

Here's a full example demonstrating a typical workflow — browsing files, making changes, and using Git to track them:

```javascript
const SESSION = 'abc-123-session';
const BASE = '/myapp';

// 1. Check git status
const status = await apiCall(`${BASE}/fileIndex/${SESSION}/.git/api/status`);

if (!status.initialized) {
  // 2. Initialize git repo
  await apiCall(`${BASE}/fileIndex/${SESSION}/.git/api/init`, { method: 'POST' });
}

// 3. Write a file
await fetch(`${BASE}/fileIndex/${SESSION}/notes.md`, {
  method: 'PUT',
  headers: { 'Content-Type': 'text/plain' },
  body: '# My Notes\n\nThis is a test.',
  credentials: 'include'
});

// 4. Upload a file
const formData = new FormData();
formData.append('file', myFileInput.files[0]);
await fetch(`${BASE}/fileIndex/${SESSION}/uploads/`, {
  method: 'POST',
  body: formData,
  credentials: 'include'
});

// 5. Commit changes
await apiCall(`${BASE}/fileIndex/${SESSION}/.git/api/commit`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ message: 'Added notes and uploaded file' })
});

// 6. Create a feature branch
await apiCall(`${BASE}/fileIndex/${SESSION}/.git/api/checkout`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ branch: 'experiment-1', create: true })
});

// 7. Make more changes, commit again...

// 8. Switch back to main
await apiCall(`${BASE}/fileIndex/${SESSION}/.git/api/checkout`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ branch: 'main' })
});

// 9. View commit history
const log = await apiCall(`${BASE}/fileIndex/${SESSION}/.git/api/log?maxCount=10`);
console.log('Recent commits:', log.commits);

// 10. Download everything as ZIP
window.location.href = `${BASE}/fileZip?session=${SESSION}&path=${encodeURIComponent('/')}`;

// 11. Delete a file
await fetch(`${BASE}/fileIndex/${SESSION}/temp/scratch.txt`, {
  method: 'DELETE',
  credentials: 'include'
});
```
