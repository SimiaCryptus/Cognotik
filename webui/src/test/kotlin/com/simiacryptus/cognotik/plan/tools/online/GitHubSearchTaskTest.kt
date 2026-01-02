package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.online.GitHubSearchTask.GitHubSearchTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object GitHubSearchTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

    @Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = GitHubSearchTask.GitHubSearch,
            typeConfig = TaskTypeConfig(
                task_type = GitHubSearchTask.GitHubSearch.name
            ),
            executionConfig = GitHubSearchTaskExecutionConfigData(
                search_query = "kotlin language:kotlin",
                search_type = "repositories",
                per_page = 5,
                sort = "stars",
                order = "desc",
                task_description = "Search for popular Kotlin repositories on GitHub",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}