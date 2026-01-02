package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.SocraticDialogueTask.SocraticDialogueTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object SocraticDialogueTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testSocraticDialogue() {
        TaskTestHarness(
            taskType = SocraticDialogueTask.SocraticDialogue,
            typeConfig = TaskTypeConfig(
                task_type = SocraticDialogueTask.SocraticDialogue.name
            ),
            executionConfig = SocraticDialogueTaskExecutionConfigData(
                initial_question = "What is the nature of software quality?",
                max_depth = 2,
                challenge_assumptions = true,
                domain_constraints = listOf("Software Engineering", "Philosophy of Technology")
            ),
            timeoutMinutes = 10,
        ).run()
    }
}