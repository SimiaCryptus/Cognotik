---
documents: ../webui/src/main/kotlin/com/simiacryptus/cognotik/embed/*
specifies: ../site/cognotik.com/build-tools.html
---

# Build Tools

The Cognotik build tools provide utilities for automated code generation, documentation processing, and task execution.
These tools enable AI-assisted development workflows that can be embedded into build processes or run standalone.

## Overview

The build tools package (`com.simiacryptus.cognotik.embed`) contains several key components:

- **DocProcessor** - Processes markdown documentation files to generate or update code files
- **FileGenerator** - Generates files based on templates and AI assistance
- **ExceptionFixer** - Automatically fixes code based on exception stack traces
- **PlanHarness** - Executes complex multi-step AI plans
- **TaskHarness** - Executes single AI tasks
- **UnifiedHarness** - Core infrastructure for running AI-assisted operations

## DocProcessor

The `DocProcessor` processes markdown documentation files that specify target files via YAML frontmatter. It supports
bidirectional documentation-code synchronization.

### Frontmatter Keys

| Key          | Description                                                                               |
|--------------|-------------------------------------------------------------------------------------------|
| `specifies`  | Glob pattern(s) for files that should be generated/updated based on this documentation    |
| `documents`  | Glob pattern(s) for source files that this documentation describes (reverse of specifies) |
| `transforms` | Pattern-based file transformations using regex with capture groups                        |
| `generates`  | Non-pattern-based generation with explicit output and input files                         |
| `related`    | Additional files to include as context                                                    |

### Usage Examples

#### Basic Specification

```yaml
---
specifies: ../src/main/kotlin/MyClass.kt
---
# MyClass Documentation
This class should implement...
```

#### Documentation Mode

```yaml
---
documents: ../src/main/kotlin/com/example/**/*.kt
specifies: ../site/api-docs.html
---
# API Documentation
This documentation is generated from the source files.
```

#### Transform Mode

```yaml
---
transforms:
  - "src/(.+)\\.kt -> generated/$1.md"
