package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object NarrativeGenerationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = NarrativeGenerationTask.NarrativeGeneration,
            typeConfig = TaskTypeConfig(
                task_type = NarrativeGenerationTask.NarrativeGeneration.name
            ),
            executionConfig = NarrativeGenerationTaskExecutionConfigData(
                subject = "The Last Librarian in a Digital World",
                target_word_count = 500,
                number_of_acts = 1,
                scenes_per_act = 1,
                writing_style = "reflective",
                point_of_view = "third person limited",
                tone = "melancholic",
                generate_scene_images = false,
                generate_cover_image = false,
                revision_passes = 1
            ),
            timeoutMinutes = 15,
        ).run()
    }
}