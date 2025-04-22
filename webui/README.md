
## Features

- **Diff Generation & Patch Application**: Efficiently compare two versions of code to identify differences and generate patches. Apply these patches to update codebases seamlessly.
- **Interactive Web UI Integration**: Render diffs in a web-based interface with interactive tabs, allowing users to view, apply, and revert changes effortlessly.
- **Multi-language Code Validation**: Validate code syntax and structure in Java, Kotlin, and JavaScript to ensure code correctness before and after patch application.
- **AI-assisted Patch Fixing**: Utilize OpenAI's language models to automatically fix and improve patches, enhancing the patching process's accuracy and reliability.
- **Code Visualization**: Integrate with TensorFlow Projector to visualize code embeddings, providing insights into code similarities and relationships.
- **Security and Sanitization**: Simplify and sanitize HTML content to prevent security vulnerabilities when rendering code and diffs.
- **Encryption Utilities**: Secure sensitive files using AWS KMS encryption, ensuring data protection within the module.
- **Real-Time Communication**: Utilizes WebSockets for instant message exchange between clients and the server.
- **OpenAI Integration**: Seamlessly integrates with OpenAI's APIs to provide AI-driven functionalities.
- **OAuth Authentication**: Implements Google OAuth for secure user authentication.
- **Session Management**: Supports multiple chat sessions with efficient session tracking and storage.
- **API Key Management**: Facilitates secure handling of API keys, including creation, editing, and deletion.
- **Extensible Architecture**: Easily extendable through various actors (e.g., ImageActor, CodingActor) and custom servlets.
- **Security**: Implements CORS filters, access control based on user permissions, and secure storage practices.
- **Logging and Monitoring**: Comprehensive logging for debugging and monitoring purposes.
- **Testing Utilities**: Includes test applications to validate different components and functionalities.

## Architecture

The module follows a modular and layered architecture, ensuring separation of concerns and ease of maintenance. Here's a high-level overview:

1. **Web Server Layer**: Powered by Jetty, it handles HTTP requests, WebSocket connections, and servlet management.
2. **Authentication Layer**: Manages user authentication using OAuth (Google) and handles session cookies.
3. **Chat Management Layer**: Core functionalities for managing chat sessions, messages, and interactions with OpenAI's APIs.
4. **Storage Layer**: Abstracted through `StorageInterface`, it handles data persistence for sessions, user settings, and API keys.
5. **API Management Layer**: Manages API keys, usage tracking, and integration with OpenAI's ChatClient.
6. **Extension Layer**: Allows integration of various actors and custom servlets to extend functionalities.

## Key Components

### ChatServer

- **Location**: `chat/ChatServer.kt`
- **Description**: An abstract base class responsible for initializing and configuring the WebSocket handlers, managing chat sessions, and integrating with storage systems.
- **Key Responsibilities**:
  - Configure WebSocket settings (e.g., timeouts, buffer sizes).
  - Create and manage `ChatSocket` instances for each user session.
  - Interface with `StorageInterface` for session data persistence.
  - Provide hooks for creating new chat sessions through `newSession`.

### ChatSocket

- **Location**: `chat/ChatSocket.kt`
- **Description**: Extends `WebSocketAdapter` to handle individual WebSocket connections. Manages the lifecycle of a WebSocket (open, message received, close).
- **Key Responsibilities**:
  - Handle WebSocket connection events (`onWebSocketConnect`, `onWebSocketText`, `onWebSocketClose`).
  - Interface with `SocketManager` to process incoming messages and manage outgoing messages.
  - Retrieve user information associated with the session.

### ChatSocketManager

- **Location**: `chat/ChatSocketManager.kt`
- **Description**: Manages the interactions between `ChatSocket` instances and OpenAI's APIs. Maintains message histories, handles API requests, and processes responses.
- **Key Responsibilities**:
  - Initialize message histories with system prompts.
  - Handle user messages and interact with OpenAI's `ChatClient`.
  - Render responses and manage conversation flows.
  - Log API interactions and errors.

### Test Applications

Located under the `test/` package, these applications facilitate testing and validation of different components:

- **FilePatchTestApp**: Tests file patching and diff application functionalities.
- **ImageActorTestApp**: Validates image generation and handling through `ImageActor`.
- **CodingActorTestApp**: Tests coding-related functionalities and code execution via `CodingActor`.
- **ParsedActorTestApp**: Assesses parsing capabilities and structured data handling.
- **SimpleActorTestApp**: Evaluates basic actor functionalities and response generation.

### Servlets

Located under the `servlet/` package, these servlets handle various HTTP endpoints:

