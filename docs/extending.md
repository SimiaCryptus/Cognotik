---
documents:
  - ../core/src/main/kotlin/com/simiacryptus/cognotik/util/DynamicEnum.kt
  - ../core/src/main/kotlin/com/simiacryptus/cognotik/models/APIProvider.kt
  - ../core/src/main/kotlin/com/simiacryptus/cognotik/models/ToolProvider.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/interpreter/CodeRuntimes.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveModeType.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveSchemaStrategy.kt
  - ../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/TaskType.kt
specifies: ../site/cognotik.com/extending.html
---

# Extending Cognotik

Cognotik provides a flexible extension system based on the `DynamicEnum` pattern, allowing you to add new providers,
tools, runtimes, cognitive modes, and task types without modifying the core codebase.

## DynamicEnum Pattern

The `DynamicEnum` class provides a runtime-extensible alternative to Java enums. It supports:

- **Registration**: New values can be registered at runtime using `DynamicEnum.register()`
- **Lookup**: Values can be retrieved by name using `valueOf()` or listed using `values()`
- **Serialization**: Built-in Jackson serializers and deserializers for JSON support

```kotlin
// Example: Creating a custom DynamicEnum
class MyExtension(name: String) : DynamicEnum<MyExtension>(name) {
  companion object {
    val Default = MyExtension("Default")

    init {
      register(MyExtension::class.java, Default)
    }

    fun values() = values(MyExtension::class.java)
    fun valueOf(name: String) = valueOf(MyExtension::class.java, name)
  }
}
```

## API Providers

API providers define connections to AI services. The `APIProvider` class supports:

### Built-in Providers

| Provider       | Base URL                                    | Features                                |
|----------------|---------------------------------------------|-----------------------------------------|
| **OpenAI**     | `https://api.openai.com/v1`                 | Chat, Embeddings, Images, Transcription |
| **Anthropic**  | `https://api.anthropic.com/v1`              | Chat (Claude models)                    |
| **Gemini**     | `https://generativelanguage.googleapis.com` | Chat, Images                            |
| **AWS**        | `https://api.openai.aws`                    | Chat (Bedrock models)                   |
| **Groq**       | `https://api.groq.com/openai/v1`            | Chat, Transcription                     |
| **Perplexity** | `https://api.perplexity.ai`                 | Chat                                    |
| **Mistral**    | `https://api.mistral.ai/v1`                 | Chat                                    |
| **DeepSeek**   | `https://api.deepseek.com`                  | Chat                                    |
| **ModelsLab**  | `https://modelslab.com/api/v6`              | Chat                                    |
| **Ollama**     | `http://localhost:11434`                    | Chat, Embeddings (local)                |
| **SearchAPI**  | `https://api.searchapi.com`                 | Search functionality                    |
| **Google**     | Search engine ID                            | Google Search                           |
| **Github**     | `https://api.github.com`                    | GitHub API                              |

### Creating a Custom Provider

```kotlin
val CustomProvider = object : APIProvider("CustomProvider", "https://api.custom.com") {
  override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> {
    // Return available chat models
    return listOf(/* your models */)
  }
  override fun getChatClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ): ChatClientInterface {
    return YourChatClient(key, base, workPool, scheduledPool)
  }

  // Optional: Override for custom authorization
  override fun authorize(request: HttpRequest, key: String, apiBase: String) {
    request.addHeader("X-Custom-Auth", key)
  }

  // Optional: Support embeddings
  override fun getEmbeddingModels(key: SecureString, baseUrl: String): List<EmbeddingModel> {
    return listOf(/* your embedding models */)
  }

  // Optional: Support image generation
  override fun getImageModels(key: SecureString, baseUrl: String): List<ImageModel> {
    return listOf(/* your image models */)
  }
}
// Register the provider
APIProvider.register(APIProvider::class.java, CustomProvider)
```

## Tool Providers

Tool providers define external executables that can be discovered and used by the system.

### Built-in Tool Providers

