# WebUI Servlets and Filters

This package contains the server-side components (Servlets and Filters) that handle HTTP requests for the Cognotik web
interface. These components manage authentication, session lifecycle, file access, API proxying, and configuration
metadata.

## Core Components

### Authentication & User Management

* **`OAuthGoogle`**: Implements the Google OAuth2 login flow. It handles the redirection to Google and the callback to
  establish a user session.
* **`LogoutServlet`**: Terminates the user session and clears authentication cookies.
* **`UserInfoServlet`**: Provides a JSON endpoint returning the current authenticated user's profile information.
* **`UserSettingsServlet`**: Manages user-specific settings, including API keys for various providers and enabled tools.
  It includes logic to mask sensitive keys in the UI.
* **`SessionIdFilter`**: A security filter that ensures requests to protected resources are authenticated, redirecting
  to the login page if necessary.

### Session Management

* **`SessionListServlet`**: Renders an HTML list of all sessions available to the user, including metadata like creation
  time.
* **`NewSessionServlet`**: Generates and returns a new unique global session ID.
* **`DeleteSessionServlet`**: Provides an interface and endpoint for deleting a session and its associated data.
* **`SessionSettingsServlet`**: Allows viewing and editing session-specific configuration (stored as JSON).
* **`CancelThreadsServlet`**: Provides a mechanism to forcefully shut down the thread pool associated with a specific
  session.
* **`StaticZipServlet`**: Packages a session's directory into a ZIP file for download.

### API & Proxy Services

* **`ProxyHttpServlet`**: Acts as a reverse proxy for OpenAI-compatible APIs. It injects user-managed API keys and
  enforces budget constraints by tracking usage costs.
* **`ApiKeyServlet`**: Manages internal API keys used for the proxy service, including budget management and an
  invitation system for sharing keys.
* **`ApiProviderServlet`**: Lists available and configured AI providers, including details about supported models and
  capabilities (chat, embeddings).
* **`UsageServlet`**: Displays a detailed breakdown of token usage and costs per model for a session or user.

### File Serving

* **`FileServlet`**: A base class for serving files and directory listings. It includes support for:
  * Large file streaming using memory-mapped buffers.
  * On-the-fly Markdown rendering to HTML or PDF.
  * File uploads via drag-and-drop or clipboard paste.
* **`SessionFileServlet`**: Specialization of `FileServlet` that scopes file access to specific session and data
  directories.

### Configuration & Metadata

* **`CognitiveConfigServlet`**: Dynamically generates configuration UI metadata for different "Cognitive Modes" based on
  Kotlin class properties and annotations.
* **`TaskConfigServlet`**: Similar to the cognitive config, it provides metadata for configuring various task types and
  tools.
* **`AppInfoServlet`**: A generic servlet for providing application-specific metadata as JSON.

### Diagnostics & Specialized Tools

* **`SessionThreadsServlet`**: Provides a diagnostic view of active threads within a session's pool, including real-time
  stack traces.
* **`SymbolGraphServlet`**: An API endpoint for the `SymbolGraphService`, allowing for searching and exploring code
  symbols, files, and packages.
* **`WelcomeServlet`**: Serves the main landing page, static resources, and the list of authorized applications
  available to the user.

### Infrastructure

* **`CorsFilter`**: Adds Cross-Origin Resource Sharing (CORS) headers to responses to allow the WebUI to interact with
  the server from different origins.
* **`OAuthBase`**: An abstract base class for implementing different OAuth providers.

## Implementation Details

* **Asynchronous Processing**: Many servlets (like `ProxyHttpServlet` and `FileServlet`) use Servlet 3.0+ asynchronous
  processing to handle long-running requests (like AI generation or large file transfers) without blocking server
  threads.
* **JSON Integration**: Uses Jackson and internal `JsonUtil` for seamless serialization of data models.
* **Security**: Authentication is enforced via `SessionIdFilter`, and authorization checks are performed within
  individual servlets using the `AuthorizationManager`.
* **Dynamic UI**: Servlets like `TaskConfigServlet` and `CognitiveConfigServlet` allow the frontend to build complex
  configuration forms dynamically based on backend code structures.