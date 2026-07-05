---
specifies: ../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/FrontmatterOrchestrationMode.kt
related:
  - ./frontmatter_schema.md
  - ./cognitive_modes.md
  - ./cognitive_mode_best_practices.md
---

# Frontmatter Orchestration Mode

## Overview

The **Frontmatter Orchestration Mode** is a cognitive mode that generates documentation-driven specifications rather
than direct code artifacts. Instead of producing a task graph that directly modifies files, this mode produces a set of
**frontmatter specification documents** that describe the relationships between documentation and code. These
specifications are then processed by the `DocProcessor` to generate or update the final artifacts.

This approach introduces a layer of indirection that provides several benefits:

1. **Declarative Intent**: The AI expresses *what* should exist and *how* files relate, not the imperative steps to
   create them.
2. **Reproducibility**: The generated frontmatter specs can be version-controlled, reviewed, and re-executed
   independently.
3. **Separation of Concerns**: Planning (what to create) is separated from execution (how to create it).
4. **Incremental Updates**: The `DocProcessor`'s overwrite modes (`Patch`, `PatchIfOlder`) enable intelligent
   incremental updates.

## Conceptual Model

### Traditional Waterfall Mode

```
User Request → Plan (Task Graph) → Execute Tasks → Modified Files
```

In Waterfall mode, the AI generates a direct plan of `TaskExecutionConfig` objects that are executed sequentially or in
dependency order. Each task directly modifies files.

### Frontmatter Orchestration Mode

```
User Request → Plan (Frontmatter Specs) → Write Spec Documents → DocProcessor → Modified Files
```

In Frontmatter Orchestration mode:

1. The AI analyzes the user request and generates a set of **markdown specification documents** with frontmatter.
2. These documents are written to a designated specs directory (e.g., `.specs/` or `docs/specs/`).
3. The `DocProcessor` is invoked to process these specifications.
4. The `DocProcessor` handles file creation, updates, and dependency ordering automatically.

## Architecture

### Phase 1: Specification Generation

The AI generates markdown documents that use the frontmatter schema defined in `frontmatter_schema.md`. Each document
represents a "file operator" that describes:

- **What file(s) to create or update** (`specifies`)
- **What source files to use as context** (`documents`, `related`)
- **What transformations to apply** (`transforms`)
- **What to generate from inputs** (`generates`)
- **How to handle existing files** (`overwrite`)
- **What task type to use** (`task_type`)

#### Example Generated Specification

```markdown
---
specifies: ../src/api/UserService.kt
related:
  - ../src/models/User.kt
  - ../src/config/DatabaseConfig.kt
overwrite: Patch
task_type: FileModification
---

# UserService Implementation

## Purpose

The `UserService` class provides CRUD operations for user management.

## Requirements

1. Implement `createUser(user: User): User` that persists a new user
2. Implement `getUserById(id: Long): User?` that retrieves a user by ID
3. Implement `updateUser(user: User): User` that updates an existing user
4. Implement `deleteUser(id: Long): Boolean` that removes a user

## Dependencies

- Use the `User` model from `models/User.kt`
- Use database configuration from `config/DatabaseConfig.kt`

## Error Handling

- Throw `UserNotFoundException` when a user is not found
- Throw `DuplicateUserException` when creating a user with an existing email
```

### Phase 2: Specification Writing

The generated specifications are written to the file system in a structured manner:

```
.specs/
├── api/
│   ├── UserService.spec.md
│   ├── AuthService.spec.md
│   └── OrderService.spec.md
├── models/
│   ├── User.spec.md
│   └── Order.spec.md
└── config/
    └── DatabaseConfig.spec.md
```

### Phase 3: DocProcessor Execution

The `DocProcessor` is invoked with the specs directory:

```kotlin
val processor = DocProcessor(
    root = projectRoot,
    docsFolder = specsFolder,
    overwriteMode = OverwriteModes.PatchToUpdate,
    concurrencyLimit = 4,
    fastModel = config.defaultFast,
    smartModel = config.defaultSmart
)
processor.run()
```

The `DocProcessor` handles:

1. **Dependency Resolution**: Sorts specifications so dependencies are processed first
2. **Glob Expansion**: Resolves patterns like `../src/**/*.kt`
3. **Overwrite Logic**: Applies the appropriate update strategy
4. **Concurrent Execution**: Processes independent specifications in parallel

## Advantages Over Direct Waterfall

### 1. Reviewable Intermediate Artifacts

The generated specifications are human-readable markdown documents that can be:

- Reviewed before execution
- Modified by humans to adjust requirements
- Version-controlled alongside the codebase
- Used as documentation after the fact

### 2. Idempotent Execution

Because the `DocProcessor` uses file modification times and overwrite modes, re-running the same specifications produces
consistent results. This enables:

- Safe re-execution after failures
- Incremental updates when specifications change
- CI/CD integration for specification-driven development

### 3. Separation of Planning and Execution

The AI's planning phase is decoupled from execution:

- **Planning errors** are caught when reviewing specifications
- **Execution errors** are isolated to the `DocProcessor` phase
- **Partial execution** is possible by processing a subset of specifications

### 4. Composable Specifications

Specifications can reference each other through the `related` field, enabling:

- Shared context across multiple targets
- Layered specifications (base + extensions)
- Cross-cutting concerns (e.g., logging, error handling)

### 5. Transform Pipelines

The `transforms` frontmatter key enables powerful file transformation pipelines:

```yaml
---
transforms:
  - src/legacy/(.+)\.java -> src/modern/$1.kt
---

# Java to Kotlin Migration

Convert all legacy Java files to modern Kotlin, following these guidelines...
```

## Configuration

### FrontmatterOrchestrationConfig

