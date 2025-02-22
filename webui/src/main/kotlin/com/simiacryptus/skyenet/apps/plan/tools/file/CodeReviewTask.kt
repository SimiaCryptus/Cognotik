package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.apps.plan.tools.file.CodeReviewTask.CodeReviewTaskConfigData
import org.slf4j.LoggerFactory

class CodeReviewTask(
  planSettings: PlanSettings,
  planTask: CodeReviewTaskConfigData?
) : AbstractAnalysisTask<CodeReviewTaskConfigData>(planSettings, planTask) {
  class CodeReviewTaskConfigData(
    @Description("Specific areas of focus for the review (optional)")
    val focusAreas: List<String>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    input_files: List<String>? = null,
    output_files: List<String>? = null,
    state: TaskState? = null
  ) : FileTaskConfigBase(
    task_type = TaskType.CodeReview.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    related_files = input_files,
    files = output_files,
    state = state
  )

  override val actorName: String = "CodeReview"
  override val actorPrompt: String = """
 Perform a comprehensive code review for the provided code files. Analyze the code for:
 1. Code quality and readability
 2. Potential bugs or errors
 3. Performance issues
 4. Security vulnerabilities
 5. Adherence to best practices and coding standards
 6. Suggestions for improvements or optimizations
                |
 Provide a detailed review with specific examples and recommendations for each issue found.
 Format the response as a markdown document with appropriate headings and code snippets.
    """.trimIndent()

  override fun getAnalysisInstruction(): String {
    val filesToReview = taskConfig?.related_files?.joinToString(", ") ?: "all provided files"
    val focusAreas = taskConfig?.focusAreas?.joinToString(", ")
    return "Review the following code files: $filesToReview" +
        if (focusAreas != null) ". Focus on these areas: $focusAreas" else ""
  }

  override fun promptSegment() = """
   CodeReview - Perform an automated code review and provide suggestions for improvements
     ** Specify the files to be reviewed
     ** Optionally provide specific areas of focus for the review
  """.trimIndent()


  companion object {
    private val log = LoggerFactory.getLogger(CodeReviewTask::class.java)
  }
}