| Category                | Tools                                                                                                                                                                 |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Version Control**     | Git                                                                                                                                                                   |
| **Languages**           | Python, Node, Ruby, PHP, Go, Rust, Haskell, OCaml, Julia, Scala                                                                                                       |
| **JVM**                 | Jdk (java, javac, jdb, javap, jlink, jarsigner, javadoc, jshell, jcmd, jconsole, jstat, jmap, jhat, jinfo, jstack)                                                    |
| **Build Tools**         | Gradle, Maven, Ant, Make, Cmake                                                                                                                                       |
| **Shells**              | Bash, Zsh, Powershell                                                                                                                                                 |
| **Containers**          | Docker, Kubectl                                                                                                                                                       |
| **Cloud**               | Gcloud, Aws, Terraform                                                                                                                                                |
| **Document Processing** | Latex, Pandoc                                                                                                                                                         |
| **Media**               | Ffmpeg, Gnuplot, Dot (Graphviz)                                                                                                                                       |
| **Math/Science**        | Octave, Maxima, Sage, Gap, Singular, PariGP                                                                                                                           |
| **Theorem Provers**     | Z3, CVC5, Lean, Coq, Isabelle, Agda                                                                                                                                   |
| **Logic**               | Prolog                                                                                                                                                                |
| **Compilers**           | Gcc                                                                                                                                                                   |
| **Remote**              | SSH                                                                                                                                                                   |
| **Language Servers**    | pylsp, typescript-language-server, kotlin-language-server, jdtls, clangd, gopls, rust-analyzer, bash-language-server, docker-langserver, texlab, yaml-language-server |

### Creating a Custom Tool Provider

```kotlin
val CustomTool = object : ToolProvider("CustomTool") {
  override fun getExecutables() = listOf("custom-tool", "custom-tool.exe")
  override fun getVersion(path: String): String? {
    return runCommand(listOf(path, "--version"))
  }

  // Optional: Custom validation
  override fun validate(path: String): Boolean {
    ---


    return try {
      val version = getVersion(path)
      version != null && version.contains("CustomTool")
    } catch (e: Exception) {
      false
    }
  }
}
// Register the tool
ToolProvider.register(ToolProvider::class.java, CustomTool)
```

### Tool Discovery

```kotlin
// Discover all tools from PATH
val tools = ToolProvider.discoverAllToolsFromPath()
// Scan a directory recursively
val projectTools = ToolProvider.scanRecursive(File("/path/to/project"), depth = 3)
// Resolve a specific tool
val pythonPath = ToolProvider.Python.resolve("/usr/local")
```

## Code Runtimes

Code runtimes execute code in various languages.

### Built-in Runtimes

| Runtime               | Extension | Description                                         |
|-----------------------|-----------|-----------------------------------------------------|
| **KotlinRuntime**     | `.kts`    | Execute Kotlin code with full JVM access            |
| **GroovyRuntime**     | `.groovy` | Execute Groovy code with dynamic scripting          |
| **BashRuntime**       | `.sh`     | Execute Bash shell scripts (Unix/Linux/Mac)         |
| **PowerShellRuntime** | `.ps1`    | Execute PowerShell scripts (Windows/Cross-platform) |
| **CmdRuntime**        | `.bat`    | Execute Windows Command Prompt scripts              |
| **PythonRuntime**     | `.py`     | Execute Python scripts                              |
| **NodeJsRuntime**     | `.js`     | Execute Node.js JavaScript code                     |
| **RubyRuntime**       | `.rb`     | Execute Ruby scripts                                |
| **PerlRuntime**       | `.pl`     | Execute Perl scripts                                |
| **RRuntime**          | `.R`      | Execute R scripts                                   |
| **PhpRuntime**        | `.php`    | Execute PHP scripts                                 |
| **LuaRuntime**        | `.lua`    | Execute Lua scripts                                 |
| **GoRuntime**         | `.go`     | Execute Go code                                     |
| **RustRuntime**       | `.rs`     | Execute Rust code (via rust-script)                 |
| **ScalaRuntime**      | `.scala`  | Execute Scala scripts                               |

### Creating a Custom Runtime

```kotlin
val CustomRuntime = CodeRuntimes("CustomRuntime", "Execute custom scripts", "custom")
CodeRuntimes.registerConstructor(CustomRuntime) { defs ->
  ProcessCodeRuntime(
    timeoutMinutes = defs["timeoutMinutes"]?.toString()?.toLongOrNull() ?: 15L,
    workingDir = defs["workingDir"]?.toString()?.let { File(it) } ?: File("."),
    env = defs["env"]?.let { it as Map<String, String> },
    lang = "custom",
    command = listOf("custom-interpreter")
  )
}
```

### Using Runtimes

```kotlin
val runtime = CodeRuntimes.getRuntime(
  CodeRuntimes.PythonRuntime,
  mapOf(
    "timeoutMinutes" to 30L,
    "workingDir" to "/path/to/project"
  )
)
val result = runtime.execute("print('Hello, World!')")
```

## Cognitive Modes

