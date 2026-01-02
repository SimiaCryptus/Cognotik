# Embedding Cognotik: A Guide for Automated Agentic Coding

This guide details how to use the **Cognotik** library (`com.cognotik:webapp`) as an embedded engine. By importing Cognotik directly into your build process (Gradle plugins, CLI tools, or GitHub Actions), you can leverage "Headless" AI agents to perform complex coding tasks, refactoring, or documentation generation without a user interface.

## 1. Installation

Add the dependency to your `build.gradle.kts`.

```kotlin
repositories {
    mavenCentral()
    // Ensure you have the repository hosting Cognotik artifacts if not on Central
}

dependencies {
    // The core webapp library contains the Harness and Planning engines
    implementation("com.cognotik:webapp:2.0.39")

    // You may need SLF4J for logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
}
```

## 2. The Core Concept: `UnifiedHarness`

The entry point for embedded execution is the `UnifiedHarness` class (found in `com.simiacryptus.cognotik.apps.general`). It wraps the complex server infrastructure, allowing you to run agents in a **Serverless** (headless) mode.

### Initialization

To run in a CI/CD or script environment, you must instantiate the harness with `serverless = true`. You must also inject your API keys programmatically, as there is no UI to configure them.

```kotlin
import com.simiacryptus.cognotik.apps.general.UnifiedHarness
import com.simiacryptus.cognotik.chat.model.OpenAIModels

val harness = UnifiedHarness(
    serverless = true,   // CRITICAL: Disables Jetty/Websockets for CLI usage
    openBrowser = false, // CRITICAL: Prevents trying to launch a browser window

    // Define the models you want to use
    smartModel = OpenAIModels.GPT4o,
    fastModel = OpenAIModels.GPT35Turbo,

    // Inject API Keys from Environment Variables
    modelInstanceFn = { apiChatModel ->
        val provider = apiChatModel.provider
        val model = apiChatModel.model!!

        // Fetch key based on provider (OpenAI, Anthropic, etc.)
        val apiKey = System.getenv("OPENAI_API_KEY")
            ?: throw RuntimeException("Missing OPENAI_API_KEY env var")

        model.instance(key = apiKey)
    }
)

// Initialize platform services (loads Task definitions, etc.)
harness.start()
```

---

## 3. Scenario A: Full Agent Planning (The "Manager")

Use this approach when you have a high-level goal (e.g., "Refactor the database layer") and want the AI to figure out the steps, break them down, and execute them.

### Usage: `runPlan`

This method spins up an orchestrator that creates a plan (Waterfall, Agile, etc.) and executes it.

```kotlin
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import java.io.File

fun runAgenticRefactor(projectDir: File, instruction: String) {

    // 1. Configure the Strategy
    val strategy = CognitiveModeConfig(
        type = CognitiveModeType.Waterfall, // or Auto_Plan, AdaptivePlanning
        name = "RefactorAgent"
    )

    // 2. Execute the Plan
    harness.runPlan(
        prompt = instruction,
        cognitiveSettings = strategy,
        workspace = projectDir, // The agent will read/write files here
        timeoutMinutes = 60,
        autoFix = true // Allow agent to fix its own errors without prompting
    )

    println("Agent execution complete. Check results.md in the workspace.")
}

// Example Call
runAgenticRefactor(
    File("./my-project"),
    "Analyze the User class and convert all public fields to private with getters/setters."
)
```

### Key Configuration Options
*   **`workspace`**: If `null`, it creates a temp dir. For CI/CD, pass `File(".")` to modify the current repository.
*   **`autoFix`**: Set to `true` for unattended execution. If `false`, the agent might hang waiting for user confirmation (which never comes in headless mode).
*   **`cognitiveSettings`**:
    *   `Waterfall`: Plans everything first, then executes. Good for predictable tasks.
    *   `AdaptivePlanning`: Loops through Think/Act cycles. Good for research or debugging.

---

## 4. Scenario B: Single Task Execution (The "Tool")

Use this approach when you want to use a specific Cognotik tool (like the Crawler or File Modifier) as a function call within your own code, skipping the high-level planning.

### Usage: `runTask`

This executes a specific `TaskType` in isolation.

#### Example: AI-Powered File Modification
This is useful for writing a Gradle task that automatically generates boilerplate code or documentation.

