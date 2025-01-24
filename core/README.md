# SkyeNet Core Module

Welcome to the **SkyeNet Core Module**! This module serves as the backbone of the SkyeNet system, providing essential functionalities for audio processing, AI-driven interactions, data management, and more. Leveraging OpenAI's cutting-edge models, SkyeNet Core facilitates seamless integration of advanced AI capabilities into applications.

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [Components](#components)
  - [Actors](#actors)
  - [Utilities](#utilities)
  - [Platform Models](#platform-models)
  - [Data Storage](#data-storage)
  - [Client Management](#client-management)
  - [Authentication & Authorization](#authentication--authorization)
  - [Cloud Platform Integration](#cloud-platform-integration)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Installation](#installation)
  - [Configuration](#configuration)
- [Usage](#usage)
  - [Examples](#examples)
- [Testing](#testing)
- [Contributing](#contributing)
- [License](#license)

## Introduction

The **SkyeNet Core Module** is designed to facilitate a wide range of AI-driven functionalities within the SkyeNet ecosystem. It offers interfaces for audio input, command recognition, image generation, code execution, text-to-speech conversion, and comprehensive data management. By abstracting complex processes and providing modular components, SkyeNet Core enables developers to build sophisticated applications with ease.

## Features

- **Audio Processing**: Capture and transcribe audio input using advanced transcription models.
- **Command Recognition**: Detect and handle specific commands from transcribed text.
- **Image Generation**: Create images based on textual descriptions using OpenAI's image models.
- **Code Execution**: Translate natural language instructions into executable code, validate, and execute it.
- **Text-to-Speech**: Convert textual responses into audio using high-definition TTS models.
- **Data Management**: Efficiently handle sessions, user data, and usage statistics.
- **Authentication & Authorization**: Secure access control mechanisms for users and operations.
- **Cloud Integration**: Seamless integration with AWS services for storage and encryption.

## Architecture Overview

The SkyeNet Core Module is structured into several interconnected components, each responsible for specific functionalities. The architecture emphasizes modularity, scalability, and ease of maintenance.

![Architecture Diagram](https://example.com/architecture-diagram.png)  <!-- Replace with actual diagram URL -->

### Key Architectural Principles

- **Modularity**: Each component is designed to function independently, allowing for easy updates and scalability.
- **Extensibility**: The system can be extended with additional features without impacting existing functionalities.
- **Reliability**: Robust error handling and validation mechanisms ensure system stability.

## Components

### Actors

Actors are the primary interfaces for different AI-driven operations. They encapsulate the logic required to interact with OpenAI's models and handle specific tasks.

- **BaseActor**: An abstract class that provides a foundation for all actors, handling common functionalities like prompt management and response processing.

- **SimpleActor**: Handles straightforward interactions, returning simple string responses based on user queries.

- **CodingActor**: Translates natural language instructions into executable code, validates it, and manages its execution. It supports multiple programming languages and handles errors gracefully.

- **ImageActor**: Generates images from textual descriptions using image models like DALL·E 2.

- **ParsedActor**: Parses user inputs into structured objects based on predefined schemas, facilitating more complex data interactions.

- **TextToSpeechActor**: Converts textual responses into audio files using high-definition TTS models.

### Utilities

Utility classes provide supporting functionalities that aid various components in performing their tasks efficiently.

- **StringSplitter**: Splits strings based on weighted separators, optimizing for natural language processing tasks.

- **SimpleDiffApplier & IterativePatchUtil**: Handle the application of diffs to source code, enabling iterative refinement of code snippets.

- **RuleTreeBuilder**: Constructs rule-based expressions for path matching, enhancing command recognition capabilities.

- **LoggingInterceptor**: Captures and redirects log outputs for monitoring and debugging purposes.

- **FileValidationUtils**: Validates the correctness of code syntax, ensuring balanced brackets and quotes.

### Platform Models

These models define the interactions with external services and manage essential operations like authentication, authorization, and cloud integrations.

- **AuthenticationInterface & AuthenticationManager**: Manage user authentication, associating users with access tokens.

- **AuthorizationInterface & AuthorizationManager**: Control access to various operations and resources based on user roles and permissions.

- **CloudPlatformInterface & AwsPlatform**: Integrate with AWS services for tasks like encryption and file storage, abstracting the complexities of cloud interactions.

### Data Storage

Efficient data management ensures that user sessions, messages, and usage statistics are stored and retrieved seamlessly.

- **DataStorage**: Implements the `StorageInterface`, handling the storage of messages, session data, and user-specific settings.

- **MetadataStorageInterface & HSQLMetadataStorage**: Manage metadata associated with sessions and users, utilizing HSQLDB for robust storage capabilities.

- **UsageInterface & HSQLUsageManager**: Track and summarize usage metrics, aiding in monitoring and billing processes.

### Client Management

Handles the creation and management of API clients required for interacting with OpenAI's models and other external services.

- **ClientManager**: Manages instances of `ChatClient` and `OpenAIClient`, ensuring that each user session has access to the necessary API clients with appropriate credentials.

## Getting Started

### Prerequisites

Before integrating the SkyeNet Core Module into your project, ensure you have the following prerequisites:

- **Java Development Kit (JDK)**: Version 11 or higher.
- **Kotlin**: Latest stable version.
- **Gradle or Maven**: For dependency management.
- **AWS Credentials**: If utilizing cloud integration features.
- **OpenAI API Key**: To access OpenAI's models.

### Installation

1. **Add Dependencies**

   For Gradle:

   ```groovy
   dependencies {
       implementation 'com.simiacryptus:skyenet-core:1.0.0' // Replace with actual group and version
       implementation 'org.apache.commons:commons-text:1.9'
       implementation 'com.fasterxml.jackson.core:jackson-databind:2.12.3'
       // Add other necessary dependencies
   }
   ```

   For Maven:

   ```xml
   <dependencies>
       <dependency>
           <groupId>com.simiacryptus</groupId>
           <artifactId>skyenet-core</artifactId>
           <version>1.0.0</version> <!-- Replace with actual version -->
       </dependency>
       <dependency>
           <groupId>org.apache.commons</groupId>
           <artifactId>commons-text</artifactId>
           <version>1.9</version>
       </dependency>
       <dependency>
           <groupId>com.fasterxml.jackson.core</groupId>
           <artifactId>jackson-databind</artifactId>
           <version>2.12.3</version>
       </dependency>
       <!-- Add other necessary dependencies -->
   </dependencies>
   ```

2. **Initialize Application Services**

   Before using the core functionalities, initialize the `ApplicationServices` to set up essential components like authentication, authorization, and data storage.

   ```kotlin
   import com.simiacryptus.skyenet.core.platform.ApplicationServices
   import com.simiacryptus.skyenet.core.platform.file.AuthenticationManager
   import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
   import com.simiacryptus.skyenet.core.platform.file.DataStorage
   import com.simiacryptus.skyenet.core.platform.file.MetadataStorage

   fun initializeApplicationServices() {
       ApplicationServices.isLocked = false // Set to true to prevent further modifications after setup
       ApplicationServices.authenticationManager = AuthenticationManager()
       ApplicationServices.authorizationManager = AuthorizationManager()
       ApplicationServices.dataStorageFactory = { DataStorage(it) }
       ApplicationServices.metadataStorageFactory = { MetadataStorage(it) }
       // Initialize other components as needed
       ApplicationServices.isLocked = true
   }
   ```

3. **Configure Cloud Integration (Optional)**

   If your application requires cloud functionalities like file storage or encryption:

   ```kotlin
   import com.simiacryptus.skyenet.core.platform.AwsPlatform

   fun configureCloudIntegration() {
       ApplicationServices.cloud = AwsPlatform.get()
       // Ensure AWS credentials are properly configured in your environment
   }
   ```

### Configuration

Configure the module based on your application's requirements. Key configurations include:

- **API Keys**: Manage OpenAI API keys and other service credentials.
- **Data Storage Paths**: Define where session data and metadata are stored.
- **Logging**: Set up logging preferences to monitor module activities.
- **Cloud Settings**: Configure cloud service parameters like bucket names and regions.

Configuration can be done via properties files, environment variables, or directly in the code as shown in the installation section.

## Usage

The SkyeNet Core Module provides various actors and utilities to perform AI-driven tasks. Below are some usage examples to get you started.

### Examples

#### 1. **Simple Chat Interaction**

Use `SimpleActor` to handle straightforward chat interactions.

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

#### 2. **Code Generation and Execution**

Leverage `CodingActor` to convert natural language instructions into executable code.

```kotlin
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.interpreter.Interpreter
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

    val codeRequest = CodingActor.CodeRequest(
        messages = listOf(
            "Write a function to calculate the factorial of a number." to ApiModel.Role.user
        ),
        autoEvaluate = true,
        fixIterations = 2,
        fixRetries = 1
    )

    val codeResult = codingActor.answer(
        input = listOf("Write a function to calculate the factorial of a number."),
        api = chatClient
    )

    println("Generated Code:\n${codeResult}\n")
    println("Execution Result: ${codeResult.result.resultValue}")
    println("Execution Output: ${codeResult.result.resultOutput}")
}
```

#### 3. **Image Generation**

Use `ImageActor` to create images based on textual descriptions.

```kotlin
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices

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

#### 4. **Text-to-Speech Conversion**

Convert AI-generated text into an audio file using `TextToSpeechActor`.

```kotlin
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.core.actors.TextToSpeechActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices

fun textToSpeechExample() {
    val ttsActor = TextToSpeechActor(
        name = "AudioAssistant",
        audioModel = AudioModels.TTS_HD,
        voice = "alloy",
        speed = 1.0,
        models = ChatModel.GPT4Turbo
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

## Testing

The SkyeNet Core Module includes comprehensive test suites to ensure reliability and correctness.

### Running Tests

1. **Configure a Testing Environment**

   Ensure that the testing environment has access to necessary resources like mock services or test databases.

2. **Execute Test Suites**

   For Gradle:

   ```bash
   ./gradlew test
   ```

   For Maven:

   ```bash
   mvn test
   ```

3. **Review Test Results**

   Test reports are generated in the `build/reports/tests` directory for Gradle and the `target/surefire-reports` directory for Maven.

### Test Coverage

The module covers various aspects, including:

- **Usage Management**: Ensures accurate tracking and summarization of usage metrics.
- **Data Storage**: Validates the storage and retrieval of session data and messages.
- **Authentication & Authorization**: Tests secure access control mechanisms.
- **Interpreter Validation**: Checks the correctness of code translation and execution.

## Contributing

Contributions are welcome! To contribute to the SkyeNet Core Module:

1. **Fork the Repository**

2. **Create a Feature Branch**

   ```bash
   git checkout -b feature/YourFeatureName
   ```

3. **Commit Your Changes**

   ```bash
   git commit -m "Add your feature"
   ```

4. **Push to the Branch**

   ```bash
   git push origin feature/YourFeatureName
   ```

5. **Create a Pull Request**

   Provide a detailed description of your changes and ensure all tests pass.

### Coding Standards

- Follow Kotlin coding conventions.
- Write clear and concise documentation for new components.
- Ensure all tests pass before submitting a pull request.

### Reporting Issues

If you encounter any issues or have suggestions for improvements:

- Open an issue on the [GitHub repository](https://github.com/your-repo/skyenet-core/issues).

## License

This project is licensed under the [MIT License](LICENSE).

---

*For further assistance or inquiries, please contact the development team at [dev@skyenet.com](mailto:dev@skyenet.com).*