package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ArticleGenerationTask.ArticleGenerationTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File

object ArticleGenerationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        val harness = TaskTestHarness(
            taskType = ArticleGenerationTask.ArticleGeneration,
            typeConfig = TaskTypeConfig(
                task_type = ArticleGenerationTask.ArticleGeneration.name
            ),
            executionConfig = ArticleGenerationTaskExecutionConfigData(
                story_topic = "The impact of AI on modern software engineering practices",
                input_files = listOf("context.md"),
                target_word_count = 500,
                article_format = "feature",
                writing_style = "analytical",
                target_publication = "Tech Insights Weekly",
                include_quotes = true,
                include_data = true,
                include_expert_analysis = true,
                include_context = true,
                revision_passes = 1,
                generate_social_snippets = true
            ),
            timeoutMinutes = 15,
        )

        // Seed input data for the task to process
        val workingDir = harness.root
        workingDir.mkdirs()
        File(workingDir, "context.md").writeText("""
            # AI in Software Engineering
            
            Recent surveys indicate that 70% of developers are using AI tools in their workflow.
            Key benefits include faster boilerplate generation and improved bug detection.
            However, concerns remain regarding code quality, security, and the 'black box' nature of LLMs.
            Expert John Doe says: "AI is a co-pilot, not the captain. It augments human creativity but doesn't replace it."
        """.trimIndent())

        harness.run()
    }
}