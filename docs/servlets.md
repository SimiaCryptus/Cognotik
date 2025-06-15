# Comprehensive Guide to Cognotik WebUI Servlets

This guide provides a detailed overview of all servlets in the Cognotik WebUI application, their purposes, endpoints, and functionality.

## Table of Contents

1. [Authentication & Authorization](#authentication--authorization)
2. [Session Management](#session-management)
3. [File Management](#file-management)
4. [User Management](#user-management)
5. [API & Proxy](#api--proxy)
6. [Utility Servlets](#utility-servlets)

---

## Authentication & Authorization

### OAuthGoogle
**Purpose**: Handles Google OAuth2 authentication flow

**Endpoints**:
- `/login` or `/googleLogin` - Initiates OAuth flow
- `/oauth2callback` - Handles OAuth callback

**Key Features**:
- Redirects users to Google OAuth authorization
- Processes authorization codes and exchanges for tokens
- Creates user sessions with secure cookies
- Supports redirect parameter for post-login navigation

**Usage Example**:
```
GET /googleLogin?redirect=/myapp
```

### SessionIdFilter
**Purpose**: Protects secure endpoints by validating user authentication

**Configuration**:
- Applied to all routes except login/callback endpoints
- Redirects unauthenticated users to login page
- Preserves original URL for post-login redirect

### LogoutServlet
**Purpose**: Handles user logout

**Endpoint**: `/logout`
**Method**: GET

**Functionality**:
- Invalidates user session
- Redirects to home page
- Returns 400 if user not found

---

## Session Management

### NewSessionServlet
**Purpose**: Generates new global session IDs

**Endpoint**: `/newSession`
**Method**: GET
**Response**: Plain text session ID

### SessionListServlet
**Purpose**: Displays list of user sessions

**Features**:
- Shows session names and creation times
- Clickable links to session details
- Filtered by authenticated user
- HTML table format with styling

### SessionSettingsServlet
**Purpose**: Manages session-specific settings

**Endpoints**:
- `GET /settings?sessionId=<id>` - View settings form
- `GET /settings?sessionId=<id>&raw=true` - Get raw JSON
- `POST /settings` - Save settings

**Parameters**:
- `sessionId` (required)
- `settings` (POST body or form parameter)

### SessionShareServlet
**Purpose**: Creates shareable links for sessions

**Endpoint**: `/share?url=<session_url>`

**Features**:
- Generates QR codes for sharing
- Uses Selenium to capture session state
- Uploads to cloud storage (S3)
- Validates host permissions
- Reuses existing share IDs when possible

### SessionThreadsServlet
**Purpose**: Displays active threads for debugging

**Endpoint**: `/threads?sessionId=<id>`

**Information Shown**:
- Pool statistics
- Active thread names
- Complete stack traces
- Thread states

### DeleteSessionServlet
**Purpose**: Permanently deletes sessions

**Endpoints**:
- `GET /delete?sessionId=<id>` - Confirmation form
- `POST /delete` - Execute deletion

**Security**:
- Requires "confirm" text input
- Validates user authorization
- Special permissions for global sessions

### CancelThreadsServlet
**Purpose**: Cancels running session threads

**Endpoints**:
- `GET /cancel?sessionId=<id>` - Confirmation form
- `POST /cancel` - Execute cancellation

**Functionality**:
- Shuts down thread pool immediately
- Requires confirmation text
- Authorization checks for global sessions

---

## File Management

### FileServlet (Abstract Base)
**Purpose**: Base class for serving files and directories

**Key Features**:
- Automatic MIME type detection
- Directory listing with breadcrumbs
- Async file streaming for large files
- Memory-mapped files for performance
- File channel caching
- ZIP download links

**Directory Listing Features**:
- Separate folders and files sections
- Icons for different file types
- Responsive HTML design
- Breadcrumb navigation

### SessionFileServlet
**Purpose**: Serves files from session directories

**Endpoint Pattern**: `/files/<sessionId>/<path>`

**Features**:
- Inherits all FileServlet capabilities
- Session-based file access
- User authentication required
- ZIP download support

### ZipServlet
**Purpose**: Creates ZIP archives of session directories

**Endpoint**: `/fileZip?session=<id>&path=<path>`

**Process**:
1. Validates session access
2. Creates temporary ZIP file
3. Recursively adds files (excluding hidden files)
4. Streams ZIP to client
5. Cleans up temporary files

---

## User Management

### UserInfoServlet
**Purpose**: Returns current user information

**Endpoint**: `/userInfo`
**Method**: GET
**Response**: JSON user object or empty object if not authenticated

### UserSettingsServlet
**Purpose**: Manages user-specific settings

**Endpoints**:
- `GET /userSettings/` - Settings form with masked API keys
- `POST /userSettings/` - Save settings

**Features**:
- API key masking for security
- Support for multiple API providers
- Local tools configuration
- Settings validation and reconstruction

**API Key Handling**:
- Displays `********` for existing keys
- Preserves existing keys when mask is submitted
- Allows updating with new keys

---

## API & Proxy

### ApiKeyServlet
**Purpose**: Manages API key records for proxy functionality

**Endpoints**:
- `GET /apiKeys/` - List API keys
- `GET /apiKeys/?action=create` - New key form
- `GET /apiKeys/?action=edit&apiKey=<key>` - Edit form
- `GET /apiKeys/?action=delete&apiKey=<key>` - Delete key
- `GET /apiKeys/?action=invite&apiKey=<key>` - Invitation page
- `POST /apiKeys/` - Save/create/accept operations

**Features**:
- Budget tracking per API key
- Usage monitoring
- Invitation system for sharing keys
- Welcome messages for invitations
- Mapped key management (proxy functionality)

**Data Structure**:
```kotlin
data class ApiKeyRecord(
    val owner: String,
    val apiKey: String,
    val mappedKey: String,
    val budget: Double,
    val comment: String,
    val welcomeMessage: String
)
```

### ProxyHttpServlet
**Purpose**: Reverse proxy for OpenAI API with budget controls

**Default Target**: `https://api.openai.com/v1/`

**Features**:
- Async request/response handling
- Budget enforcement (returns 402 when exceeded)
- API key mapping and validation
- Request/response logging
- Connection pooling and retry logic

**Flow**:
1. Extract API key from Authorization header
2. Look up proxy configuration
3. Check budget limits
4. Forward request with mapped key
5. Stream response back to client
6. Log usage for billing

---

## Utility Servlets

### WelcomeServlet
**Purpose**: Main landing page and application directory

**Endpoints**:
- `/` or `/index.html` - Welcome page
- `/user` - User information JSON
- `/apps` - Available applications list
- `/userSettings` - Settings management (POST)
- Static resources

**Features**:
- Serves welcome HTML page
- Lists authorized applications
- Provides user context information
- Handles static resource serving

### AppInfoServlet
**Purpose**: Generic servlet for application information

**Endpoint**: Configurable
**Method**: GET
**Parameters**: `session` (optional)

**Usage**:
```kotlin
AppInfoServlet { sessionId ->
    // Return application-specific info
}
```

### UsageServlet
**Purpose**: Displays API usage statistics

**Endpoints**:
- `GET /usage` - User usage summary
- `GET /usage?sessionId=<id>` - Session-specific usage

**Display Format**:
- HTML table with model breakdown
- Token counts (prompt/completion)
- Cost calculations
- Total summaries

### CorsFilter
**Purpose**: Handles Cross-Origin Resource Sharing

**Applied To**: All endpoints except WebSocket (`/ws`)

**Headers Set**:
- `Access-Control-Allow-Origin: *`
- `Access-Control-Allow-Methods: POST, GET, OPTIONS, DELETE, PUT`
- `Access-Control-Max-Age: 3600`
- `Access-Control-Allow-Headers: Content-Type, x-requested-with, authorization`

---

## Security Considerations

### Authentication Flow
1. User accesses protected resource
2. SessionIdFilter checks for valid session cookie
3. If invalid, redirects to `/googleLogin`
4. OAuth flow completes, sets secure cookie
5. User redirected to original resource

### Authorization Levels
- **Read**: View sessions and data
- **Write**: Modify sessions
- **Delete**: Remove sessions
- **Share**: Create shareable links
- **Public**: Access global sessions
- **Admin**: Full system access

### API Key Security
- Keys are masked in UI (`********`)
- Stored securely in JSON files
- Budget limits prevent abuse
- Usage tracking for accountability

### File Access
- Session-based isolation
- User authentication required
- Path traversal protection
- Hidden file exclusion from listings

---

## Configuration and Deployment

### Required Dependencies
- Jakarta Servlet API
- Jetty Server
- Google OAuth2 libraries
- Apache HTTP Client
- Jackson JSON processing
- Selenium WebDriver (for sharing)

### Environment Variables
- `domain` - Application domain for sharing
- OAuth client credentials
- Storage root directory
- Cloud storage configuration

### Typical Deployment Structure
```
/webapp
  ├── /login (OAuthGoogle)
  ├── /logout (LogoutServlet)
  ├── /sessions (SessionListServlet)
  ├── /files/* (SessionFileServlet)
  ├── /apiKeys/ (ApiKeyServlet)
  ├── /proxy/* (ProxyHttpServlet)
  └── /* (WelcomeServlet)
```
