package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.BusinessProposalTask.BusinessProposalTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object BusinessProposalTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    @Test
    @Timeout(15, unit = TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = BusinessProposalTask.BusinessProposal,
            typeConfig = TaskTypeConfig(
                task_type = BusinessProposalTask.BusinessProposal.name
            ),
            executionConfig = BusinessProposalTaskExecutionConfigData(
                proposal_title = "AI-Driven Customer Support Transformation",
                proposal_type = "project",
                objective = "Implement an automated AI support system to reduce response times by 50% and improve customer satisfaction scores by 20% within 6 months.",
                proposing_organization = "Cognotik Solutions",
                decision_makers = listOf("Chief Technology Officer", "VP of Customer Success"),
                budget_range = "$100,000 - $150,000",
                timeline = "6 months",
                target_word_count = 1000, // Lowered for test speed
                revision_passes = 0,       // Disabled for test speed
                include_roi_analysis = true,
                include_risk_assessment = true,
                include_competitive_analysis = true,
                include_timeline_milestones = true
            ),
            timeoutMinutes = 15,
        ).run()
    }
}