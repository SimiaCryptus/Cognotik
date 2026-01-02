package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.InteractiveStoryTask.InteractiveStoryTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object InteractiveStoryTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = InteractiveStoryTask.InteractiveStory,
            typeConfig = TaskTypeConfig(
                task_type = InteractiveStoryTask.InteractiveStory.name
            ),
            executionConfig = InteractiveStoryTaskExecutionConfigData(
                premise = "A lone explorer discovers a derelict space station orbiting a dying star.",
                genre = "sci-fi",
                target_audience = "adult",
                tone = "mysterious",
                num_decision_points = 3,
                choices_per_decision = 2,
                num_endings = 3,
                track_state_variables = true,
                state_variables = listOf("oxygen", "sanity", "data_recovered"),
                writing_style = "descriptive",
                point_of_view = "second_person"
            ),
            timeoutMinutes = 15,
        ).run()
    }
}