- **OAuthBase.kt**: An abstract base for OAuth implementations.
- **OAuthGoogle.kt**: Implements Google OAuth for user authentication.
- **CorsFilter.kt**: Implements CORS policies to control resource sharing across origins.
- **ZipServlet.kt**: Handles zipping and downloading of session files.
- **FileServlet.kt**: Serves files and directories, handling large file transfers efficiently.
- **UsageServlet.kt**: Provides usage statistics and API consumption details.
- **ApiKeyServlet.kt**: Manages API keys, allowing users to create, edit, delete, and invite others.
- **LogoutServlet.kt**: Handles user logout and session termination.
- **AppInfoServlet.kt**: Serves application-specific information in JSON format.
- **SessionIdFilter.kt**: Ensures secure session handling and redirects unauthorized users.
- **UserInfoServlet.kt**: Provides user information in JSON format.
- **ProxyHttpServlet.kt**: Acts as a reverse proxy for API requests, enforcing budget constraints.
- **NewSessionServlet.kt**: Generates new session IDs.
- **SessionFileServlet.kt**: Manages file access within user sessions.
- **SessionListServlet.kt**: Lists active sessions for a user.
- **SessionShareServlet.kt**: Facilitates sharing of session data via generated URLs and QR codes.
- **SessionSettingsServlet.kt**: Allows users to view and update session-specific settings.
- **CancelThreadsServlet.kt**: Provides functionality to cancel ongoing thread tasks.
- **DeleteSessionServlet.kt**: Enables deletion of user sessions.
- **SessionThreadsServlet.kt**: Displays active thread stacks for debugging and monitoring.

### Session Management

- **Location**: `session/`
- **Components**:
  - **SocketManager.kt**: Defines the `SocketManager` interface for managing WebSocket connections.
  - **SocketManagerBase.kt**: Provides a base implementation for `SocketManager`, handling message queues, session tasks, and interaction with `ChatSocket` instances.
  - **SessionTask.kt**: Represents a task associated with a session, managing message rendering, file saving, and user interactions.

### ApplicationServer

- **Location**: `application/ApplicationServer.kt`
- **Description**: Extends `ChatServer` to provide a complete application framework. Manages web application contexts, integrates servlets, and handles server initialization.
- **Key Responsibilities**:
  - Initialize and configure web contexts and servlets.
  - Manage child web applications.
  - Handle user authentication and authorization.
  - Provide settings management for sessions and users.

## Architecture

The module is structured into several key components, each responsible for specific functionalities:

1. **Diff Utilities**: Core algorithms for generating and applying diffs and patches, ensuring accurate code comparisons and updates.

2. **Validation**: Language-specific validators for Java, Kotlin, and JavaScript to maintain code integrity.

3. **Web UI Integration**: Components like `AgentPatterns`, `Discussable`, and `AddApplyDiffLinks` manage the rendering and interactive aspects of diffs within the web interface.

4. **Visualization**: The `TensorflowProjector` facilitates the embedding and visualization of code data.

5. **Utilities**: Additional tools for encryption (`EncryptFiles`) and HTML sanitization (`HtmlSimplifier`) support the module's security and usability.

## Getting Started

### Prerequisites

Ensure that the following are installed on your development environment:

- **Java 19 SDK**: Required for compiling and running Java-based components.
- **Kotlin 1.7+**: Necessary for Kotlin validation and related tasks.
- **Selenium WebDriver**: For web automation tasks.
- **ChromeDriver**: Specific driver for Google Chrome, matching your Chrome browser version.
- **AWS Credentials**: If utilizing encryption features with AWS KMS.
- **OpenAI API Key**: For AI-assisted functionalities.
- **TensorFlow Projector CLI**: For generating embeddings (optional, based on usage).

### Installation

1. **Clone the Repository**

   ```bash
   git clone https://github.com/yourusername/Cognotik-DiffPatchUtility.git
   cd Cognotik-DiffPatchUtility
   ```

2. **Build the Project**

   Use Gradle or Maven to build the project. For Gradle:

   ```bash
   ./gradlew build
   ```

3. **Configure Dependencies**

   Ensure all dependencies are correctly specified in your `build.gradle` or `pom.xml`. Key dependencies include:

  - **JSoup**: For HTML parsing and sanitization.
  - **GraalVM**: For JavaScript validation.
  - **Apache Commons Text**: For text processing tasks.
  - **Selenium**: For web automation.
  - **Jackson**: For JSON handling.
  - **SLF4J**: For logging.

4. **Set Up Environment Variables**

   Configure necessary environment variables:

  - **OpenAI_API_KEY**: Set your OpenAI API key.
  - **AWS_ACCESS_KEY_ID** and **AWS_SECRET_ACCESS_KEY**: For AWS KMS encryption (if used).

