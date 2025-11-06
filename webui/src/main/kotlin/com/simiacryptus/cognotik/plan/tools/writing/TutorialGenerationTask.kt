package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.truncateForDisplay
import com.simiacryptus.cognotik.plan.tools.reasoning.validateAndGetApi
import com.simiacryptus.cognotik.plan.transcript
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class TutorialGenerationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: TutorialGenerationTaskExecutionConfigData?
) : AbstractTask<TutorialGenerationTask.TutorialGenerationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class TutorialGenerationTaskExecutionConfigData(
    @Description("The final outcome the user should achieve (e.g., 'deploy a web app to the cloud', 'train a simple machine learning model')")
    val goal: String? = null,

    @Description("The environment the tutorial is for (e.g., 'Windows', 'Linux', 'macOS', 'VS Code', 'Docker')")
    val target_platform: String = "cross-platform",

    @Description("Whether to add placeholders like '[Screenshot of the successful output]' where visuals would be needed")
    val include_screenshots_placeholders: Boolean = true,

    @Description("Controls how much explanatory text is included with each step ('concise', 'detailed', 'verbose')")
    val verbosity: String = "detailed",

    @Description("Whether to add a common errors and troubleshooting section")
    val include_troubleshooting: Boolean = true,

    @Description("Target audience skill level (e.g., 'beginner', 'intermediate', 'advanced')")
    val skill_level: String = "beginner",

    @Description("Estimated time to complete the tutorial in minutes")
    val estimated_duration: Int = 30,

    @Description("Whether to include code examples and commands")
    val include_code_examples: Boolean = true,

    @Description("Whether to include validation steps to verify success")
    val include_validation_steps: Boolean = true,

    @Description("Whether to include a 'What You'll Learn' section")
    val include_learning_objectives: Boolean = true,

    @Description("Whether to include a 'Next Steps' section for further learning")
    val include_next_steps: Boolean = true,

    @Description("Number of main steps to break the tutorial into")
    val target_step_count: Int = 7,

    @Description("Related files or documentation to reference")
    val related_files: List<String>? = null,
    @Description("Optional input files to use as context (supports glob patterns, e.g. **/*.kt)")
    val input_files: List<String>? = null,


    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = TutorialGeneration.name,
    task_description = task_description ?: "Generate tutorial for: '$goal'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (goal.isNullOrBlank()) {
        return "goal must not be null or blank"
      }
      if (estimated_duration <= 0) {
        return "estimated_duration must be positive, got: $estimated_duration"
      }
      if (target_step_count < 3 || target_step_count > 20) {
        return "target_step_count must be between 3 and 20, got: $target_step_count"
      }
      if (verbosity.isBlank()) {
        return "verbosity must not be blank"
      }
      if (skill_level.isBlank()) {
        return "skill_level must not be blank"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class TutorialOutline(
    @Description("Tutorial title")
    val title: String = "",
    @Description("Brief description of what the tutorial covers")
    val description: String = "",
    @Description("Learning objectives")
    val learning_objectives: List<String> = emptyList(),
    @Description("Prerequisites (tools, software, prior knowledge)")
    val prerequisites: List<Prerequisite> = emptyList(),
    @Description("Main tutorial steps")
    val steps: List<TutorialStepOutline> = emptyList(),
    @Description("Estimated completion time in minutes")
    val estimated_time: Int = 0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "title must not be blank"
      if (description.isBlank()) return "description must not be blank"
      if (steps.isEmpty()) return "steps must not be empty"
      if (estimated_time <= 0) return "estimated_time must be positive"
      return ValidatedObject.validateFields(this)
    }
  }

  data class Prerequisite(
    @Description("Type of prerequisite (e.g., 'software', 'knowledge', 'account', 'hardware')")
    val type: String = "",
    @Description("Name of the prerequisite")
    val name: String = "",
    @Description("Description or installation instructions")
    val description: String = "",
    @Description("Whether this is required or optional")
    val required: Boolean = true,
    @Description("Link to download or learn more")
    val link: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (name.isBlank()) return "prerequisite name must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class TutorialStepOutline(
    @Description("Step number")
    val step_number: Int = 1,
    @Description("Step title")
    val title: String = "",
    @Description("What this step accomplishes")
    val purpose: String = "",
    @Description("Key actions to perform")
    val actions: List<String> = emptyList(),
    @Description("Whether this step includes code or commands")
    val has_code: Boolean = false,
    @Description("Whether this step needs a screenshot placeholder")
    val needs_screenshot: Boolean = false,
    @Description("Expected outcome or result")
    val expected_outcome: String = "",
    @Description("Estimated time for this step in minutes")
    val estimated_time: Int = 0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "step title must not be blank"
      if (step_number <= 0) return "step_number must be positive"
      return ValidatedObject.validateFields(this)
    }
  }

  data class TutorialStep(
    @Description("Step number")
    val step_number: Int = 1,
    @Description("Step title")
    val title: String = "",
    @Description("Detailed explanation")
    val explanation: String = "",
    @Description("Commands or code to execute")
    val code_blocks: List<CodeBlock> = emptyList(),
    @Description("Expected outcome description")
    val expected_outcome: String = "",
    @Description("Validation steps to verify success")
    val validation_steps: List<String> = emptyList(),
    @Description("Screenshot placeholder locations")
    val screenshot_placeholders: List<String> = emptyList(),
    @Description("Common issues for this step")
    val common_issues: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "step title must not be blank"
      if (explanation.isBlank()) return "step explanation must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class CodeBlock(
    @Description("Programming language or shell type")
    val language: String = "",
    @Description("The code or command")
    val code: String = "",
    @Description("Brief description of what this code does")
    val description: String = "",
    @Description("Whether this should be run in a specific directory")
    val working_directory: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (code.isBlank()) return "code must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class TroubleshootingSection(
    @Description("Common problems and solutions")
    val issues: List<TroubleshootingIssue> = emptyList()
  ) : ValidatedObject

  data class TroubleshootingIssue(
    @Description("The problem or error")
    val problem: String = "",
    @Description("Symptoms or error messages")
    val symptoms: List<String> = emptyList(),
    @Description("Possible causes")
    val causes: List<String> = emptyList(),
    @Description("Solutions to try")
    val solutions: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (problem.isBlank()) return "problem must not be blank"
      if (solutions.isEmpty()) return "solutions must not be empty"
      return ValidatedObject.validateFields(this)
    }
  }

  data class NextSteps(
    @Description("Suggestions for further learning")
    val suggestions: List<String> = emptyList(),
    @Description("Related tutorials or resources")
    val related_resources: List<String> = emptyList(),
    @Description("Advanced topics to explore")
    val advanced_topics: List<String> = emptyList()
  ) : ValidatedObject

  override fun promptSegment(): String {
    return """
TutorialGeneration - Create complete, step-by-step tutorials for processes and projects
  ** Specify the goal or final outcome to achieve
  ** Define target platform and environment
  ** Set skill level and estimated duration
  ** Enable screenshot placeholders for visual guidance
  ** Configure verbosity level (concise, detailed, verbose)
  ** Include code examples and commands
  ** Add validation steps to verify success
  ** Include troubleshooting section for common errors
  ** Add learning objectives and next steps
  ** Produces publication-ready tutorial with clear, actionable steps
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    log.info("Starting TutorialGenerationTask for goal: '${executionConfig?.goal}'")
    val transcript = task.transcript()
    val tutorialOutputFile = createTutorialOutputFile(task)

    // Validate configuration
    executionConfig?.validate()?.let { validationError ->
      log.error("Configuration validation failed: $validationError")
      task.safeComplete("CONFIGURATION ERROR: $validationError", log)
      task.error(ValidatedObject.ValidationError(validationError, executionConfig))
      resultFn("CONFIGURATION ERROR: $validationError")
      return
    }

    val goal = executionConfig?.goal
    if (goal.isNullOrBlank()) {
      log.error("No goal specified for tutorial generation")
      task.safeComplete("CONFIGURATION ERROR: No goal specified", log)
      resultFn("CONFIGURATION ERROR: No goal specified")
      return
    }

    val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return

    val tabs = TabbedDisplay(task)

    // Overview tab
    val overviewTask = task.ui.newTask(false)
    tabs["Overview"] = overviewTask.placeholder
    transcript?.write("# Tutorial Generation Transcript\n\n".toByteArray())
    transcript?.write("**Goal:** $goal\n\n".toByteArray())
    transcript?.write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n".toByteArray())
    transcript?.write("---\n\n".toByteArray())


    val overviewContent = buildString {
      appendLine("# Tutorial Generation")
      appendLine()
      appendLine("**Goal:** $goal")
      appendLine()
      appendLine("## Configuration")
      appendLine("- Target Platform: ${executionConfig.target_platform}")
      appendLine("- Skill Level: ${executionConfig.skill_level}")
      appendLine("- Estimated Duration: ${executionConfig.estimated_duration} minutes")
      appendLine("- Verbosity: ${executionConfig.verbosity}")
      appendLine("- Target Steps: ${executionConfig.target_step_count}")
      appendLine("- Include Code Examples: ${if (executionConfig.include_code_examples) "✓" else "✗"}")
      appendLine("- Include Screenshots: ${if (executionConfig.include_screenshots_placeholders) "✓" else "✗"}")
      appendLine("- Include Validation: ${if (executionConfig.include_validation_steps) "✓" else "✗"}")
      appendLine("- Include Troubleshooting: ${if (executionConfig.include_troubleshooting) "✓" else "✗"}")
      appendLine()
      appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
      appendLine("**Input Messages:** ${messages.size}")
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Progress")
      appendLine()
      appendLine("### Phase 1: Planning & Outline")
      appendLine("*Creating tutorial structure...*")
    }
    overviewTask.add(overviewContent.renderMarkdown)
    task.update()

    val resultBuilder = StringBuilder()
    resultBuilder.append("# Tutorial: $goal\n\n")

    try {
      // Gather context
      val priorContext = getPriorCode(agent.executionState)
      val contextFiles = getContextFiles()
      val inputFileContent = getInputFileCode()
      // Combine all context
      buildString {
        if (inputFileContent.isNotBlank()) appendLine(inputFileContent)
        if (contextFiles.isNotBlank()) appendLine(contextFiles)
      }

      if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
        log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
        val contextTask = task.ui.newTask(false)
        tabs["Context"] = contextTask.placeholder
        contextTask.add(
          buildString {
            appendLine("# Context & Resources")
            appendLine()
            if (inputFileContent.isNotBlank()) {
              appendLine("## Input Files")
              appendLine(inputFileContent.truncateForDisplay(2000))
              appendLine()
            }
            if (priorContext.isNotBlank()) {
              appendLine("## Prior Context")
              appendLine(priorContext.truncateForDisplay(2000))
              appendLine()
            }
            if (contextFiles.isNotBlank()) {
              appendLine("## Related Files")
              appendLine(contextFiles.truncateForDisplay(2000))
            }
          }.renderMarkdown
        )
        task.update()
      }

      // Phase 1: Create outline
      log.info("Phase 1: Creating tutorial outline")
      val outlineTask = task.ui.newTask(false)
      tabs["Outline"] = outlineTask.placeholder
      transcript?.write("## Phase 1: Planning & Outline\n\n".toByteArray())
      transcript?.write("Creating tutorial structure...\n\n".toByteArray())
      transcript?.write("**Configuration:**\n".toByteArray())
      transcript?.write("- Target Steps: ${executionConfig.target_step_count}\n".toByteArray())
      transcript?.write("- Skill Level: ${executionConfig.skill_level}\n\n".toByteArray())

      outlineTask.add(
        buildString {
          appendLine("# Tutorial Outline")
          appendLine()
          appendLine("**Status:** Creating structured outline...")
          appendLine()
        }.renderMarkdown
      )
      task.update()

      val outlineAgent = ParsedAgent(
        resultClass = TutorialOutline::class.java,
        prompt = """
You are an expert technical writer and educator. Create a detailed outline for a tutorial.

Goal: $goal

Target Platform: ${executionConfig.target_platform}
Skill Level: ${executionConfig.skill_level}
Estimated Duration: ${executionConfig.estimated_duration} minutes
Target Step Count: ${executionConfig.target_step_count}

${if (inputFileContent.isNotBlank()) "Input Files:\n${inputFileContent.truncateForDisplay(3000)}\n" else ""}
${if (messages.isNotEmpty()) "User Messages:\n${messages.joinToString("\n").truncateForDisplay(2000)}\n" else ""}
${if (priorContext.isNotBlank()) "Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Related Files:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline with:
1. A clear, descriptive title
2. Brief description of what the tutorial covers
3. ${if (executionConfig.include_learning_objectives) "3-5 specific learning objectives" else ""}
4. Complete list of prerequisites:
   - Required software and tools (with versions if relevant)
   - Prior knowledge or skills needed
   - Accounts or services required
   - Hardware requirements if applicable
5. ${executionConfig.target_step_count} main steps that:
   - Follow a logical progression
   - Are appropriately sized (not too large or small)
   - Build on previous steps
   - Lead to the stated goal
   - Include time estimates

For each step, specify:
- Clear, action-oriented title
- Purpose (what this step accomplishes)
- Key actions to perform
- Whether it includes code/commands
- Whether it needs a screenshot
- Expected outcome
- Estimated time (total should be ~${executionConfig.estimated_duration} minutes)

Ensure the outline:
- Is appropriate for ${executionConfig.skill_level} level
- Works on ${executionConfig.target_platform}
- Follows best practices for technical tutorials
- Has a clear beginning, middle, and end
          """.trimIndent(),
        model = api,
        temperature = 0.6,
        parsingChatter = orchestrationConfig.parsingChatter
      )

      var outline = outlineAgent.answer(listOf("Generate outline")).obj

      // Validate outline
      outline.validate()?.let { validationError ->
        log.error("Outline validation failed: $validationError")
        outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
        task.safeComplete("Outline validation failed: $validationError", log)
        resultFn("ERROR: Outline validation failed: $validationError")
        return
      }

      log.info("Generated outline: ${outline.steps.size} steps, ${outline.prerequisites.size} prerequisites")
      transcript?.write("### Outline Generated\n\n".toByteArray())
      transcript?.write("**Title:** ${outline.title}\n\n".toByteArray())
      transcript?.write("**Steps:** ${outline.steps.size}\n\n".toByteArray())
      transcript?.write("**Prerequisites:** ${outline.prerequisites.size}\n\n".toByteArray())
      transcript?.write("**Estimated Time:** ${outline.estimated_time} minutes\n\n".toByteArray())
      transcript?.write("---\n\n".toByteArray())


      val outlineContent = buildString {
        appendLine("## ${outline.title}")
        appendLine()
        appendLine(outline.description)
        appendLine()
        appendLine("**Estimated Time:** ${outline.estimated_time} minutes")
        appendLine()
        appendLine("---")
        appendLine()
        if (outline.learning_objectives.isNotEmpty()) {
          appendLine("### What You'll Learn")
          outline.learning_objectives.forEach { objective ->
            appendLine("- $objective")
          }
          appendLine()
          appendLine("---")
          appendLine()
        }
        appendLine("### Prerequisites")
        appendLine()
        val requiredPrereqs = outline.prerequisites.filter { it.required }
        val optionalPrereqs = outline.prerequisites.filter { !it.required }

        if (requiredPrereqs.isNotEmpty()) {
          appendLine("#### Required")
          requiredPrereqs.forEach { prereq ->
            appendLine("**${prereq.name}** (${prereq.type})")
            appendLine()
            appendLine(prereq.description)
            if (prereq.link != null) {
              appendLine()
              appendLine("Download: ${prereq.link}")
            }
            appendLine()
          }
        }

        if (optionalPrereqs.isNotEmpty()) {
          appendLine("#### Optional")
          optionalPrereqs.forEach { prereq ->
            appendLine("**${prereq.name}** (${prereq.type})")
            appendLine()
            appendLine(prereq.description)
            if (prereq.link != null) {
              appendLine()
              appendLine("Download: ${prereq.link}")
            }
            appendLine()
          }
        }
        appendLine("---")
        appendLine()
        appendLine("### Tutorial Steps")
        outline.steps.forEach { step ->
          appendLine("#### Step ${step.step_number}: ${step.title}")
          appendLine()
          appendLine("**Purpose:** ${step.purpose}")
          appendLine()
          appendLine("**Actions:**")
          step.actions.forEach { action ->
            appendLine("- $action")
          }
          appendLine()
          if (step.has_code) {
            appendLine("*Includes code/commands*")
            appendLine()
          }
          if (step.needs_screenshot) {
            appendLine("*Screenshot needed*")
            appendLine()
          }
          appendLine("**Expected Outcome:** ${step.expected_outcome}")
          appendLine()
          appendLine("**Time:** ~${step.estimated_time} min")
          appendLine()
          appendLine("---")
          appendLine()
        }
        appendLine("**Status:** ✅ Complete")
      }
      outlineTask.add(outlineContent.renderMarkdown)
      task.update()

      overviewTask.add("✅ Phase 1 Complete: Outline created (${outline.steps.size} steps)\n".renderMarkdown)
      overviewTask.add("\n### Phase 2: Writing Steps\n*Developing detailed step-by-step instructions...*\n".renderMarkdown)
      task.update()
      transcript?.write("## Phase 2: Writing Steps\n\n".toByteArray())
      transcript?.write("Input Context:\n".toByteArray())
      if (messages.isNotEmpty()) {
        transcript?.write("**Messages:** ${messages.size} items\n".toByteArray())
        messages.forEach { msg ->
          transcript?.write("- ${msg.truncateForDisplay(100)}\n".toByteArray())
        }
        transcript?.write("\n".toByteArray())
      }
      if (inputFileContent.isNotBlank()) {
        transcript?.write("**Input Files Loaded:** ${inputFileContent.length} characters\n\n".toByteArray())
      }
      transcript?.write("Developing detailed step-by-step instructions...\n\n".toByteArray())


      // Phase 2: Write each step
      log.info("Phase 2: Writing detailed steps")
      val tutorialSteps = mutableListOf<TutorialStep>()

      outline.steps.forEachIndexed { index, stepOutline ->
        log.info("Writing step ${index + 1}/${outline.steps.size}: ${stepOutline.title}")

        overviewTask.add("- Step ${index + 1}: ${stepOutline.title.truncateForDisplay(50)} ".renderMarkdown)
        task.update()
        transcript?.write("### Step ${index + 1}: ${stepOutline.title}\n\n".toByteArray())
        transcript?.write("Writing detailed instructions...\n\n".toByteArray())


        val stepTask = task.ui.newTask(false)
        tabs["Step ${index + 1}"] = stepTask.placeholder

        stepTask.add(
          buildString {
            appendLine("# Step ${index + 1}: ${stepOutline.title}")
            appendLine()
            appendLine("**Status:** Writing detailed instructions...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        // Build context from previous steps
        val previousStepsContext = if (tutorialSteps.isNotEmpty()) {
          buildString {
            appendLine("## Previous Steps Summary")
            tutorialSteps.takeLast(2).forEach { prevStep ->
              appendLine("**Step ${prevStep.step_number}:** ${prevStep.title}")
              appendLine("Outcome: ${prevStep.expected_outcome}")
              appendLine()
            }
          }
        } else {
          "This is the first step."
        }

        val stepAgent = ParsedAgent(
          resultClass = TutorialStep::class.java,
          prompt = """
You are an expert technical writer. Write detailed instructions for this tutorial step.

Overall Goal: $goal
Target Platform: ${executionConfig.target_platform}
Skill Level: ${executionConfig.skill_level}
Verbosity: ${executionConfig.verbosity}

Step to Write:
Number: ${stepOutline.step_number}
Title: ${stepOutline.title}
Purpose: ${stepOutline.purpose}
Actions: ${stepOutline.actions.joinToString("; ")}
Expected Outcome: ${stepOutline.expected_outcome}

$previousStepsContext

Write a complete step with:
1. Clear, ${executionConfig.verbosity} explanation of what to do and why
2. ${if (executionConfig.include_code_examples && stepOutline.has_code) "Exact commands or code to execute (with language/shell specified)" else ""}
3. ${if (executionConfig.include_screenshots_placeholders && stepOutline.needs_screenshot) "Screenshot placeholders where visual confirmation is helpful" else ""}
4. Description of expected outcome (what the user should see)
5. ${if (executionConfig.include_validation_steps) "Validation steps to verify success" else ""}
6. ${if (executionConfig.include_troubleshooting) "Common issues that might occur in this step" else ""}

Guidelines:
- Use ${executionConfig.verbosity} level of detail
- Write for ${executionConfig.skill_level} skill level
- Be specific about ${executionConfig.target_platform} requirements
- Use clear, imperative language ("Click...", "Run...", "Open...")
- Include exact file paths, commands, and values
- Explain technical terms if needed for skill level
- Number sub-steps if there are multiple actions
          """.trimIndent(),
          model = api,
          temperature = 0.5,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        var tutorialStep = stepAgent.answer(listOf("Write step")).obj
        tutorialSteps.add(tutorialStep)
        transcript?.write("**Completed:** ${tutorialStep.title}\n".toByteArray())
        transcript?.write("- Code blocks: ${tutorialStep.code_blocks.size}\n".toByteArray())
        transcript?.write("- Validation steps: ${tutorialStep.validation_steps.size}\n\n".toByteArray())


        val stepContent = buildString {
          appendLine("## Step ${tutorialStep.step_number}: ${tutorialStep.title}")
          appendLine()
          appendLine(tutorialStep.explanation)
          appendLine()

          if (tutorialStep.code_blocks.isNotEmpty()) {
            appendLine("### Commands/Code")
            appendLine()
            tutorialStep.code_blocks.forEach { codeBlock ->
              if (codeBlock.description.isNotBlank()) {
                appendLine(codeBlock.description)
                appendLine()
              }
              if (codeBlock.working_directory != null) {
                appendLine("*Run in directory: `${codeBlock.working_directory}`*")
                appendLine()
              }
              appendLine("```${codeBlock.language}")
              appendLine(codeBlock.code)
              appendLine("```")
              appendLine()
            }
          }

          if (tutorialStep.screenshot_placeholders.isNotEmpty()) {
            appendLine("### Visual Checkpoints")
            tutorialStep.screenshot_placeholders.forEach { placeholder ->
              appendLine("📸 $placeholder")
              appendLine()
            }
          }

          appendLine("### Expected Outcome")
          appendLine(tutorialStep.expected_outcome)
          appendLine()

          if (tutorialStep.validation_steps.isNotEmpty()) {
            appendLine("### Verify Success")
            tutorialStep.validation_steps.forEachIndexed { idx, validation ->
              appendLine("${idx + 1}. $validation")
            }
            appendLine()
          }

          if (tutorialStep.common_issues.isNotEmpty()) {
            appendLine("### Common Issues")
            tutorialStep.common_issues.forEach { issue ->
              appendLine("⚠️ $issue")
              appendLine()
            }
          }

          appendLine("---")
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }
        stepTask.add(stepContent.renderMarkdown)
        task.update()

        overviewTask.add("✅\n".renderMarkdown)
        task.update()
      }

      overviewTask.add("✅ Phase 2 Complete: All steps written\n".renderMarkdown)

      // Phase 3: Troubleshooting section (if enabled)
      var troubleshootingSection: TroubleshootingSection? = null
      if (executionConfig.include_troubleshooting) {
        overviewTask.add("\n### Phase 3: Troubleshooting\n*Compiling common issues and solutions...*\n".renderMarkdown)
        task.update()
        transcript?.write("## Phase 3: Troubleshooting\n\n".toByteArray())
        transcript?.write("Compiling common issues and solutions...\n\n".toByteArray())


        log.info("Phase 3: Creating troubleshooting section")
        val troubleshootingTask = task.ui.newTask(false)
        tabs["Troubleshooting"] = troubleshootingTask.placeholder

        troubleshootingTask.add(
          buildString {
            appendLine("# Troubleshooting")
            appendLine()
            appendLine("**Status:** Identifying common problems...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val troubleshootingAgent = ParsedAgent(
          resultClass = TroubleshootingSection::class.java,
          prompt = """
You are an expert technical support specialist. Create a troubleshooting section for this tutorial.

Goal: $goal
Target Platform: ${executionConfig.target_platform}
Skill Level: ${executionConfig.skill_level}

Tutorial Steps Summary:
${tutorialSteps.joinToString("\n") { "Step ${it.step_number}: ${it.title}" }}

Identify 5-8 common problems users might encounter, including:
- Platform-specific issues
- Configuration errors
- Permission problems
- Version compatibility issues
- Common mistakes or misunderstandings
- Environment setup problems

For each issue, provide:
- Clear description of the problem
- Symptoms or error messages users might see
- Possible causes
- Step-by-step solutions (multiple if applicable)

Focus on issues that:
- Are likely to occur for ${executionConfig.skill_level} users
- Are specific to ${executionConfig.target_platform}
- Have clear, actionable solutions
- Aren't already covered in step-specific troubleshooting
          """.trimIndent(),
          model = api,
          temperature = 0.5,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        troubleshootingSection = troubleshootingAgent.answer(listOf("Create troubleshooting")).obj
        transcript?.write("**Troubleshooting Issues Identified:** ${troubleshootingSection.issues.size}\n\n".toByteArray())
        transcript?.write("---\n\n".toByteArray())


        val troubleshootingContent = buildString {
          appendLine("## Common Issues and Solutions")
          appendLine()
          if (troubleshootingSection.issues.isEmpty()) {
            appendLine("No common issues identified. If you encounter problems, check:")
            appendLine("- Prerequisites are correctly installed")
            appendLine("- Commands are run in the correct directory")
            appendLine("- Platform-specific requirements are met")
          } else {
            troubleshootingSection.issues.forEachIndexed { index, issue ->
              appendLine("### ${index + 1}. ${issue.problem}")
              appendLine()
              if (issue.symptoms.isNotEmpty()) {
                appendLine("**Symptoms:**")
                issue.symptoms.forEach { symptom ->
                  appendLine("- $symptom")
                }
                appendLine()
              }
              if (issue.causes.isNotEmpty()) {
                appendLine("**Possible Causes:**")
                issue.causes.forEach { cause ->
                  appendLine("- $cause")
                }
                appendLine()
              }
              appendLine("**Solutions:**")
              issue.solutions.forEachIndexed { solIdx, solution ->
                appendLine("${solIdx + 1}. $solution")
              }
              appendLine()
              if (index < troubleshootingSection.issues.size - 1) {
                appendLine("---")
                appendLine()
              }
            }
          }
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }
        troubleshootingTask.add(troubleshootingContent.renderMarkdown)
        task.update()

        overviewTask.add("✅ Phase 3 Complete: Troubleshooting section added\n".renderMarkdown)
      }

      // Phase 4: Next Steps (if enabled)
      var nextSteps: NextSteps? = null
      if (executionConfig.include_next_steps) {
        overviewTask.add("\n### Phase 4: Next Steps\n*Suggesting further learning paths...*\n".renderMarkdown)
        task.update()
        transcript?.write("## Phase 4: Next Steps\n\n".toByteArray())
        transcript?.write("Suggesting further learning paths...\n\n".toByteArray())


        log.info("Phase 4: Creating next steps section")
        val nextStepsTask = task.ui.newTask(false)
        tabs["Next Steps"] = nextStepsTask.placeholder

        nextStepsTask.add(
          buildString {
            appendLine("# Next Steps")
            appendLine()
            appendLine("**Status:** Generating recommendations...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val nextStepsAgent = ParsedAgent(
          resultClass = NextSteps::class.java,
          prompt = """
You are an expert educator. Suggest next steps for learners who completed this tutorial.

Goal Achieved: $goal
Skill Level: ${executionConfig.skill_level}

Provide:
1. 3-5 suggestions for what to try next or how to extend what they learned
2. 3-5 related tutorials, documentation, or resources
3. 3-5 advanced topics to explore

Make suggestions:
- Progressive (build on what was learned)
- Appropriate for ${executionConfig.skill_level} moving to the next level
- Specific and actionable
- Include mix of practice, learning, and exploration
          """.trimIndent(),
          model = api,
          temperature = 0.6,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        nextSteps = nextStepsAgent.answer(listOf("Generate next steps")).obj
        transcript?.write("**Next Steps Generated:**\n".toByteArray())
        transcript?.write("- Suggestions: ${nextSteps.suggestions.size}\n".toByteArray())
        transcript?.write("- Resources: ${nextSteps.related_resources.size}\n\n".toByteArray())
        transcript?.write("---\n\n".toByteArray())


        val nextStepsContent = buildString {
          appendLine("## What's Next?")
          appendLine()
          appendLine("Congratulations on completing this tutorial! Here are some ways to continue your learning:")
          appendLine()
          if (nextSteps.suggestions.isNotEmpty()) {
            appendLine("### Try These Next")
            nextSteps.suggestions.forEach { suggestion ->
              appendLine("- $suggestion")
            }
            appendLine()
          }
          if (nextSteps.related_resources.isNotEmpty()) {
            appendLine("### Related Resources")
            nextSteps.related_resources.forEach { resource ->
              appendLine("- $resource")
            }
            appendLine()
          }
          if (nextSteps.advanced_topics.isNotEmpty()) {
            appendLine("### Advanced Topics")
            nextSteps.advanced_topics.forEach { topic ->
              appendLine("- $topic")
            }
            appendLine()
          }
          appendLine("**Status:** ✅ Complete")
        }
        nextStepsTask.add(nextStepsContent.renderMarkdown)
        task.update()

        overviewTask.add("✅ Phase 4 Complete: Next steps added\n".renderMarkdown)
      }

      // Phase 5: Final Assembly
      overviewTask.add("\n### Phase 5: Final Assembly\n*Compiling complete tutorial...*\n".renderMarkdown)
      task.update()
      transcript?.write("## Phase 5: Final Assembly\n\n".toByteArray())
      transcript?.write("Compiling complete tutorial...\n\n".toByteArray())


      log.info("Phase 5: Assembling final tutorial")
      val finalTask = task.ui.newTask(false)
      tabs["Complete Tutorial"] = finalTask.placeholder

      val finalTutorial = buildString {
        appendLine("# ${outline.title}")
        appendLine()
        appendLine(outline.description)
        appendLine()
        appendLine("**⏱️ Estimated Time:** ${outline.estimated_time} minutes")
        appendLine()
        appendLine("**🎯 Skill Level:** ${executionConfig.skill_level.capitalize()}")
        appendLine()
        appendLine("**💻 Platform:** ${executionConfig.target_platform}")
        appendLine()
        appendLine("---")
        appendLine()

        if (outline.learning_objectives.isNotEmpty()) {
          appendLine("## What You'll Learn")
          appendLine()
          outline.learning_objectives.forEach { objective ->
            appendLine("✓ $objective")
          }
          appendLine()
          appendLine("---")
          appendLine()
        }

        appendLine("## Prerequisites")
        appendLine()
        val requiredPrereqs = outline.prerequisites.filter { it.required }
        val optionalPrereqs = outline.prerequisites.filter { !it.required }

        if (requiredPrereqs.isNotEmpty()) {
          appendLine("### Required")
          appendLine()
          requiredPrereqs.forEach { prereq ->
            appendLine("- **${prereq.name}** (${prereq.type}): ${prereq.description}")
            if (prereq.link != null) {
              appendLine("  - Download: ${prereq.link}")
            }
          }
          appendLine()
        }

        if (optionalPrereqs.isNotEmpty()) {
          appendLine("### Optional")
          appendLine()
          optionalPrereqs.forEach { prereq ->
            appendLine("- **${prereq.name}** (${prereq.type}): ${prereq.description}")
            if (prereq.link != null) {
              appendLine("  - Download: ${prereq.link}")
            }
          }
          appendLine()
        }

        appendLine("---")
        appendLine()
        appendLine("## Tutorial Steps")
        appendLine()

        tutorialSteps.forEach { step ->
          appendLine("### Step ${step.step_number}: ${step.title}")
          appendLine()
          appendLine(step.explanation)
          appendLine()

          if (step.code_blocks.isNotEmpty()) {
            step.code_blocks.forEach { codeBlock ->
              if (codeBlock.description.isNotBlank()) {
                appendLine(codeBlock.description)
                appendLine()
              }
              if (codeBlock.working_directory != null) {
                appendLine("*Run in: `${codeBlock.working_directory}`*")
                appendLine()
              }
              appendLine("```${codeBlock.language}")
              appendLine(codeBlock.code)
              appendLine("```")
              appendLine()
            }
          }

          if (step.screenshot_placeholders.isNotEmpty()) {
            step.screenshot_placeholders.forEach { placeholder ->
              appendLine("📸 $placeholder")
              appendLine()
            }
          }

          appendLine("**Expected Outcome:** ${step.expected_outcome}")
          appendLine()

          if (step.validation_steps.isNotEmpty()) {
            appendLine("**Verify Success:**")
            step.validation_steps.forEachIndexed { idx, validation ->
              appendLine("${idx + 1}. $validation")
            }
            appendLine()
          }

          if (step.common_issues.isNotEmpty()) {
            appendLine("**⚠️ Common Issues:**")
            step.common_issues.forEach { issue ->
              appendLine("- $issue")
            }
            appendLine()
          }

          appendLine("---")
          appendLine()
        }

        if (troubleshootingSection != null && troubleshootingSection.issues.isNotEmpty()) {
          appendLine("## Troubleshooting")
          appendLine()
          troubleshootingSection.issues.forEachIndexed { index, issue ->
            appendLine("### ${index + 1}. ${issue.problem}")
            appendLine()
            if (issue.symptoms.isNotEmpty()) {
              appendLine("**Symptoms:**")
              issue.symptoms.forEach { symptom ->
                appendLine("- $symptom")
              }
              appendLine()
            }
            if (issue.causes.isNotEmpty()) {
              appendLine("**Possible Causes:**")
              issue.causes.forEach { cause ->
                appendLine("- $cause")
              }
              appendLine()
            }
            appendLine("**Solutions:**")
            issue.solutions.forEachIndexed { solIdx, solution ->
              appendLine("${solIdx + 1}. $solution")
            }
            appendLine()
          }
          appendLine("---")
          appendLine()
        }

        if (nextSteps != null) {
          appendLine("## Next Steps")
          appendLine()
          appendLine("🎉 Congratulations on completing this tutorial!")
          appendLine()
          if (nextSteps.suggestions.isNotEmpty()) {
            appendLine("### Try These Next")
            nextSteps.suggestions.forEach { suggestion ->
              appendLine("- $suggestion")
            }
            appendLine()
          }
          if (nextSteps.related_resources.isNotEmpty()) {
            appendLine("### Related Resources")
            nextSteps.related_resources.forEach { resource ->
              appendLine("- $resource")
            }
            appendLine()
          }
          if (nextSteps.advanced_topics.isNotEmpty()) {
            appendLine("### Advanced Topics")
            nextSteps.advanced_topics.forEach { topic ->
              appendLine("- $topic")
            }
            appendLine()
          }
        }
      }

      finalTask.add(finalTutorial.renderMarkdown)
      tutorialOutputFile?.write(finalTutorial.toByteArray(Charsets.UTF_8))
      task.update()

      // Final statistics
      val totalTime = System.currentTimeMillis() - startTime
      val totalWords = finalTutorial.split("\\s+".toRegex()).size
      transcript?.write("## Generation Complete\n\n".toByteArray())
      transcript?.write("**Statistics:**\n".toByteArray())
      transcript?.write("- Total Steps: ${tutorialSteps.size}\n".toByteArray())
      transcript?.write("- Prerequisites: ${outline.prerequisites.size}\n".toByteArray())
      transcript?.write("- Word Count: $totalWords\n".toByteArray())
      transcript?.write("- Code Blocks: ${tutorialSteps.sumOf { it.code_blocks.size }}\n".toByteArray())
      transcript?.write("- Total Time: ${totalTime / 1000.0}s\n\n".toByteArray())
      transcript?.write("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n".toByteArray())
      transcript?.flush()
      transcript?.close()


      overviewTask.add(
        buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ✅ Generation Complete")
          appendLine()
          appendLine("**Output Files:**")
          appendLine("- [Complete Tutorial](tutorial.md)")
          appendLine("- [Transcript](transcript.md)")
          appendLine()
          appendLine("**Statistics:**")
          appendLine("- Total Steps: ${tutorialSteps.size}")
          appendLine("- Prerequisites: ${outline.prerequisites.size}")
          appendLine("- Estimated Duration: ${outline.estimated_time} minutes")
          appendLine("- Word Count: $totalWords")
          appendLine("- Code Blocks: ${tutorialSteps.sumOf { it.code_blocks.size }}")
          if (troubleshootingSection != null) {
            appendLine("- Troubleshooting Issues: ${troubleshootingSection.issues.size}")
          }
          appendLine("- Total Time: ${totalTime / 1000.0}s")
          appendLine()
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }.renderMarkdown
      )
      task.update()

      // Write final tutorial to file
      tutorialOutputFile?.flush()
      tutorialOutputFile?.close()

      // Concise summary for resultFn
      val finalResult = buildString {
        appendLine("# ✅ Tutorial Generated: ${outline.title}")
        appendLine()
        appendLine("A comprehensive tutorial with **${tutorialSteps.size} steps** was successfully generated.")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("**Goal:** $goal")
        appendLine("**Platform:** ${executionConfig.target_platform}")
        appendLine("**Skill Level:** ${executionConfig.skill_level}")
        appendLine("**Estimated Duration:** ${outline.estimated_time} minutes")
        appendLine("**Key Features:**")
        appendLine("- ${outline.prerequisites.size} prerequisites identified")
        appendLine("- ${tutorialSteps.size} detailed steps with explanations")
        appendLine("- ${tutorialSteps.sumOf { it.code_blocks.size }} code examples")
        appendLine("- ${tutorialSteps.sumOf { it.validation_steps.size }} validation steps")
        appendLine("- Estimated completion time: ${outline.estimated_time} minutes")
        if (troubleshootingSection != null) {
          appendLine("- ${troubleshootingSection.issues.size} troubleshooting scenarios")
        }
        appendLine()
        appendLine("## Output Files")
        appendLine()
        appendLine("- **Complete Tutorial:** [tutorial.md](tutorial.md)")
        appendLine("- **Transcript:** [transcript.md](transcript.md)")
        appendLine()
        appendLine("**Generation Time:** ${totalTime / 1000.0}s")
      }

      log.info("TutorialGenerationTask completed: steps=${tutorialSteps.size}, time=${totalTime}ms")

      task.safeComplete("Tutorial generation complete: ${tutorialSteps.size} steps in ${totalTime / 1000}s", log)
      resultFn(finalResult)

    } catch (e: Exception) {
      log.error("Error during tutorial generation", e)
      task.error(e)
      transcript?.write("\n## Error Occurred\n\n".toByteArray())
      transcript?.write("**Error:** ${e.message}\n\n".toByteArray())
      transcript?.flush()
      transcript?.close()

      overviewTask.add(
        buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ❌ Error Occurred")
          appendLine()
          appendLine("**Error:** ${e.message}")
          appendLine()
          appendLine("**Type:** ${e.javaClass.simpleName}")
        }.renderMarkdown
      )
      task.update()
      tutorialOutputFile?.close()

      val errorOutput = buildString {
        appendLine("# Error in Tutorial Generation")
        appendLine()
        appendLine("**Goal:** $goal")
        appendLine()
        appendLine("**Error:** ${e.message}")
        appendLine()
        if (resultBuilder.isNotEmpty()) {
          appendLine("## Partial Results")
          appendLine()
          appendLine(resultBuilder.toString())
        }
      }
      resultFn(errorOutput)
    }
  }

  private fun createTutorialOutputFile(task: SessionTask): FileOutputStream? {
    return try {
      val (link, file) = task.createFile("tutorial.md")
      log.info("Created tutorial output file: $link")
      file?.outputStream()
    } catch (e: Exception) {
      log.error("Failed to create tutorial output file", e)
      null
    }
  }


  private fun getContextFiles(): String {
    val relatedFiles = executionConfig?.related_files ?: return ""
    if (relatedFiles.isEmpty()) return ""
    log.debug("Loading ${relatedFiles.size} related context files")

    return buildString {
      appendLine("## Related Documentation Files")
      appendLine()
      relatedFiles.forEach { file ->
        try {
          val filePath = root.resolve(file)
          if (filePath.toFile().exists()) {
            log.debug("Successfully loaded context file: $file")
            appendLine("### $file")
            appendLine("```")
            appendLine(filePath.toFile().readText().truncateForDisplay(1500))
            appendLine("```")
            appendLine()
          } else {
            log.warn("Context file not found: $file")
          }
        } catch (e: Exception) {
          log.warn("Error reading file: $file", e)
        }
      }
    }
  }

  private fun getInputFileCode(): String {
    val inputFiles = executionConfig?.input_files ?: return ""
    if (inputFiles.isEmpty()) return ""
    log.debug("Loading ${inputFiles.size} input files")
    return buildString {
      appendLine("## Input Files Context")
      appendLine()
      inputFiles.forEach { pattern ->
        try {
          val filePath = root.resolve(pattern)
          if (filePath.toFile().exists()) {
            log.debug("Successfully loaded input file: $pattern")
            appendLine("### $pattern")
            appendLine("```")
            appendLine(filePath.toFile().readText().truncateForDisplay(2000))
            appendLine("```")
            appendLine()
          } else {
            log.warn("Input file not found: $pattern")
          }
        } catch (e: Exception) {
          log.warn("Error reading input file: $pattern", e)
        }
      }
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(TutorialGenerationTask::class.java)
    val TutorialGeneration = TaskType(
      "TutorialGeneration",
      TutorialGenerationTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Create complete, step-by-step tutorials for processes and projects",
      """
              Generates comprehensive tutorials with clear, actionable steps.
              <ul>
                <li>Creates detailed outline with prerequisites and learning objectives</li>
                <li>Breaks process into logical, numbered steps</li>
                <li>Generates exact commands and code examples</li>
                <li>Includes expected outcomes and validation steps</li>
                <li>Adds screenshot placeholders for visual guidance</li>
                <li>Provides troubleshooting section for common issues</li>
                <li>Suggests next steps for continued learning</li>
                <li>Configurable verbosity and skill level</li>
                <li>Platform-specific instructions and requirements</li>
                <li>Ideal for how-to guides, educational content, and project-based learning</li>
              </ul>
            """
    )
  }
}