## `com.simiacryptus.aicoder`

This package contains the core components of the AI Coding Assistant plugin. It includes the main plugin startup
activity and server functionality.

- `PluginStartupActivity`: Initializes the plugin when IntelliJ starts, sets up logging, configures services, and shows
  the welcome screen.
- `AppServer`: Provides a web server that hosts the chat interface and API endpoints for the AI coding assistant.

## `com.simiacryptus.aicoder.config`

This package handles all configuration and settings for the plugin.

- `AppSettingsState`: The central configuration class that stores all plugin settings and persists them across IDE
  restarts.
- `AppSettingsComponent`: UI component for displaying and editing plugin settings.
- `AppSettingsConfigurable`: Connects the settings UI to the settings state.
- `MRUItems`: Manages most recently used items for various features.
- `UsageTable`: Displays token usage statistics for API calls.

## `com.simiacryptus.aicoder.ui`

This package contains UI components specific to the plugin.

- `SettingsWidgetFactory`: Creates a status bar widget that provides quick access to plugin settings and model
  selection.

## `com.simiacryptus.aicoder.util`

This package provides utility classes used throughout the plugin.

- `UITools`: Contains various UI helper methods for dialogs, notifications, and UI manipulation.
- `BgTask` and `ModalTask`: Background task execution utilities for running operations without blocking the UI.
- `PsiUtil`: Utilities for working with IntelliJ's Program Structure Interface (PSI).
- `IdeaChatClient`: A wrapper around the OpenAI API client customized for IntelliJ.
- `ComputerLanguage`: Enum representing different programming languages with their comment styles and file extensions.
- `BrowseUtil`: Utilities for opening URLs in the browser.
- `LanguageUtils`: Utilities for detecting and working with programming languages.

## `com.simiacryptus.aicoder.util.psi`

This package contains utilities for working with IntelliJ's Program Structure Interface (PSI).

- `PsiUtil`: Core utility methods for working with PSI elements.
- `PsiClassContext`: Provides context information about classes in the PSI.
- `PsiVisitorBase`: Base class for PSI visitors.

## `com.simiacryptus.aicoder.dictation`

This package implements speech-to-text functionality for the plugin.

- `DictationState`: Manages the state of the dictation feature.
- `DictationWidgetFactory`: Creates a status bar widget for controlling dictation.
- `DictationToolWindowFactory`: Creates a tool window for dictation settings and controls.
- `ControlPanel`: UI panel for controlling dictation settings.
- `SettingsPanel`: UI panel for advanced dictation settings.
- `EventPanel`: UI panel for displaying dictation events and debug information.

## `aicoder.actions`

This package contains action classes that implement various AI-assisted coding features.

- `SessionProxyServer`: Manages chat sessions between the IDE and the AI service.

## `aicoder.actions.agent`

This package implements agent-based AI features.

- Contains classes for implementing AI agents that can perform complex coding tasks.

## `aicoder.actions.plan`

This package implements planning-based AI features.

- Contains classes for implementing AI planning capabilities for larger coding tasks.

## `icons`

This package contains icon resources used by the plugin.

- `MyIcons`: Defines and loads icons used throughout the plugin UI.