### Configuration

- **WebDriver Configuration**

  Ensure that the `chromedriver` executable is accessible. Update the `Selenium2S3.kt` file's `chromeDriver` function if necessary to point to your `chromedriver` path.

- **AI Configuration**

  Configure the `OpenAIClient` with your API key to enable AI-assisted patch fixing.

- **Storage Configuration**

  Update the `TensorflowProjector.kt` with appropriate storage paths and cloud configurations to handle embeddings and visualizations.

## Usage

### Generating and Applying Patches

1. **Generate a Diff**

   Use the `DiffMatchPatch` class to generate a diff between two versions of code:

   ```kotlin
   val diffs = DiffMatchPatch.diff_main(oldCode, newCode, checklines = true)
   DiffMatchPatch.diff_cleanupSemantic(diffs)
   val patch = DiffMatchPatch.patch_make(oldCode, diffs)
   ```

2. **Apply a Patch**

   Apply the generated patch to the original code:

   ```kotlin
   val result = DiffMatchPatch.patch_apply(patch, oldCode)
   val patchedCode = result[0] as String
   val applyResults = result[1] as BooleanArray
   ```

### Code Validation

- **Java Validation**

  ```kotlin
  val isValid = JavaValidator.isValid(javaCode)
  val (valid, errors) = JavaValidator.validateWithErrors(javaCode)
  ```

- **Kotlin Validation**

  ```kotlin
  val isValid = KotlinValidator.isValid(kotlinCode)
  val (valid, errors) = KotlinValidator.validateWithErrors(kotlinCode)
  ```

- **JavaScript Validation**

  ```kotlin
  val isValid = JavaScriptValidator.isValid(jsCode)
  val (valid, errors) = JavaScriptValidator.validateWithErrors(jsCode)
  ```

### Visualization

- **Generating Embeddings and Visualization**

  ```kotlin
  val projector = TensorflowProjector(api, dataStorage, sessionID, session, userId)
  val htmlContent = projector.writeTensorflowEmbeddingProjectorHtml(listOf("function", "variable"))
  // Embed the HTML content into your web UI
  ```

## Components Overview

### AgentPatterns

**File:** `AgentPatterns.kt`

**Purpose:** Handles the rendering of code diffs within a tabbed interface. If the diff map is large (`sum of key and value lengths > 10000`), it splits the rendering into multiple tasks for performance optimization.

**Key Functions:**

- `displayMapInTabs`: Renders a map of diffs into an HTML tabbed interface, optionally splitting large diffs into separate tabs.

### Discussable

**File:** `Discussable.kt`

**Purpose:** Facilitates interactive discussions within the web UI, allowing users to provide feedback on AI-generated responses. Integrates with AI models to revise responses based on user input.

**Key Components:**

- `TabbedDisplay`: Manages tabs for different stages of the discussion.
- `feedbackForm`: Presents a feedback form to the user.
- `Retryable`: Allows users to retry applying a task if errors occur.

### Diff Utilities

**Files:** `DiffUtil.kt`, `PatchResult.kt`, `DiffMatchPatch.kt`

**Purpose:** Implements robust diff algorithms to identify differences between two texts, generate patches based on these differences, and apply patches to update texts.

**Key Functions:**

- `generateDiff`: Compares two lists of strings and categorizes differences.
- `formatDiff`: Formats the diff results into a human-readable string.
- `patch_make`: Generates patches from diffs.
- `patch_apply`: Applies patches to source texts.

### AddApplyDiffLinks

**File:** `AddApplyDiffLinks.kt`

**Purpose:** Enhances the web UI by adding interactive links to diffs, allowing users to apply or revert changes directly from the UI. Integrates with AI for automatic patch application and fixing invalid patches.

**Key Features:**

- Automatic patch application based on validation.
- Interactive buttons for applying and reverting diffs.
- Integration with AI models to fix invalid patches.

### TensorflowProjector

**File:** `TensorflowProjector.kt`

**Purpose:** Generates embeddings for code snippets and visualizes them using TensorFlow Projector. Facilitates the upload and storage of embedding vectors and metadata.

**Key Functions:**

- `writeTensorflowEmbeddingProjectorHtml`: Creates HTML content embedding TensorFlow Projector visualizations.
- `toVectorMap`: Generates embeddings for a list of words or records.
- `validate`: Ensures the integrity of content types during upload.

### TabbedDisplay

**File:** `TabbedDisplay.kt`

**Purpose:** Provides a reusable component for rendering content within a tabbed interface in the web UI. Manages the creation, updating, and removal of tabs.