```kotlin
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTaskTypeConfig

fun generateReadme(projectDir: File) {

    // 1. Define Static Configuration (The "Tool" settings)
    val typeConfig = FileModificationTaskTypeConfig(
        name = "ReadmeGenerator"
        // You can override specific models here if needed
    )

    // 2. Define Runtime Input (The "Job" settings)
    val executionConfig = FileModificationTaskExecutionConfigData(
        files = listOf("README.md"),
        modifications = "Read the source code in src/main and generate a comprehensive README.md describing the architecture.",
        extractContent = true, // Allow AI to read the file content
        task_description = "Generate Documentation"
    )

    // 3. Run
    harness.runTask(
        taskType = TaskType.FileModificationTask,
        typeConfig = typeConfig,
        executionConfig = executionConfig,
        workspace = projectDir,
        autoFix = true
    )
}
```

#### Example: Self-Healing Command Execution
Useful for CI pipelines. Run a build; if it fails, the AI attempts to fix the code and re-run it.

```kotlin
import com.simiacryptus.cognotik.plan.tools.code.SelfHealingTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.code.CommandWithWorkingDir

fun runSelfHealingBuild(projectDir: File) {

    val buildConfig = SelfHealingTaskExecutionConfigData(
        commands = listOf(
            CommandWithWorkingDir(
                command = listOf("./gradlew", "build"),
                workingDir = "."
            )
        ),
        task_description = "Build project and fix compilation errors"
    )

    // Note: You need the specific TypeConfig for SelfHealing
    // harness.runTask(TaskType.SelfHealing, ..., buildConfig, ...)
}
```

---

## 5. Advanced Configuration (`OrchestrationConfig`)

When running `runPlan`, you can inject a custom configuration lambda to control budget and safety limits.

```kotlin
harness.runPlan(
    prompt = "...",
    cognitiveSettings = ...,
    config = { session, workspace ->
        OrchestrationConfig(
            sessionId = session.sessionId,
            workingDir = workspace.absolutePath,

            // Model Selection
            defaultSmartModel = OpenAIModels.GPT4o.asApiChatModel(),
            defaultFastModel = OpenAIModels.GPT35Turbo.asApiChatModel(),

            // Safety Limits
            budget = 2.00,          // Max $2.00 USD spend
            maxIterations = 15,     // Max planning loops
            maxTasksPerIteration = 3,

            // Behavior
            autoFix = true,
            temperature = 0.1       // Low temperature for deterministic code
        )
    }
)
```

---

## 6. Integration Examples

### A. As a Gradle Plugin
You can wrap the harness in a custom Gradle Task to add AI capabilities to your build.

```kotlin
// buildSrc/src/main/kotlin/AiRefactorTask.kt
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import com.simiacryptus.cognotik.apps.general.UnifiedHarness
// ... imports ...

abstract class AiRefactorTask : DefaultTask() {
    @get:Input
    abstract var instruction: String

    @TaskAction
    fun run() {
        val harness = UnifiedHarness(serverless = true, /* config */)
        harness.start()

        harness.runPlan(
            prompt = instruction,
            cognitiveSettings = CognitiveModeConfig(type = CognitiveModeType.Waterfall),
            workspace = project.projectDir
        )

        harness.stop()
    }
}
```

### B. As a GitHub Action (via Kotlin CLI)
1.  Create a simple Kotlin application using the code in Section 2 & 3.
2.  Build a "Fat JAR" (Shadow JAR).
3.  Create a `.github/workflows/ai-coder.yml`:

```yaml
name: AI Code Reviewer
on: [workflow_dispatch]

jobs:
  ai-fix:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup Java
        uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Run Cognotik Agent
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: |
          java -jar cognotik-cli.jar \
            --instruction "Review src/main for potential NPEs and fix them." \
            --workspace .

      - name: Create Pull Request
        uses: peter-evans/create-pull-request@v5
        with:
          title: "AI Automated Fixes"
```

## 7. Troubleshooting & Best Practices

1.  **Environment Variables:** Ensure `OPENAI_API_KEY` (or other provider keys) are available in the environment where the JAR runs. The `UnifiedHarness` does not load from local `.config` files when a custom `modelInstanceFn` is used.
2.  **Context Window:** If working on large codebases, ensure you select a model with a large context window (e.g., `gpt-4-turbo` or `claude-3-opus`) in the `UnifiedHarness` constructor.
3.  **Logging:** Cognotik uses SLF4J. Configure a simple logger (like `slf4j-simple`) to see the agent's "thought process" in your console logs.
4.  **Concurrency:** In `serverless` mode, the harness runs synchronously (blocking the thread until completion). This is usually desired for CI/CD.
5.  **Artifacts:** The agent writes a `results.md` and a `plan.json` in the workspace. Archive these in your CI pipeline to review what the agent did.
