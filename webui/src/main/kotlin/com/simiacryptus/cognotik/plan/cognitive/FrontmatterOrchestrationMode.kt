package com.simiacryptus.cognotik.plan.cognitive


import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * A cognitive mode that generates frontmatter specification documents
 * instead of direct code artifacts. These specifications are then processed
 * by the DocProcessor to generate or update the final artifacts.
 *
 * This approach provides:
 * - Declarative intent: The AI expresses what should exist and how files relate
 * - Reproducibility: Generated specs can be version-controlled and re-executed
 * - Separation of concerns: Planning is separated from execution
 * - Incremental updates: DocProcessor's overwrite modes enable intelligent updates
 */
open class FrontmatterOrchestrationMode(
  orchestrationConfig: OrchestrationConfig,
  session: Session,
  user: User = defaultUser
) : CognitiveMode<FrontmatterOrchestrationMode.FrontmatterOrchestrationConfig>(
  orchestrationConfig,
  session,
  user
) {
  /**
   * Configuration for Frontmatter Orchestration Mode
   */
  class FrontmatterOrchestrationConfig(
    /** Directory where specification files are written */
    var specsDirectory: String = ".specs",
    /** Whether to automatically run DocProcessor after generating specs */
    var autoExecute: Boolean = true,
    /** Default overwrite mode for generated specifications */
    var defaultOverwriteMode: OverwriteModes = OverwriteModes.PatchToUpdate,
    /** File extension for specification documents */
    var specFileExtension: String = ".spec.md",
    /** Whether to keep specification files after execution */
    var preserveSpecs: Boolean = true,
    /** Maximum number of specifications to generate in a single plan */
    var maxSpecsPerPlan: Int = 20
  ) : CognitiveModeConfig(type = CognitiveModeType.FrontmatterOrchestration)

  /**
   * Represents a single specification document to be generated
   */
  data class SpecificationDocument(
    /** Relative path for the spec file within the specs directory */
    val specPath: String,
    /** Target file(s) this spec specifies (glob patterns supported) */
    val specifies: List<String> = emptyList(),
    /** Source files this spec documents */
    val documents: List<String> = emptyList(),
    /** Transform rules (source -> destination patterns) */
    val transforms: List<String> = emptyList(),
    /** Generation specifications */
    val generates: List<GenerateSpec> = emptyList(),
    /** Related files for context */
    val related: List<String> = emptyList(),
    /** Overwrite mode for this spec's targets */
    val overwrite: String? = null,
    /** Task type to use for processing */
    val taskType: String? = null,
    /** The markdown content body */
    val content: String
  )

  /**
   * Generation specification for explicit output files
   */
  data class GenerateSpec(
    val output: String,
    val inputs: List<String> = emptyList()
  )

  /**
   * Container for the generated specification plan
   */
  data class SpecificationPlan(
    val specifications: List<SpecificationDocument> = emptyList(),
    val summary: String = ""
  )

  /**
   * Result of specification generation with the original prompt
   */
  data class SpecificationPlanWithPrompt(
    val prompt: String,
    val plan: SpecificationPlan,
    val planText: String
  )

  private val log = LoggerFactory.getLogger(FrontmatterOrchestrationMode::class.java)
  private var transcriptStream: FileOutputStream? = null

  override fun initialize(task: SessionTask) {
    log.debug("Initializing FrontmatterOrchestrationMode")
    transcriptStream = task.transcript()
  }

  override fun contextData(): List<String> = emptyList()

  override fun handleUserMessage(userMessage: String, task: SessionTask) {
    try {
      log.debug("Handling user message: $userMessage")
      transcriptStream?.let { stream ->
        stream.write("\n## User Message\n\n$userMessage\n\n".toByteArray())
        stream.flush()
      }
      execute(userMessage, task)
    } catch (e: Throwable) {
      log.error("Error in handleUserMessage", e)
      task.error(e)
    }
  }

  private fun execute(userMessage: String, task: SessionTask) {
    try {
      val root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
        ?: task.ui.dataStorage?.getSessionDir(user, session)?.toPath()
        ?: File(".").toPath()

      val specsDir = root.resolve(config.specsDirectory)

      // Phase 1: Generate specifications
      val specPlan = generateSpecifications(userMessage, root, task)

      transcriptStream?.let { stream ->
        stream.write("\n## Generated Specifications\n\n".toByteArray())
        stream.write("${specPlan.planText}\n\n".toByteArray())
        stream.write("\n### Specification Diagram\n\n```mermaid\n${buildSpecDiagram(specPlan.plan)}\n```\n\n".toByteArray())
        stream.flush()
      }

      // Phase 2: Write specification files
      task.header("Writing Specifications")
      writeSpecifications(specPlan.plan, specsDir, task)

      // Phase 3: Execute DocProcessor (if autoExecute is enabled)
      if (config.autoExecute || orchestrationConfig.autoFix) {
        task.header("Executing DocProcessor")
        executeDocProcessor(root, specsDir, task)
      } else {
        task.add("Specifications written to `${config.specsDirectory}/`. Run DocProcessor manually to apply changes.".renderMarkdown())
      }

      task.complete()
    } catch (e: Throwable) {
      task.error(e)
      log.error("Error in execute", e)
      transcriptStream?.let { stream ->
        stream.write("\n## Error\n\n```\n${e.message}\n${e.stackTraceToString()}\n```\n\n".toByteArray())
        stream.flush()
      }
    } finally {
      transcriptStream?.close()
    }
  }

  private fun generateSpecifications(
    userMessage: String,
    root: Path,
    task: SessionTask
  ): SpecificationPlanWithPrompt {
    val codeFiles = getCodeFiles(root)
    val files = root.toFile().listFiles() ?: emptyArray()

    task.echo(userMessage.renderMarkdown())

    val describer = TaskContextYamlDescriber(orchestrationConfig)

    return if (!orchestrationConfig.autoFix) {
      Discussable(
        task = task,
        heading = "Specification Generation",
        userMessage = { userMessage },
        initialResponse = {
          newSpecPlan(
            orchestrationConfig,
            buildInputContext(userMessage, codeFiles, files, root),
            describer,
            task
          )
        },
        outputFn = { response ->
          try {
            renderSpecPlan(
              SpecificationPlanWithPrompt(
                prompt = userMessage,
                plan = response.obj,
                planText = response.text
              )
            )
          } catch (e: Throwable) {
            log.warn("Error rendering specification plan", e)
            task.error(e)
            e.message ?: e.javaClass.simpleName
          }
        },
        reviseResponse = { userMessages ->
          newSpecPlan(
            orchestrationConfig,
            userMessages.map { it.first },
            describer,
            task
          )
        }
      ).call().let {
        SpecificationPlanWithPrompt(
          prompt = userMessage,
          plan = it?.obj ?: SpecificationPlan(),
          planText = it?.text ?: "(no specifications generated)"
        )
      }
    } else {
      newSpecPlan(
        orchestrationConfig,
        buildInputContext(userMessage, codeFiles, files, root),
        describer,
        task
      ).let {
        SpecificationPlanWithPrompt(
          prompt = userMessage,
          plan = it.obj,
          planText = it.text
        )
      }
    }
  }

  private fun newSpecPlan(
    orchestrationConfig: OrchestrationConfig,
    inputs: List<String>,
    describer: TypeDescriber,
    task: SessionTask
  ): ParsedResponse<SpecificationPlan> {
    orchestrationConfig.absoluteWorkingDir?.apply { File(this).mkdirs() }

    val agent = ParsedAgent(
      name = "SpecificationPlanner",
      resultClass = SpecificationPlan::class.java,
      exampleInstance = SpecificationPlan(
        specifications = listOf(
          SpecificationDocument(
            specPath = "api/UserService.spec.md",
            specifies = listOf("../src/api/UserService.kt"),
            related = listOf("../src/models/User.kt"),
            overwrite = "Patch",
            taskType = "FileModification",
            content = "# UserService Implementation\n\n## Purpose\nImplement CRUD operations for users."
          )
        ),
        summary = "Generate UserService with CRUD operations"
      ),
      prompt = buildSpecificationPrompt(),
      model = orchestrationConfig.defaultSmart.getChildClient(task),
      parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
      temperature = orchestrationConfig.temperature,
      describer = describer
    )

    return agent.respond(
      messages = agent.chatMessages(inputs),
      input = inputs
    )
  }

  private fun buildSpecificationPrompt(): String = """
You are a software architect generating specification documents for a documentation-driven development workflow.

Given the user's request, generate a set of YAML frontmatter specification documents that describe the files to be created or modified.

## Frontmatter Schema

Each specification document should use YAML frontmatter with these keys:

- **specifies**: Glob patterns for files this spec creates/updates (e.g., "../src/api/*.kt")
- **documents**: Glob patterns for source files this spec describes (inverse of specifies)
- **transforms**: Regex transformation rules (e.g., "src/(.+)\\.java -> generated/$1.kt")
- **generates**: Explicit output files with inputs
- **related**: Additional context files
- **overwrite**: One of: Skip, Overwrite, OverwriteIfOlder, Patch, PatchIfOlder
- **taskType**: Task type for processing (default: FileModification)

## Guidelines

1. **One specification per logical unit**: A service, model, or configuration
2. **Explicit dependencies**: Use `related` to declare dependencies
3. **Appropriate granularity**: Not too fine (one spec per function) or too coarse (one spec for entire project)
4. **Clear boundaries**: Each specification should have a single responsibility
5. **Descriptive content**: The markdown body should clearly describe requirements, constraints, and examples

## Output Format

Generate a SpecificationPlan with:
- A list of SpecificationDocument objects
- A brief summary of what will be created

Do NOT generate the actual file contents. Generate specifications that describe what the files should contain.
    """.trimIndent()

  private fun buildInputContext(
    userMessage: String,
    codeFiles: Map<Path, String>,
    files: Array<File>,
    root: Path
  ): List<String> {
    val fileList = if (codeFiles.size > 10) {
      "Available files:\n${codeFiles.keys.joinToString("\n") { "* $it" }}"
    } else {
      files.filter { it.isFile }.joinToString("\n\n") { file ->
        val path = root.relativize(file.toPath())
        val content = codeFiles[path] ?: ""
        if (content.length < 2000) {
          "## $path\n\n```\n$content\n```"
        } else {
          "## $path\n\n(${content.length} characters, truncated)"
        }
      }
    }
    return listOf(fileList, userMessage)
  }

  private fun getCodeFiles(root: Path): Map<Path, String> {
    return try {
      getAvailableFiles(root)
        .filter { !it.contains("/.") && !it.startsWith(".") }
        .take(50)
        .associate { relativePath ->
          val file = root.resolve(relativePath).toFile()
          Path(relativePath) to (if (file.exists() && file.length() < 50000) file.readText() else "")
        }
    } catch (e: Exception) {
      log.warn("Error reading code files", e)
      emptyMap()
    }
  }

  private fun writeSpecifications(plan: SpecificationPlan, specsDir: Path, task: SessionTask) {
    if (!specsDir.exists()) {
      specsDir.createDirectories()
    }

    plan.specifications.forEach { spec ->
      try {
        val specFile = specsDir.resolve(spec.specPath)
        specFile.parent?.createDirectories()

        val frontmatter = buildFrontmatter(spec)
        val fullContent = "---\n$frontmatter---\n\n${spec.content}"

        specFile.writeText(fullContent)
        task.add("Written: `${spec.specPath}`".renderMarkdown())
        log.debug("Written specification: ${specFile.toAbsolutePath()}")
      } catch (e: Exception) {
        log.error("Failed to write specification: ${spec.specPath}", e)
        task.add("**Error** writing `${spec.specPath}`: ${e.message}".renderMarkdown())
      }
    }

    // Save plan metadata
    try {
      val planFile = specsDir.resolve("_plan_${now()}.json")
      planFile.writeText(JsonUtil.toJson(plan))
      task.add("Plan saved to `${planFile.fileName}`".renderMarkdown())
    } catch (e: Exception) {
      log.warn("Failed to save plan metadata", e)
    }
  }

  private fun buildFrontmatter(spec: SpecificationDocument): String {
    val lines = mutableListOf<String>()

    if (spec.specifies.isNotEmpty()) {
      if (spec.specifies.size == 1) {
        lines.add("specifies: ${spec.specifies.first()}")
      } else {
        lines.add("specifies:")
        spec.specifies.forEach { lines.add("  - $it") }
      }
    }

    if (spec.documents.isNotEmpty()) {
      if (spec.documents.size == 1) {
        lines.add("documents: ${spec.documents.first()}")
      } else {
        lines.add("documents:")
        spec.documents.forEach { lines.add("  - $it") }
      }
    }

    if (spec.transforms.isNotEmpty()) {
      if (spec.transforms.size == 1) {
        lines.add("transforms: ${spec.transforms.first()}")
      } else {
        lines.add("transforms:")
        spec.transforms.forEach { lines.add("  - $it") }
      }
    }

    if (spec.generates.isNotEmpty()) {
      lines.add("generates:")
      spec.generates.forEach { gen ->
        lines.add("  - output: ${gen.output}")
        if (gen.inputs.isNotEmpty()) {
          lines.add("    inputs:")
          gen.inputs.forEach { lines.add("      - $it") }
        }
      }
    }

    if (spec.related.isNotEmpty()) {
      if (spec.related.size == 1) {
        lines.add("related: ${spec.related.first()}")
      } else {
        lines.add("related:")
        spec.related.forEach { lines.add("  - $it") }
      }
    }

    spec.overwrite?.let { lines.add("overwrite: $it") }
    spec.taskType?.let { lines.add("task_type: $it") }

    return lines.joinToString("\n") + "\n"
  }

  private fun executeDocProcessor(root: Path, specsDir: Path, task: SessionTask) {
    try {
      val processor = DocProcessor(
        root = root.toFile(),
        docsFolder = specsDir.toFile(),
        overwriteMode = config.defaultOverwriteMode,
        fastModel = orchestrationConfig.defaultFast.modelType,
        smartModel = orchestrationConfig.defaultSmart.modelType
      )

      task.add("Starting DocProcessor on `${specsDir}`...".renderMarkdown())
      processor.run()
      task.add("DocProcessor completed successfully.".renderMarkdown())

      // Cleanup specs if not preserving
      if (!config.preserveSpecs) {
        specsDir.toFile().deleteRecursively()
        task.add("Cleaned up specification files.".renderMarkdown())
      }
    } catch (e: Exception) {
      log.error("DocProcessor execution failed", e)
      task.add("**Error** during DocProcessor execution: ${e.message}".renderMarkdown())
      throw e
    }
  }

  private fun renderSpecPlan(planWithPrompt: SpecificationPlanWithPrompt): String {
    return AgentPatterns.displayMapInTabs(
      mapOf(
        "Specs" to renderSpecList(planWithPrompt.plan),
        "Diagram" to ("```mermaid\n${buildSpecDiagram(planWithPrompt.plan)}\n```").renderMarkdown(),
        "JSON" to "```json\n${JsonUtil.toJson(planWithPrompt)}\n```".renderMarkdown()
      )
    )
  }

  private fun renderSpecList(plan: SpecificationPlan): String {
    val sb = StringBuilder()
    sb.append("## Specification Plan\n\n")
    sb.append("**Summary:** ${plan.summary}\n\n")
    sb.append("### Specifications (${plan.specifications.size})\n\n")

    plan.specifications.forEach { spec ->
      sb.append("#### ${spec.specPath}\n")
      if (spec.specifies.isNotEmpty()) {
        sb.append("- **Specifies:** ${spec.specifies.joinToString(", ")}\n")
      }
      if (spec.documents.isNotEmpty()) {
        sb.append("- **Documents:** ${spec.documents.joinToString(", ")}\n")
      }
      if (spec.related.isNotEmpty()) {
        sb.append("- **Related:** ${spec.related.joinToString(", ")}\n")
      }
      spec.overwrite?.let { sb.append("- **Overwrite:** $it\n") }
      sb.append("\n")
    }

    return sb.toString().renderMarkdown()
  }

  private fun buildSpecDiagram(plan: SpecificationPlan): String {
    val sb = StringBuilder()
    sb.append("graph TD\n")

    plan.specifications.forEachIndexed { index, spec ->
      val specId = "spec$index"
      val specLabel = spec.specPath.replace("/", "_").replace(".", "_")
      sb.append("    $specId[\"${spec.specPath}\"]\n")

      // Link to target files
      spec.specifies.forEachIndexed { targetIndex, target ->
        val targetId = "${specId}_target$targetIndex"
        sb.append("    $targetId((\"$target\"))\n")
        sb.append("    $specId -->|specifies| $targetId\n")
      }

      // Link to related files
      spec.related.forEachIndexed { relIndex, related ->
        val relId = "${specId}_rel$relIndex"
        sb.append("    $relId[/\"$related\"/]\n")
        sb.append("    $relId -.->|context| $specId\n")
      }
    }

    return sb.toString()
  }

  companion object {
    val inputCnt: Int = 1

    fun now(): String = SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(Date())
  }
}