---
```

#### Generate Mode

```yaml
---
generates:
  output: ../generated/summary.md
  inputs:
    - ../src/**/*.kt
    - ../docs/*.md
---
```

### Overwrite Modes

The `OverwriteModes` enum controls how existing files are handled:
| Mode | Behavior |
|------|----------|
| `SkipExisting` | Never modify existing files |
| `OverwriteExisting` | Always replace existing files |
| `OverwriteToUpdate` | Replace only if source is newer |
| `PatchExisting` | Apply incremental patches to existing files |
| `PatchToUpdate` | Apply patches only if source is newer |

### Programmatic Usage

```kotlin
val processor = DocProcessor(
  root = File("/project"),
  docsFolder = File("/project/docs"),
  overwriteMode = OverwriteModes.PatchToUpdate,
  concurrencyLimit = 4
)
processor.run()
```

## FileGenerator

The `FileGenerator` provides a flexible framework for generating files based on source files and AI assistance.

### Usage

```kotlin
FileGenerator().run(
  root = projectRoot,
  folder = sourceFolder,
  listFiles = { root, folder ->
    folder.listFilesRecursively()
      .filter { it.extension == "kt" }
  },
  targetFile = { source ->
    File(source.parent, "${source.nameWithoutExtension}Test.kt")
  },
  overwriteMode = OverwriteModes.SkipExisting,
  relatedFiles = { source -> listOf(source.path) },
  generationPrompt = { source, target ->
    "Generate unit tests for ${source.name}"
  }
)
```

## ExceptionFixer

The `ExceptionFixer` analyzes exception stack traces and automatically generates fixes for the relevant source files.

### Usage

```kotlin
val fixer = ExceptionFixer(
  projectRoot = File("/project"),
  related_files = listOf("src/main/kotlin/Config.kt")
)
try {
  // Code that might throw
} catch (e: Exception) {
  fixer.fix(e)
}
```

### Features

- Automatically identifies source files from stack traces
- Searches for Kotlin source files in standard Maven/Gradle layouts
- Includes related files as context for better fixes
- Uses fuzzy patching for safe modifications

## PlanHarness

The `PlanHarness` executes complex multi-step AI plans using cognitive modes.

### Configuration

```kotlin
val harness = PlanHarness(
  prompt = "Create a REST API for user management",
  cognitiveSettings = CognitiveModeConfig(
    type = CognitiveModeType.TaskPlanning
  ),
  fastModel = GeminiModels.GeminiFlash_30_Preview,
  smartModel = GeminiModels.GeminiFlash_30_Preview,
  workspace = File("/project"),
  timeoutMinutes = 30,
  openBrowser = false,
  serverless = true
)
harness.run()
```

### Options

| Parameter           | Description                       | Default                         |
|---------------------|-----------------------------------|---------------------------------|
| `prompt`            | The task description for the AI   | Required                        |
| `cognitiveSettings` | Cognitive mode configuration      | Required                        |
| `fastModel`         | Model for quick operations        | GeminiFlash_30_Preview          |
| `smartModel`        | Model for complex reasoning       | GeminiFlash_30_Preview          |
| `imageModel`        | Model for image generation        | GeminiFlash_25_Image_Generation |
| `workspace`         | Working directory                 | Auto-generated                  |
| `timeoutMinutes`    | Maximum execution time            | 30                              |
| `serverless`        | Run without web server            | true                            |
| `openBrowser`       | Open browser for interactive mode | false                           |

## TaskHarness

The `TaskHarness` executes single AI tasks with specific configurations.

### Usage

```kotlin
val harness = TaskHarness(
  taskType = FileModification,
  typeConfig = TaskTypeConfig(task_type = "FileModification"),
  executionConfig = FileModificationTaskExecutionConfigData(
    files = listOf("src/main/kotlin/MyClass.kt"),
    task_description = "Add logging to all public methods"
  ),
  workspace = File("/project"),
  timeoutMinutes = 5
)
harness.run()
```

## UnifiedHarness

The `UnifiedHarness` provides the core infrastructure for running AI-assisted operations. It can operate in two modes:

### Server Mode

Starts a Jetty web server for interactive sessions:

```kotlin
val harness = UnifiedHarness(
  port = 8080,
  serverless = false,
  openBrowser = true
)
harness.start()
// ... run tasks
harness.stop()
```

### Serverless Mode

Runs without a web server for automated/embedded use:

```kotlin
val harness = UnifiedHarness(
  serverless = true
)
// No start() needed
harness.runTask(...)
```

### Helper Function

The `withHarness` function provides a convenient way to run operations:

```kotlin
withHarness(
  root = projectRoot,
  testName = "MyOperation",
  fastModel = GeminiModels.GeminiFlash_30_Preview,
  smartModel = GeminiModels.GeminiFlash_30_Preview
) { harness ->
  harness.runTask(...)
}
```

## Dependency Sorting

The `DocProcessor` includes intelligent dependency sorting using topological sort with cycle detection. When processing
multiple files, it ensures that dependencies are processed before their dependents. If cycles are detected, they are
broken gracefully to allow progress.

## Integration with Build Systems

### Gradle Integration

```kotlin
tasks.register("generateDocs") {
  doLast {
    DocProcessor(
      root = projectDir,
      docsFolder = file("docs"),
      overwriteMode = OverwriteModes.PatchToUpdate
    ).run()
  }
}
```

### Maven Integration

Use the `exec-maven-plugin` to run the tools:

```xml

<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <mainClass>com.simiacryptus.cognotik.util.DocProcessorKt</mainClass>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Error Handling

Both `PlanHarness` and `TaskHarness` support custom error handlers:

```kotlin
PlanHarness.fix = { e ->
  // Custom error handling
  ExceptionFixer(projectRoot).fix(e)
}
TaskHarness.fix = { e ->
  // Custom error handling
  log.error("Task failed", e)
}
```
