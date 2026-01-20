# Configuration System

The `com.simiacryptus.cognotik.config` package manages the persistence and user interface for all plugin settings. It provides a robust framework for handling API keys, model selections, tool paths, and advanced system configurations.

## Key Components

### [AppSettingsState.kt](./AppSettingsState.kt)
The central data model for the plugin's configuration. It uses IntelliJ's `PersistentStateComponent` to save settings in `SdkSettingsPlugin.xml`.
- **Model Settings**: Configuration for Smart, Fast, Image, and Embedding models.
- **Audio Settings**: Parameters for voice interaction (sample rate, silence detection, etc.).
- **AWS Settings**: Credentials and region info for AWS-based services.
- **System Configuration**: Port settings, shell commands, and developer mode toggles.
- **MRU History**: Stores recently used commands and arguments.

### [StaticAppSettingsConfigurable.kt](./StaticAppSettingsConfigurable.kt)
The primary UI provider for the settings dialog. It organizes settings into several tabs:
- **Basic Settings**: Model selection and temperature control.
- **Keys**: Management of API providers (OpenAI, Anthropic, etc.) and their keys.
- **Tools**: Configuration of external executable paths.
- **Advanced Settings**: Server endpoints, shell configuration, and logging toggles.
- **AWS**: AWS-specific profile and bucket settings.
- **Import/Export**: Allows users to share or backup their entire configuration via JSON.

### [AppSettingsComponent.kt](./AppSettingsComponent.kt)
The Swing-based UI component that defines the layout of the settings forms. It includes specialized editors for API and Tool tables, including auto-detection features for common tools.

### [UsageTable.kt](./UsageTable.kt)
A specialized component that displays a summary of token usage and estimated costs across different models. It also includes a feedback mechanism that triggers after significant usage (1,000,000 tokens).

### [MRUItems.kt](./MRUItems.kt)
A thread-safe helper class for managing "Most Recently Used" lists, used for command history and instruction suggestions.

## Configuration Categories

### API Management
The system supports multiple API providers. Each configuration includes:
- **Provider Type**: (e.g., OpenAI, Anthropic, Google, etc.)
- **Name**: A unique identifier for the specific API instance.
- **Key**: Encrypted storage of API credentials.
- **Base URL**: Customizable endpoint for proxy or private deployments.

### Tool Management
Allows the AI to interact with the local system by defining paths to executables. Includes an "Auto-Detect" feature to find common tools in the system PATH.

### Model Selection
Users can independently configure models for different tasks:
- **Smart Model**: High-reasoning models for complex tasks.
- **Fast Model**: Low-latency models for simple edits or summaries.
- **Image Model**: Models for vision and image generation tasks.
- **Embedding Model**: Used for vector search and context retrieval.

## Implementation Details

- **Persistence**: Settings are serialized to JSON and stored within the IntelliJ platform's XML storage.
- **Security**: API keys are encrypted before being stored on disk using `com.simiacryptus.cognotik.util.encrypt`.
- **Reflection-based UI**: Uses `UITools` to automatically map `AppSettingsState` properties to UI components in `AppSettingsComponent` based on naming conventions and the `@Name` annotation.