Cognitive modes define different AI interaction patterns.

### Built-in Modes

| Mode             | Config Class               | Description                                       |
|------------------|----------------------------|---------------------------------------------------|
| **Chat**         | `ConversationalModeConfig` | Standard conversational interaction               |
| **Adaptive**     | `AdaptivePlanningConfig`   | Dynamic planning with cognitive schema strategies |
| **Waterfall**    | `WaterfallModeConfig`      | Sequential task execution                         |
| **Hierarchical** | `CognitiveModeConfig`      | Hierarchical task decomposition                   |
| **Parallel**     | `ParallelModeConfig`       | Concurrent task execution                         |
| **Protocol**     | `ProtocolModeConfig`       | Protocol-driven interaction                       |
| **Council**      | `CouncilModeConfig`        | Multi-agent deliberation                          |
| **PersonaChat**  | `PersonaChatConfig`        | Persona-based conversation                        |
| **Coding**       | `CodingModeConfig`         | Code-focused interaction                          |

### Creating a Custom Cognitive Mode

```kotlin
// 1. Define the config class
class CustomModeConfig : CognitiveModeConfig() {
  var customSetting: String = "default"
}
// 2. Define the mode type
val CustomMode = CognitiveModeType(
  "CustomMode",
  CustomModeConfig::class.java,
  inputCnt = 1
)

// 3. Implement the mode
class CustomCognitiveMode(
  config: OrchestrationConfig,
  session: Session,
  user: User
) : CognitiveMode<CustomModeConfig>(config, session, user) {
  override fun run(messages: List<String>): String {
    // Implement your cognitive mode logic
    return "Result"
  }
}
// 4. Register (done in CognitiveModeType companion object)
```

## Cognitive Schema Strategies

Schema strategies define how the adaptive planning mode maintains and updates its cognitive state.

### Built-in Strategies

| Strategy             | Description                                                |
|----------------------|------------------------------------------------------------|
| **ProjectManager**   | Standard goal-oriented planning with short/long-term goals |
| **ScientificMethod** | Hypothesis-driven investigation with evidence tracking     |
| **AgileDeveloper**   | Iterative TDD with user stories and acceptance criteria    |
| **CriticalAuditor**  | Security and logic validation with risk assessment         |
| **CreativeWriter**   | Narrative generation with theme and audience tracking      |

### Creating a Custom Strategy

```kotlin
// 1. Define your state class
data class CustomState(
  val objective: String? = null,
  val progress: MutableList<String>? = null,
  val metrics: MutableMap<String, Double>? = null
)

// 2. Implement the strategy
class CustomStrategy : CognitiveSchemaStrategy(
  "Custom Strategy",
  "Description of your strategy"
) {
  override fun initialize(
    userMessage: String,
    contextData: List<String>,
    orchestrationConfig: OrchestrationConfig,
    task: SessionTask,
    describer: TaskContextYamlDescriber
  ): Any {
    return ParsedAgent(
      name = "CustomInitializer",
      resultClass = CustomState::class.java,
      exampleInstance = CustomState(
        objective = "Example objective",
        progress = mutableListOf("Step 1"),
        metrics = mutableMapOf("completion" to 0.0)
      ),
      prompt = "Initialize the custom state based on the user request...",
      model = orchestrationConfig.defaultSmart.getChildClient(task),
      parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
      temperature = orchestrationConfig.temperature,
      describer = describer
    ).answer(listOf(userMessage) + contextData).obj
  }
  override fun update(
    currentState: Any,
    completedTasks: List<AdaptivePlanningMode.ExecutionRecord>,
    userMessage: String?,
    contextData: List<String>,
    orchestrationConfig: OrchestrationConfig,
    task: SessionTask,
    describer: TaskContextYamlDescriber
  ): Any {
    // Update logic
    return currentState
  }
  override fun formatState(state: Any): String {
    return JsonUtil.toJson(state)
  }
  override fun getTaskSelectionGuidance(state: Any): String {
    return "Select tasks based on current metrics and progress."
  }
}
// 3. Register the strategy
DynamicEnum.register(CognitiveSchemaStrategy::class.java, CustomStrategy())
```

## Task Types

Task types define discrete operations that can be executed within plans.

### Built-in Task Categories