```kotlin
class FrontmatterOrchestrationConfig(
    var specsDirectory: String = ".specs",
    var autoExecute: Boolean = true,
    var defaultOverwriteMode: OverwriteModes = OverwriteModes.PatchToUpdate,
    var specFileExtension: String = ".spec.md",
    var preserveSpecs: Boolean = true,
    var maxSpecsPerPlan: Int = 20
) : CognitiveModeConfig(type = CognitiveModeType.FrontmatterOrchestration)
```

| Field                  | Description                                                        |
|------------------------|--------------------------------------------------------------------|
| `specsDirectory`       | Directory where specification files are written                    |
| `autoExecute`          | Whether to automatically run `DocProcessor` after generating specs |
| `defaultOverwriteMode` | Default overwrite mode for generated specifications                |
| `specFileExtension`    | File extension for specification documents                         |
| `preserveSpecs`        | Whether to keep specification files after execution                |
| `maxSpecsPerPlan`      | Maximum number of specifications to generate in a single plan      |

## Workflow

### Interactive Mode (`autoFix = false`)

1. User submits a request
2. AI generates specification documents
3. Specifications are displayed for review in a tabbed interface:
   - **Specs**: List of generated specification files
   - **Diagram**: Mermaid diagram showing file relationships
   - **JSON**: Raw specification data
4. User can:
   - **Approve**: Execute the specifications
   - **Revise**: Request changes to the specifications
   - **Edit**: Manually modify individual specifications
5. Upon approval, `DocProcessor` executes the specifications
6. Results are displayed with links to modified files

### Automatic Mode (`autoFix = true`)

1. User submits a request
2. AI generates specification documents
3. Specifications are written to the specs directory
4. `DocProcessor` executes immediately
5. Results are displayed with links to modified files

## Specification Generation Strategy

### Prompt Engineering

The AI is prompted to think in terms of **file operators** rather than **file contents**:

```
You are a software architect. Given the user's request, generate a set of
specification documents that describe the files to be created or modified.

Each specification should:
1. Use YAML frontmatter to declare the target file(s) and relationships
2. Contain a clear description of what the file should contain
3. Include requirements, constraints, and examples
4. Reference related files that provide context

Do NOT generate the actual file contents. Generate specifications that
describe what the files should contain.
```

### Decomposition Heuristics

The AI should decompose requests into specifications following these guidelines:

1. **One specification per logical unit**: A service, model, or configuration
2. **Explicit dependencies**: Use `related` to declare dependencies
3. **Appropriate granularity**: Not too fine (one spec per function) or too coarse (one spec for entire project)
4. **Clear boundaries**: Each specification should have a single responsibility

## Error Handling

### Specification Generation Errors

If the AI generates invalid frontmatter:

1. Parse errors are caught during the writing phase
2. Invalid specifications are reported to the user
3. The user can fix the specification manually or request regeneration

### DocProcessor Execution Errors

If the `DocProcessor` fails:

1. Errors are logged with the specific specification that failed
2. Successfully processed specifications are preserved
3. Failed specifications can be retried individually
4. The transcript includes detailed error information

### Dependency Cycle Detection

The `DocProcessor` detects dependency cycles and breaks them automatically:

1. Cycles are logged as warnings
2. One specification in the cycle is processed to break the deadlock
3. The user is notified of the cycle for potential manual resolution

## Integration with Other Modes

### Sub-Planning

Frontmatter Orchestration can be used as a sub-planning strategy within other modes:

- **Hierarchical Mode**: Generate specifications for each sub-goal
- **Adaptive Mode**: Generate specifications as the plan evolves
- **Council Mode**: Have council members vote on specification quality

### Hybrid Execution

Some tasks may be better suited for direct execution:

- **Shell commands**: Run directly, not via specification
- **Web searches**: Execute immediately for context gathering
- **File reads**: Execute immediately for planning context

The mode can mix specification generation with direct task execution:

```
1. Execute WebSearch to gather context
2. Generate specifications based on search results
3. Execute DocProcessor to create files
4. Execute RunShellCommand to run tests
```

## Comparison with Related Modes

| Aspect        | Waterfall             | Frontmatter Orchestration    |
|---------------|-----------------------|------------------------------|
| Output        | Task graph            | Specification documents      |
| Execution     | Direct task execution | DocProcessor                 |
| Reviewability | JSON plan             | Markdown specifications      |
| Reusability   | Single execution      | Re-executable specifications |
| Granularity   | Task-level            | File-level                   |
| Dependencies  | Explicit in plan      | Implicit via frontmatter     |

## Future Enhancements

### 1. Specification Templates

Pre-defined templates for common patterns:

- REST API endpoint
- Database model
- Unit test suite
- Configuration file

### 2. Specification Validation

Schema validation for generated specifications:

- Required fields present
- Valid glob patterns
- Resolvable file paths
- Consistent dependencies

### 3. Specification Versioning

Track specification changes over time:

- Diff between specification versions
- Rollback to previous specifications
- Audit trail of changes

### 4. Collaborative Editing

Multi-user specification editing:

- Real-time collaboration on specifications
- Comment and review workflow
- Approval gates before execution

## Conclusion

The Frontmatter Orchestration Mode represents a shift from imperative task execution to declarative specification. By
generating file operators (frontmatter specifications) rather than direct file modifications, this mode provides:

- **Transparency**: Clear, reviewable intermediate artifacts
- **Reproducibility**: Idempotent, re-executable specifications
- **Flexibility**: Composable, modifiable specifications
- **Robustness**: Separation of planning and execution concerns

This approach aligns with documentation-driven development practices and leverages the existing `DocProcessor`
infrastructure for reliable file generation and updates.