**Key Features:**

- Dynamic tab creation based on content.
- Optional closable tabs.
- Synchronization for thread-safe updates.

### Validators

**Files:** `JavaValidator.kt`, `KotlinValidator.kt`, `JavaScriptValidator.kt`, `LanguageValidator.kt`

**Purpose:** Offer language-specific validation for Java, Kotlin, and JavaScript code snippets to ensure syntax and structural correctness.

**Key Components:**

- **LanguageValidator**: An interface defining validation methods.
- **JavaValidator**: Implements Java code validation using Eclipse JDT.
- **KotlinValidator**: Implements Kotlin code validation using Kotlin Compiler APIs.
- **JavaScriptValidator**: Implements JavaScript code validation using GraalVM.

### ProcessInterpreter

**File:** `ProcessInterpreter.kt`

**Purpose:** Executes shell commands and scripts within the module. Facilitates running commands like `bash`, capturing output, and handling timeouts.

**Key Features:**

- Configurable command execution.
- Environment variable support.
- Timeout management to prevent hanging processes.
- Integration with session and security frameworks.

### EncryptFiles

**File:** `EncryptFiles.kt`

**Purpose:** Provides utilities to encrypt and securely store sensitive files using AWS KMS. Ensures that sensitive information like API keys and secrets are protected.

**Key Functions:**

- `encrypt`: Encrypts file content using a specified KMS key.
- `write`: Writes encrypted content to a specified path.

### HtmlSimplifier

**File:** `HtmlSimplifier.kt`

**Purpose:** Sanitizes and simplifies HTML content to prevent security vulnerabilities and ensure compatibility when rendering diffs and code snippets in the web UI.

**Key Features:**

- Removes unsafe elements and attributes.
- Filters event handlers and external content sources.
- Converts relative URLs to absolute based on a base URL.
- Simplifies nested HTML structures for cleaner output.

## Development

### Contributing

We welcome contributions to enhance the **Cognotik Diff & Patch Utility Module**. To contribute:

