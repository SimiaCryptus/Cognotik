# Planning and Orchestration Actions

This package contains the IntelliJ IDEA actions and UI components responsible for configuring and launching the **Unified Planning** system. This system allows users to orchestrate complex AI workflows by combining different task types and cognitive strategies.

## Actions

### `UnifiedPlanAction`
The primary entry point for the planning system. It:
1. Determines the working directory (project root or temporary).
2. Displays the `PlanConfigDialog` to gather orchestration settings.
3. Initializes a `UnifiedPlanApp` session.
4. Launches the web-based UI for execution.

### `UnifiedPlanFromMenuAction`
A specialized version of `UnifiedPlanAction` intended for the main menu, which always uses a temporary directory instead of the current project context.

## Configuration Dialogs

### `PlanConfigDialog`
The central hub for orchestration settings. It manages:
- **Saved Configurations**: Loading and saving named presets (e.g., "Last", or user-defined names).
- **Cognitive Modes**: Selecting the high-level strategy (e.g., Chat, Auto Plan, Waterfall).
- **Model Selection**: Assigning default models for smart reasoning, fast parsing, and image processing.
- **Task List**: Managing the sequence of tasks to be executed, including adding, editing, and deleting task configurations.
- **Import/Export**: Support for copying/pasting orchestration settings as JSON via the clipboard.

### `TaskConfigDialog`
Provides detailed configuration for individual tasks. It uses Kotlin reflection to dynamically generate UI fields based on the task's configuration class.
- Supports standard types (String, Int, Boolean, Enums, and `DynamicEnum`).
- Handles complex tasks like `SubPlanTask` which can contain their own nested task configurations.
- Includes validation for task-specific constraints (e.g., timeouts, retry counts, domain formats).

### `CognitiveConfigDialog`
A specialized dialog for fine-tuning the parameters of the selected cognitive mode. Like the task dialog, it is dynamically generated from the configuration properties of the specific `CognitiveModeConfig`.

### `TaskTypeSelectionDialog`
A searchable tree-based UI for selecting new task types to add to a plan. It categorizes tasks and provides rich HTML descriptions and tooltips for each available task type.

## Key Features

- **Dynamic UI Generation**: Most configuration fields are generated at runtime using reflection and the `@Description` annotation, ensuring the UI stays in sync with the underlying data models.
- **Sub-Planning**: Support for recursive task execution where a task can itself be a plan with its own set of sub-tasks and cognitive settings.
- **Model Flexibility**: Allows per-task model overrides or global defaults for different stages of the planning process.
- **Persistence**: Settings are integrated with `AppSettingsState` to persist configurations across IDE restarts.

## Implementation Details

- **UI Framework**: Built using the IntelliJ UI DSL for modern, consistent dialog layouts.
- **Serialization**: Uses JSON for exporting/importing configurations and for passing settings to the web-based execution environment.
- **Web Integration**: Orchestration settings are passed to the `UnifiedPlanApp` which runs in a local web server, allowing for a rich, interactive execution interface.