| Category            | Tasks                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Reasoning**       | AbductiveReasoning, AbstractionLadder, AdversarialReasoning, AnalogicalReasoning, CausalInference, ChainOfThought, ConstraintRelaxation, ConstraintSatisfaction, CounterfactualAnalysis, DecisionTree, DecompositionSynthesis, DialecticalReasoning, EthicalReasoning, FiniteStateMachine, FunctorialMapping, GameTheory, GeneticOptimization, IsomorphismDiscovery, LateralThinking, MathematicalReasoning, MetaCognitiveReflection, MultiPerspectiveAnalysis, NeuralNetworkLayer, ProbabilisticReasoning, SocraticDialogue, StructuralInvariantAnalysis, SystemsThinking, TemporalReasoning |
| **Writing**         | ArticleGeneration, BusinessProposal, ComicBookGeneration, EmailCampaign, InteractiveStory, NarrativeGeneration, PersuasiveEssay, ReportGeneration, ResearchPaperGeneration, Scriptwriting, SoftwareDesignDocument, TechnicalExplanation, TutorialGeneration                                                                                                                                                                                                                                                                                                                                   |
| **File Operations** | FileAppend, FileModification, FileSearch, ReadDocuments, WriteHtml                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **Code Execution**  | AutoFix, LanguageServer, RunCode, RunTool, SingleFix, SymbolsDbCode                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Image**           | GenerateImage, GenerateQRImage, GenerateSpriteSheet, IllustrateDocument, ImageDecomposition, ImageTable, ImageVariation, SegmentedImageGeneration, TiledImageGeneration                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Data**            | DataIngest, DataTableCompilation, TableCompilation, OCR, PdfForm                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| **Online**          | CrawlerAgent, GitHubSearch, MCPTool                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| **Session**         | CommandSession, JdbcSession                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| **Social**          | Brainstorming, Discussion, JournalismReasoning, LLMExperiment, LLMPollSimulation, PoliticalOptimization                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| **Games**           | GameEconomy, GameLevelDesign, GameMechanicsDesign, GameNarrativeDesign                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| **Planning**        | SubPlan, IterativeGraphGeneration, GeneratePresentation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |

### Creating a Custom Task Type

```kotlin
// 1. Define the execution config
data class CustomTaskConfig(
  override var task_type: String? = CustomTask.name,
  override var task_description: String? = null,
  var customParameter: String? = null
) : TaskExecutionConfig()

// 2. Define the task settings
data class CustomTaskSettings(
  override var task_type: String? = CustomTask.name,
  override var enabled: Boolean = true,
  var defaultValue: String = "default"
) : TaskTypeConfig()

// 3. Implement the task
class CustomTaskImpl(
  settings: OrchestrationConfig,
  task: CustomTaskConfig?
) : AbstractTask<CustomTaskConfig, CustomTaskSettings>(settings, task) {
  override fun promptSegment(): String {
    return """
            CustomTask - Performs custom operations
            ** Specify the custom parameter
        """.trimIndent()
  }
  override fun run(
    agent: PlanCoordinator,
    messages: List<String>,
    task: SessionTask,
    api: API,
    resultFn: (String) -> Unit,
    api2: OpenAIClient,
    planSettings: PlanSettings
  ): TaskResult {
    // Implementation
    return TaskResult("Custom task completed")
  }

  companion object {
    val CustomTask = TaskType(
      name = "CustomTask",
      category = "Custom",
      taskClass = CustomTaskImpl::class.java,
      executionConfigClass = CustomTaskConfig::class.java,
      taskSettingsClass = CustomTaskSettings::class.java,
      description = "Performs custom operations"
    )
  }
}
// 4. Register (done via registerConstructor in TaskType companion object)
```

## Best Practices

1. **Use meaningful names**: Choose descriptive names for your extensions that clearly indicate their purpose.
2. **Provide documentation**: Include descriptions and tooltips where supported.
3. **Handle errors gracefully**: Implement proper error handling and validation.
4. **Follow existing patterns**: Study the built-in implementations for guidance.
5. **Register early**: Register your extensions during application initialization.
6. **Test thoroughly**: Verify your extensions work correctly with the rest of the system.

## Serialization

All DynamicEnum-based extensions support JSON serialization:

```kotlin
// Custom serializer (usually auto-generated)
class MyExtensionSerializer : DynamicEnumSerializer<MyExtension>(MyExtension::class.java)
class MyExtensionDeserializer : DynamicEnumDeserializer<MyExtension>(MyExtension::class.java)

// Apply to your class
@JsonDeserialize(using = MyExtensionDeserializer::class)
@JsonSerialize(using = MyExtensionSerializer::class)
class MyExtension(name: String) : DynamicEnum<MyExtension>(name)
```
