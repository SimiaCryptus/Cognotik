package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.apps.plan.tools.file.CodeOptimizationTask.CodeOptimizationTaskConfigData
import org.slf4j.LoggerFactory

class CodeOptimizationTask(
  planSettings: PlanSettings,
  planTask: CodeOptimizationTaskConfigData?
) : AbstractAnalysisTask<CodeOptimizationTaskConfigData>(planSettings, planTask) {

  class CodeOptimizationTaskConfigData(
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    input_files: List<String>? = null,
    output_files: List<String>? = null,
    state: TaskState? = null
  ) : FileTaskConfigBase(
    task_type = TaskType.Optimization.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    related_files = input_files,
    files = output_files,
    state = state
  )

  override val actorName = "CodeOptimization"
  override val actorPrompt = """
    Analyze the provided code and suggest optimizations to improve code quality. Focus exclusively on:
    1. Code structure and organization
    2. Readability improvements
    3. Maintainability enhancements
    4. Proper use of language-specific features and best practices
    5. Design pattern applications
                    
    Provide detailed explanations for each suggested optimization, including:
    - The reason for the optimization
    - The expected benefits
    - Any potential trade-offs or considerations
    
    Format the response as a markdown document with appropriate headings and code snippets.
    Use diff format to show the proposed changes clearly.
    """.trimIndent()

  override fun promptSegment() = """
    CodeOptimization - Analyze and optimize existing code for better readability, maintainability, and adherence to best practices
      * Specify the files to be optimized
      * Optionally provide specific areas of focus for the optimization (e.g., code structure, readability, design patterns)
    """.trimIndent()


  override fun getAnalysisInstruction() = "Optimize the following code for better readability and maintainability"

  companion object {
    private val log = LoggerFactory.getLogger(CodeOptimizationTask::class.java)
  }
}