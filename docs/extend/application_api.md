## 1. Overview & Authentication

The Cognotik web interface is built on Jetty servlets. Most endpoints expect the client to be authenticated.

* **Authentication Method:** Cookie-based.
* **Cookie Name:** The system looks for a cookie named `auth_cookie` (referenced via
  `AuthenticationInterface.AUTH_COOKIE`).
* **Authorization:** The `ApplicationServer` applies a filter to all requests (`/*`). It checks if the user associated
  with the cookie has `OperationType.Read` permission.

---

## 2. Session Management APIs

These endpoints allow you to retrieve metadata, configure settings, and manage the runtime state of specific sessions.

### Get Application/Session Info

Retrieves the current state of the application or a specific session.

* **Endpoint:** `/appInfo`
* **Method:** `GET`
* **Parameters:**
  * `session` (Optional): The ID of the session to query.
* **Response:** JSON object containing application name, input counts, and UI configuration flags.

### Session Settings

Reads or updates the JSON configuration file (`settings.json`) associated with a specific session.

#### Retrieve Settings

* **Endpoint:** `/settings`
* **Method:** `GET`
* **Parameters:**
  * `sessionId` (Required): The ID of the session.
  * `raw` (Optional): Set to `true` to receive a raw JSON response. If omitted or false, returns an HTML form.
* **Response:**
  * If `raw=true`: `application/json` containing the settings object.
  * Default: `text/html` with an editor interface.

#### Update Settings

* **Endpoint:** `/settings`
* **Method:** `POST`
* **Parameters:**
  * `sessionId` (Required): The ID of the session.
* **Body/Payload:**
  * Option A (Form Data): A parameter named `settings` containing the JSON string.
  * Option B (Raw Body): The raw JSON string in the request body.
* **Response:** Redirects to the session view on success, or returns an error message.

### Session Threads (Debugging)

View the active thread pool and stack traces for a specific session.

* **Endpoint:** `/threads`
* **Method:** `GET`
* **Parameters:**
  * `sessionId` (Required): The ID of the session.
* **Response:** `text/html` displaying the pool stats (active threads, pool size) and individual stack traces for every
  alive thread in that session's pool.

### Cancel/Kill Session

Terminates the thread pool associated with a session.

* **Endpoint:** `/cancel`
* **Method:** `GET`
  * **Function:** Returns an HTML confirmation form.
* **Method:** `POST`
  * **Parameters:**
    * `sessionId` (Required): The ID of the session.
    * `confirm` (Required): Must be the string `"confirm"`.
  * **Security:** Requires `OperationType.Delete` permission. If the session is global, requires
    `OperationType.Public` permission.
  * **Response:** Shuts down the thread pool immediately (`shutdownNow()`) and redirects to root `/`.

---

## 3. File System APIs

These APIs provide access to the file storage associated with a user and session. They handle file serving, directory
listing, uploading, and downloading.

### File Browser & Downloader

Serves files or lists directories. It includes logic to automatically render Markdown files as HTML or PDF.

* **Endpoint:** `/fileIndex/{path/to/file}`
* **Method:** `GET`
* **Path:** The URL path after `/fileIndex/` represents the relative path within the session's data directory.
* **Behavior:**
  1. **If Directory:** Returns an HTML page listing files and subfolders.
  2. **If File Exists:** Streams the file. Supports large file streaming via `FileChannel` and async I/O.
  3. **If File Missing:**
    * If the requested extension is `.html`, `.pdf`, or `.txt`, it checks for a corresponding `.md` (Markdown) file.
    * If found, it renders the Markdown to HTML (or PDF via `PdfRendererBuilder`) and serves it dynamically.

### File Upload

Upload a file to a specific directory within the session storage.

* **Endpoint:** `/fileIndex/{target/directory/path}`
* **Method:** `POST`
* **Content-Type:** `multipart/form-data`
* **Constraints:**
  * Max File Size: 50MB
  * Max Request Size: 100MB
* **Form Parts:**
  * `file`: The file binary data.
* **Behavior:**
  * Saves the file to the directory specified in the URL path.
  * **Conflict:** Returns `409 Conflict` if the file already exists (no overwrite allowed).
  * **Security:** Validates filenames (rejects `..`, `/`, `\`, etc.).
* **Response:** JSON `{"success": true, "message": "...", "filename": "..."}`.

### Download Directory as ZIP

Compresses a directory and downloads it as a ZIP archive.

* **Endpoint:** `/fileZip`
* **Method:** `GET`
* **Parameters:**
  * `session` (Required): The session ID.
  * `path` (Optional): The relative path to the directory to zip. Defaults to root `/`.
* **Response:** `application/zip` stream.

---

## 4. Usage & Analytics API

Monitor token usage and costs associated with LLM (Large Language Model) interactions.

* **Endpoint:** `/usage`
* **Method:** `GET`
* **Parameters:**
  * `sessionId` (Optional):
    * **If provided:** Returns usage statistics specific to that session.
    * **If omitted:** Returns aggregated usage statistics for the currently authenticated user across all sessions.
* **Response:** `text/html` table detailing:
  * Model Name
  * Prompt Tokens
  * Completion Tokens
  * Estimated Cost

---

## 5. Other Endpoints

The `ApplicationDirectory` and `ApplicationServer` configuration references these additional endpoints, though their
full logic depends on classes not fully detailed in the provided snippets (like `SessionListServlet`):

* **`/sessions`**: Lists available sessions (handled by `SessionListServlet`).
* **`/userInfo`**: Returns information about the current user (handled by `UserInfoServlet`).
* **`/share`**: Likely handles sharing permissions for a session (handled by `SessionShareServlet`).
* **`/delete`**: Deletes a session (handled by `DeleteSessionServlet`).