1. **Fork the Repository**

   Click the "Fork" button on the [GitHub repository](https://github.com/yourusername/Cognotik-DiffPatchUtility) to create a personal copy.

2. **Create a Feature Branch**

   ```bash
   git checkout -b feature/YourFeatureName
   ```

3. **Commit Your Changes**

   ```bash
   git commit -m "Add Your Feature"
   ```

4. **Push to Your Fork**

   ```bash
   git push origin feature/YourFeatureName
   ```

5. **Open a Pull Request**

   Navigate to the original repository and open a pull request comparing your branch with the main branch.

### Testing

The module includes a suite of unit and integration tests to ensure functionality and reliability.

1. **Run Tests**

   Use Gradle to execute tests:

   ```bash
   ./gradlew test
   ```

2. **Test Coverage**

   Generate test coverage reports:

   ```bash
   ./gradlew jacocoTestReport
   ```

## Logging

Logging is implemented using **SLF4J** for standardized log management. Logs provide insights into the module's operations, aiding in debugging and monitoring.

**Configuration:**

- **Log Levels**: Adjust log levels (INFO, DEBUG, ERROR) as needed in the application's configuration files.
- **Log Output**: Configure log outputs (console, file) based on your deployment setup.

## Troubleshooting

- **Diff Generation Issues**: Ensure that the input texts are correctly formatted and that the diff algorithms are not hitting recursion depth limits.

- **Patch Application Errors**: Validate the integrity of the patches and ensure that the source text hasn't diverged significantly from the expected state.

- **Validation Failures**: Check the output error messages for syntax or structural issues in the code snippets.

- **Selenium WebDriver Errors**: Verify that the `chromedriver` path is correctly configured and that it matches your installed Chrome version.

- **AI Integration Problems**: Ensure that the OpenAI API key is correctly set and that the API services are accessible.

## Setup and Installation

### Prerequisites

- **JDK 11 or higher**: Ensure Java is installed and configured.
- **Gradle**: For building the project.
- **Dependencies**: All dependencies are managed via Gradle and will be resolved during the build process.

### Installation Steps

1. **Clone the Repository**

   ```bash
   git clone https://github.com/your-repo/skyenet-webui-chat.git
   cd skyenet-webui-chat
   ```

2. **Build the Project**

   ```bash
   ./gradlew build
   ```

3. **Configure OAuth Credentials**

  - Obtain OAuth 2.0 credentials from the Google Cloud Console.
  - Place the `client_secret_google_oauth.json.kms` file in the `resources/` directory.
  - Ensure the OAuth redirect URI matches `$domainName/oauth2callback`.

4. **Configure API Keys**

  - Add your OpenAI API keys in the `openai.key.json.kms` file located in the `resources/` directory.
  - Ensure keys are securely encrypted if handling sensitive information.

5. **Run the Server**

   ```bash
   ./gradlew run
   ```

   The server will start on the configured port (default: `8081`) and open the application in your default browser.

## Configuration

Configuration parameters can be adjusted by modifying the relevant properties in the code or through external configuration files. Key configurations include:

- **Server Port**: Defined in `ApplicationDirectory.kt` (`val port: Int = 8081`).
- **OAuth Settings**: Managed in `OAuthGoogle.kt`.
- **CORS Policies**: Configured in `CorsFilter.kt`.
- **API Key Management**: Handled by `ApiKeyServlet.kt`.
- **Storage Settings**: Abstracted through `StorageInterface` and implemented as per requirements.
- **Logging Levels**: Adjusted via `logback.xml` or equivalent logging configurations.

## Usage

Once the server is running:

1. **Access the Application**

   Open your browser and navigate to `http://localhost:8081/` (or your configured domain).

2. **Authenticate**

  - Click on the login link to authenticate via Google OAuth.
  - Upon successful authentication, you'll be redirected to the main interface.

3. **Start a Chat Session**

  - Create a new session or join an existing one.
  - Interact with the chat interface to send and receive messages powered by OpenAI's APIs.

4. **Manage Sessions and API Keys**

  - Use the provided interfaces to create, edit, delete, and share API keys.
  - Monitor usage statistics and manage budgets to control API consumption.

## Extending and Customization

The modular design allows for easy extension and customization:

- **Adding New Actors**
  - Implement new actors by extending existing actor classes (e.g., `ImageActor`, `CodingActor`).
  - Create corresponding test applications to validate functionalities.

- **Custom Servlets**
  - Develop custom servlets by extending `HttpServlet` and integrating them into the `ApplicationServer`.
  - Register new servlets in `ApplicationServer.kt` to handle specific endpoints.

- **Integrating Additional APIs**
  - Extend `ChatSocketManager.kt` to interact with other external APIs as needed.
  - Ensure proper handling of API responses and error management.

## Testing

The `test/` package contains several test applications designed to validate different aspects of the module:

- **Running Test Applications**
  - Navigate to the `test/` directory.
  - Execute test applications using Gradle or your preferred IDE.

  ```bash
  ./gradlew run --args='testAppName'
  ```

- **Test Scenarios**
  - **File Operations**: Validate file patching and diff application.
  - **Image Generation**: Test image-related functionalities.
  - **Coding Execution**: Assess code generation and execution capabilities.
  - **Data Parsing**: Ensure accurate parsing of structured data.

## Logging

Logging is implemented using SLF4J with a backing implementation (e.g., Logback). Key logging features include:

- **Debugging Information**: Detailed logs for session management, message flows, and API interactions.
- **Error Tracking**: Comprehensive error logs to facilitate debugging and issue resolution.
- **Customizable Log Levels**: Adjust log verbosity through configuration files.

Ensure that sensitive information (e.g., API keys) is not logged to prevent security breaches.

## Security

Security is a primary concern in the Chat Module, addressed through:

- **OAuth Authentication**: Secure user authentication via Google OAuth.
- **CORS Policies**: Restrict resource access based on origin, methods, and headers.
- **API Key Management**: Secure handling, storage, and validation of API keys.
- **Access Control**: Authorization checks based on user roles and permissions.
- **Data Sanitization**: Prevent injection attacks by validating and sanitizing user inputs.
- **Secure Storage**: Encrypt sensitive data at rest and in transit.

**Best Practices**:

- Regularly update dependencies to patch known vulnerabilities.
- Enforce HTTPS in production environments.
- Implement rate limiting to prevent abuse.
- Monitor logs for suspicious activities.

## Contributing

Contributions are welcome! To contribute:

1. **Fork the Repository**

   ```bash
   git clone https://github.com/your-repo/skyenet-webui-chat.git
   ```

2. **Create a Feature Branch**

   ```bash
   git checkout -b feature/YourFeature
   ```

3. **Make Changes**

   Implement your feature or bug fix.

4. **Run Tests**

   Ensure all tests pass.

   ```bash
   ./gradlew test
   ```

5. **Commit and Push**

   ```bash
   git commit -m "Add feature: YourFeature"
   git push origin feature/YourFeature
   ```

6. **Create a Pull Request**

   Submit your PR for review.

**Guidelines**:

- Write clear, concise commit messages.
- Follow the existing code style and conventions.
- Ensure code is well-documented and tested.
- Address any review feedback promptly.
