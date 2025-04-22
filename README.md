# Cognotik Project

Welcome to **Cognotik**, a comprehensive system designed to provide advanced AI-driven functionalities, including code diffing, patching, real-time communication, and more. This project is divided into two primary modules:

1. **Web UI Module (`webui`)**: Handles the user interface, real-time communication, and interactive features.
2. **Core Module (`core`)**: Serves as the backbone, managing AI interactions, data processing, and essential services.

This README provides a detailed overview for developers looking to understand, develop, and contribute to the Cognotik project.

---

## Table of Contents

- [Cognotik Project](#skyenet-project)
  - [Table of Contents](#table-of-contents)
  - [Introduction](#introduction)
  - [Modules Overview](#modules-overview)
    - [Web UI Module (`webui`)](#web-ui-module-webui)
    - [Core Module (`core`)](#core-module-core)
  - [Architecture](#architecture)
  - [Key Components](#key-components)
    - [Web UI Module Components](#web-ui-module-components)
    - [Core Module Components](#core-module-components)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Installation](#installation)
    - [Configuration](#configuration)
  - [Usage](#usage)
    - [Web UI Module](#web-ui-module)
    - [Core Module](#core-module)
  - [Testing](#testing)
    - [Running Tests](#running-tests)
    - [Test Coverage](#test-coverage)
  - [Development](#development)
    - [Contributing](#contributing)
    - [Coding Standards](#coding-standards)
    - [Reporting Issues](#reporting-issues)
  - [Security](#security)
  - [Logging](#logging)
  - [Extending and Customization](#extending-and-customization)
  - [License](#license)
  - [Contact](#contact)

---

## Introduction

**Cognotik** is an advanced system that integrates AI-driven functionalities to assist developers and users in various tasks such as code comparison, patch management, real-time communication, and more. Leveraging OpenAI's cutting-edge models, Cognotik ensures seamless integration of AI capabilities into applications, enhancing productivity and efficiency.

---

## Modules Overview

Cognotik is organized into two main modules, each responsible for distinct functionalities:

### Web UI Module (`webui`)

The Web UI Module provides an interactive and user-friendly interface for users to engage with Cognotik's functionalities. It handles tasks such as rendering diffs, managing sessions, integrating AI features, and ensuring secure and efficient communication between clients and the server.

### Core Module (`core`)

The Core Module serves as the backbone of Cognotik, managing AI-driven interactions, data processing, authentication, authorization, and integration with external services like AWS and OpenAI. It ensures that all core functionalities operate seamlessly and efficiently.

---

## Architecture

Cognotik follows a modular and layered architecture, ensuring separation of concerns, scalability, and ease of maintenance. This architecture facilitates independent development, testing, and deployment of each module.

### High-Level Architecture

1. **Web Server Layer**: Powered by Jetty, it handles HTTP requests, WebSocket connections, and servlet management.
2. **Authentication Layer**: Manages user authentication using OAuth (Google) and handles session cookies.
3. **Chat Management Layer**: Core functionalities for managing chat sessions, messages, and interactions with OpenAI's APIs.
4. **Storage Layer**: Abstracted through `StorageInterface`, handling data persistence for sessions, user settings, and API keys.
5. **API Management Layer**: Manages API keys, usage tracking, and integration with OpenAI's ChatClient.
6. **Extension Layer**: Allows integration of various actors and custom servlets to extend functionalities.

---

## Key Components

### Web UI Module Components

#### 1. ChatServer

- **Location**: `webui/chat/ChatServer.kt`
- **Description**: Initializes and configures WebSocket handlers, manages chat sessions, and integrates with storage systems.
- **Responsibilities**:
  - Configure WebSocket settings (timeouts, buffer sizes).
  - Manage `ChatSocket` instances for each user session.
  - Interface with `StorageInterface` for session data persistence.
  - Provide hooks for creating new chat sessions.

#### 2. ChatSocket

- **Location**: `webui/chat/ChatSocket.kt`
- **Description**: Handles individual WebSocket connections, managing the lifecycle of each connection.
- **Responsibilities**:
  - Handle WebSocket events (`onWebSocketConnect`, `onWebSocketText`, `onWebSocketClose`).
  - Interface with `SocketManager` for message processing.
  - Retrieve user information associated with the session.

#### 3. ChatSocketManager

- **Location**: `webui/chat/ChatSocketManager.kt`
- **Description**: Manages interactions between `ChatSocket` instances and OpenAI's APIs.
- **Responsibilities**:
  - Initialize message histories with system prompts.
  - Handle user messages and interact with OpenAI's `ChatClient`.
  - Render responses and manage conversation flows.
  - Log API interactions and errors.

#### 4. Servlets

- **Location**: `webui/servlet/`
- **Description**: Handle various HTTP endpoints for functionalities like authentication, file management, API key management, and more.
- **Key Servlets**:
  - `OAuthGoogle.kt`: Implements Google OAuth for user authentication.
  - `CorsFilter.kt`: Implements CORS policies.
  - `ApiKeyServlet.kt`: Manages API keys.
  - `Session*Servlet.kt`: Various servlets for session management.

#### 5. Session Management

- **Location**: `webui/session/`
- **Components**:
  - `SocketManager.kt`: Interface for managing WebSocket connections.
  - `SocketManagerBase.kt`: Base implementation handling message queues and session tasks.
  - `SessionTask.kt`: Represents tasks associated with a session.

#### 6. ApplicationServer

- **Location**: `webui/application/ApplicationServer.kt`
- **Description**: Extends `ChatServer` to provide a complete application framework, managing web contexts, servlets, and server initialization.
- **Responsibilities**:
  - Initialize and configure web contexts and servlets.
  - Manage child web applications.
  - Handle user authentication and authorization.
  - Provide settings management for sessions and users.

### Core Module Components

#### 1. Actors

- **Location**: `core/actors/`
- **Description**: Interfaces for different AI-driven operations.
- **Key Actors**:
  - `BaseActor`: Abstract class for common functionalities.
  - `SimpleActor`: Handles straightforward interactions.
  - `CodingActor`: Translates natural language into executable code.
  - `ImageActor`: Generates images from textual descriptions.
  - `ParsedActor`: Parses user inputs into structured objects.
  - `TextToSpeechActor`: Converts text responses into audio files.

#### 2. Utilities

- **Location**: `core/utilities/`
- **Description**: Supporting functionalities for various components.
- **Key Utilities**:
  - `StringSplitter.kt`: Splits strings based on weighted separators.
  - `DiffUtil.kt`, `PatchResult.kt`, `DiffMatchPatch.kt`: Handle diffing and patching.
  - `RuleTreeBuilder.kt`: Constructs rule-based expressions.
  - `LoggingInterceptor.kt`: Manages log outputs.
  - `FileValidationUtils.kt`: Validates code syntax.

#### 3. Platform Models

- **Location**: `core/platform/models/`
- **Description**: Define interactions with external services and manage operations like authentication and cloud integrations.
- **Key Models**:
  - `AuthenticationManager.kt`: Manages user authentication.
  - `AuthorizationManager.kt`: Controls access based on roles.
  - `AwsPlatform.kt`: Integrates with AWS services for encryption and storage.

#### 4. Data Storage

- **Location**: `core/data/storage/`
- **Description**: Manages data persistence for sessions, user data, and usage statistics.
- **Key Components**:
  - `DataStorage.kt`: Implements `StorageInterface`.
  - `MetadataStorage.kt`: Manages metadata using HSQLDB.
  - `UsageManager.kt`: Tracks usage metrics.

#### 5. Client Management

- **Location**: `core/client/`
- **Description**: Manages API clients for interacting with OpenAI's models and other services.
- **Key Components**:
  - `ClientManager.kt`: Manages instances of `ChatClient` and `OpenAIClient`.

---

## Getting Started

### Prerequisites

Ensure the following are installed on your development environment:

- **Java Development Kit (JDK) 11+**
- **Kotlin 1.7+**
- **Gradle or Maven**: For building the project.
- **Selenium WebDriver**: For web automation tasks.
- **ChromeDriver**: Matches your Chrome browser version.
- **AWS Credentials**: For AWS KMS encryption (if used).
- **OpenAI API Key**: For AI-assisted functionalities.
- **TensorFlow Projector CLI**: For generating embeddings (optional).

### Installation

#### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/Cognotik.git
cd Cognotik
```

#### 2. Build the Project

Use Gradle or Maven to build the project. For Gradle:

```bash
./gradlew build
```

#### 3. Configure Dependencies

Ensure all dependencies are correctly specified in your `build.gradle` or `pom.xml`. Key dependencies include:

- **JSoup**: HTML parsing and sanitization.
- **GraalVM**: JavaScript validation.
- **Apache Commons Text**: Text processing.
- **Selenium**: Web automation.
- **Jackson**: JSON handling.
- **SLF4J**: Logging.

#### 4. Set Up Environment Variables

Configure necessary environment variables:

- `OpenAI_API_KEY`: Your OpenAI API key.
- `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`: For AWS KMS encryption (if used).

### Configuration

#### WebDriver Configuration

Ensure the `chromedriver` executable is accessible. Update the `Selenium2S3.kt` file's `chromeDriver` function if necessary to point to your `chromedriver` path.

#### AI Configuration

Configure the `OpenAIClient` with your API key to enable AI-assisted patch fixing.

#### Storage Configuration

Update the `TensorflowProjector.kt` with appropriate storage paths and cloud configurations to handle embeddings and visualizations.

---

## Usage

### Web UI Module

#### Running the Server

1. **Navigate to the Web UI Directory**

   ```bash
   cd webui
   ```

2. **Run the Server**

   ```bash
   ./gradlew run
   ```

   The server will start on the configured port (default: `8081`) and open the application in your default browser.

#### Accessing the Application

1. **Open Your Browser**

   Navigate to `http://localhost:8081/` (or your configured domain).

2. **Authenticate**

  - Click on the login link to authenticate via Google OAuth.
  - Upon successful authentication, you'll be redirected to the main interface.

3. **Start a Chat Session**

  - Create a new session or join an existing one.
  - Interact with the chat interface to send and receive messages powered by OpenAI's APIs.

4. **Manage Sessions and API Keys**

  - Use the provided interfaces to create, edit, delete, and share API keys.
  - Monitor usage statistics and manage budgets to control API consumption.

### Core Module

#### Using Actors

##### 1. Simple Chat Interaction

```kotlin
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices

fun simpleChatExample() {
    val simpleActor = SimpleActor(
        prompt = "You are a helpful assistant.",
        model = OpenAIModels.GPT4Turbo,
        temperature = 0.5
    )

    val chatClient = ApplicationServices.clientManager.getChatClient(session, user)

    val response = simpleActor.answer(
        input = listOf("Hello, how can you assist me today?"),
        api = chatClient
    )

    println("Assistant: $response")
}
```

##### 2. Code Generation and Execution

```kotlin
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.interpreter.PythonInterpreter
import com.simiacryptus.skyenet.core.platform.ApplicationServices

fun codeGenerationExample() {
    val codingActor = CodingActor(
        interpreterClass = PythonInterpreter::class,
        model = OpenAIModels.GPT4Turbo,
        fallbackModel = OpenAIModels.GPT4o,
        temperature = 0.3
    )

    val chatClient = ApplicationServices.clientManager.getChatClient(session, user)

    val codeResult = codingActor.answer(
        input = listOf("Write a function to calculate the factorial of a number."),
        api = chatClient
    )

    println("Generated Code:\n${codeResult}")
    println("Execution Result: ${codeResult.result.resultValue}")
    println("Execution Output: ${codeResult.result.resultOutput}")
}
```

##### 3. Image Generation

```kotlin
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import javax.imageio.ImageIO
import java.io.File

fun imageGenerationExample() {
    val imageActor = ImageActor(
        prompt = "Create an image of a sunset over a mountain range.",
        model = OpenAIModels.GPT4o,
        imageModel = ImageModels.DallE2,
        temperature = 0.7,
        width = 1024,
        height = 768
    )

    val chatClient = ApplicationServices.clientManager.getChatClient(session, user)

    val imageResponse = imageActor.answer(
        input = listOf("Create an image of a sunset over a mountain range."),
        api = chatClient
    )

    // Save the generated image
    ImageIO.write(imageResponse.image, "png", File("sunset.png"))
    println("Image saved as sunset.png")
}
```

##### 4. Text-to-Speech Conversion

```kotlin
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.TextToSpeechActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import java.io.File

fun textToSpeechExample() {
    val ttsActor = TextToSpeechActor(
        name = "AudioAssistant",
        audioModel = AudioModels.TTS_HD,
        voice = "alloy",
        speed = 1.0,
        models = OpenAIModels.GPT4Turbo
    )

    val chatClient = ApplicationServices.clientManager.getOpenAIClient(session, user)

    val speechResponse = ttsActor.answer(
        input = listOf("Hello, how can I assist you today?"),
        api = chatClient
    )

    // Save the MP3 data
    speechResponse.mp3data?.let {
        File("response.mp3").writeBytes(it)
        println("Audio saved as response.mp3")
    } ?: run {
        println("No audio data received.")
    }
}
```

---

## Testing

### Running Tests

Both modules include a suite of unit and integration tests to ensure functionality and reliability.

#### Web UI Module

1. **Navigate to the Web UI Directory**

   ```bash
   cd webui
   ```

2. **Run Tests**

   ```bash
   ./gradlew test
   ```

#### Core Module

1. **Navigate to the Core Directory**

   ```bash
   cd core
   ```

2. **Run Tests**

   ```bash
   ./gradlew test
   ```

### Test Coverage

Generate test coverage reports to ensure all components are adequately tested.

```bash
./gradlew jacocoTestReport
```

Reports are available in the `build/reports/jacoco/test/html` directory.

---

## Development

### Contributing

We welcome contributions to enhance the **Cognotik** project. To contribute:

1. **Fork the Repository**

   Click the "Fork" button on the [GitHub repository](https://github.com/yourusername/Cognotik) to create a personal copy.

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

### Coding Standards

- **Language**: Kotlin
- **Style**: Follow Kotlin coding conventions.
- **Documentation**: Document new components and functions clearly.
- **Testing**: Ensure all tests pass before submitting a pull request.

### Reporting Issues

If you encounter any issues or have suggestions for improvements:

- Open an issue on the [GitHub repository](https://github.com/yourusername/Cognotik/issues).

---

## Security

Security is a primary concern within the Cognotik project, addressed through:

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

---

## Logging

Logging is implemented using **SLF4J** with Logback as the backing implementation. Logs provide insights into the module's operations, aiding in debugging and monitoring.

### Configuration

- **Log Levels**: Adjust log levels (INFO, DEBUG, ERROR) in `logback.xml`.
- **Log Output**: Configure log outputs (console, file) based on your deployment setup.

Ensure that sensitive information (e.g., API keys) is not logged to prevent security breaches.

---

## Extending and Customization

Cognotik's modular design allows for easy extension and customization.

### Adding New Actors

1. **Implement the Actor**

   Extend the `BaseActor` class and implement necessary methods.

   ```kotlin
   class NewActor : BaseActor() {
       // Implementation
   }
   ```

2. **Register the Actor**

   Register the new actor in the appropriate manager or configuration.

3. **Create Test Applications**

   Develop corresponding test applications to validate functionalities.

### Custom Servlets

1. **Develop the Servlet**

   Extend `HttpServlet` and implement required methods.

   ```kotlin
   class CustomServlet : HttpServlet() {
       override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
           // Implementation
       }
   }
   ```

2. **Integrate into ApplicationServer**

   Register the new servlet in `ApplicationServer.kt`.

   ```kotlin
   context.addServlet(CustomServlet::class.java, "/custom-endpoint")
   ```

### Integrating Additional APIs

1. **Extend `ChatSocketManager.kt`**

   Implement interactions with the new external API.

2. **Handle API Responses and Errors**

   Ensure proper handling of responses and implement error management.
