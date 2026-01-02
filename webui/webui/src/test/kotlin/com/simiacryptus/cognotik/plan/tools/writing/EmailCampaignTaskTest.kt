package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.EmailCampaignTask.EmailCampaignTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File

object EmailCampaignTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    @Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        val harness = TaskTestHarness(
            taskType = EmailCampaignTask.EmailCampaign,
            typeConfig = TaskTypeConfig(
                task_type = EmailCampaignTask.EmailCampaign.name
            ),
            executionConfig = EmailCampaignTaskExecutionConfigData(
                campaign_goal = "Promote a new AI-powered productivity tool for remote teams",
                subject_matter = "Cognotik Productivity Suite",
                target_audience = "Remote team leads and project managers",
                campaign_type = "sales",
                num_emails = 2,
                send_intervals = listOf(2),
                brand_voice = "friendly and helpful",
                primary_cta = "start_free_trial",
                generate_subject_variants = true,
                subject_variants_count = 2,
                include_personalization = true,
                include_preview_text = true,
                use_emoji = true,
                body_length = "short",
                include_ps = true,
                revision_passes = 1,
                related_files = listOf("brand_guidelines.md")
            ),
            timeoutMinutes = 15,
        )

        // Seed input data for the task to process
        val workingDir = File(harness.orchestrationConfig.absoluteWorkingDir ?: "temp_test_dir")
        workingDir.mkdirs()
        File(workingDir, "brand_guidelines.md").writeText("""
            # Cognotik Brand Guidelines
            
            ## Voice and Tone
            Our voice is helpful, innovative, and approachable. We avoid corporate jargon.
            We use "you" and "your" to focus on the customer's needs.
            
            ## Key Value Propositions
            1. Save 10 hours a week on administrative tasks.
            2. Seamless integration with existing remote work tools.
            3. AI that understands context, not just keywords.
            
            ## Formatting
            Use short paragraphs and bullet points for readability.
            Always include a clear, single call-to-action.
        """.trimIndent())

        harness.run()
    }
}